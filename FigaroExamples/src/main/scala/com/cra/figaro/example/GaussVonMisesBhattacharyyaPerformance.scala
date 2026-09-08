package com.cra.figaro.example

import com.cra.figaro.library.atomic.continuous.*
import java.util.concurrent.CancellationException

/** Bounded matched-accuracy study, not a general benchmark or an inference algorithm. */
object GaussVonMisesBhattacharyyaPerformance {
  @volatile private var sink = 0.0
  private val tolerance = 1e-6
  private val orders = Vector(3,5,7,9,13,17,25,32)
  private val maxNodes = 300000
  private val peak = VonMisesDistribution(0,4).logDensity(0)
  private case class Fixture(name: String, n: Int, curved: Boolean, exact: Double) {
    private val zero = Vector.fill(n,n)(0.0)
    private val identity = Vector.tabulate(n,n)((i,j) => if (i == j) 1.0 else 0.0)
    private def kernel(b: Double, alpha: Double, gamma: Vector[Vector[Double]], k: Double) =
      GaussVonMisesDistribution(Vector.fill(n)(0.0),identity,alpha,Vector.fill(n)(b),gamma,k)
    val p = kernel(.2,if (curved) .3 else 0,
      if (curved) Vector(Vector(.1,.04),Vector(.04,-.05)) else zero,4)
    val q = kernel(-.2,if (curved) -.1 else 0,zero,4)
    val base = kernel(0,0,zero,0)
    def affinity(point: LinearAngular): Double = {
      val x = point.linear
      val delta = .4*x.sum + (if (curved) .4+.05*x(0)*x(0)+.04*x(0)*x(1)-.025*x(1)*x(1) else 0)
      angular(delta)
    }
  }
  private case class Method(name: String, calls: Int, evaluate: () => Double)
  private def interrupted(): Unit =
    if (Thread.currentThread().isInterrupted) throw new CancellationException("GVM performance study interrupted")
  private def angular(delta: Double): Double = {
    val radius = 4*math.abs(math.cos(delta/2))
    math.exp(radius-4+peak-VonMisesDistribution(0,radius).logDensity(0))
  }
  private def checked(value: Double, exact: Double): Double = {
    require(value.isFinite && math.abs(value-exact) <= tolerance,"unmatched distance accuracy")
    value
  }
  private def select(name: String, base: GaussVonMisesDistribution,
    callback: LinearAngular => Double, exact: Double): GaussVonMisesTensorQuadrature = {
    // Choose by accuracy, before measuring. Never select a method by its timing.
    val eligible = orders.filter(order => 2*math.pow(order,base.dimension) <= maxNodes).iterator
    while (eligible.hasNext) {
      val order = eligible.next()
      interrupted()
      val rule = base.tensorQuadrature(order,2,maxNodes)
      val error = math.abs(-math.log(rule.expectation(callback))-exact)
      println(s"GVM_ACCURACY case=$name order=$order calls=${rule.nodeCount} error=$error accepted=${error <= tolerance}")
      if (error <= tolerance) return rule
    }
    throw new IllegalStateException(s"No matched-accuracy rule for $name within the declared ladder and budget")
  }
  private def batch(method: Method, count: Int): Double = {
    val start = System.nanoTime()
    var i = 0
    while (i < count) { interrupted(); sink = method.evaluate(); i += 1 }
    (System.nanoTime()-start).toDouble
  }
  private def measure(name: String, methods: Vector[Method], rounds: Int): Unit = {
    // Interleaved warm-up, then bounded per-method batch calibration to at least 20 ms.
    for (_ <- 0 until 20; method <- methods) batch(method,1)
    val counts = methods.map { method =>
      var count = 1
      var elapsed = batch(method,count)
      while (elapsed < 20000000 && count < 4096) { count *= 2; elapsed = batch(method,count) }
      count
    }
    for (round <- 0 until rounds; offset <- methods.indices) {
      val index = (offset+round) % methods.size
      val method = methods(index)
      val elapsed = batch(method,counts(index))
      println(s"GVM_TIMING case=$name method=${method.name} round=$round batch=${counts(index)} nsPerCall=${elapsed/counts(index)}")
    }
  }

