package com.cra.figaro.test.modernization

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import com.cra.figaro.language.*
import com.cra.figaro.library.atomic.continuous.*
import com.cra.figaro.library.atomic.{LegacyInformation,InformationMetricStatus}
import com.cra.figaro.algorithm.sampling.{Importance,MetropolisHastings,ProposalScheme}
import com.cra.figaro.util.{SamplingRandom,withRandomSeed}

class CovariancePriorsTest extends AnyWordSpec with Matchers {
  private val eye=Vector(Vector(1.0,0.0),Vector(0.0,1.0))
  private def owned(body: Universe => Unit): Unit={ val u=Universe.createNew(); try body(u) finally u.clear() }
  "Covariance priors" should {
    "normalize LKJ in correlation coordinates and distinguish its Cholesky measure" in {
      val p=LKJDistribution(2,1); val q=LKJDistribution(2,2)
      p.logDensity(eye) shouldBe (-math.log(2) +- 1e-14)
      q.logDensity(eye) shouldBe (math.log(.75) +- 1e-14)
      LKJDistribution(3).logDensity(Vector.tabulate(3,3)((i,j) => if(i==j) 1.0 else 0.0)) shouldBe (-math.log(math.Pi*math.Pi/2) +- 1e-14)
      LKJInformation.kl(p,q).value.get shouldBe (2-2*math.log(2)-math.log(1.5) +- 1e-13)
      LKJInformation.bhattacharyya(p,q).value.get shouldBe (-math.log(math.Pi*math.sqrt(1.5)/4) +- 1e-13)
      val law=LKJDistribution(3,2); val l=law.sampleCholesky(SamplingRandom.scalaRandom(42))
      val r=law.sample(SamplingRandom.scalaRandom(42))
      law.logDensityCholesky(l)-law.logDensity(r) shouldBe (math.log(l(1)(1)) +- 1e-12)
      law.toCovariance(law.mean,Vector(2,3,4)).map(_.sum) shouldBe Vector(4.0,9.0,16.0)
    }
    "sample LKJ marginal correlations with the correct dimension-dependent variance" in {
      for(d <- Vector(2,3,5); eta <- Vector(.5,1.0,3.0)) {
        val law=LKJDistribution(d,eta); val rng=SamplingRandom.scalaRandom(3100+d+(eta*10).toInt)
        val attempts=Vector.fill(6000) {
          try Some(law.sample(rng)(0)(1)) catch { case _: ArithmeticException => None }
        }
        val rs=attempts.flatten; val missing=1.0-rs.size.toDouble/attempts.size
        info(s"LKJ d=$d eta=$eta: ${attempts.size-rs.size}/${attempts.size} numeric refusals")
        missing should be < .01
        // Refused draws are not redrawn or silently excluded: bound their unknown
        // contribution using rho in [-1,1] and rho^2 in [0,1], with the original denominator.
        math.abs(rs.sum/attempts.size)+missing should be < .03
        val secondLower=rs.map(x => x*x).sum/attempts.size
        val expected=1.0/(2*eta+d-1)
        math.max(math.abs(secondLower-expected),math.abs(secondLower+missing-expected)) should be < .02
      }
    }
    "match inverse-gamma density, matrix fixtures, means and transformation Jacobians" in owned { u =>
      val p=InverseWishartDistribution(8,Vector(Vector(14.0)))
      val ig=InverseGamma(4,7)(using "",u)
      for(x <- Vector(.1,1.0,10.0)) p.logDensity(Vector(Vector(x))) shouldBe (ig.logDensity(x) +- 1e-12)
      val q=InverseWishartDistribution(10,Vector(Vector(6.0)))
      InverseWishartInformation.kl(p,q).value.get shouldBe (LegacyInformation.inverseGammaKl(4,7,5,3).value.get +- 1e-11)
      InverseWishartInformation.bhattacharyya(p,q).value.get shouldBe (LegacyInformation.inverseGammaBhattacharyya(4,7,5,3).value.get +- 1e-11)
      InverseWishartDistribution(2,Vector(Vector(1.0))).mean shouldBe None
      val s=Vector(Vector(2.0,.4),Vector(.4,1.0)); val law=InverseWishartDistribution(6,s)
      law.logDensity(Vector(Vector(.8,.1),Vector(.1,.5))) shouldBe (-1.8476711382197565551 +- 1e-13)
      val l=law.sampleCholesky(SamplingRandom.scalaRandom(15)); val x=law.sample(SamplingRandom.scalaRandom(15))
      law.logDensityCholesky(l)-law.logDensity(x) shouldBe (2*math.log(2)+2*math.log(l(0)(0))+math.log(l(1)(1)) +- 1e-12)
      val rng=SamplingRandom.scalaRandom(4101)
      val draws=Vector.fill(20000)(law.sample(rng))
      for(i <- 0 until 2;j <- 0 until 2) draws.map(_(i)(j)).sum/draws.size shouldBe (law.mean.get(i)(j) +- .025)
      val precision=draws.map { a => val det=a(0)(0)*a(1)(1)-a(0)(1)*a(1)(0); a(1)(1)/det }
      precision.sum/precision.size shouldBe (6/1.84 +- .08)
    }
    "respect inverse-Wishart metric symmetry and inversion equivalence in two dimensions" in {
      val p=InverseWishartDistribution(6,Vector(Vector(2.0,.4),Vector(.4,1.0)))
      val q=InverseWishartDistribution(9,Vector(Vector(1.0,-.2),Vector(-.2,3.0)))
      def inverse(a: Vector[Vector[Double]]): Vector[Vector[Double]]={
        val det=a(0)(0)*a(1)(1)-a(0)(1)*a(1)(0)
        Vector(Vector(a(1)(1)/det,-a(0)(1)/det),Vector(-a(1)(0)/det,a(0)(0)/det))
      }
      val wp=WishartDistribution(6,inverse(p.scale)); val wq=WishartDistribution(9,inverse(q.scale))
      InverseWishartInformation.kl(p,q).value.get shouldBe (WishartInformation.kl(wp,wq).value.get +- 1e-11)
      val bh=InverseWishartInformation.bhattacharyya(p,q).value.get
      bh shouldBe (WishartInformation.bhattacharyya(wp,wq).value.get +- 1e-11)
      bh shouldBe (InverseWishartInformation.bhattacharyya(q,p).value.get +- 1e-12)
      InverseWishartInformation.kl(p,q,1e-20).status shouldBe InformationMetricStatus.NumericallyUnresolved
    }
    "score fixed and dynamic matrix observations through real Importance" in withRandomSeed(5101) { owned { u =>
      val choice=Flip(.5)(using "",u)
      val d=choice.map(b => LKJDistribution(2,if(b) 1 else 2))(using "",u)
      val r=LKJ(d)(using "",u); r.observe(eye)
      val a=Importance(15000,choice)(using u)
      try { a.start(); a.probability(choice,true) shouldBe (.4 +- .02) }
      finally if(a.isActive) a.kill()
    } }
    "integrate a random LKJ covariance into an observed multivariate Gaussian" in withRandomSeed(6101) { owned { u =>
      val r=LKJ(LKJDistribution(2,2))(using "",u)
      val cov=r.map(_.map(_.toList).toList)(using "",u)
      val y=MultivariateNormal(Constant(List(0.0,0.0))(using "",u),cov)(using "",u)
      y.observe(List(0.0,0.0))
      val a=Importance(20000,r)(using u)
      try { a.start(); a.expectation(r,(x: Vector[Vector[Double]]) => x(0)(1)*x(0)(1)) shouldBe (.25 +- .025) }
      finally if(a.isActive) a.kill()
    } }
    "score inverse-Wishart tail and hierarchical observations without density underflow" in withRandomSeed(6201) { owned { u =>
      val e=InverseWishart(InverseWishartDistribution(6,Vector(Vector(1000.0))))(using "",u)
      val observed=Vector(Vector(.1)); e.observe(observed)
      e.density(observed) shouldBe 0.0
      val a=Importance(10,e)(using u)
      try { a.start(); a.getTotalWeight shouldBe (math.log(10)+e.logDensity(observed) +- 1e-10) }
      finally { if(a.isActive) a.kill(); e.unobserve(); e.deactivate() }
      val choice=Flip(.5)(using "",u)
      val kernels=choice.map(b => InverseWishartDistribution(if(b) 4 else 6,Vector(Vector(2.0))))(using "",u)
      val x=InverseWishart(kernels)(using "",u); x.observe(Vector(Vector(1.0)))
      val b=Importance(15000,choice)(using u)
      try { b.start(); b.probability(choice,true) shouldBe (2.0/3 +- .02) }
      finally if(b.isActive) b.kill()
    } }
    "replay isolated matrix chains across worker counts" in {
      import com.cra.figaro.algorithm.sampling.parallel.{MultiChainMetropolisHastings as MH}
      def model(u: Universe,i: Int): MH.Model={
        val x=InverseWishart(InverseWishartDistribution(6,eye))(using "",u)
        val r=LKJ(LKJDistribution(2,2))(using "",u)
        x.addConstraint((a: Vector[Vector[Double]]) => math.exp(-a(0)(0)))
        MH.Model(Vector(MH.Observable("variance",x)(_(0)(0)),MH.Observable("rho",r)(_(0)(1))))
      }
      val c=MH.Config(chains=2,drawsPerChain=200,warmUp=50,parallelism=1,seed=7101)
      MH.run(c)(model).chains.map(_.draws) shouldBe MH.run(c.copy(parallelism=2))(model).chains.map(_.draws)
    }
    "reject invalid inputs, wrong dimensions, numerical boundaries and canceled work" in {
      val law=LKJDistribution(2)
      intercept[IllegalArgumentException](law.logDensity(Vector(Vector(1.0))))
      intercept[IllegalArgumentException](law.logDensity(Vector(Vector(2.0,0.0),Vector(0.0,1.0))))
      intercept[IllegalArgumentException](law.logDensity(Vector(Vector(1.0,1.0),Vector(1.0,1.0))))
      intercept[IllegalArgumentException](law.toCovariance(eye,Vector(0,1)))
      intercept[IllegalArgumentException](InverseWishartDistribution(2,eye))
      intercept[IllegalArgumentException](WishartDistribution(4,eye).logDensity(Vector(Vector(1.0))))
      val bad=new scala.util.Random(1) { override def nextDouble(): Double=0.0 }
      intercept[ArithmeticException](law.sample(bad))
      try { Thread.currentThread().interrupt()
        intercept[java.util.concurrent.CancellationException](law.sample(SamplingRandom.scalaRandom(1)))
        Thread.currentThread().isInterrupted shouldBe true
      } finally Thread.interrupted()
    }
  }
}
