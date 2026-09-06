package com.cra.figaro.algorithm.sampling

import scala.util.control.NoStackTrace

/** Opt-in blocking slice sampling of an explicit continuous vector log density.
  * No Universe, graph mutation, shared RNG, owned thread, adaptation, or convergence claim.
  * Algorithms: Schar et al. (2023), GPSS Algorithms 1-3; Heiner et al. (2025),
  * quantile slice Algorithm 2 with fixed Cauchy(0,2) reference. See docs/VECTOR_SLICE_SAMPLING.md.
  */
object VectorSliceSampler {
  /** Explicit kernel selection. GPSS needs dimension >= 2 and nonzero radius;
    * Quantile supports dimension >= 1 and updates coordinates in fixed order.
    */
  enum Method { case GPSS, Quantile }

  /** Work and memory limits for one independent chain.
    * @param method explicit kernel, never selected automatically
    * @param draws positive requested number of retained complete transitions
    * @param warmUp nonnegative discarded complete transitions
    * @param seed private java.util.Random seed, unaffected by other runs
    * @param maxEvaluations positive cap on all log-density calls, including initialization and unfinished work
    * @param maxSearch positive proposal limit per GPSS transition or per quantile coordinate
    * @param maxStoredValues positive bound on draws * dimension, not a total heap bound
    */
  final case class Config(method: Method, draws: Int = 10000, warmUp: Int = 1000,
    seed: Long = 42L, maxEvaluations: Long = 1000000L, maxSearch: Int = 10000,
    maxStoredValues: Long = 10000000L) {
    require(method != null && draws > 0 && warmUp >= 0, "Invalid method or transition counts")
    require(maxEvaluations > 0 && maxSearch > 0 && maxStoredValues > 0, "Limits must be positive")
  }

  /** DrawsReached means requested work completed, NOT that precision or convergence was established. */
  enum StopReason { case DrawsReached, MaxEvaluationsReached }

  /** Detached immutable trace. Never contains an incomplete transition or the initial state as a draw.
    * @param samples ordered complete post-warm-up vectors, possibly empty on budget exhaustion
    * @param lastState last complete state (initial state if no transition finished); NOT a resume token
    * @param evaluations total callback calls charged, including unfinished transition work
    * @param warmUpCompleted number of fully completed warm-up transitions
    * @param reason requested draws obtained or evaluation cap exhausted
    */
  final case class Result(samples: Vector[Vector[Double]], lastState: Vector[Double],
    evaluations: Long, warmUpCompleted: Int, reason: StopReason)

  /** Fail-closed search exhaustion: no fallback sample or partial successful result is returned. */
  final class SearchExhausted(message: String) extends IllegalStateException(message)

  private case object BudgetReached extends RuntimeException with NoStackTrace
  private def interrupted(): Unit =
    if (Thread.currentThread().isInterrupted) throw new InterruptedException("Vector sampling interrupted")
  private def uniform(rng: java.util.Random): Double = {
    var value = 0.0
    while (value == 0.0) { interrupted(); value = rng.nextDouble() }
    value
  }
  private def norm(x: Vector[Double]): Double = x.foldLeft(0.0)(math.hypot)

  /** Run one chain synchronously on the caller's thread.
    * @param config kernel, seed, work and storage limits
    * @param initial nonempty immutable finite vector with finite log density; GPSS cannot start at zero radius
    * @param logDensity deterministic, thread-safe when shared, unnormalized log density w.r.t. Lebesgue measure;
    *        negative infinity means outside support. Callback resources remain caller-owned.
    * @return immutable result; budget exhaustion may return fewer draws and is never precision success
    * @throws IllegalArgumentException for invalid input, NaN/+infinite density, or numerical loss of resolution
    * @throws SearchExhausted when bounded search fails; no result is returned
    * @throws InterruptedException on cooperative interruption; interrupt flag is preserved/restored
    * @example `VectorSliceSampler.run(Config(Method.GPSS, draws = 100), Vector(1.0, 1.0))(x => -x.map(v => v*v).sum / 2)`
    */
  def run(config: Config, initial: Vector[Double])(logDensity: Vector[Double] => Double): Result = {
    require(config != null && initial != null && logDensity != null, "Config, state and density required")
    require(initial.nonEmpty && initial.forall(_.isFinite), "Initial coordinates must be finite")
    require(initial.size.toLong <= config.maxStoredValues / config.draws, "Trace exceeds storage limit")
    if (config.method == Method.GPSS)
      require(initial.size >= 2 && norm(initial).isFinite && norm(initial) > 0, "GPSS needs dimension >= 2 and finite nonzero radius")
    val rng = new java.util.Random(config.seed)
    var evaluations = 0L
    var warm = 0
    var retained = 0
    var state = initial
    val samples = Vector.newBuilder[Vector[Double]]
    def evaluate(x: Vector[Double]): Double = {
      interrupted()
      if (evaluations == config.maxEvaluations) throw BudgetReached
      require(x.size == initial.size && x.forall(_.isFinite), "Invalid proposed coordinates")
      evaluations += 1
      val value = logDensity(x)
      interrupted() // Catch a callback that sets the flag but returns normally.
      require(value.isFinite || value == Double.NegativeInfinity, "Density must be finite or negative infinity")
      value
    }
    var reason = StopReason.DrawsReached
    try {
      require(evaluate(state).isFinite, "Initial state must have finite log density")
      while (warm < config.warmUp || retained < config.draws) {
        interrupted()
        // Commit only after the WHOLE transition, including every coordinate, succeeds.
        val next = config.method match {
          case Method.GPSS => polar(state, rng, evaluate, config.maxSearch)
          case Method.Quantile => quantile(state, rng, evaluate, config.maxSearch)
        }
        state = next
        if (warm < config.warmUp) warm += 1
        else { samples += state; retained += 1 }
      }
      interrupted()
    } catch {
      case BudgetReached => reason = StopReason.MaxEvaluationsReached
      case e: InterruptedException => Thread.currentThread().interrupt(); throw e
    }
    Result(samples.result(), state, evaluations, warm, reason)
  }

