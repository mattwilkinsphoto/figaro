package com.cra.figaro.test.modernization

import com.cra.figaro.algorithm.sampling.{Importance, MetropolisHastings, ProposalScheme}
import com.cra.figaro.algorithm.sampling.parallel.{MultiChainMetropolisHastings, ParImportance}
import com.cra.figaro.language.*
import com.cra.figaro.library.atomic.continuous.{Normal, VonMises, VonMisesDistribution}
import com.cra.figaro.util.{CircularStatistics as Circular, withRandomSeed}
import java.util.concurrent.CancellationException
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class VonMisesRegressionTest extends AnyWordSpec with Matchers {
  // mpmath 1.3.0, 80 digits; tools/von_mises_reference.py regenerates these independent values.
  private val references = Vector(
    (0.0, -1.8378770664093454836, 0.0),
    (1e-10, -1.8378770663093454836, 5.0e-11),
    (0.1, -1.7403755056432217269, 0.049937603987938919425),
    (1.0, -1.0737914249165241323, 0.44638996589653450705),
    (1.0001, -1.0737360656848065631, 0.44642539937823230434),
    (3.0, -0.42318468822276639907, 0.80998529395650452706),
    (50.0, 1.0345474317188499323, 0.98994896737849775259),
    (50.0001, 1.0345484368211017707, 0.98994898758472099494),
    (100.0, 1.3823902436480707998, 0.99498737300516876559),
    (10000.0, 3.6862191521583535119, 0.99994999874987498046),
    (100000000.0, 8.291401837521509988, 0.9999999949999999875))

  "Circular utilities" should {
    "normalize boundaries and avoid overflow when differencing finite angles" in {
      Circular.normalize(math.Pi) shouldBe -math.Pi
      Circular.normalize(-math.Pi) shouldBe -math.Pi
      Circular.normalize(-0.0).toString shouldBe "0.0"
      Circular.difference(Double.MaxValue, -Double.MaxValue).isFinite shouldBe true
      Circular.difference(-3.1, 3.1) shouldBe (2 * math.Pi - 6.2 +- 1e-14)
      for (x <- Vector(Double.NaN, Double.PositiveInfinity, Double.NegativeInfinity))
        intercept[IllegalArgumentException](Circular.normalize(x))
    }
    "report circular rather than arithmetic means and unidentified directions" in {
      val angles = Vector(-179.0, 179.0).map(math.toRadians)
      val summary = Circular.summarize(angles.iterator)
      summary.count shouldBe 2L
      math.abs(summary.meanDirection.get) shouldBe (math.Pi +- 1e-14)
      summary.meanResultantLength shouldBe (math.cos(math.toRadians(1)) +- 1e-14)
      Circular.summarize(Vector(0.0, math.Pi)).meanDirection shouldBe None
      Circular.summarize(Vector(0.0), 1.0).meanDirection shouldBe None
      intercept[IllegalArgumentException](Circular.summarize(Vector.empty))
      intercept[IllegalArgumentException](Circular.summarize(Vector(Double.NaN)))
      for (t <- Vector(-1.0, 1.1, Double.NaN))
        intercept[IllegalArgumentException](Circular.summarize(Vector(0.0), t))
    }
  }

  "Von Mises numerical kernel" should {
    "match independent high-precision Bessel references across both numerical branches" in {
      references.foreach { (k, peak, resultant) =>
        val d = VonMisesDistribution(0.0, k)
        withClue(s"kappa=$k: ") {
          d.logDensity(0.0) shouldBe (peak +- 2e-13)
          d.meanResultantLength shouldBe (resultant +- 3e-15)
          d.logDensity(math.Pi) shouldBe (peak - 2 * k +- (1e-12 + 1e-15 * k))
        }
      }
      VonMisesDistribution(0.5, 0.0).meanDirection shouldBe None
      VonMisesDistribution(4.0, 1.0).meanDirection.get shouldBe Circular.normalize(4.0)
    }
    "integrate to one on the circle and in scaled coordinates for concentrated laws" in {
      for (k <- Vector(0.0, 0.1, 1.0, 1.0001, 3.0, 50.0, 50.0001, 100.0, 1e4, 1e8)) {
        val d = VonMisesDistribution(0.0, k)
        val bound = if (k <= 100) math.Pi else 12.0 / math.sqrt(k)
        val step = 2 * bound / 20000
        val integral = (0 until 20000).iterator.map(i => d.density(-bound + (i + 0.5) * step)).sum * step
        withClue(s"kappa=$k: ") { integral shouldBe (1.0 +- 2e-11) }
      }
    }
    "score equivalent angles periodically and retain finite underflowed tail logs" in {
      for (k <- Vector(0.0, 0.1, 3.0, 100.0)) {
        val d = VonMisesDistribution(0.7, k)
        d.logDensity(-2.0) shouldBe (d.logDensity(-2 + 12 * math.Pi) +- 2e-12)
        d.density(-2.0) shouldBe (math.exp(d.logDensity(-2.0)) +- 1e-15)
      }
      val tail = VonMisesDistribution(0.0, 1000.0)
      tail.density(math.Pi) shouldBe 0.0
      tail.logDensity(math.Pi).isFinite shouldBe true
    }
    "reject invalid parameters, nonfinite values and invalid sampling budgets" in {
      for (k <- Vector(-1.0, 1e8 + 1.0, Double.NaN, Double.PositiveInfinity))
        intercept[IllegalArgumentException](VonMisesDistribution(0.0, k))
      intercept[IllegalArgumentException](VonMisesDistribution(Double.NaN, 1.0))
      val d = VonMisesDistribution(0.0, 1.0)
      intercept[IllegalArgumentException](d.logDensity(Double.PositiveInfinity))
      intercept[IllegalArgumentException](d.sample(new scala.util.Random(1L), 0))
      intercept[IllegalArgumentException](d.sample(null))
    }
    "match circular sample moments without collapsing at high concentration" in {
      references.zipWithIndex.foreach { case ((k, _, resultant), index) =>
        val d = VonMisesDistribution(3.0, k)
        val rng = new scala.util.Random(9981L + index)
        val values = Vector.fill(50000)(d.sample(rng))
        all(values) should (be >= -math.Pi and be < math.Pi)
        val residuals = values.map(Circular.difference(_, d.location))
        withClue(s"kappa=$k: ") {
          residuals.map(math.sin).sum / values.size shouldBe (0.0 +- 0.02)
          residuals.map(math.cos).sum / values.size shouldBe (resultant +- 0.02)
          if (k >= 1e4) {
            val scaled = residuals.map(_ * math.sqrt(k))
            scaled.sum / scaled.size shouldBe (0.0 +- 0.03)
            scaled.map(x => x * x).sum / scaled.size shouldBe (1.0 +- 0.04)
            scaled.count(_ < 0).toDouble / scaled.size shouldBe (0.5 +- 0.02)
          }
        }
      }
    }
    "honor caller seeds, bounded rejection and interruption without clearing the flag" in {
      val d = VonMisesDistribution(2.0, 4.0)
      val a = new scala.util.Random(7L); val b = new scala.util.Random(7L)
      Vector.fill(200)(d.sample(a)) shouldBe Vector.fill(200)(d.sample(b))
      for (k <- Vector(1.0, 4.0)) {
        val rejecting = new scala.util.Random(0L) {
          private var n = 0
          override def nextDouble(): Double = {
            n += 1
            if (n % 2 == 0) 0.999999 else if (k == 1) 0.0 else 0.999999
          }
        }
        intercept[IllegalStateException](VonMisesDistribution(0.0, k).sample(rejecting, 3))
      }
      try {
        Thread.currentThread().interrupt()
        intercept[CancellationException](d.sample(a))
        Thread.currentThread().isInterrupted shouldBe true
      } finally { Thread.interrupted() }
    }
  }

  "Von Mises Figaro integration" should {
    "use scoped randomness and keep logp and density consistent" in {
      val u = new Universe
      try {
        val e = VonMises(0.2, 3.0)(using "angle", u)
        def draws = Vector.fill(100)(e.generateRandomness())
        withRandomSeed(91L)(draws) shouldBe withRandomSeed(91L)(draws)
        e.logp(0.4) shouldBe e.logDensity(0.4)
        e.density(0.4) shouldBe e.distribution.density(0.4)
        withRandomSeed(4L) {
          val (_, proposal, model) = e.nextRandomness(0.1)
          proposal * model shouldBe (1.0 +- 1e-13)
        }
        val narrow = VonMises(0.0, 1000.0)(using "narrow", u)
        intercept[ArithmeticException](narrow.nextRandomness(math.Pi))
      } finally u.clear()
    }
    "preserve finite importance weights even when the ordinary density underflows" in {
      val u = Universe.createNew()
      val e = VonMises(0.0, 1000.0)(using "angle", u)
      e.observe(math.Pi)
      val alg = Importance(100, e)
      try {
        alg.start()
        alg.getTotalWeight shouldBe (math.log(100) + e.logDensity(math.Pi) +- 1e-9)
        alg.probability(e, math.Pi) shouldBe (1.0 +- 1e-12)
      } finally { if (alg.isActive) alg.kill(); u.clear() }
    }
    "preserve the HasDensity-only observation path" in {
      val u = Universe.createNew()
      val e = Normal(0.0, 1.0)(using "normal", u)
      e.observe(1.0)
      val alg = Importance(100, e)
      try { alg.start(); alg.getTotalWeight shouldBe (math.log(100) + math.log(e.density(1.0)) +- 1e-12) }
      finally { if (alg.isActive) alg.kill(); u.clear() }
    }
    "weight conditional location and concentration observations with analytic posterior odds" in {
      // Enumerate a two-state prior, independently computing the likelihood ratio.
      for (variant <- 0 until 3; shift <- Vector(0.0, 2 * math.Pi)) withRandomSeed(621L) {
        val u = Universe.createNew()
        val choice = Flip(0.4)(using "choice", u)
        val m = Apply(choice, (b: Boolean) => if (b) 0.0 else 1.0)
        val k = Apply(choice, (b: Boolean) => if (b) 3.0 else 1.0)
        val e = variant match {
          case 0 => VonMises(m, 3.0)
          case 1 => VonMises(0.0, k)
          case _ => VonMises(m, k)
        }
        val obs = 0.3
        e.observe(obs + shift)
        val falseMean = if (variant == 1) 0.0 else 1.0
        val falsePeak = if (variant == 0) references(5)._2 else references(3)._2
        val falseK = if (variant == 0) 3.0 else 1.0
        val trueLog = references(5)._2 + 3 * (math.cos(obs) - 1)
        val falseLog = falsePeak + falseK * (math.cos(obs - falseMean) - 1)
        val expected = 1.0 / (1.0 + 1.5 * math.exp(falseLog - trueLog))
        val alg = Importance(18000, choice)
        try { alg.start(); alg.probability(choice, true) shouldBe (expected +- 0.025) }
        finally { if (alg.isActive) alg.kill(); u.clear() }
      }
    }
    "match a conjugate circular posterior in both importance and ordinary MH" in {
      val center = math.atan2(3 * math.sin(1.0), 2 + 3 * math.cos(1.0))
      val concentration = math.hypot(3 * math.sin(1.0), 2 + 3 * math.cos(1.0))
      // Independent quadrature oracle rather than reusing the Bessel implementation.
      val grid = (0 until 20000).map(i => -math.Pi + (i + 0.5) * 2 * math.Pi / 20000)
      val weights = grid.map(x => math.exp(concentration * math.cos(x - center)))
      val expectedSin = grid.zip(weights).map((x,w) => math.sin(x) * w).sum / weights.sum
      val expectedCos = grid.zip(weights).map((x,w) => math.cos(x) * w).sum / weights.sum
      for (mh <- Vector(false, true)) withRandomSeed(181L) {
        val u = Universe.createNew()
        val e = VonMises(0.0, 2.0)(using "angle", u)
        e.addLogConstraint((angle: Double) => 3.0 * math.cos(1.0 - angle))
        val alg = if (mh) MetropolisHastings(25000, ProposalScheme.default(using u), 1000, e) else Importance(25000, e)
        try {
          alg.start()
          alg.expectation(e, (x: Double) => math.sin(x)) shouldBe (expectedSin +- 0.03)
          alg.expectation(e, (x: Double) => math.cos(x)) shouldBe (expectedCos +- 0.03)
        } finally { if (alg.isActive) alg.kill(); u.clear() }
      }
    }
    "support isolated parallel importance with circular queries" in {
      val alg = ParImportance.seeded(() => {
        val u = Universe.createNew()
        val angle = VonMises(0.0, 3.0)(using "angle", u)
        Apply(angle, (x: Double) => math.cos(x))(using "cos", u)
        u
      }, 3, 24000, 992L, "cos")
      try { alg.start(); alg.expectation[Double]("cos", identity) shouldBe (references(5)._3 +- 0.025) }
      finally if (alg.isActive) alg.kill()
    }
    "retain identical seeded multi-chain projections when worker count changes" in {
      import MultiChainMetropolisHastings.*
      val config = Config(chains = 2, drawsPerChain = 2000, warmUp = 100, parallelism = 1)
      def model(u: Universe, index: Int): Model = {
        val angle = VonMises(0.4, 3.0)(using "angle", u)
        Model(Vector(Observable("sin", angle)(math.sin), Observable("cos", angle)(math.cos)))
      }
      val serial = MultiChainMetropolisHastings.run(config)(model)
      val parallel = MultiChainMetropolisHastings.run(config.copy(parallelism = 2))(model)
      serial.chains.map(_.draws) shouldBe parallel.chains.map(_.draws)
      parallel.chains.map(_.draws).distinct.size shouldBe 2
    }
    "reject invalid fixed parameters without registering an element" in {
      val u = new Universe
      try {
        val before = u.activeElements.size
        intercept[IllegalArgumentException](VonMises(0.0, -1.0)(using "bad", u))
        u.activeElements.size shouldBe before
        val bad = VonMises(Constant(0.0)(using "", u), Constant(-1.0)(using "", u))(using "bad", u)
        intercept[IllegalArgumentException](bad.generate())
      } finally u.clear()
    }
  }
}
