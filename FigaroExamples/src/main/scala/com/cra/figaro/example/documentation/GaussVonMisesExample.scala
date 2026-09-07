package com.cra.figaro.example.documentation

import com.cra.figaro.algorithm.sampling.{Importance, MetropolisHastings, ProposalScheme}
import com.cra.figaro.language.*
import com.cra.figaro.library.atomic.continuous.*
import com.cra.figaro.util.{CircularStatistics as Circular, withRandomSeed}

/** Synthetic distribution examples, not an orbit model or a GVM filtering algorithm. */
object GaussVonMisesExample {
  /** Demonstrate coupled prior draws, complete observations and posterior projections.
    * @param args empty string array
    * @return Unit; prints results, checks illustrative bounds and cleans up its models
    * @example `GaussVonMisesExample.main(Array.empty[String])`
    */
  def main(args: Array[String]): Unit = {
    require(args.isEmpty, "GaussVonMisesExample takes no arguments")
    def kernel(coupling: Double) = GaussVonMisesDistribution(Vector(0.0),Vector(Vector(1.0)),
      3.0,Vector(coupling),Vector(Vector(0.4)),4.5)
    val d = kernel(0.7)
    val rng = new scala.util.Random(42L)
    val draws = Vector.fill(10000)(d.sample(rng))
    val residuals = draws.map(p => Circular.difference(p.angle,d.conditionalLocation(p.linear)))
    val residual = Circular.summarize(residuals)
    assert(math.abs(residual.meanDirection.get) < 0.05)
    println(f"Conditional residual resultant: ${residual.meanResultantLength}%.4f (expected 0.8803)")
    println(f"Conditional centers at x=-1 and x=1: ${d.conditionalLocation(Vector(-1.0))}%.4f, ${d.conditionalLocation(Vector(1.0))}%.4f radians")

    withRandomSeed(245L) {
      val u = Universe.createNew()
      val choice = Flip(0.4)(using "positiveCoupling",u)
      val reading = NonCachingChain(choice, (b: Boolean) => GaussVonMises(kernel(if(b) 0.7 else -0.7))(using "",u))
      reading.observe(LinearAngular(Vector(1.0),3.1))
      val alg = Importance(20000,choice)
      try {
        alg.start()
        val p = alg.probability(choice,true)
        val expected = 1/(1+1.5*math.exp(4.5*(math.cos(0.6)-math.cos(-0.8))))
        assert(math.abs(p-expected) < 0.025)
        println(f"P(positive coupling | complete joint observation) = $p%.4f")
      } finally { if(alg.isActive) alg.kill(); u.clear() }
    }

    withRandomSeed(53L) {
      val u = Universe.createNew()
      val state = GaussVonMises(d)(using "state",u)
      state.addLogConstraint((p: LinearAngular) => -0.5*math.pow(p.linear(0)-1,2))
      val alg = MetropolisHastings(30000,ProposalScheme.default(using u),1000,state)
      try {
        alg.start()
        val x = alg.expectation(state,(p: LinearAngular) => p.linear(0))
        assert(math.abs(x-0.5) < 0.035)
        val s = alg.expectation(state,(p: LinearAngular) => math.sin(p.angle))
        val c = alg.expectation(state,(p: LinearAngular) => math.cos(p.angle))
        println(f"Posterior E[x] = $x%.4f; angular mean = ${math.atan2(s,c)}%.4f radians")
      } finally { if(alg.isActive) alg.kill(); u.clear() }
    }
    println("Gauss-von Mises examples passed")
  }
}
