# Matched-density-budget sampling validation

## Outcome: retain multiple candidates; do not change defaults

Follow-up: [8- and 32-dimensional validation](SAMPLING_HIGH_DIMENSIONAL.md), including
positive constraints and a harder shared-label asymmetric mixture. The results below
remain the original two-dimensional study, not a general-purpose sampler ranking.

The 6 September 2026 study completed all **600 target/method/seed experiments**, with no
numerical/model/search failures and exactly **240 million target evaluations**. Its
[12000 query records](sampling-budget-results.csv) cover 2400 checkpoint/selected-stop
groups. The protocol and sampling implementation were committed as `0b3fa903` before
the full experiment ran; later changes strengthened controls/reporting, not the sampled
kernels or experimental parameters. The earlier research branch's CI also completed successfully.

At equal density-evaluation costs, **plain GPSS is the most balanced follow-up candidate
in this small screen**, while quantile sampling remains useful for tails and separated
modes. The finite-pilot affine variant is attractive for strong linear correlation but
cannot be enabled indiscriminately: it substantially worsened unequal-mode reliability.
This is an empirical interpretation, not a universal ranking or a production-readiness claim.

- On the rotated Gaussian, affine GPSS reduced Y² RMSE to 0.02901, versus 0.11029 for
  fixed Metropolis and 0.77295 for quantile sampling. Those are roughly 3.8x and 26.6x
  smaller errors at this cost, **not corresponding runtime speedups**. Both polar methods
  reached precision on all 30 runs; quantile sampling did so on none.
- On the banana, plain GPSS had Y² RMSE 0.01120, compared with 0.01370 for Metropolis,
  0.01865 for quantile, and 0.01250 for affine GPSS. The earlier draw-matched quantile
  advantage did not persist over Metropolis under this budget-matched protocol. The
  different baseline implementation, seeds, warm-up, and check schedule also matter.
- On Student5, quantile had the lowest Y² RMSE (0.01934 versus 0.03653 for Metropolis)
  and 30/30 selected-stop joint coverage. Affine tuning did not improve plain GPSS here.
- On unequal modes, quantile and plain GPSS recovered the mode mass well, but affine
  GPSS's mode-probability RMSE rose to 0.05918 versus 0.00394 for plain GPSS. Affine GPSS
  reached precision on 12/30 runs, and only **10/12 successes** jointly covered all truths.
  Fixed Metropolis never reached precision and had 0/30 joint coverage. All methods
  still estimated Y² reasonably, illustrating why checking only one easy observable misleads.

### Complete cap and selected-stop results

Each method has 100000 evaluations per chain (400000 total), including all warm-up.
Coverage is simultaneous across the five monitored quantities, with denominator 30.
`Success coverage` conditions on the method actually reaching the precision criterion.
Warm-up costs below are median totals across the four chains, inclusive of the pilot.

