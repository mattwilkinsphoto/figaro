package com.cra.figaro.test.modernization

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import com.cra.figaro.algorithm.sampling.{RareEventImportance as R,GaussianMixtureProposal as M,VectorImportance as V}
import com.cra.figaro.library.atomic.continuous.{MultivariateGaussianDistribution as G}

class WeightedRareEventTest extends AnyWordSpec with Matchers {
  val base=V.Gaussian(G(Vector(0.0),Vector(Vector(1.0))))
  "Weighted mixture fitting" should {
    "match weighted moments and ignore zero-mass outliers without treating weights as replication counts" in {
      val points=Vector.fill(100)(Vector(-1.0))++Vector.fill(100)(Vector(2.0)):+Vector(1e200)
      val logs=Vector.fill(100)(math.log(3.0))++Vector.fill(100)(0.0):+Double.NegativeInfinity
      val policy=M.Config(components=1,covarianceInflation=1,diagonalRidge=Vector(.01))
      val fit=M.fitWeighted(points,logs,policy)
      fit.status shouldBe M.Status.Fitted
      val g=fit.proposal.get.components.head.asInstanceOf[V.Gaussian].law
      g.mean.head shouldBe (-.25 +- 1e-12)
      g.covariance.head.head shouldBe (1.6875+.01 +- 1e-12)
      val shifted=M.fitWeighted(points,logs.map(_+700),policy).proposal.get.components.head.asInstanceOf[V.Gaussian].law
      shifted.mean.head shouldBe (g.mean.head +- 1e-12)
      shifted.covariance.head.head shouldBe (g.covariance.head.head +- 1e-12)
    }
    "retain both event regions with weighted CE and independent production" in {
      val fit=R.fitMixture(base,x => math.abs(x.head),4,R.FitConfig(drawsPerRound=1000,maxRounds=10,maxScoreEvaluations=10000,seed=94001),
        M.Config(components=2,diagonalRidge=Vector(.25),maxDensityEvaluations=5000000))
      info(fit.status.toString+" "+fit.message+" "+fit.rounds)
      fit.status shouldBe R.FitStatus.Fitted
      fit.componentDensityEvaluations should be > 0L
      val r=R.run(fit.proposal.get,x => math.abs(x.head),4,R.Config(draws=20000,maxScoreEvaluations=20000,seed=94001))
      r.probability.get shouldBe (6.334248366623984e-5 +- 5e-6)
      r.randomStream.index shouldBe 1
      fit.randomStream.index shouldBe 0
    }
    "refuse insufficient information, numerical collapse and aggregate work exhaustion" in {
      val points=Vector.tabulate(100)(i => Vector(i.toDouble))
      M.fitWeighted(points,Vector(0.0)++Vector.fill(99)(-1000.0)).status shouldBe M.Status.InsufficientPilot
      M.fitWeighted(Vector.fill(100)(Vector(1.0)),Vector.fill(100)(0.0)).proposal shouldBe None
      val fit=R.fitMixture(base,_.head,4,R.FitConfig(),M.Config(maxDensityEvaluations=1))
      fit.status shouldBe R.FitStatus.MixtureFitFailure
      fit.proposal shouldBe None
      fit.componentDensityEvaluations shouldBe 0L
      fit.scoreEvaluations shouldBe 1000
    }
    "preserve cancellation and reject malformed weights" in {
      intercept[IllegalArgumentException](M.fitWeighted(Vector(Vector(0.0)),Vector(Double.NaN)))
      try { Thread.currentThread().interrupt()
        intercept[InterruptedException](M.fitWeighted(Vector(Vector(0.0)),Vector(0.0)))
      } finally Thread.interrupted()
    }
  }
}
