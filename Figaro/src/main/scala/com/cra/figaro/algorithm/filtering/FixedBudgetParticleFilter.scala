package com.cra.figaro.algorithm.filtering

import com.cra.figaro.util.{RandomContext, SamplingRandom}

/** Fixed-N filtering (INF-15/16). Independent of the bounded stopping estimator.
  * States must be detached and immutable; callbacks use the scoped Figaro RNG.
  */
object FixedBudgetParticleFilter {
  /** A decision based only on the previous completed population. No default threshold. */
  enum Policy {
    case Always
    case WhenEssBelow(fraction: Double)
  }
  /** Stratified refines bounded comparisons for finite-bit draws; log-tree retains log masses. */
  enum Method { case LogTreeMultinomial, Stratified }
  enum Decision { case Initial, Resampled, CarriedWeights }

  /** Exactly particles state draws on each successful step, not a wall-clock bound.
    * @param particles fixed population size, 2 through 1,000,000; stratified decimal refinement limited to 4096
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
  /** Stratified work on a successful selection; zero for initialization, carry or log-tree.
    * Refinement uses the same draw, never a retry. Example: result.report.selection.maxPrecision.
    */
  final case class SelectionReport(refinedDecisions: Long = 0L, decimalTables: Int = 0,
    maxPrecision: Int = 0, exactTies: Long = 0L)

  final case class StepReport(stateDraws: Int, likelihoodCalls: Int, ancestorSelections: Int,
    decision: Decision, previousRelativeEss: Option[Double], contributingParents: Option[Int],
    health: ParticleFilterHealth.Report, selection: SelectionReport = SelectionReport())

  /** Successful-step proposal work; also retained on genuine zero-weight collapse.
    * Counts top-level density callbacks, not their internal mixture/component work.
    * @param targetDensityCalls N with guidance, otherwise zero
    * @param proposalDensityCalls N with guidance, otherwise zero
    * @param auxiliaryRequested whether advanceAuxiliary was called
    * @param auxiliary prepared selection diagnostics; None when no resampling occurred
    * @example `filter.lastProposalReport.map(_.targetDensityCalls)`
    */
  final case class ProposalReport(targetDensityCalls: Int, proposalDensityCalls: Int,
    auxiliaryRequested: Boolean, auxiliary: Option[AuxiliaryParticleProposal.Report])

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
  /** Terminal unresolved decision or explicit resource/domain limit, not genuine zero support.
    * @param reason numerical or resource limit reached; inspect rather than retrying until success
    */
  final class StratifiedPrecisionLoss(val reason: String = "Legacy CDF interval loss") extends ArithmeticException(
    "Stratified selection refused: " + reason +
    ". Review the numerical domain or explicitly choose LogTreeMultinomial for a new declared run; no fallback or redraw occurred.")

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
  // Historical diagnostic control only. Never used for runtime ancestor selection.
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
  private[figaro] def ancestors(logs: Vector[Double], method: Method, rng: java.util.Random): Vector[Int] =
    select(logs,method,rng)._1

  private def select(logs: Vector[Double], method: Method, rng: java.util.Random): (Vector[Int], SelectionReport) = {
    val n = logs.size
    method match {
      case Method.LogTreeMultinomial =>
        val tree = new LogParticleWeights.Tree(logs)
        val indices = Vector.tabulate(n) { _ =>
          interrupted()
          tree.draw(LogParticleWeights.logUniform(rng))
        }
        (indices, SelectionReport())
      case Method.Stratified =>
        try {
          val table = new DecisionStratified.Table(logs)
          val indices = table.draw(rng)
          (indices, SelectionReport(table.refinedDecisions,table.tablesBuilt,table.maxPrecision,table.exactTies))
        } catch {
          case e: DecisionStratified.Unresolved => throw new StratifiedPrecisionLoss(e.reason)
        }
    }
  }
  private def interrupted(): Unit =
    if (Thread.currentThread().isInterrupted) throw new InterruptedException("Fixed-budget filtering interrupted")
}

