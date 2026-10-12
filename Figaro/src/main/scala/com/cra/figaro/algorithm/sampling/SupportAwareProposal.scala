package com.cra.figaro.algorithm.sampling

import com.cra.figaro.language.*
import com.cra.figaro.library.atomic.continuous.*
import com.cra.figaro.util.random

/** Fixed-scale, opt-in random walks in unconstrained coordinates. No adaptation or
  * default-kernel replacement. See docs/INFERENCE_INTEGRATION.md and INF-05.
  * Use as a complete scheme or a DisjointScheme choice, never a sequential continuation.
  */
object SupportAwareProposal {
  private[figaro] trait Coordinates[T] {
    def encode(x: T): Vector[Double]
    def decode(y: Vector[Double]): T
    def logJacobian(y: Vector[Double]): Double
  }
  private[figaro] val positiveCoordinates: Coordinates[Double] = new Coordinates[Double] {
    def encode(x: Double) = { require(x.isFinite && x > 0); Vector(math.log(x)) }
    def decode(y: Vector[Double]) = { val x = math.exp(y.head); require(x.isFinite && x > 0, "Positive transform overflow/underflow"); x }
    def logJacobian(y: Vector[Double]) = y.head
  }
  private[figaro] def boundedCoordinates(a: Double, b: Double): Coordinates[Double] = new Coordinates[Double] {
    require(a.isFinite && b.isFinite && b > a && (b-a).isFinite)
    def encode(x: Double) = { require(x > a && x < b); Vector(math.log(x-a)-math.log(b-x)) }
    def decode(y: Vector[Double]) = {
      val t = y.head
      val p = if(t >= 0) 1/(1+math.exp(-t)) else { val e=math.exp(t); e/(1+e) }
      val x = a+(b-a)*p
      require(x > a && x < b, "Bounded transform rounded to endpoint"); x
    }
    def logJacobian(y: Vector[Double]) = math.log(b-a)-math.abs(y.head)-2*math.log1p(math.exp(-math.abs(y.head)))
  }
  private[figaro] val circularCoordinates: Coordinates[Double] = new Coordinates[Double] {
    def encode(x: Double) = { require(x.isFinite); Vector(x) }
    def decode(y: Vector[Double]) = { val x=math.atan2(math.sin(y.head),math.cos(y.head)); if(x == math.Pi) -math.Pi else x }
    def logJacobian(y: Vector[Double]) = 0.0
  }
  private[figaro] def matrixCoordinates(d: Int, correlation: Boolean): Coordinates[Vector[Vector[Double]]] =
    new Coordinates[Vector[Vector[Double]]] {
      require(d >= 1 && d <= 16)
      def encode(x: Vector[Vector[Double]]): Vector[Double] = {
        if(correlation) require(x.indices.forall(i => x(i)(i)==1.0))
        val l=GaussianBlockProposal.factorCovariance(x,d)
        val out=Vector.newBuilder[Double]
        for(i <- 0 until d) {
          var remaining=1.0
          for(j <- 0 to i) {
            if(!correlation) out += (if(i==j) math.log(l(i)(j)) else l(i)(j))
            else if(j<i) {
              val z=l(i)(j)/remaining
              require(math.abs(z)<1,"Unresolved partial correlation")
              out += .5*(math.log1p(z)-math.log1p(-z))
              remaining *= math.sqrt((1-z)*(1+z))
            }
          }
        }
        out.result()
      }
      def decode(y: Vector[Double]): Vector[Vector[Double]] = {
        val l=Array.ofDim[Double](d,d); var k=0
        for(i <- 0 until d) {
          var remaining=1.0
          for(j <- 0 to i) {
            if(!correlation) { l(i)(j)=if(i==j) math.exp(y(k)) else y(k); k+=1 }
            else if(j<i) {
              val z=math.tanh(y(k)); k+=1
              require(math.abs(z)<1,"Partial correlation rounded to boundary")
              l(i)(j)=z*remaining; remaining*=math.sqrt((1-z)*(1+z))
            } else l(i)(j)=remaining
          }
          require(l(i)(i)>0 && l(i).forall(_.isFinite),"Matrix transform outside numeric range")
        }
        Vector.tabulate(d,d)((i,j) => if(correlation && i==j) 1.0 else {
          val a=math.min(i,j); val b=math.max(i,j)
          (0 to a).iterator.map(k => l(a)(k)*l(b)(k)).sum
        })
      }
      def logJacobian(y: Vector[Double]): Double = {
        var k=0; var result=if(correlation) 0.0 else d*math.log(2)
        for(i <- 0 until d; j <- 0 to i) {
          if(!correlation) { if(i==j) result+=(d+1-i)*y(k); k+=1 }
          else if(j<i) {
            val a=math.abs(y(k)); k+=1
            result+=(d-j)*(-a-math.log1p(math.exp(-2*a))+math.log(2))
          }
        }
        result
      }
    }

