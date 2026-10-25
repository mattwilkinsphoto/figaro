# Figaro roadmap

This is the current delivery and maintenance plan. [WISHLIST.md](WISHLIST.md)
contains optional candidates; [roadmap history](docs/ROADMAP_HISTORY.md) preserves
the completed milestones and their original evidence. Historical priorities are
not an active queue.

## Current baseline: use 7.4.0

**Figaro 7.4.0 is the adoption baseline and is published on Maven Central.**
The Java 17 / Scala 3 / sbt 2 modernization and approved core performance,
distribution and modeling milestones are integrated. See the
[release scope](docs/INFERENCE_INTEGRATION.md), [installation and publication](docs/MAVEN_CENTRAL.md),
[distribution inventory](docs/DISTRIBUTION_SUPPORT.md) and
[migration limitations](docs/MIGRATION.md).

The [6.1 release record](docs/RELEASE_6_1.md) preserves the preceding baseline's
scope and acceptance evidence; it is history, not the current dependency version.

The current phase is **application-driven maintenance**, not another scheduled
modernization campaign. No remaining modernization milestone is a known adoption
blocker within the documented scope. This is not a guarantee of correctness,
precision or performance for every application model.

## Active priorities

**Build-tool maintenance closed:** stock sbt 2.0.11 is adopted; post-merge CI and
packaged consumers passed. The local workaround is retired and a brief upstream
validation confirmation was posted. See the [closeout](docs/CI_VALIDATION.md#stock-sbt-2011-adoption).
No further sbt repair or Figaro release is scheduled from that work.

### Recommended next capability assessment

**PF-PROPOSAL-01: reusable nonlinear observation-informed proposals.** The
[literature-led assessment and bounded plan](docs/NONLINEAR_PROPOSAL_RESEARCH.md)
are complete, followed by the approved [three-stage test-only experiment](docs/NONLINEAR_PROPOSAL_STATUS.md).
**The experiment is complete with no promotion:** 984 independent correctness
checks passed with identical scientific replay across three JVMs, but all candidates
failed the accuracy/cost gates; the Jacobian guide also failed an asymmetric-mode
depletion margin. Keep the cubature/Jacobian builders test-only. No new API or
release follows from this work.
The 7.4.0 APIs already correct caller-supplied guides, but built-in observation
conditioning remains linear-Gaussian. See [research provenance](docs/RESEARCH_REFERENCES.md#inf-17).

**PF-PROPOSAL-02: multi-component experiment complete; no promotion.** The
[literature-led assessment and bounded plan](docs/MULTICOMPONENT_GUIDE_RESEARCH.md)
recommend caller-supplied scalar linearization branches versus one shared,
discarded-pilot mixture fit per observation. The approved three stages freeze
independent references first, build test-only adapters second, and require fresh
held-out accuracy/mode-retention gains over cost-matched bootstrap third.
Independent references, adapter controls and branch-guide correctness passed.
The branch guide improved equal-N accuracy/mode retention, but failed both primary
cost-matched utility gates and an omitted-branch probability-error gate. The shared
pilot had 1,788 refusals in 2,816 held-out runs. See the
[complete status](docs/MULTICOMPONENT_GUIDE_STATUS.md); both remain test-only.
No optimizer, learned guide or automatic mode discovery is implied.
[INF-18](docs/RESEARCH_REFERENCES.md#inf-18) records sources,
reuse limits and counterevidence. Released stopping, defaults and automatic-cutover
behavior remain unchanged. The frozen single-Gaussian protocol and all unfavorable
results remain available for comparison; independent validation precedes promotion.

**PF-PROPOSAL-03: posterior qualification and precomputation complete; stop this workload.**
The [initial assessment](docs/EXPENSIVE_LIKELIHOOD_RESEARCH.md) identified costly
scalar ODE responses and inverse branches. The independently validated
[follow-up](docs/BRANCH_QUALIFICATION_STATUS.md) finds no step satisfying the
frozen substantial-branch criterion, while selected spline tables pass held-out
and posterior checks. Reuse amortizes construction after roughly 4,600–10,400
queries; cold short runs still favor direct evaluation. No filter speedup was
measured. Do not start a guide utility trial for this frozen model or retune it
to pass. Keep the shared pilot parked and previous no-promotion results unchanged.
No runtime integration, promotion, release, stopping or default change follows.

**DIST-02: higher-dimensional GVM fitting experiment complete; no promotion.**
The [literature-led assessment and bounded plan](docs/GVM_HIGH_DIMENSIONAL_FITTING_RESEARCH.md)
recommend two or three linear coordinates plus one angle, full covariance and
quadratic cross terms. The completed sequence qualified numerical references
and genuinely wrapped-likelihood GMM controls, built a bounded test-only fitter,
and ran a [frozen held-out accuracy/cost comparison](docs/GVM_HIGH_DIMENSIONAL_PROTOCOL.md).
The [complete 768-fit study](docs/GVM_HIGH_DIMENSIONAL_STATUS.md) passed independent
numerical checks and retained-model accuracy margins, but failed fit-completion
gates: all three-component GVM fits exhausted their phase-work cap, and Gaussian
controls also frequently hit iteration limits. Keep all implementations test-only.
The approved [bounded analytic-gradient follow-up](docs/GVM_GRADIENT_STUDY.md)
is [complete with no promotion](docs/GVM_GRADIENT_STATUS.md): 512 fits and independent
numerical checks show much less phase work and better completion, but per-cell
reliability still fails. Final-responsibility checks distinguish likelihood
stabilization from whole-fit stationarity. The approved [whole-fit convergence and
complexity study](docs/GVM_WHOLEFIT_STATUS.md) is also complete with no promotion:
560 fits and independent numerical checks pass score margins, but reliability and
the required completion gain fail. Acceleration helps K=2; separated true K=3 is
much more tractable than overlapping or overfitted cases. Retained stationarity
and completion of the restart search differ materially. The
[weight/restart literature assessment and bounded plan](docs/GVM_WEIGHT_RESTART_RESEARCH.md)
is complete: conditional convex weight optimization, resumable work-balanced starts,
then a fresh factorial comparison. The [completed 1,040-fit study](docs/GVM_WEIGHT_STATUS.md)
has no promotion: independent numerics and all score margins pass, but combined
multicomponent completion falls to 62/160 versus 70/160 baseline. Balancing alone
does not improve completion; weight polishing shifts rather than resolves the
full-fit bottleneck. Hard restart elimination is deferred. Earlier gates/results remain
intact. See [GVM-08](docs/RESEARCH_REFERENCES.md#gvm-08).
The [completed joint-optimization feasibility screen](docs/GVM_JOINT_STATUS.md)
passes its development gates: 18/20 completions versus 10/20 baseline, no lost
baseline successes and independently checked score/numerical margins. Two curved
cases remain unqualified, and fewer data passes do not establish a runtime speedup.
Recommend a separately frozen fresh-confirmation plan, not public integration or
relaxation of earlier gates.
Scalar fitting, stopping and
defaults remain unchanged. No public integration or superiority claim precedes
independent validation. See [GVM-05](docs/RESEARCH_REFERENCES.md#gvm-05).
Broader multi-latent warm-up/proposal support remains another candidate.
These are recommendations, not an automatic implementation queue. Higher-dimensional
GVM fitting remains density representation work, excluding application fusion.
Finite-population mode loss, arbitrary-graph ownership and unsupported numerical
ranges remain separate limitations; proposal helpers do not solve them universally.

### Released and completed milestones

**Guided/auxiliary fixed-budget extension: published and verified in 7.4.0.** The
[three-stage milestone](docs/GUIDED_FIXED_BUDGET_STATUS.md) combines explicit
resampling schedules with existing proposal interfaces under a
[new frozen protocol](docs/GUIDED_FIXED_BUDGET_PROTOCOL.md). It preserves released
stopping/defaults. Independent correctness, total-cost utility, packaged consumers
and remote integration CI passed; closeout and post-merge main CI passed at `a31fca1a`.
The [final release gates](docs/RELEASE_7_4_STATUS.md) passed: final-source packaging,
compatibility through 7.3.0, signed publication, four artifact byte comparisons
and all six Central-only consumers. The record retains one unresolved, pre-existing
advisory timing failure; it is not reported as passed. Poor proposals can still
hurt accuracy and cost efficiency. No further release is needed solely for
post-publication documentation housekeeping.

**Particle-diversity milestone complete: no runtime promotion.** The
[literature-led three-stage experiment](docs/PARTICLE_DIVERSITY_STATUS.md) preserved
the released stopping estimator and passed correctness/regression screens, but
failed its accuracy/diversity utility gates. Test-only diagnostics, controls and
the negative result are retained. No new sampler or release is recommended.
Larger floors and existing validated proposals remain model-dependent options;
further resample-move scheduling research needs a separate protocol. Finite-population
modal depletion remains a limitation, not a newly repaired numerical defect.

**Resampling-schedule follow-up: research gates passed.** The separately frozen
[single-selection/ESS schedule experiment](docs/RESAMPLING_SCHEDULE_STATUS.md)
supports reviewing a bounded opt-in fixed-budget facility. It does not reverse
the earlier experiment's result, alter the released stopping estimator, establish
a universal ESS threshold, or itself ship a new runtime API. The separately approved
production integration has now passed all acceptance gates. The
[published fixed-budget API](docs/FIXED_BUDGET_FILTER.md) now implements explicit
policy/method selection without changing random stopping. See
[decision-focused repair and final validation](docs/STRATIFIED_INTEGRATION.md):
the former excessive CDF refusals are resolved on the frozen controls, with explicit
domain, precision and refinement-population limits retained. The
[7.3.0 minor release](docs/RELEASE_7_3_STATUS.md) is published and verified, including
all five Central-only consumers. No default threshold or automatic cutover is introduced.

A subsequent **capability-expansion milestone is released in 7.1.0**:
[scope and execution checklist](docs/CAPABILITY_EXPANSION_STATUS.md),
[API guide](docs/CAPABILITY_EXPANSION.md). It expands bounded observation, count,
proposal, numerical and particle-recovery support; it does not remove all limits.
The approved recovery workflow now has an opt-in
[bounded-adaptive indicator filter](docs/BOUNDED_PARTICLE_FILTER.md), plus
fixed-budget health reports and recommendations. Automatic general-likelihood
recovery and arbitrary graph support are not part of this bounded contract.

The [7.1.0 readiness and release](docs/RELEASE_7_1_STATUS.md) is complete.
The independently piloted [budget-selection adviser](docs/PARTICLE_BUDGET_ADVISER.md)
passed its local and remote gates separately in 7.2.0-SNAPSHOT, as did
[likelihood-weighted bounded bootstrap filtering](docs/WEIGHTED_BOUNDED_FILTER.md)
with its [acceptance gates](docs/WEIGHTED_FILTER_STATUS.md). The approved
[split-pilot score/work calibration and observation-informed proposal extension](docs/CALIBRATED_GUIDED_FILTER.md)
also passed its [local and remote gates](docs/CALIBRATED_GUIDED_STATUS.md). These are
now published in 7.2.0. None is in the immutable
7.1.0 artifacts. The approved [auxiliary ancestor selection and Gaussian guidance
milestone](docs/AUXILIARY_PARTICLE_FILTER.md) is implemented in 7.2.0-SNAPSHOT;
its [acceptance record](docs/AUXILIARY_FILTER_STATUS.md) preserves the implementation
history. All [7.2 release gates](docs/RELEASE_7_2_STATUS.md) passed. Arbitrary-graph adaptation, automatic
nonlinear proposal fitting and posterior-precision selection remain deferred.

Before publishing 7.2.0, the approved [continuous nonlinear and multimodal
benchmark gate](docs/CONTINUOUS_FILTER_BENCHMARKS.md) extends the earlier finite
controls. It compares all outcomes against independent deterministic references;
publication readiness depends on the recorded results, not just passing unit tests.
That gate exposed systematic numerical refusals and calibration-related modal
depletion. The local log-domain/tree repair now completes all 7,680 unchanged
benchmark runs and passes all 450 statistical checks. Finite-population mode loss
remains, especially under reduced-work calibration. Final-source CI, reproducible
packaging, signed publication and all four Central-only consumers subsequently
passed. **7.2.0 is published**; neither numerical success nor a score target
establishes universal posterior precision.

Approved distribution/inference integration is complete and released in **7.0.0**.
Its five stages cover stable filtering/MH ratios,
owned multi-chain observations, exact finite counts, support-aware proposals and
selective numerical ranges. See the [user/migration guide](docs/INFERENCE_INTEGRATION.md)
and [requirements and executed gates](docs/INFERENCE_INTEGRATION_STATUS.md).
The major version reflects the approved strict particle-failure default and
filtering extension-hook migration. Clean reproducibility, packaged consumers,
main CI and Central-only consumer checks passed. Broader numerical/graph support
remains explicitly bounded; this does not activate a new universal-support milestone.

1. **Validate real application models.** Pin the released dependency, establish
   known-answer or simulated-data controls, assess inference health, and measure
   accuracy, latency and resource use. Application models and data belong in their
   own projects, not in Figaro's documentation or core library.
2. **Maintain the released library.** Prioritize reproducible correctness defects,
   security issues and compatibility problems. Keep CI, dependency review, release
   credentials, signing-key recovery and consumer documentation healthy.
3. **Use evidence to reopen development.** Promote a backlog item only when a
   concrete model needs it or profiling identifies a consequential bottleneck.
   Start new method/tool choices with primary-literature and maintained-tool
   assessment; then validate the selected approach locally.

There is no automatic commitment to another feature release. Documentation-only
cleanup does not change the library version or replace published artifacts.
Runtime fixes or features require an appropriate new version and release gates.
The [consumer-impact release policy](CONTRIBUTING.md#consumer-impact-release-policy)
requires a cumulative release-eligibility assessment and maintainer notification
when warranted at each merge to `main`, not after a fixed number of commits or on
a recurring schedule. Eligibility does not itself authorize publication.

## Completed work that previously appeared as “next”

| Former priority | Delivered outcome / authoritative guide |
| --- | --- |
| Better proposals for concentrated or multimodal posteriors | [Frozen vector proposals](docs/VECTOR_IMPORTANCE.md), [bounded mixture fitting](docs/MIXTURE_PROPOSALS.md), [owned graph integration](docs/GRAPH_PROPOSALS.md) and [joint blocks](docs/JOINT_PROPOSALS.md) |
| Coverage assessment and rare-event efficiency | [6,600-trial calibration](docs/IMPORTANCE_CALIBRATION.md), [query-aware proposals](docs/RARE_EVENT_PROPOSALS.md), [weighted event mixtures](docs/WEIGHTED_RARE_EVENT_MIXTURES.md) and [end-to-end graph cost](docs/GRAPH_COST_ACCEPTANCE.md) |
| Assumption-bounded stopping and concurrency | [Bounded IID precision](docs/BOUNDED_IID_RELIABILITY.md), [variance-adaptive confidence sequences](docs/ADAPTIVE_BOUNDED_PRECISION.md), [restricted static execution](docs/STATIC_GRAPH_EXECUTION.md) and [static proposals](docs/STATIC_GRAPH_PROPOSALS.md) |
| Common families and modeling constructions | [Nine scalar/count representatives](docs/COMMON_DISTRIBUTIONS.md), [legacy numerical contracts](docs/LEGACY_DISTRIBUTION_CONTRACTS.md), [selected vector/tail/matrix/directional laws](docs/DISTRIBUTION_BREADTH.md), [covariance priors](docs/COVARIANCE_PRIORS.md), [constructions](docs/EXTENDED_CONSTRUCTIONS.md) and [mixed measures](docs/MIXED_MEASURES.md) |
| GVM diagnostics, information and selected numerical improvements | [Joint law](docs/GAUSS_VON_MISES.md), [diagnostics](docs/GVM_DIAGNOSTICS.md), [moments](docs/GVM_MOMENTS.md), [quadrature](docs/GVM_QUADRATURE.md), [Bhattacharyya methods](docs/GVM_BHATTACHARYYA.md) and [mutual information](docs/GVM_MUTUAL_INFORMATION.md) |
| Practical 6.1 modeling workflows | [Observation semantics](docs/OBSERVATION_MODELS.md), [partial/conditional copulas](docs/COPULAS.md), [scalar-linear GVM-mixture fitting](docs/GVM_MIXTURE_FITTING.md) and [full-mixture linear/angular MI](docs/GVM_MIXTURE_MI.md) |
| Consumable compiled release | [Signed Central publication and fresh-consumer verification](docs/MAVEN_CENTRAL.md); original GitHub bundle retained |

These are completed **bounded contracts**, not blanket solutions to the broader
problems. Calibration studies include negative results; diagnostics and improved
proposals do not establish universal coverage or discover every missing mode.

## Optional backlog: promote only when needed

| Candidate | Trigger / unresolved scope |
| --- | --- |
| Particle-diversity robustness (bounded experiment complete; no promotion) | [PF-DIVERSITY-01 outcome](docs/PARTICLE_DIVERSITY_STATUS.md): preserve diagnostics and controls; any alternative resampling schedule needs a new protocol and held-out evidence; no universal missing-mode detection |
| Higher-dimensional GVM-mixture fitting and component selection | A concrete fitting workload beyond the current scalar-linear contract; independent held-out validation, explicit fit failures and total training cost |
| Broader dependence models and information measures | A model requiring mixed/count copulas, conditional interval integration, additional partitions or currently unsupported measures |
| Broader inference-health calibration | New posterior geometries, rare-event queries or dependence structures that existing validation does not cover |
| Additional distributions and constructions | A representative model requiring a missing family/flavor; see the inventory before implementing a duplicate |
| Further performance or RNG/graph infrastructure | Measured accuracy-adjusted cost, memory, replay or ownership requirements; not speculative micro-optimization |

The [GVM/GMM research backlog](docs/GVM_MIXTURE_RESEARCH_PLAN.md) retains comparative
questions without claiming general superiority. Portable RNG checkpoints,
per-sample counter addressing, additional static graph vocabulary and broader
numerical ranges remain optional work, not adoption prerequisites.

## Boundaries that remain in force

- Arbitrary mutable Figaro graphs are **not** generally thread-safe. Use the
  documented isolated model factories or restricted static executor; immutable
  kernels do not make external callbacks or shared Elements safe.
- No universal automatic precision stopping or guaranteed unknown-mode detection
  is claimed. Bounded IID guarantees require their stated assumptions; other
  diagnostics and numerical estimates retain their own limitations.
- GVM-mixture parameter fitting currently covers **one linear coordinate plus one
  angle**. Higher-dimensional fixed kernels and some metrics do not imply a
  higher-dimensional fitter.
- Temporal grammar/framework-selection work and application-specific tracking,
  report ingestion, fusion or propagation are not an active Figaro milestone.

## Gates for reopening a milestone

Record the use case, existing alternatives, primary references, supported inputs,
numerical/inference contracts, measurable acceptance criteria and explicit exclusions.
Keep pilot/training work separate from production where required; include failures,
refusals and total costs in comparisons. Add independent numerical controls,
lifecycle/concurrency tests where relevant, user/API documentation and a clean
packaged-consumer check. Commit, validate in CI, and issue a new version for runtime
changes; do not overwrite an existing release.

Use `wishlist -> researched -> planned -> implementing -> validated` status
transitions. A research result or a passing local experiment alone is not a shipped
capability. Historical evidence and original release bytes remain preserved.

Related: [wishlist](WISHLIST.md), [roadmap history](docs/ROADMAP_HISTORY.md),
[release notes](docs/RELEASE_NOTES.md), [consumer boundary](CONSUMER_BOUNDARY.md).
