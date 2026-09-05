/*
 * Gibbs.scala
 * A Gibbs sampler that operates on factor graphs.
 *
 * Created By:      William Kretschmer (kretsch@mit.edu)
 * Creation Date:   June 3, 2015
 *
 * Copyright 2017 Avrom J. Pfeffer and Charles River Analytics, Inc.
 * See http://www.cra.com or email figaro@cra.com for information.
 *
 * See http://www.github.com/p2t2/figaro for a copy of the software license.
 */

package com.cra.figaro.algorithm.factored.gibbs

import com.cra.figaro.algorithm.factored._
import com.cra.figaro.algorithm.factored.factors._
import com.cra.figaro.algorithm.lazyfactored._
import com.cra.figaro.algorithm.{ AlgorithmException, UnsupportedAlgorithmException }
import com.cra.figaro.algorithm.sampling._
import com.cra.figaro.language._
import com.cra.figaro.util._
import scala.annotation.tailrec
import scala.collection.mutable.{ Map => MutableMap }
import com.cra.figaro.algorithm.factored.factors.factory.Factory

trait Gibbs[T] extends BaseUnweightedSampler with FactoredAlgorithm[T] {
  /**
   * Semiring for use in factors.
   */
  val semiring: Semiring[T]

  /**
   * Elements whose samples will be recorded at each iteration.
   */
  val targetElements: List[Element[?]]

  /**
   * List of all factors.
   */
  var factors: List[Factor[T]] = scala.compiletime.uninitialized

  /**
   * Variables to sample at each time step.
   */
  var variables: Set[Variable[?]] = scala.compiletime.uninitialized

  /**
   * The most recent set of samples, used for sampling variables conditioned on the values of other variables.
   */
  val currentSamples: MutableMap[Variable[?], Int] = MutableMap()

  /**
   * Number of samples to throw away initially.
   */
  def burnIn: Int

  /**
   * Iterations thrown away between samples.
   */
  def interval: Int

  /**
   * Method to create a blocking scheme given information about the model and factors.
   */
  def createBlocks(): List[Gibbs.Block]
}

/**
 * The default trait for creating blocks. Works on Chain, Apply, Atomic, and Constant elements.
 * Note that our choice of blocking scheme fails when the results of a Chain have disjoint ranges.
 * We are forced to do this to prevent exponentially slow time performance on models with Chains.
 * A more detailed explanation is available in the FactorProduct trait in BlockSampler.scala.
 */
trait ChainApplyBlockingGibbs extends Gibbs[Double] {
  // Maps a variable to its deterministic parents
  def variableParentMap(): Map[Variable[?], Set[Variable[?]]] = variables.map(_ match {
    case ev: ElementVariable[_] => ev.element match {
      // For Chain, we treat all of the result variables as deterministic parents
      case c: Chain[_, _] => {
      /*
       * Must ensure this code is not called in SFI!
       */
        val chainResults: Set[Variable[?]] = LazyValues(universe).getMap(c).values.map(Variable(_)).toSet
        (ev, chainResults)
      }
      // For Apply, we take the deterministic parents to be its arguments
      case a: Apply[_] => (ev, a.args.map(Variable(_)).toSet)
      case a: Atomic[_] => (ev, Set[Variable[?]]())
      case c: Constant[_] => (ev, Set[Variable[?]]())
      case _ => throw new UnsupportedAlgorithmException(ev.element)
    }

    // This handles internal variables in Chains
    // We treat all of the result variables, as well as the parent variable, as deterministic parents
    case icv: InternalChainVariable[_] => {
      val chain = icv.chain.asInstanceOf[Chain[?, ?]]
      /*
       * Must ensure this code is not called in SFI!
       */
      val chainResults: Set[Variable[?]] = LazyValues(universe).getMap(chain).values.map(Variable(_)).toSet
      (icv, chainResults + Variable(chain.parent))
    }

    // Otherwise, we assume (perhaps incorrectly) that the variable is stochastic
    // It will not be blocked with any parents
    case v => (v, Set[Variable[?]]())
  }).toMap

