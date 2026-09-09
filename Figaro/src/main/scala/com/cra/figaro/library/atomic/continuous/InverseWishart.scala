package com.cra.figaro.library.atomic.continuous

import com.cra.figaro.language.*
import com.cra.figaro.library.atomic.{DistributionNumerics as N,InformationMetricResult,InformationMetricStatus as S,MetricCalculation as M}

/** Full-rank inverse-Wishart in independent symmetric-entry coordinates.
  * @param degreesOfFreedom real df in [dimension+1,1e6], a deliberate numerical subset of df>dimension-1
  * @param scale positive-definite 1..16 matrix Psi, NOT its inverse or the mean; diagonal [1e-100,1e100]
  * Density contains exp(-trace(Psi*inverse(value))/2). No singular measure/jitter.
  */
final case class InverseWishartDistribution(degreesOfFreedom: Double,scale: Vector[Vector[Double]]) {
  require(scale!=null && scale.nonEmpty && scale.size<=16)
  val dimension: Int=scale.size
  require(degreesOfFreedom.isFinite && degreesOfFreedom>=dimension+1 && degreesOfFreedom<=1e6)
  private[continuous] val gaussian=CovarianceNumerics.matrix(scale,dimension)
  require(scale.indices.forall(i => scale(i)(i)>=1e-100 && scale(i)(i)<=1e100))
  private[continuous] val logPartition=WishartInformation.partition(degreesOfFreedom,dimension,-gaussian.logDeterminant)
  /** @return Some(Psi/(df-dimension-1)) when the mean exists, otherwise None
    * @example `InverseWishartDistribution(5,Vector(Vector(2.0))).mean` */
  def mean: Option[Vector[Vector[Double]]] = {
    val denominator=degreesOfFreedom-dimension-1
    if(denominator<=0) None else Some(scale.map(_.map(_/denominator)))
  }
  /** @return mode Psi/(df+dimension+1), not the mean
    * @example `law.mode` */
  def mode: Vector[Vector[Double]] = scale.map(_.map(_/(degreesOfFreedom+dimension+1)))
  /** @param value matching-dimension positive-definite symmetric matrix
    * @return log density; malformed/ill-conditioned input throws rather than returning a false zero likelihood
    * @example `law.logDensity(law.mode)` */
  def logDensity(value: Vector[Vector[Double]]): Double = {
    val x=CovarianceNumerics.matrix(value,dimension)
    val out = -.5*(degreesOfFreedom+dimension+1)*x.logDeterminant-.5*WishartInformation.traceRatio(gaussian,x)-logPartition
    if(!out.isFinite) throw new ArithmeticException("Inverse-Wishart log density outside numeric range")
    out
  }
  /** @param value covariance matrix
    * @return exp(logDensity), possibly under/overflowing
    * @example `law.density(law.mode)` */
  def density(value: Vector[Vector[Double]]): Double=math.exp(logDensity(value))
  /** @param lower positive-diagonal lower triangular factor
    * @return log density on independent lower entries, INCLUDING the covariance Jacobian
    * @example `law.logDensityCholesky(law.sampleCholesky(rng))` */
  def logDensityCholesky(lower: Vector[Vector[Double]]): Double = {
    CovarianceNumerics.lower(lower,dimension,false)
    logDensity(CovarianceNumerics.gram(lower))+dimension*math.log(2)+
      (0 until dimension).map(i => (dimension-i)*math.log(lower(i)(i))).sum
  }
  /** Direct triangular sampler from Axen (2023), Theorem 3.2 / Algorithm 4.
    * @param rng caller-owned RNG, never retained
    * @return lower factor; bounded RNG requests, numeric failure throws without redraw
    * @example `law.sampleCholesky(com.cra.figaro.util.SamplingRandom.scalaRandom(42))` */
  def sampleCholesky(rng: scala.util.Random): Vector[Vector[Double]] = {
    N.check(); val random=CovarianceNumerics.adapter(rng)
    val z=Array.ofDim[Double](dimension,dimension)
    for(j <- 0 until dimension) {
      for(i <- 0 until j) z(i)(j)=random.nextGaussian()
      z(j)(j)=math.sqrt(new org.apache.commons.math3.distribution.GammaDistribution(random,(degreesOfFreedom-dimension+j+1)/2,2).sample())
    }
    // Solve Z * U = transpose(cholesky(Psi)); no dense matrix inversion.
    val upper=Array.ofDim[Double](dimension,dimension)
    for(j <- 0 until dimension; i <- j to 0 by -1) {
      N.check()
      upper(i)(j)=(gaussian.lower(j)(i)-(i+1 to j).map(k => z(i)(k)*upper(k)(j)).sum)/z(i)(i)
    }
    val lower=Vector.tabulate(dimension,dimension)((i,j) => upper(j)(i))
    CovarianceNumerics.validateDraw(lower,false)
  }
  /** @param rng caller-owned RNG
    * @return symmetric covariance matrix; same numeric refusal contract as sampleCholesky
    * @example `law.sample(rng)` */
  def sample(rng: scala.util.Random): Vector[Vector[Double]]=CovarianceNumerics.gram(sampleCholesky(rng))
}

