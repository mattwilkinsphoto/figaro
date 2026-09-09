package com.cra.figaro.algorithm.sampling

import com.cra.figaro.algorithm.sampling.parallel.McmcDiagnostics
import com.cra.figaro.language.Element

/** Opt-in, diagnostic-only assessment; never stops, retries, smooths or changes a sampler. */
object InferenceHealth {
  enum Status { case InsufficientEvidence, Warning, Danger, ChecksPassed }
  enum Code {
    case TooFewSamples, NoPositiveWeights, WeightCollapse, DominantWeight, LowEss, LowEfficiency
    case TailUnavailable, ParetoWarning, ParetoDanger, DependenceUnsupported, ConstantObservable
    case NumericalRange, PrecisionNotMet, TooFewChains, ShortChains, RhatUnavailable, RhatHigh
    case LowBulkEss, LowTailEss, LowMeanEss, McseUnavailable, SourceWarning
  }
  final case class Issue(code: Code, severity: Status, message: String)
  /** Version-one engineering thresholds, not calibrated global false-alarm probabilities.
    * @param maxSamples maximum supplied draws, including all MCMC chains
    * @param minSamples minimum independent importance draws
    * @param minEss minimum raw weight ESS
    * @param minRelativeEss minimum ESS / draw count (efficiency warning)
    * @param warnWeight largest-weight warning threshold
    * @param dangerWeight largest-weight danger threshold
    * @param minDrawsPerChain minimum MCMC trace length
    * @param minEssPerChain pooled bulk/tail/mean ESS requirement divided by chain count
    * @param maxRhat maximum rank/folded split R-hat
    * @param maxMeanMcse optional positive absolute error target for the supplied observable mean
    */
  final case class Config(maxSamples: Int = 1000000, minSamples: Int = 100, minEss: Double = 100,
    minRelativeEss: Double = 0.01, warnWeight: Double = 0.1, dangerWeight: Double = 0.5,
    minDrawsPerChain: Int = 1000, minEssPerChain: Double = 100, maxRhat: Double = 1.01,
    maxMeanMcse: Option[Double] = None) {
    require(maxSamples > 0 && minSamples >= 25 && minSamples <= maxSamples && minDrawsPerChain >= 4,
      "Invalid sample limits")
    require(minEss.isFinite && minEss > 0 && minEssPerChain.isFinite && minEssPerChain > 0,
      "Positive finite ESS thresholds required")
    require(minRelativeEss > 0 && minRelativeEss <= 1 && warnWeight > 0 && warnWeight < dangerWeight && dangerWeight <= 1,
      "Invalid weight/efficiency thresholds")
    require(maxRhat.isFinite && maxRhat > 1 && maxMeanMcse != null &&
      maxMeanMcse.forall(x => x.isFinite && x > 0), "Invalid R-hat/MCSE thresholds")
  }
  /** rawMcse is the ordinary plug-in error, potentially grossly optimistic; not a reliable interval. */
  final case class WeightSummary(samples: Int, positiveWeights: Int, ess: Option[Double],
    relativeEss: Option[Double], maxWeight: Option[Double], pareto: Option[ParetoTail.Result],
    mean: Option[Double], rawMcse: Option[Double])
  sealed trait Report { def status: Status; def issues: Vector[Issue]; def policy: Config }
  final case class ImportanceReport(status: Status, issues: Vector[Issue], policy: Config,
    diagnostics: WeightSummary, independentDraws: Boolean, precisionRequested: Boolean) extends Report
  final case class McmcReport(status: Status, issues: Vector[Issue], policy: Config,
    diagnostics: Option[McmcDiagnostics.Summary], precisionRequested: Boolean) extends Report

  private def status(issues: Vector[Issue]): Status =
    if (issues.exists(_.severity == Status.Danger)) Status.Danger
    else if (issues.exists(_.severity == Status.InsufficientEvidence)) Status.InsufficientEvidence
    else if (issues.nonEmpty) Status.Warning else Status.ChecksPassed

