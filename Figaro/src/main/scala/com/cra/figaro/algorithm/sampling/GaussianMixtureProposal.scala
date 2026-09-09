package com.cra.figaro.algorithm.sampling

import com.cra.figaro.library.atomic.continuous.MultivariateGaussianDistribution as G

/** Deterministic, bounded pilot-only Gaussian mixture fitting. No production adaptation. */
object GaussianMixtureProposal {
  /** Fit a normalized weighted empirical pilot law using regularized EM.
    * @param points discarded finite vectors of dimension 1..32
    * @param logWeights matching log masses; -Infinity ignores a point; at least one positive mass
    * @param config explicit components/work policy; minComponentDraws means component ESS here
    * @return numerical fit or refusal; arbitrary additive shifts of logWeights do not change the fit
    * @example `fitWeighted(points,logWeights,Config(components=2))`
    */
  def fitWeighted(points: Vector[Vector[Double]],logWeights: Vector[Double],config: Config=Config()): Result = {
    def check(): Unit=ParetoTail.interrupted()
    check(); require(config!=null && points!=null && logWeights!=null && points.nonEmpty && points.size==logWeights.size)
    require(points.forall(_!=null))
    val d=points.head.size; val n=points.size; val k=config.components
    require(d>=1 && d<=32 && points.forall(x => x!=null && x.size==d && x.forall(_.isFinite)))
    require(n.toLong*(d+k+1)<=config.maxStoredValues,"Weighted pilot storage cap exceeded")
    require(logWeights.forall(x => x.isFinite || x==Double.NegativeInfinity) && logWeights.exists(_.isFinite))
    require(config.diagonalRidge.isEmpty || config.diagonalRidge.size==d)
    var iterations=0; var evaluations=0L; var history=Vector.empty[Double]
    def refuse(s: Status,m: String)=Result(s,None,iterations,evaluations,history,config,m)
    val peak=logWeights.max; val raw=logWeights.map(x => math.exp(x-peak)); val total=raw.sum
    val u=raw.map(_/total); val ess=1/u.map(x => x*x).sum
    if(ess<k*config.minComponentDraws) return refuse(Status.InsufficientPilot,"Weighted pilot ESS below requested component information")
    def moments(w: Vector[Double]): G = {
      check(); val mass=w.sum
      val m=Vector.tabulate(d)(a => points.indices.iterator.filter(w(_)>0).map(i => (w(i)/mass)*points(i)(a)).sum)
      val c=Vector.tabulate(d,d) { (a,b) =>
        val x=math.min(a,b); val y=math.max(a,b)
        points.indices.iterator.filter(w(_)>0).map(i => (w(i)/mass)*(points(i)(x)-m(x))*(points(i)(y)-m(y))).sum+
          (if(a==b && config.diagonalRidge.nonEmpty) config.diagonalRidge(a) else 0)
      }
      G(m,c)
    }
    try {
      val global=moments(u)
      def distance(x: Vector[Double],y: Vector[Double]): Double=
        x.indices.map(a => math.pow((x(a)-y(a))/math.sqrt(global.covariance(a)(a)),2)).sum
      val active=points.indices.filter(u(_)>0).toVector
      var centers=Vector(active.maxBy(i => u(i)*distance(points(i),global.mean)))
      while(centers.size<k) {
        check(); val next=active.maxBy(i => u(i)*centers.map(j => distance(points(i),points(j))).min)
        if(centers.exists(j => points(j)==points(next))) return refuse(Status.DegeneratePilot,"Insufficient distinct weighted centers")
        centers :+= next
      }
      var laws=centers.map(i => G(points(i),global.covariance)); var weights=Vector.fill(k)(1.0/k)
      val r=Array.ofDim[Double](n,k)
      while(iterations<config.maxIterations) {
        check()
        if(n.toLong*k>config.maxDensityEvaluations-evaluations) return refuse(Status.EvaluationLimit,"Weighted EM density budget exhausted")
        var objective=0.0
        for(i <- points.indices) {
          check()
          if(u(i)>0) {
            val logs=Vector.tabulate(k)(j => { evaluations+=1; math.log(weights(j))+laws(j).logDensity(points(i)) })
            val m=logs.max; val shifted=logs.map(x => math.exp(x-m)); val sum=shifted.sum
            val lm=m+math.log(sum); require(lm.isFinite)
            objective+=u(i)*lm
            for(j <- 0 until k) r(i)(j)=u(i)*shifted(j)/sum
          }
        }
        iterations+=1; require(objective.isFinite)
        val converged=history.lastOption.exists(p => math.abs(objective-p)<=config.tolerance*(1+math.abs(p)))
        history :+= objective
        val masses=Vector.tabulate(k)(j => points.indices.iterator.map(i => r(i)(j)).sum)
        val effective=Vector.tabulate(k)(j => masses(j)*masses(j)/points.indices.iterator.map(i => r(i)(j)*r(i)(j)).sum)
        if(effective.exists(x => !x.isFinite || x<config.minComponentDraws) || masses.exists(_<=0))
          return refuse(Status.InsufficientComponent,"A weighted component has insufficient ESS; no pruning or reseeding")
        if(converged) {
          val inflated=laws.map(g => VectorImportance.Gaussian(G(g.mean,g.covariance.map(_.map(_*config.covarianceInflation)))))
          return Result(Status.Fitted,Some(VectorImportance.Mixture(weights,inflated)),iterations,evaluations,history,config,
            "Weighted numerical fit only; no event-region discovery certificate")
        }
        if(iterations<config.maxIterations) {
          laws=Vector.tabulate(k)(j => moments(points.indices.map(i => r(i)(j)).toVector))
          weights=masses.map(_/masses.sum)
        }
      }
      refuse(Status.IterationLimit,"Weighted EM sweep cap reached")
    } catch { case _: IllegalArgumentException | _: ArithmeticException => refuse(Status.NumericalFailure,"Weighted covariance/objective unresolved") }
  }

