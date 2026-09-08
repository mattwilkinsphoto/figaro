# Guarded GVM Bhattacharyya comparison

Status: first bounded Scala implementation integrated on main at `251f9540` after
[passing CI](https://github.com/mattwilkinsphoto/figaro/actions/runs/34188404684).
This does not replace the RC1 bundle or declare a tagged release. Follow-on performance
and reliability assessments are now integrated on main through CI-verified `e30c8b03`.
The separate [opt-in scalar positive API](GVM_SCALAR_BHATTACHARYYA.md) is now integrated
on main at CI-verified `21269b97`. It is never selected
automatically by this Fourier entry point.

## Overview: what it does and when to use it

Use Bhattacharyya comparison when you want a **symmetric measure of overlap between
two fixed GVM distributions** in the same physical coordinates. Unlike directed KL,
swapping the two inputs describes the same mathematical comparison. Neither this
distance nor KL is generally a metric satisfying the triangle inequality.

The affinity is `BC = integral sqrt(p*q)` over the joint linear/angular space, and
the distance is `DB = -log(BC)` in nats. Identical laws have BC=1 and DB=0; less overlap
means a larger distance. No evidence is ingested and no output GVM is constructed.
This is not a sampler, posterior update, filter, fusion API or decision threshold.

The method uses analytic special cases when available. Otherwise it integrates the
angle analytically and evaluates a Fourier/Gaussian series, avoiding an exponentially
large tensor grid. The [research derivation](GVM_BHATTACHARYYA_RESEARCH.md) explains
the mathematics and independent controls.

The important difference from a bare distance function is that this API can say
**unresolved**. It exposes a distance only when its estimated numerical accuracy meets
your requested tolerance. It does not pretend that adding harmonics fixes floating-point
cancellation. A resolved result is based on numerical estimates, not a rigorous
interval-arithmetic certificate or statistical confidence interval.

## Quick start in three steps

1. Define two fixed distributions:

   ```scala
   import com.cra.figaro.library.atomic.continuous.*
   val p = GaussVonMisesDistribution(
     Vector(0.3), Vector(Vector(1.21)), 0.2,
     Vector(0.7), Vector(Vector(0.3)), 4.5)
   val q = GaussVonMisesDistribution(
     Vector(-0.4), Vector(Vector(0.64)), -0.5,
     Vector(-0.2), Vector(Vector(-0.15)), 1.2)
   ```

2. Compare them with an absolute distance tolerance:

   ```scala
   val result = GaussVonMisesBhattacharyya.compare(p, q, absoluteTolerance = 1e-8)
   ```

3. Inspect the result before using a distance:

   ```scala
   result.distance match {
     case Some(value) => println(s"Bhattacharyya distance: $value nats")
     case None => println(s"${result.status}: ${result.message}")
   }
   ```

This example resolves near 0.355509913 nats. Never replace `None` with zero, infinity
or an arbitrary default: each would change an unresolved numerical result into a
substantive claim about the distributions.

## API reference

All runtime types are in `com.cra.figaro.library.atomic.continuous`. Result construction
is internal to the library; callers use `GaussVonMisesBhattacharyya.compare`.

| Function | Parameters | Returns | Example |
| --- | --- | --- | --- |
| `GaussVonMisesBhattacharyya.compare(p, q, absoluteTolerance = 1e-8, maxHarmonics = 256)` | Non-null fixed GVMs of matching dimension; positive finite absolute tolerance in nats; integer harmonic budget in 0..512 | Immutable `GaussVonMisesBhattacharyyaResult`, including a status on unsupported or numerically unresolved input | `GaussVonMisesBhattacharyya.compare(p, q, 1e-7, 128)` |
| `result.resolved` | None | Boolean, true only for `Resolved` | `if (result.resolved) println(result.distance.get)` |
| `result.logCoefficient` | None | `Option[Double]`: negative resolved distance; None otherwise | `println(result.logCoefficient)` |
| `result.coefficient` | None | `Option[Double]`: exponential of the resolved log coefficient; can underflow to zero | `println(result.coefficient)` |
| `GaussVonMisesBhattacharyyaExample.main(args)` | Empty `Array[String]`; documentation example package | Unit; runs checked exact, unresolved and six-dimensional accuracy/work examples | `GaussVonMisesBhattacharyyaExample.main(Array.empty[String])` |

Read-only limits: `GaussVonMisesBhattacharyya.MaxKappa = 50.0` and
`MaxDimension = 32`. These are stricter than the GVM kernel's own construction limits.
They are not adjustable knobs in this release.

Read-only result fields:

| Field | Type and meaning |
| --- | --- |
| `status` | `GaussVonMisesBhattacharyyaStatus`, one of the four outcomes below |
| `distance` | `Option[Double]`, resolved symmetric distance in nats; None for every unresolved status |
| `estimatedDistanceInterval` | Optional `(lower, upper)` numerical interval including tail and rounding estimates; upper may be positive infinity; **not certified** |
| `gaussianDistance` | Optional Gaussian contribution DG; may be absent after a preprocessing/numerical failure |
| `angularAffinityEstimate` | Optional raw estimate of F, where `DB = DG - log(F)`; a partial Fourier sum may be negative when unresolved |
| `angularTruncationBound` | Analytic harmonic-tail bound evaluated in Double, excluding rounding; zero for analytic shortcuts, infinity when unavailable |
| `angularRoundoffEstimate` | Heuristic absolute allowance on F for coefficient, matrix and phase arithmetic; not an error theorem |
| `harmonicsUsed` | Largest positive harmonic included; zero for analytic shortcuts or a zero-harmonic partial sum |
| `method` | `identity`, `gaussian`, `constant-angular`, `one-uniform`, `fourier` or `unavailable` |
| `message` | Explanation for people; use `status`, not message parsing, for application logic |

For example, `result.estimatedDistanceInterval`, `result.harmonicsUsed` and
`result.angularRoundoffEstimate` let you inspect accuracy and work without assuming
that the smallest displayed tail measures the entire numerical error.

| Status | Meaning | Appropriate response |
| --- | --- | --- |
| `Resolved` | Maximum estimated distance error fits the requested absolute tolerance | Use `distance`; retain diagnostics when accuracy matters |
| `BudgetExhausted` | Tail uncertainty still dominates when the harmonic budget is reached | Consider a larger explicit budget, at most 512, or an independent method |
| `NumericallyUnresolved` | Rounding/cancellation, conditioning, phase range or arithmetic checks prevent resolution | Do not merely increase the harmonic count; assess scaling, required accuracy or an independent method |
| `UnsupportedRange` | Nonidentity concentration or dimension is outside this implementation's range | Preserve the model; do not silently reduce concentration or dimension to obtain a number |

## Three common patterns

### 1. Compare full distributions and check a Gaussian reduction

```scala
val forward = GaussVonMisesBhattacharyya.compare(p, q)
val reverse = GaussVonMisesBhattacharyya.compare(q, p)
println((forward.distance, reverse.distance))
println((forward.gaussianDistance, forward.angularAffinityEstimate))

val g0 = GaussVonMisesDistribution(Vector(0.0), Vector(Vector(1.0)),
  0, Vector(0.0), Vector(Vector(0.0)), 0)
val g2 = GaussVonMisesDistribution(Vector(2.0), Vector(Vector(1.0)),
  0, Vector(0.0), Vector(Vector(0.0)), 0)
val gaussian = GaussVonMisesBhattacharyya.compare(g0, g2, maxHarmonics = 0)
println(gaussian.distance) // approximately Some(0.5)
println(g0.klDivergence(g2)) // approximately 2.0
```

With equal Gaussian covariances and identical angular conditionals, DB is one eighth
of squared Mahalanobis separation. Gaussian directed KL is one half, so DB=KL/4
**in this special case**, not generally. Comparing the scalar scores for individual
states would be a different operation from comparing two full distributions.

### 2. Keep unresolved outcomes visible

```scala
val limited = GaussVonMisesBhattacharyya.compare(p, q, maxHarmonics = 0)
limited.status match {
  case GaussVonMisesBhattacharyyaStatus.Resolved => println(limited.distance.get)
  case GaussVonMisesBhattacharyyaStatus.BudgetExhausted =>
    println("Choose a larger budget or another integration method")
  case GaussVonMisesBhattacharyyaStatus.NumericallyUnresolved =>
    println("Numerical accuracy remains unresolved")
  case GaussVonMisesBhattacharyyaStatus.UnsupportedRange =>
    println("Model is outside this comparison implementation's range")
}
```

For equal standard Gaussian marginals and constant, opposed angular centers at kappa=50,
the analytic path resolves DB near 47.127575502. Introducing a small varying coupling
in one center can remove that shortcut while leaving extremely little overlap. The
Fourier calculation then reports `NumericallyUnresolved`, with no distance. This is a
necessary numerical distinction, not evidence that the slightly changed model has
infinite distance. The executable example checks both cases.

### 3. Replace a costly tensor reference after independent accuracy checks

For a six-dimensional standard Gaussian marginal, kappa=4 and conditional centers
`+0.2 * sum(x)` and `-0.2 * sum(x)`, independent high-precision evaluation gives
DB=0.288667958949177104. The checked example compares both approaches:

| Method | Work count | Observed absolute distance error |
| --- | --- | --- |
| Guarded series, requested tolerance 1e-6 | 5 positive harmonics, plus preprocessing and coefficient lookahead | about 1.95e-14 |
| Tensor Gaussian order 3, angular order 2 | 1,458 callbacks | about 3.72e-4 |
| Tensor Gaussian order 5, angular order 2 | 31,250 callbacks | about 6.92e-7 |
| Tensor Gaussian order 7, angular order 2 | 235,298 callbacks | about 1.36e-9 |

Both the series and order-5 tensor rule meet a verified 1e-6-nat error target here.
A harmonic and a callback are not equal-cost operations, so these counts are **not
a measured wall-clock speedup**. The common Gaussian marginal makes the reference
especially simple. Its angular order is two only because the angular integral has
already been eliminated; it is not a general-purpose GVM angular-order recommendation.
The series may stop much sooner than needed on this benign case, but the requested
tolerance is not a promise of the much smaller observed error on another model.

Run all three checked patterns:

```sh
sbt "examples / Compile / runMain com.cra.figaro.example.documentation.GaussVonMisesBhattacharyyaExample"
```

## Numerical contracts and gotchas

- Except for identical stored kernels, dimensions must be at most 32 and both
  concentrations at most 50. Identity returns exact zero even outside those limits.
  Equal laws with differently expressed parameters do not necessarily take that shortcut.
- The Gaussian calculation rescales common coordinates, then uses Cholesky solves and
  log determinants. Scaled covariance eigenvalues must exceed 1e-12 and their condition
  ratios must not exceed 1e8. Otherwise resolution fails explicitly. Valid construction
  of a GVM does not imply every pair can be compared at every requested tolerance.
- General phase preprocessing rejects an absolute constant above 1e6 or summed absolute
  linear/quadratic coefficients above 1e4 in the auxiliary whitened coordinates.
  Overflow or factorization/eigensystem failures also give `NumericallyUnresolved`.
  Uniform-angle shortcuts bypass irrelevant coupling, including very large stored values.
- The general calculation uses log-scaled, positive Bessel defining series for its
  coefficients, compensated harmonic accumulation, and eigenvalue-wise continuous
  phases. It never takes a single principal square root of a complex determinant.
  No new runtime dependency is added.
- The harmonic tail is bounded using the positive-coefficient ratio derived in the
  [research note](GVM_BHATTACHARYYA_RESEARCH.md). One coefficient beyond the retained
  harmonic is evaluated for the bound. The budget counts the highest retained harmonic,
  not Bessel operations, processor time or memory allocations. The internal Bessel
  series has a separate 1,000-iteration guard.
- The rounding allowance is **heuristic**: it grows with dimension, scaled covariance
  conditioning, operand sensitivity in phase preprocessing and harmonic order. A separate
  Gaussian distance allowance is included in the displayed interval. These checks are
  supported by regression fixtures, not a proof covering every model in the input range.
  Rigorous rounding-error intervals and broader concentration support remain future work.
- Resolution requires positive overlap and an estimated distance interval no farther
  from the reported value than `absoluteTolerance` in either direction. Increasing
  tolerance is an application accuracy decision, not a way to establish the original
  requested accuracy. Tiny negative values within a rounding allowance can be normalized
  to zero; negative or inconsistent results beyond allowances are not silently accepted.
- `coefficient` can underflow to zero even when a finite distance resolves; preserve
  `logCoefficient` for very small overlap. Unresolved results have neither coefficient
  nor distance, but may have raw partial-sum diagnostics and an interval with infinite
  upper endpoint. Do not treat that endpoint as the actual distance.
- Inputs must use the same coordinate order, units and radian convention. A common
  invertible change of variables preserves the mathematical divergence, but extreme
  finite-precision transformations can defeat the numerical checks.
- Null/mismatched inputs, nonpositive/nonfinite tolerances and budgets outside 0..512
  throw `IllegalArgumentException`. Cancellation throws `CancellationException` and
  preserves the interrupt flag. Checks occur throughout preprocessing and harmonic work;
  third-party linear-algebra calls cannot be forcibly interrupted internally.
- Calls keep separate work buffers and immutable results. Fixed kernels can be shared
  across threads for independent comparisons; this does not make Figaro universes or
  elements thread-safe. No automatic quadrature fallback, model modification or inference
  change occurs when comparison is unresolved.

## Verification and related work

The [Scala suite](../Figaro/src/test/scala/com/cra/figaro/test/modernization/GaussVonMisesBhattacharyyaTest.scala)
checks exact reductions, high-precision one/two/six/eight-dimensional fixtures, concentration
boundaries, near-identity and tiny-overlap behavior, units, symmetry, budgets, unsupported
input, cancellation, conditioning and concurrent calls. The
[optional reference generator](../tools/gvm_bhattacharyya_research.py) reproduces the
fixtures at 60 digits using `mpmath==1.3.0`; run it with `python -B`. The separate Python
research suite retains direct angular and positive linear integration controls.

The [matched-accuracy timing study](GVM_BHATTACHARYYA_PERFORMANCE.md) now measures both
fresh and reused tensor rules, including a faster problem-specific reduced-rule control.
The follow-on [scalar API timing study](GVM_SCALAR_PERFORMANCE.md), at a tighter 1e-8-nat
target, favors Fourier for ordinary coupled scalar cases; positive integration remains
an explicit recovery option for eligible tiny-overlap refusals, not an automatic fallback.
The [96-pair reliability grid](GVM_BHATTACHARYYA_RELIABILITY.md) now checks concentration,
opposition, unequal Gaussian marginals and dimension at three tolerances. Eligibility
within the concentration/dimension caps does not guarantee resolution.
See the [roadmap](../ROADMAP.md) for acceptance/CI status. Next work is
tighter high-concentration bounds and stronger numerical
certification, without extending this API to report ingestion, fusion or filtering.
[Mutual information](GVM_MUTUAL_INFORMATION.md) is a separate guarded diagnostic
of dependence within one law, not an extension of this two-law comparison API.

Related: [joint GVM](GAUSS_VON_MISES.md), [KL and residuals](GVM_DIAGNOSTICS.md),
[analytic moments](GVM_MOMENTS.md), [tensor reference](GVM_TENSOR_QUADRATURE.md),
[order comparison](GVM_QUADRATURE_COMPARISON.md), [research derivation](GVM_BHATTACHARYYA_RESEARCH.md),
[generated API reference](api/README.md).
