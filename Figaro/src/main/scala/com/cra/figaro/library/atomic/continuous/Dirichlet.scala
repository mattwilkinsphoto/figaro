/*
 * Dirichlet.scala
 * Elements representing Dirichlet distributions.
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
 * Synapski		Oct 13, 2014
 * Paul Philips		May 23, 2017
 */

package com.cra.figaro.library.atomic.continuous

import scala.collection.mutable
import scala.math.log
import scala.math.pow

import com.cra.figaro.algorithm.ValuesMaker
import com.cra.figaro.algorithm.lazyfactored.ValueSet
import com.cra.figaro.language._
import com.cra.figaro.util._

import com.cra.figaro.util.SpecialFunctions.{ gamma, logGamma }
import argonaut._
import argonaut.Argonaut._
/**
 * Dirichlet distributions in which the parameters are constants.
 * These Dirichlet elements can also serve as parameters for ParameterizedSelect.
 *
 * @param alphas the prior concentration parameters
 */
class AtomicDirichlet(name: Name[Array[Double]], val alphas: Array[Double], collection: ElementCollection)
    extends Element[Array[Double]](name, collection) with Atomic[Array[Double]] with ArrayParameter with Dirichlet with HasLogDensity[Array[Double]] {
  require(alphas!=null && alphas.length>=2 && alphas.forall(a => a.isFinite && a>0) && alphas.sum.isFinite)
  private val samplingAlphas=alphas.clone()

  /**
   * The number of concentration parameters in the Dirichlet distribution.
   */
  val size = alphas.size
  def alphaValues: Array[Double] = concentrationParameters.toArray
  type Randomness = Array[Double]

  def generateRandomness(): Array[Double] = {
    val gs = samplingAlphas map (Util.generateGamma(_))
    val maximum=gs.max
    val scaled=gs.map(_/maximum); val sum=scaled.sum
    val value=scaled.map(_/sum)
    if(value.exists(x => !x.isFinite || x<=0 || x>=1)) throw new ArithmeticException("Dirichlet draw collapsed to boundary")
    value
  }

  def generateValue(rand: Randomness) = rand

  private val sumAlphas = alphas reduceLeft (_ + _)
  private val prodGammas = alphas map (gamma(_)) reduceLeft (_ * _)

  /**
   * The normalizing factor.
   */
  private val normalizer = gamma(sumAlphas) / prodGammas

  private def onePow(xAlpha: (Double, Double)) = pow(xAlpha._1, xAlpha._2 - 1)

  /**
   * Density of a value.
   */
  def logDensity(xs: Array[Double]): Double = com.cra.figaro.library.atomic.LegacyDensity.dirichlet(samplingAlphas,xs)
  override def logp(xs: Array[Double]): Double = logDensity(xs)
  override def density(xs: Array[Double]) = math.exp(logDensity(xs))

  /**
   * The learned concentration parameters of the Dirichlet distribution
   */
  var concentrationParameters: mutable.Seq[Double] = mutable.Seq(alphas*)

  def maximize(sufficientStatistics: Seq[Double]) = {
    require(sufficientStatistics.size == concentrationParameters.size)
    for (i <- sufficientStatistics.indices) {
      concentrationParameters(i) = sufficientStatistics(i) + alphas(i)
    }
  }

  private val vector = alphas.map(a => 0.0)

  private[figaro] override def sufficientStatistics[A](i: Int): Seq[Double] = {
    val result = alphas.map(a => 0.0)
    require(i < result.size)
    result.update(i, 1.0)
    result.toIndexedSeq
  }

  override def sufficientStatistics[A](a: A): Seq[Double] = {
    val result = vector
    result.toIndexedSeq
  }

  override def zeroSufficientStatistics: Seq[Double] = {
    val result = vector
    result.toIndexedSeq
  }

  override def expectedValue: Array[Double] = {

    val sumObservedAlphas = concentrationParameters reduceLeft (_ + _)
    val result = new Array[Double](size)

    concentrationParameters.zipWithIndex.foreach {
      case (v, i) => {
        result(i) = (v) / (sumObservedAlphas)
      }
    }

    result

  }

  override def MAPValue: Array[Double] = {
    val sumObservedAlphas = concentrationParameters reduceLeft (_ + _)
    val result = new Array[Double](size)

    concentrationParameters.zipWithIndex.foreach {
      case (v, i) => {
        result(i) =
          if (sumObservedAlphas == size) 1.0 / size
          else (v - 1) / (sumObservedAlphas - size)
      }
    }
    result
  }

  override def toString = "Dirichlet(" + alphas.mkString(", ") + ")"
}

/**
 * Dirichlet distributions in which the parameters are elements.
 */
class CompoundDirichlet(name: Name[Array[Double]], alphas: Array[Element[Double]], collection: ElementCollection)
    extends NonCachingChain[List[Double], Array[Double]](
      name,
      new Inject("", alphas.toIndexedSeq, collection),
      (aa: Seq[Double]) => new AtomicDirichlet("", aa.toArray, collection),
      collection)
    with Dirichlet {

  def alphaValues = alphas.map(_.value)

  override def toString = "Dirichlet(" + alphas.mkString(", ") + ")"
}

trait Dirichlet extends Continuous[Array[Double]] {

  /**
   * Current alpha values.
   */
  def alphaValues: Array[Double]

  private def sumAlphasLogGamma = logGamma(alphaValues.sum)
  private def prodGammasLog = alphaValues.map(logGamma).sum

  /**
   * The normalizing factor.
   */
  private def normalizer = sumAlphasLogGamma - prodGammasLog

  def logp(values: Array[Double]) = com.cra.figaro.library.atomic.LegacyDensity.dirichlet(alphaValues,values)

}

object Dirichlet extends Creatable {

  //Needs to be a nested field or a jEmptyArray
  implicit def DirichletEncodeJson: EncodeJson[Dirichlet] = EncodeJson((d: Dirichlet) =>
    ("name" := d.name.string) ->: ("alphaValues" := jArray((for (a <- d.alphaValues) yield { jNumber(a) }).toList.flatten)) ->: jEmptyObject)

  implicit def DirichletDecodeJson(implicit collection: ElementCollection): DecodeJson[AtomicDirichlet] =
    DecodeJson(c => for {
      alphaValues <- (c --\ "alphaValues").as[List[Double]]
      name <- (c --\ "name").as[String]
    } yield Dirichlet(alphaValues.toArray)(using name, collection))

  /**
   * Create a Dirichlet distribution in which the parameters are constants.
   */
  def apply(alphas: Double*)(implicit name: Name[Array[Double]], collection: ElementCollection) =
    new AtomicDirichlet(name, alphas.toArray, collection)

  def apply(alphas: Array[Double])(implicit name: Name[Array[Double]], collection: ElementCollection) =
    new AtomicDirichlet(name, alphas, collection)

  /**
   * Create a Dirichlet distribution in which the parameters are elements.
   */
  def apply(alphas: Element[Double]*)(implicit name: Name[Array[Double]], collection: ElementCollection) =
    new CompoundDirichlet(name, alphas.toArray, collection)

  type ResultType = Array[Double]

  def create(args: List[Element[?]]) = apply(args.map(_.asInstanceOf[Element[Double]])*)
}
