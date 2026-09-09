package com.cra.figaro.test.modernization

import com.cra.figaro.algorithm.sampling.{InferenceHealth as H, VectorSliceSampler as VS}
import com.cra.figaro.library.atomic.continuous.MultivariateGaussianDistribution as Gaussian
import com.cra.figaro.util.SamplingRandom
import org.apache.commons.math3.special.Gamma.logGamma

/** Research only. Frozen protocol: docs/DEFENSIVE_IMPORTANCE_RESEARCH.md. */
object DefensiveImportanceStudy {
  val budgets = Vector(2000, 20000, 200000)
  val methods = Vector("prior", "defensive", "slice")
  def check(): Unit = if (Thread.currentThread().isInterrupted) throw new InterruptedException("Proposal study interrupted")
  def logAdd(a: Double, b: Double): Double = {
    val top = math.max(a,b)
    if (top == Double.NegativeInfinity) top else top + math.log1p(math.exp(math.min(a,b)-top))
  }
  def inside(x: Vector[Double]): Boolean = x.forall(v => v > 0 && v < 10)

  final case class Fixture(id: String, family: String, dimension: Int, gammaSum: Double,
    gammaLogSum: Double, dirichletLogs: Vector[Double], reference: Vector[Double], sd: Vector[Double]) {
    def logTarget(x: Vector[Double]): Double = {
      require(x.size == dimension && x.forall(_.isFinite))
      if (!inside(x)) Double.NegativeInfinity
      else if (family == "gamma") (x(0)-1)*gammaLogSum - gammaSum/x(1) - 200*(logGamma(x(0))+x(0)*math.log(x(1)))
      else 200*(logGamma(x.sum)-x.map(logGamma).sum) + x.indices.map(i => (x(i)-1)*dirichletLogs(i)).sum
    }
  }
  private def fixtures(offset: Int): Vector[Fixture] = {
    def gamma(r: java.util.Random, a: Int) = (0 until a).map(_ => -math.log1p(-r.nextDouble())).sum
    val g = new java.util.Random(104729L+offset)
    val d = new java.util.Random(130363L+offset)
    val dataG = Vector.fill(200)(2*gamma(g,2))
    val dataD = Vector.fill(200) { val x=Vector(1,2,3).map(gamma(d,_)); x.map(_/x.sum) }
    val means = if (offset == 0) StatisticalValidationStudy.references else Map(
      "gamma" -> Vector(2.1344473479226362,1.8545503427937535),
      "dirichlet" -> Vector(.9522859595106329,1.9694768223230366,3.023988742447311))
    val sds = if (offset == 0) StatisticalValidationStudy.posteriorSd else Map(
      "gamma" -> Vector(.19937384381701873,.19794033663822933),
      "dirichlet" -> Vector(.06891111207876513,.14679812158777752,.228495626039935))
    Vector("gamma","dirichlet").map { f =>
      Fixture(f + (if(offset == 0) "-original" else "-heldout"), f, if(f == "gamma") 2 else 3,
        dataG.sum,dataG.map(math.log).sum,Vector.tabulate(3)(i => dataD.map(x => math.log(x(i))).sum), means(f),sds(f))
    }
  }
  lazy val targets = fixtures(0) ++ fixtures(1000003)

  /** Mixture normalized on R^d, not a Gaussian silently truncated to the prior box. */
  final case class Proposal(dimension: Int, gaussian: Option[Gaussian]) {
    require(dimension > 0 && gaussian.forall(_.dimension == dimension))
    def logDensity(x: Vector[Double]): Double = {
      require(x.size == dimension && x.forall(_.isFinite))
      // The RNG can return exactly zero; boundary density is chosen finite so a
      // zero target weight never becomes (-Inf)-(-Inf). This changes no integral.
      val prior = if (x.forall(v => v >= 0 && v <= 10)) -dimension*math.log(10) else Double.NegativeInfinity
      gaussian match {
        case None => prior
        case Some(g) => logAdd(math.log(.1)+prior, math.log(.9)+g.logDensity(x))
      }
    }
    def draw(r: scala.util.Random): Vector[Double] = {
      check()
      gaussian match {
        case Some(g) if r.nextDouble() >= .1 => g.sample(r)
        case _ => Vector.fill(dimension)(10*r.nextDouble())
      }
    }
  }
  def fit(chains: Vector[Vector[Vector[Double]]], dimension: Int): Proposal = {
    check()
    require(chains.forall(_.forall(x => x.size == dimension && x.forall(_.isFinite))))
    val points = chains.flatten
    if (chains.size != 4 || chains.exists(_.size < 4) || points.size < 20) return Proposal(dimension,None)
    val mean = Vector.tabulate(dimension)(i => points.map(_(i)).sum/points.size)
    val covariance = Vector.tabulate(dimension,dimension) { (i,j) =>
      2*points.map(x => (x(i)-mean(i))*(x(j)-mean(j))).sum/(points.size-1) + (if(i == j) 1e-4 else 0.0)
    }
    // Invalid numerical fit is a visible fallback, not a silently repaired Gaussian.
    try Proposal(dimension,Some(Gaussian(mean,covariance)))
    catch { case _: IllegalArgumentException => Proposal(dimension,None) }
  }
  final case class Coordinate(mean: Option[Double], mcse: Option[Double], ess: Option[Double],
    status: H.Status, maxWeight: Option[Double], k: Option[Double])
  final case class Trial(coordinates: Vector[Coordinate], evaluations: Long, pilotEvaluations: Long,
    pilotDraws: Int, fallback: Boolean, draws: Int, seconds: Double)

