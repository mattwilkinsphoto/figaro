package com.cra.figaro.test.modernization

import com.cra.figaro.library.atomic.continuous.*
import java.util.concurrent.{Callable,CancellationException,Executors,TimeUnit}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class GaussVonMisesMutualInformationTest extends AnyWordSpec with Matchers {
  import GaussVonMisesMutualInformation.{compute,Status}
  private def kernel(b: Vector[Double],g: Vector[Vector[Double]],k: Double,alpha: Double=0,scale: Double=1) =
    GaussVonMisesDistribution(Vector.fill(b.size)(23.0),Vector.tabulate(b.size,b.size)((i,j) => if(i == j) scale*scale else 0.0),alpha,b,g,k)
  private def scalar(b: Double=.4,g: Double=0,k: Double=2) = kernel(Vector(b),Vector(Vector(g)),k)
  "GVM linear-angular mutual information" should {
    "cover multiprecision entropy oracles, weak dependence and the 32-dimensional boundary" in {
      for((name,b,g,k,expected) <- GvmMutualInformationFixtures.all) {
        val r=compute(kernel(b,g,k))
        withClue(s"$name: $r: ") {
          r.status shouldBe Status.Estimated
          math.abs(r.value.get-expected) should be <= 1e-8
          expected should be >= r.interval.get._1
          expected should be <= r.interval.get._2
          r.value.get should be >= 0.0
          r.value.get should be <= r.upperBoundEstimate
          math.max(r.value.get-r.interval.get._1,r.interval.get._2-r.value.get) should be <= 1e-8
          r.evaluations should be <= 16384
          if(name == "weak") { r.value.get should be > 0.0; r.method shouldBe "fourier-angular-entropy" }
        }
      }
    }
    "preserve rotation and canonical coordinate invariance" in {
      val b=Vector(.4,-.2); val g=Vector(Vector(.3,.1),Vector(.1,-.4))
      val original=compute(kernel(b,g,5)).value.get
      for(alpha <- Vector(-1e20,0.0,1e20); scale <- Vector(1e-50,1.0,1e50))
        compute(kernel(b,g,5,alpha,scale)).value.get shouldBe original
      // Orthogonal swap/sign changes of latent standard-normal coordinates.
      compute(kernel(Vector(-.2,-.4),Vector(Vector(-.4,-.1),Vector(-.1,.3)),5)).value.get shouldBe (original +- 1e-12)
    }
    "use only exact independence shortcuts and respect work and accuracy limits" in {
      for(p <- Vector(scalar(b=0,k=100),scalar(b=1e200,k=0))) {
        val r=compute(p,tolerance=java.lang.Double.MIN_VALUE,maxHarmonics=1,maxEvaluations=1)
        r.value shouldBe Some(0.0); r.evaluations shouldBe 0; r.method shouldBe "exact-independence"
      }
      compute(scalar(),maxHarmonics=1).status shouldBe Status.BudgetExhausted
      val short=compute(scalar(),maxEvaluations=1)
      short.status shouldBe Status.BudgetExhausted; short.evaluations shouldBe 0; short.value shouldBe None
      compute(scalar(),tolerance=1e-14).status shouldBe Status.NumericallyUnresolved
      compute(scalar(k=50.01)).status shouldBe Status.UnsupportedRange
      compute(scalar(b=1001)).status shouldBe Status.UnsupportedRange
      compute(kernel(Vector.fill(33)(.1),Vector.fill(33)(Vector.fill(33)(0.0)),2)).status shouldBe Status.UnsupportedRange
      for(budget <- Vector(64,128,256,448,1000)) {
        val r=compute(scalar(),maxEvaluations=budget)
        r.evaluations should be <= budget
        r.quadratureErrorEstimate.isNaN shouldBe false
        if(r.status != Status.Estimated) r.value shouldBe None
      }
      val small=compute(scalar(k=1e-6))
      small.status shouldBe Status.Estimated; small.interval.get._1 shouldBe 0.0
    }
    "validate controls and propagate cancellation, interrupts and caller failures" in {
      intercept[IllegalArgumentException] { compute(null) }
      for(t <- Vector(0.0,-1.0,Double.NaN,Double.PositiveInfinity)) intercept[IllegalArgumentException] { compute(scalar(),t) }
      intercept[IllegalArgumentException] { compute(scalar(),maxHarmonics=257) }
      intercept[IllegalArgumentException] { compute(scalar(),maxEvaluations=0) }
      intercept[IllegalArgumentException] { compute(scalar(),cancelled=null) }
      intercept[CancellationException] { compute(scalar(),cancelled=() => true) }
      var checks=0
      intercept[CancellationException] { compute(scalar(),cancelled=() => { checks += 1; checks > 100 }) }
      val failure=new ArithmeticException("caller failure")
      (intercept[ArithmeticException] { compute(scalar(),cancelled=() => throw failure) } eq failure) shouldBe true
      try {
        Thread.currentThread().interrupt()
        intercept[CancellationException] { compute(scalar()) }
        Thread.currentThread().isInterrupted shouldBe true
      } finally { Thread.interrupted() }
    }
    "be deterministic and isolated across concurrent calls" in {
      val pool=Executors.newFixedThreadPool(4)
      try {
        val p=scalar(b=.7,g=.3,k=4); val expected=compute(p)
        val tasks=Vector.fill(12)(pool.submit(new Callable[GaussVonMisesMutualInformation.Result] {
          def call() = compute(p)
        }))
        tasks.foreach(_.get(10,TimeUnit.SECONDS) shouldBe expected)
      } finally { pool.shutdownNow() }
    }
  }
}
