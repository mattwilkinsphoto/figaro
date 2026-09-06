# Pilot-calibration validation

## What this establishes

The new [calibration helper](PROPOSAL_CALIBRATION.md) correctly computes and freezes a candidate covariance from a separate pilot. It does **not** make a usable pilot automatic, guarantee better end-to-end performance, or validate interval coverage on every posterior geometry. This broader experiment exposed substantial remaining limitations; calibration stays opt-in, with no default proposal change.

In particular, all six-dimensional pilots were rejected, and the curved target showed serious finite-run undercoverage even when the stopping policy frequently passed. Those are adverse results, not omitted runs. This milestone is not approval for unattended use on arbitrary targets.

## Reproduction and design

```sh
sbt "examples / Compile / runMain com.cra.figaro.example.ProposalCalibrationBenchmark 30 12000 4 6000"
python -B tools/summarize_calibration.py benchmark.log --repetitions 30
```

Capture the first command's output as `benchmark.log` using your environment's logging facility. The [summary tool](../tools/summarize_calibration.py) accepts the log path and required positive `--repetitions`. It prints a Markdown summary and returns a failing exit status on missing/duplicate run/query groups or invalid total timing. Optional `--output results.csv` writes normalized non-warm-up CSV; an existing non-calibration file is refused. Optional `--acl-script path` runs a PowerShell grant hook immediately after writing the file. It uses only Python 3.10+ standard-library modules. Its tests run with `python -B -m unittest discover -s tools -p 'test_summarize_calibration.py'`.

The checked-in [raw results](proposal-calibration-results.csv) contain **6,870 query/rejection rows in 1,440 complete run groups**. Each of 30 seeds covers six geometries, four strategies, and fixed/precision-stopped production. Round -1 warms the JVM and is excluded. Strategies rotate order; fixed/precision execution order alternates. Pilot and production root seeds differ; both construct fresh model graphs. Existing multi-element initialization-order limitations preclude claims of identical random-number coupling across separately allocated graphs.

Recorded September 6, 2026 on Windows, AMD Ryzen 9 9950X (16 cores/32 logical processors), JDK 17.0.4, Scala 3.9.0, sbt 2.0.8, 6 GiB maximum heap. No competing benchmark/build was deliberately run during measurement. Timing is descriptive on one host, not a portable performance guarantee. The benchmark process loaded the modern.6 build label before the branch moved to modern.7; the measured calibration and proposal implementations are unchanged by that label update.

Each run uses four chains/workers and 2,000 discarded warm-up transitions per chain. The pilot retains 6,000 draws per chain using **existing joint-prior proposals**, then attempts calibration with unmodified defaults: multiplier 1, diagonal shrinkage 0.05, maximum R-hat 1.01, and bulk/tail/raw-mean ESS of at least 100 per chain. The pilot fits raw coordinate values only. Rejected fits do not start production and are not retried.

Production has its own 2,000-transition warm-up and up to 12,000 retained draws per chain. The precision policy uses relative **full interval width** 0.15, nominal joint confidence 0.95, minimum 2,000 draws, and 2,000-draw checkpoints. All coordinate means **and second moments** are monitored, with the policy's multiplicity adjustment across all 2*d queries. Fixed runs retain 12,000 regardless of diagnostics.

### Target distributions and comparators

| Geometry | Analytic target |
| --- | --- |
| independent-2 | Two independent standard Normals |
| correlated-2 | Bivariate Normal, unit marginal variance, correlation 0.5 |
| narrow-2 | Bivariate Normal, unit marginal variance, correlation 0.98 |
| scaled-2 | Bivariate Normal, marginal SDs 0.02 and 20, correlation 0.9 |
| correlated-6 | Six-dimensional Normal, unit marginal variances, all off-diagonal correlations 0.8 |
| banana-2 | x = Z, y = 0.4*(Z^2 - 1) + 0.5*E for independent standard Normals Z/E; means zero, variances 1 and 0.57 |

