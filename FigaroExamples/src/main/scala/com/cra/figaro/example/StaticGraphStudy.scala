package com.cra.figaro.example

import com.cra.figaro.algorithm.sampling.{StaticGraphImportance as S,GraphProposalImportance as P,VectorImportance as V,InferenceHealth as H}
import com.cra.figaro.library.atomic.continuous.{MultivariateGaussianDistribution as G,Normal}
import com.cra.figaro.language.*
import com.cra.figaro.util.{RandomStreams,SamplingRandom}

/** Initialization-inclusive comparisons against the owned graph bridge; not universal speed assertions. */
object StaticGraphStudy {
  private def trial(model: Int,method: Int,draws: Int,seed: Long): (Double,Double,Double,Long,Long) = {
    val heap=java.lang.management.ManagementFactory.getMemoryMXBean
    val before=heap.getHeapMemoryUsage.getUsed
    val start=System.nanoTime()
    val sd=if(model==1) .1 else 1.0
    val depth=if(model==2) 64 else 0
    val c=S.Config(draws=draws,batches=16,parallelism=if(method==0) 1 else Vector(1,1,2,4)(method),seed=seed)
    val h=if(method==0) {
      val law=G(Vector(0.0),Vector(Vector(1.0)))
      val streams=RandomStreams.allocate(seed,16,SamplingRandom.defaultAlgorithm)
      val results=streams.indices.map { i =>
        val n=(draws.toLong*(i+1)/16-draws.toLong*i/16).toInt
        P.run(P.Config(draws=n,maxAttempts=n,seed=streams(i).seed,maxElementVisits=10000000),V.Gaussian(law),law.logDensity) { (u,root) =>
          val x=root.map(_.head)(using "",u)
          var transformed=x
          for(_ <- 0 until depth) transformed=transformed.map(v => .9999*v+.0001)(using "",u)
          Normal(transformed,sd*sd)(using "",u).observe(1.0)
          x
        }
      }
      H.importance(results.flatMap(_.logWeights),true,Some(results.flatMap(_.values)))
    } else {
      val nodes=scala.collection.mutable.ArrayBuffer[S.Node](S.Node.Constant(0),S.Node.Constant(1),S.Node.Normal(0,1),S.Node.Constant(sd),S.Node.Constant(.9999),S.Node.Constant(.0001))
      var mean=2
      for(_ <- 0 until depth) {
        nodes+=S.Node.Multiply(mean,4); mean=nodes.size-1
        nodes+=S.Node.Add(mean,5); mean=nodes.size-1
      }
      nodes+=S.Node.Normal(mean,3)
      S.run(S.compile(nodes.toVector),Vector(2),Map((nodes.size-1) -> 1.0),c).health.head
    }
    val elapsed=System.nanoTime()-start
    val after=heap.getHeapMemoryUsage.getUsed
    val a=math.pow(.9999,depth); val truth=a*a/(sd*sd+a*a)
    (h.diagnostics.mean.get,truth,h.diagnostics.ess.get,elapsed,after-before)
  }
  def main(args: Array[String]): Unit = {
    if(args.headOption.contains("profile")) {
      require(args.length==3)
      val method=args(1).toInt; require(method>=0 && method<=3)
      val path=java.nio.file.Path.of(args(2)); require(!java.nio.file.Files.exists(path),"Do not overwrite an existing profile")
      val recording=new jdk.jfr.Recording()
      try {
        recording.enable("jdk.ObjectAllocationInNewTLAB").withoutStackTrace()
        recording.enable("jdk.ObjectAllocationOutsideTLAB").withoutStackTrace()
        recording.start(); val result=trial(2,method,100000,91); recording.stop(); recording.dump(path)
        println(s"STATIC_PROFILE method=$method mean=${result._1} truth=${result._2}")
      } finally recording.close()
    } else {
      require(args.length<=1)
      val repeats=if(args.isEmpty) 20 else args(0).toInt; require(repeats>=1 && repeats<=100)
      trial(0,0,1000,7); trial(0,3,1000,7)
      println("STATIC_STUDY,seed,model,draws,method,mean,truth,ess,nanos,heap_delta")
      for(seed <- 0 until repeats; model <- 0 until 3; draws <- Vector(2000,20000);
        method <- (if(seed%2==0) Vector(0,1,2,3) else Vector(3,2,1,0))) {
        val (mean,truth,ess,nanos,heap)=trial(model,method,draws,620001L+7919L*seed)
        println(s"STATIC_STUDY,$seed,$model,$draws,$method,$mean,$truth,$ess,$nanos,$heap")
      }
    }
  }
}
