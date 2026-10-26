# Supporting records for the experiment retrospective

These dated source-note snapshots support the [standalone report](../LITERATURE_EXPERIMENTS_REPORT.md). They preserve historical findings and then-current status, not a current release recommendation. Only citation targets and heading levels are mechanically adapted. Source hashes identify the original bytes. Links to public implementation records use immutable commits; unavailable research source is labeled explicitly. Experimental code, private execution logs and local evidence archives are not published here. These notes are not a self-contained reproduction package.

Research context: [diagnostic provenance](../RESEARCH_REFERENCES.md#dia-01).

<a id="record-coordinate-budget-research"></a>

## Coordinate Budget Research

Original source: `docs/COORDINATE_BUDGET_RESEARCH.md`. SHA-256: `6ce10976021afc1742a59bcb5c1aba54d7c1e06e5fa29e1aacee126f843cf703`.

### Coordinate sampling budget adequacy

**Assessment complete; no promotion or fresh sampling.** The retained results
support a concrete budget problem: one random-scan transition updates only one
coordinate, so a fixed total budget provides progressively less effective sampling
as dimension grows. Initialization or adaptation effects remain unresolved. The
next experiment should separate these causes, not repeatedly increase work until
the previous failures disappear.

The subsequent fresh-seed experiment was separately approved. Its immutable
execution protocol (unpublished research source: `COORDINATE_BUDGET_PROTOCOL.md`) and
[current outcome](#record-coordinate-budget-status) are distinct from this retained-data
assessment and its unchanged results.

The [closed usability qualification](#record-coordinate-warmup-status) and earlier
performance failure retain their original decisions. The local convenience API
remains unpromoted. No runtime, stopping estimator, default or version changes are
part of this assessment. [Research provenance](#reference-inf-20).

#### Evidence from the retained experiment

The analysis (unpublished research source: `coordinate_budget_assessment.py`) examines every raw coordinate
in factor2/4/8 across all five methods, eight seeds and both JVM repetitions:
1,120 coordinate summaries, 4,480 traces and 896 candidate tuning reports. It
recomputes means, quarter/half summaries and independent nonoverlapping batch-means
error estimates at all four declared batch sizes, 50/100/200/400. Five arithmetic
controls pass. The machine-readable assessment (unpublished research source: `coordinate_budget_assessment_v1.json`)
retains every row and the original whole-study cost/failure inventory, including
the adverse and non-factorized fixtures not reanalyzed here. This post-hoc scope
answers the dimension question, not the entire promotion question.

Each chain originally had 4,000 warm-up and 4,000 retained transitions, regardless
of dimension. The following medians pool coordinates and eight seeds within the
first JVM, not across JVMs. The second JVM yields essentially the same dimension
pattern and is reported separately; its repeated seeds are not independent trials.

| Coordinates | Expected warm-up selections per coordinate | Candidate median mean ESS | Informed fixed-scale median mean ESS | Candidate minimum actual warm-up selections |
| --- | ---: | ---: | ---: | ---: |
| 2 | 2,000 | 1,642 | 1,642 | 1,930 |
| 4 | 1,000 | 782 | 794 | 948 |
| 8 | 500 | 382 | 389 | 444 |

There are 16,000 retained vector states across the four chains in each execution,
but only about 382 effective draws for a typical scalar mean at eight coordinates.
Multiplying median ESS by dimension gives about 3,285, 3,130 and 3,052. Informed
fixed scales show nearly the same decline, which argues against blaming the
convenience wrapper or adaptation alone. Fixed uninformed and default proposals
are included in the result; neither removes the problem.

Warm-up did not simply leave a coordinate untouched: every candidate coordinate
received at least 444 attempts and 184 accepted moves in the eight-root fixture.
No final scale hit a bound. Median final proposal scale divided by posterior SD
is approximately 2.4 at every dimension. These are descriptive checks, not proof
that adaptation or warm-up was sufficient.

##### Why this scaling is plausible

For an independent product target and a frozen coordinate kernel K, selecting that
coordinate with probability p gives the lazy marginal kernel
`P = (1-p) I + p K`. Under stationarity and summable autocorrelations, the
integrated autocorrelation times satisfy `tau(P) = (tau(K)+1)/p - 1`.
This follows by summing the covariance resolvent of the lazy kernel. With equal
weights `p=1/d`, even exact independent conditional refreshes have `tau=2d-1`.
Thus repeating unchanged coordinates in a retained vector is not free information.
This is an independent derivation for the product control, not a theorem for
coupled, adaptive or nonstationary Figaro graphs.

##### Counterevidence and unresolved initialization

For the narrowest coordinate x0, candidate RMS mean error across the eight seeds
is .030, .049 and .081 posterior SD, while RMS reported MCSE is .025, .035 and
.051. At eight coordinates the informed fixed-scale RMS error is .053, lower than
the candidate's .081 despite similar ESS. Eight seeds cannot establish a bias or
calibrate interval coverage, but this discrepancy prevents declaring the problem
to be purely retained-sample count.

Both failed seeds have larger first-half errors than second-half errors. Yet
this is a selected, retrospective observation; all quarter means remain recorded.
Across all eight-root coordinates the first-/second-half RMS errors are .081/.067,
whereas the two- and four-root second halves are not better. Discarding additional
draws after looking at these failures would be an invalid repair of the old test.
Discarded initialization/warm-up trajectories were not logged, so their contribution
cannot be reconstructed from retained traces.

Independent batch-means estimates also depend on batch length: at eight coordinates
their median ratio to the reported MCSE increases from .786 for batches of 50 to
.946 for batches of 400. Small batches can miss correlation; choosing the smallest
estimate would be misleading. Within-chain batches alone also cannot protect
against different chains trapped in different regions. They supplement, not
replace, the existing cross-chain diagnostics.

#### Literature and existing implementations

[Vats, Flegal and Jones, Multivariate Output Analysis for MCMC](https://arxiv.org/html/1512.07713v4),
sections 1–4, connects precision to the covariance of Monte Carlo error under a
Markov-chain CLT and consistency conditions. Its nonoverlapping batch estimator
provides the independent variance calculation used here. Both batch size and
number matter. This assessment does not implement its multivariate stopping rule
or transfer asymptotic coverage to this finite experiment.

The [Stan posterior-analysis manual](https://mc-stan.org/docs/reference-manual/analysis.html)
distinguishes convergence, effective samples and precision. The maintained
[posterior MCSE interface](https://mc-stan.org/posterior/reference/mcse_mean.html)
accepts ordered iterations by chain. Figaro already exposes raw-mean ESS and MCSE;
its rank-normalized bulk ESS is a different diagnostic. Use existing outputs,
preserve chain identity, and do not introduce another public diagnostic engine.

[He, De Sa, Mitliagkas and Ré, Scan Order in Gibbs Sampling](https://arxiv.org/html/1606.03432v1),
introduction and section 3 examples, shows that scan order can materially affect
mixing in either direction. Its exact-Gibbs examples do not establish that systematic
scan would fix these Metropolis-within-Gibbs results. Changing the scan now would
confound the budget question and require new correctness/efficiency evidence.

These passages were inspected on 2026-10-25; proofs and numerical examples were
not reproduced. The PMC copy of the scan paper was challenge-blocked and the
requested arXiv v2 HTML was absent; the accessible v1 manuscript and proceedings
record were used. No external code, data or new dependency was incorporated.
The analysis reuses installed NumPy/SciPy and a direct scalar batch-means formula.
A new sampler or R/Stan backend is unnecessary for this bounded diagnostic task.

##### Precision planning is not a replacement gate

For an illustrative stationary Normal approximation with reliable mean ESS,
a 95% mean interval with half-width .12 posterior SD needs approximately
`(1.96/.12)^2 = 267` effective samples. Applying a conservative Bonferroni
calculation to 112 coordinate/seed mean checks gives approximately 856 instead.
These are planning illustrations, not the old experiment's acceptance rule,
finite-run guarantees or minimums that certify arbitrary models. The 112 checks
are 14 coordinates times eight seeds in one JVM; independence is not needed for
the union-bound rationale. The real qualification also tests events and SDs.
The old every-execution accuracy gate stays failed and unchanged.

#### Recommended bounded experiment for review

The purpose is **cause discrimination**, not promotion. Freeze a new source map
and protocol before any fresh sampling. Do not reuse either failed seed set or
the old reserved confirmation seeds. Keep the existing adaptation, scalar moves,
fixed scan weights, graph ownership and released stopping/defaults unchanged.

##### Stage 1 Control the initialization comparison

Use the existing analytic factor2/4/8 targets and all original coordinate,
event and derived queries. Compare the candidate with informed fixed-scale
random scan; the latter removes adaptation as an explanation without requiring
another algorithm. Retain all original scale/acceptance bounds and equal weights.

Two target-equivalent constructions distinguish initialization:

- Original: `Normal(0,4)` roots plus the original quadratic log constraints.
- Oracle posterior-as-prior: `Normal(0,s_j*s_j)` roots with no likelihood factors.

Both have the same normalized factorized posterior, but the second starts from
that known posterior through the ordinary prior initializer. It is a test-only
oracle, not a practical initializer for unknown posteriors. Check density ratios,
support transformations and deterministic common-state MH acceptance independently
before use. Keep proposal step sizes in physical value units identical. Do not
mutate values in `initialState`, misuse its pure-predicate contract or override
randomness with the deterministic replay mock. No runtime initializer hook is needed.

Only the fixed-kernel oracle arm is stationary from initialization; adaptive
history can disturb that property in the tuned arm. Removing factors also changes
evaluation cost, so compare initialization arms for statistical diagnosis, not
as a claimed runtime speedup. Account for their actual costs separately.

**Exit:** if target/transition equivalence or existing controls fail, stop with an
incomplete study. Do not patch runtime or change targets without a new design review.

##### Stage 2 Separate discarded and retained work

Proposed fresh seeds: `2531001..2531032`, four chains, one worker, thinning one,
one measured JVM execution per cell, with rotated method order. Check repository
seed inventory before freezing; these seeds have not been executed by this assessment.
Use a fixed, separately identified non-study JIT seed. Hold minimum coordinate
attempts at 100; report actual attempts and acceptances, including refusals.

| Arm | Warm-up transitions per chain | Retained transitions per chain | Question |
| --- | ---: | ---: | --- |
| A | 4,000 | 4,000 | Fresh baseline |
| B | 2,000 × dimension | 4,000 | Does more warm-up help? |
| C | 4,000 | 2,000 × dimension | Does more retained work help? |
| D | 2,000 × dimension | 2,000 × dimension | Equal expected coordinate effort across dimensions |

At dimension two these four arms are identical: execute A once, explicitly map
B/C/D to that same result, and never count the aliases as independent runs.
This is 36 distinct dimension/budget/method/initialization cells and **1,152
attempts**, with **69,632,000 requested transitions** across all chains. Maximum
work in a single eight-root chain is 16,000 warm-up plus 16,000 retained moves.
No extra budget tiers, retries or selected-seed reruns. A four-hour process cap
and 12 GiB output cap stop the study as incomplete, not successful.

Record all queries, chain identity, raw retained traces, start/end states where
available without new runtime hooks, final scales, warnings, attempts, failures,
requested transitions, actual density/likelihood calls and total elapsed work.
Do not interpret differing RNG consumption as a guaranteed common-random-number
pairing. Initialization and adaptation costs count; no timing speed claim from one JVM.

##### Stage 3 Independent analysis and firm exit

Reuse analytic truths and independently recompute mean, event probability and SD
errors. Preserve the original .12-SD mean, .04 absolute probability and .20 relative
SD checks for every execution. Report all failures even though this is a diagnostic
experiment, not a second chance to pass the closed qualification.

Use the seed, not the chain, coordinate or JVM, as the replication unit. Report
per-coordinate error distributions, cross-seed bias/variance, mean ESS per update,
retained-quarter drift, and batch-size sensitivity. Quantify uncertainty in
error/ESS ratios by a predeclared seed-cluster bootstrap retaining the whole matched
cell vector; no selecting a favorable coordinate or bootstrap setting afterward.

Proposed decision rules to freeze before implementation:

- **Retained-work explanation:** at eight roots, C/A has at least 2.5 times median
  mean ESS and at most .70 times pooled standardized mean RMSE for both original
  construction methods. The seed-bootstrap 95% bounds must exclude no improvement
  (ESS ratio at most one or RMSE ratio at least one). All coordinates remain visible.
- **Warm-up relevance:** B/A mean-MSE ratio at most .75 with its 95% upper bound
  below one is evidence of a material warm-up effect, not a universal recommendation.
- **Initialization relevance:** original/oracle mean-MSE ratio at least 1.5 with
  its 95% lower bound above one under the same fixed kernel/budget is evidence that
  starting distribution matters here. If intervals cross these boundaries, label
  the causal evidence inconclusive rather than selecting a preferred explanation.
- **Dimension-normalized adequacy:** D's eight-root median mean ESS must be at
  least .75 of A's two-root value for both methods, and every original-construction
  candidate D execution must pass the unchanged accuracy checks. Failure parks
  the capability; no fifth arm or larger-budget rescue.

The .70/.75/1.5/2.5 margins are proposed engineering relevance thresholds, not
published universal constants. Freeze exact bootstrap details and synthetic
controls with the implementation before new measurements. Even a successful
diagnostic study only justifies considering a separately approved requalification
across all original ordinary and adverse fixtures; it does not promote this subset.
If the cost ceiling is exceeded, equivalence fails, or the maximum bounded arm is
inadequate, stop and return to other roadmap priorities with all evidence retained.

Related: [multi-chain diagnostics](https://github.com/mattwilkinsphoto/figaro/blob/2ba1c55ee9b60c68fc1097b6b7bbb92fd558aa28/docs/MCMC_RELIABILITY.md),
[blocked proposals](https://github.com/mattwilkinsphoto/figaro/blob/2ba1c55ee9b60c68fc1097b6b7bbb92fd558aa28/docs/BLOCKED_PROPOSALS.md), candidate guide (unpublished research source: `COORDINATE_WARMUP.md`).


<a id="record-coordinate-budget-status"></a>

## Coordinate Budget Status

Original source: `docs/COORDINATE_BUDGET_STATUS.md`. SHA-256: `ad42debd1e82ce18965996cfc956595790a7f3119bde72d5705ae0bd316c472e`.

### Coordinate budget and initialization study status

**MCMC-BUDGET-01 v1 is complete: all diagnostic exit gates pass. No promotion.**
The [approved plan](#record-coordinate-budget-research) and
fixed protocol (unpublished research source: `COORDINATE_BUDGET_PROTOCOL.md`) retain unchanged stopping/defaults
and both earlier failed experiments. [Research provenance](#reference-inf-20).

Stage 1 passes 135 Scala tests in 11 suites, including independent target and
physical-transition equivalence controls and all prior focused regressions.
Six Python controls pass for inventory, exact trace decoding, original error
thresholds, seed-cluster resampling and refusal of missing/misaligned monitors.
The fresh matrix completed all 1,152 attempts and 69,632,000 requested transitions
in one JVM without retries or execution failures. Complete captured output is
5,796,971,065 decoded bytes; process elapsed time was about 407 seconds, within
the frozen four-hour/12-GiB limits. Compilation, startup and trace serialization
are included in that process time, not the per-cell sampler measurements.

#### Findings

The complete machine-readable result (unpublished research source: `coordinate_budget_result_v1.json`)
retains every cell, warning, error flag, cost, coordinate bias and uncertainty
estimate. The primary eight-coordinate comparison increases retained draws per
chain from 4,000 to 16,000 while keeping warm-up at 4,000. Four chains and all
other settings remain fixed. Intervals below are the frozen 95% seed-cluster
percentile bootstrap intervals, not simultaneous coverage guarantees.

| Original construction, C/A | Tuned coordinates | Informed fixed scales |
| --- | --- | --- |
| Median mean-ESS ratio | 3.929 [3.801, 4.020] | 4.029 [3.930, 4.124] |
| Standardized mean-RMSE ratio | .521 [.471, .578] | .542 [.485, .607] |
| Executions failing any accuracy check, A then C | 13/32 then 0/32 | 9/32 then 0/32 |

Both methods pass the predeclared retained-work gates. This is increased precision
from more sampling, **not a faster sampler**: actual original likelihood-factor
calls rise approximately 2.5-fold. Per-cell elapsed totals rise from 8.00 to 30.57
seconds for tuned and 4.82 to 19.45 seconds for informed; these are descriptive
one-JVM measurements, not repeatable timing or equal-cost superiority claims.

Increasing warm-up alone from 4,000 to 16,000 does not establish the required
benefit: eight-root B/A mean-MSE ratios are 1.018 [.816, 1.261] for tuned and
1.006 [.775, 1.292] for informed. All eight original/oracle initialization contrasts
also fail to establish their relevance threshold. This is absence of the specified
evidence, not proof that initialization never matters. Only the informed fixed
kernel isolates starting-distribution effects without adaptation-history changes.

With both budgets scaled by dimension (D), eight-root median ESS is .939 of the
two-root baseline for tuned and .943 for informed, above the .75 point gate.
All **96/96 maximum-budget original candidate executions** pass unchanged mean,
event-probability and SD checks. Across the entire matrix, 80 executions fail at
least one accuracy check; all are lower-retained-budget A/B cells, including oracle
controls. These failures remain in the record. A completed execution is not an
accuracy pass, and a diagnostic-study pass does not imply every arm passed.

#### Independent validation and interpretation

The analyzer checks all 16,000 trace summaries, including automatic monitor
identity and exact event-indicator correspondence. A separate arithmetic audit
recomputes 12,800 scientific query decisions from analytic truths and reproduces
all 16 bootstrap contrasts without importing the primary analyzer or calling
SciPy bootstrap; the largest numerical difference is below 5e-16. The audit
independently reproduces all five exit-gate decisions. This is independent
numerical validation, not an independent human review or new convergence theorem.

The evidence supports **insufficient retained work per coordinate** as the main
explanation on these product-Normal fixtures. It does not establish a defect in
the existing initializer, universal dimension-linear budgets, or correctness for
correlated, heavy-tailed, multimodal or dynamic graphs. Strongest counterevidence
to generalization remains the earlier adverse-fixture failures and the low-budget
failures even with oracle starts. Previously closed failed protocols stay failed.

#### Next decision and delivery boundary

The frozen outcome is `eligible-for-separate-requalification-review`, not promotion.
Recommend a separate bounded review covering all original ordinary and adverse
fixtures, explicit per-coordinate work accounting, complete initialization/tuning/
sampling costs, and independently checked errors and warnings. Freeze new seeds,
budgets and exit gates before any new sampling; retain the earlier performance
and usability failures rather than retroactively repairing them. No further
experiment is authorized by this pass alone.

This milestone adds test-only study/control code and research documentation.
Existing candidate runtime bytes, released stopping estimator and defaults are
unchanged. No merge, release, automatic budget selection or public promotion.
Local focused regressions are not remote CI or packaged-consumer acceptance.

Final housekeeping checks also pass: 41 documentation tests, 18 CI-tool controls,
research registration and local links, frozen source/reference hashes, public
context checks and Git whitespace checks. Reference mapping updated: INF-20;
existing diagnostic provenance remains DIA-01. The private reproducibility bundle
contains exact inputs/source hashes, complete captured output, analysis/audit,
versions, parameters, plot and rerun instructions; it is not a public data release.


<a id="record-coordinate-requalification-status"></a>

## Coordinate Requalification Status

Original source: `docs/COORDINATE_REQUALIFICATION_STATUS.md`. SHA-256: `5a263cae8c1e0898ae09e481b77197c1b0e767c031ab000ca7502c9e35492f24`.

### Coordinate requalification status

**MCMC-REQUAL-01 v1 is complete: no promotion at the ordinary-accuracy exit gate.**
See the fixed protocol (unpublished research source: `COORDINATE_REQUALIFICATION_PROTOCOL.md`) and
[research provenance](#reference-inf-20).

All original fixtures and comparators were retained with the frozen dimension-scaled
budgets: 4,000/8,000/16,000 warm-up and retained transitions per chain at two/four/
eight roots. Four chains, 32 fresh seeds and two JVM executions produced the
complete 2,816-attempt inventory. Repeated JVM seeds are not independent replicates.
No retry, extra budget or revised threshold was used. Earlier failed studies
remain closed. Stopping/defaults and candidate runtime bytes are unchanged.

#### Acceptance outcome

| Gate | Result |
| --- | --- |
| Candidate execution | Pass: all 512 candidate attempts complete |
| Ordinary accuracy | **Fail: 24 of 384 candidate executions** |
| Added total-cost allowance | Pass: every candidate/prototype ratio is below 2.0 |
| Adverse warning safety | Pass: no inaccurate adverse candidate execution lacks all query warnings |
| Control execution and independent validation | Pass: all non-completions are expected, retained calibration refusals |

The complete result (unpublished research source: `coordinate_requalification_result_v1.json`) retains
all main and pilot outcomes. Both JVM processes completed within their four-hour/
12-GiB limits, in about 279 and 307 seconds. The planned budget was 133,120,000
transitions; completed pilot/main phases account for 129,088,000. There are 2,690
completed main runs and 126 calibrated-block refusals. Their costs and completed
pilot traces remain included; missing main draws are not zero-error results.

| Fixture | Candidate accuracy failures / 64 executions | Total API / prototype elapsed ratio |
| --- | ---: | ---: |
| Factorized 2 roots | 0 | 1.127 |
| Factorized 4 roots | 0 | 1.209 |
| Factorized 8 roots | 0 | 1.264 |
| Correlation .9 | 14 | 1.087 |
| Coupled Gamma/Beta | 10 | 1.100 |
| Circular | 0 | 1.255 |
| Strong correlation .995, adverse | 60 | 1.093 |
| Separated modes, adverse | 43 | 1.111 |

The ordinary failures are seven correlated seeds and five positive/bounded seeds,
each repeated in both JVMs. They exceed unchanged .12-reference-SD mean or .04
absolute-event limits. No ordinary failure is excused by a warning or by the
factorized successes. Timings include initialization, adaptation, diagnostics and
all applicable pilot costs; these are overhead observations, not speedup claims.

#### What the controls establish

The prior product-target budget explanation survives the fresh factor2/4/8 runs,
but does not establish adequacy for dependence. Informed fixed scalar proposals
also fail 12/64 moderate-correlation and 7/64 Gamma/Beta executions. The unchanged
prototype fails 14/64 and 10/64 respectively. Good scalar scale tuning is not a
substitute for exploring coupled directions.

Known-covariance oracle blocks pass all 64 executions on each correlated fixture.
This is evidence for the value of suitable joint geometry, not a deployable
automatic solution: the fixed pilot is refused in 62/64 moderate-correlation and
64/64 strong-correlation attempts. The two successful calibrated runs cannot
stand in for the other 126 comparisons. Default MH is a further counterexample
to replacing existing choices automatically: it fails only 2/64 Gamma/Beta and
2/64 mode runs here, but fails 18/64, 18/64 and 29/64 factor2/4/8 runs.

A warning is also not an absolute-error certificate. For Gamma/Beta seed 2541016
in JVM 1, x0 has mean 2.37167 versus reference 2.22203, exceeding the .12-SD
limit despite R-hat 1.00242 and no warning on that query. Other queries in that
run warn. The adverse safety gate checks whether *any* query warns; it does not
guarantee warning coverage for each inaccurate query or discover arbitrary modes.

The post-hoc retained-failure audit (unpublished research source: `coordinate_requalification_trace_audit.py`)
compares all scientific chain traces of the 24 failed candidate executions with
their prototype counterparts. Of 440 byte-level pairs, 420 match and 20 differ.
All 280 correlated pairs match; the differences are in five Gamma/Beta executions.
This is selected-failure attribution only, not new sampling or a revised gate.
Do not claim universal trajectory identity or infer the cause of the differing
paths from the failure counts. Existing deterministic replay controls pass.

#### Validation and delivery boundary

Independent analytic/quadrature references requalify within 1e-12 of the immutable
original values. All 137 focused Scala tests in 12 suites and seven Python controls
pass. The primary analysis independently checks 19,594 pilot/main/automatic trace
summaries and 6,656 automatic chain traces. A separate arithmetic audit, without
importing that analyzer, recomputes 17,930 scientific query decisions, full inventory,
overhead ratios and all exit gates. Raw errors, warning combinations, first/last
states, chain means, mode crossings and batch-MCSE sensitivity remain available.

Final checks pass: seven new and six prior numerical controls, 41 documentation
tests, 18 CI-tool controls, research registration/local links, all frozen runtime
and historical source maps, result hashes, public-context checks and Git whitespace.
Reference mapping updated: INF-20; diagnostic provenance remains DIA-01. The private
reproducibility bundle includes exact sources/inputs, hashes, captured outputs,
versions, results, plot and rerun instructions, including historical-check inputs.

This is independent numerical validation, not a separate human review, full remote
CI, packaged-consumer acceptance or a convergence theorem. No candidate promotion,
subgroup promotion, commit, push, merge, version change or release is made here.
The original performance/usability failures and diagnostic pass keep their own
immutable records. The candidate remains parked and 7.4.0 remains the adoption baseline.

#### Recommended next decision

Close this bounded requalification at its firm exit. Do not attempt another larger
budget merely to eliminate these failures. Return to application-driven roadmap
priorities using the released explicit proposal/diagnostic workflows.

If this capability remains important enough for a separate research milestone,
the evidence favors assessing **dependence-aware proposals and reliable pilot
qualification**, with known-geometry controls and support transformations where
needed. The oracle/pilot contrast is the concrete motivation; it does not yet
select an implementation. A new literature-led plan, representative coupled targets,
fixed cost/accuracy gates and independent validation would be required before
any new implementation or sampling. General automatic tuning remains unqualified.


<a id="record-coordinate-warmup-status"></a>

## Coordinate Warmup Status

Original source: `docs/COORDINATE_WARMUP_STATUS.md`. SHA-256: `9225d852377fdf5fbba5a4f0636d554f8d0abe15ed84426db84f57dc45454e5d`.

### Coordinate warm up qualification status

**MCMC-WARMUP-API-01 v1: no promotion.** The additive convenience API and public
examples are implemented locally, but fresh qualification fails the ordinary
accuracy gate. Stop before integration/promotion; do not change budgets, seeds
or thresholds to obtain a pass. Stopping estimators and defaults remain unchanged.

API guide (unpublished research source: `COORDINATE_WARMUP.md`), approved plan (unpublished research source: `MULTILATENT_WARMUP_API_PLAN.md`),
frozen protocol (unpublished research source: `COORDINATE_WARMUP_PROTOCOL.md`),
complete machine-readable result (unpublished research source: `coordinate_warmup_qualification_v1.json`),
[research provenance](#reference-inf-20).

#### Delivered candidate

The new `runTunedCoordinates` entry requires explicit complete declarations for
two to eight owned fixed roots. It adds matching configuration checks across
chains, actual per-coordinate attempt/acceptance counts, frozen detached reports,
automatic retained coordinate monitors, storage accounting and named refusals.
It reuses the existing scalar moves, gain rule and owned MH runner. Existing
single-latent and precision-stopping APIs retain their contracts.

The three public-only examples compile and execute for unequal Normal scales,
coupled Gamma/Beta roots and circular roots, including explicit short-budget
refusal and recovery. They print existing diagnostics and warnings; completing
an example is not posterior qualification. The final targeted run passes **131
tests in ten suites**, including 12 new API controls and the unchanged earlier
transition, observation, stopping, block and diagnostic controls. Three Python
qualification guard/mapping tests pass. This is local validation, not remote CI.

#### Fresh qualification result

The frozen matrix contains 704 attempts: eight fresh seeds, two JVM repetitions,
44 model/method cells. All 128 candidate attempts complete. Across all methods,
674 attempts complete and 30 pilot-calibrated block attempts refuse inadequate
pilots; their errors, costs and likelihood counts remain in the result.
Python independently recomputes 4,330 scientific mean/SD summaries and verifies
1,664 automatic-monitor traces against their matching scientific columns.

| Gate | Result |
| --- | --- |
| Candidate execution | Pass: no candidate execution failures |
| Ordinary accuracy | Fail: four of 96 candidate ordinary executions exceed the mean-error limit |
| Added total-cost allowance | Pass: each fixture stays below the predeclared 2.0 candidate/prototype time ratio |
| API and lifecycle controls | Pass locally, including exact deterministic-fixture replay |

The four ordinary failures are two seeds repeated in two JVMs, not four independent
seed failures. Both concern `factor8/x0`, whose reference mean is zero and SD is
.04. The permitted absolute mean error is `.12 * .04 = .0048`.

| Fresh seed | Estimated mean in both JVMs | Error in reference SD units | Allowed |
| --- | ---: | ---: | ---: |
| 2521003 | 0.0062622 | 0.1566 | 0.12 |
| 2521007 | 0.0050157 | 0.1254 | 0.12 |

A post-study audit compares the retained raw traces, not a rerun: every chain's
failing coordinate trace exactly matches the unchanged test-only prototype for
the same seed and repetition. The prototype has the same four quality failures.
This argues against a wrapper-induced trajectory regression in these failures;
it does not establish that the budget is adequate, prove absence of other bugs,
or distinguish initialization effects from finite-sample error. One failing
seed has R-hat about 1.004, illustrating why a favorable diagnostic is not an
absolute-error certificate. Estimated mean ESS is about 489–492 for these queries.

The prior performance experiment remains a separate failed result. Its reserved
confirmation seeds were not used. This usability study did not redefine that
experiment's success criterion or rewrite its frozen hash map. The approved
additive runtime change has a separate new hash map; the original runtime bytes
remain preserved and verified in the original evidence archive.

#### Costs and adverse cases

| Fixture | Mean total API time / prototype time | Candidate quality failures / 16 executions |
| --- | ---: | ---: |
| Factorized 2 roots | 1.156 | 0 |
| Factorized 4 roots | 1.256 | 0 |
| Factorized 8 roots | 1.259 | 4 |
| Correlation .9 | 1.096 | 0 |
| Coupled positive/bounded | 1.148 | 0 |
| Circular | 1.287 | 0 |
| Strong correlation .995 | 1.160 | 16 |
| Separated modes | 1.163 | 8 |

The extra measured time is roughly 10–29% in this small serial matrix, including
automatic monitoring and diagnostics. This is an overhead observation, not a
speedup or portable timing prediction. Complete per-execution costs, both JVM
ratios, likelihood counts and retained-value accounting are preserved.

Strong-correlation and mode cases remain adverse, not ordinary successes.
Oracle blocks pass all strong-correlation executions, but calibrated blocks
refuse all 16 strong pilots and 14 of 16 moderate-correlation pilots. Default MH
passes the mode quality checks here while the tuned local proposal fails half;
that is counterevidence to automatic replacement, not universal missing-mode
protection. No failures are excluded from attempt counts or costs.

#### Delivery boundary and next decision

Stage 1 is verified locally; stage 2 is complete at its firm no-promotion exit.
Stage 3's full integration CI, compatibility gates and isolated packaged consumers
were not run because qualification failed. No commit, push, merge, publication,
release-note promotion or version increase was made in this milestone. The local
development build remains `7.5.0-SNAPSHOT`, not a consumable release recommendation.

Keep the candidate parked. If this convenience capability remains a priority,
the next bounded question is **budget adequacy for the existing random-scan
kernel**, including initialization and per-coordinate effective sampling as the
number of roots grows. That would need its own prior-reviewed protocol and fresh
data; it is not authorization for a larger-budget retry now. Do not weaken the
accuracy gate or claim that a warning explains away its failure. Existing released
fixed/block proposal workflows remain available within their documented limits.


<a id="record-dependence-aware-proposal-research"></a>

## Dependence Aware Proposal Research

Original source: `docs/DEPENDENCE_AWARE_PROPOSAL_RESEARCH.md`. SHA-256: `dd23836ca4f8e3bbc1c71050dfe9f242117cb345bdc6176669bebbb3185a743d`.

### Dependence aware proposals and reliable pilot calibration

#### Recommendation and scope

**Approved graph-pilot development complete; no promotion.** The
[status and numerical audit finding](#record-dependence-pilot-status) record failed
ordinary reliability/cost gates and a tied-quantile discrepancy in the frozen
independent audit. Confirmation seeds remain untouched. The
frozen experiment (unpublished research source: `DEPENDENCE_GRAPH_PROTOCOL.md`) implemented the bounded
windowed joint-geometry learner, independent qualification and fresh production
described below. This retained assessment is not approval for a rescue experiment.

The objective is useful joint movement on correlated targets **and** a calibration
workflow that either earns independent evidence or refuses clearly. A positive
definite matrix alone does not establish useful geometry; a diagnostic pass does
not establish posterior accuracy. Keep those claims separate.

The three stages below were approved in order, subject to their exit gates. Keep the
released stopping estimator, defaults, strict `GaussianBlockCalibration.fit`
contract and parked coordinate tuner unchanged. The original assessment ran no
new sampling. Subsequent mathematical controls are test-only; the separately
approved internal graph adapter adds a narrow runtime path but no library
dependency, public API, default, promotion or release change. Research
provenance is [INF-21](#reference-inf-21), with transformations under
[INF-05](https://github.com/mattwilkinsphoto/figaro/blob/2ba1c55ee9b60c68fc1097b6b7bbb92fd558aa28/docs/RESEARCH_REFERENCES.md#inf-05), scalar adaptation under
[INF-06](https://github.com/mattwilkinsphoto/figaro/blob/2ba1c55ee9b60c68fc1097b6b7bbb92fd558aa28/docs/RESEARCH_REFERENCES.md#inf-06), prior experiments under
[INF-20](#reference-inf-20), and diagnostics under
[DIA-01](https://github.com/mattwilkinsphoto/figaro/blob/2ba1c55ee9b60c68fc1097b6b7bbb92fd558aa28/docs/RESEARCH_REFERENCES.md#dia-01).

#### The retained evidence points to geometry and calibration

The [completed requalification](#record-coordinate-requalification-status) retained
24 ordinary candidate accuracy failures: 14 correlated and 10 coupled Gamma/Beta
executions. These represent seven and five seeds respectively, each repeated in
two JVMs, not 24 independent failures. Known-covariance blocks passed all 64
executions on each correlated fixture, while learned-block pilots refused 62/64
moderate-correlation and 64/64 strong-correlation attempts. These are previously
validated results, not new measurements from this assessment.

This supports testing joint geometry, but **does not identify the best learning
algorithm or prove that covariance learning will solve the Gamma/Beta case**.
The strongest counterevidence is the existing default sampler: it failed only
2/64 Gamma/Beta and 2/64 separated-mode executions, although it performed poorly
on the factorized fixtures. Any candidate must face those controls rather than
only the weaker scalar tuner. The earlier no-promotion decisions remain closed.

#### What the literature and software suggest

| Approach | Evidence and tradeoff | Decision for this milestone |
| --- | --- | --- |
| Adaptive Metropolis with a fixed defensive move | Roberts and Rosenthal describe empirical joint covariance with an isotropic mixture. Their examples also show long learning transients and poor traversal of a curved target. | First candidate: learn geometry in bounded windows, then freeze. These window and qualification choices are Figaro research design, not a reproduction of their algorithm or theorem. |
| Robust adaptive Metropolis, RAM | Vihola updates proposal shape using direction and acceptance probability without estimating empirical covariance. This is attractive when second moments are unreliable, but its shape need not equal posterior covariance. | Reserve alternative, not an additional automatic rescue arm. It requires acceptance-probability and rank-update controls beyond the existing Boolean scalar tuner. |
| Windowed metric learning | Stan separates fast scale learning from expanding covariance windows. Dense metrics address linear dependence, not general nonlinear geometry. | Borrow the separation of timescales only. Stan is HMC; its transition efficiency and tuning parameters do not transfer to random-walk MH. |
| Automated blocking | Turek et al. compare blocking using mixing and computing cost, rather than correlation alone. NIMBLE exposes adaptive block sampling, but its `autoBlock` implementation is marked no longer actively maintained. | Caller-specified block of two to eight roots; no clustering/search over the full graph. |
| Barker and other gradient-based proposals | Livingstone and Zanella construct gradient-informed moves with a non-symmetric proposal correction. The October 2026 Barker extension reports gains that depend on geometry and total cost. | Worth a later gradient-contract assessment. Arbitrary Figaro constraint callbacks do not supply the required complete target gradient. Do not silently substitute finite differences. |

Sources inspected on 2026-10-26:

- Roberts and Rosenthal, [Examples of Adaptive MCMC](https://www.probability.ca/jeff/ftpdir/adaptex.pdf), revised January 2008, sections 1–2 and the curved example. The mixture construction motivates the first row; asymptotic conditions do not certify a finite pilot.
- Vihola, [Robust adaptive Metropolis algorithm with coerced acceptance rate](https://users.jyu.fi/~mvihola/vihola_-_ram.pdf), May 2011 manuscript of the 2012 paper, introduction and section 2, including its positive-definiteness argument. Later convergence proofs and experiments were not reproduced.
- [Stan 2.40 MCMC manual](https://mc-stan.org/docs/reference-manual/mcmc.html), warm-up windows, Euclidean metric and curvature discussion; [constraint transforms](https://mc-stan.org/docs/reference-manual/transforms.html), change of variables and lower/upper-bounded scalars.
- Turek et al., [Automated Parameter Blocking](https://arxiv.org/pdf/1503.05621), 2015 v1 preprint, sections 2–3; [NIMBLE manual](https://r-nimble.org/manual/cha-mcmc.html), sections 7.2.1.5 and 7.6.3; [current autoBlock source notice](https://raw.githubusercontent.com/nimble-dev/nimble/devel/packages/nimble/R/MCMC_autoBlock.R). The source notice qualifies the maintained-package comparison: it is not a recommendation to adopt that automatic routine.
- [AdaptiveMCMC.jl adaptation documentation](https://mvihola.github.io/docs/AdaptiveMCMC.jl/adapt/), AM, RAM and scaling interfaces. Useful independent implementation reference; not executed locally. Repository/license retrieval was unsuccessful, so reuse clearance is unresolved. No code is copied. [NIMBLE package metadata](https://raw.githubusercontent.com/nimble-dev/nimble/devel/packages/nimble/DESCRIPTION) declares `BSD_3_clause + file LICENSE | GPL (>= 2)`; file-level reuse would still need review.
- Livingstone and Zanella, [Barker proposal](https://arxiv.org/pdf/1908.11812), May 2020 v2 preprint of the 2022 paper, introduction and sections 3.2–3.3/Algorithms 1–2. [Vu, Livingstone and Samartsidis](https://arxiv.org/abs/2610.01448v1), October 2026, **abstract screening only**, not full validation or implementation evidence.
- Laitinen and Vihola, [adaptive MCMC convergence review](https://arxiv.org/pdf/2408.14903), 2024 v1 introduction, with [2026 publication record](https://doi.org/10.1214/26-EJP1509). Its discussion reinforces the need to justify adaptive kernels separately; the uniformly ergodic setting is not established for arbitrary Figaro graphs. No full-proof review is claimed.

The relevant papers were accessible without a paywall request. Failed HTML
endpoints were replaced by the indicated preprint PDFs; the Barker PMC endpoint
presented a browser challenge. Older preprints are identified explicitly rather
than described as the final journal text. No external code, dataset or numerical
benchmark was incorporated.

#### Fit with the current implementation

[GaussianBlockProposal](https://github.com/mattwilkinsphoto/figaro/blob/2ba1c55ee9b60c68fc1097b6b7bbb92fd558aa28/Figaro/src/main/scala/com/cra/figaro/algorithm/sampling/GaussianBlockProposal.scala)
already provides a fixed covariance, one joint decision, and restoration on
rejection for exact permanent `AtomicNormal` roots. Its matrix is in value units;
it is not an adaptive covariance or a posterior uncertainty estimate.
[GaussianBlockCalibration](https://github.com/mattwilkinsphoto/figaro/blob/2ba1c55ee9b60c68fc1097b6b7bbb92fd558aa28/Figaro/src/main/scala/com/cra/figaro/algorithm/sampling/GaussianBlockCalibration.scala)
fits pooled within-chain scatter only after scalar pilot diagnostic checks. It
requires a separate fixed-budget pilot and rejects insufficient exploration.

[SupportAwareProposal](https://github.com/mattwilkinsphoto/figaro/blob/2ba1c55ee9b60c68fc1097b6b7bbb92fd558aa28/Figaro/src/main/scala/com/cra/figaro/algorithm/sampling/SupportAwareProposal.scala)
has scalar real, positive and bounded transforms and their log Jacobians. It does
**not** provide a general joint transformed block. The
[MH dispatch](https://github.com/mattwilkinsphoto/figaro/blob/2ba1c55ee9b60c68fc1097b6b7bbb92fd558aa28/Figaro/src/main/scala/com/cra/figaro/algorithm/sampling/MetropolisHastings.scala)
has separate Normal-block and single-target support-aware paths, and
[ProposalScheme](https://github.com/mattwilkinsphoto/figaro/blob/2ba1c55ee9b60c68fc1097b6b7bbb92fd558aa28/Figaro/src/main/scala/com/cra/figaro/algorithm/sampling/ProposalScheme.scala)
is sealed. The supported scalar support-aware schemes cannot be composed into
the required joint transformed move.

The first work was a test-only vector kernel plus independent target controls.
The resulting architecture review explicitly approved a narrow internal
[joint transformed adapter](#record-dependence-pilot-status),
now locally validated. It adds a separate dispatch and transactional failure
boundary without replacing legacy proposals. No broader pilot lifecycle or
arbitrary graph support follows. Standalone-vector success alone cannot qualify
Figaro graph integration or later sampler promotion.

#### Proposed calibration workflow

1. **Learn, without claiming convergence.** Build four isolated training chains
   from declared dispersed starts, using a fixed initial diagonal scale in the
   chosen coordinates. Collect rejected states as well as accepted states.
   Use bounded windows to update within-chain covariance, with explicit diagonal
   shrinkage and a fixed positive-weight defensive kernel. Update scalar scale
   on a separate declared schedule; no state-dependent mixture weights. Record
   every update, eigenvalue/range refusal, bound hit and target evaluation.
2. **Freeze, then qualify independently.** Pool within-chain scatter rather than
   between-chain mean offsets, freeze one detached geometry, and use fresh RNG
   streams and fresh chains with no adaptation. Freeze diagnostic projections
   before these runs too. Check raw/transformed coordinates, narrow contrasts,
   squared projections and scientific event queries. Never refit using these
   qualification traces; changing the matrix would invalidate the check of the
   matrix actually used. Qualification produces an eligible or refused result,
   not a covariance or posterior-precision certificate.
3. **Produce separately.** Only an eligible result may start fresh production
   chains, with their own warm-up and fixed proposal. Neither training nor
   qualification draws enter reported posterior estimates. Preserve refusal
   reasons and all spent work. No retry-until-pass, threshold relaxation,
   hidden fallback, or use of the oracle geometry in candidate learning.

Use the existing default strict coordinate thresholds as a floor for the proposed
qualification check: finite rank/folded R-hat at most 1.01 and bulk, tail and
mean ESS at least 100 per chain (combined ESS divided by chain count). Do not
apply stationary-chain diagnostics to adapting training windows as a release gate.
The additional joint/scientific checks must be specified before running the study;
they are not a new universal reliability index. Measure accepted-but-inaccurate
qualifications against independent truth, as well as refusals of useful proposals.

##### Support transformations and arithmetic

For the proposed standalone controls, use identity coordinates for real roots,
log for positive roots and logit for open bounded roots. With `x = T(z)`, a fixed
symmetric random walk in z has log acceptance ratio
`log pi(xNew) - log pi(xOld) + log|J_T(zNew)| - log|J_T(zOld)|`.
Here `pi` includes all priors and evidence exactly once. The Jacobian has the
inverse-transform sign; it must not be added again by a graph adapter. This
change-of-variables derivation must be checked independently against forward and
reverse value-space proposal densities.

Do not clip endpoints, silently add jitter, or convert an arithmetic failure into
a successful sample. Declare regularization in scaled coordinates and report it;
over-regularization can destroy a narrow useful direction. Learning a covariance
in log/logit coordinates does not prove that the transformed target is elliptical.
Circular, simplex, matrix-valued, discrete, dynamic, observed/intervened and shared
roots are outside this first block contract. The original circular fixture remains
in regression inventory, with explicit candidate non-applicability rather than a
fabricated successful comparison.

#### Three bounded stages

These are the original approved bounds and acceptance requirements. Stage 1
finalized the machine-readable protocol (unpublished research source: `dependence_graph_protocol_v1.json`)
before stochastic development. The following stage-2 and stage-3 exit text remains
unchanged; the failed development result does not relax it.

##### Stage 1 Mathematical contract and independent controls

The original scope was the test kernel, deterministic proposal/covariance checks and
independent analytic/quadrature targets. One candidate family: windowed empirical
covariance plus bounded scale adaptation. A diagonal-only ablation distinguishes
geometry learning from extra training work. Do not add RAM, gradient methods,
tempering, ensemble coupling or automatic block search to rescue failures.
The separately approved internal adapter is the only runtime scope expansion;
all development and confirmation gates below remain unchanged.

Check detailed balance at fixed geometry, transform/Jacobian signs, repeated
rejections, rank-deficient/constant pilots, extreme units, endpoint rounding,
singular matrices, interruption, isolated ownership and an immutable production
matrix. Verify equivalence to the existing Normal block on matched states and
increments. Use independently coded matrix and target formulas, not a second call
to the candidate. A required runtime change stops this stage for architecture
review; tests must not bypass the released restrictions.

Freeze exact initialization, window lengths, scale bounds/updates, shrinkage,
defensive weight, target evaluation accounting, query set, seed separation and
all acceptance formulas. Values such as 0.234 or 2.38/sqrt(d) are hypotheses for
scaling, not universal constants or new Figaro defaults. No parameter grid is
authorized: choose one documented configuration before stochastic development.

##### Stage 2 Fixed development experiment with a firm exit

Use the original factorized 2/4/8, moderate correlation, Gamma/Beta, circular,
strong-correlation and separated-mode fixtures. Add at most one analytic curved
adverse control. Preserve all original truth values and query tolerances; compute
any new reference through independent analytic transformation or quadrature.

- Eight fresh development seeds, four chains, thinning one, one worker. Reserve
  32 disjoint confirmation seeds before running. Old reserved seeds are untouched.
- At most six arms per applicable fixture: candidate, diagonal ablation, released
  default, informed fixed scalar, unchanged strict calibration where applicable,
  and known-geometry block where available. The last is an oracle control, never
  a candidate input. Keep non-applicability distinct from refusal and numerical error.
- **Total cap per chain: 6,000*d transitions**, including all learning,
  qualification and production work. Proposed candidate allocation: `2,000*d`
  training; `500*d` qualification warm-up plus `1,000*d` qualification draws;
  `500*d` production warm-up plus `2,000*d` production draws. Nonlearning controls
  get `2,000*d` warm-up plus `4,000*d` retained draws. Allocate the strict pilot
  control within the same cap before freezing. This is a new cost-inclusive study,
  not an extension or repair of the closed requalification.
- Charge initialization, every density/constraint evaluation, adaptation,
  diagnostics and refused pilots. A joint transition can cost more than a scalar
  transition: report both transition counts and total evaluations/wall time.
  Equal transition counts alone are not equal work.
- Maximum nine fixtures, six arms and eight seeds: 432 scheduled development
  attempts. Four-hour process cap, 12-GiB memory cap and 4-GiB captured-output cap;
  hitting a cap is an incomplete study, not a passed gate. Do not truncate traces
  needed for the audit. No automatic rerun after a cap or statistical failure.

On the proposed ordinary in-scope fixtures, require all eight candidate
qualifications to deliver and every delivered scientific query to pass the
unchanged absolute accuracy checks. On moderate correlation, require at least a
20% reduction in the error-and-cost score defined below relative to
the **better complete released default or informed-scalar control**, selected by
the declared aggregate formula, not per seed. Require this score to be no
more than 1.25 times that same control on each remaining ordinary fixture.
Zero/missing comparator denominators make the comparison inconclusive, never a
win. Report oracle and ablation results separately.

For each query, normalize absolute error by its frozen accuracy tolerance; the
score is the maximum across queries of the mean squared normalized error,
multiplied by mean total elapsed seconds per scheduled attempt. Use all scheduled
seeds. A refused or missing estimate contributes a declared loss of one (the
accuracy boundary) for every query, and all its elapsed cost; unexpected execution
errors additionally fail the correctness gate. This refusal-penalized score is an
engineering decision rule, **not** an estimate of MSE for an unobserved result.
Also report actual MSE on delivered estimates, refusal counts, total evaluations
and total time separately. This prevents successful-pilot-only comparisons from
concealing the failed work when applying the same rule in confirmation.

Strong correlation, separated modes and the curved control remain adverse, with
all errors, refusals, contrasts and mode occupancy reported. A known inaccurate
production result that is declared qualified and lacks warnings on its inaccurate
scientific queries is a safety failure. This query-level check is deliberately
stronger than the older model-level any-warning check; it does not revise that
older result. Passing finite controls cannot guarantee unknown-mode detection.

Any required correctness, ordinary accuracy, calibration yield, safety or cost
gate failure ends development with no promotion and no consumption of confirmation
seeds. Do not turn a refusal into a zero-error result or drop difficult fixtures.

##### Stage 3 Fresh confirmation and separate promotion review

If development passes unchanged, run the 32 reserved seeds on the frozen design.
Repeat a fixed eight-seed subset in a second JVM for reproducibility; these repeats
are not extra independent statistical evidence. At most 2,160 scheduled attempts
including that subset, with the same per-process limits and no budget extension.

Require at least 30/32 eligible, accurate candidate outcomes **on every ordinary
in-scope fixture**, and no eligible inaccurate outcome. Refused attempts remain
in the denominator and total cost; report unconditional delivery probability and
conditional accuracy separately, including binomial uncertainty. The 30/32 rule
is a proposed engineering gate for this new opt-in workflow, not a relaxed version
of the old coordinate-tuner gate. Repeat all development cost and safety gates;
report uncertainty over seeds, rather than treating JVM repeats as independent.

A separate analysis implementation must recompute reference integrals, errors,
R-hat/ESS/MCSE, qualification decisions, inventory, cost ratios and gates from raw
traces without importing candidate or primary decision code. Add negative controls
that deliberately omit a Jacobian, use incorrect geometry, skip a narrow contrast,
include pilot draws, or drop a refused attempt; the audit must detect each defect.
Document Monte Carlo interval coverage and MCSE calibration alongside error and ESS;
a high ESS or acceptance rate cannot override observed error or undercoverage.

Even a full pass warrants only an integration proposal: owned joint transformed
graph moves, lifecycle/failure semantics, public examples and unchanged-stopping
regressions need review and independent graph validation before runtime promotion.
Do not infer commit, merge, release, automatic activation or a general convergence
guarantee from successful test-only confirmation.

#### Exit decision and related work

The work is worth one bounded experiment because known joint geometry already
outperformed scalar tuning on the retained correlated controls. Stop if learning
and qualification consume the gains, accepted fits remain inaccurate, or useful
results require repeated rescue. Return to application-driven use of explicit
proposals rather than automatically escalating to RAM or a more elaborate tuner.
Evidence that would change that recommendation is independently confirmed,
pilot-inclusive accuracy/cost improvement without loss of ordinary robustness.

[Manual blocks](https://github.com/mattwilkinsphoto/figaro/blob/2ba1c55ee9b60c68fc1097b6b7bbb92fd558aa28/docs/BLOCKED_PROPOSALS.md), [strict pilot calibration](https://github.com/mattwilkinsphoto/figaro/blob/2ba1c55ee9b60c68fc1097b6b7bbb92fd558aa28/docs/PROPOSAL_CALIBRATION.md),
[calibration limitations](https://github.com/mattwilkinsphoto/figaro/blob/2ba1c55ee9b60c68fc1097b6b7bbb92fd558aa28/docs/PROPOSAL_CALIBRATION_VALIDATION.md),
[multi-chain ownership and diagnostics](https://github.com/mattwilkinsphoto/figaro/blob/2ba1c55ee9b60c68fc1097b6b7bbb92fd558aa28/docs/MULTI_CHAIN_MCMC.md), and
[stopping criteria](https://github.com/mattwilkinsphoto/figaro/blob/2ba1c55ee9b60c68fc1097b6b7bbb92fd558aa28/docs/STOPPING_CRITERIA.md) remain the consumer guidance. No one
should adopt a research-only method by following this plan as an API tutorial.


<a id="record-dependence-pilot-status"></a>

## Dependence Pilot Status

Original source: `docs/DEPENDENCE_PILOT_STATUS.md`. SHA-256: `aa8b12b253010beda22e4aa1285113a7ce2749d182aa8cbe5ab6a07748aa8513`.

### Dependence pilot experiment and joint adapter status

**Status: bounded development complete; no promotion; frozen independent audit
failed on a tail-quantile numerical discrepancy. Confirmation is prohibited.**
The test-only orchestrator and approved internal adapter are not public APIs.
The [approved plan](#record-dependence-aware-proposal-research)
and [INF-21 provenance](#reference-inf-21) remain authoritative.
Stopping, defaults, strict calibration and all statistical exit gates are unchanged.
The later [separate numerical repair](https://github.com/mattwilkinsphoto/figaro/blob/43ce624eb97ad320509a6978a5ee5c3db6b7d1cb/docs/TIED_QUANTILE_REPAIR.md) corrects the tied
cutoff and passes retained-trace validation. It does not reopen this pilot or
retroactively pass its frozen audit.

#### Frozen graph pilot outcome

The frozen protocol (unpublished research source: `DEPENDENCE_GRAPH_PROTOCOL.md`) ran all 272 scheduled attempts
once, using eight fresh seeds and four isolated chains. No process or resource
limit failed. No confirmation seed was consumed. The recorded development decision
is no promotion: ordinary delivery/accuracy and cost requirements failed.

| Ordinary fixture | Candidate eligible and accurate | Required | Recorded score ratio to better complete control | Maximum |
| --- | --- | --- | --- | --- |
| Factorized 2D | 5/8 | 8/8 | 12.58 | 1.25 |
| Factorized 4D | 8/8 | 8/8 | 1.62 | 1.25 |
| Factorized 8D | 8/8 | 8/8 | 2.53 | 1.25 |
| Moderate correlation | 6/8 | 8/8 | 2.42 | 0.80 |
| Coupled Gamma/Beta | 6/8 | 8/8 | 8.49 | 1.25 |

All 33 delivered ordinary candidate outcomes passed the original scientific
accuracy checks; seven attempts refused qualification. On adverse fixtures the
candidate delivered one accurate strong-correlation result and refused the other
seven, and refused all eight separated-mode attempts. The recorded query-level
safety gate passed; refusal is not proof that every dangerous model will be detected.

Known-covariance blocks passed accuracy on 8/8 attempts for each correlated fixture.
Unchanged strict calibration refused 8/8 on each. The diagonal ablation refused
all moderate/strong-correlation attempts. These controls support the usefulness
of joint geometry, but do not qualify the learned workflow. Default and informed
controls both passed all circular attempts; the candidate remains inapplicable there.

Scores include refused attempts, diagnostics and raw-trace emission. They are
end-to-end prototype costs, not production-throughput claims. In particular,
failure here does not establish that joint adaptation can never be useful.
The firm exit forbids enlarging this pilot, dropping fixtures or promoting only
its successful subset.
The selected comparator is informed scalar for the factorized and moderate
correlation fixtures, and released default for coupled Gamma/Beta. Full aggregates,
refusal reasons and delivered-only coverage/MCSE results are in the
machine-readable outcome (unpublished research source: `dependence_graph_result_v1.json`).

##### Independent audit finding

The frozen audit recomputes diagnostics using NumPy/SciPy without importing the
candidate or primary decision implementation. It failed on the factorized 4D
candidate qualification trace for `z1`, seed 26011001. Mean, SD, R-hat, bulk ESS,
mean ESS and MCSE agreed; tail ESS was approximately 1474.92 in Figaro versus
1438.35 in the independent calculation.

The two order statistics at the 5% cutoff were both -0.6568859528122107. Figaro's
convex-combination interpolation rounded the cutoff one representable value lower,
to -0.6568859528122108, excluding ten tied draws. Replaying that arithmetic reproduces
the reported tail ESS. This is a finite-precision tied-quantile issue, not a changed
posterior or MH acceptance ratio. Both values exceed the qualification floor in
this first failing example; that alone does not establish safety for other traces.

[NumPy's linear quantile](https://numpy.org/doc/stable/reference/generated/numpy.quantile.html)
and [R's type-7 definition](https://stat.ethz.ch/R-manual/R-devel/library/stats/html/quantile.html)
provide the reference conventions inspected for this investigation. The frozen
audit failure remains part of the result. A separately labeled arithmetic replay
may characterize its extent, but cannot waive the independent-validation gate.
No runtime diagnostic, stopping rule or calibration threshold was repaired here.

The supplemental replay subsequently recomputed all 272 attempts and 5,798
diagnostic summaries. It found 45 tail-ESS differences, but no projection or
qualification decision changed. It reproduced the recorded geometry, scale updates,
references, scores and gate outcomes. Its result deliberately remains `passed=false`:
reproducing existing arithmetic does not repair or waive the frozen audit failure.
The replay also corrected its own treatment of exactly constant ranked chains,
where ordinary NumPy variance had returned a tiny positive roundoff value rather
than exact zero. Both failed audit revisions remain retained. See
[DIA-01](https://github.com/mattwilkinsphoto/figaro/blob/2ba1c55ee9b60c68fc1097b6b7bbb92fd558aa28/docs/RESEARCH_REFERENCES.md#dia-01) for diagnostic provenance.

Before development, 80 Scala regression/control tests, 12 independently recomputed
diagnostic grids, seven audit controls, five retained Jacobian failure controls and
independent reference integration checks passed. This new discrepancy demonstrates
why those finite controls were not sufficient evidence for promotion.

#### What has been checked

| Executed check | Result | What it establishes |
| --- | --- | --- |
| New Scala controls | 8 passed | Transform boundaries, proposal correction, common-state Normal-block arithmetic, repeated rejection, within-chain covariance, scale extremes, bounded learning/freeze and interruption preservation |
| Existing regression suites | 28 passed | Normal block, strict calibration and support-aware proposal regressions remain passing locally |
| Separate SciPy/NumPy arithmetic audit | 18 density-ratio cases and 168 covariance entries passed | Independent density/Jacobian and within-chain covariance calculations agree on the deterministic grid |
| Python audit controls | 5 passed | Includes missing/duplicate inventory and erroneous-ratio rejection; omitted-Jacobian errors detected in all 18 grid cases |

The initial Scala run passed 7 of 8 tests: a separate detailed-balance assertion
used the wrong Jacobian sign in the value-space proposal density. The assertion
was corrected; the kernel was not changed to obtain that pass. The failed test
revision and actual logs are retained. The first Python attempts failed because
SciPy was absent. The audit then used an isolated SciPy 1.18.1 installation with
Python 3.14.6 and NumPy 2.5.1; no Figaro dependency was added.

The prototype contains a deterministic vector kernel, fixed defensive mixture,
within-chain covariance pooling and a bounded thread-confined learning recorder.
At that initial checkpoint it did **not** contain a graph adapter. The approved
adapter is recorded below. That initial checkpoint preceded the now-completed
test-only orchestration and bounded development recorded above. Arithmetic agreement
alone did not independently validate the complete sampler.

#### Approved internal graph adapter

The architecture review is resolved by explicit approval of
JointTransformedBlock (unpublished research source: `JointTransformedBlock.scala`).
It is package-internal, opt-in, and not a public feature or default. Its fixed
covariance and defensive mixture do not perform calibration or adaptation.

| Current adapter validation | Executed result |
| --- | --- |
| Adapter control suite | 19 tests passed, including 32 independently derived whole-step decisions with both accepted and rejected outcomes |
| Separate actual-graph density audit | 18 Gamma/Beta graph ratios agree with SciPy densities and an independent value-space Jacobian calculation |
| Audit negative controls | 5 tests passed, covering missing/duplicate rows, omitted/doubled Jacobians and incorrect model scores |
| Existing regression suites | 54 tests passed across mathematical pilot controls, Normal blocks, strict calibration, support-aware proposals, owned observations and MCMC reliability |

Initial compilation attempts exposed test numeric-literal/probe-type errors, an
explicit heterogeneous evidence-map type requirement, and a test fixture argument
type error. Those failed logs remain retained; no statistical criterion was changed.
Local compilation also reported existing example match-exhaustivity and Windows
symbolic-link warnings. No remote CI or packaged-consumer check was run for this
unpromoted internal adapter.

##### Supported contract

- Two to eight distinct, exact permanent `AtomicNormal`, `AtomicGamma` or
  `AtomicBeta` roots, constituting all stochastic roots in one isolated universe.
  Subclasses, compound distributions and temporary roots are refused.
- Fixed local deterministic graphs made from exact `Constant`, `Apply1` through
  `Apply5`, and `Inject` nodes. Ordinary hard conditions and log constraints are
  allowed; observations, interventions, contingent evidence and dynamic/shared
  cross-universe graphs are refused. Graph membership, attached evidence and Beta
  learning parameters must not change after execution begins.
- Identity, log and logit coordinates respectively. Covariance is in transformed
  increment units and root order, not posterior uncertainty. A detached positive
  definite factor and explicit state-independent defensive weight/SD are fixed
  for the kernel lifetime. Singular matrices, rounded support endpoints and
  unrepresentable states/ratios fail explicitly; no clipping or hidden jitter.
- One complete proposal scheme, one joint decision, downstream updates after the
  roots. Composition inside sequential or disjoint schemes is refused. Roots use
  their actual value/randomness mappings; prior densities and the inverse-transform
  Jacobian enter exactly once. See [Stan's change-of-variables reference](https://mc-stan.org/docs/reference-manual/transforms.html)
  and [INF-21](#reference-inf-21).
- The adapter is bound to one sampler and executing thread. Initial controls used
  the owned fixed-budget runner with one worker. The later test-only pilot owns
  its static graphs explicitly and runs the same core MH steps in serial phases.
  This is not generic graph thread safety or support for thread migration between
  adaptive checkpoints.

Rejection restores touched graph state. Exceptions or interruption restore the
pre-step values, randomness, score cache, dissatisfaction and acceptance counters;
interruption remains set. Owned-runner failure disposes universes and workers.
RNG consumption and spent callback work are not rolled back. Callbacks must be
pure with respect to model structure and external state: rollback does not undo
arbitrary user I/O, external mutations or mutable objects changed in place.
The caller must not concurrently mutate or share the universe through another
sampler. No global graph lock or universal ownership detector was added.

The new path is dispatched separately; the previous MH step body is retained.
Stopping and strict calibration implementations, defaults, old protocols and
old results are unchanged. Adapter checks establish bounded graph conformance,
not calibration yield, posterior efficiency or a universal correctness guarantee.

#### Why the original architecture pause was necessary

The plan requires original coupled Gamma/Beta fixtures, actual released-sampler
comparators and total Figaro execution cost. The supported `GaussianBlockProposal`
accepts constant Normal roots only. `SupportAwareProposal` moves one target and
cannot be composed into the required joint transformed block through its supported
scheme. A standalone-vector candidate timed against graph-based controls would
confound proposal improvements with different graph overhead.

This dependency should have been resolved in the initial bounded plan. Its
explicit architecture-review gate was applied; this was not a failed statistical
gate and is not permission to drop the positive/bounded fixture.

The review considered two integration routes:

1. **Approved and implemented: a narrow internal transformed-block adapter**, restricted to
   fixed owned Normal/Gamma/Beta roots. One proposed vector, one downstream
   update, one MH decision and complete value/randomness rollback would be
   required. No default selection, arbitrary graph support or calibration
   threshold change. This explicitly approved expansion of the original test-only
   scope is the internal adapter described above.
2. A test-only custom-element/coordinator adapter using the public
   `Element.nextLogRandomness` hook and legacy proposal sequencing. This could
   avoid main-source changes, but replaces the original root implementations and
   introduces coordination, partial-update and cleanup obligations. Its existence
   means a runtime change is not proven unavoidable; it is not a verified shortcut.

#### Exit decision and next review

The graph-integration controls and test-only pilot are implemented. Development
failed its firm exit gates; confirmation and promotion remain closed. Preserve the
complete outcome inventory, failed frozen audit and all refused-pilot cost.

The separate [tied-quantile numerical review and repair](https://github.com/mattwilkinsphoto/figaro/blob/43ce624eb97ad320509a6978a5ee5c3db6b7d1cb/docs/TIED_QUANTILE_REPAIR.md)
is now locally validated. Actual Scala replay and independent recomputation of
all 5,798 summaries found 45 corrected tail ESS values and no retained projection,
qualification or strict-calibration decision changes. Non-tail diagnostics remain
bit-exact. The original failed audit above remains unchanged. Do not use the
numerical repair to reopen this learned-pilot result or spend reserved confirmation
seeds. Review the correction for ordinary CI and release integration separately.

No merge, release, version change or promotion follows from these controls.


<a id="record-gvm-complexity-selection-research"></a>

## Gvm Complexity Selection Research

Original source: `docs/GVM_COMPLEXITY_SELECTION_RESEARCH.md`. SHA-256: `21d8565f1165826c70da0caae2c30dccbf7153cfc1ed64ef492980dffa1ace77`.

### GVM complexity selection: relevance assessment and exit gate

Research provenance: [GVM-10](#reference-gvm-10), with the existing
singular-model reference in [GVM-07](#reference-gvm-07).

**Assessment complete; the narrower test-only experiment is now approved.** Complexity
selection is relevant to compact predictive density estimation, but it is not a
repair for unqualified higher-K fits. Do not implement an automatic selector under
the existing full-grid reliability contract. The only recommended follow-up is a
bounded, explicitly incomplete-comparison experiment, conditional on accepting
that different contract and identifying a concrete benefit. Otherwise park this
line and return to application-driven Figaro priorities. The subsequent
frozen execution protocol (unpublished research source: `GVM_SELECTION_PROTOCOL.md`) adopts explicit incomplete
comparisons and compact stored densities as its concrete benefit. Its
[execution status](#record-gvm-selection-status) records the separate result.

The assessment itself added no fits or selector. The approved follow-up is test-only,
with no public API, inference stopping estimator or default change. Earlier frozen
protocols and no-promotion results remain intact.

#### What the existing evidence does and does not establish

This is a review of the [whole-fit results](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/docs/GVM_WHOLEFIT_STATUS.md) and
[fresh joint confirmation](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/docs/GVM_JOINT_CONFIRMATION_STATUS.md), not a new analysis
of raw samples or another held-out trial. Their machine-readable results,
independent audits and reproducibility archives remain the numerical record.

- Joint confirmation qualifies all 80 K=2 fits and 69 of 80 K=3 fits. The unresolved
  K=3 cells are curved d=2, curved d=3 and seam d=3. A selector cannot turn their
  missing qualified alternatives into evidence against K=3.
- Curved truth has one component and seam truth has two, making redundancy a
  plausible issue. This association is not a causal demonstration. In particular,
  previous overlap cases also struggled despite having genuinely three components.
- The older whole-fit study qualified K=1 everywhere and selected smaller models
  on several families. It also explicitly reported qualification-induced selection
  bias. Its K=1 fits use different seeds and cannot complete the fresh K=2,3 grid.
- The latest candidate substantially improves completion without losing baseline
  successes, passes score margins, and completes the matched true-K=3 families.
  This is the strongest evidence against abandoning GVM fitting altogether.
- Total fit time nevertheless rises about 12%. Choosing a smaller final model
  does not recover time already spent fitting failed alternatives. Lower serving
  cost is plausible but unmeasured; no speedup or amortization claim is established.

**Current exit decision:** an unchanged fitter plus a selection wrapper cannot
meet the existing requirement to qualify every candidate reliably in each cell.
The current evidence therefore fails entry for that product. Do not raise caps,
replace failed selected starts with convenient successful ones, drop curved cases,
or relabel the old no-promotion result.

#### Three different contracts

| Intended capability | Honest result | Assessment |
| --- | --- | --- |
| Recover the true number of physical components | A claim about the generating process | Not justified by predictive score or these studies; out of scope |
| Compare every requested K using qualified fits | Selected model plus a complete candidate comparison | Not ready with the unchanged fitter |
| Select a useful predictive density among qualified candidates | Model, attempted/qualified K sets, failures, and comparison-completeness status | Plausible research question, not yet a public capability |

For the third contract, a possible report would distinguish
`QualifiedModel / ComparisonIncomplete` from `QualifiedModel / ComparisonComplete`.
These are proposed semantics, not implemented API names. Neither means a global
optimum, correct component count or guaranteed predictive accuracy. If no candidate
qualifies, return an explicit failure, not a numerical model labeled successful.
An incomplete comparison remains incomplete even when a smaller model predicts well.

#### Literature and existing solutions

Sources below were inspected on 2026-10-24. This is a targeted review of relevant
sections, not a full proof audit or an exhaustive literature survey.

1. **Singular mixtures and BIC.** Drton and Plummer's
   [singular BIC paper](https://arxiv.org/pdf/1309.0911), already indexed in GVM-07,
   explains why ordinary regular-model evidence approximations do not generally
   apply to overfitted mixtures. Sections 3.2, 4.1 and the learning-coefficient
   discussion were additionally inspected here. Some BIC consistency results still
   exist; it is not universally invalid. We have not established the coefficients,
   bounds or assumptions needed for a GVM-specific sBIC implementation. Treat BIC
   as a diagnostic comparison, not a certificate or a substitute for optimization.
2. **Selection itself can overfit.** Cawley and Talbot's
   [2010 study](https://www.jmlr.org/papers/volume11/cawley10a/cawley10a.pdf),
   introduction and evaluation discussion in section 5, motivates evaluating the
   entire selection procedure on data not used to choose models or tolerances.
   Training/validation/test separation is the proposed simple design here; do not
   recycle already-inspected confirmation scores as a new independent test.
3. **Density quality is not component-count recovery.** Cai, Campbell and Broderick's
   [2021 paper](https://proceedings.mlr.press/v139/cai21a/cai21a.pdf), introduction,
   Theorem 2.1 and section 3 assumptions, establishes Bayesian count inconsistency
   under specified misspecification, prior-support and component-family conditions.
   It does not prove failure of a bounded GVM predictive selector. It does warn
   against interpreting an adequate density as identification of physical classes.
4. **Clustering and prediction have different objectives.** The
   [mclust 5 paper](https://journal.r-project.org/articles/RJ-2016-021/), section 4,
   distinguishes BIC from the overlap-penalizing ICL criterion. Preferring cleanly
   separated clusters is not the same objective as preserving an overlapping
   density. ICL is therefore not the proposed default for this question.

Maintained Gaussian implementations already provide useful controls:
[mclust](https://mclust-org.github.io/mclust/) offers EM and model selection;
[scikit-learn GaussianMixture](https://scikit-learn.org/stable/modules/generated/sklearn.mixture.GaussianMixture.html)
offers explicit K, covariance and initialization controls. The latter's documented
EM convergence threshold concerns average lower-bound gain, not Figaro's all-block
physical stationarity check. Neither documented API is a wrapped linear-angular
GVM fitter. Applying a Euclidean Gaussian directly to a cut angle is not a valid
seam-aware replacement. These API reviews are not source audits or executed controls.

mclust declares GPL >= 2; it is reference-only here. No external implementation,
dataset or package is copied, installed or executed. Any future backend reuse needs
a pinned license/compatibility review. Existing isolated research tools and stored
results are sufficient for this assessment. The concrete gap is selection with
explicit GVM qualification and incomplete-comparison semantics, not lack of generic
mixture-selection software. A new optimizer, Bayesian mixture engine, merge/prune
scheme or generic framework is not warranted by this assessment.

#### Conditional bounded plan for review

These are proposed decision rules, not published guarantees, a frozen protocol,
or authorization to implement. No fresh seed is consumed before the protocol is
reviewed and frozen. A failed stage ends this sequence; it does not start another
optimizer or budget-tuning campaign.

##### Stage 1: contract and relevance gate

Explicitly choose whether an incomplete-comparison predictive result is useful.
If the requirement remains complete, reliable comparison across all K, stop now.
Do not quietly substitute the narrower contract.

Identify the consumer benefit before implementing: smaller stored densities or
lower end-to-end cost at a declared number of subsequent density evaluations Q.
Specify a meaningful reduction and acceptable predictive loss before looking at
new results. Q must come from an intended usage scenario, not a post-hoc choice
that amortizes an unfavorable fit cost. If no such benefit can be named, stop.

Keep scope at d=2,3, one angle, K=1,2,3 and the five existing families. Preserve
curved, seam, overlapping, separated and misspecified wrapped-Gaussian cases.
True Gaussian component count is not an oracle for the best GVM approximation K.
Use the unchanged optimizer, starts, physical qualification, bounds and per-fit
budgets. No automatic early pruning: weak K=2 improvement does not rule out K=3.

##### Stage 2: one test-only development screen

After approval, allow one development batch of at most two new seeds per existing
dimension/family cell. Fit each K independently on the same training split; choose
only using validation. Evaluate one candidate rule: the smallest qualified K whose
mean validation log score is within a predeclared tolerance of the best qualified
validation score. A tolerance is a decision parameter, not a confidence guarantee.
Do not tune it against the final test split or equate it with the old test margin.

Controls: fixed K=2 and the highest-validation-score qualified model from the same
K grid. Report all failed candidates and all attempted costs. Add independently
computed selection/score controls for ties, empty qualified sets, incomplete grids,
label permutations and circular seams. Recheck every selected model numerically.
Retain existing wrapped-GMM evidence as context, not a cost-matched certified
competitor; establishing equal qualification would be a separate prerequisite for
any GVM-over-GMM claim, not an extra scope item smuggled into this screen.

If the mechanism cannot preserve the declared density quality and demonstrate the
named compactness or serving-cost benefit on development data, stop. Passing permits
one frozen confirmation, not production integration. No second candidate rule,
optimizer change, enlarged cap or replacement development batch in this milestone.

##### Stage 3: one independent fresh-seed confirmation

Freeze the code, choice rule, tolerance, budgets, benefit threshold, Q (if relevant),
fresh seed IDs and simultaneous uncertainty method before data generation. Use at
most eight fresh seeds per existing dimension/family cell, independent training,
validation and test splits, and the prior split sizes. Retain all failures. Timing
repetitions do not add independent statistical samples.

Proposed gates for the narrower contract, to be finalized at Stage 1:

- Independent density/gradient, selection, complete-inventory and failure checks pass.
- At least 95% empirical qualified-output availability in each cell, with no lost
  qualified fixed-K=2 cases. Report complete-grid coverage separately; availability
  must never be called complete-comparison reliability. With eight seeds, the
  empirical threshold requires eight successes, not a population guarantee.
- Simultaneous held-out score noninferiority against both controls in every cell.
  The prior 0.02 nats/point margin is a proposed starting decision for review, not
  automatically suitable for a new consumer. Also predeclare regional probability
  checks for genuinely three-component/overlapping laws so a global score cannot
  hide loss of a small region. These checks do not guarantee missing-mode detection.
- Demonstrate the Stage 1 benefit against the validation-best qualified comparator,
  counting fitting, all starts/failures, validation/selection, preparation and serving.
  Report the fixed-K=2 tradeoff too. Fewer components or data passes alone do not
  demonstrate lower total cost; no post-hoc choice of Q or success-only denominator.

Any failed correctness, reliability, score, regional-probability or declared benefit
gate means **park higher-dimensional selection and return to other Figaro work**.
Retain unfavorable results and do not repeat confirmation with tuned seeds or caps.
A pass supports a separate promotion review only for the explicit predictive
contract; it does not overturn any old fixed-K no-promotion result or authorize
runtime integration. Larger K/d, multiple angles, true-count recovery, application
fusion, released stopping and defaults remain outside scope.

#### Recommendation and completion audit

The assessment recommended reviewing the narrower contract and benefit before
implementation; the approved protocol above records that choice. If complete
comparison is required, or there is no concrete compact-density
use, end this line here. The next alternative to assess is broader multi-latent
warm-up/proposal integration, building on the existing
[joint proposal API](https://github.com/mattwilkinsphoto/figaro/blob/2ba1c55ee9b60c68fc1097b6b7bbb92fd558aa28/docs/JOINT_PROPOSALS.md), not replacing it or automatically scheduling
another research campaign.

Verified complete at assessment: literature-led relevance review, existing-evidence
reconciliation, firm exit gate and bounded conditional plan. The subsequent
[test-only execution](#record-gvm-selection-status) has its own outcome and independent
validation; it does not revise the older studies. Citation and link checks do not
establish statistical utility.


<a id="record-gvm-selection-status"></a>

## Gvm Selection Status

Original source: `docs/GVM_SELECTION_STATUS.md`. SHA-256: `0b828ee8db33c7180fa468d88c14622d6dcd52714e20d314108cae8d2f1097c6`.

### Predictive density selection experiment status

Research provenance: [GVM-10](#reference-gvm-10).

**Development complete; firm exit triggered; no promotion.** The selector returns
qualified predictive models for all 20 datasets and passes independent numerical,
score and regional-probability checks. It reduces the declared storage measure by
only 3.98%, below the predeclared 20% requirement. Do not run confirmation, increase
the tolerance, change the benefit threshold or start another optimizer cycle.
Park this higher-dimensional selection line and return to other Figaro priorities.

The protocol (unpublished research source: `GVM_SELECTION_PROTOCOL.md`) and
source-hashed configuration (unpublished research source: `gvm_selection_protocol_v1.json`) were frozen
before generating the single development batch. The protocol SHA-256 begins
`58ae35b7c18e19fe`. Its source map preserves the unchanged optimizer and six prior
frozen studies. The independent result (unpublished research source: `gvm_selection_development_v1.json`)
and descriptive breakdown (unpublished research source: `gvm_selection_description_v1.json`) retain every
case and comparison. The eight confirmation seeds were not used.

#### Gate results

| Development gate | Observation | Decision |
| --- | --- | --- |
| Independent numerical, inventory and selection validation | All 60 fits retained and audited; no missing models or exceptions | Pass |
| At least 95% qualified-output availability in every cell | 2/2 in all ten cells; 20/20 overall | Pass |
| No loss of a qualified fixed-K=2 case | None; fixed K=2 qualifies in 19/20 | Pass |
| Every test-score difference against both controls >=-0.02 nats/point | Worst difference -0.01483 | Pass |
| Every specified linear-region error <=0.05, and error increase <=0.02 | Maximum error 0.02436; maximum increase 0.01632 | Pass |
| At least 20% lower aggregate float64 payload versus validation-best qualified | 916 versus 954 scalars: 3.98% reduction | **Fail** |

These are descriptive development outcomes, not independent confirmation or
population guarantees. There were no confidence-bound claims from two seeds.
The storage measure includes every model's full covariance and whitening metadata;
it excludes optimizer traces, serialized text and JVM object overhead. It must not
be described as measured heap, archive-size or runtime savings.

#### Why the benefit was small

The validation-best qualified control already chose K=1 on four datasets, K=2 on
eleven and K=3 on five. The tolerance rule changed only two choices:

- d=3 wrapped-Gaussian truth, seed 1350001: K=2 to K=1.
- d=2 overlapping GVM truth, seed 1350002: K=3 to K=2.

The final counts were K=1 on five datasets, K=2 on eleven and K=3 on four.
Thus the additional parsimony rule usually agreed with ordinary validation-best
selection. This observation explains its small incremental storage benefit on
this grid; it does not establish how a different workload would behave.
Fixed K=2 would store 940 scalars, so the candidate saves only 2.55% against that
control as well. No post-hoc target or tolerance change is justified by these data.

The strongest evidence in favor of the narrower contract is that it returned a
qualified predictive density everywhere without failing either quality check.
That is useful research evidence, but not the substantial additional benefit
required to justify this implementation milestone.

#### Incomplete comparisons remain visible

Fourteen datasets have `ComparisonComplete`; six have `ComparisonIncomplete`.
K=1 qualifies in 20/20, K=2 in 19/20 and K=3 in 15/20. Every unqualified fit is
`SolverStoppedUnqualified`; it remains in the record and cost totals.

The incomplete cases are d=2 curved and d=2 overlap on seed 1350001; d=3 curved on
both seeds; and d=3 seam and d=3 wrapped-Gaussian on seed 1350002. The curved d=2
case has an unqualified K=2 fit despite qualified K=1 and K=3 fits. This is another
reason not to interpret failed qualification as evidence for redundant components.
The older confirmation's all-K=2 success does not guarantee success on new samples.

Qualified-output availability is deliberately separate from complete-comparison
coverage. The selector also has an explicit `NoQualifiedModel` path, tested by
negative controls but not encountered in these 20 datasets. No failed alternative
is silently discarded from the reported candidate inventory.

#### Costs and numerical evidence

All 60 fits cost 34.41 seconds elapsed and 34.28 seconds process CPU on this host;
validation and selection bring the recorded search total to 34.43 seconds.
Recorded fit work totals 370.74M units, including failed fits. Fixed K=2 alone costs
8.83 seconds and 101.03M units. No fit was rerun or excluded from those totals.
The full stage takes 69.10 seconds including input/output, data generation and
access operations. These are single-run isolated Python research timings, not a
Scala benchmark or a statistical speedup comparison. There is no serving-cost
claim and no assumed number of subsequent queries used to amortize the extra fits.

Independent validation actually executed:

- Twelve deterministic controls cover ties, missing/failed candidates, bad status
  claims, nonfinite scores, duplicate inventories, validation-only selection,
  payload counting, label permutations, circular seams, raw-coordinate conversion
  and the failed-development confirmation guard. Nine unchanged optimizer controls
  also pass before freezing.
- All 20 exact input hashes, all 60 individual fit records and all 20 selection
  records agree with the consolidated inventory. Choices and exit gates are
  reconstructed without importing the selector or runner.
- Every retained model, including all six unqualified fits, has all-coordinate
  physical finite-difference checks: maximum discrepancy `9.50e-8` versus `2e-6`.
- 160 high-precision density points include fitted models and generating laws:
  maximum log-density discrepancy `4.45e-15` versus `1e-8`. All train, validation
  and test scores are recomputed; maximum mean-score discrepancy `8.89e-16`.
- Independent high-precision erf calculations check the declared linear marginal
  probabilities, including whitening reversal: maximum discrepancy `1.67e-16`.
  These interval checks do not certify angular, joint-tail or arbitrary missing-mode
  coverage. The synthetic families are not representative consumer acceptance data.

#### Reproduction and closeout

The runner (unpublished research source: `gvm_selection_study.py`),
independent auditor (unpublished research source: `gvm_selection_audit.py`),
deterministic controls (unpublished research source: `test_gvm_selection.py`) and
descriptive/archive tool (unpublished research source: `gvm_selection_evidence.py`) preserve the complete
procedure. The private evidence archive contains exact inputs and hashes, source
revisions, captured commands/output/timestamps, software versions, machine-readable
results, a plot, and rerun instructions. The archive is read-back verified before
handoff. Reproduction does not create a new independent trial or permit confirmation.

Verified complete: the approved bounded development experiment and independent
audit. Confirmation: intentionally not run because the firm exit gate failed.
Promotion: not qualified. Released stopping, defaults, optimizer, prior results,
runtime dependencies and public APIs remain unchanged. No merge or release follows.


<a id="record-multilatent-warmup-research"></a>

## Multilatent Warmup Research

Original source: `docs/MULTILATENT_WARMUP_RESEARCH.md`. SHA-256: `ab34d240f68b7d752948b28a96d6e2f1adcdca1c2481adbabd8750b9a3008d45`.

### Multi latent proposal warm up assessment

**MCMC-WARMUP-01 — bounded experiment complete at its development exit gate; no promotion.**
The investigated extension is opt-in warm-up for several fixed scalar latent
variables in an owned Figaro graph. Tune each variable's proposal scale during
discarded warm-up, then freeze every scale before retaining samples. Keep the
released single-scalar API, stopping estimators and defaults unchanged.

This fills a concrete API gap without adding another density fitter. It does not
solve strong posterior dependence, discover missing modes or make arbitrary
mutable graphs thread-safe. The GVM fitting and predictive-selection experiments
remain parked at their recorded exit gates.

[Research provenance](#reference-inf-20). Assessment date: 2026-10-25.

The narrow internal refactor was explicitly approved and implemented. Independent
transition/lifecycle controls and the source-hashed development study are complete.
The candidate passes ordinary accuracy but misses the required material utility
gain on the four-root fixture. Fresh confirmation was therefore not run. See the
frozen protocol (unpublished research source: `MULTILATENT_WARMUP_PROTOCOL.md`) and
[all-outcome status](#record-multilatent-warmup-status). The plan below is retained as
design provenance, not an instruction to repeat the experiment or relax its gates.

#### Stage 1 integration decision

The implementation review found that reusing the owned runner needs a narrow
production refactor, even if the multi-latent coordinator remains test-only.
`execute`, `Owned` and `Kernel` are private; `Kernel` is also final. The runner
accepts only the final scalar `Tuning` class and checks for exactly one latent.
The public model factory has no callback after a completed transition or at the
warm-up/production boundary.

`DisjointScheme` can record which coordinate was selected, but its callback runs
before the accept/reject decision. Observable projections run only for retained
draws. Neither supplies the exact outcome stream needed for per-coordinate
warm-up. Inferring acceptance from value changes would confuse rejected moves
with accepted moves that leave the represented value unchanged.

The existing ordinary-MH subclass pattern can support isolated transition tests,
but does not reuse this runner's likelihood handling, chain ownership and cleanup.
Copying those mechanisms into a test runner would create a second integration
implementation and weaken the evidence for later production reuse.

**Approved and implemented decision:** introduce a package-scoped warm-up controller and a
package-scoped execution entry. Keep the multi-latent controller in test sources.
Adapt the existing single-scalar tuner internally without changing its public
constructor or behavior. Do not expose `Owned`/`Kernel` or remove the single-latent
safety check from `runTuned`.

The internal contract provides the scheme, validates owned latent
coverage, begins after initialization, resets the selected-coordinate recorder before
each warm-up transition, receives its actual acceptance outcome afterward, and
freezes before retained draws. Membership validation continues in production;
adaptation callbacks must not. Callback failures must follow existing chain
failure and cleanup paths, not return partial success.

This changes production orchestration, so it is **not** entirely test-only work.
Explicit approval preceded implementation. The final local regression run passed
119 tests, including unchanged single-scalar, untuned, observation, stopping,
block and diagnostic suites and 14 new controls. The refactor preserves public
constructor signatures and existing proposal/acceptance calculations. It does
not expose the private kernel or promote a public multi-latent tuner. Full remote
CI, packaged consumers and public API review were not performed for promotion;
the development experiment stopped first. The original integration decision was
not a reason to relax the utility gates.

#### What already exists

| Existing capability | Actual boundary | Implication for this milestone |
| --- | --- | --- |
| `MultiChainMetropolisHastings.runTuned` and `SupportAwareProposal.tune` | Exactly one unobserved, unintervened stochastic latent; fixed scalar kernel and known inverse randomness map | Extend orchestration, not the scalar transform or tuning formula |
| `DisjointScheme` and fixed support-aware moves | Caller supplies alternatives and weights; no per-coordinate automatic warm-up orchestration | Reuse fixed random selection and the existing MH acceptance path |
| `GaussianBlockProposal` and `GaussianBlockCalibration` | Fixed ordinary `AtomicNormal` roots; pilot diagnostics and covariance qualification | Retain as a correlation-sensitive control, not a missing feature to recreate |
| Aligned multi-observable MH traces | Every observable uses the same retained draw indices within each chain | Preserve dependence when exporting traces; never shuffle columns independently |
| `VectorImportance.fitGaussian`, mixture fitting and `runWithPilot` | Explicit vector targets/pilots and their bounded numerical contracts | Already provide pilot-to-frozen-proposal workflows; no new fitter is needed |
| Conditional joint proposals and `GraphProposalImportance` | Explicit normalized proposals, fresh owned graph and full importance correction | Not automatic extraction or rewriting of an arbitrary graph |

Implementation map: [owned MH runner](https://github.com/mattwilkinsphoto/figaro/blob/2ba1c55ee9b60c68fc1097b6b7bbb92fd558aa28/Figaro/src/main/scala/com/cra/figaro/algorithm/sampling/parallel/MultiChainMetropolisHastings.scala),
[scalar proposals](https://github.com/mattwilkinsphoto/figaro/blob/2ba1c55ee9b60c68fc1097b6b7bbb92fd558aa28/Figaro/src/main/scala/com/cra/figaro/algorithm/sampling/SupportAwareProposal.scala),
[proposal dispatch](https://github.com/mattwilkinsphoto/figaro/blob/2ba1c55ee9b60c68fc1097b6b7bbb92fd558aa28/Figaro/src/main/scala/com/cra/figaro/algorithm/sampling/MetropolisHastings.scala),
[block calibration](https://github.com/mattwilkinsphoto/figaro/blob/2ba1c55ee9b60c68fc1097b6b7bbb92fd558aa28/Figaro/src/main/scala/com/cra/figaro/algorithm/sampling/GaussianBlockCalibration.scala).
The existing single-latent check prevents other parameters from silently freezing.
Removing that check alone would be incorrect.

#### Literature and alternative implementations

[Roberts and Rosenthal, Examples of Adaptive MCMC](https://www.probability.ca/jeff/ftpdir/adaptex.pdf),
section 3 of the revised January 2008 author manuscript, motivates separate
coordinate scales in Metropolis-within-Gibbs. Its batch adaptation is not Figaro's
per-attempt update. Its examples support investigating scale mismatch, not a
transferable speedup prediction; the preceding curved example also illustrates
poor mixing despite adaptation.

[Latuszynski, Roberts and Rosenthal, Adaptive Gibbs samplers and related MCMC methods](https://arxiv.org/pdf/1101.5838),
2013 preprint revision, sections 2–3, distinguishes fixed from adaptive coordinate
selection. Its nonergodic example cautions against treating convergence of tuning
parameters as sufficient. Keep selection weights fixed and state-independent in
this milestone. The later proofs were not audited here.

[Garthwaite, Fan and Sisson](https://arxiv.org/pdf/1006.3690), preprint section 2,
provides the stochastic-approximation background already recorded in
[INF-06](https://github.com/mattwilkinsphoto/figaro/blob/2ba1c55ee9b60c68fc1097b6b7bbb92fd558aa28/docs/RESEARCH_REFERENCES.md#inf-06). Reuse Figaro's bounded log-scale update;
do not present it as the paper's optimal-steplength algorithm. An acceptance
target such as 0.44 is a tuning policy, not a universal optimal value or evidence
of posterior accuracy.

The maintained [NIMBLE MCMC manual](https://r-nimble.org/manual/cha-mcmc.html),
sections 7.2.1.5 and 7.2.2, documents per-node sampler configuration, adaptive
controls and automated blocking. Blocking is assessed using the slowest node's
ESS per computation time, and searching for blocks has its own cost. This is a
useful implementation comparison, not a Scala drop-in or a reproduced benchmark.

[Pathfinder](https://www.jmlr.org/papers/volume23/21-0889/21-0889.pdf)
(Zhang, Carpenter, Gelman and Vehtari, 2022; abstract, introduction and reported-cost
discussion inspected) is a stronger alternative when a differentiable vector
log density is available. The [Stan 2.40 manual](https://mc-stan.org/docs/2_40/reference-manual/pathfinder.html)
describes its quasi-Newton Gaussian approximations and importance diagnostics.
The inspected Figaro graph runner has no corresponding user-supplied joint-gradient
contract. Adding that backend would be a separate architectural project, not a
small warm-up extension. Published Pathfinder gains are not Figaro measurements.

**Recommendation:** reuse Figaro's existing kernels and owned runner. NIMBLE and
Stan demonstrate maintained alternatives for broader model recompilation/backend
work, but introducing an R/C++ model boundary does not fill this Scala API gap.
No external source code, dataset or new dependency is incorporated by this plan.
Any later backend adoption needs its own compatibility and license review.

#### Bounded proposed contract

Start with **two to eight named scalar latent roots**. Each must be permanent,
active, unobserved, unintervened and fixed-parameter, owned by the chain's universe,
and accepted by the existing automatic scalar adapter. Cover the complete latent
set exactly once. Real, positive, bounded and circular coordinates are in scope;
matrix/simplex blocks and unknown custom randomness maps are not.

The prior roots may be independent while their posterior is coupled by static
deterministic descendants and supported likelihood observations or constraints.
A compound latent such as `Normal(otherLatent, variance)` is **not** a fixed root
and remains outside this contract. Do not silently rewrite hierarchical models,
drop latent variables, or fall back to a different kernel. Dynamic latent creation,
nested/shared graphs and observed deterministic expressions remain excluded.

Use an additive API rather than changing the meaning of `runTuned`, `TunedModel`
or existing constructors. Require explicit positive finite selection weights and
per-coordinate tuning settings. Validate their normalized representation; do not
accept a weight that becomes numerically unselectable. API names and the final
numerical weight limits belong to stage 1, before implementation is frozen.

One transition selects one coordinate and performs one existing support-aware MH
move. A local recorder identifies the selected coordinate; only that coordinate's
tuner receives the acceptance/rejection outcome. Use its own attempt count in the
gain schedule. Initialization is not tuning, rejection still counts as an attempt,
and updating all tuners from one chain-wide acceptance value is forbidden.

Retain the full prior, likelihood and transform-Jacobian correction. For fixed
selection weights, each coordinate kernel preserves the same target and their
mixture preserves it as well. This invariant-target argument does not establish
ergodicity for every model or make the first retained draw stationary.

Warm-up has an explicit total transition budget, not that budget multiplied by
the number of roots. Report per-coordinate attempts, acceptances, final scale and
scale-bound warnings; their attempt counts must sum to the total. Require an
explicit minimum number of attempts per coordinate. If the budget does not provide
them, return a calibration failure; do not extend it secretly. Freeze scales and
selection weights before production. Keep initialization and discarded warm-up
in end-to-end cost reports.

Create fresh tuners inside each chain factory. Revalidate latent membership and
ownership after graph updates and at the production boundary. Reports must be
detached from mutable elements and retain stable coordinate names. Exact replay
across worker counts requires deterministic initialization fixtures; do not make
a blanket bitwise-replay promise for hash-ordered multi-element initialization.

#### Three stages and exit gates

##### Stage 1 Contract and independent transition controls

Specify the additive API, supported graph patterns, selection-weight numerical
limits, budget semantics and failure statuses. Freeze direct calculations of
log acceptance for real, positive, bounded and circular moves, including coupled
likelihoods. Calculate reference densities and Jacobians independently of the
production adapter. Exercise exact dispatch accounting, rejection, scale bounds,
zero-attempt coordinates, duplicate/omitted targets, unsupported targets, reused
tuners and graph mutation.

**Exit:** proceed only if the transition and lifecycle contracts can reuse existing
MH semantics without arbitrary graph rewriting or shared mutable state. Stop for
architectural review if that boundary cannot be maintained.

##### Stage 2 Test only multi scalar orchestration

Implement a test-only coordinator using the existing coordinate kernels and the
existing log-scale tuning rule. Avoid a second MH engine. If test access requires
a production visibility/refactoring change, review that explicitly rather than
calling the whole change test-only. Verify freeze-before-retention, alignment,
cleanup on cancellation/failure, per-chain ownership and deterministic-fixture
worker replay. Retain all single-scalar regressions unchanged.

Use analytic factorized controls at 2, 4 and 8 roots; a two-root correlated
Gaussian posterior; a coupled positive/bounded posterior with independently
refined two-dimensional quadrature; and a circular seam control. Keep a strongly
correlated case and a separated multimodal case as adverse controls. The initial
matrix tests useful limits, not universal support.

**Exit:** all deterministic correctness and lifecycle checks must pass. Numerical
refusals and inaccurate references are failures to investigate, not discarded
observations. Do not widen numerical ranges merely to obtain test completion.

##### Stage 3 Frozen independent utility study and promotion review

Before confirmation, freeze model parameters, queries, initialization, explicit
scale bounds, transition budgets, development/held-out seeds, reference accuracy,
failure treatment and quantitative acceptance margins. Use a small development
screen, followed by fresh held-out seeds only if its declared exit gate passes.
Do not reuse the GVM experiments' seeds or alter earlier protocols.

Compare with fixed support-aware random scan using the same selection weights,
the unchanged default MH runner, and informed fixed scales. For the correlated
Gaussian control also include the existing block/calibration route, counting
its pilot cost. Informed scales/covariance are labeled oracle controls if derived
from exact posterior information, not free automatic competitors.

Evaluate posterior mean and tail/event-probability error against independent
answers, uncertainty calibration, coordinate and derived-query diagnostics,
refusals, mode occupancy and total work/time including initialization, warm-up
and unsuccessful runs. Count scalar transitions rather than equating one
coordinate move with a full sweep. Include repeated timing runs and numerical
error in the utility decision; acceptance rate or ESS alone cannot pass it.

Separate two decisions: **safe opt-in capability** and **performance improvement**.
Both require correctness and declared inference-quality gates. The latter also
requires a predeclared material accuracy-adjusted total-cost improvement on the
scale-mismatch workload and no unacceptable control regression. Do not promise
an order-of-magnitude gain or claim the candidate beats tuned blocks in correlated
models. If utility fails, retain the result and stop; any narrower usability-only
promotion needs a separate decision, not a retrospective relaxation of the gate.

Public integration requires independent validation, user/API examples, full CI
and packaged-consumer acceptance. Promotion and release remain separate approvals.
Stages 1 and 2 passed their local controls. Stage 3 completed its development
screen and firm no-promotion decision; conditional confirmation remains unrun
because the utility gate failed. The internal integration seam exists in runtime
sources, but the multi-latent controller and driver remain in test sources.

#### Deferred work and related capabilities

Learned scan weights, full-covariance transformed blocks, automatic blocking,
discrete/continuous hybrid tuning, arbitrary hierarchical graphs and gradient
backends are deferred. Poor mixing caused by correlation may favor the existing
Gaussian block route; missing-mode risk is not repaired by scalar adaptation.

After a qualified multi-latent pilot exists, its aligned traces can feed existing
Gaussian/mixture proposal fitters, subject to their support and diagnostic limits.
That handoff does not require another fitting algorithm. It must not treat weighted
importance draws as unweighted posterior draws or omit a coordinate-change Jacobian.

Related: [capability boundaries](https://github.com/mattwilkinsphoto/figaro/blob/2ba1c55ee9b60c68fc1097b6b7bbb92fd558aa28/docs/CAPABILITY_EXPANSION.md),
[block proposals](https://github.com/mattwilkinsphoto/figaro/blob/2ba1c55ee9b60c68fc1097b6b7bbb92fd558aa28/docs/BLOCKED_PROPOSALS.md), [vector proposals](https://github.com/mattwilkinsphoto/figaro/blob/2ba1c55ee9b60c68fc1097b6b7bbb92fd558aa28/docs/VECTOR_IMPORTANCE.md),
[mixture proposals](https://github.com/mattwilkinsphoto/figaro/blob/2ba1c55ee9b60c68fc1097b6b7bbb92fd558aa28/docs/MIXTURE_PROPOSALS.md), [joint proposals](https://github.com/mattwilkinsphoto/figaro/blob/2ba1c55ee9b60c68fc1097b6b7bbb92fd558aa28/docs/JOINT_PROPOSALS.md),
[graph proposal bridge](https://github.com/mattwilkinsphoto/figaro/blob/2ba1c55ee9b60c68fc1097b6b7bbb92fd558aa28/docs/GRAPH_PROPOSALS.md),
[multi-chain vector sampling](https://github.com/mattwilkinsphoto/figaro/blob/2ba1c55ee9b60c68fc1097b6b7bbb92fd558aa28/docs/MULTI_CHAIN_VECTOR_SAMPLING.md),
[diagnostics provenance](https://github.com/mattwilkinsphoto/figaro/blob/2ba1c55ee9b60c68fc1097b6b7bbb92fd558aa28/docs/RESEARCH_REFERENCES.md#dia-01),
[parked GVM selection](#record-gvm-selection-status).


<a id="record-multilatent-warmup-status"></a>

## Multilatent Warmup Status

Original source: `docs/MULTILATENT_WARMUP_STATUS.md`. SHA-256: `e85a020c9d68305570b6e30825c0f365a0ded706a747cadd0df943f4caf51a11`.

### Multi latent warm up experiment status

**MCMC-WARMUP-01 v1: no promotion; stopped at the development exit gate.**
The approved narrow internal runner refactor and test-only coordinator are
implemented. Ordinary posterior accuracy passes, but the required material
accuracy-adjusted cost gain does not pass every factorized fixture. Keep the
candidate parked and the unused confirmation seeds reserved. No defaults,
stopping estimator, public multi-latent API or released artifact was changed.

[Research provenance](#reference-inf-20),
[design assessment](#record-multilatent-warmup-research),
frozen protocol (unpublished research source: `MULTILATENT_WARMUP_PROTOCOL.md`),
machine-readable all-outcome results (unpublished research source: `multilatent_development_v1.json`).

#### What was tested

Four chains, 4,000 discarded warm-up transitions and 4,000 retained draws per
chain, two development seeds and two separate JVM executions. The matrix contains
144 attempts across eight fixtures and four methods, plus two additional block
controls on the two correlated fixtures. Repeated JVM executions are not four
independent seeds. The source map and numerical references were frozen before
execution; no thresholds, budgets or seed sets changed afterward.

Independent Gaussian/mixture answers, circular Bessel-versus-quadrature checks,
and tensor-versus-adaptive integration for the coupled Gamma/Beta model qualified
the references. Python independently recomputed 872 mean/SD summaries from full
retained traces and checked the exact run, query, trace and tuning inventories.
All 32 candidate attempts completed, including all 24 ordinary-case attempts.
The actual failed summary was passed to the orchestration guard, which correctly
refused confirmation. No held-out confirmation run was launched.

#### Frozen utility decision

The metric is normalized squared mean error, averaged over the declared queries
and executions, multiplied by mean total elapsed run time. Ratios below one
favor tuned moves over fixed-scale `.5` moves. This is **not a raw runtime speedup**.
Construction, initialization, warm-up, cleanup, diagnostics and any pilot are
counted; JVM startup, compilation, stdout serialization and a declared JIT warm-up
are excluded. Both timing repetitions and all failures remain in the evidence.

| Fixture | Tuned / fixed error-times-cost | Interpretation |
| --- | ---: | --- |
| Factorized, 2 roots | 0.736 | Passes required ratio at most .8 |
| Factorized, 4 roots | 0.855 | Fails required ratio at most .8 |
| Factorized, 8 roots | 0.736 | Passes required ratio at most .8 |
| Correlated, rho .9 | 0.178 | Passes ordinary non-regression limit 1.25 |
| Coupled positive/bounded | 0.408 | Passes ordinary non-regression limit 1.25 |
| Circular | 0.522 | Passes ordinary non-regression limit 1.25 |
| Strong correlation, rho .995 | 0.679 | Adverse case; quality still inadequate |
| Separated modes | 1.224 | Adverse case; no missing-mode remedy |

All candidate ordinary runs pass the declared mean, probability and SD error
limits. All ordinary non-regression limits pass. The factorized utility gate
requires **each** of the three factor cases to pass: two successes cannot cancel
the four-root failure. There is no subgroup promotion or post-hoc retuning.

#### Adverse results and incomplete comparisons

- Strong correlation: two of four candidate executions fail the quality checks;
  maximum reported query R-hat is about 1.745. The oracle Gaussian block passes
  all four executions. Better performance than a poor scalar baseline does not
  establish adequate inference.
- Separated modes: three of four candidate executions fail quality, with maximum
  R-hat about 1.736. Fixed and informed local scalar moves also fail three of
  four; unchanged default MH passes all four on this particular prior/target.
  This is important counterevidence to treating adapted local moves as universally
  preferable, not evidence that default MH always finds modes.
- Existing pilot-calibrated blocks refuse all eight correlated/strong attempts
  because pilot diagnostics fail. Error messages, elapsed time and actual
  likelihood counts are retained. These are incomplete posterior comparisons,
  not zero-error or zero-cost runs, nor proof that calibrated blocks are unusable.
  Pilot budgets were not increased after observing refusals.
- Circular tuning reports six coordinate/chain scale-bound warnings across its
  four executions. Quality passes within the existing bound; the bound was not
  widened and acceptance targeting is not a convergence certificate.

The complete result contains every comparator, failed quality query, refusal,
MCSE, ESS, R-hat and tuning report. Nominal MCSE-interval inclusion is descriptive
only: two independent seeds cannot validate coverage. Mean individual run times
are roughly 0.06–0.19 seconds, making precise timing generalizations inappropriate.
This is a small serial screen, not a parallel scaling or general model benchmark.

#### Implementation and verification status

The only runtime implementation change is a package-scoped warm-up controller
interface and runner entry. Existing single-scalar tuning is wrapped internally;
its exactly-one-latent check remains. The test-only coordinator selects one fixed
root, updates only its tuner using its local attempt count, and freezes before
retention. It does not duplicate MH scoring or expose the private kernel.

The final local run passed **119 tests in nine suites**, including 14 new
transition/accounting/lifecycle controls, unchanged single-scalar/owned-MH and
observation tests, stopping regressions, Gaussian block/calibration and diagnostic
tests. Five Python reference/orchestration controls passed. Earlier failed
test-fixture compilation and an evidence-recorder filename collision are retained
in the private reproducibility record; each was corrected before the protocol
freeze. No failed study result was removed or rerun for a more favorable seed.

The development build is `7.5.0-SNAPSHOT` to avoid labeling changed runtime bytes
as released `7.4.0`; this is not a release recommendation or publication. Full
remote CI, packaged-consumer acceptance and public API review have not been run
for promotion. No merge, release or public multi-latent API is authorized by the
experiment. Published `7.4.0` remains the adoption baseline.

#### Recommendation

Close this performance experiment at its firm exit gate. The strongest favorable
evidence is complete ordinary-case accuracy and a useful implementation that
reduces manual scale selection. The strongest counterevidence is the failed
predeclared utility margin, additional warm-up cost, adverse mixing failures and
small timing/seed sample. Neither side establishes universal efficiency.

A future **usability-only** proposal could ask whether explicit multi-root warm-up
is worth offering without a performance promise. That would need separate approval,
an API contract, fresh independent validation, CI and packaged-consumer checks;
it must retain the present result. Do not launch more optimization or consume the
held-out seeds automatically. Existing fixed support-aware and qualified Gaussian
block workflows remain the available production choices within their documented
limits.


## Research-reference snapshots

<a id="reference-gvm-05"></a>

### GVM-05: Higher-dimensional GVM fitting and wrapped Gaussian controls

**Source:** Circular-regression context in GVM-04; Rocci, Gattone and Di Mari, [A data driven equivariant approach to constrained Gaussian mixture modeling](https://arxiv.org/pdf/1609.03317), v4 (2016), introduction and §§3–6; Greco, Saraceno and Agostinelli, [Robust Fitting of a Wrapped Normal Model to Multivariate Circular Data and Outlier Detection](https://giovsaraceno.github.io/papers/stats-04-00028-v2.pdf) (2021), introduction and §2; Powell, [BOBYQA](https://optimization-online.org/wp-content/uploads/2010/05/2616.pdf) (2009), abstract/introduction and the [Commons Math API](https://commons.apache.org/proper/commons-math/javadocs/api-3.6.1/org/apache/commons/math3/optim/nonlinear/scalar/noderiv/BOBYQAOptimizer.html). Official [scikit-learn GMM documentation](https://scikit-learn.org/stable/modules/generated/sklearn.mixture.GaussianMixture.html) and [libDirectional source](https://github.com/libDirectional/libDirectional/tree/8a5c791743ddce5463fe192182f98c576b847ace) provide implementation comparisons, not reproduced results. Inspected 2026-10-23; exact scope and access limitations are in the guide.

**Role:** Test-only research candidate for two or three linear coordinates plus one angle: full-covariance/quadratic-phase fitting, fixed-frame phase coordinates, bounded local optimization and independent soft-winding GMM controls. Published constrained-Gaussian and circular-regression guarantees do not automatically transfer. No external source/data copied; libDirectional GPL-3.0 is reference-only. SciPy supplies the independent optimization control; scikit-learn was considered but not installed. Commons Math is an existing Apache-2.0 dependency. Shared pilot remains parked; stopping/defaults unchanged.

**Code:** Test-only [Commons Math GVM fitter](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/tools/GvmDimensionalFit.java), [independent Python references and soft-winding Gaussian fitter](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/tools/gvm_dimension_controls.py), and [frozen-study runner](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/tools/gvm_dimension_study.py). Existing [joint kernel](https://github.com/mattwilkinsphoto/figaro/blob/2ba1c55ee9b60c68fc1097b6b7bbb92fd558aa28/Figaro/src/main/scala/com/cra/figaro/library/atomic/continuous/GaussVonMisesDistribution.scala), [scalar-only fitter](https://github.com/mattwilkinsphoto/figaro/blob/2ba1c55ee9b60c68fc1097b6b7bbb92fd558aa28/Figaro/src/main/scala/com/cra/figaro/library/atomic/continuous/GaussVonMisesMixtureFit.scala) and [Gaussian fitter](https://github.com/mattwilkinsphoto/figaro/blob/2ba1c55ee9b60c68fc1097b6b7bbb92fd558aa28/Figaro/src/main/scala/com/cra/figaro/algorithm/sampling/GaussianMixtureProposal.scala) remain unchanged.

**Validation:** [Independent reference tests](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/tools/test_gvm_dimension_controls.py), [cross-language qualification/development controls](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/tools/gvm_dimension_qualification.py), [unchanged-kernel consumer check](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/tools/GvmKernelCompatibility.scala), and [independent all-model high-precision audit](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/tools/gvm_dimension_audit.py). The [768-fit frozen summary](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/tools/gvm_dimension_summary_v1.json) passes numerical checks and retained-fit score margins but fails completion gates; no promotion. Historical [Release61RepresentationStudy](https://github.com/mattwilkinsphoto/figaro/blob/2ba1c55ee9b60c68fc1097b6b7bbb92fd558aa28/Figaro/src/test/scala/com/cra/figaro/test/modernization/Release61RepresentationStudy.scala) remains a scalar baseline whose wrapped control wraps a chart-fit Gaussian at evaluation. All budget/iteration failures remain outcomes, not discarded trials.

**Guide:** [Higher-dimensional GVM fitting assessment and bounded plan](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/docs/GVM_HIGH_DIMENSIONAL_FITTING_RESEARCH.md); [three-stage experiment protocol](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/docs/GVM_HIGH_DIMENSIONAL_PROTOCOL.md); [completed results and no-promotion decision](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/docs/GVM_HIGH_DIMENSIONAL_STATUS.md).


<a id="reference-gvm-06"></a>

### GVM-06: Bounded analytic-gradient GVM optimization

**Source:** Byrd, Lu, Nocedal and Zhu, [A Limited Memory Algorithm for Bound Constrained Optimization](https://users.iems.northwestern.edu/~nocedal/PDFfiles/limited.pdf) (1995), abstract, introduction and algorithm outline; authors' [L-BFGS-B software and licensing page](https://users.iems.northwestern.edu/~nocedal/lbfgsb.html); official [SciPy L-BFGS-B options](https://docs.scipy.org/doc/scipy/reference/optimize.minimize-lbfgsb.html). Balakrishnan, Wainwright and Yu, [Statistical guarantees for the EM algorithm](https://www.stat.cmu.edu/~siva/publications/EM_full.pdf) (2017), abstract/introduction, provides convergence/initialization context, not a guarantee for this adaptation. Inspected 2026-10-24. GVM-05 supplies the unchanged model constraints and derivative-free control.

**Role:** Test-only adaptation using maintained SciPy L-BFGS-B with an independently derived weighted-resultant gradient. Inner projected-gradient qualification is separate from outer mixture-likelihood stabilization. No global optimum, theorem transfer or automatic promotion. SciPy remains an isolated research dependency, not a new Figaro runtime dependency. Stopping/defaults and the shared pilot are unchanged.

**Code:** [Bounded analytic-gradient fitter](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/tools/gvm_gradient_fit.py), [paired study runner](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/tools/gvm_gradient_study.py), [initialization bridge](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/tools/GvmGradientKeys.java). The [BOBYQA baseline](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/tools/GvmDimensionalFit.java) and all GVM-05 frozen sources remain unchanged.

**Validation:** [Independent gradient, solver and failure controls](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/tools/test_gvm_gradient_fit.py) use high-precision complex-resultant differentiation, central differences and finite-difference SLSQP. Finite-difference L-BFGS-B is an ablation, not an independent backend. The [all-model audit](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/tools/gvm_gradient_audit.py) uses the separate [high-precision density reference](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/tools/gvm_dimension_audit.py). The [512-fit summary](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/tools/gvm_gradient_summary_v1.json) passes numerical and score-noninferiority checks but fails per-cell reliability; no promotion. [Diagnostics](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/tools/gvm_gradient_diagnostics_v1.json) retain the separate work/iteration curves and final-responsibility gradients. Declared likelihood stabilization is not a whole-fit stationarity certificate.

**Guide:** [Bounded analytic-gradient study protocol](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/docs/GVM_GRADIENT_STUDY.md); [completed results and limits](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/docs/GVM_GRADIENT_STATUS.md).


<a id="reference-gvm-07"></a>

### GVM-07: Whole-fit stationarity, safeguarded acceleration and complexity

**Source:** Du and Varadhan, [SQUAREM: An R Package for Off-the-Shelf Acceleration of EM, MM and Other EM-Like Monotone Algorithms](https://doi.org/10.18637/jss.v092.i07) (2020), introduction, Algorithm 1 and controls discussion, inspected through a public full-text mirror after publisher timeouts; official [CRAN SQUAREM package](https://CRAN.R-project.org/package=SQUAREM) and [vignette](https://cran.r-project.org/web/packages/SQUAREM/vignettes/SQUAREM.pdf), implementation context only. Drton and Plummer, [A Bayesian information criterion for singular models](https://arxiv.org/pdf/1309.0911), v3 (2016), abstract/introduction, supplies the overfitted-mixture/BIC caution. Inspected 2026-10-24. GVM-05/06 retain the model and inner optimization provenance.

**Role:** Independently written, test-only squared-extrapolation adaptation with circular differences, bounded projections, strict likelihood guards and total-work accounting. All-parameter marginal-likelihood gradients provide a coordinate-dependent stationarity diagnostic, not an inference stopping rule or global-optimum guarantee. The CRAN package is GPL >= 2, reference-only: no external implementation code or dataset copied. SciPy remains research-only; no theorem transfer or promotion based solely on numerical agreement. Validation-data model selection is descriptive predictive selection, not true-component recovery.

**Code:** [Whole-fit derivatives/projections](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/tools/gvm_wholefit_math.py), [safeguarded fitter and unaccelerated control](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/tools/gvm_wholefit_fit.py), [fresh complexity-grid runner](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/tools/gvm_wholefit_study.py). Existing runtime implementations and frozen GVM-05/06 sources remain unchanged.

**Validation:** [All-block finite differences, boundary/algebra and failure controls](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/tools/test_gvm_wholefit.py); [independent density, objective-difference, inventory and result audit](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/tools/gvm_wholefit_audit.py). The [560-fit summary](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/tools/gvm_wholefit_summary_v1.json) passes numerical and score-noninferiority gates but fails per-cell reliability and the required completion gain; no promotion. [Descriptive breakdowns](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/tools/gvm_wholefit_diagnostics_v1.json) distinguish retained stationarity, restart-budget exhaustion and validation-only complexity selection. Confirmation outcomes are separate from implementation correctness.

**Guide:** [Whole-fit convergence and complexity protocol](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/docs/GVM_WHOLEFIT_STUDY.md); [completed results and limits](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/docs/GVM_WHOLEFIT_STATUS.md).


<a id="reference-gvm-08"></a>

### GVM-08: Conditional weight optimization and restart allocation

**Source:** Kim, Carbonetto, Stephens and Anitescu, [mixture-proportion SQP paper](https://arxiv.org/pdf/1806.01412), v5 (2020), abstract/introduction and sections 3.1–3.3; official [mixsqp implementation and license declaration](https://stephenslab.github.io/mixsqp/), [API](https://stephenslab.github.io/mixsqp/reference/mixsqp.html) and [SciPy SLSQP contract](https://docs.scipy.org/doc/scipy/reference/optimize.minimize-slsqp.html). Jaggi, [Revisiting Frank-Wolfe](https://proceedings.mlr.press/v28/jaggi13.pdf) (2013), section 2. Jamieson and Talwalkar, [Non-stochastic Best Arm Identification](https://proceedings.mlr.press/v51/jamieson16.pdf) (2016), introduction, section 2 and algorithm; Li et al., [Hyperband](https://jmlr.org/papers/volume18/16-558/16-558.pdf) (2018), introduction/section 3.1. Initialization alternatives: You, Li and Du, [MRIPEM](https://journals.plos.org/plosone/article?id=10.1371/journal.pone.0284114) (2023), abstract/method/overlap discussion, and official [GaussianMixture API](https://scikit-learn.org/stable/modules/generated/sklearn.mixture.GaussianMixture.html). Liu and Rubin, [ECME](https://doi.org/10.1093/biomet/81.4.633) (1994), publisher abstract only. Inspected 2026-10-24; scope/access limits are recorded in the guide.

**Role:** Test-only research adaptation: fixed-shape convex weight polishing using SciPy SLSQP, independently derived floored-simplex gap, and conservative resumable work allocation. Successive Halving/Hyperband and new initializations remain alternatives, not implemented methods or transferred guarantees. SciPy BSD remains an isolated research dependency; mixsqp's declared MIT implementation is reference-only. No external code/data copied or new runtime dependency. Only selected primary-paper sections were reviewed; ECME is abstract-only context. Existing stopping/defaults and prior no-promotion decisions remain unchanged.

**Code:** [Conditional solver](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/tools/gvm_weight_solver.py), [composite-map/restart adapter](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/tools/gvm_weight_fit.py), and [factorial runner](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/tools/gvm_weight_study.py). The unchanged [GVM-07 fitter](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/tools/gvm_wholefit_fit.py) remains the primary baseline. A conditional gap does not certify the nonconvex full fit or the best restart.

**Validation:** [Independent 60-digit weight reference](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/tools/gvm_weight_reference.py), [weight controls](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/tools/test_gvm_weight_solver.py), [scheduler/equivalence/failure controls](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/tools/test_gvm_weight_fit.py), [independent audit](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/tools/gvm_weight_audit.py), [negative audit controls](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/tools/test_gvm_weight_audit.py), [corruption controls](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/tools/test_gvm_weight_validation.py) and [reporting controls](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/tools/test_gvm_weight_reporting.py). The [40-case relevance screen](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/tools/gvm_weight_qualification_v1.json), 40 development-fit audits and [1,040-fit confirmation numerical/score checks](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/tools/gvm_weight_summary_v1.json) pass. Reliability and completion-improvement gates fail: no promotion. [Descriptive breakdowns](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/tools/gvm_weight_diagnostics_v1.json) retain the distinction between solution and search completion. Earlier [whole-fit diagnostics](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/tools/gvm_wholefit_diagnostics_v1.json) and [frozen gates](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/docs/GVM_WHOLEFIT_STUDY.md) remain unchanged.

**Guide:** [Mixture-weight and restart-budget assessment](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/docs/GVM_WEIGHT_RESTART_RESEARCH.md); [frozen execution protocol](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/docs/GVM_WEIGHT_PROTOCOL.md); [completed result and limitations](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/docs/GVM_WEIGHT_STATUS.md); [preceding completed study](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/docs/GVM_WHOLEFIT_STATUS.md).


<a id="reference-gvm-09"></a>

### GVM-09: Joint optimization feasibility and confirmation

**Source:** Sembach, Burgard and Schulz, [A Riemannian Newton Trust-Region Method for Fitting Gaussian Mixture Models](https://arxiv.org/html/2104.14957v2) (2022), sections 3.2 and 4; Hosseini and Sra, [An alternative to EM for Gaussian mixture models](https://portal.fis.tum.de/de/publications/an-alternative-to-em-for-gaussian-mixture-models-batch-and-stocha/) (2020), publication abstract only; official [SciPy SLSQP documentation](https://docs.scipy.org/doc/scipy/reference/optimize.minimize-slsqp.html), inspected 2026-10-24. GVM-05 through GVM-08 provide the unchanged model, derivatives and baseline.

**Role:** Test-only constrained quasi-Newton adaptation, not a port of a Riemannian Newton method. Logged Cholesky coordinates preserve positive definiteness; explicit spectral constraints preserve the original eigenvalue bounds. Published penalized-GMM convergence results and timings do not transfer. SciPy remains an isolated BSD research dependency; no external code/data copied. Stopping, defaults and prior no-promotion results are unchanged.

**Code:** [Joint adapter](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/tools/gvm_joint_fit.py), [bounded paired runner](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/tools/gvm_joint_study.py), [development geometry diagnostics](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/tools/gvm_joint_geometry.py), [fresh confirmation runner](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/tools/gvm_joint_confirm_study.py) and [seed-paired reporting](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/tools/gvm_joint_confirm_stats.py). No public higher-dimensional fitter is added.

**Validation:** [Coordinate/constraint/gradient and failure controls](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/tools/test_gvm_joint.py), [independent density and physical-gradient audit](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/tools/gvm_joint_audit.py). Nine controls and the [40-fit independent audit](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/tools/gvm_joint_summary_v1.json) pass: 18/20 candidate completions versus 10/20 baseline, no lost baseline successes and all descriptive score margins pass. The [20-model geometry screen](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/tools/gvm_joint_geometry_v1.json) is descriptive. Two curved cases remain unqualified; total measured candidate time exceeds baseline despite fewer recorded data passes. Development evidence supports a fresh-confirmation plan, not promotion.

Confirmation uses the official [Google Benchmark guide](https://google.github.io/benchmark/user_guide.html) for warmup/interleaving/repetition practice and [Python clock documentation](https://docs.python.org/3.14/library/time.html) for elapsed/process-CPU semantics, inspected 2026-10-24. These are measurement references, not imported code or speedup guarantees. The [ten frozen confirmation controls](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/tools/test_gvm_joint_confirm.py) and [independent audit](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/tools/gvm_joint_confirm_audit.py) pass. The [960-fit result](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/tools/gvm_joint_confirm_summary_v1.json) retains all failed costs and audits 320 physical gradients, 800 high-precision densities, exact repeat identity, paired reporting and critical values. Eight seed pairs, not timing repetitions, determine uncertainty. Completion improves to 149/160 versus 71/160 with no lost baseline success and all score margins pass; three K=3 cells fail reliability and total elapsed cost rises about 12%. No promotion.

**Guide:** [Bounded joint optimization protocol](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/docs/GVM_JOINT_PROTOCOL.md); [completed feasibility result and limitations](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/docs/GVM_JOINT_STATUS.md); [fresh confirmation protocol](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/docs/GVM_JOINT_CONFIRMATION_PROTOCOL.md); [confirmation results and remaining limits](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/docs/GVM_JOINT_CONFIRMATION_STATUS.md).


<a id="reference-gvm-10"></a>

### GVM-10: Predictive complexity selection and incomplete comparisons

**Source:** Cawley and Talbot, [On Over-fitting in Model Selection and Subsequent Selection Bias in Performance Evaluation](https://www.jmlr.org/papers/volume11/cawley10a/cawley10a.pdf) (2010), introduction and section 5 evaluation discussion; Cai, Campbell and Broderick, [Finite mixture models do not reliably learn the number of components](https://proceedings.mlr.press/v139/cai21a/cai21a.pdf) (2021), introduction, Theorem 2.1 and section 3 assumptions; Scrucca, Fop, Murphy and Raftery, [mclust 5](https://journal.r-project.org/articles/RJ-2016-021/) (2016), section 4. Inspected 2026-10-24, targeted sections rather than full-proof audits. GVM-07 retains the Drton-Plummer source; this assessment additionally inspects sections 3.2, 4.1 and learning-coefficient discussion. Official [mclust package](https://mclust-org.github.io/mclust/) and [scikit-learn GaussianMixture API](https://scikit-learn.org/stable/modules/generated/sklearn.mixture.GaussianMixture.html) are implementation-context reviews, not executed controls or source audits.

**Role:** Literature-led assessment and independently written test-only predictive selector. Distinguishes density quality, complete qualified-candidate comparisons and true-component recovery. Bayesian misspecification results do not transfer automatically to bounded GVM selection. Ordinary BIC is a diagnostic candidate, not an optimization or evidence certificate; no GVM-specific sBIC theory is established. mclust is GPL >= 2, reference-only; no external code/data/dependency is incorporated. The approved experiment uses compact stored densities as its benefit, not a speed claim. Official [SciPy normal CDF documentation](https://docs.scipy.org/doc/scipy/reference/generated/scipy.special.ndtr.html), inspected 2026-10-25, supplies the regional marginal evaluation convention; high-precision erf provides a separate numerical check.

**Code:** Test-only qualified predictive selector and marginal-region evaluator (unpublished research source: `gvm_predictive_selection.py`), bounded stage runner (unpublished research source: `gvm_selection_study.py`), unchanged [joint research fitter](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/tools/gvm_joint_fit.py) and [synthetic data generator](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/tools/gvm_wholefit_study.py). No production selector, optimizer, stopping or default change.

**Validation:** Prior [fresh confirmation results](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/tools/gvm_joint_confirm_summary_v1.json) and [whole-fit descriptive record](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/tools/gvm_wholefit_diagnostics_v1.json) remain unchanged. Twelve selection/failure controls (unpublished research source: `test_gvm_selection.py`) and nine unchanged optimizer controls pass before the new freeze. The independent auditor (unpublished research source: `gvm_selection_audit.py`) reconstructs selection and gates without importing the selector/runner, checks all 60 retained physical gradients and 160 high-precision density points, and audits marginal probabilities using erf. The development result (unpublished research source: `gvm_selection_development_v1.json`) passes numerical, availability, score and regional-probability gates, but fails compactness: 3.98% storage reduction versus the frozen 20% requirement. All 20 models qualify as predictive outputs; six comparisons are explicitly incomplete. Descriptive results (unpublished research source: `gvm_selection_description_v1.json`) preserve every comparison. Firm exit: no confirmation, retuning or promotion; failed alternatives are not evidence of redundancy.

**Guide:** [Complexity-selection relevance assessment and conditional bounded plan](#record-gvm-complexity-selection-research); frozen predictive-selection protocol (unpublished research source: `GVM_SELECTION_PROTOCOL.md`); [execution status and exit decision](#record-gvm-selection-status).


<a id="reference-inf-18"></a>

### INF-18: Bounded multi-component filtering guide assessment

**Source:** Cappé et al., [Adaptive Importance Sampling in General Mixture Classes](https://arxiv.org/html/0710.4242) (2008), sections 2 and 3.1–3.2, reusing INF-02; Zhang et al., [Pathfinder v4](https://arxiv.org/html/2108.03782v4) (2022), sections II.2/II.4/II.7 and III.3; Tawn et al., [Annealed leap-point sampler](https://doi.org/10.1007/s11222-026-10988-5) (17 October 2026), introduction and section 3 overview. Li et al., [SeMIS](https://www.sciencedirect.com/science/article/pii/S088832702501489X) (2026), publisher abstract/introduction screening only; direct full-text retrieval returned 403. Inspected 2026-10-23. Official pypmc, scikit-learn and Stan documentation and pinned pypmc responsibility/preparation code were inspected for reuse; exact scope, licenses and access limitations are in the guide. INF-01/10/13/16/17 retain defensive, correction, log-selection and prior nonlinear-study provenance.

**Role:** Completed frozen test-only experiment; **no promotion**. Scalar local-affine surrogate mixture and separately frozen shared-pilot comparator, not implementations of M-PMC, Pathfinder, ALPS or SeMIS. The local-affine mixture identities and shared-guide integration are Figaro research specializations. Branch correctness passed, utility failed; shared pilot failed all-runs reliability. No new runtime, dependency, optimizer, training system or mode-discovery guarantee. Released stopping and defaults unchanged.

**Code:** Test-only [adapter](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/Figaro/src/test/scala/com/cra/figaro/test/modernization/MulticomponentGuidePrototype.scala) and [study](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/Figaro/src/test/scala/com/cra/figaro/test/modernization/MulticomponentGuideStudy.scala), reusing unchanged [GaussianMixtureProposal.fitWeighted](https://github.com/mattwilkinsphoto/figaro/blob/2ba1c55ee9b60c68fc1097b6b7bbb92fd558aa28/Figaro/src/main/scala/com/cra/figaro/algorithm/sampling/GaussianMixtureProposal.scala), [WeightedParticleProposal](https://github.com/mattwilkinsphoto/figaro/blob/2ba1c55ee9b60c68fc1097b6b7bbb92fd558aa28/Figaro/src/main/scala/com/cra/figaro/algorithm/filtering/WeightedParticleProposal.scala), [LogParticleWeights](https://github.com/mattwilkinsphoto/figaro/blob/2ba1c55ee9b60c68fc1097b6b7bbb92fd558aa28/Figaro/src/main/scala/com/cra/figaro/algorithm/filtering/LogParticleWeights.scala) and [FixedBudgetParticleFilter](https://github.com/mattwilkinsphoto/figaro/blob/2ba1c55ee9b60c68fc1097b6b7bbb92fd558aa28/Figaro/src/main/scala/com/cra/figaro/algorithm/filtering/FixedBudgetParticleFilter.scala). Existing ordinary-CDF mixture sampling is not changed; the test adapter uses log-tree component selection.

**Validation:** [Adapter controls](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/Figaro/src/test/scala/com/cra/figaro/test/modernization/MulticomponentGuideTest.scala), independent references below, [all-outcome analysis](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/tools/multicomponent_analysis.py) and [analysis self-tests](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/tools/test_multicomponent_analysis.py). 68 Scala controls across seven suites passed, followed by a passing final adapter rerun; 12 reference/analysis self-tests pass. All 140,032 held-out rows per JVM replay across three JVMs; 256 independent seeds. All 4,000 screens on fully completed groups pass, but 1,788 pilot refusals remain failures, not passed comparisons. Branch utility fails both primary cost-matched gates and the omitted-branch probability-MSE gate. The [machine-readable summary](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/tools/multicomponent_summary_v1.json) and status guide retain positive and negative outcomes. The first test compile used an incorrect selector name; corrected to the existing `LogTreeMultinomial`, with failed source/log retained. The unchanged [continuous benchmark](https://github.com/mattwilkinsphoto/figaro/blob/2ba1c55ee9b60c68fc1097b6b7bbb92fd558aa28/Figaro/src/test/scala/com/cra/figaro/test/modernization/ContinuousFilterBenchmark.scala) remains a separate regression gate.

Stage 1: the approved [frozen protocol](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/docs/MULTICOMPONENT_GUIDE_PROTOCOL.md) has
independent [true-model grid references](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/tools/multicomponent_reference.py) and
[reference self-tests](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/tools/test_multicomponent_reference.py). All 1,023
reference checks and five self-tests passed before candidate implementation.
This does not establish candidate correctness, utility or promotion eligibility.
Stage 2 adapter/regression controls passed. Development calibration is frozen in
[budget metadata](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/tools/multicomponent_budgets_v1.json) and
[execution input](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/tools/multicomponent_budgets_v1.tsv). Pilot fitting refusals
remain explicit; held-out results and no-promotion decision are in the status guide.
The unchanged continuous benchmark passed 450 checks; 39 additional
stopping/weighted-pilot regressions also passed. No runtime source changed.

**Guide:** [MULTICOMPONENT GUIDE RESEARCH](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/docs/MULTICOMPONENT_GUIDE_RESEARCH.md); [MULTICOMPONENT GUIDE PROTOCOL](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/docs/MULTICOMPONENT_GUIDE_PROTOCOL.md); [MULTICOMPONENT GUIDE STATUS](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/docs/MULTICOMPONENT_GUIDE_STATUS.md).


<a id="reference-inf-19"></a>

### INF-19: Expensive likelihood branch-guide relevance

**Source:** Kersting et al., [Differentiable Likelihoods for Fast Inversion of Likelihood-Free Dynamical Systems](https://proceedings.mlr.press/v119/kersting20a/kersting20a.pdf) (2020), introduction and section 2; Galioto and Gorodetsky, [Bayesian system ID](https://ngalioto.github.io/files/galioto2020bayesian.pdf) (2020), section 6.4/equation 51; Branchini and Elvira, [optimized APF](https://proceedings.mlr.press/v161/branchini21a/branchini21a.pdf) (2021), section 3.4. Stuart and Teckentrup, [Posterior Consistency for Gaussian Process Approximations of Bayesian Posterior Distributions](https://arxiv.org/html/1603.02004v2), preprint v2, introduction/section 2: posterior-error motivation, not a theorem for this spline. Relevant sections inspected 2026-10-23, not full-proof review or reproduction. Alternate/publisher PDF access for the last paper failed; HTML preprint was inspected. Official Commons Math DP853/GBS/natural spline and SciPy solve_ivp/LSODA/CubicSpline documentation are implementation references linked in the guides.

**Role:** Completed model-only screen and independent posterior/precomputation qualification; no promoted filtering method. Independently specified damped reset-response oscillator with uncertain scalar launch velocity; not the papers' experiments or probabilistic ODE solver. Existing Commons Math and isolated BSD-licensed SciPy implementations reused; no external implementation source or dataset copied into Figaro. Shared pilot parked; stopping/defaults unchanged. Frozen workload fails posterior branch relevance, so no guide-utility trial follows; selected lookup tables pass numerical checks but are not a shipping API or uniform accuracy guarantee.

**Code:** [Forward-model screen](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/tools/ExpensiveLikelihoodRelevance.java); [precomputation comparison](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/tools/BranchPrecomputation.java); [independent LSODA posterior and spline analysis](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/tools/branch_qualification.py). The branch-guide adapter remains unchanged and was not invoked; INF-18 retains its no-promotion result.

**Validation:** [Strict analysis](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/tools/expensive_likelihood_relevance.py), [four analysis controls](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/tools/test_expensive_likelihood_relevance.py), [summary](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/tools/expensive_likelihood_summary_v1.json). Initial endpoint calibration failed 15 checks; its source/log are preserved. One recorded revision passes 7,704 deterministic checks in each of three fresh JVMs without relaxing acceptance or changing inputs. Same-library ODE agreement is not an independent posterior reference. Timing is descriptive; no filter accuracy or speedup claim.

Follow-up [seven analytic/failure controls](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/tools/test_branch_qualification.py) and
[qualification summary](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/tools/branch_qualification_summary_v1.json): 1,926 solver
comparisons, analytic Kalman control, resolution/domain refinement and a path-exit
screen passed. Three JVMs replay identical table/query results. All eight candidates
retained; two coarse T=6 tables fail pointwise log-likelihood limits. Selected tables
pass held-out and posterior checks. Central branch mass is 98.2–99.8%, with no step
passing the frozen relevance gate. A recorder filename collision is preserved
and repaired by a separate unchanged replay, not by relaxing scientific checks.

**Guide:** [EXPENSIVE LIKELIHOOD RESEARCH](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/docs/EXPENSIVE_LIKELIHOOD_RESEARCH.md); [EXPENSIVE LIKELIHOOD PROTOCOL](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/docs/EXPENSIVE_LIKELIHOOD_PROTOCOL.md); [EXPENSIVE LIKELIHOOD NUMERICAL REVISION](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/docs/EXPENSIVE_LIKELIHOOD_NUMERICAL_REVISION.md); [BRANCH QUALIFICATION PROTOCOL](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/docs/BRANCH_QUALIFICATION_PROTOCOL.md); [BRANCH QUALIFICATION STATUS](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/docs/BRANCH_QUALIFICATION_STATUS.md).


<a id="reference-inf-20"></a>

### INF-20: Multi-latent warm-up assessment

**Source:** Roberts and Rosenthal, [Examples of Adaptive MCMC](https://www.probability.ca/jeff/ftpdir/adaptex.pdf), revised January 2008 manuscript, section 3 and preceding curved example; Latuszynski, Roberts and Rosenthal, [Adaptive Gibbs samplers and related MCMC methods](https://arxiv.org/pdf/1101.5838), 2013 preprint revision, sections 2–3 (not the later proofs). Reuses Garthwaite et al. section 2 under INF-06. Maintained comparisons: [NIMBLE MCMC manual](https://r-nimble.org/manual/cha-mcmc.html), sections 7.2.1.5 and 7.2.2; [Stan 2.40 Pathfinder](https://mc-stan.org/docs/2_40/reference-manual/pathfinder.html). Zhang, Carpenter, Gelman and Vehtari, [Pathfinder](https://www.jmlr.org/papers/volume23/21-0889/21-0889.pdf) (2022), abstract/introduction and reported-cost discussion inspected, not full-paper reproduction. Passages inspected 2026-10-25.

Numerical validation uses the official SciPy [QUADPACK interface](https://docs.scipy.org/doc/scipy/reference/generated/scipy.integrate.quad.html) and [Gauss-Legendre nodes](https://docs.scipy.org/doc/scipy/reference/generated/scipy.special.roots_legendre.html), inspected 2026-10-25. Independent analytic/tensor/adaptive references qualify the frozen fixtures; these are numerical controls, not the source of the MH algorithm. Existing isolated scientific dependencies are reused only by research tools.

**Role:** Test-only bounded coordinator using an explicitly approved internal runtime interface; not a shipped multi-latent tuner. Fixed coordinate-selection weights, separate scalar adaptation during discarded warm-up, frozen production. Reuses Figaro's INF-06 update rather than the sources' batch or optimal-steplength algorithms. Strong correlations and missing modes remain counterexamples to universal efficiency claims; no source theorem or performance number is transferred. No external code/data or new runtime dependency incorporated. Development ends in no promotion at its firm utility gate.

**Code:** [SupportAwareProposal](https://github.com/mattwilkinsphoto/figaro/blob/2ba1c55ee9b60c68fc1097b6b7bbb92fd558aa28/Figaro/src/main/scala/com/cra/figaro/algorithm/sampling/SupportAwareProposal.scala), [owned MH runner and internal controller interface](https://github.com/mattwilkinsphoto/figaro/blob/2ba1c55ee9b60c68fc1097b6b7bbb92fd558aa28/Figaro/src/main/scala/com/cra/figaro/algorithm/sampling/parallel/MultiChainMetropolisHastings.scala), [proposal dispatch](https://github.com/mattwilkinsphoto/figaro/blob/2ba1c55ee9b60c68fc1097b6b7bbb92fd558aa28/Figaro/src/main/scala/com/cra/figaro/algorithm/sampling/MetropolisHastings.scala), [Gaussian block calibration](https://github.com/mattwilkinsphoto/figaro/blob/2ba1c55ee9b60c68fc1097b6b7bbb92fd558aa28/Figaro/src/main/scala/com/cra/figaro/algorithm/sampling/GaussianBlockCalibration.scala), test-only coordinator (unpublished research source: `MultiLatentWarmupPrototype.scala`). Public `runTuned` still requires exactly one latent scalar; stopping/defaults are unchanged.

**Validation:** 14 transition/lifecycle controls (unpublished research source: `MultiLatentWarmupTest.scala`) pass within a 119-test local regression run. Study driver (unpublished research source: `MultiLatentWarmupStudy.scala`), frozen source map (unpublished research source: `multilatent_protocol_v1.json`), independent references and trace analysis (unpublished research source: `multilatent_study.py`), five oracle/orchestration tests (unpublished research source: `test_multilatent_study.py`), all-outcome result (unpublished research source: `multilatent_development_v1.json`) and evidence packaging (unpublished research source: `multilatent_evidence.py`) retain 144 attempts, eight calibration refusals and 872 independently recomputed trace summaries. All ordinary candidate accuracy gates pass, but four-root cost-error ratio .855 exceeds .8: no promotion, retuning or fresh confirmation. Strong-correlation/mode failures and scale-bound warnings remain recorded. This is not full CI or packaged-consumer promotion validation.

Usability follow-up: Roberts–Rosenthal section 3 and the preceding curved example,
NIMBLE sections 7.2.2.3–7.2.2.7, and the [Stan posterior-analysis manual](https://mc-stan.org/docs/reference-manual/analysis.html)
convergence/global-convergence sections were revisited 2026-10-25. They motivate
explicit named configuration and visible diagnostics, not a speedup guarantee.
The separately approved API plan is now implemented locally in the
[additive runner entry](https://github.com/mattwilkinsphoto/figaro/blob/2ba1c55ee9b60c68fc1097b6b7bbb92fd558aa28/Figaro/src/main/scala/com/cra/figaro/algorithm/sampling/parallel/MultiChainMetropolisHastings.scala)
and internal coordinator (unpublished research source: `CoordinateWarmup.scala`).
API controls (unpublished research source: `CoordinateWarmupApiTest.scala`)
and public-only examples (unpublished research source: `CoordinateWarmupExamples.scala`)
pass locally within a 131-test regression audit. The
fresh driver (unpublished research source: `CoordinateWarmupStudy.scala`),
separate source map (unpublished research source: `coordinate_warmup_protocol_v1.json`),
independent analysis (unpublished research source: `coordinate_warmup_study.py`),
guard tests (unpublished research source: `test_coordinate_warmup_study.py`),
all-outcome result (unpublished research source: `coordinate_warmup_qualification_v1.json`) and
post-study evidence audit (unpublished research source: `coordinate_warmup_evidence.py`) retain 704
attempts, 30 pilot refusals, 4,330 independently recomputed scientific summaries
and 1,664 checked automatic traces. All candidate executions and overhead gates
pass, but two fresh eight-root seeds fail ordinary accuracy in both JVMs. Their
failing raw traces match the original prototype. No promotion, increased budgets,
fresh retry or relaxed gates. Shared runtime source changed additively; original
source-hashed bytes remain in the verified original archive, not a rewritten map.
The closed performance protocol is unchanged. No human usability study is claimed.
Existing diagnostic provenance remains DIA-01.

Budget-adequacy follow-up: Vats, Flegal and Jones,
[Multivariate Output Analysis for MCMC](https://arxiv.org/html/1512.07713v4),
v4 (2017), sections 1–4, supplies CLT/precision and nonoverlapping batch-means
background; proofs and examples were not reproduced. He, De Sa, Mitliagkas and Ré,
[Scan Order in Gibbs Sampling](https://arxiv.org/html/1606.03432v1), v1 (2016),
introduction and section 3 examples, supplies counterevidence to assuming systematic
scan is universally better, not a performance theorem for Figaro's MH kernel.
The Stan analysis manual above and maintained
[posterior mean-MCSE interface](https://mc-stan.org/posterior/reference/mcse_mean.html)
were inspected 2026-10-25. The PMC copy was challenge-blocked; accessible arXiv v1
was used after v2 HTML returned 404. No external implementation was imported.
Post-hoc assessment (unpublished research source: `coordinate_budget_assessment.py`),
five arithmetic controls (unpublished research source: `test_coordinate_budget_assessment.py`),
all-row result (unpublished research source: `coordinate_budget_assessment_v1.json`) and
evidence packaging (unpublished research source: `coordinate_budget_evidence.py`) examine 1,120 coordinate
summaries, 4,480 retained traces and 896 tuning reports. Median mean ESS drops
roughly inversely with dimension for tuned and informed fixed proposals.
Initialization is unresolved; selected half-trace improvements do not repair the
failed test. The lazy-product-kernel autocorrelation relation and scalar Normal
precision illustration are independent derivations with explicit assumptions.
No new sampling, stopping/default change, runtime edit or promotion occurs.

The subsequent approved diagnostic experiment is separate from that retained-data
assessment. The test-only driver (unpublished research source: `CoordinateBudgetStudy.scala`)
and independent target/transition controls (unpublished research source: `CoordinateBudgetTest.scala`)
compare original and oracle-posterior initialization constructions under fixed
budgets. The frozen protocol/source map (unpublished research source: `coordinate_budget_protocol_v1.json`),
bounded runner (unpublished research source: `coordinate_budget_runner.py`),
independent trace/reference analysis (unpublished research source: `coordinate_budget_study.py`),
analysis controls (unpublished research source: `test_coordinate_budget_study.py`),
separate arithmetic audit (unpublished research source: `coordinate_budget_validation.py`) and
evidence packaging (unpublished research source: `coordinate_budget_experiment_evidence.py`) preserve
the source/evidence chain. Six Python controls and 135 focused Scala regressions
pass before fresh sampling. The [SciPy bootstrap manual](https://docs.scipy.org/doc/scipy/reference/generated/scipy.stats.bootstrap.html),
inspected 2026-10-25, supplies implementation guidance for explicitly selected
percentile intervals on whole seed-index vectors; these are not simultaneous
coverage guarantees or a new stopping rule. Runtime sources and defaults are unchanged.

The completed all-outcome result (unpublished research source: `coordinate_budget_result_v1.json`)
retains 1,152 attempts, 69,632,000 transitions and 16,000 checked trace summaries.
The separate audit reproduces 12,800 scientific accuracy flags and 16 whole-seed
bootstrap contrasts. All five diagnostic gates pass, including all 96 maximum-budget
original candidate accuracy checks. Eight-root retained-work ESS ratios are 3.929
and 4.029, with RMSE ratios .521 and .542; warm-up and initialization relevance
screens are not established. All 80 lower-budget accuracy failures are preserved.
This permits only separate broader requalification review, not promotion, a
speedup claim, a universal budget rule or repair of the earlier failed protocols.

Separately approved requalification retains all eight original fixtures with
dimension-scaled budgets. Roberts–Rosenthal section 3 and the Stan convergence
discussion above were revisited 2026-10-26; adaptation success does not certify
global convergence. The test-only driver (unpublished research source: `CoordinateRequalificationStudy.scala`),
inventory controls (unpublished research source: `CoordinateRequalificationTest.scala`),
strict trace analysis (unpublished research source: `coordinate_requalification.py`),
failure controls (unpublished research source: `test_coordinate_requalification.py`),
bounded capture (unpublished research source: `coordinate_requalification_runner.py`) and
separate decision audit (unpublished research source: `coordinate_requalification_audit.py`) retain
calibration-pilot traces, refused comparisons and every adverse outcome. Before
freezing, all independent references requalify and 137 Scala/seven Python controls
pass. The completed all-outcome result (unpublished research source: `coordinate_requalification_result_v1.json`)
retains 2,816 attempts, including 126 calibration refusals; independent analysis
checks 19,594 summaries and 6,656 automatic traces. The separate audit recomputes
17,930 scientific query flags and every gate. Ordinary accuracy fails in 24/384
candidate executions on correlated/Gamma-Beta targets, despite passing all
factorized/circular, execution, overhead and adverse-warning gates: no promotion.
The post-hoc selected-failure trace audit (unpublished research source: `coordinate_requalification_trace_audit.py`)
finds 420/440 byte-identical prototype trace pairs, retaining 20 differences;
it changes no decision and makes no universal path-identity claim.
Evidence packaging (unpublished research source: `coordinate_requalification_evidence.py`) preserves
the protocol, actual outputs, complete results and historical checks. No runtime
change, production promotion or revision of the earlier failures occurs.

**Guide:** [Multi latent proposal warm up assessment](#record-multilatent-warmup-research), frozen protocol (unpublished research source: `MULTILATENT_WARMUP_PROTOCOL.md`), [completed status and limitations](#record-multilatent-warmup-status), separate usability API proposal (unpublished research source: `MULTILATENT_WARMUP_API_PLAN.md`), candidate user guide (unpublished research source: `COORDINATE_WARMUP.md`), usability qualification protocol (unpublished research source: `COORDINATE_WARMUP_PROTOCOL.md`), [usability qualification outcome](#record-coordinate-warmup-status), [budget adequacy assessment and bounded plan](#record-coordinate-budget-research), fresh budget protocol (unpublished research source: `COORDINATE_BUDGET_PROTOCOL.md`), [budget experiment outcome](#record-coordinate-budget-status), bounded requalification protocol (unpublished research source: `COORDINATE_REQUALIFICATION_PROTOCOL.md`), [requalification outcome](#record-coordinate-requalification-status).


<a id="reference-inf-21"></a>

### INF-21: Dependence-aware proposal and pilot assessment

**Source:** Reuses Roberts and Rosenthal [Examples of Adaptive MCMC](https://www.probability.ca/jeff/ftpdir/adaptex.pdf) from INF-20, now sections 1–2; Vihola [RAM manuscript](https://users.jyu.fi/~mvihola/vihola_-_ram.pdf), introduction and section 2; Turek et al. [automated blocking preprint v1](https://arxiv.org/pdf/1503.05621), sections 2–3. Maintained comparisons: [Stan 2.40 warm-up/metric documentation](https://mc-stan.org/docs/reference-manual/mcmc.html), [NIMBLE manual](https://r-nimble.org/manual/cha-mcmc.html) and [autoBlock maintenance warning](https://raw.githubusercontent.com/nimble-dev/nimble/devel/packages/nimble/R/MCMC_autoBlock.R), [AdaptiveMCMC.jl adaptation interfaces](https://mvihola.github.io/docs/AdaptiveMCMC.jl/adapt/). Gradient alternative: [Barker v2](https://arxiv.org/pdf/1908.11812), introduction and sections 3.2–3.3; [October 2026 extension](https://arxiv.org/abs/2610.01448v1), abstract only. [Laitinen and Vihola](https://arxiv.org/pdf/2408.14903), v1 introduction and 2026 publication metadata only. Inspected 2026-10-26; guide records access, version and reuse limits. Transform and diagnostic sources remain INF-05 and DIA-01.

**Role:** Completed bounded test-only graph-pilot development with an approved internal adapter; no promotion. One windowed empirical-covariance candidate with bounded scale learning and a defensive move, independent fixed-kernel qualification and fresh production. Ordinary reliability/cost gates failed; the frozen independent audit also found a tied-quantile tail-ESS discrepancy. Window schedule and acceptance gates are Figaro research design, not source guarantees. RAM, automatic block search and gradient methods are deferred alternatives. No external source code/data or library dependency incorporated; no stopping/default or strict fitter change. Independent controls use isolated [SciPy 1.18.1](https://pypi.org/project/scipy/1.18.1/) with its documented [Gamma](https://docs.scipy.org/doc/scipy/reference/generated/scipy.stats.gamma.html) and [Beta](https://docs.scipy.org/doc/scipy/reference/generated/scipy.stats.beta.html) parameterizations (inspected 2026-10-26).

**Code:** Existing integration boundaries: [GaussianBlockProposal](https://github.com/mattwilkinsphoto/figaro/blob/2ba1c55ee9b60c68fc1097b6b7bbb92fd558aa28/Figaro/src/main/scala/com/cra/figaro/algorithm/sampling/GaussianBlockProposal.scala), [GaussianBlockCalibration](https://github.com/mattwilkinsphoto/figaro/blob/2ba1c55ee9b60c68fc1097b6b7bbb92fd558aa28/Figaro/src/main/scala/com/cra/figaro/algorithm/sampling/GaussianBlockCalibration.scala), [SupportAwareProposal](https://github.com/mattwilkinsphoto/figaro/blob/2ba1c55ee9b60c68fc1097b6b7bbb92fd558aa28/Figaro/src/main/scala/com/cra/figaro/algorithm/sampling/SupportAwareProposal.scala). The separately approved JointTransformedBlock (unpublished research source: `JointTransformedBlock.scala`) adds an internal opt-in identity/log/logit joint kernel through [MH dispatch](https://github.com/mattwilkinsphoto/figaro/blob/2ba1c55ee9b60c68fc1097b6b7bbb92fd558aa28/Figaro/src/main/scala/com/cra/figaro/algorithm/sampling/MetropolisHastings.scala) and [scheme representation](https://github.com/mattwilkinsphoto/figaro/blob/2ba1c55ee9b60c68fc1097b6b7bbb92fd558aa28/Figaro/src/main/scala/com/cra/figaro/algorithm/sampling/ProposalScheme.scala). It reuses transforms and Cholesky validation, with one joint decision and transactional graph restoration. It is not a promoted public API or a complete pilot workflow. Stan's transform and finite-precision sections were rechecked on 2026-10-26; no external implementation was copied.

**Validation:** Retained requalification result (unpublished research source: `coordinate_requalification_result_v1.json`) and independent audit (unpublished research source: `coordinate_requalification_audit.py`) motivate the assessment. Initial test-only kernel (unpublished research source: `DependencePilotKernel.scala`), Scala controls (unpublished research source: `DependencePilotTest.scala`), independent arithmetic audit (unpublished research source: `dependence_pilot_controls.py`) and audit failure controls (unpublished research source: `test_dependence_pilot_controls.py`) passed locally. The subsequent test-only graph orchestration (unpublished research source: `DependenceGraphPilot.scala`), lifecycle controls (unpublished research source: `DependenceGraphPilotTest.scala`), driver (unpublished research source: `DependenceGraphStudy.scala`), primary analysis (unpublished research source: `dependence_graph_study.py`) and separate audit (unpublished research source: `dependence_graph_audit.py`) ran the frozen 272-attempt development inventory. Pre-freeze checks: 80 Scala tests, 12 independent diagnostic grids, seven new audit controls, five Jacobian controls and independent reference integrals. Development failed ordinary reliability/cost gates; the frozen audit failed numerical diagnostic equivalence. Confirmation was not run. The separate forensic replay retains that failure, not a waived validation pass.

**Guide:** [Dependence aware proposals and reliable pilot calibration](#record-dependence-aware-proposal-research); [experiment and architecture status](#record-dependence-pilot-status); frozen graph-pilot protocol (unpublished research source: `DEPENDENCE_GRAPH_PROTOCOL.md`).

Approved adapter checkpoint: JointTransformedBlockTest (unpublished research source: `JointTransformedBlockTest.scala`)
passes 19 controls, including 32 independent whole-step decisions, owned-runner
replay and failure cleanup. The separate SciPy graph audit (unpublished research source: `joint_transformed_block_audit.py`)
checks 18 actual graph ratios; audit negative controls (unpublished research source: `test_joint_transformed_block_audit.py`)
check missing/duplicate inventory and incorrect Jacobian/model scores. Also rerun:
54 existing regression tests. Those initial local controls preceded the development
and audit findings above; they did not independently validate the full learned sampler. Stopping,
defaults, strict calibration and promotion/exit requirements remain unchanged.
