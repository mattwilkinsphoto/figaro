# Figaro user guide

## Overview

Figaro is an embedded probabilistic programming library. A normal Scala value says “the temperature is 20.” An `Element[Double]` describes a distribution of possible temperatures. Connect elements to describe a model, attach observations, and ask an inference algorithm for probabilities or expectations.

Use it to diagnose hidden causes, estimate risks, reason about evolving state, or choose actions under uncertainty. It is not a dataframe library, a neural-network trainer, or a service that learns a model from arbitrary data. You supply the model and select an inference method.

This guide targets Scala 3.9.0 / sbt 2.0.8 / JDK 17. For a first run in three steps, use the [root quick start](../README.md#quick-start-three-steps).

## The mental model

| Concept | Meaning | Example |
| --- | --- | --- |
| `Universe` | Mutable context holding a model's elements and algorithms | `Universe.createNew()` |
| `Element[T]` | A model node whose outcomes have type `T`, not a single answer | `Flip(0.2)` is an `Element[Boolean]` |
| Distribution constructor | Creates an uncertain quantity | `Select(0.7 -> "yes", 0.3 -> "no")` |
| Composition | Expresses dependencies between nodes | `If(cause, Flip(0.9), Flip(0.1))` |
| Evidence | Observed values/conditions used to update beliefs | `signal.observe(true)` |
| Target | A node an algorithm retains for queries | `VariableElimination(cause)` |
| Algorithm | Computes or approximates model answers | `algorithm.probability(cause, true)` |

Constructing an element does not run inference. `element.generate()` draws a value and updates mutable state; reading `element.value` is not a posterior-probability query. To answer a probability question, run an algorithm.

## Installation and integration

You need JDK 17 and an sbt runner. The repository pins its sbt/compiler/plugin versions. Do not change them just to match a globally installed Scala version.

1. In the Figaro checkout, run `sbt "figaro / publishLocal"`.
2. In your application's `build.sbt`, add:

   ```scala
   scalaVersion := "3.9.0"
   libraryDependencies += "io.github.mattwilkinsphoto" %% "figaro" % "6.0.0-modern.3-SNAPSHOT"
   ```

3. Compile your application on JDK 17 and use the imports below.

`%%` selects the Scala binary suffix `_3`. Local publication normally uses the user's Ivy local repository; an isolated `sbt.ivy.home` changes that location. Producer and consumer must use the same repository. This snapshot is not promised on Maven Central. An unresolved dependency usually means it was not published into the consumer's repository. For a team/deployment, publish a versioned prerelease to your chosen repository rather than copying source or depending on a workstation path.

Java/Maven consumers use `io.github.mattwilkinsphoto:figaro_3:6.0.0-modern.3-SNAPSHOT` and the POM dependencies. The API is Scala-shaped (functions, contexts, collections); a small Scala facade can provide a simpler Java boundary. A dedicated Java compatibility test has not been performed.

Prefer the normal library JAR. The `-fat.jar` bundles non-Scala runtime libraries but deliberately **omits the Scala runtime**, and is not a standalone executable application. Do not put both the thin JAR with its dependencies and the fat JAR on one classpath.

## Common patterns

These are three starting patterns, not a ranking from usage telemetry. They run in [CommonPatterns.scala](../FigaroExamples/src/main/scala/com/cra/figaro/example/documentation/CommonPatterns.scala). The blocks below are method bodies with imports; the linked source provides the complete object and entry point.

### 1. Query a discrete marginal

Use exact variable elimination for a small finite model. Without evidence, the answer reproduces the model's distribution.

```scala
import com.cra.figaro.language.{Select, Universe}
import com.cra.figaro.algorithm.factored.VariableElimination

Universe.createNew()
val delivery = Select(0.7 -> "on-time", 0.2 -> "late", 0.1 -> "cancelled")
val algorithm = VariableElimination(delivery)
algorithm.start()
try {
  val probabilityLate: Double = algorithm.probability(delivery, "late")
  println(probabilityLate) // 0.2, up to floating-point representation
} finally algorithm.kill()
```

Weights and outcomes describe possible states. `probability` returns a `Double` between zero and one for this valid model. To query several targets, pass them all to `VariableElimination(a, b, ...)` at construction.

### 2. Update beliefs using evidence

A signal is more likely when a cause is present. Observing it raises the cause probability from 0.2 to about 0.6923.

```scala
import com.cra.figaro.language.{Flip, Universe}
import com.cra.figaro.library.compound.If
import com.cra.figaro.algorithm.factored.VariableElimination

Universe.createNew()
val cause = Flip(0.2)
val signal = If(cause, Flip(0.9), Flip(0.1))
signal.observe(true)
val algorithm = VariableElimination(cause)
algorithm.start()
try {
  println(algorithm.probability(cause, true)) // 9/13, approximately 0.6923077
} finally algorithm.kill()
```

The numerator is `0.2 * 0.9`; the evidence probability is `0.2 * 0.9 + 0.8 * 0.1`. Set evidence before starting. For a different scenario, finish the algorithm and build/run a fresh model; do not assume a running algorithm invalidates every cached answer after arbitrary mutation.

### 3. Estimate a continuous tail probability

For continuous models or models whose exact factors are too large, choose sampling and a deliberate sample budget.

```scala
import com.cra.figaro.language.Universe
import com.cra.figaro.library.atomic.continuous.Normal
import com.cra.figaro.algorithm.sampling.Importance

Universe.createNew()
val temperature = Normal(20.0, 4.0) // mean 20; variance 4; standard deviation 2
val algorithm = Importance(50000, temperature)
algorithm.start()
try {
  val estimate = algorithm.probability(temperature, (t: Double) => t > 21.0)
  println(estimate) // typically near 0.309; changes across runs
} finally algorithm.kill()
```

The integer argument selects a one-time sampler. `start()` waits for its fixed sample budget. More samples cost time and may improve precision; the count is not a guaranteed error bound. Rare/impossible evidence can cause poor effective sampling, rejection, or failure. Validate your model with repeated runs and appropriate tolerances.

## Choosing and managing an algorithm

Start with `VariableElimination` for a small finite model and `Importance(samples, ...)` for a bounded sampling run. Exact elimination can consume excessive memory when dependencies produce large intermediate factors. Continuous/infinite elements can trigger approximation in a factored algorithm; “variable elimination” is not an exactness guarantee for every model.

Metropolis–Hastings, belief propagation, filtering, learning, and decision algorithms have different assumptions and tuning. MH needs suitable proposals/burn-in; belief propagation is not generally exact on loopy graphs. Read the relevant class documentation and examples before using advanced algorithms.

The lifecycle is: construct model/evidence, construct algorithm with targets, `start()`, query, `kill()`. Use `try/finally` after a successful start. Starting an active algorithm or querying an inactive one is an error. Cleanup is not universally idempotent: `kill()` on an inactive algorithm throws.

Omitting the sample count (`Importance(target)`) creates an anytime worker that improves answers over time. `stop()` pauses but permits queries; `resume()` continues; `kill()` releases it. Queries/lifecycle calls are serialized with sampling steps and may block/time out. `messageTimeout` is a Scala `FiniteDuration`, e.g. `10.seconds`, not an Akka `Timeout`. Anytime inference does not impose an accuracy target or deadline for you.

## Gotchas

For concurrent Monte Carlo, start with the opt-in [seeded parallel importance sampler](PARALLEL_PERFORMANCE.md). It owns separate models and random streams per worker. More workers are not always faster; use its tuning workflow before increasing concurrency.

- **Global mutable default universe:** `Universe.createNew()` replaces the default; it does not dispose of every previous model/worker. Finish active algorithms first. Keep model construction and inference in the intended universe. Use explicit universes or dedicated parallel algorithms for independent concurrent models; the worker queue does not make arbitrary external mutation thread-safe.
- **Names are not values:** `Flip(0.2)(using "cause", universe)` names a node for references/learning without changing outcomes. Ambiguous repeated names can make reference-based code surprising.
- **Probability is not density:** for continuous quantities, ask a predicate such as `t > 21`. Equality to a sampled floating-point value is usually not the desired question.
- **Normal takes variance, not standard deviation:** `Normal(20, 4)` has standard deviation 2. Validate probabilities, positive variances/shapes, and supported outcomes yourself; early validation is not uniform across legacy constructors.
- **Impossible evidence cannot define a useful posterior:** zero total mass cannot be normalized. Diagnose the model instead of accepting `NaN`, zero successful samples, or an exception as an answer.
- **Target membership:** register every query node when constructing the algorithm. A same-looking newly created node is not the original target.
- **Lazy streams:** `distribution` returns `(probability, value)` pairs in a memoized `LazyList`. Materialize finite results while the algorithm is usable. Do not collect an unbounded posterior-sampling stream into a list.
- **Statistical and timing checks:** use tolerances/repeated trials. A timing advisory failure does not prove a probability regression; one passing sample run does not prove equivalence.
- **Dynamic creation:** `Create[T]` accepts a JVM singleton implementing `Creatable`, not any constructor. It is not a sandbox for untrusted plugin names, and cannot prove that a name returns the requested generic `T`.
- **New artifact:** rebuild consumers for `_3`; old `_2.13` applications are not drop-in compatible. See [migration](MIGRATION.md).
- **Build limitations:** Windows coverage transitions, legacy test failures and unvalidated OSGi metadata are covered in [building](BUILDING.md) and [migration](MIGRATION.md).

## Related modules

| Package/project | Role |
| --- | --- |
| `language` | Universes, elements, evidence, composition, references: the foundation of every model |
| `library.atomic`, `library.compound` | Distributions and higher-level model constructors |
| `algorithm.factored`, `algorithm.sampling` | Inference over those elements |
| `algorithm.filtering` | Evolving state: requires a time/state model, not just raw rows |
| `algorithm.learning`, `patterns.learning` | Learnable parameters and supported parameter JSON codecs, not arbitrary application serialization |
| `algorithm.decision`, `library.decision` | Decisions/utilities; nearest-neighbor policies require distance evidence |
| `library.collection` | Collections of model elements, not replacements for ordinary Scala collections |
| `experimental` | Additional algorithms to evaluate per application |
| `util` | Numeric, collection, and display helpers |
| `FigaroExamples` / sbt `examples` | Runnable models depending on core; not a dependency for your application |

The root sbt project aggregates core and examples; publish `figaro`, not the root. See [JVM integration](../CONSUMER_BOUNDARY.md) for the library's dependency contract and release-readiness checklist.
