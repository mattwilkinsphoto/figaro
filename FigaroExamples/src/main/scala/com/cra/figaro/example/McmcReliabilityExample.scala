package com.cra.figaro.example

import com.cra.figaro.algorithm.sampling.ProposalScheme
import com.cra.figaro.algorithm.sampling.parallel.{McmcPrecision, MultiChainMetropolisHastings as MH}
import com.cra.figaro.language.*
import com.cra.figaro.library.atomic.continuous.Normal

/** Inspect precision failures, compare MCSE estimates, and monitor a reparameterized target's moments. */
object McmcReliabilityExample {
  private def build(u: Universe, index: Int): MH.Model = {
    val z = Normal(0, 1)(using "", u)
    val e = Normal(0, 1)(using "", u)
    val y = Apply(z, e, (a: Double, b: Double) => 0.4 * (a * a - 1) + 0.5 * b)(using "", u)
    MH.Model(Vector(MH.Observable("x", z)(identity), MH.Observable("y", y)(identity),
      MH.Observable("ySquared", y)(v => v * v),
      MH.Observable("xTail", z)(v => if (math.abs(v) > 2) 1.0 else 0.0)), Some(ProposalScheme(z, e)))
  }

  /** Run a deliberately inadequate precision budget and a well-exploring control.
    * @param args no arguments
    * @return Unit; prints named failed checks, both MCSE estimates, used width, and final control diagnostics
    * @throws IllegalArgumentException for arguments or an unexpected example outcome; sampler failures propagate
    * @example `McmcReliabilityExample.main(Array.empty)`
    */
  def main(args: Array[String]): Unit = {
    require(args.isEmpty, "No arguments expected")
    val config = MH.Config(drawsPerChain = 4000, warmUp = 500, seed = 73101)
    val insufficient = MH.runUntilPrecise(config, McmcPrecision.Config(relativeTolerance = 1e-6))(build)
    require(insufficient.reason == MH.StopReason.MaxDrawsReached)
    insufficient.assessments.toVector.sortBy(_._1).foreach { (name, a) =>
      println(s"$name: failed=${a.failureReasons}; batch=${a.batchMeansMcse}; spectral=${a.diagnostics.mcseMean}; used=${a.mcseUsed}; width=${a.fullWidth}; target=${a.targetWidth}")
    }
    val control = MH.runUntilPrecise(config, McmcPrecision.Config(relativeTolerance = 0.2))(build)
    require(control.reason == MH.StopReason.PrecisionReached)
    require(control.assessments.values.forall(_.failureReasons.isEmpty))
    println(s"Reparameterized control: ${control.reason}; ${control.result.diagnostics}")
    println("Passing these checks is a conditional precision assessment, not proof that unseen tails or modes were explored.")
  }
}
