# Primitive diagnostic sorting

## Overview and pre-measurement protocol

Branch `modernize/primitive-diagnostic-sorting`, based on `a144eb1a`. Replace only the
rank-order and pooled-value sorting representations in `McmcDiagnostics`. Use a stable
bottom-up merge over primitive index arrays, and JDK primitive sorting of copied value
arrays. Preserve finite Double total ordering, stable equal-key index order, signed-zero
handling, the separate numeric-equality rank tie test, normal-score arithmetic and all
diagnostic formulas. Do not change FFT, ESS, sampler kernels, seeds, work budgets,
worker counts, defaults, dependencies or toolchain. Buffers are invocation-owned.

Acceptance: compare exact sorted values/indices with prior Scala expressions on 41
edge/seeded arrays and all 1024 five-value combinations of -1/-0/+0/+1. Compare every
rank-normalized position against the old implementation on 60 four-chain fixtures,
including ties, zeros and extreme finite scales. Require input immutability, concurrent
call/output isolation, interruption flags and all existing modernization tests.

Commit implementation/protocol before measurement. Run the unchanged unprofiled
`VectorSamplingPerformance 5 4000 500` grid, then `VectorSamplingProfile` in a separate
JVM: six fixtures, two methods, workers 1/2/4, four chains, 4000 draws, 500 warm-up
transitions, two JVM warm-up rounds and five measured rounds. Use the same machine and
1 GiB initial / 6 GiB maximum heap. No other local build/test alongside either full study.
Require all 252 non-timing results/fingerprints to match `primitive-fft-performance-results.csv`,
then require the profiled grid to match the new unprofiled grid. Retain all rounds and
poor-mixing cases; do not choose workloads or expand the change after observing results.

Compare unprofiled per-round timing ratios and sanitized JFR weights against the preceding
FFT checkpoint. Separate JVMs are not interleaved A/B trials; OS/JIT/GC variation remains.
Report null results and regressions. Sample weights are not exact allocations, live memory,
collector CPU cost or DRAM bandwidth. Profiled timings do not establish speedups.

Related: [FFT checkpoint](PRIMITIVE_FFT_AUTOCOVARIANCE.md),
[allocation profile](VECTOR_ALLOCATION_PROFILE.md), [benchmark protocol](VECTOR_SAMPLING_PERFORMANCE.md).

## Quick start (three steps)

1. Rebuild Figaro using the [build guide](BUILDING.md).
2. Keep your existing diagnostic/multi-chain call. There is no new flag or dependency.
3. Check estimates and warnings as before; compare end-to-end time after JVM warm-up.

Snapshot modern.10, public signatures, defaults and stored chain output are unchanged.
This is faster internal data handling, not a new sampler or convergence rule.

## What changes and when it helps

Rank diagnostics sort indices into the pooled observations, then assign one normal score
to each equal-value group. Previously this used a generic `sortBy` path with boxed
indices/comparison keys. The new implementation merges primitive integer indices in
private arrays. Equal keys keep their original order. Two other sorts, used to find
the folded median and tail cutoffs, now sort copied primitive double arrays directly.
Normal scores, folded ranks, median/quantile arithmetic and ESS remain unchanged.

The benefit is automatic for scalar summaries, vector coordinate summaries and derived
observable diagnostics. Models with many coordinates or substantial diagnostic overhead
are the intended beneficiaries. Models dominated by simulation or density evaluation may
show little total improvement. This does not add threads; it also applies with one worker.
No shared universe becomes thread-safe as a result.

## API reference: unchanged consumer interface

`McmcDiagnostics.summarize(chains: Seq[Seq[Double]]): McmcDiagnostics.Summary` accepts
at least two equal-length ordered chains, each containing at least four finite draws.
It returns pooled mean/sample standard deviation, optional R-hat, bulk/tail/raw-mean
ESS, mean MCSE and warnings. Invalid inputs throw `IllegalArgumentException`;
cooperative interruption throws `InterruptedException` without clearing the flag.
Odd-length input omits the middle draw only from split diagnostics, not the pooled mean.
See [every field and contract](MULTI_CHAIN_MCMC.md) and the
[complete generated API](api/com.cra.figaro.algorithm.sampling.parallel.md).

