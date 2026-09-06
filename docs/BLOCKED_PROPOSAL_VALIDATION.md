# Blocked-proposal validation

This milestone compares a new, fixed-covariance Gaussian block with the default proposal **and with existing joint prior resampling**. It measures useful exploration and precision, not just raw transition throughput. The implementation remains opt-in; see [usage, API, and limitations](BLOCKED_PROPOSALS.md).

## Reproduce

```sh
sbt "examples / Compile / runMain com.cra.figaro.example.BlockedProposalBenchmark 50 12000 4"
```

Keep `blocked,` CSV lines and exclude rounds -2 and -1. The [recorded CSV](blocked-proposal-results.csv) contains all 1,800 measured rows plus its header. It is intentionally unchanged by later builds or test runs.

`BlockedProposalBenchmark.main(args: Array[String]): Unit` accepts repetitions (default 20), maximum draws per chain (default 12000), and workers (default 4). Repetitions must be positive, draws at least 2000, and workers 1-4. It prints per-query data, writes no files, and checks for worker leaks. Invalid values raise `IllegalArgumentException`; malformed integers raise `NumberFormatException`. The CI smoke command uses `2 4000 2`; it checks execution, not statistical coverage or timing from two repetitions.

## Method

Recorded September 6, 2026 on Windows, AMD Ryzen 9 9950X (16 cores/32 logical processors), JDK 17.0.4, Scala 3.9.0, sbt 2.0.8, maximum JVM heap 6 GiB. The run used the implemented block kernel before the snapshot label changed from modern.5 to modern.6; the label change did not change the benchmarked kernel.

All cases use four chains, four workers, 2,000 warm-up transitions, and at most 12,000 retained draws per chain. Fixed runs always retain 12,000. Adaptive runs use relative **full** interval width 0.15, nominal joint confidence 0.95, minimum 2,000 draws, and checks every 2,000 draws. The remaining default batch-count, R-hat, and ESS safeguards are unchanged.

Fifty measured seeds are `62003 + round * 7919`, for rounds 0-49. Two full warm-up rounds are excluded. Strategy order rotates each round; fixed/adaptive execution order alternates. Same-seed pairing reduces some variation but does not imply exact traces across strategies or separately allocated multi-element graphs. Figaro's existing hash-based initialization order is not changed by this milestone.

Ground truths are analytic expectations, not estimates from a longer default run. All cases monitor two means, with Bonferroni-adjusted intervals; coverage below is **joint coverage of both**. No interval-width penalty is included when scoring coverage. Numerical intervals from failed mixing checks are reported for transparency, not endorsed as valid inference.

API times include construction, initialization, warm-up, sampling, diagnostics, and cleanup. Adaptive times include each checkpoint. The extra fixed-run precision assessment used only to populate the table is excluded from fixed API time. Timing is single-host illustrative evidence, not a dedicated microbenchmark or production service-level promise. No parallel-versus-serial speedup is measured: both approaches use four workers.

For each repetition, take the minimum mean ESS across the two queries, then divide by that run's API time. Tables give medians of those per-run quantities, not pooled draws or sums across queries. Poorly mixed runs can have unreliable ESS estimates, so compare precision success, accuracy, and coverage alongside the rate.

## Targets and proposals

| Workload | Analytic target | Strategies |
| --- | --- | --- |
| normal | Two independent standard Normals; both means 0 | Default single-element prior updates; joint random-walk block with covariance 2.8 times identity |
| correlated | Two standard-Normal priors constrained by exp(-0.5*((x-y)/0.15)^2); both means 0, marginal variance 1.0225/2.0225, correlation about 0.978 | Default; existing `ProposalScheme(x,y)` joint prior resampling; diagonal random walk; aligned random walk |
| multimodal | Equal mixture of Normals with means -4/+4 and SD 0.75; mean 0 and positive probability 0.5 | Default broad prior resampling; local random walk of variance 0.5625; fixed 80% local / 20% default mixture |

The aligned correlated proposal uses **2.8 times the known analytic posterior covariance**. The diagonal proposal uses the same marginal increment variances but zero covariance. This isolates alignment, but it is a favorable, informed choice: the implementation does not estimate that covariance automatically. The existing joint-prior proposal samples both Normal randomness values independently before the common MH decision.

