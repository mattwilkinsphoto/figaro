# MCMC reliability audit: what improved and what did not

## Outcome

The modern.8 MCSE floor fixes a demonstrated inconsistency: the former stopping rule could declare a mean precise while Figaro's other existing MCSE estimate implied a wider interval. The new rule uses the larger estimate and reports failed checks explicitly. It does **not** resolve unreliable tail exploration or establish general finite-sample coverage.

The curved-target audit still shows severe undercoverage for default and joint-prior proposals, including runs reporting `PrecisionReached`. Do not use that label alone to justify inference on such models. Reparameterization is the strongest positive control here; manual and calibrated blocks help, but early stopping still needs model-specific validation. No confidence thresholds were relaxed and no failed pilots were retried or silently omitted.

## Design and independently known target

For independent standard Normal variables Z and E, define X = Z and Y = 0.4(Z² - 1) + 0.5E. Five monitored means have analytic truths:

| Observable | Mean | Variance of the observable |
| --- | --- | --- |
| X | 0 | 1 |
| Y | 0 | 0.57 |
| X² | 1 | 2 |
| Y² | 0.57 | 1.8786 |
| Indicator of \|X\| > 2 | 0.045500263896358334 | p(1-p) |

The fourth-moment check is E[Y⁴] = 1.536 + 0.48 + 0.1875 = 2.2035, so Var(Y²) = 2.2035 - 0.57². The density-form model starts from independent standard Normal X,Y priors and adds log constraint `0.5 * (y*y - ((y - 0.4*(x*x-1))/0.5)^2)`. Multiplying that constraint by the priors gives the same target, up to a constant normalizer. No target parameters are fitted to observed coverage.

Six strategies use 60 preassigned seeds each:

| Strategy | Meaning |
| --- | --- |
| `iid` | Direct independent draws from the algebraic target, outside Figaro MH |
| `reparameterized` | Figaro model in independent Z,E coordinates; joint prior redraws give independent transformed draws, with 100% acceptance |
| `default` | Existing element-at-a-time proposals in density-form X,Y coordinates |
| `joint-prior` | Existing joint independent-prior proposal for X,Y in density form |
| `manual` | Gaussian random-walk block with fixed diagonal covariance (1, 0.57) |
| `calibrated` | Separate 6000-draw-per-chain joint-prior pilot, 2000 warm-up, unchanged calibration defaults fitted to raw X,Y only; frozen production covariance |

There are four chains, two workers, 2000 discarded warm-up draws per MH chain, and at most 48000 retained draws per chain. This cap is four times the earlier calibration audit's 12000. The precision policy uses nominal 95% joint confidence with Bonferroni adjustment over five means, 0.15 posterior-SD-relative full width, minimum 2000 draws, and checkpoints every 2000. Other guards retain their defaults. Production seed is `141011 + round * 7919` for rounds 0-59; pilot seed is production seed XOR `0x5deece66d`. Direct IID chain streams use `SplittableRandom` to assign seeds to `java.util.Random`.

For each strategy/seed, the harness generates one complete fixed trace and evaluates both rules on identical prefixes. `legacy-batch` reconstructs the former batch-only width and guards in validation-only code; `mcse-floor` uses current production assessment. It records each rule's first passing checkpoint (or cap), plus fixed checkpoints at 2000, 12000, and 48000. Thus differences between rules are not caused by different proposals or random draws. Different strategies share seed labels, not identical physical trajectories.

This is **not a timing benchmark**: all transitions are generated before prefix replay. No wall-clock speedup or time-to-precision claim follows from these counts. The three disjoint strategy shards ran in isolated JVMs against immutable copies of the same compiled candidate, before its snapshot label advanced from modern.7 to modern.8; the production mathematics was unchanged afterward. All shards completed. The checked CSV contains 13944 query/rejection rows across 720 strategy/seed/rule groups, corresponding to 360 attempted strategy/seed experiments, not 720 independent experiments.

## Paired coverage and stopping results

