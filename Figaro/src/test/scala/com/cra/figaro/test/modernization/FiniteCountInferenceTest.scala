package com.cra.figaro.test.modernization

import com.cra.figaro.algorithm.factored.VariableElimination
import com.cra.figaro.language.*
import com.cra.figaro.library.atomic.discrete.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class FiniteCountInferenceTest extends AnyWordSpec with Matchers {
  private def choose(n: Int, k: Int): BigInt =
    (1 to k).foldLeft(BigInt(1))((a, i) => a * (n - i + 1) / i)
  private def hg(k: Int): Double =
    (choose(7, k) * choose(13, 5 - k)).toDouble / choose(20, 5).toDouble

  "Explicit finite-count inference" should {
    "enumerate a hypergeometric law exactly with evidence" in {
      val u = Universe.createNew()
      val x = CountElement.exact(HypergeometricDistribution(20, 7, 5))(using "x", u)
      x.addCondition(_ >= 2)
      val algorithm = VariableElimination(x)(using u)
      try {
        algorithm.start()
        algorithm.probability(x, 2) shouldBe (hg(2) / (2 to 5).map(hg).sum +- 1e-13)
      } finally { if (algorithm.isActive) algorithm.kill(); u.clear() }
    }

    "preserve multinomial joint dependence rather than multiplying marginals" in {
      val u = Universe.createNew()
      val law = MultinomialDistribution(3, Vector(0.25, 0.25, 0.5))
      val x = Multinomial.exact(law)(using "x", u)
      val algorithm = VariableElimination(x)(using u)
      try {
        algorithm.start()
        algorithm.probability(x, Vector(1, 1, 1)) shouldBe (0.1875 +- 1e-14)
        algorithm.distribution(x).map(_._1).sum shouldBe (1.0 +- 1e-14)
        algorithm.distribution(x).foreach { case (_, counts) => counts.sum shouldBe 3 }
      } finally { if (algorithm.isActive) algorithm.kill(); u.clear() }
    }

    "use the full mass of finite mixtures including support holes and zero-weight laws" in {
      val u = Universe.createNew()
      val law = CountMixtureDistribution(Vector(0.7, 0.3, 0.0), Vector(
        HypergeometricDistribution(20, 7, 5), NegativeBinomialDistribution(2, 1), NegativeBinomialDistribution(2, 0.5)))
      val x = CountElement.exact(law)(using "x", u)
      val algorithm = VariableElimination(x)(using u)
      try {
        algorithm.start()
        algorithm.probability(x, 0) shouldBe (0.7 * hg(0) + 0.3 +- 1e-13)
      } finally { if (algorithm.isActive) algorithm.kill(); u.clear() }
    }

    "check state and cell budgets before registering any element" in {
      val u = Universe.createNew()
      intercept[IllegalArgumentException] {
        CountElement.exact(HypergeometricDistribution(20, 7, 5), FiniteCountInference.Budget(maxStates = 5))(using "x", u)
      }
      intercept[IllegalArgumentException] {
        Multinomial.exact(MultinomialDistribution(1000000, Vector(0.2, 0.3, 0.5)))(using "x", u)
      }
      intercept[IllegalArgumentException] {
        Multinomial.exact(MultinomialDistribution(3, Vector(0.2, 0.3, 0.5)),
          FiniteCountInference.Budget(maxCells = 29))(using "x", u)
      }
      intercept[IllegalArgumentException] {
        CountElement.exact(NegativeBinomialDistribution(2, 0.5))(using "x", u)
      }
      u.activeElements shouldBe empty
      u.clear()
    }

    "enumerate deterministic large-trial and zero-probability-category limits cheaply" in {
      val u = Universe.createNew()
      val x = Multinomial.exact(MultinomialDistribution(1000000, Vector(0.0, 1.0, 0.0)),
        FiniteCountInference.Budget(maxStates = 1, maxCells = 3))(using "x", u)
      x.density(Vector(0, 1000000, 0)) shouldBe 1.0
      val zero = Multinomial.exact(MultinomialDistribution(0, Vector(0.2, 0.8)),
        FiniteCountInference.Budget(maxStates = 1))(using "zero", u)
      zero.density(Vector(0, 0)) shouldBe 1.0
      u.clear()
    }
  }
}