  /** Check accuracy or time four fixed fixtures at absolute distance error at most 1e-6.
    * @param args `Array("check")` for untimed assertions, or `Array("measure", "7")` for 3 to 31 rounds (default 7)
    * @return Unit; prints accuracy/work records and optional per-round nanoseconds; throws on invalid arguments or failed accuracy
    * @example `GaussVonMisesBhattacharyyaPerformance.main(Array("check"))`
    */
  def main(args: Array[String]): Unit = {
    require(args != null && args.nonEmpty && Set("check","measure")(args(0)),"use check or measure [rounds]")
    val timed = args(0) == "measure"
    require(args.length <= (if (timed) 2 else 1),"unexpected arguments")
    val rounds = if (args.length == 2) args(1).toInt else 7
    require(rounds >= 3 && rounds <= 31,"rounds must be in [3,31]")
    println(s"GVM_ENV pid=${ProcessHandle.current().pid()} java=${System.getProperty("java.version")} vm=${System.getProperty("java.vm.name")} os=${System.getProperty("os.name")} arch=${System.getProperty("os.arch")} processors=${Runtime.getRuntime.availableProcessors} maxHeap=${Runtime.getRuntime.maxMemory} tolerance=$tolerance rounds=$rounds timed=$timed")
    val fixtures = Vector(
      Fixture("linear-1d",1,false,.06387735648997029028485409595318461855683),
      Fixture("linear-2d",2,false,.1192948144207666012358738093531004731091),
      Fixture("curved-2d",2,true,.1727350009432398629118580202897124128865),
      Fixture("linear-6d",6,false,.2886679589491771038675967423398820798992))
    val scalarBase = GaussVonMisesDistribution(Vector(0.0),Vector(Vector(1.0)),0,Vector(0.0),Vector(Vector(0.0)),0)
    var methodCount = 0
    for (f <- fixtures) {
      for (z <- Vector(-2.0,0.0,.7,2.0)) {
        val point = LinearAngular(Vector.tabulate(f.n)(i => z*(i+1)/f.n),0)
        val reference = angular(f.p.conditionalLocation(point.linear)-f.q.conditionalLocation(point.linear))
        require(math.abs(f.affinity(point)-reference) < 1e-14,"specialized callback disagrees with kernel parameterization")
      }
      val first = GaussVonMisesBhattacharyya.compare(f.p,f.q,tolerance)
      require(first.resolved,"guarded method must resolve these fixtures")
      checked(first.distance.get,f.exact)
      println(s"GVM_SERIES case=${f.name} harmonics=${first.harmonicsUsed} error=${math.abs(first.distance.get-f.exact)}")
      val rule = select(f.name,f.base,f.affinity,f.exact)
      val generic = Vector(
        Method("guarded",first.harmonicsUsed,() => {
          val result = GaussVonMisesBhattacharyya.compare(f.p,f.q,tolerance)
          require(result.resolved,"guarded method stopped resolving")
          result.distance.get
        }),
        Method("tensor-build-evaluate",rule.nodeCount,() =>
          -math.log(f.base.tensorQuadrature(rule.gaussianOrder,2,maxNodes).expectation(f.affinity))),
        Method("tensor-reuse",rule.nodeCount,() => -math.log(rule.expectation(f.affinity))))
      // Only the linear fixtures reduce exactly: sum(X_i) = sqrt(n)*Z in law.
      val reduced = if (f.curved) Vector.empty else {
        val callback: LinearAngular => Double = point => angular(.4*math.sqrt(f.n)*point.linear(0))
        val small = select(f.name+"-reduced",scalarBase,callback,f.exact)
        Vector(Method("reduced-build-evaluate",small.nodeCount,() =>
          -math.log(scalarBase.tensorQuadrature(small.gaussianOrder,2,maxNodes).expectation(callback))),
          Method("reduced-reuse",small.nodeCount,() => -math.log(small.expectation(callback))))
      }
      val methods = generic ++ reduced
      methods.foreach { method =>
        checked(method.evaluate(),f.exact)
        println(s"GVM_METHOD case=${f.name} method=${method.name} work=${method.calls}")
      }
      methodCount += methods.size
      if (timed) measure(f.name,methods,rounds)
    }
    require(methodCount == 18,"incomplete study matrix")
    println(s"GVM_COMPLETE fixtures=${fixtures.size} methods=$methodCount rounds=${if (timed) rounds else 0} sinkFinite=${sink.isFinite}")
  }
}