“Joint coverage” means all five reported intervals contain their analytic truths. Coverage below includes every available production run, even if it failed precision checks; numeric intervals on failed runs are diagnostic evidence, not certified intervals. Stopped coverage uses each rule's own stopping prefix or cap. `Precision reached` counts all attempted seeds, so pilot failures remain in its denominator. Median draws is per chain among available productions, including capped runs. The calibrated coverage denominators are conditional on its 48 accepted pilots; the 12 rejected pilots have no production interval.

| Strategy / rule | Pilot rejected | Joint coverage at 2k / 12k / 48k | Stopped joint coverage | Precision reached | Median stopping draws |
| --- | --- | --- | --- | --- | --- |
| iid / legacy-batch | 0/60 | 60 / 58 / 56 of 60 | 60/60 | 60/60 | 2000 |
| iid / mcse-floor | 0/60 | 60 / 59 / 58 of 60 | 60/60 | 60/60 | 2000 |
| reparameterized / legacy-batch | 0/60 | 59 / 56 / 56 of 60 | 59/60 | 60/60 | 2000 |
| reparameterized / mcse-floor | 0/60 | 59 / 56 / 57 of 60 | 59/60 | 60/60 | 2000 |
| default / legacy-batch | 0/60 | 18 / 24 / 25 of 60 | 26/60 | 54/60 | 20000 |
| default / mcse-floor | 0/60 | 33 / 38 / 39 of 60 | 29/60 | 34/60 | 46000 |
| joint-prior / legacy-batch | 0/60 | 29 / 31 / 27 of 60 | 22/60 | 59/60 | 6000 |
| joint-prior / mcse-floor | 0/60 | 36 / 33 / 33 of 60 | 24/60 | 54/60 | 7000 |
| manual / legacy-batch | 0/60 | 46 / 55 / 58 of 60 | 48/60 | 60/60 | 8000 |
| manual / mcse-floor | 0/60 | 56 / 56 / 58 of 60 | 48/60 | 60/60 | 10000 |
| calibrated / legacy-batch | 12/60 | 41 / 43 / 45 of 48 | 40/48 | 48/60 | 9000 |
| calibrated / mcse-floor | 12/60 | 41 / 47 / 46 of 48 | 44/48 | 47/60 | 12000 |

For clarity, here is coverage **only among runs labeled `PrecisionReached`**. These are selected subsets that differ between rules, not matched comparisons of the same successful runs:

| Strategy | Former rule | MCSE floor |
| --- | --- | --- |
| iid | 60/60 | 60/60 |
| reparameterized | 59/60 | 59/60 |
| default | 22/54 | **5/34** |
| joint-prior | 21/59 | **18/54** |
| manual | 48/60 | 48/60 |
| calibrated | 40/48 | 43/47 |

The default proposal's sharply worse conditional proportion must not be hidden by the improved overall stopped coverage. The stricter rule changes which runs receive a success label; wide intervals at the cap can cover the truth while failing the precision target. The data do not support a claim that successful stops are calibrated, or that the safeguard improves every coverage measure. Fixed-prefix widening and “never stop earlier” are deterministic invariants; coverage at a selected stopping time is a different question.

The independent controls' known-variance Normal intervals jointly cover 60/59/58 of 60 (`iid`) and 56/55/57 of 60 (`reparameterized`) at 2k/12k/48k. These intervals use `sqrt(known observable variance / total draws)` and the same critical value. They are independent variance benchmarks, not exact finite-sample intervals for squared values or event indicators. Sixty repetitions give limited resolution; small count differences do not establish superiority or exact nominal coverage.

## What explains the failures?

At the fixed cap, the Y² error shows both exploration bias and MCSE underestimation:

| Strategy | Mean error | Empirical error SD across seeds | RMS batch MCSE | RMS raw-mean ESS MCSE |
| --- | --- | --- | --- | --- |
| iid | 0.00012 | 0.00319 | 0.00314 | 0.00314 |
| reparameterized | 0.00015 | 0.00318 | 0.00312 | 0.00313 |
| default | -0.04042 | 0.05624 | 0.02504 | 0.05693 |
| joint-prior | -0.02642 | 0.05490 | 0.02267 | 0.06788 |
| manual | -0.00132 | 0.01848 | 0.02033 | 0.02190 |
| calibrated (48 accepted pilots) | 0.00095 | 0.03320 | 0.02487 | 0.02877 |

