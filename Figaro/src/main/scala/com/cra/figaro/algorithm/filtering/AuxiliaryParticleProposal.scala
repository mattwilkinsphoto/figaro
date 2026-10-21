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
    logCorrections: Vector[Double], report: Report, logProbabilities: Vector[Double])

  private[figaro] def prepare[S](particles: Vector[S], weights: Vector[Double],
    policy: LookAhead[S]): Prepared = {
    require(policy != null && particles.nonEmpty && particles.size == weights.size)
    require(weights.forall(w => w.isFinite && w >= 0) && math.abs(weights.sum-1) <= 1e-10,
      "Normalized nonnegative ancestor weights required")
    prepareLogs(particles,weights.map(math.log),policy)
  }

  private[figaro] def prepareLogs[S](particles: Vector[S], logs: Vector[Double],
    policy: LookAhead[S]): Prepared = {
    require(policy != null && particles.nonEmpty && particles.size == logs.size)
    require(logs.forall(l => l.isFinite || l == Double.NegativeInfinity) &&
      math.abs(logs.map(math.exp).sum-1) <= 1e-10, "Normalized log ancestor weights required")
    val active = logs.indices.filter(i => logs(i).isFinite).toVector
    require(active.nonEmpty)
    // A fraction of one is an explicit no-look-ahead policy, without callback cost.
    val scores = if (policy.priorFraction == 1) Vector.fill(logs.size)(0.0) else
      logs.indices.map { i =>
        if (Thread.currentThread().isInterrupted) throw new InterruptedException("Auxiliary preparation interrupted")
        if (!logs(i).isFinite) Double.NegativeInfinity
        else WeightedParticleProposal.checked(policy.logMultiplier(particles(i)))
      }.toVector
    if (policy.priorFraction == 0)
      require(active.forall(i => scores(i).isFinite),
        "Undefended look-ahead must be positive on every positive-weight parent")
    val peak = active.map(scores).max
    val fallback = peak == Double.NegativeInfinity
    val selectedLogs = if (fallback || policy.priorFraction == 1) logs else {
      val tilted = logs.indices.map { i =>
        if (!logs(i).isFinite || !scores(i).isFinite) Double.NegativeInfinity
        else {
          val value = logs(i)+(scores(i)-peak)
          if (!value.isFinite) throw new ArithmeticException("Auxiliary log weight overflow")
          value
        }
      }.toVector
      val normalized = LogParticleWeights.normalize(tilted).logs
      val logPrior = math.log(policy.priorFraction)
      val logTilt = math.log1p(-policy.priorFraction)
      val mixed = logs.indices.map(i => LogParticleWeights.add(
        logPrior+logs(i),logTilt+normalized(i))).toVector
      LogParticleWeights.normalize(mixed).logs
    }
    // Correct using the entire mixture law, never the selected mixture component.
    val corrections = logs.indices.map(i => if (!logs(i).isFinite) 0.0
      else logs(i)-selectedLogs(i)).toVector
    if (!corrections.forall(_.isFinite)) throw new ArithmeticException("Ancestor correction overflow")
    val probabilities = selectedLogs.map(math.exp)
    Prepared(probabilities, corrections, Report(
      if (policy.priorFraction == 1) 0 else active.size,
      1/logs.map(l => math.exp(2*l)).sum, 1/probabilities.map(w => w*w).sum,
      fallback, policy.priorFraction), selectedLogs)
  }
}
