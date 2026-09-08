package com.cra.figaro.test.modernization

import com.cra.figaro.library.atomic.continuous.*
import java.util.concurrent.{Callable, CancellationException, Executors, TimeUnit}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class GaussVonMisesBhattacharyyaTest extends AnyWordSpec with Matchers {
  import GaussVonMisesBhattacharyyaStatus.*
  private def scalar(mu: Double=0, sd: Double=1, alpha: Double=0, beta: Double=0, gamma: Double=0, k: Double=0) =
    GaussVonMisesDistribution(Vector(mu),Vector(Vector(sd*sd)),alpha,Vector(beta),Vector(Vector(gamma)),k)
  private def curved = (scalar(.3,1.1,.2,.7,.3,4.5),scalar(-.4,.8,-.5,-.2,-.15,1.2))
  private def canonical(n: Int,b: Double,g: Double,k: Double) = GaussVonMisesDistribution(Vector.fill(n)(0.0),
    Vector.tabulate(n,n)((i,j) => if(i == j) 1.0 else 0.0),0,Vector.fill(n)(b),
    Vector.tabulate(n,n)((i,j) => if(i == j) g else 0.0),k)
  private def checked(r: GaussVonMisesBhattacharyyaResult, expected: Double, tolerance: Double=1e-8): Unit = {
    withClue(s"${r.status}: ${r.message}; interval=${r.estimatedDistanceInterval}; roundoff=${r.angularRoundoffEstimate}: ") {
      r.status shouldBe Resolved
      r.distance.get shouldBe (expected +- tolerance)
      val (lo,hi)=r.estimatedDistanceInterval.get
      expected should be >= (lo-1e-14); expected should be <= (hi+1e-14)
      r.logCoefficient.get shouldBe -r.distance.get
      r.coefficient.get shouldBe (math.exp(-expected) +- tolerance)
    }
  }

  "Guarded GVM Bhattacharyya comparison" should {
    "preserve identity including kernels outside the nonidentity range" in {
      val p=canonical(33,2,3,1e8)
      checked(GaussVonMisesBhattacharyya.compare(p,p,1e-100,0),0)
      val q=canonical(33,2,3,1e8)
      GaussVonMisesBhattacharyya.compare(p,q).method shouldBe "identity"
      GaussVonMisesBhattacharyya.compare(p,q).estimatedDistanceInterval shouldBe Some((0.0,0.0))
    }
    "reduce to Gaussian distance and one eighth squared Mahalanobis for equal covariance" in {
      val p=scalar(); val q=scalar(mu=2)
      val r=GaussVonMisesBhattacharyya.compare(p,q,maxHarmonics=0)
      checked(r,.5); r.method shouldBe "gaussian"
      checked(GaussVonMisesBhattacharyya.compare(p,scalar(1,2)),1.0/20+math.log(1.25)/2)
      r.distance.get shouldBe (p.klDivergence(q)/4 +- 1e-14)
      // Coupling has no effect if both angles are uniform.
      checked(GaussVonMisesBhattacharyya.compare(scalar(beta=100,gamma=200),scalar(mu=2,beta= -300,gamma=400)),.5)
    }
    "resolve exact circular reductions without cancellation in opposed concentrated directions" in {
      val p=scalar(k=50); val q=scalar(alpha=math.Pi,k=50)
      val r=GaussVonMisesBhattacharyya.compare(p,q,maxHarmonics=0)
      checked(r,47.127575501871804584)
      r.method shouldBe "constant-angular"; r.harmonicsUsed shouldBe 0
      r.angularTruncationBound shouldBe 0
      // The same center difference is constant even when both laws are identically curved.
      checked(GaussVonMisesBhattacharyya.compare(scalar(beta=.7,gamma=.3,k=50),scalar(alpha=math.Pi,beta=.7,gamma=.3,k=50)),r.distance.get)
    }
    "eliminate arbitrary centers when one conditional is uniform and retain tiny concentration limits" in {
      val r=GaussVonMisesBhattacharyya.compare(scalar(beta=1e100,gamma=1e100),scalar(beta= -1e100,gamma=1e100,k=6),maxHarmonics=0)
      val reference= -(3+(-math.log(2*math.Pi)-VonMisesDistribution(0,3).logDensity(0)))+
        (6+(-math.log(2*math.Pi)-VonMisesDistribution(0,6).logDensity(0)))/2
      checked(r,reference); r.method shouldBe "one-uniform"
      checked(GaussVonMisesBhattacharyya.compare(scalar(k=Double.MinPositiveValue),scalar(alpha=1,k=Double.MinPositiveValue)),0)
    }
    "match the independent curved high precision fixture and contain it in the estimated interval" in {
      val (p,q)=curved
      val r=GaussVonMisesBhattacharyya.compare(p,q)
      checked(r,.355509912840583167741149)
      r.method shouldBe "fourier"; r.harmonicsUsed should be > 0
      r.harmonicsUsed should be <= 256
      r.angularAffinityEstimate.get shouldBe (.767698821999035514675116 +- 1e-8)
      r.angularTruncationBound should be >= 0.0
      r.angularRoundoffEstimate should be > 0.0
    }
    "match an independently checked two dimensional curved covariance fixture" in {
      val p=GaussVonMisesDistribution(Vector(0.0,0.0),Vector(Vector(1.0,0.0),Vector(0.0,1.0)),.2,
        Vector(.2,-.1),Vector(Vector(.1,.04),Vector(.04,-.05)),2)
      val q=GaussVonMisesDistribution(Vector(.2,-.1),Vector(Vector(1.21,.11),Vector(.11,.82)),-.1,
        Vector(-.1,.2),Vector(Vector(0.0,0.0),Vector(0.0,.1)),3)
      checked(GaussVonMisesBhattacharyya.compare(p,q),.08735577277717767119340934)
    }
    "resolve six dimensional linear coupling and the eight dimensional determinant branch control" in {
      checked(GaussVonMisesBhattacharyya.compare(canonical(6,.2,0,4),canonical(6,-.2,0,4),1e-6),.2886679589491771038676,1e-6)
      checked(GaussVonMisesBhattacharyya.compare(canonical(8,0,2,2),canonical(8,0,0,2),1e-5),.3565934531071511706222,1e-5)
    }
    "retain symmetry and invariance under common linear unit and angle changes" in {
      val (p,q)=curved
      val forward=GaussVonMisesBhattacharyya.compare(p,q)
      checked(GaussVonMisesBhattacharyya.compare(q,p),forward.distance.get)
      val scaledP=scalar(30,110,.2+.4,.7,.3,4.5)
      val scaledQ=scalar(-40,80,-.5+.4,-.2,-.15,1.2)
      checked(GaussVonMisesBhattacharyya.compare(scaledP,scaledQ),forward.distance.get)
    }
    "preserve large Gaussian separation in log space even when affinity underflows" in {
      val r=GaussVonMisesBhattacharyya.compare(scalar(),scalar(mu=100),1e-8)
      checked(r,1250)
      r.coefficient shouldBe Some(0.0); r.logCoefficient.get shouldBe (-1250.0 +- 1e-10)
    }
    "match high precision curved fixtures through the concentration boundary" in {
      val fixtures=Vector((.01,.09115684433359977981),(1.0,.13273486700380148944),
        (10.0,.57895034312504288118),(50.0,1.02759277991442582445))
      fixtures.foreach { (k,expected) =>
        checked(GaussVonMisesBhattacharyya.compare(scalar(.3,1.1,.2,.7,.3,k),scalar(-.4,.8,-.5,-.2,-.15,k)),expected)
      }
    }
    "recognize accurate Gaussian reductions with identical physical but different canonical conditionals" in {
      val p=scalar(0,1,.2,.4,.3,4)
      val q=scalar(2,1.5,1.6,1.5,.675,4)
      val gaussian=GaussVonMisesBhattacharyya.compare(scalar(0,1),scalar(2,1.5))
      checked(GaussVonMisesBhattacharyya.compare(p,q),gaussian.distance.get)
      val near=GaussVonMisesBhattacharyya.compare(scalar(beta=.3,gamma=.2,k=4),scalar(alpha=1e-9,beta=.3,gamma=.2,k=4))
      checked(near,0)
      near.distance.get should be >= 0.0
    }
    "preflight unsupported ranges and validate requested budgets" in {
      val p=scalar()
      val uniformReference=GaussVonMisesBhattacharyya.compare(p,scalar(k=1)).distance.get
      checked(GaussVonMisesBhattacharyya.compare(canonical(32,0,0,0),canonical(32,0,0,1)),uniformReference)
      for (q <- Vector(scalar(k=50.0001),scalar(k=1e8))) {
        val r=GaussVonMisesBhattacharyya.compare(p,q)
        r.status shouldBe UnsupportedRange; r.distance shouldBe None
      }
      GaussVonMisesBhattacharyya.compare(canonical(33,0,0,0),canonical(33,0,0,1)).status shouldBe UnsupportedRange
      for (t <- Vector(0.0,-1.0,Double.NaN,Double.PositiveInfinity)) {
        intercept[IllegalArgumentException] { GaussVonMisesBhattacharyya.compare(p,p,t) }
      }
      for (b <- Vector(-1,513)) intercept[IllegalArgumentException] { GaussVonMisesBhattacharyya.compare(p,p,maxHarmonics=b) }
      intercept[IllegalArgumentException] { GaussVonMisesBhattacharyya.compare(null,p) }
      intercept[IllegalArgumentException] { GaussVonMisesBhattacharyya.compare(p,canonical(2,0,0,1)) }
    }
    "return no distance or coefficient when harmonic budget or precision is insufficient" in {
      val (p,q)=curved
      val r=GaussVonMisesBhattacharyya.compare(p,q,maxHarmonics=0)
      r.status shouldBe BudgetExhausted; r.harmonicsUsed shouldBe 0
      r.distance shouldBe None; r.coefficient shouldBe None; r.logCoefficient shouldBe None
      val tight=GaussVonMisesBhattacharyya.compare(p,q,1e-30,512)
      tight.status shouldBe NumericallyUnresolved; tight.distance shouldBe None
      val analytic=GaussVonMisesBhattacharyya.compare(scalar(),scalar(mu=1),1e-30)
      analytic.status shouldBe NumericallyUnresolved
    }
    "refuse cancellation dominated almost opposed laws instead of inventing a finite distance" in {
      val r=GaussVonMisesBhattacharyya.compare(scalar(k=50),scalar(alpha=math.Pi,beta=1e-5,k=50))
      r.method shouldBe "fourier"; r.status shouldBe NumericallyUnresolved
      r.distance shouldBe None; r.angularRoundoffEstimate should be > 0.0
    }
    "reject conditioning and phase overflow while preserving extreme but comparable physical units" in {
      val singular=GaussVonMisesDistribution(Vector(0.0,0.0),Vector(Vector(1.0,1-1e-10),Vector(1-1e-10,1.0)),0,
        Vector(0.0,0.0),Vector.fill(2,2)(0.0),0)
      GaussVonMisesBhattacharyya.compare(singular,canonical(2,0,0,0)).status shouldBe NumericallyUnresolved
      val extreme=GaussVonMisesBhattacharyya.compare(scalar(beta=1e200,k=1),scalar(k=2))
      extreme.status shouldBe NumericallyUnresolved; extreme.distance shouldBe None
      checked(GaussVonMisesBhattacharyya.compare(scalar(0,1e-100),scalar(2e-100,1e-100)),.5)
      checked(GaussVonMisesBhattacharyya.compare(scalar(0,1e100),scalar(2e100,1e100)),.5)
    }
    "preserve cancellation flags and support independent concurrent comparisons" in {
      val (p,q)=curved
      Thread.currentThread().interrupt()
      try {
        intercept[CancellationException] { GaussVonMisesBhattacharyya.compare(p,q) }
        intercept[CancellationException] { GaussVonMisesBhattacharyya.compare(p,p) }
        Thread.currentThread().isInterrupted shouldBe true
      } finally { Thread.interrupted() }
      val expected=GaussVonMisesBhattacharyya.compare(p,q)
      val pool=Executors.newFixedThreadPool(4)
      try {
        val tasks=Vector.fill(8)(pool.submit(new Callable[GaussVonMisesBhattacharyyaResult] {
          def call(): GaussVonMisesBhattacharyyaResult=GaussVonMisesBhattacharyya.compare(p,q)
        }))
        tasks.foreach { task =>
          val r=task.get(30,TimeUnit.SECONDS)
          r.status shouldBe expected.status; r.distance shouldBe expected.distance
          r.estimatedDistanceInterval shouldBe expected.estimatedDistanceInterval
        }
      } finally { pool.shutdownNow() }
    }
  }
}
