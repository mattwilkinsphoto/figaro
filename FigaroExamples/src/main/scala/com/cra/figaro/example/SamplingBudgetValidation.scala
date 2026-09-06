package com.cra.figaro.example

import com.cra.figaro.algorithm.sampling.parallel.McmcPrecision
import org.apache.commons.math3.distribution.{NormalDistribution, TDistribution}
import scala.util.control.{NoStackTrace, NonFatal}

/** Research-only matched target-evaluation budgets. See docs/SAMPLING_BUDGET_VALIDATION.md.
  * Independently implemented GPSS (Schar et al., ICML 2023, Algorithms 1-3) and finite
  * affine tuning (Schar et al., ICML 2024, Algorithm 1). Not the shared-chain PATT system.
  * https://proceedings.mlr.press/v202/schar23a.html; https://arxiv.org/html/2401.16567v2
  */
object SamplingBudgetValidation {
  private type Point = Vector[Double]
  private val zero = Vector(0.0, 0.0)
  private val tau = 2 * math.Pi
  private val queries = Vector("x", "y", "xSquared", "ySquared", "event")
  private val methods = Vector("rwm", "quantile", "gpss", "affine-gpss")
  private val policy = McmcPrecision.Config(relativeTolerance = 0.15, minDrawsPerChain = 2000)
  private val normal = new NormalDistribution(0, 1)
  private val student = new TDistribution(5)
  private case object BudgetReached extends RuntimeException with NoStackTrace
  private final case class Target(name: String, density: Point => Double, truths: Point, event: Double => Double)
  private def logAdd(a: Double, b: Double): Double = {
    val m = math.max(a, b)
    if (m == Double.NegativeInfinity) m else m + math.log1p(math.exp(math.min(a, b) - m))
  }
  private val tail: Double => Double = x => if (math.abs(x) > 2) 1.0 else 0.0
  private val targets = Vector(
    Target("gaussian", v => -0.5 * v.map(x => x * x).sum,
      Vector(0, 0, 1, 1, 2 * normal.cumulativeProbability(-2)), tail),
    Target("rotated", v => -math.pow(v(0) + v(1), 2) / 36 - math.pow(v(0) - v(1), 2) / 0.04,
      Vector(0, 0, 4.505, 4.505, 2 * normal.cumulativeProbability(-2 / math.sqrt(4.505))), tail),
    Target("banana", v => -0.5 * v(0) * v(0) - 2 * math.pow(v(1) - 0.4 * (v(0) * v(0) - 1), 2),
      Vector(0, 0, 1, 0.57, 2 * normal.cumulativeProbability(-2)), tail),
    Target("student5", v => -3.5 * math.log1p(v.map(x => x * x).sum / 5),
      Vector(0, 0, 5.0 / 3, 5.0 / 3, 2 * student.cumulativeProbability(-2)), tail),
    Target("unequal-modes", v => logAdd(math.log(0.8) - 2 * math.pow(v(0) + 4, 2),
      math.log(0.2) - 2 * math.pow(v(0) - 4, 2)) - 0.5 * v(1) * v(1),
      Vector(-2.4, 0, 16.25, 1, 0.8 * normal.cumulativeProbability(-8) + 0.2 * normal.cumulativeProbability(8)),
      x => if (x > 0) 1 else 0))

  private def uniform(r: java.util.Random): Double = {
    var u = r.nextDouble()
    while (u == 0) u = r.nextDouble()
    u
  }
  private def validDensity(x: Double): Double = {
    require(x.isFinite || x == Double.NegativeInfinity, "Invalid target density")
    x
  }
  private final class Counter(target: Point => Double, val limit: Int) {
    require(limit > 0, "Positive evaluation cap required")
    var spent = 0
    def apply(x: Point): Double = {
      if (Thread.currentThread().isInterrupted) throw new InterruptedException("Research interrupted")
      if (spent == limit) throw BudgetReached
      spent += 1
      require(x.size == 2 && x.forall(_.isFinite), "Invalid target point")
      validDensity(target(x))
    }
  }
  // Lower-triangular affine map. Its constant Jacobian cancels in slice comparisons.
  private final case class Affine(center: Point = zero, a: Double = 1, b: Double = 0, c: Double = 1) {
    require(center.size == 2 && center.forall(_.isFinite) && a.isFinite && b.isFinite && c.isFinite && a > 0 && c > 0)
    def forward(z: Point): Point = Vector(center(0) + a * z(0), center(1) + b * z(0) + c * z(1))
    def inverse(x: Point): Point = {
      val z = (x(0) - center(0)) / a
      Vector(z, (x(1) - center(1) - b * z) / c)
    }
  }
  private final class Moments {
    private var n = 0
    private var mean = zero
    private var xx = 0.0; private var xy = 0.0; private var yy = 0.0
    def add(v: Point): Unit = {
      n += 1
      val d = v.zip(mean).map(_ - _)
      mean = mean.zip(d).map((m, delta) => m + delta / n)
      val e = v.zip(mean).map(_ - _)
      xx += d(0) * e(0); xy += d(0) * e(1); yy += d(1) * e(1)
    }
    def fitted: Affine = {
      require(n >= 2, "Insufficient pilot")
      val ridge = 1e-6 * math.max(1, (xx + yy) / (2 * (n - 1)))
      val a = math.sqrt(xx / (n - 1) + ridge)
      val b = xy / (n - 1) / a
      Affine(mean, a, b, math.sqrt(yy / (n - 1) + ridge - b * b))
    }
  }

