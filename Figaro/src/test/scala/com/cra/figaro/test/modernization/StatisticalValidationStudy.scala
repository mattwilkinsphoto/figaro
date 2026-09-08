package com.cra.figaro.test.modernization

import com.cra.figaro.algorithm.sampling.{Importance, OneTimeProbQuerySampler}
import com.cra.figaro.language.*
import com.cra.figaro.library.atomic.continuous.{Gamma, Dirichlet, Uniform}
import com.cra.figaro.util.withRandomSeed
import org.apache.commons.math3.special.Gamma.logGamma

/** Test/research harness, NOT a public inference API. See docs/STATISTICAL_VALIDATION.md. */
object StatisticalValidationStudy {
  val seeds: Vector[Long] = Vector.tabulate(30)(i => 1009L + i * 7919L)
  val budgets = Vector(2000, 20000, 200000)
  private def integerGamma(r: java.util.Random, shape: Int): Double =
    (0 until shape).map(_ => -math.log1p(-r.nextDouble())).sum
  val gammaData: Vector[Double] = {
    val r = new java.util.Random(104729)
    Vector.fill(200)(2 * integerGamma(r, 2))
  }
  val dirichletData: Vector[Vector[Double]] = {
    val r = new java.util.Random(130363)
    Vector.fill(200) {
      val values = Vector(1, 2, 3).map(integerGamma(r, _))
      values.map(_ / values.sum)
    }
  }
  val sumGamma: Double = gammaData.sum
  val sumLogGamma: Double = gammaData.map(math.log).sum
  val sumLogDirichlet: Vector[Double] = Vector.tabulate(3)(i => dirichletData.map(x => math.log(x(i))).sum)
  val references: Map[String, Vector[Double]] = Map(
    "gamma" -> Vector(2.107869973169636, 1.844729346924452),
    "dirichlet" -> Vector(0.841016150722901, 1.910950755236635, 2.640339278723853))
  val posteriorSd: Map[String, Vector[Double]] = Map(
    "gamma" -> Vector(0.1967401705530933, 0.1970468052314994),
    "dirichlet" -> Vector(0.0608399843880491, 0.1434315061608727, 0.2005600807508122))

  def logLikelihood(family: String, x: Vector[Double]): Double = {
    require(references.contains(family) && x.size == references(family).size)
    if (x.exists(v => !v.isFinite || v <= 0 || v >= 10)) Double.NegativeInfinity
    else if (family == "gamma")
      (x(0)-1)*sumLogGamma - sumGamma/x(1) - 200*(logGamma(x(0)) + x(0)*math.log(x(1)))
    else 200*(logGamma(x.sum)-x.map(logGamma).sum) + x.indices.map(i => (x(i)-1)*sumLogDirichlet(i)).sum
  }

  final case class Summary(mean: Vector[Double], mcse: Vector[Double], ess: Double, maxWeight: Double)

  /** Streaming log-scaled SNIS moments; MCSE is an asymptotic diagnostic, not a bound. */
  final class Accumulator(dimension: Int) {
    private var maximum = Double.NegativeInfinity
    private var sum = 0.0
    private var sum2 = 0.0
    private val first = Array.fill(dimension)(0.0)
    private val squareFirst = Array.fill(dimension)(0.0)
    private val squareSecond = Array.fill(dimension)(0.0)
    def add(logWeight: Double, x: Vector[Double]): Unit = {
      require(x.size == dimension && x.forall(_.isFinite))
      require(!logWeight.isNaN && logWeight != Double.PositiveInfinity)
      if (logWeight != Double.NegativeInfinity) {
        if (logWeight > maximum) {
          val scale = math.exp(maximum-logWeight)
          sum *= scale; sum2 *= scale*scale
          for (i <- x.indices) {
            first(i) *= scale
            squareFirst(i) *= scale*scale
            squareSecond(i) *= scale*scale
          }
          maximum = logWeight
        }
        val w = math.exp(logWeight-maximum)
        sum += w; sum2 += w*w
        for (i <- x.indices) {
          first(i) += w*x(i)
          squareFirst(i) += w*w*x(i)
          squareSecond(i) += w*w*x(i)*x(i)
        }
      }
    }
    def result: Summary = {
      require(sum > 0, "No positive total importance weight")
      val mean = first.toVector.map(_/sum)
      val mcse = mean.indices.map { i =>
        math.sqrt(math.max(0, squareSecond(i)-2*mean(i)*squareFirst(i)+mean(i)*mean(i)*sum2))/sum
      }.toVector
      Summary(mean, mcse, sum*sum/sum2, 1/sum)
    }
  }

