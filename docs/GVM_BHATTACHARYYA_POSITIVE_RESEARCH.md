# Positive scalar GVM overlap: numerical-method assessment

Status: research-only Python prototype with [passing CI at `70b083f4`](https://github.com/mattwilkinsphoto/figaro/actions/runs/34194233164),
integrated on main through `a54d665e`.
This research component introduced no library API or compiled release.
The follow-on [Scala prototype](GVM_POSITIVE_SCALAR_PROTOTYPE.md) validated physical-kernel
preprocessing end to end and has now moved into an [explicit public scalar API](GVM_SCALAR_BHATTACHARYYA.md),
with separate CI/integration status. The Fourier API still has no automatic fallback.

## Outcome and purpose

Positive one-dimensional integration is a promising alternative when the signed
Fourier series loses a very small overlap to cancellation. At an absolute distance
target of **1e-8 nats**, the prototype returned accepted estimates for all **84 scalar
pairs** in the [reliability grid](GVM_BHATTACHARYYA_RELIABILITY.md). The largest observed
error was **3.51e-10 nats**, with **688–25,070 integrand evaluations** per comparison
under a 50,000-evaluation cap. All estimated intervals contained the reference values.
That scalar grid includes the 12 pairs the production Fourier method declined to
resolve at its default tolerance. Ten additional unequal-concentration pairs, also
checked with phase sign and concentration order reversed, passed.

This is a **component-level result, not production readiness**. Arbitrary-precision
research preprocessing supplies scalar Gaussian-overlap and transformed phase inputs;
only the integrator uses standard-library binary64 arithmetic. The assessment does
not establish end-to-end Double preprocessing accuracy, application throughput,
multidimensional support or a rigorous error bound. It is not a speed comparison
against the production method.

## Quick start

In an isolated Python environment with the research dependency `mpmath==1.3.0`:

1. Run the seven acceptance tests:
   ```text
   python -B -m unittest discover -s tools -p test_gvm_bhattacharyya_positive.py -v
   ```
2. Print the complete 84-pair assessment and summary:
   ```text
   python -B tools/gvm_bhattacharyya_positive.py
   ```
3. Continue to use the [guarded Scala API](GVM_BHATTACHARYYA.md) in applications.
   Do not route production `None` results through this research script automatically.

The script writes to stdout only. Its arithmetic function can be imported without
mpmath; mpmath is needed by the oracle-driven command and acceptance tests.

## Method and error accounting

After the Gaussian overlap reduction, the distance is
`DG - log(E[h(delta(Z))])`, with scalar `Z ~ Normal(0,1)`,
`delta(z) = c + l*z + q*z*z/2`, `a=kappa_p/2`, `b=kappa_q/2`, and
`h(delta) = I0(hypot(a-b, 2*sqrt(a*b)*cos(delta/2))) / sqrt(I0(kappa_p)*I0(kappa_q))`.
The integrated values and all quadrature weights are nonnegative. Bessel I0 is
evaluated with its positive defining series; the density product is evaluated in log
space before exponentiation. This avoids cancellation between Fourier harmonics.

There are three separate numerical controls:

- **Omitted Gaussian mass:** mathematically `I0(abs(a-b))/den <= h <= 1`.
  Choose a radius from 4 through 16 until the two-sided standard-Gaussian tail is
  at most `tolerance * minimumAffinity / 16`. The reported tail bound is analytic,
  evaluated in binary64—not an interval-arithmetic certificate. It bounds omitted
  angular-affinity mass before multiplying by the Gaussian overlap factor.
- **Quadrature discrepancy:** prepartition until panels have width at most one and
  their quadratic phase span is at most `min(0.5,1/sqrt(max(1,kappa_p,kappa_q)))`.
  Include an interior quadratic extremum when computing that span. Then compare a
  positive coarse Simpson rule with its positive two-panel refinement and refine the
  panel with largest absolute discrepancy. Retain the refined positive estimate,
  without signed Richardson extrapolation. The summed discrepancy is an **estimate**,
  not a proven derivative-based quadrature error bound. Prepartitioning mitigates
  missed oscillations; it is not a general anti-aliasing theorem.
- **Rounding allowance:** add an explicitly heuristic binary64 allowance based on
  evaluation count, phase coefficients and accumulated positive mass. A separate
  allowance covers final logarithm/subtraction arithmetic. This does **not** account
  for errors incurred when obtaining the supplied phase coefficients from physical
  GVM parameters; that preprocessing is outside this component assessment.

Transform the affinity interval to a distance interval. Publish a distance only if
its estimated distance error is within the requested tolerance. Exhaustion and
estimated rounding limitations return no distance. The code preflights initial
panel evaluation counts and reserves four new evaluations before each refinement.
It does not add a work budget to an already exhausted budget or change the inputs.

The mathematical foundations are the [DLMF I0 integral representation](https://dlmf.nist.gov/10.32.E1),
[modified-Bessel defining series](https://dlmf.nist.gov/10.25.E2), and
[Simpson quadrature and its derivative-based remainder](https://dlmf.nist.gov/3.5.ii).
The phase prepartition and discrepancy-based stopping policy are independently
implemented research choices; DLMF's remainder formula does not certify this heuristic.
No third-party implementation code was copied.

## Research interface

`compare_phase(gaussian_distance, constant, linear, quadratic, kappa_p, kappa_q,
tolerance=1e-8, max_evaluations=50000, cancelled=None)` returns an immutable `Result`.
These are **already transformed scalar coefficients**, not original GVM mean,
covariance, beta or Gamma. They cannot be substituted for one another.

| Input | Contract |
| --- | --- |
| `gaussian_distance` | Finite, nonnegative Gaussian Bhattacharyya contribution; study cap 10,000 |
| `constant`, `linear`, `quadratic` | Finite coefficients of `c+l*z+q*z*z/2` in the standard-normal overlap coordinate; absolute study cap 10,000 each |
| `kappa_p`, `kappa_q` | Finite concentrations, each in 0..50 |
| `tolerance` | Positive finite absolute distance tolerance, in nats |
| `max_evaluations` | Integer 5..200,000, default 50,000; Boolean values rejected |
| `cancelled` | Optional no-argument predicate; True raises `InterruptedError` at entry or during work; predicate exceptions propagate |

Malformed inputs raise `ValueError`. Finite parameters outside the study caps return
`UnsupportedRange`. The result fields are:

| Field | Meaning |
| --- | --- |
| `status` | `Estimated`, `BudgetExhausted`, `NumericallyUnresolved`, or `UnsupportedRange`; these are research strings, not the Scala enum |
| `distance` | Accepted distance only for `Estimated`; otherwise `None` |
| `estimated_interval` | Optional numerical-estimate interval; may have an infinite upper endpoint; not certified |
| `evaluations`, `radius` | Actual integrand evaluation count and chosen standard-normal cutoff |
| `gaussian_tail_bound` | Bound on omitted affinity mass from the cutoff; not total error |
| `quadrature_error_estimate` | Summed coarse/fine discrepancies in affinity units |
| `roundoff_estimate` | Heuristic affinity-rounding allowance; unavailable diagnostics may be infinite |

`log_i0(x)` is the study's internal numerical helper (finite x in 0..50, returning
log I0 or raising on invalid inputs/internal exhaustion), not a proposed library API.
`main()` prints fixed-grid records and a completion summary, raises on observed
oracle error above target, and is exercised by the command above. No consumer-facing
distribution or sampler class is added.

## Three research workflows

From a Python session with `tools` on the import path:

```python
from math import pi
from gvm_bhattacharyya_positive import compare_phase

# 1. Nearly opposed scalar phase: cancellation-sensitive in the Fourier series.
r = compare_phase(0, pi, 1e-5, 0, 50, 50)
assert r.status == "Estimated"
print(r.distance)  # About 47.127575486 nats; not a certified interval.

# 2. Preserve work exhaustion as an explicit outcome.
r = compare_phase(0, pi, 1e-5, 0, 50, 50, max_evaluations=5)
assert r.status == "BudgetExhausted" and r.distance is None

# 3. Do not pretend that arbitrarily tight precision is achievable.
r = compare_phase(0, pi, 1e-5, 0, 50, 50, tolerance=1e-14)
assert r.status == "NumericallyUnresolved" and r.distance is None
```

For the nearly opposed example, the selected radius is 12 standard deviations.
A fixed 8-sigma cutoff has a worst-case omitted-mass bound over 100,000 times the
entire affinity, so it cannot justify the requested relative overlap accuracy.
This is a statement about that bound, not a claim that the actual omitted integral
is necessarily so large.

## Gotchas, remaining work and recommendation

- The accepted status is deliberately `Estimated`, not a claim of certified accuracy.
  Passing finite oracle grids cannot prove the discrepancy heuristic for arbitrary inputs.
- Positive weights remove signed-sum cancellation, not all numerical errors. Sharp
  or oscillatory phases still need work or an explicit budget refusal. Very tight
  tolerances can be blocked by rounding estimates.
- The current implementation is scalar only. It does not address the 16/32-dimensional
  refusals in the reliability study or justify exponential tensor work at those dimensions.
- The evaluation cap is not a wall-clock guarantee. Initial partitioning, Bessel
  evaluation and repeated compensated summation add overhead; profiling and production
  allocation decisions have not been made. There is no end-to-end speedup claim.
- The main command uses high-precision preprocessing and reference generation. It is
  not a demonstration that a physical-kernel-to-distance binary64 API already works.
- No source licenses, patents or application-specific permissions are inferred from
  these mathematical tests. The scope is fixed-law numerical comparison only.

The [explicit scalar Scala prototype](GVM_POSITIVE_SCALAR_PROTOTYPE.md) now exercises
Gaussian/phase preprocessing, hard budgets, interruption checks and honest estimated
error semantics in test sources. Keep the existing Fourier method and its unresolved results unchanged.
Require end-to-end independent oracles, adversarial aliasing/conditioning controls,
documentation and CI before making any new public API available. Certified quadrature
and broader dimensions remain separate gates.

Related: [prototype](../tools/gvm_bhattacharyya_positive.py),
[seven acceptance tests](../tools/test_gvm_bhattacharyya_positive.py),
[reliability grid](GVM_BHATTACHARYYA_RELIABILITY.md),
[guarded production API](GVM_BHATTACHARYYA.md), [roadmap](../ROADMAP.md).
