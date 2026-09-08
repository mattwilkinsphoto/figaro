# GVM mutual information

## Overview

`GaussVonMisesMutualInformation.compute(kernel)` measures how much dependence a fixed
Gauss-von Mises model expresses between its **complete linear vector X and its angle theta**.
It answers “how much angular uncertainty would knowing X remove, on average?”
It does not compare two candidate distributions, fit parameters, condition on new evidence,
or change a Figaro inference algorithm. It is an opt-in, deterministic diagnostic.

Values are in **nats**; divide by `math.log(2)` for bits. Zero means independence in the
mathematical model. A numerical interval including zero means the computation has not
resolved a positive dependence at that accuracy, not that independence has been proved.
Mutual information is symmetric in its two random-variable blocks, but is not a distance
between two laws. See the [cross-family metrics roadmap](INFORMATION_METRICS_ROADMAP.md).

Status: initial guarded implementation on the GVM development branch; local validation
and publication checks are recorded below. Remote CI/main promotion is a separate gate.
Older immutable library bundles do not contain this API; rebuild a commit containing it.

## Quick start: three steps

1. Import the fixed-kernel APIs and describe the joint law:

   ```scala
   import com.cra.figaro.library.atomic.continuous.*
   val model = GaussVonMisesDistribution(
     Vector(0.0), Vector(Vector(1.0)), 0.0,
     Vector(0.4), Vector(Vector(0.0)), 2.0)
   ```

2. Request the diagnostic:

   ```scala
   val result = GaussVonMisesMutualInformation.compute(model)
   ```

3. Check its status before using the value:

   ```scala
   import GaussVonMisesMutualInformation.Status
   result.status match {
     case Status.Estimated => println(s"${result.value.get} nats; ${result.interval}")
     case other => println(s"No usable value: $other")
   }
   ```

This example gives about **0.0977218984 nats**. Use
`result.value.get / math.log(2)` to convert that result to bits.
The [executable companion](../FigaroExamples/src/main/scala/com/cra/figaro/example/documentation/GaussVonMisesMutualInformationExample.scala)
runs with:

```sh
sbt "examples / Compile / runMain com.cra.figaro.example.documentation.GaussVonMisesMutualInformationExample"
```

## API reference

The only computational entry point is:

```scala
GaussVonMisesMutualInformation.compute(
  kernel: GaussVonMisesDistribution,
  tolerance: Double = 1e-8,
  maxHarmonics: Int = 128,
  maxEvaluations: Int = 16384,
  cancelled: () => Boolean = () => false
): GaussVonMisesMutualInformation.Result
```

| Parameter | Meaning and limits |
| --- | --- |
| `kernel` | Non-null fixed joint law. Numerical path: 1–32 linear coordinates, concentration at most 50, each absolute canonical beta/Gamma entry at most 1000. |
| `tolerance` | Positive finite absolute MI target in nats. Not a relative tolerance or accuracy certificate. |
| `maxHarmonics` | 1–256 Fourier coefficients. More terms can reduce truncation error, not the floating-point floor. |
| `maxEvaluations` | 1–131072 angular density evaluations across all grids, including discarded coarse grids. Setup is separately bounded and excluded. Not a wall-clock limit. |
| `cancelled` | Non-null cooperative callback. Return true to stop. Keep it cheap and thread-safe if shared by callers. |

Invalid arguments throw `IllegalArgumentException`. Cancellation or a pre-existing
thread interrupt throws `CancellationException` without clearing the interrupt flag.
Exceptions thrown by the caller's cancellation predicate propagate unchanged.

`Result` is immutable, with ordinary Scala case-class accessors, `copy`, and pattern
matching; these are data operations, not extra numerical algorithms. Inspect its fields:

| Field | Meaning |
| --- | --- |
| `status` | `Estimated`, `BudgetExhausted`, `NumericallyUnresolved`, or `UnsupportedRange`. |
| `value` | `Option[Double]` in nats. Present only for `Estimated`. |
| `interval` | Optional estimated `(lower, upper)` MI interval, clipped to mathematical nonnegativity and the estimated entropy ceiling. Not a confidence interval. |
| `upperBoundEstimate` | `kappa * I1/I0 - log(I0)` in nats on the numerical path; exact zero for an independence shortcut. Infinity before unavailable setup. |
| `harmonics`, `evaluations` | Actual work used. Coefficient setup includes bounded Bessel series and eigendecomposition; it is not counted as angular evaluations. |
| `truncationErrorEstimate` | Entropy allowance from a geometric Fourier tail, evaluated in floating point. |
| `quadratureErrorEstimate` | Four times the larger of two consecutive dyadic-grid differences. Heuristic, not certified. |
| `roundoffEstimate` | Heuristic coefficient, eigensystem, density evaluation and entropy-subtraction allowance. |
| `method` | `exact-independence` or `fourier-angular-entropy`. |

