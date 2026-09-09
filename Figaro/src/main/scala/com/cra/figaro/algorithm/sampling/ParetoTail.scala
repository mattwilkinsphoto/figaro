/* Generalized Pareto empirical-Bayes fit adapted from ArviZ v0.22.0,
 * arviz/stats/stats.py (_gpdfit and tail selection in _psislw).
 * Copyright ArviZ developers. Licensed under Apache License 2.0;
 * see ArviZ-LICENSE.txt and FigaroAttributions.txt.
 * Modified for Figaro: Scala implementation, diagnostic only (no smoothing),
 * bounded work, explicit missing-result states and cooperative interruption.
 */
package com.cra.figaro.algorithm.sampling

/** Tail-shape diagnostic for independent importance ratios; not a moment-existence proof. */
object ParetoTail {
  enum Status { case Estimated, TooFewSamples, DegenerateTail, NumericalFailure, NoPositiveWeights }
  /** Fit details; scale is relative to the maximum input weight, not original weight units. */
  final case class Result(status: Status, k: Option[Double], scale: Option[Double], tailSize: Int,
    threshold: Option[Double])

  private[sampling] def interrupted(): Unit =
    if (Thread.currentThread().isInterrupted) throw new InterruptedException("Inference health interrupted")

  /** Fit the upper tail without modifying or smoothing the supplied weights.
    * @param logWeights raw per-draw log importance ratios; negative infinity means zero weight
    * @param maxSamples positive input-size cap, default one million
    * @return empirical-Bayes k and finite-sample warning threshold, or explicit unavailable state
    * @example `ParetoTail.fit(Vector.tabulate(1000)(i => -0.3 * math.log((i + 0.5) / 1000)))`
    */
  def fit(logWeights: Seq[Double], maxSamples: Int = 1000000): Result = {
    interrupted()
    require(logWeights != null && maxSamples > 0 && logWeights.size <= maxSamples, "Invalid weight input or size cap")
    require(logWeights.forall(x => x.isFinite || x == Double.NegativeInfinity), "Weights must be finite or negative infinity")
    val n = logWeights.size
    val threshold = if (n > 1) Some(math.min(0.7, 1 - 1 / math.log10(n.toDouble))) else None
    def absent(s: Status, m: Int = 0) = Result(s, None, None, m, threshold)
    if (n == 0 || logWeights.forall(_ == Double.NegativeInfinity)) return absent(Status.NoPositiveWeights)
    val m = math.ceil(math.min(n / 5.0, 3 * math.sqrt(n.toDouble))).toInt
    if (m < 5 || m >= n) return absent(Status.TooFewSamples, m)
    val top = logWeights.max
    val ordered = logWeights.iterator.map(_ - top).toArray
    java.util.Arrays.sort(ordered)
    interrupted()
    val cutoff = math.max(ordered(n - m - 1), math.log(java.lang.Double.MIN_NORMAL))
    val base = math.exp(cutoff)
    val excess = ordered.iterator.filter(_ > cutoff).map(x => math.exp(x) - base).toArray
    val size = excess.length
    if (size < 5 || excess.head <= 0 || excess.last <= excess.head)
      return absent(Status.DegenerateTail, size)
    // ArviZ empirical-Bayes candidate grid and weak prior on k (Zhang-Stephens fit).
    val grid = 30 + math.sqrt(size.toDouble).toInt
    val quarter = excess((size / 4.0 + 0.5).toInt - 1)
    val candidates = Array.tabulate(grid) { j =>
      interrupted()
      val b = (1 - math.sqrt(grid / (j + 0.5))) / (3 * quarter) + 1 / excess.last
      val k = excess.iterator.map(x => math.log1p(-b * x)).sum / size
      val score = size * (math.log(-b / k) - k - 1)
      (b, score)
    }
    if (!candidates.forall((b, score) => b.isFinite && score.isFinite)) return absent(Status.NumericalFailure, size)
    // Stable equivalent of upstream's pairwise exponential normalization.
    val peak = candidates.map(_._2).max
    val weights = candidates.map((_, score) => math.exp(score - peak))
    val total = weights.sum
    val retained = candidates.zip(weights).filter((_, w) => w / total >= 10 * math.ulp(1.0))
    val mass = retained.map(_._2).sum
    val b = retained.iterator.map { case ((value, _), w) => value * (w / mass) }.sum
    val k = excess.iterator.map(x => math.log1p(-b * x)).sum / size
    val scale = -k / b
    val shrunk = (size * k + 5) / (size + 10)
    interrupted()
    if (!shrunk.isFinite || !scale.isFinite || scale <= 0) absent(Status.NumericalFailure, size)
    else Result(Status.Estimated, Some(shrunk), Some(scale), size, threshold)
  }
}
