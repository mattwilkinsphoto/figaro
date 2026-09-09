package com.cra.figaro.test.modernization

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import com.cra.figaro.library.atomic.continuous.*
import com.cra.figaro.library.atomic.discrete.{MultinomialDistribution,Multinomial,MultinomialInformation}
import com.cra.figaro.library.atomic.{InformationMetricStatus as Status}
import com.cra.figaro.language.*
import com.cra.figaro.algorithm.sampling.{Importance,MetropolisHastings,ProposalScheme}
import com.cra.figaro.util.{SamplingRandom,withRandomSeed}

class DistributionBreadthTest extends AnyWordSpec with Matchers {
  private val p=WishartDistribution(5,Vector(Vector(2.0,.3),Vector(.3,1.0)))
  private val q=WishartDistribution(8,Vector(Vector(1.0,.1),Vector(.1,2.0)))
  private val north=VonMisesFisher3Distribution(Vector(0,0,1),3)
  private val east=VonMisesFisher3Distribution(Vector(1,0,0),7)
  private val count=MultinomialDistribution(4,Vector(.2,.3,.5))
  private def rng=SamplingRandom.scalaRandom(42)

  "Extreme-value kernels" should {
    "match 48 independent differentiated-CDF and inverse fixtures" in {
      for((kind,shape,u,x,lp) <- BreadthFixtures.scalars) {
        val d: ScalarDistribution=if(kind=="GEV") GeneralizedExtremeValueDistribution(shape,.3,1.7) else GeneralizedParetoDistribution(shape,.3,1.7)
        d.quantile(u) shouldBe (x +- 1e-11*math.max(1,math.abs(x)))
        d.logDensity(x) shouldBe (lp +- 1e-10)
        d.cdf(x) shouldBe (u +- 1e-11)
        d.survival(x) shouldBe (1-u +- 1e-11)
      }
    }
    "honor support, endpoint densities and undefined moments" in {
      for(d <- Vector[ScalarDistribution](GeneralizedExtremeValueDistribution(-2),GeneralizedParetoDistribution(-2))) {
        d.logDensity(d.support._2) shouldBe Double.PositiveInfinity
        d.logDensity(d.support._2+1) shouldBe Double.NegativeInfinity
        d.cdf(d.support._2) shouldBe 1.0; d.survival(d.support._2) shouldBe 0.0
        d.quantile(0) shouldBe d.support._1; d.quantile(1) shouldBe d.support._2
      }
      GeneralizedParetoDistribution(-1,2,3).density(3) shouldBe (1.0/3 +- 1e-15)
      GeneralizedExtremeValueDistribution(-1,0,2).density(2) shouldBe .5
      GeneralizedExtremeValueDistribution(0).mean.get shouldBe (.5772156649015329 +- 1e-14)
      GeneralizedExtremeValueDistribution(1e-8).variance.get shouldBe (math.Pi*math.Pi/6 +- 1e-7)
      GeneralizedParetoDistribution(.2,1,2).mean.get shouldBe 3.5
      GeneralizedExtremeValueDistribution(1).mean shouldBe None
      GeneralizedParetoDistribution(.5).variance shouldBe Some(Double.PositiveInfinity)
      intercept[IllegalArgumentException](GeneralizedExtremeValueDistribution(1e-12))
      intercept[IllegalArgumentException](GeneralizedParetoDistribution(-3))
      intercept[IllegalArgumentException](GeneralizedExtremeValueDistribution(.2).cdf(Double.NaN))
      intercept[ArithmeticException](GeneralizedParetoDistribution(-2).quantile(math.nextDown(1.0)))
      val tail=GeneralizedParetoDistribution(2,0,1e-100)
      tail.survival(1e300) shouldBe (math.exp(-.5*(math.log(2)+400*math.log(10))) +- 1e-213)
      tail.logDensity(1e300).isFinite shouldBe true
      GeneralizedExtremeValueDistribution(2,0,1e-100).logDensity(1e300).isFinite shouldBe true
    }
    "sample both shapes and the zero limits with uniform probability transforms" in {
      for(kind <- Vector("GEV","GPD"); shape <- Vector(-.2,0.0,.2)) {
        val d: ScalarDistribution=if(kind=="GEV") GeneralizedExtremeValueDistribution(shape) else GeneralizedParetoDistribution(shape)
        val r=rng; val u=Vector.fill(12000)(d.cdf(d.sample(r)))
        u.sum/u.size shouldBe (.5 +- .012)
        u.count(_<.1).toDouble/u.size shouldBe (.1 +- .012)
      }
    }
    "provide analytic Gumbel/exponential comparisons and guarded general-shape integration" in {
      val a=GeneralizedExtremeValueDistribution(0,1,2); val b=GeneralizedExtremeValueDistribution(0,0,2)
      ScalarDivergence.kl(a,b).value.get shouldBe (.5+math.exp(-.5)-1 +- 1e-12)
      ScalarDivergence.bhattacharyya(a,b).value.get shouldBe (math.log(math.cosh(.25)) +- 1e-12)
      val g=GeneralizedParetoDistribution(0,0,2); val h=GeneralizedParetoDistribution(0,0,3)
      ScalarDivergence.kl(g,h).value.get shouldBe (math.log(1.5)+2.0/3-1 +- 1e-12)
      ScalarDivergence.bhattacharyya(g,h).value.get shouldBe (math.log(5/(2*math.sqrt(6))) +- 1e-12)
      ScalarDivergence.kl(GeneralizedParetoDistribution(.2),GeneralizedParetoDistribution(-.2)).status shouldBe Status.Infinite
      val numeric=ScalarDivergence.bhattacharyya(GeneralizedParetoDistribution(.1),GeneralizedParetoDistribution(.2),1e-5)
      numeric.status shouldBe Status.Estimated; numeric.value.get shouldBe (BreadthFixtures.gpdBh +- numeric.errorEstimate)
      val kl=ScalarDivergence.kl(GeneralizedParetoDistribution(.1),GeneralizedParetoDistribution(.2),1e-5)
      kl.status shouldBe Status.Estimated; kl.value.get shouldBe (BreadthFixtures.gpdKl +- kl.errorEstimate)
      ScalarDivergence.kl(GeneralizedExtremeValueDistribution(.2),GeneralizedExtremeValueDistribution(.2,-.1),maxEvaluations=1).status shouldBe Status.BudgetExhausted
    }
  }
  "Multinomial joint counts" should {
    "normalize the complete finite support and agree with binomial special cases" in {
      val points=for(i <- 0 to 4;j <- 0 to 4-i) yield Vector(i,j,4-i-j)
      points.map(count.probability).sum shouldBe (1.0 +- 1e-13)
      MultinomialDistribution(8,Vector(.3,.2,.5)).probability(Vector(1,3,4)) shouldBe (.042 +- 1e-13)
      count.logProbability(Vector(-1,2,3)) shouldBe Double.NegativeInfinity
      count.logProbability(Vector(1,1,1)) shouldBe Double.NegativeInfinity
      count.mean shouldBe Vector(.8,1.2,2.0)
      count.covariance(0)(1) shouldBe (-.24 +- 1e-15)
      MultinomialDistribution(0,Vector(.2,.8)).sample(rng) shouldBe Vector(0,0)
      MultinomialDistribution(1000000,Vector(0.0,1.0,0.0)).sample(rng) shouldBe Vector(0,1000000,0)
      intercept[IllegalArgumentException](MultinomialDistribution(3,Vector(.2,.7)))
      intercept[IllegalArgumentException](count.logProbability(Vector(1)))
    }
    "sample supported counts with correct means and scoped replay" in {
      val r=rng; val values=Vector.fill(15000)(count.sample(r))
      all(values.map(_.sum)) shouldBe 4
      for(i <- 0 until 3) values.map(_(i)).sum.toDouble/values.size shouldBe (count.mean(i) +- .025)
      val a=rng; val b=rng
      Vector.fill(20)(count.sample(a)) shouldBe Vector.fill(20)(count.sample(b))
      MultinomialDistribution(1000000,Vector(.2,.8)).sample(rng).sum shouldBe 1000000
    }
    "compare joint count laws and compute dependence between complementary blocks" in {
      val other=MultinomialDistribution(4,Vector(.4,.2,.4))
      val points=for(i <- 0 to 4;j <- 0 to 4-i) yield Vector(i,j,4-i-j)
      val kl=points.map(x => count.probability(x)*(count.logProbability(x)-other.logProbability(x))).sum
      val bh= -math.log(points.map(x => math.sqrt(count.probability(x)*other.probability(x))).sum)
      MultinomialInformation.kl(count,other).value.get shouldBe (kl +- 1e-12)
      MultinomialInformation.bhattacharyya(count,other).value.get shouldBe (bh +- 1e-12)
      val bin=MultinomialDistribution(4,Vector(.2,.8))
      val entropy= -(0 to 4).map(k => { val lp=bin.logProbability(Vector(k,4-k)); math.exp(lp)*lp }).sum
      MultinomialInformation.mutualInformation(count,Vector(0)).value.get shouldBe (entropy +- 1e-12)
      MultinomialInformation.mutualInformation(count,Vector(1,2)).value.get shouldBe (entropy +- 1e-12)
      MultinomialInformation.mutualInformation(count,Vector(0),maxTerms=1).status shouldBe Status.BudgetExhausted
      MultinomialInformation.kl(count,other.copy(trials=5)).status shouldBe Status.Infinite
      MultinomialInformation.kl(count,MultinomialDistribution(4,Vector(0,.5,.5))).status shouldBe Status.Infinite
      MultinomialInformation.kl(count,count).value shouldBe Some(0.0)
    }
  }
  "Wishart matrices" should {
    "match an independent log density and chi-square reduction" in {
      p.logDensity(Vector(Vector(4.0,1.0),Vector(1.0,3.0))) shouldBe (BreadthFixtures.wishartLogDensity +- 1e-12)
      WishartDistribution(4,Vector(Vector(1.0))).density(Vector(Vector(3.0))) shouldBe (3*math.exp(-1.5)/4 +- 1e-14)
      p.mean(0)(0) shouldBe 10.0; p.covariance(0,0,0,0) shouldBe 40.0
      intercept[IllegalArgumentException](WishartDistribution(2,p.scale))
      intercept[IllegalArgumentException](WishartDistribution(5,Vector(Vector(1.0,2.0),Vector(2.0,1.0))))
      intercept[IllegalArgumentException](p.logDensity(Vector(Vector(1.0,0.0),Vector(1.0,1.0))))
      intercept[IllegalArgumentException](p.logDensity(Vector(Vector(1.0,1.0),Vector(1.0,1.0))))
    }
    "sample symmetric positive-definite matrices with analytic means" in {
      val r=rng; val draws=Vector.fill(6000)(p.sample(r))
      all(draws.map(x => x(0)(1)==x(1)(0))) shouldBe true
      for(i <- 0 until 2;j <- 0 until 2) draws.map(_(i)(j)).sum/draws.size shouldBe (p.mean(i)(j) +- .3)
      val a=rng; val b=rng
      Vector.fill(10)(p.sample(a)) shouldBe Vector.fill(10)(p.sample(b))
    }
    "match independent KL and affinity with symmetry, identity and numeric refusals" in {
      WishartInformation.kl(p,q).value.get shouldBe (BreadthFixtures.wishartKl +- 1e-8)
      WishartInformation.bhattacharyya(p,q).value.get shouldBe (BreadthFixtures.wishartBh +- 1e-11)
      WishartInformation.bhattacharyya(q,p).value.get shouldBe (BreadthFixtures.wishartBh +- 1e-11)
      WishartInformation.kl(p,p).value shouldBe Some(0.0)
      WishartInformation.kl(p,q,1e-20).status shouldBe Status.NumericallyUnresolved
      val conditioned=WishartDistribution(5,Vector(Vector(1.0,.999999999),Vector(.999999999,1.0)))
      WishartInformation.kl(conditioned,conditioned.copy(degreesOfFreedom=6)).status shouldBe Status.NumericallyUnresolved
      WishartInformation.bhattacharyya(conditioned,conditioned.copy(degreesOfFreedom=6)).status shouldBe Status.NumericallyUnresolved
      for(s <- Vector(1e-20,1e20)) {
        WishartInformation.kl(p.copy(scale=p.scale.map(_.map(_*s))),q.copy(scale=q.scale.map(_.map(_*s))))
          .value.get shouldBe (BreadthFixtures.wishartKl +- 1e-8)
      }
    }
  }
  "Spherical von Mises-Fisher" should {
    "retain uniform and high-concentration surface-density limits" in {
      val uniform=VonMisesFisher3Distribution(Vector(0,0,1),0)
      uniform.density(Vector(1,0,0)) shouldBe (1/(4*math.Pi) +- 1e-16)
      uniform.mean shouldBe Vector(0.0,0.0,0.0)
      val concentrated=north.copy(concentration=1e6)
      concentrated.logDensity(Vector(0,0,1)) shouldBe (math.log(1e6/(2*math.Pi)) +- 1e-12)
      concentrated.logDensity(Vector(0,0,-1)).isFinite shouldBe true
      concentrated.density(Vector(0,0,-1)) shouldBe 0.0
      intercept[IllegalArgumentException](north.logDensity(Vector(0,0,2)))
      intercept[IllegalArgumentException](north.copy(concentration= -1))
    }
    "sample on the sphere with correct mean direction from uniform to concentrated" in {
      for(k <- Vector(0.0,1e-8,.001,3.0,1000.0,1e6)) {
        val d=north.copy(concentration=k); val r=rng; val values=Vector.fill(10000)(d.sample(r))
        all(values.map(x => math.abs(x.map(v => v*v).sum-1)<1e-12)) shouldBe true
        values.map(_(2)).sum/values.size shouldBe (d.mean(2) +- .02)
        values.map(_(0)).sum/values.size shouldBe (0.0 +- .02)
      }
      val a=rng; val b=rng
      Vector.fill(20)(east.sample(a)) shouldBe Vector.fill(20)(east.sample(b))
    }
    "match integrated spherical divergence controls and opposite/uniform limits" in {
      VonMisesFisher3Information.kl(north,east).value.get shouldBe (BreadthFixtures.vmfKl +- 1e-12)
      VonMisesFisher3Information.bhattacharyya(north,east).value.get shouldBe (BreadthFixtures.vmfBh +- 1e-12)
      VonMisesFisher3Information.bhattacharyya(east,north).value.get shouldBe (BreadthFixtures.vmfBh +- 1e-12)
      val opposite=north.copy(direction=Vector(0,0,-1))
      VonMisesFisher3Information.kl(north,opposite).value.get shouldBe (6*north.mean(2) +- 1e-12)
      VonMisesFisher3Information.bhattacharyya(north,opposite).value.get shouldBe (math.log(math.sinh(3)/3) +- 1e-12)
      val u=north.copy(concentration=0)
      VonMisesFisher3Information.kl(u,u.copy(direction=Vector(1,0,0))).value shouldBe Some(0.0)
      VonMisesFisher3Information.kl(north,east,1e-20).status shouldBe Status.NumericallyUnresolved
    }
  }

