# Figaro 7.3.0 release readiness

Previous published baseline: **7.2.0**, tag `v7.2.0`.
**7.3.0 is published and verified on Maven Central.** All integration, final-source,
packaging and Central-only consumer gates passed. Verification completed 2026-10-22 UTC.

## Consumer impact and release decision

This is a **minor release, 7.3.0**, under the [consumer-impact policy](../CONTRIBUTING.md#consumer-impact-release-policy).
This is useful compatible functionality, not an emergency correction to released 7.2.0.
The cumulative scope since that release includes the separate opt-in
[fixed-budget API](FIXED_BUDGET_FILTER.md), explicit resampling schedules and methods,
retained log weights, decision-focused stratification and selection diagnostics.
It suits callers who need a fixed candidate/evaluation count and control over when
to resample. It does not replace the existing stopping filters or change their defaults.

The preceding diversity experiment remains a no-promotion research result. Its
successor schedule experiment supports this bounded API, not a universal ESS threshold.
Historical negative results remain in their original status documents.

## Completed integration gates

- Numerical review, population-limit reconciliation and production integration:
  [final evidence and limitations](STRATIFIED_INTEGRATION.md).
- Independent oracle: 6,159 cases and 1,572,906 ancestor decisions; no disagreements.
- Two public-API JVM runs: 12,288 trajectories each, all 576 unchanged screens passed,
  zero refusals/errors and identical scientific/status/work replay. Replay is not new seeds.
- 30 focused and 80 broader Scala tests; unchanged stopping-filter continuous benchmark:
  all 450 checks passed across 7,680 trajectories.
- Documentation/reference checks, generated API freshness and five isolated packaged consumers.
- [Remote CI 39841735591](https://github.com/mattwilkinsphoto/figaro/actions/runs/39841735591)
  on `6c2071074efc3287920f2a7906e2553b820e924f`: all four jobs passed, including clean
  byte-reproducible builds, artifact checks and packaged consumers.

No runtime or numerical protocol changes are made by the final-status documentation
commit. The merged snapshot [main run 39853646835](https://github.com/mattwilkinsphoto/figaro/actions/runs/39853646835)
also passed before publication; final-version evidence follows below.

## Retained limits

Population configuration permits 2..1,000,000, but ambiguous nonuniform stratified
decisions refine only at N<=4096. Exact equal-positive-weight support works across
the configuration range. Log span above 1,000,000, unresolved 160-digit comparisons
and refinement-resource exhaustion fail explicitly. No redraw, truncation or method
substitution occurs. Finite-bit sampling is not ideal continuous-uniform sampling.
No guaranteed missing-mode detection, universal precision or performance claim is made.

## Final publication checklist

- [x] Obtain explicit approval to publish 7.3.0, conditional on all required gates.
- [x] Set the final version, build immutable 7.3.0 artifacts and pass final-source CI.
- [x] Verify reproducible packaging, legal contents and isolated consumers against
  those exact final-version artifacts, not the snapshot coordinates.
- [x] Sign and validate the Maven Central bundle using the existing publication workflow.
- [x] Publish only after those gates pass; verify Central artifact bytes and run the
  Central-only packaged consumers. Record the final tag, commit and results here.

## Final execution and immutable artifacts

All four [final-source main CI jobs](https://github.com/mattwilkinsphoto/figaro/actions/runs/39854173198)
passed on `abf5eec75b62f26c0b812859322dc76181bf8139`. This includes binary compatibility
with 7.0.0/7.1.0/7.2.0, TASTy and compiled-client compatibility with the hash-verified
7.2.0 JAR, clean byte-for-byte reproducibility, legal/artifact checks and all five
packaged consumers. The downloaded bundle was verified as clean-source, non-candidate
7.3.0, with all five enclosed artifact hashes checked before release.

The immutable [v7.3.0 release](https://github.com/mattwilkinsphoto/figaro/releases/tag/v7.3.0)
uses that exact source. Bundle SHA-256:
`798cb09713cf60cfdbac22df1427d92b5239438f7c11ebf25031f37cb82d7c21`.
Thin JAR SHA-256:
`6b82a0efc92ea4119a51cdfe42e38090b3680d129625d16b653e37cc821cee1f`.
These are the tested Linux CI bytes, not a cross-platform reproducibility claim.

[Signed staging](https://github.com/mattwilkinsphoto/figaro/actions/runs/39857552154)
validated deployment `6e2cc053-d9f0-4902-b471-f8b6aaf04896`. An
[initial dispatch](https://github.com/mattwilkinsphoto/figaro/actions/runs/39857851408)
supplied a malformed deployment argument and was rejected by the UUID guard before
any Portal publication request. The failed log is preserved. The
[corrected publication and acceptance run](https://github.com/mattwilkinsphoto/figaro/actions/runs/39858137427)
used the same validated deployment, confirmed `PUBLISHED`, matched all four Central
artifact bytes and passed all five Central-only consumers. No duplicate upload,
guard relaxation or released-byte replacement occurred.

The fresh-runner consumers verify the loaded JAR hash and exercise baseline,
integration, capability, budget/filter and fixed-budget APIs. The new check includes
both selection methods, explicit policies, retained tiny weights and terminal
numerical refusal. This is packaged-API acceptance, not a new statistical theorem
or a guarantee for arbitrary application models.

Scientific provenance remains [INF-15](RESEARCH_REFERENCES.md#inf-15). Numerical
protocols/results were preserved, not revised by publication. Subsequent documentation
updates do not change the tag or immutable Maven artifacts.
