# Figaro 7.3.0 release readiness

Published baseline: **7.2.0**, tag `v7.2.0`. Final artifact target: **7.3.0**.
All fixed-budget integration gates passed; the maintainer approved main integration.
**7.3.0 is release-eligible and ready for final release preparation, not yet published.**
The final documentation and implementation are delivered together on main.

## Consumer impact and release decision

Recommend a **minor release, 7.3.0**, under the [consumer-impact policy](../CONTRIBUTING.md#consumer-impact-release-policy).
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
commit. The main-branch workflow separately verifies the merged tree; its exact
commit/run is recorded in the merge handoff rather than predicted here.

## Retained limits

Population configuration permits 2..1,000,000, but ambiguous nonuniform stratified
decisions refine only at N<=4096. Exact equal-positive-weight support works across
the configuration range. Log span above 1,000,000, unresolved 160-digit comparisons
and refinement-resource exhaustion fail explicitly. No redraw, truncation or method
substitution occurs. Finite-bit sampling is not ideal continuous-uniform sampling.
No guaranteed missing-mode detection, universal precision or performance claim is made.

## Final publication checklist (not integration blockers)

- [x] Obtain explicit approval to publish 7.3.0, conditional on all required gates.
- [ ] Set the final version, build immutable 7.3.0 artifacts and pass final-source CI.
- [ ] Verify reproducible packaging, legal contents and isolated consumers against
  those exact final-version artifacts, not the snapshot coordinates.
- [ ] Sign and validate the Maven Central bundle using the existing publication workflow.
- [ ] Publish only after those gates pass; verify Central artifact bytes and run the
  Central-only packaged consumers. Record the final tag, commit and results here.

No release tag is created and no published artifact is replaced by main integration.

Final preparation advances the binary compatibility controls to include 7.2.0
alongside 7.0.0/7.1.0, and the TASTy/compiled-client control to the hash-verified
published 7.2.0 JAR. Final-version CI and publication results will be recorded here
after execution; setting the version is not evidence of publication.
