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

## Validation performed locally

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

## Remaining review / evidence boundaries

Please review whether atomic first completion is appropriate at all five sites,
particularly `forkError` notifications. There is no claim that a late error must
override an earlier success, or that all notification-drain/lifecycle races have
been excluded. These tests do verify ordinary assertion-failure events and an
abnormal exit; they do not separately force a `forkError`-notification race and
are not an exhaustive protocol proof.

Remote Linux validation of this repair and upstream maintainer review remain
outstanding. The report is held locally for approval; do not present it as an
upstream-confirmed diagnosis or accepted fix.
