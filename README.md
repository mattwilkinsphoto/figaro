# Figaro: probabilistic models in Scala

Figaro lets you describe uncertain quantities, their relationships, and observed evidence as Scala objects. Its inference algorithms answer questions such as “given this observation, how likely is that explanation?” You supply a model without implementing an inference engine yourself.

This modernized line uses **Scala 3.9.0 LTS, sbt 2.0.8, and JDK 17**. It keeps the `com.cra.figaro` packages but is a new Scala 3 artifact, not a binary-compatible replacement for `figaro_2.13`. It is a development snapshot, not a published stable release.

## Quick start: three steps

Prerequisites: Git, JDK 17 on your path, and an sbt runner. sbt downloads the compiler and uses `project/build.properties`; you do not need a separate Scala installation. The first build needs internet access.

1. Get the Scala 3 branch:

   ```sh
   git clone --branch modernize/mcmc-reliability https://github.com/mattwilkinsphoto/figaro.git
   cd figaro
   ```

2. Run the complete first example:

   ```sh
   sbt "examples / Compile / runMain com.cra.figaro.example.documentation.QuickStart"
   ```

   It prints `P(cause | signal) = 0.692308`. Read its [source](FigaroExamples/src/main/scala/com/cra/figaro/example/documentation/QuickStart.scala): create a model, observe evidence, run inference, query, and clean up.

