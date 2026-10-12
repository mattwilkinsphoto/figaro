package com.cra.figaro.test.modernization

import com.cra.figaro.library.atomic.continuous.*
import com.cra.figaro.library.atomic.discrete.HypergeometricDistribution
import com.cra.figaro.util.SamplingRandom
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class NumericalRangeIntegrationTest extends AnyWordSpec with Matchers {
  "Expanded numerical ranges" should {
    "match a Taylor-series CDF derivative oracle near zero extreme-value shape" in {
      for(s <- Vector(-1e-8,-1e-10,-1e-12,-1e-14,1e-14,1e-12,1e-10,1e-8); z <- Vector(.01,.5,2.0,10.0)) {
        // Independent truncation of log(1+s*z)/s; omitted term <= 1e-26 here.
        val a=z-s*z*z/2+s*s*z*z*z/3-s*s*s*math.pow(z,4)/4
        val gev=GeneralizedExtremeValueDistribution(s)
        val gpd=GeneralizedParetoDistribution(s)
        gev.logDensity(z) shouldBe (-(1+s)*a-math.exp(-a) +- 2e-14)
        gpd.logDensity(z) shouldBe (-(1+s)*a +- 2e-14)
        gev.cdf(z) shouldBe (math.exp(-math.exp(-a)) +- 2e-15)
        gpd.survival(z) shouldBe (math.exp(-a) +- 2e-15)
        for(p <- Vector(1e-8,.2,.5,.99,1-1e-8); law <- Vector[ScalarDistribution](gev,gpd))
          law.cdf(law.quantile(p)) shouldBe (p +- 3e-15)
      }
    }
    "match exact integer-combinatorial hypergeometric masses at large populations" in {
      def choose(n: Int,k: Int): BigInt = (1 to k).foldLeft(BigInt(1))((v,i) => v*(n-i+1)/i)
      for(n <- Vector(1000000,1000000000); k <- Vector(2,n/3,n-2)) {
        val law=HypergeometricDistribution(n,k,12)
        val denominator=BigDecimal(choose(n,12))
        for(x <- law.support._1 to law.support._2.get) {
          val expected=(BigDecimal(choose(k,x)*choose(n-k,12-x))/denominator).toDouble
          law.probability(x) shouldBe (expected +- math.max(1e-25,expected*1e-10))
        }
        val total=(law.support._1 to law.support._2.get).map(law.probability).sum
        total shouldBe (1.0 +- 1e-10)
        for(p <- Vector(.01,.5,.99)) {
          val q=law.quantile(p)
          law.cdf(q) should be >= p
          law.cdf(q-1) should be < p
          law.cdf(q)+law.survival(q) shouldBe (1.0 +- 1e-10)
        }
      }
      HypergeometricDistribution(1000000000,1000000000,1000000000).support shouldBe (1000000000,Some(1000000000))
      intercept[IllegalArgumentException](HypergeometricDistribution(1000000,500000,500000))
    }
    "reduce the new scalar matrix boundary to chi-square and reciprocal chi-square densities" in {
      val w=WishartDistribution(1,Vector(Vector(1.0)))
      val iw=InverseWishartDistribution(1,Vector(Vector(1.0)))
      iw.mean shouldBe None
      for(x <- Vector(.001,.1,1.0,10.0,1000.0)) {
        val chi= -.5*math.log(2*math.Pi)-.5*math.log(x)-x/2
        w.logDensity(Vector(Vector(x))) shouldBe (chi +- 1e-12)
        val reciprocal= -.5*math.log(2*math.Pi)-1.5*math.log(x)-1/(2*x)
        iw.logDensity(Vector(Vector(x))) shouldBe (reciprocal +- 1e-12)
      }
      val rng=SamplingRandom.scalaRandom(456)
      val draws=Vector.fill(20000)(w.sample(rng)(0)(0))
      draws.sum/draws.size shouldBe (1.0 +- .04)
      val inverseDraws=Vector.fill(20000)(1/iw.sample(rng)(0)(0))
      inverseDraws.sum/inverseDraws.size shouldBe (1.0 +- .04)
    }
    "validate finite matrix boundary draws and retain singularity refusals" in {
      val eye=Vector(Vector(1.0,0.0),Vector(0.0,1.0))
      val rng=SamplingRandom.scalaRandom(467)
      val w=WishartDistribution(2,eye); val iw=InverseWishartDistribution(2,eye)
      for(i <- 0 until 500) {
        w.logDensity(w.sample(rng)).isFinite shouldBe true
        iw.logDensity(iw.sample(rng)).isFinite shouldBe true
      }
      val singular=Vector(Vector(1.0,1.0),Vector(1.0,1.0))
      intercept[IllegalArgumentException](w.logDensity(singular))
      intercept[IllegalArgumentException](iw.logDensity(singular))
      intercept[IllegalArgumentException](WishartDistribution(1.99,eye))
      intercept[IllegalArgumentException](InverseWishartDistribution(1.99,eye))
    }
  }
}
