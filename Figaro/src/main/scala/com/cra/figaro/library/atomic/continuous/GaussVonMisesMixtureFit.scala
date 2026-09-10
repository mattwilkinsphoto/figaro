package com.cra.figaro.library.atomic.continuous

import com.cra.figaro.util.{CircularStatistics,SamplingRandom}
import com.cra.figaro.library.atomic.DistributionNumerics as N
import org.apache.commons.math3.analysis.MultivariateFunction
import org.apache.commons.math3.optim.{InitialGuess,MaxEval}
import org.apache.commons.math3.optim.nonlinear.scalar.{GoalType,ObjectiveFunction}
import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.PowellOptimizer
import org.apache.commons.math3.linear.{Array2DRowRealMatrix,ArrayRealVector,QRDecomposition}

/** Bounded, local maximum-likelihood fitting for mixtures on R x S1.
  * No phase-unwrapping likelihood, automatic component selection, global-optimum claim,
  * graph mutation, or adaptation during inference. Freeze the result before production.
  * Higher-dimensional parameter fitting is deliberately not part of this first contract.
  */
object GaussVonMisesMixtureFit {
  enum Status { case Converged, IterationLimit, AngularBudgetExhausted, DegenerateComponent, Stalled, NumericallyUnresolved }
  /** Work and regularization policy. Floors/caps define a constrained fit, not an unregularized MLE.
    * @param components explicit number of components, 1..16
    * @param restarts independent initial partitions, 1..8, all counted
    * @param maxIterations EM updates per restart, 1..200
    * @param maxAngularEvaluations objective calls per component/start, 8..2000
    * @param relativeTolerance per-observation log-likelihood convergence tolerance, (0,0.01]
    * @param varianceFloor positive minimum linear variance in physical units, [1e-12,1e12]
    * @param maxConcentration finite concentration cap, [0.1,500]
    * @param minComponentEffectiveSamples minimum responsibility ESS AND total mass, >=3
    * @param seed deterministic initialization seed, using Figaro's scientific RNG
    */
  final case class Config(components: Int=1,restarts: Int=3,maxIterations: Int=40,
    maxAngularEvaluations: Int=200,relativeTolerance: Double=1e-7,varianceFloor: Double=1e-6,
    maxConcentration: Double=100,minComponentEffectiveSamples: Double=10,seed: Long=42) {
    require(components>=1 && components<=16 && restarts>=1 && restarts<=8)
    require(maxIterations>=1 && maxIterations<=200 && maxAngularEvaluations>=8 && maxAngularEvaluations<=2000)
    require(relativeTolerance.isFinite && relativeTolerance>0 && relativeTolerance<=.01)
    require(varianceFloor.isFinite && varianceFloor>=1e-12 && varianceFloor<=1e12)
    require(maxConcentration.isFinite && maxConcentration>=.1 && maxConcentration<=500)
    require(minComponentEffectiveSamples.isFinite && minComponentEffectiveSamples>=3)
  }
  /** A restart's accepted mean training-log-likelihood trace (including initialization).
    * @param status termination, never a global optimality certificate
    * @param logLikelihoodTrace accepted mean log likelihoods in nats per observation
    * @param angularEvaluations circular-regression objective calls, including initialization
    * @param message explicit termination explanation
    */
  final case class Attempt(status: Status,logLikelihoodTrace: Vector[Double],angularEvaluations: Long,message: String)
  /** Immutable selected fit and ALL restart outcomes.
    * @param distribution best finite monotone fit, absent when every restart is refused
    * @param selectedAttempt index into attempts, absent without a fit
    * @param attempts every restart, including failed and budget-limited attempts
    * @param config exact reproducibility/work settings
    */
  final case class Result(distribution: Option[GaussVonMisesMixtureDistribution],selectedAttempt: Option[Int],
    attempts: Vector[Attempt],config: Config)

  private class Degenerate extends RuntimeException
  private def kernel(mu: Double,variance: Double,a: Double,b: Double,g: Double,k: Double)=
    GaussVonMisesDistribution(Vector(mu),Vector(Vector(variance)),a,Vector(b),Vector(Vector(g)),k)

