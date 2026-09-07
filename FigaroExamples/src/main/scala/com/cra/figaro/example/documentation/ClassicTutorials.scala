/* Adapted from the original Figaro Hello World and Burglary tutorials.
 * Original Figaro authors: Avi Pfeffer and Charles River Analytics contributors.
 * See LICENSE and FigaroAttributions.txt for license terms and modernization credits.
 */
package com.cra.figaro.example.documentation

import com.cra.figaro.algorithm.factored.VariableElimination
import com.cra.figaro.algorithm.sampling.Importance
import com.cra.figaro.language.{Constant, Flip, Select, Universe}
import com.cra.figaro.library.compound.CPD

/** Scala 3 versions of the classic introductory models, with numerical checks. */
object ClassicTutorials {
  /** Run Constant/Importance, Select/elimination, and the Burglary Bayesian network.
    * @param args command-line arguments (must be empty)
    * @return Unit; prints checked probabilities, or throws if a regression fails
    * @example `ClassicTutorials.main(Array.empty[String])`
    */
  def main(args: Array[String]): Unit = {
    require(args.isEmpty, "ClassicTutorials takes no arguments")
    helloWorld()
    uncertainGreeting()
    burglary()
    println("Classic tutorials passed: Constant, Select, Burglary and independent enumeration")
  }

  private def helloWorld(): Unit = {
    val u = Universe.createNew()
    val greeting = Constant("Hello world!")(using "greeting", u)
    val algorithm = Importance(1000, greeting)
    try {
      algorithm.start()
      val hello = algorithm.probability(greeting, "Hello world!")
      val goodbye = algorithm.probability(greeting, "Goodbye world!")
      assert(math.abs(hello - 1.0) < 1e-12 && goodbye == 0.0)
      println(f"Constant greeting: hello=$hello%.1f, goodbye=$goodbye%.1f")
    } finally {
      if (algorithm.isActive) algorithm.kill()
      u.clear()
    }
  }

  private def uncertainGreeting(): Unit = {
    val u = Universe.createNew()
    val greeting = Select(0.8 -> "Hello world!", 0.2 -> "Goodbye world!")(using "greeting", u)
    val algorithm = VariableElimination(greeting)
    try {
      algorithm.start()
      val hello = algorithm.probability(greeting, "Hello world!")
      val goodbye = algorithm.probability(greeting, "Goodbye world!")
      assert(math.abs(hello - 0.8) < 1e-12 && math.abs(goodbye - 0.2) < 1e-12)
      println(f"Uncertain greeting: hello=$hello%.1f, goodbye=$goodbye%.1f")
    } finally {
      if (algorithm.isActive) algorithm.kill()
      u.clear()
    }
  }

  private def burglary(): Unit = {
    val u = Universe.createNew()
    val burglary = Flip(0.01)(using "burglary", u)
    val earthquake = Flip(0.0001)(using "earthquake", u)
    val alarm = CPD(burglary, earthquake,
      (false, false) -> Flip(0.001), (false, true) -> Flip(0.1),
      (true, false) -> Flip(0.9), (true, true) -> Flip(0.99))
    val johnCalls = CPD(alarm, false -> Flip(0.01), true -> Flip(0.7))
    johnCalls.observe(true)
    val algorithm = VariableElimination(burglary, earthquake)
    try {
      algorithm.start()
      // Independent enumeration, without any Figaro query in the oracle.
      val cases = for (b <- Vector(false, true); e <- Vector(false, true)) yield {
        val prior = (if (b) 0.01 else 0.99) * (if (e) 0.0001 else 0.9999)
        val pAlarm = if (b && e) 0.99 else if (b) 0.9 else if (e) 0.1 else 0.001
        (b, e, prior * (pAlarm * 0.7 + (1.0 - pAlarm) * 0.01))
      }
      val evidence = cases.map(_._3).sum
      val expectedBurglary = cases.filter(_._1).map(_._3).sum / evidence
      val expectedEarthquake = cases.filter(_._2).map(_._3).sum / evidence
      val posterior = algorithm.probability(burglary, true)
      assert(math.abs(posterior - expectedBurglary) < 1e-12)
      assert(math.abs(algorithm.probability(earthquake, true) - expectedEarthquake) < 1e-12)
      assert(math.abs(posterior - 0.3733781172643905) < 1e-12)
      println(f"Burglary given John's call: $posterior%.12f")
    } finally {
      if (algorithm.isActive) algorithm.kill()
      u.clear()
    }
  }
}
