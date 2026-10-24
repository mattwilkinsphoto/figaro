# Multi-component guides assessment and bounded plan

**PF-PROPOSAL-02: assessment and three-stage test-only experiment complete; no promotion.**
Reviewed 2026-10-23. [Research provenance](RESEARCH_REFERENCES.md#inf-18).
Execution follows the separately [frozen protocol](MULTICOMPONENT_GUIDE_PROTOCOL.md);
the proposed gates below are preserved rationale, superseded by that protocol.
The [result](MULTICOMPONENT_GUIDE_STATUS.md) preserves branch-guide equal-N gains,
failed cost-matched utility and shared-pilot refusals; neither candidate ships.
The released stopping estimator, inference defaults and 7.4.0 adoption baseline
remain unchanged.

## Recommendation

Compare two small, explicitly configured Gaussian-mixture guides for scalar
fixed-budget filtering:

1. **Multiple local linearizations:** the caller supplies a bounded inventory of
   linearization points representing plausible observation branches. Existing
   Gaussian conditioning constructs a component for each point and parent.
2. **A shared weighted-pilot fit:** draw a separate, discarded predictive pilot
   once per observation, fit with the existing `GaussianMixtureProposal.fitWeighted`,
   and freeze that guide before production. This is a comparator, not an automatic
   recovery path when the first method fails.

Both retain an explicitly chosen positive prior fraction and the full proposal
density in the importance correction. Neither promises to discover every mode.
Start with guided filtering only; defer auxiliary-score optimization, learned
proposals, automatic mode finding and vector-state expansion.

This recommendation is an engineering inference from the literature and the
[completed Gaussian-guide experiment](NONLINEAR_PROPOSAL_STATUS.md), not a published
algorithm copied wholesale. That experiment passed correctness but failed utility.
Multiple components address its single-Gaussian shape limitation; shared preparation
addresses repeated construction cost. Those are separate hypotheses to test.

The strongest local counterevidence is that the previous manual two-root guide,
although better at preserving modes, still lost to larger bootstrap on the reported
asymmetric fixture's accuracy measure. Mixtures must beat that compute baseline,
not merely the failed single-Gaussian candidates. No speedup is forecast.

## Literature and competing approaches

This is a targeted relevant-section review. Published results below were not
reproduced in this assessment; references do not transfer guarantees to Figaro.

| Source and inspected scope | Consequence for this milestone |
| --- | --- |
| Cappé, Douc, Guillin, Marin and Robert, [Adaptive Importance Sampling in General Mixture Classes](https://arxiv.org/html/0710.4242), 2008, sections 2 and 3.1–3.2 | Weighted mixture adaptation has a principled target-to-proposal KL objective. The paper also exhibits nonglobal attraction and failure regions. Reuse weighted fitting, but do not equate EM convergence or favorable weight diagnostics with mode coverage. Figaro's fixed discarded-pilot fit is not full M-PMC. |
| Zhang, Carpenter, Gelman and Vehtari, [Pathfinder](https://arxiv.org/html/2108.03782v4), 2022 v4, sections II.2, II.4, II.7 and III.3 | Multiple optimization paths yield several local normal approximations. The multimodality discussion explicitly warns that importance resampling cannot recover when all paths are trapped in minor modes. Attractive later for unknown smooth targets, but gradient/optimization/ELBO machinery is materially broader than this helper. Its augmented-index weighting and Pareto smoothing are not the proposed Figaro estimator. |
| Tawn, Moores, Queniat and Roberts, [Annealed leap-point sampler for multimodal target distributions](https://doi.org/10.1007/s11222-026-10988-5), published 17 October 2026, introduction and section 3 overview | Recent support for separating mode exploration from local Gaussian representation. Its complexity discussion excludes mode-location search cost, and local non-Gaussianity remains important. ALPS is an annealed MCMC system, not a drop-in filtering proposal; its dimension-scaling results are not Figaro performance evidence. |
| Li, He and Liao, [Sequential multiple importance sampling for multi-modal Bayesian inference](https://www.sciencedirect.com/science/article/pii/S088832702501489X), 2026, publisher abstract/introduction screen only | Sequential adaptive proposals and sample reuse are relevant alternatives, but add a tempering/adaptation schedule and different accounting. Direct full-text retrieval returned HTTP 403. Defer rather than design from the abstract. |
| Prior [INF-17 assessment](RESEARCH_REFERENCES.md#inf-17): PropMixNN, normalizing-flow proposals and optimized auxiliary filtering | Retain these alternatives. Training, distribution shift, invertible-map accounting or ancestor-weight optimization do not become necessary merely because the first Gaussian guides failed. Their prior inspection limits remain unchanged; no fresh full-text review is claimed here. |

An IMIS article lookup encountered an access challenge; it is not used as a
full-text design source. No additional PDF is required for this bounded decision.
The SeMIS full text would be useful if sequential adaptation/sample reuse becomes
the next architectural choice.

### Existing implementations and reuse

- **pypmc 1.2.2:** [overview](https://pypmc.github.io/introduction.html),
  [PMC guide](https://pypmc.github.io/user_guide.html#pmc) and
  [weighted fitting API](https://pypmc.github.io/api.html) inspected. Repository
  HEAD was `9e0ab49f0efacafd676f0f25301cddfc66f96aac`, dated 2026-04-07,
  non-archived when checked. The responsibility/preparation routines in
  [pmc.pyx](https://github.com/pypmc/pypmc/blob/9e0ab49f0efacafd676f0f25301cddfc66f96aac/pypmc/mix_adapt/pmc.pyx)
  were inspected, not the entire package. It supports weighted Gaussian/t mixture
  updates and configurable pruning. Its [GPL-2.0 license file](https://github.com/pypmc/pypmc/blob/9e0ab49f0efacafd676f0f25301cddfc66f96aac/LICENSE)
  and Python/Cython/SciPy integration make it an optional isolated comparator, not
  a proposed JVM dependency or copied implementation. The initial guessed source
  path failed; repository-tree discovery resolved the actual source path.
- **scikit-learn:** maintained [GaussianMixture API](https://scikit-learn.org/stable/modules/generated/sklearn.mixture.GaussianMixture.html)
  documents regularization, restarts and convergence. The inspected `fit(X,y=None)`
  signature does not provide sample weights, so it is not a direct replacement for
  weighted pilot fitting. Repository metadata reported non-archived/BSD-3-Clause;
  no installation or license compatibility decision is needed because none is reused.
- **Stan:** current [Pathfinder manual](https://mc-stan.org/docs/reference-manual/pathfinder.html)
  supplies a maintained implementation path for the optimization alternative.
  Its diagnostic discussion still requires quantity-specific validation. Adding
  a Stan process/compiler and gradient model interface is outside this milestone.
- **Figaro already provides the needed numerical foundation:**
  [GaussianMixtureProposal](../Figaro/src/main/scala/com/cra/figaro/algorithm/sampling/GaussianMixtureProposal.scala),
  [VectorImportance](../Figaro/src/main/scala/com/cra/figaro/algorithm/sampling/VectorImportance.scala),
  [WeightedParticleProposal](../Figaro/src/main/scala/com/cra/figaro/algorithm/filtering/WeightedParticleProposal.scala)
  and [FixedBudgetParticleFilter](../Figaro/src/main/scala/com/cra/figaro/algorithm/filtering/FixedBudgetParticleFilter.scala).
  The concrete gap is per-observation preparation and a small immutable adapter
  from normalized vector mixtures to filtering laws, not another EM implementation
  or particle-filter engine.

No third-party code, datasets or dependencies were incorporated or executed.
Current `VectorImportance.Mixture.sample` uses ordinary cumulative probabilities;
positive represented weights alone do not prove every tiny selection interval
survives rounding. The proposed test adapter should reuse the existing internal
[log-tree selector](../Figaro/src/main/scala/com/cra/figaro/algorithm/filtering/LogParticleWeights.scala)
for component selection and score the same log-weight mixture. Do not silently
change the released mixture sampler as part of this experiment.

## Proposed mathematical contract

Scope is one real state, one real observation, Gaussian initial/conditional
transition laws and positive additive Gaussian observation variance. The true
likelihood remains `g(y|x) = Normal(y; h(x), R)`. All states and prepared objects
are immutable. General graphs, singular laws, manifolds, non-Gaussian noise and
true mixture transitions are excluded from this first helper's acceptance scope.

### Candidate A multiple local linearizations

For each observation, provide `K` distinct finite anchors `a_j`, their finite
derivatives `H_j = h'(a_j)`, and explicit positive coefficients `rho_j` summing to
one. Propose `1 <= K <= 4`. Anchors come from declared model knowledge, not from
reference posterior modes, production draws or an unbudgeted optimizer. Charge
their preparation, including callback work. They need not be exact roots.

Set `b_j = h(a_j) - H_j*a_j`. Define the *surrogate* observation model

```text
g_tilde(y|x) = sum_j rho_j Normal(y; b_j + H_j*x, R).
```

For a parent-specific prior `p_i(x) = Normal(x; m_i, P_i)`, use the existing
linear-Gaussian helper to obtain

```text
z_ij = Normal(y; b_j + H_j*m_i, R + H_j^2*P_i)
q_ij(x) = p_i(x) Normal(y; b_j + H_j*x, R) / z_ij
alpha_ij = rho_j*z_ij / sum_k rho_k*z_ik
qGuide_i(x) = sum_j alpha_ij*q_ij(x)
q_i(x) = epsilon*p_i(x) + (1-epsilon)*qGuide_i(x).
```

These are exact identities for the surrogate, not an assertion that its branch
weights equal the true posterior mode masses. The surrogate is a mixture of
globally extended affine observation models, not a partition or truncated law.
Overlapping local approximations can misallocate mass. Identical affine branches
reduce to the same analytic Gaussian; zero slopes require no division by `H_j`.
Evaluate predictive terms and mixture sums in logs. Preserve numerical refusals.

The actual production correction uses `log p_i(x) + log g(y|x) - log q_i(x)`,
plus the unchanged filter's prior-weight/ancestor accounting. It never uses
`g_tilde` as the true likelihood. `sum_j rho_j*z_ij` is not an unbiased estimate
of true evidence. Auxiliary use of that score is deferred, not implicitly enabled.

Compute `h(a_j)`, slopes and intercepts once per observation. Parent-specific
conditioning remains real work. Prepare an immutable lookup for the exact current
parent values before `advance`; do not round states or retain a mutable cross-step
cache. Duplicated states may reuse identical laws. Count all preparations, even
for parents subsequently not selected. Exceeding the explicit storage/work cap
is a refusal, not a switch to an uncharged lazy implementation.

### Candidate B shared weighted pilot

At initialization, draw a fixed pilot from `p0`. At later steps, sample independently
from the current empirical predictive mixture `r_t(x) = sum_i w_i*p_i(x)` using
the existing retained log weights. Weight those draws by the true `g(y|x)` and
call `fitWeighted` once. Thus the fitted guide approximates the empirical
*marginal* filtering density, not every parent-conditional posterior.

Discard pilot draws, freeze the returned mixture `qFit_t`, and use
`q_i = epsilon*p_i + (1-epsilon)*qFit_t` for fresh production draws. Conditional
on the past population and pilot, this is an ordinary frozen proposal; it does
not require replacing the true `p_i` in the correction with `r_t`. Independent
pilot and production streams must be explicit and replayable. Never run fitting
inside a proposal sampler or retry until a fit succeeds.

This shared fit controls construction cost, but strong parent-child dependence
can make it much worse than conditional guidance. A pilot that misses a mode
cannot learn that mode. Component ESS in `fitWeighted` is a numerical information
check, not an exploration certificate. Existing refusal statuses, covariance
regularization, inflation and iteration limits remain visible and unchanged.

### Common safety boundaries

Require an explicit `0 < epsilon < 1`; no new default or automatic cutover.
In exact arithmetic `q_i >= epsilon*p_i`, which bounds `p_i/q_i`, not arbitrary
functions, full-path error or missing-mode probability. Finite draws can still
miss a branch, and floating-point/numerical support limits still apply.

Sampling and scoring use the full same normalized mixture, never only the sampled
component. No production adaptation, weight smoothing, clipping, silent component
dropping, hidden jitter or successful-run filtering. Preparation failure leaves
the filter untouched; in-step failure retains the released terminal behavior.
Failure reports distinguish numerical refusal, pilot insufficiency and work limits.

## Approved three stage design

### Stage 1 freeze the specification and independent references

Freeze equations, fixture/anchor provenance, exact resource counters, seed/stream
manifest, selection numerics and acceptance rules before candidate sampling.
Resolve the adapter's log-density/sampler agreement independently, including rare
component selection. Use analytic Gaussian and finite-mixture controls plus scalar
density recursion with resolution/domain checks. Reference integrals must evaluate
the true model, never the candidate local approximations or EM fit. Keep oracle
code and candidate code separate; shared filter code is not independent validation.

Proposed ceilings: four guided components plus prior defense; 512 pilot draws,
50 EM sweeps and 200,000 component-density evaluations per observation; explicit
100,000-value preparation storage ceiling. These are experiment limits to freeze,
not API defaults or evidence of sufficient pilots. All other fitter settings must
be explicit. Limit total execution to 12 serial hours, 6 GiB heap and 5 GiB evidence.
If the manifest cannot fit those limits, revise scope before confirmation, not
after observing which configurations failed.

Use a new protocol identifier and unused seed ranges. Prior experiment seeds may
serve regression replay only. Suggested allocation: 16 development and 256 fresh
confirmation seeds; each confirmation seed includes both its pilot and production
substreams. Three JVM timing replays are not 768 independent replications.

**Exit:** frozen protocol and passing reference self-tests. If robust component
selection or preparation cannot reuse the existing interfaces, stop for design
review rather than expand the runtime API under this approval.

### Stage 2 isolated test-only construction and accounting

Build the two adapters only under test sources. Reuse existing conditioning,
weighted EM, log-tree selection and filtering corrections. Test affine reduction,
known mixture sampling/density, anchor permutation, duplicate/invalid anchors,
overlap, zero slope, extreme component imbalance, underflow, invalid covariance,
budget exhaustion, interruption and callback exceptions. Confirm no pilot draw
enters production summaries and no preparation consumes the production stream.

Test initialization, carry steps and ordinary resampling with both existing
ancestor methods. Compare conditional and shared guides on a known Gaussian
control to expose the shared guide's loss of parent dependence. Include explicit
failed-fit and omitted-branch controls; successful fitting must not assert that
all modes were found. Do not alter released bootstrap/stopping regressions.

**Exit:** deterministic, lifecycle and independent accounting tests pass. A
correct but expensive or inaccurate proposal is not yet promotion-eligible.

### Stage 3 independent utility validation

Freeze a limited configuration choice using development data, then evaluate once
on fresh held-out seeds. Save every attempt, refusal and unavailable downstream
step. Numerical failures are not zero evidence. Changed candidates require a new
protocol and holdout, not favorable-seed retries.

| Fixture group | Required comparison or failure question |
| --- | --- |
| Affine and weak observation | Analytic truth; no invented improvement; preparation overhead and shared-guide inefficiency |
| Symmetric square observation | Can declared two-branch guides retain both modes? Independent symmetry and grid truth |
| Asymmetric square observation with sharp noise | Primary accuracy and mode-mass test, including unequal prior support |
| Scalar three-branch observation, such as a cubic with two turning points | Second primary case: actual generalization beyond a hand-coded two-root recipe; explicit anchors without oracle mode locations |
| Monotone and far-tail observations | Retained failure regimes from the previous experiment; no silent removal because they are inconvenient |
| Merging branches, flat derivatives and deliberately omitted branch | Limits of the local model and discovery claims; classify supported versus expected-refusal inputs before runs |
| Narrow transition with separated parents | Shared marginal guide versus parent-conditional dependence; cost and correction controls |

Compare unchanged bootstrap at equal N and larger cost-matched N, the manual
two-root recipe where applicable, retained single-Gaussian guides, and both new
candidates. Preserve the former benchmark/results unchanged. A three-branch model
may require new independent references; if they cannot converge within the
budget, record the milestone incomplete rather than substitute easier truth.

Count pilot transitions, likelihood/derivative calls, EM sweeps, all component
density calls, conditioning, ancestor selection, lookup preparation/storage and
end-to-end time. Freeze candidate-specific larger-bootstrap budgets from
development timing; report actual confirmation timing and work, not assumed
equivalence. Also report matched model-evaluation cost to expose hardware effects.
Rotate execution order and use warmed repeated JVMs. No parallel hardware advantage
may be attributed to an algorithm without an equivalent resource comparison.

Proposed promotion gates, finalized before execution:

- All deterministic/numerical and independently computed unnormalized evidence,
  first/second moment and event-mass checks pass, with predeclared multiplicity
  handling and numerical-reference error allowance. Log evidence and normalized
  moments are not claimed unbiased. Unexpected supported-domain refusal blocks
  that candidate; report successful-run accuracy only as a secondary diagnostic.
- At least 10% lower predeclared root loss than cost-matched bootstrap on **both**
  designated primary multimodal fixtures, with paired uncertainty intervals
  supporting improvement. Include mean, second moment and every declared mode's
  probability in the loss. ESS is secondary. Candidates qualify separately.
- No material mode-loss regression: propose at most three additional depleted
  runs per 256 compared with bootstrap, and report uncertainty for that difference.
  Freeze a depletion definition relative to independently known region mass;
  rare modes need their own probability-error checks rather than a central-mode
  threshold. The manual recipe is an additional quality/cost comparator.
- Weak/affine aggregate loss no worse than 1.25 times equal-N bootstrap; no hidden
  worsening of declared tail/event metrics. Freeze their tolerances separately.
  Actual median time must be at most 1.25 times the cost-matched control in every
  timing replay. Resource ceilings are hard bounds, not success criteria.
- Passing these gates permits a promotion **review**, not production integration.
  API/numerical review, relevant full CI and packaged-consumer acceptance are
  required before any separately approved promotion. No release/default/stopping
  change follows automatically. Auxiliary capability needs its own later evidence.

Preserve scripts, input hashes, seeds, versions, real logs/errors, machine-readable
all-outcome results and plots in a verified evidence archive. No new trajectories
or numerical performance measurements were generated for this assessment.

## Decision and remaining limits

The approved three test-only stages above evaluate the explicit-anchor contract.
It reduces manual proposal algebra without pretending to infer unknown branches.
The shared-pilot comparator tests whether less model-specific input is worth its
exploration cost. If neither beats larger bootstrap, retain that negative result
and reassess rather than automatically add component counts, optimizers or training.

What would change this recommendation: demonstrated need for unknown-mode discovery,
representative high-dimensional geometry, or evidence that affordable pilots cannot
cover relevant regions. Those would favor a separately reviewed optimization,
tempering or transport approach. None supplies universal missing-mode detection.

Related: [mixture fitting](MIXTURE_PROPOSALS.md),
[guided fixed-budget filtering](GUIDED_FIXED_BUDGET_FILTER.md),
[prior negative result](NONLINEAR_PROPOSAL_STATUS.md), [roadmap](../ROADMAP.md).
