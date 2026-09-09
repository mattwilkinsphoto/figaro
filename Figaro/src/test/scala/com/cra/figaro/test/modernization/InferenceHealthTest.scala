package com.cra.figaro.test.modernization

import com.cra.figaro.algorithm.sampling.{InferenceHealth as H, ParetoTail as P, Importance}
import com.cra.figaro.algorithm.sampling.parallel.McmcDiagnostics
import com.cra.figaro.library.atomic.continuous.Normal
import com.cra.figaro.language.Universe
import com.cra.figaro.util.{SamplingRandom, withRandomSeed}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class InferenceHealthTest extends AnyWordSpec with Matchers {
  private def fixture(shape: Double, n: Int = 2000): Vector[Double] = Vector.tabulate(n) { i =>
    val p = (i + 0.5)/n
    val x = if (shape == 0) -math.log1p(-p) else math.expm1(-shape*math.log1p(-p))/shape
    math.log1p(x)
  }
  private def healthyLogs = Vector.tabulate(2000)(i => math.log(1 + (i + 0.5)/2000))
  private def chains: Vector[Vector[Double]] = Vector.tabulate(4) { i =>
    val r = SamplingRandom.seeded(991L + i)
    Vector.fill(2000)(r.nextGaussian())
  }

  "Inference health" should {
    "match unchanged ArviZ v0.22.0 NumPy reference fits across bounded and heavy tails" in {
      val references = Vector(
        (-.25, -.18098173657398042, .11153861525632346),
        (0.0, .04591366679702962, .10643152086262633),
        (.3, .31712924231012535, .05920291277745888),
        (.7, .6786439979721525, .014020195775330434),
        (1.2, 1.1305491440927482, .0014809784418866791))
      references.foreach { (shape, k, scale) =>
        val result = P.fit(fixture(shape))
        result.status shouldBe P.Status.Estimated
        result.k.get shouldBe (k +- 2e-11)
        result.scale.get shouldBe (scale +- 2e-11)
        result.tailSize shouldBe 135
        result.threshold.get shouldBe (1 - 1/math.log10(2000) +- 1e-14)
      }
    }
    "preserve permutation and log-shift invariance without mutating inputs" in {
      val logs = fixture(.3)
      val values = logs.map(math.sin)
      val original = H.importance(logs, true, Some(values))
      for (shift <- Vector(-10000.0, 10000.0)) {
        val other = H.importance(logs.reverse.map(_ + shift), true, Some(values.reverse))
        other.status shouldBe original.status
        other.diagnostics.ess.get shouldBe (original.diagnostics.ess.get +- 1e-7)
        other.diagnostics.mean.get shouldBe (original.diagnostics.mean.get +- 1e-11)
        other.diagnostics.rawMcse.get shouldBe (original.diagnostics.rawMcse.get +- 1e-11)
        other.diagnostics.pareto.get.k.get shouldBe (original.diagnostics.pareto.get.k.get +- 1e-8)
      }
      logs shouldBe fixture(.3)
    }
    "retain exact concentration diagnostics and expose zero-error weight collapse" in {
      val r = H.importance(Vector(0.0, Double.NegativeInfinity), true, Some(Vector(2.0, 999.0)))
      r.status shouldBe H.Status.Danger
      r.diagnostics.ess shouldBe Some(1.0)
      r.diagnostics.maxWeight shouldBe Some(1.0)
      r.diagnostics.mean shouldBe Some(2.0)
      r.diagnostics.rawMcse shouldBe Some(0.0)
      r.issues.map(_.code) should contain (H.Code.ConstantObservable)
      H.importance(Vector.fill(100)(Double.NegativeInfinity), true).status shouldBe H.Status.Danger
      H.importance(Vector.empty, true).status shouldBe H.Status.InsufficientEvidence
    }
    "distinguish passed checks, warnings, danger and unavailable tail evidence" in {
      H.importance(healthyLogs, true).status shouldBe H.Status.ChecksPassed
      val strict = H.Config(minEss=3000)
      H.importance(healthyLogs, true, config=strict).status shouldBe H.Status.Warning
      H.importance(fixture(1.2), true).issues.map(_.code) should contain (H.Code.ParetoDanger)
      H.importance(fixture(1.2), true).status shouldBe H.Status.Danger
      H.importance(Vector.fill(2000)(0.0), true).status shouldBe H.Status.InsufficientEvidence
      H.importance(healthyLogs.take(20), true).issues.map(_.code) should contain (H.Code.TooFewSamples)
      val dependent = H.importance(healthyLogs, false, Some(healthyLogs))
      dependent.status shouldBe H.Status.InsufficientEvidence
      dependent.diagnostics.pareto shouldBe None
      dependent.diagnostics.rawMcse shouldBe None
    }
    "make precision query-specific and never pass an all-zero event indicator" in {
      val r = H.importance(healthyLogs, true, Some(Vector.fill(2000)(0.0)), H.Config(maxMeanMcse=Some(.01)))
      r.status shouldBe H.Status.InsufficientEvidence
      r.precisionRequested shouldBe true
      val strict = H.importance(healthyLogs, true, Some(healthyLogs), H.Config(maxMeanMcse=Some(1e-10)))
      strict.issues.map(_.code) should contain (H.Code.PrecisionNotMet)
      strict.status shouldBe H.Status.Warning
    }
    "reject invalid inputs and bounded-work violations without producing partial reports" in {
      for (bad <- Vector(Double.NaN, Double.PositiveInfinity)) {
        intercept[IllegalArgumentException](H.importance(Vector(bad), true))
        intercept[IllegalArgumentException](P.fit(Vector(bad)))
      }
      intercept[IllegalArgumentException](H.importance(null, true))
      intercept[IllegalArgumentException](H.importance(Vector(0.0), true, Some(Vector.empty)))
      intercept[IllegalArgumentException](H.importance(Vector(0.0), true, Some(Vector(Double.NaN))))
      intercept[IllegalArgumentException](H.importance(healthyLogs, true, config=H.Config(maxSamples=100)))
      intercept[IllegalArgumentException](P.fit(healthyLogs, 10))
      intercept[IllegalArgumentException](H.importance(healthyLogs, true, config=H.Config(maxMeanMcse=Some(.01))))
      intercept[IllegalArgumentException](H.Config(minEss=Double.NaN))
      intercept[IllegalArgumentException](H.Config(warnWeight=.6, dangerWeight=.5))
      intercept[IllegalArgumentException](H.mcmc(Vector(Vector(1.0), Vector(1.0, 2.0))))
      intercept[IllegalArgumentException](H.mcmc(Vector(Vector(Double.NaN))))
      intercept[IllegalArgumentException](H.mcmc(chains, H.Config(maxSamples=100)))
    }
    "reuse existing MCMC diagnostics and flag separated, short and constant traces" in {
      val x = chains
      val r = H.mcmc(x)
      r.diagnostics shouldBe Some(McmcDiagnostics.summarize(x))
      r.status shouldBe H.Status.ChecksPassed
      H.mcmc(x, H.Config(maxMeanMcse=Some(1e-10))).issues.map(_.code) should contain (H.Code.PrecisionNotMet)
      val separated = x.zipWithIndex.map((v, i) => v.map(_ + 10*i))
      H.mcmc(separated).status shouldBe H.Status.Danger
      H.mcmc(Vector.fill(4)(Vector.fill(2000)(0.0))).status shouldBe H.Status.InsufficientEvidence
      H.mcmc(Vector.empty).status shouldBe H.Status.InsufficientEvidence
      H.mcmc(x.take(1)).status shouldBe H.Status.InsufficientEvidence
      H.mcmc(x.map(_.take(4))).status should not be H.Status.ChecksPassed
    }
    "match the unchanged Importance estimate and preserve caller models on success and failure" in {
      class TrackedUniverse extends Universe {
        var owned = Set.empty[com.cra.figaro.algorithm.Algorithm]
        override def registerAlgorithm(a: com.cra.figaro.algorithm.Algorithm): Unit = {
          super.registerAlgorithm(a); owned += a
        }
        override def deregisterAlgorithm(a: com.cra.figaro.algorithm.Algorithm): Unit = {
          super.deregisterAlgorithm(a); owned -= a
        }
      }
      val u = new TrackedUniverse
      val target = Normal(0, 1)(using "x", u)
      Normal(target, 1.0)(using "observed", u).observe(.5)
      try {
        val expected = withRandomSeed(42L) {
          val algorithm = Importance(200, target)(using u)
          try { algorithm.start(); algorithm.mean(target) } finally algorithm.kill()
        }
        val report = withRandomSeed(42L) { H.runImportance(200, target, (x: Double) => x) }
        report.diagnostics.samples shouldBe 200
        report.diagnostics.mean.get shouldBe (expected +- 1e-12)
        target.active shouldBe true
        u.owned shouldBe empty
        val failure = new IllegalStateException("projection failure")
        intercept[IllegalStateException](H.runImportance(200, target, (_: Double) => throw failure)) shouldBe failure
        target.active shouldBe true
        u.owned shouldBe empty
        Thread.currentThread().interrupt()
        try intercept[InterruptedException](H.runImportance(200, target, (x: Double) => x))
        finally Thread.interrupted()
        u.owned shouldBe empty
      } finally u.clear()
    }
    "preserve interruption flags and support concurrent independent reports" in {
      Thread.currentThread().interrupt()
      try {
        intercept[InterruptedException](P.fit(healthyLogs))
        intercept[InterruptedException](H.importance(healthyLogs, true))
        intercept[InterruptedException](H.mcmc(chains))
        Thread.currentThread().isInterrupted shouldBe true
      } finally Thread.interrupted()
      val pool = java.util.concurrent.Executors.newFixedThreadPool(2)
      try {
        val jobs = Vector.fill(4)(pool.submit(new java.util.concurrent.Callable[H.ImportanceReport] {
          def call() = H.importance(healthyLogs, true)
        }))
        jobs.map(_.get()).distinct.size shouldBe 1
      } finally { pool.shutdownNow(); pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS) }
    }
  }
}
