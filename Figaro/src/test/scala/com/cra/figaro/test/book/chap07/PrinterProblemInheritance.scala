/*
 * PrinterProblemInheritance.scala 
 * Book example unit test.
 * 
 * Created By:      Michael Reposa (mreposa@cra.com), Avi Pfeffer (apfeffer@cra.com)
 * Creation Date:   Feb 26, 2016
 * 
 * Copyright 2017 Avrom J. Pfeffer and Charles River Analytics, Inc.
 * See http://www.cra.com or email figaro@cra.com for information.
 * 
 * See http://www.github.com/p2t2/figaro for a copy of the software license.
 */

package com.cra.figaro.test.book.chap07

import com.cra.figaro.language._
import com.cra.figaro.library.compound._
import com.cra.figaro.algorithm.factored.VariableElimination
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import com.cra.figaro.test.tags.BookExample
import com.cra.figaro.test.tags.NonDeterministic

object PrinterProblemInheritance {
  abstract class Printer {
    val powerButtonOn = Flip(0.95)

    val paperFlow = Select(0.6 -> Symbol("smooth"), 0.2 -> Symbol("uneven"), 0.2 -> Symbol("jammed"))
    val paperJamIndicatorOn =
      If(powerButtonOn,
         CPD(paperFlow,
             Symbol("smooth") -> Flip(0.1),
             Symbol("uneven") -> Flip(0.3),
             Symbol("jammed") -> Flip(0.99)),
         Constant(false))

    val state: Element[Symbol]
  }

  class LaserPrinter extends Printer {
    val tonerLevel = Select(0.7 -> Symbol("high"), 0.2 -> Symbol("low"), 0.1 -> Symbol("out"))
    val tonerLowIndicatorOn =
      If(powerButtonOn,
         CPD(tonerLevel,
             Symbol("high") -> Flip(0.2),
             Symbol("low") -> Flip(0.6),
             Symbol("out") -> Flip(0.99)),
         Constant(false))
    val state =
      Apply(powerButtonOn, tonerLevel, paperFlow,
            (power: Boolean, toner: Symbol, paper: Symbol) => {
              if (power) {
                if (toner == Symbol("high") && paper == Symbol("smooth")) Symbol("good")
                else if (toner == Symbol("out") || paper == Symbol("out")) Symbol("out")
                else Symbol("poor")
              } else Symbol("out")
            }
      )
  }

  class InkjetPrinter extends Printer {
    val inkCartridgeEmpty = Flip(0.1)
    val inkCartridgeEmptyIndicator = If(inkCartridgeEmpty, Flip(0.99), Flip(0.3))
    val cloggedNozzle = Flip(0.001)
    val state =
      Apply(powerButtonOn, inkCartridgeEmpty, cloggedNozzle, paperFlow,
            (power: Boolean, ink: Boolean, nozzle: Boolean, paper: Symbol) => {
              if (power && !ink && !nozzle) {
                if (paper == Symbol("smooth")) Symbol("good")
                else if (paper == Symbol("uneven")) Symbol("poor")
                else Symbol("out")
              } else Symbol("out")
            }
      )
  }

  class Software {
    val state = Select(0.8 -> Symbol("correct"), 0.15 -> Symbol("glitchy"), 0.05 -> Symbol("crashed"))
  }

  class Network {
    val state = Select(0.7 -> Symbol("up"), 0.2 -> Symbol("intermittent"), 0.1 -> Symbol("down"))
  }

  class User {
    val commandCorrect = Flip(0.65)
  }

  class PrintExperience(printer: Printer, software: Software, network: Network, user: User) {
    val numPrintedPages =
      RichCPD(user.commandCorrect, network.state, software.state, printer.state,
          (*, *, *, OneOf(Symbol("out"))) -> Constant(Symbol("zero")),
          (*, *, OneOf(Symbol("crashed")), *) -> Constant(Symbol("zero")),
          (*, OneOf(Symbol("down")), *, *) -> Constant(Symbol("zero")),
          (OneOf(false), *, *, *) -> Select(0.3 -> Symbol("zero"), 0.6 -> Symbol("some"), 0.1 -> Symbol("all")),
          (OneOf(true), *, *, *) -> Select(0.01 -> Symbol("zero"), 0.01 -> Symbol("some"), 0.98 -> Symbol("all")))
    val printsQuickly =
      Chain(network.state, software.state,
            (network: Symbol, software: Symbol) =>
              if (network == Symbol("down") || software == Symbol("crashed")) Constant(false)
              else if (network == Symbol("intermittent") || software == Symbol("glitchy")) Flip(0.5)
              else Flip(0.9))
    val goodPrintQuality =
      CPD(printer.state,
          Symbol("good") -> Flip(0.95),
          Symbol("poor") -> Flip(0.3),
          Symbol("out") -> Constant(false))
    val summary =
      Apply(numPrintedPages, printsQuickly, goodPrintQuality,
            (pages: Symbol, quickly: Boolean, quality: Boolean) =>
            if (pages == Symbol("zero")) Symbol("none")
            else if (pages == Symbol("some") || !quickly || !quality) Symbol("poor")
            else Symbol("excellent"))
  }

