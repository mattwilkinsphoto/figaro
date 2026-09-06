# Higher-dimensional GPSS and quantile validation

## Outcome: useful candidates, different strengths

All 480 experiments completed without execution failures, consuming 576 million target
density evaluations. The [checked data](sampling-high-dimensional-results.csv) retain
every seed, checkpoint, observable, and selected stopping decision. The protocol and
kernel controls were committed at `8d9cca12` before the full experiment.

GPSS scales well on the unconstrained Gaussian and heavy-tailed fixtures. Quantile
sampling is substantially better on the positive-constrained fixture. Neither is a
general replacement: strong correlation, curvature, and separated modes remain important
limits. In particular, **both methods have zero joint coverage and zero precision
successes on the 32-dimensional asymmetric mixture**. Completing all transitions does
not mean a chain explored the target adequately.

### Coverage and stopping, including unsuccessful cases

Each coverage entry is the number of runs whose intervals cover **all six** analytic
truths, out of 20. Cap coverage uses the final fixed budget; stopped coverage uses the
first precision success or the cap. Success coverage includes only runs that declared
precision, so its denominator differs. Zero successes means conditional coverage is
undefined, not zero. Every row has zero execution failures.

| Dimension | Target | Method | Cap coverage | Stopped coverage | Precision successes | Success coverage |
| --- | --- | --- | --- | --- | --- | --- |
| 8 | Gaussian | GPSS | 18/20 | 20/20 | 20/20 | 20/20 |
| 8 | Gaussian | Quantile | 20/20 | 19/20 | 20/20 | 19/20 |
| 8 | Correlated | GPSS | 20/20 | 20/20 | 16/20 | 16/16 |
| 8 | Correlated | Quantile | 19/20 | 19/20 | 0/20 | N/A |
| 8 | Banana | GPSS | 19/20 | 19/20 | 20/20 | 19/20 |
| 8 | Banana | Quantile | 19/20 | 19/20 | 20/20 | 19/20 |
| 8 | Student5 | GPSS | 19/20 | 20/20 | 20/20 | 20/20 |
| 8 | Student5 | Quantile | 18/20 | 19/20 | 20/20 | 19/20 |
| 8 | Positive | GPSS | 19/20 | 19/20 | 20/20 | 19/20 |
| 8 | Positive | Quantile | 19/20 | 18/20 | 20/20 | 18/20 |
| 8 | Asymmetric | GPSS | 18/20 | 18/20 | 0/20 | N/A |
| 8 | Asymmetric | Quantile | 0/20 | 0/20 | 0/20 | N/A |
| 32 | Gaussian | GPSS | 18/20 | 19/20 | 20/20 | 19/20 |
| 32 | Gaussian | Quantile | 18/20 | 18/20 | 20/20 | 18/20 |
| 32 | Correlated | GPSS | 19/20 | 19/20 | 0/20 | N/A |
| 32 | Correlated | Quantile | 8/20 | 8/20 | 0/20 | N/A |
| 32 | Banana | GPSS | 19/20 | 19/20 | 0/20 | N/A |
| 32 | Banana | Quantile | 18/20 | 18/20 | 0/20 | N/A |
| 32 | Student5 | GPSS | 19/20 | 18/20 | 20/20 | 18/20 |
| 32 | Student5 | Quantile | 20/20 | 20/20 | 1/20 | 1/1 |
| 32 | Positive | GPSS | 19/20 | 19/20 | 0/20 | N/A |
| 32 | Positive | Quantile | 20/20 | 20/20 | 20/20 | 20/20 |
| 32 | Asymmetric | GPSS | 0/20 | 0/20 | 0/20 | N/A |
| 32 | Asymmetric | Quantile | 0/20 | 0/20 | 0/20 | N/A |

Good coverage with no precision successes can simply mean wide intervals. Conversely,
precision success is not a guarantee of coverage. Twenty repetitions are too few to
certify nominal 95% coverage or rank methods by one or two coverage counts.

### Equal-budget error comparison at 32 dimensions

RMSE is computed across all 20 final-cap estimates. Each cell gives **GPSS / quantile**;
smaller is better. These three queries summarize marginal, aggregate, and event behavior;
the report tool prints all six queries for both dimensions, without selecting winners.

