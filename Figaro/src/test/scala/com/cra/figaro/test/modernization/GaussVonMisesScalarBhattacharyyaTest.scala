package com.cra.figaro.test.modernization

import com.cra.figaro.library.atomic.continuous.*
import java.util.concurrent.{Callable,CancellationException,Executors,TimeUnit}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class GaussVonMisesScalarBhattacharyyaTest extends AnyWordSpec with Matchers {
  import GaussVonMisesScalarBhattacharyya.{compare,Status}
  private def scalar(mu: Double=0,sd: Double=1,alpha: Double=0,beta: Double=0,gamma: Double=0,k: Double=0) =
    GaussVonMisesDistribution(Vector(mu),Vector(Vector(sd*sd)),alpha,Vector(beta),Vector(Vector(gamma)),k)
  private def checked(p: GaussVonMisesDistribution,q: GaussVonMisesDistribution,expected: Double,tolerance: Double=1e-8) = {
    val r=compare(p,q,tolerance)
    withClue(s"${r.status}; interval=${r.interval}; preprocessing=${r.preprocessingErrorEstimate}: ") {
      r.status shouldBe Status.Estimated
      math.abs(r.distance.get-expected) should be <= tolerance
      r.distance.get.isFinite shouldBe true
      r.distance.get should be >= 0.0
      val (lo,hi)=r.interval.get
      expected should be >= (lo-8*math.ulp(expected))
      expected should be <= (hi+8*math.ulp(expected))
      math.max(r.distance.get-lo,hi-r.distance.get) should be <= tolerance
      r.evaluations should be <= 50000
      r.preprocessingErrorEstimate should be >= 0.0
    }
    r
  }
  "The opt-in scalar positive GVM comparison" should {
    "use zero-evaluation analytic shortcuts without erasing weak coupling" in {
      val p=scalar(k=50)
      val identity=compare(p,p,tolerance=java.lang.Double.MIN_VALUE,maxEvaluations=5)
      identity.method shouldBe "identity"; identity.distance shouldBe Some(0.0)
      identity.interval shouldBe Some((0.0,0.0)); identity.evaluations shouldBe 0
      val gaussian=checked(scalar(),scalar(mu=2),.5)
      gaussian.method shouldBe "gaussian"; gaussian.evaluations shouldBe 0
      // Angular coupling is irrelevant when the other conditional is uniform.
      val uniform=checked(scalar(beta=1e200),scalar(beta=1e200,k=50),1.08705974593665853275)
      uniform.method shouldBe "one-uniform"; uniform.evaluations shouldBe 0
      val constant=checked(p,scalar(alpha=math.Pi,k=50),47.1275755018718045)
      constant.method shouldBe "constant-angular"; constant.evaluations shouldBe 0
      val weak=checked(p,scalar(alpha=math.Pi,beta=1e-5,k=50),47.1275754862468045)
      weak.method shouldBe "positive-integration"; weak.evaluations should be > 0
      constant.distance.get-weak.distance.get should be > 1e-8
      val common=checked(scalar(beta=1e200,k=50),scalar(alpha=math.Pi,beta=1e200,k=50),constant.distance.get)
      common.method shouldBe "constant-angular"
    }
    "handle near-equal variances and enforce limits without making accuracy promises" in {
      val sd=1.0+1e-7
      val expected=math.log1p(math.pow(sd*sd-1,2)/(4*sd*sd))/4
      val near=compare(scalar(),scalar(sd=sd))
      near.distance.get should be > 0.0
      math.abs(near.distance.get-expected) should be < 1e-22
      checked(scalar(),scalar(mu=100),1250).distance.map(d => math.exp(-d)) shouldBe Some(0.0)
      compare(scalar(),scalar(sd=1e-4)).status shouldBe Status.NumericallyUnresolved
      compare(scalar(),scalar(sd=1e-4),tolerance=.01).status shouldBe Status.Estimated
      compare(scalar(),scalar(sd=.999e-4),tolerance=.01).status shouldBe Status.NumericallyUnresolved
      compare(scalar(k=50),scalar(k=50.00001)).status shouldBe Status.UnsupportedRange
      val outside=scalar(k=50.00001)
      compare(outside,outside).status shouldBe Status.UnsupportedRange
      compare(scalar(k=1),scalar(beta=10000,k=1),maxEvaluations=5).status shouldBe Status.BudgetExhausted
      compare(scalar(k=1),scalar(beta=10000.0001,k=1),maxEvaluations=5).status shouldBe Status.UnsupportedRange
      val almost=compare(scalar(k=50),scalar(alpha=math.Pi,beta=1e-5,k=50),tolerance=1e-12)
      almost.status shouldBe Status.NumericallyUnresolved; almost.distance shouldBe None
      var calls=0
      intercept[CancellationException] { compare(scalar(),scalar(),cancelled=() => { calls += 1; true }) }
      calls shouldBe 1
      var checkpoints=0
      val callerFailure=new ArithmeticException("caller predicate failure")
      val propagated=intercept[ArithmeticException] { compare(scalar(k=50),scalar(beta=1,k=50),cancelled=() => {
        checkpoints += 1
        if(checkpoints == 10) throw callerFailure
        false
      }) }
      (propagated eq callerFailure) shouldBe true
    }
    "meet all scalar grid oracles in both directions from physical kernel inputs" in {
      val fixtures=GvmBhattacharyyaReliabilityFixtures.all.filter(_.n == 1)
      fixtures.size shouldBe 84
      var count=0; var maxError=0.0; var maxWork=0
      for(f <- fixtures) {
        val (p,q)=if(f.family == "linear") (scalar(k=f.k),scalar(alpha=f.alpha,beta=f.beta,k=f.k))
          else (scalar(.5,1,.25,.5,.25,f.k),scalar(-.5,.5,-.5,-.25,f.gamma,f.k))
        for((left,right) <- Vector((p,q),(q,p))) {
          withClue(f.id+": ") {
            val r=checked(left,right,f.expected)
            maxError=math.max(maxError,math.abs(r.distance.get-f.expected)); maxWork=math.max(maxWork,r.evaluations)
            count += 1
          }
        }
      }
      count shouldBe 168
      println(s"GVM_SCALAR_POSITIVE comparisons=$count maxError=$maxError maxEvaluations=$maxWork")
    }
    "match high precision unequal-concentration fixtures in both directions" in {
      // 80-digit general Comparison.series(128), using binary64 phase inputs;
      // reproduced by the optional positive-integration research controls.
      val cases=Vector((0.0,1.08705974593665853275,1.08705974593665853275),
        (.125,1.15024633423857051697,1.03907211370285448861),
        (1.0,1.69480912256830085138,.80264777914085005615),
        (10.0,9.94566336394639124473,.83698934834938121170),
        (25.0,24.47351623761949426286,1.33122276572518684799))
      for((k,opposed,curved) <- cases;
        (q,expected) <- Vector((scalar(alpha=math.Pi,beta=1e-5,k=50),opposed),
          (scalar(alpha=.5,beta=.1,gamma=.2,k=50),curved))) {
        checked(scalar(k=k),q,expected); checked(q,scalar(k=k),expected)
      }
    }
    "preserve Gaussian reductions and unit changes with nontrivial preprocessing" in {
      checked(scalar(),scalar(mu=2),.5)
      checked(scalar(),scalar(mu=100),1250)
      checked(scalar(),scalar(mu=1,sd=2),1.0/20+math.log(1.25)/2)
      val expected=.355509912840583167741149
      for(s <- Vector(1.0,1e-100,1e100)) {
        checked(scalar(.3*s,1.1*s,.2,.7,.3,4.5),scalar(-.4*s,.8*s,-.5,-.2,-.15,1.2),expected)
      }
      val gaussian=GaussVonMisesBhattacharyya.compare(scalar(),scalar(2,1.5)).distance.get
      checked(scalar(0,1,.2,.4,.3,4),scalar(2,1.5,1.6,1.5,.675,4),gaussian)
    }
    "recover opposed weak coupling without changing the production method" in {
      val p=scalar(k=50); val q=scalar(alpha=math.Pi,beta=1e-5,k=50)
      val r=checked(p,q,47.12757548624680452352)
      r.radius shouldBe 12.0
      r.gaussianTailBound/math.exp(-r.distance.get) should be < 1e-9
      GaussVonMisesBhattacharyya.compare(p,q).status shouldBe GaussVonMisesBhattacharyyaStatus.NumericallyUnresolved
      for(b <- Vector(5,320,500)) {
        val low=compare(p,q,maxEvaluations=b)
        low.status shouldBe Status.BudgetExhausted; low.distance shouldBe None
        low.evaluations should be <= b
      }
      val alias=compare(scalar(k=50),scalar(beta=2*math.Pi*64,k=50),maxEvaluations=500)
      alias.status shouldBe Status.BudgetExhausted; alias.evaluations shouldBe 0
      val curvedAlias=compare(scalar(k=50),scalar(beta=50,gamma=400,k=50),maxEvaluations=500)
      curvedAlias.status shouldBe Status.BudgetExhausted; curvedAlias.evaluations shouldBe 0
    }
    "refuse inadequate preprocessing precision, extreme contrast and unsupported dimensions" in {
      val tight=compare(scalar(k=50),scalar(alpha=math.Pi,beta=1e-5,k=50),1e-14)
      tight.status shouldBe Status.NumericallyUnresolved; tight.distance shouldBe None
      val commonLarge=compare(scalar(beta=1e8,k=2),scalar(alpha=1,beta=1e8+1e-6,k=2))
      commonLarge.status shouldBe Status.NumericallyUnresolved; commonLarge.distance shouldBe None
      compare(scalar(),scalar(sd=1e-5)).status shouldBe Status.NumericallyUnresolved
      compare(scalar(mu= -1e308),scalar(mu=1e308)).status shouldBe Status.NumericallyUnresolved
      compare(scalar(),scalar(k=50.1)).status shouldBe Status.UnsupportedRange
      compare(scalar(k=1),scalar(beta=1e5,k=1)).status shouldBe Status.UnsupportedRange
      val two=GaussVonMisesDistribution(Vector(0.0,0.0),Vector(Vector(1.0,0.0),Vector(0.0,1.0)),0,
        Vector(0.0,0.0),Vector.fill(2,2)(0.0),0)
      compare(two,two).status shouldBe Status.UnsupportedRange
      intercept[IllegalArgumentException] { compare(null,scalar()) }
      intercept[IllegalArgumentException] { compare(scalar(),two) }
      intercept[IllegalArgumentException] { compare(scalar(),scalar(),cancelled=null) }
      for(t <- Vector(0.0,-1.0,Double.NaN,Double.PositiveInfinity))
        intercept[IllegalArgumentException] { compare(scalar(),scalar(),t) }
      for(b <- Vector(4,200001)) intercept[IllegalArgumentException] { compare(scalar(),scalar(),maxEvaluations=b) }
    }
    "preserve interruption at entry and during work without publishing a result" in {
      val p=scalar(k=50); val q=scalar(beta=1,k=50)
      Thread.currentThread().interrupt()
      try { intercept[CancellationException] { compare(p,q) }; Thread.currentThread().isInterrupted shouldBe true }
      finally { Thread.interrupted() }
      var checkpoints=0
      try {
        intercept[CancellationException] { compare(p,q,cancelled=() => {
          checkpoints += 1
          if(checkpoints == 5000) { Thread.currentThread().interrupt(); true } else false
        }) }
        checkpoints shouldBe 5000; Thread.currentThread().isInterrupted shouldBe true
      } finally { Thread.interrupted() }
    }
    "keep work buffers independent across concurrent calls" in {
      val p=scalar(k=50); val q=scalar(alpha=math.Pi,beta=.1,k=50)
      val expected=compare(p,q)
      val pool=Executors.newFixedThreadPool(4)
      try {
        val tasks=Vector.fill(8)(pool.submit(new Callable[GaussVonMisesScalarBhattacharyya.Result] {
          def call(): GaussVonMisesScalarBhattacharyya.Result = compare(p,q)
        }))
        tasks.foreach(t => t.get(30,TimeUnit.SECONDS) shouldBe expected)
      } finally { pool.shutdownNow() }
    }
  }
}
