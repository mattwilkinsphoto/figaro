# Pilot-fitted defensive importance sampling: assessment and protocol

This milestone evaluates a better proposal for concentrated posteriors. It does not
change a public sampler, defaults, warning thresholds, or stopping policy. Prototype
code remains in test sources and is not shipped in the library.

Follow-on: the [public frozen-proposal API](VECTOR_IMPORTANCE.md) now provides an
opt-in implementation and separate multimodal/boundary/heavy-tail acceptance grid.
The historical protocol, prototype and results below remain unchanged; their
research-only boundary describes that prototype, not the new library entry point.

**Outcome:** the unchanged prototype substantially improves point accuracy on these
low-dimensional, concentrated unimodal posteriors once enough pilot work is available.
It is not a suitable automatic replacement at small budgets, nor a demonstrated
solution for multimodality, rare events or general high-dimensional models.

## Literature baseline and choice

Hesterberg's defensive mixtures retain a broad component; [Owen and Zhou (2000)](https://www.tandfonline.com/doi/abs/10.1080/01621459.2000.10473909)
develop mixture/control-variate variance results. We use the support-preserving
mixture idea, not their control-variate estimator or its stronger variance claims.
[Delyon and Portier (2021)](https://arxiv.org/html/1903.08507v4) analyze safe adaptive
mixtures under explicit conditions. Our frozen parametric two-stage experiment is
not their sequential kernel-density algorithm and does not inherit its theorem.
[Korba and Portier (2022)](https://proceedings.mlr.press/v151/korba22a.html) study
regularized adaptive weights; production weights here remain unmodified.

Recent alternatives include [flow-based latent proposal updates (2025)](https://arxiv.org/abs/2501.03394)
and [importance-corrected neural JKO sampling (2025)](https://proceedings.mlr.press/v267/hertrich25a.html).
They merit a future higher-dimensional/complex-geometry assessment. They are not
the first implementation for these two- and three-dimensional fixtures: training,
dependencies and density-correction validation would materially expand the work.
No external implementation is copied or installed for this prototype.

## Predeclared experiment (before benchmark execution)

- Four targets: the original Gamma/Dirichlet datasets and a second independently
  generated dataset of each family (fixture generator seeds offset by 1,000,003).
  All retain 200 observations and the full independent Uniform(0,10) parameter prior.
- Thirty run seeds: `1009 + 7919*i`, i=0..29. The first 15 are the familiar batch;
  the remaining 15 are reported separately as a replication batch, not claimed to
  be previously unseen seeds. The second datasets are new to this proposal study.
- Total target-evaluation budgets: 2,000, 20,000 and 200,000. Every method pays for
  initialization, discarded warm-up, pilot draws and unfinished slice transitions.
- All inference streams use explicitly pinned LXM. Fixture generation deliberately
  retains the historical Java-compatible generator so the data remain reproducible.
- Baseline: independent Uniform(0,10) importance proposals, spending the whole budget.
- Candidate: four existing Quantile slice pilot chains spend half the budget, capped
  at 10,000 target calls in total. Starts use a separate seeded Uniform(1,9) stream;
  each chain discards 20 complete transitions. Pool retained pilot draws, fit one
  full-covariance Gaussian with covariance multiplied by 2 and diagonal ridge 1e-4.
  Fewer than four retained draws in any chain or fewer than 20 pooled draws means
  explicit fallback to the prior, still charging pilot cost. No convergence or
  posterior-accuracy claim is made for the training draws.
- Freeze `q = 0.1*Uniform(box) + 0.9*Gaussian`. Use a separate production RNG stream,
  fresh independent mixture draws and the **full mixture density** in every weight.
  Gaussian draws outside the box receive zero target weight; never redraw them.
  Production uses the remaining budget. Pilot draws are never included in estimates.
- Comparator: four existing Quantile slice chains with the same start convention,
  discarded 20-transition warm-up and one quarter of the total evaluation cap each.
  Align retained prefixes for diagnostics; charge all work, including unused tails.
- No reference mean, posterior SD or generating parameter is used in proposal fitting,
  initialization, fallback decisions or sampling. Independent full-box quadrature
  supplies validation-only reference means and SDs after the protocol is frozen.
- Report every coordinate: reference error, ordinary MCSE, nominal 95% interval
  coverage, accuracy within 0.1 posterior SD, ESS, health status, pilot/fallback state,
  retained draws, exact evaluation count and elapsed time including fitting/diagnostics.
  Budget exhaustion is a work outcome, not a convergence claim.
- One complete grid, no retries until passing and no threshold/seed tuning. Timings
  are exploratory single-JVM measurements, not controlled speedup claims. Compare
  target calls needed to achieve the same predefined accuracy before timing claims.

## Correctness and safety gates

Test mixture normalization/density, component-versus-mixture denominator distinction,
support, explicit outside-box zero weights, seed replay, fitting refusal/fallback,
hard evaluation caps, interruption and untouched production APIs. Independently
check new dataset sufficient statistics and quadrature order agreement. Preserve
all benchmark rows, including failures or inadequate traces; require a complete
grid in the evidence parser. Do not turn a danger flag into an accuracy pass.

Conditional on the frozen pilot, production draws are IID from the known proposal.
Poor pilot mixing can reduce efficiency but does not justify omitting the proposal
density correction. The uniform component covers this bounded prior box only;
it is not a generic full-space safety component. It does not guarantee that a
small finite sample discovers a narrow missed mode. No PSIS smoothing is used.

## Separately declared coverage follow-up

After observing the initial grid (including 25/30 coverage for one Dirichlet
coordinate), retain the method, fit, thresholds and 20,000-call budget unchanged.
Run the defensive candidate on the two new datasets with 200 fresh seeds each:
`1000000007 + 7919*i`, i=0..199. Report all 400 trials / 1,000 coordinate rows.
This is an additional replication, not a replacement for the original grid.
Use per-coordinate binomial Wilson intervals to show uncertainty in observed
coverage; they are descriptive, not multiplicity-adjusted acceptance tests.
No tuning or retries are allowed in response to this batch.

## Results

The [complete comparison](defensive-importance-results.csv) contains 1,080 trials /
2,700 coordinate rows. A run meets the accuracy target only if **every coordinate's
posterior mean error is at most 0.1 of that coordinate's reference posterior SD**.
These are accuracy counts, not counts of green health reports.

| Total target calls per run | Prior importance | Pilot + defensive importance | Quantile slice |
| --- | --- | --- | --- |
| 2,000 | 9/120 accurate | 1/120 accurate; all 120 fell back after inadequate pilot training | 6/120 accurate |
| 20,000 | 37/120 accurate | 120/120 accurate | 92/120 accurate |
| 200,000 | 60/120 accurate | 120/120 accurate | 120/120 accurate |

At 20,000 total calls, median raw-weight ESS increased from about 45 to 6,208 on
the original Gamma dataset, and from 1.9 to 5,782 on the original Dirichlet dataset.
Only 10,000 candidate draws contribute to the final estimate: the other 10,000 calls
paid for training. The second datasets showed the same qualitative improvement.
Each 15-seed half retained 60/60 candidate accuracy successes at that budget.

The candidate met the accuracy target in all runs with one tenth of the **tested**
budget where the slice comparator first met it in all runs. This is a discrete
budget comparison, not an estimate of the exact minimum budget, a universal 10x
improvement or a 10x wall-clock speedup. The slice kernel was faster at equal call
budgets, while the candidate estimates were more accurate. Gaussian proposal
evaluation, fitting and health diagnostics have real costs.

The low-budget counterexample is material: all candidates spent 1,000 calls on a
pilot that could not support fitting, then used only 1,000 fresh prior draws. Both
families' RMSE worsened against spending all 2,000 calls on the prior. A public API
must expose this outcome and require a deliberate training/fallback policy.

### Fresh-seed replication and interval coverage

The [separate coverage batch](defensive-importance-coverage.csv) contains 400 trials /
1,000 coordinate rows. Both new datasets met the all-coordinate point-accuracy
target in 200/200 trials with 20,000 total calls each; none fell back.

| New dataset / coordinate | Nominal 95% interval covered reference | Observed coverage | Descriptive Wilson 95% interval |
| --- | --- | --- | --- |
| Gamma shape | 189/200 | 94.5% | 90.4–96.9% |
| Gamma scale | 185/200 | 92.5% | 88.0–95.4% |
| Dirichlet alpha 1 | 189/200 | 94.5% | 90.4–96.9% |
| Dirichlet alpha 2 | 189/200 | 94.5% | 90.4–96.9% |
| Dirichlet alpha 3 | 187/200 | 93.5% | 89.2–96.2% |

All these descriptive intervals include 95%, but this does **not** establish
nominal coverage. Modest undercoverage remains plausible, especially for Gamma
scale. Coordinates are correlated; do not pool them as independent binomial trials.
Both second datasets use the same generating parameter settings as their original
counterparts; this is data replication, not a broad model-geometry stress test.

The original small-batch low coverage remains in the evidence, including 25/30 for
one Dirichlet coordinate. It was neither removed nor used to retune the method.
Six fresh Gamma runs still received Pareto danger warnings despite meeting the
point-accuracy target; all 200 Dirichlet runs passed these health checks. In the
original grid, 1/120 candidate runs at 20,000 calls and 9/120 at 200,000 calls were
flagged danger. Point accuracy for selected means does not settle tail reliability
or justify suppressing these warnings. No warning thresholds were changed.

### Scope of the measurement

These use sufficient-statistic vector log densities, **not** wall-clock timings of
the complete 200-observation Figaro graph. Their likelihoods are checked against
the historical study and independent sufficient statistics. The held-out reference
means/SDs come from independent NumPy/standard-library full-box quadrature with
144-versus-216 order agreement; this is a numerical control, not a rigorous error
enclosure. A poisoned-reference regression verifies that fitting/sampling never
reads the validation-only reference mean or SD.

Local verification: 406 modernization tests across 38 suites passed, including five
new proposal-contract groups. The final explicit RNG pin and oracle-separation
assertion also passed the focused suite. Independent posterior tests and complete-grid
evidence tests verify references, completeness, work counts, accuracy/coverage flags,
retained danger labels and malformed-evidence refusal. This is not a remote CI claim.

## Reproduce in three steps

1. Run contract checks and optionally the cheap entry-point smoke grid:

   ```sh
   sbt "figaro / Test / testOnly com.cra.figaro.test.modernization.DefensiveImportanceTest"
   sbt "figaro / Test / runMain com.cra.figaro.test.modernization.DefensiveImportanceStudy smoke"
   ```

2. Run `DefensiveImportanceStudy full` or `DefensiveImportanceStudy coverage` through
   the same `figaro / Test / runMain` command. Preserve stdout, including all `DI` rows.

3. Validate a saved log or the checked-in CSV:

   ```sh
   python -B tools/summarize_defensive_importance.py docs/defensive-importance-results.csv
   python -B tools/summarize_defensive_importance.py docs/defensive-importance-results.csv --batch second
   python -B tools/summarize_defensive_importance.py docs/defensive-importance-coverage.csv --coverage
   python -B -m unittest discover -s tools -p 'test_statistical_validation_reference.py'
   python -B -m unittest discover -s tools -p 'test_summarize_defensive_importance.py'
   ```

The oracle needs NumPy 2.3.5; the evidence parser uses only Python's standard library.
It rejects partial, duplicate or altered-work grids, never drops unfavorable rows,
and optionally writes a normalized CSV with `--export PATH`. Windows export requires
`--acl-script PATH` to an appropriate exact-path access-verification hook. Summaries
include coordinate-wise RMSE/coverage, all-coordinate accuracy, fallback/health counts,
first-coordinate ESS and median time. `NA` means unavailable, not zero error; a slice
run without sufficient retained chains counts as unsuccessful accuracy/coverage.
Repeated per-coordinate timing/work fields describe one trial and must not be summed.

## Delivery boundary and next milestone

Promotion requires favorable replicated accuracy/coverage evidence and an API design
for explicit densities/proposals, callback ownership, support, resource budgets and
refusal semantics. Automatic graph proposal replacement, model-coordinate discovery,
EM/multi-component fitting, normalizing flows and automatic sampler switching are
outside this initial experiment. See [inference health](INFERENCE_HEALTH.md) and
[statistical validation](STATISTICAL_VALIDATION.md) for the motivating evidence.

Recommended next milestone: design an opt-in **explicit-vector frozen-proposal**
interface, with pilot and production budgets reported separately, full proposal
densities and caller-visible fit failure. Validate it first on separated modes,
skew/boundary concentration and heavier tails, with mean and event-probability
references. Keep training separate, retain the original comparison evidence and
examine MCSE calibration before adding automatic precision-based stopping. This
study supports pursuing that milestone; it does not complete those production gates.
The subsequent [API milestone](VECTOR_IMPORTANCE.md#acceptance-evidence-and-reproduction)
records its own implementation and local validation; automatic precision stopping
remains deferred pending further coverage calibration.
