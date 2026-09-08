# GVM Bhattacharyya: concentration and cancellation reliability

Status: bounded local validation on the development branch, 2026-09-08;
CI/integration pending. Production comparison code, numerical limits, dependencies
and compiled releases are unchanged. This follows the
[matched-accuracy timing assessment](GVM_BHATTACHARYYA_PERFORMANCE.md).
Local acceptance: all 300 modernization regressions across 24 Scala suites, four
new Python oracle checks, 18 documentation-parser tests, API freshness and local links pass.

## Overview: what this assessment establishes

The [guarded API](GVM_BHATTACHARYYA.md) should return an accurate distance **or explicitly
decline to resolve it**. This assessment checks that behavior beyond the moderate cases
used for timing. It does not try to improve a success percentage by relaxing error checks.

The fixed grid has 96 input pairs, each compared in both directions at three absolute
distance tolerances: 576 comparisons. Of these, **460 resolved and all met their requested
accuracy** against high-precision references; all corresponding estimated intervals
contained the references. The other **116 returned `NumericallyUnresolved` without a
distance or coefficient**. No false resolution was observed in this grid.

These are deterministic test counts, not probabilities of success on your application,
statistical coverage rates, or proof that the heuristic roundoff estimate always bounds
error. No support-range expansion is justified by these results.

## Quick start: three steps

1. Run the Scala grid using only the normal build dependencies:
   ```text
   sbt "figaro / Test / testOnly com.cra.figaro.test.modernization.GaussVonMisesBhattacharyyaReliabilityTest"
   ```
2. To verify the optional research oracles independently, use an isolated Python
   environment with `mpmath==1.3.0`, then run:
   ```text
   python -B -m unittest discover -s tools -p test_gvm_bhattacharyya_reliability.py -v
   ```
   This checks source-fixture freshness, higher-precision refinement and positive
   integration controls. It writes no fixtures or runtime configuration.
3. For application code, use the normal [comparison API](GVM_BHATTACHARYYA.md) and
   inspect `status`/`distance`. The tests do not enable a new mode or fallback.

## Fixed inputs and reference construction

The [oracle generator](../tools/gvm_bhattacharyya_reliability.py) defines the complete,
predeclared grid; it is not a random sample or a selection of passing inputs.
Both distributions have the same concentration within each pair:

| Family | Fixed input grid | Pairs |
| --- | --- | --- |
| Linear coupling, common Gaussian | One standard-normal coordinate; kappa 0.125, 1, 10, 25, 40, 50; angular offset 0, 0.5, 2, binary64 pi; beta difference 1e-5, 0.1, 1; zero Gamma | 72 |
| Curved, unequal Gaussian marginals | kappa 1, 10, 25, 50; first law `(mean, sd, alpha, beta, Gamma)=(0.5,1,0.25,0.5,0.25)`; second `(-0.5,0.5,-0.5,-0.25,Gamma)` with Gamma 0.01, 0.5, 2 | 12 |
| Dimension/curvature | Common standard Gaussian in 2, 6, 16, 32 dimensions; kappa 1, 10, 50; first beta=0.2 in every coordinate and Gamma=0.2 I; second beta=-0.2 and Gamma=0; both alpha=0 | 12 |

Here `sd` is standard deviation, not variance. Conditional location uses
`alpha + beta^T z + 0.5 z^T Gamma z`. Inputs are promoted from their binary64 values
to arbitrary precision, so the oracle describes the supplied Scala numbers, not an
ideal decimal model with slightly different inputs.

The [checked-in Scala fixtures](../Figaro/src/test/scala/com/cra/figaro/test/modernization/GvmBhattacharyyaReliabilityFixtures.scala)
store 45-digit distances generated at 80 decimal digits with 128 positive harmonics.
Every fixture is re-evaluated at 110 digits with 192 positive harmonics; absolute
distance differences must be below 1e-48. The analytic omitted-series bound relative
to affinity must be below 1e-50 before a fixture is accepted. These are research
arithmetic checks, not directed-rounding certificates.

Independent numerical-method controls integrate the **positive angular-reduced
integrand**, without Fourier coefficients or Gaussian characteristic evaluation:
five opposed linear cases cover concentrations 10, 25, 50 and weak through strong
coupling; a curved unequal-Gaussian case uses concentration 50. Integration extends
to 16 standard deviations with subdivisions; the omitted Gaussian mass divided by
the computed affinity must be below 1e-30, and distance agreement below 1e-25.
A two-dimensional curvature control uses positive tensor quadrature of Gaussian
order 64 and checks distance agreement below 1e-12.
These controls cover representative cases, not independent quadrature for all 96 pairs.
High-precision refinement alone is not independent verification of the mathematical formula.

## Observed results

Each row below contains 192 comparisons: 96 pairs in both directions, with the default
256-harmonic budget. The worst error ratio is `abs(returned - reference) / tolerance`,
computed only for resolved results.

| Requested absolute tolerance (nats) | Resolved | Numerically unresolved | Worst resolved error / tolerance |
| --- | --- | --- | --- |
| 1e-6 | 166 | 26 | 0.892 |
| 1e-8 (API default) | 156 | 36 | 0.426 |
| 1e-10 | 138 | 54 | 0.797 |

At the default tolerance, the linear family resolved 120 of 144 directed comparisons,
the unequal-Gaussian family 24 of 24, and the dimension family 12 of 24.
Every resolved interval included its oracle; checks allow eight ulps of the stored
reference for binary64 conversion/comparison, not as an extra user tolerance.
The observed error ratios above are unadjusted and all below one.

