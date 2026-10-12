package com.cra.figaro.test.modernization

import com.cra.figaro.algorithm.filtering.{BoundedParticleBudget as A, BoundedParticleFilter as B}
import com.cra.figaro.util.{random, withRandomSeed}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class BoundedParticleBudgetTest extends AnyWordSpec with Matchers {
  private def binomial(n: Int, k: Int, p: Double): Double = {
    val choose = (1 to k).foldLeft(BigInt(1))((v,i)=>v*(n-i+1)/i)
    choose.toDouble * math.pow(p,k) * math.pow(1-p,n-k)
  }
  "The independent-pilot budget adviser" should {
    "return explicit inconclusive and insufficient outcomes without increasing caps" in {
      val zero=A.advise(A.Pilot(100,0),A.Request(1000))
      zero.status shouldBe A.Status.NoPilotSuccesses
      zero.plan shouldBe None
      zero.interval.lower shouldBe 0.0
      zero.interval.upper shouldBe (-math.expm1(math.log(.025)/100) +- 3e-14)
      val small=A.advise(A.Pilot(1000,1),A.Request(100))
      small.status shouldBe A.Status.InsufficientBudget
      small.plan shouldBe None
      A.advise(A.Pilot(10,10),A.Request(2,3)).status shouldBe A.Status.InsufficientBudget
      val all=A.advise(A.Pilot(100,100),A.Request(1000))
      all.interval.upper shouldBe 1.0
      all.interval.lower shouldBe (math.pow(.025,.01) +- 3e-14)
      all.interval.lower should be < 1.0
    }
    "meet nominal coverage by exhaustive small-count summation rather than random success" in {
      for (n <- 1 to 40; confidence <- Vector(.8,.95,.999)) {
        val intervals=(0 to n).map(k=>A.advise(A.Pilot(n,k),A.Request(1000,1,confidence)).interval)
        for (p <- Vector(0.0,.001,.01,.05,.1,.25,.5,.9,.99,.999,1.0)) {
          val coverage=(0 to n).filter(k=>intervals(k).lower <= p && p <= intervals(k).upper)
            .map(k=>binomial(n,k,p)).sum
          withClue(s"n=$n p=$p confidence=$confidence: ") {coverage should be >= (confidence-1e-12)}
        }
      }
    }
    "select a minimal feasible cap and preserve explicit seed and target semantics" in {
      for (k <- Vector(10,100,500,990,1000)) {
        val a=A.advise(A.Pilot(1000,k),A.Request(100000,20))
        a.status shouldBe A.Status.Ready
        val plan=a.plan.get
        plan.targetSuccesses shouldBe 21
        plan.maxCandidates should be <= a.request.maxCandidates
        A.miss(plan.maxCandidates,21,a.interval.lower) should be <= .05
        A.miss(plan.maxCandidates-1,21,a.interval.lower) should be > .05
        plan.expectedCandidates should be >= plan.minCandidates.toDouble
        plan.expectedCandidates should be <= plan.maxCandidates.toDouble
        plan.config(202).seed shouldBe 202L
      }
      val plans=Vector(20,100,500,990).map(k=>A.advise(A.Pilot(1000,k),A.Request(100000,20)).plan.get.maxCandidates)
      plans.sliding(2).foreach(pair=>pair.head should be >= pair.last)
    }
    "match expected work to independently enumerated finite stopping paths" in {
      for (max <- 2 to 9; min <- 2 to max; target <- 2 to max; p <- Vector(0.0,.01,.2,.7,1.0)) {
        var expected=0.0
        for (bits <- 0 until (1 << max)) {
          var attempted=0; var successes=0
          while(attempted < min || (attempted < max && successes < target)) {
            if ((bits & (1 << attempted)) != 0) successes+=1
            attempted+=1
          }
          val k=Integer.bitCount(bits)
          expected+=attempted*math.pow(p,k)*math.pow(1-p,max-k)
        }
        A.expectedWork(min,max,target,p) shouldBe (expected +- 2e-11)
      }
    }
    "keep pilot cost fixed and its RNG independent of the ambient stream" in {
      var calls=0
      val a=A.runPilot(300,101)(()=>{calls+=1;random.nextDouble()<.3})
      calls shouldBe 300
      a shouldBe A.runPilot(300,101)(()=>random.nextDouble()<.3)
      val baseline=withRandomSeed(123){Vector.fill(2)(random.nextDouble())}
      val actual=withRandomSeed(123){
        val first=random.nextDouble()
        A.runPilot(100,55)(()=>random.nextBoolean())
        Vector(first,random.nextDouble())
      }
      actual shouldBe baseline
      var allCalls=0
      A.runPilot(40,1)(()=>{allCalls+=1;true}) shouldBe A.Pilot(40,40)
      allCalls shouldBe 40
    }
    "propagate callback errors and interruption without returning a selected pilot" in {
      val failure=new IllegalStateException("callback")
      val caught=intercept[IllegalStateException]{A.runPilot(10,1)(()=>throw failure)}
      (caught eq failure) shouldBe true
      try {
        intercept[InterruptedException]{A.runPilot(10,1)(()=>{Thread.currentThread().interrupt();true})}
        Thread.currentThread().isInterrupted shouldBe true
      } finally Thread.interrupted()
      withRandomSeed(99){
        val first=random.nextInt()
        intercept[IllegalStateException]{A.runPilot(3,1)(()=>throw failure)}
        val second=random.nextInt()
        Vector(first,second) shouldBe withRandomSeed(99){Vector(random.nextInt(),random.nextInt())}
      }
    }
    "refuse invalid inputs and retain finite behavior at supported numerical boundaries" in {
      intercept[IllegalArgumentException]{A.Pilot(0,0)}
      intercept[IllegalArgumentException]{A.Pilot(1000001,1)}
      intercept[IllegalArgumentException]{A.Request(100,confidence=Double.NaN)}
      intercept[IllegalArgumentException]{A.Request(100,missProbability=0)}
      for(k <- Vector(0,1,500000,999999,1000000); c <- Vector(.8,.999)) {
        val a=A.advise(A.Pilot(1000000,k),A.Request(1000000,1,c,.001))
        a.interval.lower.isFinite shouldBe true
        a.interval.upper.isFinite shouldBe true
        a.interval.lower should be <= a.pilot.estimate
        a.interval.upper should be >= a.pilot.estimate
        a.tailAtHardCap.isFinite shouldBe true
      }
    }
    "run a frozen advised production configuration without consuming its pilot outcomes" in {
      val a=A.advise(A.runPilot(1000,11)(()=>random.nextDouble()<.2),A.Request(10000,20))
      val config=a.plan.get.config(202)
      val model=B.Model(()=>random.nextDouble(),(_:Double)=>random.nextDouble())
      def run()={val f=B(model,config);try f.start(_<.2) finally f.close()}
      val x=run(); val y=run()
      x shouldBe y
      x.report.attempts should be <= config.maxCandidates
      config shouldBe a.plan.get.config(202)
    }
  }
}
