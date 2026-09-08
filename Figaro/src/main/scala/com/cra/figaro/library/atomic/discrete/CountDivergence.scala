package com.cra.figaro.library.atomic.discrete

import com.cra.figaro.library.atomic.{DistributionNumerics as N,InformationMetricResult,InformationMetricStatus,MetricCalculation as M}
import java.util.concurrent.CancellationException

/** Same-family count divergences: finite sums for hypergeometric; analytic or tail-bounded
  * infinite sums for negative binomial. No truncation is silently renormalized.
  */
object CountDivergence {
  import InformationMetricStatus.*
  /** @param p source count law
    * @param q comparison law of the same family and count convention
    * @param tolerance positive finite absolute target in nats, default 1e-8
    * @param maxTerms maximum count terms in [1,100000], default 10000
    * @param cancelled non-null cooperative cancellation callback; exceptions propagate
    * @return directed KL, genuine support infinity, or explicit refusal
    * @example `CountDivergence.kl(NegativeBinomialDistribution(2,.4),NegativeBinomialDistribution(3,.6))`
    */
  def kl(p: CountDistribution,q: CountDistribution,tolerance: Double=1e-8,maxTerms: Int=10000,
    cancelled: () => Boolean=() => false): InformationMetricResult = compare(p,q,false,tolerance,maxTerms,cancelled)
  /** @param p first count law
    * @param q second law of the same family
    * @param tolerance positive finite absolute target in nats, default 1e-8
    * @param maxTerms maximum count terms in [1,100000], default 10000
    * @param cancelled non-null cooperative callback
    * @return symmetric negative log affinity; infinity means mathematically zero overlap
    * @example `CountDivergence.bhattacharyya(HypergeometricDistribution(20,7,5),HypergeometricDistribution(20,9,5))`
    */
  def bhattacharyya(p: CountDistribution,q: CountDistribution,tolerance: Double=1e-8,maxTerms: Int=10000,
    cancelled: () => Boolean=() => false): InformationMetricResult = compare(p,q,true,tolerance,maxTerms,cancelled)