  // Two-dimensional GPSS variant 3: polar-Jacobian slice, geodesic shrink, then
  // unit-width radial stepping-out and shrink. A search limit aborts, never accepts.
  private def polar(x: Point, logTarget: Point => Double, r: java.util.Random, maxSearch: Int = 10000): Point = {
    require(x.size == 2 && x.forall(_.isFinite) && maxSearch > 0)
    val radius = math.hypot(x(0), x(1))
    require(radius > 0 && radius.isFinite, "Polar origin/overflow is unsupported")
    val direction = x.map(_ / radius)
    val sign = if (r.nextBoolean()) 1.0 else -1.0
    val tangent = Vector(-sign * direction(1), sign * direction(0))
    val initial = validDensity(logTarget(x)) + math.log(radius) // (d - 1) log r, d = 2
    require(initial.isFinite, "Invalid current polar density")
    val level = initial + math.log(uniform(r))
    require(level.isFinite, "Invalid polar slice level")
    var searches = 0
    def onSlice(rad: Double, dir: Point): Boolean = {
      if (Thread.currentThread().isInterrupted) throw new InterruptedException("Polar search interrupted")
      searches += 1
      if (searches > maxSearch) throw new IllegalStateException("Polar search exhausted")
      require(rad > 0 && rad.isFinite && dir.forall(_.isFinite), "Invalid polar proposal")
      validDensity(logTarget(dir.map(_ * rad))) + math.log(rad) > level
    }
    var angle = tau * uniform(r)
    var left = angle - tau; var right = angle
    def proposedDirection: Point = direction.zip(tangent).map((a, b) => a * math.cos(angle) + b * math.sin(angle))
    var dir = proposedDirection
    while (!onSlice(radius, dir)) {
      if (angle < 0) left = angle else right = angle
      angle = left + uniform(r) * (right - left)
      require(angle > left && angle < right, "Angular bracket collapsed")
      dir = proposedDirection
    }
    val offset = uniform(r)
    var low = math.max(0, radius - offset)
    var high = radius + 1 - offset
    while (low > 0 && onSlice(low, dir)) low = math.max(0, low - 1)
    while (onSlice(high, dir)) { val next = high + 1; require(next > high, "Radial expansion collapsed"); high = next }
    var rad = low + uniform(r) * (high - low)
    require(rad > low && rad < high, "Radial bracket collapsed")
    while (!onSlice(rad, dir)) {
      if (rad < radius) low = rad else high = rad
      rad = low + uniform(r) * (high - low)
      require(rad > low && rad < high, "Radial bracket collapsed")
    }
    dir.map(_ * rad)
  }

