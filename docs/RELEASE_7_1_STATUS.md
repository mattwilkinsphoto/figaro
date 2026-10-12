# Figaro 7.1 release readiness and follow-up

Baseline: published 7.0.0. Publication of 7.1.0 is authorized, conditional on the
checks below. A development version or Git tag is not evidence of publication.

## Acceptance checklist

- [x] Backward compatibility audit against the published 7.0.0 artifact; resolve
  consumer-facing breaks rather than silently excluding them.
- [x] Longer synthetic filtering sequences, abrupt observation changes, repeated
  budget exhaustion and persistent ancestry collapse; retain strict work bounds.
- [ ] Final remote regression, independent numerical, documentation and packaged-consumer gates.
- [ ] Clean-build reproducibility and branch/main remote CI.
- [ ] Release notes, scoped commit and merge; preserve unrelated local work.
- [ ] Immutable GitHub release, validated signed Central deployment, publication
  and Central-only consumer verification for 7.1.0.
- [ ] After publication: research, implement and verify an advisory independent-pilot
  budget helper. Do not silently tune production stopping from its own outcomes.
- [ ] Stop for design discussion before implementing general likelihood-weighted
  bounded filtering.

## Evidence and status

The six capability extensions have prior local test and numerical evidence in
[the capability status record](CAPABILITY_EXPANSION_STATUS.md). Release-specific
checks above are pending; no release or remote CI success is yet claimed.

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
only to an isolated development repository; public publication is still pending.

All three separate application consumers passed against the clean 7.1.0 thin JAR,
SHA-256 `feb3fa1aade82910864890d34c3168a32ef63f2ba2a0a06ed8118368326d80bc`.
Local research-reference checks, 38 documentation tests and 13063 generated public
method entries passed. A bridge-file CRLF whitespace check failure was corrected
by normalizing that edited file, not by excluding it. The original failure log is
retained privately. Unrelated documentation edits remain excluded from this release.

All four [branch CI jobs](https://github.com/mattwilkinsphoto/figaro/actions/runs/38269359687)
passed for `be8e23a49e38a5b976e6313c6c1f81b5cbaafbe0`, including clean-build
reproducibility and packaged consumers. The 7.0.0-compiled custom filter extension
also executed with the new JAR. Main CI and public publication remain pending.
The main gate additionally selects bash explicitly so `tee` cannot hide a failing
study. No regression or compatibility exclusions were added.
