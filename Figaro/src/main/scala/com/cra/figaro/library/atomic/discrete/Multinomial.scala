package com.cra.figaro.library.atomic.discrete

import com.cra.figaro.language.*
import com.cra.figaro.library.atomic.{DistributionNumerics as N,InformationMetricResult,InformationMetricStatus,MetricCalculation as M,DiscreteInformation}
import org.apache.commons.math3.special.Gamma

/** Joint category counts, not a categorical choice. Counting measure on vectors summing to trials.
  * @param trials integer in [0,1000000]
  * @param probabilities 1..128 finite nonnegative entries, sum within 1e-12 of one; normalized consistently
  */
final case class MultinomialDistribution(trials: Int,probabilities: Vector[Double]) {
  require(trials>=0 && trials<=1000000)
  require(probabilities!=null && probabilities.nonEmpty && probabilities.size<=128 && probabilities.forall(x => x.isFinite && x>=0))
  private val total=probabilities.sum
  require(math.abs(total-1)<=1e-12,"probabilities must sum to one within 1e-12")
  val normalizedProbabilities: Vector[Double]=probabilities.map(_/total)
  val dimension: Int=probabilities.size
  /** @param counts matching count vector
    * @return log probability; negative counts, wrong total or impossible categories give -Infinity
    * @example `MultinomialDistribution(4,Vector(.2,.3,.5)).logProbability(Vector(1,1,2))`
    */
  def logProbability(counts: Vector[Int]): Double = {
    N.check(); require(counts!=null && counts.size==dimension)
    if(counts.exists(_<0) || counts.map(_.toLong).sum!=trials || counts.indices.exists(i => counts(i)>0 && normalizedProbabilities(i)==0)) return Double.NegativeInfinity
    Gamma.logGamma(trials+1.0)-counts.map(x => Gamma.logGamma(x+1.0)).sum+
      counts.indices.map(i => if(counts(i)==0) 0.0 else counts(i)*math.log(normalizedProbabilities(i))).sum
  }
  /** @param counts matching vector
    * @return probability mass, possibly underflowing
    * @example `law.probability(Vector(1,1,2))`
    */
  def probability(counts: Vector[Int]): Double=math.exp(logProbability(counts))
  /** @return expected category counts n*p */
  def mean: Vector[Double]=normalizedProbabilities.map(_*trials)
  /** @return covariance n*(diag(p)-p*p^T), singular because total is fixed */
  def covariance: Vector[Vector[Double]]=Vector.tabulate(dimension,dimension)((i,j) => trials*((if(i==j) normalizedProbabilities(i) else 0)-normalizedProbabilities(i)*normalizedProbabilities(j)))
  /** Sequential conditional binomial draws; work does not loop once per trial.
    * @param rng private caller-owned generator, not retained
    * @return supported count vector; interruption/numeric failure throws, no retry filtering
    * @example `law.sample(com.cra.figaro.util.SamplingRandom.scalaRandom(42))`
    */
  def sample(rng: scala.util.Random): Vector[Int] = {
    require(rng!=null); N.check()
    val out=new Array[Int](dimension); var remaining=trials
    val adapter=new org.apache.commons.math3.random.AbstractRandomGenerator {
      def setSeed(seed: Long): Unit=throw new UnsupportedOperationException("Caller owns seed")
      def nextDouble(): Double=N.open(rng)
    }
    for(i <- 0 until dimension-1) {
      N.check()
      val mass=normalizedProbabilities.drop(i).sum
      val p=if(mass==0) 0.0 else normalizedProbabilities(i)/mass
      out(i)=if(remaining==0 || p==0) 0 else if(p>=1) remaining
        else new org.apache.commons.math3.distribution.BinomialDistribution(adapter,remaining,p).sample()
      remaining-=out(i)
    }
    out(dimension-1)=remaining; out.toVector
  }
}

