# Statistical validation: correctness, Monte Carlo accuracy, and false alarms

## Overview

This test/research milestone investigates intermittent legacy continuous-distribution
test failures. It separates distribution correctness, posterior approximation error,
finite-data estimation effects, and significance-test false alarms. It does **not**
loosen a tolerance, remove a legacy test, or declare all historical failures explained.
The original two-RNG experiment retains explicit legacy streams. The companion
[RNG milestone](RNG_ASSESSMENT.md) subsequently changes the production default to LXM.

The immediate fix is in `TTestResult.errorMessage`: standard error is now sample
standard deviation divided by the square root of the replicate count. Previously it
printed variance divided by that square root. The Apache t-test decision was already
using the statistics object correctly and is unchanged.

## Quick start (three steps)

From the repository root with Java 17 and sbt 2:

1. Run deterministic checks: `sbt "figaro / Test / testOnly com.cra.figaro.test.modernization.StatisticalValidationTest"`.
2. Reproduce the study: run the following commands, retaining stdout in one log.
   ```text
   sbt "figaro / Test / runMain com.cra.figaro.test.modernization.StatisticalValidationStudy kernel"
   sbt "figaro / Test / runMain com.cra.figaro.test.modernization.StatisticalValidationStudy graph"
   ```
3. Summarize that log, or inspect the checked-in complete evidence:
   ```text
   python -B tools/summarize_statistical_validation.py docs/statistical-validation-results.csv
   ```

For independent numerical references, install **research-only** `numpy==2.3.5` in
an isolated Python environment, then run
`python -B -m unittest discover -s tools -p 'test_statistical_validation_reference.py' -v`.
NumPy is not a Figaro runtime or consumer dependency.

## What the legacy tests were asking

The Gamma and Dirichlet tests each generate 200 observations, fit unknown parameters
with independent Uniform(0,10) priors using 2,000 importance draws, and repeat ten
times. A t-test then compares replicate posterior means with the generating parameters.
That is not solely a test of Monte Carlo correctness: finite observations and the
prior affect the exact posterior mean, whose repeated-data expectation need not equal
the fixed generating parameter either. More importance draws eliminate neither issue.

