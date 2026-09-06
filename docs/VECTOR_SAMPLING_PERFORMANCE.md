# Multi-chain vector scaling study

This is the preserved **serial-diagnostics baseline**. The subsequent implementation and
matching-trace results are in [bounded parallel diagnostics](PARALLEL_VECTOR_DIAGNOSTICS.md).
The benchmark source now exercises that newer scheduling when run on the follow-up branch.

## Outcome: optimize diagnostics next, not just worker count

The 6 September 2026 study completed all **252 runs** (180 measured plus 72 JVM warm-ups),
with no failed or incomplete runs. Every timing-free fingerprint matched across worker
counts. The [checked CSV](vector-sampling-performance-results.csv) retains every round.
Protocol and instrumentation were committed at `46df612b` before measurement.

Measurements used an AMD Ryzen 9 9950X (16 cores / 32 logical processors), Windows 11 Pro,
Temurin JDK 17.0.4, sbt 2.0.8, Scala 3.9.0, and a 6 GiB maximum / 1 GiB initial heap.
The example ran inside one sbt JVM; no other build or benchmark was launched alongside
it by the study. Background desktop/OS work was not controlled. Measured rounds consumed
156590286 density evaluations in total, including repeated worker configurations.

Four workers help most when the density is expensive. On the dense-likelihood quantile
fixture, the sampling phase improves **3.74x**, but end-to-end time improves **2.18x**
because diagnostics remain serial. For GPSS Gaussian fixtures, diagnostics consume
about **88-89%** of four-worker runtime and total speedup is only **1.04-1.07x**.
This is direct phase measurement, not a replay/subtraction estimate.

### Scheduling results (all five measured rounds retained)

Wall times are medians. Speedups are medians of paired per-round ratios against one
worker. Small differences between two and four workers are not a reliable fine ranking.

| Fixture / method | 1-worker ms | 4-worker ms | Wall speedup: 2 workers | Wall speedup: 4 workers | Sampling speedup: 4 workers | Diagnostics at 4 workers |
| --- | --- | --- | --- | --- | --- | --- |
| Gaussian 8D / GPSS | 162.62 | 157.25 | 1.03x | 1.04x | 1.41x | 88.03% |
| Gaussian 8D / Quantile | 171.71 | 154.04 | 1.09x | 1.10x | 1.70x | 84.45% |
| Gaussian 32D / GPSS | 614.24 | 576.11 | 1.06x | 1.07x | 1.63x | 88.99% |
| Gaussian 32D / Quantile | 991.51 | 829.82 | 1.19x | 1.20x | 1.52x | 61.75% |
| Correlated 32D / GPSS | 693.31 | 623.13 | 1.11x | 1.12x | 1.56x | 78.72% |
| Correlated 32D / Quantile | 1800.55 | 1331.46 | 1.33x | 1.36x | 1.58x | 37.39% |
| Positive 32D / GPSS | 681.70 | 607.17 | 1.11x | 1.14x | 1.83x | 83.40% |
| Positive 32D / Quantile | 925.73 | 693.94 | 1.25x | 1.32x | 2.23x | 73.56% |
| Dense likelihood 8D / GPSS | 206.14 | 150.47 | 1.23x | 1.37x | 3.46x | 84.76% |
| Dense likelihood 8D / Quantile | 502.35 | 230.94 | 1.56x | 2.18x | 3.74x | 56.27% |
| Mixture 8D / GPSS | 194.10 | 166.42 | 1.15x | 1.17x | 1.63x | 74.53% |
| Mixture 8D / Quantile | 286.27 | 209.37 | 1.26x | 1.37x | 1.99x | 61.76% |

### Effective-sample throughput must be read with correctness evidence

ESS/s is the median of the worst-coordinate raw-mean ESS divided by end-to-end seconds.
The R-hat and absolute mean-error columns are maxima over coordinates and measured rounds.
Warnings are counts of measured rounds with any coordinate warning (out of five).
Statistical outputs are identical across worker counts, so faster scheduling cannot repair
their errors. The table deliberately includes throughput estimates from badly explored targets.