The suite also tests that tightening tolerance is no more permissive, a zero harmonic
budget remains visibly exhausted for these nonanalytic fixtures, and opposed cases
with tiny varying coupling remain unresolved even with a budget of 512. Known usable
low-concentration and unequal-Gaussian cases must resolve: returning `None` for every
input cannot pass the suite. Resolution counts elsewhere are observations, not a
permanent requirement to preserve a particular heuristic decision at a boundary.

## Three common user decisions

### 1. A returned distance is suitable for the requested numerical check

```scala
import com.cra.figaro.library.atomic.continuous.*

val comparison = GaussVonMisesBhattacharyya.compare(p, q, absoluteTolerance = 1e-8)
comparison.distance.foreach(d => println(s"Bhattacharyya distance: $d nats"))
```

`p` and `q` are fixed GVM kernels in matching coordinates; their construction is shown
in the [API guide](GVM_BHATTACHARYYA.md). `Resolved` means the numerical estimates
accepted the requested tolerance, not that the result has a rigorous error certificate.
Absolute error also does not imply small relative error for a nearly zero distance.

### 2. An unresolved result is not a zero distance or an infinite distance

```scala
val comparison = GaussVonMisesBhattacharyya.compare(p, q)
comparison.status match {
  case GaussVonMisesBhattacharyyaStatus.Resolved => println(comparison.distance.get)
  case other => println(s"No resolved distance: $other; ${comparison.message}")
}
```

Keep `None` visible to callers. Do not use `getOrElse(0)`, rank it as dissimilarity
zero, or interpret an infinite upper interval endpoint as the actual distance.
Near opposition at concentration 50, true affinity can be around 3.4e-21; summing
moderate-sized signed Fourier terms to recover it is cancellation-sensitive.
The harmonic tail may become tiny while rounding still dominates.

### 3. Decide whether retrying changes the real numerical limitation

```scala
val first = GaussVonMisesBhattacharyya.compare(p, q, maxHarmonics = 8)
val withMoreWork =
  if (first.status == GaussVonMisesBhattacharyyaStatus.BudgetExhausted)
    GaussVonMisesBhattacharyya.compare(p, q, maxHarmonics = 256)
  else first
```

This retry can address a small **work budget**; it does not guarantee resolution.
For `NumericallyUnresolved`, more harmonics need not help. A looser tolerance is
appropriate only if the application's accuracy requirement genuinely permits it.
Check units/conditioning, retain an unresolved outcome, or use a separately validated
method. This release supplies no automatic positive-quadrature or arbitrary-precision fallback.

## Gotchas and remaining gaps

- The concentration and dimension caps are **eligibility limits**, not guarantees of
  resolution. The 32-dimensional, concentration-1 curved fixture is unresolved at the
  default 1e-8 tolerance although it resolves at 1e-6. Conservative phase/matrix rounding
  estimates grow with dimension even for well-conditioned Gaussian marginals.
- Exactly constant angular separation admits a stable analytic reduction. Tiny
  varying coupling is a different law and uses the series. At concentration 50,
  removing beta=1e-5 from an opposed fixture changes the reference distance by about
  1.56e-8 nats—already larger than the default tolerance. Never silently zero coupling
  to obtain a convenient analytic result.
- These pairs use equal concentrations within each pair; unequal-concentration ratios,
  broad correlated/ill-conditioned covariance grids, adversarial phase transformations,
  dimensions beyond the cap and concentration above 50 are not covered by this grid.
  Earlier regression controls remain in place, but this is not exhaustive validation.
- No observed false resolution is encouraging, but does not turn heuristic roundoff
  allowances into proofs. Arbitrary-precision oracle arithmetic is research-only and
  adds no dependency or hidden expense to a Scala comparison call.
- Resolution counts depend on this deliberate stress grid and tolerance; they are
  not an application reliability score or a reason to discard difficult cases.

The next substantive numerical candidate is an **explicit, bounded positive-integration
alternative for cancellation-dominated low-dimensional comparisons**, with independent
accuracy checks and budget/failure contracts. It requires a separate assessment before
exposure as an API; it must not quietly replace `None` with an unchecked quadrature
estimate. Tighter multidimensional roundoff analysis and unequal-concentration stress
testing remain useful alongside it. Mutual information remains separate research.

## API/reference and related work

No public library function was added or changed. The complete params, return values,
examples and status contracts remain in the [guarded API reference](GVM_BHATTACHARYYA.md).
The private Scala fixture/test objects are not consumer APIs.
`python -B tools/gvm_bhattacharyya_reliability.py` takes no arguments and prints generated
Scala source to stdout; it does not modify files. The four Python tests run through
`unittest` as shown above and exit unsuccessfully on disagreement or stale fixtures.

Related: [Scala reliability suite](../Figaro/src/test/scala/com/cra/figaro/test/modernization/GaussVonMisesBhattacharyyaReliabilityTest.scala),
[Python oracle controls](../tools/test_gvm_bhattacharyya_reliability.py),
[method derivation](GVM_BHATTACHARYYA_RESEARCH.md), [performance assessment](GVM_BHATTACHARYYA_PERFORMANCE.md),
[tensor reference](GVM_TENSOR_QUADRATURE.md), [roadmap](../ROADMAP.md).
The work remains fixed-law mathematics: no report ingestion, fusion, filtering or propagation.
