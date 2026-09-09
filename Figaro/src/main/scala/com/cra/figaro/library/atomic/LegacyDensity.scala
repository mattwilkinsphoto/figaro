package com.cra.figaro.library.atomic

/** Shared implementation for the legacy adapters; public conventions live in their named APIs. */
private[figaro] object LegacyDensity {
  import org.apache.commons.math3.special.{Gamma as G}
  def positive(x: Double): Unit=require(x.isFinite && x>0,"finite positive parameter required")
  def argument(x: Double): Unit=DistributionNumerics.argument(x)
  def resolved(x: Double): Double={ if(x.isNaN) throw new ArithmeticException("log density numerically unresolved"); x }
  private def power(a: Double,x: Double): Double=if(a==0) 0.0 else a*math.log(x)
  def normal(mu: Double,v: Double,x: Double): Double = {
    require(mu.isFinite); positive(v); argument(x)
    val diff=x-mu; val sd=math.sqrt(v)
    val z=if(diff.isFinite) diff/sd else x/sd-mu/sd
    -.5*math.log(2*math.Pi)-.5*math.log(v)-.5*z*z
  }
  def gamma(a: Double,s: Double,x: Double): Double = {
    positive(a); positive(s); argument(x)
    if(x<0 || x.isInfinity) Double.NegativeInfinity
    else resolved(power(a-1,x)-x/s-G.logGamma(a)-a*math.log(s))
  }
  def inverseGamma(a: Double,s: Double,x: Double): Double = {
    positive(a); positive(s); argument(x)
    if(x<=0 || x.isInfinity) Double.NegativeInfinity
    else resolved(a*math.log(s)-G.logGamma(a)-(a+1)*math.log(x)-s/x)
  }
  def beta(a: Double,b: Double,x: Double): Double = {
    positive(a); positive(b); argument(x)
    if(x<0 || x>1) Double.NegativeInfinity
    else resolved(power(a-1,x)+(if(b==1) 0 else (b-1)*math.log1p(-x))-org.apache.commons.math3.special.Beta.logBeta(a,b))
  }
  def exponential(rate: Double,x: Double): Double = {
    positive(rate); argument(x)
    if(x<0 || x.isInfinity) Double.NegativeInfinity else math.log(rate)-rate*x
  }
  def uniform(lo: Double,hi: Double,x: Double): Double = {
    require(lo.isFinite && hi.isFinite && hi>lo && (hi-lo).isFinite,"finite ordered uniform endpoints required")
    argument(x); if(x<lo || x>=hi) Double.NegativeInfinity else -math.log(hi-lo)
  }
  def dirichlet(a: Array[Double],x: Array[Double]): Double = {
    DistributionNumerics.check()
    require(a!=null && a.length>=2 && a.forall(v => v.isFinite && v>0) && a.sum.isFinite)
    require(x!=null && x.length==a.length && x.forall(!_.isNaN),"matching non-NaN simplex coordinates required")
    if(x.exists(v => !v.isFinite || v<0 || v>1) || math.abs(x.sum-1)>1e-12) return Double.NegativeInfinity
    resolved(G.logGamma(a.sum)-a.map(G.logGamma).sum+a.zip(x).map((ai,xi) => power(ai-1,xi)).sum)
  }
  def poisson(rate: Double,k: Int): Double = {
    DistributionNumerics.check(); require(rate.isFinite && rate>=0)
    if(k<0) Double.NegativeInfinity else if(rate==0) { if(k==0) 0 else Double.NegativeInfinity }
    else k*math.log(rate)-rate-G.logGamma(k+1.0)
  }
  def geometric(failure: Double,k: Int): Double = {
    DistributionNumerics.check(); require(failure.isFinite && failure>=0 && failure<1)
    if(k<1) Double.NegativeInfinity else if(failure==0) { if(k==1) 0 else Double.NegativeInfinity }
    else (k-1.0)*math.log(failure)+math.log1p(-failure)
  }
  def binomial(n: Int,p: Double,k: Int): Double = {
    DistributionNumerics.check(); require(n>=0); DistributionNumerics.probability(p)
    if(k<0 || k>n) Double.NegativeInfinity
    else new org.apache.commons.math3.distribution.BinomialDistribution(n,p).logProbability(k)
  }
}
