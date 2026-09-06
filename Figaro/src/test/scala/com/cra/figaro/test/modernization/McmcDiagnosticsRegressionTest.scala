package com.cra.figaro.test.modernization

import com.cra.figaro.algorithm.sampling.parallel.McmcDiagnostics
import org.apache.commons.math3.transform.{DftNormalization, FastFourierTransformer, TransformType}
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
    "preserve primitive value and stable index sorting including finite signed zeros" in {
      val random = new java.util.Random(992103L)
      val edges = Vector(Array.empty[Double], Array(0.0), Array(-0.0, 0.0, -0.0, 0.0),
        Array(Double.MaxValue, -Double.MaxValue, java.lang.Double.MIN_VALUE, -java.lang.Double.MIN_VALUE),
        Array.fill(33)(7.0), Array.tabulate(65)(_.toDouble), Array.tabulate(65)(i => -i.toDouble))
      val cases = edges ++ (for (n <- Vector(2, 3, 4, 5, 7, 8, 9, 31, 32, 33, 255, 256, 257, 1023, 1024, 1025, 16000);
        ties <- Vector(false, true)) yield Array.fill(n)(if (ties) random.nextInt(7).toDouble else random.nextGaussian()))
      cases.foreach { x =>
        val before = x.map(java.lang.Double.doubleToRawLongBits).toVector
        McmcDiagnostics.sortedIndices(x).toVector shouldBe x.indices.toArray.sortBy(x(_)).toVector
        McmcDiagnostics.sortedValues(x).map(java.lang.Double.doubleToRawLongBits).toVector shouldBe
          x.sorted.map(java.lang.Double.doubleToRawLongBits).toVector
        x.map(java.lang.Double.doubleToRawLongBits).toVector shouldBe before
      }
      // Exhaust all short combinations, including distinct signed-zero bit patterns.
      val alphabet = Vector(-1.0, -0.0, 0.0, 1.0)
      for (code <- 0 until 1024) {
        val x = Array.tabulate(5)(i => alphabet((code >> (2 * i)) & 3))
        McmcDiagnostics.sortedIndices(x).toVector shouldBe x.indices.toArray.sortBy(x(_)).toVector
        McmcDiagnostics.sortedValues(x).map(java.lang.Double.doubleToRawLongBits).toVector shouldBe
          x.sorted.map(java.lang.Double.doubleToRawLongBits).toVector
      }
    }

    "preserve the old rank normalization at every position for ties and extreme scales" in {
      def oldRanks(chains: Array[Array[Double]]): Array[Array[Double]] = {
        val values = chains.flatten
        val order = values.indices.toArray.sortBy(values(_))
        val result = new Array[Double](values.length)
        val normal = new org.apache.commons.math3.distribution.NormalDistribution(0, 1)
        var first = 0
        while (first < order.length) {
          var end = first + 1
          while (end < order.length && values(order(end)) == values(order(first))) end += 1
          val rank = (first + 1.0 + end) / 2.0
          val score = normal.inverseCumulativeProbability((rank - 0.375) / (values.length + 0.25))
          (first until end).foreach(i => result(order(i)) = score)
          first = end
        }
        result.grouped(chains.head.length).map(_.toArray).toArray
      }
      def bits(x: Array[Array[Double]]) = x.map(_.map(java.lang.Double.doubleToRawLongBits).toVector).toVector
      val random = new java.util.Random(882090L)
      for (n <- Vector(4, 5, 7, 8, 9, 31, 32, 33, 255, 256, 257, 1023, 1024, 1025, 2000);
        kind <- 0 until 4) {
        val chains = Array.fill(4)(Array.fill(n)(kind match {
          case 0 => random.nextGaussian()
          case 1 => random.nextInt(7).toDouble
          case 2 => if (random.nextBoolean()) -0.0 else 0.0
          case _ => java.lang.Math.scalb(random.nextGaussian(), if (random.nextBoolean()) -1022 else 1000)
        }))
        val before = bits(chains)
        bits(McmcDiagnostics.rankNormalize(chains)) shouldBe bits(oldRanks(chains))
        bits(chains) shouldBe before
      }
    }

    "isolate ranking buffers between callers and preserve sorting cancellation flags" in {
      val pool = java.util.concurrent.Executors.newFixedThreadPool(4)
      val input = Array.tabulate(4)(c => Array.tabulate(1025)(i => ((i * 7 + c) % 31).toDouble))
      def ranked() = McmcDiagnostics.rankNormalize(input).map(_.toVector).toVector
      val expected = ranked()
      try {
        val futures = Vector.fill(16)(pool.submit(new java.util.concurrent.Callable[Vector[Vector[Double]]] {
          def call(): Vector[Vector[Double]] = ranked()
        }))
        futures.foreach(_.get(10, java.util.concurrent.TimeUnit.SECONDS) shouldBe expected)
        val mutated = McmcDiagnostics.rankNormalize(input)
        mutated(0)(0) = -123.0
        ranked() shouldBe expected
      } finally {
        pool.shutdownNow()
        pool.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS) shouldBe true
      }
      try {
        Thread.currentThread().interrupt()
        intercept[InterruptedException](McmcDiagnostics.sortedValues(input(0)))
        intercept[InterruptedException](McmcDiagnostics.sortedIndices(input(0)))
        intercept[InterruptedException](McmcDiagnostics.rankNormalize(input))
        Thread.currentThread().isInterrupted shouldBe true
      } finally Thread.interrupted()
    }

    "match the former Complex-array autocovariance at every lag without mutating inputs" in {
      def oldAutocovariance(x: Array[Double]): Array[Double] = {
        var length = 1
        while (length.toLong < 2L * x.length) length *= 2
        val centered = new Array[Double](length)
        val mean = x.head + x.iterator.map(_ - x.head).sum / x.length
        x.indices.foreach(i => centered(i) = x(i) - mean)
        val fft = new FastFourierTransformer(DftNormalization.STANDARD)
        val spectrum = fft.transform(centered, TransformType.FORWARD)
        val inverse = fft.transform(spectrum.map(z => z.multiply(z.conjugate())), TransformType.INVERSE)
        inverse.take(x.length).map(_.getReal / x.length)
      }
      val random = new java.util.Random(442190L)
      val edges = Vector(Array(0.0), Array(-0.0), Array(-0.0, 0.0, -0.0),
        Array.fill(65)(3.0), Array.tabulate(65)(i => if (i == 0) 1.0 else 0.0),
        Array.tabulate(65)(i => if (i % 2 == 0) 1.0 else -1.0),
        Array(1e16, 1.0, -1e16, 1.0), Array(Double.MaxValue, -Double.MaxValue, 0.0),
        Array(1e160, -1e160, 0.0), Array(Double.NaN, 1.0), Array(Double.PositiveInfinity, 1.0))
      val cases = edges ++ (for (n <- Vector(1, 2, 3, 4, 5, 7, 8, 9, 31, 32, 33, 1023, 1024, 1025, 2000);
        exponent <- Vector(-1022, -500, -10, 0, 10, 500, 1000)) yield {
          Array.fill(n)(java.lang.Math.scalb(random.nextGaussian(), exponent))
        })
      cases.foreach { x =>
        val before = x.map(java.lang.Double.doubleToRawLongBits).toVector
        val expected = oldAutocovariance(x).map(java.lang.Double.doubleToLongBits).toVector
        McmcDiagnostics.autocovariance(x).map(java.lang.Double.doubleToLongBits).toVector shouldBe expected
        x.map(java.lang.Double.doubleToRawLongBits).toVector shouldBe before
      }
    }

    "preserve infinite forward-spectrum semantics from finite observations" in {
      // Zero shifted mean, but the forward FFT overflows: exercise the infinity
      // fallback separately from NaN input and finite-spectrum product overflow.
      val x = Array(0.0, Double.MaxValue, -Double.MaxValue, 0.0)
      val fft = new FastFourierTransformer(DftNormalization.STANDARD)
      val spectrum = fft.transform(x ++ Array.fill(4)(0.0), TransformType.FORWARD)
      spectrum.exists(_.isInfinite) shouldBe true
      val expected = fft.transform(spectrum.map(z => z.multiply(z.conjugate())), TransformType.INVERSE)
        .take(x.length).map(z => java.lang.Double.doubleToLongBits(z.getReal / x.length)).toVector
      McmcDiagnostics.autocovariance(x).map(java.lang.Double.doubleToLongBits).toVector shouldBe expected
    }

    "match direct biased autocovariances including the last lag and padding boundaries" in {
      val random = new java.util.Random(72039L)
      for (n <- Vector(2, 3, 4, 7, 8, 9, 31, 32, 33, 127, 128, 129)) {
        val x = Array.fill(n)(random.nextGaussian())
        val mean = x.sum / n
        val actual = McmcDiagnostics.autocovariance(x)
        for (lag <- x.indices) {
          val expected = (0 until n - lag).map(i => (x(i) - mean) * (x(i + lag) - mean)).sum / n
          actual(lag) shouldBe (expected +- 1e-12)
        }
      }
    }

    "isolate FFT buffers between concurrent calls and returned arrays" in {
      val pool = java.util.concurrent.Executors.newFixedThreadPool(4)
      val input = Array.tabulate(1025)(i => math.sin(i * 0.37))
      val expected = McmcDiagnostics.autocovariance(input).toVector
      try {
        val futures = Vector.fill(16)(pool.submit(new java.util.concurrent.Callable[Array[Double]] {
          def call(): Array[Double] = McmcDiagnostics.autocovariance(input)
        }))
        val results = futures.map(_.get(10, java.util.concurrent.TimeUnit.SECONDS))
        results.foreach(_.toVector shouldBe expected)
        results.head(0) = -123.0
        results.tail.foreach(_.toVector shouldBe expected)
        McmcDiagnostics.autocovariance(input).toVector shouldBe expected
      } finally {
        pool.shutdownNow()
        pool.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS) shouldBe true
      }
    }

    "preserve cancellation flags at autocovariance entry" in {
      try {
        Thread.currentThread().interrupt()
        intercept[InterruptedException](McmcDiagnostics.autocovariance(Array(1.0, 2.0)))
        Thread.currentThread().isInterrupted shouldBe true
      } finally Thread.interrupted()
    }

    "match the previous iterator reductions bit for bit without mutating inputs" in {
      def oldAverage(x: Array[Double]): Double = x.head + x.iterator.map(_ - x.head).sum / x.length
      def oldVariance(x: Array[Double]): Double = {
        val mean = oldAverage(x)
        x.iterator.map(v => (v - mean) * (v - mean)).sum / (x.length - 1)
      }
      def bits(x: Double) = java.lang.Double.doubleToLongBits(x)
      val random = new java.util.Random(673821L)
      val edges = Vector(Array(0.0), Array(-0.0), Array(-0.0, 0.0, -0.0),
        Array(1e16, 1.0, -1e16, 1.0), Array(1e-300, -1e-300, java.lang.Double.MIN_VALUE),
        Array(Double.MaxValue, -Double.MaxValue, 0.0), Array.fill(101)(7.0))
      val cases = edges ++ (for (n <- Vector(1, 2, 3, 4, 5, 31, 32, 33, 1023, 1024, 1025, 4000);
        exponent <- Vector(-1022, -500, -10, 0, 10, 500, 1000)) yield {
          Array.fill(n)(java.lang.Math.scalb(random.nextGaussian(), exponent))
        })
      cases.foreach { x =>
        val before = x.map(java.lang.Double.doubleToRawLongBits)
        bits(McmcDiagnostics.average(x)) shouldBe bits(oldAverage(x))
        bits(McmcDiagnostics.variance(x)) shouldBe bits(oldVariance(x))
        x.map(java.lang.Double.doubleToRawLongBits).toVector shouldBe before.toVector
      }
    }

    "preserve cancellation flags in both primitive reductions" in {
      try {
        Thread.currentThread().interrupt()
        intercept[InterruptedException](McmcDiagnostics.average(Array(1.0, 2.0)))
        intercept[InterruptedException](McmcDiagnostics.variance(Array(1.0, 2.0)))
        Thread.currentThread().isInterrupted shouldBe true
      } finally Thread.interrupted()
    }

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
