# Primitive diagnostic sorting

## Overview and pre-measurement protocol

Branch `modernize/primitive-diagnostic-sorting`, based on `a144eb1a`. Replace only the
rank-order and pooled-value sorting representations in `McmcDiagnostics`. Use a stable
bottom-up merge over primitive index arrays, and JDK primitive sorting of copied value
arrays. Preserve finite Double total ordering, stable equal-key index order, signed-zero
handling, the separate numeric-equality rank tie test, normal-score arithmetic and all
diagnostic formulas. Do not change FFT, ESS, sampler kernels, seeds, work budgets,
worker counts, defaults, dependencies or toolchain. Buffers are invocation-owned.

Acceptance: compare exact sorted values/indices with prior Scala expressions on 41
edge/seeded arrays and all 1024 five-value combinations of -1/-0/+0/+1. Compare every
rank-normalized position against the old implementation on 60 four-chain fixtures,
including ties, zeros and extreme finite scales. Require input immutability, concurrent
call/output isolation, interruption flags and all existing modernization tests.

Commit implementation/protocol before measurement. Run the unchanged unprofiled
`VectorSamplingPerformance 5 4000 500` grid, then `VectorSamplingProfile` in a separate
JVM: six fixtures, two methods, workers 1/2/4, four chains, 4000 draws, 500 warm-up
transitions, two JVM warm-up rounds and five measured rounds. Use the same machine and
1 GiB initial / 6 GiB maximum heap. No other local build/test alongside either full study.
Require all 252 non-timing results/fingerprints to match `primitive-fft-performance-results.csv`,
then require the profiled grid to match the new unprofiled grid. Retain all rounds and
poor-mixing cases; do not choose workloads or expand the change after observing results.

Compare unprofiled per-round timing ratios and sanitized JFR weights against the preceding
FFT checkpoint. Separate JVMs are not interleaved A/B trials; OS/JIT/GC variation remains.
Report null results and regressions. Sample weights are not exact allocations, live memory,
collector CPU cost or DRAM bandwidth. Profiled timings do not establish speedups.

Related: [FFT checkpoint](PRIMITIVE_FFT_AUTOCOVARIANCE.md),
[allocation profile](VECTOR_ALLOCATION_PROFILE.md), [benchmark protocol](VECTOR_SAMPLING_PERFORMANCE.md).
