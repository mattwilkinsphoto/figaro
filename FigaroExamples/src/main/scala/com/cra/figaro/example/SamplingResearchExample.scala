package com.cra.figaro.example

import com.cra.figaro.algorithm.sampling.GaussianBlockProposal
import com.cra.figaro.algorithm.sampling.parallel.{McmcPrecision, MultiChainMetropolisHastings as MH}
import com.cra.figaro.language.*
import com.cra.figaro.library.atomic.continuous.Normal
import java.util.concurrent.atomic.AtomicLong
import org.apache.commons.math3.distribution.NormalDistribution

/** Research-only comparison; no production sampling API is changed.
  * Uniform MESS is independently implemented from Senn et al. (2026), Algorithm 2,
  * https://arxiv.org/html/2602.22358v1 (paper CC BY 4.0). No upstream source is vendored.
  * M=1 is the paper's repositioned ESS baseline, not a new 2026 algorithm.
  */
object SamplingResearchExample {
  private type Point = Vector[Double]
  private val tau = 2 * math.Pi
  private val normal = new NormalDistribution(0, 1)
  private val names = Vector("x", "y", "xSquared", "ySquared", "event")
  private val policy = McmcPrecision.Config(relativeTolerance = 0.15, minDrawsPerChain = 2000, checkEvery = 2000)

  private case class Target(name: String, logDensity: Point => Double, truths: Vector[Double], event: Double => Double)
  private def logAdd(a: Double, b: Double): Double = {
    val top = math.max(a, b)
    top + math.log(math.exp(a - top) + math.exp(b - top))
  }
  private val targets = Vector(
    Target("gaussian", v => -0.5 * (v(0) * v(0) + v(1) * v(1)),
      Vector(0, 0, 1, 1, 2 * normal.cumulativeProbability(-2)), x => if (math.abs(x) > 2) 1 else 0),
    Target("banana", v => -0.5 * v(0) * v(0) - 2 * math.pow(v(1) - 0.4 * (v(0) * v(0) - 1), 2),
      Vector(0, 0, 1, 0.57, 2 * normal.cumulativeProbability(-2)), x => if (math.abs(x) > 2) 1 else 0),
    Target("unequal-modes", v => logAdd(math.log(0.8) - 2 * math.pow(v(0) + 4, 2),
      math.log(0.2) - 2 * math.pow(v(0) - 4, 2)) - 0.5 * v(1) * v(1),
      Vector(-2.4, 0, 16.25, 1, 0.8 * normal.cumulativeProbability(-8) + 0.2 * normal.cumulativeProbability(8)),
      x => if (x > 0) 1 else 0))

  // Density residual against N(0,I). This is not the full target log density.
  private def residual(target: Target)(v: Point): Double = target.logDensity(v) + 0.5 * v.map(x => x * x).sum

  private def openUniform(rng: java.util.Random): Double = {
    var u = rng.nextDouble()
    while (u == 0) u = rng.nextDouble()
    u
  }

  /** Immutable-state kernel. Finite means/scales specify a fixed diagonal Gaussian reference.
    * No learned transport, LP selection, adaptation, graph mutation, or parallel evaluation.
    */
  private def step(current: Point, currentLogWeight: Double, means: Point, scales: Point,
    proposals: Int, rng: java.util.Random, logWeight: Point => Double, maxBatches: Int = 1000): (Point, Double, Int) = {
    require(current.nonEmpty && current.forall(_.isFinite) && currentLogWeight.isFinite, "Invalid current state")
    require(means.size == current.size && means.forall(_.isFinite) && scales.size == current.size &&
      scales.forall(s => s.isFinite && s > 0), "Invalid Gaussian reference")
    require(proposals >= 1 && proposals <= 64 && maxBatches > 0, "Invalid kernel budget")
    val auxiliary = scales.map(s => s * rng.nextGaussian())
    val threshold = currentLogWeight + math.log(openUniform(rng))
    require(threshold.isFinite, "Slice level outside numeric range")
    val alpha = tau * openUniform(rng)
    var left = 0.0
    var right = tau
    var batches = 0
    while (batches < maxBatches) {
      if (Thread.currentThread().isInterrupted) throw new InterruptedException("Research kernel interrupted")
      val angles = Vector.fill(proposals)(left + (right - left) * openUniform(rng))
      val candidates = angles.map { angle =>
        current.indices.map(i => means(i) + (current(i) - means(i)) * math.cos(angle - alpha) +
          auxiliary(i) * math.sin(angle - alpha)).toVector
      }
      require(candidates.forall(_.forall(_.isFinite)), "Candidate outside numeric range")
      val weights = candidates.map(logWeight)
      require(weights.forall(w => w.isFinite || w == Double.NegativeInfinity), "Invalid log weight")
      batches += 1
      val valid = weights.indices.filter(i => weights(i) > threshold)
      if (valid.nonEmpty) {
        val selected = if (valid.size == 1) valid.head else valid(rng.nextInt(valid.size))
        return (candidates(selected), weights(selected), batches * proposals)
      }
      angles.foreach { angle =>
        if (angle < alpha) left = math.max(left, angle) else right = math.min(right, angle)
      }
      require(left < alpha && alpha < right, "Slice bracket collapsed; refusing a fabricated transition")
    }
    throw new IllegalStateException("Slice search budget exhausted; no fallback sample returned")
  }

