package com.cra.figaro.library.atomic.continuous

import com.cra.figaro.library.atomic.DistributionNumerics as N

/** Y = offset + multiplier X. Negative multipliers reflect the law.
  * @param base immutable continuous scalar law
  * @param offset finite translation, absolute value <= 1e100
  * @param multiplier signed nonzero scale, absolute value in [1e-100,1e100]
  */
final case class AffineDistribution(base: ScalarDistribution, offset: Double, multiplier: Double) extends ScalarDistribution {
  require(base != null); N.location(offset); N.scale(math.abs(multiplier))
  private def inverse(x: Double) = (x-offset)/multiplier
  def logDensity(x: Double): Double = { N.argument(x); base.logDensity(inverse(x))-math.log(math.abs(multiplier)) }
  def cdf(x: Double): Double = { N.argument(x); if(multiplier > 0) base.cdf(inverse(x)) else base.survival(inverse(x)) }
  def survival(x: Double): Double = { N.argument(x); if(multiplier > 0) base.survival(inverse(x)) else base.cdf(inverse(x)) }
  def quantile(p: Double): Double = {
    N.probability(p)
    // Invert the reflected law directly: computing 1-p loses tiny lower tails.
    if(multiplier < 0 && p > 0 && p < 1) ConstructionMath.inverse(this,p)
    else N.interior(p,offset+multiplier*base.quantile(if(multiplier > 0) p else 1-p))
  }
  def support: (Double,Double) = {
    val a=offset+multiplier*base.support._1; val b=offset+multiplier*base.support._2
    (math.min(a,b),math.max(a,b))
  }
  def mean: Option[Double] = base.mean.map(x => offset+multiplier*x)
  def variance: Option[Double] = base.variance.map(x => multiplier*multiplier*x)
  /** Transform a base draw directly, without repeatedly evaluating a CDF.
    * @param rng non-null caller-owned RNG
    * @return finite transformed draw; unrepresentable values throw, never retry selectively
    * @example `AffineDistribution(GaussianDistribution(0,1),3,-2).sample(new scala.util.Random(42))`
    */
  override def sample(rng: scala.util.Random): Double = {
    val x=offset+multiplier*base.sample(rng)
    if(!x.isFinite || !logDensity(x).isFinite) throw new ArithmeticException("affine sample outside finite-density numeric support")
    x
  }
}

/** Y = exp(X), with the inverse-Jacobian -log(y) included in its log density.
  * @param base immutable continuous scalar law; unrepresentable draws throw
  */
final case class ExpDistribution(base: ScalarDistribution) extends ScalarDistribution {
  require(base != null)
  def logDensity(x: Double): Double = { N.argument(x); if(x <= 0 || x.isInfinity) Double.NegativeInfinity else base.logDensity(math.log(x))-math.log(x) }
  def cdf(x: Double): Double = { N.argument(x); if(x <= 0) 0 else base.cdf(math.log(x)) }
  def survival(x: Double): Double = { N.argument(x); if(x <= 0) 1 else base.survival(math.log(x)) }
  def quantile(p: Double): Double = {
    N.probability(p); val x=N.interior(p,math.exp(base.quantile(p)))
    if(p > 0 && p < 1 && x == 0) throw new ArithmeticException("exponential quantile underflow")
    x
  }
  def support: (Double,Double) = (math.exp(base.support._1),math.exp(base.support._2))
  /** No generic moment-generating function is assumed; None also means not implemented. */
  def mean: Option[Double] = None
  def variance: Option[Double] = None
  /** Transform a base draw directly; no inverse-CDF search or representability retry.
    * @param rng non-null caller-owned RNG
    * @return finite positive draw
    * @example `ExpDistribution(GaussianDistribution(0,1)).sample(new scala.util.Random(42))`
    */
  override def sample(rng: scala.util.Random): Double = {
    val x=math.exp(base.sample(rng))
    if(!x.isFinite || x == 0 || !logDensity(x).isFinite) throw new ArithmeticException("exponential sample outside finite-density numeric support")
    x
  }
}

