# Primitive diagnostic reductions

## Overview: an internal allocation reduction

MCMC diagnostics repeatedly calculate means and sample variances for raw, ranked,
folded and autocovariance traces. Previously these two reductions used mapped iterators
over primitive arrays, creating boxed numeric values. They now use loops over those
same arrays. This changes the representation cost, not the diagnostic estimator.

No new option needs enabling. Rebuild against this checkpoint and use the existing
`McmcDiagnostics.summarize` or multi-chain APIs. The optimization applies to scalar
diagnostic callers too, although the performance study covers the fixed vector workloads,
not every graph-based model. Snapshot modern.10, dependencies and public signatures stay
unchanged. Private package-visible helpers exist for regression testing, not consumer use.

## Protocol before measurement

Branch `modernize/primitive-diagnostic-reductions`, based on `0462f1b0`. Replace only
the iterator/map reductions in `McmcDiagnostics.average` and `variance` with primitive
array loops. Preserve the first-value accumulator, left-to-right summation, shifted
mean, two-pass sample variance and periodic interruption checks. Do not change FFT,
ranking, covariance/ESS formulas, kernel/seed/work budgets, worker counts, defaults,
dependencies or toolchain. The [allocation profile](VECTOR_ALLOCATION_PROFILE.md)
identified these two sites as about 15% of allocation weight and sampled Java execution.

Acceptance: compare both helpers with the prior iterator expressions over signed zero,
ties, cancellation-sensitive sums, subnormal/extreme inputs and fixed-seed arrays around
loop checkpoint boundaries. Require all existing scalar-diagnostic oracles and lifecycle
tests. Validate all 252 full-grid non-timing benchmark fields, including complete
trace/diagnostic fingerprints, against the preceding checkpoint.

Run the unchanged unprofiled `VectorSamplingPerformance 5 4000 500` grid first, then
the unchanged `VectorSamplingProfile` grid in a separate JVM, on the same machine with
1 GiB initial / 6 GiB maximum heap. No other local build/test runs alongside either
measurement. Keep every round and all poor-mixing cases. Compare unprofiled timings
with `parallel-vector-diagnostics-results.csv` and allocation classes/categories with
`vector-allocation-profile-results.csv`. Profiled timings are not a speedup estimate.

Historical-versus-new JVM runs are not interleaved A/B trials. Small timings and sampled
allocation-weight differences may reflect runtime/OS variability. A reduction in boxing
does not establish a proportional wall-time gain, retained-heap reduction or hardware
bandwidth improvement. Report null results and regressions rather than changing inputs
or expanding the optimization after seeing the data.

Implementation/protocol commit `d94aa71d` precedes both full measurements.

## Unprofiled timing results

All 252 runs completed with every non-timing field identical to the preceding
parallel-diagnostics dataset. The [full new CSV](primitive-reduction-performance-results.csv)
retains two JVM warm-up rounds and five measured rounds for all cases and worker counts.
Measurements used the same AMD Ryzen 9 9950X (16 cores / 32 logical processors), Windows
11 Pro, Temurin 17.0.4, Scala 3.9.0, sbt 2.0.8 and 1/6 GiB initial/maximum heap.
No other local build/test was launched alongside either full study. Desktop activity,
affinity, runtime compilation and GC state were not controlled.

Wall times are medians; gains are medians of per-round old/new ratios, not ratios of
displayed medians. Both old/new timing columns use four workers. The final column is
one-to-four-worker scaling within the new implementation, not the gain from this change.

| Fixture / method | Old 4-worker ms | New 4-worker ms | Total gain | Diagnostic gain | New 1-to-4 worker gain |
| --- | --- | --- | --- | --- | --- |
| Gaussian 8D / GPSS | 85.36 | 68.65 | 1.15x | 1.37x | 2.12x |
| Gaussian 8D / Quantile | 76.15 | 61.84 | 1.25x | 1.39x | 2.28x |
| Gaussian 32D / GPSS | 261.10 | 208.91 | 1.26x | 1.37x | 2.43x |
| Gaussian 32D / Quantile | 526.68 | 457.57 | 1.15x | 1.44x | 1.75x |
| Correlated 32D / GPSS | 342.15 | 278.02 | 1.21x | 1.42x | 2.16x |
| Correlated 32D / Quantile | 1047.98 | 979.19 | 1.07x | 1.41x | 1.64x |
| Positive 32D / GPSS | 310.50 | 249.24 | 1.25x | 1.41x | 2.37x |
| Positive 32D / Quantile | 420.71 | 329.83 | 1.30x | 1.43x | 2.46x |
| Dense likelihood 8D / GPSS | 77.39 | 62.71 | 1.23x | 1.38x | 2.94x |
| Dense likelihood 8D / Quantile | 156.07 | 139.02 | 1.12x | 1.38x | 3.45x |
| Mixture 8D / GPSS | 95.03 | 83.16 | 1.17x | 1.32x | 2.08x |
| Mixture 8D / Quantile | 145.80 | 117.33 | 1.24x | 1.51x | 2.20x |

