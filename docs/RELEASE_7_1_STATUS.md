# Figaro 7.1 release readiness and follow-up

Baseline: published 7.0.0. Publication of 7.1.0 is authorized, conditional on the
checks below. A development version or Git tag is not evidence of publication.

## Acceptance checklist

- [x] Backward compatibility audit against the published 7.0.0 artifact; resolve
  consumer-facing breaks rather than silently excluding them.
- [x] Longer synthetic filtering sequences, abrupt observation changes, repeated
  budget exhaustion and persistent ancestry collapse; retain strict work bounds.
- [x] Final remote regression, independent numerical, documentation and packaged-consumer gates.
- [x] Clean-build reproducibility and branch/main remote CI.
- [x] Release notes, scoped commit and merge; preserve unrelated local work.
- [x] Immutable GitHub release, validated signed Central deployment, publication
  and Central-only consumer verification for 7.1.0.
- [x] After publication: research, implement and verify an advisory independent-pilot
  budget helper. Do not silently tune production stopping from its own outcomes.
- [ ] Stop for design discussion before implementing general likelihood-weighted
  bounded filtering.

## Evidence and status

The six capability extensions have prior local test and numerical evidence in
[the capability status record](CAPABILITY_EXPANSION_STATUS.md). Release-specific
checks and publication are complete. The following paragraphs preserve the sequence
of checks, including issues found and corrected before release.

Compatibility tools are assessment aids, not behavior proofs. MiMa checks JVM
linkage; Scala 3 TASTy compatibility is a separate concern. Sources inspected:
[MiMa](https://github.com/scala-garden/mima) and
[TASTy-MiMa](https://github.com/scalacenter/tasty-mima).

Readiness execution: the first MiMa run identified two new synthetic trait
accessors introduced by diagnostic state. State storage was moved off the public
trait into a synchronized weak, array-identity-keyed registry. MiMa then passed
without exclusions; 42 focused regression tests passed. This does not make mutable
filters thread-safe. New tests cover 2048 all-success steps, 256 exhausted steps
with a single ancestor followed by an impossible observation, and 512 independent
64-step runs with abrupt rare observations. The latter retained all outcomes and
passed its predeclared six-standard-error evidence gate.

The initial supplemental TASTy tool build failed because parser 1.10.0 pulled Scala
3.10 artifacts. Parser 1.9.0 targets Scala 3.9; the rerun passed without exclusions. This tooling mismatch
did not alter Figaro's published dependencies or suppress a compatibility finding.

The clean 7.1.0 build with a fresh action cache compiled 349 library sources,
83 example sources and 259 test sources and built thin/assembly/source/API/POM
artifacts. Existing compiler/Scaladoc warnings are retained. Local publication is
only to an isolated development repository; public verification is recorded below.

All three separate application consumers passed against the clean 7.1.0 thin JAR,
SHA-256 `feb3fa1aade82910864890d34c3168a32ef63f2ba2a0a06ed8118368326d80bc`.
Local research-reference checks, 38 documentation tests and 13063 generated public
method entries passed. A bridge-file CRLF whitespace check failure was corrected
by normalizing that edited file, not by excluding it. The original failure log is
retained privately. Unrelated documentation edits remain excluded from this release.

All four [branch CI jobs](https://github.com/mattwilkinsphoto/figaro/actions/runs/38269359687)
passed for `be8e23a49e38a5b976e6313c6c1f81b5cbaafbe0`, including clean-build
reproducibility and packaged consumers. The 7.0.0-compiled custom filter extension
also executed with the new JAR. Main CI and publication subsequently passed.
The main gate additionally selects bash explicitly so `tee` cannot hide a failing
study. No regression or compatibility exclusions were added.

All four [main CI jobs](https://github.com/mattwilkinsphoto/figaro/actions/runs/38272596410)
passed for release source `ad22068d9d47dfca2be9e35ff44696c7be9faeef`. The downloaded
non-candidate, clean-source bundle and all five contained artifact hashes were
verified. Approved bundle SHA-256:
`7028044a441594d94a8c7d28b206edc71830d823ee28a10c41aee70fcf128a4d`.
Approved release thin JAR SHA-256:
`324f6a640e6b0c0e802aa1ab39821b46135f21ef23c2c1dc38da29f6605c3f35`.
These are the CI release bytes, not the earlier local Windows build. Reproducibility
was checked between independent clean builds in the same CI environment; no
cross-platform byte-identity claim is made.

Central publication completed on 2026-10-12. The signed deployment validated;
the first publication wait timed out in `PUBLISHING`, and a same-deployment
continuation confirmed `PUBLISHED`. All four public artifacts match the approved
bytes and all three Central-only consumer programs passed. The [publication record](MAVEN_CENTRAL.md)
links the actual successful and timed-out workflows. No duplicate upload was made.

## Post-release budget adviser

The [budget adviser](PARTICLE_BUDGET_ADVISER.md) is implemented separately in
7.2.0-SNAPSHOT, not added to the immutable 7.1.0 artifacts. Local validation passed:
50 focused tests, 20 actual-output high-precision numerical rows, independent cap
minimality checks, packaged consumer, and Scala compatibility against the
hash-verified Central 7.1.0 baseline. Documentation checks passed: 38 tooling tests,
13077 generated public method entries and 15137 local link targets.

The first boundary test caught avoidable beta-inverse error for shape one; closed-form
boundary quantiles fixed it without relaxing the tolerance. An initial documentation
check ran while Scaladoc was being regenerated; its post-build rerun passed. The
compatibility fixture refused a local 7.1.0 test artifact; Central-only resolvers plus
a fresh tooling action cache resolved the published baseline, and the SHA-256 gate
passed without exclusions. Original failed logs are retained in the private evidence
record. See [branch CI](https://github.com/mattwilkinsphoto/figaro/actions?query=branch%3Acodex%2Fparticle-budget-adviser)
for subsequent remote gate results; this paragraph records locally executed results.

The first adviser branch CI run passed its Scala tests but stopped before the
independent Python check: the new oracle preceded installation of its pinned
`mpmath` dependency. Installation was moved before both numerical oracles, with
a fast ordering regression test. This did not change numerical acceptance criteria
or the already published 7.1.0 library.

The second adviser CI run passed numerical controls, regressions and clean-build
reproducibility, but caught an overbroad build resolver change at `publishLocal`.
MiMa now uses its dedicated `mimaDependencyResolution` hook to the isolated
Central-only tooling project; the library's normal publishing resolver is retained.
Local publication was also added to the early packaging preflight. No runtime
algorithm or numerical tolerance changed in either CI correction.

General likelihood-weighted bounded filtering remains at the discussion gate.
No implementation, automatic cutover or arbitrary-graph support is claimed.
