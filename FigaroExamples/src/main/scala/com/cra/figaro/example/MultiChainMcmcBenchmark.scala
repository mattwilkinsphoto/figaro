package com.cra.figaro.example

import com.cra.figaro.algorithm.sampling.parallel.MultiChainMetropolisHastings.*
import com.cra.figaro.language.*
import com.cra.figaro.library.atomic.continuous.Normal
import java.lang.management.ManagementFactory

/** Fixed-chain-count scaling benchmark. Timings include diagnostics and cleanup, not just transitions. */
object MultiChainMcmcBenchmark {
  private def model(kind: String, u: Universe): Model = {
    val x = Normal(0.0, 1.0)(using "", u)
    if (kind == "likelihood") x.addLogConstraint((v: Double) => -0.5 * (v - 1) * (v - 1))
    val query = if (kind == "correlated") {
      val y = Normal(0.0, 1.0)(using "", u)
      val difference = Apply(x, y, (a: Double, b: Double) => a - b)(using "", u)
      difference.addLogConstraint((d: Double) => -50 * d * d)
      x
    } else if (kind == "wide") {
      val rest = List.fill(31)(Normal(0.0, 1.0)(using "", u))
      Apply(Inject((x :: rest)*)(using "", u), (xs: List[Double]) => xs.sum)(using "", u)
    } else x
    Model(Vector(Observable("x", query)(identity)))
  }
  /** Print CSV; args: workload normal/likelihood/correlated/wide, draws per chain,
    * measured repeats, comma-separated workers, chain count. Defaults: normal 20000 3 1,2,4 4.
    * Two negative-index rounds warm the JVM; invalid inputs throw IllegalArgumentException.
    * Returns Unit. Example: `MultiChainMcmcBenchmark.main(Array("wide", "20000", "5", "1,2,4", "4"))`.
    */
  def main(args: Array[String]): Unit = {
    val kind = args.headOption.getOrElse("normal")
    val draws = args.lift(1).map(_.toInt).getOrElse(20000)
    val repeats = args.lift(2).map(_.toInt).getOrElse(3)
    val workers = args.lift(3).getOrElse("1,2,4").split(",").map(_.toInt).toVector
    val chains = args.lift(4).map(_.toInt).getOrElse(4)
    require(Set("normal", "likelihood", "correlated", "wide")(kind) && repeats > 0, "Invalid benchmark arguments")
    val config = Config(chains = chains, drawsPerChain = draws, warmUp = 2000)
    require(workers.nonEmpty && workers.forall(w => w > 0 && w <= chains), "Workers must be between one and chain count")
    val os = ManagementFactory.getOperatingSystemMXBean.asInstanceOf[com.sun.management.OperatingSystemMXBean]
    println("workload,workers,chains,drawsPerChain,round,wallMs,cpuMs,sumChainMs,meanEss,meanEssPerSecond,rHat,absoluteError")
    for (round <- -2 until repeats; worker <- (if (round % 2 == 0) workers else workers.reverse)) {
      val cpu = os.getProcessCpuTime
      val result = run(config.copy(parallelism = worker, seed = 721L + round))((u, _) => model(kind, u))
      val cpuMs = (os.getProcessCpuTime - cpu) / 1e6
      val d = result.diagnostics("x")
      val ess = d.meanEss.getOrElse(Double.NaN)
      val error = math.abs(d.mean - (if (kind == "likelihood") 0.5 else 0.0))
      println(s"$kind,$worker,$chains,$draws,$round,${result.elapsedSeconds * 1000},$cpuMs,${result.chains.map(_.samplingSeconds).sum * 1000},$ess,${ess / result.elapsedSeconds},${d.rHat.getOrElse(Double.NaN)},$error")
    }
  }
}
