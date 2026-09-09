package com.cra.figaro.test.modernization

import com.cra.figaro.library.atomic.continuous.*
import com.cra.figaro.algorithm.sampling.{VectorImportance as V,GraphProposalImportance as P}
import com.cra.figaro.algorithm.sampling.parallel.MultiChainMetropolisHastings as MH
import com.cra.figaro.language.*
import com.cra.figaro.util.SamplingRandom
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class MultivariateStudentTTest extends AnyWordSpec with Matchers {
  val law=MultivariateStudentTDistribution(5,Vector(0.0,1.0),Vector(Vector(2.0,.3),Vector(.3,1.0)))
  "Elliptical multivariate Student t" should {
    "reduce to scalar t densities and preserve defined moments and marginals" in {
      // Independent mpmath 1.3.0, 60-digit determinant/inverse reference.
      law.logDensity(Vector(1.0,-1.0)) shouldBe (-4.7045718664293367217 +- 1e-13)
      for(df <- Vector(.001,.5,1.0,2.0,5.0,100.0,1e6); x <- Vector(-10.0,0.0,2.0,1e100)) {
        val d=MultivariateStudentTDistribution(df,Vector(1.0),Vector(Vector(4.0)))
        d.logDensity(Vector(x)) shouldBe (StudentTDistribution(df,1,2).logDensity(x) +- 1e-6)
      }
      law.mean.get shouldBe Vector(0.0,1.0)
      law.covariance.get(0)(0) shouldBe (10.0/3 +- 1e-14)
      law.marginal(Vector(1,0)).shape shouldBe Vector(Vector(1.0,.3),Vector(.3,2.0))
      law.copy(degreesOfFreedom=1).mean shouldBe None
      law.copy(degreesOfFreedom=2).covariance shouldBe None
      law.logDensity(Vector(1e300,-1e300)).isFinite shouldBe true
      intercept[IllegalArgumentException](law.logDensity(Vector(Double.NaN,0.0)))
      intercept[IllegalArgumentException](law.copy(shape=Vector(Vector(1.0,1.0),Vector(1.0,1.0))))
    }
    "sample a shared radial scale rather than independent t coordinates" in {
      val d=MultivariateStudentTDistribution(5,Vector(0.0,0.0),Vector(Vector(1.0,0.0),Vector(0.0,1.0)))
      val rng=SamplingRandom.scalaRandom(83)
      val xs=Vector.fill(50000)(d.sample(rng))
      xs.map(_(0)).sum/xs.size shouldBe (0.0 +- .035)
      xs.map(x => x(0)*x(0)).sum/xs.size shouldBe (5.0/3 +- .12)
      // Common-scale dependence is visible even when covariance off-diagonals are zero.
      val both=xs.count(x => math.abs(x(0))>2 && math.abs(x(1))>2).toDouble/xs.size
      val marginal=2*StudentTDistribution(5).survival(2)
      both should be > (marginal*marginal*1.5)
      d.sample(SamplingRandom.scalaRandom(12)) shouldBe d.sample(SamplingRandom.scalaRandom(12))
    }
    "work as an observation and frozen proposal in owned graphs" in {
      val result=P.run(P.Config(draws=2000,maxAttempts=2000),V.StudentT(law),law.logDensity) { (u,root) =>
        val kernel=root.map(_ => law)(using "",u)
        MultivariateStudentT(kernel)(using "",u).observe(Vector(0.0,1.0))
        root.map(_.head)(using "",u)
      }
      result.health.diagnostics.mean.get shouldBe (0.0 +- .15)
      result.health.diagnostics.ess.get shouldBe (2000.0 +- 1e-7)
      val result2=MH.run(MH.Config(drawsPerChain=500,warmUp=100,parallelism=2,seed=4)) { (u,_) =>
        val x=MultivariateStudentT(law)(using "",u)
        MH.Model(Vector(MH.Observable("x",x)(_.head)))
      }
      result2.diagnostics("x").mean shouldBe (0.0 +- .15)
    }
    "respect interruption without consuming the flag" in {
      Thread.currentThread.interrupt()
      try {
        intercept[java.util.concurrent.CancellationException](law.sample(SamplingRandom.scalaRandom(1)))
        Thread.currentThread.isInterrupted shouldBe true
      } finally { Thread.interrupted() }
    }
  }
}
