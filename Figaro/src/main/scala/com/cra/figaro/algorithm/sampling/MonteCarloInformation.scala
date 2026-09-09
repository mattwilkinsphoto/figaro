package com.cra.figaro.algorithm.sampling

import com.cra.figaro.util.SamplingRandom

/** Fixed-budget Monte Carlo information metrics for normalized continuous vector laws.
  * Estimates/MCSE are not certified bounds, precision stopping, or mode-discovery guarantees.
  * Signed noisy KL/MI estimates are NOT clipped to zero.
  */
object MonteCarloInformation {
  /** @param draws fixed independent samples, 2..1000000
    * @param seed private RNG seed
    * @param randomAlgorithm named scientific backend
    */
  final case class Config(draws: Int=10000,seed: Long=43,
    randomAlgorithm: SamplingRandom.Algorithm=SamplingRandom.defaultAlgorithm) {
    require(draws>=2 && draws<=1000000 && randomAlgorithm!=null)
  }
  enum Status { case Estimated, NumericallyUnresolved }
  /** @param value signed KL/MI or nonnegative Bhattacharyya estimate in nats; absent on unresolved overlap
    * @param mcse plug-in standard error (delta method for Bhattacharyya), not a confidence interval
    * @param rawMean mean of sampled log ratios, or affinity for Bhattacharyya
    * @param rawMcse standard error of that raw sample mean
    * @param evaluations complete density evaluations, excludes internal mixture-component calls
    * @param method estimator identity; record config/provider as well as seed
    */
  final case class Result(status: Status,value: Option[Double],mcse: Option[Double],rawMean: Double,
    rawMcse: Double,evaluations: Long,method: String,config: Config,randomProvider: String)
  private def check(): Unit=ParetoTail.interrupted()
  private def validate(p: VectorImportance.Proposal,q: VectorImportance.Proposal,c: Config): Unit = {
    check(); require(p!=null && q!=null && c!=null && p.dimension>=1 && p.dimension<=128 && p.dimension==q.dimension)
  }
  private def finiteLog(q: VectorImportance.Proposal,x: Vector[Double]): Double = {
    check(); val value=q.logDensity(x); check()
    require(value.isFinite || value==Double.NegativeInfinity,"Invalid normalized log density")
    value
  }
  private def estimate(c: Config,method: String,affinity: Boolean)(draw: scala.util.Random => Double): Result = {
    val rng=SamplingRandom.scalaRandom(c.seed,c.randomAlgorithm)
    var mean=0.0; var m2=0.0
    for(i <- 1 to c.draws) {
      check(); val x=draw(rng); check()
      if(!x.isFinite) throw new ArithmeticException("Information integrand outside finite numeric range; infinity is not proved")
      val delta=x-mean; mean+=delta/i; m2+=delta*(x-mean)
      if(!mean.isFinite || !m2.isFinite) throw new ArithmeticException("Information moment overflow")
    }
    val se=math.sqrt(math.max(0,m2)/(c.draws-1)/c.draws)
    val resolved= !affinity || (mean>0 && mean<=1 && se/mean<1 && (se>0 || mean==1))
    Result(if(resolved) Status.Estimated else Status.NumericallyUnresolved,
      if(resolved) Some(if(affinity) -math.log(mean) else mean) else None,
      if(resolved) Some(if(affinity) se/mean else se) else None,mean,se,2L*c.draws,method,c,SamplingRandom.provenance(c.randomAlgorithm))
  }
  /** @param p normalized first law; samples are drawn from p
    * @param q normalized comparison law, positive wherever p contributes
    * @param config fixed work and RNG policy
    * @return estimate of E_p[log p-log q]; finite-variance MCSE is an assumption, not verified
    * @example `kl(VectorImportance.Gaussian(p),VectorImportance.Gaussian(q))`
    */
  def kl(p: VectorImportance.Proposal,q: VectorImportance.Proposal,config: Config=Config()): Result = {
    validate(p,q,config)
    estimate(config,"iid directed KL",false) { rng =>
      val x=p.sample(rng); require(x!=null && x.size==p.dimension && x.forall(_.isFinite))
      val a=finiteLog(p,x); val b=finiteLog(q,x)
      a-b
    }
  }
  /** Sample the equal mixture (p+q)/2; the affinity integrand 2*sqrt(p*q)/(p+q) is bounded by one.
    * @param p first normalized law
    * @param q second normalized law
    * @param config fixed draws and private RNG
    * @return negative log estimated affinity with delta MCSE; unresolved/zero overlap is not infinity
    * @example `bhattacharyya(VectorImportance.Gaussian(p),VectorImportance.StudentT(t))`
    */
  def bhattacharyya(p: VectorImportance.Proposal,q: VectorImportance.Proposal,config: Config=Config()): Result = {
    validate(p,q,config)
    estimate(config,"iid equal-mixture affinity",true) { rng =>
      val chooseFirst=rng.nextBoolean()
      val selected=if(chooseFirst) p else q
      val x=selected.sample(rng); require(x!=null && x.size==p.dimension && x.forall(_.isFinite))
      val a=finiteLog(p,x); val b=finiteLog(q,x)
      require((if(chooseFirst) a else b).isFinite,"Selected law must have finite density at its own draw")
      if(a==Double.NegativeInfinity && b==Double.NegativeInfinity)
        throw new ArithmeticException("Sample outside both reported supports")
      if(a==Double.NegativeInfinity || b==Double.NegativeInfinity) 0.0
      else { val difference=math.abs(a-b); math.exp(math.log(2)-.5*difference-math.log1p(math.exp(-difference))) }
    }
  }
  /** MI is KL of a joint law to the product of its EXACT marginals, in concatenated coordinates.
    * @param joint normalized joint law
    * @param first exact leading-block marginal, not a conditional or independently fitted approximation
    * @param second exact remaining-block marginal
    * @param config fixed work/RNG policy
    * @return signed noisy MI estimate; the library cannot prove supplied laws are the true marginals
    * @example `mutualInformation(joint,firstMarginal,secondMarginal)`
    */
  def mutualInformation(joint: VectorImportance.Proposal,first: VectorImportance.Proposal,
    second: VectorImportance.Proposal,config: Config=Config()): Result = {
    require(joint!=null && first!=null && second!=null && first.dimension+second.dimension==joint.dimension)
    val product=VectorImportance.Conditional(first,second.dimension,_ => second)
    kl(joint,product,config).copy(method="iid joint-to-product MI",evaluations=3L*config.draws)
  }
}
