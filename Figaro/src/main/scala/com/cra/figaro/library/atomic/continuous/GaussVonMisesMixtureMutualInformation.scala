package com.cra.figaro.library.atomic.continuous

import com.cra.figaro.util.SamplingRandom
import com.cra.figaro.library.atomic.DistributionNumerics as N
import org.apache.commons.math3.linear.{Array2DRowRealMatrix,EigenDecomposition}

/** Full-mixture I(linear vector; angle), NOT component-label MI or an average of
  * component information. Exact Gaussian quadratic characteristic functions give
  * the angular marginal; IID joint draws estimate the log density ratio.
  */
object GaussVonMisesMixtureMutualInformation {
  enum Status { case Estimated, UnsupportedRange, NumericallyUnresolved }
  /** Fixed work and RNG policy.
    * @param draws IID samples, 2..1000000
    * @param seed scientific RNG seed
    * @param harmonics fixed Fourier budget, 8..256
    * @param maxLogDensityError permitted per-draw marginal-log error estimate, (0,0.01]
    */
  final case class Config(draws: Int=10000,seed: Long=42,harmonics: Int=128,maxLogDensityError: Double=1e-7) {
    require(draws>=2 && draws<=1000000 && harmonics>=8 && harmonics<=256)
    require(maxLogDensityError.isFinite && maxLogDensityError>0 && maxLogDensityError<=.01)
  }
  /** @param status estimated success or explicit refusal
    * @param value signed MI estimate in nats, absent on refusal
    * @param mcse plug-in IID Monte Carlo standard error, NOT coverage certification
    * @param marginalLogErrorEstimate mean numerical log-marginal error allowance on the draws;
    *        analytic series-tail bound plus heuristic roundoff, separate from MCSE
    * @param completedDraws completed IID draws (zero for analytic independence)
    * @param config reproducibility and work policy
    */
  final case class Result(status: Status,value: Option[Double],mcse: Option[Double],
    marginalLogErrorEstimate: Double,completedDraws: Int,config: Config)
  /** @param law immutable joint mixture, 1..32 linear coordinates; active components require
    *        kappa<=50 and canonical coupling magnitudes<=1000 on the numerical path
    * @param config fixed sample/Fourier budget, not an automatic stopping criterion
    * @return full-mixture partition MI estimate and separate sampling/numerical diagnostics
    * @example `GaussVonMisesMixtureMutualInformation.compute(law)`
    * Invalid arguments throw; cancellation propagates; unsupported/numerical cases expose no value.
    */
  def compute(law: GaussVonMisesMixtureDistribution,config: Config=Config()): Result = {
    require(law!=null && config!=null); N.check()
    val active=law.weights.indices.filter(law.weights(_)>0).toVector
    val first=law.components(active.head)
    val independent=active.forall { j=> val g=law.components(j)
      g.kappa==0 || (g.beta.forall(_==0) && g.gamma.flatten.forall(_==0))
    } && active.forall { j=>val g=law.components(j)
      g.kappa==first.kappa && (g.kappa==0 || com.cra.figaro.util.CircularStatistics.difference(g.alpha,first.alpha)==0)
    }
    if(independent) return Result(Status.Estimated,Some(0),Some(0),0,0,config)
    def refused(status: Status,n: Int=0)=Result(status,None,None,Double.PositiveInfinity,n,config)
    if(active.exists { j=>val g=law.components(j); g.kappa>50 || g.beta.exists(math.abs(_)>1000) || g.gamma.flatten.exists(math.abs(_)>1000) })
      return refused(Status.UnsupportedRange)
    val re=Array.fill(config.harmonics)(0.0); val im=Array.fill(config.harmonics)(0.0)
    var error=64*math.ulp(1.0)+math.abs(law.weights.sum-1); var completed=0
    try {
      active.foreach { j=>
        N.check(); val g=law.components(j); val k=g.kappa
        def bessel(order: Int): Double = {
          var leading=1.0; for(i<-1 to order) leading*=k/(2*i)
          var term=1.0; var sum=1.0; var m=1
          while(m<=1000 && (m==1 || term>sum*1e-17)) { N.check(); term*=k*k/(4*m.toDouble*(m+order)); sum+=term; m+=1 }
          if(m>1000) throw new ArithmeticException("Bessel series budget")
          leading*sum
        }
        val i0=bessel(0)
        val eigen=new EigenDecomposition(new Array2DRowRealMatrix(g.gamma.map(_.toArray).toArray,false))
        val lambda=eigen.getRealEigenvalues
        val beta=Vector.tabulate(g.dimension)(i=>g.beta.indices.map(r=>eigen.getV.getEntry(r,i)*g.beta(r)).sum)
        for(h<-1 to config.harmonics) {
          N.check(); var logMagnitude=0.0; var phase=h*g.alpha; var sensitivity=1.0+g.dimension
          for(i<-0 until g.dimension) {
            val l=h*lambda(i); val scale=math.hypot(1,l); val b=h*beta(i)/scale
            logMagnitude-=.5*math.log(scale)+.5*b*b
            phase+=.5*math.atan(l)-.5*b*b*l
            sensitivity+=math.abs(l)+h.toDouble*h*beta(i)*beta(i)
          }
          val amplitude=law.weights(j)*bessel(h)/i0*math.exp(logMagnitude)
          re(h-1)+=amplitude*math.cos(phase); im(h-1)+=amplitude*math.sin(phase)
          error+=128*math.ulp(1.0)*amplitude*(sensitivity+math.abs(phase)+h)
        }
        val ratio=k/(2*(config.harmonics+2))
        if(ratio>=1) throw new ArithmeticException("Fourier tail unresolved")
        error+=law.weights(j)*2*bessel(config.harmonics+1)/i0/(1-ratio)
      }
      if(!error.isFinite) return refused(Status.NumericallyUnresolved)
      val rng=SamplingRandom.scalaRandom(config.seed); val linear=law.linearMarginal
      var mean=0.0; var m2=0.0; var errorSum=0.0
      for(i<-1 to config.draws) {
        N.check(); val point=law.sample(rng)
        var density=1.0; var compensation=0.0
        for(h<-1 to config.harmonics) {
          val term=2*(re(h-1)*math.cos(h*point.angle)+im(h-1)*math.sin(h*point.angle))-compensation
          val next=density+term; compensation=(next-density)-term; density=next
        }
        if(!density.isFinite || density<=error) throw new ArithmeticException("Angular marginal unresolved")
        val allowance= -math.log1p(-error/density)
        if(allowance>config.maxLogDensityError) throw new ArithmeticException("Angular marginal precision exhausted")
        val value=law.logDensity(point)-linear.logDensity(point.linear)-math.log(density/(2*math.Pi))
        if(!value.isFinite) throw new ArithmeticException("Information ratio unresolved")
        val delta=value-mean; mean+=delta/i; m2+=delta*(value-mean); errorSum+=allowance; completed=i
      }
      val se=math.sqrt(math.max(0,m2)/(config.draws-1)/config.draws)
      if(!mean.isFinite || !se.isFinite) return refused(Status.NumericallyUnresolved,completed)
      Result(Status.Estimated,Some(mean),Some(se),errorSum/config.draws,completed,config)
    } catch {
      case _: ArithmeticException=>refused(Status.NumericallyUnresolved,completed)
      case _: org.apache.commons.math3.exception.MathIllegalStateException=>refused(Status.NumericallyUnresolved,completed)
    }
  }
}
