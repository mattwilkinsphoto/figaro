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

## Production candidate protocol (after isolated screen)

The three-JVM isolated screen completed all 3240 records. On continuous inputs, radix
sorting gained 4.653x at 16000 values and 5.053x at 64000; full ranks gained 2.340x and
2.552x. Small and monotone cases regressed, so do not replace every sort unconditionally.
The production candidate retains the unchanged merge implementation below 16000 pooled
values and for monotone/constant input. A cancellation-checked monotonicity scan exits
as soon as both directions are disproved. Only larger nonmonotone input uses stable radix
sorting. No normal-score, tie, FFT, estimator, sampling or callback expression changes.

The allocation screen observed two index arrays plus approximately 1040 extra bytes
for the radix counters. This is not a peak-memory guarantee. Guard scanning itself has
a cost on ordered inputs; the end-to-end study must not hide that cost.

Commit the production implementation, tests and this extension before its measurements.
Use four balanced fresh-JVM pairs against the study's unchanged-library runtime, the same
benchmark/dependency jars, 4000 draws, 500 warm-up, all six fixtures, both methods and
1/2/4 workers. Retain all 2016 records and require every non-timing field/fingerprint to
match the sorting checkpoint. Do not run builds/tests concurrently with measurement.

The isolated stage labels `mergeSort`/`mergeRank` refer to the production code at
`eda9ebba`, before this candidate. Reproduce that historical timing study using its pinned
runtime/revision, not the changed library; otherwise those labels no longer identify a
merge-only baseline. The `check` mode remains a valid candidate correctness smoke test.

Related: [interleaved audit](INTERLEAVED_PERFORMANCE_AUDIT.md),
[sorting checkpoint](PRIMITIVE_DIAGNOSTIC_SORTING.md), and
[allocation profiling](VECTOR_ALLOCATION_PROFILE.md).
