package com.cra.figaro.library.atomic.continuous

import com.cra.figaro.language.*
import com.cra.figaro.library.atomic.DistributionNumerics as N
import org.apache.commons.math3.special.Gamma as GammaFunction

/** Full-rank elliptical Student t, with a COMMON random scale for all coordinates.
  * @param degreesOfFreedom finite degrees of freedom in [0.001,1e6]
  * @param location 1..128 finite locations, absolute value at most 1e100
  * @param shape positive-definite shape matrix, NOT covariance; Gaussian kernel limits apply
  * @example `MultivariateStudentTDistribution(5,Vector(0.0,0.0),Vector(Vector(1.0,.5),Vector(.5,1.0)))`
  */
final case class MultivariateStudentTDistribution(degreesOfFreedom: Double,
  location: Vector[Double], shape: Vector[Vector[Double]]) {
  N.shape(degreesOfFreedom)
  private val gaussian=MultivariateGaussianDistribution(location,shape)
  val dimension: Int=gaussian.dimension
  private val logNormalizer=GammaFunction.logGamma((degreesOfFreedom+dimension)/2)-
    GammaFunction.logGamma(degreesOfFreedom/2)-.5*(dimension*math.log(degreesOfFreedom*math.Pi)+gaussian.logDeterminant)
  /** @param x finite vector of matching dimension
    * @return natural-log Lebesgue density; scaled whitening avoids squaring huge residuals
    * @example `law.logDensity(Vector(0.0,1.0))`
    */
  def logDensity(x: Vector[Double]): Double = {
    N.check(); require(x!=null && x.size==dimension && x.forall(_.isFinite))
    val delta=x.zip(location).map(_-_); val largest=delta.map(math.abs).max
    if(largest==0) logNormalizer
    else {
      val z=gaussian.whiten(delta.map(_/largest)); val magnitude=z.map(math.abs).max
      val logSquare=2*math.log(largest)+2*math.log(magnitude)+math.log(z.map(v => math.pow(v/magnitude,2)).sum)
      val result=logNormalizer-(degreesOfFreedom+dimension)/2*N.log1pExp(logSquare-math.log(degreesOfFreedom))
      if(!result.isFinite) throw new ArithmeticException("Student t log density unresolved")
      result
    }
  }
  /** @param x finite vector
    * @return ordinary density, possibly underflowing to zero
    * @example `law.density(Vector(0.0,1.0))`
    */
  def density(x: Vector[Double]): Double=math.exp(logDensity(x))
  /** @return location if df>1, otherwise None: a location is not always a mean */
  def mean: Option[Vector[Double]]=if(degreesOfFreedom>1) Some(location) else None
  /** @return finite covariance df/(df-2)*shape if df>2, otherwise None */
  def covariance: Option[Vector[Vector[Double]]]=
    if(degreesOfFreedom>2) Some(shape.map(_.map(_*(degreesOfFreedom/(degreesOfFreedom-2))))) else None
  /** @param indices nonempty distinct valid coordinates in output order
    * @return exact t marginal, retaining degrees of freedom
    * @example `law.marginal(Vector(1))`
    */
  def marginal(indices: Vector[Int]): MultivariateStudentTDistribution = {
    val g=gaussian.marginal(indices); MultivariateStudentTDistribution(degreesOfFreedom,g.mean,g.covariance)
  }
  /** @param rng exclusively caller-owned RNG, never retained
    * @return finite location + Gaussian / sqrt(ChiSquare(df)/df) draw
    * Scale underflow/overflow or 10000 RNG calls without completion throws an arithmetic exception; no resampling bias.
    * @example `law.sample(com.cra.figaro.util.SamplingRandom.scalaRandom(42))`
    */
  def sample(rng: scala.util.Random): Vector[Double] = {
    require(rng!=null); N.check()
    var calls=0
    def tick(): Unit = { N.check(); calls+=1; if(calls>10000) throw new ArithmeticException("Student t random work cap exceeded") }
    val adapter=new org.apache.commons.math3.random.AbstractRandomGenerator {
      def setSeed(seed: Long): Unit=throw new UnsupportedOperationException("Caller owns RNG seed")
      def nextDouble(): Double={ tick(); rng.nextDouble() }
      override def nextGaussian(): Double={ tick(); rng.nextGaussian() }
    }
    val chi=new org.apache.commons.math3.distribution.GammaDistribution(adapter,degreesOfFreedom/2,2).sample()
    N.check(); val divisor=math.sqrt(chi/degreesOfFreedom)
    if(!(divisor>0) || !divisor.isFinite) throw new ArithmeticException("Student t random scale outside numeric range")
    val z=Vector.fill(dimension)(rng.nextGaussian())
    val x=location.indices.map(i => location(i)+(0 to i).map(j => gaussian.lower(i)(j)*z(j)).sum/divisor).toVector
    N.check(); if(!x.forall(_.isFinite)) throw new ArithmeticException("Student t draw outside numeric range")
    x
  }
}

/** Fixed-kernel observation-ready element; randomness is the complete vector draw. */
final class AtomicMultivariateStudentT private[continuous](name: Name[Vector[Double]],
  val distribution: MultivariateStudentTDistribution,collection: ElementCollection)
  extends Element[Vector[Double]](name,collection) with Atomic[Vector[Double]] with Continuous[Vector[Double]] with HasLogDensity[Vector[Double]] {
  type Randomness=Vector[Double]
  def generateRandomness(): Vector[Double]=distribution.sample(com.cra.figaro.util.random)
  def generateValue(value: Vector[Double]): Vector[Double]=value
  def logDensity(value: Vector[Double]): Double=distribution.logDensity(value)
  def logp(value: Vector[Double]): Double=logDensity(value)
}

object MultivariateStudentT {
  /** @param distribution validated full-rank t kernel
    * @param name contextual name
    * @param collection owning collection
    * @return registered atomic vector element
    * @example `MultivariateStudentT(law)`
    */
  def apply(distribution: MultivariateStudentTDistribution)(using name: Name[Vector[Double]],collection: ElementCollection): AtomicMultivariateStudentT = {
    require(distribution!=null); new AtomicMultivariateStudentT(name,distribution,collection)
  }
  /** @param distribution graph element producing validated t kernels
    * @param name contextual name
    * @param collection owning collection
    * @return non-caching hierarchical element; invalid parameters fail at evaluation
    * @example `MultivariateStudentT(df.map(n => MultivariateStudentTDistribution(n,center,shape)))`
    */
  def apply(distribution: Element[MultivariateStudentTDistribution])(using name: Name[Vector[Double]],collection: ElementCollection): Element[Vector[Double]] = {
    require(distribution!=null); NonCachingChain(distribution,(d: MultivariateStudentTDistribution) => apply(d)(using "",collection))
  }
}
