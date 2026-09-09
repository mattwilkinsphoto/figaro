# Importance uncertainty calibration and robustness

## Literature baseline and predeclared protocol

This study tests uncertainty estimates, not just posterior point accuracy. It does
not change the estimator, smooth weights, or enable precision stopping.

- [Vehtari et al., JMLR 2024](https://jmlr.org/papers/v25/19-556.html): PSIS stabilizes
  importance weights and supplies tail diagnostics. Its MCSE remains an approximate
  diagnostic; a good fitted weight tail is not proof of unobserved support coverage.
  We retain raw SNIS as the control rather than claiming PSIS results for raw weights.
- [Paananen et al., 2021](https://link.springer.com/article/10.1007/s11222-020-09982-2)
  distinguish common weights from expectation-specific contributions and describe
  SNIS as a ratio of means. This motivates checking the query contributions, not
  only the common importance ratios. Their adaptive moment-matching algorithm is
  not implemented by this calibration study.
- [Agapiou et al., 2017](https://arxiv.org/abs/1511.06196) relate importance-sampling
  cost to target/proposal discrepancy and intrinsic dimension. We include a
  correlated 12-dimensional control and a nonlinear four-dimensional target.
- [Chen et al., April 2026 preprint](https://arxiv.org/html/2604.03827v1) investigate
  rare-event rate intervals under a compound-Poisson/Horvitz–Thompson formulation.
  This is useful caution about sparse events, not a drop-in interval for Figaro's
  self-normalized posterior estimator. No code or figures are copied.

Before running: fix 200 fresh seeds `5000003 + 104729*i`, three production budgets
2,000/10,000/40,000 and all 11 scenarios in `ImportanceCalibrationStudy`. The two
held-out posterior cases also charge 4,000 pilot target calls per run. Include
normal, correlated, nonlinear, boundary, rare-event, deliberately tilted rare-event,
heavy-tail, separated-mode and deliberately missed-mode controls. Repetitions are
fresh across seeds; budgets use nested production prefixes and are not independent
replications. Analytic references and point-accuracy tolerances are fixed in source.

Compare ordinary raw plug-in 95% intervals with a research-only 20-batch delta-ratio
interval using Student t with 19 degrees of freedom. For globally normalized weights,
`z_i = w_i * (h_i - mean)`, batch standard error is
`sqrt(20/19 * sum_b (sum_{i in b} z_i)^2)`. Do not average independently normalized
batch estimates. This interval is still asymptotic; batching cannot discover absent
events, overcome infinite contribution variance, or correct missed modes.

Record variance-contribution effective count `(sum z_i^2)^2 / sum z_i^4` and a Pareto
fit to `abs(z_i)`. A predeclared exploratory flag uses count below 20 or fitted k at
least 0.7. These are engineering diagnostics, not calibrated error probabilities or
a theorem inherited from PSIS. Zero variation counts as unavailable precision, not
zero uncertainty. Preserve all outcomes, including warning/danger runs. Report
unconditional coverage and point accuracy, plus coverage conditional on passing
diagnostics with its denominator. Never tune these thresholds on these results.

The intended outcome is an honest operating envelope and a decision about whether
the batch candidate merits public exposure. Automatic stopping needs a separate
sequential-coverage study even if fixed-budget coverage improves.

## Results and decision

The [complete 6,600-trial evidence](importance-calibration-results.csv) preserves
every seed, budget and outcome. Each row is one trial, not a selected passing run.
Coverage means inclusion of the analytic truth in an approximate nominal 95%
interval. Zero observed variation is counted as noncoverage, not a valid interval.

| Control | Raw coverage at 2k / 10k / 40k draws (out of 200) | Batch coverage at 2k / 10k / 40k | Interpretation |
| --- | --- | --- | --- |
| Normal | 193 / 188 / 190 | 195 / 190 / 191 | Ordinary control behaves plausibly |
| Correlated 12D | 192 / 191 / 196 | 189 / 193 / 195 | Correlation alone is manageable with matched covariance |
| Nonlinear banana 4D | 188 / 170 / 179 | 186 / 170 / 181 | More draws do not automatically repair proposal mismatch |
| Boundary Beta | 190 / 191 / 187 | 190 / 188 / 185 | Batching is not consistently superior |
| Rare Gaussian event | 44 / 148 / 177 | 44 / 148 / 180 | Sparse event contributions defeat ordinary intervals |
| Same event, tilted mixture | 196 / 186 / 188 | 193 / 188 / 187 | Improving the proposal addresses the sparse-event mechanism |
| Heavy Student-t target | 193 / 193 / 189 | 194 / 194 / 190 | Symmetric mean coverage can look good despite dangerous tails |
| Separated modes | 191 / 190 / 189 | 190 / 190 / 191 | Coverage is distinct from a tight requested accuracy |
| Deliberately missed mode | 0 / 0 / 0 | 0 / 0 / 0 | Neither interval repairs absent target mass |
| Pilot-fitted held-out Gamma | 189 / 188 / 192 | 190 / 191 / 192 | Stronger evidence than the original 30-run group |
| Pilot-fitted held-out Dirichlet | 192 / 188 / 192 | 188 / 185 / 193 | No consistent batch-interval advantage |

The evidence checker prints Wilson 95% intervals for each measured coverage rate.
For example, 190/200 gives about [91.0%, 97.3%]; do not interpret a single observed
95% proportion as exact calibration. Budgets share seeds and cannot be pooled as
independent trials. This is a finite, explicit-model suite, not a universal guarantee.

At 2k production draws, the ordinary rare-event proposal met its predeclared 50%
relative-error target in **0/200** trials; the tilted mixture did so in **200/200**.
The latter is supplied using known event geometry, not discovered automatically;
its planning cost is not measured. This is accuracy per target-call evidence, not
a wall-clock speedup claim. At 10k/40k, ordinary-proposal accuracy was 82/200 and
146/200. Existing health checks passed 44/148/198 runs respectively and some of
those missed the accuracy target. A passed health assessment is not a requested
precision guarantee—these runs did not ask the health API for a precision threshold.

The exploratory contribution flag removes those sparse-event green assessments,
but its conditional coverage is not safe: for banana at 40k, only 15 runs pass
the combined filter and just 6/15 intervals cover. Conditioning on diagnostics
changes the population of runs. Accurate-but-flagged trials are not necessarily
false alarms; the true sampling variance may be dangerous despite a lucky estimate.

**Decision:** retain the existing raw estimator and warning semantics; do not
promote the batch interval or contribution filter as a calibrated public remedy.
Do not enable automatic precision stopping. The study identifies an operating
boundary and justifies the next proposal-fitting milestone rather than concealing
the nonlinear/rare-event failures by relaxing thresholds or rerunning seeds.

## Practical guidance

Use frozen vector proposals for supported explicit continuous densities when the
proposal covers the important mass and relevant query tails. Inspect health,
repeat with fresh seeds and different plausible proposals, and compare the query
of interest, not only a generic mean. For a rare event, a broad component plus an
event-focused component can be much more effective than simply increasing draws.
For separated modes use components covering each plausible mode; pilot starts
must explore them. For nonlinear geometry, coordinate transforms or richer fitted
proposals need their own validation. No variance estimator sees what was never sampled.

## Reproduction and delivery gates

```sh
sbt "figaro / Test / testOnly com.cra.figaro.test.modernization.ImportanceCalibrationTest"
sbt "figaro / Test / runMain com.cra.figaro.test.modernization.ImportanceCalibrationStudy full"
python -B tools/summarize_importance_calibration.py docs/importance-calibration-results.csv
python -B -m unittest discover -s tools -p 'test_summarize_importance_calibration.py'
```

The study is test-only and adds no library runtime or dependency. CI checks the
saved complete evidence and deterministic formulas; the full 200-seed study remains
an explicit reproducible assessment, not a stochastic pass/fail gate. Commit,
remote CI and main integration are separate delivery steps. The next milestone is
bounded multi-component pilot fitting with independent production, followed by
explicit, ownership-safe graph integration—not automatic graph rewriting.

Local modern.11 candidate validation: 417 modernization tests passed, including
the two new formula/protocol regressions; all 70 evidence-tool tests and seven
artifact-checker tests passed. All four rebuilt archives and the independently
compiled consumer passed. The API handbook remains current at 11,983 method
entries and local documentation links pass. Four pre-existing Scaladoc warnings
remain. This records local checks; remote CI/integration status is tracked separately.