The implementation represents these targets with constant-parameter Normal elements plus an explicit target/prior log-density correction. For the banana transformation the Jacobian is constant; its normalizing constant cancels in MH. Means and second moments follow analytically, independently of sampled estimates.

Comparators are existing default MH, existing joint-prior resampling of all block elements, a **manual oracle covariance**, and the calibrated candidate. The manual proposal uses the analytically known target covariance with multiplier 1 and no shrinkage; it is a geometry-informed comparator, not an automatically available or universally optimal proposal. For the banana its covariance is diagonal and cannot follow the curved ridge. These fixtures/settings differ from the [previous block benchmark](BLOCKED_PROPOSAL_VALIDATION.md); do not transfer its speedup ratios to this experiment.

### Metric definitions

- Joint coverage means **every monitored analytic mean/second moment** lies inside its reported interval. Missing intervals count as not covered.
- Fixed efficiency is the median, over completed production runs, of the **minimum** raw-mean ESS across queries divided by total time.
- Calibrated total time includes the pilot runner, fit, and production API. The full pilot cost is charged separately to each fixed/precision comparison, representing either standalone use. Other strategies incur no pilot charge. Fixed-run post-hoc precision assessments are outside fixed API time; precision-run checkpoint assessments are inside its API time.
- Precision success counts against **all 30 attempts**, including pilot rejections. Successful total time is conditional on actual `PrecisionReached`; capped failures and rejected pilots are not successful time-to-precision results.
- Coverage denominators exclude rejected pilots because they produce no intervals, but include completed production runs that reached the cap. A `0/0` entry means no production result, not perfect coverage. Calibrated efficiency and coverage on accepted pilots are **selected-subset results**, not evidence about rejected pilots.

## Results

| Geometry / strategy | Pilot rejected | Fixed joint coverage | Fixed min ESS/total s | Precision success | Successful total ms | Precision joint coverage |
| --- | --- | --- | --- | --- | --- | --- |
| banana-2 / calibrated | 1/30 | 25/29 | 3050 | 25/30 | 541.8 | 22/29 |
| banana-2 / default | 0/30 | 15/30 | 1576 | 11/30 | 536.0 | 14/30 |
| banana-2 / joint-prior | 0/30 | 12/30 | 6339 | 26/30 | 108.4 | 10/30 |
| banana-2 / manual | 0/30 | 27/30 | 5453 | 28/30 | 353.4 | 24/30 |
| correlated-2 / calibrated | 0/30 | 28/30 | 9945 | 30/30 | 299.3 | 29/30 |
| correlated-2 / default | 0/30 | 30/30 | 16961 | 30/30 | 100.1 | 28/30 |
| correlated-2 / joint-prior | 0/30 | 27/30 | 28658 | 30/30 | 40.9 | 27/30 |
| correlated-2 / manual | 0/30 | 29/30 | 17217 | 30/30 | 109.9 | 29/30 |
| correlated-6 / calibrated | 30/30 | 0/0 | unavailable | 0/30 | not reached | 0/0 |
| correlated-6 / default | 0/30 | 3/30 | 174 | 0/30 | not reached | 11/30 |
| correlated-6 / joint-prior | 0/30 | 0/30 | 79 | 0/30 | not reached | 0/30 |
| correlated-6 / manual | 0/30 | 29/30 | 3121 | 30/30 | 1009.8 | 29/30 |
| independent-2 / calibrated | 0/30 | 28/30 | 10071 | 30/30 | 305.6 | 30/30 |
| independent-2 / default | 0/30 | 29/30 | 60518 | 30/30 | 36.7 | 27/30 |
| independent-2 / joint-prior | 0/30 | 29/30 | 166320 | 30/30 | 41.0 | 29/30 |
| independent-2 / manual | 0/30 | 28/30 | 17161 | 30/30 | 110.3 | 30/30 |
| narrow-2 / calibrated | 19/30 | 11/11 | 8589 | 11/30 | 263.5 | 11/11 |
| narrow-2 / default | 0/30 | 1/30 | 198 | 0/30 | not reached | 1/30 |
| narrow-2 / joint-prior | 0/30 | 23/30 | 2711 | 14/30 | 492.0 | 22/30 |
| narrow-2 / manual | 0/30 | 30/30 | 17452 | 30/30 | 109.6 | 30/30 |
| scaled-2 / calibrated | 5/30 | 25/25 | 9763 | 25/30 | 283.1 | 24/25 |
| scaled-2 / default | 0/30 | 20/30 | 1376 | 0/30 | not reached | 20/30 |
| scaled-2 / joint-prior | 0/30 | 23/30 | 4772 | 27/30 | 214.2 | 23/30 |
| scaled-2 / manual | 0/30 | 30/30 | 17468 | 30/30 | 109.6 | 29/30 |