  /** @param element permanent fixed-kernel AtomicScalar with support (0,+Infinity)
    * @param stepSd positive finite log-coordinate increment SD
    * @return corrected log-space MH scheme
    * @example `SupportAwareProposal.positive(ScalarElement(LogNormalDistribution(0,1)), 0.2)` */
  def positive(element: AtomicScalar, stepSd: Double = .5): ProposalScheme = {
    require(element != null && element.distribution.support == (0.0,Double.PositiveInfinity))
    build(element,positiveCoordinates,stepSd)
  }
  /** @param element permanent AtomicScalar with finite distinct support endpoints
    * @param stepSd positive finite logit-coordinate increment SD
    * @return corrected bounded-value MH scheme; endpoints cannot be states
    * @example `SupportAwareProposal.bounded(ScalarElement(KumaraswamyDistribution(2,3)))` */
  def bounded(element: AtomicScalar, stepSd: Double = .5): ProposalScheme = {
    require(element != null); val (a,b)=element.distribution.support
    build(element,boundedCoordinates(a,b),stepSd)
  }
  /** @param element permanent circular von Mises element
    * @param stepSd positive finite angular increment SD, in radians
    * @return symmetric wrapped-normal proposal, continuous across the angular seam
    * @example `SupportAwareProposal.circular(VonMises(0,2), 0.2)` */
  def circular(element: AtomicVonMises, stepSd: Double = .5): ProposalScheme = build(element,circularCoordinates,stepSd)
  /** @param element permanent Wishart or inverse-Wishart adapter (not an arbitrary matrix graph)
    * @param stepSd positive finite increment SD in log-diagonal/raw-off-diagonal Cholesky coordinates
    * @return covariance proposal including the Cholesky Jacobian
    * @example `SupportAwareProposal.covariance(Wishart(law), 0.1)` */
  def covariance(element: Element[Vector[Vector[Double]]], stepSd: Double = .1): ProposalScheme = element match {
    case e: AtomicWishart => build(e,matrixCoordinates(e.distribution.scale.size,false),stepSd)
    case e: AtomicInverseWishart => build(e,matrixCoordinates(e.distribution.scale.size,false),stepSd)
    case _ => throw new IllegalArgumentException("Requires fixed Wishart or inverse-Wishart element")
  }
  /** @param element permanent fixed LKJ adapter
    * @param stepSd positive finite unconstrained partial-correlation increment SD
    * @return correlation-matrix MH scheme including its full Jacobian
    * @example `SupportAwareProposal.correlation(LKJ(LKJDistribution(3)), 0.2)` */
  def correlation(element: AtomicLKJ, stepSd: Double = .2): ProposalScheme = {
    require(element != null); build(element,matrixCoordinates(element.distribution.dimension,true),stepSd)
  }
  private def build[T](e: Element[T] & HasLogDensity[T], c: Coordinates[T], sd: Double): ProposalScheme = {
    require(e != null && sd.isFinite && sd>0)
    val move=new Move(e, () => {
      val y=c.encode(e.value); val next=y.map(_+sd*random.nextGaussian())
      require(next.forall(_.isFinite),"Proposal outside numeric range")
      val x=c.decode(next)
      val before=e.logDensity(e.value); val after=e.logDensity(x)
      val q=c.logJacobian(next)-c.logJacobian(y)
      require(before.isFinite && after.isFinite && q.isFinite && (after-before).isFinite,
        "Support-aware proposal density outside numeric range")
      (x,q,after-before)
    })
    move.validate(e.universe); SupportAwareScheme(move)
  }
  private[sampling] final class Move(val target: Element[?], proposal: () => (Any,Double,Double)) {
    def validate(owner: Universe): Unit = require((target.universe eq owner) && target.active &&
      !target.isTemporary && target.args.isEmpty && target.observation.isEmpty && target.intervention.isEmpty,
      "Support-aware proposal requires a permanent active fixed unobserved, unintervened target in the sampler universe")
    def prepare(owner: Universe): (Any,Double,Double) = { validate(owner); proposal() }
  }
}
