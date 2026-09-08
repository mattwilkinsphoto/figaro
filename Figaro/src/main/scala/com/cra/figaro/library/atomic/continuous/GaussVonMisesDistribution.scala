/* Independent implementation of Horwood-Poore (2014), Definition 3.1.
 * See docs/GAUSS_VON_MISES.md for numerical contracts and release-review status.
 */
package com.cra.figaro.library.atomic.continuous

import com.cra.figaro.util.CircularStatistics
import java.util.concurrent.CancellationException
import org.apache.commons.math3.linear.{Array2DRowRealMatrix, EigenDecomposition}

/** Nonnegative KL contributions in nats; created by klDivergenceComponents.
  * @param gaussian divergence between the linear Gaussian marginals
  * @param conditionalAngular expected conditional angular divergence
  */
final case class GaussVonMisesKL(gaussian: Double, conditionalAngular: Double) {
  require(gaussian.isFinite && gaussian >= 0 && conditionalAngular.isFinite && conditionalAngular >= 0,
    "KL contributions must be finite and nonnegative")
  /** Sum of both contributions, in nats. */
  val total: Double = gaussian + conditionalAngular
  require(total.isFinite, "KL total outside finite numeric range")
}

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

  /** Standardize a point to independent Gaussian coordinates and a circular residual.
    * @param value non-null finite state of this kernel's dimension
    * @return LinearAngular(z, delta), with delta in [-Pi,Pi); not scaled by kappa
    * @example `kernel.canonicalResidual(LinearAngular(Vector(0.2), 3.1))`
    */
  def canonicalResidual(value: LinearAngular): LinearAngular = {
    require(value != null, "value must be non-null")
    val z = whiten(value.linear)
    LinearAngular(z, CircularStatistics.difference(value.angle, center(z)))
  }

  /** Invert canonicalResidual, up to angular wrapping and floating-point rounding.
    * @param residual finite standardized vector z and angular residual in radians
    * @return physical state with angle normalized to [-Pi,Pi)
    * @example `kernel.fromCanonical(LinearAngular(Vector(0.0), 0.0))`
    */
  def fromCanonical(residual: LinearAngular): LinearAngular = {
    require(residual != null && residual.linear.size == dimension, "residual dimension invalid")
    val z = residual.linear
    val x = Vector.tabulate(dimension) { i =>
      checkInterrupted()
      var v = mean(i)
      for (j <- 0 to i) v = finite(v + finite(lower(i)(j) * z(j)))
      v
    }
    LinearAngular(x, CircularStatistics.normalize(center(z) + CircularStatistics.normalize(residual.angle)))
  }

  /** Squared Mahalanobis-von-Mises score ||z||^2 + 4 kappa sin(delta/2)^2.
    * @param value non-null finite state of this kernel's dimension
    * @return nonnegative score (possibly +Infinity in extreme tails), not a p-value
    * @example `kernel.mahalanobisSquared(LinearAngular(Vector(0.2), 3.1))`
    */
  def mahalanobisSquared(value: LinearAngular): Double = {
    require(value != null, "value must be non-null")
    val z = whiten(value.linear)
    var norm = 0.0
    z.foreach(v => norm = math.hypot(norm, v))
    val sine = if (kappa == 0) 0.0 else math.sin(0.5 * CircularStatistics.difference(value.angle, center(z)))
    norm * norm + 4.0 * kappa * sine * sine
  }

  /** Analytic directed divergence D_KL(this || other), in nats; no sampling.
    * @param other non-null GVM on the same coordinates and dimension
    * @return finite nonnegative divergence; numeric overflow throws ArithmeticException
    * @example `kernel.klDivergence(kernel)` returns zero
    */
  def klDivergence(other: GaussVonMisesDistribution): Double = klDivergenceComponents(other).total

  /** Split KL into Gaussian marginal and expected conditional angular contributions.
    * Uses Gaussian quadratic characteristic functions, not a Gaussian approximation.
    * @param other non-null GVM with matching dimension and coordinate meanings
    * @return immutable contributions and total in nats; invalid input throws IllegalArgumentException
    * @example `kernel.klDivergenceComponents(kernel).conditionalAngular` returns zero
    */
  def klDivergenceComponents(other: GaussVonMisesDistribution): GaussVonMisesKL = {
    require(other != null && other.dimension == dimension, "KL requires matching dimensions")
    checkInterrupted()
    val n = dimension
    // x = mu_p + A_p z; q's whitened coordinate is d + B z.
    val d = other.whiten(mean)
    val bmat = Array.ofDim[Double](n, n)
    for (j <- 0 until n; i <- j until n) {
      checkInterrupted()
      var v = lower(i)(j)
      for (k <- j until i) v = finite(v - finite(other.lower(i)(k) * bmat(k)(j)))
      bmat(i)(j) = finite(v / other.lower(i)(i))
    }
    // Stable t^2 - 1 - 2 log(t) around t=1; all summed terms are nonnegative.
    def diagonalTerm(t: Double): Double = {
      if (t <= 0) throw new ArithmeticException("KL scale ratio underflow")
      val u = t - 1.0
      if (math.abs(u) < 1e-4) {
        var power = u*u; var remainder = 0.0
        for (j <- 2 to 12) { remainder += (if (j % 2 == 0) power else -power) / j; power *= u }
        u*u + 2.0*remainder
      } else t*t - 1.0 - 2.0*math.log(t)
    }
    var gaussian = 0.0
    for (i <- 0 until n) {
      gaussian = finite(gaussian + 0.5*d(i)*d(i) + 0.5*diagonalTerm(bmat(i)(i)))
      for (j <- 0 until i) gaussian = finite(gaussian + 0.5*bmat(i)(j)*bmat(i)(j))
    }
    val rp = circular.meanResultantLength
    // Use scaled Bessel normalizers, avoiding subtraction of quantities near kappa.
    val logScaledDifference = circular.logDensity(0.0) - other.circular.logDensity(0.0)
    val concentrationTerm = finite((other.kappa - kappa) * (1.0 - rp))
    def nonnegative(v: Double, scale: Double): Double = {
      finite(v)
      if (v >= 0) v
      else if (v >= -64.0 * math.ulp(math.max(1.0, scale))) 0.0
      else throw new ArithmeticException("KL negative beyond roundoff tolerance")
    }
    val concentrationKL = if (kappa == other.kappa) 0.0
      else if (kappa > 50.0 && other.kappa > 50.0)
        GaussVonMisesDistribution.concentratedKL(kappa, other.kappa)
      else
      nonnegative(logScaledDifference + concentrationTerm, math.abs(logScaledDifference) + math.abs(concentrationTerm))
    // When either concentration is zero, angular centers cannot affect the expectation.
    if (rp == 0.0 || other.kappa == 0.0) {
      finite(gaussian + concentrationKL)
      return GaussVonMisesKL(gaussian, concentrationKL)
    }

    val gd = Vector.tabulate(n)(i => finite((0 until n).map(j => finite(other.gamma(i)(j)*d(j))).sum))
    val c = finite(CircularStatistics.difference(alpha, other.alpha) -
      finite((0 until n).map(i => finite(other.beta(i)*d(i) + 0.5*finite(d(i)*gd(i)))).sum))
    val b = Vector.tabulate(n)(i => finite(beta(i) -
      finite((0 until n).map(j => finite(bmat(j)(i)*finite(other.beta(j)+gd(j)))).sum)))
    val gb = Array.tabulate(n,n)((i,j) => finite((0 until n).map(k => finite(other.gamma(i)(k)*bmat(k)(j))).sum))
    val g = Array.ofDim[Double](n,n)
    for (i <- 0 until n; j <- 0 to i) {
      checkInterrupted()
      val v = finite(gamma(i)(j) - finite((0 until n).map(k => finite(bmat(k)(i)*gb(k)(j))).sum))
      g(i)(j) = v; g(j)(i) = v
    }
    // Real eigensystem avoids the ambiguous square-root branch of a complex determinant.
    val eigen = new EigenDecomposition(new Array2DRowRealMatrix(g, false))
    checkInterrupted()
    var logMagnitude = 0.0; var phase = CircularStatistics.normalize(c)
    for (i <- 0 until n) {
      checkInterrupted()
      val lambda = finite(eigen.getRealEigenvalue(i))
      val v = finite(eigen.getEigenvector(i).toArray.zip(b).map((x,y) => finite(x*y)).sum)
      val h = math.hypot(1.0, lambda)
      val scaled = finite(v / h)
      // log1p preserves small quadratic coupling contributions when hypot rounds to 1.
      val logH = if (math.abs(lambda) < 1.0) 0.5*math.log1p(lambda*lambda) else math.log(h)
      logMagnitude = finite(logMagnitude - 0.5*logH - 0.5*scaled*scaled)
      phase = CircularStatistics.normalize(finite(phase + 0.5*math.atan(lambda) - 0.5*finite(scaled*scaled*lambda)))
    }
    val sine = math.sin(0.5*phase)
    val oneMinusCosineExpectation = -math.expm1(logMagnitude) + math.exp(logMagnitude)*2.0*sine*sine
    val angular = finite(concentrationKL + finite(other.kappa*rp*oneMinusCosineExpectation))
    finite(gaussian + angular)
    GaussVonMisesKL(gaussian, angular)
  }

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
  // log(I0(k) exp(-k) sqrt(2 Pi k)) = sum_j a_j / k^j.
  // Derive log-series coefficients from the same I0 asymptotic series as the circular kernel.
  // 20 terms are below double precision throughout k > 50; no unscaled exp(k).
  private val logBesselCoefficients: Vector[Double] = {
    val s = new Array[Double](21); val a = new Array[Double](21)
    s(0) = 1.0
    for (n <- 1 to 20) {
      s(n) = s(n-1) * (2*n-1).toDouble * (2*n-1) / (8.0*n)
      a(n) = s(n) - (1 until n).map(j => j*a(j)*s(n-j)).sum/n
    }
    a.toVector
  }

  private def concentratedKL(p: Double, q: Double): Double = {
    val u = (q-p)/p
    // -log(1+u)+u, and (1+u)^(-j)-1+j*u, without first-order cancellation.
    def remainder(j: Int): Double = {
      if (math.abs(u) >= 0.001) {
        val logRatio = math.log(q/p)
        if (j == 0) u-logRatio else math.expm1(-j*logRatio)+j*u
      } else {
        var term = if (j == 0) u*u/2 else j.toDouble*(j+1)*u*u/2
        var sum = term
        for (k <- 3 to 14) {
          term *= (if (j == 0) -(k-1).toDouble/k else -(j+k-1).toDouble/k)*u
          sum += term
        }
        sum
      }
    }
    var result = 0.5*remainder(0)
    var power = 1.0
    for (j <- 1 to 20) { power /= p; result += logBesselCoefficients(j)*power*remainder(j) }
    result
  }

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
