package com.cra.figaro.test.modernization
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import com.cra.figaro.library.atomic.continuous.*
import com.cra.figaro.algorithm.sampling.{MonteCarloInformation as I,Importance}
import com.cra.figaro.util.SamplingRandom
import com.cra.figaro.language.*

class GvmMixtureCopulaTest extends AnyWordSpec with Matchers {
  def gvm(mu: Double,angle: Double=0,kappa: Double=4)=GaussVonMisesDistribution(Vector(mu),Vector(Vector(1.0)),angle,Vector(.3),Vector(Vector(.1)),kappa)
  val corr=Vector(Vector(1.0,.6),Vector(.6,1.0))
  val identity=Vector(Vector(1.0,0.0),Vector(0.0,1.0))
  "GVM mixtures" should {
    "reduce to one component, respect permutations and ignore zero weights" in {
      val g=gvm(0); val p=GaussVonMisesMixtureDistribution(Vector(1),Vector(g))
      val x=LinearAngular(Vector(.2),3.1)
      p.logDensity(x) shouldBe g.logDensity(x)
      val a=GaussVonMisesMixtureDistribution(Vector(.3,.7,0),Vector(g,gvm(2),gvm(-10)))
      val b=GaussVonMisesMixtureDistribution(Vector(.7,.3),Vector(gvm(2),g))
      a.logDensity(x) shouldBe (b.logDensity(x) +- 1e-14)
      a.responsibilities(x).sum shouldBe (1.0 +- 1e-14)
      a.responsibilities(x).last shouldBe 0.0
      a.logDensity(x.copy(angle=x.angle+2*math.Pi)) shouldBe (a.logDensity(x) +- 1e-13)
      a.linearMarginal.mean.head shouldBe (1.4 +- 1e-14)
    }
    "normalize the circular dimension and sample the linear mixture" in {
      val p=GaussVonMisesMixtureDistribution(Vector(.4,.6),Vector(gvm(-2,3),gvm(2,-3)))
      val n=4096; val dx=2*math.Pi/n
      val integral=(0 until n).map(i=>p.density(LinearAngular(Vector(.5),-math.Pi+(i+.5)*dx))*dx).sum
      integral shouldBe (p.linearMarginal.density(Vector(.5)) +- 1e-12)
      val rng=SamplingRandom.scalaRandom(102001); val samples=Vector.fill(15000)(p.sample(rng))
      samples.map(_.linear.head).sum/samples.size shouldBe (.4 +- .07)
      val chart=p.asProposal(3)
      chart.logDensity(Vector(0,3+math.Pi)) shouldBe Double.NegativeInfinity
      val c=chart.sample(SamplingRandom.scalaRandom(5)); c.last should be >= (3-math.Pi); c.last should be < (3+math.Pi)
    }
    "compare complete laws and distinguish label MI from linear-angular MI" in {
      val g=gvm(0); val p=GaussVonMisesMixtureDistribution(Vector(.4,.6),Vector(g,g))
      I.kl(p.asProposal(),p.asProposal(),I.Config(1000,1)).value.get shouldBe 0.0
      GaussVonMisesMixtureInformation.componentMutualInformation(p,I.Config(1000,1)).value.get shouldBe (0.0 +- 1e-14)
      GaussVonMisesMixtureInformation.bhattacharyya(p,p,I.Config(1000,1)).value.get shouldBe 0.0
      val q=GaussVonMisesMixtureDistribution(Vector(1),Vector(gvm(.5)))
      val r=GaussVonMisesMixtureInformation.kl(p,q,I.Config(20000,102002))
      r.value.get shouldBe (g.klDivergenceComponents(q.components.head).total +- (6*r.mcse.get+1e-12))
      Universe.createNew(); GaussVonMisesMixture(p).logDensity(LinearAngular(Vector(0),0)) shouldBe p.logDensity(LinearAngular(Vector(0),0))
    }
    "preserve cancellation and reject malformed mixtures" in {
      intercept[IllegalArgumentException](GaussVonMisesMixtureDistribution(Vector(.2),Vector(gvm(0))))
      val p=GaussVonMisesMixtureDistribution(Vector(1),Vector(gvm(0)))
      Thread.currentThread().interrupt()
      try intercept[java.util.concurrent.CancellationException](p.sample(SamplingRandom.scalaRandom(1)))
      finally Thread.interrupted()
    }
  }
  "Continuous copulas" should {
    "recover a multivariate Gaussian and analytic partition MI" in {
      val law=CopulaDistribution(Vector(GaussianDistribution(1,2),GaussianDistribution(-1,3)),corr)
      val gaussian=MultivariateGaussianDistribution(Vector(1,-1),Vector(Vector(4.0,3.6),Vector(3.6,9.0)))
      for(x<-Vector(Vector(0.0,0.0),Vector(2.0,-3.0),Vector(-2.0,4.0)))
        law.logDensity(x) shouldBe (gaussian.logDensity(x) +- 1e-12)
      law.gaussianPartitionMutualInformation(Vector(0)) shouldBe (-.5*math.log(1-.36) +- 1e-14)
      law.marginal(Vector(1)).logDensity(Vector(2)) shouldBe (GaussianDistribution(-1,3).logDensity(2) +- 1e-12)
      CopulaDistribution(Vector.fill(2)(GaussianDistribution(0,1)),corr).logDensity(Vector(1,-1)) shouldBe (-4.1147335150951357 +- 1e-12)
    }
    "recover elliptical Student t without confusing identity correlation with independence" in {
      val law=CopulaDistribution(Vector.fill(2)(StudentTDistribution(5)),corr,Some(5))
      val t=MultivariateStudentTDistribution(5,Vector(0,0),corr)
      for(x<-Vector(Vector(0.0,0.0),Vector(2.0,-3.0))) law.logDensity(x) shouldBe (t.logDensity(x) +- 1e-11)
      law.logDensity(Vector(1,-1)) shouldBe (-4.0407486470549443 +- 1e-12)
      val independent=CopulaDistribution(Vector.fill(2)(GaussianDistribution(0,1)),identity)
      independent.logDensity(Vector(1,2)) shouldBe (GaussianDistribution(0,1).logDensity(1)+GaussianDistribution(0,1).logDensity(2) +- 1e-14)
      val tc=CopulaDistribution(Vector.fill(2)(GaussianDistribution(0,1)),identity,Some(4))
      math.abs(tc.logDensity(Vector(0,0))-independent.logDensity(Vector(0,0))) should be > .05
      intercept[IllegalArgumentException](tc.gaussianPartitionMutualInformation(Vector(0)))
    }
    "preserve non-Gaussian marginals and rank dependence in seeded draws" in {
      val law=CopulaDistribution(Vector(WeibullDistribution(2,3),LogNormalDistribution(.2,.6)),corr)
      val rng=SamplingRandom.scalaRandom(102003); val samples=Vector.fill(6000)(law.sample(rng))
      for(j<-0 to 1) {
        val median=law.marginals(j).quantile(.5)
        samples.count(_(j)<=median).toDouble/samples.size shouldBe (.5 +- .025)
      }
      val m0=law.marginals(0).quantile(.5); val m1=law.marginals(1).quantile(.5)
      samples.count(x=>x(0)>m0 && x(1)>m1).toDouble/samples.size shouldBe (.25+math.asin(.6)/(2*math.Pi) +- .025)
      val result=I.mutualInformation(law,law.marginal(Vector(0)),law.marginal(Vector(1)),I.Config(3000,102004))
      result.value.get shouldBe (law.gaussianPartitionMutualInformation(Vector(0)) +- (6*result.mcse.get+.001))
    }
    "validate matrix, support, boundaries and actual graph observations" in {
      intercept[IllegalArgumentException](CopulaDistribution(Vector.fill(2)(GaussianDistribution(0,1)),Vector(Vector(2.0,0.0),Vector(0.0,1.0))))
      intercept[IllegalArgumentException](CopulaDistribution(Vector.fill(2)(GaussianDistribution(0,1)),Vector(Vector(1.0,1.0),Vector(1.0,1.0))))
      val law=CopulaDistribution(Vector(WeibullDistribution(2,1),GaussianDistribution(0,1)),corr)
      law.logDensity(Vector(-1,0)) shouldBe Double.NegativeInfinity
      intercept[ArithmeticException](law.logDensity(Vector(1,40)))
      Universe.createNew(); com.cra.figaro.util.setSeed(102005)
      val high=Flip(.5)
      val observed=NonCachingChain(high,(b: Boolean)=>CopulaElement(CopulaDistribution(Vector.fill(2)(GaussianDistribution(if(b) 1 else -1,1)),identity)))
      observed.observe(Vector(1,1))
      val alg=Importance(15000,high)
      try { alg.start(); alg.probability(high,true) shouldBe (1/(1+math.exp(-4)) +- .015) }
      finally alg.kill()
    }
  }
}
