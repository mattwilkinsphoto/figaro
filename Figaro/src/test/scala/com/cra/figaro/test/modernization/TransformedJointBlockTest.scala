package com.cra.figaro.algorithm.sampling

import com.cra.figaro.language.*
import com.cra.figaro.library.atomic.continuous.*
import com.cra.figaro.util.{random,withRandomSeed}
import com.cra.figaro.algorithm.sampling.parallel.MultiChainMetropolisHastings as MH
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class TransformedJointBlockTest extends AnyWordSpec with Matchers {
  val eye=Vector(Vector(1.0,0.0),Vector(0.0,1.0))
  def isolated(f: Universe=>Unit): Unit = { val u=new Universe; try f(u) finally u.clear() }
  def block(xs: Element[Double]*): ProposalScheme=TransformedJointBlock(xs,eye)
  class Probe(u: Universe,s: ProposalScheme,xs: Element[?]*) extends OneTimeMetropolisHastings(u,1,s,0,1,xs*) {
    var decision: Option[Boolean]=None
    def prime(): Unit = doInitialize()
    def step(): MetropolisHastings.State=mhStep()
    def proposal(): MetropolisHastings.State=proposeAndUpdate()
    def scores: Map[Element[?],Double]=currentConstraintValues.toMap
    def counts: (Int,Int)=(accepts,rejects)
    override protected def decideToAccept(s: MetropolisHastings.State): Boolean=decision.getOrElse(super.decideToAccept(s))
  }
  def snapshot(u: Universe)=u.activeElements.map(e=>(e,e.value,e.randomness)).toSet
  "TJB-01 candidate transformed joint blocks" should {
    "match the existing Normal block on common states and innovations" in isolated { u =>
      val x=Normal(2,4)(using "",u); val y=Normal(-3,9)(using "",u)
      x.randomness=.3; x.value=x.generateValue(.3); y.randomness= -.7; y.value=y.generateValue(-.7)
      val cov=Vector(Vector(.7,.2),Vector(.2,1.2))
      val a=GaussianBlockProposal(Vector(x,y),cov).asInstanceOf[GaussianBlockScheme].block
      val b=TransformedJointBlock(Vector(x,y),cov).asInstanceOf[TransformedJointScheme].block
      val before=snapshot(u)
      val old=withRandomSeed(871001L)(a.prepare(u))
      val now=withRandomSeed(871001L)(b.prepare(u))
      now.randomness shouldBe old._1
      now.logProposal shouldBe 0.0
      now.logPrior shouldBe (old._2 +- 1e-13)
      snapshot(u) shouldBe before
    }
    "include Gamma scale and Beta Jacobians exactly once" in isolated { u =>
      val g=Gamma(2,3)(using "",u); val b=Beta(2,3)(using "",u)
      g.randomness=.7; g.value=2.1; b.randomness=.3; b.value=.3
      // Use generateValue so the current state is bitwise consistent, not decimal-rounded.
      g.value=g.generateValue(g.randomness)
      val p=block(g,b).asInstanceOf[TransformedJointScheme].block.prepare(u,Vector(.2,-.4))
      val gn=g.generateValue(p.randomness(0)); val bn=p.randomness(1)
      val q=math.log(gn/g.value)+math.log(bn*(1-bn)/(b.value*(1-b.value)))
      val prior=math.log(gn/g.value)-(gn-g.value)/3+math.log(bn/b.value)+2*math.log((1-bn)/(1-b.value))
      p.logProposal shouldBe (q +- 1e-13); p.logPrior shouldBe (prior +- 1e-13)
    }
    "update a shared descendant once after all roots and restore repeated rejection" in isolated { u =>
      val x=Normal(0,1)(using "",u); val y=Normal(0,1)(using "",u)
      var calls=0; val sum=Apply(x,y,(a: Double,b: Double)=>{calls+=1;a+b})(using "",u)
      val p=new Probe(u,block(x,y),sum); p.prime(); p.decision=Some(false)
      val before=snapshot(u)
      for(i<-1 to 12) { calls=0; p.step(); calls shouldBe 1; snapshot(u) shouldBe before }
      p.counts shouldBe (0,12)
      p.decision=Some(true); calls=0; p.step(); calls shouldBe 1
      sum.value shouldBe (x.value+y.value); p.counts shouldBe (1,12)
    }
    "restore early hard-condition rejection including every touched randomness" in isolated { u =>
      val g=Gamma(2,1)(using "",u); val b=Beta(2,3)(using "",u)
      val p=new Probe(u,block(g,b),g,b); p.prime()
      val original=g.value; g.addCondition((v: Double)=>v==original)
      val before=snapshot(u); p.step(); snapshot(u) shouldBe before; p.counts shouldBe (0,1)
    }
    "rollback callback failure and retain a usable sampler" in isolated { u =>
      val g=Gamma(2,1)(using "",u); val b=Beta(2,3)(using "",u)
      var fail=false
      val v=Apply(g,b,(x: Double,y: Double)=>{if(fail) throw new IllegalStateException("control");x*y})(using "",u)
      v.addLogConstraint((x: Double)=> -.5*(x-1)*(x-1))
      val p=new Probe(u,block(g,b),v); p.prime()
      val before=snapshot(u); val scores=p.scores; fail=true
      intercept[IllegalStateException](p.step())
      snapshot(u) shouldBe before; p.scores shouldBe scores; p.counts shouldBe (0,0)
      fail=false; p.decision=Some(true); p.step(); v.value shouldBe (g.value*b.value)
    }
    "rollback score failure after downstream mutation" in isolated { u =>
      val g=Gamma(2,1)(using "",u); val b=Beta(2,3)(using "",u)
      val v=Apply(g,b,(x: Double,y: Double)=>x*y)(using "",u); var fail=false
      v.addLogConstraint((x: Double)=>{if(fail) throw new ArithmeticException("score control"); -x*x})
      val p=new Probe(u,block(g,b),v); p.prime(); val before=snapshot(u); val scores=p.scores
      fail=true; intercept[ArithmeticException](p.step())
      snapshot(u) shouldBe before; p.scores shouldBe scores; p.counts shouldBe (0,0)
    }
    "rollback interruption after mutation without clearing the flag" in isolated { u =>
      val x=Normal(0,1)(using "",u); val y=Normal(0,1)(using "",u); var stop=false
      val v=Apply(x,y,(a: Double,b: Double)=>{if(stop) Thread.currentThread().interrupt();a+b})(using "",u)
      val p=new Probe(u,block(x,y),v); p.prime(); val before=snapshot(u)
      stop=true
      try { intercept[InterruptedException](p.step()); Thread.currentThread().isInterrupted shouldBe true
        snapshot(u) shouldBe before; p.counts shouldBe (0,0)
      } finally Thread.interrupted()
    }
    "refuse singular matrices, duplicate targets, rounded endpoints and overflow before mutation" in isolated { u =>
      val g=Gamma(2,1)(using "",u); val b=Beta(2,3)(using "",u)
      intercept[IllegalArgumentException](block(g,g))
      intercept[IllegalArgumentException](TransformedJointBlock(Vector(g,b),Vector(Vector(1.0,1.0),Vector(1.0,1.0))))
      g.randomness=1; g.value=1; b.randomness=.5; b.value=.5
      val k=block(g,b).asInstanceOf[TransformedJointScheme].block; val before=snapshot(u)
      intercept[IllegalArgumentException](k.prepare(u,Vector(1000,0)))
      intercept[IllegalArgumentException](k.prepare(u,Vector(0,1000)))
      snapshot(u) shouldBe before
    }
    "refuse foreign, observed, changed, extra-root and dynamic graphs" in isolated { u =>
      val g=Gamma(2,1)(using "",u); val b=Beta(2,3)(using "",u); val s=block(g,b)
      isolated(v=>intercept[IllegalArgumentException](s.asInstanceOf[TransformedJointScheme].block.bind(new Object,v)))
      b.observe(.5); intercept[IllegalArgumentException](block(g,b)); b.unobserve()
      val p=new Probe(u,s,g,b); p.prime(); p.step()
      Constant(1)(using "",u); intercept[IllegalArgumentException](p.step())
      val other=Normal(0,1)(using "",u)
      intercept[IllegalArgumentException](new Probe(u,block(g,b),g,b).step())
    }
    "refuse dynamic and intervened elements and changed Beta learning parameters" in isolated { u =>
      val g=Gamma(2,1)(using "",u); val b=Beta(2,3)(using "",u)
      val s=block(g,b); b.learnedAlpha=9
      intercept[IllegalArgumentException](s.asInstanceOf[TransformedJointScheme].block.validateRoots(u))
      b.learnedAlpha=2; g.intervene(1.0); intercept[IllegalArgumentException](block(g,b)); g.unintervene()
      val dynamic=Chain(b,(p: Double)=>Constant(p)(using "",u))(using "",u)
      val sampler=new Probe(u,block(g,b),dynamic); sampler.prime()
      intercept[IllegalArgumentException](sampler.step())
    }
    "refuse reuse by another sampler or thread and bounded-score optimization" in isolated { u =>
      val x=Normal(0,1)(using "",u); val y=Normal(0,1)(using "",u); val s=block(x,y)
      val p=new Probe(u,s,x,y); p.prime(); p.step()
      intercept[IllegalArgumentException](new Probe(u,s,x,y).step())
      val caught=new java.util.concurrent.atomic.AtomicReference[Throwable]()
      val thread=new Thread(()=>{try p.step() catch {case e: Throwable=>caught.set(e)};()})
      thread.start(); thread.join(); caught.get() shouldBe a[IllegalArgumentException]
      p.constraintsBound=true; intercept[IllegalArgumentException](p.step())
    }
    "refuse mixture/sequential composition rather than silently freezing other roots" in isolated { u =>
      val x=Normal(0,1)(using "",u); val y=Normal(0,1)(using "",u); val s=block(x,y)
      val p=new Probe(u,DisjointScheme(1.0 -> (()=>s)),x,y); p.prime()
      intercept[IllegalArgumentException](p.step())
      val seq=new Probe(u,UntypedScheme(()=>x,Some(s)),x,y); seq.prime(); val before=snapshot(u)
      intercept[IllegalArgumentException](seq.step()); snapshot(u) shouldBe before
    }
    "keep coordinate covariance detached from caller containers" in isolated { u =>
      val x=Normal(0,1)(using "",u); val y=Normal(0,1)(using "",u)
      x.randomness=0; x.value=0; y.randomness=0; y.value=0
      val rows=scala.collection.mutable.ArrayBuffer(scala.collection.mutable.ArrayBuffer(4.0,0.0),
        scala.collection.mutable.ArrayBuffer(0.0,9.0))
      val s=TransformedJointBlock(Vector(x,y),rows.map(_.toVector).toVector).asInstanceOf[TransformedJointScheme].block
      rows(0)(0)=100
      s.prepare(u,Vector(1.0,-1.0)).randomness shouldBe Vector(2.0,-3.0)
    }
    "refuse evidence changes and invalid scores without treating them as ordinary rejection" in isolated { u =>
      val x=Normal(0,1)(using "",u); val y=Normal(0,1)(using "",u); var bad=false
      x.addLogConstraint((v: Double)=>if(bad) Double.NaN else -v*v)
      val p=new Probe(u,block(x,y),x,y); p.prime(); p.step(); val before=snapshot(u); val counts=p.counts
      bad=true; intercept[IllegalArgumentException](p.step()); snapshot(u) shouldBe before; p.counts shouldBe counts
      bad=false; y.addLogConstraint((v: Double)=> -v*v)
      intercept[IllegalArgumentException](p.step()); snapshot(u) shouldBe before
    }
    "execute reproducibly through the owned single-worker fixed-budget runner" in {
      def build(u: Universe,index: Int): MH.Model = {
        val g=Gamma(2,3)(using "",u); val b=Beta(2,3)(using "",u)
        val v=Apply(g,b,(x: Double,y: Double)=>x*y)(using "",u)
        v.addLogConstraint((x: Double)=> -.5*math.pow((x-1)/.3,2))
        MH.Model(Vector(MH.Observable("g",g)(identity),MH.Observable("b",b)(identity)),Some(block(g,b)))
      }
      val config=MH.Config(chains=4,drawsPerChain=40,warmUp=20,parallelism=1,seed=873001L)
      val a=MH.run(config)(build); val b=MH.run(config)(build)
      a.chains.map(_.draws) shouldBe b.chains.map(_.draws)
      a.chains.flatMap(_.draws("g")).forall(x=>x>0 && x.isFinite) shouldBe true
      a.chains.flatMap(_.draws("b")).forall(x=>x>0 && x<1) shouldBe true
    }
    "cover the declared dimension bounds and nested deterministic dependencies" in {
      for(d<-Vector(2,4,8)) isolated { u =>
        val xs=Vector.fill(d)(Normal(0,1)(using "",u))
        val list=Inject(xs*)(using "",u)
        var calls=0
        val sum=Apply(list,(v: List[Double])=>{calls+=1;v.sum})(using "",u)
        val s=TransformedJointBlock(xs,Vector.tabulate(d,d)((i,j)=>if(i==j) 1.0 else .1))
        val p=new Probe(u,s,sum); p.prime(); p.decision=Some(true); calls=0; p.step()
        calls shouldBe 1; sum.value shouldBe xs.map(_.value).sum
      }
    }
    "reject subclasses and contingent evidence explicitly" in isolated { u =>
      val custom=new AtomicNormal("",0,1,u) {}
      val y=Normal(0,1)(using "",u)
      intercept[IllegalArgumentException](block(custom,y))
      u.deactivate(List(custom))
      val x=Normal(0,1)(using "",u)
      x.addLogConstraint((v: Double)=> -v*v,List(Element.ElemVal(y,0.0)))
      val p=new Probe(u,block(x,y),x,y); p.prime()
      intercept[IllegalArgumentException](p.step())
    }
    "dispose owned universes and workers on downstream failure" in {
      val universes=scala.collection.mutable.ArrayBuffer.empty[Universe]
      intercept[MH.ChainFailure] {
        MH.run(MH.Config(chains=2,drawsPerChain=10,warmUp=2,parallelism=1,seed=873002L)) { (u,index) =>
          universes+=u
          val g=Gamma(2,1)(using "",u); val b=Beta(2,3)(using "",u); var calls=0
          val v=Apply(g,b,(x: Double,y: Double)=>{calls+=1;if(calls>1) throw new IllegalStateException("worker control");x*y})(using "",u)
          MH.Model(Vector(MH.Observable("product",v)(identity)),Some(block(g,b)))
        }
      }
      universes.nonEmpty shouldBe true
      universes.forall(_.activeElements.isEmpty) shouldBe true
      import scala.jdk.CollectionConverters.*
      Thread.getAllStackTraces.keySet().asScala.exists(_.getName.startsWith("figaro-mcmc-worker-")) shouldBe false
    }
    "reject dimensions outside two through eight and invalid matrix entries" in isolated { u =>
      val xs=Vector.fill(9)(Normal(0,1)(using "",u))
      for(d<-Vector(1,9)) intercept[IllegalArgumentException] {
        TransformedJointBlock(xs.take(d),Vector.tabulate(d,d)((i,j)=>if(i==j) 1.0 else 0.0))
      }
      for(c<-Vector(Vector(Vector(1.0,.1),Vector(.2,1.0)),
        Vector(Vector(Double.NaN,0.0),Vector(0.0,1.0)),Vector(Vector(-1.0,0.0),Vector(0.0,1.0))))
        intercept[IllegalArgumentException](TransformedJointBlock(xs.take(2),c))
    }
    "respect root and covariance ordering with independently solved innovations" in isolated { u =>
      val x=Normal(0,1)(using "",u); val y=Normal(0,1)(using "",u)
      x.randomness=0; x.value=0; y.randomness=0; y.value=0
      val a=TransformedJointBlock(Vector(x,y),Vector(Vector(4.0,1.0),Vector(1.0,9.0))).asInstanceOf[TransformedJointScheme].block
      val b=TransformedJointBlock(Vector(y,x),Vector(Vector(9.0,1.0),Vector(1.0,4.0))).asInstanceOf[TransformedJointScheme].block
      val p=a.prepare(u,Vector(.15,(-.4-.5*.15)/math.sqrt(8.75)))
      val q=b.prepare(u,Vector(-.4/3,(.3+.4/9)/math.sqrt(4-1.0/9)))
      p.randomness(0) shouldBe (.3 +- 1e-14); p.randomness(1) shouldBe (-.4 +- 1e-14)
      q.randomness.reverse.zip(p.randomness).foreach { (a,b) => a shouldBe (b +- 1e-14) }
      p.logPrior shouldBe (q.logPrior +- 1e-14)
    }
    "refuse lost increments at extreme Normal locations without mutation" in isolated { u =>
      val x=Normal(1e16,1)(using "",u); val y=Normal(0,1)(using "",u)
      x.randomness=0; x.value=1e16; y.randomness=0; y.value=0
      val s=block(x,y).asInstanceOf[TransformedJointScheme].block; val before=snapshot(u)
      intercept[IllegalArgumentException](s.prepare(u,Vector(.5,0)))
      snapshot(u) shouldBe before
    }
    "treat genuine zero likelihood as rejection rather than a numeric exception" in isolated { u =>
      val x=Normal(0,1)(using "",u); val y=Normal(0,1)(using "",u); var zero=false
      x.addLogConstraint((v: Double)=>if(zero) Double.NegativeInfinity else 0.0)
      val p=new Probe(u,block(x,y),x,y); p.prime(); val before=snapshot(u)
      zero=true; p.step(); p.counts shouldBe (0,1); snapshot(u) shouldBe before
    }
    "refuse anytime ownership and precision checkpoints before sampling" in {
      isolated { u =>
        val x=Normal(0,1)(using "",u); val y=Normal(0,1)(using "",u); val s=block(x,y)
        val anytime=new AnytimeMetropolisHastings(u,s,0,1,x,y)
        intercept[IllegalArgumentException](s.asInstanceOf[TransformedJointScheme].block.bind(anytime,u))
      }
      val owned=scala.collection.mutable.ArrayBuffer.empty[Universe]
      val error=intercept[MH.ChainFailure] {
        MH.runUntilPrecise(MH.Config(chains=2,drawsPerChain=200,warmUp=2,parallelism=1,seed=7412202402L),
          com.cra.figaro.algorithm.sampling.parallel.McmcPrecision.Config(minDrawsPerChain=100)) { (u,i) =>
          owned+=u
          val x=Normal(0,1)(using "",u); val y=Normal(0,1)(using "",u)
          MH.Model(Vector(MH.Observable("x",x)(identity)),Some(block(x,y)))
        }
      }
      error.getCause.getMessage should include ("fixed-budget")
      owned.forall(_.activeElements.isEmpty) shouldBe true
    }
    "match independently derived whole-step acceptance and rejection decisions" in {
      var accepted=0; var rejected=0
      for(seed<-874001L to 874032L) isolated { u =>
        val g=Gamma(2,3)(using "",u); val b=Beta(2,3)(using "",u)
        val v=Apply(g,b,(x: Double,p: Double)=>x*p)(using "",u)
        v.addLogConstraint((x: Double)=> -.5*math.pow((x-1)/.3,2))
        val s=TransformedJointBlock(Vector(g,b),Vector(Vector(.16,.06),Vector(.06,.25)))
        val sampler=new Probe(u,s,g,b); sampler.prime()
        // Reinitialize all states and the accepted constraint cache consistently.
        g.randomness=1; g.value=3; b.randomness=.3; b.value=.3; v.value=.9
        class Primed extends Probe(u,s,g,b) { def cache(): Unit={initConstrainedValues();dissatisfied=Set.empty} }
        val p=new Primed; p.cache()
        val (logU,ng,nb)=withRandomSeed(seed) {
          val lu=math.log(random.nextDouble()); val n0=random.nextGaussian();val n1=random.nextGaussian()
          val a=.4*n0
          val c=.15*n0+math.sqrt(.2275)*n1
          (lu,3*math.exp(a),1/(1+math.exp(-(math.log(.3/.7)+c))))
        }
        def target(x: Double,p: Double): Double=math.log(x)-x/3+math.log(p)+2*math.log1p(-p)-.5*math.pow((x*p-1)/.3,2)
        val ratio=target(ng,nb)-target(3,.3)+math.log(ng*nb*(1-nb)/(3*.3*.7))
        val expect=logU<ratio
        withRandomSeed(seed)(p.step())
        p.counts shouldBe (if(expect) (1,0) else (0,1))
        if(expect) {accepted+=1;g.value shouldBe (ng +- 1e-12);b.value shouldBe (nb +- 1e-12)}
        else {rejected+=1;g.value shouldBe 3.0;b.value shouldBe .3;v.value shouldBe .9}
      }
      accepted should be > 0; rejected should be > 0
    }
  }
}

