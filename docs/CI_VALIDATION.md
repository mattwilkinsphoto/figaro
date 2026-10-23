# Collection benchmarks and sbt worker validation

Status: local repair validation and stock-runner branch CI completed; isolated
Linux repair validation and maintainer review are separate gates. No merge,
release or upstream submission is implied.
[Research provenance](RESEARCH_REFERENCES.md#eng-01).

## Why the enumeration test changed

The old enumeration assertion divided `(t4000 - t3000)` by `(t2000 - t1000)` and
required a result below 1.1. Small or negative differences made this unstable:
negative ratios could pass while otherwise correct code could fail. The test
discarded enumeration results and measured different hash-table capacity regimes.
Its threshold was not a reliable test of linear complexity or a regression bound.

The replacement separates two different questions:

1. **Correctness:** the ordinary ScalaTest suite verifies complete, duplicate-free
   enumeration before and after removals at all four sizes. This is a required
   check, not a timing assertion.
2. **Performance:** the standalone [JMH runner](../tools/collection-benchmark/run.py)
   consumes the result, warms up each JVM, uses fresh forks, profiles allocation,
   and alternates baseline/candidate order. It retains every measurement. Missing,
   malformed or failed runs fail the runner; execution success is not evidence of
   a speedup or a performance acceptance decision.

The other legacy collection timing assertions remain advisory and unchanged.
This replacement does not claim to repair them. Do not interpret a green workflow
as proof that a `continue-on-error` timing step passed.

## Running the benchmark

Use Python 3.11+ and a JDK 17 with `java` and `javac` on PATH. Build the candidate
JAR first. Run from the repository root, choosing a new output directory:

```text
python -B tools/collection-benchmark/run.py --candidate target/out/jvm/scala-3.9.0/figaro/figaro_3-7.4.0-SNAPSHOT.jar --output .cache/collection-full --profile full
```

The immutable reference is the SHA-256-pinned Maven Central 7.3.0 JAR. JMH 1.37
and its dependencies are also pinned; none is added to Figaro's runtime or POM.
`--profile smoke` checks execution and output structure only. CI uses that mode
without a speed threshold. For full measurements, use a quiet machine and retain
the protocol, per-fork JSON, command logs and artifact hashes together.

The full protocol is four sizes (1,000 through 4,000), three fresh JVMs per
artifact/size, five one-second warm-up and five one-second measurement iterations,
one thread, 512 MiB heap, and GC allocation profiling. The runner writes the
protocol before measurement. Compare absolute times, allocation and variability;
do not calculate a ratio of timing differences. Do not select successful forks or
rerun until a preferred conclusion appears. A future regression threshold needs
separate baseline calibration and a documented operating envelope.

On Windows, set `FIGARO_WORKSPACE_OWNER` to the non-administrator workspace owner;
the tools preserve ACLs and verify an explicit Full Control grant on generated
items. An optional `FIGARO_VALIDATION_DOWNLOAD_CACHE` directory supplies previously
downloaded files by URL basename. SHA-256 verification is identical in that mode;
it is not a TLS-verification bypass. Record the cache provenance with the results.

## Narrow sbt repair

The observed sbt 2.0.8 exception arose when worker response handling and process
exit raced between `isCompleted` and a throwing Promise completion. The proposed
repair changes only five calls in `React`: `success` to `trySuccess` and `failure`
to `tryFailure`. See the [review draft](../tools/sbt-worker-repair/UPSTREAM_REPORT.md)
and [reproduction instructions](../tools/sbt-worker-repair/README.md).

The preparation tool refuses unreviewed source/JAR hashes, copies the stock boot
tree to a new, disjoint directory, compiles only `React`, and verifies that only
`sbt/React.class` and `sbt/React.tasty` change. The stock cache is not modified.
The repaired boot is explicitly selected for diagnostic runs; it is **not**
automatically installed into developer environments or the standard CI runner.

First completion still wins. The patch is not a redesign of sbt's worker/event
protocol and does not guarantee that a later contradictory error overrides an
earlier successful completion. It must not hide test assertion failures or abnormal
worker exits. Reproduction uses both deterministic race controls and real forked
tests, not a passing Figaro suite alone. Reverting means selecting the original
boot directory again; no published binary or shared cache needs restoration.

## Local evidence and remaining gates

Local validation on JDK 17 / sbt 2.0.8 / Scala 3.9.0 established:

- Stock sbt reproduced 40 duplicate-completion exceptions in 160 controlled cases;
  the repaired class produced none in the same 160 cases. The matrix includes
  response/exit orderings, protocol errors, wrong IDs and duplicate exit signals.
- Real forked fixtures reported all 100 passing events, all 100 passing plus one
  deliberately failing assertion, and a separate worker exit code 17. The latter
  two builds failed as required. No completion exception appeared.
- Nine focused Figaro suites passed all 102 tests. The loaded `React` code source
  was checked against the isolated boot directory, not inferred from arguments.
- All 24 full-protocol benchmark runs completed with valid output. Timing varied
  substantially across forks and sizes; these results establish harness execution,
  not a performance improvement, regression, or calibrated speed gate.

Stock-runner [branch CI](https://github.com/mattwilkinsphoto/figaro/actions/runs/39896801206)
passed all four jobs, the four-fork benchmark smoke check and the advisory step
(20 tests, zero failures); no duplicate-Promise exception appeared in its logs.
This did not exercise the repaired boot. The separate
[Linux validation workflow](../.github/workflows/sbt-repair-linux.yml) freezes the
same repair matrix, forked failure fixtures and 102 focused Figaro tests, verifies
actual repaired-class loading, and preserves the original stock JAR hashes.
Its results must be reviewed; merely adding the workflow is not a validation pass.

The evidence archives retain inputs, hashes, commands and unfiltered logs.
These are bounded results, not upstream acceptance. Before promotion: review the
patch/report and Linux results, then validate any proposed CI-wide repaired-runner
activation separately. Existing guided-filter independent
validation remains applicable but does not waive these tooling gates. The released
stopping estimator, inference defaults and publication workflow remain unchanged.
