# Primitive diagnostic reductions

## Protocol before measurement

Branch `modernize/primitive-diagnostic-reductions`, based on `0462f1b0`. Replace only
the iterator/map reductions in `McmcDiagnostics.average` and `variance` with primitive
array loops. Preserve the first-value accumulator, left-to-right summation, shifted
mean, two-pass sample variance and periodic interruption checks. Do not change FFT,
ranking, covariance/ESS formulas, kernel/seed/work budgets, worker counts, defaults,
dependencies or toolchain. The [allocation profile](VECTOR_ALLOCATION_PROFILE.md)
identified these two sites as about 15% of allocation weight and sampled Java execution.

Acceptance: compare both helpers with the prior iterator expressions over signed zero,
ties, cancellation-sensitive sums, subnormal/extreme inputs and fixed-seed arrays around
loop checkpoint boundaries. Require all existing scalar-diagnostic oracles and lifecycle
tests. Validate all 252 full-grid non-timing benchmark fields, including complete
trace/diagnostic fingerprints, against the preceding checkpoint.

Run the unchanged unprofiled `VectorSamplingPerformance 5 4000 500` grid first, then
the unchanged `VectorSamplingProfile` grid in a separate JVM, on the same machine with
1 GiB initial / 6 GiB maximum heap. No other local build/test runs alongside either
measurement. Keep every round and all poor-mixing cases. Compare unprofiled timings
with `parallel-vector-diagnostics-results.csv` and allocation classes/categories with
`vector-allocation-profile-results.csv`. Profiled timings are not a speedup estimate.

Historical-versus-new JVM runs are not interleaved A/B trials. Small timings and sampled
allocation-weight differences may reflect runtime/OS variability. A reduction in boxing
does not establish a proportional wall-time gain, retained-heap reduction or hardware
bandwidth improvement. Report null results and regressions rather than changing inputs
or expanding the optimization after seeing the data.
