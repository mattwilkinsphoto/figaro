# Independent Figaro consumer check

This standalone build tests the published dependency boundary, not a source-project
dependency. It exists to catch stale snapshots, missing runtime dependencies and example/
test classpath leakage that an in-repository example could miss.

## Quick start

The current check includes [distribution constructions and GMMs](../../docs/DISTRIBUTION_CONSTRUCTIONS.md),
Gaussian KL/Bhattacharyya/partition MI, and observation-ready scalar/count/vector adapters.
Figaro 6.1 additionally checks exact-coordinate conditional copulas, bounded GVM
mixture fitting and full-mixture linear/angular mutual information.

To test Maven Central in a fresh environment, set `FIGARO_CENTRAL_ONLY=true`,
`FIGARO_VERSION=7.0.0`, and
`FIGARO_EXPECTED_SHA256=3d6806de0c895b432c1fc699cf3226df9ec0f8c8ad021479005e5838538a4eb4`,
then run `sbt "runMain FigaroConsumerCheck"` and
`sbt "runMain IntegrationConsumerCheck"` here. This mode excludes Ivy local and
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

For the capability-expansion release build, set `FIGARO_VERSION=7.1.0`
and `FIGARO_CAPABILITY_CHECK=true`, set the hash of that exact new JAR, and additionally
run `runMain CapabilityConsumerCheck`. This compiles the separate capability source
directory; leave the flag unset when testing immutable 7.0.0 or 6.1.0 artifacts.
The new check exercises grouped counts, native log ratios, automatic scalar moves,
discarded/frozen tuning, tiny-shape density and Cholesky-entry density APIs.

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
