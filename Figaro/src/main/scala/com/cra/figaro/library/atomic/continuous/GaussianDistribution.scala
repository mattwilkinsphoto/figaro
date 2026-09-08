package com.cra.figaro.library.atomic.continuous

import com.cra.figaro.library.atomic.DistributionNumerics as N
import org.apache.commons.math3.special.Erf
import org.apache.commons.math3.linear.{Array2DRowRealMatrix,CholeskyDecomposition,EigenDecomposition}

/** Immutable scalar Gaussian kernel. Unlike Normal's factory, the second parameter is standard deviation.
  * @param location mean, absolute value <=1e100
  * @param standardDeviation positive standard deviation in [1e-100,1e100]
  */
final case class GaussianDistribution(location: Double,standardDeviation: Double) extends ScalarDistribution {
  N.location(location); N.scale(standardDeviation)
  def logDensity(x: Double): Double = { N.argument(x); val z=(x-location)/standardDeviation; -.5*math.log(2*math.Pi)-math.log(standardDeviation)-(.5*z)*z }
  def cdf(x: Double): Double = { N.argument(x); .5*Erf.erfc((location-x)/standardDeviation/math.sqrt(2)) }
  def survival(x: Double): Double = { N.argument(x); .5*Erf.erfc((x-location)/standardDeviation/math.sqrt(2)) }
  def quantile(p: Double): Double = {
    N.probability(p)
    if(p == 0) return Double.NegativeInfinity
    if(p == 1) return Double.PositiveInfinity
    if(p == .5) return location
    val target=math.min(p,1-p); var lo=0.0; var hi=40.0; var j=0
    while(j < 100) { N.check(); val mid=(lo+hi)/2; if(.5*Erf.erfc(mid/math.sqrt(2)) > target) lo=mid else hi=mid; j += 1 }
    N.interior(p,location+(if(p < .5) -1 else 1)*standardDeviation*(lo+hi)/2)
  }
  def support: (Double,Double) = (Double.NegativeInfinity,Double.PositiveInfinity)
  def mean: Option[Double] = Some(location)
  def variance: Option[Double] = Some(standardDeviation*standardDeviation)
  override def sample(rng: scala.util.Random): Double = {
    require(rng != null); N.check(); val x=location+standardDeviation*rng.nextGaussian()
    if(!x.isFinite) throw new ArithmeticException("Gaussian draw outside numeric range")
    x
  }
}

/** Full-rank multivariate Gaussian with immutable, caller-independent parameters.
  * @param mean finite vector of 1..128 means, each absolute value <=1e100
  * @param covariance symmetric positive-definite covariance, diagonal in [1e-200,1e200]
  * Correlation condition number must be <=1e10. Singular laws and implicit jitter are not supported.
  * @example `MultivariateGaussianDistribution(Vector(0.0,0.0),Vector(Vector(1.0,.5),Vector(.5,1.0)))`
  */
