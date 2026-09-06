# Vector allocation and GC investigation

Follow-up implementation: [primitive mean/variance reductions](PRIMITIVE_DIAGNOSTIC_REDUCTIONS.md).
This document and its checked data remain the pre-optimization profiling baseline.

## Overview

This example-only tool identifies where Figaro's vector benchmark allocates temporary
objects and where sampled Java execution occurs. It exists to choose the next optimization
using evidence, not to replace the sampler or automatically tune JVM settings. Ordinary
sampling remains unprofiled. Raw recordings are ignored by Git; do not publish them
without reviewing their contents even though the enabled event set is deliberately narrow.

## Fixed protocol, before measurement

Branch `modernize/vector-allocation-profile`, based on `0e2456d8`. No production library,
kernel, estimator, worker policy, default, dependency or toolchain changes are planned.
Use the unchanged full `VectorSamplingPerformance` grid: six fixtures, two methods,
workers 1/2/4, four chains, 4000 draws, 500 warm-up transitions, two JVM warm-up rounds,
five measured rounds. Validate all 252 non-timing outputs against the parallel diagnostic
checkpoint, including every trace/diagnostic fingerprint. Retain all failures and poor
mixing cases. Do not optimize source or choose workloads after inspecting the profile.

The new example records only the benchmark invocation, excluding sbt startup/compilation
and the final profile aggregation. It includes JVM warm-up rounds, fingerprinting,
console output and JVM background activity. It enables JDK 17 allocation samples at
300/s, Java execution samples at 10 ms, GC events, GC heap summaries and data-loss events.
No environment/system-property, command-line, file or network events are requested.
Use the same 1 GiB initial / 6 GiB maximum heap and machine as the preceding study;
do not run another local build or benchmark concurrently.

Aggregate full stacks into diagnostics, sampler, other and unknown categories, with
nearest Figaro diagnostic/sampler method and allocation class or execution leaf. Keep
all categories, not only interesting hotspots. Allocation sample weights estimate
allocation pressure, not retained heap or exact object counts. Execution sample shares
are not wall-time shares. The recorded GC sum-of-pauses excludes concurrent GC work;
observed GC heap snapshots are neither peak process RSS nor a leak test.

**Hardware DRAM bandwidth is not measured.** No hardware-counter profiler was found in
the available command path. Allocation bytes/s, CPU samples and sublinear worker scaling
cannot establish bandwidth saturation. Hardware counter/NUMA/cache analysis requires
a separate supported profiler; no administrative changes or drivers are authorized here.

Raw JFR files stay local because recordings can carry identifying metadata. Publish only
sanitized aggregate CSV and the complete benchmark CSV. A profile with data-loss events,
missing allocation/execution events or insufficient full-grid records must not support
a completed finding. Profiled timing is advisory; use unprofiled data for speed claims.

Protocol/harness commit `3b267dfc` precedes the full recording. The completed study below
does not change its workload or discard inconvenient results.

## Findings and next decision

On 6 September 2026, all **252 runs completed** with every non-timing field identical to
the parallel-diagnostic checkpoint, including warm-up rows and trace/diagnostic hashes.
The [complete benchmark CSV](vector-profile-benchmark-results.csv) and
[all profile aggregate rows](vector-allocation-profile-results.csv) are retained.
The recording reported zero lost bytes, 30117 allocation samples and 5857 Java execution
samples across an event span of 105.226 seconds. Hardware/JVM context: AMD Ryzen 9 9950X,
16 cores / 32 logical processors, Windows 11 Pro, Temurin 17.0.4, Scala 3.9.0, sbt 2.0.8,
1 GiB initial / 6 GiB maximum heap. No other local build/test ran alongside this recording.

| Stack category | Allocation sample count | Share of allocation weight | Java execution samples | Share of Java samples |
| --- | --- | --- | --- | --- |
| Diagnostics | 13385 | 39.25% | 4925 | 84.09% |
| Sampler, including model callbacks | 16067 | 60.22% | 609 | 10.40% |
| Other, including benchmark/runtime overhead | 636 | 0.53% | 323 | 5.51% |
| Missing stacks | 29 | below 0.01% | 0 | 0% |

The total sample weight is 598215223256 bytes (about 598 GB decimal), **not** peak heap,
exact allocated-byte accounting or DRAM traffic. Diagnostics contribute 234789561280
weighted bytes. Within diagnostic allocations, boxed `java.lang.Double` contributes
126306148072 weighted bytes (about 54%), `Complex` objects 54675777736 (about 23%),
primitive double arrays 33469398272 (about 14%), and `Complex[]` arrays 8262571848 (about
4%). These classes identify transient representation costs worth testing, not leaks.

