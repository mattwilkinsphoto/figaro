# Fixed-budget integration validation protocol v1

[Research provenance](RESEARCH_REFERENCES.md#inf-15).

Frozen before benchmark execution; the run metadata captures this file's SHA-256.
No threshold selection, default promotion or tuning on these outcomes. Prior
protocols, references and released estimators remain unchanged.

## Questions and scope

Validate the separate public fixed-N bootstrap kernel, including carried weights,
evidence, explicit scheduling and numerical refusal. Compare always-stratified
with ESS-triggered stratified at the same N, not with a smaller selected baseline.
This is integration validation and descriptive ablation, not a new superiority claim.

Use N=256, both LogTreeMultinomial and Stratified, crossed with Always and ESS
fractions .25, .50, .75. Policies are fixed for each run; strict previous ESS/N <
fraction triggers selection. No output resampling. Stable particle order, no sorting.
Six unchanged INF-15 fixtures/references: growth, growth-sharp, persistent-modes,
late, gaussian and switching. Reference preparation remains independent of Figaro.

Seeds 50001..50256 are disjoint from prior diversity calibration 2001..2016 and
confirmation 10001..10256, schedule calibration 30001..30016 and confirmation
40001..40256, and original continuous seeds 1..256. Three serial fresh JVMs, two
excluded warm-ups (-202,-201) per configuration/fixture. Same seeds across JVMs:
256 independent seeds, not 768. Rotate configuration order by seed.

## Gates and all-outcome reporting

1. All deterministic finite accounting, numerical, support, lifecycle and replay
   tests must pass. Independent Fraction oracle must still produce (93/500,23/500).
2. Exactly one row for every configuration/fixture/seed/step; failures and subsequent
   unavailable steps retained. No retries, replacements or success-only evidence.
   All log-tree configurations must complete every trajectory. StratifiedPrecisionLoss
   is the specified refusal boundary, counted and reported, never an accurate estimate
   or a performance success. Any other exception fails integration acceptance.
3. Scientific outputs/statuses must replay across all three JVMs, excluding timing.
4. Only fully completed configuration/fixture groups receive unconditional RMSE,
   evidence or efficiency comparisons. No conditional-success ranking for refused
   groups. For complete groups, first/middle/final unnormalized evidence, mean,
   second moment and positive mass must match independent references within
   6 estimated standard errors + 1e-4 * max(1,abs(reference moment)). This is a
   conservative regression screen, not a confidence or precision guarantee.
5. Descriptively report sign-mass RMSE integrated over reference probabilities in
   [.1,.9], final mean/second/sign RMSE, depletion outside [.01,.99] on any eligible
   step, mean resamplings, callback work and per-JVM timing. Do not require .75 or
   any method to win. Do not claim superiority or general threshold calibration.
6. Rerun the unchanged 450-check INF-12 regression, focused released-filter tests,
   documentation tests, remote CI and isolated packaged-consumer acceptance.

Broad stratified applicability remains unestablished if any group refuses. Publish
that limitation prominently in the guide and status; no silent fallback or relaxed
CDF check. If usability requires a wider numerical contract, stop for a separate
design review rather than changing this protocol after seeing results.

## Resources and evidence

At most 12 hours serial runtime, 6 GiB JVM heap and 5 GiB evidence. Preserve protocol,
source/input hashes, exact commands, versions, actual stdout/stderr, statuses and
timestamps; archive results, machine-readable summaries and supporting plots with
rerun instructions. No source snapshots or private paths go into public guides.
