package com.cra.figaro.test.modernization

import com.cra.figaro.algorithm.filtering.{BoundedParticleFilter as B, WeightedBoundedParticleFilter as W,
  WeightedParticleCalibration as C, WeightedParticleProposal as P}
import com.cra.figaro.util.{random,withRandomSeed}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class CalibratedGuidedFilterTest extends AnyWordSpec with Matchers {
  private def model = B.Model(() => random.nextGaussian(),(x:Double) => .8*x+math.sqrt(.3)*random.nextGaussian())
  private def evidence = W.Evidence[Double](_ => 0.0,_ => 1.0)
  "Calibrated guided filtering" should {
    "validate total work and probability preferences" in {
      intercept[IllegalArgumentException](C.Request(maxTotalCandidates=640))
      intercept[IllegalArgumentException](C.Request(scaleSamples=Int.MaxValue))
      intercept[IllegalArgumentException](C.Request(scaleQuantile=Double.NaN))
      intercept[IllegalArgumentException](C.Request(scoreTarget=Double.PositiveInfinity))
      intercept[IllegalArgumentException](C.Request(confidence=1))
    }
    "use disjoint fixed pilots and a frozen scale without changing weights" in {
      var n=0
      val r=C.run(C.Request(1000,4,128,2,10,scaleQuantile=.5),1)(() => {
        n+=1; if(n<=4) Vector(-4.0,-3.0,-2.0,-1.0)(n-1) else -10.0
      })
      n shouldBe 132
      r.logScale shouldBe Some(-3.0)
      r.meanScore.get shouldBe (math.exp(-7) +- 1e-15)
      r.status shouldBe C.Status.UnresolvedScoreMean
      r.minCandidates shouldBe 868
      r.maxCandidates shouldBe 868
    }
    "choose the smallest conservative cap above the floor and include pilot cost" in {
      val request=C.Request(2000,16,512,2,100)
      val a=C.run(request,1)(() => 5.0)
      a.status shouldBe C.Status.Ready
      a.meanScore shouldBe Some(1.0)
      a.missBound.get should be <= request.missProbability
      C.miss(a.maxCandidates-1,a.lowerMean,100) should be > request.missProbability
      a.maximumTotalCandidates should be <= request.maxTotalCandidates
      a.config(W.Config()).minCandidates shouldBe 100
    }
    "retain production even when no scale or adequate adaptive cap is found" in {
      var n=0
      val f=W(B.Model(()=>{n+=1;n},(x:Int)=>x),W.Config(2,2,1))
      val request=C.Request(200,4,16,2,1)
      try {
        val r=f.startCalibrated(x => if(x<=20) Double.NegativeInfinity else math.log(2),request)
        f.lastCalibration.get.status shouldBe C.Status.NoFiniteScale
        r.report.attempts shouldBe 180
        r.particles.min shouldBe 21
        n shouldBe 200
        r.logEvidence shouldBe (math.log(2) +- 1e-14)
      } finally f.close()
      val insufficient=C.run(C.Request(100,2,16,2,80),1)(() => 0.0)
      insufficient.status shouldBe C.Status.InsufficientBudget
      insufficient.minCandidates shouldBe insufficient.maxCandidates
    }
    "keep pilot work diagnostics on genuine production collapse without retries" in {
      var calls=0
      val f=W(B.Model(()=>{calls+=1;0},(x:Int)=>x),W.Config(2,2,1))
      try {
        val e=intercept[W.NoViableParticles](f.startCalibrated(_=>Double.NegativeInfinity,C.Request(100,4,16,2,1)))
        calls shouldBe 100
        e.report.attempts shouldBe 80
        f.lastCalibration.get.pilotCandidates shouldBe 20
        intercept[IllegalArgumentException](f.current)
      } finally f.close()
    }
    "restore ambient randomness and replay calibration plus production" in {
      def run() = {
        val f=W(model,W.Config(seed=53))
        try {
          val a=f.startCalibrated(x=> -x*x/2,C.Request(1000,16,128,16,20))
          val b=f.advanceCalibrated(x=> -(x-1)*(x-1)/2,C.Request(1000,16,128,16,20))
          (a,b,f.lastCalibration)
        } finally f.close()
      }
      run() shouldBe run()
      def ambient(work:Boolean)=withRandomSeed(777){val a=random.nextDouble();if(work)run();(a,random.nextDouble())}
      ambient(true) shouldBe ambient(false)
    }
    "fail closed on pilot callbacks interruption and reentrant use" in {
      val f=W(model)
      intercept[IllegalStateException](f.startCalibrated(_=>throw new IllegalStateException("pilot"),C.Request()))
      f.lastCalibration shouldBe None
      intercept[IllegalArgumentException](f.current)
      f.close()
      val g=W(model)
      intercept[IllegalArgumentException](g.startCalibrated(_=>{g.close();0.0},C.Request()))
      g.close()
      val h=W(model)
      try intercept[InterruptedException](h.startCalibrated(_=>{Thread.currentThread().interrupt();0.0},C.Request()))
      finally {Thread.interrupted();h.close()}
    }
    "reject invalid proposal densities and correction overflow" in {
      val prior=P.Law(()=>0.0,(_:Double)=>0.0)
      for(bad <- Vector(Double.NaN,Double.PositiveInfinity,Double.NegativeInfinity)) {
        val q=P.Law(()=>0.0,(_:Double)=>bad)
        val f=W(model,W.Config(2,2,1))
        try intercept[IllegalArgumentException](f.start(evidence,P.Kernel(prior,_=>prior,q,_=>q)))
        finally f.close()
      }
      val p=P.Law(()=>0.0,(_:Double)=>1e308)
      val q=P.Law(()=>0.0,(_:Double)=> -1e308)
      val f=W(model,W.Config(2,2,1))
      try intercept[ArithmeticException](f.start(evidence,P.Kernel(p,_=>p,q,_=>q))) finally f.close()
    }
    "apply initial and parent-conditional density corrections exactly once" in {
      def law(p:Double)=P.Law(()=>if(random.nextDouble()<p)1 else 0,(x:Int)=>math.log(if(x==1)p else 1-p))
      val p=law(.3); val q=law(.8)
      val k=P.Kernel(p,(x:Int)=>law(if(x==1).7 else .2),q,(_:Int)=>q)
      val f=W(B.Model(()=>throw new IllegalStateException("bootstrap used"),(_:Int)=>throw new IllegalStateException("bootstrap used")),W.Config(100,100,1,22))
      try {
        val a=f.start(W.Evidence[Int](_=>math.log(2),_=>1),k)
        val raw=a.particles.map(x=>2*(if(x==1).3/.8 else .7/.2))
        a.weights.zip(raw).foreach((w,r)=>w shouldBe (r/raw.sum +- 1e-14))
        a.logEvidence shouldBe (math.log(raw.sum/raw.size) +- 1e-14)
        val b=f.advance(W.Evidence[Int](_=>0,_=>1),k)
        b.report.distinctParents.get should be > 1
        b.weights.sum shouldBe (1.0 +- 1e-14)
      } finally f.close()
    }
    "use the full defensive mixture density including both branches" in {
      val p=P.Law(()=>0,(x:Int)=>if(x==0)0.0 else Double.NegativeInfinity)
      val q=P.Law(()=>1,(x:Int)=>if(x==1)0.0 else Double.NegativeInfinity)
      val mixture=P.defensive(p,q,.2)
      mixture.logDensity(0) shouldBe (math.log(.2) +- 1e-14)
      mixture.logDensity(1) shouldBe (math.log(.8) +- 1e-14)
      mixture.logDensity(2) shouldBe Double.NegativeInfinity
      intercept[IllegalArgumentException](P.defensive(p,q,0))
      val draws=withRandomSeed(12){Vector.fill(100)(mixture.sample())}
      draws.toSet shouldBe Set(0,1)
    }
    "match Gaussian conditioning and preserve its constant corrected initial weight" in {
      val q=P.gaussianObservation(0,1,2,.5)
      val oracle=P.gaussian(1.6,math.sqrt(.2))
      for(x<-Vector(-3.0,0.0,1.6,4.0)) q.logDensity(x) shouldBe (oracle.logDensity(x) +- 1e-13)
      val p=P.gaussian(0,1)
      val k=P.Kernel(p,(_:Double)=>p,q,(_:Double)=>q)
      val f=W(model,W.Config(100,100,1))
      try {
        val r=f.start(W.Evidence[Double](x=>P.gaussian(x,.5).logDensity(2),_=>1),k)
        r.logEvidence shouldBe (P.gaussian(0,math.sqrt(1.25)).logDensity(2) +- 1e-13)
        r.weights.foreach(w=>w shouldBe (.01 +- 1e-14))
      } finally f.close()
    }
    "match exact finite guided stopping trees for evidence and state mass" in {
      // Enumerate q draws, including unused suffixes. Non-prior q=.8; p=.3.
      for(cap<-2 to 6; floor<-2 to cap; target<-Vector(.6,1.2,2.0)) {
        var z=0.0; var mass=0.0
        for(bits<-0 until (1<<cap)) {
          val xs=Vector.tabulate(cap)(i=>(bits>>i)&1)
          val probability=xs.map(x=>if(x==1).8 else .2).product
          var i = -1
          val r=W.weightedStep(W.Config(floor,cap,target),false)(()=>{
            i+=1; val x=xs(i); (x,-1,math.log(if(x==1)1.4*.3/.8 else .2*.7/.2))
          },w=>W.cappedLikelihood(0)(w))
          z+=probability*math.exp(r.logIncrement)
          mass+=probability*math.exp(r.logIncrement)*r.values.zip(r.weights).map((x,w)=>x*w).sum
        }
        z shouldBe (.56 +- 2e-12)
        mass shouldBe (.42 +- 2e-12)
      }
    }
    "apply corrected scores and retain zero target mass without treating it as density failure" in {
      var n=0
      val p=P.Law(()=>0,(x:Int)=>if(x==0)0.0 else Double.NegativeInfinity)
      val q=P.Law(()=>{n+=1;n%2},(_:Int)=>math.log(.5))
      val k=P.Kernel(p,(_:Int)=>p,q,(_:Int)=>q)
      val f=W(B.Model(()=>0,(x:Int)=>x),W.Config(2,4,1.5))
      try {
        val r=f.start(W.Evidence[Int](_=>0,w=>if(w==Double.NegativeInfinity)0 else .75),k)
        r.report.attempts shouldBe 4
        r.report.retainedCandidates shouldBe 3
        r.particles shouldBe Vector(1,0,1)
        r.weights shouldBe Vector(0.0,1.0,0.0)
        r.logEvidence shouldBe (math.log(2.0/3) +- 1e-14)
      } finally f.close()
    }
    "clear calibration on manual steps and reject foreign-thread calibration" in {
      val f=W(model,W.Config(8,8,1))
      try {
        f.startCalibrated(_=>0,C.Request(200,8,32,2,4))
        f.lastCalibration should not be empty
        f.advance(evidence)
        f.lastCalibration shouldBe None
        val error=new java.util.concurrent.atomic.AtomicReference[Throwable]()
        val thread=new Thread(()=>{try {f.advanceCalibrated(_=>0,C.Request()); ()} catch {case e:Throwable=>error.set(e)}})
        thread.start();thread.join()
        error.get().isInstanceOf[IllegalArgumentException] shouldBe true
        f.current.report.attempts shouldBe 8
      } finally f.close()
    }
    "bound Bernoulli score misses conservatively against exact enumeration" in {
      for(n<-2 to 12; p<-Vector(.2,.5,.8); target<-Vector(.5,1.5,2.5)) {
        val exact=(0 until (1<<n)).map { b=>
          val k=Integer.bitCount(b)
          if(k<target)math.pow(p,k)*math.pow(1-p,n-k) else 0.0
        }.sum
        exact should be <= (C.miss(n,p,target)+1e-12)
      }
    }
    "cover bounded-score pilot means by exact binomial summation" in {
      for(n<-Vector(2,8,32); p<-Vector(.01,.2,.5,.8,.99); confidence<-Vector(.8,.95,.99)) {
        var noncoverage=0.0
        for(k<-0 to n) {
          var i = -1
          val a=C.run(C.Request(1000,1,n,2,1,confidence=confidence),1)(()=>{
            i+=1; if(i==0 || i<=k)0.0 else Double.NegativeInfinity
          })
          val choose=(1 to k).foldLeft(1.0)((v,j)=>v*(n-j+1)/j)
          if(a.lowerMean>p)noncoverage+=choose*math.pow(p,k)*math.pow(1-p,n-k)
        }
        noncoverage should be <= (1-confidence+1e-12)
      }
    }
    "match Gaussian evidence and unnormalized moments with work-inclusive calibration" in {
      CalibratedGuidedFilterStudy.run().foreach { r=>
        info(r.line)
        math.abs(r.mean-r.expected) should be <= (6*r.standardError+1e-12)
      }
    }
  }
}
