# Collection benchmarks and sbt worker validation

Status: local and isolated Linux repair validation completed; maintainer review
and any standard-runner activation remain separate gates. No merge, release or
upstream submission is implied.
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
[Linux run 39899930618](https://github.com/mattwilkinsphoto/figaro/actions/runs/39899930618)
passed at commit `0d151029` on the Ubuntu 24 image, Temurin 17.0.20.1 and Python
3.12.15. Downloaded evidence was independently checked: the same 40 forced stock
exceptions and zero repaired exceptions across 160 cases per binary; the expected
passing/assertion-failing/exit-17 fixture outcomes; and 102 passing Figaro tests
with the repaired JAR actually loaded. All stock boot JAR hashes remained unchanged.
The downloaded stock/repaired actions JARs differ only in `React.class` and
`React.tasty`; source snapshots and command-log hashes were also verified.

These forced case counts are not estimates of a naturally occurring race rate.
The Linux check does not add a separately forced `forkError` race or change
first-completion semantics. A clean stock-runner CI run alone would not establish
this result; this dedicated run exercised the repaired binary explicitly.

The companion [stock-runner CI run 39899930482](https://github.com/mattwilkinsphoto/figaro/actions/runs/39899930482)
completed all four jobs successfully, including reproducibility and packaged-consumer
checks; all 20 remaining advisory timing tests passed. Its raw log nevertheless
contains one `Promise already completed` exception at `React.notifyExit`
(`ForkTests.scala:204`) during the continuous-vector checks, whose 18 tests passed.
This is another observation of the stock-runner symptom, not a failure of the
isolated repaired run or proof that green CI means the worker log is clean.

The evidence archives retain inputs, hashes, commands and unfiltered logs.
These are bounded results, not upstream acceptance. Before promotion: review the
patch/report and Linux results, then validate any proposed CI-wide repaired-runner
activation separately. Existing guided-filter independent
validation remains applicable but does not waive these tooling gates. The released
stopping estimator, inference defaults and publication workflow remain unchanged.

## Review: narrowly scoped CI adoption

**Decision: suitable for a branch-only, non-authoritative canary after the gates
below; not ready to replace the standard CI runner.** This is a review recommendation,
not activation. No workflow, repair code, runtime default or release setting was
changed by the review. Existing Linux results remain the evidence baseline; the
additional controls below had not been executed at that review. See the pilot
implementation status below for subsequent work.

### Findings and alternatives

The five-site patch uses the documented [Promise completion API](https://www.scala-lang.org/api/current/scala/concurrent/Promise.html).
The response success/error and exit success/error sites retain their first winner;
the `forkError` site likewise cannot replace an already completed Promise. A
duplicate `trySuccess`/`tryFailure` returns false instead of throwing. This does not
make competing success/error outcomes deterministic or establish which should win.
In stock `React.apply`, the existing `NonFatal` handler already discards exceptions
from duplicate response/notification completion. The patch removes the uncaught
exit-thread exception; it is not a new policy for preserving late errors.

The strongest favorable evidence is the unchanged controlled matrix plus real
assertion-failure and abnormal-exit controls on Windows and Linux. The strongest
limitation is that `forkError` is a changed site without its own explicit ordering
control, and successful process completion does not prove all test notifications
have been drained. Source inspection also shows exit notification iterating the
shared listener list outside the synchronization used for response dispatch and
listener registration. These are pre-existing protocol concerns, not demonstrated
new failures caused by this repair; neither has been validated away.

The [latest official release inspected](https://github.com/sbt/sbt/releases/tag/v2.0.11)
was 2.0.11. Fresh retrieval of its `ForkTests.scala` and the 2.0.8 source produced
the same pinned SHA-256 recorded in the [report](../tools/sbt-worker-repair/UPSTREAM_REPORT.md).
That is source evidence, not a binary test of 2.0.11, an exhaustive search of upstream
work, or proof that no other lifecycle changes exist. An upgrade is not presently
an evidence-backed substitute for this repair. Keep the stock runner as the
comparison baseline; do not serialize tests further, disable forking, filter error
output or weaken assertions to hide the exception.

| Review area | Evidence and disposition |
| --- | --- |
| Patch containment | Exact upstream source/actions-JAR pins; only `React.class` and `React.tasty` differ. Acceptable for an isolated canary, not an official sbt distribution. |
| Failure handling | Ordinary assertions, protocol error responses and abnormal exits tested. Add explicit `forkError` orderings before using the repaired result as a CI gate. |
| Runner identity | Linux verified a direct launcher with fresh boot and server autostart disabled. Standard CI uses `sbt --server --batch`; that mode is not covered by the repaired Linux run. |
| Outcome completeness | Focused run verified 102 tests, not every standard job. A green exit alone is insufficient, as the stock companion run demonstrates. |
| Isolation and rollback | Disjoint boot supported by [launcher configuration](https://www.scala-sbt.org/1.x/docs/Launcher-Configuration.html) and verified on Linux. Keep both stock boot and stock CI unchanged; discard candidate job state to roll back. |

### Bounded implementation proposal (approval required)

1. **Close the changed-site gap without broadening the patch.** Retain the original
   eight-case matrix and add separately identified `forkError` controls using actual
   sbt classes and valid protocol payloads: error before success/exit, error during
   exit handling, duplicate error, success before late error, and wrong request ID.
   Verify the winning outcome as well as exception behavior. A first error must
   remain a failure; success before a contradictory late error must be recorded as
   the existing limitation, not renamed a safe outcome. Add a real worker/framework
   error fixture that proves notification processing and a nonzero build exit.
   Retain the passing, assertion-failing and exit-17 fixtures unchanged. If the new
   evidence reveals lost failures, stop and review the protocol; do not patch more
   sites or redefine acceptance automatically.

2. **Run a separate branch canary, retaining stock CI.** Use one explicit direct-Java
   entry point, not a PATH replacement, global environment override or persistent
   sbt server. Limit scope to the existing nine suites plus
   `VectorSliceSamplerRegressionTest` and `MultiChainVectorSliceSamplerRegressionTest`,
   covering the latest observed stock exception. Freeze expected suite/test identity
   before running. Execute two predeclared passes in fresh JVMs with distinct boot,
   home, target/output and action-cache roots; no restored test-result/action cache.
   This is a bounded repeatability check, not a reliability-rate estimate. Require
   the matrix and negative controls first, then full suite outcomes on both passes.
   A canary failure remains red and retained; do not retry until green. It cannot
   substitute for a failed standard gate or authorize a merge/release.

3. **Review the canary before adopting any required gate.** If approved after those
   results, select only the named test invocations for a repaired direct-launcher
   gate. Keep packaging, compatibility, publication, developer commands and all other
   jobs on stock sbt. No initial migration to repaired server mode. Expanding to that
   mode would need a separate same-process/repeated-command test of boot identity,
   negative controls and lifecycle behavior. A full standard-runner replacement is
   outside this proposal.

### Required containment and acceptance contract

- Fail closed on launcher/source/actions-JAR/version mismatch, unexpected compiled
  entries, missing evidence, wrong loaded code source or incomplete outcomes. Never
  silently fall back to stock and label the run repaired. Compare relative
  compiler-input hashes with the reviewed Linux manifest; any drift requires review.
- Use a disposable runner, read-only repository token, no publishing/signing secrets,
  no privileged pull-request trigger, and no upload of repaired files into normal
  dependency caches or release artifacts. Retain upstream license and notices.
- Resolve launcher and boot explicitly; verify `React` location and JAR hash inside
  **each** invocation, not just a separate preflight JVM. Preserve resource options
  deliberately; direct Java does not automatically interpret the sbt shell runner's
  `JAVA_OPTS`, `--server` or `--batch` options. Keep socket paths short and isolated.
- Capture raw stdout/stderr, exit status, commands, hashes and complete per-suite
  outcomes even on failure. Reject unexpected worker exceptions, aborts, cancellations,
  missing/duplicate suites and empty or cached-only executions. Keep intentional
  failing-fixture results distinct from ordinary Figaro test success. Retain the
  stock comparison even when it contains a green-exit worker exception.
- Enforce job and subprocess timeouts. If required evidence cannot be produced,
  report an incomplete gate rather than promotion. Do not add exception swallowing
  or change test counts/thresholds to make the gate pass.
- Rollback disables the canary or removes only explicitly adopted test invocations;
  stock CI and caches need no repair. Retain failed evidence. Reassess on any sbt,
  Scala boot compiler, JDK, runner-image or patch change. Retire the workaround only
  after an official replacement passes the same reproduction and failure controls.

This work is repository CI tooling, not a Figaro runtime capability or a reason by
itself to publish a new library version. Merge, release, upstream submission and
activation remain separate approval decisions.

### Pilot implementation status

The approved branch-only pilot is implemented in
[branch_pilot.py](../tools/sbt-worker-repair/branch_pilot.py), with the standard
workflow and the five-site repair unchanged. It adds seven separately identified
notification orderings (280 cases across both binaries), a real framework-error
fixture, and two fresh 131-test/11-suite passes. The original 320-case matrix and
passing/assertion-failing/exit-17 controls remain mandatory. The dedicated workflow
is branch-restricted and uses Ubuntu 24.04, JDK 17 and read-only permissions.

Local notification controls passed and the corrected framework fixture verified
the actual `ForkError` failure. The first fixture attempt failed discovery rather
than exercising the notification; validation rejected it, and that attempt was
retained. Its nested fingerprint base was replaced with a top-level base. No
acceptance criterion or repair changed. Remote pilot results are pending; this
implementation is not promotion or proof of a complete worker-protocol repair.

The inventory and compiler pins are frozen before execution. Each pilot pass
requires a fresh checkout/cache, in-process runner location/hash, complete JUnit
identities and explicit successful outcomes. Missing, duplicate, skipped, failed,
cached-only or wrong-runner results fail closed. Evidence is retained on failure;
neither pass is a retry of the other. The remaining first-success/late-error
limitation is an asserted observation, not labeled safe failure handling.
