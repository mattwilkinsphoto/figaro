package com.cra.figaro.test.modernization

import com.cra.figaro.algorithm.sampling.{VectorImportance as V, VectorSliceSampler as VS, ParetoTail}
import com.cra.figaro.algorithm.sampling.parallel.MultiChainVectorSliceSampler as MC
import com.cra.figaro.library.atomic.continuous.{MultivariateGaussianDistribution as G, StudentTDistribution}

/** Fixed protocol: independent repetitions, no tuning/retries/exclusion by health status. */
object ImportanceCalibrationStudy {
  case class Scenario(id: String, proposal: V.Proposal, target: Vector[Double] => Double,
    query: Vector[Double] => Double, reference: Double, tolerance: Double, pilot: Boolean = false)
  def gaussian(d: Int, variance: Double, center: Double = 0): V.Gaussian =
    V.Gaussian(G(Vector.fill(d)(center),Vector.tabulate(d,d)((i,j) => if(i==j) variance else 0)))
  def cases: Vector[Scenario] = {
    val normal=gaussian(1,1)
    val cov=Vector.tabulate(12,12)((i,j) => if(i==j) 1.0 else .8)
    val correlated=V.Gaussian(G(Vector.fill(12)(0.0),cov))
    val modes=V.Mixture(Vector(.5,.5),Vector(gaussian(1,.09,-5),gaussian(1,.09,5)))
    val missed=V.Mixture(Vector(.5,.5),Vector(gaussian(1,.0001,-8),gaussian(1,.0001,8)))
    val t=StudentTDistribution(3)
    val fixed=Vector(
      Scenario("normal",gaussian(1,2),normal.logDensity,_.head,0,.1),
      Scenario("correlated12",V.Gaussian(G(Vector.fill(12)(0.0),cov.map(_.map(_*1.3)))),correlated.logDensity,_.head,0,.1),
      Scenario("banana4",gaussian(4,2),x => -(x(0)*x(0)+math.pow(x(1)-.5*(x(0)*x(0)-1),2)+x(2)*x(2)+x(3)*x(3))/2,_(1),0,.1*math.sqrt(1.5)),
      Scenario("boundary",V.Box(Vector(0.0),Vector(1.0)),x => if(x.head>0 && x.head<1) math.log(72)+math.log(x.head)+7*math.log1p(-x.head) else Double.NegativeInfinity,_.head,.2,.1*math.sqrt(16.0/1100)),
      Scenario("rare",gaussian(1,1.2),normal.logDensity,x => if(x.head>4) 1 else 0,3.167124183311998e-5,1.583562091655999e-5),
      Scenario("rare-tilted",V.Mixture(Vector(.5,.5),Vector(normal,gaussian(1,1,4))),normal.logDensity,x => if(x.head>4) 1 else 0,3.167124183311998e-5,1.583562091655999e-5),
      Scenario("heavy",gaussian(1,2),x => t.logDensity(x.head),_.head,0,.1*math.sqrt(3)),
      Scenario("modes",gaussian(1,25),modes.logDensity,x => if(x.head>0) 1 else 0,.5,.05),
      Scenario("missed-mode",gaussian(1,.0001,-8),missed.logDensity,x => if(x.head>0) 1 else 0,.5,.05))
    fixed ++ DefensiveImportanceStudy.targets.filter(_.id.endsWith("heldout")).map { f =>
      Scenario(f.id,V.Box(Vector.fill(f.dimension)(0.0),Vector.fill(f.dimension)(10.0)),f.logTarget,_.head,f.reference.head,.1*f.sd.head,true)
    }
  }
  /** Delta-method ratio batching, not a mean of separately normalized batch estimates. */
  def assessment(logs: Vector[Double], values: Vector[Double], mean: Double): (Double,Double,Option[Double]) = {
    val peak=logs.max
    val scaled=logs.map(w => math.exp(w-peak)); val total=scaled.sum
    val z=scaled.indices.map(i => scaled(i)/total*(values(i)-mean)).toVector
    val squares=z.map(v => v*v); val sum=squares.sum
    val effective=if(sum==0) 0.0 else sum*sum/squares.map(v => v*v).sum
    val groups=z.grouped(z.size/20).map(_.sum).toVector
    require(groups.size==20)
    val error=math.sqrt(20.0/19*groups.map(v => v*v).sum)
    val tail=ParetoTail.fit(z.map(v => if(v==0) Double.NegativeInfinity else math.log(math.abs(v)))).k
    (error,effective,tail)
  }
  def main(args: Array[String]): Unit = {
    require(args.length==1 && Set("smoke","full").contains(args(0)))
    val repetitions=if(args(0)=="full") 200 else 1
    println("IC,case,seed,draws,pilotCalls,reference,tolerance,mean,rawMcse,batchMcse,varianceEss,queryK,health,coveredRaw,coveredBatch,accurate,queryFlag")
    for(c <- cases; i <- 0 until repetitions; n <- Vector(2000,10000,40000)) {
      val seed=5000003L+104729L*i
      val config=V.Config(draws=n,maxEvaluations=n,seed=seed)
      val (result,pilotCalls)=if(c.pilot) {
        val pilot=MC.Config(VS.Config(VS.Method.Quantile,draws=2000,warmUp=20,maxEvaluations=1000,seed=seed+999999999L),parallelism=1)
        val starts=Vector.tabulate(4)(i => Vector.tabulate(c.proposal.dimension)(j => 1.0+(i+j)%4*2))
        val trained=V.runWithPilot(pilot,starts,c.proposal,config,c.target,c.query,V.FitConfig(diagonalRidge=Vector.fill(c.proposal.dimension)(1e-4)))
        require(trained.fit.status==V.FitStatus.Fitted) // Protocol regression, never substitute/retry.
        (trained.production.get,trained.pilotEvaluations)
      } else (V.run(config,c.proposal,c.target,c.query),0L)
      require(result.evaluations==n)
      val mean=result.health.diagnostics.mean.get
      val raw=result.health.diagnostics.rawMcse.get
      val (batch,effective,k)=assessment(result.logWeights,result.samples.map(c.query),mean)
      val error=math.abs(mean-c.reference)
      val rawCovered=raw>0 && error<=1.959963984540054*raw
      val batchCovered=batch>0 && error<=2.093024054408263*batch // t_19, fixed before study
      val flag=effective<20 || k.exists(_>=.7)
      println(s"IC,${c.id},$seed,$n,$pilotCalls,${c.reference},${c.tolerance},$mean,$raw,$batch,$effective,${k.map(_.toString).getOrElse("NA")},${result.health.status},$rawCovered,$batchCovered,${error<=c.tolerance},$flag")
    }
  }
}