| Target / method | Cap coverage | Stopped coverage | Precision reached | Success coverage | Median aligned draws/chain | Median warm-up evaluations | Y² RMSE | Event RMSE |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Gaussian / RWM | 28/30 | 27/30 | 30/30 | 27/30 | 98999 | 4004 | 0.00678 | 0.00095 |
| Gaussian / quantile | 30/30 | 30/30 | 30/30 | 30/30 | 15394 | 24312 | 0.00595 | 0.00086 |
| Gaussian / GPSS | 30/30 | 29/30 | 30/30 | 29/30 | 14044 | 26570 | 0.00628 | 0.00093 |
| Gaussian / affine GPSS | 28/30 | 29/30 | 30/30 | 29/30 | 13692 | 26995 | 0.00579 | 0.00106 |
| Rotated / RWM | 29/30 | 29/30 | 25/30 | 24/25 | 98999 | 4004 | 0.11029 | 0.00719 |
| Rotated / quantile | 27/30 | 27/30 | 0/30 | 0/0 | 5906 | 56332 | 0.77295 | 0.04186 |
| Rotated / GPSS | 28/30 | 28/30 | 30/30 | 28/30 | 5936 | 56937 | 0.06476 | 0.00516 |
| Rotated / affine GPSS | 28/30 | 28/30 | 30/30 | 28/30 | 13440 | 30282 | 0.02901 | 0.00241 |
| Banana / RWM | 30/30 | 28/30 | 30/30 | 28/30 | 98999 | 4004 | 0.01370 | 0.00154 |
| Banana / quantile | 29/30 | 28/30 | 30/30 | 28/30 | 11838 | 31032 | 0.01865 | 0.00258 |
| Banana / GPSS | 30/30 | 29/30 | 30/30 | 29/30 | 12546 | 29370 | 0.01120 | 0.00129 |
| Banana / affine GPSS | 27/30 | 28/30 | 30/30 | 28/30 | 12058 | 30082 | 0.01250 | 0.00172 |
| Student5 / RWM | 27/30 | 27/30 | 30/30 | 27/30 | 98999 | 4004 | 0.03653 | 0.00133 |
| Student5 / quantile | 29/30 | 30/30 | 30/30 | 30/30 | 15948 | 23524 | 0.01934 | 0.00125 |
| Student5 / GPSS | 30/30 | 28/30 | 30/30 | 28/30 | 13024 | 28408 | 0.02342 | 0.00140 |
| Student5 / affine GPSS | 27/30 | 28/30 | 30/30 | 28/30 | 13684 | 27144 | 0.03185 | 0.00177 |
| Unequal modes / RWM | 0/30 | 0/30 | 0/30 | 0/0 | 98999 | 4004 | 0.00550 | 0.37472 |
| Unequal modes / quantile | 30/30 | 30/30 | 30/30 | 30/30 | 9868 | 36788 | 0.00689 | 0.00741 |
| Unequal modes / GPSS | 27/30 | 28/30 | 30/30 | 28/30 | 8766 | 40822 | 0.00985 | 0.00394 |
| Unequal modes / affine GPSS | 17/30 | 18/30 | 12/30 | 10/12 | 9636 | 36790 | 0.01658 | 0.05918 |

The report tool also prints coverage and Y² RMSE at 25000, 50000, and 100000 evaluations
per chain. Every method's cap RMSE for Y² decreased between those checkpoints, but
coverage did not improve monotonically. Unequal-mode affine GPSS had 17/30 joint coverage
at all three fixed checkpoints: simply raising this budget did not remove that shortfall.
Thirty seeds leave substantial uncertainty; 30/30 coverage does not certify a 95% guarantee,
and small differences such as 28/30 versus 29/30 are not evidence of a meaningful ranking.

### Decision and next boundary

Do not make affine tuning an automatic default or label quantile sampling a general
replacement. Next, validate plain GPSS and quantile kernels on higher-dimensional,
constrained, and asymmetric multimodal targets, including independent implementation
cross-checks. A production milestone should then add an explicit opt-in continuous-block
interface with density/transform contracts, lifecycle/cancellation, independent-chain
ownership, and graph-state isolation. Affine tuning needs a separate pilot-quality/mode
exploration investigation; no reliable automatic detector for its observed regression
has been demonstrated here. AGESS, learned nonlinear transports, and LHS/RQMC remain
unimplemented follow-ups, not capabilities implied by this study.

## Protocol fixed before the experiment

Research branch `modernize/sampling-budget-validation`, based on `22bba687`.
This is a follow-up to [the draw-matched screen](SAMPLING_RESEARCH.md), not a production release.

The predeclared comparison uses 30 new seed labels (`812031 + 104729 * round`),
four independently seeded chains, and five two-dimensional targets: standard Gaussian,
rotated anisotropic Gaussian, banana, multivariate Student t with five degrees of freedom,
and the previous unequal mixture. Five observables are X, Y, X², Y², and an event probability.
All analytic truths are specified in code, never fitted from experiment output.

| Fixture | Construction (independent standard Normals Z,E) | E[X], E[Y], E[X²], E[Y²] |
| --- | --- | --- |
| Gaussian | X=Z, Y=E | 0, 0, 1, 1 |
| Rotated | X=(3Z+0.1E)/sqrt(2), Y=(3Z-0.1E)/sqrt(2) | 0, 0, 4.505, 4.505 |
| Banana | X=Z, Y=0.4(Z²-1)+0.5E | 0, 0, 1, 0.57 |
| Student5 | X=Z/sqrt(V/5), Y=E/sqrt(V/5), independent V~chi-squared(5) | 0, 0, 5/3, 5/3 |
| Unequal modes | X is 0.8 N(-4,0.25)+0.2 N(4,0.25), Y=E; Normal variances shown | -2.4, 0, 16.25, 1 |

