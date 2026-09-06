package com.cra.figaro.algorithm.sampling.parallel

import org.apache.commons.math3.distribution.NormalDistribution

/** Gaussian truncated SPRT and discrete KL utilities. This is not an MCMC convergence test. */
object TruncatedSprt {
  /** Continue collecting observations, or select one of the two design hypotheses. */
  enum Decision { case Continue, AcceptH0, AcceptH1 }

  /** Immutable test state. Construct with Design.initial and update with Design.advance.
    * @param samples observations consumed
    * @param logLikelihoodRatio accumulated log L(H1)/L(H0)
    * @param decision current decision; terminal states cannot be advanced
    * @param atTruncation true if the terminal fixed-sample rule made the decision
    */
  final class State private[TruncatedSprt] (private[TruncatedSprt] val design: Design,
    val samples: Int, val logLikelihoodRatio: Double, val decision: Decision, val atTruncation: Boolean)

  /** Validated design for independent equal-variance Normal observations with mean0 < mean1.
    * Sequential boundaries use the allocated error budgets and Wald's overshoot approximation.
    * The terminal threshold is recomputed after rounding the nominal sample limit upward.
    * Known model parameters are required: plugging in adaptively estimated parameters changes calibration.
    */
  final class Design private[TruncatedSprt] (val mean0: Double, val mean1: Double,
    val observationSd: Double, val lowerBoundary: Double, val upperBoundary: Double,
    val nominalSamples: Double, val maxSamples: Int, val terminalBoundary: Double) {
    /** @return a fresh state with no evidence and Decision.Continue
      * @example `val state = design.initial`
      */
    def initial: State = new State(this, 0, 0.0, Decision.Continue, false)

    /** Consume one observation; the terminal rule takes precedence at maxSamples.
      * @param state nonterminal state created by this design
      * @param observation finite scalar observation, not a precomputed likelihood ratio
      * @return a new state; the supplied state is unchanged
      * @throws IllegalArgumentException for foreign/terminal states or unrepresentable arithmetic
      * @example `val next = design.advance(design.initial, 0.2)`
      */
    def advance(state: State, observation: Double): State = {
      require(state != null && (state.design eq this), "State belongs to another design")
      require(state.decision == Decision.Continue, "Test already decided")
      require(observation.isFinite, "Observation must be finite")
      val separation = (mean1 - mean0) / observationSd
      val increment = separation * ((observation - mean0) / observationSd - separation / 2)
      val total = state.logLikelihoodRatio + increment
      require(increment.isFinite && total.isFinite, "Likelihood ratio outside numeric range")
      val count = state.samples + 1
      val terminal = count == maxSamples
      val decision = if (terminal) {
        if (total >= terminalBoundary) Decision.AcceptH1 else Decision.AcceptH0
      } else if (total >= upperBoundary) Decision.AcceptH1
      else if (total <= lowerBoundary) Decision.AcceptH0
      else Decision.Continue
      new State(this, count, total, decision, terminal)
    }
  }

