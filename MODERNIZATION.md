# Figaro modernization log

For day-to-day use, start with the [user guide](docs/USER_GUIDE.md), [migration changes](docs/MIGRATION.md), and [build instructions](docs/BUILDING.md). This file preserves the historical sequence of checkpoints rather than replacing the current user documentation.

Documentation follow-up: the new onboarding examples passed in [CI run 33969951669](https://github.com/mattwilkinsphoto/figaro/actions/runs/33969951669), as did the regression steps preceding coverage. Coverage then aborted before running tests because instrumented classes attempted to write into a missing `scoverage-data` directory. The absence of a coverage compile in that warmed run is consistent with action-cache restoration omitting required side effects. The coverage gate now disables cache stores and cleans before enabling instrumentation; the user-facing build commands carry the same precaution. This is distinct from the Windows exported-JAR locking issue.

This log records decisions, compatibility findings, risks, and test evidence for the modernization of the Charles River Analytics Figaro codebase. The original license and `FigaroAttributions.txt` remain authoritative and must be preserved.

## Repository and provenance

- Upstream: <https://github.com/charles-river-analytics/figaro>
- Modernization origin: <https://github.com/mattwilkinsphoto/figaro>
- Upstream baseline commit: `14f48d148f715017211822e31b7ea3291733fefe` (2022-06-01).

The modernization repository began as an empty standalone public repository rather than a GitHub fork. It preserves the upstream Git history so provenance and future synchronization remain explicit.

## Stage 0: legacy baseline

Date: 2026-09-04

Legacy build definition:

- sbt 0.13.16
- Scala 2.12.2 (with 2.11.8 listed for cross-building)
- JDK 8-era build and plugins

Evidence:

- Java 17 / sbt 0.13.16: project loading fails before compilation because the Scala 2.10.6 compiler used by sbt 0.13.16 cannot load `java.lang.Object` from the modular JDK.
- Temurin JDK 8u504 / sbt 0.13.16: all 263 main, 164 test, and 37 example Scala sources compile.
- Full legacy `clean test`: 1,188 tests completed, 22 failed, 0 errored, and 0 skipped across 106 completed suites.
- The run was stopped after `LearningComponentTest` spent more than 30 minutes CPU-active at approximately 5.1 GB working set without producing a report. This test is a book example that builds 31,005 active elements and runs expectation maximization with belief propagation.
- The checkout remained clean after the baseline; generated build outputs are ignored.

Failure categories observed before modernization:

- Exact floating-point equality differing by one final bit.
- Stochastic tests with unstable or overly narrow tolerances.
- Example expectations that appear stale or internally inconsistent.
- Long-running or non-terminating learning examples mixed into the default unit-test task.

These failures are baseline behavior. Modernization gates must compare against this known state and must not claim that the legacy full suite is green.

## Stage 1: Java 17, Scala 2.12, and sbt 1.x

Decision: migrate build tooling first while holding runtime dependency versions constant.

Initial target:

- Java 17
- Scala 2.12.21
- sbt 1.13.0
- sbt-assembly 2.4.1
- sbt-scoverage 2.4.4

Rationale:

- Scala's official JDK compatibility table lists Scala 2.12.15 as the minimum 2.12 release for JDK 17 and recommends the latest patch release; 2.12.21 is the current 2.12 maintenance release.
- Scala 2.12 releases are binary compatible, allowing the build-tool migration to be tested before dependency upgrades.
- sbt's official migration guide recommends unified slash syntax for sbt 1.x.
- Scala 2.13.18 remains a separate follow-on stage because its collections redesign introduces source and API changes.

Primary references:

- <https://docs.scala-lang.org/overviews/jdk-compatibility/overview.html>
- <https://www.scala-lang.org/download/2.12.21.html>
- <https://www.scala-lang.org/download/2.13.18.html>
- <https://www.scala-sbt.org/1.x/docs/Combined%2BPages.html>
- <https://docs.scala-lang.org/overviews/core/collections-migration-213.html>
- <https://docs.oracle.com/en/java/javase/17/migrate/index.html>

Validation evidence:

- Java 17 / sbt 1.13.0 / Scala 2.12.21 compiles all 263 main, 164 test, and 37 example sources.
- The upgraded compiler exposed a recursive Argonaut decoder selection in `ModelParameters`; decoding `Dirichlet` through the abstract `Parameter[_]` decoder failed at runtime. The decoder now selects `AtomicDirichlet` explicitly, and `SerializationTest` passes 5/5.
- The initial 87-test smoke selection completed with 86 passes and one stochastic `AtomicGeometric` Metropolis-Hastings miss. The same test passed in the immediately preceding run, confirming that it is unsuitable as a deterministic CI gate rather than indicating a repeatable modernization regression.
- `ProbabilityRegressionTest` provides exact, non-sampling checks for a Bernoulli marginal, a Bayesian posterior under evidence, and a compound discrete marginal. CI pairs it with the parameter serialization regression.
- sbt's fixed package timestamp and sbt-assembly's repeatable entry ordering are explicitly pinned. CI compares SHA-256 checksums after a clean rebuild.
- CI builds the thin JAR, sources, API documentation, fat JAR, and local Maven publication, then generates a CycloneDX JSON SBOM from the assembled runtime.

The legacy full-suite failures and long-running learning example remain tracked work. They are not hidden by the focused stage-one gate.

## Stage 2: dependency replacement and upgrades

Dependency work begins only after the stage-one commit is stable. See `DEPENDENCIES.md` for the usage inventory and replacement order. Each dependency change must be isolated and tested against the deterministic probability and serialization gates before proceeding to the next library.

Completed dependency checkpoints:

- Removed unused direct ASM 3.3.1 and Prefuse beta-20071021 dependencies.
- Removed Breeze 0.13.1 after confirming its only source occurrence was an unused import. Its old netlib/ARPACK/Spire/Shapeless transitive graph is no longer part of runtime.
- Upgraded Apache Commons Math from 3.3 to 3.6.1 and replaced every production JSci call through a narrow `SpecialFunctions` boundary.
- Replaced JSci-based test oracles with Commons Math adapters that retain Figaro's variance, exponential-rate, and one-based geometric conventions. JSci and its obsolete XML/native-solver transitives are fully removed.
- All source sets compile. Thirteen focused numerical/probability/serialization checks and thirteen legacy deterministic density checks pass.
- Replaced the narrow Akka 2.4 actor surface with a JDK queue/worker. Lifecycle and query requests remain serialized and blocking; timeout configuration now uses Scala `FiniteDuration`.
- Upgraded Argonaut to 6.3.13 on its current `io.github.argonaut-io` coordinate; the five JSON serialization and round-trip fixtures remain green.

## Stage 3: Scala 2.13

Completed target:

- Scala 2.13.18
- `scala-parallel-collections` 1.2.0 for the four algorithms that retain parallel collection behavior
- Modernization version `5.0.0-modern.2-SNAPSHOT`

The migration updates the source for the Scala 2.13 collections redesign while preserving Figaro's public packages and inference behavior. The principal changes are explicit immutable `Seq` types where the API contract requires them, eager replacements for legacy `mapValues`, the 2.13 mutable-collection `addOne`/`subtractOne` protocol, and removal of obsolete `JavaConversions`, `Stack`, and builder imports.

Parallel belief propagation, particle filtering, and importance sampling now opt into the separately published parallel-collections module. Custom `MultiSet`, priority-map, selectable-set, and cache implementations retain their original behavior under the 2.13 collection contracts. Legacy tests that used ScalaTest's pre-2.13 `PrivateMethodTester` implementation now use direct calls where the method is public and narrow Java reflection where it is not.

One existing complex-`Dist` factor assertion depended on `Set` iteration order and indexed a one-value `Constant(false)` range with indices from a two-value result range. The test now identifies both tuple values and the constant's own range index explicitly; the production factor behavior is unchanged.

Validation evidence:

- All Figaro main, test, and example sources compile on JDK 17 with Scala 2.13.18.
- The required modernization, density, anytime lifecycle, custom collection/cache, factor, resampler, serialization, and deterministic parallel-structure selections pass. These checks cover 111 tests across the migration-sensitive surfaces.
- A wider parallel smoke run passed all 39 particle-filter and parallel-importance tests once. A repeat run passed 38/39: one untagged Monte Carlo evidence estimate produced `0.7087` against an expected `0.72 ± 0.01`. This is consistent with the stochastic-tolerance failures established on the JDK 8 baseline, so the full sampling suite is evidence rather than a required CI gate.
- CI builds and checksum-compares the Scala 2.13 thin and fat JARs after a clean rebuild, publishes sources and Scaladoc classifiers locally, and generates a CycloneDX SBOM from the assembled runtime.
- The complete legacy suite remains outside the required gate because its 22 known failures and heavyweight learning example were established on the untouched JDK 8 baseline before modernization.

## Stage 4: sbt 2 on the Scala 2.13 baseline

Date: 2026-09-05. Branch: `modernize/sbt-2`, based on the accepted `main` baseline `b3431027`.

Target: sbt 2.0.8, JDK 17, Scala 2.13.18. The library version, runtime dependencies, public API, and application/test sources remain unchanged. Scala 3 library migration is a separate stage; sbt's internal Scala 3.8.4 compiler only compiles the build definition.

Build changes:

- Use Scala 3 build imports and common settings, and scope the root name explicitly so sbt 2 does not rename every subproject.
- Convert legal-file package mappings to hashed virtual file references. Read external manifest and legal inputs through uncached tasks so cached packaging cannot retain stale input hashes.
- Preserve the explicit ZIP timestamp and repeatable assembly policy, and use the typed empty test result for assembly. The focused test gate remains a separate required CI step.
- Retain sbt-assembly 2.4.1 and sbt-scoverage 2.4.4, both of which publish sbt 2 artifacts.
- Align only the test-side `scala-xml` dependency to 2.4.0, excluding ScalaTest's transitive 1.2.0. sbt 2 rejects that older XML version alongside scoverage's reporter. A minimal sbt 1.13.0 comparison accepted the same original coverage dependency combination. No eviction warnings are suppressed.
- Update CI command sequences to a single quoted semicolon-separated string and artifact paths to `target/out/jvm/scala-2.13.18/figaro/`.
- Disable action-cache backends during the second reproducibility build with `set Global / cacheStores := Seq.empty`; an ordinary sbt 2 `clean` can restore previously built artifacts and is not sufficient proof of a fresh compilation.
- Preserve the forked application's subproject working directory explicitly.

Local verification:

- All 264 main, 167 test, and 37 example sources compile.
- The original 111-test migration selection passed once. A final repeat exposed a tagged legacy timing flake in `SelectableSetTest`: the selection-time ratio was 2.4608 against a 2.42 threshold. CI now separates the seven already-tagged collection performance checks into a visible advisory step, retaining all 104 functional checks as the required gate. No assertions or test sources are changed.
- The final 104-test functional gate passes with the aligned XML dependency and coverage disabled.
- Coverage instrumentation and the three exact probability tests pass, and XML/HTML/Cobertura reports are generated. This is a plugin smoke test, not a full-suite coverage measurement. Coverage is then disabled, outputs cleaned, and normal artifacts restored.
- Thin JAR, fat JAR, sources, Scaladoc, and isolated local publication succeed. All four JARs contain Figaro's license and attribution; neither test-only XML nor coverage runtime classes appear in the binary artifacts.
- A cached clean rebuild and a separate cache-bypassed fresh compilation produce identical thin and fat JARs. Thin SHA-256: `20C9EB19B4961112CD5FE2D38902159111D03E2953E72F5E265A0A08FEAD88E6`; fat SHA-256: `BEF7BCB600B96E96A287F31E178C8EA3FB67CE317B2F497C234F2EF078519F32`.
- The thin JAR is byte-for-byte identical to the sbt 1 baseline. Every shared entry in the old and new fat JARs has identical contents; the new fat JAR additionally includes Figaro's own legal entries because sbt 2 supplies the packaged project JAR on the assembly classpath.

Windows notes: a long temporary directory initially exceeded the worker IPC socket-path limit. A short `XDG_RUNTIME_DIR` resolved it without disabling test forking. Developer Mode is not required: sbt reports failed optional symbolic-link creation but the build and cache restoration succeed.

The known legacy full-suite failures remain unchanged in scope; this stage does not claim that the entire historical suite is green. The inherited OSGi manifests still contain legacy bundle metadata; OSGi deployment is not validated by this JVM migration gate.

References: [sbt 2 migration guide](https://www.scala-sbt.org/2.x/docs/en/changes/migrating-from-sbt-1.x.html), [cached tasks](https://www.scala-sbt.org/2.x/docs/en/reference/cached-task.html).

## Stage 5: Scala 3 library migration

Date: 2026-09-05. Branch: `modernize/scala-3`, based on the pushed sbt 2 checkpoint `b281f016`. The sbt 2 checkpoint's [GitHub Actions run](https://github.com/mattwilkinsphoto/figaro/actions/runs/33950767885) passed the required gates; the separate legacy timing advisory reported its known selectable-set timing failure.

Target: Scala 3.9.0 LTS, sbt 2.0.8, JDK 17, library version `6.0.0-modern.1-SNAPSHOT`. This is a Scala 3-only line with the `_3` artifact suffix. The prior Scala 2.13 artifact is not replaced in place and Scala consumers must recompile.

Migration decisions:

- Compile all library, example, and test sources with normal Scala 3 type checking. The temporary `-source:3.0-migration` flag is removed. `-no-indent` deliberately preserves the repository's brace-delimited syntax; adopting indentation syntax is not required to use Scala 3.
- Replace procedure syntax, symbol literals, and implicit empty-argument method calls. Align overridden method signatures, explicitly type priority-queue orderings and inherited learning parameters, and replace removed view bounds with `DistanceConversion[T]` context evidence.
- Replace Scala 2 runtime mirrors with JVM singleton lookup and direct calls to the existing `Creatable` contract. Unknown class names, invalid objects, and rejected arguments have regression coverage. Unused `TypeTag` constraints and the `scala-reflect` dependency are removed.
- Preserve the correlation between an element's value type and its weight-map key type using a generic `WeightSeen` wrapper. Explicitly widen heterogeneous, read-only joint-sampling inputs at the collection boundary. Check the boxed initialization sentinel through `Element.hasValue`, distinguishing missing values from valid `false` and `0` values.
- Represent heterogeneous query targets with `BaseProbQueryAlgorithm[Q, U[_] <: Q]` and `BaseProbQuerySampler[Q, U[_] <: Q]`. Standard specializations use `Element[?]` or `Reference[?]` for Q. Scala 3 cannot apply an existential wildcard directly to an abstract higher-kinded constructor.
- Use native Scala 3 Argonaut 6.3.13, Scala Swing 3.0.0, parallel collections 1.2.0, and ScalaTest 3.2.20. Test styles migrate to `AnyWordSpec` and `matchers.should.Matchers`; the previous test-only XML alignment is removed.
- Fix two contradictory dependent-factor test coordinates in the alternate variable-order branch (`xFalse` was asserted to have both 0.25 and 0.5 probability). Preserve the existing sampling budget and tolerance. Two chain-rendering tests now inspect the actual generated functions rather than assuming Scala 2's shared `<function1>`/`<function2>` descriptions. Production rendering is unchanged.
- Inspect compiler-generated rewrites for Windows CRLF effects: the compiler had moved a carriage return into some end-of-line symbol strings. Restore those original symbol names and enforce LF for Scala/build sources in `.gitattributes`.

Source/API notes for consumers: accessor-style members such as `isCachable`, `burnIn`, `interval`, `discretize`, and `fullyRefinable` are consistently parameterless; lifecycle and generation methods retain `()`. Abstract target/universe/bound getters permit lazy implementations. Custom subclasses of the two generic query bases or of samplers using the protected weight representation need source adaptation. Packages remain under `com.cra.figaro`; this is not a promise of Scala 2 binary or complete source compatibility.

Behavioral validation:

- All 264 main, 168 test, and 37 example sources compile.
- The required functional selections pass 119 tests, including 15 new Scala 3 regressions for dynamic creation, initialization, heterogeneous sampling, primitive/custom distance conversion, and flat/VP nearest-neighbor ordering.
- A broader 284-test selection passes across language/universe/reference behavior, decision utilities, structured ranges/raising, semirings, and sparse factors.
- One earlier required-gate run produced a Monte Carlo estimate of `0.48995` against `0.50 +/- 0.01` in the legacy dependent-factor test. The repeat passed without changing the tolerance or sampling budget. This remains a bounded sampling check, not a deterministic proof; the full historical suite is still not claimed green.

At this checkpoint, legacy deprecations remained visible rather than suppressed. The subsequent [deprecation-retirement stage](docs/DEPRECATION_RETIREMENT.md) replaces deprecated syntax and library usage and removes obsolete Figaro entry points. The inherited OSGi metadata remains unvalidated.

Artifact validation:

- Scala 3 coverage instrumentation passes the three exact probability fixtures and emits XML, HTML, and Cobertura reports. This is a coverage-plugin smoke check, not a full-suite coverage claim.
- A clean normal build compiles every source set and produces thin, fat, sources, and API-documentation JARs, plus isolated local publication. All four preserve the byte-identical Figaro license and attribution; assembly renames the license to `META-INF/LICENSE_figaro-6.0.0-modern.1-SNAPSHOT` to avoid third-party license collisions. Removed and test-only runtimes are absent from binary entries. The POM uses native `_3` dependencies and keeps ScalaTest test-scoped.
- A second clean compilation with `Global / cacheStores := Seq.empty` reproduces both binary JARs byte-for-byte. Thin SHA-256: `086E61E07BD7BF97B40DF2CC45FF72657B110803EE954E0FC37962798F3664D0`; fat SHA-256: `BF8AFD0D8624426C85E21782FFC94798CE6BFAFB68BE9169E88FD1BB3F297EA2`.

Windows build caveat: switching normal and coverage compilation inside one sbt JVM can leave the exported project JAR open and fail replacement with `AccessDeniedException`. Use a fresh sbt invocation for coverage, and another fresh invocation for `clean` plus normal packaging. This does not require disabling forked tests, changing file ownership, or granting administrator membership.

References: [Scala 3.9.0 LTS download](https://www.scala-lang.org/download/), [runtime compatibility](https://docs.scala-lang.org/scala3/guides/migration/compatibility-runtime.html), [migration-mode tooling](https://docs.scala-lang.org/scala3/guides/migration/tooling-migration-mode.html), [higher-kinded wildcard restriction](https://docs.scala-lang.org/scala3/reference/error-codes/E043.html).

## Stage 6: deprecation retirement

The [deprecation-retirement guide](docs/DEPRECATION_RETIREMENT.md) records source/API replacements and verification at `55adc816`. Its [required CI run](https://github.com/mattwilkinsphoto/figaro/actions/runs/33997021717) passed; the separate legacy collection-timing advisory retained a failure. This is the parent checkpoint for the performance work.

## Stage 7: parallel Monte Carlo performance

Branch: `modernize/parallel-performance`; snapshot: `6.0.0-modern.3-SNAPSHOT`. The [performance guide](docs/PARALLEL_PERFORMANCE.md) provides the public contracts, runnable quick start, complete metric definitions, and [sanitized measured rounds](docs/performance-results.csv).

The opt-in `ParImportance.seeded` factory uses bounded worker execution, worker-owned universes and RNG streams, exact sample-budget partitioning, and weight-aware aggregation. Thread scopes restore RNG/default-universe state on exit. Public child lifecycle delegation now preserves activation and importance-cache deregistration. Cooperative cancellation includes rejected-sample retries; factory failures undo registrations already created by that factory. Uninterruptible callbacks, arbitrary shared model state, and other inference algorithms remain outside the isolation guarantee.

Profiling and repeated workload grids support removing shared-RNG contention, but scaling is model-dependent: the final Gaussian grid improves eight-worker sampling from 464.65 ms (legacy median) to 281.34 ms (scoped median); simple coin models plateau near two workers. Independent MH chains are benchmarked without parallelizing dependent chain steps or introducing a general multi-chain API. Allocation/traversal hot spots are the next measured candidates.

Broader statistical check: an initial 233-test selection passed 230 tests, with two legacy importance estimates outside tolerance and a learning-statistics failure. Both importance failures passed isolated repeats without changed tolerances or budgets. `EMWithImportanceTest` failed again on a different learned-statistic check; this stage does not certify that learning test or claim every historical failure is unchanged. The new parallel regression suite passes 16 tests, including nested/global RNG compatibility, scoped callbacks and dynamic models, budgeting, weighted estimates, lifecycle cleanup, construction failures, and interruption of endless rejection.

The maintained 143-test acceptance selection, three user examples, 12 documentation-tool tests, generated-reference freshness, and local links pass. The 19 probability/parallel tests also pass under coverage instrumentation. A fresh non-instrumented build produces thin/fat/source/API JARs with legal entries preserved and no test/coverage runtime in the binary artifacts; isolated local publication succeeds. Required CI repeats acceptance and verifies byte-for-byte fresh binary rebuilds before merge.

The first Linux CI run exposed a shutdown-order race: `ThreadPoolExecutor.awaitTermination` can return after its final worker signals executor termination but before that thread exits. Seeded shutdown now also joins its explicitly tracked worker threads within the same 30-second shutdown budget. The strict no-surviving-worker assertion is retained; no sleep or relaxed assertion is used to conceal the race.

## Stage 8: multi-chain MCMC

Branch: `modernize/multi-chain-mcmc`, based on the CI-green `439f0644` parallel checkpoint; snapshot: `6.0.0-modern.4-SNAPSHOT`. This branch includes the approved, expanded importance-user documentation. The [multi-chain guide](docs/MULTI_CHAIN_MCMC.md) provides overview, quick start, public contracts, three common workflows, evidence conversion, diagnostic interpretation, ownership audit boundaries, and reviewed benchmark data.

The opt-in runner owns a supplied universe, model, proposal state, cache, and RNG for each independent chain. Chain count and retained draws per chain are distinct from pool size. It retains aligned finite scalar traces, including rejected states, after bounded initialization and discarded warm-up. It reuses the existing MH transition kernel without the ordinary sampler's query histograms/per-draw target maps. Conditions and explicit likelihood constraints are supported; `observe()` is deliberately rejected rather than inheriting unvalidated observation behavior. This is an additive scoped API, not a general shared-graph thread-safety retrofit.

Completion-queue failure propagation interrupts sibling workers promptly. Shutdown awaits executor termination and explicitly joins owned threads; constructed-but-queued models are disposed too. Caller interruption is preserved, and secondary cleanup failures are suppressed onto the primary error. Arbitrary callbacks must cooperate: an uninterruptible worker can outlive a failed 30-second shutdown. Nested inference, factored/particle global caches, learning/filtering, and shared external callback state remain outside the audited path.

Diagnostics include rank-normalized/folded split R-hat, bulk/tail ESS, raw-scale mean ESS, and mean MCSE, with unavailable values and explicit warnings. ESS uses FFT autocovariances and classical positive/monotone paired truncation, conservatively capped at split draw count; this differs from posterior/Stan's improved antithetic implementation. Independent fixtures exposed and fixed rounded constant-chain variance and folded-rank tie perturbation; binary scaling and symmetric median-distance evaluation preserve those cases. There is no automatic convergence certificate.

The four-chain benchmark fixes 20,000 draws per chain and 2,000 warm-up transitions while varying workers. Five measured rounds after two warm-ups show median one-to-four-worker end-to-end gains of 1.21x (normal), 1.42x (likelihood), 1.81x (correlated), and 1.37x (wide). Diagnostics and cleanup are included. The correlated workload has R-hat as high as 1.35: its faster execution is not a statistical-quality success. The [CSV and methodology](docs/MULTI_CHAIN_MCMC.md#measured-checkpoint) retain that counterexample. These results do not claim speedup over ordinary MH at equal concurrency.

Local verification: 27 new regressions pass; the broader acceptance selection passes 185 tests including extra cache suites. Thirty probability/MCMC tests pass under coverage instrumentation (16.47% whole-library statement coverage for that smoke selection). Documentation snippets compile/run, 12 tooling tests pass, and all 11,263 generated public-method entries and local link targets verify. Thin/fat/source/API-documentation JARs preserve byte-identical legal files, and binary artifacts exclude test/coverage runtimes. Two fully clean cache-bypassed builds reproduce all four publication JARs; isolated local publication succeeds. Thin SHA-256: `0C48DC7B6D92526A026ED37506A6A1D774BF88546559CF7D7F2F33D3D6DD8EF0`; fat SHA-256: `14346814DDB04CDC1EED3EB58C33E283EB0F2A7E80D3E103126900117BDA86A3`. Required Linux CI is the final branch gate; the historical full-suite limitations remain explicitly documented.

## Stage 9: explicit stopping policies

Branch: `modernize/stopping-criteria`, based on the multi-chain milestone; snapshot: `6.0.0-modern.5-SNAPSHOT`. The [stopping guide](docs/STOPPING_CRITERIA.md) documents the corrected Gaussian truncated SPRT, explicit false-alarm/miss conventions, KL utility, and separate opt-in scalar-mean precision policy for persistent multi-chain MH. Budget exhaustion is an unsuccessful precision outcome, not convergence.

The [representative validation report](docs/STOPPING_VALIDATION.md) records 50 paired seeds across seven analytic-target fixtures, with raw data and accuracy/coverage/runtime comparisons. Well-mixing cases save about 83% of retained draws at the chosen tolerance; correlated and trapped cases never report precision success and incur checkpoint overhead. These are limited empirical results, not universal error-rate or speedup guarantees. KL-driven automatic MCMC termination remains outside the supported policy.

CI includes stopping regressions, runnable examples, and a representative-model smoke test. The existing documentation, coverage, packaging, publication, and clean-rebuild reproducibility gates remain in place.

## Stage 10: fixed-covariance Gaussian blocks

Branch: `modernize/blocked-proposals`, based on `731ac977`; snapshot: `6.0.0-modern.6-SNAPSHOT`. The [block-proposal guide](docs/BLOCKED_PROPOSALS.md) documents an opt-in symmetric random walk for permanent constant-parameter Normals, including the prior-density correction, joint evidence updates, and rejection rollback. Defaults and toolchain versions remain unchanged. Online adaptation and arbitrary element types are intentionally outside this milestone.

The [50-seed comparison](docs/BLOCKED_PROPOSAL_VALIDATION.md) includes existing joint prior proposals, diagonal random walks, aligned blocks, and separated-mode counterexamples. The informed aligned block improves median ESS/s over existing joint prior resampling by about 1.72x on the correlated fixture and halves time to the selected precision target. Independent-Normal and multimodal examples demonstrate that blocking can be worse. These are model-specific empirical results, not general speedup or finite-sample coverage guarantees.

CI adds a dedicated block regression gate, all three documented patterns, a two-worker benchmark smoke run, and block regressions under coverage instrumentation. The original stopping and reproducibility gates remain required.

## Stage 11: separate pilot proposal calibration

Branch: `modernize/proposal-calibration`, based on the merged `9b404420` multi-chain baseline; snapshot: `6.0.0-modern.7-SNAPSHOT`. The [calibration guide](docs/PROPOSAL_CALIBRATION.md) documents a detached fit from discarded raw-value pilot traces, recomputed scalar mixing checks, explicitly regularized within-chain covariance, and name-based binding to fresh production elements. Production proposals remain frozen, and existing sampler defaults/execution paths are unchanged. Pilot exclusion and independent production setup remain explicit caller responsibilities.

The [30-seed broader-geometry report](docs/PROPOSAL_CALIBRATION_VALIDATION.md) includes 1,440 run groups, analytic first/second moments, existing default/joint-prior proposals, a manual oracle comparator, and pilot-inclusive timing. Easy cases favored existing proposals. All six-dimensional pilots and 19/30 narrow-target pilots were rejected. Curved-target interval undercoverage persisted even when precision checks passed; calibration does not close that gap or certify covariance accuracy. The feature remains opt-in, not an automatic arbitrary-model tuning workflow.

All 113 modernization regressions passed locally, including 11 calibration regressions. Three benchmark-summary tests enforce complete reports and rejection denominators. CI adds the calibration regression/example/benchmark gates and instrumentation coverage, retaining documentation freshness and clean-rebuild reproducibility checks. Broader pilot strategies, curved/multimodal exploration, diagnostic reliability/cost, and trace-memory pressure remain future work.

## Stage 12: MCMC reliability safeguards

Branch: `modernize/mcmc-reliability`, based on merged multi-chain checkpoint `795d4dde`; snapshot: `6.0.0-modern.8-SNAPSHOT`. The precision policy now uses the maximum of its existing batch-means and raw-mean ESS-based MCSE estimates, requiring both to be valid. Named failure reasons and `mcseUsed` expose the decision. No proposal, calibration, fixed-sampling, RNG, or toolchain changes are included. Assessment arity and stopping semantics require the [documented consumer migration](docs/MIGRATION.md).

The [60-seed paired audit](docs/MCMC_RELIABILITY_VALIDATION.md) compares six strategies, five analytic observables, fixed budgets through 48000 draws per chain, and both stopping rules on identical prefixes. All 360 attempted strategy/seed experiments are accounted for, including 12 rejected pilots. Independent and reparameterized target controls support the target algebra and error-scale checks. A direct target/proposal log-ratio regression confirms the difficult tail transition's MH correction and rejection rollback.

The safeguard improves fixed-prefix widths but does not solve hard-target exploration or selected-stop coverage. In particular, only 5/34 default-proposal successes and 18/54 joint-prior successes jointly covered all five truths under the new rule. Those limitations remain explicit in the user guide; this milestone is not general MCMC reliability certification. Geometry-aware proposals/reparameterization and broader stress tests remain follow-on research, not benefits implied by more threads.

All 121 modernization tests passed locally, including eight new reliability regressions. Five new Python summary tests verify complete paired reports, corrupt records, and rejection accounting. CI adds the reliability example, a complete one-seed audit smoke check, checked-data validation, and instrumentation coverage; existing reproducibility/publication gates remain required.

## Stage 13: recent sampling research and isolated prototypes

Branch: `modernize/sampling-research`, based on `0c8732b7`. The production modern.8 library and toolchain are unchanged. The [research report](docs/SAMPLING_RESEARCH.md) screens recent quantile, multiproposal/adaptive elliptical, polar/affine, transport, and RQMC methods, with primary references and implementation boundaries.

Independent Scala example-only prototypes implement uniform MESS and fixed-Cauchy quantile slice sampling, with an M=1 elliptical baseline and actual Figaro Gaussian-block comparator. No upstream sampler code or dependencies were imported. All 450 target/sampler/seed experiments completed: 30 seeds, three analytic targets, five methods, four chains, 12000 retained draws per chain. Quantile sampling improved banana stopped coverage and second-moment error, but required about 5.4 times as many density evaluations as the block comparator. Elliptical methods recovered unequal mode weights, but did not resolve curved-target undercoverage; higher proposal counts were not a consistent cost-normalized improvement.

Kernel controls, complete-data validation, and three report-tool tests are CI-gated. This is a research checkpoint, not a production sampler release or an equal-budget speedup claim. Broader matched-computation validation and graph/lifecycle integration remain separate decisions.

## Stage 14: matched-budget affine/polar validation

Branch: `modernize/sampling-budget-validation`. The [protocol](docs/SAMPLING_BUDGET_VALIDATION.md) and kernels were recorded at `0b3fa903` before the study. All 600 experiments completed (30 fresh seed labels, five targets, four methods, four chains each), consuming 240 million target evaluations including pilot/warm-up. The new GPSS and independently piloted finite affine variant remain private, two-dimensional example kernels; production modern.8 code, defaults, toolchain, and dependencies are unchanged.

Equal-budget results do not identify a universal replacement. Plain GPSS is the most balanced candidate in this screen. Affine GPSS substantially reduces rotated-Gaussian error but regresses on unequal modes (only 10/12 precision successes jointly cover all truths). Quantile sampling remains strong on heavy tails and separated modes but struggles with strong correlation; its earlier draw-matched banana advantage over Metropolis does not persist here. The different standalone comparator/protocol is documented. These are target-evaluation comparisons, not thread or wall-clock speedup claims.

The milestone adds analytic stationarity/cross-moment controls, hard-cap/prefix replay and failure/interruption checks, a complete 12000-row checked dataset, four Python validator tests, and a CI smoke experiment. Higher-dimensional validation, pilot-quality safeguards, and production graph/lifecycle integration remain outstanding.

## Stage 15: higher-dimensional sampling validation

Branch: `modernize/sampling-high-dimensional`. The [protocol and results](docs/SAMPLING_HIGH_DIMENSIONAL.md) extend plain GPSS and quantile sampling to eight and 32 dimensions. The protocol and verified kernel were committed at `8d9cca12` before 480 experiments across six analytic targets, 20 fresh seed groups, and four chains per experiment. All completed without execution failures, consuming 576 million density evaluations including initialization and warm-up. The checked 11520-row dataset retains all three fixed checkpoints and selected stops.

GPSS reduces Gaussian and heavy-tailed estimation error at matched target-call budgets; quantile is substantially stronger on positive-constrained targets. Neither method achieves precision on the 32-dimensional correlated or banana fixtures, and both have zero joint coverage and zero precision successes on the 32-dimensional asymmetric mixture. These findings do not certify nominal coverage, establish wall-clock speedups, or compare against higher-dimensional production Metropolis. Production modern.8 APIs, defaults, dependencies, and toolchain are unchanged.

Controls cover tangent geometry, independent one-step stationarity, analytic marginal/aggregate/cross/event quantities, support, budget truncation, seeded replay, failure, and interruption. Four new report tests enforce complete records and earliest-success replay; CI adds the controls, checked-data validation, and a complete low-budget smoke run. A CI startup failure preceding compilation was separately traced to the sbt thin-client connection path. Direct `sbt --server --batch` invocations select the foreground runner; putting those switches in `SBT_OPTS` did not work with the CI runner. See [build troubleshooting](docs/BUILDING.md).

The next proposed boundary is an explicitly opt-in continuous-vector interface with audited lifecycle and independent implementation checks, not automatic integration into arbitrary graphs. Constraint transformations, preconditioning, and multimodal exploration remain distinct follow-on work.

## Stage 16: opt-in continuous-vector sampler

Branch: `modernize/continuous-vector-sampling`, based on the CI-green `7d2e72c7` research checkpoint; snapshot: `6.0.0-modern.9-SNAPSHOT`. The [user guide](docs/VECTOR_SLICE_SAMPLING.md) documents `VectorSliceSampler.run`: a blocking single-chain GPSS/quantile interface for an explicit immutable-vector log density. Method selection, initial point, seed, discarded warm-up, requested draws, density-call cap, proposal-search cap, and storage limit are explicit. No graph adapter, shared-state retrofit, executor, adaptation, or automatic precision stopping is introduced. Existing inference/defaults, reliability policies, dependencies, and toolchain are unchanged.

All density calls, including initialization and unfinished work, count toward the cap. Only complete transitions enter detached traces; a partial quantile sweep never leaks coordinate updates into the returned state. Budget exhaustion is distinct from requested-draw completion, and neither certifies convergence. Model exceptions propagate unchanged; numerical/search failures throw without fallback samples. Cooperative interruption preserves the calling thread's flag. Callbacks and their resources remain caller-owned.

Independent checks compare GPSS with a planar angle/determinant implementation on Gaussian, correlated, and banana targets, and quantile reference-target draws with Commons Math inverse-CDF values. Exact-start Gaussian/exponential controls cover eight and 32 dimensions. These are specialized implementation/analytic checks, not third-party cross-language certification or proof of exploration. Regression coverage also checks limits, incomplete-prefix accounting, warm-up, deterministic concurrent/nested calls, and cancellation. Three runnable workflows compare explicit-density usage with the existing graph-based approach. CI adds the new regression/example gates and coverage instrumentation, retaining packaging, publication, and clean-rebuild reproducibility checks.

Local compilation and all 132 modernization regressions pass, including 11 new vector-sampler groups. All three workflows execute; 31 report/documentation-tool tests, 11299 generated public-method entries, and local links verify. This is not a full historical-suite success claim or a new end-to-end performance benchmark.

## Stage 17: bounded multi-chain vector orchestration

Branch: `modernize/multi-chain-vector-sampling`, based on CI-green `719c3be8`; snapshot: `6.0.0-modern.10-SNAPSHOT`. The [multi-chain vector guide](docs/MULTI_CHAIN_VECTOR_SAMPLING.md) documents an additive wrapper around the unchanged `VectorSliceSampler.run`. Serial model construction receives deterministic index-ordered seeds, a private bounded pool executes independent chains, and the existing diagnostic implementation summarizes every coordinate. Chain count, worker count, per-chain budgets, and aggregate trace storage are distinct. There is no new kernel, graph adapter, automatic precision stopping, or default/dependency/toolchain change.

Complete single-chain output is preserved, including evaluation-capped chains. Diagnostics use the shortest common prefix, with explicit alignment/insufficient-trace warnings; no chain or excess sample is removed from returned accounting. Failure collection uses completion order so a later failed chain is not hidden behind an earlier blocked chain. Sibling tasks are interrupted and executor termination plus thread joins share one bounded shutdown deadline. Repeated caller interruption does not skip cleanup; the interrupt flag is restored. An uncooperative callback can outlive a failed shutdown, and cleanup failures remain visible without masking the primary error. Callback resources remain caller-owned.

Regression tests compare results exactly with direct single-chain calls across worker counts, check seed assignment and independent coordinate summaries, exercise factory/storage validation, budget alignment, failure propagation, nested runs, and cooperative/uncooperative/repeated-interruption shutdown. Runnable workflows cover bounded Gaussian chains, positive-target caps, and separately diagnosed derived events. No wall-clock speedup, arbitrary-model mixing, or full historical-suite guarantee is inferred from these execution-contract checks.

Local compilation, all 143 modernization regressions including 11 new orchestration groups, the three workflows, and 31 documentation/report-tool tests pass. CI adds regression/example execution and coverage instrumentation while retaining publication and reproducible-rebuild gates.

All 11321 generated public-method entries and local links verify. Thin/fat/source/API-documentation artifacts preserve byte-identical legal entries, binary artifacts exclude test/coverage runtimes, and isolated local publication succeeds. Required Linux CI remains the final branch gate.

## Stage 18: fixed-trace vector scheduling measurements

Branch: `modernize/vector-sampling-performance`, based on `92e3b646`; snapshot modern.10 and toolchain remain unchanged. The [protocol and results](docs/VECTOR_SAMPLING_PERFORMANCE.md) record 252 complete runs, including 180 measured runs: six fixtures, two methods, worker counts 1/2/4, four fixed chains, two JVM warm-up rounds and five measured rounds. Protocol/instrumentation commit `46df612b` precedes measurement. Package-private timing separates construction, sampling/joined shutdown, and diagnostics without changing public result types or kernels. Full trace/diagnostic fingerprints agree across worker counts.

Four-worker end-to-end gains range from 1.04x on cheap Gaussian GPSS to 2.18x on dense-likelihood quantile sampling. The latter's sampling phase improves 3.74x; serial diagnostics limit total gain. Four-worker Gaussian GPSS spends about 88-89% of elapsed time in diagnostics. All target errors and warnings remain visible: three quantile mixture rounds have R-hat below 1.001 and no coordinate warnings while coordinate mean error is about 4.50. Apparent ESS/s is not reliable evidence of correct global exploration in those cases.

The study adds complete-record/fingerprint/phase validation, four report-tool tests, a timing-partition regression, and a CI smoke grid. All 144 modernization regressions and 35 documentation/report-tool tests pass locally. Bounded coordinate-diagnostic parallelism is the next recommended performance milestone; it must preserve exact statistics and lifecycle guarantees. This checkpoint introduces no performance optimization or default change.

## Stage 19: bounded parallel coordinate diagnostics

Branch: `modernize/parallel-vector-diagnostics`, based on `b4d26d97`; snapshot modern.10,
public signatures/default values, kernels and toolchain remain unchanged. The existing
vector runner's `parallelism` now also bounds coordinate diagnostics after sampling
workers exit: at most `min(dimension, chains, parallelism)` scratch owners, with one
task per worker rather than per coordinate. One worker keeps diagnostics on the caller.
Independent summaries retain exact coordinate order, aligned prefixes and warnings.
Cleanup joins owned workers; failures cancel siblings without returning partial success.
Scalar diagnostic calculations now check interruption between stages and in rank/ESS loops.
See [usage, lifecycle, memory tradeoffs and results](docs/PARALLEL_VECTOR_DIAGNOSTICS.md).

Implementation/protocol commit `829b36e5` precedes the unchanged 252-run grid. Every
non-timing field and trace/diagnostic fingerprint matches the baseline. Four-worker
diagnostic speedups are 2.12-2.57x and end-to-end gains over the old four-worker runner
are 1.28-2.21x. Dense-likelihood quantile now scales 3.26x from one to four workers.
Separate JVM invocations and uncontrolled desktop/GC conditions limit causal attribution;
the one-worker positive quantile timing regression is retained and explicitly reported.
Faster summaries do not repair the unchanged mixture/mixing counterexamples. Allocation
and memory-bandwidth profiling, not further unmeasured worker increases, is the next
performance investigation.

All 150 modernization tests, 37 documentation/report-tool tests, 108 smoke-grid runs,
three vector workflows and four legacy anytime tests pass locally. The benchmark's
checked data and cross-revision equality join the CI gates. The previous branch's CI
failed in the legacy anytime step despite passing vector/documentation checks; local
success does not diagnose or resolve that remote failure. No gates are removed or weakened.

## Stage 20: fixed-work allocation and GC profile

Branch: `modernize/vector-allocation-profile`, based on `0e2456d8`. The previous
parallel-diagnostic branch's [required CI passed](https://github.com/mattwilkinsphoto/figaro/actions/runs/34055899573).
The production library, snapshot modern.10, public library API, estimator, defaults and
toolchain remain unchanged. An example-only JDK Flight Recorder wrapper and standard-library
report validator capture a fixed workload with explicit recording ownership and sanitized
aggregates; raw recordings are ignored by Git and remain local. See the
[protocol, complete datasets, API and findings](docs/VECTOR_ALLOCATION_PROFILE.md).

Protocol commit `3b267dfc` precedes all 252 full-grid profiled runs. Every non-timing
benchmark result matches the preceding unprofiled checkpoint. There are 30117 allocation
samples, 5857 Java execution samples, zero reported lost bytes and a 105.226-second event
span. Diagnostics represent 39.25% of allocation weight and 84.09% of Java execution
samples; boxed doubles dominate diagnostic allocations. Mean/variance reduction sites
represent about 15% of both totals. FFT/complex conversions and rank sorting are other
material candidates. Sampler callback attribution includes benchmark density code.

GC reports 586 collections and 1.674 seconds of summed pauses (1.59% of event span),
with a longest pause of 17.905 ms. These are not total collector CPU cost, exact allocation
accounting, process RSS or proof of memory-bandwidth saturation. Direct DRAM/cache/NUMA
counter measurement remains unavailable. The next proposed experiment is primitive
diagnostic reductions preserving existing arithmetic/ordering/cancellation, followed by
unchanged-trace validation, profiling and independent unprofiled measurement. No new
performance gain or reliability improvement is claimed by this profiling checkpoint.

Compilation, all 150 modernization regressions, 41 documentation/report-tool tests,
the 108-run profiling smoke grid, complete checked datasets, reference freshness and
local links pass. An existing recording is protected by exclusive creation and a verified
unchanged SHA-256 on attempted reuse. CI adds profiler smoke/data-validation gates;
raw recordings and local access details are not committed.