  def generator(name: String, seed: Long): java.util.random.RandomGenerator = name match {
    case "Random" => new java.util.Random(seed)
    case "L64X128MixRandom" => java.util.random.RandomGeneratorFactory.of[java.util.random.RandomGenerator](name).create(seed)
    case _ =>
      val algorithm = com.cra.figaro.util.SamplingRandom.Algorithm.values.find(_.id == name)
        .getOrElse(throw new IllegalArgumentException("Unreviewed generator: " + name))
      com.cra.figaro.util.SamplingRandom.seeded(seed, algorithm)
  }

  def kernel(family: String, algorithm: String, seed: Long, count: Int): Summary = {
    require(count > 0)
    val r = generator(algorithm, seed)
    val d = references(family).size
    val accumulator = new Accumulator(d)
    for (_ <- 0 until count) {
      val x = Vector.fill(d)(10*r.nextDouble())
      accumulator.add(logLikelihood(family, x), x)
    }
    accumulator.result
  }

  /** Exercise the real 200-observation CompoundGamma/Dirichlet + Importance path.
    * Compare every finite sample log weight to the independent sufficient-stat form.
    * Legacy density-to-log underflow is counted, not silently repaired in this study.
    */
  def graph(family: String, seed: Long, count: Int): (Summary, Int) =
    withRandomSeed(seed, com.cra.figaro.util.SamplingRandom.Algorithm.LegacyJava) {
    val u = Universe.createNew()
    val parameters = Vector.tabulate(references(family).size)(i => Uniform(0, 10)(using s"p$i", u))
    val joint = Inject(parameters*)(using "joint", u)
    if (family == "gamma") gammaData.foreach { value =>
      Gamma(parameters(0), parameters(1))(using "", u).observe(value)
    }
    else dirichletData.foreach { value =>
      Dirichlet(parameters*)(using "", u).observe(value.toArray)
    }
    val accumulator = new Accumulator(parameters.size)
    var underflows = 0
    val alg = new Importance(u, joint) with OneTimeProbQuerySampler {
      val numSamples = count
      override protected def updateWeightSeenForTarget[T](sample: Sample, seen: WeightSeen[T]): Unit = {
        val x = sample._2(joint).asInstanceOf[List[Double]].toVector
        val expected = logLikelihood(family, x)
        if (sample._1 == Double.NegativeInfinity && expected.isFinite) underflows += 1
        else require(math.abs(sample._1-expected) <= 1e-7*math.max(1, math.abs(expected)),
          s"Likelihood mismatch: ${sample._1} versus $expected")
        accumulator.add(sample._1, x)
        super.updateWeightSeenForTarget(sample, seen)
      }
    }
    try {
      alg.start()
      val result = accumulator.result
      for (i <- parameters.indices) {
        val actual = alg.expectation(joint, (xs: List[Double]) => xs(i))
        require(math.abs(actual-result.mean(i)) < 1e-8, "Importance aggregation mismatch")
      }
      (result, underflows)
    } finally { if (alg.isActive) alg.kill(); u.clear() }
  }

  private def emit(mode: String, family: String, rng: String, seed: Long, count: Int,
      summary: Summary, elapsed: Double, underflows: Int): Unit = {
    for (i <- summary.mean.indices) {
      val error = summary.mean(i)-references(family)(i)
      val covered = math.abs(error) <= 1.959963984540054*summary.mcse(i)
      val accurate = math.abs(error) <= 0.1*posteriorSd(family)(i)
      println(s"SV,$mode,$family,$rng,$seed,$count,$i,${summary.mean(i)},$error,${summary.mcse(i)},${summary.ess},${summary.maxWeight},$covered,$accurate,$elapsed,$underflows")
    }
  }

