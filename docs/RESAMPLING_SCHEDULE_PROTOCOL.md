# Resampling-schedule experiment: frozen protocol v1

[Research provenance](RESEARCH_REFERENCES.md#inf-15).
Approved bounded follow-up to [the negative diversity experiment](PARTICLE_DIVERSITY_STATUS.md).
Freeze this protocol in Git before candidate execution. No runtime source, stopping
estimator, original 450-check benchmark, library version or previous result changes.

## Basis and scope

Whiteley, Lee and Heine, *On the role of interaction in sequential Monte Carlo
algorithms*, Bernoulli 22 (2016), sections 2.1-2.2, algorithms 2/3 and ESS rule (13),
[v3 full text](https://arxiv.org/html/1309.2918v3), inspected 2026-10-21: retaining
weights when not resampling is essential. Their stability results require their
assumptions; no universal Figaro stability or mode-discovery claim follows.
Also inspected the maintained [particles core](https://github.com/nchopin/particles/blob/master/particles/core.py)
`resample_move`, `reset_weights`, `compute_summaries` and
[official tutorial](https://particles-sequential-monte-carlo-in-python.readthedocs.io/en/latest/notebooks/advanced_tutorial_ssm.html).
The moving master source is a design comparator, not executed numerical evidence.
The previously pinned core URL failed retrieval; no external code is copied or installed.
INF-14 retains the stratified-resampling and numerical provenance.

Use test-only immutable scalar states and existing log arithmetic. No MCMC,
random-stopping adaptation, arbitrary graphs, path reconstruction or automatic
mode discovery. A second selection in the old experiment is a hypothesis to test,
not a proven cause of its negative outcome.

## Algorithms and evidence identity

At initialization draw N prior samples, use log base weights -log(N), and multiply
by likelihood. At each later step, make the resampling decision using only the
previous normalized log weights. If resampling, select N ancestors once, reset
base weights to -log(N), then propagate each selected offspring exactly once.
Otherwise propagate each existing particle once and retain its normalized weight.
Add log likelihood to base weight; log-sum-exp is this step's evidence increment;
normalize and accumulate log evidence. Return weighted estimates before any
unnecessary output resampling.

Conditionally on the previous population, both branches have expected unnormalized
measure sum_i W_i f_i(g*phi). In the resampling branch this uses unbiased offspring
counts, not independent offspring. The branch decision is previous-population
measurable. Multiplication by the previous normalizer and induction gives the
unnormalized identity. Normalized moments/log evidence need not be unbiased.
This argument does not authorize changing the released stopping construction.

Candidates at N=256: `strat` (always one stratified selection), `ess25`, `ess50`,
`ess75` (stratify iff previous ESS/N < .25/.50/.75). No sorting of particles.
Controls at N=256: `single` (one multinomial selection) and `double` (multinomial
selection plus output stratification, matching the prior scheduling penalty).
Both controls are excluded from candidate selection. Finite-resolution stratified
CDF sampling is not a tiny-probability preservation guarantee.

Baselines call released WeightedBoundedParticleFilter at fixed min=max=N:
bootstrap, guided and auxiliary, each N=64/256/1024. Original quadratic observation
guidance reuses ContinuousFilterBenchmark's frozen defensive proposals; linear
Gaussian observations use exact Gaussian conditional components; the finite model
uses exact categorical conditional probabilities. Auxiliary uses defensive lookahead
fraction .1. All guidance is frozen conditional on parent and observation. No pilot
fitting, training on held-out outputs, or new baseline tuning.

## Data, seeds and selection

Preserve original growth/growth-sharp/persistent fixtures, and prior experiment's
late/Gaussian fixtures and independently checked references. All parameter
conventions remain unchanged. Calibration uses these five fixtures only.
New confirmation-only `switching`: states (-1,0,1), prior (.45,.10,.45), transition
rows (.94,.05,.01),(.10,.80,.10),(.01,.05,.94); 32 observations, y=.8 throughout;
observation mean abs(x) for t<24 and x thereafter, Gaussian sd .35. Positive-region
mass means P(x>0). Independent exact-state forward recursion is the reference.
This is one unseen model family, not general coverage of arbitrary models.

Calibration seeds 30001..30016. Confirmation seeds 40001..40256, three serial fresh
JVMs; repetitions assess timing/replay, not additional independent samples.
Warm-up seeds -102,-101, two per configuration/fixture, excluded from estimates.
Rotate configuration order by seed. Preserve every failure and subsequent unavailable
step. No replacing failed seeds. Run all baselines in confirmation for measured
cost/time frontiers. Use Figaro's existing default LXM RNG.

For each run compute mean squared positive-mass error over all steps whose reference
mass lies in [.1,.9]. This time-integrated ambiguous-period error, not final near-one
probability, drives selection and primary tests. Select candidate minimizing sum of
these MSEs on persistent/late times summed mean total callback work on those fixtures;
ties lexical. Select one baseline by lowest summed MSE among baseline configurations
no more costly than the selected candidate on both fixtures; ties lexical then N.
If none is affordable, cost gate unavailable and no promotion. Commit the selection
before any confirmation execution; do not run switching during calibration.

## Controls and gates

- Exact Fraction enumeration: N=2, two states/two observations, asymmetric transition,
  all four candidate thresholds plus always/never resampling; enumerate independent
  multinomial and stratified offspring, verify expected unnormalized vector
  (93/500,23/500). Include true-zero support and constant-likelihood cases.
- Scala tests: correct skipped-weight update/evidence, ESS boundary decision,
  zeros/extreme log weights, stratified count/support, deterministic replay,
  interruption, fixed work bounds; released bootstrap adapter comparison with
  the independent one-selection harness in distribution (not bit-identical streams).
- At first/middle/final steps all confirmation configurations: evidence and weighted
  unnormalized first/second/sign moments within 6 SE + 1e-4*max(1,abs(reference)).
  All original 450 benchmark gates must pass unchanged. No failed outcomes allowed.
- Primary superiority on persistent, late AND switching: integrated RMSE ratio
  <=.8 versus preselected baseline; paired-seed bootstrap 10,000 resamples, PCG64
  seed 491317, one-sided family-wise .05/3 upper quantile <1. Also report ordinary
  95% intervals. Zero baseline error makes ratio unavailable, never a free pass.
- Worst eligible-step depletion (mass outside [.01,.99]) and its exact one-sided
  95% binomial upper bound must not worsen. Reference-eligible steps only.
- Candidate work must not exceed primary baseline work for a direct same-work
  claim; when baseline is cheaper, require <=.8 error versus every measured baseline
  no more costly than candidate. The same <=.8 frontier requirement applies to every
  baseline no slower than candidate in EACH of three JVMs, with no extrapolation.
- Gaussian/growth final mean/variance/sign RMSE <=1.25*primary baseline RMSE plus
  .01*max(1,abs(reference observable)). No threshold relaxation after outcomes.

Report mean/variance/sign errors, evidence-relative error exp(logZhat-logZ)-1 and
log-evidence error separately, ESS, distinct immediate parents, resampling counts,
all callback categories and elapsed time. Distinct parents are not surviving modes
or valid full histories. Count state draws, likelihood/target/proposal-density calls,
guidance constructions, lookahead calls and ancestor selections separately; work
is their sum. Count underlying Gaussian mixture component density evaluations as
additional setup/density work where guidance uses them; report that convention.
Wall-clock comparisons guard against unequal callback costs. Timing includes step
setup, excludes warm-up/process startup/output; record those separately via logs.

## Budget, delivery and decision

Maximum 12 hours serial local study, 6 GiB JVM heap, 4 GiB reference scratch, 5 GiB
evidence. Resource-only repeat may measure process memory but not add statistical
replicates. Preserve input/source hashes, software versions, commands, actual logs,
errors, timestamps, machine results and plots in a create-only archive.
Implement and test, freeze calibration selection, confirm and rerun frozen regression,
then record an evidence-led recommendation. No public API even on success without
separate integration review. Commit/push protocol, implementation and results;
no main merge/publication is authorized by this experimental milestone.
