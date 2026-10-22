# Particle-diversity milestone execution

[Research provenance](RESEARCH_REFERENCES.md#inf-14).
[Approved assessment](PARTICLE_DIVERSITY_RESEARCH.md) and
[frozen protocol](PARTICLE_DIVERSITY_PROTOCOL.md).

## Stage 1 — complete

Protocol committed before outcomes at `f0544116`. Test-only instrumentation calls
the released filter unchanged; replay across six configurations and three original
fixtures preserves all particles, weights and evidence through subsequent updates.
Two Scala diagnostics tests and two independent Python rational-control tests passed.
The initial Scala compile failed because the suite's `run` shadowed the helper;
qualifying that name fixed it. The failed log is retained, not replaced.

All 4,608 diagnostic trajectories completed (92,160 steps). In the persistent-mode
fixture, raising calibrated floor 32 to 128 at total ceiling 256 reduced final
depleted runs from 12/256 to 3/256 and sign-mass RMSE from .26343 to .20627.
Fixed auxiliary at 256 had 0/256 depleted and RMSE .13897; at 1024, .07290.
These comparisons are NOT equal-cost superiority claims. More floor work retains
more parents; weight ESS alone does not establish adequate modal precision.

The expected-distinct-parent formula is used only for fixed counts. Conditioning
on a random stopping count changes its interpretation; calibrated rows explicitly
mark this diagnostic unavailable rather than applying the fixed-count formula.

The independent late-observation reference passed both spacing/domain refinements;
maximum log-evidence change was below 3e-12 and predictive mass error below 6e-14.
Exact rational two-step unnormalized controls equal (93/500, 23/500) for every
declared move count 0/1/4/8. These validate the mathematical control, not an as-yet
unimplemented Scala move kernel. Gaussian controls use analytic recursion.

The preserved original benchmark and the new observer agree on particle estimates.
The comparison exposed a historical prose double count of calibration pilots;
[the benchmark guide](CONTINUOUS_FILTER_BENCHMARKS.md) now correctly describes its
candidate count as pilot-inclusive. Raw evidence and existing gates are unchanged.

## Stage 2 — next

Implement and test the fixed-budget scalar candidate, then calibrate/select using
only the frozen calibration seeds. Confirmation and timing repetitions follow the
protocol without modifying the released stopping estimator.

## Stage 3 — pending evidence

No production integration or new release recommendation yet. Correctness, utility,
resource and regression gates must be evaluated before a promote/no-promote decision.