  private def projected(v: Point, target: Target): Vector[Double] =
    Vector(v(0), v(1), v(0) * v(0), v(1) * v(1), target.event(v(0)))

  // Heiner et al., Quantile Slice Sampling (2024, revised 2025), Algorithm 2:
  // https://arxiv.org/html/2407.12608v2. Independent implementation, no package code copied.
  // One fixed-order Gibbs sweep with a Cauchy(0,2) pseudo-target for each conditional.
  private[example] def quantileSweep(current: Point, rng: java.util.Random, logDensity: Point => Double,
    maxTrials: Int = 1000): Point = {
    require(current.nonEmpty && current.forall(_.isFinite) && maxTrials > 0, "Invalid quantile state/budget")
    def logReference(x: Double): Double = -math.log1p(math.pow(x / 2, 2)) // constants cancel
    var state = current
    for (j <- current.indices) {
      val at = 0.5 + math.atan(state(j) / 2) / math.Pi
      require(at > 0 && at < 1, "CDF rounded to a boundary; refusing to clip the state")
      val initial = logDensity(state) - logReference(state(j))
      require(initial.isFinite, "Invalid current quantile density")
      val level = initial + math.log(openUniform(rng))
      require(level.isFinite, "Invalid quantile slice level")
      var left = 0.0; var right = 1.0; var accepted = false; var trials = 0
      while (!accepted && trials < maxTrials) {
        if (Thread.currentThread().isInterrupted) throw new InterruptedException("Quantile kernel interrupted")
        val u = left + (right - left) * openUniform(rng)
        require(u > left && u < right, "Quantile bracket lost floating-point resolution")
        val proposed = 2 * math.tan(math.Pi * (u - 0.5))
        require(proposed.isFinite, "Invalid inverse CDF")
        val candidate = state.updated(j, proposed)
        val value = logDensity(candidate) - logReference(proposed)
        require(value.isFinite || value == Double.NegativeInfinity, "Invalid candidate quantile density")
        if (value > level) { state = candidate; accepted = true }
        else if (u < at) left = u else right = u
        trials += 1
      }
      if (!accepted) throw new IllegalStateException("Quantile search exhausted; no fallback sample returned")
    }
    state
  }

  private def sampled(target: Target, method: String, draws: Int, seed: Long): (Vector[Map[String, Vector[Double]]], Long) = {
    val evaluations = new AtomicLong()
    def weight(v: Point): Double = { evaluations.incrementAndGet(); residual(target)(v) }
    if (method == "figaro-block") {
      val r = MH.run(MH.Config(drawsPerChain = draws, warmUp = 2000, parallelism = 2, seed = seed)) { (u, _) =>
        val x = Normal(0, 1)(using "", u); val y = Normal(0, 1)(using "", u)
        Inject(x, y)(using "", u).addLogConstraint((v: List[Double]) => weight(v.toVector))
        val ys = if (target.name == "banana") 0.57 else 1.0
        MH.Model(Vector(MH.Observable("x", x)(identity), MH.Observable("y", y)(identity),
          MH.Observable("xSquared", x)(v => v * v), MH.Observable("ySquared", y)(v => v * v),
          MH.Observable("event", x)(target.event)),
          Some(GaussianBlockProposal(Vector(x, y), Vector(Vector(1.0, 0.0), Vector(0.0, ys)))))
      }
      (r.chains.map(_.draws), evaluations.get())
    } else {
      val count = if (method == "qslice-cauchy") 0 else method.stripPrefix("mess-").toInt
      val seeds = new java.util.SplittableRandom(seed)
      val traces = Vector.fill(4) {
        val rng = new java.util.Random(seeds.nextLong())
        var state = Vector.fill(2)(rng.nextGaussian())
        var w = if (count == 0) 0.0 else weight(state)
        val rows = Vector.newBuilder[Vector[Double]]
        for (i <- 0 until draws + 2000) {
          if (count == 0) state = quantileSweep(state, rng, v => { evaluations.incrementAndGet(); target.logDensity(v) })
          else {
            val next = step(state, w, Vector(0, 0), Vector(1, 1), count, rng, weight)
            state = next._1; w = next._2
          }
          if (i >= 2000) rows += projected(state, target)
        }
        val values = rows.result()
        names.indices.map(i => names(i) -> values.map(_(i))).toMap
      }
      (traces, evaluations.get())
    }
  }