  enum Status { case Fitted, InsufficientPilot, DegeneratePilot, InsufficientComponent, NumericalFailure, IterationLimit, EvaluationLimit }
  /** @param components explicit component count, 1..8; no automatic selection
    * @param maxIterations maximum complete density sweeps, at least 2
    * @param tolerance positive relative tolerance on average training log density
    * @param minComponentDraws minimum effective responsibility mass per component, at least 2
    * @param maxDensityEvaluations cap on component log-density evaluations, not target calls
    * @param maxStoredValues input scalar and responsibility slot cap (not a heap bound)
    * @param diagonalRidge empty or one explicit nonnegative variance per coordinate
    * @param covarianceInflation positive multiplier applied AFTER fitting
    */
  final case class Config(components: Int = 2, maxIterations: Int = 100, tolerance: Double = 1e-6,
    minComponentDraws: Double = 20, maxDensityEvaluations: Long = 2000000L,
    maxStoredValues: Long = 1000000L, diagonalRidge: Vector[Double] = Vector.empty,
    covarianceInflation: Double = 1.5) {
    require(components>=1 && components<=8 && maxIterations>=2 && maxIterations<=1000)
    require(tolerance.isFinite && tolerance>0 && minComponentDraws.isFinite && minComponentDraws>=2)
    require(maxDensityEvaluations>0 && maxStoredValues>0 && maxStoredValues<=10000000L)
    require(diagonalRidge!=null && diagonalRidge.forall(x => x.isFinite && x>=0))
    require(covarianceInflation.isFinite && covarianceInflation>0)
  }
  /** Numerical convergence is not pilot convergence or target-mode discovery.
    * @param proposal present only for Fitted; immutable inflated mixture, without a defensive component
    * @param iterations completed density sweeps
    * @param densityEvaluations actual component log-density calls
    * @param trainingLogDensity average log density of the UNINFLATED fitted mixture at each sweep
    */
  final case class Result(status: Status, proposal: Option[VectorImportance.Mixture], iterations: Int,
    densityEvaluations: Long, trainingLogDensity: Vector[Double], config: Config, message: String)

