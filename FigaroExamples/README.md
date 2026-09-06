# Figaro examples module

## Overview

`FigaroExamples/` is a separate sbt project named `examples` that depends on the library. It contains executable Scala models, including historical tutorial/book examples and small, verified Scala 3 onboarding examples. It is not an inference library or a single executable JAR.

## Quick start

From the repository root with JDK 17 and sbt:

1. Run `sbt "examples / Compile / runMain com.cra.figaro.example.documentation.QuickStart"`.
2. Run `sbt "examples / Compile / runMain com.cra.figaro.example.documentation.CommonPatterns"`.
3. Copy/adapt a model into your application using the [user guide](../docs/USER_GUIDE.md).

The quick start prints `P(cause | signal) = 0.692308`. Common patterns prints `0.200000`, `0.692308`, and a varying estimate normally near `0.309`.

## Documentation example API reference

These are every public function added for onboarding. Import `com.cra.figaro.example.documentation.{QuickStart, CommonPatterns}`. They reset the default universe and must not run concurrently with models relying on that shared default. They create and dispose their own algorithms; caller-owned universes are not automatically cleared.

| Public function | Parameters | Returns / side effects | Example |
| --- | --- | --- | --- |
| `QuickStart.main(args: Array[String]): Unit` | Command-line arguments, ignored | Checks and prints the exact posterior; assertion failure signals an unexpected result | `QuickStart.main(Array.empty[String])` |
| `CommonPatterns.exactMarginal(): Double` | None | Exact late-delivery probability, `0.2` | `val pLate = CommonPatterns.exactMarginal()` |
| `CommonPatterns.bayesianPosterior(): Double` | None | Exact cause probability after a signal, `9.0 / 13.0` | `val posterior = CommonPatterns.bayesianPosterior()` |
| `CommonPatterns.sampledThreshold(samples: Int): Double` | Positive importance-sample budget; zero/negative throws `IllegalArgumentException` | Estimated probability of temperature above 21 for `Normal(20, 4)`; no error-bound guarantee | `val tail = CommonPatterns.sampledThreshold(50000)` |
| `CommonPatterns.main(args: Array[String]): Unit` | Command-line arguments, ignored | Runs all three patterns, asserts exact answers and sampled probability bounds, and prints results | `CommonPatterns.main(Array.empty[String])` |

See [QuickStart source](src/main/scala/com/cra/figaro/example/documentation/QuickStart.scala) and [CommonPatterns source](src/main/scala/com/cra/figaro/example/documentation/CommonPatterns.scala). The library's full API is [referenced separately](../docs/api/README.md). Generate `sbt "examples / Compile / doc"` for the broader examples module's compiler API; historical example entry points are demonstrations, not supported application APIs.

## Three common patterns, with code

```scala
import com.cra.figaro.example.documentation.CommonPatterns

// 1. Query a finite categorical model without evidence.
val lateProbability = CommonPatterns.exactMarginal()

// 2. Update a belief after observing a dependent signal.
val posterior = CommonPatterns.bayesianPosterior()

// 3. Estimate a continuous tail probability with a sample budget.
val thresholdRisk = CommonPatterns.sampledThreshold(50000)
```

The [user guide](../docs/USER_GUIDE.md#common-patterns) expands these calls into model-building and inference code, with explanations of the answers.

## Parallel-performance examples

Import `com.cra.figaro.example.{ParallelSamplingExample, SamplingBenchmark}`. These are the two public functions added for the performance stage; their helpers are private.

| Public function | Parameters | Returns / side effects | Example |
| --- | --- | --- | --- |
| `ParallelSamplingExample.main(args: Array[String]): Unit` | Arguments, ignored | Runs four seeded importance workers, prints a posterior estimate, checks a broad accuracy bound, and disposes the sampler | `ParallelSamplingExample.main(Array.empty)` |
| `SamplingBenchmark.main(args: Array[String]): Unit` | Workload `coin/evidence/normal/mh/rng`, positive sample count, positive measured repeats, comma-separated worker counts from 1 through sample count, optional `legacy/seeded`; defaults `coin 100000 3 1,2,4,8 legacy` | Prints CSV timings, estimates, and resource diagnostics; invalid input throws `IllegalArgumentException` (numeric parsing may throw its `NumberFormatException` subtype) | `SamplingBenchmark.main(Array("normal", "200000", "5", "1,2,4,8", "seeded"))` |

Run with `sbt "examples / Compile / runMain com.cra.figaro.example.ParallelSamplingExample"`. The [performance guide](../docs/PARALLEL_PERFORMANCE.md) explains benchmark forking, warm-up, metric limitations, and all three common workflows. This benchmark is diagnostic, not a wall-clock CI gate or a general parallel-MCMC API. It uses HotSpot-compatible management beans and should run in a dedicated JVM, not alongside application models.

## Gotchas (all examples)

- Use an explicit `runMain` class name. There are many historical entry points; `run` may prompt you to choose one.
- Compilation does not establish that every historical example finishes quickly, has its required input files, or matches modern behavior. Some learning examples are very expensive and Swing examples need a graphical environment.
- Sampled results change between runs. The onboarding check verifies numerical bounds, not a tight tolerance that could fail randomly.
- Run unrelated models in deliberately managed universes. `Universe.createNew()` changes the default; it does not clean up algorithms owned by an older universe.
- These sources are compiled against Scala 3. They are not copy/paste instructions for the old Scala 2 artifact.

## Related

[Library module](../Figaro/README.md) implements the APIs; [build guide](../docs/BUILDING.md) explains tests and documentation generation; [migration guide](../docs/MIGRATION.md) explains changed syntax, dependencies, and remaining risks.