  // Deterministic kernel contracts and seeded analytic controls, not empirical coverage gates.
  private def check(): Unit = {
    def invalid(body: => Any): Unit = {
      var thrown = false
      try body catch { case _: IllegalArgumentException => thrown = true }
      require(thrown, "Expected explicit validation failure")
    }
    val zero = Vector(0.0, 0.0)
    val unit = Vector(1.0, 1.0)
    invalid(step(zero, 0, zero, unit, 0, new java.util.Random(1), _ => 0))
    invalid(step(zero, 0, zero, Vector(1, 0), 1, new java.util.Random(1), _ => 0))
    invalid(step(zero, 0, zero, unit, 1, new java.util.Random(1), _ => Double.NaN))
    invalid(step(zero, 0, zero, unit, 1, new java.util.Random(1), _ => Double.PositiveInfinity))
    var exhausted = false
    try step(zero, 0, zero, unit, 4, new java.util.Random(1), _ => Double.NegativeInfinity, 1)
    catch { case _: IllegalStateException => exhausted = true }
    require(exhausted, "No silent fallback on search exhaustion")
    val sentinel = new RuntimeException("callback")
    var propagated = false
    try step(zero, 0, zero, unit, 1, new java.util.Random(1), _ => throw sentinel)
    catch { case e: RuntimeException if e eq sentinel => propagated = true }
    require(propagated, "Callback exception must propagate")

    // A flat residual must preserve a nonzero-mean, unequal-scale Gaussian reference.
    for (m <- Vector(1, 4, 8)) {
      val rng = new java.util.Random(8719 + m)
      val means = Vector(2.0, -3.0); val sd = Vector(0.5, 2.0)
      var state = means
      var sums = zero; var squares = zero
      for (i <- 0 until 51000) {
        val next = step(state, 0, means, sd, m, rng, _ => 0)
        require(next._3 == m, "Flat residual must accept its first proposal batch")
        state = next._1
        if (i >= 1000) {
          sums = sums.zip(state).map(_ + _)
          squares = squares.zip(state).map((a, b) => a + b * b)
        }
      }
      for (i <- means.indices) {
        val mean = sums(i) / 50000
        val variance = squares(i) / 50000 - mean * mean
        require(math.abs(mean - means(i)) < 0.04 * sd(i), "Gaussian mean control failed")
        require(math.abs(variance / (sd(i) * sd(i)) - 1) < 0.05, "Gaussian variance control failed")
      }
      val a = step(zero, 0, zero, unit, m, new java.util.Random(91), _ => 0)
      val b = step(zero, 0, zero, unit, m, new java.util.Random(91), _ => 0)
      require(a == b && zero == Vector(0, 0), "Reproducibility/immutable state failed")
    }
    // Nonconstant residual: N(0,I) reference times likelihood gives N(0, 1/4 I).
    val rng = new java.util.Random(619L)
    var state = zero
    def narrow(v: Point): Double = -1.5 * v.map(x => x * x).sum
    var sumSquares = 0.0
    for (i <- 0 until 51000) {
      state = step(state, narrow(state), zero, unit, 4, rng, narrow)._1
      if (i >= 1000) sumSquares += state.map(x => x * x).sum
    }
    require(math.abs(sumSquares / 100000 - 0.25) < 0.015, "Residual/prior factorization control failed")
    invalid(quantileSweep(Vector(Double.MaxValue), rng, _ => 0))
    invalid(quantileSweep(zero, rng, _ => Double.NaN))
    invalid(quantileSweep(zero, rng, _ => 0, 0))
    var qstate = zero
    var qsum = zero; var qsquare = zero
    val qrng = new java.util.Random(391L)
    for (i <- 0 until 21000) {
      qstate = quantileSweep(qstate, qrng, targets.head.logDensity)
      if (i >= 1000) {
        qsum = qsum.zip(qstate).map(_ + _)
        qsquare = qsquare.zip(qstate).map((a, b) => a + b * b)
      }
    }
    require(qsum.forall(s => math.abs(s / 20000) < 0.04) && qsquare.forall(s => math.abs(s / 20000 - 1) < 0.05),
      "Quantile Jacobian/Normal target control failed")
    // One-step stationarity from independent exact unequal-mixture draws checks mode weights,
    // without relying on chain convergence or the diagnostic code under comparison.
    val mixture = targets.last
    for (m <- Vector(1, 4, 8, 0)) {
      val r = new java.util.Random(902L + m)
      var positive = 0
      for (_ <- 0 until 10000) {
        val start = Vector((if (r.nextDouble() < 0.8) -4 else 4) + 0.5 * r.nextGaussian(), r.nextGaussian())
        val end = if (m == 0) quantileSweep(start, r, mixture.logDensity)
          else step(start, residual(mixture)(start), zero, unit, m, r, residual(mixture))._1
        if (end.head > 0) positive += 1
      }
      require(math.abs(positive / 10000.0 - 0.2) < 0.025, "Unequal-mode stationarity control failed")
    }
    println("Research kernel checks passed (budgets, invalid densities, propagation, Gaussian and unequal-mode controls, reproducibility).")
  }

