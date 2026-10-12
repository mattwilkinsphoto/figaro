package com.cra.figaro.algorithm.filtering

import com.cra.figaro.util.{RandomContext, SamplingRandom}
import org.apache.commons.math3.distribution.{BetaDistribution, BinomialDistribution}

/** Advisory budgets from a separate, fixed-size IID Boolean pilot (research index INF-08).
  * This is not a precision certificate, a retry policy, or production-time adaptation.
  * Counts from dependent/resampled particles or changing observations are not an IID pilot.
  */
object BoundedParticleBudget {
  /** Validated engineering envelope, not a mathematical upper limit. */
  val maximumCount: Int = 1000000

  /** Counts from a completed, predeclared-size IID pilot of the same candidate/evidence law. */
  final case class Pilot(attempts: Int, successes: Int) {
    require(attempts >= 1 && attempts <= maximumCount && successes >= 0 && successes <= attempts,
      s"Require 1 <= attempts <= $maximumCount and 0 <= successes <= attempts")
    /** Empirical survival fraction; zero does not establish impossible evidence. */
    def estimate: Double = successes.toDouble / attempts
  }

  /** maxCandidates is a hard per-step work ceiling; retainedSuccesses is not an accuracy target.
    * confidence is pilot interval coverage; missProbability is a separate single-step design aim.
    */
  final case class Request(maxCandidates: Int, retainedSuccesses: Int = 100,
    confidence: Double = .95, missProbability: Double = .05) {
    require(maxCandidates >= 2 && maxCandidates <= maximumCount, "Unsupported work cap")
    require(retainedSuccesses >= 1 && retainedSuccesses < maximumCount, "Unsupported survivor target")
    require(confidence >= .8 && confidence <= .999, "Require .8 <= confidence <= .999")
    require(missProbability >= .001 && missProbability <= .25, "Require .001 <= miss probability <= .25")
  }

  /** Frequentist Clopper-Pearson interval, computed numerically, not a posterior over p. */
  final case class Interval(lower: Double, upper: Double)
  enum Status { case Ready, NoPilotSuccesses, InsufficientBudget }

  /** Frozen candidate counts plus expected work under the stationary IID pilot point estimate. */
  final case class Plan(minCandidates: Int, maxCandidates: Int, targetSuccesses: Int,
    expectedCandidates: Double) {
    /** Explicit opt-in conversion; choose a production seed/stream distinct from the pilot.
      * Example: `advice.plan.get.config(seed = 202)`.
      */
    def config(seed: Long, algorithm: SamplingRandom.Algorithm = SamplingRandom.defaultAlgorithm,
      health: ParticleFilterHealth.Config = ParticleFilterHealth.Config()): BoundedParticleFilter.Config =
      BoundedParticleFilter.Config(minCandidates, maxCandidates, targetSuccesses, seed, algorithm, health)
  }

  /** tailAtHardCap is P(Bin(cap, lower) < target), not a posterior-error probability.
    * plan is absent rather than silently exceeding the caller's cap or inventing p after zero successes.
    */
  final case class Advice(pilot: Pilot, request: Request, interval: Interval, status: Status,
    tailAtHardCap: Double, plan: Option[Plan])

  /** Execute exactly attempts Boolean draws using an owned RNG scope; callbacks must be IID.
    * Uses util.random; never seeds or consumes the caller's ambient stream. Errors/interruption
    * propagate without a partial Pilot. Example: `runPilot(2000, 101)(() => random.nextDouble() < .1)`.
    */
  def runPilot(attempts: Int, seed: Long,
    algorithm: SamplingRandom.Algorithm = SamplingRandom.defaultAlgorithm)(draw: () => Boolean): Pilot = {
    require(attempts >= 1 && attempts <= maximumCount && draw != null && algorithm != null,
      "Valid fixed pilot size, Boolean callback and RNG required")
    RandomContext.withRandom(SamplingRandom.seeded(seed, algorithm)) {
      var successes = 0
      var i = 0
      while (i < attempts) {
        if (Thread.currentThread().isInterrupted) throw new InterruptedException("Pilot interrupted; no completed advice")
        if (draw()) successes += 1
        i += 1
      }
      if (Thread.currentThread().isInterrupted) throw new InterruptedException("Pilot interrupted; no completed advice")
      Pilot(attempts, successes)
    }
  }

