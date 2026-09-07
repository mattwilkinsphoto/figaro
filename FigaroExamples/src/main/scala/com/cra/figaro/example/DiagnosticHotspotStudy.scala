package com.cra.figaro.example

import com.cra.figaro.algorithm.sampling.parallel.McmcDiagnostics as D
import org.apache.commons.math3.distribution.NormalDistribution
import java.lang.management.ManagementFactory
import java.nio.ByteBuffer
import java.security.MessageDigest

/** Isolated candidate study, not a consumer sampler API. See docs/DIAGNOSTIC_HOTSPOT_STUDY.md. */
object DiagnosticHotspotStudy {
  @volatile private var sink: AnyRef = null
  private def interrupted(): Unit =
    if (Thread.currentThread().isInterrupted) throw new InterruptedException("Diagnostic study interrupted")

  // Stable LSD radix sorting of IEEE total-order keys. No shared buffers or input mutation.
  // Canonical NaNs also match Double.compare, although public diagnostics require finite data.
  private def radix(values: Array[Double]): Array[Int] = {
    interrupted()
    val n = values.length
    var order = new Array[Int](n)
    var scratch = new Array[Int](n)
    val counts = new Array[Int](256)
    var i = 0
    while (i < n) {
      if ((i & 1023) == 0) interrupted()
      order(i) = i; i += 1
    }
    def bucket(index: Int, shift: Int): Int = {
      val bits = java.lang.Double.doubleToLongBits(values(index))
      val key = bits ^ ((bits >> 63) | Long.MinValue)
      ((key >>> shift) & 255L).toInt
    }
    var shift = 0
    while (shift < 64) {
      interrupted()
      java.util.Arrays.fill(counts, 0)
      i = 0
      while (i < n) {
        if ((i & 1023) == 0) interrupted()
        counts(bucket(order(i), shift)) += 1; i += 1
      }
      var total = 0
      i = 0
      while (i < counts.length) {
        val count = counts(i); counts(i) = total; total += count; i += 1
      }
      i = 0
      while (i < n) {
        if ((i & 1023) == 0) interrupted()
        val index = order(i)
        val b = bucket(index, shift)
        scratch(counts(b)) = index; counts(b) += 1; i += 1
      }
      val previous = order; order = scratch; scratch = previous
      shift += 8
    }
    interrupted()
    order
  }

  // Same tie grouping, probability expression, inverse CDF and scatter as production.
  private def score(values: Array[Double], order: Array[Int], chainLength: Int): Array[Array[Double]] = {
    interrupted()
    val result = new Array[Double](values.length)
    val normal = new NormalDistribution(0, 1)
    var first = 0
    while (first < order.length) {
      interrupted()
      var end = first + 1
      while (end < order.length && values(order(end)) == values(order(first))) {
        if ((end & 1023) == 0) interrupted()
        end += 1
      }
      val rank = (first + 1.0 + end) / 2.0
      val z = normal.inverseCumulativeProbability((rank - 0.375) / (values.length + 0.25))
      var index = first
      while (index < end) {
        if ((index & 1023) == 0) interrupted()
        result(order(index)) = z; index += 1
      }
      first = end
    }
    result.grouped(chainLength).map(_.toArray).toArray
  }
  private def radixRank(chains: Array[Array[Double]]): Array[Array[Double]] = {
    interrupted()
    val values = chains.flatten
    score(values, radix(values), chains.head.length)
  }
  private def bits(x: Array[Array[Double]]): Vector[Long] =
    x.iterator.flatMap(_.iterator.map(java.lang.Double.doubleToLongBits)).toVector

