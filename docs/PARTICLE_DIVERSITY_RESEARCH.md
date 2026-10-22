# Particle-diversity robustness: assessment and bounded plan

Status: **assessment complete; three-stage experiment approved and in progress**.
Assessed 2026-10-21 against the published 7.2.0 baseline. No new sampler,
runtime default, scientific test result or release is introduced here.
[Research provenance](RESEARCH_REFERENCES.md#inf-14).

## Recommendation

Investigate **bounded resample-move rejuvenation**, beginning with a fixed-budget,
test-only implementation for immutable, density-evaluable scalar states. First
separate where diversity is lost and compare the candidate against the simpler
alternative: spending the same resources on more particles. Do not insert MCMC
steps or correlated resampling into the released random-stopping loop.

The outcome may be a useful opt-in method, a narrower recommendation to raise
the work floor, or a negative result. A new API is not the acceptance criterion.
The three stages below are approved. The [frozen protocol](PARTICLE_DIVERSITY_PROTOCOL.md)
defines experiment settings and gates before new outcomes are inspected.

## What problem remains after 7.2.0?

These are different failure mechanisms and should not share one warning:

| Mechanism | What helps / what does not establish a solution |
| --- | --- |
| Numerical loss of positive weights | The shipped log-weight/tree repair preserves representable log masses; it cannot create an unsampled state. |
| Weight concentration | Better proposals, auxiliary look-ahead and more work may help. Positive-weight count alone says little about accuracy. |
| Resampling impoverishment | Repeated ancestor selection reduces represented lineages. Lower-variance resampling or valid moves may help, but equal weights after resampling do not imply independent information. |
| Poor movement between modes | A locally mixing move can make every particle different while leaving all of them in the wrong region. Global proposals need correct densities and adequate support. |
| Lost historical hypotheses | Current-state moves do not reconstruct discarded paths or infer absent discrete labels with zero transition support. A smoothing/path method is a separate capability. |

The [unchanged 7.2 benchmark](CONTINUOUS_FILTER_BENCHMARKS.md#remaining-mode-risk-and-cost)
completed all 7,680 runs and passed 450 statistical checks after numerical repair.
In its persistent-mode fixture, final sign-mass depletion occurred in **12/256**
calibrated-auxiliary runs at ceiling 256, versus **0/256** fixed-work auxiliary
runs. At ceiling 1024, all five tested methods had zero observed depleted runs.
Zero observed failures is not zero failure probability. These are previously
recorded results, not experiments rerun for this assessment.

The calibrated method used fewer production draws even after including its pilots;
the benchmark therefore does not isolate an intrinsic defect in calibration or
prove that resample-move is better. Increasing the work floor is the strongest
existing counterproposal. The persistent fixture has Gaussian transitions with
positive support, not a hard sign-preserving transition: losing a sign is not
mathematical impossibility of returning, but returning may be practically unlikely.

## Literature and implementation assessment

This is a targeted method-selection review, not an exhaustive survey or a proof
audit. The following access depths describe what was inspected, not merely found
in search results. Dates are publication/preprint versions, not search crawl dates.

| Primary source | Inspected material and implication for this milestone |
| --- | --- |
| Doucet and Johansen, *A Tutorial on Particle Filtering and Smoothing: Fifteen Years Later* ([author PDF](https://www.stats.ox.ac.uk/~doucet/doucet_johansen_tutorialPF.pdf), version 1.1, December 2008; chapter published 2011) | Sections 4.4-4.5, invariant-kernel definition and resample-move/block-sampling discussion. Rejuvenation is a target-preserving operation, not arbitrary jitter. Current-state and path-space targets must be distinguished. Moves mitigate consequences of resampling but do not by themselves prevent concentrated importance weights. |
| Gerber, Chopin and Whiteley, *Negative association, ordering and convergence of resampling methods* (2019; [arXiv v3](https://arxiv.org/pdf/1707.01845v3), 2020) | Sections 3.2-3.4, Proposition 1 and stratified/residual comparisons. Stratification is a justified fixed-population comparator. Systematic resampling has ordering-related counterexamples; it is not an unqualified best default. These results do not validate substituting dependent ancestors in Figaro's stopped IID construction. |
| Sherlock, Golightly and Lee, *Robust, partially alive particle Metropolis-Hastings via The Frankenfilter* ([v2](https://arxiv.org/html/2601.23173v2), June 2026) | General one-step Algorithm 3, Proposition 1's exchangeability argument and section 2.2. This is the stopping-estimator compatibility reference already mapped in INF-09/11, not a diversity-recovery method. |
| Klaas, de Freitas and Doucet, *Toward Practical N² Monte Carlo: the Marginal Particle Filter* ([author PDF](https://www.cs.ubc.ca/~arnaud/klass_defreitas_doucet_marginalparticlefilterUAI2005.pdf), 2005) | Section 3, full predictive-mixture formulation and section 3.2's cost/variance tradeoff. Useful basis for an explicit current-state target; direct mixture evaluation is quadratic per population step. The proposed MCMC experiment is not the paper's marginal importance sampler or its fast approximation. |
| Dau and Chopin, *Waste-free Sequential Monte Carlo* (2022; [preprint](https://arxiv.org/pdf/2011.02328)) | Sections 2.3-2.5, Algorithm 2 and Proposition 2. Retains intermediate MCMC states using a specific extended-state SMC construction. Attractive later, but its correlated particles and normalizer argument cannot be imported merely by retaining Figaro move histories. |
| Le Fay, Chopin and Vihola, *On the complexity of standard and waste-free SMC samplers* ([v2](https://arxiv.org/html/2604.03352v2), April 2026) | Section 6's assumptions/complexity summaries and end-user discussion following it. Recommendations differ for moments versus normalizers; the multimodal example cautions against too few locally mixing chains. Bounds depend on mixing and target assumptions, not an automatic guarantee for Figaro's nonlinear filters. |
| Corenflos and Finke, *Particle-MALA and Particle-mGRAD* ([v1](https://arxiv.org/html/2401.14868v1), 2024) | Abstract and section 2.1's CSMC algorithm/invariance and dimension discussion; gradient variants screened from the abstract, not their full proofs. Promising for trajectory inference, but gradients, path storage and a different inference contract put these outside this first online-filter milestone. |

Historical originating reference: Gilks and Berzuini (2001),
[*Following a moving target*](https://doi.org/10.1111/1467-9868.00280).
The publisher retrieval timed out; its full text was **not** reviewed. The accessible
Doucet-Johansen treatment supplies the inspected resample-move explanation. Two
tutorial mirrors also timed out before the author PDF above succeeded. No paywalled
PDF is presently necessary to approve the bounded experiment; this is not a claim
to have audited all originating proofs.

### Existing software rather than a new framework

Inspected `particles` at commit
[`3388c4bd30ab9acc5a27c970089230f93610b627`](https://github.com/nchopin/particles/tree/3388c4bd30ab9acc5a27c970089230f93610b627):
[`smc_samplers.py`](https://github.com/nchopin/particles/blob/3388c4bd30ab9acc5a27c970089230f93610b627/particles/smc_samplers.py),
specifically `ArrayMetropolis`, Gaussian random-walk/independent proposals,
`MCMCSequenceWF`, `AdaptiveMCMCSequence`, and their SMC-sampler configuration.
Its [MIT license](https://github.com/nchopin/particles/blob/3388c4bd30ab9acc5a27c970089230f93610b627/LICENSE)
was inspected. It provides a useful independent reference and already implements
standard/waste-free move scheduling; it was not installed or executed here.

Reusing this Python framework as the JVM runtime would not directly meet Figaro's
owned-RNG, immutable-state, retained-log-weight and bounded-stopping contracts.
That is the concrete integration gap, not a reason to reinvent generic SMC.
Reuse Figaro's existing laws and log arithmetic for a narrow experiment; use exact
controls as primary validation and the pinned external implementation only where
targets/schedules actually match. No external code or data was copied and no new
runtime dependency is proposed.

## Options and decision

| Option | Decision / tradeoff |
| --- | --- |
| Larger floor or fixed budget, existing guided/APF proposals | Mandatory baseline; already available and may be the best accuracy-per-cost choice. |
| Stratified resampling | Test-only fixed-budget comparator to isolate resampling variance. It selects existing states, not new modes. No replacement inside random stopping. |
| Corrected resample-move | First new candidate: explicit target, fixed number of attempted moves, local and frozen global proposals. Cost and mixing must earn adoption. |
| Uncorrected roughening/jitter | Reject as a generic solution: it can alter the modeled posterior. Any regularized approximation would need its own error contract. |
| Likelihood tempering / bridging | Defer: additional targets and evidence accounting. Reconsider if experiments show severe incremental weight collapse rather than duplicated populations is dominant. |
| Waste-free SMC, particle Gibbs/MALA, fixed-lag smoothing | Defer: different dependence, path and/or gradient requirements. Valuable separate milestones, not additions to the existing loop by analogy. |
| Independent islands / ensembles | Useful diagnostic comparator later, not guaranteed mode discovery; dividing a small total budget can make each population less robust. |

## Target and estimator boundary

For the initial scalar experiment, freeze the previous weighted population
`(x_i, W_i)` and observation before drawing this step. With transition density
`f(x | x_i)` and likelihood `g(y | x)`, define the unnormalized target

```text
gamma_N(x) = g(y | x) * sum_i W_i * f(x | x_i)
log alpha(x,z) = min(0, log gamma_N(z) - log gamma_N(x)
                       + log q(x | z) - log q(z | x))
```

At initialization use prior density times likelihood. This is the current-state
posterior induced by the **empirical previous population**, not the exact filtering
posterior or a distribution over reconstructed histories. Evaluate the entire
predictive mixture with retained log weights. A fixed-parent conditional move has
a different target and must not be advertised as the same algorithm.

This target is an explicit research design choice. It avoids requiring arbitrary
graph densities or storing an unbounded path, but a target evaluation needs O(N)
transition densities. Moving N particles for K sweeps can cost O(K*N²). Retain only
O(N) state/target caches rather than an obligatory N-by-N matrix. Establish whether
this cost is worthwhile at scalar budgets before considering vector generalization
or fast mixture approximations.

A global proposal must have an evaluable **full** density and reach target support;
a mixture uses its entire density, not just the selected component. Use existing
Gaussian/mixture laws and log-ratio infrastructure. Freeze its parameters before
production (predeclared or independently piloted); do not refit from each moved
population. Local Gaussian MH is a control, not a promised multimodal remedy.

The existing `WeightedBoundedParticleFilter.update` draws conditionally independent
parent/child candidates from frozen proposals. Its retained population and evidence
denominator depend on the floor/cap/score stopping branch. See
[INF-09](RESEARCH_REFERENCES.md#inf-09), [INF-11](RESEARCH_REFERENCES.md#inf-11)
and [INF-13](RESEARCH_REFERENCES.md#inf-13).

**Do not interleave moves or stratified offspring with those draws.** First study a
separate fixed-budget harness, recording the importance increment before optional
resampling/moves. An invariant kernel alone is not a proof for the complete
multi-step estimator: derive the conditional unnormalized-measure identity,
including all population/proposal dependencies. Require exact finite controls
before claiming evidence preservation. Post-stop moves might be supportable later;
their validity has not been established by this review. No changed evidence API
or pseudo-marginal compatibility claim is proposed now.

## Bounded implementation proposal

### Stage 1 — diagnosis and frozen acceptance protocol

Test/harness work only. Preserve the released five-method benchmark and its seeds,
fixtures and 450 checks unchanged. Add a separate instrumented experiment, checking
that instrumentation does not consume RNG draws or alter outcomes.

Record pre-resampling weight ESS, parent selection counts/concentration, occupied
known regions, mode-mass error against independent references, and per-step cost.
For frozen weighted parents, `sum_i (1-(1-W_i)^N)` gives expected distinct parents
under N independent multinomial draws; test this directly on finite populations.
It is not an expected number of posterior modes. Distinct current states, ancestral
lineages and weight ESS must remain separate fields. After a marginal-state move,
old ancestor IDs describe pre-move provenance, not a valid sampled historical path.

Compare larger fixed budgets and larger calibrated floors before introducing a new
method. Do not infer a universal warning threshold or automatically switch methods
from ESS alone. This stage should identify whether selection, inadequate work or
poor state proposals dominates the designated fixtures.

### Stage 2 — small fixed-budget resample-move experiment

Scope: finite-state exact controls and scalar continuous fixtures, fixed population
sizes, explicit prior/transition/likelihood densities, owned reproducible RNG.
No arbitrary Elements, new runtime framework or public API yet.
Cap the initial quadratic-cost candidate at **256 particles**; larger particle
counts remain available to the existing linear-cost baselines. Limit each candidate
step to **2,000,000 transition-density evaluations**, including target-mixture
work. Stage 1 must budget the entire study before execution; exceeding its recorded
resource allowance requires a revised plan, not silently reducing held-out coverage.

- Compare no extra resampling, resampling-only, and resampling plus moves, so the
  noise/cost of the extra resampling step is visible. Reuse the log-mass multinomial
  baseline; add stratified resampling as a separate fixed-budget control only.
- Compare local MH with a frozen independence/global mixture proposal. Predeclare
  move counts **0, 1, 4 and 8 attempted sweeps**, including rejected moves; do not
  stop after a chosen number of acceptances. These are experiment settings, not
  recommended defaults. Keep all chains' final states; no waste-free reuse yet.
- Budget transition/likelihood/proposal evaluations, pilot work, full-mixture
  evaluations and moves separately. Choose affordable complete sweeps before the
  step. No unbounded retries, convergence loop or hidden extra recovery work.
  A callback-call ceiling is not a hard real-time deadline for arbitrary user code.
- Start with the existing three scalar fixtures and add a long-horizon/late-
  disambiguation fixture plus a deliberately missing-support control. Freeze new
  data conventions, references and seeds before evaluating candidates. Missing
  support must remain an explicit limitation, not be concealed by injected modes.

### Stage 3 — evidence-led integration decision

Only recommend an opt-in runtime facility if Stage 2 passes correctness **and**
matched-cost utility gates. Then specify the smallest immutable-state interface,
failure/lifecycle contract, user examples and migration-free defaults. Broader
vector models and combination with random stopping need their own derivation and
validation; neither is automatically included in approving the scalar experiment.

If larger floors/proposals dominate, document that result and stop feature work.
If moves improve diversity but not error, do not promote on acceptance rate,
distinct-state count or ESS alone. Release eligibility is reviewed only after a
consumer-facing implementation exists; this documentation does not change 7.2.0.

## Acceptance gates to freeze before implementation experiments

These are **proposed requirements**, not passed checks. Stage 1 must commit the
exact fixture/seed manifest, numerical tolerances, uncertainty procedure and cost
budgets before Stage 2 results are inspected. Do not tune them on validation seeds.

| Gate | Required evidence |
| --- | --- |
| Target correctness | Independent finite enumeration of MH transition matrices: row sums, nonnegativity, detailed balance/invariance, asymmetric proposals, full mixtures and true-zero support. Derive and check one-/two-step unnormalized expectations, not just stationary moments. |
| Numerical/lifecycle contract | Log-ratio extremes and tiny retained weights, NaN/overflow refusal, genuine zero viability, invalid callbacks, budget exhaustion, interruption, deterministic replay and owner-thread checks. No floors, selective retries or discarded failing seeds. |
| Independent accuracy controls | Exact finite filters, analytic linear-Gaussian controls, independently converged nonlinear grids, and known multimodal mass. Report means, variances, mode probabilities and likelihood-estimator error separately from log-likelihood error. |
| Frozen regression | All original 7.2 benchmark checks remain, with all outcomes included. New experimental results do not replace or loosen the existing baseline. |
| Useful improvement | Proposed promotion threshold: at least 20% lower mode-mass RMSE on the designated persistent-mode and late-disambiguation fixtures at matched total evaluation budget, with a predeclared 95% interval for the RMSE ratio entirely below 1. Depletion rates and their uncertainty must not worsen. This is an engineering target, not a literature theorem. |
| Cost honesty | Compare to the best preselected larger-floor/fixed-budget baseline, not only to the smallest calibrated population. Report full evaluation counts and warmed wall-clock accuracy/cost curves over three fresh JVMs. No production promotion if gains disappear at matched elapsed time; record memory and all pilot/setup costs. |
| Generalization / regressions | Use disjoint calibration and held-out seeds/fixtures; predeclare family-wise handling for comparisons. Gaussian and ordinary-growth errors must stay within independently set regression tolerances. Publish negative results, zero-event uncertainty and unsupported cases. |
| Runtime promotion, if warranted | Focused/full CI, compatibility and packaged-consumer checks, complete API/usage documentation, maintained research mappings and explicit approval before publication. None executed for this assessment. |

The 20% utility threshold and 95% comparison are proposed now, before implementation;
the exact statistical procedure and regression tolerances remain a Stage 1 deliverable.
No universal mode discovery, guaranteed precision, automatic optimal budget or
generic graph thread safety is part of this plan.

## Related code and documents

- [Weighted filter](../Figaro/src/main/scala/com/cra/figaro/algorithm/filtering/WeightedBoundedParticleFilter.scala),
  [proposal laws](../Figaro/src/main/scala/com/cra/figaro/algorithm/filtering/WeightedParticleProposal.scala),
  [log-weight tree](../Figaro/src/main/scala/com/cra/figaro/algorithm/filtering/LogParticleWeights.scala): shipped baseline, not rejuvenation implementations.
- [Frozen continuous harness](../Figaro/src/test/scala/com/cra/figaro/test/modernization/ContinuousFilterBenchmark.scala)
  and [independent reference tool](../tools/continuous_filter_reference.py): retain as controls.
- [Auxiliary filtering](AUXILIARY_PARTICLE_FILTER.md), [calibration](CALIBRATED_GUIDED_FILTER.md),
  [roadmap](../ROADMAP.md) and [wishlist](../WISHLIST.md).