  private def observed[A,D](left: D,right: D,value: A,lp: D => Double,make: (Element[D],Universe) => Element[A]): Unit = withRandomSeed(119) {
    val u=Universe.createNew(); val choice=Flip(.4)(using "choice",u)
    val law=Apply(choice,(b: Boolean) => if(b) left else right)(using "",u)
    val e=make(law,u); e.observe(value)
    val target=1/(1+1.5*math.exp(lp(right)-lp(left)))
    val algorithm=Importance(6000,choice)
    try { algorithm.start(); algorithm.probability(choice,true) shouldBe (target +- .04) }
    finally { if(algorithm.isActive) algorithm.kill(); u.clear() }
  }
  "Breadth elements" should {
    "support conditional observed likelihoods for all five representatives" in {
      for(kind <- Vector("GEV","GPD")) {
        val a: ScalarDistribution=if(kind=="GEV") GeneralizedExtremeValueDistribution(.2) else GeneralizedParetoDistribution(.2)
        val b: ScalarDistribution=if(kind=="GEV") GeneralizedExtremeValueDistribution(0) else GeneralizedParetoDistribution(0)
        observed[Double,ScalarDistribution](a,b,.5,_.logDensity(.5),(d,u) => ScalarElement(d)(using "",u))
      }
      observed[Vector[Int],MultinomialDistribution](count,count.copy(probabilities=Vector(.4,.2,.4)),Vector(1,1,2),_.logProbability(Vector(1,1,2)),(d,u) => Multinomial(d)(using "",u))
      val x=Vector(Vector(4.0,1.0),Vector(1.0,3.0))
      observed[Vector[Vector[Double]],WishartDistribution](p,q,x,_.logDensity(x),(d,u) => Wishart(d)(using "",u))
      observed[Vector[Double],VonMisesFisher3Distribution](north,east,Vector(0,0,1),_.logDensity(Vector(0,0,1)),(d,u) => VonMisesFisher3(d)(using "",u))
    }
    "preserve log likelihood underflow and reject singular boundary observations" in {
      val u=Universe.createNew(); val e=VonMisesFisher3(north.copy(concentration=1000))(using "",u); e.observe(Vector(0,0,-1))
      val a=Importance(20,e)
      try { a.start(); a.getTotalWeight shouldBe (math.log(20)+e.logDensity(Vector(0,0,-1)) +- 1e-10) }
      finally { if(a.isActive) a.kill(); u.clear() }
      val v=new Universe
      try { intercept[ArithmeticException](GeneralizedPareto(-2)(using "",v).logDensity(.5)) }
      finally v.clear()
    }
    "support prior-proposal MH on counts, matrices and spherical states" in {
      def check[A](make: Universe => Element[A],event: A => Boolean,probability: Double): Unit=withRandomSeed(731) {
        val u=Universe.createNew(); val e=make(u); e.addConstraint((x: A) => if(event(x)) 2.0 else 1.0)
        val a=MetropolisHastings(9000,ProposalScheme.default(using u),300,e)
        try { a.start(); a.probability(e,event) shouldBe (2*probability/(1+probability) +- .045) }
        finally { if(a.isActive) a.kill(); u.clear() }
      }
      check[Vector[Int]](u => Multinomial(count)(using "",u),_(0)==0,math.pow(.8,4))
      check[Vector[Vector[Double]]](u => Wishart(WishartDistribution(4,Vector(Vector(1.0))))(using "",u),_(0)(0)<=2,1-2/math.E)
      check[Vector[Double]](u => VonMisesFisher3(north.copy(concentration=0))(using "",u),_(2)>=0,.5)
      check[Double](u => GeneralizedExtremeValue(.2)(using "",u),_<=GeneralizedExtremeValueDistribution(.2).quantile(.5),.5)
      check[Double](u => GeneralizedPareto(.2)(using "",u),_<=GeneralizedParetoDistribution(.2).quantile(.5),.5)
    }
    "cancel all new sampling paths before consuming caller randomness" in {
      val r=rng
      try {
        Thread.currentThread().interrupt()
        intercept[java.util.concurrent.CancellationException](p.sample(r))
        intercept[java.util.concurrent.CancellationException](north.sample(r))
        intercept[java.util.concurrent.CancellationException](count.sample(r))
        intercept[java.util.concurrent.CancellationException](GeneralizedParetoDistribution(.2).sample(r))
        intercept[java.util.concurrent.CancellationException](GeneralizedExtremeValueDistribution(.2).sample(r))
      } finally Thread.interrupted()
    }
    "preserve isolated multi-chain results across worker counts for each new family" in {
      import com.cra.figaro.algorithm.sampling.parallel.{MultiChainMetropolisHastings as MH}
      def check[A](make: Universe => Element[A],query: A => Double): Unit = {
        def model(u: Universe,i: Int): MH.Model={ val e=make(u); MH.Model(Vector(MH.Observable("value",e)(query))) }
        val c=MH.Config(chains=2,drawsPerChain=100,warmUp=20,parallelism=1,seed=891)
        MH.run(c)(model).chains.map(_.draws) shouldBe MH.run(c.copy(parallelism=2))(model).chains.map(_.draws)
      }
      check[Vector[Int]](u => Multinomial(count)(using "",u),_(0).toDouble)
      check[Vector[Vector[Double]]](u => Wishart(p)(using "",u),_(0)(0))
      check[Vector[Double]](u => VonMisesFisher3(north)(using "",u),_(2))
      check[Double](u => GeneralizedExtremeValue(.2)(using "",u),identity)
      check[Double](u => GeneralizedPareto(.2)(using "",u),identity)
    }
  }
}