The multimodal target uses a prior of variance 25 with a compensating log constraint, and chains start in alternating positive/negative regions. Local moves have a positive theoretical probability of crossing the gap but explore modes poorly at this budget. Balanced initial regions can make a pooled mean look deceptively good; mode occupancy is therefore monitored separately.

## Fixed-budget results

Every row uses 48,000 retained draws in total. Acceptance and times are median per-run values. ESS is the minimum raw-scale mean ESS across the two monitored queries.

| Workload / proposal | Acceptance | API ms | Minimum mean ESS | Minimum mean ESS/s | Joint coverage |
| --- | --- | --- | --- | --- | --- |
| normal / default | 100.0% | 117.4 | 15,630 | 133,077 | 47/50 |
| normal / block | 35.8% | 131.1 | 6,259 | 47,457 | 49/50 |
| correlated / default | 15.4% | 106.5 | 48 | 453 | 23/50 |
| correlated / joint-prior | 13.3% | 118.7 | 3,294 | 27,499 | 47/50 |
| correlated / diagonal | 8.6% | 121.0 | 928 | 7,717 | 44/50 |
| correlated / block | 35.8% | 133.6 | 6,427 | 47,377 | 50/50 |
| multimodal / default | 27.5% | 108.7 | 7,243 | 66,373 | 48/50 |
| multimodal / block | 70.4% | 116.1 | 4 | 34 | 0/50 |
| multimodal / mixed | 61.8% | 115.7 | 1,358 | 11,502 | 48/50 |

On the correlated fixture, the aligned block's median rate was about **1.72x the existing joint-prior proposal** and 6.14x the diagonal random walk. It was slower in raw elapsed time than the default, but explored much more effectively. Do not describe the large numerical ratio against the failed default ESS as a calibrated speedup to equally trustworthy estimates: the default did not produce trustworthy precision at this budget.

Joint prior resampling already solves much of this fixture's difficulty. The new API should not receive credit for that existing capability. Conversely, on independent Normals the new block's ESS/s was only about 36% of the default's: a random walk is unnecessary when direct prior updates already work well.

## Adaptive stopping and time to precision

The cap remains 12,000 draws per chain. Successful time is the median API time among runs actually reporting `PrecisionReached`. A capped failure is **not** a time-to-precision observation, and no success speedup is assigned when the comparator never succeeds.

| Workload / proposal | Precision reached | Median draws/chain | Mean draws/chain | All-run API ms | Successful time ms | Joint coverage |
| --- | --- | --- | --- | --- | --- | --- |
| normal / default | 50/50 | 2,000 | 2,000 | 17.9 | 17.9 | 46/50 |
| normal / block | 50/50 | 2,000 | 2,040 | 23.0 | 23.0 | 47/50 |
| correlated / default | 0/50 | 12,000 | 12,000 | 309.9 | not reached | 23/50 |
| correlated / joint-prior | 50/50 | 4,000 | 4,040 | 49.0 | 49.0 | 48/50 |
| correlated / diagonal | 50/50 | 10,000 | 9,640 | 230.8 | 230.8 | 45/50 |
| correlated / block | 50/50 | 2,000 | 2,040 | 23.5 | 23.5 | 47/50 |
| multimodal / default | 50/50 | 2,000 | 2,000 | 17.8 | 17.8 | 48/50 |
| multimodal / block | 0/50 | 12,000 | 12,000 | 336.5 | not reached | 0/50 |
| multimodal / mixed | 50/50 | 8,000 | 7,560 | 145.2 | 145.2 | 47/50 |

The aligned correlated block reached precision in 50/50 runs, generally at the first checkpoint. Joint prior resampling also reached precision in 50/50, generally at 4,000 draws. The ratio of median successful times is about **2.09x** in favor of the aligned block. The default never reached the target, so its roughly 310 ms median is a capped unsuccessful runtime, not a valid comparator for a finite time-to-precision speedup.

