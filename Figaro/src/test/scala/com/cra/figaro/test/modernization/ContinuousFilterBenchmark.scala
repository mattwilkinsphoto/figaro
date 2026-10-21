package com.cra.figaro.test.modernization

import com.cra.figaro.algorithm.filtering.{AuxiliaryParticleProposal as A, BoundedParticleFilter as B,
  WeightedBoundedParticleFilter as W, WeightedParticleProposal as P, WeightedParticleCalibration as C}
import com.cra.figaro.util.random
import scala.io.Source
import scala.util.control.NonFatal

/** Benchmark-only frozen guidance and all-outcome harness. See INF-12 and the frozen protocol.
  * No runtime API, weight policy or numerical refusal is changed for this study.
  */
object ContinuousFilterBenchmark {
  val modes = Vector("bootstrap","guided","auxiliary","calibrated-auxiliary","poor-auxiliary")
  val scenarios = Vector("growth","growth-sharp","persistent-modes")
  final case class Component(mean: Double, sd: Double, weight: Double)
  final case class Spec(name: String) {
    require(scenarios.contains(name))
    val a: Double = if(name=="persistent-modes") .25 else .05
    val noise: Double = if(name=="growth-sharp") .25 else if(name=="growth") 1.0 else .5
    val process: Double = if(name=="persistent-modes") .3 else math.sqrt(10)
    val initial: Vector[Component] = if(name=="persistent-modes")
      Vector(Component(-4,.5,.5),Component(4,.5,.5)) else Vector(Component(0,2,1))
    def drift(x: Double,t: Int): Double = if(name=="persistent-modes") x
      else .5*x+25*x/(1+x*x)+8*math.cos(1.2*(t-1))
    def components(parent: Option[Double],t: Int): Vector[Component] =
      parent.fold(initial)(x => Vector(Component(drift(x,t),process,1)))
    def logLikelihood(y: Double)(x: Double): Double = P.gaussian(a*x*x,noise).logDensity(y)
  }
  def logSum(values: Vector[Double]): Double = {
    val peak=values.max
    peak+math.log(values.map(x => math.exp(x-peak)).sum)
  }
  def mixture(components: Vector[Component]): P.Law[Double] = {
    require(components.nonEmpty && components.forall(c => c.weight>=0 && c.weight.isFinite) &&
      math.abs(components.map(_.weight).sum-1)<1e-12)
    val laws=components.map(c => P.gaussian(c.mean,c.sd))
    P.Law(() => {
      val u=random.nextDouble();var i=0;var cumulative=components(0).weight
      while(i<components.size-1 && u>=cumulative){i+=1;cumulative+=components(i).weight}
      laws(i).sample()
    },x => logSum(components.indices.map(i => math.log(components(i).weight)+laws(i).logDensity(x)).toVector))
  }
  /** Two inverse-observation roots with local Gaussian widths; a benchmark approximation. */
  def guidance(spec: Spec,cs: Vector[Component],y: Double): (P.Law[Double],Double) = {
    val target=mixture(cs)
    if(y<=0) {
      val predictive=cs.map { c =>
        val v=c.sd*c.sd
        math.log(c.weight)+P.gaussian(spec.a*(c.mean*c.mean+v),
          math.sqrt(spec.noise*spec.noise+spec.a*spec.a*(2*v*v+4*c.mean*c.mean*v))).logDensity(y)
      }
      (target,logSum(predictive))
    } else {
      val root=math.sqrt(y/spec.a)
      val derivative=2*spec.a*math.max(root,1.0)
      val localSd=math.max(.25,spec.noise/derivative)
      val parts=for(c <- cs; r <- Vector(-root,root)) yield {
        val v=c.sd*c.sd;val rv=localSd*localSd;val gain=v/(v+rv)
        val conditional=Component(c.mean+gain*(r-c.mean),math.sqrt(v*rv/(v+rv)),1)
        val logMass=math.log(c.weight)+P.gaussian(c.mean,math.sqrt(v+rv)).logDensity(r)-math.log(derivative)
        (conditional,logMass)
      }
      val predictive=logSum(parts.map(_._2))
      val proposed=mixture(parts.map((c,l) => c.copy(weight=math.exp(l-predictive))))
      (P.defensive(target,proposed,.1),predictive)
    }
  }
  final class Counts {
    var candidates=0L;var pilots=0L;var likelihoods=0L;var targetDensities=0L
    var proposalDensities=0L;var guides=0L;var lookahead=0L;var nanos=0L
  }
  private def run(spec: Spec,ys: Vector[Double],budget: Int,mode: String,seed: Int,emit: Boolean): Unit = {
    val counts=new Counts
    var t=0
    def target(parent: Option[Double])=mixture(spec.components(parent,t))
    // All draws occur inside the filter-owned RNG scope, including split pilots.
    def bootstrap(parent: Option[Double]): Double = {counts.candidates+=1;target(parent).sample()}
    val filter=W(B.Model(() => bootstrap(None),(x:Double) => bootstrap(Some(x))),W.Config(budget,budget,budget/4.0,seed))
    var terminal=""
    try ys.indices.foreach { index =>
      t=index
      var state:Option[W.Update[Double]]=None
      var status="unavailable";var error=terminal
      if(terminal.isEmpty) {
        val before=counts.candidates
        val start=System.nanoTime()
        try {
          val y=ys(t)
          def likelihood(x:Double):Double={counts.likelihoods+=1;spec.logLikelihood(y)(x)}
          def targetLaw(parent:Option[Double]):P.Law[Double]={
            val law=target(parent)
            P.Law(law.sample,x => {counts.targetDensities+=1;law.logDensity(x)})
          }
          def guided(parent:Option[Double]):P.Law[Double]={
            counts.guides+=1
            val law=guidance(spec,spec.components(parent,t),y)._1
            P.Law(() => {counts.candidates+=1;law.sample()},x => {counts.proposalDensities+=1;law.logDensity(x)})
          }
          val q=if(mode=="bootstrap")None else Some(P.Kernel(targetLaw(None),
            (x:Double)=>targetLaw(Some(x)),guided(None),(x:Double)=>guided(Some(x))))
          val look=A.LookAhead[Double](x => {
            counts.lookahead+=1;counts.guides+=1
            val lp=guidance(spec,spec.components(Some(x),t),y)._2
            if(mode=="poor-auxiliary") -2*lp else lp
          },.1)
          val evidence=W.Evidence[Double](likelihood,W.cappedLikelihood(0))
          val answer=if(mode=="calibrated-auxiliary") {
            val request=C.Request(budget,16,32,32,budget/4.0)
            if(t==0)filter.startCalibrated(likelihood,request,q)
            else filter.advanceAuxiliaryCalibrated(likelihood,request,look,q)
          } else if(t==0) q.fold(filter.start(evidence))(p => filter.start(evidence,p))
          else if(mode=="bootstrap")filter.advance(evidence)
          else if(mode=="guided")filter.advance(evidence,q.get)
          else filter.advanceAuxiliary(evidence,look,q)
          state=Some(answer);status="ok";error=""
        } catch {
          case _:W.NoViableParticles => status="zero";error="zero";terminal="zero"
          case e:ArithmeticException => status="numerical";error=e.getMessage;terminal=error
          case NonFatal(e) => status="error";error=e.getClass.getSimpleName+":"+e.getMessage;terminal=error
        } finally {
          if(mode=="calibrated-auxiliary")counts.pilots+=math.min(48L,counts.candidates-before)
          counts.nanos+=System.nanoTime()-start
        }
      }
      if(emit) {
        val measurements=state.map(r => Vector(r.logEvidence,r.expectation(identity),r.expectation(x=>x*x),
          r.expectation(math.abs),r.expectation(x=>if(x>0)1.0 else 0.0),r.report.health.ess.getOrElse(0.0),
          r.report.distinctParents.getOrElse(0).toDouble).map(_.toString)).getOrElse(Vector.fill(7)("NA"))
        val prefix=Vector(spec.name,budget.toString,mode,seed.toString,t.toString,status)
        val work=Vector(counts.candidates,counts.pilots,counts.likelihoods,counts.targetDensities,
          counts.proposalDensities,counts.guides,counts.lookahead,counts.nanos).map(_.toString)
        println("CONTINUOUS_ROW "+(prefix++measurements++work:+error.replace('\t',' ').replace('\n',' ')).mkString("\t"))
      }
    } finally filter.close()
  }
  def main(args:Array[String]):Unit = {
    require(args.length==1,"Provide frozen fixtures.tsv")
    val source=Source.fromFile(args(0))
    val entries=try source.getLines().drop(1).map(_.split("\t")).toVector finally source.close()
    val fixtures=scenarios.map(s => s->entries.filter(_(0)==s).sortBy(_(1).toInt).map(_(2).toDouble)).toMap
    require(fixtures.values.forall(_.size==20))
    println("CONTINUOUS_PROTOCOL base=bf6a2cd7 seeds=1..256 budgets=256,1024 steps=20 modes="+modes.mkString(","))
    for(s<-scenarios;m<-modes;seed<- -2 to -1)run(Spec(s),fixtures(s),256,m,seed,false)
    for(seed<-1 to 256;s<-scenarios;n<-Vector(256,1024);j<-modes.indices)
      run(Spec(s),fixtures(s),n,modes((j+seed)%modes.size),seed,true)
  }
}
