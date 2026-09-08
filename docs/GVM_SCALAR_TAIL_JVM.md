# Scalar GVM tail bounds: JVM prototype and timings

Status: test-only implementation, locally validated; CI/integration pending.
No production sources, public signatures, inference defaults or release coordinates
change. Users cannot enable this candidate in the compiled Figaro library yet.

## Overview: what this adds

The [high-precision assessment](GVM_SCALAR_TAIL_ASSESSMENT.md) suggested that a stronger
angular-affinity lower bound could shorten the positive integrator's Gaussian domain.
This prototype tests that idea in the actual JVM comparison pipeline, including
Double preprocessing, setup cost, adaptive refinement and final error checks.

On the expensive curved fixture, complete comparisons improve from **2.895 ms to
1.142 ms**, about **2.54x**, against the already-optimized audited implementation.
The other four fixtures show little change. The result supports targeted production
work, not a general promise that every GVM comparison gets faster.

The public [scalar comparison](GVM_SCALAR_BHATTACHARYYA.md) remains unchanged. Continue
trying [Fourier first for ordinary coupled laws](GVM_SCALAR_PERFORMANCE.md); this
prototype is not an automatic fallback and does not change the recommendation.

## Quick start: reproduce in three steps

Use JDK 17 and the repository's sbt version. All Scala code here is in the test source set.

1. Run the independent-bound and candidate contract tests:

   ```sh
   sbt "figaro / Test / testOnly com.cra.figaro.test.modernization.GvmScalarCellBoundTest com.cra.figaro.test.modernization.GvmScalarTailCandidateTest"
   ```

2. Measure complete calls in three fresh JVMs. Run this command three times and retain
   all `GVM_TAIL_JVM_` records together as `tail-runs.txt`:

   ```sh
   sbt 'set figaro / Test / fork := true; set figaro / Test / javaOptions ++= Seq("-Xms1G", "-Xmx6G"); figaro / Test / runMain com.cra.figaro.test.modernization.GvmScalarTailPerformance'
   ```

   Shell quoting varies; the inner JVM options must reach sbt with their double quotes.
   Running `runMain` three times in one sbt process with `fork := true` also creates
   three fresh child JVMs, as used for the checked-in study.

3. Summarize with `python -B tools/summarize_gvm_scalar_tail.py tail-runs.txt`.
   Substitute `docs/GVM_SCALAR_TAIL_JVM_RUNS.txt` to reproduce the checked-in table.

Timing is advisory. There is no speed threshold in CI.

## Results and measurement protocol

Java 17.0.4, 32 available processors, 6 GiB maximum heap per measured JVM. Each method
calibrates batches toward 20 ms (cap 4,096 calls), receives at least 500 ms of interleaved
warmup per fixture, recalibrates, then runs seven alternating-order measurement rounds.
Every timed operation is a fresh full comparison; no cached overlap or bound is reused.
Both methods must return `Estimated` within `1e-8` nats of independent reference values.
Their complete results are rechecked after timing. A volatile sink consumes results.

The [raw evidence](GVM_SCALAR_TAIL_JVM_RUNS.txt) retains 210 timing rounds and all
accuracy/work records from three distinct JVMs. Values below are the median of the
three per-JVM medians, not the fastest observed call.

| Fixture | Public audited, microseconds | Candidate, microseconds | Baseline / candidate | Integrand calls, before → after |
| --- | ---: | ---: | ---: | ---: |
| Ordinary linear, concentration 4 | 77.891 | 76.935 | 1.012x | 712 → 712 |
| Nearly opposed, weak coupling, concentration 50 | 71.876 | 71.815 | 1.001x | 764 → 764 |
| Linear coupling, concentration 50 | 200.289 | 193.172 | 1.037x | 1,800 → 1,176 |
| Curved, unequal Gaussian/concentration parameters | 88.407 | 87.319 | 1.012x | 849 → 849 |
| Strong curvature, concentration 50 | 2,894.975 | 1,141.597 | 2.536x | 25,098 → 9,102 |

