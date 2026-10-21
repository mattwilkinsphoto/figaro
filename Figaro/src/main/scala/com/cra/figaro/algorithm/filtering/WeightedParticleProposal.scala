package com.cra.figaro.algorithm.filtering

import com.cra.figaro.library.atomic.continuous.{GaussianDistribution, MultivariateGaussianDistribution}
import org.apache.commons.math3.linear.{Array2DRowRealMatrix, ArrayRealVector, CholeskyDecomposition, MatrixUtils, RealMatrix}
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

  /** Fixed per-observation laws. Ordinary guided calls use previous ancestor weights;
    * auxiliary calls separately correct their observation-informed ancestor law.
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

  /** Exact log p(y) for x~N(mean,sd^2), y|x~N(x,noiseSd^2).
    * @param mean prior/transition mean
    * @param standardDeviation prior/transition standard deviation, not variance
    * @param observation observed finite scalar
    * @param noiseStandardDeviation positive observation standard deviation
    * @return finite predictive log density for auxiliary look-ahead; extremes fail explicitly
    * @example `gaussianPredictiveLogLikelihood(.8*parent, .5, y, .2)`
    */
  def gaussianPredictiveLogLikelihood(mean: Double, standardDeviation: Double,
    observation: Double, noiseStandardDeviation: Double): Double = {
    GaussianDistribution(mean,standardDeviation)
    GaussianDistribution(observation,noiseStandardDeviation)
    val result = gaussian(mean,math.hypot(standardDeviation,noiseStandardDeviation)).logDensity(observation)
    if (!result.isFinite) throw new ArithmeticException("Predictive Gaussian log density not representable")
    result
  }

  /** Adapt an immutable full-rank Gaussian to the filter's owned RNG.
    * @param distribution existing validated vector Gaussian
    * @return normalized vector law with the original covariance contract
    * @example `multivariateGaussian(distribution)`
    */
  def multivariateGaussian(distribution: MultivariateGaussianDistribution): Law[Vector[Double]] = {
    require(distribution != null)
    Law(() => distribution.sample(random), distribution.logDensity)
  }

  /** Exact conditional and predictive score for a linear-Gaussian observation.
    * @param posterior normalized distribution of x given y
    * @param logPredictive natural log p(y), NOT an extra observation factor
    */
  final case class GaussianObservation(posterior: MultivariateGaussianDistribution, logPredictive: Double) {
    require(posterior != null && logPredictive.isFinite)
    /** @return proposal using the filter RNG; e.g. `guide.proposal` */
    def proposal: Law[Vector[Double]] = multivariateGaussian(posterior)
  }

  /** Condition x~N(m,P) on y=H*x+offset+noise, noise~N(0,R), independent of x.
    * Cholesky solves on scaled innovation covariance; Joseph covariance update.
    * No matrix inverse, fitted model, jitter or deterministic/zero-noise conditioning.
    * @param prior immutable full-rank prior or conditional transition Gaussian
    * @param observation finite observed vector of size 1..128
    * @param observationMatrix H with observation.size rows and prior.dimension columns
    * @param noiseCovariance R, full-rank observation covariance (NOT standard deviations)
    * @param offset optional finite observation offset; empty means zeros
    * @return conditional proposal and predictive log likelihood for the SAME model
    * @example `linearGaussianObservation(prior, Vector(y), Vector(Vector(1.0,0.0)), Vector(Vector(.2)))`
    */
  def linearGaussianObservation(prior: MultivariateGaussianDistribution, observation: Vector[Double],
    observationMatrix: Vector[Vector[Double]], noiseCovariance: Vector[Vector[Double]],
    offset: Vector[Double] = Vector.empty): GaussianObservation = {
    require(prior != null && observation != null && observation.nonEmpty && observation.size <= 128 &&
      observation.forall(_.isFinite), "Finite observation vector of size 1..128 required")
    val d = prior.dimension
    val k = observation.size
    require(observationMatrix != null && observationMatrix.size == k &&
      observationMatrix.forall(r => r != null && r.size == d && r.forall(_.isFinite)), "Invalid observation matrix")
    require(offset != null && (offset.isEmpty || (offset.size == k && offset.forall(_.isFinite))), "Invalid offset")
    val b = if (offset.isEmpty) Vector.fill(k)(0.0) else offset
    MultivariateGaussianDistribution(Vector.fill(k)(0.0),noiseCovariance)
    def matrix(x: Vector[Vector[Double]]) = new Array2DRowRealMatrix(x.map(_.toArray).toArray,false)
    def symmetric(m: RealMatrix): Vector[Vector[Double]] = Vector.tabulate(m.getRowDimension,m.getColumnDimension) {
      (i,j) => m.getEntry(i,j)/2 + m.getEntry(j,i)/2
    }
    if (Thread.currentThread().isInterrupted) throw new InterruptedException("Gaussian conditioning interrupted")
    val p = matrix(prior.covariance); val h = matrix(observationMatrix); val r = matrix(noiseCovariance)
    val predictedMean = h.operate(prior.mean.toArray).toVector.zip(b).map(_+_)
    val s = symmetric(h.multiply(p).multiply(h.transpose()).add(r))
    val predictive = MultivariateGaussianDistribution(predictedMean,s)
    val scales = Vector.tabulate(k)(i => math.sqrt(s(i)(i)))
    val correlation = matrix(Vector.tabulate(k,k)((i,j) => s(i)(j)/scales(i)/scales(j)))
    val solver = new CholeskyDecomposition(correlation,1e-12,1e-14).getSolver
    val cross = p.multiply(h.transpose())
    val gain = new Array2DRowRealMatrix(Vector.tabulate(d) { i =>
      val rhs = new ArrayRealVector(Vector.tabulate(k)(j => cross.getEntry(i,j)/scales(j)).toArray,false)
      solver.solve(rhs).toArray.zip(scales).map(_/_)
    }.toArray,false)
    val residual = observation.zip(predictedMean).map(_-_)
    val mean = prior.mean.zip(gain.operate(residual.toArray)).map(_+_)
    val a = MatrixUtils.createRealIdentityMatrix(d).subtract(gain.multiply(h))
    val covariance = symmetric(a.multiply(p).multiply(a.transpose()).add(gain.multiply(r).multiply(gain.transpose())))
    GaussianObservation(MultivariateGaussianDistribution(mean,covariance),predictive.logDensity(observation))
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
