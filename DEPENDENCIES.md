# Dependency modernization inventory

This inventory separates build-tool migration from runtime dependency changes. Versions listed as "legacy" are intentionally retained during the first Java 17 / Scala 2.12.21 build pass.

## Current Scala 3 line

The Scala 3.9.0 LTS build uses native `_3` artifacts for Argonaut 6.3.13, Scala Swing 3.0.0, parallel collections 1.2.0, and ScalaTest 3.2.20 (test only). Commons Math remains 3.6.1. Scala 2 runtime reflection has been removed: dynamic creation resolves the JVM singleton and invokes Figaro's `Creatable` interface directly. Unused `TypeTag` bounds are gone.

ScalaTest now uses `AnyWordSpec` and `matchers.should.Matchers`; the temporary ScalaTest 3.1 XML exclusion/alignment from the sbt 2 checkpoint is no longer needed. The historical inventory below records how the previous checkpoints were reached.

| Dependency | Legacy version | Observed source surface | Initial remediation decision |
| --- | ---: | --- | --- |
| JSci | 1.2 | Special functions and factorial/binomial helpers in main code; statistical distribution oracles in legacy tests; pulls very old XML and lpsolve transitive artifacts | Removed completely. Main code uses a narrow Commons Math boundary with fixed numerical regressions; test-only adapters preserve legacy variance/rate/support conventions on Commons Math. |
| Akka actor | 2.4.18 | Concentrated in the `Anytime` actor runner plus six ask/timeout wrappers; one test timeout import | Removed. A dedicated JDK queue/worker preserves serialized steps, blocking lifecycle/query calls, timeout handling, and the existing response protocol without an actor runtime. |
| Breeze | 0.13.1 | One unused import in experimental particle belief propagation; no call sites | Removed. This also eliminates the old netlib, ARPACK, Spire, Shapeless, JTransforms, OpenCSV, and SLF4J transitive graph from the runtime artifact. |
| Argonaut | 6.2 | Model-parameter JSON codecs in three main files and one serialization test | Upgraded to the actively published `io.github.argonaut-io` 6.3.13 line, which supports Scala 2.12, 2.13, and 3. The existing JSON shape and round-trip fixtures remain the compatibility contract. |
| Prefuse | beta-20071021 | No direct Scala, Java, resource, or reflection references found | Removed after complete source search and Java 17 compile/regression validation. |
| ASM | 3.3.1 | No direct Scala, Java, resource, or reflection references found | Removed completely with the unused ScalaMeter dependency. |
| Commons Math | 3.3 | Used directly and is the JSci replacement | Upgraded to 3.6.1. `SpecialFunctionsRegressionTest` fixes representative gamma, log-gamma, beta, erf, factorial, log-factorial, and binomial values. |
| Scala Swing | 2.0.0 | UI capability in main sources | Upgraded to 2.1.1, the first stable line published for both Scala 2.12 and 2.13. A future module split can make the desktop UI optional without coupling that structural change to the language migration. |
| ScalaMeter | 0.8.2 | Build registration only; no benchmark source imports or suites exist | Removed completely. Figaro's named `FactorPerformanceTest` is a ScalaTest functional suite and does not use ScalaMeter. |
| ScalaTest | 3.0.3 | 164 legacy test sources using the pre-3.2 package style | Upgraded to 3.1.0, the first stable release published for Scala 2.13 while retaining the legacy `WordSpec` and `Matchers` aliases. |

## Test architecture risks

The sbt 2 migration keeps all runtime dependencies and ScalaTest 3.1.0 fixed. Its test-only `scala-xml` dependency is explicitly aligned to 2.4.0 (excluding ScalaTest's transitive 1.2.0) to coexist with sbt-scoverage 2.4.4's reporter. This resolves sbt 2's coverage-mode eviction error without relaxing dependency compatibility checks or adding XML to Figaro's runtime dependency graph.

- Default `test` currently mixes deterministic unit tests, stochastic checks, performance assertions, and heavyweight book examples.
- Several probability assertions use exact `Double` equality.
- Stochastic tests do not consistently expose or pin random seeds.
- The first modernization gate should compile all sources, run a bounded deterministic smoke/regression set, and report legacy failures separately.

## Planned order

1. Make the existing source compile on Java 17, Scala 2.12.21, and sbt 1.13.0 without runtime dependency upgrades.
2. Add deterministic probability-output regression fixtures and separate heavyweight/stochastic suites.
3. Remove or replace unused and narrow dependencies (Prefuse, ASM, Breeze use, then Akka).
4. Replace JSci with tested Commons Math equivalents and upgrade Commons Math.
5. Freeze and modernize serialization.
6. Add Scala 2.13.18 as a distinct migration stage. Completed with the external parallel-collections module and focused collection, factor, cache, and parallel-algorithm regressions.

## Completed dependency reductions

The first dependency-only checkpoint removes ASM 3.3.1, Prefuse beta-20071021, and Breeze 0.13.1. No implementation replacement was required: ASM and Prefuse had no references, while the sole Breeze occurrence was an unused import. The checkpoint is accepted only if all source sets compile and the deterministic probability/serialization gate remains green.

The second checkpoint upgrades Apache Commons Math from 3.3 to 3.6.1 and routes all production special-function/combinatorics calls through `com.cra.figaro.util.SpecialFunctions`. Test-only distribution adapters preserve the legacy tests' parameter conventions. JSci and its obsolete XML and native-solver transitives are absent from compile, test, the published POM, and the assembled runtime.

The third checkpoint replaces Akka 2.4.18 with a JDK `LinkedBlockingQueue`, `CompletableFuture`, and one daemon worker thread per active anytime algorithm. `messageTimeout` is now a standard Scala `FiniteDuration`; callers use values such as `5.seconds`. The service/response types and serialized query behavior remain intact.

The fourth checkpoint upgrades Argonaut from the old `io.argonaut` 6.2 coordinate to `io.github.argonaut-io` 6.3.13. This keeps the small existing codec surface while moving onto an actively published release line with Scala 2.13 support.

The fifth checkpoint removes ScalaMeter 0.8.2 after confirming that no benchmark suites use it. Scala Swing moves to 2.1.1 and ScalaTest to 3.1.0, keeping existing APIs source-compatible while making every remaining Scala dependency available for Scala 2.13.

The sixth checkpoint moves the build to Scala 2.13.18 and adds `scala-parallel-collections` 1.2.0. This dependency is required only because four existing algorithms use `.par`; it replaces functionality removed from the 2.13 standard library without changing those algorithms' public API. ScalaTest remains on 3.1.0 for the legacy `WordSpec`/`Matchers` source aliases; its broken private-method name extraction on Scala 2.13 is removed from the three affected tests.