  private def quantile(current: Vector[Double], rng: java.util.Random,
    density: Vector[Double] => Double, maxSearch: Int): Vector[Double] = {
    def reference(x: Double): Double = -math.log1p(math.pow(x / 2, 2))
    var state = current
    for (j <- current.indices) {
      val anchor = 0.5 + math.atan(state(j) / 2) / math.Pi
      require(anchor > 0 && anchor < 1, "Quantile CDF rounded to boundary; state is not clipped")
      val initial = density(state) - reference(state(j))
      require(initial.isFinite, "Invalid current quantile residual")
      val level = initial + math.log(uniform(rng))
      require(level.isFinite, "Invalid quantile slice level")
      var low = 0.0; var high = 1.0; var accepted = false; var proposals = 0
      while (!accepted && proposals < maxSearch) {
        val u = low + (high - low) * uniform(rng)
        require(u > low && u < high, "Quantile bracket collapsed")
        val proposed = 2 * math.tan(math.Pi * (u - 0.5))
        require(proposed.isFinite, "Invalid quantile inverse CDF")
        val candidate = state.updated(j, proposed)
        val residual = density(candidate) - reference(proposed)
        require(residual.isFinite || residual == Double.NegativeInfinity, "Invalid quantile residual")
        if (residual > level) { state = candidate; accepted = true }
        else if (u < anchor) low = u else high = u
        proposals += 1
      }
      if (!accepted) throw new SearchExhausted(s"Quantile coordinate $j search exhausted")
    }
    state
  }

  private def polar(current: Vector[Double], rng: java.util.Random,
    density: Vector[Double] => Double, maxSearch: Int): Vector[Double] = {
    val radius = norm(current)
    require(radius.isFinite && radius > 0, "GPSS radius is zero or outside numeric range")
    val direction = current.map(_ / radius)
    def dot(a: Vector[Double], b: Vector[Double]): Double = a.zip(b).map(_ * _).sum
    var tangent = Vector.empty[Double]
    var attempts = 0
    while (tangent.isEmpty && attempts < 100) {
      interrupted()
      val noise = direction.map(_ => rng.nextGaussian())
      val projection = dot(noise, direction)
      val first = noise.zip(direction).map((g, u) => g - projection * u)
      val correction = dot(first, direction)
      val orthogonal = first.zip(direction).map((g, u) => g - correction * u)
      val length = norm(orthogonal)
      if (length.isFinite && length > 1e-12) tangent = orthogonal.map(_ / length)
      attempts += 1
    }
    if (tangent.isEmpty) throw new SearchExhausted("GPSS tangent search exhausted")
    val initial = density(current) + (current.size - 1) * math.log(radius)
    require(initial.isFinite, "Invalid polar current density")
    val level = initial + math.log(uniform(rng))
    require(level.isFinite, "Invalid polar slice level")
    var proposals = 0
    def accepted(rad: Double, dir: Vector[Double]): Boolean = {
      interrupted()
      if (proposals == maxSearch) throw new SearchExhausted("GPSS search exhausted")
      proposals += 1
      require(rad.isFinite && rad > 0, "Invalid proposed radius")
      val value = density(dir.map(_ * rad)) + (current.size - 1) * math.log(rad)
      require(value.isFinite || value == Double.NegativeInfinity, "Invalid polar density")
      value > level
    }
    val tau = 2 * math.Pi
    var angle = tau * uniform(rng)
    var left = angle - tau; var right = angle
    def angular: Vector[Double] = direction.zip(tangent).map((a, b) => a * math.cos(angle) + b * math.sin(angle))
    var dir = angular
    while (!accepted(radius, dir)) {
      if (angle < 0) left = angle else right = angle
      angle = left + uniform(rng) * (right - left)
      require(angle > left && angle < right, "Angular bracket collapsed")
      dir = angular
    }
    val offset = uniform(rng)
    var low = math.max(0, radius - offset); var high = radius + 1 - offset
    while (low > 0 && accepted(low, dir)) low = math.max(0, low - 1)
    while (accepted(high, dir)) { val next = high + 1; require(next > high, "Radial expansion collapsed"); high = next }
    var rad = low + uniform(rng) * (high - low)
    require(rad > low && rad < high, "Radial bracket collapsed")
    while (!accepted(rad, dir)) {
      if (rad < radius) low = rad else high = rad
      rad = low + uniform(rng) * (high - low)
      require(rad > low && rad < high, "Radial bracket collapsed")
    }
    dir.map(_ * rad)
  }
}
