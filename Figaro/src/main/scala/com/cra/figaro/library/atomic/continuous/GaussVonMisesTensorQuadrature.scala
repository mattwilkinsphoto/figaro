package com.cra.figaro.library.atomic.continuous

import com.cra.figaro.util.CircularStatistics
import org.apache.commons.math3.analysis.integration.gauss.GaussIntegratorFactory

/** Positive-weight tensor reference, with streamed physical points and exponential callback cost.
  * Construct with the companion factory or kernel.tensorQuadrature().
  * @param gaussianOrder Gauss-Hermite order in each canonical linear coordinate
  * @param angularOrder Gauss-Legendre order on the concentration-scaled angular interval
  * @param nodeCount total callback count per successful expectation
  * @param angularMassEstimate integrated angular mass before normalization; must be within 1e-8 of one
  * @param angularTruncationBound bound on omitted angular probability, not total quadrature error
  */
final class GaussVonMisesTensorQuadrature private (
  private val kernel: GaussVonMisesDistribution,
  val gaussianOrder: Int, val angularOrder: Int, val nodeCount: Int,
  val angularMassEstimate: Double, val angularTruncationBound: Double,
  private val gaussianNodes: Vector[Double], private val gaussianWeights: Vector[Double],
  private val angularNodes: Vector[Double], private val angularWeights: Vector[Double]) {

  /** Approximate a scalar expectation using nonnegative normalized product weights.
    * @param f non-null deterministic callback with finite output at every point
    * @return compensated sum; not an error-certified integral; tiny products may underflow
    * @example `rule.expectation(p => math.exp(-p.linear(0)*p.linear(0)))`
    */
  def expectation(f: LinearAngular => Double): Double = {
    require(f != null,"callback must be non-null")
    expectationVector(1)(p => Vector(f(p))).head
  }

  /** Evaluate several expectations together, with one callback per tensor point.
    * @param outputDimension required output length in [1,10000]
    * @param f non-null deterministic callback returning a non-null finite vector of that length
    * @return immutable estimates; callback exceptions propagate and no partial result is returned
    * @example `rule.expectationVector(2)(p => Vector(p.linear(0), math.cos(p.angle)))`
    */
  def expectationVector(outputDimension: Int)(f: LinearAngular => Vector[Double]): Vector[Double] = {
    require(outputDimension >= 1 && outputDimension <= 10000,"outputDimension must be in [1,10000]")
    require(f != null,"callback must be non-null")
    val sums=Array.fill(outputDimension)(0.0); val corrections=Array.fill(outputDimension)(0.0)
    val linearCount=nodeCount/angularOrder
    for (index <- 0 until linearCount) {
      GaussVonMisesQuadrature.interrupted()
      var remainder=index; var weight=1.0
      val z=new Array[Double](kernel.dimension)
      var axis=kernel.dimension-1
      while (axis >= 0) {
        val digit=remainder % gaussianOrder; remainder /= gaussianOrder
        z(axis)=gaussianNodes(digit); weight *= gaussianWeights(digit); axis -= 1
      }
      // Map each linear point once, then vary only the independent angular residual.
      val base=kernel.fromCanonical(LinearAngular(z.toVector,0))
      for (j <- 0 until angularOrder) {
        GaussVonMisesQuadrature.interrupted()
        val point=LinearAngular(base.linear,CircularStatistics.normalize(base.angle+angularNodes(j)))
        val values=f(point)
        GaussVonMisesQuadrature.interrupted()
        require(values != null && values.size == outputDimension && values.forall(_.isFinite),
          "callback must return the requested finite vector")
        val productWeight=weight*angularWeights(j)
        for (v <- 0 until outputDimension) {
          val term=GaussVonMisesQuadrature.finite(productWeight*values(v))
          val total=GaussVonMisesQuadrature.finite(sums(v)+term)
          val adjustment=if(math.abs(sums(v)) >= math.abs(term)) (sums(v)-total)+term else (term-total)+sums(v)
          corrections(v)=GaussVonMisesQuadrature.finite(corrections(v)+adjustment)
          sums(v)=total
        }
      }
    }
    Vector.tabulate(outputDimension)(i => GaussVonMisesQuadrature.finite(sums(i)+corrections(i)))
  }
}

object GaussVonMisesTensorQuadrature {
  /** Build an adjustable-order positive-weight reference; does not automatically refine.
    * @param kernel non-null fixed GVM
    * @param gaussianOrder points per Gaussian coordinate in [1,32], default 5
    * @param angularOrder points on the angular interval in [2,256], default 64
    * @param maxNodes callback-count guard in [1,1000000], default 100000; must cover angularOrder*gaussianOrder^dimension
    * @return immutable streamed rule; rejects angular normalization error above 1e-8
    * @example `GaussVonMisesTensorQuadrature(kernel, 9, 64).expectation(p => math.cos(p.angle))`
    */
  def apply(kernel: GaussVonMisesDistribution, gaussianOrder: Int=5,
    angularOrder: Int=64, maxNodes: Int=100000): GaussVonMisesTensorQuadrature = {
    require(kernel != null,"kernel must be non-null")
    require(gaussianOrder >= 1 && gaussianOrder <= 32,"gaussianOrder must be in [1,32]")
    require(angularOrder >= 2 && angularOrder <= 256,"angularOrder must be in [2,256]")
    require(maxNodes >= 1 && maxNodes <= 1000000,"maxNodes must be in [1,1000000]")
    var count=angularOrder.toLong
    for (_ <- 0 until kernel.dimension) {
      GaussVonMisesQuadrature.interrupted()
      count *= gaussianOrder
      require(count <= maxNodes,"tensor quadrature exceeds maxNodes; reduce order/dimension or raise the guard")
    }
    val factory=new GaussIntegratorFactory
    val hermite=factory.hermite(gaussianOrder)
    GaussVonMisesQuadrature.interrupted()
    val gn=Vector.tabulate(gaussianOrder)(i => math.sqrt(2)*hermite.getPoint(i))
    val gw0=Vector.tabulate(gaussianOrder)(hermite.getWeight)
    val gw=gw0.map(_/gw0.sum)
    val scale=math.max(1.0,math.sqrt(kernel.kappa))
    val full=math.Pi*scale; val end=math.min(full,12.0)
    val legendre=factory.legendre(angularOrder,-end,end)
    GaussVonMisesQuadrature.interrupted()
    val circular=VonMisesDistribution(0,kernel.kappa)
    val an=Vector.tabulate(angularOrder)(i => legendre.getPoint(i)/scale)
    val raw=Vector.tabulate(angularOrder)(i => legendre.getWeight(i)/scale*circular.density(an(i)))
    val mass=raw.sum
    if (!mass.isFinite || math.abs(mass-1) > 1e-8)
      throw new ArithmeticException("Angular quadrature normalization failed; increase angularOrder")
    val rate=2/(math.Pi*math.Pi)
    val bound=if(end == full) 0.0 else
      math.exp(math.log(2)+circular.logDensity(0)-math.log(scale)-rate*end*end-math.log(2*rate*end))
    val aw=raw.map(_/mass)
    if (!gw.forall(w => w.isFinite && w > 0) || !aw.forall(w => w.isFinite && w >= 0))
      throw new ArithmeticException("Invalid tensor quadrature weights")
    new GaussVonMisesTensorQuadrature(kernel,gaussianOrder,angularOrder,count.toInt,mass,bound,gn,gw,an,aw)
  }
}
