import com.cra.figaro.algorithm.filtering.{BoundedParticleFilter as B, FixedBudgetParticleFilter as F,
  WeightedParticleProposal as P, AuxiliaryParticleProposal as A}

/** Compiled against hash-verified 7.4.0; executed with only the replacement JAR. */
object OldGuidedClient {
  def main(args: Array[String]): Unit = {
    val initial = P.gaussian(0, 1)
    def transition(x: Double) = P.gaussian(x, .5)
    val model = B.Model(initial.sample, (x: Double) => transition(x).sample())
    val kernel = P.Kernel(initial, transition, initial, transition)
    val look = A.LookAhead[Double](_ => 0.0, .1)
    for (policy <- Vector(F.Policy.Always, F.Policy.WhenEssBelow(.5)); method <- F.Method.values) {
      val f = F(model, F.Config(32, policy, method, 740))
      try {
        f.start(_ => 0.0, kernel)
        val result = f.advanceAuxiliary(_ => 0.0, look, Some(kernel))
        require(result.particles.size == 32 && math.abs(result.logEvidence) < 1e-12)
        require(f.lastProposalReport.get.auxiliaryRequested)
        require(f.lastProposalReport.get.targetDensityCalls == 32)
        f.advance(_ => 0.0, kernel)
      } finally f.close()
    }
    println("7.4.0-compiled guided/auxiliary client ran against the replacement JAR.")
  }
}
