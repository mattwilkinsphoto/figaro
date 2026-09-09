/*
 * Poisson.scala
 * Elements representing Poisson distributions.
 * 
 * Created By:      Avi Pfeffer (apfeffer@cra.com)
 * Creation Date:   Feb 25, 2011
 * 
 * Copyright 2017 Avrom J. Pfeffer and Charles River Analytics, Inc.
 * See http://www.cra.com or email figaro@cra.com for information.
 * 
 * See http://www.github.com/p2t2/figaro for a copy of the software license.
 */

package com.cra.figaro.library.atomic.discrete

import com.cra.figaro.language._
import com.cra.figaro.util._
import annotation.tailrec
import scala.math.{ exp, pow }
import com.cra.figaro.util.SpecialFunctions.{ factorial, logFactorial }

/**
 * A Poisson distribution in which the parameter is constant.
 */
class AtomicPoisson(name: Name[Int], lambda: Double, collection: ElementCollection)
  extends Element[Int](name, collection) with Atomic[Int] with OneShifter with Cacheable[Int] with HasLogDensity[Int] {
  require(lambda.isFinite && lambda>=0 && lambda<=1e8,"Poisson rate must be in [0,1e8] for Int-valued draws")
  protected lazy val lowerBound = 0
  protected lazy val upperBound = Int.MaxValue

  // Commons Math Poisson sampler, with bounded requests to the scoped Figaro RNG.
  def generateRandomness() = {
    com.cra.figaro.library.atomic.DistributionNumerics.check()
    if(lambda==0) 0 else {
      val x=new org.apache.commons.math3.distribution.PoissonDistribution(LegacyCountRandom.adapter(),lambda,1e-12,10000).sample()
      if(x==Int.MaxValue) throw new ArithmeticException("Poisson draw outside Int range")
      x
    }
  }

  /**
   * The Metropolis-Hastings proposal is to increase or decrease the value of by 1.
   */
  override def nextRandomness(rand: Randomness) = shiftOne(rand)

  def generateValue(rand: Int) = rand

  /**
   * Probability of a value.
   */
  def logDensity(k: Int): Double = com.cra.figaro.library.atomic.LegacyDensity.poisson(lambda,k)
  override def density(k: Int) = math.exp(logDensity(k))
  override def toString = "Poisson(" + lambda + ")"
}

/**
 * A Possion distribution in which the parameter is an element.
 */
class CompoundAtomic(name: Name[Int], lambda: Element[Double], collection: ElementCollection)
  extends NonCachingChain(
    name,
    lambda,
    (l: Double) => new AtomicPoisson("", l, collection),
    collection) {
  override def toString = "Poisson(" + lambda + ")"
}

object Poisson extends Creatable {
  /**
   * Create a Poisson distribution in which the parameter is a constant.
   */
  def apply(lambda: Double)(implicit name: Name[Int], collection: ElementCollection) =
    new AtomicPoisson(name, lambda, collection)

  /**
   * Create a Poisson distribution in which the parameter is an element.
   */
  def apply(lambda: Element[Double])(implicit name: Name[Int], collection: ElementCollection) =
    new CompoundAtomic(name, lambda, collection)

  type ResultType = Int

  def create(args: List[Element[?]]) = apply(args(0).asInstanceOf[Element[Double]])
}
