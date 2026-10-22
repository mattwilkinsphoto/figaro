# Particle-diversity experiment protocol v1

Approved scope: the three stages in [the assessment](PARTICLE_DIVERSITY_RESEARCH.md).
[Research provenance](RESEARCH_REFERENCES.md#inf-14).
Frozen before new experimental outcomes. Research code only; 7.2 runtime source,
the original continuous harness, its fixtures and its 450 gates remain unchanged.

## Stage 1

Use the original growth, growth-sharp and persistent-modes fixtures. Run released
bootstrap and auxiliary methods at 256/1024 candidates, and calibrated auxiliary
at ceiling 256 with floors 32/128. Calibration uses 16 training and 32 evaluation
pilots, score target 64. Seeds 1..256. Record every step, ESS, distinct parents,
parent-selection concentration, expected independent distinct-parent count,
known-region mass, all callback costs and elapsed time. A diagnostics-off replay
must have identical particles/weights/evidence and next RNG behavior; counters
must consume no randomness. Production ancestry excludes pilots and discarded
stopping candidates. No warning threshold is introduced.

## Stage 2

Scalar fixed-budget test harness, separate from released filtering. Prior/transition
bootstrap importance draws; optional output multinomial or stratified resampling;
optional MH on the full empirical predictive-mixture target. Moves are local
Gaussian random walk (sd 0.5) or an independence mixture: 0.5*N(-r,1)+0.5*N(r,1),
where r=sqrt(max(y,0)/a) for square observations, or r=y for linear observations.
The latter mixture is a deliberately simple full-support benchmark proposal, not
automatic nonlinear fitting. Freeze it before drawing production particles.

Candidates use N=64 and K=0/1/4/8 sweeps: no output resampling; multinomial only;
stratified only; multinomial plus local/global moves at K=1/4/8. Resampling-only
and no-resampling outcomes are essential controls. Target evaluations are cached
per current particle but never approximated. Max 2,000,000 transition-density
evaluations per step; no more than 256 candidate particles in this milestone.

Fixtures: the original three (20 observations each); late-disambiguation (40 steps,
initial equal N(-4,.5)/N(4,.5), random walk sd .3, y=4 with square observation
x²/4 and noise .5 for steps 0..31, then linear y=4, noise .5 for steps 32..39);
Gaussian (20 steps, N(0,4) prior, transition .8*x+N(0,.3), observation x+N(0,.5),
y=sin(t)). A separate finite disconnected-support control starts with no positive
ancestor and verifies that transitions/moves cannot manufacture missing support.

Calibration: seeds 2001..2016 for all candidates and bootstrap baselines N=64,
256,1024,4096. Select one candidate by minimum sum of final sign-mass squared
errors on persistent/late fixtures times total callback evaluations. Ties use
method-name order. Exclude no-resampling from candidate selection but retain it
as a control. Select each baseline for confirmation as the lowest calibration
mode RMSE among baseline budgets whose mean total evaluations do not exceed the
selected candidate's on both designated fixtures; if none, mark matched-cost gate
unavailable and do not promote. No held-out results inform selection.

Confirmation: selected candidate, no-resampling N=64 and preselected baseline,
all five fixtures, seeds 10001..10256, repeated in three fresh JVMs with two
unreported warm-up runs per configuration. Repetitions reuse seeds to assess
timing; they are NOT 768 independent statistical replicates. Rotate method order
by seed. Record every outcome, including failures. Expected source RNG: Figaro LXM.

## Mathematical and numerical gates

For frozen previous population mu_N and fixed invariant kernel K_N,
E[Zhat_t * eta_t(phi) | mu_N] = mu_N(f*g*phi) before moves. Unbiased output
resampling followed by K_N replaces phi with K_N(phi); invariance of normalized
gamma_N implies gamma_N(K_N phi)=gamma_N(phi). Multiplying by the previous
evidence estimate and taking expectations gives induction for unnormalized
moments. This requires K_N to depend only on frozen previous population and
predeclared observation/proposal, not on current importance draws or acceptance.
It does not make normalized posterior estimates or log evidence unbiased.

Verify this identity by independent exact rational enumeration for N=2, two
states/two time steps, asymmetric MH proposals, zeros, and all K=0/1/4/8; compare
the Scala transition/acceptance calculation to those exact values. No theorem
transfer to the released random-stopping loop is claimed.

Continuous references: preserve the original independently converged grids;
Gaussian analytic recursion; late fixture deterministic grids at domain +/-12,
spacing .025/.0125 and expanded +/-16 at .0125. Maximum refinement change <=
2e-4 for sign probability, 1e-5 for log evidence/mean/second moment; predictive
mass error <=1e-8. These are numerical convergence checks, not certified bounds.

Test log-weight extremes/zero support, invalid values, asymmetric proposal ratios,
full mixture densities, interruption, exact work cap, deterministic replay and
owner-thread checks. Stage-2 failures stay in results and preclude promotion.

## Promotion gates (no post-hoc relaxation)

On both designated fixtures require RMSE ratio <=0.8 at matched evaluation cost.
Compute paired-seed bootstrap confidence intervals with 10,000 resamples and NumPy
PCG64 seed 91317, upper endpoint at 97.5% (Bonferroni one-sided family of two).
The upper ratio must be <1. Report ordinary 95% intervals too. Zero RMSE makes
the ratio unavailable, not an automatic win. Depletion is mass outside [.01,.99]
only while reference mass is in [.1,.9]; compare worst per-run eligible-step
depletion. Both its rate and exact binomial upper confidence bound must not exceed
the baseline's (same number of seeds). Do not count the asymmetric late posterior
as depletion merely because it correctly concentrates.

At t=0, middle and final step, unnormalized evidence and first/second/sign moments
must be within 6 standard errors +1e-4*max(1,abs(truth)) of exact/grid controls.
This is a conservative regression screen, not proof of unbiasedness. All original
450 checks must pass unchanged. For Gaussian/growth, normalized RMSE may not
exceed the preselected baseline by more than 25% plus .01 times max(1,abs(truth)).
No promotion unless matched elapsed-time curves also show useful improvement
in all three JVMs; interpolation may not extrapolate beyond measured budgets.

## Resource limit and evidence

Whole study allowance: 12 CPU-wall hours of serial local execution, 6 GiB JVM heap,
4 GiB reference scratch, 5 GiB durable evidence; original frozen regression rerun
included. Stop and report before exceeding it, never omit held-out seeds to fit.
Record input/source SHA-256, versions, commands, UTC times, raw logs/errors,
machine-readable measurements and plots in a create-only evidence archive.
Stage 3 records promote/no-promote with every gate. A negative decision completes
the experiment but does not claim a new consumer capability or warrant a release.
