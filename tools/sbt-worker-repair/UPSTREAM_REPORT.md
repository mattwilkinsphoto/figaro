# Draft for review: duplicate Promise completion in forked-test React

**Not submitted.** Proposed issue title: `sbt 2: React response/worker-exit race
throws IllegalStateException: Promise already completed`.

## Environment and observation

Observed with sbt 2.0.8, JDK 17, forked ScalaTest tests in GitHub Actions Linux.
The worker-exit stack included `sbt.React.notifyExit(ForkTests.scala:204)` and
`sbt.internal.WorkerProxy` at line 197. The affected suite reported 11 passing tests
and no aborted suite; the exception therefore did not make this particular CI job
fail. We do not infer that all tests/workers are safe from that observation.

Relevant source:

- [React in sbt 2.0.8](https://github.com/sbt/sbt/blob/v2.0.8/main-actions/src/main/scala/sbt/ForkTests.scala)
- [WorkerExchange in sbt 2.0.8](https://github.com/sbt/sbt/blob/v2.0.8/main-actions/src/main/scala/sbt/internal/WorkerExchange.scala)
- [ForkTests in sbt 2.0.11](https://github.com/sbt/sbt/blob/v2.0.11/main-actions/src/main/scala/sbt/ForkTests.scala)

The inspected 2.0.8 and 2.0.11 ForkTests source files were byte-identical (SHA-256
`c75f1219c0d2918022f3ecd3cc5b2d0636af9cc82fdfefb8c0c6bd1cecc7599f`).
Binary reproduction and repair validation below used **2.0.8 only**. We have not
tested every later sbt version or established whether maintainers already have a fix.

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

## Windows validation

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

## Independent Linux execution

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
been excluded. These tests do verify ordinary assertion-failure events and an
abnormal exit; they do not separately force a `forkError`-notification race and
are not an exhaustive protocol proof.

Upstream maintainer review and any standard-runner activation remain outstanding.
The report is held locally for approval; do not present it as an upstream-confirmed
diagnosis or accepted fix.