  def createBlocks(): List[Gibbs.Block] = {
    val variableParents = variableParentMap()
    // Maps each variable to its deterministic children, i.e. variables that should be included in a block with this variable
    val variableChildren: Map[Variable[?], Set[Variable[?]]] =
      variables.map(v => v -> variables.filter(variableParents(_).contains(v))).toMap

    // Start with the purely stochastic variables with no parents
    val starterVariables = variables.filter(variableParents(_).isEmpty)

    @tailrec // Recursively add deterministic children to the block
    def expandBlock(expand: Set[Variable[?]], block: Set[Variable[?]] = Set()): Gibbs.Block = {
      if (expand.isEmpty) block.toList
      else {
        val expandNext = expand.flatMap(variableChildren(_))
        expandBlock(expandNext, block ++ expand)
      }
    }
    starterVariables.map(v => expandBlock(Set(v))).toList
  }
}

trait ProbabilisticGibbs extends Gibbs[Double] {
  class StarSampleException(elem: Element[?]) extends AlgorithmException

  val semiring = LogSumProductSemiring()

  protected var blockSamplers: List[BlockSampler] = scala.compiletime.uninitialized

  def getFactors(neededElements: List[Element[?]], targetElements: List[Element[?]], upperBounds: Boolean = false): List[Factor[Double]] = {
    val thisUniverseFactors = neededElements flatMap(Factory.makeFactorsForElement(_))
    val dependentUniverseFactors =
      for { (dependentUniverse, evidence) <- dependentUniverses } yield Factory.makeDependentFactor(Variable.cc, universe, dependentUniverse, dependentAlgorithm(dependentUniverse, evidence))
    // Make logarithmic to prevent underflow in product computations
    (dependentUniverseFactors ::: thisUniverseFactors).map(_.mapTo(Math.log, LogSumProductSemiring()))
  }

  // Update the map of samples one block at a time, conditioned on intermediate samples of all the other variables
  // i.e. perform a single step of blocked Gibbs sampling
  def sampleAllBlocks(): Unit = {
    blockSamplers.foreach(_.sample(currentSamples))
  }

  // Perform an iteration of sampling and record the results
  def sample(): (Boolean, Sample) = {
    sampleAllBlocks()
    val result = targetElements.map(e => {
      val variable = Variable(e)
      val extended = variable.range(currentSamples(variable))
      // Accept the sample unless it is star
      if (extended.isRegular) (e, extended.value)
      else throw new StarSampleException(e)
    })
    (true, MutableMap(result*))
  }

  protected override def doSample() = {
    // This takes care of the thinning defined by interval
    for (i <- 1 until interval) {
      sampleAllBlocks()
    }
    super.doSample()
  }
}

abstract class ProbQueryGibbs(override val universe: Universe, targets: Element[?]*)(
  val dependentUniverses: List[(Universe, List[NamedEvidence[?]])],
  val dependentAlgorithm: (Universe, List[NamedEvidence[?]]) => () => Double,
  val burnIn: Int, val interval: Int,
  val blockToSampler: Gibbs.BlockSamplerCreator, upperBounds: Boolean = false)
  extends BaseUnweightedSampler(universe, targets*)
  with ProbabilisticGibbs with UnweightedSampler {

  val targetElements = targets.toList

  override def initialize() = {
    super.initialize()
    // Get the needed elements, factors, and blocks
    val neededElements = getNeededElements(targetElements, Int.MaxValue)._1
    factors = getFactors(neededElements, targetElements, upperBounds)
    variables = factors.flatMap(_.variables).toSet
    val blocks = createBlocks()
    // Create block samplers
    blockSamplers = blocks.map(block => blockToSampler((block, factors.filter(_.variables.exists(block.contains(_))))))
    // Initialize the samples to a valid state and take the burn-in samples
    val initialSample = WalkSAT(factors, variables, semiring, chainMapper)
    variables.foreach(v => currentSamples(v) = initialSample(v))
    for (_ <- 1 to burnIn) sampleAllBlocks()
  }
  
  def chainMapper(chain: Chain[?,?]): Set[Variable[?]] = LazyValues(chain.universe).getMap(chain).values.map(Variable(_)).toSet
}

object Gibbs {
  // A block is just a list of variables
  type Block = List[Variable[?]]

  // Information passed to BlockSampler constructor
  type BlockInfo = (Block, List[Factor[Double]])

  // Constructor for BlockSampler
  type BlockSamplerCreator = BlockInfo => BlockSampler

