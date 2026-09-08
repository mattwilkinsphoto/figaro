package com.cra.figaro.library.atomic.continuous

import com.cra.figaro.library.atomic.DistributionNumerics as N
import org.apache.commons.math3.special.{Gamma as G, Beta as B, Erf}
import org.apache.commons.math3.distribution.NormalDistribution

/** Immutable scalar law in Lebesgue measure; no RNG or universe is retained.
  * All seven implementations are documented in docs/COMMON_DISTRIBUTIONS.md.
  */
sealed trait ScalarDistribution {
  /** @param x real argument (NaN rejected)
    * @return natural-log density; a singular finite boundary can return positive infinity
    * @example `StudentTDistribution(5).logDensity(2)`
    */
  def logDensity(x: Double): Double
  /** @param x real argument
    * @return density, with ordinary exponential underflow/overflow
    * @example `LaplaceDistribution(0,1).density(0)`
    */
  final def density(x: Double): Double = math.exp(logDensity(x))
  /** @param x real argument
    * @return P(X <= x), rounded to binary64
    * @example `WeibullDistribution(2,3).cdf(3)`
    */
  def cdf(x: Double): Double
  /** @param x real argument
    * @return P(X > x), evaluated directly rather than subtracting a rounded CDF
    * @example `LogNormalDistribution(0,1).survival(100)`
    */
  def survival(x: Double): Double
  /** @param p probability in [0,1]
    * @return inverse CDF; endpoints return support limits; unrepresentable interior values throw
    * @example `CauchyDistribution(0,1).quantile(.5)`
    */
  def quantile(p: Double): Double
  /** @return support infimum and supremum; endpoint density conventions are family-specific */
  def support: (Double,Double)
  /** @return None if undefined; Some(infinity) can indicate moment overflow */
  def mean: Option[Double]
  /** @return None if undefined; Some(infinity) for infinite variance or numeric overflow */
  def variance: Option[Double]
  /** Inverse-transform draw using only the caller RNG; not optimized for bulk throughput.
    * @param rng non-null caller-owned RNG; do not share it concurrently
    * @return finite supported draw; numeric collapse to a singular endpoint throws
    * @example `StudentTDistribution(5).sample(new scala.util.Random(42))`
    */
  final def sample(rng: scala.util.Random): Double = {
    val x=quantile(N.open(rng))
    if(!x.isFinite || !logDensity(x).isFinite) throw new ArithmeticException("sample collapsed outside finite-density support")
    x
  }
}

/** Location-scale Student t; scale is NOT standard deviation.
  * @param degreesOfFreedom shape in [0.001,1e6]
  * @param location center, absolute value <=1e100; default 0
  * @param scale positive scale in [1e-100,1e100]; default 1
  */
final case class StudentTDistribution(degreesOfFreedom: Double,location: Double=0,scale: Double=1) extends ScalarDistribution {
  N.shape(degreesOfFreedom); N.location(location); N.scale(scale)
  private val logBeta=B.logBeta(degreesOfFreedom/2,.5)
  private val normalizer= -.5*math.log(degreesOfFreedom)-logBeta-math.log(scale)
  private def logZ(x: Double) = N.logAbsDifference(x,location)-math.log(scale)
  private def positiveTail(lz: Double): Double = {
    val l=2*lz-math.log(degreesOfFreedom)
    if(l <= 0) { val e=math.exp(l); .5-.5*N.regularizedBeta(e/(1+e),.5,degreesOfFreedom/2) }
    else {
      val logQ= -N.log1pExp(l)
      if(logQ < -700) .5*math.exp(degreesOfFreedom/2*logQ-math.log(degreesOfFreedom/2)-logBeta)
      else .5*N.regularizedBeta(math.exp(logQ),degreesOfFreedom/2,.5)
    }
  }
  def logDensity(x: Double): Double = { N.argument(x); if(x.isInfinity) Double.NegativeInfinity else
    normalizer-(degreesOfFreedom+1)/2*N.log1pExp(2*logZ(x)-math.log(degreesOfFreedom)) }
  def cdf(x: Double): Double = { N.argument(x); if(x == Double.NegativeInfinity) 0 else if(x == Double.PositiveInfinity) 1 else { val t=positiveTail(logZ(x)); if(x < location) t else 1-t } }
  def survival(x: Double): Double = { N.argument(x); if(x == Double.PositiveInfinity) 0 else if(x == Double.NegativeInfinity) 1 else { val t=positiveTail(logZ(x)); if(x > location) t else 1-t } }
  def quantile(p: Double): Double = {
    N.probability(p)
    if(p == 0) return Double.NegativeInfinity
    if(p == 1) return Double.PositiveInfinity
    if(p == .5) return location
    val target=math.min(p,1-p)
    var lo= -750.0; var hi=math.log(Double.MaxValue)-math.log(scale)
    if(positiveTail(hi) > target) throw new ArithmeticException("Student t quantile exceeds numeric range")
    var j=0
    while(j < 100) { N.check(); val mid=(lo+hi)/2; if(positiveTail(mid) > target) lo=mid else hi=mid; j += 1 }
    val magnitude=math.exp(math.log(scale)+(lo+hi)/2)
    N.interior(p,location+(if(p < .5) -magnitude else magnitude))
  }
  def support = (Double.NegativeInfinity,Double.PositiveInfinity)
  def mean = if(degreesOfFreedom > 1) Some(location) else None
  def variance = if(degreesOfFreedom <= 1) None else Some(if(degreesOfFreedom <= 2) Double.PositiveInfinity else scale*scale*degreesOfFreedom/(degreesOfFreedom-2))
}

