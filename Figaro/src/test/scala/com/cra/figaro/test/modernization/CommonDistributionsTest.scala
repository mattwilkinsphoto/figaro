package com.cra.figaro.test.modernization

import com.cra.figaro.library.atomic.continuous.*
import com.cra.figaro.library.atomic.discrete.{NegativeBinomial,NegativeBinomialDistribution,Hypergeometric,HypergeometricDistribution,CountDistribution,CountElement}
import com.cra.figaro.language.*
import com.cra.figaro.algorithm.sampling.{Importance,MetropolisHastings,ProposalScheme}
import com.cra.figaro.algorithm.sampling.parallel.MultiChainMetropolisHastings as MH
import com.cra.figaro.util.withRandomSeed
import java.util.concurrent.{CancellationException,Callable,Executors,TimeUnit}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class CommonDistributionsTest extends AnyWordSpec with Matchers {
  private val scalars: Vector[ScalarDistribution]=Vector(StudentTDistribution(5,.3,1.2),CauchyDistribution(.2,1.3),
    LaplaceDistribution(-.2,.7),LogNormalDistribution(.3,.8),WeibullDistribution(1.7,2.3),
    TriangularDistribution(-1,.3,2),KumaraswamyDistribution(1.4,2.3))
  private val counts: Vector[CountDistribution]=Vector(NegativeBinomialDistribution(2.5,.4),HypergeometricDistribution(20,7,5))
  "Common scalar/count kernels" should {
    "match independent 70-digit log-density and cumulative fixtures" in {
      for((d,x,log,cdf,sf) <- CommonDistributionFixtures.scalar) withClue(s"$d at $x: ") {
        if(log.isInfinity) d.logDensity(x) shouldBe log else d.logDensity(x) shouldBe (log +- (1e-10*(1+math.abs(log))))
        d.cdf(x) shouldBe (cdf +- 2e-12)
        d.survival(x) shouldBe (sf +- math.max(1e-300,math.abs(sf)*2e-11))
      }
      for((d,k,log,cdf) <- CommonDistributionFixtures.count) withClue(s"$d at $k: ") {
        d.logProbability(k) shouldBe (log +- 2e-11)
        d.cdf(k) shouldBe (cdf +- 2e-12)
        d.survival(k) shouldBe (1-cdf +- 2e-12)
      }
    }
    "invert CDFs and retain direct tails after a rounded CDF reaches one" in {
      for(d <- scalars; p <- Vector(1e-8,.01,.2,.5,.9,1-1e-8)) withClue(s"$d at $p: ") {
        d.cdf(d.quantile(p)) shouldBe (p +- 2e-10)
        d.cdf(d.quantile(p))+d.survival(d.quantile(p)) shouldBe (1.0 +- 2e-14)
      }
      for(d <- scalars) {
        d.quantile(0) shouldBe d.support._1; d.quantile(1) shouldBe d.support._2
        d.cdf(Double.NegativeInfinity) shouldBe 0.0; d.cdf(Double.PositiveInfinity) shouldBe 1.0
      }
      for(d <- counts; p <- Vector(.0001,.1,.5,.99,.9999)) {
        val k=d.quantile(p); d.cdf(k) should be >= p; d.cdf(k-1) should be < p
      }
      CauchyDistribution(0,1).survival(1e100) should be > 0.0
      CauchyDistribution(0,1).cdf(-0.0) shouldBe .5
      CauchyDistribution(0,1).survival(0.0) shouldBe .5
      val subnormalTail=(.1/1e308)/math.Pi
      CauchyDistribution(0,.1).survival(1e308) shouldBe subnormalTail
      CauchyDistribution(0,.1).cdf(-1e308) shouldBe subnormalTail
      subnormalTail should be > 0.0
      StudentTDistribution(.001).survival(1e300) should be > .1
      StudentTDistribution(5,0,1e-100).logDensity(1e300).isFinite shouldBe true
      CauchyDistribution(0,1e-100).logDensity(1e300).isFinite shouldBe true
      LaplaceDistribution(0,1).cdf(100) shouldBe 1.0
      LaplaceDistribution(0,1).survival(100) should be > 0.0
      LogNormalDistribution(0,1).survival(math.exp(10)) should be > 0.0
      // Probability-coordinate inverses must not lose a representable result in
      // an intermediate product, reciprocal, or subtraction from one.
      val lognormal=LogNormalDistribution(0,1)
      for(p <- Vector(1e-8,1e-12,1e-100,1e-250)) lognormal.cdf(lognormal.quantile(p))/p shouldBe (1.0 +- 2e-11)
      // Independent 100-digit mpmath erfc inversion, not the Scala CDF round trip.
      lognormal.quantile(1e-100) shouldBe (5.7684151320867909669e-10 +- 2e-23)
      lognormal.quantile(1e-250) shouldBe (2.0942389594866970489e-15 +- 1e-28)
      val cauchy=CauchyDistribution(0,1e-100)
      cauchy.cdf(cauchy.quantile(1e-250))/1e-250 shouldBe (1.0 +- 2e-13)
      val triangle=TriangularDistribution(0,.5,1)
      triangle.quantile(1e-300) shouldBe (math.sqrt(.5)*1e-150 +- 1e-164)
      val skewTriangle=TriangularDistribution(0,1e-200,1)
      skewTriangle.quantile(1e-100)/1e-100 shouldBe (.5 +- 1e-14)
      val bounded=KumaraswamyDistribution(2,1e6)
      bounded.cdf(bounded.quantile(1e-320)) shouldBe (1e-320 +- 1e-323)
      for(d <- Vector[CountDistribution](NegativeBinomialDistribution(2.5,.4),HypergeometricDistribution(100,50,50))) {
        val p=math.nextDown(1.0); val k=d.quantile(p)
        d.survival(k) should be <= (1-p); d.survival(k-1) should be > (1-p)
      }
      HypergeometricDistribution(100,50,50).quantile(1) shouldBe 50
    }
    "honor special cases, moment existence and endpoint singularities" in {
      for(x <- Vector(-100.0,-1.0,0.0,1.0,100.0)) {
        StudentTDistribution(1,2,3).logDensity(x) shouldBe (CauchyDistribution(2,3).logDensity(x) +- 1e-13)
        StudentTDistribution(1,2,3).cdf(x) shouldBe (CauchyDistribution(2,3).cdf(x) +- 1e-13)
      }
      StudentTDistribution(1).mean shouldBe None; StudentTDistribution(1).variance shouldBe None
      StudentTDistribution(2).variance shouldBe Some(Double.PositiveInfinity)
      StudentTDistribution(5,2,3).variance shouldBe Some(15.0)
      CauchyDistribution(0,1).mean shouldBe None
      WeibullDistribution(1,2).logDensity(0) shouldBe -math.log(2)
      WeibullDistribution(.5,2).logDensity(0) shouldBe Double.PositiveInfinity
      KumaraswamyDistribution(.5,2).logDensity(0) shouldBe Double.PositiveInfinity
      KumaraswamyDistribution(2,.5).logDensity(1) shouldBe Double.PositiveInfinity
      KumaraswamyDistribution(1,1).mean.get shouldBe (.5 +- 1e-15)
      KumaraswamyDistribution(1,1).variance.get shouldBe (1.0/12 +- 1e-15)
      // High-shape Weibull variance cannot subtract two nearly equal raw moments.
      WeibullDistribution(1e6,1).variance.get shouldBe (1.6449297637827162e-12 +- 1e-23)
      intercept[ArithmeticException] { KumaraswamyDistribution(1e6,.001).variance }
      for(k <- 0 to 10) NegativeBinomialDistribution(1,.4).probability(k) shouldBe (.4*math.pow(.6,k) +- 1e-14)
      NegativeBinomialDistribution(2,1).quantile(1) shouldBe 0
      HypergeometricDistribution(1,1,1).variance shouldBe 0.0
      HypergeometricDistribution(20,0,5).quantile(.9) shouldBe 0
      HypergeometricDistribution(20,7,20).quantile(.9) shouldBe 7
    }
    "sample reproducibly and recover distribution-level probabilities" in {
      for(d <- scalars) withClue(d.toString+": ") {
        val a=new scala.util.Random(781); val b=new scala.util.Random(781)
        Vector.fill(20)(d.sample(a)) shouldBe Vector.fill(20)(d.sample(b))
        val rng=new scala.util.Random(111)
        val below=Vector.fill(3000)(d.sample(rng)).count(_ <= d.quantile(.3)).toDouble/3000
        below shouldBe (.3 +- .035)
      }
      for(d <- counts) {
        val rng=new scala.util.Random(321); val xs=Vector.fill(5000)(d.sample(rng))
        xs.sum.toDouble/xs.size shouldBe (d.mean +- 6*math.sqrt(d.variance/xs.size))
        xs.forall(k => k >= d.support._1 && d.support._2.forall(k <= _)) shouldBe true
      }
    }
    "reject invalid input and fail explicitly on unrepresentable quantiles or bad RNGs" in {
      intercept[IllegalArgumentException] { StudentTDistribution(0) }
      intercept[IllegalArgumentException] { CauchyDistribution(0,0) }
      intercept[IllegalArgumentException] { LogNormalDistribution(0,-1) }
      intercept[IllegalArgumentException] { WeibullDistribution(Double.NaN,1) }
      intercept[IllegalArgumentException] { TriangularDistribution(0,0,1) }
      intercept[IllegalArgumentException] { KumaraswamyDistribution(1,0) }
      intercept[IllegalArgumentException] { NegativeBinomialDistribution(1,0) }
      intercept[IllegalArgumentException] { HypergeometricDistribution(10,11,1) }
      for(d <- scalars) { intercept[IllegalArgumentException] { d.cdf(Double.NaN) }; intercept[IllegalArgumentException] { d.quantile(-.1) } }
      intercept[ArithmeticException] { NegativeBinomialDistribution(2,.5).quantile(1) }
      intercept[ArithmeticException] { StudentTDistribution(.001).quantile(.01) }
      intercept[ArithmeticException] { LogNormalDistribution(500,50).quantile(1-1e-12) }
      intercept[ArithmeticException] { WeibullDistribution(.001,1).quantile(1e-10) }
      val broken=new scala.util.Random { override def nextDouble(): Double = 0 }
      intercept[ArithmeticException] { scalars.head.sample(broken) }
      try { Thread.currentThread().interrupt(); intercept[CancellationException] { scalars.head.sample(new scala.util.Random) }; Thread.currentThread().isInterrupted shouldBe true }
      finally { Thread.interrupted() }
    }
    "share kernels concurrently without shared random state" in {
      val pool=Executors.newFixedThreadPool(3)
      try {
        val kernel=StudentTDistribution(5)
        def draws=Vector.fill(30)(kernel.sample(new scala.util.Random(1)))
        val expected=draws
        val tasks=Vector.fill(6)(pool.submit(new Callable[Vector[Double]] { def call() = draws }))
        tasks.foreach(_.get(10,TimeUnit.SECONDS) shouldBe expected)
      } finally pool.shutdownNow()
    }
  }

  "New Figaro distribution elements" should {
    "register all named factories, reject bad parameters before registration, and use scoped RNGs" in {
      val u=new Universe
      try {
        val named=Vector(StudentT(5)(using "t",u),Cauchy(0,1)(using "c",u),Laplace(0,1)(using "l",u),
          LogNormal(0,1)(using "ln",u),Weibull(2,1)(using "w",u),Triangular(0,.3,1)(using "tr",u),Kumaraswamy(2,3)(using "k",u))
        val discrete=Vector(NegativeBinomial(2,.4)(using "nb",u),Hypergeometric(20,7,5)(using "hg",u))
        for(e <- named) withRandomSeed(12) { val x=e.generateRandomness(); e.logp(x) shouldBe e.distribution.logDensity(x) }
        for(e <- discrete) e.logDensity(2) shouldBe e.distribution.logProbability(2)
        val before=u.activeElements.size
        intercept[IllegalArgumentException] { Weibull(-1,2)(using "bad",u) }; u.activeElements.size shouldBe before
        intercept[ArithmeticException] { ScalarElement(WeibullDistribution(.5,1))(using "boundary",u).logDensity(0) }
        withRandomSeed(13)(Vector.fill(10)(named.head.generateRandomness())) shouldBe withRandomSeed(13)(Vector.fill(10)(named.head.generateRandomness()))
      } finally u.clear()
    }
    "retain log likelihoods when observed raw densities underflow" in {
      val u=Universe.createNew(); val e=Laplace(0,1)(using "x",u); e.observe(1000)
      val alg=Importance(30,e)
      try { alg.start(); e.density(1000) shouldBe 0.0; alg.getTotalWeight shouldBe (math.log(30)+e.logDensity(1000) +- 1e-10) }
      finally { if(alg.isActive) alg.kill(); u.clear() }
    }
    "support conditional-kernel observations with enumerated posterior odds" in {
      val pairs=scalars.map(p => (p,scalars.head: ScalarDistribution))
      for((p,q) <- pairs) withRandomSeed(617) {
        val u=Universe.createNew(); val choice=Flip(.4)(using "choice",u)
        val selected=Apply(choice,(b: Boolean) => if(b) p else q)
        val e=ScalarElement(selected); val obs=.2; e.observe(obs)
        val expected=1/(1+1.5*math.exp(q.logDensity(obs)-p.logDensity(obs)))
        val alg=Importance(8000,choice)
        try { alg.start(); alg.probability(choice,true) shouldBe (expected +- .035) }
        finally { if(alg.isActive) alg.kill(); u.clear() }
      }
      for(p <- counts) withRandomSeed(617) {
        val u=Universe.createNew(); val choice=Flip(.4)(using "choice",u); val q=counts.head
        val e=CountElement(Apply(choice,(b: Boolean) => if(b) p else q)); e.observe(2)
        val expected=1/(1+1.5*math.exp(q.logProbability(2)-p.logProbability(2)))
        val alg=Importance(8000,choice)
        try { alg.start(); alg.probability(choice,true) shouldBe (expected +- .035) }
        finally { if(alg.isActive) alg.kill(); u.clear() }
      }
    }
    "recover constrained posteriors with ordinary MH for every new family" in {
      for(d <- scalars) withRandomSeed(33) {
        val u=Universe.createNew(); val e=ScalarElement(d)(using "x",u); val threshold=d.quantile(.5)
        e.addConstraint((x: Double) => if(x <= threshold) 2.0 else 1.0)
        val alg=MetropolisHastings(7000,ProposalScheme.default(using u),300,e)
        try { alg.start(); alg.probability(e,(x: Double) => x <= threshold) shouldBe (2.0/3 +- .04) }
        finally { if(alg.isActive) alg.kill(); u.clear() }
      }
      for(d <- counts) withRandomSeed(33) {
        val u=Universe.createNew(); val e=CountElement(d)(using "x",u); val threshold=d.quantile(.5); val mass=d.cdf(threshold)
        e.addConstraint((x: Int) => if(x <= threshold) 2.0 else 1.0)
        val alg=MetropolisHastings(7000,ProposalScheme.default(using u),300,e)
        try { alg.start(); alg.probability(e,(x: Int) => x <= threshold) shouldBe (2*mass/(1+mass) +- .04) }
        finally { if(alg.isActive) alg.kill(); u.clear() }
      }
    }
    "preserve seeded multi-chain results as parallelism changes" in {
      for(d <- scalars) {
        def model(u: Universe,i: Int): MH.Model = { val e=ScalarElement(d)(using "x",u); MH.Model(Vector(MH.Observable("cdf",e)(d.cdf))) }
        val c=MH.Config(chains=2,drawsPerChain=150,warmUp=20,parallelism=1,seed=92L)
        MH.run(c)(model).chains.map(_.draws) shouldBe MH.run(c.copy(parallelism=2))(model).chains.map(_.draws)
      }
      for(d <- counts) {
        def model(u: Universe,i: Int): MH.Model = { val e=CountElement(d)(using "x",u); MH.Model(Vector(MH.Observable("count",e)(_.toDouble))) }
        val c=MH.Config(chains=2,drawsPerChain=150,warmUp=20,parallelism=1,seed=92L)
        MH.run(c)(model).chains.map(_.draws) shouldBe MH.run(c.copy(parallelism=2))(model).chains.map(_.draws)
      }
    }
  }
}
