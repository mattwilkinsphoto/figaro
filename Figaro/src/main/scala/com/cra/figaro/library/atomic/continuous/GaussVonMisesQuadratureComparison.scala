package com.cra.figaro.library.atomic.continuous

/** Four-corner estimates and observed order sensitivity, not an accuracy certificate.
  * All vectors have the requested output dimension (one for a scalar comparison).
  * @param baseline estimates at the two baseline orders
  * @param gaussianRefined estimates with only Gaussian order increased
  * @param angularRefined estimates with only angular order increased
  * @param jointRefined estimates with both orders increased; not necessarily the most accurate
  * @param maxGaussianChange maximum absolute Gaussian-order change across both angular orders
  * @param maxAngularChange maximum absolute angular-order change across both Gaussian orders
  * @param withinTolerance per-component agreement on all four edges of the order rectangle
  * @param evaluations total callback count across all four rules, including repeated points
  */
final class GaussVonMisesQuadratureComparisonResult private[continuous] (
  val baseline: Vector[Double], val gaussianRefined: Vector[Double],
  val angularRefined: Vector[Double], val jointRefined: Vector[Double],
  val maxGaussianChange: Vector[Double], val maxAngularChange: Vector[Double],
  val withinTolerance: Vector[Boolean], val evaluations: Int) {

  /** Whether every component passes all four pairwise tolerance checks.
    * @return true means observed agreement only; even exact agreement can hide large integration error
    * @example `println(result.ordersAgree)`
    */
  def ordersAgree: Boolean = withinTolerance.forall(identity)
}

/** Immutable, budgeted four-rule plan. Construct with the companion factory.
  * Calls are sequential; no callback results are cached across rules or comparisons.
  * @param baselineRule rule at the two baseline orders
  * @param gaussianRule rule with only Gaussian order increased
  * @param angularRule rule with only angular order increased
  * @param jointRule rule with both orders increased
  * @param evaluations total planned callbacks per successful scalar or vector comparison
  */
final class GaussVonMisesQuadratureComparison private (
  val baselineRule: GaussVonMisesTensorQuadrature,
  val gaussianRule: GaussVonMisesTensorQuadrature,
  val angularRule: GaussVonMisesTensorQuadrature,
  val jointRule: GaussVonMisesTensorQuadrature,
  val evaluations: Int) {

  /** Compare a scalar expectation at all four order combinations.
    * @param f non-null deterministic callback, finite at every evaluated point
    * @param absoluteTolerance finite nonnegative tolerance in output units; default 1e-6
    * @param relativeTolerance finite tolerance in [0,1]; default 1e-4; at least one tolerance must be positive
    * @return one-component estimates and diagnostics; agreement is not an error bound
    * @example `plan.compare(p => math.exp(-p.linear(0)*p.linear(0)))`
    */
  def compare(f: LinearAngular => Double, absoluteTolerance: Double=1e-6,
    relativeTolerance: Double=1e-4): GaussVonMisesQuadratureComparisonResult = {
    require(f != null,"callback must be non-null")
    compareVector(1,absoluteTolerance,relativeTolerance)(p => Vector(f(p)))
  }

  /** Compare several expectations with one callback per point per rule.
    * Each edge requires an absolute difference at most absoluteTolerance plus
    * relativeTolerance times the larger absolute estimate on that edge.
    * @param outputDimension required vector length in [1,10000]
    * @param absoluteTolerance finite nonnegative tolerance shared by every output; default 1e-6
    * @param relativeTolerance finite tolerance in [0,1]; default 1e-4; at least one tolerance must be positive
    * @param f non-null deterministic callback returning a non-null finite vector of the requested length
    * @return immutable estimates and diagnostics; callback/cancellation/arithmetic failures return no partial result
    * @example `plan.compareVector(2)(p => Vector(p.linear(0), math.cos(p.angle)))`
    */
  def compareVector(outputDimension: Int, absoluteTolerance: Double=1e-6,
    relativeTolerance: Double=1e-4)(f: LinearAngular => Vector[Double]): GaussVonMisesQuadratureComparisonResult = {
    GaussVonMisesQuadrature.interrupted()
    require(outputDimension >= 1 && outputDimension <= 10000,"outputDimension must be in [1,10000]")
    require(f != null,"callback must be non-null")
    require(absoluteTolerance.isFinite && absoluteTolerance >= 0,"absoluteTolerance must be finite and nonnegative")
    require(relativeTolerance.isFinite && relativeTolerance >= 0 && relativeTolerance <= 1,
      "relativeTolerance must be finite and in [0,1]")
    require(absoluteTolerance > 0 || relativeTolerance > 0,"at least one tolerance must be positive")
    val estimates=Vector(baselineRule,gaussianRule,angularRule,jointRule).map { rule =>
      GaussVonMisesQuadrature.interrupted()
      rule.expectationVector(outputDimension)(f)
    }
    val b=estimates(0); val g=estimates(1); val a=estimates(2); val j=estimates(3)
    val diagnostics=Vector.tabulate(outputDimension) { i =>
      GaussVonMisesQuadrature.interrupted()
      val edges=Vector((b(i),g(i)),(a(i),j(i)),(b(i),a(i)),(g(i),j(i)))
      val changes=edges.map((x,y) => GaussVonMisesQuadrature.finite(math.abs(x-y)))
      // Scale after subtracting the absolute allowance: do not overflow the tolerance sum.
      val agrees=edges.zip(changes).forall { case ((x,y),change) =>
        val scale=math.max(math.abs(x),math.abs(y))
        change <= absoluteTolerance || (scale > 0 && (change-absoluteTolerance)/scale <= relativeTolerance)
      }
      (math.max(changes(0),changes(1)),math.max(changes(2),changes(3)),agrees)
    }
    GaussVonMisesQuadrature.interrupted()
    new GaussVonMisesQuadratureComparisonResult(b,g,a,j,diagnostics.map(_._1),
      diagnostics.map(_._2),diagnostics.map(_._3),evaluations)
  }
}

