# Nonlinear proposal experiment: frozen protocol v1

[Provenance and method review](RESEARCH_REFERENCES.md#inf-17).
Frozen before prototype or sampling results; follows the approved
[three-stage plan](NONLINEAR_PROPOSAL_RESEARCH.md). Test-only, no runtime promotion,
default changes or change to the released stopping estimator.

## Contract and construction

Immutable Euclidean vectors, state/observation dimensions 1–4; exact Gaussian
initial/conditional transition and independent additive Gaussian observation noise.
All covariance and density restrictions of the existing Gaussian kernels apply.
Explicit method, defensive fraction .1 for this experiment (not a new default),
and preparation budget. Pure deterministic callbacks; no RNG or production draws
in preparation. Failure is explicit, no jitter, clipping, fallback or retry.

EKF comparator: H=Jacobian(h,m); condition the exact Gaussian prior using affine
surrogate H*x+h(m)-H*m and the stated noise R. The deliberately poor comparator
uses -H but retains the same true likelihood in importance weights.

Cubature: x_j=m +/- sqrt(d)*L_j, P=L*L', equal weights 1/(2d).
Compute transformed mean b, centered cross covariance C=Cov(x,h) and solve
P*H'=C. Compute E=R+sum_j w_j (h(x_j)-b-H*(x_j-m))*(...)'.
Condition using H*x+b-H*m with covariance E through the existing linear helper.
This positive residual-sum form is algebraically the joint-moment Gaussian
conditioning update; it avoids subtracting nearly equal covariance matrices.
This is a Figaro test-only implementation choice, not copied source or a new theorem.
Cholesky solves are scaled using prior marginal standard deviations.

Each result contains normalized guide, approximate log predictive score and exact
top-level preparation counts. Sampler and density use the full .1 prior + .9 guide
mixture. The existing true p*g/q correction and auxiliary parent correction remain
unchanged. Preparation is bounded to 2d h calls (or one h and one Jacobian call),
one explicit prior factorization for cubature and the existing helper's fixed work.
Record Gaussian constructions (each includes eigen/Cholesky work), explicit
factorizations/solves, h/Jacobian/density/draw/ancestor calls and total wall time.
Counts are operation categories, not equivalent CPU units.

No cache optimization in v1: deterministic reconstruction per requested conditional
law, including ancestor scoring. Charge every reconstruction. This conservative
baseline makes a future cache optimization separately measurable.

## Fixtures and independent references

Synthetic, specified observations, no external dataset. Time zero is observed.
Scalar initial N(m0,1.21), transition N(.7*x,.64), with variance convention here.
Monotone h(x)=x+.25*x^3; square h(x)=.25*x^2. Scalar observations:

| Fixture | m0 | h / noise sd | Observations |
| --- | --- | --- | --- |
| affine | 0 | x / .5 | .4,1.2,-.3,.7,1.5,.2 |
| mono | 0 | monotone / .5 | .4,1.2,-.3,.7,1.5,.2 |
| sharp | 0 | monotone / .15 | .4,1.2,-.3,.7,1.5,.2 |
| weak | 0 | monotone / 4 | .4,1.2,-.3,.7,1.5,.2 |
| symmetric | 0 | square / .5 | 1,1.2,.8,1.4,1,.7 |
| asymmetric | .8 | square / .5 | 1,1.2,.8,1.4,1,.7 |
| tail | 0 | monotone / .5 | 6 |

Vector curved control is one observation y=1, h(x)=x0+.3*x1^2, R=.25,
prior mean (0,0), covariance ((1,.3),(.3,1)). Independent 2D density integration;
this does not validate a multi-step 4D nonlinear filter. Affine construction tests
cover dimensions 1–4 and correlated matrices. Singular matrices, nonfinite inputs,
overflow and depleted budgets are expected-refusal unit controls, not successful
sample runs.

Independent NumPy reference uses direct density recursion on uniform grids, not
the proposal equations. Scalar grids [-10,10] with spacing .05,.025,.0125, and
expanded [-12,12] at .0125; integrate with Simpson weights, splitting at zero for
positive mass. Vector grids [-8,8], spacing .1,.05,.025; expand to [-10,10] at .025.
Require fine/medium and expanded/fine differences: log evidence, mean and second
moment <=1e-5; positive mass <=2e-5. Check scalar affine recursion independently
with Kalman equations and symmetric positive mass=.5 (tolerance 1e-9).
Reference convergence is an empirical numerical check, not a rigorous error bound.
Reference failure blocks sampling until explained; preserve failures and amendments.

Pre-candidate reference amendment: the original affine fine grid (.0125) missed
the analytic positive-mass tolerance at three steps (maximum 2.11e-9 against
1e-9); its coarser unit-test grid also failed. Retain both failures and originals.
Refine ONLY affine grids to .025,.0125,.00625, expanded .00625; use .00625 in
the affine unit test. No tolerance, fixture, candidate or utility criterion changes.

## Stage 2 controls

Test affine equivalence, orientation/correlation/scaling, zero slope/cross covariance,
mixture log density and sample consistency, immutable repeatability/no RNG use,
explicit work budgets, malformed callbacks, covariance/range failures, interruption
and in-filter terminal callback semantics. Run existing guided fixed-budget,
stratified and released stopping regressions plus exact finite oracle controls.
Production sources, build version and defaults must be byte-unchanged from baseline.

## Stage 3 experiment and selection

N=64; policy WhenEssBelow(.5), method Stratified, default released RNG explicitly
recorded. All fixtures use bootstrap, cubature guided, EKF guided, cubature
auxiliary+guided and poor-Jacobian guided. Square fixtures additionally use the
unchanged INF-12 manual two-root recipe (persistent-modes observation parameters),
but the Gaussian priors/transitions specified above. Affine exact helper is an
additional control. No mixture-prior widening of the candidate contract.

Development seeds 89001..89016; confirmation 90001..90128. Two excluded warm-up
seeds -402,-401 per fixture/configuration/JVM. Rotate methods by seed. No algorithm
parameter selection from development results: both candidates are retained.
Development is used only to freeze a per-fixture larger-bootstrap N:
ceil(64*max candidate median total time / bootstrap median total time), bounded
below by 64 and above by 4096. Include cubature/EKF/auxiliary in this maximum,
not intentionally poor guidance. Report when the cap prevents a cost match.
This shared larger-bootstrap comparator is conservative for cheaper candidates.

Three fresh confirmation JVMs repeat identical seeds. Require scientific row replay
excluding timing; these are 128 statistical replications, not 384. Count all work
from before prior/guide construction until weighted summaries, including setup.
Record every attempted row, exact failure and all subsequent unavailable steps.
No unexplained failure or supported-domain refusal may be dropped from comparison.

Correctness screens at each time: sample means of unnormalized evidence, first
moment, second moment and positive mass against reference within 6 empirical SE
+ 1e-4*max(1,abs(reference)). This is a conservative multiple-check regression
screen, not a coverage theorem; report all checks without selecting favorable ones.
Any failure blocks promotion review pending explanation, not automatic seed reruns.

Per-run primary loss is the time-average of
(mean-error)^2/max(1,reference variance) +
(second-error)^2/max(1,reference second^2) + (positive-mass-error)^2.
Report each component, ESS and depletion separately. Depletion means any step
outside [.01,.99] when reference positive mass is in [.1,.9].

A candidate qualifies for useful narrow-domain promotion review only if BOTH mono
and sharp have sqrt(mean loss) <=.9 times cost-matched bootstrap, and the paired
mean loss difference plus 2.62 SE is <0 (a conservative one-sided screen for the
six candidate/primary comparisons). Actual median total time must be <=1.25 times
larger bootstrap in all three JVMs; a missing cost match is not a passed gate.
Weak-case mean loss <=1.25 times equal-N bootstrap; symmetric/asymmetric depletion
must exceed equal-N bootstrap in no more than 3 of 128 runs. These are explicit
practical margins, not theoretical constants. No general multimodal promotion from
unimodal success. Report tail and vector outcomes even if unfavorable. No candidate
selection after seeing confirmation results; preserve all negative findings.

Ceilings: 12 hours serial, 6 GiB JVM, 5 GiB evidence. No new runtime dependencies.
Save source/protocol/input hashes, versions, commands, timestamps, actual logs and
errors, all outcome rows, analysis, plots and rerun instructions in a verified
archive. Existing benchmark controls stay unchanged. Runtime promotion, merge and
publication require separate review; this request authorizes test-only commits/pushes.
