package com.cra.figaro.example.documentation

import com.cra.figaro.algorithm.sampling.Importance
import com.cra.figaro.language.*
import com.cra.figaro.library.atomic.continuous.*
import com.cra.figaro.util.{CircularStatistics as Circular, withRandomSeed}

/** Analytic summaries and an explicitly constructed hierarchical angular model. */
object GaussVonMisesMomentsExample {
  /** Compare analytic/sample moments, use a conditional kernel, then observe a conditional element.
    * @param args empty string array; nonempty arguments are rejected
    * @return Unit; prints results, checks expectations and disposes the Figaro model
    * @example `GaussVonMisesMomentsExample.main(Array.empty[String])`
    */
  def main(args: Array[String]): Unit = {
    require(args.isEmpty,"GaussVonMisesMomentsExample takes no arguments")
    val kernel=GaussVonMisesDistribution(Vector(0.0),Vector(Vector(1.0)),
      3.0,Vector(0.7),Vector(Vector(0.4)),4.5)
    val moments=kernel.moments
    val rng=new scala.util.Random(201L)
    val draws=Vector.fill(40000)(kernel.sample(rng))
    val empirical=Circular.summarize(draws.map(_.angle))
    assert(math.abs(Circular.difference(empirical.meanDirection.get,moments.meanDirection.get)) < 0.03)
    val mixed=draws.map(p => p.linear.head*math.sin(p.angle)).sum/draws.size
    assert(math.abs(mixed-moments.linearSin.head) < 0.025)
    println(f"Marginal direction: ${moments.meanDirection.get}%.6f; center at mean: ${kernel.alpha}%.6f radians")
    println(f"Analytic E[x sin(theta)]: ${moments.linearSin.head}%.6f; sampled: $mixed%.6f")

    val conditional=kernel.conditionalAngle(Vector(1.0))
    val observation=3.1
    assert(math.abs(kernel.logDensity(LinearAngular(Vector(1.0),observation)) -
      kernel.linearLogDensity(Vector(1.0))-conditional.logDensity(observation)) < 1e-13)
    println(f"Conditional center at x=1: ${conditional.location}%.6f; one draw: ${conditional.sample(rng)}%.6f")

    // Explicit hierarchy: unlike an observed Apply projection, this angular element has a density.
    withRandomSeed(804L) {
      val u=Universe.createNew()
      val linear=Normal(0.0,1.0)(using "linear",u)
      val angle=NonCachingChain(linear,(x: Double) => {
        val c=kernel.conditionalAngle(Vector(x))
        VonMises(c.location,c.kappa)(using "",u)
      })(using "angle",u)
      angle.observe(observation)
      // Independent direct Gaussian-weighted midpoint integral for the posterior mean.
      val grid=(0 until 10000).map(i => -10+(i+0.5)*20/10000)
      val weights=grid.map(x => math.exp(-0.5*x*x+4.5*math.cos(observation-(3+0.7*x+0.2*x*x))))
      val expected=grid.zip(weights).map((x,w) => x*w).sum/weights.sum
      val algorithm=Importance(40000,linear)
      try {
        algorithm.start()
        val actual=algorithm.expectation(linear,(x: Double) => x)
        assert(math.abs(actual-expected) < 0.025)
        println(f"Hierarchical posterior E[x]: $actual%.6f; independent quadrature: $expected%.6f")
      } finally { if(algorithm.isActive) algorithm.kill(); u.clear() }
    }
    println("Gauss-von Mises moments examples passed")
  }
}
