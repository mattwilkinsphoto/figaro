# Migrating to Scala 3 and sbt 2

## Capability expansion (modern.22)

The [three new capabilities](CAPABILITY_EXPANSION_ACCEPTANCE.md) are opt-in. Rebuild
consumers: `RareEventImportance.FitResult` and `StaticGraphImportance.Result` gain
work-accounting fields; `FitStatus` gains `MixtureFitFailure`; the sealed static
node vocabulary gains five variants. Exhaustive client matches may need new cases.
`TruncatedDistribution` now accepts infinite endpoints within base support. Some
previously unsupported scalar comparisons now return guarded numerical results.
Unweighted mixture fitting, single-Gaussian fitting and inference defaults retain
their existing contracts. No automatic graph rewriting or model-selection policy
is introduced. Read the new guides before enabling a proposal or custom transform.

## Query-aware rare events (modern.21)

[RareEventImportance](RARE_EVENT_PROPOSALS.md) is additive and explicitly opt-in.
It requires a normalized base law and uses ordinary event contributions, unlike
the existing self-normalized `VectorImportance` API. Do not pass an unnormalized
posterior density or interpret empirical MCSE as an automatic stopping guarantee.
Pilot/production work and RNG lanes are separate; no existing default or dependency changes.

## Covariance priors (modern.20)

[LKJ and inverse-Wishart](COVARIANCE_PRIORS.md) are additive APIs. Rebuild consumers
against the new snapshot. They preserve existing RNG defaults and add no runtime
dependency. Covariance scales, standard deviations and Cholesky densities are
explicitly distinct. Wishart density now rejects a wrong-sized observation before
matrix arithmetic, rather than potentially ignoring coordinates or indexing past bounds.

## Legacy distribution consolidation (modern.19)

Read [legacy distribution contracts](LEGACY_DISTRIBUTION_CONTRACTS.md) before upgrading:
non-unit-scale inverse-Gamma sampling now agrees with its density; Beta/Dirichlet
`logp` describes the sampling prior rather than learned summary parameters. Invalid
parameters fail earlier. Direct log-likelihood scoring avoids underflow and no longer
generates discarded prior draws for observed `HasLogDensity` elements. Count samplers
and owned proposal traversal also change random-number consumption. Rebaseline seeded
sequences; do not expect bitwise replay of pre-modern.19 runs. No runtime dependency
was added, and no arbitrary shared-graph safety or new stopping guarantee is implied.

## Explicit graph proposal integration

[GraphProposalImportance](GRAPH_PROPOSALS.md) adds an opt-in fresh-Universe bridge
for a caller-supplied joint prior/proposal and ordinary graph evidence. Existing
sampler APIs and defaults are unchanged; there is no new runtime dependency.
Rebuild consumers against the incremented snapshot. The bridge owns and clears
its graph, returns detached scalar weighted evidence, retains rejected attempts,
and forbids observed/intervened roots or cross-Universe dependencies. It does not
rewrite existing models, automatically fit proposals, or enable precision stopping.

Modern.13 also corrects the reproducibility/coverage build procedure: an empty
`Global / cacheStores` setting did not suppress disk hits in the installed sbt 2.0.8.
Use new isolated action caches and verify actual compiler execution as described in
[building](BUILDING.md). Previously tested incremental JAR hashes can differ from
clean builds; distribute the independently verified clean artifacts.

## Pilot-only mixture fitting

[GaussianMixtureProposal](MIXTURE_PROPOSALS.md) adds a pure, bounded fitter returning
an immutable `VectorImportance.Mixture`. Existing single-Gaussian fitting and graph
samplers are unchanged. This addition has no runtime dependency; rebuild consumers
against the incremented snapshot when integrated. Component count, ridge, fitting
limits and separate production remain explicit; no automatic fitting fallback occurs.

## Opt-in explicit vector proposals

[VectorImportance](VECTOR_IMPORTANCE.md) adds frozen normalized proposals, full-density
importance ratios and optional discarded pilot fitting. It accepts explicit continuous
vector densities, not arbitrary graph models. Existing samplers, defaults and stopping
policies are unchanged; there is no runtime dependency addition. Insufficient or invalid
fits produce no production estimate, rather than an implicit prior fallback. Rebuild
and publish the branch snapshot before use; verify the artifact hash when consuming an
unchanged snapshot coordinate. See the guide for callback/support contracts, separate
pilot/production budgets, replay metadata and empirical coverage limitations.

## Opt-in inference health

