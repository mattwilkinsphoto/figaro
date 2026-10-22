import com.cra.figaro.algorithm.filtering.{BoundedParticleFilter as B, FixedBudgetParticleFilter as F}
import com.cra.figaro.util.random

/** Public APIs only; source must resolve to the expected packaged JAR, never project classes. */
object FixedBudgetConsumerCheck {
  def main(args:Array[String]):Unit={
    val artifact=java.nio.file.Path.of(classOf[F[?]].getProtectionDomain.getCodeSource.getLocation.toURI)
    require(artifact.getFileName.toString.endsWith(".jar"))
    val hash=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
      .digest(java.nio.file.Files.readAllBytes(artifact)))
    require(hash.equalsIgnoreCase(sys.env("FIGARO_EXPECTED_SHA256")))
    val model=B.Model(()=>random.nextGaussian(),(x:Double)=>.8*x+math.sqrt(.3)*random.nextGaussian())
    def likelihood(y:Double):Double=>Double=x=> -.5*(math.log(2*math.Pi*.5)+(y-x)*(y-x)/.5)
    for(policy<-Vector(F.Policy.Always,F.Policy.WhenEssBelow(.5))) {
      val f=F(model,F.Config(256,policy,F.Method.LogTreeMultinomial,42))
      try {
        f.start(likelihood(.2));val r=f.advance(likelihood(-.3))
        require(r.particles.size==256 && r.logEvidence.isFinite && r.expectation(identity).isFinite)
        require(r.report.stateDraws==256 && r.report.likelihoodCalls==256)
        require(math.abs(r.weights.sum-1)<1e-12 && f.current==r && f.lastReport.contains(r.report))
      }finally f.close()
    }
    val finite=B.Model(()=>random.nextDouble()<.5,(x:Boolean)=>if(random.nextDouble()<.1)!x else x)
    val f=F(finite,F.Config(256,F.Policy.Always,F.Method.Stratified,42))
    try {
      f.start(x=>math.log(if(x).8 else .2))
      val r=f.advance(x=>math.log(if(x).3 else .7))
      require(r.report.decision==F.Decision.Resampled && r.report.ancestorSelections==256)
    }finally f.close()
    var i = -1
    val carried=F(B.Model(()=>{i+=1;i},(x:Int)=>x),F.Config(2,F.Policy.WhenEssBelow(.25),F.Method.Stratified))
    try {
      carried.start(x=>if(x==0)0.0 else -1000.0)
      val r=carried.advance(x=>if(x==0)-1000.0 else 0.0)
      require(r.report.decision==F.Decision.CarriedWeights && r.weights.forall(w=>math.abs(w-.5)<1e-14))
    }finally carried.close()
    println("Fixed-budget public API verified from expected packaged artifact: "+hash)
  }
}
