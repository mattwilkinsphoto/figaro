# Figaro roadmap

This is the current delivery and maintenance plan. [WISHLIST.md](WISHLIST.md)
contains optional candidates; [roadmap history](docs/ROADMAP_HISTORY.md) preserves
the completed milestones and their original evidence. Historical priorities are
not an active queue.

## Current baseline: use 6.1.0

**Figaro 6.1.0 is the adoption baseline and is published on Maven Central.**
The Java 17 / Scala 3 / sbt 2 modernization and approved core performance,
distribution and modeling milestones are integrated. See the
[release scope](docs/RELEASE_6_1.md), [installation and publication](docs/MAVEN_CENTRAL.md),
[distribution inventory](docs/DISTRIBUTION_SUPPORT.md) and
[migration limitations](docs/MIGRATION.md).

The current phase is **application-driven maintenance**, not another scheduled
modernization campaign. No remaining modernization milestone is a known adoption
blocker within the documented scope. This is not a guarantee of correctness,
precision or performance for every application model.

## Active priorities

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
