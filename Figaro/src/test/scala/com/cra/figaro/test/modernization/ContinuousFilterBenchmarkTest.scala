package com.cra.figaro.test.modernization

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import com.cra.figaro.util.withRandomSeed
import com.cra.figaro.algorithm.filtering.{LogParticleWeights as L, WeightedBoundedParticleFilter as W}

class ContinuousFilterBenchmarkTest extends AnyWordSpec with Matchers {
  import ContinuousFilterBenchmark.*
  "Continuous benchmark fixtures and guidance" should {
    "retain the formerly refused tiny weights and ancestor intervals" in {
      val tree=new L.Tree(Vector(.5,1e-20,.5).map(math.log))
      var calls=0
      tree.draw {calls+=1; if(calls==1)-1.0 else -100.0} shouldBe 1
      var i=0
      val result=W.weightedStep(W.Config(2,2,1),false)(() => {
          val logWeight=if(i==0)0.0 else -1000.0
          i+=1
          (i,-1,logWeight)
        },_ => 1.0)
      result.logWeights shouldBe Vector(0.0,-1000.0)
      result.logIncrement shouldBe (-math.log(2) +- 1e-14)
    }
    "use the declared time and variance conventions" in {
      Spec("growth").drift(0,1) shouldBe 8.0
      Spec("growth").initial.head.sd shouldBe 2.0
      Spec("growth").process*Spec("growth").process shouldBe (10.0 +- 1e-14)
      Spec("persistent-modes").drift(-4,17) shouldBe -4.0
    }
    "normalize the full defensive proposal for positive and nonpositive evidence" in {
      for(s<-scenarios;y<-Vector(-.2,1.0,8.0)) {
        val spec=Spec(s)
        val (q,predictive)=guidance(spec,spec.initial,y)
        predictive.isFinite shouldBe true
        val dx=.01
        val integral=(-5000 until 5000).iterator.map(i=>math.exp(q.logDensity((i+.5)*dx))*dx).sum
        integral shouldBe (1.0 +- 1e-8)
      }
    }
    "retain both modes and deterministic scoped sampling in the symmetric case" in {
      val spec=Spec("persistent-modes")
      val (q,_)=guidance(spec,spec.initial,4)
      for(x<-Vector(.1,2.0,4.0,6.0))q.logDensity(x) shouldBe (q.logDensity(-x) +- 1e-12)
      val first=withRandomSeed(351){Vector.fill(1000)(q.sample())}
      val second=withRandomSeed(351){Vector.fill(1000)(q.sample())}
      first shouldBe second
      first.exists(_>0) shouldBe true
      first.exists(_<0) shouldBe true
    }
  }
}
