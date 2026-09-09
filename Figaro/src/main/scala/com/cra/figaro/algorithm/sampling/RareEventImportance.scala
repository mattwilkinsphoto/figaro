package com.cra.figaro.algorithm.sampling

import com.cra.figaro.library.atomic.continuous.{MultivariateGaussianDistribution as G}
import com.cra.figaro.util.{RandomStreams,SamplingRandom}

/** Opt-in event-query importance sampling of a NORMALIZED explicit vector law.
  * Pilot-only cross-entropy fitting, frozen independent production, no automatic stopping.
  * The event is score(x)>=threshold. See docs/RARE_EVENT_PROPOSALS.md.
  */
object RareEventImportance {
  private def check(): Unit= ParetoTail.interrupted()
  private def point(x: Vector[Double],d: Int): Unit =
    require(x!=null && x.size==d && x.forall(_.isFinite),"Finite matching vector required")
  private def validate(base: VectorImportance.Proposal): Unit = {
    check(); require(base!=null && base.dimension>=1 && base.dimension<=32)
  }
  /** Immutable proposal wrapper; callbacks must themselves be pure and normalized.
    * @param base normalized law whose event probability is requested
    * @param proposal complete normalized production density, including defense
    * @param defensiveWeight retained prior mass; p/q<=1/defensiveWeight under valid contracts
    */
  final class Frozen private[RareEventImportance](val base: VectorImportance.Proposal,
    val proposal: VectorImportance.Proposal,val defensiveWeight: Double)

  /** @param base normalized prior law
    * @return frozen direct Monte Carlo control, with weight bound one
    * @example `RareEventImportance.prior(base)` */
  def prior(base: VectorImportance.Proposal): Frozen = { validate(base); new Frozen(base,base,1) }
  /** @param base normalized target/prior law
    * @param candidate normalized fixed proposal (may itself be a supplied mixture)
    * @param priorWeight retained base mass in [1e-6,1); not annealed during production
    * @return full-density defensive mixture; does not certify discovery of every event region
    * @example `RareEventImportance.defensive(base,shiftedGaussian,.1)` */
  def defensive(base: VectorImportance.Proposal,candidate: VectorImportance.Proposal,priorWeight: Double=.1): Frozen = {
    validate(base); require(candidate!=null && candidate.dimension==base.dimension)
    require(priorWeight.isFinite && priorWeight>=1e-6 && priorWeight<1)
    new Frozen(base,VectorImportance.Mixture(Vector(priorWeight,1-priorWeight),Vector(base,candidate)),priorWeight)
  }

  /** @param draws requested production draws
    * @param maxScoreEvaluations positive score-call cap; may return a shorter fixed-budget run
    * @param seed root seed; logical RNG stream 1 is reserved for production
    * @param randomAlgorithm named RNG backend
    * @param streams versioned allocation policy; optional partitioned backends supported
    */
  final case class Config(draws: Int=10000,maxScoreEvaluations: Int=10000,seed: Long=42,
    randomAlgorithm: SamplingRandom.Algorithm=SamplingRandom.defaultAlgorithm,streams: RandomStreams.Config=RandomStreams.Config()) {
    require(draws>0 && draws<=10000000 && maxScoreEvaluations>0 && streams!=null)
    streams.validate(randomAlgorithm)
  }
  enum StopReason { case DrawsReached, ScoreBudgetReached }
  /** Probability is the ordinary unnormalized-weight sample mean, NOT self-normalized IS.
    * @param probability estimate; None for positive binary64 underflow (use logProbability)
    * @param logProbability stable log estimate; -Infinity means no positive contributions
    * @param standardError estimated fixed-budget MCSE, unavailable for zero contributions/one draw/underflow
    * @param logStandardError stable log MCSE; -Infinity can mean exact zero empirical variance
    * @param relativeStandardError estimated MCSE divided by the estimate; not a coverage guarantee
    * @param eventEss contribution-specific effective count, not raw-weight or MCMC ESS
    * @param largestContributionShare largest event contribution divided by the total
    * @param eventHits score-threshold hits, including possible zero-base-density points
    * @param positiveContributions hits with positive base mass
    * @param scoreEvaluations actual production score calls; no pilot work included
    * @param densityEvaluations base plus full-proposal calls, not internal component/kernel calls
    * @param reason completed draw request or exhausted score budget; neither means accuracy success
    * @param warnings diagnostic cautions, never approval to stop or proof of complete mode coverage
    * @param randomStream start-of-stream replay descriptor
    */
  final case class Result(probability: Option[Double],logProbability: Double,standardError: Option[Double],
    logStandardError: Option[Double],relativeStandardError: Option[Double],eventEss: Double,
    largestContributionShare: Double,eventHits: Int,positiveContributions: Int,scoreEvaluations: Int,
    densityEvaluations: Long,reason: StopReason,warnings: Vector[String],randomStream: RandomStreams.Descriptor)

