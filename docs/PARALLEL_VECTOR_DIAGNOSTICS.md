# Bounded parallel coordinate diagnostics

## Overview

`MultiChainVectorSliceSampler` now uses its existing worker allowance for coordinate
diagnostics as well as sampling. Sampling workers exit before diagnostic workers start.
The [earlier scaling study](VECTOR_SAMPLING_PERFORMANCE.md) identified serial diagnostics
as the dominant cost on inexpensive targets. This change schedules independent coordinate
summaries concurrently; it does not approximate, omit or change their calculations.

## Measurement protocol (recorded before the new run)

Compare against the checked 252-row baseline from `b4d26d97` with the unchanged
`VectorSamplingPerformance` fixture grid, seeds, starts, budgets and phase instrumentation.
Run two JVM warm-up rounds and five measured rounds, four chains, 4000 retained draws,
500 sampler warm-up transitions, both methods, all six fixtures, workers 1/2/4.
Retain every round and require every non-timing field, including complete trace and
diagnostic SHA-256 fingerprints, to match the baseline exactly. Do not tune inputs or
drop poor-mixing cases after seeing results. No other local build/test runs alongside
measurement. Record total and diagnostic-phase gains, including any regressions.

The baseline and optimized measurements use separate JVM invocations, not interleaved
A/B trials. Same seeds pair work/statistics, not OS load or GC state; small timing
differences are not reliable rankings. The benchmark is not a memory profiler.

Protocol and implementation were committed at `829b36e5` before measurement.

## Results: faster diagnostics, identical statistics

On 6 September 2026 all **252 runs completed** (180 measured plus 72 JVM warm-ups),
with zero failed/incomplete cases. Every non-timing field matched the baseline, including
warm-up fingerprints, all traces, evaluation accounting, summaries and warnings. The
[complete new CSV](parallel-vector-diagnostics-results.csv) preserves all rounds; the
[baseline CSV](vector-sampling-performance-results.csv) is unchanged.

Both measurements used an AMD Ryzen 9 9950X (16 cores / 32 logical processors), Windows
11 Pro, Temurin JDK 17.0.4, Scala 3.9.0 and sbt 2.0.8, with 1 GiB initial and 6 GiB
maximum heap. Each study used one sbt JVM, with no other local builds/tests launched
alongside it. OS/background activity, affinity and GC state were not controlled.
The optimized measured grid consumed the same 156590286 density evaluations.

Times below are medians. Gains are medians of paired per-round ratios, not ratios of
the displayed median times. Old/new columns both use four workers; the final column
compares one and four workers within the new implementation.

| Fixture / method | Old 4-worker ms | New 4-worker ms | Total gain | Diagnostic gain | New 1-to-4 worker gain |
| --- | --- | --- | --- | --- | --- |
| Gaussian 8D / GPSS | 157.25 | 85.36 | 1.87x | 2.12x | 1.87x |
| Gaussian 8D / Quantile | 154.04 | 76.15 | 2.04x | 2.44x | 2.12x |
| Gaussian 32D / GPSS | 576.11 | 261.10 | 2.21x | 2.57x | 2.20x |
| Gaussian 32D / Quantile | 829.82 | 526.68 | 1.58x | 2.48x | 1.78x |
| Correlated 32D / GPSS | 623.13 | 342.15 | 1.81x | 2.40x | 2.02x |
| Correlated 32D / Quantile | 1331.46 | 1047.98 | 1.28x | 2.41x | 1.70x |
| Positive 32D / GPSS | 607.17 | 310.50 | 1.93x | 2.44x | 2.24x |
| Positive 32D / Quantile | 693.94 | 420.71 | 1.64x | 2.44x | 2.82x |
| Dense likelihood 8D / GPSS | 150.47 | 77.39 | 1.94x | 2.45x | 2.60x |
| Dense likelihood 8D / Quantile | 230.94 | 156.07 | 1.48x | 2.46x | 3.26x |
| Mixture 8D / GPSS | 166.42 | 95.03 | 1.76x | 2.45x | 2.03x |
| Mixture 8D / Quantile | 209.37 | 145.80 | 1.44x | 2.24x | 2.08x |

