package com.cra.figaro.test.modernization

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class ResamplingScheduleExperimentTest extends AnyWordSpec with Matchers {
  import ResamplingScheduleExperiment.*
  "The research schedule" should {
    "carry normalized weights and use a weighted likelihood increment" in {
      val (logs,z)=reweight(Vector(math.log(.8),math.log(.2)),Vector(math.log(.25),math.log(.75)))
      math.exp(z) shouldBe (.35 +- 1e-14)
      math.exp(logs(0)) shouldBe (4.0/7 +- 1e-14)
      val (_,constant)=reweight(logs,Vector.fill(2)(math.log(.4)))
      math.exp(constant) shouldBe (.4 +- 1e-14)
      val (zero,_)=reweight(Vector(0,Double.NegativeInfinity),Vector(-1000,-2000))
      zero shouldBe Vector(0,Double.NegativeInfinity)
      intercept[IllegalArgumentException]{reweight(Vector(0),Vector(Double.NaN))}
    }
    "use strict ESS thresholds and retain all ancestors when balanced" in {
      for(m<-candidates.filter(_.startsWith("ess")))shouldResample(m,Vector.fill(4)(-math.log(4))) shouldBe false
      shouldResample("ess50",Vector(0,Double.NegativeInfinity)) shouldBe false
      shouldResample("ess75",Vector(0,Double.NegativeInfinity)) shouldBe true
    }
    "replay every schedule and retain fixed draw budgets" in {
      for(m<-candidates++controls++Vector("bootstrap","guided","auxiliary")){
        val a=ResamplingScheduleExperiment.run("switching",Vector.fill(4)(.8),m,16,22)
        val b=ResamplingScheduleExperiment.run("switching",Vector.fill(4)(.8),m,16,22)
        a.map(_.copy(nanos=0)) shouldBe b.map(_.copy(nanos=0))
        all(a.map(_.status)) shouldBe "ok"
        a.last.draws shouldBe 64L
        a.last.variance should be >= 0.0
        a.last.resamplings should be <= 7L
      }
    }
    "honor interruption and ownership without starting an unbounded retry" in {
      val c=new Counts;val failed=new java.util.concurrent.atomic.AtomicBoolean(false)
      val thread=new Thread(()=>{try c.access() catch{case _:IllegalArgumentException=>failed.set(true)}})
      thread.start();thread.join();failed.get() shouldBe true
      Thread.currentThread().interrupt()
      try intercept[InterruptedException]{c.access()} finally Thread.interrupted()
    }
  }
}
