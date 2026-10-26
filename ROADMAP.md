# Figaro roadmap

This is the current delivery and maintenance plan. [WISHLIST.md](WISHLIST.md)
contains optional candidates; [roadmap history](docs/ROADMAP_HISTORY.md) preserves
the completed milestones and their original evidence. Historical priorities are
not an active queue.

## Current baseline: use 7.4.1

**Figaro 7.4.1 is the adoption baseline and is published on Maven Central.**
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

### Diagnostic correctness: published in 7.4.1

The isolated [tied-quantile repair](docs/TIED_QUANTILE_REPAIR.md) corrects a
floating-point cutoff error affecting tail ESS for repeated MCMC states. Its
independent replay, integration/post-merge CI and final-version CI passed. The
prepared Linux bundle passed all seven packaged consumers on Linux and Windows.
The [7.4.1 release record](docs/RELEASE_7_4_1_STATUS.md) pins the final-main source,
artifact hashes, signed publication and all seven successful Central-only consumers.
Stopping, defaults and strict-calibration thresholds are unchanged. A tail-ESS
warning or a caller's near-threshold decision may nevertheless change.

### Research closeout: completed assessments are not an active queue

The [standalone retrospective](docs/LITERATURE_EXPERIMENTS_REPORT.md) and its
[dated source records](docs/reports/EXPERIMENT_RECORDS.md) preserve motivations,
full-outcome results, original thresholds and no-promotion decisions.

| Completed line | Disposition / condition for reconsideration |
| --- | --- |
| PF-PROPOSAL-01 nonlinear and multi-component guides | No promotion. Equal-particle gains did not establish total-cost utility; the expensive-likelihood relevance screen stopped before an unjustified utility trial. Reopen only for a qualified posterior/workload and a new frozen comparison. |
| Higher-dimensional GVM fitting, joint optimization and predictive selection | Parked after reliability, cost or incremental-benefit gates failed. Stronger completion and useful retained densities remain favorable evidence, not permission to alter failed gates. Existing scalar-linear fitting remains supported. |
| Multi-root coordinate warm-up and budget scaling | Not promoted. More retained work repaired product-target failures, but original-fixture requalification still failed on dependence. No automatic dimension-to-budget law follows. |
| Learned transformed-block pilot | Closed after reliability and total-cost failures. Correct adapter/Jacobian controls do not qualify the learned workflow. Its original audit and no-promotion outcome remain unchanged by the separate diagnostic repair. |

### Joint-block bounded implementation approved; independent gates required

The bounded review of **caller-specified transformed joint-block proposals** is
complete and its bounded three-stage plan is approved. The milestone covers owned constant-parameter
Normal/Gamma/Beta roots, with caller-supplied fixed geometry, not automatic learning.
Existing [Normal-only fixed-covariance blocks](docs/BLOCKED_PROPOSALS.md) remain
the supported starting point. The proposal specifies support/Jacobian/rollback
and ownership contracts and requires independent validation before public promotion.
The [bounded API review](docs/TRANSFORMED_JOINT_BLOCK_API_REVIEW.md) recommends a
complete fixed-budget scheme with explicit coordinate covariance and a staged
independent-validation plan. Implementation and validation are next, not shipped code.
No automatic covariance learning, calibration relaxation, hidden fallback,
universal performance claim or arbitrary-graph support is approved by this review.
Finite-population mode loss and unsupported numerical ranges remain separate limits.

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
| Higher-dimensional GVM-mixture fitting and component selection (parked) | A concrete new workload/contract justifying reopening the closed experiments; independent held-out validation, explicit incomplete comparisons and total training cost |
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
