/*
 * Deterministic probability-output regressions for the modernization effort.
 *
 * Copyright 2017 Avrom J. Pfeffer and Charles River Analytics, Inc.
 * See LICENSE and FigaroAttributions.txt.
 */

package com.cra.figaro.test.modernization

import com.cra.figaro.algorithm.factored.VariableElimination
import com.cra.figaro.language.{Flip, Select, Universe}
import com.cra.figaro.library.compound.If
import org.scalatest.{Matchers, WordSpec}

class ProbabilityRegressionTest extends WordSpec with Matchers {
  private val tolerance = 1e-12

  "The modernized Java 17 build" should {
    "preserve a direct Bernoulli marginal" in {
      Universe.createNew()
      val target = Flip(0.3)
      val algorithm = VariableElimination(target)

      algorithm.start()
      try algorithm.probability(target, true) should be(0.3 +- tolerance)
      finally algorithm.kill()
    }

    "preserve an exact Bayesian posterior under evidence" in {
      Universe.createNew()
      val cause = Flip(0.2)
      val signal = If(cause, Flip(0.9), Flip(0.1))
      signal.observe(true)
      val algorithm = VariableElimination(cause)

      algorithm.start()
      try {
        val expected = (0.2 * 0.9) / (0.2 * 0.9 + 0.8 * 0.1)
        algorithm.probability(cause, true) should be(expected +- tolerance)
      } finally algorithm.kill()
    }

    "preserve a compound discrete marginal" in {
      Universe.createNew()
      val branch = Flip(0.4)
      val target = If(
        branch,
        Select(0.25 -> "x", 0.75 -> "y"),
        Select(0.8 -> "x", 0.2 -> "y")
      )
      val algorithm = VariableElimination(target)

      algorithm.start()
      try {
        val expected = 0.4 * 0.25 + 0.6 * 0.8
        algorithm.probability(target, "x") should be(expected +- tolerance)
      } finally algorithm.kill()
    }
  }
}
