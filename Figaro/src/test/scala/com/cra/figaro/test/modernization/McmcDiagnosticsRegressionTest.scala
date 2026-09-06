package com.cra.figaro.test.modernization

import com.cra.figaro.algorithm.sampling.parallel.McmcDiagnostics
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class McmcDiagnosticsRegressionTest extends AnyWordSpec with Matchers {
  private def independent(n: Int) = (0 until 4).map { c =>
    val random = new java.util.Random(1293L + c)
    Vector.fill(n)(random.nextGaussian())
  }
  // Independent O(N^2) oracle: no FFT, normal ranking, or production helpers.
  private def directEss(chains: Seq[Seq[Double]]): Double = {
    val split = chains.flatMap(x => Seq(x.take(x.size / 2), x.takeRight(x.size / 2)))
    val n = split.head.size
    def variance(xs: Seq[Double]) = { val mean = xs.sum / xs.size; xs.map(x => math.pow(x - mean, 2)).sum / (xs.size - 1) }
    val within = split.map(variance).sum / split.size
    val v = within * (n - 1.0) / n + variance(split.map(x => x.sum / n))
    val rho = (0 until n).map { lag =>
      if (lag == 0) 1.0 else {
        val ac = split.map { xs =>
          val mean = xs.sum / n
          (0 until n - lag).map(i => (xs(i) - mean) * (xs(i + lag) - mean)).sum / n
        }.sum / split.size
        1 - (within - ac) / v
      }
    }
    val positive = rho.grouped(2).filter(_.size == 2).map(_.sum).takeWhile(_ > 0).toVector
    val monotone = positive.scanLeft(Double.PositiveInfinity)(math.min).tail
    split.size.toDouble * n / math.max(1, -1 + 2 * monotone.sum)
  }
  "MCMC diagnostics" should {
    "match an independently calculated tied-rank and folded-R-hat fixture" in {
      // Python standard-library statistics.NormalDist.inv_cdf, midranks, and
      // sample variances give rank R-hat=0.9860127788581047 and folded value below.
      val xs = Vector.tabulate(4)(c => Vector.tabulate(40)(i => ((i * i + 7 * i + 11 * c) % 37).toDouble))
      val d = McmcDiagnostics.summarize(xs)
      d.rHat.get shouldBe (1.0114695510300256 +- 1e-12)
      d.mean shouldBe (18.075 +- 1e-12)
      d.standardDeviation shouldBe (10.800390162996704 +- 1e-12)
      d.meanEss.get shouldBe (directEss(xs) +- 1e-8)
    }
    "report near-unit R-hat and high ESS for independent draws" in {
      val d = McmcDiagnostics.summarize(independent(4000))
      d.mean shouldBe (0.0 +- 0.03)
      d.standardDeviation shouldBe (1.0 +- 0.03)
      d.rHat.get should be < 1.01
      d.bulkEss.get should be > 10000.0
      d.tailEss.get should be > 8000.0
      d.mcseMean.get shouldBe (d.standardDeviation / math.sqrt(d.meanEss.get) +- 1e-12)
    }
    "detect autocorrelation and agree with a direct autocovariance reference" in {
      val chains = independent(200).map(_.scanLeft(0.0)((x, z) => 0.85 * x + z).tail)
      val d = McmcDiagnostics.summarize(chains)
      d.meanEss.get shouldBe (directEss(chains) +- 1e-8)
      d.meanEss.get should be < 200.0
    }
    "detect shifted, scale-mismatched, and drifting chains" in {
      val draws = independent(2000)
      McmcDiagnostics.summarize(draws.zipWithIndex.map((xs, i) => xs.map(_ + i * 3))).rHat.get should be > 1.1
      McmcDiagnostics.summarize(draws.zipWithIndex.map((xs, i) => xs.map(_ * (if (i == 0) 10 else 1)))).rHat.get should be > 1.1
      McmcDiagnostics.summarize(draws.map(_.zipWithIndex.map((x, i) => x + i * 0.01))).rHat.get should be > 1.1
    }
    "return unavailable diagnostics for constants, not a false convergence certificate" in {
      val same = McmcDiagnostics.summarize(Vector.fill(4)(Vector.fill(100)(1.0)))
      same.rHat shouldBe None
      same.bulkEss shouldBe None
      same.mcseMean shouldBe None
      same.warnings should not be empty
      val stuck = McmcDiagnostics.summarize(Vector.tabulate(4)(i => Vector.fill(100)(i.toDouble)))
      stuck.rHat shouldBe Some(Double.PositiveInfinity)
      stuck.bulkEss.get should be < 20.0
    }
    "handle tied discrete ranks and explicitly flag degenerate tails" in {
      val d = McmcDiagnostics.summarize(independent(1000).map(_.map(x => if (x > 0) 1.0 else 0.0)))
      d.rHat.get should be < 1.02
      d.bulkEss.get should be > 2000.0
      d.tailEss shouldBe None
      d.warnings.exists(_.contains("discrete tails")) shouldBe true
    }
    "use all odd-length draws for the mean but omit their midpoint only for split diagnostics" in {
      val xs = independent(100)
      val odd = xs.map(x => x.take(50) ++ Vector(1000.0) ++ x.drop(50))
      val result = McmcDiagnostics.summarize(odd)
      result.mean shouldBe (odd.flatten.sum / 404 +- 1e-12)
      result.bulkEss shouldBe McmcDiagnostics.summarize(xs).bulkEss
      result.warnings.exists(_.contains("Middle draw")) shouldBe true
    }
    "preserve dimensionless diagnostics under large finite rescaling" in {
      val xs = independent(100)
      val normal = McmcDiagnostics.summarize(xs)
      val large = McmcDiagnostics.summarize(xs.map(_.map(_ * 1e200)))
      large.rHat.get shouldBe (normal.rHat.get +- 1e-12)
      large.meanEss.get shouldBe (normal.meanEss.get +- 1e-8)
      large.mcseMean.get / 1e200 shouldBe (normal.mcseMean.get +- 1e-12)
      large.standardDeviation.isFinite shouldBe true
    }
    "reject invalid dimensions and non-finite data and warn for short traces" in {
      intercept[IllegalArgumentException](McmcDiagnostics.summarize(Vector.empty))
      intercept[IllegalArgumentException](McmcDiagnostics.summarize(Vector(Vector.fill(10)(1.0))))
      intercept[IllegalArgumentException](McmcDiagnostics.summarize(Vector(Vector.fill(3)(1.0), Vector.fill(4)(1.0))))
      intercept[IllegalArgumentException](McmcDiagnostics.summarize(Vector.fill(2)(Vector(1.0, 2.0, 3.0, Double.PositiveInfinity))))
      val d = McmcDiagnostics.summarize(Vector(Vector(1.0, 2.0, 3.0, 4.0), Vector(2.0, 1.0, 4.0, 3.0)))
      d.meanEss shouldBe None
      d.warnings should not be empty
    }
  }
}
