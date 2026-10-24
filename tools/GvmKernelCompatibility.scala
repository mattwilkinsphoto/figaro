import com.cra.figaro.library.atomic.continuous.*
import org.apache.commons.math3.linear.{Array2DRowRealMatrix, CholeskyDecomposition}

/** GVM-05 consumer check against the unchanged published kernel and scalar fit. */
object GvmKernelCompatibility {
  def main(args: Array[String]): Unit = {
    val source = scala.io.Source.fromFile(args(0))
    try source.getLines().zipWithIndex.foreach { (line, index) =>
      val values = line.split("\\s+").map(_.toDouble)
      val d = values(0).toInt
      var cursor = 1
      def take(n: Int): Vector[Double] = {
        val out = values.slice(cursor, cursor+n).toVector
        cursor += n
        out
      }
      val mean = take(d)
      val covariance = Vector.fill(d)(take(d))
      val alpha = take(1).head
      val beta = take(d)
      val gamma = Array.fill(d,d)(0.0)
      for (i <- 0 until d; j <- i until d) {
        val v = take(1).head
        gamma(i)(j) = v; gamma(j)(i) = v
      }
      val kappa = take(1).head
      val x = take(d); val angle = take(1).head; val expected = take(1).head
      val lower = new CholeskyDecomposition(new Array2DRowRealMatrix(covariance.map(_.toArray).toArray)).getL
      val matrix = new Array2DRowRealMatrix(gamma)
      val shift = matrix.operate(mean.toArray)
      val a = alpha + beta.zip(mean).map(_ * _).sum + .5*mean.zip(shift).map(_ * _).sum
      val b = lower.transpose.operate(beta.zip(shift).map(_ + _).toArray)
      val g = lower.transpose.multiply(matrix).multiply(lower).getData
      val law = GaussVonMisesDistribution(mean, covariance, a, b.toVector, g.map(_.toVector).toVector, kappa)
      val actual = law.logDensity(LinearAngular(x, angle))
      require(math.abs(actual-expected) < 1e-8, s"Export mismatch $index: $actual / $expected")
      println(s"kernel,$index,$d,$actual,$expected")
    } finally source.close()
    val scalarSource = scala.io.Source.fromFile(args(1))
    val scalar = try scalarSource.getLines().map { line =>
      val a = line.split("\\s+").map(_.toDouble)
      LinearAngular(Vector(a(0)), a(1))
    }.toVector finally scalarSource.close()
    val fit = GaussVonMisesMixtureFit.fit(scalar, GaussVonMisesMixtureFit.Config(
      components=1, maxConcentration=50, seed=102))
    require(fit.distribution.nonEmpty)
    val score = scalar.map(fit.distribution.get.logDensity).sum
    require(score.isFinite)
    println(s"scalar,$score,${fit.attempts.map(_.status).mkString("|")}")
  }
}
