package com.cra.figaro.example.documentation

import com.cra.figaro.algorithm.sampling.{VectorImportance as V, VectorSliceSampler as VS}
import com.cra.figaro.algorithm.sampling.parallel.MultiChainVectorSliceSampler as MC

/** Opt-in explicit-density examples; no graph rewriting or precision stopping. */
object VectorImportanceExample {
  def main(args: Array[String]): Unit = {
    val broad=V.Box(Vector(-5.0),Vector(5.0))
    val target: Vector[Double] => Double = x => if(math.abs(x.head)<5) -x.head*x.head/2 else Double.NegativeInfinity
    val pilot=MC.Config(VS.Config(VS.Method.Quantile,draws=300,warmUp=100,maxEvaluations=5000,seed=42),parallelism=2)
    val starts=Vector(-2.0,-.5,.5,2.0).map(Vector(_))
    val attempt=V.runWithPilot(pilot,starts,broad,V.Config(draws=5000,maxEvaluations=5000,seed=43),target,_.head)
    require(attempt.fit.status==V.FitStatus.Fitted)
    val mean=attempt.production.get
    println(s"Mean=${mean.health.diagnostics.mean}, health=${mean.health.status}, pilot calls=${attempt.pilotEvaluations}, total=${attempt.totalEvaluations}")
    mean.health.issues.foreach(i => println(s"${i.code}: ${i.message}"))
    // Freeze once, reuse for a different query with fresh production randomness.
    val fixed=V.Mixture(Vector(.1,.9),Vector(broad,attempt.fit.proposal.get))
    val event=V.run(V.Config(seed=44),fixed,target,x => if(x.head>1) 1.0 else 0.0)
    println(s"P(x>1)=${event.health.diagnostics.mean}; inspect all health issues before use")
    // Too little training returns no posterior estimate and does not use the prior silently.
    val short=V.runWithPilot(pilot.copy(sampler=pilot.sampler.copy(maxEvaluations=1)),starts,broad,V.Config(seed=45),target,_.head)
    require(short.production.isEmpty && short.totalEvaluations==4)
    println(s"Insufficient pilot: ${short.fit.status}; production was not run")
  }
}
