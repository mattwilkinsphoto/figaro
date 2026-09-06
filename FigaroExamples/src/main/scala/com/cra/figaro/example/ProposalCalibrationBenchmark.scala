package com.cra.figaro.example

import com.cra.figaro.algorithm.sampling.{GaussianBlockCalibration as Calibration, GaussianBlockProposal, ProposalScheme}
import com.cra.figaro.algorithm.sampling.parallel.{McmcPrecision, MultiChainMetropolisHastings as MH}
import com.cra.figaro.language.*
import com.cra.figaro.library.atomic.continuous.Normal
import scala.jdk.CollectionConverters.*

/** Broader-geometry comparisons with analytic first/second moments, including the cost of pilot rejection. */
object ProposalCalibrationBenchmark {
  private case class Geometry(name: String, scales: Vector[Double], rho: Double, banana: Boolean = false) {
    val dimension: Int = scales.size
    val names: Vector[String] = scales.indices.map(i => s"x$i").toVector
    def variance(i: Int): Double = scales(i) * scales(i) * (if (banana && i == 1) 0.57 else 1.0)
    def covariance: Vector[Vector[Double]] = Vector.tabulate(dimension, dimension) { (i, j) =>
      if (i == j) variance(i) else if (banana) 0.0 else scales(i) * scales(j) * rho
    }
  }
  private val geometries = Vector(
    Geometry("independent-2", Vector(1.0, 1.0), 0),
    Geometry("correlated-2", Vector(1.0, 1.0), 0.5),
    Geometry("narrow-2", Vector(1.0, 1.0), 0.98),
    Geometry("scaled-2", Vector(0.02, 20.0), 0.9),
    Geometry("correlated-6", Vector.fill(6)(1.0), 0.8),
    Geometry("banana-2", Vector(1.0, 1.0), 0, banana = true))
  private val strategies = Vector("default", "joint-prior", "manual", "calibrated")

  private def model(g: Geometry, strategy: String, fit: Option[Calibration.Fit])(u: Universe, index: Int): MH.Model = {
    val xs = g.scales.map(s => Normal(0, s * s)(using "", u))
    val all = Inject(xs*)(using "", u)
    all.addLogConstraint { (values: List[Double]) =>
      val z = values.zip(g.scales).map((v, s) => v / s)
      val prior = z.map(v => v * v).sum
      val target = if (g.banana) z.head * z.head + math.pow((z(1) - 0.4 * (z.head * z.head - 1)) / 0.5, 2)
      else (prior - g.rho * math.pow(z.sum, 2) / (1 + (g.dimension - 1) * g.rho)) / (1 - g.rho)
      0.5 * (prior - target)
    }
    val proposal = strategy match {
      case "default" => None
      case "joint-prior" => Some(ProposalScheme(xs*))
      case "manual" => Some(GaussianBlockProposal(xs, g.covariance))
      case "calibrated" => Some(fit.get.proposal(g.names.zip(xs).toMap))
    }
    val queries = xs.indices.toVector.flatMap(i => Vector(MH.Observable(g.names(i), xs(i))(identity),
      MH.Observable(s"square$i", xs(i))(v => v * v)))
    MH.Model(queries, proposal)
  }
  private def csv(values: Any*): Unit = println(values.map(v => "\"" + v.toString.replace("\"", "\"\"") + "\"").mkString(","))

