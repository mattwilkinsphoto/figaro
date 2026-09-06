package com.cra.figaro.example

import com.cra.figaro.algorithm.sampling.parallel.ParImportance
import com.cra.figaro.language.*

/** Minimal seeded parallel importance-sampling example. */
object ParallelSamplingExample {
  /** Construct isolated models, run a bounded sampler, query, and release its workers.
    * @param args ignored; the example uses four workers, 80000 samples, and seed 42
    * @return Unit; prints the estimated posterior and checks it against its known value
    * @example `ParallelSamplingExample.main(Array.empty)`
    */
  def main(args: Array[String]): Unit = {
    val makeModel = () => {
      val universe = Universe.createNew()
      val cause = Flip(0.3)(using "cause", universe)
      cause.addConstraint(value => if (value) 0.8 else 0.2)
      universe
    }
    val algorithm = ParImportance.seeded(makeModel, 4, 80000, 42L, "cause")
    try {
      algorithm.start()
      val posterior = algorithm.probability[Boolean]("cause", true)
      println(f"Estimated P(cause | evidence) = $posterior%.6f")
      assert(math.abs(posterior - 0.24 / 0.38) < 0.02)
    } finally {
      if (algorithm.isActive) algorithm.kill()
    }
  }
}
