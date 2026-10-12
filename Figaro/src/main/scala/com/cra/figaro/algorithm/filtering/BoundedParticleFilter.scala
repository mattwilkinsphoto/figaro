package com.cra.figaro.algorithm.filtering

import com.cra.figaro.language.{Element, Universe}
import com.cra.figaro.algorithm.sampling.Forward
import com.cra.figaro.util.{RandomContext, SamplingRandom}
import scala.collection.mutable.ArrayBuffer

/** Indicator-only, bounded-work specialization of Frankenfilter Algorithms 3–5.
  * State values must be immutable and detached; callbacks must be pure except for Figaro's scoped RNG.
  * No general likelihood weighting, automatic retries, shared graphs or thread safety is implied.
  */
object BoundedParticleFilter {
  enum StopReason { case InitialBatch, SuccessTarget, BudgetExhausted }

  /** Budgets count candidate simulations, not seconds or raw RNG draws.
    * targetSuccesses includes the last success that is discarded on adaptive stopping.
    */
  final case class Config(minCandidates: Int = 1000, maxCandidates: Int = 10000,
    targetSuccesses: Int = 101, seed: Long = 1L,
    algorithm: SamplingRandom.Algorithm = SamplingRandom.defaultAlgorithm,
    health: ParticleFilterHealth.Config = ParticleFilterHealth.Config()) {
    require(minCandidates >= 2 && maxCandidates >= minCandidates && targetSuccesses >= 2 &&
      targetSuccesses <= maxCandidates, "Require 2 <= min <= max and 2 <= target <= max")
    require(algorithm != null && health != null, "RNG and health policy required")
  }

  /** Prior and transition draws; use util.random inside callbacks for owned seeded replay. */
  final case class Model[S](initial: () => S, transition: S => S) {
    require(initial != null && transition != null, "Prior and transition required")
  }
  final case class StepReport(attempts: Int, observedSuccesses: Int, retainedCandidates: Int,
    retainedSuccesses: Int, reason: StopReason, targetMet: Boolean, distinctParents: Option[Int],
    health: ParticleFilterHealth.Report) {
    /** Additional effort/ancestry warnings; no automatic retry or precision claim. */
    def warnings: Vector[String] =
      Option.when(!targetMet)("Candidate budget exhausted below the requested success target.").toVector ++
      Option.when(distinctParents.contains(1) && retainedSuccesses > 1)(
        "All retained candidates share one previous particle; extra current-step effort cannot certify lost-history recovery.").toVector
  }
  final case class Update[S](particles: Vector[S], logEvidence: Double, report: StepReport) {
    /** Mean of an observable under the retained equal-weight population, not a precision certificate. */
    def expectation(f: S => Double): Double = {
      require(f != null && particles.nonEmpty, "Observable and viable population required")
      val values = particles.map(f)
      require(values.forall(_.isFinite), "Finite observable values required")
      val scale = math.max(1.0, values.iterator.map(math.abs).max)
      values.iterator.map(_/scale).sum / values.size * scale
    }
  }
  /** Terminal collapse: zero evidence estimate must be included in evidence calibration.
    * No posterior is available; not proof of impossible evidence.
    */
  final class NoViableParticles(val report: StepReport) extends IllegalStateException(
    s"No viable particles after ${report.attempts} candidates. No posterior is available; evidence may be rare, not impossible. " +
    "Review evidence/proposals or increase the configured budget for a new run; never keep only successful retries.") {
    val logEvidence: Double = Double.NegativeInfinity
  }

  /** Create an unstarted thread-confined filter. Example: `val f = BoundedParticleFilter(model, Config())`. */
  def apply[S](model: Model[S], config: Config = Config()): BoundedParticleFilter[S] =
    new BoundedParticleFilter(model, config)

  /** Adapt fixed, evidence-free Figaro DAGs to detached state draws.
    * Each callback receives a fresh universe and must return an owned Element[S].
    * Use immutable S (e.g. tuples), not Element/Universe or mutable collections.
    * Observations, conditions, constraints, interventions, external parents and dynamically
    * created elements are rejected. Apply is supported; dynamic Chains are not.
    * Example: `elementModel(u => Flip(.5)(using "x", u), (x,u) => Flip(if(x) .8 else .2)(using "x",u))`.
    */
  def elementModel[S](initial: Universe => Element[S], transition: (S, Universe) => Element[S]): Model[S] = {
    require(initial != null && transition != null, "Graph factories required")
    def draw(build: Universe => Element[S]): S = {
      val u = new Universe
      try {
        val x = build(u)
        require(x != null && (x.universe eq u), "Return an element owned by the supplied fresh universe")
        def validate(): Set[Element[?]] = {
          val nodes = u.activeElements.toSet
          require(u.conditionedElements.isEmpty && u.constrainedElements.isEmpty &&
            nodes.forall(e => e.observation.isEmpty && e.intervention.isEmpty),
            "Use the explicit Boolean evidence predicate, not graph observations/constraints/interventions")
          require(nodes.forall(e => e.args.forall(a => (a.universe eq u) && nodes.contains(a))),
            "External or inactive graph parents are unsupported")
          require(!nodes.exists(_.isInstanceOf[com.cra.figaro.language.Chain[?, ?]]), "Dynamic Chains are unsupported")
          nodes
        }
        val before = validate()
        Forward(u)
        require(validate() == before, "Dynamic graph expansion is unsupported")
        x.value
      } finally u.clear()
    }
    Model(() => draw(initial), previous => draw(u => transition(previous, u)))
  }

