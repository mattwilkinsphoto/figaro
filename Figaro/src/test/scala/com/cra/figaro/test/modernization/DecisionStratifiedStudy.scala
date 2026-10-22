package com.cra.figaro.test.modernization
import com.cra.figaro.algorithm.filtering.{FixedBudgetParticleFilter as F,LogParticleWeights as L}
import com.cra.figaro.util.{SamplingRandom,RandomContext}
import scala.util.control.NonFatal

/** Test-only fixed-N kernel with identical accounting; no production source modification. */
object DecisionStratifiedStudy {
  def run(name:String,ys:Vector[Double],method:String,seed:Int):Unit={
    import ResamplingScheduleExperiment.{Counts,Model,Row}
    val counts=new Counts;val model=new Model(name,ys,counts);val n=256
    val policy=if(method=="proto-always")F.Policy.Always else F.Policy.WhenEssBelow(method.split("-")(1).toDouble/100)
    var states=Vector.empty[Double];var logs=Vector.empty[Double];var logZ=0.0;var terminal=""
    var weights=Vector.empty[Double]
    var resamplings=0L;var refinements=0L;var tables=0L;var precision=0
    val rng=SamplingRandom.seeded(seed)
    RandomContext.withRandom(rng){ys.indices.foreach { t=>
      val start=System.nanoTime();var status="ok";var parents=0;var ess=Double.NaN
      if(terminal.nonEmpty)status="unavailable"
      else try {
        val resample=t>0 && F.shouldResample(policy,F.relativeEss(logs))
        val indices=if(t==0)Vector.empty else if(resample){
          val table=new DecisionStratifiedPrototype.Table(logs)
          val a=table.draw(rng);refinements+=table.refinedDecisions;tables+=table.tablesBuilt;precision=math.max(precision,table.maxPrecision)
          counts.ancestors+=n;resamplings+=1;a
        }else Vector.range(0,n)
        val base=if(t==0 || resample)Vector.fill(n)(-math.log(n))else logs
        val old=states
        states=Vector.tabulate(n){i=>counts.draws+=1;model.target(if(t==0)None else Some(old(indices(i))),t).sample()}
        val raw=F.reweight(base,states.map(x=>model.likelihood(x,t)))
        if(t>0)parents=raw.indices.filter(i=>raw(i).isFinite).map(indices).distinct.size
        val normalized=L.normalize(raw);logs=normalized.logs;weights=normalized.weights;logZ+=normalized.peak+normalized.logSum
        ess=1.0/normalized.weights.map(w=>w*w).sum
        require(logZ.isFinite && counts.draws==(t+1L)*n && counts.likelihoods==(t+1L)*n)
      }catch{case e:InterruptedException=>throw e;case NonFatal(e)=>status="error";terminal=e.getClass.getSimpleName+":"+e.getMessage}
      counts.nanos+=System.nanoTime()-start
      def moment(f:Double=>Double):Double=if(status!="ok")Double.NaN else {
        val values=states.map(f);val scale=math.max(1.0,values.map(math.abs).max)
        values.zip(weights).map((v,w)=>(v/scale)*w).sum*scale
      }
      val mean=moment(identity)
      val row=Row(name,method,n,seed,t,status,if(status=="ok")logZ else Double.NaN,mean,moment(x=>x*x),
        moment(x=>(x-mean)*(x-mean)),moment(x=>if(x>0)1.0 else 0.0),ess,parents,
        counts.work,counts.draws,counts.likelihoods,0,0,0,0,counts.ancestors,counts.components,resamplings,counts.nanos,terminal)
      println("SCHEDULE_ROW "+row.line)
    }}
    println(s"DECISION_WORK $name\t$method\t$seed\t$refinements\t$tables\t$precision")
  }
  def main(args:Array[String]):Unit={
    require(args.length==1);val data=ParticleDiversityDiagnostics.fixtures(args(0))
    println("DECISION_PROTOCOL_V1")
    val configs=for(m<-Vector("multi","proto");p<-Vector("always","25","50","75"))yield s"$m-$p"
    for(k<-60001 to 60256;s<-ResamplingScheduleExperiment.scenarios;j<-configs.indices){
      val m=configs((j+k)%configs.size);val ys=ResamplingScheduleExperiment.observations(s,data)
      if(m.startsWith("multi"))FixedBudgetFilterStudy.run(s,ys,m,k).foreach(r=>println("SCHEDULE_ROW "+r.line))else run(s,ys,m,k)
    }
  }
}
