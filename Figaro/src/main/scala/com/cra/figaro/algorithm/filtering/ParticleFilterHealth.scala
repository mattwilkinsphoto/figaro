package com.cra.figaro.algorithm.filtering

/** Pre-resampling diagnostics, not a posterior-accuracy or missing-mode certificate. */
object ParticleFilterHealth {
  enum Severity { case Warning, Danger }
  enum Code { case NoSurvivors, DominantParticle, LowEss, LowEfficiency }
  enum Eligibility { case Unknown, IndicatorContract }

  /** Engineering warning thresholds, not calibrated error probabilities. */
  final case class Config(minEss: Double = 100, minRelativeEss: Double = .01,
    warnMaxWeight: Double = .1, dangerMaxWeight: Double = .5) {
    require(minEss.isFinite && minEss > 0 && minRelativeEss.isFinite &&
      minRelativeEss > 0 && minRelativeEss <= 1, "Invalid ESS thresholds")
    require(warnMaxWeight > 0 && warnMaxWeight < dangerMaxWeight && dangerMaxWeight <= 1,
      "Invalid dominant-weight thresholds")
  }
  final case class Issue(code: Code, severity: Severity, message: String)
  final case class Report(attempts: Int, positiveWeights: Int, ess: Option[Double],
    relativeEss: Option[Double], maxWeight: Option[Double], eligibility: Eligibility,
    issues: Vector[Issue], recommendations: Vector[String])

  /** Assess raw log weights BEFORE resampling. Finite tiny weights are not genuine zeros.
    * @param logWeights finite values or negative infinity; nonempty
    * @param config configurable warning thresholds
    * @param eligibility declared model contract, never inferred from realized weights
    * @return detached report; `assess(Vector(0.0, Double.NegativeInfinity))`
    */
  def assess(logWeights: Seq[Double], config: Config = Config(),
    eligibility: Eligibility = Eligibility.Unknown): Report = {
    require(config != null && eligibility != null && logWeights != null && logWeights.nonEmpty,
      "Weights, thresholds and eligibility required")
    require(logWeights.forall(w => w.isFinite || w == Double.NegativeInfinity), "Invalid log weights")
    val n = logWeights.size
    val positive = logWeights.count(_.isFinite)
    if (positive == 0) summarize(n, 0, None, None, config, eligibility)
    else {
      val peak = logWeights.max
      val shifted = logWeights.iterator.map(w => math.exp(w - peak)).toVector
      val sum = shifted.sum
      val ess = math.min(positive.toDouble, sum * sum / shifted.iterator.map(w => w*w).sum)
      summarize(n, positive, Some(ess), Some(1.0/sum), config, eligibility)
    }
  }

  private[filtering] def summarize(n: Int, positive: Int, ess: Option[Double], max: Option[Double],
    config: Config, eligibility: Eligibility): Report = {
    val issues = Vector.newBuilder[Issue]
    if (positive == 0) issues += Issue(Code.NoSurvivors, Severity.Danger,
      "No posterior is available; this does not establish impossible evidence.")
    max.foreach { w =>
      if (w >= config.warnMaxWeight) issues += Issue(Code.DominantParticle,
        if (w >= config.dangerMaxWeight) Severity.Danger else Severity.Warning,
        "One candidate carries a large fraction of weight; resampling does not restore lost diversity.")
    }
    ess.foreach { e =>
      if (e < config.minEss) issues += Issue(Code.LowEss, Severity.Warning, "ESS is below the configured warning minimum.")
      if (e/n < config.minRelativeEss) issues += Issue(Code.LowEfficiency, Severity.Warning, "Very little candidate work contributes weight.")
    }
    val found = issues.result()
    val recommendations = if (found.isEmpty) Vector.empty else Vector(
      "Check evidence consistency, proposal support and ancestry diversity; counts do not establish precision.",
      if (eligibility == Eligibility.IndicatorContract)
        "Use or increase an explicit bounded-adaptive candidate budget; consider better proposals if repeatedly exhausted."
      else "For an explicit Boolean indicator model, consider BoundedParticleFilter with a maximum candidate budget. Eligibility is unverified; ordinary likelihood observations are not automatically interchangeable.",
      "Do not discard failed runs and retain only successful retries when estimating evidence.")
    Report(n, positive, ess, ess.map(_/n), max, eligibility, found, recommendations)
  }
}
