package com.cra.figaro.library.atomic.continuous

import java.util.concurrent.CancellationException
import org.apache.commons.math3.special.Erf
import scala.collection.mutable

/** Opt-in positive scalar integration; independent of the Fourier comparison API.
  * All numerical intervals are estimates, not certified bounds. No random sampling is used.
  */
object GaussVonMisesScalarBhattacharyya {
  /** Estimated exposes a distance; all other outcomes require caller attention. */
  enum Status { case Estimated, BudgetExhausted, NumericallyUnresolved, UnsupportedRange }
  import Status.*
  /** Immutable numerical diagnostics. Affinity errors and distance errors have different units.
    * @param status numerical outcome; Estimated is not an accuracy certificate
    * @param distance symmetric Bhattacharyya distance in nats, only when estimates meet tolerance
    * @param interval estimated distance interval in nats; upper endpoint can be infinite
    * @param evaluations positive integrand evaluations, excluding bounded tail-selection setup; zero for shortcuts or preflight refusal
    * @param radius truncated standard-normal radius; zero for analytic shortcuts
    * @param gaussianTailBound omitted Gaussian mass, in angular-affinity units; infinity if unavailable
    * @param quadratureErrorEstimate summed Simpson differences, in angular-affinity units; heuristic
    * @param roundoffEstimate integration roundoff allowance, in angular-affinity units; heuristic
    * @param preprocessingErrorEstimate parameter transformation allowance, in distance nats; heuristic
    * @param method identity, gaussian, one-uniform, constant-angular, positive-integration, or unavailable
    */
  final case class Result(status: Status, distance: Option[Double], interval: Option[(Double,Double)],
    evaluations: Int, radius: Double, gaussianTailBound: Double, quadratureErrorEstimate: Double,
    roundoffEstimate: Double, preprocessingErrorEstimate: Double, method: String="positive-integration")
  private case class Phase(gaussian: Double,c: Double,l: Double,q: Double,
    cMagnitude: Double,lMagnitude: Double,qMagnitude: Double,contrast: Double)
  private case class Panel(left: Double,right: Double,values: Vector[Double],value: Double,error: Double,serial: Long)
  private class NumericalFailure(message: String) extends RuntimeException(message)

