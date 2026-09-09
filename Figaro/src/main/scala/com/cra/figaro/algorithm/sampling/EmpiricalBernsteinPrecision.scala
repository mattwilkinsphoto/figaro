package com.cra.figaro.algorithm.sampling

import java.math.{BigDecimal as D, MathContext, RoundingMode}
import com.cra.figaro.util.SamplingRandom

/** Predictable plug-in empirical-Bernstein confidence sequence for bounded IID draws.
  * Based on Waudby-Smith/Ramdas, arXiv:2010.09686v7, Theorem 2.
  * This is the exponential construction, not inversion of the hedged capital process.
  */
object EmpiricalBernsteinPrecision {
  /** Detached output. Weighted estimate is NOT the ordinary arithmetic sample mean.
    * @param estimate predictable-weighted center in original units
    * @param sampleMean ordinary arithmetic sample mean, reported separately
    * @param lower outward lower confidence-sequence endpoint
    * @param upper outward upper confidence-sequence endpoint
    * @param errorBound maximum outward endpoint distance from estimate
    * @param draws completed IID draws
    * @param reason precision reached or budget exhausted
    * @param config fixed support, alpha, work and RNG policy
    * @param randomProvider replay provenance
    */
  final case class Result(estimate: Double,sampleMean: Double,lower: Double,upper: Double,errorBound: Double,
    draws: Int,reason: BoundedIidPrecision.StopReason,config: BoundedIidPrecision.Config,randomProvider: String)
  private val up=new MathContext(40,RoundingMode.CEILING)
  private val down=new MathContext(40,RoundingMode.FLOOR)
  private val nearest=new MathContext(40,RoundingMode.HALF_EVEN)
  private val two=D.valueOf(2)
  private def exact(x: Double)=new D(x)
  private def outward(x: D,upper: Boolean): Double = {
    val v=x.doubleValue()
    if(v.isInfinite) v
    else if(upper && exact(v).compareTo(x)<0) Math.nextUp(v)
    else if(!upper && exact(v).compareTo(x)>0) Math.nextDown(v)
    else v
  }
  // Dyadic, positive predictable bets; precomputed rational upper bounds for -log(1-lambda)-lambda.
  private val bets=Vector.tabulate(32)(i => exact(math.scalb(1.0,-i-1)))
  private val penalties=bets.map { l =>
    var power=l.multiply(l); var sum=D.ZERO
    for(j <- 2 to 48) { sum=sum.add(power.divide(D.valueOf(j),up),up); power=power.multiply(l) }
    sum.add(power.divide(D.valueOf(49).multiply(D.ONE.subtract(l)),up),up)
  }
  /** Adaptive bounded-IID mean estimation with time-uniform, outward-rounded intervals.
    * @param config same known support/absolute-error/budget contract as BoundedIidPrecision; chosen before sampling
    * @param sample pure IID draw using only the provided private RNG; must return promptly
    * @return weighted estimate and interval, ordinary sample mean, and explicit stop reason
    * @example `run(BoundedIidPrecision.Config(0,1,.02))(_.nextDouble())`
    */
  def run(config: BoundedIidPrecision.Config)(sample: scala.util.Random => Double): Result = {
    ParetoTail.interrupted(); require(config!=null && sample!=null)
    if(config.lower==config.upper) {
      val r=BoundedIidPrecision.run(config)(sample)
      return Result(r.mean,r.mean,r.lower,r.upper,r.errorBound,r.draws,r.reason,config,r.randomProvider)
    }
    val a=exact(config.lower); val b=exact(config.upper); val width=b.subtract(a)
    var alphaPower=exact(config.alpha); var k=0
    while(alphaPower.compareTo(two)<0) { alphaPower=alphaPower.multiply(two); k+=1 }
    val threshold=new D("0.6931471805599454").multiply(D.valueOf(k))
    val rng=SamplingRandom.scalaRandom(config.seed,config.randomAlgorithm)
    var total=D.ZERO; var weightedLow=D.ZERO; var weightedHigh=D.ZERO; var weight=D.ZERO
    var penalty=D.ZERO; var normalizedSum=0.5; var varianceSum=0.25; var prediction=0.5
    var n=0; var result: Result=null
    while(n<config.maxDraws) {
      ParetoTail.interrupted()
      // Only past observations affect this draw's weight. Approximate tuning cannot invalidate the bound.
      val desired=math.min(.5,math.sqrt(2*threshold.doubleValue()/
        ((varianceSum/(n+1))*(n+1)*math.log(n+2.0))))
      var index=0
      while(index<31 && bets(index).doubleValue()>desired) index+=1
      val l=bets(index); val predictor=exact(prediction)
      val x=sample(rng); ParetoTail.interrupted()
      require(x.isFinite && x>=config.lower && x<=config.upper,"Draw outside declared finite support")
      val value=exact(x); total=total.add(value); n+=1
      val offset=value.subtract(a)
      val low=offset.divide(width,down); val high=offset.divide(width,up)
      val residual=low.subtract(predictor).abs().max(high.subtract(predictor).abs())
      penalty=penalty.add(residual.multiply(residual,up).multiply(penalties(index),up),up)
      weight=weight.add(l)
      weightedLow=weightedLow.add(low.multiply(l,down),down)
      weightedHigh=weightedHigh.add(high.multiply(l,up),up)
      val normalized=low.add(high).divide(two,nearest).doubleValue().max(0).min(1)
      normalizedSum+=normalized; prediction=(normalizedSum/(n+1)).max(0).min(1)
      varianceSum+=math.pow(normalized-prediction,2)
      if(n==config.maxDraws || (n>=config.minDraws && n%config.checkEvery==0)) {
        val allowance=threshold.add(penalty,up)
        val lower=outward(weightedLow.subtract(allowance).divide(weight,down).multiply(width).add(a).max(a),false)
        val upper=outward(weightedHigh.add(allowance).divide(weight,up).multiply(width).add(a).min(b),true)
        val center=weightedLow.add(weightedHigh).divide(two.multiply(weight),nearest).multiply(width).add(a)
          .doubleValue().max(config.lower).min(config.upper)
        val error=outward(exact(center).subtract(exact(lower)).max(exact(upper).subtract(exact(center))),true)
        val reason=if(n>=config.minDraws && error<=config.absoluteError) BoundedIidPrecision.StopReason.PrecisionReached
          else BoundedIidPrecision.StopReason.BudgetExhausted
        result=Result(center,total.divide(D.valueOf(n),nearest).doubleValue(),lower,upper,error,n,reason,
          config,SamplingRandom.provenance(config.randomAlgorithm))
        if(reason==BoundedIidPrecision.StopReason.PrecisionReached) return result
      }
    }
    result
  }
}
