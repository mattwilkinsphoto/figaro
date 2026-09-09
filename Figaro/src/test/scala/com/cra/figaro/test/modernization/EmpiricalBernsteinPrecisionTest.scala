package com.cra.figaro.test.modernization

import com.cra.figaro.algorithm.sampling.{BoundedIidPrecision as B,EmpiricalBernsteinPrecision as E}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class EmpiricalBernsteinPrecisionTest extends AnyWordSpec with Matchers {
  "Predictable empirical Bernstein stopping" should {
    "enclose the independent direct-log reference on exactly represented observations" in {
      var i=0
      val r=E.run(B.Config(0,1,1e-12,maxDraws=1000)) { _ => val x=.25+(i%3)/16.0; i+=1; x }
      r.estimate shouldBe (.3124375 +- 1e-15)
      r.lower shouldBe (.303080734369049 +- 2e-15)
      r.upper shouldBe (.321794265630951 +- 2e-15)
    }
    "save samples on a bounded constant while retaining uncertainty" in {
      val c=B.Config(0,1,.02,maxDraws=100000)
      val adaptive=E.run(c)(_ => .25); val baseline=B.run(c)(_ => .25)
      adaptive.reason shouldBe B.StopReason.PrecisionReached
      adaptive.errorBound should be > 0.0
      adaptive.draws should be < baseline.draws/10
      adaptive.lower should be <= .25; adaptive.upper should be >= .25
    }
    "report the weighted center separately and preserve seeded replay" in {
      val c=B.Config(0,1,.02,maxDraws=20000,seed=12)
      val r=E.run(c)(_.nextDouble())
      r shouldBe E.run(c)(_.nextDouble())
      r.estimate should not be r.sampleMean
      r.lower should be <= .5; r.upper should be >= .5
    }
    "not certify rare-event absence or precision from a zero prefix" in {
      val r=E.run(B.Config(0,1,1e-6,maxDraws=1000))(_ => 0.0)
      r.errorBound should be > .001
      r.reason shouldBe B.StopReason.BudgetExhausted
    }
    "handle equal support, extreme units and final checks" in {
      val c=B.Config(2,2,.01,maxDraws=123,minDraws=101)
      E.run(c)(_ => 2).draws shouldBe 123
      val r=E.run(B.Config(-Double.MaxValue,Double.MaxValue,1,maxDraws=2,minDraws=2))(_ => Double.MaxValue)
      r.estimate shouldBe Double.MaxValue
      r.reason shouldBe B.StopReason.BudgetExhausted
    }
    "reject bad draws and propagate exceptions and interruption" in {
      val c=B.Config(0,1,.01)
      intercept[IllegalArgumentException](E.run(c)(_ => Double.NaN))
      intercept[IllegalArgumentException](E.run(c)(_ => 2.0))
      intercept[IllegalStateException](E.run(c)(_ => throw new IllegalStateException))
      try {
        intercept[InterruptedException](E.run(c) { _ => Thread.currentThread().interrupt(); .5 })
        Thread.currentThread().isInterrupted shouldBe true
      } finally Thread.interrupted()
    }
    "cover bounded means on held-out optional-stopping controls" in {
      val truths=Vector(.5,.01,.001,.5)
      val misses=truths.indices.map { model =>
        (0 until 100).count { seed =>
          val r=E.run(B.Config(0,1,.04,maxDraws=10000,checkEvery=37,seed=810001L+7919L*seed)) { rng =>
            if(model==3) .45+.1*rng.nextDouble() else if(rng.nextDouble()<truths(model)) 1.0 else 0.0
          }
          r.lower>truths(model) || r.upper<truths(model)
        }
      }
      info(s"Adaptive terminal misses /100 (Bernoulli .5/.01/.001, bounded uniform): $misses")
      all(misses) should be <= 5
    }
    "isolate concurrent state and RNGs" in {
      val pool=java.util.concurrent.Executors.newFixedThreadPool(2)
      try {
        val c=B.Config(0,1,.05,maxDraws=2000)
        val futures=(0 until 4).map(i => pool.submit(new java.util.concurrent.Callable[E.Result] {
          def call(): E.Result=E.run(c.copy(seed=i))(_.nextDouble())
        }))
        futures.zipWithIndex.foreach { (f,i) => f.get(30,java.util.concurrent.TimeUnit.SECONDS) shouldBe E.run(c.copy(seed=i))(_.nextDouble()) }
      } finally { pool.shutdownNow(); pool.awaitTermination(30,java.util.concurrent.TimeUnit.SECONDS) }
    }
  }
}
