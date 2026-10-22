package com.cra.figaro.algorithm.filtering

import com.cra.figaro.util.{RandomContext, SamplingRandom}

/** Fixed-N bootstrap filtering (INF-15). Independent of the bounded stopping estimator.
  * States must be detached and immutable; callbacks use the scoped Figaro RNG.
  */
object FixedBudgetParticleFilter {
  /** A decision based only on the previous completed population. No default threshold. */
  enum Policy {
    case Always
    case WhenEssBelow(fraction: Double)
  }
  /** Stratified uses a finite-resolution CDF; LogTreeMultinomial retains log masses. */
  enum Method { case LogTreeMultinomial, Stratified }
  enum Decision { case Initial, Resampled, CarriedWeights }

  /** Exactly particles state draws on each successful step, not a wall-clock bound.
    * @param particles fixed population size, 2 through 1,000,000
    * @param policy explicit Always or WhenEssBelow with finite fraction in (0,1]
    * @param method explicit ancestor sampling method
    * @param seed owned stream seed
    * @param algorithm Figaro RNG choice
    * @param health diagnostic thresholds, independent of scheduling
    */
  final case class Config(particles: Int, policy: Policy, method: Method, seed: Long = 1L,
    algorithm: SamplingRandom.Algorithm = SamplingRandom.defaultAlgorithm,
    health: ParticleFilterHealth.Config = ParticleFilterHealth.Config()) {
    require(particles >= 2 && particles <= 1000000, "Require 2 <= particles <= 1000000")
    require(policy != null && method != null && algorithm != null && health != null, "Explicit policies required")
    policy match {
      case Policy.WhenEssBelow(r) => require(r.isFinite && r > 0 && r <= 1, "ESS fraction must be in (0,1]")
      case Policy.Always => ()
    }
  }

  /** Previous ESS drives scheduling; health describes the NEW weighted population.
    * contributingParents counts immediate parents of positive-log-weight offspring,
    * not unique states or distinct complete histories. Work counts completed callbacks.
    */
  final case class StepReport(stateDraws: Int, likelihoodCalls: Int, ancestorSelections: Int,
    decision: Decision, previousRelativeEss: Option[Double], contributingParents: Option[Int],
    health: ParticleFilterHealth.Report)

  /** Immutable weighted result. logEvidence is cumulative, not an unbiased log estimate. */
  final case class Update[S](particles: Vector[S], weights: Vector[Double], logWeights: Vector[Double],
    logEvidence: Double, report: StepReport) {
    /** Weighted finite observable mean. Example: result.expectation(x => x*x). */
    def expectation(f: S => Double): Double = {
      require(f != null && particles.nonEmpty && particles.size == weights.size, "Valid population required")
      val values = particles.map(f)
      require(values.forall(_.isFinite), "Finite observable values required")
      val scale = math.max(1.0, values.iterator.map(math.abs).max)
      val result = values.iterator.zip(weights).map((x,w) => (x/scale)*w).sum * scale
      if (!result.isFinite) throw new ArithmeticException("Observable expectation not representable")
      result
    }
  }
  /** No posterior; does not establish impossible evidence. Never retry until success. */
  final class NoViableParticles(val report: StepReport) extends IllegalStateException(
    "No viable fixed-budget particles. Review evidence and proposal support or predeclare a larger new run; retain failed outcomes.") {
    val logEvidence: Double = Double.NegativeInfinity
  }
  /** Detected loss of a positive interval, not a proof that all other rounding is harmless. */
  final class StratifiedPrecisionLoss extends ArithmeticException(
    "Positive ancestor interval is not representable in the stratified CDF. Explicitly choose LogTreeMultinomial for a new run; no automatic fallback occurred.")

  /** Create an unstarted owner-thread filter. No score policy or stopping target. */
  def apply[S](model: BoundedParticleFilter.Model[S], config: Config): FixedBudgetParticleFilter[S] =
    new FixedBudgetParticleFilter(model, config)

