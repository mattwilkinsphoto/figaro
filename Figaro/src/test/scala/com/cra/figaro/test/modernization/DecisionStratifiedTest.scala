package com.cra.figaro.test.modernization
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import DecisionStratifiedPrototype.*

class DecisionStratifiedTest extends AnyWordSpec with Matchers {
  "Decision-focused stratification" should {
    "resolve equal weights and exact ties on an exhaustive midpoint grid" in {
      for(n<-2 to 32){val t=new Table(Vector.fill(n)(0.0))
        for(j<-0 until n; k<-0 until 32)t.choose(j,k/32.0) shouldBe j}
    }
    "exclude true zeros and preserve a zero coordinate" in {
      val t=new Table(Vector(Double.NegativeInfinity,-1000.0,0.0,Double.NegativeInfinity))
      t.choose(0,0.0) shouldBe 1
      t.choose(3,math.nextDown(1.0)) shouldBe 2
    }
    "resolve dominant-weight and underflow populations without global CDF intervals" in {
      for(logs<-Vector(Vector(0.0,-40.0),Vector(0.0,-1000.0),Vector(-1000.0,0.0))){
        val t=new Table(logs);for(j<-0 to 1)t.choose(j,.5) shouldBe logs.indexOf(0.0)}
    }
    "refine boundary-adjacent decisions and report exhausted budgets" in {
      val logs=Vector(0.0, -1e-17)
      val t=new Table(logs)
      t.choose(1,0.0) shouldBe 0
      t.refinedDecisions shouldBe 1L
      t.maxPrecision shouldBe 40
      intercept[Unresolved](new Table(logs,Vector.empty).choose(1,0.0))
    }
    "keep exact equal-log symbolic ties distinct from approximate equality" in {
      val t=new Table(Vector(0.0,-1.0,0.0,-1.0))
      t.choose(2,0.0) shouldBe 2
      t.exactTies should be > 0L
    }
    "refuse a genuinely unresolved comparison at the full precision limit" in {
      val t=new Table(Vector(0.0,-1e-200))
      intercept[Unresolved](t.choose(1,0.0))
      t.maxPrecision shouldBe 160
      t.tablesBuilt shouldBe 3
    }
    "preserve offset invariance and finite-grid conditional offspring counts" in {
      for(offset<-Vector(-1000000.0,1000000.0)){
        val t=new Table(Vector(offset,offset-40.0))
        t.choose(0,.5) shouldBe 0;t.choose(1,.5) shouldBe 0
      }
      val logs=Vector(0.0,math.log(2.0),0.0,math.log(2.0));val t=new Table(logs)
      val counts=Array.fill(4)(0);val grid=2048
      for(j<-0 until 4;k<-0 until grid)counts(t.choose(j,(k+.5)/grid))+=1
      val weights=Vector(1.0/6,1.0/3,1.0/6,1.0/3)
      for(i<-0 until 4)math.abs(counts(i)-4*grid*weights(i)) should be <= 2.0
    }
    "consume exactly one random coordinate per stratum despite refinement" in {
      var calls=0
      val r=new java.util.Random(1){override def nextDouble():Double={calls+=1;0.0}}
      new Table(Vector(0.0,-1e-17)).draw(r) shouldBe Vector(0,0)
      calls shouldBe 2
    }
    "validate domain and interrupt without clearing the flag" in {
      intercept[IllegalArgumentException](new Table(Vector(0.0,-1000001.0)))
      intercept[IllegalArgumentException](new Table(Vector.fill(4097)(0.0)))
      intercept[IllegalArgumentException](new Table(Vector(Double.NaN,0.0)))
      intercept[IllegalArgumentException](new Table(Vector(0.0,0.0)).choose(0,1.0))
      Thread.currentThread().interrupt()
      try {intercept[InterruptedException](new Table(Vector(0.0,0.0)));Thread.currentThread().isInterrupted shouldBe true}
      finally Thread.interrupted()
    }
  }
}
