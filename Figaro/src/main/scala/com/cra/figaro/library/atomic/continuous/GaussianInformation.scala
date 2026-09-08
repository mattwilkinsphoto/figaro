package com.cra.figaro.library.atomic.continuous

import com.cra.figaro.library.atomic.{DistributionNumerics as N,InformationMetricResult,InformationMetricStatus,MetricCalculation as M}

/** Gaussian information diagnostics in nats; no mixture-to-Gaussian substitution.
  * Uses Cholesky solves/log determinants, never explicit matrix inverses.
  */
object GaussianInformation {
  private def check(p: MultivariateGaussianDistribution,q: MultivariateGaussianDistribution,tolerance: Double): Unit = {
    N.check(); require(p != null && q != null && p.dimension == q.dimension,"matching Gaussian dimensions required")
    require(tolerance.isFinite && tolerance > 0,"positive finite tolerance required")
  }
  private def calculate(body: => InformationMetricResult): InformationMetricResult = try body catch {
    case _: ArithmeticException => M.unavailable(InformationMetricStatus.NumericallyUnresolved)
    case _: IllegalArgumentException => M.unavailable(InformationMetricStatus.NumericallyUnresolved)
  }
  /** @param p source Gaussian
    * @param q reference Gaussian in the same coordinates/units
    * @param tolerance positive absolute floating-point allowance (default 1e-8), not a statistical confidence level
    * @return directed KL(P||Q); equal covariances reduce to half the squared Mahalanobis separation
    * @example `GaussianInformation.kl(p,q)`
    */
  def kl(p: MultivariateGaussianDistribution,q: MultivariateGaussianDistribution,tolerance: Double=1e-8): InformationMetricResult = {
    check(p,q,tolerance)
    if(p == q) return M.identity
    calculate {
      val displacement=q.whiten(p.mean.zip(q.mean).map(_-_)).map(x => x*x).sum
      val conditioning=p.dimension*math.max(p.conditionNumber,q.conditionNumber)
      if(p.covariance == q.covariance) M.analytic(.5*displacement,tolerance,(1+displacement)*conditioning,"Gaussian equal-covariance KL")
      else {
        val trace=(0 until p.dimension).map(j => q.whiten(p.lower.map(_(j))).map(x => x*x).sum).sum
        val determinant=q.logDeterminant-p.logDeterminant
        M.analytic(.5*(trace+displacement-p.dimension+determinant),tolerance,(1+trace+displacement+math.abs(p.logDeterminant)+math.abs(q.logDeterminant))*conditioning,"Gaussian KL")
      }
    }
  }
  /** @param p first full-rank Gaussian
    * @param q second full-rank Gaussian
    * @param tolerance positive absolute numeric allowance in nats (default 1e-8)
    * @return symmetric negative log Bhattacharyya coefficient; equal covariances give Mahalanobis squared / 8
    * @example `GaussianInformation.bhattacharyya(p,q)`
    */
  def bhattacharyya(p: MultivariateGaussianDistribution,q: MultivariateGaussianDistribution,tolerance: Double=1e-8): InformationMetricResult = {
    check(p,q,tolerance)
    if(p == q) return M.identity
    calculate {
      val average=MultivariateGaussianDistribution(q.mean,Vector.tabulate(p.dimension,p.dimension)((i,j) => p.covariance(i)(j)/2+q.covariance(i)(j)/2))
      val distance=average.mahalanobisSquared(p.mean)/8
      val determinant=if(p.covariance == q.covariance) 0.0 else .5*average.logDeterminant-.25*p.logDeterminant-.25*q.logDeterminant
      val conditioning=p.dimension*Vector(p.conditionNumber,q.conditionNumber,average.conditionNumber).max
      M.analytic(distance+determinant,tolerance,(1+distance+math.abs(average.logDeterminant)+math.abs(p.logDeterminant)+math.abs(q.logDeterminant))*conditioning,"Gaussian Bhattacharyya")
    }
  }
  /** MI between a selected coordinate block and its complement in ONE joint Gaussian.
    * @param joint full-rank joint Gaussian
    * @param first nonempty distinct valid indices; must not select the whole vector
    * @param tolerance positive absolute numeric allowance in nats (default 1e-8)
    * @return I(X_first;X_complement); means do not affect this diagnostic
    * @example `GaussianInformation.mutualInformation(joint,Vector(0))`
    */
  def mutualInformation(joint: MultivariateGaussianDistribution,first: Vector[Int],tolerance: Double=1e-8): InformationMetricResult = {
    check(joint,joint,tolerance)
    require(first != null && first.nonEmpty && first.size < joint.dimension && first.distinct.size == first.size && first.forall(i => i >= 0 && i < joint.dimension),"proper nonempty coordinate partition required")
    calculate {
      val second=joint.mean.indices.filterNot(first.contains).toVector
      if(first.forall(i => second.forall(j => joint.covariance(i)(j) == 0))) M.identity
      else {
        val a=joint.marginal(first).logDeterminant; val b=joint.marginal(second).logDeterminant; val c=joint.logDeterminant
        M.analytic(.5*(a+b-c),tolerance,(1+math.abs(a)+math.abs(b)+math.abs(c))*joint.dimension*joint.conditionNumber,"Gaussian partition MI")
      }
    }
  }
  /** Convert a scalar kernel to a dimension-one Gaussian for the same metric API.
    * @param law scalar Gaussian (second parameter is standard deviation)
    * @return equivalent vector kernel
    * @example `GaussianInformation.kl(GaussianInformation.scalar(p),GaussianInformation.scalar(q))`
    */
  def scalar(law: GaussianDistribution): MultivariateGaussianDistribution = {
    require(law != null); MultivariateGaussianDistribution(Vector(law.location),Vector(Vector(law.standardDeviation*law.standardDeviation)))
  }
}
