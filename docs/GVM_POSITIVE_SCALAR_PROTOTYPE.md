# Positive scalar GVM overlap: end-to-end Scala prototype

Status: test-only Scala prototype, locally validated; CI/integration pending. No public
API or automatic fallback is added. Production library sources, numerical limits,
dependencies and release artifacts remain unchanged.

## Overview

The [Python assessment](GVM_BHATTACHARYYA_POSITIVE_RESEARCH.md) showed that positive
integration can avoid cancellation in very small scalar overlaps, but used
high-precision research preprocessing. This prototype closes that specific gap:
it accepts two actual `GaussVonMisesDistribution` kernels and computes the Gaussian
overlap, transformed phase and positive integral entirely with JVM Double arithmetic.

All **84 scalar pairs in both directions (168 comparisons)** met a 1e-8-nat absolute
distance target and included the oracle in their estimated intervals. The largest
observed error was **3.51e-10 nats**; the largest work count was **25,098 integrand
evaluations**, below the default 50,000 cap. Ten unequal-concentration pairs also
passed in both directions, alongside Gaussian reductions, extreme common unit changes,
budget/precision refusals, interruption and concurrent-call checks.

These are bounded fixture results, not a certified error guarantee or an application
success rate. All **307 modernization tests across 25 suites** pass. The thin library
was rebuilt and checked: it contains no test/prototype classes. This code deliberately
lives under `Figaro/src/test`, rather than becoming an unsupported consumer API.

## Quick start in three steps

1. Run the focused prototype checks:
   ```text
   sbt "figaro / Test / testOnly com.cra.figaro.test.modernization.GvmPositiveScalarPrototypeTest"
   ```
2. Inspect the [prototype](../Figaro/src/test/scala/com/cra/figaro/test/modernization/GvmPositiveScalarPrototype.scala)
   and [end-to-end tests](../Figaro/src/test/scala/com/cra/figaro/test/modernization/GvmPositiveScalarPrototypeTest.scala).
   The summary reports 168 main-grid comparisons, maximum error and maximum work;
   the other focused tests separately cover additional cases.
3. In applications, continue using the [existing guarded API](GVM_BHATTACHARYYA.md).
   A production `None` result remains unresolved; this prototype is not automatically
   invoked and is not importable from the published library.

## What the preprocessing does

Let physical Gaussian variances be `P` and `Q`, and let `s=sqrt(max(P,Q))`.
Work with `vp=P/s/s`, `vq=Q/s/s`, and `d=(mean_q-mean_p)/s`. This avoids multiplying
potentially extreme physical variances or inverting a generic matrix.
The Gaussian contribution is
`d*d/(4*(vp+vq)) + (log((vp+vq)/2) - (log(vp)+log(vq))/2)/2`.

The bridge coordinate is a standard-normal scalar `z`. Each original canonical
coordinate becomes `offset + transform*z`. Stable offsets are
`sqrt(vp)*d/(vp+vq)` for p and `-sqrt(vq)*d/(vp+vq)` for q; transforms are
`sqrt(2*vq/(vp+vq))` and `sqrt(2*vp/(vp+vq))`. In particular, the q offset is not
computed by subtracting two nearly equal means.

Substitute these into `alpha + beta*z + Gamma*z*z/2` and subtract the two phases.
The resulting coefficients are the input to positive scalar integration. The
underlying positive-panel method, tail selection and discrepancy-based stopping
remain as described in the [research assessment](GVM_BHATTACHARYYA_POSITIVE_RESEARCH.md).
JVM integration uses compensated summation, a stable tie-broken refinement queue,
positive weights, retained panel samples and per-call work buffers.

### Preprocessing uncertainty stays separate

The prototype tracks the magnitudes of the **original operands**, not just their
possibly tiny differences. A large common coupling can otherwise cancel to a small
phase coefficient while masking sensitivity in the transformation.
Its heuristic coefficient-error allowance grows with variance contrast and the
phase magnitudes across the entire truncated integration interval.

For `a=kappa_p/2`, `b=kappa_q/2`, the angular affinity obeys
`abs(d log(h)/d delta) <= min(a,b)`. To see the bound, its resultant radius satisfies
`abs(dr/d delta) <= min(a,b)`, and `0 <= I1(r)/I0(r) <= 1`.
This converts a phase-error allowance to a log-affinity allowance. The Gaussian
contribution and final logarithm/subtraction also receive rounding allowances.
The derivative inequality does **not** make the upstream heuristic coefficient
allowance a proof. No claim of rigorous interval arithmetic is made.

## Test-only interface and result units

`GvmPositiveScalarPrototype.compare(p, q, tolerance=1e-8, maxEvaluations=50000,
cancelled=() => false)` is package-restricted to the modernization tests. It is a
prototype contract, not a compatibility promise for a future public API.

