package com.cra.figaro.test.modernization

import com.cra.figaro.algorithm.sampling.*
import com.cra.figaro.algorithm.sampling.parallel.{McmcPrecision, MultiChainMetropolisHastings as MH}
import com.cra.figaro.language.*
import com.cra.figaro.library.atomic.continuous.{AtomicNormal, Normal}
import com.cra.figaro.util.{random, withRandomSeed}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import scala.jdk.CollectionConverters.*

class GaussianBlockProposalRegressionTest extends AnyWordSpec with Matchers {
  private val eye = Vector(Vector(1.0, 0.0), Vector(0.0, 1.0))
  private def isolated(body: Universe => Unit): Unit = {
    val u = new Universe
    try body(u) finally u.clear()
  }
  private class Probe(u: Universe, scheme: ProposalScheme, targets: Element[?]*)
    extends OneTimeMetropolisHastings(u, 1, scheme, 0, 1, targets*) {
    def prime(): Unit = { doInitialize(); () }
    def resetConditions(): Unit = { dissatisfied = Set.empty; initConstrainedValues(); () }
    def proposal(): MetropolisHastings.State = proposeAndUpdate()
    def restore(s: MetropolisHastings.State): Unit = undo(s)
    def step(): Unit = { mhStep(); () }
  }
  private def correlated(u: Universe, index: Int): MH.Model = {
    val x = Normal(0, 1)(using "", u)
    val y = Normal(0, 1)(using "", u)
    val d = Apply(x, y, (a: Double, b: Double) => a - b)(using "", u)
    d.addLogConstraint((v: Double) => -0.5 * math.pow(v / 0.15, 2))
    val variance = 1.0225 / 2.0225
    val covariance = 1 / 2.0225
    val block = GaussianBlockProposal(Vector(x, y),
      Vector(Vector(2.8 * variance, 2.8 * covariance), Vector(2.8 * covariance, 2.8 * variance)))
    MH.Model(Vector(MH.Observable("x", x)(identity), MH.Observable("y", y)(identity)), Some(block))
  }

