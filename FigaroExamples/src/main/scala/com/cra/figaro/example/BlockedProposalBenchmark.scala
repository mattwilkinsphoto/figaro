package com.cra.figaro.example

import com.cra.figaro.algorithm.sampling.{DisjointScheme, GaussianBlockProposal, ProposalScheme}
import com.cra.figaro.algorithm.sampling.parallel.{McmcPrecision, MultiChainMetropolisHastings as MH}
import com.cra.figaro.language.*
import com.cra.figaro.library.atomic.continuous.Normal
import scala.jdk.CollectionConverters.*

/** Paired fixed/adaptive proposal comparisons against analytic posterior means. */
object BlockedProposalBenchmark {
  private val cases = Vector("normal" -> "default", "normal" -> "block",
    "correlated" -> "default", "correlated" -> "joint-prior", "correlated" -> "diagonal", "correlated" -> "block",
    "multimodal" -> "default", "multimodal" -> "block", "multimodal" -> "mixed")

  private def model(workload: String, strategy: String)(u: Universe, index: Int): MH.Model = {
    if (workload == "multimodal") {
      val x = Normal(0, 25)(using "", u) // Variance 25, not SD 25.
      x.addLogConstraint((v: Double) => {
        val a = -0.5 * math.pow((v - 4) / 0.75, 2)
        val b = -0.5 * math.pow((v + 4) / 0.75, 2)
        val max = math.max(a, b)
        max + math.log(math.exp(a - max) + math.exp(b - max)) + 0.5 * math.pow(v / 5, 2)
      })
      val proposal = if (strategy == "default") None else {
        val block = GaussianBlockProposal(Vector(x), Vector(Vector(0.75 * 0.75)))
        Some(if (strategy == "mixed") DisjointScheme(0.8 -> (() => block),
          0.2 -> (() => ProposalScheme.default(using u))) else block)
      }
      MH.Model(Vector(MH.Observable("x", x)(identity),
        MH.Observable("positive", x)(v => if (v > 0) 1.0 else 0.0)), proposal,
        initialState = () => (x.value > 0) == (index % 2 == 0))
    } else {
      val x = Normal(0, 1)(using "", u)
      val y = Normal(0, 1)(using "", u)
      if (workload == "correlated") {
        val d = Apply(x, y, (a: Double, b: Double) => a - b)(using "", u)
        d.addLogConstraint((v: Double) => -0.5 * math.pow(v / 0.15, 2))
      }
      val variance = if (workload == "correlated") 1.0225 / 2.0225 else 1.0
      val covariance = if (workload == "correlated" && strategy == "block") 1 / 2.0225 else 0.0
      val proposal = strategy match {
        case "default" => None
        case "joint-prior" => Some(ProposalScheme(x, y))
        case _ => Some(GaussianBlockProposal(Vector(x, y),
          Vector(Vector(2.8 * variance, 2.8 * covariance), Vector(2.8 * covariance, 2.8 * variance))))
      }
      MH.Model(Vector(MH.Observable("x", x)(identity), MH.Observable("y", y)(identity)), proposal)
    }
  }

  /** Print CSV comparisons; no files are written and timing thresholds are not asserted.
    * @param args repetitions (default 20), maximum draws per chain (default 12000), workers (default 4)
    * @return Unit; emits truth, error, interval coverage, stopping status, ESS/s, acceptance and API time
    * @throws IllegalArgumentException for invalid budgets or leaked workers; NumberFormatException for malformed integers
    * @example `BlockedProposalBenchmark.main(Array("50", "12000", "4"))`
    */
  def main(args: Array[String]): Unit = {
    require(args.length <= 3, "Arguments: repetitions maximumDrawsPerChain workers")
    val repeats = args.headOption.map(_.toInt).getOrElse(20)
    val maximum = args.lift(1).map(_.toInt).getOrElse(12000)
    val workers = args.lift(2).map(_.toInt).getOrElse(4)
    require(repeats > 0 && maximum >= 2000 && workers >= 1 && workers <= 4, "Invalid benchmark budget")
    val policy = McmcPrecision.Config(relativeTolerance = 0.15, minDrawsPerChain = 2000, checkEvery = 2000)
    println("blocked,workload,strategy,round,seed,method,query,truth,estimate,error,fullWidth,covered,criteriaMet,reason,drawsPerChain,checks,apiSeconds,rHat,bulkEss,meanEss,meanEssPerSecond,acceptance")
    for (round <- -2 until repeats) {
      // Rotate strategy order; alternate fixed/adaptive execution; exclude two warm-up rounds.
      val offset = math.floorMod(round, cases.size)
      for ((workload, strategy) <- cases.drop(offset) ++ cases.take(offset)) {
        val config = MH.Config(chains = 4, drawsPerChain = maximum, warmUp = 2000,
          parallelism = workers, seed = 62003L + round * 7919L)
        var fixed: MH.Result = null
        var adaptive: MH.StoppedResult = null
        if (round % 2 == 0) {
          fixed = MH.run(config)(model(workload, strategy))
          adaptive = MH.runUntilPrecise(config, policy)(model(workload, strategy))
        } else {
          adaptive = MH.runUntilPrecise(config, policy)(model(workload, strategy))
          fixed = MH.run(config)(model(workload, strategy))
        }
        for ((method, result, assessments, reason, checks) <- Vector(
          ("fixed", fixed, fixed.diagnostics.keys.map(k =>
            k -> McmcPrecision.evaluate(fixed.chains.map(_.draws(k)), policy, 2)).toMap, "FixedBudget", 1),
          ("adaptive", adaptive.result, adaptive.assessments, adaptive.reason.toString, adaptive.checks))) {
          val acceptance = result.chains.map(_.acceptanceRate).sum / 4
          for (query <- result.diagnostics.keys.toVector.sorted) {
            val a = assessments(query)
            val d = a.diagnostics
            val truth = if (query == "positive") 0.5 else 0.0
            val error = d.mean - truth
            val width = a.fullWidth.getOrElse(Double.NaN)
            val covered = a.fullWidth.exists(w => math.abs(error) <= w / 2)
            println(s"blocked,$workload,$strategy,$round,${config.seed},$method,$query,$truth,${d.mean},$error,$width,$covered,${a.criteriaMet},$reason,${result.chains.head.draws(query).size},$checks,${result.elapsedSeconds},${d.rHat.getOrElse(Double.NaN)},${d.bulkEss.getOrElse(Double.NaN)},${d.meanEss.getOrElse(Double.NaN)},${d.meanEss.map(_ / result.elapsedSeconds).getOrElse(Double.NaN)},$acceptance")
          }
        }
        require(!Thread.getAllStackTraces.keySet().asScala.exists(_.getName.startsWith("figaro-mcmc-worker-")), "Worker leaked")
      }
    }
  }
}
