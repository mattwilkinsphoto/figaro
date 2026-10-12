import com.cra.figaro.algorithm.filtering.{BoundedParticleBudget as A, BoundedParticleFilter as B}
import com.cra.figaro.util.random
import com.cra.figaro.algorithm.filtering.WeightedBoundedParticleFilter as W

/** Consumer compiled only for the post-7.1 adviser, never for the immutable 7.1 release. */
object BudgetConsumerCheck {
  def main(args:Array[String]):Unit={
    val artifact=java.nio.file.Path.of(classOf[B[?]].getProtectionDomain.getCodeSource.getLocation.toURI)
    require(artifact.getFileName.toString.endsWith(".jar"))
    val hash=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
      .digest(java.nio.file.Files.readAllBytes(artifact)))
    require(hash.equalsIgnoreCase(sys.env("FIGARO_EXPECTED_SHA256")))
    val pilot=A.runPilot(2000,101)(()=>random.nextDouble()<.1)
    val advice=A.advise(pilot,A.Request(10000))
    require(advice.status==A.Status.Ready)
    val plan=advice.plan.get
    val f=B(B.Model(()=>random.nextDouble(),(_:Double)=>random.nextDouble()),plan.config(202))
    try {val r=f.start(_<.1); require(r.report.attempts<=10000)} finally f.close()
    require(A.advise(A.Pilot(1000,0),A.Request(10000)).plan.isEmpty)
    require(A.advise(A.Pilot(1000,1),A.Request(100)).status==A.Status.InsufficientBudget)
    val weighted=W(B.Model(()=>random.nextDouble(),(x:Double)=>x),W.Config(20,100,10,seed=203))
    try {
      val e=W.Evidence[Double](x=> -x,W.cappedLikelihood(0))
      weighted.start(e)
      val r=weighted.advance(e)
      require(r.report.attempts<=100 && r.logEvidence.isFinite)
      require(math.abs(r.weights.sum-1)<1e-12)
      require(r.expectation(identity)>=0 && r.expectation(identity)<=1)
    } finally weighted.close()
    println("Weighted bounded filtering API verified from the same artifact")
    println(s"Budget adviser API verified from artifact $hash")
  }
}
