package com.cra.figaro.test.modernization

import com.cra.figaro.algorithm.filtering.{FixedBudgetParticleFilter as F, BoundedParticleFilter as B,
  WeightedParticleProposal as P, AuxiliaryParticleProposal as A}
import scala.util.control.NonFatal

/** INF-16 frozen protocol v1. Only public runtime APIs; independent reference is Python. */
object GuidedFixedBudgetStudy {
  val configs=Vector("b-m-a","g-m-a","a-m-a","b-s-50","g-s-50","a-s-50",
    "bad-s-50","big-s-50","old-guided","old-auxiliary")
  def run(name:String,ys:Vector[Double],mode:String,seed:Int):Vector[ResamplingScheduleExperiment.Row]={
    import ResamplingScheduleExperiment.{Counts,Model,Row}
    if(mode.startsWith("old-"))return ResamplingScheduleExperiment.run(name,ys,mode.drop(4),128,seed).map(_.copy(method=mode))
    val n=if(mode=="big-s-50")512 else 128
    val counts=new Counts;val model=new Model(name,ys,counts)
    var t=0;var terminal=""
    val policy=if(mode.endsWith("-a"))F.Policy.Always else F.Policy.WhenEssBelow(.5)
    val method=if(mode.contains("-m-"))F.Method.LogTreeMultinomial else F.Method.Stratified
    val isGuided=mode.startsWith("g-") || mode.startsWith("a-") || mode.startsWith("bad-")
    val isAuxiliary=mode.startsWith("a-") || mode.startsWith("bad-")
    val filter=F(B.Model(()=>{counts.draws+=1;model.target(None,t).sample()},
      (x:Double)=>{counts.draws+=1;model.target(Some(x),t).sample()}),F.Config(n,policy,method,seed))
    try ys.indices.map { index=>
      t=index;val start=System.nanoTime();var status="ok"
      var answer:Option[F.Update[Double]]=None
      if(terminal.nonEmpty)status="unavailable"
      else try{
        def target(parent:Option[Double])={val law=model.target(parent,t)
          P.Law(law.sample,(x:Double)=>{counts.targets+=1;law.logDensity(x)})}
        def proposed(parent:Option[Double])={val law=model.guidance(parent,t)._1
          P.Law(()=>{counts.draws+=1;law.sample()},(x:Double)=>{counts.proposals+=1;law.logDensity(x)})}
        val q=if(isGuided)Some(P.Kernel(target(None),(x:Double)=>target(Some(x)),proposed(None),(x:Double)=>proposed(Some(x))))else None
        val look=A.LookAhead[Double](x=>{counts.lookahead+=1
          val lp=model.guidance(Some(x),t)._2
          if(mode.startsWith("bad")) -2*lp else lp},.1)
        val likelihood=(x:Double)=>model.likelihood(x,t)
        val r=if(t==0)q.fold(filter.start(likelihood))(k=>filter.start(likelihood,k))
          else if(isAuxiliary)filter.advanceAuxiliary(likelihood,look,q)
          else q.fold(filter.advance(likelihood))(k=>filter.advance(likelihood,k))
        answer=Some(r);counts.ancestors+=r.report.ancestorSelections
        if(r.report.decision==F.Decision.Resampled)counts.resamplings+=1
        require(counts.draws==(t+1L)*n && counts.likelihoods==(t+1L)*n,"Fixed callback budget violated")
        val extra=filter.lastProposalReport.get
        require(extra.targetDensityCalls==(if(isGuided)n else 0))
        require(extra.auxiliary.map(_.evaluations).getOrElse(0)==
          (if(isAuxiliary && r.report.decision==F.Decision.Resampled)n else 0))
      }catch{
        case e:InterruptedException=>throw e
        case e:F.StratifiedPrecisionLoss=>status="refused";terminal=e.getClass.getSimpleName+":"+e.reason
        case NonFatal(e)=>status="error";terminal=e.getClass.getSimpleName+":"+e.getMessage
      }
      counts.nanos+=System.nanoTime()-start
      def moment(f:Double=>Double)=answer.map(_.expectation(f)).getOrElse(Double.NaN)
      val mean=moment(identity)
      Row(name,mode,n,seed,t,status,answer.map(_.logEvidence).getOrElse(Double.NaN),mean,moment(x=>x*x),
        moment(x=>(x-mean)*(x-mean)),moment(x=>if(x>0)1.0 else 0.0),
        answer.flatMap(_.report.health.ess).getOrElse(Double.NaN),answer.flatMap(_.report.contributingParents).getOrElse(0),
        counts.work,counts.draws,counts.likelihoods,counts.targets,counts.proposals,counts.guides,counts.lookahead,
        counts.ancestors,counts.components,counts.resamplings,counts.nanos,terminal)
    }.toVector finally filter.close()
  }
  def main(args:Array[String]):Unit={
    require(args.length==1,"Frozen fixture path required")
    val data=ParticleDiversityDiagnostics.fixtures(args(0))
    println("GUIDED_FIXED_PROTOCOL_V1 seeds=70001..70256")
    for(s<-ResamplingScheduleExperiment.scenarios;m<-configs;k<- -302 to -301)
      run(s,ResamplingScheduleExperiment.observations(s,data),m,k)
    for(k<-70001 to 70256;s<-ResamplingScheduleExperiment.scenarios;j<-configs.indices){
      val m=configs((j+k)%configs.size)
      run(s,ResamplingScheduleExperiment.observations(s,data),m,k).foreach(r=>println("SCHEDULE_ROW "+r.line))
    }
  }
}
