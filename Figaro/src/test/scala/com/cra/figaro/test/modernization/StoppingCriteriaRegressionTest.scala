package com.cra.figaro.test.modernization

import com.cra.figaro.algorithm.sampling.parallel.{TruncatedSprt, McmcPrecision, MultiChainMetropolisHastings as MH}
import com.cra.figaro.algorithm.sampling.ProposalScheme
import com.cra.figaro.language.*
import com.cra.figaro.library.atomic.continuous.Normal
import org.apache.commons.math3.distribution.NormalDistribution
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import java.util.concurrent.ConcurrentLinkedQueue
import scala.jdk.CollectionConverters.*

class StoppingCriteriaRegressionTest extends AnyWordSpec with Matchers {
  private val normal = new NormalDistribution(0, 1)
  private def gaussianTraces(n: Int, seed: Long = 10L): Vector[Vector[Double]] = {
    val rng = new java.util.Random(seed)
    Vector.fill(4)(Vector.fill(n)(rng.nextGaussian()))
  }
  private def model(u: Universe, index: Int): MH.Model = {
    val x = Normal(0, 1)(using "", u)
    MH.Model(Vector(MH.Observable("x", x)(identity)))
  }
  private val budget = MH.Config(chains = 4, drawsPerChain = 2301, warmUp = 37, parallelism = 2, thin = 2)
  private val tight = McmcPrecision.Config(relativeTolerance = 1e-8, minDrawsPerChain = 400, checkEvery = 311)

