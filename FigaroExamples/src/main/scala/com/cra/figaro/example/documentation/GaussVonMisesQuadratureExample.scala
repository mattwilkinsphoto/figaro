package com.cra.figaro.example.documentation

import com.cra.figaro.library.atomic.continuous.*

/** Checked sparse-quadrature comparisons, including a deliberate out-of-class failure. */
object GaussVonMisesQuadratureExample {
  /** Run scalar, analytic-oracle and vector workflows; uses only caller-owned randomness.
    * @param args empty command-line arguments
    * @return Unit; prints evaluation counts and approximation errors, including signed-weight limitations
    * @example `GaussVonMisesQuadratureExample.main(Array.empty[String])`
    */
  def main(args: Array[String]): Unit = {
    val n=6
    val kernel=GaussVonMisesDistribution(Vector.fill(n)(0.0),
      Vector.tabulate(n,n)((i,j) => if(i == j) 1.0 else 0.0),0,
      Vector.fill(n)(0.0),Vector.fill(n,n)(0.0),4.5)
    val rule=kernel.thirdOrderQuadrature()
    def squaredNorm(p: LinearAngular): Double=p.linear.map(x => x*x).sum

    // 1. Exactness-class function: compare 15 deterministic evaluations with 50,000 draws.
    val deterministic=rule.expectation(squaredNorm)
    val rng=new scala.util.Random(97314L); val draws=50000
    val sampled=(0 until draws).map(_ => squaredNorm(kernel.sample(rng))).sum/draws
    println(f"E[||x||^2]: sparse=$deterministic%.6f (${rule.nodeCount} evaluations), MC=$sampled%.6f ($draws evaluations), exact=6")
    require(math.abs(deterministic-6) < 1e-13)
    require(math.abs(sampled-6) < 5*math.sqrt(12.0/draws))

    // 2. A weakly coupled angular function is approximate; prefer supported analytic moments.
    val curved=GaussVonMisesDistribution(Vector(0.0),Vector(Vector(1.0)),0.3,Vector(0.1),Vector(Vector(0.05)),4.5)
    val cosine=curved.thirdOrderQuadrature().expectation(p => math.cos(p.angle))
    val analytic=curved.moments.meanCos
    println(f"Coupled E[cos(theta)]: sparse=$cosine%.9f, analytic=$analytic%.9f, error=${cosine-analytic}%.9f")
    require(math.abs(cosine-analytic) < 0.002)

    // 3. Reuse an expensive callback for several scalar outputs in a single traversal.
    var calls=0
    val vector=rule.expectationVector(3) { p =>
      calls += 1
      Vector(p.linear(0),p.linear(0)*p.linear(0),squaredNorm(p))
    }
    println(s"Vector expectations=$vector; callback invocations=$calls")
    require(calls == rule.nodeCount)
    vector.zip(Vector(0.0,1.0,6.0)).foreach((a,b) => require(math.abs(a-b) < 1e-13))

    // Signed rules can badly miss even smooth, positive functions outside the exactness class.
    val failed=rule.expectation(p => math.exp(-squaredNorm(p)))
    println(f"Counterexample E[exp(-||x||^2)]: sparse=$failed%.9f, exact=${1.0/27}%.9f; negative weights=${rule.hasNegativeWeights}")
    require(failed < 0) // Do not clip this to zero or interpret it as a valid probability.
  }
}
