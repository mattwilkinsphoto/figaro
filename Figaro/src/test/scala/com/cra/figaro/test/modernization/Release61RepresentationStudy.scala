package com.cra.figaro.test.modernization

import com.cra.figaro.library.atomic.continuous.*
import com.cra.figaro.algorithm.sampling.{GaussianMixtureProposal as Fit,VectorImportance as V}
import com.cra.figaro.util.{SamplingRandom,CircularStatistics}

/** Independent train/test data; GVM AND Gaussian-generated truths; EM GMM controls.
  * Public fitting kernels, but this study/control adapter is research-only.
  * Times include preprocessing/normalization per candidate; no accuracy winner is assumed.
  */
object Release61RepresentationStudy {
  private def sum(logs: Vector[Double]): Double = { val m=logs.max; if(m==Double.NegativeInfinity) m else m+math.log(logs.map(x=>math.exp(x-m)).sum) }
  private def kernel(mu: Double,a: Double,b: Double,c: Double,k: Double)=
    GaussVonMisesDistribution(Vector(mu),Vector(Vector(1.0)),a,Vector(b),Vector(Vector(c)),k)
  /** Research-only exact sampler / truncated-image density control on R x [-Pi,Pi).
    * Gaussian image summation spans at least twelve conditional standard deviations;
    * this is not a general certified wrapped-distribution API.
    */
  private[modernization] def wrapped(gmm: GaussianMixtureDistribution): V.Proposal = new V.Proposal {
    val dimension=2
    def sample(rng: scala.util.Random): Vector[Double] = { val x=gmm.sample(rng); Vector(x.head,CircularStatistics.normalize(x.last)) }
    def logDensity(x: Vector[Double]): Double = {
      require(x.size==2 && x.forall(_.isFinite))
      if(x.last < -math.Pi || x.last>=math.Pi) return Double.NegativeInfinity
      sum(gmm.components.indices.map { j=>
        val g=gmm.components(j); val v=g.covariance(0)(0)
        val mu=g.mean(1)+g.covariance(1)(0)/v*(x.head-g.mean.head)
        val sd=math.sqrt(g.covariance(1)(1)-g.covariance(1)(0)*g.covariance(0)(1)/v)
        val radius=math.ceil(12*sd/(2*math.Pi)).toInt+1
        require(radius<=1000 && math.abs(mu)<1e6)
        val center=math.rint((mu-x.last)/(2*math.Pi)).toInt
        val angle=GaussianDistribution(mu,sd)
        val terms=(-radius to radius).map(i=>angle.logDensity(x.last+2*math.Pi*(center+i))).toVector
        math.log(gmm.weights(j))+GaussianDistribution(g.mean.head,math.sqrt(v)).logDensity(x.head)+sum(terms)
      }.toVector)
    }
  }
  private def chart(gmm: GaussianMixtureDistribution,center: Double): V.Proposal = {
    val mass=gmm.components.indices.map { j=>val g=gmm.components(j)
      gmm.weights(j)*math.exp(ObservationLikelihood.logInterval(GaussianDistribution(g.mean(1),math.sqrt(g.covariance(1)(1))),center-math.Pi,center+math.Pi))
    }.sum
    require(mass>0 && mass<=1+1e-12)
    new V.Proposal {
      val dimension=2
      def logDensity(x: Vector[Double]): Double = {
        val y=Vector(x.head,center+CircularStatistics.normalize(x.last-center))
        gmm.logDensity(y)-math.log(mass)
      }
      def sample(rng: scala.util.Random): Vector[Double] = {
        var count=0
        while(count<10000) { val x=gmm.sample(rng); if(x.last>=center-math.Pi && x.last<center+math.Pi) return Vector(x.head,CircularStatistics.normalize(x.last)); count+=1 }
        throw new ArithmeticException("Chart rejection budget")
      }
    }
  }
  /** @param args optional independent dataset count 1..30 (default 5)
    * @return Unit; CSV includes every attempted fit and its budget/status
    * @example `sbt "figaro / Test / runMain com.cra.figaro.test.modernization.Release61RepresentationStudy 5"`
    */
  def main(args: Array[String]): Unit = {
    val seeds=if(args.isEmpty) 5 else args(0).toInt; require(seeds>=1 && seeds<=30)
    def gv(g: GaussVonMisesDistribution)=GaussVonMisesMixtureDistribution(Vector(1),Vector(g)).asProposal()
    val truths=Vector(
      "gvm-curved"->gv(kernel(0,0,.4,2,20)),
      "gvm-seam"->gv(kernel(0,3.1,.5,.4,15)),
      "gvm-separated"->GaussVonMisesMixtureDistribution(Vector(.5,.5),Vector(kernel(-2,-1,.3,0,10),kernel(2,1,.3,0,10))).asProposal(),
      "gaussian-local"->wrapped(GaussianMixtureDistribution(Vector(1),Vector(MultivariateGaussianDistribution(Vector(0,0),Vector(Vector(1.0,.15),Vector(.15,.05)))))),
      "gaussian-wrapped"->wrapped(GaussianMixtureDistribution(Vector(1),Vector(MultivariateGaussianDistribution(Vector(0,3),Vector(Vector(1.0,1.0),Vector(1.0,3.0)))))))
    println("fixture,seed,parameterBudget,components,parameters,method,status,train,heldout,meanLogScore,scoreMCSE,regionProbability,referenceRegion,fitMillis,scoreMillis,sampleMillis,angularEvaluations")
    for((name,truth)<-truths; seed<-0 until seeds) {
      val trainRng=SamplingRandom.scalaRandom(611000+seed); val data=Vector.fill(800)(truth.sample(trainRng))
      val testRng=SamplingRandom.scalaRandom(612000+seed); val test=Vector.fill(3000)(truth.sample(testRng))
      def event(x: Vector[Double])=x.head>1 && math.cos(x.last)>0
      val reference=test.count(event)/test.size.toDouble
      val methods=if(seed%2==0) Vector("gvm-em","gmm-chart-em","gmm-wrapped-em") else Vector("gmm-wrapped-em","gmm-chart-em","gvm-em")
      for(budget<-Vector(6,13,27); method<-methods) {
        val k=if(method=="gvm-em") (budget+1)/7 else (budget+1)/6
        val parameters=(if(method=="gvm-em") 7 else 6)*k-1
        val start=System.nanoTime(); var angular=0L; var fitStatus="refused"
        try {
          val law: V.Proposal=if(method=="gvm-em") {
            val fit=GaussVonMisesMixtureFit.fit(data.map(x=>LinearAngular(Vector(x.head),x.last)),
              GaussVonMisesMixtureFit.Config(components=k,maxIterations=60,maxAngularEvaluations=300,seed=613000+seed))
            angular=fit.attempts.map(_.angularEvaluations).sum
            fitStatus=fit.selectedAttempt.map(i=>fit.attempts(i).status.toString).getOrElse("refused")
            fit.distribution.getOrElse(throw new ArithmeticException("Fit refused")).asProposal()
          } else {
            val center=math.atan2(data.map(x=>math.sin(x.last)).sum,data.map(x=>math.cos(x.last)).sum)
            val points=data.map(x=>Vector(x.head,center+CircularStatistics.normalize(x.last-center)))
            val fitted=Fit.fitWeighted(points,Vector.fill(points.size)(0.0),Fit.Config(components=k,maxIterations=200,
              minComponentDraws=10,maxDensityEvaluations=2000000,diagonalRidge=Vector(1e-6,1e-6),covarianceInflation=1))
            fitStatus=fitted.status.toString
            val p=fitted.proposal.getOrElse(throw new ArithmeticException("GMM fit refused"))
            val gmm=GaussianMixtureDistribution(p.weights,p.components.map(_.asInstanceOf[V.Gaussian].law))
            if(method=="gmm-chart-em") chart(gmm,center) else wrapped(gmm)
          }
          val fitted=System.nanoTime(); val scores=test.map(law.logDensity); val mean=scores.sum/scores.size
          val se=math.sqrt(scores.map(x=>math.pow(x-mean,2)).sum/(scores.size-1)/scores.size)
          val scored=System.nanoTime(); val rng=SamplingRandom.scalaRandom(614000+seed)
          val region=Vector.fill(3000)(law.sample(rng)).count(event)/3000.0; val sampled=System.nanoTime()
          require(mean.isFinite && se.isFinite)
          println(s"$name,$seed,$budget,$k,$parameters,$method,$fitStatus,800,3000,$mean,$se,$region,$reference,${(fitted-start)/1e6},${(scored-fitted)/1e6},${(sampled-scored)/1e6},$angular")
        } catch {
          case _: ArithmeticException | _: IllegalArgumentException=>
            println(s"$name,$seed,$budget,$k,$parameters,$method,refused-$fitStatus,800,3000,,,,$reference,${(System.nanoTime()-start)/1e6},,,$angular")
        }
      }
    }
  }
}
