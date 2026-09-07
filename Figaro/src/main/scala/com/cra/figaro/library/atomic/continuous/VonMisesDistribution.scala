/* Independently implemented from NIST DLMF 10.25.2/10.40.1 and Best-Fisher (1979).
 * No third-party implementation copied. See LICENSE and FigaroAttributions.txt.
 */
package com.cra.figaro.library.atomic.continuous

import com.cra.figaro.util.CircularStatistics
import java.util.concurrent.CancellationException

/** Immutable circular von Mises numeric kernel; contains no RNG or universe state.
  * Construct using VonMisesDistribution.apply. All angles are radians.
  */
final class VonMisesDistribution private (val location: Double, val kappa: Double) {
  private val scaledI0 = VonMisesDistribution.scaledBessel(kappa, 0)
  private val logScale = -math.log(2.0 * math.Pi) - math.log(scaledI0)
  private val proposalD = if (kappa <= 1.0) 0.0 else {
    // d = r - 1 in Best-Fisher; avoid subtracting nearly equal r and 1.
    val s = math.hypot(1.0, 2.0 * kappa)
    val t = 1.0 + s
    val oneMinusRho = (math.sqrt(2.0 * t) - 1.0 - 1.0 / (s + 2.0 * kappa)) / (2.0 * kappa)
    oneMinusRho * oneMinusRho / (2.0 * (1.0 - oneMinusRho))
  }

  /** Theoretical mean resultant length I1(kappa)/I0(kappa), not linear variance.
    * @return value in [0,1]; zero for the uniform law
    * @example `VonMisesDistribution(0.0, 2.0).meanResultantLength`
    */
  def meanResultantLength: Double = VonMisesDistribution.scaledBessel(kappa, 1) / scaledI0

  /** Theoretical mean direction; the uniform distribution has no identified direction.
    * @return Some(location) for positive concentration, otherwise None
    * @example `VonMisesDistribution(0.0, 0.0).meanDirection`
    */
  def meanDirection: Option[Double] = if (kappa == 0.0) None else Some(location)

  /** Stable periodic log density with respect to radians over one turn.
    * @param angle finite radian value; equivalent angles receive equivalent scores within rounding
    * @return finite log density throughout the supported concentration range
    * @example `VonMisesDistribution(0.0, 2.0).logDensity(math.Pi)`
    */
  def logDensity(angle: Double): Double = {
    val sine = math.sin(0.5 * CircularStatistics.difference(angle, location))
    logScale - 2.0 * kappa * sine * sine
  }

  /** Periodic density, which may underflow in extreme tails; use logDensity for likelihoods.
    * @param angle finite radian value
    * @return nonnegative density per radian
    * @example `VonMisesDistribution(0.0, 2.0).density(0.0)`
    */
  def density(angle: Double): Double = math.exp(logDensity(angle))

  /** Draw from the circular law without a Gaussian approximation.
    * @param rng caller-owned RNG; do not share a mutable RNG across workers
    * @param maxAttempts positive rejection-attempt budget (default 100000)
    * @return angle in [-Pi, Pi); throws on exhaustion or thread interruption
    * @example `VonMisesDistribution(0.0, 2.0).sample(new scala.util.Random(42L))`
    */
  def sample(rng: scala.util.Random, maxAttempts: Int = 100000): Double = {
    require(rng != null && maxAttempts > 0, "rng must be non-null and maxAttempts positive")
    var attempt = 0
    while (attempt < maxAttempts) {
      if (Thread.currentThread().isInterrupted) throw new CancellationException("von Mises sampling interrupted")
      attempt += 1
      if (kappa <= 1.0) {
        // Uniform envelope: exact also at zero and tiny concentration.
        val offset = (2.0 * rng.nextDouble() - 1.0) * math.Pi
        val sine = math.sin(offset / 2.0)
        if (kappa == 0.0 || math.log(rng.nextDouble()) <= -2.0 * kappa * sine * sine)
          return CircularStatistics.normalize(location + offset)
      } else {
        val half = 0.5 * math.Pi * rng.nextDouble()
        val cosine = math.cos(half); val sine = math.sin(half)
        // q = 1-f; compute directly to retain concentrated angular deviations.
        val q = proposalD * (2.0 * sine * sine) / (proposalD + 2.0 * cosine * cosine)
        val c = kappa * (proposalD + q)
        val u = rng.nextDouble()
        if (c * (2.0 - c) >= u || math.log(c / u) + 1.0 >= c) {
          val offset = 2.0 * math.asin(math.sqrt(math.max(0.0, math.min(1.0, q / 2.0))))
          return CircularStatistics.normalize(location + (if (rng.nextDouble() < 0.5) -offset else offset))
        }
      }
    }
    throw new IllegalStateException("von Mises rejection sampling exhausted maxAttempts")
  }
}

object VonMisesDistribution {
  /** Largest supported concentration; larger values require a separate precision assessment. */
  val MaxKappa: Double = 1e8

  /** Construct a validated immutable kernel, normalizing the location.
    * @param location finite mean-direction parameter in radians (irrelevant when kappa is zero)
    * @param kappa finite concentration in [0, MaxKappa], not variance or standard deviation
    * @return immutable distribution; invalid parameters throw IllegalArgumentException
    * @example `VonMisesDistribution(math.Pi, 4.0)`
    */
  def apply(location: Double, kappa: Double): VonMisesDistribution = {
    require(kappa.isFinite && kappa >= 0.0 && kappa <= MaxKappa, s"kappa must be in [0,$MaxKappa]")
    new VonMisesDistribution(CircularStatistics.normalize(location), kappa)
  }

  private def scaledBessel(x: Double, order: Int): Double = {
    if (x <= 50.0) {
      val square = (x / 2.0) * (x / 2.0)
      var term = 1.0; var sum = 1.0; var j = 1
      while (j < 1000) {
        term *= square / (j.toDouble * (j + order))
        sum += term
        if (term <= sum * 1e-16) return math.exp(-x) * (if (order == 0) sum else (x / 2.0) * sum)
        j += 1
      }
      throw new ArithmeticException("Bessel series did not converge")
    } else {
      var term = 1.0; var sum = 1.0; var j = 1
      while (j < 1000) {
        term *= ((2.0 * j - 1.0) * (2.0 * j - 1.0) - 4.0 * order * order) / (8.0 * x * j)
        sum += term
        if (math.abs(term) <= math.abs(sum) * 1e-16) return sum / math.sqrt(2.0 * math.Pi * x)
        j += 1
      }
      throw new ArithmeticException("Bessel asymptotic expansion did not converge")
    }
  }
}
