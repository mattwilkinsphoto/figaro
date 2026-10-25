# Whole-fit GVM convergence and model-complexity results

Research provenance: [GVM-07](RESEARCH_REFERENCES.md#gvm-07).

**The bounded study is complete; no promotion.** Safeguarded squared extrapolation
improves two-component fitting, but does not make higher-dimensional mixtures
reliably fit within the declared budget. Numerical correctness and all retained-
score noninferiority checks pass. Per-cell reliability and the required overall
completion improvement fail. Figaro's released stopping estimator, defaults,
scalar fitter, runtime dependencies and release version remain unchanged.

The [protocol](GVM_WHOLEFIT_STUDY.md) and
[configuration/source hashes](../tools/gvm_wholefit_protocol_v1.json) were committed
and pushed as `f504b8bd` before accessing confirmation seeds. The
[complete numerical summary](../tools/gvm_wholefit_summary_v1.json) and
[descriptive breakdowns](../tools/gvm_wholefit_diagnostics_v1.json) preserve the
outcomes. No held-out retuning, relaxed threshold, omitted failure or enlarged
budget was used. The earlier [gradient study](GVM_GRADIENT_STATUS.md) is unchanged.

## What was tested

Both research fitters use the same analytic angular solver, parameter bounds and
three initialization partitions. One uses ordinary generalized EM; the other
uses an independently written, bounded adaptation of SQUAREM with projection,
circular differences, likelihood-decrease rejection and stabilization. This is
not the CRAN implementation and does not inherit its convergence guarantees.

The new `Stationary` qualification checks the complete mean marginal-likelihood
gradient: weights, means, covariance, angular offset, phase coefficients and
concentration. Responsibilities are refreshed from the retained model. The
coordinate-dependent 1e-4 threshold is not interchangeable with the earlier
weighted-resultant threshold or likelihood-stabilization criterion. This is a
**research optimizer diagnostic**, not a change to inference precision stopping.

There are 80 independent synthetic datasets: two or three linear coordinates
plus one angle, five generating families, eight fresh seeds. Each contains 2,000
training, 2,000 validation and 20,000 test observations; whitening is training-
only. K=1,2,3 and two methods give 480 primary fits, plus 80 wrapped-GMM controls.
All 560 attempts retained finite models. No timeouts or numerical refusals were
hidden; every unsuccessful primary fit reported `WorkBudgetExhausted`.

Each primary fit shares 20M logical work units across its three starts, with at
most 200 EM maps per start and 120 seconds per fit. This includes phase, scoring,
moment and full-gradient work, unlike the older phase-only budget. Work units are
not FLOPs. A global budget failure overrides the status of a retained successful
start; that distinction matters below.

## Frozen gate results

| Gate | Observed result | Decision |
| --- | --- | --- |
| Independent numerical/inventory validation | All checks pass | Pass |
| At least 95% stationary completion in every dimension/family/K cell | 17 of 30 candidate cells meet the requirement | Fail |
| At least 20 percentage points more qualified multicomponent fits | 49/160 baseline versus 71/160 candidate: +13.75 points | Fail |
| All 30 paired test-score lower bounds above -0.02 nats/point | 30/30 pass | Pass |

The score bounds use the predeclared one-sided Bonferroni adjustment across 30
comparisons and eight independent training seeds per comparison. They assess
retained finite-budget models, including unqualified fits. They establish neither
global optima nor score superiority. Eight of eight per cell is required by the
95% empirical threshold; it is not a population reliability confidence bound.

| Fitted components | Unaccelerated stationary fits | Accelerated stationary fits | Other baseline / candidate outcomes |
| --- | ---: | ---: | --- |
| K=1 | 80/80 | 80/80 | None |
| K=2 | 36/80 | 56/80 | 44 / 24 work-budget failures |
| K=3 | 13/80 | 15/80 | 67 / 65 work-budget failures |

Acceleration is useful evidence for K=2, not a complete solution for K=3.
No public fitter, automatic model selector or production promotion follows.

## Complexity and separation change the picture

All 15 successful accelerated K=3 fits come from the **genuinely three-component,
separated GVM** truth: 15/16 versus 13/16 for unaccelerated EM. This is strong
counterevidence to the idea that three-component GVMs are inherently unfit to
optimize. It still misses the frozen per-cell reliability requirement.

Neither method completes any K=3 fit in the other four families: deliberately
overfitted one-component curved or two-component seam GVMs, genuinely
three-component overlapping GVMs, or three-component wrapped-Gaussian controls.
The latter are not an exactly specified GVM truth. Overfitting is not the only
problem: the overlapping, correctly specified three-component GVM also struggles.
These conclusions concern this generator/budget, not every possible mixture.

Validation-only selection among qualified candidates chooses:

| Generating family | Accelerated selected K across 16 datasets |
| --- | --- |
| Curved GVM, true K=1 | K=1 in all 16 |
| Seam GVM, true K=2 | K=2 in all 16 |
| Separated GVM, true K=3 | K=3 in 15; K=2 in 1 |
| Overlapping GVM, true K=3 | K=2 in all 16 |
| Wrapped Gaussian mixture, true K=3 | K=1 in 8; K=2 in 8 |

Every dataset has a qualified K=1 model, so selection is available everywhere.
But qualification filters out many larger models: selecting K=2 does **not** prove
that the underlying phenomenon has two components. In particular, the overlap
result cannot separate predictive parsimony from inability to qualify K=3.
Test data were not used to choose K. Median total search work across all three K
fits is about 29.2M units accelerated versus 40.1M unaccelerated; it is not the
cost of fitting only the selected model.

## Retained stationarity and restart-search completion differ

Among accelerated multicomponent fits, 100/160 retained models meet the full
gradient threshold, but only 71/160 complete the declared workflow. The other
29 contain a selected start recorded as `Stationary`, followed by global budget
exhaustion during another restart. Baseline counts are 67/160 retained stationary
models versus 49/160 completed workflows. These descriptive counts are useful
counterevidence to dismissing acceleration, but do not substitute for the frozen
completion gate or authorize relabeling failures.

Of 80 accelerated K=3 models, 44 still exceed the gradient threshold. Their
largest residual is the weight block in 39 cases, angular offset in two,
covariance in two and phase coefficients in one. Thus another faster inner phase
solver alone is unlikely to resolve all remaining work. This is a diagnosis of
the recorded residuals, not proof that a particular alternative will succeed.

The step cap is also relevant: it is reached in 3,245 of 4,156 K=3 extrapolation
attempts. There are 4,024 accepted and 111 rejected K=3 attempts; 21 attempts end
at a global work limit before an acceptance/rejection decision. These are retained
outcomes, not a reason to increase the cap after looking at held-out results.

## Costs, Gaussian controls and independent evidence

Median K=2 total work falls from about 20.0M to 9.10M logical units; median K=3
work remains 20.0M for both. Median core fit times are about 0.93 versus 0.47 seconds
for K=2, and 0.88 versus 0.93 seconds for K=3 (baseline then candidate). These are
descriptive finite-budget timings, not time-to-convergence speedups: many fits
terminate unsuccessfully, and brief validation/tooling commands ran concurrently
with part of the grid. The full 560-fit run took about 622 seconds.

Wrapped-GMM K=4 controls met their older likelihood-stabilization criterion in
65/80 cases and reached iteration limits in 15. Their different work accounting
and criterion preclude calling them cost-matched, whole-fit-qualified competitors.
No universal GVM-over-GMM performance or representation claim is made.

Independent checks actually executed:

- Nine new mathematical/failure control tests, including every physical derivative,
  covariance/simplex boundaries, zero concentration, circular seams, the SQUAREM
  second difference, rejected trials and explicit time/work/map limits.
- Twenty development fits on separate seeds; all-model finite-difference and
  high-precision density checks passed. Eight unchanged GVM controls and five
  unchanged gradient-control groups also passed.
- All 80 input hashes, frozen source hashes, full/unique inventory with two negative
  controls, parameter envelopes, retained scores and monotone accepted traces.
- 1,280 independent 60-digit density evaluations, including generating laws:
  maximum absolute log-density error below 5.4e-15 (limit 1e-8).
- All physical-coordinate objective differences for 60 fitted models: maximum
  gradient discrepancy below 3.6e-9 (limit 2e-6). Maximum recomputed score
  discrepancy below 3.7e-12. No models were omitted.

Independent numerical agreement is not independent proof of statistical utility,
global optimality, arbitrary dimension support or representative-data readiness.

## Decision and next bounded question

Keep the candidate test-only and retain all no-promotion results. If further
fitting research is approved, start with the two observed bottlenecks:

1. Separate retained-solution qualification from completion of the restart search
   in reporting, then investigate bounded restart allocation. Preserve this
   study's original combined gate; do not retrospectively declare its 29 cases
   successful.
2. Research constrained mixture-weight optimization and initialization for
   overlapping components before building another optimizer variant. Compare
   against this frozen baseline, not only easy separated mixtures.

Any follow-up needs a literature-led design, fresh seeds and independent numerical
and utility validation. Larger caps, weaker thresholds and automatic public
integration are not approved remedies. Multiple angles, automatic true-component
recovery, application fusion and generic inference guarantees remain out of scope.

The subsequent [weight/restart assessment](GVM_WEIGHT_RESTART_RESEARCH.md) now
provides that proposed bounded design, with provenance in
[GVM-08](RESEARCH_REFERENCES.md#gvm-08). It does not change this study's frozen
protocol, numerical outcomes or no-promotion decision. The subsequent
[test-only execution](GVM_WEIGHT_PROTOCOL.md) is separately approved; its
confirmation and promotion decisions do not replace this study's results.

## Reproduction and completion audit

Use the [runner](../tools/gvm_wholefit_study.py),
[independent audit](../tools/gvm_wholefit_audit.py),
[descriptive analysis](../tools/gvm_wholefit_diagnostics.py),
[plot generator](../tools/gvm_wholefit_plot.py) and
[archive builder](../tools/gvm_wholefit_archive.py). The private, read-back-verified
archive contains exact confirmation inputs, source/configuration hashes, versions,
actual execution logs/errors/timestamps, models/traces, plots and rerun instructions.
The initial eight-test source revision was not separately saved; its original log
is retained. The final nine-test source and all confirmation sources are frozen
and preserved exactly. This limitation does not affect confirmation provenance.

Verified complete: bounded study, independent audit, unchanged protected runtime
and earlier protocols, documented result and reproducibility evidence. Promotion:
not qualified. No merge, release, stopping/default change or shared-pilot revival.