/** Actual graph proposal ratios for a separate SciPy audit; no posterior performance claim. */
object TransformedJointBlockControls {
  def main(args: Array[String]): Unit = {
    for(a<-Vector(-3.0,-.5,1.0); b<-Vector(-4.0,0.0,3.0); seed<-Vector(872001L,872002L)) {
      val u=new Universe
      try {
        val g=Gamma(2,3)(using "",u); val p=Beta(2,3)(using "",u)
        val v=Apply(g,p,(x: Double,y: Double)=>x*y)(using "",u)
        v.addLogConstraint((x: Double)=> -.5*math.pow((x-1)/.3,2))
        val scheme=TransformedJointBlock(Vector(g,p),Vector(Vector(.16,.06),Vector(.06,.25)))
        class GraphProbe extends OneTimeMetropolisHastings(u,1,scheme,0,1,g,p) {
          def initial(): Unit={doInitialize();g.randomness=math.exp(a)/3;g.value=g.generateValue(g.randomness)
            p.randomness=1/(1+math.exp(-b));p.value=p.randomness;v.value=g.value*p.value;initConstrainedValues()}
          def measure(): (Double,Double,Double,Double)={val s=proposeAndUpdate();(g.value,p.value,s.proposalProb,s.modelProb+computeScores())}
        }
        val probe=new GraphProbe
        probe.initial(); val oldG=g.value; val oldP=p.value
        val (ng,np,q,m)=withRandomSeed(seed)(probe.measure())
        println(Vector("GRAPH",oldG,oldP,ng,np,q,m).mkString("\t"))
      } finally u.clear()
    }
    for(d<-Vector(2,4,8,2)) {
      val covariance=Vector.tabulate(d,d)((i,j)=>if(i==j) 1.0 else if(d==2) .999999 else .1)
      val factor=GaussianBlockProposal.factorCovariance(covariance,d)
      val square=Vector.tabulate(d,d)((i,j)=>if(j<=i) factor(i)(j) else 0.0)
      println((Vector("FACTOR",d.toString)++covariance.flatten.map(_.toString)++square.flatten.map(_.toString)).mkString("\t"))
    }
    println("COMPLETE\t18")
  }
}
