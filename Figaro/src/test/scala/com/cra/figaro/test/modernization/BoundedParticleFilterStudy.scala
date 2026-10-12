package com.cra.figaro.test.modernization

import com.cra.figaro.algorithm.filtering.BoundedParticleFilter as B
import com.cra.figaro.util.{random, withRandomSeed}

/** Synthetic, exactly solvable two-step HMM calibration; includes every zero run.
  * Outputs CSV records to stdout. No wall-clock or application speedup claim.
  */
object BoundedParticleFilterStudy {
  private val transition=Vector(Vector(.6,.1,.3),Vector(.2,.6,.2),Vector(.5,.1,.4))
  private def draw(p:Vector[Double]):Int={val u=random.nextDouble();if(u<p(0)) 0 else if(u<p(0)+p(1)) 1 else 2}
  private case class Trial(evidence:Double, numerator:Double, attempts:Int, failed:Boolean, belowTarget:Boolean)
  private def adaptive(prior:Vector[Double],seed:Long):Trial={
    val f=B(B.Model(()=>draw(prior),(x:Int)=>draw(transition(x))),B.Config(8,256,8,seed))
    var work=0
    var below=false
    try {
      val first=f.start(_!=0);work+=first.report.attempts;below ||= !first.report.targetMet
      val second=f.advance(_!=0);work+=second.report.attempts;below ||= !second.report.targetMet
      val z=math.exp(second.logEvidence)
      Trial(z,z*second.particles.count(_==2)/second.particles.size,work,false,below)
    } catch {case e:B.NoViableParticles=>Trial(0,0,work+e.report.attempts,true,true)}
    finally f.close()
  }
  /** Independent fixed-population bootstrap reference, not the adaptive stopping kernel. */
  private def fixed(prior:Vector[Double],n:Int,seed:Long):Trial=withRandomSeed(seed){
    val first=Vector.fill(n)(draw(prior)).filter(_!=0)
    if(first.isEmpty) Trial(0,0,n,true,false)
    else {
      val second=Vector.fill(n)(draw(transition(first(random.nextInt(first.size))))).filter(_!=0)
      val z=first.size.toDouble/n*second.size/n
      Trial(z,if(second.isEmpty) 0 else z*second.count(_==2)/second.size,2*n,second.isEmpty,false)
    }
  }
  def main(args:Array[String]):Unit={
    val runs=4000
    println("BOUNDED_CSV,scenario,method,runs,min_candidates,max_candidates,target,fixed_n,exact_evidence,mean_evidence,se_evidence,exact_numerator,mean_numerator,se_numerator,mean_attempts,zero_runs,below_target_runs")
    for ((name,prior) <- Vector("ordinary"->Vector(.5,.3,.2),"rare"->Vector(.98,.012,.008))) {
      // Separate pilot fixes the comparison budget before evaluating either method.
      val pilotCost=(0 until 500).map(i=>adaptive(prior,100000L+i).attempts).sum/500.0
      val p=1-prior(0)
      val n=(2 to 512).minBy(k=>math.abs(k*(2-math.pow(1-p,k))-pilotCost))
      val exactZ=prior.indices.map(i=>if(i==0) 0.0 else prior(i)*(1-transition(i)(0))).sum
      val exactNumerator=prior.indices.map(i=>if(i==0) 0.0 else prior(i)*transition(i)(2)).sum
      for(method<-Vector("adaptive","fixed")) {
        val trials=Vector.tabulate(runs)(i=>if(method=="adaptive") adaptive(prior,200000L+i) else fixed(prior,n,300000L+i))
        def stats(f:Trial=>Double):(Double,Double)={
          val xs=trials.map(f);val mean=xs.sum/runs
          (mean,math.sqrt(xs.map(x=>(x-mean)*(x-mean)).sum/(runs-1)/runs))
        }
        val(z,zse)=stats(_.evidence);val(q,qse)=stats(_.numerator)
        // Predeclared smoke-calibration gates, not confidence guarantees or a superiority test.
        require(math.abs(z-exactZ)<=6*zse+1e-12,s"Evidence calibration failed: $name/$method")
        require(math.abs(q-exactNumerator)<=6*qse+1e-12,s"Joint moment calibration failed: $name/$method")
        println(s"BOUNDED_CSV,$name,$method,$runs,8,256,8,$n,$exactZ,$z,$zse,$exactNumerator,$q,$qse,${trials.map(_.attempts.toLong).sum.toDouble/runs},${trials.count(_.failed)},${trials.count(_.belowTarget)}")
      }
    }
  }
}
