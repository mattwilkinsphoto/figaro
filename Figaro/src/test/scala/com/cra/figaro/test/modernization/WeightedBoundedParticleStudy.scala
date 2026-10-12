package com.cra.figaro.test.modernization

import com.cra.figaro.algorithm.filtering.{BoundedParticleFilter as B, WeightedBoundedParticleFilter as W}
import com.cra.figaro.util.random

/** Synthetic analytic calibration, not a performance benchmark or real-world validation. */
object WeightedBoundedParticleStudy {
  final case class Row(mode:String, metric:String, expected:Double, mean:Double,
    standardError:Double, runs:Int, candidates:Long) {
    def line:String=s"WEIGHTED $mode $metric $expected $mean $standardError $runs $candidates"
  }
  def run():Vector[Row] = {
    val observations=Vector(.2,-.3,1.5,.8,-.2)
    val noise=.5
    val drift=.8
    val process=.3
    var m=0.0; var v=1.0; var logZ=0.0
    for((y,t)<-observations.zipWithIndex) {
      if(t>0) {m*=drift; v=drift*drift*v+process}
      val innovation=y-m
      val s=v+noise
      logZ+= -.5*(math.log(2*math.Pi*s)+innovation*innovation/s)
      m+=v/s*innovation
      v=v*noise/s
    }
    println(s"WEIGHTED_ORACLE $logZ $m ${m*m+v}")
    Vector("fixed","adaptive").flatMap { mode =>
      var cost=0L
      val data=(1 to 512).map { seed =>
        val config=if(mode=="fixed") W.Config(384,384,100,seed) else W.Config(128,768,100,seed)
        val f=W(B.Model(()=>random.nextGaussian(),(x:Double)=>drift*x+math.sqrt(process)*random.nextGaussian()),config)
        try {
          for((y,t)<-observations.zipWithIndex) {
            val e=W.Evidence[Double](x=> -.5*(math.log(2*math.Pi*noise)+(y-x)*(y-x)/noise),
              W.cappedLikelihood(-.5*math.log(2*math.Pi*noise)))
            val r=if(t==0) f.start(e) else f.advance(e)
            require(r.report.attempts<=config.maxCandidates)
            cost+=r.report.attempts
          }
          val z=math.exp(f.current.logEvidence-logZ)
          Vector(z,z*f.current.expectation(identity),z*f.current.expectation(x=>x*x))
        } catch {case e:W.NoViableParticles => cost+=e.report.attempts; Vector(0.0,0.0,0.0)}
        finally f.close()
      }
      Vector(("evidence",1.0),("firstMoment",m),("secondMoment",m*m+v)).zipWithIndex.map {
        case ((metric,expected),i) =>
          val values=data.map(_(i)); val mean=values.sum/values.size
          val se=math.sqrt(values.map(x=>(x-mean)*(x-mean)).sum/(values.size-1)/values.size)
          Row(mode,metric,expected,mean,se,values.size,cost)
      }
    }
  }
  def main(args:Array[String]):Unit=run().foreach(r=>println(r.line))
}
