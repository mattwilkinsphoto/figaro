package com.cra.figaro.example

import com.cra.figaro.algorithm.sampling.parallel.MultiChainMetropolisHastings.*
import com.cra.figaro.library.atomic.continuous.Normal

/** A normal prior plus explicit Gaussian log likelihood, with aligned mean/tail queries. */
object MultiChainMcmcExample {
  /** Run four independent chains and print diagnostics. Arguments are ignored; returns Unit. */
  def main(args: Array[String]): Unit = {
    val result = run(Config(drawsPerChain = 10000, warmUp = 2000)) { (u, index) =>
      val mean = Normal(0.0, 1.0)(using "", u)
      // One measurement y=1, variance=1; omit only the parameter-independent normalizer.
      mean.addLogConstraint((m: Double) => -0.5 * (m - 1) * (m - 1))
      Model(Vector(Observable("mean", mean)(identity),
        Observable("positive", mean)(m => if (m > 0) 1.0 else 0.0)),
        initialState = () => if (index % 2 == 0) mean.value < -1 else mean.value > 1)
    }
    result.diagnostics.toVector.sortBy(_._1).foreach { (name, summary) =>
      println(s"$name: mean=${summary.mean}, R-hat=${summary.rHat}, bulk ESS=${summary.bulkEss}, MCSE=${summary.mcseMean}")
      summary.warnings.foreach(warning => println(s"  $warning"))
    }
    result.chains.foreach(c => println(s"chain ${c.index}: seed=${c.seed}, acceptance=${c.acceptanceRate}, draws=${c.draws("mean").size}"))
    require(math.abs(result.diagnostics("mean").mean - 0.5) < 0.1, "Unexpected posterior mean")
    // Results are plain immutable values; the runner has already disposed every model.
  }
}
