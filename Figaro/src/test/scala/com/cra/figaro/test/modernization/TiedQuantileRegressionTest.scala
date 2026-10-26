package com.cra.figaro.test.modernization

import com.cra.figaro.algorithm.sampling.parallel.McmcDiagnostics
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Deterministic arithmetic controls; no study or confirmation seeds. */
class TiedQuantileRegressionTest extends AnyWordSpec with Matchers {
  private def bits(x: Double) = java.lang.Double.doubleToRawLongBits(x)
  private def legacy(x: Array[Double], p: Double): Double = {
    val t = p * (x.length - 1); val i = t.toInt; val w = t - i
    x(i) * (1 - w) + x(math.min(i + 1, x.length - 1)) * w
  }
  private val probabilities = Vector(0.0, 0.05, 0.1, 0.3, 0.5, 0.95, 1.0)

  "Tail quantile interpolation" should {
    "preserve the exact repeated endpoint from the frozen audit failure" in {
      val v = -0.6568859528122107
      val x = Array.fill(16000)(v)
      bits(legacy(x, 0.05)) should not be bits(v)
      bits(McmcDiagnostics.quantile(x, 0.05)) shouldBe bits(v)
    }

    "preserve tied finite endpoints across signs, exponents and subnormals" in {
      val values = Vector(-0.0, 0.0, Double.MaxValue, -Double.MaxValue,
        java.lang.Double.MIN_VALUE, -java.lang.Double.MIN_VALUE,
        java.lang.Double.MIN_NORMAL, -java.lang.Double.MIN_NORMAL) ++
        (for (e <- -1074 to 1023 by 7; s <- Vector(-1.0, 1.0))
          yield java.lang.Math.scalb(s * 1.3141592653589793, e)).filter(_.isFinite)
      for (v <- values; n <- Vector(1, 2, 21, 101, 16000); p <- probabilities) {
        bits(McmcDiagnostics.quantile(Array.fill(n)(v), p)) shouldBe bits(v)
      }
    }

    "retain unequal-endpoint arithmetic and avoid opposite-sign subtraction overflow" in {
      val cases = Vector(Array(-Double.MaxValue, Double.MaxValue),
        Array(-java.lang.Double.MIN_VALUE, java.lang.Double.MIN_VALUE),
        Array(-3.0, -1.0, 0.0, 1.0, 7.0), Array(1.0, math.nextUp(1.0)),
        Array(-0.0, 0.0))
      for (x <- cases; p <- probabilities) {
        val t = p * (x.length - 1); val lo = t.toInt; val hi = math.min(lo + 1, x.length - 1)
        val actual = McmcDiagnostics.quantile(x, p)
        actual.isFinite shouldBe true
        actual should be >= x(lo)
        actual should be <= x(hi)
        if (x(lo) != x(hi)) bits(actual) shouldBe bits(legacy(x, p))
      }
      McmcDiagnostics.quantile(Array(-Double.MaxValue, Double.MaxValue), 0.5) shouldBe 0.0
      val x = Array(-3.0, -1.0, 0.0, 1.0, 7.0)
      (0 to 1000).map(i => McmcDiagnostics.quantile(x, i / 1000.0))
        .sliding(2).foreach(v => v(1) should be >= v(0))
    }

    "agree with direct indicator autocovariances on repeated rejected-state patterns" in {
      // Each tail plateau has exactly known endpoints. No quantile helper in the oracle.
      val lo = -0.6568859528122107; val hi = 0.6177859378881788
      val chains = Vector.tabulate(4)(c => Vector.tabulate(101) { i =>
        val j = (i * 17 + c * 13) % 101
        if (j < 12) lo else if (j >= 89) hi else (j - 50) / 100.0
      })
      def directEss(input: Vector[Vector[Double]], cut: Double): Double = {
        val split = input.flatMap(x => Vector(x.take(50), x.takeRight(50)))
          .map(_.map(x => if (x <= cut) 1.0 else 0.0))
        val n = 50; val means = split.map(_.sum / n)
        val ac = split.zip(means).map { (x, mean) =>
          (0 until n).map(lag => (0 until n - lag).map(i => (x(i)-mean)*(x(i+lag)-mean)).sum / n)
        }
        val w = ac.map(_(0)).sum / split.size * n / (n - 1.0)
        val center = means.sum / means.size
        val v = w * (n-1.0)/n + means.map(m => (m-center)*(m-center)).sum/(means.size-1)
        if (!(v > 0)) Double.NaN else {
          val rho = (0 until n).map(lag => if (lag == 0) 1.0 else 1-(w-ac.map(_(lag)).sum/split.size)/v)
          val pairs = rho.grouped(2).map(_.sum).takeWhile(_ > 0).toVector
          val monotone = pairs.scanLeft(Double.PositiveInfinity)(math.min).tail
          split.size * n / math.max(1.0, -1 + 2*monotone.sum)
        }
      }
      val d = McmcDiagnostics.summarize(chains)
      // q95 is the maximum: its indicator is constant and tail ESS must be absent.
      directEss(chains, hi).isNaN shouldBe true
      d.tailEss shouldBe None
      // Add a distinct upper tail so both indicator ESS estimates are defined.
      val shifted = chains.map(_.zipWithIndex.map((v,i) => if (i % 41 == 0) 1.0 else v))
      val indicators = shifted.map(_.map(v => if (v <= lo) 1.0 else 0.0))
      val sorted = shifted.flatten.sorted.toArray
      McmcDiagnostics.quantile(sorted, 0.05) shouldBe lo
      McmcDiagnostics.quantile(sorted, 0.95) shouldBe hi
      McmcDiagnostics.summarize(shifted).tailEss.get shouldBe
        (math.min(directEss(shifted, lo), directEss(shifted, hi)) +- 1e-8)
      indicators.flatten.count(_ == 1.0) should be > 0
    }

    "retain undefined constant tails and infinite R-hat for separated constants" in {
      val v = -0.6568859528122107
      val identical = McmcDiagnostics.summarize(Vector.fill(4)(Vector.fill(101)(v)))
      identical.tailEss shouldBe None
      identical.rHat shouldBe None
      val separated = McmcDiagnostics.summarize(Vector.tabulate(4)(c => Vector.fill(101)(v + c)))
      separated.rHat shouldBe Some(Double.PositiveInfinity)
      separated.warnings.exists(_.contains("constant")) shouldBe true
    }
  }
}
