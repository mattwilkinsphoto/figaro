# TJB-01: caller-specified transformed joint blocks

Frozen protocol v1, 2026-10-26, before candidate execution. This is an approved
bounded experiment, not a shipped API or an automatic learned proposal. The
[API review](TRANSFORMED_JOINT_BLOCK_API_REVIEW.md) supplies the contract and source
assessment. Published baseline: 7.4.1. Stopping, defaults and strict calibration
remain unchanged. No outside implementation or data is incorporated.

## Stage 1: contract and independent controls

Candidate `TransformedJointBlock(roots, incrementCovariance)` has two required
arguments, two through eight exact constant Normal/Gamma/Beta roots, all stochastic
roots of one isolated fixed graph. Coordinate order is root order: identity,
log and logit. Covariance is fixed, copied, finite, exactly symmetric and numerically
positive definite. No jitter, defensive mixture, tuning, default replacement or
automatic fallback. Exact Constant/Apply1-5/Inject descendants and pure local hard
conditions/log constraints only. No observed/intervened/contingent/dynamic/shared
graphs. One sampler and executing thread own each scheme. No composition,
anytime/resume or precision-checkpoint runner. Reject unsupported use explicitly.

One joint proposal includes each prior and inverse-transform Jacobian once.
Rejection restores touched roots/descendants; exceptions and interruption restore
values, randomness, accepted-score caches, dissatisfaction and proposal counters.
RNG and external callback side effects are not reversible. Numerical range errors
are failures, not clipping, retrying or silently accepting ordinary rejection.

Before utility execution require deterministic matrix/density/Jacobian controls,
permutation, scales, dimensions 2/4/8 and rejection of 1/9, singular/near-singular
matrices, rounded endpoints, nonfinite scores, early hard rejection, repeated
rejection, downstream exceptions, interruption/cleanup, ownership/reuse, parameter,
graph and evidence mutation, and unsupported composition/runners. Retained adapter
tests are inputs, not new passes. Independent SciPy density/matrix calculations
must audit actual emitted values; negative controls must detect omitted, doubled
and sign-reversed Jacobians, wrong priors, duplicates and missing outputs.
Any unresolved correctness/rollback/ownership failure stops this milestone.

## Stage 2: exact model definitions

Let z be the transformed root coordinates. The graph uses independent base priors
Normal(0,1), Gamma(shape=2, scale=3), Beta(2,3), and a deterministic log constraint
`log f(z) - log|dx/dz| - sum(log basePrior(x))`. Thus the posterior density of z
is the declared f, not a fitted target. These are deliberately analytic controls,
not application models. Oracle covariance is supplied free of estimation work;
no claim about learning geometry is permitted. Construction and evaluation costs
are measured. No empirical covariance may replace the fixed matrices below.

| Fixture | Roots | f(z) and supplied target covariance |
| --- | --- | --- |
| product2 (ordinary) | Gamma, Beta | independent Normal coordinates, means (0.2,-0.3), variances (0.25,0.64) |
| product8 (ordinary) | Gamma, Beta, Normal, Gamma, Beta, Normal, Gamma, Normal | means (0.2,-0.3,0.1,0.2,-0.3,0.1,0.2,0.1), variances (0.25,0.64,0.49,0.25,0.64,0.49,0.25,0.49), independent |
| correlatedNormal (ordinary) | Normal, Normal | mean (0,0), covariance ((1,0.97),(0.97,1)) |
| coupledSupport (ordinary) | Gamma, Beta | mean (0.2,-0.3), covariance ((0.25,0.388),(0.388,0.64)) |
| curved (adverse) | Gamma, Beta | z0~N(0,1), z1=0.8(z0^2-1)+0.3e, independent e~N(0,1); supplied covariance diag(1,1.37) |
| separated (adverse) | Gamma, Beta | equal mixture N((-2.5,-1.5),C) and N((2.5,1.5),C), C=((0.36,0.12),(0.12,0.49)); supplied covariance ((6.61,3.87),(3.87,2.74)) |

