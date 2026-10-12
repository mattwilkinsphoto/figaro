package com.cra.figaro.test.modernization

import com.cra.figaro.algorithm.filtering.ParticleFilter
import com.cra.figaro.language.{Atomic, Element, Flip, HasLogDensity, HasLogPriorProposal, Universe}
import com.cra.figaro.library.atomic.continuous.Laplace
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Analytic, non-statistical controls for inference integration. */
class InferenceIntegrationRegressionTest extends AnyWordSpec with Matchers {
  "Particle-filter log evidence" should {
    "retain a finite log likelihood when the ordinary likelihood underflows" in {
      val universe = Universe.createNew()
      val observed = Laplace(0.0, 1.0)(using "observed", universe)
      observed.observe(1000.0)
      val filter = ParticleFilter(universe, (_: Universe) => new Universe(), 1)
      try {
        filter.start()
        filter.getlogProbEvidence() shouldBe (-1000.0 - math.log(2.0) +- 1e-12)
      } finally {
        if (filter.isActive) filter.kill()
        universe.clear()
      }
    }

    "retain a finite log likelihood when the joint ordinary density overflows" in {
      val universe = Universe.createNew()
      (0 until 4).foreach { i =>
        val observed = Laplace(0.0, 1e-100)(using s"observed$i", universe)
        observed.observe(0.0)
      }
      val filter = ParticleFilter(universe, (_: Universe) => new Universe(), 1)
      try {
        filter.start()
        filter.getlogProbEvidence() shouldBe (-4.0 * math.log(2e-100) +- 1e-12)
      } finally {
        if (filter.isActive) filter.kill()
        universe.clear()
      }
    }
  }

  "The legacy MH ratio interface" should {
    "explicitly refuse an unrepresentable ratio even when reciprocal ratios cancel" in {
      val universe = Universe.createNew()
      val observed = new Element[Double]("observed", universe) with Atomic[Double] with HasLogDensity[Double] {
        type Randomness = Double
        def generateRandomness(): Double = 0.0
        def generateValue(value: Double): Double = value
        def logDensity(value: Double): Double = -math.abs(value) - math.log(2.0)
      }
      // Fixed randomness exercises the real legacy adapter without a rare RNG draw.
      val delta = observed.logDensity(0.0) - observed.logDensity(1000.0)
      delta shouldBe 1000.0
      math.exp(delta) shouldBe Double.PositiveInfinity
      math.exp(-delta) shouldBe 0.0
      (-delta + delta) shouldBe 0.0
      intercept[ArithmeticException] { observed.nextRandomness(1000.0) }
      universe.clear()
    }
  }

  "Particle-filter zero-weight policy" should {
    "fail explicitly and prevent posterior queries by default" in {
      val universe = Universe.createNew()
      Flip(0.0)(using "impossible", universe).observe(true)
      val filter = ParticleFilter(universe, (_: Universe) => new Universe(), 2)
      try {
        intercept[ParticleFilter.NoViableParticles] { filter.start() }
        intercept[IllegalStateException] { filter.currentProbability("impossible", true) }
        intercept[IllegalStateException] { filter.getlogProbEvidence() }
        intercept[IllegalStateException] { filter.advanceTime() }
      } finally {
        if (filter.isActive) filter.kill()
        universe.clear()
      }
    }

    "retain the explicitly requested legacy fallback" in {
      val universe = Universe.createNew()
      Flip(0.0)(using "impossible", universe).observe(true)
      val filter = ParticleFilter(universe, (_: Universe) => new Universe(), 2,
        ParticleFilter.ZeroWeightPolicy.LegacyUniform)
      try {
        filter.start()
        filter.getlogProbEvidence() shouldBe Double.NegativeInfinity
        filter.currentProbability("impossible", true) shouldBe 1.0
      } finally {
        if (filter.isActive) filter.kill()
        universe.clear()
      }
    }
  }

