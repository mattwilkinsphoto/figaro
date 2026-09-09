package com.cra.figaro.library.atomic.continuous

import com.cra.figaro.library.atomic.DistributionNumerics as N
import com.cra.figaro.language.*
import org.apache.commons.math3.special.Gamma

private[continuous] object ExtremeNumerics {
  def shape(x: Double): Unit=require(x.isFinite && math.abs(x)<=2 && (x==0 || math.abs(x)>=1e-8),"shape must be zero or have absolute value in [1e-8,2]")
  def endpoint(location: Double,scale: Double,shape: Double): Double = {
    val x=location-scale/shape
    require(x.isFinite && x!=location,"finite distinct support endpoint required; rescale coordinates")
    x
  }
  def ratio(x: Double): Double=if(x==0) 1 else math.expm1(x)/x
  def logBase(x: Double,location: Double,scale: Double,shape: Double): Double = {
    val t=shape*((x-location)/scale)
    val out=if(t==Double.PositiveInfinity) math.log(math.abs(shape))+N.logAbsDifference(x,location)-math.log(scale)
      else math.log1p(t)
    if(out.isNaN || t<= -1) throw new ArithmeticException("Extreme-value support boundary is numerically unresolved")
    out
  }
  private val zeta=Vector(1.6449340668482264,1.2020569031595943,1.0823232337111382,1.03692775514337,1.0173430619844491)
  def gammaLogOverShape(x: Double): Double = .5772156649015329+zeta.indices.map(i => zeta(i)*math.pow(x,i+1)/(i+2)).sum
  def varianceSmall(x: Double): Double = {
    val g=x*gammaLogOverShape(x)
    val d=zeta.indices.map(i => zeta(i)*(math.pow(2,i+2)-2)*math.pow(x,i)/(i+2)).sum
    math.exp(2*g)*d*ratio(x*x*d)
  }
}

/** Generalized extreme value for maxima, using xi (the NEGATIVE of SciPy genextreme's c).
  * @param shape xi: zero or absolute value in [1e-8,2]; zero is the Gumbel limit
  * @param location finite center, absolute value <=1e100
  * @param scale positive scale in [1e-100,1e100], not variance
  */
final case class GeneralizedExtremeValueDistribution(shape: Double,location: Double=0,scale: Double=1) extends ScalarDistribution {
  ExtremeNumerics.shape(shape); N.location(location); N.scale(scale)
  private val end=if(shape==0) 0.0 else ExtremeNumerics.endpoint(location,scale,shape)
  def support: (Double,Double)=if(shape>0) (end,Double.PositiveInfinity) else if(shape<0) (Double.NegativeInfinity,end) else (Double.NegativeInfinity,Double.PositiveInfinity)
  private def logT(x: Double): Double = {
    val z=(x-location)/scale
    if(shape==0) -z else -ExtremeNumerics.logBase(x,location,scale,shape)/shape
  }
  def logDensity(x: Double): Double = {
    N.argument(x)
    if(x.isInfinity || x<support._1 || x>support._2) return Double.NegativeInfinity
    if(shape!=0 && x==end) return if(shape>0 || shape> -1) Double.NegativeInfinity else if(shape== -1) -math.log(scale) else Double.PositiveInfinity
    val l=logT(x); val t=math.exp(l)
    if(t.isPosInfinity) Double.NegativeInfinity else {
      val value= -math.log(scale)+(1+shape)*l-t
      if(value.isNaN) throw new ArithmeticException("GEV density unresolved at support boundary")
      value
    }
  }
  def cdf(x: Double): Double = { N.argument(x); if(x<=support._1) 0 else if(x>=support._2) 1 else math.exp(-math.exp(logT(x))) }
  def survival(x: Double): Double = { N.argument(x); if(x<=support._1) 1 else if(x>=support._2) 0 else -math.expm1(-math.exp(logT(x))) }
  def quantile(p: Double): Double = {
    N.probability(p); if(p==0) return support._1; if(p==1) return support._2
    val v= -math.log(-math.log(p)); val z=if(shape==0) v else v*ExtremeNumerics.ratio(shape*v)
    val x=N.interior(p,location+scale*z)
    if(x<=support._1 || x>=support._2) throw new ArithmeticException("GEV quantile collapsed to support boundary")
    x
  }
  def mean: Option[Double] = if(shape>=1) None else {
    val m=if(shape==0) .5772156649015329 else if(math.abs(shape)<.001) {
      val r=ExtremeNumerics.gammaLogOverShape(shape); r*ExtremeNumerics.ratio(shape*r)
    } else math.expm1(Gamma.logGamma(1-shape))/shape
    Some(location+scale*m)
  }
  def variance: Option[Double] = if(shape>=1) None else if(shape>=.5) Some(Double.PositiveInfinity) else {
    val v=if(shape==0) math.Pi*math.Pi/6 else if(math.abs(shape)<.001) ExtremeNumerics.varianceSmall(shape)
      else math.exp(2*Gamma.logGamma(1-shape))*math.expm1(Gamma.logGamma(1-2*shape)-2*Gamma.logGamma(1-shape))/(shape*shape)
    Some(scale*scale*v)
  }
}

