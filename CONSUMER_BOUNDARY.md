# Using Figaro as a JVM library

Figaro provides a probabilistic modeling language and inference algorithms as a versioned JVM library. This guide describes its dependency contract and release-readiness requirements. For a first model, start with the [user guide](docs/USER_GUIDE.md).

## Dependency contract

Depend on a compiled, versioned Figaro artifact. The coordinates for the current development snapshot are:

```scala
scalaVersion := "3.9.0"
libraryDependencies += "io.github.mattwilkinsphoto" %% "figaro" % "6.0.0-modern.6-SNAPSHOT"
```

Resolve this snapshot by running `sbt "figaro / publishLocal"` in the Figaro checkout, or by publishing it to a configured package repository. Local publication is not a public release. See [building and publication](docs/BUILDING.md) for repository settings and artifact contents.

Java consumers use the corresponding binary artifact (`figaro_3` for the modernized line) together with its declared Scala/runtime dependencies. The POM is the dependency contract. The API is Scala-shaped; Java interoperability and any facade require application-level validation. The fat JAR excludes the Scala runtime and is not a standalone executable.

## Figaro release-readiness checklist

- A tagged, immutable Figaro release built on Java 17.
- A published thin library JAR, sources JAR, Scaladoc/Javadoc artifact where practical, and dependency POM.
- Documented public package/API surface under `com.cra.figaro`, including any compatibility breaks.
- Deterministic regression evidence for representative exact inference and bounded stochastic inference outputs.
- CI evidence for the supported JDK/Scala matrix.
- An SBOM and preserved `LICENSE` / `FigaroAttributions.txt` in the release materials.

## Compatibility and validation

The Scala 3 artifact is not a binary-compatible replacement for `figaro_2.13`. Recompile dependent code and validate model construction, evidence, inference results, cleanup, and supported parameter serialization. Focused modernization tests do not certify every model or deployment mode; the full historical test suite is not claimed green.

Use an immutable release for deployment once available. Until then, use a locally published snapshot for development or an explicitly versioned prerelease from a package repository. See [migration changes](docs/MIGRATION.md) for source adaptations and remaining limitations.
