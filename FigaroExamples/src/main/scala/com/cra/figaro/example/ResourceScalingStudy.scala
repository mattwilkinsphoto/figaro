package com.cra.figaro.example

import com.cra.figaro.algorithm.sampling.VectorSliceSampler as VS
import com.cra.figaro.algorithm.sampling.ProposalScheme
import com.cra.figaro.algorithm.sampling.parallel.{MultiChainVectorSliceSampler as MC, MultiChainMetropolisHastings as MH}
import com.cra.figaro.language.*
import com.cra.figaro.library.atomic.continuous.Normal
import java.lang.management.ManagementFactory
import java.nio.{ByteBuffer}
import java.nio.file.{Files, Path}
import java.security.MessageDigest
import java.time.Duration
import java.util.concurrent.{Callable, Executors, TimeUnit}
import jdk.jfr.Recording
import jdk.jfr.consumer.RecordingFile
import scala.jdk.CollectionConverters.*

/** Resource measurements outside production APIs; see docs/RESOURCE_SCALING_ASSESSMENT.md. */
object ResourceScalingStudy {
  @volatile private var retained: AnyRef = null
  private val design = Vector.tabulate(64, 8)((i, j) =>
    (if (Integer.bitCount((i % 8) & j) % 2 == 0) 1.0 else -1.0) / math.sqrt(8))
  private def referenceDensity(kind: String, x: Vector[Double]): Double = kind match {
    case "gaussian32" => -0.5 * x.map(v => v * v).sum
    case "positive32" => if (x.forall(_ > 0)) -x.sum else Double.NegativeInfinity
    case "likelihood8" =>
      var total = x.map(v => v * v).sum
      for (row <- design) {
        var product = 0.0; var j = 0
        while (j < 8) { product += row(j) * x(j); j += 1 }
        total += product * product
      }
      -0.5 * total
  }
  // Application callback experiment only. Preserve reduction order, including its first value.
  private def loopDensity(kind: String, x: Vector[Double]): Double = {
    var total = if (kind == "positive32") x.head else x.head * x.head
    if (kind == "positive32" && !(x.head > 0)) return Double.NegativeInfinity
    var i = 1
    while (i < x.length) {
      val v = x(i)
      if (kind == "positive32") {
        if (!(v > 0)) return Double.NegativeInfinity
        total += v
      } else total += v * v
      i += 1
    }
    if (kind == "likelihood8") {
      i = 0
      while (i < design.length) {
        val row = design(i)
        var product = 0.0; var j = 0
        while (j < 8) { product += row(j) * x(j); j += 1 }
        total += product * product; i += 1
      }
    }
    if (kind == "positive32") -total else -0.5 * total
  }

