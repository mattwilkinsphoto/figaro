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

## Pilot proposal calibration

`ProposalCalibrationExample.main(args: Array[String]): Unit` takes no arguments and prints a separate pilot's fitted covariance/diagnostics followed by fresh fixed-budget and precision-stopped production runs. Example: `ProposalCalibrationExample.main(Array.empty)`. Inadequate pilot diagnostics raise `IllegalArgumentException`; model failures propagate. See the [three-step user guide](../docs/PROPOSAL_CALIBRATION.md).

`ProposalCalibrationBenchmark.main(args: Array[String]): Unit` accepts repetitions (positive, default 20), production draw cap (at least 2000, default 12000), workers (1-4, default 4), and pilot draws (at least 500, default 6000). It prints quoted `calibration` CSV rows comparing six geometries and four strategies, then returns Unit. Round -1 is an excluded JVM warm-up. Example: `ProposalCalibrationBenchmark.main(Array("30", "12000", "4", "6000"))`. Pilot-fit rejection is reported explicitly without production sampling; malformed arguments, model failures, and leaked workers fail execution. [Validation](../docs/PROPOSAL_CALIBRATION_VALIDATION.md) explains coverage, pilot-inclusive timing, and limitations.

## MCMC reliability examples

`McmcReliabilityExample.main(args: Array[String]): Unit` requires no arguments. It prints a deliberately capped run's failure reasons and both MCSE estimates, then checks a successful reparameterized control. Example: `McmcReliabilityExample.main(Array.empty)`. Invalid arguments or failed assertions raise `IllegalArgumentException`; sampler errors propagate. See the [user guide](../docs/MCMC_RELIABILITY.md) for interpretation and three common patterns.

`McmcReliabilityValidation.main(args: Array[String]): Unit` accepts positive repetitions (default 60), maximum draws per chain (at least 12000 and a multiple of 2000, default 48000), workers (1-4, default 4), and an optional comma-separated subset of `iid,reparameterized,default,joint-prior,manual,calibrated`. It returns Unit and prints quoted `reliability` CSV for paired former/current stopping rules on identical trace prefixes. Example: `McmcReliabilityValidation.main(Array("60", "48000", "2"))`. Invalid/duplicate strategies or budgets raise `IllegalArgumentException`, malformed numbers may raise `NumberFormatException`, and model failures/leaked workers fail execution. Rejected pilots are recorded without production or retry. This generates complete fixed traces first: it is a statistical audit, not a timing benchmark. See [reproduction and results](../docs/MCMC_RELIABILITY_VALIDATION.md).

## Sampling research (experimental)

`SamplingResearchExample.main(args: Array[String]): Unit` is the only public entry point; all kernels and fixtures are private. `Array("check")` runs deterministic contracts and seeded analytic controls. Otherwise arguments are positive repetitions (default 30), retained draws per chain (2000-1000000, multiple of 2000, default 12000), and an optional comma-separated subset of `figaro-block,mess-1,mess-4,mess-8,qslice-cauchy`. Example: `SamplingResearchExample.main(Array("1", "2000"))`. It returns Unit and prints quoted research CSV on three analytic targets, or check confirmation. Invalid parameters/densities/numerical boundaries throw `IllegalArgumentException`; exhausted searches throw `IllegalStateException`, interruption throws `InterruptedException`, and callback/model errors propagate. No failure is converted into a sample.

This compares Figaro's actual fixed block sampler with standalone immutable-vector research kernels; it does not accept arbitrary Figaro graphs. Four chains and 2000 warm-up draws are fixed; full traces are generated before stopped-prefix replay. Density-evaluation counts include full sampling/warm-up even on stopped records. See [research findings, reproduction, and three common workflows](../docs/SAMPLING_RESEARCH.md).

## Matched-budget sampling validation (experimental)

`SamplingBudgetValidation.main(args: Array[String]): Unit` runs `Array("check")` controls, or accepts positive repetitions (30), a per-chain density-evaluation cap (100000, 20000-1000000 and divisible by four), and nonnegative first seed round (0). Example: `SamplingBudgetValidation.main(Array("1", "30000"))`. It returns Unit and prints quoted CSV on five analytic targets and four standalone samplers, including finite-pilot affine GPSS. Invalid arguments throw; numerical/model/search failures become explicit failed records, and interruption aborts. A zero exit code does not imply every experiment passed. The [protocol, full API contract, examples, and limitations](../docs/SAMPLING_BUDGET_VALIDATION.md) explain equal costs, warm-up charges, aligned traces, and selected-stop coverage. No production library API or default changes.

## Higher-dimensional validation (experimental)

For the supported explicit-vector execution interface, see
[continuous-vector sampling](../docs/VECTOR_SLICE_SAMPLING.md).
`VectorSliceSamplingExample.main(args: Array[String]): Unit` accepts only an empty
array, runs Gaussian GPSS, positive quantile, and independent-chain diagnostics examples,
and prints work status/estimates. Invalid arguments or an incomplete fixture throw.
Example: `VectorSliceSamplingExample.main(Array.empty)`.

`HighDimensionalSamplingValidation.main(args: Array[String]): Unit` runs `Array("check")` controls or accepts repetitions (20, positive), a per-chain density-evaluation cap (300000, 20000-1000000 and divisible by four), and first round (0, nonnegative). Example: `HighDimensionalSamplingValidation.main(Array("1", "20000"))`. It returns Unit and prints quoted CSV for GPSS/quantile samplers across six targets at dimensions 8 and 32. Invalid arguments throw; model/numerical/search failures are explicit failed rows; interruption aborts. A successful process exit is not proof of precision or successful execution of every chain. See the [full API, protocol, reproduction examples, and limitations](../docs/SAMPLING_HIGH_DIMENSIONAL.md). All kernels remain private and no production API changes.

[Library module](../Figaro/README.md) implements the APIs; [build guide](../docs/BUILDING.md) explains tests and documentation generation; [migration guide](../docs/MIGRATION.md) explains changed syntax, dependencies, and remaining risks.
