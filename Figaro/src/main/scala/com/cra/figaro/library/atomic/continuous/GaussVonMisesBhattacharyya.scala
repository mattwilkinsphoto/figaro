package com.cra.figaro.library.atomic.continuous

import com.cra.figaro.util.CircularStatistics
import java.util.concurrent.CancellationException
import org.apache.commons.math3.exception.{MathIllegalArgumentException, MathIllegalStateException}
import org.apache.commons.math3.linear.{Array2DRowRealMatrix, CholeskyDecomposition, EigenDecomposition, RealMatrix}
import org.apache.commons.math3.special.Gamma

/** Resolution of a numerical comparison, not a statistical decision or accuracy certificate. */
enum GaussVonMisesBhattacharyyaStatus {
  case Resolved, BudgetExhausted, NumericallyUnresolved, UnsupportedRange
}

/** Immutable diagnostic result. Only resolved comparisons expose a distance.
  * @param status numerical outcome
  * @param distance resolved symmetric distance in nats; None otherwise
  * @param estimatedDistanceInterval rounding-estimate plus truncation interval, not certified; upper endpoint may be infinite
  * @param gaussianDistance Gaussian contribution, if preprocessing succeeded
  * @param angularAffinityEstimate raw Fourier estimate, which may be negative when unresolved
  * @param angularTruncationBound analytic tail bound evaluated in Double; infinity when unavailable
  * @param angularRoundoffEstimate heuristic allowance including matrix/phase sensitivity, not a rigorous bound
  * @param harmonicsUsed largest included positive harmonic; zero for analytic shortcuts or no positive harmonics
  * @param method identity, gaussian, constant-angular, one-uniform, fourier, or unavailable
  * @param message human-readable explanation, not a stable machine-readable status
  */
final class GaussVonMisesBhattacharyyaResult private[continuous] (
  val status: GaussVonMisesBhattacharyyaStatus, val distance: Option[Double],
  val estimatedDistanceInterval: Option[(Double,Double)], val gaussianDistance: Option[Double],
  val angularAffinityEstimate: Option[Double], val angularTruncationBound: Double,
  val angularRoundoffEstimate: Double, val harmonicsUsed: Int, val method: String, val message: String) {
  /** Whether the requested absolute distance accuracy passed the numerical estimates.
    * @return true only for Resolved; not a proof of accuracy
    * @example `if (result.resolved) println(result.distance.get)`
    */
  def resolved: Boolean = status == GaussVonMisesBhattacharyyaStatus.Resolved

  /** Log of the resolved affinity, retaining widely separated Gaussian comparisons.
    * @return Some(-distance), or None when unresolved
    * @example `println(result.logCoefficient)`
    */
  def logCoefficient: Option[Double] = distance.map(-_)

  /** Resolved affinity between zero and one; very small values can underflow to zero.
    * @return exponential of logCoefficient, or None when unresolved
    * @example `println(result.coefficient)`
    */
  def coefficient: Option[Double] = logCoefficient.map(math.exp)
}

object GaussVonMisesBhattacharyya {
  import GaussVonMisesBhattacharyyaStatus.*

  /** Maximum concentration for nonidentity comparisons in this first numerical implementation. */
  val MaxKappa: Double = 50.0
  /** Maximum linear dimension for nonidentity comparisons. */
  val MaxDimension: Int = 32

