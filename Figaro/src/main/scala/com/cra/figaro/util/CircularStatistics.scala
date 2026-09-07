/* Modern Figaro circular utilities. See LICENSE and FigaroAttributions.txt. */
package com.cra.figaro.util

/** Circular summaries use radians, not arithmetic averages of angular coordinates. */
object CircularStatistics {
  /** Summary of equally weighted finite angles.
    * @param count number of observations
    * @param meanDirection direction in [-Pi, Pi), or None below the requested resultant threshold
    * @param meanResultantLength magnitude of the mean unit vector, in [0, 1]
    */
  final case class Summary(count: Long, meanDirection: Option[Double], meanResultantLength: Double)

  /** Normalize a finite radian angle to [-Pi, Pi); canonicalize signed zero.
    * @param angle finite radians; very large inputs may already have lost phase precision
    * @return equivalent representative; throws IllegalArgumentException for nonfinite input
    * @example `CircularStatistics.normalize(3 * math.Pi)`
    */
  def normalize(angle: Double): Double = {
    require(angle.isFinite, "angle must be finite radians")
    val remainder = angle % (2.0 * math.Pi)
    val result = if (remainder >= math.Pi) remainder - 2.0 * math.Pi
      else if (remainder < -math.Pi) remainder + 2.0 * math.Pi else remainder
    if (result == 0.0) 0.0 else result
  }

  /** Signed shortest displacement from reference to angle, in [-Pi, Pi).
    * @param angle finite destination in radians
    * @param reference finite reference in radians
    * @return normalized difference; antipodal ties map to -Pi
    * @example `CircularStatistics.difference(-3.1, 3.1)`
    */
  def difference(angle: Double, reference: Double): Double = normalize(normalize(angle) - normalize(reference))

  /** Summarize equally weighted angles in one pass with compensated sums.
    * @param angles nonempty, finite radian observations; consumed once
    * @param minResultant threshold in [0,1]; direction is None when resultant <= threshold
    * @return sample summary, not a confidence/convergence assessment
    * @example `CircularStatistics.summarize(Vector(-3.1, 3.1))`
    */
  def summarize(angles: IterableOnce[Double], minResultant: Double = 1e-12): Summary = {
    require(minResultant.isFinite && minResultant >= 0.0 && minResultant <= 1.0,
      "minResultant must be in [0,1]")
    var count = 0L
    var sine = 0.0; var cosine = 0.0
    var sineError = 0.0; var cosineError = 0.0
    angles.iterator.foreach { raw =>
      val angle = normalize(raw)
      val sy = math.sin(angle) - sineError; val st = sine + sy
      sineError = (st - sine) - sy; sine = st
      val cy = math.cos(angle) - cosineError; val ct = cosine + cy
      cosineError = (ct - cosine) - cy; cosine = ct
      count += 1L
    }
    require(count > 0L, "angles must be nonempty")
    val resultant = math.min(1.0, math.hypot(sine / count, cosine / count))
    Summary(count, if (resultant <= minResultant) None else Some(normalize(math.atan2(sine, cosine))), resultant)
  }
}
