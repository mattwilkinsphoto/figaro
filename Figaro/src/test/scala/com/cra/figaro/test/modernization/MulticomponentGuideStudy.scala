package com.cra.figaro.test.modernization

import com.cra.figaro.algorithm.filtering.{FixedBudgetParticleFilter as F,BoundedParticleFilter as B,WeightedParticleProposal as P}
import com.cra.figaro.library.atomic.continuous.MultivariateGaussianDistribution as G
import com.cra.figaro.util.SamplingRandom
import scala.io.Source
import scala.util.control.NonFatal

/** INF-18 frozen fixtures, all-outcome rows and explicit work accounting. Test sources only. */
object MulticomponentGuideStudy {
  import MulticomponentGuidePrototype.*
  val names=Vector("affine","weak","mono","tail","symmetric","asymmetric","three","merging","flat","omitted","narrow")
  val squares=Set("symmetric","asymmetric","merging","omitted","narrow")
  val extra=Vector("big-branch","big-pilot","eval-branch","eval-pilot")
  def modes(name:String)=Vector("bootstrap","branch","pilot","cubature","ekf")++
    (if(squares(name))Vector("manual")else Vector.empty)++(if(name=="affine")Vector("exact")else Vector.empty)
  final case class Spec(name:String){
    require(names.contains(name))
    val noise=if(name=="weak")4.0 else if(name=="asymmetric").2 else if(name=="three").35 else if(name=="narrow").3 else .5
    val ys=if(name=="tail")Vector(6.0)else if(name=="three")Vector(0.0,.2,-.2,0.0,.1,-.1)
      else if(name=="merging")Vector(.04,.01,0.0,-.02)else if(name=="flat")Vector(0.0,.1,-.1,0.0)
      else if(squares(name))Vector(1.0,1.2,.8,1.4,1.0,.7)else Vector(.4,1.2,-.3,.7,1.5,.2)
    def gaussian(parent:Option[V]):G=G(Vector(parent.fold(if(name=="asymmetric").8 else 0.0)(_.head*(if(name=="narrow").95 else .7))),
      Vector(Vector(if(parent.isEmpty){if(name=="narrow")4.0 else 1.21}else if(name=="narrow").0225 else .64)))
    def h(x:Double):Double=if(name=="affine")x else if(name=="flat")0.0 else if(name=="three")x*x*x-3*x
      else if(squares(name)).25*x*x else x+.25*x*x*x
    def jac(x:Double):Double=if(name=="affine")1.0 else if(name=="flat")0.0 else if(name=="three")3*x*x-3
      else if(squares(name)).5*x else 1+.75*x*x
    def points(t:Int):V=if(name=="affine")Vector(-1.0,1.0)else if(name=="flat")Vector(0.0)
      else if(name=="tail")Vector(1.0,2.0,3.0)else if(name=="three")Vector(-math.sqrt(3),0.0,math.sqrt(3))
      else if(squares(name)){if(ys(t)<=0)Vector(0.0)else if(name=="omitted")Vector(2*math.sqrt(ys(t)))else Vector(-2*math.sqrt(ys(t)),2*math.sqrt(ys(t)))}
      else Vector(-1.0,0.0,1.0)
  }
  final class Counts {
    var draws=0L;var likelihoods=0L;var targets=0L;var proposals=0L;var components=0L
    var guides=0L;var h=0L;var jac=0L;var gaussians=0L;var conditioning=0L
    var pilotDraws=0L;var pilotLikelihoods=0L;var pilotAncestors=0L;var fitDensities=0L;var fitSweeps=0L
    var ancestors=0L;var stored=0L;var nanos=0L;var fitStatus="none";var unknownGaussian=0L
  }
  val columns="name mode n seed t status logEvidence mean second positive tail r0 r1 r2 ess draws likelihoods targets proposals components guides h jac gaussians conditioning pilotDraws pilotLikelihoods pilotAncestors fitDensities fitSweeps ancestors stored unknownGaussian nanos fitStatus error".split(" ").toVector
  def run(name:String,mode:String,n:Int,seed:Int,emit:Boolean):Unit={
    val s=Spec(name);val c=new Counts;var terminal="";var t=0
    def gaussian(parent:Option[V]):G={c.gaussians+=1;s.gaussian(parent)}
    def target(parent:Option[V]):P.Law[V]={val p=P.multivariateGaussian(gaussian(parent))
      P.Law(p.sample,x=>{c.targets+=1;c.components+=1;p.logDensity(x)})}
    def bootstrap(parent:Option[V]):V={c.draws+=1;target(parent).sample()}
    val f=F(B.Model(()=>bootstrap(None),(x:V)=>bootstrap(Some(x))),F.Config(n,F.Policy.WhenEssBelow(.5),F.Method.Stratified,seed))
    def counted(law:P.Law[V],parts:Int):P.Law[V]=P.Law(()=>{c.draws+=1;law.sample()},x=>{c.proposals+=1;c.components+=parts;law.logDensity(x)})
    def oldGuide(parent:Option[V]):P.Law[V]={
      c.guides+=1;val prior=gaussian(parent);val y=s.ys(t)
      val (law,parts)=if(mode=="manual"){
        val r=ContinuousFilterBenchmark.guidance(ContinuousFilterBenchmark.Spec("persistent-modes"),
          Vector(ContinuousFilterBenchmark.Component(prior.mean.head,math.sqrt(prior.covariance.head.head),1)),y)
        c.gaussians+=(if(y>0)5 else 2);c.components+=(if(y>0)2 else 1)
        (P.Law[V](()=>Vector(r._1.sample()),x=>r._1.logDensity(x.head)),if(y>0)3 else 1)
      }else if(mode=="exact"){
        val r=P.linearGaussianObservation(prior,Vector(y),Vector(Vector(1.0)),Vector(Vector(s.noise*s.noise)))
        c.gaussians+=3;c.conditioning+=1;(P.defensive(P.multivariateGaussian(prior),r.proposal,.1),2)
      }else{
        val method=if(mode=="ekf")NonlinearProposalPrototype.Method.Jacobian else NonlinearProposalPrototype.Method.Cubature
        val r=NonlinearProposalPrototype.prepare(prior,Vector(y),x=>Vector(s.h(x.head)),Vector(Vector(s.noise*s.noise)),
          method,.1,NonlinearProposalPrototype.Budget(8,1),Some(x=>Vector(Vector(s.jac(x.head)))))
        c.h+=r.work.hCalls;c.jac+=r.work.jacobianCalls;c.gaussians+=r.work.gaussianConstructions;c.conditioning+=1
        (r.proposal,2)
      }
      counted(law,parts)
    }
    try s.ys.indices.foreach{index=>
      t=index;val start=System.nanoTime();var status="ok";var answer:Option[F.Update[V]]=None
      if(terminal.nonEmpty)status="unavailable"
      else try{
        val likelihood=(x:V)=>{c.likelihoods+=1;c.h+=1;val z=(s.ys(t)-s.h(x.head))/s.noise
          -.5*z*z-math.log(s.noise)-.5*math.log(2*math.Pi)}
        if(mode=="bootstrap" || extra.contains(mode))answer=Some(if(t==0)f.start(likelihood)else f.advance(likelihood))
        else{
          val unused=P.Law[V](()=>throw new AssertionError("unused initial"),_=>throw new AssertionError("unused initial"))
          val p0=if(t==0)target(None)else unused
          val guidePair:(P.Law[V],V=>P.Law[V])=if(mode=="branch"){
            val b=anchors(s.ys(t),s.noise*s.noise,s.points(t),x=>{c.h+=1;s.h(x)},x=>{c.jac+=1;s.jac(x)})
            if(t==0){val r=branch(gaussian(None),b,.1);c.guides+=1;c.conditioning+=r.conditioning;c.gaussians+=3*r.conditioning
              c.components+=r.conditioning;c.stored=math.max(c.stored,r.stored);(counted(r.law,r.components),(_:V)=>unused)}
            else{
              val cache=parentLookup(f.current.particles.map(_.head),b,Limits(n,100000),x=>gaussian(Some(Vector(x))),.1)
              c.guides+=cache.size;c.conditioning+=cache.size*b.size;c.gaussians+=3L*cache.size*b.size;c.components+=cache.size*b.size
              c.stored=math.max(c.stored,cache.size*slots(b.size))
              (unused,(x:V)=>{val r=cache(x.head);counted(r.law,r.components)})
            }
          }else if(mode=="pilot"){
            val initial=gaussian(None)
            val parents=if(t==0)Vector.empty[G]else f.current.particles.map(x=>gaussian(Some(x)))
            val logs=if(t==0)Vector.empty[Double]else f.current.logWeights
            c.pilotDraws+=512;c.pilotAncestors+=(if(t==0)0 else 512)
            val r=pilot(500000000L+100L*seed+t,initial,parents,logs,s.ys(t),s.noise,x=>{c.pilotLikelihoods+=1;c.h+=1;s.h(x)},s.points(t).size)
            c.fitDensities+=r.result.densityEvaluations;c.fitSweeps+=r.result.iterations;c.fitStatus=r.result.status.toString
            val constructions=fitConstructions(r.result)
            if(constructions<0)c.unknownGaussian+=1 else c.gaussians+=constructions
            c.stored=math.max(c.stored,r.stored)
            val mixture=r.mixture.getOrElse(throw new IllegalStateException("Pilot refusal: "+r.result.status+": "+r.result.message))
            c.guides+=1
            def shared(parent:Option[V])=counted(defend(P.multivariateGaussian(gaussian(parent)),mixture,.1),mixture.laws.size+1)
            (if(t==0)shared(None)else unused,(x:V)=>shared(Some(x)))
          }else (if(t==0)oldGuide(None)else unused,(x:V)=>oldGuide(Some(x)))
          val (q0,qt)=guidePair
          val kernel=P.Kernel(p0,(x:V)=>target(Some(x)),q0,qt)
          answer=Some(if(t==0)f.start(likelihood,kernel)else f.advance(likelihood,kernel))
        }
        c.ancestors+=answer.get.report.ancestorSelections
        require(c.draws==(t+1L)*n && c.likelihoods==(t+1L)*n,"Fixed production budget violated")
        require(c.stored<=100000,"Storage budget violated")
      }catch{case e:InterruptedException=>throw e;case NonFatal(e)=>status="error";terminal=e.getClass.getSimpleName+":"+e.getMessage}
      def moment(g:V=>Double)=answer.map(_.expectation(g)).getOrElse(Double.NaN)
      def indicator(p:Double=>Boolean)=moment(x=>if(p(x.head))1.0 else 0.0)
      val values=Vector(moment(_.head),moment(x=>x.head*x.head),indicator(_>0),indicator(_>2.5),
        indicator(_ < (if(name=="three") -1.0 else 0.0)),indicator(x=>if(name=="three")x>= -1 && x<=1 else x>=0),
        indicator(x=>name=="three" && x>1))
      c.nanos+=System.nanoTime()-start
      val row=Vector[Any](name,mode,n,seed,t,status,answer.map(_.logEvidence).getOrElse(Double.NaN))++values++
        Vector[Any](answer.flatMap(_.report.health.ess).getOrElse(Double.NaN),c.draws,c.likelihoods,c.targets,c.proposals,c.components,
          c.guides,c.h,c.jac,c.gaussians,c.conditioning,c.pilotDraws,c.pilotLikelihoods,c.pilotAncestors,c.fitDensities,c.fitSweeps,
          c.ancestors,c.stored,c.unknownGaussian,c.nanos,c.fitStatus,terminal.replace('\t',' ').replace('\n',' '))
      if(emit)println("MC_ROW\t"+row.mkString("\t"))
    }finally f.close()
  }
  def main(args:Array[String]):Unit={
    require(args.nonEmpty && Set("development","confirmation").contains(args(0)))
    val confirmation=args(0)=="confirmation"
    val budgets=if(confirmation){require(args.length==2);val src=Source.fromFile(args(1))
      try src.getLines().map(l=>{val a=l.split("\t");(a(0),a(1))->a(2).toInt}).toMap finally src.close()}else Map.empty[(String,String),Int]
    if(confirmation)require(budgets.keySet==(for(n<-names;m<-extra)yield(n,m)).toSet && budgets.values.forall(n=>n>=64 && n<=4096))
    def n(name:String,mode:String)=if(extra.contains(mode))budgets(name,mode)else 64
    println("MC_PROTOCOL_V1 "+args(0)+" rng="+SamplingRandom.defaultAlgorithm)
    println("MC_COLUMNS\t"+columns.mkString("\t"))
    for(name<-names;mode<-modes(name)++(if(confirmation)extra else Vector.empty);seed<- -502 to -501)run(name,mode,n(name,mode),seed,false)
    val seeds=if(confirmation)120001 to 120256 else 110001 to 110016
    for(seed<-seeds;name<-names){val ms=modes(name)++(if(confirmation)extra else Vector.empty)
      for(j<-ms.indices){val mode=ms((j+seed)%ms.size);run(name,mode,n(name,mode),seed,true)}}
  }
}
