package com.cra.figaro.algorithm.filtering

import com.cra.figaro.library.atomic.continuous.GaussianDistribution
import com.cra.figaro.util.random

/** Observation-informed, normalized proposal laws for detached immutable states (INF-10).
  * All densities must use the same reference measure; callbacks must be fixed and pure.
  * Support coverage cannot be established from sampled points.
  */
object WeightedParticleProposal {
  /** A normalized law whose sampler uses the filter's scoped Figaro RNG.
    * @param sample independent draw, with no retained mutable sampling state
    * @param logDensity natural-log density/mass, finite or -Infinity
    */
  final case class Law[S](sample: () => S, logDensity: S => Double) {
    require(sample != null && logDensity != null, "Sampler and normalized density required")
  }

  /** Fixed per-observation laws. Ancestors still use the previous normalized weights.
    * @param initial target initial law p0 (normalized, without this observation)
    * @param transition target transition p(x|parent), without this observation
    * @param proposedInitial observation-informed initial law q0
    * @param proposedTransition observation-informed q(x|parent)
    */
  final case class Kernel[S](initial: Law[S], transition: S => Law[S],
    proposedInitial: Law[S], proposedTransition: S => Law[S]) {
    require(initial != null && transition != null && proposedInitial != null && proposedTransition != null,
      "Target and proposal laws required")
    private[filtering] def draw(parent: Option[S]): (S,Double) = {
      val p = parent.fold(initial)(transition)
      val q = parent.fold(proposedInitial)(proposedTransition)
      require(p != null && q != null, "Conditional laws required")
      val x = q.sample()
      val lp = checked(p.logDensity(x))
      val lq = checked(q.logDensity(x))
      require(lq.isFinite, "Proposal assigned zero density to its own draw")
      val correction = if (lp == Double.NegativeInfinity) lp else lp-lq
      if (lp.isFinite && !correction.isFinite) throw new ArithmeticException("Proposal correction overflow")
      (x,correction)
    }
  }

  private[filtering] def checked(value: Double): Double = {
    require(value.isFinite || value == Double.NegativeInfinity, "Log density must be finite or -Infinity")
    value
  }

  /** Adapt the existing Gaussian kernel. Second argument is standard deviation, not variance.
    * @return normalized scalar law; e.g. `gaussian(0, 1)`
    */
  def gaussian(mean: Double, standardDeviation: Double): Law[Double] = {
    val law = GaussianDistribution(mean, standardDeviation)
    Law(() => law.sample(random), law.logDensity)
  }

  /** Gaussian conditional for x~N(mean,sd^2), y|x~N(x,noiseSd^2).
    * Uses scaled precision arithmetic; this is a proposal, not an observation applied twice.
    * @return normalized q(x|y); e.g. `gaussianObservation(0,1,2,.2)`
    */
  def gaussianObservation(mean: Double, standardDeviation: Double, observation: Double,
    noiseStandardDeviation: Double): Law[Double] = {
    GaussianDistribution(mean,standardDeviation)
    GaussianDistribution(observation,noiseStandardDeviation)
    val ratio = math.min(standardDeviation,noiseStandardDeviation) / math.max(standardDeviation,noiseStandardDeviation)
    val r2 = ratio*ratio
    val gain = if (standardDeviation <= noiseStandardDeviation) r2/(1+r2) else 1/(1+r2)
    val posteriorMean = (1-gain)*mean + gain*observation
    val posteriorSd = math.min(standardDeviation,noiseStandardDeviation)/math.sqrt(1+r2)
    gaussian(posteriorMean,posteriorSd)
  }

  /** Support-preserving q = priorFraction*p + (1-priorFraction)*guided.
    * @param prior normalized target prior/transition law with full target support
    * @param guided normalized observation-informed law using the same measure
    * @param priorFraction finite fraction in (0,1), fixed before the step
    * @return mixture sampler AND full mixture density, never the sampled component density
    * @example `defensive(gaussian(0,1),gaussianObservation(0,1,2,.2),.1)`
    */
  def defensive[S](prior: Law[S], guided: Law[S], priorFraction: Double = .1): Law[S] = {
    require(prior != null && guided != null && priorFraction.isFinite && priorFraction > 0 && priorFraction < 1)
    Law(() => if (random.nextDouble() < priorFraction) prior.sample() else guided.sample(), x => {
      val a = math.log(priorFraction)+checked(prior.logDensity(x))
      val b = math.log1p(-priorFraction)+checked(guided.logDensity(x))
      val peak = math.max(a,b)
      if (peak == Double.NegativeInfinity) peak else peak+math.log1p(math.exp(math.min(a,b)-peak))
    })
  }
}
