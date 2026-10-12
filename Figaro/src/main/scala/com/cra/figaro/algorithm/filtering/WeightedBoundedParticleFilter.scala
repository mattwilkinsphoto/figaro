package com.cra.figaro.algorithm.filtering

import com.cra.figaro.util.{RandomContext, SamplingRandom}
import scala.collection.mutable.ArrayBuffer

/** Bounded bootstrap filtering with general likelihoods (research reference INF-09).
  * Independent specialization of Frankenfilter v2 Algorithms 3/4, not a graph retry.
  * Callbacks must be pure except for Figaro's scoped RNG; states must be immutable.
  */
object WeightedBoundedParticleFilter {
  enum StopReason { case InitialBatch, ScoreTarget, BudgetExhausted }

  /** Work counts candidates, not seconds. scoreTarget is not an ESS/precision target. */
  final case class Config(minCandidates: Int = 1000, maxCandidates: Int = 10000,
    scoreTarget: Double = 100, seed: Long = 1L,
    algorithm: SamplingRandom.Algorithm = SamplingRandom.defaultAlgorithm,
    health: ParticleFilterHealth.Config = ParticleFilterHealth.Config()) {
    require(minCandidates >= 2 && maxCandidates >= minCandidates && maxCandidates <= 1000000,
      "Require 2 <= min <= max <= 1000000")
    require(scoreTarget.isFinite && scoreTarget > 0 && scoreTarget <= maxCandidates,
      "Require finite 0 < score target <= max")
    require(algorithm != null && health != null, "RNG and health policy required")
  }

  /** Fixed evidence policy for one step. score receives the raw log likelihood, not normalized weights.
    * Neither callback may adapt to this step's previously drawn candidates.
    */
  final case class Evidence[S](logLikelihood: S => Double, score: Double => Double) {
    require(logLikelihood != null && score != null, "Likelihood and fixed score policy required")
  }

  /** Return s(logWeight)=min(1, exp(logWeight-logScale)); never changes inference weights.
    * Choose finite logScale independently before sampling, e.g. a known density peak.
    */
  def cappedLikelihood(logScale: Double): Double => Double = {
    require(logScale.isFinite, "Finite predeclared log scale required")
    w => {
      require(w.isFinite || w == Double.NegativeInfinity, "Invalid log likelihood")
      math.exp(math.min(0.0, w-logScale))
    }
  }

  final case class StepReport(attempts: Int, retainedCandidates: Int, totalScore: Double,
    reason: StopReason, targetMet: Boolean, distinctParents: Option[Int],
    health: ParticleFilterHealth.Report) {
    /** Work and ancestry warnings, not accuracy certificates. */
    def warnings: Vector[String] =
      Option.when(!targetMet)("Work cap reached below the score target; inspect weights and proposal support.").toVector ++
      Option.when(distinctParents.contains(1))("All positive retained weight descends from one parent; more current-step work cannot certify history recovery.").toVector
  }
  final case class Update[S](particles: Vector[S], weights: Vector[Double],
    logEvidence: Double, report: StepReport) {
    /** Weighted finite observable mean; normalized posterior estimates need not be unbiased. */
    def expectation(f: S => Double): Double = {
      require(f != null && particles.nonEmpty && particles.size == weights.size, "Valid population required")
      val values = particles.map(f)
      require(values.forall(_.isFinite), "Finite observable values required")
      val scale = math.max(1.0, values.iterator.map(math.abs).max)
      values.iterator.zip(weights).map((x,w) => (x/scale)*w).sum * scale
    }
  }
  /** Genuine zero retained likelihood: no posterior, not proof of impossible evidence. */
  final class NoViableParticles(val report: StepReport) extends IllegalStateException(
    "No viable weighted particles. Review evidence/proposal support or predeclare a larger new run; do not keep only successful retries.") {
    val logEvidence: Double = Double.NegativeInfinity
  }

  /** Create an unstarted, thread-confined filter; model draws directly from prior/transition. */
  def apply[S](model: BoundedParticleFilter.Model[S], config: Config = Config()): WeightedBoundedParticleFilter[S] =
    new WeightedBoundedParticleFilter(model, config)

  private[figaro] final case class Step[S](values: Vector[S], weights: Vector[Double],
    report: StepReport, logIncrement: Double)

