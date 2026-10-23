# Nonlinear observation-informed proposals: assessment and bounded plan

**PF-PROPOSAL-01 — assessment complete; test-only experiment approved.** Reviewed
2026-10-23. This is a recommendation for a separately approved experiment, not a
new API, frozen experimental protocol, performance result or release commitment.
[Research/code map: INF-17](RESEARCH_REFERENCES.md#inf-17).

Subsequent execution is recorded in the [status](NONLINEAR_PROPOSAL_STATUS.md)
and [frozen protocol](NONLINEAR_PROPOSAL_PROTOCOL.md). The assessment below is
the preserved rationale; its proposed numerical gates are superseded by the
separately frozen protocol, not changed in response to candidate outcomes.

## Recommendation

Start with a **training-free, derivative-free cubature Gaussian proposal**, with
an explicit-Jacobian local Gaussian as the simpler comparator. Use the existing
fixed-budget filter and normalized proposal interfaces. Require an explicitly
chosen defensive prior fraction; do not introduce a default or automatic switch.
Test scalar and small vector states before considering broader support.

This is an engineering preference for the first experiment, **not evidence that
cubature wins**. A bounded mixture is the next candidate if single-Gaussian guides
fail the intended utility tests. Do not expand the experiment into learned
proposals, generic automatic differentiation or a new particle-filter engine.

The practical benefit sought is less wasted sampling when an informative nonlinear
observation concentrates probability in a small part of a transition distribution.
Users would supply their observation function instead of hand-building a guide.
Weak observations, cheap transition sampling, multimodal conditionals and expensive
observation functions may favor bootstrap or a manually supplied proposal instead.
There is no speedup estimate yet.

## Literature and implementation evidence

This is a targeted relevant-section review, not an exhaustive survey or independent
verification of published experiments. Source scope is explicit below. No external
code, datasets, model weights or dependencies have been incorporated or executed.

| Source and inspected scope | Finding relevant to this decision |
| --- | --- |
| van der Merwe, Doucet, de Freitas and Wan, [The Unscented Particle Filter](https://proceedings.neurips.cc/paper/1818-the-unscented-particle-filter.pdf), NeurIPS 2000, sections 2–6; detailed technical report not reviewed | Observation-informed Gaussian proposals can be importance-corrected. The paper motivates sigma-point proposals and reports a favorable example; it does not establish universal superiority or a Figaro result. Its full per-particle UKF machinery is not the proposed implementation. |
| Doucet and Johansen, [particle-filtering tutorial](https://www.stats.ox.ac.uk/~doucet/doucet_johansen_tutorialPF2011.pdf), corrected March 2012 text, sections 4.1–4.3 | Proposal quality, ancestor selection and path degeneracy are distinct. Even an optimal conditional proposal can leave large parent-weight variability. Healthy-looking weights are not sufficient evidence of accurate inference. |
| Solin, [Cubature Integration Methods in Non-Linear Kalman Filtering and Smoothing](https://users.aalto.fi/~asolin/documents/pdf/Solin-2010-BSc-thesis.pdf), 2010, spherical-radial rule, update listing and Appendix A | An accessible derivation of the third-degree, positive equal-weight, 2d-point rule and its relation to the unscented transform. It cites Arasaratnam and Haykin (2009); that original paper was not separately full-text reviewed here. This supplies the bounded derivative-free candidate, not a posterior-accuracy guarantee. |
| Branchini and Elvira, [Optimized Auxiliary Particle Filters](https://proceedings.mlr.press/v161/branchini21a/branchini21a.pdf), UAI 2021, abstract and sections 3.3–3.4 | Optimizes mixture selection weights with a constrained problem; experiments use transition kernels. This is not a ready-made nonlinear conditional-Gaussian builder. Changing ancestor optimization and weighting would broaden this milestone. |
| Cox et al., [PropMixNN](https://sarapv.github.io/files/paper/cox2024end.pdf), ICASSP 2024, abstract and section 3, including Algorithm 2 | Learns equally weighted, diagonal-covariance Gaussian-mixture proposals with a neural network and differentiable filtering. A serious future option, but training, gradients and validation under distribution shift are additional work. Published gains were not reproduced. |
| Chen and Li, [Normalizing Flow-based Differentiable Particle Filters](https://arxiv.org/html/2403.01499v2), January 2025 revision, abstract, sections IV-B/IV-D and experiment/code overview | Invertible observation-conditioned transformations permit evaluable proposal densities. Flexibility comes with learned transformations and Jacobian accounting; this is a separate dependency/training design, not a small helper. |
| Bunch and Godsill, [Gaussian particle-flow importance sampling](https://arxiv.org/abs/1406.3183), v3, abstract only | A later option for moving particles through difficult likelihoods, with numerical integration and correction obligations. The earlier [progressive-proposal preprint](https://arxiv.org/abs/1401.2791) was withdrawn and superseded; do not use it as the implementation specification. |

The open author copy of PropMixNN was accessible; no paywalled PDF is needed for
this decision. Detailed learned-method or particle-flow adoption would require a
deeper algorithm, license and numerical review first.

### Reuse and maintenance screen

- **particles:** inspected `GuidedPF`, `AuxiliaryPF` and proposal hooks at
  [3388c4bd](https://github.com/nchopin/particles/blob/3388c4bd30ab9acc5a27c970089230f93610b627/particles/state_space_models.py),
  whose latest commit was dated 2026-09-24 when checked. The
  [MIT license](https://github.com/nchopin/particles/blob/3388c4bd30ab9acc5a27c970089230f93610b627/LICENSE)
  was inspected. Useful maintained Python comparator; user-defined proposal hooks
  do not themselves supply a generic nonlinear constructor. No Python runtime
  dependency in Figaro is proposed.
- **FilterPy:** inspected point construction, moment transform and update portions
  of [CubatureKalmanFilter](https://github.com/rlabbe/filterpy/blob/3b51149ebcff0401ff1e10bf08ffca7b6bbc4a33/filterpy/kalman/CubatureKalmanFilter.py)
  and its [MIT license](https://github.com/rlabbe/filterpy/blob/3b51149ebcff0401ff1e10bf08ffca7b6bbc4a33/LICENSE).
  HEAD was dated 2022-08-22 when checked: treat it as an established reference,
  not evidence of recent maintenance. Its stateful filter is not a drop-in immutable
  JVM proposal law or independent posterior oracle. Web retrieval failed; the
  authorized GitHub API supplied the inspected source instead.
- **Normalizing-Flows-DPFs:** inspected the
  [README](https://github.com/xiongjiechen/Normalizing-Flows-DPFs/blob/1cfcf8ee53f9bd82ff29f4105add68b324a87ab9/README.md)
  and repository file listing at `1cfcf8ee` (HEAD dated 2025-01-03).
  GitHub's license endpoint returned 404, and no license/copying file appeared in
  the filename screen. Reuse permission is **unverified**, not presumed permissive;
  the README also identifies borrowed implementations requiring their own review.
  This is a research-code lead only, not an approved dependency.

The concrete integration gap is small: Figaro has full-rank Gaussian laws,
Cholesky-based linear conditioning, defensive mixtures and corrected fixed-budget
filtering, but no nonlinear moment-to-proposal adapter. Reuse those components
and the existing Commons Math linear algebra backend rather than introducing a
second inference engine. A small independently written Scala adapter is justified;
copying third-party source would need separate attribution/license handling.

## Candidate tradeoffs

| Candidate | Requirements / work | Main weakness | Recommended disposition |
| --- | --- | --- | --- |
| Local Gaussian, EKF-like | Observation value and caller-supplied Jacobian at the conditional mean; existing linear conditioning can be reused | Local slope can vanish or misrepresent curvature; one component cannot represent separated alternatives | Required simple comparator; potentially sufficient for mildly nonlinear cases |
| Spherical-radial cubature Gaussian | 2d observation-function evaluations per conditional guide plus matrix work; no Jacobian or unscented spread-parameter search | Low-order moment approximation and Gaussian conditioning can be poor; finite-precision covariance still needs checks | First prototype candidate, not a selected production winner |
| Bounded pilot-fitted Gaussian mixture | Discarded weighted pilot, explicit component/iteration/evaluation limits and full mixture density | Pilot can miss modes; per-parent fitting may cost more than extra bootstrap particles | Follow-on only if single-component limits justify it; reuse existing bounded fitter |
| Multi-start local/Laplace mixture | Explicit starts and bounded optimization/curvature work | No guarantee of finding all modes; deduplication and component weighting need their own design | Defer rather than add an optimizer to this first milestone |
| Learned mixtures / flows | Training data or observation sequences, optimizer, model lifecycle and shift tests | Training overhead, reproducibility and out-of-distribution performance | Longer-term candidate, not this milestone |

### Strongest counterexample

Consider a symmetric Gaussian conditional prior and `y = x*x + noise`. Symmetry
gives `Cov(x, x*x) = 0`; a symmetric sigma-point rule also gives zero cross-covariance.
Its Gaussian conditioning gain is zero. An EKF at zero has the same problem because
the derivative is zero. Yet a sufficiently informative positive observation can
give two separated posterior modes. This is an analytic design counterexample,
not a newly executed numerical experiment. More accurate moment integration alone
does not remove the single-Gaussian conditioning limitation.

A poor Gaussian guide remains a mathematically valid proposal when properly
corrected and support-preserving; that does not make it useful. A defensive prior
component protects support, not finite-sample mode discovery. Neither ESS nor an
approximation-disagreement diagnostic can certify that missing modes do not exist.

## Bounded model and integration contract

The proposed initial scope is **state dimension 1–4 and observation dimension 1–4**,
an engineering validation boundary, not a theoretical maximum. The true initial
or per-parent transition law must be a known, normalized, full-rank Gaussian.
A nonlinear transition mean is allowed when the conditional law is still Gaussian.
Observations have form `y = h(x) + e`, with independent additive Gaussian noise
of known positive-definite covariance. Functions and observations are fixed for
each step. No hidden replacement of the true model by its approximation.

The prospective helper takes the conditional Gaussian, observation, pure `h`,
noise covariance and explicit method/policy. The Jacobian comparator additionally
takes a pure Jacobian callback. It returns either an immutable normalized guide,
an approximate predictive log score and a preparation report, or an explicit
refusal. These are conceptual outputs, **not currently callable API names**.
The approximate score is labeled approximate even though the proposal's own
Gaussian density is normalized and evaluable.

Reuse:

- [WeightedParticleProposal](../Figaro/src/main/scala/com/cra/figaro/algorithm/filtering/WeightedParticleProposal.scala):
  Gaussian laws, `linearGaussianObservation`, `defensive` and `Kernel`.
- [FixedBudgetParticleFilter](../Figaro/src/main/scala/com/cra/figaro/algorithm/filtering/FixedBudgetParticleFilter.scala):
  existing guided/auxiliary calls and correction accounting.
- [GaussianMixtureProposal](../Figaro/src/main/scala/com/cra/figaro/algorithm/sampling/GaussianMixtureProposal.scala):
  possible later `fitWeighted` reuse. Its current dimensions 1–32, components 1–8,
  explicit regularization and refusal behavior do not automatically validate
  per-parent nonlinear filtering or supply an exact predictive normalizer.

For a guide `qg`, use `q = epsilon*p + (1-epsilon)*qg`, with explicitly supplied
`0 < epsilon < 1`. Sampling and scoring must use the same **full** mixture.
True log likelihood, true transition density and full proposal density remain in
the correction. An approximate predictive score may inform ancestor selection;
it is not another observation factor or an evidence estimate. Ancestor defense
and child-proposal defense remain separate settings.

Preserve initialization, carry, ordinary resampling and auxiliary correction
semantics in the [released protocol](GUIDED_FIXED_BUDGET_PROTOCOL.md). The trigger
continues to use previous completed posterior ESS. Preparation is deterministic
and frozen within a step; no tuning from production draws. Cache only exact
per-step immutable inputs under explicit storage limits, without rounding parent
states or silently changing the target. Charge cache misses, hits and all setup
work; avoid persistent mutable caches captured by callbacks.

Use solves rather than explicit inverses. Check dimensions, finiteness, covariance
symmetry/conditioning and output validity with existing numerical boundaries.
No implicit jitter, clipping, successful retries, noise inflation or bootstrap
fallback. Explicitly supplied proposal inflation, if studied, must be frozen in
the protocol and used in both sampler and density. Refusal before a step must not
mutate the filter; in-step callback failures retain existing terminal semantics.

Excluded: arbitrary `Element` graphs, singular/deterministic constraints, bounded
or manifold states, non-Gaussian noise, mixtures as true conditional priors,
automatic mode finding, smoother/rejuvenation changes and universal guarantees.
Legacy mixture-prior fixtures remain regression/manual-guide controls, not a
reason to silently widen this helper's scope. Released stopping estimators,
bootstrap defaults, RNG policy and resampling defaults remain unchanged.

## Proposed three-stage work order

### 1. Freeze the contract and independent protocol

After plan approval, specify equations, resource ceilings, callback ownership,
preparation counts and failure statuses. Derive the two builders independently
of the existing filter's weighting code. Freeze fixtures, seed manifest, budgets,
metrics, statistical tolerances and utility thresholds **before candidate runs**.
This document proposes gates; it does not claim that a runnable protocol is frozen.

Build independent analytic affine-Gaussian and scalar quadrature references first,
with domain expansion and resolution convergence tests. Use a separate numerical
implementation, not the candidate sigma points or a second copy of its formulas
as the posterior oracle. A third-party CKF can cross-check construction, not truth.
Preserve reference errors and failed checks. Suggested experiment ceiling: 12 hours
serial execution, 6 GiB JVM heap and 5 GiB evidence; exhaustion means incomplete,
not permission to drop fixtures.

**Exit:** reviewed contract and frozen protocol; references pass their self-tests.

### 2. Implement isolated test-only builders

Prototype cubature and explicit-Jacobian guides using existing laws/corrections.
No runtime API change. Test affine reduction against the current exact helper;
matrix orientation, correlated covariances, scaling, full mixture density and
sampling agreement; invalid inputs, covariance failure, overflow, interruption,
callback exceptions and preparation budgets. Verify production draws never enter
preparation, and existing bootstrap/stopping results and RNG behavior are unchanged.

Separate guided-only from auxiliary-plus-guided tests to identify whether gains
come from child proposals or ancestor selection. Include previous-weight carry
and both existing ancestor methods. Reuse exact finite accounting controls as
regressions; do not claim continuous states became exactly enumerable.

**Exit:** construction/accounting tests pass; no claim of practical benefit yet.

### 3. Independent held-out validation and promotion review

Use separate development and confirmation seeds, with the latter unseen during
tuning. Freeze configuration selection on development data, then confirm once;
changes after failures require a new versioned protocol and fresh holdout.

Required fixtures:

| Case | Question and independent reference |
| --- | --- |
| Affine scalar and correlated vector Gaussian | Does the builder recover analytic conditioning and evidence? |
| Smooth monotone nonlinear observation, ordinary and sharp noise | Is there useful gain where a local Gaussian is plausible? Scalar quadrature truth |
| Symmetric square observation and asymmetric two-mode variant | Does the method retain mode mass, or merely yield a misleading mean/ESS? Converged grid and symmetry controls |
| Weak observation and deliberately poor Jacobian/guide | Is extra work wasted, and does exact correction remain correct? Same true-model references |
| Low-dimensional curved observation | Does usefulness survive beyond one dimension? Independently converged quadrature, otherwise no promotion claim for that dimension |
| Far-tail observations and near numerical boundaries | Does the method correctly refuse outside scope without silent repair? Separate supported and expected-refusal cases |

Compare unchanged bootstrap, larger bootstrap budgets, the exact affine guide,
both candidate builders and the existing hand-built two-root recipe on its
applicable fixtures. The [continuous benchmark](CONTINUOUS_FILTER_BENCHMARKS.md)
and [7.4 protocol](GUIDED_FIXED_BUDGET_PROTOCOL.md) remain untouched regressions.
Their old seeds/results cannot stand in for fresh candidate acceptance.

Count all preparation `h`/Jacobian evaluations, factorizations, density calls,
draws, ancestor scores, storage and end-to-end time. N children is not N units of
work. Select cost-matched bootstrap budgets from development calibration, freeze
them, and report actual confirmation costs rather than assuming a nominal match.
Use repeated warmed JVMs and rotated order; repeated timings of the same seed are
not independent statistical replications.

Proposed acceptance rules, to be finalized before execution:

1. All deterministic correctness/refusal tests and independent oracle checks pass.
   Record every expected row, failure and subsequent unavailable step. Unexpected
   numerical refusal blocks that configuration's promotion; no successful-run-only
   ranking or conversion of refusal into zero evidence.
2. Unnormalized evidence and first/second moments agree with independent references
   under predeclared Monte Carlo screens. Assess normalizing constants, not claimed
   unbiasedness of log evidence or normalized posterior moments. Multiple comparisons
   and reference error allowances must be specified in the frozen analysis.
3. Measure posterior moment error, event/mode mass error and mode-depletion frequency
   across the entire sequence. ESS is secondary. For promotion, target at least a
   10% reduction in the preselected error metric against cost-matched bootstrap on
   two designated nonlinear fixtures, with uncertainty intervals supporting benefit.
   Predeclare acceptable regressions on weak-data cases and mode-loss margins; do
   not choose a metric after seeing which one improves.
4. Supported-domain safety and utility are separate. A transparent, corrected but
   inefficient guide is not a successful capability improvement. If only a narrow
   unimodal domain passes, recommend only that scope. Do not market it as a multimodal
   solution; failing mode-retention gates requires a bounded-mixture follow-up.
5. Runtime promotion requires a separate review of API, numerical scope and independent
   results, followed by relevant full CI and packaged-consumer acceptance. No automatic
   merge, release, default change or transfer to the released stopping estimator.

Save exact source/protocol/input hashes, seeds, versions, commands, real logs/errors,
all outcomes, independent analysis and plots in a reproducibility archive. Preserve
negative results. If construction cost erases gains or single-Gaussian limitations
dominate, stop and recommend the separately scoped mixture assessment rather than
repeatedly tuning the same method until a favorable result appears.

## Completion and next decision

- Verified: targeted literature/reuse assessment, existing-code compatibility review
  and this bounded recommendation.
- Not executed: candidate implementation, numerical experiments, independent
  acceptance, runtime promotion or publication.
- Next decision: approve stages 1–3 as a test-only milestone. Any expansion to
  learned proposals, arbitrary graphs, a new numerical dependency or stopping
  changes requires a separate architectural decision.

Research-only documentation does not warrant a new consumable release. The current
adoption baseline remains 7.4.0. Related: [fixed-budget guide](GUIDED_FIXED_BUDGET_FILTER.md),
[auxiliary helpers](AUXILIARY_PARTICLE_FILTER.md), [roadmap](../ROADMAP.md) and
[wishlist](../WISHLIST.md).