  /** Construct a Gaussian truncated SPRT (Blostein-Huang 1991, equations 12-15).
    * @param mean0 observation mean under H0
    * @param mean1 observation mean under H1, strictly greater than mean0
    * @param observationSd known common observation standard deviation, positive
    * @param falseAlarmRate P(select H1 | H0), strictly between zero and 0.5
    * @param missedDetectionRate P(select H0 | H1), strictly between zero and 0.5; NOT detection power
    * @param terminalFalseAlarmFraction fraction of false-alarm budget reserved for the terminal decision, in (0,1)
    * @param terminalMissFraction fraction of miss budget reserved for the terminal decision, in (0,1)
    * @return immutable design with named boundaries and a positive integer sample limit
    * @throws IllegalArgumentException for invalid inputs or a design outside Double/Int range
    * @example `val design = TruncatedSprt.gaussian(0.0, 1.0, 1.0, missedDetectionRate = 0.10)`
    */
  def gaussian(mean0: Double, mean1: Double, observationSd: Double,
    falseAlarmRate: Double = 0.05, missedDetectionRate: Double = 0.10,
    terminalFalseAlarmFraction: Double = 0.5, terminalMissFraction: Double = 0.5): Design = {
    require(mean0.isFinite && mean1.isFinite && mean1 > mean0, "Need finite ordered means")
    require(observationSd.isFinite && observationSd > 0, "Need positive finite observation SD")
    require(Vector(falseAlarmRate, missedDetectionRate).forall(p => p > 0 && p < 0.5), "Error rates must be in (0,0.5)")
    require(Vector(terminalFalseAlarmFraction, terminalMissFraction).forall(p => p > 0 && p < 1), "Fractions must be in (0,1)")
    val alphaTerminal = terminalFalseAlarmFraction * falseAlarmRate
    val betaTerminal = terminalMissFraction * missedDetectionRate
    val alphaSequential = (1 - terminalFalseAlarmFraction) * falseAlarmRate
    val betaSequential = (1 - terminalMissFraction) * missedDetectionRate
    require(Vector(alphaTerminal, betaTerminal, alphaSequential, betaSequential).forall(_ > 0), "Error allocation underflow")
    val lower = math.log(betaSequential) - math.log1p(-alphaSequential)
    val upper = math.log1p(-betaSequential) - math.log(alphaSequential)
    val normal = new NormalDistribution(0, 1)
    val q0 = normal.inverseCumulativeProbability(alphaTerminal)
    val q1 = normal.inverseCumulativeProbability(betaTerminal)
    val separation = (mean1 - mean0) / observationSd
    // LLR has means +/- separation^2/2 and SD separation.
    val rootN = -(q0 + q1) / separation
    val nominal = rootN * rootN
    require(separation.isFinite && separation > 0 && nominal.isFinite && nominal > 0 && nominal <= Int.MaxValue,
      "Sample limit outside numeric range")
    val count = math.ceil(nominal).toInt
    val drift = separation * separation / 2
    val noise = math.sqrt(count.toDouble) * separation
    // At the rounded count, any threshold in [lo, hi] meets both terminal error allocations.
    val lo = -count * drift - noise * q0
    val hi = count * drift + noise * q1
    val threshold = lo / 2 + hi / 2
    require(drift.isFinite && drift > 0 && lo.isFinite && hi.isFinite && threshold.isFinite,
      "Terminal threshold outside numeric range")
    new Design(mean0, mean1, observationSd, lower, upper, nominal, count, threshold)
  }

  /** Discrete D_KL(P || Q) in nats, with explicit aligned support and no hidden smoothing.
    * @param p nonempty normalized probability vector (sum tolerance 1e-10)
    * @param q normalized vector of the same size and category order
    * @return nonnegative divergence; +Infinity if p has positive mass where q is zero; 0*log(0/q)=0
    * @throws IllegalArgumentException for invalid probabilities or mismatched support sizes
    * @example `TruncatedSprt.klDivergence(Vector(1.0, 0.0), Vector(0.5, 0.5)) // log(2)`
    */
  def klDivergence(p: Seq[Double], q: Seq[Double]): Double = {
    require(p != null && q != null && p.nonEmpty && p.size == q.size, "Aligned nonempty support required")
    require(Vector(p, q).forall(v => v.forall(x => x.isFinite && x >= 0 && x <= 1) && math.abs(v.sum - 1) <= 1e-10),
      "Inputs must be normalized probabilities")
    var total = 0.0
    var correction = 0.0
    val pairs = p.iterator.zip(q.iterator)
    while (pairs.hasNext) {
      val (a, b) = pairs.next()
      if (a > 0) {
        if (b == 0) return Double.PositiveInfinity
        val term = a * (math.log(a) - math.log(b)) - correction
        val next = total + term
        correction = (next - total) - term
        total = next
      }
    }
    math.max(0.0, total)
  }
}