  private def stream(seed: Long,algorithm: SamplingRandom.Algorithm,policy: RandomStreams.Config,lane: Int): RandomStreams.Stream =
    RandomStreams.allocate(seed,2,algorithm,policy)(lane)
  private def scoreAt(f: Vector[Double] => Double,x: Vector[Double]): Double = {
    check(); val s=f(x); check(); require(s.isFinite,"Finite event score required"); s
  }
  private def weight(law: Frozen,x: Vector[Double]): Double = {
    val q=law.proposal.logDensity(x); check(); require(q.isFinite,"Finite proposal density at own draw required")
    val p=law.base.logDensity(x); check(); require(p.isFinite || p==Double.NegativeInfinity,"Invalid base log density")
    val w=p-q
    require(w.isFinite || p==Double.NegativeInfinity,"Log weight outside numerical range")
    require(w<= -math.log(law.defensiveWeight)+1e-10,"Base/proposal violates defensive weight bound")
    w
  }
  /** @param law frozen normalized base/proposal pair
    * @param score pure finite deterministic score callback
    * @param threshold finite event boundary; event includes equality
    * @param config production-only work/RNG settings
    * @return streaming probability and diagnostic estimates; callback failures publish no partial result
    * @example `RareEventImportance.run(frozen,x => x.head,5.0)` */
  def run(law: Frozen,score: Vector[Double] => Double,threshold: Double,config: Config=Config()): Result = {
    check(); require(law!=null && score!=null && threshold.isFinite && config!=null)
    val allocated=stream(config.seed,config.randomAlgorithm,config.streams,1)
    val rng=new scala.util.Random(allocated.random)
    val n=math.min(config.draws,config.maxScoreEvaluations)
    var peak=Double.NegativeInfinity; var sum=0.0; var squares=0.0; var hits=0; var positive=0
    var scaledMean=0.0; var centeredSquares=0.0
    for(i <- 0 until n) {
      check(); val x=law.proposal.sample(rng); check(); point(x,law.base.dimension)
      val w=weight(law,x)
      var contribution=0.0
      if(scoreAt(score,x)>=threshold) {
        hits+=1
        if(w!=Double.NegativeInfinity) {
          positive+=1
          if(w>peak) {
            val f=math.exp(peak-w); sum=sum*f+1; squares=squares*f*f+1
            scaledMean*=f; centeredSquares*=f*f; peak=w; contribution=1
          }
          else { val f=math.exp(w-peak); sum+=f; squares+=f*f; contribution=f }
        }
      }
      // Welford includes zero-event draws, avoiding cancellation in E[Y^2]-E[Y]^2.
      val delta=contribution-scaledMean
      scaledMean+=delta/(i+1)
      centeredSquares+=delta*(contribution-scaledMean)
    }
    val lp=if(positive==0) Double.NegativeInfinity else peak+math.log(sum)-math.log(n)
    val estimate=math.exp(lp)
    val relative=if(positive==0 || n<2) None else Some(math.sqrt(math.max(0,centeredSquares)/(n.toDouble*(n-1)))/scaledMean)
    val logSe=relative.map(r => if(r==0) Double.NegativeInfinity else lp+math.log(r))
    val se=logSe.flatMap { v => val e=math.exp(v); if(e==0 && v.isFinite) None else Some(e) }
    val ess=if(positive==0) 0.0 else sum*sum/squares
    val share=if(positive==0) 0.0 else 1/sum
    val warnings=Vector.newBuilder[String]
    warnings += "Finite samples cannot certify discovery of every event region; compare independently specified proposals/regions"
    if(positive==0) warnings += "No positive event contributions: zero estimate is not evidence of an impossible event"
    if(positive<20) warnings += "Fewer than 20 positive contributions: empirical MCSE is not reliable evidence of precision"
    if(ess<100 || share>.1) warnings += "Concentrated event contributions: effective sampling may be inadequate"
    if(relative.exists(_>.1)) warnings += "Estimated relative MCSE exceeds 10 percent"
    if(estimate==0 && lp.isFinite) warnings += "Positive probability estimate underflowed: use logProbability"
    Result(if(estimate==0 && lp.isFinite) None else Some(estimate),lp,se,logSe,relative,ess,share,hits,positive,n,2L*n,
      if(n==config.draws) StopReason.DrawsReached else StopReason.ScoreBudgetReached,warnings.result(),allocated.descriptor)
  }

