package com.cra.figaro.library.atomic.continuous

import com.cra.figaro.language.*
import com.cra.figaro.util.random
import com.cra.figaro.library.atomic.{DistributionNumerics as N,InformationMetricResult,InformationMetricStatus as S}

/** Finite atoms plus an optional continuous slab. Not a ScalarDistribution: its
  * reference measure includes counting measure at atoms as well as Lebesgue measure.
  * @param atoms 0..128 distinct finite locations and strictly positive masses
  * @param slab continuous law and strictly positive mixture weight, or None
  * Weights must sum to one within 1e-12; input collections are immutable.
  */
final case class MixedScalarDistribution(atoms: Vector[(Double,Double)],slab: Option[(ScalarDistribution,Double)]) {
  require(atoms!=null && atoms.size<=128 && atoms.forall((x,w)=>x.isFinite && w.isFinite && w>0))
  require(atoms.map(_._1).distinct.size==atoms.size,"Atom locations must be distinct")
  require(slab!=null && slab.forall((d,w)=>d!=null && w.isFinite && w>0))
  require(math.abs(atoms.map(_._2).sum+slab.map(_._2).getOrElse(0.0)-1)<=1e-12,"Normalized mixed weights required")
  /** @param x finite location @return P(X=x), never continuous density
    * @example `law.probabilityAt(0)` */
  def probabilityAt(x: Double): Double = { require(x.isFinite); atoms.find(_._1==x).map(_._2).getOrElse(0.0) }
  /** @param x finite location @return log continuous sub-density (weighted slab), including at atoms
    * @example `law.continuousLogDensity(1)` */
  def continuousLogDensity(x: Double): Double = {
    require(x.isFinite); N.check()
    slab.map((d,w)=>math.log(w)+d.logDensity(x)).getOrElse(Double.NegativeInfinity)
  }
  /** @param x finite exact observation @return log mass at an atom, otherwise log sub-density
    * @example `law.logLikelihood(0)` does NOT add the slab's density to atom mass */
  def logLikelihood(x: Double): Double = {
    val p=probabilityAt(x); val result=if(p>0) math.log(p) else continuousLogDensity(x)
    if(result.isNaN || result==Double.PositiveInfinity) throw new ArithmeticException("Singular mixed likelihood")
    result
  }
  /** @param x non-NaN endpoint @return P(X<=x), including endpoint atoms
    * @example `law.cdf(0)` */
  def cdf(x: Double): Double = {
    N.argument(x); N.check()
    math.min(1,atoms.filter(_._1<=x).map(_._2).sum+slab.map((d,w)=>w*d.cdf(x)).getOrElse(0.0))
  }
  /** @param lower exclusive lower bound @param upper inclusive upper bound
    * @return P(lower<X<=upper), with exact atom membership and guarded continuous mass
    * @example `law.intervalProbability(-1,0)` includes an atom at zero */
  def intervalProbability(lower: Double,upper: Double): Double = {
    require(!lower.isNaN && !upper.isNaN && lower<upper)
    val a=atoms.filter((x,w)=>x>lower && x<=upper).map(_._2).sum
    val c=slab.map((d,w)=>w*math.exp(ObservationLikelihood.logInterval(d,lower,upper))).getOrElse(0.0)
    val result=a+c
    if(result==0 && slab.exists((d,w)=>upper>d.support._1 && lower<d.support._2))
      throw new ArithmeticException("Mixed interval underflow")
    math.min(1,result)
  }
  /** @param rng caller-owned RNG @return mixed draw; a slab draw rounding onto an atom is refused
    * @example `law.sample(com.cra.figaro.util.SamplingRandom.scalaRandom(42))` */
  def sample(rng: scala.util.Random): Double = {
    require(rng!=null); N.check(); val u=N.open(rng)
    var cumulative=0.0; var i=0
    while(i<atoms.size) { cumulative+=atoms(i)._2; if(u<cumulative) return atoms(i)._1; i+=1 }
    slab match {
      case Some((d,_)) =>
        val x=d.sample(rng)
        if(probabilityAt(x)>0) throw new ArithmeticException("Continuous draw rounded onto a mixed atom")
        x
      case None => atoms.last._1
    }
  }
}

