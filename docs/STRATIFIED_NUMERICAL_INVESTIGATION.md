# Stratified resampling numerical investigation

[Research provenance](RESEARCH_REFERENCES.md#inf-15).
[Original integration results](FIXED_BUDGET_STATUS.md).

## Frozen diagnostic scope

This is a bounded investigation, not a runtime repair or release acceptance.
The released stopping estimator, development fixed-budget API, original study,
fixtures, seeds and integration acceptance criteria remain unchanged. Merge and
release are on hold. No probability floors, automatic fallback or threshold tuning.

Replay all 6,144 original stratified trajectories: six existing fixtures, N=256,
Always and ESS .25/.50/.75, seeds 50001..50256. Capture the previous immutable
population before a failing advance, without any extra RNG calls. Compare every
scientific, status and work field with the original run; exclude only timing.
Capture all first-refusal vectors plus the first resampled population of completed
trajectories as descriptive controls, not an unbiased sample of all successful steps.

Classify the first failing index with separate flags for ordinary underflow,
cumulative stagnation, endpoint loss and cumulative overshoot. Preserve overlaps.
Retain exact binary64 log weights, normalized ordinary weights and CDF boundaries.
Measure lost positive mass and the discrepancy of represented CDF increments
against independently normalized exponentials of the captured log weights using
Python Decimal at 80 and 140 significant digits. Require summary discrepancies
below 1e-60 absolute between precision levels; this is a diagnostic convergence
screen, not a proof or runtime error guarantee. Record nonmonotone CDFs separately.

Evaluate compensated accumulation and high-precision-prefix-to-binary64 projection
only as numerical probes on these same vectors. Do not interpret a lower refusal
count as correctness or change the API guard. Include explicit underflow, dominant
weight, zero-weight and endpoint controls; probe inverse-CDF boundary decisions.
No end-to-end repair validation, fresh held-out confirmation or performance claim
is implied. Budget: two hours compute, 6 GiB JVM heap and 2 GiB new evidence.

## Research basis and interpretation

Murray, Lee and Jacob, [Parallel resampling in the particle filter](https://arxiv.org/html/1301.4019v3),
sections 1.3-1.4 and 2.1, explain cumulative-sum instability and the bias tradeoff of
finite Metropolis resampling. Their experiments are not evidence for the magnitude
of errors in these Figaro cases. Gerber, Chopin and Whiteley,
[Negative association, ordering and convergence](https://arxiv.org/html/1707.01845v3),
section 2.2, give the inverse-CDF definition and conditional variance comparison;
neither implies finite-precision correctness or whole-filter superiority.

The maintained [particles implementation](https://github.com/nchopin/particles/blob/master/particles/resampling.py)
was inspected for its ordinary cumulative inverse CDF and stratified coordinates,
not imported or treated as a high-precision oracle. Python's
[Decimal documentation](https://docs.python.org/3.14/library/decimal.html) specifies
exact float conversion and correctly rounded exponential evaluation. Decimal is an
existing standard-library dependency, used only in independent research tooling.

## Results and decision

The diagnostic replay matched **all 155,648 scientific/status/work rows** from the
original run exactly. All 6,144 trajectories were retained, with 5,468 first-failure
snapshots and 676 completed-trajectory control snapshots. No runtime source changed.
The 80/140-digit reference comparison passed for every snapshot; the largest
weight/prefix difference between precision levels was 1.24e-78 (rounded upward).
This tests arithmetic convergence on the stored binary64 logs, not model truth.

| First failing-index classification | Trajectories |
| --- | ---: |
| Interior cumulative stagnation | 4,612 |
| Ordinary-weight underflow | 852 |
| Cumulative overshoot above one | 4 |
| Endpoint collapse as the first classified reason | 0 |

At a first failing index, underflow takes precedence over overshoot and stagnation;
overlapping flags and later failing intervals remain in the machine-readable data.
This is attribution of whole-trajectory refusals, not a per-draw failure rate.

Across these captured vectors, the original arithmetic's largest reference mass
assigned to zero/negative-width CDF intervals was approximately **4.91e-16**. Its
largest absolute CDF error was **7.27e-15**. Thus the guard is terminating these runs
for very small local arithmetic losses, not loss of a large fraction of posterior
mass. This does not bound downstream posterior error, rule out consequential rare
histories, or justify silently dropping mass in a general-purpose API.

For diagnosis only, construction was continued past the first rejected interval.
That counterfactual full CDF was nonmonotone/out of range in 532 snapshots; the actual
filter refused before using those invalid distributions. A valid-CDF total variation
comparison was withheld for those snapshots, not computed with negative probabilities.

### Why a small summation patch is insufficient

| Arithmetic probe on the same 6,144 snapshots | Strict-guard refusals | Invalid full CDFs | Maximum absolute CDF error |
| --- | ---: | ---: | ---: |
| Original accumulation | 5,468 | 532 | 7.27e-15 |
| Compensated accumulation of existing ordinary weights | 5,425 | 521 | 2.44e-15 |
| High-precision prefixes rounded back to binary64 | 5,441 | 0 | 5.56e-17 |

These are vector-level arithmetic probes, **not repaired end-to-end filter runs**.
The compensated probe does not repair normalization error or the final endpoint.
Even accurately rounded prefixes cannot give every tiny positive weight a distinct
binary64 interval. Requiring that property makes the contract impractical here.
Forced boundary probes find selection disagreements even for accurately rounded
prefixes; their deliberately adversarial counts are not sampling probabilities.
Neither completing more vectors nor passing the existing refusal tests is sufficient
for promotion. No probabilities were clipped, default changed or guard removed.

### Recommended next bounded milestone

Keep merge/release on hold. Investigate **decision-focused inverse-CDF comparisons**:
preserve the within-stratum random coordinate separately instead of first collapsing
it into a global binary64 point; compute conservative arithmetic error bounds;
accept an ancestor only when its membership is unambiguous; refine ambiguous
comparisons at bounded higher precision using the original log weights. If the
refinement budget cannot resolve the choice, fail explicitly with diagnostics.
Refinement of the same draw is not a retry with a new seed or a change of method.

This is a design recommendation, not an implemented or certified algorithm. It
needs an explicit precision/resource contract and treatment of exponentials and
random-bit resolution. Python Decimal is a diagnostic reference, not an automatically
suitable JVM runtime dependency. A higher-precision CDF alone is not sufficient.

Before promotion, require adversarial boundary/underflow/zero-support tests,
independent high-precision decisions, conditional offspring/evidence checks and
all-outcome whole-filter comparisons under a new frozen protocol with fresh held-out
seeds. Establish useful completion in the advertised domain and include refinement
costs; do not tune acceptance on this diagnostic set or transfer a finite-precision
result into an exact unbiasedness theorem. The released stopping estimator stays
unchanged. If the stronger comparison design is not defensible within bounded work,
keep stratified research-only and review a log-tree-only public API separately.

### Executed checks and limitations

Four independent diagnostic tests and all 16 unchanged fixed-budget accounting,
numerical and lifecycle tests passed. No production repair, new end-to-end candidate,
fresh-seed utility test, remote CI run, merge or publication was performed here.
The original integration protocol and old evidence remain unchanged. All sources,
exact binary64 snapshots, logs, input hashes and the diagnostic plot are preserved
in a separate reproducibility archive. Python 3.14.6/libmpdec 4.0.0 supplied the
independent arithmetic; the Scala replay used Java 17, Scala 3.9.0 and sbt 2.0.8.
