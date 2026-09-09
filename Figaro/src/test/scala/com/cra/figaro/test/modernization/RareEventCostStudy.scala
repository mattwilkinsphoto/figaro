package com.cra.figaro.test.modernization

import com.cra.figaro.algorithm.sampling.{RareEventImportance as R,VectorImportance as V}
import com.cra.figaro.library.atomic.continuous.{MultivariateGaussianDistribution as G}

/** Fixed, pilot-inclusive held-out study. Prints machine-readable rows; never writes files. */
object RareEventCostStudy {
  def main(args: Array[String]): Unit = {
    val repetitions=if(args.nonEmpty) args(0).toInt else 30
    require(repetitions>=1 && repetitions<=200)
    val base=V.Gaussian(G(Vector(0.0),Vector(Vector(1.0))))
    val cases=Vector(("common",0.0,false,.5),("tail3",3.0,false,.0013498980316300945),
      ("tail5",5.0,false,2.8665157187919391e-7),("two_tail4",4.0,true,6.334248366623984e-5))
    val budget=10000
    // Untimed warmup exercises both methods; it is not included in accepted rows.
    for(i <- 0 until 3) {
      R.run(R.prior(base),_.head,3,R.Config(draws=2000,maxScoreEvaluations=2000,seed=i))
      R.fitGaussian(base,_.head,3,R.FitConfig(drawsPerRound=500,diagonalRidge=Vector(.25),seed=i))
    }
    println("RARE_HEADER,case,seed,method,status,truth,estimate,mcse,eventEss,hits,pilotCalls,productionCalls,densityCalls,seconds")
    for((name,threshold,twoSided,truth) <- cases; i <- 0 until repetitions) {
      val seed=90000L+i
      val score=(x: Vector[Double]) => if(twoSided) math.abs(x.head) else x.head
      def direct(): Unit={
        val start=System.nanoTime()
        val r=R.run(R.prior(base),score,threshold,R.Config(draws=budget,maxScoreEvaluations=budget,seed=seed))
        val seconds=(System.nanoTime()-start)/1e9
        println(s"RARE_ROW,$name,$seed,prior,Completed,$truth,${r.probability.get},${r.standardError.getOrElse(Double.NaN)},${r.eventEss},${r.eventHits},0,${r.scoreEvaluations},${r.densityEvaluations},$seconds")
      }
      def fitted(): Unit={
        val start=System.nanoTime()
        val fit=R.fitGaussian(base,score,threshold,R.FitConfig(drawsPerRound=500,maxRounds=10,maxScoreEvaluations=5000,
          diagonalRidge=Vector(.25),seed=seed))
        fit.proposal match {
          case Some(q) =>
            val remaining=budget-fit.scoreEvaluations
            val r=R.run(q,score,threshold,R.Config(draws=remaining,maxScoreEvaluations=remaining,seed=seed))
            val seconds=(System.nanoTime()-start)/1e9
            println(s"RARE_ROW,$name,$seed,ce,${fit.status},$truth,${r.probability.get},${r.standardError.getOrElse(Double.NaN)},${r.eventEss},${r.eventHits},${fit.scoreEvaluations},${r.scoreEvaluations},${fit.densityEvaluations+r.densityEvaluations},$seconds")
          case None =>
            val seconds=(System.nanoTime()-start)/1e9
            println(s"RARE_ROW,$name,$seed,ce,${fit.status},$truth,NaN,NaN,0,0,${fit.scoreEvaluations},0,${fit.densityEvaluations},$seconds")
        }
      }
      def supplied(): Unit={
        val start=System.nanoTime()
        val positive=V.Gaussian(G(Vector(threshold),Vector(Vector(1.0))))
        val candidate=if(twoSided) V.Mixture(Vector(.5,.5),Vector(positive,V.Gaussian(G(Vector(-threshold),Vector(Vector(1.0)))))) else positive
        val r=R.run(R.defensive(base,candidate),score,threshold,R.Config(draws=budget,maxScoreEvaluations=budget,seed=seed))
        val seconds=(System.nanoTime()-start)/1e9
        println(s"RARE_ROW,$name,$seed,supplied,Completed,$truth,${r.probability.get},${r.standardError.getOrElse(Double.NaN)},${r.eventEss},${r.eventHits},0,${r.scoreEvaluations},${r.densityEvaluations},$seconds")
      }
      i%3 match {
        case 0 => direct(); fitted(); supplied()
        case 1 => fitted(); supplied(); direct()
        case _ => supplied(); direct(); fitted()
      }
    }
  }
}
