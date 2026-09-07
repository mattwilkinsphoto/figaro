/* Circular foundation examples. See LICENSE and FigaroAttributions.txt. */
package com.cra.figaro.example.documentation

import com.cra.figaro.algorithm.sampling.Importance
import com.cra.figaro.algorithm.sampling.parallel.ParImportance
import com.cra.figaro.language.*
import com.cra.figaro.library.atomic.continuous.{VonMises, VonMisesDistribution}
import com.cra.figaro.util.{CircularStatistics as Circular, withRandomSeed}

object VonMisesExample {
  /** Run circular summaries, observed-heading inference and isolated parallel sampling.
    * @param args must be empty
    * @return Unit; prints results and throws if illustrative numerical checks fail
    * @example `VonMisesExample.main(Array.empty[String])`
    */
  def main(args: Array[String]): Unit = {
    require(args.isEmpty, "VonMisesExample takes no arguments")
    val headings = Vector(-179.0, 179.0).map(math.toRadians)
    val summary = Circular.summarize(headings)
    println(f"Arithmetic mean: ${math.toDegrees(headings.sum / 2)}%.1f degrees; circular mean: ${math.toDegrees(summary.meanDirection.get)}%.1f degrees")
    val kernel = VonMisesDistribution(math.toRadians(179), 20.0)
    val rng = new scala.util.Random(42L)
    val sampled = Circular.summarize(Vector.fill(10000)(kernel.sample(rng)))
    assert(math.abs(Circular.difference(sampled.meanDirection.get, kernel.location)) < 0.1)
    println(f"Sampled direction: ${math.toDegrees(sampled.meanDirection.get)}%.2f degrees; resultant: ${sampled.meanResultantLength}%.4f")

    withRandomSeed(42L) {
      val u = Universe.createNew()
      val direction = Select(0.5 -> 3.1, 0.5 -> 0.0)(using "direction", u)
      val reading = VonMises(direction, 4.0)(using "reading", u)
      reading.observe(-3.1)
      val alg = Importance(10000, direction)
      try {
        alg.start()
        val posterior = alg.probability(direction, 3.1)
        assert(posterior > 0.98)
        println(f"P(direction=3.1 | reading=-3.1) = $posterior%.6f")
      } finally { if (alg.isActive) alg.kill(); u.clear() }
    }

    val parallel = ParImportance.seeded(() => {
      val u = Universe.createNew()
      val angle = VonMises(0.0, 3.0)(using "angle", u)
      Apply(angle, (x: Double) => math.cos(x))(using "cos", u)
      u
    }, 2, 20000, 42L, "cos")
    try {
      parallel.start()
      val cosine = parallel.expectation[Double]("cos", identity)
      assert(math.abs(cosine - 0.8099852939565045) < 0.03)
      println(f"Parallel E[cos(angle)] = $cosine%.6f")
    } finally if (parallel.isActive) parallel.kill()
    println("Von Mises examples passed")
  }
}
