/* Numerical evaluation of Horwood-Poore (2014), section 4.7.
 * Integrates the finite-kappa convolution, without replacing its angular part by chi-square.
 */
package com.cra.figaro.library.atomic.continuous

import java.util.concurrent.CancellationException
import org.apache.commons.math3.special.Gamma

/** Numerical probability result; obtain through cdfEstimate/survivalEstimate.
  * @param value probability estimate in [0,1]
  * @param estimatedAbsoluteError quadrature error estimate plus angular truncation bound and roundoff allowance;
  *        not an interval-arithmetic certificate or a statistical confidence interval
  * @param angularTruncationBound bound on omitted angular probability
  * @param evaluations number of integrand evaluations (one gamma evaluation at kappa=0)
  */
final class GaussVonMisesScoreProbability private[continuous] (
  val value: Double, val estimatedAbsoluteError: Double,
  val angularTruncationBound: Double, val evaluations: Int)

/** Immutable null distribution of the squared Mahalanobis-von-Mises score.
  * Construct with the companion factory or kernel.scoreDistribution().
  */
final class GaussVonMisesScoreDistribution private (
  val linearDimension: Int, val kappa: Double, val absoluteTolerance: Double, val maxEvaluations: Int) {
  private val shape = linearDimension / 2.0
  private val angularScale = math.max(1.0, math.sqrt(kappa))
  private val logPrefactor = math.log(2.0) + VonMisesDistribution(0,kappa).logDensity(0) - math.log(angularScale)
  private val fullEnd = math.Pi * angularScale
  private val envelopeRate = 2.0 / (math.Pi * math.Pi)
  private val end = if (kappa <= 1) fullEnd else math.min(fullEnd,
    math.max(3.0, math.sqrt(math.max(0.0,(logPrefactor-math.log(absoluteTolerance/8))/envelopeRate))))
  private val omitted = if (end == fullEnd) 0.0 else
    math.exp(logPrefactor-envelopeRate*end*end-math.log(2*envelopeRate*end))
  private val roundoff = 64.0*math.ulp(1.0)

  private def interrupted(): Unit =
    if (Thread.currentThread().isInterrupted) throw new CancellationException("GVM score calibration interrupted")

  private def chiProbability(score: Double, upper: Boolean): Double = {
    if (score <= 0) { if (upper) 1.0 else 0.0 }
    else {
      val p = if (upper) Gamma.regularizedGammaQ(shape,score/2,1e-14,100000)
        else Gamma.regularizedGammaP(shape,score/2,1e-14,100000)
      if (!p.isFinite || p < 0 || p > 1) throw new ArithmeticException("Invalid chi-square probability")
      p
    }
  }

  /** Cumulative probability P(M <= score) for a draw from the specified fixed GVM law.
    * @param score squared score; negative values and infinities are allowed, NaN is rejected
    * @return probability in [0,1], with numerical rather than statistical error
    * @example `calibration.cdf(5.0)`
    */
  def cdf(score: Double): Double = cdfEstimate(score).value

  /** Direct upper-tail probability P(M > score), avoiding subtraction from one.
    * @param score squared score, not its square root; NaN is rejected
    * @return upper-tail probability in [0,1]; very small probabilities may underflow
    * @example `calibration.survival(kernel.mahalanobisSquared(point))`
    */
  def survival(score: Double): Double = survivalEstimate(score).value

  /** CDF with numerical diagnostics; see cdf for the score contract.
    * @param score squared score, including +/-Infinity but not NaN
    * @return immutable estimate, error estimate, truncation bound and evaluation count
    * @example `calibration.cdfEstimate(5.0).estimatedAbsoluteError`
    */
  def cdfEstimate(score: Double): GaussVonMisesScoreProbability = probability(score,false)

  /** Direct survival probability with numerical diagnostics.
    * @param score squared score, including +/-Infinity but not NaN
    * @return immutable numerical estimate; no chi-square approximation at finite concentration
    * @example `calibration.survivalEstimate(10.0).value`
    */
  def survivalEstimate(score: Double): GaussVonMisesScoreProbability = probability(score,true)

  private def probability(score: Double, upper: Boolean): GaussVonMisesScoreProbability = {
    require(!score.isNaN,"score must not be NaN")
    interrupted()
    if (score <= 0 || score == Double.PositiveInfinity) {
      val lower = if (score <= 0) 0.0 else 1.0
      return new GaussVonMisesScoreProbability(if (upper) 1-lower else lower,0,0,0)
    }
    if (kappa == 0) return new GaussVonMisesScoreProbability(chiProbability(score,upper),roundoff,0,1)
    var evaluations = 0
    def evaluate(f: Double => Double, x: Double): Double = {
      interrupted()
      if (evaluations >= maxEvaluations) throw new ArithmeticException("GVM score integration exhausted maxEvaluations")
      evaluations += 1
      val result = f(x)
      if (!result.isFinite || result < 0) throw new ArithmeticException("Invalid GVM score integrand")
      result
    }
    def integrate(f: Double => Double, a: Double, b: Double, tolerance: Double): (Double,Double) = {
      if (b <= a) return (0,0)
      def refine(left: Double, right: Double, fl: Double, fm: Double, fr: Double,
        old: Double, budget: Double, depth: Int): (Double,Double) = {
        val mid = left+(right-left)/2
        val lm = left+(mid-left)/2; val rm = mid+(right-mid)/2
        val f1 = evaluate(f,lm); val f2 = evaluate(f,rm)
        val s1 = (mid-left)*(fl+4*f1+fm)/6
        val s2 = (right-mid)*(fm+4*f2+fr)/6
        val delta = s1+s2-old; val error = math.abs(delta)/15
        if (error <= budget) (s1+s2+delta/15,error)
        else {
          if (depth == 0 || lm == left || rm == right)
            throw new ArithmeticException("GVM score quadrature did not converge")
          val l = refine(left,mid,fl,f1,fm,s1,budget/2,depth-1)
          val r = refine(mid,right,fm,f2,fr,s2,budget/2,depth-1)
          (l._1+r._1,l._2+r._2)
        }
      }
      // Seed multiple panels so a small initial sample cannot miss a localized integrand.
      var value = 0.0; var error = 0.0
      for (i <- 0 until 16) {
        val left = a+(b-a)*i/16; val right = a+(b-a)*(i+1)/16
        val fl=evaluate(f,left); val fm=evaluate(f,left+(right-left)/2); val fr=evaluate(f,right)
        val part=refine(left,right,fl,fm,fr,(right-left)*(fl+4*fm+fr)/6,tolerance/16,40)
        value += part._1; error += part._2
      }
      (value,error)
    }
    def angularScore(t: Double): Double = {
      val s=math.sin(t/(2*angularScale)); 4*kappa*s*s
    }
    def density(t: Double): Double = math.exp(logPrefactor-angularScore(t)/2)
    // Split at M's support boundary and smooth the chi-square df=1 square-root endpoint.
    val crossing = if (score >= 4*kappa) fullEnd else
      2*angularScale*math.asin(math.sqrt(score/(4*kappa)))
    val cut = math.min(end,crossing)
    val smooth: Double => Double = u => {
      if (u == 0) 0.0
      else {
        val t=cut*(1-u*u)
        2*cut*u*density(t)*chiProbability(math.max(0.0,score-angularScore(t)),upper)
      }
    }
    val first=integrate(smooth,0,1,absoluteTolerance/4)
    val rest=if (upper && cut < end) integrate(density,cut,end,absoluteTolerance/4) else (0.0,0.0)
    val value=first._1+rest._1
    val error=first._2+rest._2+omitted+roundoff
    if (error > absoluteTolerance || value < -error || value > 1+error)
      throw new ArithmeticException("GVM score probability failed numerical checks")
    new GaussVonMisesScoreProbability(math.max(0,math.min(1,value)),error,omitted,evaluations)
  }

  /** Squared-score threshold containing the requested modeled probability mass.
    * @param probability in [0,1]; interior smaller tail must be >=100*absoluteTolerance
    * @return threshold; 0 at probability 0, +Infinity at 1; not a parameter confidence interval
    * @example `calibration.quantile(0.95)`
    */
  def quantile(probability: Double): Double = inverse(probability,false)

  /** Squared-score threshold exceeded with the specified modeled upper-tail probability.
    * @param tailProbability in [0,1]; interior smaller tail must be >=100*absoluteTolerance
    * @return threshold; +Infinity at tail 0 and zero at tail 1
    * @example `calibration.inverseSurvival(0.05)`
    */
  def inverseSurvival(tailProbability: Double): Double = inverse(tailProbability,true)

  private def inverse(requested: Double, isUpper: Boolean): Double = {
    require(requested.isFinite && requested >= 0 && requested <= 1,"probability must be in [0,1]")
    interrupted()
    if (requested == 0) return if (isUpper) Double.PositiveInfinity else 0.0
    if (requested == 1) return if (isUpper) 0.0 else Double.PositiveInfinity
    require(math.min(requested,1-requested) >= 100*absoluteTolerance,
      "requested tail is too small for absoluteTolerance; tighten tolerance within the supported range")
    val upper=if (isUpper) requested <= 0.5 else requested > 0.5
    val target=if (upper == isUpper) requested else 1-requested
    def below(value: Double): Boolean = if (upper) value > target else value < target
    var lo=0.0; var hi=linearDimension+10*math.sqrt(2.0*linearDimension)+20
    var count=0
    while (below(probability(hi,upper).value)) {
      if (count >= 64) throw new ArithmeticException("GVM score quantile could not bracket the target")
      hi *= 2; count += 1
    }
    var iteration=0
    while (iteration < 96) {
      interrupted()
      val mid=lo+(hi-lo)/2
      val p=probability(mid,upper).value
      if (math.abs(p-target) <= absoluteTolerance || hi-lo <= 1e-10*math.max(1.0,mid)) return mid
      if (below(p)) lo=mid else hi=mid
      iteration += 1
    }
    throw new ArithmeticException("GVM score quantile did not converge")
  }
}

