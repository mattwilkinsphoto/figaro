# Building, testing, and maintaining documentation

## Overview

The sbt 2 build compiles the `figaro` library and its dependent `examples` project, runs tests, and packages the library. The root aggregates both projects; it is not an application or a published artifact. Run commands below from the repository root, with JDK 17 and an sbt runner on your path. The runner reads the pinned version in `project/build.properties`. Initial dependency resolution requires network access.

CI passes `--server --batch` directly to each `sbt` invocation to run the build JVM in the foreground. This avoids
the native thin-client startup race seen in run `34048566245`, where an incomplete
`active.json` prevented connection before compilation began. For the same symptom locally,
use `sbt --server --batch "compile"`; ordinary interactive development need not change.
Do not put these runner switches in `SBT_OPTS`: the installed runner passed them through
to Java, which rejected `--server` in the first attempted CI fix.
The [sbt runner reference](https://www.scala-sbt.org/2.x/docs/en/reference/sbt.html) distinguishes
the runner/client from the build JVM. This setting changes process startup, not the pinned
sbt version, Scala compiler, sampling algorithms, or test requirements.

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
| `figaro_3-6.0.0-modern.15-SNAPSHOT.jar` | Thin library JAR; use dependency resolution for its runtime dependencies |
| `-sources.jar` | Library Scala sources |
| `-javadoc.jar` | Generated Scala 3 API documentation |
| `-fat.jar` | Library plus assembled dependencies, **excluding Scala runtime**; not a `java -jar` application |

`publishLocal` writes to sbt's local Ivy repository using Maven-style metadata. It does not publish to Maven Central or a shared server. The consumer must run as the same user with the same local repository settings. Source/doc/thin JARs carry the project's license and attribution under `META-INF`; assembly preserves them too, renaming the project's license to avoid collisions. Do not strip dependency notices.

For a genuine reproducibility comparison, use two **new, empty action-cache
directories** and clean outputs for both builds. Example POSIX shell commands:

```sh
test ! -e /tmp/figaro-repro-a && test ! -e /tmp/figaro-repro-b
sbt -Dsbt.global.localcache=/tmp/figaro-repro-a "clean; figaro / Compile / packageBin; figaro / assembly"
# Record the thin/fat JAR SHA-256 hashes before the second build.
sbt -Dsbt.global.localcache=/tmp/figaro-repro-b "clean; figaro / Compile / packageBin; figaro / assembly"
```

Choose unused task-specific paths on your system (on Windows, quote an absolute
directory under your workspace). Do not delete a shared cache to run this check.
Compare both hashes and require the logs to show actual compilation in each build.
CI uses separate runner-temporary caches and explicitly checks those compile lines.

The earlier `set Global / cacheStores := Seq.empty` instruction was insufficient in
sbt 2.0.8: it left disk cache hits enabled. A cache-restored JAR is not fresh-build
evidence. The [sbt 2.0.8 cache-path implementation](https://github.com/sbt/sbt/blob/v2.0.8/main/src/main/scala/sbt/internal/SysProp.scala)
supports `sbt.global.localcache`; this redirects only the action cache, not the
dependency download cache. The [sbt caching guide](https://www.scala-sbt.org/2.x/docs/en/concepts/caching.html)
explains machine-wide task caching. Incremental and clean compilation can produce
different class/TASTy bytes: use clean artifacts for distribution and compare
like-for-like toolchains. Historical cache-disabled reproducibility claims should
not be read as proof of two independent compilations unless the logs establish it.

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
test ! -e /tmp/figaro-coverage-cold
sbt -Dsbt.global.localcache=/tmp/figaro-coverage-cold "clean; coverage; figaro / Test / testOnly com.cra.figaro.test.modernization.ProbabilityRegressionTest; figaro / coverageReport; coverageOff"
sbt "clean; figaro / Compile / packageBin; figaro / assembly"
```

This smoke run verifies instrumentation/reporting, not broad coverage. Inspect the reported XML/HTML/Cobertura locations in sbt output. Never publish instrumentation-bearing artifacts.

The clean, cache-bypassed coverage build is intentional: a warmed CI run restored instrumented classes without the required `scoverage-data` directory, causing `FileNotFoundException` before any test could run. Recompiling coverage outputs avoids relying on cached compiler side effects. This is separate from Windows JAR locking; creating an empty directory alone would not establish that report metadata is complete.

## Parallel-performance checks

Run `sbt "figaro / Test / testOnly com.cra.figaro.test.modernization.ParallelPerformanceRegressionTest"` for RNG isolation, worker ownership, exact budget allocation, weighting, failure cleanup, and cooperative cancellation. Run `sbt "examples / Compile / runMain com.cra.figaro.example.ParallelSamplingExample"` for the user-facing example. The [benchmark guide](PARALLEL_PERFORMANCE.md#3-measure-before-choosing-a-worker-count) supplies reproducible performance commands; timings do not gate CI.

## Multi-chain MCMC checks

Run `sbt "figaro / Test / testOnly com.cra.figaro.test.modernization.MultiChainMcmcRegressionTest com.cra.figaro.test.modernization.McmcDiagnosticsRegressionTest"` for posterior correctness, retained rejection states, dynamic ownership, worker-count seed independence, initialization limits, failure/cancellation cleanup, and independently checked diagnostics. Run `sbt "examples / Compile / runMain com.cra.figaro.example.MultiChainMcmcExample"` for the complete user example. The [multi-chain benchmark](MULTI_CHAIN_MCMC.md#performance-and-resource-planning) keeps chain count and sampling work fixed while varying concurrency. Timings are diagnostic, never a CI accuracy gate.

## Pilot calibration checks

For pilot calibration, run `sbt "figaro / Test / testOnly com.cra.figaro.test.modernization.GaussianBlockCalibrationRegressionTest com.cra.figaro.test.modernization.GaussianBlockProposalRegressionTest"` and `sbt "examples / Compile / runMain com.cra.figaro.example.ProposalCalibrationExample"`. The [calibration validation guide](PROPOSAL_CALIBRATION_VALIDATION.md) gives broader repeated-seed benchmark commands. Pilot-inclusive performance and statistical coverage are measured, not enforced as brittle CI timing thresholds.

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
- Deprecations are errors through `-Wconf:cat=deprecation:error`, including test and example compilation. Other compiler diagnostics remain visible; no warning suppression or migration mode is retained. See [deprecation retirement](DEPRECATION_RETIREMENT.md) for replacements and compatibility changes.

## Related

[User guide](USER_GUIDE.md) explains modeling; [migration](MIGRATION.md) explains compatibility and remaining validation gaps; [library](../Figaro/README.md) maps packages; [examples](../FigaroExamples/README.md) provides runnable entry points.
