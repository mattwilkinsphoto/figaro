# Resource and scaling assessment

## Overview

This development-only study measures where memory and worker scaling limit Figaro's
modernized samplers. It does not change a production API, sampler default, stopping
rule, diagnostic, or JVM setting for ordinary users. Use it to distinguish application
callback costs from library costs before proposing another optimization.

## Protocol fixed before full measurement

Use a pinned runtime snapshot on Java 17, Scala 3.9.0 and sbt 2.0.8, with a 1 GiB initial
and 6 GiB maximum heap, 6 MiB thread stacks, and four chains. Each case runs in a fresh
JVM, performs two full discarded warm-ups using the same seed/work, then measures one
batch. Three rounds use different fixed root seeds. Run cases sequentially; no local
builds, tests or other benchmarks run alongside them. Do not selectively rerun slow cases.

The complete grid contains **108 fresh JVMs** (87 plain, 21 profiled):

| Workload | Plain cases per round | Profile cases per round |
| --- | --- | --- |
| 32-D Gaussian / GPSS; 32-D positive support / Quantile; 8-D likelihood / GPSS | Each: 4000 and 16000 draws, workers 1/2/4, reference callback; plus 4000 draws, four workers, loop callback | Each: 16000 draws, four workers, reference and loop callback |
| Wide graph: 32 independent Normals and a sum observable | 1000 and 4000 draws, workers 1/4 | 4000 draws, four workers |
| Two independent jobs | Gaussian 16000 draws and likelihood 4000 draws, four workers per job, serial versus overlapping | None |

All chains discard 500 warm-up transitions. Worker order rotates between rounds;
serial/overlap and profiled callback order alternate (three rounds necessarily have a
2:1 order imbalance). Plain loop cases follow the reference grid, so callback comparisons
are exploratory and not balanced adjacent A/B evidence. Do not promote a callback or
library change based on a small timing difference here.

The graph control uses `ProposalScheme(xs*)`: an explicitly ordered joint proposal of
all 32 nodes, not the default random-node proposal. The initial draft's exact-hash check
failed with the default proposal. That exceeded the documented reproducibility guarantee:
graph/hash traversal can affect exact traces even with stable chain-index seeds. Ordered
joint proposals passed repeated one/two/four-worker checks. This controls the benchmark;
it does not fix graph-wide reproducibility, imply that joint proposals are universally
better, or establish default-MH scaling. Retain all observable draws, acceptance rates,
initialization attempts and diagnostic metadata in the graph fingerprint.

Vector callback rewrites preserve arithmetic order and use scalar loops instead of
intermediate mapped collections. They are **application changes only**, not Figaro
library gains. Check 600 numerical callback cases plus full result fingerprints across
callback variants, workers and overlapping jobs before measurement. Recheck every
warm-up and measured fingerprint across the complete study; fail closed on mismatch.

### What each measurement means

- `wallSeconds` includes complete inference, result hashing and outer job orchestration.
  It excludes discarded warm-ups, explicit GC, profile setup/dump/readback and printing.
  Vector construction/sampling/diagnostic phase fields come from `measuredRun` and exclude
  hashing. For two jobs these phase fields are **sums**, not overlapping wall time.
  Graph phase fields and evaluation counts are unavailable (`NaN` and `-1`), not zero.
- CPU, compilation time and GC milliseconds are process counter deltas over the measured
  batch. Compilation activity and slow timing together do not prove JIT causality.
- `heapBeforeBytes` and `heapRetainedBytes` follow explicit GC requests. Their flags record
  whether a collection was observed. Returned results remain strongly reachable. A
  retained-minus-before difference estimates incremental live heap only when both flags
  are true, and is not exact object accounting or a leak test.
- `heapAtReturnBytes` includes garbage not yet collected. `sumHeapPoolPeaksBytes` sums
  independently reset heap-pool peaks; those peaks need not occur simultaneously.
