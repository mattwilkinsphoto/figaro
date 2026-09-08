package com.cra.figaro.test.modernization

import com.cra.figaro.library.atomic.continuous.GaussVonMisesDistribution
import java.util.concurrent.CancellationException
import org.apache.commons.math3.special.Erf
import scala.collection.mutable

/** Test-only end-to-end prototype. Not packaged in the library or a public fallback API. */
private[modernization] object GvmPositiveScalarPrototype {
  enum Status { case Estimated, BudgetExhausted, NumericallyUnresolved, UnsupportedRange }
  import Status.*
  final case class Result(status: Status, distance: Option[Double], interval: Option[(Double,Double)],
    evaluations: Int, radius: Double, gaussianTailBound: Double, quadratureErrorEstimate: Double,
    roundoffEstimate: Double, preprocessingErrorEstimate: Double)
  private case class Phase(gaussian: Double,c: Double,l: Double,q: Double,
    cMagnitude: Double,lMagnitude: Double,qMagnitude: Double,contrast: Double)
  private case class Panel(left: Double,right: Double,values: Vector[Double],value: Double,error: Double,serial: Long)

  def compare(p: GaussVonMisesDistribution,q: GaussVonMisesDistribution,
    tolerance: Double=1e-8,maxEvaluations: Int=50000,cancelled: () => Boolean=() => false): Result = {
    require(p != null && q != null && p.dimension == q.dimension,"non-null matching dimensions required")
    require(tolerance.isFinite && tolerance > 0,"positive finite tolerance required")
    require(maxEvaluations >= 5 && maxEvaluations <= 200000,"evaluation budget must be in [5,200000]")
    require(cancelled != null,"non-null cancellation predicate required")
    def interrupted(): Unit =
      if (Thread.currentThread().isInterrupted || cancelled()) throw new CancellationException("scalar GVM prototype interrupted")
    def finite(x: Double): Double = {
      if (!x.isFinite) throw new ArithmeticException("nonfinite scalar prototype intermediate")
      x
    }
    def unavailable(status: Status,evals: Int=0,radius: Double=0,tail: Double=Double.PositiveInfinity) =
      Result(status,None,None,evals,radius,tail,Double.PositiveInfinity,Double.PositiveInfinity,Double.PositiveInfinity)
    interrupted()
    if (p.dimension != 1 || math.max(p.kappa,q.kappa) > 50) return unavailable(UnsupportedRange)
    var evaluations=0
    var radius=0.0
    var tail=Double.PositiveInfinity
    try {
      // Common unit scale; no physical variance products or generic matrix inverse.
      val scale=math.sqrt(math.max(p.covariance(0)(0),q.covariance(0)(0)))
      val vp=finite(p.covariance(0)(0)/scale/scale)
      val vq=finite(q.covariance(0)(0)/scale/scale)
      val contrast=math.max(vp,vq)/math.min(vp,vq)
      if (math.min(vp,vq) <= 0 || contrast > 1e8) return unavailable(NumericallyUnresolved)
      val d=finite((q.mean(0)-p.mean(0))/scale)
      val sum=vp+vq
      val rawGaussian=finite(d*d/(4*sum)+(math.log(sum/2)-.5*(math.log(vp)+math.log(vq)))/2)
      val gaussianAllowance=64*math.ulp(1.0)*contrast*(1+math.abs(rawGaussian))
      if (rawGaussian < -gaussianAllowance) return unavailable(NumericallyUnresolved)
      val gaussian=math.max(0,rawGaussian)
      // Stable offsets in each kernel's canonical coordinate: avoid subtracting
      // two almost equal bridge/original means when the variances differ greatly.
      val dp=finite(math.sqrt(vp)*d/sum); val dq=finite(-math.sqrt(vq)*d/sum)
      val tp=math.sqrt(2*vq/sum); val tq=math.sqrt(2*vp/sum)
      def components(k: GaussVonMisesDistribution,offset: Double,transform: Double) = {
        interrupted()
        val b=k.beta(0); val g=k.gamma(0)(0)
        val c=finite(k.alpha+b*offset+.5*g*offset*offset)
        val l=finite(transform*(b+g*offset)); val h=finite(g*transform*transform)
        val cm=finite(math.abs(k.alpha)+math.abs(b*offset)+math.abs(.5*g*offset*offset))
        val lm=finite(transform*(math.abs(b)+math.abs(g*offset)))
        (c,l,h,cm,lm,math.abs(h))
      }
      val cp=components(p,dp,tp); val cq=components(q,dq,tq)
      val phase=Phase(gaussian,finite(cp._1-cq._1),finite(cp._2-cq._2),finite(cp._3-cq._3),
        finite(cp._4+cq._4),finite(cp._5+cq._5),finite(cp._6+cq._6),contrast)
      if (Vector(phase.c,phase.l,phase.q).exists(x => math.abs(x) > 1e4) || gaussian > 1e4)
        return unavailable(UnsupportedRange)
      def logI0(x: Double): Double = {
        var term=1.0; var total=1.0; var j=1
        while (j <= 1000) {
          interrupted()
          term *= (x/2)*(x/2)/(j.toDouble*j)
          total += term
          if (term <= total*1e-17) return math.log(total)
          j += 1
        }
        throw new ArithmeticException("Bessel work guard exhausted")
      }
      val a=p.kappa/2; val b=q.kappa/2
      val logDen=(logI0(p.kappa)+logI0(q.kappa))/2
      val minimum=math.exp(logI0(math.abs(a-b))-logDen)
      radius=4
      def gaussianTail = Erf.erfc(radius/math.sqrt(2))
      while (gaussianTail > tolerance*minimum/16 && radius < 16) { interrupted(); radius += 1 }
      tail=gaussianTail
      if (tail > tolerance*minimum/16) return unavailable(NumericallyUnresolved,0,radius,tail)
      // Heuristic operand-sensitive phase allowance over the entire truncated domain.
      // |d log(h)/d delta| <= min(a,b); this factor converts phase error to log-affinity error.
      // The coefficient-error allowance itself is NOT a directed-rounding proof.
      val phaseAllowance=finite(64*math.ulp(1.0)*contrast*(1+phase.cMagnitude+
        radius*phase.lMagnitude+.5*radius*radius*phase.qMagnitude))
      val preprocessing=finite(gaussianAllowance+math.min(a,b)*phaseAllowance)
      def at(z: Double): Double = finite(phase.c+phase.l*z+.5*phase.q*z*z)
      def span(left: Double,right: Double): Double = {
        val endpoints=Vector(at(left),at(right))
        val vertex=if(phase.q == 0) Double.PositiveInfinity else -phase.l/phase.q
        val values=if(vertex > left && vertex < right) endpoints :+ at(vertex) else endpoints
        values.max-values.min
      }
      val phaseStep=math.min(.5,1/math.sqrt(math.max(1,math.max(p.kappa,q.kappa))))
      val pending=mutable.ArrayBuffer((-radius,radius)); val intervals=mutable.ArrayBuffer.empty[(Double,Double)]
      while (pending.nonEmpty) {
        interrupted()
        val (left,right)=pending.remove(pending.size-1)
        if (right-left <= 1 && span(left,right) <= phaseStep) intervals += ((left,right))
        else {
          val mid=(left+right)/2
          if (mid == left || mid == right) return unavailable(NumericallyUnresolved,0,radius,tail)
          pending += ((mid,right)); pending += ((left,mid))
        }
        if (5L*(pending.size+intervals.size) > maxEvaluations) return unavailable(BudgetExhausted,0,radius,tail)
      }
      def integrand(z: Double): Double = {
        interrupted()
        require(evaluations < maxEvaluations,"internal evaluation budget overrun")
        evaluations += 1
        val r=math.min(a+b,math.hypot(a-b,2*math.sqrt(a*b)*math.cos(at(z)/2)))
        finite(math.exp(-z*z/2+logI0(r)-logDen)/math.sqrt(2*math.Pi))
      }
      var serial=0L
      def panel(left: Double,right: Double,values: Vector[Double]): Panel = {
        val width=right-left
        val coarse=width*(values(0)+4*values(2)+values(4))/6
        val fine=width*(values(0)+4*values(1)+2*values(2)+4*values(3)+values(4))/12
        serial += 1
        Panel(left,right,values,fine,math.abs(fine-coarse),serial)
      }
      given Ordering[Panel] = Ordering.by(p => (p.error,-p.serial))
      val heap=mutable.PriorityQueue.empty[Panel]
      intervals.foreach { (left,right) =>
        heap.enqueue(panel(left,right,Vector.tabulate(5)(i => integrand(left+(right-left)*i/4))))
      }
      def compensated(values: Iterator[Double]): Double = {
        var total=0.0; var correction=0.0
        values.foreach { value =>
          interrupted()
          val updated=total+value
          correction += (if(math.abs(total) >= math.abs(value)) (total-updated)+value else (value-updated)+total)
          total=updated
        }
        finite(total+correction)
      }
      while (true) {
        interrupted()
        val value=compensated(heap.iterator.map(_.value)); val error=compensated(heap.iterator.map(_.error))
        val rounding=finite(64*math.ulp(1.0)*(evaluations+1)*(1+math.abs(phase.c)+math.abs(phase.l)+math.abs(phase.q))*value)
        val allowance=error+rounding
        val low=math.max(0,value-allowance); val high=math.min(1,value+allowance+tail)
        val logAllowance=preprocessing+64*math.ulp(1.0)*(1+gaussian)
        val interval=if(high > 0 && low <= high) Some((math.max(0,gaussian-math.log(high)-logAllowance),
          if(low > 0) gaussian-math.log(low)+logAllowance else Double.PositiveInfinity)) else None
        val estimate=if(value > 0 && value <= 1+allowance) Some(math.max(0,gaussian-math.log(math.min(1,value)))) else None
        val ok=estimate.exists(d => interval.exists((lo,hi) => math.max(d-lo,hi-d) <= tolerance))
        val floorDominates=error <= rounding || logAllowance >= tolerance
        val status=if(ok) Estimated else if(floorDominates) NumericallyUnresolved else BudgetExhausted
        val result=Result(status,if(ok) estimate else None,interval,evaluations,radius,tail,error,rounding,preprocessing)
        if(ok || floorDominates || evaluations+4 > maxEvaluations) return result
        val parent=heap.dequeue(); val mid=(parent.left+parent.right)/2
        if(mid == parent.left || mid == parent.right) return unavailable(NumericallyUnresolved,evaluations,radius,tail)
        for ((left,right,old) <- Vector((parent.left,mid,parent.values.take(3)),(mid,parent.right,parent.values.drop(2)))) {
          val values=Vector(old(0),integrand(left+(right-left)/4),old(1),integrand(left+3*(right-left)/4),old(2))
          heap.enqueue(panel(left,right,values))
        }
      }
      throw new AssertionError("unreachable")
    } catch {
      case _: ArithmeticException => unavailable(NumericallyUnresolved,evaluations,radius,tail)
    }
  }
}
