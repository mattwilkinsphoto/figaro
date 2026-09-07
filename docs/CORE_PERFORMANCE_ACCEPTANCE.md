# Core performance acceptance

## Scope and pre-measurement protocol

This is acceptance of the accumulated development snapshot, not an immutable production
release. Main adopts accepted Scala 3/sbt 2 checkpoint `ed160a67`, preserving the preceding
Scala 2.13/sbt 1 baseline `b3431027` in history. The measured Scala 3 library candidate
is `71d39cbb`. The candidate's [full required Linux CI](https://github.com/mattwilkinsphoto/figaro/actions/runs/34080838730)
passes, including resource controls, lifecycle regressions, coverage, repeatable rebuilds,
publication and artifacts. Local Windows modernization checks also pass. General shared-
graph safety, unrestricted legacy compatibility and production coverage are not implied.

The cumulative performance comparison uses library baseline `0462f1b0` (before primitive
reductions, FFT and sorting optimizations) versus candidate `71d39cbb`. Both already have
the same vector samplers and bounded parallel coordinate diagnostics; this does not
measure gains from introducing those features, or Scala 2-to-3 migration speed.
Only `McmcDiagnostics.scala` differs in production source between these library points.

Use the existing [balanced runner](INTERLEAVED_PERFORMANCE_AUDIT.md#reproducing-the-paired-experiment):
four adjacent fresh-JVM pairs in alternating baseline/current order, two discarded warm-up
rounds and five measured rounds, all six fixtures, both vector methods, workers 1/2/4,
four chains, 4000 draws, 500 warm-up transitions. Retain all 2016 rows and require every
non-timing field to match `parallel-vector-diagnostics-results.csv`. Do not selectively
rerun slow cases, multiply historical stage ratios or drop mixing failures.

Both runtime snapshots use the exact same example/dependency jars from the resource
study. The benchmark source is unchanged since the baseline; only the Figaro jar is
substituted. Manifest revision identifies the library source, not a complete historical
example bundle. Verify every jar hash before each invocation and require exactly one
different jar. Java 17.0.4, the same Windows machine, 1 GiB initial / 6 GiB maximum heap
and 6 MiB stacks apply. Build and validate the independent consumer before measurement;
no local builds/tests/profiling overlap the timed comparison. Report medians/ranges of
four within-pair estimates, not confidence intervals or a universal speed promise.

## Cumulative results: what users gain

Protocol commit `0897a60b` preceded measurement. All **2016 records** pass the exact-work
check: 1440 measured records and 576 retained, discarded warm-ups from eight fresh JVMs.
The machine was a Ryzen 9950X with 16 physical cores / 32 logical processors. Results
are comparisons on this machine and runtime, not predictions for another deployment.

At four workers, the table gives the median of four within-pair gains. A gain of 2 means
the candidate takes half the baseline time. The range is across those four pairs; each
pair estimate is the median of five matched rounds, not an independent-seed confidence
interval. Every fixture/method improved in all four pair estimates; this does not say
every individual timing row improved.

| Fixture / method | Complete-inference gain | Pair range | Diagnostic-phase gain |
| --- | ---: | ---: | ---: |
| Gaussian 8-D / GPSS | 2.020x | 1.991–2.048x | 2.861x |
| Gaussian 8-D / Quantile | 1.994x | 1.851–2.181x | 3.250x |
| Gaussian 32-D / GPSS | 2.183x | 2.127–2.269x | 3.446x |
| Correlated 32-D / GPSS | 1.759x | 1.700–1.802x | 3.336x |
| Gaussian 32-D / Quantile | 1.388x | 1.304–1.404x | 3.394x |
| Correlated 32-D / Quantile | 1.161x | 1.149–1.164x | 3.111x |
| Positive 32-D / GPSS | 1.893x | 1.824–1.933x | 3.125x |
| Positive 32-D / Quantile | 1.486x | 1.341–1.565x | 3.440x |
| Likelihood 8-D / GPSS | 1.920x | 1.875–2.034x | 3.379x |
| Likelihood 8-D / Quantile | 1.273x | 1.258–1.324x | 3.367x |
| Mixture 8-D / GPSS | 1.621x | 1.595–1.648x | 3.104x |
| Mixture 8-D / Quantile | 1.372x | 1.316–1.397x | 3.168x |

These are direct cumulative gains, **not multiplied stage ratios** and not an
order-of-magnitude overall speedup. The baseline already runs chains and coordinate
diagnostics in parallel. Improving diagnostics helps less when model evaluation dominates:
correlated Quantile improves about 16% overall despite diagnostics becoming about 3.1x
faster. Sampling budgets and outputs are unchanged, including difficult mixing cases.

For existing [multi-chain vector](MULTI_CHAIN_VECTOR_SAMPLING.md) users, rebuild with the
current library: the diagnostic optimizations are automatic. For users of an ordinary
single-chain graph sampler, this table is not a promise of equivalent gains. Consider
independent chains or an explicit vector density only when those APIs fit the model;
use the [resource assessment](RESOURCE_SCALING_ASSESSMENT.md) to choose worker counts and
account for memory. Do not increase concurrency blindly or stop checking convergence.

The [complete dataset](core-performance-acceptance-results.csv) also retains one- and
two-worker measurements. Check it without rerunning the benchmark:

```sh
python3 -B tools/summarize_interleaved_performance.py check docs/core-performance-acceptance-results.csv --baseline-csv docs/parallel-vector-diagnostics-results.csv
```

For provenance, the frozen baseline/current manifest SHA-256 values are respectively
`189c88a1d384f383e5513eda855196d8b604505d8f9d1c5a5171e6e41cdfb259` and
`b2387ba172d5db77e7072b4e6afd4d1ef082a60b5ade313f37ddf6e040745d0c`.
The substituted library SHA-256 values are respectively
`841f04a616f53d2977e6c630b9d064d3772acf9a89a0469463d825834566f833` and
`27a0e71c35862f9a90594762cb49f6cf0c02c5cdf10ee7bcd79c2b59969a32f8`.
The common example JAR hash is
`bc04261e7a09d78831f37320c47fc985c0559f778667af70de8fb96f92745abe`.
Raw logs and frozen runtime copies are retained locally; the repository includes the
complete sanitized dataset, protocol and runner, not private filesystem paths.

## Independent consumer acceptance

`tools/acceptance-consumer` is a separate sbt build. It depends only on the published
`io.github.mattwilkinsphoto:figaro_3:6.0.0-modern.10-SNAPSHOT` coordinate, not the repository
project or examples. The check verifies the loaded artifact SHA-256 and absence of test,
example and coverage runtimes. It exercises exact inference, bounded parallel importance,
isolated graph/vector chains, block proposals, evaluation caps, stopping safeguards,
pre-interrupted cancellation and worker cleanup. These are consumer/API smoke controls,
not calibrated coverage or representative application performance claims.

From the repository root, with JDK 17 and matching producer/consumer local repositories:

```sh
sbt --server --batch "figaro / publishLocal"
export FIGARO_EXPECTED_SHA256=$(sha256sum target/out/jvm/scala-3.9.0/figaro/figaro_3-6.0.0-modern.10-SNAPSHOT.jar | cut -d' ' -f1)
cd tools/acceptance-consumer
sbt --server --batch "runMain FigaroConsumerCheck"
```

On PowerShell use `(Get-FileHash <jar>).Hash` to set `$env:FIGARO_EXPECTED_SHA256`.
`FigaroConsumerCheck.main(args: Array[String]): Unit` accepts no arguments, prints the
verified hash and a completion line, and throws on any failed assertion or absent/wrong
expected hash. It forks into a separate JVM with a 1 GiB heap. No benchmark timings are
reported. See [consumer instructions](../tools/acceptance-consumer/README.md).

Local publication and independent compilation/run passed on Windows Java 17.0.4. The
same compiled consumer also ran successfully against the downloaded Linux-built thin
artifact from CI `34080838730`, with its declared runtime dependencies. This latter
check is a JVM runtime-consumption check, not a new compiler/TASTY compatibility claim.
The [acceptance protocol CI](https://github.com/mattwilkinsphoto/figaro/actions/runs/34082303985)
also passed independent compilation and execution on Linux, along with lifecycle,
coverage, clean binary rebuild and publication checks.

## Artifact inspection: quick start and tooling API

After building the thin/fat/source/API JARs, run these read-only checks from the root:

```sh
python3 -B -m unittest discover -s tools -p 'test_check_acceptance_artifacts.py'
python3 -B tools/check_acceptance_artifacts.py target/out/jvm/scala-3.9.0/figaro
```

The CLI requires one directory containing all four modern.10 JARs. Optional
`--legal-root PATH` supplies a different root containing `LICENSE` and
`FigaroAttributions.txt`; the default is this repository. It prints one JSON record per
artifact with filename, entry count and SHA-256; any failure exits nonzero. It does not
extract or execute archive contents, inspect dependency vulnerabilities, or establish
that arbitrary applications work.

`validate(archive, kind, legal) -> int` accepts an open `zipfile.ZipFile`, a kind of
`thin`, `fat`, `sources` or `javadoc`, and a mapping of required archive names to expected
legal-file bytes. It returns the entry count or raises on invalid content. For example,
`validate(archive, 'thin', {'META-INF/LICENSE': Path('LICENSE').read_bytes()})` checks a
thin archive and that notice; use the CLI to require **both** repository notices.
`main() -> None` parses CLI arguments, opens each artifact, calls `validate` and prints
the records. The module has no application-library API and no write side effects.

Three common uses are checking locally built artifacts with the command above,
checking downloaded CI artifacts by passing their directory instead, and running the
same command as a publication gate. CI checks all four artifacts before the independent
consumer gate. The seven in-memory unit tests cover valid packaging and rejected
missing/altered notices, leaked runtimes, missing classes/classifiers and invalid bytecode.

Checks include unique entry names, required Figaro runtime classes, Java-17-compatible
class headers, source/API entry points and absence of known test/coverage/example
packages or coverage instrumentation. Thin JARs cannot bundle the Scala namespace;
fat JARs can contain the declared parallel-collections dependency but are checked for
core Scala runtime markers. This is a targeted packaging gate, not exhaustive classpath
or bytecode verification. Core Scala dependencies must still be supplied by the consumer.

Assembly renames Figaro's license to
`META-INF/LICENSE_figaro-6.0.0-modern.10-SNAPSHOT`; the checker accepts that exact entry
for the fat JAR and validates its full content. Another dependency's license cannot
substitute for Figaro's. Only CRLF/LF differences are normalized. Version changes require
updating the pinned filenames and assembly-license entry in the checker.

All four downloaded artifacts from CI `34080838730` pass inspection (thin 1627 entries,
fat 3781, sources 320, API 1162). The Linux thin JAR SHA-256 is
`1f434ee6f2a0502d57f0209f65d31cbb9551659566b9e9454e97f1b4b09f9ea2`.
It has the same entry names as the local thin JAR but 276 content-different entries:
two legal files differ only by line endings and 274 class/TASTY entries also differ.
The latter differences have not been diagnosed. Successful clean repeat builds within
CI do **not** establish Windows/Linux byte identity; do not compare those hashes as
though they came from an identical build environment.

## Known boundaries for integration

- Snapshot coordinates are development-only; there is no new version/tag/public release.
- Scala 2 consumers must recompile and follow [migration](MIGRATION.md); retired APIs are
  deliberately removed. Existing migration workarounds remain documented.
- Seed assignment is stable, but general graph traversal is not cross-JVM bitwise
  reproducibility. The [resource graph control](RESOURCE_SCALING_ASSESSMENT.md) uses a
  specifically ordered joint proposal.
- Wrong-mode mixing and precision undercoverage are not fixed by faster execution. Keep
  diagnostics, budgets and the [reliability safeguards](MCMC_RELIABILITY.md).
- The mixed-workload positive-Quantile slow-sampling state remains unexplained even though
  it did not recur in isolated resource runs. Hardware bandwidth/NUMA diagnosis is absent.
- OSGi manifests retain legacy metadata and are not a validated deployment contract.
  Support here is ordinary JVM classpath consumption; Java facades/custom loaders and
  arbitrary application models require their own acceptance.

The evidence supports integration of this development baseline. It does not broaden
the supported deployment matrix or establish that all historical tests are green.

## Integration record and verification

The accepted checkpoint is `ed160a67`; its required branch workflow is
[run 34083664890](https://github.com/mattwilkinsphoto/figaro/actions/runs/34083664890).
Integration fast-forwards main from `b3431027`, retaining the complete baseline,
protocol and performance history. The accompanying documentation-only commit changes
checkout instructions to main; it does not change the tested library or CI gates.

Check the [main-branch workflow](https://github.com/mattwilkinsphoto/figaro/actions/workflows/ci.yml?query=branch%3Amain)
for the exact integration commit's result, including the standalone consumer, datasets,
lifecycle, coverage, artifacts and clean rebuild. Branch success is not a substitute for
that post-integration check. Snapshot modern.10 remains unchanged; versioning, tagging
and public publication require a separate release decision.

The preceding main commit is
`b3431027c1da5980d41993f4e484620a4baf163c`. It remains reachable in history rather than
as a separately maintained legacy line. If rollback becomes necessary, first assess
downstream dependency/source changes and use a reviewed revert or new corrective commit;
do not force-reset shared main or silently swap Scala 3 dependencies for Scala 2 binaries.

Downstream Scala 2 applications do not acquire these changes automatically: the Scala 3
artifact has a different binary coordinate and requires recompilation. Do not remove
their old dependency until the application's own migration tests pass.