  /** Compare fixed scalar GVM laws using analytic reductions or positive adaptive integration.
    * Invalid arguments throw IllegalArgumentException; interruption/cancellation throws
    * CancellationException without clearing the thread interrupt flag. Per-call work is isolated.
    * @param p non-null fixed GVM with one linear coordinate and concentration at most 50
    * @param q non-null GVM in the same physical coordinates and units, with matching dimension
    * @param tolerance positive finite absolute distance tolerance in nats; default 1e-8
    * @param maxEvaluations integrand work budget in [5,200000], default 50000; excludes optional 64-cell/2304-term tail setup; not a time limit
    * @param cancelled cooperative cancellation predicate, default always false; must be non-null
    * @return immutable estimated/unavailable diagnostics; never a distance on numerical failure
    * @example `GaussVonMisesScalarBhattacharyya.compare(p, q, tolerance = 1e-7)`
    */
  def compare(p: GaussVonMisesDistribution,q: GaussVonMisesDistribution,
    tolerance: Double=1e-8,maxEvaluations: Int=50000,cancelled: () => Boolean=() => false): Result = {
    require(p != null && q != null && p.dimension == q.dimension,"non-null matching dimensions required")
    require(tolerance.isFinite && tolerance > 0,"positive finite tolerance required")
    require(maxEvaluations >= 5 && maxEvaluations <= 200000,"evaluation budget must be in [5,200000]")
    require(cancelled != null,"non-null cancellation predicate required")
    def interrupted(): Unit =
      if (Thread.currentThread().isInterrupted || cancelled()) throw new CancellationException("scalar GVM comparison interrupted")
    def finite(x: Double): Double = {
      if (!x.isFinite) throw new NumericalFailure("nonfinite scalar comparison intermediate")
      x
    }
    def unavailable(status: Status,evals: Int=0,radius: Double=0,tail: Double=Double.PositiveInfinity) =
      Result(status,None,None,evals,radius,tail,Double.PositiveInfinity,Double.PositiveInfinity,Double.PositiveInfinity,"unavailable")
    interrupted()
    if (p.dimension != 1 || math.max(p.kappa,q.kappa) > 50) return unavailable(UnsupportedRange)
    if ((p eq q) || (p.mean == q.mean && p.covariance == q.covariance && p.alpha == q.alpha &&
      p.beta == q.beta && p.gamma == q.gamma && p.kappa == q.kappa))
      return Result(Estimated,Some(0),Some((0,0)),0,0,0,0,0,0,"identity")
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
      val rawGaussian=finite(d*d/(4*sum)+math.log1p((vp-vq)*(vp-vq)/(4*vp*vq))/4)
      val gaussianAllowance=64*math.ulp(1.0)*contrast*(1+math.abs(rawGaussian))
      if (rawGaussian < -gaussianAllowance) return unavailable(NumericallyUnresolved)
      val gaussian=math.max(0,rawGaussian)
      if (gaussian > 1e4) return unavailable(UnsupportedRange)
      def analytic(logAffinity: Double,error: Double,method: String): Result = {
        val distance=finite(gaussian-logAffinity)
        val allowance=finite(error+64*math.ulp(1.0)*(1+math.abs(distance)+p.kappa+q.kappa))
        val interval=(math.max(0,distance-allowance),distance+allowance)
        val ok=distance >= 0 && math.max(distance-interval._1,interval._2-distance) <= tolerance
        Result(if(ok) Estimated else NumericallyUnresolved,if(ok) Some(distance) else None,
          Some(interval),0,0,0,0,0,allowance,method)
      }
      def logI0(x: Double): Double = {
        var term=1.0; var total=1.0; var j=1
        while (j <= 1000) {
          interrupted()
          term *= (x/2)*(x/2)/(j.toDouble*j)
          total += term
          if (term <= total*1e-17) return math.log(total)
          j += 1
        }
        throw new NumericalFailure("Bessel work guard exhausted")
      }
      if (p.kappa == 0 && q.kappa == 0) return analytic(0,gaussianAllowance,"gaussian")
      val a=p.kappa/2; val b=q.kappa/2
      val logDen=(logI0(p.kappa)+logI0(q.kappa))/2
      if (p.kappa == 0 || q.kappa == 0)
        return analytic(logI0(a+b)-logDen,gaussianAllowance,"one-uniform")
      // Only exact structural reductions: never round small nonzero coupling to zero.
      val constant=(p.beta(0) == 0 && q.beta(0) == 0 && p.gamma(0)(0) == 0 && q.gamma(0)(0) == 0) ||
        (p.mean == q.mean && p.covariance == q.covariance && p.beta == q.beta && p.gamma == q.gamma)
      if (constant) {
        val delta=finite(p.alpha-q.alpha)
        val r=math.min(a+b,math.hypot(a-b,2*math.sqrt(a*b)*math.cos(delta/2)))
        val phaseError=64*math.ulp(1.0)*(1+math.abs(p.alpha)+math.abs(q.alpha))*math.min(a,b)
        return analytic(logI0(r)-logDen,gaussianAllowance+phaseError,"constant-angular")
      }
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
      var minimum=math.exp(logI0(math.abs(a-b))-logDen)
      radius=4
      def gaussianTail = Erf.erfc(radius/math.sqrt(2))
      while (gaussianTail > tolerance*minimum/16 && radius < 16) { interrupted(); radius += 1 }
      // BEGIN BOUNDED CELL-TAIL POLICY
      // Skip cheap/narrow domains and near-constant phases. The prepass has a
      // separate fixed cap: maxEvaluations still counts integrand calls only.
      if(radius >= 8 && math.abs(phase.l)*radius+math.abs(phase.q)*radius*radius > 4) {
        val cellLower=GaussVonMisesScalarCellBound.lower(phase.c,phase.l,phase.q,p.kappa,q.kappa,
          () => { interrupted(); false })
        minimum=math.max(minimum,cellLower)
        radius=4
        while (gaussianTail > tolerance*minimum/16 && radius < 16) { interrupted(); radius += 1 }
      }
      // END BOUNDED CELL-TAIL POLICY
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
      // Incremental totals guide refinement only. Rebuild from current positive
      // panels every 64 splits and before ANY published numerical decision.
      // Thus subtracting a retired panel never supplies the final error estimate.
      class RunningSum {
        private var total=0.0
        private var correction=0.0
        def reset(value: Double): Unit = { total=value; correction=0.0 }
        def add(value: Double): Unit = {
          val updated=total+value
          correction += (if(math.abs(total) >= math.abs(value)) (total-updated)+value else (value-updated)+total)
          total=updated
        }
        def value: Double = finite(total+correction)
      }
      val valueSum=new RunningSum
      val errorSum=new RunningSum
      var splitsSinceAudit=64
      def audit(): Unit = {
        valueSum.reset(compensated(heap.iterator.map(_.value)))
        errorSum.reset(compensated(heap.iterator.map(_.error)))
        splitsSinceAudit=0
      }
      while (true) {
        interrupted()
        if(splitsSinceAudit >= 64) audit()
        val value=valueSum.value; val error=errorSum.value
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
        if(ok || floorDominates || evaluations+4 > maxEvaluations) {
          if(splitsSinceAudit == 0) return result
          // Re-enter the stopping test with fresh full sums, even for budget/precision
          // refusals. An optimistic incremental estimate cannot publish a distance.
          audit()
        } else {
          val parent=heap.dequeue(); val mid=(parent.left+parent.right)/2
          if(mid == parent.left || mid == parent.right) return unavailable(NumericallyUnresolved,evaluations,radius,tail)
          valueSum.add(-parent.value); errorSum.add(-parent.error)
          for ((left,right,old) <- Vector((parent.left,mid,parent.values.take(3)),(mid,parent.right,parent.values.drop(2)))) {
            val values=Vector(old(0),integrand(left+(right-left)/4),old(1),integrand(left+3*(right-left)/4),old(2))
            val child=panel(left,right,values)
            heap.enqueue(child)
            valueSum.add(child.value); errorSum.add(child.error)
          }
          splitsSinceAudit += 1
        }
      }
      throw new AssertionError("unreachable")
    } catch {
      case _: NumericalFailure => unavailable(NumericallyUnresolved,evaluations,radius,tail)
    }
  }
}
