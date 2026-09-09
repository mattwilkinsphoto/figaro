/*
 * Util.scala
 * Utility functions for atomic discrete elements.
 * 
 * Created By:      Avi Pfeffer (apfeffer@cra.com)
 * Creation Date:   Feb 25, 2011
 * 
 * Copyright 2017 Avrom J. Pfeffer and Charles River Analytics, Inc.
 * See http://www.cra.com or email figaro@cra.com for information.
 * 
 * See http://www.github.com/p2t2/figaro for a copy of the software license.
 */

package com.cra.figaro.library.atomic.discrete

import com.cra.figaro.util._
import scala.math.{ ceil, log }

object Util {
  /**
   * Generate a geometric distributed random variable.
   */
  def generateGeometric(probFail: Double) = {
    com.cra.figaro.library.atomic.DistributionNumerics.check()
    require(probFail.isFinite && probFail>=0 && probFail<1)
    if(probFail==0) 1 else {
      val x=ceil(log(com.cra.figaro.library.atomic.DistributionNumerics.open(random))/log(probFail))
      if(!x.isFinite || x<1 || x>Int.MaxValue) throw new ArithmeticException("Geometric draw outside Int range")
      x.toInt
    }
  }

  /**
   * Density of the given number of positive outcomes under a binomial random variable with the given number of trials.
   * Computing a binomial coefficient exactly can be very expensive for a large number of trials, so this method uses
   * an approximation algorithm when the number of trials is sufficiently large.
   */  
  def binomialDensity(numTrials: Int, probSuccess: Double, numPositive: Int): Double = {
    math.exp(com.cra.figaro.library.atomic.LegacyDensity.binomial(numTrials,probSuccess,numPositive))
  }
}

