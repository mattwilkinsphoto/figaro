/*
 * ParImportance.scala
 * Parallel importance sampling.
 * 
 * Created By:      Lee Kellogg (lkellog@cra.com)
 * Creation Date:   May 11, 2015
 * 
 * Copyright 2017 Avrom J. Pfeffer and Charles River Analytics, Inc.
 * See http://www.cra.com or email figaro@cra.com for information.
 * 
 * See http://www.github.com/p2t2/figaro for a copy of the software license.
 */

package com.cra.figaro.algorithm.sampling.parallel

import scala.collection.parallel.ParSeq
import scala.collection.parallel.CollectionConverters._
import com.cra.figaro.algorithm.sampling._
import com.cra.figaro.algorithm._
import com.cra.figaro.language._
import com.cra.figaro.util.RandomContext
import java.util.concurrent.{Callable, CancellationException, ExecutionException, ExecutorService, Executors, TimeUnit}
import scala.jdk.CollectionConverters.*
import scala.util.control.NonFatal

object ParImportance {
  
  /**
   * Create a parallel anytime importance sampler with the given target query references.
   * 
   * @param generator a function that returns a universe, with any evidence applied
   * @param numThreads the number of threads to spawn
   * @param targets references to the target elements
   */
  def apply(generator: () => Universe, numThreads: Int, targets: Reference[?]*) = {
    require(numThreads > 0, "numThreads must be positive")
    val algs = for ( _ <- 1 to numThreads) yield {
      val universe = generator()
      val elements = targets.map(universe.getElementByReference(_))
      Importance(elements*)(using universe)
    }
    new ParSampler(algs, targets*) with ParAnytime {
      
      override val parAlgs: ParSeq[Importance & AnytimeProbQuerySampler] = algs.par
      
    }
  }

  /**
   * Create a parallel one-time importance sampler with the given target query references
   * using the given number of samples.
   * 
   * @param generator a function that returns a universe, with any evidence applied
   * @param numThreads the number of threads to spawn
   * @param numSamples the number of samples to take, total, across the threads
   * @param targets references to the target elements
   */
  def apply(generator: () => Universe, numThreads: Int, numSamples: Int, targets: Reference[?]*) = {
    require(numThreads > 0 && numSamples > 0, "numThreads and numSamples must be positive")
    val workerCount = math.min(numThreads, numSamples)
    val algs = for (index <- 0 until workerCount) yield {
      val universe = generator()
      val elements = targets.map(universe.getElementByReference(_))
      val budget = numSamples / workerCount + (if (index < numSamples % workerCount) 1 else 0)
      Importance(budget, elements*)(using universe)
    }
    new ParSampler(algs, targets*) with ParOneTime with ProbEvidenceQuery {
      
      override val parAlgs: ParSeq[Importance & OneTimeProbQuerySampler & ProbEvidenceQuery] = algs.par
      
      /**
        * Compute the probability of the given named evidence.
        * Takes the conditions and constraints in the model as part of the model definition.
        * This method takes care of creating and running the necessary algorithms.
        */
      override def probabilityOfEvidence(evidence: List[NamedEvidence[?]]): Double = {
        val poes = parAlgs.toList.map { alg => 
          alg.probabilityOfEvidence(evidence)
        }
        val total = getTotalWeight
        val weightedPOEs = algs zip poes map { case (alg, poe) =>
          // raise from log space and apply to POE
          poe * math.exp(alg.getTotalWeight - total)
        }
        weightedPOEs.sum
      }
    }
  }

