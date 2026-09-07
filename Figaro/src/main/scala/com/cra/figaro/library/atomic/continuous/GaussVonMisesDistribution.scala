/* Independent implementation of Horwood-Poore (2014), Definition 3.1.
 * See docs/GAUSS_VON_MISES.md for numerical contracts and release-review status.
 */
package com.cra.figaro.library.atomic.continuous

import com.cra.figaro.util.CircularStatistics
import java.util.concurrent.CancellationException

/** Immutable point on a real-vector/circle product space. Numeric angles are retained.
  * @param linear nonempty finite real vector
  * @param angle finite radians; normalize explicitly if canonical equality is desired
  */
final case class LinearAngular(linear: Vector[Double], angle: Double) {
  require(linear != null && linear.nonEmpty && linear.forall(_.isFinite), "linear must be a nonempty finite vector")
  require(angle.isFinite, "angle must be finite radians")
}

/** Immutable Horwood-Poore GVM kernel, constructed through the companion factory.
  * Stores only immutable parameters/factors; each sample call uses a caller-owned RNG.
  * This is a distribution, not a tracking, fusion or uncertainty-propagation algorithm.
  */
final class GaussVonMisesDistribution private (
  val mean: Vector[Double], val covariance: Vector[Vector[Double]], val alpha: Double,
  val beta: Vector[Double], val gamma: Vector[Vector[Double]], val kappa: Double,
  private val lower: Vector[Vector[Double]]) {

  /** Number of linear coordinates; the complete state additionally has one angle. */
  val dimension: Int = mean.size
  private val circular = VonMisesDistribution(0.0, kappa)
  private val gaussianLogScale = -0.5 * dimension * math.log(2.0 * math.Pi) -
    lower.indices.map(i => math.log(lower(i)(i))).sum

  private def checkInterrupted(): Unit =
    if (Thread.currentThread().isInterrupted) throw new CancellationException("GVM operation interrupted")

  private def finite(value: Double): Double = {
    if (!value.isFinite) throw new ArithmeticException("GVM intermediate outside finite numeric range")
    value
  }

  private def whiten(linear: Vector[Double]): Vector[Double] = {
    require(linear != null && linear.size == dimension && linear.forall(_.isFinite), "linear dimensions/entries invalid")
    val z = new Array[Double](dimension)
    for (i <- 0 until dimension) {
      checkInterrupted()
      var residual = finite(linear(i) - mean(i))
      for (j <- 0 until i) residual = finite(residual - finite(lower(i)(j) * z(j)))
      z(i) = finite(residual / lower(i)(i))
    }
    z.toVector
  }

  private def center(z: Vector[Double]): Double = {
    var result = alpha
    for (i <- 0 until dimension) {
      checkInterrupted()
      result = finite(result + finite(beta(i) * z(i)))
      // Do not halve a subnormal coefficient before multiplying by its coordinates.
      result = finite(result + finite(finite(gamma(i)(i) * z(i)) * (0.5 * z(i))))
      // Symmetry folds the off-diagonal terms of 0.5 z^T Gamma z exactly once.
      for (j <- 0 until i) result = finite(result + finite(finite(gamma(i)(j) * z(i)) * z(j)))
    }
    CircularStatistics.normalize(result)
  }

  private def gaussianLog(z: Vector[Double]): Double = {
    var norm = 0.0
    z.foreach(v => norm = math.hypot(norm, v))
    gaussianLogScale - (0.5 * norm) * norm
  }

  /** Conditional angular center alpha + beta^T z + 0.5 z^T Gamma z, modulo one turn.
    * @param linear finite vector of the configured dimension
    * @return radians in [-Pi,Pi); this is not generally the marginal angular mean
    * @example `kernel.conditionalLocation(Vector(0.2))`
    */
  def conditionalLocation(linear: Vector[Double]): Double = center(whiten(linear))

  /** Gaussian marginal log density, using a triangular solve rather than an inverse.
    * @param linear finite vector of the configured dimension
    * @return log density per linear-coordinate volume; extreme tails may return -Infinity
    * @example `kernel.linearLogDensity(Vector(0.2))`
    */
  def linearLogDensity(linear: Vector[Double]): Double = gaussianLog(whiten(linear))

  /** Joint density in log space with respect to linear volume times radians.
    * @param value non-null state with matching linear dimension and finite coordinates
    * @return log Gaussian marginal plus conditional circular log density; may be -Infinity
    * @example `kernel.logDensity(LinearAngular(Vector(0.2), 3.1))`
    */
  def logDensity(value: LinearAngular): Double = {
    require(value != null, "value must be non-null")
    val z = whiten(value.linear)
    val angular = if (kappa == 0.0) circular.logDensity(value.angle)
      else circular.logDensity(CircularStatistics.difference(value.angle, center(z)))
    gaussianLog(z) + angular
  }

  /** Ordinary joint density; prefer logDensity to avoid overflow/underflow.
    * @param value finite state of the configured dimension
    * @return exp(logDensity(value)), subject to ordinary exponentiation limits
    * @example `kernel.density(LinearAngular(Vector(0.2), 3.1))`
    */
  def density(value: LinearAngular): Double = math.exp(logDensity(value))

  /** Independent prior draw: Gaussian vector followed by its conditional circular angle.
    * @param rng non-null caller-owned RNG, not shared between workers
    * @param maxAttempts positive circular rejection budget (default 100000)
    * @return detached immutable state with canonical angle; no MCMC or approximation
    * @example `kernel.sample(new scala.util.Random(42L))`
    */
  def sample(rng: scala.util.Random, maxAttempts: Int = 100000): LinearAngular = {
    require(rng != null && maxAttempts > 0, "rng must be non-null and maxAttempts positive")
    checkInterrupted()
    val z = Vector.fill(dimension) { checkInterrupted(); finite(rng.nextGaussian()) }
    val linear = Vector.tabulate(dimension) { i =>
      checkInterrupted()
      var value = mean(i)
      for (j <- 0 to i) value = finite(value + finite(lower(i)(j) * z(j)))
      value
    }
    val location = if (kappa == 0.0) 0.0 else center(z)
    val angle = CircularStatistics.normalize(location + circular.sample(rng, maxAttempts))
    checkInterrupted()
    LinearAngular(linear, angle)
  }
}

