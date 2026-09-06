# Primitive FFT autocovariance

## Overview and pre-measurement protocol

Branch `modernize/primitive-fft-autocovariance`, based on `bb8be673`. Replace only
the temporary representation in `McmcDiagnostics.autocovariance`. Use the existing
Commons Math 3.6.1 in-place transform with invocation-owned real/imaginary primitive
arrays; preserve zero padding, transform normalization, arithmetic order and lag
division. Keep the full conjugate-product expression and delegate non-finite
components to the existing `Complex` operations. Do not change FFT butterflies,
ranking, ESS, seeds, kernels, work budgets, parallelism, dependencies or defaults.

This is an internal optimization, enabled automatically when Figaro is rebuilt.
Snapshot modern.10 and public signatures remain unchanged. No shared buffer pool,
cache or new user flag is introduced. Scalar diagnostic callers also use this path;
the measured workloads cover vector inference, not arbitrary graph models.

Acceptance: canonical-NaN bit comparisons against the preceding Complex-array
implementation for every lag of 116 edge/seeded arrays, covering signed zero,
constant/impulse/alternating inputs, padding boundaries, extreme scaling and overflow.
Also require an independent direct biased-autocovariance oracle, input immutability,
concurrent-call/output isolation, cancellation flags and all modernization regressions.
Public summaries still reject non-finite observations; exceptional internal tests are
compatibility checks, not a new supported public input domain.

Commit implementation and protocol before measuring. Run the unchanged unprofiled
`VectorSamplingPerformance 5 4000 500` grid, then `VectorSamplingProfile` in a separate
JVM. Use the same machine, 1 GiB initial / 6 GiB maximum heap and fixed seeds, two JVM
warm-up rounds and five measured rounds. Run no other local build/test alongside
either full study. Require all 252 non-timing records, including trace/diagnostic
fingerprints, to match `primitive-reduction-performance-results.csv`; require the
profiled grid to match the new unprofiled grid too. Retain all poor-mixing cases.

Compare unprofiled paired timings against the preceding primitive-reduction checkpoint
and sanitized JFR weights against `primitive-reduction-profile-results.csv`. Historical
and new JVMs are not interleaved A/B trials; runtime/OS noise is uncontrolled. Report
null results and regressions. Profile weights are sampling estimates, not exact allocated
bytes, retained memory, GC CPU cost or measured DRAM bandwidth. Do not use profiled
timings as the performance claim or change the benchmark after seeing results.

Related: [primitive reductions](PRIMITIVE_DIAGNOSTIC_REDUCTIONS.md),
[allocation profiling](VECTOR_ALLOCATION_PROFILE.md),
[vector benchmark protocol](VECTOR_SAMPLING_PERFORMANCE.md).

## Quick start (three steps)

1. Rebuild Figaro using the [build guide](BUILDING.md).
2. Keep your existing diagnostic or multi-chain call; there is no new setting to enable.
3. Check estimates/warnings as before and compare end-to-end time after JVM warm-up.
   A faster summary is not a reason to reduce the sampling budget.

## Unprofiled results

Implementation/protocol commit `b9d9f34f` precedes both measurements. All 252 runs in
the [complete unprofiled CSV](primitive-fft-performance-results.csv) completed with
every non-timing field identical to the preceding primitive-reduction checkpoint,
including warm-up rounds and trace/diagnostic SHA-256 fingerprints. The six fixtures,
two methods, workers 1/2/4, four chains, 4000 draws and 500 warm-up transitions are
unchanged. Seeds are `420013 + 7919 * round`; JVM warm-up rounds are -2/-1 and measured
rounds are 0 through 4. Initial coordinate values remain `0.5 + chainIndex / 4.0`.

Machine/JVM: AMD Ryzen 9 9950X, 16 cores / 32 logical processors, Windows 11 Pro,
Temurin 17.0.4, Scala 3.9.0, sbt 2.0.8, 1 GiB initial / 6 GiB maximum heap. No other
local build/test ran alongside either full study. Desktop activity, CPU affinity,
runtime compilation and GC state were not controlled; these separate-JVM comparisons
are not interleaved A/B trials or confidence intervals.

