/* See LICENSE and FigaroAttributions.txt for the project's license terms. */
package com.cra.figaro.test.modernization

import com.cra.figaro.algorithm.decision.index.{Distance, LNode, Node}
import com.cra.figaro.algorithm.factored.VariableElimination
import com.cra.figaro.algorithm.sampling.{Importance, MetropolisHastings, ProposalScheme}
import com.cra.figaro.language.*
import com.cra.figaro.library.collection.{FixedSizeArray, MakeArray, VariableSizeArray}
import com.cra.figaro.library.compound.IntSelector
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class DeprecationRegressionTest extends AnyWordSpec with Matchers {
  "Lazy posterior results" should {
    "retain a finite distribution after its algorithm is disposed" in {
      Universe.createNew()
      val target = Select(0.25 -> "a", 0.75 -> "b")
      val algorithm = VariableElimination(target)
      algorithm.start()
      val result: LazyList[(Double, String)] = try algorithm.distribution(target)
      finally algorithm.kill()
      result.map { case (p, value) => value -> p }.toMap shouldBe Map("a" -> 0.25, "b" -> 0.75)
      result.toList shouldBe result.toList
    }

    "retain weighted posterior samples after cleanup" in {
      Universe.createNew()
      val target = Constant(42)
      val algorithm = Importance(20, target)
      algorithm.start()
      val result: LazyList[Int] = try algorithm.sampleFromPosterior(target)
      finally algorithm.kill()
      result.take(5).toList shouldBe List.fill(5)(42)
      result.take(5).toList shouldBe List.fill(5)(42)
    }

    "retain unweighted posterior samples after cleanup" in {
      Universe.createNew()
      val target = Flip(1.0)
      val algorithm = MetropolisHastings(20, ProposalScheme.default, target)
      algorithm.start()
      val result: LazyList[Boolean] = try algorithm.sampleFromPosterior(target)
      finally algorithm.kill()
      result.take(5).toList shouldBe List.fill(5)(true)
    }
  }

  "Lazy model collections" should {
    "create each requested item once and reuse it across array prefixes" in {
      val universe = Universe.createNew()
      var created = Vector.empty[Int]
      val count = Constant(3)
      val array = new MakeArray[Int](Name[FixedSizeArray[Int]]("items"), count,
        i => { created :+= i; Constant(i) }, universe)
      // Element registration asks for args, initializing the first memoized item.
      created shouldBe Vector(0)
      val first = array.items.head
      created shouldBe Vector(0)
      array.items.head should be theSameInstanceAs first
      array.items.take(3).toList.size shouldBe 3
      created shouldBe Vector(0, 1, 2)
      array.arrays(1)(0) should be theSameInstanceAs first
      array.arrays(3)(0) should be theSameInstanceAs first
      created shouldBe Vector(0, 1, 2)
    }

    "replace random-length lists without losing exact probabilities" in {
      Universe.createNew()
      val count = Select(0.4 -> 0, 0.6 -> 2)
      val items = VariableSizeArray(count, (_: Int) => Flip(0.5))
      val values = items.foldLeft(List.empty[Boolean])((xs, value) => xs :+ value)
      val algorithm = VariableElimination(values)
      algorithm.start()
      try {
        algorithm.probability(values, List.empty[Boolean]) shouldBe (0.4 +- 1e-12)
        algorithm.probability(values, List(true, false)) shouldBe (0.15 +- 1e-12)
      } finally algorithm.kill()
    }

    "memoize selector randomness when the bound changes" in {
      Universe.createNew()
      val count = Constant(3)
      val selector = IntSelector(count)
      val randomness: LazyList[Double] = selector.generateRandomness()
      val prefix = randomness.take(3).toList
      randomness.take(5).toList.take(3) shouldBe prefix
      count.value = 3
      val first = selector.generateValue(randomness)
      count.value = 5
      selector.generateValue(randomness)
      count.value = 3
      selector.generateValue(randomness) shouldBe first
    }
  }

  "Decision leaf storage" should {
    "retain distinct bindings without duplicating equal values" in {
      case class Key(value: Int) extends Distance[Int] {
        def distance(that: Int): Double = math.abs(value - that).toDouble
      }
      val leaf = new Node[Int, String](null, true) with LNode[Int, String]
      val key = Key(2)
      leaf.addObject(key, "a")
      leaf.addObject(key, "b")
      leaf.addObject(key, "a")
      leaf.objects(key).toSet shouldBe Set("a", "b")
      leaf.oDist(3).toList.map { case (distance, values) => distance -> values.toSet } shouldBe
        List(1.0 -> Set("a", "b"))
    }
  }

  "Registered cache storage" should {
    "retain its registration hash as entries change and remove deactivated elements" in {
      val universe = Universe.createNew()
      val cache = new com.cra.figaro.util.RegisteredMap[Element[?], Int](123)
      universe.register(cache)
      val element = Constant(42)
      cache.update(element, 1)
      cache.hashCode() shouldBe 123
      element.deactivate()
      cache.get(element) shouldBe None
      cache.hashCode() shouldBe 123
      universe.deregister(cache)
      val retained = Constant(7)
      cache.update(retained, 2)
      retained.deactivate()
      cache.get(retained) shouldBe Some(2)
    }
  }
}
