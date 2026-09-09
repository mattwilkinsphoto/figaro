package com.cra.figaro.library.atomic

import java.util.concurrent.CancellationException

private[atomic] object DistributionNumerics {
  // DLMF 5.5.2 recurrence and 5.11.2 asymptotic expansion. Shift to x>=16;
  // the first omitted Bernoulli term is <1.2e-19 there (positive real axis).
  // This avoids Commons Math 3's several-e-9 digamma error at small integers.
  def digammaPositive(value: Double): Double = {
    check(); require(value.isFinite && value>0)
    var x=value; var correction=0.0
    while(x<16) { correction-=1/x; x+=1 }
    val z=1/x/x
    correction+math.log(x)-.5/x-z*(1.0/12-z*(1.0/120-z*(1.0/252-z*(1.0/240-z*(1.0/132-z*(691.0/32760))))))
  }
  def check(): Unit = if(Thread.currentThread().isInterrupted) throw new CancellationException("distribution operation interrupted")
  def argument(x: Double): Unit = { check(); require(!x.isNaN,"NaN argument") }
  def probability(p: Double): Unit = { check(); require(p.isFinite && p >= 0 && p <= 1,"probability must be in [0,1]") }
  def shape(x: Double): Unit = require(x.isFinite && x >= .001 && x <= 1e6,"shape must be in [0.001,1e6]")
  def scale(x: Double): Unit = require(x.isFinite && x >= 1e-100 && x <= 1e100,"scale must be in [1e-100,1e100]")
  def location(x: Double): Unit = require(x.isFinite && math.abs(x) <= 1e100,"location must be finite with absolute value <=1e100")
  def open(rng: scala.util.Random): Double = {
    require(rng != null,"non-null caller RNG required")
    var attempts=0
    while(attempts < 1024) { check(); val u=rng.nextDouble(); if(u > 0 && u < 1) return u; attempts += 1 }
    throw new ArithmeticException("RNG did not produce an open-unit draw in 1024 attempts")
  }
  def interior(p: Double,x: Double): Double = {
    if(x.isNaN || (p > 0 && p < 1 && !x.isFinite)) throw new ArithmeticException("quantile outside representable numeric range")
    x
  }
  def logSquarePlusOne(z: Double): Double = {
    val a=math.abs(z)
    if(a > 1) 2*math.log(a)+math.log1p(1/a/a) else math.log1p(a*a)
  }
  def log1pExp(x: Double): Double = if(x > 0) x+math.log1p(math.exp(-x)) else math.log1p(math.exp(x))
  def logAbsDifference(x: Double,y: Double): Double = {
    val d=math.abs(x-y)
    if(d.isFinite) math.log(d) else { val a=math.max(math.abs(x),math.abs(y)); math.log(a)+math.log1p(math.min(math.abs(x),math.abs(y))/a) }
  }
  def log1mexp(x: Double): Double = if(x < -math.log(2)) math.log1p(-math.exp(x)) else math.log(-math.expm1(x))
  def logExpm1(x: Double): Double = if(x > 50) x+math.log1p(-math.exp(-x)) else math.log(math.expm1(x))
  // Commons Math's default continued fraction has an effectively unbounded iteration
  // limit. Bound each call and keep convergence failure distinct from a zero tail.
  def regularizedBeta(x: Double,a: Double,b: Double): Double = {
    check()
    val result=try org.apache.commons.math3.special.Beta.regularizedBeta(x,a,b,1e-14,10000)
      catch { case cause: org.apache.commons.math3.exception.MathIllegalStateException =>
        val failure=new ArithmeticException("incomplete beta did not resolve within 10000 iterations")
        failure.initCause(cause)
        throw failure
      }
    check()
    if(!result.isFinite || result < 0 || result > 1) throw new ArithmeticException("incomplete beta returned an unresolved probability")
    result
  }
}
