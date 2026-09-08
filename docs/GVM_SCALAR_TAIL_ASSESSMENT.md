# Scalar GVM tail-radius assessment

Status: research-only high-precision component study. No library behavior changes.
Integrated on main at `b726fe4f` after
[passing CI](https://github.com/mattwilkinsphoto/figaro/actions/runs/34205631150).
The preceding [audited-totals optimization](GVM_SCALAR_AUDITED_TOTALS.md) is integrated
on main at CI-verified `213aa587`. This assessment identifies a possible next improvement;
it does not establish a new production radius policy or a measured speedup.
Local validation passes all 58 GVM research/evidence tests, 18 documentation-tool
tests, public-reference freshness and local links. The existing CI scalar-research
test discovery includes these seven new tests automatically.
Follow-on: the [test-only JVM prototype](GVM_SCALAR_TAIL_JVM.md) now measures complete
comparisons, including conservative setup arithmetic: about 2.54x on the curved fixture,
with little change on four other fixtures. The [production integration](GVM_SCALAR_TAIL_PRODUCTION.md)
now adds held-out controls, consumer verification and a repeated timing gate; CI/main
promotion is pending.

## Overview

The positive scalar Bhattacharyya integrator covers a finite interval of a standard
Gaussian variable and bounds the omitted tails. Its initial phase-aware partition can
be expensive when the angular phase curves sharply. After the bookkeeping optimization,
the curved fixture still uses 25,098 integrand evaluations: 23,250 are the five initial
evaluations in each of 4,650 panels. Reducing unnecessary tail coverage could therefore
remove substantial work before adaptive refinement begins.

The existing radius uses a global minimum of angular affinity. That minimum is safe
in exact arithmetic but can be much smaller than the actual average. This study builds
a stronger lower bound from positive contributions on 64 cells covering `[-4,4]`.
Users should continue using the existing [method-selection guidance](GVM_SCALAR_PERFORMANCE.md).
There is no setting to enable this candidate in Figaro yet.

## Quick start: reproduce in three steps

1. In a disposable Python environment, install the optional research dependency:
   `python -m pip install mpmath==1.3.0`.
2. From the repository root, run `python -B tools/gvm_scalar_tail_assessment.py`.
3. Check the evidence and mathematical controls with
   `python -B -m unittest discover -s tools -p 'test_gvm_scalar_tail_assessment.py' -v`.

The report goes to standard output; the tool does not write files. The checked-in
[84-case report](GVM_SCALAR_TAIL_ASSESSMENT_RUNS.txt) is regenerated exactly by a test.
This dependency belongs to research tooling, not the Figaro library.

## How the candidate works

Write the standardized angular affinity as

```text
h(z) = I0(sqrt((a-b)^2 + 4ab cos(delta(z)/2)^2)) / denominator
F    = E[h(Z)], Z ~ Normal(0,1)
a = kappaP/2, b = kappaQ/2
denominator = sqrt(I0(kappaP) I0(kappaQ))
delta(z) = constant + linear*z + quadratic*z*z/2
distance = Gaussian distance - log(F)
```

On each cell, the quadratic's endpoints and any interior vertex give its phase range.
If that range includes an odd multiple of pi, the minimum squared cosine is zero;
otherwise its minimum occurs at a range endpoint. Since `I0` is increasing for
nonnegative arguments, this produces a minimum `h` over the entire cell. Multiplying
by the Gaussian cell probability and summing gives a lower bound `Lcell <= F`.
Contributions outside `[-4,4]` are omitted only from this **lower bound**; the actual
comparison integral is not truncated there.

Use `L = max(Lcell, I0(abs(a-b))/denominator)` so the candidate cannot weaken the old
bound in exact arithmetic. Select the first integer radius `R` from 4 through 16 with
`erfc(R/sqrt(2)) <= tolerance * L / 16`, retaining the existing tail allocation.
Because `0 <= h <= 1`, the omitted angular integral is at most this Gaussian tail.
The production distance interval must still include that tail contribution; this
selection rule alone is not the complete distance-error calculation.

The study calculates the lower bound at 80 decimal digits. A separate high-precision
Fourier reference validates it but **does not choose the radius**. Finite-precision
mpmath evaluations are not directed-rounding certification.

## Results: initial work, not elapsed time

All rows use the existing scalar reliability grid and a `1e-8`-nat target.

| Case | Existing radius → candidate | Existing initial panels → candidate |
| --- | --- | --- |
| Strong curvature, concentration 50 (`unequal-83`) | 12 → 7 | 4,650 → 1,494 |
| Strong curvature, concentration 25 (`unequal-80`) | 10 → 7 | 2,293 → 1,127 |
| Moderate curvature, concentration 50 (`unequal-82`) | 12 → 7 | 1,068 → 331 |
| Nearly opposed, weak coupling, concentration 50 (`linear-69`) | 12 → 12 | 32 → 32 |
| Complete 84-case grid | 49 cases have smaller radius | 14,736 → 7,737 total |

The strongest curved case would start with 7,470 rather than 23,250 integrand calls.
Its candidate Gaussian tail divided by reference affinity is about `6.99e-12`.
The difficult tiny-overlap case correctly retains the wide radius. Some smaller
radii retain the same panel count because the partition uses repeated bisection.
Aggregate panel counts do not represent a workload-weighted benchmark.

These counts exclude the 64-cell prepass, adaptive refinements and JVM overhead.
The initial partition uses Double arithmetic with high-precision preprocessing
converted to Double, rather than invoking the full Scala preprocessing pipeline.
Consequently neither the panel reduction nor its reciprocal is a measured speedup.

## Research helper reference

All helpers live in [gvm_scalar_tail_assessment.py](../tools/gvm_scalar_tail_assessment.py).
They are maintainer tools, not public Scala APIs. `c` is a one-dimensional research
`Comparison`; valid scalar inputs and ordered finite intervals are assumed unless
explicitly checked. Except `assess`, callers select their mpmath precision context.

| Function | Parameters and return | Example |
| --- | --- | --- |
| `phase_range(c, left, right)` | Closed interval endpoints; returns `(minimum, maximum)` quadratic phase in current precision | `phase_range(c, -4, 4)` |
| `cell_lower_bound(c, cells=64)` | Integer cell count 1..1,024; returns positive affinity lower bound; rejects invalid counts | `cell_lower_bound(c, 128)` |
| `radius_for(lower, tolerance=None)` | Finite affinity bound in `(0,1]`, positive finite tolerance (default `1e-8`); returns integer 4..16; raises on invalid input or exhausted cap | `radius_for(lower)` |
| `initial_panels(c, radius)` | Positive integration radius; returns initial panel count; raises on unresolved midpoint or research panel cap 40,000 | `initial_panels(c, 7)` |
| `assess(spec)` | Scalar tuple from `specifications()`; returns old/candidate radii, panel counts and candidate tail/reference-affinity ratio at 80 digits; raises if bound exceeds reference allowance | `assess(('unequal', 1, 50., 0., 0., 2.))` |
| `report()` | No parameters; returns all 84 records plus summary as a string | `text = report()` |
| `main()` | No parameters; prints report, returns `None` | `main()` |

## Three common maintainer patterns

Run these snippets from `tools` after installing the optional dependency.

1. Compare current and candidate initial work for the expensive fixture:

   ```python
   from gvm_scalar_tail_assessment import assess
   print(assess(('unequal', 1, 50., 0., 0., 2.)))
   # Existing: radius 12, 4650 panels. Candidate: radius 7, 1494 panels.
   ```

2. Check a case where a smaller radius would be inappropriate:

   ```python
   import math
   print(assess(('linear', 1, 50., math.pi, 1e-5, 0.)))
   # Both retain radius 12: tiny overlap still needs tight absolute tail control.
   ```

3. Study prepass cost versus lower-bound quality before choosing a JVM design:

   ```python
   import mpmath as mp
   from gvm_bhattacharyya_reliability import comparison
   from gvm_scalar_tail_assessment import cell_lower_bound, radius_for
   with mp.workdps(80):
       c = comparison(('unequal', 1, 50., 0., 0., 2.))
       old = mp.besseli(0, abs(c.a-c.b))/c.denominator
       for cells in (8, 16, 32, 64):
           lower = max(old, cell_lower_bound(c, cells))
           print(cells, radius_for(old), radius_for(lower))
   # More setup can improve the bound; it need not reduce the integer radius.
   ```

## Gotchas and production gates

- A mathematical lower bound can be rounded upward in Double arithmetic. A JVM
  candidate must conservatively handle phase-range uncertainty, cell probabilities
  and Bessel evaluation before trusting the resulting radius. Phase wraps, interior
  vertices and radius-selection boundaries need explicit adversarial tests.
- The seven research tests cover the full grid, evidence freshness, tiny overlap,
  curvature, nested-cell monotonicity, invalid configuration and ten additional
  unequal-concentration cases with operand reversal. They do not certify all inputs.
- A prepass can make cheap comparisons slower. Prototype it in test-only JVM code,
  account for its setup cost, preserve analytic shortcuts and measure complete calls
  against the audited production implementation in fresh JVMs.
- Preserve cancellation, bounded prepass work, documented evaluation-budget semantics,
  the final Gaussian tail contribution and all existing error/refusal checks. No
  smaller tolerance, erased coupling or oracle-selected production policy is proposed.
- Scalar concentration limits remain unchanged. Multidimensional comparison, stronger
  numerical certification and mutual information are separate roadmap items. No
  automatic Fourier fallback, data fusion or domain-specific processing is added.

Related: [scalar API](GVM_SCALAR_BHATTACHARYYA.md),
[reliability grid](GVM_BHATTACHARYYA_RELIABILITY.md),
[audited totals](GVM_SCALAR_AUDITED_TOTALS.md), [roadmap](../ROADMAP.md).
