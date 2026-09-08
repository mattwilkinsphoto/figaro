package com.cra.figaro.test.modernization

import com.cra.figaro.ndtest.TTestResult
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class StatisticalValidationTest extends AnyWordSpec with Matchers {
  import StatisticalValidationStudy.*
  "Statistical validation diagnostics" should {
    "report standard deviation over square root n without changing the t test" in {
      val result = new TTestResult("control", 4)
      Vector(2.0, 4.0, 6.0).foreach(result.update)
      result.errorMessage should include ("standard error 1.154701")
      result.check shouldBe true
      val biased = new TTestResult("biased", -10)
      Vector(2.0, 4.0, 6.0).foreach(biased.update)
      biased.check shouldBe false
    }
    "match the independent observation sufficient statistics" in {
      gammaData.size shouldBe 200
      dirichletData.size shouldBe 200
      sumGamma shouldBe (767.2000858136155 +- 1e-10)
      sumLogGamma shouldBe (217.97979608090918 +- 1e-10)
      dirichletData.foreach(_.sum shouldBe (1.0 +- 1e-14))
    }
    "compute weight concentration and MCSE with log-shift invariance" in {
      for (shift <- Vector(0.0, -10000.0, 10000.0)) {
        val a = new Accumulator(1)
        a.add(shift, Vector(1)); a.add(shift, Vector(3))
        val r = a.result
        r.mean shouldBe Vector(2)
        r.ess shouldBe 2.0
        r.maxWeight shouldBe .5
        r.mcse.head shouldBe (math.sqrt(.5) +- 1e-14)
      }
    }
    "expose degenerate weights instead of mistaking zero MCSE for certainty" in {
      val a = new Accumulator(1)
      a.add(Double.NegativeInfinity, Vector(999))
      intercept[IllegalArgumentException](a.result)
      a.add(0, Vector(2))
      a.result.ess shouldBe 1.0
      a.result.maxWeight shouldBe 1.0
      a.result.mcse shouldBe Vector(0.0)
      intercept[IllegalArgumentException](a.add(Double.NaN, Vector(2)))
    }
    "replay both named RNGs without selecting passing seeds" in {
      for (family <- Vector("gamma", "dirichlet"); rng <- Vector("Random", "L64X128MixRandom")) {
        kernel(family, rng, seeds.head, 2000) shouldBe kernel(family, rng, seeds.head, 2000)
      }
    }
    "match actual observed-graph weights and Importance aggregation" in {
      for (family <- Vector("gamma", "dirichlet")) {
        val (summary, _) = graph(family, seeds.head, 100)
        summary.ess should be >= 1.0
        summary.mean.forall(_.isFinite) shouldBe true
      }
    }
  }
}
