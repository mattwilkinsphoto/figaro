package com.cra.figaro.library.atomic.continuous

import com.cra.figaro.language.Element
import com.cra.figaro.library.atomic.DistributionNumerics as N

/** Observation semantics for continuous laws. Intervals are (lower,upper]; endpoint
  * inclusion is immaterial only because ScalarDistribution excludes atoms.
  */
enum ScalarObservation {
  case Exact(value: Double)
  case Interval(lower: Double, upper: Double)
  case LeftCensored(upper: Double)
  case RightCensored(lower: Double)
}

/** Stable observation likelihoods; no sampling, conditioning or graph mutation unless
  * attach is explicitly called. Arithmetic failures are not impossible observations.
  */
object ObservationLikelihood {
  private def checked(x: Double): Double = {
    if(x.isNaN || x == Double.PositiveInfinity) throw new ArithmeticException("Unresolved observation likelihood")
    x
  }
  private def gaussianLogTail(z: Double): Double = {
    if(z <= 26) math.log(.5*org.apache.commons.math3.special.Erf.erfc(z/math.sqrt(2)))
    else {
      // Mills asymptotic series, safely deep in the tail; no exp(-z*z/2).
      var term=1.0; var sum=1.0
      for(k <- 1 to 20) { term *= -(2*k-1)/z/z; sum += term }
      -.5*z*z-math.log(z)-.5*math.log(2*math.Pi)+math.log(sum)
    }
  }
  private def logTail(law: ScalarDistribution,x: Double,upper: Boolean): Double = {
    N.check(); N.argument(x)
    val (lo,hi)=law.support
    if(if(upper) x>=hi else x<=lo) return Double.NegativeInfinity
    if(if(upper) x<=lo else x>=hi) return 0.0
    val result=law match {
      case g: GaussianDistribution =>
        val z=(x-g.location)/g.standardDeviation*(if(upper) 1 else -1)
        if(!z.isFinite) throw new ArithmeticException("Gaussian standardized observation overflow")
        gaussianLogTail(z)
      case _ =>
        val p=if(upper) law.survival(x) else law.cdf(x)
        if(!p.isFinite || p<=0 || p>1) throw new ArithmeticException("Tail probability unresolved; not certified zero")
        math.log(p)
    }
    if(!result.isFinite) throw new ArithmeticException("Log tail outside numeric range")
    result
  }
  private def logDifference(a: Double,b: Double): Double = {
    if(b==Double.NegativeInfinity) a
    else if(a>b) a+math.log(-math.expm1(b-a))
    else Double.NegativeInfinity
  }
  /** @param law non-null continuous law
    * @param lower lower endpoint, possibly -Infinity
    * @param upper larger upper endpoint, possibly +Infinity
    * @return log P(lower < X <= upper); disjoint support gives -Infinity;
    *         cancellation/underflow inside support throws, not a fabricated zero
    * @example `logInterval(GaussianDistribution(0,1),40,41)`
    */
  def logInterval(law: ScalarDistribution,lower: Double,upper: Double): Double = {
    require(law!=null && !lower.isNaN && !upper.isNaN && lower<upper,"Ordered non-NaN interval required")
    if(upper<=law.support._1 || lower>=law.support._2) return Double.NegativeInfinity
    if(lower==Double.NegativeInfinity) return logTail(law,upper,false)
    if(upper==Double.PositiveInfinity) return logTail(law,lower,true)
    val a=logTail(law,upper,false); val b=logTail(law,lower,false)
    // A well-separated CDF difference needs no possibly underflowed far-tail evaluation.
    if(b==Double.NegativeInfinity || a-b>.01) return logDifference(a,b)
    val c=logTail(law,lower,true); val d=logTail(law,upper,true)
    // Use the side whose subtraction has the better relative separation.
    val (large,small)=if(b-a < d-c) (a,b) else (c,d)
    if(small.isFinite && large-small<=256*math.ulp(math.max(1,math.max(math.abs(large),math.abs(small)))))
      throw new ArithmeticException("Interval cancellation exceeds numeric resolution")
    val result=logDifference(large,small)
    if(!result.isFinite) throw new ArithmeticException("Interval probability unresolved inside support")
    result
  }
  /** @param law immutable continuous kernel
    * @param observation exact density or interval/tail probability semantics
    * @return natural-log likelihood; exact singular boundary density is refused
    * @example `logLikelihood(GaussianDistribution(0,1),ScalarObservation.RightCensored(3))`
    */
  def logLikelihood(law: ScalarDistribution,observation: ScalarObservation): Double = {
    require(law!=null && observation!=null); N.check()
    checked(observation match {
      case ScalarObservation.Exact(x) => require(x.isFinite); law.logDensity(x)
      case ScalarObservation.Interval(a,b) => logInterval(law,a,b)
      case ScalarObservation.LeftCensored(x) => require(x.isFinite); logTail(law,x,false)
      case ScalarObservation.RightCensored(x) => require(x.isFinite); logTail(law,x,true)
    })
  }
  /** @param value finite reported rounded value
    * @param resolution finite positive bin width in the same units
    * @return interval centered on value; endpoints must remain distinct and finite
    * @example `rounded(12,1)` means the interval (11.5,12.5]
    */
  def rounded(value: Double,resolution: Double): ScalarObservation = {
    require(value.isFinite && resolution.isFinite && resolution>0)
    val a=value-resolution/2; val b=value+resolution/2
    require(a.isFinite && b.isFinite && a<b,"Rounding bin collapsed or overflowed")
    ScalarObservation.Interval(a,b)
  }
  /** Attach one independent observation factor to an element of kernels. Calling
    * twice adds two factors; do not also observe a sampled value for the same datum.
    * @param kernels parameter-dependent continuous laws in the owning graph
    * @param observation immutable observation specification
    * @return Unit; graph ownership/lifecycle remains the caller's responsibility
    * @example `attach(Apply(mu,(m: Double)=>GaussianDistribution(m,1)),rounded(2,.1))`
    */
  def attach[D <: ScalarDistribution](kernels: Element[D],observation: ScalarObservation): Unit = {
    require(kernels!=null && observation!=null)
    kernels.addLogConstraint((d: D)=>logLikelihood(d,observation))
  }
}
