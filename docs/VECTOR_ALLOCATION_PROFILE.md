# Vector allocation and GC investigation

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

Reference semantics: [JDK 17 JFR troubleshooting](https://docs.oracle.com/en/java/javase/17/troubleshoot/troubleshoot-performance-issues-using-jfr.html)
and [OpenJDK 17 event metadata](https://github.com/openjdk/jdk17u/blob/master/src/hotspot/share/jfr/metadata/metadata.xml).
