package com.cra.figaro.algorithm.sampling

import java.math.{BigDecimal as Decimal, MathContext, RoundingMode}
import com.cra.figaro.util.SamplingRandom

/** Fixed-budget occupancy of a declared inventory under an IID sampling law.
  * Neither unknown-mode discovery nor a posterior coverage certificate from proposal samples.
  */
object DeclaredRegionCoverage {
  /** @param label unique nonblank inventory label, at most 200 characters
    * @param contains pure fixed region membership predicate; overlapping regions are allowed
    */
  final case class Region[A](label: String, contains: A => Boolean) {
    require(label!=null && label.trim.nonEmpty && label.length<=200 && contains!=null)
  }
  /** An external assumption, NOT an estimate from these draws.
    * @param minimumProbability known lower bound on EACH declared region's probability under samplingLaw
    * @param justification caller's reason for that lower bound; retained in the result
    */
  final case class MassAssumption(minimumProbability: Double, justification: String) {
    require(minimumProbability>0 && minimumProbability<=1 && justification!=null && justification.trim.nonEmpty)
  }
  /** @param samplingLaw human-readable identity of the law actually sampled (not an intended target)
    * @param draws fixed IID draws, 1..1000000; at most 10000000 predicate calls total
    * @param seed private seed
    * @param randomAlgorithm scientific RNG backend
    */
  final case class Config(samplingLaw: String, draws: Int=10000, seed: Long=43,
    randomAlgorithm: SamplingRandom.Algorithm=SamplingRandom.defaultAlgorithm) {
    require(samplingLaw!=null && samplingLaw.trim.nonEmpty && draws>=1 && draws<=1000000 && randomAlgorithm!=null)
  }
  enum Status { case DeclaredInventoryObserved, DeclaredRegionsUnobserved }
  /** Counts can sum above draws when regions overlap, or below draws for an incomplete inventory. */
  final case class Occupancy(label: String, count: Int)
  /** Ex-ante union bound for this FIXED budget and declared mass assumption, not posterior confidence.
    * probabilityUpper is rounded upward, so positive underflow is reported as Double.MIN_VALUE, not zero.
    * logUpper is an approximate log of the mathematical bound, not a directed numerical certificate.
    */
  final case class MissBound(probabilityUpper: Double, logUpper: Double, assumption: MassAssumption)
  /** Detached report, with no retained callbacks or observations. Unknown regions remain unassessed. */
  final case class Result(occupancies: Vector[Occupancy], status: Status, missBound: Option[MissBound],
    predicateCalls: Long, config: Config, randomProvider: String)

  private def bound(k: Int,n: Int,a: MassAssumption): MissBound = {
    val context=new MathContext(40,RoundingMode.CEILING)
    var base=Decimal.ONE.subtract(new Decimal(a.minimumProbability))
    var exponent=n; var product=Decimal.ONE
    while(exponent>0) {
      if((exponent & 1)==1) product=product.multiply(base,context)
      exponent=exponent >>> 1
      if(exponent>0) base=base.multiply(base,context)
    }
    val upper=product.multiply(Decimal.valueOf(k.toLong),context).min(Decimal.ONE)
    val rounded=upper.doubleValue()
    val p=if(new Decimal(rounded).compareTo(upper)<0) Math.nextUp(rounded) else rounded
    MissBound(p,math.min(0,math.log(k)+n*math.log1p(-a.minimumProbability)),a)
  }
  /** Count visits to fixed regions without retaining draws or automatically changing sampling strategy.
    * @param config fixed sample count, sampling-law identity and RNG
    * @param regions 1..128 distinct named predicates, chosen before drawing
    * @param massAssumption optional externally justified minimum sampling probability per region
    * @param sample pure IID draw with the supplied RNG; callbacks must return promptly
    * @return observed/unobserved inventory and optional conditional, fixed-budget union bound
    * @example `run(Config("uniform"),Vector(Region[Double]("left",_ < 0.5)))(_.nextDouble())`
    */
  def run[A](config: Config,regions: Vector[Region[A]],massAssumption: Option[MassAssumption]=None)
    (sample: scala.util.Random => A): Result = {
    ParetoTail.interrupted(); require(config!=null && regions!=null && massAssumption!=null && sample!=null)
    require(regions.nonEmpty && regions.size<=128 && regions.forall(_!=null))
    require(regions.map(_.label).distinct.size==regions.size && config.draws.toLong*regions.size<=10000000)
    require(massAssumption.forall(_!=null))
    val rng=SamplingRandom.scalaRandom(config.seed,config.randomAlgorithm)
    val counts=Array.fill(regions.size)(0)
    for(_ <- 0 until config.draws) {
      ParetoTail.interrupted(); val value=sample(rng); ParetoTail.interrupted()
      regions.indices.foreach { j =>
        ParetoTail.interrupted(); val inside=regions(j).contains(value); ParetoTail.interrupted()
        if(inside) counts(j)+=1
      }
    }
    Result(regions.indices.map(j => Occupancy(regions(j).label,counts(j))).toVector,
      if(counts.forall(_>0)) Status.DeclaredInventoryObserved else Status.DeclaredRegionsUnobserved,
      massAssumption.map(bound(regions.size,config.draws,_)),config.draws.toLong*regions.size,
      config,SamplingRandom.provenance(config.randomAlgorithm))
  }
}
