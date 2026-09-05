/*
 * OpenUniverseTest.scala  
 * Open universe example test.
 * 
 * Created By:      Avi Pfeffer (apfeffer@cra.com)
 * Creation Date:   Jan 1, 2009
 * 
 * Copyright 2017 Avrom J. Pfeffer and Charles River Analytics, Inc.
 * See http://www.cra.com or email figaro@cra.com for information.
 * 
 * See http://www.github.com/p2t2/figaro for a copy of the software license.
 */

package com.cra.figaro.test.example

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import com.cra.figaro.algorithm._
import com.cra.figaro.algorithm.sampling._
import com.cra.figaro.language._
import com.cra.figaro.language.Universe._
import com.cra.figaro.library.compound._
import com.cra.figaro.library.atomic._
import com.cra.figaro.library.collection.{MakeArray, FixedSizeArrayElement, FixedSizeArray}
import com.cra.figaro.util._
import com.cra.figaro.test._
import com.cra.figaro.test.tags.Example

class OpenUniverseTest extends AnyWordSpec with Matchers {
  "The open universe example" should {
    "produce the correct answer under Metropolis-Hastings" taggedAs (Example) in {
      test()
    }
  }

  val probOneMoreSource = 0.9

  def test(): Unit = {
    Universe.createNew()

    val numSources = discrete.Geometric(probOneMoreSource)
    val sourceItems = new MakeArray[Double](Name[FixedSizeArray[Double]](""), numSources,
      (_: Int) => continuous.Uniform(0.0, 1.0), Universe.universe)
    val sources = new FixedSizeArrayElement(sourceItems)
      .foldLeft(List.empty[Double])((xs, value) => xs :+ value)

    class Sample(s: String) {
      val sourceNum = IntSelector(numSources)
      val source = Apply(sources, sourceNum, (s: Seq[Double], i: Int) => s(i))
      val position = NonCachingChain(source, (x: Double) => continuous.Uniform(x - 0.1, x + 0.1))
    }

    val sample1 = new Sample("sample1")
    val sample2 = new Sample("sample2")
    val samples = List(sample1, sample2)
    val numSamples = samples.length

    val equal = sample1.sourceNum === sample2.sourceNum

    def chooseScheme(): ProposalScheme =
      DisjointScheme(
        (0.25, () => ProposalScheme(numSources)),
        (0.25, () => ProposalScheme(sourceItems.items(random.nextInt(numSources.value)))),
        (0.25, () => ProposalScheme(samples(random.nextInt(numSamples)).sourceNum)),
        (0.25, () => ProposalScheme(samples(random.nextInt(numSamples)).position)))

    sample1.position.addCondition((y: Double) => y >= 0.5 && y < 0.8)
    sample2.position.addCondition((y: Double) => y >= 0.5 && y < 0.8)

    // Probability of same sources given fixed number of sources =
    // (Density(conditions | same source) * Number of combinations with same source) /
    // (Density(conditions | different sources) * Number of combinations with different sources).

    // Density(conditions | different source) = Density(condition 1) ^ 2
    // Density(condition 1) = \int_0.4^0.6 (x - 0.4) / 0.2 + 0.1 + \int_0.7^0.9 (0.9 - x) / 0.2
    // = 0.1 + 10 [0.5 (x - 0.4)^2]_0.4^0.6 = 0.3

    // Density(conditions | same source) = 
    // \int_0.4^0.6 ((x-0.4)/0.2)^2 dx + 0.1 + \int_0.7^0.9 ((0.9-x)/0.2)^2 dx =
    // = 0.1 + 50 [1/3 (x-0.4)^3]_0.4^0.6 = 0.23...

    // With n sources, number of same source combinations = n, number of different source combinations = n^2 - n
    val limitNumSources = 1001
    def geometricProb(n: Int) = scala.math.pow(probOneMoreSource, n - 1) * (1 - probOneMoreSource)
    def probSame(n: Int) = geometricProb(n) / n * 0.23333333
    def probDifferent(n: Int) = geometricProb(n) * (n - 1) / n * 0.3 * 0.3
    val totalProbSame = ((1 to limitNumSources)).foldLeft(0.0)(_ + probSame(_))
    val totalProbDifferent = ((1 to limitNumSources)).foldLeft(0.0)(_ + probDifferent(_))
    val answer = totalProbSame / (totalProbSame + totalProbDifferent)
    val alg = MetropolisHastings(200000, chooseScheme(), 5000, equal)
    alg.start()
    alg.probability(equal, true) should be(answer +- 0.02)
    alg.kill()
  }
}
