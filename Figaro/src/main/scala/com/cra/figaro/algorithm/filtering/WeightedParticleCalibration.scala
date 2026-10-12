package com.cra.figaro.algorithm.filtering

import com.cra.figaro.util.{RandomContext, SamplingRandom}

/** Split-pilot score/work calibration, not automatic accuracy selection (INF-10).
  * Each pilot uses independent draws of the same frozen candidate/ancestor law as production.
  * Production weights are never clipped or reused from the pilot.
  */
object WeightedParticleCalibration {
  enum Status { case Ready, NoFiniteScale, UnresolvedScoreMean, InsufficientBudget }

  /** Total ceiling includes BOTH pilots and production, per observation, not wall time.
    * scoreTarget is a work-policy choice, never ESS or desired posterior precision.
    * confidence and missProbability refer only to bounded score sums, under IID assumptions.
    */
  final case class Request(maxTotalCandidates: Int = 10000, scaleSamples: Int = 128,
    evaluationSamples: Int = 512, minCandidates: Int = 128, scoreTarget: Double = 100,
    scaleQuantile: Double = .9, confidence: Double = .99, missProbability: Double = .01) {
    require(scaleSamples >= 1 && evaluationSamples >= 1 && maxTotalCandidates <= 1000000)
    require(minCandidates >= 2 && scaleSamples.toLong+evaluationSamples+minCandidates <= maxTotalCandidates,
      "Total cap must cover two fixed pilots and production floor")
    require(scoreTarget.isFinite && scoreTarget > 0 && scoreTarget <= maxTotalCandidates-scaleSamples-evaluationSamples)
    require(scaleQuantile >= 0 && scaleQuantile <= 1 && confidence >= .8 && confidence <= .999 &&
      missProbability >= .001 && missProbability <= .25, "Invalid calibration probability")
  }

  /** Frozen policy and diagnostics. Non-Ready always means full remaining FIXED budget;
    * it is not a failed estimate or a reason to select only successful pilot runs.
    */
  final case class Advice(status: Status, logScale: Option[Double], meanScore: Option[Double],
    lowerMean: Double, minCandidates: Int, maxCandidates: Int, scoreTarget: Double,
    pilotCandidates: Int, missBound: Option[Double]) {
    /** Frozen score of the corrected log importance weight. No-scale fallback uses constant one. */
    def score(logWeight: Double): Double = logScale match {
      case Some(scale) => WeightedBoundedParticleFilter.cappedLikelihood(scale)(logWeight)
      case None => WeightedParticleProposal.checked(logWeight); 1.0
    }
    /** Pilot + maximum production work; actual work adds pilotCandidates to report.attempts. */
    def maximumTotalCandidates: Int = pilotCandidates+maxCandidates
    /** Copy only work settings; retain the filter's RNG and health policy. */
    def config(base: WeightedBoundedParticleFilter.Config): WeightedBoundedParticleFilter.Config = {
      require(base != null)
      base.copy(minCandidates=minCandidates,maxCandidates=maxCandidates,scoreTarget=scoreTarget)
    }
  }

  /** Run fixed-size, disjoint scale/evaluation pilots in an owned RNG scope.
    * @param request predeclared total ceiling and score-policy preferences
    * @param seed pilot seed, distinct from any separately run production stream
    * @param algorithm Figaro RNG backend
    * @param drawLogWeight IID corrected log-weight callback; no mutation or production reuse
    * @return frozen Advice; invalid input/callback/interruption throws, never partial advice
    * @example `run(Request(),101)(() => logWeightOfNewDraw())`
    */
  def run(request: Request, seed: Long,
    algorithm: SamplingRandom.Algorithm = SamplingRandom.defaultAlgorithm)(drawLogWeight: () => Double): Advice = {
    require(algorithm != null)
    RandomContext.withRandom(SamplingRandom.seeded(seed,algorithm))(calibrate(request)(drawLogWeight))
  }

  private[filtering] def calibrate(request: Request)(drawLogWeight: () => Double): Advice = {
    require(request != null && drawLogWeight != null)
    def draw(): Double = {
      if (Thread.currentThread().isInterrupted) throw new InterruptedException("Calibration interrupted")
      val w = WeightedParticleProposal.checked(drawLogWeight())
      if (Thread.currentThread().isInterrupted) throw new InterruptedException("Calibration interrupted")
      w
    }
    // Scale selection is engineering policy, NOT a known upper bound on likelihood/weights.
    val training = Vector.fill(request.scaleSamples)(draw()).filter(_.isFinite).sorted
    val scale = Option.when(training.nonEmpty)(training(math.floor(request.scaleQuantile*(training.size-1)).toInt))
    val evaluation = Vector.fill(request.evaluationSamples)(draw())
    val mean = scale.map(s => evaluation.iterator.map(WeightedBoundedParticleFilter.cappedLikelihood(s)).sum/evaluation.size)
    // One-sided Hoeffding lower confidence bound; numerical margin is conservative,
    // not an interval-arithmetic proof. Apply only to the independent evaluation batch.
    val lower = mean.map(m => math.max(0.0,m-math.sqrt(-math.log1p(-request.confidence)/(2*evaluation.size))-1e-12)).getOrElse(0.0)
    val pilots = request.scaleSamples+request.evaluationSamples
    val hardCap = request.maxTotalCandidates-pilots
    val status = if (scale.isEmpty) Status.NoFiniteScale
      else if (lower == 0) Status.UnresolvedScoreMean
      else if (miss(hardCap,lower,request.scoreTarget) > request.missProbability) Status.InsufficientBudget
      else Status.Ready
    if (status != Status.Ready)
      Advice(status,scale,mean,lower,hardCap,hardCap,request.scoreTarget,pilots,None)
    else {
      var lo = request.minCandidates; var hi = hardCap
      while (lo < hi) {
        val mid = lo+(hi-lo)/2
        if (miss(mid,lower,request.scoreTarget) <= request.missProbability) hi=mid else lo=mid+1
      }
      val floor = math.min(lo,math.max(request.minCandidates,math.ceil(request.scoreTarget/mean.get).toInt))
      Advice(status,scale,mean,lower,floor,lo,request.scoreTarget,pilots,Some(miss(lo,lower,request.scoreTarget)))
    }
  }

  private[figaro] def miss(n: Int, lower: Double, target: Double): Double = {
    val gap = n*lower-target
    if (gap <= 0) 1.0 else math.min(1.0,math.exp(-2*gap*gap/n)+1e-12)
  }
}
