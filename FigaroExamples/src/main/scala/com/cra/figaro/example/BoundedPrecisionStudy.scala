package com.cra.figaro.example

import com.cra.figaro.algorithm.sampling.{BoundedIidPrecision as B,EmpiricalBernsteinPrecision as E}

/** Reproducible stopped-work comparison; timings are evidence, never a CI speed assertion. */
object BoundedPrecisionStudy {
  def main(args: Array[String]): Unit = {
    require(args.length<=1)
    val repeats=if(args.isEmpty) 20 else args(0).toInt
    require(repeats>=1 && repeats<=100)
    println("BOUNDED_STUDY,seed,model,method,draws,nanos,covered,precision,error")
    // Warm both arithmetic paths; production evidence uses different seeds.
    B.run(B.Config(0,1,.1,maxDraws=1000))(_.nextDouble())
    E.run(B.Config(0,1,.1,maxDraws=1000))(_.nextDouble())
    for(seed <- 0 until repeats; model <- 0 until 4; method <- (if(seed%2==0) Vector(0,1) else Vector(1,0))) {
      val c=B.Config(0,1,.02,maxDraws=100000,seed=510001L+7919L*seed)
      val truth=if(model==1) .001 else .5
      def sample(rng: scala.util.Random): Double = model match {
        case 0 => if(rng.nextDouble()<.5) 1.0 else 0.0
        case 1 => if(rng.nextDouble()<.001) 1.0 else 0.0
        case 2 => .49+.02*rng.nextDouble()
        case 3 =>
          // Antisymmetric bounded utility with known mean .5 and meaningful callback cost.
          val u=rng.nextDouble()-.5
          var z=u
          for(_ <- 0 until 200) z=math.sin(z)
          .5+.01*z
      }
      val start=System.nanoTime()
      val (n,lo,hi,reason,error)=if(method==0) {
        val r=B.run(c)(sample); (r.draws,r.lower,r.upper,r.reason,r.errorBound)
      } else {
        val r=E.run(c)(sample); (r.draws,r.lower,r.upper,r.reason,r.errorBound)
      }
      val elapsed=System.nanoTime()-start
      println(s"BOUNDED_STUDY,$seed,$model,$method,$n,$elapsed,${lo<=truth && truth<=hi},${reason==B.StopReason.PrecisionReached},$error")
    }
  }
}
