/* Independent evaluation of Horwood-Poore (2014), sections 4.2-4.3.
 * Real-valued API: no complex-number dependency leaks into user code.
 */
package com.cra.figaro.library.atomic.continuous

import com.cra.figaro.util.CircularStatistics
import java.util.concurrent.CancellationException
import org.apache.commons.math3.linear.{Array2DRowRealMatrix, EigenDecomposition}

/** Immutable analytic first-harmonic moments, obtained from kernel.moments.
  * Vectors and matrices use physical linear coordinates, not whitened coordinates.
  * Mixed moments are raw expectations, not correlations or covariances.
  * @param meanCos E[cos(theta)]
  * @param meanSin E[sin(theta)]
  * @param meanResultantLength magnitude of E[exp(i theta)], in [0,1]
  * @param logMeanResultantLength natural log magnitude; -Infinity for uniform angles
  * @param meanDirection canonical marginal circular direction, None if uniform or magnitude underflows
  * @param linearCos vector E[x cos(theta)]
  * @param linearSin vector E[x sin(theta)]
  * @param linearLinearCos matrix E[x x^T cos(theta)]
  * @param linearLinearSin matrix E[x x^T sin(theta)]
  */
final class GaussVonMisesMoments private[continuous] (
  val meanCos: Double, val meanSin: Double, val meanResultantLength: Double,
  val logMeanResultantLength: Double, val meanDirection: Option[Double],
  val linearCos: Vector[Double], val linearSin: Vector[Double],
  val linearLinearCos: Vector[Vector[Double]], val linearLinearSin: Vector[Vector[Double]])

private[continuous] object GvmMomentCalculation {
  def compute(mean: Vector[Double], lower: Vector[Vector[Double]], alpha: Double,
    beta: Vector[Double], gamma: Vector[Vector[Double]], kappa: Double,
    resultant: Double): GaussVonMisesMoments = {
    def interrupted(): Unit =
      if (Thread.currentThread().isInterrupted) throw new CancellationException("GVM moments interrupted")
    def finite(v: Double): Double = {
      if (!v.isFinite) throw new ArithmeticException("GVM moment intermediate outside finite numeric range")
      v
    }
    interrupted()
    val n = mean.size
    if (kappa == 0.0) {
      val zero = Vector.fill(n)(0.0); val matrix = Vector.fill(n)(zero)
      return new GaussVonMisesMoments(0,0,0,Double.NegativeInfinity,None,zero,zero,matrix,matrix)
    }
    val eigen = new EigenDecomposition(new Array2DRowRealMatrix(gamma.map(_.toArray).toArray, false))
    interrupted()
    val u = eigen.getV.getData
    val lambda = eigen.getRealEigenvalues.map(finite)
    val h = lambda.map(v => math.hypot(1.0,v))
    val projected = Array.tabulate(n) { j =>
      interrupted()
      finite((0 until n).map(i => finite(u(i)(j)*beta(i))).sum)
    }
    val scaled = Array.tabulate(n)(j => finite(projected(j)/h(j)))
    // The small-k expansion retains a log resultant even if kappa/2 underflows.
    var logMagnitude = if (kappa < 1e-100) math.log(kappa)-math.log(2.0) else math.log(resultant)
    var phase = alpha
    for (j <- 0 until n) {
      interrupted()
      val logH = if (math.abs(lambda(j)) < 1) 0.5*math.log1p(lambda(j)*lambda(j)) else math.log(h(j))
      logMagnitude = finite(logMagnitude - 0.5*logH - 0.5*scaled(j)*scaled(j))
      phase = CircularStatistics.normalize(finite(phase + 0.5*math.atan(lambda(j)) -
        0.5*finite(scaled(j)*scaled(j)*lambda(j))))
    }
    val magnitude = math.exp(logMagnitude)
    val cosine = math.cos(phase); val sine = math.sin(phase)
    // Multiply a complex coefficient by exp(logMagnitude+i*phase) in scaled/log space.
    // A marginal harmonic can underflow while a large physical mixed moment remains representable.
    def moment(real: Double, imaginary: Double): (Double,Double) = {
      finite(real); finite(imaginary)
      val scale = math.max(math.abs(real),math.abs(imaginary))
      if (scale == 0) (0.0,0.0)
      else {
        val r = real/scale; val i = imaginary/scale
        def component(v: Double): Double = if (v == 0) 0.0 else
          math.copySign(finite(math.exp(logMagnitude + math.log(scale) + math.log(math.abs(v)))),v)
        (component(r*cosine-i*sine), component(r*sine+i*cosine))
      }
    }
    val angular = moment(1,0)
    // S = A U diag(1/h). Then A Q A^T = S diag(1+i lambda) S^T,
    // where Q=(I-i Gamma)^-1. The physical first coefficient is w=mu+i A Q beta.
    val s = Array.tabulate(n,n) { (i,j) =>
      interrupted()
      finite((0 to i).map(k => finite(lower(i)(k)*u(k)(j))).sum/h(j))
    }
    val wr = new Array[Double](n); val wi = new Array[Double](n)
    for (i <- 0 until n) {
      interrupted()
      val vr = finite((0 until n).map(j => finite(s(i)(j)*scaled(j))).sum)
      val vi = finite((0 until n).map(j => finite(s(i)(j)*finite(projected(j)*(lambda(j)/h(j))))).sum)
      wr(i) = finite(mean(i)-vi); wi(i) = vr
    }
    val first = Vector.tabulate(n)(i => moment(wr(i),wi(i)))
    val secondReal = Array.ofDim[Double](n,n); val secondImaginary = Array.ofDim[Double](n,n)
    for (i <- 0 until n; j <- 0 to i) {
      interrupted()
      val qr = finite((0 until n).map(k => finite(s(i)(k)*s(j)(k))).sum)
      val qi = finite((0 until n).map(k => finite(s(i)(k)*finite(s(j)(k)*lambda(k)))).sum)
      val r = finite(qr + finite(wr(i)*wr(j)) - finite(wi(i)*wi(j)))
      val im = finite(qi + finite(wr(i)*wi(j)) + finite(wi(i)*wr(j)))
      val value = moment(r,im)
      secondReal(i)(j)=value._1; secondReal(j)(i)=value._1
      secondImaginary(i)(j)=value._2; secondImaginary(j)(i)=value._2
    }
    new GaussVonMisesMoments(angular._1,angular._2,magnitude,logMagnitude,
      if (magnitude == 0) None else Some(phase), first.map(_._1),first.map(_._2),
      secondReal.map(_.toVector).toVector,secondImaginary.map(_.toVector).toVector)
  }
}
