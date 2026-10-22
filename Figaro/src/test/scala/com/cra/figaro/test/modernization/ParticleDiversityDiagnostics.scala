package com.cra.figaro.test.modernization

import com.cra.figaro.algorithm.filtering.{AuxiliaryParticleProposal as A, BoundedParticleFilter as B,
  WeightedBoundedParticleFilter as W, WeightedParticleProposal as P, WeightedParticleCalibration as C}
import scala.collection.mutable.ArrayBuffer
import scala.io.Source

/** Test-only observer of the unchanged released API. INF-14; no RNG draws in diagnostics. */
object ParticleDiversityDiagnostics {
  val modes=Vector("bootstrap256","bootstrap1024","auxiliary256","auxiliary1024","floor32","floor128")
  def expectedParents(weights:Vector[Double],n:Int):Double = {
    require(n>=0 && weights.nonEmpty && weights.forall(w=>w>=0 && w<=1) && math.abs(weights.sum-1)<1e-10)
    weights.map(w => if(w==1) {if(n==0)0.0 else 1.0} else -math.expm1(n*math.log1p(-w))).sum
  }
  final case class Result(states:Vector[W.Update[Double]], rows:Vector[String])
  def run(name:String,ys:Vector[Double],mode:String,seed:Int,observe:Boolean):Result = {
    import ContinuousFilterBenchmark.*
    val spec=Spec(name);val n=if(mode.endsWith("1024"))1024 else 256
    val calibrated=mode.startsWith("floor");val guided= !mode.startsWith("bootstrap")
    var t=0;var draws=0L;var likelihoods=0L;var densities=0L;var lookups=0L;var nanos=0L
    var previous=Vector.empty[Double];var logs=Vector.empty[Double]
    var index=Map.empty[Double,Int]
    val parents=ArrayBuffer.empty[Int];val scores=scala.collection.mutable.Map.empty[Double,Double]
    def record(p:Option[Double]):Unit={draws+=1;if(observe)parents+=p.fold(-1)(index)}
    def target(p:Option[Double])=mixture(spec.components(p,t))
    def sample(p:Option[Double]):Double={record(p);target(p).sample()}
    val filter=W(B.Model(()=>sample(None),(x:Double)=>sample(Some(x))),W.Config(n,n,64,seed))
    val states=ArrayBuffer.empty[W.Update[Double]];val rows=ArrayBuffer.empty[String]
    try ys.indices.foreach { step =>
      t=step;parents.clear();scores.clear();index=previous.zipWithIndex.toMap
      require(index.size==previous.size,"Duplicate parent values make this scalar observer ambiguous")
      def targetLaw(p:Option[Double]):P.Law[Double]={val law=target(p);P.Law(law.sample,x=>{densities+=1;law.logDensity(x)})}
      def guide(p:Option[Double]):P.Law[Double]={val law=guidance(spec,spec.components(p,t),ys(t))._1
        P.Law(()=>{record(p);law.sample()},x=>{densities+=1;law.logDensity(x)})}
      val q=if(guided)Some(P.Kernel(targetLaw(None),(x:Double)=>targetLaw(Some(x)),guide(None),(x:Double)=>guide(Some(x)))) else None
      val look=A.LookAhead[Double](x=>{lookups+=1;val v=guidance(spec,spec.components(Some(x),t),ys(t))._2
        if(observe)scores(x)=v;v},.1)
      def likelihood(x:Double):Double={likelihoods+=1;spec.logLikelihood(ys(t))(x)}
      val evidence=W.Evidence[Double](likelihood,W.cappedLikelihood(0))
      val start=System.nanoTime()
      val r=if(calibrated){
        val request=C.Request(256,16,32,if(mode=="floor32")32 else 128,64)
        if(t==0)filter.startCalibrated(likelihood,request,q) else filter.advanceAuxiliaryCalibrated(likelihood,request,look,q)
      }else if(t==0)q.fold(filter.start(evidence))(p=>filter.start(evidence,p))
      else if(guided)filter.advanceAuxiliary(evidence,look,q) else filter.advance(evidence)
      nanos+=System.nanoTime()-start
      if(observe){
        val selected=parents.takeRight(r.report.attempts).take(r.report.retainedCandidates).filter(_>=0)
        val histogram=selected.groupMapReduce(identity)(_=>1)(_+_)
        val concentration=if(selected.isEmpty)0.0 else histogram.values.map(k=>math.pow(k.toDouble/selected.size,2)).sum
        val probabilities=if(t==0)Vector.empty else if(guided)
          A.prepareLogs(previous,logs,A.LookAhead[Double](x=>scores(x),.1)).probabilities else logs.map(math.exp)
        // Random stopping conditions the retained count: the fixed-N formula is not its conditional expectation.
        val expected=if(t==0 || calibrated)-1.0 else expectedParents(probabilities,r.report.retainedCandidates)
        val fields=Vector(name,mode,seed.toString,t.toString,r.logEvidence.toString,r.expectation(identity).toString,
          r.expectation(x=>x*x).toString,r.expectation(x=>if(x>0)1.0 else 0.0).toString,
          r.report.health.ess.getOrElse(0.0).toString,r.report.distinctParents.getOrElse(0).toString,
          concentration.toString,expected.toString,r.report.attempts.toString,draws.toString,likelihoods.toString,
          densities.toString,lookups.toString,nanos.toString)
        rows+=fields.mkString("\t")
      }
      states+=r;previous=r.particles;logs=filter.currentLogWeights
    } finally filter.close()
    Result(states.toVector,rows.toVector)
  }
  def fixtures(path:String):Map[String,Vector[Double]]={
    val source=Source.fromFile(path)
    val entries=try source.getLines().drop(1).map(_.split("\t")).toVector finally source.close()
    entries.groupBy(_(0)).view.mapValues(_.sortBy(_(1).toInt).map(_(2).toDouble)).toMap
  }
  def main(args:Array[String]):Unit={
    require(args.length==1,"Frozen fixtures.tsv required")
    val data=fixtures(args(0))
    println("DIVERSITY_DIAGNOSTICS_V1 seeds=1..256; diagnostics do not consume RNG")
    for(seed<-1 to 256;s<-ContinuousFilterBenchmark.scenarios;m<-modes)
      run(s,data(s),m,seed,true).rows.foreach(r=>println("DIVERSITY_DIAG "+r))
  }
}
