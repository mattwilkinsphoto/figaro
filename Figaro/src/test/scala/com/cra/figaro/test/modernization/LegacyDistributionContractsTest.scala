package com.cra.figaro.test.modernization

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import com.cra.figaro.language.*
import com.cra.figaro.library.atomic.{LegacyInformation as I,InformationMetricStatus as S}
import com.cra.figaro.library.atomic.continuous.*
import com.cra.figaro.library.atomic.discrete.{Poisson,Geometric,Binomial}
import com.cra.figaro.algorithm.sampling.{Importance,MetropolisHastings,ProposalScheme}
import com.cra.figaro.util.withRandomSeed

class LegacyDistributionContractsTest extends AnyWordSpec with Matchers {
  private def owned(body: Universe => Unit): Unit={ val u=Universe.createNew(); try body(u) finally u.clear() }
  "Legacy log-density contracts" should {
    "score observed log-density elements without generating discarded prior randomness" in owned { u =>
      val e=new AtomicGamma("",2,1,u) {
        override def generateRandomness(): Double=throw new IllegalStateException("must not draw an observed prior")
      }
      e.observe(2); val a=Importance(10,e)(using u)
      try { a.start(); a.getTotalWeight shouldBe (math.log(10)+e.logDensity(2) +- 1e-12) }
      finally if(a.isActive) a.kill()
    }
    "agree with exact limits and reject malformed or unsupported values" in owned { u =>
      Gamma(1,2)(using "",u).logp(0) shouldBe -math.log(2)
      Gamma(2,2)(using "",u).logp(0) shouldBe Double.NegativeInfinity
      Gamma(.5,2)(using "",u).logp(0) shouldBe Double.PositiveInfinity
      Beta(1,1)(using "",u).logp(0) shouldBe 0.0
      Beta(1,1)(using "",u).logp(1) shouldBe 0.0
      Beta(2,3)(using "",u).logp(-.1) shouldBe Double.NegativeInfinity
      Uniform(-2,2)(using "",u).logp(0) shouldBe -math.log(4)
      Uniform(-2,2)(using "",u).logp(2) shouldBe Double.NegativeInfinity
      Exponential(2)(using "",u).logp(-1) shouldBe Double.NegativeInfinity
      InverseGamma(3,2)(using "",u).logp(0) shouldBe Double.NegativeInfinity
      val d=Dirichlet(1.0,1.0,1.0)(using "",u)
      d.logp(Array(0.0,.5,.5)) shouldBe (math.log(2) +- 1e-14)
      d.logp(Array(.2,.2,.2)) shouldBe Double.NegativeInfinity
      intercept[IllegalArgumentException](d.logp(Array(.5,.5)))
      intercept[IllegalArgumentException](d.logp(Array(Double.NaN,.5,.5)))
      intercept[IllegalArgumentException](Normal(0,0)(using "",u))
      intercept[IllegalArgumentException](Gamma(0)(using "",u))
      intercept[IllegalArgumentException](Beta(-1,2)(using "",u))
      intercept[IllegalArgumentException](Uniform(2,2)(using "",u))
      intercept[IllegalArgumentException](Geometric(1)(using "",u))
      intercept[IllegalArgumentException](Binomial(-1,.5)(using "",u))
      val mu=Constant(List(0.0))(using "",u); mu.generate()
      val cov=Constant(List(List(1.0)))(using "",u); cov.generate()
      MultivariateNormal(mu,List(List(1.0)))(using "",u).logp(List(0.0)) shouldBe (-.5*math.log(2*math.Pi) +- 1e-14)
      MultivariateNormal(mu,cov)(using "",u).logp(List(0.0)) shouldBe (-.5*math.log(2*math.Pi) +- 1e-14)
    }
    "score extreme observations directly in log space through actual Importance" in owned { u =>
      def check[A](e: Element[A] & HasLogDensity[A],x: A): Unit={
        e.logDensity(x).isFinite shouldBe true; e.density(x) shouldBe 0.0
        e.observe(x); val a=Importance(10,e)(using u)
        try { a.start(); a.getTotalWeight shouldBe (math.log(10)+e.logDensity(x) +- 1e-9) }
        finally { if(a.isActive) a.kill(); e.unobserve(); e.deactivate() }
      }
      check(Normal(0,1)(using "",u),40.0)
      check(Gamma(2,1)(using "",u),1000.0)
      check(InverseGamma(2,1)(using "",u),.001)
      check(Beta(3,1)(using "",u),1e-200)
      check(Exponential(1)(using "",u),1000.0)
      check(Dirichlet(3.0,1.0)(using "",u),Array(1e-200,1.0))
      check(Poisson(20)(using "",u),1000)
      check(Binomial(2000,.5)(using "",u),1)
      check(Geometric(.1)(using "",u),1000)
    }
    "match non-unit inverse-gamma sampling to its density and large Poisson rates" in owned { u =>
      withRandomSeed(42) {
        val d=InverseGamma(4,7)(using "",u)
        d.generateValue(2) shouldBe 3.5
        d.logp(2) shouldBe (4*math.log(7)-math.log(6)-5*math.log(2)-3.5 +- 1e-14)
        val xs=Vector.fill(20000)(d.generateValue(d.generateRandomness()))
        xs.sum/xs.size shouldBe (7.0/3 +- .1)
        val p=Poisson(1000)(using "",u); val counts=Vector.fill(10000)(p.generateRandomness())
        counts.sum.toDouble/counts.size shouldBe (1000.0 +- 2.0)
        counts.map(k => math.pow(k-1000.0,2)).sum/counts.size shouldBe (1000.0 +- 70.0)
      }
    }
    "keep learned summaries distinct from the sampling prior" in owned { u =>
      val b=Beta(2,3)(using "",u); val old=b.logp(.4)
      val draws=withRandomSeed(7)(Vector.fill(20)(b.generateRandomness()))
      b.maximize(Vector(10,0)); b.expectedValue shouldBe .8
      b.logp(.4) shouldBe old
      withRandomSeed(7)(Vector.fill(20)(b.generateRandomness())) shouldBe draws
      val d=Dirichlet(2.0,3.0)(using "",u); val lp=d.logp(Array(.4,.6))
      d.maximize(Vector(10,0)); d.expectedValue.toVector shouldBe Vector(.8,.2)
      d.logp(Array(.4,.6)) shouldBe lp
    }
    "handle degenerate count proposals and retain finite ratios in tiny-density tails" in owned { u =>
      val p=Poisson(0)(using "",u); p.generateRandomness() shouldBe 0
      p.nextRandomness(0) shouldBe ((0,1.0,1.0))
      val g=Geometric(0)(using "",u); g.generateRandomness() shouldBe 1
      g.nextRandomness(1) shouldBe ((1,1.0,1.0))
      val b=Binomial(1,.3)(using "",u)
      val next=b.nextRandomness(0); next._1 shouldBe 1
      next._2 shouldBe 1.0; next._3 shouldBe (.3/.7 +- 1e-14)
      Binomial(0,.5)(using "",u).nextRandomness(0) shouldBe ((0,1.0,1.0))
      val tail=Poisson(1000)(using "",u).nextRandomness(0)
      tail._1 shouldBe 1; tail._2.isFinite shouldBe true; tail._3 shouldBe (1000.0 +- 1e-9)
    }
    "preserve hierarchical observed posterior odds" in withRandomSeed(43) { owned { u =>
      val choice=Flip(.4)(using "",u)
      val shape=choice.map(b => if(b) 2.0 else 3.0)(using "",u)
      val e=Gamma(shape)(using "",u); e.observe(2.0)
      val a=Importance(15000,choice)(using u)
      try { a.start(); a.probability(choice,true) shouldBe (.4 +- .02) }
      finally if(a.isActive) a.kill()
    } }
    "preserve geometric proposal balance and pooled multi-seed accuracy" in {
      val estimates=(0 until 8).map { seed => withRandomSeed(12000+seed) { ownedValue { u =>
        val e=Geometric(.9)(using "",u)
        def q(x: Int,y: Int): Double = if(x==1) 1.0 else if(y<x) 1/(1+.9*.9) else .9*.9/(1+.9*.9)
        for(x <- 1 to 30; repeat <- 1 to 5) {
          val (y,proposal,model)=e.nextRandomness(x)
          proposal shouldBe (q(y,x)/q(x,y) +- 1e-12)
          model shouldBe (math.pow(.9,y-x) +- 1e-12)
        }
        val a=MetropolisHastings(100000,ProposalScheme.default(using u),1000,e)
        try { a.start(); a.probability(e,3) } finally if(a.isActive) a.kill()
      } } }
      info("Geometric p(X=3) across eight predeclared seeds: "+estimates.mkString(", "))
      estimates.sum/estimates.size shouldBe (.081 +- .005)
    }
    "run constrained MH and replay independent chains for continuous and count priors" in {
      import com.cra.figaro.algorithm.sampling.parallel.{MultiChainMetropolisHastings as MH}
      def check[A](make: Universe => Element[A],f: A => Double): Unit={
        def model(u: Universe,i: Int): MH.Model={ val e=make(u); MH.Model(Vector(MH.Observable("x",e)(f))) }
        val c=MH.Config(chains=2,drawsPerChain=100,warmUp=20,parallelism=1,seed=17)
        MH.run(c)(model).chains.map(_.draws) shouldBe MH.run(c.copy(parallelism=2))(model).chains.map(_.draws)
      }
      check[Double](u => Gamma(3,2)(using "",u),identity)
      check[Double](u => InverseGamma(4,7)(using "",u),identity)
      check[Double](u => Beta(2,3)(using "",u),identity)
      check[Array[Double]](u => Dirichlet(2.0,3.0)(using "",u),_(0))
      check[Int](u => Poisson(5)(using "",u),_.toDouble)
      check[Int](u => Binomial(10,.3)(using "",u),_.toDouble)
      check[Int](u => Geometric(.5)(using "",u),_.toDouble)
      withRandomSeed(91) { owned { u =>
        val e=Binomial(1,.3)(using "",u); e.addConstraint((x: Int) => if(x==1) 2.0 else 1.0)
        val a=MetropolisHastings(10000,ProposalScheme.default(using u),200,e)
        try { a.start(); a.probability(e,1) shouldBe (6.0/13 +- .025) }
        finally if(a.isActive) a.kill()
      } }
    }
    "honor cancellation without clearing the interrupt flag" in owned { u =>
      val g=Gamma(2)(using "",u); val p=Poisson(1000)(using "",u)
      try { Thread.currentThread().interrupt()
        intercept[java.util.concurrent.CancellationException](g.generateRandomness())
        intercept[java.util.concurrent.CancellationException](p.generateRandomness())
        Thread.currentThread().isInterrupted shouldBe true
      } finally Thread.interrupted()
    }
  }
  private def ownedValue[A](body: Universe => A): A={ val u=Universe.createNew(); try body(u) finally u.clear() }
  "Legacy information measures" should {
    "match independent integral fixtures and exact count reductions" in {
      I.gammaKl(2,3,4,5).value.get shouldBe (2.1894932940950835 +- 1e-12)
      I.gammaBhattacharyya(2,3,4,5).value.get shouldBe (.5549531476434343 +- 1e-12)
      // Other numeric fixtures are independently regenerated by tools/test_legacy_information_reference.py.
      I.gammaKl(1,1,1,2).value.get shouldBe (math.log(2)-.5 +- 1e-14)
      I.inverseGammaKl(2,3,4,5).value.get shouldBe (I.gammaKl(2,1.0/3,4,1.0/5).value.get +- 1e-12)
      I.betaKl(2,3,4,5).value shouldBe I.dirichletKl(Vector(2,3),Vector(4,5)).value
      I.poissonKl(2,5).value.get shouldBe (2*math.log(.4)+3 +- 1e-14)
      I.poissonBhattacharyya(2,5).value.get shouldBe (.5*math.pow(math.sqrt(2)-math.sqrt(5),2) +- 1e-14)
      I.geometricKl(.2,.6).value.get shouldBe (math.log(2)+.25*math.log(1.0/3) +- 1e-14)
      I.binomialKl(10,.2,.4).value.get shouldBe (10*(.2*math.log(.5)+.8*math.log(4.0/3)) +- 1e-13)
      I.poissonKl(1,0).status shouldBe S.Infinite
      I.geometricKl(.5,0).status shouldBe S.Infinite
      I.gammaKl(2,3,2,3).value shouldBe Some(0.0)
      I.gammaKl(2,3,4,5,1e-20).status shouldBe S.NumericallyUnresolved
    }
  }
}
