package com.cra.figaro.test.modernization

import com.cra.figaro.algorithm.filtering.{WeightedParticleProposal as P,FixedBudgetParticleFilter as F,BoundedParticleFilter as B}
import com.cra.figaro.library.atomic.continuous.{MultivariateGaussianDistribution as G}
import com.cra.figaro.util.{withRandomSeed,random}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class NonlinearProposalTest extends AnyWordSpec with Matchers {
  import NonlinearProposalPrototype.*
  val prior=G(Vector(0.0),Vector(Vector(1.0)))
  def prep(method:Method=Method.Cubature)=prepare(prior,Vector(1.0),identity,Vector(Vector(.25)),method,.1,Budget(8,1),Some(_=>Vector(Vector(1.0))))
  "Frozen nonlinear proposal prototype" should {
    "reduce both methods to correlated affine conditioning for dimensions 1 through 4" in {
      for(d<-1 to 4;k<-1 to 4;method<-Method.values){
        val p=G(Vector.tabulate(d)(i=>i*.3),Vector.tabulate(d,d)((i,j)=>if(i==j)1+i*.2 else .1))
        val h=Vector.tabulate(k,d)((i,j)=>(i+1.0)/(j+2.0));val r=Vector.tabulate(k,k)((i,j)=>if(i==j).5 else .05)
        val b=Vector.tabulate(k)(i=>.2*i);val y=Vector.fill(k)(.7)
        val f=(x:V)=>h.map(row=>row.zip(x).map(_*_).sum).zip(b).map(_+_)
        val q=prepare(p,y,f,r,method,.1,Budget(8,1),Some(_=>h))
        val exact=P.linearGaussianObservation(p,y,h,r,b)
        q.gaussian.mean.zip(exact.posterior.mean).foreach((a,b)=>a shouldBe(b +- 1e-11))
        q.gaussian.covariance.flatten.zip(exact.posterior.covariance.flatten).foreach((a,b)=>a shouldBe(b +- 1e-11))
        q.approximateLogPredictive shouldBe(exact.logPredictive +- 1e-11)
      }
    }
    "retain scaled covariance geometry" in {
      val p=G(Vector(0.0,0.0),Vector(Vector(1e-12,.2),Vector(.2,1e12)))
      val h=Vector(Vector(1e6,1e-6));val f=(x:V)=>Vector(h.head.zip(x).map(_*_).sum)
      val q=prepare(p,Vector(1.0),f,Vector(Vector(1.0)),Method.Cubature,.1,Budget(4,0))
      val e=P.linearGaussianObservation(p,Vector(1.0),h,Vector(Vector(1.0)))
      q.gaussian.mean.zip(e.posterior.mean).foreach((a,b)=>(a-b)/math.max(1,math.abs(b)) shouldBe(0.0 +- 1e-10))
    }
    "expose the symmetric quadratic zero-gain counterexample" in {
      for(m<-Method.values){val q=prepare(prior,Vector(4.0),x=>Vector(x.head*x.head),Vector(Vector(.1)),m,.1,Budget(2,1),Some(x=>Vector(Vector(2*x.head))))
        q.gaussian.mean shouldBe prior.mean;q.gaussian.covariance.head.head shouldBe(1.0 +- 1e-12)}
    }
    "score the full defensive mixture" in {
      val q=prep();val x=Vector(2.0)
      q.proposal.logDensity(x) shouldBe(math.log(.1*prior.density(x)+.9*q.gaussian.density(x)) +- 1e-12)
    }
    "sample consistently with the normalized mixture" in {
      val q=prep();val xs=withRandomSeed(81221){Vector.fill(20000)(q.proposal.sample().head)}
      xs.sum/xs.size shouldBe(.9*q.gaussian.mean.head +- .025)
      xs.map(x=>x*x).sum/xs.size shouldBe(.1+.9*(q.gaussian.covariance.head.head+math.pow(q.gaussian.mean.head,2)) +- .04)
    }
    "prepare deterministically without consuming random numbers" in {
      val a=withRandomSeed(81){prep();random.nextDouble()};val b=withRandomSeed(81){random.nextDouble()}
      a shouldBe b;prep().gaussian shouldBe prep().gaussian
    }
    "count callbacks and refuse before exceeding the preparation budget" in {
      var calls=0
      intercept[IllegalArgumentException]{prepare(prior,Vector(0.0),x=>{calls+=1;x},Vector(Vector(1.0)),Method.Cubature,.1,Budget(1,0))}
      calls shouldBe 0
      val q=prepare(prior,Vector(0.0),x=>{calls+=1;x},Vector(Vector(1.0)),Method.Cubature,.1,Budget(2,0))
      calls shouldBe 2;q.work.hCalls shouldBe 2;q.work.gaussianConstructions shouldBe 4
    }
    "reject invalid callback dimensions and nonfinite values" in {
      for(f<-Vector[V=>V](_=>Vector.empty,_=>Vector(Double.NaN),_=>null))
        intercept[IllegalArgumentException]{prepare(prior,Vector(0.0),f,Vector(Vector(1.0)),Method.Cubature,.1,Budget(2,0))}
    }
    "reject invalid Jacobians and missing explicit derivatives" in {
      for(j<-Vector[Option[V=>M]](None,Some(_=>Vector(Vector(Double.PositiveInfinity))),Some(_=>Vector.empty)))
        intercept[IllegalArgumentException]{prepare(prior,Vector(0.0),identity,Vector(Vector(1.0)),Method.Jacobian,.1,Budget(1,1),j)}
    }
    "refuse singular noise, unsupported dimensions and implicit defense" in {
      intercept[IllegalArgumentException]{prepare(prior,Vector(0.0),identity,Vector(Vector(0.0)),Method.Cubature,.1,Budget(2,0))}
      intercept[IllegalArgumentException]{prepare(prior,Vector.fill(5)(0.0),identity,Vector(Vector(1.0)),Method.Cubature,.1,Budget(2,0))}
      intercept[IllegalArgumentException]{prepare(prior,Vector(0.0),identity,Vector(Vector(1.0)),Method.Cubature,0.0,Budget(2,0))}
    }
    "propagate callback failures and overflow without fallback" in {
      intercept[IllegalStateException]{prepare(prior,Vector(0.0),_=>throw new IllegalStateException("fixture"),Vector(Vector(1.0)),Method.Cubature,.1,Budget(2,0))}
      intercept[IllegalArgumentException]{prepare(prior,Vector(0.0),x=>Vector(x.head*Double.MaxValue),Vector(Vector(1.0)),Method.Cubature,.1,Budget(2,0))}
    }
    "honor interruption" in {
      Thread.currentThread().interrupt()
      try intercept[InterruptedException]{prep()} finally Thread.interrupted()
    }
    "preserve filter state on pre-step refusal and invalidate on in-step failure" in {
      val law=P.multivariateGaussian(prior)
      val f=F(B.Model(law.sample,(_:V)=>law.sample()),F.Config(16,F.Policy.Always,F.Method.Stratified,71))
      try{val initial=f.start(_=>0.0)
        intercept[IllegalArgumentException]{prepare(prior,Vector(0.0),identity,Vector(Vector(1.0)),Method.Cubature,.1,Budget(0,0))}
        f.current shouldBe initial
        val k=P.Kernel(law,(_:V)=>law,law,(_:V)=>throw new IllegalStateException("fixture"))
        intercept[IllegalStateException]{f.advance(_=>0.0,k)}
        intercept[IllegalArgumentException]{f.current}
      }finally f.close()
    }
  }
}
