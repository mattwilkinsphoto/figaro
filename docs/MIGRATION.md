# Migrating to Scala 3 and sbt 2

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

The build uses normal Scala 3 type checking. `-source:3.0-migration` is **not** retained. `-no-indent` is an intentional brace-syntax choice, not a type-safety escape hatch. Remaining deprecated spellings are visible compiler warnings. Scala/build sources are pinned to LF to avoid a Windows carriage-return issue encountered in automatic symbol rewrites.

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
| Deprecations | Visible maintenance debt (`Stream`, syntax, non-local returns, etc.); not all cleaned up in this migration. |

The migration implementation passed 119 required tests, 284 broader local checks, coverage-plugin smoke tests, publication, legal-file checks, and genuinely fresh byte-for-byte thin/fat JAR rebuilds. [Required GitHub CI](https://github.com/mattwilkinsphoto/figaro/actions/runs/33952691424) passed; its timing advisory failed. These counts describe that checkpoint, not a universal guarantee for all workloads or a full-suite coverage percentage.

Before production: exercise the actual consumer's model construction, evidence, selected algorithms, and parameter persistence end to end; compare against known numerical fixtures; test cleanup/error handling and representative resource use; harden relevant statistical checks. A production release should have an immutable version/tag and published artifacts, not only a local snapshot.

## Related

[User guide](USER_GUIDE.md), [API changes in context](API_GUIDE.md), [build/verification](BUILDING.md), [engineering log](../MODERNIZATION.md), and [JVM integration](../CONSUMER_BOUNDARY.md).