For Gaussian 32D GPSS, worst-coordinate mean ESS/s rises from 57195 to 71801 at four
workers, with identical ESS and traces. The full data also retain the bad mixture estimates:
no-warning quantile runs with coordinate mean error around 4.50 remain no-warning runs
with the same error. This is faster calculation, not improved exploration or coverage.
No four-worker fixture-level paired median regressed in this grid; this is not a claim
that every individual round, worker count, deployment or short trace improves.

## Separate allocation profile

The [new profile aggregates](primitive-reduction-profile-results.csv) and
[profiled benchmark accounting](primitive-reduction-profile-benchmark-results.csv)
retain the second complete 252-run grid. Every non-timing field again matches the
unprofiled run. The unchanged recording harness reported 27907 allocation samples,
4983 Java execution samples and zero lost bytes over a 96.810-second event span.
The raw JFR remains local; only sanitized aggregates and benchmark records are published.

| Profile measure | Before | After | Interpretation |
| --- | --- | --- | --- |
| Total allocation sample weight | 598215223256 bytes | 486621276816 bytes | About 19% lower weighted pressure |
| Diagnostic allocation weight | 234789561280 bytes | 132070472976 bytes | About 44% lower |
| Diagnostic `Double` allocation weight | 126306148072 bytes | 35225494024 bytes | About 72% lower |
| Allocation attributed to mean/variance nearest sites | 89660254272 bytes | 734078424 bytes | Per-element boxing largely removed; not zero allocation |
| GC collections | 586 | 552 | Whole recorded grid, including warm-ups |
| Summed GC pauses | 1.674 s | 1.503 s | Not total GC CPU cost |
| Longest GC pause | 17.905 ms | 16.096 ms | One observed maximum per recording |
| Maximum observed after-GC heap | 75278048 bytes | 74869440 bytes | Roughly unchanged; not process peak RSS |

All allocation values are summed JFR sample weights, not exact allocation accounting.
The recordings cover identical work but have different durations, sample counts and JIT/GC
states. The observed reductions support the source-level change, not a confidence bound
or proof of a proportional memory/runtime reduction. The remaining `average` allocation
site is the array-head access; the change does not promise allocation-free summaries.
GC pauses remain roughly 1.55% of event span, and concurrent collector costs are unmeasured.

Diagnostics still represent 81.18% of sampled Java execution. Their remaining weighted
allocation is led by `Complex` objects (49930780456 bytes), boxed doubles (35225494024),
primitive double arrays (32043241328), and `Complex[]` (6196590872). These measurements
point to **FFT/autocovariance temporary representations** as the next bounded candidate,
with ranking costs also material. Preserve the existing transform normalization, floating
point results, zero padding, lag order and interruption behavior when testing any follow-up.
No FFT, ranking or additional reduction rewrite is included here; no hardware bandwidth
counter was measured.

## Reproducing the two studies

Verification at this checkpoint: compilation, all 152 modernization tests, 41
documentation/report-tool tests, all three vector example workflows and the complete
108-run smoke grid pass locally. The two full studies add 504 completed runs with
unchanged non-timing results. Fresh Scaladoc regenerates the same 11321 public method
entries; the existing four Scaladoc warnings remain. CI validates all three new checked
datasets against the same schema and equality gates, without timing thresholds.

```sh
sbt --server --batch "examples / Compile / runMain com.cra.figaro.example.VectorSamplingPerformance 5 4000 500" > reductions.log
python3 -B tools/summarize_vector_performance.py reductions.log --repetitions 5 --baseline docs/parallel-vector-diagnostics-results.csv
sbt --server --batch "examples / Compile / runMain com.cra.figaro.example.VectorSamplingProfile reductions.jfr 5 4000 500" > reductions-profile.log
python3 -B tools/summarize_vector_profile.py reductions-profile.log
python3 -B tools/summarize_vector_performance.py reductions-profile.log --repetitions 5 --baseline docs/primitive-reduction-performance-results.csv
```

