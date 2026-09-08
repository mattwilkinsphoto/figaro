package com.cra.figaro.library.atomic.discrete

import com.cra.figaro.language.*
import com.cra.figaro.util.random

/** Prior-proposal atomic adapter for validated count kernels; not a custom finite-factor implementation. */
final class AtomicCount private[discrete](name: Name[Int],val distribution: CountDistribution,collection: ElementCollection)
  extends Element[Int](name,collection) with Atomic[Int] with HasLogDensity[Int] {
  type Randomness = Int
  /** @return independent count draw from the scoped Figaro RNG */
  def generateRandomness(): Int = distribution.sample(random)
  /** @param rand sampled count randomness
    * @return rand unchanged
    */
  def generateValue(rand: Int): Int = rand
  /** @param k count to score
    * @return natural-log probability mass, also used by likelihood weighting
    */
  def logDensity(k: Int): Double = distribution.logProbability(k)
}

/** Fixed and stochastic-kernel factories for new count families. */
object CountElement {
  /** @param distribution non-null immutable count law
    * @param name contextual name
    * @param collection owning universe/collection
    * @return atomic count with direct log-mass evidence
    * @example `CountElement(NegativeBinomialDistribution(2.5,.4))`
    */
  def apply(distribution: CountDistribution)(using name: Name[Int],collection: ElementCollection): AtomicCount = {
    require(distribution != null,"non-null distribution required"); new AtomicCount(name,distribution,collection)
  }
  /** @param distribution element producing validated count kernels
    * @param name contextual name
    * @param collection owning universe/collection
    * @return non-caching conditional count law
    * @example `CountElement(Apply(Constant(.4), (p: Double) => NegativeBinomialDistribution(2,p)))`
    */
  def apply[D <: CountDistribution](distribution: Element[D])(using name: Name[Int],collection: ElementCollection): Element[Int] = {
    require(distribution != null,"non-null distribution element required")
    NonCachingChain(distribution,(d: D) => apply(d)(using "",collection))
  }
}
