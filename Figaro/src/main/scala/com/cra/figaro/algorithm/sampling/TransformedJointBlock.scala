package com.cra.figaro.algorithm.sampling

import com.cra.figaro.algorithm.OneTime
import com.cra.figaro.language.*
import com.cra.figaro.library.atomic.continuous.*
import com.cra.figaro.util.random

/** TJB-01 development candidate, not part of published 7.4.1.
  * Fixed joint increments in identity/log/logit coordinates; no learning or fallback.
  * See TRANSFORMED_JOINT_BLOCK_PROTOCOL.md and INF-22 for validation gates.
  */
object TransformedJointBlock {
  /** Construct one complete, sampler-owned fixed proposal for an isolated graph.
    * @param roots two to eight distinct exact permanent Normal/Gamma/Beta roots;
    *              these must be all stochastic roots of the universe
    * @param incrementCovariance finite symmetric positive-definite covariance of
    *              coordinate increments, in root order (not a factor or raw covariance)
    * @return one joint MH proposal; only one-time and owned fixed-budget execution
    * @throws IllegalArgumentException for unsupported graphs, ownership or numeric ranges
    * @example `TransformedJointBlock(Vector(g, b), Vector(Vector(.25,.1),Vector(.1,.36)))`
    */
  def apply(roots: Seq[Element[Double]], incrementCovariance: Seq[Seq[Double]]): ProposalScheme = {
    require(roots != null && roots.size >= 2 && roots.size <= 8 && roots.forall(_ != null),
      "A joint block requires two through eight non-null roots")
    val xs = roots.toVector
    require(xs.distinct.size == xs.size, "Joint block roots must be distinct")
    val block = new Block(xs, GaussianBlockProposal.factorCovariance(incrementCovariance,xs.size))
    block.validateRoots(xs.head.universe)
    TransformedJointScheme(block)
  }

  private[sampling] def checkInterrupt(): Unit =
    if (Thread.currentThread().isInterrupted) throw new InterruptedException("Joint block interrupted")

  private[sampling] final case class Prepared(randomness: Vector[Double], logProposal: Double, logPrior: Double)

