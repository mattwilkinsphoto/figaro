package com.cra.figaro.library.atomic.continuous
import com.cra.figaro.library.atomic.DistributionNumerics as N

/** Caller-supplied continuous strictly monotone differentiable bijection.
  * Implementations must be immutable/pure and mutually consistent; finite probes cannot prove this contract.
  * Endpoint evaluations must supply the appropriate limits, including infinities.
  */
trait MonotoneTransform {
  def domain: (Double,Double)
  def increasing: Boolean
  def forward(x: Double): Double
  def inverse(y: Double): Double
  /** Natural log of the absolute inverse derivative, evaluated in output coordinates. */
  def logInverseJacobian(y: Double): Double
}
object MonotoneTransform {
  /** Positive X -> log(X); endpoint zero maps to negative infinity. */
  case object Log extends MonotoneTransform {
    val domain=(0.0,Double.PositiveInfinity); val increasing=true
    def forward(x: Double): Double=math.log(x)
    def inverse(y: Double): Double=math.exp(y)
    def logInverseJacobian(y: Double): Double=y
  }
}

/** Push-forward through an explicitly declared bijection, with inverse Jacobian.
  * @param base continuous scalar law whose support lies inside transform.domain
  * @param transform pure bijection, including endpoint limits; retained, never inferred
  * @example `MonotoneDistribution(LogNormalDistribution(0,1),MonotoneTransform.Log)`
  */
final case class MonotoneDistribution(base: ScalarDistribution,transform: MonotoneTransform) extends ScalarDistribution {
  require(base!=null && transform!=null)
  require(base.support._1>=transform.domain._1 && base.support._2<=transform.domain._2)
  private val left=transform.forward(base.support._1); private val right=transform.forward(base.support._2)
  require(!left.isNaN && !right.isNaN && (if(transform.increasing) left<right else right<left))
  val support: (Double,Double)=(math.min(left,right),math.max(left,right))
  def logDensity(y: Double): Double = {
    N.argument(y)
    if(y<support._1 || y>support._2 || y.isInfinity) Double.NegativeInfinity
    else {
      val x=transform.inverse(y); val jac=transform.logInverseJacobian(y)
      if(!x.isFinite || !jac.isFinite) throw new ArithmeticException("Transform inverse/Jacobian outside finite range")
      val v=base.logDensity(x)+jac
      if(v.isNaN) throw new ArithmeticException("Transform density unresolved")
      v
    }
  }
  def cdf(y: Double): Double = { N.argument(y); if(y<=support._1) 0 else if(y>=support._2) 1 else
    if(transform.increasing) base.cdf(transform.inverse(y)) else base.survival(transform.inverse(y)) }
  def survival(y: Double): Double = { N.argument(y); if(y<=support._1) 1 else if(y>=support._2) 0 else
    if(transform.increasing) base.survival(transform.inverse(y)) else base.cdf(transform.inverse(y)) }
  def quantile(p: Double): Double = {
    N.probability(p); if(p==0) support._1 else if(p==1) support._2
    else if(transform.increasing) N.interior(p,transform.forward(base.quantile(p))) else ConstructionMath.inverse(this,p)
  }
  override def sample(rng: scala.util.Random): Double = {
    N.check(); val y=transform.forward(base.sample(rng))
    if(!y.isFinite || !logDensity(y).isFinite) throw new ArithmeticException("Transformed draw outside finite-density support")
    y
  }
  def mean: Option[Double]=None
  def variance: Option[Double]=None
}

/** Y=abs(X), summing both inverse branches; NOT truncation or clipping.
  * @param base continuous scalar law; generic folded moments are not implemented
  * @example `FoldedDistribution(GaussianDistribution(0,1))`
  */
