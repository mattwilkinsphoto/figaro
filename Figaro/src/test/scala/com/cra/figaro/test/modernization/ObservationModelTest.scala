package com.cra.figaro.test.modernization
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import com.cra.figaro.library.atomic.continuous.*
import com.cra.figaro.library.atomic.InformationMetricStatus as S
import com.cra.figaro.language.*
import com.cra.figaro.algorithm.sampling.Importance
import com.cra.figaro.util.SamplingRandom

class ObservationModelTest extends AnyWordSpec with Matchers {
  val normal=GaussianDistribution(0,1)
  "Observation likelihoods" should {
    "distinguish exact densities from interval and censoring masses" in {
      ObservationLikelihood.logLikelihood(normal,ScalarObservation.Exact(0)) shouldBe normal.logDensity(0)
      math.exp(ObservationLikelihood.logLikelihood(normal,ScalarObservation.LeftCensored(0))) shouldBe (.5 +- 1e-15)
      math.exp(ObservationLikelihood.logLikelihood(normal,ScalarObservation.RightCensored(0))) shouldBe (.5 +- 1e-15)
      math.exp(ObservationLikelihood.logLikelihood(normal,ObservationLikelihood.rounded(0,2))) shouldBe (.6826894921370859 +- 1e-14)
      ObservationLikelihood.logInterval(normal,40,41) shouldBe (-804.6084420137538 +- 1e-10)
      ObservationLikelihood.logInterval(normal,-41,-40) shouldBe (ObservationLikelihood.logInterval(normal,40,41) +- 1e-12)
      ObservationLikelihood.logInterval(normal,Double.NegativeInfinity,Double.PositiveInfinity) shouldBe 0.0
    }
    "reject unresolved bins and preserve support impossibility" in {
      intercept[IllegalArgumentException](ObservationLikelihood.rounded(1e100,1))
      intercept[IllegalArgumentException](ObservationLikelihood.logInterval(normal,1,1))
      ObservationLikelihood.logInterval(WeibullDistribution(2,1),-2,-1) shouldBe Double.NegativeInfinity
      ObservationLikelihood.logInterval(WeibullDistribution(2,1),-1,100) shouldBe 0.0
      intercept[ArithmeticException](ObservationLikelihood.logInterval(normal,1,math.nextUp(1.0)))
    }
    "infer a latent value under explicitly modeled measurement error" in {
      Universe.createNew(); com.cra.figaro.util.setSeed(101003)
      val latent=Normal(0,1)
      val kernels=Apply(latent,(x: Double)=>GaussianDistribution(x,1))
      ObservationLikelihood.attach(kernels,ScalarObservation.Exact(1))
      val alg=Importance(40000,latent)
      try { alg.start(); alg.expectation(latent,(x: Double)=>x) shouldBe (.5 +- .025) }
      finally alg.kill()
    }
    "apply one censored factor in a hierarchical graph and include measurement uncertainty" in {
      Universe.createNew(); com.cra.figaro.util.setSeed(101001)
      val high=Flip(.5)
      val kernels=Apply(high,(b: Boolean)=>GaussianDistribution(if(b) 1 else -1,1))
      ObservationLikelihood.attach(kernels,ScalarObservation.RightCensored(0))
      val alg=Importance(30000,high)
      try { alg.start(); alg.probability(high,true) shouldBe (.8413447460685429 +- .015) }
      finally alg.kill()
      // Known independent Gaussian measurement error adds variances, not standard deviations.
      val measured=GaussianDistribution(2,math.sqrt(3*3+4*4))
      ObservationLikelihood.logLikelihood(measured,ScalarObservation.Exact(2)) shouldBe (GaussianDistribution(2,5).logDensity(2) +- 1e-15)
    }
  }
  "Mixed measures" should {
    "keep a spike separate from the slab density and account for interval endpoints" in {
      val p=MixedScalarDistribution.spikeAndSlab(0,.3,normal)
      p.probabilityAt(0) shouldBe .3
      p.logLikelihood(0) shouldBe math.log(.3)
      p.continuousLogDensity(0) shouldBe (math.log(.7)+normal.logDensity(0))
      p.intervalProbability(-1,0) shouldBe (.3+.7*(normal.cdf(0)-normal.cdf(-1)) +- 1e-14)
      p.intervalProbability(0,1) shouldBe (.7*(normal.cdf(1)-normal.cdf(0)) +- 1e-14)
      val rng=SamplingRandom.scalaRandom(101002)
      val draws=Vector.fill(20000)(p.sample(rng))
      draws.count(_==0).toDouble/draws.size shouldBe (.3 +- .015)
    }
    "distinguish clipping, truncation and folding" in {
      val p=MixedScalarDistribution.clipped(normal,0,Double.PositiveInfinity)
      p.probabilityAt(0) shouldBe .5
      p.cdf(1) shouldBe (normal.cdf(1) +- 1e-14)
      p.logLikelihood(1) shouldBe (normal.logDensity(1) +- 1e-14)
      TruncatedDistribution(normal,0,Double.PositiveInfinity).logDensity(1) shouldBe (p.logLikelihood(1)+math.log(2) +- 1e-14)
      MixedScalarDistribution.clipped(normal,-1,1).atoms.map(_._2).sum shouldBe (2*normal.cdf(-1) +- 1e-14)
      intercept[IllegalArgumentException](MixedScalarDistribution(Vector(0.0->.4,0.0->.6),None))
    }
    "compare full mixed laws, including disjoint atoms" in {
      val p=MixedScalarDistribution.spikeAndSlab(0,.2,normal)
      val q=MixedScalarDistribution.spikeAndSlab(0,.6,normal)
      MixedScalarInformation.kl(p,q).value.get shouldBe (.2*math.log(.2/.6)+.8*math.log(.8/.4) +- 1e-13)
      MixedScalarInformation.bhattacharyya(p,q).value.get shouldBe (-math.log(math.sqrt(.12)+math.sqrt(.32)) +- 1e-13)
      MixedScalarInformation.kl(p,MixedScalarDistribution.spikeAndSlab(1,.2,normal)).status shouldBe S.Infinite
      MixedScalarInformation.kl(p,p).value.get shouldBe (0.0 +- 1e-13)
      Universe.createNew()
      MixedScalarElement(p).logDensity(0) shouldBe math.log(.2)
    }
    "use atom masses in actual hierarchical evidence with a common atom location" in {
      Universe.createNew(); com.cra.figaro.util.setSeed(101004)
      val high=Flip(.5)
      val value=NonCachingChain(high,(b: Boolean)=>MixedScalarElement(MixedScalarDistribution.spikeAndSlab(0,if(b) .8 else .2,normal)))
      value.observe(0)
      val alg=Importance(20000,high)
      try { alg.start(); alg.probability(high,true) shouldBe (.8 +- .015) }
      finally alg.kill()
    }
  }
}