  /** Fit unweighted, discarded pilot traces with regularized EM and deterministic farthest-point starts.
    * @param chains finite post-warm-up vector traces; at least four chains with five draws each
    * @param config explicit dimension/work/regularization policy; dimensions 1..32
    * @return fitted normalized mixture or explicit refusal; never a partial production proposal
    * @example `GaussianMixtureProposal.fit(pilot.chains.map(_.result.samples), Config(components=2))`
    */
  def fit(chains: Vector[Vector[Vector[Double]]], config: Config = Config()): Result = {
    def check(): Unit = ParetoTail.interrupted()
    check(); require(config!=null && chains!=null && chains.forall(_!=null))
    val nLong=chains.iterator.map(_.size.toLong).sum
    require(nLong<=config.maxStoredValues, "Pilot draw count exceeds storage cap")
    require(chains.iterator.flatMap(_.iterator).forall(x => x!=null && x.size>=1 && x.size<=32), "Dimension must be 1..32")
    val d=chains.iterator.flatMap(_.iterator).take(1).map(_.size).nextOption().getOrElse(1)
    require(nLong*(d+config.components)<=config.maxStoredValues, "Pilot plus responsibility storage cap exceeded")
    var iterations=0; var evaluations=0L; var history=Vector.empty[Double]
    def refuse(s: Status, m: String)=Result(s,None,iterations,evaluations,history,config,m)
    val base=VectorImportance.fitGaussian(chains,VectorImportance.FitConfig(covarianceInflation=1,
      diagonalRidge=config.diagonalRidge,maxPilotValues=config.maxStoredValues))
    if(base.proposal.isEmpty) return refuse(base.status match {
      case VectorImportance.FitStatus.InsufficientPilot => Status.InsufficientPilot
      case VectorImportance.FitStatus.DegeneratePilot => Status.DegeneratePilot
      case _ => Status.NumericalFailure
    },base.message)
    val points=chains.flatten; val n=points.size; val k=config.components
    if(n<k*config.minComponentDraws) return refuse(Status.InsufficientPilot,"Too few draws for requested component masses")
    val global=base.proposal.get.law
    def distance(a: Vector[Double], b: Vector[Double]): Double =
      a.indices.map(i => math.pow((a(i)-b(i))/math.sqrt(global.covariance(i)(i)),2)).sum
    // Stable index tie breaking; deterministic for the same ordered input, not permutation invariant.
    var centers=Vector(points.maxBy(x => distance(x,global.mean)))
    while(centers.size<k) {
      check()
      val next=points.maxBy(x => centers.map(distance(x,_)).min)
      if(centers.contains(next)) return refuse(Status.DegeneratePilot,"Not enough distinct starting locations")
      centers :+= next
    }
    var laws=centers.map(m => G(m,global.covariance))
    var weights=Vector.fill(k)(1.0/k)
    val responsibilities=Array.ofDim[Double](n,k)
    try {
      while(iterations<config.maxIterations) {
        check()
        if(n.toLong*k>config.maxDensityEvaluations-evaluations)
          return refuse(Status.EvaluationLimit,"Component-density budget exhausted; no fitted proposal returned")
        var average=0.0
        for(i <- 0 until n) {
          check()
          val logs=Vector.tabulate(k) { j =>
            evaluations+=1
            math.log(weights(j))+laws(j).logDensity(points(i))
          }
          val peak=logs.max; val shifted=logs.map(x => math.exp(x-peak)); val sum=shifted.sum
          val logMix=peak+math.log(sum)
          require(logMix.isFinite,"Nonfinite training mixture density")
          average+=logMix/n
          for(j <- 0 until k) responsibilities(i)(j)=shifted(j)/sum
        }
        iterations+=1; require(average.isFinite,"Nonfinite training objective")
        val converged=history.lastOption.exists(previous => math.abs(average-previous)<=config.tolerance*(1+math.abs(previous)))
        history :+= average
        val masses=Vector.tabulate(k)(j => responsibilities.iterator.map(_(j)).sum)
        if(masses.exists(x => !x.isFinite || x<config.minComponentDraws))
          return refuse(Status.InsufficientComponent,"A component has insufficient responsibility mass; no pruning or reseeding")
        if(converged) {
          val inflated=laws.map(g => VectorImportance.Gaussian(G(g.mean,g.covariance.map(_.map(_*config.covarianceInflation)))))
          return Result(Status.Fitted,Some(VectorImportance.Mixture(weights,inflated)),iterations,evaluations,history,config,
            "Numerical fit only; inspect pilot exploration and preserve independent production")
        }
        if(iterations==config.maxIterations) return refuse(Status.IterationLimit,"Training objective did not converge within the sweep cap")
        laws=Vector.tabulate(k) { j =>
          check()
          val mean=Vector.tabulate(d)(a => points.indices.iterator.map(i => (points(i)(a)/masses(j))*responsibilities(i)(j)).sum)
          val cov=Vector.tabulate(d,d) { (a,b) =>
            check()
            points.indices.iterator.map(i => responsibilities(i)(j)*((points(i)(a)-mean(a))*(points(i)(b)-mean(b))/masses(j))).sum +
              (if(a==b && config.diagonalRidge.nonEmpty) config.diagonalRidge(a) else 0.0)
          }
          G(mean,cov)
        }
        weights=masses.map(_/n)
      }
      refuse(Status.IterationLimit,"Sweep cap reached")
    } catch { case _: IllegalArgumentException => refuse(Status.NumericalFailure,"Unsupported numerical range; no hidden ridge or covariance repair") }
  }
}
