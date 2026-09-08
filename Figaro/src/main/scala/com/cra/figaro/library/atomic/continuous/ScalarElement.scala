package com.cra.figaro.library.atomic.continuous

import com.cra.figaro.language.*
import com.cra.figaro.util.random

/** Atomic adapter for validated scalar kernels; never shares mutable model state. */
final class AtomicScalar private[continuous](name: Name[Double],val distribution: ScalarDistribution,collection: ElementCollection)
  extends Element[Double](name,collection) with Atomic[Double] with Continuous[Double] with HasLogDensity[Double] {
  type Randomness = Double
  /** @return independent draw from the currently scoped Figaro RNG */
  def generateRandomness(): Double = distribution.sample(random)
  /** @param rand sampled scalar randomness
    * @return rand unchanged
    */
  def generateValue(rand: Double): Double = rand
  /** @param x observed value
    * @return log likelihood; singular infinite boundary densities throw rather than corrupt weights
    */
  def logDensity(x: Double): Double = {
    val result=distribution.logDensity(x)
    if(result.isNaN || result == Double.PositiveInfinity) throw new ArithmeticException("singular boundary likelihood is not supported; use interval evidence")
    result
  }
  /** @param x value
    * @return logDensity(x), the Continuous alias
    */
  def logp(x: Double): Double = logDensity(x)
}

/** Fixed and stochastic-kernel factories shared by the new scalar families. */
object ScalarElement {
  /** @param distribution non-null immutable validated kernel
    * @param name contextual element name
    * @param collection owning universe/collection
    * @return atomic scalar with observation-ready log likelihood
    * @example `ScalarElement(StudentTDistribution(5,0,1))`
    */
  def apply(distribution: ScalarDistribution)(using name: Name[Double],collection: ElementCollection): AtomicScalar = {
    require(distribution != null,"non-null distribution required"); new AtomicScalar(name,distribution,collection)
  }
  /** Compose any stochastic parameters into a kernel element before calling this overload.
    * @param distribution element producing a validated kernel; invalid dynamic parameters fail during evaluation
    * @param name contextual element name
    * @param collection owning universe/collection
    * @return non-caching chain whose atomic children use stable log likelihoods
    * @example `ScalarElement(Apply(Constant(5.0), (df: Double) => StudentTDistribution(df)))`
    */
  def apply[D <: ScalarDistribution](distribution: Element[D])(using name: Name[Double],collection: ElementCollection): Element[Double] = {
    require(distribution != null,"non-null distribution element required")
    NonCachingChain(distribution,(d: D) => apply(d)(using "",collection))
  }
}