  /** Pilot-only Gaussian CE policy, all dimensions 1..32.
    * @param drawsPerRound fixed pilot batch size, at least 20
    * @param maxRounds maximum complete training rounds, 1..64
    * @param eliteFraction upper score fraction in (0,.5], before capping at the event threshold
    * @param minEliteEss minimum weighted elite effective count, at least two
    * @param priorWeight defensive prior mass in [1e-6,1)
    * @param covarianceInflation positive post-fit covariance multiplier
    * @param diagonalRidge empty or explicit nonnegative variance per coordinate, added before inflation
    * @param maxScoreEvaluations total pilot score-call budget; incomplete batches are not started
    * @param maxStoredValues cap on batch scalar slots N*(dimension+4), not a heap bound
    * @param seed root seed; logical stream zero is reserved for all pilot rounds
    * @param randomAlgorithm named backend
    * @param streams versioned allocation policy
    */
  final case class FitConfig(drawsPerRound: Int=1000,maxRounds: Int=8,eliteFraction: Double=.1,minEliteEss: Double=20,
    priorWeight: Double=.1,covarianceInflation: Double=1.5,diagonalRidge: Vector[Double]=Vector.empty,
    maxScoreEvaluations: Int=8000,maxStoredValues: Long=1000000,seed: Long=42,
    randomAlgorithm: SamplingRandom.Algorithm=SamplingRandom.defaultAlgorithm,streams: RandomStreams.Config=RandomStreams.Config()) {
    require(drawsPerRound>=20 && maxRounds>=1 && maxRounds<=64 && eliteFraction.isFinite && eliteFraction>0 && eliteFraction<=.5)
    require(minEliteEss.isFinite && minEliteEss>=2 && priorWeight.isFinite && priorWeight>=1e-6 && priorWeight<1)
    require(covarianceInflation.isFinite && covarianceInflation>0 && diagonalRidge!=null && diagonalRidge.forall(x => x.isFinite && x>=0))
    require(maxScoreEvaluations>0 && maxStoredValues>0 && maxStoredValues<=10000000 && streams!=null)
    streams.validate(randomAlgorithm)
  }
  enum FitStatus { case Fitted, RoundBudgetReached, ScoreBudgetReached, InsufficientElite, NumericalFailure }
  /** Each round's actual threshold, effective elite count and observed event hits. */
  final case class Round(level: Double,eliteEss: Double,eventHits: Int)
  /** No pilot draw contributes to a production estimate; proposal exists only for Fitted.
    * densityEvaluations counts base/full-proposal calls, not nested component calls.
    */
  final case class FitResult(status: FitStatus,proposal: Option[Frozen],rounds: Vector[Round],
    scoreEvaluations: Int,densityEvaluations: Long,randomStream: RandomStreams.Descriptor,message: String)

