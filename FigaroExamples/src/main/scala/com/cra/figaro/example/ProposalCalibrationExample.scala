package com.cra.figaro.example

import com.cra.figaro.algorithm.sampling.{GaussianBlockCalibration as Calibration, ProposalScheme}
import com.cra.figaro.algorithm.sampling.parallel.{McmcPrecision, MultiChainMetropolisHastings as MH}
import com.cra.figaro.language.*
import com.cra.figaro.library.atomic.continuous.Normal

/** Separate pilot, inspectable frozen fit, and fresh production chains. */
object ProposalCalibrationExample {
  private def build(fit: Option[Calibration.Fit])(u: Universe, index: Int): MH.Model = {
    val x = Normal(0, 1)(using "", u)
    val y = Normal(0, 1)(using "", u)
    val difference = Apply(x, y, (a: Double, b: Double) => a - b)(using "", u)
    difference.addLogConstraint((v: Double) => -0.5 * math.pow(v / 0.3, 2))
    val proposal = fit.fold(ProposalScheme(x, y))(_.proposal(Map("x" -> x, "y" -> y)))
    MH.Model(Vector(MH.Observable("x", x)(identity), MH.Observable("y", y)(identity)), Some(proposal))
  }

  /** Run all three stages, printing the fitted matrix and independent production diagnostics.
    * @param args no arguments
    * @return Unit; pilot draws are discarded and never pooled with production estimates
    * @throws IllegalArgumentException if pilot diagnostics are inadequate; increase pilot work or improve its proposal
    * @example `ProposalCalibrationExample.main(Array.empty)`
    */
  def main(args: Array[String]): Unit = {
    require(args.isEmpty, "No arguments expected")
    val pilot = MH.run(MH.Config(drawsPerChain = 6000, warmUp = 2000, seed = 71001))(build(None))
    val fit = Calibration.fit(pilot, Vector("x", "y"))
    println(s"Frozen proposal covariance: ${fit.covariance}; settings: ${fit.config}")
    println(s"Pilot diagnostics: ${fit.diagnostics}")
    // Different root seed and newly built universes; production has its own warm-up.
    val config = MH.Config(drawsPerChain = 12000, warmUp = 2000, seed = 81001)
    val fixed = MH.run(config)(build(Some(fit)))
    println(s"Fixed production diagnostics: ${fixed.diagnostics}")
    val stopped = MH.runUntilPrecise(config, McmcPrecision.Config(relativeTolerance = 0.15,
      minDrawsPerChain = 2000, checkEvery = 2000))(build(Some(fit)))
    println(s"Precision production: ${stopped.reason}; ${stopped.assessments}")
    println(s"Pilot cost: ${pilot.elapsedSeconds}s; fixed production: ${fixed.elapsedSeconds}s; stopped production: ${stopped.result.elapsedSeconds}s")
  }
}
