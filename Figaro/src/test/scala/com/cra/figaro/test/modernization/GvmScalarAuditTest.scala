package com.cra.figaro.test.modernization

import com.cra.figaro.library.atomic.continuous.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class GvmScalarAuditTest extends AnyWordSpec with Matchers {
  private def kernel(mu: Double=0,sd: Double=1,a: Double=0,b: Double=0,g: Double=0,k: Double=0) =
    GaussVonMisesDistribution(Vector(mu),Vector(Vector(sd*sd)),a,Vector(b),Vector(Vector(g)),k)
  private def identical(p: GaussVonMisesDistribution,q: GaussVonMisesDistribution,t: Double=1e-8,b: Int=50000): Unit = {
    val old=GvmScalarFullSumBaseline.compare(p,q,t,b)
    val current=GaussVonMisesScalarBhattacharyya.compare(p,q,t,b)
    // Status enum types differ; all remaining diagnostic fields compare directly.
    current.status.toString shouldBe old.status.toString
    current.productIterator.drop(1).toVector shouldBe old.productIterator.drop(1).toVector
  }
  "Audited scalar refinement totals" should {
    "retain every final diagnostic and evaluation count on the bidirectional oracle grid" in {
      for(f <- GvmBhattacharyyaReliabilityFixtures.all.filter(_.n == 1)) {
        val (p,q)=if(f.family == "linear") (kernel(k=f.k),kernel(a=f.alpha,b=f.beta,k=f.k))
          else (kernel(.5,1,.25,.5,.25,f.k),kernel(-.5,.5,-.5,-.25,f.gamma,f.k))
        withClue(f.id+": ") { identical(p,q); identical(q,p) }
      }
    }
    "audit precision and budget exits as well as successful termination" in {
      val cases=Vector((kernel(k=50),kernel(a=math.Pi,b=1e-5,k=50)),
        (kernel(.5,1,.25,.5,.25,50),kernel(-.5,.5,-.5,-.25,2,50)),
        (kernel(.3,1.1,.2,.7,.3,4.5),kernel(-.4,.8,-.5,-.2,-.15,1.2)))
      for((p,q) <- cases; t <- Vector(1e-6,1e-8,1e-12); b <- Vector(5,500,15000,25095,25098,25100,50000))
        withClue(s"t=$t b=$b: ") { identical(p,q,t,b); identical(q,p,t,b) }
    }
    "retain difficult input refusals and exact shortcuts" in {
      identical(kernel(),kernel(mu=2))
      identical(kernel(k=50),kernel(a=math.Pi,k=50))
      identical(kernel(b=1e8,k=2),kernel(a=1,b=1e8+1e-6,k=2))
      identical(kernel(k=50),kernel(b=2*math.Pi*64,k=50),b=500)
      identical(kernel(k=50),kernel(b=50,g=400,k=50),b=500)
    }
  }
}
