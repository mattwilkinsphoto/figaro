# Opt-in sbt 2.0.8 worker-completion repair

This is an isolated diagnostic repair, not a replacement sbt release or an automatic
installation. Read the [scope and local evidence](../../docs/CI_VALIDATION.md) and
[upstream report draft](UPSTREAM_REPORT.md) first. No upstream issue has been filed.

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

The separate [Linux workflow](../../.github/workflows/sbt-repair-linux.yml) runs
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
Linux results must be inspected before any promotion decision. The existing
first-completion and unforced `forkError`-race limitations still apply.

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