/** Cauchy location-scale law; mean and variance are undefined.
  * @param location center, absolute value <=1e100
  * @param scale positive scale in [1e-100,1e100]
  */
final case class CauchyDistribution(location: Double,scale: Double) extends ScalarDistribution {
  N.location(location); N.scale(scale)
  def logDensity(x: Double): Double = { N.argument(x); if(x.isInfinity) Double.NegativeInfinity else -math.log(math.Pi)-math.log(scale)-N.log1pExp(2*(N.logAbsDifference(x,location)-math.log(scale))) }
  // Divide scale by the residual directly: 1/((x-location)/scale) can lose a
  // representable subnormal tail when the intermediate standardized value overflows.
  private def tail(x: Double): Double = math.atan(scale/math.abs(x-location))/math.Pi
  def cdf(x: Double): Double = { N.argument(x); if(x == location) .5 else if(x < location) tail(x) else 1-tail(x) }
  def survival(x: Double): Double = { N.argument(x); if(x == location) .5 else if(x > location) tail(x) else 1-tail(x) }
  def quantile(p: Double): Double = { N.probability(p); val displacement=if(p < .5) -scale/math.tan(math.Pi*p) else scale/math.tan(math.Pi*(1-p)); N.interior(p,if(p == .5) location else location+displacement) }
  def support = (Double.NegativeInfinity,Double.PositiveInfinity)
  def mean = None
  def variance = None
}

/** Two-sided exponential law.
  * @param location center, absolute value <=1e100
  * @param scale positive scale in [1e-100,1e100]; variance is 2*scale^2
  */
final case class LaplaceDistribution(location: Double,scale: Double) extends ScalarDistribution {
  N.location(location); N.scale(scale)
  def logDensity(x: Double): Double = { N.argument(x); -math.log(2)-math.log(scale)-math.abs((x-location)/scale) }
  def cdf(x: Double): Double = { N.argument(x); val z=(x-location)/scale; if(z <= 0) .5*math.exp(z) else -.5*math.expm1(-z)+.5 }
  def survival(x: Double): Double = { N.argument(x); val z=(x-location)/scale; if(z >= 0) .5*math.exp(-z) else -.5*math.expm1(z)+.5 }
  def quantile(p: Double): Double = { N.probability(p); N.interior(p,location+scale*(if(p <= .5) math.log(2*p) else -math.log(2)-math.log1p(-p))) }
  def support = (Double.NegativeInfinity,Double.PositiveInfinity)
  def mean = Some(location)
  def variance = Some(2*scale*scale)
}

/** Positive law with Normal(logMean, logStandardDeviation^2) logarithm.
  * @param logMean mean of log(X), in [-500,500]
  * @param logStandardDeviation standard deviation of log(X), in [0.001,50], NOT variance
  */