```scala
import com.cra.figaro.algorithm.sampling.parallel.McmcDiagnostics
val chains = Vector.tabulate(4)(c => Vector.tabulate(100)(i => ((i + 7*c) % 17).toDouble))
val summary = McmcDiagnostics.summarize(chains)
println((summary.mean, summary.rHat, summary.warnings))
```

This deterministic snippet demonstrates the API, not a converged posterior. There are
no new public functions or options. Package-visible sorting/ranking helpers exist for
internal regression tests, not consumer use. Multi-chain constructors and lifecycle
contracts remain as documented in the [vector guide](MULTI_CHAIN_VECTOR_SAMPLING.md).

## Three common patterns: before versus after

### 1. Existing vector inference

```scala
import com.cra.figaro.algorithm.sampling.VectorSliceSampler as VS
import com.cra.figaro.algorithm.sampling.parallel.MultiChainVectorSliceSampler as MC
val config = MC.Config(VS.Config(VS.Method.GPSS, draws = 4000, seed = 42), parallelism = 4)
def build(i: Int, seed: Long): MC.Model = MC.Model(Vector.fill(32)(0.5 + i / 4.0),
  x => -x.map(v => v*v).sum / 2)
val result = MC.run(config)(build)
println((result.elapsedSeconds, result.diagnostics.map(_.warnings)))
```

Before: each coordinate's rank and quantile calculations traversed generic sorting
representations. After: the same calling code uses primitive sorting storage. The
retained samples, chain seeds, warning rules and numerical summaries are unchanged.

### 2. A derived probability with many ties

```scala
import com.cra.figaro.algorithm.sampling.parallel.McmcDiagnostics
val n = result.diagnosticDrawsPerChain
require(n >= 4)
val event = McmcDiagnostics.summarize(result.chains.map(
  _.result.samples.take(n).map(x => if (x.head > 0) 1.0 else 0.0)))
println((event.mean, event.mcseMean, event.warnings))
```

Both versions assign the same midrank to all equal event values. Primitive storage
does not change this statistical rule or eliminate discrete-tail warnings. This
tie-heavy example illustrates correctness, not a measured speedup for every discrete
observable. Preserve chain identity and order; do not sort your input traces yourself.

### 3. Serial execution under CPU contention

```scala
val serial = MC.run(config.copy(parallelism = 1))(build)
assert(serial.chains == result.chains)
assert(serial.diagnostics == result.diagnostics)
```

The same `parallelism` setting still limits workers. The new sorting path is used with
one worker too. These assertions compare deterministic outputs, not elapsed times;
there is no option to restore the former generic sort.

## Gotchas

- Sorting is internal and operates on copies/indices. The original temporal order is
  needed for autocorrelation; sorting a user's chain before passing it in is incorrect.
- Stable index ordering is preserved for equal comparator keys. Sorting distinguishes
  negative and positive zero, while the existing numeric `==` tie test groups both zeros
  together for ranking. Replacing either comparison rule would change compatibility.
- Primitive value sorting need not preserve the origin of equal values: these arrays
  have no attached identities, and equal finite comparator keys have identical bits.
  Public summaries still reject NaNs and infinities. No new NaN-payload contract exists.
- The index merge uses two N-element integer buffers (about 8N payload bytes, excluding
  array headers); sorted values use a fresh double-array copy plus any JDK workspace.
  The summary is not allocation-free, pooled/cached or subject to a new hard heap cap.
- Index sorting is O(N log N), including sorted/tie-heavy inputs. The former adaptive
  generic sort can exploit existing runs; this change is not guaranteed faster for every
  ordering or size. Small-array timing is especially sensitive to JVM/scheduling noise.
- Index initialization and merging check interruption every 1024 indices, with checks
  at entry and between passes. JDK value sorting and a single FFT call remain
  non-preemptible; there are checks around them. The caller interrupt flag is retained.
- No rank-score cache, approximation, fewer diagnostics, thinning or sample-budget
  reduction is introduced. Poor mixing, missed modes and undercoverage remain possible.