  private final case class Output(raw: AnyRef, fingerprint: String, evaluations: Long,
    construction: Double, sampling: Double, diagnostics: Double, stored: Long)
  private def hash(): (MessageDigest, Long => Unit) = {
    val h = MessageDigest.getInstance("SHA-256"); val b = ByteBuffer.allocate(8)
    (h, n => { b.clear(); b.putLong(n); h.update(b.array()); () })
  }
  private def one(kind: String, variant: String, draws: Int, workers: Int, seed: Long): Output = {
    val (h, number) = hash()
    def real(x: Double): Unit = number(java.lang.Double.doubleToLongBits(x))
    def text(x: String): Unit = { val b = x.getBytes(java.nio.charset.StandardCharsets.UTF_8); number(b.length); h.update(b) }
    def summary(d: com.cra.figaro.algorithm.sampling.parallel.McmcDiagnostics.Summary): Unit = {
      real(d.mean); real(d.standardDeviation)
      Vector(d.rHat,d.bulkEss,d.tailEss,d.meanEss,d.mcseMean).foreach(v => real(v.getOrElse(Double.NaN)))
      number(d.warnings.size); d.warnings.foreach(text)
    }
    if (kind == "graphWide") {
      val r = MH.run(MH.Config(chains=4,drawsPerChain=draws,warmUp=500,parallelism=workers,seed=seed)) { (u, _) =>
        val xs = List.fill(32)(Normal(0.0,1.0)(using "", u))
        val sum = Apply(Inject(xs*)(using "", u), (x: List[Double]) => x.sum)(using "", u)
        // Fixed ordered joint proposal: this is not the default random-node graph workload.
        MH.Model(Vector(MH.Observable("sum",sum)(identity)), Some(ProposalScheme(xs*)))
      }
      r.chains.foreach { c =>
        number(c.index); number(c.seed); real(c.acceptanceRate); number(c.initializationAttempts)
        number(c.draws("sum").size); c.draws("sum").foreach(real)
      }
      summary(r.diagnostics("sum"))
      Output(r,java.util.HexFormat.of().formatHex(h.digest()),-1L,Double.NaN,Double.NaN,Double.NaN,4L*draws)
    } else {
      val dim = if (kind == "likelihood8") 8 else 32
      val method = if (kind == "positive32") VS.Method.Quantile else VS.Method.GPSS
      val (r,t) = MC.measuredRun(MC.Config(VS.Config(method,draws=draws,warmUp=500,seed=seed,maxEvaluations=100000000),parallelism=workers)) {
        (i, _) => MC.Model(Vector.fill(dim)(0.5+i/4.0), x =>
          if (variant == "loop") loopDensity(kind,x) else referenceDensity(kind,x))
      }
      require(r.chains.forall(_.result.reason==VS.StopReason.DrawsReached),"Incomplete sampling work")
      r.chains.foreach { c =>
        number(c.index); number(c.seed); number(c.result.evaluations); number(c.result.warmUpCompleted)
        text(c.result.reason.toString); number(c.result.samples.size)
        c.result.samples.foreach(_.foreach(real)); c.result.lastState.foreach(real)
      }
      number(r.diagnosticDrawsPerChain); r.diagnostics.foreach(summary)
      number(r.warnings.size); r.warnings.foreach(text)
      Output(r,java.util.HexFormat.of().formatHex(h.digest()),r.chains.map(_.result.evaluations).sum,
        t.constructionSeconds,t.samplingAndShutdownSeconds,t.diagnosticsSeconds,4L*draws*dim)
    }
  }
  private def batch(kind: String, variant: String, draws: Int, workers: Int, jobs: String, seed: Long): Vector[Output] = {
    val count = if (jobs == "single") 1 else 2
    if (jobs != "overlap") Vector.tabulate(count)(i => one(kind,variant,draws,workers,seed+100003L*i))
    else {
      val pool = Executors.newFixedThreadPool(2)
      try {
        val futures = Vector.tabulate(count)(i => pool.submit(new Callable[Output] {
          def call(): Output = one(kind,variant,draws,workers,seed+100003L*i)
        }))
        futures.map(_.get())
      } finally {
        pool.shutdownNow()
        require(pool.awaitTermination(30,TimeUnit.SECONDS),"Outer study jobs did not terminate")
      }
    }
  }
  private def gcCount: Long = ManagementFactory.getGarbageCollectorMXBeans.asScala.map(_.getCollectionCount).filter(_>=0).sum
  private def gcMillis: Long = ManagementFactory.getGarbageCollectorMXBeans.asScala.map(_.getCollectionTime).filter(_>=0).sum
  private def collect(): Boolean = {
    val before = gcCount; System.gc(); gcCount > before
  }
  private def profile(path: Path): Vector[Long] = {
    // Allocation weights are sampled, not exact bytes. Missing/inlined callback frames remain ambiguous.
    val weights = Array.fill[Long](6)(0L); var samples=0L; var truncated=0L; var lost=0L
    var compilation=0L; var deoptimization=0L
    val file = new RecordingFile(path)
    try while (file.hasMoreEvents) {
      val event=file.readEvent()
      event.getEventType.getName match {
        case "jdk.DataLoss" => lost=Math.addExact(lost,event.getLong("amount"))
        case "jdk.Compilation" => compilation+=1
        case "jdk.Deoptimization" => deoptimization+=1
        case "jdk.ObjectAllocationSample" =>
          samples+=1
          val stack=event.getStackTrace
          if (stack!=null && stack.isTruncated) truncated+=1
          val frames=Option(stack).toVector.flatMap(_.getFrames.asScala)
          val names=frames.map(f => (f.getMethod.getType.getName,f.getMethod.getName))
          val group = if (names.isEmpty) 5
            else if (names.exists(_._1.startsWith("com.cra.figaro.algorithm.sampling.parallel.McmcDiagnostics"))) 2
            else if (names.exists((c,m) => c.startsWith("com.cra.figaro.example.ResourceScalingStudy") &&
              (m.startsWith("referenceDensity") || m.startsWith("loopDensity")))) 0
            else if (names.exists(_._1.startsWith("com.cra.figaro.algorithm.sampling.VectorSliceSampler"))) 1
            else if (names.exists(_._1.startsWith("com.cra.figaro.algorithm.sampling"))) 3
            else 4
          weights(group)=Math.addExact(weights(group),event.getLong("weight"))
        case _ => ()
      }
    } finally file.close()
    require(lost==0,"Profile reported data loss")
    weights.toVector ++ Vector(samples,truncated,lost,compilation,deoptimization)
  }
  private def check(): Unit = {
    val rng=new java.util.Random(71193L)
    var count=0
    for (kind <- Vector("gaussian32","positive32","likelihood8"); i <- 0 until 200) {
      val n=if(kind=="likelihood8") 8 else 32
      val x=Vector.tabulate(n)(j => if(i==0) -0.0 else if(i==1) Double.MaxValue else if(i==2) java.lang.Double.MIN_VALUE
        else if(i==3) Double.NaN else if(i==4) Double.PositiveInfinity else rng.nextGaussian()*math.pow(10,i%20-10))
      require(java.lang.Double.doubleToLongBits(referenceDensity(kind,x))==java.lang.Double.doubleToLongBits(loopDensity(kind,x)),"Callback arithmetic mismatch")
      count+=1
    }
    for (kind <- Vector("gaussian32","positive32","likelihood8")) {
      val a=batch(kind,"reference",100,1,"single",420013L)
      val b=batch(kind,"loop",100,4,"single",420013L)
      require(a.map(_.fingerprint)==b.map(_.fingerprint),"Callback/worker dependent result")
      val serial=batch(kind,"reference",100,2,"serial",420013L)
      val overlap=batch(kind,"reference",100,2,"overlap",420013L)
      require(serial.map(_.fingerprint)==overlap.map(_.fingerprint),"Overlap-dependent result")
    }
    val graph=one("graphWide","reference",100,1,420013L).fingerprint
    for (w <- Vector(1,2,4)) require(graph==one("graphWide","reference",100,w,420013L).fingerprint,
      "Ordered-joint graph result changed across repetitions/workers")
    println(s"Resource study checks passed: $count callback cases, all workload/worker/overlap controls")
  }

