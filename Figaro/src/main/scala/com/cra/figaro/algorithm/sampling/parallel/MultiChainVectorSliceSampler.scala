package com.cra.figaro.algorithm.sampling.parallel

import com.cra.figaro.algorithm.sampling.VectorSliceSampler as VS
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicLong
import scala.jdk.CollectionConverters.*
import scala.util.control.NonFatal

/** Bounded independent-chain orchestration of VectorSliceSampler, without graph or kernel duplication. */
object MultiChainVectorSliceSampler {
  /** Work, scheduling and aggregate storage limits.
    * @param sampler per-chain method/work limits; its seed is the ROOT expanded in chain-index order
    * @param chains number of independent chains, at least two
    * @param parallelism positive maximum simultaneous chains; does not affect seeds or traces
    * @param maxStoredValues positive cap on chains * requested draws * dimension, not total heap use
    * @param shutdownTimeoutMillis worker termination/join budget, 1-30000 milliseconds; not a run timeout
    */
  final case class Config(sampler: VS.Config, chains: Int = 4, parallelism: Int = 4,
    maxStoredValues: Long = 40000000L, shutdownTimeoutMillis: Long = 30000L) {
    require(sampler != null && chains >= 2 && parallelism > 0, "Invalid sampler, chain count or parallelism")
    require(maxStoredValues > 0 && sampler.draws.toLong <= maxStoredValues / chains, "Aggregate storage limit exceeded")
    require(shutdownTimeoutMillis > 0 && shutdownTimeoutMillis <= 30000, "Shutdown budget must be 1-30000 ms")
  }

  /** Caller-owned model callbacks/resources; the runner neither mutates nor closes them.
    * @param initial finite immutable initial point with the same dimension/order/target across chains
    * @param logDensity deterministic complete joint log density, negative infinity outside support;
    *                   independent closure per chain or safely shared pure function
    */
  final case class Model(initial: Vector[Double], logDensity: Vector[Double] => Double)

  /** One detached chain result.
    * @param index zero-based chain index
    * @param seed actual seed passed to VectorSliceSampler.run
    * @param result unchanged single-chain output, including budget status and all retained vectors
    */
  final case class ChainResult(index: Int, seed: Long, result: VS.Result)

  /** All chain outputs in index order, returned only after owned workers exit.
    * @param chains every chain, including those exhausting their evaluation cap
    * @param diagnostics coordinate-index ordered summaries over aligned prefixes; empty if fewer than four draws per chain
    * @param diagnosticDrawsPerChain shortest retained length; excess samples remain in chains, not diagnostics
    * @param warnings aggregate budget/alignment/insufficient-trace warnings; also inspect each diagnostic's warnings
    * @param elapsedSeconds end-to-end time including serial construction, shutdown and diagnostics
    */
  final case class Result(chains: Vector[ChainResult], diagnostics: Vector[McmcDiagnostics.Summary],
    diagnosticDrawsPerChain: Int, warnings: Vector[String], elapsedSeconds: Double)

  /** Identifies a failed factory or worker; underlying cause is preserved. No partial success is returned. */
  final class ChainFailure(val chainIndex: Int, cause: Throwable)
    extends RuntimeException(s"Vector chain $chainIndex failed: ${cause.getMessage}", cause)

  private val runIds = new AtomicLong()
  private def interrupted(): Unit =
    if (Thread.currentThread().isInterrupted) throw new InterruptedException("Multi-chain vector sampling interrupted")

