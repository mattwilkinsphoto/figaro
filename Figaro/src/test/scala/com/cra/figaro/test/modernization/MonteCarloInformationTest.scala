package com.cra.figaro.test.modernization

import com.cra.figaro.algorithm.sampling.{MonteCarloInformation as I,VectorImportance as V}
import com.cra.figaro.library.atomic.continuous.{MultivariateGaussianDistribution as G,GaussianInformation}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class MonteCarloInformationTest extends AnyWordSpec with Matchers {
  def g(m: Double)=V.Gaussian(G(Vector(m),Vector(Vector(1.0))))
  "Fixed-budget information estimates" should {
    "match analytic Gaussian KL and Bhattacharyya and preserve replay" in {
      val c=I.Config(draws=50000,seed=27)
      val kl=I.kl(g(0),g(1),c)
      kl.value.get shouldBe (.5 +- .02); kl.evaluations shouldBe 100000L
      kl shouldBe I.kl(g(0),g(1),c)
      val b=I.bhattacharyya(g(0),g(1),c)
      b.value.get shouldBe (.125 +- .006)
      I.kl(g(0),g(0),c).value shouldBe Some(0.0)
      I.bhattacharyya(g(0),g(0),c).value shouldBe Some(0.0)
    }
    "evaluate full mixtures rather than component labels or matched moments" in {
      val p=V.Mixture(Vector(.3,.7),Vector(g(-4),g(4)))
      val q=V.Mixture(Vector(.7,.3),Vector(g(-4),g(4)))
      val k=I.kl(p,q,I.Config(draws=50000,seed=93))
      k.value.get shouldBe (.4*math.log(7.0/3) +- .02)
      val b=I.bhattacharyya(p,q,I.Config(draws=50000,seed=5))
      b.value.get shouldBe (-math.log(2*math.sqrt(.21)) +- .002)
      val reordered=V.Mixture(Vector(.7,.3),Vector(g(4),g(-4)))
      I.kl(p,reordered).value.get shouldBe (0.0 +- 1e-13)
    }
    "compute joint-to-product MI and decline unresolved overlap" in {
      val joint=G(Vector(0.0,0.0),Vector(Vector(1.0,.8),Vector(.8,1.0)))
      val mi=I.mutualInformation(V.Gaussian(joint),g(0),g(0),I.Config(draws=50000,seed=37))
      mi.value.get shouldBe (GaussianInformation.mutualInformation(joint,Vector(0)).value.get +- .025)
      mi.evaluations shouldBe 150000L
      val far=I.bhattacharyya(g(-100),g(100))
      far.status shouldBe I.Status.NumericallyUnresolved; far.value shouldBe None
      intercept[IllegalArgumentException](I.Config(draws=1))
      intercept[ArithmeticException](I.kl(V.Box(Vector(0.0),Vector(1.0)),V.Box(Vector(2.0),Vector(3.0))))
    }
    "calibrate reported Gaussian MCSE across independent declared seeds" in {
      val results=(0 until 100).map(i => I.kl(g(0),g(1),I.Config(draws=2000,seed=70001L+7919L*i)))
      val mse=results.map(r => math.pow(r.value.get-.5,2)).sum/results.size
      val predicted=results.map(r => math.pow(r.mcse.get,2)).sum/results.size
      mse/predicted should be > .6
      mse/predicted should be < 1.5
      // A calibration control, not an anytime or arbitrary-law coverage guarantee.
      results.count(r => math.abs(r.value.get-.5)<=1.96*r.mcse.get) should be >= 85
    }
    "retain signed sampling noise and deterministic isolated concurrent calls" in {
      val config=I.Config(draws=2,seed=7)
      val signed=(0 until 20).map(i => I.kl(g(0),g(.01),config.copy(seed=i)).value.get)
      signed.exists(_<0) shouldBe true
      val pool=java.util.concurrent.Executors.newFixedThreadPool(2)
      try {
        val futures=(0 until 8).map(i => pool.submit(new java.util.concurrent.Callable[I.Result] {
          def call(): I.Result=I.kl(g(0),g(1),I.Config(draws=1000,seed=i))
        }))
        futures.zipWithIndex.foreach { (f,i) => f.get(30,java.util.concurrent.TimeUnit.SECONDS) shouldBe I.kl(g(0),g(1),I.Config(draws=1000,seed=i)) }
      } finally { pool.shutdownNow(); pool.awaitTermination(30,java.util.concurrent.TimeUnit.SECONDS) }
    }
  }
}
