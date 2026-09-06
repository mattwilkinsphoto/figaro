package com.cra.figaro.example

import com.cra.figaro.algorithm.sampling.{DisjointScheme, GaussianBlockProposal, ProposalScheme}
import com.cra.figaro.algorithm.sampling.parallel.{McmcPrecision, MultiChainMetropolisHastings as MH}
import com.cra.figaro.language.*
import com.cra.figaro.library.atomic.continuous.Normal

/** Runnable counterparts of the three blocked-proposal guide patterns. */
object BlockedProposalExample {
  private def build(blocked: Boolean)(u: Universe, index: Int): MH.Model = {
    val x = Normal(0, 1)(using "", u)
    val y = Normal(0, 1)(using "", u)
    val difference = Apply(x, y, (a: Double, b: Double) => a - b)(using "", u)
    difference.addLogConstraint((v: Double) => -0.5 * math.pow(v / 0.15, 2))
    val v = 2.8 * 1.0225 / 2.0225
    val c = 2.8 / 2.0225
    val proposal = if (blocked)
      Some(GaussianBlockProposal(Vector(x, y), Vector(Vector(v, c), Vector(c, v)))) else None
    MH.Model(Vector(MH.Observable("x", x)(identity), MH.Observable("y", y)(identity)), proposal)
  }
  /** Run fixed-budget comparison, a mixed discrete/continuous model, and precision stopping.
    * @param args must be empty
    * @return Unit; prints diagnostics and stopping status, with runner-owned cleanup
    * @throws IllegalArgumentException for nonempty arguments
    * @example `BlockedProposalExample.main(Array.empty)`
    */
  def main(args: Array[String]): Unit = {
    require(args.isEmpty, "No arguments expected")
    val config = MH.Config(drawsPerChain = 12000, warmUp = 2000, seed = 42L)
    val standard = MH.run(config)(build(false))
    val blocked = MH.run(config)(build(true))
    for ((label, result) <- Vector("standard" -> standard, "blocked" -> blocked)) {
      val d = result.diagnostics("x")
      println((label, d.mean, d.rHat, d.meanEss.map(_ / result.elapsedSeconds)))
    }
    val mixed = MH.run(MH.Config(drawsPerChain = 10000)) { (u, _) =>
      val x = Normal(0, 1)(using "", u)
      val flag = Flip(0.3)(using "", u)
      val block = GaussianBlockProposal(Vector(x), Vector(Vector(1.0)))
      val scheme = DisjointScheme(0.7 -> (() => block), 0.3 -> (() => ProposalScheme.default(using u)))
      MH.Model(Vector(MH.Observable("x", x)(identity),
        MH.Observable("flag", flag)(b => if (b) 1.0 else 0.0)), Some(scheme))
    }
    println(mixed.diagnostics)
    val stopped = MH.runUntilPrecise(MH.Config(drawsPerChain = 20000, warmUp = 2000),
      McmcPrecision.Config(relativeTolerance = 0.15, minDrawsPerChain = 2000, checkEvery = 2000))(build(true))
    println(stopped.reason)
    println(stopped.assessments)
  }
}
