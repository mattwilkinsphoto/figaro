/*
 * Numerical compatibility checks for the JSci replacement boundary.
 *
 * Copyright 2017 Avrom J. Pfeffer and Charles River Analytics, Inc.
 * See LICENSE and FigaroAttributions.txt.
 */

package com.cra.figaro.test.modernization

import com.cra.figaro.util.SpecialFunctions
import org.scalatest.{Matchers, WordSpec}

class SpecialFunctionsRegressionTest extends WordSpec with Matchers {
  private val tolerance = 1e-12

  "The Commons Math special-function boundary" should {
    "preserve gamma and log-gamma values" in {
      SpecialFunctions.gamma(0.5) should be(math.sqrt(math.Pi) +- tolerance)
      SpecialFunctions.logGamma(5.0) should be(math.log(24.0) +- tolerance)
    }

    "preserve the beta function" in {
      SpecialFunctions.beta(2.0, 3.0) should be((1.0 / 12.0) +- tolerance)
    }

    "preserve the Gaussian error function" in {
      SpecialFunctions.error(1.0) should be(0.8427007929497149 +- tolerance)
    }

    "preserve factorial and log-factorial values" in {
      SpecialFunctions.factorial(10) should equal(3628800.0)
      SpecialFunctions.logFactorial(12) should be(math.log(479001600.0) +- tolerance)
    }

    "preserve binomial coefficients" in {
      SpecialFunctions.binomial(20, 7) should equal(77520.0)
    }
  }
}
