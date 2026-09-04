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

Target: Scala 2.13.18 after the dependency surface is made compatible. This is a separate source-migration stage, not part of the Java 17 build-tool commit.