/** Conditional scalar law on [lower,upper], not censoring or clipping.
  * @param base continuous law, without point masses
  * @param lower finite lower endpoint inside the base support
  * @param upper finite upper endpoint greater than lower and inside the base support
  * Normalizers unresolved at binary64 precision are refused. Moments are not computed.
  */
final case class TruncatedDistribution(base: ScalarDistribution,lower: Double,upper: Double) extends ScalarDistribution {
  require(base != null && lower.isFinite && upper.isFinite && lower < upper,"finite ordered bounds required")
  require(lower >= base.support._1 && upper <= base.support._2,"bounds must lie inside base support")
  private def interval(a: Double,b: Double,normalizing: Boolean=false): Double = {
    if(a == b) return 0
    val fa=base.cdf(a); val fb=base.cdf(b); val sa=base.survival(a); val sb=base.survival(b)
    val (large,small)=if(fb <= sa) (fb,fa) else (sa,sb)
    val mass=large-small
    // A barely nonzero difference is not a usable normalizer: its relative
    // error multiplies every density. Keep a much larger cancellation margin
    // there than for a small subinterval CDF query. This is a roundoff guard,
    // not a certificate of the base law's CDF accuracy.
    val margin=if(normalizing) 1e8 else 64.0
    if(!mass.isFinite || mass <= 0 || mass <= margin*math.ulp(large))
      throw new ArithmeticException("truncation interval mass is numerically unresolved")
    mass
  }
  /** Retained probability under the original law, not a fitted parameter. */
  val retainedProbability: Double = interval(lower,upper,normalizing=true)
  def logDensity(x: Double): Double = { N.argument(x); if(x < lower || x > upper) Double.NegativeInfinity else base.logDensity(x)-math.log(retainedProbability) }
  def cdf(x: Double): Double = { N.argument(x); if(x <= lower) 0 else if(x >= upper) 1 else interval(lower,x)/retainedProbability }
  def survival(x: Double): Double = { N.argument(x); if(x <= lower) 1 else if(x >= upper) 0 else interval(x,upper)/retainedProbability }
  def quantile(p: Double): Double = { N.probability(p); if(p == 0) lower else if(p == 1) upper else ConstructionMath.bisect(this,p,lower,upper) }
  def support: (Double,Double) = (lower,upper)
  def mean: Option[Double] = None
  def variance: Option[Double] = None
}

/** A finite continuous mixture; weights are probabilities, not arbitrary amplitudes.
  * @param weights immutable normalized nonnegative weights, sum within 1e-12 of one
  * @param components matching immutable laws (1..1024); zero-weight components are ignored
  * @example `ScalarMixtureDistribution(Vector(.3,.7),Vector(GaussianDistribution(-2,1),GaussianDistribution(2,1)))`
  */
