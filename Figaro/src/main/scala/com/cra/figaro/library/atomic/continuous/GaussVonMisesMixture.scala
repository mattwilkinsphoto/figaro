package com.cra.figaro.library.atomic.continuous

import com.cra.figaro.language.*
import com.cra.figaro.util.{random,CircularStatistics,SamplingRandom}
import com.cra.figaro.library.atomic.DistributionNumerics as N
import com.cra.figaro.algorithm.sampling.{VectorImportance,MonteCarloInformation}

/** Fixed finite mixture on R^n times S^1, not PDF fusion or a parameter fitter.
  * @param weights 1..128 nonnegative probabilities summing to one within 1e-12
  * @param components matching immutable GVM kernels sharing coordinate meanings and dimension 1..32
  */
final case class GaussVonMisesMixtureDistribution(weights: Vector[Double],components: Vector[GaussVonMisesDistribution]) {
  require(weights!=null && components!=null && weights.nonEmpty && weights.size<=128 && weights.size==components.size)
  require(weights.forall(w=>w.isFinite && w>=0) && math.abs(weights.sum-1)<=1e-12)
  require(components.forall(_!=null)); val dimension: Int=components.head.dimension
  require(dimension<=32 && components.forall(_.dimension==dimension))
  private def terms(value: LinearAngular): Vector[Double] = {
    require(value!=null && value.linear.size==dimension); N.check()
    weights.indices.map(i=>if(weights(i)==0) Double.NegativeInfinity else math.log(weights(i))+components(i).logDensity(value)).toVector
  }
  private def logSum(values: Vector[Double]): Double = {
    val largest=values.max
    if(largest==Double.NegativeInfinity) largest else largest+math.log(values.map(v=>math.exp(v-largest)).sum)
  }
  /** @param value matching linear coordinates and angle in radians @return full-mixture log density
    * @example `law.logDensity(LinearAngular(Vector(0),3.1))` */
  def logDensity(value: LinearAngular): Double=logSum(terms(value))
  /** @param value joint state @return ordinary full density, potentially underflowing
    * @example `law.density(point)` */
  def density(value: LinearAngular): Double=math.exp(logDensity(value))
  /** @param value joint state @return posterior component probabilities; undefined tails throw
    * @example `law.responsibilities(point)` */
  def responsibilities(value: LinearAngular): Vector[Double] = {
    val t=terms(value); val z=logSum(t)
    if(!z.isFinite) throw new ArithmeticException("Mixture responsibilities unresolved")
    t.map(v=>math.exp(v-z))
  }
  private[continuous] def drawComponent(rng: scala.util.Random): Int = {
    require(rng!=null); N.check(); val u=N.open(rng); var sum=0.0; var i=0
    while(i<weights.size-1) { sum+=weights(i); if(u<sum) return i; i+=1 }
    // Tolerated normalization rounding must not select a zero-weight final component.
    if(weights(i)>0) i else weights.lastIndexWhere(_>0)
  }
  /** @param rng caller-owned RNG @return mixture draw, with no mutable state retained
    * @example `law.sample(SamplingRandom.scalaRandom(42))` */
  def sample(rng: scala.util.Random): LinearAngular=components(drawComponent(rng)).sample(rng)
  /** @return exact linear Gaussian mixture marginal, not a conditional law
    * @example `law.linearMarginal.logDensity(Vector(0))` */
  def linearMarginal: GaussianMixtureDistribution=GaussianMixtureDistribution(weights,components.map(g=>MultivariateGaussianDistribution(g.mean,g.covariance)))
  /** @param chartCenter finite center in radians @return normalized vector proposal in the
    * half-open angular chart [center-Pi,center+Pi); outside chart density is zero.
    * @example `law.asProposal()` can be used with MonteCarloInformation, unlike a periodically repeated Euclidean PDF
    */
  def asProposal(chartCenter: Double=0): VectorImportance.Proposal = {
    require(chartCenter.isFinite && math.abs(chartCenter)<=1e6,"Bounded chart center required")
    val self=this
    new VectorImportance.Proposal {
      val dimension=self.dimension+1
      def sample(rng: scala.util.Random): Vector[Double] = {
        val x=self.sample(rng); val angle=chartCenter+CircularStatistics.normalize(x.angle-chartCenter)
        if(angle<chartCenter-math.Pi || angle>=chartCenter+math.Pi) throw new ArithmeticException("Angular chart rounding")
        x.linear :+ angle
      }
      def logDensity(x: Vector[Double]): Double = {
        require(x!=null && x.size==dimension && x.forall(_.isFinite))
        if(x.last<chartCenter-math.Pi || x.last>=chartCenter+math.Pi) Double.NegativeInfinity
        else self.logDensity(LinearAngular(x.init,x.last))
      }
    }
  }
}

