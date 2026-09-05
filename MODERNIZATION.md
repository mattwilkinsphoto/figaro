# Figaro modernization log

This log records decisions, compatibility findings, risks, and test evidence for the modernization of the Charles River Analytics Figaro codebase. The original license and `FigaroAttributions.txt` remain authoritative and must be preserved.

## Repository and provenance

- Upstream: <https://github.com/charles-river-analytics/figaro>
- Modernization origin: <https://github.com/mattwilkinsphoto/figaro>
- Local repository: `E:\Users\mpwil\Documents\Figaro\figaro`
- Traffic software boundary: `E:\Users\mpwil\Documents\Easement` is a separate repository and is not modified by this work.
- Upstream baseline commit: `14f48d148f715017211822e31b7ea3291733fefe` (2022-06-01).

The GitHub origin began as an empty standalone public repository rather than a GitHub fork. It is seeded from the exact upstream history, and the local checkout retains a separate `upstream` remote so provenance and future synchronization remain explicit.

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

Windows notes: the isolated long temporary directory initially exceeded the worker IPC socket-path limit. A short, task-local `XDG_RUNTIME_DIR` resolved it without disabling test forking. Developer Mode is not required: sbt reports failed optional symbolic-link creation but the build and cache restoration succeed. Generated items receive explicit Full Control for the non-administrator `MATT-DESKTOP\astroman97` account.

The known legacy full-suite failures remain unchanged in scope; this stage does not claim that the entire historical suite is green. The inherited OSGi manifests still contain legacy bundle metadata; OSGi deployment is not validated by this JVM migration gate.

References: [sbt 2 migration guide](https://www.scala-sbt.org/2.x/docs/en/changes/migrating-from-sbt-1.x.html), [cached tasks](https://www.scala-sbt.org/2.x/docs/en/reference/cached-task.html).
