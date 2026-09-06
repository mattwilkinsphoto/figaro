package com.cra.figaro.example

import com.cra.figaro.algorithm.sampling.{GaussianBlockCalibration as Calibration, GaussianBlockProposal, ProposalScheme}
import com.cra.figaro.algorithm.sampling.parallel.{McmcPrecision, MultiChainMetropolisHastings as MH}
import com.cra.figaro.language.*
import com.cra.figaro.library.atomic.continuous.Normal
import org.apache.commons.math3.distribution.NormalDistribution
import scala.jdk.CollectionConverters.*

/** Matched-prefix reliability audit on a curved target with independently known moments. */
object McmcReliabilityValidation {
  private val normal = new NormalDistribution(0, 1)
  private val tail = 2 * normal.cumulativeProbability(-2)
  private val names = Vector("x", "y", "xSquared", "ySquared", "xTail")
  private val truths = Vector(0.0, 0.0, 1.0, 0.57, tail)
  // Var(y^2) = E[y^4] - E[y^2]^2 = (1.536 + 0.48 + 0.1875) - 0.57^2.
  private val variances = Vector(1.0, 0.57, 2.0, 1.8786, tail * (1 - tail))
  private val strategies = Vector("iid", "reparameterized", "default", "joint-prior", "manual", "calibrated")
  private val policy = McmcPrecision.Config(relativeTolerance = 0.15, minDrawsPerChain = 2000, checkEvery = 2000)

  private def model(strategy: String, fit: Option[Calibration.Fit])(u: Universe, index: Int): MH.Model = {
    val x = Normal(0, 1)(using "", u)
    val second = Normal(0, 1)(using "", u)
    val y: Element[Double] = if (strategy == "reparameterized")
      Apply(x, second, (z: Double, e: Double) => 0.4 * (z * z - 1) + 0.5 * e)(using "", u)
    else {
      val pair = Inject(x, second)(using "", u)
      pair.addLogConstraint { (v: List[Double]) =>
        val residual = (v(1) - 0.4 * (v.head * v.head - 1)) / 0.5
        0.5 * (v(1) * v(1) - residual * residual)
      }
      second
    }
    val proposal = strategy match {
      case "default" => None
      case "joint-prior" | "reparameterized" => Some(ProposalScheme(x, second))
      case "manual" => Some(GaussianBlockProposal(Vector(x, second), Vector(Vector(1.0, 0.0), Vector(0.0, 0.57))))
      case "calibrated" => Some(fit.get.proposal(Map("x" -> x, "y" -> second)))
    }
    MH.Model(Vector(MH.Observable("x", x)(identity), MH.Observable("y", y)(identity),
      MH.Observable("xSquared", x)(v => v * v), MH.Observable("ySquared", y)(v => v * v),
      MH.Observable("xTail", x)(v => if (math.abs(v) > 2) 1.0 else 0.0)), proposal)
  }
  private def independent(n: Int, seed: Long): Vector[Map[String, Vector[Double]]] = {
    val seeds = new java.util.SplittableRandom(seed)
    Vector.fill(4) {
      val rng = new java.util.Random(seeds.nextLong())
      val rows = Vector.fill(n) {
        val x = rng.nextGaussian(); val y = 0.4 * (x * x - 1) + 0.5 * rng.nextGaussian()
        Vector(x, y, x * x, y * y, if (math.abs(x) > 2) 1.0 else 0.0)
      }
      names.indices.map(j => names(j) -> rows.map(_(j))).toMap
    }
  }
  private def csv(values: Any*): Unit = println(values.map(v => "\"" + v.toString.replace("\"", "\"\"") + "\"").mkString(","))

