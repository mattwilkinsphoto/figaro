package com.cra.figaro.example

import com.cra.figaro.algorithm.sampling.parallel.{McmcPrecision, MultiChainMetropolisHastings as MH}
import com.cra.figaro.algorithm.sampling.ProposalScheme
import com.cra.figaro.language.*
import com.cra.figaro.library.atomic.continuous.Normal

/** Repeated-seed paired validation against analytic expectations, not against fixed-run estimates. */
object StoppingCriteriaValidation {
  private final case class Fixture(name: String, truth: Vector[(String, Double)], build: (Universe, Int) => MH.Model,
    negativeControl: Boolean = false)

  private def fixtures: Vector[Fixture] = Vector(
    Fixture("normal", Vector("x" -> 0.0), (u, _) => {
      val x = Normal(0, 1)(using "", u)
      MH.Model(Vector(MH.Observable("x", x)(identity)))
    }),
    Fixture("likelihood", Vector("x" -> 0.5), (u, _) => {
      val x = Normal(0, 1)(using "", u)
      x.addLogConstraint((v: Double) => -0.5 * (v - 1) * (v - 1))
      MH.Model(Vector(MH.Observable("x", x)(identity)))
    }),
    Fixture("conditioned", Vector("x" -> math.sqrt(2 / math.Pi)), (u, _) => {
      val x = Normal(0, 1)(using "", u)
      x.addCondition((v: Double) => v > 0)
      MH.Model(Vector(MH.Observable("x", x)(identity)))
    }),
    Fixture("bernoulli", Vector("p" -> (0.24 / 0.38)), (u, _) => {
      val x = Flip(0.3)(using "", u)
      x.addConstraint((b: Boolean) => if (b) 0.8 else 0.2)
      MH.Model(Vector(MH.Observable("p", x)(b => if (b) 1.0 else 0.0)))
    }),
    Fixture("correlated", Vector("x" -> 0.0, "y" -> 0.0), (u, _) => {
      val x = Normal(0, 1)(using "", u)
      val y = Normal(0, 1)(using "", u)
      val difference = Apply(x, y, (a: Double, b: Double) => a - b)(using "", u)
      // Bivariate normal: zero means and correlation 1/(1 + 0.15^2) = 0.9780.
      difference.addLogConstraint((v: Double) => -0.5 * math.pow(v / 0.15, 2))
      MH.Model(Vector(MH.Observable("x", x)(identity), MH.Observable("y", y)(identity)))
    }),
    Fixture("multimodal", Vector("x" -> 0.0, "positive" -> 0.5), (u, index) => {
      val x = Normal(0, 25)(using "", u) // Figaro's second argument is VARIANCE.
      // Cancel the prior (SD 5) to obtain an equal mixture with means +/-4 and SD 0.75.
      x.addLogConstraint((v: Double) => {
        val a = -0.5 * math.pow((v - 4) / 0.75, 2)
        val b = -0.5 * math.pow((v + 4) / 0.75, 2)
        val max = math.max(a, b)
        max + math.log(math.exp(a - max) + math.exp(b - max)) + 0.5 * math.pow(v / 5, 2)
      })
      MH.Model(Vector(MH.Observable("x", x)(identity), MH.Observable("positive", x)(v => if (v > 0) 1.0 else 0.0)),
        initialState = () => (x.value > 0) == (index % 2 == 0))
    }),
    Fixture("trapped", Vector("x" -> 0.0), (u, index) => {
      val mode = Flip(0.5)(using "", u)
      val noise = Normal(0, 1)(using "", u)
      val x = Apply(mode, noise, (b: Boolean, v: Double) => v + (if (b) 6 else -6))(using "", u)
      // Deliberately non-ergodic proposal: negative control, not a supported sampling strategy.
      MH.Model(Vector(MH.Observable("x", x)(identity)), Some(ProposalScheme(noise)),
        initialState = () => mode.value == (index % 2 == 0))
    }, negativeControl = true)
  )

