package com.cra.figaro.test.modernization

import com.cra.figaro.library.atomic.continuous.*
import java.util.concurrent.{Callable, CancellationException, Executors, TimeUnit}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class GaussVonMisesTensorQuadratureTest extends AnyWordSpec with Matchers {
  private def canonical(n: Int=2,k: Double=4.5) = GaussVonMisesDistribution(Vector.fill(n)(0.0),
    Vector.tabulate(n,n)((i,j) => if(i == j) 1.0 else 0.0),0,
    Vector.fill(n)(0.0),Vector.fill(n,n)(0.0),k)

  "Positive-weight GVM tensor quadrature" should {
    "integrate Gaussian cross moments that the sparse rule misses" in {
      val p=canonical(); val q=p.tensorQuadrature(3,64)
      q.expectation(_ => 1) shouldBe (1.0 +- 1e-14)
      q.expectation(v => math.pow(v.linear(0),4)*math.pow(v.linear(1),4)) shouldBe (9.0 +- 1e-12)
      q.expectation(v => v.linear(0)*v.linear(0)*v.linear(1)*v.linear(1)) shouldBe (1.0 +- 1e-13)
      q.expectation(v => v.linear(0)*v.linear(0)*math.cos(v.angle)) shouldBe
        (VonMisesDistribution(0,4.5).meanResultantLength +- 1e-12)
      val maximumOrders=canonical(1,1e8).tensorQuadrature(32,256)
      maximumOrders.nodeCount shouldBe 8192
      maximumOrders.expectation(v => v.linear(0)*v.linear(0)) shouldBe (1.0 +- 1e-12)
    }
    "resolve circular moments from uniform to highly concentrated without missing the peak" in {
      for (k <- Vector(0.0,Double.MinPositiveValue,0.1,4.5,50.0,1000.0,1e8)) {
        val q=canonical(1,k).tensorQuadrature(1,64)
        q.angularMassEstimate shouldBe (1.0 +- 1e-10)
        q.angularTruncationBound should be >= 0.0
        q.angularTruncationBound should be < 1e-12
        q.expectation(v => math.cos(v.angle)) shouldBe (VonMisesDistribution(0,k).meanResultantLength +- 1e-10)
        q.expectation(v => math.sin(v.angle)) shouldBe (0.0 +- 1e-14)
      }
      canonical(1,1e8).tensorQuadrature(1,64).expectation(v => math.pow(math.sin(v.angle/2),2)) shouldBe
        (2.50000000625e-9 +- 1e-19)
    }
    "converge toward independent analytic mixed moments for a coupled physical kernel" in {
      val p=GaussVonMisesDistribution(Vector(1.0),Vector(Vector(2.25)),0.3,Vector(0.7),Vector(Vector(0.4)),4.5)
      val m=p.moments; val q=p.tensorQuadrature(25,96)
      val result=q.expectationVector(4)(v => Vector(math.cos(v.angle),math.sin(v.angle),v.linear(0)*math.cos(v.angle),
        v.linear(0)*v.linear(0)*math.sin(v.angle)))
      result.zip(Vector(m.meanCos,m.meanSin,m.linearCos.head,m.linearLinearSin.head.head))
        .foreach((a,b) => a shouldBe (b +- 2e-9))
    }
    "stay nonnegative on the smooth six-dimensional sparse counterexample without implying accuracy" in {
      val p=canonical(6,0)
      def f(v: LinearAngular): Double=math.exp(-v.linear.map(x => x*x).sum)
      p.thirdOrderQuadrature().expectation(f) should be < 0.0
      val value=p.tensorQuadrature(3,2).expectation(f)
      value should be > 0.0; value should be <= 1.0
      math.abs(value-1.0/27) should be > 0.01 // Positive does not mean accurate.
    }
    "reduce Gaussian-order error on an independently integrable nonlinear expectation" in {
      val p=canonical(2,0)
      def f(v: LinearAngular): Double=math.exp(-v.linear.map(x => x*x).sum)
      val errors=Vector(3,7,15,25).map(order => math.abs(p.tensorQuadrature(order,2).expectation(f)-1.0/3))
      errors.sliding(2).foreach(pair => pair(1) should be < pair(0))
      errors.last should be < 1e-7
    }
    "require independent angular refinement even when the constant-mass check passes" in {
      val p=canonical(1,0)
      val coarse=p.tensorQuadrature(1,8); val fine=p.tensorQuadrature(1,128)
      coarse.angularMassEstimate shouldBe (1.0 +- 1e-14)
      math.abs(coarse.expectation(v => math.cos(16*v.angle))) should be > 0.01
      fine.expectation(v => math.cos(16*v.angle)) shouldBe (0.0 +- 1e-12)
      intercept[ArithmeticException] { canonical(1,1e8).tensorQuadrature(1,2) }
    }
    "stream exactly the declared callback count and share work across vector outputs" in {
      val q=canonical().tensorQuadrature(3,64); var calls=0
      val result=q.expectationVector(2) { v => calls += 1; Vector(v.linear(0),v.linear(0)*v.linear(0)) }
      calls shouldBe 576; q.nodeCount shouldBe calls
      q.gaussianOrder shouldBe 3; q.angularOrder shouldBe 64
      result.head shouldBe (0.0 +- 1e-14); result(1) shouldBe (1.0 +- 1e-13)
    }
    "reject exponential budgets before building rules and validate all public inputs" in {
      intercept[IllegalArgumentException] { GaussVonMisesTensorQuadrature(null) }
      val p=canonical(6)
      intercept[IllegalArgumentException] { p.tensorQuadrature() } // 5^6*64 exceeds default.
      intercept[IllegalArgumentException] { p.tensorQuadrature(32,256,1000000) }
      for (g <- Vector(0,33)) intercept[IllegalArgumentException] { p.tensorQuadrature(g) }
      for (a <- Vector(1,257)) intercept[IllegalArgumentException] { p.tensorQuadrature(1,a) }
      for (b <- Vector(0,1000001)) intercept[IllegalArgumentException] { p.tensorQuadrature(1,2,b) }
      val q=canonical(1,0).tensorQuadrature(1,2)
      intercept[IllegalArgumentException] { q.expectation(null) }
      intercept[IllegalArgumentException] { q.expectation(_ => Double.NaN) }
      intercept[IllegalArgumentException] { q.expectationVector(2)(_ => null) }
      intercept[IllegalArgumentException] { q.expectationVector(2)(_ => Vector(1.0)) }
      intercept[IllegalArgumentException] { q.expectationVector(0)(_ => Vector.empty) }
      intercept[IllegalArgumentException] { q.expectationVector(10001)(_ => Vector.empty) }
    }
    "propagate callback failures and defer physical mapping failures to evaluation" in {
      val q=canonical().tensorQuadrature()
      val failure=new IllegalStateException("test callback")
      intercept[IllegalStateException] { q.expectation(_ => throw failure) } shouldBe failure
      q.expectation(_ => 1) shouldBe (1.0 +- 1e-13)
      val p=GaussVonMisesDistribution(Vector(0.0),Vector(Vector(1.0)),0,Vector(Double.MaxValue),Vector(Vector(0.0)),0)
      val extreme=p.tensorQuadrature(3,2)
      intercept[ArithmeticException] { extreme.expectation(_ => 1) }
    }
    "preserve cancellation at construction and callback boundaries" in {
      val p=canonical(); val q=p.tensorQuadrature()
      Thread.currentThread().interrupt()
      try {
        intercept[CancellationException] { p.tensorQuadrature() }
        intercept[CancellationException] { q.expectation(_ => 1) }
        Thread.currentThread().isInterrupted shouldBe true
      } finally { Thread.interrupted() }
      try {
        intercept[CancellationException] { q.expectation { _ => Thread.currentThread().interrupt(); 1.0 } }
        Thread.currentThread().isInterrupted shouldBe true
      } finally { Thread.interrupted() }
    }
    "support concurrent deterministic calls with isolated accumulators" in {
      val q=canonical().tensorQuadrature(3,32)
      val expected=q.expectation(v => v.linear(0)*v.linear(0))
      val pool=Executors.newFixedThreadPool(4)
      try {
        val tasks=Vector.fill(8)(pool.submit(new Callable[Double] { def call(): Double=q.expectation(v => v.linear(0)*v.linear(0)) }))
        tasks.foreach(_.get(30,TimeUnit.SECONDS) shouldBe expected)
      } finally { pool.shutdownNow() }
    }
  }
}
