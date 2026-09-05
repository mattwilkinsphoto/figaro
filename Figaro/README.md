# Figaro library module

## Overview

`Figaro/` contains the probabilistic modeling language, distributions, inference engines, learning/filtering algorithms, and tests. It is the published `figaro_3` library. Use it to describe uncertainty and evidence as a model, then compute or estimate answers without implementing an inference engine. It depends on Commons Math, Argonaut, Scala Swing, and Scala parallel collections; ScalaTest is test-only.

## Quick start

From the repository root, with JDK 17 and sbt installed:

1. Run `sbt "figaro / compile"`.
2. Run `sbt "examples / Compile / runMain com.cra.figaro.example.documentation.QuickStart"`.
3. Follow [application installation](../docs/USER_GUIDE.md#installation-and-integration) to publish locally and add the `_3` dependency to your application.

## API reference

Start with the [practical API guide](../docs/API_GUIDE.md) for constructor, evidence, lifecycle, and query contracts. The [complete method reference](../docs/api/README.md) lists public methods with parameter lists, returns, source contracts where available, and invocation templates. Generate searchable full Scaladoc with `sbt "figaro / Compile / doc"`; that also documents fields, types, constructors, and inheritance.

| Package under `com.cra.figaro` | Responsibility / usual collaborators |
| --- | --- |
| `language` | `Element`, `Universe`, `Flip`, `Select`, `Apply`, `Chain`; foundation for every model |
| `library.atomic.discrete`, `library.atomic.continuous` | Probability distributions; inputs to compound models and inference |
| `library.compound`, `library.collection`, `library.cache` | Conditional models, collections, and reuse around core elements |
| `algorithm.factored`, `algorithm.sampling` | Exact finite-model inference and approximate sampling; query model elements |
| `algorithm.filtering`, `algorithm.online` | Evolving/time-indexed models; require appropriate universe/stream management |
| `algorithm.learning`, `patterns.learning` | Parameter learning and learnable model patterns |
| `algorithm.decision`, `library.decision` | Decisions, utilities, and policies |
| `algorithm.lazyfactored`, `algorithm.structured` | Specialized inference, refinement, and solver strategies |
| `experimental` | Research algorithms; publicly visible is not a production-support guarantee |
| `util` | Collections, math, randomness, and shared infrastructure |

## Three common patterns

The [user guide](../docs/USER_GUIDE.md#common-patterns) contains complete model code for:

1. Exact categorical probability, using `Select` and `VariableElimination`.
2. Bayesian updating, using `Flip`, `If`, observation, and `VariableElimination`.
3. Continuous threshold estimation, using `Normal` and `Importance`.

All three run together in [CommonPatterns.scala](../FigaroExamples/src/main/scala/com/cra/figaro/example/documentation/CommonPatterns.scala).

## Gotchas

`Element[T]` is a random variable, not a sampled `T`. Set evidence before inference, include every query target in the algorithm, and call `kill()` when finished. A normal distribution's second argument is variance, not standard deviation. Exact inference can be prohibitively expensive; sampling has error. Mutable default universes and caches need deliberate ownership in servers and concurrent applications.

This is a Scala 3 snapshot requiring consumer recompilation, not a binary-compatible update to `_2.13`. Full historical-suite success and OSGi/runtime-consumer certification are not claimed. See [migration risks](../docs/MIGRATION.md) and [build/test instructions](../docs/BUILDING.md).

## Related

[Examples module](../FigaroExamples/README.md) depends on this library. The root build aggregates both. See [JVM integration](../CONSUMER_BOUNDARY.md) for Figaro's dependency contract and release-readiness checklist.