Wall times are medians. Gains are medians of paired per-round ratios, not ratios of
displayed medians. Old/new timing columns both use four workers; the final column
compares one versus four workers within the new implementation.

| Fixture / method | Old 4-worker ms | New 4-worker ms | Total gain | Diagnostic gain | New 1-to-4 worker gain |
| --- | --- | --- | --- | --- | --- |
| Gaussian 8D / GPSS | 68.65 | 48.63 | 1.40x | 1.57x | 2.67x |
| Gaussian 8D / Quantile | 61.84 | 48.69 | 1.30x | 1.46x | 2.63x |
| Gaussian 32D / GPSS | 208.91 | 159.04 | 1.31x | 1.51x | 2.78x |
| Gaussian 32D / Quantile | 457.57 | 420.30 | 1.10x | 1.46x | 1.77x |
| Correlated 32D / GPSS | 278.02 | 226.46 | 1.23x | 1.58x | 2.34x |
| Correlated 32D / Quantile | 979.19 | 927.98 | 1.04x | 1.54x | 1.68x |
| Positive 32D / GPSS | 249.24 | 200.62 | 1.26x | 1.49x | 2.63x |
| Positive 32D / Quantile | 329.83 | 287.14 | 1.15x | 1.56x | 2.62x |
| Dense likelihood 8D / GPSS | 62.71 | 50.18 | 1.25x | 1.54x | 3.26x |
| Dense likelihood 8D / Quantile | 139.02 | 126.43 | 1.10x | 1.47x | 3.63x |
| Mixture 8D / GPSS | 83.16 | 70.53 | 1.18x | 1.50x | 2.23x |
| Mixture 8D / Quantile | 117.33 | 105.04 | 1.11x | 1.45x | 2.30x |

No four-worker fixture-level paired median regressed in this grid. Individual rounds,
other worker counts, machines and workloads need their own comparisons. Gaussian 32D
GPSS worst-coordinate mean ESS/s rises from 71801 to 93900 with unchanged ESS. Mixture
Quantile rounds 2 through 4 still have no diagnostic warnings despite coordinate mean
error around 4.50: the optimization does not repair wrong-mode sampling.

## Separate allocation profile

The [sanitized profile CSV](primitive-fft-profile-results.csv) and
[full profiled benchmark CSV](primitive-fft-profile-benchmark-results.csv) retain the
second 252-run grid. All runs completed, with every non-timing output identical to the
new unprofiled grid. The recording has 25097 allocation samples, 3827 Java execution
samples, zero reported lost bytes and an 87.728-second event span. Profiled timings
are retained for accounting, not used to claim speedup.

| Profile measure | Before | After | Interpretation |
| --- | --- | --- | --- |
| Total allocation sample weight | 486621276816 bytes | 409677543912 bytes | About 16% lower weighted pressure |
| Diagnostic allocation weight | 132070472976 bytes | 55035570936 bytes | About 58% lower |
| Diagnostic `Complex` weight | 49930780456 bytes | No samples | Ordinary path avoids these objects |
| Diagnostic `Complex[]` weight | 6196590872 bytes | No samples | Object-array FFT round trips removed |
| Diagnostic primitive double-array weight | 32043241328 bytes | 20091293352 bytes | Arrays remain; about 37% lower weight |
| GC collections | 552 | 463 | Whole grid, including warm-ups |
| Summed GC pauses | 1.503 s | 1.259 s | Not total collector CPU cost |
| Longest GC pause | 16.096 ms | 18.514 ms | Worse single observed maximum; retained |
| Maximum observed after-GC heap | 74869440 bytes | 74094976 bytes | Roughly unchanged; not peak process RSS |

These are summed JFR sample weights, not exact allocated bytes or live-memory savings.
Recordings perform identical work but differ in duration, sample counts and runtime
state. No samples for a class is not a general zero-allocation guarantee: non-finite
internal spectrum components still use `Complex` arithmetic. GC pauses occupy about
1.44% of this event span; concurrent collection work and hardware bandwidth are unmeasured.

Diagnostics now account for 13.43% of weighted allocations and 77.16% of Java execution
samples; sampling/model callbacks account for 86.07% and 15.78%, respectively. Remaining
diagnostic allocation weight includes boxed doubles (25776975656 bytes), primitive
double arrays (20091293352) and boxed integers (5232655712). These categories do not
identify an exact end-to-end time saving from any one future rewrite.

