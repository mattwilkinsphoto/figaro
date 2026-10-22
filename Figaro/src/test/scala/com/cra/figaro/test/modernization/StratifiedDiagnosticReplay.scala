package com.cra.figaro.test.modernization

import com.cra.figaro.algorithm.filtering.{FixedBudgetParticleFilter as F, BoundedParticleFilter as B, LogParticleWeights as L}
import scala.util.control.NonFatal

/** INF-15 read-only diagnostics around the unchanged public API. No extra RNG use. */
object StratifiedDiagnosticReplay {
  def capture(name:String,method:String,seed:Int,t:Int,kind:String,logs:Vector[Double]):Unit={
    val weights=L.normalize(logs).weights
    val last=logs.lastIndexWhere(_.isFinite)
    var total=0.0
    val cdf=weights.indices.map { i=>
      total=if(i>=last)1.0 else total+weights(i)
      total
    }.toVector
    val predicted=logs.indices.exists(i=>logs(i).isFinite &&
      (weights(i)==0.0 || cdf(i)<=(if(i==0)0.0 else cdf(i-1)) || cdf(i)>1.0))
    require(predicted==(kind=="refused"),"Diagnostic guard and actual outcome disagree")
    def hex(xs:Vector[Double])=xs.map(java.lang.Double.toHexString).mkString(",")
    println("STRAT_DIAG "+Vector(name,method,seed.toString,t.toString,kind,
      F.relativeEss(logs).toString,hex(logs),hex(weights),hex(cdf)).mkString("\t"))
  }
  def run(name:String,ys:Vector[Double],method:String,seed:Int):Unit={
    import ResamplingScheduleExperiment.{Counts,Model,Row}
    val counts=new Counts;val model=new Model(name,ys,counts)
    var t=0;var resamplings=0L;var terminal=""
    var first:Option[(Int,Vector[Double])]=None
    var failure:Option[(Int,Vector[Double])]=None
    val part=method.split("-")(1)
    val policy=if(part=="always")F.Policy.Always else F.Policy.WhenEssBelow(part.toDouble/100)
    val filter=F(B.Model(()=>{counts.draws+=1;model.target(None,t).sample()},
      (x:Double)=>{counts.draws+=1;model.target(Some(x),t).sample()}),F.Config(256,policy,F.Method.Stratified,seed))
    try {
      ys.indices.foreach { index=>
        t=index;var status="ok";var answer:Option[F.Update[Double]]=None
        val before=if(t>0 && terminal.isEmpty)Some(filter.current.logWeights) else None
        if(terminal.nonEmpty)status="unavailable"
        else try {
          val r=if(t==0)filter.start(x=>model.likelihood(x,t)) else filter.advance(x=>model.likelihood(x,t))
          answer=Some(r);counts.ancestors+=r.report.ancestorSelections
          if(r.report.decision==F.Decision.Resampled){resamplings+=1;if(first.isEmpty)first=Some(t->before.get)}
          require(counts.draws==(t+1L)*256 && counts.likelihoods==(t+1L)*256)
        }catch{
          case e:InterruptedException=>throw e
          case e:F.StratifiedPrecisionLoss=>status="refused";terminal=e.getClass.getSimpleName;failure=Some(t->before.get)
          case NonFatal(e)=>status="error";terminal=e.getClass.getSimpleName+":"+e.getMessage
        }
        def moment(f:Double=>Double)=answer.map(_.expectation(f)).getOrElse(Double.NaN)
        val mean=moment(identity)
        val row=Row(name,method,256,seed,t,status,answer.map(_.logEvidence).getOrElse(Double.NaN),
          mean,moment(x=>x*x),moment(x=>(x-mean)*(x-mean)),moment(x=>if(x>0)1.0 else 0.0),
          answer.flatMap(_.report.health.ess).getOrElse(Double.NaN),answer.flatMap(_.report.contributingParents).getOrElse(0),
          counts.work,counts.draws,counts.likelihoods,0,0,0,0,counts.ancestors,counts.components,resamplings,0,terminal)
        println("SCHEDULE_ROW "+row.line)
      }
      failure match {
        case Some((at,logs))=>capture(name,method,seed,at,"refused",logs)
        case None=>first.foreach((at,logs)=>capture(name,method,seed,at,"completed-control",logs))
      }
    }finally filter.close()
  }
  def main(args:Array[String]):Unit={
    require(args.length==1,"Fixture path required")
    val data=ParticleDiversityDiagnostics.fixtures(args(0))
    println("STRATIFIED_DIAGNOSTIC_V1")
    for(k<-50001 to 50256;s<-ResamplingScheduleExperiment.scenarios;m<-Vector("strat-always","strat-25","strat-50","strat-75"))
      run(s,ResamplingScheduleExperiment.observations(s,data),m,k)
  }
}
