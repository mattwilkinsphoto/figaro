import com.cra.figaro.algorithm.factored.VariableElimination
import com.cra.figaro.algorithm.sampling.{VectorSliceSampler as VS, GaussianBlockProposal}
import com.cra.figaro.algorithm.sampling.parallel.{ParImportance, MultiChainMetropolisHastings as MH,
  MultiChainVectorSliceSampler as MC, McmcPrecision, TruncatedSprt}
import com.cra.figaro.language.*
import com.cra.figaro.library.atomic.continuous.{Normal, GaussVonMisesDistribution,
  GaussVonMisesScalarBhattacharyya as ScalarOverlap, GaussVonMisesMutualInformation as MI}
import java.nio.file.{Files, Path}
import java.security.MessageDigest
import scala.jdk.CollectionConverters.*

/** Standalone consumer: no source-project or example/test dependency is allowed. */
object FigaroConsumerCheck {
  def main(args: Array[String]): Unit = {
    require(args.isEmpty)
    val artifact=Path.of(classOf[Universe].getProtectionDomain.getCodeSource.getLocation.toURI)
    require(artifact.getFileName.toString.endsWith(".jar"), "Figaro must load from the published jar")
    val sha=java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(artifact)))
    val expected=sys.env.getOrElse("FIGARO_EXPECTED_SHA256", throw new IllegalArgumentException("Expected artifact hash required"))
    require(expected.matches("[0-9a-fA-F]{64}") && sha.equalsIgnoreCase(expected), "Wrong or stale published artifact")
    for (name <- Vector("org.scalatest.Suite", "scoverage.Invoker", "com.cra.figaro.example.ResourceScalingStudy")) {
      var absent=false
      try Class.forName(name, false, getClass.getClassLoader)
      catch { case _: ClassNotFoundException => absent=true }
      require(absent, "Consumer unexpectedly resolved a test/example/instrumentation class")
    }
    println(s"Published artifact verified: $sha")

    val gvmP=GaussVonMisesDistribution(Vector(0.0),Vector(Vector(1.0)),0,
      Vector(0.0),Vector(Vector(0.0)),50)
    val gvmQ=GaussVonMisesDistribution(Vector(0.0),Vector(Vector(1.0)),math.Pi,
      Vector(1e-5),Vector(Vector(0.0)),50)
    val overlap=ScalarOverlap.compare(gvmP,gvmQ)
    require(overlap.status == ScalarOverlap.Status.Estimated &&
      overlap.distance.exists(d => math.abs(d-47.1275754862468045) <= 1e-8))
    require(ScalarOverlap.compare(gvmP,gvmQ,maxEvaluations=5).distance.isEmpty)
    val curvedP=GaussVonMisesDistribution(Vector(.5),Vector(Vector(1.0)),.25,
      Vector(.5),Vector(Vector(.25)),50)
    val curvedQ=GaussVonMisesDistribution(Vector(-.5),Vector(Vector(.25)),-.5,
      Vector(-.25),Vector(Vector(2.0)),50)
    val curved=ScalarOverlap.compare(curvedP,curvedQ)
    require(curved.status == ScalarOverlap.Status.Estimated && curved.radius == 7 &&
      curved.evaluations <= 10000 && curved.distance.exists(d => math.abs(d-1.3162805891364138) <= 1e-8))
    require(curved.gaussianTailBound > 0 && curved.interval.exists((lo,hi) => lo <= 1.3162805891364138 && hi >= 1.3162805891364138))
    require(ScalarOverlap.compare(curvedP,curvedQ,maxEvaluations=5).distance.isEmpty)
    println("Published scalar GVM API: positive overlap, bounded-tail curvature and work-budget refusals passed")

    val dependent=GaussVonMisesDistribution(Vector(0.0),Vector(Vector(1.0)),0,Vector(.4),Vector(Vector(0.0)),2)
    val information=MI.compute(dependent)
    require(information.status == MI.Status.Estimated && information.value.exists(v => math.abs(v-.09772189838645959) < 1e-8))
    require(information.interval.exists((lo,hi) => lo <= .09772189838645959 && hi >= .09772189838645959))
    require(MI.compute(dependent,maxEvaluations=1).value.isEmpty)
    println("Published GVM mutual information: dependence oracle, estimated interval and budget refusal passed")

    {
      import com.cra.figaro.library.atomic.continuous.*
      import com.cra.figaro.library.atomic.discrete.{NegativeBinomial,Hypergeometric,NegativeBinomialDistribution,CountDivergence}
      import com.cra.figaro.library.atomic.{DiscreteInformation,InformationMetricStatus}
      val familyUniverse=new Universe
      try {
        val elements=Vector(StudentT(5)(using "t",familyUniverse),Cauchy(0,1)(using "c",familyUniverse),
          Laplace(0,1)(using "l",familyUniverse),LogNormal(0,1)(using "ln",familyUniverse),
          Weibull(2,1)(using "w",familyUniverse),Triangular(0,.3,1)(using "tr",familyUniverse),
          Kumaraswamy(2,3)(using "k",familyUniverse))
        elements.foreach(e => require(e.logDensity(e.distribution.quantile(.4)).isFinite))
        require(NegativeBinomial(2,.4)(using "nb",familyUniverse).density(2) > 0)
        require(Hypergeometric(20,7,5)(using "hg",familyUniverse).density(2) > 0)
      } finally familyUniverse.clear()
      val kl=ScalarDivergence.kl(StudentTDistribution(5),StudentTDistribution(8,.4,1.2))
      require(kl.status == InformationMetricStatus.Estimated && math.abs(kl.value.get-.0599146463826821) < 1e-6)
      val b=ScalarDivergence.bhattacharyya(WeibullDistribution(2,1),WeibullDistribution(2,3))
      require(b.status == InformationMetricStatus.Analytic && math.abs(b.value.get-math.log(5.0/3)) < 1e-12)
      val count=CountDivergence.kl(NegativeBinomialDistribution(2.5,.4),NegativeBinomialDistribution(3.2,.6))
      require(count.status == InformationMetricStatus.Estimated && math.abs(count.value.get-.2796974092865718) < 1e-8)
      require(math.abs(DiscreteInformation.mutualInformation(Vector(Vector(.5,0.0),Vector(0.0,.5))).value.get-math.log(2)) < 1e-12)
      println("Published common-family APIs: nine adapters, scalar/count divergences and joint-table MI passed")
    }

    val universe=Universe.createNew()
    val cause=Flip(0.3)(using "", universe)
    cause.addConstraint(b => if (b) 0.8 else 0.2)
    val exact=VariableElimination(cause)
    try {
      exact.start()
      require(math.abs(exact.probability(cause,true)-0.24/0.38)<1e-12)
    } finally { if(exact.isActive) exact.kill(); universe.clear() }

    val importance=ParImportance.seeded(() => {
      val u=Universe.createNew()
      val c=Flip(0.3)(using "cause",u)
      c.addConstraint(b => if(b) 0.8 else 0.2)
      u
    },4,80000,42L,"cause")
    try {
      importance.start()
      require(math.abs(importance.probability[Boolean]("cause",true)-0.24/0.38)<0.02)
    } finally if(importance.isActive) importance.kill()

    def graph(u: Universe,i: Int): MH.Model = {
      val x=Normal(0,1)(using "",u)
      MH.Model(Vector(MH.Observable("x",x)(identity)))
    }
    val graphConfig=MH.Config(drawsPerChain=4000,warmUp=500,parallelism=1,seed=913L)
    val serial=MH.run(graphConfig)(graph)
    val parallel=MH.run(graphConfig.copy(parallelism=4))(graph)
    require(serial.chains.map(_.copy(samplingSeconds=0))==parallel.chains.map(_.copy(samplingSeconds=0)))
    require(math.abs(parallel.diagnostics("x").mean)<0.06)

    def vector(i: Int,seed: Long): MC.Model=MC.Model(Vector(i+0.5,-i-0.5),x => -x.map(v=>v*v).sum/2)
    val config=MC.Config(VS.Config(VS.Method.GPSS,draws=1000,warmUp=200,seed=9301L),parallelism=1)
    val a=MC.run(config)(vector); val b=MC.run(config.copy(parallelism=4))(vector)
    require(a.chains==b.chains && a.diagnostics==b.diagnostics)
    require(b.chains.forall(_.result.reason==VS.StopReason.DrawsReached))
    val capped=VS.run(VS.Config(VS.Method.Quantile,draws=1000,warmUp=100,maxEvaluations=20),Vector(1.0,1.0))(
      x => if(x.forall(_>0)) -x.sum else Double.NegativeInfinity)
    require(capped.reason==VS.StopReason.MaxEvaluationsReached && capped.evaluations==20)

    val blocked=MH.run(MH.Config(drawsPerChain=100,warmUp=50,parallelism=2)) { (u,i) =>
      val x=Normal(0,1)(using "",u); val y=Normal(0,1)(using "",u)
      MH.Model(Vector(MH.Observable("x",x)(identity)),
        Some(GaussianBlockProposal(Vector(x,y),Vector(Vector(0.5,0.1),Vector(0.1,0.5)))))
    }
    require(blocked.chains.forall(c=>c.draws("x").size==100 && c.draws("x").forall(_.isFinite)))
    require(!McmcPrecision.evaluate(Vector.fill(4)(Vector.fill(1000)(1.0)),McmcPrecision.Config()).criteriaMet)
    val design=TruncatedSprt.gaussian(0,1,1,falseAlarmRate=0.05,missedDetectionRate=0.10)
    var state=design.initial
    while(state.decision==TruncatedSprt.Decision.Continue) state=design.advance(state,1.0)
    require(state.samples<=design.maxSamples && state.decision!=TruncatedSprt.Decision.Continue)

    var cancelled=false
    Thread.currentThread().interrupt()
    try VS.run(VS.Config(VS.Method.GPSS),Vector(1.0,1.0))(x => -x.map(v=>v*v).sum/2)
    catch { case _: InterruptedException => cancelled=true }
    finally Thread.interrupted()
    require(cancelled, "Pre-interrupted caller was not cancelled")
    require(!Thread.getAllStackTraces.keySet().asScala.exists(t=>t.isAlive &&
      t.getName.startsWith("figaro-")), "Owned worker leaked")
    println("Consumer acceptance passed: artifact isolation, exact/importance/graph/vector inference, block proposals, budgets, stopping safeguards, cancellation and cleanup")
  }
}
