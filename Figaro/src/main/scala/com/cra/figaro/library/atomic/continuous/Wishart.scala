package com.cra.figaro.library.atomic.continuous

import com.cra.figaro.language.*
import com.cra.figaro.library.atomic.{DistributionNumerics as N,InformationMetricResult,InformationMetricStatus,MetricCalculation as M}
import org.apache.commons.math3.special.Gamma

/** Full-rank Wishart in the independent symmetric-entry Lebesgue measure.
  * @param degreesOfFreedom real df in [dimension+1,1e6]; deliberately excludes the near-singular df boundary
  * @param scale symmetric positive-definite 1..16 matrix; diagonal in [1e-100,1e100], correlation condition <=1e10
  * Scale is NOT the mean: E[X]=df*scale. No implicit jitter or symmetrization.
  */
final case class WishartDistribution(degreesOfFreedom: Double,scale: Vector[Vector[Double]]) {
  require(scale!=null && scale.nonEmpty && scale.size<=16)
  val dimension: Int=scale.size
  require(degreesOfFreedom.isFinite && degreesOfFreedom>=dimension+1 && degreesOfFreedom<=1e6)
  require(scale.forall(r => r!=null && r.size==dimension))
  require(scale.indices.forall(i => scale(i)(i)>=1e-100 && scale(i)(i)<=1e100))
  private[continuous] val gaussian=MultivariateGaussianDistribution(Vector.fill(dimension)(0.0),scale)
  private[continuous] val logPartition: Double=WishartInformation.partition(degreesOfFreedom,dimension,gaussian.logDeterminant)
  /** @return mean matrix df*scale */
  def mean: Vector[Vector[Double]]=scale.map(_.map(_*degreesOfFreedom))
  /** @param i first row index
    * @param j first column index
    * @param k second row index
    * @param l second column index
    * @return Cov(Xij,Xkl)=df*(Sik*Sjl+Sil*Sjk)
    * @example `law.covariance(0,0,1,1)`
    */
  def covariance(i: Int,j: Int,k: Int,l: Int): Double = {
    N.check(); require(Vector(i,j,k,l).forall(v => v>=0 && v<dimension))
    degreesOfFreedom*(scale(i)(k)*scale(j)(l)+scale(i)(l)*scale(j)(k))
  }
  /** @param value exactly symmetric, finite positive-definite matrix in the Gaussian numeric range
    * @return log density in independent symmetric coordinates; invalid/ill-conditioned matrices throw, not zero likelihood
    * @example `law.logDensity(Vector(Vector(4.0,1.0),Vector(1.0,3.0)))`
    */
  def logDensity(value: Vector[Vector[Double]]): Double = {
    N.check(); val x=CovarianceNumerics.matrix(value,dimension)
    val trace=WishartInformation.traceRatio(x,gaussian)
    val out=.5*(degreesOfFreedom-dimension-1)*x.logDeterminant-.5*trace-logPartition
    if(!out.isFinite) throw new ArithmeticException("Wishart log density outside numeric range")
    out
  }
  /** @param value positive-definite matrix
    * @return density, possibly under/overflowing after a finite log density
    * @example `law.density(law.mean)`
    */
  def density(value: Vector[Vector[Double]]): Double=math.exp(logDensity(value))
  /** Bartlett decomposition with caller-owned Gaussian/chi-square randomness.
    * @param rng private caller RNG, never retained
    * @return symmetric positive-definite matrix; numeric singularity throws without resampling
    * @example `law.sample(com.cra.figaro.util.SamplingRandom.scalaRandom(42))`
    */
  def sample(rng: scala.util.Random): Vector[Vector[Double]] = {
    N.check(); require(rng!=null)
    var calls=0
    def tick(): Unit={ N.check(); calls+=1; if(calls>100000) throw new ArithmeticException("Wishart RNG work budget exceeded") }
    val adapter=new org.apache.commons.math3.random.AbstractRandomGenerator {
      def setSeed(seed: Long): Unit=throw new UnsupportedOperationException("Caller owns seed")
      def nextDouble(): Double={ tick(); rng.nextDouble() }
      override def nextGaussian(): Double={ tick(); rng.nextGaussian() }
    }
    val a=Array.ofDim[Double](dimension,dimension)
    for(i <- 0 until dimension) {
      a(i)(i)=math.sqrt(new org.apache.commons.math3.distribution.GammaDistribution(adapter,(degreesOfFreedom-i)/2,2).sample())
      for(j <- 0 until i) a(i)(j)=adapter.nextGaussian()
    }
    val b=Array.ofDim[Double](dimension,dimension)
    for(i <- 0 until dimension;j <- 0 to i) { N.check(); b(i)(j)=(j to i).map(k => gaussian.lower(i)(k)*a(k)(j)).sum }
    val x=Array.ofDim[Double](dimension,dimension)
    for(i <- 0 until dimension;j <- 0 to i) {
      N.check(); val v=(0 to j).map(k => b(i)(k)*b(j)(k)).sum
      x(i)(j)=v; x(j)(i)=v
    }
    val value=x.map(_.toVector).toVector
    try MultivariateGaussianDistribution(Vector.fill(dimension)(0.0),value)
    catch { case e: IllegalArgumentException => throw new ArithmeticException("Wishart draw numerically unresolved: "+e.getMessage) }
    value
  }
}