  private[figaro] final case class Step[S](values: Vector[S], report: StepReport, logIncrement: Double)
  /** Shared stopping kernel, exposed only within Figaro for exhaustive path tests. */
  private[figaro] def step[S](config: Config, hasParents: Boolean)(draw: () => (S, Int), evidence: S => Boolean): Step[S] = {
    val survivors = ArrayBuffer.empty[(S, Int)]
    var attempts = 0
    while (attempts < config.minCandidates || (attempts < config.maxCandidates && survivors.size < config.targetSuccesses)) {
      if (Thread.currentThread().isInterrupted) throw new InterruptedException("Bounded filtering interrupted; no completed estimate")
      val candidate = draw()
      attempts += 1
      if (evidence(candidate._1)) survivors += candidate
    }
    if (Thread.currentThread().isInterrupted) throw new InterruptedException("Bounded filtering interrupted; no completed estimate")
    val observed = survivors.size
    val reason = if (attempts == config.minCandidates) StopReason.InitialBatch
      else if (observed >= config.targetSuccesses) StopReason.SuccessTarget else StopReason.BudgetExhausted
    val retained = if (reason == StopReason.SuccessTarget) {
      survivors.remove(survivors.size - 1)
      attempts - 1
    } else attempts
    val count = survivors.size
    val health = ParticleFilterHealth.summarize(retained, count,
      Option.when(count > 0)(count.toDouble), Option.when(count > 0)(1.0/count),
      config.health, ParticleFilterHealth.Eligibility.IndicatorContract)
    val report = StepReport(attempts, observed, retained, count, reason, observed >= config.targetSuccesses,
      Option.when(hasParents)(survivors.iterator.map(_._2).toSet.size), health)
    Step(survivors.iterator.map(_._1).toVector, report,
      if (count == 0) Double.NegativeInfinity else math.log(count.toDouble) - math.log(retained.toDouble))
  }
}

/** Sequential owned filter. Do not mutate states or invoke methods from callbacks. */
final class BoundedParticleFilter[S] private (model: BoundedParticleFilter.Model[S], val config: BoundedParticleFilter.Config) {
  import BoundedParticleFilter.*
  require(model != null && config != null, "Model and config required")
  private val owner = Thread.currentThread()
  private val rng = SamplingRandom.seeded(config.seed, config.algorithm)
  private var latest: Option[Update[S]] = None
  private var diagnostic: Option[StepReport] = None
  private var terminal = false
  private var running = false
  private def access(): Unit = {
    require(Thread.currentThread() eq owner, "Filter is thread-confined")
    require(!running, "Reentrant filtering is unsupported")
  }
  /** Last completed assessment remains readable after collapse; absent after callback failure. */
  def lastReport: Option[StepReport] = { access(); diagnostic }
  /** Current valid posterior; throws before start, after failure, or after close. */
  def current: Update[S] = {
    access()
    require(!terminal && latest.isDefined, "No current posterior: not started, closed or failed")
    latest.get
  }
  /** Draw the initial state population under an explicit Boolean indicator. Call once. */
  def start(evidence: S => Boolean): Update[S] = {
    access(); require(!terminal && latest.isEmpty, "Filter already started, closed or failed")
    update(evidence, initial = true)
  }
  /** Advance one step, choosing ancestors uniformly from the corrected survivor population. */
  def advance(evidence: S => Boolean): Update[S] = {
    current
    update(evidence, initial = false)
  }
  private def update(evidence: S => Boolean, initial: Boolean): Update[S] = {
    require(evidence != null, "Boolean evidence predicate required")
    running = true
    diagnostic = None
    try RandomContext.withRandom(rng) {
      val old = latest
      val result = step(config, !initial)(() => {
        if (initial) (model.initial(), -1)
        else {
          val population = old.get.particles
          val i = rng.nextInt(population.size)
          (model.transition(population(i)), i)
        }
      }, evidence)
      diagnostic = Some(result.report)
      if (result.values.isEmpty) throw new NoViableParticles(result.report)
      val logEvidence = old.map(_.logEvidence).getOrElse(0.0) + result.logIncrement
      require(logEvidence.isFinite, "Accumulated log evidence is not representable")
      val answer = Update(result.values, logEvidence, result.report)
      latest = Some(answer)
      answer
    } catch {
      case e: InterruptedException => terminal = true; latest = None; throw e
      case scala.util.control.NonFatal(e) => terminal = true; latest = None; throw e
    } finally running = false
  }
  /** Release retained values; an idempotent terminal operation on the owning thread. */
  def close(): Unit = { access(); latest = None; terminal = true }
}
