package com.cra.figaro.test.modernization

import com.cra.figaro.algorithm.sampling.{StaticGraphImportance as S,InferenceHealth as H,GraphProposalImportance as P,VectorImportance as V}
import com.cra.figaro.library.atomic.continuous.{MultivariateGaussianDistribution as G,Normal as GraphNormal}
import com.cra.figaro.language.*
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import scala.jdk.CollectionConverters.*

class StaticGraphImportanceTest extends AnyWordSpec with Matchers {
  import S.Node.*
  private def gaussian=S.compile(Vector(Constant(0),Constant(1),S.Node.Normal(0,1),S.Node.Normal(2,1)))
  private def alive=Thread.getAllStackTraces.keySet().asScala.filter(t => t.isAlive && t.getName.startsWith("figaro-static-")).toVector
  "Restricted static graph importance" should {
    "validate types, dependencies, static domains and size before execution" in {
      intercept[IllegalArgumentException](S.compile(Vector(Add(0,0))))
      intercept[IllegalArgumentException](S.compile(Vector(Constant(.3),Bernoulli(0),Add(1,0))))
      intercept[IllegalArgumentException](S.compile(Vector(Constant(0),S.Node.Normal(0,0))))
      intercept[IllegalArgumentException](S.compile(Vector(Constant(2),Bernoulli(0))))
      intercept[IllegalArgumentException](S.compile(Vector.fill(4097)(Constant(0))))
      gaussian.nodeCount shouldBe 4
    }
    "recover an observed conjugate Gaussian posterior and exact log weights" in {
      val r=S.run(gaussian,Vector(2),Map(3 -> 1.0),S.Config(draws=30000))
      r.health.head.diagnostics.mean.get shouldBe (.5 +- .025)
      r.logWeights.zip(r.values.head).foreach { (w,x) => w shouldBe (-.5*math.log(2*math.Pi)-.5*math.pow(1-x,2) +- 1e-13) }
      r.nodeEvaluations shouldBe 120000L
    }
    "agree with the existing owned graph bridge on the same posterior" in {
      val law=G(Vector(0.0),Vector(Vector(1.0)))
      val old=P.run(P.Config(draws=30000,maxAttempts=30000),V.Gaussian(law),law.logDensity) { (u,root) =>
        given ElementCollection=u
        val x=root.map(_.head)
        GraphNormal(x,1.0).observe(1.0)
        x
      }
      val fresh=S.run(gaussian,Vector(2),Map(3 -> 1.0),S.Config(draws=30000))
      fresh.health.head.diagnostics.mean.get shouldBe (old.health.diagnostics.mean.get +- .04)
    }
    "preserve Boolean observation and eager selection semantics" in {
      val m=S.compile(Vector(Constant(.3),Bernoulli(0),Constant(.8),Constant(.2),Choose(1,2,3),Bernoulli(4),Indicator(1)))
      val r=S.run(m,Vector(1,6),Map(5 -> 1.0),S.Config(draws=30000))
      r.values(0) shouldBe r.values(1)
      r.health.head.diagnostics.mean.get shouldBe ((.3*.8/(.3*.8+.7*.2)) +- .025)
    }
    "replay exactly across worker counts without sharing mutable state" in {
      val m=gaussian; val c=S.Config(draws=4000,batches=7,parallelism=1)
      val a=S.run(m,Vector(2),Map(3 -> 1.0),c)
      for(workers <- Vector(2,4)) {
        val b=S.run(m,Vector(2),Map(3 -> 1.0),c.copy(parallelism=workers))
        b.copy(config=c) shouldBe a
      }
      alive shouldBe empty
    }
    "keep concurrent evidence snapshots isolated and restore the caller universe" in {
      val m=gaussian; val outer=Universe.universe; val active=outer.activeElements
      val evidence=Map(3 -> 1.0); val other=evidence.updated(3,-1.0)
      val pool=java.util.concurrent.Executors.newFixedThreadPool(2)
      try {
        val calls=Vector(evidence,other).map(e => pool.submit(new java.util.concurrent.Callable[S.Result] {
          def call(): S.Result=S.run(m,Vector(2),e,S.Config(draws=10000))
        }))
        calls.head.get(30,java.util.concurrent.TimeUnit.SECONDS).health.head.diagnostics.mean.get shouldBe (.5 +- .04)
        calls.last.get(30,java.util.concurrent.TimeUnit.SECONDS).health.head.diagnostics.mean.get shouldBe (-.5 +- .04)
      } finally { pool.shutdownNow(); pool.awaitTermination(30,java.util.concurrent.TimeUnit.SECONDS) }
      evidence shouldBe Map(3 -> 1.0)
      Universe.universe shouldBe outer; outer.activeElements shouldBe active
      alive shouldBe empty
    }
    "support versioned native stream allocation and immutable model reuse" in {
      import com.cra.figaro.util.{RandomStreams as R,SamplingRandom as A}
      val m=gaussian
      val changed=m.nodes.updated(0,Constant(99))
      changed should not be m.nodes
      for(algorithm <- Vector(A.Algorithm.Lxm,A.Algorithm.Philox4x64,A.Algorithm.Xoshiro256PlusPlus)) {
        val c=S.Config(draws=1000,parallelism=1,randomAlgorithm=algorithm,randomStreams=R.Config(R.Allocation.PartitionedV1))
        S.run(m,Vector(2),config=c).values shouldBe S.run(m,Vector(2),config=c.copy(parallelism=4)).values
      }
    }
    "retain impossible-evidence attempts and report unsupported posterior estimates" in {
      val m=S.compile(Vector(Constant(0),Bernoulli(0)))
      val r=S.run(m,Vector(1),Map(1 -> 1.0),S.Config(draws=100))
      r.logWeights shouldBe Vector.fill(100)(Double.NegativeInfinity)
      r.health.head.status shouldBe H.Status.Danger
      r.health.head.diagnostics.mean shouldBe None
    }
    "reject observation, storage and work violations before launching workers" in {
      val m=gaussian
      intercept[IllegalArgumentException](S.run(m,Vector(2),Map(0 -> 1.0)))
      intercept[IllegalArgumentException](S.run(m,Vector(2,2)))
      intercept[IllegalArgumentException](S.run(m,Vector(2),config=S.Config(maxStoredValues=1)))
      intercept[IllegalArgumentException](S.run(m,Vector(2),config=S.Config(maxNodeEvaluations=1)))
      intercept[IllegalArgumentException](S.run(S.compile(Vector(Constant(.5),Bernoulli(0))),Vector(1),Map(1 -> .2)))
      alive shouldBe empty
    }
    "evaluate supported arithmetic and reject numeric or dynamic-domain failure without leaked workers" in {
      val m=S.compile(Vector(Constant(-1000),Sigmoid(0),Exp(0),Add(1,2),Multiply(1,2)))
      val r=S.run(m,Vector(1,2,3,4),config=S.Config(draws=10))
      all(r.values.flatten) shouldBe 0.0
      val invalid=S.compile(Vector(Constant(-1),Constant(1),Multiply(0,1),S.Node.Normal(1,2)))
      intercept[IllegalArgumentException](S.run(invalid,Vector(3)))
      intercept[ArithmeticException](S.run(S.compile(Vector(Constant(10000),Exp(0))),Vector(1)))
      val overflow=S.compile(Vector(Constant(0),Constant(1)) ++ Vector.fill(4)(S.Node.Normal(0,1)))
      intercept[ArithmeticException](S.run(overflow,Vector(2),(2 to 5).map(_ -> 1e154).toMap))
      alive shouldBe empty
    }
    "preserve cancellation at entry and terminate in-flight workers before returning" in {
      try {
        Thread.currentThread().interrupt()
        intercept[InterruptedException](S.run(gaussian,Vector(2)))
        Thread.currentThread().isInterrupted shouldBe true
      } finally Thread.interrupted()
      val failure=new java.util.concurrent.atomic.AtomicReference[Throwable]()
      val flag=new java.util.concurrent.atomic.AtomicBoolean(false)
      val m=S.compile(Vector(Constant(0),Constant(1)) ++ Vector.fill(4094)(S.Node.Normal(0,1)))
      val caller=new Thread(() => {
        try S.run(m,Vector(2),config=S.Config(draws=200000,maxNodeEvaluations=1000000000))
        catch { case e: Throwable => failure.set(e); flag.set(Thread.currentThread().isInterrupted) }
        ()
      })
      caller.start()
      val deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(10)
      while(alive.isEmpty && caller.isAlive && System.nanoTime()<deadline) java.util.concurrent.locks.LockSupport.parkNanos(1000000)
      try { caller.interrupt(); caller.join(35000); caller.isAlive shouldBe false }
      finally { if(caller.isAlive) { caller.interrupt(); caller.join(35000) } }
      failure.get() shouldBe a[InterruptedException]; flag.get() shouldBe true
      alive shouldBe empty
    }
  }
}
