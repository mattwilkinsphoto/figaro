package com.cra.figaro.library.atomic.discrete

import com.cra.figaro.language.*

/** Failure count before a positive real number of successes. Stochastic parameters use CountElement with an Apply-created kernel. */
object NegativeBinomial {
  /** Register a fixed-parameter element; parameters are validated before registration.
    * @param successes positive real shape in [0.001,1e6]
    * @param successProbability probability in [1e-6,1]; one gives a point mass at zero
    * @param name contextual name
    * @param collection owning universe/collection
    * @return AtomicCount backed by NegativeBinomialDistribution
    * @example `NegativeBinomial(2.5,.4)`
    */
  def apply(successes: Double,successProbability: Double)(using name: Name[Int],collection: ElementCollection): AtomicCount =
    CountElement(NegativeBinomialDistribution(successes,successProbability))
}
