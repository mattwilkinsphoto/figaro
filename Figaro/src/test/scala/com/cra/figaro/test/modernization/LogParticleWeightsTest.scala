package com.cra.figaro.test.modernization

import com.cra.figaro.algorithm.filtering.{LogParticleWeights as L,
  AuxiliaryParticleProposal as A, WeightedBoundedParticleFilter as W, BoundedParticleFilter as B}
import com.cra.figaro.util.SamplingRandom
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class LogParticleWeightsTest extends AnyWordSpec with Matchers {
  "Log-domain particle weights" should {
    "retain finite tails while separating genuine zero and rejecting unrepresentable log ranges" in {
      val n=L.normalize(Vector(10000.0,9000.0,Double.NegativeInfinity))
      n.logs shouldBe Vector(0.0,-1000.0,Double.NegativeInfinity)
      n.weights shouldBe Vector(1.0,0.0,0.0)
      intercept[IllegalArgumentException](L.normalize(Vector(Double.NegativeInfinity)))
      intercept[IllegalArgumentException](L.normalize(Vector(0.0,Double.NaN)))
      intercept[ArithmeticException](L.normalize(Vector(Double.MaxValue,-Double.MaxValue)))
    }
    "select subnormal and smaller masses without a cumulative interval or probability floor" in {
      for (tiny <- Vector(-46.0,-750.0,-1000.0); order <- Vector(false,true)) {
        val logs=if(order)Vector(tiny,0.0) else Vector(0.0,tiny)
        val tree=new L.Tree(logs)
        tree.draw(tiny-1) shouldBe (if(order)0 else 1)
        tree.draw(-.1) shouldBe (if(order)1 else 0)
      }
      val single=new L.Tree(Vector(Double.NegativeInfinity,0.0,Double.NegativeInfinity))
      single.draw(throw new Exception("deterministic branch must not draw")) shouldBe 1
      new L.Tree(Vector(0.0)).draw(throw new Exception("one leaf")) shouldBe 0
    }
    "extend the log-uniform exponent with independent relative-resolution mantissa bits" in {
      var calls=0
      val scripted=new java.util.Random(0L) {
        override def nextLong(): Long = {calls+=1; if(calls<=22)0L else 1L<<31}
        override def nextDouble(): Double = .25
      }
      L.logUniform(scripted) shouldBe (-(22*64+33)*math.log(2)+math.log1p(.25) +- 1e-12)
      calls shouldBe 23
      val rng=SamplingRandom.seeded(481)
      val draws=Vector.fill(100000)(L.logUniform(rng))
      for(p <- Vector(.001,.01,.1,.5)) {
        val count=draws.count(_<math.log(p)).toDouble
        math.abs(count-100000*p) should be <= (6*math.sqrt(100000*p*(1-p)))
      }
    }
    "match categorical probabilities for uneven non-power-of-two trees including exact zeros" in {
      val p=Vector(0.0,.03,.17,.3,0.0,.5,0.0)
      val tree=new L.Tree(p.map(math.log)); val rng=SamplingRandom.seeded(482)
      val counts=Array.fill(p.size)(0)
      for(i <- 0 until 100000) counts(tree.draw(L.logUniform(rng)))+=1
      for(i <- p.indices) {
        if(p(i)==0) counts(i) shouldBe 0
        else math.abs(counts(i)-100000*p(i)) should be <= (6*math.sqrt(100000*p(i)*(1-p(i))))
      }
    }
    "recover an ordinary-underflowed ancestor through informative look-ahead without losing its correction" in {
      val prepared=A.prepareLogs(Vector(0,1),Vector(0.0,-1000.0),A.LookAhead[Int](i => i*1000.0,0))
      prepared.probabilities.foreach(_ shouldBe (.5 +- 1e-14))
      prepared.logCorrections(1) shouldBe (-1000.0+math.log(2) +- 1e-12)
      prepared.report.evaluations shouldBe 2
      val zero=A.prepareLogs(Vector(0,1),Vector(0.0,Double.NegativeInfinity),
        A.LookAhead[Int](i => if(i==1)throw new Exception("genuine zero") else 0.0))
      zero.report.evaluations shouldBe 1
    }
    "retain tiny ancestors across real filter steps and clear retained state on close or failure" in {
      var counter=0
      val f=W(B.Model[Int](() => {counter+=1; counter%2},identity),W.Config(512,512,1,seed=483))
      try {
        val first=f.start(W.Evidence[Int](i => -1000.0*i,_ => 1.0))
        first.weights.count(_==0.0) shouldBe 256
        f.currentLogWeights.count(_.isFinite) shouldBe 512
        val second=f.advanceAuxiliary(W.Evidence[Int](i => 1000.0*i,_ => 1.0),A.LookAhead[Int](i => 1000.0*i,0))
        second.expectation(_.toDouble) shouldBe (.5 +- .14)
        second.logEvidence shouldBe (0.0 +- 1e-10)
        second.weights.foreach(_ shouldBe (1.0/512 +- 1e-12))
        intercept[W.NoViableParticles](f.advance(W.Evidence[Int](_ => Double.NegativeInfinity,_ => 0.0)))
        intercept[IllegalArgumentException](f.currentLogWeights)
      } finally f.close()
      intercept[IllegalArgumentException](f.currentLogWeights)
    }
  }
}
