package com.cra.figaro.example

import com.cra.figaro.algorithm.sampling.VectorSliceSampler as VS
import com.cra.figaro.algorithm.sampling.parallel.{MultiChainVectorSliceSampler as MC, McmcDiagnostics}

/** Complete examples for bounded vector chains, capped output, and derived observables. */
object MultiChainVectorSamplingExample {
  /** Run all three documented workflows.
    * @param args no arguments accepted
    * @return Unit; prints statuses/diagnostics, throws if an example's execution contract fails
    * @example `MultiChainVectorSamplingExample.main(Array.empty)`
    */
  def main(args: Array[String]): Unit = {
    require(args.isEmpty, "No arguments expected")
    val base = MC.Config(VS.Config(VS.Method.GPSS, draws = 2000, warmUp = 200, seed = 9301),
      chains = 4, parallelism = 2)
    val gaussian = MC.run(base) { (i, _) =>
      MC.Model(Vector(i + 0.5, -i - 0.5), x => -x.map(v => v * v).sum / 2)
    }
    require(gaussian.chains.forall(_.result.reason == VS.StopReason.DrawsReached))
    println(s"Gaussian: ${gaussian.chains.size} chains, ${gaussian.diagnosticDrawsPerChain} aligned draws, ${gaussian.elapsedSeconds} seconds")
    gaussian.diagnostics.zipWithIndex.foreach((d, i) => println(s"coordinate $i: mean=${d.mean}, R-hat=${d.rHat}, warnings=${d.warnings}"))

    val capped = MC.run(MC.Config(VS.Config(VS.Method.Quantile, draws = 10000,
      warmUp = 100, maxEvaluations = 2000), parallelism = 2)) { (i, _) =>
      MC.Model(Vector(i + 0.5, i + 1.0), x => if (x.forall(_ > 0)) -x.sum else Double.NegativeInfinity)
    }
    require(capped.chains.forall(_.result.reason == VS.StopReason.MaxEvaluationsReached))
    require(capped.chains.forall(_.result.evaluations == 2000))
    println(s"Capped positive chains: lengths=${capped.chains.map(_.result.samples.size)}, warnings=${capped.warnings}")

    // Derived quantities need separate diagnostics; coordinate summaries do not cover every event.
    val n = gaussian.diagnosticDrawsPerChain
    val event = McmcDiagnostics.summarize(gaussian.chains.map(_.result.samples.take(n).map(
      x => if (x.head > 0) 1.0 else 0.0)))
    println(s"P(first > 0): ${event.mean}, warnings=${event.warnings}")
  }
}
