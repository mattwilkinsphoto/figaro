package com.cra.figaro.test.modernization

import com.cra.figaro.algorithm.sampling.InferenceHealth

/** Fixed historical workloads, not a general false-alarm calibration or a new RNG comparison. */
object InferenceHealthStudy {
  def main(args: Array[String]): Unit = {
    require(args.isEmpty)
    val study = StatisticalValidationStudy
    println("HEALTH,family,seed,draws,status,ess,maxWeight,k,mean,error,rawMcse,issues")
    for (family <- Vector("gamma", "dirichlet"); seed <- study.seeds) {
      val rng = study.generator("L64X128MixRandom", seed)
      val points = Vector.fill(2000)(Vector.fill(study.references(family).size)(10*rng.nextDouble()))
      val report = InferenceHealth.importance(points.map(study.logLikelihood(family, _)), true,
        Some(points.map(_.head)))
      val d = report.diagnostics
      val reference = study.kernel(family, "L64X128MixRandom", seed, 2000)
      require(math.abs(d.mean.get-reference.mean.head) < 1e-10, "Changed estimate")
      require(math.abs(d.ess.get-reference.ess) < 1e-9, "Changed weight ESS")
      require(report.status != InferenceHealth.Status.ChecksPassed, "Missed known low-ESS workload")
      val k = d.pareto.flatMap(_.k).map(_.toString).getOrElse("NA")
      val codes = report.issues.map(_.code.toString).mkString("|")
      println(s"HEALTH,$family,$seed,2000,${report.status},${d.ess.get},${d.maxWeight.get},$k,${d.mean.get},${d.mean.get-study.references(family).head},${d.rawMcse.get},$codes")
    }
  }
}
