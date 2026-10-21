package com.cra.figaro.test.modernization

import com.cra.figaro.algorithm.filtering.{AuxiliaryParticleProposal as A, BoundedParticleFilter as B,
  WeightedBoundedParticleFilter as W, WeightedParticleProposal as P, WeightedParticleCalibration as C}
import com.cra.figaro.util.random

/** Predeclared controls, not application data. Outputs EVERY seed including zero outcomes.
  * Fixed modes use 256 candidates/step; calibrated mode has the same ceiling including pilots.
  * Time includes preparation/pilots/production; no speedup threshold is an acceptance gate.
  */
object AuxiliaryParticleFilterStudy {
  private val modes=Vector("bootstrap","guided","auxiliary","calibrated-auxiliary","poor-auxiliary")
  private val emissions=Vector(Vector(.9,.2,.1,.2,.9),Vector(.1,.8,.2,.8,.1),Vector(.8,.3,.1,.3,.8))
  private def probabilities(i:Int):Vector[Double]=Vector.tabulate(5) { j =>
    (1.0+(if(j==(i*i+1)%5)8 else 0)+(if(j==i)3 else 0))/16
  }
  private def categorical(weights:Vector[Double]):P.Law[Double]=P.Law(() => {
    val u=random.nextDouble();var i=0;var s=weights(0)
    while(i<weights.size-1 && u>=s){i+=1;s+=weights(i)}
    i.toDouble
  },x => if(x>=0 && x<weights.size && x==x.toInt.toDouble)math.log(weights(x.toInt)) else Double.NegativeInfinity)

  private def run(scenario:String,mode:String,seed:Int,emit:Boolean):Unit={
    val gaussian=scenario=="gaussian"
    val observations=if(gaussian)Vector(.2,-.3,1.5,.8,-.2) else Vector(0.0,1.0,2.0)
    val prior=if(gaussian)P.gaussian(0,1) else categorical(Vector.fill(5)(.2))
    def transition(x:Double)=if(gaussian)P.gaussian(.8*x,math.sqrt(.3)) else categorical(probabilities(x.toInt))
    var densities=0L
    def counted(law:P.Law[Double])=P.Law(law.sample,(x:Double) => {densities+=1;law.logDensity(x)})
    val f=W(B.Model(prior.sample,(x:Double) => transition(x).sample()),W.Config(256,256,50,seed))
    var candidates=0L;var pilots=0L;var lookahead=0L;var ancestry=0.0;var collapsed=false
    var logZ=Double.NegativeInfinity;var mean=0.0;var second=0.0;var positive=0.0
    val begin=System.nanoTime()
    try {
      observations.zipWithIndex.foreach { (y,t) =>
        def likelihood(x:Double):Double=if(gaussian)P.gaussian(x,math.sqrt(.5)).logDensity(y) else math.log(emissions(t)(x.toInt))
        def predictive(parent:Double):Double=if(gaussian)P.gaussianPredictiveLogLikelihood(.8*parent,math.sqrt(.3),y,math.sqrt(.5))
          else math.log(probabilities(parent.toInt).zip(emissions(t)).map(_*_).sum)
        def conditional(parent:Option[Double]):P.Law[Double]=if(gaussian) {
          P.gaussianObservation(parent.map(.8*_).getOrElse(0.0),if(parent.isEmpty)1 else math.sqrt(.3),y,math.sqrt(.5))
        } else {
          val priorWeights=parent.map(x => probabilities(x.toInt)).getOrElse(Vector.fill(5)(.2))
          val weights=priorWeights.zip(emissions(t)).map(_*_);categorical(weights.map(_/weights.sum))
        }
        val q=P.Kernel(counted(prior),(x:Double) => counted(transition(x)),counted(conditional(None)),
          (x:Double) => counted(conditional(Some(x))))
        val policy=A.LookAhead[Double](x => if(mode=="poor-auxiliary") -2*predictive(x) else predictive(x),
          if(mode=="poor-auxiliary") .1 else 0)
        val e=W.Evidence[Double](likelihood,W.cappedLikelihood(0))
        val r=if(mode=="calibrated-auxiliary") {
          val req=C.Request(256,16,32,32,50)
          if(t==0)f.startCalibrated(likelihood,req,Some(q)) else f.advanceAuxiliaryCalibrated(likelihood,req,policy,Some(q))
        } else if(t==0) {
          if(mode=="bootstrap")f.start(e) else f.start(e,q)
        } else if(mode=="bootstrap") f.advance(e)
        else if(mode=="guided") f.advance(e,q)
        else f.advanceAuxiliary(e,policy,Some(q))
        candidates+=r.report.attempts
        f.lastCalibration.foreach(a => {pilots+=a.pilotCandidates;candidates+=a.pilotCandidates})
        f.lastAuxiliary.foreach(a => lookahead+=a.evaluations)
        r.report.distinctParents.foreach(n => ancestry+=n)
      }
      logZ=f.current.logEvidence
      def value(x:Double)=if(gaussian)x else x-2
      mean=f.current.expectation(value)
      second=f.current.expectation(x => value(x)*value(x))
      positive=f.current.expectation(x => if(value(x)>0)1 else 0)
    } catch {case e:W.NoViableParticles =>
      collapsed=true;candidates+=e.report.attempts
      f.lastCalibration.foreach(a => {pilots+=a.pilotCandidates;candidates+=a.pilotCandidates})
      f.lastAuxiliary.foreach(a => lookahead+=a.evaluations)
    } finally f.close()
    val nanos=System.nanoTime()-begin
    if(emit)println(s"APF_RUN $scenario $mode $seed $logZ $mean $second $positive $candidates $pilots $lookahead $densities $nanos $ancestry $collapsed")
  }
  def main(args:Array[String]):Unit={
    println("APF_PROTOCOL seeds=1..512 fixed=256 calibratedTotal=256 scalePilot=16 evaluationPilot=32 floor=32 score=50")
    // Warm every path; no warm-up outcome enters reported estimates or cost totals.
    for(scenario <- Vector("gaussian","nonlinear-finite"); mode <- modes; seed <- 1 to 8)
      run(scenario,mode,-seed,false)
    // Rotate mode order to reduce, not eliminate, JVM/order timing confounding.
    for(seed <- 1 to 512; scenario <- Vector("gaussian","nonlinear-finite"); j <- modes.indices)
      run(scenario,modes((j+seed)%modes.size),seed,true)
  }
}
