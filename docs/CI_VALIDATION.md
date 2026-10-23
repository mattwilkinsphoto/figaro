# Collection benchmarks and sbt worker validation

Status: stock sbt 2.0.11 adoption passed full CI and packaged-consumer acceptance
at implementation commit `c04ff1b8`; it remains on a review branch. The local repair is retired
from active workflows. Figaro 7.4.0 remains the published release; its historical
validation and advisory timing results are preserved below. No merge, new release
or upstream submission is authorized by this tooling change.
[Research provenance](RESEARCH_REFERENCES.md#eng-01).

## Stock sbt 2.0.11 adoption

Both source-build selectors now use 2.0.11. The existing named direct-launcher job
runs [the stock harness](../tools/sbt-worker-repair/stock_linux.py) with
`--mode direct --ci-gate`, checksum-verifying official launcher/actions binaries.
It retains all 300 forced React cases, five real fixtures (pass, assertion failure,
exit 17, framework error, upstream slow listener), and two fresh complete 131-test
passes at the current CI commit. The forced cases still expect the documented
60 duplicate exceptions: they bypass response draining and are not a natural race
rate. Real builds must surface failures and may not emit those exceptions.

Only the unstable historical 2.0.8 false-green reproduction is excluded from the
active candidate-only gate; its original protocol and failed observations remain
unchanged below. No test identity, statistical threshold or failure expectation
has been relaxed. Fresh compilation, exact JUnit identities, loaded-binary hashes,
unchanged stock boot checks and failure evidence remain mandatory.

The retired [workflow snapshot](../tools/sbt-worker-repair/legacy-linux-workflow.yml.example)
is outside `.github/workflows`; `branch_pilot.py --ci-gate` now refuses execution.
Historical repair tools remain for evidence replay, not automatic installation.
All other full CI gates remain in place, including instrumentation, compatibility,
reproducible packaging and all six packaged consumers. The publication workflow
is unchanged and is not run. Merge and upstream submission remain on hold.

Acceptance: [full run 40006071239](https://github.com/mattwilkinsphoto/figaro/actions/runs/40006071239)
passed all five jobs and every step at `c04ff1b8`, including the unchanged advisory
timing checks. Independent auditing verified all 504 named-gate payload files,
16 captured command logs, official binary hashes, five expected fixture exits and
both fresh exact 131-test inventories. All 73 logged ordinary sbt starts used 2.0.11;
no unexpected worker-completion exception appeared. All six consumers passed in
preflight, local-publication and Maven-layout-without-Ivy-local paths, each checking
the expected artifact hash. Both fresh builds produced identical thin/fat JARs.

The rebuilt thin JAR is byte-identical to published 7.4.0 (SHA-256
`0abfdadfeb007af1eee39a04b0591e49686d4cab74181402ccf8d9948abe4dbf`);
source/Scaladoc/POM hashes also match the release record. Bundle metadata now
correctly records the source selector as 2.0.11. Local checks passed 84 tests plus
reference/navigation validation. These results support merge review, not automatic
merge or upstream submission. This tooling-only change does not warrant a new
library release; published artifacts and tags remain untouched.

## Stock 2.0.11 Linux/server follow-up

Historical pre-adoption assessment: bounded stock candidate checks completed. The
[bounded protocol](../tools/sbt-worker-repair/README.md#branch-only-linux-and-server-assessment)
retains the existing repair and standard CI while testing stock 2.0.11 in isolated
Linux direct-launcher, foreground `--server --batch`, persistent `--client`, and
coverage/packaging contexts. The official
[runner documentation](https://www.scala-sbt.org/2.x/docs/en/guide/sbt-components.html)
and [tagged source](https://github.com/sbt/sbt/blob/v2.0.11/sbt) distinguish these
invocations; `--server --batch` alone is not evidence of persistent-session reuse.
The native-client control requires unchanged server PID and successful recovery
after intentional failures. The original baseline reproduction check remains
unchanged and can leave its job red independently of candidate correctness.
No production adoption, merge, release or upstream submission is implied.

[First run 39992354718](https://github.com/mattwilkinsphoto/figaro/actions/runs/39992354718)
passed direct-launcher and foreground-server controls, both fresh 131-test passes
in each mode, and the packaging job. The persistent-client stage failed before
fixture execution: sbtn joined the separate arguments into one invalid `eval`
expression. The correction sends one explicit semicolon-delimited command sequence,
with the test last, following the [command syntax](https://www.scala-sbt.org/2.x/docs/en/reference/sbt.html).
It does not change any fixture or expected outcome. The first upload also omitted
the workflow snapshot under `.github`; hidden files are now included only inside
the curated evidence tree. Neither failure is erased or classified as an sbt defect.

[Second run 39994305620](https://github.com/mattwilkinsphoto/figaro/actions/runs/39994305620)
again passed direct and packaging jobs, and all candidate foreground checks.
The old 2.0.8 foreground false-green outcome did not recur, leaving that baseline
check failed. The persistent client verified JAR/PID but rejected inline arguments
to `testFull` before testing. The focused follow-up uses the documented
`testOptions` setting and resets filters before negative controls. Explicit
client-only dispatch avoids rerunning the unchanged baseline; prior failed runs
are retained. See the [report](../tools/sbt-worker-repair/UPSTREAM_REPORT.md#linux-and-server-assessment-2026-10-23).

[Focused client run 39996678945](https://github.com/mattwilkinsphoto/figaro/actions/runs/39996678945)
passed with one verified server PID across pass/assertion/exit-17/recovery, exact
JUnit identities and successful owned-server shutdown. Complete manifests and raw
outputs were audited. Two local syntax preflights failed on a Windows socket-path
limit and are not counted as passes. The completed candidate gates support review
of a controlled stock upgrade, not a claim that all prior workflow runs passed.
At that assessment revision, production selectors and the adopted repair remained
unchanged. The separately approved adoption above requires its own full CI and
packaging/consumer acceptance; upstream submission remains on hold.

The [second companion run](https://github.com/mattwilkinsphoto/figaro/actions/runs/39994305544)
was green with one failed advisory search-timing assertion (19/20 passed;
`SelectableSetTest.scala:120`, 2.42082170161744 versus the 2.4 threshold). This used
the unchanged 2.0.8 setup, not the candidate. It is retained as an unresolved
advisory result, not silently counted as passed.

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

### 7.4.0 final-source advisory result

[Final-source run 39975157515](https://github.com/mattwilkinsphoto/figaro/actions/runs/39975157515)
passed all five jobs and required packaging/consumer gates, but its legacy
selection-timing assertion failed: 19 advisory tests passed and one failed;
ratio 2.91156 exceeded the unchanged bound 2.42. All 99 utility class/TASTy
entries are byte-identical to published 7.3.0. The test aggregates timings in
one JVM and does not retain individual trials, hash-table layouts or GC/JIT
profiles. The cause cannot be determined from that result. No rerun or acceptance
threshold change was used to obtain release approval under the existing advisory policy.

OpenJDK's [JMH forking example](https://github.com/openjdk/jmh/blob/master/jmh-samples/src/main/java/org/openjdk/jmh/samples/JMHSample_12_Forking.java)
and [loop example](https://github.com/openjdk/jmh/blob/master/jmh-samples/src/main/java/org/openjdk/jmh/samples/JMHSample_11_Loops.java)
explain relevant measurement risks; they do not diagnose this particular failure.
A future selection-performance investigation should retain fixture/layout details
and use independently calibrated, forked measurements. The enumeration benchmark
below does not validate selection timing, and no selection speedup is claimed.

## Running the benchmark

Use Python 3.11+ and a JDK 17 with `java` and `javac` on PATH. Build the candidate
JAR first. Run from the repository root, choosing a new output directory:

```text
python -B tools/collection-benchmark/run.py --candidate target/out/jvm/scala-3.9.0/figaro/figaro_3-7.4.0.jar --output .cache/collection-full --profile full
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

Historical sections below describe the 2.0.8 repair, its original approval and
results. They are not current activation instructions; stock adoption above
supersedes the repaired named gate. Use the recorded historical commits for replay.

The observed sbt 2.0.8 exception arose when worker response handling and process
exit raced between `isCompleted` and a throwing Promise completion. The proposed
repair changes only five calls in `React`: `success` to `trySuccess` and `failure`
to `tryFailure`. See the [review draft](../tools/sbt-worker-repair/UPSTREAM_REPORT.md)
and [reproduction instructions](../tools/sbt-worker-repair/README.md).

The preparation tool refuses unreviewed source/JAR hashes, copies the stock boot
tree to a new, disjoint directory, compiles only `React`, and verifies that only
`sbt/React.class` and `sbt/React.tasty` change. The stock cache is not modified.
The repaired boot is explicitly selected for diagnostic runs and the named CI gate
below; it is **not** installed into developer environments or the stock sbt runner.

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
[historical Linux validation workflow](../tools/sbt-worker-repair/legacy-linux-workflow.yml.example) freezes the
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

**Subsequent correction:** the original review compared only `ForkTests.scala`
and inferred too much from identical sources. Official
[sbt 2.0.11](https://github.com/sbt/sbt/releases/tag/v2.0.11) includes
[PR #9860](https://github.com/sbt/sbt/pull/9860), closing
[issue #9859](https://github.com/sbt/sbt/issues/9859), with a response-draining fix in
`WorkerExchange.scala`. This is a relevant upstream improvement that the local
five-site Promise patch does not supply. The
[revised report](../tools/sbt-worker-repair/UPSTREAM_REPORT.md) distinguishes
stock-version validation from the earlier repair evidence and any future adoption.
Keep the stock runner as the comparison baseline; do not serialize tests further,
disable forking, filter error output or weaken assertions to hide the exception.

The 2026-10-23 Windows/direct-launcher assessment completed two fresh stock
2.0.11 Figaro passes, each with all 131 exact test identities in 11 suites, plus
the real success/assertion/exit-17/framework-error controls. The unmodified upstream
slow-listener fixture reported all 40 tests and its final intentional failure.
The 2.0.8 baseline also surfaced that failure: its predeclared false-green
reproduction check therefore remains failed, not retried or relabeled. Both
binaries retain the same 60 forced exceptions across 300 direct `React` cases
per binary. Those controls bypass the new draining path and do not establish a
remaining real 2.0.11 lifecycle defect. Linux, persistent-server, coverage and
packaging adoption checks remain outstanding. No production sbt/CI setting or
library default changed, and the report is still held for review, not submitted.

| Review area | Evidence and disposition |
| --- | --- |
| Patch containment | Exact upstream source/actions-JAR pins; only `React.class` and `React.tasty` differ. Acceptable for an isolated canary, not an official sbt distribution. |
| Failure handling | Ordinary assertions, protocol error responses and abnormal exits tested. Add explicit `forkError` orderings before using the repaired result as a CI gate. |
| Runner identity | Linux verified a direct launcher with fresh boot and server autostart disabled. Standard CI uses `sbt --server --batch`; that mode is not covered by the repaired Linux run. |
| Outcome completeness | Focused run verified 102 tests, not every standard job. A green exit alone is insufficient, as the stock companion run demonstrates. |
| Isolation and rollback | Disjoint boot supported by [launcher configuration](https://www.scala-sbt.org/1.x/docs/Launcher-Configuration.html) and verified on Linux. Keep both stock boot and stock CI unchanged; discard candidate job state to roll back. |

### Bounded implementation proposal (historical review)

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
acceptance criterion or repair changed. This implementation is not promotion or
proof of a complete worker-protocol repair; the completed pilot results follow.

The first remote pilot at `88ed9db1` stopped at source hashing, before bootstrapping
sbt or running tests: one inventory entry used a local CRLF file instead of its
committed LF bytes. The failed [run](https://github.com/mattwilkinsphoto/figaro/actions/runs/39904520602)
is retained. Byte comparison confirmed only line-ending differences; the pin was
corrected to committed bytes and a Git-source regression check added. Test names,
counts, repair and outcome criteria did not change. This is a corrected harness
revision, not a discarded statistical failure or a retry of an unchanged candidate.

The inventory and compiler pins are frozen before execution. Each pilot pass
requires a fresh checkout/cache, in-process runner location/hash, complete JUnit
identities and explicit successful outcomes. Missing, duplicate, skipped, failed,
cached-only or wrong-runner results fail closed. Evidence is retained on failure;
neither pass is a retry of the other. The remaining first-success/late-error
limitation is an asserted observation, not labeled safe failure handling.

The corrected [Linux pilot 39904806939](https://github.com/mattwilkinsphoto/figaro/actions/runs/39904806939)
passed at `268801c1`, on Ubuntu 24 image `20261012.334.1`, Temurin 17.0.20.1 and
Python 3.12.15. Downloaded evidence was independently audited:

- The original 320 and additive 280 controlled cases all met their expected
  outcomes: 60 forced duplicate exceptions on stock and zero on repaired sbt.
  These repetitions do not estimate a natural race frequency.
- Passing, assertion-failing, exit-17 and framework-error fixtures returned build
  exits 0, 1, 1 and 1, with the required event/error markers. Every fixture JVM
  verified its loaded repaired JAR path and hash.
- Both independent passes completed all 131 named tests in 11 suites. Exact JUnit
  identities, fresh test compilation, runner identities and source/log hashes
  matched. Reviewed compiler-input hashes matched, and the downloaded actions JAR
  comparison again found only `React.class` and `React.tasty` changed.

The first-success/late-error limitation was observed in both binaries as expected;
it was not repaired or relabeled as reliable failure handling. The evidence closes
the explicit notification-control and fresh direct-launcher pilot gates. It does
not validate persistent server mode, exhaustive event-draining orderings or a
CI-wide runner replacement. Any adoption of named test gates still requires review.

Unchanged stock CI passed all four jobs for both the
[initial pilot commit](https://github.com/mattwilkinsphoto/figaro/actions/runs/39904520767)
and the [corrected commit](https://github.com/mattwilkinsphoto/figaro/actions/runs/39904807046),
including reproducible rebuilds and packaged consumers. Raw-log review found all
20 advisory tests passed in each, with no duplicate-Promise exception or failed-test
marker. These two clean stock runs do not negate the earlier observed race or
replace controlled repaired-runner validation. No merge, release, upstream report
submission or standard-runner adoption followed these results at that revision.

## Approved named direct-launcher CI gates

The subsequent approval adopts only the frozen
[11-suite inventory](../tools/sbt-worker-repair/pilot-inventory.json) as the
`named-direct-launcher-tests` job in [development CI](../.github/workflows/ci.yml).
The shared harness runs with `--ci-gate`; the separate branch pilot remains available
and retains its branch restriction. The new job has no advisory/fallback status.
Repository branch-protection settings are not changed by this workflow edit.

| Named non-instrumented suites | Execution |
| --- | --- |
| `SelectableSetTest`, `GaussianBlockProposalRegressionTest` | Repaired direct launcher |
| `GuidedFixedBudgetTest`, `FixedBudgetParticleFilterTest`, `FixedBudgetNumericalTest`, `DecisionStratifiedProductionTest` | Repaired direct launcher |
| `WeightedBoundedParticleFilterTest`, `CalibratedGuidedFilterTest`, `AuxiliaryParticleFilterTest` | Repaired direct launcher |
| `VectorSliceSamplerRegressionTest`, `MultiChainVectorSliceSamplerRegressionTest` | Repaired direct launcher |
| All other suites, studies, examples and compatibility checks | Stock sbt |
| Instrumented coverage, advisory Performance-tag collection tests, packaging, reproducibility and consumers | Stock sbt, including duplicate coverage of some named suites |

Only those ordinary test invocations move out of the existing mixed stock steps;
their neighboring tests and studies remain. The repair is **not** put on PATH,
installed in a shared cache, added to a library artifact or used in publication.
The stopping estimator, runtime defaults, sbt version and Figaro version are unchanged.

The adopted job deliberately reuses the pilot's entire acceptance protocol: reviewed
compiler/source/launcher pins, original and additive controls (600 cases), four
fixture outcomes, two fresh 131-test passes, same-JVM code identity, exact JUnit
test identities, fresh compilation and unchanged stock bytes. Two passes are
predeclared checks, not retries. Evidence uploads even after a failure. Source or
toolchain drift fails closed and requires explicit review of the frozen inventory
or pins; do not regenerate them merely to make a red job pass.

The original four stock jobs remain, with the named ordinary test invocations
relocated as above. Coverage and advisory runs can still encounter the stock race;
this narrowly adopted repair does not fix their server-mode lifecycle. Keep reviewing
raw logs, not just the workflow conclusion. First-success/late-error and exhaustive
notification-draining limitations also remain. Rollback restores the relocated
test invocations to stock and removes the new job; no shared-cache restoration is
necessary. A CI-tooling change alone does not warrant a Figaro release.

Remote integration validation passed at `0275d6a1` in
[development CI 39908126561](https://github.com/mattwilkinsphoto/figaro/actions/runs/39908126561).
All five jobs passed, including the adopted gate and retained stock jobs. The
[companion pilot 39908126558](https://github.com/mattwilkinsphoto/figaro/actions/runs/39908126558)
also passed. Independently audited downloaded evidence confirms all 600 controls
(60 expected duplicate exceptions on stock, zero on repaired), four fixture exits
0/1/1/1 and both fresh 131-test passes, with exact identities and unchanged stock
bytes. Only `React.class` and `React.tasty` differ in the repaired JAR.

Stock raw logs contained no duplicate-Promise exception or failed-test marker;
all 20 advisory timing tests passed. Stock instrumented coverage, reproducible
rebuilds, artifact checks and all six packaged consumers also passed. These are
observed outcomes for this commit, not removal of the stock race or the protocol
limitations above. The CI evidence archive is 9,764,153 bytes with SHA-256
`89809986b2444d9b23bc25a723fd3fe576cf47b37b73ea84ecd45acdc23626dd`.

The [final guided-filter review](GUIDED_FIXED_BUDGET_STATUS.md#final-promotion-review-and-integration-decision)
accepts this narrowly scoped tooling gate. Main integration and
[post-merge CI](https://github.com/mattwilkinsphoto/figaro/actions/runs/39968790021)
passed. The separate [7.4.0 release gates](RELEASE_7_4_STATUS.md) now govern publication. The draft
report remains unsubmitted and no CI-wide or persistent-server repair is claimed.