The dense-likelihood quantile case now realizes 3.26x end-to-end scaling from one to
four workers, rather than the baseline study's 2.18x. Gaussian 32D GPSS improves from
576.11 to 261.10 ms at four workers; its median worst-coordinate mean ESS/s rises from
25849 to 57195, with unchanged estimates and diagnostics. Diagnostic time still accounts
for 76.52% of that optimized run. Allocation/GC and memory-bandwidth profiling are the
next measurement, not a claim that their individual costs have already been identified.

**Not every timing improves.** For example, one-worker positive-target quantile median
time rises from 925.73 to 1188.04 ms between studies even though its seeded work is
identical. These separate-JVM data cannot isolate the cause or certify absence of serial
regressions. Four-worker gains above do not imply a universal improvement at every
worker count, small trace length, dimension or memory limit. All worker-level data and
CPU/GC counters remain in the CSV and standard summary output.

The problematic statistical results are unchanged too: quantile mixture rounds 2-4
still show no coordinate warnings, maximum R-hat below 1.001 and mean error about 4.50.
Higher apparent ESS/s for those runs is **not** evidence of accurate mode weights or
convergence. Correlated and positive-target GPSS warning cases were not excluded.

## Quick start (three steps)

1. Build the same explicit-density model factory described in the
   [multi-chain vector guide](MULTI_CHAIN_VECTOR_SAMPLING.md).
2. Choose `parallelism = 2` or `4` if independent coordinate diagnostics justify the
   extra CPU and scratch memory. Existing calls already receive this scheduling change;
   `parallelism = 1` selects serial diagnostics and one sampling worker.
3. Compare end-to-end time and inspect all diagnostic warnings and chain statuses.
   More threads do not improve the statistical quality of the fixed seeded traces.

## API and execution contract

