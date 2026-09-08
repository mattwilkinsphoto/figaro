package com.cra.figaro.library.atomic.continuous

import com.cra.figaro.language.*

/** Bounded triangular law with a strictly interior mode. Stochastic parameters use ScalarElement with an Apply-created kernel. */
object Triangular {
  /** Register a fixed-parameter element; parameters are validated before registration.
    * @param lower finite lower endpoint, absolute value <=1e100
    * @param mode strictly interior mode
    * @param upper finite upper endpoint, absolute value <=1e100
    * @param name contextual name
    * @param collection owning universe/collection
    * @return AtomicScalar backed by TriangularDistribution
    * @example `Triangular(0,.3,1)`
    */
  def apply(lower: Double,mode: Double,upper: Double)(using name: Name[Double],collection: ElementCollection): AtomicScalar =
    ScalarElement(TriangularDistribution(lower,mode,upper))
}