  /** Print raw CSV rows prefixed with "validation," for paired fixed/adaptive runs.
    * @param args repeats (default 20), maximum draws per chain (default 12000), workers (default 4)
    * @return Unit; emits per-query truth/error/coverage, precision status, work, and API elapsed time
    * @throws IllegalArgumentException for invalid arguments or lifecycle/negative-control failure
    * @example `StoppingCriteriaValidation.main(Array("50", "12000", "4"))`
    */
  def main(args: Array[String]): Unit = {
    require(args.length <= 3, "Arguments: repeats maximumDrawsPerChain workers")
    val repeats = args.headOption.map(_.toInt).getOrElse(20)
    val maxDraws = args.lift(1).map(_.toInt).getOrElse(12000)
    val workers = args.lift(2).map(_.toInt).getOrElse(4)
    require(repeats > 0 && maxDraws >= 2000 && workers >= 1 && workers <= 4, "Invalid validation budget")
    val policy = McmcPrecision.Config(relativeTolerance = 0.15, minDrawsPerChain = 2000, checkEvery = 2000)
    val base = MH.Config(chains = 4, drawsPerChain = maxDraws, warmUp = 2000, parallelism = workers)
    println("validation,workload,round,seed,method,query,truth,estimate,error,fullWidth,targetWidth,covered,criteriaMet,reason,drawsPerChain,checks,apiSeconds,rHat,bulkEss,meanEss")
    for (round <- -2 until repeats; fixture <- fixtures) {
      val config = base.copy(seed = 87001L + round * 7919L)
      var fixed: MH.Result = null
      var adaptive: MH.StoppedResult = null
      // Alternate order; two full warm-up rounds precede the measured rounds.
      if (round % 2 == 0) {
        fixed = MH.run(config)(fixture.build)
        adaptive = MH.runUntilPrecise(config, policy)(fixture.build)
      } else {
        adaptive = MH.runUntilPrecise(config, policy)(fixture.build)
        fixed = MH.run(config)(fixture.build)
      }
      require(!fixture.negativeControl || adaptive.reason == MH.StopReason.MaxDrawsReached,
        "Negative control incorrectly declared precision success")
      // Identity and exact prefix preservation is checked in dedicated deterministic unit tests.
      // Complex graph hash traversal is not assumed bitwise reproducible across separate runs here.
      for ((method, result, assessments, reason, checks) <- Vector(
        ("fixed", fixed, fixture.truth.map { (key, _) =>
          key -> McmcPrecision.evaluate(fixed.chains.map(_.draws(key)), policy, fixture.truth.size)
        }.toMap, "FixedBudget", 1),
        ("adaptive", adaptive.result, adaptive.assessments, adaptive.reason.toString, adaptive.checks))) {
        for ((query, truth) <- fixture.truth) {
          val assessment = assessments(query)
          val d = assessment.diagnostics
          val error = d.mean - truth
          val width = assessment.fullWidth.getOrElse(Double.NaN)
          val covered = assessment.fullWidth.exists(w => math.abs(error) <= w / 2)
          println(s"validation,${fixture.name},$round,${config.seed},$method,$query,$truth,${d.mean},$error,$width,${assessment.targetWidth},$covered,${assessment.criteriaMet},$reason,${result.chains.head.draws(query).size},$checks,${result.elapsedSeconds},${d.rHat.getOrElse(Double.NaN)},${d.bulkEss.getOrElse(Double.NaN)},${d.meanEss.getOrElse(Double.NaN)}")
        }
      }
      // Extra fixed-run precision assessment above is excluded from API timing, stated in the guide.
      import scala.jdk.CollectionConverters.*
      require(!Thread.getAllStackTraces.keySet().asScala.exists(_.getName.startsWith("figaro-mcmc-worker-")), "Worker leaked")
    }
  }
}
