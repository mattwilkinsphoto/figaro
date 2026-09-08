package com.cra.figaro.test.modernization

import com.cra.figaro.library.atomic.continuous.*
import com.cra.figaro.util.CircularStatistics
import java.util.concurrent.{Callable, CancellationException, Executors, TimeUnit}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class GaussVonMisesMomentsTest extends AnyWordSpec with Matchers {
  private def scalar(mu: Double = 0, sd: Double = 1, a: Double = 0, b: Double = 0,
    g: Double = 0, k: Double = 4) = GaussVonMisesDistribution(Vector(mu),Vector(Vector(sd*sd)),
    a,Vector(b),Vector(Vector(g)),k)
  private def coupled = GaussVonMisesDistribution(Vector(1.0,-2.0),
    Vector(Vector(4.0,1.2),Vector(1.2,2.61)),3.05,Vector(0.7,-0.4),
    Vector(Vector(0.3,0.2),Vector(0.2,-0.5)),4.5)

  "Analytic GVM moments" should {
    "match independent 80-digit complex-matrix fixtures in physical coordinates" in {
      // tools/gauss_von_mises_moments_reference.py uses explicit inverse and a known factor.
      val m=coupled.moments
      m.meanCos shouldBe (-0.60259699719808070946 +- 2e-14)
      m.meanSin shouldBe (0.10030458123809611658 +- 2e-14)
      m.meanResultantLength shouldBe (0.61088800123221729054 +- 2e-14)
      m.logMeanResultantLength shouldBe (-0.49284164065170462039 +- 2e-14)
      m.meanDirection.get shouldBe (2.97665106262550928546 +- 2e-14)
      m.linearCos.zip(Vector(-0.57474084441486420343,1.499087973484905694)).foreach((a,e) => a shouldBe (e +- 3e-14))
      m.linearSin.zip(Vector(-0.658831133469910772,-0.205381822279153060)).foreach((a,e) => a shouldBe (e +- 3e-14))
      val cos=Vector(Vector(-1.873219165229256660,0.603877767936065582),Vector(0.603877767936065582,-4.953309303546819785))
      val sin=Vector(Vector(-1.493409600182479284,1.295391697777483188),Vector(1.295391697777483188,0.896231840893922005))
      for(i <- 0 until 2; j <- 0 until 2) {
        m.linearLinearCos(i)(j) shouldBe (cos(i)(j) +- 8e-14)
        m.linearLinearSin(i)(j) shouldBe (sin(i)(j) +- 8e-14)
      }
    }
    "factor correctly for independent Gaussian and circular components across concentrations" in {
      for (k <- Vector(0.0,1e-6,4.5,50.0,1e8)) {
        val p=scalar(mu=2,sd=3,a=0.7,k=k); val m=p.moments
        val r=VonMisesDistribution(0.7,k).meanResultantLength
        m.meanCos shouldBe (r*math.cos(0.7) +- 1e-14)
        m.meanSin shouldBe (r*math.sin(0.7) +- 1e-14)
        m.meanResultantLength shouldBe (r +- 1e-14)
        m.linearCos.head shouldBe (2*m.meanCos +- 1e-14)
        m.linearSin.head shouldBe (2*m.meanSin +- 1e-14)
        m.linearLinearCos.head.head shouldBe (13*m.meanCos +- 1e-13)
        m.linearLinearSin.head.head shouldBe (13*m.meanSin +- 1e-13)
        if (k == 0) m.meanDirection shouldBe None else m.meanDirection.get shouldBe (0.7 +- 1e-14)
      }
    }
    "make every first-harmonic moment zero for uniform angles despite extreme coupling" in {
      val m=scalar(mu=Double.MaxValue,b=Double.MaxValue,g=Double.MaxValue,k=0).moments
      m.meanCos shouldBe 0.0; m.meanSin shouldBe 0.0
      m.meanResultantLength shouldBe 0.0; m.logMeanResultantLength shouldBe Double.NegativeInfinity
      m.meanDirection shouldBe None
      m.linearCos shouldBe Vector(0.0); m.linearSin shouldBe Vector(0.0)
      m.linearLinearCos shouldBe Vector(Vector(0.0)); m.linearLinearSin shouldBe Vector(Vector(0.0))
    }
    "distinguish marginal direction from the center at the Gaussian mean" in {
      val p=scalar(a=3.0,g=1.0); val m=p.moments
      CircularStatistics.difference(m.meanDirection.get,3.0+math.Pi/8) shouldBe (0.0 +- 1e-14)
      m.meanResultantLength shouldBe (VonMisesDistribution(0,4).meanResultantLength/math.pow(2,0.25) +- 1e-14)
      p.conditionalAngle(Vector(0.0)).location shouldBe 3.0
    }
    "recover the linear-coupling signs and zero second-order mixed moment" in {
      val m=scalar(b=1).moments
      val f=VonMisesDistribution(0,4).meanResultantLength*math.exp(-0.5)
      m.meanCos shouldBe (f +- 1e-14); m.meanSin shouldBe 0.0
      m.linearCos.head shouldBe (0.0 +- 1e-14)
      m.linearSin.head shouldBe (f +- 1e-14)
      m.linearLinearCos.head.head shouldBe (0.0 +- 1e-14)
      m.linearLinearSin.head.head shouldBe (0.0 +- 1e-14)
    }
    "match independent direct joint-density integrals for all scalar mixed moments" in {
      val p=scalar(mu=0.3,sd=1.2,a=2.8,b=0.7,g=0.6,k=3)
      val m=p.moments; val sums=Array.fill(6)(0.0)
      val nx=600; val nt=400; val dx=24.0/nx; val dt=2*math.Pi/nt
      for (i <- 0 until nx; j <- 0 until nt) {
        val x = -12+(i+0.5)*dx; val t = -math.Pi+(j+0.5)*dt
        val weight=p.density(LinearAngular(Vector(x),t))*dx*dt
        val terms=Array(math.cos(t),math.sin(t),x*math.cos(t),x*math.sin(t),x*x*math.cos(t),x*x*math.sin(t))
        for (k <- 0 until 6) sums(k) += terms(k)*weight
      }
      val actual=Vector(m.meanCos,m.meanSin,m.linearCos.head,m.linearSin.head,m.linearLinearCos.head.head,m.linearLinearSin.head.head)
      actual.zip(sums).foreach((a,e) => a shouldBe (e +- 2e-11))
    }
    "recover physical-coordinate moments through independent seeded prior samples" in {
      val p=coupled; val m=p.moments; val rng=new scala.util.Random(9187L)
      val draws=Vector.fill(80000)(p.sample(rng))
      def average(f: LinearAngular => Double): Double = draws.map(f).sum/draws.size
      average(v => math.cos(v.angle)) shouldBe (m.meanCos +- 0.012)
      average(v => math.sin(v.angle)) shouldBe (m.meanSin +- 0.012)
      for (i <- 0 until 2) {
        average(v => v.linear(i)*math.cos(v.angle)) shouldBe (m.linearCos(i) +- 0.06)
        average(v => v.linear(i)*math.sin(v.angle)) shouldBe (m.linearSin(i) +- 0.06)
        for (j <- 0 until 2) {
          average(v => v.linear(i)*v.linear(j)*math.cos(v.angle)) shouldBe (m.linearLinearCos(i)(j) +- 0.2)
          average(v => v.linear(i)*v.linear(j)*math.sin(v.angle)) shouldBe (m.linearLinearSin(i)(j) +- 0.2)
        }
      }
    }
    "preserve the continuous characteristic-function branch in eight dimensions" in {
      val n=8; val eye=Vector.tabulate(n,n)((i,j) => if(i==j) 1.0 else 0.0)
      val p=GaussVonMisesDistribution(Vector.fill(n)(0.0),eye,0,Vector.fill(n)(0.0),eye,3)
      val m=p.moments; val r=VonMisesDistribution(0,3).meanResultantLength
      m.meanCos shouldBe (-r/4 +- 1e-14); m.meanSin shouldBe (0.0 +- 1e-14)
      m.meanResultantLength shouldBe (r/4 +- 1e-14)
      m.meanDirection.get shouldBe (-math.Pi +- 1e-14)
      m.linearLinearCos.head.head shouldBe (-r/8 +- 1e-14)
      m.linearLinearSin.head.head shouldBe (-r/8 +- 1e-14)
    }
    "retain a log resultant and representable mixed moments when the angular moment underflows" in {
      val m=scalar(mu=1e100,b=40).moments
      val logF=math.log(VonMisesDistribution(0,4).meanResultantLength)-800
      m.meanResultantLength shouldBe 0.0; m.meanDirection shouldBe None
      m.logMeanResultantLength shouldBe (logF +- 1e-13)
      m.linearCos.head shouldBe (math.exp(logF+math.log(1e100)) +- 1e-260)
      m.linearLinearCos.head.head shouldBe (math.exp(logF+2*math.log(1e100)) +- 1e-160)
    }
    "retain mixed moments for a subnormal concentration whose half is unrepresentable" in {
      val m=scalar(mu=1e100,k=java.lang.Double.MIN_VALUE).moments
      val expectedLog=math.log(java.lang.Double.MIN_VALUE)-math.log(2.0)
      m.logMeanResultantLength shouldBe (expectedLog +- 1e-13)
      m.linearCos.head shouldBe (math.exp(expectedLog+math.log(1e100)) +- 1e-235)
      m.linearLinearCos.head.head shouldBe (math.exp(expectedLog+2*math.log(1e100)) +- 1e-135)
    }
    "transform physical moments consistently under common unit changes and angular rotations" in {
      val p=coupled; val m=p.moments; val scale=Vector(2.0,0.25); val angle=1.3
      val q=GaussVonMisesDistribution(p.mean.zip(scale).map((v,s) => v*s),
        Vector.tabulate(2,2)((i,j) => p.covariance(i)(j)*(scale(i)*scale(j))),
        p.alpha+angle,p.beta,p.gamma,p.kappa).moments
      val c=math.cos(angle); val s=math.sin(angle)
      q.meanCos shouldBe (c*m.meanCos-s*m.meanSin +- 1e-14)
      q.meanSin shouldBe (s*m.meanCos+c*m.meanSin +- 1e-14)
      for(i <- 0 until 2) {
        q.linearCos(i) shouldBe (scale(i)*(c*m.linearCos(i)-s*m.linearSin(i)) +- 1e-13)
        for(j <- 0 until 2) q.linearLinearSin(i)(j) shouldBe
          (scale(i)*scale(j)*(s*m.linearLinearCos(i)(j)+c*m.linearLinearSin(i)(j)) +- 1e-12)
      }
    }
    "share immutable results across threads and fail explicitly on cancellation or overflow" in {
      val p=coupled; val expected=p.moments.linearLinearCos
      val pool=Executors.newFixedThreadPool(2)
      try {
        val jobs=Vector.fill(12)(pool.submit(new Callable[Vector[Vector[Double]]] {
          def call(): Vector[Vector[Double]] = p.moments.linearLinearCos
        }))
        jobs.foreach(_.get(10,TimeUnit.SECONDS) shouldBe expected)
      } finally { pool.shutdownNow(); pool.awaitTermination(10,TimeUnit.SECONDS) }
      intercept[ArithmeticException](scalar(mu=1e200).moments)
      intercept[ArithmeticException](scalar(b=Double.MaxValue).moments)
      val uniform=scalar(k=0)
      Thread.currentThread().interrupt()
      try {
        intercept[CancellationException](p.moments)
        intercept[CancellationException](uniform.moments)
        intercept[CancellationException](p.conditionalAngle(p.mean))
        Thread.currentThread().isInterrupted shouldBe true
      } finally Thread.interrupted()
    }
  }

  "The exact angular conditional helper" should {
    "factor joint log densities including wrapped angles and uniform concentration" in {
      for (k <- Vector(0.0,4.0,1e8)) {
        val p=scalar(mu=0.3,sd=1.2,a=2.8,b=0.7,g=0.6,k=k)
        val x=Vector(1.0); val conditional=p.conditionalAngle(x)
        conditional.kappa shouldBe k
        for(a <- Vector(-3.1,3.1,20.0)) p.logDensity(LinearAngular(x,a)) shouldBe
          (p.linearLogDensity(x)+conditional.logDensity(a) +- 1e-7)
      }
    }
    "sample reproducibly from the conditional law without creating a Figaro model" in {
      val p=coupled; val x=Vector(2.0,-1.0); val c=p.conditionalAngle(x)
      val first=new scala.util.Random(73L); val second=new scala.util.Random(73L)
      Vector.fill(100)(c.sample(first)) shouldBe Vector.fill(100)(c.sample(second))
      val draws=Vector.fill(20000)(c.sample(first))
      draws.map(t => math.cos(CircularStatistics.difference(t,c.location))).sum/draws.size shouldBe
        (c.meanResultantLength +- 0.015)
      c.location shouldBe p.conditionalLocation(x)
    }
    "validate dimensions and ignore irrelevant center overflow for uniform angles" in {
      val p=scalar(b=Double.MaxValue,g=Double.MaxValue,k=0)
      val c=p.conditionalAngle(Vector(Double.MaxValue))
      c.kappa shouldBe 0.0; c.meanDirection shouldBe None
      intercept[IllegalArgumentException](p.conditionalAngle(null))
      intercept[IllegalArgumentException](p.conditionalAngle(Vector.empty))
      intercept[IllegalArgumentException](p.conditionalAngle(Vector(Double.NaN)))
      intercept[ArithmeticException](scalar(b=Double.MaxValue).conditionalAngle(Vector(2.0)))
    }
  }
}
