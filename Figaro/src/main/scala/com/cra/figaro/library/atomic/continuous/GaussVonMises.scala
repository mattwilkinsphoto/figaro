/* Fixed-parameter GVM adapter. See docs/GAUSS_VON_MISES.md for release-review status. */
package com.cra.figaro.library.atomic.continuous

import com.cra.figaro.language.*
import com.cra.figaro.util.random

/** Atomic joint element backed by a caller-supplied immutable kernel. Use the factory. */
final class AtomicGaussVonMises private[continuous] (
  name: Name[LinearAngular], val distribution: GaussVonMisesDistribution, collection: ElementCollection)
  extends Element[LinearAngular](name, collection) with Atomic[LinearAngular]
    with Continuous[LinearAngular] with HasLogDensity[LinearAngular] {
  type Randomness = LinearAngular
  /** @return independent joint draw using the currently scoped Figaro RNG */
  def generateRandomness(): LinearAngular = distribution.sample(random)
  /** @param rand sampled immutable joint randomness
    * @return rand unchanged
    */
  def generateValue(rand: LinearAngular): LinearAngular = rand
  /** @param value finite joint state of the configured dimension
    * @return stable joint log density
    */
  def logDensity(value: LinearAngular): Double = distribution.logDensity(value)
  /** @param value finite joint state
    * @return logDensity(value), the Continuous interface alias
    */
  def logp(value: LinearAngular): Double = logDensity(value)
}

object GaussVonMises {
  /** Register a fixed-parameter joint element; each worker needs its own element/universe.
    * @param distribution non-null immutable validated kernel, safe to reuse across workers
    * @param name contextual element name
    * @param collection contextual owning collection
    * @return atomic joint element; invalid kernel throws before registration
    * @example `GaussVonMises(kernel)(using "state", universe)`
    */
  def apply(distribution: GaussVonMisesDistribution)(using name: Name[LinearAngular], collection: ElementCollection): AtomicGaussVonMises = {
    require(distribution != null, "distribution must be non-null")
    new AtomicGaussVonMises(name, distribution, collection)
  }
}
