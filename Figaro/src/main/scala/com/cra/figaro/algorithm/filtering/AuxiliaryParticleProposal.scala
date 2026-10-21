package com.cra.figaro.algorithm.filtering

/** Frozen observation-informed ancestor selection for the weighted bounded filter (INF-11).
  * This changes the sampling law, not the target posterior. No history recovery is implied.
  */
object AuxiliaryParticleProposal {
  /** Look-ahead multiplier eta(parent), evaluated once per positive-weight parent.
    * @param logMultiplier pure deterministic log score, finite or -Infinity; need not be normalized
    * @param priorFraction share of ordinary weighted ancestor sampling, in [0,1]; default .1
    * @example `LookAhead[Double](x => -x*x, priorFraction = .1)`
    * Zero fraction requires finite scores for ALL positive-weight parents. A defensive
    * fraction preserves their support even if a rough look-ahead assigns zero scores.
    */
  final case class LookAhead[S](logMultiplier: S => Double, priorFraction: Double = .1) {
    require(logMultiplier != null && priorFraction.isFinite && priorFraction >= 0 && priorFraction <= 1,
      "Look-ahead and defensive fraction in [0,1] required")
  }

  /** Pre-production diagnostics, not a posterior-accuracy or mode-coverage certificate.
    * @param evaluations actual look-ahead calls (zero-weight parents are skipped)
    * @param previousEss ESS of the previous normalized population
    * @param selectionEss ESS of the proposed ancestor probabilities
    * @param usedPriorFallback all active scores were zero; ordinary ancestor weights used
    * @param priorFraction requested defensive fraction
    */
  final case class Report(evaluations: Int, previousEss: Double, selectionEss: Double,
    usedPriorFallback: Boolean, priorFraction: Double)

  private[figaro] final case class Prepared(probabilities: Vector[Double],
    logCorrections: Vector[Double], report: Report)

  private[figaro] def prepare[S](particles: Vector[S], weights: Vector[Double],
    policy: LookAhead[S]): Prepared = {
    require(policy != null && particles.nonEmpty && particles.size == weights.size)
    require(weights.forall(w => w.isFinite && w >= 0) && math.abs(weights.sum-1) <= 1e-10,
      "Normalized nonnegative ancestor weights required")
    val active = weights.indices.filter(weights(_) > 0).toVector
    require(active.nonEmpty)
    // A fraction of one is an explicit no-look-ahead policy, without callback cost.
    val scores = if (policy.priorFraction == 1) Vector.fill(weights.size)(0.0) else
      weights.indices.map { i =>
        if (Thread.currentThread().isInterrupted) throw new InterruptedException("Auxiliary preparation interrupted")
        if (weights(i) == 0) Double.NegativeInfinity
        else WeightedParticleProposal.checked(policy.logMultiplier(particles(i)))
      }.toVector
    if (policy.priorFraction == 0)
      require(active.forall(i => scores(i).isFinite),
        "Undefended look-ahead must be positive on every positive-weight parent")
    val peak = active.map(scores).max
    val fallback = peak == Double.NegativeInfinity
    val probabilities = if (fallback || policy.priorFraction == 1) weights else {
      // Common log-score shifts cancel before adding small log weights.
      val logs = weights.indices.map(i => if (weights(i) == 0) Double.NegativeInfinity
        else math.log(weights(i)) + (scores(i)-peak)).toVector
      val logPeak = logs.max
      val terms = logs.map(l => math.exp(l-logPeak))
      val sum = terms.sum
      val mixed = weights.indices.map(i => policy.priorFraction*weights(i) +
        (1-policy.priorFraction)*(terms(i)/sum)).toVector
      val total = mixed.sum
      mixed.map(_/total)
    }
    if (active.exists(i => probabilities(i) == 0))
      throw new ArithmeticException("Positive auxiliary ancestor probability underflow")
    // Correct using the entire mixture law, never the selected mixture component.
    val corrections = weights.indices.map(i => if (weights(i) == 0) 0.0
      else math.log(weights(i))-math.log(probabilities(i))).toVector
    if (!corrections.forall(_.isFinite)) throw new ArithmeticException("Ancestor correction overflow")
    Prepared(probabilities, corrections, Report(
      if (policy.priorFraction == 1) 0 else active.size,
      1/weights.map(w => w*w).sum, 1/probabilities.map(w => w*w).sum, fallback, policy.priorFraction))
  }

  /** Refuse a positive interval lost to cumulative floating-point rounding. */
  private[figaro] def cdf(probabilities: Vector[Double]): Vector[Double] = {
    var cumulative = 0.0
    val last = probabilities.lastIndexWhere(_ > 0)
    probabilities.zipWithIndex.map { (p,i) =>
      val next = if (i >= last) 1.0 else math.min(1.0,cumulative+p)
      if (p > 0 && next <= cumulative)
        throw new ArithmeticException("Auxiliary CDF lost a positive ancestor interval")
      cumulative = next
      next
    }
  }
}
