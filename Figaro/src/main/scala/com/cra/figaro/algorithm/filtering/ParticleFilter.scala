/*
 * ParticleFilter.scala
 * Particle Filtering
 * 
 * Created By:      Avi Pfeffer (apfeffer@cra.com)
 * Creation Date:   Jan 1, 2009
 * 
 * Copyright 2017 Avrom J. Pfeffer and Charles River Analytics, Inc.
 * See http://www.cra.com or email figaro@cra.com for information.
 * 
 * See http://www.github.com/p2t2/figaro for a copy of the software license.
 */

package com.cra.figaro.algorithm.filtering

import com.cra.figaro.algorithm.sampling._
import com.cra.figaro.language._
import com.cra.figaro.util._
import com.cra.figaro.library.cache.PermanentCache
import com.cra.figaro.library.cache.Cache
import com.cra.figaro.algorithm.sampling.LikelihoodWeighter

/**
 * An abstract class of particle filters.
 * A particle filter is provided with three models:
 * a static model, containing a universe defining a distribution over static elements that do not change over time;
 * an initial model, containing a universe defining a distribution over the initial state of time-varying elements;
 * and a transition model, which is a function from the previous universe to a new universe. defining the way the distribution over the new state
 * of the time-varying variables depends on their values in the previous state.
 * The fourth argument to the particle filter is the number of particles to use at each time step.
 *
 * The particle filter works in an online fashion. At each point in time, it maintains its current beliefs about the state of the system as a set of
 * representative states. advanceTime is used to move forward one time step. The particle filter updates its beliefs in light
 * of the new evidence.
 *
 * @param static A universe with static elements that do not change over time.
 * @param intitial The universe describing the initial distribution of the model.
 * @param transition The transition function that returns a new universe from a static and previous universe, respectively.
 */
trait ParticleFilter {

  val numParticles: Int

  /** The belief about the state of the system at the current point in time. */
  val beliefState: ParticleFilter.BeliefState = Array.fill(numParticles)(null)

  protected var logProbEvidence: Double = 0.0

  /** Policy for genuinely zero weights, not for small finite log weights. */
  def zeroWeightPolicy: ParticleFilter.ZeroWeightPolicy = ParticleFilter.ZeroWeightPolicy.Fail
  private var failedUpdate: Option[Throwable] = None
  private def checkBelief(): Unit = failedUpdate.foreach { cause =>
    throw new IllegalStateException("The last particle-filter update failed; no posterior is available", cause)
  }

  /** Failed filters must be discarded; partial updates are not transactional rollbacks. */
  protected final def guardedUpdate(body: => Unit): Unit = {
    checkBelief()
    try body catch { case scala.util.control.NonFatal(e) => failedUpdate = Some(e); throw e }
  }

  /** Run a complete log-weight update. Failed updates cannot be queried as posteriors. */
  protected def logTimeStep(create: => Seq[ParticleFilter.LogWeightedParticle]): Unit = {
    try {
      val particles = create
      require(numParticles > 0 && particles.size == numParticles, "Positive, matching particle count required")
      require(particles.forall(p => !p.logWeight.isNaN && p.logWeight != Double.PositiveInfinity),
        "Invalid particle log weight: NaN or positive infinity")
      val maximum = particles.map(_.logWeight).max
      if (maximum == Double.NegativeInfinity) {
        if (zeroWeightPolicy == ParticleFilter.ZeroWeightPolicy.Fail)
          throw new ParticleFilter.NoViableParticles
        updateBeliefState(particles.map(p => (0.0, p.state)))
        logProbEvidence = Double.NegativeInfinity
      } else {
        val weights = particles.map(p => math.exp(p.logWeight - maximum))
        val pivot = weights.indexOf(1.0)
        val remainder = weights.indices.iterator.filter(_ != pivot).map(weights).sum
        val increment = maximum + math.log1p(remainder) - math.log(numParticles.toDouble)
        val nextEvidence = logProbEvidence + increment
        require(increment.isFinite && (nextEvidence.isFinite || logProbEvidence == Double.NegativeInfinity),
          "Accumulated log evidence is not representable")
        updateBeliefState(weights.zip(particles.map(_.state)))
        logProbEvidence = nextEvidence
      }
      failedUpdate = None
    } catch {
      case scala.util.control.NonFatal(e) =>
        failedUpdate = Some(e)
        throw e
    }
  }

