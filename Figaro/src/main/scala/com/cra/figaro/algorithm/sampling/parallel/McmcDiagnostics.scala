package com.cra.figaro.algorithm.sampling.parallel

import org.apache.commons.math3.distribution.NormalDistribution
import org.apache.commons.math3.transform.{DftNormalization, FastFourierTransformer, TransformType}

/** Diagnostics for equally sized, ordered scalar chains; never a proof of convergence. */
object McmcDiagnostics {
  /** Summary of one scalar observable. Missing diagnostics mean insufficient/degenerate information.
    * @param mean pooled mean, using every supplied draw
    * @param standardDeviation pooled sample standard deviation (not Monte Carlo error)
    * @param rHat maximum rank-normalized and folded split R-hat, when defined
    * @param bulkEss rank-normalized split-chain ESS, conservatively capped at split draw count
    * @param tailEss minimum ESS of pooled 5%/95% quantile indicators, when both are defined
    * @param meanEss raw split-chain ESS used for mean error, not bulk ESS
    * @param mcseMean estimated standard error of the mean; requires meaningful chain mixing
    * @param warnings diagnostic concerns; an empty vector does not certify convergence
    */
  final case class Summary(mean: Double, standardDeviation: Double, rHat: Option[Double],
    bulkEss: Option[Double], tailEss: Option[Double], meanEss: Option[Double],
    mcseMean: Option[Double], warnings: Vector[String])

  /** Summarize chains without concatenating away their order or identity.
    * @param chains at least two equal-length chains with at least four finite draws each
    * @return scalar summary; odd-length chains lose their middle draw only for split diagnostics
    * @throws IllegalArgumentException for invalid dimensions or non-finite input
    * @example `McmcDiagnostics.summarize(Vector(Vector(1.0, 2.0, 1.0, 3.0), Vector(2.0, 1.0, 3.0, 2.0)))`
    */
  def summarize(chains: Seq[Seq[Double]]): Summary = {
    require(chains.size >= 2, "Diagnostics require at least two chains")
    val n = chains.head.size
    require(n >= 4 && chains.forall(_.size == n), "Chains must have equal lengths of at least four")
    require(chains.forall(_.forall(_.isFinite)), "Diagnostic draws must be finite")
    val raw = chains.map(_.toArray).toArray
    val flat = raw.flatten
    // Binary scaling avoids overflow without perturbing exact symmetric/tied
    // values as division by an arbitrary maximum would do before folding.
    val scale = java.lang.Math.scalb(1.0, java.lang.Math.getExponent(flat.iterator.map(math.abs).max))
    val scaled = flat.map(_ / scale)
    val center = average(scaled)
    val sd = math.sqrt(variance(scaled)) * scale
    val warnings = Vector.newBuilder[String]
    if (n < 20) warnings += "Fewer than 20 draws per chain: diagnostics are very unstable"
    if (n % 2 != 0) warnings += "Middle draw omitted from each odd-length chain for split diagnostics"
    if (raw.exists(x => x.forall(_ == x.head))) warnings += "At least one chain is constant; inspect stuck or deterministic observables"
    val splitRaw = split(raw)
    val ranked = rankNormalize(splitRaw)
    val sortedScaled = scaled.sorted
    val lowerMedian = sortedScaled((flat.length - 1) / 2)
    val upperMedian = sortedScaled(flat.length / 2)
    // Preserve the exact tie between the two central order statistics. Subtracting
    // a rounded median can otherwise spuriously give them different folded ranks.
    val folded = raw.map(_.map { x =>
      val value = x / scale
      math.abs((value - lowerMedian) / 2 + (value - upperMedian) / 2)
    })
    val rhats = Vector(rhat(ranked), rhat(rankNormalize(split(folded)))).flatten
    val rh = if (rhats.isEmpty) None else Some(rhats.max)
    val bulk = ess(ranked)
    val sorted = flat.sorted
    val tails = Vector(0.05, 0.95).map { p =>
      val cut = quantile(sorted, p)
      ess(split(raw.map(_.map(x => if (x <= cut) 1.0 else 0.0))))
    }
    val tail = if (tails.forall(_.isDefined)) Some(tails.flatten.min) else None
    val meanEss = ess(splitRaw.map(_.map(_ / scale)))
    val mcse = meanEss.map(e => math.sqrt(variance(scaled) / e) * scale).filter(_.isFinite)
    if (rh.isEmpty) warnings += "R-hat unavailable: degenerate traces"
    if (rh.exists(_ > 1.01)) warnings += "R-hat exceeds 1.01: chains may not have mixed"
    if (bulk.isEmpty || bulk.exists(_ < 100.0 * raw.length)) warnings += "Bulk ESS unavailable or below 100 per chain"
    if (tail.isEmpty || tail.exists(_ < 100.0 * raw.length)) warnings += "Tail ESS unavailable or below 100 per chain (discrete tails can be constant)"
    if (!sd.isFinite || mcse.isEmpty) warnings += "Standard deviation or mean error is unavailable or outside numeric range"
    Summary(center * scale, sd, rh, bulk, tail, meanEss, mcse, warnings.result())
  }