| Target | First-coordinate squared RMSE | Mean-square RMSE | Event-probability RMSE |
| --- | --- | --- | --- |
| Gaussian | 0.00712 / 0.02247 | 0.00053 / 0.00378 | 0.00081 / 0.00294 |
| Correlated | 0.16195 / 0.21610 | 0.16226 / 0.21715 | 0.01432 / 0.02717 |
| Banana | 0.03689 / 0.04011 | 0.00977 / 0.01309 | 0.00478 / 0.00608 |
| Student5 | 0.01622 / 0.07166 | 0.01234 / 0.09257 | 0.00118 / 0.00559 |
| Positive | 0.40326 / 0.06958 | 0.03913 / 0.01266 | 0.02784 / 0.00410 |
| Asymmetric | 0.49289 / 0.50090 | 0.50027 / 0.49861 | 0.10000 / 0.10001 |

For example, GPSS's Gaussian mean-square error is about seven times smaller at this
budget. On positive support, quantile's first-coordinate squared error is about six
times smaller. These are **error ratios, not CPU speedups**. Banana first-coordinate
mean error instead favors quantile (0.01702 versus GPSS 0.03947), illustrating why one
aggregate score cannot decide which sampler is best.

At 32 dimensions, the median aligned retained draws per chain are 38688 / 2876 for
Gaussian and 16898 / 2111 for positive support (GPSS / quantile). More draws did not
rescue GPSS on the constrained case. Quantile's corresponding median warm-up costs,
summed over four chains, are 77860 and 103604 evaluations; warm-up is not free.
The mixture event estimates remain near zero instead of the analytic probability
approximately 0.10003. This demonstrates incorrect recovered mode mass, but the
summary records alone do not establish that no chain ever visited the other mode.

### Recommended next boundary

Keep both kernels research-only for this checkpoint and leave production defaults
unchanged. A next implementation milestone should be a narrowly scoped, opt-in
continuous-vector sampler interface: GPSS for unconstrained targets and quantile for
coordinate-constrained targets, with explicit log-density, initialization, immutable
state, cancellation, and budget contracts. Validate independent implementation agreement
and lifecycle behavior before exposing it as a supported production API. Do not imply
automatic support for arbitrary Figaro graphs or automatic sampler selection.

Constraint transformations and frozen preconditioning deserve separate, cost-accounted
comparisons. Multimodal exploration needs its own strategy and mode-weight tests;
additional threads or these two local kernels alone are not a solution. This study has
no higher-dimensional Metropolis comparator, so it cannot establish superiority over
the existing production runner. Its 200-transition warm-up and six-query adjustment
also differ from the preceding two-dimensional study: their coverage percentages are
not directly interchangeable.

## Protocol fixed before the experiment

Branch `modernize/sampling-high-dimensional`, based on `bde34b65` plus the CI foreground
startup fix. This follows [matched-budget validation](SAMPLING_BUDGET_VALIDATION.md).
Production library APIs/defaults and dependencies remain unchanged.

Twenty new seed groups (`1700113 + 130363 * round`, rounds 0-19), dimensions 8 and 32,
six analytic targets, two methods (`gpss`, `quantile`), and four independently seeded
chains give 480 experiments. Each chain has 300000 target-density evaluations including
initialization and 200 discarded warm-up transitions. Checkpoints are 75000, 150000,
and 300000. No affine fitting or post-result tuning is permitted in this screen.

Targets: independent Gaussian; equicorrelated Gaussian (correlation 0.95); independent
banana pairs X=Z,Y=0.4(Z²-1)+0.5E; multivariate Student t(5); independent rate-1
exponentials on the positive orthant; and an asymmetric mixture with shared mode label,
0.9 N(-2*1,0.25 I) + 0.1 N(3*1,0.25 I). Initial states are standard Normal, except
positive-target coordinates use 0.1+abs(Normal). Initial states are not exact target draws.

Six monitored observables are first coordinate, last coordinate, first coordinate squared,
mean squared coordinate, first-times-last coordinate, and P(|first|>2), except the positive
target uses P(first>2) and the mixture uses P(first>0). Truths are specified analytically
before execution, with an independent Python check. Aggregate moments alone cannot hide
bad marginal means or mode mass. These six observables still do not exhaust all coordinates.

GPSS uses (d-1)*log(radius), a normalized Gaussian projected onto the tangent hyperplane,
geodesic shrinkage, and width-1 radial stepping-out/shrinkage. Degenerate tangents are
redrawn with a bounded limit; zero radius, numerical collapse, and exhausted search fail
explicitly. Quantile sampling reuses the fixed-Cauchy(0,2) coordinate sweep, with exact
target/reference correction. Both implementations use immutable vectors and bounded calls.

