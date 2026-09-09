package com.cra.figaro.test.modernization

import com.cra.figaro.algorithm.sampling.{VectorImportance as V,GraphProposalImportance as P}
import com.cra.figaro.library.atomic.continuous.{MultivariateGaussianDistribution as G,Normal}
import com.cra.figaro.util.SamplingRandom
import com.cra.figaro.language.*
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class ConditionalProposalTest extends AnyWordSpec with Matchers {
  private def gaussian(m: Double,v: Double)=V.Gaussian(G(Vector(m),Vector(Vector(v))))
  "Explicit conditional proposals" should {
    "retain dependence and evaluate the full joint density" in {
      val q=V.Conditional(gaussian(0,1),1,x => gaussian(x.head,.01))
      val joint=G(Vector(0.0,0.0),Vector(Vector(1.0,1.0),Vector(1.0,1.01)))
      for(x <- Vector(Vector(0.0,0.0),Vector(1.0,1.2),Vector(-2.0,-1.7)))
        q.logDensity(x) shouldBe (joint.logDensity(x) +- 1e-10)
      val rng=SamplingRandom.scalaRandom(41)
      val draws=Vector.fill(20000)(q.sample(rng))
      draws.map(x => math.pow(x(1)-x(0),2)).sum/draws.size shouldBe (.01 +- .0005)
      q.sample(SamplingRandom.scalaRandom(1)) shouldBe q.sample(SamplingRandom.scalaRandom(1))
    }
    "correct a larger graph block without double counting its latent prior" in {
      val prior=V.Conditional(gaussian(0,1),1,x => gaussian(x.head,.01))
      val posterior=G(Vector(1/1.02,1.01/1.02),Vector(
        Vector(1-1/1.02,1-1.01/1.02),Vector(1-1.01/1.02,1.01-1.01*1.01/1.02)))
      val result=P.run(P.Config(draws=3000,maxAttempts=3000,seed=11),V.Gaussian(posterior),prior.logDensity) { (u,root) =>
        val latent=root.map(_(1))(using "",u)
        Normal(latent,.01)(using "",u).observe(1.0)
        root.map(_.head)(using "",u)
      }
      result.health.diagnostics.mean.get shouldBe (1/1.02 +- .015)
      result.health.diagnostics.ess.get shouldBe (3000.0 +- 1e-6)
    }
    "reject changing dimensions, invalid outputs, overflow and null callbacks" in {
      intercept[IllegalArgumentException](V.Conditional(gaussian(0,1),128,_ => gaussian(0,1)))
      intercept[IllegalArgumentException](V.Conditional(gaussian(0,1),1,null))
      val bad=V.Conditional(gaussian(0,1),2,_ => gaussian(0,1))
      intercept[IllegalArgumentException](bad.sample(SamplingRandom.scalaRandom(1)))
      intercept[IllegalArgumentException](bad.logDensity(Vector(0.0,0.0,0.0)))
      val box=V.Conditional(V.Box(Vector(0.0),Vector(1.0)),1,_ => throw new AssertionError("off support callback"))
      box.logDensity(Vector(-1.0,0.0)) shouldBe Double.NegativeInfinity
      intercept[IllegalArgumentException](box.logDensity(Vector(Double.NaN,0.0)))
    }
  }
}