/** Generalized Pareto excess above location; shape zero is exponential, shape -1 is uniform.
  * @param shape xi: zero or absolute value in [1e-8,2]
  * @param location threshold, absolute value <=1e100
  * @param scale positive scale in [1e-100,1e100]
  */
final case class GeneralizedParetoDistribution(shape: Double,location: Double=0,scale: Double=1) extends ScalarDistribution {
  ExtremeNumerics.shape(shape); N.location(location); N.scale(scale)
  private val upper=if(shape<0) ExtremeNumerics.endpoint(location,scale,shape) else Double.PositiveInfinity
  def support: (Double,Double)=(location,upper)
  private def logSurvival(x: Double): Double = {
    val z=(x-location)/scale
    if(shape==0) -z else -ExtremeNumerics.logBase(x,location,scale,shape)/shape
  }
  def logDensity(x: Double): Double = {
    N.argument(x)
    if(x<location || x>upper || x.isInfinity) Double.NegativeInfinity
    else if(x==upper) {
      if(shape> -1) Double.NegativeInfinity else if(shape== -1) -math.log(scale) else Double.PositiveInfinity
    } else {
      val v= -math.log(scale)+(1+shape)*logSurvival(x)
      if(v.isNaN) throw new ArithmeticException("GPD density unresolved at support boundary")
      v
    }
  }
  def cdf(x: Double): Double = { N.argument(x); if(x<=location) 0 else if(x>=upper) 1 else -math.expm1(logSurvival(x)) }
  def survival(x: Double): Double = { N.argument(x); if(x<=location) 1 else if(x>=upper) 0 else math.exp(logSurvival(x)) }
  def quantile(p: Double): Double = {
    N.probability(p); if(p==0) return location; if(p==1) return upper
    val v= -math.log1p(-p); val z=if(shape==0) v else v*ExtremeNumerics.ratio(shape*v)
    val x=N.interior(p,location+scale*z)
    if(x<=location || x>=upper) throw new ArithmeticException("GPD quantile collapsed to support boundary")
    x
  }
  def mean: Option[Double]=if(shape>=1) None else Some(location+scale/(1-shape))
  def variance: Option[Double]=if(shape>=1) None else Some(if(shape>=.5) Double.PositiveInfinity else scale*scale/((1-shape)*(1-shape)*(1-2*shape)))
}

object GeneralizedExtremeValue {
  /** @param shape maxima xi, zero or absolute value in [1e-8,2]
    * @param location center
    * @param scale positive scale
    * @param name contextual name
    * @param collection owning collection
    * @return observation-ready scalar; stochastic parameters use ScalarElement
    * @example `GeneralizedExtremeValue(.2,0,1)`
    */
  def apply(shape: Double,location: Double=0,scale: Double=1)(using name: Name[Double],collection: ElementCollection): AtomicScalar =
    ScalarElement(GeneralizedExtremeValueDistribution(shape,location,scale))
}
object GeneralizedPareto {
  /** @param shape excess-tail xi, zero or absolute value in [1e-8,2]
    * @param location threshold
    * @param scale positive scale
    * @param name contextual name
    * @param collection owning collection
    * @return observation-ready scalar; stochastic parameters use ScalarElement
    * @example `GeneralizedPareto(.2,0,1)`
    */
  def apply(shape: Double,location: Double=0,scale: Double=1)(using name: Name[Double],collection: ElementCollection): AtomicScalar =
    ScalarElement(GeneralizedParetoDistribution(shape,location,scale))
}
