package com.cra.figaro.test.modernization

import com.cra.figaro.algorithm.filtering.{FixedBudgetParticleFilter as F, BoundedParticleFilter as B}
import scala.util.control.NonFatal

/** INF-15 integration protocol: public API only, unchanged independent model fixtures. */
object FixedBudgetFilterStudy {
  val configs=for(m<-Vector("multi","strat");p<-Vector("always","25","50","75"))yield s"$m-$p"
  def run(name:String,ys:Vector[Double],method:String,seed:Int):Vector[ResamplingScheduleExperiment.Row]={
    import ResamplingScheduleExperiment.{Counts,Model,Row}
    val counts=new Counts;val model=new Model(name,ys,counts)
    var t=0;var resamplings=0L;var terminal=""
    val parts=method.split("-")
    val policy=if(parts(1)=="always")F.Policy.Always else F.Policy.WhenEssBelow(parts(1).toDouble/100)
    val scheme=if(parts(0)=="multi")F.Method.LogTreeMultinomial else F.Method.Stratified
    val filter=F(B.Model(()=>{counts.draws+=1;model.target(None,t).sample()},
      (x:Double)=>{counts.draws+=1;model.target(Some(x),t).sample()}),F.Config(256,policy,scheme,seed))
    try ys.indices.map { index=>
      t=index;val start=System.nanoTime();var status="ok"
      var answer:Option[F.Update[Double]]=None
      if(terminal.nonEmpty)status="unavailable"
      else try {
        val r=if(t==0)filter.start(x=>model.likelihood(x,t))else filter.advance(x=>model.likelihood(x,t))
        answer=Some(r);counts.ancestors+=r.report.ancestorSelections
        if(r.report.decision==F.Decision.Resampled)resamplings+=1
        require(counts.draws==(t+1L)*256 && counts.likelihoods==(t+1L)*256,"Fixed callback budget violated")
      }catch{
        case e:InterruptedException=>throw e
        case e:F.StratifiedPrecisionLoss=>status="refused";terminal=e.getClass.getSimpleName
        case NonFatal(e)=>status="error";terminal=e.getClass.getSimpleName+":"+e.getMessage
      }
      counts.nanos+=System.nanoTime()-start
      def moment(f:Double=>Double)=answer.map(_.expectation(f)).getOrElse(Double.NaN)
      val mean=moment(identity)
      Row(name,method,256,seed,t,status,answer.map(_.logEvidence).getOrElse(Double.NaN),
        mean,moment(x=>x*x),moment(x=>(x-mean)*(x-mean)),moment(x=>if(x>0)1.0 else 0.0),
        answer.flatMap(_.report.health.ess).getOrElse(Double.NaN),answer.flatMap(_.report.contributingParents).getOrElse(0),
        counts.work,counts.draws,counts.likelihoods,0,0,0,0,counts.ancestors,counts.components,resamplings,counts.nanos,terminal)
    }.toVector finally filter.close()
  }
  def main(args:Array[String]):Unit={
    require(args.length==1,"Fixture path required")
    val data=ParticleDiversityDiagnostics.fixtures(args(0))
    println("FIXED_BUDGET_PROTOCOL_V1")
    for(s<-ResamplingScheduleExperiment.scenarios;m<-configs;k<- -202 to -201)
      run(s,ResamplingScheduleExperiment.observations(s,data),m,k)
    for(k<-50001 to 50256;s<-ResamplingScheduleExperiment.scenarios;j<-configs.indices){
      val m=configs((j+k)%configs.size)
      run(s,ResamplingScheduleExperiment.observations(s,data),m,k).foreach(r=>println("SCHEDULE_ROW "+r.line))
    }
  }
}
