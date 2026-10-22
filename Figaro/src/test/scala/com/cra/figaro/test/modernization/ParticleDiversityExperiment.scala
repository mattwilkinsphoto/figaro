package com.cra.figaro.test.modernization

import com.cra.figaro.algorithm.filtering.{LogParticleWeights as L, WeightedParticleProposal as P}
import com.cra.figaro.util.{RandomContext, SamplingRandom}
import scala.util.control.NonFatal

/** INF-14 research experiment only. No public filter, stopped-estimator hook or runtime integration. */
object ParticleDiversityExperiment {
  val scenarios=Vector("growth","growth-sharp","persistent-modes","late","gaussian")
  val candidates=Vector("none","multi","strat","local1","local4","local8","global1","global4","global8")
  final class Work(val limit:Long=2000000L) {
    private val owner=Thread.currentThread();private var closed=false
    var transitions=0L;var likelihoods=0L;var proposals=0L;var draws=0L;var moves=0L;var accepted=0L
    private var stepTransitions=0L
    def access():Unit={require(Thread.currentThread()==owner && !closed,"Owned active work scope required")
      if(Thread.currentThread().isInterrupted)throw new InterruptedException("Diversity experiment interrupted")}
    def begin():Unit={access();stepTransitions=0}
    def target():Unit={access();if(stepTransitions>=limit)throw new IllegalStateException("Transition-density work cap exhausted")
      transitions+=1;stepTransitions+=1}
    def close():Unit={require(Thread.currentThread()==owner);closed=true}
    def total:Long=transitions+likelihoods+proposals+draws
  }
  def logAcceptance(current:Double,proposed:Double,forward:Double,reverse:Double):Double={
    require(current.isFinite && forward.isFinite,"Positive current target and forward proposal required")
    require((proposed.isFinite || proposed==Double.NegativeInfinity) &&
      (reverse.isFinite || reverse==Double.NegativeInfinity),"Invalid target or reverse proposal")
    if(proposed==Double.NegativeInfinity || reverse==Double.NegativeInfinity)Double.NegativeInfinity
    else {val ratio=(proposed-current)+(reverse-forward)
      if(!ratio.isFinite)throw new ArithmeticException("Unrepresentable MH log ratio")
      math.min(0.0,ratio)}
  }
  def resample(logs:Vector[Double],scheme:String,rng:java.util.Random):Vector[Int]={
    require(scheme=="multi" || scheme=="strat")
    val normalized=L.normalize(logs).logs;val n=logs.size
    if(scheme=="multi") {val tree=new L.Tree(normalized);Vector.fill(n)(tree.draw(L.logUniform(rng)))}
    else {
      // Research-only fixed-count stratification; finite-resolution CDF, not a tiny-interval guarantee.
      var i=0;var cumulative=normalized(0)
      Vector.tabulate(n){j=>
        val threshold=math.log((j+rng.nextDouble())/n)
        while(i<n-1 && threshold>=cumulative){i+=1;cumulative=L.add(cumulative,normalized(i))}
        i
      }
    }
  }
  final case class Row(scenario:String,method:String,n:Int,seed:Int,t:Int,status:String,
    logZ:Double,mean:Double,second:Double,positive:Double,ess:Double,parents:Int,
    work:Long,targets:Long,likelihoods:Long,proposals:Long,draws:Long,moves:Long,accepted:Long,nanos:Long,error:String) {
    def line:String=productIterator.map(_.toString.replace('\t',' ').replace('\n',' ')).mkString("\t")
  }
  def observations(name:String,data:Map[String,Vector[Double]]):Vector[Double]=name match {
    case "late"=>Vector.fill(40)(4.0)
    case "gaussian"=>Vector.tabulate(20)(t=>math.sin(t.toDouble))
    case _=>data(name)
  }
  def run(name:String,ys:Vector[Double],method:String,n:Int,seed:Int):Vector[Row]={
    require(scenarios.contains(name) && (candidates.contains(method) || method=="baseline"))
    require(n>=2 && n<=4096 && (method=="baseline" || n<=256))
    val k=if(method.startsWith("local") || method.startsWith("global"))method.last.asDigit else 0
    val base=ContinuousFilterBenchmark.Spec(if(name=="late")"persistent-modes" else if(name=="gaussian")"growth" else name)
    val rng=SamplingRandom.seeded(seed);val work=new Work()
    var states=Vector.empty[Double];var logs=Vector.empty[Double];var logZ=0.0;var nanos=0L;var terminal=""
    val rows=Vector.newBuilder[Row]
    try RandomContext.withRandom(rng){ys.indices.foreach { t=>
      val start=System.nanoTime();var ess=0.0;var parents=0;var status="ok"
      var mean=Double.NaN;var second=Double.NaN;var positive=Double.NaN
      if(terminal.nonEmpty)status="unavailable"
      else try {
        work.begin()
        val old=states;val oldLogs=logs
        val initial=if(name=="gaussian")P.gaussian(0,2) else ContinuousFilterBenchmark.mixture(base.initial)
        val transition=old.map(x=>if(name=="gaussian")P.gaussian(.8*x,math.sqrt(.3)) else P.gaussian(base.drift(x,t),base.process))
        val tree=if(t==0)None else Some(new L.Tree(oldLogs))
        val root=if(name=="gaussian" || (name=="late" && t>=32))ys(t) else math.sqrt(math.max(ys(t),0)/base.a)
        val global=ContinuousFilterBenchmark.mixture(Vector(
          ContinuousFilterBenchmark.Component(-root,1,.5),ContinuousFilterBenchmark.Component(root,1,.5)))
        def likelihood(x:Double):Double={work.access();work.likelihoods+=1
          val linear=name=="gaussian" || (name=="late" && t>=32)
          P.gaussian(if(linear)x else base.a*x*x,if(name=="gaussian")math.sqrt(.5) else base.noise).logDensity(ys(t))}
        def target(x:Double):Double={
          val prediction=if(t==0){work.target();initial.logDensity(x)}else{
            var sum=Double.NegativeInfinity;var i=0
            while(i<n){work.target();sum=L.add(sum,oldLogs(i)+transition(i).logDensity(x));i+=1};sum}
          val v=prediction+likelihood(x)
          require(v.isFinite || v==Double.NegativeInfinity,"Invalid target");v
        }
        val selected=Vector.newBuilder[Int]
        states=Vector.fill(n){work.access();work.draws+=1
          if(t==0)initial.sample() else {val i=tree.get.draw(L.logUniform(rng));selected+=i;transition(i).sample()}}
        parents=selected.result().distinct.size
        val normalized=L.normalize(states.map(likelihood))
        logZ+=normalized.peak+normalized.logSum-math.log(n)
        require(logZ.isFinite,"Invalid cumulative evidence")
        logs=normalized.logs;ess=1/normalized.weights.map(w=>w*w).sum
        if(method!="none" && method!="baseline"){
          val indices=resample(logs,if(method=="strat")"strat" else "multi",rng)
          states=indices.map(states);logs=Vector.fill(n)(-math.log(n))
          if(k>0){
            val current=states.toArray;val targets=current.map(target)
            for(sweep<-0 until k;i<-0 until n){
              work.access();work.moves+=1;work.draws+=1
              val proposed=if(method.startsWith("local"))current(i)+.5*rng.nextGaussian() else global.sample()
              val candidate=target(proposed)
              val (forward,reverse)=if(method.startsWith("local"))(0.0,0.0)else{
                work.proposals+=2;(global.logDensity(proposed),global.logDensity(current(i)))}
              if(L.logUniform(rng)<logAcceptance(targets(i),candidate,forward,reverse)){
                current(i)=proposed;targets(i)=candidate;work.accepted+=1}
            }
            states=current.toVector
          }
        }
        val weights=logs.map(math.exp)
        mean=states.zip(weights).map((x,w)=>x*w).sum
        second=states.zip(weights).map((x,w)=>x*x*w).sum
        positive=states.zip(weights).filter(_._1>0).map(_._2).sum
      }catch{
        case e:InterruptedException=>throw e
        case NonFatal(e)=>status="error";terminal=e.getClass.getSimpleName+":"+e.getMessage
      }
      nanos+=System.nanoTime()-start
      rows+=Row(name,method,n,seed,t,status,logZ,mean,second,positive,ess,parents,work.total,
        work.transitions,work.likelihoods,work.proposals,work.draws,work.moves,work.accepted,nanos,terminal)
    }}finally work.close()
    rows.result()
  }
  def main(args:Array[String]):Unit={
    require(args.length==2 || args.length==4,"fixtures.tsv calibration | confirmation method baselineN")
    val data=ParticleDiversityDiagnostics.fixtures(args(0))
    val configurations=if(args(1)=="calibration")candidates.map(_->64)++Vector(64,256,1024,4096).map("baseline"->_)
      else (Vector(args(2)->64,"none"->64)++Vector(64,256,1024,4096).map("baseline"->_)).distinct
    val seeds=if(args(1)=="calibration")2001 to 2016 else 10001 to 10256
    println("DIVERSITY_EXPERIMENT_V1 "+SamplingRandom.provenance(SamplingRandom.defaultAlgorithm))
    for(s<-scenarios;c<-configurations;seed<- -2 to -1)run(s,observations(s,data),c._1,c._2,seed)
    for(seed<-seeds;s<-scenarios;j<-configurations.indices){
      val (m,n)=configurations((j+seed)%configurations.size)
      run(s,observations(s,data),m,n,seed).foreach(r=>println("DIVERSITY_ROW "+r.line))
    }
  }
}
