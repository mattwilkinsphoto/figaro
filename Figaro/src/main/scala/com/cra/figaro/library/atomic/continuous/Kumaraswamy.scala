package com.cra.figaro.library.atomic.continuous

import com.cra.figaro.language.*

/** Bounded beta-like law on [0,1]. Stochastic parameters use ScalarElement with an Apply-created kernel. */
object Kumaraswamy {
  /** Register a fixed-parameter element; parameters are validated before registration.
    * @param a first shape in [0.001,1e6]
    * @param b second shape in [0.001,1e6]
    * @param name contextual name
    * @param collection owning universe/collection
    * @return AtomicScalar backed by KumaraswamyDistribution
    * @example `Kumaraswamy(2,3)`
    */
  def apply(a: Double,b: Double)(using name: Name[Double],collection: ElementCollection): AtomicScalar =
    ScalarElement(KumaraswamyDistribution(a,b))
}