/** Same-coordinate inverse-Wishart comparisons, without explicitly inverting scale matrices. */
object InverseWishartInformation {
  private def compare(p: InverseWishartDistribution,q: InverseWishartDistribution,t: Double,bh: Boolean): InformationMetricResult = {
    N.check(); require(p!=null && q!=null && p.dimension==q.dimension && t.isFinite && t>0)
    if(p==q) return M.identity
    try {
      val d=p.dimension; val n=p.degreesOfFreedom; val m=q.degreesOfFreedom
      var condition=d*math.max(p.gaussian.conditionNumber,q.gaussian.conditionNumber)
      val terms=if(bh) {
        val scale=Vector.tabulate(d,d)((i,j) => .5*p.scale(i)(j)+.5*q.scale(i)(j))
        val middle=InverseWishartDistribution(.5*(n+m),scale)
        condition=math.max(condition,d*middle.gaussian.conditionNumber)
        Vector(.5*p.logPartition,.5*q.logPartition,-middle.logPartition)
      } else {
        val elog=p.gaussian.logDeterminant-d*math.log(2)-(0 until d).map(i => N.digammaPositive((n-i)/2)).sum
        val trace=WishartInformation.traceRatio(q.gaussian,p.gaussian)
        Vector(.5*(m-n)*elog,.5*n*trace,-.5*n*d,q.logPartition,-p.logPartition)
      }
      M.analytic(terms.sum,t,(terms.map(math.abs).sum+(n+m)*d)*condition,"Inverse-Wishart normalizer")
    } catch {
      case _: ArithmeticException | _: IllegalArgumentException | _: org.apache.commons.math3.exception.MathIllegalArgumentException =>
        M.unavailable(S.NumericallyUnresolved,0,"Inverse-Wishart matrix arithmetic")
    }
  }
  /** @param p source law
    * @param q same-dimension comparison law
    * @param tolerance positive numerical target in nats
    * @return directed analytic KL or numerical refusal
    * @example `InverseWishartInformation.kl(p,q)` */
  def kl(p: InverseWishartDistribution,q: InverseWishartDistribution,tolerance: Double=1e-8): InformationMetricResult=compare(p,q,tolerance,false)
  /** @param p first law
    * @param q same-dimension comparison law
    * @param tolerance positive numerical target in nats
    * @return symmetric negative log affinity or numerical refusal
    * @example `InverseWishartInformation.bhattacharyya(p,q)` */
  def bhattacharyya(p: InverseWishartDistribution,q: InverseWishartDistribution,tolerance: Double=1e-8): InformationMetricResult=compare(p,q,tolerance,true)
}

/** Observation-ready covariance element with prior independence MH proposals. */
final class AtomicInverseWishart private[continuous](name: Name[Vector[Vector[Double]]],val distribution: InverseWishartDistribution,collection: ElementCollection)
  extends Element[Vector[Vector[Double]]](name,collection) with Atomic[Vector[Vector[Double]]] with HasLogDensity[Vector[Vector[Double]]] {
  type Randomness=Vector[Vector[Double]]
  def generateRandomness(): Randomness=distribution.sample(com.cra.figaro.util.random)
  def generateValue(value: Randomness): Randomness=value
  def logDensity(value: Randomness): Double=distribution.logDensity(value)
}
object InverseWishart {
  /** @param distribution validated covariance law
    * @param name contextual name
    * @param collection owning collection
    * @return observation-ready matrix element
    * @example `InverseWishart(InverseWishartDistribution(5,Vector(Vector(2.0))))` */
  def apply(distribution: InverseWishartDistribution)(using name: Name[Vector[Vector[Double]]],collection: ElementCollection): AtomicInverseWishart = {
    require(distribution!=null); new AtomicInverseWishart(name,distribution,collection)
  }
  /** @param distribution element yielding covariance kernels
    * @param name contextual name
    * @param collection owning collection
    * @return hierarchical non-caching matrix element
    * @example `InverseWishart(df.map(n => InverseWishartDistribution(n,scale)))` */
  def apply(distribution: Element[InverseWishartDistribution])(using name: Name[Vector[Vector[Double]]],collection: ElementCollection): Element[Vector[Vector[Double]]] = {
    require(distribution!=null); NonCachingChain(distribution,(d: InverseWishartDistribution) => apply(d)(using "",collection))
  }
}
