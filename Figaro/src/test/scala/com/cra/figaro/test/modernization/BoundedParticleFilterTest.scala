package com.cra.figaro.test.modernization

import com.cra.figaro.algorithm.filtering.{BoundedParticleFilter as B, ParticleFilter, ParticleFilterHealth as H, ParParticleFilter}
import com.cra.figaro.language.*
import com.cra.figaro.library.atomic.discrete.*
import com.cra.figaro.util.{random, withRandomSeed}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class BoundedParticleFilterTest extends AnyWordSpec with Matchers {
  private def scripted(bits: Vector[Boolean], c: B.Config): B.Step[Int] = {
    var i = 0
    B.step(c, true)(() => { val j=i; i+=1; (j,j) }, j => bits(j))
  }
  private def sequences(n: Int, k: Int): Vector[Vector[Int]] =
    if (n == 0) Vector(Vector.empty) else sequences(n-1,k).flatMap(s => (0 until k).map(s :+ _))

  "Bounded indicator filtering" should {
    "preserve exact all-success updates over a long sequence" in {
      val f=B(B.Model(()=>0,(x:Int)=>x+1),B.Config(8,32,6,seed=72))
      try {
        for (t <- 0 until 2048) {
          val r=if(t==0) f.start(_==t) else f.advance(_==t)
          r.particles shouldBe Vector.fill(8)(t)
          r.logEvidence shouldBe 0.0
          r.report.attempts shouldBe 8
        }
      } finally f.close()
    }
    "retain repeated exhausted populations and report persistent ancestry collapse" in {
      var draws=0
      def draw(t:Int):(Int,Boolean)={draws+=1;(t,draws%8==1)}
      val f=B(B.Model(()=>draw(0),(x:(Int,Boolean))=>draw(x._1+1)),B.Config(2,8,4))
      try {
        for(t <- 0 until 256) {
          val r=if(t==0) f.start(_._2) else f.advance(_._2)
          r.particles shouldBe Vector((t,true))
          r.report.attempts shouldBe 8
          r.report.reason shouldBe B.StopReason.BudgetExhausted
          r.report.targetMet shouldBe false
          r.report.warnings should not be empty
          if(t>0) r.report.distinctParents shouldBe Some(1)
          r.logEvidence shouldBe (-(t+1)*math.log(8.0) +- 1e-9)
        }
        // An abrupt impossible observation must terminate, not silently retry.
        intercept[B.NoViableParticles](f.advance(_._1 < 0))
        draws shouldBe 257*8
        intercept[IllegalArgumentException](f.current)
      } finally f.close()
    }
    "calibrate long sequences with abrupt rare observations against exact evidence" in {
      val length=64
      val runs=512
      val logExact=(0 until length).map(t=>math.log(if(t%8==7) .2 else .8)).sum
      val ratios=(1 to runs).map { seed =>
        val f=B(B.Model(()=>random.nextDouble()<.8,(_:Boolean)=>random.nextDouble()<.8),
          B.Config(64,128,48,seed=seed))
        try {
          for(t <- 0 until length) {
            val wanted=t%8!=7
            val r=if(t==0) f.start(_==wanted) else f.advance(_==wanted)
            r.report.attempts should be <= 128
            r.particles.forall(_==wanted) shouldBe true
          }
          math.exp(f.current.logEvidence-logExact)
        } catch {case _:B.NoViableParticles => 0.0} finally f.close()
      }
      val mean=ratios.sum/runs
      val se=math.sqrt(ratios.map(x=>(x-mean)*(x-mean)).sum/(runs-1)/runs)
      info(s"Long-sequence evidence: runs=$runs steps=$length normalizedMean=$mean standardError=$se zeros=${ratios.count(_==0.0)}")
      math.abs(mean-1.0) should be <= (6*se+1e-12)
    }
    "use the entire initial batch even when its target was reached earlier" in {
      val r = scripted(Vector(true,true,true,false), B.Config(4,8,2))
      r.values shouldBe Vector(0,1,2)
      r.report.reason shouldBe B.StopReason.InitialBatch
      math.exp(r.logIncrement) shouldBe (.75 +- 1e-14)
    }
    "discard the final success including at exactly the cap" in {
      val r = scripted(Vector(false,true,false,true), B.Config(2,4,2))
      r.values shouldBe Vector(1)
      r.report.observedSuccesses shouldBe 2
      r.report.retainedCandidates shouldBe 3
      r.report.distinctParents shouldBe Some(1)
      r.report.reason shouldBe B.StopReason.SuccessTarget
      math.exp(r.logIncrement) shouldBe (1.0/3 +- 1e-14)
    }
    "keep partial successes at the cap instead of declaring a false zero" in {
      val r = scripted(Vector(false,true,false,false), B.Config(2,4,2))
      r.values shouldBe Vector(1)
      r.report.reason shouldBe B.StopReason.BudgetExhausted
      math.exp(r.logIncrement) shouldBe (.25 +- 1e-14)
    }
    "match exact Bernoulli means over complete finite stopping trees, including zero paths" in {
      for (minimum <- 2 to 4; maximum <- minimum to 6; target <- 2 to maximum;
        p <- Vector(0.0,.01,.2,.5,.9,1.0)) {
        val c = B.Config(minimum,maximum,target)
        val expected = sequences(maximum,2).map { path =>
          val probability = path.map(x => if(x==1) p else 1-p).product
          val r=scripted(path.map(_==1),c)
          r.report.attempts should be <= maximum
          probability * math.exp(r.logIncrement)
        }.sum
        expected shouldBe (p +- 3e-14)
      }
    }
    "match exact two-step evidence and unnormalized posterior moments over all candidate trees" in {
      val prior = Vector(.5,.3,.2)
      val transition = Vector(Vector(.6,.1,.3),Vector(.2,.6,.2),Vector(.5,.1,.4))
      val c=B.Config(2,3,2)
      def outcomes(prob: Vector[Double]) = sequences(3,3).map { path =>
        var i=0
        val step=B.step(c,false)(() => { val x=path(i); i+=1; (x,-1) }, _ != 0)
        (path.map(prob).product, step)
      }
      var evidence=0.0
      var numerator=0.0
      for ((p1,s1) <- outcomes(prior) if s1.values.nonEmpty) {
        val mixture=Vector.tabulate(3)(j => s1.values.map(i => transition(i)(j)).sum/s1.values.size)
        for ((p2,s2) <- outcomes(mixture)) {
          val weighted=p1*p2*math.exp(s1.logIncrement+s2.logIncrement)
          evidence+=weighted
          if(s2.values.nonEmpty) numerator+=weighted*s2.values.count(_==2)/s2.values.size
        }
      }
      evidence shouldBe (.34 +- 3e-14)
      numerator shouldBe (.14 +- 3e-14)
    }
    "report collapse and forbid later queries or automatic retries" in {
      var calls=0
      val f=B(B.Model(() => {calls+=1; 0}, (x:Int) => x),B.Config(2,5,2))
      val e=intercept[B.NoViableParticles](f.start(_==1))
      calls shouldBe 5
      e.logEvidence shouldBe Double.NegativeInfinity
      f.lastReport.get.retainedSuccesses shouldBe 0
      intercept[IllegalArgumentException](f.current)
      intercept[IllegalArgumentException](f.advance(_ => true))
      intercept[IllegalArgumentException](f.start(_ => true))
      calls shouldBe 5
      f.close(); f.close()
    }
    "retain a below-target viable population and freeze configuration" in {
      var i=0
      val f=B(B.Model(() => {i+=1;i}, (x:Int) => x),B.Config(2,5,3))
      val r=f.start(_==1)
      r.particles shouldBe Vector(1)
      r.report.reason shouldBe B.StopReason.BudgetExhausted
      r.report.targetMet shouldBe false
      r.report.warnings should not be empty
      r.report.health.issues.map(_.code) should contain (H.Code.DominantParticle)
      f.advance(_==1).expectation(_.toDouble) shouldBe 1.0
      f.close()
    }
    "never propagate the adaptively discarded success as an ancestor" in {
      var i=0
      val f=B(B.Model(()=>{i+=1;i},(x:Int)=>x),B.Config(2,4,2))
      val first=f.start(x=>x==2 || x==4)
      first.particles shouldBe Vector(2)
      f.advance(_=>true).particles shouldBe Vector(2,2)
      f.lastReport.get.warnings.mkString should include ("one previous particle")
      f.close()
    }
    "approximate a partially observed finite-state posterior at a declared large budget" in {
      val prior=Vector(.5,.3,.2)
      val transitions=Vector(Vector(.6,.1,.3),Vector(.2,.6,.2),Vector(.5,.1,.4))
      def draw(p:Vector[Double]):Int={val u=random.nextDouble();if(u<p(0)) 0 else if(u<p(0)+p(1)) 1 else 2}
      for(seed<- 1L to 4L) {
        val f=B(B.Model(()=>draw(prior),(x:Int)=>draw(transitions(x))),B.Config(20000,30000,2001,seed))
        try {
          f.start(_!=0)
          val r=f.advance(_!=0)
          r.expectation(x=>if(x==2) 1.0 else 0.0) shouldBe ((.14/.34) +- .03)
        } finally f.close()
      }
    }
    "replay seeded runs and restore the caller RNG scope" in {
      def run() = {
        val f=B(B.Model(() => random.nextInt(4), (x:Int) => (x+random.nextInt(4))%4),B.Config(10,100,8,seed=94))
        try Vector(f.start(_>0),f.advance(_<3)) finally f.close()
      }
      run() shouldBe run()
      val baseline=withRandomSeed(44){Vector(random.nextDouble(),random.nextDouble())}
      val actual=withRandomSeed(44){val a=random.nextDouble();run();Vector(a,random.nextDouble())}
      actual shouldBe baseline
    }
    "invalidate on callback exceptions and reentrant updates" in {
      val f=B(B.Model(() => 1, (_:Int) => throw new IllegalStateException("callback")),B.Config(2,3,2))
      f.start(_=>true)
      intercept[IllegalStateException](f.advance(_=>true))
      f.lastReport shouldBe None
      intercept[IllegalArgumentException](f.current)
      val g=B(B.Model(() => 1, (x:Int)=>x),B.Config(2,3,2))
      intercept[IllegalArgumentException](g.start { _ => g.current;true })
      intercept[IllegalArgumentException](g.current)
    }
    "refuse foreign threads and preserve an interrupted status" in {
      val f=B(B.Model(() => 1, (x:Int)=>x),B.Config(2,3,2))
      val error=new java.util.concurrent.atomic.AtomicReference[Throwable]()
      val worker=new Thread(() => {try {f.start(_=>true);()} catch {case e:Throwable=>error.set(e)}})
      worker.start();worker.join()
      error.get() shouldBe a[IllegalArgumentException]
      Thread.currentThread().interrupt()
      try {
        intercept[InterruptedException](f.start(_=>true))
        Thread.currentThread().isInterrupted shouldBe true
      } finally Thread.interrupted()
      intercept[IllegalArgumentException](f.current)
    }
    "sample fresh fixed Figaro graphs and clear every owned universe" in {
      val universes=scala.collection.mutable.ArrayBuffer.empty[Universe]
      val model=B.elementModel[Boolean](u => {universes+=u;Flip(.5)(using "x",u)},
        (x,u) => {universes+=u;Flip(if(x) .8 else .2)(using "x",u)})
      val f=B(model,B.Config(10,100,8,seed=5))
      try {
        f.start(identity).particles.forall(identity) shouldBe true
        f.advance(identity).particles.forall(identity) shouldBe true
        universes.forall(_.activeElements.isEmpty) shouldBe true
      } finally f.close()
    }
    "reject hidden likelihood evidence and external graph parents without clearing external data" in {
      var owned:Universe=null
      val m=B.elementModel[Boolean](u=>{owned=u;val x=Flip(.5)(using "x",u);x.observe(true);x},
        (x,u)=>Constant(x)(using "x",u))
      intercept[IllegalArgumentException](B(m,B.Config(2,3,2)).start(identity))
      owned.activeElements shouldBe empty
      val other=new Universe
      try {
        val parent=Flip(.5)(using "p",other)
        val m2=B.elementModel[Boolean](u=>Apply(parent,(x:Boolean)=>x)(using "x",u),
          (x,u)=>Constant(x)(using "x",u))
        intercept[IllegalArgumentException](B(m2,B.Config(2,3,2)).start(identity))
        other.activeElements should contain (parent)
      } finally other.clear()
    }
    "reject constraints, interventions and dynamic graph construction and clean owned graphs" in {
      var owned:Universe=null
      val builders:Vector[Universe=>Element[Boolean]]=Vector(
        u=>{val x=Flip(.5)(using "x",u);x.addConstraint(_=>.5);x},
        u=>{val x=Flip(.5)(using "x",u);x.intervene(true);x},
        u=>Chain(Flip(.5)(using "p",u),(b:Boolean)=>Constant(b)(using "child",u))(using "x",u),
        u=>Apply(Flip(.5)(using "p",u),(b:Boolean)=>{Constant(1)(using "extra",u);b})(using "x",u))
      builders.foreach { build =>
        val m=B.elementModel[Boolean](u=>{owned=u;build(u)},(x,u)=>Constant(x)(using "x",u))
        intercept[IllegalArgumentException](B(m,B.Config(2,3,2)).start(identity))
        owned.activeElements shouldBe empty
      }
    }
    "reject invalid budgets and health thresholds before sampling" in {
      intercept[IllegalArgumentException](B.Config(1,5,2))
      intercept[IllegalArgumentException](B.Config(5,4,2))
      intercept[IllegalArgumentException](B.Config(2,5,1))
      intercept[IllegalArgumentException](H.Config(minEss=Double.NaN))
      intercept[IllegalArgumentException](H.assess(Vector(Double.PositiveInfinity)))
    }
  }

  "Fixed-budget filter health" should {
    "distinguish real zeros from tiny finite weights and avoid guessing indicator eligibility" in {
      val r=H.assess(Vector(-1000.0,-1000.0,Double.NegativeInfinity))
      r.positiveWeights shouldBe 2
      r.ess shouldBe Some(2.0)
      r.eligibility shouldBe H.Eligibility.Unknown
      r.recommendations.mkString should include ("Eligibility is unverified")
    }
    "expose actionable reports after sequential and parallel zero-weight failures" in {
      def model():Universe={val u=new Universe;Constant(0)(using "x",u).addCondition(_==1);u}
      val initial=model()
      val fixed=ParticleFilter(initial,(u:Universe)=>u,10)
      try {
        val e=intercept[ParticleFilter.NoViableParticles](fixed.start())
        e.diagnostics.get.attempts shouldBe 10
        fixed.lastHealth.get.positiveWeights shouldBe 0
        e.getMessage should include ("BoundedParticleFilter")
        intercept[IllegalStateException](fixed.getlogProbEvidence())
      } finally {fixed.kill();initial.clear()}
      val parallel=ParParticleFilter(()=>model(),(u:Universe)=>u,10,2)
      try {
        intercept[ParticleFilter.NoViableParticles](parallel.start())
        parallel.lastHealth.get.positiveWeights shouldBe 0
      } finally parallel.kill()
    }
  }
}