  "Sequential and parallel filtering" should {
    "apply the explicit strict and legacy policies in the parallel factory" in {
      def impossible(): Universe = {
        val u=new Universe; Flip(0.0)(using "x",u).observe(true); u
      }
      val strict=ParticleFilter.par(() => impossible(), (_: Universe) => impossible(), 4, 2)
      try {
        intercept[ParticleFilter.NoViableParticles](strict.start())
        intercept[IllegalStateException](strict.probEvidence())
      } finally if(strict.isActive) strict.kill()
      val legacy=ParticleFilter.par(() => impossible(), (_: Universe) => impossible(), 4, 2,
        ParticleFilter.ZeroWeightPolicy.LegacyUniform)
      try { legacy.start(); legacy.getlogProbEvidence() shouldBe Double.NegativeInfinity }
      finally if(legacy.isActive) legacy.kill()
    }

    "refuse accumulated log evidence overflow rather than mislabel finite likelihoods as zero" in {
      def extreme(): Universe = {
        val u=new Universe; Laplace(0,1)(using "y",u).observe(1e308); u
      }
      val filter=ParticleFilter(extreme(), (_: Universe) => extreme(),1)
      try {
        filter.start(); filter.getlogProbEvidence().isFinite shouldBe true
        intercept[IllegalArgumentException](filter.advanceTime())
        intercept[IllegalStateException](filter.getlogProbEvidence())
      } finally if(filter.isActive) filter.kill()
    }

    "invalidate both belief and evidence when a later transition fails" in {
      def model(): Universe = { val u=new Universe; Flip(.5)(using "x",u); u }
      val sequential=ParticleFilter(model(), (_: Universe) => throw new IllegalArgumentException("bad transition"), 2)
      val parallel=ParticleFilter.par(() => model(), (_: Universe) => throw new IllegalArgumentException("bad transition"), 2, 2)
      val filters=Vector((sequential, () => sequential.advanceTime()), (parallel, () => parallel.advanceTime()))
      filters.foreach { (filter, advance) =>
        try {
          filter.start()
          intercept[IllegalArgumentException](advance())
          intercept[IllegalStateException](filter.getlogProbEvidence())
          intercept[IllegalStateException](filter.computeCurrentExpectation[Boolean]("x", b => if(b) 1.0 else 0.0))
        } finally if(filter.isActive) filter.kill()
      }
    }
    "preserve finite log evidence over several time steps" in {
      def model(): Universe = {
        val u = new Universe()
        Laplace(0.0, 1.0)(using "observed", u).observe(1000.0)
        u
      }
      val initial = model()
      val sequential = ParticleFilter(initial, (_: Universe) => model(), 4)
      val parallel = ParticleFilter.par(() => model(), (_: Universe) => model(), 4, 2)
      try {
        sequential.start()
        parallel.start()
        (1 to 3).foreach { step =>
          val expected = step * (-1000.0 - math.log(2.0))
          sequential.getlogProbEvidence() shouldBe (expected +- 1e-11)
          parallel.getlogProbEvidence() shouldBe (expected +- 1e-11)
          if (step < 3) { sequential.advanceTime(); parallel.advanceTime() }
        }
      } finally {
        if (sequential.isActive) sequential.kill()
        if (parallel.isActive) parallel.kill()
        initial.clear()
      }
    }
  }

  "Log-space proposals" should {
    "retain separate finite corrections beyond the ordinary ratio range" in {
      val u = Universe.createNew()
      val element = new Element[Double]("", u) with Atomic[Double] with HasLogPriorProposal[Double] {
        type Randomness = Double
        def generateRandomness(): Double = 0.0
        def generateValue(value: Double): Double = value
        def logDensity(value: Double): Double = -math.abs(value) - math.log(2.0)
      }
      val (_, proposal, model) = element.nextLogRandomness(1000.0)
      proposal shouldBe -1000.0
      model shouldBe 1000.0
      (proposal + model) shouldBe 0.0
      // Annealing must not lose these separately nonzero terms.
      (proposal + 0.5 * model) shouldBe -500.0
      u.clear()
    }

    "preserve an existing custom legacy proposal override" in {
      val u = Universe.createNew()
      val element = new Element[Double]("", u) with Atomic[Double] {
        type Randomness = Double
        def generateRandomness(): Double = 0.0
        def generateValue(value: Double): Double = value
        def density(value: Double): Double = 1.0
        override def nextRandomness(old: Double): (Double, Double, Double) = (old + 1.0, 2.0, 3.0)
      }
      element.nextLogRandomness(4.0) shouldBe ((5.0, math.log(2.0), math.log(3.0)))
      u.clear()
    }
  }
}
