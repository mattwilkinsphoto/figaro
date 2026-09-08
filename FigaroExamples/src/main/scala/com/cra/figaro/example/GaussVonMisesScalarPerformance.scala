package com.cra.figaro.example

import com.cra.figaro.library.atomic.continuous.*
import java.util.concurrent.CancellationException

/** Matched-accuracy scalar study. Timings of refusals are not successful comparisons. */
object GaussVonMisesScalarPerformance {
  @volatile private var sink=0.0
  private val tolerance=1e-8
  private case class Fixture(name: String,p: GaussVonMisesDistribution,q: GaussVonMisesDistribution,oracle: Double)
  private case class Observation(status: String,distance: Option[Double],work: Int,path: String)
  private case class Method(name: String,unit: String,evaluate: () => Observation)
  private def kernel(mu: Double=0,sd: Double=1,alpha: Double=0,beta: Double=0,gamma: Double=0,k: Double=0) =
    GaussVonMisesDistribution(Vector(mu),Vector(Vector(sd*sd)),alpha,Vector(beta),Vector(Vector(gamma)),k)
  private def interrupted(): Unit =
    if(Thread.currentThread().isInterrupted) throw new CancellationException("scalar GVM study interrupted")
  private def fixtures = Vector(
    Fixture("gaussian",kernel(),kernel(mu=2),.5),
    Fixture("constant-opposed",kernel(k=50),kernel(alpha=math.Pi,k=50),47.1275755018718045),
    Fixture("linear-moderate",kernel(k=4),kernel(beta=.4,k=4),.06387735648997029028485409595318461855683),
    Fixture("curved-unequal",kernel(.3,1.1,.2,.7,.3,4.5),kernel(-.4,.8,-.5,-.2,-.15,1.2),.355509912840583167741149),
    Fixture("linear-concentrated",kernel(k=50),kernel(beta=1,k=50),1.29442047240411212017621814538838746359),
    Fixture("opposed-weak",kernel(k=50),kernel(alpha=math.Pi,beta=1e-5,k=50),47.1275754862468045235184686645218055091),
    Fixture("opposed-moderate",kernel(k=50),kernel(alpha=math.Pi,beta=.1,k=50),45.0461096112953972045121001065173035263),
    Fixture("curved-concentrated",kernel(.5,1,.25,.5,.25,50),kernel(-.5,.5,-.5,-.25,2,50),1.3162805891364137825114985731082026827))

  private def methods(f: Fixture) = Vector(
    Method("fourier","harmonics",() => {
      val r=GaussVonMisesBhattacharyya.compare(f.p,f.q,tolerance,256)
      Observation(r.status.toString,r.distance,r.harmonicsUsed,r.method)
    }),
    Method("positive","evaluations",() => {
      val r=GaussVonMisesScalarBhattacharyya.compare(f.p,f.q,tolerance,50000)
      Observation(r.status.toString,r.distance,r.evaluations,r.method)
    }))

  private def validate(f: Fixture,m: Method,r: Observation): Unit = {
    val accepted=if(m.name == "fourier") "Resolved" else "Estimated"
    require((r.status == accepted) == r.distance.nonEmpty,"status/distance contract mismatch")
    require(r.work >= 0 && r.work <= (if(m.name == "fourier") 256 else 50000),"work cap exceeded")
    r.distance.foreach(d => require(d.isFinite && math.abs(d-f.oracle) <= tolerance,"unmatched oracle accuracy"))
    if(m.name == "positive") require(r.status == "Estimated","positive method must resolve study fixtures")
    else require(r.status == (if(f.name.startsWith("opposed-")) "NumericallyUnresolved" else "Resolved"),
      "Fourier outcome changed: reassess study rather than silently changing the timing population")
  }

  private def batch(m: Method,count: Int): Double = {
    val start=System.nanoTime()
    var i=0
    while(i < count) { interrupted(); sink=m.evaluate().distance.getOrElse(-1.0); i += 1 }
    (System.nanoTime()-start).toDouble
  }

  /** Check eight fixed scalar pairs or measure both APIs at absolute distance error at most 1e-8.
    * @param args `Array("check")` or `Array("measure", "7")`; measured rounds range from 3 to 31
    * @return Unit; prints complete accuracy/work/outcome records and optional nanosecond timings; throws on failed controls
    * @example `GaussVonMisesScalarPerformance.main(Array("check"))`
    */
  def main(args: Array[String]): Unit = {
    require(args != null && args.nonEmpty && Set("check","measure")(args(0)),"use check or measure [rounds]")
    val timed=args(0) == "measure"
    require(args.length <= (if(timed) 2 else 1),"unexpected arguments")
    val rounds=if(args.length == 2) args(1).toInt else 7
    require(rounds >= 3 && rounds <= 31,"rounds must be in [3,31]")
    println(s"GVM_SCALAR_ENV pid=${ProcessHandle.current().pid()} java=${System.getProperty("java.version")} os=${System.getProperty("os.name").replace(' ','_')} arch=${System.getProperty("os.arch")} processors=${Runtime.getRuntime.availableProcessors} maxHeap=${Runtime.getRuntime.maxMemory} tolerance=$tolerance rounds=$rounds timed=$timed warmupMs=500 calibrationTargetMs=20 maxBatch=65536")
    var accepted=0; var refused=0
    for(f <- fixtures) {
      val candidates=methods(f)
      val initial=candidates.map { m =>
        val r=m.evaluate(); validate(f,m,r)
        if(r.distance.nonEmpty) accepted += 1 else refused += 1
        println(s"GVM_SCALAR_METHOD case=${f.name} method=${m.name} status=${r.status} distance=${r.distance.fold("none")(_.toString)} oracle=${f.oracle} error=${r.distance.fold("none")(d => math.abs(d-f.oracle).toString)} work=${r.work} unit=${m.unit} path=${r.path}")
        r
      }
      if(timed) {
        // Construct kernels outside timing for both APIs. Include all API preprocessing,
        // integration and result allocation; never reuse a computed comparison result.
        for(_ <- 0 until 20; m <- candidates) batch(m,1)
        def calibrate(m: Method): Int = {
          var count=1; var elapsed=batch(m,count)
          while(elapsed < 20000000 && count < 65536) { count *= 2; elapsed=batch(m,count) }
          count
        }
        val warmCounts=candidates.map(calibrate)
        val warmStart=System.nanoTime()
        while(System.nanoTime()-warmStart < 500000000L)
          for((m,count) <- candidates.zip(warmCounts)) batch(m,count)
        val counts=candidates.map(calibrate)
        for(round <- 0 until rounds; offset <- candidates.indices) {
          val index=(offset+round)%candidates.size
          val elapsed=batch(candidates(index),counts(index))
          println(s"GVM_SCALAR_TIMING case=${f.name} method=${candidates(index).name} round=$round batch=${counts(index)} nsPerCall=${elapsed/counts(index)}")
        }
      }
      for((m,r) <- candidates.zip(initial)) {
        val after=m.evaluate(); validate(f,m,after)
        require(after == r,"numerical outcome changed after warm-up/timing")
      }
    }
    require(accepted == 14 && refused == 2,"incomplete outcome population")
    println(s"GVM_SCALAR_COMPLETE fixtures=8 methods=16 accepted=$accepted refused=$refused rounds=${if(timed) rounds else 0} sinkFinite=${sink.isFinite}")
  }
}
