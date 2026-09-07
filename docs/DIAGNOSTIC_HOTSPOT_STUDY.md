# Diagnostic hotspot study

## Pre-measurement protocol

Based on `2bb0755a`, branch `modernize/diagnostic-hotspot-study`. First investigate
without changing production diagnostics, sampling, callbacks, defaults or cancellation
policy. The previous audit's CI passed its new gates but failed the legacy anytime
lifecycle step; detailed log access is currently denied. Do not label that checkpoint
CI-green or weaken the lifecycle gate.

Measure the existing stable merge sorter, a study-only stable eight-pass radix candidate,
unchanged inverse-normal score/tie grouping/scatter, full rank normalization with each
sorter, and the unchanged complete scalar summary. The radix candidate uses two primitive
index arrays plus 256 counters, not a retained array of floating-point keys. Preserve
signed-zero ordering, tie stability, input immutability and independent scratch buffers.
Keep entry/exit and periodic interruption checks; do not reduce responsiveness to win.

Use deterministic continuous, tied/signed-zero, ordered, reverse and constant arrays
of 1024, 16000 and 64000 pooled values (four chains). Every stage performs at least 64000
values of work per round, retaining five warm-up and seven measured rounds. Rotate/reverse
stage order. Run three fresh sequential JVMs on the same JDK/machine, without simultaneous
builds/tests or profiling. Retain every result. Hash complete outputs outside timing,
check both candidate sort orders and rank scores exactly, and record current-thread
allocated bytes when supported. This is not a peak-memory or multi-worker measurement.

Commit this protocol and study before measuring. Use JVM-level medians and ranges,
not individual inner calls as independent replications. Stage times are overlapping
experiments and must not be added/subtracted as exclusive causal percentages.

Promotion screen: look for at least 1.5x sorting and 1.2x full-rank gains on large
continuous fixtures, with repeatability across JVMs and explicit examination of small,
ordered and tied regressions. These are engineering selection criteria, not CI timing
gates. If the candidate merits production integration, require exact regression oracles,
unchanged full-grid statistical fingerprints and balanced fresh-JVM end-to-end validation.
Seek a material end-to-end improvement (roughly 5% or more on affected workloads) before
accepting complexity; do not claim a library gain from this isolated study alone.

The example entry point and candidate are investigation code, not a new inference API.
Related: [interleaved audit](INTERLEAVED_PERFORMANCE_AUDIT.md),
[sorting checkpoint](PRIMITIVE_DIAGNOSTIC_SORTING.md), and
[allocation profiling](VECTOR_ALLOCATION_PROFILE.md).
