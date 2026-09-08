package com.cra.figaro.example.documentation

import com.cra.figaro.library.atomic.continuous.*
import org.apache.commons.math3.distribution.ChiSquaredDistribution

/** Finite-concentration score thresholds, direct tails and modeled coverage. */
object GaussVonMisesScoreExample {
  /** Run three fixed-kernel calibration patterns; no Figaro model or global RNG is created.
    * @param args empty command-line arguments
    * @return Unit; prints checked finite-concentration comparisons and sampled coverage
    * @example `GaussVonMisesScoreExample.main(Array.empty[String])`
    */
  def main(args: Array[String]): Unit = {
    val kernel=GaussVonMisesDistribution(Vector(1.0,-2.0),
      Vector(Vector(4.0,1.2),Vector(1.2,2.61)),3.05,Vector(0.7,-0.4),
      Vector(Vector(0.3,0.2),Vector(0.2,-0.5)),0.1)
    val calibration=kernel.scoreDistribution()

    // 1. Compare the finite-concentration threshold with the large-kappa approximation.
    val threshold=calibration.quantile(0.95)
    val approximate=new ChiSquaredDistribution(kernel.dimension+1).inverseCumulativeProbability(0.95)
    println(f"95%% squared threshold: finite kappa=$threshold%.6f, chi-square approximation=$approximate%.6f")
    println(f"Actual modeled coverage of approximate threshold: ${calibration.cdf(approximate)}%.6f")
    require(threshold < approximate)

    // 2. Interpret a fixed state's squared score through its direct modeled upper tail.
    val state=LinearAngular(Vector(4.0,0.0),-2.8)
    val score=kernel.mahalanobisSquared(state)
    val tail=calibration.survivalEstimate(score)
    println(f"State score=$score%.6f, modeled upper tail=${tail.value}%.6f; exceeds 95%% threshold=${score > threshold}")
    require(math.abs(tail.value+calibration.cdf(score)-1) < 2e-10)

    // 3. Reuse dimension/concentration calibration and check known-prior coverage.
    val reusable=GaussVonMisesScoreDistribution(kernel.dimension,kernel.kappa)
    require(reusable.quantile(0.95) == threshold)
    val rng=new scala.util.Random(7421L)
    val size=40000
    val covered=(0 until size).count(_ => kernel.mahalanobisSquared(kernel.sample(rng)) <= threshold)
    val fraction=covered.toDouble/size
    println(f"Known-kernel sample coverage: $fraction%.6f (target 0.95, $size draws)")
    require(math.abs(fraction-0.95) < 5*math.sqrt(0.95*0.05/size))
  }
}
