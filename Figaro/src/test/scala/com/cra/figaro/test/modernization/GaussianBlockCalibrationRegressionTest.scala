package com.cra.figaro.test.modernization

import com.cra.figaro.algorithm.sampling.{GaussianBlockCalibration as Calibration, ProposalScheme}
import com.cra.figaro.algorithm.sampling.parallel.{McmcPrecision, MultiChainMetropolisHastings as MH}
import com.cra.figaro.language.*
import com.cra.figaro.library.atomic.continuous.Normal
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import scala.jdk.CollectionConverters.*

class GaussianBlockCalibrationRegressionTest extends AnyWordSpec with Matchers {
  private def pilot(n: Int = 2000, scales: Vector[Double] = Vector(2.0, 3.0), shift: Double = 0): MH.Result = {
    val chains = Vector.tabulate(4) { c =>
      val rng = new java.util.Random(871L + c)
      val rows = Vector.fill(n) { val x = rng.nextGaussian(); val y = 0.6 * x + 0.8 * rng.nextGaussian()
        Vector(shift + scales(0) * x, shift + scales(1) * y) }
      MH.ChainResult(c, c.toLong, Map("x" -> rows.map(_(0)), "y" -> rows.map(_(1))), 1.0, 1, 0.0)
    }
    MH.Result(chains, Map.empty, 0.0)
  }
  private def replace(p: MH.Result)(f: MH.ChainResult => MH.ChainResult): MH.Result = p.copy(chains = p.chains.map(f))
  private def model(fit: Option[Calibration.Fit])(u: Universe, i: Int): MH.Model = {
    val x = Normal(0, 1)(using "", u)
    MH.Model(Vector(MH.Observable("x", x)(identity)), fit.map(_.proposal(Map("x" -> x))))
  }