No new public function, configuration field or result field is introduced. See the
[complete API contract](MULTI_CHAIN_VECTOR_SAMPLING.md#api-reference) for parameters,
returns, failures and examples of `run`, `Config`, `Model` and result accessors.
`Config.parallelism` keeps its default of 4 and now bounds both phases:

| Phase | Maximum workers |
| --- | --- |
| Model construction | Caller thread, serial |
| Sampling | `min(chains, parallelism)` |
| Coordinate diagnostics | `min(dimension, chains, parallelism)` |

Diagnostic dispatch submits only one task per worker, not a future or transposed trace
for every coordinate. Each worker owns the temporary arrays for one coordinate at a
time. Summary results are returned in coordinate-index order regardless of completion
order. With one diagnostic worker, computation stays on the caller thread.

The shortest-chain prefix rule, odd-length handling, estimator arithmetic, warning
contents/order, seeds, retained traces and evaluation accounting are unchanged. Pool
shutdown and thread joins finish before successful return. The existing shutdown budget
applies separately to each non-overlapping pool's cleanup, not to the whole run.
Unexpected diagnostic errors propagate without partial results; sibling work is
interrupted. Cleanup failures remain visible without hiding the primary error.

`McmcDiagnostics.summarize(chains)` also now checks cooperative interruption between
stages and within rank/ESS loops, throwing `InterruptedException` without clearing the
interrupt flag. Its parameters, summary values and warnings are otherwise unchanged.
Sorting, array operations and an individual third-party FFT call are not preemptible;
cancellation is cooperative, not a hard real-time guarantee.

## Three common patterns

### 1. Compare serial and parallel execution on a cheap multidimensional target

```scala
import com.cra.figaro.algorithm.sampling.VectorSliceSampler as VS
import com.cra.figaro.algorithm.sampling.parallel.MultiChainVectorSliceSampler as MC

val config = MC.Config(VS.Config(VS.Method.GPSS, draws = 4000, seed = 42),
  chains = 4, parallelism = 1)
def build(i: Int, seed: Long): MC.Model = MC.Model(Vector.fill(32)(0.5 + i / 4.0),
  x => -x.map(v => v * v).sum / 2)
val serial = MC.run(config)(build)
val parallel = MC.run(config.copy(parallelism = 4))(build)
assert(serial.chains == parallel.chains)
assert(serial.diagnostics == parallel.diagnostics)
println((serial.elapsedSeconds, parallel.elapsedSeconds))
```

Previously only the chain computations overlapped. Now up to four coordinate summaries
also overlap after those chains finish. Repeat measurements after JVM warm-up; one tiny
run does not establish a speedup.

### 2. Limit memory pressure or avoid nested oversubscription

```scala
// Reuse config/build above. Useful when several jobs already run concurrently.
val limited = MC.run(config.copy(parallelism = 1))(build)
println(limited.diagnostics.map(_.warnings))
```

This restores serial diagnostic scheduling. Two workers are another explicit compromise.
`maxStoredValues` limits retained trace values, NOT total heap. Concurrent coordinate
scratch space, FFT arrays and ranking allocations can multiply by the diagnostic worker
count. Pools are private per invocation; there is no global CPU/memory scheduler.

### 3. Validate an optimization without hiding wrong-mode results

```text
sbt "examples / Compile / runMain com.cra.figaro.example.VectorSamplingPerformance 5 4000 500"
python3 -B tools/summarize_vector_performance.py benchmark.log --repetitions 5 --baseline docs/vector-sampling-performance-results.csv
```

Capture the benchmark output as `benchmark.log` using your shell or build runner. The
new optional `--baseline PATH` argument validates the baseline using the same grid and
rejects any non-timing difference before displaying cross-revision ratios. All existing
[summary options and CSV fields](VECTOR_SAMPLING_PERFORMANCE.md) remain supported.
Failed/incomplete paired cases receive no speedup estimate. All rounds remain present.

## Gotchas and related work

- This is vector-runner scheduling, not thread safety for arbitrary Figaro graphs or
  parallelization of the graph-based multi-chain MH diagnostics.
- Fewer than four aligned draws still produce no coordinate diagnostics. One coordinate
  cannot benefit from coordinate parallelism. Very short traces may cost more to dispatch.
- More workers do not improve ESS, R-hat or mode exploration for identical traces. The
  baseline mixture counterexample still applies even when diagnostics show no warnings.
- No automatic worker tuning, precision stopping, adaptation or dependency change is added.
- See [multi-chain vector sampling](MULTI_CHAIN_VECTOR_SAMPLING.md),
  [scalar diagnostics and MH](MULTI_CHAIN_MCMC.md),
  [diagnostic reliability](MCMC_RELIABILITY.md) and the
  [baseline study](VECTOR_SAMPLING_PERFORMANCE.md).

## Verification checkpoint

All 150 modernization regressions pass, including six new coordinate-diagnostic groups:
exact odd/tied/constant/extreme summaries, bounded execution and ordering, failure with
sibling cancellation, caller interruption, scalar/serial pre-interruption, and bounded
shutdown failure without masking the primary error. Existing kernel, alignment, seed,
nested-run and scalar diagnostic reference tests remain required.

Compilation, Scaladoc, all three vector example workflows, the 108-run small benchmark
grid, 25 report-tool tests and 12 documentation-tool tests pass. The full new dataset
matches all 252 baseline records in every non-timing field. CI validates both datasets
and cross-revision identity, with no machine-dependent speed threshold. Public-method
inventory remains 11321 entries; local links and generated-reference freshness are checked.

The previous checkpoint's [CI run](https://github.com/mattwilkinsphoto/figaro/actions/runs/34054229083)
passed vector and documentation gates but failed the legacy anytime-lifecycle step.
All four tests in that step pass locally on this branch. Detailed remote logs were
unavailable (HTTP 403); this does not establish its cause or resolve the Linux failure.
No legacy test, tolerance or gate was weakened. This is not a full historical-suite,
packaging/publication or final CI-success claim.