The older multivariate-Normal sampling test examines two means, two variances and a
covariance using ten replicates of 10,001 draws. Multiple correlated comparisons occur
inside one reported test case. The suite's number of ScalaTest cases is not its number
of independent statistical tests. A 5% individual rejection rate does not mean a 5%
suite-level false-alarm rate. [NIST multiple-comparison guidance](https://www.itl.nist.gov/div898/handbook/prc/section4/prc47.htm).

## Fixed-data posterior references

Two new, fixed, synthetic datasets are defined by integer-shape gamma generation as
sums of exponential variates. Gamma data use seed 104729, shape 2, scale 2; Dirichlet
data use seed 130363 and normalized independent gamma variates with shapes 1,2,3.
Both contain 200 observations. The fixture RNG is explicitly Java's documented
48-bit recurrence, independently reproduced in Python. It defines the data only;
it does not establish the accuracy of the integration or validate the RNG.

Python uses NumPy Gauss-Legendre nodes, Python `math.lgamma`, sufficient statistics,
positive tensor weights and log scaling over the **entire** (0,10)^d prior box.
It does not call Figaro. Orders 144 and 216 agree within 1e-8 for each posterior mean
and SD; lower orders 64 and 96 expose under-resolution. This is numerical convergence
evidence, not a certified integration-error bound.

| Parameter | Generating value | Fixed-data posterior mean | Posterior SD |
| --- | --- | --- | --- |
| Gamma shape | 2 | 2.1078699732 | 0.1967401706 |
| Gamma scale | 2 | 1.8447293469 | 0.1970468052 |
| Dirichlet alpha1 | 1 | 0.8410161507 | 0.0608399844 |
| Dirichlet alpha2 | 2 | 1.9109507552 | 0.1434315062 |
| Dirichlet alpha3 | 3 | 2.6403392787 | 0.2005600808 |

These are not reconstructed historical failing datasets. Their deviations from the
generating parameters demonstrate why a fixed-data inference check needs a posterior
reference; they do not quantify repeated-data estimator bias.

## Design and results

Before the measured runs, the harness fixed 30 seeds `1009 + 7919*i`, i=0..29;
budgets 2,000 / 20,000 / 200,000; and RNGs `Random` / `L64X128MixRandom`.
Each larger budget replays a prefix of the same stream. Different RNGs do **not**
produce matched draws merely because their numeric seed is equal. No seeds were
discarded or replaced after observing accuracy. The descriptive accuracy target is
absolute mean error <= 0.1 posterior SD, not a new CI acceptance tolerance.

The **kernel** study implements prior-proposal self-normalized importance sampling
using sufficient-statistic log likelihoods. It isolates proposal efficiency without
allocating 200 observed elements per draw. It is not a benchmark of the full Figaro
graph. The **graph** study uses the actual CompoundGamma/CompoundDirichlet observed
elements and Importance implementation at 2,000 draws for the first three seeds. It
checks finite per-draw log weights against sufficient-statistic likelihoods and checks
the public posterior expectation against independently accumulated sample weights.
Graph traversal consumes randomness differently, so graph and kernel estimates need
not match for the same seed.

All 915 parameter rows (366 experiments) are retained in
[the evidence CSV](statistical-validation-results.csv). The summarizer rejects missing,
extra, duplicate, nonfinite and inconsistent rows.

| Model | Draws | Median ESS, Random | Median ESS, LXM | Median largest weight, Random / LXM |
| --- | --- | --- | --- | --- |
| Gamma | 2,000 | 5.07 | 4.59 | 32.6% / 34.0% |
| Gamma | 20,000 | 43.00 | 44.63 | 4.45% / 4.44% |
| Gamma | 200,000 | 444.20 | 439.33 | 0.44% / 0.45% |
| Dirichlet | 2,000 | 1.002 | 1.002 | 99.91% / 99.92% |
| Dirichlet | 20,000 | 2.32 | 2.14 | 59.65% / 60.45% |
| Dirichlet | 200,000 | 9.95 | 9.53 | 20.38% / 20.10% |

At 200,000 draws, both generators met the descriptive accuracy target for both Gamma
parameters in all 30 runs. Dirichlet remained difficult: Random met it in 11/7/7
runs for alpha1/2/3; LXM in 8/16/7. This small, two-dataset study does not establish
one generator as statistically superior. It clearly shows that changing the RNG
does **not** resolve the prior/posterior mismatch.

The actual Dirichlet graph runs had median ESS 1.005 and median largest weight 99.77%.
None of their nine nominal 95% Monte Carlo intervals contained the numerical reference.
Across the 30 kernel runs at 2,000 draws, coverage counts were 0/2/3 of 30 for Random
and 4/3/5 for LXM. Those are intervals for **Monte Carlo mean-estimation error**, not
posterior credible intervals or repeated-data parameter coverage. Counts from only
30 seeds have substantial binomial uncertainty and are correlated across parameters.

The failure mode is important: when one draw receives essentially all the weight,
the usual plug-in Monte Carlo standard error can approach zero despite a badly wrong
estimate. ESS and maximum weight must be considered alongside MCSE. ESS itself is
not a certificate and does not measure how well completely missed modes were explored.
[Importance-sampling ESS limitations](https://arxiv.org/abs/1809.04129).

No density-to-log underflows were observed in these six graph experiments. That does
not clear the broader legacy log-density audit, particularly for extreme parameters.

## Harness interface and three common uses

The harness lives in test sources, is not shipped as a public library API, and takes
exactly one mode. All modes return normally after reporting results; invalid arguments,
invalid weights or likelihood/aggregation mismatches fail the process. Poor measured
accuracy is reported rather than suppressed or retried until it passes.

| Mode | Use | Output |
| --- | --- | --- |
| `kernel` | Compare proposal efficiency and RNGs across the complete seed/budget grid | 900 `SV` parameter rows |
| `graph` | Validate the real observed-element/inference path | 15 `SV` parameter rows |
| `smoke` | Check that both kernel/RNG paths execute; not a full study | First seed, 2,000 draws |
| `calibration` | Isolate significance-test false alarms under a known normal null | Two `CALIBRATION` rows |
| `mvnormal` | Repeat the legacy five-moment design using the production Gaussian kernel | One `MVNORMAL` row |

1. **Investigate an inaccurate posterior:** run `graph`, inspect mean error, ESS and
   maximum weight. A tiny MCSE with ESS near one is a warning, not strong precision.
2. **Choose a work budget:** run `kernel`, summarize the full grid, compare RMSE and
   frequency of attaining the same accuracy target. More draws alone may be wasteful;
   the Dirichlet case motivates a better proposal or a well-diagnosed alternative sampler.
3. **Investigate intermittent CI failures:** run `calibration` and `mvnormal`, and
   separate expected null rejection from an incorrectly specified expected answer.
   For example:
   ```text
   sbt "figaro / Test / runMain com.cra.figaro.test.modernization.StatisticalValidationStudy calibration"
   sbt "figaro / Test / runMain com.cra.figaro.test.modernization.StatisticalValidationStudy mvnormal"
   ```

Each `SV` row identifies mode, family, RNG, seed, draws, zero-based parameter,
posterior mean estimate, signed reference error, asymptotic MCSE, weight ESS,
largest normalized weight, nominal 95% coverage flag, accuracy flag, elapsed seconds,
and count of finite-reference / negative-infinite graph weights. Diagnostics and time
repeat across parameters; do not sum those repeated times. Kernel underflow fields
are zero placeholders because that mode has no density-to-log observation path.
Timing includes JVM warm-up/order effects and is **not** controlled speedup evidence.

`summarize_statistical_validation.py INPUT [--export OUTPUT]` validates the complete
kernel+graph grid, prints median concentration diagnostics and parameterwise RMSE /
coverage / accuracy counts, and optionally extracts a clean CSV. It intentionally
rejects smoke-only or partially completed input. It uses the Python standard library.

## False-alarm controls and next work

The normal-null control uses 1,000 independent simulated families of 20 tests, each
testing the mean of ten normal draws. Random rejected 998/20,000 individual nulls;
LXM rejected 1,009/20,000. Both had at least one rejection in 655/1,000 families.
Bonferroni alpha/20 reduced family rejections to 54 and 55 respectively. These are
simulation counts, not guarantees or a calibration of the complete legacy suite.

The production-Gaussian five-moment repetition retained 31/500 individual rejections,
23/100 families with a rejection, and 6/100 with a Bonferroni-adjusted rejection.
This uses legacy seeded fixture streams and the production Gaussian kernel, not
the full legacy ScalaTest suite. It supports treating the original isolated covariance
failure as plausibly a false alarm, not as proof of a defective Gaussian sampler.

Keep deterministic algebra, likelihood and aggregation regressions in required CI;
retain stochastic studies as complete, reproducible evidence. A future redesign of
legacy statistical gates should predeclare its family of comparisons and error budget
(for example Holm/Bonferroni), test power against meaningful injected defects, and
use valid posterior references. Do not indiscriminately lower alpha or pick a passing seed.

The strongest follow-on priority is improved inference for concentrated posteriors:
evaluate a pilot-fitted, defensive proposal against current importance sampling and
existing diagnosed MCMC/slice alternatives at matched accuracy. Keep proposal training
separate from evaluation; preserve support and account correctly for proposal density.
Repeat across additional fixed datasets and independent seed batches before making
coverage claims. No adaptive proposal or automatic sampler switch is delivered here.

## Related and migration implications

- [Inference health](INFERENCE_HEALTH.md): opt-in per-run warnings, licensed Pareto-tail
  fitting, raw MCSE limitations and a complete 60-run detection regression on these fixtures.
- [RNG assessment](RNG_ASSESSMENT.md): replacement recommendation and compatibility gates.
- [Parallel performance](PARALLEL_PERFORMANCE.md): worker isolation is not a remedy for weight collapse.
- [Distribution constructions acceptance](DISTRIBUTION_CONSTRUCTIONS_ACCEPTANCE.md): original legacy observations.
- [Roadmap](../ROADMAP.md): this reliability work precedes further distribution breadth.

The statistical investigation changes test diagnostics and adds research/CI tooling;
the accompanying RNG migration does change production sampling sequences and requires
recompiling consumers. The unchanged-baseline failure and one passing paired
seed from the earlier milestone remain limited evidence; this study is not an exhaustive
baseline/candidate, multi-dataset post-mortem of the historical failures.

The subsequent five-backend study retains [2,250 parameter rows](rng-statistical-results.csv)
across 30 seeds and three budgets, including PCG, MT and Xoshiro. Every backend had
median Dirichlet ESS near one at 2,000 draws and around ten at 200,000 draws. This
strengthens the proposal-efficiency diagnosis without claiming RNG certification.
Run `StatisticalValidationStudy backends` and pass its output to the same summarizer;
it validates that complete grid separately from the historical kernel+graph grid.