  /**
   * Returns the expectation of the element referred to by the reference
   * under the given function at the current time point.
   */
  def computeCurrentExpectation[T](reference: Reference[T], function: T => Double): Double = {
    checkBelief()
    val fValues: Seq[Double] = beliefState.iterator.map(state => function(state.get(reference))).toIndexedSeq
    val total = (fValues).foldRight(0.0)(_ + _)
    total.toDouble / numParticles
  }

  /**
   * Returns the distribution over the element referred to by the reference at the current time point.
   */
  def computeCurrentDistribution[T](reference: Reference[T]): LazyList[(Double, T)] = {
    checkBelief()
    val map = scala.collection.mutable.Map[T, Int]()
    for {
      state <- beliefState
    } {
      val t = state.get(reference)
      val prevCount = map.getOrElse(t, 0)
      map += t -> (prevCount + 1)
    }
    val z = 1.0 / beliefState.size
    val normalized = map.toList.map((pair: (T, Int)) => (pair._2 * z, pair._1))
    normalized.to(LazyList)
  }

  /*
   * Careful: makeWeightedParticle overwrites the previous state with the new state. That means we can't use it to generate another new particle from the same previous
   * state. The reason for this design is to avoid creating new snapshots and states to conserve memory.
   
   * TODO: previous state could be replaced by the static universe (or a universe window)
   */
  protected def makeWeightedParticle(previousState: State, currentUniverse: Universe, lw: LikelihoodWeighter): ParticleFilter.WeightedParticle = {
    val particle = makeLogWeightedParticle(previousState, currentUniverse, lw)
    (math.exp(particle.logWeight), particle.state)
  }

  /** Stable extension point used by the built-in sequential and parallel filters. */
  protected def makeLogWeightedParticle(previousState: State, currentUniverse: Universe, lw: LikelihoodWeighter): ParticleFilter.LogWeightedParticle = {
    val weight = try {
      lw.computeWeight(currentUniverse.activeElements)
    } catch {
      case Importance.Reject => Double.NegativeInfinity
    }   

    val snapshot = new Snapshot
    snapshot.store(currentUniverse)
    val state = new State(snapshot, previousState.static)
    currentUniverse.clearTemporaries()
    ParticleFilter.LogWeightedParticle(weight, state)
  }

  private[figaro] def updateBeliefState(weightedParticles: Seq[ParticleFilter.WeightedParticle]): Unit = {
    // If all the particles have weight 1, there is no need to resample
    // If all the particles have weight 0, none of them satisfy the conditions, so the best we can do is produce a uniform distribution over them.
    if (weightedParticles.forall(_._1 == 1.0) || weightedParticles.forall(_._1 == 0.0)) {
      val weightedParticleArray = weightedParticles.toArray
      for { i <- 0 until numParticles } {
        beliefState(i) = weightedParticleArray(i)._2
      }
    } else {
      val resampler = new MapResampler(weightedParticles.filter(_._1 > 0.0))
      for { i <- 0 until numParticles } {
        beliefState(i) = resampler.resample()
      }
    }
  }

  private[figaro] def computeProbEvidence(weightedParticles: Seq[ParticleFilter.WeightedParticle]): Unit = {
    // compute probability of evidence here by taking the average weight of the weighted particles and store it so you can later return it as a query result
    val weightedParticleArray = weightedParticles.toArray
    val sum = weightedParticleArray.map(_._1).sum
    logProbEvidence = logProbEvidence + scala.math.log(sum / numParticles)
  }

