package com.cra.figaro.test.modernization

import com.cra.figaro.algorithm.sampling.{VectorImportance as V, VectorSliceSampler as VS, InferenceHealth as H}
import com.cra.figaro.algorithm.sampling.parallel.MultiChainVectorSliceSampler as MC
import com.cra.figaro.library.atomic.continuous.MultivariateGaussianDistribution as G

/** Predeclared public-API acceptance grid. Fixed 30 seeds; no retries or health-threshold tuning. */
object VectorImportanceAcceptance {
  final case class Query(name: String, project: Vector[Double] => Double, reference: Double, tolerance: Double)
  final case class Case(name: String, target: Vector[Double] => Double, broad: V.Proposal,
    starts: Vector[Vector[Double]], queries: Vector[Query])
  def main(args: Array[String]): Unit = {
    require(args.length == 1 && Set("smoke","full").contains(args(0)))
    val seeds=if(args(0)=="full") StatisticalValidationStudy.seeds else StatisticalValidationStudy.seeds.take(1)
    val standard=DefensiveImportanceStudy.targets.map { f =>
      Case(f.id,f.logTarget,V.Box(Vector.fill(f.dimension)(0.0),Vector.fill(f.dimension)(10.0)),
        Vector.tabulate(4)(i => Vector.tabulate(f.dimension)(j => 1.0+(i+j)%4*2)),
        Vector.tabulate(f.dimension)(i => Query(s"mean$i",_(i),f.reference(i),.1*f.sd(i))))
    }
    val left=V.Gaussian(G(Vector(-5.0),Vector(Vector(.09))))
    val right=V.Gaussian(G(Vector(5.0),Vector(Vector(.09))))
    val modes=V.Mixture(Vector(.5,.5),Vector(left,right))
    val bimodal=Case("separated-modes",modes.logDensity,V.ProductStudentT(Vector(0.0),Vector(5.0),1),
      Vector(-5.5,-4.5,4.5,5.5).map(Vector(_)),Vector(Query("mean",_.head,0,.1*math.sqrt(25.09)),
        Query("positive",x => if(x.head>0) 1 else 0,.5,.05)))
    val boundary=Case("boundary-beta",x => if(x.forall(v => v>0 && v<1))
      x.map(v => math.log(72)+math.log(v)+7*math.log1p(-v)).sum else Double.NegativeInfinity,
      V.Box(Vector(0.0,0.0),Vector(1.0,1.0)),Vector(.05,.15,.5,.85).map(x => Vector(x,x)),
      Vector(Query("mean",_.head,.2,.1*math.sqrt(16.0/1100)),Query("upper-half",x => if(x.head>.5) 1 else 0,5.0/256,.01)))
    val heavy=Case("cauchy-event",x => -math.log(math.Pi)-math.log1p(x.head*x.head),
      V.ProductStudentT(Vector(0.0),Vector(1.0),1),Vector(-3.0,-1.0,1.0,3.0).map(Vector(_)),
      Vector(Query("above-five",x => if(x.head>5) 1 else 0,.5-math.atan(5)/math.Pi,.02)))
    println("VI,case,seed,query,reference,tolerance,mean,error,mcse,covered95,accurate,health,fit,pilotCalls,productionCalls,ess,seconds")
    def value(x: Option[Double])=x.map(_.toString).getOrElse("NA")
    for(c <- standard++Vector(bimodal,boundary,heavy); seed <- seeds) {
      val start=System.nanoTime()
      val pilot=MC.Config(VS.Config(VS.Method.Quantile,draws=10000,warmUp=20,maxEvaluations=2500,seed=seed),parallelism=1)
      val result=V.runWithPilot(pilot,c.starts,c.broad,V.Config(seed=seed ^ 0x5deece66dL),c.target,c.queries.head.project,
        V.FitConfig(diagonalRidge=Vector.fill(c.broad.dimension)(1e-4)))
      require(result.pilotEvaluations==10000)
      val reports=c.queries.zipWithIndex.map { (q,i) =>
        result.production.map { p => if(i==0) p.health else H.importance(p.logWeights,true,Some(p.samples.map(q.project))) }
      }
      val elapsed=(System.nanoTime()-start)/1e9
      c.queries.zip(reports).foreach { (q,report) =>
        val mean=report.flatMap(_.diagnostics.mean)
        val error=mean.map(_-q.reference)
        val mcse=report.flatMap(_.diagnostics.rawMcse)
        val covered=error.exists(e => mcse.exists(s => math.abs(e)<=1.959963984540054*s))
        val accurate=error.exists(e => math.abs(e)<=q.tolerance)
        val status=report.map(_.status.toString).getOrElse("NotRun")
        println(s"VI,${c.name},$seed,${q.name},${q.reference},${q.tolerance},${value(mean)},${value(error)},${value(mcse)},$covered,$accurate,$status,${result.fit.status},${result.pilotEvaluations},${result.production.map(_.evaluations).getOrElse(0L)},${value(report.flatMap(_.diagnostics.ess))},$elapsed")
      }
    }
  }
}