object GaussVonMisesDistribution {
  /** Validate and snapshot fixed GVM parameters in the paper's lower-Cholesky convention.
    * @param mean nonempty finite Gaussian mean vector
    * @param covariance finite exactly symmetric positive-definite covariance; diagonal is variance
    * @param alpha finite radian angular center at the Gaussian mean
    * @param beta finite linear coupling in whitened coordinates, same length as mean
    * @param gamma finite exactly symmetric quadratic coupling matrix; need not be positive definite
    * @param kappa circular concentration in [0, VonMisesDistribution.MaxKappa]
    * @return immutable kernel; invalid/unrepresentable parameters throw IllegalArgumentException
    * @example `GaussVonMisesDistribution(Vector(0.0), Vector(Vector(1.0)), 0.0, Vector(0.5), Vector(Vector(0.2)), 4.0)`
    */
  def apply(mean: scala.collection.Seq[Double], covariance: scala.collection.Seq[scala.collection.Seq[Double]],
    alpha: Double, beta: scala.collection.Seq[Double], gamma: scala.collection.Seq[scala.collection.Seq[Double]],
    kappa: Double): GaussVonMisesDistribution = {
    require(mean != null && mean.nonEmpty && mean.forall(_.isFinite), "mean must be nonempty and finite")
    val mu = mean.toVector
    val n = mu.size
    require(beta != null && beta.size == n && beta.forall(_.isFinite), "beta dimensions/entries invalid")
    val b = beta.toVector
    def matrix(input: scala.collection.Seq[scala.collection.Seq[Double]], label: String): Vector[Vector[Double]] = {
      require(input != null && input.size == n && input.forall(row => row != null && row.size == n && row.forall(_.isFinite)),
        s"$label dimensions/entries invalid")
      val result = input.map(_.toVector).toVector
      require(result.indices.forall(i => result.indices.forall(j => result(i)(j) == result(j)(i))), s"$label must be exactly symmetric")
      result
    }
    val p = matrix(covariance, "covariance")
    val g = matrix(gamma, "gamma")
    val a = VonMisesDistribution(alpha, kappa).location
    require(p.indices.forall(i => p(i)(i) > 0), "covariance diagonal must be positive")
    val sd = p.indices.map(i => math.sqrt(p(i)(i))).toVector
    val lower = Array.ofDim[Double](n, n)
    // Scale to a correlation matrix first: no absolute pivot tolerance tied to user units.
    for (i <- 0 until n; j <- 0 to i) {
      if (Thread.currentThread().isInterrupted) throw new CancellationException("GVM construction interrupted")
      var residual = (p(i)(j) / sd(i)) / sd(j)
      for (k <- 0 until j) residual -= lower(i)(k) * lower(j)(k)
      require(residual.isFinite && (i != j || residual > 0), "covariance is not numerically positive definite")
      lower(i)(j) = if (i == j) math.sqrt(residual) else residual / lower(j)(j)
      require(lower(i)(j).isFinite, "covariance factor outside numeric range")
    }
    val factor = Vector.tabulate(n)(i => Vector.tabulate(i + 1)(j => lower(i)(j) * sd(i)))
    require(factor.forall(_.forall(_.isFinite)) && factor.indices.forall(i => factor(i)(i) > 0), "covariance factor outside numeric range")
    new GaussVonMisesDistribution(mu, p, a, b, g, kappa, factor)
  }
}