  protected def addWeightedParticle(evidence: Seq[NamedEvidence[?]], index: Int, universes: UniverseWindow, lw: LikelihoodWeighter): ParticleFilter.WeightedParticle = {
    val previousState = beliefState(index)
    previousState.dynamic.restore(universes.previous)
    previousState.static.restore(universes.static)
    universes.current.assertEvidence(evidence)
    val result = makeWeightedParticle(previousState, universes.current, lw)
    result
  }

  protected def initialWeightedParticle(static: Universe, current: Universe, lw: LikelihoodWeighter): ParticleFilter.WeightedParticle = {
    Forward(static)
    val staticSnapshot = new Snapshot
    staticSnapshot.store(static)
    val state = new State(new Snapshot, staticSnapshot)
    makeWeightedParticle(state, current, lw)
  }

  /** Generate a transition particle without exponentiating its likelihood. */
  protected def addLogWeightedParticle(evidence: Seq[NamedEvidence[?]], index: Int, universes: UniverseWindow, lw: LikelihoodWeighter): ParticleFilter.LogWeightedParticle = {
    val previousState = beliefState(index)
    previousState.dynamic.restore(universes.previous)
    previousState.static.restore(universes.static)
    universes.current.assertEvidence(evidence)
    makeLogWeightedParticle(previousState, universes.current, lw)
  }

  /** Generate an initial particle without exponentiating its likelihood. */
  protected def initialLogWeightedParticle(static: Universe, current: Universe, lw: LikelihoodWeighter): ParticleFilter.LogWeightedParticle = {
    Forward(static)
    val staticSnapshot = new Snapshot
    staticSnapshot.store(static)
    makeLogWeightedParticle(new State(new Snapshot, staticSnapshot), current, lw)
  }

  /*
   * Advance the universe one time step.
   * The previous universe becomes a copy of the current universe with all named elements replaced by constants.
   * This is done so we don't have to store the previous universe (and the universes previous to it), and we can release the memory.
   * 
   * Returns a tuple: (previous, current)
   */
  protected def advanceUniverse(universes: UniverseWindow, transition: (Universe, Universe) => Universe): UniverseWindow = {

    val newPrevious = Universe.createNew()
    for { element <- universes.current.activeElements.filter(!_.name.isEmpty) } {
      new Settable(element.name.string, element.value, newPrevious)
    }
    val newCurrent = transition(universes.static, newPrevious)
    new UniverseWindow(newPrevious, newCurrent, universes.static)
  }

  /**
   * The computed log probability of evidence.
   */
  def getlogProbEvidence(): Double = {
    checkBelief()
    logProbEvidence
  }

  /**
   * The computed probability of evidence.
   */
  def probEvidence(): Double = {
    checkBelief()
    val probEvidence = scala.math.exp(logProbEvidence)
    probEvidence
  }

}

/**
 * A one-time particle filter.
 *
 * @param static The universe of elements whose values do not change over time
 * @param initial The universe describing the distribution over the initial state of the system
 * @param transition The transition model describing how the current state of the system depends on the previous
 * @param numParticles The number of particles to use at each time step
 */