| Fixture / method | ESS/s: 1 worker | ESS/s: 4 workers | Max R-hat | Max absolute mean error | Warning rounds |
| --- | --- | --- | --- | --- | --- |
| Gaussian 8D / GPSS | 92517 | 95682 | 1.001 | 0.0181 | 0/5 |
| Gaussian 8D / Quantile | 88095 | 96942 | 1.001 | 0.0259 | 0/5 |
| Gaussian 32D / GPSS | 24192 | 25849 | 1.002 | 0.0215 | 0/5 |
| Gaussian 32D / Quantile | 14780 | 17841 | 1.001 | 0.0284 | 0/5 |
| Correlated 32D / GPSS | 1051 | 1185 | 1.615 | 0.0680 | 5/5 |
| Correlated 32D / Quantile | 17.66 | 24.00 | 1.388 | 0.2246 | 5/5 |
| Positive 32D / GPSS | 7.29 | 8.26 | 1.791 | 1.5562 | 5/5 |
| Positive 32D / Quantile | 8632 | 11531 | 1.001 | 0.0304 | 0/5 |
| Dense likelihood 8D / GPSS | 72696 | 100516 | 1.001 | 0.0053 | 0/5 |
| Dense likelihood 8D / Quantile | 30633 | 66150 | 1.001 | 0.0102 | 0/5 |
| Mixture 8D / GPSS | 61.46 | 69.19 | 1.148 | 0.9445 | 4/5 |
| Mixture 8D / Quantile | 43576 | 63283 | 1.528 | 4.5121 | 2/5 |

**The mixture's apparent ESS/s is not trustworthy evidence of accurate inference.**
In quantile rounds 2, 3, and 4, maximum R-hat is below 1.001, there are no coordinate
warnings, and estimated minimum ESS is about 12827-14134. Yet maximum coordinate mean
error is about 4.50 against the true mean -1.5. This is consistent with chains exploring
the same wrong mode well while failing global exploration. Starts here are deliberately
ordinary positive points, not dispersed mode-aware starts. Do not remove these rounds,
change starts after seeing the answer, or label no-warning output as convergence.

### Recommended next optimization

Prioritize **bounded parallel coordinate diagnostics**, retaining the exact estimator,
chain alignment and warning semantics. Coordinates can be summarized independently after
sampling, but worker ownership, aggregate memory pressure, ordering and interruption must
remain explicit. Benchmark the same fixed traces before and after; do not improve timings
by omitting diagnostics or weakening their calculations. Allocation profiling is the next
measurement if scaling remains limited. No such optimization is implemented in this study.

For users now: two workers are a reasonable starting comparison for cheap models, while
four deserve testing on expensive densities. These are results from four fixed chains on
this machine, not a universal default recommendation. Multimodal exploration and reliable
mode-weight estimation remain separate statistical work, regardless of diagnostic speed.

## Protocol recorded before measurement

Branch `modernize/vector-sampling-performance`, based on `92e3b646`. This is a benchmark,
not a kernel/default/API migration; snapshot modern.10 and toolchain remain unchanged.
Four independent chains each request 4000 retained draws after 500 discarded warm-up
transitions, with a generous 100 million density-call cap per chain and unchanged
10000-proposal search limits. Worker counts are 1, 2, and 4. GPSS and quantile are both
tested on all six fixtures: Gaussian 8D, Gaussian 32D, correlated Gaussian 32D,
positive exponential 32D, dense Gaussian likelihood 8D, asymmetric mixture 8D.

Five measured rounds follow two negative-index JVM warm-up rounds. Within each
fixture/method, rotate and alternate worker order to reduce order bias. Root seed is
`420013 + 7919 * round`, fixed across workers and fixtures; chain seeds follow the
production wrapper's index-ordered expansion. The complete study contains 252 runs,
180 measured and 72 JVM warm-up runs. No after-result tuning or dropping slow rounds.

Gaussian targets have unit variance. Correlated covariance is `0.05 I + 0.95 11^T`.
Positive coordinates are independent rate-1 exponentials. The mixture is
`0.9 N(-2*1, 0.25 I) + 0.1 N(3*1, 0.25 I)` with a shared mode label.
The dense-likelihood model has a standard Normal prior and 64 zero-valued unit-noise
observations with normalized Hadamard design rows in eight dimensions: `X'X = 8 I`,
so its exact posterior is `N(0, I/9)`. The density evaluates the actual 64 row dot products,
not a simplified posterior expression or synthetic delay. Starts are `0.5 + chain/4`
in every coordinate, independent of worker count, with no exact-target initialization.

Package-private instrumentation separates serial construction/validation, pool creation
through sampling and joined shutdown, and aligned coordinate diagnostics/result preparation.
Their times sum to runner end-to-end time; no per-density timers alter the hot loop.
JVM process CPU time includes concurrent runtime/GC work. GC time is the sum of available
collector counters, not an allocation or peak-memory measurement. SHA-256 fingerprints of
all traces, seeds, evaluation counts, statuses and coordinate diagnostics must match across
worker counts, excluding timing. Fingerprint computation, CSV printing and validation are
outside the measured runner interval. Only one benchmark process runs at a time.

