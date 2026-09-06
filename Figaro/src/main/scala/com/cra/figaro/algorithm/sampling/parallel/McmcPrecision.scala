package com.cra.figaro.algorithm.sampling.parallel

import org.apache.commons.math3.distribution.NormalDistribution

/** Fixed-width stopping for scalar means, with batch-means MCSE and mixing safeguards.
  * Inspired by Flegal-Gong's relative standard-deviation rule. Coverage is asymptotic under
  * a functional CLT and consistent variance estimation, not a finite-run convergence guarantee.
  */
object McmcPrecision {
  /** Precision and checking policy. All requested observables must pass.
    * @param relativeTolerance target FULL confidence-interval width as a fraction of posterior SD
    * @param absoluteTolerance optional FULL-width target in observable units, overriding relativeTolerance
    * @param confidence simultaneous nominal confidence level; Bonferroni adjustment across observables
    * @param minDrawsPerChain minimum retained draws before stopping is allowed, at least 100
    * @param checkEvery retained draws per chain between checks, positive
    * @param minBatches minimum complete sqrt(n)-sized batches per chain, at least 10
    * @param maxRHat maximum finite rank/folded split R-hat, greater than one
    * @param minEssPerChain minimum pooled bulk AND mean ESS divided by chain count
    */
  final case class Config(relativeTolerance: Double = 0.05, absoluteTolerance: Option[Double] = None,
    confidence: Double = 0.95, minDrawsPerChain: Int = 1000, checkEvery: Int = 500,
    minBatches: Int = 20, maxRHat: Double = 1.01, minEssPerChain: Double = 100) {
    require(relativeTolerance.isFinite && relativeTolerance > 0, "Positive relative tolerance required")
    require(absoluteTolerance != null && absoluteTolerance.forall(x => x.isFinite && x > 0), "Invalid absolute tolerance")
    require(confidence > 0 && confidence < 1, "Confidence must be in (0,1)")
    require(minDrawsPerChain >= 100 && checkEvery > 0 && minBatches >= 10, "Invalid checking schedule")
    require(maxRHat.isFinite && maxRHat > 1 && minEssPerChain.isFinite && minEssPerChain > 0, "Invalid mixing safeguards")
  }

  /** One observable's stopping assessment; None means precision could not be estimated safely.
    * @param diagnostics existing rank/ESS diagnostics, including warnings
    * @param batchMeansMcse correlation-adjusted standard error of the pooled mean
    * @param fullWidth full Normal-approximation confidence-interval width (without penalty)
    * @param targetWidth configured absolute or posterior-SD-relative width
    * @param penalty posterior SD / total draws, added to width to discourage premature stopping
    * @param batchesPerChain number of complete batches used per chain
    * @param criteriaMet true only when precision, minimum work, and mixing guards all pass
    */
  final case class Assessment(diagnostics: McmcDiagnostics.Summary, batchMeansMcse: Option[Double],
    fullWidth: Option[Double], targetWidth: Double, penalty: Double, batchesPerChain: Int, criteriaMet: Boolean)

  /** Evaluate ordered, equal-length scalar traces; does not alter them.
    * @param chains at least two finite chains of at least four draws
    * @param config precision policy
    * @param simultaneousQueries positive count of observables checked together, for Bonferroni confidence adjustment
    * @return diagnostics and a conditional precision assessment; no proof of convergence
    * @example `McmcPrecision.evaluate(traces, McmcPrecision.Config(relativeTolerance = 0.1))`
    */
  def evaluate(chains: Seq[Seq[Double]], config: Config, simultaneousQueries: Int = 1): Assessment = {
    require(config != null && simultaneousQueries > 0, "Policy and positive query count required")
    val diagnostics = McmcDiagnostics.summarize(chains)
    val n = chains.head.size
    val m = chains.size
    val batchSize = math.sqrt(n.toDouble).toInt
    val batches = n / batchSize
    val total = n.toDouble * m
    val target = config.absoluteTolerance.getOrElse(config.relativeTolerance * diagnostics.standardDeviation)
    val penalty = diagnostics.standardDeviation / total
    // Use the lower tail to avoid rounding 1 - tiny_probability to 1.
    val critical = -new NormalDistribution(0, 1).inverseCumulativeProbability((1 - config.confidence) / (2.0 * simultaneousQueries))
    val scale = java.lang.Math.scalb(1.0, java.lang.Math.getExponent(chains.iterator.flatMap(_.iterator).map(math.abs).max))
    val estimates = chains.map { chain =>
      val values = chain.toArray
      val means = Array.tabulate(batches) { batch =>
        var sum = 0.0
        var i = batch * batchSize
        while (i < (batch + 1) * batchSize) { sum += values(i) / scale; i += 1 }
        sum / batchSize
      }
      val center = means.sum / batches
      batchSize * means.iterator.map(x => (x - center) * (x - center)).sum / (batches - 1)
    }
    val mcse = if (estimates.forall(x => x.isFinite && x > 0)) {
      Some(math.sqrt(estimates.sum / (m.toDouble * m * n)) * scale).filter(x => x.isFinite && x > 0)
    } else None
    val width = mcse.map(2 * critical * _).filter(_.isFinite)
    val enough = n >= config.minDrawsPerChain && batches >= config.minBatches
    val mixed = diagnostics.rHat.exists(x => x.isFinite && x <= config.maxRHat) &&
      diagnostics.bulkEss.exists(_ >= config.minEssPerChain * m) &&
      diagnostics.meanEss.exists(_ >= config.minEssPerChain * m)
    val precise = target.isFinite && target > 0 && penalty.isFinite && width.exists(_ + penalty <= target)
    Assessment(diagnostics, mcse, width, target, penalty, batches, enough && mixed && precise)
  }
}
