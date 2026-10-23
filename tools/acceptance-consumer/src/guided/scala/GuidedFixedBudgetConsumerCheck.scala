import com.cra.figaro.algorithm.filtering.{BoundedParticleFilter as B, FixedBudgetParticleFilter as F,
  WeightedParticleProposal as P, AuxiliaryParticleProposal as A}

/** Development consumer, enabled separately so immutable 7.3 checks stay usable. */
object GuidedFixedBudgetConsumerCheck {
  def main(args:Array[String]):Unit={
    val artifact=java.nio.file.Path.of(classOf[F[?]].getProtectionDomain.getCodeSource.getLocation.toURI)
    require(artifact.getFileName.toString.endsWith(".jar"))
    val hash=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
      .digest(java.nio.file.Files.readAllBytes(artifact)))
    require(hash.equalsIgnoreCase(sys.env("FIGARO_EXPECTED_SHA256")))
    val initial=P.gaussian(0,2)
    def transition(x:Double)=P.gaussian(.8*x,math.sqrt(.3))
    val model=B.Model(initial.sample,(x:Double)=>transition(x).sample())
    def likelihood(y:Double):Double=>Double=x=>P.gaussian(x,.4).logDensity(y)
    def kernel(y:Double)=P.Kernel(initial,transition,P.gaussianObservation(0,2,y,.4),
      (x:Double)=>P.gaussianObservation(.8*x,math.sqrt(.3),y,.4))
    def look(y:Double)=A.LookAhead[Double](x=>P.gaussianPredictiveLogLikelihood(.8*x,math.sqrt(.3),y,.4),.1)
    for(policy<-Vector(F.Policy.Always,F.Policy.WhenEssBelow(.5));method<-F.Method.values){
      val f=F(model,F.Config(256,policy,method,42))
      try{
        val first=f.start(likelihood(.2),kernel(.2))
        require(math.abs(first.logEvidence-P.gaussianPredictiveLogLikelihood(0,2,.2,.4))<1e-12)
        val r=f.advanceAuxiliary(likelihood(-.3),look(-.3),Some(kernel(-.3)))
        require(r.particles.size==256 && r.logEvidence.isFinite && r.expectation(identity).isFinite)
        require(f.lastProposalReport.get.targetDensityCalls==256)
        require(f.lastProposalReport.get.auxiliaryRequested)
        if(policy==F.Policy.Always)require(f.lastProposalReport.get.auxiliary.get.evaluations==256)
        else require(f.lastProposalReport.get.auxiliary.isEmpty && r.report.decision==F.Decision.CarriedWeights)
        f.advance(likelihood(.1),kernel(.1))
      }finally f.close()
    }
    println("Guided fixed-budget API verified from expected packaged artifact: "+hash)
  }
}
