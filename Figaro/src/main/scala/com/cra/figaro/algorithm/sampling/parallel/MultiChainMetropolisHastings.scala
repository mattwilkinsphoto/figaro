package com.cra.figaro.algorithm.sampling.parallel

import com.cra.figaro.algorithm.OneTime
import com.cra.figaro.algorithm.sampling.{ForwardWeighter, MetropolisHastings, ProposalScheme}
import com.cra.figaro.language.*
import com.cra.figaro.util.RandomContext
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import scala.jdk.CollectionConverters.*
import scala.util.control.NonFatal

/** Blocking, isolated multi-chain MH with detached scalar traces and explicit diagnostics. */
object MultiChainMetropolisHastings {
  /** Fixed work/storage limits. Draw count is PER CHAIN; parallelism changes scheduling, not chain seeds.
    * @param chains number of independent chains, at least two
    * @param drawsPerChain retained draws per chain, at least four
    * @param warmUp discarded MH transitions per chain, nonnegative
    * @param parallelism maximum simultaneously running chains, positive
    * @param seed root seed, expanded in chain-index order
    * @param thin transitions per retained draw, positive; use one unless storage requires thinning
    * @param maxInitializationAttempts positive bound on prior initial-state attempts
    * @param maxStoredValues positive cap on chains * draws * scalar observables (not a total heap bound)
    */
  final case class Config(chains: Int = 4, drawsPerChain: Int = 10000, warmUp: Int = 1000,
    parallelism: Int = 4, seed: Long = 42L, thin: Int = 1,
    maxInitializationAttempts: Int = 1000, maxStoredValues: Long = 10000000L) {
    require(chains >= 2 && drawsPerChain >= 4, "Need at least two chains and four draws per chain")
    require(warmUp >= 0 && parallelism > 0 && thin > 0, "Invalid warm-up, parallelism, or thinning")
    require(maxInitializationAttempts > 0 && maxStoredValues > 0, "Limits must be positive")
    require(warmUp.toLong + drawsPerChain.toLong * thin <= Int.MaxValue, "Too many transitions per chain")
    require(chains.toLong * drawsPerChain <= maxStoredValues, "Draw budget exceeds storage limit")
  }

  /** Named, finite scalar projection; aligned draw indices preserve dependence across observables. */
  final class Observable private (val name: String, private[parallel] val element: Element[?],
    private[parallel] val read: () => Double)
  object Observable {
    /** Create a scalar query in the supplied chain universe.
      * @param name unique nonempty result key, identical across chains
      * @param target chain-owned element
      * @param project pure outcome-to-finite-Double mapping, e.g. a Boolean indicator
      * @return an observable definition, evaluated after each retained transition
      * @example `Observable("positive", x)(value => if (value > 0) 1.0 else 0.0)`
      */
    def apply[T](name: String, target: Element[T])(project: T => Double): Observable = {
      require(name != null && name.nonEmpty && target != null && project != null, "Invalid observable")
      new Observable(name, target, () => project(target.value))
    }
  }
  /** Model definition; the runner owns the universe supplied to the factory and clears it on exit.
    * @param observables nonempty, uniquely named scalar projections in that universe
    * @param proposal optional chain-owned proposal; None selects the existing default MH proposal
    * @param initialState pure predicate selecting acceptable PRIOR initial states only; not posterior evidence
    */
  final case class Model(observables: Vector[Observable], proposal: Option[ProposalScheme] = None,
    initialState: () => Boolean = () => true)

  /** Immutable output of one chain, independent of disposed model objects.
    * @param index zero-based chain index
    * @param seed assigned stream seed, independent of worker count
    * @param draws columns of ordered, post-warm-up scalar draws, including repeated rejected states
    * @param acceptanceRate accepted / attempted post-warm-up transitions, including thinning transitions
    * @param initializationAttempts number of prior draws needed to find an initial state
    * @param samplingSeconds initialization, warm-up, and retained sampling elapsed time for this chain
    */
  final case class ChainResult(index: Int, seed: Long, draws: Map[String, Vector[Double]],
    acceptanceRate: Double, initializationAttempts: Int, samplingSeconds: Double)

