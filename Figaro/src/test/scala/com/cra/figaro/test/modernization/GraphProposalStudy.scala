package com.cra.figaro.test.modernization

import com.cra.figaro.algorithm.sampling.{GraphProposalImportance as P, VectorImportance as V}
import com.cra.figaro.library.atomic.continuous.{Normal,MultivariateGaussianDistribution as G}
import com.cra.figaro.language.*

/** Fixed 100-seed graph acceptance grid, including raw zero-weight attempts. */
object GraphProposalStudy {
  def main(args: Array[String]): Unit = {
    require(args.length==1 && Set("smoke","full").contains(args(0)))
    val repeats=if(args(0)=="full") 100 else 1
    def gaussian(m: Double,v: Double)=V.Gaussian(G(Vector(m),Vector(Vector(v))))
    val normal=gaussian(0,1)
    val mixture=V.Mixture(Vector(.7,.3),Vector(gaussian(-5,.09),gaussian(5,.09)))
    val informed=V.Mixture(Vector(.14/.38,.24/.38),Vector(gaussian(-5,.12),gaussian(5,.12)))
    println("GP,case,seed,method,attempts,priorCalls,proposalDraws,rejected,reference,mean,ess,accurate,health")
    for(name <- Vector("normal","hierarchical","mixture"); i <- 0 until repeats; method <- Vector("prior","informed")) {
      val seed=19000001L+15485863L*i
      val prior: V.Proposal=if(name=="mixture") mixture else normal
      val proposal=if(method=="prior") prior else name match {
        case "normal" => gaussian(.8,.3)
        case "hierarchical" => gaussian(1.0/3,.8)
        case _ => informed
      }
      val result=P.run(P.Config(draws=2000,maxAttempts=2000,seed=seed),proposal,prior.logDensity) { (u,root) =>
        val theta=root.map(_.head)(using "",u)
        name match {
          case "normal" => Normal(theta,.25)(using "",u).observe(1); theta
          case "hierarchical" =>
            val latent=Normal(theta,1)(using "",u)
            Normal(latent,1)(using "",u).observe(1); theta
          case _ =>
            Flip(root.map(x => if(x.head>0) .8 else .2)(using "",u))(using "",u).observe(true)
            root.map(x => if(x.head>0) 1.0 else 0.0)(using "",u)
        }
      }
      val reference=if(name=="normal") .8 else if(name=="hierarchical") 1.0/3 else .24/.38
      val tolerance=if(name=="hierarchical") .05 else .025
      val mean=result.health.diagnostics.mean.get
      println(s"GP,$name,$seed,$method,${result.attempts},${result.priorEvaluations},${result.proposalDraws},${result.rejected},$reference,$mean,${result.health.diagnostics.ess.get},${math.abs(mean-reference)<=tolerance},${result.health.status}")
    }
  }
}
