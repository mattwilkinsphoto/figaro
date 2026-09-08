package com.cra.figaro.library.atomic.continuous

import com.cra.figaro.language.*

/** Positive lognormal; parameters describe log(X), not X. Stochastic parameters use ScalarElement with an Apply-created kernel. */
object LogNormal {
  /** Register a fixed-parameter element; parameters are validated before registration.
    * @param logMean mean of log(X) in [-500,500]
    * @param logStandardDeviation standard deviation of log(X) in [0.001,50], not variance
    * @param name contextual name
    * @param collection owning universe/collection
    * @return AtomicScalar backed by LogNormalDistribution
    * @example `LogNormal(0,1)`
    */
  def apply(logMean: Double,logStandardDeviation: Double)(using name: Name[Double],collection: ElementCollection): AtomicScalar =
    ScalarElement(LogNormalDistribution(logMean,logStandardDeviation))
}
