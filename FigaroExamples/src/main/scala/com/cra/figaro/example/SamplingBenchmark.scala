package com.cra.figaro.example

import com.cra.figaro.algorithm.sampling.*
import com.cra.figaro.algorithm.sampling.parallel.ParImportance
import com.cra.figaro.language.*
import com.cra.figaro.library.atomic.continuous.Normal
import java.lang.management.ManagementFactory
import java.util.concurrent.{Callable, Executors}
import scala.jdk.CollectionConverters.*

/** Repeatable workload benchmark, not a pass/fail timing test or a convergence diagnostic. */
object SamplingBenchmark {
  private val os = ManagementFactory.getOperatingSystemMXBean.asInstanceOf[com.sun.management.OperatingSystemMXBean]
  private val threads = ManagementFactory.getThreadMXBean.asInstanceOf[com.sun.management.ThreadMXBean]
  private val pools = ManagementFactory.getMemoryPoolMXBeans.asScala.filter(_.getType == java.lang.management.MemoryType.HEAP)
  private def allocated: Map[Long, Long] = {
    if (!threads.isThreadAllocatedMemorySupported) Map.empty
    else {
      if (!threads.isThreadAllocatedMemoryEnabled) threads.setThreadAllocatedMemoryEnabled(true)
      val ids = threads.getAllThreadIds
      ids.zip(threads.getThreadAllocatedBytes(ids)).filter(_._2 >= 0L).toMap
    }
  }
  private def model(kind: String): Universe = {
    val u = Universe.createNew()
    if (kind == "normal") {
      val inputs = List.fill(32)(Normal(0.0, 1.0))
      Apply(Inject(inputs*), (xs: List[Double]) => xs.sum > 0.0)(using "query", u)
    } else {
      val query = Flip(0.3)(using "query", u)
      if (kind == "evidence") query.addConstraint((value: Boolean) => if (value) 0.8 else 0.2)
      if (kind == "mh") List.fill(31)(Flip(0.5))
    }
    u
  }
  private def expected(kind: String): Double = kind match {
    case "normal" => 0.5
    case "evidence" => 0.24 / 0.38
    case _ => 0.3
  }
  private class TraceMH(u: Universe, count: Int, target: Element[Boolean])
    extends OneTimeMetropolisHastings(u, count, ProposalScheme.default(using u), 1000, 1, target) {
    val trace = new Array[Double](count)
    private var position = 0
    override def sample(): (Boolean, Sample) = {
      val result = super.sample()
      if (result._1) { trace(position) = if (target.value) 1.0 else 0.0; position += 1 }
      result
    }
  }
  // Non-overlapping batch-means ESS, deliberately labelled approximate.
  private def batchEss(xs: Array[Double]): Double = {
    val size = math.sqrt(xs.length.toDouble).toInt.max(1)
    val batches = xs.length / size
    if (batches < 2) Double.NaN
    else {
      val used = xs.take(batches * size)
      val mean = used.sum / used.length
      val variance = used.map(x => (x - mean) * (x - mean)).sum / (used.length - 1)
      val means = used.grouped(size).map(_.sum / size).toArray
      val batchVariance = means.map(x => (x - mean) * (x - mean)).sum / (batches - 1)
      if (variance == 0.0 || batchVariance == 0.0) Double.NaN
      else math.min(used.length.toDouble, used.length * variance / (size * batchVariance))
    }
  }
  private def run(kind: String, workers: Int, count: Int, round: Int, seeded: Boolean): Unit = {
    // This resets the legacy shared RNG; scheduling still changes which worker gets each draw.
    com.cra.figaro.util.random.setSeed(1234567L + round)
    pools.foreach(_.resetPeakUsage())
    val beforeAllocation = allocated
    val cpuStart = os.getProcessCpuTime
    val started = System.nanoTime()
    var estimate = Double.NaN
    var ess = Double.NaN
    var sampleEnd = 0L
    var setupEnd = 0L
    var queryEnd = 0L
    var allocationAtQuery = Map.empty[Long, Long]
    if (kind == "mh") {
      val algorithms = (0 until workers).map { index =>
        val u = model(kind)
        new TraceMH(u, count / workers + (if (index < count % workers) 1 else 0), u.getElementByReference[Boolean]("query"))
      }
      val executor = Executors.newFixedThreadPool(workers)
      setupEnd = System.nanoTime()
      try {
        executor.invokeAll(algorithms.zipWithIndex.map { case (a, index) => new Callable[Unit] {
          def call(): Unit = {
            if (seeded) com.cra.figaro.util.withRandomSeed(1234567L + round * workers + index) { a.start() }
            else a.start()
          }
        }}.asJava).asScala.foreach(_.get())
        sampleEnd = System.nanoTime()
        estimate = algorithms.map(a => a.trace.sum).sum / count
        ess = algorithms.map(a => batchEss(a.trace)).sum
        queryEnd = System.nanoTime()
        allocationAtQuery = allocated
      } finally {
        algorithms.foreach(a => { if (a.isActive) a.kill(); a.universe.clear() })
        executor.shutdown()
      }
    } else if (workers == 1) {
      val u = model(kind)
      val target = u.getElementByReference[Boolean]("query")
      val algorithm = Importance(count, target)(using u)
      setupEnd = System.nanoTime()
      try {
        if (seeded) com.cra.figaro.util.withRandomSeed(1234567L + round) { algorithm.start() }
        else algorithm.start()
        sampleEnd = System.nanoTime()
        estimate = algorithm.probability(target, true)
        queryEnd = System.nanoTime()
        allocationAtQuery = allocated
      } finally { if (algorithm.isActive) algorithm.kill(); u.clear() }
    } else {
      val universes = scala.collection.mutable.ArrayBuffer.empty[Universe]
      val generator = () => { val u = model(kind); universes += u; u }
      val algorithm = if (seeded) ParImportance.seeded(generator, workers, count, 1234567L + round, "query")
        else ParImportance(generator, workers, count, "query")
      setupEnd = System.nanoTime()
      try {
        algorithm.start()
        sampleEnd = System.nanoTime()
        estimate = algorithm.probability[Boolean]("query", true)
        queryEnd = System.nanoTime()
        allocationAtQuery = allocated
      } finally { if (algorithm.isActive) algorithm.kill(); universes.foreach(_.clear()) }
    }
    val ended = System.nanoTime()
    val cpuMs = (os.getProcessCpuTime - cpuStart) / 1e6
    // Snapshot before kill: otherwise terminated private-pool threads disappear from ThreadMXBean.
    val allocation = allocationAtQuery.map { case (id, bytes) => (bytes - beforeAllocation.getOrElse(id, 0L)).max(0L) }.sum
    val peak = pools.map(_.getPeakUsage.getUsed).sum
    if (kind != "mh") {
      if (kind == "evidence") {
        val rawTrueFraction = estimate / (4.0 - 3.0 * estimate)
        val meanWeight = 0.2 + 0.6 * rawTrueFraction
        ess = count * meanWeight * meanWeight / (0.04 + 0.60 * rawTrueFraction)
      } else ess = count.toDouble
    }
    def ms(nanos: Long): Double = nanos / 1e6
    val label = kind + (if (seeded) "-seeded" else "")
    println(f"BENCH,$label,$workers,$count,$round,${ms(setupEnd-started)}%.3f,${ms(sampleEnd-setupEnd)}%.3f,${ms(queryEnd-sampleEnd)}%.3f,${ms(ended-queryEnd)}%.3f,$cpuMs%.3f,$allocation,$peak,$estimate%.8f,${math.abs(estimate-expected(kind))}%.8f,$ess%.1f")
  }
  private def rng(workers: Int, count: Int, round: Int, shared: Boolean): Unit = {
    val executor = Executors.newFixedThreadPool(workers)
    val common = new scala.util.Random(42L)
    val started = System.nanoTime()
    try {
      val results = executor.invokeAll((0 until workers).map { index => new Callable[Double] {
        def call(): Double = {
          val random = if (shared) common else new scala.util.Random(42L + index)
          val n = count / workers + (if (index < count % workers) 1 else 0)
          var total = 0.0
          var i = 0
          while (i < n) { total += random.nextDouble(); i += 1 }
          total
        }
      }}.asJava).asScala.map(_.get()).sum
      println(f"RNG,${if (shared) "shared" else "local"},$workers,$count,$round,${(System.nanoTime()-started)/1e6}%.3f,$results%.5f")
    } finally executor.shutdown()
  }