  /** Completed output, returned only if every chain succeeds and workers exit.
    * @param chains chain-index ordered immutable traces and metadata
    * @param diagnostics per-observable diagnostics; inspect warnings, not just pooled means
    * @param elapsedSeconds end-to-end time including construction, cleanup, and diagnostics
    */
  final case class Result(chains: Vector[ChainResult], diagnostics: Map[String, McmcDiagnostics.Summary],
    elapsedSeconds: Double)

  /** A failure identified by chain index, retaining the underlying exception. */
  final class ChainFailure(val chainIndex: Int, cause: Throwable)
    extends RuntimeException(s"MCMC chain $chainIndex failed: ${cause.getMessage}", cause)

  /** Construct models serially, run chains on a private bounded pool, and return detached results.
    * @param config work, scheduling, seed, and storage configuration
    * @param build creates equivalent independent models in the supplied universe; index allows dispersed initial regions
    * @return complete traces and diagnostics; no partial success result is returned
    * @throws ChainFailure for a model/worker failure; other workers are interrupted
    * @throws InterruptedException when the calling thread is interrupted; its interrupt flag is restored
    * @throws java.lang.IllegalStateException if callbacks prevent worker shutdown within 30 seconds
    * @example `MultiChainMetropolisHastings.run(Config()) { (u, i) => Model(Vector(Observable("coin", Flip(0.3)(using "", u))(b => if (b) 1.0 else 0.0))) }`
    */
  def run(config: Config)(build: (Universe, Int) => Model): Result = {
    require(config != null && build != null, "Config and model factory are required")
    val started = System.nanoTime()
    val seeds = new java.util.SplittableRandom(config.seed)
    val owned = scala.collection.mutable.ArrayBuffer.empty[Owned]
    val threads = new ConcurrentLinkedQueue[Thread]
    var pool: ExecutorService = null
    var error: Throwable = null
    var interrupted = false
    var stopped = true
    try {
      var names = Vector.empty[String]
      for (index <- 0 until config.chains) {
        checkInterrupted()
        val seed = seeds.nextLong()
        val entry = new Owned(index, seed, new Universe, new java.util.Random(seed))
        owned += entry
        try entry.scoped {
          val model = build(entry.universe, index)
          require(model != null && model.observables != null && model.observables.nonEmpty, "Observables required")
          require(model.initialState != null && model.proposal != null && !model.proposal.contains(null), "Invalid model callbacks")
          require(model.observables.forall(o => o != null && (o.element.universe eq entry.universe)), "Observable from another universe")
          require(model.observables.forall(_.element.active), "Observable target is inactive")
          val keys = model.observables.map(_.name)
          require(keys.distinct.size == keys.size, "Observable names must be unique")
          if (index == 0) names = keys else require(keys == names, "Observable names/order must match across chains")
          require(keys.size.toLong <= config.maxStoredValues / config.chains / config.drawsPerChain, "Observable traces exceed storage limit")
          require(entry.universe.activeElements.forall(_.args.forall(_.universe eq entry.universe)), "Cross-universe model dependency")
          checkObservations(entry.universe)
          entry.model = model
        } catch {
          case e: InterruptedException => throw e
          case NonFatal(e) => throw new ChainFailure(index, e)
        }
      }
      pool = Executors.newFixedThreadPool(math.min(config.parallelism, config.chains), (task: Runnable) => {
        val thread = new Thread(task, s"figaro-mcmc-worker-${threads.size() + 1}")
        thread.setDaemon(true)
        threads.add(thread)
        thread
      })
      stopped = false
      val completed = new ExecutorCompletionService[ChainResult](pool)
      owned.foreach { entry => completed.submit(new Callable[ChainResult] {
        def call(): ChainResult = {
          entry.begin()
          entry.scoped {
            withCleanup {
              try {
                val kernel = new Kernel(entry, config)
                withCleanup { kernel.start(); kernel.output } { if (kernel.isActive) kernel.kill() }
              } catch {
                case e if NonFatal(e) || e.isInstanceOf[InterruptedException] => throw new ChainFailure(entry.index, e)
              }
            } { entry.dispose() }
          }
        }
      }) }
      val results = new Array[ChainResult](config.chains)
      for (_ <- 0 until config.chains) {
        val result = try completed.take().get() catch { case e: ExecutionException => throw e.getCause }
        results(result.index) = result
      }
      closePool(pool, threads)
      stopped = true
      val chains = results.toVector
      val summaries = names.map { name =>
        checkInterrupted()
        name -> McmcDiagnostics.summarize(chains.map(_.draws(name)))
      }.toMap
      Result(chains, summaries, (System.nanoTime() - started) / 1e9)
    } catch {
      case e: Throwable => error = e; throw e
    } finally {
      interrupted = Thread.interrupted() || error.isInstanceOf[InterruptedException]
      try {
        if (pool != null && !stopped) { closePool(pool, threads); stopped = true }
      } catch {
        case cleanup: Throwable =>
          if (cleanup.isInstanceOf[InterruptedException]) interrupted = true
          if (error != null) error.addSuppressed(cleanup) else throw cleanup
      } finally {
        // Never clear a model while an uncooperative worker may still be using it.
        try {
          var cleanupError: Throwable = null
          owned.foreach { entry =>
            try { if (stopped) entry.dispose() else entry.disposeIfNotStarted() } catch {
              case e: Throwable =>
                if (error != null) error.addSuppressed(e)
                else if (cleanupError == null) cleanupError = e
                else cleanupError.addSuppressed(e)
            }
          }
          if (cleanupError != null) throw cleanupError
        }
        finally { if (interrupted) Thread.currentThread().interrupt() }
      }
    }
  }

