# Figaro 7.4.0 release readiness

Previous published baseline: **7.3.0**, tag `v7.3.0`.
Final release preparation and publication are authorized **after all packaging
gates pass**. Setting the version to 7.4.0 does not establish Central availability.

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
- [ ] Final-source CI, binary compatibility through 7.3.0, retained 7.2 TASTy
  check and additional 7.3 TASTy/compiled fixed-budget client check.
- [ ] Clean byte-reproducible final-version artifacts, legal/POM checks and all six
  consumers using exact packaged bytes, without producer classes or Ivy-local fallback.
- [ ] Immutable GitHub release and reviewed source/bundle/consumer-hash pins.
- [ ] Signed Central staging and VALIDATED deployment review.
- [ ] Publish that deployment, verify all four Central artifact bytes and all six
  Central-only packaged consumers; record source, hashes and run links here.

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
