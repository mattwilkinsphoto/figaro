# Maven Central publication

## Overview and current status

The Central release workflow distributes Figaro as a normal Maven dependency,
without asking applications to compile Figaro or configure a downloaded file
repository. Publication infrastructure is prepared; availability must be confirmed
by a successful **publish** or **verify** workflow run before relying on Central.
The existing GitHub 6.1.0 bundle remains usable independently.

The first publication reuses the exact 6.1.0 thin JAR, sources, documentation and
POM from the tested GitHub release. It does not rebuild them, change the POM under
an existing version, move the tag, or publish the supplemental fat JAR. The existing
POM preserves the original contributor metadata. Maintainer metadata improvements
belong in the next version rather than silently changing released bytes.

## Consumer quick start (after publication)

1. Use JDK 17+ and Scala 3.9.0.
2. Add to `build.sbt`:

   ```scala
   scalaVersion := "3.9.0"
   libraryDependencies += "io.github.mattwilkinsphoto" %% "figaro" % "6.1.0"
   ```

3. Compile and run the application normally. No `publishLocal`, local Figaro
   checkout or custom Figaro resolver is needed. Maven/Gradle consumers use the
   explicit artifact name `figaro_3`.

Central publication does not change runtime performance, statistical guarantees,
Scala/JDK requirements, or the licensing terms.

## Maintainer workflow

The manually dispatched **Maven Central release** workflow only releases from
`main`. Pushes and pull requests run in-memory tooling tests without credentials;
they do not stage or publish anything. Actions are pinned to commit hashes.

1. Choose **stage**. The workflow checks successful main CI at the approved source
   revision and the approved GitHub bundle SHA-256. It verifies the source record,
   artifact hashes and POM metadata; signs and locally verifies all four artifacts;
   adds checksums; and uploads a `USER_MANAGED` deployment to Sonatype. It waits
   for `VALIDATED`, not publication. Save the public deployment UUID from the log
   or the `central-release-record` artifact's `deployment.json`.
2. Review the stage result and Sonatype validation. Choose **publish** and supply
   that exact UUID. The workflow refuses a different deployment name or different
   coordinates, and only publishes a validated deployment. The action is an
   explicit release approval: Central publication is immutable.
3. Publication waits for `PUBLISHED`, downloads all four artifacts from Central and
   compares their bytes with the original GitHub release. A fresh-runner standalone
   consumer then resolves from **Central only**, verifies the loaded JAR hash and
   exercises the integration checks. Choose **verify** to repeat these read-only
   availability and consumer checks later.

The current release approval is deliberately pinned to 6.1.0 in
`tools/central_release.py`. A new version requires reviewing and updating the
version, source revision, bundle checksum and expected consumer hash together;
an arbitrary tag or user-supplied download URL is not accepted.

## Credentials, signing and recovery

Repository Actions secrets:

| Name | Contents |
| --- | --- |
| `MAVEN_CENTRAL_USERNAME` | Portal token-generated username, not account login |
| `MAVEN_CENTRAL_PASSWORD` | Portal token-generated password |
| `GPG_PRIVATE_KEY` | Passphrase-protected ASCII-armored signing key |
| `GPG_PASSPHRASE` | Signing-key passphrase |
| `GPG_FINGERPRINT` | Full uppercase primary-key fingerprint |

Never commit credentials, private keys, key backups or revocation certificates.
Keep an encrypted recovery backup and its password separately. Publish the public
key to a Central-supported keyserver. Token expiration affects new publication,
not existing consumers. Replacing a token requires updating both token secrets.

Signing occurs in a separate temporary directory on the Linux runner. Secrets
are removed from the subprocess environment, private inputs are sent through stdin,
and the key directory is removed after signing. Only signed public artifacts and
the non-secret receipt are uploaded. Signing and publication use different steps
of the human workflow; publishing never requires exposing a private key to chat.

## Gotchas and failure handling

- **Do not re-upload blindly after a timeout.** Upload is not automatically retried:
  the server may have accepted it despite a lost response. Inspect the Portal for
  the deployment name/UUID. Validation failures remain available for diagnosis.
- A successful upload is not a successful validation, and validation is not
  publication. A publication timeout can occur after release; use **verify** or
  inspect the existing deployment instead of creating another one.
- A `PUBLISHED` response can precede download availability. Polling is bounded;
  a later **verify** run can complete the consumer gate after propagation.
- Coordinate metadata is required before publishing. If the Portal omits it
  during/after publication, the workflow still checks deployment identity and
  independently compares all four public artifact hashes; omission is not treated
  as proof of a different artifact. Any supplied conflicting coordinates fail.
- A wrong/expired key or missing public-keyserver record is a release blocker,
  not a reason to omit signatures. Signing passphrase errors are deliberately
  reported without echoing private subprocess output.
- Central artifacts are immutable. Fixes require a new version. Keep the GitHub
  bundle as a supplemental channel and never replace a JAR under existing
  coordinates to address metadata or platform differences.
- The bundle's original README records its pre-Central distribution status. It
  is historical release content; this page and the workflow record track later
  Central availability without rewriting the original archive.

## Related

- [Release 6.1 scope and validation](RELEASE_6_1.md)
- [Standalone consumer](../tools/acceptance-consumer/README.md)
- [Sonatype publication requirements](https://central.sonatype.org/publish/requirements/)
- [Central Portal API and deployment states](https://central.sonatype.org/publish/publish-portal-api/)
- [Central immutability policy](https://central.sonatype.org/publish/requirements/immutability/)