  /**
   * Create a one-time Gibbs sampler using the given number of samples and target elements.
   */
  def apply(mySamples: Int, targets: Element[?]*)(implicit universe: Universe) =
    new ProbQueryGibbs(universe, targets*)(
      List(),
      (u: Universe, e: List[NamedEvidence[?]]) => () => ProbEvidenceSampler.computeProbEvidence(10000, e)(using u),
      0, 1, BlockSampler.default) with OneTimeProbQuerySampler with ChainApplyBlockingGibbs { val numSamples = mySamples }

  /**
   * Create a one-time Gibbs sampler using the given number of samples, the number of samples to burn in,
   * the sampling interval, the BlockSampler generator, and target elements.
   */
  def apply(mySamples: Int, burnIn: Int, interval: Int, blockToSampler: BlockSamplerCreator, targets: Element[?]*)(implicit universe: Universe) =
    new ProbQueryGibbs(universe, targets*)(
      List(),
      (u: Universe, e: List[NamedEvidence[?]]) => () => ProbEvidenceSampler.computeProbEvidence(10000, e)(using u),
      burnIn, interval, blockToSampler) with OneTimeProbQuerySampler with ChainApplyBlockingGibbs { val numSamples = mySamples }

  /**
   * Create a one-time Gibbs sampler using the given dependent universes and algorithm,
   * the number of samples, the number of samples to burn in,
   * the sampling interval, the BlockSampler generator, and target elements.
   */
  def apply(dependentUniverses: List[(Universe, List[NamedEvidence[?]])],
    dependentAlgorithm: (Universe, List[NamedEvidence[?]]) => () => Double,
    mySamples: Int, burnIn: Int, interval: Int, blockToSampler: BlockSamplerCreator, targets: Element[?]*)(implicit universe: Universe) =
    new ProbQueryGibbs(universe, targets*)(
      dependentUniverses,
      dependentAlgorithm,
      burnIn, interval, blockToSampler) with OneTimeProbQuerySampler with ChainApplyBlockingGibbs { val numSamples = mySamples }

  /**
   * Create an anytime Gibbs sampler using the given target elements.
   */
  def apply(targets: Element[?]*)(implicit universe: Universe) =
    new ProbQueryGibbs(universe, targets*)(
      List(),
      (u: Universe, e: List[NamedEvidence[?]]) => () => ProbEvidenceSampler.computeProbEvidence(10000, e)(using u),
      0, 1, BlockSampler.default) with AnytimeProbQuerySampler with ChainApplyBlockingGibbs

  /**
   * Create an anytime Gibbs sampler using the given number of samples to burn in,
   * the sampling interval, the BlockSampler generator, and target elements.
   */
  def apply(burnIn: Int, interval: Int, blockToSampler: BlockSamplerCreator, targets: Element[?]*)(implicit universe: Universe) =
    new ProbQueryGibbs(universe, targets*)(
      List(),
      (u: Universe, e: List[NamedEvidence[?]]) => () => ProbEvidenceSampler.computeProbEvidence(10000, e)(using u),
      burnIn, interval, blockToSampler) with AnytimeProbQuerySampler with ChainApplyBlockingGibbs

  /**
   * Create an anytime Gibbs sampler using the given dependent universes and algorithm,
   * the number of samples to burn in, the sampling interval,
   * the BlockSampler generator, and target elements.
   */
  def apply(dependentUniverses: List[(Universe, List[NamedEvidence[?]])],
    dependentAlgorithm: (Universe, List[NamedEvidence[?]]) => () => Double,
    burnIn: Int, interval: Int, blockToSampler: BlockSamplerCreator, targets: Element[?]*)(implicit universe: Universe) =
    new ProbQueryGibbs(universe, targets*)(
      dependentUniverses,
      dependentAlgorithm,
      burnIn, interval, blockToSampler) with AnytimeProbQuerySampler with ChainApplyBlockingGibbs

  /**
   * Use Gibbs sampling to compute the probability that the given element satisfies the given predicate.
   */
  def probability[T](target: Element[T], predicate: T => Boolean): Double = {
    val alg = Gibbs(10000, target)
    alg.start()
    val result = alg.probability(target, predicate)
    alg.kill()
    result
  }

  /**
   * Use Gibbs sampling to compute the probability that the given element has the given value.
   */
  def probability[T](target: Element[T], value: T): Double =
    probability(target, (t: T) => t == value)
}