Report median paired end-to-end and sampling-phase speedup relative to one worker,
diagnostic share of wall time, worst-coordinate raw-mean ESS per end-to-end second,
maximum coordinate R-hat, warning-bearing runs and coordinate mean errors. ESS/s is a
diagnostic estimate, not proof of convergence; high R-hat or target errors can invalidate
a superficially attractive throughput number. Derived events/mode weights are not diagnosed
in this timing study. Failed/incomplete runs remain explicit, and no timing gain can make
them successful inference. Cross-method comparisons use equal requested draws, **not equal
density-call budgets**, so these results are primarily within-method scheduling comparisons.

The measurements are machine/JVM/workload-specific and exploratory, not a JMH study,
universal speed guarantee, coverage experiment, or evidence for changing sampler defaults.

## Overview: how to use this study

The question is whether more workers give more **useful sampling per elapsed second**,
not whether more threads are running. Chain count, starts, seeds, retained draws, and
discarded MCMC warm-up stay fixed when workers change. This isolates scheduling effects.
Coordinate diagnostics remain enabled in every timing, even when they are expensive.
Sampling phases include pool startup and joined shutdown; they are not isolated kernel
throughput. No production optimization or sampler-selection rule is introduced here.

Use this benchmark to decide whether your next optimization should target density
evaluation, worker scheduling, or diagnostics. Do not interpret a larger ESS/s on a
poorly mixed target as more accurate inference. ESS and R-hat are estimated from finite
traces, and small coordinate mean errors cannot rule out incorrect tails or mode weights.

## Quick start (three steps)

1. Run the small execution check:
   `sbt --server --batch "examples / Compile / runMain com.cra.figaro.example.VectorSamplingPerformance 1 100 20" > vector-smoke.log`.
2. Validate every fixture, method, worker and warm-up round:
   `python3 -B tools/summarize_vector_performance.py vector-smoke.log --repetitions 1 --draws 100 --warm-up 20`.
3. Run the full command below when no other build/benchmark is using the machine; compare
   within each fixture/method, using end-to-end speedup and diagnostic warnings together.

The small check tests execution and report integrity, not performance or convergence.

## Public entry point and report-tool reference

`VectorSamplingPerformance.main(args: Array[String]): Unit` is the only public benchmark
entry point. Zero to three numeric strings specify measured repetitions (default 5,
1-100), retained draws per chain (4000, 4-100000), and discarded warm-up transitions
(500, 0-100000). All fixtures, both methods, four chains, three worker counts and two
extra JVM warm-up rounds always run. Example:
`VectorSamplingPerformance.main(Array("1", "100", "20"))`.

Returns Unit and prints quoted CSV. Invalid arguments throw. Runtime/model failures
produce explicit `Failed` rows; incomplete evaluation-capped runs remain `Incomplete`.
Interruption aborts. Any differing fingerprint across worker counts throws rather than
reporting a speed comparison on different outputs. Zero exit alone does not prove all
runs succeeded: inspect the status counts and validate report completeness.

`summarize_vector_performance.py LOG... --repetitions N [--draws N] [--warm-up N]` accepts
logs or normalized CSV, validates the exact requested grid, and prints measured-round
tables. Defaults for draws/warm-up match the full study; repetitions is required. Missing
or duplicate records, changed seeds/budgets, mismatched non-timing outputs, invalid
numeric fields or inconsistent timing partitions raise errors. `--output PATH` exclusively
creates a normalized CSV (never overwrites); `--acl-script PATH` optionally invokes a
trusted Windows access hook after creation. Internal Python helpers and package-private
phase instrumentation are not supported application APIs.

| CSV field(s) | Interpretation |
| --- | --- |
| `fixture`, `method`, `workers`, `round` | Run identity; rounds -2 and -1 are JVM warm-up, not measured statistics |
| `seed`, `draws`, `warmUp` | Root seed and per-chain work; `warmUp` is discarded MCMC work on every run, distinct from JVM warm-up rounds |
| `status`, `error` | Complete requested draws, incomplete budget, or explicit failure; failures have no invented estimates |
| `wallSeconds` | Construction + sampling/shutdown + diagnostics, from the actual runner |
| `constructionSeconds` | Serial factory construction and structural validation, not initial density calls |
| `samplingSeconds` | Pool construction, chain initialization/warm-up/retained sampling, and joined shutdown |
| `diagnosticsSeconds` | Alignment, coordinate summaries and result preparation on the caller thread |
| `cpuSeconds`, `gcSeconds` | Process CPU and aggregate collector-time deltas; CPU may be NaN when unavailable; neither measures peak memory |
| `evaluations`, `alignedDraws` | Density calls summed over all four chains; common retained prefix length per chain |
| `minMeanEss`, `minBulkEss`, `minTailEss` | Minimum diagnostic ESS across coordinates, NaN if any coordinate lacks that diagnostic |
| `maxRHat`, `maxMeanError` | Maximum coordinate R-hat and absolute coordinate mean error against the analytic target mean |
| `warningCoordinates` | Number of coordinate summaries carrying any diagnostic warning |
| `fingerprint` | Hash of timing-free traces/metadata/diagnostics, checked across worker counts |

