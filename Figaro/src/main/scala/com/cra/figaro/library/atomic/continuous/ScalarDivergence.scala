package com.cra.figaro.library.atomic.continuous

import com.cra.figaro.library.atomic.{DistributionNumerics as N,InformationMetricResult,InformationMetricStatus,MetricCalculation as M}
import org.apache.commons.math3.special.Gamma
import org.apache.commons.math3.analysis.integration.gauss.GaussIntegratorFactory
import java.util.concurrent.CancellationException

/** Same-family KL and Bhattacharyya comparisons for the seven new scalar kernels.
  * Analytic reductions first, otherwise bounded probability-coordinate quadrature.
  * Numerical error estimates are heuristic, not certified; no sampling or fusion.
  */
object ScalarDivergence {
  import InformationMetricStatus.*
  private class NumericalFailure extends RuntimeException
  /** Directed KL(P||Q); compare laws expressed in the same units.
    * @param p source law
    * @param q comparison law of the same family; unlike-family comparisons return Unsupported
    * @param tolerance positive finite absolute target in nats, default 1e-6
    * @param maxEvaluations budget in [1,65536], default 16384; excludes Gauss-node setup
    * @param cancelled non-null cooperative callback; true throws CancellationException
    * @return analytic/estimated/infinite result or explicit refusal; callback exceptions propagate
    * @example `ScalarDivergence.kl(StudentTDistribution(5), StudentTDistribution(8,.4,1.2))`
    */
  def kl(p: ScalarDistribution,q: ScalarDistribution,tolerance: Double=1e-6,maxEvaluations: Int=16384,
    cancelled: () => Boolean=() => false): InformationMetricResult = compare(p,q,false,tolerance,maxEvaluations,cancelled)
  /** Symmetric negative log overlap (not a metric with a general triangle inequality).
    * @param p first law
    * @param q second law of the same family and in the same units
    * @param tolerance positive finite absolute target in nats, default 1e-6
    * @param maxEvaluations budget in [1,65536], default 16384; both orientations are charged
    * @param cancelled non-null cooperative callback
    * @return analytic/estimated/infinite result or explicit refusal; no value on numerical failure
    * @example `ScalarDivergence.bhattacharyya(WeibullDistribution(2,1), WeibullDistribution(2,3))`
    */
  def bhattacharyya(p: ScalarDistribution,q: ScalarDistribution,tolerance: Double=1e-6,maxEvaluations: Int=16384,
    cancelled: () => Boolean=() => false): InformationMetricResult = compare(p,q,true,tolerance,maxEvaluations,cancelled)