  /** Assess raw per-draw weights, optionally for one aligned scalar observable.
    * @param logWeights log importance ratios BEFORE aggregation, normalization or resampling
    * @param independentDraws caller assertion of independent proposal draws; false disables tail/error assessment
    * @param values optional finite scalar values, exactly one per raw weight; event indicators are allowed
    * @param config warning policy and bounded input size
    * @return report retaining all reasons; passed checks never certify missed-mode coverage
    * @example `InferenceHealth.importance(logWeights, independentDraws = true, values = Some(samples))`
    */
  def importance(logWeights: Seq[Double], independentDraws: Boolean, values: Option[Seq[Double]] = None,
    config: Config = Config()): ImportanceReport = {
    ParetoTail.interrupted()
    require(config != null && logWeights != null && values != null && !values.contains(null), "Inputs and policy required")
    val n = logWeights.size
    require(n <= config.maxSamples && logWeights.forall(x => x.isFinite || x == Double.NegativeInfinity), "Invalid raw weights")
    require(values.forall(v => v.size == n && v.forall(_.isFinite)), "Finite aligned observable required")
    require(config.maxMeanMcse.isEmpty || values.isDefined, "MCSE target requires observable values")
    val issues = Vector.newBuilder[Issue]
    def add(c: Code, s: Status, m: String): Unit = issues += Issue(c, s, m)
    if (n < config.minSamples) add(Code.TooFewSamples, Status.InsufficientEvidence, "Too few independent draws for assessment")
    if (!independentDraws) add(Code.DependenceUnsupported, Status.InsufficientEvidence, "Dependent importance draws need a separate dependence-aware assessment")
    val positive = logWeights.count(_.isFinite)
    var ess: Option[Double] = None
    var largest: Option[Double] = None
    var estimate: Option[Double] = None
    var mcse: Option[Double] = None
    if (positive == 0) add(Code.NoPositiveWeights, if (n == 0) Status.InsufficientEvidence else Status.Danger,
      "No positive weight: no posterior estimate is supported")
    else {
      val peak = logWeights.max
      val shifted = logWeights.iterator.map(w => math.exp(w - peak)).toArray
      val sum = shifted.sum
      val weights = shifted.map(_ / sum)
      val e = math.min(n.toDouble, 1 / weights.iterator.map(w => w*w).sum)
      val max = weights.max
      ess = Some(e); largest = Some(max)
      if (max >= config.dangerWeight) add(Code.WeightCollapse, Status.Danger, "One draw carries a dangerous fraction of total weight")
      else if (max >= config.warnWeight) add(Code.DominantWeight, Status.Warning, "A small number of draws may dominate the answer")
      if (e < config.minEss) add(Code.LowEss, Status.Warning, "Raw weight ESS is below the configured minimum")
      if (e/n < config.minRelativeEss) add(Code.LowEfficiency, Status.Warning, "Very little nominal sampling work contributes effective weight")
      values.foreach { xs =>
        val data = xs.toArray
        val scale = math.max(1.0, data.iterator.map(math.abs).max)
        val meanScaled = data.indices.iterator.map(i => weights(i) * (data(i)/scale)).sum
        val mean = meanScaled * scale
        val error = math.sqrt(data.indices.iterator.map { i =>
          val z = weights(i) * (data(i)/scale - meanScaled); z*z
        }.sum) * scale
        estimate = Option.when(mean.isFinite)(mean)
        if (independentDraws) mcse = Option.when(error.isFinite)(error)
        if (data.forall(_ == data.head) || error == 0)
          add(Code.ConstantObservable, Status.InsufficientEvidence, "Zero observed variation cannot establish rare-event or mean precision")
        if (!mean.isFinite || !error.isFinite) add(Code.NumericalRange, Status.InsufficientEvidence, "Mean/error outside finite numerical range")
      }
    }
    val tail = if (independentDraws) Some(ParetoTail.fit(logWeights, config.maxSamples)) else None
    tail.foreach { t =>
      t.k match {
        case Some(k) if k >= 0.7 => add(Code.ParetoDanger, Status.Danger, "Heavy fitted weight tail: ordinary error bars and convergence may be unreliable")
        case Some(k) if t.threshold.exists(k >= _) => add(Code.ParetoWarning, Status.Warning, "Pareto k exceeds the sample-size-dependent warning threshold")
        case Some(_) => ()
        case None => add(Code.TailUnavailable, Status.InsufficientEvidence, s"Tail shape unavailable: ${t.status}; not a safe-tail certificate")
      }
    }
    config.maxMeanMcse.foreach { target =>
      if (mcse.isEmpty) add(Code.McseUnavailable, Status.InsufficientEvidence, "Requested mean precision cannot be assessed")
      else if (mcse.get > target) add(Code.PrecisionNotMet, Status.Warning, "Raw mean MCSE exceeds the requested absolute target")
    }
    ParetoTail.interrupted()
    val all = issues.result()
    ImportanceReport(status(all), all, config, WeightSummary(n, positive, ess, ess.map(_/n), largest, tail, estimate, mcse),
      independentDraws, config.maxMeanMcse.nonEmpty)
  }