  /** Print all outcomes, including pilot rejections; no timing speedups are inferred from prefix replay.
    * @param args repetitions (60), maximum draws per chain (48000, multiple of 2000, at least 12000), workers (4),
    *             optional comma-separated strategy subset for independent audit shards
    * @return Unit; quoted CSV of matched fixed/stopped assessments, errors, coverage and MCSE comparisons
    * @throws IllegalArgumentException for invalid budgets; model failures propagate rather than being omitted
    * @example `McmcReliabilityValidation.main(Array("60", "48000", "4"))`
    */
  def main(args: Array[String]): Unit = {
    require(args.length <= 4, "Arguments: repetitions maximumDrawsPerChain workers [strategies]")
    val repeats = args.headOption.map(_.toInt).getOrElse(60)
    val maximum = args.lift(1).map(_.toInt).getOrElse(48000)
    val workers = args.lift(2).map(_.toInt).getOrElse(4)
    val selected = args.lift(3).map(_.split(",").toVector).getOrElse(strategies)
    require(selected.nonEmpty && selected.distinct.size == selected.size && selected.forall(strategies.contains), "Invalid strategies")
    require(repeats > 0 && maximum >= 12000 && maximum % 2000 == 0 && workers > 0 && workers <= 4, "Invalid audit budget")
    csv("reliability", "strategy", "rule", "round", "seed", "method", "draws", "query", "truth", "estimate", "error",
      "fullWidth", "covered", "criteriaMet", "allCriteriaMet", "reason", "batchMcse", "spectralMcse", "iidOracleMcse",
      "iidOracleCovered", "rHat", "bulkEss", "meanEss", "tailEss", "acceptance", "pilotStatus", "failureReasons")
    for (round <- 0 until repeats; strategy <- selected) {
      val seed = 141011L + round * 7919L
      var pilotStatus = "NotUsed"
      val fit = if (strategy != "calibrated") None else {
        val p = MH.run(MH.Config(drawsPerChain = 6000, warmUp = 2000, parallelism = workers, seed = seed ^ 0x5deece66dL))(
          model("joint-prior", None))
        try { val f = Calibration.fit(p, Vector("x", "y")); pilotStatus = "Accepted"; Some(f) }
        catch { case e: IllegalArgumentException => pilotStatus = e.getMessage; None }
      }
      if (strategy == "calibrated" && fit.isEmpty) {
        for (rule <- Vector("legacy-batch", "mcse-floor"))
          csv("reliability", strategy, rule, round, seed, "rejected", 0, "", "", "", "", "", false, false, false,
            "PilotRejected", "", "", "", "", "", "", "", "", "", pilotStatus, "")
      } else {
        var acceptance = 1.0
        val traces = if (strategy == "iid") independent(maximum, seed) else {
          val r = MH.run(MH.Config(drawsPerChain = maximum, warmUp = 2000, parallelism = workers, seed = seed))(model(strategy, fit))
          acceptance = r.chains.map(_.acceptanceRate).sum / r.chains.size
          r.chains.map(_.draws)
        }
        var stopped = Set.empty[String]
        val fixedBudgets = Set(2000, 12000, maximum)
        for (count <- 2000 to maximum by 2000 if stopped.size < 2 || fixedBudgets(count)) {
          val assessments = names.map(k => McmcPrecision.evaluate(traces.map(_(k).take(count)), policy, names.size))
          // Replay the former rule explicitly on the SAME traces; no old sampler or production code is retained.
          val critical = -normal.inverseCumulativeProbability(0.05 / (2 * names.size))
          val legacy = assessments.map { a =>
            val width = a.batchMeansMcse.map(2 * critical * _).filter(_.isFinite)
            val d = a.diagnostics
            val met = count >= policy.minDrawsPerChain && a.batchesPerChain >= policy.minBatches &&
              d.rHat.exists(r => r.isFinite && r <= policy.maxRHat) &&
              d.bulkEss.exists(_ >= 4 * policy.minEssPerChain) && d.meanEss.exists(_ >= 4 * policy.minEssPerChain) &&
              a.targetWidth.isFinite && a.targetWidth > 0 && a.penalty.isFinite && width.exists(_ + a.penalty <= a.targetWidth)
            a.copy(fullWidth = width, criteriaMet = met, failureReasons = Vector.empty)
          }
          for ((rule, values) <- Vector("legacy-batch" -> legacy, "mcse-floor" -> assessments)) {
            val all = values.forall(_.criteriaMet)
            val stopHere = !stopped(rule) && (all || count == maximum)
            val methods = (if (fixedBudgets(count)) Vector("fixed") else Vector.empty) ++ (if (stopHere) Vector("stopped") else Vector.empty)
            for (method <- methods; q <- names.indices) {
            val a = values(q); val d = a.diagnostics
            val error = d.mean - truths(q)
            val isIid = strategy == "iid" || strategy == "reparameterized"
            val oracle = math.sqrt(variances(q) / (4.0 * count))
            csv("reliability", strategy, rule, round, seed, method, count, names(q), truths(q), d.mean, error,
              a.fullWidth.getOrElse(Double.NaN), a.fullWidth.exists(w => math.abs(error) <= w / 2), a.criteriaMet, all,
              if (method == "fixed") "FixedBudget" else if (all) "PrecisionReached" else "MaxDrawsReached",
              a.batchMeansMcse.getOrElse(Double.NaN), d.mcseMean.getOrElse(Double.NaN),
              if (isIid) oracle else Double.NaN, if (isIid) (math.abs(error) <= critical * oracle).toString else "",
              d.rHat.getOrElse(Double.NaN), d.bulkEss.getOrElse(Double.NaN), d.meanEss.getOrElse(Double.NaN),
              d.tailEss.getOrElse(Double.NaN), acceptance, pilotStatus, a.failureReasons.mkString("|"))
            }
            if (stopHere) stopped += rule
          }
        }
      }
      require(!Thread.getAllStackTraces.keySet().asScala.exists(_.getName.startsWith("figaro-mcmc-worker-")), "Worker leaked")
    }
  }
}
