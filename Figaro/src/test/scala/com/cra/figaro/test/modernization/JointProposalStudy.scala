package com.cra.figaro.test.modernization

import com.cra.figaro.algorithm.sampling.{VectorImportance as V,GraphProposalImportance as P}
import com.cra.figaro.library.atomic.continuous.{MultivariateGaussianDistribution as G,GaussianDistribution,Normal}
import com.cra.figaro.language.*

/** Analytic controls: no pilot is charged because these proposals are supplied, not fitted. */
object JointProposalStudy {
  def g(m: Double,v: Double)=V.Gaussian(G(Vector(m),Vector(Vector(v))))
  val prior=g(0,1)
  def main(args: Array[String]): Unit = {
    require(args.length==1 && Set("smoke","full").contains(args(0)))
    println("JP,case,method,seed,draws,mean,reference,ess,mcse,seconds")
    for(i <- 0 until (if(args(0)=="full") 100 else 1); name <- Vector("hierarchical","nonlinear","rare-event"); method <- Vector("prior","root","joint")) {
      val seed=59000021L+15485863L*i; val start=System.nanoTime()
      val nonlinear=name=="nonlinear"
      val queryMean=if(nonlinear) (x: Double) => x*x else (x: Double) => x
      val thetaProposal=if(nonlinear) V.Mixture(Vector(.5,.5),Vector(g(-1,.02),g(1,.02))) else g(1/1.02,.04)
      val result=if(name=="rare-event") {
        val q=method match {
          case "prior" => prior
          case "root" => V.Mixture(Vector(.1,.9),Vector(prior,g(2,1)))
          case _ => V.Mixture(Vector(.1,.9),Vector(prior,g(4,1)))
        }
        P.run(P.Config(draws=4000,maxAttempts=4000,seed=seed),q,prior.logDensity) { (u,root) =>
          root.map(x => if(x.head>4) 1.0 else 0.0)(using "",u)
        }
      } else if(method=="joint") {
        val original=V.Conditional(prior,1,x => g(queryMean(x.head),.01))
        val q=V.Conditional(thetaProposal,1,x => g((queryMean(x.head)+1)/2,.005))
        val defensive=V.Mixture(Vector(.1,.9),Vector(original,q))
        P.run(P.Config(draws=4000,maxAttempts=4000,seed=seed),defensive,original.logDensity) { (u,root) =>
          val z=root.map(_(1))(using "",u)
          Normal(z,.01)(using "",u).observe(1.0)
          root.map(x => if(nonlinear) { if(x.head>0) 1.0 else 0.0 } else x.head)(using "",u)
        }
      } else {
        val q=if(method=="prior") prior else V.Mixture(Vector(.1,.9),Vector(prior,thetaProposal))
        P.run(P.Config(draws=4000,maxAttempts=4000,seed=seed),q,prior.logDensity) { (u,root) =>
          val theta=root.map(_.head)(using "",u)
          val z=Normal(theta.map(queryMean)(using "",u),.01)(using "",u)
          Normal(z,.01)(using "",u).observe(1.0)
          if(nonlinear) theta.map(x => if(x>0) 1.0 else 0.0)(using "",u) else theta
        }
      }
      val ref=if(name=="rare-event") GaussianDistribution(0,1).survival(4) else if(nonlinear) .5 else 1/1.02
      val d=result.health.diagnostics
      println(s"JP,$name,$method,$seed,4000,${d.mean.get},$ref,${d.ess.get},${d.rawMcse.getOrElse(Double.NaN)},${(System.nanoTime()-start)*1e-9}")
    }
  }
}