  private[figaro] def relativeEss(logs: Vector[Double]): Double = {
    val n = LogParticleWeights.normalize(logs)
    math.min(1.0, 1.0 / (n.weights.iterator.map(w => w*w).sum * logs.size))
  }
  private[figaro] def shouldResample(policy: Policy, relativeEss: Double): Boolean = policy match {
    case Policy.Always => true
    case Policy.WhenEssBelow(r) => relativeEss < r
  }
  private[figaro] def reweight(base: Vector[Double], likelihoods: Vector[Double]): Vector[Double] = {
    require(base.nonEmpty && base.size == likelihoods.size)
    base.zip(likelihoods).map { (b,l) =>
      require((b.isFinite || b == Double.NegativeInfinity) &&
        (l.isFinite || l == Double.NegativeInfinity), "Log likelihood must be finite or -Infinity")
      if (b == Double.NegativeInfinity || l == Double.NegativeInfinity) Double.NegativeInfinity
      else {
        val w = b+l
        if (!w.isFinite) throw new ArithmeticException("Carried log weight overflow")
        w
      }
    }
  }
  private[figaro] def stratifiedCdf(logs: Vector[Double]): Vector[Double] = {
    val weights = LogParticleWeights.normalize(logs).weights
    val last = logs.lastIndexWhere(_.isFinite)
    var sum = 0.0
    weights.indices.map { i =>
      val before = sum
      sum = if (i == last) 1.0 else if (i > last) 1.0 else sum + weights(i)
      if (logs(i).isFinite && (weights(i) == 0 || sum <= before || sum > 1))
        throw new StratifiedPrecisionLoss
      sum
    }.toVector
  }
  private[figaro] def ancestors(logs: Vector[Double], method: Method, rng: java.util.Random): Vector[Int] = {
    val n = logs.size
    method match {
      case Method.LogTreeMultinomial =>
        val tree = new LogParticleWeights.Tree(logs)
        Vector.tabulate(n) { _ =>
          interrupted()
          tree.draw(LogParticleWeights.logUniform(rng))
        }
      case Method.Stratified =>
        val cdf = stratifiedCdf(logs)
        var parent = 0
        Vector.tabulate(n) { i =>
          interrupted()
          // Addition can round the final point to one; never select a trailing zero.
          val u = math.min(math.nextDown(1.0), (i.toDouble + rng.nextDouble()) / n)
          while (parent < n-1 && u >= cdf(parent)) parent += 1
          parent
        }
    }
  }
  private def interrupted(): Unit =
    if (Thread.currentThread().isInterrupted) throw new InterruptedException("Fixed-budget filtering interrupted")
}

/** Sequential, thread-confined bootstrap filter. No guided/APF or mutable graph hooks. */
final class FixedBudgetParticleFilter[S] private (model: BoundedParticleFilter.Model[S],
  val config: FixedBudgetParticleFilter.Config) {
  import FixedBudgetParticleFilter.*
  require(model != null && config != null, "Model and explicit configuration required")
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
  /** Last valid posterior; unavailable before start, after failure or close. */
  def current: Update[S] = { access(); require(!terminal && latest.isDefined, "No current posterior"); latest.get }
  /** Last completed report, including collapse; absent after callback/numerical failure. */
  def lastReport: Option[StepReport] = { access(); diagnostic }
  /** Draw N initial states and apply one log likelihood per state. Call once. */
  def start(logLikelihood: S => Double): Update[S] = {
    access(); require(!terminal && latest.isEmpty, "Filter already started, closed or failed")
    update(logLikelihood)
  }
  /** Select ancestors once or carry weights, propagate N times, and apply new evidence. */
  def advance(logLikelihood: S => Double): Update[S] = { current; update(logLikelihood) }
  private def update(logLikelihood: S => Double): Update[S] = {
    require(logLikelihood != null, "Log likelihood required")
    running = true
    diagnostic = None
    try RandomContext.withRandom(rng) {
      interrupted()
      val old = latest
      val previousEss = old.map(x => relativeEss(x.logWeights))
      val resample = previousEss.exists(shouldResample(config.policy, _))
      val n = config.particles
      val indices = if (resample) ancestors(old.get.logWeights, config.method, rng) else Vector.range(0,n)
      val base = if (old.isEmpty || resample) Vector.fill(n)(-math.log(n)) else old.get.logWeights
      val values = Vector.tabulate(n) { i =>
        interrupted()
        old.fold(model.initial())(p => model.transition(p.particles(indices(i))))
      }
      val likelihoods = values.map { x => interrupted(); logLikelihood(x) }
      interrupted()
      val raw = reweight(base, likelihoods)
      val rawHealth = ParticleFilterHealth.assess(raw, config.health)
      val health = rawHealth.copy(recommendations = if (rawHealth.issues.isEmpty) Vector.empty else Vector(
        "Inspect likelihood consistency, weight concentration and contributing parents; ESS is not precision or mode coverage.",
        "Predeclare a larger new run or review model/proposal support. Resampling cannot recover extinct histories; do not retain only successful retries."))
      val report = StepReport(n,n,if (resample) n else 0,
        if (old.isEmpty) Decision.Initial else if (resample) Decision.Resampled else Decision.CarriedWeights,
        previousEss, old.map(_ => raw.indices.filter(i => raw(i).isFinite).map(indices).distinct.size), health)
      if (!raw.exists(_.isFinite)) { diagnostic = Some(report); throw new NoViableParticles(report) }
      val normalized = LogParticleWeights.normalize(raw)
      val increment = normalized.peak + normalized.logSum
      if (!increment.isFinite) throw new ArithmeticException("Evidence increment not representable")
      val logEvidence = old.map(_.logEvidence).getOrElse(0.0) + increment
      if (!logEvidence.isFinite) throw new ArithmeticException("Accumulated evidence not representable")
      interrupted()
      val result = Update(values,normalized.weights,normalized.logs,logEvidence,report)
      latest = Some(result); diagnostic = Some(report)
      result
    } catch {
      case e: NoViableParticles => terminal = true; latest = None; throw e
      case e: InterruptedException => terminal = true; latest = None; diagnostic = None; throw e
      case scala.util.control.NonFatal(e) => terminal = true; latest = None; diagnostic = None; throw e
    } finally { running = false }
  }
  /** Idempotent terminal cleanup; owner thread only. Existing detached snapshots remain valid. */
  def close(): Unit = { access(); latest = None; terminal = true }
}