  private[figaro] def step[S](config: Config, hasParents: Boolean)(draw: () => (S,Int),
    evidence: Evidence[S]): Step[S] = {
    val candidates = ArrayBuffer.empty[(S,Int,Double)]
    var score = 0.0
    while (candidates.size < config.minCandidates ||
      (candidates.size < config.maxCandidates && score < config.scoreTarget)) {
      if (Thread.currentThread().isInterrupted) throw new InterruptedException("Weighted filtering interrupted; no completed estimate")
      val (state,parent) = draw()
      val logWeight = evidence.logLikelihood(state)
      require(logWeight.isFinite || logWeight == Double.NegativeInfinity, "Log likelihood must be finite or -Infinity")
      val s = evidence.score(logWeight)
      require(s.isFinite && s >= 0 && s <= 1, "Success score must be in [0,1]")
      score += s
      candidates += ((state,parent,logWeight))
    }
    if (Thread.currentThread().isInterrupted) throw new InterruptedException("Weighted filtering interrupted; no completed estimate")
    val attempts = candidates.size
    val met = score >= config.scoreTarget
    val reason = if (attempts == config.minCandidates) StopReason.InitialBatch
      else if (met) StopReason.ScoreTarget else StopReason.BudgetExhausted
    if (reason == StopReason.ScoreTarget) candidates.remove(attempts-1)
    val logs = candidates.iterator.map(_._3).toVector
    val rawHealth = ParticleFilterHealth.assess(logs, config.health)
    val health = rawHealth.copy(recommendations = if (rawHealth.issues.isEmpty) Vector.empty else Vector(
      "Inspect weight concentration, evidence consistency and ancestor diversity; a score target is not precision.",
      "Review the fixed score scale, work cap and bootstrap proposal if repeatedly exhausted.",
      "Retain failed outcomes in evidence calibration; do not retry until success."))
    val report = StepReport(attempts, candidates.size, score, reason, met,
      Option.when(hasParents)(candidates.iterator.filter(_._3.isFinite).map(_._2).toSet.size), health)
    val peak = logs.max
    if (peak == Double.NegativeInfinity)
      Step(candidates.iterator.map(_._1).toVector, Vector.empty, report, Double.NegativeInfinity)
    else {
      val shifted = logs.map(w => math.exp(w-peak))
      val sum = shifted.sum
      val weights = shifted.map(_/sum)
      if (logs.zip(weights).exists((l,w) => l.isFinite && w == 0.0))
        throw new ArithmeticException("Positive normalized weight underflow; unsupported dynamic range")
      val increment = peak + (math.log(sum)-math.log(candidates.size.toDouble))
      if (!increment.isFinite) throw new ArithmeticException("Evidence increment not representable")
      Step(candidates.iterator.map(_._1).toVector, weights, report, increment)
    }
  }

  /** Strict upper-bound search prevents a zero-weight candidate being selected at u=0. */
  private[figaro] def ancestor(cdf: Vector[Double], u: Double): Int = {
    var lo = 0
    var hi = cdf.size-1
    while (lo < hi) {
      val mid = lo+(hi-lo)/2
      if (u < cdf(mid)) hi = mid else lo = mid+1
    }
    lo
  }
}

/** Sequential owned lifecycle; custom proposals and automatic recovery are not supported. */
final class WeightedBoundedParticleFilter[S] private (model: BoundedParticleFilter.Model[S],
  val config: WeightedBoundedParticleFilter.Config) {
  import WeightedBoundedParticleFilter.*
  require(model != null && config != null, "Model and configuration required")
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
  /** Last completed report, including zero-weight collapse; absent after callback/numerical failure. */
  def lastReport: Option[StepReport] = { access(); diagnostic }
  /** Latest valid posterior; throws before start, after failure, or after close. */
  def current: Update[S] = {
    access(); require(!terminal && latest.isDefined, "No current posterior")
    latest.get
  }
  /** Draw the model's initial state and weight it with this first evidence policy. Call once. */
  def start(evidence: Evidence[S]): Update[S] = {
    access(); require(!terminal && latest.isEmpty, "Filter already started, closed or failed")
    update(evidence, true)
  }
  /** Sample independent weighted ancestors, propagate, then apply the next evidence policy. */
  def advance(evidence: Evidence[S]): Update[S] = { current; update(evidence, false) }
  private def update(evidence: Evidence[S], initial: Boolean): Update[S] = {
    require(evidence != null, "Evidence policy required")
    running = true
    diagnostic = None
    try RandomContext.withRandom(rng) {
      val old = latest
      // One CDF per step; force its last positive entry to 1, never a trailing zero.
      val cdf = old.map { r =>
        val cumulative = r.weights.scanLeft(0.0)(_+_).tail
        val last = r.weights.lastIndexWhere(_ > 0)
        cumulative.zipWithIndex.map((x,i) => if (i >= last) 1.0 else math.min(x,1.0))
      }.getOrElse(Vector.empty)
      val result = step(config, !initial)(() => {
        if (initial) (model.initial(), -1)
        else {
          val i = ancestor(cdf, rng.nextDouble())
          (model.transition(old.get.particles(i)), i)
        }
      }, evidence)
      diagnostic = Some(result.report)
      if (result.weights.isEmpty) throw new NoViableParticles(result.report)
      val logEvidence = old.map(_.logEvidence).getOrElse(0.0) + result.logIncrement
      if (!logEvidence.isFinite) throw new ArithmeticException("Accumulated log evidence not representable")
      val answer = Update(result.values, result.weights, logEvidence, result.report)
      latest = Some(answer)
      answer
    } catch {
      case e: InterruptedException => terminal = true; latest = None; diagnostic = None; throw e
      case e: NoViableParticles => terminal = true; latest = None; throw e
      case scala.util.control.NonFatal(e) => terminal = true; latest = None; diagnostic = None; throw e
    } finally running = false
  }
  /** Release retained values. Terminal, idempotent, owner-thread only. */
  def close(): Unit = { access(); latest = None; terminal = true }
}
