/* Independently implements Horwood-Poore (2014), section 5.1; not section 6's filter. */
package com.cra.figaro.library.atomic.continuous

import java.util.concurrent.CancellationException

/** Immutable third-order sparse GVM integration rule; weights may be negative.
  * Construct with GaussVonMisesQuadrature.thirdOrder or kernel.thirdOrderQuadrature().
  * @param nodes physical-coordinate points in fixed order: center, angular pair, linear pairs
  * @param weights corresponding signed integration weights, not probabilities or sample weights
  */
final class GaussVonMisesQuadrature private (
  val nodes: Vector[LinearAngular], val weights: Vector[Double]) {
  /** Number of callback invocations in each successful expectation call, including zero-weight nodes. */
  val nodeCount: Int = nodes.size
  /** Whether any integration weight is negative; negative results are not clipped. */
  val hasNegativeWeights: Boolean = weights.exists(_ < 0)
  /** Sum of absolute weights; indicates potential amplification of bounded callback errors. */
  val absoluteWeightSum: Double = weights.map(math.abs).sum

  /** Approximate a scalar expectation using the stored signed rule, without randomness.
    * @param f non-null deterministic callback; must return a finite value at every node
    * @return compensated weighted sum, not an error-certified estimate or probability
    * @example `rule.expectation(point => point.linear(0)*point.linear(0))`
    */
  def expectation(f: LinearAngular => Double): Double = {
    require(f != null,"callback must be non-null")
    expectationVector(1)(point => Vector(f(point))).head
  }

  /** Approximate several expectations in one traversal, evaluating the callback once per node.
    * @param outputDimension required output vector length in [1,10000]
    * @param f non-null deterministic callback returning a non-null finite vector of that length
    * @return immutable vector of compensated weighted sums; callback exceptions propagate
    * @example `rule.expectationVector(2)(p => Vector(p.linear(0), math.sin(p.angle)))`
    */
  def expectationVector(outputDimension: Int)(f: LinearAngular => Vector[Double]): Vector[Double] = {
    require(outputDimension >= 1 && outputDimension <= 10000,"outputDimension must be in [1,10000]")
    require(f != null,"callback must be non-null")
    val sums=Array.fill(outputDimension)(0.0); val corrections=Array.fill(outputDimension)(0.0)
    for (i <- nodes.indices) {
      GaussVonMisesQuadrature.interrupted()
      val values=f(nodes(i))
      GaussVonMisesQuadrature.interrupted()
      require(values != null && values.size == outputDimension && values.forall(_.isFinite),
        "callback must return the requested finite vector")
      for (j <- 0 until outputDimension) {
        val term=GaussVonMisesQuadrature.finite(weights(i)*values(j))
        val total=GaussVonMisesQuadrature.finite(sums(j)+term)
        val adjustment=if (math.abs(sums(j)) >= math.abs(term)) (sums(j)-total)+term else (term-total)+sums(j)
        corrections(j)=GaussVonMisesQuadrature.finite(corrections(j)+adjustment)
        sums(j)=total
      }
    }
    Vector.tabulate(outputDimension)(j => GaussVonMisesQuadrature.finite(sums(j)+corrections(j)))
  }
}

object GaussVonMisesQuadrature {
  private[continuous] def interrupted(): Unit =
    if (Thread.currentThread().isInterrupted) throw new CancellationException("GVM quadrature interrupted")
  private[continuous] def finite(value: Double): Double = {
    if (!value.isFinite) throw new ArithmeticException("GVM quadrature intermediate outside finite numeric range")
    value
  }

  // Return B1 = 1-I1/I0 and D = 4 B1-B2. D is O(kappa^-2): direct subtraction
  // loses precision at high concentration, so combine asymptotic coefficients first.
  private def angularDeficits(k: Double): (Double,Double) = {
    if (k <= 50) {
      val r=VonMisesDistribution(0,k).meanResultantLength
      val b1=1-r
      val b2=if (k < 1e-8) 1-k*k/8 else 2*r/k
      (b1,4*b1-b2)
    } else {
      var c0=1.0; var c1=1.0; var c2=1.0; var scale=1.0
      var s0=1.0; var b=0.0; var d=0.0; var j=1
      while (j < 1000) {
        interrupted()
        val odd=2.0*j-1
        c0 *= odd*odd/(8*j)
        c1 *= (odd*odd-4)/(8*j)
        c2 *= (odd*odd-16)/(8*j)
        scale /= k
        val t0=c0*scale; val tb=(c0-c1)*scale
        val td=if (j == 1) 0.0 else (3*c0-4*c1+c2)*scale
        s0 += t0; b += tb; d += td
        if (j >= 3 && math.abs(t0) <= math.abs(s0)*1e-16 &&
          math.abs(tb) <= math.abs(b)*1e-16 && math.abs(td) <= math.abs(d)*1e-16)
          return (b/s0,d/s0)
        j += 1
      }
      throw new ArithmeticException("GVM quadrature angular coefficients did not converge")
    }
  }

  /** Construct the paper's 2n+3-node third-order sparse rule for a fixed kernel.
    * @param kernel non-null immutable GVM, with n linear coordinates
    * @param maxNodes allocation/evaluation-count guard in [5,10001], default 1001; must cover 2n+3
    * @return immutable physical nodes and signed weights; no automatic error estimate or refinement
    * @example `GaussVonMisesQuadrature.thirdOrder(kernel).expectation(p => p.linear(0))`
    */
  def thirdOrder(kernel: GaussVonMisesDistribution, maxNodes: Int = 1001): GaussVonMisesQuadrature = {
    require(kernel != null,"kernel must be non-null")
    require(maxNodes >= 5 && maxNodes <= 10001,"maxNodes must be in [5,10001]")
    require(2L*kernel.dimension+3 <= maxNodes,"quadrature exceeds maxNodes")
    interrupted()
    val (b1,d)=angularDeficits(kernel.kappa)
    val halfSineSquared=d/(4*b1)
    val angularWeight=b1*b1/d
    require(b1 > 0 && d > 0 && halfSineSquared > 0 && halfSineSquared <= 1 && angularWeight.isFinite,
      "invalid quadrature angular coefficients")
    val offset=2*math.asin(math.sqrt(halfSineSquared))
    val zero=Vector.fill(kernel.dimension)(0.0)
    val canonical=Vector(LinearAngular(zero,0),LinearAngular(zero,offset),LinearAngular(zero,-offset)) ++
      Vector.tabulate(kernel.dimension)(i => Vector(
        LinearAngular(zero.updated(i,math.sqrt(3)),0),LinearAngular(zero.updated(i,-math.sqrt(3)),0))).flatten
    val weights=Vector(1-2*angularWeight-kernel.dimension/3.0,angularWeight,angularWeight) ++
      Vector.fill(2*kernel.dimension)(1.0/6)
    val physical=canonical.map { point => interrupted(); kernel.fromCanonical(point) }
    new GaussVonMisesQuadrature(physical,weights)
  }
}
