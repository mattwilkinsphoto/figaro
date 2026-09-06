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

## Multi-chain MCMC examples

Import `com.cra.figaro.example.{MultiChainMcmcExample, MultiChainMcmcBenchmark}`. Helpers are private; the public functions are:

| Function | Parameters | Returns / behavior | Example |
| --- | --- | --- | --- |
| `MultiChainMcmcExample.main(args: Array[String]): Unit` | Arguments, ignored | Runs a known normal posterior with dispersed starts, prints scalar diagnostics and chain metadata, checks a broad mean bound, and disposes models through the runner | `MultiChainMcmcExample.main(Array.empty)` |
| `MultiChainMcmcBenchmark.main(args: Array[String]): Unit` | Workload `normal/likelihood/correlated/wide`, draws per chain, positive measured repeats, comma-separated worker counts, chain count; defaults `normal 20000 3 1,2,4 4` | Prints fixed-chain end-to-end timing/CPU/ESS CSV; invalid inputs throw `IllegalArgumentException`, including numeric parsing errors | `MultiChainMcmcBenchmark.main(Array("wide", "20000", "5", "1,2,4", "4"))` |

Run `sbt "examples / Compile / runMain com.cra.figaro.example.MultiChainMcmcExample"`. See the [multi-chain user guide](../docs/MULTI_CHAIN_MCMC.md) for the evidence boundary, trace/diagnostic interpretation, independent-chain ownership, and benchmark metric definitions. The earlier `SamplingBenchmark mh` remains a historical experiment, not an alias for this new API.

## Gotchas (all examples)

`StoppingCriteriaValidation.main(args: Array[String]): Unit` runs paired fixed/adaptive validation against analytic expectations for normal, likelihood, conditioned, Bernoulli, correlated, and bimodal models, plus a deliberately trapped negative control. Arguments are positive repetitions (default 20), maximum draws per chain (at least 2000, default 12000), and workers (1-4, default 4). Two negative-index warm-up rounds are printed but excluded from analysis. It returns Unit and prints `validation,`-prefixed CSV; invalid arguments or a false precision success on the negative control throw `IllegalArgumentException`. Example: `StoppingCriteriaValidation.main(Array("50", "12000", "4"))`. See [validation results](../docs/STOPPING_VALIDATION.md) for timing and coverage qualifications.

The [stopping-criteria example](src/main/scala/com/cra/figaro/example/StoppingCriteriaExample.scala) adds `StoppingCriteriaExample.main(args: Array[String]): Unit`. It requires empty arguments, prints categorical KL and a seeded Gaussian TSPRT decision, then compares fixed and adaptive MCMC retained work and assessments. Run `sbt "examples / Compile / runMain com.cra.figaro.example.StoppingCriteriaExample"`; this is an illustrative comparison, not a controlled speed benchmark. See [stopping criteria](../docs/STOPPING_CRITERIA.md) for precision units and statistical assumptions.

- Use an explicit `runMain` class name. There are many historical entry points; `run` may prompt you to choose one.
- Compilation does not establish that every historical example finishes quickly, has its required input files, or matches modern behavior. Some learning examples are very expensive and Swing examples need a graphical environment.
- Sampled results change between runs. The onboarding check verifies numerical bounds, not a tight tolerance that could fail randomly.
- Run unrelated models in deliberately managed universes. `Universe.createNew()` changes the default; it does not clean up algorithms owned by an older universe.
- These sources are compiled against Scala 3. They are not copy/paste instructions for the old Scala 2 artifact.

## Related

The [blocked-proposal guide](../docs/BLOCKED_PROPOSALS.md) has three runnable patterns in `BlockedProposalExample.main(args: Array[String]): Unit`. Arguments must be empty; it prints fixed/default comparisons, a mixed discrete/continuous model, and adaptive stopping results. Example: `BlockedProposalExample.main(Array.empty)`.

`BlockedProposalBenchmark.main(args: Array[String]): Unit` accepts repetitions (positive, default 20), maximum draws per chain (at least 2000, default 12000), and workers (1-4, default 4). It prints `blocked,`-prefixed per-query CSV for fixed/adaptive runs and returns Unit; rounds -2 and -1 are JVM warm-ups. Invalid arguments raise `IllegalArgumentException` (including `NumberFormatException` for malformed integers); leaked workers also fail the run. Example: `BlockedProposalBenchmark.main(Array("50", "12000", "4"))`. See [measured results](../docs/BLOCKED_PROPOSAL_VALIDATION.md) for metric definitions and limitations.

[Library module](../Figaro/README.md) implements the APIs; [build guide](../docs/BUILDING.md) explains tests and documentation generation; [migration guide](../docs/MIGRATION.md) explains changed syntax, dependencies, and remaining risks.
