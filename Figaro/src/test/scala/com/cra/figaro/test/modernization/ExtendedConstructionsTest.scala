package com.cra.figaro.test.modernization
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import com.cra.figaro.library.atomic.continuous.*
import com.cra.figaro.library.atomic.discrete.{CountMixtureDistribution as C,CountMixtureInformation as I,HypergeometricDistribution as H,NegativeBinomialDistribution as B,CountElement}
import com.cra.figaro.library.atomic.{InformationMetricStatus as Status}
import com.cra.figaro.util.SamplingRandom
import com.cra.figaro.language.*
import com.cra.figaro.algorithm.sampling.Importance

class ExtendedConstructionsTest extends AnyWordSpec with Matchers {
  "Extended constructions" should {
    "include Jacobians, direct survival and common-bijection information invariance" in {
      val p=MonotoneDistribution(LogNormalDistribution(.3,1.2),MonotoneTransform.Log)
      val q=MonotoneDistribution(LogNormalDistribution(-.2,.8),MonotoneTransform.Log)
      val g=GaussianDistribution(.3,1.2)
      for(x <- Vector(-3.0,0.0,2.0)) { p.logDensity(x) shouldBe (g.logDensity(x) +- 1e-12); p.cdf(x) shouldBe (g.cdf(x) +- 1e-12) }
      ScalarDivergence.kl(p,q).value.get shouldBe (ScalarDivergence.kl(p.base,q.base).value.get +- 1e-12)
      p.cdf(p.quantile(.1)) shouldBe (.1 +- 1e-12)
      val reverse=new MonotoneTransform {
        val domain=(Double.NegativeInfinity,Double.PositiveInfinity); val increasing=false
        def forward(x: Double)= -x; def inverse(y: Double)= -y; def logInverseJacobian(y: Double)=0.0
      }
      val reflected=MonotoneDistribution(GaussianDistribution(1,2),reverse)
      reflected.cdf(reflected.quantile(1e-10)) shouldBe (1e-10 +- 1e-14)
      intercept[IllegalArgumentException](MonotoneDistribution(GaussianDistribution(0,1),MonotoneTransform.Log))
    }
    "normalize half-infinite truncations and distinguish them from folding" in {
      val g=GaussianDistribution(0,1); val half=TruncatedDistribution(g,0,Double.PositiveInfinity)
      half.retainedProbability shouldBe (.5 +- 1e-15)
      half.logDensity(1) shouldBe (g.logDensity(1)+math.log(2) +- 1e-14)
      for(p <- Vector(1e-6,.1,.9,1-1e-8)) half.cdf(half.quantile(p)) shouldBe (p +- 1e-12)
      val tail=TruncatedDistribution(g,8,Double.PositiveInfinity)
      tail.retainedProbability shouldBe (6.220960574271784e-16 +- 1e-28)
      tail.cdf(tail.quantile(.5)) shouldBe (.5 +- 1e-10)
      val shifted=GaussianDistribution(1,1); val fold=FoldedDistribution(shifted)
      fold.density(1) shouldBe (shifted.density(1)+shifted.density(-1) +- 1e-14)
      fold.cdf(1) shouldBe (.4772498680518208 +- 1e-12)
      fold.cdf(fold.quantile(.3)) shouldBe (.3 +- 1e-12)
      math.abs(fold.density(1)-TruncatedDistribution(shifted,0,Double.PositiveInfinity).density(1)) should be > .01
    }
    "normalize wrapped densities and preserve angular seams and circular moments" in {
      for(mu <- Vector(0.0,2.9,-2.9); rho <- Vector(0.0,.5,.95)) {
        val w=WrappedCauchyDistribution(mu,rho)
        val integral=(0 until 20000).map(i => w.density(-math.Pi+(i+.5)*2*math.Pi/20000)).sum*2*math.Pi/20000
        integral shouldBe (1.0 +- 1e-10)
        for(p <- Vector(.001,.3,.99)) w.cdf(w.quantile(p)) shouldBe (p +- 1e-12)
        w.angularLogDensity(-math.Pi) shouldBe (w.angularLogDensity(math.Pi) +- 1e-12)
        w.cdf(.2)+w.survival(.2) shouldBe (1.0 +- 1e-14)
      }
      val law=WrappedCauchyDistribution(.4,.7); val rng=SamplingRandom.scalaRandom(99001)
      val xs=Vector.fill(30000)(law.sample(rng))
      xs.map(x => math.cos(x-.4)).sum/xs.size shouldBe (.7 +- .015)
      xs.map(x => math.sin(x-.4)).sum/xs.size shouldBe (0.0 +- .015)
      intercept[IllegalArgumentException](WrappedCauchyDistribution(0,1))
    }
    "compare full wrapped laws analytically and folded laws with guarded quadrature" in {
      val p=WrappedCauchyDistribution(0,.5); val q=WrappedCauchyDistribution(.7,.3)
      val numerical=(0 until 40000).map { i => val x= -math.Pi+(i+.5)*2*math.Pi/40000; p.density(x)*(p.logDensity(x)-q.logDensity(x)) }.sum*2*math.Pi/40000
      ScalarDivergence.kl(p,q).value.get shouldBe (numerical +- 1e-10)
      ScalarDivergence.bhattacharyya(p,q,1e-5).value.get should be > 0.0
      val a=FoldedDistribution(GaussianDistribution(0,1)); val b=FoldedDistribution(GaussianDistribution(0,2))
      ScalarDivergence.kl(a,b,1e-5).value.get shouldBe (math.log(2)+.125-.5 +- 1e-5)
    }
    "keep count mixtures discrete with exact full-law metrics and component-count MI" in {
      val a=H(10,0,1); val b=H(10,10,1)
      val p=C(Vector(.25,.75),Vector(a,b)); val q=C(Vector(.5,.5),Vector(a,b))
      p.probability(0) shouldBe (.25 +- 1e-15); p.mean shouldBe .75; p.variance shouldBe .1875
      p.quantile(.25) shouldBe 0; p.quantile(.25001) shouldBe 1
      p.responsibilities(1) shouldBe Vector(0.0,1.0)
      I.kl(p,q).value.get shouldBe (.25*math.log(.5)+.75*math.log(1.5) +- 1e-12)
      I.componentMutualInformation(p).value.get shouldBe (-.25*math.log(.25)-.75*math.log(.75) +- 1e-12)
      I.bhattacharyya(p,q).value.get shouldBe (-math.log(math.sqrt(.125)+math.sqrt(.375)) +- 1e-12)
      I.kl(C(Vector(1.0),Vector(B(2,.4))),B(3,.4)).status shouldBe Status.Unsupported
      I.componentMutualInformation(p,maxTerms=1).status shouldBe Status.BudgetExhausted
    }
    "use constructed laws through real observation likelihoods and scoped sampling" in com.cra.figaro.util.withRandomSeed(99002) {
      Universe.createNew()
      val root=Flip(.5)
      val observed=ScalarElement(Apply(root,(b: Boolean) => FoldedDistribution(GaussianDistribution(if(b) 0 else 2,1))))
      observed.observe(.5)
      val algorithm=Importance(20000,root)
      try {
        algorithm.start()
        val a=FoldedDistribution(GaussianDistribution(0,1)).density(.5); val b=FoldedDistribution(GaussianDistribution(2,1)).density(.5)
        algorithm.probability(root,true) shouldBe (a/(a+b) +- .025)
      } finally algorithm.kill()
      val law=C(Vector(.3,.7),Vector(H(10,0,1),H(10,10,1)))
      CountElement(law).logDensity(1) shouldBe (math.log(.7) +- 1e-14)
    }
  }
}
