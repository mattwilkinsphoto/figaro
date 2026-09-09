package com.cra.figaro.library.atomic.continuous

import com.cra.figaro.library.atomic.{DistributionNumerics as N}

private[continuous] object CovarianceNumerics {
  type Matrix = Vector[Vector[Double]]
  def adapter(rng: scala.util.Random): org.apache.commons.math3.random.RandomGenerator = {
    require(rng != null)
    new org.apache.commons.math3.random.AbstractRandomGenerator {
      private var calls = 0
      def setSeed(seed: Long): Unit = throw new UnsupportedOperationException("Caller owns seed")
      def nextDouble(): Double = {
        N.check(); calls += 1
        if (calls > 100000) throw new ArithmeticException("Covariance sampling RNG budget exceeded")
        N.open(rng)
      }
    }
  }
  def matrix(x: Matrix, d: Int): MultivariateGaussianDistribution = {
    N.check(); require(x != null && x.size == d && x.forall(r => r != null && r.size == d))
    MultivariateGaussianDistribution(Vector.fill(d)(0.0), x)
  }
  def gram(l: Matrix, correlation: Boolean = false): Matrix = {
    val d = l.size
    val out = Array.ofDim[Double](d,d)
    for (i <- 0 until d; j <- 0 to i) {
      N.check()
      val v = if (correlation && i == j) 1.0 else (0 to j).map(k => l(i)(k)*l(j)(k)).sum
      out(i)(j) = v; out(j)(i) = v
    }
    out.map(_.toVector).toVector
  }
  def lower(l: Matrix, d: Int, correlation: Boolean): Unit = {
    N.check(); require(l != null && l.size == d && l.forall(r => r != null && r.size == d && r.forall(_.isFinite)))
    require(l.indices.forall(i => l(i)(i)>0 && (i+1 until d).forall(j => l(i)(j)==0)))
    if (correlation) require(l.forall(r => math.abs(r.map(v => v*v).sum-1)<1e-12),"Correlation Cholesky rows must have unit norm")
    matrix(gram(l,correlation),d)
  }
  def validateDraw(l: Matrix, correlation: Boolean): Matrix = {
    try lower(l,l.size,correlation)
    catch { case e: IllegalArgumentException => throw new ArithmeticException("Covariance draw numerically unresolved: "+e.getMessage) }
    l
  }
}
