package com.cra.figaro.test.modernization

import com.cra.figaro.algorithm.filtering.{WeightedParticleProposal as P,FixedBudgetParticleFilter as F,BoundedParticleFilter as B,LogParticleWeights as L}
import com.cra.figaro.algorithm.sampling.GaussianMixtureProposal as EM
import com.cra.figaro.library.atomic.continuous.MultivariateGaussianDistribution as G
import com.cra.figaro.util.{withRandomSeed,random}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class MulticomponentGuideTest extends AnyWordSpec with Matchers {
  import MulticomponentGuidePrototype.*
  val prior=G(Vector(0.0),Vector(Vector(1.0)))
  val p=P.multivariateGaussian(prior)
  def affine=anchors(.7,.25,Vector(-1.0,1.0),x=>2*x+.3,_=>2.0)
  def square=anchors(1.0,.25,Vector(-2.0,2.0),x=>.25*x*x,x=>.5*x)
  "Frozen multi-component prototype" should {
    "reduce overlapping affine branches to analytic conditioning" in {
      val q=branch(prior,affine,.1)
      val e=P.linearGaussianObservation(prior,Vector(.7),Vector(Vector(2.0)),Vector(Vector(.25)),Vector(.3))
      for(x<-Vector(-5.0,0.0,.2,2.0,20.0))q.law.logDensity(Vector(x)) shouldBe
        (math.log(.1*prior.density(Vector(x))+.9*e.posterior.density(Vector(x))) +- 1e-11)
      q.conditioning shouldBe 2
    }
    "match independently specified normalized mixture density and moments" in {
      val a=P.multivariateGaussian(G(Vector(-2.0),Vector(Vector(.25))))
      val b=P.multivariateGaussian(G(Vector(3.0),Vector(Vector(1.0))))
      val q=Mixture(Vector(math.log(.3),math.log(.7)),Vector(a,b))
      for(x<- -5 to 6)q.law.logDensity(Vector(x.toDouble)) shouldBe
        (math.log(.3*math.exp(a.logDensity(Vector(x)))+.7*math.exp(b.logDensity(Vector(x)))) +- 1e-12)
      val xs=withRandomSeed(1818){Vector.fill(30000)(q.law.sample().head)}
      xs.sum/xs.size shouldBe(1.5 +- .06)
      xs.map(x=>x*x).sum/xs.size shouldBe(8.275 +- .12)
    }
    "retain rare log components and select them with a scripted tail decision" in {
      val q=Mixture(Vector(0.0,-800.0),Vector(p,P.multivariateGaussian(G(Vector(50.0),Vector(Vector(1.0))))))
      q.normalized(1) shouldBe -800.0
      q.law.logDensity(Vector(50.0)) shouldBe(-800.0-.5*math.log(2*math.Pi) +- 1e-10)
      new L.Tree(q.normalized).draw(-801.0) shouldBe 1
      new L.Tree(q.normalized).draw(-1.0) shouldBe 0
      val scripted=new java.util.Random(0L){
        var words=0
        override def nextLong():Long={words+=1;if(words<=19)0L else Long.MinValue}
        override def nextDouble():Double=0.0
        override def nextGaussian():Double=0.0
      }
      com.cra.figaro.util.RandomContext.withRandom(scripted){q.law.sample()} shouldBe Vector(50.0)
    }
    "preserve density under anchor permutation without claiming sample identity" in {
      val b=anchors(1.0,.25,Vector(2.0,-2.0),x=>.25*x*x,x=>.5*x)
      for(x<- -4 to 4)branch(prior,b,.1).law.logDensity(Vector(x)) shouldBe
        (branch(prior,square,.1).law.logDensity(Vector(x)) +- 1e-12)
    }
    "handle zero slopes without division and refuse duplicate or excessive anchors" in {
      val b=anchors(0.0,.25,Vector(0.0),_=>0.0,_=>0.0)
      branch(prior,b,.1).law.logDensity(Vector(1.0)) shouldBe(p.logDensity(Vector(1.0)) +- 1e-12)
      for(a<-Vector(Vector(0.0,0.0),Vector.empty,Vector.fill(5)(1.0),Vector(Double.NaN)))
        intercept[IllegalArgumentException]{anchors(0.0,.25,a,identity,_=>1.0)}
    }
    "reject invalid covariance derivatives offsets and implicit defense" in {
      intercept[IllegalArgumentException]{anchors(0.0,0.0,Vector(0.0),identity,_=>1.0)}
      intercept[IllegalArgumentException]{anchors(0.0,1.0,Vector(0.0),identity,_=>Double.NaN)}
      intercept[IllegalArgumentException]{anchors(0.0,1.0,Vector(2.0),identity,_=>Double.MaxValue)}
      intercept[IllegalArgumentException]{branch(prior,affine,0.0)}
      intercept[IllegalArgumentException]{G(Vector(0.0),Vector(Vector(0.0)))}
    }
    "refuse preparation budgets before calling parent construction and reuse exact duplicates" in {
      var calls=0
      def make(x:Double)={calls+=1;G(Vector(x),Vector(Vector(1.0)))}
      intercept[IllegalArgumentException]{parentLookup(Vector(0.0,1.0),affine,Limits(1,100000),make,.1)}
      intercept[IllegalArgumentException]{parentLookup(Vector(0.0),affine,Limits(1,1),make,.1)}
      calls shouldBe 0
      val q=parentLookup(Vector(0.0,0.0,1.0),affine,Limits(2,100000),make,.1)
      q.size shouldBe 2;calls shouldBe 2
    }
    "propagate callback failures and interruption without fallback" in {
      intercept[IllegalStateException]{anchors(0.0,1.0,Vector(0.0),_=>throw new IllegalStateException("fixture"),_=>1.0)}
      Thread.currentThread().interrupt()
      try intercept[InterruptedException]{branch(prior,affine,.1)}finally Thread.interrupted()
    }
    "keep preparation and discarded pilots outside the production random stream" in {
      val expected=withRandomSeed(1919){random.nextLong()}
      val actual=withRandomSeed(1919){branch(prior,square,.1);pilot(2020,prior,Vector.empty,Vector.empty,0.0,1.0,_=>0.0,1);random.nextLong()}
      actual shouldBe expected
      val a=pilot(2020,prior,Vector.empty,Vector.empty,0.0,1.0,_=>0.0,1)
      val b=pilot(2020,prior,Vector.empty,Vector.empty,0.0,1.0,_=>0.0,1)
      a.result shouldBe b.result;a.draws shouldBe 512;a.ancestors shouldBe 0
      a.result.status shouldBe EM.Status.Fitted
    }
    "retain insufficient pilot refusal with no mixture or retry" in {
      val q=pilot(2021,prior,Vector.empty,Vector.empty,6.0,.001,identity,4)
      q.result.status shouldBe EM.Status.InsufficientPilot;q.mixture shouldBe None;q.draws shouldBe 512
      fitConstructions(q.result) shouldBe 0
    }
    "expose shared guidance loss of parent dependence and omitted-branch asymmetry" in {
      val left=G(Vector(-3.0),Vector(Vector(.01)));val right=G(Vector(3.0),Vector(Vector(.01)))
      val l=branch(left,square,.1);val r=branch(right,square,.1)
      l.law.logDensity(Vector(-3.0)) should be > r.law.logDensity(Vector(-3.0))
      val only=branch(prior,anchors(1.0,.25,Vector(2.0),x=>.25*x*x,x=>.5*x),.1)
      only.law.logDensity(Vector(2.0)) should be > only.law.logDensity(Vector(-2.0))
      val shared=Mixture(Vector(0.0),Vector(P.multivariateGaussian(G(Vector(0.0),Vector(Vector(9.0))))))
      defend(P.multivariateGaussian(left),shared,.1).logDensity(Vector(3.0)) should be > l.law.logDensity(Vector(3.0))
    }
    "keep pilots out of fixed-budget summaries on carry and resample paths for both selectors" in {
      for(method<-Vector(F.Method.Stratified,F.Method.LogTreeMultinomial);policy<-Vector(F.Policy.Always,F.Policy.WhenEssBelow(.01))){
        val f=F(B.Model(p.sample,(_:V)=>p.sample()),F.Config(32,policy,method,2022))
        try{
          val fitted=pilot(2023,prior,Vector.empty,Vector.empty,0.0,1.0,_=>0.0,1)
          val q=defend(p,fitted.mixture.get,.1);var draws=0
          val counted=P.Law[V](()=>{draws+=1;q.sample()},q.logDensity)
          val kernel=P.Kernel(p,(_:V)=>p,counted,(_:V)=>counted)
          val first=f.start(_=>0.0,kernel);val second=f.advance(_=>0.0,kernel)
          draws shouldBe 64;first.particles.size shouldBe 32;second.particles.size shouldBe 32
          first.report.stateDraws shouldBe 32;second.report.stateDraws shouldBe 32
          second.report.ancestorSelections shouldBe(if(policy==F.Policy.Always)32 else 0)
        }finally f.close()
      }
    }
    "preserve state on preparation refusal and become terminal on in-step failure" in {
      val f=F(B.Model(p.sample,(_:V)=>p.sample()),F.Config(16,F.Policy.Always,F.Method.Stratified,2024))
      try{val first=f.start(_=>0.0)
        intercept[IllegalArgumentException]{parentLookup(Vector(0.0),affine,Limits(1,1),_=>prior,.1)}
        f.current shouldBe first
        val bad=P.Law[V](()=>throw new IllegalStateException("fixture"),p.logDensity)
        intercept[IllegalStateException]{f.advance(_=>0.0,P.Kernel(p,(_:V)=>p,p,(_:V)=>bad))}
        intercept[IllegalArgumentException]{f.current}
      }finally f.close()
    }
  }
}
