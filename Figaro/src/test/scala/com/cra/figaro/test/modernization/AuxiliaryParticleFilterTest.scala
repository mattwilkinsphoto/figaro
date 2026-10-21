package com.cra.figaro.test.modernization

import com.cra.figaro.algorithm.filtering.{AuxiliaryParticleProposal as A, BoundedParticleFilter as B,
  WeightedBoundedParticleFilter as W, WeightedParticleProposal as P, WeightedParticleCalibration as C}
import com.cra.figaro.library.atomic.continuous.MultivariateGaussianDistribution as G
import com.cra.figaro.util.{random, withRandomSeed}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class AuxiliaryParticleFilterTest extends AnyWordSpec with Matchers {
  private val p = P.gaussian(0,1)
  private def transition(x: Double) = P.gaussian(.8*x,.5)
  private def model = B.Model(p.sample,(x:Double) => transition(x).sample())
  private def likelihood(y:Double)(x:Double) = P.gaussian(x,.3).logDensity(y)
  private def kernel(y:Double) = P.Kernel(p,transition,P.gaussianObservation(0,1,y,.3),
    (x:Double) => P.gaussianObservation(.8*x,.5,y,.3))
  private def look(y:Double,fraction:Double=0) = A.LookAhead[Double](
    x => P.gaussianPredictiveLogLikelihood(.8*x,.5,y,.3),fraction)
  private val evidence = W.Evidence[Double](_ => 0.0,_ => 1.0)

  "Auxiliary filtering" should {
    "normalize full defensive ancestor probabilities and correct their complete law" in {
      val w=Vector(.2,.8,0.0)
      val a=A.prepare(Vector(0,1,2),w,A.LookAhead[Int](i => if(i==2)throw new Exception("zero parent") else math.log(i+1.0),.1))
      a.report.evaluations shouldBe 2
      a.probabilities(0) shouldBe (.12 +- 1e-14)
      a.probabilities(1) shouldBe (.88 +- 1e-14)
      a.probabilities(2) shouldBe 0.0
      w.indices.foreach(i => a.probabilities(i)*math.exp(a.logCorrections(i)) shouldBe (w(i) +- 1e-14))
      val shifted=A.prepare(Vector(0,1,2),w,A.LookAhead[Int](i => 10000+math.log(i+1.0),.1))
      a.probabilities.zip(shifted.probabilities).foreach((x,y) => x shouldBe (y +- 1e-12))
    }
    "preserve support for zero look-ahead scores and disclose an all-zero fallback" in {
      val a=A.prepare(Vector(0,1),Vector(.25,.75),A.LookAhead[Int](i => if(i==0)0 else Double.NegativeInfinity,.2))
      a.probabilities.zip(Vector(.85,.15)).foreach((x,y) => x shouldBe (y +- 1e-14))
      val b=A.prepare(Vector(0,1),Vector(.25,.75),A.LookAhead[Int](_ => Double.NegativeInfinity))
      b.probabilities shouldBe Vector(.25,.75)
      b.report.usedPriorFallback shouldBe true
      intercept[IllegalArgumentException](A.prepare(Vector(0,1),Vector(.25,.75),A.LookAhead[Int](_ => Double.NegativeInfinity,0)))
      for(bad <- Vector(Double.NaN,Double.PositiveInfinity))
        intercept[IllegalArgumentException](A.prepare(Vector(0),Vector(1.0),A.LookAhead[Int](_ => bad)))
      intercept[IllegalArgumentException](A.LookAhead[Int](_ => 0,-.1))
      val tiny=A.prepare(Vector(0,1),Vector(.5,.5),A.LookAhead[Int](i => -1000.0*i,0))
      tiny.logProbabilities(1) shouldBe (-1000.0 +- 1e-12)
      tiny.logCorrections(1) shouldBe (1000.0-math.log(2) +- 1e-12)
    }
    "give constant corrected weights under exact Gaussian adaptation without losing evidence normalization" in {
      val f=W(model,W.Config(128,128,1,seed=42))
      try {
        val first=f.start(W.Evidence(likelihood(.2),_ => 1),kernel(.2))
        val expected=first.weights.zip(first.particles).map((w,x) => w*math.exp(look(1.3).logMultiplier(x))).sum
        val r=f.advanceAuxiliary(W.Evidence(likelihood(1.3),_ => 1),look(1.3),Some(kernel(1.3)))
        r.weights.foreach(w => w shouldBe (1.0/128 +- 1e-12))
        r.logEvidence-first.logEvidence shouldBe (math.log(expected) +- 1e-12)
        f.lastAuxiliary.get.evaluations shouldBe 128
      } finally f.close()
    }
    "replay ordinary filtering exactly with a unit defensive share and no look-ahead calls" in {
      val f=W(model,W.Config(16,16,1,seed=53)); val g=W(model,W.Config(16,16,1,seed=53))
      try {
        f.start(evidence) shouldBe g.start(evidence)
        val a=f.advanceAuxiliary(evidence,A.LookAhead[Double](_ => throw new Exception("unused"),1))
        a shouldBe g.advance(evidence)
        f.lastAuxiliary.get.evaluations shouldBe 0
        f.advance(evidence)
        f.lastAuxiliary shouldBe None
      } finally { f.close();g.close() }
    }
    "prepare once before calibration and account separately for pilots production and look-ahead" in {
      val f=W(model,W.Config(24,24,1,seed=17)); var calls=0
      try {
        f.start(evidence)
        val a=A.LookAhead[Double](x => {calls+=1; -x*x})
        val r=f.advanceAuxiliaryCalibrated(likelihood(.4),C.Request(300,16,32,8,10),a,Some(kernel(.4)))
        calls shouldBe 24
        f.lastAuxiliary.get.evaluations shouldBe 24
        f.lastCalibration.get.pilotCandidates shouldBe 48
        f.lastCalibration.get.pilotCandidates+r.report.attempts should be <= 300
        r.weights.sum shouldBe (1.0 +- 1e-12)
      } finally f.close()
    }
    "replay calibrated auxiliary steps and restore ambient randomness" in {
      def run() = {
        val f=W(model,W.Config(32,32,1,seed=91))
        try {
          f.start(evidence)
          val r=f.advanceAuxiliaryCalibrated(likelihood(.3),C.Request(300,16,32,8,10),look(.3,.1),Some(kernel(.3)))
          (r,f.lastCalibration,f.lastAuxiliary)
        } finally f.close()
      }
      run() shouldBe run()
      val expected=withRandomSeed(82){Vector.fill(3)(random.nextDouble())}
      val actual=withRandomSeed(82){val a=random.nextDouble();run();Vector(a,random.nextDouble(),random.nextDouble())}
      actual shouldBe expected
    }
    "invalidate after callback failure and preserve diagnostics only for genuine collapse" in {
      val f=W(model,W.Config(8,8,1))
      f.start(evidence)
      intercept[IllegalArgumentException](f.advanceAuxiliary(evidence,A.LookAhead[Double](_ => Double.NaN)))
      f.lastReport shouldBe None; f.lastAuxiliary shouldBe None
      intercept[IllegalArgumentException](f.current); f.close()
      val g=W(model,W.Config(8,8,1)); g.start(evidence)
      intercept[W.NoViableParticles](g.advanceAuxiliary(W.Evidence[Double](_ => Double.NegativeInfinity,_ => 1),look(0)))
      g.lastAuxiliary.get.evaluations shouldBe 8
      g.lastReport.get.health.positiveWeights shouldBe 0
      intercept[IllegalArgumentException](g.current);g.close()
    }
    "reject use before start foreign threads reentrancy and interrupted preparation" in {
      val f=W(model,W.Config(8,8,1))
      intercept[IllegalArgumentException](f.advanceAuxiliary(evidence,look(0)))
      f.start(evidence)
      val error=new java.util.concurrent.atomic.AtomicReference[Throwable]()
      val t=new Thread(() => {try { f.advanceAuxiliary(evidence,look(0)); () } catch {case e:Throwable => error.set(e)}})
      t.start(); t.join(); error.get().isInstanceOf[IllegalArgumentException] shouldBe true
      intercept[IllegalArgumentException](f.advanceAuxiliary(evidence,A.LookAhead[Double](_ => f.current.logEvidence)))
      f.close()
      val g=W(model,W.Config(8,8,1));g.start(evidence)
      Thread.currentThread().interrupt()
      try intercept[InterruptedException](g.advanceAuxiliary(evidence,look(0)))
      finally Thread.interrupted()
      g.lastAuxiliary shouldBe None;intercept[IllegalArgumentException](g.current);g.close()
    }
    "match exhaustive two-step evidence and unnormalized mass including bounded stopping" in {
      for(cap <- 2 to 3; floor <- 2 to cap; target <- Vector(.8,1.5); fraction <- Vector(0.0,.2)) {
        val config=W.Config(floor,cap,target)
        var evidenceMean=0.0; var massMean=0.0
        for(bits <- 0 until (1<<cap)) {
          val xs=Vector.tabulate(cap)(i => (bits>>i)&1)
          val pathProbability=xs.map(x => if(x==1).3 else .7).product
          var j = -1
          val first=W.weightedStep(config,false)(() => {j+=1;(xs(j),-1,math.log(if(xs(j)==1)1.7 else .6))},W.cappedLikelihood(0))
          val prep=A.prepare(first.values,first.weights,A.LookAhead[Int](x => math.log(if(x==1).1 else 3.0),fraction))
          val categories=first.values.size*2
          val paths=math.pow(categories,cap).toInt
          for(code <- 0 until paths) {
            var n=code
            val draws=Vector.fill(cap){val v=n%categories;n/=categories;v}
            val probability=draws.map { v =>
              val parent=v/2; val x=v%2; val q=if(first.values(parent)==1).4 else .7
              prep.probabilities(parent)*(if(x==1)q else 1-q)
            }.product
            var k = -1
            val second=W.weightedStep(config,true)(() => {
              k+=1; val parent=draws(k)/2;val x=draws(k)%2
              val p1=if(first.values(parent)==1).8 else .2
              val q1=if(first.values(parent)==1).4 else .7
              val ratio=(if(x==1)p1/q1 else (1-p1)/(1-q1))*(if(x==1)1.3 else .4)
              (x,parent,math.log(ratio)+prep.logCorrections(parent))
            },W.cappedLikelihood(0))
            val z=math.exp(first.logIncrement+second.logIncrement)*pathProbability*probability
            evidenceMean+=z
            massMean+=z*second.values.zip(second.weights).map((x,w) => x*w).sum
          }
        }
        val expectedMass=(.7*.6*.2+.3*1.7*.8)*1.3
        val expected=expectedMass+(.7*.6*.8+.3*1.7*.2)*.4
        evidenceMean shouldBe (expected +- 2e-11)
        massMean shouldBe (expectedMass +- 2e-11)
      }
    }
  }
  "Gaussian observation helpers" should {
    "handle multiple correlated observation coordinates and disparate physical scales" in {
      val covariance=Vector(Vector(2.0,.5),Vector(.5,1.0))
      val h=Vector(Vector(1.0,2.0),Vector(-1.0,.5))
      val noise=Vector(Vector(.5,.1),Vector(.1,.8))
      val y=Vector(1.5,-.2);val offset=Vector(.25,-.4)
      val prior=G(Vector(1.0,-1.0),covariance)
      val guide=P.linearGaussianObservation(prior,y,h,noise,offset)
      for(x <- Vector(Vector(0.0,0.0),Vector(2.0,-1.0),Vector(-1.0,3.0))) {
        val m=h.map(row => row.zip(x).map(_*_).sum).zip(offset).map(_+_)
        prior.logDensity(x)+G(m,noise).logDensity(y)-guide.proposal.logDensity(x) shouldBe (guide.logPredictive +- 1e-10)
      }
      // Change state units, leaving the observed law fixed. No arbitrary jitter is needed.
      val units=Vector(1e20,1e-20)
      val scaled=G(prior.mean.zip(units).map(_*_),Vector.tabulate(2,2)((i,j)=>covariance(i)(j)*units(i)*units(j)))
      val hs=h.map(row=>row.zip(units).map(_/_))
      val converted=P.linearGaussianObservation(scaled,y,hs,noise,offset)
      converted.logPredictive shouldBe (guide.logPredictive +- 1e-10)
      converted.posterior.mean.zip(units).map(_/_).zip(guide.posterior.mean).foreach((a,b)=>a shouldBe (b +- 1e-10))
      for(i<-0 until 2;j<-0 until 2)
        converted.posterior.covariance(i)(j)/units(i)/units(j) shouldBe (guide.posterior.covariance(i)(j) +- 1e-10)
    }
    "match a rational rectangular-observation control and the joint density identity" in {
      val prior=G(Vector(1.0,-1.0),Vector(Vector(2.0,.5),Vector(.5,1.0)))
      val guide=P.linearGaussianObservation(prior,Vector(1.5),Vector(Vector(1.0,2.0)),Vector(Vector(.5)),Vector(.25))
      guide.posterior.mean.zip(Vector(61.0/34,-23.0/68)).foreach((a,b) => a shouldBe (b +- 1e-10))
      guide.posterior.covariance.flatten.zip(Vector(16.0/17,-13.0/34,-13.0/34,9.0/34)).foreach((a,b) => a shouldBe (b +- 1e-10))
      guide.logPredictive shouldBe (P.gaussian(-.75,math.sqrt(8.5)).logDensity(1.5) +- 1e-10)
      for(x <- Vector(Vector(0.0,0.0),Vector(2.0,-1.0),Vector(-1.0,3.0))) {
        val joint=prior.logDensity(x)+P.gaussian(x(0)+2*x(1)+.25,math.sqrt(.5)).logDensity(1.5)
        joint-guide.proposal.logDensity(x) shouldBe (guide.logPredictive +- 1e-10)
      }
    }
    "reduce to scalar conditioning and reject malformed singular or zero-noise inputs" in {
      val prior=G(Vector(0.0),Vector(Vector(1.0)))
      val g=P.linearGaussianObservation(prior,Vector(2.0),Vector(Vector(1.0)),Vector(Vector(.25)))
      g.posterior.mean.head shouldBe (1.6 +- 1e-10)
      g.posterior.covariance.head.head shouldBe (.2 +- 1e-10)
      g.logPredictive shouldBe (P.gaussianPredictiveLogLikelihood(0,1,2,.5) +- 1e-10)
      intercept[IllegalArgumentException](P.linearGaussianObservation(prior,Vector(2.0),Vector(Vector(1.0)),Vector(Vector(0.0))))
      intercept[IllegalArgumentException](P.linearGaussianObservation(prior,Vector(2.0),Vector(Vector(1.0,2.0)),Vector(Vector(1.0))))
      intercept[IllegalArgumentException](P.gaussianPredictiveLogLikelihood(0,1,2,0))
    }
  }
}
