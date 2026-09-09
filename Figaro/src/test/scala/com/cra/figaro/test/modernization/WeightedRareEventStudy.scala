package com.cra.figaro.test.modernization
import com.cra.figaro.algorithm.sampling.{RareEventImportance as R,GaussianMixtureProposal as M,VectorImportance as V}
import com.cra.figaro.library.atomic.continuous.MultivariateGaussianDistribution as G

/** Complete held-out paired study; no output files or sampling defaults are changed. */
object WeightedRareEventStudy {
  def main(args: Array[String]): Unit = {
    val repetitions=if(args.nonEmpty) args(0).toInt else 30
    require(repetitions>=1 && repetitions<=100)
    val base=V.Gaussian(G(Vector(0.0),Vector(Vector(1.0))))
    val budget=12000
    val cases=Vector(("common",0.0,false,.5),("one_tail",3.0,false,.0013498980316300945),
      ("two_tail",4.0,true,6.334248366623984e-5))
    def trial(name: String,t: Double,two: Boolean,truth: Double,seed: Long,method: String,emit: Boolean): Unit = {
      val score=(x: Vector[Double]) => if(two) math.abs(x.head) else x.head
      val start=System.nanoTime()
      val policy=R.FitConfig(drawsPerRound=1000,maxRounds=8,maxScoreEvaluations=8000,diagonalRidge=Vector(.25),seed=seed)
      val fit=method match {
        case "single" => Some(R.fitGaussian(base,score,t,policy))
        case "weighted" => Some(R.fitMixture(base,score,t,policy,M.Config(components=2,diagonalRidge=Vector(.25),maxDensityEvaluations=5000000)))
        case _ => None
      }
      val law=fit match {
        case Some(f) => f.proposal
        case None if method=="prior" => Some(R.prior(base))
        case None =>
          val pos=V.Gaussian(G(Vector(t),Vector(Vector(1.0))))
          val q=if(two) V.Mixture(Vector(.5,.5),Vector(pos,V.Gaussian(G(Vector(-t),Vector(Vector(1.0)))))) else pos
          Some(R.defensive(base,q))
      }
      val pilot=fit.map(_.scoreEvaluations).getOrElse(0)
      val result=law.map(q => R.run(q,score,t,R.Config(draws=budget-pilot,maxScoreEvaluations=budget-pilot,seed=seed)))
      val seconds=(System.nanoTime()-start)/1e9
      if(emit) println(s"WM_ROW,$name,$seed,$method,${fit.map(_.status.toString).getOrElse("Completed")},$truth,${result.flatMap(_.probability).getOrElse(Double.NaN)},$pilot,${result.map(_.scoreEvaluations).getOrElse(0)},${fit.map(_.componentDensityEvaluations).getOrElse(0L)},$seconds")
    }
    val methods=Vector("prior","single","weighted","supplied")
    for(i <- 0 until 3; method <- methods) trial("warm",3,true,.002699796063260189,i,method,false)
    println("WM_HEADER,case,seed,method,status,truth,estimate,pilotCalls,productionCalls,componentCalls,seconds")
    for((name,t,two,p) <- cases; i <- 0 until repetitions; j <- methods.indices)
      trial(name,t,two,p,95000L+i,methods((j+i)%methods.size),true)
  }
}
