package com.cra.figaro.test.modernization
import com.cra.figaro.algorithm.sampling.{StaticGraphImportance as S,GraphProposalImportance as P,VectorImportance as V,RareEventImportance as R,InferenceHealth as H}
import com.cra.figaro.library.atomic.continuous.{MultivariateGaussianDistribution as G,Normal}
import com.cra.figaro.util.{RandomStreams,SamplingRandom}
import com.cra.figaro.language.*

/** End-to-end supplied-proposal comparison; includes model/proposal setup, scoring and cleanup. */
object StaticProposalStudy {
  def main(args: Array[String]): Unit = {
    val repeats=if(args.nonEmpty) args(0).toInt else 20; require(repeats>=1 && repeats<=100)
    def trial(kind: Int,method: Int,seed: Long,emit: Boolean): Unit = {
      val start=System.nanoTime(); val n=6000; val depth=if(kind==2) 32 else 0; val sd=if(kind==1) .05 else 1.0
      val a=math.pow(.99,depth); val truth=a/(a*a+sd*sd); val variance=sd*sd/(a*a+sd*sd)
      val base=V.Gaussian(G(Vector(0.0),Vector(Vector(1.0))))
      val q=R.defensive(base,V.Gaussian(G(Vector(truth),Vector(Vector(variance))))).proposal
      val health=if(method==0) {
        val streams=RandomStreams.allocate(seed,16,SamplingRandom.defaultAlgorithm)
        val results=streams.indices.map { i =>
          val count=(n.toLong*(i+1)/16-n.toLong*i/16).toInt
          P.run(P.Config(draws=count,maxAttempts=count,seed=streams(i).seed),q,base.logDensity) { (u,root) =>
            val x=root.map(_.head)(using "",u); var y=x
            for(_ <- 0 until depth) y=y.map(_*.99)(using "",u)
            Normal(y,sd*sd)(using "",u).observe(1.0); x
          }
        }
        H.importance(results.flatMap(_.logWeights),true,Some(results.flatMap(_.values)))
      } else {
        val nodes=scala.collection.mutable.ArrayBuffer[S.Node](S.Node.Constant(0),S.Node.Constant(1),S.Node.Normal(0,1),S.Node.Constant(sd),S.Node.Constant(.99))
        var last=2; for(_ <- 0 until depth) { nodes+=S.Node.Multiply(last,4); last=nodes.size-1 }
        nodes+=S.Node.Normal(last,3); val model=S.compile(nodes.toVector)
        val c=S.Config(draws=n,parallelism=if(method==3) 4 else 1,seed=seed)
        (if(method==1) S.run(model,Vector(2),Map((nodes.size-1) -> 1.0),c)
          else S.runWithProposal(model,Vector(2),q,Vector(2),Map((nodes.size-1) -> 1.0),c)).health.head
      }
      val seconds=(System.nanoTime()-start)/1e9
      if(emit) println(s"SP_ROW,$kind,$method,$seed,$n,${health.diagnostics.mean.get},$truth,${health.diagnostics.ess.get},$seconds")
    }
    for(i <- 0 until 2; m <- 0 until 4) trial(0,m,i,false)
    println("SP_HEADER,model,method,seed,draws,estimate,truth,ess,seconds")
    for(kind <- 0 until 3; i <- 0 until repeats; j <- 0 until 4) trial(kind,(i+j)%4,98000L+i,true)
  }
}