  /** Compare two fixed GVMs using exact reductions or a bounded Fourier/Gaussian series.
    * @param p non-null fixed distribution
    * @param q non-null distribution with the same linear dimension and coordinate conventions
    * @param absoluteTolerance positive finite requested absolute distance accuracy in nats, default 1e-8
    * @param maxHarmonics largest positive harmonic permitted in [0,512], default 256; analytic shortcuts ignore this budget
    * @return immutable resolved/unresolved diagnostics; no distance on budget, range or numerical failure
    * @example `GaussVonMisesBhattacharyya.compare(p, q, absoluteTolerance = 1e-7)`
    */
  def compare(p: GaussVonMisesDistribution, q: GaussVonMisesDistribution,
    absoluteTolerance: Double=1e-8, maxHarmonics: Int=256): GaussVonMisesBhattacharyyaResult = {
    require(p != null && q != null && p.dimension == q.dimension,"comparison requires non-null matching dimensions")
    require(absoluteTolerance.isFinite && absoluteTolerance > 0,"absoluteTolerance must be positive and finite")
    require(maxHarmonics >= 0 && maxHarmonics <= 512,"maxHarmonics must be in [0,512]")
    interrupted()
    if ((p eq q) || (p.mean == q.mean && p.covariance == q.covariance && p.alpha == q.alpha &&
      p.beta == q.beta && p.gamma == q.gamma && p.kappa == q.kappa))
      return new GaussVonMisesBhattacharyyaResult(Resolved,Some(0),Some((0,0)),Some(0),Some(1),0,0,0,"identity","Identical stored laws")
    if (p.dimension > MaxDimension || math.max(p.kappa,q.kappa) > MaxKappa)
      return unavailable(UnsupportedRange,"Nonidentity comparisons require dimension <= 32 and concentrations <= 50")
    try calculate(p,q,absoluteTolerance,maxHarmonics)
    catch {
      case e: ArithmeticException => unavailable(NumericallyUnresolved,e.getMessage)
      case e: MathIllegalArgumentException => unavailable(NumericallyUnresolved,e.getMessage)
      case e: MathIllegalStateException => unavailable(NumericallyUnresolved,e.getMessage)
    }
  }

  private def interrupted(): Unit =
    if (Thread.currentThread().isInterrupted) throw new CancellationException("GVM Bhattacharyya interrupted")
  private def finite(x: Double): Double = {
    if (!x.isFinite) throw new ArithmeticException("Nonfinite comparison intermediate")
    x
  }
  private def unavailable(status: GaussVonMisesBhattacharyyaStatus, message: String) =
    new GaussVonMisesBhattacharyyaResult(status,None,None,None,None,Double.PositiveInfinity,
      Double.PositiveInfinity,0,"unavailable",message)
  private def matrix(rows: Vector[Vector[Double]]): RealMatrix = new Array2DRowRealMatrix(rows.map(_.toArray).toArray,false)
  private def symmetric(m: RealMatrix): RealMatrix = m.add(m.transpose()).scalarMultiply(0.5)
  private def check(m: RealMatrix): RealMatrix = { m.getData.foreach(_.foreach(finite)); m }
  private def column(v: Vector[Double]): RealMatrix = new Array2DRowRealMatrix(v.map(x => Array(x)).toArray,false)
  private def sumAbs(m: RealMatrix): Double = finite(m.getData.iterator.flatMap(_.iterator).map(math.abs).sum)
  private def logDet(chol: CholeskyDecomposition): Double = {
    val l=chol.getL
    2*(0 until l.getRowDimension).map(i => math.log(l.getEntry(i,i))).sum
  }
  private def lowerSolve(l: RealMatrix, rhs: RealMatrix): RealMatrix = {
    val out=rhs.getData
    for (i <- out.indices; j <- out(i).indices) {
      interrupted()
      var value=out(i)(j)
      for (k <- 0 until i) value=finite(value-l.getEntry(i,k)*out(k)(j))
      out(i)(j)=finite(value/l.getEntry(i,i))
    }
    new Array2DRowRealMatrix(out,false)
  }

  // Positive defining series in log-scaled form, limited to x <= 50. No unstable upward recurrence.
  private def logScaledBessel(x: Double, order: Int): Double = {
    if (x == 0) return if (order == 0) 0 else Double.NegativeInfinity
    var term=1.0; var sum=1.0; var j=1
    while (j <= 1000) {
      interrupted()
      term *= (x/2)*(x/2)/(j.toDouble*(j+order))
      sum += term
      if (term <= sum*1e-17)
        return finite(-x+(if(order == 0) 0 else order*(math.log(x)-math.log(2)))-Gamma.logGamma(order+1.0)+math.log(sum))
      j += 1
    }
    throw new ArithmeticException("Bessel coefficient series exhausted its internal guard")
  }

