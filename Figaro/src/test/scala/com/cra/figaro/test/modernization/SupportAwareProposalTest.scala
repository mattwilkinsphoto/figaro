package com.cra.figaro.test.modernization

import com.cra.figaro.algorithm.sampling.SupportAwareProposal as S
import com.cra.figaro.algorithm.sampling.parallel.MultiChainMetropolisHastings as MH
import com.cra.figaro.language.*
import com.cra.figaro.library.atomic.continuous.*
import org.apache.commons.math3.linear.{Array2DRowRealMatrix, LUDecomposition}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class SupportAwareProposalTest extends AnyWordSpec with Matchers {
  private val config=MH.Config(chains=2,drawsPerChain=15000,warmUp=2000,parallelism=2,seed=8421L)
  "Support-aware proposals" should {
    "match numerical Jacobians and invert scalar transforms" in {
      for((c,y) <- Vector((S.positiveCoordinates,.7),(S.boundedCoordinates(-2,5),-.4))) {
        val x=c.decode(Vector(y)); c.encode(x).head shouldBe (y +- 1e-12)
        val derivative=(c.decode(Vector(y+1e-5))-c.decode(Vector(y-1e-5)))/2e-5
        math.log(math.abs(derivative)) shouldBe (c.logJacobian(Vector(y)) +- 1e-9)
      }
      S.circularCoordinates.decode(Vector(math.Pi+.1)) shouldBe (-math.Pi+.1 +- 1e-14)
      intercept[IllegalArgumentException](S.positiveCoordinates.decode(Vector(-1000)))
      intercept[IllegalArgumentException](S.boundedCoordinates(0,1).decode(Vector(1000)))
    }
    "match independent finite-difference determinants for covariance and correlation maps" in {
      for(d <- 2 to 4; correlation <- Vector(false,true)) {
        val c=S.matrixCoordinates(d,correlation)
        val n=if(correlation) d*(d-1)/2 else d*(d+1)/2
        val y=Vector.tabulate(n)(i => .1*(i%3-1))
        val x=c.decode(y)
        c.encode(x).zip(y).foreach((a,b) => a shouldBe (b +- 1e-12))
        def entries(z: Vector[Double]): Vector[Double] = {
          val m=c.decode(z)
          (for(i <- 0 until d; j <- 0 to i if !correlation || j<i) yield m(i)(j)).toVector
        }
        val columns=y.indices.map { j =>
          val plus=entries(y.updated(j,y(j)+1e-5)); val minus=entries(y.updated(j,y(j)-1e-5))
          plus.zip(minus).map((a,b) => (a-b)/2e-5)
        }
        val jac=Array.tabulate(n,n)((i,j) => columns(j)(i))
        val determinant=new LUDecomposition(new Array2DRowRealMatrix(jac)).getDeterminant
        math.log(math.abs(determinant)) shouldBe (c.logJacobian(y) +- 1e-8)
      }
    }
    "recover a concentrated positive posterior and retain the prior-proposal comparator" in {
      for(local <- Vector(false,true)) {
        val r=MH.run(config) { (u,_) =>
          val x=ScalarElement(LogNormalDistribution(0,1))(using "x",u)
          x.addLogConstraint((v: Double) => -.5*math.pow((math.log(v)-2)/.05,2))
          MH.Model(Vector(MH.Observable("logx",x)(math.log)),if(local) Some(S.positive(x,.1)) else None)
        }
        r.diagnostics("logx").mean shouldBe (2/1.0025 +- .015)
        info(s"positive local=$local diagnostics=${r.diagnostics("logx")} seconds=${r.elapsedSeconds}")
      }
    }
    "recover bounded and circular prior moments without losing their Jacobians or seam" in {
      val bounded=MH.run(config) { (u,_) =>
        val x=ScalarElement(KumaraswamyDistribution(1,1))(using "x",u)
        MH.Model(Vector(MH.Observable("x",x)(identity),MH.Observable("square",x)(v => v*v)),Some(S.bounded(x,1.5)))
      }
      bounded.diagnostics("x").mean shouldBe (.5 +- .025)
      bounded.diagnostics("square").mean shouldBe (1.0/3 +- .025)
      val circular=MH.run(config) { (u,_) =>
        val x=VonMises(math.Pi,2)(using "x",u)
        MH.Model(Vector(MH.Observable("sin",x)(math.sin),MH.Observable("cos",x)(math.cos)),Some(S.circular(x,.8)))
      }
      circular.diagnostics("sin").mean shouldBe (0.0 +- .035)
      circular.diagnostics("cos").mean shouldBe (-.697774657964008 +- .035)
    }
    "recover Wishart and LKJ matrix moments" in {
      val eye=Vector(Vector(1.0,0.0),Vector(0.0,1.0))
      val covariance=MH.run(config.copy(drawsPerChain=25000)) { (u,_) =>
        val x=Wishart(WishartDistribution(5,eye))(using "x",u)
        MH.Model(Vector(MH.Observable("trace",x)(v => v(0)(0)+v(1)(1))),Some(S.covariance(x,.35)))
      }
      covariance.diagnostics("trace").mean shouldBe (10.0 +- .5)
      val correlation=MH.run(config) { (u,_) =>
        val x=LKJ(LKJDistribution(2,1))(using "x",u)
        MH.Model(Vector(MH.Observable("rho",x)(_(1)(0)),MH.Observable("square",x)(v => v(1)(0)*v(1)(0))),Some(S.correlation(x,.8)))
      }
      correlation.diagnostics("rho").mean shouldBe (0.0 +- .035)
      correlation.diagnostics("square").mean shouldBe (1.0/3 +- .025)
    }
    "refuse incompatible support and observed targets before sampling" in {
      val u=new Universe
      try {
        val x=ScalarElement(LogNormalDistribution(0,1))(using "x",u)
        intercept[IllegalArgumentException](S.bounded(x))
        intercept[IllegalArgumentException](S.positive(x,0))
        x.observe(1)
        intercept[IllegalArgumentException](S.positive(x))
      } finally u.clear()
    }
  }
}