**Next bounded candidate: ranking and sorting representation.** The rank-order sort
and two pooled-value sorts together account for 1673 of 3827 Java execution samples
(43.72%). Normal-score calculation and the unchanged FFT itself also remain material.
Investigate primitive sorting/index storage while preserving tie groups, stable ordering
where relevant, signed-zero treatment, folded ranks and exact diagnostic outputs.
This stage does not change ranking, the FFT butterflies or sampler density callbacks.

## Reproduce the measurements

Local verification: compilation, all 157 modernization tests, 41 documentation/report-tool
tests, all three vector example workflows and the complete 108-run smoke grid pass.
Fresh Scaladoc verifies the same 11321 public method entries; four existing Scaladoc
warnings remain. Local documentation links and all three checked datasets validate.
After measurement, a supplemental regression explicitly exercises an infinite forward
spectrum produced by finite observations; production code and benchmark data are unchanged
from the implementation/protocol commit. CI retains the existing tests and adds these
datasets to the schema/equality gates, with no machine-specific timing thresholds.

```sh
sbt --server --batch "examples / Compile / runMain com.cra.figaro.example.VectorSamplingPerformance 5 4000 500" > fft.log
python3 -B tools/summarize_vector_performance.py fft.log --repetitions 5 --baseline docs/primitive-reduction-performance-results.csv
sbt --server --batch "examples / Compile / runMain com.cra.figaro.example.VectorSamplingProfile fft.jfr 5 4000 500" > fft-profile.log
python3 -B tools/summarize_vector_profile.py fft-profile.log
python3 -B tools/summarize_vector_performance.py fft-profile.log --repetitions 5 --baseline docs/primitive-fft-performance-results.csv
```

