/*
 * Commons Math adapters preserving the parameter conventions used by legacy tests.
 *
 * Copyright 2017 Avrom J. Pfeffer and Charles River Analytics, Inc.
 * See LICENSE and FigaroAttributions.txt.
 */

package com.cra.figaro.test.modernization.oracles

import org.apache.commons.math3.distribution.{
  BetaDistribution => CommonsBetaDistribution,
  BinomialDistribution => CommonsBinomialDistribution,
  ExponentialDistribution => CommonsExponentialDistribution,
  GammaDistribution => CommonsGammaDistribution,
  GeometricDistribution => CommonsGeometricDistribution,
  NormalDistribution => CommonsNormalDistribution,
  PoissonDistribution => CommonsPoissonDistribution
}

trait ProbabilityDistribution {
  def cumulative(value: Double): Double
  def probability(value: Double): Double
}

final class NormalDistribution(mean: Double, variance: Double) extends ProbabilityDistribution {
  private val delegate = new CommonsNormalDistribution(mean, math.sqrt(variance))
  def cumulative(value: Double): Double = delegate.cumulativeProbability(value)
  def probability(value: Double): Double = delegate.density(value)
}

final class ExponentialDistribution(rate: Double) extends ProbabilityDistribution {
  private val delegate = new CommonsExponentialDistribution(1.0 / rate)
  def cumulative(value: Double): Double = delegate.cumulativeProbability(value)
  def probability(value: Double): Double = delegate.density(value)
}

final class GammaDistribution(shape: Double) extends ProbabilityDistribution {
  private val delegate = new CommonsGammaDistribution(shape, 1.0)
  def cumulative(value: Double): Double = delegate.cumulativeProbability(value)
  def probability(value: Double): Double = delegate.density(value)
}

final class BetaDistribution(alpha: Double, beta: Double) extends ProbabilityDistribution {
  private val delegate = new CommonsBetaDistribution(alpha, beta)
  def cumulative(value: Double): Double = delegate.cumulativeProbability(value)
  def probability(value: Double): Double = delegate.density(value)
}

final class GeometricDistribution(successProbability: Double) {
  private val delegate = new CommonsGeometricDistribution(successProbability)

  // Legacy Figaro/JSci tests count trials starting at one; Commons Math counts failures from zero.
  def probability(trial: Double): Double = delegate.probability(trial.toInt - 1)
}

final class PoissonDistribution(mean: Double) {
  private val delegate = new CommonsPoissonDistribution(mean)
  def probability(value: Int): Double = delegate.probability(value)
  def probability(value: Double): Double = delegate.probability(value.toInt)
}

final class BinomialDistribution(trials: Int, successProbability: Double) {
  private val delegate = new CommonsBinomialDistribution(trials, successProbability)
  def probability(value: Int): Double = delegate.probability(value)
}