  /** Fit supplied IID training observations; use independent data for quality assessment.
    * @param data 30..100000 finite observations, each with exactly one linear coordinate,
    *        absolute linear magnitude <=1e50; fixed component counts require enough data
    * @param config bounded fitting settings; initialization and optimizer costs included
    * @return best locally fitted immutable law plus explicit per-restart diagnostics
    * @example `GaussVonMisesMixtureFit.fit(points, GaussVonMisesMixtureFit.Config(components=2))`
    * Invalid data/config throws; interruption propagates without clearing the interrupt flag.
    */
  def fit(data: Vector[LinearAngular],config: Config=Config()): Result = {
    require(data!=null && config!=null && data.size>=30 && data.size<=100000)
    require(data.forall(p=>p!=null && p.linear.size==1 && math.abs(p.linear.head)<=1e50))
    require(data.size>=config.components*config.minComponentEffectiveSamples)
    N.check()
    val x=data.map(_.linear.head); val angles=data.map(p=>CircularStatistics.normalize(p.angle))
    val rng=SamplingRandom.scalaRandom(config.seed)
    var selected: Option[GaussVonMisesMixtureDistribution]=None
    var selectedIndex: Option[Int]=None; var bestScore=Double.NegativeInfinity
    val attempts=Vector.newBuilder[Attempt]
    for(restart<-0 until config.restarts) {
      var evaluations=0L; var budgetHit=false
      var trace=Vector.empty[Double]; var current: Option[GaussVonMisesMixtureDistribution]=None
      var status=Status.IterationLimit; var message="EM iteration budget reached"
      def fitComponent(weights: Vector[Double],previous: Option[GaussVonMisesDistribution]): GaussVonMisesDistribution = {
        N.check(); val mass=weights.sum; val ess=mass*mass/weights.map(w=>w*w).sum
        if(!mass.isFinite || mass<config.minComponentEffectiveSamples || ess<config.minComponentEffectiveSamples) throw new Degenerate
        val mean=x.indices.map(i=>weights(i)*x(i)).sum/mass
        val variance=math.max(config.varianceFloor,x.indices.map(i=>weights(i)*math.pow(x(i)-mean,2)).sum/mass)
        val sd=math.sqrt(variance); val z=x.map(v=>(v-mean)/sd)
        var bestR= -1.0; var bestA=0.0; var bestB=0.0; var bestG=0.0
        def objective(v: Array[Double]): Double = {
          N.check(); evaluations+=1
          if(v.length!=2 || !v.forall(_.isFinite) || v.exists(math.abs(_)>1000)) return -1.0
          var c=0.0; var s=0.0
          for(i<-x.indices) {
            if((i & 255)==0) N.check()
            val phase=angles(i)-v(0)*z(i)-.5*v(1)*z(i)*z(i)
            c+=weights(i)*math.cos(phase); s+=weights(i)*math.sin(phase)
          }
          val r=math.hypot(c,s)/mass
          if(!r.isFinite || r>1+1e-12) throw new ArithmeticException("Circular resultant unresolved")
          if(r>bestR) { bestR=r; bestA=math.atan2(s,c); bestB=v(0); bestG=v(1) }
          r
        }
        val starts=Vector.newBuilder[Array[Double]]
        starts+=Array(0.0,0.0)
        previous.foreach { old=>
          val a=(mean-old.mean.head)/math.sqrt(old.covariance.head.head)
          val b=sd/math.sqrt(old.covariance.head.head)
          starts+=Array(b*(old.beta.head+old.gamma.head.head*a),old.gamma.head.head*b*b)
        }
        // Unwrapping provides only a candidate start. Every accepted update is scored
        // by the periodic von Mises likelihood, including across the angular seam.
        if(previous.isEmpty) {
          val order=x.indices.filter(weights(_)>0).sortBy(x(_))
          var last=angles(order.head)
          val phase=order.map { i=>last+=CircularStatistics.normalize(angles(i)-last); last }
          val design=order.map(i=> { val w=math.sqrt(weights(i)); Array(w,w*z(i),w*.5*z(i)*z(i)) }).toArray
          val response=order.indices.map(j=>phase(j)*math.sqrt(weights(order(j)))).toArray
          val solver=new QRDecomposition(new Array2DRowRealMatrix(design,false),1e-10).getSolver
          if(solver.isNonSingular) {
            val v=solver.solve(new ArrayRealVector(response,false)).toArray
            if(v.forall(_.isFinite)) starts+=Array(v(1),v(2))
          }
        }
        starts.result().foreach { start=>
          objective(start)
          try {
            new PowellOptimizer(1e-8,1e-10).optimize(new MaxEval(config.maxAngularEvaluations),
              new ObjectiveFunction(new MultivariateFunction { def value(v: Array[Double]): Double=objective(v) }),
              GoalType.MAXIMIZE,new InitialGuess(start))
          } catch { case _: org.apache.commons.math3.exception.TooManyEvaluationsException=>budgetHit=true }
        }
        if(bestR<0) throw new ArithmeticException("No finite circular fit")
        var lo=0.0; var hi=config.maxConcentration
        for(i<-0 until 60) { N.check(); val mid=.5*(lo+hi)
          if(VonMisesDistribution(0,mid).meanResultantLength<bestR) lo=mid else hi=mid
        }
        kernel(mean,variance,bestA,bestB,bestG,.5*(lo+hi))
      }
      def score(law: GaussVonMisesMixtureDistribution): Double = {
        val v=data.map { p=>N.check(); law.logDensity(p) }.sum/data.size
        if(!v.isFinite) throw new ArithmeticException("Mixture training score unresolved")
        v
      }
      try {
        val order=if(restart==0) x.indices.sortBy(x(_)).toVector
          else if(restart==1) x.indices.sortBy(i=>angles(i)).toVector
          else rng.shuffle(x.indices.toVector)
        val groups=Vector.tabulate(config.components)(k=>order.slice(k*data.size/config.components,(k+1)*data.size/config.components).toSet)
        val init=groups.map(group=>fitComponent(x.indices.map(i=>if(group(i)) 1.0 else 0.0).toVector,None))
        var law=GaussVonMisesMixtureDistribution(groups.map(_.size.toDouble/data.size),init)
        current=Some(law); trace=Vector(score(law)); var iteration=0; var done=false
        while(iteration<config.maxIterations && !done) {
          N.check()
          val responsibilities=data.map(law.responsibilities)
          val masses=Vector.tabulate(config.components)(k=>responsibilities.map(_(k)).sum)
          val next=GaussVonMisesMixtureDistribution(masses.map(_/masses.sum),
            Vector.tabulate(config.components)(k=>fitComponent(responsibilities.map(_(k)),Some(law.components(k)))))
          val nextScore=score(next); val gain=nextScore-trace.last
          if(gain<0) { status=Status.Stalled; message="Likelihood-decreasing update refused"; done=true }
          else {
            law=next; current=Some(law); trace=trace:+nextScore
            if(gain<=config.relativeTolerance*(1+math.abs(nextScore))) {
              status=Status.Converged; message="Local mean-likelihood tolerance reached"; done=true
            }
          }
          iteration+=1
        }
        if(budgetHit) { status=Status.AngularBudgetExhausted; message="A circular optimizer reached its evaluation budget; retained monotone candidate, not convergence" }
      } catch {
        case _: Degenerate=>status=Status.DegenerateComponent; message="Component mass/effective sample floor violated"; current=None
        case _: ArithmeticException=>status=Status.NumericallyUnresolved; message="Finite arithmetic could not resolve this fit"; current=None
        case _: org.apache.commons.math3.exception.MathIllegalArgumentException=>status=Status.NumericallyUnresolved; message="Numerical solver refused this fit"; current=None
      }
      if(current.nonEmpty && trace.nonEmpty && trace.last>bestScore) { selected=current; selectedIndex=Some(restart); bestScore=trace.last }
      attempts+=Attempt(status,trace,evaluations,message)
    }
    Result(selected,selectedIndex,attempts.result(),config)
  }
}
