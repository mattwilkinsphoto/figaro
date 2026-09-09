package com.cra.figaro.algorithm.sampling

import java.math.{BigDecimal as Decimal, MathContext, RoundingMode}
import com.cra.figaro.util.SamplingRandom

/** Opt-in, time-uniform mean bounds for independent identically distributed bounded draws.
  * This does not accept MCMC output or self-normalized importance estimates as IID observations.
  * The caller supplies the support and independence contract; Figaro cannot verify either from a trace.
  */
object BoundedIidPrecision {
  /** @param lower known, finite lower support bound (not the observed minimum)
    * @param upper known, finite upper support bound (not the observed maximum)
    * @param absoluteError desired maximum error of the reported sample mean
    * @param alpha simultaneous error probability, from 1e-12 through 0.5
    * @param maxDraws hard callback count, from 1 through 1000000
    * @param minDraws minimum work before successful stopping
    * @param checkEvery interval between checks; the final budget is always checked
    * @param seed private RNG seed
    * @param randomAlgorithm scientific RNG backend
    */
  final case class Config(lower: Double, upper: Double, absoluteError: Double,
    alpha: Double=0.05, maxDraws: Int=100000, minDraws: Int=100, checkEvery: Int=100,
    seed: Long=43, randomAlgorithm: SamplingRandom.Algorithm=SamplingRandom.defaultAlgorithm) {
    require(lower.isFinite && upper.isFinite && lower<=upper, "Known finite support required")
    require(absoluteError.isFinite && absoluteError>0 && alpha>=1e-12 && alpha<=0.5)
    require(maxDraws>=1 && maxDraws<=1000000 && minDraws>=1 && minDraws<=maxDraws && checkEvery>=1)
    require(randomAlgorithm!=null)
  }
  enum StopReason { case PrecisionReached, BudgetExhausted }
  /** Detached interval. errorBound bounds distance from mean to either endpoint, rounding outward.
    * The real-arithmetic statistical theorem assumes IID draws in the declared support.
    * Numerical bounds concern the returned binary64 observations, not errors inside the callback.
    */
  final case class Result(mean: Double, lower: Double, upper: Double, errorBound: Double,
    draws: Int, reason: StopReason, config: Config, randomProvider: String)

  private val up=new MathContext(40,RoundingMode.CEILING)
  private val down=new MathContext(40,RoundingMode.FLOOR)
  // Strict rational upper bound on log(2); see tools/test_bounded_iid_reference.py.
  private val logTwoUpper=new Decimal("0.6931471805599454")
  private def d(x: Double): Decimal=new Decimal(x) // exact binary64 value, NOT valueOf
  private def outward(x: Decimal, upward: Boolean): Double = {
    val v=x.doubleValue()
    if(v.isInfinite) v
    else if(upward && d(v).compareTo(x)<0) Math.nextUp(v)
    else if(!upward && d(v).compareTo(x)>0) Math.nextDown(v)
    else v
  }
  private def interval(sum: Decimal,n: Int,c: Config): Result = {
    val count=Decimal.valueOf(n.toLong)
    val meanLow=sum.divide(count,down); val meanHigh=sum.divide(count,up)
    val mean=sum.divide(count,new MathContext(40,RoundingMode.HALF_EVEN)).doubleValue()
    // 2*n*(n+1)/alpha <= 2^k. Replacing log by k*logTwoUpper is conservative.
    val numerator=Decimal.valueOf(2L*n*(n+1L))
    var power=d(c.alpha); var k=0
    while(power.compareTo(numerator)<0) { power=power.multiply(Decimal.valueOf(2)); k+=1 }
    val width=d(c.upper).subtract(d(c.lower))
    val square=width.multiply(width).multiply(logTwoUpper).multiply(Decimal.valueOf(k.toLong))
      .divide(Decimal.valueOf(2L*n),up)
    var radius=square.sqrt(up)
    // Verify the outward sqrt instead of relying on a transcendental binary64 error allowance.
    if(radius.multiply(radius).compareTo(square)<0) radius=radius.add(radius.ulp())
    require(radius.multiply(radius).compareTo(square)>=0,"Outward square-root check failed")
    val lo=outward(meanLow.subtract(radius).max(d(c.lower)),false)
    val hi=outward(meanHigh.add(radius).min(d(c.upper)),true)
    val error=outward(d(mean).subtract(d(lo)).max(d(hi).subtract(d(mean))),true)
    Result(mean,lo,hi,error,n,
      if(n>=c.minDraws && error<=c.absoluteError) StopReason.PrecisionReached else StopReason.BudgetExhausted,
      c,SamplingRandom.provenance(c.randomAlgorithm))
  }
  /** Draw until the outward interval meets absoluteError or the budget is exhausted.
    * @param config known support, precision, work and RNG policy, fixed before sampling
    * @param sample pure IID scalar draw using only the supplied private RNG; must return promptly
    * @return detached sample mean, confidence sequence interval and explicit stopping reason
    * @example `run(Config(0,1,0.02))(rng => if(rng.nextDouble()<0.3) 1.0 else 0.0)`
    */
  def run(config: Config)(sample: scala.util.Random => Double): Result = {
    ParetoTail.interrupted(); require(config!=null && sample!=null)
    val rng=SamplingRandom.scalaRandom(config.seed,config.randomAlgorithm)
    var sum=Decimal.ZERO; var n=0; var result: Result=null
    while(n<config.maxDraws) {
      ParetoTail.interrupted(); val value=sample(rng); ParetoTail.interrupted()
      require(value.isFinite && value>=config.lower && value<=config.upper,"Draw outside declared finite support")
      sum=sum.add(d(value)); n+=1
      if(n==config.maxDraws || (n>=config.minDraws && n%config.checkEvery==0)) {
        result=interval(sum,n,config)
        if(result.reason==StopReason.PrecisionReached) return result
      }
    }
    result
  }
}