  "Truncated SPRT" should {
    "allocate sequential error budgets with explicit miss probability, not detection power" in {
      val d = TruncatedSprt.gaussian(0, 1, 1)
      d.upperBoundary shouldBe (math.log(0.95 / 0.025) +- 1e-12)
      d.lowerBoundary shouldBe (math.log(0.05 / 0.975) +- 1e-12)
    }
    "match an independent Gaussian fixed-sample derivation after integer rounding" in {
      for ((gap, sd) <- Vector((0.25, 1.0), (1.0, 1.0), (2.0, 0.7), (20.0, 1.0))) {
        val d = TruncatedSprt.gaussian(-0.3, -0.3 + gap, sd)
        val z0 = normal.inverseCumulativeProbability(0.975)
        val z1 = normal.inverseCumulativeProbability(0.95)
        d.nominalSamples shouldBe (math.pow((z0 + z1) * sd / gap, 2) +- 1e-9)
        d.maxSamples shouldBe math.ceil(d.nominalSamples).toInt
        val separation = gap / sd
        val noise = math.sqrt(d.maxSamples.toDouble) * separation
        val drift = d.maxSamples * separation * separation / 2
        val falseAlarm = normal.cumulativeProbability(-(d.terminalBoundary + drift) / noise)
        val miss = normal.cumulativeProbability((d.terminalBoundary - drift) / noise)
        falseAlarm should be <= (0.025 + 1e-12)
        miss should be <= (0.05 + 1e-12)
        // Asymmetric errors exercise the threshold sign; a symmetric fixture would hide it.
        d.terminalBoundary should be > 0.0
      }
    }
    "require more samples for closer means and remain invariant to observation units" in {
      val d = TruncatedSprt.gaussian(0, 1, 1)
      TruncatedSprt.gaussian(0, 0.5, 1).nominalSamples shouldBe (4 * d.nominalSamples +- 1e-10)
      val scaled = TruncatedSprt.gaussian(10, 13, 3)
      scaled.nominalSamples shouldBe d.nominalSamples
      scaled.terminalBoundary shouldBe d.terminalBoundary
      scaled.advance(scaled.initial, 10.6).logLikelihoodRatio shouldBe (d.advance(d.initial, 0.2).logLikelihoodRatio +- 1e-12)
    }
    "select the correct boundary and leave immutable evidence unchanged" in {
      val d = TruncatedSprt.gaussian(0, 1, 1)
      val initial = d.initial
      val high = d.advance(initial, 100)
      high.decision shouldBe TruncatedSprt.Decision.AcceptH1
      d.advance(initial, -100).decision shouldBe TruncatedSprt.Decision.AcceptH0
      initial.samples shouldBe 0
      intercept[IllegalArgumentException](d.advance(high, 0))
      intercept[IllegalArgumentException](d.advance(TruncatedSprt.gaussian(0, 1, 1).initial, 0))
    }
    "use the terminal rule at the integer limit, including the one-sample case" in {
      val d = TruncatedSprt.gaussian(0, 1, 1)
      var s = d.initial
      for (_ <- 1 until d.maxSamples) {
        s = d.advance(s, 0.5) // zero LLR increment
        s.decision shouldBe TruncatedSprt.Decision.Continue
      }
      s = d.advance(s, 0.5)
      s.atTruncation shouldBe true
      s.decision shouldBe TruncatedSprt.Decision.AcceptH0
      val one = TruncatedSprt.gaussian(0, 20, 1)
      one.maxSamples shouldBe 1
      one.advance(one.initial, 20).atTruncation shouldBe true
      one.advance(one.initial, 20).decision shouldBe TruncatedSprt.Decision.AcceptH1
    }
    "validate inputs and fail explicitly for unrepresentable designs and observations" in {
      intercept[IllegalArgumentException](TruncatedSprt.gaussian(1, 0, 1))
      intercept[IllegalArgumentException](TruncatedSprt.gaussian(0, 1, 0))
      intercept[IllegalArgumentException](TruncatedSprt.gaussian(0, 1, 1, missedDetectionRate = 0.9))
      intercept[IllegalArgumentException](TruncatedSprt.gaussian(0, 1, 1, terminalMissFraction = 1))
      intercept[IllegalArgumentException](TruncatedSprt.gaussian(0, 1e-100, 1))
      intercept[IllegalArgumentException](TruncatedSprt.gaussian(0, 1, Double.NaN))
      val d = TruncatedSprt.gaussian(0, 1, 1)
      intercept[IllegalArgumentException](d.advance(d.initial, Double.PositiveInfinity))
    }
    "stay within nominal risks in seeded repeated independent Gaussian experiments" in {
      val rng = new java.util.Random(8193L)
      val repetitions = 10000
      for (gap <- Vector(0.25, 1.0, 2.0)) {
        val d = TruncatedSprt.gaussian(0, gap, 1)
        for ((mean, wrong, bound) <- Vector((0.0, TruncatedSprt.Decision.AcceptH1, 0.05),
          (gap, TruncatedSprt.Decision.AcceptH0, 0.10))) {
          var errors = 0
          var work = 0L
          for (_ <- 0 until repetitions) {
            var state = d.initial
            while (state.decision == TruncatedSprt.Decision.Continue) state = d.advance(state, mean + rng.nextGaussian())
            if (state.decision == wrong) errors += 1
            work += state.samples
          }
          info(s"Gaussian gap=$gap mean=$mean: errors=$errors/$repetitions, mean samples=${work.toDouble / repetitions}, cap=${d.maxSamples}")
          errors.toDouble / repetitions should be <= (bound + 0.01)
          work should be <= repetitions.toLong * d.maxSamples
        }
      }
    }
    "calculate directional KL with explicit zero-support behavior and validate normalization" in {
      TruncatedSprt.klDivergence(Vector(1, 0), Vector(0.5, 0.5)) shouldBe (math.log(2) +- 1e-14)
      TruncatedSprt.klDivergence(Vector(0.5, 0.5), Vector(1, 0)) shouldBe Double.PositiveInfinity
      TruncatedSprt.klDivergence(Vector(0, 1), Vector(0, 1)) shouldBe 0.0
      intercept[IllegalArgumentException](TruncatedSprt.klDivergence(Vector(0.1), Vector(1)))
      intercept[IllegalArgumentException](TruncatedSprt.klDivergence(Vector(Double.NaN), Vector(1)))
      intercept[IllegalArgumentException](TruncatedSprt.klDivergence(Vector(1), Vector(0, 1)))
    }
  }