  private[sampling] final class Block private[TransformedJointBlock](val targets: Vector[Element[Double]],
    factor: Vector[Vector[Double]]) {
    val universe: Universe = targets.head.universe
    private var sampler: AnyRef = null
    private var thread: Thread = null
    private var graph = Set.empty[Element[?]]
    private var evidence = Map.empty[Element[?],(Any,Any)]
    private val betaParameters = targets.collect { case b: AtomicBeta => b -> (b.aValue,b.bValue) }.toMap
    private val rootsAllowed = Set[Class[?]](classOf[AtomicNormal],classOf[AtomicGamma],classOf[AtomicBeta])
    private val descendantsAllowed = Set[Class[?]](classOf[Constant[?]],classOf[Apply1[?,?]],
      classOf[Apply2[?,?,?]],classOf[Apply3[?,?,?,?]],classOf[Apply4[?,?,?,?,?]],
      classOf[Apply5[?,?,?,?,?,?]],classOf[Inject[?]])

    def validateRoots(owner: Universe): Unit = {
      require(owner eq universe, "Foreign joint block universe")
      targets.zipWithIndex.foreach { (e,i) =>
        require((e.universe eq owner) && e.active && !e.isTemporary && e.args.isEmpty &&
          e.observation.isEmpty && e.intervention.isEmpty && rootsAllowed.contains(e.getClass),
          s"Joint root $i must be an exact permanent unobserved constant Normal/Gamma/Beta")
      }
      require(betaParameters.forall { case (b,(a,c)) => b.aValue == a && b.bValue == c &&
        a.isFinite && c.isFinite && a > 0 && c > 0 }, "Beta parameters changed")
    }

    def bind(token: AnyRef, owner: Universe): Vector[Element[?]] = synchronized {
      checkInterrupt(); validateRoots(owner)
      require(token.isInstanceOf[OneTime], "Joint blocks require one-time fixed-budget execution")
      val active = owner.activeElements.toSet
      require(owner.parentElements.isEmpty && owner.stochasticElements.toSet == targets.toSet,
        "Joint block must own all stochastic roots")
      require(active.forall(e => (e.universe eq owner) && e.active && !e.isTemporary &&
        e.observation.isEmpty && e.intervention.isEmpty && e.args.forall(active.contains) &&
        owner.usedBy(e).forall(active.contains) && (targets.contains(e) || descendantsAllowed.contains(e.getClass))),
        "Joint block requires a fixed local deterministic graph, without observed/shared/dynamic nodes")
      require(active.forall(_.elementsIAmContingentOn.isEmpty), "Contingent evidence is unsupported")
      val currentEvidence: Map[Element[?],(Any,Any)] = active.iterator.map { e =>
        (e: Element[?]) -> ((e.allConditions: Any),(e.allConstraints: Any))
      }.toMap
      if (sampler == null) { sampler=token; thread=Thread.currentThread(); graph=active; evidence=currentEvidence }
      require((sampler eq token) && (thread eq Thread.currentThread()) && graph == active,
        "Joint block cannot be reused across samplers/threads or changed graphs")
      require(currentEvidence == evidence, "Joint block evidence changed after execution began")
      owner.activeElements.toVector
    }

    private def coordinates(e: Element[Double]): SupportAwareProposal.Coordinates[Double] = e match {
      case _: AtomicGamma => SupportAwareProposal.positiveCoordinates
      case _: AtomicBeta => SupportAwareProposal.boundedCoordinates(0,1)
      case _: AtomicNormal => new SupportAwareProposal.Coordinates[Double] {
        def encode(x: Double) = { require(x.isFinite); Vector(x) }
        def decode(z: Vector[Double]) = { require(z.size == 1 && z.head.isFinite); z.head }
        def logJacobian(z: Vector[Double]) = 0.0
      }
      case _ => throw new IllegalArgumentException("Unsupported joint root")
    }

    def prepare(owner: Universe): Prepared = prepare(owner,Vector.fill(targets.size)(random.nextGaussian()))

    /** Fixed innovations for deterministic conformance; never mutates graph state. */
    def prepare(owner: Universe, noise: Vector[Double]): Prepared = {
      checkInterrupt(); validateRoots(owner)
      require(noise.size == targets.size && noise.forall(_.isFinite), "Invalid joint innovations")
      var logQ=0.0; var logPrior=0.0
      val rs=targets.indices.map { i =>
        val e=targets(i); val oldR=e.randomness.asInstanceOf[Double]
        require(oldR.isFinite && e.value.isFinite && e.generateValue(e.randomness) == e.value,
          s"Joint root $i has inconsistent or unrepresentable current state")
        val c=coordinates(e); val oldZ=c.encode(e.value)
        val delta=factor(i).indices.iterator.map(j => factor(i)(j)*noise(j)).sum
        val z=oldZ.head+delta
        require(z.isFinite, s"Joint root $i increment outside numeric range")
        val proposed=c.decode(Vector(z))
        val r=e match {
          case n: AtomicNormal => oldR+delta/n.standardDeviation
          case g: AtomicGamma => proposed/g.thetaValue
          case _: AtomicBeta => proposed
        }
        require(r.isFinite, s"Joint root $i randomness outside numeric range")
        val value=e.generateValue(r.asInstanceOf[e.Randomness])
        require(!(delta != 0.0 && r != oldR && value == e.value),
          s"Joint root $i increment lost in value/randomness round trip")
        val realized=c.encode(value)
        val density=e.asInstanceOf[HasLogDensity[Double]]
        val before=density.logDensity(e.value); val after=density.logDensity(value)
        require(before.isFinite && after.isFinite, s"Joint root $i density outside numeric range")
        logPrior += after-before
        logQ += c.logJacobian(realized)-c.logJacobian(oldZ)
        r
      }.toVector
      require(logQ.isFinite && logPrior.isFinite && (logQ+logPrior).isFinite, "Joint ratio outside numeric range")
      Prepared(rs,logQ,logPrior)
    }
  }
}