  /** Assess one observable's ordered chains using existing rank/folded R-hat and ESS.
    * @param chains finite rectangular traces; fewer than two chains/four draws returns insufficient evidence
    * @param config warning thresholds, optional absolute mean-MCSE target and aggregate draw cap
    * @return diagnostic report; does not alter McmcPrecision or any stopping decision
    * @example `InferenceHealth.mcmc(result.chains.map(_.draws("query")))`
    */
  def mcmc(chains: Seq[Seq[Double]], config: Config = Config()): McmcReport = {
    ParetoTail.interrupted()
    require(config != null && chains != null && chains.forall(_ != null), "Chains and policy required")
    require(chains.iterator.map(_.size.toLong).sum <= config.maxSamples, "Aggregate draw cap exceeded")
    require(chains.forall(_.forall(_.isFinite)) && (chains.isEmpty || chains.forall(_.size == chains.head.size)), "Finite rectangular chains required")
    val issues = Vector.newBuilder[Issue]
    def add(c: Code, s: Status, m: String): Unit = issues += Issue(c, s, m)
    val count = chains.size
    val n = chains.headOption.map(_.size).getOrElse(0)
    if (count < 2) add(Code.TooFewChains, Status.InsufficientEvidence, "At least two chains required; dispersed starts remain essential")
    if (n < config.minDrawsPerChain) add(Code.ShortChains, Status.InsufficientEvidence, "Trace length below the assessment minimum")
    val summary = if (count >= 2 && n >= 4) Some(McmcDiagnostics.summarize(chains)) else None
    summary.foreach { s =>
      if (s.rHat.isEmpty) add(Code.RhatUnavailable, Status.InsufficientEvidence, "R-hat unavailable for degenerate traces")
      else if (s.rHat.get > config.maxRhat) add(Code.RhatHigh, Status.Danger, "Between/within-chain disagreement exceeds the configured R-hat threshold")
      for ((ess, code) <- Vector((s.bulkEss, Code.LowBulkEss), (s.tailEss, Code.LowTailEss), (s.meanEss, Code.LowMeanEss))) {
        if (ess.isEmpty) add(code, Status.InsufficientEvidence, "ESS unavailable; inspect constant or poorly explored observables")
        else if (ess.get < config.minEssPerChain*count) add(code, Status.Warning, "ESS below the configured per-chain requirement")
      }
      if (!s.mcseMean.exists(x => x.isFinite && x > 0)) add(Code.McseUnavailable, Status.InsufficientEvidence, "Positive finite mean MCSE unavailable")
      if (config.maxMeanMcse.exists(target => s.mcseMean.exists(_ > target)))
        add(Code.PrecisionNotMet, Status.Warning, "Mean MCSE exceeds the requested absolute target")
      s.warnings.foreach(w => add(Code.SourceWarning, Status.Warning, w))
    }
    ParetoTail.interrupted()
    val all = issues.result()
    McmcReport(status(all), all, config, summary, config.maxMeanMcse.nonEmpty)
  }

  /** Run an opt-in, blocking Importance sampler and retain raw per-draw diagnostics for one query.
    * @param numSamples positive draw budget within config.maxSamples; memory is O(numSamples)
    * @param target caller-owned active element; its universe is not cleared
    * @param project deterministic finite scalar projection of the query (e.g. an event indicator)
    * @param config assessment policy; reports warnings without changing samples or stopping early
    * @return detached report after owned algorithm cleanup; caller controls RNG scoping
    * @example `InferenceHealth.runImportance(10000, coin, (b: Boolean) => if (b) 1.0 else 0.0)`
    */
  def runImportance[T](numSamples: Int, target: Element[T], project: T => Double,
    config: Config = Config()): ImportanceReport = {
    require(config != null && numSamples > 0 && numSamples <= config.maxSamples && target != null &&
      target.active && project != null, "Valid draw budget, active target, projection and policy required")
    val logs = Vector.newBuilder[Double]
    val values = Vector.newBuilder[Double]
    val requested = numSamples
    val algorithm = new Importance(target.universe, target) with OneTimeProbQuerySampler {
      val numSamples = requested
      override protected def checkSamplingInterrupted(): Unit = ParetoTail.interrupted()
      override protected def updateWeightSeenForTarget[A](s: Sample, seen: WeightSeen[A]): Unit = {
        val value = project(s._2(target).asInstanceOf[T])
        require(value.isFinite, "Finite projection required")
        logs += s._1; values += value
        super.updateWeightSeenForTarget(s, seen)
      }
    }
    var primary: Throwable = null
    try { algorithm.start(); importance(logs.result(), true, Some(values.result()), config) }
    catch { case error: Throwable => primary = error; throw error }
    finally {
      try algorithm.kill()
      catch { case cleanup: Throwable => if (primary != null) primary.addSuppressed(cleanup) else throw cleanup }
    }
  }
}
