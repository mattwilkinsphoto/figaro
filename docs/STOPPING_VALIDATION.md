# Stopping-policy validation

This report tests whether Figaro's opt-in mean-precision stopping policy saves useful work without silently treating poorly mixed chains as successful. It accompanies the `6.0.0-modern.5-SNAPSHOT` stopping milestone. It is empirical evidence for these fixtures, not certification of arbitrary models or exact finite-sample coverage.

## Reproduce in three steps

1. Build with the JDK, Scala, and sbt versions in [Building](BUILDING.md).
2. Run the paired experiment:

   ```sh
   sbt "examples / Compile / runMain com.cra.figaro.example.StoppingCriteriaValidation 50 12000 4"
   ```

3. Keep lines beginning with `validation,`, exclude rounds -2 and -1, and compare the fixed/adaptive rows by workload, round, and query. The [recorded CSV](stopping-validation-results.csv) contains the header and all 900 measured per-query rows.

The public example's `main(args: Array[String]): Unit` accepts repetitions (default 20), maximum retained draws per chain (default 12,000), and workers (default 4). It requires positive repetitions, at least 2,000 maximum draws, and 1-4 workers. Invalid numeric strings raise `NumberFormatException`; invalid bounds, a leaked worker, or a false success on the trapped control raise `IllegalArgumentException`. It prints CSV, does not create files, and does not publish anything. Example: `StoppingCriteriaValidation.main(Array("2", "4000", "2"))` is the CI smoke test. CI does not assert noisy timing or coverage thresholds from two repetitions.

## Experimental design

Recorded on September 5, 2026, using Windows, an AMD Ryzen 9 9950X (16 cores, 32 logical processors), JDK 17.0.4, Scala 3.9.0, sbt 2.0.8, and a 6 GiB maximum JVM heap. The recorded run used the stopping implementation before the snapshot-label bump; that bump did not alter sampling or validation code.

Every case uses four independent chains, four workers, 2,000 warm-up transitions per chain, and a 12,000 retained-draw cap per chain. The policy uses relative full width 0.15, nominal confidence 0.95, minimum 2,000 retained draws per chain, and a checkpoint every 2,000 draws. Other settings remain at their documented defaults: at least 20 batches, maximum R-hat 1.01, and minimum effective sample size 100 per chain. The minimum work means the earliest possible stop is 2,000 draws.

There are 50 measured root seeds, `87001 + round * 7919` for rounds 0-49. Two earlier full rounds warm up the JVM and are excluded. Fixed and adaptive runs use the same root seed and alternate execution order. Analytic posterior expectations supply the ground truth: a long fixed-budget run is a comparator, not a substitute for truth. Exact cross-run trace equality is not assumed for arbitrary complex graphs; simpler deterministic regression fixtures check prefix and worker-count invariance.

| Fixture | Target and monitored truth | Purpose |
| --- | --- | --- |
| normal | Standard normal; mean 0 | Well-mixing continuous baseline |
| likelihood | Standard normal prior times Gaussian likelihood centered at 1 with variance 1; posterior mean 0.5 | Log-likelihood constraint |
| conditioned | Standard normal restricted to positive values; mean sqrt(2/pi) | Hard condition |
| bernoulli | Prior probability 0.3, likelihood weights 0.8/0.2; posterior probability 0.24/0.38 | Discrete probability as an indicator mean |
| correlated | Bivariate Gaussian, zero means, correlation 1/(1 + 0.15^2), approximately 0.978 | Difficult default proposal and slow mixing |
| multimodal | Equal mixture of normals with means -4/+4 and SD 0.75; mean 0 and positive probability 0.5 | Two separated modes, both queries required |
| trapped | Equal mixture centered at -6/+6; mean 0; proposal deliberately freezes the mode | Non-ergodic negative control |

The correlated fixture has independent standard-normal priors with a constraint on their difference. The multimodal fixture uses a broad normal prior with variance 25 and a compensating log constraint to produce the stated mixture. Figaro's `Normal` second argument is **variance**, not SD. The trapped fixture is deliberately invalid as a general-purpose sampler: half the chains begin in each mode, but none can change modes. Balanced starts can make its pooled mean look excellent despite invalid exploration.

Coverage means that the truth falls inside the reported mean interval, using its full width and excluding the stopping penalty. Multi-query intervals use the policy's Bonferroni adjustment; the table reports joint coverage of all monitored queries. Criteria require every query to pass, not just interval coverage.

API elapsed time includes model setup, warm-up, sampling, built-in diagnostics, and cleanup. Adaptive time also includes every precision checkpoint. The separate after-the-run precision assessment added to the fixed result for this experiment is excluded from fixed API time. Both methods use four workers: the speed ratio measures **early stopping versus fixed work**, not parallel versus serial execution.

## Results

Fixed runs always retain 12,000 draws per chain. Times below are medians in milliseconds, fixed / adaptive. Speed ratio is the median of 50 paired fixed/adaptive time ratios, so it need not equal the ratio of the two medians.

