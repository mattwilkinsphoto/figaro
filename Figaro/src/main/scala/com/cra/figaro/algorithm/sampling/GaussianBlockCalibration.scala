package com.cra.figaro.algorithm.sampling

import com.cra.figaro.algorithm.sampling.parallel.{McmcDiagnostics, MultiChainMetropolisHastings as MH}
import com.cra.figaro.library.atomic.continuous.AtomicNormal

/** Estimate a fixed value-space proposal from a separate, discarded multi-chain pilot.
  * No sampler is mutated and no adaptation occurs during production sampling.
  * Scalar mixing checks cannot certify covariance accuracy or discovery of every mode.
  */
object GaussianBlockCalibration {
  /** Explicit regularization and pilot acceptance policy.
    * @param varianceMultiplier positive finite multiplier on the regularized covariance; 1 is not an optimality claim
    * @param diagonalShrinkage fraction in (0, 1] shrinking off-diagonals toward zero; diagonal variances are unchanged
    * @param minDrawsPerChain at least 20 post-warm-up pilot draws per chain
    * @param maxRHat finite maximum rank/folded split R-hat, at least 1
    * @param minEssPerChain positive minimum bulk, tail, and raw-mean ESS divided by chain count
    * @param maxDimension positive block-size limit bounding quadratic storage/work per draw
    */
  final case class Config(varianceMultiplier: Double = 1.0, diagonalShrinkage: Double = 0.05,
    minDrawsPerChain: Int = 500, maxRHat: Double = 1.01, minEssPerChain: Double = 100.0,
    maxDimension: Int = 64) {
    require(varianceMultiplier.isFinite && varianceMultiplier > 0, "Variance multiplier must be positive and finite")
    require(diagonalShrinkage.isFinite && diagonalShrinkage > 0 && diagonalShrinkage <= 1,
      "Diagonal shrinkage must be in (0, 1]")
    require(minDrawsPerChain >= 20 && maxDimension > 0, "Invalid pilot size limits")
    require(maxRHat.isFinite && maxRHat >= 1 && minEssPerChain.isFinite && minEssPerChain > 0,
      "Invalid diagnostic thresholds")
  }

  /** Detached immutable fit. Covariances are in names order and VALUE units, not prior randomness units.
    * Empirical covariance pools within-chain scatter with chains * (draws - 1) degrees of freedom;
    * between-chain mean offsets do not inflate the proposal. Diagnostics are recomputed from traces.
    */
  final class Fit private[GaussianBlockCalibration] (
    val names: Vector[String], val empiricalCovariance: Vector[Vector[Double]],
    val covariance: Vector[Vector[Double]], val diagnostics: Map[String, McmcDiagnostics.Summary],
    val chains: Int, val drawsPerChain: Int, val config: Config) {
    /** Bind the frozen matrix to fresh production elements, by pilot observable name.
      * @param targets exact name-to-AtomicNormal map; values must represent the same coordinates/units as the pilot
      * @return a new chain-owned fixed Gaussian proposal; no pilot model objects are retained
      * @throws IllegalArgumentException for missing/extra names or unsupported block targets
      * @example `fit.proposal(Map("x" -> x, "y" -> y))`
      */
    def proposal(targets: Map[String, AtomicNormal]): ProposalScheme = {
      require(targets != null && targets.keySet == names.toSet, "Production targets must match every pilot name exactly")
      GaussianBlockProposal(names.map(targets), covariance)
    }
  }

