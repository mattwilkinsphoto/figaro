package com.cra.figaro.test.modernization

import com.cra.figaro.algorithm.filtering.{BoundedParticleFilter as B, WeightedBoundedParticleFilter as W,
  WeightedParticleCalibration as C, WeightedParticleProposal as P}

/** Predeclared synthetic study. All seeds retained; pilot draws count as work.
  * Not a wall-time benchmark or a universal variance/precision guarantee.
  */
object CalibratedGuidedFilterStudy {
  final case class Row(mode:String, metric:String, expected:Double, mean:Double,
    standardError:Double, runs:Int, candidates:Long, pilotCandidates:Long, fallbacks:Int) {
    def line:String=s"GUIDED $mode $metric $expected $mean $standardError $runs $candidates $pilotCandidates $fallbacks"
  }
  def run():Vector[Row] = {
    // Same analytic inputs as WeightedBoundedParticleStudy, preserving its independent oracle.
    val observations=Vector(.2,-.3,1.5,.8,-.2)
    val noise=.5; val drift=.8; val process=.3
    var m=0.0; var v=1.0; var logZ=0.0
    for((y,t)<-observations.zipWithIndex) {
      if(t>0) {m*=drift;v=drift*drift*v+process}
      val d=y-m;val s=v+noise
      logZ+= -.5*(math.log(2*math.Pi*s)+d*d/s)
      m+=v/s*d;v=v*noise/s
    }
    println(s"GUIDED_ORACLE $logZ $m ${m*m+v}")
    Vector("fixed-bootstrap","fixed-guided","calibrated-bootstrap","calibrated-guided").flatMap { mode=>
      var cost=0L;var pilots=0L;var fallbacks=0
      val prior=P.gaussian(0,1)
      def trans(x:Double)=P.gaussian(drift*x,math.sqrt(process))
      val data=(1 to 512).map { seed=>
        val f=W(B.Model(prior.sample,(x:Double)=>trans(x).sample()),W.Config(768,768,100,seed))
        try {
          for((y,t)<-observations.zipWithIndex) {
            val likelihood=(x:Double)=>P.gaussian(x,math.sqrt(noise)).logDensity(y)
            val proposal=P.Kernel(prior,trans,P.gaussianObservation(0,1,y,math.sqrt(noise)),
              (x:Double)=>P.gaussianObservation(drift*x,math.sqrt(process),y,math.sqrt(noise)))
            val guided=mode.endsWith("-guided")
            val r=if(mode.startsWith("calibrated")) {
              val q=Option.when(guided)(proposal)
              val req=C.Request(768,32,128,64,100)
              val result=if(t==0)f.startCalibrated(likelihood,req,q) else f.advanceCalibrated(likelihood,req,q)
              val a=f.lastCalibration.get
              pilots+=a.pilotCandidates;cost+=a.pilotCandidates
              if(a.status!=C.Status.Ready)fallbacks+=1
              require(a.pilotCandidates+result.report.attempts<=768)
              result
            } else {
              val e=W.Evidence(likelihood,W.cappedLikelihood(0))
              if(guided) {if(t==0)f.start(e,proposal) else f.advance(e,proposal)}
              else {if(t==0)f.start(e) else f.advance(e)}
            }
            cost+=r.report.attempts
          }
          val z=math.exp(f.current.logEvidence-logZ)
          Vector(z,z*f.current.expectation(identity),z*f.current.expectation(x=>x*x))
        } catch {case e:W.NoViableParticles=>
          cost+=e.report.attempts
          f.lastCalibration.foreach(a=>{cost+=a.pilotCandidates;pilots+=a.pilotCandidates})
          Vector(0.0,0.0,0.0)
        } finally f.close()
      }
      Vector(("evidence",1.0),("firstMoment",m),("secondMoment",m*m+v)).zipWithIndex.map {
        case ((metric,expected),i)=>
          val values=data.map(_(i));val mean=values.sum/values.size
          val se=math.sqrt(values.map(x=>(x-mean)*(x-mean)).sum/(values.size-1)/values.size)
          Row(mode,metric,expected,mean,se,values.size,cost,pilots,fallbacks)
      }
    }
  }
  def main(args:Array[String]):Unit=run().foreach(r=>println(r.line))
}