/** Same-dimension Wishart information, using Cholesky solves and exponential-family normalizers. */
object WishartInformation {
  private[continuous] def partition(df: Double,d: Int,logDet: Double): Double =
    .5*df*d*math.log(2)+.5*df*logDet+.25*d*(d-1)*math.log(math.Pi)+(0 until d).map(i => Gamma.logGamma((df-i)/2)).sum
  private[continuous] def traceRatio(p: MultivariateGaussianDistribution,q: MultivariateGaussianDistribution): Double = {
    (0 until p.dimension).map { j => N.check(); q.whiten(p.lower.map(_(j))).map(x => x*x).sum }.sum
  }
  private def compare(p: WishartDistribution,q: WishartDistribution,t: Double,bh: Boolean): InformationMetricResult = {
    N.check(); require(p!=null && q!=null && p.dimension==q.dimension && t.isFinite && t>0)
    if(p==q) return M.identity
    try {
      val d=p.dimension; val n=p.degreesOfFreedom; val m=q.degreesOfFreedom
      var conditioning=d*math.max(p.gaussian.conditionNumber,q.gaussian.conditionNumber)
      var magnitude=1+math.abs(p.logPartition)+math.abs(q.logPartition)+(n+m)*d
      val value=if(!bh) {
        val elog=d*math.log(2)+p.gaussian.logDeterminant+(0 until d).map(i => N.digammaPositive((n-i)/2)).sum
        val trace=traceRatio(p.gaussian,q.gaussian)
        magnitude+=math.abs((n-m)*elog)+n*(trace+d)
        .5*(n-m)*elog+.5*n*(trace-d)+q.logPartition-p.logPartition
      } else {
        def precision(g: MultivariateGaussianDistribution): Vector[Vector[Double]] = {
          val cols=Vector.tabulate(d)(j => g.whiten(Vector.tabulate(d)(i => if(i==j) 1.0 else 0.0)))
          Vector.tabulate(d,d)((i,j) => cols(i).zip(cols(j)).map(_*_).sum)
        }
        val a=precision(p.gaussian); val b=precision(q.gaussian)
        val average=Vector.tabulate(d,d)((i,j) => .5*a(i)(j)+.5*b(i)(j))
        val precisionModel=MultivariateGaussianDistribution(Vector.fill(d)(0.0),average)
        conditioning=math.max(conditioning,d*precisionModel.conditionNumber)
        val middle=partition(.5*(n+m),d,-precisionModel.logDeterminant)
        magnitude+=math.abs(middle)
        .5*(p.logPartition+q.logPartition)-middle
      }
      M.analytic(value,t,magnitude*conditioning,"Wishart exponential-family normalizer")
    } catch {
      case _: ArithmeticException | _: IllegalArgumentException | _: org.apache.commons.math3.exception.MathIllegalArgumentException =>
        M.unavailable(InformationMetricStatus.NumericallyUnresolved,0,"Wishart matrix arithmetic")
    }
  }
  /** @param p source Wishart law
    * @param q matching-dimension comparison law
    * @param tolerance positive numerical target in nats
    * @return directed analytic KL or numerical refusal; no estimated sampling interval
    * @example `WishartInformation.kl(p,q)`
    */
  def kl(p: WishartDistribution,q: WishartDistribution,tolerance: Double=1e-8): InformationMetricResult=compare(p,q,tolerance,false)
  /** @param p first Wishart law
    * @param q second law in matching coordinates
    * @param tolerance positive numerical target in nats
    * @return analytic negative log affinity or numerical refusal
    * @example `WishartInformation.bhattacharyya(p,q)`
    */
  def bhattacharyya(p: WishartDistribution,q: WishartDistribution,tolerance: Double=1e-8): InformationMetricResult=compare(p,q,tolerance,true)
}

/** Prior-proposal matrix adapter; no arbitrary Euclidean matrix perturbation is supplied. */
final class AtomicWishart private[continuous](name: Name[Vector[Vector[Double]]],val distribution: WishartDistribution,collection: ElementCollection)
  extends Element[Vector[Vector[Double]]](name,collection) with Atomic[Vector[Vector[Double]]] with HasLogDensity[Vector[Vector[Double]]] {
  type Randomness=Vector[Vector[Double]]
  def generateRandomness(): Vector[Vector[Double]]=distribution.sample(com.cra.figaro.util.random)
  def generateValue(value: Vector[Vector[Double]]): Vector[Vector[Double]]=value
  def logDensity(value: Vector[Vector[Double]]): Double=distribution.logDensity(value)
}
object Wishart {
  /** @param distribution validated full-rank matrix kernel
    * @param name contextual name
    * @param collection owning collection
    * @return observation-ready Wishart element
    * @example `Wishart(WishartDistribution(5,Vector(Vector(1.0))))`
    */
  def apply(distribution: WishartDistribution)(using name: Name[Vector[Vector[Double]]],collection: ElementCollection): AtomicWishart = {
    require(distribution!=null); new AtomicWishart(name,distribution,collection)
  }
  /** @param distribution element producing matrix kernels
    * @param name contextual name
    * @param collection owning collection
    * @return hierarchical non-caching matrix element
    * @example `Wishart(df.map(n => WishartDistribution(n,scale)))`
    */
  def apply(distribution: Element[WishartDistribution])(using name: Name[Vector[Vector[Double]]],collection: ElementCollection): Element[Vector[Vector[Double]]] = {
    require(distribution!=null); NonCachingChain(distribution,(d: WishartDistribution) => apply(d)(using "",collection))
  }
}