final case class FoldedDistribution(base: ScalarDistribution) extends ScalarDistribution {
  require(base!=null)
  val support: (Double,Double)=(if(base.support._1<=0 && base.support._2>=0) 0.0 else math.min(math.abs(base.support._1),math.abs(base.support._2)),
    math.max(math.abs(base.support._1),math.abs(base.support._2)))
  def logDensity(x: Double): Double = { N.argument(x); if(x<0 || x.isInfinity) Double.NegativeInfinity
    else ConstructionMath.logSum(Vector(base.logDensity(x),base.logDensity(-x))) }
  def cdf(x: Double): Double = {
    N.argument(x); if(x<=support._1) 0 else if(x>=support._2) 1 else {
      val a=base.cdf(x); val b=base.cdf(-x); val c=base.survival(-x); val d=base.survival(x)
      val (hi,lo)=if(a<=c) (a,b) else (c,d); val mass=hi-lo
      if(mass<=64*math.ulp(hi) || !mass.isFinite) throw new ArithmeticException("Folded interval probability unresolved")
      mass
    }
  }
  def survival(x: Double): Double = { N.argument(x); if(x<=support._1) 1 else if(x>=support._2) 0 else math.min(1,base.cdf(-x)+base.survival(x)) }
  def quantile(p: Double): Double = { N.probability(p); if(p==0) support._1 else if(p==1) support._2 else ConstructionMath.inverse(this,p) }
  override def sample(rng: scala.util.Random): Double = { val x=math.abs(base.sample(rng)); if(!logDensity(x).isFinite) throw new ArithmeticException("Folded draw outside finite-density support"); x }
  def mean: Option[Double]=None
  def variance: Option[Double]=None
}

/** Wrapped Cauchy represented on the fixed principal chart [-Pi,Pi]. Radians only.
  * @param location mean direction in [-Pi,Pi]; uniform rho=0 has no unique direction
  * @param rho mean resultant length in [0,.9999]; rho=1 point masses are excluded
  * @example `WrappedCauchyDistribution(0,.5)`
  */
final case class WrappedCauchyDistribution(location: Double,rho: Double) extends ScalarDistribution {
  require(location.isFinite && math.abs(location)<=math.Pi && rho.isFinite && rho>=0 && rho<=.9999)
  val support=(-math.Pi,math.Pi)
  private def wrap(x: Double): Double={ val r=math.IEEEremainder(x,2*math.Pi); if(r>=math.Pi) r-2*math.Pi else r }
  private def primitive(x: Double): Double = {
    val periods=math.floor((x+math.Pi)/(2*math.Pi)); val t=x-periods*2*math.Pi
    periods+.5+math.atan2((1+rho)*math.sin(t/2),(1-rho)*math.cos(t/2))/math.Pi
  }
  def logDensity(x: Double): Double = { N.argument(x); if(x < -math.Pi || x>math.Pi) Double.NegativeInfinity else
    math.log1p(-rho*rho)-math.log(2*math.Pi)-math.log((1-rho)*(1-rho)+4*rho*math.pow(math.sin((x-location)/2),2)) }
  /** @param angle any finite angle in radians
    * @return periodic log density after canonical wrapping; unlike chart-based logDensity
    * @example `law.angularLogDensity(3*math.Pi)` */
  def angularLogDensity(angle: Double): Double={ require(angle.isFinite); logDensity(wrap(angle)) }
  def cdf(x: Double): Double={ N.argument(x); if(x<= -math.Pi) 0 else if(x>=math.Pi) 1 else primitive(x-location)-primitive(-math.Pi-location) }
  def survival(x: Double): Double={ N.argument(x); if(x<= -math.Pi) 1 else if(x>=math.Pi) 0 else primitive(math.Pi-location)-primitive(x-location) }
  def quantile(p: Double): Double={ N.probability(p); if(p==0) -math.Pi else if(p==1) math.Pi else {
    require(p>=1e-12 && p<=1-1e-12,"Wrapped chart quantile tails below 1e-12 are unsupported")
    ConstructionMath.bisect(this,p,-math.Pi,math.Pi)
  } }
  override def sample(rng: scala.util.Random): Double=wrap(location+2*math.atan((1-rho)/(1+rho)*math.tan(math.Pi*(N.open(rng)-.5))))
  def mean: Option[Double]=None
  def variance: Option[Double]=None
  /** Circular mean direction; None for the uniform law. */
  def circularMean: Option[Double]=if(rho==0) None else Some(wrap(location))
  /** Circular variance, not variance of the principal-chart real coordinate. */
  def circularVariance: Double=1-rho
}
