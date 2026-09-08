# Independent Figaro consumer check

This standalone build tests the published dependency boundary, not a source-project
dependency. It exists to catch stale snapshots, missing runtime dependencies and example/
test classpath leakage that an in-repository example could miss.

## Quick start

1. From the Figaro root run `sbt "figaro / publishLocal"`.
2. Set `FIGARO_EXPECTED_SHA256` to the SHA-256 of the built thin Figaro jar.
3. From this directory run `sbt "runMain FigaroConsumerCheck"` with the same JDK/local repository settings.

## API and common patterns

`FigaroConsumerCheck.main(args: Array[String]): Unit` accepts an empty array and requires
the environment variable above. It prints the loaded jar hash and a completion line;
missing/incorrect hashes, non-jar resolution or any failed model/lifecycle check throw.
Its individual checks are ordinary application calls, not additions to Figaro's public API.

- **Before integrating a snapshot:** publish it, then run the three steps above.
- **In CI:** set the hash from the just-built jar and run this separate build after publication.
- **After a dependency upgrade:** update `build.sbt`, republish the exact coordinate, and
  rerun the check. Do not accept an older jar merely because it uses the same snapshot name.

The check also exercises the opt-in [scalar GVM comparison](../../docs/GVM_SCALAR_BHATTACHARYYA.md)
from the published jar, including cancellation-sensitive overlap and budget refusal.
The check covers representative API/linkage/lifecycle behavior, not exhaustive statistical
coverage, OSGi, arbitrary graphs, Java facades, memory ceilings or performance. It uses
no test framework dependency. `run` forks so sbt's own libraries do not satisfy missing
application dependencies accidentally. Producer and consumer must see the same local
Ivy repository; custom `sbt.ivy.home` settings must agree.

Related: [acceptance protocol and evidence](../../docs/CORE_PERFORMANCE_ACCEPTANCE.md),
[JVM consumer contract](../../CONSUMER_BOUNDARY.md), and [building](../../docs/BUILDING.md).
