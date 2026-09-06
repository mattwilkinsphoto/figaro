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

Results will be added after correctness checks and the fixed-grid measurement.

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