3. Run and adapt the [three common patterns](docs/USER_GUIDE.md#common-patterns):

   ```sh
   sbt "examples / Compile / runMain com.cra.figaro.example.documentation.CommonPatterns"
   ```

   These demonstrate a discrete marginal, a Bayesian posterior, and approximate inference for a continuous model.

## Use Figaro in your application

From this checkout, run `sbt "figaro / publishLocal"`. In a separate Scala application's `build.sbt`:

```scala
scalaVersion := "3.9.0"
libraryDependencies += "io.github.mattwilkinsphoto" %% "figaro" % "6.0.0-modern.10-SNAPSHOT"
```

That coordinate resolves only after local publication, unless you separately publish it to a repository. Local publication is per user and machine. Producer and consumer must use the same local repository. See [installation and integration](docs/USER_GUIDE.md#installation-and-integration), including Java and fat-JAR usage.

## Documentation

- [Gaussian block proposals](docs/BLOCKED_PROPOSALS.md): opt-in correlated moves, covariance selection, acceptance rules, and measured counterexamples.
- [Pilot proposal calibration](docs/PROPOSAL_CALIBRATION.md): estimate an inspectable fixed covariance from discarded pilot chains, then start fresh production sampling; includes rejection rules and pilot-inclusive comparisons.
- [MCMC reliability](docs/MCMC_RELIABILITY.md): understand precision failures, compare error estimates, and recognize exploration problems that a stopping rule cannot fix.
- [Sampling research](docs/SAMPLING_RESEARCH.md): recent literature, isolated quantile/multiproposal slice prototypes, and measured limits; not a new production inference API.
- [Matched-budget validation](docs/SAMPLING_BUDGET_VALIDATION.md): compare quantile and affine/polar research samplers with initialization-inclusive evaluation caps and explicit coverage checks.
- [Higher-dimensional validation](docs/SAMPLING_HIGH_DIMENSIONAL.md): GPSS and quantile research at 8 and 32 dimensions, including hard constraints and asymmetric modes.
- [Continuous-vector sampling](docs/VECTOR_SLICE_SAMPLING.md): opt-in GPSS/quantile over explicit log densities, with immutable traces, hard budgets, and cooperative cancellation.
- [Multi-chain vector sampling](docs/MULTI_CHAIN_VECTOR_SAMPLING.md): bounded independent-chain execution, deterministic seeds, complete budget accounting, and aligned coordinate diagnostics.
- [Vector scaling measurements](docs/VECTOR_SAMPLING_PERFORMANCE.md): matched-trace worker comparisons, effective samples per second, and diagnostic overhead.
- [Parallel coordinate diagnostics](docs/PARALLEL_VECTOR_DIAGNOSTICS.md): bounded scheduling, serial fallback, memory tradeoffs, and before/after verification.
- [Allocation and GC profiling](docs/VECTOR_ALLOCATION_PROFILE.md): opt-in JDK recording, allocation hotspots, and limits of memory-bandwidth inference.
- [Primitive diagnostic reductions](docs/PRIMITIVE_DIAGNOSTIC_REDUCTIONS.md): reduced boxing, exact-result checks, and measured end-to-end effects.
- [Primitive FFT autocovariance](docs/PRIMITIVE_FFT_AUTOCOVARIANCE.md): fewer complex-array temporaries, unchanged numerical results, and matched-work measurements.
- [Primitive diagnostic sorting](docs/PRIMITIVE_DIAGNOSTIC_SORTING.md): stable rank indices, primitive value sorting, exact tie handling, and measured tradeoffs.
- [Interleaved performance audit](docs/INTERLEAVED_PERFORMANCE_AUDIT.md): fresh-JVM paired comparisons, callback allocation attribution, and limits of causal interpretation.

- [User guide](docs/USER_GUIDE.md): concepts, common patterns, gotchas, and related modules.
- [API guide](docs/API_GUIDE.md): practical contracts and examples for the main entry points.
- [Complete public-method reference](docs/api/README.md): compiler-derived signatures, parameter lists, returns, and invocation templates, including advanced and experimental APIs.
- [Migration changes](docs/MIGRATION.md): breaking changes, accepted workarounds, remaining risks, and upgrade checklist.
- [Deprecation retirement](docs/DEPRECATION_RETIREMENT.md): API replacements and lazy-collection behavior.
- [Parallel Monte Carlo](docs/PARALLEL_PERFORMANCE.md): opt-in seeded importance sampling, worker ownership, benchmarks, and limitations.
- [Multi-chain MCMC](docs/MULTI_CHAIN_MCMC.md): isolated MH chains, retained traces, R-hat/ESS/MCSE diagnostics, evidence restrictions, and end-to-end benchmarks.
- [Stopping criteria](docs/STOPPING_CRITERIA.md): Gaussian TSPRT, categorical KL, and opt-in scalar-mean precision stopping for multi-chain MCMC.
- [Build and verification](docs/BUILDING.md): sbt 2 commands, tests, coverage, publication, documentation generation, and Windows troubleshooting.
- [Library module](Figaro/README.md) and [examples module](FigaroExamples/README.md).
- [Engineering history](MODERNIZATION.md), [dependency inventory](DEPENDENCIES.md), and [JVM integration](CONSUMER_BOUNDARY.md).

Generate the searchable Scala 3 API site with `sbt "figaro / Compile / doc"`. Open `target/out/jvm/scala-3.9.0/figaro/api/index.html` locally. The checked-in `ScalaDoc/` tree is historical Scala 2 documentation, not this branch's API reference.

## Important limitations

Set evidence before starting inference. Query only targets supplied to the algorithm. Release an active algorithm with `kill()` when finished. Sampling estimates vary; they are not exact probabilities or confidence guarantees. Do not share mutable universes indiscriminately across threads.

All source sets and focused migration checks pass, but the entire historical test suite is not green. Timing tests remain advisory, some statistical tests are flaky, and OSGi deployment is unvalidated. On Windows, use separate sbt processes for coverage and normal packaging. The [migration guide](docs/MIGRATION.md) explains these limits.

## Provenance and license

This repository modernizes [Charles River Analytics Figaro](https://github.com/charles-river-analytics/figaro). Original authorship and history are preserved. See [LICENSE](LICENSE) and [FigaroAttributions.txt](FigaroAttributions.txt). The historical [release notes](https://github.com/charles-river-analytics/figaro/releases/download/5.0.0.0/Figaro_Release_Notes.pdf) and [tutorial](https://github.com/charles-river-analytics/figaro/releases/download/5.0.0.0/Figaro_Tutorial.pdf) explain the original project, not this branch's build or compatibility requirements.
