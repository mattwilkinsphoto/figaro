import com.cra.figaro.language.*
import com.cra.figaro.library.atomic.continuous.*
import com.cra.figaro.library.atomic.discrete.*
import com.cra.figaro.algorithm.sampling.SupportAwareProposal as S
import com.cra.figaro.algorithm.sampling.parallel.MultiChainMetropolisHastings as MH
import com.cra.figaro.algorithm.filtering.{BoundedParticleFilter as B, ParticleFilterHealth}

/** Independent packaged public-API linkage check, not a performance certification. */
object CapabilityConsumerCheck {
  def main(args: Array[String]): Unit = {
    val artifact=java.nio.file.Path.of(classOf[Universe].getProtectionDomain.getCodeSource.getLocation.toURI)
    require(artifact.getFileName.toString.endsWith(".jar"))
    val hash=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
      .digest(java.nio.file.Files.readAllBytes(artifact)))
    require(hash.equalsIgnoreCase(sys.env("FIGARO_EXPECTED_SHA256")))
    val counts=MultinomialDistribution(20,Vector.fill(10)(.1))
    val groups=Vector((0 until 4).toVector,(4 until 10).toVector)
    require(counts.aggregate(groups).dimension == 2)
    val u=new Universe
    try {
      FiniteCountInference.multinomialAggregated(counts,groups)(using "counts",u)
      S.automatic(Gamma(2,3)(using "gamma",u),.3)
    } finally u.clear()
    require(LogProposalRatios.transition(7,-1000,-1,-900,-2) == ((7,898.0,999.0)))
    val result=MH.runTuned(MH.Config(chains=2,drawsPerChain=4000,warmUp=1000)) { (owner,_) =>
      val x=Normal(0,4)(using "x",owner)
      x.addLogConstraint(v => -.5*math.pow((v-1)/.2,2))
      MH.TunedModel(MH.Model(Vector(MH.Observable("x",x)(identity))),S.tune(x))
    }
    require(math.abs(result.result.diagnostics("x").mean-1/1.01)<.06)
    require(result.tuning.forall(_.warmUpAttempts==1000))
    val eye=Vector(Vector(1.0,0.0),Vector(0.0,1.0))
    val factor=Vector(Vector(1.0,0.0),Vector(1.0,1e-20))
    require(WishartDistribution(4,eye).logDensityCholesky(factor).isFinite)
    require(InverseWishartDistribution(4,eye).logDensityCholesky(factor).isFinite)
    require(GeneralizedExtremeValueDistribution(1e-100).logDensity(1).isFinite)
    val filter=B(B.elementModel[Boolean](u=>Flip(.5)(using "x",u),
      (x,u)=>Flip(if(x) .8 else .2)(using "x",u)),B.Config(10,100,8,seed=51))
    try {
      require(filter.start(identity).particles.forall(identity))
      require(filter.advance(identity).expectation(b=>if(b) 1.0 else 0.0)==1.0)
      require(filter.lastReport.get.health.eligibility==ParticleFilterHealth.Eligibility.IndicatorContract)
    } finally filter.close()
    require(ParticleFilterHealth.assess(Vector(Double.NegativeInfinity)).recommendations.nonEmpty)
    println(s"Capability APIs verified from artifact $hash")
  }
}
