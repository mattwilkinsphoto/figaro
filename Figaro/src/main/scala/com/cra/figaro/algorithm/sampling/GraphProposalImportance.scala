package com.cra.figaro.algorithm.sampling

import com.cra.figaro.language.*
import com.cra.figaro.library.cache.PermanentCache
import com.cra.figaro.util.{SamplingRandom, RandomContext, random}

/** Explicit joint-root proposal integration, using owned graph likelihood weighting. */
object GraphProposalImportance {
  /** @param draws requested independent graph attempts, including zero-weight attempts
    * @param maxAttempts positive attempt cap, independent of acceptance
    * @param maxElementVisits positive total traversal-observation lookup cap; exhaustion throws without a partial result
    * @param seed private scoped RNG seed
    * @param randomAlgorithm named RNG backend
    * @param maxStoredValues cap on retained log weights plus scalar query values, not a heap bound
    * @param health diagnostic-only policy, never precision stopping
    */
  final case class Config(draws: Int = 10000, maxAttempts: Int = 10000,
    maxElementVisits: Long = 1000000L, seed: Long = 43L,
    randomAlgorithm: SamplingRandom.Algorithm = SamplingRandom.defaultAlgorithm,
    maxStoredValues: Long = 2000000L, health: InferenceHealth.Config = InferenceHealth.Config()) {
    require(health!=null && draws>0 && draws<=health.maxSamples && maxAttempts>0 && maxElementVisits>0)
    require(randomAlgorithm!=null && maxStoredValues>0 && math.min(draws,maxAttempts).toLong<=maxStoredValues/2)
  }
  enum StopReason { case DrawsReached, MaxAttemptsReached }
  /** No partial estimator is returned after a mid-attempt traversal cap. */
  final case class TraversalLimit(limit: Long) extends RuntimeException(s"Graph traversal exceeded $limit observation lookups")
  /** Detached raw evidence; zero-weight attempts use scalar value zero without calling the query projection.
    * Rejected is the count of negative-infinite final weights, including hard conditions.
    * Proposal draws/prior calls include any work triggered during model construction.
    */
  final case class Result(logWeights: Vector[Double], values: Vector[Double], health: InferenceHealth.ImportanceReport,
    attempts: Int, rejected: Int, proposalDraws: Long, priorEvaluations: Long, elementVisits: Long,
    reason: StopReason, config: Config, randomProvider: String)

