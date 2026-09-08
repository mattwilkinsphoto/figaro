package com.cra.figaro.test.modernization

import com.cra.figaro.library.atomic.continuous.*
import java.util.concurrent.{Callable, CancellationException, Executors, TimeUnit}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class GaussVonMisesQuadratureComparisonTest extends AnyWordSpec with Matchers {
  private def canonical(n: Int=1,k: Double=0) = GaussVonMisesDistribution(Vector.fill(n)(0.0),
    Vector.tabulate(n,n)((i,j) => if(i == j) 1.0 else 0.0),0,
    Vector.fill(n)(0.0),Vector.fill(n,n)(0.0),k)
  private def nonlinear(v: LinearAngular): Double=math.exp(-v.linear(0)*v.linear(0))

  "Budgeted GVM order comparison" should {
    "match four direct tensor estimates and charge every callback including repeated points" in {
      val p=canonical(); val q=GaussVonMisesQuadratureComparison(p,3,7,2,4,60)
      var calls=0
      val r=q.compareVector(2) { v => calls += 1; Vector(nonlinear(v),v.linear(0)*v.linear(0)) }
      calls shouldBe 60; q.evaluations shouldBe 60; r.evaluations shouldBe 60
      Vector(r.baseline,r.gaussianRefined,r.angularRefined,r.jointRefined)
        .zip(Vector(q.baselineRule,q.gaussianRule,q.angularRule,q.jointRule)).foreach { (value,rule) =>
          value shouldBe rule.expectationVector(2)(v => Vector(nonlinear(v),v.linear(0)*v.linear(0)))
        }
      r.withinTolerance shouldBe Vector(false,true)
      r.ordersAgree shouldBe false
      r.maxGaussianChange.head should be > 0.09
      r.maxAngularChange.foreach(_ should be < 1e-14)
      r.jointRefined(1) shouldBe (1.0 +- 1e-14)
    }
    "identify angular sensitivity even when Gaussian refinement and angular mass agree" in {
      val q=GaussVonMisesQuadratureComparison(canonical(),1,2,8,128)
      val r=q.compare(v => math.cos(16*v.angle))
      r.maxGaussianChange.head should be < 1e-14
      r.maxAngularChange.head should be > 0.01
      r.ordersAgree shouldBe false
      r.jointRefined.head shouldBe (0.0 +- 1e-12)
    }
    "check all four edges instead of accepting cancellation on the diagonal" in {
      val q=GaussVonMisesQuadratureComparison(canonical(),3,7,8,128)
      val g=q.compare(nonlinear)
      val a=q.compare(v => math.cos(16*v.angle))
      val coefficient= -(g.jointRefined.head-g.baseline.head)/(a.jointRefined.head-a.baseline.head)
      val r=q.compare(v => nonlinear(v)+coefficient*math.cos(16*v.angle))
      math.abs(r.jointRefined.head-r.baseline.head) should be < 1e-12
      r.maxGaussianChange.head should be > 0.09
      r.maxAngularChange.head should be > 0.09
      r.ordersAgree shouldBe false
    }
    "measure Gaussian changes at the refined angular order as well as at baseline" in {
      val q=GaussVonMisesQuadratureComparison(canonical(),3,7,8,128)
      val offset=q.baselineRule.expectation(v => math.cos(16*v.angle))
      val r=q.compare(v => nonlinear(v)*(math.cos(16*v.angle)-offset))
      math.abs(r.gaussianRefined.head-r.baseline.head) should be < 1e-12
      math.abs(r.jointRefined.head-r.angularRefined.head) should be > 0.01
      r.maxGaussianChange.head shouldBe math.abs(r.jointRefined.head-r.angularRefined.head)
      r.ordersAgree shouldBe false
    }
    "explicitly demonstrate false agreement on a polynomial with analytic expectation one" in {
      val q=GaussVonMisesQuadratureComparison(canonical(),3,5,2,4)
      val r=q.compare { v =>
        val z=v.linear(0); val h3=z*z*z-3*z; val h5=math.pow(z,5)-10*z*z*z+15*z
        math.pow(h3*h5,2)/295920.0
      }
      // E[(H3(Z)*H5(Z))^2]=295920 from Gaussian even moments. Both orders sample roots of the product.
      r.ordersAgree shouldBe true
      r.jointRefined.head shouldBe (0.0 +- 1e-20)
      math.abs(r.jointRefined.head-1.0) should be > 0.99
    }
    "apply absolute and relative tolerances to each edge and each output" in {
      val q=GaussVonMisesQuadratureComparison(canonical(),3,7,2,4)
      val tight=q.compare(nonlinear,1e-15,0)
      val delta=tight.maxGaussianChange.head
      q.compare(nonlinear,delta*1.001,0).ordersAgree shouldBe true
      q.compare(nonlinear,delta*0.999,0).ordersAgree shouldBe false
      val scale=math.max(math.abs(tight.baseline.head),math.abs(tight.gaussianRefined.head))
      q.compare(nonlinear,0,delta/scale*1.001).ordersAgree shouldBe true
      q.compare(nonlinear,0,delta/scale*0.999).ordersAgree shouldBe false
      q.compare(_ => 0,0,1e-4).ordersAgree shouldBe true
      q.compare(_ => 1e300,1e300,1).ordersAgree shouldBe true
      q.compare(v => nonlinear(v)*1e300,delta*1e300*1.001,0).ordersAgree shouldBe true
    }
    "reject an overflowing estimate difference instead of treating infinite tolerance as agreement" in {
      val q=GaussVonMisesQuadratureComparison(canonical(),1,2,2,4)
      def extreme(v: LinearAngular): Double=if(math.abs(v.linear(0)) < 0.1) Double.MaxValue else -Double.MaxValue
      q.baselineRule.expectation(extreme).isFinite shouldBe true
      q.gaussianRule.expectation(extreme).isFinite shouldBe true
      intercept[ArithmeticException] { q.compare(extreme,Double.MaxValue,1) }
    }
    "preflight the whole budget before constructing any rules" in {
      val p=canonical()
      // Largest individual rule costs 20; all four together cost 48.
      intercept[IllegalArgumentException] { GaussVonMisesQuadratureComparison(p,3,5,2,4,30) }
      GaussVonMisesQuadratureComparison(p,3,5,2,4,48).evaluations shouldBe 48
      // Budget error wins over the baseline rule's angular normalization error.
      val error=intercept[IllegalArgumentException] { GaussVonMisesQuadratureComparison(canonical(1,1e8),3,5,2,4,30) }
      error.getMessage should include ("maxEvaluations")
      intercept[ArithmeticException] { GaussVonMisesQuadratureComparison(canonical(1,1e8),3,5,2,64) }
      intercept[IllegalArgumentException] { GaussVonMisesQuadratureComparison(canonical(3)) }
      intercept[IllegalArgumentException] { GaussVonMisesQuadratureComparison(canonical(20),31,32,255,256,1000000) }
      GaussVonMisesQuadratureComparison(canonical()).evaluations shouldBe 2688
      GaussVonMisesQuadratureComparison(canonical(2)).evaluations shouldBe 20352
    }
    "validate factory orders and guard limits" in {
      intercept[IllegalArgumentException] { GaussVonMisesQuadratureComparison(null) }
      for ((g,r) <- Vector((0,2),(3,3),(5,3),(31,33))) {
        intercept[IllegalArgumentException] { GaussVonMisesQuadratureComparison(canonical(),g,r) }
      }
      for ((a,r) <- Vector((1,2),(64,64),(128,64),(255,257))) {
        intercept[IllegalArgumentException] { GaussVonMisesQuadratureComparison(canonical(),1,2,a,r) }
      }
      for (budget <- Vector(0,1000001)) {
        intercept[IllegalArgumentException] { GaussVonMisesQuadratureComparison(canonical(),maxEvaluations=budget) }
      }
    }
    "validate callbacks dimensions and tolerances without consuming callbacks on invalid inputs" in {
      val q=GaussVonMisesQuadratureComparison(canonical(),3,5,2,4); var calls=0
      def f(v: LinearAngular): Double={ calls += 1; 1.0 }
      for (t <- Vector(-1.0,Double.NaN,Double.PositiveInfinity)) {
        intercept[IllegalArgumentException] { q.compare(f,t,1e-4) }
        intercept[IllegalArgumentException] { q.compare(f,1e-6,t) }
      }
      intercept[IllegalArgumentException] { q.compare(f,0,0) }
      intercept[IllegalArgumentException] { q.compare(f,0,1.01) }
      for (d <- Vector(0,10001)) intercept[IllegalArgumentException] { q.compareVector(d)(v => Vector(f(v))) }
      calls shouldBe 0
      intercept[IllegalArgumentException] { q.compare(null) }
      intercept[IllegalArgumentException] { q.compareVector(1)(null) }
      intercept[IllegalArgumentException] { q.compare(_ => Double.NaN) }
      intercept[IllegalArgumentException] { q.compare(_ => Double.PositiveInfinity) }
      intercept[IllegalArgumentException] { q.compareVector(2)(_ => Vector(1.0)) }
      intercept[IllegalArgumentException] { q.compareVector(2)(_ => null) }
    }
    "propagate failures from later rules without poisoning reusable plans" in {
      val q=GaussVonMisesQuadratureComparison(canonical(),3,5,2,4)
      val failure=new IllegalStateException("callback failure"); var calls=0
      intercept[IllegalStateException] {
        q.compare { _ => calls += 1; if(calls > q.baselineRule.nodeCount) throw failure; 1.0 }
      } shouldBe failure
      calls shouldBe q.baselineRule.nodeCount+1
      q.compare(_ => 1).ordersAgree shouldBe true
    }
    "preserve cancellation during construction and evaluation including later rules" in {
      val p=canonical(); val q=GaussVonMisesQuadratureComparison(p,3,5,2,4)
      Thread.currentThread().interrupt()
      try {
        intercept[CancellationException] { GaussVonMisesQuadratureComparison(p) }
        intercept[CancellationException] { q.compare(_ => 1) }
        Thread.currentThread().isInterrupted shouldBe true
      } finally { Thread.interrupted() }
      var calls=0
      try {
        intercept[CancellationException] { q.compare { _ =>
          calls += 1
          if(calls == q.baselineRule.nodeCount+1) Thread.currentThread().interrupt()
          1.0
        } }
        calls shouldBe q.baselineRule.nodeCount+1
        Thread.currentThread().isInterrupted shouldBe true
      } finally { Thread.interrupted() }
    }
    "support concurrent deterministic comparisons without shared accumulation state" in {
      val q=GaussVonMisesQuadratureComparison(canonical(),3,5,2,4)
      val expected=q.compare(nonlinear); val pool=Executors.newFixedThreadPool(4)
      try {
        val tasks=Vector.fill(8)(pool.submit(new Callable[GaussVonMisesQuadratureComparisonResult] {
          def call(): GaussVonMisesQuadratureComparisonResult=q.compare(nonlinear)
        }))
        tasks.foreach { task =>
          val r=task.get(30,TimeUnit.SECONDS)
          r.jointRefined shouldBe expected.jointRefined
          r.withinTolerance shouldBe expected.withinTolerance
          r.maxGaussianChange shouldBe expected.maxGaussianChange
        }
      } finally { pool.shutdownNow() }
    }
  }
}
