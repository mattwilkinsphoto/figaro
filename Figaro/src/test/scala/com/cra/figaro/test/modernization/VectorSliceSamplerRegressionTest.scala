package com.cra.figaro.test.modernization

import com.cra.figaro.algorithm.sampling.VectorSliceSampler as VS
import java.util.concurrent.{Callable, CountDownLatch, Executors, TimeUnit}
import java.util.concurrent.atomic.AtomicReference
import org.apache.commons.math3.distribution.CauchyDistribution
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class VectorSliceSamplerRegressionTest extends AnyWordSpec with Matchers {
  private val normal: Vector[Double] => Double = x => -x.map(v => v * v).sum / 2
  private val start = Vector(1.0, 0.5)
  private def config(m: VS.Method) = VS.Config(m, draws = 100, warmUp = 20, seed = 779)
  private def open(r: java.util.Random): Double = { var u = r.nextDouble(); while (u == 0) u = r.nextDouble(); u }

  // Independent two-dimensional formulation: angular state and determinant orientation,
  // rather than the production d-dimensional projected/reorthogonalized vector arithmetic.
  private def planar(x: Vector[Double], r: java.util.Random, f: Vector[Double] => Double): Vector[Double] = {
    val radius = math.hypot(x(0), x(1))
    val theta = math.atan2(x(1), x(0))
    val g0 = r.nextGaussian(); val g1 = r.nextGaussian()
    val sign = if (x(0) * g1 - x(1) * g0 > 0) 1.0 else -1.0
    val level = f(x) + math.log(radius) + math.log(open(r))
    def point(rad: Double, angle: Double) = Vector(rad * math.cos(angle), rad * math.sin(angle))
    var angle = 2 * math.Pi * open(r)
    var lower = angle - 2 * math.Pi; var upper = angle
    var tries = 0
    def inside(rad: Double, phi: Double): Boolean = {
      tries += 1; require(tries < 10000)
      f(point(rad, phi)) + math.log(rad) > level
    }
    while (!inside(radius, theta + sign * angle)) {
      if (angle < 0) lower = angle else upper = angle
      angle = lower + open(r) * (upper - lower)
    }
    val phi = theta + sign * angle
    val offset = open(r)
    lower = math.max(0, radius - offset); upper = radius + 1 - offset
    while (lower > 0 && inside(lower, phi)) lower = math.max(0, lower - 1)
    while (inside(upper, phi)) upper += 1
    var rad = lower + open(r) * (upper - lower)
    while (!inside(rad, phi)) {
      if (rad < radius) lower = rad else upper = rad
      rad = lower + open(r) * (upper - lower)
    }
    point(rad, phi)
  }

  "The explicit vector sampler" should {
    "validate inputs and storage before calling the model" in {
      intercept[IllegalArgumentException](VS.Config(null))
      intercept[IllegalArgumentException](config(VS.Method.GPSS).copy(draws = 0))
      intercept[IllegalArgumentException](config(VS.Method.GPSS).copy(warmUp = -1))
      intercept[IllegalArgumentException](config(VS.Method.GPSS).copy(maxEvaluations = 0))
      intercept[IllegalArgumentException](config(VS.Method.GPSS).copy(maxSearch = 0))
      intercept[IllegalArgumentException](config(VS.Method.GPSS).copy(maxStoredValues = 0))
      var calls = 0
      val f: Vector[Double] => Double = x => { calls += 1; normal(x) }
      for (x <- Vector(Vector.empty[Double], Vector(0.0, 0.0), Vector(1.0), Vector(Double.NaN, 1.0), Vector(Double.MaxValue, Double.MaxValue)))
        intercept[IllegalArgumentException](VS.run(config(VS.Method.GPSS), x)(f))
      intercept[IllegalArgumentException](VS.run(config(VS.Method.GPSS).copy(maxStoredValues = 199), start)(f))
      calls shouldBe 0
      intercept[IllegalArgumentException](VS.run(null, start)(f))
      intercept[IllegalArgumentException](VS.run(config(VS.Method.GPSS), null)(f))
      intercept[IllegalArgumentException](VS.run(config(VS.Method.GPSS), start)(null))
    }

    "reject invalid densities and zero-support starts without masking callbacks" in {
      for (m <- VS.Method.values; bad <- Vector(Double.NaN, Double.PositiveInfinity, Double.NegativeInfinity))
        intercept[IllegalArgumentException](VS.run(config(m), start)(_ => bad))
      for (m <- VS.Method.values) {
        val sentinel = new RuntimeException("model failed")
        var calls = 0
        val caught = intercept[RuntimeException] {
          VS.run(config(m), start) { x => calls += 1; if (calls == 3) throw sentinel else normal(x) }
        }
        caught should be theSameInstanceAs sentinel
        calls shouldBe 3
        intercept[IllegalArgumentException] {
          var n = 0
          VS.run(config(m), start) { x => n += 1; if (n == 3) Double.NaN else normal(x) }
        }
      }
    }

    "produce detached reproducible complete traces and discard exact warm-up counts" in {
      for (m <- VS.Method.values) {
        val c = config(m)
        val a = VS.run(c, start)(normal)
        a shouldBe VS.run(c, start)(normal)
        a.samples.size shouldBe c.draws
        a.warmUpCompleted shouldBe c.warmUp
        a.reason shouldBe VS.StopReason.DrawsReached
        a.lastState shouldBe a.samples.last
        val all = VS.run(c.copy(warmUp = 0, draws = c.draws + c.warmUp), start)(normal)
        a.samples shouldBe all.samples.drop(c.warmUp)
        a.evaluations shouldBe all.evaluations
        a.samples should not be VS.run(c.copy(seed = 780), start)(normal).samples
        start shouldBe Vector(1.0, 0.5)
      }
    }

    "charge every callback and keep incomplete transitions out of capped traces" in {
      for (m <- VS.Method.values) {
        val c = config(m)
        val full = VS.run(c, start)(normal)
        for (budget <- Vector(1L, 2L, 3L, 20L, full.evaluations - 1, full.evaluations)) {
          var calls = 0L
          val cut = VS.run(c.copy(maxEvaluations = budget), start) { x => calls += 1; normal(x) }
          cut.evaluations shouldBe calls
          calls shouldBe budget
          full.samples.take(cut.samples.size) shouldBe cut.samples
          if (budget < full.evaluations) cut.reason shouldBe VS.StopReason.MaxEvaluationsReached
          else cut.reason shouldBe VS.StopReason.DrawsReached
        }
        val tiny = VS.run(c.copy(maxEvaluations = 1), start)(normal)
        tiny.samples shouldBe empty
        tiny.lastState shouldBe start
        tiny.warmUpCompleted shouldBe 0
      }
      // Flat quantile residual accepts the first coordinate, then stops mid-sweep.
      val target: Vector[Double] => Double = x => -x.map(v => math.log1p(v * v / 4)).sum
      val partial = VS.run(VS.Config(VS.Method.Quantile, draws = 1, warmUp = 0, maxEvaluations = 3), start)(target)
      partial.samples shouldBe empty
      partial.lastState shouldBe start
    }

    "fail closed on search exhaustion and floating-point boundary collapse" in {
      intercept[VS.SearchExhausted](VS.run(config(VS.Method.GPSS).copy(maxSearch = 1), start)(normal))
      intercept[VS.SearchExhausted] {
        VS.run(config(VS.Method.Quantile).copy(maxSearch = 1), start)(x => if (x == start) 0 else Double.NegativeInfinity)
      }
      intercept[IllegalArgumentException] {
        VS.run(config(VS.Method.Quantile), Vector(Double.MaxValue))(_ => 0)
      }
    }

    "preserve caller interruption before, during and after a callback" in {
      for (m <- VS.Method.values) {
        try {
          Thread.currentThread().interrupt()
          intercept[InterruptedException](VS.run(config(m), start)(_ => fail("Should not invoke callback")))
          Thread.currentThread().isInterrupted shouldBe true
        } finally Thread.interrupted()
        try {
          val thrown = new InterruptedException("callback interrupted")
          intercept[InterruptedException](VS.run(config(m), start)(_ => throw thrown)) should be theSameInstanceAs thrown
          Thread.currentThread().isInterrupted shouldBe true
        } finally Thread.interrupted()
        try {
          intercept[InterruptedException] {
            VS.run(config(m), start) { x => Thread.currentThread().interrupt(); normal(x) }
          }
          Thread.currentThread().isInterrupted shouldBe true
        } finally Thread.interrupted()
      }
    }

    "isolate simultaneous and nested runs without global RNG or worker ownership" in {
      val pool = Executors.newFixedThreadPool(2)
      try {
        val configs = VS.Method.values.toVector.map(config)
        val expected = configs.map(c => VS.run(c, start)(normal))
        val futures = configs.map(c => pool.submit(new Callable[VS.Result] {
          def call(): VS.Result = VS.run(c, start)(normal)
        }))
        futures.map(_.get(10, TimeUnit.SECONDS)) shouldBe expected
        var nested = false
        val result = VS.run(configs.head, start) { x =>
          if (!nested) { nested = true; VS.run(configs.last, start)(normal) shouldBe expected.last }
          normal(x)
        }
        result shouldBe expected.head
      } finally {
        pool.shutdownNow()
        pool.awaitTermination(10, TimeUnit.SECONDS) shouldBe true
      }
    }

    "allow another thread to cancel a cooperatively blocked density callback" in {
      for (m <- VS.Method.values) {
        val entered = new CountDownLatch(1)
        val block = new CountDownLatch(1)
        val failure = new AtomicReference[Throwable]()
        val flag = new AtomicReference[Boolean](false)
        val thread = new Thread(new Runnable {
          def run(): Unit = {
            try VS.run(config(m), start) { x => entered.countDown(); block.await(); normal(x) }
            catch { case e: Throwable => failure.set(e); flag.set(Thread.currentThread().isInterrupted) }
          }
        }, "vector-slice-cancellation-test")
        thread.setDaemon(true)
        thread.start()
        try {
          entered.await(5, TimeUnit.SECONDS) shouldBe true
          thread.interrupt()
          thread.join(5000)
          thread.isAlive shouldBe false
          failure.get() shouldBe a[InterruptedException]
          flag.get() shouldBe true
        } finally {
          block.countDown()
          thread.interrupt()
          thread.join(5000)
        }
      }
    }

    "match an independent planar angular implementation on nonradial targets" in {
      val targets = Vector(normal,
        (x: Vector[Double]) => -0.5 * (x(0) * x(0) + math.pow(x(1) - 0.4 * (x(0) * x(0) - 1), 2) / 0.25),
        (x: Vector[Double]) => -0.5 * (x(0) * x(0) - 1.8 * x(0) * x(1) + x(1) * x(1)) / 0.19)
      for (target <- targets; seed <- 1L to 5L) {
        val r = new java.util.Random(seed)
        var state = start
        val actual = VS.run(VS.Config(VS.Method.GPSS, draws = 100, warmUp = 0, seed = seed), start)(target)
        for (point <- actual.samples) {
          state = planar(state, r, target)
          point.zip(state).foreach((a, b) => a shouldBe b +- 1e-9)
        }
      }
    }

    "match independent inverse-CDF draws when the quantile target equals its reference" in {
      val reference = new CauchyDistribution(0, 2)
      val seed = 105L
      val rng = new java.util.Random(seed)
      val actual = VS.run(VS.Config(VS.Method.Quantile, draws = 500, warmUp = 0, seed = seed), Vector(0.0))(
        x => -math.log1p(x.head * x.head / 4))
      actual.samples.foreach { x =>
        open(rng) // Slice height; residual is exactly constant.
        x.head shouldBe reference.inverseCumulativeProbability(open(rng)) +- 1e-10
      }
      actual.evaluations shouldBe 1001L
    }

    "preserve independent Gaussian and constrained exponential target moments in 8 and 32 dimensions" in {
      for (m <- VS.Method.values; d <- Vector(8, 32); positive <- Vector(false, true)) {
        val rng = new java.util.Random(291L + d)
        var sums = Vector.fill(4)(0.0)
        val count = 4000
        for (i <- 0 until count) {
          val x = Vector.fill(d)(if (positive) -math.log(open(rng)) else rng.nextGaussian())
          val f: Vector[Double] => Double = if (positive) v => if (v.forall(_ > 0)) -v.sum else Double.NegativeInfinity else normal
          val result = VS.run(VS.Config(m, draws = 1, warmUp = 0, seed = rng.nextLong()), x)(f)
          result.reason shouldBe VS.StopReason.DrawsReached
          val y = result.samples.head
          if (positive) all(y) should be > 0.0
          val values = Vector(y.head, y.head * y.head, y.map(v => v * v).sum / d, y.head * y.last)
          sums = sums.zip(values).map(_ + _)
        }
        val expected = if (positive) Vector(1.0, 2.0, 2.0, 1.0) else Vector(0.0, 1.0, 1.0, 0.0)
        sums.zip(expected).foreach((sum, truth) => sum / count shouldBe truth +- 0.20)
      }
    }
  }
}