  /** Return a conservative single-step work plan from a separate pilot; never executes production.
    * Example: `advise(Pilot(2000, 200), Request(maxCandidates = 10000))`.
    * Invalid inputs throw IllegalArgumentException; unresolved numerical calculations throw
    * ArithmeticException (no plan). Default target 101 accounts for the discarded final success.
    */
  def advise(pilot: Pilot, request: Request): Advice = {
    require(pilot != null && request != null, "Pilot and request required")
    val n = pilot.attempts
    val k = pilot.successes
    val alpha = (1 - request.confidence) / 2
    // Null RNG: only numerical distribution functions are used; no hidden sampling backend.
    // The small outward margin exceeds the configured inverse absolute tolerance. It is a
    // numerical safeguard, not an interval-arithmetic proof of coverage for every input.
    def lower(a: Int, b: Int): Double = {
      val beta = new BetaDistribution(null, a.toDouble, b.toDouble, 1e-14)
      val q = if (a == 1) -math.expm1(math.log1p(-alpha)/b)
        else if (b == 1) math.exp(math.log(alpha)/a)
        else beta.inverseCumulativeProbability(alpha)
      if (!q.isFinite || q <= 0 || q >= 1) throw new ArithmeticException("Unresolved pilot quantile")
      val outward = math.max(0.0, q - 2e-14)
      if (beta.cumulativeProbability(outward) > alpha + 1e-10)
        throw new ArithmeticException("Pilot quantile residual too large")
      outward
    }
    val interval = Interval(if (k == 0) 0.0 else lower(k, n-k+1),
      if (k == n) 1.0 else 1.0-lower(n-k, k+1))
    val target = request.retainedSuccesses + 1
    val tail = miss(request.maxCandidates, target, interval.lower)
    if (k == 0) Advice(pilot, request, interval, Status.NoPilotSuccesses, tail, None)
    else if (interval.lower <= 0) throw new ArithmeticException("Positive survival bound is unresolved")
    else if (tail > request.missProbability)
      Advice(pilot, request, interval, Status.InsufficientBudget, tail, None)
    else {
      var lo = target
      var hi = request.maxCandidates
      while (lo < hi) {
        val mid = lo + (hi-lo)/2
        if (miss(mid, target, interval.lower) <= request.missProbability) hi = mid else lo = mid+1
      }
      val floor = math.max(2, math.min(lo.toDouble, math.ceil(target / pilot.estimate)).toInt)
      val work = expectedWork(floor, lo, target, pilot.estimate)
      Advice(pilot, request, interval, Status.Ready, tail, Some(Plan(floor, lo, target, work)))
    }
  }

  private[figaro] def miss(n: Int, target: Int, p: Double): Double = {
    val value = if (target > n || p == 0) 1.0 else if (p == 1) 0.0
      else new BinomialDistribution(null, n, p).cumulativeProbability(target-1)
    if (!value.isFinite || value < 0 || value > 1) throw new ArithmeticException("Unresolved binomial tail")
    value
  }

  /** E[max(min, min(T_target, max))], independently validated against finite stopping trees. */
  private[figaro] def expectedWork(min: Int, max: Int, target: Int, p: Double): Double = {
    if (p == 0) return max.toDouble
    if (p == 1) return math.max(min, math.min(target, max)).toDouble
    def capped(n: Int): Double =
      (target/p) * (1.0-miss(n+1, target+1, p)) + n*miss(n, target, p)
    val work = capped(max) + min - capped(min)
    if (!work.isFinite || work < min-1e-6 || work > max+1e-6)
      throw new ArithmeticException("Unresolved expected candidate work")
    math.max(min, math.min(max, work))
  }
}
