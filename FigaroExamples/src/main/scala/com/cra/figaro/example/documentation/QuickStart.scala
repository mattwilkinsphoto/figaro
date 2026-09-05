/* See LICENSE and FigaroAttributions.txt for the project's license terms. */
package com.cra.figaro.example.documentation

import com.cra.figaro.algorithm.factored.VariableElimination
import com.cra.figaro.language.{Flip, Universe}
import com.cra.figaro.library.compound.If

/** A complete first model: infer a possible cause from an observed signal. */
object QuickStart {
  /** Run exact Bayesian inference and print the posterior.
    * @param args command-line arguments (unused)
    * @return Unit; prints P(cause | signal) = 0.692308
    * @example `QuickStart.main(Array.empty[String])`
    */
  def main(args: Array[String]): Unit = {
    Universe.createNew()
    val cause = Flip(0.2)
    val signal = If(cause, Flip(0.9), Flip(0.1))
    signal.observe(true)
    val algorithm = VariableElimination(cause)
    algorithm.start()
    try {
      val posterior = algorithm.probability(cause, true)
      assert(math.abs(posterior - 9.0 / 13.0) < 1e-12)
      println(f"P(cause | signal) = $posterior%.6f")
    } finally algorithm.kill()
  }
}
