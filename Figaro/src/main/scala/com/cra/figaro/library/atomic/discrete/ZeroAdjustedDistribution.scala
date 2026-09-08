package com.cra.figaro.library.atomic.discrete

import com.cra.figaro.library.atomic.DistributionNumerics as N

/** Count-only zero adjustment; never mixes a point mass with a continuous density.
  * @param base immutable nonnegative count law
  * @param zeroProbability probability of the structural-zero branch, in [0,1]
  * @param hurdle true removes ALL base zeros; false adds structural zeros to unchanged base
  * @example `CountElement(ZeroAdjustedDistribution(NegativeBinomialDistribution(2,.5),.3,true))`
  */
final case class ZeroAdjustedDistribution(base: CountDistribution,zeroProbability: Double,hurdle: Boolean=false) extends CountDistribution {
  require(base != null && base.support._1 >= 0,"nonnegative count law required"); N.probability(zeroProbability)
  private val retained=if(hurdle) base.survival(0) else 1.0
  require(zeroProbability == 1 || retained > 0,"hurdle requires positive-count probability")
  private val logScale=math.log1p(-zeroProbability)-math.log(retained)
  def logProbability(k: Int): Double = {
    N.check()
    if(k < 0) Double.NegativeInfinity
    else if(zeroProbability == 1) { if(k == 0) 0 else Double.NegativeInfinity }
    else if(k > 0) logScale+base.logProbability(k)
    else if(hurdle) math.log(zeroProbability)
    else {
      val a=math.log(zeroProbability); val b=logScale+base.logProbability(0); val m=math.max(a,b)
      if(m == Double.NegativeInfinity) m else m+math.log(math.exp(a-m)+math.exp(b-m))
    }
  }
  def survival(k: Int): Double = { N.check(); if(k < 0) 1 else if(zeroProbability == 1) 0 else (1-zeroProbability)*(base.survival(k)/retained) }
  def cdf(k: Int): Double = {
    N.check()
    if(k < 0) 0 else if(k == 0) probability(0) else if(zeroProbability == 1) 1
    else if(!hurdle) zeroProbability+(1-zeroProbability)*base.cdf(k)
    else -math.expm1(math.log(survival(k)))
  }
  def support: (Int,Option[Int]) = {
    if(zeroProbability == 1) (0,Some(0))
    else (if(zeroProbability > 0) 0 else math.max(if(hurdle) 1 else 0,base.support._1),base.support._2)
  }
  def quantile(p: Double): Int = {
    N.probability(p)
    if(p == 0) return support._1
    if(p == 1) return support._2.getOrElse(throw new ArithmeticException("unbounded count endpoint"))
    def reached(k: Int) = if(p <= .5) cdf(k) >= p else survival(k) <= 1-p
    val upper=support._2.getOrElse(Int.MaxValue)
    if(!reached(upper)) throw new ArithmeticException("count quantile exceeds Int")
    var lo=support._1.toLong-1; var hi=upper.toLong
    while(hi-lo > 1) { N.check(); val mid=(lo+hi)/2; if(reached(mid.toInt)) hi=mid else lo=mid }
    hi.toInt
  }
  def mean: Double = if(zeroProbability == 1) 0 else (1-zeroProbability)*(base.mean/retained)
  def variance: Double = if(zeroProbability == 1) 0 else {
    val second=(1-zeroProbability)*((base.variance+base.mean*base.mean)/retained)
    val result=second-mean*mean
    if(!result.isFinite || result < -64*math.ulp(second)) throw new ArithmeticException("zero-adjusted variance unresolved")
    math.max(0,result)
  }
}
