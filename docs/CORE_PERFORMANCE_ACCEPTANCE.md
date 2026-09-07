# Core performance acceptance

## Scope and pre-measurement protocol

This is acceptance of the accumulated development snapshot, not an immutable production
release. Main still contains Scala 2.13/sbt 1 baseline `b3431027`; the Scala 3 candidate
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

The evidence supports deciding whether to integrate this development baseline. It does
not authorize silently broadening the supported deployment matrix or treating all
historical tests as green. Main is not moved while acceptance work is incomplete.
