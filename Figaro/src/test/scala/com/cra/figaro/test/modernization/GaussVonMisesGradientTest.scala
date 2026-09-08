package com.cra.figaro.test.modernization

import com.cra.figaro.library.atomic.continuous.*
import java.util.concurrent.{Callable, CancellationException, Executors, TimeUnit}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class GaussVonMisesGradientTest extends AnyWordSpec with Matchers {
  private def coupled = GaussVonMisesDistribution(Vector(1.0,-2.0),
    Vector(Vector(4.0,1.2),Vector(1.2,2.61)),3.05,Vector(0.7,-0.4),
    Vector(Vector(0.3,0.2),Vector(0.2,-0.5)),4.5)
  private def scalar(k: Double=4.5, b: Double=0, g: Double=0, variance: Double=1) =
    GaussVonMisesDistribution(Vector(0.0),Vector(Vector(variance)),0,Vector(b),Vector(Vector(g)),k)
  private def components(g: GaussVonMisesStateGradient) = g.linear :+ g.angular
  private def perturb(v: LinearAngular, index: Int, offset: Double): LinearAngular =
    if (index == v.linear.size) LinearAngular(v.linear,v.angle+offset)
    else LinearAngular(v.linear.updated(index,v.linear(index)+offset),v.angle)
  private def derivative(f: LinearAngular => Double, v: LinearAngular, index: Int): Double = {
    val h=1e-4
    (f(perturb(v,index,-2*h))-8*f(perturb(v,index,-h))+
      8*f(perturb(v,index,h))-f(perturb(v,index,2*h)))/(12*h)
  }

  "GVM analytic state gradients" should {
    "match independent 80-digit derivatives of the density definition" in {
      // tools/gauss_von_mises_gradient_reference.py: high-precision numerical differentiation.
      val fixtures=Vector(
        (LinearAngular(Vector(1.0,-2.0),3.1),Vector(0.09670969253876256622,-0.05997500312481399455,-0.22490626171805247958)),
        (LinearAngular(Vector(2.0,-1.0),-3.1),Vector(-0.31238592125434287316,-0.21537593057690095534,0.26925519525246606311)),
        (LinearAngular(Vector(-2.0,1.0),1.2),Vector(3.72679567037155270718,-5.90623468428289207164,-3.12967601321216905373)))
      fixtures.foreach { (point,expected) =>
        components(coupled.logDensityGradient(point)).zip(expected).foreach((a,e) => a shouldBe (e +- 2e-13))
      }
    }
    "agree with physical-coordinate finite differences for both log density and squared score" in {
      val p=coupled
      for (v <- Vector(LinearAngular(Vector(2.0,-1.0),-3.1),LinearAngular(Vector(-2.0,1.0),1.2)); i <- 0 to 2) {
        components(p.logDensityGradient(v))(i) shouldBe (derivative(p.logDensity,v,i) +- 2e-9)
        components(p.mahalanobisSquaredGradient(v))(i) shouldBe (derivative(p.mahalanobisSquared,v,i) +- 4e-9)
      }
    }
    "reduce to independent Gaussian and circular derivatives across concentrations" in {
      for (k <- Vector(0.0,Double.MinPositiveValue,0.1,50.0,1e8)) {
        val gradient=scalar(k=k,variance=4).logDensityGradient(LinearAngular(Vector(3.0),0.4))
        gradient.linear.head shouldBe -0.75
        gradient.angular shouldBe (-k*math.sin(0.4) +- math.max(1e-14,k*1e-15))
      }
    }
    "ignore irrelevant extreme coupling at zero concentration" in {
      val gradient=scalar(k=0,b=Double.MaxValue,g=Double.MaxValue).logDensityGradient(LinearAngular(Vector(10.0),2))
      gradient.linear shouldBe Vector(-10.0); gradient.angular shouldBe 0.0
    }
    "remain continuous across the angular branch cut and periodic in the state angle" in {
      val p=coupled; val x=Vector(2.0,-1.0)
      val left=components(p.logDensityGradient(LinearAngular(x,math.Pi-1e-9)))
      val right=components(p.logDensityGradient(LinearAngular(x,-math.Pi+1e-9)))
      left.zip(right).foreach((a,b) => a shouldBe (b +- 2e-8))
      val v=LinearAngular(x,math.Pi)
      val gradient=components(p.logDensityGradient(v))
      gradient.last shouldBe (derivative(p.logDensity,v,2) +- 2e-9)
      components(p.logDensityGradient(LinearAngular(x,math.Pi+8*math.Pi))).zip(gradient)
        .foreach((a,b) => a shouldBe (b +- 1e-13))
    }
    "vanish at the joint mode and keep derivatives unwrapped" in {
      val p=coupled
      components(p.logDensityGradient(LinearAngular(p.mean,p.alpha))).foreach(_ shouldBe (0.0 +- 1e-14))
      scalar(k=50).logDensityGradient(LinearAngular(Vector(0.0),math.Pi/2)).angular shouldBe -50.0
    }
    "obey the covector unit transformation rather than returning whitened derivatives" in {
      val p=coupled; val scale=Vector(1000.0,0.01)
      val q=GaussVonMisesDistribution(p.mean.zip(scale).map(_*_ ),
        Vector.tabulate(2,2)((i,j) => p.covariance(i)(j)*scale(i)*scale(j)),
        p.alpha,p.beta,p.gamma,p.kappa)
      val point=LinearAngular(Vector(2.0,-1.0),-3.1)
      val gp=p.logDensityGradient(point)
      val gq=q.logDensityGradient(LinearAngular(point.linear.zip(scale).map(_*_),point.angle))
      for (i <- 0 until 2) gq.linear(i) shouldBe (gp.linear(i)/scale(i) +- 1e-11)
      gq.angular shouldBe (gp.angular +- 1e-13)
    }
    "satisfy the expected zero score identity under independent known-kernel draws" in {
      val p=coupled; val rng=new scala.util.Random(82612L); val size=80000
      val sum=Array.fill(3)(0.0); val squared=Array.fill(3)(0.0)
      for (_ <- 0 until size) {
        val values=components(p.logDensityGradient(p.sample(rng)))
        for (i <- 0 until 3) { sum(i) += values(i); squared(i) += values(i)*values(i) }
      }
      for (i <- 0 until 3) {
        val mean=sum(i)/size; val variance=(squared(i)-sum(i)*sum(i)/size)/(size-1)
        mean shouldBe (0.0 +- 5*math.sqrt(variance/size))
      }
    }
    "retain finite tail derivatives without exponentiating the density and reject numeric overflow" in {
      val p=scalar(k=0); val v=LinearAngular(Vector(1e200),0)
      p.logDensity(v) shouldBe Double.NegativeInfinity
      p.logDensityGradient(v).linear shouldBe Vector(-1e200)
      p.mahalanobisSquaredGradient(v).linear shouldBe Vector(2e200)
      intercept[ArithmeticException] { scalar(k=0,variance=1e-300).logDensityGradient(LinearAngular(Vector(1e150),0)) }
      intercept[ArithmeticException] { p.mahalanobisSquaredGradient(LinearAngular(Vector(Double.MaxValue),0)) }
      intercept[ArithmeticException] { scalar(b=Double.MaxValue).logDensityGradient(LinearAngular(Vector(2.0),1)) }
    }
    "reject null or mismatched states and preserve interruption" in {
      val p=coupled
      intercept[IllegalArgumentException] { p.logDensityGradient(null) }
      intercept[IllegalArgumentException] { p.mahalanobisSquaredGradient(LinearAngular(Vector(0.0),0)) }
      Thread.currentThread().interrupt()
      try {
        intercept[CancellationException] { p.logDensityGradient(LinearAngular(p.mean,p.alpha)) }
        intercept[CancellationException] { p.mahalanobisSquaredGradient(LinearAngular(p.mean,p.alpha)) }
        Thread.currentThread().isInterrupted shouldBe true
      } finally { Thread.interrupted() }
    }
    "support concurrent reuse and leave previously returned results unchanged" in {
      val p=coupled; val point=LinearAngular(Vector(2.0,-1.0),-3.1)
      val first=p.logDensityGradient(point); val expected=components(first)
      val pool=Executors.newFixedThreadPool(4)
      try {
        val tasks=Vector.fill(8)(pool.submit(new Callable[Vector[Double]] {
          def call(): Vector[Double]=components(p.logDensityGradient(point))
        }))
        tasks.foreach(_.get(30,TimeUnit.SECONDS) shouldBe expected)
      } finally { pool.shutdownNow() }
      p.logDensityGradient(LinearAngular(Vector(0.0,0.0),0))
      components(first) shouldBe expected
    }
  }
}
