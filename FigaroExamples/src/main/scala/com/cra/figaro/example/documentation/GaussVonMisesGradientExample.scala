package com.cra.figaro.example.documentation

import com.cra.figaro.library.atomic.continuous.*

/** Analytic state sensitivities, directional checks and squared-score derivatives. */
object GaussVonMisesGradientExample {
  /** Run three gradient patterns without creating a Figaro model or consuming randomness.
    * @param args empty command-line arguments
    * @return Unit; prints and checks local sensitivities and a directional approximation
    * @example `GaussVonMisesGradientExample.main(Array.empty[String])`
    */
  def main(args: Array[String]): Unit = {
    val kernel=GaussVonMisesDistribution(Vector(1.0,-2.0),
      Vector(Vector(4.0,1.2),Vector(1.2,2.61)),3.05,Vector(0.7,-0.4),
      Vector(Vector(0.3,0.2),Vector(0.2,-0.5)),4.5)
    val point=LinearAngular(Vector(2.0,-1.0),-3.1)

    // 1. Local sensitivity in original coordinate units, not whitened coordinates.
    val gradient=kernel.logDensityGradient(point)
    println(s"Log-density gradient: linear=${gradient.linear}, angular=${gradient.angular} per radian")
    require(math.abs(gradient.angular-0.2692551952524661) < 1e-12)

    // 2. Directional derivative: a dot product replaces repeated finite differences.
    val direction=Vector(0.2,-0.1); val angularDirection=0.3; val step=1e-4
    val slope=gradient.linear.zip(direction).map(_*_).sum+gradient.angular*angularDirection
    val moved=LinearAngular(point.linear.zip(direction).map((x,d) => x+step*d),point.angle+step*angularDirection)
    val predicted=step*slope
    val actual=kernel.logDensity(moved)-kernel.logDensity(point)
    println(f"Small-step log-density change: first order=$predicted%.10f, actual=$actual%.10f")
    require(math.abs(actual-predicted) < 1e-8)

    // 3. Score sensitivity is exactly -2 times log-density sensitivity for fixed parameters.
    val scoreGradient=kernel.mahalanobisSquaredGradient(point)
    println(s"Squared-score gradient: linear=${scoreGradient.linear}, angular=${scoreGradient.angular}")
    scoreGradient.linear.zip(gradient.linear).foreach((a,b) => require(a == -2*b))
    require(scoreGradient.angular == -2*gradient.angular)
  }
}