  private def checkInterrupted(): Unit = if (Thread.currentThread().isInterrupted) throw new InterruptedException("MCMC cancelled")
  private def withCleanup[A](body: => A)(cleanup: => Unit): A = {
    var failure: Throwable = null
    try body catch { case e: Throwable => failure = e; throw e }
    finally {
      try cleanup catch {
        case e: Throwable => if (failure != null) failure.addSuppressed(e) else throw e
      }
    }
  }
  private def checkObservations(u: Universe): Unit = require(
    !u.conditionedElements.exists(_.observation.nonEmpty),
    "This MH runner supports conditions and explicit likelihood constraints, not observe(); see MULTI_CHAIN_MCMC.md")

  private final class Owned(val index: Int, val seed: Long, val universe: Universe, random: java.util.Random) {
    var model: Model = null
    // 0 = queued/constructed, 1 = exclusively worker-owned, 2 = disposed.
    // A CAS prevents a late-starting task from racing disposal after failed shutdown.
    private val state = new AtomicInteger(0)
    def begin(): Unit = if (!state.compareAndSet(0, 1)) throw new CancellationException("Chain already disposed")
    def scoped[A](body: => A): A = Universe.withUniverse(universe) { RandomContext.withRandom(random)(body) }
    def disposeIfNotStarted(): Unit = if (state.compareAndSet(0, 2)) scoped { universe.clear() }
    def dispose(): Unit = if (state.getAndSet(2) != 2) scoped { universe.clear() }
  }
  private def closePool(pool: ExecutorService, threads: ConcurrentLinkedQueue[Thread]): Unit = {
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
    pool.shutdownNow()
    if (!pool.awaitTermination(30, TimeUnit.SECONDS)) throw new IllegalStateException("MCMC callbacks did not stop within 30 seconds")
    threads.asScala.foreach { thread =>
      val remaining = deadline - System.nanoTime()
      if (remaining > 0) TimeUnit.NANOSECONDS.timedJoin(thread, remaining)
      if (thread.isAlive) throw new IllegalStateException("MCMC worker did not exit within 30 seconds")
    }
  }