| Input | Meaning |
| --- | --- |
| `p`, `q` | Non-null immutable GVM kernels, matching dimensions and coordinate conventions; this prototype accepts only one linear coordinate |
| `tolerance` | Positive finite absolute distance accuracy target, in nats |
| `maxEvaluations` | Integer 5..200,000; counts integrand evaluations, not every arithmetic operation or elapsed time |
| `cancelled` | Non-null test hook; a true result throws `CancellationException`; the real thread interrupt flag is checked independently and never cleared |

Malformed/null/mismatched inputs throw `IllegalArgumentException`. Both concentrations
must be at most 50. The study caps Gaussian distance and absolute transformed phase
coefficients at 10,000; out-of-range values or dimensions return `UnsupportedRange`.
Variance contrast above 1e8, nonfinite intermediates and inadequate estimated precision
return `NumericallyUnresolved`. These are eligibility checks, not resolution guarantees.

| Result field | Meaning/units |
| --- | --- |
| `status` | `Estimated`, `BudgetExhausted`, `NumericallyUnresolved`, `UnsupportedRange`; this is not the production enum |
| `distance`, `interval` | Optional distance and estimated interval in nats; a distance exists only for `Estimated`; an interval can have an infinite upper endpoint |
| `evaluations`, `radius` | Actual integrand count and standard-normal truncation radius; zero can indicate a preflight refusal |
| `gaussianTailBound` | Analytic omitted standard-Gaussian mass bound, evaluated in Double; bounds omitted affinity mass, not total distance error |
| `quadratureErrorEstimate`, `roundoffEstimate` | Heuristic integration discrepancy and arithmetic allowance in affinity units |
| `preprocessingErrorEstimate` | Separate heuristic preprocessing allowance in **distance units (nats)**; do not add it directly to affinity-unit fields |

Unavailable diagnostics may be infinite. Interruption aborts without publishing a
partial result. Buffers are not shared between calls; sharing immutable kernels does
not make Figaro universes or elements thread-safe.

## Three maintainer workflows

The following snippets belong in the modernization **test package**, with `p` and `q`
constructed as fixed kernels; they are not consumer examples for a published API.

```scala
// 1. Verify a cancellation-sensitive comparison end to end.
val result = GvmPositiveScalarPrototype.compare(p, q)
assert(result.status == GvmPositiveScalarPrototype.Status.Estimated)
println(result.distance)

// 2. Exercise an intentionally insufficient evaluation budget.
val limited = GvmPositiveScalarPrototype.compare(p, q, maxEvaluations = 5)
assert(limited.status == GvmPositiveScalarPrototype.Status.BudgetExhausted)
assert(limited.distance.isEmpty)

// 3. Check that precision limitations remain explicit.
val tight = GvmPositiveScalarPrototype.compare(p, q, tolerance = 1e-14)
assert(tight.status == GvmPositiveScalarPrototype.Status.NumericallyUnresolved)
assert(tight.distance.isEmpty)
```

For these three examples, the focused suite uses standard-normal marginals,
concentrations 50, and angular centers `0` versus `pi + 1e-5*z`.
The ordinary production Fourier call still returns `NumericallyUnresolved` for the
same inputs; the test explicitly verifies that the existing behavior has not changed.

## Gotchas and remaining promotion gates

- End-to-end oracle agreement is encouraging, but finite test grids do not certify
  the quadrature discrepancy or preprocessing allowances. `Estimated` is intentional.
- Tiny input differences may be significant. The prototype does not zero coupling,
  relax tolerance, or silently switch to another law to manufacture a result.
- Hard budgets bound evaluation counts, not wall time. Phase-aware prepartitioning
  can refuse an oscillatory case before any integrand evaluation; refinement also
  reserves its needed evaluations before starting.
- Uniform and identical laws still use integration here. A promoted implementation
  should assess exact reductions separately, without weakening error contracts.
- Extreme common unit scales of 1e-100 and 1e100 are checked on the curved fixture;
  arbitrary ill-conditioned transformations are not guaranteed. Covariance/mean
  overflow and large cancelled operands can cause a numerical refusal.
- This remains one-dimensional. It is not a solution for the 16/32-dimensional
  unresolved cases and makes no inference-speed, posterior-quality or coverage claim.

Next: decide and validate an explicit opt-in **public scalar API** with final naming,
statuses/diagnostic units, stable analytic reductions, additional near-limit conditioning
and phase controls, documentation examples, and measured end-to-end work. Require CI
before promotion; leave the existing Fourier entry point unchanged. Certified error
bounds and multidimensional alternatives remain separate work.

Related: [Python research](GVM_BHATTACHARYYA_POSITIVE_RESEARCH.md),
[high-precision fixture grid](GVM_BHATTACHARYYA_RELIABILITY.md),
[production comparison](GVM_BHATTACHARYYA.md), [roadmap](../ROADMAP.md).
No report ingestion, fusion, filtering, or propagation is implemented.
