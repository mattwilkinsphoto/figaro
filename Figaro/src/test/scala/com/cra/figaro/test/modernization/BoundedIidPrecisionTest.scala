package com.cra.figaro.test.modernization

import com.cra.figaro.algorithm.sampling.{BoundedIidPrecision as B, DeclaredRegionCoverage as R, InferenceHealth as H}
import com.cra.figaro.util.SamplingRandom
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class BoundedIidPrecisionTest extends AnyWordSpec with Matchers {
  private val fixed=B.Config(0,1,1e-10,maxDraws=1000)
  "Bounded IID stopping" should {
    "retain nonzero uncertainty on constant prefixes and exhaust the exact budget" in {
      var calls=0
      val r=B.run(fixed) { _ => calls+=1; 0.0 }
      calls shouldBe 1000; r.draws shouldBe calls
      r.mean shouldBe 0; r.lower shouldBe 0
      r.upper should be > .09355583763830906 // exact Hoeffding radius oracle
      r.upper should be < .096
      r.errorBound shouldBe r.upper
      r.reason shouldBe B.StopReason.BudgetExhausted
    }
    "reach requested absolute error and replay the private stream" in {
      val c=B.Config(0,1,.08,maxDraws=10000)
      def draw(r: scala.util.Random)=if(r.nextDouble()<.3) 1.0 else 0.0
      val x=B.run(c)(draw)
      x shouldBe B.run(c)(draw)
      x.reason shouldBe B.StopReason.PrecisionReached
      x.errorBound should be <= .08
      x.lower should be <= .3; x.upper should be >= .3
    }
    "check a nonaligned final budget and honor minimum work" in {
      val r=B.run(B.Config(2,2,.001,maxDraws=123,minDraws=101,checkEvery=100))(_ => 2.0)
      r.draws shouldBe 123; r.mean shouldBe 2; r.errorBound shouldBe 0
      r.reason shouldBe B.StopReason.PrecisionReached
    }
    "enclose binary64 extremes without overflowing the running sum" in {
      val m=Double.MaxValue
      val r=B.run(B.Config(-m,m,1,maxDraws=2,minDraws=2)) { rng => m }
      r.mean shouldBe m; r.upper shouldBe m
      r.lower shouldBe -m; r.errorBound shouldBe Double.PositiveInfinity
      r.reason shouldBe B.StopReason.BudgetExhausted
      val tiny=B.run(B.Config(0,java.lang.Double.MIN_VALUE,java.lang.Double.MIN_VALUE,maxDraws=1,minDraws=1))(_ => 0.0)
      tiny.upper shouldBe java.lang.Double.MIN_VALUE
      tiny.errorBound shouldBe java.lang.Double.MIN_VALUE
    }
    "reject invalid configuration and observed contract violations" in {
      intercept[IllegalArgumentException](B.Config(1,0,.1))
      intercept[IllegalArgumentException](B.Config(0,1,.1,alpha=Double.NaN))
      intercept[IllegalArgumentException](B.Config(0,1,.1,maxDraws=1000001))
      for(v <- Vector(-.01,1.01,Double.NaN,Double.PositiveInfinity))
        intercept[IllegalArgumentException](B.run(fixed)(_ => v))
      intercept[IllegalArgumentException](B.run(null)(_ => 0.0))
    }
    "propagate callback failure and cooperative interruption without partial success" in {
      val failure=new IllegalStateException("callback")
      intercept[IllegalStateException](B.run(fixed)(_ => throw failure)) shouldBe failure
      try {
        intercept[InterruptedException](B.run(fixed) { _ => Thread.currentThread().interrupt(); 0.0 })
        Thread.currentThread().isInterrupted shouldBe true
      } finally Thread.interrupted()
    }
    "cover stopped bounded means across a declared held-out seed grid" in {
      // Regression experiment, NOT the proof of coverage. Boundary clipping makes stopping data-dependent.
      val truth=Vector(.3,.5,.001)
      val failures=truth.indices.map { model =>
        (0 until 200).count { seed =>
          val r=B.run(B.Config(0,1,.08,maxDraws=3000,checkEvery=37,seed=900001L+7919L*seed)) { rng =>
            if(model==1) rng.nextDouble() else if(rng.nextDouble()<truth(model)) 1.0 else 0.0
          }
          r.lower>truth(model) || r.upper<truth(model)
        }
      }
      info(s"Held-out simultaneous-bound terminal coverage failures /200 (Bernoulli, uniform, rare): $failures")
      all(failures) should be <= 10
    }
    "isolate concurrent runs and preserve deterministic replay" in {
      val pool=java.util.concurrent.Executors.newFixedThreadPool(2)
      try {
        val tasks=(0 until 4).map(i => pool.submit(new java.util.concurrent.Callable[B.Result] {
          def call(): B.Result=B.run(fixed.copy(seed=i))(_.nextDouble())
        }))
        tasks.zipWithIndex.foreach { (task,i) =>
          task.get(30,java.util.concurrent.TimeUnit.SECONDS) shouldBe B.run(fixed.copy(seed=i))(_.nextDouble())
        }
      } finally { pool.shutdownNow(); pool.awaitTermination(30,java.util.concurrent.TimeUnit.SECONDS) }
    }
  }
  "Declared region coverage" should {
    "report overlapping and unvisited inventory without a fabricated mass bound" in {
      val regions=Vector(R.Region[Double]("all",_ => true),R.Region[Double]("positive",_ > 0),R.Region[Double]("distant",_ > 100))
      val r=R.run(R.Config("uniform [0,1)",draws=100),regions)(_.nextDouble())
      r.occupancies.map(_.count) shouldBe Vector(100,100,0)
      r.status shouldBe R.Status.DeclaredRegionsUnobserved
      r.missBound shouldBe None; r.predicateCalls shouldBe 300
    }
    "match the independently evaluated fixed-budget union bound" in {
      val regions=Vector(R.Region[Double]("left",_ < .5),R.Region[Double]("right",_ >= .5))
      val c=R.Config("uniform [0,1)",draws=100)
      val a=Some(R.MassAssumption(.1,"Each half has probability 0.5, hence at least 0.1"))
      val r=R.run(c,regions,a)(_.nextDouble())
      r shouldBe R.run(c,regions,a)(_.nextDouble())
      r.status shouldBe R.Status.DeclaredInventoryObserved
      r.missBound.get.probabilityUpper should be >= .00005312279777517492110
      r.missBound.get.probabilityUpper should be < .000053122797775175
      r.missBound.get.logUpper shouldBe (math.log(.00005312279777517492110) +- 1e-12)
    }
    "keep positive underflow distinct from a mathematically zero bound" in {
      val regions=Vector(R.Region[Double]("all",_ => true))
      val c=R.Config("test law",draws=2000)
      val tiny=R.run(c,regions,Some(R.MassAssumption(.5,"analytic bound")))(_.nextDouble()).missBound.get
      tiny.probabilityUpper shouldBe java.lang.Double.MIN_VALUE
      tiny.logUpper should be < -1000.0
      val zero=R.run(c,regions,Some(R.MassAssumption(1,"whole support")))(_.nextDouble()).missBound.get
      zero.probabilityUpper shouldBe 0; zero.logUpper shouldBe Double.NegativeInfinity
    }
    "reject duplicate labels, invalid assumptions and excess predicate work before sampling" in {
      val r=R.Region[Double]("x",_ => true)
      intercept[IllegalArgumentException](R.run(R.Config("law"),Vector(r,r))(_ => fail("must not draw")))
      intercept[IllegalArgumentException](R.MassAssumption(0,"unknown"))
      intercept[IllegalArgumentException](R.MassAssumption(.2," "))
      intercept[IllegalArgumentException](R.run(R.Config("law",draws=1000000),Vector.tabulate(11)(i => R.Region[Double](i.toString,_ => true)))(_ => fail("must not draw")))
    }
    "propagate failed and interrupted predicates" in {
      val c=R.Config("law",draws=1)
      intercept[IllegalStateException](R.run(c,Vector(R.Region[Double]("bad",_ => throw new IllegalStateException)))(_ => 0.0))
      try {
        intercept[InterruptedException](R.run(c,Vector(R.Region[Double]("cancel",_ => { Thread.currentThread().interrupt(); true })))(_ => 0.0))
        Thread.currentThread().isInterrupted shouldBe true
      } finally Thread.interrupted()
    }
    "expose a declared distant region even when within-mode MCMC health checks pass" in {
      val chains=Vector.tabulate(4) { i =>
        val rng=SamplingRandom.scalaRandom(321L+i)
        Vector.fill(2000)(rng.nextGaussian())
      }
      H.mcmc(chains).status shouldBe H.Status.ChecksPassed
      val r=R.run(R.Config("local N(0,1), NOT a full mixture posterior"),
        Vector(R.Region[Double]("local",x => math.abs(x)<5),R.Region[Double]("distant",x => x>95 && x<105)))(_.nextGaussian())
      r.occupancies.last.count shouldBe 0
      r.status shouldBe R.Status.DeclaredRegionsUnobserved
      r.missBound shouldBe None
    }
  }
}
