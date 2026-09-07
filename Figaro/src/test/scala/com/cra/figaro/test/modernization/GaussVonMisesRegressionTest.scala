package com.cra.figaro.test.modernization

import com.cra.figaro.algorithm.sampling.{Importance, MetropolisHastings, ProposalScheme}
import com.cra.figaro.algorithm.sampling.parallel.{MultiChainMetropolisHastings as MC, ParImportance}
import com.cra.figaro.language.*
import com.cra.figaro.library.atomic.continuous.*
import com.cra.figaro.util.{CircularStatistics as Circular, withRandomSeed}
import java.util.concurrent.CancellationException
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class GaussVonMisesRegressionTest extends AnyWordSpec with Matchers {
  private def kernel(k: Double = 4.5) = GaussVonMisesDistribution(Vector(1.0, -2.0),
    Vector(Vector(4.0, 1.2), Vector(1.2, 2.61)), 3.05, Vector(0.7, -0.4),
    Vector(Vector(0.3, 0.2), Vector(0.2, -0.5)), k)
  private def scalar(k: Double = 4.5, b: Double = 0.7, g: Double = 0.4) =
    GaussVonMisesDistribution(Vector(0.0), Vector(Vector(1.0)), 3.0, Vector(b), Vector(Vector(g)), k)
  private def directCenter(x: Vector[Double]): Double = {
    val z0 = (x(0)-1)/2
    val z1 = (x(1)+2-0.6*z0)/1.5
    3.05 + 0.7*z0 - 0.4*z1 + 0.15*z0*z0 + 0.2*z0*z1 - 0.25*z1*z1
  }

  "The joint Gauss-von Mises kernel" should {
    "match independent 80-digit non-diagonal, nonzero-quadratic fixtures" in {
      // mpmath 1.3.0; tools/gauss_von_mises_reference.py uses explicit inverse/determinant.
      val rows = Vector(
        (Vector(1.0,-2.0),3.1,3.05,-2.936489355077455175,-3.141114657484356359),
        (Vector(2.0,-1.0),-3.1,3.24305555555555556,-3.170378243966344064,-3.377442314331439916),
        (Vector(-2.0,1.0),1.2,-1.1725,-7.441489355077455175,-15.37392987342573247))
      val d = kernel()
      for ((x,a,c,l,j) <- rows) {
        Circular.difference(d.conditionalLocation(x), c) shouldBe (0.0 +- 2e-14)
        d.linearLogDensity(x) shouldBe (l +- 2e-14)
        d.logDensity(LinearAngular(x,a)) shouldBe (j +- 5e-14)
        d.logDensity(LinearAngular(x,a + 2*math.Pi)) shouldBe (j +- 5e-14)
      }
    }
    "normalize the coupled joint density by independent two-dimensional quadrature" in {
      val d = scalar()
      val nx = 600; val na = 400
      val dx = 16.0/nx; val da = 2*math.Pi/na
      var total = 0.0
      for (i <- 0 until nx; j <- 0 until na)
        total += d.density(LinearAngular(Vector(-8+(i+0.5)*dx), -math.Pi+(j+0.5)*da))
      total*dx*da shouldBe (1.0 +- 1e-11)
    }
    "reduce to independent Gaussian and circular factors and to uniform angles" in {
      for (k <- Vector(0.0, 0.1, 4.5, 1e8)) {
        val d = scalar(k, 0.0, 0.0)
        val p = LinearAngular(Vector(0.25), 3.0)
        val gaussian = -0.5*math.log(2*math.Pi)-0.5*0.25*0.25
        d.logDensity(p) shouldBe (gaussian + VonMisesDistribution(3.0,k).logDensity(p.angle) +- 1e-13)
      }
      scalar(0.0).logDensity(LinearAngular(Vector(0.25), 0.0)) shouldBe
        scalar(0.0, 0.0, 0.0).logDensity(LinearAngular(Vector(0.25), 2.0))
    }
    "recover Gaussian moments and independent circular residuals including concentrated angles" in {
      for (k <- Vector(0.0, 4.5, 1e8)) {
        val d = kernel(k); val rng = new scala.util.Random(718L)
        val samples = Vector.fill(40000)(d.sample(rng))
        val zs = samples.map { p =>
          val z0 = (p.linear(0)-1)/2
          Vector(z0, (p.linear(1)+2-0.6*z0)/1.5)
        }
        for (i <- 0 until 2) {
          zs.map(_(i)).sum/zs.size shouldBe (0.0 +- 0.022)
          zs.map(z => z(i)*z(i)).sum/zs.size shouldBe (1.0 +- 0.035)
        }
        zs.map(z => z(0)*z(1)).sum/zs.size shouldBe (0.0 +- 0.025)
        val residuals = samples.map(p => Circular.difference(p.angle, directCenter(p.linear)))
        residuals.map(math.sin).sum/residuals.size shouldBe (0.0 +- 0.02)
        val expectedR = if (k == 0) 0.0 else if (k == 4.5) 0.88033130048989837 else 0.999999995
        residuals.map(math.cos).sum/residuals.size shouldBe (expectedR +- 0.02)
        zs.zip(residuals).map((z,r) => z(0)*math.sin(r)).sum/zs.size shouldBe (0.0 +- 0.025)
        samples.forall(p => p.angle >= -math.Pi && p.angle < math.Pi) shouldBe true
        if (k == 1e8) residuals.map(r => k*r*r).sum/residuals.size shouldBe (1.0 +- 0.04)
      }
    }
    "support disparate covariance units without an absolute pivot cutoff" in {
      val d = GaussVonMisesDistribution(Vector(0.0,0.0),
        Vector(Vector(1e-280,0.0),Vector(0.0,1e280)),0.0,Vector(0.0,0.0),
        Vector(Vector(0.0,0.0),Vector(0.0,0.0)),0.0)
      d.linearLogDensity(Vector(1e-140,1e140)) shouldBe (-math.log(2*math.Pi)-1 +- 1e-12)
      d.sample(new scala.util.Random(7L)).linear.forall(_.isFinite) shouldBe true
    }
    "snapshot mutable inputs and leave earlier draws unchanged" in {
      import scala.collection.mutable.ArrayBuffer
      val mu = ArrayBuffer(0.0); val p = ArrayBuffer(ArrayBuffer(1.0))
      val b = ArrayBuffer(0.7); val g = ArrayBuffer(ArrayBuffer(0.4))
      val d = GaussVonMisesDistribution(mu,p,3.0,b,g,4.5)
      val expected = d.logDensity(LinearAngular(Vector(0.5),1.0))
      mu(0)=100; p(0)(0)=200; b(0)=50; g(0)(0)=70
      d.logDensity(LinearAngular(Vector(0.5),1.0)) shouldBe expected
      val rng = new scala.util.Random(19L); val first = d.sample(rng); val copy = first.copy()
      Vector.fill(100)(d.sample(rng))
      first shouldBe copy
      Vector.fill(10)(d.sample(new scala.util.Random(5L))).distinct.size shouldBe 1
    }
    "reject malformed dimensions, nonfinite values, asymmetry and invalid covariance" in {
      intercept[IllegalArgumentException](GaussVonMisesDistribution(Vector.empty,Vector.empty,0,Vector.empty,Vector.empty,1))
      for (p <- Vector(Vector(Vector(0.0)),Vector(Vector(-1.0)),Vector(Vector(Double.NaN))))
        intercept[IllegalArgumentException](GaussVonMisesDistribution(Vector(0.0),p,0,Vector(0.0),Vector(Vector(0.0)),1))
      for (p <- Vector(Vector(Vector(1.0,2.0),Vector(2.0,1.0)),Vector(Vector(1.0,1.0),Vector(1.0,1.0)),
        Vector(Vector(1.0,0.2),Vector(0.3,1.0))))
        intercept[IllegalArgumentException](GaussVonMisesDistribution(Vector(0.0,0.0),p,0,Vector(0.0,0.0),p,1))
      intercept[IllegalArgumentException](GaussVonMisesDistribution(Vector(0.0),Vector(Vector(1.0)),0,Vector.empty,Vector(Vector(0.0)),1))
      intercept[IllegalArgumentException](GaussVonMisesDistribution(Vector(0.0),Vector(Vector(1.0)),0,Vector(0.0),Vector(Vector(Double.PositiveInfinity)),1))
      intercept[IllegalArgumentException](GaussVonMisesDistribution(Vector(0.0,0.0),Vector(Vector(1.0,0.0),Vector(0.0,1.0)),0,Vector(0.0,0.0),Vector(Vector(0.0,1.0),Vector(0.0,0.0)),1))
      for (k <- Vector(-1.0, Double.NaN, Double.PositiveInfinity, 1e9)) intercept[IllegalArgumentException](scalar(k))
      intercept[IllegalArgumentException](LinearAngular(Vector(0.0),Double.NaN))
      intercept[IllegalArgumentException](LinearAngular(Vector.empty,0.0))
      intercept[IllegalArgumentException](scalar().logDensity(LinearAngular(Vector(0.0,1.0),0.0)))
      intercept[IllegalArgumentException](scalar().sample(null))
      intercept[IllegalArgumentException](scalar().sample(new scala.util.Random(1L),0))
    }
    "fail explicitly on numeric overflow and interruption without clearing the interrupt" in {
      scalar(4.5,0.0,java.lang.Double.MIN_VALUE).conditionalLocation(Vector(1e160)) shouldBe
        (3.0002470328229206233 +- 1e-14)
      intercept[ArithmeticException](scalar(4.5,Double.MaxValue,0).conditionalLocation(Vector(2.0)))
      val d = scalar()
      Thread.currentThread().interrupt()
      try {
        intercept[CancellationException](d.sample(new scala.util.Random(1L)))
        intercept[CancellationException](d.logDensity(LinearAngular(Vector(0.0),0.0)))
        Thread.currentThread().isInterrupted shouldBe true
      } finally Thread.interrupted()
      val reject = new scala.util.Random(1L) {
        override def nextGaussian(): Double = 0.0
        override def nextDouble(): Double = 0.999999
      }
      intercept[IllegalStateException](scalar(4.5).sample(reject,1))
    }
  }

  "The Figaro GVM adapter" should {
    "score complete joint observations in log space even when ordinary density is zero" in {
      val u = Universe.createNew(); val e = GaussVonMises(scalar(1000,0,0))(using "state",u)
      val value = LinearAngular(Vector(0.0),3.0+math.Pi)
      e.density(value) shouldBe 0.0
      e.logp(value).isFinite shouldBe true
      e.observe(value)
      val alg = Importance(100,e)
      try { alg.start(); alg.getTotalWeight shouldBe (math.log(100)+e.logp(value) +- 1e-9) }
      finally { if (alg.isActive) alg.kill(); u.clear() }
    }
    "weight a conditional joint observation against independently enumerated model odds" in {
      withRandomSeed(245L) {
        val u = Universe.createNew(); val choice = Flip(0.4)(using "choice",u)
        val e = NonCachingChain(choice, (b: Boolean) => GaussVonMises(scalar(4.5,if(b) 0.7 else -0.7,0.4))(using "",u))
        val value = LinearAngular(Vector(1.0),3.1)
        e.observe(value)
        val odds = 1.5 * math.exp(4.5*(math.cos(3.1-2.5)-math.cos(3.1-3.9)))
        val alg = Importance(20000,choice)
        try { alg.start(); alg.probability(choice,true) shouldBe (1/(1+odds) +- 0.025) }
        finally { if(alg.isActive) alg.kill(); u.clear() }
      }
    }
    "recover a Gaussian posterior and circular projections through ordinary MH" in {
      // The linear marginal posterior is N(0.5,0.5); integrate its conditional angle independently.
      val xs = (0 until 10000).map(i => -8+(i+0.5)*16/10000)
      val weights = xs.map(x => math.exp(-(x-0.5)*(x-0.5)))
      val r = 0.88033130048989837
      val expectedSin = xs.zip(weights).map((x,w) => w*r*math.sin(3+0.7*x+0.2*x*x)).sum/weights.sum
      withRandomSeed(53L) {
        val u = Universe.createNew(); val e = GaussVonMises(scalar())(using "state",u)
        e.addLogConstraint((p: LinearAngular) => -0.5*math.pow(p.linear(0)-1,2))
        val alg = MetropolisHastings(30000,ProposalScheme.default(using u),1000,e)
        try {
          alg.start()
          alg.expectation(e,(p: LinearAngular) => p.linear(0)) shouldBe (0.5 +- 0.035)
          alg.expectation(e,(p: LinearAngular) => math.sin(p.angle)) shouldBe (expectedSin +- 0.035)
        } finally { if(alg.isActive) alg.kill(); u.clear() }
      }
    }
    "share only immutable kernels across isolated parallel inference models" in {
      val d = scalar()
      val alg = ParImportance.seeded(() => {
        val u = Universe.createNew(); val e = GaussVonMises(d)(using "state",u)
        Apply(e,(p: LinearAngular) => p.linear(0)*p.linear(0))(using "square",u)
        u
      },2,20000,818L,"square")
      try { alg.start(); alg.expectation[Double]("square",identity) shouldBe (1.0 +- 0.05) }
      finally if(alg.isActive) alg.kill()
      val config = MC.Config(chains=2,drawsPerChain=1500,warmUp=100,parallelism=1)
      def model(u: Universe,i: Int): MC.Model = {
        val e = GaussVonMises(d)(using "state",u)
        MC.Model(Vector(MC.Observable("x",e)(_.linear(0)),MC.Observable("sin",e)(p => math.sin(p.angle))))
      }
      val serial = MC.run(config)(model)
      val parallel = MC.run(config.copy(parallelism=2))(model)
      serial.chains.map(_.draws) shouldBe parallel.chains.map(_.draws)
      parallel.chains.map(_.draws).distinct.size shouldBe 2
    }
    "reject null kernels without registering elements and preserve seeded generation" in {
      val u = new Universe
      try {
        val before = u.activeElements.size
        intercept[IllegalArgumentException](GaussVonMises(null)(using "bad",u))
        u.activeElements.size shouldBe before
        val e = GaussVonMises(kernel())(using "state",u)
        withRandomSeed(91L)(e.generateRandomness()) shouldBe withRandomSeed(91L)(e.generateRandomness())
      } finally u.clear()
    }
  }
}
