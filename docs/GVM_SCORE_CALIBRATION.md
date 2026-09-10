# Finite-concentration GVM score calibration

## Overview

A Mahalanobis-von-Mises score says how far a point is from a fixed GVM's mode in
density terms. This module adds the missing interpretation: what fraction of draws
from **that known kernel** would have a smaller or larger score? It supplies cumulative
probabilities, direct upper tails and thresholds without substituting the
large-concentration chi-square approximation.

Use it when comparing a point with a specified GVM, choosing a modeled probability
region, or validating simulated draws. It does not fit parameters, calculate KL,
assess MCMC convergence, or implement report ingestion, fusion, filtering or propagation.
Calibration is numerical, not a new sampling strategy or a speedup to Figaro inference.

Shipped in [Figaro 6.1.0](MAVEN_CENTRAL.md); originally integrated at `755eb425`, with
[passing CI](https://github.com/mattwilkinsphoto/figaro/actions/runs/34176109863).
It is not in the immutable RC1 library bundle. Later increments retain their own gates.

## Quick start in three steps

1. Build or obtain a fixed [GVM kernel](GAUSS_VON_MISES.md):

   ```scala
   import com.cra.figaro.library.atomic.continuous.*
   val kernel = GaussVonMisesDistribution(Vector(0.0, 0.0),
     Vector(Vector(1.0, 0.0), Vector(0.0, 1.0)), 0.0,
     Vector(0.0, 0.0), Vector(Vector(0.0, 0.0), Vector(0.0, 0.0)), 0.1)
   ```

2. Create and retain a calibrator: `val calibration = kernel.scoreDistribution()`.
3. Choose a threshold: `val threshold = calibration.quantile(0.95)`.
   Compare `kernel.mahalanobisSquared(point) <= threshold`, where `point` is a
   `LinearAngular` value in the same coordinates and units as the kernel.

Both numbers are **squared scores**. Do not take the square root of just one side.

## Public API reference

All methods are deterministic. Import `com.cra.figaro.library.atomic.continuous.*`.

| API | Parameters | Returns | Example |
| --- | --- | --- | --- |
| `GaussVonMisesScoreDistribution.apply(linearDimension, kappa, absoluteTolerance = 1e-10, maxEvaluations = 100000)` | Dimension in 1..10000; concentration in 0..1e8; finite tolerance in 1e-12..1e-4; integer budget in 32..1000000 | Immutable calibrator, independent of mean, covariance and coupling | `GaussVonMisesScoreDistribution(2, 0.1)` |
| `kernel.scoreDistribution(absoluteTolerance = 1e-10, maxEvaluations = 100000)` | Same tolerance and budget; dimension/concentration from the kernel | New calibrator; retain it for repeated use | `kernel.scoreDistribution(1e-12, 200000)` |
| `cdf(score)` | Squared score; negative values and infinities allowed, NaN rejected | P(M <= score) in [0,1] | `calibration.cdf(6.186468)` |
| `survival(score)` | Same score contract | P(M > score), evaluated directly rather than `1-cdf` | `calibration.survival(10.0)` |
| `cdfEstimate(score)` | Same score contract | `GaussVonMisesScoreProbability`, with value and numerical diagnostics | `calibration.cdfEstimate(5.0).estimatedAbsoluteError` |
| `survivalEstimate(score)` | Same score contract | Same result type for the direct upper tail | `calibration.survivalEstimate(10.0).value` |
| `quantile(probability)` | Finite p in [0,1]; for interior p, `min(p, 1-p) >= 100*absoluteTolerance` | Squared threshold enclosing approximately p modeled mass; 0 at p=0, +Infinity at p=1 | `calibration.quantile(0.95)` |
| `inverseSurvival(tailProbability)` | Same probability/tolerance contract | Squared threshold exceeded with approximately that probability; +Infinity at 0, 0 at 1 | `calibration.inverseSurvival(0.05)` |
| `GaussVonMisesScoreExample.main(args)` | Empty `Array[String]`; documentation example package | Unit; prints checked comparisons and sample coverage, creates no Figaro universe | `GaussVonMisesScoreExample.main(Array.empty[String])` |

The calibrator exposes read-only `linearDimension`, `kappa`, `absoluteTolerance` and
`maxEvaluations`, with the meanings above. Probability results have library-internal
constructors and these immutable fields:

| Field | Meaning | Example |
| --- | --- | --- |
| `value` | Estimated probability, in [0,1] | `calibration.cdfEstimate(5).value` |
| `estimatedAbsoluteError` | Adaptive-quadrature error estimate, plus angular truncation bound and a roundoff allowance | `calibration.cdfEstimate(5).estimatedAbsoluteError` |
| `angularTruncationBound` | Bound on omitted angular mass; zero when integrating the full angular support | `calibration.cdfEstimate(5).angularTruncationBound` |
| `evaluations` | Integrand evaluation count; at kappa=0, one gamma evaluation; endpoints zero | `calibration.cdfEstimate(5).evaluations` |

The error field is **not** a rigorous interval-arithmetic bound or a statistical
confidence interval. Gamma-function error and floating-point cancellation are not
individually certified. `absoluteTolerance` is an absolute probability target, not
relative accuracy and not an error bound in squared-score units.

## Three common patterns

### 1. Replace an assumed chi-square threshold with finite-concentration calibration

Previously a user might approximate the law by chi-square with n+1 degrees of freedom:

```scala
import org.apache.commons.math3.distribution.ChiSquaredDistribution
val approximate = new ChiSquaredDistribution(kernel.dimension + 1)
  .inverseCumulativeProbability(0.95)
val calibrated = calibration.quantile(0.95)
println(calibration.cdf(approximate)) // actual modeled coverage of the approximation
```

For two linear coordinates and kappa=0.1:

| Approach | 95% squared-score threshold | Actual modeled enclosed mass |
| --- | --- | --- |
| Chi-square(n+1) approximation | 7.814728 | 0.977849 |
| Finite-concentration calibration | 6.186468 | 0.950000 |

The approximation labels a much larger region “95%” in this example. At kappa=0 the
correct law is exactly chi-square(n), with threshold 5.991465 for n=2. At large kappa
the extra angular component approaches chi-square(1). These are limiting facts, not
a universal concentration cutoff at which the approximation becomes adequate. Compare
actual coverage for the dimension, concentration and tail relevant to your model.

### 2. Report a point's modeled upper-tail probability

```scala
val point = LinearAngular(Vector(2.0, 1.0), 0.4)
val score = kernel.mahalanobisSquared(point)
val tail = calibration.survivalEstimate(score)
println(s"Squared score=$score, upper tail=${tail.value}")
println(s"Numerical error estimate=${tail.estimatedAbsoluteError}")
val threshold99 = calibration.inverseSurvival(0.01)
println(score > threshold99)
```

Previously the raw score alone had no probability attached. Now the upper tail states
the modeled probability of a draw being still less typical by this score. It is not
the probability that this particular point or the model is “correct.” Compute small
upper tails with `survival`, not `1-cdf`: subtraction can erase a representable tail.
Direct evaluation still cannot guarantee relative accuracy below the absolute tolerance,
and extreme tails can underflow. For inverse tails near 1e-8, consider
`kernel.scoreDistribution(absoluteTolerance = 1e-12)`; the minimum supported interior
tail is then 1e-10. More extreme inverse requests fail instead of suggesting precision
the implementation does not support.

### 3. Reuse calibration across kernels and check simulated coverage

```scala
val shared = GaussVonMisesScoreDistribution(2, 0.1)
val threshold = shared.quantile(0.95)
val rng = new scala.util.Random(7421L)
val draws = 40000
val covered = (0 until draws).count { _ =>
  kernel.mahalanobisSquared(kernel.sample(rng)) <= threshold
}
println(covered.toDouble / draws)
```

Previously one could estimate thresholds by sorting a large calibration sample.
The deterministic helper avoids sampling variability in threshold construction and
does not consume an RNG. The subsequent coverage experiment remains random: at 40,000
independent draws, its binomial standard error around 0.95 is about 0.00109.
One run of the coupled executable example obtains 0.94885, not exactly 0.95.

Reuse the same calibrator if kernels differ in mean, positive-definite covariance,
angular center or linear/quadratic coupling, **but have identical dimension and kappa**.
Compute each point's score using its own kernel. Rebuild calibration if dimension or
kappa changes. The immutable calibrator is safe for concurrent calls; it retains no
RNG or shared mutable work buffers. Store thresholds too when reusing one probability
level: quantiles are recomputed on each call and perform multiple integrations.

All three patterns run in the [checked example](../FigaroExamples/src/main/scala/com/cra/figaro/example/documentation/GaussVonMisesScoreExample.scala):

```sh
sbt "examples / Compile / runMain com.cra.figaro.example.documentation.GaussVonMisesScoreExample"
```

## Mathematics and numerical method

[Horwood and Poore, section 4.7](https://doi.org/10.1137/130917296) gives the independent
canonical residuals z ~ Normal(0,I_n) and phi ~ vonMises(0,kappa). Therefore

```text
M = ||z||^2 + 4 kappa sin(phi/2)^2 = chiSquare(n) + Y_kappa
P(M <= s) = integral f_VM(phi) F_chiSquare(n)(s - Y_kappa(phi)) dphi
```

This finite-concentration law is exact; its implementation uses numerical integration.
The upper tail uses the analogous chi-square survival function directly. Gamma P/Q
come from the existing [Commons Math dependency](https://commons.apache.org/proper/commons-math/javadocs/api-3.6.1/org/apache/commons/math3/special/Gamma.html).
No third-party implementation was copied and no runtime dependency was added.

Adaptive Simpson integration uses a concentration-scaled positive half-angle, seeded
panels, and a change of variable at the chi-square support boundary to smooth the
one-dimensional square-root endpoint. At high concentration an angular tail is omitted
only with an explicit envelope bound. For t=sqrt(kappa)*phi, the inequality
`sin(phi/2) >= phi/Pi` bounds the angular density by a constant times
`exp(-2*t*t/Pi^2)`. The returned truncation bound includes this omitted mass.

Quantiles bracket and bisect using the smaller probability tail. They stop on the
absolute probability tolerance or a relative squared-score bracket width of 1e-10
(absolute width 1e-10 below score 1), with bounded iterations. The per-integration
budget is not a total quantile budget: inversion can evaluate many probabilities.

## Gotchas and limitations

- Coverage assumes a known, fixed, correctly specified GVM. Plugging in parameters
  estimated from the same observations does not automatically preserve nominal coverage.
  Mixtures and arbitrary Figaro posteriors do not inherit this law.
- Repeated checks, selection of points after seeing the data, and multiple comparisons
  need their own statistical design. This is not an anytime-valid stopping criterion,
  an operational association policy, or a parameter-confidence procedure.
- `cdf` is the **score** CDF, not a multivariate GVM CDF or an angular CDF. A KL divergence
  between kernels has a different meaning and is not an input to this calibration.
- The numeric kernel accepts larger dimensions than may be practical to construct as
  dense joint kernels; 10000 is a calibration input limit, not a performance promise.
- Factory/argument errors throw `IllegalArgumentException`. Exhausted integration
  budgets, failed numeric checks or inversion throw `ArithmeticException`. Commons Math
  convergence exceptions can propagate. A minimal valid budget can still be too small.
- Interrupting the thread throws `CancellationException` at checked boundaries without
  clearing the flag. An individual third-party gamma calculation is not interrupted
  mid-call, but has an iteration cap.
- Ill-conditioned or overflow-sensitive physical-coordinate scoring retains the
  [underlying kernel's limitations](GVM_DIAGNOSTICS.md). Accurate calibration cannot
  repair an inaccurate score or an inappropriate model.

## Verification and related work

Local acceptance: 14 focused tests, all 234 modernization regressions across 18 suites,
three GVM executable examples, Scala API generation and thin-library packaging.
Remote CI explicitly includes the score suite and example. The exact milestone
`755eb425` passed [branch CI](https://github.com/mattwilkinsphoto/figaro/actions/runs/34176109863)
and was integrated on main; this does not certify later extensions.

The [focused tests](../Figaro/src/test/scala/com/cra/figaro/test/modernization/GaussVonMisesScoreTest.scala)
compare independent 80-digit angular-quadrature fixtures, the exact n=2 exponential
tail identity, both chi-square limits, inverse consistency, extreme inputs, numerical
budgets, concurrency and cancellation. Four coupled kernels have 95%/99% coverage
checks over 160,000 total independent draws with five-binomial-standard-deviation
tolerances. These tests are acceptance evidence, not an exhaustive numerical proof.
The [optional oracle](../tools/gauss_von_mises_score_reference.py) uses `mpmath==1.3.0`
outside the runtime and writes no files; run with `python -B`.

Related: [joint GVM](GAUSS_VON_MISES.md), [score and KL diagnostics](GVM_DIAGNOSTICS.md),
[moments and conditionals](GVM_MOMENTS.md), [circular foundation](VON_MISES.md),
[roadmap](../ROADMAP.md), [support inventory](DISTRIBUTION_SUPPORT.md),
[compiler API reference](api/README.md). [State gradients](GVM_GRADIENTS.md) are now
on main at CI-verified `141dcc15`. [Third-order expectation quadrature](GVM_QUADRATURE.md)
is on main at CI-verified `b06e957f`, with its own exactness/negative-weight limitations;
separate [Bhattacharyya](GVM_BHATTACHARYYA.md) and
[mutual-information](GVM_MUTUAL_INFORMATION.md) guides describe later diagnostics.
No fusion or propagation scope is added.