  private def compare(p: ScalarDistribution,q: ScalarDistribution,overlap: Boolean,tol: Double,budget: Int,cancelled: () => Boolean): InformationMetricResult = {
    require(p != null && q != null,"non-null laws required")
    require(tol.isFinite && tol > 0,"positive finite tolerance required")
    require(budget >= 1 && budget <= 65536,"evaluation budget must be in [1,65536]")
    require(cancelled != null,"non-null cancellation callback required")
    def check(): Unit = { N.check(); if(cancelled()) throw new CancellationException("scalar divergence cancelled") }
    def numeric[A](f: => A): A = try f catch {
      case _: ArithmeticException => throw new NumericalFailure
      case _: org.apache.commons.math3.exception.MathIllegalStateException => throw new NumericalFailure
    }
    check()
    if(p.getClass != q.getClass) return M.unavailable(Unsupported)
    if(p == q) return M.identity
    (p,q) match {
      case (a: AffineDistribution,b: AffineDistribution) if a.offset == b.offset && a.multiplier == b.multiplier =>
        return compare(a.base,b.base,overlap,tol,budget,cancelled).copy(method="common affine transform")
      case (a: ExpDistribution,b: ExpDistribution) =>
        return compare(a.base,b.base,overlap,tol,budget,cancelled).copy(method="common exponential transform")
      case (a: GaussianDistribution,b: GaussianDistribution) =>
        return if(overlap) GaussianInformation.bhattacharyya(GaussianInformation.scalar(a),GaussianInformation.scalar(b),tol)
          else GaussianInformation.kl(GaussianInformation.scalar(a),GaussianInformation.scalar(b),tol)
      case (_: StudentTDistribution | _: CauchyDistribution | _: LaplaceDistribution | _: LogNormalDistribution |
            _: WeibullDistribution | _: TriangularDistribution | _: KumaraswamyDistribution,_) => ()
      case _ => return M.unavailable(Unsupported)
    }
    if(!overlap && (p.support._1 < q.support._1 || p.support._2 > q.support._2)) return M.infinite
    if(overlap && math.max(p.support._1,q.support._1) >= math.min(p.support._2,q.support._2)) return M.infinite
    def logCosh(x: Double): Double = { val a=math.abs(x); a+math.log1p(math.exp(-2*a))-math.log(2) }
    def analytic(v: Double,m: Double=1) = M.analytic(v,tol,m)
    (p,q) match {
      case (a: LogNormalDistribution,b: LogNormalDistribution) =>
        val logRatio=math.log(b.logStandardDeviation/a.logStandardDeviation)
        val d=a.logMean-b.logMean
        if(!overlap) {
          val r=a.logStandardDeviation/b.logStandardDeviation; val z=d/b.logStandardDeviation
          return analytic(logRatio+.5*(r*r-1+z*z),math.abs(logRatio)+r*r+z*z)
        } else {
          val sum=a.logStandardDeviation*a.logStandardDeviation+b.logStandardDeviation*b.logStandardDeviation
          return analytic(.5*logCosh(logRatio)+d*d/(4*sum),d*d/sum)
        }
      case (a: CauchyDistribution,b: CauchyDistribution) if !overlap =>
        val scale=math.max(a.scale,b.scale); val x=a.scale/scale; val y=b.scale/scale
        val d=(a.location-b.location)/scale
        return analytic(math.log1p(((x-y)*(x-y)+d*d)/(4*x*y)),d*d/(x*y))
      case (a: LaplaceDistribution,b: LaplaceDistribution) if !overlap =>
        val ratio=a.scale/b.scale; val z=math.abs(a.location-b.location)/a.scale
        val part=ratio*(z+math.exp(-z))
        return analytic(-math.log(ratio)+part-1,math.abs(math.log(ratio))+part)
      case (a: LaplaceDistribution,b: LaplaceDistribution) if a.location == b.location =>
        return analytic(logCosh(.5*(math.log(a.scale)-math.log(b.scale))))
      case (a: LaplaceDistribution,b: LaplaceDistribution) if a.scale == b.scale =>
        val x=math.abs(a.location-b.location)/(2*a.scale)
        return analytic(x-math.log1p(x),x)
      case (a: WeibullDistribution,b: WeibullDistribution) if !overlap =>
        val ratio=b.shape/a.shape; val l=b.shape*(math.log(a.scale)-math.log(b.scale))
        val moment=math.exp(l+Gamma.logGamma(1+ratio))
        val v= -math.log(ratio)-l+.5772156649015328606*(ratio-1)+moment-1
        return analytic(v,math.abs(l)+ratio+moment)
      case (a: WeibullDistribution,b: WeibullDistribution) if a.shape == b.shape =>
        return analytic(logCosh(.5*a.shape*(math.log(a.scale)-math.log(b.scale))))
      case (a: KumaraswamyDistribution,b: KumaraswamyDistribution) if a.a == b.a =>
        val l=math.log(b.b/a.b)
        return if(overlap) analytic(logCosh(.5*l)) else analytic(math.expm1(l)-l,math.exp(l)+math.abs(l))
      case _ => ()
    }
    var work=0
    try {
      // Initial numerical range: protects inverse-CDF and operand precision, not a guarantee of resolution.
      def eligible(d: ScalarDistribution): Boolean = d match {
        case a: StudentTDistribution => a.degreesOfFreedom >= .25 && a.degreesOfFreedom <= 1000
        case a: WeibullDistribution => a.shape >= .1 && a.shape <= 100
        case a: KumaraswamyDistribution => a.a >= .1 && a.a <= 100 && a.b >= .1 && a.b <= 100
        case _ => true
      }
      if(!eligible(p) || !eligible(q)) return M.unavailable(Unsupported)
      val widthP=numeric(p.quantile(.75)-p.quantile(.25)); val widthQ=numeric(q.quantile(.75)-q.quantile(.25))
      val contrast=math.max(widthP,widthQ)/math.min(widthP,widthQ)
      val shift=math.abs(numeric(p.quantile(.5)-q.quantile(.5)))/math.min(widthP,widthQ)
      if(!contrast.isFinite || contrast > 1e6 || !shift.isFinite || shift > 1e6) return M.unavailable(NumericallyUnresolved)
      // Split at triangular support edges and modes in both probability coordinates.
      val edges=Vector(p,q).flatMap {
        case t: TriangularDistribution => Vector(t.lower,t.mode,t.upper)
        case t: LaplaceDistribution => Vector(t.location)
        case _ => Vector.empty
      }
      val probabilities=edges.flatMap(x => Vector(numeric(p.cdf(x)),numeric(q.cdf(x)))).filter(u => u > 0 && u < 1)
      def toT(u: Double) = math.sqrt(u)/(math.sqrt(u)+math.sqrt(1-u))
      val knots=(Vector(0.0,1.0)++probabilities.map(toT)).distinct.sorted
      val pieces=knots.sliding(2).toVector
      val factory=new GaussIntegratorFactory
      var order=8; var previous=0.0; var grids=0; var lastDifference=Double.PositiveInfinity
      while(order <= 512 && pieces.size.toLong*order*(if(overlap) 2 else 1) <= budget-work) {
        check()
        val rule=factory.legendre(order,0,1)
        var total=0.0; var compensation=0.0; var absolute=0.0
        for(piece <- pieces; i <- 0 until order) {
          check()
          val t=piece(0)+(piece(1)-piece(0))*rule.getPoint(i)
          val a=t*t; val b=(1-t)*(1-t); val denom=a+b
          val u=a/denom; val jacobian=2*t*(1-t)/(denom*denom)
          if(u <= 0 || u >= 1) throw new NumericalFailure
          def at(left: ScalarDistribution,right: ScalarDistribution): Double = {
            val x=numeric(left.quantile(u)); val lp=numeric(left.logDensity(x)); val lq=numeric(right.logDensity(x))
            work += 1
            if(!lp.isFinite || lq.isNaN || lq == Double.PositiveInfinity) throw new NumericalFailure
            // Overlap is E_(P+Q)/2[2*sqrt(p*q)/(p+q)]. This integrand is bounded
            // by one, unlike the fragile sqrt(q/p) importance-ratio representation.
            val difference=math.abs(lq-lp)
            val v=if(overlap) 2*math.exp(-.5*difference)/(1+math.exp(-difference)) else lp-lq
            if(!v.isFinite) throw new NumericalFailure
            v
          }
          val value=if(overlap) .5*(at(p,q)+at(q,p)) else at(p,q)
          val weighted=value*jacobian*rule.getWeight(i)*(piece(1)-piece(0))
          val add=weighted-compensation; val next=total+add; compensation=(next-total)-add; total=next
          absolute += math.abs(weighted)
        }
        val difference=if(grids == 0) Double.PositiveInfinity else math.abs(total-previous)
        val roundoff=128*math.ulp(1.0)*(1+absolute)*(1+work+contrast+shift)
        val rawError=8*math.max(difference,lastDifference)+roundoff
        val error=if(overlap && total > rawError) rawError/(total-rawError) else if(overlap) Double.PositiveInfinity else rawError
        val value=if(overlap) -math.log(total) else total
        if(error.isFinite && error <= tol && value.isFinite && value >= -error) {
          return InformationMetricResult(Estimated,Some(math.max(0,value)),error,work,"probability-quadrature")
        }
        if(roundoff > tol && !overlap) return M.unavailable(NumericallyUnresolved,work)
        previous=total; lastDifference=difference; grids += 1; order *= 2
      }
      M.unavailable(if(order > 512) NumericallyUnresolved else BudgetExhausted,work,"probability-quadrature")
    } catch { case _: NumericalFailure => M.unavailable(NumericallyUnresolved,work,"probability-quadrature") }
  }
}