For the strongest curved case, the per-JVM medians range from 2.843–2.983 ms before
and 1.125–1.207 ms after. Radius drops from 12 to 7; its oracle error is `1.21e-10`
nats. The concentrated linear case demonstrates the tradeoff: fewer integrand calls
produce only a modest elapsed-time improvement after paying for the prepass.
The roughly 1% changes in screened cases are not evidence of a meaningful gain.
These are five bounded fixtures on one host, not a representative application survey.
Do not multiply ratios from different studies into a claimed end-to-end speedup.

## How the JVM bound differs from the research bound

Both use 64 dyadic cells over `[-4,4]`, but this candidate favors conservative, bounded
work over the tightest possible cell minima:

- Interval arithmetic expands basic arithmetic outward with `nextDown`/`nextUp`.
  It encloses the quadratic phase over the entire cell without needing a numerically
  delicate vertex or phase-wrap decision. The cosine's Lipschitz bound supplies a
  lower bound on its absolute value, including intervals that cross zeros.
- Positive 32-term Bessel partial sums provide numerator lower bounds. Two 128-term
  series with explicit geometric remainder bounds provide the denominator upper bound.
  Omitting positive numerator terms can only weaken the candidate lower bound.
- Minimum Gaussian density over each cell times its width supplies a lower mass bound;
  no subtraction of nearby approximate Gaussian CDF values is required.
- `Math.cos` and `Math.exp` results receive a four-ulp allowance. Java 17 specifies
  at most one-ulp error for these operations; square root is correctly rounded.
  See the [Java Math specification](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Math.html).

The bound applies to the **computed Double phase coefficients**, not directly to the
ideal physical-input transformation. The existing separate preprocessing allowance
still accounts heuristically for that transformation. This work does not certify the
complete distance interval: Simpson error estimates, preprocessing estimates and
the existing Gaussian-tail evaluation remain part of the original estimated contract.

The candidate takes the maximum of this cell bound and the old global lower bound,
then reselects the radius with the original tail allocation. The full omitted-tail
contribution remains in the final distance interval. Its value grows when the radius
shrinks; it is not silently removed or replaced with the smaller prepass-domain tail.

## Setup, budgets and screening

The prepass is attempted only when the old radius is at least 8 and
`abs(linear)*radius + abs(quadratic)*radius*radius > 4`. This inexpensive engineering
heuristic skips narrow domains and nearly constant phases. It is not a theorem about
profitability; broader workload validation remains necessary before production use.

A full prepass has a fixed cap: two 128-term and 64 32-term Bessel series, or 2,304
terms. Cancellation is checked at entry, every cell and every term: 2,369 checks.
Caller exceptions propagate; interruption does not clear the interrupt flag.
The original `maxEvaluations` continues to count **integrand calls only**. Prepass
terms are separate bounded setup work, including when a small integrand budget later
causes refusal. The timing table includes this cost; the evaluation-count column does not.
Analytic shortcuts still bypass the prepass and integration entirely.

## Test-only API and helper reference

These are not supported application entry points. Scala helpers are scoped to the
`com.cra.figaro.test.modernization` package except the executable benchmark class.

