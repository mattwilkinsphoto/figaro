package com.cra.figaro.example

import com.cra.figaro.algorithm.sampling.VectorSliceSampler as VS
import com.cra.figaro.algorithm.sampling.parallel.MultiChainVectorSliceSampler as MC
import java.lang.management.ManagementFactory
import java.nio.ByteBuffer
import java.security.MessageDigest
import scala.jdk.CollectionConverters.*
import scala.util.control.NonFatal

/** Fixed-trace end-to-end scheduling study; see docs/VECTOR_SAMPLING_PERFORMANCE.md. */
object VectorSamplingPerformance {
  private val fixtures = Vector("gaussian8", "gaussian32", "correlated32", "positive32", "likelihood8", "mixture8")
  private def dimension(kind: String): Int = if (kind.endsWith("32")) 32 else 8
  private def density(kind: String): Vector[Double] => Double = {
    val design = if (kind == "likelihood8") Vector.tabulate(64, 8)((i, j) =>
      (if (Integer.bitCount((i % 8) & j) % 2 == 0) 1.0 else -1.0) / math.sqrt(8)) else Vector.empty
    x => kind match {
      case "gaussian8" | "gaussian32" => -0.5 * x.map(v => v * v).sum
      case "correlated32" =>
        val mean = x.sum / x.size
        -0.5 * (x.map(v => math.pow(v - mean, 2)).sum / 0.05 + x.size * mean * mean / (0.05 + 0.95 * x.size))
      case "positive32" => if (x.forall(_ > 0)) -x.sum else Double.NegativeInfinity
      case "likelihood8" =>
        var total = x.map(v => v * v).sum
        for (row <- design) {
          var product = 0.0
          var j = 0
          while (j < 8) { product += row(j) * x(j); j += 1 }
          total += product * product
        }
        -0.5 * total
      case "mixture8" =>
        val a = math.log(0.9) - 2 * x.map(v => math.pow(v + 2, 2)).sum
        val b = math.log(0.1) - 2 * x.map(v => math.pow(v - 3, 2)).sum
        val high = math.max(a, b)
        high + math.log1p(math.exp(math.min(a, b) - high))
    }
  }
  private def fingerprint(result: MC.Result): String = {
    val hash = MessageDigest.getInstance("SHA-256")
    val bytes = ByteBuffer.allocate(8)
    def number(n: Long): Unit = { bytes.clear(); bytes.putLong(n); hash.update(bytes.array()) }
    def real(x: Double): Unit = number(java.lang.Double.doubleToLongBits(x))
    def string(x: String): Unit = {
      val b = x.getBytes(java.nio.charset.StandardCharsets.UTF_8)
      number(b.length); hash.update(b)
    }
    result.chains.foreach { c =>
      number(c.index); number(c.seed); number(c.result.evaluations); number(c.result.warmUpCompleted)
      string(c.result.reason.toString); number(c.result.samples.size)
      c.result.samples.foreach(_.foreach(real)); c.result.lastState.foreach(real)
    }
    number(result.diagnosticDrawsPerChain)
    result.diagnostics.foreach { d =>
      real(d.mean); real(d.standardDeviation)
      Vector(d.rHat, d.bulkEss, d.tailEss, d.meanEss, d.mcseMean).foreach(v => real(v.getOrElse(Double.NaN)))
      number(d.warnings.size); d.warnings.foreach(string)
    }
    number(result.warnings.size); result.warnings.foreach(string)
    hash.digest().map(b => f"${b & 0xff}%02x").mkString
  }
  /** Execute all predeclared fixtures and methods; emit complete quoted CSV, including failures.
    * @param args optional repetitions (5, positive <= 100), draws per chain (4000, 4-100000), warm-up (500, 0-100000)
    * @return Unit; emits two negative warm-up rounds and all measured rounds; malformed input/trace mismatch throws;
    *         runtime failures emit failed rows, interruption aborts
    * @example `VectorSamplingPerformance.main(Array("1", "100", "20"))`
    */
  def main(args: Array[String]): Unit = {
    require(args.length <= 3)
    val repeats = args.headOption.map(_.toInt).getOrElse(5)
    val draws = args.lift(1).map(_.toInt).getOrElse(4000)
    val warm = args.lift(2).map(_.toInt).getOrElse(500)
    require(repeats > 0 && repeats <= 100 && draws >= 4 && draws <= 100000 && warm >= 0 && warm <= 100000)
    val os = ManagementFactory.getOperatingSystemMXBean.asInstanceOf[com.sun.management.OperatingSystemMXBean]
    def gc: Long = ManagementFactory.getGarbageCollectorMXBeans.asScala.map(_.getCollectionTime).filter(_ >= 0).sum
    def csv(xs: Any*): Unit = println(xs.map(x => "\"" + x.toString.replace("\"", "\"\"") + "\"").mkString(","))
    csv("vectorPerformance", "fixture", "method", "workers", "round", "seed", "draws", "warmUp", "status",
      "wallSeconds", "constructionSeconds", "samplingSeconds", "diagnosticsSeconds", "cpuSeconds", "gcSeconds",
      "evaluations", "alignedDraws", "minMeanEss", "minBulkEss", "minTailEss", "maxRHat", "maxMeanError",
      "warningCoordinates", "fingerprint", "error")
    for (kind <- fixtures; method <- VS.Method.values; round <- -2 until repeats) {
      val seed = 420013L + 7919L * round
      val workers = Vector(1, 2, 4)
      val offset = Math.floorMod(round, 3)
      val rotated = workers.drop(offset) ++ workers.take(offset)
      val order = if (round % 2 == 0) rotated else rotated.reverse
      var reference: Option[String] = None
      for (worker <- order) {
        val cpuStart = os.getProcessCpuTime; val gcStart = gc
        val measured = try {
          val output = MC.measuredRun(MC.Config(VS.Config(method, draws = draws, warmUp = warm,
            seed = seed, maxEvaluations = 100000000), parallelism = worker)) { (i, _) =>
            MC.Model(Vector.fill(dimension(kind))(0.5 + i / 4.0), density(kind))
          }
          Right(output)
        } catch {
          case e: InterruptedException => throw e
          case e: MC.ChainFailure if e.getCause.isInstanceOf[InterruptedException] => throw e.getCause
          case NonFatal(e) => Left(e.getClass.getSimpleName + ":" + Option(e.getMessage).getOrElse(""))
        }
        val cpuEnd = os.getProcessCpuTime
        val cpu = if (cpuStart < 0 || cpuEnd < 0) Double.NaN else (cpuEnd - cpuStart) / 1e9
        val gcSeconds = (gc - gcStart) / 1000.0
        measured match {
          case Left(error) => csv("vectorPerformance", kind, method, worker, round, seed, draws, warm, "Failed",
            Double.NaN, Double.NaN, Double.NaN, Double.NaN, cpu, gcSeconds, -1, -1,
            Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, -1, "", error)
          case Right((r, t)) =>
            val hash = fingerprint(r)
            reference.foreach(previous => require(previous == hash, s"Worker-dependent output: $kind/$method/$round"))
            reference = Some(hash)
            def minimum(f: com.cra.figaro.algorithm.sampling.parallel.McmcDiagnostics.Summary => Option[Double]): Double = {
              val values = r.diagnostics.map(f)
              if (values.nonEmpty && values.forall(_.isDefined)) values.flatten.min else Double.NaN
            }
            val rhats = r.diagnostics.map(_.rHat)
            val rhat = if (rhats.nonEmpty && rhats.forall(_.isDefined)) rhats.flatten.max else Double.NaN
            val truth = if (kind == "positive32") 1.0 else if (kind == "mixture8") -1.5 else 0.0
            val error = if (r.diagnostics.isEmpty) Double.NaN else r.diagnostics.map(d => math.abs(d.mean - truth)).max
            csv("vectorPerformance", kind, method, worker, round, seed, draws, warm,
              if (r.chains.forall(_.result.reason == VS.StopReason.DrawsReached)) "Complete" else "Incomplete",
              r.elapsedSeconds, t.constructionSeconds, t.samplingAndShutdownSeconds, t.diagnosticsSeconds,
              cpu, gcSeconds, r.chains.map(_.result.evaluations).sum, r.diagnosticDrawsPerChain,
              minimum(_.meanEss), minimum(_.bulkEss), minimum(_.tailEss), rhat, error,
              r.diagnostics.count(_.warnings.nonEmpty), hash, r.warnings.mkString("|"))
        }
      }
    }
  }
}
