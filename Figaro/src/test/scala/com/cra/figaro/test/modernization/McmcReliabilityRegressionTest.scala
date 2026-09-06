package com.cra.figaro.test.modernization

import com.cra.figaro.algorithm.sampling.parallel.McmcPrecision
import com.cra.figaro.algorithm.sampling.parallel.{MultiChainMetropolisHastings as MH}
import com.cra.figaro.algorithm.sampling.{OneTimeMetropolisHastings, MetropolisHastings, ProposalScheme}
import com.cra.figaro.language.*
import com.cra.figaro.library.atomic.continuous.Normal
import com.cra.figaro.util.withRandomSeed
import com.cra.figaro.algorithm.sampling.parallel.McmcPrecision.FailureReason
import org.apache.commons.math3.distribution.NormalDistribution
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class McmcReliabilityRegressionTest extends AnyWordSpec with Matchers {
  private def traces(n: Int, rho: Double = 0, seed: Long = 7311L): Vector[Vector[Double]] = {
    val rng = new java.util.Random(seed)
    Vector.fill(4) {
      var x = rng.nextGaussian()
      Vector.fill(n) { x = rho * x + math.sqrt(1 - rho * rho) * rng.nextGaussian(); x }
    }
  }
  private val critical = -new NormalDistribution(0, 1).inverseCumulativeProbability(0.025)
  private val loose = McmcPrecision.Config(relativeTolerance = 100)

  "MCMC reliability safeguards" should {
    "match an independent target/proposal density ratio in the curved target's difficult tail" in {
      val u = new Universe
      try {
        val x = Normal(0, 1)(using "", u); val y = Normal(0, 1)(using "", u)
        val pair = Inject(x, y)(using "", u)
        pair.addLogConstraint((v: List[Double]) => 0.5 * (v(1) * v(1) -
          math.pow((v(1) - 0.4 * (v.head * v.head - 1)) / 0.5, 2)))
        class Probe extends OneTimeMetropolisHastings(u, 1, ProposalScheme(x, y), 0, 1, x, y) {
          def prime(): Unit = { doInitialize(); () }
          def scoreState(): Unit = { dissatisfied = Set.empty; initConstrainedValues(); () }
          def propose(): (MetropolisHastings.State, Double) = {
            val state = proposeAndUpdate()
            (state, state.modelProb + state.proposalProb + computeScores())
          }
          def restore(state: MetropolisHastings.State): Unit = undo(state)
        }
        val p = new Probe
        p.prime()
        x.randomness = 4; x.value = 4; y.randomness = 6; y.value = 6; pair.value = List(4, 6)
        p.scoreState()
        val (state, ratio) = withRandomSeed(915L)(p.propose())
        def logTarget(a: Double, b: Double): Double = -0.5 * a * a - 2 * math.pow(b - 0.4 * (a * a - 1), 2)
        def logPrior(a: Double, b: Double): Double = -0.5 * (a * a + b * b)
        val expected = logTarget(x.value, y.value) - logTarget(4, 6) + logPrior(4, 6) - logPrior(x.value, y.value)
        ratio shouldBe (expected +- 1e-10)
        // Moving from this tail ridge to the central ridge is intrinsically hard for this proposal.
        val centerRatio = logTarget(0, -0.4) - logTarget(4, 6) + logPrior(4, 6) - logPrior(0, -0.4)
        centerRatio shouldBe (-17.92 +- 1e-12)
        p.restore(state)
        x.value shouldBe 4.0; y.value shouldBe 6.0; pair.value shouldBe List(4.0, 6.0)
      } finally u.clear()
    }
    "recover the same curved target through independent latent-coordinate proposals" in {
      val r = MH.run(MH.Config(drawsPerChain = 20000, warmUp = 0, seed = 81017)) { (u, _) =>
        val z = Normal(0, 1)(using "", u); val e = Normal(0, 1)(using "", u)
        val y = Apply(z, e, (a: Double, b: Double) => 0.4 * (a * a - 1) + 0.5 * b)(using "", u)
        MH.Model(Vector(MH.Observable("x2", z)(v => v * v), MH.Observable("y", y)(identity),
          MH.Observable("y2", y)(v => v * v), MH.Observable("tail", z)(v => if (math.abs(v) > 2) 1.0 else 0.0)),
          Some(ProposalScheme(z, e)))
      }
      r.chains.foreach(_.acceptanceRate shouldBe 1.0)
      r.diagnostics("x2").mean shouldBe (1.0 +- 0.05)
      r.diagnostics("y").mean shouldBe (0.0 +- 0.04)
      r.diagnostics("y2").mean shouldBe (0.57 +- 0.05)
      r.diagnostics("tail").mean shouldBe (0.045500263896358334 +- 0.006)
    }
    "preserve batch-means estimation against an independent complete-batch arithmetic oracle" in {
      val data = traces(4097)
      val batchSize = math.sqrt(4097.0).toInt
      val batches = 4097 / batchSize
      val estimates = data.map { chain =>
        val means = chain.take(batches * batchSize).grouped(batchSize).map(v => v.sum / batchSize).toVector
        val average = means.sum / batches
        batchSize * means.map(v => math.pow(v - average, 2)).sum / (batches - 1)
      }
      val expected = math.sqrt(estimates.sum / (16 * 4097.0))
      val a = McmcPrecision.evaluate(data, loose)
      a.batchMeansMcse.get shouldBe (expected +- 1e-14)
      a.diagnostics.mean shouldBe (data.flatten.sum / (4 * 4097.0) +- 1e-14)
      a.batchesPerChain shouldBe batches
    }
    "refuse a width that batch means passes but the existing raw-mean MCSE contradicts" in {
      val data = traces(12000, 0.95)
      val a = McmcPrecision.evaluate(data, loose)
      a.criteriaMet shouldBe true
      a.diagnostics.mcseMean.get should be > a.batchMeansMcse.get
      val formerWidth = 2 * critical * a.batchMeansMcse.get
      val target = (formerWidth + a.fullWidth.get) / 2 + a.penalty
      val guarded = McmcPrecision.evaluate(data, loose.copy(absoluteTolerance = Some(target)))
      (formerWidth + guarded.penalty) should be < target
      guarded.criteriaMet shouldBe false
      guarded.failureReasons shouldBe Vector(FailureReason.WidthTooLarge)
    }
    "never narrow the interval below either valid constituent estimate" in {
      for (rho <- Vector(0.0, 0.5, 0.95); seed <- Vector(91L, 7311L)) {
        val a = McmcPrecision.evaluate(traces(4000, rho, seed), loose, 5)
        a.mcseUsed.get shouldBe math.max(a.batchMeansMcse.get, a.diagnostics.mcseMean.get)
        val simultaneous = -new NormalDistribution(0, 1).inverseCumulativeProbability(0.005)
        a.fullWidth.get shouldBe (2 * simultaneous * a.mcseUsed.get +- 1e-12)
      }
    }
    "explain failed minimum-work, degenerate-error, and mixing checks" in {
      val short = McmcPrecision.evaluate(traces(100), loose)
      short.failureReasons should contain (FailureReason.InsufficientDraws)
      short.failureReasons should contain (FailureReason.InsufficientBatches)
      val constant = McmcPrecision.evaluate(Vector.fill(4)(Vector.fill(2000)(1.0)), loose)
      constant.mcseUsed shouldBe None
      constant.fullWidth shouldBe None
      constant.failureReasons should contain (FailureReason.UnavailableMcse)
      constant.failureReasons should contain (FailureReason.InvalidRHat)
      constant.failureReasons should contain (FailureReason.InvalidTargetWidth)
      val shifted = traces(2000).zipWithIndex.map((values, i) => values.map(_ + 100 * i))
      McmcPrecision.evaluate(shifted, loose).failureReasons should contain (FailureReason.InvalidRHat)
      McmcPrecision.evaluate(traces(4000), loose).failureReasons shouldBe empty
    }
    "only pass checkpoints that also passed the former batch-only rule" in {
      val policy = McmcPrecision.Config(relativeTolerance = 0.15)
      for (rho <- Vector(0.0, 0.9, 0.95)) {
        val data = traces(6000, rho)
        for (count <- 1000 to 6000 by 1000) {
          val a = McmcPrecision.evaluate(data.map(_.take(count)), policy)
          val d = a.diagnostics
          val legacy = count >= policy.minDrawsPerChain && a.batchesPerChain >= policy.minBatches &&
            d.rHat.exists(_ <= policy.maxRHat) && d.bulkEss.exists(_ >= 400) && d.meanEss.exists(_ >= 400) &&
            a.batchMeansMcse.exists(se => 2 * critical * se + a.penalty <= a.targetWidth)
          if (a.criteriaMet) legacy shouldBe true
          a.criteriaMet shouldBe a.failureReasons.isEmpty
        }
      }
    }
    "preserve units and refuse unavailable constituent error estimates" in {
      val data = traces(2000, 0.5)
      val a = McmcPrecision.evaluate(data, loose)
      for (scale <- Vector(1e-100, 8.0, 1e100)) {
        val b = McmcPrecision.evaluate(data.map(_.map(_ * scale)), loose)
        b.mcseUsed.get / scale shouldBe (a.mcseUsed.get +- 1e-12)
        b.fullWidth.get / scale shouldBe (a.fullWidth.get +- 1e-12)
        b.criteriaMet shouldBe a.criteriaMet
      }
      a.copy(diagnostics = a.diagnostics.copy(mcseMean = None)).mcseUsed shouldBe None
      a.copy(batchMeansMcse = Some(Double.NaN)).mcseUsed shouldBe None
      a.copy(batchMeansMcse = Some(0.0)).mcseUsed shouldBe None
    }
  }
}
