package com.cra.figaro.test.modernization

import com.cra.figaro.algorithm.factored.VariableElimination
import com.cra.figaro.algorithm.sampling.SupportAwareProposal as S
import com.cra.figaro.algorithm.sampling.parallel.MultiChainMetropolisHastings as MH
import com.cra.figaro.language.*
import com.cra.figaro.library.atomic.continuous.*
import com.cra.figaro.library.atomic.discrete.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class CapabilityExpansionTest extends AnyWordSpec with Matchers {
  private val config=MH.Config(chains=2,drawsPerChain=12000,warmUp=2000,parallelism=2,seed=9134L)
  "Capability expansion" should {
    "score nested owned Normal likelihoods against independent four-state enumeration" in {
      val states=for(m <- Vector(-1.0,1.0); v <- Vector(.5,2.0)) yield {
        val w=math.exp(-.5*(.3-m)*(.3-m)/v)/math.sqrt(v)
        (m,v,w)
      }
      val expected=states.map((m,v,w) => m*w).sum/states.map(_._3).sum
      def build(u: Universe, i: Int): MH.Model = {
        val m=Select(.5 -> -1.0,.5 -> 1.0)(using "m",u)
        val v=Select(.5 -> .5,.5 -> 2.0)(using "v",u)
        Normal(m,v)(using "y",u).observe(.3)
        MH.Model(Vector(MH.Observable("m",m)(identity)))
      }
      val result=MH.run(config)(build)
      result.diagnostics("m").mean shouldBe (expected +- .04)
      val a=MH.run(config.copy(drawsPerChain=1000,parallelism=1))(build)
      val b=MH.run(config.copy(drawsPerChain=1000,parallelism=2))(build)
      a.chains.map(_.draws) shouldBe b.chains.map(_.draws)
    }
    "aggregate before enumerating a combinatorial count model" in {
      val u=new Universe
      try {
        val law=MultinomialDistribution(20,Vector.fill(10)(.1))
        intercept[IllegalArgumentException] {
          FiniteCountInference.multinomial(law)(using "full",u)
        }
        val x=FiniteCountInference.multinomialAggregated(law,
          Vector((0 until 4).toVector,(4 until 10).toVector))(using "groups",u)
        val ve=VariableElimination(x)
        try {
          ve.start()
          val choose=(1 to 8).foldLeft(1.0)((a,k) => a*(20-k+1)/k)
          ve.probability(x,Vector(8,12)) shouldBe (choose*math.pow(.4,8)*math.pow(.6,12) +- 1e-12)
        } finally ve.kill()
        intercept[IllegalArgumentException](law.aggregate(Vector(Vector(0),Vector(0))))
      } finally u.clear()
    }
    "match grouped probabilities to exhaustive original count vectors" in {
      val law=MultinomialDistribution(4,Vector(.1,.2,.3,.4))
      val grouped=law.aggregate(Vector(Vector(0,2),Vector(1,3)))
      for(k <- 0 to 4) {
        val exact=(for(a <- 0 to 4;b <- 0 to 4-a;c <- 0 to 4-a-b if a+c==k)
          yield law.probability(Vector(a,b,c,4-a-b-c))).sum
        grouped.probability(Vector(k,4-k)) shouldBe (exact +- 1e-14)
      }
    }
    "retain asymmetric custom proposal terms without ordinary-space overflow" in {
      val r=LogProposalRatios.transition(7,-1000,-1,-900,-2)
      r shouldBe ((7,898.0,999.0))
      LogProposalRatios.transition(7,-1,Double.NegativeInfinity,-1,-1)._3 shouldBe Double.NegativeInfinity
      intercept[IllegalArgumentException](LogProposalRatios.transition(7,0,0,Double.NegativeInfinity,0))
      intercept[ArithmeticException](LogProposalRatios.transition(7,-Double.MaxValue,Double.MaxValue,0,0))
      val u=new Universe
      try {
        val x=new Element[Double]("",u) with Atomic[Double] with HasLogRandomnessProposal[Double] {
          type Randomness=Double
          def generateRandomness()=0.0
          def generateValue(r: Double)=r
          def density(r: Double)=math.exp(logRandomnessDensity(r))
          def proposeRandomness(old: Double)=old+1
          def logRandomnessDensity(r: Double)= -1000+r*999
          def logProposalDensity(from: Double,to: Double)=if(to>from) -900.0 else -2.0
          override def nextRandomness(old: Double): (Double,Double,Double)=throw new AssertionError("Legacy hook called")
        }
        x.nextLogRandomness(0) shouldBe ((1.0,898.0,999.0))
      } finally u.clear()
    }
    "use legacy Gamma and Beta value-to-randomness maps in local moves" in {
      val gamma=MH.run(config) { (u,_) =>
        val x=Gamma(2,3)(using "x",u)
        MH.Model(Vector(MH.Observable("x",x)(identity)),Some(S.automatic(x,.6)))
      }
      gamma.diagnostics("x").mean shouldBe (6.0 +- .35)
      val beta=MH.run(config) { (u,_) =>
        val x=Beta(2,3)(using "x",u)
        MH.Model(Vector(MH.Observable("x",x)(identity)),Some(S.automatic(x,1)))
      }
      beta.diagnostics("x").mean shouldBe (.4 +- .025)
    }
    "adapt only discarded warm-up and freeze scales before retained sampling" in {
      def build(u: Universe,i: Int): MH.TunedModel = {
        val x=Normal(0,4)(using "x",u)
        x.addLogConstraint((v: Double) => -.5*math.pow((v-1)/.2,2))
        MH.TunedModel(MH.Model(Vector(MH.Observable("x",x)(identity))),S.tune(x))
      }
      val result=MH.runTuned(config)(build)
      result.result.diagnostics("x").mean shouldBe (1/1.01 +- .025)
      result.tuning.foreach { t =>
        t.warmUpAttempts shouldBe config.warmUp
        t.stepSd should be > 0.0
      }
      val a=MH.runTuned(config.copy(drawsPerChain=1000,parallelism=1))(build)
      val b=MH.runTuned(config.copy(drawsPerChain=1000,parallelism=2))(build)
      a.result.chains.map(_.draws) shouldBe b.result.chains.map(_.draws)
      a.tuning shouldBe b.tuning
    }
    "support an explicit invertible custom value map without guessing its semantics" in {
      val result=MH.run(config) { (u,_) =>
        val x=new Element[Double]("x",u) with Atomic[Double] with HasLogDensity[Double] {
          type Randomness=Double
          def generateRandomness()=com.cra.figaro.util.random.nextGaussian()
          def generateValue(r: Double)=2*r+3
          def logDensity(v: Double)= -.5*math.pow((v-3)/2,2)-math.log(2)-.5*math.log(2*math.Pi)
        }
        intercept[IllegalArgumentException](S.automatic(x))
        val move=S.customScalar(x,S.ScalarCoordinates.Real,.8)(v => (v-3)/2)
        MH.Model(Vector(MH.Observable("x",x)(identity)),Some(move))
      }
      result.diagnostics("x").mean shouldBe (3.0 +- .15)
    }
    "refuse automatic tuning that would silently freeze another latent" in {
      intercept[MH.ChainFailure] {
        MH.runTuned(config.copy(drawsPerChain=10)) { (u,_) =>
          val x=Normal(0,1)(using "x",u)
          Normal(0,1)(using "other",u)
          MH.TunedModel(MH.Model(Vector(MH.Observable("x",x)(identity))),S.tune(x))
        }
      }
    }
    "evaluate tiny-shape limits without losing a representable log survival" in {
      for(s <- Vector(-1e-100,-1e-20,1e-20,1e-100); z <- Vector(.01,.5,2.0,10.0)) {
        GeneralizedParetoDistribution(s).logDensity(z) shouldBe (-z +- 2e-14)
        GeneralizedExtremeValueDistribution(s).logDensity(z) shouldBe (-z-math.exp(-z) +- 2e-14)
      }
      val tiny=GeneralizedParetoDistribution(java.lang.Double.MIN_VALUE)
      tiny.logDensity(.1) shouldBe (-.1 +- 1e-16)
      intercept[IllegalArgumentException](GeneralizedExtremeValueDistribution(java.lang.Double.MIN_VALUE))
    }
    "evaluate near-singular factors without reconstructing a singular matrix" in {
      val eye=Vector(Vector(1.0,0.0),Vector(0.0,1.0))
      val w=WishartDistribution(4,eye)
      val iw=InverseWishartDistribution(4,eye)
      val factor=Vector(Vector(1.0,0.0),Vector(1.0,1e-20))
      val rounded=Vector(Vector(1.0,1.0),Vector(1.0,1.0))
      intercept[IllegalArgumentException](w.logDensity(rounded))
      intercept[IllegalArgumentException](iw.logDensity(rounded))
      // Normalizer at df=4,d=2 is 8*pi; direct independent triangular-entry formulas.
      val expected=math.log(4)-math.log(8*math.Pi)+2*math.log(1e-20)-1
      w.logDensityCholesky(factor) shouldBe (expected +- 1e-12)
      iw.logDensityCholesky(factor)/(-1e40) shouldBe (1.0 +- 1e-14)
      val ordinary=Vector(Vector(2.0,0.0),Vector(.3,1.0))
      val matrix=Vector(Vector(4.0,.6),Vector(.6,1.09))
      val jac=math.log(4)+2*math.log(2)
      w.logDensityCholesky(ordinary) shouldBe (w.logDensity(matrix)+jac +- 1e-12)
      iw.logDensityCholesky(ordinary) shouldBe (iw.logDensity(matrix)+jac +- 1e-12)
    }
  }
}
