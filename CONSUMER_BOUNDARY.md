# JVM consumer boundary

The Figaro modernization repository and the traffic-analysis repository remain independent. Vehicle-specific models, data schemas, and traffic workflows do not belong in Figaro core.

## Intended boundary

The consumer should depend on a compiled, versioned JVM artifact rather than source-copying or nesting repositories. The provisional coordinates for modernization builds are:

```scala
libraryDependencies += "io.github.mattwilkinsphoto" %% "figaro" % "<released-version>"
```

Java consumers will use the corresponding binary artifact (`figaro_3` for the modernized line) together with its declared Scala/runtime dependencies. The published POM is the dependency contract.

## Release requirements before traffic integration

- A tagged, immutable Figaro release built on Java 17.
- A published thin library JAR, sources JAR, Scaladoc/Javadoc artifact where practical, and dependency POM.
- Stable public package/API surface under the existing `com.cra.figaro` namespace unless a separately reviewed compatibility decision changes it.
- Deterministic regression evidence for representative exact inference and bounded stochastic inference outputs.
- CI evidence for the supported JDK/Scala matrix.
- An SBOM and preserved `LICENSE` / `FigaroAttributions.txt` in the release materials.

## Consumer integration rule

The traffic software may add an adapter module that translates its domain objects into Figaro model construction calls. That adapter should own vehicle-specific types and configuration. Figaro core should not import or depend on the traffic repository.

Until a stable release exists, integration work should use only a local published snapshot (`publishLocal`) or a versioned prerelease from a package repository; it should not reference this repository by filesystem path.
