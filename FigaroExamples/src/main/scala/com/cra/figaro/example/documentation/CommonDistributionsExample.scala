package com.cra.figaro.example.documentation

import com.cra.figaro.library.atomic.continuous.*
import com.cra.figaro.library.atomic.discrete.{NegativeBinomialDistribution,HypergeometricDistribution,CountDivergence}
import com.cra.figaro.library.atomic.{DiscreteInformation,InformationMetricStatus}
import com.cra.figaro.language.*
import com.cra.figaro.algorithm.sampling.Importance
import com.cra.figaro.util.withRandomSeed

/** Executable companion to the common-family and information-metric guides. */
object CommonDistributionsExample {
  /** Run robust hierarchical evidence, positive/count modeling, and information comparisons.
    * @param args unused
    * @return Unit after checking all three patterns; algorithms/universes are cleaned up
    * @example `sbt "examples / Compile / runMain com.cra.figaro.example.documentation.CommonDistributionsExample"`
    */
  def main(args: Array[String]): Unit = {
    withRandomSeed(431) {
      val u=Universe.createNew(); val heavy=Flip(.3)(using "heavy",u)
      val law=Apply(heavy,(b: Boolean) => StudentTDistribution(if(b) 3 else 30))
      val observation=ScalarElement(law); observation.observe(4.0)
      val expected=1/(1+(0.7/0.3)*math.exp(StudentTDistribution(30).logDensity(4)-StudentTDistribution(3).logDensity(4)))
      val inference=Importance(12000,heavy)
      try { inference.start(); val posterior=inference.probability(heavy,true)
        require(math.abs(posterior-expected) < .03); println(s"Tail-model posterior: $posterior (enumerated $expected)") }
      finally { if(inference.isActive) inference.kill(); u.clear() }
    }
    val lifetime=WeibullDistribution(1.7,2.3); val positive=LogNormalDistribution(.3,.8)
    val count=NegativeBinomialDistribution(2.5,.4); val withoutReplacement=HypergeometricDistribution(20,7,5)
    require(count.variance > count.mean)
    println(s"Lifetime P(X>3)=${lifetime.survival(3)}; lognormal median=${positive.quantile(.5)}")
    println(s"Count mean=${count.mean}, variance=${count.variance}; without-replacement P(X=2)=${withoutReplacement.probability(2)}")

    val kl=ScalarDivergence.kl(StudentTDistribution(5),StudentTDistribution(8,.4,1.2))
    val overlap=ScalarDivergence.bhattacharyya(WeibullDistribution(1.7,2.3),WeibullDistribution(2.4,1.4))
    val countKl=CountDivergence.kl(count,NegativeBinomialDistribution(3.2,.6))
    val mi=DiscreteInformation.mutualInformation(Vector(Vector(.5,0.0),Vector(0.0,.5)))
    for(result <- Vector(kl,overlap,countKl,mi)) {
      require(Set(InformationMetricStatus.Analytic,InformationMetricStatus.Estimated).contains(result.status))
      println(s"${result.method}: ${result.value} nats, error estimate ${result.errorEstimate}, work ${result.evaluations}")
    }
    require(math.abs(mi.value.get-math.log(2)) < 1e-12)
    require(ScalarDivergence.kl(StudentTDistribution(5),StudentTDistribution(8),maxEvaluations=1).value.isEmpty)
  }
}
