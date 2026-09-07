/* Opt-in stable likelihood support. See LICENSE and FigaroAttributions.txt. */
package com.cra.figaro.language

/** An element with a stable log density, used directly by likelihood weighting.
  * Existing HasDensity-only elements retain their previous behavior.
  */
trait HasLogDensity[T] extends HasDensity[T] {
  /** Evaluate log density in the element's reference measure.
    * @param value value to score
    * @return finite log density or negative infinity for zero density; reject malformed inputs
    */
  def logDensity(value: T): Double

  /** Exponentiate logDensity; small positive densities may round to zero.
    * @param value value to score
    * @return density, with ordinary floating-point exponentiation limits
    */
  override def density(value: T): Double = math.exp(logDensity(value))

  /** Prior proposal with ratios formed from log densities, preserving annealer semantics.
    * @param oldRandomness previous randomness with finite log density
    * @return (new randomness, reverse/forward proposal ratio, new/old density ratio)
    * @throws java.lang.ArithmeticException if the legacy ratio interface cannot represent both ratios
    */
  override def nextRandomness(oldRandomness: Randomness): (Randomness, Double, Double) = {
    val next = generateRandomness()
    val delta = logDensity(generateValue(next)) - logDensity(generateValue(oldRandomness))
    val modelRatio = math.exp(delta)
    val proposalRatio = math.exp(-delta)
    if (!modelRatio.isFinite || !proposalRatio.isFinite || modelRatio <= 0.0 || proposalRatio <= 0.0)
      throw new ArithmeticException("MH density ratios are not representable; use a supported initialization/proposal")
    (next, proposalRatio, modelRatio)
  }
}