final class AtomicGaussVonMisesMixture private[continuous](name: Name[LinearAngular],val distribution: GaussVonMisesMixtureDistribution,collection: ElementCollection)
  extends Element[LinearAngular](name,collection) with Atomic[LinearAngular] with Continuous[LinearAngular] with HasLogDensity[LinearAngular] {
  type Randomness=LinearAngular
  def generateRandomness(): LinearAngular=distribution.sample(random)
  def generateValue(value: LinearAngular): LinearAngular=value
  def logDensity(value: LinearAngular): Double=distribution.logDensity(value)
  def logp(value: LinearAngular): Double=logDensity(value)
}
object GaussVonMisesMixture {
  /** @param distribution fixed mixture @param name element name @param collection owning graph
    * @return observation-ready joint element; each worker owns its graph
    * @example `GaussVonMisesMixture(law)` */
  def apply(distribution: GaussVonMisesMixtureDistribution)(using name: Name[LinearAngular],collection: ElementCollection): AtomicGaussVonMisesMixture = {
    require(distribution!=null); new AtomicGaussVonMisesMixture(name,distribution,collection)
  }
}

object GaussVonMisesMixtureInformation {
  /** @param p first mixture @param q second, same coordinate meanings @param config fixed work/RNG
    * @return full-mixture directed KL estimate/MCSE, not a componentwise average or certified bound
    * @example `kl(p,q,MonteCarloInformation.Config(20000,42))` */
  def kl(p: GaussVonMisesMixtureDistribution,q: GaussVonMisesMixtureDistribution,config: MonteCarloInformation.Config=MonteCarloInformation.Config()): MonteCarloInformation.Result =
    MonteCarloInformation.kl(p.asProposal(),q.asProposal(),config)
  /** @param p first mixture @param q second @param config fixed work/RNG
    * @return full-mixture Bhattacharyya estimate, or numerical refusal
    * @example `bhattacharyya(p,q)` */
  def bhattacharyya(p: GaussVonMisesMixtureDistribution,q: GaussVonMisesMixtureDistribution,config: MonteCarloInformation.Config=MonteCarloInformation.Config()): MonteCarloInformation.Result =
    MonteCarloInformation.bhattacharyya(p.asProposal(),q.asProposal(),config)
  /** MI between the explicit component label and the COMPLETE joint state, NOT
    * between linear and angular partitions of the mixture.
    * @param law fixed mixture @param config fixed draws/RNG @return signed IID estimate and plug-in MCSE
    * @example `componentMutualInformation(law)`; duplicate components give zero
    */
  def componentMutualInformation(law: GaussVonMisesMixtureDistribution,config: MonteCarloInformation.Config=MonteCarloInformation.Config()): MonteCarloInformation.Result = {
    require(law!=null && config!=null)
    val rng=SamplingRandom.scalaRandom(config.seed,config.randomAlgorithm); var mean=0.0; var m2=0.0
    for(i<-1 to config.draws) {
      N.check(); val k=law.drawComponent(rng); val x=law.components(k).sample(rng)
      val y=law.components(k).logDensity(x)-law.logDensity(x)
      if(!y.isFinite) throw new ArithmeticException("Component information unresolved")
      val delta=y-mean; mean+=delta/i; m2+=delta*(y-mean)
    }
    val se=math.sqrt(math.max(0,m2)/(config.draws-1)/config.draws)
    if(!mean.isFinite || !se.isFinite) throw new ArithmeticException("Component information moment overflow")
    MonteCarloInformation.Result(MonteCarloInformation.Status.Estimated,Some(mean),Some(se),mean,se,
      2L*config.draws,"iid component-label/joint-state MI",config,SamplingRandom.provenance(config.randomAlgorithm))
  }
}