  private def average(x: Array[Double]): Double = x.head + x.iterator.map(_ - x.head).sum / x.length
  private def variance(x: Array[Double]): Double = {
    val mean = average(x)
    x.iterator.map(v => (v - mean) * (v - mean)).sum / (x.length - 1)
  }
  private def split(chains: Array[Array[Double]]): Array[Array[Double]] =
    chains.flatMap(x => Array(x.take(x.length / 2), x.takeRight(x.length / 2)))
  private def quantile(sorted: Array[Double], p: Double): Double = {
    val position = p * (sorted.length - 1)
    val lo = position.toInt
    val weight = position - lo
    // Convex combination avoids overflow in subtracting opposite-sign extreme values.
    sorted(lo) * (1 - weight) + sorted(math.min(lo + 1, sorted.length - 1)) * weight
  }
  private def rankNormalize(chains: Array[Array[Double]]): Array[Array[Double]] = {
    val values = chains.flatten
    val order = values.indices.toArray.sortBy(values(_))
    val result = new Array[Double](values.length)
    val normal = new NormalDistribution(0, 1)
    var first = 0
    while (first < order.length) {
      var end = first + 1
      while (end < order.length && values(order(end)) == values(order(first))) end += 1
      val rank = (first + 1.0 + end) / 2.0
      val score = normal.inverseCumulativeProbability((rank - 0.375) / (values.length + 0.25))
      var index = first
      while (index < end) { result(order(index)) = score; index += 1 }
      first = end
    }
    result.grouped(chains.head.length).map(_.toArray).toArray
  }
  private def rhat(chains: Array[Array[Double]]): Option[Double] = {
    val n = chains.head.length
    val within = chains.map(variance).sum / chains.length
    val between = variance(chains.map(average))
    if (within == 0.0) {
      if (between == 0.0) None else Some(Double.PositiveInfinity)
    } else Some(math.sqrt((n - 1.0) / n + between / within))
  }

  /** Biased autocovariances with zero padding, equivalent to sum((x_i-mean)(x_{i+lag}-mean))/N. */
  private[parallel] def autocovariance(values: Array[Double]): Array[Double] = {
    var length = 1
    while (length.toLong < 2L * values.length) {
      require(length <= (1 << 28), "Trace too large for FFT diagnostics")
      length *= 2
    }
    val centered = new Array[Double](length)
    val mean = average(values)
    var i = 0
    while (i < values.length) { centered(i) = values(i) - mean; i += 1 }
    val fft = new FastFourierTransformer(DftNormalization.STANDARD)
    val spectrum = fft.transform(centered, TransformType.FORWARD)
    val power = spectrum.map(z => z.multiply(z.conjugate()))
    fft.transform(power, TransformType.INVERSE).take(values.length).map(_.getReal / values.length)
  }
  private def ess(chains: Array[Array[Double]]): Option[Double] = {
    val n = chains.head.length
    if (n < 3) return None
    val covariance = chains.map(autocovariance)
    val within = covariance.map(_(0)).sum / chains.length * n / (n - 1.0)
    val totalVariance = within * (n - 1.0) / n + variance(chains.map(average))
    if (!(totalVariance > 0.0) || !totalVariance.isFinite) return None
    def rho(lag: Int): Double = if (lag == 0) 1.0
      else 1.0 - (within - covariance.map(_(lag)).sum / chains.length) / totalVariance
    var lag = 0
    var previous = Double.PositiveInfinity
    var sum = 0.0
    var positive = true
    // Geyer's initial positive, then monotone paired autocorrelation sequence.
    while (lag + 1 < n && positive) {
      val pair = rho(lag) + rho(lag + 1)
      if (pair <= 0.0) positive = false
      else { previous = math.min(previous, pair); sum += previous; lag += 2 }
    }
    val tau = math.max(1.0, -1.0 + 2.0 * sum)
    Some(chains.length.toDouble * n / tau)
  }
}