  val myLaserPrinter = new LaserPrinter
  val myInkjetPrinter = new InkjetPrinter
  val mySoftware = new Software
  val myNetwork = new Network
  val me = new User
  val myExperience1 = new PrintExperience(myLaserPrinter, mySoftware, myNetwork, me)
  val myExperience2 = new PrintExperience(myInkjetPrinter, mySoftware, myNetwork, me)

  def step1(): Unit = {
    myExperience1.summary.observe(Symbol("none"))
    val alg = VariableElimination(myLaserPrinter.powerButtonOn, myNetwork.state)
    alg.start()
    println("After observing that printing with the laser printer produces no result:")
    println("Probability laser printer power button is on = " + alg.probability(myLaserPrinter.powerButtonOn, true))
    println("Probability network is down = " + alg.probability(myNetwork.state, Symbol("down")))
    alg.kill()
  }

  def step2(): Unit = {
    myExperience2.summary.observe(Symbol("none"))
    val alg = VariableElimination(myLaserPrinter.powerButtonOn, myNetwork.state)
    alg.start()
    println("\nAfter observing that printing with the inkjet printer also produces no result:")
    println("Probability laser printer power button is on = " + alg.probability(myLaserPrinter.powerButtonOn, true))
    println("Probability network is down = " + alg.probability(myNetwork.state, Symbol("down")))
    alg.kill()
  }

  def main(args: Array[String]): Unit = {
    step1()
    step2()
  }
}

class PrinterProblemInheritanceTest extends AnyWordSpec with Matchers {
  Universe.createNew()
  val myLaserPrinter = new PrinterProblemInheritance.LaserPrinter
  val myInkjetPrinter = new PrinterProblemInheritance.InkjetPrinter
  val mySoftware = new PrinterProblemInheritance.Software
  val myNetwork = new PrinterProblemInheritance.Network
  val me = new PrinterProblemInheritance.User
  val myExperience1 = new PrinterProblemInheritance.PrintExperience(myLaserPrinter, mySoftware, myNetwork, me)
  val myExperience2 = new PrinterProblemInheritance.PrintExperience(myInkjetPrinter, mySoftware, myNetwork, me)
  
  "Printer Problem Inheritance" should {
    myExperience1.summary.observe(Symbol("none"))
    val alg1 = VariableElimination(myLaserPrinter.powerButtonOn, myNetwork.state)
    alg1.start()
    val btnOn1 = alg1.probability(myLaserPrinter.powerButtonOn, true)
    val netDown1 = alg1.probability(myNetwork.state, Symbol("down"))
    alg1.kill()

    "after observing that printing with the laser printer produces no result:" should {
      "produce a probability laser printer power button is on = 0.8573402523786461" taggedAs (BookExample) in {
        btnOn1 should be(0.8573402523786461)
      }
      "produce a probability network is down = 0.2853194952427" taggedAs (BookExample) in {
        netDown1 should be(0.2853194952427 +- 0.0000000000001)
      }
    }
    
    myExperience2.summary.observe(Symbol("none"))
    val alg2 = VariableElimination(myLaserPrinter.powerButtonOn, myNetwork.state)
    alg2.start()
    val btnOn2 = alg2.probability(myLaserPrinter.powerButtonOn, true)
    val netDown2 = alg2.probability(myNetwork.state, Symbol("down"))
    alg2.kill()

    "after observing that printing with the inkjet printer also produces no result:" should {
      "produce a probability laser printer power button is on = 0.8978039844824163" taggedAs (BookExample) in {
        btnOn2 should be(0.8978039844824163)
      }
      "produce a probability network is down = 0.4250135950242" taggedAs (BookExample) in {
        netDown2 should be(0.4250135950242 +- 0.0000000000001)
      }
    }
  }
}
