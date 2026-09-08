package com.cra.figaro.test.modernization

import com.cra.figaro.library.atomic.continuous.*
import java.util.concurrent.{Callable, CancellationException, Executors, TimeUnit}
import org.apache.commons.math3.distribution.ChiSquaredDistribution
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class GaussVonMisesScoreTest extends AnyWordSpec with Matchers {
  private def coupled(k: Double) = GaussVonMisesDistribution(Vector(1.0,-2.0),
    Vector(Vector(4.0,1.2),Vector(1.2,2.61)),3.05,Vector(0.7,-0.4),
    Vector(Vector(0.3,0.2),Vector(0.2,-0.5)),k)

  "Finite-concentration GVM score calibration" should {
    "match independent 80-digit angular-quadrature fixtures" in {
      // tools/gauss_von_mises_score_reference.py, physical-angle tanh-sinh quadrature.
      val fixtures=Vector((1,0.1,0.2,0.14257053280407557049),
        (1,1.0,4.0,0.85501430280901810222), (2,4.5,5.0,0.81904553950425807233),
        (5,3.0,12.0,0.93040267644827174455), (1,50.0,7.0,0.96953059296895008134),
        (20,0.5,30.0,0.91606900350481819707))
      fixtures.foreach { (n,k,s,p) =>
        val d=GaussVonMisesScoreDistribution(n,k)
        d.cdf(s) shouldBe (p +- 1e-10)
        d.survival(s) shouldBe (1-p +- 1e-10)
      }
    }
    "reduce exactly to chi-square with n degrees of freedom at zero concentration" in {
      for (n <- Vector(1,2,5,20,10000); s <- Vector(0.01,1.0,10.0,n.toDouble)) {
        val d=GaussVonMisesScoreDistribution(n,0)
        d.cdf(s) shouldBe (new ChiSquaredDistribution(n).cumulativeProbability(s) +- 1e-12)
        (d.cdf(s)+d.survival(s)) shouldBe (1.0 +- 1e-12)
        d.cdfEstimate(s).evaluations shouldBe 1
      }
      GaussVonMisesScoreDistribution(2,0).quantile(0.95) shouldBe (-2*math.log(0.05) +- 1e-8)
    }
    "satisfy the exact exponential survival identity above the angular support for n=2" in {
      for (k <- Vector(0.1,1.0,4.5); s <- Vector(4*k,4*k+10,4*k+30)) {
        val expected=math.exp(-s/2+math.log(2*math.Pi)+VonMisesDistribution(0,k).logDensity(0))
        GaussVonMisesScoreDistribution(2,k,1e-12).survival(s) shouldBe (expected +- 1e-12)
      }
      GaussVonMisesScoreDistribution(2,4.5,1e-12).survival(40) shouldBe
        (1.0613655525734271664e-8 +- 1e-12)
    }
    "approach the n+1 chi-square law at high concentration without missing the angular peak" in {
      for (n <- Vector(1,2,5,20); s <- Vector(0.2,5.0,20.0)) {
        GaussVonMisesScoreDistribution(n,1e8).cdf(s) shouldBe
          (new ChiSquaredDistribution(n+1).cumulativeProbability(s) +- 2e-8)
      }
    }
    "remain complementary and monotone across concentrations and the angular support boundary" in {
      for (n <- Vector(1,2,5,20); k <- Vector(1e-12,0.1,1.0,4.5,50.0,1e8)) {
        val d=GaussVonMisesScoreDistribution(n,k)
        val scores=Vector(1e-12,0.01,0.2,1.0,5.0,20.0,4*k,4*k*(1-1e-8),4*k*(1+1e-8)).sorted
        var previous=0.0
        scores.foreach { s =>
          val c=d.cdf(s); val q=d.survival(s)
          c should be >= (previous-1e-10)
          c+q shouldBe (1.0 +- 2e-10)
          previous=c
        }
      }
    }
    "handle subnormal concentration and tiny positive scores" in {
      val d=GaussVonMisesScoreDistribution(1,Double.MinPositiveValue)
      d.cdf(1) shouldBe (new ChiSquaredDistribution(1).cumulativeProbability(1) +- 1e-10)
      d.cdf(Double.MinPositiveValue) shouldBe (0.0 +- 1e-10)
      d.survival(Double.MinPositiveValue) shouldBe (1.0 +- 1e-10)
    }
    "invert both tails and expose the substantial low-concentration approximation difference" in {
      for (k <- Vector(0.0,0.1,4.5,50.0,1e8); p <- Vector(0.01,0.5,0.95,0.99,0.999999)) {
        val d=GaussVonMisesScoreDistribution(2,k)
        val threshold=d.quantile(p)
        d.cdf(threshold) shouldBe (p +- 3e-10)
        d.inverseSurvival(1-p) shouldBe (threshold +- 2e-7)
      }
      val d=GaussVonMisesScoreDistribution(2,0.1)
      d.quantile(0.95) should be < 6.3
      new ChiSquaredDistribution(3).inverseCumulativeProbability(0.95) should be > 7.8
    }
    "retain a direct representable upper tail after the CDF rounds to one" in {
      val d=GaussVonMisesScoreDistribution(2,1.0)
      d.cdf(100) shouldBe (1.0 +- 1e-10)
      d.survival(100) should be > 0.0
      val tighter=GaussVonMisesScoreDistribution(2,4.5,1e-12)
      tighter.survival(tighter.inverseSurvival(1e-8)) shouldBe (1e-8 +- 3e-12)
    }
    "report bounded work and honest numerical diagnostics" in {
      for (k <- Vector(0.1,4.5,50.0,1e8)) {
        val d=GaussVonMisesScoreDistribution(2,k)
        val r=d.cdfEstimate(5)
        r.estimatedAbsoluteError should be <= d.absoluteTolerance
        r.angularTruncationBound should be >= 0.0
        r.angularTruncationBound should be <= r.estimatedAbsoluteError
        r.evaluations should be > 0
        r.evaluations should be <= d.maxEvaluations
      }
      intercept[ArithmeticException] { GaussVonMisesScoreDistribution(2,1,maxEvaluations=32).cdf(5) }
    }
    "provide explicit endpoints and reject invalid inputs or unsupported inverse tails" in {
      val d=GaussVonMisesScoreDistribution(2,1)
      for (s <- Vector(Double.NegativeInfinity,-1.0,0.0)) { d.cdf(s) shouldBe 0.0; d.survival(s) shouldBe 1.0 }
      d.cdf(Double.PositiveInfinity) shouldBe 1.0; d.survival(Double.PositiveInfinity) shouldBe 0.0
      d.quantile(0) shouldBe 0.0; d.quantile(1) shouldBe Double.PositiveInfinity
      d.inverseSurvival(0) shouldBe Double.PositiveInfinity; d.inverseSurvival(1) shouldBe 0.0
      intercept[IllegalArgumentException] { d.cdf(Double.NaN) }
      for (p <- Vector(-0.1,1.1,Double.NaN,Double.PositiveInfinity,1e-12,1-1e-12)) {
        intercept[IllegalArgumentException] { d.quantile(p) }
        intercept[IllegalArgumentException] { d.inverseSurvival(p) }
      }
      for (n <- Vector(0,10001)) intercept[IllegalArgumentException] { GaussVonMisesScoreDistribution(n,1) }
      for (k <- Vector(-1.0,Double.NaN,Double.PositiveInfinity,1e9))
        intercept[IllegalArgumentException] { GaussVonMisesScoreDistribution(2,k) }
      for (tol <- Vector(1e-13,1e-3,Double.NaN))
        intercept[IllegalArgumentException] { GaussVonMisesScoreDistribution(2,1,tol) }
      for (budget <- Vector(31,1000001))
        intercept[IllegalArgumentException] { GaussVonMisesScoreDistribution(2,1,maxEvaluations=budget) }
    }
    "depend only on dimension and concentration, not physical coordinates or coupling" in {
      val p=coupled(4.5)
      val other=GaussVonMisesDistribution(Vector(0.0,0.0),Vector(Vector(1.0,0.0),Vector(0.0,1.0)),
        0,Vector(0.0,0.0),Vector(Vector(0.0,0.0),Vector(0.0,0.0)),4.5)
      p.scoreDistribution().quantile(0.95) shouldBe other.scoreDistribution().quantile(0.95)
      p.scoreDistribution(1e-12,20000).absoluteTolerance shouldBe 1e-12
    }
    "achieve modeled coverage for independent draws from coupled physical-coordinate kernels" in {
      for (k <- Vector(0.0,0.1,4.5,50.0)) {
        val p=coupled(k); val d=p.scoreDistribution(); val rng=new scala.util.Random(48301L)
        val levels=Vector(0.95,0.99); val thresholds=levels.map(d.quantile)
        val counts=Array.fill(2)(0); val size=40000
        for (_ <- 0 until size) {
          val score=p.mahalanobisSquared(p.sample(rng))
          for (i <- 0 until 2) if (score <= thresholds(i)) counts(i) += 1
        }
        // Five binomial standard deviations; fixed kernels, not fitted to these draws.
        for (i <- 0 until 2) counts(i).toDouble/size shouldBe
          (levels(i) +- (5*math.sqrt(levels(i)*(1-levels(i))/size)))
      }
    }
    "support concurrent read-only use with per-call work buffers" in {
      val d=GaussVonMisesScoreDistribution(2,4.5); val expected=d.quantile(0.95)
      val pool=Executors.newFixedThreadPool(4)
      try {
        val tasks=Vector.fill(8)(pool.submit(new Callable[Double] { def call(): Double=d.quantile(0.95) }))
        tasks.foreach(_.get(30,TimeUnit.SECONDS) shouldBe expected)
      } finally { pool.shutdownNow() }
    }
    "honor cancellation without clearing the interrupt flag" in {
      val d=GaussVonMisesScoreDistribution(2,4.5)
      Thread.currentThread().interrupt()
      try {
        intercept[CancellationException] { d.cdf(1) }
        intercept[CancellationException] { d.quantile(0.95) }
        Thread.currentThread().isInterrupted shouldBe true
      } finally { Thread.interrupted() }
    }
  }
}
