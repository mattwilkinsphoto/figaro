/*
 * ProbQueryAlgorithm.scala
 * Algorithms that compute conditional probabilities of queries.
 * 
 * Created By:      Avi Pfeffer (apfeffer@cra.com)
 * Creation Date:   Jan 1, 2009
 * 
 * Copyright 2017 Avrom J. Pfeffer and Charles River Analytics, Inc.
 * See http://www.cra.com or email figaro@cra.com for information.
 * 
 * See http://www.github.com/p2t2/figaro for a copy of the software license.
 */

/*
 * Additional Updates from our community
 * 
 * Cagdas Senol		Jul 3, 2016
 * Cagdas Senol		Jul 16, 2016
 */

package com.cra.figaro.algorithm

import com.cra.figaro.language._
import scala.language.higherKinds

/**
 * Algorithms that compute conditional probabilities of queries over elements in a universe.
 */
trait ProbQueryAlgorithm extends BaseProbQueryAlgorithm[Element[?], Element] {
  
  def universe: Universe
  
  /**
   * Return an element representing the posterior probability distribution of the given element.
   */
  def posteriorElement[T](target: Element[T], universe: Universe = Universe.universe): Element[T] = {
    Select(distribution(target).toList*)(using "", universe)
  }

  universe.registerAlgorithm(this)
}


/**
 * Algorithms that compute conditional probabilities of queries. This is a base trait, to provide 
 * support for both elements in a single universe, or references across multiple universes.
 * Generic type U is either Element or Reference; Q is its heterogeneous target
 * supertype (Element[?] or Reference[?]). The bound keeps every U[T] within Q
 * without applying a wildcard to an abstract higher-kinded type.
 */
trait BaseProbQueryAlgorithm[Q, U[_] <: Q]
  extends Algorithm {
  
  class NotATargetException[T](target: U[T]) extends AlgorithmException
  /*
   * @param targets List of elements that can be queried after running the algorithm.
   */
  def queryTargets: Seq[Q]
  /*
   * Particular implementations of algorithm must provide the following two methods.
   */

  /**
   * Return an estimate of the marginal probability distribution over the target that lists each element
   * with its probability. The result is a lazy stream. It is up to the algorithm how the stream is
   * ordered.
   */
  def computeDistribution[T](target: U[T]): LazyList[(Double, T)]

  /**
   * Return an estimate of the expectation of the function under the marginal probability distribution
   * of the target.
   */
  def computeExpectation[T](target: U[T], function: T => Double): Double

  /**
   * Return an estimate of the probability of the predicate under the marginal probability distribution
   * of the target.
   */
  def computeProbability[T](target: U[T], predicate: T => Boolean): Double = {
    computeExpectation(target, (t: T) => if (predicate(t)) 1.0; else 0.0)
  }

  protected[algorithm] def computeProjection[T](target: U[T]): List[(T, Double)] = {
    projectDistribution(computeDistribution(target))
  }
  
  private def projectDistribution[T](distribution: LazyList[(Double, T)]): List[(T, Double)] = {
    (distribution map (_.swap)).toList
  }
    

  /*
   * The following methods are defined in either the onetime or anytime versions of this class, 
   * and do not need to be defined by particular algorithm implementations.
   */

  protected def doDistribution[T](target: U[T]): LazyList[(Double, T)]

  protected def doExpectation[T](target: U[T], function: T => Double): Double

  protected def doProbability[T](target: U[T], predicate: T => Boolean): Double

  protected def doProjection[T](target: U[T]): List[(T, Double)] = {
    projectDistribution(doDistribution(target))
  }

  protected def check[T](target: U[T]): Unit = {
    if (!active) throw new AlgorithmInactiveException
    if (!(queryTargets contains target)) throw new NotATargetException(target)
  }

  /**
   * Return an estimate of the marginal probability distribution over the target that lists each element
   * with its probability. The result is a lazy stream. It is up to the algorithm how the stream is
   * ordered.
   * Throws NotATargetException if called on a target that is not in the list of
   * targets of the algorithm.
   * Throws AlgorithmInactiveException if the algorithm is inactive.
   */
  def distribution[T](target: U[T]): LazyList[(Double, T)] = {
    check(target)
    doDistribution(target)
  }

  /**
   * Return an estimate of the expectation of the function under the marginal probability distribution
   * of the target.
   * Throws NotATargetException if called on a target that is not in the list of
   * targets of the algorithm.
   * Throws AlgorithmInactiveException if the algorithm is inactive.
   */
  def expectation[T](target: U[T], function: T => Double): Double = {
    check(target)
    doExpectation(target, function)
  }

  /**
    * Return an estimate of the expectation of the function under the marginal probability distribution
    * of the target.
    * Throws NotATargetException if called on a target that is not in the list of
    * targets of the algorithm.
    * Throws AlgorithmInactiveException if the algorithm is inactive.
    */
  def expectation[T](target: U[T])(function: T => Double, c: Any = DummyImplicit): Double = {
    check(target)
    doExpectation(target, function)
  }

  /**
   * Return the mean of the probability density function for the given continuous element.
   */
  def mean(target: U[Double]): Double = {
    expectation(target, (d: Double) => d)
  }

  /**
   * Return the variance of the probability density function for the given continuous element.
   */
  def variance(target: U[Double]): Double = {
    val m = mean(target)
    val ex2 = expectation(target, (d: Double) => d * d)
    ex2 - m*m
  }
  
  /**
   * Return an estimate of the probability of the predicate under the marginal probability distribution
   * of the target.
   * Throws NotATargetException if called on a target that is not in the list of
   * targets of the algorithm.
   * Throws AlgorithmInactiveException if the algorithm is inactive.
   */
  def probability[T](target: U[T], predicate: T => Boolean): Double = {
    check(target)
    doProbability(target, predicate)
  }

  /**
    * Return an estimate of the probability of the predicate under the marginal probability distribution
    * of the target.
    * Throws NotATargetException if called on a target that is not in the list of
    * targets of the algorithm.
    * Throws AlgorithmInactiveException if the algorithm is inactive.
    */
  def probability[T](target: U[T])(predicate: T => Boolean, c: Any = DummyImplicit): Double = {
    probability(target, predicate)
  }


  /**
   * Return an estimate of the probability that the target produces the value.
   * Throws NotATargetException if called on a target that is not in the list of
   * targets of the algorithm.
   * Throws AlgorithmInactiveException if the algorithm is inactive.
   */
  def probability[T](target: U[T], value: T): Double = {
    check(target)
    doProbability(target, (t: T) => t == value)
  }
}


trait StreamableProbQueryAlgorithm extends ProbQueryAlgorithm {
	/**
	 * Sample an value from the posterior of this element
	 */
	def sampleFromPosterior[T](element: Element[T]): LazyList[T]
}

