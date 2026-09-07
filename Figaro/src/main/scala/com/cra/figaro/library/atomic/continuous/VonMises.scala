/* Circular Figaro elements. See LICENSE and FigaroAttributions.txt. */
package com.cra.figaro.library.atomic.continuous

import com.cra.figaro.language.*
import com.cra.figaro.util.random

/** Atomic circular element backed by an immutable kernel; create with VonMises.apply. */
final class AtomicVonMises private[continuous] (
  name: Name[Double], val distribution: VonMisesDistribution, collection: ElementCollection)
  extends Element[Double](name, collection) with Atomic[Double] with Continuous[Double] with HasLogDensity[Double] {
  type Randomness = Double

  /** @return a fresh angle from the currently scoped Figaro RNG, in [-Pi, Pi) */
  def generateRandomness(): Double = distribution.sample(random)
  /** @param rand sampled angular randomness in radians
    * @return rand unchanged; generation already normalizes it
    */
  def generateValue(rand: Double): Double = rand
  /** @param angle finite radians
    * @return stable periodic log density, evaluated by the immutable kernel
    */
  def logDensity(angle: Double): Double = distribution.logDensity(angle)
  /** @param angle finite radians
    * @return logDensity(angle), the Continuous interface alias
    */
  def logp(angle: Double): Double = logDensity(angle)
}

/** Factories for circular von Mises elements. Evidence retains the supplied numeric angle;
  * normalize observations explicitly when downstream code expects canonical representatives.
  */
object VonMises {
  /** Create a fixed-parameter element.
    * @param location finite radians
    * @param kappa concentration in [0, VonMisesDistribution.MaxKappa]
    * @param name contextual element name
    * @param collection contextual owning collection
    * @return atomic circular element with density/logDensity/logp
    * @example `VonMises(0.0, 2.0)`
    */
  def apply(location: Double, kappa: Double)(using name: Name[Double], collection: ElementCollection): AtomicVonMises =
    new AtomicVonMises(name, VonMisesDistribution(location, kappa), collection)

  /** Create a conditional-location element; parameter values are validated when evaluated.
    * @param location element producing finite radians
    * @param kappa fixed valid concentration
    * @param name contextual element name
    * @param collection contextual owning collection
    * @return chain whose atomic children have stable log densities
    * @example `VonMises(Select(0.5 -> 0.0, 0.5 -> 1.0), 2.0)`
    */
  def apply(location: Element[Double], kappa: Double)(using name: Name[Double], collection: ElementCollection): Element[Double] = {
    VonMisesDistribution(0.0, kappa) // validate the fixed parameter before registering a chain
    NonCachingChain(location, (m: Double) => apply(m, kappa)(using "", collection))
  }

  /** Create a conditional-concentration element.
    * @param location fixed finite radians
    * @param kappa element producing a concentration in the supported range
    * @param name contextual element name
    * @param collection contextual owning collection
    * @return chain; invalid dynamic parameters fail on evaluation
    * @example `VonMises(0.0, Select(0.5 -> 1.0, 0.5 -> 4.0))`
    */
  def apply(location: Double, kappa: Element[Double])(using name: Name[Double], collection: ElementCollection): Element[Double] = {
    val m = VonMisesDistribution(location, 0.0).location
    NonCachingChain(kappa, (k: Double) => apply(m, k)(using "", collection))
  }

  /** Create an element with two stochastic parameters.
    * @param location element producing finite radians
    * @param kappa element producing concentration in the supported range
    * @param name contextual element name
    * @param collection contextual owning collection
    * @return non-caching chain; both values are checked when its child is constructed
    * @example `VonMises(Constant(0.0), Constant(2.0))`
    */
  def apply(location: Element[Double], kappa: Element[Double])(using name: Name[Double], collection: ElementCollection): Element[Double] =
    NonCachingChain(location, kappa, (m: Double, k: Double) => apply(m, k)(using "", collection))
}
