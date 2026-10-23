# Stock sbt validation and historical worker-completion repair

Current source builds use stock sbt 2.0.11. The local 2.0.8 repair is retired from
active workflows; its sources and results remain for historical replay. The named
development CI job runs `stock_linux.py --mode direct --ci-gate --output NEW_DIRECTORY`
on Linux, only within the named workflow. It checks pinned stock binaries, all
candidate failure controls and two fresh 131-test inventories at the current commit.
It does not modify sbt binaries. Full adoption CI and packaged acceptance remain
pending; [current status](../../docs/CI_VALIDATION.md#stock-sbt-2011-adoption).
The retired `branch_pilot.py --ci-gate` entry point fails explicitly.

The instructions below preserve historical experiments. For repair reproduction,
use the commits recorded with each result, not the current production selectors.
The former Actions workflow is preserved as a non-executable
[snapshot](legacy-linux-workflow.yml.example). No upstream issue has been filed.

This is an isolated diagnostic repair, not a replacement sbt release or an automatic
installation. Read the [scope and local evidence](../../docs/CI_VALIDATION.md) and
[upstream report draft](UPSTREAM_REPORT.md) first. No upstream issue has been filed.

**Current review:** official sbt 2.0.11 includes a response-draining fix outside
`React`. The [revised report](UPSTREAM_REPORT.md#current-upstream-disposition)
supersedes the earlier source-only inference against an upgrade. Keep the existing
2.0.8 repair evidence separate from the stock-version assessment below. Neither
this document nor that assessment changes the production toolchain automatically.

Prerequisites: Python 3.11+, JDK 17, an already populated official sbt 2.0.8 boot
directory, and the official sbt launcher. Choose new output directories whose
parents already exist. Shell variables below denote paths supplied by the caller:

```sh
python -B tools/sbt-worker-repair/prepare.py --boot "$STOCK_BOOT" --output .cache/sbt-repaired
python -B tools/sbt-worker-repair/verify.py --stock-boot "$STOCK_BOOT" --repaired-boot .cache/sbt-repaired/boot --output .cache/sbt-race-check
python -B tools/sbt-worker-repair/integration.py --boot .cache/sbt-repaired/boot --launcher "$SBT_LAUNCHER" --user-home "$ISOLATED_USER_HOME" --output .cache/sbt-fork-check
```

`prepare.py` downloads hash-pinned upstream source and compiles only its `React`
class using the stock boot's Scala compiler. It emits `upstream.patch`, source,
compiler logs, a manifest with all compiler-input hashes, and a repaired boot copy.
It refuses an unexpected source/JAR or overlapping output/stock directories. All
other entries in the actions JAR must remain byte-identical. The generated source
retains sbt's copyright header; the upstream Apache-2.0 license accompanies this
tool in [sbt-LICENSE.txt](sbt-LICENSE.txt).

### Bounded Linux validation

The original Linux entry point runs
`python -B tools/sbt-worker-repair/linux_validation.py --output NEW_DIRECTORY` on
a Linux JDK 17 host. It bootstraps a SHA-pinned sbt launcher into a fresh stock
boot, then invokes the same preparation, 320-case matrix and real forked fixtures
used locally. Finally it runs the same nine Figaro suites (102 tests), asserting
the loaded repaired JAR location and unchanged stock JAR hashes. Its protocol is
written before execution. Logs, source hashes, patch and both actions JARs are
retained even on failure; ordinary dependency caches are not uploaded.

This workflow is independent of standard CI. It does not install the repaired
runner globally or change inference defaults. The two intentionally failing fixture
builds must fail with their specified markers; they are not ignored CI failures.
Linux results must be inspected before any promotion decision. The original run
did not separately force the `forkError` race; see the additive pilot below.

### Branch-only adoption pilot

The historical [Linux workflow](legacy-linux-workflow.yml.example) ran
[branch_pilot.py](branch_pilot.py), exclusively on `codex/guided-fixed-budget`.
It retains stock CI and the unchanged five-site repair. No repaired server mode,
publication change or default installation is enabled.

The pilot retains the original matrix and three real fixtures, then runs
[ForkErrorCheck.java](ForkErrorCheck.java): seven additional orderings x20 for each
binary (280 cases). Real sbt payload types and a tracing logger verify notification
deserialization/processing; cases assert Promise outcomes, including the explicit
limitation that success preceding a late error remains success. An isolated
[framework fixture](framework-fixture/build.sbt) throws in worker `runner.tasks`,
outside ordinary assertion handling. The build must fail with the deserialized
`ForkError` and sentinel message, not merely an arbitrary nonzero exit.

Two predeclared Figaro passes each use a fresh direct-launcher JVM, boot copy, Git
archive checkout, home and caches. [The frozen inventory](pilot-inventory.json)
contains all 131 test names across 11 suites and their source hashes. Full JUnit
identities, fresh test compilation, loaded React path/hash and complete outcomes
are required. [Compiler input hashes](compiler-inputs.json) are taken from the
reviewed Linux run, not learned from the new candidate at execution time.

The original fixture runner accepts optional `--verify-identity` to check the
loaded JAR in each fixture JVM without changing the original expected outcomes.
The pilot enforces subprocess/job timeouts and preserves failed evidence. An
overall green stock workflow is not a substitute for these gates. Pilot review and
the subsequent [named-gate integration validation](../../docs/CI_VALIDATION.md#approved-named-direct-launcher-ci-gates)
are complete. At that revision only the frozen inventory adopted the repaired direct launcher;
all other invocations remained stock. The first-winner policy and broader
event-draining/protocol limitations are unchanged. No upstream submission or
CI-wide runner replacement is implied.

`verify.py` uses reflection on the actual stock and repaired classes with a
controllable fake Process. Stock exceptions are expected positive controls; a
failure to reproduce them fails validation. Repaired duplicate completion must
not throw and earlier failures must remain failures.

`integration.py` runs three **separate** real forked sbt builds. One must pass;
the assertion-failure and exit-17 builds must fail and contain the specified event
counts/markers. These fixtures are intentionally outside Figaro's normal test
aggregation. Do not copy them into its test tree. A successful verification script
does not mean its deliberately failing fixtures succeeded.

For a focused Figaro run, explicitly select the repaired boot without changing a
shared cache or reusing a previously running server:

```sh
java -Dsbt.boot.directory="$REPAIRED_BOOT" -Dsbt.server.autostart=false -jar "$SBT_LAUNCHER" 'eval Class.forName("sbt.React").getProtectionDomain.getCodeSource.getLocation' 'figaro / Test / testOnly com.cra.figaro.test.modernization.GuidedFixedBudgetTest'
```

Inspect the printed code-source URL: it must point inside the repaired boot.
Preserve the original working JVM/cache options needed by your environment.
Windows tools require `FIGARO_WORKSPACE_OWNER`; see the guide for access handling.
On platforms with short socket-path limits, choose a short existing runtime
directory for the sbt runner rather than changing the worker protocol.

Rollback is selecting the original boot. No global sbt defaults, released estimator,
library dependency or publication workflow is changed. An upstream release should
replace this workaround only after checking its actual source and validation.

## Stock 2.0.11 assessment

[stock_assessment.py](stock_assessment.py) is a bounded diagnostic runner, not a
production installer or replacement for upstream's entire scripted-test suite.
It invokes the existing Java controls and fixtures unchanged, and runs the exact
upstream slow-listener build/test sources through a direct launcher. The upstream
`-> test` scripted expectation is represented by requiring a nonzero build exit;
the candidate additionally must report all 40 identities and the final failure.

Prepare a fresh evidence root before invocation. Required inputs are:

- `inputs.json`: the frozen Figaro Git commit (`head`) and protected repository
  path-to-SHA-256 mapping (`protected`); retain platform and upstream provenance.
- For each version `2.0.8` and `2.0.11`: its official `sbt-launch-VERSION.jar`,
  isolated populated `boot/`, and `boot-hashes.json` mapping every boot JAR's
  relative path to SHA-256. Verify Maven checksums before execution.
- `upstream/slow-build.sbt` and `upstream/slow-ManyTests.scala`, copied byte-for-byte
  from the tagged upstream regression. Retain the upstream license, scripted
  expectation, source/API responses and hashes alongside them. Provide a fresh
  `ipc/` runtime directory. Windows requires the explicit access handling described
  above; access failures must not be ignored.

Then, from the Figaro repository root:

```sh
python -B tools/sbt-worker-repair/stock_assessment.py --output "$ASSESSMENT_ROOT" --phase controls
python -B tools/sbt-worker-repair/stock_assessment.py --output "$ASSESSMENT_ROOT" --phase figaro
python -B tools/test_stock_assessment.py
```

Each phase writes its protocol before execution and refuses to overwrite an
existing phase directory. Inspect a failed controls phase before continuing the
independent Figaro phase; do not conceal its nonzero exit. Two fresh source/boot/
cache Figaro passes require the original 131 named tests in 11 suites, fresh
compilation, complete JUnit identities, and verified loaded stock binary identity.
The launcher, caches and checkouts remain isolated; no shared defaults change.

The controlled stock exceptions are expected diagnostic findings, not fixed-defect
claims. The frozen 2.0.8 slow-listener baseline expects the historical false-green
outcome; failure to reproduce it remains a failed reproduction check even if the
candidate passes. There are no retries or empirically selected thresholds. See
the report for the actual outcomes, Windows scope and remaining adoption gates.

[Research provenance](../../docs/RESEARCH_REFERENCES.md#eng-01).

## Branch-only Linux and server assessment

[stock_linux.py](stock_linux.py) and its
[workflow](../../.github/workflows/sbt-stock-linux.yml) run only on
`codex/stock-sbt-2-0-11-validation`, with read-only repository permissions and no
publication/signing secrets. They do not replace the adopted repair or change the
production sbt selector. The frozen library baseline is `2f8e6c54`; only isolated
checkout selectors change to 2.0.11. Tooling and result provenance are recorded
separately from that baseline.

Three predeclared jobs cover direct Java, the official `--server --batch` runner,
and packaging. The first two retain both stock control matrices, all real fixtures
and two fresh 131-test passes. The existing baseline false-green expectation stays
unchanged: if it fails to reproduce, that stage and its workflow job remain red,
even if independent candidate checks pass. Every attempted stage is retained;
there are no unchanged retries or fallback runners to obtain a green result.
A demonstrated harness defect may be corrected in a separately identified commit
and run, retaining the failed attempt and unchanged empirical acceptance criteria.
The [report](UPSTREAM_REPORT.md#linux-and-server-assessment-2026-10-23) records the
first client-invocation failure and the narrowly corrected follow-up.

The experiment is now manually dispatched with `scope=all` or `scope=client`;
ordinary production CI is unchanged. `client` runs only the remaining persistent
gate and must be read alongside prior direct/foreground/packaging evidence. It
does not relabel the second run's failed historical baseline reproduction.

The official [runner source](https://github.com/sbt/sbt/blob/v2.0.11/sbt) distinguishes
foreground `--server` from persistent `--client`. An additional native-client
sequence requires the **same server PID** for passing tests, an assertion failure,
a worker exit-17 failure and a subsequent passing `testFull` execution. It then
checks shutdown of that owned server. It does not use global `shutdownall`.
Framework-error and slow-listener checks remain separate foreground fixtures.
`testFull` receives framework arguments through `testOptions`, as documented in
the [testing reference](https://www.scala-sbt.org/2.x/docs/en/reference/sbt-test.html),
not inline arguments. Negative controls explicitly remove the passing-event filter.

The packaging job instruments the same 131-test inventory, checks coverage output,
turns instrumentation off, requires two fresh byte-identical thin/fat rebuilds,
checks classifiers/legal notices, MiMa/TASTy compatibility, and runs the six
existing Maven-layout consumers against the expected candidate JAR hash with Ivy
local excluded. `--candidate` is deliberate: no artifact is published. The existing
bundle README still names 2.0.8; its template must be reconciled before any future
production toolchain adoption, not silently treated as accurate for this candidate.

On a Linux JDK 17/Python 3.12+ runner, choose a new output root:

```sh
python -B tools/sbt-worker-repair/stock_linux.py --mode direct --output "$NEW_DIRECT_ROOT"
python -B tools/sbt-worker-repair/stock_linux.py --mode server --output "$NEW_SERVER_ROOT"
python -B tools/sbt-worker-repair/stock_linux.py --mode packaging --output "$NEW_PACKAGING_ROOT"
```

Each job freezes its protocol before execution and preserves bounded evidence in
`evidence/`, including failures. Review raw logs and exact identities, not just
workflow status. These checks are bounded adoption evidence, not an exhaustive
worker-protocol proof, timing benchmark, complete standard-CI run or approval to
submit the separate hardening patch upstream.