final case class LogNormalDistribution(logMean: Double,logStandardDeviation: Double) extends ScalarDistribution {
  require(logMean.isFinite && math.abs(logMean) <= 500,"logMean must be in [-500,500]")
  require(logStandardDeviation.isFinite && logStandardDeviation >= .001 && logStandardDeviation <= 50,"log standard deviation must be in [0.001,50]")
  private val standard=new NormalDistribution(null,0,1,1e-12)
  def logDensity(x: Double): Double = { N.argument(x); if(x <= 0 || x.isInfinity) Double.NegativeInfinity else {
    val l=math.log(x); val z=(l-logMean)/logStandardDeviation; -l-math.log(logStandardDeviation)-.5*math.log(2*math.Pi)-.5*z*z } }
  def cdf(x: Double): Double = { N.argument(x); if(x <= 0) 0 else .5*Erf.erfc((logMean-math.log(x))/logStandardDeviation/math.sqrt(2)) }
  def survival(x: Double): Double = { N.argument(x); if(x <= 0) 1 else .5*Erf.erfc((math.log(x)-logMean)/logStandardDeviation/math.sqrt(2)) }
  def quantile(p: Double): Double = {
    N.probability(p)
    // The backend's erfInv(2*p-1) loses tiny lower-tail probabilities. Invert the
    // direct complementary error function there, with a fixed iteration budget.
    val z=if(p > 0 && p < 1e-4) {
      var lo=0.0; var hi=40.0; var iteration=0
      while(iteration < 100) {
        N.check(); val mid=(lo+hi)/2
        if(Erf.erfc(mid/math.sqrt(2)) > 2*p) lo=mid else hi=mid
        iteration += 1
      }
      -(lo+hi)/2
    } else standard.inverseCumulativeProbability(p)
    val x=N.interior(p,math.exp(logMean+logStandardDeviation*z))
    if(p > 0 && x == 0) throw new ArithmeticException("positive quantile underflow")
    x
  }
  def support = (0.0,Double.PositiveInfinity)
  def mean = Some(math.exp(logMean+.5*logStandardDeviation*logStandardDeviation))
  def variance = { val v=logStandardDeviation*logStandardDeviation; Some(math.exp(2*logMean+v+N.logExpm1(v))) }
}

/** Weibull minimum/lifetime law on x>=0, without a location shift.
  * @param shape positive shape in [0.001,1e6]
  * @param scale positive scale in [1e-100,1e100]
  */
final case class WeibullDistribution(shape: Double,scale: Double) extends ScalarDistribution {
  N.shape(shape); N.scale(scale)
  private def power(x: Double) = math.exp(shape*(math.log(x)-math.log(scale)))
  def logDensity(x: Double): Double = { N.argument(x); if(x < 0 || x.isInfinity) Double.NegativeInfinity
    else if(x == 0) { if(shape == 1) -math.log(scale) else if(shape < 1) Double.PositiveInfinity else Double.NegativeInfinity }
    else { val l=math.log(x)-math.log(scale); math.log(shape)-math.log(scale)+(shape-1)*l-power(x) } }
  def cdf(x: Double): Double = { N.argument(x); if(x <= 0) 0 else -math.expm1(-power(x)) }
  def survival(x: Double): Double = { N.argument(x); if(x <= 0) 1 else math.exp(-power(x)) }
  def quantile(p: Double): Double = { N.probability(p); val x=N.interior(p,math.exp(math.log(scale)+math.log(-math.log1p(-p))/shape)); if(p > 0 && x == 0) throw new ArithmeticException("positive quantile underflow"); x }
  def support = (0.0,Double.PositiveInfinity)
  def mean = Some(math.exp(math.log(scale)+G.logGamma(1+1/shape)))
  def variance = {
    val t=1/shape; val a=G.logGamma(1+t)
    // log Gamma(1+2t)-2 log Gamma(1+t): cancel the linear term analytically.
    val d=if(shape >= 1000) t*t*(1.6449340668482264+t*(-2.4041138063191885+t*(3.788131317988984+t*(-6.22156653086022+t*(31.0/3*math.pow(math.Pi,6)/945)))))
      else G.logGamma(1+2*t)-2*a
    if(d <= 0 || d.isNaN) throw new ArithmeticException("Weibull variance unresolved")
    Some(math.exp(2*math.log(scale)+2*a+N.logExpm1(d)))
  }
}

/** Bounded triangular law with an interior mode.
  * @param lower finite lower endpoint, absolute value <=1e100
  * @param mode strictly between lower and upper
  * @param upper finite upper endpoint, absolute value <=1e100
  */
