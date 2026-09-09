package com.cra.figaro.library.atomic.continuous

import com.cra.figaro.language.*
import com.cra.figaro.library.atomic.{DistributionNumerics as N,InformationMetricResult,MetricCalculation as M}
import org.apache.commons.math3.special.Gamma

/** LKJ law on independent off-diagonal correlation entries, NOT on covariance matrices.
  * @param dimension matrix order in 2..16
  * @param shape eta in [.001,1e6]; eta=1 is uniform jointly, not marginally when dimension>2
  * Numeric matrix checks follow MultivariateGaussianDistribution; no jitter/resampling.
  */
final case class LKJDistribution(dimension: Int, shape: Double = 1.0) {
  require(dimension>=2 && dimension<=16); N.shape(shape)
  private[continuous] val logPartition: Double = LKJInformation.partition(dimension,shape)
  /** @return expected correlation matrix (identity), not the covariance of its entries
    * @example `LKJDistribution(3,2).mean` */
  def mean: Vector[Vector[Double]] = Vector.tabulate(dimension,dimension)((i,j) => if(i==j) 1.0 else 0.0)
  /** @param value exactly symmetric positive-definite matrix with exact unit diagonal
    * @return log density in independent off-diagonal coordinates; invalid/numerically unresolved input throws
    * @example `law.logDensity(law.mean)` */
  def logDensity(value: Vector[Vector[Double]]): Double = {
    val g=CovarianceNumerics.matrix(value,dimension)
    require(value.indices.forall(i => value(i)(i)==1.0),"LKJ requires a correlation matrix")
    (shape-1)*g.logDeterminant-logPartition
  }
  /** @param value correlation matrix
    * @return exp(logDensity), possibly under/overflowing
    * @example `law.density(law.mean)` */
  def density(value: Vector[Vector[Double]]): Double = math.exp(logDensity(value))
  /** @param lower lower triangular factor with positive diagonal and unit-length rows
    * @return density log in free lower-factor entries, INCLUDING the correlation Jacobian
    * @example `law.logDensityCholesky(law.sampleCholesky(rng))` */
  def logDensityCholesky(lower: Vector[Vector[Double]]): Double = {
    CovarianceNumerics.lower(lower,dimension,true)
    (1 until dimension).map(i => (dimension-i-1+2*shape-2)*math.log(lower(i)(i))).sum-logPartition
  }
  /** Canonical-partial-correlation beta construction; no repair or retry of singular draws.
    * @param rng caller-owned RNG, never retained
    * @return lower Cholesky factor; unrepresentable/ill-conditioned draw throws
    * @example `law.sampleCholesky(com.cra.figaro.util.SamplingRandom.scalaRandom(42))` */
  def sampleCholesky(rng: scala.util.Random): Vector[Vector[Double]] = {
    N.check(); val random=CovarianceNumerics.adapter(rng)
    val l=Array.ofDim[Double](dimension,dimension); l(0)(0)=1
    for(i <- 1 until dimension) {
      var remaining=1.0
      for(j <- 0 until i) {
        val a=shape+.5*(dimension-j-2)
        val z=2*new org.apache.commons.math3.distribution.BetaDistribution(random,a,a).sample()-1
        if(!z.isFinite || math.abs(z)>=1) throw new ArithmeticException("LKJ partial correlation rounded to boundary")
        l(i)(j)=z*remaining
        remaining*=math.sqrt((1-z)*(1+z))
      }
      l(i)(i)=remaining
    }
    CovarianceNumerics.validateDraw(l.map(_.toVector).toVector,true)
  }
  /** @param rng caller-owned RNG
    * @return symmetric unit-diagonal correlation matrix; same refusal contract as sampleCholesky
    * @example `law.sample(rng)` */
  def sample(rng: scala.util.Random): Vector[Vector[Double]] = CovarianceNumerics.gram(sampleCholesky(rng),true)
  /** @param correlation validated correlation matrix
    * @param standardDeviations positive finite SDs, NOT variances, in matching coordinate order
    * @return covariance diag(sd)*correlation*diag(sd), within the Gaussian numerical contract
    * @example `law.toCovariance(law.mean,Vector(2.0,3.0))` */
  def toCovariance(correlation: Vector[Vector[Double]], standardDeviations: Vector[Double]): Vector[Vector[Double]] = {
    logDensity(correlation)
    require(standardDeviations!=null && standardDeviations.size==dimension && standardDeviations.forall(x => x.isFinite && x>0))
    val out=Vector.tabulate(dimension,dimension)((i,j) => {
      val a=math.min(i,j); val b=math.max(i,j)
      correlation(a)(b)*standardDeviations(a)*standardDeviations(b)
    })
    CovarianceNumerics.matrix(out,dimension); out
  }
}

