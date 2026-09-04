/*
 * Narrow numerical boundary for special functions and combinatorics.
 *
 * Copyright 2017 Avrom J. Pfeffer and Charles River Analytics, Inc.
 * See LICENSE and FigaroAttributions.txt.
 */

package com.cra.figaro.util

import org.apache.commons.math3.special.{Beta, Erf, Gamma}
import org.apache.commons.math3.util.CombinatoricsUtils

private[figaro] object SpecialFunctions {
  def beta(a: Double, b: Double): Double = math.exp(Beta.logBeta(a, b))

  def gamma(x: Double): Double = Gamma.gamma(x)

  def logGamma(x: Double): Double = Gamma.logGamma(x)

  def error(x: Double): Double = Erf.erf(x)

  def factorial(n: Int): Double = CombinatoricsUtils.factorialDouble(n)

  def logFactorial(n: Int): Double = CombinatoricsUtils.factorialLog(n)

  def binomial(n: Int, k: Int): Double = CombinatoricsUtils.binomialCoefficientDouble(n, k)
}
