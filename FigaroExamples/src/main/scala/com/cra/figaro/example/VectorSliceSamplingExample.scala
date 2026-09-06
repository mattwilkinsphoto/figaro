package com.cra.figaro.example

import com.cra.figaro.algorithm.sampling.VectorSliceSampler as VS
import com.cra.figaro.algorithm.sampling.parallel.McmcDiagnostics

/** Three runnable continuous-vector workflows, without a Figaro Universe. */
object VectorSliceSamplingExample {
  /** Execute Gaussian GPSS, constrained quantile, and independent-chain diagnostics examples.
    * @param args no arguments accepted
    * @return Unit; prints work status and estimates, throwing if a fixture fails to complete
    * @example `VectorSliceSamplingExample.main(Array.empty)`
    */
  def main(args: Array[String]): Unit = {
    require(args.isEmpty, "This example accepts no arguments")
    val gaussian = VS.run(VS.Config(VS.Method.GPSS, draws = 2000, warmUp = 200), Vector(1.0, -1.0))(
      x => -x.map(v => v * v).sum / 2)
    require(gaussian.reason == VS.StopReason.DrawsReached)
    println(s"Gaussian: ${gaussian.reason}, ${gaussian.evaluations} calls, mean=${gaussian.samples.map(_.head).sum / gaussian.samples.size}")

    val positive = VS.run(VS.Config(VS.Method.Quantile, draws = 2000, warmUp = 200), Vector(1.0, 1.0))(
      x => if (x.forall(_ > 0)) -x.sum else Double.NegativeInfinity)
    require(positive.reason == VS.StopReason.DrawsReached && positive.samples.forall(_.forall(_ > 0)))
    println(s"Positive: ${positive.reason}, ${positive.evaluations} calls, mean=${positive.samples.map(_.head).sum / positive.samples.size}")

    // Run sequentially here. Independent calls may also be scheduled on a caller-owned pool.
    val seeds = new java.util.SplittableRandom(9301)
    val chains = Vector.tabulate(4) { i =>
      VS.run(VS.Config(VS.Method.GPSS, draws = 2000, warmUp = 200, seed = seeds.nextLong()),
        Vector(i + 0.5, -i - 0.5))(x => -x.map(v => v * v).sum / 2)
    }
    require(chains.forall(_.reason == VS.StopReason.DrawsReached))
    val summary = McmcDiagnostics.summarize(chains.map(_.samples.map(_.head)))
    println(s"Independent Gaussian chains: mean=${summary.mean}, R-hat=${summary.rHat}, warnings=${summary.warnings}")
  }
}