Four arms: released default; released joint-prior `ProposalScheme(roots*)`;
uniform random choice of released fixed scalar support-aware moves; candidate.
Candidate increment covariance is `suppliedCovariance/dimension`. Scalar increment
SD is the square root of the corresponding marginal variance (one root selected
per transition). These fixed engineering choices are not optimal-scaling claims.
No tuning/search/retries. Run each arm on the same complete graph and query set.

Queries: each raw and transformed coordinate's mean and second moment; coordinate
0 minus coordinate 1 in transformed units and its second moment; indicators z0>0
and z1>0. Also report variances derived from moments and mode occupancy. Reference
means/second moments/variances are analytic Gaussian/lognormal or independent
deterministic SciPy quadrature (conditional Gaussian integration for curved/mixed
cases). Validate quadrature at tighter tolerances before sampling. Store numeric
truth and tolerances before the first candidate trajectory. No Monte Carlo oracle.

Mean tolerance: 0.12 times the reference SD of the query. Second-moment query
tolerance: 0.12 times its own reference SD. Event-probability tolerance: 0.04.
Derived variance tolerance: 0.15 times its true variance. All tolerances are
absolute once evaluated from reference moments. No adaptation to observed errors.
Zero/nonfinite oracle variances or unqualified quadrature stop before sampling.

## Seeds, budgets, costs and exit gates

Development seeds: 7412202601 through 7412202608 inclusive. Confirmation seeds:
7412202701 through 7412202732 inclusive, reserved and unused unless development
passes. Add 10000 times the zero-based fixture index in the table; arms share
the resulting root seed, with the released owned-stream policy. This is paired
seed allocation, not a claim of matched paths or independent arms. Check freshness
against retained campaign seed records before execution.

Four chains, 12000 transitions per chain including 4000 discarded warm-up, 8000
retained, thinning 1, one worker for cost comparisons. No early precision stopping.
Run order per seed/fixture rotates the four arms by `(seedIndex+fixtureIndex)%4`.
At most 192 development attempts; at most 768 confirmation attempts. One JVM per
phase, fixed settings, no adaptive reruns. Before measurement, fixed untimed
correctness smoke warm-up is permitted only on separate seeds 7412202501/2502,
128 draws and 64 warm-up; its cost and outcomes must be recorded separately.

Each phase is capped at four wall-clock hours, 12 GiB process memory and 4 GiB
output. A cap hit means incomplete, stops the phase and disallows confirmation.
Record startup, setup, sampling, diagnostics, trace serialization and audit costs;
include all measured per-attempt construction/sampling/diagnostics/output time in
the utility score. Retain failures and their spent work. Report callback evaluation
counts, acceptance, all raw outcomes, R-hat/bulk/tail/mean ESS and MCSE. Independently
recompute diagnostics from traces; uncertainty/absence is not a passing guarantee.

Development requires all 8/8 candidate outcomes per ordinary fixture within every
accuracy tolerance; confirmation requires at least 30/32 per ordinary fixture.
Any numerical/execution correctness or lifecycle failure is a hard stop. Adverse
inaccuracy and mode occupancy remain visible and do not become ordinary success.

Utility score per fixture/arm: maximum over queries of the mean squared
tolerance-normalized error, multiplied by mean total attempt seconds. Include
derived variance in the query inventory. Missing outcomes incur loss one for every
query and retain all spent time; zero/incomplete comparator scores are inconclusive.
Compare to the best complete released control: score ratio <=0.8 for each of the
two correlated ordinary fixtures; <=1.25 for each product fixture. Report
delivered-only error, delivery rate and paired-seed uncertainty separately.
Failing any ordinary accuracy/utility/audit gate ends development without
confirmation or promotion. Do not drop fixtures or revise matrices/budgets to pass.

## Stage 3: separate promotion review

Only after development and fresh confirmation independently pass: full CI,
compatibility, packaged examples, one/multiple owned workers and failure cleanup,
then a separate promotion decision. No merge into the released runtime or new
release solely because arithmetic controls pass. Any future compatible public API
is a minor-release candidate; 7.4.1 remains immutable.

[Research provenance](RESEARCH_REFERENCES.md#inf-22).
