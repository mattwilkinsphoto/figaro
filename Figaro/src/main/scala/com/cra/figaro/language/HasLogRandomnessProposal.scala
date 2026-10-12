package com.cra.figaro.language

/** Opt-in custom MH proposal expressed entirely in the element's randomness measure.
  * Unlike HasLogPriorProposal this supports asymmetric, non-prior transitions.
  * Existing nextRandomness overrides are untouched unless this trait is explicitly
  * selected. See docs/CAPABILITY_EXPANSION.md, INF-04.
  */
trait HasLogRandomnessProposal[T] extends Element[T] {
  /** Draw the proposed randomness conditional on old randomness. */
  def proposeRandomness(old: Randomness): Randomness
  /** Log model density in randomness coordinates, including any required Jacobian. */
  def logRandomnessDensity(value: Randomness): Double
  /** Log transition density q(to | from), in the same randomness coordinates. */
  def logProposalDensity(from: Randomness, to: Randomness): Double

  /** @param old current randomness with finite model log density
    * @return proposed randomness, log reverse/forward ratio, log new/old model ratio
    * @example Override the three primitive methods instead of exponentiating ratios.
    */
  override def nextLogRandomness(old: Randomness): (Randomness, Double, Double) = {
    val next = proposeRandomness(old)
    LogProposalRatios.transition(next, logRandomnessDensity(old), logRandomnessDensity(next),
      logProposalDensity(old, next), logProposalDensity(next, old))
  }
}

/** Validated ratio construction, also usable from an existing nextLogRandomness override. */
object LogProposalRatios {
  /** @param next proposed randomness, not necessarily the element's value
    * @param oldLogModel finite model log density at the current state
    * @param newLogModel finite or -Infinity model log density at the proposed state
    * @param logForward finite log q(next | old); a generated proposal cannot have zero density
    * @param logReverse finite or -Infinity log q(old | next)
    * @return separate log proposal and model ratios; -Infinity means rejection
    * @example `transition(next, -1000.0, -1.0, -2.0, -3.0)`
    */
  def transition[R](next: R, oldLogModel: Double, newLogModel: Double,
    logForward: Double, logReverse: Double): (R, Double, Double) = {
    def finiteOrZero(x: Double): Boolean = x.isFinite || x == Double.NegativeInfinity
    require(oldLogModel.isFinite && logForward.isFinite &&
      finiteOrZero(newLogModel) && finiteOrZero(logReverse), "Invalid log proposal/model density")
    val model = newLogModel - oldLogModel
    val proposal = logReverse - logForward
    if ((newLogModel.isFinite && !model.isFinite) || (logReverse.isFinite && !proposal.isFinite))
      throw new ArithmeticException("Log proposal/model difference is not representable")
    (next, proposal, model)
  }
}
