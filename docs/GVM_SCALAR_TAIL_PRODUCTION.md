# Bounded tail selection in the scalar GVM API

Status: implemented in the development branch; CI/main integration pending.
This is a change to the existing modernization snapshot, not a new tagged release or
replacement of an immutable library bundle.
The preceding test-only prototype is integrated on main at `44b6b73f` after
[passing CI](https://github.com/mattwilkinsphoto/figaro/actions/runs/34207249603).

## Overview: what changes for a user

`GaussVonMisesScalarBhattacharyya.compare` now avoids some unnecessarily wide integration
domains. It uses a stronger angular-affinity lower bound when a bounded setup pass looks
worthwhile. The method still integrates positive panels and includes the omitted Gaussian
tail in its final estimated distance interval.

There is **no new switch** and no signature change. After rebuilding a snapshot containing
this change, existing callers of the scalar API receive the new policy automatically.
Calling the separate Fourier API does not invoke it. This improves the implementation of
the explicitly selected scalar method; it is not an automatic inference-method fallback.

The costly curved fixture improves about **2.53x** against the previous audited scalar
implementation, including setup. Ordinary and tiny-overlap fixtures change little. The
[Fourier-first recommendation](GVM_SCALAR_PERFORMANCE.md) for ordinary coupled comparisons
still applies, and `Estimated` still means estimated accuracy, not a certified bound.

## Quick start in three steps

1. Build the development snapshot containing the change with `sbt "figaro / publishLocal"`.
   Until CI promotion, use `modernize/gauss-von-mises`, not an older main checkout or RC1 bundle.
2. Construct the curved scalar pair (one linear coordinate and one angle):

   ```scala
   import com.cra.figaro.library.atomic.continuous.*
   val p = GaussVonMisesDistribution(Vector(.5), Vector(Vector(1.0)), .25,
     Vector(.5), Vector(Vector(.25)), 50)
   val q = GaussVonMisesDistribution(Vector(-.5), Vector(Vector(.25)), -.5,
     Vector(-.25), Vector(Vector(2.0)), 50)
   ```

3. Use the same comparison call and inspect its diagnostics:

   ```scala
   val r = GaussVonMisesScalarBhattacharyya.compare(p, q)
   println((r.status, r.distance, r.interval, r.radius, r.evaluations))
   // Estimated, distance about 1.31628058914; radius 7; 9,102 integrand evaluations.
   ```

The independent [published-library consumer](../tools/acceptance-consumer/README.md)
checks this case from the built JAR, not from project source dependencies.

## API and budget contract

The [full scalar API reference](GVM_SCALAR_BHATTACHARYYA.md#api-reference) documents every
parameter and result field. The computation remains:

```scala
GaussVonMisesScalarBhattacharyya.compare(
  p: GaussVonMisesDistribution, q: GaussVonMisesDistribution,
  tolerance: Double = 1e-8, maxEvaluations: Int = 50000,
  cancelled: () => Boolean = () => false
): GaussVonMisesScalarBhattacharyya.Result
```

`p` and `q` must be valid fixed kernels in the same coordinates with one linear dimension
and concentrations at most 50. `tolerance` is a positive finite absolute distance target
in nats. `maxEvaluations` remains an integer in `[5,200000]` counting **integrand calls**.
The non-null cancellation predicate aborts cooperatively; caller exceptions propagate,
and interruption is preserved. `distance` is present only for `Estimated` results.

The optional prepass has separate fixed work: 64 cells, two 128-term denominator series
and 64 32-term numerator partial sums, at most **2,304 Bessel terms**. It is excluded from
`evaluations` and `maxEvaluations`, just as transformation and queue work are excluded.
It can run even if a tiny integrand budget subsequently causes refusal. There are checks
at each cell and each term, in addition to existing comparison cancellation checks.
Analytic shortcuts bypass this setup entirely. Neither budget is an elapsed-time limit.

The helper is internal and has no supported application entry point. There is no user
parameter for cell count, radius or screening threshold; do not depend on those choices
remaining fixed in future snapshots.

## Three common patterns

### 1. Upgrade an existing scalar workload without changing its call sites

```scala
val result = GaussVonMisesScalarBhattacharyya.compare(p, q, tolerance = 1e-8)
result.distance match {
  case Some(d) => println(s"distance=$d, interval=${result.interval}")
  case None => println(s"comparison unavailable: ${result.status}")
}
```

Before this change, the quick-start pair used radius 12 and 25,098 integrand calls.
It now uses radius 7 and 9,102 calls at the same requested tolerance. Returned distance
digits, radii, work counts and interval widths can change; do not require old bitwise
diagnostics when testing a library upgrade.

### 2. Retain wide tails when the overlap is genuinely tiny

```scala
val opposedP = GaussVonMisesDistribution(Vector(0.0), Vector(Vector(1.0)), 0,
  Vector(0.0), Vector(Vector(0.0)), 50)
val opposedQ = GaussVonMisesDistribution(Vector(0.0), Vector(Vector(1.0)), math.Pi,
  Vector(1e-5), Vector(Vector(0.0)), 50)
val tiny = GaussVonMisesScalarBhattacharyya.compare(opposedP, opposedQ)
println((tiny.distance, tiny.radius)) // about Some(47.1275754862468), radius 12
```

This case keeps its original diagnostics and skips the prepass. A universally smaller
radius would be inappropriate: tiny overlaps need tighter absolute tail control.

### 3. Bound integration work and handle refusal explicitly

```scala
val limited = GaussVonMisesScalarBhattacharyya.compare(p, q, maxEvaluations = 5)
assert(limited.distance.isEmpty)
val retry = GaussVonMisesScalarBhattacharyya.compare(p, q, maxEvaluations = 50000)
println((retry.status, retry.evaluations))
```

The first call may have done bounded setup despite reporting zero integrand calls.
More integration budget can help a budget refusal; it cannot cure a precision floor.
Use a cheap cancellation predicate when a caller also needs cooperative time control.

## Why the shorter domain remains guarded

The prepass lower-bounds angular affinity for the computed Double phase using positive
cell contributions and outward-expanded arithmetic. Its denominator Bessel series has
an explicit positive remainder bound; numerator partial sums omit only positive terms.
The old global bound remains available, and taking their maximum cannot deliberately
weaken radius selection. Nonfinite prepass arithmetic contributes zero rather than
manufacturing a stronger bound.

Setup is screened out unless the old radius is at least 8 and the transformed phase's
variation proxy exceeds 4. This is an engineering cost heuristic, not a profitability
guarantee. The final Gaussian tail term, preprocessing allowance, positive panels,
periodic full-sum audits and final stopping checks remain in place. Shorter radius
means a **larger** omitted-tail contribution; the final interval still includes it.
The [prototype derivation](GVM_SCALAR_TAIL_JVM.md#how-the-jvm-bound-differs-from-the-research-bound)
explains the arithmetic and why this does not certify the full distance computation.

## Matched production measurements

The [retained production evidence](GVM_SCALAR_TAIL_PRODUCTION_RUNS.txt) contains 210
timing rounds from three fresh JVMs, using the [same five-case protocol](GVM_SCALAR_TAIL_JVM.md#results-and-measurement-protocol)
as the prototype: Java 17.0.4, 6 GiB maximum heap, interleaved warmup, calibrated batches
and seven alternating rounds. Timed calls include setup; results and reference accuracy
are checked before and after timing. Each table entry is a median of JVM medians.

| Fixture | Previous audited, microseconds | Integrated policy, microseconds | Previous / integrated |
| --- | ---: | ---: | ---: |
| Ordinary linear | 76.977 | 78.392 | 0.982x |
| Nearly opposed, weak coupling | 72.006 | 73.146 | 0.984x |
| Concentrated linear | 199.123 | 192.945 | 1.032x |
| Curved unequal parameters | 86.418 | 87.676 | 0.986x |
| Strongly curved, concentration 50 | 2,906.675 | 1,146.591 | 2.535x |

The expensive case's JVM medians range from 2.861–2.944 ms before and 1.129–1.186 ms
after. The few-percent differences elsewhere are small and include slight regressions;
they are not broad performance gains. Timing is advisory, not a CI pass/fail threshold
or an application-wide guarantee. Historical prototype and production runs retain
identical numerical/work check records. The log label `candidate` now denotes the
integrated policy; `audited` denotes the frozen pre-change control.

Reproduce with the benchmark commands in the prototype guide; summarize this report
with `python -B tools/summarize_gvm_scalar_tail.py docs/GVM_SCALAR_TAIL_PRODUCTION_RUNS.txt`.
`parse(text)`, `summarize(runs)` and the CLI have the same documented helper contracts.
The optional `tools/gvm_scalar_tail_holdout.py` adds `rows()` (44 input/tolerance/oracle
tuples) and `scala_source()` (generated fixture string); its CLI prints source, writes
no files and requires `mpmath==1.3.0` only for research regeneration.

## Validation and remaining limits

All **319 distinct modernization tests in 28 suites** pass locally. The count removes
nine duplicate prototype contract tests and adds three held-out/boundary tests; the
public scalar contract tests now exercise the integrated implementation directly.
Additional controls cover 44 held-out physical-input/threshold fixtures in both directions,
including one-ulp neighbors, and low budgets on every fixture. The independent cell-bound
grid still covers 108 phase fixtures in both directions. The original public scalar grid
has maximum observed error `3.44e-10` nats and at most 9,102 evaluations at `1e-8` tolerance.

The duplicate candidate integrator and test suite were retired; Git history retains
them. A hash-pinned audited baseline remains in test sources for historical differential
and performance controls. Source-provenance checks ensure the production integration
changes only the radius policy, not the existing final integration/error logic.

Local validation also passes **67 GVM research/evidence tests**, 18 documentation-tool
tests, seven artifact-validator tests, executable examples and regenerated public-reference
checks. The thin, assembled, source and API-documentation artifacts pass legal/content
validation; runtime JARs exclude test/example/coverage classes and target Java 17.
The separate consumer verifies the rebuilt thin JAR's SHA-256
`0cd853d0cf64ca0cd352412ada720758d464fec4c24593b0138feb766afc4271`
and passes scalar curvature, tiny-overlap and budget checks alongside existing inference
acceptance tests. Scaladoc reports the same four pre-existing warnings; no new warning
was introduced. Local publication is not a remote release or a replacement RC bundle.

Precision estimates, supported scalar range and concentration caps have not become
certified or broader. No multidimensional extension, automatic Fourier fallback,
new threading behavior, report ingestion, fusion or domain-specific processing is added.
After CI promotion, this completes the targeted scalar tail optimization; stronger
numerical certification and broader GVM capabilities remain separate roadmap decisions.

Related: [scalar API](GVM_SCALAR_BHATTACHARYYA.md), [migration](MIGRATION.md),
[roadmap](../ROADMAP.md), [consumer check](../tools/acceptance-consumer/README.md).
