package com.cra.figaro.test.modernization

import com.cra.figaro.algorithm.filtering.{FixedBudgetParticleFilter as F,BoundedParticleFilter as B,WeightedParticleProposal as P,AuxiliaryParticleProposal as A}
import com.cra.figaro.library.atomic.continuous.{MultivariateGaussianDistribution as G}
import com.cra.figaro.util.SamplingRandom
import scala.io.Source
import scala.util.control.NonFatal

/** INF-17 frozen synthetic fixtures and all-outcome public-API study. Test sources only. */
object NonlinearProposalStudy {
  import NonlinearProposalPrototype.*
  val names=Vector("affine","mono","sharp","weak","symmetric","asymmetric","tail","curved")
  def modes(name:String)=Vector("bootstrap","cubature","ekf","aux","bad")++
    (if(name=="symmetric" || name=="asymmetric")Vector("manual")else Vector.empty)++
    (if(name=="affine")Vector("exact")else Vector.empty)
  final case class Spec(name:String){
    require(names.contains(name))
    val d=if(name=="curved")2 else 1
    val square=name=="symmetric" || name=="asymmetric"
    val noise=if(name=="sharp").15 else if(name=="weak")4.0 else .5
    val ys=if(name=="curved")Vector(1.0)else if(name=="tail")Vector(6.0)
      else if(square)Vector(1.0,1.2,.8,1.4,1.0,.7)else Vector(.4,1.2,-.3,.7,1.5,.2)
    def gaussian(parent:Option[V]):G=if(d==2)G(Vector(0.0,0.0),Vector(Vector(1.0,.3),Vector(.3,1.0)))
      else G(Vector(parent.fold(if(name=="asymmetric").8 else 0.0)(_.head*.7)),Vector(Vector(if(parent.isEmpty)1.21 else .64)))
    def h(x:V):V=Vector(if(d==2)x(0)+.3*x(1)*x(1)else if(name=="affine")x.head else if(square).25*x.head*x.head else x.head+.25*math.pow(x.head,3))
    def jac(x:V):M=Vector(if(d==2)Vector(1.0,.6*x(1))else Vector(if(name=="affine")1.0 else if(square).5*x.head else 1+.75*x.head*x.head))
  }
  final class Counts {
    var draws=0L;var likelihoods=0L;var targets=0L;var proposals=0L;var components=0L
    var guides=0L;var h=0L;var jac=0L;var gaussians=0L;var cholesky=0L;var solves=0L
    var look=0L;var ancestors=0L;var nanos=0L
  }
  val columns="name mode n seed t status logEvidence mean second positive ess draws likelihoods targets proposals components guides h jac gaussians cholesky solves look ancestors nanos error".split(" ").toVector
  def run(name:String,mode:String,n:Int,seed:Int,emit:Boolean):Unit={
    val spec=Spec(name);val c=new Counts;var terminal="";var t=0
    def gaussian(parent:Option[V]):G={c.gaussians+=1;spec.gaussian(parent)}
    def target(parent:Option[V]):P.Law[V]={val p=P.multivariateGaussian(gaussian(parent))
      P.Law(p.sample,x=>{c.targets+=1;c.components+=1;p.logDensity(x)})}
    def bootstrap(parent:Option[V]):V={c.draws+=1;target(parent).sample()}
    val f=F(B.Model(()=>bootstrap(None),(x:V)=>bootstrap(Some(x))),F.Config(n,F.Policy.WhenEssBelow(.5),F.Method.Stratified,seed))
    def guide(parent:Option[V]):(P.Law[V],Double)={
      c.guides+=1;val prior=gaussian(parent);val y=spec.ys(t)
      val (law,score,parts)=if(mode=="manual"){
        val result=ContinuousFilterBenchmark.guidance(ContinuousFilterBenchmark.Spec("persistent-modes"),
          Vector(ContinuousFilterBenchmark.Component(prior.mean.head,math.sqrt(prior.covariance.head.head),1)),y)
        c.gaussians+=5;c.components+=2 // two predictive scalar densities in the retained recipe
        (P.Law[V](()=>Vector(result._1.sample()),x=>result._1.logDensity(x.head)),result._2,3)
      }else if(mode=="exact"){
        val result=P.linearGaussianObservation(prior,Vector(y),Vector(Vector(1.0)),Vector(Vector(.25)))
        c.gaussians+=3;c.cholesky+=1;c.solves+=spec.d
        (P.defensive(P.multivariateGaussian(prior),result.proposal,.1),result.logPredictive,2)
      }else{
        val method=if(mode=="ekf" || mode=="bad")Method.Jacobian else Method.Cubature
        val result=prepare(prior,Vector(y),spec.h,Vector(Vector(spec.noise*spec.noise)),method,.1,Budget(8,1),
          Some(x=>spec.jac(x).map(_.map(v=>if(mode=="bad") -v else v))))
        c.h+=result.work.hCalls;c.jac+=result.work.jacobianCalls
        c.gaussians+=result.work.gaussianConstructions;c.cholesky+=result.work.explicitCholesky;c.solves+=result.work.solves
        (result.proposal,result.approximateLogPredictive,2)
      }
      // Explicit density categories include the defensive component, not only q's top-level call.
      (P.Law(()=>{c.draws+=1;law.sample()},x=>{c.proposals+=1;c.components+=parts;law.logDensity(x)}),score)
    }
    try spec.ys.indices.foreach{index=>
      t=index;val start=System.nanoTime();var status="ok";var answer:Option[F.Update[V]]=None
      if(terminal.nonEmpty)status="unavailable"
      else try{
        val likelihood=(x:V)=>{c.likelihoods+=1;c.h+=1
          val z=(spec.ys(t)-spec.h(x).head)/spec.noise
          -.5*z*z-math.log(spec.noise)-.5*math.log(2*math.Pi)}
        if(mode=="bootstrap" || mode=="big")answer=Some(if(t==0)f.start(likelihood)else f.advance(likelihood))
        else{
          // Only initial laws are needed at t=0; do not charge an unused initial guide later.
          val unused=P.Law[V](()=>throw new AssertionError("unused initial"),_=>throw new AssertionError("unused initial"))
          val p0=if(t==0)target(None)else unused
          val q0=if(t==0)guide(None)._1 else unused
          val k=P.Kernel(p0,(x:V)=>target(Some(x)),q0,(x:V)=>guide(Some(x))._1)
          answer=Some(if(t==0)f.start(likelihood,k)else if(mode=="aux")
            f.advanceAuxiliary(likelihood,A.LookAhead[V](x=>{c.look+=1;guide(Some(x))._2},.1),Some(k))
            else f.advance(likelihood,k))
        }
        c.ancestors+=answer.get.report.ancestorSelections
        require(c.draws==(t+1L)*n && c.likelihoods==(t+1L)*n,"Fixed production budget violated")
      }catch{case e:InterruptedException=>throw e;case NonFatal(e)=>status="error";terminal=e.getClass.getSimpleName+":"+e.getMessage}
      def moment(g:V=>Double)=answer.map(_.expectation(g)).getOrElse(Double.NaN)
      val values=Vector(moment(_.head),moment(x=>x.head*x.head),moment(x=>if(x.head>0)1.0 else 0.0))
      c.nanos+=System.nanoTime()-start
      val row=Vector[Any](name,mode,n,seed,t,status,answer.map(_.logEvidence).getOrElse(Double.NaN))++values++
        Vector[Any](answer.flatMap(_.report.health.ess).getOrElse(Double.NaN),c.draws,c.likelihoods,c.targets,c.proposals,
          c.components,c.guides,c.h,c.jac,c.gaussians,c.cholesky,c.solves,c.look,c.ancestors,c.nanos,terminal.replace('\t',' ').replace('\n',' '))
      if(emit)println("NP_ROW\t"+row.mkString("\t"))
    }finally f.close()
  }
  def main(args:Array[String]):Unit={
    require(args.length>=1 && Set("development","confirmation").contains(args(0)))
    val confirmation=args(0)=="confirmation"
    val budgets=if(confirmation){require(args.length==2);val src=Source.fromFile(args(1))
      try src.getLines().map(l=>{val a=l.split("\t");a(0)->a(1).toInt}).toMap finally src.close()}else Map.empty[String,Int]
    if(confirmation)require(budgets.keySet==names.toSet && budgets.values.forall(n=>n>=64 && n<=4096))
    println("NP_PROTOCOL_V1 "+args(0)+" rng="+SamplingRandom.defaultAlgorithm)
    println("NP_COLUMNS\t"+columns.mkString("\t"))
    for(name<-names;mode<-modes(name)++(if(confirmation)Vector("big")else Vector.empty);seed<- -402 to -401)
      run(name,mode,if(mode=="big")budgets(name)else 64,seed,false)
    val seeds=if(confirmation)90001 to 90128 else 89001 to 89016
    for(seed<-seeds;name<-names){val ms=modes(name)++(if(confirmation)Vector("big")else Vector.empty)
      for(j<-ms.indices){val mode=ms((j+seed)%ms.size);run(name,mode,if(mode=="big")budgets(name)else 64,seed,true)}}
  }
}
