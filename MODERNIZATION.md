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
