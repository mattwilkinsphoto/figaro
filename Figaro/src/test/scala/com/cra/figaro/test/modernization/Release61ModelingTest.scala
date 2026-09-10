package com.cra.figaro.test.modernization

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import com.cra.figaro.library.atomic.continuous.*
import com.cra.figaro.util.SamplingRandom

class Release61ModelingTest extends AnyWordSpec with Matchers {
  private val r=Vector(Vector(1.0,.6),Vector(.6,1.0))
  private def g(mu: Double=0,a: Double=0,b: Double=.4,c: Double=.7,k: Double=8)=
    GaussVonMisesDistribution(Vector(mu),Vector(Vector(1.0)),a,Vector(b),Vector(Vector(c)),k)
  "Exact-coordinate copula conditioning" should {
    "integrate partial graph observations instead of scoring the unobserved coordinates" in {
      import com.cra.figaro.language.*
      import com.cra.figaro.algorithm.sampling.Importance
      Universe.createNew()
      val choice=Flip(.5)
      val observation=Chain(choice,(flag: Boolean)=>CopulaElement.observedMarginal(
        CopulaDistribution(Vector(GaussianDistribution(if(flag) 1 else -1,1),GaussianDistribution(0,1)),r),Vector(0)))
      observation.observe(Vector(1))
      com.cra.figaro.util.random.setSeed(610011)
      val algorithm=Importance(10000,choice)
      try { algorithm.start(); algorithm.probability(choice,true) shouldBe ((1/(1+math.exp(-2))) +- .025) }
      finally algorithm.kill()
    }
    "leave Gaussian independent coordinates unchanged and update t scale at zero correlation" in {
      val identity=Vector(Vector(1.0,0.0),Vector(0.0,1.0))
      val law=CopulaDistribution(Vector.fill(2)(GaussianDistribution(0,1)),identity)
      law.condition(Vector(0),Vector(3)).logDensity(Vector(.4)) shouldBe (GaussianDistribution(0,1).logDensity(.4) +- 1e-13)
      val t=CopulaDistribution(Vector.fill(2)(StudentTDistribution(5)),identity,Some(5))
      val conditional=t.condition(Vector(0),Vector(3))
      val ref=MultivariateStudentTDistribution(6,Vector(0),Vector(Vector(14.0/6)))
      conditional.logDensity(Vector(.4)) shouldBe (ref.logDensity(Vector(.4)) +- 1e-12)
    }
    "recover Gaussian conditionals and marginal evidence" in {
      val law=CopulaDistribution(Vector(GaussianDistribution(1,2),GaussianDistribution(-1,3)),r)
      val c=law.condition(Vector(0),Vector(3))
      c.remainingIndices shouldBe Vector(1)
      val reference=GaussianDistribution(.8,2.4)
      for(x<-Vector(-3.0,0.0,2.0)) {
        c.logDensity(Vector(x)) shouldBe (reference.logDensity(x) +- 1e-12)
        c.logDensity(Vector(x))+c.logEvidence shouldBe (law.logDensity(Vector(3,x)) +- 1e-12)
      }
      law.partialLogDensity(Vector.empty,Vector.empty) shouldBe 0.0
      law.partialLogDensity(Vector(1,0),Vector(2,3)) shouldBe (law.logDensity(Vector(3,2)) +- 1e-12)
      val rng=SamplingRandom.scalaRandom(610001)
      val draws=Vector.fill(10000)(c.sample(rng).head)
      draws.sum/draws.size shouldBe (.8 +- .09)
      draws.map(x=>math.pow(x-.8,2)).sum/draws.size shouldBe (5.76 +- .3)
    }
    "use updated t degrees of freedom but the original coordinate transform" in {
      val law=CopulaDistribution(Vector.fill(2)(StudentTDistribution(5)),r,Some(5))
      val c=law.condition(Vector(1),Vector(2))
      val ref=MultivariateStudentTDistribution(6,Vector(1.2),Vector(Vector(.96)))
      c.logDensity(Vector(.4)) shouldBe (-1.3087690632938632 +- 1e-12) // independent 60-digit control
      for(x<-Vector(-3.0,0.0,2.0)) {
        c.logDensity(Vector(x)) shouldBe (ref.logDensity(Vector(x)) +- 1e-11)
        c.logDensity(Vector(x))+c.logEvidence shouldBe (law.logDensity(Vector(x,2)) +- 1e-11)
      }
      val rng=SamplingRandom.scalaRandom(610002)
      val values=Vector.fill(10000)(c.sample(rng).head)
      values.sum/values.size shouldBe (1.2 +- .07)
      values.map(x=>math.pow(x-1.2,2)).sum/values.size shouldBe (1.44 +- .15)
    }
    "respect non-Gaussian transforms and arbitrary observed index order" in {
      val corr=Vector(Vector(1.0,.2,.3),Vector(.2,1.0,.4),Vector(.3,.4,1.0))
      for(df<-Vector(None,Some(4.0))) {
        val law=CopulaDistribution(Vector(LogNormalDistribution(0,.5),WeibullDistribution(2,3),GaussianDistribution(2,1)),corr,df)
        val c=law.condition(Vector(2,0),Vector(1,.8))
        c.remainingIndices shouldBe Vector(1)
        for(x<-Vector(.3,1.0,3.0)) c.logDensity(Vector(x))+c.logEvidence shouldBe (law.logDensity(Vector(.8,x,1)) +- 1e-10)
        c.sample(SamplingRandom.scalaRandom(1)).head should be > 0.0
      }
    }
    "refuse invalid conditioning and preserve cancellation" in {
      val law=CopulaDistribution(Vector.fill(2)(GaussianDistribution(0,1)),r)
      intercept[IllegalArgumentException](law.condition(Vector(0,0),Vector(1,1)))
      intercept[IllegalArgumentException](law.condition(Vector.empty,Vector.empty))
      intercept[IllegalArgumentException](law.condition(Vector(0,1),Vector(1,1)))
      intercept[ArithmeticException](law.condition(Vector(0),Vector(100)))
      val c=law.condition(Vector(0),Vector(1))
      Thread.currentThread().interrupt()
      try intercept[java.util.concurrent.CancellationException](c.sample(SamplingRandom.scalaRandom(1)))
      finally Thread.interrupted()
    }
  }
  "GVM mixture fitting" should {
    "fit equivalent angular representations without using a linear angular likelihood" in {
      val rng=SamplingRandom.scalaRandom(610009); val data=Vector.fill(250)(g(a=3).sample(rng))
      val cfg=GaussVonMisesMixtureFit.Config(restarts=1,maxIterations=8)
      val a=GaussVonMisesMixtureFit.fit(data,cfg).distribution.get
      val b=GaussVonMisesMixtureFit.fit(data.map(p=>p.copy(angle=p.angle+2*math.Pi)),cfg).distribution.get
      data.take(10).foreach(p=>a.logDensity(p) shouldBe (b.logDensity(p) +- 1e-6))
    }
    "respect explicit variance and concentration constraints on repeated observations" in {
      val data=Vector.fill(40)(LinearAngular(Vector(2),.5))
      val result=GaussVonMisesMixtureFit.fit(data,GaussVonMisesMixtureFit.Config(restarts=1,maxIterations=3,varianceFloor=.01,maxConcentration=5))
      val g=result.distribution.get.components.head
      g.covariance.head.head shouldBe .01
      g.kappa shouldBe (5.0 +- 1e-12)
      result.distribution.get.logDensity(data.head).isFinite shouldBe true
    }
    "fit a curved seam-crossing law with monotone accepted likelihoods and reproducibility" in {
      val truth=g(a=3.0); val rng=SamplingRandom.scalaRandom(610003)
      val data=Vector.fill(500)(truth.sample(rng))
      val cfg=GaussVonMisesMixtureFit.Config(restarts=1,maxIterations=10,maxAngularEvaluations=300)
      val fit=GaussVonMisesMixtureFit.fit(data,cfg)
      fit.distribution.isDefined shouldBe true
      fit.attempts.head.logLikelihoodTrace.sliding(2).filter(_.size==2).foreach(p=>p(1) should be >= p(0))
      val fitted=fit.distribution.get.components.head
      fitted.mean.head shouldBe (0.0 +- .15)
      fitted.beta.head shouldBe (.4 +- .2)
      fitted.gamma.head.head shouldBe (.7 +- .25)
      val again=GaussVonMisesMixtureFit.fit(data,cfg)
      again.attempts shouldBe fit.attempts
      again.distribution.get.logDensity(data.head) shouldBe fit.distribution.get.logDensity(data.head)
      val testRng=SamplingRandom.scalaRandom(610004)
      val test=Vector.fill(2000)(truth.sample(testRng))
      val loss=test.map(p=>truth.logDensity(p)-fit.distribution.get.logDensity(p)).sum/test.size
      loss should be < .08
    }
    "retain distinct linear modes without forcing automatic component selection" in {
      val truth=GaussVonMisesMixtureDistribution(Vector(.5,.5),Vector(g(-3,-1),g(3,1)))
      val rng=SamplingRandom.scalaRandom(610005); val data=Vector.fill(700)(truth.sample(rng))
      val fit=GaussVonMisesMixtureFit.fit(data,GaussVonMisesMixtureFit.Config(components=2,restarts=2,maxIterations=12))
      fit.attempts.size shouldBe 2
      fit.distribution.get.components.map(_.mean.head).sorted.head shouldBe (-3.0 +- .3)
      fit.distribution.get.components.map(_.mean.head).sorted.last shouldBe (3.0 +- .3)
      fit.distribution.get.weights.sum shouldBe (1.0 +- 1e-12)
    }
    "bound optimizer work and reject unsupported data" in {
      val rng=SamplingRandom.scalaRandom(610006); val data=Vector.fill(100)(g().sample(rng))
      val fit=GaussVonMisesMixtureFit.fit(data,GaussVonMisesMixtureFit.Config(restarts=1,maxIterations=1,maxAngularEvaluations=8))
      fit.attempts.head.status shouldBe GaussVonMisesMixtureFit.Status.AngularBudgetExhausted
      fit.attempts.head.angularEvaluations should be <= 36L
      intercept[IllegalArgumentException](GaussVonMisesMixtureFit.fit(data.take(5)))
      intercept[IllegalArgumentException](GaussVonMisesMixtureFit.fit(data.map(p=>p.copy(linear=Vector(0,0)))))
      Thread.currentThread().interrupt()
      try intercept[java.util.concurrent.CancellationException](GaussVonMisesMixtureFit.fit(data))
      finally Thread.interrupted()
    }
  }
  "Full mixture linear-angle MI" should {
    "agree with independent positive integration on a label-induced dependence fixture" in {
      val a=g(-1,-.8,0,0,2); val b=g(1,.8,0,0,2)
      val law=GaussVonMisesMixtureDistribution(Vector(.4,.6),Vector(a,b))
      // Separate rectangle quadrature in physical x/angle coordinates: no Fourier coefficients.
      val nx=500; val nt=256; val dx=16.0/nx; val dt=2*math.Pi/nt
      var integral=0.0
      for(ix<-0 until nx; it<-0 until nt) {
        val x= -8+(ix+.5)*dx; val t= -math.Pi+(it+.5)*dt
        val point=LinearAngular(Vector(x),t); val joint=law.density(point)
        val angle=.4*VonMisesDistribution(-.8,2).density(t)+.6*VonMisesDistribution(.8,2).density(t)
        integral+=joint*(math.log(joint)-math.log(law.linearMarginal.density(Vector(x)))-math.log(angle))*dx*dt
      }
      val result=GaussVonMisesMixtureMutualInformation.compute(law,GaussVonMisesMixtureMutualInformation.Config(draws=20000,seed=610010))
      result.value.get shouldBe (integral +- (6*result.mcse.get+1e-7))
    }
    "work in more than one linear dimension and honor interruption" in {
      val g=GaussVonMisesDistribution(Vector(0,1),Vector(Vector(1.0,0.0),Vector(0.0,2.0)),.2,Vector(.3,.2),Vector(Vector(.1,.05),Vector(.05,-.2)),3)
      val law=GaussVonMisesMixtureDistribution(Vector(1),Vector(g))
      val result=GaussVonMisesMixtureMutualInformation.compute(law)
      result.value.get shouldBe (GaussVonMisesMutualInformation.compute(g).value.get +- (6*result.mcse.get+1e-7))
      Thread.currentThread().interrupt()
      try intercept[java.util.concurrent.CancellationException](GaussVonMisesMixtureMutualInformation.compute(law))
      finally Thread.interrupted()
    }
    "recover single-component MI and distinguish component independence from mixture independence" in {
      val kernel=g(); val law=GaussVonMisesMixtureDistribution(Vector(1),Vector(kernel))
      val result=GaussVonMisesMixtureMutualInformation.compute(law,GaussVonMisesMixtureMutualInformation.Config(draws=15000,seed=610007))
      result.status shouldBe GaussVonMisesMixtureMutualInformation.Status.Estimated
      val reference=GaussVonMisesMutualInformation.compute(kernel).value.get
      result.value.get shouldBe (reference +- (6*result.mcse.get+1e-7))
      val mixed=GaussVonMisesMixtureDistribution(Vector(.5,.5),Vector(g(-3,-1,0,0,10),g(3,1,0,0,10)))
      val mi=GaussVonMisesMixtureMutualInformation.compute(mixed)
      mi.value.get should be > .5
      mi.value.get should be < .72
      val independent=GaussVonMisesMixtureDistribution(Vector(.5,.5),Vector(g(-3,1,0,0,10),g(3,1,0,0,10)))
      GaussVonMisesMixtureMutualInformation.compute(independent).value.get shouldBe 0.0
    }
    "preserve duplicate-component equivalence and expose Fourier range/budget refusals" in {
      val a=g(); val p=GaussVonMisesMixtureDistribution(Vector(.5,.5),Vector(a,a))
      val q=GaussVonMisesMixtureDistribution(Vector(1),Vector(a))
      val cfg=GaussVonMisesMixtureMutualInformation.Config(draws=1000,seed=610008)
      GaussVonMisesMixtureMutualInformation.compute(p,cfg).value.get shouldBe (GaussVonMisesMixtureMutualInformation.compute(q,cfg).value.get +- 1e-12)
      GaussVonMisesMixtureMutualInformation.compute(GaussVonMisesMixtureDistribution(Vector(1),Vector(g(k=60)))).status shouldBe GaussVonMisesMixtureMutualInformation.Status.UnsupportedRange
      GaussVonMisesMixtureMutualInformation.compute(GaussVonMisesMixtureDistribution(Vector(1),Vector(g(k=50))),cfg.copy(harmonics=8)).value shouldBe None
    }
  }
  "Wrapped Gaussian research control" should {
    "normalize its angle marginal and retain periodic density" in {
      val g=MultivariateGaussianDistribution(Vector(0,3),Vector(Vector(1.0,.5),Vector(.5,3.0)))
      val wrapped=Release61RepresentationStudy.wrapped(GaussianMixtureDistribution(Vector(1),Vector(g)))
      val n=2048; val dt=2*math.Pi/n
      val mass=(0 until n).map(i=>math.exp(wrapped.logDensity(Vector(.2,-math.Pi+(i+.5)*dt)))*dt).sum
      mass shouldBe (GaussianDistribution(0,1).density(.2) +- 1e-12)
      wrapped.logDensity(Vector(0,math.Pi)) shouldBe Double.NegativeInfinity
    }
  }
}
