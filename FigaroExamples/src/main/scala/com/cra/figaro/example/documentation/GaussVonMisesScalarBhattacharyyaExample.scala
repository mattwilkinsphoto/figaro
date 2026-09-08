package com.cra.figaro.example.documentation

import com.cra.figaro.library.atomic.continuous.*

/** Executable companion to docs/GVM_SCALAR_BHATTACHARYYA.md. */
object GaussVonMisesScalarBhattacharyyaExample {
  /** Run three fixed-distribution comparison patterns; no inference or fusion is performed.
    * @param args unused
    * @return Unit after checking scalar recovery, budget handling and the Gaussian shortcut
    * @example `sbt "examples / Compile / runMain com.cra.figaro.example.documentation.GaussVonMisesScalarBhattacharyyaExample"`
    */
  def main(args: Array[String]): Unit = {
    def kernel(mu: Double=0,alpha: Double=0,beta: Double=0,kappa: Double=50) =
      GaussVonMisesDistribution(Vector(mu),Vector(Vector(1.0)),alpha,Vector(beta),Vector(Vector(0.0)),kappa)
    val p=kernel(); val q=kernel(alpha=math.Pi,beta=1e-5)
    val fourier=GaussVonMisesBhattacharyya.compare(p,q)
    val positive=GaussVonMisesScalarBhattacharyya.compare(p,q)
    assert(fourier.status == GaussVonMisesBhattacharyyaStatus.NumericallyUnresolved)
    assert(positive.status == GaussVonMisesScalarBhattacharyya.Status.Estimated)
    assert(math.abs(positive.distance.get-47.1275754862468045) <= 1e-8)
    println(s"Fourier: ${fourier.status}; opt-in scalar: ${positive.distance} nats, ${positive.evaluations} evaluations")

    val limited=GaussVonMisesScalarBhattacharyya.compare(p,q,maxEvaluations=5)
    assert(limited.status == GaussVonMisesScalarBhattacharyya.Status.BudgetExhausted)
    assert(limited.distance.isEmpty)
    println(s"Insufficient budget: ${limited.status}; no invented distance")

    val gaussian=GaussVonMisesScalarBhattacharyya.compare(kernel(kappa=0),kernel(mu=2,kappa=0))
    assert(gaussian.distance.contains(.5) && gaussian.evaluations == 0 && gaussian.method == "gaussian")
    println(s"Gaussian shortcut: ${gaussian.distance} nats; zero integrand evaluations")
  }
}