final case class TriangularDistribution(lower: Double,mode: Double,upper: Double) extends ScalarDistribution {
  N.location(lower); N.location(mode); N.location(upper)
  require(lower < mode && mode < upper,"strictly ordered lower < mode < upper required")
  private val width=upper-lower; private val split=(mode-lower)/width
  def logDensity(x: Double): Double = { N.argument(x); if(x <= lower || x >= upper) Double.NegativeInfinity else
    math.log(2)-math.log(width)+(if(x <= mode) math.log(x-lower)-math.log(mode-lower) else math.log(upper-x)-math.log(upper-mode)) }
  def cdf(x: Double): Double = { N.argument(x); if(x <= lower) 0 else if(x >= upper) 1 else if(x <= mode) ((x-lower)/width)*((x-lower)/(mode-lower)) else 1-((upper-x)/width)*((upper-x)/(upper-mode)) }
  def survival(x: Double): Double = { N.argument(x); if(x <= lower) 1 else if(x >= upper) 0 else if(x >= mode) ((upper-x)/width)*((upper-x)/(upper-mode)) else 1-((x-lower)/width)*((x-lower)/(mode-lower)) }
  def quantile(p: Double): Double = {
    N.probability(p)
    if(p == 0) return lower
    if(p == 1) return upper
    if(p <= split) lower+math.sqrt(p)*math.sqrt(width)*math.sqrt(mode-lower)
    else {
      val z=math.sqrt(1-p)*math.sqrt(1-split)
      if(z > .5) lower+width*((p+split-p*split)/(1+z)) else upper-width*z
    }
  }
  def support = (lower,upper)
  def mean = Some(lower+(width+mode-lower)/3)
  def variance = Some(width*width*(1+split*split-split)/18)
}

/** Kumaraswamy on [0,1], a bounded beta-like family with explicit inverse CDF.
  * @param a first shape in [0.001,1e6]
  * @param b second shape in [0.001,1e6]
  */
final case class KumaraswamyDistribution(a: Double,b: Double) extends ScalarDistribution {
  N.shape(a); N.shape(b)
  def logDensity(x: Double): Double = { N.argument(x); if(x < 0 || x > 1) Double.NegativeInfinity
    else if(x == 0) { if(a == 1) math.log(b) else if(a < 1) Double.PositiveInfinity else Double.NegativeInfinity }
    else if(x == 1) { if(b == 1) math.log(a) else if(b < 1) Double.PositiveInfinity else Double.NegativeInfinity }
    else math.log(a)+math.log(b)+(a-1)*math.log(x)+(b-1)*N.log1mexp(a*math.log(x)) }
  private def logSurvival(x: Double): Double = {
    val l=a*math.log(x)
    // For exp(l)<2.4e-16, log(1-exp(l))=-exp(l) to working precision.
    // Combine b in log space before exponentiation to retain subnormal CDFs.
    if(l < -36) -math.exp(math.log(b)+l) else b*N.log1mexp(l)
  }
  def cdf(x: Double): Double = { N.argument(x); if(x <= 0) 0 else if(x >= 1) 1 else -math.expm1(logSurvival(x)) }
  def survival(x: Double): Double = { N.argument(x); if(x <= 0) 1 else if(x >= 1) 0 else math.exp(logSurvival(x)) }
  def quantile(p: Double): Double = {
    N.probability(p)
    val logV=math.log(-math.log1p(-p))-math.log(b)
    val logPower=if(logV < -36) logV else N.log1mexp(-math.exp(logV))
    val x=math.exp(logPower/a)
    if(p > 0 && p < 1 && (x == 0 || x == 1)) throw new ArithmeticException("bounded quantile collapsed to endpoint")
    x
  }
  def support = (0.0,1.0)
  private def logMoment(order: Int) = math.log(b)+B.logBeta(1+order.toDouble/a,b)
  def mean = Some(math.exp(logMoment(1)))
  def variance = {
    val first=logMoment(1); val second=logMoment(2); val difference=2*first-second
    if(difference >= -1e-10 || difference.isNaN) throw new ArithmeticException("Kumaraswamy variance cancellation unresolved")
    Some(math.exp(second)*(-math.expm1(difference)))
  }
}
