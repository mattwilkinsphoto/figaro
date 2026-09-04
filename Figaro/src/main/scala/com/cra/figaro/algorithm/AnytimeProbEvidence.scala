/*
 * AnytimeProbEvidence.scala
 * Anytime algorithms that compute probability of evidence.
 * 
 * Created By:      Avi Pfeffer (apfeffer@cra.com)
 * Creation Date:   Jan 1, 2009
 * 
 * Copyright 2017 Avrom J. Pfeffer and Charles River Analytics, Inc.
 * See http://www.cra.com or email figaro@cra.com for information.
 * 
 * See http://www.github.com/p2t2/figaro for a copy of the software license.
 */

package com.cra.figaro.algorithm


/**
 * Anytime algorithms that compute probability of evidence.
 * A class that implements this trait must implement initialize, runStep, and computeProbEvidence methods.
 */

trait AnytimeProbEvidence extends ProbEvidenceAlgorithm with Anytime {
  private case object ComputeProbEvidence extends Service
  private case class ProbEvidence(probability: Double) extends Response

  def handle(service: Service): Response =
    service match {
      case ComputeProbEvidence =>
        ProbEvidence(computedResult)
    }

  /**
   * Returns the probability of evidence of the universe on which the algorithm operates.
   * Throws AlgorithmInactiveException if the algorithm is not active.
   */  
  def probabilityOfEvidence(): Double = {
    request(ComputeProbEvidence) match {
      case ProbEvidence(result) => result
      case _ => 0.0
    }    
  }

}
