# Primitive FFT autocovariance

## Overview and pre-measurement protocol

Branch `modernize/primitive-fft-autocovariance`, based on `bb8be673`. Replace only
the temporary representation in `McmcDiagnostics.autocovariance`. Use the existing
Commons Math 3.6.1 in-place transform with invocation-owned real/imaginary primitive
arrays; preserve zero padding, transform normalization, arithmetic order and lag
division. Keep the full conjugate-product expression and delegate non-finite
components to the existing `Complex` operations. Do not change FFT butterflies,
ranking, ESS, seeds, kernels, work budgets, parallelism, dependencies or defaults.

This is an internal optimization, enabled automatically when Figaro is rebuilt.
Snapshot modern.10 and public signatures remain unchanged. No shared buffer pool,
cache or new user flag is introduced. Scalar diagnostic callers also use this path;
the measured workloads cover vector inference, not arbitrary graph models.

Acceptance: canonical-NaN bit comparisons against the preceding Complex-array
implementation for every lag of 116 edge/seeded arrays, covering signed zero,
constant/impulse/alternating inputs, padding boundaries, extreme scaling and overflow.
Also require an independent direct biased-autocovariance oracle, input immutability,
concurrent-call/output isolation, cancellation flags and all modernization regressions.
Public summaries still reject non-finite observations; exceptional internal tests are
compatibility checks, not a new supported public input domain.

Commit implementation and protocol before measuring. Run the unchanged unprofiled
`VectorSamplingPerformance 5 4000 500` grid, then `VectorSamplingProfile` in a separate
JVM. Use the same machine, 1 GiB initial / 6 GiB maximum heap and fixed seeds, two JVM
warm-up rounds and five measured rounds. Run no other local build/test alongside
either full study. Require all 252 non-timing records, including trace/diagnostic
fingerprints, to match `primitive-reduction-performance-results.csv`; require the
profiled grid to match the new unprofiled grid too. Retain all poor-mixing cases.

Compare unprofiled paired timings against the preceding primitive-reduction checkpoint
and sanitized JFR weights against `primitive-reduction-profile-results.csv`. Historical
and new JVMs are not interleaved A/B trials; runtime/OS noise is uncontrolled. Report
null results and regressions. Profile weights are sampling estimates, not exact allocated
bytes, retained memory, GC CPU cost or measured DRAM bandwidth. Do not use profiled
timings as the performance claim or change the benchmark after seeing results.

Related: [primitive reductions](PRIMITIVE_DIAGNOSTIC_REDUCTIONS.md),
[allocation profiling](VECTOR_ALLOCATION_PROFILE.md),
[vector benchmark protocol](VECTOR_SAMPLING_PERFORMANCE.md).
