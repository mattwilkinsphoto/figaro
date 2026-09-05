/*
 * CarAndEngine.scala
 * A probabilistic relational model example with reference uncertainty.
 * 
 * Created By:      Avi Pfeffer (apfeffer@cra.com)
 * Creation Date:   Jan 1, 2009
 * 
 * Copyright 2017 Avrom J. Pfeffer and Charles River Analytics, Inc.
 * See http://www.cra.com or email figaro@cra.com for information.
 * 
 * See http://www.github.com/p2t2/figaro for a copy of the software license.
 */

/*
 * Additional Updates from our community
 * 
 * Cagdas Senol		Jul 16, 2016
 */

package com.cra.figaro.example

import com.cra.figaro.algorithm.sampling._
import com.cra.figaro.algorithm.factored._
import com.cra.figaro.language._
import com.cra.figaro.library.compound._
import com.cra.figaro.library.atomic.discrete.Uniform
import com.cra.figaro.library.atomic.continuous.Normal

/**
 * A probabilistic relational model example with reference uncertainty.
 */
object CarAndEngine {
  abstract class Engine extends ElementCollection {
    val power: Element[Symbol]
  }

  private class V8 extends Engine {
    val power: Element[Symbol] = Select(0.8 -> Symbol("high"), 0.2 -> Symbol("medium"))("power", this)
  }

  private class V6 extends Engine {
    val power: Element[Symbol] = Select(0.2 -> Symbol("high"), 0.5 -> Symbol("medium"), 0.3 -> Symbol("low"))("power", this)
  }

  private object MySuperEngine extends V8 {
    override val power: Element[Symbol] = Constant(Symbol("high"))("power", this)
  }

  class Car extends ElementCollection {
    val engine = Uniform[Engine](new V8, new V6, MySuperEngine)("engine", this)

    val speed = CPD(
      get[Symbol]("engine.power"),
      Symbol("high") -> Constant(90.0),
      Symbol("medium") -> Constant(80.0),
      Symbol("low") -> Constant(70.0))
  }

  def main(args: Array[String]): Unit = {
    val car = new Car
    val alg = VariableElimination(car.speed)
    alg.start()
    alg.stop()
    println(alg.expectation(car.speed)(d => d))
    alg.kill()
  }
}
