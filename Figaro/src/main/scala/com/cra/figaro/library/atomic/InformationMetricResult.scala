package com.cra.figaro.library.atomic

/** Analytic/Estimated are finite successes; Infinite is a proved support/special-case result.
  * Numerical failure never masquerades as mathematical infinity or independence.
  */
enum InformationMetricStatus { case Analytic, Estimated, Infinite, Unsupported, BudgetExhausted, NumericallyUnresolved }

/** Immutable information diagnostic, in natural-log units (nats).
  * @param status method outcome; Estimated is not an accuracy certificate
  * @param value finite nats for successful calculations, positive infinity only for Infinite, absent on refusal
  * @param errorEstimate absolute numerical allowance in nats; heuristic floating-point accounting, not a confidence interval
  * @param evaluations integrand/mass terms actually evaluated; analytic setup is excluded
  * @param method algorithm/reduction label
  */
final case class InformationMetricResult(status: InformationMetricStatus,value: Option[Double],errorEstimate: Double,evaluations: Int,method: String)

private[atomic] object MetricCalculation {
  import InformationMetricStatus.*
  def unavailable(status: InformationMetricStatus,work: Int=0,method: String="unavailable") =
    InformationMetricResult(status,None,Double.PositiveInfinity,work,method)
  def infinite = InformationMetricResult(Infinite,Some(Double.PositiveInfinity),0,0,"support")
  def analytic(value: Double,tolerance: Double,magnitude: Double=1,method: String="analytic") = {
    val error=128*math.ulp(1.0)*(1+math.abs(magnitude)+math.abs(value))
    if(!value.isFinite || !error.isFinite || value < -error || error > tolerance) unavailable(NumericallyUnresolved,0,method)
    else InformationMetricResult(Analytic,Some(math.max(0,value)),error,0,method)
  }
  def identity = InformationMetricResult(Analytic,Some(0),0,0,"identity")
}