Unavailable error diagnostics can be positive infinity. A budget failure is not a
zero-MI result. The compiler-generated [continuous API reference](api/com.cra.figaro.library.atomic.continuous.md)
includes the complete public signatures, including case-class methods.

## Common patterns

### 1. Quantify dependence instead of assuming independence

Without this API, marginal variances or linear correlations do not summarize all
linear-angular dependence: a quadratic coupling can be dependent even with zero beta.

```scala
val curved = GaussVonMisesDistribution(
  Vector(0.0), Vector(Vector(1.0)), 0.0,
  Vector(0.0), Vector(Vector(1.2)), 8.0)
val dependence = GaussVonMisesMutualInformation.compute(curved)
require(dependence.status == GaussVonMisesMutualInformation.Status.Estimated)
println(dependence.value) // about 0.6637103410 nats, despite beta == 0
```

The returned quantity is a property of the supplied law, not an estimate of parameter
uncertainty or a statistical test from observed data.

### 2. Compare dependence across model configurations

Previously, KL was available to ask how different two laws were. Here compute each
law's MI to ask which expresses stronger dependence between its own variable blocks.

```scala
def scenario(beta: Double) = GaussVonMisesDistribution(
  Vector(0.0), Vector(Vector(1.0)), 0.0,
  Vector(beta), Vector(Vector(0.0)), 2.0)
val independent = GaussVonMisesMutualInformation.compute(scenario(0.0))
val coupled = GaussVonMisesMutualInformation.compute(scenario(0.4))
println((independent.value, coupled.value)) // Some(0.0), Some(0.0977218984...)
```

These are two within-law dependence values, **not** `KL(independent || coupled)`.
Do not subtract them and call the result a divergence. For comparing configurations
numerically, check both statuses and whether their estimated intervals overlap.

### 3. Handle a work limit explicitly

```scala
val limited = GaussVonMisesMutualInformation.compute(model, maxEvaluations = 1)
val next = limited.status match {
  case GaussVonMisesMutualInformation.Status.BudgetExhausted =>
    GaussVonMisesMutualInformation.compute(model, maxEvaluations = 16384)
  case _ => limited
}
println((next.status, next.value, next.evaluations))
```

Increasing a budget helps only a budget shortage. `NumericallyUnresolved` may require
a looser tolerance or an independently validated numerical method. `UnsupportedRange`
means this initial implementation has not been cleared for the parameters; do not
silently alter the distribution to fit the caps. Always inspect the retry status.

## Method and validation

In canonical coordinates, `Z ~ N(0,I)` and
`theta = alpha + beta'Z + Z'Gamma Z/2 + epsilon (mod 2*pi)`, with independent
von Mises noise of concentration kappa. An invertible linear transformation takes Z
to X; it does not change MI. Angular rotation also does not change MI.

Writing `A1 = I1(kappa)/I0(kappa)`, the conditional angular entropy is
`log(2*pi) + log(I0(kappa)) - kappa*A1`. Hence:

```text
I(X;theta) = H(theta) - H(theta | X)
          = U - average_theta [g(theta) log g(theta)]
U = kappa*A1 - log(I0(kappa));  g = 2*pi*p(theta)
0 <= I(X;theta) <= U
```

