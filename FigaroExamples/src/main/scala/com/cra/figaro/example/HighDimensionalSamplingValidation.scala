package com.cra.figaro.example

import com.cra.figaro.algorithm.sampling.parallel.McmcPrecision
import org.apache.commons.math3.distribution.{NormalDistribution, TDistribution}
import scala.util.control.{NoStackTrace, NonFatal}

/** Isolated d-dimensional research, not a Figaro inference factory.
  * Independently implemented GPSS Algorithms 1-3 (Schar et al., ICML 2023):
  * https://proceedings.mlr.press/v202/schar23a.html.
  * Protocol: docs/SAMPLING_HIGH_DIMENSIONAL.md; no external implementation is imported.
  */
object HighDimensionalSamplingValidation {
  private type Point = Vector[Double]
  private val tau = 2 * math.Pi
  private val targets = Vector("gaussian", "correlated", "banana", "student5", "positive", "asymmetric")
  private val methods = Vector("gpss", "quantile")
  private val dimensions = Vector(8, 32)
  private val queries = Vector("first", "last", "firstSquared", "meanSquare", "cross", "event")
  private val policy = McmcPrecision.Config(relativeTolerance = 0.15, minDrawsPerChain = 2000)
  private val normal = new NormalDistribution(0, 1)
  private val student = new TDistribution(5)
  private case object BudgetReached extends RuntimeException with NoStackTrace
  private def uniform(r: java.util.Random): Double = {
    var u = r.nextDouble()
    while (u == 0) u = r.nextDouble()
    u
  }
  private def norm(x: Point): Double = x.foldLeft(0.0)(math.hypot)
  private def dot(x: Point, y: Point): Double = x.zip(y).map(_ * _).sum
  private def densityValue(v: Double): Double = {
    require(v.isFinite || v == Double.NegativeInfinity, "Invalid log density")
    v
  }
  private def tangent(direction: Point, rng: java.util.Random): Point = {
    require(direction.size >= 2 && direction.forall(_.isFinite) && math.abs(norm(direction) - 1) < 1e-10)
    var attempts = 0
    while (attempts < 100) {
      val noise = direction.map(_ => rng.nextGaussian())
      val projection = dot(noise, direction)
      val first = noise.zip(direction).map((g, u) => g - projection * u)
      // Reorthogonalize to control cancellation when the random vector is nearly parallel.
      val correction = dot(first, direction)
      val orthogonal = first.zip(direction).map((g, u) => g - correction * u)
      val length = norm(orthogonal)
      if (length.isFinite && length > 1e-12) return orthogonal.map(_ / length)
      attempts += 1
    }
    throw new IllegalStateException("Degenerate tangent search exhausted")
  }
  private def polar(current: Point, rng: java.util.Random, logDensity: Point => Double, maxSearch: Int = 10000): Point = {
    require(current.size >= 2 && current.forall(_.isFinite) && maxSearch > 0)
    val radius = norm(current)
    require(radius.isFinite && radius > 0, "Polar origin/overflow unsupported")
    val direction = current.map(_ / radius)
    val perpendicular = tangent(direction, rng)
    val initial = densityValue(logDensity(current)) + (current.size - 1) * math.log(radius)
    require(initial.isFinite, "Invalid current density")
    val level = initial + math.log(uniform(rng))
    require(level.isFinite, "Invalid slice level")
    var evaluations = 0
    def accepted(rad: Double, dir: Point): Boolean = {
      if (Thread.currentThread().isInterrupted) throw new InterruptedException("Polar kernel interrupted")
      evaluations += 1
      if (evaluations > maxSearch) throw new IllegalStateException("Polar search exhausted")
      require(rad.isFinite && rad > 0, "Invalid proposed radius")
      densityValue(logDensity(dir.map(_ * rad))) + (current.size - 1) * math.log(rad) > level
    }
    var angle = tau * uniform(rng)
    var left = angle - tau; var right = angle
    def candidateDirection: Point = direction.zip(perpendicular).map((a, b) => a * math.cos(angle) + b * math.sin(angle))
    var dir = candidateDirection
    while (!accepted(radius, dir)) {
      if (angle < 0) left = angle else right = angle
      angle = left + uniform(rng) * (right - left)
      require(angle > left && angle < right, "Angular bracket collapsed")
      dir = candidateDirection
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
  private def density(target: String)(x: Point): Double = target match {
    case "gaussian" => -0.5 * x.map(v => v * v).sum
    case "correlated" =>
      // Equicorrelation inverse; rewriting around the sample mean avoids subtracting large sums.
      val mean = x.sum / x.size
      -0.5 * (x.map(v => math.pow(v - mean, 2)).sum / 0.05 + x.size * mean * mean / (0.05 + 0.95 * x.size))
    case "banana" => x.grouped(2).map(p => -0.5 * p(0) * p(0) - 2 * math.pow(p(1) - 0.4 * (p(0) * p(0) - 1), 2)).sum
    case "student5" => -(5 + x.size) * 0.5 * math.log1p(x.map(v => v * v).sum / 5)
    case "positive" => if (x.forall(_ > 0)) -x.sum else Double.NegativeInfinity
    case "asymmetric" =>
      val a = math.log(0.9) - 2 * x.map(v => math.pow(v + 2, 2)).sum
      val b = math.log(0.1) - 2 * x.map(v => math.pow(v - 3, 2)).sum
      val m = math.max(a, b)
      if (m == Double.NegativeInfinity) m else m + math.log1p(math.exp(math.min(a, b) - m))
  }
  private def truths(target: String): Point = target match {
    case "gaussian" => Vector(0, 0, 1, 1, 0, 2 * normal.cumulativeProbability(-2))
    case "correlated" => Vector(0, 0, 1, 1, 0.95, 2 * normal.cumulativeProbability(-2))
    case "banana" => Vector(0, 0, 1, 0.785, 0, 2 * normal.cumulativeProbability(-2))
    case "student5" => Vector(0, 0, 5.0 / 3, 5.0 / 3, 0, 2 * student.cumulativeProbability(-2))
    case "positive" => Vector(1, 1, 2, 2, 1, math.exp(-2))
    case "asymmetric" => Vector(-1.5, -1.5, 4.75, 4.75, 4.5,
      0.9 * normal.cumulativeProbability(-4) + 0.1 * normal.cumulativeProbability(6))
  }
  private def project(x: Point, target: String): Point = {
    val event = if (target == "asymmetric") x.head > 0 else if (target == "positive") x.head > 2 else math.abs(x.head) > 2
    Vector(x.head, x.last, x.head * x.head, x.map(v => v * v).sum / x.size, x.head * x.last, if (event) 1 else 0)
  }
  // Exact independent target draws are for controls only, never benchmark initialization.
  private def exact(target: String, d: Int, r: java.util.Random): Point = target match {
    case "gaussian" => Vector.fill(d)(r.nextGaussian())
    case "correlated" => val shared = math.sqrt(0.95) * r.nextGaussian(); Vector.fill(d)(shared + math.sqrt(0.05) * r.nextGaussian())
    case "banana" => Vector.fill(d / 2) { val z = r.nextGaussian(); Vector(z, 0.4 * (z * z - 1) + 0.5 * r.nextGaussian()) }.flatten
    case "student5" => val scale = math.sqrt(Vector.fill(5)(math.pow(r.nextGaussian(), 2)).sum / 5); Vector.fill(d)(r.nextGaussian() / scale)
    case "positive" => Vector.fill(d)(-math.log(uniform(r)))
    case "asymmetric" => val center = if (r.nextDouble() < 0.9) -2 else 3; Vector.fill(d)(center + 0.5 * r.nextGaussian())
  }
  private final case class Trace(values: Vector[Point], costs: Vector[Int], warm: Int, spent: Int, error: String)
  private def chain(target: String, method: String, d: Int, seed: Long, cap: Int): Trace = {
    require(d >= 2 && d % 2 == 0 && cap > 0 && targets.contains(target) && methods.contains(method))
    var spent = 0; var warm = 0; var error = ""
    val values = Vector.newBuilder[Point]; val costs = Vector.newBuilder[Int]
    def counted(x: Point): Double = {
      if (Thread.currentThread().isInterrupted) throw new InterruptedException("Research interrupted")
      if (spent == cap) throw BudgetReached
      spent += 1
      require(x.size == d && x.forall(_.isFinite), "Invalid proposed point")
      densityValue(density(target)(x))
    }
    try {
      val r = new java.util.Random(seed)
      var state = Vector.fill(d) { val z = r.nextGaussian(); if (target == "positive") 0.1 + math.abs(z) else z }
      require(counted(state).isFinite, "Invalid initial density")
      var n = 0
      while (true) {
        state = if (method == "gpss") polar(state, r, counted) else SamplingResearchExample.quantileSweep(state, r, counted)
        n += 1
        if (n == 200) warm = spent
        if (n > 200) { values += project(state, target); costs += spent }
      }
    } catch {
      case BudgetReached => ()
      case e: InterruptedException => throw e
      case NonFatal(e) => error = e.getClass.getSimpleName + ":" + Option(e.getMessage).getOrElse("")
    }
    Trace(values.result(), costs.result(), if (warm == 0) spent else warm, spent, error)
  }
  private final case class Report(budget: Int, n: Int, available: Int, spent: Int, warm: Int,
    status: String, values: Vector[(Double, Double, Double, Boolean, String)]) {
    def reached: Boolean = status == "Ok" && values.forall(_._4)
  }
  private def assess(traces: Vector[Trace], budget: Int): Report = {
    val counts = traces.map(_.costs.takeWhile(_ <= budget).size)
    val n = counts.min
    val errors = traces.map(_.error).filter(_.nonEmpty).mkString("|")
    val status = if (errors.nonEmpty) errors else if (n < 4) "InsufficientTrace" else "Ok"
    val results = queries.indices.map { j =>
      if (status != "Ok") (Double.NaN, Double.NaN, Double.NaN, false, status)
      else {
        val a = McmcPrecision.evaluate(traces.map(_.values.take(n).map(_(j))), policy, queries.size)
        (a.diagnostics.mean, a.fullWidth.getOrElse(Double.NaN), a.diagnostics.meanEss.getOrElse(Double.NaN), a.criteriaMet, a.failureReasons.mkString("|"))
      }
    }.toVector
    Report(budget, n, counts.sum, traces.map(t => math.min(t.spent, budget)).sum,
      traces.map(t => math.min(t.warm, budget)).sum, status, results)
  }
  private def check(): Unit = {
    def invalid(f: => Any): Unit = { var caught = false; try f catch { case _: IllegalArgumentException => caught = true }; require(caught) }
    val rng = new java.util.Random(331919)
    invalid(polar(Vector(0, 0), rng, _ => 0))
    invalid(polar(Vector(1), rng, _ => 0))
    invalid(polar(Vector(1, 1), rng, _ => Double.NaN))
    invalid(polar(Vector(1, 1), rng, _ => Double.PositiveInfinity))
    var exhausted = false
    try polar(Vector(1, 1), rng, density("gaussian"), 1) catch { case _: IllegalStateException => exhausted = true }
    require(exhausted)
    val sentinel = new RuntimeException("callback")
    var propagated = false
    try polar(Vector(1, 1), rng, _ => throw sentinel) catch { case e: RuntimeException if e eq sentinel => propagated = true }
    require(propagated)
    for (d <- Vector(2, 8, 32)) {
      val dir = Vector.fill(d)(rng.nextGaussian()); val unit = dir.map(_ / norm(dir))
      var tangentSquares = Vector.fill(d)(0.0)
      for (_ <- 0 until 10000) {
        val t = tangent(unit, rng)
        require(math.abs(norm(t) - 1) < 1e-12 && math.abs(dot(unit, t)) < 1e-12)
        tangentSquares = tangentSquares.zip(t).map((a, b) => a + b * b)
      }
      require(unit.indices.forall(i => math.abs(tangentSquares(i) / 10000 - (1 - unit(i) * unit(i)) / (d - 1)) < 0.02))
    }
    // Independently generated exact starts test preservation, not convergence from typical starts.
    for (d <- dimensions; target <- targets; method <- methods) {
      var sums = Vector.fill(6)(0.0)
      val count = 12000
      for (_ <- 0 until count) {
        val start = exact(target, d, rng)
        val end = if (method == "gpss") polar(start, rng, density(target))
          else SamplingResearchExample.quantileSweep(start, rng, density(target))
        if (target == "positive") require(end.forall(_ > 0), "Support violation")
        sums = sums.zip(project(end, target)).map(_ + _)
      }
      val tolerance = Vector(0.10, 0.10, 0.25, 0.20, 0.20, 0.025)
      require(sums.indices.forall(i => math.abs(sums(i) / count - truths(target)(i)) < tolerance(i)),
        s"Stationarity failed: $d/$target/$method ${sums.map(_ / count)}")
    }
    for (method <- methods) {
      val a = chain("gaussian", method, 8, 101, 20000)
      val b = chain("gaussian", method, 8, 101, 20000)
      require(a == b && a.error.isEmpty && a.values.nonEmpty && a.spent == 20000)
      val short = chain("gaussian", method, 8, 101, 10000)
      require(short.values == a.values.take(a.costs.takeWhile(_ <= 10000).size))
      require(chain("gaussian", method, 8, 101, 1).values.isEmpty)
    }
    Thread.currentThread().interrupt()
    var interrupted = false
    try chain("gaussian", "gpss", 8, 1, 100) catch { case _: InterruptedException => interrupted = true }
    finally Thread.interrupted()
    require(interrupted)
    println("Higher-dimensional controls passed: tangent geometry, analytic stationarity, support, budgets, replay, failures and interruption.")
  }
  /** Run controls or the predeclared higher-dimensional screen.
    * @param args `check`, or positive repetitions (20), per-chain evaluation cap (300000,
    *             20000-1000000 divisible by four), and nonnegative first round (0)
    * @return Unit; quoted CSV. Invalid arguments throw; model/search failures are explicit records;
    *         interruption aborts. No production inference object is returned.
    * @example `HighDimensionalSamplingValidation.main(Array("1", "20000"))`
    */
  def main(args: Array[String]): Unit = {
    if (args.toVector == Vector("check")) { check(); return }
    require(args.length <= 3)
    val repetitions = args.headOption.map(_.toInt).getOrElse(20)
    val cap = args.lift(1).map(_.toInt).getOrElse(300000)
    val first = args.lift(2).map(_.toInt).getOrElse(0)
    require(repetitions > 0 && first >= 0 && first.toLong + repetitions <= Int.MaxValue && cap >= 20000 && cap <= 1000000 && cap % 4 == 0)
    def csv(v: Any*): Unit = println(v.map(x => "\"" + x.toString.replace("\"", "\"\"") + "\"").mkString(","))
    csv("highDimensional", "dimension", "target", "sampler", "round", "seed", "record", "budgetPerChain", "drawsPerChain",
      "availableDraws", "evaluations", "warmupEvaluations", "status", "reason", "query", "truth", "estimate", "fullWidth", "meanEss", "covered", "criteriaMet", "failureReasons")
    for (round <- first until first + repetitions; d <- dimensions; target <- targets; method <- methods) {
      val seed = 1700113L + 130363L * round
      val seeds = new java.util.SplittableRandom(seed)
      val traces = Vector.fill(4)(chain(target, method, d, seeds.nextLong(), cap))
      val reports = Vector(cap / 4, cap / 2, cap).map(assess(traces, _))
      val stop = reports.find(_.reached).getOrElse(reports.last)
      for ((kind, report) <- reports.map("fixed" -> _) :+ ("stopped" -> stop); i <- queries.indices) {
        val (mean, width, ess, passed, reasons) = report.values(i)
        val reason = if (report.status != "Ok") "RunFailure" else if (kind == "fixed") "FixedBudget"
          else if (report.reached) "PrecisionReached" else "MaxEvaluationsReached"
        csv("highDimensional", d, target, method, round, seed, kind, report.budget, report.n, report.available,
          report.spent, report.warm, report.status, reason, queries(i), truths(target)(i), mean, width, ess,
          width.isFinite && math.abs(mean - truths(target)(i)) <= width / 2, passed, reasons)
      }
    }
  }
}