`McmcDiagnostics.average` and `variance` nearest sites together account for about 15%
of all allocation weight and 15% of all sampled Java execution. Source inspection shows
iterator/map-based reductions over primitive arrays. FFT/autocovariance and ranking
are also material: sampled diagnostic leaves include `transformInPlace` (569 samples),
`createComplexArray` (499), `TimSort.mergeLo` (548), `mergeHi` (495), `binarySort` (368)
and `Double.valueOf` (437). Inclusive sites and leaves are different views of the same
events; do not add their percentages as independent costs.

The largest allocation site overall is the sampler's `evaluate` callback boundary,
47.44% of total sample weight. This includes the benchmark's density callbacks and their
collection operations. It is **not** evidence that the wrapper itself allocates all of
that memory, and optimizing diagnostic reductions cannot remove model callback costs.

GC recorded **586 collections**, **1.674 seconds of summed pauses** (about 1.59% of
event span), and a longest pause of **17.905 ms**. Maximum observed heap use at GC
snapshots was 1253699072 bytes (about 1.17 GiB); maximum observed after-GC use was
75278048 bytes (about 71.8 MiB). This supports investigating temporary allocation, but
does not establish absence of leaks in other workloads or quantify concurrent collector
cost. Pause time alone is too small to explain the dominant diagnostic execution here.

### Recommended next milestone: primitive diagnostic reductions

First replace allocation-heavy mean/variance and related scalar reductions with
primitive-array loops, keeping the existing numerical summation behavior, traversal
order, scaling, tie handling, warnings and interruption checks. This is a narrower first
experiment than replacing the FFT or rank estimator. Acceptance requires the scalar
diagnostic oracles, lifecycle checks, exact full-grid fingerprints, a repeated profile
and a separate unprofiled timing comparison. No speedup is promised before that test.

Then consider reducing FFT `Complex` conversions/temporary arrays, followed by ranking
representation costs if they still dominate. Benchmark-density allocation is a separate
candidate and must not be confused with a general library improvement. Do not increase
worker counts, loosen diagnostics, change stopping rules or select a new GC merely from
these figures. **Memory-bandwidth saturation remains unproven.** Direct hardware-counter
measurement is still an external tooling gap, not a result inferred from allocation rate.

Profiled four-worker timings range from small apparent gains to regressions against the
previous unprofiled run; they are retained but are not an optimization or overhead
estimate from controlled A/B trials. The earlier one-worker positive-quantile median
of 1188.04 ms is 896.68 ms in this recording without any production change. This reinforces
the need for repeated/interleaved unprofiled measurements before attributing that anomaly.
The known wrong-mode/mixing outcomes remain exactly unchanged.