The direct and reparameterized controls agree with analytic moments and error scale. For default/joint-prior, Y² is systematically underestimated across these seeds and batch MCSE is much smaller than observed run-to-run variability. Taking the larger estimator addresses that discrepancy but cannot remove bias from regions the sample rarely visits. Matching an aggregate error SD does not establish per-run interval calibration.

There is also an analytic proposal weakness. On the ridge `y = 0.4*(x*x-1)`, log(target / joint-prior proposal), apart from a constant, is `0.5*y*y`, growing quartically in x. The proposal has too-thin tails there. Moving from ridge point (4,6) to (0,-0.4) has MH log acceptance ratio -17.92, approximately 1.65e-8 acceptance probability. Such states can be hard to reach and slow to leave. This is a proposal/target mismatch, not evidence that the MH proposal correction should be removed.

An independent regression checks the actual MH score/proposal ratio against a direct target-plus-proposal log-density oracle at that difficult transition, including rejection rollback. Another checks reparameterized Figaro draws against analytic first/second moments and tail probability, with all proposals accepted. These controls isolate a real geometry problem; they do not prove every MH implementation path is defect-free.

## Reproduce and validate

From the repository root:

```sh
sbt "examples / Compile / runMain com.cra.figaro.example.McmcReliabilityValidation 60 48000 2" > reliability.log
python3 -B tools/summarize_reliability.py reliability.log --repetitions 60 --max-draws 48000
python3 -B tools/summarize_reliability.py docs/mcmc-reliability-results.csv --repetitions 60 --max-draws 48000
```

The run takes substantial time. For disjoint process shards, add a fourth argument such as `iid,reparameterized`, then supply all three complete logs to the summary command. Defaults/argument contracts are in the [examples reference](../FigaroExamples/README.md#mcmc-reliability-examples). `--output PATH` optionally writes normalized CSV; `--acl-script PATH` invokes a Windows access-grant hook immediately after writing when your workspace requires one. No machine-specific path is needed in the source.

The summarizer rejects partial/missing/duplicate groups, mismatched prefixes, narrower candidate intervals, candidate-only successful fixed checks, earlier candidate stops, inconsistent failure reasons, and invalid decision/coverage records. It reports pilot failures and success-conditional coverage explicitly. It cannot prove an arbitrary input log was produced by this executable; source and seeded regression tests remain part of the evidence. CI smoke-runs one repetition and validates the complete checked dataset; it does not repeat the whole 60-seed audit on each push.

Eight new Scala regressions cover the target/proposal oracle, reparameterized control, independent batch arithmetic including leftover draws, an optimistic-batch AR fixture, width/decision invariants, failure reasons, paired checkpoint subsets, and extreme unit scaling/invalid estimators. All 121 modernization tests passed locally. Five new standard-library Python tests exercise audit completeness, sharding, corrupt measurements, paired invariants, and rejection denominators. Existing documentation and CI checks remain separate gates.

## Remaining work and practical guidance

Treat hard curved/multimodal models as requiring model-specific validation. Compare fixed budgets and independent seeds; monitor second moments and scientifically relevant events, not only a near-zero mean. Reparameterize when equivalent coordinates can be justified, then validate under the actual evidence. Manual or calibrated covariance blocks cannot straighten arbitrary curvature or discover every mode. Do not loosen diagnostics simply to obtain a successful stop label.

The next useful research milestone is geometry-aware sampling/reparameterization with independent target oracles and the same fixed-versus-stopped audit, followed by broader tail/multimodal stress tests. More threads would execute the same flawed exploration faster; they would not resolve these reliability gaps. The current changes are a bounded diagnostic safeguard, not completion of general MCMC reliability certification.

Related: [user guide](MCMC_RELIABILITY.md), [stopping policy](STOPPING_CRITERIA.md), [earlier calibration audit](PROPOSAL_CALIBRATION_VALIDATION.md), [raw results](mcmc-reliability-results.csv), and [migration notes](MIGRATION.md).
