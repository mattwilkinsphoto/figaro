package com.cra.figaro.library.atomic.discrete
import com.cra.figaro.library.atomic.{DistributionNumerics as N,DiscreteInformation,InformationMetricResult,InformationMetricStatus as S,MetricCalculation as M}

/** Finite mixture of count laws, not an integer-rounded continuous mixture.
  * @param weights matching nonnegative probabilities, sum within 1e-12 of one
  * @param components 1..128 immutable count laws; zero-weight laws are ignored
  * @example `CountMixtureDistribution(Vector(.4,.6),Vector(NegativeBinomialDistribution(2,.5),NegativeBinomialDistribution(4,.7)))`
  */
final case class CountMixtureDistribution(weights: Vector[Double],components: Vector[CountDistribution]) extends CountDistribution {
  require(weights!=null && components!=null && weights.nonEmpty && weights.size<=128 && weights.size==components.size && components.forall(_!=null))
  require(weights.forall(x => x.isFinite && x>=0) && math.abs(weights.sum-1)<=1e-12)
  private[discrete] val normalized=weights.map(_/weights.sum)
  private val active=components.indices.filter(normalized(_)>0).toVector
  def logProbability(k: Int): Double = {
    N.check(); val logs=active.map(i => math.log(normalized(i))+components(i).logProbability(k)); val peak=logs.max
    if(logs.exists(x => x.isNaN || x==Double.PositiveInfinity)) throw new ArithmeticException("Invalid component log mass")
    if(peak==Double.NegativeInfinity) peak else peak+math.log(logs.map(x => math.exp(x-peak)).sum)
  }
  def cdf(k: Int): Double={ N.check(); math.min(1,active.map(i => normalized(i)*components(i).cdf(k)).sum) }
  def survival(k: Int): Double={ N.check(); math.min(1,active.map(i => normalized(i)*components(i).survival(k)).sum) }
  val support: (Int,Option[Int])=(active.map(i => components(i).support._1).min,
    if(active.exists(i => components(i).support._2.isEmpty)) None else Some(active.map(i => components(i).support._2.get).max))
  def quantile(p: Double): Int={
    N.probability(p); if(p==0) return support._1
    if(p==1) return support._2.getOrElse(throw new ArithmeticException("Unbounded count quantile cannot represent p=1"))
    var lo=support._1.toLong-1; var hi=support._2.getOrElse(Int.MaxValue).toLong
    def reached(k: Int)=if(p<=.5) cdf(k)>=p else survival(k)<=1-p
    if(!reached(hi.toInt)) throw new ArithmeticException("Mixture quantile exceeds Int range")
    while(hi-lo>1) { N.check(); val mid=(lo+hi)/2; if(reached(mid.toInt)) hi=mid else lo=mid }
    hi.toInt
  }
  def mean: Double=active.map(i => normalized(i)*components(i).mean).sum
  def variance: Double={ val m=mean; active.map(i => normalized(i)*(components(i).variance+math.pow(components(i).mean-m,2))).sum }
  /** @param k count with positive representable mixture mass
    * @return membership probabilities in original component order
    * @example `law.responsibilities(3)` */
  def responsibilities(k: Int): Vector[Double]={
    val log=logProbability(k); if(!log.isFinite) throw new ArithmeticException("Undefined count responsibilities")
    components.indices.map(i => if(normalized(i)==0) 0.0 else math.exp(math.log(normalized(i))+components(i).logProbability(k)-log)).toVector
  }
}

/** Full-law finite-support comparisons; no component-average substitution or unbounded-tail truncation. */
object CountMixtureInformation {
  private def range(p: CountDistribution,q: CountDistribution,cap: Int): Option[Range.Inclusive] = {
    require(p!=null && q!=null && cap>=1 && cap<=100000)
    for(a <- p.support._2;b <- q.support._2 if math.max(a,b).toLong-math.min(p.support._1,q.support._1)+1<=cap)
      yield math.min(p.support._1,q.support._1) to math.max(a,b)
  }
  private def mass(p: CountDistribution,k: Int): Double={
    val l=p.logProbability(k); val v=math.exp(l)
    if(!v.isFinite || (v==0 && l.isFinite)) throw new ArithmeticException("Finite-table mass outside numeric range")
    v
  }
  private def compare(p: CountDistribution,q: CountDistribution,tolerance: Double,maxTerms: Int,bh: Boolean): InformationMetricResult={
    N.check(); require(tolerance.isFinite && tolerance>0)
    val r=range(p,q,maxTerms)
    if(p==q) return M.identity
    if(r.isEmpty) return M.unavailable(if(p.support._2.isEmpty || q.support._2.isEmpty) S.Unsupported else S.BudgetExhausted)
    try {
      val a=r.get.map(mass(p,_)); val b=r.get.map(mass(q,_))
      if(bh) DiscreteInformation.bhattacharyya(a,b,tolerance) else DiscreteInformation.kl(a,b,tolerance)
    } catch { case _: ArithmeticException => M.unavailable(S.NumericallyUnresolved) }
  }
  /** @param p source count law with finite support
    * @param q comparison law, same integer measure
    * @param tolerance numerical allowance in nats
    * @param maxTerms maximum union-support counts, 1..100000
    * @return full-law KL; Unsupported for nonidentical unbounded laws
    * @example `CountMixtureInformation.kl(p,q)` */
  def kl(p: CountDistribution,q: CountDistribution,tolerance: Double=1e-8,maxTerms: Int=100000): InformationMetricResult=compare(p,q,tolerance,maxTerms,false)
  /** @param p first finite-support count law
    * @param q second law
    * @param tolerance numerical allowance in nats
    * @param maxTerms union-support count cap
    * @return full-law Bhattacharyya divergence
    * @example `CountMixtureInformation.bhattacharyya(p,q)` */
  def bhattacharyya(p: CountDistribution,q: CountDistribution,tolerance: Double=1e-8,maxTerms: Int=100000): InformationMetricResult=compare(p,q,tolerance,maxTerms,true)
  /** MI of the explicitly defined component label and count, NOT MI between two mixtures.
    * @param law finite-support count mixture
    * @param tolerance numerical allowance in nats
    * @param maxTerms total component-by-count cells, 1..100000
    * @return I(component;count), or refusal without truncating an infinite tail
    * @example `CountMixtureInformation.componentMutualInformation(law)` */
  def componentMutualInformation(law: CountMixtureDistribution,tolerance: Double=1e-8,maxTerms: Int=100000): InformationMetricResult={
    N.check(); require(law!=null && tolerance.isFinite && tolerance>0)
    val r=range(law,law,maxTerms)
    if(r.isEmpty) return M.unavailable(if(law.support._2.isEmpty) S.Unsupported else S.BudgetExhausted)
    if(r.get.size.toLong*law.components.size>maxTerms) return M.unavailable(S.BudgetExhausted)
    try DiscreteInformation.mutualInformation(law.components.indices.map(i => r.get.map { k =>
      if(law.normalized(i)==0) 0.0 else {
        val component=mass(law.components(i),k); val joint=law.normalized(i)*component
        if(joint==0 && component>0) throw new ArithmeticException("Joint component-count mass underflow")
        joint
      }
    }),tolerance)
    catch { case _: ArithmeticException => M.unavailable(S.NumericallyUnresolved) }
  }
}
