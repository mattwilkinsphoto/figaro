package com.cra.figaro.test.modernization

import com.cra.figaro.library.atomic.continuous.*
import com.cra.figaro.library.atomic.discrete.*
import com.cra.figaro.library.atomic.InformationMetricStatus.*
import com.cra.figaro.language.*
import com.cra.figaro.algorithm.sampling.{Importance,MetropolisHastings,ProposalScheme}
import com.cra.figaro.algorithm.sampling.parallel.MultiChainMetropolisHastings as MH
import com.cra.figaro.util.withRandomSeed
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class DistributionConstructionsTest extends AnyWordSpec with Matchers {
  private val normal=GaussianDistribution(0,1)
  private val p=MultivariateGaussianDistribution(Vector(0.0,1.0),Vector(Vector(2.0,.3),Vector(.3,1.0)))
  private val q=MultivariateGaussianDistribution(Vector(1.0,-1.0),Vector(Vector(1.0,-.2),Vector(-.2,3.0)))
  private val gmm=GaussianMixtureDistribution(Vector(.25,.75),Vector(p,q))
  private val scalarMixture=ScalarMixtureDistribution(Vector(.3,.7),Vector(GaussianDistribution(-2,1),GaussianDistribution(2,.5)))
  private def integrate(d: ScalarDistribution,a: Double,b: Double,n: Int=20000): Double = {
    val step=(b-a)/n
    (0 until n).map(i => d.density(a+(i+.5)*step)*step).sum
  }
  "Reusable distribution constructions" should {
    "handle affine Jacobians, reflections, and nonlinear Jacobians" in {
      val a=AffineDistribution(normal,3,-2)
      a.logDensity(3) shouldBe (normal.logDensity(0)-math.log(2) +- 1e-14)
      a.mean.get shouldBe 3.0; a.variance.get shouldBe 4.0
      for(prob <- Vector(1e-100,.01,.25,.5,.9,1-1e-8)) {
        a.cdf(a.quantile(prob)) shouldBe (prob +- math.max(1e-112,prob*1e-10))
      }
      val e=ExpDistribution(GaussianDistribution(.3,.8)); val lognormal=LogNormalDistribution(.3,.8)
      for(x <- Vector(.01,.5,1.0,4.0,100.0)) {
        e.logDensity(x) shouldBe (lognormal.logDensity(x) +- 1e-12)
        e.cdf(x) shouldBe (lognormal.cdf(x) +- 1e-13)
      }
      integrate(e,0,100) shouldBe (1.0 +- 1e-6)
      e.logDensity(0) shouldBe Double.NegativeInfinity
      intercept[IllegalArgumentException](AffineDistribution(normal,0,0))
      intercept[ArithmeticException](ExpDistribution(GaussianDistribution(1000,1)).quantile(.5))
    }
    "normalize truncation using direct tails and refuse unresolved intervals" in {
      val t=TruncatedDistribution(normal,-1,2)
      integrate(t,-1,2) shouldBe (1.0 +- 1e-8)
      t.logDensity(-2) shouldBe Double.NegativeInfinity
      for(prob <- Vector(.0001,.1,.5,.9,.9999)) t.cdf(t.quantile(prob)) shouldBe (prob +- 1e-12)
      val tail=TruncatedDistribution(normal,8,9)
      tail.retainedProbability shouldBe (6.219831985865830282868259670512219675e-16 +- 1e-28)
      integrate(tail,8,9) shouldBe (1.0 +- 1e-7)
      tail.cdf(tail.quantile(.4)) shouldBe (.4 +- 1e-12)
      intercept[ArithmeticException](TruncatedDistribution(normal,40,41))
      intercept[ArithmeticException](TruncatedDistribution(normal,1,math.nextUp(1.0)))
      intercept[IllegalArgumentException](TruncatedDistribution(normal,1,-1))
      t.quantile(0) shouldBe -1.0; t.quantile(1) shouldBe 2.0
    }
    "preserve scalar mixture normalization, quantiles, moments, and mode membership" in {
      integrate(scalarMixture,-12,12) shouldBe (1.0 +- 1e-8)
      scalarMixture.mean.get shouldBe (.8 +- 1e-14)
      scalarMixture.variance.get shouldBe (3.835 +- 1e-12)
      for(prob <- Vector(1e-8,.01,.3,.5,.9,1-1e-8)) scalarMixture.cdf(scalarMixture.quantile(prob)) shouldBe (prob +- 1e-12)
      scalarMixture.responsibilities(2).sum shouldBe (1.0 +- 1e-14)
      scalarMixture.responsibilities(2)(1) should be > .999
      val zero=ScalarMixtureDistribution(Vector(1.0,0.0),Vector(normal,CauchyDistribution(100,1)))
      zero.variance shouldBe Some(1.0); zero.responsibilities(0)(1) shouldBe 0.0
      val a=new scala.util.Random(1); val b=new scala.util.Random(1)
      Vector.fill(100)(scalarMixture.sample(a)) shouldBe Vector.fill(100)(scalarMixture.sample(b))
      scalarMixture.logDensity(100).isFinite shouldBe true
      scalarMixture.density(100) shouldBe 0.0
      intercept[IllegalArgumentException](ScalarMixtureDistribution(Vector(.4,.4),Vector(normal,normal)))
      intercept[IllegalArgumentException](ScalarMixtureDistribution(Vector(1.0),Vector.empty))
    }
    "distinguish zero inflation from a hurdle and match normalized masses and moments" in {
      val base=NegativeBinomialDistribution(2,.5)
      val inflated=ZeroAdjustedDistribution(base,.3)
      val hurdle=ZeroAdjustedDistribution(base,.3,true)
      inflated.probability(0) shouldBe (.475 +- 1e-14)
      hurdle.probability(0) shouldBe (.3 +- 1e-14)
      for(d <- Vector(inflated,hurdle)) {
        (0 to 200).map(d.probability).sum shouldBe (1.0 +- 1e-12)
        (0 to 200).map(k => k*d.probability(k)).sum shouldBe (d.mean +- 1e-11)
        (0 to 200).map(k => (k-d.mean)*(k-d.mean)*d.probability(k)).sum shouldBe (d.variance +- 1e-10)
        for(prob <- Vector(.01,.3,.5,.95,.999999)) {
          val k=d.quantile(prob); d.cdf(k) should be >= prob; d.cdf(k-1) should be < prob
        }
        for(k <- 0 to 20) d.cdf(k)+d.survival(k) shouldBe (1.0 +- 1e-14)
      }
      ZeroAdjustedDistribution(base,0,true).support._1 shouldBe 1
      ZeroAdjustedDistribution(base,1,true).quantile(1) shouldBe 0
      ZeroAdjustedDistribution(NegativeBinomialDistribution(1,1),1,true).variance shouldBe 0.0
      intercept[IllegalArgumentException](ZeroAdjustedDistribution(NegativeBinomialDistribution(1,1),.5,true))
      intercept[ArithmeticException](hurdle.quantile(1))
    }
    "keep unsupported construction divergences explicit instead of dispatching unsafe generic estimates" in {
      ScalarDivergence.kl(scalarMixture,scalarMixture.copy(weights=Vector(.4,.6))).status shouldBe Unsupported
      val z=ZeroAdjustedDistribution(NegativeBinomialDistribution(2,.5),.2)
      CountDivergence.kl(z,z.copy(base=NegativeBinomialDistribution(3,.5))).status shouldBe Unsupported
    }
    "reuse exact information invariances instead of integrating transformed densities" in {
      val other=GaussianDistribution(.5,1.2)
      val a=AffineDistribution(normal,100,-3); val b=AffineDistribution(other,100,-3)
      ScalarDivergence.kl(a,b).value shouldBe ScalarDivergence.kl(normal,other).value
      ScalarDivergence.bhattacharyya(ExpDistribution(normal),ExpDistribution(other)).value shouldBe ScalarDivergence.bhattacharyya(normal,other).value
      val z=ZeroAdjustedDistribution(NegativeBinomialDistribution(2,.5),.2,true)
      val q=z.copy(zeroProbability=.4)
      CountDivergence.kl(z,q).value.get shouldBe (.2*math.log(.2/.4)+.8*math.log(.8/.6) +- 1e-12)
      CountDivergence.bhattacharyya(z,q).value.get shouldBe (-math.log(math.sqrt(.2*.4)+math.sqrt(.8*.6)) +- 1e-12)
      CountDivergence.kl(z,z.copy(zeroProbability=1)).status shouldBe Infinite
    }
  }
  "Gaussian kernels and information" should {
    "reconstruct the configured covariance from deterministic standard-normal innovations" in {
      val columns=(0 until p.dimension).map { j =>
        val innovations=(0 until p.dimension).map(i => if(i == j) 1.0 else 0.0).iterator
        val rng=new scala.util.Random(0) { override def nextGaussian(): Double = innovations.next() }
        p.sample(rng).zip(p.mean).map(_-_)
      }
      for(i <- 0 until p.dimension; j <- 0 until p.dimension)
        columns.map(c => c(i)*c(j)).sum shouldBe (p.covariance(i)(j) +- 1e-14)
    }
    "match independent 70-digit inverse-matrix and determinant controls" in {
      GaussianInformation.kl(p,q).value.get shouldBe (1.46904301313871524988 +- 1e-12)
      GaussianInformation.bhattacharyya(p,q).value.get shouldBe (.45776780271643533150 +- 1e-12)
      GaussianInformation.mutualInformation(p,Vector(0)).value.get shouldBe (.02302196925070340230 +- 1e-12)
      gmm.logDensity(Vector(.2,-.4)) shouldBe (-2.83821081207703776531 +- 1e-12)
      gmm.responsibilities(Vector(.2,-.4))(0) shouldBe (.16693671195322205316 +- 1e-12)
      gmm.responsibilities(Vector(.2,-.4)).sum shouldBe (1.0 +- 1e-14)
    }
    "reduce equal-covariance KL and Bhattacharyya to squared Mahalanobis with the correct factors" in {
      val r=p.copy(mean=Vector(2.0,3.0)); val squared=p.mahalanobisSquared(r.mean)
      GaussianInformation.kl(p,r).value.get shouldBe (squared/2 +- 1e-12)
      GaussianInformation.bhattacharyya(p,r).value.get shouldBe (squared/8 +- 1e-12)
      GaussianInformation.kl(p,p).value shouldBe Some(0.0)
      GaussianInformation.bhattacharyya(p,q).value.get shouldBe (GaussianInformation.bhattacharyya(q,p).value.get +- 1e-12)
      GaussianInformation.kl(p,q).value should not be GaussianInformation.kl(q,p).value
      val scalar=GaussianDistribution(2,3)
      ScalarDivergence.kl(normal,scalar).value shouldBe GaussianInformation.kl(GaussianInformation.scalar(normal),GaussianInformation.scalar(scalar)).value
    }
    "compute partition MI without depending on means or coordinate order" in {
      val joint=MultivariateGaussianDistribution(Vector(1.0,2.0,3.0),Vector(Vector(1.0,.6,0.0),Vector(.6,1.0,0.0),Vector(0.0,0.0,2.0)))
      GaussianInformation.mutualInformation(joint,Vector(0,2)).value.get shouldBe (-.5*math.log(1-.36) +- 1e-12)
      GaussianInformation.mutualInformation(joint,Vector(2)).value shouldBe Some(0.0)
      GaussianInformation.mutualInformation(joint,Vector(0,2)).value shouldBe GaussianInformation.mutualInformation(joint,Vector(2,0)).value
      intercept[IllegalArgumentException](GaussianInformation.mutualInformation(joint,Vector(0,0)))
      intercept[IllegalArgumentException](GaussianInformation.mutualInformation(joint,Vector(0,1,2)))
      GaussianInformation.kl(p,q,1e-30).status shouldBe NumericallyUnresolved
    }
    "retain between-mode covariance and exact Gaussian-mixture marginals" in {
      gmm.mean shouldBe Vector(.75,-.5)
      gmm.covariance(0)(0) shouldBe (1.4375 +- 1e-14)
      gmm.covariance(1)(1) shouldBe (3.25 +- 1e-14)
      gmm.covariance(0)(1) shouldBe (-.45 +- 1e-14)
      val marginal=gmm.marginal(Vector(1))
      marginal.mean shouldBe Vector(-.5)
      marginal.covariance shouldBe Vector(Vector(3.25))
      val singleton=GaussianMixtureDistribution(Vector(1.0),Vector(p))
      singleton.logDensity(Vector(0.0,1.0)) shouldBe p.logDensity(Vector(0.0,1.0))
      val a=new scala.util.Random(42); val b=new scala.util.Random(42)
      Vector.fill(200)(gmm.sample(a)) shouldBe Vector.fill(200)(gmm.sample(b))
      val rng=new scala.util.Random(321); val draws=Vector.fill(30000)(gmm.sample(rng))
      draws.map(_(0)).sum/draws.size shouldBe (.75 +- .03)
      draws.map(x => math.pow(x(1)+.5,2)).sum/draws.size shouldBe (3.25 +- .1)
    }
    "reject malformed/singular covariances and dimension errors without repair" in {
      intercept[IllegalArgumentException](p.copy(covariance=Vector(Vector(1.0,1.0),Vector(1.0,1.0))))
      intercept[IllegalArgumentException](p.copy(covariance=Vector(Vector(1.0,.1),Vector(.2,1.0))))
      intercept[IllegalArgumentException](p.copy(covariance=Vector(Vector(1.0,2.0),Vector(2.0,1.0))))
      intercept[IllegalArgumentException](p.logDensity(Vector(0.0)))
      intercept[IllegalArgumentException](p.marginal(Vector(2)))
      intercept[IllegalArgumentException](gmm.copy(weights=Vector(-.1,1.1)))
      intercept[IllegalArgumentException](gmm.copy(components=Vector(p,p.marginal(Vector(0)))))
    }
    "retain tiny Gaussian tails and obey common coordinate rescaling" in {
      normal.cdf(normal.quantile(1e-250)) shouldBe (1e-250 +- 1e-261)
      normal.survival(10) should be > 0.0
      def rescale(d: MultivariateGaussianDistribution): MultivariateGaussianDistribution = {
        val scale=Vector(1e30,1e-20)
        d.copy(mean=d.mean.indices.map(i => d.mean(i)*scale(i)).toVector,
          covariance=Vector.tabulate(2,2)((i,j) => d.covariance(i)(j)*(scale(i)*scale(j))))
      }
      GaussianInformation.kl(rescale(p),rescale(q)).value.get shouldBe (GaussianInformation.kl(p,q).value.get +- 1e-10)
      GaussianInformation.mutualInformation(rescale(p),Vector(0)).value.get shouldBe (GaussianInformation.mutualInformation(p,Vector(0)).value.get +- 1e-10)
    }
  }
  "Construction inference adapters" should {
    "use stable GMM and legacy multivariate-Normal observation likelihoods" in withRandomSeed(11) {
      val u=Universe.createNew()
      val choice=Flip(.5)(using "choice",u)
      val kernels=Apply(choice,(b: Boolean) => GaussianMixtureDistribution(Vector(1.0),Vector(if(b) p else q)))(using "kernels",u)
      val observation=GaussianMixture(kernels)(using "observation",u)
      val x=Vector(.2,-.4); observation.observe(x)
      val expected=math.exp(p.logDensity(x))/(math.exp(p.logDensity(x))+math.exp(q.logDensity(x)))
      val alg=Importance(12000,choice)
      try { alg.start(); alg.probability(choice,true) shouldBe (expected +- .025) }
      finally { if(alg.isActive) alg.kill(); u.clear() }
      val v=Universe.createNew()
      try {
        val legacy=MultivariateNormal(p.mean.toList,p.covariance.map(_.toList).toList)(using "legacy",v)
        legacy.logDensity(x.toList) shouldBe p.logDensity(x)
        legacy.logp(x.toList) shouldBe p.logDensity(x)
        legacy.logDensity(List(100.0,100.0)).isFinite shouldBe true
        legacy.density(List(100.0,100.0)) shouldBe 0.0
      } finally v.clear()
    }
    "sample composed scalar/count models with constrained MH" in withRandomSeed(17) {
      for(d <- Vector[ScalarDistribution](AffineDistribution(normal,2,-1),ExpDistribution(normal),TruncatedDistribution(normal,-1,2),scalarMixture)) {
        val u=Universe.createNew(); val e=ScalarElement(d)(using "value",u); val median=d.quantile(.5)
        e.addConstraint((x: Double) => if(x <= median) 2.0 else 1.0)
        val alg=MetropolisHastings(7000,ProposalScheme.default(using u),300,e)
        try { alg.start(); alg.probability(e,(x: Double) => x <= median) shouldBe (2.0/3 +- .045) }
        finally { if(alg.isActive) alg.kill(); u.clear() }
      }
      val u=Universe.createNew(); val d=ZeroAdjustedDistribution(NegativeBinomialDistribution(2,.5),.3,true)
      val e=CountElement(d)(using "count",u); val alg=Importance(7000,e)
      try { alg.start(); alg.probability(e,0) shouldBe (.3 +- .03) }
      finally { if(alg.isActive) alg.kill(); u.clear() }
    }
    "weight observations even when every ordinary component density underflows" in withRandomSeed(22) {
      val u=Universe.createNew(); val which=Flip(.5)(using "which",u)
      val small=MultivariateGaussianDistribution(Vector(0.0),Vector(Vector(1.0)))
      val large=small.copy(mean=Vector(1.0))
      small.density(Vector(1000.0)) shouldBe 0.0; large.density(Vector(1000.0)) shouldBe 0.0
      val kernel=Apply(which,(b: Boolean) => GaussianMixtureDistribution(Vector(1.0),Vector(if(b) large else small)))(using "kernel",u)
      GaussianMixture(kernel)(using "observed",u).observe(Vector(1000.0))
      val alg=Importance(3000,which)
      try { alg.start(); alg.probability(which,true) should be > .999 }
      finally { if(alg.isActive) alg.kill(); u.clear() }
    }
    "honor interruption and refuse precision claims for ill-conditioned metrics" in {
      val near=MultivariateGaussianDistribution(Vector(0.0,0.0),Vector(Vector(1.0,.99999999),Vector(.99999999,1.0)))
      GaussianInformation.mutualInformation(near,Vector(0)).status shouldBe NumericallyUnresolved
      Thread.currentThread().interrupt()
      try intercept[java.util.concurrent.CancellationException](gmm.sample(new scala.util.Random(1)))
      finally Thread.interrupted()
    }
    "produce identical isolated GMM chains across worker counts" in {
      def model(u: Universe,i: Int): MH.Model = {
        val e=GaussianMixture(gmm)(using "gmm",u)
        MH.Model(Vector(MH.Observable("first",e)(_(0))))
      }
      val config=MH.Config(chains=2,drawsPerChain=200,warmUp=20,parallelism=1,seed=831)
      MH.run(config)(model).chains.map(_.draws) shouldBe MH.run(config.copy(parallelism=2))(model).chains.map(_.draws)
    }
  }
}
