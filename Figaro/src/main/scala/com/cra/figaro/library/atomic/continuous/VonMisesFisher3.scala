package com.cra.figaro.library.atomic.continuous

import com.cra.figaro.language.*
import com.cra.figaro.library.atomic.{DistributionNumerics as N,InformationMetricResult,MetricCalculation as M}

private[continuous] object SphericalNumerics {
  def norm(v: Vector[Double]): Double=math.hypot(math.hypot(v(0),v(1)),v(2))
  def unit(v: Vector[Double]): Vector[Double] = {
    N.check(); require(v!=null && v.size==3 && v.forall(_.isFinite),"three finite coordinates required")
    val n=norm(v); require(math.abs(n-1)<=1e-12,"unit direction required within 1e-12")
    v.map(_/n)
  }
  def peak(k: Double): Double = {
    if(k==0) -math.log(4*math.Pi)
    else if(k<.01) {
      val t=k*k
      k-math.log(4*math.Pi)-math.log1p(t/6+t*t/120+t*t*t/5040+t*t*t*t/362880)
    } else math.log(k)-math.log(2*math.Pi)-math.log(-math.expm1(-2*k))
  }
  def a(k: Double): Double=if(k<.01) { val t=k*k; k*(1.0/3-t/45+2*t*t/945-t*t*t/4725) }
    else 1-1/k+2/math.expm1(2*k)
  def oneMinusA(k: Double): Double=if(k<.01) 1-a(k) else 1/k-2/math.expm1(2*k)
  def cross(a: Vector[Double],b: Vector[Double]): Vector[Double]=Vector(a(1)*b(2)-a(2)*b(1),a(2)*b(0)-a(0)*b(2),a(0)*b(1)-a(1)*b(0))
}

/** Spherical (S2) von Mises-Fisher; density is per unit surface area, NOT 3D Lebesgue density.
  * @param direction three finite unit coordinates within 1e-12, normalized for rounding only
  * @param concentration kappa in [0,1e6]; zero is uniform on the sphere
  */
final case class VonMisesFisher3Distribution(direction: Vector[Double],concentration: Double) {
  val meanDirection: Vector[Double]=SphericalNumerics.unit(direction)
  require(concentration.isFinite && concentration>=0 && concentration<=1e6)
  private[continuous] val peak: Double=SphericalNumerics.peak(concentration)
  /** @return expected vector A3(kappa)*meanDirection; its length is generally below one */
  def mean: Vector[Double]=meanDirection.map(_*SphericalNumerics.a(concentration))
  /** @param value unit vector within 1e-12 (rounding normalized); not an arbitrary spatial point
    * @return natural log surface-area density; finite even for tiny opposite-direction likelihood
    * @example `law.logDensity(Vector(0.0,0.0,1.0))`
    */
  def logDensity(value: Vector[Double]): Double = {
    val x=SphericalNumerics.unit(value)
    peak-concentration*.5*x.zip(meanDirection).map((a,b) => (a-b)*(a-b)).sum
  }
  /** @param value unit vector
    * @return surface-area density, possibly underflowing
    * @example `law.density(Vector(1.0,0.0,0.0))`
    */
  def density(value: Vector[Double]): Double=math.exp(logDensity(value))
  /** Stable inverse polar CDF plus uniform azimuth; no rejection sampler or shared RNG.
    * @param rng private caller-owned generator, not retained
    * @return unit vector on S2
    * @example `law.sample(com.cra.figaro.util.SamplingRandom.scalaRandom(42))`
    */
  def sample(rng: scala.util.Random): Vector[Double] = {
    val u=N.open(rng); val k=concentration
    val w=if(k==0) 2*u-1 else if(k<.5) -1+math.log1p(u*math.expm1(2*k))/k
      else 1+math.log(u+(1-u)*math.exp(-2*k))/k
    if(!w.isFinite || w< -1-1e-14 || w>1+1e-14) throw new ArithmeticException("Spherical polar draw unresolved")
    val z=math.max(-1.0,math.min(1.0,w)); val radius=math.sqrt((1-z)*(1+z))
    val phi=2*math.Pi*N.open(rng)
    val axis=meanDirection.indices.minBy(i => math.abs(meanDirection(i)))
    val raw=SphericalNumerics.cross(meanDirection,Vector.tabulate(3)(i => if(i==axis) 1.0 else 0.0))
    val e1=raw.map(_/SphericalNumerics.norm(raw)); val e2=SphericalNumerics.cross(meanDirection,e1)
    val x=Vector.tabulate(3)(i => z*meanDirection(i)+radius*(math.cos(phi)*e1(i)+math.sin(phi)*e2(i)))
    SphericalNumerics.unit(x)
  }
}