  /** Run a benchmark grid and print CSV records to standard output.
    * @param args workload (`coin`, `evidence`, `normal`, `mh`, or `rng`), total samples/draws,
    *             measured repeats, comma-separated worker counts, optional `seeded` mode;
    *             defaults: coin, 100000, 3, 1,2,4,8, legacy.
    * @return Unit; invalid arguments throw IllegalArgumentException. Two warm-up rounds are labelled -2/-1.
    * @example `SamplingBenchmark.main(Array("normal", "100000", "3", "1,2,4,8"))`
    */
  def main(args: Array[String]): Unit = {
    val kind = args.headOption.getOrElse("coin")
    val count = args.lift(1).map(_.toInt).getOrElse(100000)
    val repeats = args.lift(2).map(_.toInt).getOrElse(3)
    val workers = args.lift(3).getOrElse("1,2,4,8").split(",").map(_.toInt)
    val mode = args.lift(4).getOrElse("legacy")
    require(Set("legacy", "seeded").contains(mode), "Mode must be legacy or seeded")
    require(Set("coin", "evidence", "normal", "mh", "rng").contains(kind), "Unknown workload")
    require(count > 0 && repeats > 0 && workers.forall(w => w > 0 && w <= count), "Positive counts/workers required; workers cannot exceed samples")
    println(s"ENV,java=${System.getProperty("java.version")},processors=${Runtime.getRuntime.availableProcessors()},maxHeap=${Runtime.getRuntime.maxMemory()}")
    println("BENCH,workload,workers,samples,round,setupMs,sampleMs,queryMs,cleanupAndMetricsMs,cpuMs,liveThreadAllocatedBytes,heapPoolPeakBytes,estimate,absoluteError,estimatedEss")
    println("RNG,mode,workers,draws,round,elapsedMs,checksum")
    for (round <- -2 until repeats; n <- (if (round % 2 == 0) workers else workers.reverse)) {
      if (kind == "rng") { rng(n, count, round, true); rng(n, count, round, false) }
      else run(kind, n, count, round, mode == "seeded")
    }
  }
}
