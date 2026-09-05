/* See LICENSE and FigaroAttributions.txt for the project's license terms. */
package com.cra.figaro.example.documentation

import com.cra.figaro.algorithm.factored.VariableElimination
import com.cra.figaro.algorithm.sampling.Importance
import com.cra.figaro.language.{Flip, Select, Universe}
import com.cra.figaro.library.atomic.continuous.Normal
import com.cra.figaro.library.compound.If

/** Three independent models used by the user guide. Each resets the default universe. */
object CommonPatterns {
  /** Query a categorical distribution without evidence.
    * @return the exact probability of a late delivery (0.2)
    * @example `val lateProbability = CommonPatterns.exactMarginal()`
    */
  def exactMarginal(): Double = {
    Universe.createNew()
    val delivery = Select(0.7 -> "on-time", 0.2 -> "late", 0.1 -> "cancelled")
    val algorithm = VariableElimination(delivery)
    algorithm.start()
    try algorithm.probability(delivery, "late")
    finally algorithm.kill()
  }

  /** Update a cause probability after observing a signal.
    * @return the exact posterior P(cause | signal), 9/13
    * @example `val posterior = CommonPatterns.bayesianPosterior()`
    */
  def bayesianPosterior(): Double = {
    Universe.createNew()
    val cause = Flip(0.2)
    val signal = If(cause, Flip(0.9), Flip(0.1))
    signal.observe(true)
    val algorithm = VariableElimination(cause)
    algorithm.start()
    try algorithm.probability(cause, true)
    finally algorithm.kill()
  }

  /** Estimate a tail probability for a continuous temperature model.
    * @param samples positive importance-sample count; higher values cost more time
    * @return an estimate of P(temperature > 21), normally near 0.309; not an error bound
    * @throws IllegalArgumentException if samples is not positive
    * @example `val tailProbability = CommonPatterns.sampledThreshold(50000)`
    */
  def sampledThreshold(samples: Int): Double = {
    require(samples > 0, "samples must be positive")
    Universe.createNew()
    // Normal's second argument is VARIANCE: standard deviation is sqrt(4) = 2.
    val temperature = Normal(20.0, 4.0)
    val algorithm = Importance(samples, temperature)
    algorithm.start()
    try algorithm.probability(temperature, (value: Double) => value > 21.0)
    finally algorithm.kill()
  }

  /** Run the examples and check exact results and valid sampled probability bounds.
    * @param args command-line arguments (unused)
    * @return Unit; prints two exact answers and one approximate answer
    * @example `CommonPatterns.main(Array.empty[String])`
    */
  def main(args: Array[String]): Unit = {
    val marginal = exactMarginal()
    val posterior = bayesianPosterior()
    val tail = sampledThreshold(50000)
    assert(math.abs(marginal - 0.2) < 1e-12)
    assert(math.abs(posterior - 9.0 / 13.0) < 1e-12)
    assert(!tail.isNaN && tail >= 0.0 && tail <= 1.0)
    println(f"P(late) = $marginal%.6f")
    println(f"P(cause | signal) = $posterior%.6f")
    println(f"Estimated P(temperature > 21) = $tail%.6f")
  }
}
