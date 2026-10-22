package com.cra.figaro.test.modernization

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class ParticleDiversityDiagnosticsTest extends AnyWordSpec with Matchers {
  import ParticleDiversityDiagnostics.*
  "Test-only diversity instrumentation" should {
    "match exact occupancy enumeration including zero and unit weights" in {
      expectedParents(Vector(.25,.75),2) shouldBe (1.375 +- 1e-14)
      expectedParents(Vector(0,1),0) shouldBe 0.0
      expectedParents(Vector(0,1),20) shouldBe 1.0
      expectedParents(Vector(1e-20,1-1e-20),2) shouldBe (1.0 +- 1e-14)
    }
    "leave every particle weight and evidence value unchanged across subsequent updates" in {
      for(mode<-modes;s<-ContinuousFilterBenchmark.scenarios){
        val a=ParticleDiversityDiagnostics.run(s,Vector(4,4.2,3.8,4.1),mode,714,true)
        val b=ParticleDiversityDiagnostics.run(s,Vector(4,4.2,3.8,4.1),mode,714,false)
        a.states shouldBe b.states
        a.rows.size shouldBe 4
        b.rows shouldBe empty
      }
    }
  }
}
