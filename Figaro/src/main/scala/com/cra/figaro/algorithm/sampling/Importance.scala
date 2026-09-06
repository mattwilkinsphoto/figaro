/*
 * Importance.scala
 * Importance sampler.
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
 * Kartik Thakore	Jul 21, 2015
 */

package com.cra.figaro.algorithm.sampling

import com.cra.figaro.algorithm._
import com.cra.figaro.language._
import com.cra.figaro.util._
import scala.annotation.tailrec
import scala.collection.mutable.{ Set, Map }
import com.cra.figaro.experimental.particlebp.AutomaticDensityEstimator
import com.cra.figaro.algorithm.factored.ParticleGenerator
import com.cra.figaro.algorithm.sampling.parallel.ParImportance
import com.cra.figaro.library.cache.PermanentCache
import com.cra.figaro.library.collection.Container

/**
 * Importance samplers.
 */
abstract class Importance(universe: Universe, targets: Element[?]*)
  extends WeightedSampler(universe, targets*) {
  import Importance.State

  val lw = new LikelihoodWeighter(universe, new PermanentCache(universe))

  private var numRejections = 0
  private var logSuccessWeight = 0.0
  private var numSamples = 0
  def getSamples() = numSamples

  /** Extension hook invoked for every attempt, including retries after rejected evidence. */
  protected def checkSamplingInterrupted(): Unit = ()

  override protected def resetCounts(): Unit = {
    super.resetCounts()
    numRejections = 0
    logSuccessWeight = 0.0
    numSamples = 0
  }

  override def kill (): Unit = {
    super.kill()
    lw.clearCache()
    lw.deregisterDependencies()
    universe.deregisterAlgorithm(this)
  }

  /*
   * Produce one weighted sample of the given element. weightedSample takes into account conditions and constraints
   * on all elements in the Universe, including those that depend on this element.
   */
  @tailrec final def sample(): Sample = {
    checkSamplingInterrupted()
    /*
     * We need to recreate the activeElements each sample, because non-temporary elements may have been made active
     * in a previous iteration. See the relevant test in ImportanceTest.
     */
    val activeElements = universe.activeElements
    val resultOpt: Option[Sample] =
      try {
        val weight = lw.computeWeight(activeElements)
        val bindings = targets map (elem => elem -> elem.value)
        Some((weight, Map(bindings*)))
      } catch {
        case Importance.Reject =>
          None
      }

    universe.clearTemporaries()
    resultOpt match {
      case Some(x) =>
        logSuccessWeight = logSum(logSuccessWeight, x._1)
        numSamples += 1
        x
      case None =>
        numRejections += 1
        sample()
    }
  }

  /**
   * The computed probability of evidence.
   */
  def logProbEvidence: Double = {
    logSuccessWeight - Math.log(numSamples + numRejections)
  }

}

object Importance {
  /*
   * An element cannot be assigned more than once during importance sampling. If an element has been assigned,
   * its assigned value will be held in its value field. A state consists of the set of variables that have
   * been assigned, together with the accumulated weight so far. */
  /**
   * Convenience class to store the set of sampled elements, along with the current sampling weight.
   */
  case class State(assigned: Set[Element[?]] = Set(), var weight: Double = 0.0)

  object Reject extends RuntimeException

  /**
   * Create an anytime importance sampler with the given target query elements over the given universe.
   */
  def apply(targets: Element[?]*)(implicit universe: Universe) =
    new Importance(universe, targets*) with AnytimeProbQuerySampler

  /**
   * Create an one-time importance sampler with the given target query elements over the given universe
   * using the given number of samples.
   */
  def apply(myNumSamples: Int, targets: Element[?]*)(implicit universe: Universe) =
    new Importance(universe, targets*) with OneTimeProbQuerySampler with ProbEvidenceQuery {
      val numSamples = myNumSamples

      /**
       * Use one-time sampling to compute the probability of the given named evidence.
       * Takes the conditions and constraints in the model as part of the model definition.
       * This method takes care of creating and running the necessary algorithms.
       */
      override def probabilityOfEvidence(evidence: List[NamedEvidence[?]]): Double = {
        val logPartition = logProbEvidence
        this.universe.assertEvidence(evidence)
        if (active) kill()
        start()
        Math.exp(logProbEvidence - logPartition)
      }

    }

  /**
   * Use IS to compute the probability that the given element satisfies the given predicate.
   */
  def probability[T](target: Element[T], predicate: T => Boolean)(implicit universe: Universe): Double = {
    val alg = Importance(10000, target)(using universe)
    alg.start()
    val result = alg.probability(target, predicate)
    alg.kill()
    result
  }

  /**
   * Use IS to compute the probability that the given element has the given value.
   */
  def probability[T](target: Element[T], value: T)(implicit universe: Universe): Double =
    probability(target, (t: T) => t == value)(using universe)

    /**
     * Use IS to sample the joint posterior distribution of several variables
     */
  def sampleJointPosterior(targets: Element[?]*)(implicit universe: Universe): LazyList[List[Any]] = {
    val jointElement = Container[Any](targets.asInstanceOf[Seq[Element[Any]]]*).foldLeft(List[Any]())((l: List[Any], i: Any) => l :+ i)
    val alg = Importance(10000, jointElement)(using universe)
    alg.start()
    val posterior = alg.sampleFromPosterior(jointElement)
    alg.kill()
    posterior
  }

  /**
   * The parallel implementation of IS
   */
  def par = ParImportance

}