  /** One fresh-JVM study case, not a performance mode for ordinary inference.
    * @param args `check`, or kind variant draws workers jobs round mode; optional new JFR path and ACL hook for profile;
    *             optional final `hold` keeps process alive until stdin newline so a parent can read its OS peak memory
    * @return Unit; quoted resource CSV, with two discarded full-work warm-ups and one measured result
    * @throws IllegalArgumentException on invalid configuration, changed work or existing profile output
    * @example `ResourceScalingStudy.main(Array("gaussian32","reference","4000","4","single","0","plain"))`
    */
  def main(args: Array[String]): Unit = {
    if(args.toVector==Vector("check")) { check(); return }
    val hold=args.lastOption.contains("hold")
    val a=if(hold) args.dropRight(1) else args
    require(a.length>=7 && a.length<=9,"Expected kind variant draws workers jobs round mode [new JFR] [ACL hook] [hold]")
    val kind=a(0); val variant=a(1); val draws=a(2).toInt; val workers=a(3).toInt; val jobs=a(4); val round=a(5).toInt; val mode=a(6)
    require(Set("gaussian32","positive32","likelihood8","graphWide")(kind) && Set("reference","loop")(variant))
    require(kind!="graphWide" || variant=="reference")
    require(draws>=4 && draws<=64000 && Set(1,2,4)(workers) && Set("single","serial","overlap")(jobs) && round>=0 && round<10)
    require(Set("plain","profile")(mode) && (if(mode=="profile") a.length>=8 else a.length==7))
    val seed=420013L+7919L*round
    val bean=ManagementFactory.getMemoryMXBean
    val pools=ManagementFactory.getMemoryPoolMXBeans.asScala.filter(_.getType==java.lang.management.MemoryType.HEAP)
    val os=ManagementFactory.getOperatingSystemMXBean.asInstanceOf[com.sun.management.OperatingSystemMXBean]
    def compiler: Long=Option(ManagementFactory.getCompilationMXBean).filter(_.isCompilationTimeMonitoringSupported).map(_.getTotalCompilationTime).getOrElse(-1L)
    def csv(xs: Any*): Unit=println(xs.map(x => "\""+x.toString.replace("\"","\"\"")+"\"").mkString(","))
    for(w <- -2 to -1) {
      val start=System.nanoTime(); val output=batch(kind,variant,draws,workers,jobs,seed)
      println(s"resourceWarmup,$w,${(System.nanoTime()-start)/1e9},${output.map(_.fingerprint).mkString(";")}")
    }
    val gcBeforeOk=collect(); val heapBefore=bean.getHeapMemoryUsage.getUsed
    pools.foreach(_.resetPeakUsage())
    var recording: Recording=null; var path: Path=null
    if(mode=="profile") {
      path=Path.of(a(7)).toAbsolutePath.normalize(); require(Files.isDirectory(path.getParent))
      Files.createFile(path)
      if(a.length==9) require(new ProcessBuilder("pwsh.exe","-NoProfile","-File",Path.of(a(8)).toAbsolutePath.toString,"-Paths",path.toString).inheritIO().start().waitFor()==0,"ACL hook failed")
      recording=new Recording()
      recording.enable("jdk.ObjectAllocationSample").withStackTrace().`with`("throttle","300/s")
      recording.enable("jdk.Compilation").withThreshold(Duration.ZERO)
      recording.enable("jdk.Deoptimization"); recording.enable("jdk.DataLoss")
      recording.start()
    }
    val gcStart=gcMillis; val compileStart=compiler; val cpuStart=os.getProcessCpuTime; val start=System.nanoTime()
    val output=try batch(kind,variant,draws,workers,jobs,seed) catch {
      case error: Throwable =>
        if(recording!=null) {
          try { recording.stop(); recording.dump(path) }
          catch { case cleanup: Throwable => if(cleanup ne error) error.addSuppressed(cleanup) }
          finally recording.close()
        }
        throw error
    }
    val elapsed=(System.nanoTime()-start)/1e9; val cpuEnd=os.getProcessCpuTime
    val compileEnd=compiler; val gcEnd=gcMillis
    retained=output
    val heapAtReturn=bean.getHeapMemoryUsage.getUsed
    val poolPeaks=pools.map(_.getPeakUsage.getUsed).sum
    if(recording!=null) { try { recording.stop(); recording.dump(path) } finally recording.close() }
    val gcAfterOk=collect(); val heapRetained=bean.getHeapMemoryUsage.getUsed
    val allocation=if(path==null) Vector.fill(11)(-1L) else profile(path)
    csv("resourceStudy","kind","variant","draws","workers","jobs","round","mode","seed","wallSeconds","cpuSeconds","constructionSeconds","samplingSeconds","diagnosticsSeconds","gcMillis","compilationMillis","heapBeforeBytes","heapAtReturnBytes","sumHeapPoolPeaksBytes","heapRetainedBytes","gcBeforeObserved","gcAfterObserved","storedValues","evaluations","fingerprints","callbackWeight","vectorWeight","diagnosticWeight","graphWeight","otherWeight","unknownWeight","allocationSamples","truncatedSamples","lostBytes","compilationEvents","deoptimizationEvents")
    def sum(f: Output=>Double): Double=output.map(f).sum
    csv((Vector[Any]("row",kind,variant,draws,workers,jobs,round,mode,seed,elapsed,
      if(cpuStart<0 || cpuEnd<0) Double.NaN else (cpuEnd-cpuStart)/1e9,sum(_.construction),sum(_.sampling),sum(_.diagnostics),gcEnd-gcStart,
      if(compileStart<0 || compileEnd<0) -1 else compileEnd-compileStart,heapBefore,heapAtReturn,poolPeaks,heapRetained,gcBeforeOk,gcAfterOk,
      output.map(_.stored).sum,if(kind=="graphWide") -1L else output.map(_.evaluations).sum,output.map(_.fingerprint).mkString(";")) ++ allocation)*)
    println("RESOURCE_READY"); System.out.flush()
    if(hold) require(System.in.read()>=0,"Parent released input unexpectedly")
    java.lang.ref.Reference.reachabilityFence(retained)
  }
}