  /** Fit weighted elite moments toward score(x)>=threshold, then freeze the proposal.
    * @param base normalized prior law; sampling/density callbacks must be mutually consistent
    * @param score pure finite event score; higher means nearer/in the event
    * @param threshold finite boundary, equality included
    * @param config explicit pilot limits and regularization; production is separate
    * @return one defensive Gaussian or explicit fit refusal; not automatic mixture fitting/mode discovery
    * @example `RareEventImportance.fitGaussian(base,x => x.head,5.0)` */
  def fitGaussian(base: VectorImportance.Proposal,score: Vector[Double] => Double,threshold: Double,config: FitConfig=FitConfig()): FitResult = {
    validate(base); require(score!=null && threshold.isFinite && config!=null)
    val d=base.dimension; val n=config.drawsPerRound
    require(n.toLong*(d+4)<=config.maxStoredValues,"Pilot storage budget exceeded")
    require(config.diagonalRidge.isEmpty || config.diagonalRidge.size==d)
    val allocated=stream(config.seed,config.randomAlgorithm,config.streams,0)
    val rng=new scala.util.Random(allocated.random)
    var current=prior(base); var history=Vector.empty[Round]; var calls=0; var previous=Double.NegativeInfinity
    def result(s: FitStatus,p: Option[Frozen],message: String)=FitResult(s,p,history,calls,2L*calls,allocated.descriptor,message)
    var round=0
    while(round<config.maxRounds) {
      if(n>config.maxScoreEvaluations-calls) return result(FitStatus.ScoreBudgetReached,None,"No partial pilot batch started")
      val rows=Vector.fill(n) {
        check(); val x=current.proposal.sample(rng); check(); point(x,d)
        val w=weight(current,x); val s=scoreAt(score,x); calls+=1; (x,s,w)
      }
      val ranked=rows.map(_._2).sorted
      val quantile=ranked(math.min(n-1,math.floor((1-config.eliteFraction)*n).toInt))
      val level=math.min(threshold,math.max(previous,quantile)); previous=level
      val elite=rows.filter(r => r._2>=level && r._3!=Double.NegativeInfinity)
      val peak=elite.map(_._3).maxOption.getOrElse(Double.NegativeInfinity)
      val raw=elite.map(r => math.exp(r._3-peak)); val sum=raw.sum
      val ess=if(sum==0) 0.0 else sum*sum/raw.map(w => w*w).sum
      history :+= Round(level,ess,rows.count(_._2>=threshold))
      if(!ess.isFinite || ess<config.minEliteEss) return result(FitStatus.InsufficientElite,None,"Insufficient weighted elite information")
      val weights=raw.map(_/sum)
      val mean=Vector.tabulate(d)(i => elite.indices.map(k => weights(k)*elite(k)._1(i)).sum)
      val covariance=Vector.tabulate(d,d) { (i,j) =>
        val a=math.min(i,j); val b=math.max(i,j)
        val v=elite.indices.map(k => weights(k)*(elite(k)._1(a)-mean(a))*(elite(k)._1(b)-mean(b))).sum
        (v+(if(i==j && config.diagonalRidge.nonEmpty) config.diagonalRidge(i) else 0))*config.covarianceInflation
      }
      val candidate=try VectorImportance.Gaussian(G(mean,covariance))
        catch { case _: IllegalArgumentException | _: ArithmeticException | _: org.apache.commons.math3.exception.MathIllegalArgumentException =>
          return result(FitStatus.NumericalFailure,None,"Weighted elite covariance is numerically unresolved") }
      current=defensive(base,candidate,config.priorWeight)
      if(level==threshold) return result(FitStatus.Fitted,Some(current),"Pilot threshold reached; independent production and region checks still required")
      round+=1
    }
    result(FitStatus.RoundBudgetReached,None,"Event threshold not reached within pilot round budget")
  }
}
