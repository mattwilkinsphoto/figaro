package com.cra.figaro.example.documentation

import com.cra.figaro.algorithm.sampling.{VectorImportance as V, GaussianMixtureProposal as M, VectorSliceSampler as VS}
import com.cra.figaro.algorithm.sampling.parallel.MultiChainVectorSliceSampler as MC
import com.cra.figaro.library.atomic.continuous.MultivariateGaussianDistribution as G

object MixtureProposalExample {
  def main(args: Array[String]): Unit = {
    def gaussian(m: Double,v: Double)=V.Gaussian(G(Vector(m),Vector(Vector(v))))
    val target=V.Mixture(Vector(.7,.3),Vector(gaussian(-5,.09),gaussian(5,.09)))
    val pilotConfig=MC.Config(VS.Config(VS.Method.Quantile,draws=300,warmUp=100,maxEvaluations=4000,seed=41),parallelism=2)
    val starts=Vector(-5.2,-4.8,4.8,5.2)
    val pilot=MC.run(pilotConfig)((i,_) => MC.Model(Vector(starts(i)),target.logDensity))
    val fit=M.fit(pilot.chains.map(_.result.samples),M.Config(components=2,diagonalRidge=Vector(1e-4)))
    println(s"Fit=${fit.status}; pilot target calls=${pilot.chains.map(_.result.evaluations).sum}; fit component-density calls=${fit.densityEvaluations}")
    fit.proposal match {
      case Some(q) =>
        val frozen=V.Mixture(Vector(.1,.9),Vector(gaussian(0,100),q))
        val result=V.run(V.Config(seed=43),frozen,target.logDensity,x => if(x.head>0) 1 else 0)
        println(s"P(x>0)=${result.health.diagnostics.mean}; health=${result.health.status}")
        result.health.issues.foreach(i => println(i.message))
      case None => println(s"No production: ${fit.message}")
    }
  }
}
