# Classic Figaro tutorials, updated for Scala 3

## Overview

The original Quick Start Guide teaches three valuable steps: describe a model, choose
an inference algorithm, and query its result. This walkthrough preserves its Constant,
Select and Burglary examples while replacing obsolete installation instructions and
adding executable numerical checks and guaranteed cleanup. Original examples and
explanations are credited to Avi Pfeffer and the Charles River Analytics contributors;
see [attributions](../FigaroAttributions.txt) and the [preservation audit](DOCUMENTATION_MIGRATION.md).

Use this after the [three-step setup](../README.md#quick-start-three-steps). You need JDK 17;
the repository pins Scala 3.9.0 and sbt 2.0.8. Do not install FigaroWork, add a JAR to the
system PATH, or copy the old `_2.12` dependency coordinates. For application integration,
consume the compiled library using [the dependency contract](../CONSUMER_BOUNDARY.md).

## Quick start: three steps

1. Obtain this repository's main branch and complete the [setup](../README.md).
2. From its root run:

   ```sh
   sbt "examples / Compile / runMain com.cra.figaro.example.documentation.ClassicTutorials"
   ```

3. Read and adapt [ClassicTutorials.scala](../FigaroExamples/src/main/scala/com/cra/figaro/example/documentation/ClassicTutorials.scala).
   It prints the values below and fails if they differ from the checked expectations.

```text
Constant greeting: hello=1.0, goodbye=0.0
Uncertain greeting: hello=0.8, goodbye=0.2
Burglary given John's call: 0.373378117264
Classic tutorials passed: Constant, Select, Burglary and independent enumeration
```

## Common patterns

### 1. A deterministic model with a sampling algorithm

The original Hello World example shows that a model node is a distribution over values,
even when only one value is possible. These lines are inside the complete example:

```scala
val u = Universe.createNew()
val greeting = Constant("Hello world!")(using "greeting", u)
val algorithm = Importance(1000, greeting)
try {
  algorithm.start()
  println(algorithm.probability(greeting, "Hello world!"))
  println(algorithm.probability(greeting, "Goodbye world!"))
} finally {
  if (algorithm.isActive) algorithm.kill()
  u.clear()
}
```

Imports are `com.cra.figaro.language.{Constant, Universe}` and
`com.cra.figaro.algorithm.sampling.Importance`. The integer requests a bounded one-time
sampler, so `start()` waits for the sampling budget. Its probabilities are 1 and 0 here
because the model is deterministic, not because 1000 samples guarantee exact answers in
general. Sampling is unnecessary for a Constant but illustrates the common interface.

### 2. An uncertain greeting with exact elimination

Replace the Constant with a finite distribution and use exact inference:

```scala
val greeting = Select(0.8 -> "Hello world!", 0.2 -> "Goodbye world!")
val algorithm = VariableElimination(greeting)
```

Import `Select` from `language` and `VariableElimination` from `algorithm.factored`.
Use the same start/query/finally-cleanup structure as above, in a fresh universe. The
answers are 0.8 and 0.2, up to floating-point representation. The complete example checks
both to `1e-12`. Elimination is exact for this small finite model; it is not an exactness
or memory-safety promise for every continuous, infinite or densely connected model.

### 3. Learn from evidence: the original Burglary network

The old guide's model is retained, including its original probabilities. These differ
from some other commonly published alarm networks, so compare against this model's answer.

```scala
val burglary = Flip(0.01)
val earthquake = Flip(0.0001)
val alarm = CPD(burglary, earthquake,
  (false, false) -> Flip(0.001), (false, true) -> Flip(0.1),
  (true, false) -> Flip(0.9), (true, true) -> Flip(0.99))
val johnCalls = CPD(alarm, false -> Flip(0.01), true -> Flip(0.7))
johnCalls.observe(true)
val algorithm = VariableElimination(burglary, earthquake)
```

Import `Flip` from `language` and `CPD` from `library.compound`. Run and clean up the
algorithm as in the complete example. Query `algorithm.probability(burglary, true)`.
Observing John's call increases burglary probability from 0.01 to about 0.373378.
That is `P(burglary | John calls)`, **not** `P(alarm | burglary)` or proof of a burglary.

For an independent check, enumerate the four burglary/earthquake combinations. For each,
multiply its prior probability by `pAlarm * 0.7 + (1 - pAlarm) * 0.01`. Sum all four
weights for the evidence probability; sum the two burglary weights for the numerator.
Their ratio matches Figaro. The runnable example also verifies the earthquake posterior.

## API reference for this walkthrough

`ClassicTutorials.main(args: Array[String]): Unit` is the only public function in the
new example. Pass an empty array (or no command-line arguments). It runs all three
models, prints results and a completion line, and throws on unexpected arguments or
failed assertions. Example: `ClassicTutorials.main(Array.empty[String])`.
The private model helpers are not a published library API. Their universes and algorithms
are disposed after each run. Do not call this example concurrently: it intentionally
uses `Universe.createNew()` to teach the sequential workflow.

The [API guide](API_GUIDE.md) documents constructor parameters and the returns of
`Constant`, `Select`, `Flip`, `observe`, `start`, `probability`, `kill` and universe
cleanup. [Compound APIs](api/com.cra.figaro.library.compound.md) supply the precise CPD
overloads. Use the [complete reference](api/README.md), not the deleted Scala 2 API JAR.

## Gotchas carried forward and corrected

- Use Scala 3 `*` wildcard imports, explicit `(): Unit =` method bodies, and `using` for
  explicit contextual argument lists. Old `:_*`, symbol literals and bare lifecycle
  method syntax need migration; see [deprecation retirement](DEPRECATION_RETIREMENT.md).
- The original quick-start's broken/missing parentheses and inconsistent greeting
  punctuation are not copied into executable source. Query strings must match outcomes.
- Create the model and evidence before inference; build a fresh scenario for changed
  evidence instead of assuming an active algorithm automatically invalidates its caches.
- Both query nodes must be registered with elimination. New nodes that look identical
  are not the original target objects.
- Stop/kill semantics differ: `stop()` pauses an anytime algorithm, while `kill()` disposes
  it. Consecutive queries after resume may match; no step is promised between queries.
- For a small exact model, parallelism can add overhead. The later performance APIs are
  opt-in and require isolated models; they do not change these examples automatically.

## Continue learning

The tutorial is not reduced to these three examples. Continue with the
[modeling and algorithm-extension companion](LEGACY_MODELING_GUIDE.md) for the original
relational, open-universe, filtering, learning, decision and advanced inference material.
The [user guide](USER_GUIDE.md) adds a continuous-tail example, while
[parallel importance](PARALLEL_PERFORMANCE.md), [multi-chain MCMC](MULTI_CHAIN_MCMC.md)
and [vector chains](MULTI_CHAIN_VECTOR_SAMPLING.md) cover the modernization additions.
