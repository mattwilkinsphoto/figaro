package com.cra.figaro.test.modernization

import com.cra.figaro.algorithm.sampling.SupportAwareProposal
import com.cra.figaro.algorithm.sampling.parallel.MultiChainMetropolisHastings as MH
import com.cra.figaro.library.atomic.continuous.{ScalarElement,LogNormalDistribution}

/** Analytic fixtures, not application data. Output CSV is captured by the build log.
  * Repeat in a fresh JVM to assess timing variability; no CI speed threshold.
  */
object SupportAwareProposalStudy {
  def main(args: Array[String]): Unit = {
    println("case,seed,proposal,draws_per_chain,mean,expected,mean_ess,rhat,mcse,seconds,ess_per_second")
    // Warm both paths without retaining a timing claim.
    for(local <- Vector(false,true)) evaluate(true,0,local,2000,false)
    for(strong <- Vector(true,false); seed <- Vector(8421L,8422L,8423L); local <- Vector(false,true))
      evaluate(strong,seed,local,20000,true)
  }
  private def evaluate(strong: Boolean,seed: Long,local: Boolean,n: Int,report: Boolean): Unit = {
    val result=MH.run(MH.Config(chains=2,drawsPerChain=n,warmUp=2000,parallelism=2,seed=seed)) { (u,_) =>
      val x=ScalarElement(LogNormalDistribution(0,1))(using "x",u)
      if(strong) x.addLogConstraint((v: Double) => -.5*math.pow((math.log(v)-2)/.05,2))
      val scheme=if(local) Some(SupportAwareProposal.positive(x,if(strong) .1 else 1.0)) else None
      MH.Model(Vector(MH.Observable("logx",x)(math.log)),scheme)
    }
    val d=result.diagnostics("logx"); val expected=if(strong) 2/1.0025 else 0.0
    require(math.abs(d.mean-expected)<(if(strong) .02 else .06),"Analytic posterior control failed")
    if(report) println(s"${if(strong) "concentrated" else "prior"},$seed,${if(local) "log_walk" else "prior"},$n,${d.mean},$expected,${d.meanEss.get},${d.rHat.get},${d.mcseMean.get},${result.elapsedSeconds},${d.meanEss.get/result.elapsedSeconds}")
  }
}