  def run(f: Fixture, method: String, seed: Long, budget: Int): Trial = {
    check()
    require(methods.contains(method) && budget >= 200 && budget <= 1000000 && budget % 8 == 0)
    val started = System.nanoTime()
    var calls = 0L
    def evaluate(x: Vector[Double]): Double = {
      check(); require(calls < budget, "Global budget exceeded"); calls += 1; f.logTarget(x)
    }
    def slice(cap: Int): Vector[VS.Result] = Vector.tabulate(4) { i =>
      val startRng = SamplingRandom.seeded(seed+100000007L*(i+1),SamplingRandom.Algorithm.Lxm)
      val initial = Vector.fill(f.dimension)(1+8*startRng.nextDouble())
      val config = VS.Config(VS.Method.Quantile,draws=budget,warmUp=20,
        seed=seed+200000033L*(i+1),maxEvaluations=cap/4,randomAlgorithm=SamplingRandom.Algorithm.Lxm)
      VS.run(config,initial)(evaluate)
    }
    var pilotCalls = 0L
    var pilotDraws = 0
    var fallback = false
    var draws = 0
    val coordinates = if (method == "slice") {
      val results = slice(budget)
      require(results.map(_.evaluations).sum == calls)
      val n = results.map(_.samples.size).min
      draws = 4*n
      Vector.tabulate(f.dimension) { j =>
        val r = H.mcmc(results.map(_.samples.take(n).map(_(j))))
        Coordinate(r.diagnostics.map(_.mean),r.diagnostics.flatMap(_.mcseMean),
          r.diagnostics.flatMap(_.meanEss),r.status,None,None)
      }
    } else {
      val proposal = if (method == "defensive") {
        val results = slice(math.min(10000,budget/2))
        pilotCalls = calls
        require(results.map(_.evaluations).sum == pilotCalls)
        pilotDraws = results.map(_.samples.size).sum
        val p = fit(results.map(_.samples),f.dimension)
        fallback = p.gaussian.isEmpty
        p
      } else Proposal(f.dimension,None)
      draws = budget-calls.toInt
      val r = SamplingRandom.scalaRandom(seed ^ 0x5deece66dL,SamplingRandom.Algorithm.Lxm)
      val points = Vector.fill(draws)(proposal.draw(r))
      val weights = points.map(x => evaluate(x)-proposal.logDensity(x))
      Vector.tabulate(f.dimension) { j =>
        val report = H.importance(weights,true,Some(points.map(_(j))))
        val d = report.diagnostics
        Coordinate(d.mean,d.rawMcse,d.ess,report.status,d.maxWeight,d.pareto.flatMap(_.k))
      }
    }
    require(calls == budget, "Study did not consume its declared target-call budget")
    Trial(coordinates,calls,pilotCalls,pilotDraws,fallback,draws,(System.nanoTime()-started)/1e9)
  }
  def main(args: Array[String]): Unit = {
    require(args.length == 1 && Set("smoke","full","coverage").contains(args(0)))
    val seeds = if(args(0) == "coverage") Vector.tabulate(200)(i => 1000000007L+7919L*i)
      else if(args(0) == "smoke") StatisticalValidationStudy.seeds.take(1) else StatisticalValidationStudy.seeds
    val caps = if(args(0) == "coverage") Vector(20000) else if(args(0) == "smoke") Vector(2000) else budgets
    val selectedTargets = if(args(0) == "coverage") targets.filter(_.id.endsWith("heldout")) else targets
    val selectedMethods = if(args(0) == "coverage") Vector("defensive") else methods
    println("DI,target,seed,budget,method,coordinate,mean,error,mcse,ess,status,maxWeight,k,covered95,accurate,pilotCalls,pilotDraws,fallback,draws,evaluations,seconds")
    def value(x: Option[Double]) = x.map(_.toString).getOrElse("NA")
    for (f <- selectedTargets; seed <- seeds; budget <- caps; method <- selectedMethods) {
      val r = run(f,method,seed,budget)
      r.coordinates.zipWithIndex.foreach { (c,i) =>
        val error = c.mean.map(_-f.reference(i))
        val covered = error.exists(e => c.mcse.exists(s => math.abs(e) <= 1.959963984540054*s))
        val accurate = error.exists(e => math.abs(e) <= .1*f.sd(i))
        println(s"DI,${f.id},$seed,$budget,$method,$i,${value(c.mean)},${value(error)},${value(c.mcse)},${value(c.ess)},${c.status},${value(c.maxWeight)},${value(c.k)},$covered,$accurate,${r.pilotEvaluations},${r.pilotDraws},${r.fallback},${r.draws},${r.evaluations},${r.seconds}")
      }
    }
  }
}