class OneTimeParticleFilter(static: Universe = new Universe(), initial: Universe, transition: (Universe, Universe) => Universe, val numParticles: Int,
  override val zeroWeightPolicy: ParticleFilter.ZeroWeightPolicy = ParticleFilter.ZeroWeightPolicy.Fail)
  extends Filtering(static, initial, transition) with ParticleFilter with OneTimeFiltering {

  var currentUniverse: Universe = initial
  var previousUniverse: Universe = scala.compiletime.uninitialized

  require(numParticles > 0, "numParticles must be positive")
  require(zeroWeightPolicy != null, "A zero-weight policy is required")

  private def doTimeStep(weightedParticleCreator: Int => ParticleFilter.LogWeightedParticle): Unit =
    logTimeStep((0 until numParticles).map(weightedParticleCreator))

  /**
   * Begin the particle filter, determining the initial distribution.
   */
  def run(): Unit = guardedUpdate {
    val lw = new LikelihoodWeighter(currentUniverse, new PermanentCache(currentUniverse))
    try doTimeStep((i: Int) => initialLogWeightedParticle(static, currentUniverse, lw))
    finally lw.deregisterDependencies()
  }

  /**
   * Advance the filtering one time step, conditioning on the given evidence at the new time point.
   */
  def advanceTime(evidence: Seq[NamedEvidence[?]] = List()): Unit = guardedUpdate {

    val currentWindow = new UniverseWindow(previousUniverse, currentUniverse, static)
    val newWindow = advanceUniverse(currentWindow, transition)
    val lw = new LikelihoodWeighter(newWindow.current, new PermanentCache(newWindow.current))
    try {
      doTimeStep((i: Int) => addLogWeightedParticle(evidence, i, newWindow, lw))
      previousUniverse = newWindow.previous
      currentUniverse = newWindow.current
    } finally lw.deregisterDependencies()
  }
}

object ParticleFilter {

  /** Fail by default. LegacyUniform retains rejected particles, not a valid posterior. */
  enum ZeroWeightPolicy { case Fail, LegacyUniform }

  /** No sampled particle has nonzero likelihood; this need not prove impossible evidence. */
  final class NoViableParticles extends IllegalStateException(
    "No viable particles: all log weights are -Infinity. Check evidence and particle budget; no posterior is available.")

  /** Log-space counterpart of the retained ordinary-weight compatibility type. */
  final case class LogWeightedParticle(logWeight: Double, state: State)

  /** Explicit zero-weight policy; use LegacyUniform only for compatibility. */
  def apply(static: Universe, initial: Universe, transition: (Universe, Universe) => Universe,
    numParticles: Int, policy: ZeroWeightPolicy): OneTimeParticleFilter =
    new OneTimeParticleFilter(static, initial, transition, numParticles, policy)

  /** Explicit zero-weight policy with no static model. */
  def apply(initial: Universe, transition: Universe => Universe, numParticles: Int,
    policy: ZeroWeightPolicy): OneTimeParticleFilter =
    apply(new Universe(), initial, (_, previous) => transition(previous), numParticles, policy)

  /**
   * A one-time particle filter.
   *
   * @param static The universe of elements whose values do not change over time
   * @param initial The universe describing the distribution over the initial state of the system
   * @param transition The transition model describing how the current state of the system depends on the static and previous, respectively
   * @param numParticles Number of particles to use at each time step
   */
  def apply(static: Universe, initial: Universe, transition: (Universe, Universe) => Universe, numParticles: Int): OneTimeParticleFilter =
    new OneTimeParticleFilter(static, initial, transition, numParticles)

  /**
   * A one-time particle filter in which the static universe is empty.
   *
   * @param initial The universe describing the distribution over the initial state of the system
   * @param transition The transition model describing how the current state of the system depends on the previous
   * @param numParticles Number of particles to use at each time step
   */
  def apply(initial: Universe, transition: Universe => Universe, numParticles: Int): OneTimeParticleFilter =
    apply(new Universe(), initial, (static: Universe, previous: Universe) => transition(previous), numParticles)

  /**
   *  A representation of the current beliefs of the particle filter.
   *  A BeliefState should not be confused with a State, which is a particular configuration of the system.
   *  A BeliefState represents a distribution over States, and in a particle filter, it is implemented as a collection of representative States.
   */
  type BeliefState = Array[State] // dynamic and static

  /** Weighted particles, consisting of a weight and a state. */
  type WeightedParticle = (Double, State)

  /** Reference to parallel implementation. */
  def par = ParParticleFilter

}

/**
 * A class representing a single window in time, with a current universe, a previous universe,
 * and a static universe.
 */
class UniverseWindow(val previous: Universe, val current: Universe, val static: Universe)