/** Analytic count-vector comparisons and MI between complementary category blocks. */
object MultinomialInformation {
  private def validate(p: MultinomialDistribution,q: MultinomialDistribution,t: Double): Unit = {
    N.check(); require(p!=null && q!=null && p.dimension==q.dimension && t.isFinite && t>0)
  }
  private def scaled(p: MultinomialDistribution,q: MultinomialDistribution,t: Double,bh: Boolean): InformationMetricResult = {
    validate(p,q,t)
    if(p.trials!=q.trials) return M.infinite
    if(p.trials==0) return M.identity
    val r=if(bh) DiscreteInformation.bhattacharyya(p.normalizedProbabilities,q.normalizedProbabilities,t/p.trials)
      else DiscreteInformation.kl(p.normalizedProbabilities,q.normalizedProbabilities,t/p.trials)
    r.copy(value=r.value.map(_*p.trials),errorEstimate=r.errorEstimate*p.trials,method="multinomial category reduction")
  }
  /** @param p source counts
    * @param q comparison counts in the same category order; unequal totals imply disjoint support
    * @param tolerance positive numerical target in nats
    * @return n times categorical directed KL, with propagated numerical allowance/refusal
    * @example `MultinomialInformation.kl(p,q)`
    */
  def kl(p: MultinomialDistribution,q: MultinomialDistribution,tolerance: Double=1e-8): InformationMetricResult=scaled(p,q,tolerance,false)
  /** @param p first law
    * @param q second law in matching category order
    * @param tolerance positive numerical target in nats
    * @return negative log count-vector affinity
    * @example `MultinomialInformation.bhattacharyya(p,q)`
    */
  def bhattacharyya(p: MultinomialDistribution,q: MultinomialDistribution,tolerance: Double=1e-8): InformationMetricResult=scaled(p,q,tolerance,true)
  /** The complementary blocks are independent given their total count; each determines that total.
    * @param law one joint count law
    * @param first nonempty distinct category indices; complement must also be nonempty
    * @param tolerance positive numerical allowance in nats
    * @param maxTerms integer in [1,100000]; n+1 terms required except deterministic totals
    * @return I(counts(first);counts(complement)) = entropy of their binomial subtotal; no omitted-tail approximation
    * @example `MultinomialInformation.mutualInformation(law,Vector(0))`
    */
  def mutualInformation(law: MultinomialDistribution,first: Vector[Int],tolerance: Double=1e-8,maxTerms: Int=100000): InformationMetricResult = {
    validate(law,law,tolerance)
    require(first!=null && first.nonEmpty && first.size<law.dimension && first.distinct.size==first.size && first.forall(i => i>=0 && i<law.dimension))
    require(maxTerms>=1 && maxTerms<=100000)
    val a=first.map(law.normalizedProbabilities).sum
    val b=law.normalizedProbabilities.indices.filterNot(first.contains).map(law.normalizedProbabilities).sum
    if(law.trials==0 || a==0 || b==0) return M.identity
    if(law.trials.toLong+1>maxTerms) return M.unavailable(InformationMetricStatus.BudgetExhausted,0,"binomial subtotal entropy")
    val binomial=new org.apache.commons.math3.distribution.BinomialDistribution(law.trials,a/(a+b))
    var sum=0.0; var correction=0.0
    for(k <- 0 to law.trials) {
      N.check(); val lp=binomial.logProbability(k); val term=if(lp==Double.NegativeInfinity) 0 else -math.exp(lp)*lp
      val adjusted=term-correction; val next=sum+adjusted; correction=(next-sum)-adjusted; sum=next
    }
    val r=M.analytic(sum,tolerance,(law.trials+1.0)*(1+math.abs(sum)),"binomial subtotal entropy")
    r.copy(status=if(r.value.nonEmpty) InformationMetricStatus.Estimated else r.status,evaluations=law.trials+1)
  }
}

final class AtomicMultinomial private[discrete](name: Name[Vector[Int]],val distribution: MultinomialDistribution,collection: ElementCollection)
  extends Element[Vector[Int]](name,collection) with Atomic[Vector[Int]] with HasLogDensity[Vector[Int]] {
  type Randomness=Vector[Int]
  def generateRandomness(): Vector[Int]=distribution.sample(com.cra.figaro.util.random)
  def generateValue(value: Vector[Int]): Vector[Int]=value
  def logDensity(value: Vector[Int]): Double=distribution.logProbability(value)
}
object Multinomial {
  /** @param distribution validated count kernel
    * @param name contextual name
    * @param collection owning collection
    * @return observation-ready vector-count element
    * @example `Multinomial(MultinomialDistribution(10,Vector(.2,.8)))`
    */
  def apply(distribution: MultinomialDistribution)(using name: Name[Vector[Int]],collection: ElementCollection): AtomicMultinomial = {
    require(distribution!=null); new AtomicMultinomial(name,distribution,collection)
  }
  /** @param distribution element producing validated kernels
    * @param name contextual name
    * @param collection owning collection
    * @return hierarchical non-caching count element
    * @example `Multinomial(probabilities.map(p => MultinomialDistribution(10,p)))`
    */
  def apply(distribution: Element[MultinomialDistribution])(using name: Name[Vector[Int]],collection: ElementCollection): Element[Vector[Int]] = {
    require(distribution!=null); NonCachingChain(distribution,(d: MultinomialDistribution) => apply(d)(using "",collection))
  }
}
