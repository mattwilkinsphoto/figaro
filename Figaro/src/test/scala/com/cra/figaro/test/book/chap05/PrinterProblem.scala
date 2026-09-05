/*
 * PrinterProblem.scala 
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

package com.cra.figaro.test.book.chap05

import com.cra.figaro.language._
import com.cra.figaro.library.compound._
import com.cra.figaro.algorithm.factored.VariableElimination
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import com.cra.figaro.test.tags.BookExample

object PrinterProblem {
    val printerPowerButtonOn = Flip(0.95)
    val tonerLevel = Select(0.7 -> Symbol("high"), 0.2 -> Symbol("low"), 0.1 -> Symbol("out"))
    val tonerLowIndicatorOn =
      If(printerPowerButtonOn,
         CPD(tonerLevel,
             Symbol("high") -> Flip(0.2),
             Symbol("low") -> Flip(0.6),
             Symbol("out") -> Flip(0.99)),
         Constant(false))
    val paperFlow = Select(0.6 -> Symbol("smooth"), 0.2 -> Symbol("uneven"), 0.2 -> Symbol("jammed"))
    val paperJamIndicatorOn =
      If(printerPowerButtonOn,
         CPD(paperFlow,
             Symbol("smooth") -> Flip(0.1),
             Symbol("uneven") -> Flip(0.3),
             Symbol("jammed") -> Flip(0.99)),
         Constant(false))
    val printerState =
      Apply(printerPowerButtonOn, tonerLevel, paperFlow,
            (power: Boolean, toner: Symbol, paper: Symbol) => {
              if (power) {
                if (toner == Symbol("high") && paper == Symbol("smooth")) Symbol("good")
                else if (toner == Symbol("out") || paper == Symbol("out")) Symbol("out")
                else Symbol("poor")
              } else Symbol("out")
            })
    val softwareState = Select(0.8 -> Symbol("correct"), 0.15 -> Symbol("glitchy"), 0.05 -> Symbol("crashed"))
    val networkState = Select(0.7 -> Symbol("up"), 0.2 -> Symbol("intermittent"), 0.1 -> Symbol("down"))
    val userCommandCorrect = Flip(0.65)
    val numPrintedPages =
      RichCPD(userCommandCorrect, networkState, softwareState, printerState,
          (*, *, *, OneOf(Symbol("out"))) -> Constant(Symbol("zero")),
          (*, *, OneOf(Symbol("crashed")), *) -> Constant(Symbol("zero")),
          (*, OneOf(Symbol("down")), *, *) -> Constant(Symbol("zero")),
          (OneOf(false), *, *, *) -> Select(0.3 -> Symbol("zero"), 0.6 -> Symbol("some"), 0.1 -> Symbol("all")),
          (OneOf(true), *, *, *) -> Select(0.01 -> Symbol("zero"), 0.01 -> Symbol("some"), 0.98 -> Symbol("all")))
    val printsQuickly =
      Chain(networkState, softwareState,
            (network: Symbol, software: Symbol) =>
              if (network == Symbol("down") || software == Symbol("crashed")) Constant(false)
              else if (network == Symbol("intermittent") || software == Symbol("glitchy")) Flip(0.5)
              else Flip(0.9))
    val goodPrintQuality =
      CPD(printerState,
          Symbol("good") -> Flip(0.95),
          Symbol("poor") -> Flip(0.3),
          Symbol("out") -> Constant(false))
    val printResultSummary =
      Apply(numPrintedPages, printsQuickly, goodPrintQuality,
            (pages: Symbol, quickly: Boolean, quality: Boolean) =>
            if (pages == Symbol("zero")) Symbol("none")
            else if (pages == Symbol("some") || !quickly || !quality) Symbol("poor")
            else Symbol("excellent"))

  def step1() : Double = {
    val answerWithNoEvidence = VariableElimination.probability(printerPowerButtonOn, true)
    println("Prior probability the printer power button is on = " + answerWithNoEvidence)
    answerWithNoEvidence
  }

  def step2() : Double =  {
    printResultSummary.observe(Symbol("poor"))
    val answerIfPrintResultPoor = VariableElimination.probability(printerPowerButtonOn, true)
    println("Probability the printer power button is on given a poor result = " + answerIfPrintResultPoor)
    answerIfPrintResultPoor
  }

  def step3() : Double =  {
    printResultSummary.observe(Symbol("none"))
    val answerIfPrintResultNone = VariableElimination.probability(printerPowerButtonOn, true)
    println("Probability the printer power button is on given empty result = " + answerIfPrintResultNone)
    answerIfPrintResultNone
  }

  def step4() : Double =  {
    printResultSummary.unobserve()
    printerState.observe(Symbol("out"))
    val answerIfPrinterStateOut = VariableElimination.probability(printerPowerButtonOn, true)
    println("Probability the printer power button is on given " + "out printer state = " + answerIfPrinterStateOut)
    answerIfPrinterStateOut
  }

  def step4a() : Double =  {
    printResultSummary.observe(Symbol("none"))
    val answerIfPrinterStateOutAndResultNone = VariableElimination.probability(printerPowerButtonOn, true)
    println("Probability the printer power button is on given out printer state and empty result = " + answerIfPrinterStateOutAndResultNone)
    answerIfPrinterStateOutAndResultNone
  }  
  
  def step5() : Double =  {
    printResultSummary.unobserve()
    printerState.unobserve()
    val printerStateGoodPrior = VariableElimination.probability(printerState, Symbol("good"))
    println("Prior probability the printer state is good = " + printerStateGoodPrior)
    printerStateGoodPrior
  }

  def step5a() : Double =  {
    tonerLowIndicatorOn.observe(true)
    val printerStateGoodGivenTonerLowIndicatorOn = VariableElimination.probability(printerState, Symbol("good"))
    println("Probability printer state is good given low toner indicator = " + printerStateGoodGivenTonerLowIndicatorOn)
    printerStateGoodGivenTonerLowIndicatorOn
  }

  def step6() : Double =  {
    tonerLowIndicatorOn.unobserve()
    val softwareStateCorrectPrior = VariableElimination.probability(softwareState, Symbol("correct"))
    println("Prior probability the software state is correct = " + softwareStateCorrectPrior)
    softwareStateCorrectPrior
  }

  def step6a() : Double =  {
    networkState.observe(Symbol("up"))
    val softwareStateCorrectGivenNetworkUp = VariableElimination.probability(softwareState, Symbol("correct"))
    println("Probability software state is correct given network up = " + softwareStateCorrectGivenNetworkUp)
    softwareStateCorrectGivenNetworkUp
  }

  def step7() : Double =  {
    networkState.unobserve()
    printsQuickly.observe(false)
    val softwareStateCorrectGivenPrintsSlowly = VariableElimination.probability(softwareState, Symbol("correct"))
    println("Probability software state is correct given prints slowly = " + softwareStateCorrectGivenPrintsSlowly)
    softwareStateCorrectGivenPrintsSlowly
  }

  def step7a() : Double =  {
    networkState.observe(Symbol("up"))
    val softwareStateCorrectGivenPrintsSlowlyAndNetworkUp = VariableElimination.probability(softwareState, Symbol("correct"))
    println("Probability software state is correct given prints slowly and network up = " + softwareStateCorrectGivenPrintsSlowlyAndNetworkUp)
    softwareStateCorrectGivenPrintsSlowlyAndNetworkUp
  }  
  
  def main(args: Array[String]): Unit = {
    step1()
    step2()
    step3()
    step4()
    step4a()
    step5()
    step5a()
    step6()
    step6a()
    step7()
    step7a()
  }
}

class PrinterProblemTest extends AnyWordSpec with Matchers {
  Universe.createNew()
  "Printer Problem" should {
    "answerWithNoEvidence equals 0.95" taggedAs (BookExample) in {
      PrinterProblem.step1() should be(0.95)
    }
    "answerIfPrintResultPoor equals 1.0" taggedAs (BookExample) in {
      PrinterProblem.step2() should be(1.0)
    }
    "answerIfPrintResultNone equals 0.8573402523786461" taggedAs (BookExample) in {
      PrinterProblem.step3() should be(0.8573402523786461)
    }
    "answerIfPrinterStateOut equals 0.6551724137931032" taggedAs (BookExample) in {
      PrinterProblem.step4() should be(0.6551724137931032)
    }
    "answerIfPrinterStateOutAndResultNone equals 0.6551724137931033" taggedAs (BookExample) in {
      PrinterProblem.step4a() should be(0.6551724137931033)
    }
    "printerStateGoodPrior equals 0.39899999999999997" taggedAs (BookExample) in {
      PrinterProblem.step5() should be(0.39899999999999997)
    }
    "printerStateGoodGivenTonerLowIndicatorOn approximately equals 0.2339832869" taggedAs (BookExample) in {
      PrinterProblem.step5a() should be(0.2339832869 +- 0.00000000001)
    }
    "softwareStateCorrectPrior equals 0.8" taggedAs (BookExample) in {
      PrinterProblem.step6() should be(0.8)
    }
    "softwareStateCorrectGivenNetworkUp equals 0.7999999999999999" taggedAs (BookExample) in {
      PrinterProblem.step6a() should be(0.7999999999999999)
    }
    "softwareStateCorrectGivenPrintsSlowly equals 0.6197991391678623" taggedAs (BookExample) in {
      PrinterProblem.step7() should be(0.6197991391678623)
    }
    "softwareStateCorrectGivenPrintsSlowlyAndNetworkUp equals 0.39024390243902435" taggedAs (BookExample) in {
      PrinterProblem.step7a() should be(0.39024390243902435)
    }
  }
}
