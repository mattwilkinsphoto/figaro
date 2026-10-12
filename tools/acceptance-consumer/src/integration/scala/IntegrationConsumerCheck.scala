import com.cra.figaro.language.*
import com.cra.figaro.library.atomic.continuous.*
import com.cra.figaro.library.atomic.discrete.*
import com.cra.figaro.algorithm.filtering.ParticleFilter
import com.cra.figaro.algorithm.factored.VariableElimination
import com.cra.figaro.algorithm.sampling.SupportAwareProposal
import com.cra.figaro.algorithm.sampling.parallel.MultiChainMetropolisHastings as MH

/** Packaged API/linkage checks, not a performance benchmark or convergence certificate. */
object IntegrationConsumerCheck {
  def main(args: Array[String]): Unit = {
    require(args.isEmpty)
    val artifact=java.nio.file.Path.of(classOf[Universe].getProtectionDomain.getCodeSource.getLocation.toURI)
    require(artifact.getFileName.toString.endsWith(".jar"))
    val hash=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
      .digest(java.nio.file.Files.readAllBytes(artifact)))
    require(hash.equalsIgnoreCase(sys.env("FIGARO_EXPECTED_SHA256")))
    val u=new Universe
    val y=Laplace(0,1)(using "y",u); y.observe(1000)
    val pf=ParticleFilter(u, (_: Universe) => new Universe,2)
    try { pf.start(); require(math.abs(pf.getlogProbEvidence()+1000+math.log(2))<1e-11) }
    finally { if(pf.isActive) pf.kill(); u.clear() }

    val v=new Universe
    val counts=Multinomial.exact(MultinomialDistribution(3,Vector(.25,.25,.5)))(using "counts",v)
    val exact=VariableElimination(counts)(using v)
    try { exact.start(); require(math.abs(exact.probability(counts,Vector(1,1,1))-.1875)<1e-12) }
    finally { if(exact.isActive) exact.kill(); v.clear() }

    val result=MH.run(MH.Config(chains=2,drawsPerChain=4000,warmUp=500)) { (owner,_) =>
      val x=ScalarElement(LogNormalDistribution(0,1))(using "x",owner)
      val kernel=x.map(z => LaplaceDistribution(math.log(z),1))(using "kernel",owner)
      ScalarElement(kernel)(using "y",owner).observe(0)
      MH.Model(Vector(MH.Observable("logx",x)(math.log)),Some(SupportAwareProposal.positive(x,.8)))
    }
    require(math.abs(result.diagnostics("logx").mean)<.12)
    require(GeneralizedExtremeValueDistribution(1e-14).logDensity(1).isFinite)
    require(HypergeometricDistribution(1000000000,2,1).probability(1)>0)
    require(WishartDistribution(1,Vector(Vector(1.0))).logDensity(Vector(Vector(1.0))).isFinite)
    println(s"Integration APIs verified from artifact $hash")
  }
}