final case class MultivariateGaussianDistribution(mean: Vector[Double],covariance: Vector[Vector[Double]]) {
  require(mean != null && mean.nonEmpty && mean.size <= 128 && mean.forall(x => x.isFinite && math.abs(x) <= 1e100),"1..128 finite bounded means required")
  val dimension: Int = mean.size
  require(covariance != null && covariance.size == dimension && covariance.forall(r => r != null && r.size == dimension && r.forall(_.isFinite)),"finite square covariance required")
  private val scales=mean.indices.map { i => val v=covariance(i)(i); require(v >= 1e-200 && v <= 1e200,"variance outside [1e-200,1e200]"); math.sqrt(v) }.toVector
  private val correlation=mean.indices.map(i => mean.indices.map { j =>
    require(covariance(i)(j) == covariance(j)(i),"covariance must be exactly symmetric")
    val r=covariance(i)(j)/scales(i)/scales(j)
    require(r.isFinite && math.abs(r) <= 1+1e-14,"invalid covariance correlation")
    r
  }.toVector).toVector
  N.check()
  private val matrix=new Array2DRowRealMatrix(correlation.map(_.toArray).toArray,false)
  private val eigenvalues=new EigenDecomposition(matrix).getRealEigenvalues
  private[continuous] val conditionNumber: Double = eigenvalues.max/eigenvalues.min
  require(eigenvalues.forall(x => x.isFinite && x > 0) && conditionNumber <= 1e10,"positive definite, numerically resolved correlation required")
  private val factor=new CholeskyDecomposition(matrix,1e-12,1e-14).getL
  private[continuous] val lower: Vector[Vector[Double]] = mean.indices.map(i => mean.indices.map(j => factor.getEntry(i,j)*scales(i)).toVector).toVector
  private[continuous] val logDeterminant: Double = 2*mean.indices.map(i => math.log(lower(i)(i))).sum
  private def validate(x: Vector[Double]): Unit = require(x != null && x.size == dimension && x.forall(_.isFinite),"finite vector of matching dimension required")
  private[continuous] def whiten(x: Vector[Double]): Vector[Double] = {
    validate(x); val z=new Array[Double](dimension)
    for(i <- mean.indices) { N.check(); var value=x(i)/scales(i); for(j <- 0 until i) value -= factor.getEntry(i,j)*z(j); z(i)=value/factor.getEntry(i,i) }
    if(!z.forall(_.isFinite)) throw new ArithmeticException("Gaussian whitening overflow")
    z.toVector
  }
  /** @param x finite coordinate vector
    * @return squared Mahalanobis distance; overflow throws
    * @example `gaussian.mahalanobisSquared(Vector(1.0,0.0))`
    */
  def mahalanobisSquared(x: Vector[Double]): Double = {
    validate(x); val z=whiten(x.zip(mean).map(_-_)); val result=z.map(v => v*v).sum
    if(!result.isFinite) throw new ArithmeticException("Mahalanobis square overflow")
    result
  }
  /** @param x finite coordinate vector
    * @return natural-log Lebesgue density (ordinary density may underflow)
    * @example `gaussian.logDensity(Vector(0.0,0.0))`
    */
  def logDensity(x: Vector[Double]): Double = -.5*(dimension*math.log(2*math.Pi)+logDeterminant+mahalanobisSquared(x))
  def density(x: Vector[Double]): Double = math.exp(logDensity(x))
  /** @param rng non-null caller-owned generator
    * @return draw in physical coordinates; no mutable RNG retained
    * @example `gaussian.sample(new scala.util.Random(42))`
    */
  def sample(rng: scala.util.Random): Vector[Double] = {
    require(rng != null); N.check(); val z=Vector.fill(dimension)(rng.nextGaussian())
    val x=mean.indices.map(i => mean(i)+(0 to i).map(j => lower(i)(j)*z(j)).sum).toVector
    if(!x.forall(_.isFinite)) throw new ArithmeticException("Gaussian sample overflow")
    x
  }
  /** @param indices nonempty distinct valid coordinate indices, in desired output order
    * @return exact Gaussian marginal (not conditioning)
    * @example `gaussian.marginal(Vector(1))`
    */
  def marginal(indices: Vector[Int]): MultivariateGaussianDistribution = {
    require(indices != null && indices.nonEmpty && indices.distinct.size == indices.size && indices.forall(i => i >= 0 && i < dimension),"distinct valid marginal indices required")
    MultivariateGaussianDistribution(indices.map(mean),indices.map(i => indices.map(covariance(i))))
  }
}

/** Full-covariance Gaussian mixture model (GMM); immutable kernel, not an EM fitter.
  * @param weights normalized nonnegative probabilities (1..1024)
  * @param components matching Gaussian kernels of the same dimension
  */
final case class GaussianMixtureDistribution(weights: Vector[Double],components: Vector[MultivariateGaussianDistribution]) {
  private val normalized=ConstructionMath.weights(weights,components)
  val dimension: Int = components.head.dimension
  require(components.forall(_.dimension == dimension),"component dimensions must match")
  private val active=components.indices.filter(normalized(_) > 0).toVector
  /** @param x finite coordinate vector
    * @return stable log-sum-exp mixture density
    * @example `gmm.logDensity(Vector(0.0,1.0))`
    */
  def logDensity(x: Vector[Double]): Double = ConstructionMath.logSum(active.map(i => math.log(normalized(i))+components(i).logDensity(x)))
  def density(x: Vector[Double]): Double = math.exp(logDensity(x))
  def sample(rng: scala.util.Random): Vector[Double] = components(ConstructionMath.choose(normalized,rng)).sample(rng)
  /** @return total mean, including all positive-weight modes */
  def mean: Vector[Double] = Vector.tabulate(dimension)(j => active.map(i => normalized(i)*components(i).mean(j)).sum)
  /** @return within-component PLUS between-component covariance; overflow throws */
  def covariance: Vector[Vector[Double]] = {
    val m=mean
    val result=Vector.tabulate(dimension,dimension) { (j,k) => active.map { i =>
      normalized(i)*(components(i).covariance(j)(k)+(components(i).mean(j)-m(j))*(components(i).mean(k)-m(k)))
    }.sum }
    if(!result.flatten.forall(_.isFinite)) throw new ArithmeticException("mixture covariance overflow")
    result
  }
  /** @param x finite coordinate vector
    * @return posterior component membership probabilities; this does not fit or update the model
    * @example `gmm.responsibilities(Vector(0.0,1.0))`
    */
  def responsibilities(x: Vector[Double]): Vector[Double] = {
    val log=logDensity(x); if(!log.isFinite) throw new ArithmeticException("unresolved mixture likelihood")
    components.indices.map(i => if(normalized(i) == 0) 0.0 else math.exp(math.log(normalized(i))+components(i).logDensity(x)-log)).toVector
  }
  /** @param indices distinct valid coordinate indices
    * @return exact mixture marginal, retaining component weights/order
    * @example `gmm.marginal(Vector(0))`
    */
  def marginal(indices: Vector[Int]): GaussianMixtureDistribution = GaussianMixtureDistribution(weights,components.map(_.marginal(indices)))
}
