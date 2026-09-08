package com.cra.figaro.library.atomic.discrete

import com.cra.figaro.language.*

/** Success count from sampling without replacement. Stochastic parameters use CountElement with an Apply-created kernel. */
object Hypergeometric {
  /** Register a fixed-parameter element; parameters are validated before registration.
    * @param population integer in [1,100000]
    * @param successes integer in [0,population]
    * @param draws integer in [0,population]
    * @param name contextual name
    * @param collection owning universe/collection
    * @return AtomicCount backed by HypergeometricDistribution
    * @example `Hypergeometric(20,7,5)`
    */
  def apply(population: Int,successes: Int,draws: Int)(using name: Name[Int],collection: ElementCollection): AtomicCount =
    CountElement(HypergeometricDistribution(population,successes,draws))
}
