/*
 * Binomial.scala
 * Elements representing binomial distributions.
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

import com.cra.figaro.algorithm.ValuesMaker
import com.cra.figaro.algorithm.lazyfactored.ValueSet
import com.cra.figaro.algorithm.factored.factors._
import com.cra.figaro.language._
import com.cra.figaro.library.atomic.continuous._

/**
 * A binomial distribution in which the parameters are constants.
 */
class AtomicBinomial(name: Name[Int], val numTrials: Int, val probSuccess: Double, collection: ElementCollection)
  extends Element[Int](name, collection) with Atomic[Int] with ValuesMaker[Int] with Cacheable[Int]
  with OneShifter with HasLogDensity[Int] {
  require(numTrials>=0)
  com.cra.figaro.library.atomic.DistributionNumerics.probability(probSuccess)
  protected lazy val lowerBound = 0
  protected lazy val upperBound = numTrials

  // Commons Math inverse-CDF sampling, driven exclusively by the scoped Figaro RNG.
  def generateRandomness() = {
    com.cra.figaro.library.atomic.DistributionNumerics.check()
    if(probSuccess==0 || numTrials==0) 0 else if(probSuccess==1) numTrials
    else new org.apache.commons.math3.distribution.BinomialDistribution(LegacyCountRandom.adapter(),numTrials,probSuccess).sample()
  }

  /**
   * The Metropolis-Hastings proposal is to increase or decrease the value of by 1.
   */
  override def nextRandomness(rand: Randomness) = shiftOne(rand)

  def generateValue(rand: Int) = rand

  /**
   * Probability of a value.
   */
  def logDensity(k: Int): Double = com.cra.figaro.library.atomic.LegacyDensity.binomial(numTrials,probSuccess,k)
  override def density(k: Int) = math.exp(logDensity(k))

  /**
   * Return the range of values of the element.
   */
  def makeValues(depth: Int) = ValueSet.withoutStar((for { i <- 0 to numTrials } yield i).toSet)

  /**
   * Convert an element into a list of factors.
   */
//  def makeFactors = {
//    val binVar = Variable(this)
//    val factor = Factory.make[Double](List(binVar))
//    for { (xvalue, index) <- binVar.range.zipWithIndex } {
//      factor.set(List(index), density(xvalue.value))
//    }
//    List(factor)
//  }

  override def toString = "Binomial(" + numTrials + ", " + probSuccess + ")"
}

/**
 * A binomial distribution in which the number of trials is fixed and the success probability is an element.
 */
class BinomialFixedNumTrials(name: Name[Int], val numTrials: Int, val probSuccess: Element[Double], collection: ElementCollection)
  extends NonCachingChain[Double, Int](name, probSuccess, (p: Double) => new AtomicBinomial("", numTrials, p, collection), collection) {
  override def toString = "Binomial(" + numTrials + ", " + probSuccess + ")"
}

 /**
 * A binomial with a fixed number of trials parameterized by a beta distribution.
 */
class ParameterizedBinomialFixedNumTrials(name: Name[Int], val numTrials: Int, override val parameter: AtomicBeta, collection: ElementCollection)
  extends CachingChain[Double, Int](name, parameter, (p: Double) => new AtomicBinomial("", numTrials, p, collection), collection)
  with SingleParameterized[Int] {
  override def distributionToStatistics(distribution: LazyList[(Double, Int)]): Seq[Double] = {
    val distList = distribution.toList
    var totalPos = 0.0
    var totalNeg = 0.0
    for { i <- 0 to numTrials } {
      distList.find(_._2 == i) match {
        case Some((prob, _)) =>
          totalPos += prob * i
          totalNeg += prob * (numTrials - i)
        case None => ()
      }
    }
    List(totalPos, totalNeg)
  }

  def density(value: Int): Double = {
    val probSuccess = parameter.value
    if (value < 0 || value > numTrials) 0.0
    else com.cra.figaro.library.atomic.discrete.Util.binomialDensity(numTrials, probSuccess, value)
  }

 override def toString = "ParameterizedBinomial(" + numTrials + ", " + parameter + ")"
}

/**
 * A binomial distribution in which the parameters are elements.
 */
class CompoundBinomial(name: Name[Int], val numTrials: Element[Int], val probSuccess: Element[Double], collection: ElementCollection)
  extends CachingChain[Int, Int](
    name,
    numTrials,
    (n: Int) => new NonCachingChain(
      "",
      probSuccess,
      (p: Double) => new AtomicBinomial("", n, p, collection),
      collection),
    collection) {
  override def toString = "Binomial(" + numTrials + ", " + probSuccess + ")"
}

object Binomial extends Creatable {
  /**
   * Create a binomial distribution in which the parameters are constants.
   */
  def apply(n: Int, p: Double)(implicit name: Name[Int], collection: ElementCollection) =
    new AtomicBinomial(name, n, p, collection)

  /**
   * Create a binomial distribution in which the number of trials is fixed and the success probability is an element.
   *
   * If the element is an atomic beta element, the flip uses that element
   * as a learnable parameter.
   */
  def apply(n: Int, p: Element[Double])(implicit name: Name[Int], collection: ElementCollection) = {
    if (p.isInstanceOf[AtomicBeta])
    new ParameterizedBinomialFixedNumTrials(name, n, p.asInstanceOf[AtomicBeta], collection)
    else new BinomialFixedNumTrials(name, n, p, collection)
  }

  /**
   * Create a binomial distribution in which the parameters are elements.
   */
  def apply(n: Element[Int], p: Element[Double])(implicit name: Name[Int], collection: ElementCollection) =
    new CompoundBinomial(name, n, p, collection)

  type ResultType = Int

  def create(args: List[Element[?]]) = apply(args(0).asInstanceOf[Element[Int]], args(1).asInstanceOf[Element[Double]])
}