  private def compare(p: CountDistribution,q: CountDistribution,overlap: Boolean,tol: Double,budget: Int,cancelled: () => Boolean): InformationMetricResult = {
    require(p != null && q != null,"non-null count laws required")
    require(tol.isFinite && tol > 0,"positive finite tolerance required")
    require(budget >= 1 && budget <= 100000,"term budget must be in [1,100000]")
    require(cancelled != null,"non-null cancellation callback required")
    def check(): Unit = { N.check(); if(cancelled()) throw new CancellationException("count divergence cancelled") }
    check()
    if(p.getClass != q.getClass) return M.unavailable(Unsupported)
    if(p == q) return M.identity
    (p,q) match {
      case (a: ZeroAdjustedDistribution,b: ZeroAdjustedDistribution) if a.base == b.base =>
        val pa=Vector(a.probability(0),a.survival(0)); val pb=Vector(b.probability(0),b.survival(0))
        return if(overlap) com.cra.figaro.library.atomic.DiscreteInformation.bhattacharyya(pa,pb,tol)
          else com.cra.figaro.library.atomic.DiscreteInformation.kl(pa,pb,tol)
      case _ => ()
    }
    p match {
      case _: NegativeBinomialDistribution | _: HypergeometricDistribution => ()
      case _ => return M.unavailable(Unsupported)
    }
    (p,q) match {
      case (a: NegativeBinomialDistribution,b: NegativeBinomialDistribution) =>
        if(a.successProbability == 1) return M.analytic(-b.logProbability(0)*(if(overlap) .5 else 1),tol)
        if(b.successProbability == 1) return if(overlap) M.analytic(-.5*a.logProbability(0),tol) else M.infinite
        if(a.successes == b.successes) {
          val first=a.successes*math.log(a.successProbability/b.successProbability)
          val second=a.mean*(math.log1p(-a.successProbability)-math.log1p(-b.successProbability))
          if(!overlap) return M.analytic(first+second,tol,math.abs(first)+math.abs(second))
          val c=.5*(math.log1p(-a.successProbability)+math.log1p(-b.successProbability))
          val logAffinity=a.successes*(.5*(math.log(a.successProbability)+math.log(b.successProbability))-N.log1mexp(c))
          return M.analytic(-logAffinity,tol,a.successes*(1+math.abs(c)))
        }
      case _ => ()
    }
    val (pl,pu)=p.support; val (ql,qu)=q.support
    if(!overlap && (pl < ql || (pu.nonEmpty && qu.nonEmpty && pu.get > qu.get))) return M.infinite
    if(overlap && pu.nonEmpty && qu.nonEmpty && math.max(pl,ql) > math.min(pu.get,qu.get)) return M.infinite
    val lower=if(overlap) math.max(pl,ql) else pl
    val upper=if(overlap) for(a <- pu;b <- qu) yield math.min(a,b) else pu
    if(upper.exists(_.toLong-lower+1 > budget)) return M.unavailable(BudgetExhausted)
    var k=lower; var work=0; var total=0.0; var correction=0.0; var absolute=0.0
    while(work < budget && upper.forall(k <= _)) {
      check()
      val lp=p.logProbability(k); val lq=q.logProbability(k)
      if(lp.isNaN || lq.isNaN) return M.unavailable(NumericallyUnresolved,work)
      val value=if(overlap) math.exp(.5*(lp+lq)) else if(lp == Double.NegativeInfinity) 0.0 else math.exp(lp)*(lp-lq)
      if(!value.isFinite) return M.unavailable(NumericallyUnresolved,work)
      val add=value-correction; val next=total+add; correction=(next-total)-add; total=next; absolute += math.abs(value)
      work += 1
      val tail=upper match {
        case Some(end) => if(k == end) 0.0 else Double.PositiveInfinity
        case None =>
          val a=p.asInstanceOf[NegativeBinomialDistribution]; val b=q.asInstanceOf[NegativeBinomialDistribution]
          val nextK=k+1
          def ratio(d: NegativeBinomialDistribution) = (1-d.successProbability)*math.max(1,(nextK+d.successes)/(nextK+1))
          val rp=ratio(a); val rq=ratio(b)
          if(overlap) {
            val r=math.sqrt(rp*rq)
            if(r >= 1) Double.PositiveInfinity else math.exp(.5*(a.logProbability(nextK)+b.logProbability(nextK)))/(1-r)
          } else if(rp >= 1) Double.PositiveInfinity else {
            val logRatio=a.logProbability(nextK)-b.logProbability(nextK)
            val slope=math.abs(math.log1p(-a.successProbability)-math.log1p(-b.successProbability))+
              math.abs(math.log((nextK+a.successes)/(nextK+b.successes)))
            math.exp(a.logProbability(nextK))/(1-rp)*(math.abs(logRatio)+slope*rp/(1-rp))
          }
      }
      val shape=p match { case a: NegativeBinomialDistribution => math.max(a.successes,q.asInstanceOf[NegativeBinomialDistribution].successes); case _ => 1.0 }
      val roundoff=128*math.ulp(1.0)*(1+absolute)*(1+work+shape)
      val rawError=tail+roundoff
      val error=if(overlap && total > rawError) rawError/(total-rawError) else if(overlap) Double.PositiveInfinity else rawError
      val result=if(overlap) -math.log(total) else total
      if(error.isFinite && error <= tol && result.isFinite && result >= -error)
        return InformationMetricResult(Estimated,Some(math.max(0,result)),error,work,if(upper.nonEmpty) "finite-sum" else "bounded-count-tail")
      if(upper.contains(k)) return M.unavailable(NumericallyUnresolved,work,"finite-sum")
      k += 1
    }
    M.unavailable(BudgetExhausted,work,"bounded-count-tail")
  }
}
