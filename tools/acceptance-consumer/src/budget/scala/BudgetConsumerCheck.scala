import com.cra.figaro.algorithm.filtering.{BoundedParticleBudget as A, BoundedParticleFilter as B}
import com.cra.figaro.util.random
import com.cra.figaro.algorithm.filtering.WeightedBoundedParticleFilter as W
import com.cra.figaro.algorithm.filtering.{WeightedParticleCalibration as C, WeightedParticleProposal as P}
import com.cra.figaro.algorithm.filtering.AuxiliaryParticleProposal as AP
import com.cra.figaro.library.atomic.continuous.MultivariateGaussianDistribution

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
    val p=P.gaussian(0,1)
    def transition(x:Double)=P.gaussian(.8*x,math.sqrt(.3))
    val calibrated=W(B.Model(p.sample,(x:Double)=>transition(x).sample()),W.Config(seed=204))
    def q(y:Double)=P.Kernel(p,transition,P.gaussianObservation(0,1,y,math.sqrt(.5)),
      (x:Double)=>P.defensive(transition(x),P.gaussianObservation(.8*x,math.sqrt(.3),y,math.sqrt(.5))))
    try {
      def likelihood(y:Double)(x:Double)=P.gaussian(x,math.sqrt(.5)).logDensity(y)
      val req=C.Request(maxTotalCandidates=2000)
      calibrated.startCalibrated(likelihood(.2),req,Some(q(.2)))
      val r=calibrated.advanceCalibrated(likelihood(-.3),req,Some(q(-.3)))
      require(calibrated.lastCalibration.get.pilotCandidates+r.report.attempts<=2000)
      require(r.logEvidence.isFinite && r.expectation(identity).isFinite)
      val look=AP.LookAhead[Double](x=>P.gaussianPredictiveLogLikelihood(.8*x,math.sqrt(.3),.7,math.sqrt(.5)))
      val auxiliary=calibrated.advanceAuxiliaryCalibrated(likelihood(.7),req,look,Some(q(.7)))
      require(calibrated.lastAuxiliary.get.evaluations==r.particles.size)
      require(auxiliary.logEvidence.isFinite && auxiliary.weights.sum > .999999999)
      calibrated.advanceAuxiliary(W.Evidence(likelihood(.4),W.cappedLikelihood(0)),
        AP.LookAhead[Double](x=> -x*x),Some(q(.4)))
    } finally calibrated.close()
    println("Calibrated guided filtering verified from the same artifact")
    val priorVector=MultivariateGaussianDistribution(Vector(0.0,0.0),Vector(Vector(1.0,0.0),Vector(0.0,1.0)))
    val conditioned=P.linearGaussianObservation(priorVector,Vector(1.0),Vector(Vector(1.0,0.0)),Vector(Vector(1.0)))
    require(math.abs(conditioned.posterior.mean.head-.5)<1e-12)
    require(conditioned.proposal.logDensity(Vector(.5,0.0)).isFinite)
    require(P.multivariateGaussian(priorVector).logDensity(Vector(0.0,0.0)).isFinite)
    println("Auxiliary ancestor and vector Gaussian guidance verified from the same artifact")
    println(s"Budget adviser API verified from artifact $hash")
  }
}
