package com.cra.figaro.algorithm.sampling

import com.cra.figaro.algorithm.sampling.parallel.MultiChainVectorSliceSampler as MC
import com.cra.figaro.library.atomic.continuous.{MultivariateGaussianDistribution, MultivariateStudentTDistribution, StudentTDistribution}
import com.cra.figaro.util.{SamplingRandom, RandomStreams}

/** Opt-in self-normalized importance sampling of explicit vector densities.
  * No graph rewriting, adaptive production weights, automatic fallback or stopping claim.
  */
object VectorImportance {
  private def check(): Unit = ParetoTail.interrupted()
  private def dimension(d: Int): Unit = require(d >= 1 && d <= 128, "Dimension must be 1..128")
  private def point(x: Vector[Double], d: Int): Unit =
    require(x != null && x.size == d && x.forall(_.isFinite), "Finite vector of matching dimension required")
  private def logAdd(a: Double, b: Double): Double = {
    val high = math.max(a,b)
    if (high == Double.NegativeInfinity) high else high + math.log1p(math.exp(math.min(a,b)-high))
  }

  /** A normalized proposal w.r.t. vector Lebesgue measure. Implementations must be
    * immutable/stateless and sample exactly the density they report. The caller must
    * establish positive proposal density wherever the target contributes; this cannot
    * be inferred from finitely many draws. Callbacks/resources remain caller-owned.
    */
  trait Proposal {
    def dimension: Int
    /** @param rng exclusively owned run RNG; do not retain it or use another RNG
      * @return a finite vector drawn from this normalized proposal
      */
    def sample(rng: scala.util.Random): Vector[Double]
    /** @param x finite vector of matching dimension
      * @return normalized log proposal density; -Infinity is allowed off support
      */
    def logDensity(x: Vector[Double]): Double
  }
  /** Independent uniform coordinates, including a finite density convention at endpoints.
    * @param lower finite lower endpoints
    * @param upper strictly larger finite upper endpoints; widths must be finite
    */
  final case class Box(lower: Vector[Double], upper: Vector[Double]) extends Proposal {
    require(lower != null && upper != null)
    val dimension: Int = lower.size
    VectorImportance.dimension(dimension); point(lower,dimension); point(upper,dimension)
    private val widths = lower.zip(upper).map((a,b) => b-a)
    require(widths.forall(w => w.isFinite && w > 0), "Positive finite box widths required")
    private val logVolume = widths.map(math.log).sum
    def sample(rng: scala.util.Random): Vector[Double] = {
      check(); require(rng != null)
      lower.indices.map(i => lower(i)+widths(i)*rng.nextDouble()).toVector
    }
    def logDensity(x: Vector[Double]): Double = {
      point(x,dimension)
      if (x.indices.forall(i => x(i) >= lower(i) && x(i) <= upper(i))) -logVolume else Double.NegativeInfinity
    }
  }
  /** Adapter for an existing immutable full-covariance Gaussian law. */
  final case class Gaussian(law: MultivariateGaussianDistribution) extends Proposal {
    require(law != null)
    val dimension: Int = law.dimension
    def sample(rng: scala.util.Random): Vector[Double] = law.sample(rng)
    def logDensity(x: Vector[Double]): Double = law.logDensity(x)
  }
  /** Elliptical multivariate t adapter; all coordinates share the SAME random scale. */
  final case class StudentT(law: MultivariateStudentTDistribution) extends Proposal {
    require(law != null)
    val dimension: Int=law.dimension
    def sample(rng: scala.util.Random): Vector[Double]=law.sample(rng)
    def logDensity(x: Vector[Double]): Double=law.logDensity(x)
  }
  /** Explicit hierarchical joint proposal q(x,z)=q(x)q(z|x), in concatenated coordinates.
    * The conditional callback must be pure: no fitting/adaptation or retained mutable RNG.
    * @param prefix normalized proposal for the leading coordinate block
    * @param tailDimension number of conditional coordinates; total dimension must be 1..128
    * @param conditional returns a normalized fixed-dimensional proposal given a finite prefix
    * @example `Conditional(qTheta,1,x => Gaussian(G(Vector(x.head),Vector(Vector(.1)))))`
    */
  final case class Conditional(prefix: Proposal, tailDimension: Int,
    conditional: Vector[Double] => Proposal) extends Proposal {
    require(prefix != null && conditional != null && tailDimension > 0 && tailDimension <= 128)
    VectorImportance.dimension(prefix.dimension)
    val dimension: Int = prefix.dimension + tailDimension
    VectorImportance.dimension(dimension)
    private def tail(x: Vector[Double]): Proposal = {
      check(); val q=conditional(x); check()
      require(q != null && q.dimension == tailDimension, "Conditional proposal dimension changed")
      q
    }
    def sample(rng: scala.util.Random): Vector[Double] = {
      require(rng != null); check()
      val x=prefix.sample(rng); check(); point(x,prefix.dimension)
      val z=tail(x).sample(rng); check(); point(z,tailDimension)
      x ++ z
    }
    def logDensity(x: Vector[Double]): Double = {
      check(); point(x,dimension)
      val a=x.take(prefix.dimension); val first=prefix.logDensity(a); check()
      require(first.isFinite || first == Double.NegativeInfinity, "Invalid prefix density")
      // Off prefix support, the joint is zero; a conditional may not exist there.
      if(first == Double.NegativeInfinity) first
      else {
        val last=tail(a).logDensity(x.drop(prefix.dimension)); check()
        require(last.isFinite || last == Double.NegativeInfinity, "Invalid conditional density")
        val result=first+last
        require(result.isFinite || last == Double.NegativeInfinity, "Conditional log density overflow")
        result
      }
    }
  }
  /** Independent Student-t coordinates; NOT an elliptical multivariate Student t.
    * @param location finite coordinate locations
    * @param scale positive coordinate scale parameters, not standard deviations
    * @param degreesOfFreedom common positive degrees of freedom, default 5
    */
  final case class ProductStudentT(location: Vector[Double], scale: Vector[Double], degreesOfFreedom: Double = 5) extends Proposal {
    require(location != null && scale != null)
    val dimension: Int = location.size
    VectorImportance.dimension(dimension); point(location,dimension); point(scale,dimension)
    private val laws = location.indices.map(i => StudentTDistribution(degreesOfFreedom,location(i),scale(i))).toVector
    def sample(rng: scala.util.Random): Vector[Double] = { check(); require(rng != null); laws.map(_.sample(rng)) }
    def logDensity(x: Vector[Double]): Double = { point(x,dimension); laws.indices.map(i => laws(i).logDensity(x(i))).sum }
  }
  /** Random mixture, evaluated using ALL component densities, not the sampled component alone.
    * @param weights 1..32 positive finite relative weights; representable normalized weights required
    * @param components immutable normalized proposals with a common dimension
    */
  final case class Mixture(weights: Vector[Double], components: Vector[Proposal]) extends Proposal {
    require(weights != null && components != null && weights.nonEmpty && weights.size <= 32 &&
      weights.size == components.size && weights.forall(w => w.isFinite && w > 0) && components.forall(_ != null))
    val dimension: Int = components.head.dimension
    VectorImportance.dimension(dimension); require(components.forall(_.dimension == dimension))
    private val scaled = weights.map(_/weights.max)
    val probabilities: Vector[Double] = scaled.map(_/scaled.sum)
    require(probabilities.forall(_ > 0), "Mixture weight underflow")
    def sample(rng: scala.util.Random): Vector[Double] = {
      check(); require(rng != null)
      val u = rng.nextDouble()
      var i = 0; var cumulative = probabilities.head
      while (i < probabilities.size-1 && u >= cumulative) { i += 1; cumulative += probabilities(i) }
      val x = components(i).sample(rng); check(); point(x,dimension); x
    }
    def logDensity(x: Vector[Double]): Double = {
      point(x,dimension)
      components.indices.foldLeft(Double.NegativeInfinity) { (total,i) =>
        check(); val value = components(i).logDensity(x)
        require(value.isFinite || value == Double.NegativeInfinity, "Invalid component density")
        logAdd(total,math.log(probabilities(i))+value)
      }
    }
  }