[InferenceHealth and ParetoTail](INFERENCE_HEALTH.md) add detached reports for raw
independent importance draws and ordered MCMC chains, plus a blocking single-query
Importance collector. Existing sampler signatures, results, RNG selection and stopping
rules do not change. Warnings are returned, not printed or acted on automatically.
This is a diagnostic policy, not a posterior-correctness or confidence-coverage certificate.
The licensed ArviZ tail-fit adaptation adds `META-INF/ArviZ-LICENSE.txt` to all four
library artifacts; it adds no runtime dependency. Rebuild/publish the branch snapshot
before using the new API; the unchanged snapshot coordinate alone does not identify it.

## Purpose-based RNG presets

[RandomSelection](RNG_SELECTION.md) adds opt-in V1 presets for general inference,
fixed chains and counter ranges. Resolve once and apply the decision to a runner
config or blocking importance construction. Existing API defaults, stream mappings,
sampler result shapes and dependencies do not change. No automatic re-selection
occurs on replay; use recorded stream descriptors. Explicit pairs remain subject
to hard requirement checks; unsupported per-sample addressing is rejected.
Persist selection metadata separately alongside the unchanged result descriptors.

## Versioned stream allocation and Philox

[RandomStreams](RNG_STREAMS.md) adds opt-in `PartitionedV1`: native LXM splitting,
Xoshiro256++ jumping and Philox4x64-10 counter ranges with per-stream raw-word limits.
LXM remains the default backend; `SeededV1` remains the allocation default and preserves
previous root/index sequences. Philox reuses Commons RNG 1.7 already on the classpath.

Recompile consumers: both multi-chain `Config` types add a final `randomStreams` field,
and both `ChainResult` types add `randomStream: Option[RandomStreams.Descriptor]`.
Defaults preserve older constructor calls, not binary compatibility or positional
pattern-match arity. Prefer named fields. `ParImportance.seededWithStreams` is additive;
existing importance signatures retain `SeededV1` behavior.

In partitioned mode, `seed` in results/factory callbacks is a compatibility label,
not a standalone engine seed. Retain the descriptor and experiment configuration.
Replay recreates the start, rejects provider/JDK mismatch, and is not a checkpoint.
Partitioned generators reject reseeding and throw on raw-word exhaustion. Graph hash
traversal can still prevent exact trace replay with the default MH proposal; this
change does not silently replace that proposal. See the guide for explicit proposals.

## Scientific RNG default and explicit alternatives

The [RNG guide](RNG_ASSESSMENT.md) replaces Figaro-owned Java Random streams with
L64X128MixRandom by default. Historical seeds yield different samples. Explicit
LegacyJava mode remains for synchronous/owned-runner replay; Xoshiro256++, PCG
RXS-M-XS-64 and MT19937 are selectable. Recompile consumers: MH/vector Config case
classes gain a final randomAlgorithm field. Positional pattern matches must be updated.
Commons RNG Simple 1.7 is a new transitive runtime dependency; MT uses existing Commons
Math. Normal Maven/sbt dependency resolution brings it in automatically. Manually managed
thin-JAR classpaths must include Commons RNG Simple, Core and Client API 1.7.
Caller-supplied Scala Random objects retain their own backend. See the guide for
scopes, stream allocation, provenance and limits on cross-version replay.

The [statistical study](STATISTICAL_VALIDATION.md) corrects a test's printed standard
error and supplies posterior-reference checks; it does not loosen statistical tolerances
or claim that RNG replacement cures concentrated-posterior importance sampling.

## Distribution constructions and Gaussian likelihoods

The [D4 guide](DISTRIBUTION_CONSTRUCTIONS.md) adds opt-in transformations, truncation,
mixtures, zero-adjusted counts and Gaussian information metrics. The existing
List-valued `MultivariateNormal` atomic adapter now supplies stable `HasLogDensity`
likelihoods and draws through a validated Gaussian kernel with the scoped Figaro RNG.
Its previous `logp` placeholder is removed for atomic instances. Kernel limits now
apply to density/sampling: dimension 1..128, symmetric full-rank covariance, bounded
parameters and resolved correlation conditioning. Seeded samples differ from the
former Apache sampling path. The public legacy Apache distribution member is retained
for compatibility but bypasses the new contract when called directly. Scalar `Normal`
still takes variance; the new `GaussianDistribution` takes standard deviation.
No inference default, stable release version or historical bundle changes.

## Scalar GVM bounded-tail integration

