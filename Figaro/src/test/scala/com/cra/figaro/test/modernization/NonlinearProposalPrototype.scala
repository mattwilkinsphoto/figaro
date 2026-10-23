package com.cra.figaro.test.modernization

import com.cra.figaro.algorithm.filtering.{WeightedParticleProposal as P}
import com.cra.figaro.library.atomic.continuous.{MultivariateGaussianDistribution as G}
import org.apache.commons.math3.linear.{Array2DRowRealMatrix,ArrayRealVector,CholeskyDecomposition}

/** INF-17 frozen v1. Test-only construction; no filtering, adaptation or hidden fallback. */
object NonlinearProposalPrototype {
  type V = Vector[Double]
  type M = Vector[V]
  enum Method { case Cubature, Jacobian }
  final case class Budget(hCalls:Int,jacobianCalls:Int) {
    require(hCalls>=0 && jacobianCalls>=0)
  }
  final case class Work(hCalls:Int,jacobianCalls:Int,gaussianConstructions:Int,
    explicitCholesky:Int,solves:Int)
  final case class Prepared(gaussian:G,proposal:P.Law[V],approximateLogPredictive:Double,work:Work)
  private def check():Unit=if(Thread.currentThread().isInterrupted)throw new InterruptedException("Proposal preparation interrupted")
  private def matrix(a:M)=new Array2DRowRealMatrix(a.map(_.toArray).toArray,false)
  private def finite(v:V,n:Int):Unit=require(v!=null && v.size==n && v.forall(_.isFinite),"Invalid callback vector")
  private def dot(a:V,b:V):Double=a.zip(b).map(_*_).sum

  def prepare(prior:G,y:V,h:V=>V,r:M,method:Method,epsilon:Double,budget:Budget,
    jacobian:Option[V=>M]=None):Prepared={
    check()
    require(prior!=null && prior.dimension<=4 && y!=null && y.nonEmpty && y.size<=4)
    require(h!=null && method!=null && budget!=null && epsilon.isFinite && epsilon>0 && epsilon<1)
    val d=prior.dimension;val k=y.size;finite(y,k)
    val nh=if(method==Method.Cubature)2*d else 1
    val nj=if(method==Method.Jacobian)1 else 0
    require(budget.hCalls>=nh && budget.jacobianCalls>=nj,"Preparation budget exhausted")
    G(Vector.fill(k)(0.0),r) // Validate true noise before callbacks.
    def eval(x:V):V={check();val v=h(x);finite(v,k);v}
    val (hm,obsMatrix,effectiveNoise)=if(method==Method.Jacobian){
      require(jacobian!=null && jacobian.exists(_!=null),"Explicit Jacobian required")
      val b=eval(prior.mean);check();val j=jacobian.get(prior.mean)
      require(j!=null && j.size==k && j.forall(v=>v!=null && v.size==d && v.forall(_.isFinite)),"Invalid Jacobian")
      (b,j,r)
    }else{
      val s=prior.covariance.indices.map(i=>math.sqrt(prior.covariance(i)(i))).toVector
      val corr=Vector.tabulate(d,d)((i,j)=>prior.covariance(i)(j)/s(i)/s(j))
      val factor=new CholeskyDecomposition(matrix(corr),1e-12,1e-14)
      val l=factor.getL
      val points=(for(j<-0 until d;sign<-Vector(-1.0,1.0))yield
        Vector.tabulate(d)(i=>prior.mean(i)+sign*math.sqrt(d)*s(i)*l.getEntry(i,j))).toVector
      val values=points.map(eval)
      val b=Vector.tabulate(k)(a=>values.map(_(a)).sum/(2*d))
      val cross=Vector.tabulate(d,k)((i,a)=>points.indices.map(j=>(points(j)(i)-prior.mean(i))*(values(j)(a)-b(a))).sum/(2*d))
      val j=Vector.tabulate(k){a=>
        val rhs=new ArrayRealVector(Vector.tabulate(d)(i=>cross(i)(a)/s(i)).toArray,false)
        factor.getSolver.solve(rhs).toArray.toVector.zip(s).map(_/_)
      }
      val residual=points.indices.map(i=>Vector.tabulate(k)(a=>values(i)(a)-b(a)-dot(j(a),points(i).zip(prior.mean).map(_-_))))
      // Identical operand order for symmetric entries; no jitter or subtraction repair.
      val e=Vector.tabulate(k,k){(a,z)=>val lo=math.min(a,z);val hi=math.max(a,z)
        r(a)(z)+residual.map(v=>v(lo)*v(hi)).sum/(2*d)}
      (b,j,e)
    }
    check()
    val offset=hm.zip(obsMatrix.map(row=>dot(row,prior.mean))).map(_-_)
    val result=P.linearGaussianObservation(prior,y,obsMatrix,effectiveNoise,offset)
    val law=P.defensive(P.multivariateGaussian(prior),result.proposal,epsilon)
    Prepared(result.posterior,law,result.logPredictive,
      Work(nh,nj,4,if(method==Method.Cubature)2 else 1, d+(if(method==Method.Cubature)k else 0)))
  }
}