  private def calculate(p: GaussVonMisesDistribution, q: GaussVonMisesDistribution,
    tolerance: Double, budget: Int): GaussVonMisesBhattacharyyaResult = {
    val n=p.dimension
    val scales=Vector.tabulate(n)(i => math.sqrt(math.max(p.covariance(i)(i),q.covariance(i)(i))))
    def scaledCov(k: GaussVonMisesDistribution): RealMatrix = symmetric(check(matrix(
      Vector.tabulate(n,n)((i,j) => finite(k.covariance(i)(j)/scales(i)/scales(j))))))
    val pp=scaledCov(p); val pq=scaledCov(q)
    // Spectral checks are unit-scaled and reject ill-conditioned cases rather than claiming accuracy.
    def conditioning(m: RealMatrix): Double = {
      interrupted()
      val values=new EigenDecomposition(m).getRealEigenvalues.map(finite)
      interrupted()
      if (values.min <= 1e-12 || values.max/values.min > 1e8)
        throw new ArithmeticException("Scaled covariance conditioning exceeds the tested numerical range")
      values.max/values.min
    }
    val condition=math.max(conditioning(pp),conditioning(pq))
    val cp=new CholeskyDecomposition(pp,1e-12,1e-14)
    val cq=new CholeskyDecomposition(pq,1e-12,1e-14)
    val sum=pp.add(pq); val cs=new CholeskyDecomposition(sum,1e-12,1e-14)
    val difference=column(Vector.tabulate(n)(i => finite((q.mean(i)-p.mean(i))/scales(i))))
    val solved=check(cs.getSolver.solve(difference))
    val rawGaussian=finite(difference.transpose().multiply(solved).getEntry(0,0)/4+
      (logDet(cs)-n*math.log(2)-0.5*(logDet(cp)+logDet(cq)))/2)
    val base=64*math.ulp(1.0)*n*n*condition
    val gaussianError=finite(base*(1+math.abs(rawGaussian)))
    if (rawGaussian < -gaussianError) throw new ArithmeticException("Negative Gaussian distance beyond rounding allowance")
    val gaussian=math.max(0,rawGaussian)
    val a=p.kappa/2; val b=q.kappa/2
    val logDen=(logScaledBessel(p.kappa,0)+logScaledBessel(q.kappa,0))/2

    def analytic(logF: Double, method: String): GaussVonMisesBhattacharyyaResult = {
      interrupted()
      val angularError=base*(1+p.kappa+q.kappa)
      val error=finite(gaussianError+angularError)
      val raw=finite(gaussian-logF)
      if (logF > angularError || raw < -error) throw new ArithmeticException("Invalid analytic affinity beyond rounding allowance")
      val value=math.max(0,raw)
      val interval=(math.max(0,value-error),finite(value+error))
      val ok=error <= tolerance
      new GaussVonMisesBhattacharyyaResult(if(ok) Resolved else NumericallyUnresolved,
        if(ok) Some(value) else None,Some(interval),Some(gaussian),Some(math.exp(logF)),0,
        math.exp(logF)*angularError,0,method,if(ok) "Analytic reduction with rounding allowance" else "Requested tolerance is below the rounding estimate")
    }
    if (p.kappa == 0 && q.kappa == 0) return analytic(0,"gaussian")
    if (p.kappa == 0 || q.kappa == 0)
      return analytic(logScaledBessel(a+b,0)-logDen,"one-uniform")
    val zeroP=p.beta.forall(_ == 0) && p.gamma.forall(_.forall(_ == 0))
    val zeroQ=q.beta.forall(_ == 0) && q.gamma.forall(_.forall(_ == 0))
    if ((zeroP && zeroQ) || (p.mean == q.mean && p.covariance == q.covariance && p.beta == q.beta && p.gamma == q.gamma)) {
      val delta=CircularStatistics.difference(p.alpha,q.alpha)
      val radius=math.hypot(a-b,2*math.sqrt(a*b)*math.cos(delta/2))
      return analytic(radius-a-b+logScaledBessel(radius,0)-logDen,"constant-angular")
    }

    val mean=check(pp.multiply(solved))
    val covariance=symmetric(check(pp.multiply(cs.getSolver.solve(pq)).scalarMultiply(2)))
    val linearFactor=new CholeskyDecomposition(covariance,1e-12,1e-14).getL
    def phase(k: GaussVonMisesDistribution, lower: RealMatrix, offset: RealMatrix): (Double,RealMatrix,RealMatrix,Double) = {
      val d=lowerSolve(lower,offset); val transform=lowerSolve(lower,linearFactor)
      val beta=column(k.beta); val gamma=matrix(k.gamma)
      val constant=finite(k.alpha+beta.transpose().multiply(d).getEntry(0,0)+d.transpose().multiply(gamma).multiply(d).getEntry(0,0)/2)
      val linear=check(transform.transpose().multiply(beta.add(gamma.multiply(d))))
      val quadratic=check(transform.transpose().multiply(gamma).multiply(transform))
      val sensitivity=finite(1+math.abs(k.alpha)+math.pow(1+sumAbs(d),2)*(1+sumAbs(beta)+sumAbs(gamma))*math.pow(1+sumAbs(transform),2))
      (constant,linear,quadratic,sensitivity)
    }
    val fp=phase(p,cp.getL,mean); val fq=phase(q,cq.getL,mean.subtract(difference))
    val constant=finite(fp._1-fq._1)
    val linear=check(fp._2.subtract(fq._2)); val quadratic=symmetric(check(fp._3.subtract(fq._3)))
    val sensitivity=finite(fp._4+fq._4)
    if (math.abs(constant) > 1e6 || sumAbs(linear) > 1e4 || sumAbs(quadratic) > 1e4)
      throw new ArithmeticException("Transformed phase exceeds the tested numerical range")
    val eigen=new EigenDecomposition(quadratic)
    interrupted()
    val lambda=eigen.getRealEigenvalues.map(finite)
    val v=check(eigen.getV.transpose().multiply(linear)).getColumn(0)
    def characteristic(j: Int): Double = {
      var magnitude=0.0; var angle=CircularStatistics.normalize(j*constant)
      for (t <- 0 until n) {
        interrupted()
        val jl=j*lambda(t); val h=math.hypot(1,jl); val scaled=j*v(t)/h
        val logH=if(math.abs(jl) < 1) math.log1p(jl*jl)/2 else math.log(h)
        magnitude=finite(magnitude-logH/2-scaled*scaled/2)
        angle=CircularStatistics.normalize(finite(angle+math.atan(jl)/2-scaled*scaled*jl/2))
      }
      math.exp(magnitude)*math.cos(angle)
    }
    def coefficient(j: Int): Double = math.exp((if(j == 0) 0 else math.log(2))+
      logScaledBessel(a,j)+logScaledBessel(b,j)-logDen)
    var total=coefficient(0); var correction=0.0; var rounding=base
    var used=0; var next=coefficient(1)
    def result(tail: Double): GaussVonMisesBhattacharyyaResult = {
      val value=finite(total+correction); val error=finite(rounding+base)
      val low=math.max(0,value-tail-error); val high=math.min(1,value+tail+error)
      val interval=if(high > 0 && low <= high) Some((math.max(0,gaussian-gaussianError-math.log(high)),
        if(low > 0) finite(gaussian+gaussianError-math.log(low)) else Double.PositiveInfinity)) else None
      val estimate=if(value > 0 && value <= 1+error) Some(math.max(0,gaussian-math.log(math.min(1,value)))) else None
      val ok=estimate.exists(d => interval.exists((lo,hi) => math.max(d-lo,hi-d) <= tolerance))
      val status=if(ok) Resolved else if(tail <= error) NumericallyUnresolved else BudgetExhausted
      new GaussVonMisesBhattacharyyaResult(status,if(ok) estimate else None,interval,Some(gaussian),Some(value),tail,error,used,"fourier",
        if(ok) "Requested distance tolerance met by numerical estimates" else if(status == NumericallyUnresolved)
          "Rounding/cancellation estimate prevents resolving the requested distance" else "Harmonic budget insufficient for the requested distance")
    }
    while (true) {
      interrupted()
      val ratio=a*b/(4*(used+2.0)*(used+2.0))
      val tail=if(ratio < 1) math.max(java.lang.Double.MIN_VALUE,next/(1-ratio)) else Double.PositiveInfinity
      val current=result(tail)
      if(current.status != BudgetExhausted || used == budget) return current
      used += 1
      val term=next*characteristic(used); val updated=finite(total+term)
      correction=finite(correction+(if(math.abs(total) >= math.abs(term)) (total-updated)+term else (term-updated)+total))
      total=updated
      rounding=finite(rounding+next*base*sensitivity*(1.0+used)*(1.0+used))
      next=coefficient(used+1)
    }
    throw new AssertionError("unreachable")
  }
}
