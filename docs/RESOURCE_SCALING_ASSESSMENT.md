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

[Multi-chain MCMC](MULTI_CHAIN_MCMC.md), [vector samplers](VECTOR_SLICE_SAMPLING.md),
[diagnostic hotspot study](DIAGNOSTIC_HOTSPOT_STUDY.md),
[allocation profiling](VECTOR_ALLOCATION_PROFILE.md), and
[interleaved audit](INTERLEAVED_PERFORMANCE_AUDIT.md).

Implement another production optimization only if these measurements identify a material,
actionable library cost. Callback-only improvements must remain labeled separately. Full
cross-platform acceptance, required CI/artifact checks and integration remain later work;
this stage does not merge the development branch into main.
