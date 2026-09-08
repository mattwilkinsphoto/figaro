package com.cra.figaro.test.modernization

import com.cra.figaro.library.atomic.continuous.*
import java.util.concurrent.{Callable, CancellationException, Executors, TimeUnit}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class GaussVonMisesQuadratureTest extends AnyWordSpec with Matchers {
  private def canonical(n: Int=2,k: Double=4.5) = GaussVonMisesDistribution(Vector.fill(n)(0.0),
    Vector.tabulate(n,n)((i,j) => if(i == j) 1.0 else 0.0),0,
    Vector.fill(n)(0.0),Vector.fill(n,n)(0.0),k)
  private def coupled = GaussVonMisesDistribution(Vector(1.0,-2.0),
    Vector(Vector(4.0,1.2),Vector(1.2,2.61)),3.05,Vector(0.7,-0.4),
    Vector(Vector(0.3,0.2),Vector(0.2,-0.5)),4.5)

  "Third-order sparse GVM quadrature" should {
    "match independent high-precision equation 5.7 fixtures across concentration regimes" in {
      // tools/gauss_von_mises_quadrature_reference.py: direct 80-digit Bessel deficits.
      val fixtures=Vector((0.0,2.0943951023931954923,0.3333333333333333333),
        (0.1,2.0650508620326450155,0.3221914547252493015),
        (4.5,0.8831030414661891457,0.1638204126253452358),
        (50.0,0.2462010715007462405,0.1666577994559322176),
        (50.0001,0.2462008227390021760,0.1666577994925417215),
        (1000.0,0.05478596356164613604,0.1666666457706360207),
        (1e8,0.0001732050811899004359,0.1666666666666666646))
      fixtures.foreach { (k,eta,w) =>
        val q=canonical(k=k).thirdOrderQuadrature()
        q.nodes(1).angle shouldBe (eta +- 2e-11)
        q.weights(1) shouldBe (w +- 2e-11)
        q.nodes(2).angle shouldBe (-eta +- 2e-11)
      }
    }
    "integrate the canonical exactness class and individual fourth Gaussian powers" in {
      for (k <- Vector(0.0,Double.MinPositiveValue,1e-8,0.1,4.5,50.0,50.0001,1e8)) {
        val q=canonical(3,k).thirdOrderQuadrature(); val r=VonMisesDistribution(0,k).meanResultantLength
        q.expectation(_ => 1) shouldBe (1.0 +- 1e-14)
        q.expectation(p => math.cos(p.angle)) shouldBe (r +- 2e-12)
        val r2=if(k < 1e-8) 0.0 else 1-2*r/k
        q.expectation(p => math.cos(2*p.angle)) shouldBe (r2 +- 3e-12)
        for (i <- 0 until 3; j <- 0 until 3) {
          q.expectation(p => p.linear(i)*p.linear(j)) shouldBe ((if(i == j) 1.0 else 0.0) +- 2e-14)
          q.expectation(p => p.linear(i)*p.linear(i)*p.linear(j)) shouldBe (0.0 +- 1e-14)
        }
        q.expectation(p => math.pow(p.linear(0),4)) shouldBe (3.0 +- 1e-14)
        q.expectation(p => math.sin(p.linear.sum)*math.cos(p.angle)) shouldBe (0.0 +- 1e-14)
        q.expectation(p => math.exp(-p.linear.map(x => x*x).sum)*math.sin(p.angle)) shouldBe (0.0 +- 1e-14)
      }
    }
    "preserve physical Gaussian means and covariance with nontrivial coupling and correlated units" in {
      val p=coupled; val q=p.thirdOrderQuadrature()
      for (i <- 0 until 2; j <- 0 until 2) {
        q.expectation(v => v.linear(i)) shouldBe (p.mean(i) +- 1e-14)
        q.expectation(v => (v.linear(i)-p.mean(i))*(v.linear(j)-p.mean(j))) shouldBe
          (p.covariance(i)(j) +- 1e-13)
      }
      // Mapping is canonical-to-physical, not an independent angular rule at every x.
      q.nodes.foreach { v =>
        val residual=p.canonicalResidual(v)
        p.fromCanonical(residual).linear.zip(v.linear).foreach((a,b) => a shouldBe (b +- 1e-13))
      }
    }
    "keep concentrated angular nodes resolved instead of cancelling them to zero" in {
      val q=canonical(k=1e8).thirdOrderQuadrature()
      q.nodes(1).angle should be > 1e-4
      q.expectation(p => math.pow(math.sin(p.angle/2),2)) shouldBe (2.50000000625e-9 +- 1e-21)
      q.expectation(p => math.pow(math.sin(p.angle/2),4)) shouldBe (1.875000009375e-17 +- 1e-28)
    }
    "use exactly 2n+3 callbacks in stable node order and allow explicit node reuse" in {
      val q=canonical(6).thirdOrderQuadrature(); q.nodeCount shouldBe 15
      var visited=Vector.empty[LinearAngular]
      q.expectation { p => visited :+= p; 1.0 } shouldBe (1.0 +- 1e-14)
      visited shouldBe q.nodes
      q.weights.size shouldBe q.nodeCount
      q.absoluteWeightSum shouldBe (q.weights.map(math.abs).sum +- 1e-14)
    }
    "evaluate a vector callback once per node rather than once per output" in {
      val q=coupled.thirdOrderQuadrature(); var calls=0
      val result=q.expectationVector(3) { p => calls += 1; Vector(p.linear(0),p.linear(1),p.linear(0)*p.linear(0)) }
      calls shouldBe 7
      result.zip(Vector(1.0,-2.0,5.0)).foreach((a,b) => a shouldBe (b +- 1e-13))
    }
    "expose failures outside its exactness class instead of claiming universal third-order accuracy" in {
      val q=canonical(2,0).thirdOrderQuadrature()
      q.expectation(p => p.linear(0)*p.linear(0)*p.linear(1)*p.linear(1)) shouldBe 0.0 // truth: 1
      q.expectation(p => p.linear(0)*p.linear(0)*math.cos(p.angle)) shouldBe (1.0 +- 1e-14) // truth: 0
      val six=canonical(6).thirdOrderQuadrature()
      six.hasNegativeWeights shouldBe true
      six.expectation(p => math.exp(-p.linear.map(x => x*x).sum)) shouldBe (-1+2*math.exp(-3) +- 1e-14)
      // True expectation of this positive function is 1/27; never clip the negative approximation.
    }
    "compare a weakly coupled angular expectation with analytic moments and seeded Monte Carlo" in {
      val p=GaussVonMisesDistribution(Vector(0.0),Vector(Vector(1.0)),0.3,Vector(0.1),Vector(Vector(0.05)),4.5)
      val exact=p.moments.meanCos; val approx=p.thirdOrderQuadrature().expectation(v => math.cos(v.angle))
      approx shouldBe (exact +- 0.002)
      val rng=new scala.util.Random(44917L); val size=80000
      var sum=0.0; var sumSquares=0.0
      for (_ <- 0 until size) { val v=math.cos(p.sample(rng).angle); sum += v; sumSquares += v*v }
      val mean=sum/size; val variance=(sumSquares-sum*sum/size)/(size-1)
      mean shouldBe (exact +- 5*math.sqrt(variance/size))
    }
    "reject invalid budgets and malformed callbacks without returning a partial result" in {
      intercept[IllegalArgumentException] { GaussVonMisesQuadrature.thirdOrder(null) }
      for (limit <- Vector(4,6,10002)) intercept[IllegalArgumentException] { canonical().thirdOrderQuadrature(limit) }
      val q=canonical().thirdOrderQuadrature()
      intercept[IllegalArgumentException] { q.expectation(null) }
      intercept[IllegalArgumentException] { q.expectation(_ => Double.NaN) }
      intercept[IllegalArgumentException] { q.expectation(_ => Double.PositiveInfinity) }
      intercept[IllegalArgumentException] { q.expectationVector(2)(_ => null) }
      intercept[IllegalArgumentException] { q.expectationVector(2)(_ => Vector(1.0)) }
      for (n <- Vector(0,10001)) intercept[IllegalArgumentException] { q.expectationVector(n)(_ => Vector(1.0)) }
      val failure=new IllegalStateException("callback failed")
      intercept[IllegalStateException] { q.expectation(_ => throw failure) } shouldBe failure
      q.expectation(_ => 1) shouldBe (1.0 +- 1e-14)
    }
    "fail explicitly on construction and weighted-sum overflow" in {
      val p=GaussVonMisesDistribution(Vector(0.0),Vector(Vector(1.0)),0,Vector(Double.MaxValue),Vector(Vector(0.0)),0)
      intercept[ArithmeticException] { p.thirdOrderQuadrature() }
      val q=canonical(12).thirdOrderQuadrature()
      intercept[ArithmeticException] { q.expectation(_ => Double.MaxValue) }
    }
    "honor interruption at construction, entry and after a callback without clearing the flag" in {
      val p=canonical(); val q=p.thirdOrderQuadrature()
      Thread.currentThread().interrupt()
      try {
        intercept[CancellationException] { p.thirdOrderQuadrature() }
        intercept[CancellationException] { q.expectation(_ => 1) }
        Thread.currentThread().isInterrupted shouldBe true
      } finally { Thread.interrupted() }
      try {
        intercept[CancellationException] { q.expectation { _ => Thread.currentThread().interrupt(); 1.0 } }
        Thread.currentThread().isInterrupted shouldBe true
      } finally { Thread.interrupted() }
    }
    "support concurrent independent calls and retain immutable results" in {
      val q=coupled.thirdOrderQuadrature(); val saved=q.nodes
      val pool=Executors.newFixedThreadPool(4)
      try {
        val tasks=Vector.fill(8)(pool.submit(new Callable[Double] { def call(): Double=q.expectation(_.linear(0)) }))
        tasks.foreach(_.get(30,TimeUnit.SECONDS) shouldBe (1.0 +- 1e-14))
      } finally { pool.shutdownNow() }
      q.nodes shouldBe saved
    }
  }
}
