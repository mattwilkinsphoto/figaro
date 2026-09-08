package com.cra.figaro.test.modernization

import com.cra.figaro.library.atomic.continuous.*

/** Test-only matched-input control: frozen full scans versus the current audited totals. */
object GvmScalarAuditPerformance {
  @volatile private var sink=0.0
  def main(args: Array[String]): Unit = {
    require(args.isEmpty,"no arguments")
    def kernel(mu: Double=0,sd: Double=1,a: Double=0,b: Double=0,g: Double=0,k: Double=0) =
      GaussVonMisesDistribution(Vector(mu),Vector(Vector(sd*sd)),a,Vector(b),Vector(Vector(g)),k)
    val cases=Vector(("ordinary",kernel(k=4),kernel(b=.4,k=4),.0638773564899703),
      ("opposed",kernel(k=50),kernel(a=math.Pi,b=1e-5,k=50),47.127575486246805),
      ("curved",kernel(.5,1,.25,.5,.25,50),kernel(-.5,.5,-.5,-.25,2,50),1.3162805891364138))
    println(s"GVM_AUDIT_ENV pid=${ProcessHandle.current().pid()} java=${System.getProperty("java.version")} rounds=7 tolerance=1e-8")
    for((name,p,q,oracle) <- cases) {
      val before=GvmScalarFullSumBaseline.compare(p,q)
      val after=GaussVonMisesScalarBhattacharyya.compare(p,q)
      require(before.status.toString == "Estimated" && after.status.toString == "Estimated")
      require(before.productIterator.drop(1).toVector == after.productIterator.drop(1).toVector)
      require(math.abs(after.distance.get-oracle) <= 1e-8)
      println(s"GVM_AUDIT_CHECK case=$name identical=true evaluations=${after.evaluations} error=${math.abs(after.distance.get-oracle)}")
      val methods=Vector("full" -> (() => GvmScalarFullSumBaseline.compare(p,q).distance.get),
        "audited" -> (() => GaussVonMisesScalarBhattacharyya.compare(p,q).distance.get))
      def batch(index: Int,count: Int): Double = {
        val start=System.nanoTime()
        for(_ <- 0 until count) {
          if(Thread.currentThread().isInterrupted) throw new java.util.concurrent.CancellationException()
          sink=methods(index)._2()
        }
        (System.nanoTime()-start).toDouble
      }
      def calibrate(index: Int): Int = {
        var count=1
        while(batch(index,count) < 20000000 && count < 4096) count *= 2
        count
      }
      val warm=methods.indices.map(calibrate)
      val start=System.nanoTime()
      while(System.nanoTime()-start < 500000000L) methods.indices.foreach(i => batch(i,warm(i)))
      val counts=methods.indices.map(calibrate)
      for(round <- 0 until 7; offset <- methods.indices) {
        val i=(round+offset)%2
        val elapsed=batch(i,counts(i))
        println(s"GVM_AUDIT_TIMING case=$name method=${methods(i)._1} round=$round batch=${counts(i)} nsPerCall=${elapsed/counts(i)}")
      }
    }
    println(s"GVM_AUDIT_COMPLETE cases=3 methods=6 rounds=7 sinkFinite=${sink.isFinite}")
  }
}