  private def check(): Unit = {
    val random = new java.util.Random(270913L)
    val edge = Array(-0.0, 0.0, -Double.MaxValue, Double.MaxValue, java.lang.Double.MIN_VALUE,
      -java.lang.Double.MIN_VALUE, Double.NegativeInfinity, Double.PositiveInfinity, Double.NaN,
      java.lang.Double.longBitsToDouble(0xfff8000000000001L))
    var cases = 0
    for (n <- Vector(0, 1, 2, 3, 7, 31, 255, 256, 257, 1023, 1024, 1025, 16000, 64000);
      kind <- 0 until 5) {
      val input = Array.tabulate(n) { i => kind match {
        case 0 => java.lang.Double.longBitsToDouble(random.nextLong())
        case 1 => edge(i % edge.length)
        case 2 => i.toDouble
        case 3 => (n - i).toDouble
        case _ => (i % 7).toDouble
      }}
      val before = input.map(java.lang.Double.doubleToRawLongBits).toVector
      val oracle = input.indices.toArray.sortWith((i, j) => {
        val compared = java.lang.Double.compare(input(i), input(j))
        compared < 0 || (compared == 0 && i < j)
      })
      require(radix(input).toVector == oracle.toVector, "Radix order/stability mismatch")
      require(input.map(java.lang.Double.doubleToRawLongBits).toVector == before, "Input mutation")
      cases += 1
    }
    for (n <- Vector(4, 32, 256, 1024, 4000, 16000); kind <- Vector("continuous", "ties", "ordered", "reverse", "constant")) {
      val x = data(kind, 4*n)
      val chains = x.grouped(n).map(_.toArray).toArray
      require(bits(radixRank(chains)) == bits(D.rankNormalize(chains)), "Rank score mismatch")
    }
    val pool = java.util.concurrent.Executors.newFixedThreadPool(4)
    try {
      val x = data("continuous", 16000)
      val expected = radix(x).toVector
      val results = Vector.fill(16)(pool.submit(new java.util.concurrent.Callable[Array[Int]] {
        def call(): Array[Int] = radix(x)
      })).map(_.get(10, java.util.concurrent.TimeUnit.SECONDS))
      results.foreach(x => require(x.toVector == expected))
      results.head(0) = -1
      results.tail.foreach(x => require(x.toVector == expected))
    } finally {
      pool.shutdownNow()
      require(pool.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS))
    }
    try {
      Thread.currentThread().interrupt()
      var caught = false
      try radix(Array(1.0)) catch { case _: InterruptedException => caught = true }
      require(caught && Thread.currentThread().isInterrupted)
    } finally Thread.interrupted()
    println(s"Diagnostic hotspot checks passed: $cases sort cases, 30 rank cases, concurrent isolation and interruption")
  }

  private def data(kind: String, n: Int): Array[Double] = {
    val rng = new java.util.Random(91073L + n)
    Array.tabulate(n) { i => kind match {
      case "continuous" => rng.nextGaussian()
      case "ties" => if (i % 7 == 0) -0.0 else (rng.nextInt(17) - 8).toDouble
      case "ordered" => i.toDouble
      case "reverse" => (n - i).toDouble
      case "constant" => 1.0
    }}
  }
  private def fingerprint(result: AnyRef): String = {
    val hash = MessageDigest.getInstance("SHA-256")
    val buffer = ByteBuffer.allocate(8)
    def add(v: Long): Unit = { buffer.clear(); buffer.putLong(v); hash.update(buffer.array()) }
    result match {
      case x: Array[Int] => x.foreach(v => add(v.toLong))
      case x: Array[Array[Double]] => x.foreach(_.foreach(v => add(java.lang.Double.doubleToLongBits(v))))
      case x: D.Summary => hash.update(x.toString.getBytes(java.nio.charset.StandardCharsets.UTF_8))
      case _ => throw new IllegalArgumentException("Unexpected study result")
    }
    java.util.HexFormat.of().formatHex(hash.digest())
  }

  /** Run candidate correctness controls or a complete isolated timing grid.
    * @param args `check`, or optional measured rounds (7, 1-20) and workload values per stage (64000, 64000-1024000)
    * @return Unit; quoted CSV includes five warm-up rounds, elapsed time, current-thread allocation and fingerprints
    * @throws IllegalArgumentException for invalid arguments or candidate/reference mismatch
    * @throws InterruptedException on cooperative cancellation; partial logs are not a completed study
    * @example `DiagnosticHotspotStudy.main(Array("check"))`
    */
  def main(args: Array[String]): Unit = {
    if (args.toVector == Vector("check")) { check(); return }
    require(!D.getClass.getDeclaredMethods.exists(_.getName == "radixSortedIndices"),
      "Full hotspot timings require the pre-integration eda9ebba library runtime; use check on the current library")
    require(args.length <= 2)
    val rounds = args.headOption.map(_.toInt).getOrElse(7)
    val work = args.lift(1).map(_.toInt).getOrElse(64000)
    require(rounds >= 1 && rounds <= 20 && work >= 64000 && work <= 1024000)
    val bean = ManagementFactory.getThreadMXBean match {
      case x: com.sun.management.ThreadMXBean if x.isThreadAllocatedMemorySupported =>
        if (!x.isThreadAllocatedMemoryEnabled) x.setThreadAllocatedMemoryEnabled(true)
        Some(x)
      case _ => None
    }
    def allocated: Long = bean.map(_.getThreadAllocatedBytes(Thread.currentThread().getId)).getOrElse(-1L)
    def csv(xs: Any*): Unit = println(xs.map(x => "\"" + x.toString.replace("\"", "\"\"") + "\"").mkString(","))
    csv("diagnosticHotspot", "shape", "values", "round", "stage", "iterations", "seconds", "allocatedBytes", "fingerprint")
    for (n <- Vector(1024, 16000, 64000); kind <- Vector("continuous", "ties", "ordered", "reverse", "constant")) {
      val values = data(kind, n)
      val before = values.map(java.lang.Double.doubleToRawLongBits).toVector
      val chains = values.grouped(n / 4).map(_.toArray).toArray
      val seq = chains.map(_.toVector).toVector
      val order = D.sortedIndices(values)
      require(radix(values).toVector == order.toVector, "Sort mismatch")
      val ranks = D.rankNormalize(chains)
      require(bits(radixRank(chains)) == bits(ranks) && bits(score(values, order, n/4)) == bits(ranks), "Score mismatch")
      val stages: Vector[(String, () => AnyRef)] = Vector(
        "mergeSort" -> (() => D.sortedIndices(values)), "radixSort" -> (() => radix(values)),
        "scoresAndScatter" -> (() => score(values, order, n/4)),
        "mergeRank" -> (() => D.rankNormalize(chains)), "radixRank" -> (() => radixRank(chains)),
        "summary" -> (() => D.summarize(seq)))
      val expected = stages.map((name, call) => name -> fingerprint(call())).toMap
      for (round <- -5 until rounds) {
        val offset = Math.floorMod(round, stages.size)
        val rotated = stages.drop(offset) ++ stages.take(offset)
        val ordered = if (round % 2 == 0) rotated else rotated.reverse
        val iterations = math.max(1, work / n)
        for ((name, call) <- ordered) {
          val startBytes = allocated
          val start = System.nanoTime()
          var i = 0
          while (i < iterations) { sink = call(); i += 1 }
          val seconds = (System.nanoTime() - start) / 1e9
          val endBytes = allocated
          val bytes = if (startBytes < 0 || endBytes < 0) "NaN" else (endBytes - startBytes).toString
          val actual = fingerprint(sink)
          require(actual == expected(name), "Unstable output")
          csv("row", kind, n, round, name, iterations, seconds, bytes, actual)
        }
      }
      require(values.map(java.lang.Double.doubleToRawLongBits).toVector == before, "Input mutation")
    }
  }
}
