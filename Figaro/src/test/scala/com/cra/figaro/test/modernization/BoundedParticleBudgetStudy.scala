package com.cra.figaro.test.modernization

import com.cra.figaro.algorithm.filtering.BoundedParticleBudget as A

/** Machine-readable output for the independent high-precision oracle, not a benchmark. */
object BoundedParticleBudgetStudy {
  def main(args:Array[String]):Unit={
    for ((n,k,c) <- Vector((10,0,.95),(10,1,.95),(10,10,.95),(1000,10,.95),
      (1000,500,.95),(1000000,1,.999),(1000000,999999,.999),(1000000,500000,.8))) {
      val a=A.advise(A.Pilot(n,k),A.Request(1000000,10,c,.05))
      println(s"BUDGET_INTERVAL $n $k $c ${a.interval.lower} ${a.interval.upper}")
      a.plan.foreach { p=>
        println(s"BUDGET_PLAN $n $k $c ${p.minCandidates} ${p.maxCandidates} ${p.targetSuccesses} ${p.expectedCandidates}")
      }
    }
    for ((min,max,r,p) <- Vector((2,20,3,.1),(12,50,5,.3),(2,1000000,101,.0001),
      (500,500,50,.2),(100,10000,101,.999),(2,1000000,2,.000001)))
      println(s"BUDGET_WORK $min $max $r $p ${A.expectedWork(min,max,r,p)}")
  }
}
