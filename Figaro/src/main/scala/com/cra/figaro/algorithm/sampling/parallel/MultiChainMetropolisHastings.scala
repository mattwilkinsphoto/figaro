package com.cra.figaro.algorithm.sampling.parallel

import com.cra.figaro.algorithm.OneTime
import com.cra.figaro.algorithm.sampling.{FinalScheme, ForwardWeighter, LikelihoodWeighter, MetropolisHastings, ProposalScheme, SupportAwareProposal}
import com.cra.figaro.language.*
import com.cra.figaro.util.{RandomContext, SamplingRandom, RandomStreams}
import java.util.concurrent.*
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger}
import scala.jdk.CollectionConverters.*
import scala.util.control.NonFatal

/** Blocking, isolated multi-chain MH with detached scalar traces and explicit diagnostics. */
object MultiChainMetropolisHastings {
  /** Fixed work/storage limits. Draw count is PER CHAIN; parallelism changes scheduling, not chain seeds.
    * @param chains number of independent chains, at least two
    * @param drawsPerChain retained draws per chain, at least four
    * @param warmUp discarded MH transitions per chain, nonnegative
    * @param parallelism maximum simultaneously running chains, positive
    * @param seed root seed, allocated in chain-index order under randomStreams
    * @param thin transitions per retained draw, positive; use one unless storage requires thinning
    * @param maxInitializationAttempts positive bound on prior initial-state attempts
    * @param maxStoredValues positive cap on chains * draws * scalar observables (not a total heap bound)
    * @param randomAlgorithm named per-chain RNG; LXM by default, LegacyJava for historical replay
    * @param randomStreams versioned allocation policy; default preserves previous seed expansion
    */
  final case class Config(chains: Int = 4, drawsPerChain: Int = 10000, warmUp: Int = 1000,
    parallelism: Int = 4, seed: Long = 42L, thin: Int = 1,
    maxInitializationAttempts: Int = 1000, maxStoredValues: Long = 10000000L,
    randomAlgorithm: SamplingRandom.Algorithm = SamplingRandom.defaultAlgorithm,
    randomStreams: RandomStreams.Config = RandomStreams.Config()) {
    require(chains >= 2 && drawsPerChain >= 4, "Need at least two chains and four draws per chain")
    require(warmUp >= 0 && parallelism > 0 && thin > 0, "Invalid warm-up, parallelism, or thinning")
    require(maxInitializationAttempts > 0 && maxStoredValues > 0, "Limits must be positive")
    require(warmUp.toLong + drawsPerChain.toLong * thin <= Int.MaxValue, "Too many transitions per chain")
    require(chains.toLong * drawsPerChain <= maxStoredValues, "Draw budget exceeds storage limit")
    require(randomAlgorithm != null, "RNG algorithm is required")
    require(randomStreams != null, "Stream policy required")
    randomStreams.validate(randomAlgorithm)
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

  /** Opt-in adaptation wrapper; preserves the existing Model constructor contract. */
  final case class TunedModel(model: Model, tuning: SupportAwareProposal.Tuning)
  /** Detached ordinary result and one frozen scale report per chain, in chain order. */
  final case class TunedResult(result: Result, tuning: Vector[SupportAwareProposal.TuningReport])

  /** Run with discarded scalar warm-up tuning, followed by frozen production moves.
    * @param config fixed warm-up/production budgets; warmUp must be positive
    * @param build returns a fresh ordinary model and tuner in each supplied universe
    * @return posterior traces/diagnostics plus frozen scale reports
    * @example `runTuned(config) { (u,i) => TunedModel(model, SupportAwareProposal.tune(x)) }`
    */
  def runTuned(config: Config)(build: (Universe,Int) => TunedModel): TunedResult = {
    require(build != null)
    val tuners=scala.collection.mutable.LinkedHashMap.empty[Universe,SupportAwareProposal.Tuning]
    val result=execute(config,None,u => tuners.get(u)) { (u,i) =>
      val built=build(u,i)
      require(built != null && built.tuning != null && built.model != null)
      tuners.update(u,built.tuning)
      built.model
    }.result
    TunedResult(result,tuners.valuesIterator.map(_.report).toVector)
  }

  /** Immutable output of one chain, independent of disposed model objects.
    * @param index zero-based chain index
    * @param seed historical seed label, independent of worker count; use randomStream for partitioned replay
    * @param draws columns of ordered, post-warm-up scalar draws, including repeated rejected states
    * @param acceptanceRate accepted / attempted post-warm-up transitions, including thinning transitions
    * @param initializationAttempts number of prior draws needed to find an initial state
    * @param samplingSeconds initialization, warm-up, and retained sampling elapsed time for this chain
    * @param randomStream start-of-stream replay descriptor; seed alone is insufficient for partitioned allocation
    */
  final case class ChainResult(index: Int, seed: Long, draws: Map[String, Vector[Double]],
    acceptanceRate: Double, initializationAttempts: Int, samplingSeconds: Double,
    randomStream: Option[RandomStreams.Descriptor] = None)

  /** Completed output, returned only if every chain succeeds and workers exit.
    * @param chains chain-index ordered immutable traces and metadata
    * @param diagnostics per-observable diagnostics; inspect warnings, not just pooled means
    * @param elapsedSeconds end-to-end time including construction, cleanup, and diagnostics
    */
  final case class Result(chains: Vector[ChainResult], diagnostics: Map[String, McmcDiagnostics.Summary],
    elapsedSeconds: Double)

  /** Why an adaptive run finished; exhausting the budget is not a precision success. */
  enum StopReason { case PrecisionReached, MaxDrawsReached }

  /** Adaptive output with final assessments and number of synchronized checks. */
  final case class StoppedResult(result: Result, reason: StopReason,
    assessments: Map[String, McmcPrecision.Assessment], checks: Int)

  /** Run persistent chains in batches until every scalar mean meets the precision policy or budget.
    * @param config drawsPerChain is the MAXIMUM per-chain budget; other settings retain their meaning
    * @param precision minimum work, batch/check schedule, confidence, and precision settings
    * @param build independent model factory, exactly as for run
    * @return detached traces, final assessments, and an explicit stop reason; inspect diagnostics too
    * @example `runUntilPrecise(Config(), McmcPrecision.Config(relativeTolerance = 0.1))(build)`
    */
  def runUntilPrecise(config: Config, precision: McmcPrecision.Config)(build: (Universe, Int) => Model): StoppedResult = {
    require(precision != null, "Precision policy required")
    execute(config, Some(precision))(build)
  }

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
  def run(config: Config)(build: (Universe, Int) => Model): Result = execute(config, None)(build).result

  private def execute(config: Config, precision: Option[McmcPrecision.Config],
    tunerFor: Universe => Option[SupportAwareProposal.Tuning] = _ => None)(build: (Universe, Int) => Model): StoppedResult = {
    require(config != null && build != null, "Config and model factory are required")
    val started = System.nanoTime()
    val streams = RandomStreams.allocate(config.seed, config.chains, config.randomAlgorithm, config.randomStreams)
    val owned = scala.collection.mutable.ArrayBuffer.empty[Owned]
    val threads = new ConcurrentLinkedQueue[Thread]
    val abandoned = new AtomicBoolean(false)
    var pool: ExecutorService = null
    var error: Throwable = null
    var interrupted = false
    var stopped = true
    try {
      var names = Vector.empty[String]
      for (index <- 0 until config.chains) {
        checkInterrupted()
        val stream = streams(index)
        val seed = stream.seed
        val entry = new Owned(index, seed, new Universe, stream.random, stream.descriptor)
        owned += entry
        try entry.scoped {
          val model = build(entry.universe, index)
          require(model != null && model.observables != null && model.observables.nonEmpty, "Observables required")
          require(model.initialState != null && model.proposal != null && !model.proposal.contains(null), "Invalid model callbacks")
          entry.tuning=tunerFor(entry.universe)
          entry.tuning.foreach { tuner =>
            require(model.proposal.isEmpty && config.warmUp > 0,
              "Automatic tuning requires positive discarded warm-up and no competing custom proposal")
            val latent=entry.universe.stochasticElements.filter(e => e.observation.isEmpty && e.intervention.isEmpty)
            require(latent.size == 1 && (latent.head eq tuner.target),
              "Automatic scalar tuning requires exactly one latent stochastic element; other parameters would be frozen")
          }
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
      def submit(limit: Int): Unit = owned.foreach { entry => completed.submit(new Callable[ChainResult] {
        def call(): ChainResult = {
          entry.begin()
          withCleanup { entry.scoped {
            try {
              if (entry.kernel == null) {
                entry.kernel = new Kernel(entry, config)
                entry.kernel.start()
              }
              entry.kernel.advanceTo(limit)
              entry.kernel.output
            } catch {
              case e if NonFatal(e) || e.isInstanceOf[InterruptedException] => throw new ChainFailure(entry.index, e)
            }
          } } {
            entry.release()
            if (abandoned.get()) entry.disposeIfIdle()
          }
        }
      }) }
      val results = new Array[ChainResult](config.chains)
      var limit = precision.fold(config.drawsPerChain)(p => math.min(p.minDrawsPerChain, config.drawsPerChain))
      var assessments = Map.empty[String, McmcPrecision.Assessment]
      var checks = 0
      var reached = false
      var done = false
      while (!done) {
        submit(limit)
        // Never block worker threads at a barrier: fewer workers than chains is supported.
        for (_ <- 0 until config.chains) {
          val result = try completed.take().get() catch { case e: ExecutionException => throw e.getCause }
          results(result.index) = result
        }
        precision.foreach { policy =>
          assessments = names.map { name =>
            checkInterrupted()
            name -> McmcPrecision.evaluate(results.toVector.map(_.draws(name)), policy, names.size)
          }.toMap
          checks += 1
          reached = assessments.values.forall(_.criteriaMet)
        }
        done = reached || limit == config.drawsPerChain
        if (!done) limit = math.min(config.drawsPerChain.toLong, limit.toLong + precision.get.checkEvery).toInt
      }
      closePool(pool, threads)
      stopped = true
      val chains = results.toVector
      val summaries = if (precision.isDefined) assessments.view.mapValues(_.diagnostics).toMap else names.map { name =>
        checkInterrupted()
        name -> McmcDiagnostics.summarize(chains.map(_.draws(name)))
      }.toMap
      // Dispose before returning (and before timing stops); failure cleanup remains in finally.
      owned.foreach(_.dispose())
      StoppedResult(Result(chains, summaries, (System.nanoTime() - started) / 1e9),
        if (reached) StopReason.PrecisionReached else StopReason.MaxDrawsReached, assessments, checks)
    } catch {
      case e: Throwable => abandoned.set(true); error = e; throw e
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
            try { if (stopped) entry.dispose() else entry.disposeIfIdle() } catch {
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
  private def checkObservations(u: Universe): Unit = u.conditionedElements.filter(_.observation.nonEmpty).foreach { e =>
    require(!e.isTemporary && e.intervention.isEmpty,
      "Observations must be permanent and cannot also be interventions")
    require(e.isInstanceOf[HasDensity[?]] || e.isInstanceOf[CompoundFlip] || e.isInstanceOf[Chain[?, ?]],
      "Observed element needs an explicit likelihood; arbitrary deterministic equality observations are unsupported")
  }

  private def ownedProposal(owner: Owned): ProposalScheme = owner.tuning.map(_.scheme).orElse(owner.model.proposal).getOrElse {
    if (!owner.universe.conditionedElements.exists(_.observation.nonEmpty))
      ProposalScheme.default(using owner.universe)
    else FinalScheme(() => {
      val choices = owner.universe.stochasticElements.filter(e => e.observation.isEmpty && e.intervention.isEmpty)
      require(choices.nonEmpty, "No unobserved stochastic elements to propose; use exact evaluation")
      choices(com.cra.figaro.util.random.nextInt(choices.size))
    })
  }

  private final class Owned(val index: Int, val seed: Long, val universe: Universe, random: java.util.Random,
    val descriptor: RandomStreams.Descriptor) {
    var model: Model = null
    var tuning: Option[SupportAwareProposal.Tuning] = None
    var kernel: Kernel = null
    // 0 = constructed, 1 = running, 2 = disposed, 3 = idle between batches.
    // CAS transitions prevent queued tasks and a late-returning worker racing failure cleanup.
    private val state = new AtomicInteger(0)
    def begin(): Unit = if (!state.compareAndSet(0, 1) && !state.compareAndSet(3, 1)) throw new CancellationException("Chain unavailable")
    def release(): Unit = { state.compareAndSet(1, 3); () }
    def scoped[A](body: => A): A = Universe.withUniverse(universe) { RandomContext.withRandom(random)(body) }
    private def clear(): Unit = scoped { withCleanup { if (kernel != null && kernel.isActive) kernel.kill() } { universe.clear() } }
    def disposeIfIdle(): Unit = if (state.compareAndSet(0, 2) || state.compareAndSet(3, 2)) clear()
    def dispose(): Unit = if (state.getAndSet(2) != 2) clear()
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
    extends MetropolisHastings(owner.universe, ownedProposal(owner), 0, 1)
    with OneTime {
    private val observedRoots = universe.conditionedElements.filter(_.observation.nonEmpty).toVector
    private val originalObservations: Map[Element[?], Option[Any]] =
      observedRoots.foldLeft(Map.empty[Element[?], Option[Any]])((m, e) => m.updated(e, e.observation))
    private val propagated = scala.collection.mutable.Set.empty[Element[?]]
    private var acceptedObservationScore = 0.0
    private var proposedObservationScore = 0.0
    override protected def isFixedObservation(e: Element[?]): Boolean = e.observation.nonEmpty

    private def validateEvidence(): Unit = {
      checkObservations(universe)
      require(observedRoots.forall(e => e.active && e.observation == originalObservations(e)),
        "Observation roots cannot change during sampling")
      require(universe.conditionedElements.filter(_.observation.nonEmpty).forall(observedRoots.contains),
        "New observations cannot be attached during sampling")
      propagated.filterInPlace(_.active)
      owner.tuning.foreach { tuner =>
        val latent=universe.stochasticElements.filter(e => e.observation.isEmpty && e.intervention.isEmpty)
        require(latent.size == 1 && (latent.head eq tuner.target),
          "Automatic scalar tuning cannot freeze newly generated latent elements")
      }
    }

    private def scoreObservation(e: Element[?], value: Any): Double = {
      val score = e match {
        case c: Chain[?, ?] => scoreObservation(chainCache(c).get, value)
        case f: CompoundFlip =>
          val p = f.prob.value
          require(p.isFinite && p >= 0.0 && p <= 1.0, "Invalid observed Bernoulli probability")
          if (value.asInstanceOf[Boolean]) math.log(p) else math.log1p(-p)
        case d: HasLogDensity[?] => d.asInstanceOf[HasLogDensity[Any]].logDensity(value)
        case d: HasDensity[?] =>
          val density = d.asInstanceOf[HasDensity[Any]].density(value)
          require(density.isFinite && density >= 0.0, "Invalid observed density; provide stable logDensity")
          math.log(density)
        case _ => throw new IllegalArgumentException("Unsupported observed likelihood")
      }
      require(!score.isNaN && score != Double.PositiveInfinity, "Invalid observed log likelihood")
      score
    }

    private def observationScore(): Double = {
      val score = observedRoots.map(e => scoreObservation(e, e.observation.get)).sum
      require(!score.isNaN && score != Double.PositiveInfinity, "Observed log-likelihood sum overflow")
      score
    }
    // Validate elements as the cache encounters them, including dynamic Chain children
    // and custom-proposal targets. No global caches or shared model locks are introduced.
    chainCache.clear()
    chainCache = new com.cra.figaro.library.cache.MHCache(universe) {
      private def validate(element: Element[?]): Unit = {
        require(element.universe eq universe, "Cross-universe model dependency or proposal")
        require(element.observation.isEmpty || observedRoots.contains(element) || propagated.contains(element),
          "Unsupported dynamic observation; observe a permanent likelihood root")
        require(element.args.forall(_.universe eq universe), "Cross-universe model dependency")
      }
      override def apply[T](element: Element[T]): Option[Element[T]] = {
        validate(element)
        val result = super.apply(element)
        if (element.observation.nonEmpty && element.isInstanceOf[Chain[?, ?]]) result.foreach { child =>
          require((child.universe eq universe) && child.isTemporary && universe.inContext(child, element),
            "Observed Chain must create its own atomic likelihood child; shared children are unsupported")
          val nested = child.isInstanceOf[Chain[?, ?]]
          require(((child.isInstanceOf[Atomic[?]] && child.args.isEmpty) ||
            (nested && child.args.forall(e => !e.isTemporary))) &&
            child.intervention.isEmpty && child.allConstraints.isEmpty,
            "Observed Chain must return an atomic likelihood or owned nested Chain with permanent parents")
          require(child.context.size <= 32, "Observed likelihood nesting exceeds depth budget 32")
          require(element.directContextContents.forall(e => (e eq child) || propagated.contains(e)),
            "Observed Chain must create only its likelihood child; additional latent nodes are unsupported")
          if (!propagated.contains(child)) {
            require(child.observation.isEmpty && child.allConditions.isEmpty,
              "Attach observations to the permanent Chain, not inside its callback")
            propagated += child
            // Internal likelihood clamping: public observe() forbids temporary elements
            // and would regenerate downstream graphs. The permanent root owns evidence.
            child.observation = element.observation
          }
          child.value = element.observation.get
        }
        result.foreach(validate)
        result
      }
    }
    private val queries = owner.model.observables
    private val columns = Array.fill(queries.size)(new Array[Double](config.drawsPerChain))
    private var retained = 0
    private var attempts = 0
    private var samplingNanos = 0L
    def output: ChainResult = ChainResult(owner.index, owner.seed,
      queries.indices.map(i => queries(i).name -> columns(i).iterator.take(retained).toVector).toMap,
      accepts.toDouble / (accepts.toLong + rejects), attempts, samplingNanos / 1e9, Some(owner.descriptor))
    override protected def mhStep(): MetropolisHastings.State = {
      checkInterrupted()
      val state = super.mhStep()
      validateEvidence()
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
      proposedObservationScore = observationScore()
      val combined = score + proposedObservationScore - acceptedObservationScore
      require(!combined.isNaN && combined != Double.PositiveInfinity, "Total log-likelihood difference overflow")
      combined
    }
    override protected def accept(state: MetropolisHastings.State): Unit = {
      super.accept(state)
      acceptedObservationScore = proposedObservationScore
    }
    override protected def decideToAccept(state: MetropolisHastings.State): Boolean = {
      require(!state.modelProb.isNaN && !state.proposalProb.isNaN, "Invalid model or proposal probability ratio")
      super.decideToAccept(state)
    }
    def run(): Unit = {
      val start = System.nanoTime()
      val initializer = if (observedRoots.isEmpty) new ForwardWeighter(universe, chainCache)
        else new LikelihoodWeighter(universe, chainCache) { override def rejectionAction(): Unit = () }
      var valid = false
      try {
        while (!valid && attempts < config.maxInitializationAttempts) {
          checkInterrupted()
          attempts += 1
          initializer.computeWeight(universe.activeElements)
          validateEvidence()
          acceptedObservationScore = observationScore()
          valid = universe.conditionedElements.forall(_.conditionSatisfied) &&
            universe.constrainedElements.forall(_.constraintValue.isFinite) && acceptedObservationScore.isFinite && owner.model.initialState()
        }
      } finally initializer.deregisterDependencies()
      require(valid, "No valid initial state within maxInitializationAttempts; check evidence/initial region")
      initConstrainedValues()
      dissatisfied = Set.empty[Element[?]]
      owner.tuning.foreach(_.begin(universe))
      var i = 0
      while (i < config.warmUp) {
        val before=accepts
        mhStep()
        owner.tuning.foreach(_.update(accepts > before))
        i += 1
      }
      owner.tuning.foreach(_.freeze())
      accepts = 0
      rejects = 0
      samplingNanos += System.nanoTime() - start
    }
    def advanceTo(limit: Int): Unit = {
      val start = System.nanoTime()
      while (retained < limit) {
        var step = 0
        while (step < config.thin) { mhStep(); step += 1 }
        require(dissatisfied.isEmpty, "MH left the valid state space; check proposal/evidence")
        var q = 0
        while (q < queries.size) {
          require(queries(q).element.active, s"Observable target became inactive: ${queries(q).name}")
          val value = queries(q).read()
          require(value.isFinite, s"Non-finite observable: ${queries(q).name}")
          columns(q)(retained) = value
          q += 1
        }
        retained += 1
      }
      samplingNanos += System.nanoTime() - start
    }
  }
}