Run sequentially, with no overlapping build/test. Use a new JFR filename in an existing
directory; the tool refuses to overwrite an existing recording. Follow the
[recording access hook and complete profile contract](VECTOR_ALLOCATION_PROFILE.md#api-reference)
when running on Windows. Raw JFR stays local; publish only sanitized aggregates.
Compare allocation classes/categories, not shifted source line numbers. The benchmark
and profile schemas and validators are unchanged.

## What changes for a user?

Previously, each scalar autocovariance calculation converted a centered real array into
a complex spectrum, built a second array of conjugate products, then converted the
inverse result back to real values. These conversions wrapped individual numbers in
objects and copied full padded arrays. Now two private primitive buffers hold real and
imaginary parts through the forward transform, product and inverse transform. A fresh
result array contains only the requested lags. The same Commons Math FFT is still used.

The change benefits calls that spend substantial time computing diagnostics over many
coordinates or long chains. It also applies with one worker. If model simulation or
the density callback dominates, total gains can be much smaller. Very short traces may
be dominated by startup/scheduling overhead. Measure your own model; there is no new
sampling strategy, automatic worker tuning or general thread-safety guarantee for a
shared Figaro universe.

## API reference: no new public functions

`McmcDiagnostics.summarize(chains: Seq[Seq[Double]]): McmcDiagnostics.Summary`
is unchanged. Supply at least two equal-length ordered chains with at least four finite
draws each. The result contains pooled mean/sample standard deviation, optional R-hat,
bulk/tail/raw-mean ESS, mean MCSE and warnings. Invalid inputs throw
`IllegalArgumentException`; cooperative interruption throws `InterruptedException`
without clearing the flag. See [all fields and contracts](MULTI_CHAIN_MCMC.md) and the
[complete generated reference](api/com.cra.figaro.algorithm.sampling.parallel.md).

```scala
import com.cra.figaro.algorithm.sampling.parallel.McmcDiagnostics
val chains = Vector.tabulate(4)(c => Vector.tabulate(100)(i => ((i + 7*c) % 17).toDouble))
val summary = McmcDiagnostics.summarize(chains)
println((summary.mean, summary.meanEss, summary.warnings))
```

This is an API illustration, not a converged posterior. The package-visible
`autocovariance` helper is internal/testing infrastructure, not a consumer API. Its
result is the biased covariance at lags 0 through N-1, divided by N (not N-lag).
Multi-chain constructors, argument defaults, returned samples and lifecycle contracts
remain unchanged; see the [vector API](MULTI_CHAIN_VECTOR_SAMPLING.md).

## Three common patterns, before and after

### 1. Existing vector inference: unchanged calling code

```scala
import com.cra.figaro.algorithm.sampling.VectorSliceSampler as VS
import com.cra.figaro.algorithm.sampling.parallel.MultiChainVectorSliceSampler as MC
val config = MC.Config(VS.Config(VS.Method.GPSS, draws = 4000, seed = 42), parallelism = 4)
def build(i: Int, seed: Long): MC.Model = MC.Model(Vector.fill(32)(0.5 + i / 4.0),
  x => -x.map(v => v*v).sum / 2)
val result = MC.run(config)(build)
println((result.elapsedSeconds, result.diagnostics.map(_.warnings)))
```

Before: every coordinate's FFT diagnostics created intermediate `Complex` arrays.
After: those transforms use private primitive buffers. The chain simulation and
diagnostic definition are unchanged; no configuration edit opts in or out.

### 2. Derived probabilities: preserve chain identity

```scala
import com.cra.figaro.algorithm.sampling.parallel.McmcDiagnostics
val n = result.diagnosticDrawsPerChain
require(n >= 4)
val event = McmcDiagnostics.summarize(result.chains.map(
  _.result.samples.take(n).map(x => if (x.head > 0) 1.0 else 0.0)))
println((event.mean, event.mcseMean, event.warnings))
```

Before and after use the same expression. The derived event receives the internal FFT
benefit automatically, but its discrete/tied values can still yield unavailable tail
diagnostics. Coordinate diagnostics alone do not establish reliability for this event.
Do not concatenate chains or drop a stuck chain to improve the reported diagnostics.

### 3. Serial diagnostics when CPU contention matters

```scala
val serial = MC.run(config.copy(parallelism = 1))(build)
assert(serial.chains == result.chains)
assert(serial.diagnostics == result.diagnostics)
```

Before and after, one worker bounds scheduling. After rebuilding, one-worker execution
also avoids the intermediate FFT objects. More workers are not required to obtain the
optimization, and more workers may still increase concurrent working memory. This
deterministic example checks exact outputs, not timings.

## Gotchas and limitations

- The transform remains O(L log L), with L the smallest power of two at least 2N.
  Crossing a padding boundary still doubles buffer length. This is not an allocation-free
  summary or a hard heap bound; arrays, ranking storage and retained traces remain.
- Private per-call buffers can be overwritten safely only within that invocation.
  Input arrays and returned results are not shared as scratch space. There is no global
  pool, thread-local retained buffer or change to the existing bounded worker policy.
- Keep the exact complex product, including its imaginary part. Simplifying it to
  squared magnitude plus a literal zero can change exceptional/signed-zero behavior.
  Non-finite spectrum components still use the dependency's `Complex` operations;
  no universal claim of zero `Complex` allocations is made.
- Arithmetic order, STANDARD inverse normalization, zero padding, final division by N,
  lag order and ESS truncation are unchanged. Bit regression checks canonicalize NaNs;
  no NaN-payload identity is promised. Finite/signed-zero values are checked exactly.
- Entry/between-transform checks remain; centering, product and extraction loops now
  also check interruption every 1024 indices. A single dependency FFT call and sorting
  remain non-preemptible. Caller interruption is not cleared.
- Faster diagnostics do not fix missed modes, undercoverage, poor initialization or
  autocorrelation. Keep the existing sample budgets and convergence limitations.

## Dependency rationale

Commons Math's [3.6.1 FFT implementation](https://github.com/apache/commons-math/blob/MATH_3_6_1/src/main/java/org/apache/commons/math3/transform/FastFourierTransformer.java)
already routes its object-array interfaces through `transformInPlace`. This change calls
that same public dependency API directly, not a copied/reimplemented FFT. The
[complex arithmetic source](https://github.com/apache/commons-math/blob/MATH_3_6_1/src/main/java/org/apache/commons/math3/complex/Complex.java)
defines the exceptional-value behavior retained by the fallback. No dependency upgrade,
new library, borrowed FFT implementation or licensing change is needed.