/** Analytic same-dimension LKJ comparisons. Measures must match; neither method is matrix-entry MI. */
object LKJInformation {
  private[continuous] def partition(d: Int,eta: Double): Double = (0 until d-1).map { j =>
    val a=eta+.5*(d-j-2)
    (d-j-1)*(.5*math.log(math.Pi)+Gamma.logGamma(a)-Gamma.logGamma(a+.5))
  }.sum
  private def compare(p: LKJDistribution,q: LKJDistribution,t: Double,bh: Boolean): InformationMetricResult = {
    N.check(); require(p!=null && q!=null && p.dimension==q.dimension && t.isFinite && t>0)
    if(p==q) return M.identity
    val d=p.dimension
    val terms=if(bh) Vector(.5*p.logPartition,.5*q.logPartition,-partition(d,.5*(p.shape+q.shape)))
    else {
      val e=(0 until d-1).map { j => val a=p.shape+.5*(d-j-2); (d-j-1)*(N.digammaPositive(a)-N.digammaPositive(a+.5)) }.sum
      Vector(q.logPartition,-p.logPartition,(p.shape-q.shape)*e)
    }
    M.analytic(terms.sum,t,terms.map(math.abs).sum+d*d*(p.shape+q.shape+1),"LKJ log-normalizer")
  }
  /** @param p source law
    * @param q same-dimension comparison law
    * @param tolerance positive numerical target in nats
    * @return directed analytic KL or numerical refusal, not a sampling confidence bound
    * @example `LKJInformation.kl(LKJDistribution(3,1),LKJDistribution(3,2))` */
  def kl(p: LKJDistribution,q: LKJDistribution,tolerance: Double=1e-8): InformationMetricResult=compare(p,q,tolerance,false)
  /** @param p first law
    * @param q same-dimension law
    * @param tolerance positive numerical target in nats
    * @return symmetric negative log affinity or numerical refusal
    * @example `LKJInformation.bhattacharyya(p,q)` */
  def bhattacharyya(p: LKJDistribution,q: LKJDistribution,tolerance: Double=1e-8): InformationMetricResult=compare(p,q,tolerance,true)
}

/** Correlation-matrix element with prior independence MH proposals. */
final class AtomicLKJ private[continuous](name: Name[Vector[Vector[Double]]],val distribution: LKJDistribution,collection: ElementCollection)
  extends Element[Vector[Vector[Double]]](name,collection) with Atomic[Vector[Vector[Double]]] with HasLogDensity[Vector[Vector[Double]]] {
  type Randomness=Vector[Vector[Double]]
  def generateRandomness(): Randomness=distribution.sample(com.cra.figaro.util.random)
  def generateValue(value: Randomness): Randomness=value
  def logDensity(value: Randomness): Double=distribution.logDensity(value)
}
object LKJ {
  /** @param distribution validated correlation law
    * @param name contextual name
    * @param collection owning collection
    * @return observation-ready matrix element
    * @example `LKJ(LKJDistribution(3,2))` */
  def apply(distribution: LKJDistribution)(using name: Name[Vector[Vector[Double]]],collection: ElementCollection): AtomicLKJ = {
    require(distribution!=null); new AtomicLKJ(name,distribution,collection)
  }
  /** @param distribution element yielding validated LKJ kernels
    * @param name contextual name
    * @param collection owning collection
    * @return hierarchical non-caching correlation element
    * @example `LKJ(eta.map(e => LKJDistribution(3,e)))` */
  def apply(distribution: Element[LKJDistribution])(using name: Name[Vector[Vector[Double]]],collection: ElementCollection): Element[Vector[Vector[Double]]] = {
    require(distribution!=null); NonCachingChain(distribution,(d: LKJDistribution) => apply(d)(using "",collection))
  }
}
