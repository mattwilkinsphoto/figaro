package com.cra.figaro.library.atomic.continuous

import java.util.concurrent.CancellationException
import org.apache.commons.math3.linear.{Array2DRowRealMatrix, EigenDecomposition}

/** Dependence between the complete linear vector and angle of ONE fixed GVM law.
  * Uses analytic conditional entropy and one-dimensional marginal integration.
  * Numerical intervals are estimates, not certified error enclosures. Units: nats.
  */
object GaussVonMisesMutualInformation {
  /** Only Estimated exposes a value; it does not certify requested accuracy. */
  enum Status { case Estimated, BudgetExhausted, NumericallyUnresolved, UnsupportedRange }
  import Status.*

  /** Immutable per-call outcome; unavailable diagnostics use positive infinity.
    * @param status estimated success, work exhaustion, numerical refusal, or unsupported parameters
    * @param value mutual information in nats, absent unless status is Estimated
    * @param interval estimated nonnegative interval in nats, absent before integration
    * @param upperBoundEstimate angular-uniform entropy ceiling with floating-point error; zero for exact independence
    * @param harmonics Fourier coefficients retained; zero for exact independence
    * @param evaluations angular density evaluations across all grids (not coefficient setup)
    * @param truncationErrorEstimate entropy allowance from the analytic Fourier tail
    * @param quadratureErrorEstimate heuristic successive-grid allowance in nats
    * @param roundoffEstimate heuristic preprocessing/evaluation allowance in nats
    * @param method exact-independence or fourier-angular-entropy
    */
  final case class Result(status: Status, value: Option[Double], interval: Option[(Double,Double)],
    upperBoundEstimate: Double, harmonics: Int, evaluations: Int, truncationErrorEstimate: Double,
    quadratureErrorEstimate: Double, roundoffEstimate: Double, method: String="fourier-angular-entropy")

  private class NumericalFailure extends RuntimeException

