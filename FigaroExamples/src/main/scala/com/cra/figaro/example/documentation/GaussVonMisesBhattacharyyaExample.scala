package com.cra.figaro.example.documentation

import com.cra.figaro.library.atomic.continuous.*

object GaussVonMisesBhattacharyyaExample {
  private def scalar(mu: Double=0,sd: Double=1,alpha: Double=0,beta: Double=0,gamma: Double=0,k: Double=0) =
    GaussVonMisesDistribution(Vector(mu),Vector(Vector(sd*sd)),alpha,Vector(beta),Vector(Vector(gamma)),k)
  private def canonical(n: Int,b: Double,k: Double) = GaussVonMisesDistribution(Vector.fill(n)(0.0),
    Vector.tabulate(n,n)((i,j) => if(i == j) 1.0 else 0.0),0,Vector.fill(n)(b),Vector.fill(n,n)(0.0),k)

  /** Run exact, guarded-failure and six-dimensional accuracy/cost examples.
    * @param args empty command-line arguments
    * @return Unit; prints checked distances, statuses and accuracy/work counts, not universal speed claims
    * @example `GaussVonMisesBhattacharyyaExample.main(Array.empty[String])`
    */
  def main(args: Array[String]): Unit = {
    // 1. Compare complete laws; Bhattacharyya is symmetric, unlike directed KL.
    val gaussian=GaussVonMisesBhattacharyya.compare(scalar(),scalar(mu=2))
    require(gaussian.resolved && math.abs(gaussian.distance.get-.5) < 1e-12)
    val p=scalar(.3,1.1,.2,.7,.3,4.5); val q=scalar(-.4,.8,-.5,-.2,-.15,1.2)
    val curved=GaussVonMisesBhattacharyya.compare(p,q)
    println(s"Curved: status=${curved.status}, distance=${curved.distance}, interval=${curved.estimatedDistanceInterval}, harmonics=${curved.harmonicsUsed}")
    require(curved.resolved && math.abs(curved.distance.get-.35550991284058316774) < 1e-8)

    // 2. Handle all outcomes without substituting a guessed distance.
    val exhausted=GaussVonMisesBhattacharyya.compare(p,q,maxHarmonics=0)
    val cancellation=GaussVonMisesBhattacharyya.compare(scalar(k=50),scalar(alpha=math.Pi,beta=1e-5,k=50))
    val unsupported=GaussVonMisesBhattacharyya.compare(scalar(k=1000),scalar(alpha=.1,k=1000))
    for (r <- Vector(exhausted,cancellation,unsupported)) {
      println(s"Unresolved: ${r.status}; distance=${r.distance}; ${r.message}")
      require(!r.resolved && r.distance.isEmpty)
    }
    // Removing the tiny varying coupling admits a stable exact circular formula.
    val opposed=GaussVonMisesBhattacharyya.compare(scalar(k=50),scalar(alpha=math.Pi,k=50))
    require(opposed.resolved && math.abs(opposed.distance.get-47.127575501871805) < 1e-8)

    // 3. Same-Gaussian six-dimensional comparison against a positive tensor reference.
    val sixP=canonical(6,.2,4); val sixQ=canonical(6,-.2,4)
    val exact=.2886679589491771038676 // Independent high-precision Fourier fixture.
    val series=GaussVonMisesBhattacharyya.compare(sixP,sixQ,1e-6)
    require(series.resolved && math.abs(series.distance.get-exact) < 1e-6)
    println(s"Six-dimensional series: harmonics=${series.harmonicsUsed}, absolute distance error=${math.abs(series.distance.get-exact)}")
    val base=canonical(6,0,0); val peak=VonMisesDistribution(0,4).logDensity(0)
    var finestError=Double.PositiveInfinity
    for (order <- Vector(3,5,7)) {
      val rule=base.tensorQuadrature(order,2,300000)
      // Here the Gaussian overlap measure equals the common marginal; no angular grid is mathematically needed.
      val affinity=rule.expectation { x =>
        val radius=4*math.abs(math.cos(.2*x.linear.sum))
        math.exp(radius-4+peak-VonMisesDistribution(0,radius).logDensity(0))
      }
      val distance= -math.log(affinity)
      finestError=math.abs(distance-exact)
      println(s"Six-dimensional tensor: G=$order, callbacks=${rule.nodeCount}, absolute distance error=$finestError")
    }
    require(finestError < 1e-6)
  }
}
