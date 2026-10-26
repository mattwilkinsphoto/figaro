# Independent Figaro consumer check

This standalone build tests the published dependency boundary, not a source-project
dependency. It exists to catch stale snapshots, missing runtime dependencies and example/
test classpath leakage that an in-repository example could miss.

When comparing different local bundles with the same SNAPSHOT coordinate, use a
fresh consumer output directory and a fresh sbt action cache (for example
`-Dsbt.global.localcache=<new-task-specific-directory>`). Inspect
`show Compile / dependencyClasspath` and retain the expected-JAR hash assertion.
A previous resolution result can point at an older bundle even when the new JAR
contains the requested API. This was observed during stratified-selector acceptance;
the failed compilation and the cold rerun are recorded separately. Never fix such
a failure by disabling the hash check or adding producer classes to the classpath.
See [sbt's cache model](https://www.scala-sbt.org/2.x/docs/en/concepts/caching.html).

## Quick start

The current check includes [distribution constructions and GMMs](../../docs/DISTRIBUTION_CONSTRUCTIONS.md),
Gaussian KL/Bhattacharyya/partition MI, and observation-ready scalar/count/vector adapters.
Figaro 6.1 additionally checks exact-coordinate conditional copulas, bounded GVM
mixture fitting and full-mixture linear/angular mutual information.

To test Maven Central in a fresh environment, set `FIGARO_CENTRAL_ONLY=true`,
`FIGARO_VERSION=7.4.0`, and
`FIGARO_EXPECTED_SHA256=0abfdadfeb007af1eee39a04b0591e49686d4cab74181402ccf8d9948abe4dbf`.
Also set `FIGARO_CAPABILITY_CHECK=true`, `FIGARO_BUDGET_CHECK=true` and
`FIGARO_FIXED_BUDGET_CHECK=true` and `FIGARO_GUIDED_FIXED_CHECK=true`, then run each of
`FigaroConsumerCheck`, `IntegrationConsumerCheck`, `CapabilityConsumerCheck`,
`BudgetConsumerCheck`, `FixedBudgetConsumerCheck` and `GuidedFixedBudgetConsumerCheck`
with `sbt "runMain <name>"` here. All six passed against
published 7.4.0. This mode excludes Ivy local and
the downloaded file repository. The [Central workflow](../../docs/MAVEN_CENTRAL.md)
runs it on a fresh runner without restoring dependency caches.

For local producer testing instead:

For the 7.0 release set `FIGARO_VERSION=7.0.0` (or the exact snapshot
coordinate when checking a development build).
This opts into the separate `src/integration/scala` packaged-API check; after the
ordinary check, run `runMain IntegrationConsumerCheck`. Leaving the variable unset
continues to target the immutable 6.1.0 Central baseline. The integration check
exercises log filtering, exact counts, observed multi-chain likelihoods and
support-aware proposals from the verified JAR, not from producer classes.

1. From the Figaro root run `sbt "figaro / publishLocal"`.
2. Set `FIGARO_EXPECTED_SHA256` to the SHA-256 of the built thin Figaro jar.
3. From this directory run `sbt "runMain FigaroConsumerCheck"` with the same JDK/local repository settings.

To test a downloaded release instead, extract its Maven-layout ZIP and set
`FIGARO_RELEASE_MAVEN_ROOT` to the extracted `maven` directory. Set the expected
hash from that bundle's thin JAR, then run `sbt "clean; runMain FigaroConsumerCheck"`.
This mode excludes Ivy local and uses the bundle plus Maven Central for dependencies;
it cannot pass by silently loading an earlier `publishLocal` artifact.

## API and common patterns

For 7.4.0 and later, set `FIGARO_GUIDED_FIXED_CHECK=true` and
run `runMain GuidedFixedBudgetConsumerCheck` with the other five consumers.
Use the hash of the exact candidate/published JAR; the quick start pins published 7.4.0.
This separately compiled check exercises guided initialization/propagation,
auxiliary resampling, carry steps and proposal diagnostics under both methods.
Leave the flag unset for releases before 7.4.0. Central availability is tracked
in the [release record](../../docs/RELEASE_7_4_STATUS.md).

For 7.3.0 and later, `FIGARO_FIXED_BUDGET_CHECK=true` enables the separate fixed-budget
source directory. Its check covers both methods and explicit policies, retained
tiny weights, selection diagnostics and invalidation after numerical refusal.
Leave this flag unset when checking earlier immutable releases without the API.

For the capability-expansion release build, set `FIGARO_VERSION=7.1.0`
and `FIGARO_CAPABILITY_CHECK=true`, set the hash of that exact new JAR, and additionally
run `runMain CapabilityConsumerCheck`. This compiles the separate capability source
directory; leave the flag unset when testing immutable 7.0.0 or 6.1.0 artifacts.
The new check exercises grouped counts, native log ratios, automatic scalar moves,
discarded/frozen tuning, tiny-shape density and Cholesky-entry density APIs.

For post-7.1 development builds, additionally set `FIGARO_BUDGET_CHECK=true`
and run `runMain BudgetConsumerCheck`. This checks the indicator budget adviser,
weighted bounded filtering and [calibrated observation-informed proposals](../../docs/CALIBRATED_GUIDED_FILTER.md)
from the same hash-verified JAR. Leave this flag unset for immutable 7.1.0 artifacts.
It also checks `currentLogWeights` and auxiliary reuse of ancestors whose ordinary
weights rounded to zero, with analytically cancelling observation weights. This
guards the packaged numerical-repair API rather than relying on producer classes.

`FigaroConsumerCheck.main(args: Array[String]): Unit` accepts an empty array and requires
the environment variable above. It prints the loaded jar hash and a completion line;
missing/incorrect hashes, non-jar resolution or any failed model/lifecycle check throw.
Its individual checks are ordinary application calls, not additions to Figaro's public API.

- **Before integrating a snapshot:** publish it, then run the three steps above.
- **In CI:** set the hash from the just-built jar and run this separate build after publication.
- **After a dependency upgrade:** update `build.sbt`, republish the exact coordinate, and
  rerun the check. Do not accept an older jar merely because it uses the same snapshot name.

The check also exercises the opt-in [scalar GVM comparison](../../docs/GVM_SCALAR_BHATTACHARYYA.md)
from the published jar, including cancellation-sensitive overlap, the bounded-tail
curved case (radius 7 with retained error/tail checks), and budget refusal.
It checks [GVM mutual information](../../docs/GVM_MUTUAL_INFORMATION.md), all nine
[common-family adapters](../../docs/COMMON_DISTRIBUTIONS.md), scalar/count divergences
and explicit joint-table MI from that same verified JAR. These additions need a newly
published artifact; the older immutable integration bundle does not contain them.
The check covers representative API/linkage/lifecycle behavior, not exhaustive statistical
coverage, OSGi, arbitrary graphs, Java facades, memory ceilings or performance. It uses
no test framework dependency. `run` forks so sbt's own libraries do not satisfy missing
application dependencies accidentally. Producer and consumer must see the same local
Ivy repository in the default local-publication mode; custom `sbt.ivy.home` settings
must agree. The explicit Maven-layout mode does not use Ivy local.

Related: [acceptance protocol and evidence](../../docs/CORE_PERFORMANCE_ACCEPTANCE.md),
[JVM consumer contract](../../CONSUMER_BOUNDARY.md), and [building](../../docs/BUILDING.md).

## 7.4.1 diagnostic preparation control

When switching `FIGARO_RELEASE_MAVEN_ROOT` between local bundles with the same
coordinates, start with `clean; update` before the consumer commands, even when
using a fresh action-cache directory. A retained dependency-resolution report can
otherwise still point at the earlier bundle. Keep the expected loaded-JAR hash
assertion: it detects this mistake instead of silently testing the wrong bytes.

`DiagnosticConsumerCheck` exercises `McmcDiagnostics.summarize` from the exact
hash-verified packaged JAR using repeated-state traces with nonconstant tails.
The normal invocation requires the corrected tail ESS and preserves undefined
constant-tail behavior. `--expect-legacy-tie-defect` is a separate negative control
restricted to the immutable 7.4.0 JAR; it must reproduce the old missing-tail result.
It is never an alternative success mode for the 7.4.1 candidate. Development CI
runs all six existing consumers plus this seventh check, including the Maven-layout
path without Ivy local. The existing Central publication workflow remains pinned
to 7.4.0 until publication of a new version is separately approved.
