package com.cra.figaro.test.modernization

import com.cra.figaro.algorithm.sampling.{VectorImportance as V, VectorSliceSampler as VS, InferenceHealth as H}
import com.cra.figaro.algorithm.sampling.parallel.MultiChainVectorSliceSampler as MC
import com.cra.figaro.library.atomic.continuous.MultivariateGaussianDistribution as G
import com.cra.figaro.util.{SamplingRandom, RandomStreams}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import java.util.concurrent.atomic.AtomicInteger

class VectorImportanceTest extends AnyWordSpec with Matchers {
  private val box = V.Box(Vector(-10.0),Vector(10.0))
  private val gaussian = V.Gaussian(G(Vector(0.0),Vector(Vector(1.0))))
  private val starts = Vector(-2.0,-.5,.5,2.0).map(Vector(_))
  private def pilot = MC.Config(VS.Config(VS.Method.Quantile,draws=100,warmUp=20,maxEvaluations=3000,seed=42),parallelism=1)
  private def target(x: Vector[Double]) = -x.head*x.head/2
  "Public vector importance sampling" should {
    "evaluate normalized built-in densities including mixture support and extreme log scales" in {
      box.logDensity(Vector(10.0)) shouldBe -math.log(20)
      box.logDensity(Vector(11.0)) shouldBe Double.NegativeInfinity
      val mixture = V.Mixture(Vector(1.0,9.0),Vector(box,gaussian))
      for(x <- Vector(-12.0,0.0,3.0)) {
        val expected = .9*math.exp(-x*x/2)/math.sqrt(2*math.Pi)+(if(math.abs(x)<=10) .1/20 else 0)
        math.exp(mixture.logDensity(Vector(x))) shouldBe (expected +- 1e-14)
      }
      val t=V.ProductStudentT(Vector(0.0),Vector(2.0),1)
      math.exp(t.logDensity(Vector(2.0))) shouldBe (1/(4*math.Pi) +- 1e-14)
      t.sample(SamplingRandom.scalaRandom(1L)).forall(_.isFinite) shouldBe true
      V.Mixture(Vector(1e300,1e300),Vector(box,gaussian)).probabilities shouldBe Vector(.5,.5)
      intercept[IllegalArgumentException](V.Mixture(Vector(1e-300,1e300),Vector(box,gaussian)))
    }
    "retain each draw, use the full ratio and charge zero-target evaluations without retrying" in {
      val calls = new AtomicInteger
      val q = V.Mixture(Vector(.1,.9),Vector(box,gaussian))
      val r = V.run(V.Config(draws=2000,maxEvaluations=1234),q,x => { calls.incrementAndGet(); if(x.head>0) target(x) else Double.NegativeInfinity },_.head)
      r.reason shouldBe V.StopReason.MaxEvaluationsReached
      r.samples.size shouldBe 1234
      calls.get shouldBe 1234
      r.logWeights shouldBe r.samples.map(x => (if(x.head>0) target(x) else Double.NegativeInfinity)-q.logDensity(x))
      r.logWeights.count(_ == Double.NegativeInfinity) should be > 0
      r.health.diagnostics.samples shouldBe 1234
      val replay = V.run(r.config,q,x => if(x.head>0) target(x) else Double.NegativeInfinity,_.head)
      replay shouldBe r
    }
    "refuse malformed callbacks and numerical failures without publishing partial results" in {
      for(bad <- Vector(Double.NaN,Double.PositiveInfinity))
        intercept[IllegalArgumentException](V.run(V.Config(),box,_ => bad,_.head))
      intercept[IllegalArgumentException](V.run(V.Config(),box,target,_ => Double.NaN))
      val badProposal = new V.Proposal {
        val dimension=1
        def sample(r: scala.util.Random) = Vector(0.0)
        def logDensity(x: Vector[Double]) = Double.NegativeInfinity
      }
      intercept[IllegalArgumentException](V.run(V.Config(),badProposal,target,_.head))
      intercept[IllegalArgumentException](V.run(V.Config(maxStoredValues=10),box,target,_.head))
      intercept[IllegalArgumentException](V.Box(Vector(0.0),Vector(0.0)))
      intercept[IllegalArgumentException](V.ProductStudentT(Vector(0.0),Vector(-1.0)))
      val failure = new IllegalStateException("caller callback")
      intercept[IllegalStateException](V.run(V.Config(),box,_ => throw failure,_.head)) shouldBe failure
    }
    "expose all-zero weights and constant rare-event queries without claiming precision" in {
      val zero=V.run(V.Config(draws=200),box,_ => Double.NegativeInfinity,_.head)
      zero.health.status shouldBe H.Status.Danger
      zero.health.diagnostics.mean shouldBe None
      val constant=V.run(V.Config(draws=200),box,target,_ => 0.0)
      constant.health.status should not be H.Status.ChecksPassed
      constant.health.issues.map(_.code) should contain(H.Code.ConstantObservable)
    }
    "fit immutable moments with only explicit regularization and clear insufficient/degenerate refusals" in {
      val points=Vector(Vector(-1.0,-2.0),Vector(0.0,0.0),Vector(1.0,2.0),Vector(2.0,4.0),Vector(3.0,6.0))
      val chains=Vector.fill(4)(points)
      V.fitGaussian(chains).status shouldBe V.FitStatus.NumericalFailure
      val fit=V.fitGaussian(chains,V.FitConfig(diagonalRidge=Vector(.01,.02)))
      fit.status shouldBe V.FitStatus.Fitted
      fit.proposal.get.law.mean shouldBe Vector(1.0,2.0)
      fit.proposal.get.law.covariance(0)(0) shouldBe (80.0/19+.01 +- 1e-12)
      V.fitGaussian(Vector.empty).status shouldBe V.FitStatus.InsufficientPilot
      V.fitGaussian(Vector.fill(4)(Vector.fill(5)(Vector(1.0)))).status shouldBe V.FitStatus.DegeneratePilot
      intercept[IllegalArgumentException](V.fitGaussian(chains,V.FitConfig(maxPilotValues=1)))
      intercept[IllegalArgumentException](V.fitGaussian(chains,V.FitConfig(diagonalRidge=Vector(.01))))
    }
    "separate pilot and production work, preserve scheduling determinism and never fall back silently" in {
      val r=V.runWithPilot(pilot,starts,box,V.Config(draws=1000),target,_.head)
      r.fit.status shouldBe V.FitStatus.Fitted
      r.production.get.samples.size shouldBe 1000
      r.totalEvaluations shouldBe r.pilotEvaluations+1000
      val parallel=V.runWithPilot(pilot.copy(parallelism=4),starts,box,V.Config(draws=1000),target,_.head)
      parallel.pilot.chains shouldBe r.pilot.chains
      parallel.fit shouldBe r.fit
      parallel.production shouldBe r.production
      val tiny=pilot.copy(sampler=pilot.sampler.copy(maxEvaluations=1))
      val refused=V.runWithPilot(tiny,starts,box,V.Config(),target,_.head)
      refused.fit.status shouldBe V.FitStatus.InsufficientPilot
      refused.production shouldBe None
      refused.totalEvaluations shouldBe 4
    }
    "reject production configuration and seeded-stream collisions before pilot callbacks" in {
      val count=new AtomicInteger
      val f: Vector[Double] => Double = x => { count.incrementAndGet(); target(x) }
      intercept[IllegalArgumentException](V.runWithPilot(pilot,starts,box,V.Config(seed=42),f,_.head))
      val collision=RandomStreams.allocate(42,4,pilot.sampler.randomAlgorithm).head.seed
      intercept[IllegalArgumentException](V.runWithPilot(pilot,starts,box,V.Config(seed=collision),f,_.head))
      intercept[IllegalArgumentException](V.runWithPilot(pilot,starts,box,V.Config(maxStoredValues=2),f,_.head))
      count.get shouldBe 0
    }
    "preserve cancellation during callbacks and clean up pilot workers on failure" in {
      intercept[InterruptedException] {
        try V.run(V.Config(),box,x => { Thread.currentThread().interrupt(); target(x) },_.head)
        finally { Thread.currentThread().isInterrupted shouldBe true; Thread.interrupted() }
      }
      val error=intercept[MC.ChainFailure](V.runWithPilot(pilot,starts,box,V.Config(),_ => throw new IllegalStateException("pilot failed"),_.head))
      error.getCause.getMessage shouldBe "pilot failed"
      import scala.jdk.CollectionConverters.*
      Thread.getAllStackTraces.keySet().asScala.exists(t => t.isAlive && t.getName.startsWith("figaro-vector")) shouldBe false
    }
    "demonstrate that nominal full support does not guarantee discovery of a distant mode" in {
      val left=V.Gaussian(G(Vector(-8.0),Vector(Vector(.0001))))
      val right=V.Gaussian(G(Vector(8.0),Vector(Vector(.0001))))
      val truth=V.Mixture(Vector(.5,.5),Vector(left,right))
      val missed=V.run(V.Config(draws=2000),left,truth.logDensity,x => if(x.head>0) 1.0 else 0.0)
      missed.health.diagnostics.mean shouldBe Some(0.0) // true event probability is 0.5
      missed.health.status should not be H.Status.ChecksPassed
    }
  }
}
