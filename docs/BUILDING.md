# Building, testing, and maintaining documentation

## Overview

The sbt 2 build compiles the `figaro` library and its dependent `examples` project, runs tests, and packages the library. The root aggregates both projects; it is not an application or a published artifact. Run commands below from the repository root, with JDK 17 and an sbt runner on your path. The runner reads the pinned version in `project/build.properties`. Initial dependency resolution requires network access.

## Quick start

1. Check `java -version` reports JDK 17.
2. Compile everything: `sbt "compile; Test / compile"`.
3. Run `sbt "examples / Compile / runMain com.cra.figaro.example.documentation.QuickStart"`.

## Three common workflows

### 1. Develop and check a model

```sh
sbt "examples / Compile / runMain com.cra.figaro.example.documentation.CommonPatterns"
sbt "figaro / Test / testOnly com.cra.figaro.test.modernization.Scala3RegressionTest"
```

The first command checks two exact answers and basic validity of an estimated answer. It is not a statistical certification. The second exercises Scala 3 migration regressions. For the maintained acceptance set, use the commands in [CI](../.github/workflows/ci.yml); it additionally checks densities, lifecycle, serialization, collections, factors, and parallel structures.

`sbt "figaro / Test / test"` requests the entire historical suite. That suite is **not a green release gate**: it includes known failures, stochastic checks, and expensive learning examples. The `det` and `nonDet` configurations filter historical tags, but old tagging does not guarantee that every untagged test is deterministic. Performance-tagged timing checks in CI are explicitly advisory.

### 2. Package or consume the library

```sh
sbt "figaro / Compile / packageBin; figaro / Compile / packageSrc; figaro / Compile / packageDoc; figaro / assembly; figaro / publishLocal"
```

Outputs are under `target/out/jvm/scala-3.9.0/figaro/`:

| Artifact suffix | Purpose |
| --- | --- |
| `figaro_3-6.0.0-modern.1-SNAPSHOT.jar` | Thin library JAR; use dependency resolution for its runtime dependencies |
| `-sources.jar` | Library Scala sources |
| `-javadoc.jar` | Generated Scala 3 API documentation |
| `-fat.jar` | Library plus assembled dependencies, **excluding Scala runtime**; not a `java -jar` application |

`publishLocal` writes to sbt's local Ivy repository using Maven-style metadata. It does not publish to Maven Central or a shared server. The consumer must run as the same user with the same local repository settings. Source/doc/thin JARs carry the project's license and attribution under `META-INF`; assembly preserves them too, renaming the project's license to avoid collisions. Do not strip dependency notices.

For a genuine reproducibility comparison, record thin/fat JAR SHA-256 hashes, then bypass the sbt 2 action cache for the second build:

```sh
sbt "set Global / cacheStores := Seq.empty; clean; figaro / Compile / packageBin; figaro / assembly"
```

Compare both new hashes with the originals. A cache-restored JAR alone is not evidence of a fresh reproducible build. CI implements this comparison.

### 3. Update documentation or measure coverage

Generate the full searchable API and refresh the checked-in reference with Python 3.10+ (standard library only):

```sh
sbt "figaro / Compile / doc"
python -B tools/docs/build_reference.py
python -B -m unittest discover -s tools/docs -p "test_*.py"
python -B tools/docs/build_reference.py --check
python -B tools/docs/check_links.py
```

Open `target/out/jvm/scala-3.9.0/figaro/api/index.html` locally. Edit public contracts in source Scaladoc, not generated `docs/api` files. Edit explanations in the handwritten guides and keep their models aligned with the runnable documentation examples. The checked-in `ScalaDoc/` directory describes the legacy API; do not use it to regenerate this reference. See [documentation tooling](../tools/docs/README.md) for scope and CLI options.

Coverage is a separate workflow. On Windows especially, start it in a **fresh sbt process**, then exit before normal packaging:

```sh
sbt "set Global / cacheStores := Seq.empty; clean; coverage; figaro / Test / testOnly com.cra.figaro.test.modernization.ProbabilityRegressionTest; figaro / coverageReport; coverageOff"
sbt "clean; figaro / Compile / packageBin; figaro / assembly"
```

This smoke run verifies instrumentation/reporting, not broad coverage. Inspect the reported XML/HTML/Cobertura locations in sbt output. Never publish instrumentation-bearing artifacts.

The clean, cache-bypassed coverage build is intentional: a warmed CI run restored instrumented classes without the required `scoverage-data` directory, causing `FileNotFoundException` before any test could run. Recompiling coverage outputs avoids relying on cached compiler side effects. This is separate from Windows JAR locking; creating an empty directory alone would not establish that report metadata is complete.

## Build helper reference

These functions belong to the build definition, not the published library API. See [build.sbt](../build.sbt).

| Public build function | Parameters | Returns / behavior | Example |
| --- | --- | --- | --- |
| `readManifest(path: String)` | Path to a manifest readable by the build process | `java.util.jar.Manifest`; always closes its input stream; I/O/format errors propagate | `readManifest((baseDirectory.value / "META-INF" / "MANIFEST.MF").getPath)` inside a setting/task |
| `legalMappings(repositoryRoot: File, converter: xsbti.FileConverter)` | Repository containing `LICENSE` and `FigaroAttributions.txt`; sbt's file converter | `Seq[(xsbti.HashedVirtualFileRef, String)]`, mapping the two legal files to their archive paths | `legalMappings(baseDirectory.value.getParentFile, fileConverter.value)` inside a package-mappings task |

External manifest/legal inputs are read with `Def.uncached`; the resulting content hashes and manifest attributes still feed the parent packaging tasks. Both helpers assume this repository layout. They are not general-purpose safe path parsers.

## Gotchas and recovery

- Quote the entire semicolon-separated sbt command sequence. In PowerShell, an unquoted semicolon separates shell commands instead.
- Allow substantial memory for large inference tests. The checked-in build forks tests with a 6 GB maximum heap; running multiple sbt/test JVMs can exceed your available memory.
- Do not run concurrent builds against the same output tree. On Windows, switching a running sbt session from ordinary testing to coverage can lock an exported project JAR. Exit the sessions holding it and use separate fresh processes; do not disable test forking or run the whole build as administrator as a workaround.
- Very long Windows IPC paths can prevent forked tests from starting. Point `XDG_RUNTIME_DIR` at a short, existing, writable local directory before launching sbt. Keep it specific to your checkout/process. This is a path-length workaround, not a fix to sbt's IPC implementation.
- Custom local-repository settings can make a successful `publishLocal` invisible to a consumer using different settings. Ensure both builds resolve the same Ivy local repository.
- New compiler warnings remain visible. Deprecated `Stream`, symbol APIs, or legacy implicit syntax may compile today without being desirable new application APIs.

## Related

[User guide](USER_GUIDE.md) explains modeling; [migration](MIGRATION.md) explains compatibility and remaining validation gaps; [library](../Figaro/README.md) maps packages; [examples](../FigaroExamples/README.md) provides runnable entry points.