  // Use the existing transition kernel without its outcome histograms or per-draw target maps.
  private final class Kernel(owner: Owned, config: Config)
    extends MetropolisHastings(owner.universe, owner.model.proposal.getOrElse(ProposalScheme.default(using owner.universe)), 0, 1)
    with OneTime {
    // Validate elements as the cache encounters them, including dynamic Chain children
    // and custom-proposal targets. No global caches or shared model locks are introduced.
    chainCache.clear()
    chainCache = new com.cra.figaro.library.cache.MHCache(universe) {
      private def validate(element: Element[?]): Unit = {
        require(element.universe eq universe, "Cross-universe model dependency or proposal")
        require(element.observation.isEmpty, "Use conditions or explicit likelihood constraints, not observe()")
        require(element.args.forall(_.universe eq universe), "Cross-universe model dependency")
      }
      override def apply[T](element: Element[T]): Option[Element[T]] = {
        validate(element)
        val result = super.apply(element)
        result.foreach(validate)
        result
      }
    }
    private var result: ChainResult = null
    def output: ChainResult = result
    override protected def mhStep(): MetropolisHastings.State = {
      checkInterrupted()
      val state = super.mhStep()
      checkObservations(universe)
      state
    }
    override protected def computeScores(): Double = {
      var score = 0.0
      universe.constrainedElements.foreach { element =>
        if (element.intervention.isEmpty) {
          val value = element.constraintValue
          require(!value.isNaN && value != Double.PositiveInfinity, "Invalid log likelihood: NaN or positive infinity")
          score += value - currentConstraintValues.getOrElseUpdate(element, 0.0)
        }
      }
      require(!score.isNaN && score != Double.PositiveInfinity, "Log-likelihood difference overflow")
      score
    }
    override protected def decideToAccept(state: MetropolisHastings.State): Boolean = {
      require(!state.modelProb.isNaN && !state.proposalProb.isNaN, "Invalid model or proposal probability ratio")
      super.decideToAccept(state)
    }
    def run(): Unit = {
      val start = System.nanoTime()
      val initializer = new ForwardWeighter(universe, chainCache)
      var attempts = 0
      var valid = false
      try {
        while (!valid && attempts < config.maxInitializationAttempts) {
          checkInterrupted()
          attempts += 1
          initializer.computeWeight(universe.activeElements)
          checkObservations(universe)
          valid = universe.conditionedElements.forall(_.conditionSatisfied) &&
            universe.constrainedElements.forall(_.constraintValue.isFinite) && owner.model.initialState()
        }
      } finally initializer.deregisterDependencies()
      require(valid, "No valid initial state within maxInitializationAttempts; check evidence/initial region")
      initConstrainedValues()
      dissatisfied = Set.empty[Element[?]]
      var i = 0
      while (i < config.warmUp) { mhStep(); i += 1 }
      accepts = 0
      rejects = 0
      val queries = owner.model.observables
      val columns = Array.fill(queries.size)(new Array[Double](config.drawsPerChain))
      i = 0
      while (i < config.drawsPerChain) {
        var step = 0
        while (step < config.thin) { mhStep(); step += 1 }
        require(dissatisfied.isEmpty, "MH left the valid state space; check proposal/evidence")
        var q = 0
        while (q < queries.size) {
          require(queries(q).element.active, s"Observable target became inactive: ${queries(q).name}")
          val value = queries(q).read()
          require(value.isFinite, s"Non-finite observable: ${queries(q).name}")
          columns(q)(i) = value
          q += 1
        }
        i += 1
      }
      result = ChainResult(owner.index, owner.seed, queries.indices.map(i => queries(i).name -> columns(i).toVector).toMap,
        accepts.toDouble / (accepts.toLong + rejects), attempts, (System.nanoTime() - start) / 1e9)
    }
  }
}