The fifth observable is P(|X|>2), except for unequal modes where it is P(X>0),
approximately 0.2. Normal/Student CDFs give the exact probabilities in the raw records;
the Python validator independently checks them. The rotated Gaussian has covariance
eigenvalues 9 and 0.01 (condition number 900), while the banana tests nonlinear curvature.

Methods: fixed unit-increment random-walk Metropolis, the previous fixed-Cauchy quantile
slice sweep, plain Gibbsian polar slice sampling (GPSS), and finite-pilot affine-tuned GPSS.
All are standalone immutable-vector kernels with the same counted target callback.
The random-walk comparator is not the native Figaro graph runner, so this is not a
direct cost comparison with the earlier graph-based benchmark.

Each chain receives a strict ceiling of 100000 target-density evaluations, including
initialization, 500 pilot transitions, and 500 additional warm-up transitions. The
affine method updates a per-chain mean/regularized covariance after pilot transitions
100, 250, and 500, then freezes it. Other methods discard the same number of transitions.
No pilot information is shared between chains. No gradients, target-specific tuning,
oracle initialization, or post-result parameter selection are used.

Reports use matched evaluation checkpoints 25000, 50000, and 100000 per chain.
Only complete transitions within a checkpoint enter its trace; an unfinished transition
is never converted to a draw. Unequal chain lengths are aligned to the shortest prefix,
with discarded excess draws and pilot costs reported. This computational stopping rule
itself can select state-dependent trace lengths; coverage is measured empirically, not
assumed to retain fixed-draw guarantees. Density counts do not equate CPU time or include
linear algebra/diagnostic overhead. The full experiment is serial, not a thread speed test.

At each checkpoint the unchanged modern.8 precision assessment uses relative full width
0.15, minimum 2000 draws per chain, and nominal Bonferroni-adjusted 95% confidence over
five observables. The selected-stop record uses the first successful budget checkpoint
or the final cap. Report all-run joint coverage, success count, success-conditional
coverage, observable RMSE, and failures, without dropping inconvenient seeds. The
stopped check schedule is cost-based here, not the earlier every-2000-draw schedule.