The conditional entropy follows directly from the
[von Mises density](https://docs.scipy.org/doc/scipy/reference/generated/scipy.stats.vonmises.html).
The entropy/MI framework is covered by the
[MIT information-theory notes](https://ocw.mit.edu/courses/6-441-information-theory-spring-2016/pages/lecture-notes/).
The implementation's reduction and numerical policy are independently developed here.

The [Bessel Fourier expansion](https://dlmf.nist.gov/10.35) gives coefficients
`I_j(kappa)/I0(kappa) * E[exp(i*j*(beta'Z + Z'Gamma Z/2))]`.
The Gaussian expectation is evaluated analytically in the eigencoordinates of Gamma,
with complex phase accumulated per eigenvalue to avoid determinant square-root branch
errors. This leaves only a **one-dimensional angular integral**, not a tensor grid
over every coordinate. It is still finite-precision quadrature, not a general closed form.

The positive defining Bessel series avoids unstable upward recurrence. The inequality
`I_(j+1)/I_j <= kappa/(2*(j+1))`, together with characteristic-function magnitude at
most one, gives a geometric omitted-density bound once its ratio is below one.
For density perturbation `d <= 1`, the code uses the conservative continuity allowance
`d*(2-log(d)+log(max(1,maximumDensity+d)))` for `x log(x)`. No strictly positive minimum
density is assumed. Tiny negative reconstructions are clipped only within the density
allowance, with the entropy allowance still charged to the result.

Angular grids begin at a power of two at least `max(64,4*(harmonics+1))` and double.
At least three independently evaluated grids are needed. Success requires the sum of
tail, grid-difference and roundoff allowances to fit the tolerance. Floating-point and
grid-difference allowances are heuristic: this is **not interval arithmetic certification**.

Reproducible validation:

```sh
python3 -m pip install mpmath==1.3.0  # optional research environment only
python3 -B -m unittest discover -s tools -p 'test_gvm_mutual_information.py' -v
sbt "figaro / Test / testOnly com.cra.figaro.test.modernization.GaussVonMisesMutualInformationTest"
```

The research implementation generates twelve checked fixtures using 60-digit
Gauss-Legendre integration and 160 harmonics. Selected fixtures are checked at 80 digits
and 192 harmonics. A separate positive Gaussian-mixture integration verifies marginal
densities without using the quadratic-form characteristic function; a positive chi-square
mixture independently checks the eight-dimensional phase-branch case. Scala tests cover
the oracle intervals, exact independence, uniform angles, weak/curved dependence,
six/eight/32 dimensions, coordinate/rotation invariance, cancellation, limits and concurrent calls.
These are numerical checks, not a proof over every admitted input.

### Local acceptance record (2026-09-08)

- 324 modernization tests in 29 suites pass, including the five MI regression groups
  and all twelve high-precision MI fixtures.
- 72 GVM research checks pass, including five MI checks; no Python runtime dependency
  is added to the Figaro library.
- The runnable MI example, 18 documentation-tool tests, seven artifact-validator tests,
  generated-reference freshness (11,526 public methods) and local documentation links pass.
- Thin, fat, source and Scaladoc jars rebuild and pass class/legal/isolation checks.
  A separate application using only the locally published library passes the MI oracle,
  interval and budget-refusal checks alongside its existing inference acceptance tests.
  The checked thin-jar SHA-256 is
  `8aaf5d92ade4f73a75b2ac8383eab4deb28a28ab28f45ee3fa0f6e455ac80cfb`.
- Four pre-existing Scaladoc warnings remain: classpath configuration, two parallel-type
  refinements and an unresolved `AlgorithmInactiveException` documentation link. No
  new MI compilation warning was reported. Remote CI is required before main promotion.

## Gotchas

- This computes MI of the **full vector versus angle**, not each linear coordinate,
  arbitrary coordinate partitions, conditional MI, or MI between two separate kernels.
- Mean/covariance changes at fixed **canonical** beta/Gamma do not change this quantity.
  Changing physical coupling while varying covariance can require changing canonical
  parameters; those are different experiments.
- Near independence, subtracting entropies can lose precision. No small nonzero beta
  or Gamma is treated as exact independence. An interval touching zero is inconclusive.
- Exact kappa-zero or beta/Gamma-zero shortcuts bypass numerical range caps and use
  no integration budget, but still validate arguments and honor cancellation.
- Differential entropy can be negative and depends on coordinate units. MI does not
  inherit that unit dependence under invertible transformations of either block.
- No sample-based MI estimator, posterior integration, automatic inference setting,
  multidimensional-angle support, certified enclosure, or measured speedup is claimed.
- The public result is data, not a validation token: manually constructing or copying
  a Result does not run the calculation.

## Related

[GVM distribution](GAUSS_VON_MISES.md), [conditional laws and moments](GVM_MOMENTS.md),
[KL and Mahalanobis diagnostics](GVM_DIAGNOSTICS.md),
[Bhattacharyya comparison](GVM_BHATTACHARYYA.md),
[scalar positive overlap](GVM_SCALAR_BHATTACHARYYA.md),
[family-first roadmap](../ROADMAP.md), and
[cross-family information metrics](INFORMATION_METRICS_ROADMAP.md).
This feature does not ingest reports or implement fusion, tracking, or propagation.