  /** Build a fresh graph around one explicit proposed joint root and estimate one scalar query.
    * @param config attempt/traversal/storage/RNG policy
    * @param proposal frozen normalized joint proposal; every draw must be valid for the model
    * @param logPrior pure unnormalized ORIGINAL joint prior density for the root, not the posterior likelihood
    * @param model callback receiving the owned universe and joint root; returns an owned scalar query
    * @return detached result after graph cleanup; callbacks must not retain/mutate owned elements or start algorithms
    * @example `run(Config(), proposal, logPrior)((u, root) => root.map(_.head))`
    */
  def run(config: Config, proposal: VectorImportance.Proposal, logPrior: Vector[Double] => Double)
    (model: (Universe,Element[Vector[Double]]) => Element[Double]): Result = {
    def check(): Unit = ParetoTail.interrupted()
    check(); require(config!=null && proposal!=null && logPrior!=null && model!=null)
    require(proposal.dimension>=1 && proposal.dimension<=128)
    val owned=new Universe {
      override def registerAlgorithm(algorithm: com.cra.figaro.algorithm.Algorithm): Unit =
        throw new IllegalArgumentException("Do not start or construct other algorithms inside the owned graph factory")
    }
    val rng=SamplingRandom.seeded(config.seed,config.randomAlgorithm)
    Universe.withUniverse(owned) { RandomContext.withRandom(rng) {
      var weighter: LikelihoodWeighter=null
      var primary: Throwable=null
      var proposalDraws=0L; var priorCalls=0L; var visits=0L
      try {
        final class Block extends Element[Vector[Double]]("",owned) with Atomic[Vector[Double]] with HasLogDensity[Vector[Double]] {
          type Randomness=Vector[Double]
          def generateRandomness(): Vector[Double] = {
            check(); proposalDraws+=1
            val x=proposal.sample(random); check()
            require(x!=null && x.size==proposal.dimension && x.forall(_.isFinite),"Invalid joint proposal draw")
            require(proposal.logDensity(x).isFinite,"Finite proposal log density required at its own draw")
            check(); x
          }
          def generateValue(x: Vector[Double]): Vector[Double]=x
          def logDensity(x: Vector[Double]): Double=proposal.logDensity(x)
        }
        val root=new Block
        def correction(x: Vector[Double]): Double = {
          check(); priorCalls+=1
          val p=logPrior(x); check()
          require(p.isFinite || p==Double.NegativeInfinity,"Invalid original joint prior log density")
          if(p==Double.NegativeInfinity) throw Importance.Reject
          val q=proposal.logDensity(x); check()
          require(q.isFinite && (p-q).isFinite,"Unrepresentable joint proposal correction")
          p-q
        }
        val query=model(owned,root); check()
        require(Universe.universe eq owned,"Model changed its owned universe scope")
        require(query!=null && (query.universe eq owned) && query.active,"Query must belong to the owned universe")
        def rootState(): Unit = require(root.active && root.observation.isEmpty && root.intervention.isEmpty,
          "The proposed root must remain active and unobserved/unintervened")
        rootState()
        weighter=new LikelihoodWeighter(owned,new PermanentCache(owned)) {
          override protected def getObservation(element: Element[?], observation: Option[?]): Option[Any] = {
            check()
            require(element.universe eq owned,"Cross-universe graph dependencies are unsupported")
            if(visits>=config.maxElementVisits) throw TraversalLimit(config.maxElementVisits)
            visits+=1
            if(element eq root) require(observation.isEmpty && root.observation.isEmpty,"Propagated observations on the proposed root are unsupported")
            super.getObservation(element,observation)
          }
          override private[figaro] def computeNextWeight(current: Double, element: Element[?], observation: Option[?]): Double = {
            check()
            val adjustment=if(element eq root) correction(root.value) else 0.0
            val next=super.computeNextWeight(current,element,observation)+adjustment
            check(); next
          }
        }
        val logs=Vector.newBuilder[Double]; val values=Vector.newBuilder[Double]
        val count=math.min(config.draws,config.maxAttempts)
        var rejected=0
        for(_ <- 0 until count) {
          check(); rootState()
          var attemptFailure: Throwable=null
          try {
            val weight=try weighter.computeWeight(root :: owned.activeElements.filterNot(_ eq root))
              catch { case Importance.Reject => Double.NegativeInfinity }
            check(); require(weight.isFinite || weight==Double.NegativeInfinity,"Invalid final graph weight")
            val value=if(weight==Double.NegativeInfinity) { rejected+=1; 0.0 } else query.value
            require(value.isFinite,"Finite scalar graph query required")
            logs+=weight; values+=value
          } catch { case error: Throwable => attemptFailure=error; throw error }
          finally {
            try owned.clearTemporaries()
            catch { case cleanup: Throwable => if(attemptFailure!=null) attemptFailure.addSuppressed(cleanup) else throw cleanup }
          }
        }
        val weights=logs.result(); val observations=values.result()
        val report=InferenceHealth.importance(weights,true,Some(observations),config.health)
        Result(weights,observations,report,count,rejected,proposalDraws,priorCalls,visits,
          if(count==config.draws) StopReason.DrawsReached else StopReason.MaxAttemptsReached,config,SamplingRandom.provenance(config.randomAlgorithm))
      } catch { case error: Throwable => primary=error; throw error }
      finally {
        var cleanup: Throwable=null
        def clean(body: => Unit): Unit = try body catch {
          case error: Throwable => if(cleanup==null) cleanup=error else cleanup.addSuppressed(error)
        }
        if(weighter!=null) { clean(weighter.clearCache()); clean(weighter.deregisterDependencies()) }
        clean(owned.clear())
        if(cleanup!=null) { if(primary!=null) primary.addSuppressed(cleanup) else throw cleanup }
      }
    } }
  }
}