object MixedScalarDistribution {
  /** @param location finite atom @param probability atom mass in [0,1]
    * @param continuous continuous slab @return normalized spike-and-slab law
    * @example `spikeAndSlab(0,.2,GaussianDistribution(0,1))` */
  def spikeAndSlab(location: Double,probability: Double,continuous: ScalarDistribution): MixedScalarDistribution = {
    require(location.isFinite && continuous!=null); N.probability(probability)
    MixedScalarDistribution(if(probability>0) Vector(location->probability) else Vector.empty,
      if(probability<1) Some(continuous->(1-probability)) else None)
  }
  /** Distribution of max(lower,min(X,upper)); unlike truncation it retains boundary masses.
    * @param base continuous law @param lower lower clip, possibly -Infinity
    * @param upper larger upper clip, possibly +Infinity @return atom/slab decomposition
    * @example `clipped(GaussianDistribution(0,1),0,Double.PositiveInfinity)` is rectified normal
    */
  def clipped(base: ScalarDistribution,lower: Double,upper: Double): MixedScalarDistribution = {
    require(base!=null && !lower.isNaN && !upper.isNaN && lower<upper)
    val a=base.cdf(lower); val b=base.survival(upper)
    if(!a.isFinite || !b.isFinite || a<0 || b<0 || a+b>1) throw new ArithmeticException("Unresolved clipping masses")
    if((a==0 && lower>base.support._1) || (b==0 && upper<base.support._2))
      throw new ArithmeticException("Clipping atom underflow")
    val atoms=Vector(lower->a,upper->b).filter(_._2>0)
    val lo=math.max(lower,base.support._1); val hi=math.min(upper,base.support._2)
    val slab=if(lo>=hi) None else {
      val truncated=TruncatedDistribution(base,lo,hi)
      Some((truncated: ScalarDistribution)->truncated.retainedProbability)
    }
    MixedScalarDistribution(atoms,slab)
  }
}

/** Fixed mixed-measure adapter. Dynamic atom locations are deliberately not exposed:
  * likelihood comparison needs a common dominating measure across hypotheses.
  */
final class AtomicMixedScalar private[continuous](name: Name[Double],val distribution: MixedScalarDistribution,collection: ElementCollection)
  extends Element[Double](name,collection) with Atomic[Double] with HasLogDensity[Double] {
  type Randomness=Double
  def generateRandomness(): Double=distribution.sample(random)
  def generateValue(value: Double): Double=value
  def logDensity(value: Double): Double=distribution.logLikelihood(value)
}
object MixedScalarElement {
  /** @param distribution fixed mixed law @param name contextual name @param collection owning graph
    * @return observation-ready fixed element; not exact factor conversion or a continuous-vector target
    * @example `MixedScalarElement(MixedScalarDistribution.spikeAndSlab(0,.2,GaussianDistribution(0,1)))` */
  def apply(distribution: MixedScalarDistribution)(using name: Name[Double],collection: ElementCollection): AtomicMixedScalar = {
    require(distribution!=null); new AtomicMixedScalar(name,distribution,collection)
  }
}

/** Full mixed-law comparisons using the union of atoms plus Lebesgue measure. */
object MixedScalarInformation {
  private def compare(p: MixedScalarDistribution,q: MixedScalarDistribution,kl: Boolean): InformationMetricResult = {
    require(p!=null && q!=null); N.check()
    def infinite=InformationMetricResult(S.Infinite,Some(Double.PositiveInfinity),0,0,"mixed support")
    var atomic=0.0
    var index=0
    while(index<p.atoms.size) {
      val (x,w)=p.atoms(index)
      val v=q.probabilityAt(x)
      if(kl && v==0) return infinite
      atomic+= (if(kl) w*(math.log(w)-math.log(v)) else math.sqrt(w)*math.sqrt(v))
      index+=1
    }
    var result=atomic; var error=0.0; var work=0; var status=S.Analytic
    p.slab match {
      case Some((a,w)) => q.slab match {
        case None => if(kl) return infinite
        case Some((b,v)) =>
          val r=if(kl) ScalarDivergence.kl(a,b) else ScalarDivergence.bhattacharyya(a,b)
          if(r.value.isEmpty) return r.copy(method="mixed slab: "+r.method)
          if(kl && r.status==S.Infinite) return infinite
          val d=r.value.get
          if(kl) { result+=w*(math.log(w)-math.log(v)+d); error=w*r.errorEstimate }
          else {
            val affinity=math.sqrt(w)*math.sqrt(v)*math.exp(-d)
            if(d.isFinite && affinity==0) return InformationMetricResult(S.NumericallyUnresolved,None,Double.PositiveInfinity,r.evaluations,"mixed overlap underflow")
            result+=affinity; error=affinity*math.expm1(r.errorEstimate)
          }
          work=r.evaluations; if(r.status==S.Estimated) status=S.Estimated
      }
      case None => ()
    }
    if(!kl && result==0) return infinite
    val value=if(kl) result else -math.log(result)
    error=(if(kl) error else error/result)+256*math.ulp(1.0)*(1+math.abs(value))
    if(!value.isFinite || !error.isFinite || value < -error)
      InformationMetricResult(S.NumericallyUnresolved,None,Double.PositiveInfinity,work,"mixed decomposition")
    else InformationMetricResult(status,Some(math.max(0,value)),error,work,"mixed atom/slab decomposition")
  }
  /** @param p first mixed law @param q second mixed law @return directed KL in nats or explicit refusal
    * @example `kl(p,q)` includes atom support mismatch, not only slab KL */
  def kl(p: MixedScalarDistribution,q: MixedScalarDistribution): InformationMetricResult=compare(p,q,true)
  /** @param p first mixed law @param q second mixed law @return symmetric full-law overlap divergence
    * @example `bhattacharyya(p,q)` */
  def bhattacharyya(p: MixedScalarDistribution,q: MixedScalarDistribution): InformationMetricResult=compare(p,q,false)
}
