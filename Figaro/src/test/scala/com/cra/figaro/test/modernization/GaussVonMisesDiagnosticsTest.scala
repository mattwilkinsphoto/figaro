package com.cra.figaro.test.modernization

import com.cra.figaro.library.atomic.continuous.*
import com.cra.figaro.util.CircularStatistics
import java.util.concurrent.{Callable, CancellationException, Executors, TimeUnit}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class GaussVonMisesDiagnosticsTest extends AnyWordSpec with Matchers {
  private def scalar(mu: Double = 0, sd: Double = 1, a: Double = 0, b: Double = 0,
    g: Double = 0, k: Double = 4) = GaussVonMisesDistribution(Vector(mu),
    Vector(Vector(sd*sd)), a, Vector(b), Vector(Vector(g)), k)
  private def coupled = GaussVonMisesDistribution(Vector(1.0,-2.0),
    Vector(Vector(4.0,1.2),Vector(1.2,2.61)),3.05,Vector(0.7,-0.4),
    Vector(Vector(0.3,0.2),Vector(0.2,-0.5)),4.5)

  "GVM canonical and Mahalanobis diagnostics" should {
    "round-trip non-diagonal, coupled states across the angular boundary" in {
      val p = coupled
      for (x <- Vector(Vector(1.0,-2.0),Vector(-2.0,1.0),Vector(2.0,-1.0)); a <- Vector(-3.1,3.1,12.0)) {
        val state = LinearAngular(x,a)
        val residual = p.canonicalResidual(state)
        val restored = p.fromCanonical(residual)
        restored.linear.zip(x).foreach((actual,expected) => actual shouldBe (expected +- 1e-14))
        CircularStatistics.difference(restored.angle,a) shouldBe (0.0 +- 1e-14)
        val mode = LinearAngular(p.mean,p.alpha)
        p.mahalanobisSquared(state) shouldBe (2*(p.logDensity(mode)-p.logDensity(state)) +- 2e-13)
      }
      p.mahalanobisSquared(LinearAngular(p.mean,p.alpha)) shouldBe 0.0
    }
    "ignore irrelevant angular coupling in uniform scores and retain tail infinity" in {
      val p = scalar(b=Double.MaxValue,k=0)
      p.mahalanobisSquared(LinearAngular(Vector(2.0),1.0)) shouldBe 4.0
      scalar(k=0).mahalanobisSquared(LinearAngular(Vector(1e200),1.0)) shouldBe Double.PositiveInfinity
      // Canonical coordinates still have a defined parameter-dependent center at kappa=0.
      intercept[ArithmeticException](p.canonicalResidual(LinearAngular(Vector(2.0),1.0)))
    }
  }

  "Analytic GVM KL" should {
    "reduce exactly to half squared Mahalanobis for shared covariance and angular law" in {
      val p = scalar(mu=1,sd=2,a=3,k=7)
      val q = scalar(mu=5,sd=2,a=3,k=7)
      val result = p.klDivergenceComponents(q)
      result.gaussian shouldBe 2.0
      result.conditionalAngular shouldBe 0.0
      2*result.total shouldBe q.mahalanobisSquared(LinearAngular(p.mean,3.0))
      p.klDivergence(q) shouldBe q.klDivergence(p)
    }
    "retain covariance divergence, directionality, and zero for identical laws" in {
      val p = scalar(sd=2); val q = scalar(mu=1,sd=3)
      p.klDivergence(q) shouldBe (0.5*(4.0/9+1.0/9-1+math.log(9.0/4)) +- 1e-14)
      p.klDivergence(q) should not be q.klDivergence(p)
      coupled.klDivergence(coupled) shouldBe (0.0 +- 1e-28)
      scalar(b=7,g=3,k=0).klDivergence(scalar(a=2,b = -1,g=5,k=0)) shouldBe 0.0
    }
    "reduce to circular KL and preserve tiny angular separations at high concentration" in {
      for (k <- Vector(0.0,0.01,4.0,50.0,1e8); delta <- Vector(1e-9,0.1,math.Pi)) {
        val p = scalar(k=k); val q = scalar(a=delta,k=k)
        val sine = math.sin(delta/2)
        val expected = 2*k*VonMisesDistribution(0,k).meanResultantLength*sine*sine
        p.klDivergence(q) shouldBe (expected +- math.max(1e-28,math.abs(expected)*2e-14))
      }
      val local = scalar(k=1e8).klDivergence(scalar(a=1e-5,k=1e8))
      local shouldBe (0.5*1e8*1e-10 +- 1e-10)
    }
    "match independent direct joint-density quadrature with all scalar parameters changed" in {
      val p = scalar(0.3,1.2,2.8,0.7,0.6,3.0)
      val q = scalar(-0.4,0.9,-2.9,-0.2,-0.3,1.2)
      p.klDivergence(q) shouldBe (1.05497841734648 +- 2e-13)
      val nx=600; val nt=400; val dx=24.0/nx; val dt=2*math.Pi/nt
      var integral=0.0
      for (i <- 0 until nx; j <- 0 until nt) {
        val state=LinearAngular(Vector(-12+(i+0.5)*dx),-math.Pi+(j+0.5)*dt)
        val lp=p.logDensity(state)
        integral += math.exp(lp)*(lp-q.logDensity(state))*dx*dt
      }
      p.klDivergence(q) shouldBe (integral +- 2e-11)
    }
    "use the continuous determinant branch in higher-dimensional quadratic cases" in {
      val n=8
      def p(g: Double) = GaussVonMisesDistribution(Vector.fill(n)(0.0),
        Vector.tabulate(n,n)((i,j) => if(i==j) 1.0 else 0.0),0,
        Vector.fill(n)(0.0),Vector.tabulate(n,n)((i,j) => if(i==j) g else 0.0),3)
      // Product of n independent scalar characteristic factors, not sqrt of their determinant.
      // For n=8, g=1 the true expectation is -1/4; the principal determinant root gives +1/4.
      val expectedCos=math.pow(2.0,-n/4.0)*math.cos(n*0.5*math.atan(1.0))
      p(1).klDivergence(p(0)) shouldBe
        (3*VonMisesDistribution(0,3).meanResultantLength*(1-expectedCos) +- 1e-13)
    }
    "match independent 80-digit non-diagonal and concentration fixtures" in {
      // tools/gauss_von_mises_kl_reference.py: explicit known factors and complex arithmetic.
      val q=GaussVonMisesDistribution(Vector(-0.4,0.8),
        Vector(Vector(0.64,-0.24),Vector(-0.24,1.30)),-2.9,Vector(-0.2,0.6),
        Vector(Vector(-0.3,0.15),Vector(0.15,0.25)),1.2)
      val result=coupled.klDivergenceComponents(q)
      result.gaussian shouldBe (6.2513435960203525203 +- 2e-14)
      result.conditionalAngular shouldBe (1.348485675228587497 +- 2e-14)
      result.total shouldBe (7.5998292712489400173 +- 3e-14)
      for ((p,k,expected,tol) <- Vector(
        (0.0,1e-6,2.49999999999984375e-13,1e-15),
        (4.0,4.000001,1.92240200727090677e-14,2e-15),
        (50.0,51.0,0.00009969723977665537,2e-15),
        (1e8,99990000.0,2.50016669166891706e-9,1e-22),
        (1e8,1e6,1.807585215506608165,2e-13),
        (0.0,1e8,99999989.870721096,2e-8))) {
        scalar(k=p).klDivergence(scalar(k=k)) shouldBe (expected +- tol)
      }
    }
    "retain small linear and quadratic coupling and covariance perturbations" in {
      val p=scalar(); val q=scalar(b=1e-9)
      val r=VonMisesDistribution(0,4).meanResultantLength
      p.klDivergence(q) shouldBe (4*r*5e-19 +- 1e-32)
      val g=1e-8
      p.klDivergence(scalar(g=g)) shouldBe (4*r*3*g*g/8 +- 1e-29)
      val scale=1+1e-8
      val gaussian=scalar(sd=scale).klDivergence(p)
      gaussian shouldBe (math.pow(scale-1,2) +- 2e-23)
    }
    "share immutable kernels safely between concurrent diagnostics" in {
      val p=coupled; val q=GaussVonMisesDistribution(p.mean,p.covariance,1.0,p.beta,p.gamma,2)
      val expected=p.klDivergence(q)
      val pool=Executors.newFixedThreadPool(2)
      try {
        val jobs=Vector.fill(12)(pool.submit(new Callable[Double] { def call(): Double = p.klDivergence(q) }))
        jobs.foreach(_.get(10,TimeUnit.SECONDS) shouldBe expected)
      } finally { pool.shutdownNow(); pool.awaitTermination(10,TimeUnit.SECONDS) }
    }
    "recognize identical physical conditionals expressed in different whitened coordinates" in {
      val p=scalar(b=0.5,g=0.2)
      val q=scalar(mu=1,sd=2,a=0.6,b=1.4,g=0.8)
      p.klDivergenceComponents(q).conditionalAngular shouldBe (0.0 +- 1e-28)
      p.klDivergence(q) shouldBe (scalar(k=0).klDivergence(scalar(mu=1,sd=2,k=0)) +- 1e-14)
    }
    "preserve KL under common linear unit changes and angular rotations" in {
      val p=coupled
      val q=GaussVonMisesDistribution(Vector(-0.4,0.8),
        Vector(Vector(0.64,-0.24),Vector(-0.24,1.30)),-2.9,Vector(-0.2,0.6),
        Vector(Vector(-0.3,0.15),Vector(0.15,0.25)),1.2)
      def transform(k: GaussVonMisesDistribution): GaussVonMisesDistribution = {
        val scale=Vector(1e-80,1e80)
        GaussVonMisesDistribution(k.mean.zip(scale).map((v,s) => v*s),
          Vector.tabulate(2,2)((i,j) => k.covariance(i)(j)*(scale(i)*scale(j))),
          k.alpha+2.0,k.beta,k.gamma,k.kappa)
      }
      transform(p).klDivergence(transform(q)) shouldBe (p.klDivergence(q) +- 5e-13)
    }
    "reject invalid comparisons and numeric overflow, preserving cancellation" in {
      val p=scalar()
      intercept[IllegalArgumentException](p.klDivergence(null))
      intercept[IllegalArgumentException](p.klDivergence(coupled))
      intercept[IllegalArgumentException](p.canonicalResidual(null))
      intercept[IllegalArgumentException](p.fromCanonical(LinearAngular(Vector(1.0,2.0),0)))
      intercept[IllegalArgumentException](GaussVonMisesKL(-1,0))
      intercept[ArithmeticException](scalar(mu=Double.MaxValue).klDivergence(scalar(mu = -Double.MaxValue)))
      Thread.currentThread().interrupt()
      try {
        intercept[CancellationException](p.klDivergence(p))
        intercept[CancellationException](p.fromCanonical(LinearAngular(Vector(0.0),0)))
        Thread.currentThread().isInterrupted shouldBe true
      } finally Thread.interrupted()
    }
  }
}