The JDK specifies the floating-point ordering used by
[primitive double sorting](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Arrays.html#sort(double%5B%5D)).
The index merge is Figaro code using that same comparator order; no additional sorting
library, copied third-party implementation or license change is introduced.

## Unprofiled results

Implementation/protocol commit `65bdb63a` precedes both measurements. All 252 runs in
the [full unprofiled CSV](primitive-sorting-performance-results.csv) completed with
every non-timing output identical to the preceding FFT checkpoint, including warm-up
records, budgets, warnings and complete trace/diagnostic SHA-256 fingerprints.
Seeds remain `420013 + 7919 * round`, with JVM warm-up rounds -2/-1 and measured rounds
0 through 4; initial coordinate values remain `0.5 + chainIndex / 4.0`.

Machine/JVM: AMD Ryzen 9 9950X, 16 cores / 32 logical processors, Windows 11 Pro,
Temurin 17.0.4, Scala 3.9.0, sbt 2.0.8, 1 GiB initial / 6 GiB maximum heap. No other
local build/test ran alongside either full study. Desktop activity, affinity and JIT/GC
state were not controlled. Small effects cannot be attributed confidently to the code
change from a single historical-versus-new comparison.

Wall times are medians; gains are medians of paired per-round ratios, **not ratios of
the displayed medians**. Both old/new columns use four workers. The last column is
one-to-four-worker scaling within the new implementation.

| Fixture / method | Old 4-worker ms | New 4-worker ms | Paired total gain | Paired diagnostic gain | New 1-to-4 worker gain |
| --- | --- | --- | --- | --- | --- |
| Gaussian 8D / GPSS | 48.63 | 53.11 | 1.10x | 1.28x | 2.01x |
| Gaussian 8D / Quantile | 48.69 | 42.30 | 1.13x | 1.31x | 2.37x |
| Gaussian 32D / GPSS | 159.04 | 139.98 | 1.13x | 1.27x | 2.61x |
| Gaussian 32D / Quantile | 420.30 | 391.33 | 1.07x | 1.36x | 1.67x |
| Correlated 32D / GPSS | 226.46 | 209.90 | 1.06x | 1.20x | 2.18x |
| Correlated 32D / Quantile | 927.98 | 914.47 | 1.02x | 1.25x | 1.62x |
| Positive 32D / GPSS | 200.62 | 179.96 | 1.10x | 1.28x | 2.50x |
| Positive 32D / Quantile | 287.14 | 259.33 | 1.10x | 1.32x | 2.56x |
| Dense likelihood 8D / GPSS | 50.18 | 44.36 | 1.12x | 1.26x | 3.23x |
| Dense likelihood 8D / Quantile | 126.43 | 119.98 | 1.05x | 1.30x | 3.65x |
| Mixture 8D / GPSS | 70.53 | 66.09 | 1.06x | 1.25x | 2.13x |
| Mixture 8D / Quantile | 105.04 | 99.03 | 1.06x | 1.30x | 2.25x |

**Retained regression:** Gaussian 8D GPSS median runtime increases about 9%, despite
the positive paired-ratio median. Its five old/new ratios are 1.14, 0.89, 1.15, 0.87,
1.10: three improve and two regress. The middle value of these ratios differs from
the ratio of the two runtime medians. Neither statistic should hide the other. This
case's median worst-coordinate mean ESS/s falls from 302199 to 293760. All rounds
remain in the data; no rerun or discarded observation replaces this finding.

Gaussian 32D GPSS mean ESS/s increases from 93900 to 107199, with unchanged ESS.
The paired total gains elsewhere are modest and noisy; this is not a universal speedup
or improved worker scaling. Wrong-mode mixture Quantile rounds 2 through 4 still have
no warnings despite coordinate mean error around 4.50. Statistical reliability is unchanged.

## Separate profile and next decision

The [sanitized profile](primitive-sorting-profile-results.csv) and
[complete profiled benchmark](primitive-sorting-profile-benchmark-results.csv) retain
the second 252-run grid. All runs completed with non-timing outputs identical to the new
unprofiled grid. The recording includes 22927 allocation samples, 3136 Java execution
samples, zero lost bytes and an 80.086-second event span. Its timings are accounting
data, not a replacement for the unprofiled results or their retained regression.

| Profile measure | Before | After | Interpretation |
| --- | --- | --- | --- |
| Total allocation sample weight | 409677543912 bytes | 402270497440 bytes | About 2% lower; small relative to total |
| Diagnostic allocation weight | 55035570936 bytes | 43257611552 bytes | About 21% lower |
| Diagnostic boxed Integer weight | 5232655712 bytes | No samples | Primitive rank indices avoid boxing |
| Diagnostic Object-array weight | 1378360272 bytes | No samples | Not a general zero-allocation guarantee |
| Diagnostic boxed Double weight | 25776975656 bytes | 19522829784 bytes | About 24% lower; other reductions still box |
| Diagnostic double-array weight | 20091293352 bytes | 20979903072 bytes | About 4% higher; copies/workspace remain |
| Diagnostic integer-array weight | 762543688 bytes | 1117461976 bytes | About 47% higher; primitive merge scratch |
| GC collections | 463 | 509 | Increased; collector/runtime state varies |
| Summed GC pauses | 1.259 s | 1.188 s | Not total GC CPU cost |
| Longest GC pause | 18.514 ms | 13.459 ms | One observed maximum per recording |
| Maximum observed after-GC heap | 74094976 bytes | 69331072 bytes | Not peak process RSS or a heap guarantee |

Weights are sampled allocation-pressure estimates, not exact allocated/live bytes.
Counts, durations and JIT/GC state differ across these identical-work recordings;
small total differences do not establish a memory or bandwidth improvement. GC pauses
are about 1.48% of event span; concurrent collector costs are not measured.

Diagnostics account for 10.75% of allocation weight but 73.98% of Java execution samples.
Sampler/model-callback stacks account for 88.51% and 17.86%, respectively. In particular,
the density-callback call site accounts for 69.07% of total allocation weight, including
the benchmark's own model code. That is not evidence that Figaro's call boundary itself
creates all those objects or dominates runtime.

The diagnostic interruption helper now receives 669 execution samples (21.33% of total),
while the two FFT call sites receive 672, primitive value sorting 325 and normal-score
calculation 247. These are sampled stack attributions, not independent wall-time costs.
Do not remove or weaken cancellation checks based only on this ranking.

**Next recommendation: controlled performance revalidation and focused attribution.**
Interleave repeated unprofiled baseline/current runs to assess the noisy small-case
regression, and distinguish sampler-owned allocation from model-callback allocation
before choosing the next rewrite. Investigate interruption/normal-score attribution
without changing responsiveness or diagnostic arithmetic. No further sampler, callback,
normal-score or cancellation-policy optimization is included in this checkpoint.

## Reproduce the two studies

Local verification: compilation, all 160 modernization tests, 41 documentation/report-tool
tests, three vector example workflows and all 108 smoke-grid runs pass. Fresh Scaladoc
regenerates the unchanged 11321 public method entries; four existing Scaladoc warnings
remain. Local documentation links and all three new datasets validate. CI keeps the
existing regression/profile/reference checks and adds the new data to schema/equality
gates, with no timing thresholds. Production code/tests are unchanged from `65bdb63a`.

```sh
sbt --server --batch "examples / Compile / runMain com.cra.figaro.example.VectorSamplingPerformance 5 4000 500" > sorting.log
python3 -B tools/summarize_vector_performance.py sorting.log --repetitions 5 --baseline docs/primitive-fft-performance-results.csv
sbt --server --batch "examples / Compile / runMain com.cra.figaro.example.VectorSamplingProfile sorting.jfr 5 4000 500" > sorting-profile.log
python3 -B tools/summarize_vector_profile.py sorting-profile.log
python3 -B tools/summarize_vector_performance.py sorting-profile.log --repetitions 5 --baseline docs/primitive-sorting-performance-results.csv
```

Run sequentially, without another local build/test. Use a new JFR filename in an existing
directory; recordings are never overwritten. Follow the
[profile API and Windows access hook](VECTOR_ALLOCATION_PROFILE.md#api-reference).
Only sanitized aggregate CSV and complete benchmark records are published; raw JFR
remains local. Compare classes/categories rather than source lines, which have shifted.