  /** Build serially, execute on a private bounded pool, then summarize each coordinate.
    * @param config per-chain budgets, root seed, worker/storage/shutdown limits
    * @param build serial factory receiving chain index and assigned seed; must produce equivalent independent targets
    * @return complete chain accounting and explicit prefix diagnostics; budget exhaustion is not convergence
    * @throws ChainFailure for invalid factory output or a worker/model failure; sibling tasks are interrupted
    * @throws InterruptedException for caller/factory interruption, preserving the caller's interrupt flag
    * @throws java.lang.IllegalStateException if workers cannot terminate within the shutdown budget
    * @example `run(Config(VS.Config(VS.Method.GPSS, draws = 100))) { (i, seed) => Model(Vector(i + 1.0, 1.0), x => -x.map(v => v*v).sum / 2) }`
    */
  def run(config: Config)(build: (Int, Long) => Model): Result = {
    require(config != null && build != null, "Config and model factory required")
    val started = System.nanoTime()
    val threads = new ConcurrentLinkedQueue[Thread]()
    var pool: ExecutorService = null
    var shutdownAttempted = false
    var primary: Throwable = null
    try {
      val seeds = new java.util.SplittableRandom(config.sampler.seed)
      var dimension = 0
      val models = Vector.tabulate(config.chains) { index =>
        interrupted()
        val seed = seeds.nextLong()
        val model = try {
          val m = build(index, seed)
          interrupted()
          require(m != null && m.initial != null && m.logDensity != null, "Initial point and density required")
          require(m.initial.nonEmpty && m.initial.forall(_.isFinite), "Initial coordinates must be finite")
          if (index == 0) dimension = m.initial.size
          require(m.initial.size == dimension, "Coordinate dimensions must match across chains")
          require(dimension.toLong <= config.maxStoredValues / config.chains / config.sampler.draws,
            "Aggregate trace storage limit exceeded")
          require(dimension.toLong <= config.sampler.maxStoredValues / config.sampler.draws,
            "Per-chain trace storage limit exceeded")
          if (config.sampler.method == VS.Method.GPSS) {
            val radius = m.initial.foldLeft(0.0)(math.hypot)
            require(dimension >= 2 && radius.isFinite && radius > 0, "GPSS needs dimension >= 2 and finite nonzero radius")
          }
          m
        } catch {
          case e: InterruptedException => throw e
          case NonFatal(e) => throw new ChainFailure(index, e)
        }
        (model, seed)
      }
      interrupted()
      val runId = runIds.incrementAndGet()
      pool = Executors.newFixedThreadPool(math.min(config.parallelism, config.chains), (task: Runnable) => {
        val thread = new Thread(task, s"figaro-vector-mcmc-$runId-${threads.size() + 1}")
        thread.setDaemon(true)
        threads.add(thread)
        thread
      })
      val completed = new ExecutorCompletionService[ChainResult](pool)
      models.zipWithIndex.foreach { case ((model, seed), index) =>
        interrupted()
        completed.submit(new Callable[ChainResult] {
          def call(): ChainResult = try {
            ChainResult(index, seed, VS.run(config.sampler.copy(seed = seed), model.initial)(model.logDensity))
          } catch {
            case e if NonFatal(e) || e.isInstanceOf[InterruptedException] => throw new ChainFailure(index, e)
          }
        })
      }
      val results = new Array[ChainResult](config.chains)
      for (_ <- 0 until config.chains) {
        interrupted()
        val chain = try completed.take().get() catch { case e: ExecutionException => throw e.getCause }
        results(chain.index) = chain
      }
      shutdownAttempted = true
      closePool(pool, threads, config.shutdownTimeoutMillis)
      interrupted()
      val chains = results.toVector
      val n = chains.map(_.result.samples.size).min
      val warnings = Vector.newBuilder[String]
      if (chains.exists(_.result.reason == VS.StopReason.MaxEvaluationsReached))
        warnings += "At least one chain exhausted its evaluation cap; requested work is incomplete"
      if (chains.exists(_.result.samples.size != n))
        warnings += s"Diagnostics use only the first $n draws of every chain; excess draws remain in chain results"
      val diagnostics = if (n < 4) {
        warnings += "Diagnostics unavailable: fewer than four retained draws in at least one chain"
        Vector.empty[McmcDiagnostics.Summary]
      } else Vector.tabulate(dimension) { j =>
        interrupted()
        val summary = McmcDiagnostics.summarize(chains.map(_.result.samples.take(n).map(_(j))))
        interrupted()
        summary
      }
      interrupted()
      Result(chains, diagnostics, n, warnings.result(), (System.nanoTime() - started) / 1e9)
    } catch {
      case e: Throwable =>
        primary = e
        if (e.isInstanceOf[InterruptedException]) Thread.currentThread().interrupt()
        throw e
    } finally {
      if (pool != null && !shutdownAttempted) {
        try closePool(pool, threads, config.shutdownTimeoutMillis)
        catch {
          case cleanup: Throwable =>
            if (primary == null) throw cleanup
            else if (primary ne cleanup) primary.addSuppressed(cleanup)
        }
      }
    }
  }

  // A signalled executor can finish before its final worker thread exits. Join both.
  // Keep trying until one common deadline despite repeated caller interrupts.
  private def closePool(pool: ExecutorService, threads: ConcurrentLinkedQueue[Thread], millis: Long): Unit = {
    val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis)
    var wasInterrupted = Thread.interrupted()
    try {
      pool.shutdownNow()
      while (!pool.isTerminated && deadline - System.nanoTime() > 0) {
        try pool.awaitTermination(math.max(1L, deadline - System.nanoTime()), TimeUnit.NANOSECONDS)
        catch { case _: InterruptedException => wasInterrupted = true }
      }
      if (!pool.isTerminated) throw new IllegalStateException(s"Vector callbacks did not stop within $millis ms")
      threads.asScala.foreach { thread =>
        while (thread.isAlive && deadline - System.nanoTime() > 0) {
          try TimeUnit.NANOSECONDS.timedJoin(thread, math.max(1L, deadline - System.nanoTime()))
          catch { case _: InterruptedException => wasInterrupted = true }
        }
        if (thread.isAlive) throw new IllegalStateException(s"Vector worker did not exit within $millis ms")
      }
      if (wasInterrupted) throw new InterruptedException("Interrupted while shutting down vector workers")
    } finally { if (wasInterrupted) Thread.currentThread().interrupt() }
  }
}
