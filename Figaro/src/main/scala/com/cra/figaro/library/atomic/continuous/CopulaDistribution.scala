package com.cra.figaro.library.atomic.continuous

import com.cra.figaro.language.*
import com.cra.figaro.util.random
import com.cra.figaro.library.atomic.DistributionNumerics as N
import com.cra.figaro.algorithm.sampling.VectorImportance

/** Continuous Gaussian or Student-t copula with caller-declared continuous marginals.
  * @param marginals 1..32 immutable continuous scalar laws, not count/mixed laws
  * @param correlation positive definite, exactly symmetric matrix with unit diagonal;
  *        latent correlation, NOT generally the Pearson correlation of physical outputs
  * @param degreesOfFreedom None for Gaussian, Some(df) for t; df in [0.1,1e6]
  * Finite CDF/quantile resolution is checked explicitly; no clipping or redraws.
  */
final case class CopulaDistribution(marginals: Vector[ScalarDistribution],correlation: Vector[Vector[Double]],degreesOfFreedom: Option[Double]=None)
  extends VectorImportance.Proposal {
  require(marginals!=null && marginals.nonEmpty && marginals.size<=32 && marginals.forall(_!=null))
  val dimension: Int=marginals.size
  require(correlation!=null && correlation.size==dimension && correlation.forall(r=>r!=null && r.size==dimension))
  require(correlation.indices.forall(i=>correlation(i)(i)==1.0),"Copula correlation must have unit diagonal")
  require(degreesOfFreedom!=null && degreesOfFreedom.forall(df=>df.isFinite && df>=.1 && df<=1e6))
  private val gaussian=MultivariateGaussianDistribution(Vector.fill(dimension)(0.0),correlation)
  private val student=degreesOfFreedom.map(df=>MultivariateStudentTDistribution(df,Vector.fill(dimension)(0.0),correlation))
  private val scalar: ScalarDistribution=degreesOfFreedom.map(df=>StudentTDistribution(df): ScalarDistribution).getOrElse(GaussianDistribution(0,1))
  private def latentLog(x: Vector[Double]): Double=student.map(_.logDensity(x)).getOrElse(gaussian.logDensity(x))
  private def checkedProbability(p: Double): Double = {
    if(!p.isFinite || p<=0 || p>=1) throw new ArithmeticException("Copula probability rounded to a boundary; no clipping is performed")
    p
  }
  private def probability(law: ScalarDistribution,x: Double): Double = {
    val lower=law.cdf(x); val upper=law.survival(x)
    if(!lower.isFinite || !upper.isFinite || lower<0 || upper<0 || lower>1 || upper>1 || math.abs(lower+upper-1)>1e-10)
      throw new ArithmeticException("Inconsistent copula marginal probabilities")
    checkedProbability(if(lower<=upper) lower else 1-upper)
  }
  /** @param x finite physical-coordinate vector @return full joint Lebesgue log density
    * @example `law.logDensity(Vector(1,2))`; includes all marginal densities and the copula correction
    */
  def logDensity(x: Vector[Double]): Double = {
    require(x!=null && x.size==dimension && x.forall(_.isFinite)); N.check()
    val logMarginals=marginals.indices.map(i=>marginals(i).logDensity(x(i))).toVector
    if(logMarginals.exists(_==Double.NegativeInfinity)) return Double.NegativeInfinity
    if(!logMarginals.forall(_.isFinite)) throw new ArithmeticException("Singular copula marginal density")
    val z=marginals.indices.map(i=>scalar.quantile(probability(marginals(i),x(i)))).toVector
    val result=latentLog(z)-z.map(scalar.logDensity).sum+logMarginals.sum
    if(!result.isFinite) throw new ArithmeticException("Copula log density unresolved")
    result
  }
  /** @param rng caller-owned RNG @return dependent draw with the declared marginals
    * @example `law.sample(com.cra.figaro.util.SamplingRandom.scalaRandom(42))`
    */
  def sample(rng: scala.util.Random): Vector[Double] = {
    require(rng!=null); N.check()
    val z=student.map(_.sample(rng)).getOrElse(gaussian.sample(rng))
    val x=marginals.indices.map(i=>marginals(i).quantile(probability(scalar,z(i)))).toVector
    if(!x.forall(_.isFinite)) throw new ArithmeticException("Copula inverse transform overflow")
    // Verify the resulting state can be scored; refusing a draw does not silently resample it.
    logDensity(x); x
  }
  /** @param indices nonempty distinct valid coordinates in desired order
    * @return exact marginal copula law; this is not a conditional distribution
    * @example `law.marginal(Vector(0))` */
  def marginal(indices: Vector[Int]): CopulaDistribution = {
    require(indices!=null && indices.nonEmpty && indices.distinct.size==indices.size && indices.forall(i=>i>=0 && i<dimension))
    CopulaDistribution(indices.map(marginals),indices.map(i=>indices.map(correlation(i))),degreesOfFreedom)
  }
  /** @param first nonempty proper subset of coordinates
    * @return analytic MI in nats between these coordinates and their complement
    * for a Gaussian copula, invariant to invertible continuous marginal transforms.
    * Student-t copulas require explicit numerical estimation and are refused here.
    * @example `law.gaussianPartitionMutualInformation(Vector(0))` */
  def gaussianPartitionMutualInformation(first: Vector[Int]): Double = {
    require(degreesOfFreedom.isEmpty,"Analytic partition MI is Gaussian-only")
    require(first!=null && first.nonEmpty && first.size<dimension && first.distinct.size==first.size && first.forall(i=>i>=0 && i<dimension))
    val second=(0 until dimension).filterNot(first.contains).toVector
    val value=.5*(gaussian.marginal(first).logDeterminant+gaussian.marginal(second).logDeterminant-gaussian.logDeterminant)
    if(!value.isFinite || value < -1e-10) throw new ArithmeticException("Copula MI unresolved")
    math.max(0,value)
  }
}

final class AtomicCopula private[continuous](name: Name[Vector[Double]],val distribution: CopulaDistribution,collection: ElementCollection)
  extends Element[Vector[Double]](name,collection) with Atomic[Vector[Double]] with Continuous[Vector[Double]] with HasLogDensity[Vector[Double]] {
  type Randomness=Vector[Double]
  def generateRandomness(): Vector[Double]=distribution.sample(random)
  def generateValue(value: Vector[Double]): Vector[Double]=value
  def logDensity(value: Vector[Double]): Double=distribution.logDensity(value)
  def logp(value: Vector[Double]): Double=logDensity(value)
}
object CopulaElement {
  /** @param distribution fixed continuous copula @param name name @param collection owning graph
    * @return observation-ready vector element; not mixed-marginal or missing-coordinate integration
    * @example `CopulaElement(law)` */
  def apply(distribution: CopulaDistribution)(using name: Name[Vector[Double]],collection: ElementCollection): AtomicCopula = {
    require(distribution!=null); new AtomicCopula(name,distribution,collection)
  }
}
