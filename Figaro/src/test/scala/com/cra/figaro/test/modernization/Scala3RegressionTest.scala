/* See LICENSE and FigaroAttributions.txt for the project's license terms. */
package com.cra.figaro.test.modernization

import com.cra.figaro.algorithm.factored.VariableElimination
import com.cra.figaro.algorithm.sampling.{Importance, OneTimeProbQuerySampler, WeightedSampler}
import com.cra.figaro.algorithm.decision.index.*
import com.cra.figaro.algorithm.decision.index.Distance.*
import com.cra.figaro.language.*
import com.cra.figaro.library.atomic.continuous.CompoundNormal
import com.cra.figaro.library.decision.DecisionSample
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import scala.collection.mutable.Map

class Scala3RegressionTest extends AnyWordSpec with Matchers {
  "Dynamic element creation" should {
    "resolve a companion name and preserve exact inference" in {
      Universe.createNew()
      val flip = Create[Boolean]("com.cra.figaro.language.Flip", Constant(0.25))
      val algorithm = VariableElimination(flip)
      algorithm.start()
      try algorithm.probability(flip, true) shouldBe (0.25 +- 1e-12)
      finally algorithm.kill()
    }

    "accept an explicit JVM module name" in {
      Universe.createNew()
      val flip = Create[Boolean]("com.cra.figaro.language.Flip$", Constant(1.0))
      flip.generate()
      flip.value shouldBe true
    }

    "preserve multi-argument continuous distribution parameters" in {
      Universe.createNew()
      val mean = Constant(2.0)
      val variance = Constant(4.0)
      mean.generate()
      variance.generate()
      val normal = Create[Double]("com.cra.figaro.library.atomic.continuous.Normal", mean, variance)
        .asInstanceOf[CompoundNormal]
      normal.meanValue shouldBe 2.0
      normal.varianceValue shouldBe 4.0
    }

    "reject objects outside the Creatable contract" in {
      an[IllegalArgumentException] should be thrownBy Create[Any]("com.cra.figaro.language.Constant")
    }

    "reject ordinary classes outside the Creatable contract" in {
      an[IllegalArgumentException] should be thrownBy Create[Any]("java.lang.String")
    }

    "preserve class-not-found errors" in {
      an[ClassNotFoundException] should be thrownBy Create[Any]("com.cra.figaro.missing.NoSuchDistribution")
    }

    "retain the cause when creation rejects its arguments" in {
      val error = intercept[RuntimeException] { Create[Boolean]("com.cra.figaro.language.Flip") }
      error.getMessage shouldBe "Wrong argument types for external distribution"
      error.getCause should not be null
    }
  }

  "Element initialization" should {
    "distinguish an uninitialized Boolean from a valid false value" in {
      Universe.createNew()
      val flip = Flip(0.0)
      flip.hasValue shouldBe false
      flip.generate()
      flip.hasValue shouldBe true
      flip.value shouldBe false
    }

    "preserve zero-valued arguments when evaluating Apply" in {
      Universe.createNew()
      val zero = Constant(0)
      val result = Apply(zero, (n: Int) => n + 1)
      result.generate()
      zero.hasValue shouldBe true
      result.value shouldBe 1
    }
  }

  "Weighted sampling" should {
    "preserve heterogeneous values in a joint posterior" in {
      given Universe = Universe.createNew()
      val result = Importance.sampleJointPosterior(Constant(true), Constant(7), Constant("item"))
      result.take(3).toList shouldBe List.fill(3)(List(true, 7, "item"))
    }

    "keep mixed Boolean and integer target counts correlated" in {
      Universe.createNew()
      val boolean = Flip(0.5)
      val integer = Constant(0)
      val sampler = new WeightedSampler(Universe.universe, boolean, integer) with OneTimeProbQuerySampler {
        val numSamples = 2
        private var position = 0
        def sample(): Sample = {
          position += 1
          if (position == 1) (math.log(2.0), Map[Element[?], Any](boolean -> true, integer -> 1))
          else (math.log(3.0), Map[Element[?], Any](boolean -> false, integer -> 2))
        }
      }
      sampler.start()
      try {
        sampler.probability(boolean, true) shouldBe (0.4 +- 1e-12)
        sampler.probability(integer, 2) shouldBe (0.6 +- 1e-12)
      } finally sampler.kill()
    }
  }

  "Decision distance evidence" should {
    "order flat-index neighbors by distance" in {
      val data = (0 until 20).map(i => ((i.toDouble * 2, i), DecisionSample(i.toDouble, 1.0))).toMap
      val index = FlatIndex[Double, Int](data)
      index.getNN(3.1, 3).map(_._2) shouldBe List(2, 1, 3)
    }

    "preserve VP tree neighbor selection through internal-node splits" in {
      val data = (0 until 20).map(i => ((i.toDouble * 2, i), DecisionSample(i.toDouble, 1.0))).toMap
      val flat = FlatIndex[Double, Int](data)
      val tree = VPIndex[Double, Int](data, 3)
      for (query <- List(0.1, 3.1, 17.3, 39.1)) {
        tree.getNN(query, 3).sortBy(_._1) shouldBe flat.getNN(query, 3)
      }
    }

    "combine heterogeneous tuple distance conversions" in {
      TupleDistance2[Int, Double]((0, 0.0)).distance((3, 4.0)) shouldBe 5.0
    }

    "accept a user-defined parent that implements Distance directly" in {
      case class Point(x: Int) extends Distance[Point] {
        def distance(that: Point): Double = math.abs(x - that.x).toDouble
      }
      val data = scala.collection.immutable.Map(
        (Point(0), "near") -> DecisionSample(1.0, 1.0),
        (Point(10), "far") -> DecisionSample(2.0, 1.0))
      FlatIndex[Point, String](data).getNN(Point(1), 1).head._2 shouldBe "near"
    }
  }
}
