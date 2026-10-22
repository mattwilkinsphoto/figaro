package com.cra.figaro.test.modernization

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import com.cra.figaro.util.SamplingRandom

class ParticleDiversityExperimentTest extends AnyWordSpec with Matchers {
  import ParticleDiversityExperiment.*
  "The research-only move kernel" should {
    "match the independent rational asymmetric transition matrix" in {
      math.exp(logAcceptance(math.log(.2),math.log(.8),math.log(1.0/3),math.log(.75)))/3 shouldBe (1.0/3 +- 1e-14)
      .75*math.exp(logAcceptance(math.log(.8),math.log(.2),math.log(.75),math.log(1.0/3))) shouldBe (1.0/12 +- 1e-14)
      logAcceptance(0,Double.NegativeInfinity,0,0) shouldBe Double.NegativeInfinity
      logAcceptance(0,-1000,0,0) shouldBe -1000.0
    }
    "refuse invalid arithmetic and enforce bounded owner-thread work" in {
      intercept[IllegalArgumentException]{logAcceptance(0,Double.NaN,0,0)}
      intercept[ArithmeticException]{logAcceptance(-Double.MaxValue,Double.MaxValue,0,0)}
      val w=new Work(2);w.begin();w.target();w.target()
      intercept[IllegalStateException]{w.target()}
      w.transitions shouldBe 2L
      val failed=new java.util.concurrent.atomic.AtomicBoolean(false)
      val thread=new Thread(()=>{try w.access() catch {case _:IllegalArgumentException=>failed.set(true)}})
      thread.start();thread.join();failed.get() shouldBe true
      Thread.currentThread().interrupt()
      try intercept[InterruptedException]{w.access()} finally Thread.interrupted()
      w.close();intercept[IllegalArgumentException]{w.access()}
    }
    "resample only supported states and preserve exact count" in {
      for(s<-Vector("multi","strat")){
        resample(Vector(Double.NegativeInfinity,0),s,SamplingRandom.seeded(1)) shouldBe Vector(1,1)
        resample(Vector.fill(64)(-math.log(64)),s,SamplingRandom.seeded(3)).size shouldBe 64
      }
      intercept[IllegalArgumentException]{resample(Vector(Double.NegativeInfinity),"multi",SamplingRandom.seeded(1))}
    }
    "replay deterministic results and honor every attempted sweep" in {
      for(m<-candidates){
        val a=ParticleDiversityExperiment.run("persistent-modes",Vector(4,4),m,16,123)
        val b=ParticleDiversityExperiment.run("persistent-modes",Vector(4,4),m,16,123)
        a.map(_.copy(nanos=0)) shouldBe b.map(_.copy(nanos=0))
        all(a.map(_.status)) shouldBe "ok"
        val k=if(m.startsWith("local") || m.startsWith("global"))m.last.asDigit else 0
        a.last.moves shouldBe (32L*k)
        a.last.accepted should be <= a.last.moves
      }
    }
  }
}
