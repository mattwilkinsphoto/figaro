package com.cra.figaro.test.modernization

import com.cra.figaro.library.atomic.*
import com.cra.figaro.library.atomic.continuous.*
import com.cra.figaro.library.atomic.discrete.*
import java.util.concurrent.{CancellationException,Callable,Executors,TimeUnit}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class CommonInformationMetricsTest extends AnyWordSpec with Matchers {
  import InformationMetricStatus.*
  private def checked(r: InformationMetricResult,expected: Double,tol: Double): Unit = withClue(r.toString+": ") {
    Set(Analytic,Estimated) should contain(r.status)
    r.value.get should be >= 0.0
    math.abs(r.value.get-expected) should be <= tol
    math.abs(r.value.get-expected) should be <= (r.errorEstimate+1e-12)
    r.errorEstimate should be <= tol
  }
  "Common-family information metrics" should {
    "match independent density-integral scalar oracles" in {
      for((p,q,kl,bd) <- CommonMetricFixtures.scalar) withClue(s"$p versus $q: ") {
        val k=ScalarDivergence.kl(p,q); val b=ScalarDivergence.bhattacharyya(p,q)
        checked(k,kl,1e-6); checked(b,bd,1e-6)
        b.evaluations should be <= 16384
        ScalarDivergence.bhattacharyya(q,p).value.get shouldBe (b.value.get +- 1e-12)
      }
    }
    "match infinite count sums and finite hypergeometric sums" in {
      for((p,q,kl,bd) <- CommonMetricFixtures.count) {
        checked(CountDivergence.kl(p,q),kl,1e-8)
        val b=CountDivergence.bhattacharyya(p,q); checked(b,bd,1e-8)
        b.evaluations should be <= 10000
        CountDivergence.bhattacharyya(q,p).value.get shouldBe (b.value.get +- 1e-12)
      }
      val p=NegativeBinomialDistribution(2.5,.4); val q=NegativeBinomialDistribution(3.2,.6)
      for(t <- Vector(1e-5,1e-7,1e-9)) checked(CountDivergence.kl(p,q,t),.2796974092865718,t)
      CountDivergence.kl(p,q,maxTerms=2).status shouldBe BudgetExhausted
    }
    "distinguish identity, support infinity and numeric refusal" in {
      val p=StudentTDistribution(5)
      ScalarDivergence.kl(p,p,tolerance=java.lang.Double.MIN_VALUE,maxEvaluations=1).value shouldBe Some(0.0)
      ScalarDivergence.kl(p,StudentTDistribution(8,.4,1.2),maxEvaluations=1).status shouldBe BudgetExhausted
      ScalarDivergence.kl(StudentTDistribution(.1),StudentTDistribution(.2)).status shouldBe Unsupported
      ScalarDivergence.kl(p,LaplaceDistribution(0,1)).status shouldBe Unsupported
      ScalarDivergence.kl(LogNormalDistribution(0,1),LogNormalDistribution(.1,1),tolerance=1e-15).status shouldBe NumericallyUnresolved
      val wide=TriangularDistribution(-1,0,1); val narrow=TriangularDistribution(0,.5,1)
      ScalarDivergence.kl(wide,narrow).status shouldBe Infinite
      ScalarDivergence.bhattacharyya(wide,TriangularDistribution(2,3,4)).value shouldBe Some(Double.PositiveInfinity)
      val pCount=NegativeBinomialDistribution(2,.4); val point=NegativeBinomialDistribution(3,1)
      CountDivergence.kl(pCount,point).status shouldBe Infinite
      checked(CountDivergence.kl(point,pCount),-2*math.log(.4),1e-8)
      checked(CountDivergence.bhattacharyya(point,pCount),-math.log(.4),1e-8)
      CountDivergence.kl(HypergeometricDistribution(20,7,5),HypergeometricDistribution(20,0,5)).status shouldBe Infinite
      CountDivergence.bhattacharyya(HypergeometricDistribution(20,20,5),HypergeometricDistribution(20,0,5)).status shouldBe Infinite
      CountDivergence.kl(pCount,HypergeometricDistribution(20,7,5)).status shouldBe Unsupported
    }
    "recover Gaussian-derived lognormal, exponential and common-shape transformations" in {
      // Same log variance: KL = squared log-coordinate Mahalanobis /2, Bhat = /8.
      checked(ScalarDivergence.kl(LogNormalDistribution(0,2),LogNormalDistribution(3,2)),9.0/8,1e-6)
      checked(ScalarDivergence.bhattacharyya(LogNormalDistribution(0,2),LogNormalDistribution(3,2)),9.0/32,1e-6)
      checked(ScalarDivergence.kl(WeibullDistribution(1,2),WeibullDistribution(1,3)),math.log(1.5)+2.0/3-1,1e-6)
      val a=ScalarDivergence.kl(StudentTDistribution(5),StudentTDistribution(8,.4,1.2))
      checked(ScalarDivergence.kl(StudentTDistribution(5,10,3),StudentTDistribution(8,11.2,3.6)),a.value.get,1e-6)
      val near=ScalarDivergence.kl(LogNormalDistribution(0,1),LogNormalDistribution(1e-5,1))
      near.value.get should be > 0.0
      math.abs(near.value.get-5e-11) should be < 1e-15
    }
    "validate controls and preserve cancellation and arbitrary caller exceptions" in {
      val p=StudentTDistribution(5); val q=StudentTDistribution(8,.4,1.2)
      intercept[IllegalArgumentException] { ScalarDivergence.kl(null,q) }
      intercept[IllegalArgumentException] { ScalarDivergence.kl(p,q,tolerance=0) }
      intercept[IllegalArgumentException] { ScalarDivergence.kl(p,q,maxEvaluations=0) }
      intercept[IllegalArgumentException] { CountDivergence.kl(NegativeBinomialDistribution(2,.4),NegativeBinomialDistribution(3,.5),maxTerms=0) }
      var checkpoints=0
      intercept[CancellationException] { ScalarDivergence.kl(p,q,cancelled=() => { checkpoints += 1; checkpoints > 20 }) }
      val failure=new ArithmeticException("caller-owned failure")
      (intercept[ArithmeticException] { ScalarDivergence.kl(p,q,cancelled=() => throw failure) } eq failure) shouldBe true
      intercept[CancellationException] { CountDivergence.kl(NegativeBinomialDistribution(2,.4),NegativeBinomialDistribution(3,.5),cancelled=() => true) }
      try { Thread.currentThread().interrupt(); intercept[CancellationException] { ScalarDivergence.kl(p,q) }; Thread.currentThread().isInterrupted shouldBe true }
      finally { Thread.interrupted() }
    }
    "isolate numerical work across concurrent calls" in {
      val pool=Executors.newFixedThreadPool(3)
      try {
        val p=StudentTDistribution(5); val q=StudentTDistribution(8,.4,1.2); val expected=ScalarDivergence.kl(p,q)
        val tasks=Vector.fill(6)(pool.submit(new Callable[InformationMetricResult] { def call() = ScalarDivergence.kl(p,q) }))
        tasks.foreach(_.get(30,TimeUnit.SECONDS) shouldBe expected)
      } finally pool.shutdownNow()
    }
  }
  "Finite-table information measures" should {
    "handle zeros, identity, support mismatch and rare overlap without underflow" in {
      val p=Vector(.4,.6); val q=Vector(.5,.5)
      checked(DiscreteInformation.kl(p,q),.4*math.log(.8)+.6*math.log(1.2),1e-8)
      checked(DiscreteInformation.bhattacharyya(p,q),-math.log(math.sqrt(.2)+math.sqrt(.3)),1e-8)
      DiscreteInformation.kl(p,p).value shouldBe Some(0.0)
      DiscreteInformation.kl(Vector(0.0,1.0),Vector(1.0,0.0)).status shouldBe Infinite
      DiscreteInformation.bhattacharyya(Vector(0.0,1.0),Vector(1.0,0.0)).status shouldBe Infinite
      checked(DiscreteInformation.bhattacharyya(Vector(1.0,1e-300,0.0),Vector(0.0,1e-300,1.0)),-math.log(1e-300),1e-8)
    }
    "compute MI from a joint table, not from its two separate marginals" in {
      val independent=Vector(Vector(.12,.28),Vector(.18,.42))
      checked(DiscreteInformation.mutualInformation(independent),0,1e-8)
      checked(DiscreteInformation.mutualInformation(Vector(Vector(.5,0.0),Vector(0.0,.5))),math.log(2),1e-8)
      val joint=Vector(Vector(.1,.2,0.0),Vector(.3,.1,.3))
      val transpose=joint.transpose
      DiscreteInformation.mutualInformation(joint).value.get shouldBe (DiscreteInformation.mutualInformation(transpose).value.get +- 1e-14)
      val rows=joint.map(_.sum); val cols=joint.transpose.map(_.sum)
      val product=rows.flatMap(r => cols.map(_*r))
      DiscreteInformation.mutualInformation(joint).value.get shouldBe (DiscreteInformation.kl(joint.flatten,product).value.get +- 1e-14)
    }
    "reject malformed, oversized and non-probability tables" in {
      intercept[IllegalArgumentException] { DiscreteInformation.kl(Vector(.5),Vector(1.0)) }
      intercept[IllegalArgumentException] { DiscreteInformation.kl(Vector(-.1,1.1),Vector(.5,.5)) }
      intercept[IllegalArgumentException] { DiscreteInformation.mutualInformation(Vector(Vector(.5),Vector(.2,.3))) }
      intercept[IllegalArgumentException] { DiscreteInformation.mutualInformation(Vector.empty) }
      intercept[IllegalArgumentException] { DiscreteInformation.kl(Vector.fill(100001)(1.0/100001),Vector(1.0)) }
      intercept[IllegalArgumentException] { DiscreteInformation.kl(Vector(1.0),Vector(1.0),tolerance=Double.NaN) }
    }
  }
}
