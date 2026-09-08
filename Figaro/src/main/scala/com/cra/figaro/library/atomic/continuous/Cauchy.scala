package com.cra.figaro.library.atomic.continuous

import com.cra.figaro.language.*

/** Cauchy law with undefined mean and variance. Stochastic parameters use ScalarElement with an Apply-created kernel. */
object Cauchy {
  /** Register a fixed-parameter element; parameters are validated before registration.
    * @param location center with absolute value <=1e100
    * @param scale positive scale in [1e-100,1e100]
    * @param name contextual name
    * @param collection owning universe/collection
    * @return AtomicScalar backed by CauchyDistribution
    * @example `Cauchy(0,1)`
    */
  def apply(location: Double,scale: Double)(using name: Name[Double],collection: ElementCollection): AtomicScalar =
    ScalarElement(CauchyDistribution(location,scale))
}
