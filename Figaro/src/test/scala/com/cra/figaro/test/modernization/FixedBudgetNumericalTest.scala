package com.cra.figaro.test.modernization

import com.cra.figaro.algorithm.filtering.{FixedBudgetParticleFilter as F, BoundedParticleFilter as B}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class FixedBudgetNumericalTest extends AnyWordSpec with Matchers {
  private def filter(method:F.Method=F.Method.LogTreeMultinomial,policy:F.Policy=F.Policy.Always) = {
    var i = -1
    F(B.Model(()=>{i+=1;i},(x:Int)=>x),F.Config(2,policy,method))
  }
  "Fixed-budget numerical and lifecycle contracts" should {
    "retain tiny positive logs without declaring genuine collapse" in {
      val f=filter()
      try {
        val r=f.start(i=>if(i==0)0.0 else -1000.0)
        r.weights shouldBe Vector(1.0,0.0)
        r.logWeights shouldBe Vector(0.0,-1000.0)
        r.report.health.positiveWeights shouldBe 2
        f.advance(_=>0.0).particles.size shouldBe 2
      } finally f.close()
    }
    "retain the historical CDF diagnosis but resolve actual draws without a fallback" in {
      for(logs<-Vector(Vector(0.0,-1000.0),Vector(0.0,-40.0),Vector(-1000.0,0.0)))
        intercept[F.StratifiedPrecisionLoss](F.stratifiedCdf(logs))
      val f=filter(F.Method.Stratified)
      f.start(i=>if(i==0)0.0 else -1000.0)
      val r=f.advance(_=>0.0)
      r.particles shouldBe Vector(0,0)
      r.report.selection.decimalTables shouldBe 0
      f.close()
    }
    "invalidate the public filter on an explicit stratified domain refusal" in {
      val f=filter(F.Method.Stratified)
      f.start(i=>if(i==0)0.0 else -1000001.0)
      intercept[F.StratifiedPrecisionLoss](f.advance(_=>0.0)).reason should include ("span")
      intercept[IllegalArgumentException](f.current)
      f.lastReport shouldBe None
      f.close()
    }
    "preserve tiny log weights when resampling is skipped" in {
      val f=filter(F.Method.Stratified,F.Policy.WhenEssBelow(.25))
      try {
        f.start(i=>if(i==0)0.0 else -1000.0)
        val r=f.advance(i=>if(i==0)-1000.0 else 0.0)
        r.weights.foreach(_ shouldBe (.5 +- 1e-14))
        r.report.decision shouldBe F.Decision.CarriedWeights
        r.logEvidence shouldBe (-1000.0 +- 1e-12)
      } finally f.close()
    }
    "fail with a completed collapse report and forbid stale posterior or restart" in {
      val f=filter(); val old=f.start(_=>0.0)
      val failure=intercept[F.NoViableParticles](f.advance(_=>Double.NegativeInfinity))
      failure.logEvidence shouldBe Double.NegativeInfinity
      failure.report.health.positiveWeights shouldBe 0
      failure.report.contributingParents shouldBe Some(0)
      f.lastReport shouldBe Some(failure.report)
      intercept[IllegalArgumentException](f.current)
      intercept[IllegalArgumentException](f.start(_=>0.0))
      old.expectation(_.toDouble) shouldBe .5
      f.close();f.close()
    }
    "handle absolute tiny or large likelihoods without ordinary-space overflow" in {
      for(offset<-Vector(-10000.0,10000.0)) {
        val f=filter()
        try { f.start(_=>offset).logEvidence shouldBe (offset +- 1e-11) }
        finally f.close()
      }
    }
    "reject invalid likelihoods, carried arithmetic overflow and cumulative overflow" in {
      for(value<-Vector(Double.NaN,Double.PositiveInfinity)) {
        val f=filter();intercept[IllegalArgumentException](f.start(_=>value))
        intercept[IllegalArgumentException](f.current); f.lastReport shouldBe None; f.close()
      }
      intercept[ArithmeticException](F.reweight(Vector(-Double.MaxValue),Vector(-Double.MaxValue)))
      val f=filter();f.start(_=>Double.MaxValue)
      intercept[ArithmeticException](f.advance(_=>Double.MaxValue))
      f.lastReport shouldBe None;intercept[IllegalArgumentException](f.current);f.close()
    }
    "invalidate on callback failure and reentrancy without publishing a partial step" in {
      val f=filter();f.start(_=>0.0)
      intercept[IllegalStateException](f.advance(_=>throw new IllegalStateException("callback")))
      f.lastReport shouldBe None;intercept[IllegalArgumentException](f.current);f.close()
      val g=filter()
      intercept[IllegalArgumentException](g.start(_=>{g.current;0.0}))
      intercept[IllegalArgumentException](g.current);g.close()
    }
    "honor interruption before and during a callback and leave the interrupt flag set" in {
      for(before<-Vector(false,true)) {
        val f=filter()
        try {
          if(before)Thread.currentThread().interrupt()
          intercept[InterruptedException](f.start(_=>{Thread.currentThread().interrupt();0.0}))
          Thread.currentThread().isInterrupted shouldBe true
          f.lastReport shouldBe None
          intercept[IllegalArgumentException](f.current)
        } finally { Thread.interrupted(); f.close() }
      }
    }
    "confine lifecycle access to the owner without damaging the owner's valid state" in {
      val f=filter();f.start(_=>0.0)
      val error=new java.util.concurrent.atomic.AtomicReference[Throwable]()
      val thread=new Thread(()=>{try {f.advance(_=>0.0);()} catch {case e:Throwable=>error.set(e)}})
      thread.start();thread.join()
      error.get().isInstanceOf[IllegalArgumentException] shouldBe true
      f.current.particles.size shouldBe 2
      f.close();intercept[IllegalArgumentException](f.current)
    }
    "validate observable queries without changing the filter" in {
      val f=filter();val r=f.start(_=>0.0)
      intercept[IllegalArgumentException](r.expectation(_=>Double.NaN))
      r.expectation(_=>Double.MaxValue) shouldBe Double.MaxValue
      f.current shouldBe r;f.close()
    }
  }
}
