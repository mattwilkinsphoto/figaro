import com.cra.figaro.algorithm.sampling.parallel.McmcDiagnostics
import java.nio.file.{Files, Path}
import java.security.MessageDigest

/** Public diagnostic regression; the explicit old-release mode is a negative control. */
object DiagnosticConsumerCheck {
  def main(args: Array[String]): Unit = {
    require(args.isEmpty || args.toVector == Vector("--expect-legacy-tie-defect"))
    val artifact = Path.of(McmcDiagnostics.getClass.getProtectionDomain.getCodeSource.getLocation.toURI)
    require(artifact.getFileName.toString.endsWith(".jar"), "Producer classes are not allowed")
    val hash = java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(artifact)))
    require(hash.equalsIgnoreCase(sys.env("FIGARO_EXPECTED_SHA256")), "Wrong or stale artifact")
    val lo = -0.6568859528122107
    val hi = 0.6177859378881788
    val chains = Vector.tabulate(4)(c => Vector.tabulate(4000) { i =>
      val j = (i * 17 + c * 13) % 100
      if (j < 12) lo else if (j >= 97) 1.0 else if (j >= 88) hi else (j - 50) / 100.0
    })
    // The 5% and 95% ranks lie inside the lo/hi plateaus, with nonconstant indicators.
    val summary = McmcDiagnostics.summarize(chains)
    require(summary.mean.isFinite && summary.bulkEss.nonEmpty && summary.meanEss.nonEmpty)
    if (args.nonEmpty) {
      require(artifact.getFileName.toString == "figaro_3-7.4.0.jar")
      require(summary.tailEss.isEmpty, "Published baseline did not reproduce the frozen defect")
      println("Expected published-7.4.0 tail-ESS defect reproduced: " + hash)
    } else {
      require(summary.tailEss.exists(x => x.isFinite && x > 0), "Tied quantile incorrectly removed a nonconstant tail")
      val constant = McmcDiagnostics.summarize(Vector.fill(4)(Vector.fill(101)(lo)))
      require(constant.tailEss.isEmpty && constant.rHat.isEmpty)
      println("Tied-quantile public diagnostic regression passed: " + hash)
    }
  }
}
