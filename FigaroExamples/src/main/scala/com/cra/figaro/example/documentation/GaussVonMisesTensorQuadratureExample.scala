package com.cra.figaro.example.documentation

import com.cra.figaro.library.atomic.continuous.*

/** Reproducible evaluation-count/accuracy comparisons; no universal wall-clock claim. */
object GaussVonMisesTensorQuadratureExample {
  private def canonical(n: Int)=GaussVonMisesDistribution(Vector.fill(n)(0.0),
    Vector.tabulate(n,n)((i,j) => if(i == j) 1.0 else 0.0),0,Vector.fill(n)(0.0),Vector.fill(n,n)(0.0),0)
  /** Run positive-weight, refinement and vector-output comparisons against analytic answers.
    * @param args empty command-line arguments
    * @return Unit; prints callback counts, absolute errors and checked vector expectations
    * @example `GaussVonMisesTensorQuadratureExample.main(Array.empty[String])`
    */
  def main(args: Array[String]): Unit = {
    def f(p: LinearAngular): Double=math.exp(-p.linear.map(x => x*x).sum)
    // 1. Fix positivity on the sparse counterexample, without claiming that positivity fixes accuracy.
    val six=canonical(6); val exactSix=1.0/27
    println(f"Six-dimensional sparse result=${six.thirdOrderQuadrature().expectation(f)}%.9f, exact=$exactSix%.9f")
    for (order <- Vector(3,5,7)) {
      // The function ignores the angle and kappa=0: two angular points suffice in this example only.
      val q=six.tensorQuadrature(order,2,300000); val estimate=q.expectation(f)
      println(f"six dimensions: G=$order%2d A=2 calls=${q.nodeCount}%7d estimate=$estimate%.9f absoluteError=${math.abs(estimate-exactSix)}%.9f")
      require(estimate > 0 && estimate < 1)
    }
    // 2. Refine low-dimensional Gaussian order and measure bias, not just agreement between rules.
    val two=canonical(2); val exactTwo=1.0/3
    var previous=Double.PositiveInfinity
    for (order <- Vector(3,7,15,25)) {
      val q=two.tensorQuadrature(order,2); val estimate=q.expectation(f); val error=math.abs(estimate-exactTwo)
      println(f"two dimensions: G=$order%2d A=2 calls=${q.nodeCount}%7d estimate=$estimate%.9f absoluteError=$error%.9g")
      require(error < previous); previous=error
    }
    require(previous < 1e-7)
    // 3. A coupled physical expectation: share one callback for multiple statistics.
    val curved=GaussVonMisesDistribution(Vector(1.0),Vector(Vector(2.25)),0.3,Vector(0.7),Vector(Vector(0.4)),4.5)
    val q=curved.tensorQuadrature(25,96)
    val result=q.expectationVector(2)(p => Vector(p.linear(0),math.cos(p.angle)))
    println(s"Coupled vector=$result, calls=${q.nodeCount}, angular mass=${q.angularMassEstimate}")
    require(math.abs(result.head-1) < 1e-12)
    require(math.abs(result(1)-curved.moments.meanCos) < 2e-9)
  }
}
