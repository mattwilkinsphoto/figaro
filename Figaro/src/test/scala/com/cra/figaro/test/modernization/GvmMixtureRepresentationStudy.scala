package com.cra.figaro.test.modernization

import com.cra.figaro.library.atomic.continuous.*
import com.cra.figaro.algorithm.sampling.VectorImportance
import com.cra.figaro.util.{SamplingRandom,CircularStatistics}
import org.apache.commons.math3.linear.{Array2DRowRealMatrix,ArrayRealVector,QRDecomposition}

/** Research-only representation comparison, NOT a public GVM fitter or tracking system.
  * Both candidates use the same training data, linear-quantile groups, component counts
  * and held-out samples. GVM uses quadratic phase regression; GMM uses chart moments.
  * This is a controlled baseline, not optimized maximum-likelihood fitting for either.
  */
object GvmMixtureRepresentationStudy {
  private def wrap(x: Double,center: Double)=center+CircularStatistics.normalize(x-center)
  private def gvm(mu: Double,alpha: Double,beta: Double,gamma: Double,kappa: Double)=
    GaussVonMisesDistribution(Vector(mu),Vector(Vector(1.0)),alpha,Vector(beta),Vector(Vector(gamma)),kappa)
  private def train(data: Vector[LinearAngular],k: Int,gvmMode: Boolean,center: Double): VectorImportance.Proposal = {
    val sorted=data.sortBy(_.linear.head)
    val groups=Vector.tabulate(k)(i=>sorted.slice(i*sorted.size/k,(i+1)*sorted.size/k))
    val weights=groups.map(_.size.toDouble/data.size)
    if(gvmMode) {
      val components=groups.map { points=>
        val n=points.size; val mean=points.map(_.linear.head).sum/n
        val variance=points.map(p=>math.pow(p.linear.head-mean,2)).sum/n+1e-6
        val z=points.map(p=>(p.linear.head-mean)/math.sqrt(variance))
        var previous=points.head.angle
        val phase=points.map { p=> val next=previous+CircularStatistics.normalize(p.angle-previous); previous=next; next }
        val design=new Array2DRowRealMatrix(z.map(v=>Array(1.0,v,.5*v*v)).toArray,false)
        val coefficients=new QRDecomposition(design,1e-12).getSolver.solve(new ArrayRealVector(phase.toArray,false)).toArray
        val residual=z.indices.map(i=>phase(i)-coefficients(0)-coefficients(1)*z(i)-.5*coefficients(2)*z(i)*z(i))
        val c=residual.map(math.cos).sum/n; val s=residual.map(math.sin).sum/n; val resultant=math.hypot(c,s)
        require(resultant<VonMisesDistribution(0,100).meanResultantLength,"Research concentration cap")
        var lo=0.0; var hi=100.0
        for(i<-0 until 60) { val mid=(lo+hi)/2; if(VonMisesDistribution(0,mid).meanResultantLength<resultant) lo=mid else hi=mid }
        GaussVonMisesDistribution(Vector(mean),Vector(Vector(variance)),coefficients(0)+math.atan2(s,c),Vector(coefficients(1)),Vector(Vector(coefficients(2))),(lo+hi)/2)
      }
      GaussVonMisesMixtureDistribution(weights,components).asProposal(center)
    } else {
      val components=groups.map { points=>
        val values=points.map(p=>Vector(p.linear.head,wrap(p.angle,center)))
        val mean=Vector.tabulate(2)(j=>values.map(_(j)).sum/values.size)
        val cov=Vector.tabulate(2,2)((a,b)=>values.map(x=>(x(a)-mean(a))*(x(b)-mean(b))).sum/values.size+(if(a==b) 1e-6 else 0))
        MultivariateGaussianDistribution(mean,cov)
      }
      val mixture=GaussianMixtureDistribution(weights,components)
      val mass=components.indices.map(i=>weights(i)*math.exp(ObservationLikelihood.logInterval(
        GaussianDistribution(components(i).mean(1),math.sqrt(components(i).covariance(1)(1))),center-math.Pi,center+math.Pi))).sum
      require(mass>0 && mass<=1)
      new VectorImportance.Proposal {
        val dimension=2
        def logDensity(x: Vector[Double]): Double=if(x.last<center-math.Pi || x.last>=center+math.Pi) Double.NegativeInfinity else mixture.logDensity(x)-math.log(mass)
        def sample(rng: scala.util.Random): Vector[Double] = {
          var i=0
          while(i<10000) { val x=mixture.sample(rng); if(x.last>=center-math.Pi && x.last<center+math.Pi) return x; i+=1 }
          throw new ArithmeticException("Research GMM chart rejection cap")
        }
      }
    }
  }
  /** @param args optional seed count 1..30, default 5
    * @return Unit; CSV rows retain every refusal and time training/scoring/drawing separately
    * @example `sbt "figaro / Test / runMain com.cra.figaro.test.modernization.GvmMixtureRepresentationStudy 5"`
    */
  def main(args: Array[String]): Unit = {
    val count=if(args.isEmpty) 5 else args(0).toInt; require(count>=1 && count<=30)
    val fixtures=Vector(
      "local"->GaussVonMisesMixtureDistribution(Vector(1),Vector(gvm(0,0,.1,0,30))),
      "curved"->GaussVonMisesMixtureDistribution(Vector(1),Vector(gvm(0,0,.4,2,20))),
      "seam"->GaussVonMisesMixtureDistribution(Vector(1),Vector(gvm(0,3.1,.5,.4,15))),
      "separated"->GaussVonMisesMixtureDistribution(Vector(.5,.5),Vector(gvm(-2,-1,.3,0,10),gvm(2,1,.3,0,10))))
    println("fixture,seed,components,method,parameters,status,train,heldout,draws,chartCenter,meanLogScore,regionProbability,referenceRegion,fitMillis,scoreMillis,drawMillis")
    for((name,truth)<-fixtures; seed<-0 until count) {
      val rng=SamplingRandom.scalaRandom(103000+seed)
      val data=Vector.fill(1200)(truth.sample(rng)); val testRng=SamplingRandom.scalaRandom(104000+seed)
      val test=Vector.fill(4000)(truth.sample(testRng))
      val center=math.atan2(data.map(p=>math.sin(p.angle)).sum,data.map(p=>math.cos(p.angle)).sum)
      val physical=test.map(p=>Vector(p.linear.head,wrap(p.angle,center)))
      def event(x: Vector[Double])=x.head>1 && math.cos(x.last)>0
      val reference=physical.count(event).toDouble/test.size
      for(k<-Vector(1,2,4); mode<- (if(seed%2==0) Vector(false,true) else Vector(true,false))) {
        val method=if(mode) "gvm-regression" else "gmm-chart-moments"
        val params=(if(mode) 6 else 5)*k+k-1
        val prefix=s"$name,${103000+seed},$k,$method,$params"
        try {
          val start=System.nanoTime(); val law=train(data,k,mode,center); val fitted=System.nanoTime()
          val score=physical.map(law.logDensity).sum/test.size; val scored=System.nanoTime()
          val drawRng=SamplingRandom.scalaRandom(105000+seed)
          val probability=Vector.fill(4000)(law.sample(drawRng)).count(event)/4000.0; val sampled=System.nanoTime()
          require(score.isFinite)
          println(s"$prefix,completed,1200,4000,4000,$center,$score,$probability,$reference,${(fitted-start)/1e6},${(scored-fitted)/1e6},${(sampled-scored)/1e6}")
        } catch {
          case e: ArithmeticException => println(s"$prefix,refused,1200,4000,4000,$center,,,,,,")
          case e: IllegalArgumentException => println(s"$prefix,refused,1200,4000,4000,$center,,,,,,")
        }
      }
    }
  }
}
