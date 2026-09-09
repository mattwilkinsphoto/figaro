package com.cra.figaro.test.modernization

import com.cra.figaro.algorithm.sampling.{VectorImportance as V, GaussianMixtureProposal as M, VectorSliceSampler as VS}
import com.cra.figaro.algorithm.sampling.parallel.MultiChainVectorSliceSampler as MC
import com.cra.figaro.library.atomic.continuous.MultivariateGaussianDistribution as G

/** Same pilot and production seed for single/multi-component comparisons; all failures retained. */
object MixtureProposalStudy {
  def main(args: Array[String]): Unit = {
    require(args.length==1 && Set("smoke","full").contains(args(0)))
    val repeats=if(args(0)=="full") 100 else 1
    def gaussian(center: Double)=V.Gaussian(G(Vector(center),Vector(Vector(.09))))
    val scenarios=Vector(("balanced",Vector(.5,.5),Vector(-5.0,5.0)),
      ("unbalanced",Vector(.9,.1),Vector(-5.0,5.0)),
      ("three-modes",Vector(.5,.3,.2),Vector(-6.0,0.0,6.0)))
    println("MP,case,seed,draws,method,fit,pilotCalls,fitCalls,productionCalls,reference,mean,ess,accurate,health")
    for((name,weights,centers) <- scenarios; i <- 0 until repeats) {
      val seed=9000001L+130363L*i
      val target=V.Mixture(weights,centers.map(gaussian))
      val broad=V.Gaussian(G(Vector(0.0),Vector(Vector(100.0))))
      val pilotConfig=MC.Config(VS.Config(VS.Method.Quantile,draws=2000,warmUp=20,maxEvaluations=2000,seed=seed),chains=6,parallelism=1)
      val pilot=MC.run(pilotConfig)((i,_) => MC.Model(Vector(centers(i%centers.size)+.1),target.logDensity))
      val traces=pilot.chains.map(_.result.samples)
      val single=V.fitGaussian(traces,V.FitConfig(diagonalRidge=Vector(1e-4),covarianceInflation=1.5))
      val multi=M.fit(traces,M.Config(components=centers.size,diagonalRidge=Vector(1e-4)))
      val fits=Vector(("single",single.status.toString,single.proposal.map(p => p: V.Proposal),0L),
        ("multi",multi.status.toString,multi.proposal.map(p => p: V.Proposal),multi.densityEvaluations))
      val reference=weights.last
      for((method,status,fit,calls) <- fits; n <- Vector(2000,10000)) {
        val result=fit.map { q => V.run(V.Config(draws=n,maxEvaluations=n,seed=seed+999999999L),
          V.Mixture(Vector(.1,.9),Vector(broad,q)),target.logDensity,x => if(x.head>centers.last-1) 1 else 0) }
        // Threshold is >3.333 SD below final center: use exact Gaussian event probability.
        val normal=new org.apache.commons.math3.distribution.NormalDistribution()
        val exact=centers.zip(weights).map((m,w) => w*normal.cumulativeProbability((m-centers.last+1)/.3)).sum
        val mean=result.flatMap(_.health.diagnostics.mean)
        val ess=result.flatMap(_.health.diagnostics.ess)
        println(s"MP,$name,$seed,$n,$method,$status,${pilot.chains.map(_.result.evaluations).sum},$calls,${result.map(_.evaluations).getOrElse(0L)},$exact,${mean.map(_.toString).getOrElse("NA")},${ess.map(_.toString).getOrElse("NA")},${mean.exists(m => math.abs(m-exact)<=.025)},${result.map(_.health.status.toString).getOrElse("NotRun")}")
      }
    }
  }
}
