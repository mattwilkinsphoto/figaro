package com.cra.figaro.test.modernization

import com.cra.figaro.algorithm.sampling.{GaussianMixtureProposal as M, VectorImportance as V}
import com.cra.figaro.library.atomic.continuous.MultivariateGaussianDistribution as G
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class GaussianMixtureProposalTest extends AnyWordSpec with Matchers {
  private val points=Vector.tabulate(4)(_ => Vector.tabulate(100)(i => Vector((if(i<75) -6 else 6)+(i%5-2)*.1)))
  "Pilot-only Gaussian mixture fitting" should {
    "match the independent NumPy inverse-matrix oracle in two dimensions" in {
      val input=Vector.tabulate(4)(j => Vector.tabulate(80) { i =>
        val t=(j*80+i).toDouble
        Vector((if(t%4<3) -1.0 else 2.0)+.8*math.sin(t*1.7),
          (if(t%4<3) .5 else -1.0)+.6*math.cos(t*.9))
      })
      val result=M.fit(input,M.Config(tolerance=1e-9,maxIterations=200,covarianceInflation=1,diagonalRidge=Vector(1e-4,1e-4)))
      result.status shouldBe M.Status.Fitted
      result.iterations shouldBe 7
      result.trainingLogDensity.last shouldBe (-1.9732677597299386 +- 1e-10)
      val q=result.proposal.get
      val ordered=q.components.map(_.asInstanceOf[V.Gaussian].law).zip(q.probabilities).sortBy(_._1.mean.head)
      val expected=Vector((Vector(-1.001133989409685,.499549949773826),Vector(.318957649710218,.0009575579100725377,.1793538498612241),.7500038299852501),
        (Vector(2.0141025025549193,-1.003505967022141),Vector(.32277252436490406,.0023282418771288036,.18320496497108166),.24999617001474989))
      ordered.zip(expected).foreach { case ((law,w),(mean,cov,weight)) =>
        law.mean.zip(mean).foreach((a,b) => a shouldBe (b +- 1e-10))
        Vector(law.covariance(0)(0),law.covariance(0)(1),law.covariance(1)(1)).zip(cov).foreach((a,b) => a shouldBe (b +- 1e-10))
        w shouldBe (weight +- 1e-10)
      }
    }
    "recover separated analytic component moments and weights without changing pilot inputs" in {
      val result=M.fit(points,M.Config(covarianceInflation=1))
      result.status shouldBe M.Status.Fitted
      val q=result.proposal.get
      val components=q.components.map(_.asInstanceOf[V.Gaussian].law)
      val ordered=components.zip(q.probabilities).sortBy(_._1.mean.head)
      ordered.map(_._1.mean.head).zip(Vector(-6.0,6.0)).foreach((a,b) => a shouldBe (b +- 1e-10))
      ordered.map(_._1.covariance.head.head).foreach(_ shouldBe (.02 +- 1e-10))
      ordered.map(_._2) shouldBe Vector(.75,.25)
      result.densityEvaluations shouldBe result.iterations*800L
      result.trainingLogDensity.sliding(2).foreach(p => p(1) should be >= (p(0)-1e-10))
      M.fit(points,M.Config(covarianceInflation=1)) shouldBe result
      points.head.head shouldBe Vector(-6.2)
    }
    "separate regularization from final covariance inflation and handle a single component" in {
      val result=M.fit(points,M.Config(components=1,covarianceInflation=2,diagonalRidge=Vector(.1)))
      result.status shouldBe M.Status.Fitted
      val law=result.proposal.get.components.head.asInstanceOf[V.Gaussian].law
      law.mean.head shouldBe (-3.0 +- 1e-12)
      law.covariance.head.head shouldBe (2*(27.02+.1) +- 1e-10)
      result.proposal.get.probabilities shouldBe Vector(1.0)
    }
    "return explicit work and data refusals without partial production proposals" in {
      for((config,status) <- Vector(
        (M.Config(maxDensityEvaluations=1),M.Status.EvaluationLimit),
        (M.Config(maxIterations=2),M.Status.IterationLimit),
        (M.Config(minComponentDraws=190),M.Status.InsufficientComponent),
        (M.Config(minComponentDraws=201),M.Status.InsufficientPilot))) {
        val r=M.fit(points,config); r.status shouldBe status; r.proposal shouldBe None
        r.densityEvaluations should be <= config.maxDensityEvaluations
      }
      M.fit(Vector.empty).status shouldBe M.Status.InsufficientPilot
      M.fit(Vector.fill(4)(Vector.fill(5)(Vector(1.0)))).status shouldBe M.Status.DegeneratePilot
      intercept[IllegalArgumentException](M.fit(points,M.Config(maxStoredValues=10)))
      intercept[IllegalArgumentException](M.fit(points,M.Config(diagonalRidge=Vector(.1,.1))))
      intercept[IllegalArgumentException](M.Config(components=9))
    }
    "reject singular fits without hidden regularization and preserve interruption" in {
      val collinear=points.map(_.map(x => Vector(x.head,2*x.head)))
      M.fit(collinear).status shouldBe M.Status.NumericalFailure
      M.fit(collinear,M.Config(diagonalRidge=Vector(.01,.01))).status shouldBe M.Status.Fitted
      intercept[InterruptedException] {
        try { Thread.currentThread().interrupt(); M.fit(points) }
        finally { Thread.currentThread().isInterrupted shouldBe true; Thread.interrupted() }
      }
    }
    "freeze fitted components for correctly weighted independent production" in {
      val fit=M.fit(points).proposal.get
      val broad=V.Gaussian(G(Vector(0.0),Vector(Vector(100.0))))
      val q=V.Mixture(Vector(.1,.9),Vector(broad,fit))
      val target=V.Mixture(Vector(.75,.25),Vector(V.Gaussian(G(Vector(-6.0),Vector(Vector(.02)))),V.Gaussian(G(Vector(6.0),Vector(Vector(.02))))))
      val result=V.run(V.Config(draws=5000,seed=1181),q,target.logDensity,x => if(x.head>0) 1 else 0)
      result.logWeights shouldBe result.samples.map(x => target.logDensity(x)-q.logDensity(x))
      result.health.diagnostics.mean.get shouldBe (.25 +- .03)
      fit.components.size shouldBe 2
    }
    "keep independent concurrent fitting work isolated" in {
      val pool=java.util.concurrent.Executors.newFixedThreadPool(2)
      try {
        val expected=M.fit(points)
        val jobs=Vector.fill(2)(pool.submit(new java.util.concurrent.Callable[M.Result] {
          def call(): M.Result=M.fit(points)
        }))
        jobs.foreach(_.get(10,java.util.concurrent.TimeUnit.SECONDS) shouldBe expected)
      } finally {
        pool.shutdownNow()
        pool.awaitTermination(10,java.util.concurrent.TimeUnit.SECONDS) shouldBe true
      }
    }
  }
}
