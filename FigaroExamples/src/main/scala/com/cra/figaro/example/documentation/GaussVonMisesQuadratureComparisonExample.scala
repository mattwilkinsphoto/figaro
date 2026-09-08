package com.cra.figaro.example.documentation

import com.cra.figaro.library.atomic.continuous.*

object GaussVonMisesQuadratureComparisonExample {
  /** Run budgeted scalar/vector comparisons and an analytic false-agreement control.
    * @param args empty command-line arguments
    * @return Unit; prints checked estimates, directional changes, agreement and callback counts
    * @example `GaussVonMisesQuadratureComparisonExample.main(Array.empty[String])`
    */
  def main(args: Array[String]): Unit = {
    val kernel=GaussVonMisesDistribution(Vector(0.0),Vector(Vector(1.0)),0,Vector(0.0),Vector(Vector(0.0)),0)
    def nonlinear(p: LinearAngular): Double=math.exp(-p.linear(0)*p.linear(0))

    // 1. A single estimate versus a pre-budgeted diagnostic. Here f ignores the uniform angle.
    val single=kernel.tensorQuadrature(3,2).expectation(nonlinear)
    val plan=GaussVonMisesQuadratureComparison(kernel,3,7,2,4,60)
    val result=plan.compare(nonlinear)
    println(s"Single=$single; jointly refined=${result.jointRefined.head}; exact=${1/math.sqrt(3)}")
    println(s"Gaussian change=${result.maxGaussianChange.head}; angular change=${result.maxAngularChange.head}; calls=${result.evaluations}")
    require(single == result.baseline.head && !result.ordersAgree)
    require(result.maxGaussianChange.head > 0.09 && result.maxAngularChange.head < 1e-14)

    // 2. Reuse a plan and evaluate several statistics per point; inspect every component.
    val vector=plan.compareVector(2)(p => Vector(p.linear(0)*p.linear(0),nonlinear(p)))
    println(s"Vector=${vector.jointRefined}; agreement=${vector.withinTolerance}; calls=${vector.evaluations}")
    require(vector.withinTolerance == Vector(true,false))
    require(vector.evaluations == 60)

    // 3. Agreement is not accuracy: a polynomial vanishes at both orders' Gaussian nodes.
    val trap=GaussVonMisesQuadratureComparison(kernel,3,5,2,4)
    val misleading=trap.compare { p =>
      val z=p.linear(0); val h3=z*z*z-3*z; val h5=math.pow(z,5)-10*z*z*z+15*z
      math.pow(h3*h5,2)/295920.0
    }
    // Exact expectation is one, from standard Gaussian even moments.
    println(s"False agreement: estimate=${misleading.jointRefined.head}; ordersAgree=${misleading.ordersAgree}; exact=1.0")
    require(misleading.ordersAgree && math.abs(misleading.jointRefined.head-1) > 0.99)
  }
}