/** Fixed-law comparisons in the same spherical surface-area measure, in nats. */
object VonMisesFisher3Information {
  private def validate(p: VonMisesFisher3Distribution,q: VonMisesFisher3Distribution,t: Double): Unit = {
    N.check(); require(p!=null && q!=null && t.isFinite && t>0)
  }
  private def same(p: VonMisesFisher3Distribution,q: VonMisesFisher3Distribution): Boolean =
    p.concentration==q.concentration && (p.concentration==0 || p.meanDirection==q.meanDirection)
  /** @param p source spherical law
    * @param q comparison law using the same coordinate axes
    * @param tolerance positive absolute numerical target in nats
    * @return analytic directed KL or explicit numerical refusal
    * @example `VonMisesFisher3Information.kl(p,q)`
    */
  def kl(p: VonMisesFisher3Distribution,q: VonMisesFisher3Distribution,tolerance: Double=1e-8): InformationMetricResult = {
    validate(p,q,tolerance); if(same(p,q)) return M.identity
    val separation=.5*p.meanDirection.zip(q.meanDirection).map((a,b) => (a-b)*(a-b)).sum
    val a=SphericalNumerics.a(p.concentration)
    val v=p.peak-q.peak+SphericalNumerics.oneMinusA(p.concentration)*(q.concentration-p.concentration)+a*q.concentration*separation
    M.analytic(v,tolerance,math.abs(p.peak)+math.abs(q.peak)+p.concentration+q.concentration,"spherical exponential-family KL")
  }
  /** @param p first spherical law
    * @param q second spherical law in matching axes
    * @param tolerance positive absolute numerical target in nats
    * @return analytic negative log affinity, with uniform/opposite-direction limits
    * @example `VonMisesFisher3Information.bhattacharyya(p,q)`
    */
  def bhattacharyya(p: VonMisesFisher3Distribution,q: VonMisesFisher3Distribution,tolerance: Double=1e-8): InformationMetricResult = {
    validate(p,q,tolerance); if(same(p,q)) return M.identity
    val a=.5*p.concentration; val b=.5*q.concentration; val r=a+b
    val h=p.meanDirection.zip(q.meanDirection).map((x,y) => a*x+b*y)
    val k=SphericalNumerics.norm(h)
    val distance=p.meanDirection.zip(q.meanDirection).map((x,y) => (x-y)*(x-y)).sum
    val loss=if(r==0) 0.0 else a*b*distance/(r+k)
    val v=SphericalNumerics.peak(k)-.5*(p.peak+q.peak)+loss
    M.analytic(v,tolerance,r+math.abs(p.peak)+math.abs(q.peak),"spherical normalizer affinity")
  }
}

/** Prior-proposal atomic adapter; no Euclidean Continuous proposal is imposed on the sphere. */
final class AtomicVonMisesFisher3 private[continuous](name: Name[Vector[Double]],val distribution: VonMisesFisher3Distribution,collection: ElementCollection)
  extends Element[Vector[Double]](name,collection) with Atomic[Vector[Double]] with HasLogDensity[Vector[Double]] {
  type Randomness=Vector[Double]
  def generateRandomness(): Vector[Double]=distribution.sample(com.cra.figaro.util.random)
  def generateValue(value: Vector[Double]): Vector[Double]=value
  def logDensity(value: Vector[Double]): Double=distribution.logDensity(value)
}
object VonMisesFisher3 {
  /** @param distribution validated S2 kernel
    * @param name contextual name
    * @param collection owning collection
    * @return spherical observation-ready element
    * @example `VonMisesFisher3(VonMisesFisher3Distribution(Vector(0,0,1),10))`
    */
  def apply(distribution: VonMisesFisher3Distribution)(using name: Name[Vector[Double]],collection: ElementCollection): AtomicVonMisesFisher3 = {
    require(distribution!=null); new AtomicVonMisesFisher3(name,distribution,collection)
  }
  /** @param distribution element producing S2 kernels
    * @param name contextual name
    * @param collection owning collection
    * @return non-caching hierarchical spherical element
    * @example `VonMisesFisher3(kappa.map(k => VonMisesFisher3Distribution(Vector(0,0,1),k)))`
    */
  def apply(distribution: Element[VonMisesFisher3Distribution])(using name: Name[Vector[Double]],collection: ElementCollection): Element[Vector[Double]] = {
    require(distribution!=null); NonCachingChain(distribution,(d: VonMisesFisher3Distribution) => apply(d)(using "",collection))
  }
}
