package com.cra.figaro.test.modernization

import com.cra.figaro.library.atomic.continuous.*
import java.util.concurrent.CancellationException
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class GvmScalarCellBoundTest extends AnyWordSpec with Matchers {
  private def kernel(mu: Double=0,sd: Double=1,a: Double=0,b: Double=0,g: Double=0,k: Double=0) =
    GaussVonMisesDistribution(Vector(mu),Vector(Vector(sd*sd)),a,Vector(b),Vector(Vector(g)),k)
  "The internal scalar cell bound" should {
    "stay below independent phase-affinity oracles across concentration, wrapping and vertices" in {
      GvmScalarCellFixtures.all.size shouldBe 108
      for((c,l,q,kp,kq,expected) <- GvmScalarCellFixtures.all) {
        val value=GaussVonMisesScalarCellBound.lower(c,l,q,kp,kq)
        withClue(s"c=$c l=$l q=$q kp=$kp kq=$kq: ") {
          value.isFinite shouldBe true; value should be >= 0.0
          value should be <= Math.nextUp(expected)
          GaussVonMisesScalarCellBound.lower(-c,-l,-q,kq,kp) should be <= Math.nextUp(expected)
        }
      }
    }
    "bound setup work and cancel inside the prepass without swallowing caller failures" in {
      var calls=0
      GaussVonMisesScalarCellBound.lower(.5,1,2,50,50,() => { calls += 1; false })
      calls shouldBe 2369
      for(stop <- Vector(1,100,300,1000,2369)) {
        calls=0
        intercept[CancellationException] {
          GaussVonMisesScalarCellBound.lower(.5,1,2,50,50,() => { calls += 1; calls == stop })
        }
        calls shouldBe stop
      }
      val failure=new ArithmeticException("caller predicate")
      (intercept[ArithmeticException] {
        GaussVonMisesScalarCellBound.lower(0,0,0,1,1,() => throw failure)
      } eq failure) shouldBe true
      Thread.currentThread().interrupt()
      try {
        intercept[CancellationException] { GaussVonMisesScalarCellBound.lower(0,0,0,1,1) }
        Thread.currentThread().isInterrupted shouldBe true
      } finally { Thread.interrupted() }
    }
    "handle zero, subnormal and extreme phases conservatively and reject invalid parameters" in {
      for(k <- Vector(0.0,java.lang.Double.MIN_VALUE,1e-100,50.0)) {
        val value=GaussVonMisesScalarCellBound.lower(math.Pi,0,0,k,50)
        value.isFinite shouldBe true; value should be >= 0.0; value should be <= 1.0
      }
      GaussVonMisesScalarCellBound.lower(Double.MaxValue,Double.MaxValue,Double.MaxValue,50,50) shouldBe 0.0
      intercept[IllegalArgumentException] { GaussVonMisesScalarCellBound.lower(Double.NaN,0,0,1,1) }
      intercept[IllegalArgumentException] { GaussVonMisesScalarCellBound.lower(0,0,0,-1,1) }
      intercept[IllegalArgumentException] { GaussVonMisesScalarCellBound.lower(0,0,0,1,50.1) }
      intercept[IllegalArgumentException] { GaussVonMisesScalarCellBound.lower(0,0,0,1,1,null) }
    }
    "retain cheap-case diagnostics and full tail accounting while reducing curved work" in {
      for((p,q) <- Vector((kernel(k=4),kernel(b=.4,k=4)),
        (kernel(k=50),kernel(a=math.Pi,b=1e-5,k=50)))) {
        val before=GvmScalarAuditedBaseline.compare(p,q)
        val after=GaussVonMisesScalarBhattacharyya.compare(p,q)
        after.productIterator.drop(1).toVector shouldBe before.productIterator.drop(1).toVector
      }
      val p=kernel(.5,1,.25,.5,.25,50); val q=kernel(-.5,.5,-.5,-.25,2,50)
      val before=GvmScalarAuditedBaseline.compare(p,q)
      val after=GaussVonMisesScalarBhattacharyya.compare(p,q)
      before.radius shouldBe 12.0; after.radius shouldBe 7.0
      after.evaluations should be < before.evaluations
      after.gaussianTailBound shouldBe org.apache.commons.math3.special.Erf.erfc(7/math.sqrt(2))
      after.gaussianTailBound should be > before.gaussianTailBound
      for(t <- Vector(1e-6,1e-8,1e-12); budget <- Vector(5,500,7470,9102,15000,50000)) {
        val r=GaussVonMisesScalarBhattacharyya.compare(p,q,t,budget)
        r.evaluations should be <= budget
        if(r.status == GaussVonMisesScalarBhattacharyya.Status.Estimated)
          math.abs(r.distance.get-1.3162805891364138) should be <= t
        else r.distance shouldBe None
      }
    }
  }
}
