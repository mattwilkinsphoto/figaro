package com.cra.figaro.test.modernization

import com.cra.figaro.algorithm.filtering.{FixedBudgetParticleFilter as F, BoundedParticleFilter as B, LogParticleWeights as L}
import com.cra.figaro.util.random
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class FixedBudgetParticleFilterTest extends AnyWordSpec with Matchers {
  private def config(p: F.Policy = F.Policy.Always, m: F.Method = F.Method.LogTreeMultinomial) = F.Config(2,p,m)
  private def bits = Vector(Vector(0,0),Vector(0,1),Vector(1,0),Vector(1,1))
  "Fixed-budget bootstrap filtering" should {
    "require an explicit valid population policy and method" in {
      for(n <- Vector(0,1,1000001)) intercept[IllegalArgumentException](F.Config(n,F.Policy.Always,F.Method.Stratified))
      for(r <- Vector(0.0,-1.0,1.01,Double.NaN,Double.PositiveInfinity))
        intercept[IllegalArgumentException](config(F.Policy.WhenEssBelow(r)))
      intercept[IllegalArgumentException](F.Config(2,null,F.Method.Stratified))
      intercept[IllegalArgumentException](F.Config(2,F.Policy.Always,null))
    }
    "use strict previous-population thresholds without equating fraction one with Always" in {
      F.shouldResample(F.Policy.WhenEssBelow(.75),.75) shouldBe false
      F.shouldResample(F.Policy.WhenEssBelow(.75),math.nextDown(.75)) shouldBe true
      F.shouldResample(F.Policy.WhenEssBelow(1),1) shouldBe false
      F.shouldResample(F.Policy.Always,1) shouldBe true
    }
    "carry normalized weights and use weighted evidence rather than an unweighted mean" in {
      var initial = -1; var draws=0; var calls=0
      val f=F(B.Model(()=>{initial+=1;initial},(x:Int)=>{draws+=1;x}),config(F.Policy.WhenEssBelow(.1)))
      try {
        val first=f.start(x=>{calls+=1;math.log(if(x==0)1.0 else 3.0)})
        first.logEvidence shouldBe (math.log(2) +- 1e-14)
        val second=f.advance(x=>{calls+=1;math.log(if(x==0)2.0 else 4.0)})
        second.logEvidence shouldBe (math.log(7) +- 1e-14)
        second.weights(1) shouldBe (6.0/7 +- 1e-14)
        second.report.decision shouldBe F.Decision.CarriedWeights
        second.report.ancestorSelections shouldBe 0
        second.report.contributingParents shouldBe Some(2)
        second.report.stateDraws shouldBe 2
        second.report.likelihoodCalls shouldBe 2
        draws shouldBe 2; calls shouldBe 4
        first.weights shouldBe Vector(.25,.75)
      } finally f.close()
    }
    "match independent exact two-state expectations over every initial ancestor and transition outcome" in {
      val prior=Vector(1.0/3,2.0/3); val g0=Vector(.4,.8); val g1=Vector(.9,.1)
      val transition=Vector(Vector(.75,.25),Vector(.2,.8))
      for(policy <- Vector(F.Policy.Always,F.Policy.WhenEssBelow(.25),F.Policy.WhenEssBelow(.75),F.Policy.WhenEssBelow(1));
          method <- F.Method.values) {
        val result=Array(0.0,0.0)
        for(initial <- bits) {
          val p0=initial.map(prior).product; val z0=initial.map(g0).sum/2
          val logs=L.normalize(initial.map(i=>math.log(g0(i)))).logs
          val weights=logs.map(math.exp)
          val resample=F.shouldResample(policy,F.relativeEss(logs))
          val choices=if(resample)bits else Vector(Vector(0,1))
          for(a <- choices) {
            val pa=if(!resample)1.0 else a.indices.map { j =>
              if(method==F.Method.LogTreeMultinomial)weights(a(j))
              else {
                val left=if(a(j)==0)0.0 else weights(0)
                val right=if(a(j)==0)weights(0) else 1.0
                2*math.max(0.0,math.min((j+1)/2.0,right)-math.max(j/2.0,left))
              }
            }.product
            for(next <- bits) {
              val pn=next.indices.map(i=>transition(initial(a(i)))(next(i))).product
              val base=if(resample)Vector.fill(2)(-math.log(2)) else logs
              val raw=F.reweight(base,next.map(i=>math.log(g1(i))))
              val norm=L.normalize(raw); val z=z0*math.exp(norm.peak+norm.logSum)
              for(j <- 0 to 1) result(j)+=p0*pa*pn*z*next.indices.filter(i=>next(i)==j).map(norm.weights).sum
            }
          }
        }
        result(0) shouldBe (93.0/500 +- 1e-14)
        result(1) shouldBe (23.0/500 +- 1e-14)
      }
    }
    "select stratified ancestors using exact interval boundaries and never select zero mass" in {
      val logs=Vector(Double.NegativeInfinity,math.log(.25),Double.NegativeInfinity,math.log(.75),Double.NegativeInfinity)
      val rng=new java.util.Random(1) { override def nextDouble():Double=0.0 }
      F.ancestors(logs,F.Method.Stratified,rng) shouldBe Vector(1,1,3,3,3)
      val equal=Vector.fill(4)(-math.log(4))
      F.ancestors(equal,F.Method.Stratified,rng) shouldBe Vector(0,1,2,3)
      val nearOne=new java.util.Random(1) { override def nextDouble():Double=math.nextDown(1.0) }
      F.ancestors(logs,F.Method.Stratified,nearOne).forall(i=>i==1 || i==3) shouldBe true
    }
    "replay both methods and account for only one ancestor selection per step" in {
      for(m <- F.Method.values) {
        def run()={
          val f=F(B.Model(()=>random.nextGaussian(),(x:Double)=>.8*x+random.nextGaussian()),F.Config(64,F.Policy.Always,m,77))
          try { f.start(x=> -.1*x*x); Vector.fill(3)(f.advance(x=> -.1*x*x)) } finally f.close()
        }
        val a=run(); a shouldBe run()
        a.foreach { r=> r.report.ancestorSelections shouldBe 64; r.report.decision shouldBe F.Decision.Resampled }
      }
    }
  }
}
