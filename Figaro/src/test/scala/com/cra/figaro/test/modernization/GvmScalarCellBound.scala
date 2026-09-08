package com.cra.figaro.test.modernization

import java.util.concurrent.CancellationException

/** Test-only lower bound for the computed Double phase, not a physical-input certificate.
  * Basic operations expand one representable value outward. Math transcendental
  * results expand four ulps (Java 17 specifies at most one ulp for cos/exp).
  * The production integrator's separate preprocessing/error estimates remain necessary.
  */
private[modernization] object GvmScalarCellBound {
  private def down(x: Double): Double = Math.nextDown(x)
  private def up(x: Double): Double = Math.nextUp(x)
  private case class Interval(lo: Double,hi: Double) {
    def +(r: Interval): Interval = Interval(down(lo+r.lo),up(hi+r.hi))
    def *(r: Interval): Interval = {
      val xs=Vector(lo*r.lo,lo*r.hi,hi*r.lo,hi*r.hi)
      Interval(down(xs.min),up(xs.max))
    }
    def /(r: Interval): Interval = {
      require(r.lo > 0)
      this * Interval(down(1/r.hi),up(1/r.lo))
    }
    def sqrt: Interval = Interval(math.max(0,down(StrictMath.sqrt(math.max(0,lo)))),up(StrictMath.sqrt(hi)))
  }
  private def point(x: Double): Interval = Interval(x,x)

  /** Bound E[h(Z)] using 64 dyadic cells over [-4,4], or return zero on nonfinite arithmetic.
    * c,l,q are exact Double coefficients of c+l*z+q*z*z/2; kp,kq are in [0,50].
    * At most two 128-term Bessel upper bounds and 64 positive 32-term partial sums.
    * Cancellation is checked at entry, each cell and every Bessel term; failures propagate.
    */
  def lower(c: Double,l: Double,q: Double,kp: Double,kq: Double,cancelled: () => Boolean=() => false): Double = {
    require(Vector(c,l,q,kp,kq).forall(_.isFinite) && kp >= 0 && kq >= 0 && kp <= 50 && kq <= 50)
    require(cancelled != null)
    def check(): Unit = if(Thread.currentThread().isInterrupted || cancelled())
      throw new CancellationException("scalar cell-bound prepass interrupted")
    check()
    def i0(x: Double): Interval = {
      val y=point(x)*point(x)/point(4)
      var term=point(1); var total=point(1)
      for(j <- 1 to 128) {
        check()
        term=term*y/point(j.toDouble*j)
        total=total+term
      }
      // Remaining positive terms are bounded by a geometric series: subsequent
      // ratios decrease, and the largest ratio after term 129 is y/(130^2).
      val next=up(up(term.hi*y.hi)/(129.0*129))
      val ratio=up(y.hi/(130.0*130))
      val tail=up(next/down(1-ratio))
      Interval(total.lo,up(total.hi+tail))
    }
    val denominator=(i0(kp)*i0(kq)).sqrt.hi
    val a=point(kp)/point(2); val b=point(kq)/point(2)
    def i0Lower(x: Double): Double = {
      val y=math.max(0,down(down(x*x)/4))
      var term=1.0; var sum=1.0
      // Positive truncation is already a lower bound: no guessed remainder.
      // A shorter series can only make radius selection more conservative.
      for(j <- 1 to 32) {
        check()
        term=math.max(0,down(down(term*y)/(j.toDouble*j)))
        sum=math.max(0,down(sum+term))
      }
      sum
    }
    // pi's nearest Double plus one ulp is above mathematical pi.
    val gaussianDen=(point(2)*point(up(math.Pi))).sqrt.hi
    var total=0.0
    for(i <- 0 until 64) {
      check()
      val left= -4.0+i/8.0; val right=left+.125
      val z=Interval(left,right)
      val phase=(point(c)+point(l)*z+point(q)*z*z*point(.5))/point(2)
      val mid=phase.lo/2+phase.hi/2
      val width=math.max(up(mid-phase.lo),up(phase.hi-mid))
      val cosine=Math.cos(mid)
      // cos is 1-Lipschitz, so this remains valid across wraps and interior extrema.
      val cosLower=math.max(0,down(down(math.abs(cosine)-4*math.ulp(cosine))-width))
      val difference=Interval(down(a.lo-b.hi),up(a.hi-b.lo))
      // Squaring an interval crossing zero needs a zero lower endpoint.
      val squared=if(difference.lo <= 0 && difference.hi >= 0)
        Interval(0,up(math.max(difference.lo*difference.lo,difference.hi*difference.hi)))
        else difference*difference
      val r=(squared+point(4)*a*b*point(cosLower)*point(cosLower)).sqrt.lo
      val affinity=math.max(0,down(i0Lower(r)/denominator))
      val outer=math.max(math.abs(left),math.abs(right))
      // Minimum Gaussian density on the whole cell times its width is a lower
      // mass bound; no subtraction of nearby approximate erf values is needed.
      val exponent=down(-up(outer*outer)/2)
      val exponential=Math.exp(exponent)
      val expLower=math.max(0,down(exponential-4*math.ulp(exponential)))
      val mass=math.max(0,down(down(expLower/gaussianDen)*.125))
      total=math.max(0,down(total+math.max(0,down(mass*affinity))))
    }
    if(total.isFinite && total >= 0 && total <= 1) total else 0.0
  }
}
