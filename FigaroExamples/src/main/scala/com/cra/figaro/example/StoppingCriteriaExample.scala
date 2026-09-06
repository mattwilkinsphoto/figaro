package com.cra.figaro.example

import com.cra.figaro.algorithm.sampling.parallel.{TruncatedSprt, McmcPrecision, MultiChainMetropolisHastings as MH}
import com.cra.figaro.language.Universe
import com.cra.figaro.library.atomic.continuous.Normal

/** Runnable categorical KL, sequential decision, and precision-limited MCMC examples. */
object StoppingCriteriaExample {
  /** Compare fixed/adaptive retained work; this is not a controlled speed benchmark.
    * @param args empty command-line arguments
    * @return Unit; prints divergence, decision, sample counts, precision, and elapsed times
    * @example `StoppingCriteriaExample.main(Array.empty)`
    */
  def main(args: Array[String]): Unit = {
    require(args.isEmpty, "This example takes no arguments")
    println(s"Categorical KL (nats): ${TruncatedSprt.klDivergence(Vector(0.5, 0.5), Vector(0.6, 0.4))}")
    val design = TruncatedSprt.gaussian(0, 1, 1, falseAlarmRate = 0.05, missedDetectionRate = 0.10)
    val rng = new java.util.Random(42L)
    var state = design.initial
    while (state.decision == TruncatedSprt.Decision.Continue) state = design.advance(state, 1 + rng.nextGaussian())
    println(s"SPRT: ${state.decision}, observations=${state.samples}, cap=${design.maxSamples}, terminal=${state.atTruncation}")
    def build(u: Universe, index: Int): MH.Model = {
      val x = Normal(0, 1)(using "", u)
      MH.Model(Vector(MH.Observable("x", x)(identity)))
    }
    val budget = MH.Config(drawsPerChain = 20000, warmUp = 1000)
    val fixed = MH.run(budget)(build)
    val adaptive = MH.runUntilPrecise(budget, McmcPrecision.Config(relativeTolerance = 0.10))(build)
    println(s"Fixed: draws/chain=${fixed.chains.head.draws("x").size}, seconds=${fixed.elapsedSeconds}")
    println(s"Adaptive: ${adaptive.reason}, draws/chain=${adaptive.result.chains.head.draws("x").size}, checks=${adaptive.checks}, seconds=${adaptive.result.elapsedSeconds}")
    println(s"Final precision assessment: ${adaptive.assessments("x")}")
  }
}