  def main(args: Array[String]): Unit = {
    require(args.length == 1 && Set("kernel", "backends", "graph", "smoke", "calibration", "mvnormal").contains(args(0)),
      "Expected kernel, backends, graph, smoke, calibration, or mvnormal")
    if (args(0) == "calibration") { calibration(); return }
    if (args(0) == "mvnormal") { mvnormal(); return }
    println("SV,mode,family,rng,seed,draws,parameter,mean,error,mcse,ess,maxWeight,covered95,withinPointOnePosteriorSd,seconds,underflows")
    val selectedSeeds = if (args(0) == "smoke") seeds.take(1) else if (args(0) == "graph") seeds.take(3) else seeds
    val selectedBudgets = if (Set("kernel", "backends").contains(args(0))) budgets else Vector(2000)
    val selectedRngs = if (args(0) == "backends") com.cra.figaro.util.SamplingRandom.Algorithm.values.map(_.id).toVector
      else if (args(0) == "graph") Vector("Random") else Vector("Random", "L64X128MixRandom")
    for (seed <- selectedSeeds; family <- Vector("gamma", "dirichlet"); count <- selectedBudgets;
         rng <- selectedRngs) {
      val start = System.nanoTime()
      val (summary, underflows) = if (args(0) == "graph") graph(family, seed, count)
        else (kernel(family, rng, seed, count), 0)
      emit(args(0), family, rng, seed, count, summary, (System.nanoTime()-start)/1e9, underflows)
    }
  }

  /** Controlled normal-null simulation, not a claim about the whole legacy suite. */
  def calibration(): Unit = {
    val tester = new org.apache.commons.math3.stat.inference.TTest
    for (algorithm <- Vector("Random", "L64X128MixRandom")) {
      val r = generator(algorithm, 32452843L)
      var individual = 0; var families = 0; var corrected = 0
      for (_ <- 0 until 1000) {
        var any = false; var anyCorrected = false
        for (_ <- 0 until 20) {
          val values = Array.fill(10)(r.nextGaussian())
          val p = tester.tTest(0, values)
          if (p < .05) { individual += 1; any = true }
          if (p < .05/20) anyCorrected = true
        }
        if (any) families += 1
        if (anyCorrected) corrected += 1
      }
      println(s"CALIBRATION,$algorithm,tests=20000,rejections=$individual,families=1000,anyRejected=$families,bonferroniRejected=$corrected")
    }
  }

  /** Repeat the five-moment MVNormal experiment, retaining every rejection. */
  def mvnormal(): Unit = {
    val law = com.cra.figaro.library.atomic.continuous.MultivariateGaussianDistribution(
      Vector(1.0, 2.0), Vector(Vector(.25, .15), Vector(.15, .25)))
    val targets = Vector(1.0, 2.0, .25, .25, .15)
    val tester = new org.apache.commons.math3.stat.inference.TTest
    var rejected = 0; var families = 0; var corrected = 0
    for (experiment <- 0 until 100) {
      val rng = new scala.util.Random(49979687L + 7919L*experiment)
      val estimates = Vector.fill(10) {
        var mx = 0.0; var my = 0.0; var vx = 0.0; var vy = 0.0; var covariance = 0.0
        for (n <- 1 to 10001) {
          val x = law.sample(rng)
          val dx = x(0)-mx; val dy = x(1)-my
          mx += dx/n; my += dy/n
          vx += dx*(x(0)-mx); vy += dy*(x(1)-my); covariance += dx*(x(1)-my)
        }
        Vector(mx, my, vx/10000, vy/10000, covariance/10000)
      }
      val p = targets.indices.map(i => tester.tTest(targets(i), estimates.map(_(i)).toArray))
      rejected += p.count(_ < .05)
      if (p.exists(_ < .05)) families += 1
      if (p.exists(_ < .05/5)) corrected += 1
    }
    println(s"MVNORMAL,Random,tests=500,rejections=$rejected,families=100,anyRejected=$families,bonferroniRejected=$corrected")
  }
}