  "Gaussian block calibration" should {
    "match an independent within-chain covariance oracle and disclose shrinkage and scale" in {
      val p = pilot(2000, shift = 1e6)
      val config = Calibration.Config(varianceMultiplier = 2.0, diagonalShrinkage = 0.2)
      val fit = Calibration.fit(p, Vector("x", "y"), config)
      val keys = fit.names
      for (i <- 0 until 2; j <- 0 until 2) {
        val expected = p.chains.map { c =>
          val a = c.draws(keys(i)); val b = c.draws(keys(j))
          val ma = a.sum / a.size; val mb = b.sum / b.size
          a.zip(b).map((x, y) => (x - ma) * (y - mb)).sum
        }.sum / (4 * 1999)
        fit.empiricalCovariance(i)(j) shouldBe (expected +- 1e-9)
        fit.covariance(i)(j) shouldBe (expected * 2 * (if (i == j) 1 else 0.8) +- 1e-9)
        fit.covariance(i)(j) shouldBe fit.covariance(j)(i)
      }
      fit.config shouldBe config
      fit.chains shouldBe 4
      fit.drawsPerChain shouldBe 2000
      fit.diagnostics.keySet shouldBe Set("x", "y") // Recomputed even though input diagnostics are empty.
    }
    "preserve value-unit scaling across extremely unequal coordinate scales" in {
      val fit = Calibration.fit(pilot(scales = Vector(1e-100, 1e100)), Vector("x", "y"))
      (fit.empiricalCovariance(0)(0) / 1e-200) shouldBe (1.0 +- 0.06)
      (fit.empiricalCovariance(1)(1) / 1e200) shouldBe (1.0 +- 0.06)
      fit.empiricalCovariance(0)(1) shouldBe (0.6 +- 0.06)
    }
    "respect requested name order and bind only an exact fresh target map" in {
      val forward = Calibration.fit(pilot(), Vector("x", "y"))
      val reverse = Calibration.fit(pilot(), Vector("y", "x"))
      reverse.covariance(0)(0) shouldBe forward.covariance(1)(1)
      val u = new Universe
      try {
        val x = Normal(0, 1)(using "", u); val y = Normal(0, 1)(using "", u)
        reverse.proposal(Map("x" -> x, "y" -> y))
        intercept[IllegalArgumentException](reverse.proposal(Map("x" -> x)))
        intercept[IllegalArgumentException](reverse.proposal(Map("x" -> x, "y" -> y, "extra" -> x)))
        intercept[IllegalArgumentException](reverse.proposal(Map("x" -> x, "y" -> x)))
        intercept[IllegalArgumentException](reverse.proposal(null))
      } finally u.clear()
    }
    "make perfectly collinear pilot coordinates numerically usable with explicit shrinkage" in {
      val p = replace(pilot())(c => c.copy(draws = c.draws.updated("y", c.draws("x"))))
      val fit = Calibration.fit(p, Vector("x", "y"))
      fit.covariance(0)(1) shouldBe (fit.covariance(0)(0) * 0.95 +- 1e-12)
      val diagonal = Calibration.fit(p, Vector("x", "y"), Calibration.Config(diagonalShrinkage = 1))
      diagonal.covariance(0)(1) shouldBe 0.0
    }
    "reject invalid configuration rather than hiding arbitrary regularization" in {
      Vector(0.0, -1.0, Double.NaN, Double.PositiveInfinity).foreach { v =>
        intercept[IllegalArgumentException](Calibration.Config(varianceMultiplier = v))
        intercept[IllegalArgumentException](Calibration.Config(diagonalShrinkage = v))
        intercept[IllegalArgumentException](Calibration.Config(minEssPerChain = v))
      }
      intercept[IllegalArgumentException](Calibration.Config(diagonalShrinkage = 1.1))
      intercept[IllegalArgumentException](Calibration.Config(maxRHat = 0.99))
      intercept[IllegalArgumentException](Calibration.Config(minDrawsPerChain = 19))
      intercept[IllegalArgumentException](Calibration.Config(maxDimension = 0))
    }
    "reject missing names, invalid dimensions, short and misaligned traces" in {
      val p = pilot()
      Vector(Vector.empty[String], Vector("x", "x"), Vector("absent")).foreach { names =>
        intercept[IllegalArgumentException](Calibration.fit(p, names))
      }
      intercept[IllegalArgumentException](Calibration.fit(null, Vector("x")))
      intercept[IllegalArgumentException](Calibration.fit(p, null))
      intercept[IllegalArgumentException](Calibration.fit(p, Vector("x"), null))
      intercept[IllegalArgumentException](Calibration.fit(p, Vector("x", "y"), Calibration.Config(maxDimension = 1)))
      intercept[IllegalArgumentException](Calibration.fit(pilot(100), Vector("x")))
      intercept[IllegalArgumentException](Calibration.fit(p.copy(chains = p.chains.take(1)), Vector("x")))
      intercept[IllegalArgumentException](Calibration.fit(replace(p)(c => c.copy(draws = c.draws.updated("y", Vector(1.0)))), Vector("x", "y")))
    }
    "reject nonfinite, stuck, separated, and low-ESS pilots without trusting supplied summaries" in {
      val p = pilot()
      val invalid = Vector(
        replace(p)(c => c.copy(draws = c.draws.updated("x", c.draws("x").updated(0, Double.NaN)))),
        replace(p)(c => c.copy(draws = c.draws.updated("x", Vector.fill(2000)(c.index.toDouble)))),
        replace(p)(c => c.copy(draws = c.draws.updated("x", c.draws("x").map(_ + 100 * c.index)))),
        replace(p)(c => c.copy(draws = c.draws.updated("x", c.draws("x").take(20).flatMap(v => Vector.fill(100)(v))))))
      invalid.foreach(bad => intercept[IllegalArgumentException](Calibration.fit(bad, Vector("x"))))
      intercept[IllegalArgumentException](Calibration.fit(p, Vector("x"), Calibration.Config(minEssPerChain = 1e9)))
    }
    "reject unrepresentable final covariance instead of adding hidden jitter" in {
      intercept[IllegalArgumentException](Calibration.fit(pilot(scales = Vector(1e-200, 1.0)), Vector("x", "y")))
      intercept[IllegalArgumentException](Calibration.fit(pilot(), Vector("x", "y"),
        Calibration.Config(varianceMultiplier = Double.MaxValue)))
    }
    "honor interruption without clearing the caller interrupt flag" in {
      Thread.currentThread().interrupt()
      try {
        intercept[InterruptedException](Calibration.fit(pilot(), Vector("x", "y")))
        Thread.currentThread().isInterrupted shouldBe true
      } finally { Thread.interrupted(); () }
    }
    "recover correlated posterior moments using pilot-selected geometry rather than an oracle matrix" in {
      def build(fit: Option[Calibration.Fit])(u: Universe, index: Int): MH.Model = {
        val x = Normal(0, 1)(using "", u); val y = Normal(0, 1)(using "", u)
        val difference = Apply(x, y, (a: Double, b: Double) => a - b)(using "", u)
        difference.addLogConstraint((v: Double) => -0.5 * math.pow(v / 0.3, 2))
        val product = Apply(x, y, (a: Double, b: Double) => a * b)(using "", u)
        MH.Model(Vector(MH.Observable("x", x)(identity), MH.Observable("y", y)(identity),
          MH.Observable("xy", product)(identity)),
          Some(fit.fold(ProposalScheme(x, y))(_.proposal(Map("x" -> x, "y" -> y)))))
      }
      val pilot = MH.run(MH.Config(drawsPerChain = 10000, warmUp = 1000, seed = 923))(build(None))
      val fit = Calibration.fit(pilot, Vector("x", "y"))
      val result = MH.run(MH.Config(drawsPerChain = 12000, warmUp = 1000, seed = 945))(build(Some(fit)))
      result.diagnostics("x").mean shouldBe (0.0 +- 0.08)
      result.diagnostics("y").mean shouldBe (0.0 +- 0.08)
      result.diagnostics("xy").mean shouldBe (1.0 / 2.09 +- 0.08)
      math.pow(result.diagnostics("x").standardDeviation, 2) shouldBe (1.09 / 2.09 +- 0.08)
    }
    "reuse a detached fit in fresh fixed and precision runs without retaining pilot draws" in {
      val pilotConfig = MH.Config(chains = 4, drawsPerChain = 3000, warmUp = 500, seed = 321)
      val p = MH.run(pilotConfig)(model(None))
      val fit = Calibration.fit(p, Vector("x"))
      val config = MH.Config(chains = 4, drawsPerChain = 3000, warmUp = 500, seed = 654)
      val before = fit.covariance
      val fixed = MH.run(config)(model(Some(fit)))
      val serial = MH.run(config.copy(parallelism = 1))(model(Some(fit)))
      fixed.chains.map(_.draws) shouldBe serial.chains.map(_.draws)
      fixed.diagnostics("x").mean shouldBe (0.0 +- 0.1)
      fixed.diagnostics("x").standardDeviation shouldBe (1.0 +- 0.1)
      val stopped = MH.runUntilPrecise(config, McmcPrecision.Config(relativeTolerance = 0.4,
        minDrawsPerChain = 1000, checkEvery = 1000))(model(Some(fit)))
      stopped.result.chains.zip(fixed.chains).foreach { (early, full) =>
        early.draws("x") shouldBe full.draws("x").take(early.draws("x").size)
      }
      fixed.chains.foreach(_.draws("x").size shouldBe config.drawsPerChain)
      fit.covariance shouldBe before
      Thread.getAllStackTraces.keySet().asScala.exists(_.getName.startsWith("figaro-mcmc-worker-")) shouldBe false
    }
  }
}
