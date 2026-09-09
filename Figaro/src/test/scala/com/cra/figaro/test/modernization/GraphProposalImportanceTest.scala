package com.cra.figaro.test.modernization

import com.cra.figaro.algorithm.sampling.{GraphProposalImportance as P, VectorImportance as V, Importance}
import com.cra.figaro.language.*
import com.cra.figaro.library.atomic.continuous.{Normal, MultivariateGaussianDistribution as G}
import com.cra.figaro.util.{SamplingRandom,RandomContext,random}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class GraphProposalImportanceTest extends AnyWordSpec with Matchers {
  private def gaussian(mean: Double,variance: Double)=V.Gaussian(G(Vector(mean),Vector(Vector(variance))))
  private val prior=gaussian(0,1)
  "Owned graph proposal importance" should {
    "apply the full original-prior correction independently of user constraints" in {
      val proposal=gaussian(.5,2)
      val result=P.run(P.Config(draws=2000),proposal,prior.logDensity) { (u,root) =>
        root.removeConstraints()
        root.map(_.head)(using "",u)
      }
      result.logWeights shouldBe result.values.map(x => prior.logDensity(Vector(x))-proposal.logDensity(Vector(x)))
      result.proposalDraws shouldBe 2000
      result.priorEvaluations shouldBe 2000
      result.rejected shouldBe 0
      result.health.diagnostics.mean.get shouldBe (0.0 +- .1)
    }
    "match a conjugate observed Normal posterior and retain deterministic replay" in {
      val proposal=gaussian(.8,.3)
      def model(u: Universe,root: Element[Vector[Double]]): Element[Double] = {
        val theta=root.map(_.head)(using "",u)
        Normal(theta,.25)(using "",u).observe(1.0)
        theta
      }
      val result=P.run(P.Config(seed=1009),proposal,prior.logDensity)(model)
      result.health.diagnostics.mean.get shouldBe (.8 +- .025)
      val again=P.run(P.Config(seed=1009),proposal,prior.logDensity)(model)
      again shouldBe result
      result.logWeights.zip(result.values).foreach { (w,x) =>
        val likelihood = -.5*math.log(2*math.Pi*.25)-math.pow(x-1,2)/(.5)
        w shouldBe (prior.logDensity(Vector(x))-proposal.logDensity(Vector(x))+likelihood +- 1e-10)
      }
    }
    "integrate an additional stochastic hierarchical layer" in {
      val r=P.run(P.Config(draws=20000,maxAttempts=20000,seed=1009),gaussian(1.0/3,1),prior.logDensity) { (u,root) =>
        val theta=root.map(_.head)(using "",u)
        val latent=Normal(theta,1.0)(using "",u)
        Normal(latent,1.0)(using "",u).observe(1.0)
        theta
      }
      r.health.diagnostics.mean.get shouldBe (1.0/3 +- .035)
    }
    "combine full-mixture root correction with discrete observed evidence" in {
      val joint=V.Mixture(Vector(.7,.3),Vector(gaussian(-5,.09),gaussian(5,.09)))
      val proposal=V.Mixture(Vector(.5,.5),Vector(gaussian(-5,.12),gaussian(5,.12)))
      val r=P.run(P.Config(seed=991),proposal,joint.logDensity) { (u,root) =>
        val p=root.map(x => if(x.head>0) .8 else .2)(using "",u)
        Flip(p)(using "",u).observe(true)
        root.map(x => if(x.head>0) 1.0 else 0.0)(using "",u)
      }
      r.health.diagnostics.mean.get shouldBe (.24/.38 +- .025)
    }
    "retain rejected attempts without retries and stop at the attempt cap" in {
      val result=P.run(P.Config(draws=1000,maxAttempts=123),prior,prior.logDensity) { (u,root) =>
        root.addCondition(_ => false)
        root.map(_.head)(using "",u)
      }
      result.attempts shouldBe 123
      result.rejected shouldBe 123
      result.proposalDraws shouldBe 123
      result.reason shouldBe P.StopReason.MaxAttemptsReached
      result.logWeights shouldBe Vector.fill(123)(Double.NegativeInfinity)
      result.health.diagnostics.mean shouldBe None
    }
    "reject zero-prior draws before evaluating invalid downstream parameters" in {
      val q=V.Box(Vector(-1.0),Vector(1.0))
      var projected=0
      val result=P.run(P.Config(draws=2000),q,x => if(x.head>0) 0.0 else Double.NegativeInfinity) { (u,root) =>
        root.map { x => require(x.head>0); projected+=1; x.head }(using "",u)
      }
      result.rejected should be > 0
      projected shouldBe result.attempts-result.rejected
      result.health.diagnostics.mean.get shouldBe (.5 +- .04)
    }
    "restore caller state and clear the owned graph on success and callback failures" in {
      val caller=Universe.universe
      val outside=SamplingRandom.seeded(42); val control=SamplingRandom.seeded(42)
      var owned: Universe=null
      RandomContext.withRandom(outside) {
        val r=P.run(P.Config(draws=100),prior,prior.logDensity) { (u,root) => owned=u; root.map(_.head)(using "",u) }
        r.attempts shouldBe 100
        random.nextDouble() shouldBe control.nextDouble()
      }
      Universe.universe shouldBe caller
      owned.activeElements shouldBe empty
      val failure=new IllegalStateException("factory failed")
      intercept[IllegalStateException](P.run(P.Config(),prior,prior.logDensity) { (u,root) => owned=u; throw failure }) shouldBe failure
      owned.activeElements shouldBe empty
      intercept[IllegalArgumentException](P.run(P.Config(draws=100),prior,prior.logDensity) { (u,root) => owned=u; root.map(_ => Double.NaN)(using "",u) })
      owned.activeElements shouldBe empty
      Universe.universe shouldBe caller
    }
    "enforce traversal caps, preserve cancellation, and forbid observed roots or foreign dependencies" in {
      var owned: Universe=null
      intercept[P.TraversalLimit](P.run(P.Config(maxElementVisits=1),prior,prior.logDensity) { (u,root) => owned=u; root.map(_.head)(using "",u) })
      owned.activeElements shouldBe empty
      intercept[InterruptedException] {
        try P.run(P.Config(),prior,_ => { Thread.currentThread().interrupt(); 0.0 }) { (u,root) => owned=u; root.map(_.head)(using "",u) }
        finally { Thread.currentThread().isInterrupted shouldBe true; Thread.interrupted() }
      }
      owned.activeElements shouldBe empty
      intercept[IllegalArgumentException](P.run(P.Config(),prior,prior.logDensity) { (u,root) => owned=u; root.observe(Vector(1.0)); root.map(_.head)(using "",u) })
      owned.activeElements shouldBe empty
      val foreign=new Universe
      val outside=Normal(0,1)(using "",foreign)
      try {
        intercept[IllegalArgumentException](P.run(P.Config(),prior,prior.logDensity) { (u,root) => owned=u; outside.map(identity)(using "",u) })
        outside.active shouldBe true
        owned.activeElements shouldBe empty
      } finally foreign.clear()
    }
    "reject nested algorithms and keep concurrent owned graphs isolated" in {
      intercept[IllegalArgumentException](P.run(P.Config(),prior,prior.logDensity) { (u,root) =>
        val query=root.map(_.head)(using "",u)
        Importance(1,query)(using u).start()
        query
      })
      def run()=P.run(P.Config(draws=500),prior,prior.logDensity)((u,root) => root.map(_.head)(using "",u))
      val expected=run()
      val pool=java.util.concurrent.Executors.newFixedThreadPool(2)
      try {
        val jobs=Vector.fill(2)(pool.submit(new java.util.concurrent.Callable[P.Result] { def call()=run() }))
        jobs.foreach(_.get(10,java.util.concurrent.TimeUnit.SECONDS) shouldBe expected)
      } finally { pool.shutdownNow(); pool.awaitTermination(10,java.util.concurrent.TimeUnit.SECONDS) shouldBe true }
    }
  }
}