| Entry point | Parameters, return and example |
| --- | --- |
| `GvmScalarTailCandidate.compare(p,q,tolerance=1e-8,maxEvaluations=50000,cancelled=()=>false)` | Same inputs, result fields, status names and invalid-input rules as the [public scalar API](GVM_SCALAR_BHATTACHARYYA.md), but its own test-only result/enum types. Example: `GvmScalarTailCandidate.compare(p,q)`; returns no distance on numerical refusal. |
| `GvmScalarCellBound.lower(c,l,q,kp,kq,cancelled=()=>false)` | Finite computed phase coefficients, concentrations in `[0,50]`, non-null predicate; returns an affinity lower bound or zero on nonfinite intermediate arithmetic. Invalid arguments throw; cancellation/caller failures propagate. Example: `GvmScalarCellBound.lower(.5,1,2,50,50)`. |
| `GvmScalarTailPerformance.main(args)` | Empty string array only; returns `Unit`, prints the fixed five-case study, throws on failed accuracy/results. Example: `GvmScalarTailPerformance.main(Array.empty)`. |
| Python `gvm_scalar_cell_oracles.rows()` | No arguments; returns 108 tuples `(c,l,q,kp,kq,affinity)` using exact binary64 inputs and 80-digit reference series. |
| Python `gvm_scalar_cell_oracles.scala_source()` | No arguments; returns generated fixture source as a string. Running the script prints it; it does not write files. Requires optional `mpmath==1.3.0`. |
| Python `summarize_gvm_scalar_tail.parse(text)` | Log string; returns three validated run dictionaries. Rejects incomplete, duplicate, nonfinite or inconsistent evidence. Example: `runs = parse(text)`. |
| Python `summarize_gvm_scalar_tail.summarize(runs)` | Validated run dictionaries; returns formatted microsecond medians, ranges and ratios. Example: `print(summarize(runs))`. |
| Python `summarize_gvm_scalar_tail.main()` | Reads CLI log path, prints validated summary, returns `None`. Standard-library-only; no output file writes. |

## Three common patterns

1. **Use Figaro today:** keep the supported call unchanged. Rebuilding this branch
   does not place the candidate in the published JAR.

   ```scala
   val result = GaussVonMisesScalarBhattacharyya.compare(p, q)
   // Inspect status and interval before consuming result.distance.
   ```

2. **Compare policies during development:** inside the modernization test package,
   inspect both numerical results, not just elapsed time.

   ```scala
   val before = GaussVonMisesScalarBhattacharyya.compare(p, q)
   val after = GvmScalarTailCandidate.compare(p, q)
   println((before.radius, after.radius, before.evaluations, after.evaluations))
   println((before.interval, after.interval))
   // Strong curved fixture: (12,7,25098,9102); weak opposed fixture stays (12,12,764,764).
   ```

3. **Verify evidence before proposing production changes:** regenerate the independent
   phase oracles and check that only the radius-selection block differs from production.

   ```sh
   python -B -m unittest discover -s tools -p 'test_gvm_scalar_tail_jvm.py' -v
   ```

## Validation, limitations and next step

Thirteen new Scala tests run alongside the existing modernization suites: **325 tests
across 28 suites pass locally**. Candidate contracts include the full 168-comparison
bidirectional physical-input grid, unequal concentrations, unit changes, precision and
budget refusals, shortcuts, interruption and concurrent calls. Independent lower-bound
controls cover 108 phase fixtures in both directions, including phase-wrap neighbors,
quadratic vertices and large phases. Maximum candidate grid error is `3.44e-10` nats.

Seven Python tests verify generated-oracle freshness, complete 210-round evidence,
malformed-evidence rejection and source provenance: the candidate is identical to the
public audited implementation except its package/object and marked radius block, and
it runs every public scalar contract test. Production source files are unchanged.
All **65 GVM research/evidence tests**, 18 documentation-tool tests, public-reference
freshness and local-link checks pass locally. The preceding high-precision assessment
is integrated on main at `b726fe4f` after
[passing CI](https://github.com/mattwilkinsphoto/figaro/actions/runs/34205631150).

Next: pass CI, add held-out radius/screen-boundary controls, then integrate the bounded
policy into the public scalar implementation with published setup-budget semantics,
artifact/consumer checks and a final paired performance gate. Avoid letting the test
copy become a second independently maintained integrator. This prototype does not add
multidimensional support, automatic Fourier fallback, fusion or application-specific processing.

Related: [tail assessment](GVM_SCALAR_TAIL_ASSESSMENT.md),
[audited totals](GVM_SCALAR_AUDITED_TOTALS.md), [roadmap](../ROADMAP.md).