| Fixture | Adaptive precision reached | Mean adaptive draws/chain | API ms: fixed / adaptive | Paired speed ratio | Joint coverage: fixed / adaptive |
| --- | --- | --- | --- | --- | --- |
| normal | 50/50 | 2,000 | 72.6 / 12.0 | 6.02x | 45/50 / 48/50 |
| likelihood | 50/50 | 2,000 | 73.9 / 13.0 | 5.67x | 50/50 / 48/50 |
| conditioned | 50/50 | 2,000 | 71.4 / 12.6 | 5.71x | 48/50 / 46/50 |
| bernoulli | 50/50 | 2,000 | 55.1 / 10.5 | 5.31x | 49/50 / 47/50 |
| correlated | 0/50 | 12,000 | 106.3 / 304.6 | 0.35x | 24/50 / 24/50 |
| multimodal | 50/50 | 2,040 | 109.4 / 18.0 | 6.16x | 49/50 / 50/50 |
| trapped | 0/50 | 12,000 | 74.3 / 217.7 | 0.34x | 47/50 / 47/50 |

For the normal, likelihood, conditioned, and Bernoulli fixtures, all adaptive runs stopped at the first checkpoint. The mixture stopped there in 49 runs and at 4,000 draws in one run. These cases saved about 83% of retained draws; accounting for the shared warm-up, the reduction in nominal per-chain transitions is about 71%. Their observed speed ratios were approximately 5-6x on this host. Millisecond-scale, single-host measurements are sensitive to JIT behavior, garbage collection, scheduling, and model cost; they are not a promised production speedup.

Less work produces less precise estimates, even when the requested tolerance is satisfied. Root mean squared error against analytic truth makes that tradeoff explicit:

| Fixture: query | Fixed RMSE | Adaptive RMSE |
| --- | --- | --- |
| normal: x | 0.00544 | 0.00889 |
| likelihood: x | 0.00438 | 0.0105 |
| conditioned: x | 0.00482 | 0.0131 |
| bernoulli: p | 0.00361 | 0.00969 |
| correlated: x | 0.107 | 0.107 |
| correlated: y | 0.107 | 0.107 |
| multimodal: x | 0.0499 | 0.0990 |
| multimodal: positive | 0.00603 | 0.0117 |
| trapped: x | 0.00446 | 0.00446 |

Among well-mixing fixtures, adaptive joint coverage ranged from 46/50 to 50/50. Fifty repetitions are too few to establish exact 95% coverage; even a 48/50 result has an approximate 95% Wilson interval of 86.5%-98.9%. The fixed normal comparator covered only 45/50, which is also reported rather than hidden. The confidence level is an asymptotic design setting, not a guarantee that 95 out of every 100 finite experiments will cover.

### Where stopping does not help

The correlated fixture hit the cap in **all 50 runs**, with no false declaration of precision success. Its joint interval coverage was only 24/50 for both methods. Poor mixing and insufficient long-run variance estimation make those numerical intervals unreliable; they must not be described as successful 95% uncertainty estimates. The returned `MaxDrawsReached` and failed R-hat/ESS guards are essential, not optional warnings. A larger budget and, especially, better proposals or reparameterization are the next remedies.

The trapped control also hit the cap in every run. Its apparently reassuring mean and 47/50 coverage result are consequences of deliberately balanced, frozen modes, not evidence of valid inference. The mixing guards correctly rejected it. This does not establish that the diagnostics can detect every form of trapping, especially if all chains start in the same missed mode.

For both unsuccessful fixtures, repeated diagnostic work made adaptive execution roughly 2.9x slower than fixed execution. Raising `checkEvery` can reduce checkpoint overhead, but cannot make a poor proposal mix. For a known difficult model, first validate exploration and choose a suitable proposal; enable precision stopping after that groundwork.

## Interpretation and release boundary

The results support shipping the policy as **opt-in scalar-mean stopping with explicit unsuccessful termination**, alongside the existing fixed-budget API. They do not support automatic conversion of all fixed runs, a universal speedup claim, quantile/tail precision, or KL-based automatic MCMC convergence claims.

The statistical checks are complemented by deterministic tests for likelihood-ratio direction, rounded terminal thresholds, invalid input, exact sampling prefixes, all-query decisions, worker-count independence, cleanup, and trapped/degenerate traces. Gaussian TSPRT calibration experiments are summarized separately in [Stopping criteria](STOPPING_CRITERIA.md); this benchmark evaluates the MCMC precision policy, not that independent-observation test.

Remaining work includes better proposals for strongly correlated targets, broader nonstationary and multimodal validation, larger coverage experiments across tolerances, and lower-cost checkpoint diagnostics. Early stopping still preallocates trace buffers at the maximum budget, so it does not solve peak-memory scaling.

Related: [Stopping criteria and API examples](STOPPING_CRITERIA.md), [multi-chain MCMC](MULTI_CHAIN_MCMC.md), [parallel performance](PARALLEL_PERFORMANCE.md), and [migration changes](MIGRATION.md).
