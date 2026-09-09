package com.cra.figaro.library.atomic

import org.apache.commons.math3.special.{Gamma as G}

/** Analytic information measures for legacy families. Parameters describe fixed laws,
  * not mutable learned Element state. Natural logarithms/nats; no sampling confidence claims.
  * See docs/LEGACY_DISTRIBUTION_CONTRACTS.md for the complete parameter/reference-measure table.
  */
object LegacyInformation {
  private def tolerance(t: Double): Unit={ DistributionNumerics.check(); require(t.isFinite && t>0) }
  private def gamma(a: Double,s: Double,b: Double,t: Double,eps: Double,bh: Boolean,inverse: Boolean): InformationMetricResult = {
    tolerance(eps); Vector(a,b).foreach(DistributionNumerics.shape); Vector(s,t).foreach(DistributionNumerics.scale)
    if(a==b && s==t) return MetricCalculation.identity
    val ls=math.log(s)*(if(inverse) -1 else 1); val lt=math.log(t)*(if(inverse) -1 else 1)
    val ga=G.logGamma(a); val gb=G.logGamma(b)
    val terms=if(bh) {
      val h=.5*(a+b); val m=math.max(-ls,-lt)
      val lr=m+math.log1p(math.exp(-math.abs(ls-lt)))-math.log(2)
      Vector(.5*(ga+gb),.5*(a*ls+b*lt),h*lr,-G.logGamma(h))
    } else Vector((a-b)*DistributionNumerics.digammaPositive(a),-ga,gb,-b*(ls-lt),a*math.expm1(ls-lt))
    MetricCalculation.analytic(terms.sum,eps,terms.map(math.abs).sum,"gamma exponential family / reciprocal invariance")
  }
  /** @param shapeP source shape in [.001,1e6]
    * @param scaleP source scale in [1e-100,1e100]
    * @param shapeQ comparison shape
    * @param scaleQ comparison scale (not rate)
    * @param tolerance positive numerical allowance in nats
    * @return directed KL or numerical refusal
    * @example `LegacyInformation.gammaKl(2,3,4,5)`
    */
  def gammaKl(shapeP: Double,scaleP: Double,shapeQ: Double,scaleQ: Double,tolerance: Double=1e-8): InformationMetricResult=gamma(shapeP,scaleP,shapeQ,scaleQ,tolerance,false,false)
  /** Same parameters as gammaKl; returns symmetric negative log affinity, or refusal.
    * @example `LegacyInformation.gammaBhattacharyya(2,3,4,5)` */
  def gammaBhattacharyya(shapeP: Double,scaleP: Double,shapeQ: Double,scaleQ: Double,tolerance: Double=1e-8): InformationMetricResult=gamma(shapeP,scaleP,shapeQ,scaleQ,tolerance,true,false)
  /** Shapes/scales as in InverseGamma (density contains exp(-scale/x)); directed KL in nats.
    * @example `LegacyInformation.inverseGammaKl(2,3,4,5)` */
  def inverseGammaKl(shapeP: Double,scaleP: Double,shapeQ: Double,scaleQ: Double,tolerance: Double=1e-8): InformationMetricResult=gamma(shapeP,scaleP,shapeQ,scaleQ,tolerance,false,true)
  /** InverseGamma parameters as above; symmetric negative log affinity in nats.
    * @example `LegacyInformation.inverseGammaBhattacharyya(2,3,4,5)` */
  def inverseGammaBhattacharyya(shapeP: Double,scaleP: Double,shapeQ: Double,scaleQ: Double,tolerance: Double=1e-8): InformationMetricResult=gamma(shapeP,scaleP,shapeQ,scaleQ,tolerance,true,true)

  private def dirichlet(p: Vector[Double],q: Vector[Double],eps: Double,bh: Boolean): InformationMetricResult = {
    tolerance(eps); require(p!=null && q!=null && p.size>=2 && p.size<=128 && p.size==q.size)
    (p++q).foreach(DistributionNumerics.shape)
    if(p==q) return MetricCalculation.identity
    def logB(x: Vector[Double]): Double=x.map(G.logGamma).sum-G.logGamma(x.sum)
    val bp=logB(p); val bq=logB(q)
    val terms=if(bh) Vector(.5*bp,.5*bq,-logB(p.zip(q).map((a,b) => .5*(a+b))))
      else Vector(bq,-bp) ++ p.zip(q).map((a,b) => (a-b)*(DistributionNumerics.digammaPositive(a)-DistributionNumerics.digammaPositive(p.sum)))
    // Include normalizer constituent magnitudes, not only their canceled totals.
    val magnitude=terms.map(math.abs).sum+(p++q).map(a => math.abs(G.logGamma(a))).sum+math.abs(G.logGamma(p.sum))+math.abs(G.logGamma(q.sum))
    MetricCalculation.analytic(terms.sum,eps,magnitude,"Dirichlet normalizer")
  }
  /** @param alphaP source concentration vector, length 2..128, entries [.001,1e6]
    * @param alphaQ comparison concentrations in the same category order
    * @param tolerance positive numerical allowance in nats
    * @return directed KL in the simplex measure, or refusal
    * @example `LegacyInformation.dirichletKl(Vector(2,3,4),Vector(3,4,5))` */
  def dirichletKl(alphaP: Vector[Double],alphaQ: Vector[Double],tolerance: Double=1e-8): InformationMetricResult=dirichlet(alphaP,alphaQ,tolerance,false)
  /** Same parameters as dirichletKl; symmetric negative log affinity.
    * @example `LegacyInformation.dirichletBhattacharyya(Vector(2,3),Vector(3,4))` */
  def dirichletBhattacharyya(alphaP: Vector[Double],alphaQ: Vector[Double],tolerance: Double=1e-8): InformationMetricResult=dirichlet(alphaP,alphaQ,tolerance,true)
  /** Positive shape pairs alphaP,betaP and alphaQ,betaQ in [.001,1e6]; positive tolerance in nats.
    * @return beta KL through the two-category Dirichlet identity
    * @example `LegacyInformation.betaKl(2,3,4,5)` */
  def betaKl(alphaP: Double,betaP: Double,alphaQ: Double,betaQ: Double,tolerance: Double=1e-8): InformationMetricResult=dirichletKl(Vector(alphaP,betaP),Vector(alphaQ,betaQ),tolerance)
  /** Same shapes/tolerance as betaKl; symmetric negative log affinity.
    * @example `LegacyInformation.betaBhattacharyya(2,3,4,5)` */
  def betaBhattacharyya(alphaP: Double,betaP: Double,alphaQ: Double,betaQ: Double,tolerance: Double=1e-8): InformationMetricResult=dirichletBhattacharyya(Vector(alphaP,betaP),Vector(alphaQ,betaQ),tolerance)

