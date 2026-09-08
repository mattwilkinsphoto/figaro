package com.cra.figaro.library.atomic.discrete

import com.cra.figaro.library.atomic.DistributionNumerics as N
import org.apache.commons.math3.special.Beta as B
import org.apache.commons.math3.distribution.{HypergeometricDistribution as Hypergeom}

/** Immutable integer-count law; no RNG or universe is retained. */
sealed trait CountDistribution {
  /** @param k count (negative integers are outside these supports)
    * @return natural log probability mass, or negative infinity outside support
    * @example `NegativeBinomialDistribution(2.5,.4).logProbability(3)`
    */
  def logProbability(k: Int): Double
  /** @param k count
    * @return mass, subject to exponential underflow
    * @example `HypergeometricDistribution(20,7,5).probability(2)`
    */
  final def probability(k: Int): Double = math.exp(logProbability(k))
  /** @param k count
    * @return P(X<=k)
    * @example `NegativeBinomialDistribution(2,.5).cdf(4)`
    */
  def cdf(k: Int): Double
  /** @param k count
    * @return P(X>k), evaluated directly
    * @example `NegativeBinomialDistribution(2,.5).survival(4)`
    */
  def survival(k: Int): Double
  /** @param p probability in [0,1]
    * @return smallest supported count with CDF>=p; unbounded p=1 and Int overflow throw
    * @example `HypergeometricDistribution(20,7,5).quantile(.5)`
    */
  def quantile(p: Double): Int
  /** @return minimum count and optional finite maximum; None means mathematically unbounded */
  def support: (Int,Option[Int])
  /** @return theoretical mean in count units */
  def mean: Double
  /** @return theoretical variance in squared count units */
  def variance: Double
  /** @param rng non-null caller-owned RNG
    * @return count from inverse transform; out-of-Int tail draws throw, never clip
    * @example `NegativeBinomialDistribution(2,.5).sample(new scala.util.Random(42))`
    */
  final def sample(rng: scala.util.Random): Int = quantile(N.open(rng))
}

/** Failures before r successes, generalized to positive real r; NOT total trials.
  * @param successes positive real shape r in [0.001,1e6]
  * @param successProbability p in [1e-6,1]; p=1 is a point mass at zero
  */
final case class NegativeBinomialDistribution(successes: Double,successProbability: Double) extends CountDistribution {
  N.shape(successes)
  require(successProbability.isFinite && successProbability >= 1e-6 && successProbability <= 1,"success probability must be in [1e-6,1]")
  def logProbability(k: Int): Double = {
    N.check()
    if(k < 0 || (successProbability == 1 && k != 0)) Double.NegativeInfinity
    else if(k == 0) successes*math.log(successProbability)
    else -B.logBeta(successes,k.toDouble+1)-math.log(successes+k)+successes*math.log(successProbability)+k*math.log1p(-successProbability)
  }
  def cdf(k: Int): Double = { N.check(); if(k < 0) 0 else if(successProbability == 1) 1 else N.regularizedBeta(successProbability,successes,k.toDouble+1) }
  def survival(k: Int): Double = { N.check(); if(k < 0) 1 else if(successProbability == 1) 0 else N.regularizedBeta(1-successProbability,k.toDouble+1,successes) }
  def quantile(p: Double): Int = {
    N.probability(p)
    if(p == 0 || successProbability == 1) return 0
    if(p == 1 || cdf(Int.MaxValue) < p) throw new ArithmeticException("negative-binomial quantile exceeds Int support representation")
    var lo = -1L; var hi=Int.MaxValue.toLong
    while(hi-lo > 1) { N.check(); val middle=(hi+lo)/2; if(cdf(middle.toInt) >= p) hi=middle else lo=middle }
    hi.toInt
  }
  def support = (0,if(successProbability == 1) Some(0) else None)
  def mean = successes*(1-successProbability)/successProbability
  def variance = mean/successProbability
}

/** Successes in draws without replacement; includes deterministic finite cases.
  * @param population population N in [1,100000], bounding cumulative-sum work
  * @param successes number of successes K in [0,N]
  * @param draws draw count n in [0,N]
  */
final case class HypergeometricDistribution(population: Int,successes: Int,draws: Int) extends CountDistribution {
  require(population >= 1 && population <= 100000,"population must be in [1,100000]")
  require(successes >= 0 && successes <= population && draws >= 0 && draws <= population,"successes and draws must be in [0,population]")
  private val engine=new Hypergeom(null,population,successes,draws)
  private val lower=math.max(0,draws+successes-population); private val upper=math.min(successes,draws)
  def logProbability(k: Int): Double = { N.check(); if(k < lower || k > upper) Double.NegativeInfinity else if(lower == upper) 0 else engine.logProbability(k) }
  def cdf(k: Int): Double = { N.check(); if(k < lower) 0 else if(k >= upper) 1 else engine.cumulativeProbability(k) }
  def survival(k: Int): Double = { N.check(); if(k < lower) 1 else if(k >= upper) 0 else engine.upperCumulativeProbability(k+1) }
  def quantile(p: Double): Int = { N.probability(p); var lo=lower.toLong-1; var hi=upper.toLong
    if(p == 0) return lower
    while(hi-lo > 1) { N.check(); val mid=(hi+lo)/2; if(cdf(mid.toInt) >= p) hi=mid else lo=mid }; hi.toInt }
  def support = (lower,Some(upper))
  def mean = draws.toDouble*successes/population
  def variance = if(population == 1) 0 else mean*(1-successes.toDouble/population)*(population-draws)/(population-1)
}
