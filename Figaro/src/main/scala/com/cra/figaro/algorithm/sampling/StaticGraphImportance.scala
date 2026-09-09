package com.cra.figaro.algorithm.sampling

import com.cra.figaro.util.{RandomStreams,SamplingRandom}
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicLong
import scala.jdk.CollectionConverters.*

/** Callback-free, static scalar DAG likelihood weighting. Not a compiler for arbitrary Elements.
  * Immutable definitions are shared; arrays, RNGs and evidence snapshots belong to one invocation.
  */
object StaticGraphImportance {
  enum Kind { case Real, Boolean }
  /** References are zero-based positions of EARLIER nodes. Choose selects already evaluated values.
    * Normal's second parameter is a STANDARD DEVIATION, not variance.
    */
  enum Node {
    case Constant(value: Double)
    case Bernoulli(probability: Int)
    case Normal(mean: Int,standardDeviation: Int)
    case Add(left: Int,right: Int)
    case Multiply(left: Int,right: Int)
    case Choose(condition: Int,whenTrue: Int,whenFalse: Int)
    case Indicator(boolean: Int)
    case Exp(value: Int)
    case Sigmoid(value: Int)
  }
  /** Opaque compiled tables; there is no public mutable execution-state handle. */
  final class Model private[StaticGraphImportance](val nodes: Vector[Node],val kinds: Vector[Kind],
    private[StaticGraphImportance] val ops: Array[Int],private[StaticGraphImportance] val first: Array[Int],
    private[StaticGraphImportance] val second: Array[Int],private[StaticGraphImportance] val third: Array[Int],
    private[StaticGraphImportance] val constants: Array[Double]) {
    val nodeCount: Int=nodes.size
  }
  /** Compile validated nodes to immutable, privately owned primitive tables.
    * @param nodes 1..4096 nodes in dependency order; forward/cyclic references are rejected
    * @return shareable model; unsupported node kinds cannot enter this sealed vocabulary
    * @example `compile(Vector(Node.Constant(0),Node.Constant(1),Node.Normal(0,1)))`
    */
  def compile(nodes: Vector[Node]): Model = {
    ParetoTail.interrupted(); require(nodes!=null && nodes.nonEmpty && nodes.size<=4096 && nodes.forall(_!=null))
    val n=nodes.size; val ops=new Array[Int](n); val first=new Array[Int](n)
    val second=new Array[Int](n); val third=new Array[Int](n); val constants=new Array[Double](n)
    val kinds=Array.fill(n)(Kind.Real)
    for(i <- nodes.indices) {
      ParetoTail.interrupted()
      def ref(j: Int,kind: Kind): Unit=require(j>=0 && j<i && kinds(j)==kind,s"Invalid or mistyped dependency $j at node $i")
      def real(j: Int): Unit=ref(j,Kind.Real)
      nodes(i) match {
        case Node.Constant(v) => require(v.isFinite); ops(i)=0; constants(i)=v
        case Node.Bernoulli(p) =>
          real(p); nodes(p) match { case Node.Constant(v) => require(v>=0 && v<=1); case _ => () }
          ops(i)=1; first(i)=p; kinds(i)=Kind.Boolean
        case Node.Normal(m,s) =>
          real(m); real(s); nodes(s) match { case Node.Constant(v) => require(v>0); case _ => () }
          ops(i)=2; first(i)=m; second(i)=s
        case Node.Add(a,b) => real(a); real(b); ops(i)=3; first(i)=a; second(i)=b
        case Node.Multiply(a,b) => real(a); real(b); ops(i)=4; first(i)=a; second(i)=b
        case Node.Choose(c,a,b) => ref(c,Kind.Boolean); real(a); real(b); ops(i)=5; first(i)=c; second(i)=a; third(i)=b
        case Node.Indicator(b) => ref(b,Kind.Boolean); ops(i)=6; first(i)=b
        case Node.Exp(v) => real(v); ops(i)=7; first(i)=v
        case Node.Sigmoid(v) => real(v); ops(i)=8; first(i)=v
      }
    }
    new Model(nodes,kinds.toVector,ops,first,second,third,constants)
  }
  /** @param draws total independent importance attempts, 1..1000000; no rejection retries
    * @param batches fixed logical RNG streams, 1..256 (clamped to draws), independent of worker count
    * @param parallelism maximum workers per invocation, 1..32
    * @param seed root stream seed
    * @param maxStoredValues cap for draws*(queries+1), at most 10000000
    * @param maxNodeEvaluations cap for draws*nodes, at most 1000000000
    * @param randomAlgorithm named scientific engine
    * @param randomStreams versioned stream allocation, validated before launching workers
    */
  final case class Config(draws: Int=10000,batches: Int=16,parallelism: Int=4,seed: Long=43,
    maxStoredValues: Long=2000000,maxNodeEvaluations: Long=100000000,
    randomAlgorithm: SamplingRandom.Algorithm=SamplingRandom.defaultAlgorithm,
    randomStreams: RandomStreams.Config=RandomStreams.Config()) {
    require(draws>=1 && draws<=1000000 && batches>=1 && batches<=256 && parallelism>=1 && parallelism<=32)
    require(maxStoredValues>=1 && maxStoredValues<=10000000 && maxNodeEvaluations>=1 && maxNodeEvaluations<=1000000000)
    require(randomAlgorithm!=null && randomStreams!=null); randomStreams.validate(randomAlgorithm)
  }
  /** Detached raw weights and query columns; Boolean queries are represented by 0/1.
    * No posterior precision/mode-coverage certificate is implied by the health reports.
    */
  final case class Result(logWeights: Vector[Double],values: Vector[Vector[Double]],queries: Vector[Int],
    health: Vector[InferenceHealth.ImportanceReport],observations: Map[Int,Double],nodeEvaluations: Long,
    streams: Vector[RandomStreams.Descriptor],config: Config)
  private val workerIds=new AtomicLong(0)