Use a new JFR filename and an existing parent directory. Run these invocations sequentially;
do not overlap them with builds/tests. See the [profiler API and Windows access hook](VECTOR_ALLOCATION_PROFILE.md#api-reference)
for safe recording ownership. Compare profile category/class weights, not source line
numbers: lines shifted when the loops were added. The [full summary schema](VECTOR_SAMPLING_PERFORMANCE.md)
and [profile schema](VECTOR_ALLOCATION_PROFILE.md#api-reference) are unchanged.

## Quick start (three steps)

1. Rebuild Figaro using the [build guide](BUILDING.md).
2. Run your existing diagnostic or multi-chain call; no migration or feature flag is needed.
3. Check estimates and warnings as before, and compare end-to-end time after JVM warm-up.
   Lower allocation does not fix poorly explored targets or justify fewer samples.

## API reference: unchanged consumer interface

`McmcDiagnostics.summarize(chains: Seq[Seq[Double]]): McmcDiagnostics.Summary` accepts
at least two equal-length ordered chains, each with at least four finite draws. It
returns pooled mean, standard deviation, optional R-hat, bulk/tail/raw-mean ESS,
mean MCSE and warnings. Invalid input throws `IllegalArgumentException`; cooperative
interruption throws `InterruptedException` without clearing the flag. See the
[complete diagnostic API](MULTI_CHAIN_MCMC.md) and
[generated reference](api/com.cra.figaro.algorithm.sampling.parallel.md) for every field.

```scala
import com.cra.figaro.algorithm.sampling.parallel.McmcDiagnostics
val chains = Vector.tabulate(4)(c => Vector.tabulate(100)(i => ((i + 7*c) % 17).toDouble))
val summary = McmcDiagnostics.summarize(chains)
println((summary.mean, summary.standardDeviation, summary.warnings))
```

The example is a deterministic API illustration, not a converged posterior sample.
There are no new public constructors, functions or result fields. Existing
[multi-chain vector arguments and lifecycle](MULTI_CHAIN_VECTOR_SAMPLING.md) are unchanged.

## Three common patterns

### 1. Existing multi-chain vector inference

```scala
import com.cra.figaro.algorithm.sampling.VectorSliceSampler as VS
import com.cra.figaro.algorithm.sampling.parallel.MultiChainVectorSliceSampler as MC
val config = MC.Config(VS.Config(VS.Method.GPSS, draws = 4000, seed = 42), parallelism = 4)
def build(i: Int, seed: Long): MC.Model = MC.Model(Vector.fill(32)(0.5 + i / 4.0),
  x => -x.map(v => v*v).sum / 2)
val result = MC.run(config)(build)
println((result.elapsedSeconds, result.diagnostics.map(_.warnings)))
```

Previously each coordinate's mean/variance calculations boxed intermediate scalar values.
Now they traverse primitive arrays directly. Chain simulation, rank/FFT calculations,
retained draws, coordinate scheduling and diagnostic warnings still follow the same path.

### 2. Diagnose a derived event without losing chain identity

```scala
import com.cra.figaro.algorithm.sampling.parallel.McmcDiagnostics
val n = result.diagnosticDrawsPerChain
require(n >= 4)
val event = McmcDiagnostics.summarize(result.chains.map(
  _.result.samples.take(n).map(x => if (x.head > 0) 1.0 else 0.0)))
println((event.mean, event.mcseMean, event.warnings))
```

This uses the same internal reductions automatically. Tied/discrete observations still
receive the same rank handling and potentially unavailable tail diagnostics. Do not
concatenate chains or omit a stuck chain to make a summary look healthier.

### 3. Keep serial execution when memory or CPU contention matters

```scala
val serial = MC.run(config.copy(parallelism = 1))(build)
assert(serial.chains == result.chains)
assert(serial.diagnostics == result.diagnostics)
```

Primitive reductions help without requiring extra threads. `parallelism = 1` still
limits scheduling; it is not an option to restore the previous iterator implementation.
There is no compatibility toggle for an internal allocation-only change.

## Gotchas and related work

- Arithmetic order matters: a different summation algorithm, parallel reduction,
  compensation scheme or one-pass variance formula can change floating-point results.
  This change keeps the first transformed value as accumulator seed and preserves the
  previous left-to-right arithmetic. Bit comparisons canonicalize NaNs; they distinguish
  signed zero and all finite values. No general NaN-payload preservation is promised.
- Helpers require internal nonempty arrays and never mutate them. No pooling, shared
  mutable buffers, retained-array references or global caches are introduced.
- Loop interruption checks run every 1024 indices and at mean entry. Existing outer
  diagnostic checks remain. Sorting and a single FFT call are still non-preemptible.
- Two-pass variance, scaling, rank ties, odd-length splits, ESS caps and warning order
  are unchanged. Small or degenerate traces still cannot certify convergence.
- This removes only two allocation sites. FFT complex arrays, ranking, other reductions
  and density-callback allocations remain. It is not a heap cap or proof of bandwidth gains.

Related: [allocation profile](VECTOR_ALLOCATION_PROFILE.md),
[bounded coordinate diagnostics](PARALLEL_VECTOR_DIAGNOSTICS.md),
[graph multi-chain MCMC](MULTI_CHAIN_MCMC.md),
[diagnostic reliability](MCMC_RELIABILITY.md), and
[vector scaling protocol](VECTOR_SAMPLING_PERFORMANCE.md).
