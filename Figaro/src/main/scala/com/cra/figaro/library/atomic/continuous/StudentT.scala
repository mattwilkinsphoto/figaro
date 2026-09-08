package com.cra.figaro.library.atomic.continuous

import com.cra.figaro.language.*

/** Student t: degrees of freedom, location and scale (not variance). Stochastic parameters use ScalarElement with an Apply-created kernel. */
object StudentT {
  /** Register a fixed-parameter element; parameters are validated before registration.
    * @param degreesOfFreedom positive shape in [0.001,1e6]
    * @param location center with absolute value <=1e100; default 0
    * @param scale positive scale in [1e-100,1e100]; default 1
    * @param name contextual name
    * @param collection owning universe/collection
    * @return AtomicScalar backed by StudentTDistribution
    * @example `StudentT(5,0,1)`
    */
  def apply(degreesOfFreedom: Double,location: Double=0,scale: Double=1)(using name: Name[Double],collection: ElementCollection): AtomicScalar =
    ScalarElement(StudentTDistribution(degreesOfFreedom,location,scale))
}