The geometry candidate follows the affine-transform principle in
[Schär, Habeck, and Rudolf (2024)](https://arxiv.org/html/2401.16567v2) and the radial/angular
kernel in [their GPSS paper (2023)](https://proceedings.mlr.press/v202/schar23a.html).
It is a finite-adaptation, independently piloted variant, not a reproduction of the
paper's shared-chain PATT system or its published performance claims. All sampler
implementation code is independently written; no third-party source is imported.

Selection remains provisional: prefer a candidate only when its error/coverage improves
across targets at these costs, and expose regressions. Thirty replications and small
analytic fixtures cannot certify coverage or general-purpose superiority. Production
graph integration and high-dimensional validation require separate decisions.

## What the implementation changes

The [example](../FigaroExamples/src/main/scala/com/cra/figaro/example/SamplingBudgetValidation.scala)
adds no production inference API or dependency. `quantile` reuses the previous research
kernel through package-private access. `gpss` samples a direction on the circle and then
a positive radius on the same slice, including the polar Jacobian `log(radius)` in two
dimensions. Radial stepping-out uses width 1. Bounded search failures abort the transition;
they do not accept an arbitrary candidate or act as ordinary Metropolis rejections.

`affine-gpss` learns each chain's center and 2x2 covariance from its first 500 pilot states.
The covariance receives diagonal ridge `1e-6 * max(1, trace(covariance)/2)` before Cholesky
factorization. Updates occur only at 100, 250, and 500. Each transition maps to the current
affine coordinates, evaluates the exact target after mapping candidates back, and returns
to original coordinates. The constant affine determinant cancels in slice comparisons.
All 500 subsequent warm-up transitions and all retained transitions use the frozen map.
These choices are a bounded research variant, not the paper's recommended schedule.

Every method starts from two standard Normal draws. The Metropolis proposal adds independent
standard Normal increments to X and Y and caches its current density; slice samplers count
their repeated current/candidate evaluations. No synthetic likelihood delay is inserted.
The `pilotEvaluations` field counts the first 500 transitions plus initialization for all
methods, whether they fit anything or not. `warmupEvaluations` is cumulative through 1000
transitions, **including** the pilot, not an additional amount to add to it. All costs are
summed across four chains. `budgetPerChain` remains a per-chain ceiling.

`availableDraws - 4 * drawsPerChain` measures completed draws not used after equal-length
alignment. Their evaluation cost is still charged. Each fixed checkpoint reports its own
consumed work, unlike the earlier screen's full-run cost on every stopped row. Full traces
are generated to the final ceiling before prefix replay; this is not an online controller.
Any numerical/model failure conservatively invalidates that experiment's assessments at
all checkpoints, including earlier ones; such runs remain explicit in the data. An external
interruption aborts execution instead of being counted as an ordinary completed experiment.

## Quick start (three steps)

1. Check controls: `sbt "examples / Compile / runMain com.cra.figaro.example.SamplingBudgetValidation check"`.
2. Run a small comparison: `sbt "examples / Compile / runMain com.cra.figaro.example.SamplingBudgetValidation 1 30000" > budget-smoke.log`.
3. Validate it: `python3 -B tools/summarize_sampling_budget.py budget-smoke.log --repetitions 1 --cap 30000`.

### Common pattern 1: inspect the completed study

```sh
python3 -B tools/summarize_sampling_budget.py docs/sampling-budget-results.csv --repetitions 30 --cap 100000
```

This recomputes cap/selected-stop coverage, precision success, failed-run count, aligned
draws, warm-up cost, Y²/event RMSE, and coverage/error at all three computation checkpoints.
It verifies seed labels, analytic truths, completeness, cost limits, and earliest-stop
replay. Failed estimates are never silently excluded from RMSE: they make it unavailable.
Coverage always retains the full experiment denominator.

### Common pattern 2: reproduce every predeclared case

```sh
sbt "examples / Compile / runMain com.cra.figaro.example.SamplingBudgetValidation 30 100000" > budget-full.log
python3 -B tools/summarize_sampling_budget.py budget-full.log --repetitions 30 --cap 100000
```

### Common pattern 3: split work into disjoint seed ranges

```sh
sbt "examples / Compile / runMain com.cra.figaro.example.SamplingBudgetValidation 15 100000 0" > budget-first.log
sbt "examples / Compile / runMain com.cra.figaro.example.SamplingBudgetValidation 15 100000 15" > budget-second.log
python3 -B tools/summarize_sampling_budget.py budget-first.log budget-second.log --repetitions 30 --cap 100000
```

The summarizer refuses duplicate or incomplete experiments. For an intentionally partial
study, provide its exact `--repetitions` and `--first-round`; do not present it as the full
predeclared study. Optional `--output PATH` writes a normalized CSV without overwriting an
existing file. `--acl-script PATH` invokes a Windows access-grant hook after file creation.

## API reference and gotchas

`SamplingBudgetValidation.main(args: Array[String]): Unit` is the only public entry point.
Use `Array("check")` for controls, or zero to three numeric strings: repetitions (default
30, positive), per-chain cap (default 100000, 20000-1000000 and divisible by four), and first
round (default zero, nonnegative). Example: `SamplingBudgetValidation.main(Array("1", "30000"))`.
It returns Unit, prints quoted CSV, and does not return a sampler object. Invalid arguments
throw `IllegalArgumentException` or `NumberFormatException`. Model/numerical/search failures
are recorded with failed statistics; interruption propagates and aborts. A successful process
exit is therefore not proof that every chain executed successfully: inspect `status` and the
failure totals. The report validator can reject malformed or misleading output even when
the Scala process exits successfully.

Kernels and protocol helpers are private; this is not an arbitrary graph or production
callback interface. Controls cover one-step analytic moments and event probabilities under
identity/nontrivial affine maps, cross moments, numeric validation, callback propagation,
strict caps, prefix equivalence, reproducible seeds, and interruption. These are stronger
than checking a plausible trace, but do not prove convergence on a new model.

Affine maps address scale, shift, and linear correlation, not all curvature or isolated
modes. Two-dimensional fixtures do not establish high-dimensional behavior. Student-t second
moments are tail-sensitive even with five degrees of freedom. Short pilots can learn a
misleading covariance; freezing them preserves a fixed production kernel but does not fix
bad exploration. Nominal 95% intervals and a precision label are not coverage guarantees,
especially under state-dependent cost stopping. No threading or wall-clock speedup is claimed.

Related: [prior research](SAMPLING_RESEARCH.md), [precision/reliability](MCMC_RELIABILITY.md),
[pilot block calibration](PROPOSAL_CALIBRATION.md), [parallel performance](PARALLEL_PERFORMANCE.md),
and [examples module](../FigaroExamples/README.md).