  /** Evaluate a fresh likelihood-weighting run over a shared immutable definition.
    * @param model compiled static model, never an existing mutable Element graph
    * @param queries 1..128 distinct node IDs, in result-column order
    * @param observations immutable map of Bernoulli (0/1) or Normal observations; deterministic evidence is unsupported
    * @param config work limits and private stream allocation
    * @return detached results, including all zero-weight attempts; failures/cancellation never return partial success
    * @example `run(model,Vector(2),Map(3 -> 1.0),Config(draws=10000))`
    */
  def run(model: Model,queries: Vector[Int],observations: Map[Int,Double]=Map.empty,config: Config=Config()): Result = {
    ParetoTail.interrupted(); require(model!=null && queries!=null && observations!=null && config!=null)
    require(queries.nonEmpty && queries.size<=128 && queries.distinct.size==queries.size && queries.forall(i => i>=0 && i<model.nodeCount))
    require(config.draws.toLong*(queries.size+1)<=config.maxStoredValues,"Stored-value budget exceeded")
    require(config.draws.toLong*model.nodeCount<=config.maxNodeEvaluations,"Node-evaluation budget exceeded")
    val observed=Array.fill(model.nodeCount)(false); val evidence=new Array[Double](model.nodeCount)
    observations.foreach { (i,x) =>
      require(i>=0 && i<model.nodeCount && x.isFinite && (model.ops(i)==1 || model.ops(i)==2),"Only stochastic-node observations supported")
      if(model.ops(i)==1) require(x==0 || x==1,"Bernoulli observations must be 0/1")
      observed(i)=true; evidence(i)=x
    }
    val count=math.min(config.batches,config.draws)
    val streams=RandomStreams.allocate(config.seed,count,config.randomAlgorithm,config.randomStreams)
    val logs=new Array[Double](config.draws)
    val values=Array.fill(queries.size)(new Array[Double](config.draws))
    def batch(index: Int): Unit = {
      val state=new Array[Double](model.nodeCount)
      val rng=streams(index).random
      val from=(config.draws.toLong*index/count).toInt
      val until=(config.draws.toLong*(index+1)/count).toInt
      var draw=from
      while(draw<until) {
        ParetoTail.interrupted(); var logWeight=0.0; var impossible=false; var i=0
        while(i<model.nodeCount) {
          if((i & 127)==0) ParetoTail.interrupted()
          val a=model.first(i); val b=model.second(i)
          val x=model.ops(i) match {
            case 0 => model.constants(i)
            case 1 =>
              val p=state(a); require(p>=0 && p<=1,"Dynamic probability outside [0,1]")
              val x=if(observed(i)) evidence(i) else if(rng.nextDouble()<p) 1.0 else 0.0
              if(observed(i)) {
                val lp=if(x==1) math.log(p) else math.log1p(-p)
                if(lp==Double.NegativeInfinity) impossible=true else logWeight+=lp
              }
              x
            case 2 =>
              val m=state(a); val sd=state(b); require(sd>0 && sd.isFinite,"Dynamic standard deviation must be positive finite")
              if(observed(i)) {
                val z=(evidence(i)-m)/sd; val logp= -.5*math.log(2*math.Pi)-math.log(sd)-.5*z*z
                if(!logp.isFinite) throw new ArithmeticException("Normal observation log density outside numeric range")
                logWeight+=logp; evidence(i)
              } else m+sd*rng.nextGaussian()
            case 3 => state(a)+state(b)
            case 4 => state(a)*state(b)
            case 5 => if(state(a)==1) state(b) else state(model.third(i))
            case 6 => state(a)
            case 7 => math.exp(state(a))
            case 8 => val x=state(a); if(x>=0) 1/(1+math.exp(-x)) else { val e=math.exp(x); e/(1+e) }
            case _ => throw new IllegalStateException("Invalid compiled opcode")
          }
          if(!x.isFinite) throw new ArithmeticException(s"Nonfinite value at static node $i")
          state(i)=x; i+=1
        }
        if(!logWeight.isFinite) throw new ArithmeticException("Invalid accumulated log likelihood")
        logs(draw)=if(impossible) Double.NegativeInfinity else logWeight
        var q=0; while(q<queries.size) { values(q)(draw)=state(queries(q)); q+=1 }
        draw+=1
      }
    }
    if(config.parallelism==1 || count==1) (0 until count).foreach(batch)
    else {
      val threads=new ConcurrentLinkedQueue[Thread]()
      val pool=Executors.newFixedThreadPool(math.min(count,config.parallelism),new ThreadFactory {
        def newThread(r: Runnable): Thread = {
          val t=new Thread(r,s"figaro-static-${workerIds.incrementAndGet()}"); t.setDaemon(true); threads.add(t); t
        }
      })
      val completion=new ExecutorCompletionService[Unit](pool)
      val futures=scala.collection.mutable.ArrayBuffer.empty[Future[Unit]]
      var failure: Throwable=null
      try {
        (0 until count).foreach { i =>
          ParetoTail.interrupted(); futures+=completion.submit(new Callable[Unit] { def call(): Unit=batch(i) })
        }
        (0 until count).foreach { _ =>
          try completion.take().get() catch { case e: ExecutionException => throw e.getCause }
        }
      } catch { case e: Throwable => failure=e; throw e }
      finally {
        var interrupted=Thread.interrupted() || failure.isInstanceOf[InterruptedException]
        futures.foreach(_.cancel(true)); pool.shutdownNow()
        try {
          val deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(30)
          while(!pool.isTerminated && System.nanoTime()<deadline) {
            try pool.awaitTermination(math.max(1,deadline-System.nanoTime()),TimeUnit.NANOSECONDS)
            catch { case _: InterruptedException => interrupted=true }
          }
          if(!pool.isTerminated) throw new IllegalStateException("Static workers failed to terminate")
          threads.asScala.foreach { t =>
            while(t.isAlive && System.nanoTime()<deadline) {
              try TimeUnit.NANOSECONDS.timedJoin(t,math.max(1,deadline-System.nanoTime()))
              catch { case _: InterruptedException => interrupted=true }
            }
            if(t.isAlive) throw new IllegalStateException("Static worker still alive after shutdown")
          }
        } catch { case cleanup: Throwable =>
          interrupted ||= cleanup.isInstanceOf[InterruptedException]
          if(failure!=null) failure.addSuppressed(cleanup) else throw cleanup
        } finally { if(interrupted) Thread.currentThread().interrupt() }
      }
    }
    ParetoTail.interrupted()
    val weights=logs.toVector; val columns=values.iterator.map(_.toVector).toVector
    val health=columns.map(v => InferenceHealth.importance(weights,true,Some(v)))
    Result(weights,columns,queries,health,observations,config.draws.toLong*model.nodeCount,
      streams.map(_.descriptor).toVector,config)
  }
}
