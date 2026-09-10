import com.cra.figaro.algorithm.factored.VariableElimination
import com.cra.figaro.algorithm.sampling.{VectorSliceSampler as VS, GaussianBlockProposal}
import com.cra.figaro.algorithm.sampling.parallel.{ParImportance, MultiChainMetropolisHastings as MH,
  MultiChainVectorSliceSampler as MC, McmcPrecision, TruncatedSprt}
import com.cra.figaro.language.*
import com.cra.figaro.util.SamplingRandom
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

    {
      import com.cra.figaro.library.atomic.continuous.*
      val law=CopulaDistribution(Vector.fill(2)(GaussianDistribution(0,1)),Vector(Vector(1.0,.6),Vector(.6,1.0)))
      val conditional=law.condition(Vector(0),Vector(1))
      require(math.abs(conditional.logDensity(Vector(.2))-GaussianDistribution(.6,.8).logDensity(.2))<1e-12)
      require(law.partialLogDensity(Vector.empty,Vector.empty)==0)
      val g=GaussVonMisesDistribution(Vector(0),Vector(Vector(1.0)),.1,Vector(.4),Vector(Vector(.3)),4)
      val rng=SamplingRandom.scalaRandom(610100)
      val fitted=GaussVonMisesMixtureFit.fit(Vector.fill(100)(g.sample(rng)),GaussVonMisesMixtureFit.Config(restarts=1,maxIterations=4))
      require(fitted.distribution.nonEmpty)
      val mi=GaussVonMisesMixtureMutualInformation.compute(fitted.distribution.get,GaussVonMisesMixtureMutualInformation.Config(draws=500))
      require(mi.value.nonEmpty && mi.mcse.nonEmpty)
      println("6.1 conditional copula, GVM fitting and mixture partition MI verified")
    }

    {
      import com.cra.figaro.algorithm.sampling.{BoundedIidPrecision as B,DeclaredRegionCoverage as R}
      val bounded=B.run(B.Config(0,1,.1,maxDraws=10000))(_.nextDouble())
      require(bounded.reason==B.StopReason.PrecisionReached && bounded.errorBound<=.1)
      val adaptive=com.cra.figaro.algorithm.sampling.EmpiricalBernsteinPrecision.run(B.Config(0,1,.02))(_ => .5)
      require(adaptive.reason==B.StopReason.PrecisionReached && adaptive.errorBound<=.02)
      val regions=Vector(R.Region[Double]("left",_ < .5),R.Region[Double]("right",_ >= .5))
      val occupancy=R.run(R.Config("uniform [0,1)",draws=100),regions,
        Some(R.MassAssumption(.5,"each uniform half")))(_.nextDouble())
      require(occupancy.status==R.Status.DeclaredInventoryObserved && occupancy.missBound.nonEmpty)
      println("Bounded IID precision and declared-region published APIs verified")
    }

    {
      import com.cra.figaro.algorithm.sampling.{VectorImportance as V,MonteCarloInformation as I,GraphProposalImportance as P}
      import com.cra.figaro.library.atomic.continuous.{MultivariateGaussianDistribution as G,MultivariateStudentTDistribution as T,MultivariateStudentT}
      val t=T(5,Vector(0.0,0.0),Vector(Vector(1.0,.3),Vector(.3,1.0)))
      require(t.covariance.get(0)(0)==5.0/3)
      val q=V.StudentT(t)
      require(I.kl(q,q,I.Config(draws=100)).value.contains(0.0))
      require(I.mutualInformation(q,V.StudentT(t.marginal(Vector(0))),V.StudentT(t.marginal(Vector(1))),I.Config(draws=100)).value.nonEmpty)
      def g(m: Double,v: Double)=V.Gaussian(G(Vector(m),Vector(Vector(v))))
      val joint=V.Conditional(g(0,1),1,x => g(x.head,.1))
      val result=P.run(P.Config(draws=100,maxAttempts=100),joint,joint.logDensity) { (u,root) =>
        MultivariateStudentT(t)(using "",u).observe(Vector(0.0,0.0))
        root.map(_.head)(using "",u)
      }
      require(result.attempts==100 && result.health.diagnostics.mean.nonEmpty)
      val mixture=V.Mixture(Vector(.3,.7),Vector(g(-4,1),g(4,1)))
      require(I.bhattacharyya(mixture,mixture,I.Config(draws=100)).value.contains(0.0))
    }

    {
      import com.cra.figaro.algorithm.sampling.{GraphProposalImportance as P, VectorImportance as V}
      import com.cra.figaro.library.atomic.continuous.MultivariateGaussianDistribution as G
      val prior=G(Vector(0.0),Vector(Vector(1.0)))
      val proposal=V.Gaussian(G(Vector(.8),Vector(Vector(.3))))
      val result=P.run(P.Config(seed=43),proposal,prior.logDensity) { (u,root) =>
        val theta=root.map(_.head)(using "",u)
        Normal(theta,.25)(using "",u).observe(1.0)
        theta
      }
      require(math.abs(result.health.diagnostics.mean.get-.8)<.025)
      require(result.attempts==10000 && result.priorEvaluations==10000)
      val rejected=P.run(P.Config(draws=200,maxAttempts=123),proposal,prior.logDensity) { (u,root) =>
        root.addCondition(_ => false)
        root.map(_.head)(using "",u)
      }
      require(rejected.rejected==123 && rejected.proposalDraws==123 && rejected.health.diagnostics.mean.isEmpty)
      require(rejected.reason==P.StopReason.MaxAttemptsReached)
      println("Published graph proposals: observed posterior, attempt cap and zero-weight accounting passed")
    }

    {
      import com.cra.figaro.algorithm.sampling.GaussianMixtureProposal as M
      val traces=Vector.tabulate(4)(_ => Vector.tabulate(100)(i => Vector((if(i<75) -6 else 6)+(i%5-2)*.1)))
      val fitted=M.fit(traces)
      require(fitted.status==M.Status.Fitted && fitted.proposal.get.components.size==2)
      require(M.fit(traces,M.Config(maxDensityEvaluations=1)).proposal.isEmpty)
      println("Published mixture fitting: frozen components and explicit budget refusal passed")
    }

    {
      import com.cra.figaro.algorithm.sampling.VectorImportance as V
      val broad = V.Box(Vector(-5.0),Vector(5.0))
      val target: Vector[Double] => Double = x => if (math.abs(x.head)<5) -x.head*x.head/2 else Double.NegativeInfinity
      val pilot = MC.Config(VS.Config(VS.Method.Quantile,draws=100,warmUp=50,maxEvaluations=3000,seed=42),parallelism=2)
      val starts = Vector(-2.0,-.5,.5,2.0).map(Vector(_))
      val trained = V.runWithPilot(pilot,starts,broad,V.Config(draws=1000,maxEvaluations=1000,seed=43),target,_.head)
      require(trained.fit.status == V.FitStatus.Fitted)
      require(trained.production.get.samples.size == 1000)
      require(trained.totalEvaluations == trained.pilotEvaluations + 1000)
      val fixed = V.Mixture(Vector(.1,.9),Vector(broad,trained.fit.proposal.get))
      val event = V.run(V.Config(draws=1000,maxEvaluations=500,seed=44),fixed,target,x => if(x.head>1) 1.0 else 0.0)
      require(event.reason == V.StopReason.MaxEvaluationsReached && event.samples.size == 500)
      require(event.logWeights == event.samples.map(x => target(x)-fixed.logDensity(x)))
      val refused = V.runWithPilot(pilot.copy(sampler=pilot.sampler.copy(maxEvaluations=1)),starts,broad,V.Config(seed=45),target,_.head)
      require(refused.production.isEmpty && refused.totalEvaluations == 4)
    }

    {
      import com.cra.figaro.algorithm.sampling.{InferenceHealth as H, ParetoTail}
      val logs = Vector.tabulate(2000)(i => math.log(1 + (i + .5)/2000))
      require(H.importance(logs, true).status == H.Status.ChecksPassed)
      require(ParetoTail.fit(logs).status == ParetoTail.Status.Estimated)
      require(H.importance(Vector.fill(2000)(0.0), true).status == H.Status.InsufficientEvidence)
      require(H.importance(Vector(0.0, Double.NegativeInfinity), true).status == H.Status.Danger)
      val u = new Universe
      val x = Normal(0, 1)(using "health", u)
      try {
        val r = H.runImportance(200, x, (v: Double) => v)
        require(r.diagnostics.samples == 200 && r.diagnostics.mean.exists(_.isFinite) && x.active)
      } finally u.clear()
      println("Published inference health: raw weights, explicit unavailable/danger states and owned sampler passed")
    }

    // All named backends must resolve transitively from the published thin library.
    val sr = com.cra.figaro.util.SamplingRandom
    for (algorithm <- sr.Algorithm.values) {
      val first = sr.scalaRandom(481L, algorithm)
      val second = sr.scalaRandom(481L, algorithm)
      require(Vector.fill(100)(first.nextGaussian()) == Vector.fill(100)(second.nextGaussian()))
      require(sr.provenance(algorithm).contains(algorithm.id))
    }

    {
      import com.cra.figaro.util.RandomStreams as RS
      val policy = RS.Config(RS.Allocation.PartitionedV1, 1000000L)
      for (a <- Vector(sr.Algorithm.Lxm, sr.Algorithm.Xoshiro256PlusPlus, sr.Algorithm.Philox4x64)) {
        val stream = RS.allocate(42L, 3, a, policy)(2)
        val replay = stream.descriptor.replay()
        require(Vector.fill(100)(stream.random.nextGaussian()) == Vector.fill(100)(replay.nextGaussian()))
      }
      val config = MC.Config(VS.Config(VS.Method.Quantile, draws=100, seed=42L,
        randomAlgorithm=sr.Algorithm.Philox4x64), chains=3, parallelism=1, randomStreams=policy)
      def model(i: Int, seed: Long): MC.Model = MC.Model(Vector(i.toDouble), x => -x.head*x.head/2)
      val serial = MC.run(config)(model)
      val parallel = MC.run(config.copy(parallelism=3))(model)
      require(serial.chains.map(_.result.samples) == parallel.chains.map(_.result.samples))
      require(serial.chains.forall(_.randomStream.exists(_.config == policy)))
      println("Published stream API: native allocation, replay and Philox vector-chain scheduling checks passed")
      val selection = com.cra.figaro.util.RandomSelection.resolve(
        com.cra.figaro.util.RandomSelection.Purpose.CounterRanges)
      require(selection.algorithm == sr.Algorithm.Philox4x64 && !selection.overridden)
      require(selection.reason.nonEmpty && selection.provider == sr.provenance(selection.algorithm))
      val selected = selection.configure(config)
      require(MC.run(selected)(model).chains.map(_.result.samples) == serial.chains.map(_.result.samples))
      require(selection.allocate(42L, 3).map(_.descriptor) == MC.run(selected)(model).chains.flatMap(_.randomStream))
      println("Published purpose selector: resolved metadata, allocation and vector integration passed")
    }

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

    {
      import com.cra.figaro.library.atomic.continuous.*
      import com.cra.figaro.library.atomic.discrete.*
      val gaussian=GaussianDistribution(0,1)
      val truncated=TruncatedDistribution(gaussian,8,9)
      require(math.abs(truncated.cdf(truncated.quantile(.4))-.4) < 1e-10)
      require(AffineDistribution(gaussian,2,-1).quantile(.5) == 2)
      require(ExpDistribution(gaussian).quantile(.5) == 1)
      val vector=MultivariateGaussianDistribution(Vector(0.0,1.0),Vector(Vector(2.0,.3),Vector(.3,1.0)))
      val other=MultivariateGaussianDistribution(Vector(1.0,-1.0),Vector(Vector(1.0,-.2),Vector(-.2,3.0)))
      val mixture=GaussianMixtureDistribution(Vector(.25,.75),Vector(vector,other))
      require(math.abs(mixture.logDensity(Vector(.2,-.4))+2.8382108120770378) < 1e-12)
      require(math.abs(GaussianInformation.kl(vector,other).value.get-1.4690430131387152) < 1e-12)
      require(math.abs(GaussianInformation.bhattacharyya(vector,other).value.get-.45776780271643533) < 1e-12)
      require(math.abs(GaussianInformation.mutualInformation(vector,Vector(0)).value.get-.0230219692507034) < 1e-12)
      val constructionUniverse=new Universe
      try {
        val scalar=ScalarElement(ScalarMixtureDistribution(Vector(.4,.6),Vector(gaussian,GaussianDistribution(2,1))))(using "scalar",constructionUniverse)
        require(scalar.logDensity(100).isFinite)
        val counts=CountElement(ZeroAdjustedDistribution(NegativeBinomialDistribution(2,.5),.3,true))(using "counts",constructionUniverse)
        require(math.abs(counts.logDensity(0)-math.log(.3)) < 1e-12)
        val element=GaussianMixture(mixture)(using "mixture",constructionUniverse)
        require(element.logDensity(Vector(.2,-.4)).isFinite)
      } finally constructionUniverse.clear()
      println("Published construction APIs: transformations, truncation, zero adjustment, GMM and Gaussian information passed")
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
    val health = com.cra.figaro.algorithm.sampling.InferenceHealth.mcmc(parallel.chains.map(_.draws("x")))
    require(health.diagnostics.contains(parallel.diagnostics("x")))

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

    val staticModel=com.cra.figaro.algorithm.sampling.StaticGraphImportance.compile(Vector(
      com.cra.figaro.algorithm.sampling.StaticGraphImportance.Node.Constant(0),
      com.cra.figaro.algorithm.sampling.StaticGraphImportance.Node.Constant(1),
      com.cra.figaro.algorithm.sampling.StaticGraphImportance.Node.Normal(0,1)))
    val staticResult=com.cra.figaro.algorithm.sampling.StaticGraphImportance.run(staticModel,Vector(2))
    require(staticResult.values.head.size==10000 && staticResult.nodeEvaluations==30000)

    val breadthCounts=com.cra.figaro.library.atomic.discrete.MultinomialDistribution(4,Vector(.2,.3,.5))
    require(com.cra.figaro.library.atomic.discrete.MultinomialInformation.mutualInformation(breadthCounts,Vector(0)).value.exists(_>0))
    val breadthTail=com.cra.figaro.library.atomic.continuous.GeneralizedParetoDistribution(.2)
    require(math.abs(breadthTail.survival(breadthTail.quantile(.99))-.01)<1e-12)
    require(com.cra.figaro.library.atomic.continuous.GeneralizedExtremeValueDistribution(0).quantile(.5).isFinite)
    val breadthMatrix=com.cra.figaro.library.atomic.continuous.WishartDistribution(4,Vector(Vector(1.0)))
    require(com.cra.figaro.library.atomic.continuous.WishartInformation.kl(breadthMatrix,breadthMatrix.copy(degreesOfFreedom=5)).value.exists(_>0))
    val breadthSphere=com.cra.figaro.library.atomic.continuous.VonMisesFisher3Distribution(Vector(0,0,1),3)
    require(com.cra.figaro.library.atomic.continuous.VonMisesFisher3Information.bhattacharyya(breadthSphere,breadthSphere.copy(concentration=4)).value.exists(_>0))
    require(math.abs(com.cra.figaro.library.atomic.LegacyInformation.gammaKl(2,3,4,5).value.get-2.1894932940950835)<1e-12)
    val inverseGamma=com.cra.figaro.library.atomic.continuous.InverseGamma(4,7)
    require(inverseGamma.generateValue(2)==3.5 && inverseGamma.logp(.001).isFinite)
    val covariancePrior=com.cra.figaro.library.atomic.continuous.InverseWishartDistribution(8,Vector(Vector(14.0)))
    require(math.abs(covariancePrior.mean.get(0)(0)-7.0/3)<1e-12)
    val correlationPrior=com.cra.figaro.library.atomic.continuous.LKJDistribution(2,2)
    require(correlationPrior.logDensity(correlationPrior.mean).isFinite)
    require(com.cra.figaro.library.atomic.continuous.LKJInformation.kl(correlationPrior,correlationPrior.copy(shape=1)).value.exists(_>0))
    val eventBase=com.cra.figaro.algorithm.sampling.VectorImportance.Box(Vector(0.0),Vector(1.0))
    val eventResult=com.cra.figaro.algorithm.sampling.RareEventImportance.run(
      com.cra.figaro.algorithm.sampling.RareEventImportance.prior(eventBase),_.head,.75)
    require(math.abs(eventResult.probability.get-.25)<.02 && eventResult.scoreEvaluations==10000)
    println("Published breadth, covariance priors, rare-event and legacy information contracts passed")
    locally {
      import com.cra.figaro.algorithm.sampling.{RareEventImportance as RE,GaussianMixtureProposal as WM,VectorImportance as VP,StaticGraphImportance as SG}
      import com.cra.figaro.library.atomic.continuous.*
      import com.cra.figaro.library.atomic.discrete.{CountMixtureDistribution,CountMixtureInformation,HypergeometricDistribution}
      val base=VP.Gaussian(MultivariateGaussianDistribution(Vector(0.0),Vector(Vector(1.0))))
      val fit=RE.fitMixture(base,x => math.abs(x.head),4,RE.FitConfig(seed=94001),WM.Config(components=2,diagonalRidge=Vector(.25)))
      require(fit.proposal.isDefined && fit.componentDensityEvaluations>0)
      val graph=SG.runWithProposal(staticModel,Vector(2),fit.proposal.get.proposal,Vector(2),config=SG.Config(draws=100))
      require(graph.proposalDensityEvaluations==100)
      val half=TruncatedDistribution(GaussianDistribution(0,1),0,Double.PositiveInfinity)
      require(math.abs(half.cdf(half.quantile(.5))-.5)<1e-12)
      require(MonotoneDistribution(LogNormalDistribution(0,1),MonotoneTransform.Log).logDensity(0).isFinite)
      require(FoldedDistribution(GaussianDistribution(1,1)).density(1)>0)
      require(ScalarDivergence.kl(WrappedCauchyDistribution(0,.5),WrappedCauchyDistribution(1,.3)).value.exists(_>0))
      val counts=CountMixtureDistribution(Vector(.5,.5),Vector(HypergeometricDistribution(10,0,1),HypergeometricDistribution(10,10,1)))
      require(math.abs(CountMixtureInformation.componentMutualInformation(counts).value.get-math.log(2))<1e-12)
      println("Published weighted-event, static-proposal and extended-construction APIs passed")
    }

    locally {
      import com.cra.figaro.library.atomic.continuous.*
      val gaussian=GaussianDistribution(0,1)
      require(math.abs(ObservationLikelihood.logInterval(gaussian,40,41)+804.6084420137538)<1e-10)
      val mixed=MixedScalarDistribution.spikeAndSlab(0,.2,gaussian)
      require(MixedScalarElement(mixed).logDensity(0)==math.log(.2))
      require(MixedScalarInformation.kl(mixed,mixed).value.exists(math.abs(_)<1e-12))
      val kernel=GaussVonMisesDistribution(Vector(0.0),Vector(Vector(1.0)),0,Vector(.2),Vector(Vector(.1)),5)
      val mixture=GaussVonMisesMixtureDistribution(Vector(.5,.5),Vector(kernel,kernel))
      require(mixture.asProposal().logDensity(Vector(0,0)).isFinite)
      require(GaussVonMisesMixture(mixture).logDensity(LinearAngular(Vector(0),0)).isFinite)
      val copula=CopulaDistribution(Vector(WeibullDistribution(2,3),LogNormalDistribution(0,1)),Vector(Vector(1.0,.6),Vector(.6,1.0)))
      require(math.abs(copula.gaussianPartitionMutualInformation(Vector(0))-.22314355131420976)<1e-12)
      require(CopulaElement(copula).logDensity(Vector(1,1)).isFinite)
      println("Published observation, mixed-measure, GVM-mixture and copula APIs passed")
    }
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