  "MCMC precision" should {
    "distinguish full-width targets, simultaneous confidence, and absolute precision" in {
      val traces = gaussianTraces(4000)
      val loose = McmcPrecision.Config(relativeTolerance = 0.2)
      val a = McmcPrecision.evaluate(traces, loose)
      a.criteriaMet shouldBe true
      a.fullWidth.get shouldBe (2 * normal.inverseCumulativeProbability(0.975) * a.mcseUsed.get +- 1e-12)
      McmcPrecision.evaluate(traces, loose, 5).fullWidth.get should be > a.fullWidth.get
      McmcPrecision.evaluate(traces, tight).criteriaMet shouldBe false
      McmcPrecision.evaluate(traces, loose.copy(absoluteTolerance = Some(1e-8))).criteriaMet shouldBe false
      a.targetWidth shouldBe (0.2 * a.diagnostics.standardDeviation +- 1e-12)
    }
    "refuse short, constant, and separated-chain evidence even with loose precision" in {
      val loose = McmcPrecision.Config(relativeTolerance = 100)
      McmcPrecision.evaluate(gaussianTraces(100), loose).criteriaMet shouldBe false
      McmcPrecision.evaluate(Vector.fill(4)(Vector.fill(1000)(2.0)), loose).criteriaMet shouldBe false
      val separated = gaussianTraces(2000).zipWithIndex.map((v, i) => v.map(_ + i * 100))
      McmcPrecision.evaluate(separated, loose).criteriaMet shouldBe false
    }
    "account for serial correlation in batch-means error estimates" in {
      val innovations = gaussianTraces(10000)
      val correlated = innovations.map { values =>
        var state = 0.0
        values.map { value => state = 0.95 * state + math.sqrt(1 - 0.95 * 0.95) * value; state }
      }
      val iid = McmcPrecision.evaluate(innovations, tight)
      val ar = McmcPrecision.evaluate(correlated, tight)
      ar.batchMeansMcse.get should be > (3 * iid.batchMeansMcse.get)
    }
    "preserve units and reject invalid policies" in {
      val traces = gaussianTraces(2000)
      val a = McmcPrecision.evaluate(traces, tight)
      val b = McmcPrecision.evaluate(traces.map(_.map(_ * 8)), tight)
      b.batchMeansMcse.get shouldBe (8 * a.batchMeansMcse.get +- 1e-12)
      b.targetWidth shouldBe (8 * a.targetWidth +- 1e-12)
      intercept[IllegalArgumentException](McmcPrecision.Config(checkEvery = 0))
      intercept[IllegalArgumentException](McmcPrecision.Config(confidence = 1))
      intercept[IllegalArgumentException](McmcPrecision.Config(absoluteTolerance = Some(Double.NaN)))
    }
    "check stopped-interval coverage on seeded stationary independent and correlated Gaussian chains" in {
      val repeats = 100
      val policy = McmcPrecision.Config(relativeTolerance = 0.15, minDrawsPerChain = 1000, checkEvery = 500)
      for (rho <- Vector(0.0, 0.9)) {
        val rng = new java.util.Random(72109L)
        var covered = 0
        var reached = 0
        var retained = 0L
        for (_ <- 0 until repeats) {
          val traces = Vector.fill(4) {
            var x = rng.nextGaussian() // stationary start for this validation fixture only
            Vector.fill(8000) { x = rho * x + math.sqrt(1 - rho * rho) * rng.nextGaussian(); x }
          }
          var count = policy.minDrawsPerChain
          var assessment = McmcPrecision.evaluate(traces.map(_.take(count)), policy)
          while (!assessment.criteriaMet && count < 8000) {
            count += policy.checkEvery
            assessment = McmcPrecision.evaluate(traces.map(_.take(count)), policy)
          }
          if (assessment.criteriaMet) reached += 1
          if (math.abs(assessment.diagnostics.mean) <= assessment.fullWidth.get / 2) covered += 1
          retained += count
        }
        info(s"Stopped Gaussian rho=$rho: covered=$covered/$repeats, reached=$reached/$repeats, mean draws/chain=${retained.toDouble / repeats}")
        // Broad finite-experiment regression bounds, not a claim of exact nominal coverage.
        covered should be >= 85
        reached should be >= 90
      }
    }
  }

