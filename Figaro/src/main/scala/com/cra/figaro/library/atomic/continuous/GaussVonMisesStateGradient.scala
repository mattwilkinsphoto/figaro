package com.cra.figaro.library.atomic.continuous

/** Immutable derivatives with respect to a GVM point, not distribution parameters.
  * Obtain through logDensityGradient or mahalanobisSquaredGradient.
  * This is a tangent/covector result, not a LinearAngular state: never wrap angular.
  * @param linear partial derivatives in physical coordinate order, per coordinate unit
  * @param angular partial derivative with respect to the angle, per radian
  */
final class GaussVonMisesStateGradient private[continuous] (
  val linear: Vector[Double], val angular: Double)