  /** Fit a proposal from raw-value pilot observables; discard the pilot and start fresh production chains.
    * @param pilot completed fixed-budget multi-chain run; its reported diagnostics are not trusted/reused
    * @param names distinct observable names selecting raw Normal VALUES in the desired block order
    * @param config explicit covariance regularization, scaling, and diagnostic acceptance settings
    * @return immutable empirical/regularized covariance, recomputed diagnostics, and pilot/configuration metadata
    * @throws IllegalArgumentException for invalid traces, inadequate mixing, degenerate coordinates, or numeric range failure;
    *         there is no automatic fallback or automatic retry with relaxed thresholds
    * @example `GaussianBlockCalibration.fit(pilot, Vector("x", "y"), GaussianBlockCalibration.Config())`
    */
  def fit(pilot: MH.Result, names: Seq[String], config: Config = Config()): Fit = {
    require(pilot != null && config != null && names != null && names.nonEmpty &&
      names.forall(n => n != null && n.nonEmpty) && names.distinct.size == names.size, "Invalid pilot, names, or config")
    val keys = names.toVector
    require(keys.size <= config.maxDimension, "Block exceeds calibration dimension limit")
    require(pilot.chains != null && pilot.chains.size >= 2 &&
      pilot.chains.forall(c => c != null && c.draws != null && keys.forall(k => c.draws.get(k).exists(_ != null))),
      "At least two pilot chains with all requested observables are required")
    val count = pilot.chains.size
    val n = pilot.chains.head.draws(keys.head).size
    require(n >= config.minDrawsPerChain && n > keys.size,
      "Insufficient pilot draws: need configured minimum and more draws per chain than block dimensions")
    require(pilot.chains.forall(c => keys.forall(k => c.draws(k).size == n && c.draws(k).forall(_.isFinite))),
      "Pilot columns/chains must have equal lengths and finite values")
    require(pilot.chains.forall(c => keys.forall(k => c.draws(k).exists(_ != c.draws(k).head))),
      "A pilot coordinate is constant within a chain; inspect exploration before calibration")
    val summaries = keys.map { key =>
      checkInterrupted()
      val summary = McmcDiagnostics.summarize(pilot.chains.map(_.draws(key)))
      val ess = Vector(summary.bulkEss, summary.tailEss, summary.meanEss)
      require(summary.rHat.exists(r => r.isFinite && r <= config.maxRHat) &&
        ess.forall(_.exists(e => e.isFinite && e / count >= config.minEssPerChain)),
        s"Pilot diagnostics rejected '$key': R-hat=${summary.rHat}, bulk/tail/mean ESS=$ess; improve pilot exploration or budget")
      key -> summary
    }.toMap

    val d = keys.size
    // Binary scaling keeps scatter computation stable across heterogeneous value units.
    val scales = keys.map { key =>
      val largest = pilot.chains.iterator.flatMap(_.draws(key)).map(math.abs).max
      java.lang.Math.scalb(1.0, java.lang.Math.getExponent(largest))
    }
    val scatter = Array.ofDim[Double](d, d)
    pilot.chains.foreach { chain =>
      val means = keys.indices.map { j =>
        val column = chain.draws(keys(j))
        val origin = column.head / scales(j)
        origin + column.iterator.map(v => v / scales(j) - origin).sum / n
      }.toVector
      for (row <- 0 until n) {
        if (row % 256 == 0) checkInterrupted()
        val centered = keys.indices.map(j => chain.draws(keys(j))(row) / scales(j) - means(j))
        for (i <- 0 until d; j <- 0 to i) scatter(i)(j) += centered(i) * centered(j)
      }
    }
    val degrees = count.toDouble * (n - 1)
    val empirical = Vector.tabulate(d, d) { (i, j) =>
      val normalized = scatter(math.max(i, j))(math.min(i, j)) / degrees
      // Use identical multiplication order on both sides to preserve exact symmetry.
      normalized * scales(math.min(i, j)) * scales(math.max(i, j))
    }
    require(empirical.forall(_.forall(_.isFinite)) && empirical.indices.forall(i => empirical(i)(i) > 0),
      "Empirical covariance outside numeric range; rescale model coordinates")
    val covariance = Vector.tabulate(d, d) { (i, j) =>
      empirical(i)(j) * (if (i == j) 1.0 else 1.0 - config.diagonalShrinkage) * config.varianceMultiplier
    }
    GaussianBlockProposal.factorCovariance(covariance, d)
    new Fit(keys, empirical, covariance, summaries, count, n, config)
  }

  private def checkInterrupted(): Unit =
    if (Thread.currentThread().isInterrupted) throw new InterruptedException("Proposal calibration cancelled")
}