  "Adaptive multi-chain MH" should {
    "exhaust an insufficient budget without changing fixed-run traces, acceptance, or initialization" in {
      val fixed = MH.run(budget)(model)
      val adaptive = MH.runUntilPrecise(budget, tight)(model)
      adaptive.reason shouldBe MH.StopReason.MaxDrawsReached
      adaptive.checks should be > 1
      adaptive.result.chains.map(_.draws) shouldBe fixed.chains.map(_.draws)
      adaptive.result.chains.map(_.acceptanceRate) shouldBe fixed.chains.map(_.acceptanceRate)
      adaptive.result.chains.map(_.initializationAttempts) shouldBe fixed.chains.map(_.initializationAttempts)
    }
    "stop early at identical prefixes across worker counts without restarting or repeating warm-up" in {
      val policy = McmcPrecision.Config(relativeTolerance = 0.3, minDrawsPerChain = 1000, checkEvery = 500)
      val config = budget.copy(drawsPerChain = 6000)
      val a = MH.runUntilPrecise(config.copy(parallelism = 1), policy)(model)
      val b = MH.runUntilPrecise(config.copy(parallelism = 4), policy)(model)
      a.reason shouldBe MH.StopReason.PrecisionReached
      a.result.chains.head.draws("x").size should be < config.drawsPerChain
      a.result.chains.map(_.draws) shouldBe b.result.chains.map(_.draws)
      a.assessments shouldBe b.assessments
      val fixed = MH.run(config)(model)
      a.result.chains.zip(fixed.chains).foreach { (short, long) =>
        short.draws("x") shouldBe long.draws("x").take(short.draws("x").size)
      }
    }
    "require all observables and clean models after a later-batch failure" in {
      val universes = new ConcurrentLinkedQueue[Universe]
      val mixed = MH.runUntilPrecise(budget, tight.copy(relativeTolerance = 1)) { (u, _) =>
        val x = Normal(0, 1)(using "", u)
        val constant = Constant(1.0)(using "", u)
        MH.Model(Vector(MH.Observable("x", x)(identity), MH.Observable("constant", constant)(identity)))
      }
      mixed.reason shouldBe MH.StopReason.MaxDrawsReached
      intercept[MH.ChainFailure] {
        MH.runUntilPrecise(budget, tight) { (u, index) =>
          universes.add(u)
          val x = Normal(0, 1)(using "", u)
          var read = 0
          MH.Model(Vector(MH.Observable("x", x) { value =>
            read += 1
            if (index == 0 && read == 500) throw new IllegalStateException("second batch failure")
            value
          }))
        }
      }
      universes.asScala.foreach(_.activeElements shouldBe empty)
      Thread.getAllStackTraces.keySet().asScala.filter(_.getName.startsWith("figaro-mcmc-worker-")).toVector shouldBe empty
    }
    "refuse precision success for chains trapped in distinct modes" in {
      val result = MH.runUntilPrecise(budget.copy(chains = 2), tight.copy(relativeTolerance = 100)) { (u, index) =>
        val mode = Flip(0.5)(using "", u)
        val noise = Normal(0, 1)(using "", u)
        val x = Apply(mode, noise, (b: Boolean, v: Double) => v + (if (b) 10 else -10))(using "", u)
        MH.Model(Vector(MH.Observable("x", x)(identity)), Some(ProposalScheme(noise)), () => mode.value == (index == 0))
      }
      result.reason shouldBe MH.StopReason.MaxDrawsReached
      result.assessments("x").diagnostics.rHat.get should be > 1.1
    }
  }
}
