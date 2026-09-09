package com.cra.figaro.example.documentation

import com.cra.figaro.algorithm.sampling.{GraphProposalImportance as P, VectorImportance as V, GaussianMixtureProposal as M}
import com.cra.figaro.algorithm.sampling.parallel.MultiChainMetropolisHastings as MH
import com.cra.figaro.library.atomic.continuous.{Normal, GaussianMixture, GaussianMixtureDistribution, MultivariateGaussianDistribution as G}
import com.cra.figaro.language.*

/** Original joint prior + frozen proposal + ordinary graph evidence, with owned cleanup. */
object GraphProposalExample {
  def main(args: Array[String]): Unit = {
    val prior=G(Vector(0.0),Vector(Vector(1.0)))
    val proposal=V.Gaussian(G(Vector(.8),Vector(Vector(.3))))
    val result=P.run(P.Config(seed=43),proposal,prior.logDensity) { (u,root) =>
      val theta=root.map(_.head)(using "theta",u)
      Normal(theta,.25)(using "measurement",u).observe(1.0)
      theta
    }
    println(s"Observed Normal posterior mean=${result.health.diagnostics.mean}; expected=0.8; attempts=${result.attempts}")
    println(s"Health=${result.health.status}; graph traversal lookups=${result.elementVisits}")

    val law=GaussianMixtureDistribution(Vector(.7,.3),Vector(
      G(Vector(-5.0),Vector(Vector(.09))),G(Vector(5.0),Vector(Vector(.09)))))
    // The existing graph MH pilot uses an explicit likelihood constraint, not observe().
    val pilot=MH.run(MH.Config(drawsPerChain=500,warmUp=100,seed=91,parallelism=2)) { (u,i) =>
      val theta=GaussianMixture(law)(using "",u)
      theta.addLogConstraint(x => math.log(if(x.head>0) .8 else .2))
      MH.Model(Vector(MH.Observable("theta",theta)(_.head)))
    }
    println(s"Pilot diagnostics=${pilot.diagnostics("theta")}")
    val traces=pilot.chains.map(_.draws("theta").map(Vector(_)))
    val fit=M.fit(traces,M.Config(components=2,diagonalRidge=Vector(1e-4)))
    fit.proposal match {
      case None => println(s"No graph production: ${fit.status}: ${fit.message}")
      case Some(q) =>
        val broad=V.Gaussian(G(Vector(0.0),Vector(Vector(100.0))))
        val frozen=V.Mixture(Vector(.1,.9),Vector(broad,q))
        val posterior=P.run(P.Config(seed=93),frozen,law.logDensity) { (u,root) =>
          val probability=root.map(x => if(x.head>0) .8 else .2)(using "",u)
          Flip(probability)(using "observation",u).observe(true)
          root.map(x => if(x.head>0) 1.0 else 0.0)(using "",u)
        }
        println(s"Pilot-fitted graph event=${posterior.health.diagnostics.mean}; expected=${.24/.38}; health=${posterior.health.status}")
    }
  }
}
