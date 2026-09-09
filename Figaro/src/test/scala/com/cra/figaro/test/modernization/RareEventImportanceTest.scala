package com.cra.figaro.test.modernization

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import com.cra.figaro.algorithm.sampling.{RareEventImportance as R,VectorImportance as V}
import com.cra.figaro.library.atomic.continuous.{MultivariateGaussianDistribution as G}
import com.cra.figaro.util.{SamplingRandom,RandomStreams}

class RareEventImportanceTest extends AnyWordSpec with Matchers {
  private val base=V.Gaussian(G(Vector(0.0),Vector(Vector(1.0))))
  "Query-aware rare-event importance" should {
    "use ordinary full-mixture weights and the fixed-budget variance formula" in {
      val b=V.Box(Vector(0.0),Vector(1.0)); val q=V.Box(Vector(.75),Vector(1.0))
      val result=R.run(R.defensive(b,q,.2),_.head,.75,R.Config(draws=1000,maxScoreEvaluations=1000))
      val h=result.eventHits.toDouble; val n=1000.0
      result.probability.get shouldBe (h/(3.4*n) +- 1e-14)
      result.standardError.get shouldBe (math.sqrt(h*(n-h)/(n*n*(n-1)))/3.4 +- 1e-14)
      result.eventEss shouldBe (h +- 1e-10)
      result.densityEvaluations shouldBe 2000L
      result.probability.get shouldBe (.25 +- .01)
      result.warnings.exists(_.contains("every event region")) shouldBe true
    }
    "fit the event rather than the prior and separate pilot/production streams" in {
      val config=R.FitConfig(drawsPerRound=1000,maxRounds=12,maxScoreEvaluations=12000,seed=8111,diagonalRidge=Vector(.25))
      val fit=R.fitGaussian(base,_.head,5,config)
      info("Five-sigma CE trace: "+fit.status+" "+fit.rounds.mkString(","))
      fit.status shouldBe R.FitStatus.Fitted
      fit.scoreEvaluations shouldBe fit.rounds.size*1000
      val result=R.run(fit.proposal.get,_.head,5,R.Config(draws=20000,maxScoreEvaluations=20000,seed=config.seed))
      result.probability.get shouldBe (2.8665157187919391e-7 +- 2e-8)
      result.eventEss should be > 500.0
      result.randomStream.index shouldBe 1; fit.randomStream.index shouldBe 0
      R.run(fit.proposal.get,_.head,5,R.Config(draws=20000,maxScoreEvaluations=20000,seed=config.seed)) shouldBe result
      val repeated=R.fitGaussian(base,_.head,5,config)
      repeated.rounds shouldBe fit.rounds
      repeated.proposal.get.proposal shouldBe fit.proposal.get.proposal
    }
    "fit correlated multivariate event directions with the full weighted covariance" in {
      val b=V.Gaussian(G(Vector(0.0,0.0),Vector(Vector(1.0,.7),Vector(.7,1.0))))
      val score=(x: Vector[Double]) => x.sum/math.sqrt(3.4)
      val fit=R.fitGaussian(b,score,3,R.FitConfig(seed=8222))
      fit.status shouldBe R.FitStatus.Fitted
      val result=R.run(fit.proposal.get,score,3,R.Config(seed=8222))
      result.probability.get shouldBe (.0013498980316300945 +- .00015)
    }
    "retain positive log estimates beyond ordinary floating-point probability range" in {
      val shifted=V.Gaussian(G(Vector(40.0),Vector(Vector(.01))))
      val result=R.run(R.defensive(base,shifted),_.head,40,R.Config(draws=20000,maxScoreEvaluations=20000,seed=8333))
      result.probability shouldBe None
      result.logProbability shouldBe (-804.6084420137538 +- .08)
      result.logStandardError.exists(_.isFinite) shouldBe true
      result.standardError shouldBe None
    }
    "refuse unsupported pilot fits and enforce budgets without hidden target work" in {
      var calls=0
      def score(x: Vector[Double]): Double={calls+=1;x.head}
      val fit=R.fitGaussian(base,score,20,R.FitConfig(drawsPerRound=100,maxScoreEvaluations=99))
      fit.status shouldBe R.FitStatus.ScoreBudgetReached; calls shouldBe 0
      val short=R.fitGaussian(base,score,20,R.FitConfig(drawsPerRound=1000,maxRounds=1))
      short.status shouldBe R.FitStatus.RoundBudgetReached; short.proposal shouldBe None
      val inadequate=R.fitGaussian(base,score,5,R.FitConfig(drawsPerRound=20,minEliteEss=20))
      inadequate.status shouldBe R.FitStatus.InsufficientElite
      val result=R.run(R.prior(base),score,0,R.Config(draws=100,maxScoreEvaluations=17))
      result.scoreEvaluations shouldBe 17; result.reason shouldBe R.StopReason.ScoreBudgetReached
      val zero=R.run(R.prior(V.Box(Vector(0),Vector(1))),_.head,2)
      zero.probability shouldBe Some(0.0); zero.standardError shouldBe None
      zero.warnings.exists(_.contains("No positive")) shouldBe true
    }
    "show why a defensive component and small empirical MCSE do not certify both rare modes" in {
      val q=V.Gaussian(G(Vector(5.0),Vector(Vector(.000001))))
      val score=(x: Vector[Double]) => .001-math.abs(math.abs(x.head)-5)
      val result=R.run(R.defensive(base,q),score,0,R.Config(draws=20000,maxScoreEvaluations=20000,seed=8444))
      // Each narrow interval has probability 2.9734509232365575e-9. The remote
      // interval is almost never sampled by 2000 defensive prior draws.
      val oneRegion=2.9734509232365575e-9
      result.probability.get shouldBe (oneRegion +- oneRegion*.05)
      result.relativeStandardError.get should be < .03
      math.abs(result.probability.get-2*oneRegion)/(2*oneRegion) should be > .4
      result.warnings.exists(_.contains("every event region")) shouldBe true
    }
    "support partitioned RNG replay and propagate callback failures and interruption" in {
      val config=R.Config(draws=50,maxScoreEvaluations=50,randomAlgorithm=SamplingRandom.Algorithm.Philox4x64,
        streams=RandomStreams.Config(RandomStreams.Allocation.PartitionedV1))
      R.run(R.prior(base),_.head,0,config) shouldBe R.run(R.prior(base),_.head,0,config)
      intercept[IllegalStateException](R.fitGaussian(base,_ => throw new IllegalStateException("callback"),0))
      intercept[IllegalArgumentException](R.run(R.prior(base),_ => Double.NaN,0))
      intercept[IllegalArgumentException](R.defensive(base,base,0))
      try { Thread.currentThread().interrupt()
        intercept[InterruptedException](R.run(R.prior(base),_.head,0))
        Thread.currentThread().isInterrupted shouldBe true
      } finally Thread.interrupted()
    }
    "resolve small but nonzero empirical variance without subtracting raw moments" in {
      val epsilon=1e-8
      val linear=new V.Proposal {
        val dimension=1
        def sample(rng: scala.util.Random): Vector[Double]={
          val u=rng.nextDouble(); val a=1-epsilon
          Vector(2*u/(a+math.sqrt(a*a+4*epsilon*u)))
        }
        def logDensity(x: Vector[Double]): Double=if(x.head<0 || x.head>1) Double.NegativeInfinity else math.log1p(epsilon*(2*x.head-1))
      }
      val result=R.run(R.defensive(linear,V.Box(Vector(0),Vector(1))),_ => 1.0,0,R.Config(draws=1000,maxScoreEvaluations=1000))
      result.standardError.get should be > 1e-10
      result.standardError.get should be < 3e-10
    }
  }
}