  /** Run a bounded research experiment, not a general Figaro inference factory.
    * @param args `check` for regression controls, or repetitions (30) and draws per chain (12000,
    *             at least 2000 and a multiple of 2000), optional comma-separated sampler subset;
    *             four chains, 2000 warm-up, fixed seeds
    * @return Unit; quoted research CSV, or check confirmation; all invalid/search/model errors propagate
    * @example `SamplingResearchExample.main(Array("30", "12000"))`
    */
  def main(args: Array[String]): Unit = {
    if (args.toVector == Vector("check")) { check(); return }
    require(args.length <= 3, "Arguments: check OR repetitions drawsPerChain [samplers]")
    val repeats = args.headOption.map(_.toInt).getOrElse(30)
    val draws = args.lift(1).map(_.toInt).getOrElse(12000)
    val methods = Vector("figaro-block", "mess-1", "mess-4", "mess-8", "qslice-cauchy")
    val selected = args.lift(2).map(_.split(",").toVector).getOrElse(methods)
    require(selected.nonEmpty && selected.distinct.size == selected.size && selected.forall(methods.contains), "Invalid sampler subset")
    require(repeats > 0 && draws >= 2000 && draws % 2000 == 0 && draws <= 1000000, "Invalid research budget")
    def csv(values: Any*): Unit = println(values.map(v => "\"" + v.toString.replace("\"", "\"\"") + "\"").mkString(","))
    csv("research", "target", "sampler", "round", "seed", "method", "draws", "query", "truth", "estimate",
      "fullWidth", "covered", "criteriaMet", "reason", "meanEss", "evaluationsFullRun", "failureReasons")
    for (round <- 0 until repeats; target <- targets; method <- selected) {
      val seed = 141011L + round * 7919L
      val (traces, evaluations) = sampled(target, method, draws, seed)
      var stopped = false
      for (n <- 2000 to draws by 2000 if !stopped || n == draws) {
        val assessments = names.map(k => McmcPrecision.evaluate(traces.map(_(k).take(n)), policy, names.size))
        val all = assessments.forall(_.criteriaMet)
        val stopNow = !stopped && (all || n == draws)
        val records = (if (n == draws) Vector("fixed") else Vector.empty) ++ (if (stopNow) Vector("stopped") else Vector.empty)
        for (record <- records; i <- names.indices) {
          val a = assessments(i)
          csv("research", target.name, method, round, seed, record, n, names(i), target.truths(i), a.diagnostics.mean,
            a.fullWidth.getOrElse(Double.NaN), a.fullWidth.exists(w => math.abs(a.diagnostics.mean - target.truths(i)) <= w / 2),
            a.criteriaMet, if (record == "fixed") "FixedBudget" else if (all) "PrecisionReached" else "MaxDrawsReached",
            a.diagnostics.meanEss.getOrElse(Double.NaN), evaluations, a.failureReasons.mkString("|"))
        }
        if (stopNow) stopped = true
      }
    }
  }
}