  private def poisson(p: Double,q: Double,t: Double,bh: Boolean): InformationMetricResult = {
    tolerance(t); require(p.isFinite && q.isFinite && p>=0 && q>=0 && p<=1e8 && q<=1e8)
    if(p==q) return MetricCalculation.identity
    if(!bh && q==0 && p>0) return MetricCalculation.infinite
    val v=if(bh) { val d=math.sqrt(p)-math.sqrt(q); .5*d*d }
      else if(p==0) q else { val r=math.log(p)-math.log(q); p*r+q-p }
    MetricCalculation.analytic(v,t,p+q+math.abs(v),"Poisson normalizer")
  }
  /** Rates in [0,1e8], source then comparison; positive numerical tolerance in nats.
    * @return directed KL, with true support infinity at q=0<p
    * @example `LegacyInformation.poissonKl(2,5)` */
  def poissonKl(rateP: Double,rateQ: Double,tolerance: Double=1e-8): InformationMetricResult=poisson(rateP,rateQ,tolerance,false)
  /** Same rates/tolerance as poissonKl; symmetric negative log affinity.
    * @example `LegacyInformation.poissonBhattacharyya(2,5)` */
  def poissonBhattacharyya(rateP: Double,rateQ: Double,tolerance: Double=1e-8): InformationMetricResult=poisson(rateP,rateQ,tolerance,true)
  private def geometric(p: Double,q: Double,t: Double,bh: Boolean): InformationMetricResult = {
    tolerance(t); require(p.isFinite && q.isFinite && p>=0 && p<1 && q>=0 && q<1)
    if(p==q) return MetricCalculation.identity
    if(!bh && p>0 && q==0) return MetricCalculation.infinite
    val v=if(bh) math.log1p(-math.sqrt(p)*math.sqrt(q))-.5*(math.log1p(-p)+math.log1p(-q))
      else math.log1p(-p)-math.log1p(-q)+(if(p==0) 0 else p/(1-p)*(math.log(p)-math.log(q)))
    MetricCalculation.analytic(v,t,1/(1-p)+1/(1-q)+math.abs(v),"Geometric normalizer")
  }
  /** Failure probabilities in [0,1), trials-until-first-success support 1,2,...; positive tolerance.
    * @return directed KL, or support infinity/numerical refusal
    * @example `LegacyInformation.geometricKl(.2,.6)` */
  def geometricKl(failureP: Double,failureQ: Double,tolerance: Double=1e-8): InformationMetricResult=geometric(failureP,failureQ,tolerance,false)
  /** Same failure probabilities/tolerance as geometricKl; symmetric negative log affinity.
    * @example `LegacyInformation.geometricBhattacharyya(.2,.6)` */
  def geometricBhattacharyya(failureP: Double,failureQ: Double,tolerance: Double=1e-8): InformationMetricResult=geometric(failureP,failureQ,tolerance,true)
  private def binomial(n: Int,p: Double,q: Double,t: Double,bh: Boolean): InformationMetricResult = {
    tolerance(t); require(n>=0); DistributionNumerics.probability(p); DistributionNumerics.probability(q)
    if(n==0) return MetricCalculation.identity
    if(t/n==0) return MetricCalculation.unavailable(InformationMetricStatus.NumericallyUnresolved,0,"binomial tolerance underflow")
    val a=Vector(p,1-p); val b=Vector(q,1-q)
    val r=if(bh) DiscreteInformation.bhattacharyya(a,b,t/n) else DiscreteInformation.kl(a,b,t/n)
    r.copy(value=r.value.map(_*n),errorEstimate=r.errorEstimate*n,method="equal-trial binomial category reduction")
  }
  /** Nonnegative common trials, success probabilities p,q in [0,1], positive tolerance in nats.
    * @return directed equal-trial binomial KL; unequal-trial laws are not accepted by this API
    * @example `LegacyInformation.binomialKl(10,.2,.4)` */
  def binomialKl(trials: Int,successP: Double,successQ: Double,tolerance: Double=1e-8): InformationMetricResult=binomial(trials,successP,successQ,tolerance,false)
  /** Same trials/probabilities/tolerance as binomialKl; symmetric negative log affinity.
    * @example `LegacyInformation.binomialBhattacharyya(10,.2,.4)` */
  def binomialBhattacharyya(trials: Int,successP: Double,successQ: Double,tolerance: Double=1e-8): InformationMetricResult=binomial(trials,successP,successQ,tolerance,true)
}
