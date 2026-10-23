import com.cra.figaro.algorithm.filtering.{BoundedParticleFilter as B, FixedBudgetParticleFilter as F}

/** Compiled only against the hash-verified 7.3.0 artifact, then loaded with 7.4.0. */
object OldFixedBudgetClient {
  def main(args: Array[String]): Unit = {
    for (policy <- Vector(F.Policy.Always, F.Policy.WhenEssBelow(.25)); method <- F.Method.values) {
      var index = -1
      val model = B.Model(() => { index += 1; index }, (x: Int) => x)
      val config = F.Config(2, policy, method, seed = 42)
      val f = F(model, config)
      try {
        val first = f.start(_ => 0.0)
        require(first.weights == Vector(.5, .5))
        val result = f.advance(_ => 0.0)
        val copied = result.copy(report = result.report.copy(selection = F.SelectionReport()))
        require(copied.weights == result.weights && copied.particles.size == 2)
        require(result.report.stateDraws == 2 && result.report.likelihoodCalls == 2)
        require(result.report.decision == (if (policy == F.Policy.Always) F.Decision.Resampled else F.Decision.CarriedWeights))
        require(f.current == result && f.lastReport.contains(result.report))
      } finally f.close()
    }
    println("7.3.0-compiled fixed-budget client ran against the replacement JAR.")
  }
}