Only complete transitions whose cost fits a checkpoint are retained. Chain lengths are
aligned to the shortest prefix; excess completed draws and all warm-up costs remain
charged. Incomplete transitions never become samples. Full traces precede replay, so
this is a cost-matched statistical audit, not an online or wall-clock speed benchmark.
State-dependent cost stopping can itself select trace lengths; coverage is measured,
not assumed. Numerical/model failures invalidate that run conservatively at every
checkpoint and stay in all coverage denominators. Interruption aborts the experiment.

The unchanged modern.8 assessment uses minimum 2000 retained draws per chain, relative
full width 0.15, and nominal 95% Bonferroni-adjusted confidence across six observables.
Selected stopping is the first successful cost checkpoint or final cap. Report fixed
and selected-stop joint coverage, successes, success-conditional coverage, failures,
aligned draws, and marginal/aggregate/event RMSE. No promotion threshold is inferred
from a small difference in 20 replications.

Controls will test tangent geometry, polar-Jacobian correctness, support preservation,
one-step stationarity from independently generated analytic target draws, higher-dimensional
moments/cross moments, hard caps, reproducibility, and partial-transition replay. Analytic
oracles are an independent check of target preservation, not a cross-language replication
of a third-party implementation; that separate comparison remains a limitation.

Primary references: [GPSS (2023)](https://proceedings.mlr.press/v202/schar23a.html),
[quantile slice sampling (2025 revision)](https://arxiv.org/html/2407.12608v2), and the
[Julia GPSS documentation](https://turinglang.org/SliceSampling.jl/stable/gibbs_polar/).
No external sampler source is copied or installed. Research controls do not establish
mixing on arbitrary models, and no general graph integration is included.

## Overview: what a user should take from this

The [example](../FigaroExamples/src/main/scala/com/cra/figaro/example/HighDimensionalSamplingValidation.scala)
asks whether the favorable two-dimensional results survive more coordinates, hard support
boundaries, and a harder mixture. It does not add `GPSS(...)` to Figaro's inference API.
Use it to reproduce this study or investigate sampler behavior, not to pass a live Figaro
universe into a new production sampler. It shares only the package-private quantile sweep
with the earlier example; existing two-dimensional kernels and recorded results are unchanged.

The two methods spend evaluations differently. Quantile sampling must update every
coordinate to complete a sweep, so its cost per retained draw grows with dimension.
GPSS moves the whole vector using one direction and one radius, but expensive angular
searches, narrow feasible regions, and mode barriers can erase that advantage. Equal
target-call counts do not account for vector algebra or diagnostic overhead, and calls
are generally more expensive in 32 dimensions than in eight. Compare methods **within**
a dimension/target, not absolute runtimes between dimensions.

### Analytic quantities being checked

| Target | E[first], E[last] | E[first²] | E[mean square] | E[first*last] | Event probability |
| --- | --- | --- | --- | --- | --- |
| Gaussian | 0, 0 | 1 | 1 | 0 | 2 Phi(-2) |
| Correlated | 0, 0 | 1 | 1 | 0.95 | 2 Phi(-2) |
| Banana | 0, 0 | 1 | 0.785 | 0 | 2 Phi(-2) |
| Student5 | 0, 0 | 5/3 | 5/3 | 0 | 2 T5(-2) |
| Positive | 1, 1 | 2 | 2 | 1 | exp(-2) |
| Asymmetric | -1.5, -1.5 | 4.75 | 4.75 | 4.5 | 0.9 Phi(-4) + 0.1 Phi(6) |

Phi and T5 are standard Normal and Student-t(5) CDFs. All truths are the same in eight
and 32 dimensions. The correlated target has covariance `0.05 I + 0.95 11ᵀ`. Banana
pairs are independent; the first and last coordinates belong to different pairs. The
Student target shares one chi-squared scale across all coordinates. The mixture has one
shared mode label, not a product of independent scalar mixtures. Those distinctions are
important: superficially similar constructions would have different joint distributions.

## Quick start (three steps)

1. Run controls: `sbt --server --batch "examples / Compile / runMain com.cra.figaro.example.HighDimensionalSamplingValidation check"`.
2. Run a smoke comparison: `sbt --server --batch "examples / Compile / runMain com.cra.figaro.example.HighDimensionalSamplingValidation 1 20000" > high-dimensional-smoke.log`.
3. Check it: `python3 -B tools/summarize_high_dimensional.py high-dimensional-smoke.log --repetitions 1 --cap 20000`.

The smoke run deliberately has too little work for many precision decisions. Passing
the validator means the report is complete and internally consistent, not that all
samplers reached precision or every interval covered its truth.

### Common pattern 1: inspect the checked study

```sh
python3 -B tools/summarize_high_dimensional.py docs/sampling-high-dimensional-results.csv --repetitions 20 --cap 300000
```

This recomputes cap/selected-stop coverage, successful stops, success-conditional coverage,
failure counts, aligned draws, warm-up costs, and RMSE for all six observables. All-run
coverage retains failed runs in its denominator. If an estimate failed, the corresponding
RMSE is unavailable, rather than quietly excluding the failed case.

### Common pattern 2: reproduce the full comparison

```sh
sbt --server --batch "examples / Compile / runMain com.cra.figaro.example.HighDimensionalSamplingValidation 20 300000" > high-dimensional.log
python3 -B tools/summarize_high_dimensional.py high-dimensional.log --repetitions 20 --cap 300000
```

### Common pattern 3: partition the predeclared seed groups

```sh
sbt --server --batch "examples / Compile / runMain com.cra.figaro.example.HighDimensionalSamplingValidation 10 300000 0" > first-half.log
sbt --server --batch "examples / Compile / runMain com.cra.figaro.example.HighDimensionalSamplingValidation 10 300000 10" > second-half.log
python3 -B tools/summarize_high_dimensional.py first-half.log second-half.log --repetitions 20 --cap 300000
```

Do not combine overlapping seed ranges; the validator rejects duplicates and missing cases.
An intentionally partial report requires its exact `--first-round` and `--repetitions`.
Optional `--output PATH` creates a normalized CSV without overwriting an existing file;
`--acl-script PATH` invokes a Windows access-grant hook immediately after creation.

## API reference

`HighDimensionalSamplingValidation.main(args: Array[String]): Unit` is the sole public
entry point. `Array("check")` runs the controls. Otherwise accept zero to three numeric
strings: repetitions (default 20, positive), per-chain evaluation cap (default 300000,
20000-1000000 and divisible by four), and first round (default zero, nonnegative).
Example: `HighDimensionalSamplingValidation.main(Array("1", "20000"))`.

Returns Unit and prints quoted CSV, not an inference result object. Invalid budgets or
arguments throw `IllegalArgumentException`/`NumberFormatException`; model/numerical/search
failures become explicit failed rows; interruption aborts. A zero process exit therefore
does not imply all experiments executed successfully. Inspect `status` and the failure
counts. Internal kernels, exact controls, and target definitions are private.

`evaluations` and `warmupEvaluations` are totals across four chains; `budgetPerChain`
is the cap for each individual chain. Warm-up includes initialization. `availableDraws`
counts completed retained draws before equal-length alignment; subtract `4*drawsPerChain`
to see excluded excess draws. Their work remains charged. A stopped row copies the first
successful checkpoint (or cap), including that checkpoint's cost, not the whole run's cost.

## Gotchas and remaining boundaries

- One-step preservation from exact starts is a correctness control, **not evidence of
  convergence** from ordinary starting points. A sampler trapped in a mode may preserve
  stationarity while failing the practical task of discovering the right mode weights.
- Hard constraints are represented by negative-infinite log density outside support;
  that is a normal rejected proposal, not a numerical error. NaN and positive infinity
  fail. GPSS cannot start at radius zero; tangent/numerical/search exhaustion is explicit.
- There are no per-coordinate likelihood caches: every quantile proposal evaluates the
  complete target. Incremental graph evaluation could change its practical cost substantially.
- Six monitored quantities do not cover every marginal, tail, dependence, or mode. Twenty
  replications cannot certify 95% coverage or support fine rankings. No formal paired
  significance test or cross-language third-party implementation replication is claimed.
- No affine tuning, learned transport, gradient support, graph-state mutation, thread pool,
  or arbitrary-model factory is introduced. Positive orthants are only one kind of constraint;
  simplices, manifolds, disconnected supports, and dimensions above 32 remain untested.

## Verification

Local clean compilation, kernel controls, the complete experiment, and the exact CI
smoke command passed. All 11520 checked query rows validate; 19 report-tool tests and
12 documentation-tool tests pass, with generated-reference freshness and local links
verified. The [full CI run at 46c0ab39](https://github.com/mattwilkinsphoto/figaro/actions/runs/34049298709)
passed after the foreground-runner fix, including packaging, publication, and reproducible
rebuilds. That run compiled this kernel but preceded the added higher-dimensional control,
data, and smoke gates; subsequent branch runs exercise those additional gates.

Related: [previous comparison](SAMPLING_BUDGET_VALIDATION.md), [literature review](SAMPLING_RESEARCH.md),
[reliability policy](MCMC_RELIABILITY.md), [parallel performance](PARALLEL_PERFORMANCE.md),
and [build/CI troubleshooting](BUILDING.md).
