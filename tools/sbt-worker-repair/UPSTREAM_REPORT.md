# Draft for review: sbt worker completion after the 2.0.11 lifecycle fix

**Not submitted. Hold for review.** The original issue proposal below is now
historical evidence, not a ready-to-file report against current sbt.

## Current upstream disposition

sbt [2.0.11 release notes](https://github.com/sbt/sbt/releases/tag/v2.0.11) include
[PR #9860](https://github.com/sbt/sbt/pull/9860), which closes
[issue #9859](https://github.com/sbt/sbt/issues/9859). The repair is in
[WorkerExchange](https://github.com/sbt/sbt/blob/v2.0.11/main-actions/src/main/scala/sbt/internal/WorkerExchange.scala):
the exit watcher waits for response reading to finish, bounded by ten seconds,
before notifying listeners of process exit. Its regression uses 40 forked munit
tests, a 50 ms/event listener, and a deliberately failing final test. This addresses
lost notifications and false-success outcomes, not just a thrown Promise exception.

**Correction to the earlier assessment:** identical `ForkTests.scala` files do
not establish that upgrading cannot help. The earlier source comparison omitted
this relevant lifecycle change. The five-site local patch does not implement
response draining and is not a substitute for the upstream fix.

The new [stock assessment](stock_assessment.py) retains the original controls and
adds the unmodified upstream fixture. No production toolchain or CI adoption
follows automatically from this assessment.

## Stock-version assessment: 2026-10-23

Execution environment: Windows 11, Temurin 17.0.4, Python 3.14.6; direct launchers
with server autostart disabled and isolated homes, caches and boot directories.
Official launcher/actions checksums were verified and stock boot JARs were
unchanged after the controls. The stock 2.0.11 actions JAR SHA-256 is
`688505c9b1c30f505275826d5fb8dd14f274bef0e284942e392c75f5dcfc63be`.

| Check | Stock 2.0.8 | Stock 2.0.11 | Interpretation |
| --- | --- | --- | --- |
| Original eight orderings, 20 trials each | 40 forced exceptions; asserted outcomes retained | Same | `React` still uses non-atomic check-then-complete |
| Seven notification orderings, 20 trials each | 20 forced exceptions; asserted outcomes retained | Same | Error-first behavior retained; success-before-late-error still wins |
| Real passing fixture | 100 successes; build exit 0 | Same | Complete passing-event control |
| Real assertion-failure fixture | 100 successes, one failure; exit 1 | Same | Intentional assertion failure surfaced |
| Real worker exit 17 | Reported; build exit 1 | Same | Abnormal worker termination surfaced |
| Real framework error | Deserialized `ForkError`; exit 1 | Same | Error outside per-test assertion handling surfaced |
| Upstream slow-listener fixture | 39 successes, final failure; exit 1 | Same; all 40 exact identities verified | Candidate passes; historical false-green baseline **not reproduced** |
| Fresh Figaro regression passes | Not repeated in this assessment | Two passes, each 131/131 tests in 11 suites | Exact inventory, fresh compilation and loaded stock JAR verified |

The Figaro passes used separate Git-archive checkouts at
`2f8e6c54d78c9c5906edb4b94bab67f0db6d8bb6`, changing only each isolated checkout's
sbt version selector. Each pass used fresh boot/home/cache/target directories.
Every frozen JUnit test identity occurred exactly once per pass; no test was
failed, aborted, canceled, ignored or pending. No duplicate-Promise/worker exception
occurred in these two runs. This is the named 11-suite control inventory, not the
entire Figaro test suite or a packaged-consumer acceptance run.

The baseline protocol expected exit 0 for its false-green reproduction. Actual
exit 1 therefore leaves that check and the aggregate controls status **failed**.
It was not retried or relabeled a successful reproduction. This prevents a local
before/after claim about the causal benefit of the upstream patch. Scheduling or
environment differences could explain the discrepancy with upstream's reproduction,
but this run does not identify the cause. It does not negate the upstream report
and fix. Source fixtures and acceptance criteria remained unchanged.

The 600 total forced cases exercise `React` directly with controlled fake
processes. They bypass the changed `WorkerProxy` response-draining path. Their
remaining exceptions neither estimate a natural failure rate nor demonstrate
that the upstream lifecycle fix is ineffective. The real fixtures emitted no
duplicate-completion exception; their small number cannot prove its absence in
all valid process/listener schedules.

### What remains before any upstream submission

1. Prefer evaluating the official response-draining fix to extending the local
   2.0.8 workaround. A production upgrade needs Linux/direct-launcher and actual
   server-mode CI validation, including the relevant coverage/packaging gates;
   this Windows assessment does not replace them.
2. A new defect claim against 2.0.11 needs a minimal reproducer through its real
   `WorkerProxy` lifecycle, or a concrete source-level path showing that a harmful
   ordering remains reachable. The ten-second drain bound is a scope boundary,
   not a failure demonstrated by the 50 ms/event regression. We have not tested
   drain-timeout, arbitrary listener failures or every transport.
3. If such a residual case is established, reference #9859/#9860 and distinguish
   it from the already fixed lost-event issue. Otherwise, any proposed five-site
   change should be framed as defensive hardening for maintainer discussion, not
   proof that the official fix failed. Its first-winner limitation still applies.

**Disposition:** hold the original bug report and proposed repair for review.
Do not file a duplicate of #9859, claim a reproduced 2.0.11 lifecycle failure, or
submit a patch merely because the forced controls still throw. Production sbt/CI,
published artifacts, filtering defaults and the stopping estimator are unchanged.
Historical exposure is useful context, not evidence of a current regression.

## Environment and observation

Observed with sbt 2.0.8, JDK 17, forked ScalaTest tests in GitHub Actions Linux.
The worker-exit stack included `sbt.React.notifyExit(ForkTests.scala:204)` and
`sbt.internal.WorkerProxy` at line 197. The affected suite reported 11 passing tests
and no aborted suite; the exception therefore did not make this particular CI job
fail. We do not infer that all tests/workers are safe from that observation.

Historical exposure predates the initial Figaro 6.0 modernization build:
[commit b281f016](https://github.com/mattwilkinsphoto/figaro/commit/b281f0160da5967b7c5db962e773c148f869a735)
already selects sbt 2.0.8 and forked tests while identifying the build as
`5.0.0-modern.2-SNAPSHOT`.
[Commit bfc7e447](https://github.com/mattwilkinsphoto/figaro/commit/bfc7e447fa00662e09faa849d154b254a5aaaf03)
then selects `6.0.0-modern.1-SNAPSHOT` with the same sbt/fork settings. This establishes
toolchain exposure, not that a particular historical run lost results, that every
earlier release was affected, or which upstream version first introduced the defect.

Relevant source:

- [React in sbt 2.0.8](https://github.com/sbt/sbt/blob/v2.0.8/main-actions/src/main/scala/sbt/ForkTests.scala)
- [WorkerExchange in sbt 2.0.8](https://github.com/sbt/sbt/blob/v2.0.8/main-actions/src/main/scala/sbt/internal/WorkerExchange.scala)
- [ForkTests in sbt 2.0.11](https://github.com/sbt/sbt/blob/v2.0.11/main-actions/src/main/scala/sbt/ForkTests.scala)

The inspected 2.0.8 and 2.0.11 ForkTests source files were byte-identical (SHA-256
`c75f1219c0d2918022f3ecd3cc5b2d0636af9cc82fdfefb8c0c6bd1cecc7599f`).
The original binary reproduction and repair validation in the historical sections
below used **2.0.8 only**. The new stock assessment is separate; upstream has already
fixed the response-draining issue described above.

## Cause and deterministic reproduction

`notifyExit` checks `!promise.isCompleted`, then obtains `exitValue`, then calls
throwing `success` or `failure`. Response handling can complete the same Promise
between the check and completion. A prior check cannot make completion atomic.

[WorkerCompletionCheck.java](WorkerCompletionCheck.java) reflects on the actual
`sbt.React` class. A controlled Process invokes a matching JSON-RPC response inside
`exitValue`, after the completion check. This deterministically exercises the race
window without timing sleeps or loading Figaro. It does not reproduce the whole
operating-system scheduling sequence of the original CI job.

Follow [README.md](README.md) to prepare and verify. Include the Java reproducer,
verification/preparation scripts, shared `validation_support.py`, and the fixtures
with any eventual report attachment. Their relative repository layout matters.
The verified original `actions_3-2.0.8.jar` SHA-256 is
`95498714672cffa9f62d843d8a7d07e212704d5ecfc52c8b71164dedb20126a1`.

## Proposed minimal repair

In React, replace its two `promise.success` calls with `promise.trySuccess`, and
its three `promise.failure` calls with `promise.tryFailure`. The preparation script
emits the five-site unified diff as `upstream.patch`; no exception handler is added.
The notification routing, listener dispatch, process lifecycle and first-completion
policy are unchanged. The existing preliminary checks can remain as fast paths,
but correctness must not depend on their atomicity.

Scala's [Promise API](https://www.scala-lang.org/api/current/scala/concurrent/Promise.html)
defines the nonthrowing completion operations used here. Ignoring a false return
retains first-completion behavior; it is not a policy for reconciling contradictory
late messages. Maintainer review should decide whether those cases warrant a
separate protocol/lifecycle change. This patch does not claim to solve them.

## Historical Windows repair validation (sbt 2.0.8)

On Windows / JDK 17, using the original sbt binary and a separately prepared boot:

| Controlled ordering | Trials per binary | Original exceptions | Repaired exceptions |
| --- | ---: | ---: | ---: |
| Response first | 20 | 0 | 0 |
| Exit first | 20 | 0 | 0 |
| Success response during exit handling | 20 | 20 | 0 |
| Error first | 20 | 0 | 0 |
| Error response during exit handling | 20 | 20 | 0 |
| Nonzero exit first, then success response | 20 | 0 | 0 |
| Duplicate exit notification | 20 | 0 | 0 |
| Unrelated response ID | 20 | 0 | 0 |

The harness also asserts Promise outcomes: earlier errors/nonzero exit failures
remain failures, and unrelated IDs do not complete the Promise. A zero in the table
alone is not the acceptance criterion. All 320 controlled cases met expectations.

Real forked integration fixtures additionally verified:

- All 100 success events reported and a successful build exit.
- All 100 successes plus one intentional assertion failure reported, with build exit 1.
- A separate worker terminated with exit 17; sbt reported it and returned build exit 1.

No duplicate-completion exception occurred in these runs. Nine focused Figaro suites
also passed all 102 tests with the repaired JAR's loaded code source confirmed.
Only `React.class` and `React.tasty` differ inside the copied actions JAR; the stock
boot remains untouched. This is not an official sbt binary distribution.

## Historical independent Linux execution (sbt 2.0.8)

The same frozen checks passed in
[GitHub Actions run 39899930618](https://github.com/mattwilkinsphoto/figaro/actions/runs/39899930618)
at Figaro commit `0d151029afed80e9ba67b5a0fbb0657d377aa1c5`, using the Ubuntu 24
runner image `20261012.334.1`, Temurin 17.0.20.1 and Python 3.12.15.

The matrix above was repeated unchanged: 40 forced duplicate-completion exceptions
on stock sbt, zero on the repaired class, and all expected Promise outcomes retained.
All three real forked fixtures produced their required statuses (0, 1, 1), and
the same nine Figaro suites passed all 102 tests. The loaded React code source was
explicitly verified against the repaired boot. Stock boot JAR hashes were unchanged.

Downloaded source snapshots, command logs and both actions JARs were independently
checked after the run. Only `sbt/React.class` and `sbt/React.tasty` differ. The Linux
repaired actions JAR SHA-256 is
`b657e36db95b23f30a41297892fbd2c6ed6e917865f682838d6c44023d7fb4bf`.
The workflow retains the source, patch, compiler-input hashes and actual raw outputs,
including the intentionally failing fixtures. No criteria were relaxed for Linux.
The orchestrator is [linux_validation.py](linux_validation.py).

These are controlled reproduction results, not empirical estimates of the race's
frequency. The standard Figaro CI runner was not switched to the repair.

The same-commit [companion stock-runner run](https://github.com/mattwilkinsphoto/figaro/actions/runs/39899930482)
finished successfully but logged another `Promise already completed` exception at
`React.notifyExit` (`ForkTests.scala:204`, through `WorkerProxy` at
`WorkerExchange.scala:197`). The surrounding continuous-vector check reported all
18 tests passed. This unforced observation is retained separately from the repaired
run; an overall successful workflow does not establish an exception-free worker log.

## Remaining review / evidence boundaries

Please review whether atomic first completion is appropriate at all five sites,
particularly `forkError` notifications. There is no claim that a late error must
override an earlier success, or that all notification-drain/lifecycle races have
been excluded. The original tests verified ordinary assertion-failure events and
an abnormal exit but did not separately force `forkError`; the additive validation
below now covers that specific gap. Neither stage is an exhaustive protocol proof.

## Additive notification controls and branch-only pilot

[Pilot run 39904806939](https://github.com/mattwilkinsphoto/figaro/actions/runs/39904806939)
at `268801c1` passed with the same five-site repair. The additional
[notification reproducer](ForkErrorCheck.java) uses sbt's own serialized error types
and verifies logger trace processing plus the winning Promise outcome. Seven
orderings, each repeated 20 times for each binary, cover error before success/exit,
error during exit, duplicate error, success before late error and wrong outer/inner
request IDs. The error-during-exit control adds 20 forced stock exceptions and zero
repaired exceptions. Together with the unchanged original matrix, all 600 cases
met their expected outcomes (60 stock exceptions, zero repaired).

A separate framework throws in worker `runner.tasks`, outside ordinary per-test
assertion handling. Its deserialized `ForkError` caused build exit 1. The original
passing, assertion-failing and exit-17 fixtures retained their expected outcomes.
Both fresh direct-launcher Linux passes completed 131 named tests in 11 suites;
all fixture/pilot JVMs checked the loaded repaired JAR path/hash. Compiler pins,
source/log hashes, complete JUnit identities and two-entry JAR difference were
independently checked after download. No standard runner was replaced.

The rejected first local fixture had no discovered tests; the rejected first remote
pilot had one local-CRLF source pin inconsistent with committed LF bytes and stopped
before executing tests. Both attempts were retained; corrections affected only
fixture discovery and source provenance, not repair or outcome criteria.

Success followed by a late error still yields success in both binaries, explicitly
recorded as a limitation. This evidence does not settle notification draining,
other lifecycle races, first-winner policy or persistent server mode.

Narrow adoption for the named non-instrumented direct-launcher CI suites has since
been approved and [remote integration validation passed](https://github.com/mattwilkinsphoto/figaro/actions/runs/39908126561)
at `0275d6a1`. Its acceptance protocol is the
same as the pilot. Other invocations, including stock server-mode coverage and
packaging, remain unchanged. This is not upstream or CI-wide runner acceptance.
Upstream maintainer review remains outstanding.
The report is held locally for approval; do not present it as an upstream-confirmed
diagnosis or accepted fix.
