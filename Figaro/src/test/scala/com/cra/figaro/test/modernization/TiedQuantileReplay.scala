package com.cra.figaro.test.modernization

import com.cra.figaro.algorithm.sampling.parallel.McmcDiagnostics
import java.nio.{ByteBuffer, ByteOrder}
import java.nio.file.{Files, Path}
import java.util.Base64
import java.util.zip.GZIPInputStream
import scala.collection.mutable
import scala.io.Source

/** Read-only replay of retained development draws. Never runs a sampler. */
object TiedQuantileReplay {
  def main(args: Array[String]): Unit = {
    require(args.length == 1, "Retained development log directory required")
    var summaries = 0; var attempts = 0
    for (index <- 0 until 8) {
      val traces = mutable.Map.empty[(String, String), mutable.Map[Int, Vector[Double]]]
      val source = Source.fromInputStream(new GZIPInputStream(
        Files.newInputStream(Path.of(args(0), s"development-$index.log.gz"))), "UTF-8")
      try source.getLines().foreach { line =>
        val v = line.split("\t", -1)
        if (v.headOption.contains("DP")) {
          require(v(3).toLong == 26011001L + index, "Only retained development seeds allowed")
          if (v(4) == "TRACE" && v(5) != "training") {
            val bytes = Base64.getDecoder.decode(v(9))
            require(bytes.length % 8 == 0)
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
            val draws = Vector.fill(bytes.length / 8)(buffer.getDouble())
            val group = traces.getOrElseUpdate((v(5), v(6)), mutable.Map.empty)
            require(!group.contains(v(7).toInt), "Duplicate chain")
            group(v(7).toInt) = draws
          } else if (v(4) == "END") {
            require(Set("delivered", "refused")(v(5)), "Failed original attempt")
            traces.toVector.sortBy(_._1).foreach { case ((phase, query), group) =>
              require(group.keySet == Set(0, 1, 2, 3))
              val chains = Vector.tabulate(4)(group)
              val d = McmcDiagnostics.summarize(chains)
              val sorted = chains.flatten.sorted.toArray
              val cuts = Vector(0.05, 0.95).map(p => McmcDiagnostics.quantile(sorted, p))
              println((Vector[Any]("QR", v(1), v(2), v(3), phase, query,
                d.mean, d.standardDeviation, d.rHat.getOrElse(Double.NaN),
                d.bulkEss.getOrElse(Double.NaN), d.tailEss.getOrElse(Double.NaN),
                d.meanEss.getOrElse(Double.NaN), d.mcseMean.getOrElse(Double.NaN)) ++
                cuts ++ Vector(chains.exists(x => x.forall(_ == x.head)), d.warnings.mkString("|"))).mkString("\t"))
              summaries += 1
            }
            traces.clear(); attempts += 1
          }
        }
      } finally source.close()
      require(traces.isEmpty, "Truncated attempt")
      println(s"REPLAY_PROGRESS\t$index\t$attempts\t$summaries")
    }
    require(attempts == 272 && summaries == 5798, "Incomplete retained inventory")
    println(s"REPLAY_COMPLETE\t$attempts\t$summaries")
  }
}
