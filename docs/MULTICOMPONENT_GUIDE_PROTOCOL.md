# Multi-component guide protocol v1

[Research provenance](RESEARCH_REFERENCES.md#inf-18).
[Assessment and equations](MULTICOMPONENT_GUIDE_RESEARCH.md).

Frozen before candidate execution. Test-only scalar Gaussian transitions and
Gaussian observation noise; no released stopping/default/runtime changes.
Stage 1 accepts independent references, Stage 2 implements and tests, Stage 3
calibrates cost using development only then confirms on fresh seeds. No automatic
promotion, merge or publication.

## Fixtures and observables

Unless overridden, initial N(0,1.21), transition N(.7*x,.64), observations
[.4,1.2,-.3,.7,1.5,.2], noise standard deviation .5. Variances and standard
deviations are intentionally distinguished. All data are synthetic controls.

| Name | Observation h(x), overrides and anchor inventory |
| --- | --- |
| affine | h=x; anchors [-1,1] |
| weak | h=x+.25*x^3; noise sd 4; anchors [-1,0,1] |
| mono | same h, normal noise; anchors [-1,0,1] |
| tail | same h; single y=6; anchors [1,2,3] |
| symmetric | h=.25*x^2; y=[1,1.2,.8,1.4,1,.7]; anchors +/-2*sqrt(y) |
| asymmetric | square h and y; initial mean .8; noise sd .2; same anchors; primary |
| three | h=x^3-3*x; y=[0,.2,-.2,0,.1,-.1]; noise sd .35; anchors [-sqrt(3),0,sqrt(3)]; primary |
| merging | square h; y=[.04,.01,0,-.02]; anchors +/-2*sqrt(y) for positive y, otherwise [0] |
| flat | h=0; y=[0,.1,-.1,0]; anchor [0] |
| omitted | symmetric fixture with only positive anchor; intentional inadequacy, not missing-mode detection |
| narrow | square h; initial variance 4, transition N(.95*x,.0225); noise sd .3; square y/anchors |

Report mean, second moment, positive probability, P(x>2.5) and all region masses.
For three, regions are (-infinity,-1), [-1,1], (1,infinity); otherwise negative
and nonnegative half-lines (third region zero). Boundaries have zero mass.
Anchors derive solely from declared h/y or fixed points, never oracle modes.

Independent NumPy Simpson-grid density recursion evaluates the true likelihood
and Gaussian transition. Use grids (10,.05), (10,.025), (10,.0125), (12,.0125)
where pairs denote symmetric radius and spacing. Affine uses .025,.0125,.00625,
then radius12/.00625. Fine-versus-medium and expanded-domain differences must be
<=2e-5 for probabilities and <=1e-5 for log evidence/moments. Affine controls
compare analytic Kalman recursion within 1e-9; symmetric masses within 1e-9.
Keep actual failed references; any resolution amendment must precede candidates,
retain original outputs and preserve tolerances. Grid agreement is not a proof.

## Candidates and fixed policies

Candidate `branch` uses the assessment's mixture of affine likelihoods, equal
explicit rho=1/K and exact surrogate Gaussian conditioning. Candidate `pilot`
uses one 512-draw predictive pilot per observation; fitWeighted with K equal to
the declared anchor count, 50 iterations, tolerance1e-6, minComponentDraws20,
maxDensityEvaluations200000, maxStoredValues100000, ridge variance1e-4 and
covarianceInflation1.5. Any fitter refusal is recorded; no retries or pruning.
Pilot uses fresh owned LXM seed `500000000 + 100*seed + step`, separate from
filter seed. Pilot descendants are discarded, never appended to production.

Explicit child defense .1, N64, WhenEssBelow(.5), Stratified, released default
LXM. These experiment choices are not new library defaults. Component sampling
uses the existing log-tree and full log-mixture density. Branch likelihood/derivative
values are prepared once per observation; parent laws stored in an immutable
exact-value lookup before advancing. At most 100000 stored scalar slots, four
components plus defense, at most N distinct parent preparations per step.

Controls: `bootstrap`; retained cubature and Jacobian (`ekf`) test builders;
the existing manual two-root recipe on square fixtures; exact affine guide on
affine. Confirmation adds `big-branch`, `big-pilot`, `eval-branch`, `eval-pilot`.
The latter two are bootstrap with matched counted model evaluations, not timing.
All methods use true likelihood/transition and unchanged filter correction.
Omitted/merging/flat are intentional stress controls, not exclusions from reporting.
Unexpected errors in any fixture still block that candidate's overall promotion.

## Work calibration and timing

Development seeds110001..110016; confirmation120001..120256; warmups -502,-501
never emitted. No prior study's candidate-acceptance seeds are reused. Rotate
method order by seed. Three fresh JVM confirmation runs replay identical scientific
outcomes; 256 independent replications, not 768. No post-confirmation tuning.

Per candidate/fixture, set time-matched bootstrap N to ceil(64 * candidate median
total time / bootstrap median time), bounded [64,4096]. Set evaluation-matched N
analogously using h/Jacobian calls plus transition/prior draws, including pilot.
Freeze these budgets and hashes before held-out execution. If a development run
fails, its match is marked unavailable and N4096 is retained as a conservative
descriptive control, never a successful calibration. A cap preventing matching
blocks that utility claim. All refusal rows remain in the inventory.

Charge total observation preparation, all conditioning and Gaussian constructions,
pilot draws/likelihood calls, fitting density sweeps, production target/proposal
and component density calls, ancestor selection, stored-value peak and total time
including summaries. Time starts before preparation. Counts are not heap or
wall-clock bounds. Experiment ceiling12 serial hours,6GiB heap,5GiB evidence;
exhaustion is incomplete, not permission to silently drop work.

## Frozen acceptance rules

Every expected fixture/method/seed/step row is required; duplicates, nonfinite
completed rows and budget violations fail analysis. Terminal errors produce explicit
unavailable downstream rows. No successful-run-only promotion comparison.

For every fully completed group and time, check unnormalized evidence and each
moment/region/tail integral against independent truth: absolute mean discrepancy
<=6 Monte Carlo standard errors +1e-4*max(1,abs(reference integral)). This is a
conservative diagnostic screen across the declared inventory, not a finite-sample
simultaneous coverage guarantee. A missing/error group is not a passed check.
Candidate-specific correctness excludes errors in a competing candidate but requires
its own full inventory, all controls and exact scientific replay across JVMs.

Per-seed loss is the time average of squared mean error/max(1,reference variance),
squared second-moment error/max(1,reference second moment squared), plus the sum
of squared errors in all declared region probabilities. Positive/tail probabilities
are separately checked, not double-counted in the primary loss.

Each candidate needs all of the following:

- On both asymmetric and three fixtures, root mean loss <=.9 of its time-matched
  bootstrap, and paired loss difference +2.81 standard errors <0. The multiplier
  is a prespecified approximate one-sided Bonferroni-normal screen for four primary
  comparisons at family alpha .01; not an exact non-normal finite-sample bound.
- Actual median total time <=1.25 of the time-matched bootstrap in each JVM on
  both primary cases; no unavailable/capped match. Evaluation-matched results
  reported separately, never substituted for a failed timing gate.
- At most three excess depleted runs per256 against equal-N bootstrap on symmetric,
  asymmetric,three,narrow. Depletion means any region with reference mass in [.1,.9]
  has estimated mass below .01 at any step. Report paired rate difference and its
  approximate95% interval; this margin is an engineering gate, not a theorem.
- Mean loss <=1.25 of equal-N bootstrap on both affine and weak. Tail-event MSE
  on every fixture <=1.25*bootstrap MSE+1e-5, and per-region MSE on every fixture
  <=1.25*bootstrap MSE+1e-4. These predeclared absolute allowances prevent ratios
  near zero dominating; no posthoc removal of unfavorable boundary/tail fixtures.
- All its correctness, numerical, lifecycle, frozen-budget and baseline regression
  gates pass. A failed candidate stays test-only even if another candidate passes.

Promotion means eligibility for a separate review only. Full CI, packaged consumers,
API/numerical review and explicit approval precede runtime integration. Auxiliary
scores, automatic switching, stochastic stopping changes and default changes remain
out of scope. Preserve source/input hashes, logs, failed revisions and results in
a verified evidence archive, with a plot of actual results rather than predicted gains.
