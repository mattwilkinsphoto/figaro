package com.cra.figaro.test.modernization

import com.cra.figaro.algorithm.filtering.{BoundedParticleFilter as B, WeightedBoundedParticleFilter as W}
import com.cra.figaro.util.{random, withRandomSeed}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class WeightedBoundedParticleFilterTest extends AnyWordSpec with Matchers {
  private def scripted(logs: Vector[Double], scores: Vector[Double], c: W.Config) = {
    var i = -1
    W.step(c,true)(() => {i+=1; (i,i)}, W.Evidence[Int](logs(_), _ => scores(i)))
  }
  "Weighted bounded filtering" should {
    "retain the entire initial batch and discard only an adaptive stopping candidate" in {
      val a=scripted(Vector.fill(4)(math.log(2)),Vector.fill(4)(1.0),W.Config(4,8,1))
      a.values shouldBe Vector(0,1,2,3)
      a.logIncrement shouldBe (math.log(2) +- 1e-14)
      val b=scripted(Vector(0,0,0,math.log(100)),Vector(.25,.25,.25,.75),W.Config(2,4,1.5))
      b.values shouldBe Vector(0,1,2)
      b.weights shouldBe Vector.fill(3)(1.0/3)
      b.report.reason shouldBe W.StopReason.ScoreTarget
      b.report.attempts shouldBe 4
      b.report.totalScore shouldBe 1.5
      b.logIncrement shouldBe (0.0 +- 1e-14)
      b.report.distinctParents shouldBe Some(3)
    }
    "keep partial weighted populations when the hard cap is exhausted" in {
      val r=scripted(Vector(0,math.log(3),0,0),Vector.fill(4)(.1),W.Config(2,4,2))
      r.report.reason shouldBe W.StopReason.BudgetExhausted
      r.values.size shouldBe 4
      r.logIncrement shouldBe (math.log(1.5) +- 1e-14)
      r.report.warnings should not be empty
    }
    "preserve absolute tiny and large likelihoods and retain tiny positive log weights" in {
      for(offset <- Vector(-10000.0,10000.0)) {
        val r=scripted(Vector(offset,offset+math.log(3)),Vector(1,1),W.Config(2,2,1))
        r.weights(1) shouldBe (.75 +- 1e-12)
        r.logIncrement shouldBe (offset+math.log(2) +- 2e-12)
      }
      val tiny=scripted(Vector(0,-1000),Vector(1,1),W.Config(2,2,1))
      tiny.weights shouldBe Vector(1.0,0.0)
      tiny.logWeights shouldBe Vector(0.0,-1000.0)
      tiny.logIncrement shouldBe (-math.log(2) +- 1e-14)
    }
    "validate likelihoods scores and work boundaries without accepting invalid results" in {
      intercept[IllegalArgumentException](W.Config(1,4,1))
      intercept[IllegalArgumentException](W.Config(2,1000001,1))
      intercept[IllegalArgumentException](W.Config(2,4,Double.NaN))
      for(w <- Vector(Double.NaN,Double.PositiveInfinity))
        intercept[IllegalArgumentException](scripted(Vector(w,0),Vector(1,1),W.Config(2,2,1)))
      for(s <- Vector(-.1,1.1,Double.NaN,Double.PositiveInfinity))
        intercept[IllegalArgumentException](scripted(Vector(0,0),Vector(s,1),W.Config(2,2,1)))
      W.cappedLikelihood(-1000)(-1001) shouldBe (math.exp(-1) +- 1e-15)
      W.cappedLikelihood(-1000)(0) shouldBe 1.0
      W.cappedLikelihood(0)(Double.NegativeInfinity) shouldBe 0.0
    }
    "select weighted ancestors with strict boundaries and never a zero interval" in {
      val c=Vector(0.0,.25,.25,1.0,1.0)
      W.ancestor(c,0) shouldBe 1
      W.ancestor(c,.25) shouldBe 3
      W.ancestor(c,math.nextDown(1.0)) shouldBe 3
    }
    "match exact two-step finite evidence and unnormalized state moments" in {
      // Full fixed-cap bit strings partition probability, including unused suffixes.
      for(cap <- 2 to 5; floor <- 2 to cap; target <- Vector(.75,1.5,2.0)) {
        val c=W.Config(floor,cap,target)
        def outcomes(p:Double, weights:Vector[Double]) = (0 until (1<<cap)).map { bits =>
          val xs=Vector.tabulate(cap)(i => (bits>>i)&1)
          val probability=xs.map(x=>if(x==1)p else 1-p).product
          var i = -1
          val r=W.step(c,false)(()=>{i+=1;(xs(i),-1)},
            W.Evidence[Int](x=>math.log(weights(x)),w=>if(w==math.log(weights(1))) .75 else .25))
          (probability,r)
        }
        var evidence=0.0
        var moment=0.0
        for((p,a)<-outcomes(.4,Vector(.2,1.4))) {
          val post=a.values.zip(a.weights).map((x,w)=>x*w).sum
          for((q,b)<-outcomes(.25+.5*post,Vector(1.2,.3))) {
            val z=math.exp(a.logIncrement+b.logIncrement)
            evidence+=p*q*z
            moment+=p*q*z*b.values.zip(b.weights).map((x,w)=>x*w).sum
          }
        }
        val joint1=.4*1.4
        val joint0=.6*.2
        val end1=(joint0*.25+joint1*.75)*.3
        val end0=(joint0*.75+joint1*.25)*1.2
        evidence shouldBe (end0+end1 +- 2e-12)
        moment shouldBe (end1 +- 2e-12)
      }
    }
    "terminate on genuine zero weights and retain diagnostics without retry" in {
      var count=0
      val f=W(B.Model(()=>{count+=1;0},(x:Int)=>x),W.Config(2,5,2))
      val error=intercept[W.NoViableParticles](f.start(W.Evidence[Int](_=>Double.NegativeInfinity,_=>0)))
      count shouldBe 5
      error.logEvidence shouldBe Double.NegativeInfinity
      f.lastReport.get.health.positiveWeights shouldBe 0
      intercept[IllegalArgumentException](f.current)
      f.close(); f.close()
    }
    "propagate callback failure interruption and reentrancy without partial estimates" in {
      val f=W(B.Model(()=>0,(x:Int)=>x),W.Config(2,5,2))
      intercept[IllegalStateException](f.start(W.Evidence[Int](_=>throw new IllegalStateException("callback"),_=>1)))
      f.lastReport shouldBe None
      intercept[IllegalArgumentException](f.current)
      f.close()
      val interrupted=W(B.Model(()=>{Thread.currentThread().interrupt();0},(x:Int)=>x),W.Config(2,2,1))
      try intercept[InterruptedException](interrupted.start(W.Evidence[Int](_=>0,_=>1)))
      finally {Thread.interrupted(); interrupted.close()}
      val reentrant=W(B.Model(()=>0,(x:Int)=>x),W.Config(2,2,1))
      intercept[IllegalArgumentException](reentrant.start(W.Evidence[Int](_=>{reentrant.close();0},_=>1)))
      reentrant.lastReport shouldBe None
      reentrant.close()
    }
    "replay owned streams and reject access from another thread" in {
      def run() = {
        val f=W(B.Model(()=>random.nextDouble(),(x:Double)=>x+random.nextDouble()),W.Config(8,32,10,seed=44))
        try {f.start(W.Evidence[Double](x=> -x,W.cappedLikelihood(0))); f.advance(W.Evidence[Double](x=> -x,W.cappedLikelihood(0)))}
        finally f.close()
      }
      run() shouldBe run()
      val f=W(B.Model(()=>0,(x:Int)=>x),W.Config(2,2,1))
      val error=new java.util.concurrent.atomic.AtomicReference[Throwable]()
      val t=new Thread(()=>{try f.close() catch {case e:Throwable=>error.set(e)}})
      t.start(); t.join()
      error.get().isInstanceOf[IllegalArgumentException] shouldBe true
      f.close()
    }
    "restore the ambient RNG even after a sampling callback fails" in {
      def sequence(run:Boolean)=withRandomSeed(777) {
        val first=random.nextDouble()
        if(run) {
          val f=W(B.Model(()=>{random.nextDouble();throw new IllegalStateException("draw")},(_:Int)=>0),W.Config(2,2,1))
          try intercept[IllegalStateException](f.start(W.Evidence[Int](_=>0,_=>1))) finally f.close()
        }
        (first,random.nextDouble())
      }
      sequence(true) shouldBe sequence(false)
    }
    "exclude the stopping candidate from actual next-step ancestry" in {
      var draw = -1
      val f=W(B.Model(()=>{draw+=1;draw},(x:Int)=>{require(x<3,"Discarded ancestor used");x}),W.Config(2,4,1.5))
      try {
        val r=f.start(W.Evidence[Int](i=>if(i==3)math.log(100) else 0,
          w=>if(w>0).75 else .25))
        r.particles shouldBe Vector(0,1,2)
        for(_<-0 until 20) f.advance(W.Evidence[Int](_=>0,_=>.25)).particles.forall(_<3) shouldBe true
      } finally f.close()
    }
    "warn about concentration even when every likelihood is positive" in {
      val r=scripted(Vector(0,-100,-100,-100),Vector.fill(4)(.1),W.Config(2,4,2))
      r.report.health.positiveWeights shouldBe 4
      r.report.health.ess.get shouldBe (1.0 +- 1e-14)
      r.report.health.issues should not be empty
    }
    "preserve constant-weight evidence on long sequences" in {
      val f=W(B.Model(()=>1,(x:Int)=>x),W.Config(2,4,1))
      try {
        for(t<-1 to 256) {
          val e=W.Evidence[Int](_=> -1000,_=>.25)
          val r=if(t==1)f.start(e) else f.advance(e)
          r.logEvidence shouldBe (-1000.0*t +- 1e-8)
          r.expectation(_.toDouble) shouldBe (1.0 +- 1e-14)
        }
      } finally f.close()
    }
    "match Kalman evidence and unnormalized moments across all predeclared seeds" in {
      WeightedBoundedParticleStudy.run().foreach { r =>
        info(r.line)
        math.abs(r.mean-r.expected) should be <= (6*r.standardError+1e-12)
      }
    }
  }
}