final case class ScalarMixtureDistribution(weights: Vector[Double],components: Vector[ScalarDistribution]) extends ScalarDistribution {
  private val normalized=ConstructionMath.weights(weights,components)
  private val active=components.indices.filter(normalized(_) > 0).toVector
  def logDensity(x: Double): Double = { N.argument(x); ConstructionMath.logSum(active.map(i => math.log(normalized(i))+components(i).logDensity(x))) }
  def cdf(x: Double): Double = { N.argument(x); math.min(1,active.map(i => normalized(i)*components(i).cdf(x)).sum) }
  def survival(x: Double): Double = { N.argument(x); math.min(1,active.map(i => normalized(i)*components(i).survival(x)).sum) }
  def support: (Double,Double) = (active.map(i => components(i).support._1).min,active.map(i => components(i).support._2).max)
  def quantile(p: Double): Double = {
    N.probability(p)
    if(p == 0) support._1 else if(p == 1) support._2 else {
      val bounds=active.map(i => components(i).quantile(p))
      ConstructionMath.bisect(this,p,bounds.min,bounds.max)
    }
  }
  /** Draw component then value, avoiding a mixture inverse-CDF search. Uses only caller RNG. */
  override def sample(rng: scala.util.Random): Double = components(ConstructionMath.choose(normalized,rng)).sample(rng)
  def mean: Option[Double] = if(active.exists(i => components(i).mean.isEmpty)) None else {
    val value=active.map(i => normalized(i)*components(i).mean.get).sum
    if(value.isNaN) throw new ArithmeticException("mixture mean unresolved")
    Some(value)
  }
  def variance: Option[Double] = mean.flatMap { m =>
    if(!m.isFinite || active.exists(i => components(i).variance.isEmpty)) None
    else Some(active.map { i => val d=components(i).mean.get-m; normalized(i)*(components(i).variance.get+d*d) }.sum)
  }
  /** @param x finite-density observation
    * @return component membership probabilities, in original order; undefined observations throw
    * @example `mixture.responsibilities(0.2)`
    */
  def responsibilities(x: Double): Vector[Double] = {
    val log=logDensity(x); if(!log.isFinite) throw new ArithmeticException("responsibilities require a finite log density")
    components.indices.map(i => if(normalized(i) == 0) 0.0 else math.exp(math.log(normalized(i))+components(i).logDensity(x)-log)).toVector
  }
}

private[continuous] object ConstructionMath {
  def weights[A](w: Vector[Double],c: Vector[A]): Vector[Double] = {
    require(w != null && c != null && w.nonEmpty && w.size <= 1024 && w.size == c.size && c.forall(_ != null),"1..1024 matching components required")
    require(w.forall(x => x.isFinite && x >= 0) && math.abs(w.sum-1) <= 1e-12,"weights must sum to one")
    w.map(_/w.sum)
  }
  def logSum(xs: Vector[Double]): Double = {
    if(xs.exists(_.isNaN)) throw new ArithmeticException("NaN component log likelihood")
    val m=xs.max
    if(m.isInfinity) m else m+math.log(xs.map(x => math.exp(x-m)).sum)
  }
  def choose(w: Vector[Double],rng: scala.util.Random): Int = {
    val u=N.open(rng); var total=0.0; var i=0
    while(i < w.size) { total += w(i); if(u < total) return i; i += 1 }
    w.lastIndexWhere(_ > 0)
  }
  def reached(d: ScalarDistribution,p: Double,x: Double): Boolean = if(p <= .5) d.cdf(x) >= p else d.survival(x) <= 1-p
  def bisect(d: ScalarDistribution,p: Double,start: Double,end: Double): Double = {
    var lo=start; var hi=end; var j=0
    if(!lo.isFinite || !hi.isFinite) throw new ArithmeticException("finite quantile bracket required")
    while(j < 2048 && lo < hi) {
      N.check(); val mid=lo/2+hi/2
      if(mid == lo || mid == hi) return N.interior(p,hi)
      if(reached(d,p,mid)) hi=mid else lo=mid
      j += 1
    }
    if(lo != hi) throw new ArithmeticException("quantile bisection budget exhausted")
    N.interior(p,hi)
  }
  def inverse(d: ScalarDistribution,p: Double): Double = {
    var lo=math.max(-1.0,d.support._1); var hi=math.min(1.0,d.support._2)
    if(lo > hi) {
      if(d.support._1 > 1) { lo=d.support._1; hi=math.min(d.support._2,lo+math.max(1,math.abs(lo))) }
      else { hi=d.support._2; lo=math.max(d.support._1,hi-math.max(1,math.abs(hi))) }
    }
    var j=0
    while(reached(d,p,lo) && lo > d.support._1 && j < 2048) { N.check(); lo=math.max(d.support._1,if(lo >= 0) -1 else lo*2); j += 1 }
    while(!reached(d,p,hi) && hi < d.support._2 && j < 4096) { N.check(); hi=math.min(d.support._2,if(hi <= 0) 1 else hi*2); j += 1 }
    bisect(d,p,lo,hi)
  }
}
