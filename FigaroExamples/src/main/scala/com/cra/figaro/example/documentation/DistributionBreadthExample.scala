package com.cra.figaro.example.documentation

import com.cra.figaro.library.atomic.continuous.*
import com.cra.figaro.library.atomic.discrete.{MultinomialDistribution,MultinomialInformation}
import com.cra.figaro.util.SamplingRandom

/** Executable standalone patterns; graph evidence and MH are verified in DistributionBreadthTest. */
object DistributionBreadthExample {
  def main(args: Array[String]): Unit = {
    val counts=MultinomialDistribution(20,Vector(.2,.3,.5))
    val other=counts.copy(probabilities=Vector(.3,.3,.4))
    require(counts.sample(SamplingRandom.scalaRandom(42)).sum==20)
    require(MultinomialInformation.kl(counts,other).value.exists(_>0))
    require(MultinomialInformation.mutualInformation(counts,Vector(0)).value.exists(_>0))
    val excess=GeneralizedParetoDistribution(.2,0,2)
    require(math.abs(excess.survival(excess.quantile(.99))-.01)<1e-12)
    require(GeneralizedExtremeValueDistribution(0,10,2).quantile(.99)>10)
    val matrix=WishartDistribution(5,Vector(Vector(1.0,.2),Vector(.2,1.0)))
    require(WishartInformation.bhattacharyya(matrix,matrix.copy(degreesOfFreedom=8)).value.exists(_>0))
    val north=VonMisesFisher3Distribution(Vector(0,0,1),10)
    val east=north.copy(direction=Vector(1,0,0))
    require(VonMisesFisher3Information.kl(north,east).value.exists(_>0))
    println("Distribution breadth: joint counts, tail probabilities, matrix and spherical comparisons passed")
  }
}
