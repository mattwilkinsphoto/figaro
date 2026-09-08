package com.cra.figaro.example

import com.cra.figaro.library.atomic.continuous.*
import java.nio.file.{Files,Path}
import java.time.Duration
import jdk.jfr.Recording
import jdk.jfr.consumer.RecordingFile
import scala.collection.mutable
import scala.jdk.CollectionConverters.*

/** Bounded JFR CPU-sample profile of the high-work scalar fixture; no library instrumentation. */
object GaussVonMisesScalarProfile {
  @volatile private var sink=0.0
  /** Profile repeated high-curvature comparisons and print inclusive stack-frame counts.
    * @param args output JFR path (must not exist) and optional call count in [10,5000], default 400
    * @return Unit; writes a JFR recording and prints CPU samples involving the scalar integrator
    * @example `GaussVonMisesScalarProfile.main(Array("scalar-profile.jfr", "400"))`
    */
  def main(args: Array[String]): Unit = {
    require(args != null && args.length >= 1 && args.length <= 2,"use output.jfr [calls]")
    val destination=Path.of(args(0)).toAbsolutePath.normalize()
    require(!Files.exists(destination),"profile output must not exist")
    val calls=if(args.length == 2) args(1).toInt else 400
    require(calls >= 10 && calls <= 5000,"calls must be in [10,5000]")
    def k(mu: Double,sd: Double,a: Double,b: Double,g: Double) =
      GaussVonMisesDistribution(Vector(mu),Vector(Vector(sd*sd)),a,Vector(b),Vector(Vector(g)),50)
    val p=k(.5,1,.25,.5,.25); val q=k(-.5,.5,-.5,-.25,2)
    def compare(): Unit = {
      val r=GaussVonMisesScalarBhattacharyya.compare(p,q)
      require(r.status == GaussVonMisesScalarBhattacharyya.Status.Estimated &&
        math.abs(r.distance.get-1.3162805891364138) <= 1e-8,"profile fixture failed accuracy")
      sink=r.distance.get
    }
    for(_ <- 0 until 40) compare()
    val recording=new Recording()
    try {
      recording.enable("jdk.ExecutionSample").withPeriod(Duration.ofMillis(2))
      recording.start()
      for(_ <- 0 until calls) compare()
      recording.stop()
      recording.dump(destination)
    } finally recording.close()
    var samples=0; var scalar=0; var summation=0
    val inclusive=mutable.Map.empty[String,Int].withDefaultValue(0)
    val input=new RecordingFile(destination)
    try while(input.hasMoreEvents) {
      val event=input.readEvent()
      if(event.getEventType.getName == "jdk.ExecutionSample" && event.getStackTrace != null) {
        samples += 1
        val names=event.getStackTrace.getFrames.asScala.map(f => f.getMethod.getType.getName+"."+f.getMethod.getName)
        val relevant=names.filter(_.contains("GaussVonMisesScalarBhattacharyya"))
        if(relevant.nonEmpty) {
          scalar += 1
          if(relevant.exists(_.contains("compensated"))) summation += 1
          relevant.distinct.foreach(n => inclusive(n) += 1)
        }
      }
    } finally input.close()
    require(scalar > 0,"no scalar CPU samples captured")
    println(s"GVM_PROFILE calls=$calls samples=$samples scalarSamples=$scalar compensatedSamples=$summation sink=$sink")
    inclusive.toVector.sortBy(-_._2).take(15).foreach((name,count) => println(s"GVM_PROFILE_FRAME count=$count method=$name"))
  }
}