  /** @param draws requested independent production draws, positive and within health.maxSamples
    * @param maxEvaluations cap on production target calls; may return fewer than draws
    * @param seed private production RNG seed
    * @param randomAlgorithm explicit backend; default follows SamplingRandom
    * @param maxStoredValues cap on retained scalar slots: actual draws * (dimension + 2); not a heap bound
    * @param health diagnostic policy for the supplied scalar query; never a stopping rule
    */
  final case class Config(draws: Int = 10000, maxEvaluations: Long = 10000, seed: Long = 43L,
    randomAlgorithm: SamplingRandom.Algorithm = SamplingRandom.defaultAlgorithm,
    maxStoredValues: Long = 10000000L, health: InferenceHealth.Config = InferenceHealth.Config()) {
    require(health != null && draws > 0 && draws <= health.maxSamples && maxEvaluations > 0 &&
      randomAlgorithm != null && maxStoredValues > 0, "Invalid production configuration")
  }
  enum StopReason { case DrawsReached, MaxEvaluationsReached }
  /** Completed work, not accuracy success. Raw draws include zero-target-weight points.
    * @param samples independent production draws only
    * @param logWeights full target-minus-proposal log ratios, before normalization
    * @param health query-specific report, including unreliable/missing MCSE states
    * @param evaluations actual production target calls
    * @param reason draw request or evaluation cap reached
    * @param config actual production policy and RNG identity
    * @param randomProvider recorded provider/JDK provenance; replay requires compatible software
    */
  final case class Result(samples: Vector[Vector[Double]], logWeights: Vector[Double],
    health: InferenceHealth.ImportanceReport, evaluations: Long, reason: StopReason, config: Config, randomProvider: String)
  private def preflight(config: Config, proposal: Proposal, target: Vector[Double] => Double, project: Vector[Double] => Double): Int = {
    check(); require(config != null && proposal != null && target != null && project != null)
    val d = proposal.dimension; dimension(d)
    require(math.min(config.draws.toLong,config.maxEvaluations) <= config.maxStoredValues/(d+2), "Production storage cap exceeded")
    d
  }
  /** Run one frozen proposal synchronously. No resampling/retries for zero target density.
    * @param config production-only budgets and RNG/health policies
    * @param proposal normalized immutable proposal covering target support
    * @param logTarget pure unnormalized log Lebesgue density; -Infinity means outside support
    * @param project pure finite scalar query, including a 0/1 event indicator
    * @return detached draws and health report; exceptions publish no partial result
    * @example `run(Config(), proposal, logTarget, x => x.head)`
    */
  def run(config: Config, proposal: Proposal, logTarget: Vector[Double] => Double,
    project: Vector[Double] => Double): Result = {
    val d = preflight(config,proposal,logTarget,project)
    val count = math.min(config.draws.toLong,config.maxEvaluations).toInt
    val rng = SamplingRandom.scalaRandom(config.seed,config.randomAlgorithm)
    val points = Vector.newBuilder[Vector[Double]]; val logs = Vector.newBuilder[Double]; val values = Vector.newBuilder[Double]
    for (_ <- 0 until count) {
      check(); val x = proposal.sample(rng); check(); point(x,d)
      val q = proposal.logDensity(x); check()
      require(q.isFinite, "Proposal must report finite log density at its own draw")
      val p = logTarget(x); check()
      require(p.isFinite || p == Double.NegativeInfinity, "Invalid target density")
      val w = p-q
      require(w.isFinite || p == Double.NegativeInfinity, "Log weight overflow")
      val value = project(x); check(); require(value.isFinite, "Finite query projection required")
      points += x; logs += w; values += value
    }
    val weights = logs.result()
    val health = InferenceHealth.importance(weights,true,Some(values.result()),config.health)
    Result(points.result(),weights,health,count,
      if (count == config.draws) StopReason.DrawsReached else StopReason.MaxEvaluationsReached,config,SamplingRandom.provenance(config.randomAlgorithm))
  }

