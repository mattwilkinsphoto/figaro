package com.cra.figaro.test.modernization

import com.cra.figaro.algorithm.filtering.{FixedBudgetParticleFilter as F, BoundedParticleFilter as B,
  WeightedParticleProposal as P, AuxiliaryParticleProposal as A}
import com.cra.figaro.library.atomic.continuous.MultivariateGaussianDistribution
import com.cra.figaro.util.random
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class GuidedFixedBudgetTest extends AnyWordSpec with Matchers {
  def cat(p:Double):P.Law[Int]=P.Law(()=>if(random.nextDouble()<p)0 else 1,
    x=>math.log(if(x==0)p else 1-p))
  val target=cat(.3)
  val q=cat(.8)
  val kernel=P.Kernel(target,(_:Int)=>target,q,(_:Int)=>q)
  def make(policy:F.Policy=F.Policy.Always,method:F.Method=F.Method.LogTreeMultinomial,n:Int=32)=
    F(B.Model(()=>target.sample(),(_:Int)=>target.sample()),F.Config(n,policy,method,seed=71))
  def check(r:F.Update[Int],raw:Vector[Double],previous:Double):Unit={
    r.logEvidence shouldBe (previous+math.log(raw.sum) +- 2e-12)
    r.weights.zip(raw).foreach((w,x)=> w shouldBe (x/raw.sum +- 2e-12))
  }
  "Guided fixed-budget accounting" should {
    "correct initialization and carry using the full proposal law" in {
      val f=make(F.Policy.WhenEssBelow(.01))
      try {
        val r=f.start(x=>math.log(if(x==0).9 else .2),kernel)
        check(r,r.particles.map(x=>(if(x==0).3*.9/.8 else .7*.2/.2)/32),0)
        val s=f.advance(x=>math.log(if(x==0).4 else .6),kernel)
        s.report.decision shouldBe F.Decision.CarriedWeights
        check(s,s.particles.indices.map(i=>r.weights(i)*(if(s.particles(i)==0).3*.4/.8 else .7*.6/.2)).toVector,r.logEvidence)
        f.lastProposalReport.get.targetDensityCalls shouldBe 32
      }finally f.close()
    }
    "correct ordinary and auxiliary selection including its normalizer for both methods" in {
      for(method<-F.Method.values){
        // State carries parent identity so the independent formula can recover actual ancestry.
        var index= -1
        val f=F(B.Model(()=>{index+=1;(index,0)},(x:(Int,Int))=>x),F.Config(32,F.Policy.Always,method,71))
        val initial=f.start(x=>math.log(x._1+1.0))
        val targetLaw=(parent:(Int,Int))=>P.Law(()=>(parent._1,0),
          (x:(Int,Int))=>math.log(if(x._2==0).3 else .7))
        val proposalLaw=(parent:(Int,Int))=>P.Law(()=>(parent._1,if(random.nextDouble()<.8)0 else 1),
          (x:(Int,Int))=>math.log(if(x._2==0).8 else .2))
        val k=P.Kernel(targetLaw((0,0)),targetLaw,proposalLaw((0,0)),proposalLaw)
        val look=A.LookAhead[(Int,Int)](x=>math.log(33.0-x._1),.2)
        val z=initial.weights.indices.map(i=>initial.weights(i)*(33-i)).sum
        val r=f.advanceAuxiliary(_=>math.log(.4),look,Some(k))
        val raw=r.particles.map { case(i,x)=>
          val a=.2*initial.weights(i)+.8*initial.weights(i)*(33-i)/z
          initial.weights(i)/a*(if(x==0).3/.8 else .7/.2)*.4/32
        }
        r.logEvidence shouldBe (initial.logEvidence+math.log(raw.sum) +- 2e-12)
        r.weights.zip(raw).foreach((w,x)=>w shouldBe (x/raw.sum +- 2e-12))
        f.lastProposalReport.get.auxiliary.get.evaluations shouldBe 32
        f.close()
        val ordinary=make(method=method)
        try{val prior=ordinary.start(_=>0.0,kernel);val s=ordinary.advance(_=>0.0,kernel)
          check(s,s.particles.map(x=>(if(x==0).3/.8 else .7/.2)/32),prior.logEvidence)
        }finally ordinary.close()
      }
    }
    "skip look-ahead on carry and leave guided results unchanged" in {
      val a=make(F.Policy.WhenEssBelow(.01));val b=make(F.Policy.WhenEssBelow(.01))
      try{
        a.start(_=>0.0,kernel) shouldBe b.start(_=>0.0,kernel)
        a.advanceAuxiliary(_=>0.0,A.LookAhead[Int](_=>throw new AssertionError("Must not call")),Some(kernel)) shouldBe b.advance(_=>0.0,kernel)
        a.lastProposalReport.get.auxiliaryRequested shouldBe true
        a.lastProposalReport.get.auxiliary shouldBe None
      }finally{a.close();b.close()}
    }
    "preserve bootstrap replay with no guidance or neutral auxiliary sampling" in {
      for(method<-F.Method.values){val a=make(method=method);val b=make(method=method)
        try{a.start(_=>0.0) shouldBe b.start(_=>0.0)
          a.advance(_=>0.0) shouldBe b.advanceAuxiliary(_=>0.0,A.LookAhead[Int](_=>throw new AssertionError("unused"),1.0))
          b.lastProposalReport.get.targetDensityCalls shouldBe 0
        }finally{a.close();b.close()}}
    }
    "allow an auxiliary bootstrap step without child densities" in {
      val f=make();try{f.start(_=>0.0)
        f.advanceAuxiliary(_=>0.0,A.LookAhead[Int](x=>if(x==0)0 else -4)).weights.sum shouldBe (1.0 +- 1e-12)
        f.lastProposalReport.get.proposalDensityCalls shouldBe 0
      }finally f.close()
    }
    "retain tiny parent log weights and restore them on carry" in {
      var i= -1;val f=F(B.Model(()=>{i+=1;i},(x:Int)=>x),F.Config(2,F.Policy.WhenEssBelow(.01),F.Method.Stratified))
      try{f.start(x=>if(x==0)0 else -1000)
        val r=f.advanceAuxiliary(x=>if(x==0)-1000 else 0,A.LookAhead[Int](_=>throw new AssertionError("unused")))
        r.weights shouldBe Vector(.5,.5)
      }finally f.close()
    }
    "produce constant corrected scalar Gaussian weights" in {
      val p=P.gaussian(0,2);val proposed=P.gaussianObservation(0,2,.7,.4)
      val f=F(B.Model(p.sample,(x:Double)=>x),F.Config(64,F.Policy.Always,F.Method.Stratified))
      try{val r=f.start(x=>P.gaussian(x,.4).logDensity(.7),P.Kernel(p,(_:Double)=>p,proposed,(_:Double)=>proposed))
        r.logEvidence shouldBe (P.gaussianPredictiveLogLikelihood(0,2,.7,.4) +- 1e-12)
        all(r.weights) shouldBe (1.0/64 +- 1e-12)
      }finally f.close()
    }
    "produce constant corrected multivariate Gaussian weights" in {
      val prior=MultivariateGaussianDistribution(Vector(0.0,1.0),Vector(Vector(2.0,.3),Vector(.3,1.0)))
      val observed=Vector(.7);val h=Vector(Vector(1.0,.5));val noise=Vector(Vector(.4))
      val guidance=P.linearGaussianObservation(prior,observed,h,noise)
      val p=P.multivariateGaussian(prior);val q=guidance.proposal
      val f=F(B.Model(p.sample,(x:Vector[Double])=>x),F.Config(64,F.Policy.Always,F.Method.Stratified))
      try{val r=f.start(x=>P.gaussian(x(0)+.5*x(1),math.sqrt(.4)).logDensity(.7),
          P.Kernel(p,(_:Vector[Double])=>p,q,(_:Vector[Double])=>q))
        r.logEvidence shouldBe (guidance.logPredictive +- 1e-12)
        // Direct observation marginal: H m=.5, H C H' + R=2.95.
        r.logEvidence shouldBe (-.5*(math.log(2*math.Pi*2.95)+.2*.2/2.95) +- 1e-12)
        all(r.weights) shouldBe (1.0/64 +- 1e-12)
      }finally f.close()
    }
    "retain collapse diagnostics but invalidate failed density callbacks" in {
      val f=make();intercept[F.NoViableParticles](f.start(_=>Double.NegativeInfinity,kernel))
      f.lastProposalReport.get.targetDensityCalls shouldBe 32
      intercept[IllegalArgumentException](f.current);f.close()
      for(bad<-Vector(Double.NaN,Double.PositiveInfinity,Double.NegativeInfinity)){
        val g=make();val law=P.Law(()=>0,(_:Int)=>bad)
        intercept[IllegalArgumentException](g.start(_=>0.0,P.Kernel(target,(_:Int)=>target,law,(_:Int)=>law)))
        g.lastReport shouldBe None;g.lastProposalReport shouldBe None
        intercept[IllegalArgumentException](g.current);g.close()
      }
    }
    "fail terminally for unsupported auxiliary scores and allow defended fallback" in {
      val f=make();f.start(_=>0.0)
      intercept[IllegalArgumentException](f.advanceAuxiliary(_=>0.0,A.LookAhead[Int](_=>Double.NegativeInfinity,0)))
      f.lastProposalReport shouldBe None;intercept[IllegalArgumentException](f.current);f.close()
      val g=make();try{g.start(_=>0.0);g.advanceAuxiliary(_=>0.0,A.LookAhead[Int](_=>Double.NegativeInfinity,.1))
        g.lastProposalReport.get.auxiliary.get.usedPriorFallback shouldBe true
      }finally g.close()
    }
    "reject density overflow, reentrancy and interruption without a partial posterior" in {
      val f=make();val p=P.Law(()=>0,(_:Int)=>Double.MaxValue);val q=P.Law(()=>0,(_:Int)=> -Double.MaxValue)
      intercept[ArithmeticException](f.start(_=>0.0,P.Kernel(p,(_:Int)=>p,q,(_:Int)=>q)));f.close()
      val g=make();g.start(_=>0.0)
      intercept[IllegalArgumentException](g.advanceAuxiliary(_=>0.0,A.LookAhead[Int](_=>{g.current;0.0})))
      g.lastReport shouldBe None;g.close()
      val j=make();Thread.currentThread().interrupt()
      try{intercept[InterruptedException](j.start(_=>0.0,kernel))}finally Thread.interrupted()
      j.lastProposalReport shouldBe None;intercept[IllegalArgumentException](j.current);j.close()
    }
    "enforce owner access and lifecycle on the new methods" in {
      val f=make()
      intercept[IllegalArgumentException](f.advance(_=>0.0,kernel))
      intercept[IllegalArgumentException](f.advanceAuxiliary(_=>0.0,A.LookAhead[Int](_=>0)))
      f.start(_=>0.0,kernel)
      intercept[IllegalArgumentException](f.start(_=>0.0,kernel))
      val error=new java.util.concurrent.atomic.AtomicReference[Throwable]()
      val thread=new Thread(()=>{try{f.lastProposalReport;()}catch{case e:Throwable=>error.set(e)}})
      thread.start();thread.join();error.get().isInstanceOf[IllegalArgumentException] shouldBe true
      f.close();f.close()
      intercept[IllegalArgumentException](f.advance(_=>0.0,kernel))
    }
  }
}
