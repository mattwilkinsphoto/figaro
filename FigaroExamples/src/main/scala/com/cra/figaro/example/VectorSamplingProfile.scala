package com.cra.figaro.example

import java.nio.file.{Files, Path}
import java.time.Duration
import jdk.jfr.Recording
import jdk.jfr.consumer.RecordingFile
import scala.collection.mutable
import scala.jdk.CollectionConverters.*

/** Opt-in JDK 17 allocation/GC investigation; never enabled by a library sampler. */
object VectorSamplingProfile {
  /** Record the unchanged full vector benchmark and emit sanitized aggregate profile CSV.
    * @param args new JFR file in an existing directory, optional repetitions (5), draws (4000),
    *             warm-up (500), and optional Windows ACL PowerShell script called on the new file
    * @return Unit; benchmark CSV plus profile CSV on stdout; existing output and invalid inputs fail
    * @example `VectorSamplingProfile.main(Array("profile.jfr", "1", "100", "20"))`
    */
  def main(args: Array[String]): Unit = {
    require(args.length >= 1 && args.length <= 5, "Expected new JFR path, repetitions, draws, warm-up, optional ACL script")
    val repeats = args.lift(1).map(_.toInt).getOrElse(5)
    val draws = args.lift(2).map(_.toInt).getOrElse(4000)
    val warm = args.lift(3).map(_.toInt).getOrElse(500)
    require(repeats > 0 && repeats <= 100 && draws >= 4 && draws <= 100000 && warm >= 0 && warm <= 100000)
    val path = Path.of(args(0)).toAbsolutePath.normalize()
    require(Files.isDirectory(path.getParent), "Output parent must already exist")
    Files.createFile(path) // Exclusive reservation: never overwrite someone else's recording.
    args.lift(4).foreach { script =>
      val process = new ProcessBuilder("pwsh.exe", "-NoProfile", "-File",
        Path.of(script).toAbsolutePath.toString, "-Paths", path.toString).inheritIO().start()
      require(process.waitFor() == 0, "Profile output ACL hook failed")
    }
    val recording = new Recording()
    var primary: Throwable = null
    var started = false
    try {
      // Explicit allowlist excludes environment variables, JVM arguments, file and network events.
      recording.enable("jdk.ObjectAllocationSample").withStackTrace().`with`("throttle", "300/s")
      recording.enable("jdk.ExecutionSample").withPeriod(Duration.ofMillis(10))
      recording.enable("jdk.GarbageCollection").withThreshold(Duration.ZERO)
      recording.enable("jdk.GCHeapSummary")
      recording.enable("jdk.DataLoss")
      recording.start(); started = true
      VectorSamplingPerformance.main(Array(repeats.toString, draws.toString, warm.toString))
    } catch {
      case e: Throwable => primary = e; throw e
    } finally {
      try {
        if (started) { recording.stop(); recording.dump(path) }
      } catch {
        case e: Throwable => if (primary == null) throw e else if (e ne primary) primary.addSuppressed(e)
      } finally recording.close()
    }
    // Recording metadata is read from the file below; this phase is outside recorded benchmark work.
    summarize(path)
  }

  private def summarize(path: Path): Unit = {
    def className(name: String): String =
      name.replace('/', '.').replaceAll("\\+0x[0-9a-fA-F]+(?:\\.\\d+)?", "")
    val allocations = mutable.Map.empty[(String, String, String), (Long, Long)]
    val execution = mutable.Map.empty[(String, String, String), Long]
    var gcCount = 0L
    var pauseNanos = 0L
    var maxPauseNanos = 0L
    var heapCount = 0L
    var maxHeap = 0L
    var maxAfterGcHeap = 0L
    var lost = 0L
    var first: java.time.Instant = null
    var last: java.time.Instant = null
    val input = new RecordingFile(path)
    try {
      while (input.hasMoreEvents) {
        val e = input.readEvent()
        if (first == null || e.getStartTime.isBefore(first)) first = e.getStartTime
        if (last == null || e.getEndTime.isAfter(last)) last = e.getEndTime
        e.getEventType.getName match {
          case "jdk.ObjectAllocationSample" | "jdk.ExecutionSample" =>
            val frames = Option(e.getStackTrace).toVector.flatMap(_.getFrames.asScala).map { f =>
              (className(f.getMethod.getType.getName), f.getMethod.getName, f.getLineNumber)
            }
            val diagnostic = frames.find(_._1.startsWith("com.cra.figaro.algorithm.sampling.parallel.McmcDiagnostics"))
            val sampler = frames.find(_._1.startsWith("com.cra.figaro.algorithm.sampling.VectorSliceSampler"))
            val group = if (diagnostic.nonEmpty) "diagnostics" else if (sampler.nonEmpty) "sampling"
              else if (frames.isEmpty) "unknown" else "other"
            val site = diagnostic.orElse(sampler).orElse(frames.headOption)
              .map((owner, method, line) => s"$owner.$method:$line").getOrElse("unknown")
            if (e.getEventType.getName == "jdk.ObjectAllocationSample") {
              val key = (group, className(e.getClass("objectClass").getName), site)
              val (count, weight) = allocations.getOrElse(key, (0L, 0L))
              allocations(key) = (count + 1L, Math.addExact(weight, e.getLong("weight")))
            } else {
              val leaf = frames.headOption.map((owner, method, _) => s"$owner.$method").getOrElse("unknown")
              val key = (group, leaf, site)
              execution(key) = execution.getOrElse(key, 0L) + 1L
            }
          case "jdk.GarbageCollection" =>
            gcCount += 1
            pauseNanos += e.getDuration("sumOfPauses").toNanos
            maxPauseNanos = math.max(maxPauseNanos, e.getDuration("longestPause").toNanos)
          case "jdk.GCHeapSummary" =>
            heapCount += 1
            maxHeap = math.max(maxHeap, e.getLong("heapUsed"))
            if (e.getString("when") == "After GC") maxAfterGcHeap = math.max(maxAfterGcHeap, e.getLong("heapUsed"))
          case "jdk.DataLoss" => lost += e.getLong("amount")
          case _ => ()
        }
      }
    } finally input.close()
    def csv(xs: Any*): Unit = println(xs.map(x => "\"" + x.toString.replace("\"", "\"\"") + "\"").mkString(","))
    csv("vectorProfile", "kind", "group", "detail", "site", "count", "value")
    def metric(name: String, value: Any): Unit = csv("vectorProfile", "metric", name, "", "", 1, value)
    metric("eventSpanSeconds", if (first == null) 0.0 else Duration.between(first, last).toNanos / 1e9)
    metric("gcCount", gcCount); metric("gcPauseSeconds", pauseNanos / 1e9)
    metric("longestGcPauseSeconds", maxPauseNanos / 1e9)
    metric("heapSummaryCount", heapCount); metric("maxObservedHeapBytes", maxHeap)
    metric("maxObservedAfterGcHeapBytes", maxAfterGcHeap); metric("lostBytes", lost)
    allocations.toVector.sortBy(_._1).foreach { case ((group, cls, site), (count, weight)) =>
      csv("vectorProfile", "allocation", group, cls, site, count, weight)
    }
    execution.toVector.sortBy(_._1).foreach { case ((group, leaf, site), count) =>
      csv("vectorProfile", "execution", group, leaf, site, count, count)
    }
  }
}