/** Sequential, thread-confined filter with explicit opt-in guidance. No mutable graph hooks. */
final class FixedBudgetParticleFilter[S] private (model: BoundedParticleFilter.Model[S],
  val config: FixedBudgetParticleFilter.Config) {
  import FixedBudgetParticleFilter.*
  require(model != null && config != null, "Model and explicit configuration required")
  private val owner = Thread.currentThread()
  private val rng = SamplingRandom.seeded(config.seed, config.algorithm)
  private var latest: Option[Update[S]] = None
  private var diagnostic: Option[StepReport] = None
  private var proposalDiagnostic: Option[ProposalReport] = None
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
  /** Proposal work for the last completed step, including collapse; None after other failure.
    * No arguments. Example: filter.lastProposalReport.flatMap(_.auxiliary).
    */
  def lastProposalReport: Option[ProposalReport] = { access(); proposalDiagnostic }
  /** Draw N initial states and apply one log likelihood per state. Call once. */
  def start(logLikelihood: S => Double): Update[S] = {
    access(); require(!terminal && latest.isEmpty, "Filter already started, closed or failed")
    update(logLikelihood)
  }
  /** Select ancestors once or carry weights, propagate N times, and apply new evidence. */
  def advance(logLikelihood: S => Double): Update[S] = { current; update(logLikelihood) }

  /** Initialize from a normalized proposal with full p/q correction.
    * @param logLikelihood finite or -Infinity log likelihood, including its normalizer
    * @param proposal frozen normalized target/proposal laws with matching support and measure
    * @return initial weighted Update; model callbacks are not called
    * @example `filter.start(likelihood, kernel)`
    */
  def start(logLikelihood: S => Double, proposal: WeightedParticleProposal.Kernel[S]): Update[S] = {
    access(); require(!terminal && latest.isEmpty, "Filter already started, closed or failed")
    require(proposal != null, "Proposal required")
    update(logLikelihood, Some(proposal))
  }
  /** Propagate with p/q correction; preserve the explicit resampling policy.
    * @param logLikelihood new normalized log likelihood
    * @param proposal frozen target/proposal laws for this observation
    * @return weighted Update; carry steps retain previous weights
    * @example `filter.advance(likelihood, kernel)`
    */
  def advance(logLikelihood: S => Double, proposal: WeightedParticleProposal.Kernel[S]): Update[S] = {
    current; require(proposal != null, "Proposal required")
    update(logLikelihood, Some(proposal))
  }
  /** Observation-informed ancestor selection ONLY when the explicit policy resamples.
    * On carry steps the look-ahead is not evaluated and no ancestor correction is applied.
    * @param logLikelihood new normalized log likelihood
    * @param lookAhead frozen log multipliers and defensive ancestor fraction
    * @param proposal optional child guidance; None uses the model's true transition draw
    * @return weighted Update with full W_i/r_i and optional p/q correction
    * @example `filter.advanceAuxiliary(likelihood, lookAhead, Some(kernel))`
    */
  def advanceAuxiliary(logLikelihood: S => Double, lookAhead: AuxiliaryParticleProposal.LookAhead[S],
    proposal: Option[WeightedParticleProposal.Kernel[S]] = None): Update[S] = {
    current
    require(lookAhead != null && proposal != null && proposal.forall(_ != null), "Valid look-ahead/proposal required")
    update(logLikelihood, proposal, Some(lookAhead))
  }
  private def update(logLikelihood: S => Double, proposal: Option[WeightedParticleProposal.Kernel[S]] = None,
    lookAhead: Option[AuxiliaryParticleProposal.LookAhead[S]] = None): Update[S] = {
    require(logLikelihood != null, "Log likelihood required")
    running = true
    diagnostic = None
    proposalDiagnostic = None
    try RandomContext.withRandom(rng) {
      interrupted()
      val old = latest
      val previousEss = old.map(x => relativeEss(x.logWeights))
      val resample = previousEss.exists(shouldResample(config.policy, _))
      val n = config.particles
      val auxiliary = if (resample) lookAhead.map(p =>
        AuxiliaryParticleProposal.prepareLogs(old.get.particles,old.get.logWeights,p)) else None
      val (indices,selection) = if (resample)
        select(auxiliary.fold(old.get.logWeights)(_.logProbabilities), config.method, rng)
        else (Vector.range(0,n),SelectionReport())
      val base = if (old.isEmpty || resample) Vector.fill(n)(-math.log(n)) else old.get.logWeights
      val parentBase = auxiliary.fold(base)(a => reweight(base,indices.map(a.logCorrections)))
      val corrections = Array.fill(n)(0.0)
      val values = Vector.tabulate(n) { i =>
        interrupted()
        proposal match {
          case None => old.fold(model.initial())(p => model.transition(p.particles(indices(i))))
          case Some(q) =>
            val (value,correction) = q.draw(old.map(_.particles(indices(i))))
            corrections(i) = correction
            value
        }
      }
      val likelihoods = values.map { x => interrupted(); logLikelihood(x) }
      interrupted()
      val correctedBase = if (proposal.isDefined) reweight(parentBase,corrections.toVector) else parentBase
      val raw = reweight(correctedBase, likelihoods)
      val rawHealth = ParticleFilterHealth.assess(raw, config.health)
      val health = rawHealth.copy(recommendations = if (rawHealth.issues.isEmpty) Vector.empty else Vector(
        "Inspect likelihood consistency, weight concentration and contributing parents; ESS is not precision or mode coverage.",
        "Predeclare a larger new run or review model/proposal support. Resampling cannot recover extinct histories; do not retain only successful retries."))
      val report = StepReport(n,n,if (resample) n else 0,
        if (old.isEmpty) Decision.Initial else if (resample) Decision.Resampled else Decision.CarriedWeights,
        previousEss, old.map(_ => raw.indices.filter(i => raw(i).isFinite).map(indices).distinct.size), health, selection)
      proposalDiagnostic = Some(ProposalReport(if (proposal.isDefined) n else 0,
        if (proposal.isDefined) n else 0,lookAhead.isDefined,auxiliary.map(_.report)))
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
      case e: InterruptedException => terminal = true; latest = None; diagnostic = None; proposalDiagnostic = None; throw e
      case scala.util.control.NonFatal(e) => terminal = true; latest = None; diagnostic = None; proposalDiagnostic = None; throw e
    } finally { running = false }
  }
  /** Idempotent terminal cleanup; owner thread only. Existing detached snapshots remain valid. */
  def close(): Unit = { access(); latest = None; terminal = true }
}