## What a user should conclude

**Easy targets do not need calibration here.** For independent Normals, existing joint-prior resampling produced about 166,320 minimum mean ESS/s versus 10,071 pilot-inclusive for calibration. Calibration's production-only median was about 17,398 ESS/s, so removing pilot cost alone would not make it competitive. The moderate-correlation target also favored joint-prior resampling in end-to-end performance.

**The pilot is a real prerequisite.** The narrow target accepted only 11/30 pilots. All 11 resulting production runs reached precision with joint coverage, but that does not establish a 30/30 usable workflow. The six-dimensional joint-prior pilot was rejected in every repetition. The manually informed block reached precision in all 30 six-dimensional runs, showing a geometry opportunity that this pilot strategy failed to recover. Do not relax thresholds merely to fill this gap.

**Shrinkage and setup have costs.** The narrow target's calibrated production-only minimum ESS/s was about 13,673 versus the manual block's 17,452; pilot-inclusive calibration fell to 8,589. Shrinking off-diagonals can widen a narrow proposal in the wrong direction. The scaled target's calibrated production-only median was about 16,024, falling to 9,763 when pilot cost was included. Median pilot-plus-fit costs across all attempts were about 195 ms (independent), 189 ms (moderate correlation), 136 ms (narrow), 173 ms (scaled), 301 ms (six-dimensional), and 182 ms (banana). Reuse may amortize setup only when model/evidence/coordinate semantics remain valid; this experiment does not measure a reuse workload.

**Curved-target uncertainty remains a serious shortfall.** Calibrated banana production achieved joint coverage in only 22/29 precision runs, despite 25/30 total precision successes. Existing joint-prior resampling was faster but covered only 10/30 precision runs. High throughput or frequent stopping success therefore cannot be read as reliable inference. Scalar pilot checks and production width/ESS/R-hat guards are not a finite-sample coverage theorem, particularly for second moments and poorly explored tails. The raw rows include errors and estimates for further per-query analysis. Broader/longer validation and geometry-aware proposals remain necessary before relying on these intervals for such targets.

Thirty repetitions provide limited coverage precision even on favorable geometries. The benchmark does not establish exact 95% coverage, performance across machines, general multimodal exploration, or high-dimensional covariance accuracy.

## Correctness checks and release boundary

Eleven new regressions cover an independent covariance oracle, unequal units, exact symmetry/name ordering, explicit shrinkage of collinear data, malformed/short/nonfinite/stuck/separated/low-ESS pilots, numeric range failure, cancellation, analytic correlated moments, fresh production runs, worker-count agreement, fixed/precision prefixes on a deterministic fixture, and cleanup. All **113 modernization regressions** passed locally. Three Python summary tests ensure complete data and honest rejection denominators; existing documentation tests remain required. CI runs the new tests, example, and two-worker benchmark smoke check, plus the existing coverage and reproducible-packaging gates.

Supported: a detached, opt-in calibration utility for the existing constant-parameter Normal block surface, with explicit rejection and frozen production proposals. Not supported or claimed: online adaptation, automatic pilot selection/retries, guaranteed speedup, covariance certification, reliable arbitrary-geometry coverage, or global mode discovery. Existing defaults and `main` remain unchanged.