- `osPeakBytes` is the child JVM's **lifetime** resident-memory high-water mark, including
  startup, both warm-ups, measured work and profile processing. It is not a measured-phase
  peak, heap capacity, commit charge or DRAM traffic. Windows uses
  [PeakWorkingSetSize](https://learn.microsoft.com/en-us/windows/win32/api/psapi/ns-psapi-process_memory_counters)
  while the child remains alive; Linux uses `VmHWM`. Unsupported counters are unavailable.
- Profiles enable allocation samples (300/s), compilation, deoptimization and data-loss
  events only during the measured batch. Weighted allocation samples are estimates, not
  exact bytes or retained memory. Categories are based on observed stack frames: diagnostics,
  callback, vector sampler, other sampling code, other, missing stack. Inlining/missing
  frames prevent clean ownership attribution. Small counts are weak evidence; truncated
  stacks are reported and any reported lost bytes invalidate the recording.
- Profile setup can affect heap state and execution. Use **plain runs for timing/memory
  comparisons**, profiles for allocation hypotheses. Do not treat profiled/reference
  timing differences as measured profiler overhead.

Hardware DRAM bandwidth, NUMA effects, cache-miss rates and arbitrary shared-graph thread
safety are outside this study. Sublinear scaling alone does not diagnose bandwidth limits.
All four graph models are constructed even with one worker. This fixed-width graph
control is not a sweep of graph sizes or chain counts and cannot isolate per-model
replication cost from other temporary JVM memory.

## Findings and user guidance

Protocol/harness commit `c5a09dc8933e60449a18cebeb2d1c31500043b44` preceded the full study.
All **108 cases** and their **216 discarded warm-ups** completed without a result-hash
mismatch. The [complete aggregate dataset](resource-scaling-results.csv) includes every
case, including regressions. Runtime manifest hash:
`1fc63e9d1dd6562c6a0beea93c565b6a3dffb21791d08f9ef916a719c54f6be3`.
The Figaro jar is unchanged from the preceding production checkpoint (SHA-256
`27a0e71c35862f9a90594762cb49f6cf0c02c5cdf10ee7bcd79c2b59969a32f8`).
Machine: Ryzen 9 9950X, 16 cores / 32 logical processors, Windows, Temurin 17.0.4;
the fixed heap/stack settings above apply. These are three machine-specific JVM/seed
rounds, not confidence intervals or a cross-platform speed guarantee.

### Worker counts help, but four workers do not mean four times faster

Each speedup below is the median of three matched-round one-worker/four-worker ratios.
Times are median complete batch milliseconds, including result hashing.

| Workload / draws per chain | 1 worker | 2 workers | 4 workers | Median speedup; round range |
| --- | --- | --- | --- | --- |
| Gaussian 32-D / 4000 | 297.8 | 180.7 | 134.3 | 2.217x; 2.151–2.235 |
| Gaussian 32-D / 16000 | 1421.3 | 914.0 | 580.1 | 2.440x; 2.434–2.488 |
| Positive Quantile 32-D / 4000 | 494.7 | 356.7 | 262.7 | 1.809x; 1.743–1.944 |
| Positive Quantile 32-D / 16000 | 2123.9 | 1368.6 | 955.6 | 2.258x; 2.219–2.261 |
| Likelihood 8-D / 4000 | 129.1 | 70.3 | 48.7 | 2.650x; 2.598–2.971 |
| Likelihood 8-D / 16000 | 472.9 | 251.2 | 158.0 | 2.992x; 2.970–3.022 |
| Ordered-joint graph / 1000 | 97.9 | Not tested | 77.3 | 1.255x; 1.212–1.382 |
| Ordered-joint graph / 4000 | 272.0 | Not tested | 159.3 | 1.708x; 1.703–1.718 |

Four workers is a useful configuration to test for a single sufficiently sized vector
job on a machine with spare cores. Use one/two workers when resource sharing matters;
benchmark the actual model, and keep the four-chain statistical work unchanged. The
smaller graph control benefits much less. This is not a reason to increase chain counts
or reduce draws just to make a benchmark look faster.

### Result storage is much smaller than process peak memory

All before/after explicit GC requests were observed. Four-worker plain-run medians:

| Workload / draws | Incremental retained heap | Lifetime peak working set |
| --- | --- | --- |
| Gaussian 32-D / 4000 | 14.3 MiB | 763.7 MiB |
| Gaussian 32-D / 16000 | 56.9 MiB | 1869.1 MiB |
| Positive Quantile 32-D / 4000 | 14.4 MiB | 1125.9 MiB |
| Positive Quantile 32-D / 16000 | 56.9 MiB | 2177.5 MiB |
| Likelihood 8-D / 4000 | 4.0 MiB | 595.0 MiB |
| Likelihood 8-D / 16000 | 16.0 MiB | 792.3 MiB |
| Ordered-joint graph / 4000 | 0.46 MiB | 780.3 MiB |

The incremental retained heap is after-GC heap minus before-GC heap; it is not the total
heap column printed by the CLI. The roughly fourfold trace-storage growth is expected
when quadrupling retained draws. The graph stores only one scalar observable per draw;
its tiny retained trace does not measure the graph's live sampling footprint.

For memory planning, leave room for graph objects, temporary proposal/diagnostic buffers,
boxed values, thread stacks and JVM/collector overhead. **Do not size a container from
`maxStoredValues * 8`** or treat a 57 MiB retained trace as a 57 MiB job. The peaks include
warm-up/startup and depend on the chosen heap/collector; they are neither portable
minimum-memory requirements nor measurements at the configured storage cap.

Two overlapping four-worker Gaussian jobs gained only **1.141x** over running the same
two jobs serially (round range 1.101–1.251), while median peak working set rose from
2482.4 to 3123.3 MiB. The likelihood pair gained **1.186x** (1.068–1.231), with peaks
666.0 versus 748.6 MiB. Concurrent jobs do not double throughput here. Queue large jobs
or budget their combined workers/memory at the application level; no global scheduler
or automatic worker policy is introduced by this study.

### The material callback opportunity is workload-specific

At 4000 draws and four workers, replacing only the density reduction with a scalar loop
gave these complete-job ratios (reference time / loop time):

| Callback | Median ratio | Three-round range | Interpretation |
| --- | --- | --- | --- |
| Gaussian | 1.053x | 1.048–1.129 | Small overall gain despite faster sampling |
| Positive support | 1.566x | 1.557–1.735 | Worth testing in an application with this hot reduction |
| Likelihood | 0.960x | 0.748–1.155 | No demonstrated overall benefit; retain the adverse cases |

The positive callback's sampling-phase median ratio is 2.480x. Its 16000-draw profiles
show median total allocation sample weights of 7.05 GiB for the reference versus
2.63 GiB for the loop, with observed reference callback frames accounting for 57.51%
of reference weight. Those are **sampled allocation-pressure estimates**, not retained
heap savings. Plain callback runs are not balanced adjacent A/B pairs, so verify the
effect in the real application before adopting it.

For the positive-support exponential target specifically, the tested replacement is
equivalent to `if (x.forall(_ > 0)) -x.sum else Double.NegativeInfinity`:

```scala
def positiveLogDensity(x: Vector[Double]): Double = {
  require(x.nonEmpty)
  var total = x.head
  if (!(total > 0)) return Double.NegativeInfinity
  var i = 1
  while (i < x.length) {
    val value = x(i)
    if (!(value > 0)) return Double.NegativeInfinity
    total += value
    i += 1
  }
  -total
}
```

This is a model-specific callback, **not a substitute for another model's likelihood**.
It preserves the original reduction order; no sampling budget or statistical diagnostic
is relaxed. All tested callbacks produced identical samples and diagnostic metadata.

Across the 21 profiles, 5287 allocation events were recorded with zero reported lost
bytes. Reference Gaussian allocation weight is 20.07% observed callback, 31.30% vector
sampler and 44.22% diagnostics; reference likelihood is 10.05%, 65.35% and 24.01%.
These are inclusive observed-stack categories, not exact exclusive ownership. The graph
recordings have 271 truncated stacks out of 527 allocation events; they are not suitable
for fine-grained call-site attribution. Zero observed loop-callback weight does not prove
zero allocation, and the reduction in likelihood sample weight did not deliver a reliable
unprofiled total-time gain.

### Positive-Quantile anomaly and next decision

The earlier alternating slow-sampling state did **not** recur in this isolated workload
screen: at 4000 draws the three one-worker sampling times were 262–278 ms, two-worker
times 218–236 ms, and four-worker times 176–191 ms. One-worker measured compilation
counters were 28–32 ms and GC counters 5–15 ms. Profiles still show compilation and some
deoptimization. This does not establish the previous anomaly's cause: the earlier
mixed-workload JVM exercised a different compilation history. Keep that reproduction
gap open rather than declaring it fixed or attributing it to GC/JIT without a controlled
experiment.

**No additional production optimization is accepted from this screen.** It identifies
a useful application callback change and confirms meaningful worker scaling, but does
not show that higher worker defaults, shared-graph threading, buffer pooling or another
small diagnostic tweak would improve representative jobs safely. The vector proposal/
callback boundary remains a plausible allocation target; any primitive-buffer experiment
must preserve immutable callback inputs and exact traces and earn acceptance in a separate
matched-runtime comparison. Do not silently expose mutable reused arrays to callbacks.

Next core work is comparative acceptance and integration, with representative consumer
workloads and a second runtime/platform where available. Keep the mixed-workload anomaly,
default graph trace reproducibility and direct hardware-memory-counter gaps explicit.
Broad shared-graph safety and new sampling APIs remain separate extensions. Main is not
merged by this milestone.

## Quick start (three steps)

1. Run `sbt --server --batch "examples / Compile / runMain com.cra.figaro.example.ResourceScalingStudy check"`.
2. Prepare a pinned jar snapshot using the [existing snapshot instructions](INTERLEAVED_PERFORMANCE_AUDIT.md#reproducing-the-paired-experiment), then run:

   ```sh
   python3 -B tools/summarize_resource_scaling.py run --java /path/to/java --runtime resource-runtime --output resource-runs
   ```

3. Validate and summarize: `python3 -B tools/summarize_resource_scaling.py check resource-runs/resource-results.csv`.

Use a new output directory every time. On Windows, pass `--acl-script` when required by
workspace ownership policy. `--smoke` runs four tiny tooling cases; pass the same flag to
`check`. Smoke results are not performance evidence. Raw logs/JFR files remain local;
review metadata before sharing. Publish only the validated aggregate CSV.

## API reference

`ResourceScalingStudy.main(args: Array[String]): Unit` prints quoted CSV or throws on
invalid configuration, incomplete work, changed results or lossy profile data. Arguments:

| Position | Meaning |
| --- | --- |
| Sole argument `check` | Run numerical and reproducibility controls |
| 1–2 | Workload `gaussian32`, `positive32`, `likelihood8`, `graphWide`; callback `reference` or `loop` (graph: reference only) |
| 3–4 | Draws per chain, 4–64000; workers, 1/2/4 |
| 5–7 | Jobs `single`, `serial`, `overlap`; round 0–9; mode `plain` or `profile` |
| 8–9 | Profile only: new JFR filename (existing parent), optional ACL hook |
| Final `hold` | Runner-only handshake; retain results/process until stdin receives a byte |

Example: `ResourceScalingStudy.main(Array("positive32","reference","4000","4","single","0","plain"))`.
Use the runner, not sbt timings, for comparisons. The Python CLI has `run` (required
`--java`, `--runtime`, `--output`; optional `--acl-script`, `--smoke`) and `check` (CSV;
optional `--smoke`). Both reject partial/changed grids; `run` preserves failing logs,
stops its own child after a 300-second timeout, and does not silently retry.

## Common patterns

1. **Choose worker count:** compare plain reference runs at 1/2/4 workers, holding chains,
   draws, model and seed fixed. Compare both complete wall time and phase times; a faster
   sampling phase can be offset by diagnostics or orchestration.
2. **Assess longer traces:** compare 4000 versus 16000 draws and retained heap/OS peaks.
   `maxStoredValues` limits scalar trace count, not total heap. Graphs, temporary buffers,
   boxed values, stacks and collector headroom also consume memory.
3. **Assess simultaneous inference jobs:** compare two serial jobs with the same two jobs
   overlapping. Two four-worker jobs can run eight chain workers plus diagnostics/runtime
   threads. This experiment does not share model objects or introduce a global scheduler.

## Related and next decision

Verification: 163 modernization regressions, 600 callback numerical cases plus full
worker/overlap controls, four smoke JVMs, and 60 documentation/report-tool tests pass.
The unchanged 11321-entry public library reference and local links validate. Four
existing Scaladoc warnings remain. CI runs the lightweight study controls and validates
the complete checked-in aggregate dataset; it does not rerun 108 JVMs or impose timing
thresholds. Check the branch workflow before integration.

[Multi-chain MCMC](MULTI_CHAIN_MCMC.md), [vector samplers](VECTOR_SLICE_SAMPLING.md),
[diagnostic hotspot study](DIAGNOSTIC_HOTSPOT_STUDY.md),
[allocation profiling](VECTOR_ALLOCATION_PROFILE.md), and
[interleaved audit](INTERLEAVED_PERFORMANCE_AUDIT.md).

Implement another production optimization only if these measurements identify a material,
actionable library cost. Callback-only improvements must remain labeled separately. Full
cross-platform acceptance, required CI/artifact checks and integration remain later work;
this stage does not merge the development branch into main.
