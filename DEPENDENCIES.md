# Dependency modernization inventory

This inventory separates build-tool migration from runtime dependency changes. Versions listed as "legacy" are intentionally retained during the first Java 17 / Scala 2.12.21 build pass.

| Dependency | Legacy version | Observed source surface | Initial remediation decision |
| --- | ---: | --- | --- |
| JSci | 1.2 | Special functions and factorial/binomial helpers in 9 main files; statistical distributions in tests; pulls very old XML and lpsolve transitive artifacts | Replace main-code math with Apache Commons Math equivalents behind regression tests, then remove JSci and its transitives. |
| Akka actor | 2.4.18 | Concentrated in the `Anytime` actor runner plus six ask/timeout wrappers; one test import | Preserve behavior during the build migration. Then replace the limited actor/ask lifecycle with JDK concurrency or isolate it behind an optional module before considering any Akka upgrade. |
| Breeze | 0.13.1 | One unused import in experimental particle belief propagation; no call sites | Removed. This also eliminates the old netlib, ARPACK, Spire, Shapeless, JTransforms, OpenCSV, and SLF4J transitive graph from the runtime artifact. |
| Argonaut | 6.2 | Model-parameter JSON codecs in three main files and one serialization test | Freeze JSON round-trip and compatibility fixtures before upgrading or replacing. |
| Prefuse | beta-20071021 | No direct Scala, Java, resource, or reflection references found | Removed after complete source search and Java 17 compile/regression validation. |
| ASM | 3.3.1 | No direct Scala, Java, resource, or reflection references found | Removed as a direct runtime dependency. A newer ASM may remain test-only through ScalaMeter until benchmark tooling is modernized. |
| Commons Math | 3.3 | Used directly and is a likely JSci replacement | Upgrade separately to 3.6.1 with density/CDF and probability-output regression tests. |
| Scala Swing | 2.0.0 | UI/example capability | Keep out of the core artifact if feasible; evaluate a separate optional module. |
| ScalaMeter | 0.8.2 | Benchmark test framework | Move benchmarks out of the default unit-test gate and upgrade or replace separately. |
| ScalaTest | 3.0.3 | 164 legacy test sources | Retain for the first 2.12 build; migrate test APIs before the Scala 2.13 stage if required. |

## Test architecture risks

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
6. Add Scala 2.13.18 as a distinct migration stage.

## Completed dependency reductions

The first dependency-only checkpoint removes ASM 3.3.1, Prefuse beta-20071021, and Breeze 0.13.1. No implementation replacement was required: ASM and Prefuse had no references, while the sole Breeze occurrence was an unused import. The checkpoint is accepted only if all source sets compile and the deterministic probability/serialization gate remains green.
