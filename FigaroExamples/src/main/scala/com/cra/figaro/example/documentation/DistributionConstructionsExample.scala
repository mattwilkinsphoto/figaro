package com.cra.figaro.example.documentation

import com.cra.figaro.library.atomic.continuous.*
import com.cra.figaro.library.atomic.discrete.*
import com.cra.figaro.language.*
import com.cra.figaro.algorithm.sampling.Importance
import com.cra.figaro.util.withRandomSeed

/** Runnable counterpart of the three patterns in DISTRIBUTION_CONSTRUCTIONS.md. */
object DistributionConstructionsExample {
  /** @param args no arguments expected
    * @return Unit; runs transformations/truncation, hierarchical GMM, and zero-adjusted counts
    * @example `sbt "examples / Compile / runMain com.cra.figaro.example.documentation.DistributionConstructionsExample"`
    */
  def main(args: Array[String]): Unit = {
    require(args.isEmpty)
    val standard=GaussianDistribution(0,1)
    val reflected=AffineDistribution(standard,3,-2)
    val positive=ExpDistribution(standard)
    val bounded=TruncatedDistribution(standard,8,9)
    require(math.abs(reflected.quantile(.5)-3) < 1e-12)
    require(math.abs(positive.quantile(.5)-1) < 1e-12)
    require(math.abs(bounded.cdf(bounded.quantile(.4))-.4) < 1e-10)
    println(s"Truncated Gaussian retains ${bounded.retainedProbability} of the original probability")

    val p=MultivariateGaussianDistribution(Vector(-2.0,0.0),Vector(Vector(1.0,.3),Vector(.3,1.0)))
    val q=p.copy(mean=Vector(2.0,0.0))
    val gmm=GaussianMixtureDistribution(Vector(.4,.6),Vector(p,q))
    println(s"Mixture mean=${gmm.mean}; covariance=${gmm.covariance}; membership=${gmm.responsibilities(Vector(2.0,0.0))}")
    val results=Vector(GaussianInformation.kl(p,q),GaussianInformation.bhattacharyya(p,q),GaussianInformation.mutualInformation(p,Vector(0)))
    results.foreach(r => { require(r.value.nonEmpty); println(s"${r.method}: ${r.value} nats") })

    withRandomSeed(91) {
      val u=Universe.createNew()
      val firstRegime=Flip(.5)(using "regime",u)
      val kernels=Apply(firstRegime,(b: Boolean) => gmm.copy(weights=if(b) Vector(.8,.2) else Vector(.2,.8)))(using "kernel",u)
      GaussianMixture(kernels)(using "measurement",u).observe(Vector(2.0,0.0))
      val algorithm=Importance(10000,firstRegime)
      try {
        algorithm.start()
        val posterior=algorithm.probability(firstRegime,true)
        require(posterior > .15 && posterior < .25)
        println(s"P(first regime | measurement)=$posterior")
      } finally { if(algorithm.isActive) algorithm.kill(); u.clear() }
    }

    val count=NegativeBinomialDistribution(2,.5)
    val inflated=ZeroAdjustedDistribution(count,.3)
    val hurdle=ZeroAdjustedDistribution(count,.3,hurdle=true)
    require(math.abs(inflated.probability(0)-.475) < 1e-12)
    require(math.abs(hurdle.probability(0)-.3) < 1e-12)
    println(s"P(zero): ordinary=${count.probability(0)}, inflated=${inflated.probability(0)}, hurdle=${hurdle.probability(0)}")
  }
}
