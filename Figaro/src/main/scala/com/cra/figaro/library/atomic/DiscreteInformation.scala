package com.cra.figaro.library.atomic

/** Finite probability-table information measures; category order is part of the contract.
  * Inputs must sum to one within 1e-12; only that rounding discrepancy is normalized.
  * This does not estimate a table from samples or extract a joint from scalar marginals.
  */
object DiscreteInformation {
  import InformationMetricStatus.*
  private def sum(values: IterableOnce[Double]): Double = {
    var total=0.0; var correction=0.0
    values.iterator.foreach { value =>
      DistributionNumerics.check()
      val add=value-correction; val next=total+add
      correction=(next-total)-add; total=next
    }
    total
  }
  private def probabilities(values: scala.collection.Seq[Double]): Vector[Double] = {
    require(values != null,"non-null probabilities required")
    val copy=values.iterator.take(100001).map { x => DistributionNumerics.check(); require(x.isFinite && x >= 0,"finite nonnegative probabilities required"); x }.toVector
    require(copy.nonEmpty && copy.size <= 100000,"table size must be in [1,100000]")
    val total=sum(copy)
    require(math.abs(total-1) <= 1e-12,"probabilities must sum to one within 1e-12")
    copy.map(_/total)
  }
  private def tolerance(t: Double): Unit = require(t.isFinite && t > 0,"positive finite tolerance required")
  private def result(value: Double,work: Int,tol: Double,magnitude: Double): InformationMetricResult = {
    val error=128*math.ulp(1.0)*(1+work)*(1+magnitude)
    if(!value.isFinite || value < -error || error > tol) MetricCalculation.unavailable(NumericallyUnresolved,work,"finite-table")
    else InformationMetricResult(Estimated,Some(math.max(0,value)),error,work,"finite-table")
  }
  /** @param p probabilities in explicit category order, at most 100000 entries
    * @param q matching category probabilities (include zero entries for absent categories)
    * @param tolerance positive finite absolute roundoff allowance target in nats, default 1e-8
    * @return finite-table directed KL, or Infinite for positive P mass where Q is zero
    * @example `DiscreteInformation.kl(Vector(.4,.6),Vector(.5,.5))`
    */
  def kl(p: scala.collection.Seq[Double],q: scala.collection.Seq[Double],tolerance: Double=1e-8): InformationMetricResult = {
    DistributionNumerics.check(); this.tolerance(tolerance)
    val a=probabilities(p); val b=probabilities(q); require(a.size == b.size,"matching categories required")
    if(a == b) return MetricCalculation.identity
    if(a.indices.exists(i => a(i) > 0 && b(i) == 0)) return MetricCalculation.infinite
    val terms=a.indices.map { i => DistributionNumerics.check(); if(a(i) == 0) 0.0 else a(i)*(math.log(a(i))-math.log(b(i))) }
    result(sum(terms),a.size,tolerance,sum(terms.map(math.abs)))
  }
  /** @param p first finite probability table
    * @param q second table in matching category order
    * @param tolerance positive finite roundoff target in nats, default 1e-8
    * @return symmetric negative log affinity; Infinite only for disjoint support
    * @example `DiscreteInformation.bhattacharyya(Vector(.4,.6),Vector(.5,.5))`
    */
  def bhattacharyya(p: scala.collection.Seq[Double],q: scala.collection.Seq[Double],tolerance: Double=1e-8): InformationMetricResult = {
    DistributionNumerics.check(); this.tolerance(tolerance)
    val a=probabilities(p); val b=probabilities(q); require(a.size == b.size,"matching categories required")
    if(a == b) return MetricCalculation.identity
    val logs=a.indices.filter(i => a(i) > 0 && b(i) > 0).map { i => DistributionNumerics.check(); .5*(math.log(a(i))+math.log(b(i))) }
    if(logs.isEmpty) return MetricCalculation.infinite
    val maximum=logs.max; val distance= -maximum-math.log(sum(logs.map(x => math.exp(x-maximum))))
    result(distance,a.size,tolerance,math.abs(distance))
  }
  /** MI between the row and column variables of an explicit finite JOINT table.
    * @param joint nonempty rectangular table, at most 100000 total cells, summing to one
    * @param tolerance positive finite absolute roundoff target in nats, default 1e-8
    * @return I(row;column), not a distance between two distributions; zero cells are safe
    * @example `DiscreteInformation.mutualInformation(Vector(Vector(.5,0.0),Vector(0.0,.5)))`
    */
  def mutualInformation(joint: scala.collection.Seq[scala.collection.Seq[Double]],tolerance: Double=1e-8): InformationMetricResult = {
    DistributionNumerics.check(); this.tolerance(tolerance); require(joint != null,"non-null joint table required")
    val rows=scala.collection.mutable.ArrayBuffer.empty[Vector[Double]]; var cells=0; var width=0
    val iterator=joint.iterator
    while(iterator.hasNext) {
      DistributionNumerics.check(); val row=iterator.next(); require(row != null,"non-null row required")
      val copy=row.iterator.take(100001-cells).toVector
      cells += copy.size; require(cells <= 100000 && copy.nonEmpty,"nonempty joint with at most 100000 cells required")
      if(rows.isEmpty) width=copy.size else require(copy.size == width,"rectangular table required")
      rows += copy
    }
    require(rows.nonEmpty,"nonempty joint table required")
    val flat=probabilities(rows.flatten.toVector); val matrix=flat.grouped(width).toVector
    val rowMass=matrix.map(row => sum(row)); val colMass=Vector.tabulate(width)(j => sum(matrix.iterator.map(_(j))))
    var total=0.0; var correction=0.0; var absolute=0.0
    for(i <- matrix.indices;j <- 0 until width) {
      DistributionNumerics.check(); val p=matrix(i)(j)
      if(p > 0) {
        val term=p*(math.log(p)-math.log(rowMass(i))-math.log(colMass(j)))
        val add=term-correction; val next=total+add; correction=(next-total)-add; total=next
        absolute += math.abs(term)
      }
    }
    result(total,cells,tolerance,absolute)
  }
}
