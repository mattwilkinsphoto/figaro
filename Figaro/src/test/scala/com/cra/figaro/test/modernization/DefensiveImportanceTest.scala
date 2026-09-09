package com.cra.figaro.test.modernization

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import com.cra.figaro.library.atomic.continuous.MultivariateGaussianDistribution as Gaussian
import com.cra.figaro.util.SamplingRandom

class DefensiveImportanceTest extends AnyWordSpec with Matchers {
  private val S = DefensiveImportanceStudy
  "The research defensive proposal" should {
    "use the complete normalized mixture density inside and outside the prior box" in {
      val g = Gaussian(Vector(5.0,5.0),Vector(Vector(1.0,.3),Vector(.3,2.0)))
      val p = S.Proposal(2,Some(g))
      val oracle = new org.apache.commons.math3.distribution.MultivariateNormalDistribution(
        g.mean.toArray,g.covariance.map(_.toArray).toArray)
      for (x <- Vector(Vector(5.0,5.0),Vector(-1.0,5.0),Vector(9.0,9.0))) {
        val expected = .9*oracle.density(x.toArray)+(if(S.inside(x)) .001 else 0.0)
        math.exp(p.logDensity(x)) shouldBe (expected +- 1e-14)
      }
      p.logDensity(Vector(5.0,5.0)) should not be g.logDensity(Vector(5.0,5.0))
      S.targets.head.logTarget(Vector(-1.0,5.0)) shouldBe Double.NegativeInfinity
      p.logDensity(Vector(-1.0,5.0)).isFinite shouldBe true
    }
    "retain seeded replay, outside-box draws, and explicit undertrained fallback" in {
      val p = S.Proposal(2,Some(Gaussian(Vector(0.0,0.0),Vector(Vector(1.0,0.0),Vector(0.0,1.0)))))
      def draws = { val r=SamplingRandom.scalaRandom(1L); Vector.fill(1000)(p.draw(r)) }
      draws shouldBe draws
      draws.exists(x => !S.inside(x)) shouldBe true
      S.fit(Vector.empty,2).gaussian shouldBe None
      val points = Vector.tabulate(20)(i => Vector(i.toDouble/10,2*i.toDouble/10))
      val fitted = S.fit(Vector.fill(4)(points),2).gaussian.get
      fitted.mean shouldBe Vector(.95,1.9)
      fitted.covariance(0)(0) should be > 0.0
      intercept[IllegalArgumentException](S.fit(Vector.fill(4)(Vector.fill(20)(Vector(Double.NaN))),1))
    }
    "charge pilot, initialization and discarded work to exact total budgets" in {
      val f = S.targets.head
      for (method <- S.methods) {
        val r = S.run(f,method,42L,2000)
        r.evaluations shouldBe 2000L
        r.coordinates.size shouldBe 2
        if(method == "defensive") {
          r.pilotEvaluations shouldBe 1000L
          r.draws shouldBe 1000
          val replay = S.run(f,method,42L,2000)
          r.copy(seconds=0) shouldBe replay.copy(seconds=0)
          val poisonedOracle = f.copy(reference=Vector.fill(2)(Double.NaN),sd=Vector.empty)
          S.run(poisonedOracle,method,42L,2000).copy(seconds=0) shouldBe r.copy(seconds=0)
        }
      }
      val short = S.run(f,"defensive",42L,200)
      short.fallback shouldBe true
      short.pilotEvaluations shouldBe 100L
      short.evaluations shouldBe 200L
    }
    "match independent held-out sufficient statistics and existing likelihood controls" in {
      val old = S.targets.head
      val x = Vector(2.0,1.8)
      old.logTarget(x) shouldBe StatisticalValidationStudy.logLikelihood("gamma",x)
      val held = S.targets.find(_.id == "gamma-heldout").get
      held.gammaSum shouldBe (781.045857800488 +- 1e-10)
      held.gammaLogSum shouldBe (222.2346072267285 +- 1e-10)
      held.dirichletLogs.zip(Vector(-471.7548305989705,-259.05917606997656,-153.0829008375151))
        .foreach((a,b) => a shouldBe (b +- 1e-10))
    }
    "preserve cancellation and reject invalid work requests" in {
      intercept[IllegalArgumentException](S.run(S.targets.head,"unknown",42L,2000))
      intercept[IllegalArgumentException](S.run(S.targets.head,"prior",42L,201))
      Thread.currentThread().interrupt()
      try {
        intercept[InterruptedException](S.run(S.targets.head,"prior",42L,2000))
        Thread.currentThread().isInterrupted shouldBe true
      } finally Thread.interrupted()
    }
  }
}