object GaussVonMisesScoreDistribution {
  /** Build reusable finite-concentration score calibration, independent of means/coupling.
    * @param linearDimension number of Gaussian coordinates, in [1,10000]
    * @param kappa concentration in [0,1e8]
    * @param absoluteTolerance probability integration tolerance in [1e-12,1e-4], default 1e-10
    * @param maxEvaluations integrand budget per probability call in [32,1000000], default 100000
    * @return immutable calibrator; invalid arguments throw IllegalArgumentException
    * @example `GaussVonMisesScoreDistribution(2, 4.5).quantile(0.95)`
    */
  def apply(linearDimension: Int, kappa: Double, absoluteTolerance: Double = 1e-10,
    maxEvaluations: Int = 100000): GaussVonMisesScoreDistribution = {
    require(linearDimension >= 1 && linearDimension <= 10000,"linearDimension must be in [1,10000]")
    require(kappa.isFinite && kappa >= 0 && kappa <= VonMisesDistribution.MaxKappa,"kappa must be in [0,1e8]")
    require(absoluteTolerance.isFinite && absoluteTolerance >= 1e-12 && absoluteTolerance <= 1e-4,
      "absoluteTolerance must be in [1e-12,1e-4]")
    require(maxEvaluations >= 32 && maxEvaluations <= 1000000,"maxEvaluations must be in [32,1000000]")
    new GaussVonMisesScoreDistribution(linearDimension,kappa,absoluteTolerance,maxEvaluations)
  }
}
