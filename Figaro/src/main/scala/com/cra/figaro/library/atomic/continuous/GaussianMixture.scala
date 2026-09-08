package com.cra.figaro.library.atomic.continuous

import com.cra.figaro.language.*
import com.cra.figaro.util.random

/** Observation-ready full-covariance GMM element. A draw is a Vector, not a component label. */
final class AtomicGaussianMixture private[continuous](name: Name[Vector[Double]],val distribution: GaussianMixtureDistribution,collection: ElementCollection)
  extends Element[Vector[Double]](name,collection) with Atomic[Vector[Double]] with Continuous[Vector[Double]] with HasLogDensity[Vector[Double]] {
  type Randomness = Vector[Double]
  def generateRandomness(): Vector[Double] = distribution.sample(random)
  def generateValue(value: Vector[Double]): Vector[Double] = value
  def logDensity(value: Vector[Double]): Double = distribution.logDensity(value)
  def logp(value: Vector[Double]): Double = logDensity(value)
}

/** Fixed or hierarchical GMM factories. Scalar mixtures use ScalarElement instead. */
object GaussianMixture {
  /** @param distribution immutable validated GMM
    * @param name contextual name
    * @param collection owning collection
    * @return atomic GMM with stable observation likelihoods
    * @example `GaussianMixture(kernel)`
    */
  def apply(distribution: GaussianMixtureDistribution)(using name: Name[Vector[Double]],collection: ElementCollection): AtomicGaussianMixture = {
    require(distribution != null); new AtomicGaussianMixture(name,distribution,collection)
  }
  /** @param distribution element producing validated GMM kernels
    * @param name contextual name
    * @param collection owning collection
    * @return non-caching chain; invalid dynamic parameters fail at evaluation
    * @example `GaussianMixture(Apply(weight, (w: Double) => GaussianMixtureDistribution(Vector(w,1-w),components)))`
    */
  def apply(distribution: Element[GaussianMixtureDistribution])(using name: Name[Vector[Double]],collection: ElementCollection): Element[Vector[Double]] = {
    require(distribution != null); NonCachingChain(distribution,(d: GaussianMixtureDistribution) => apply(d)(using "",collection))
  }
}
