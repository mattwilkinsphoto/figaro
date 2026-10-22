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

## Stage 2 — complete

Test-only implementation committed at `07f21637`; six Scala tests and four Python
tests passed. An initial compile caught invalid numeric-literal syntax; that failed
log is retained. No runtime source changed. Calibration completed 1,040 trajectories
across five fixtures, 13 configurations and 16 seeds, without failed outcomes.

The predeclared cost-weighted error score selects **stratified resampling only at
N=64**, with **ordinary bootstrap N=64** as its primary baseline. No MCMC candidate
wins this selection; the no-extra-resampling baseline scores better than all
experimental candidates. This is a negative calibration result, not an invitation
to retune against the held-out seeds. Confirmation repeats the selected option,
the N=64 no-extra-resampling control and bootstrap N=64/256/1024/4096 in three fresh
JVMs, using the frozen 256 held-out seeds. Replications measure timing/replay, not
additional independent statistical samples.

Held-out confirmation completed 7,680 trajectories per JVM, 23,040 in total,
with 552,960 step records and no failed outcomes. Scientific outputs were identical
across the three JVMs. All 360 unnormalized control checks and all six normalized
Gaussian/growth regression checks passed. These are the frozen statistical screens,
not proofs that every normalized estimator is unbiased.

## Stage 3 — complete: do not promote

The selected stratified-only option **fails the predeclared utility gates** against
ordinary bootstrap at the same N=64 and counted callback work:

| Held-out fixture | Final sign-mass RMSE ratio (candidate/baseline) | Trajectories depleted at any eligible step | Decision |
| --- | --- | --- | --- |
| Persistent modes | 1.06094; paired 95% interval [0.99639, 1.12804] | 105/256 versus 79/256 | Fails accuracy, depletion and all three timing comparisons |
| Late disambiguation | 4.68e-9; boundary-sensitive metric, see below | 114/256 versus 96/256 | Fails depletion despite tiny final sign-mass error |

The family-adjusted one-sided upper RMSE ratio for persistent modes is 1.12804,
not below one; the required point ratio was at most .8. Exact binomial upper
depletion bounds also worsen: .46326 versus .35953 for persistent modes, and
.49864 versus .42762 for late disambiguation. Stage 1's depletion counts describe
the final step; these Stage 2 counts use the frozen worst-eligible-step definition.
They are not interchangeable statistics.

The tiny late-disambiguation ratio is **not evidence of general mode recovery**.
Its final reference sign probability is near one. An equal-weight resampled
population can give exactly one while a weighted population retains a tiny
negative-region weight. This boundary-sensitive final metric does not measure
earlier mode retention, which was worse, or recovery of discarded histories.

No MCMC candidate was selected in calibration: the full predictive-mixture target
requires quadratic density work, which did not earn its cost in this experiment.
The held-out comparison therefore evaluates the selected resampling-only option,
not a confirmatory test of every MCMC candidate. This is a bounded negative result,
not a claim that resample-move methods generally fail.

In particular, the tested schedule adds output resampling and still selects
ancestors at the next propagation step. Even equal-weight outputs undergo that
next selection. Its extra sampling noise is a material limitation; a schedule
that propagates each resampled parent once was **not tested**. Investigating that
would require a new protocol and held-out seeds, not retuning this result.

### Regression and delivery

The unchanged 7.2 continuous harness completed 7,680 trajectories / 153,600 steps;
all **450 original checks passed**, with none blocked. All **59 focused Scala
tests** passed, including the new diagnostics and experimental suites. The five
independent Python oracle/reporting tests also passed. Failed initial compile
logs remain part of the evidence, alongside the successful reruns.

Production filtering source, stopping estimator, original benchmark, defaults and
library version remain unchanged. This milestone adds test-only research code,
controls, CI coverage and documentation; it does **not** add a public rejuvenation
API or warrant a consumer release. Figaro 7.2.0 remains the adoption baseline.

The reporting audit added descriptive posterior-variance, relative-evidence and
log-evidence errors from the preserved records, without changing any gates.
For persistent modes at the final step, candidate/baseline RMSE was 11.7394/10.7551
for variance, .71955/.61384 for relative evidence, and .73573/.66967 for log evidence.
Relative evidence error is `exp(logZhat - logZreference) - 1`; it must not be
confused with `logZhat - logZreference`. Variance reconstructed as second moment
minus squared mean can suffer cancellation: 24 of the 23,040 selected-step
estimates were slightly negative. They remain recorded, not silently clamped.
This supplemental reporting is descriptive, not an additional promotion test.

The resource-only repeat completed successfully. Maximum observed peak working
sets were 754,429,952 bytes for the forked experiment and 954,249,216 bytes for its
sbt launcher (sampled every two seconds). These are process-level observations,
not per-method memory measurements or certified final peaks. Setup inside each
step is timed; JVM startup, warm-up, output logging and offline calibration are
not amortized into the accuracy/time curves. Calibration and warm-up commands and
complete process elapsed times are retained separately. No resource limit was hit.

Calibration and confirmation use disjoint seeds on the same predeclared fixtures,
not a second set of unseen model families. The result cannot establish cross-family
generalization. This limits any positive claim; it does not rescue the failed
promotion gates or justify expanding the approved experiment after seeing results.

Retain the validated existing proposal/auxiliary capabilities and assess higher
particle floors against model-specific accuracy and work. Neither a high weight
ESS nor many distinct current states proves adequate mode or path coverage.
Further rejuvenation work is optional follow-up, not an adoption prerequisite.

### Reproduction

Protocol: `f0544116`; Stage 1 implementation: `31427545`; experiment and analysis
gates: `07f21637`; calibration choice recorded before held-out work: `ecd17d5f`.
The plotting-only addition does not change selection or acceptance rules.
The experiment uses the five synthetic fixtures and seeds in the
[protocol](PARTICLE_DIVERSITY_PROTOCOL.md). Commands, versions, source/input hashes,
actual logs (including errors), per-step TSVs, reference/decision JSON and plots
are retained in the accompanying private reproducibility archive. A fourth run
is resource-only and is excluded from statistical replication and timing gates.
CI completion is recorded separately in the delivery handoff and evidence archive;
local success is not a substitute for remote CI.