Reference semantics: [JDK 17 JFR troubleshooting](https://docs.oracle.com/en/java/javase/17/troubleshoot/troubleshoot-performance-issues-using-jfr.html)
and [OpenJDK 17 event metadata](https://github.com/openjdk/jdk17u/blob/master/src/hotspot/share/jfr/metadata/metadata.xml).

## Quick start (three steps)

1. Run in the Figaro checkout with JDK 17 and a **new** output filename:

   ```sh
   sbt --server --batch "examples / Compile / runMain com.cra.figaro.example.VectorSamplingProfile profile.jfr 5 4000 500" > profile.log
   ```

2. Inspect allocation and GC findings:

   ```sh
   python3 -B tools/summarize_vector_profile.py profile.log
   ```

3. Verify that profiling preserved every benchmark result:

   ```sh
   python3 -B tools/summarize_vector_performance.py profile.log --repetitions 5 --baseline docs/parallel-vector-diagnostics-results.csv
   ```

Do not run compilation/tests alongside the measurement. A one-round `1 100 20` smoke
run checks tooling but is too short for optimization decisions.

## API reference

`VectorSamplingProfile.main(args: Array[String]): Unit` is the only public Scala entry
point. It invokes `VectorSamplingPerformance.main` unchanged, records the invocation,
stops/dumps/closes the recording, then reads it and prints profile aggregates. Arguments:

| Position | Meaning | Default / allowed values |
| --- | --- | --- |
| 1 | New raw JFR output file | Required; parent directory must exist; never overwrites an existing file |
| 2 | Measured repetitions | 5; 1-100 |
| 3 | Retained draws per chain | 4000; 4-100000 |
| 4 | Discarded sampler warm-up | 500; 0-100000 |
| 5 | Optional Windows ACL hook | PowerShell script run via `pwsh.exe -NoProfile -File SCRIPT -Paths ABSOLUTE_OUTPUT` immediately after file creation; requires PowerShell 7 on PATH |

Example: `VectorSamplingProfile.main(Array("profile.jfr", "1", "100", "20"))`.
Invalid arguments, unavailable recording support, existing output, I/O or hook failures
throw. An existing file remains untouched; a failed new run may leave an empty or partial
local recording for inspection. Benchmark runtime failures remain explicit CSV rows;
interruption aborts. A successful process exit alone is not evidence all cases completed.

The Python CLI accepts `LOG_OR_CSV`, optional `--output NEW_CSV`, and optional
`--acl-script SCRIPT` for immediate Windows access verification on a saved aggregate file.
It prints all-category totals and the 12 highest-weight nearest sites; all class/site
rows remain in the saved CSV. Missing metrics/samples, duplicate or malformed rows,
non-identifier class/site data, inconsistent counters or nonzero reported data loss fail
validation. It validates profile structure, not the separate benchmark grid or statistical
reliability; run the benchmark validator too.

Profile CSV schema: `vectorProfile,kind,group,detail,site,count,value`.

- `allocation`: group is `diagnostics`, `sampling`, `other` or `unknown`; detail is the
  allocated JVM class, site is the nearest diagnostic/sampler frame (otherwise leaf),
  count is sampled events, and value is the sum of JFR allocation-sample weights in bytes.
- `execution`: same groups; detail is the sampled Java leaf method, site is the nearest
  diagnostic/sampler frame; count and value both equal the number of execution samples.
- `metric`: group names the metric, detail/site are empty and count is 1. Metrics are
  `eventSpanSeconds`, `gcCount`, `gcPauseSeconds`, `longestGcPauseSeconds`,
  `heapSummaryCount`, `maxObservedHeapBytes`, `maxObservedAfterGcHeapBytes`, `lostBytes`.
  Event span is earliest-to-latest event, not precisely the recording boundary or sum
  of benchmark wall times. Heap byte maxima refer only to observed GC snapshots.

JVM names use dots in place of slashes; hidden-class address suffixes are removed.
Frame line numbers refer to the profiled source revision. Aggregates combine all fixtures,
worker counts and warm-up/measured rounds: they do not estimate per-fixture or per-worker
allocation differences. Nearest-site attribution is inclusive of callees; sampler
`evaluate` therefore includes the benchmark's density callback and not just library code.

## Three common patterns

### 1. Find a hot allocation site before rewriting code

Use the quick start. Compare the diagnostic/sampling shares and inspect the corresponding
source. High allocation weight identifies a candidate, not a proven speedup or a leak.
Use allocation classes and sampled execution leaves together to distinguish boxed scalar
collection operations from FFT work and model-density callbacks.

### 2. Keep portable findings without sharing machine details

```sh
python3 -B tools/summarize_vector_profile.py profile.log --output profile-summary.csv
python3 -B tools/summarize_vector_performance.py profile.log --repetitions 5 --output profile-benchmark.csv
```

Both outputs require new filenames. Review them before sharing. Keep `.jfr` and raw
console logs local; the latter may include build paths and the user-supplied ACL hook.

### 3. Evaluate a later optimization honestly

Repeat the same profile to check whether allocation pressure moved, and separately
repeat the unprofiled [fixed-trace benchmark](VECTOR_SAMPLING_PERFORMANCE.md) to establish
timing changes. Require exact non-timing equality for an allocation-only rewrite. Keep
every round, including regressions and poor-mixing targets. Do not equate a lower sampled
allocation weight with equivalent reductions in retained heap, GC pauses or runtime.

## Limitations and related modules

Java execution sampling omits native execution and is not a CPU-cycle counter. The
recording includes JIT/runtime/benchmark overhead in `other`; truncated or missing
stacks can lose attribution. Sampling weights have variance; short recordings and a
single combined workload do not justify fine rankings. GC pauses are a subset of GC
cost; concurrent collector CPU and memory traffic are not quantified here. No retained
object graph, process RSS peak, cache-miss or DRAM/NUMA counters are measured.

See [parallel diagnostic implementation](PARALLEL_VECTOR_DIAGNOSTICS.md),
[vector sampler](VECTOR_SLICE_SAMPLING.md), [multi-chain vector runner](MULTI_CHAIN_VECTOR_SAMPLING.md),
and [diagnostic reliability](MCMC_RELIABILITY.md). The existing wrong-mode counterexamples
remain unchanged; faster calculations cannot certify convergence.

## Verification checkpoint

Compilation and all 150 modernization regressions pass. All 29 report-tool tests and
12 documentation-tool tests pass, including four new profile validation groups covering
complete records, missing/duplicate data, malformed/unsanitized values, data loss and
inconsistent GC counters. The complete 108-run profile smoke grid and all 252 full-run
fingerprints validate. Reusing an existing JFR filename throws `FileAlreadyExistsException`;
the existing recording's SHA-256 remains unchanged. The raw full recording's `jfr summary`
independently confirms the reported event counts and zero environment/system-property/JVM
argument events. Only sanitized CSV is checked into Git.

The public library reference still contains 11321 methods; generated-reference freshness
and local links pass. CI adds the profiler smoke invocation and both checked-dataset
validators without any performance threshold or weakened existing gates. The preceding
parallel-diagnostics checkpoint's [CI is green](https://github.com/mattwilkinsphoto/figaro/actions/runs/34055899573).
These local results do not claim a complete historical test-suite run or fresh artifact
publication/reproducibility checks for this profiling-only milestone.
