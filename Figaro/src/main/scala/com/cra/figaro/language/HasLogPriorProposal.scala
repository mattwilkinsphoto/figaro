package com.cra.figaro.language

/** Explicit opt-in for independent prior proposals with separate, stable log ratios.
  * Not mixed into HasLogDensity automatically: that would bypass existing subclasses'
  * custom nextRandomness overrides. Custom non-prior proposals should instead override
  * Element.nextLogRandomness with their own forward/reverse correction.
  */
trait HasLogPriorProposal[T] extends HasLogDensity[T] {
  /** @param old previous randomness with finite log density
    * @return prior draw, negative log density difference, positive log density difference
    * @throws java.lang.ArithmeticException if either state or their difference is not representable
    */
  override def nextLogRandomness(old: Randomness): (Randomness, Double, Double) = {
    val next = generateRandomness()
    val before = logDensity(generateValue(old))
    val after = logDensity(generateValue(next))
    val delta = after - before
    if (!before.isFinite || !after.isFinite || !delta.isFinite)
      throw new ArithmeticException("Prior MH proposal requires finite log densities and difference")
    (next, -delta, delta)
  }
}
