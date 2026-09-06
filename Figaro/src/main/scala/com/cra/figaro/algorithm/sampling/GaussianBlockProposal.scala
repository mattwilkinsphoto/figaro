package com.cra.figaro.algorithm.sampling

import com.cra.figaro.language.Universe
import com.cra.figaro.library.atomic.continuous.AtomicNormal
import com.cra.figaro.util.random

/** Fixed, symmetric Gaussian random-walk proposals for blocks of constant-parameter Normals.
  * Covariance describes increments in VALUE units, not prior randomness or posterior uncertainty.
  * This changes proposals only, not the target distribution, and performs no online adaptation.
  */
object GaussianBlockProposal {
  /** Build a chain-owned joint proposal. Use as the whole scheme or a DisjointScheme choice.
    * @param elements nonempty distinct active AtomicNormal instances in one universe; subclasses,
    *                 observations, interventions, compound Normals, and temporary elements are unsupported
    * @param covariance finite exactly symmetric positive-definite increment covariance in element order;
    *                   diagonal entries are variances, not standard deviations
    * @return a fixed ProposalScheme that updates every member and makes one joint MH decision
    * @throws IllegalArgumentException for invalid targets/covariance or unrepresentable factorization;
    *         execution also rejects foreign/inactive/modified targets and sequential composition
    * @example `GaussianBlockProposal(Vector(x, y), Vector(Vector(0.5, 0.49), Vector(0.49, 0.5)))`
    */
  def apply(elements: Seq[AtomicNormal], covariance: Seq[Seq[Double]]): ProposalScheme = {
    require(elements != null && elements.nonEmpty && elements.forall(_ != null), "A nonempty Normal block is required")
    val targets = elements.toVector
    require(targets.distinct.size == targets.size, "Block targets must be distinct")
    val universe = targets.head.universe
    validateTargets(targets, universe)
    val n = targets.size
    require(covariance != null && covariance.size == n &&
      covariance.forall(row => row != null && row.size == n && row.forall(_.isFinite)), "Invalid covariance dimensions/entries")
    val matrix = covariance.map(_.toVector).toVector
    require(matrix.indices.forall(i => matrix(i)(i) > 0 &&
      matrix.indices.forall(j => matrix(i)(j) == matrix(j)(i))), "Covariance must be symmetric with positive diagonal")
    // Factor the correlation matrix first to avoid an arbitrary absolute pivot tolerance across units.
    val sd = matrix.indices.map(i => math.sqrt(matrix(i)(i))).toVector
    val lower = Array.ofDim[Double](n, n)
    for (i <- 0 until n; j <- 0 to i) {
      var residual = (matrix(i)(j) / sd(i)) / sd(j)
      for (k <- 0 until j) residual -= lower(i)(k) * lower(j)(k)
      require(residual.isFinite && (i != j || residual > 0), "Covariance is not numerically positive definite")
      lower(i)(j) = if (i == j) math.sqrt(residual) else residual / lower(j)(j)
      require(lower(i)(j).isFinite, "Covariance factor outside numeric range")
    }
    val factor = Vector.tabulate(n)(i => Vector.tabulate(i + 1)(j => lower(i)(j) * sd(i)))
    require(factor.forall(_.forall(_.isFinite)) && factor.indices.forall(i => factor(i)(i) > 0),
      "Covariance factor outside numeric range")
    GaussianBlockScheme(new Block(targets, universe, factor))
  }

  private def validateTargets(targets: Vector[AtomicNormal], universe: Universe): Unit =
    require(targets.forall(e => e.getClass == classOf[AtomicNormal] && (e.universe eq universe) &&
      e.active && !e.isTemporary && e.observation.isEmpty && e.intervention.isEmpty &&
      e.mean.isFinite && e.variance.isFinite && e.variance > 0),
      "Block requires active permanent unobserved, unintervened constant-parameter Normals in the sampler universe")

  private[sampling] final class Block(val targets: Vector[AtomicNormal], val universe: Universe,
    factor: Vector[Vector[Double]]) {
    // Return new standard-Normal randomness and its joint log-prior ratio, without mutating the graph.
    def prepare(samplerUniverse: Universe): (Vector[Double], Double) = {
      require(samplerUniverse eq universe, "Block belongs to another sampler universe")
      validateTargets(targets, universe)
      require(targets.forall(e => e.randomness.isFinite && e.value.isFinite), "Non-finite current block state")
      val noise = Vector.fill(targets.size)(random.nextGaussian())
      val proposed = targets.indices.map { i =>
        val delta = factor(i).indices.iterator.map(j => factor(i)(j) * noise(j)).sum
        targets(i).randomness + delta / targets(i).standardDeviation
      }.toVector
      require(proposed.indices.forall(i => proposed(i).isFinite && targets(i).generateValue(proposed(i)).isFinite),
        "Block proposal outside numeric range")
      val logPriorRatio = proposed.indices.iterator.map { i =>
        -0.5 * (proposed(i) - targets(i).randomness) * (proposed(i) + targets(i).randomness)
      }.sum
      require(logPriorRatio.isFinite, "Block prior ratio outside numeric range")
      (proposed, logPriorRatio)
    }
  }
}