  /** Create a blocking importance sampler with a bounded private pool and worker-local RNGs.
   * Model factories run serially in isolated default-universe/RNG scopes; sampling runs in parallel.
   * Each worker retains one RNG across lifecycle phases. A fresh factory call replays its seed assignment,
   * not necessarily identical model traversal or floating-point results. Changing worker count changes streams/budgets.
   * Put evidence in generator; this overload does not provide incremental probabilityOfEvidence.
   * Call start, query, then kill in try/finally. Do not concurrently call lifecycle/query methods.
   * Callbacks must cooperate with interruption; arbitrary non-interruptible user code cannot be forcibly stopped.
   * @param generator creates a fresh universe with all evidence; never share mutable model nodes between workers
   * @param numThreads positive maximum number of worker threads (capped at numSamples)
   * @param numSamples positive total sample budget, including any remainder
   * @param seed root seed deterministically expanded into one java.util.Random seed per worker
   * @param targets references resolved separately in each generated universe
   * @return a one-time parallel sampler; kill releases its executor and child sampler resources
   * @example `val alg = ParImportance.seeded(makeModel, 4, 100000, 42L, "query")`
   */
  def seeded(generator: () => Universe, numThreads: Int, numSamples: Int, seed: Long,
    targets: Reference[?]*): ParSampler & ParOneTime = {
    require(numThreads > 0 && numSamples > 0, "numThreads and numSamples must be positive")
    val count = math.min(numThreads, numSamples)
    val seeds = new java.util.SplittableRandom(seed)
    val seen = new java.util.IdentityHashMap[Universe, java.lang.Boolean]
    val created = scala.collection.mutable.ArrayBuffer.empty[(Importance & OneTimeProbQuerySampler, java.util.Random)]
    val workers = try (0 until count).map { index =>
      val random = new java.util.Random(seeds.nextLong())
      val budget = numSamples / count + (if (index < numSamples % count) 1 else 0)
      val algorithm = RandomContext.withRandom(random) {
        Universe.withUniverse(Universe.universe) {
          val universe = generator()
          require(universe != null && !seen.containsKey(universe), "generator must return distinct, non-null universes")
          seen.put(universe, true)
          val elements = targets.map(universe.getElementByReference(_))
          new Importance(universe, elements*) with OneTimeProbQuerySampler {
            val numSamples = budget
            override protected def checkSamplingInterrupted(): Unit = {
              if (Thread.currentThread().isInterrupted) throw new CancellationException("Sampling interrupted")
            }
          }
        }
      }
      val worker = (algorithm, random)
      created += worker
      worker
    } catch {
      case error if NonFatal(error) || error.isInstanceOf[InterruptedException] =>
        // Construction has not started any sampler. Undo only our registrations, not caller model state.
        created.foreach { case (algorithm, _) =>
          try {
            algorithm.lw.clearCache()
            algorithm.lw.deregisterDependencies()
            algorithm.universe.deregisterAlgorithm(algorithm)
          } catch { case cleanup: Exception => error.addSuppressed(cleanup) }
        }
        throw error
    }
    new ParSampler(workers.map(_._1), targets*) with ParOneTime {
      override protected val parAlgs: ParSeq[Importance & OneTimeProbQuerySampler] = workers.map(_._1).par
      private var executor: ExecutorService = null
      private def pool: ExecutorService = {
        if (executor == null || executor.isShutdown) {
          val threadIds = new java.util.concurrent.atomic.AtomicInteger(0)
          executor = Executors.newFixedThreadPool(count, (task: Runnable) => {
            val thread = new Thread(task, s"figaro-importance-worker-${threadIds.incrementAndGet()}")
            thread.setDaemon(true)
            thread
          })
        }
        executor
      }
      override protected def foreachAlgorithm(function: Algorithm => Unit): Unit = {
        val tasks = workers.map { case (algorithm, random) => new Callable[Unit] {
          def call(): Unit = Universe.withUniverse(algorithm.universe) {
            RandomContext.withRandom(random) { function(algorithm) }
          }
        }}
        // invokeAll waits for all workers before a failure is rethrown, so cleanup cannot race live sampling.
        val results = pool.invokeAll(tasks.asJava)
        results.asScala.foreach { result =>
          try result.get()
          catch { case e: ExecutionException => throw e.getCause }
        }
      }
      private def closePool(): Unit = {
        if (executor != null) {
          executor.shutdownNow()
          if (!executor.awaitTermination(30, TimeUnit.SECONDS))
            throw new IllegalStateException("Sampling callbacks did not respond to cancellation within 30 seconds")
          executor = null
        }
      }
      override protected[algorithm] def doStart(): Unit = synchronized {
        try super.doStart()
        catch {
          case error if NonFatal(error) || error.isInstanceOf[InterruptedException] =>
            try {
              closePool()
              workers.foreach { case (algorithm, random) =>
                Universe.withUniverse(algorithm.universe) {
                  RandomContext.withRandom(random) { if (algorithm.isActive) algorithm.kill() }
                }
              }
            } catch { case cleanup: Exception => error.addSuppressed(cleanup) }
            active = false
            if (error.isInstanceOf[InterruptedException]) Thread.currentThread().interrupt()
            throw error
        }
      }
      override protected[algorithm] def doKill(): Unit = synchronized {
        try foreachAlgorithm(a => if (a.isActive) a.kill())
        finally { try closePool() finally active = false }
      }
    }
  }

  /**
   * Use parallel IS to compute the probability that the given reference element satisfies the given predicate.
   */
  def probability[T](generator: () => Universe, numThreads: Int, target: Reference[T], predicate: T => Boolean): Double = {
    val alg = ParImportance(generator, numThreads, 10000, target)
    alg.start()
    val result = alg.probability(target, predicate)
    alg.kill()
    result
  }

  /**
   * Use parallel IS to compute the probability that the given reference element has the given value.
   */
  def probability[T](generator: () => Universe, numThreads: Int, target: Reference[T], value: T): Double =
    probability(generator, numThreads, target, (t: T) => t == value)
}