  private final case class Trace(points: Vector[Point], costs: Vector[Int], pilotCost: Int,
    warmCost: Int, spent: Int, error: String)
  private def chain(target: Target, method: String, seed: Long, cap: Int): Trace = {
    val counter = new Counter(target.density, cap)
    val r = new java.util.Random(seed)
    val points = Vector.newBuilder[Point]; val costs = Vector.newBuilder[Int]
    var pilotCost = 0; var warmCost = 0; var error = ""
    try {
      var state = Vector.fill(2)(r.nextGaussian())
      var currentDensity = counter(state)
      require(currentDensity.isFinite, "Invalid initial density")
      var map = Affine()
      val moments = new Moments
      var iteration = 0
      while (true) {
        state = method match {
          case "rwm" =>
            val candidate = state.map(_ + r.nextGaussian())
            val proposed = counter(candidate)
            if (math.log(uniform(r)) < proposed - currentDensity) { currentDensity = proposed; candidate } else state
          case "quantile" => SamplingResearchExample.quantileSweep(state, r, counter.apply)
          case "gpss" | "affine-gpss" => map.forward(polar(map.inverse(state), z => counter(map.forward(z)), r))
        }
        iteration += 1
        if (iteration <= 500 && method == "affine-gpss") {
          moments.add(state)
          if (iteration == 100 || iteration == 250 || iteration == 500) map = moments.fitted
        }
        if (iteration == 500) pilotCost = counter.spent
        if (iteration == 1000) warmCost = counter.spent
        if (iteration > 1000) { points += state; costs += counter.spent }
      }
    } catch {
      case BudgetReached => () // Terminal budget boundary; no incomplete transition is recorded.
      case e: InterruptedException => throw e
      case NonFatal(e) => error = e.getClass.getSimpleName + ":" + Option(e.getMessage).getOrElse("")
    }
    Trace(points.result(), costs.result(), if (pilotCost == 0) counter.spent else pilotCost,
      if (warmCost == 0) counter.spent else warmCost, counter.spent, error)
  }
  private def projection(p: Point, target: Target): Point = Vector(p(0), p(1), p(0) * p(0), p(1) * p(1), target.event(p(0)))
  private final case class Report(budget: Int, n: Int, available: Int, evaluations: Int, pilot: Int, warm: Int,
    status: String, values: Vector[(Double, Double, Double, Boolean, String)]) {
    def reached: Boolean = status == "Ok" && values.forall(_._4)
  }
  private def assess(traces: Vector[Trace], target: Target, budget: Int): Report = {
    val counts = traces.map(_.costs.takeWhile(_ <= budget).size)
    val n = counts.min
    val error = traces.map(_.error).filter(_.nonEmpty).mkString("|")
    val status = if (error.nonEmpty) error else if (n < 4) "InsufficientTrace" else "Ok"
    val projected = traces.map(_.points.take(n).map(projection(_, target)))
    val values = queries.indices.map { j =>
      if (status != "Ok") (Double.NaN, Double.NaN, Double.NaN, false, status)
      else {
        val a = McmcPrecision.evaluate(projected.map(_.map(_(j))), policy, queries.size)
        (a.diagnostics.mean, a.fullWidth.getOrElse(Double.NaN), a.diagnostics.meanEss.getOrElse(Double.NaN),
          a.criteriaMet, a.failureReasons.mkString("|"))
      }
    }.toVector
    Report(budget, n, counts.sum, traces.map(t => math.min(t.spent, budget)).sum,
      traces.map(t => math.min(t.pilotCost, budget)).sum, traces.map(t => math.min(t.warmCost, budget)).sum, status, values)
  }

