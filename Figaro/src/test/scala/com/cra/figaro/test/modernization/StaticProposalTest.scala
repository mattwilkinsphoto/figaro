package com.cra.figaro.test.modernization
import com.cra.figaro.algorithm.sampling.{StaticGraphImportance as S,VectorImportance as V,RareEventImportance as R}
import com.cra.figaro.library.atomic.continuous.MultivariateGaussianDistribution as G
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class StaticProposalTest extends AnyWordSpec with Matchers {
  import S.Node.*
  val model=S.compile(Vector(Constant(0),Constant(1),Normal(0,1),Normal(2,1)))
  val prior=V.Gaussian(G(Vector(0.0),Vector(Vector(1.0))))
  "Static joint proposals" should {
    "consume a fitted event mixture without exposing mutable graph callbacks" in {
      val fit=R.fitMixture(prior,x => math.abs(x.head),4,R.FitConfig(seed=94001),
        com.cra.figaro.algorithm.sampling.GaussianMixtureProposal.Config(components=2,diagonalRidge=Vector(.25)))
      fit.proposal.isDefined shouldBe true
      val event=S.compile(Vector(Constant(0),Constant(1),Normal(0,1),Abs(2),Constant(4),GreaterThan(3,4)))
      val result=S.runWithProposal(event,Vector(2),fit.proposal.get.proposal,Vector(5),config=S.Config(draws=30000,seed=94001))
      result.health.head.diagnostics.mean.get shouldBe (6.334248366623984e-5 +- 1e-5)
    }
    "correct by the full defensive mixture and downstream hierarchical likelihood" in {
      val q=R.defensive(prior,V.Gaussian(G(Vector(1.5),Vector(Vector(.5))))).proposal
      val config=S.Config(draws=20000,seed=97001)
      val r=S.runWithProposal(model,Vector(2),q,Vector(2),Map(3 -> 3.0),config)
      r.health.head.diagnostics.mean.get shouldBe (1.5 +- .03)
      r.logWeights.zip(r.values.head).foreach { (w,x) =>
        w shouldBe (prior.logDensity(Vector(x))-q.logDensity(Vector(x))-.5*math.log(2*math.Pi)-.5*math.pow(3-x,2) +- 1e-12)
      }
      val serial=S.runWithProposal(model,Vector(2),q,Vector(2),Map(3 -> 3.0),config.copy(parallelism=1))
      serial.logWeights shouldBe r.logWeights; serial.values shouldBe r.values
      r.proposalDensityEvaluations shouldBe 20000L
    }
    "apply correlated joint proposals in declared root order" in {
      val m=S.compile(Vector(Constant(0),Constant(1),Normal(0,1),Normal(0,1),Add(2,3)))
      val q=V.Gaussian(G(Vector(1.0,-1.0),Vector(Vector(1.0,.4),Vector(.4,1.0))))
      val r=S.runWithProposal(m,Vector(3,2),q,Vector(2,3),config=S.Config(draws=100))
      r.values.head.zip(r.values(1)).zip(r.logWeights).foreach { case ((x,y),w) =>
        w shouldBe (prior.logDensity(Vector(x))+prior.logDensity(Vector(y))-q.logDensity(Vector(y,x)) +- 1e-12)
      }
    }
    "refuse unsafe callbacks, bounded proposals, observed roots and non-root dependencies" in {
      intercept[IllegalArgumentException](S.runWithProposal(model,Vector(3),prior,Vector(2)))
      intercept[IllegalArgumentException](S.runWithProposal(model,Vector(2),prior,Vector(2),Map(2 -> 0.0)))
      intercept[IllegalArgumentException](S.runWithProposal(model,Vector(2),V.Box(Vector(-1),Vector(1)),Vector(2)))
      val custom=new V.Proposal {
        val dimension=1
        def sample(r: scala.util.Random)=throw new AssertionError("callback must not execute")
        def logDensity(x: Vector[Double])=throw new AssertionError("callback must not execute")
      }
      intercept[IllegalArgumentException](S.runWithProposal(model,Vector(2),custom,Vector(2)))
    }
    "support Uniform, Exponential and deterministic event nodes with exact evidence weights" in {
      val m=S.compile(Vector(Constant(0),Constant(2),Uniform(0,1),Exponential(1),Log(3),Abs(4),GreaterThan(3,1)))
      val r=S.run(m,Vector(2,3,6),Map(2 -> 1.0,3 -> .5),S.Config(draws=100))
      r.logWeights.foreach(_ shouldBe (-1.0 +- 1e-14))
      r.values(2).forall(_==0) shouldBe true
      val draws=S.run(m,Vector(2,3),config=S.Config(draws=20000))
      draws.health(0).diagnostics.mean.get shouldBe (1.0 +- .02)
      draws.health(1).diagnostics.mean.get shouldBe (.5 +- .02)
      S.run(m,Vector(2),Map(2 -> 3),S.Config(draws=10)).logWeights.forall(_==Double.NegativeInfinity) shouldBe true
      intercept[IllegalArgumentException](S.run(S.compile(Vector(Constant(-1),Log(0))),Vector(1)))
    }
  }
}
