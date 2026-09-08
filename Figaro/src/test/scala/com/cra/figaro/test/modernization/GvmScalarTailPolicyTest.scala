package com.cra.figaro.test.modernization

import com.cra.figaro.library.atomic.continuous.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class GvmScalarTailPolicyTest extends AnyWordSpec with Matchers {
  private def kernels(f: GvmScalarTailHoldoutFixtures.Fixture) = {
    val p=GaussVonMisesDistribution(Vector(f.pm),Vector(Vector(f.ps*f.ps)),0,Vector(0.0),Vector(Vector(0.0)),f.kp)
    val q=GaussVonMisesDistribution(Vector(f.qm),Vector(Vector(f.qs*f.qs)),f.alpha,Vector(f.beta),Vector(Vector(f.gamma)),f.kq)
    (p,q)
  }
  "Scalar tail selection on held-out inputs" should {
    "meet independent physical-input oracles in both directions including screening boundaries" in {
      GvmScalarTailHoldoutFixtures.all.size shouldBe 44
      var count=0
      for(f <- GvmScalarTailHoldoutFixtures.all) {
        val (p,q)=kernels(f)
        for((left,right) <- Vector((p,q),(q,p))) {
          val before=GvmScalarAuditedBaseline.compare(left,right,f.tolerance)
          val r=GaussVonMisesScalarBhattacharyya.compare(left,right,f.tolerance)
          withClue(f.id+": ") {
            r.status shouldBe GaussVonMisesScalarBhattacharyya.Status.Estimated
            math.abs(r.distance.get-f.expected) should be <= f.tolerance
            val (lo,hi)=r.interval.get
            f.expected should be >= (lo-8*math.ulp(f.expected))
            f.expected should be <= (hi+8*math.ulp(f.expected))
            r.radius should be <= before.radius
            r.evaluations should be <= 50000
          }
          count += 1
        }
      }
      count shouldBe 88
    }
    "exercise both sides of the radius and phase-span screens without a discontinuous distance" in {
      for(prefix <- Vector("linear-screen", "quadratic-screen")) {
        val values=GvmScalarTailHoldoutFixtures.all.filter(_.id.startsWith(prefix)).map { f =>
          val (p,q)=kernels(f)
          val r=GaussVonMisesScalarBhattacharyya.compare(p,q,f.tolerance)
          val active=math.abs(f.beta)*12+math.abs(f.gamma)*12*12 > 4
          r.radius shouldBe (if(active) 7.0 else 12.0)
          r.distance.get
        }
        values.max-values.min should be < 1e-8
      }
      val radii=GvmScalarTailHoldoutFixtures.all.filter(_.id.startsWith("radius-screen")).map { f =>
        val (p,q)=kernels(f)
        GvmScalarAuditedBaseline.compare(p,q,f.tolerance,maxEvaluations=5).radius
      }
      radii.head shouldBe 8.0; radii.last shouldBe 7.0
    }
    "respect integrand caps on held-out and near-boundary inputs" in {
      for(f <- GvmScalarTailHoldoutFixtures.all; budget <- Vector(5,500)) {
        val (p,q)=kernels(f)
        val r=GaussVonMisesScalarBhattacharyya.compare(p,q,f.tolerance,budget)
        r.evaluations should be <= budget
        if(r.status == GaussVonMisesScalarBhattacharyya.Status.Estimated)
          math.abs(r.distance.get-f.expected) should be <= f.tolerance
        else r.distance shouldBe None
      }
    }
  }
}