The diagonal proposal eventually met the policy in every adaptive run, but required much more work and checkpoint time. Its 45/50 adaptive coverage and 44/50 fixed coverage also caution against assuming that finite-run diagnostics prove nominal coverage. Its fixed precision assessment passed in 49/50 runs; the adaptive policy can stop at an earlier passing checkpoint even if a later fixed assessment would not pass.

## Accuracy tradeoffs

Root mean squared error against analytic truth, using all 50 runs including failures:

| Workload / proposal: query | Fixed RMSE | Adaptive RMSE |
| --- | --- | --- |
| normal / default: x | 0.00759 | 0.0239 |
| normal / default: y | 0.00781 | 0.0176 |
| normal / block: x | 0.0113 | 0.0317 |
| normal / block: y | 0.0147 | 0.0292 |
| correlated / default: x | 0.0961 | 0.0963 |
| correlated / default: y | 0.0965 | 0.0967 |
| correlated / joint-prior: x | 0.0111 | 0.0215 |
| correlated / joint-prior: y | 0.0113 | 0.0220 |
| correlated / diagonal: x | 0.0239 | 0.0264 |
| correlated / diagonal: y | 0.0246 | 0.0271 |
| correlated / block: x | 0.00811 | 0.0198 |
| correlated / block: y | 0.00830 | 0.0213 |
| multimodal / default: positive | 0.00596 | 0.0157 |
| multimodal / default: x | 0.0473 | 0.126 |
| multimodal / block: positive | 0.104 | 0.104 |
| multimodal / block: x | 0.836 | 0.836 |
| multimodal / mixed: positive | 0.0127 | 0.0194 |
| multimodal / mixed: x | 0.101 | 0.155 |

Fewer retained draws generally increase point-estimate error even when the requested tolerance is met. Fifty repetitions cannot establish exact 95% coverage; adaptive joint coverage among successful strategies ranges from 45/50 to 48/50 here. The aligned correlated block covered 47/50 adaptively and 50/50 at the fixed budget. These are finite experiments under particular settings, not universal guarantees or an automatic validation of other posterior geometries.

## Counterexamples that matter

The local multimodal block had about 70% acceptance but **0/50 precision successes** and **0/50 joint interval coverage**. Some mode-occupancy intervals were unavailable because a chain never changed mode. Its high acceptance describes local movement, not global exploration. The default broad proposal was best for this fixture.

Mixing 20% default moves restored practical exploration in these runs and reached precision in 50/50, but remained much slower than the default alone: roughly 145 ms versus 18 ms median time to precision. A fallback is useful to avoid freezing unlisted variables and can help mode changes, but it does not guarantee an efficiency improvement.

The correlated default's 23/50 coverage and failed diagnostics reproduce the earlier bottleneck. The diagonal proposal's imperfect coverage despite frequent precision success shows that R-hat/ESS/width guards are safeguards, not a finite-sample theorem. Use broader independent validation, larger budgets, and better geometry where the application needs stronger assurance.

## Correctness and release boundary

Regression tests cover invalid covariance and targets, an independent value-space Cholesky/log-density oracle with unequal prior scales, early and dependent-evidence rejection rollback, conjugate posterior moments, correlated posterior moments and stopping, mixtures with variables outside the block, runtime ownership failures, unsupported composition, worker cleanup, and exact fixed/adaptive/ordinary-MH agreement on a deterministic initialization fixture. The existing modernization regressions remain required.

This supports shipping a documented **fixed-covariance, opt-in proposal for the declared Normal surface**. It does not establish support for adaptive MCMC, arbitrary element types, hidden callback dependencies, automatic block selection, or generally effective multimodal sampling. The current default is unchanged.

Next candidates are broader posterior geometries and covariance-selection guidance, followed by separately designed warm-up-only adaptation, lower-cost stopping diagnostics, and reduced trace-memory pressure. Adaptation is not silently introduced by this release.

Related: [blocked-proposal user guide](BLOCKED_PROPOSALS.md), [multi-chain diagnostics](MULTI_CHAIN_MCMC.md), [stopping policy](STOPPING_CRITERIA.md), and [migration](MIGRATION.md).