  /** @param covarianceInflation positive finite covariance multiplier (not standard-deviation multiplier)
    * @param diagonalRidge empty for no regularization, or explicit nonnegative variances per coordinate
    * @param minChains minimum training chains, at least two
    * @param minDrawsPerChain minimum retained training draws, at least two
    * @param minTotalDraws minimum pooled retained draws, at least two
    * @param maxPilotValues cap on input scalar slots for fitting
    */
  final case class FitConfig(covarianceInflation: Double = 2, diagonalRidge: Vector[Double] = Vector.empty,
    minChains: Int = 4, minDrawsPerChain: Int = 5, minTotalDraws: Int = 20, maxPilotValues: Long = 10000000L) {
    require(covarianceInflation.isFinite && covarianceInflation > 0 && diagonalRidge != null &&
      diagonalRidge.forall(v => v.isFinite && v >= 0) && minChains >= 2 && minDrawsPerChain >= 2 &&
      minTotalDraws >= 2 && maxPilotValues > 0, "Invalid fit policy")
  }
  enum FitStatus { case Fitted, InsufficientPilot, DegeneratePilot, NumericalFailure }
  /** Fitted means a numerical proposal was constructed, NOT that the pilot converged. */
  final case class FitResult(status: FitStatus, proposal: Option[Gaussian], draws: Int, config: FitConfig, message: String)
  /** Fit unweighted post-warm-up pilot chains; these draws must not be reused in production estimates.
    * @param chains immutable, finite vector traces, possibly unequal lengths; empty/short input returns refusal
    * @param config explicit covariance regularization and bounded fitting policy
    * @return fitted immutable Gaussian or explicit refusal; no hidden ridge or prior fallback
    * @example `fitGaussian(pilot.chains.map(_.result.samples), FitConfig())`
    */
  def fitGaussian(chains: Vector[Vector[Vector[Double]]], config: FitConfig = FitConfig()): FitResult = {
    check(); require(chains != null && config != null && chains.forall(_ != null))
    val slots = chains.iterator.flatMap(_.iterator).map { x => require(x != null); x.size.toLong }.sum
    require(slots <= config.maxPilotValues, "Pilot fitting storage cap exceeded")
    val points = chains.flatten
    def refused(s: FitStatus, why: String) = FitResult(s,None,points.size,config,why)
    if (points.isEmpty) return refused(FitStatus.InsufficientPilot,"No retained pilot draws")
    val d = points.head.size; dimension(d); points.foreach(point(_,d))
    require(config.diagonalRidge.isEmpty || config.diagonalRidge.size == d, "One ridge variance per coordinate required")
    if (chains.size < config.minChains || chains.exists(_.size < config.minDrawsPerChain) || points.size < config.minTotalDraws)
      return refused(FitStatus.InsufficientPilot,"Pilot draw requirements not met; production was not authorized by this fit")
    if ((0 until d).exists(i => points.forall(_(i) == points.head(i))))
      return refused(FitStatus.DegeneratePilot,"Constant pilot coordinate; ridge does not establish exploration")
    val mean = Vector.tabulate(d) { i => check(); points.iterator.map(_(i)/points.size).sum }
    val covariance = Vector.tabulate(d,d) { (i,j) =>
      check()
      points.iterator.map(x => (x(i)-mean(i))*(x(j)-mean(j))/(points.size-1)).sum*config.covarianceInflation +
        (if(i == j && config.diagonalRidge.nonEmpty) config.diagonalRidge(i) else 0.0)
    }
    check()
    try FitResult(FitStatus.Fitted,Some(Gaussian(MultivariateGaussianDistribution(mean,covariance))),points.size,config,
      "Numerical fit only; inspect pilot diagnostics and production health")
    catch { case _: IllegalArgumentException => refused(FitStatus.NumericalFailure,"Covariance/mean outside supported numerical range; no implicit repair") }
  }
  /** Separate pilot work and optional production. None means fit refusal, not a prior estimate. */
  final case class PilotRun(pilot: MC.Result, fit: FitResult, production: Option[Result],
    pilotConfig: MC.Config, defensiveWeight: Double) {
    def pilotEvaluations: Long = pilot.chains.map(_.result.evaluations).sum
    def totalEvaluations: Long = pilotEvaluations + production.map(_.evaluations).getOrElse(0L)
  }
  /** Train using existing isolated slice chains, freeze a defensive mixture, then use fresh production draws.
    * @param pilotConfig existing multi-chain policy; owns the entire pilot evaluation budget
    * @param initialStates one dispersed, finite vector per pilot chain
    * @param defensive normalized broad component; caller establishes target-support coverage
    * @param productionConfig separate production budget and distinct root seed
    * @param logTarget pure joint log density, safe to call concurrently during pilot training
    * @param project pure finite production scalar query
    * @param fitConfig explicit fitting requirements; default has no diagonal ridge
    * @param defensiveWeight strictly between zero and one, default 0.1
    * @return pilot/fit diagnostics and optional production; no automatic fallback or precision stopping
    * @example `runWithPilot(pilotConfig, starts, broad, Config(seed = 43), logTarget, _.head)`
    */
  def runWithPilot(pilotConfig: MC.Config, initialStates: Vector[Vector[Double]], defensive: Proposal,
    productionConfig: Config, logTarget: Vector[Double] => Double, project: Vector[Double] => Double,
    fitConfig: FitConfig = FitConfig(), defensiveWeight: Double = .1): PilotRun = {
    val d = preflight(productionConfig,defensive,logTarget,project)
    require(pilotConfig != null && fitConfig != null && initialStates != null && initialStates.size == pilotConfig.chains)
    require(pilotConfig.chains <= 128, "At most 128 pilot chains")
    initialStates.foreach(point(_,d))
    require(defensiveWeight > 0 && defensiveWeight < 1 && productionConfig.seed != pilotConfig.sampler.seed,
      "Positive mixture fractions and distinct pilot/production root seeds required")
    require(fitConfig.diagonalRidge.isEmpty || fitConfig.diagonalRidge.size == d)
    require(pilotConfig.sampler.draws.toLong <= fitConfig.maxPilotValues/pilotConfig.chains/d, "Requested pilot exceeds fitting storage cap")
    if (pilotConfig.randomStreams.allocation == RandomStreams.Allocation.SeededV1 &&
        pilotConfig.sampler.randomAlgorithm == productionConfig.randomAlgorithm) {
      val streams = RandomStreams.allocate(pilotConfig.sampler.seed,pilotConfig.chains,pilotConfig.sampler.randomAlgorithm,pilotConfig.randomStreams)
      require(!streams.exists(_.seed == productionConfig.seed), "Production seed collides with a pilot stream; choose another seed")
    }
    val pilot = MC.run(pilotConfig)((i,_) => MC.Model(initialStates(i),logTarget))
    val fit = fitGaussian(pilot.chains.map(_.result.samples),fitConfig)
    val production = fit.proposal.map { gaussian =>
      run(productionConfig,Mixture(Vector(defensiveWeight,1-defensiveWeight),Vector(defensive,gaussian)),logTarget,project)
    }
    PilotRun(pilot,fit,production,pilotConfig,defensiveWeight)
  }
}
