package com.cra.figaro.test.modernization
import java.math.BigDecimal as D
import com.cra.figaro.util.SamplingRandom
import com.cra.figaro.algorithm.filtering.DecisionStratified.*

object DecisionStratifiedProductionOracle {
  def hex(xs:Seq[Double]):String=xs.map(java.lang.Double.toHexString).mkString(",")
  def emit(id:String,logs:Vector[Double],us:Vector[Double]):Unit={
    val table=new Table(logs)
    val choices=us.indices.map(j=>table.choose(j,us(j)))
    println(Vector("DECISION_CASE "+id,hex(logs),hex(us),choices.mkString(","),
      hex(table.lower.toSeq),hex(table.upper.toSeq),table.refinedDecisions.toString,table.maxPrecision.toString).mkString("\t"))
  }
  def main(args:Array[String]):Unit={
    require(args.length==1)
    val file=scala.io.Source.fromFile(args(0));var index=0
    try file.getLines().filter(_.contains("STRAT_DIAG ")).foreach { line=>
      val f=line.split("STRAT_DIAG ",2)(1).split("\t")
      val logs=f(6).split(",").toVector.map(java.lang.Double.valueOf(_).doubleValue)
      val rng=SamplingRandom.seeded(700001L+index)
      emit(index.toString,logs,Vector.fill(logs.size)(rng.nextDouble()));index+=1
    } finally file.close()
    require(index==6144)
    val cases=Vector(Vector(0.0,-1e-17),Vector(0.0,-40.0),Vector(-1000.0,0.0),
      Vector(0.0,-1.0,0.0,-1.0),Vector(Double.NegativeInfinity,-1000.0,0.0,Double.NegativeInfinity))
    for((logs,i)<-cases.zipWithIndex;u<-Vector(0.0,math.nextDown(1.0),.5))emit(s"adversarial-$i-$u",logs,Vector.fill(logs.size)(u))
    for(x<-Vector(0.0,1e-20,.125,.5,1.0,20.0,40.0,745.0,1000.0,1000000.0);p<-Vector(40,80,160)){
      val b=expNegative(new D(x),p)
      println(s"EXP_CASE ${java.lang.Double.toHexString(x)}\t$p\t${b.lo.toString}\t${b.hi.toString}")
    }
  }
}