  "Gaussian block proposals" should {
    "reject invalid dimensions, duplicates, nonfinite, asymmetric and non-positive-definite covariance" in isolated { u =>
      val x = Normal(0, 1)(using "", u)
      val y = Normal(0, 1)(using "", u)
      intercept[IllegalArgumentException](GaussianBlockProposal(Vector.empty, Vector.empty))
      intercept[IllegalArgumentException](GaussianBlockProposal(null, eye))
      intercept[IllegalArgumentException](GaussianBlockProposal(Vector(x, x), eye))
      val invalid = Vector(null, Vector.empty, Vector(Vector(1.0)), Vector(Vector(0.0, 0.0), Vector(0.0, 1.0)),
        Vector(Vector(1.0, 1.0), Vector(1.0, 1.0)), Vector(Vector(1.0, 2.0), Vector(2.0, 1.0)),
        Vector(Vector(1.0, 0.1), Vector(0.2, 1.0)), Vector(Vector(Double.NaN, 0.0), Vector(0.0, 1.0)))
      invalid.foreach(c => intercept[IllegalArgumentException](GaussianBlockProposal(Vector(x, y), c)))
      // Covariance validation is relative to units, not an absolute positive-pivot tolerance.
      GaussianBlockProposal(Vector(x, y), Vector(Vector(1e-200, 0.0), Vector(0.0, 1e200)))
    }
    "reject foreign, observed, intervened, inactive, invalid and subclassed targets" in isolated { u =>
      val x = Normal(0, 1)(using "", u)
      isolated { other =>
        val y = Normal(0, 1)(using "", other)
        intercept[IllegalArgumentException](GaussianBlockProposal(Vector(x, y), eye))
      }
      val observed = Normal(0, 1)(using "", u); observed.observe(0.0)
      val intervened = Normal(0, 1)(using "", u); intervened.intervene(0.0)
      val invalid = Normal(0, 0)(using "", u)
      val subclass = new AtomicNormal("", 0, 1, u) {}
      val inactive = Normal(0, 1)(using "", u); inactive.deactivate()
      Vector(observed, intervened, invalid, subclass, inactive).foreach { e =>
        intercept[IllegalArgumentException](GaussianBlockProposal(Vector(e), Vector(Vector(1.0))))
      }
    }
    "match an independent value-space Cholesky and log-density-ratio oracle with unequal prior scales" in isolated { u =>
      val x = Normal(2, 4)(using "", u)
      val y = Normal(-3, 9)(using "", u)
      val p = new Probe(u, GaussianBlockProposal(Vector(x, y),
        Vector(Vector(1.0, 0.6), Vector(0.6, 4.0))), x, y)
      p.prime()
      x.randomness = 0.5; x.value = x.generateValue(0.5)
      y.randomness = -0.25; y.value = y.generateValue(-0.25)
      val old = Vector(x.value, y.value)
      val noises = withRandomSeed(778L) { random.nextDouble(); Vector(random.nextGaussian(), random.nextGaussian()) }
      val expected = Vector(old(0) + noises(0), old(1) + 0.6 * noises(0) + math.sqrt(3.64) * noises(1))
      val state = withRandomSeed(778L)(p.proposal())
      x.value shouldBe (expected(0) +- 1e-12)
      y.value shouldBe (expected(1) +- 1e-12)
      val priorRatio = -0.5 * (math.pow((expected(0) - 2) / 2, 2) - 0.25 +
        math.pow((expected(1) + 3) / 3, 2) - 0.0625)
      state.modelProb shouldBe (priorRatio +- 1e-12)
      state.proposalProb shouldBe 0.0
      p.restore(state)
      Vector(x.value, y.value) shouldBe old
      Vector(x.randomness, y.randomness) shouldBe Vector(0.5, -0.25)
    }
    "restore both randomness and values after early target-condition rejection" in isolated { u =>
      val x = Normal(0, 1)(using "", u)
      val y = Normal(0, 1)(using "", u)
      y.addCondition((v: Double) => v == 1.0)
      val p = new Probe(u, GaussianBlockProposal(Vector(x, y), eye), x, y)
      p.prime()
      x.randomness = 1; x.value = 1; y.randomness = 1; y.value = 1
      p.resetConditions()
      withRandomSeed(77L) {
        for (_ <- 0 until 100) {
          p.step()
          Vector(x.value, y.value, x.randomness, y.randomness) shouldBe Vector.fill(4)(1.0)
        }
      }
      p.acceptRejectRatio shouldBe 0.0
    }
    "restore block targets and descendants after joint evidence rejection" in isolated { u =>
      val x = Normal(0, 1)(using "", u)
      val y = Normal(0, 1)(using "", u)
      val sum = Apply(x, y, (a: Double, b: Double) => a + b)(using "", u)
      sum.addCondition((v: Double) => v == 2.0)
      val p = new Probe(u, GaussianBlockProposal(Vector(x, y), eye), x, y)
      p.prime()
      x.randomness = 1; x.value = 1; y.randomness = 1; y.value = 1; sum.value = 2
      p.resetConditions()
      withRandomSeed(78L) { for (_ <- 0 until 100) {
        p.step()
        Vector(x.value, y.value, x.randomness, y.randomness, sum.value) shouldBe Vector(1.0, 1.0, 1.0, 1.0, 2.0)
      } }
    }
    "recover conjugate moments without incorrectly cancelling the prior" in {
      val result = MH.run(MH.Config(drawsPerChain = 20000, warmUp = 2000, seed = 311L)) { (u, _) =>
        val x = Normal(2, 4)(using "", u)
        val y = Normal(-3, 9)(using "", u)
        x.addLogConstraint((v: Double) => -0.5 * math.pow(v - 4, 2))
        val scheme = GaussianBlockProposal(Vector(x, y), Vector(Vector(1.0, 0.6), Vector(0.6, 4.0)))
        MH.Model(Vector(MH.Observable("x", x)(identity), MH.Observable("y", y)(identity)), Some(scheme))
      }
      result.diagnostics("x").mean shouldBe (3.6 +- 0.07)
      result.diagnostics("x").standardDeviation shouldBe (math.sqrt(0.8) +- 0.07)
      result.diagnostics("y").mean shouldBe (-3.0 +- 0.16)
      result.diagnostics("y").standardDeviation shouldBe (3.0 +- 0.16)
      result.chains.foreach(c => c.acceptanceRate should (be > 0.1 and be < 0.9))
    }
    "recover correlated moments and reach precision" in {
      val config = MH.Config(drawsPerChain = 12000, warmUp = 2000, seed = 891L)
      val policy = McmcPrecision.Config(relativeTolerance = 0.15, minDrawsPerChain = 2000, checkEvery = 2000)
      val parallel = MH.runUntilPrecise(config, policy)(correlated)
      parallel.reason shouldBe MH.StopReason.PrecisionReached
      parallel.result.diagnostics.values.foreach { d =>
        d.mean shouldBe (0.0 +- 0.08)
        d.standardDeviation shouldBe (math.sqrt(1.0225 / 2.0225) +- 0.08)
      }
    }
    "preserve exact worker-count and fixed/adaptive prefixes on a deterministic initialization fixture" in {
      def build(u: Universe, index: Int): MH.Model = {
        val x = Normal(0, 1)(using "", u)
        MH.Model(Vector(MH.Observable("x", x)(identity)),
          Some(GaussianBlockProposal(Vector(x), Vector(Vector(2.0)))))
      }
      val config = MH.Config(drawsPerChain = 12000, warmUp = 2000, seed = 892L)
      val policy = McmcPrecision.Config(relativeTolerance = 0.15, minDrawsPerChain = 2000, checkEvery = 2000)
      val serial = MH.runUntilPrecise(config.copy(parallelism = 1), policy)(build)
      val parallel = MH.runUntilPrecise(config, policy)(build)
      assert(serial.result.chains.map(_.draws) == parallel.result.chains.map(_.draws), "Worker count changed block traces")
      val fixed = MH.run(config)(build)
      parallel.result.chains.zip(fixed.chains).foreach { (a, b) =>
        a.draws.foreach((key, values) => assert(values == b.draws(key).take(values.size), "Adaptive prefix changed"))
      }
      val first = fixed.chains.head
      val ordinary = withRandomSeed(first.seed) {
        val u = new Universe
        val x = Normal(0, 1)(using "", u)
        val block = GaussianBlockProposal(Vector(x), Vector(Vector(2.0)))
        val trace = Vector.newBuilder[Double]
        val sampler = new OneTimeMetropolisHastings(u, config.drawsPerChain, block, config.warmUp, 1, x) {
          override def sample(): (Boolean, Sample) = {
            val result = super.sample()
            if (result._1) trace += x.value
            result
          }
        }
        try { sampler.start(); trace.result() }
        finally { if (sampler.isActive) sampler.kill(); u.clear() }
      }
      assert(ordinary == first.draws("x"), "Ordinary and multi-chain block kernels diverged")
    }
    "allow state-independent mixtures so variables outside the block are not frozen" in {
      val result = MH.run(MH.Config(drawsPerChain = 10000, seed = 58L)) { (u, _) =>
        val x = Normal(0, 1)(using "", u)
        val coin = Flip(0.3)(using "", u)
        val block = GaussianBlockProposal(Vector(x), Vector(Vector(1.0)))
        val scheme = DisjointScheme(0.7 -> (() => block), 0.3 -> (() => ProposalScheme.default(using u)))
        MH.Model(Vector(MH.Observable("x", x)(identity), MH.Observable("p", coin)(b => if (b) 1.0 else 0.0)), Some(scheme))
      }
      result.diagnostics("x").mean shouldBe (0.0 +- 0.08)
      result.diagnostics("p").mean shouldBe (0.3 +- 0.04)
    }
    "reject unsafe execution and clean up workers on callback failure" in isolated { u =>
      val x = Normal(0, 1)(using "", u)
      val foreign = GaussianBlockProposal(Vector(x), Vector(Vector(1.0)))
      intercept[MH.ChainFailure] {
        MH.run(MH.Config(chains = 2, drawsPerChain = 10)) { (own, _) =>
          val y = Normal(0, 1)(using "", own)
          MH.Model(Vector(MH.Observable("y", y)(identity)), Some(foreign))
        }
      }
      val local = new Probe(u, foreign, x)
      local.prime()
      local.constraintsBound = true
      intercept[IllegalArgumentException](local.proposal())
      local.constraintsBound = false
      x.observe(0.0)
      intercept[IllegalArgumentException](local.proposal())
      Thread.getAllStackTraces.keySet().asScala.exists(_.getName.startsWith("figaro-mcmc-worker-")) shouldBe false
    }
    "reject sequential continuation instead of silently changing joint-proposal semantics" in isolated { u =>
      val x = Normal(0, 1)(using "", u)
      val y = Normal(0, 1)(using "", u)
      val block = GaussianBlockProposal(Vector(y), Vector(Vector(1.0)))
      val p = new Probe(u, UntypedScheme(() => x, Some(block)), x, y)
      p.prime()
      intercept[IllegalArgumentException](p.proposal())
    }
  }
}