  private def check(): Unit = {
    def invalid(f: => Any): Unit = {
      var caught = false
      try f catch { case _: IllegalArgumentException => caught = true }
      require(caught, "Expected validation failure")
    }
    invalid(polar(zero, _ => 0, new java.util.Random(1)))
    invalid(polar(Vector(1, 1), _ => Double.NaN, new java.util.Random(1)))
    invalid(polar(Vector(1, 1), _ => Double.PositiveInfinity, new java.util.Random(1)))
    invalid(Affine(a = 0))
    val map = Affine(Vector(2, -3), 2, 0.7, 0.2)
    val x = Vector(3.0, 4.0)
    require(map.inverse(map.forward(x)).zip(x).forall((a, b) => math.abs(a - b) < 1e-12))
    val counter = new Counter(_ => 0, 1)
    counter(x)
    var bounded = false
    try counter(x) catch { case BudgetReached => bounded = true }
    require(bounded && counter.spent == 1)
    var exhausted = false
    try polar(x, targets.head.density, new java.util.Random(19), 1)
    catch { case _: IllegalStateException => exhausted = true }
    require(exhausted, "Search exhaustion must not manufacture a draw")
    val sentinel = new RuntimeException("callback")
    var propagated = false
    try polar(x, _ => throw sentinel, new java.util.Random(1))
    catch { case e: RuntimeException if e eq sentinel => propagated = true }
    require(propagated)
    // One-step stationarity from independent analytic draws: covariance, radial Jacobian,
    // heavy tails, nonlinear curvature, and unequal mode mass, without MCMC diagnostics.
    val r = new java.util.Random(70921)
    for (target <- targets; affine <- Vector(Affine(), map)) {
      var sums = Vector.fill(5)(0.0)
      val total = 30000
      for (_ <- 0 until total) {
        val z = r.nextGaussian(); val e = r.nextGaussian()
        val start = target.name match {
          case "gaussian" => Vector(z, e)
          case "rotated" => Vector((3 * z + 0.1 * e) / math.sqrt(2), (3 * z - 0.1 * e) / math.sqrt(2))
          case "banana" => Vector(z, 0.4 * (z * z - 1) + 0.5 * e)
          case "student5" =>
            val scale = math.sqrt(Vector.fill(5)(math.pow(r.nextGaussian(), 2)).sum / 5)
            Vector(z / scale, e / scale)
          case "unequal-modes" => Vector((if (r.nextDouble() < 0.8) -4 else 4) + 0.5 * z, e)
        }
        val end = affine.forward(polar(affine.inverse(start), v => target.density(affine.forward(v)), r))
        sums = sums.zip(projection(end, target)).map(_ + _)
      }
      val tolerance = Vector(0.06, 0.06, 0.14, 0.14, 0.015)
      require(sums.indices.forall(i => math.abs(sums(i) / total - target.truths(i)) < tolerance(i)),
        s"Stationarity failed: ${target.name}, $affine, ${sums.map(_ / total)}")
    }
    for (method <- methods) {
      val a = chain(targets.head, method, 1234, 30000)
      val b = chain(targets.head, method, 1234, 30000)
      require(a == b && a.error.isEmpty && a.spent == 30000 && a.points.nonEmpty)
      require(a.costs.sliding(2).forall(v => v.size < 2 || v(0) < v(1)))
      require(a.pilotCost < a.warmCost && a.warmCost < a.costs.head && a.costs.last <= a.spent)
      val truncated = chain(targets.head, method, 1234, 25000)
      require(truncated.points == a.points.take(a.costs.takeWhile(_ <= 25000).size), "Budget replay mismatch")
    }
    println("Budget kernel checks passed: stationarity, affine maps, hard caps, replay, failures, reproducibility.")
  }

  /** Run research controls or a complete four-method/five-target experiment.
    * @param args `check`, or positive repetitions (30), evaluation cap per chain (100000,
    *             20000-1000000 and divisible by 4), optional zero-based first round (0)
    * @return Unit; writes quoted CSV. Invalid arguments throw; per-chain model/search failures
    *         become explicit failed records, while interruption aborts the experiment.
    * @example `SamplingBudgetValidation.main(Array("1", "30000"))`
    */
  def main(args: Array[String]): Unit = {
    if (args.toVector == Vector("check")) { check(); return }
    require(args.length <= 3, "Arguments: check OR repetitions cap [firstRound]")
    val repeats = args.headOption.map(_.toInt).getOrElse(30)
    val cap = args.lift(1).map(_.toInt).getOrElse(100000)
    val first = args.lift(2).map(_.toInt).getOrElse(0)
    require(repeats > 0 && first >= 0 && first.toLong + repeats <= Int.MaxValue && cap >= 20000 && cap <= 1000000 && cap % 4 == 0)
    def csv(v: Any*): Unit = println(v.map(x => "\"" + x.toString.replace("\"", "\"\"") + "\"").mkString(","))
    csv("budgetResearch", "target", "sampler", "round", "seed", "record", "budgetPerChain", "drawsPerChain",
      "availableDraws", "evaluations", "pilotEvaluations", "warmupEvaluations", "status", "reason", "query", "truth",
      "estimate", "fullWidth", "meanEss", "covered", "criteriaMet", "failureReasons")
    for (round <- first until first + repeats; target <- targets; method <- methods) {
      val seed = 812031L + 104729L * round
      val seeds = new java.util.SplittableRandom(seed)
      val traces = Vector.fill(4)(chain(target, method, seeds.nextLong(), cap))
      val reports = Vector(cap / 4, cap / 2, cap).map(assess(traces, target, _))
      val stop = reports.find(_.reached).getOrElse(reports.last)
      for ((kind, report) <- reports.map("fixed" -> _) :+ ("stopped" -> stop); i <- queries.indices) {
        val (mean, width, ess, passed, failures) = report.values(i)
        val reason = if (report.status != "Ok") "RunFailure" else if (kind == "fixed") "FixedBudget"
          else if (report.reached) "PrecisionReached" else "MaxEvaluationsReached"
        csv("budgetResearch", target.name, method, round, seed, kind, report.budget, report.n, report.available,
          report.evaluations, report.pilot, report.warm, report.status, reason, queries(i), target.truths(i), mean,
          width, ess, width.isFinite && math.abs(mean - target.truths(i)) <= width / 2, passed, failures)
      }
    }
  }
}
