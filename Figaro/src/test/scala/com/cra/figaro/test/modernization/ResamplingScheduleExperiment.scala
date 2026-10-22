package com.cra.figaro.test.modernization

import com.cra.figaro.algorithm.filtering.{LogParticleWeights as L, WeightedParticleProposal as P,
  WeightedBoundedParticleFilter as W, BoundedParticleFilter as B, AuxiliaryParticleProposal as A}
import com.cra.figaro.util.{SamplingRandom, RandomContext, random}
import scala.util.control.NonFatal

/** INF-15: fixed-budget research only; never changes the released stopping estimator. */
object ResamplingScheduleExperiment {
  val training=Vector("growth","growth-sharp","persistent-modes","late","gaussian")
  val scenarios=training :+ "switching"
  val candidates=Vector("strat","ess25","ess50","ess75")
  val controls=Vector("single","double")
  val baselines=for(m<-Vector("bootstrap","guided","auxiliary");n<-Vector(64,256,1024))yield(m,n)
  val transition=Vector(Vector(.94,.05,.01),Vector(.10,.80,.10),Vector(.01,.05,.94))
  val prior=Vector(.45,.10,.45)
  final class Counts {
    val owner=Thread.currentThread()
    var draws=0L;var likelihoods=0L;var targets=0L;var proposals=0L;var guides=0L
    var lookahead=0L;var ancestors=0L;var components=0L;var resamplings=0L;var nanos=0L
    def access():Unit={require(owner==Thread.currentThread(),"Owned experiment required")
      if(Thread.currentThread().isInterrupted)throw new InterruptedException("Schedule interrupted")}
    def work:Long=draws+likelihoods+targets+proposals+guides+lookahead+ancestors+components
  }
  final case class Row(scenario:String,method:String,n:Int,seed:Int,t:Int,status:String,logZ:Double,
    mean:Double,second:Double,variance:Double,positive:Double,ess:Double,parents:Int,
    work:Long,draws:Long,likelihoods:Long,targets:Long,proposals:Long,guides:Long,
    lookahead:Long,ancestors:Long,components:Long,resamplings:Long,nanos:Long,error:String) {
    def line:String=productIterator.map(_.toString.replace('\t',' ').replace('\n',' ')).mkString("\t")
  }
  def observations(s:String,data:Map[String,Vector[Double]]):Vector[Double]=s match {
    case "switching"=>Vector.fill(32)(.8)
    case _=>ParticleDiversityExperiment.observations(s,data)
  }
  def shouldResample(method:String,logs:Vector[Double]):Boolean={
    require(candidates.contains(method) || controls.contains(method))
    if(!method.startsWith("ess"))true
    else 1.0/logs.map(x=>math.exp(2*x)).sum/logs.size < method.drop(3).toDouble/100
  }
  /** Input log weights are normalized. The increment is not log-mean when carrying weights. */
  def reweight(base:Vector[Double],likelihood:Vector[Double]):(Vector[Double],Double)={
    require(base.nonEmpty && base.size==likelihood.size)
    val r=L.normalize(base.zip(likelihood).map(_+_))
    (r.logs,r.peak+r.logSum)
  }
  final class Model(val name:String,val ys:Vector[Double],val counts:Counts) {
    import ContinuousFilterBenchmark.Component
    val spec=ContinuousFilterBenchmark.Spec(if(name=="late")"persistent-modes" else if(training.take(3).contains(name))name else "growth")
    def linear(t:Int):Boolean=name=="gaussian" || (name=="late" && t>=32)
    def sd:Double=if(name=="gaussian")math.sqrt(.5) else if(name=="switching").35 else spec.noise
    def normalLog(x:Double,m:Double,s:Double):Double={counts.components+=1;P.gaussian(m,s).logDensity(x)}
    def mixture(cs:Vector[Component]):P.Law[Double]={
      val raw=ContinuousFilterBenchmark.mixture(cs)
      P.Law(raw.sample,x=>{counts.components+=cs.size;raw.logDensity(x)})
    }
    def categorical(weights:Vector[Double]):P.Law[Double]=P.Law(()=>{
      val u=random.nextDouble();if(u<weights(0)) -1.0 else if(u<weights(0)+weights(1))0.0 else 1.0
    },x=>if(x== -1 || x==0 || x==1)math.log(weights(x.toInt+1)) else Double.NegativeInfinity)
    def cs(parent:Option[Double],t:Int):Vector[Component]=
      if(name=="gaussian")parent.fold(Vector(Component(0,2,1)))(x=>Vector(Component(.8*x,math.sqrt(.3),1)))
      else spec.components(parent,t)
    def target(parent:Option[Double],t:Int):P.Law[Double]=
      if(name=="switching")categorical(parent.fold(prior)(x=>transition(x.toInt+1))) else mixture(cs(parent,t))
    def likelihood(x:Double,t:Int):Double={counts.access();counts.likelihoods+=1
      val m=if(name=="switching"){if(t<24)math.abs(x) else x}else if(linear(t))x else spec.a*x*x
      normalLog(ys(t),m,sd)
    }
    def guidance(parent:Option[Double],t:Int):(P.Law[Double],Double)={
      counts.access();counts.guides+=1
      if(name=="switching"){
        val weights=parent.fold(prior)(x=>transition(x.toInt+1))
        val parts=Vector(-1.0,0.0,1.0).zip(weights).map((x,w)=>math.log(w)+normalLog(ys(t),if(t<24)math.abs(x)else x,sd))
        val z=ContinuousFilterBenchmark.logSum(parts)
        (categorical(parts.map(x=>math.exp(x-z))),z)
      }else if(linear(t)){
        val parts=cs(parent,t).map{c=>
          val v=c.sd*c.sd;val r=sd*sd;val gain=v/(v+r)
          (Component(c.mean+gain*(ys(t)-c.mean),math.sqrt(v*r/(v+r)),1),math.log(c.weight)+normalLog(ys(t),c.mean,math.sqrt(v+r)))
        }
        val z=ContinuousFilterBenchmark.logSum(parts.map(_._2))
        (mixture(parts.map((c,w)=>c.copy(weight=math.exp(w-z)))),z)
      }else{
        // Frozen INF-12 guidance: count its explicit component-density calls without altering its law.
        val parts=cs(parent,t);val (law,z)=ContinuousFilterBenchmark.guidance(spec,parts,ys(t))
        counts.components+=parts.size*(if(ys(t)>0)2 else 1)
        val evaluated=P.Law(law.sample,x=>{counts.components+=parts.size*(if(ys(t)>0)3 else 1);law.logDensity(x)})
        (evaluated,z)
      }
    }
  }
  def run(name:String,ys:Vector[Double],method:String,n:Int,seed:Int):Vector[Row]={
    require(scenarios.contains(name) && n>=2 && n<=1024)
    val released=Vector("bootstrap","guided","auxiliary").contains(method)
    require(released || candidates.contains(method) || controls.contains(method))
    val c=new Counts;val model=new Model(name,ys,c);val rng=SamplingRandom.seeded(seed)
    var t=0;var states=Vector.empty[Double];var logs=Vector.empty[Double];var logZ=0.0;var terminal=""
    val filter=if(released)Some(W(B.Model(()=>{c.draws+=1;model.target(None,t).sample()},
      (x:Double)=>{c.draws+=1;model.target(Some(x),t).sample()}),W.Config(n,n,n/4.0,seed)))else None
    val rows=Vector.newBuilder[Row]
    try RandomContext.withRandom(rng){ys.indices.foreach {index=>
      t=index;c.access();val start=System.nanoTime();var parents=0;var status="ok";var ess=Double.NaN
      if(terminal.nonEmpty)status="unavailable"
      else try{
        if(released){
          def target(parent:Option[Double])={val law=model.target(parent,t);P.Law(law.sample,x=>{c.targets+=1;law.logDensity(x)})}
          def proposal(parent:Option[Double])={val law=model.guidance(parent,t)._1
            P.Law(()=>{c.draws+=1;law.sample()},x=>{c.proposals+=1;law.logDensity(x)})}
          val kernel=if(method=="bootstrap")None else Some(P.Kernel(target(None),(x:Double)=>target(Some(x)),proposal(None),(x:Double)=>proposal(Some(x))))
          val e=W.Evidence[Double](x=>model.likelihood(x,t),W.cappedLikelihood(0))
          val look=A.LookAhead[Double](x=>{c.lookahead+=1;model.guidance(Some(x),t)._2},.1)
          val result=if(t==0)kernel.fold(filter.get.start(e))(q=>filter.get.start(e,q))
            else if(method=="bootstrap")filter.get.advance(e)
            else if(method=="guided")filter.get.advance(e,kernel.get)
            else filter.get.advanceAuxiliary(e,look,kernel)
          if(t>0){c.ancestors+=result.report.attempts;c.resamplings+=1}
          states=result.particles;logs=filter.get.currentLogWeights;logZ=result.logEvidence
          parents=result.report.distinctParents.getOrElse(0);ess=result.report.health.ess.getOrElse(0.0)
        }else{
          var base=Vector.fill(n)(-math.log(n))
          val old=states
          val indices=if(t==0)Vector.empty else if(shouldResample(method,logs)){
            c.ancestors+=n;c.resamplings+=1
            ParticleDiversityExperiment.resample(logs,if(method=="single" || method=="double")"multi" else "strat",rng)
          }else{base=logs;Vector.range(0,n)}
          parents=indices.distinct.size
          states=Vector.tabulate(n){i=>c.access();c.draws+=1;model.target(if(t==0)None else Some(old(indices(i))),t).sample()}
          val updated=reweight(base,states.map(x=>model.likelihood(x,t)));logs=updated._1;logZ+=updated._2
          ess=1/logs.map(x=>math.exp(2*x)).sum
          if(method=="double"){
            val selected=ParticleDiversityExperiment.resample(logs,"strat",rng)
            c.ancestors+=n;c.resamplings+=1;states=selected.map(states);logs=Vector.fill(n)(-math.log(n))
          }
        }
        require(logZ.isFinite,"Finite evidence required")
        require(c.draws==(t+1L)*n,"Exactly N state draws per step required")
      }catch{case e:InterruptedException=>throw e;case NonFatal(e)=>status="error";terminal=e.getClass.getSimpleName+":"+e.getMessage}
      c.nanos+=System.nanoTime()-start
      val weights=logs.map(math.exp)
      def expectation(f:Double=>Double)=states.zip(weights).map((x,w)=>f(x)*w).sum
      val mean=if(status=="ok")expectation(identity)else Double.NaN
      rows+=Row(name,method,n,seed,t,status,logZ,mean,if(status=="ok")expectation(x=>x*x)else Double.NaN,
        if(status=="ok")expectation(x=>(x-mean)*(x-mean))else Double.NaN,
        if(status=="ok")expectation(x=>if(x>0)1.0 else 0.0)else Double.NaN,ess,parents,
        c.work,c.draws,c.likelihoods,c.targets,c.proposals,c.guides,c.lookahead,c.ancestors,c.components,c.resamplings,c.nanos,terminal)
    }}finally filter.foreach(_.close())
    rows.result()
  }
  def main(args:Array[String]):Unit={
    require(args.length>=2 && (args(1)=="calibration" || args(1)=="confirmation"))
    val confirming=args(1)=="confirmation";require(!confirming || args.length==3)
    val data=ParticleDiversityDiagnostics.fixtures(args(0))
    val configs=(if(confirming)Vector(args(2)->256)else candidates.map(_->256))++controls.map(_->256)++baselines
    val fixtures=if(confirming)scenarios else training
    val seeds=if(confirming)40001 to 40256 else 30001 to 30016
    println("SCHEDULE_PROTOCOL_V1 "+SamplingRandom.provenance(SamplingRandom.defaultAlgorithm))
    for(s<-fixtures;(m,n)<-configs;k<- -102 to -101)run(s,observations(s,data),m,n,k)
    for(seed<-seeds;s<-fixtures;j<-configs.indices){val(m,n)=configs((j+seed)%configs.size)
      run(s,observations(s,data),m,n,seed).foreach(r=>println("SCHEDULE_ROW "+r.line))}
  }
}