  /** Print CSV; rotated strategy order, one excluded JVM warm-up round, and explicit pilot-inclusive timing.
    * @param args repetitions (20), production cap per chain (12000), workers (4), pilot draws per chain (6000)
    * @return Unit; each row reports analytic truth, coverage, ESS/s, stopping, pilot status/cost and frozen matrix
    * @throws IllegalArgumentException for invalid budgets or leaked workers; parsing/model errors are not suppressed
    * @example `ProposalCalibrationBenchmark.main(Array("20", "12000", "4", "6000"))`
    */
  def main(args: Array[String]): Unit = {
    require(args.length <= 4, "Arguments: repetitions productionDraws workers pilotDraws")
    val repeats = args.headOption.map(_.toInt).getOrElse(20)
    val cap = args.lift(1).map(_.toInt).getOrElse(12000)
    val workers = args.lift(2).map(_.toInt).getOrElse(4)
    val pilotDraws = args.lift(3).map(_.toInt).getOrElse(6000)
    require(repeats > 0 && cap >= 2000 && workers >= 1 && workers <= 4 && pilotDraws >= 500, "Invalid benchmark budget")
    val precision = McmcPrecision.Config(relativeTolerance = 0.15, minDrawsPerChain = 2000, checkEvery = 2000)
    csv("calibration", "geometry", "strategy", "round", "seed", "method", "query", "truth", "estimate", "error",
      "fullWidth", "covered", "criteriaMet", "reason", "drawsPerChain", "productionSeconds", "pilotSeconds", "totalSeconds",
      "rHat", "meanEss", "meanEssPerTotalSecond", "acceptance", "pilotStatus", "covariance")
    for (round <- -1 until repeats; g <- geometries) {
      val seed = 97103L + round * 7919L
      val pilotStart = System.nanoTime()
      val p = MH.run(MH.Config(drawsPerChain = pilotDraws, warmUp = 2000, parallelism = workers, seed = seed ^ 0x5deece66dL))(
        model(g, "joint-prior", None))
      var rejection = ""
      val fit = try Some(Calibration.fit(p, g.names)) catch {
        case e: IllegalArgumentException => rejection = e.getMessage; None
      }
      val pilotSeconds = (System.nanoTime() - pilotStart) / 1e9
      val offset = math.floorMod(round, strategies.size)
      for (strategy <- strategies.drop(offset) ++ strategies.take(offset)) {
        val config = MH.Config(drawsPerChain = cap, warmUp = 2000, parallelism = workers, seed = seed)
        val methods = if (round % 2 == 0) Vector("fixed", "precision") else Vector("precision", "fixed")
        for (method <- methods) {
          val cost = if (strategy == "calibrated") pilotSeconds else 0.0
          if (strategy == "calibrated" && fit.isEmpty) {
            csv("calibration", g.name, strategy, round, seed, method, "", "", "", "", "", false, false,
              "PilotRejected", 0, 0, cost, cost, "", "", "", "", rejection, "")
          } else {
            val (result, assessments, reason) = if (method == "fixed") {
              val r = MH.run(config)(model(g, strategy, fit))
              (r, r.diagnostics.keys.map(k => k -> McmcPrecision.evaluate(r.chains.map(_.draws(k)), precision, 2 * g.dimension)).toMap, "FixedBudget")
            } else {
              val r = MH.runUntilPrecise(config, precision)(model(g, strategy, fit))
              (r.result, r.assessments, r.reason.toString)
            }
            for (query <- assessments.keys.toVector.sorted) {
              val a = assessments(query); val d = a.diagnostics
              val truth = if (query.startsWith("square")) g.variance(query.stripPrefix("square").toInt) else 0.0
              val error = d.mean - truth
              csv("calibration", g.name, strategy, round, seed, method, query, truth, d.mean, error,
                a.fullWidth.getOrElse(Double.NaN), a.fullWidth.exists(w => math.abs(error) <= w / 2), a.criteriaMet,
                reason, result.chains.head.draws(query).size, result.elapsedSeconds, cost, result.elapsedSeconds + cost,
                d.rHat.getOrElse(Double.NaN), d.meanEss.getOrElse(Double.NaN),
                d.meanEss.map(_ / (result.elapsedSeconds + cost)).getOrElse(Double.NaN),
                result.chains.map(_.acceptanceRate).sum / config.chains,
                if (strategy == "calibrated") "Accepted" else "NotUsed",
                if (strategy == "calibrated") fit.get.covariance.map(_.mkString("|")).mkString("/") else "")
            }
          }
        }
      }
      require(!Thread.getAllStackTraces.keySet().asScala.exists(_.getName.startsWith("figaro-mcmc-worker-")), "Worker leaked")
    }
  }
}