The summary's ESS/s divides each run's worst-coordinate raw-mean ESS by that run's
end-to-end seconds, then takes the median. Speedup is the median of per-round paired
one-worker/time ratios, **not** a ratio of independent medians. Diagnostic percentage is
likewise a median of each run's phase/total ratio. Max R-hat is the maximum across all
measured rounds and coordinates. Warning counts are numbers of measured runs with any
coordinate warning. If any compared measured run is failed/incomplete, its comparison
table uses N/A instead of quietly selecting successful rounds. Raw failure records stay
available. Missing ESS is unavailable, not zero or an excuse to drop that round.

## Three common workflows

### 1. Reproduce the predeclared workload

```sh
sbt --server --batch "examples / Compile / runMain com.cra.figaro.example.VectorSamplingPerformance 5 4000 500" > vector-performance.log
python3 -B tools/summarize_vector_performance.py vector-performance.log --repetitions 5
```

Record CPU, OS, JDK, heap settings and background load. Do not compare a cold one-worker
run with a warm four-worker run, change seeds between worker counts, or run all worker
configurations simultaneously in separate processes.

### 2. Compare scheduling, not different inference budgets

```scala
import com.cra.figaro.algorithm.sampling.VectorSliceSampler as VS
import com.cra.figaro.algorithm.sampling.parallel.MultiChainVectorSliceSampler as MC
val fixed = MC.Config(VS.Config(VS.Method.GPSS, draws = 4000, warmUp = 500, seed = 420013),
  chains = 4, parallelism = 1)
val parallel = fixed.copy(parallelism = 4)
// Supply the same deterministic, equivalent-target factory to both configurations.
// Compare chains/diagnostics for equality; elapsedSeconds is expected to differ.
```

This changes concurrency only. Increasing `chains` or `draws` at the same time would
change the statistical work, so it would not answer the same scaling question.

### 3. Identify the limiting phase

```sh
python3 -B tools/summarize_vector_performance.py vector-performance.log --repetitions 5 --output normalized-vector-performance.csv
```

Compare paired sampling speedup with paired wall speedup. Large sampling gains but small
wall gains suggest serial diagnostics/construction are limiting. If sampling itself
does not improve, investigate density cost, allocation, pool overhead and contention.
The raw per-round CPU/GC/error fields help explain anomalies; do not remove anomalies
merely because they weaken a speedup claim.

## Gotchas and related work

- Five measured rounds support an exploratory comparison, not precise confidence limits.
  The desktop is not an isolated performance lab; unrelated OS activity and power policies
  can affect timings. No CPU affinity, governor control, allocation profiler or heap-peak
  measurement is included. Two JVM warm-ups do not prove compilation/GC steady state.
- The dense likelihood is a deliberately redundant but valid regression model. Its result
  should not be generalized to every expensive posterior. Model algebraic simplification
  could matter more than adding workers.
- Coordinate diagnostics are not a joint convergence certificate. In particular, mixture
  mode weights and event/tail precision need their own validation before trusting ESS/s.
- No default is changed, and no wall-clock timing threshold gates CI. CI checks correctness,
  phase accounting, output identity and complete smoke reports, not machine-dependent speed.

Local verification: all 144 modernization regressions, 35 report/documentation-tool tests,
the complete 252-row study and 108-row smoke grid passed. The public-method inventory
remains at 11321 entries; phase instrumentation is package-private. The predecessor
multi-chain milestone's full CI run passed before this benchmark was published.

Related: [multi-chain vector API](MULTI_CHAIN_VECTOR_SAMPLING.md),
[single-chain kernels](VECTOR_SLICE_SAMPLING.md), [high-dimensional statistical limits](SAMPLING_HIGH_DIMENSIONAL.md),
and [earlier graph-runner performance](MULTI_CHAIN_MCMC.md).
