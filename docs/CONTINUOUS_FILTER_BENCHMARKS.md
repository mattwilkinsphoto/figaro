# Continuous nonlinear and multimodal filtering validation

Completed pre-publication gate for **7.2.0**, now [published](RELEASE_7_2_STATUS.md).
The preserved baseline uses `bf6a2cd7`;
the subsequent numerical repair is compared with identical fixtures and protocol.
[Benchmark provenance](RESEARCH_REFERENCES.md#inf-12);
[numerical repair provenance](RESEARCH_REFERENCES.md#inf-13).

## Purpose and frozen protocol

The earlier finite-state example is not a continuous nonlinear benchmark. This
study compares Figaro's bootstrap, guided, auxiliary, calibrated auxiliary and
deliberately poor auxiliary strategies on continuous states. It is a correctness,
robustness and cost assessment, not a claim to reproduce a paper's performance or
beat other frameworks. Runtime code and refusal policies must remain unchanged
during this baseline. Preserve every failure and every seed.

### Models and data

All three use scalar continuous states and Gaussian observation noise, with
`h(x)=a*x*x`. Twenty observations per model are generated once with NumPy PCG64,
seed 20261022; raw states, noise-derived observations and parameters are saved.
They are synthetic fixtures, not supplied application data or copied datasets.

1. **growth:** Gordon/Salmond/Smith nonlinear growth equations, transition mean
   `.5*x + 25*x/(1+x*x) + 8*cos(1.2*(t-1))`, process variance 10, `a=.05`,
   observation sd 1. Initial `N(0,4)` follows the maintained particles example;
   this initial convention and newly generated data are not a literal replication
   of the original paper's experiment.
2. **growth-sharp:** same dynamics and initial law, observation sd .25. This is
   a declared stress variant, not a different model chosen after seeing results.
3. **persistent-modes:** initial equal mixture `N(-4,.25)` / `N(4,.25)`;
   random-walk transition variance .09; `a=.25`, observation sd .5. This is an
   independently constructed sign-ambiguity stress test, not a literature dataset.
   Symmetry makes posterior positive-side mass exactly .5 at every time, even
   though sample histories may lose a mode.

Time starts at zero: initial state is observed before any transition. Growth
transition to time t>=1 uses the cosine above. Normal parameters in this section
are mean and **variance**; public proposal helpers take standard deviations.

### Reference and acceptance gates (declared before execution)

Use an independent Python/NumPy deterministic density recursion, not the Scala
particle algorithm. Integrate prior/transition densities on a trapezoidal grid;
normalize only after applying the observation, retaining predictive mass and
log evidence. Run growth grids [-60,60] at spacings .1, .05, .025 and an expanded
[-80,80] grid at .025; persistent-mode grids [-15,15] at .05, .025, .0125 and
[-20,20] at .0125. All intervals include zero. Positive mass uses a half endpoint
at zero (integration of a density, not a point mass).

Pre-production reference amendment: the first trapezoidal absolute-moment check
failed at the kink in `|x|` (fine/medium differences 6.56e-5 and 8.83e-5 in the
growth cases). Evidence/mean/second/domain checks passed. Keep those failed
results and the original source. Integrate absolute moment separately on each
side of zero with composite Simpson's rule, and use the same smooth-half rule
for positive mass. The density recursion, grids, fixtures and all acceptance
tolerances remain unchanged. A cubic-polynomial identity checks the new rule;
this is a numerical-reference correction before production, not a relaxed test.

For every observation require fine versus medium and expanded versus fine:
absolute log-evidence difference <=1e-5, mean <=1e-5, second moment <=1e-4,
absolute moment <=1e-5, positive mass <=2e-4. Require predictive integrated mass
within 1e-8 of one and persistent-mode symmetry within 1e-10. These are numerical
convergence checks, not rigorous global quadrature-error bounds. Failure blocks
reference acceptance; do not silently expand tolerance. Independent linear
Gaussian recursion and initial mixture integration provide reference self-tests.

Production: seeds 1..256, budgets 256 and 1024 candidates/step, all five modes,
all three models = 7,680 runs. Fixed modes have min=max=budget; calibrated mode
shares that total ceiling with 16 scale and 32 evaluation pilots, floor 32 and
score target budget/4. Both ancestor and child defensive fractions are .1.
Child guidance is a frozen two-root Gaussian approximation of the square
observation, combined analytically with each Gaussian prior/transition component;
nonpositive observations use the target proposal. This is benchmark-local
guidance, not a new automatic nonlinear proposal API. Look-ahead uses its
approximate predictive density; poor guidance negates twice that log score.

Warm-up uses two negative seeds per model/mode at budget 256; excluded only from
reported estimates and timing. Rotate mode order across seeds. Use the existing
Figaro default RNG; no seed search, successful retry, likelihood clipping, weight
floor or numerical-policy relaxation. Capture each completed step and the first
terminal failure with its exact exception; record subsequent steps as unavailable.

For groups without numerical/callback refusals, check unnormalized evidence,
first/second/absolute moments and positive-side mass at t=0,9,19 against the
reference using six empirical standard errors plus 1e-4 times max(1,|reference|).
The numerical allowance exceeds the predeclared grid convergence tolerance.
Genuine zero-weight collapse contributes zero unnormalized mass. Numerical
refusal is **undefined**, not zero evidence: it blocks that group's unconditional
comparison. Never present successful-run-only estimates as unbiased validation.

Report completed/failed runs, failure step and cause, posterior errors and mode
mass errors at every step; mode loss means estimated positive mass outside
[.01,.99] while reference mass lies in [.1,.9]. Report all-seed availability and
clearly label conditional summaries. Track candidates, pilot candidates,
likelihood/target/proposal density calls, guidance constructions, look-ahead
calls, retained ancestry, ESS and elapsed time including preparation. Equal
candidate ceilings are not equal work or time; report cost/error tradeoffs, not
an automatic speedup claim. Timing is descriptive, not a pass gate.

### Release decision

Reference disagreement, unexplained statistical failure or systematic numerical
refusal in the standard continuous benchmark blocks a positive broader-readiness
claim and requires review before publishing 7.2.0. Poor guidance or a stress
variant being worse is a result to explain, not a reason to drop a scenario.
This gate does not require auxiliary filtering to win. Do not broaden numerical
support or change the architecture just to make a benchmark pass.

## Research and reuse assessment

Gordon, Salmond and Smith (1993), section 4.1/equations 9-10, defines the nonlinear
growth comparison. The maintained MIT-licensed `particles` Gordon_etal source
supplies a transparent initial-state convention and an implementation comparison.
Neither source's code or experimental dataset is copied. The first attempted
Notre Dame PDF mirror failed; the Inria-hosted paper is accessible.

Johansen and Doucet (2008), sections 2.2-2.4, motivates tail-sensitive guidance
and cautions against universal variance ordering. Branchini and Elvira (2021),
section 5 and Figure 1, motivates assessing modal representation and computation,
not just means. Their optimized APF, Lorenz and stochastic-volatility experiments
are **not implemented or reproduced here**. Higher-dimensional/chaotic systems
remain outside this scalar continuous gate.

NumPy supplies arrays, Gaussian RNG for fixed data generation, and linear algebra
for deterministic integration. A small custom harness is necessary to exercise
Figaro's corrected bounded-population APIs and exact refusal/cost contracts;
an external filter cannot substitute for that integration test. No new runtime
dependency is introduced. SciPy was absent from both inspected Python runtimes;
it is not needed for these fixed-grid references and no package was installed.

## Preserved baseline execution

**Initial execution: broader readiness BLOCKED; publication was held.** The
subsequent repair and final release results below supersede that readiness state,
without removing the original failure evidence.

The frozen 7,680-run matrix completed on the unchanged `bf6a2cd7` runtime:
153,600 step records, 2,537 complete trajectories and 5,143 numerical refusals.
No genuine zero-likelihood collapse or unexpected callback exception occurred.
All 190 evaluable unnormalized statistical checks passed; 260 checks were blocked
by undefined refused outcomes. Passing checks are not a pass for the full matrix.

Completed trajectories, each out of 256:

| Scenario | Candidate budget | Bootstrap | Guided | Auxiliary | Calibrated auxiliary | Poor auxiliary |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Growth | 256 | 139 | 241 | 0 | 0 | 0 |
| Growth | 1024 | 19 | 191 | 0 | 0 | 0 |
| Growth, sharp observation | 256 | 0 | 0 | 0 | 0 | 0 |
| Growth, sharp observation | 1024 | 0 | 0 | 0 | 0 | 0 |
| Persistent modes | 256 | 256 | 256 | 229 | 251 | 91 |
| Persistent modes | 1024 | 256 | 256 | 129 | 222 | 1 |

### What failed, and what did not

1. `Positive normalized weight underflow; unsupported dynamic range`: a finite
   log weight exponentiates to zero relative to the maximum. The baseline filter
   deliberately rejected the entire update rather than silently losing positive
   weight. These square-observation likelihoods reach that condition under quite
   ordinary Gaussian draws; it is not confined to exotic matrix boundaries.
2. `Auxiliary CDF lost a positive ancestor interval`: cumulative addition rounds
   a small positive selection probability away, or reaches one before the last
   positive entry. The auxiliary implementation explicitly refuses this. The
   ordinary bootstrap CDF does not enforce the same interval check, so its higher
   completion rate does not prove exact preservation of every small probability.
3. Separately, among the 251 completed calibrated-auxiliary persistent-mode runs
   at budget 256, ten ended with positive mass outside [.01,.99] even though the
   exact answer is .5. This is modal depletion, not just numerical refusal; work
   calibration is not posterior-precision or mode-preservation calibration.

The archived deterministic reproductions in `ContinuousFilterBenchmarkTest` exercised
both baseline guards with finite positive mathematical weights. They confirmed
refusal semantics, **not that those semantics were adequate for release**. No
guard was removed, likelihood clipped, numerical zero reclassified, or retry
conditioned on success. More particles did not repair the problem: this matrix
often encountered more refusals at 1024 than at 256. That observation is consistent
with more opportunities to encounter extreme weights; it is not an RNG diagnosis.

### Accuracy and cost where full comparisons are available

On persistent modes, bootstrap and guided completed every seed at both budgets.
Final positive-mass RMSE was .20221 versus .16216 at 256, and .10609 versus .08598
at 1024. Mean error can hide sign ambiguity, so the mode-mass result matters.
Guidance improved these errors but was not a free speedup: measured mean run time
was 1.451 versus 4.421 ms at 256, and 6.080 versus 18.548 ms at 1024. Guided draws
also required two top-level target/proposal density evaluations per candidate.
These are one warmed JVM session's descriptive timings, not a portable benchmark.

Auxiliary surviving-run errors are recorded but **cannot rank overall efficiency**:
availability differs, some configurations completed no trajectories, and early
refusals can look artificially cheap. Per-step conditional error/ESS/ancestry
summaries must be read alongside completion/failure counts. All raw outcomes and
separate work counters are retained; no cumulative score or ESS certifies modes.

### Validation record and next gate

The first reference revision failed only absolute-moment resolution checks; the
documented split-Simpson correction passed all original thresholds. Fixtures are
byte-identical across revisions. Gaussian recursion and symmetric-mixture controls
passed. The first Scala compile failed on a decimal literal in the new test;
the corrected three-test harness passed before any production run. Later targeted
guard reproductions are diagnostic additions, not changes to the executed study.
Final verification passed 87 Scala regressions in seven suites, four independent
Python reference tests, 41 documentation tests and scientific-reference checks.
The first documentation whitespace invocation treated CRLF as trailing whitespace;
task-owned edited documentation was normalized to LF and rechecked. This did not
change numerical output. Local execution is recorded; no remote CI for this new
benchmark branch or new publication is claimed.

One independently generated observation sequence per scenario was held fixed
while sampling seeds varied. This estimates algorithm variability conditional on
those data, not robustness averaged over many datasets. Only scalar continuous
states are covered; no Lorenz, high-dimensional or application-data validation is
implied. The original papers and maintained implementation comparisons informed
benchmark selection; their published performance results are not Figaro measurements.

**Baseline's recommended next milestone:** design numerical-range and ancestor-selection
handling that distinguishes negligible representational loss from genuine zero
support, with explicit error accounting and tests; preserve the strict baseline.
Do not merely delete the guards or introduce undocumented weight floors. Then
rerun these exact fixtures/seeds plus the existing exact finite/Gaussian controls.
Address modal-depletion/calibration limitations separately. Runtime design and
publication were subsequent work; no production algorithm was changed in that baseline.

## Numerical repair and identical-protocol rerun

The follow-up retains normalized log weights across steps, forms the entire
defensive ancestor mixture and its correction in logs, and samples independent
ancestors with a log-mass binary tree. No clipping, likelihood floor, seed removal,
selective retry or acceptance-threshold change was used. `currentLogWeights` exposes
finite weights even when their ordinary `Update.weights` representation is zero.
See the [numerical contract](WEIGHTED_BOUNDED_FILTER.md#numerical-representation-and-reproducibility).
The former refusal tests now assert retention and forced selection of tiny masses;
the original refusal source and failed run remain in the baseline evidence archive.

The rerun used the byte-identical benchmark harness and fixtures, all original
seeds 1..256, both budgets, all five modes, the same reference JSON, warm-up policy
and all 450 acceptance checks. **All 7,680 trajectories completed** (153,600 step
records), with **zero numerical refusals, zero genuine zero-weight failures and
zero callback errors**. **All 450 checks passed**, none blocked; the original
baseline completed 2,537 trajectories, refused 5,143, passed 190 checks and blocked
260. The analyzer's `broader_readiness` is now true for this bounded numerical gate,
not authorization or proof of complete release readiness.

Local follow-up verification passed 93 Scala tests across eight suites, all 40
independent finite/Gaussian study checks (512 seeds per configuration), binary
compatibility against the configured 7.0.0/7.1.0 baselines, TASTy compatibility with
7.1.0, JAR packaging and Scaladoc generation. Four Python reference tests and 41
documentation tests passed; API freshness, links and research mappings were checked.
One initial documentation run caught a missing INF-13 navigation link; it was fixed
and the unchanged checks passed. Only plot captions were subsequently generalized
from baseline-specific wording; numerical analysis and acceptance were unchanged.
Remote CI and a new packaged-application acceptance run were not performed here.

### Remaining mode risk and cost

All entries below now include all 256 seeds, not just surviving runs. The reference
positive-side posterior mass in the persistent-mode model is exactly .5. A
"depleted" run has final estimated mass outside [.01,.99]; this diagnostic was
declared before the baseline, and is not an added pass/fail gate.

| Budget | Method | Final sign-mass RMSE | Depleted runs / 256 | Mean elapsed ms / run |
| ---: | --- | ---: | ---: | ---: |
| 256 | Bootstrap | .19633 | 1 | 3.271 |
| 256 | Guided | .16421 | 0 | 5.980 |
| 256 | Auxiliary | .13897 | 0 | 8.368 |
| 256 | Calibrated auxiliary | .26343 | 12 | 3.193 |
| 256 | Poor auxiliary | .27616 | 24 | 8.099 |
| 1024 | Bootstrap | .09098 | 0 | 14.676 |
| 1024 | Guided | .08278 | 0 | 25.904 |
| 1024 | Auxiliary | .07290 | 0 | 35.611 |
| 1024 | Calibrated auxiliary | .13445 | 0 | 10.497 |
| 1024 | Poor auxiliary | .16693 | 0 | 34.275 |

Auxiliary guidance improves this mode-mass error at the same candidate ceiling,
but costs more; it is not a universal speedup. Calibration used approximately
2,395 production candidates plus 960 pilots per 20-step run at budget 256, versus
5,120 production candidates in each fixed mode. At budget 1024 it used about
6,658 plus 960 pilots, versus 20,480 fixed-mode candidates. Lower work came with
higher mode error. Bad look-ahead remained harmful despite full support protection.

The robust tree is also more expensive than the old CDF: the fully completed
persistent-mode bootstrap baseline averaged 1.451/6.080 ms at budgets 256/1024,
versus 3.271/14.676 ms now. These are separate short warmed-JVM sessions, not an
isolated resampler microbenchmark or a portable speed ratio. Changed RNG consumption
changes individual trajectories despite identical seeds. Refused baseline runs
are never used to claim a performance advantage. The purpose of this repair is
numerical usability and support handling, not a speed claim.

The observations resolve the two numerical refusal mechanisms for this benchmark.
They do **not** eliminate finite-population mode loss, establish a precision rule,
or validate arbitrary tails/dimensions/data. A fixed larger budget or better
proposal can help, but no automatic recovery was added. Publication remained on
hold at the end of this repair task. Subsequent final-source CI, integration and
Central consumer gates passed; [7.2.0 was published](RELEASE_7_2_STATUS.md) on
2026-10-21. The original numerical evidence and its limitations are unchanged.

### Reproduce

```text
python -B tools/continuous_filter_reference.py prepare --output <new-reference-directory>
sbt "figaro / Test / testOnly com.cra.figaro.test.modernization.ContinuousFilterBenchmarkTest"
sbt "figaro / Test / runMain com.cra.figaro.test.modernization.ContinuousFilterBenchmark <new-reference-directory>/fixtures.tsv" > study.log
python -B tools/continuous_filter_reference.py analyze --output <new-analysis-directory> --reference <new-reference-directory>/reference.json --log study.log
python -B tools/continuous_filter_reference.py report --output <new-report-directory> --analysis <new-analysis-directory>/analysis.json
```

Use Python with NumPy and matplotlib for plotting; set BLAS/OMP threads to one
for the reference. On Windows with an explicit workspace-access requirement,
pass the local `--acl-script` hook. Every output is create-only, so retries must
use new directories. Analyzer exit zero means analysis completed; the JSON
`broader_readiness: false` is an explicit failed readiness assessment, not success.
Inspect `blocked`, `failed` and availability before interpreting numerical gates.