The [bounded-tail policy](GVM_SCALAR_TAIL_PRODUCTION.md) changes the implementation of
`GaussVonMisesScalarBhattacharyya.compare`, not its signature, result shape or dependency
coordinate. Existing scalar callers need only rebuild the new snapshot; there is no
new switch. Fourier and inference defaults remain unchanged. Integrated on main at
`f4884cfb` after [passing CI](https://github.com/mattwilkinsphoto/figaro/actions/runs/34209082583).

Radius, evaluation count, last distance digits and error diagnostics may differ at the
same tolerance. `maxEvaluations` and `Result.evaluations` count integrand calls, excluding
the optional fixed 64-cell/2,304-term tail-selection prepass. Do not interpret a zero
evaluation count as zero setup cost or a work budget as a wall-clock timeout. Existing
precision/refusal checks remain; no accuracy certification or wider supported range
is implied. The immutable RC1 bundle is unchanged.

## Core performance acceptance

`modernize/core-performance-acceptance` adds acceptance evidence and CI gates, not new
production code, dependencies, defaults or public library signatures. Snapshot modern.10
is unchanged. Existing multi-chain vector users get the accumulated diagnostic improvements
when rebuilding against the current library; no new switch is required. Parallel samplers
remain opt-in, and ordinary graph models do not become generally thread-safe.

The [acceptance report](CORE_PERFORMANCE_ACCEPTANCE.md) measures the combined diagnostic
changes directly and documents a separate application compiled against local publication.
Neither improved throughput nor successful consumer smoke tests establish statistical
coverage or validate an arbitrary downstream application. Main now includes the accepted
Scala 3/sbt 2 development baseline; there is no new public release. The preceding main
commit `b3431027` remains in history for comparison and rollback planning.

## Resource/scaling assessment and lifecycle-test repair

`modernize/resource-scaling-assessment` adds a [resource study](RESOURCE_SCALING_ASSESSMENT.md),
not a new production sampler or setting. Snapshot modern.10, public library signatures,
dependencies and defaults remain unchanged. Consumers have nothing new to enable or
migrate. Study-only loop callbacks and ordered graph proposals are explicitly labeled
controls, not automatic changes to user models.

The legacy resume regression now waits for an actual sampling step before requiring
progress. Consecutive queries are allowed to return the same answer; resume does not
promise a step between each query. The worker implementation and existing timeout are
unchanged. See the [failure diagnosis and checks](DIAGNOSTIC_HOTSPOT_STUDY.md#verification-and-ci-boundary).

## Diagnostic hotspot follow-up

`modernize/diagnostic-hotspot-study` retains snapshot modern.10, public signatures,
dependencies and defaults. Rebuild to use stable radix ordering for nonmonotone rank
inputs with at least 16000 pooled values; smaller and monotone inputs retain the merge
sorter. No option, larger sampling budget or consumer source change is required. Exact
ties, signed zeros, inverse-normal scores, diagnostics and interruption safeguards are
preserved. See [selection rationale, usage and complete measurements](DIAGNOSTIC_HOTSPOT_STUDY.md).
This does not add general graph thread safety or resolve statistical undercoverage.

## Interleaved performance audit follow-up

`modernize/interleaved-performance-audit` adds investigation tools and checked evidence
only. Snapshot modern.10, production code, public API, dependencies and sampling defaults
are unchanged; consumers have nothing new to enable or migrate. The audit compares the
FFT and sorting checkpoints in balanced fresh-JVM pairs and separates observed model
callback allocations from sampler overhead. See the [protocol, reproduction commands,
results and limitations](INTERLEAVED_PERFORMANCE_AUDIT.md). Timing evidence does not
justify reducing sampling budgets or weakening cancellation safeguards.

## Primitive diagnostic sorting follow-up

`modernize/primitive-diagnostic-sorting` retains snapshot modern.10, public signatures,
dependencies and defaults. Rebuild to use primitive rank-index and copied-value sorting
automatically. Existing tie groups, signed-zero handling, normal scores and diagnostic
outputs are unchanged. Inputs retain their temporal order; do not sort chains yourself.
There is no new thread setting or shared buffer pool. See
[examples, compatibility checks and measured tradeoffs](PRIMITIVE_DIAGNOSTIC_SORTING.md).

## Primitive FFT autocovariance follow-up

`modernize/primitive-fft-autocovariance` keeps snapshot modern.10, public signatures,
dependencies and defaults. Rebuild to obtain invocation-owned primitive FFT buffers;
existing diagnostic/multi-chain calls benefit automatically without a new flag. The
FFT algorithm, normalization, lag divisor, estimates and warnings are unchanged. Buffers
are not shared between calls, and this does not make a shared model universe thread-safe.
See [examples, exact-result checks and measurements](PRIMITIVE_FFT_AUTOCOVARIANCE.md).

## Primitive diagnostic reductions follow-up

`modernize/primitive-diagnostic-reductions` retains snapshot modern.10, public signatures
and defaults. Rebuild to receive the internal mean/variance allocation reduction; no
consumer changes or feature flags are needed. Existing summation order, estimates,
warnings and cancellation contracts are preserved. This also affects scalar diagnostic
callers; measured speedups cover the fixed vector benchmark, not arbitrary graph models.
See [implementation scope, examples and results](PRIMITIVE_DIAGNOSTIC_REDUCTIONS.md).

## Parallel coordinate diagnostics follow-up

`modernize/parallel-vector-diagnostics` retains snapshot `6.0.0-modern.10-SNAPSHOT`
and all public signatures/default values. Rebuild to use the changed scheduling:
`MultiChainVectorSliceSampler.Config.parallelism` now also bounds coordinate diagnostics
after the sampling pool exits. Existing parallel calls automatically use this allowance;
`parallelism = 1` restores serial diagnostic scheduling. Temporary diagnostic memory can
increase with worker count; `maxStoredValues` is not a heap limit. Seeds, traces, summaries,
warning order and aligned-prefix rules are unchanged. Scalar `McmcDiagnostics.summarize`
now honors interruption between stages and within rank/ESS loops without clearing the
flag. No graph thread-safety, estimator, dependency or toolchain change is implied.
See [execution contracts and measured comparisons](PARALLEL_VECTOR_DIAGNOSTICS.md).

## Multi-chain continuous-vector follow-up

`modernize/multi-chain-vector-sampling` uses `6.0.0-modern.10-SNAPSHOT`. The additive
`MultiChainVectorSliceSampler` owns a bounded worker pool and calls `VectorSliceSampler.run`
unchanged. Its factory receives chain index and assigned seed; the embedded single-chain
configuration seed is a root expanded into independent chain seeds. Returned traces stay
in chain-index order. Coordinate diagnostics use explicitly aligned prefixes if caps
produce unequal lengths; every chain and its full accounting remain in the result.

No existing API migration is required. Rebuild against this snapshot to use the wrapper.
Graph inference, kernels, precision policies, dependencies, and toolchain are unchanged.
Caller/factory interruption and worker failure have distinct contracts; uncooperative
callbacks can outlive a failed bounded shutdown. See the
[API and lifecycle guide](MULTI_CHAIN_VECTOR_SAMPLING.md).

## Continuous-vector sampling follow-up

`modernize/continuous-vector-sampling` uses `6.0.0-modern.9-SNAPSHOT`. The additive
`VectorSliceSampler` API accepts an explicit immutable continuous-vector log density,
with GPSS or quantile selected by the caller. Existing graph samplers, stopping policies,
dependencies, and the Scala 3.9.0 / sbt 2.0.8 / JDK 17 baseline are unchanged. Rebuild
consumers against the new snapshot to use it; no existing API migration is required.

Unlike the graph multi-chain runner, it runs one chain on the caller's thread, owns no
Universe or executor, and supplies no automatic diagnostics/precision stopping. Budget
exhaustion returns only complete transitions; callback/search/numerical failures throw.
Do not substitute its output for graph query objects or treat `DrawsReached` as convergence.
See [contracts, examples, and limitations](VECTOR_SLICE_SAMPLING.md).

## MCMC reliability follow-up

`modernize/mcmc-reliability` uses `6.0.0-modern.8-SNAPSHOT`, with the same Scala 3.9.0, sbt 2.0.8, and JDK 17 baseline. Rebuild and recompile consumers. The [reliability guide](MCMC_RELIABILITY.md) explains the changed precision semantics and actionable failure reasons.

`McmcPrecision.evaluate` and `MH.runUntilPrecise` now size intervals with the larger of the existing batch-means and raw-mean ESS-based MCSE estimates, requiring both to be finite and positive. `batchMeansMcse` itself is unchanged; `mcseUsed` exposes the combined estimate. Identical prefixes cannot yield narrower intervals or earlier successful stopping, but runs may now take longer or reach their cap. Configuration and diagnostic thresholds are unchanged. This is an internal consistency safeguard, not a general finite-sample coverage guarantee: [the paired audit](MCMC_RELIABILITY_VALIDATION.md) still finds serious undercoverage on poorly explored curved targets.

`Assessment` adds a final `failureReasons: Vector[FailureReason]` field, defaulting to empty for older constructor calls. Its product/unapply arity changes from seven to eight: update positional pattern matches or prefer named field access. Compiled consumers must be rebuilt; manually constructing/copying an assessment does not validate consistency among fields. Fixed-budget sampling, proposal/calibration arithmetic, random streams, worker ownership, Gaussian TSPRT, and categorical KL are unchanged. Earlier checkpoint descriptions below are historical.

## Pilot-calibration follow-up

`modernize/proposal-calibration` uses snapshot `6.0.0-modern.7-SNAPSHOT`. The additive [calibration API](PROPOSAL_CALIBRATION.md) estimates a fixed Gaussian block covariance from a separate pilot, discloses shrinkage/scaling and diagnostics, and binds the frozen matrix to fresh production elements by name. The Scala 3.9.0, sbt 2.0.8, and JDK 17 baseline is unchanged; rebuild and recompile consumers against this snapshot.

There are no new proposal subtypes, changed sampler defaults, or changes to retained-sampling execution. Existing manual block proposals keep their numerical factorization and validation behavior. Pilot exclusion, separate production seeds/warm-up, and equivalent model/coordinate semantics are explicit caller responsibilities. Inadequate pilot traces are rejected without fallback. This is offline pilot calibration, not online adaptive MCMC, global mode discovery, automatic block selection, or a covariance-accuracy certificate. See [broader validation](PROPOSAL_CALIBRATION_VALIDATION.md), including pilot rejection and cases where calibration cost is not recovered.

## Blocked-proposal follow-up

`modernize/blocked-proposals` uses snapshot `6.0.0-modern.6-SNAPSHOT` and adds [fixed-covariance Gaussian block proposals](BLOCKED_PROPOSALS.md). Scala 3.9.0, sbt 2.0.8, and JDK 17 are unchanged. Rebuild and recompile consumers against this snapshot; this is not a published stable release.

Existing default, fixed-budget, and adaptive sampler behavior remains unchanged unless you supply the new scheme. The additive factory supports only permanent constant-parameter Normal targets, with one covariance per chain fixed before sampling. It includes the prior-density correction for symmetric random walks and uses the existing evidence updates and rejection cleanup. There is no automatic adaptation, global covariance discovery, or new shared-universe thread-safety promise. Exhaustive external matches on the sealed `ProposalScheme` hierarchy may need a fallback for the new internal subtype.

See the [validation report](BLOCKED_PROPOSAL_VALIDATION.md) for comparisons with both default MH and existing joint prior proposals, plus cases where blocking is counterproductive. Earlier checkpoint version numbers below are historical.

## Stopping-criteria follow-up

The `modernize/stopping-criteria` branch adds [Gaussian TSPRT, categorical KL, and adaptive MCMC precision stopping](STOPPING_CRITERIA.md). Existing fixed-budget APIs and result types remain available. New `runUntilPrecise` treats `drawsPerChain` as a cap and returns an explicit stop reason plus batch-means precision assessments. No historical stopping-parameter tuple is adopted: error probabilities, observation SD, and boundary directions are named explicitly. KL-based MCMC convergence is not claimed or enabled. These changes do not require a Scala, sbt, or JDK upgrade.

The preceding multi-chain milestone used snapshot `6.0.0-modern.4-SNAPSHOT`; the stopping follow-up uses `6.0.0-modern.5-SNAPSHOT`. Scala 3.9.0, sbt 2.0.8, and JDK 17 remain fixed. This includes the earlier [parallel importance work](PARALLEL_PERFORMANCE.md) and [deprecation retirement](DEPRECATION_RETIREMENT.md); obsolete APIs remain removed and deprecations still fail compilation. Recompile consumers after rebuilding this branch. The tables below describe earlier upgrade checkpoints.

## Multi-chain migration changes

This is an additive API: ordinary MH and seeded parallel importance remain available. Opt in by supplying a model factory to `MultiChainMetropolisHastings.run`. Each factory receives a runner-owned universe; do not pass existing model nodes or retain them after return. Results are immutable scalar traces and diagnostic summaries, not live algorithms requiring `kill()`.

The draw count is **per chain**. Worker count controls scheduling independently of chain count and seed assignment. Warm-up is separately discarded for every chain, and rejection repeats are retained. Diagnostics report rank-normalized/folded split R-hat, bulk/tail ESS, raw-scale mean ESS, and MCSE; undefined values/warnings do not certify convergence. The ESS estimator is conservatively capped at the split draw count and is not an exact numerical clone of Stan/posterior's antithetic implementation.

The supported evidence contract is intentionally narrower than all legacy MH call sites: use hard conditions or explicit likelihood constraints. `observe()` is rejected, not silently ignored or automatically translated. The guide shows a continuous likelihood and explains when omitted normalizers are valid. Initialization is bounded and must find a valid prior state; `initialState` only selects a starting region, not a different posterior for each chain.

The isolated execution path does not certify shared universes, nested inference, learning/filtering caches, or arbitrary callback state as thread-safe. Cancellation is cooperative with bounded worker-shutdown waiting; uninterruptible user code can outlive a failed call. Model-specific proposals, multimodal exploration, trace storage pressure, and uncertainty in diagnostic estimates remain user-visible concerns. See the [complete contracts and gotchas](MULTI_CHAIN_MCMC.md).

## Parallel-performance changes

- `ParImportance.seeded` is an opt-in, blocking, one-time sampler with a bounded private executor, worker-local random streams, and scoped default universes. Existing `ParImportance.apply` overloads remain available; they do not acquire the new isolation contract. Put evidence in the seeded model factory; incremental `probabilityOfEvidence` is not part of the new return type.
- Both one-time factories now use the entire sample budget, distribute remainder samples, and cap workers at the sample count. Invalid worker/sample counts throw `IllegalArgumentException` before constructing models. This intentionally changes results that previously dropped the remainder or created zero-work workers.
- Parallel lifecycle delegation now calls each child's public `start/stop/resume/kill`, preserving active flags and importance-cache deregistration. Custom subclasses that depended on bypassed public lifecycle overrides need review.
- `util.random` remains a stable `scala.util.Random`. New `withRandomSeed(seed)(body)` temporarily routes it on the calling thread and restores the previous stream afterward. Unscoped sequences are regression-tested against Scala's RNG; user-created RNGs and asynchronous work are not scoped.
- `Universe.universe` is now an implicit getter plus setter backed by a process default and private thread scopes. Ordinary reads and assignments retain source spelling, but reflection/TASTy consumers must account for the former `var` becoming accessor methods. Outside the seeded sampler, default-universe mutation remains process-wide. Hash allocation is atomic; that alone does not make universes thread-safe.
- Cancellation is cooperative, including importance rejection retries. Do not call lifecycle/query methods concurrently; interrupt the thread blocked in `start()` to request cancellation. Uninterruptible user callbacks cannot be forcibly stopped. See the complete [contracts and gotchas](PARALLEL_PERFORMANCE.md#gotchas).

This is not a blanket thread-safety upgrade for every inference, learning, or filtering algorithm. The benchmark demonstrates independent MCMC chains, not concurrent steps within a dependent chain.

## Overview

This document is for application developers updating their build/model code. [MODERNIZATION.md](../MODERNIZATION.md) retains the chronological engineering evidence. The new line is a reasonable development baseline, not a claim that every historical algorithm, workload, or deployment mode has been fully revalidated.

| Item | Accepted Scala 2.13 baseline | sbt 2 checkpoint | Scala 3 line |
| --- | --- | --- | --- |
| JDK | 17 | 17 | 17 |
| sbt | 1.13.0 | 2.0.8 | 2.0.8 |
| Scala library compiler | 2.13.18 | 2.13.18 | 3.9.0 LTS |
| Snapshot artifact | `figaro_2.13:5.0.0-modern.2-SNAPSHOT` | Same | `figaro_3:6.0.0-modern.1-SNAPSHOT` |
| Checkpoint | `b3431027` | `b281f016` | `bfc7e447` (migration implementation) |

The Scala compiler inside sbt compiles the build definition; it does not select the language version for your library. The two upgrades were deliberately separate checkpoints. At documentation time they are on `modernize/sbt-2` and `modernize/scala-3`; do not assume `main` already contains them.

## Upgrade in three steps

1. Install/use JDK 17, obtain the Scala 3 branch, and run its documented examples. Let `project/build.properties` select sbt 2.0.8.
2. Publish Figaro locally, move the consumer to Scala 3.9.0, update its dependency to `6.0.0-modern.1-SNAPSHOT`, and recompile. Replace manually pinned `_2.13` coordinates with `_3`; `%%` does this in sbt.
3. Fix source changes below and run representative end-to-end inference/serialization tests in the consumer. Compare numerical answers with justified tolerances, not only successful compilation.

Retain the previous dependency/checkpoint until the consumer passes. Reverting a consumer dependency is a deployment rollback; it does not make Scala 3-compiled application classes runnable against an old Scala 2 JAR. Rebuild the matching application version too. Release publication is a separate step from local upgrade testing.

## sbt 2 changes users encounter

- Pass multiple commands as one quoted semicolon-separated string: `sbt "clean; compile; Test / compile"`.
- Artifacts now live under `target/out/jvm/scala-3.9.0/figaro/`, not the old `Figaro/target/scala-...` layout.
- Use project-qualified commands: `figaro / publishLocal`, `figaro / assembly`, and `examples / Compile / runMain ...`. The root is an aggregation project and is not published.
- A successful cached `clean` rebuild can restore prior artifacts. Reproducibility testing disables action-cache stores for the comparison build; see [building](BUILDING.md).
- The build uses Scala 3 imports, hashed file mappings, and explicit uncached reads for external manifest/legal files. Consumers do not need to reproduce these internal packaging helpers.
- sbt-assembly and sbt-scoverage remain supported in the verified build. The fat JAR still excludes Scala runtime dependencies.

## Scala 3 source and API changes

### This is a new binary line

Public package names remain `com.cra.figaro`, but `_3` is not a drop-in binary replacement for `_2.13`. Recompile consumers and subclasses. There is no Scala 2 cross-build in this line and no complete source-compatibility promise.

### Syntax and overridden members

Procedure syntax becomes explicit `Unit`, and empty argument lists must be consistent:

```scala
// Old
// def refresh() { ... }
// override def generateValue = ...

// Scala 3 shape
def refresh(): Unit = { /* implementation */ }
// override def generateValue() = ...
```

Accessor-like members are now consistently parameterless, including `isCachable`, `burnIn`, `interval`, `discretize`, `fullyRefinable`, `depth`, `isLog`, `computedResult`, and `zeroSufficientStatistics`. For example, use `element.isCachable`, not `element.isCachable()`. Lifecycle and generation methods still use `()`. Match the actual base declaration when overriding rather than mechanically adding/removing parentheses everywhere.

The build uses normal Scala 3 type checking. `-source:3.0-migration` is **not** retained. `-no-indent` is an intentional brace-syntax choice, not a type-safety escape hatch. The deprecation-retirement branch treats deprecations as errors. Scala/build sources are pinned to LF to avoid a Windows carriage-return issue encountered in automatic symbol rewrites.

### Generic query extension points

Scala 3 cannot express the old abstract `U[_]` wildcard application. Custom subclasses now supply a heterogeneous target supertype Q:

```scala
import com.cra.figaro.algorithm.BaseProbQueryAlgorithm
import com.cra.figaro.language.Element

// Abstract extension shape; implement the required algorithm/query methods.
trait MyElementQueries extends BaseProbQueryAlgorithm[Element[?], Element]
```

Parallel reference-based specializations use `BaseProbQuerySampler[Reference[?], Reference]`. Ordinary users of `VariableElimination` or `Importance` do not need to declare these type arguments.

`queryTargets` and the ordinary query algorithm's `universe` are abstract getters where lazy implementations are allowed. Protected `WeightSeen` tuples became generic wrappers preserving the element/map-key type relationship. Extension code constructing/destructuring those tuples must adapt to the wrapper; direct field access remains `_1` and `_2`. A few heterogeneous read-only collection boundaries still use explicit casts; this is not general permission to cast incompatible element types.

### Decision-distance evidence

Removed view bounds (`T <% Distance[T]`) become `T: DistanceConversion`, where `DistanceConversion[T]` is `T => Distance[T]`. Built-in primitive and tuple conversions remain available from `Distance.*`. Generic implementations invoke their evidence explicitly rather than assuming a function value is an automatic conversion.

```scala
import com.cra.figaro.algorithm.decision.index.*
import com.cra.figaro.algorithm.decision.index.Distance.*

val distance = TupleDistance2[Int, Double]((0, 0.0)).distance((3, 4.0)) // 5.0
```

Custom parent types can implement `Distance[YourType]`. Flat/VP index ordering and custom/tuple evidence have dedicated regressions.

### Dynamic element creation

`Create[T](name, inputs*)` resolves the JVM singleton and calls its `Creatable` interface directly; it no longer depends on Scala 2 runtime mirrors. Companion names and explicit `$` module names are supported. Unknown names raise `ClassNotFoundException`; ordinary/non-Creatable objects are rejected; rejected argument creation preserves its cause.

This remains a narrow trusted-JVM extension point. It is not arbitrary constructor reflection or a security boundary. Type erasure means the supplied name/inputs must really agree with the requested result `T`; a cast cannot establish that contract. Custom class-loader/plugin arrangements and every third-party `Creatable` implementation have not been validated. Unused `TypeTag` bounds were removed; do not re-add `scala-reflect` merely to satisfy the old signatures.

### Dependencies and tests

Native `_3` dependencies are Argonaut 6.3.13, Scala Swing 3.0.0, parallel collections 1.2.0, and test-only ScalaTest 3.2.20. Commons Math remains 3.6.1. The temporary ScalaTest 3.1 XML alignment used in the sbt 2 checkpoint is removed.

```scala
// ScalaTest 3.2 package names
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
```

Inherited parameter declarations sometimes require an explicit `AtomicBeta` result type in custom learning models. Lambda `toString` values now expose identity details; do not persist or compare them as stable model identifiers. Supported parameter JSON fixtures still pass; that does not promise serialization of an entire universe or arbitrary Scala objects.

## If you are coming from original Figaro 5

Earlier modernization stages also removed Akka, JSci, and unused Breeze/ASM/Prefuse/ScalaMeter dependencies, replaced narrow numeric calls with Commons Math, and migrated collections to Scala 2.13 conventions. Anytime algorithms use a serialized JDK worker. Timeout configuration is now a Scala `FiniteDuration`:

```scala
import scala.concurrent.duration.*
// Given an anytime sampler:
// sampler.messageTimeout = 10.seconds
```

Legacy Akka configuration/types do not control that worker. Scala parallel collections come from a separate dependency. See [dependency history](../DEPENDENCIES.md) for the full sequence.

## Accepted workarounds and remaining risk

| Area | Assessment and action |
| --- | --- |
| Normal Scala 3 compile, explicit types, brace syntax | Accepted source adaptations; not suppressed type checks. New focused behavioral tests cover the migration-sensitive paths. |
| JVM singleton reflection and narrow casts | Accepted for the current API, with the limits above. Test custom dynamic distributions in the consumer. |
| Windows JAR locking | Operational workaround, not a root-cause fix: use fresh sbt invocations for coverage and subsequent normal clean/packaging. See exact commands in [building](BUILDING.md#3-update-documentation-or-measure-coverage). |
| Long Windows IPC paths | A short `XDG_RUNTIME_DIR` avoids the observed socket-path failure. Forked tests remain enabled; administrator membership is unnecessary. |
| Warm-cache coverage | A later documentation CI run found instrumented classes without `scoverage-data` after cache reuse. The coverage command now disables action-cache stores and cleans before instrumentation so compiler/report side effects are regenerated; it does not weaken or skip tests. |
| Timing advisory | Machine-sensitive legacy checks are visible but non-blocking. Their failure is not a demonstrated numerical regression, nor proof that performance is unchanged. |
| Statistical tests | A required legacy test missed `0.50 +/- 0.01` once with `0.48995`; repeats passed without loosening tolerance. Test reliability remains work, not a solved risk. |
| Full historical suite | Already had failures and a heavyweight learning example before modernization. We have not made it green or proved every old failure unchanged under Scala 3. |
| OSGi, Java facade, custom class loaders, broad performance | Not comprehensively validated. Validate any of these deployment modes before depending on them. |
| Deprecations | Retired in the [follow-up stage](DEPRECATION_RETIREMENT.md); obsolete public entry points are removed and consumers must recompile. Other warning categories remain a separate audit. |

The migration implementation passed 119 required tests, 284 broader local checks, coverage-plugin smoke tests, publication, legal-file checks, and genuinely fresh byte-for-byte thin/fat JAR rebuilds. [Required GitHub CI](https://github.com/mattwilkinsphoto/figaro/actions/runs/33952691424) passed; its timing advisory failed. These counts describe that checkpoint, not a universal guarantee for all workloads or a full-suite coverage percentage.

Before production: exercise the actual consumer's model construction, evidence, selected algorithms, and parameter persistence end to end; compare against known numerical fixtures; test cleanup/error handling and representative resource use; harden relevant statistical checks. A production release should have an immutable version/tag and published artifacts, not only a local snapshot.

## Related

[User guide](USER_GUIDE.md), [API changes in context](API_GUIDE.md), [build/verification](BUILDING.md), [engineering log](../MODERNIZATION.md), and [JVM integration](../CONSUMER_BOUNDARY.md).
