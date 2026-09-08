package com.cra.figaro.test.modernization

import com.cra.figaro.library.atomic.continuous.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class GaussVonMisesBhattacharyyaReliabilityTest extends AnyWordSpec with Matchers {
  import GaussVonMisesBhattacharyyaStatus.*
  import GvmBhattacharyyaReliabilityFixtures.{Fixture, all as fixtures}

  private def kernels(f: Fixture): (GaussVonMisesDistribution,GaussVonMisesDistribution) = {
    def kernel(mean: Double,sd: Double,alpha: Double,beta: Double,gamma: Double) =
      GaussVonMisesDistribution(Vector.fill(f.n)(mean),
        Vector.tabulate(f.n,f.n)((i,j) => if(i == j) sd*sd else 0.0),alpha,Vector.fill(f.n)(beta),
        Vector.tabulate(f.n,f.n)((i,j) => if(i == j) gamma else 0.0),f.k)
    f.family match {
      case "linear" => (kernel(0,1,0,0,0),kernel(0,1,f.alpha,f.beta,0))
      case "unequal" => (kernel(.5,1,.25,.5,.25),kernel(-.5,.5,-.5,-.25,f.gamma))
      case "dimension" => (kernel(0,1,0,f.beta,f.gamma),kernel(0,1,0,-f.beta,0))
      case _ => throw new IllegalArgumentException("unknown fixture family")
    }
  }

  "GVM Bhattacharyya reliability grid" should {
    "meet oracle accuracy for every resolved result and expose all unresolved outcomes" in {
      fixtures.size shouldBe 96
      fixtures.map(_.id).distinct.size shouldBe 96
      for (tolerance <- Vector(1e-6,1e-8,1e-10)) {
        val counts = scala.collection.mutable.Map.empty[(String,Double,GaussVonMisesBhattacharyyaStatus),Int].withDefaultValue(0)
        var worstRatio = 0.0
        for (f <- fixtures; (p,q) = kernels(f); (left,right) <- Vector((p,q),(q,p))) {
          val r = GaussVonMisesBhattacharyya.compare(left,right,tolerance)
          counts((f.family,f.k,r.status)) += 1
          withClue(s"${f.id} tolerance=$tolerance status=${r.status} interval=${r.estimatedDistanceInterval}: ") {
            // Retain useful availability controls: an implementation returning None
            // for the whole grid must not pass this reliability suite.
            if (f.family == "unequal" || (f.family == "linear" && f.k <= 1) ||
              (f.family == "dimension" && f.n == 2 && f.k <= 10 && tolerance >= 1e-8))
              r.status shouldBe Resolved
            r.status should not be UnsupportedRange
            r.status should not be BudgetExhausted
            r.harmonicsUsed should be <= 256
            r.method shouldBe "fourier"
            if (r.resolved) {
              val distance = r.distance.get
              val error = math.abs(distance-f.expected)
              worstRatio = math.max(worstRatio,error/tolerance)
              val oracleRounding = 8*math.ulp(f.expected)
              error should be <= (tolerance+oracleRounding)
              distance.isFinite shouldBe true
              distance should be >= 0.0
              val (lo,hi) = r.estimatedDistanceInterval.get
              f.expected should be >= (lo-oracleRounding)
              f.expected should be <= (hi+oracleRounding)
              math.max(distance-lo,hi-distance) should be <= tolerance
              r.logCoefficient shouldBe Some(-distance)
            } else {
              r.status shouldBe NumericallyUnresolved
              r.distance shouldBe None
              r.logCoefficient shouldBe None
              r.coefficient shouldBe None
            }
          }
        }
        counts.toVector.sortBy((key,_) => (key._1,key._2,key._3.toString)).foreach { (key,count) =>
          println(s"GVM_RELIABILITY family=${key._1} kappa=${key._2} tolerance=$tolerance status=${key._3} comparisons=$count")
        }
        counts.values.sum shouldBe 192
        println(s"GVM_RELIABILITY_COMPLETE tolerance=$tolerance comparisons=192 worstResolvedErrorRatio=$worstRatio")
      }
    }
    "make tighter tolerance no more permissive and keep a zero budget visibly exhausted" in {
      for (f <- fixtures) {
        val (p,q) = kernels(f)
        val coarse = GaussVonMisesBhattacharyya.compare(p,q,1e-6)
        val fine = GaussVonMisesBhattacharyya.compare(p,q,1e-10)
        withClue(f.id+": ") {
          if (fine.resolved) coarse.resolved shouldBe true
          val empty = GaussVonMisesBhattacharyya.compare(p,q,1e-8,0)
          empty.status shouldBe BudgetExhausted
          empty.distance shouldBe None
          empty.harmonicsUsed shouldBe 0
        }
      }
    }
    "distinguish exact opposed circular shortcuts from tiny varying coupling" in {
      for (k <- Vector(25.0,40.0,50.0)) {
        val variable = fixtures.find(f => f.family == "linear" && f.k == k && f.alpha == math.Pi && f.beta == 1e-5).get
        val (p,q) = kernels(variable.copy(beta=0))
        val exact = GaussVonMisesBhattacharyya.compare(p,q,1e-8,0)
        exact.status shouldBe Resolved
        exact.method shouldBe "constant-angular"
        val (movingP,movingQ) = kernels(variable)
        val moving = GaussVonMisesBhattacharyya.compare(movingP,movingQ,1e-8,512)
        moving.status shouldBe NumericallyUnresolved
        moving.distance shouldBe None
        // A larger harmonic budget does not repair cancellation or silently erase coupling.
        moving.method shouldBe "fourier"
      }
    }
  }
}
