package com.cra.figaro.test.modernization

import com.cra.figaro.algorithm.sampling.{GraphProposalImportance as P, VectorImportance as V, GaussianMixtureProposal as M, Importance}
import com.cra.figaro.algorithm.sampling.parallel.MultiChainMetropolisHastings as MH
import com.cra.figaro.library.atomic.continuous.{Normal, GaussianMixture, GaussianMixtureDistribution as GM, MultivariateGaussianDistribution as G}
import com.cra.figaro.language.*
import com.cra.figaro.util.{RandomContext, SamplingRandom}

/** End-to-end cost grid. Training, fit refusals and inaccurate runs are never discarded. */
object GraphCostStudy {
  val cases = Vector("concentrated", "hierarchical", "mixture")
  val methods = Vector("legacy", "prior", "single", "mixture", "mh")
  // Counts frozen from the initial 20-seed cost grid; no held-out accuracy tuning.
  val matchedCounts: Vector[Vector[Int]]=Vector(
    Vector(58112,116584),Vector(44100,88416),Vector(35104,73080),Vector(23168,63196),Vector(45744,92732),
    Vector(43792,87804),Vector(34824,69696),Vector(29384,61512),Vector(11160,41528),Vector(46584,94824),
    Vector(102232,204768),Vector(47620,95408),Vector(35864,74768),Vector(29192,62548),Vector(42832,86692))
  def prior(name: String): GM = if(name == "mixture") GM(Vector(.7,.3),Vector(
    G(Vector(-5.0),Vector(Vector(.09))),G(Vector(5.0),Vector(Vector(.09)))))
    else GM(Vector(1.0),Vector(G(Vector(0.0),Vector(Vector(1.0)))))
  def reference(name: String): Double = name match {
    case "concentrated" => 3.0/1.01
    case "hierarchical" => 1.0/1.02
    case _ => .24/.38
  }
  def query(name: String, u: Universe, root: Element[Vector[Double]], mh: Boolean): Element[Double] = {
    val theta=root.map(_.head)(using "",u)
    if(name == "mixture") {
      if(mh) theta.addLogConstraint(x => math.log(if(x>0) .8 else .2))
      else Flip(theta.map(x => if(x>0) .8 else .2)(using "",u))(using "",u).observe(true)
      theta.map(x => if(x>0) 1.0 else 0.0)(using "",u)
    } else {
      val latent=if(name == "hierarchical") Normal(theta,.01)(using "",u) else theta
      val y=if(name == "hierarchical") 1.0 else 3.0
      if(mh) latent.addLogConstraint(x => -.5*math.log(2*math.Pi*.01)-.5*(x-y)*(x-y)/.01)
      else Normal(latent,.01)(using "",u).observe(y)
      theta
    }
  }
  def chain(name: String, draws: Int, seed: Long): MH.Result =
    MH.run(MH.Config(drawsPerChain=draws,warmUp=250,seed=seed,parallelism=2)) { (u,_) =>
      val root=GaussianMixture(prior(name))(using "",u)
      val q=query(name,u,root,true)
      MH.Model(Vector(MH.Observable("root",root)(_.head), MH.Observable("query",q)(identity)))
    }
  def trial(name: String, method: String, draws: Int, seed: Long, jvm: Int, emit: Boolean): Unit = {
    val started=System.nanoTime()
    var pilotSeconds=0.0; var fitSeconds=0.0; var pilotTransitions=0
    var status="Completed"; var mean=Double.NaN; var ess=Double.NaN; var rhat=Double.NaN
    var attempts=0; var visits=0L
    val law=prior(name)
    val base=V.Mixture(law.weights,law.components.map(V.Gaussian.apply))
    if(method == "legacy") {
      val u=new Universe
      RandomContext.withRandom(SamplingRandom.seeded(seed)) {
        Universe.withUniverse(u) {
          try {
            val root=GaussianMixture(law)(using "",u)
            val q=query(name,u,root,false)
            val a=Importance(draws,q)(using u)
            try { a.start(); mean=a.expectation(q,(x: Double) => x); attempts=draws }
            finally { a.kill() }
          } finally { u.clear() }
        }
      }
    } else if(method == "mh") {
      val result=chain(name,draws/4,seed)
      mean=result.diagnostics("query").mean
      rhat=result.diagnostics("query").rHat.getOrElse(Double.NaN)
      attempts=draws; pilotTransitions=1000
    } else {
      var proposal: Option[V.Proposal]=Some(base)
      if(method != "prior") {
        val before=System.nanoTime(); val pilot=chain(name,500,seed+1000000007L)
        pilotSeconds=(System.nanoTime()-before)*1e-9; pilotTransitions=3000
        rhat=pilot.diagnostics("root").rHat.getOrElse(Double.NaN)
        val traces=pilot.chains.map(_.draws("root").map(Vector(_)))
        val fitStart=System.nanoTime()
        if(method == "single") {
          val fit=V.fitGaussian(traces,V.FitConfig(covarianceInflation=1.5,diagonalRidge=Vector(1e-4)))
          proposal=fit.proposal; status=fit.status.toString
        } else {
          val fit=M.fit(traces,M.Config(components=2,covarianceInflation=1.5,diagonalRidge=Vector(1e-4)))
          proposal=fit.proposal; status=fit.status.toString
        }
        fitSeconds=(System.nanoTime()-fitStart)*1e-9
        proposal=proposal.map(q => V.Mixture(Vector(.1,.9),Vector(base,q)))
      }
      proposal.foreach { q =>
        val result=P.run(P.Config(draws=draws,maxAttempts=draws,seed=seed,maxElementVisits=20000000),q,law.logDensity) {
          (u,root) => query(name,u,root,false)
        }
        mean=result.health.diagnostics.mean.getOrElse(Double.NaN)
        ess=result.health.diagnostics.ess.getOrElse(Double.NaN)
        attempts=result.attempts; visits=result.elementVisits
      }
    }
    val seconds=(System.nanoTime()-started)*1e-9
    if(emit) println(s"GC,$jvm,$name,$method,$draws,$seed,$status,$mean,$ess,$rhat,$attempts,$pilotTransitions,$visits,$pilotSeconds,$fitSeconds,$seconds,${reference(name)}")
  }
  def main(args: Array[String]): Unit = {
    require(args.length==2 && Set("smoke","full","matched").contains(args(0)))
    val jvm=args(1).toInt; require(jvm>=1 && jvm<=3)
    println(s"GraphCost JVM=$jvm Java=${System.getProperty("java.version")} processors=${Runtime.getRuntime.availableProcessors}")
    for(c <- cases; m <- methods) trial(c,m,2000,71, jvm,false)
    println("GC,jvm,case,method,draws,seed,status,mean,ess,rhat,attempts,pilotTransitions,visits,pilotSeconds,fitSeconds,totalSeconds,reference")
    for(i <- 0 until (if(args(0)=="smoke") 1 else 20); c <- cases; b <- 0 until 2;
      m <- methods.drop(i%methods.size) ++ methods.take(i%methods.size)) {
      val matched=args(0)=="matched"
      val n=if(matched) matchedCounts(cases.indexOf(c)*methods.size+methods.indexOf(m))(b) else Vector(2000,8000)(b)
      trial(c,m,n,(if(matched) 41000017L else 23000003L)+15485863L*i,jvm,true)
    }
  }
}