  /** Estimate I(X;theta), not a distance between two distributions.
    * Invalid arguments throw IllegalArgumentException. Cancellation throws CancellationException;
    * caller predicate exceptions propagate and thread interruption is not cleared.
    * No random numbers or shared mutable state are used.
    * @param kernel non-null fixed GVM; numerical path supports dimension <=32, kappa <=50,
    *   and absolute canonical beta/Gamma entries <=1000; exact independence bypasses these caps
    * @param tolerance positive finite absolute error target in nats, default 1e-8; not a certificate
    * @param maxHarmonics coefficient budget in [1,256], default 128
    * @param maxEvaluations total angular evaluations in [1,131072], default 16384;
    *   excludes bounded eigendecomposition/Bessel setup and is not a time limit
    * @param cancelled non-null cooperative cancellation predicate, default always false
    * @return immutable diagnostics; no value on failure, and no exact-zero shortcut for weak coupling
    * @example `GaussVonMisesMutualInformation.compute(kernel, tolerance = 1e-7)`
    */
  def compute(kernel: GaussVonMisesDistribution, tolerance: Double=1e-8,
    maxHarmonics: Int=128, maxEvaluations: Int=16384, cancelled: () => Boolean=() => false): Result = {
    require(kernel != null,"non-null GVM required")
    require(tolerance.isFinite && tolerance > 0,"positive finite tolerance required")
    require(maxHarmonics >= 1 && maxHarmonics <= 256,"harmonic budget must be in [1,256]")
    require(maxEvaluations >= 1 && maxEvaluations <= 131072,"evaluation budget must be in [1,131072]")
    require(cancelled != null,"non-null cancellation predicate required")
    def interrupted(): Unit =
      if(Thread.currentThread().isInterrupted || cancelled()) throw new CancellationException("GVM mutual information interrupted")
    var harmonics=0; var evaluations=0
    var upper=Double.PositiveInfinity; var tail=Double.PositiveInfinity
    var roundoff=Double.PositiveInfinity; var quadrature=Double.PositiveInfinity
    def unavailable(status: Status): Result =
      Result(status,None,None,upper,harmonics,evaluations,tail,quadrature,roundoff)
    def finite(x: Double): Double = { if(!x.isFinite) throw new NumericalFailure; x }
    interrupted()
    if(kernel.kappa == 0 || (kernel.beta.forall(_ == 0) && kernel.gamma.forall(_.forall(_ == 0))))
      return Result(Estimated,Some(0),Some((0,0)),0,0,0,0,0,0,"exact-independence")
    if(kernel.dimension > 32 || kernel.kappa > 50 ||
      kernel.beta.exists(x => math.abs(x) > 1000) || kernel.gamma.exists(_.exists(x => math.abs(x) > 1000)))
      return unavailable(UnsupportedRange)
    try {
      val k=kernel.kappa; val n=kernel.dimension; val eps=math.ulp(1.0)
      // Positive defining series, not unstable upward Bessel recurrence.
      def bessel(order: Int): Double = {
        var leading=1.0; var i=1
        while(i <= order) { leading *= (k/2)/i; i += 1 }
        var term=1.0; var extra=0.0; var j=1
        while(j <= 1000) {
          interrupted()
          term *= (k/2)*(k/2)/(j.toDouble*(j+order)); extra += term
          if(term <= (1+extra)*1e-17) return leading*(1+extra)
          j += 1
        }
        throw new NumericalFailure
      }
      // log1p preserves the small-concentration I0-1 contribution.
      var term=1.0; var extra=0.0; var j=1
      while(j <= 1000 && (j == 1 || term > (1+extra)*1e-17)) {
        interrupted(); term *= (k/2)*(k/2)/(j.toDouble*j); extra += term; j += 1
      }
      val i0=1+extra; val logI0=math.log1p(extra)
      upper=finite(k*bessel(1)/i0-logI0)
      val upperError=128*eps*(1+k)
      if(upper < -upperError) return unavailable(NumericallyUnresolved)
      upper=math.max(0,upper)
      val maximum=math.exp(k-logI0)
      // Continuity of x log(x), including zero: split at 1 and integrate |1+log(x)|.
      // Applies to a uniform density perturbation d <= 1 on [0,maximum+d].
      def entropyAllowance(d: Double): Double =
        if(d == 0) 0 else if(d > 1 || !d.isFinite) Double.PositiveInfinity
        else d*(2-math.log(d)+math.log(math.max(1,maximum+d)))
      val eigen = try { new EigenDecomposition(new Array2DRowRealMatrix(kernel.gamma.map(_.toArray).toArray,false)) }
        catch { case _: org.apache.commons.math3.exception.MathIllegalStateException => return unavailable(NumericallyUnresolved) }
      interrupted()
      val lambda=Vector.tabulate(n)(i => finite(eigen.getRealEigenvalue(i)))
      val projected=Vector.tabulate(n)(i => finite(kernel.beta.indices.map(r => eigen.getV.getEntry(r,i)*kernel.beta(r)).sum))
      val coefficients=scala.collection.mutable.ArrayBuffer.empty[(Double,Double)]
      var densityRoundoff=32*eps*(1+n); var densityTail=Double.PositiveInfinity
      while(harmonics < maxHarmonics && tail > tolerance/8) {
        interrupted(); harmonics += 1
        val h=harmonics.toDouble
        var logMagnitude=0.0; var phase=0.0; var sensitivity=1.0+n
        for(i <- 0 until n) {
          val l=h*lambda(i); val scale=math.hypot(1,l); val b=h*projected(i)/scale
          logMagnitude -= .5*math.log(scale)+.5*b*b
          phase += .5*math.atan(l)-.5*b*b*l
          sensitivity += math.abs(l)+h*h*projected(i)*projected(i)
        }
        val amplitude=finite(bessel(harmonics)/i0*math.exp(logMagnitude))
        coefficients += ((amplitude*math.cos(phase),amplitude*math.sin(phase)))
        // Heuristic, operand-sensitive coefficient and Fourier-evaluation allowance.
        densityRoundoff += 64*eps*amplitude*(sensitivity+h)
        val ratio=k/(2*(harmonics+2))
        densityTail=if(ratio < 1) 2*bessel(harmonics+1)/i0/(1-ratio) else Double.PositiveInfinity
        tail=entropyAllowance(densityTail)
      }
      roundoff=finite(entropyAllowance(densityRoundoff)+upperError)
      if(tail > tolerance/8) return unavailable(BudgetExhausted)
      if(roundoff+tail >= tolerance) return unavailable(NumericallyUnresolved)
      def grid(size: Int): Double = {
        var total=0.0; var compensation=0.0; var index=0
        while(index < size) {
          interrupted()
          val theta=2*math.Pi*index/size
          var density=1.0; var correction=0.0; var order=0
          while(order < coefficients.size) {
            val (re,im)=coefficients(order); val angle=(order+1)*theta
            val add=2*(re*math.cos(angle)+im*math.sin(angle))-correction
            val next=density+add; correction=(next-density)-add; density=next
            order += 1
          }
          evaluations += 1
          if(density < -(densityTail+densityRoundoff)) throw new NumericalFailure
          // A tiny negative Fourier reconstruction is clipped only within the reported allowance.
          val positive=math.max(0,density)
          val entropy=if(positive == 0) 0.0 else positive*math.log(positive)
          val add=entropy-compensation; val next=total+add
          compensation=(next-total)-add; total=next; index += 1
        }
        finite(total/size)
      }
      var size=64
      while(size < 4*(harmonics+1)) size *= 2
      var previous=0.0; var grids=0; var previousDifference=Double.PositiveInfinity
      while(size <= maxEvaluations-evaluations) {
        val deficit=grid(size)
        val difference=if(grids == 0) Double.PositiveInfinity else math.abs(deficit-previous)
        quadrature=4*math.max(difference,previousDifference)
        val error=tail+roundoff+quadrature
        if(error.isFinite && error <= tolerance) {
          val raw=finite(upper-deficit)
          if(raw < -error || raw > upper+error) return unavailable(NumericallyUnresolved)
          val value=math.max(0,math.min(upper,raw))
          val interval=(math.max(0,raw-error),math.min(upper+upperError,raw+error))
          return Result(Estimated,Some(value),Some(interval),upper,harmonics,evaluations,tail,quadrature,roundoff)
        }
        previous=deficit; previousDifference=difference; grids += 1; size *= 2
      }
      unavailable(BudgetExhausted)
    } catch { case _: NumericalFailure => unavailable(NumericallyUnresolved) }
  }
}