object GaussVonMisesQuadratureComparison {
  /** Preflight the entire callback budget, then construct all four tensor rules before evaluation.
    * @param kernel non-null fixed GVM with n linear dimensions
    * @param gaussianOrder baseline Gaussian order in [1,31], default 5
    * @param refinedGaussianOrder strictly larger Gaussian order, at most 32; default 9
    * @param angularOrder baseline angular order in [2,255], default 64
    * @param refinedAngularOrder strictly larger angular order, at most 256; default 128
    * @param maxEvaluations total callback guard in [1,1000000], default 100000; must cover (G^n+Gref^n)*(A+Aref)
    * @return immutable comparison plan; any rule's failed angular mass check rejects the whole plan
    * @example `GaussVonMisesQuadratureComparison(kernel, 3, 7, 64, 128)`
    */
  def apply(kernel: GaussVonMisesDistribution, gaussianOrder: Int=5, refinedGaussianOrder: Int=9,
    angularOrder: Int=64, refinedAngularOrder: Int=128, maxEvaluations: Int=100000): GaussVonMisesQuadratureComparison = {
    GaussVonMisesQuadrature.interrupted()
    require(kernel != null,"kernel must be non-null")
    require(gaussianOrder >= 1 && refinedGaussianOrder > gaussianOrder && refinedGaussianOrder <= 32,
      "Gaussian orders must satisfy 1 <= gaussianOrder < refinedGaussianOrder <= 32")
    require(angularOrder >= 2 && refinedAngularOrder > angularOrder && refinedAngularOrder <= 256,
      "angular orders must satisfy 2 <= angularOrder < refinedAngularOrder <= 256")
    require(maxEvaluations >= 1 && maxEvaluations <= 1000000,"maxEvaluations must be in [1,1000000]")
    def count(order: Int): Long = {
      var total=(angularOrder+refinedAngularOrder).toLong
      for (_ <- 0 until kernel.dimension) {
        GaussVonMisesQuadrature.interrupted()
        total *= order // Previous iteration was bounded by 1e6, so multiplication cannot overflow.
        require(total <= maxEvaluations,"comparison exceeds maxEvaluations; reduce orders/dimension or raise the guard")
      }
      total
    }
    val total=count(gaussianOrder)+count(refinedGaussianOrder)
    require(total <= maxEvaluations,"comparison exceeds maxEvaluations; budget must cover all four rules")
    val b=kernel.tensorQuadrature(gaussianOrder,angularOrder,maxEvaluations)
    val g=kernel.tensorQuadrature(refinedGaussianOrder,angularOrder,maxEvaluations)
    val a=kernel.tensorQuadrature(gaussianOrder,refinedAngularOrder,maxEvaluations)
    val j=kernel.tensorQuadrature(refinedGaussianOrder,refinedAngularOrder,maxEvaluations)
    GaussVonMisesQuadrature.interrupted()
    new GaussVonMisesQuadratureComparison(b,g,a,j,total.toInt)
  }
}
