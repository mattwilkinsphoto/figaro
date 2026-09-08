package com.cra.figaro.example.documentation

import com.cra.figaro.library.atomic.continuous.*

/** Executable companion to docs/GVM_MUTUAL_INFORMATION.md. */
object GaussVonMisesMutualInformationExample {
  /** Demonstrate dependence, scenario comparison, and refusal handling for fixed laws.
    * @param args unused
    * @return Unit after checking the three documented patterns
    * @example `sbt "examples / Compile / runMain com.cra.figaro.example.documentation.GaussVonMisesMutualInformationExample"`
    */
  def main(args: Array[String]): Unit = {
    import GaussVonMisesMutualInformation.{compute,Status}
    def model(b: Double,g: Double=0,k: Double=2) =
      GaussVonMisesDistribution(Vector(0.0),Vector(Vector(1.0)),0,Vector(b),Vector(Vector(g)),k)
    val coupled=compute(model(.4))
    require(coupled.status == Status.Estimated && math.abs(coupled.value.get-.09772189838645959) < 1e-8)
    println(s"Linear-angular dependence: ${coupled.value} nats; estimated interval ${coupled.interval}")
    println(s"Bits: ${coupled.value.map(_/math.log(2))}")

    val independent=compute(model(0))
    val curved=compute(model(.7,.3,4))
    require(independent.value.contains(0.0) && curved.value.exists(_ > coupled.value.get))
    println(s"Independent: ${independent.value}; coupled: ${coupled.value}; curved: ${curved.value}")

    val limited=compute(model(.4),maxEvaluations=1)
    require(limited.status == Status.BudgetExhausted && limited.value.isEmpty)
    val retry=compute(model(.4),maxEvaluations=16384)
    require(retry.status == Status.Estimated)
    println(s"Explicit budget retry: ${limited.status} -> ${retry.status}; ${retry.evaluations} evaluations")
  }
}
