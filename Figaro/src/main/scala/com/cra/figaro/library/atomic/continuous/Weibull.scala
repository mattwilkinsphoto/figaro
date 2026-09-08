package com.cra.figaro.library.atomic.continuous

import com.cra.figaro.language.*

/** Positive Weibull minimum/lifetime law. Stochastic parameters use ScalarElement with an Apply-created kernel. */
object Weibull {
  /** Register a fixed-parameter element; parameters are validated before registration.
    * @param shape in [0.001,1e6]
    * @param scale in [1e-100,1e100]
    * @param name contextual name
    * @param collection owning universe/collection
    * @return AtomicScalar backed by WeibullDistribution
    * @example `Weibull(2,3)`
    */
  def apply(shape: Double,scale: Double)(using name: Name[Double],collection: ElementCollection): AtomicScalar =
    ScalarElement(WeibullDistribution(shape,scale))
}
