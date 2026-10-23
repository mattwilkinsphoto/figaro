# Figaro 7.4.0 release and verification

Previous published baseline: **7.3.0**, tag `v7.3.0`.
**7.4.0 is published and verified on Maven Central.** Required packaging gates
passed before publication; all four public artifacts and six Central-only
consumers were verified afterward. The advisory timing result below remains unresolved.

## Consumer impact

This normal-priority minor release adds opt-in guided and auxiliary operations to
[fixed-budget filtering](GUIDED_FIXED_BUDGET_FILTER.md). Use guidance when an
informative observation makes ordinary model draws inefficient; add auxiliary
look-ahead when predictive information helps choose parents. Both require correct
normalized target/proposal densities and full importance corrections. Poor guides
can increase cost and error; a larger bootstrap population remains an alternative.

The caller still chooses population size, resampling policy and method explicitly.
Previous posterior ESS controls scheduled resampling. Carry steps do not evaluate
look-ahead or apply an ancestor correction. The existing stopping estimator,
bootstrap entry points, configuration defaults and result/report shapes are unchanged.
New proposal-work diagnostics are separate from the existing step report.

## Completed integration evidence

The [frozen milestone record](GUIDED_FIXED_BUDGET_STATUS.md) retains the independent
oracle, 46,080 three-JVM executions, all 720 correctness screens and cost-adjusted
utility results, including negative results. These are 256 independent seeds per
configuration/fixture, not 768. No numerical protocol or acceptance rule changes
as part of release preparation.

Snapshot [branch CI](https://github.com/mattwilkinsphoto/figaro/actions/runs/39964999116)
and [merged main CI](https://github.com/mattwilkinsphoto/figaro/actions/runs/39968790021)
passed all five jobs at `a31fca1a`, including reproducible packaging and all six
consumers. Independent raw-log and repaired-runner audits passed. The
[CI repair](CI_VALIDATION.md) applies only to named direct-launcher tests, not
library artifacts or the publication runner. Its upstream report remains unsubmitted.

## Final publication checklist

- [x] Explicit conditional approval to prepare and publish 7.4.0.
- [x] Final-source CI, binary compatibility through 7.3.0, retained 7.2 TASTy
  check and additional 7.3 TASTy/compiled fixed-budget client check.
- [x] Clean byte-reproducible final-version artifacts, legal/POM checks and all six
  consumers using exact packaged bytes, without producer classes or Ivy-local fallback.
- [x] Immutable GitHub release and reviewed source/bundle/consumer-hash pins.
- [x] Signed Central staging and VALIDATED deployment review.
- [x] Publish that deployment, verify all four Central artifact bytes and all six
  Central-only packaged consumers; record source, hashes and run links here.

## Final-source evidence

[Final-source CI](https://github.com/mattwilkinsphoto/figaro/actions/runs/39975157515)
passed all five jobs at `86f48a06334da78a9315292d2e7fd87fd7e7ef88`.
The [immutable GitHub release](https://github.com/mattwilkinsphoto/figaro/releases/tag/v7.4.0)
contains the exact clean, byte-reproducible artifacts tested by all six consumers.
Bundle SHA-256: `d64b4aecdd1b87301c428fd22653e36961626b0898554fe9da0eba58019e337e`.
Thin JAR SHA-256: `0abfdadfeb007af1eee39a04b0591e49686d4cab74181402ccf8d9948abe4dbf`.

[Signed staging](https://github.com/mattwilkinsphoto/figaro/actions/runs/39980905388)
reached VALIDATED. Deployment `3fdcd5e9-13b3-4d80-86a9-d630adfe7c93` was reviewed
against the exact source/bundle pins and all four signed payloads and checksums.
[Publication and consumer acceptance](https://github.com/mattwilkinsphoto/figaro/actions/runs/39981195050)
then completed on 2026-10-23 UTC: PUBLISHED, all four Central artifacts matching
the approved bytes, and all six consumers resolving Central only on a fresh runner
with the expected loaded-JAR hash. Independent public downloads also matched
the thin JAR, sources JAR, Scaladoc JAR and POM hashes. No second upload or repeat
publish request was needed. The [Central guide](MAVEN_CENTRAL.md) provides coordinates.

The raw log also contains **one failed advisory selection-timing assertion**:
19 advisory tests passed, one failed; observed ratio 2.91156 exceeded 2.42.
This pre-existing `continue-on-error` step is not a required packaging gate and
is not reported as passed. All 99 utility class/TASTy entries match published
7.3.0 byte-for-byte. That rules out an altered utility implementation, not an
environment-specific performance difference. The cause remains unresolved;
no timing rerun or threshold change was made. See [CI validation](CI_VALIDATION.md).

## Remaining limits

This is sequential, owner-thread filtering over detached immutable states, not
arbitrary shared Figaro graphs. Correct callback densities and support remain the
caller's responsibility. Fixed N does not bound arbitrary callback cost or imply
precision. Existing stratification precision/resource/population limits still
apply, including to auxiliary selection laws. There is no automatic proposal,
threshold or budget selection, smoothing, rejuvenation or guaranteed missing-mode
detection. Comparative utility is not established for arbitrary high-dimensional,
real-data or long-history models.

[Research provenance](RESEARCH_REFERENCES.md#inf-16).
