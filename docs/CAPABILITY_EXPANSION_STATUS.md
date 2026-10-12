# Capability expansion after 7.0.0

Status: **all six bounded extensions implemented and locally verified, including
the approved bounded-adaptive indicator workflow. Committed and pushed; all four
branch CI jobs passed. Main CI and release publication remain pending.**
Baseline: published 7.0.0, main `9dee7a74`. The approved scope is the six
remaining capability limits below, not removal of mathematical or machine limits.

The dated entries below preserve the development history, including then-pending
checks. Current release progress is in the [7.1 checklist](RELEASE_7_1_STATUS.md).

Research provenance: [INF-05](RESEARCH_REFERENCES.md#inf-05),
[INF-06](RESEARCH_REFERENCES.md#inf-06), [INF-07](RESEARCH_REFERENCES.md#inf-07).

## Requirements and acceptance checklist

- [x] Observations: design and validate deeper owned likelihood graphs; preserve
  evidence normalizers, rejection rollback, ownership and worker-count replay.
  Deterministic continuous equality needs a specified likelihood/disintegration,
  not a silent reinterpretation as noisy evidence. Shared arbitrary graphs remain
  a separate ownership problem.
- [x] Counts: reduce combinatorial work using exact model structure, with independent
  enumeration controls, explicit budgets and no discarded tails. General exact
  continuous inference and unlimited factor treewidth are not promised.
- [x] Proposals: broaden supported legacy/custom targets and add explicit automatic
  coordinate selection and warm-up-only scale tuning. Production must be frozen;
  report selected scales and test posterior controls and rejected-state restoration.
- [x] Custom MH: provide a reusable native log-ratio construction, retain existing
  overrides, verify extreme ratios and annealing separation. Unknown third-party
  proposal formulas cannot be migrated automatically.
- [x] Numerical ranges: independently validate targeted boundary expansions;
  retain explicit refusal of unresolved tails, singularity and representation loss.
- [x] Particle recovery: select a bounded-work, statistically specified approach;
  preserve strict default failure. Test stop-at-budget edge cases, evidence estimates,
  impossible evidence, state ownership and total computational work. A design
  choice is resolved: opt-in indicator filtering, fixed-budget diagnostics and
  recommendations, no silent post-failure retries. Local exact/lifecycle/calibration
  and packaged-consumer gates passed; wider contracts remain explicitly unsupported.
- [x] User/API examples, research mappings, local regression and packaged-consumer
  gates for the implemented first five areas. Remote CI has not been run.
- [x] Report verified outcomes, remaining boundaries and conditional release eligibility.

## Initial design assessment

Reuse Figaro's existing MH, constraints, owned universes and finite Select backend.
Candidate additions are nested single-owned likelihood spines, exact multinomial
category aggregation, transformed-coordinate proposal adapters and discarded
warm-up tuning, native log proposal helpers, and a bounded-work particle kernel.
All six bounded extensions are implemented and locally validated. None of these
extensions is in the immutable 7.0.0 release.

Research inspected 2026-10-12: Stan's warm-up/frozen-sampling separation; the
Garthwaite/Fan/Sisson adaptive-scaling preprint (section 2 inspected);
multinomial marginalization teaching reference; alive-filter literature
and Sherlock/Golightly/Lee's 2026 Frankenfilter preprint, sections 1–2 and Algorithms
2–5 and Appendix A's exchangeability/induction argument. The latter distinguishes
correct truncation from biased retry/threshold rules.
No external implementation has been copied or dependency installed.

## Execution record

- Recovery follow-up approved: implement an owned immutable-state indicator kernel
  with a fresh fixed-graph Element adapter; retain corrected variable-size survivors
  between steps. Add pre-resampling diagnostics to existing fixed-budget filters.
  Acceptance checklist: exact stopping-tree evidence controls; cap boundary;
  multistep posterior/evidence and cost comparisons including zero runs; lifecycle,
  RNG replay, graph refusal/cleanup; user/API guide; packaged consumer; evidence archive.
- Initial recovery validation: 26/26 tests passed. Broader follow-up: 61/61 passed
  across seven suites, then 4000 runs per method/scenario passed the predeclared
  six-standard-error evidence and unnormalized posterior-moment calibration gates.
  These test selections overlap. Final follow-up passed 29/29 tests including three
  additional ancestor, posterior and graph-refusal checks; calibration was rerun
  with identical results. Test counts are not added together as unique coverage.
- Synthetic rare-observation comparison: adaptive had 28 zero outcomes versus 244
  for the fixed bootstrap reference in 4000 runs, with mean candidate work 261.3365
  versus 261.765. Adaptive still finished below target in 3432 runs (including zeros).
  Ordinary-case fixed filtering had slightly lower evidence and joint-moment standard
  errors at similar work. This is collapse resistance, not universal superiority;
  no application timing or speedup result is claimed. All zero outcomes were included.
- Final snapshot thin/source/API artifacts built and published locally; all three
  independent consumers passed, including the new filter and diagnostic APIs.
  Snapshot JAR SHA-256:
  `3061a7bca7cc1f9d7572b18cb5796a86c3f87d9ec4c04500d4952e70c7efb68a`.
  "Published" in the legacy consumer's output means LOCAL publication only.
- Final documentation gates passed: research mappings, 38 documentation tests,
  13063 public method entries/freshness, 15090 local link targets in 165 Markdown
  files, four-job CI YAML parse, and task-scoped whitespace checks. Existing
  Scaladoc warnings remain visible. CI includes the new tests/study but has not run remotely.
- Recovery evidence is archived privately with source/baseline hashes, commands,
  actual logs, machine-readable study output, runtime details and the tested JAR.
  The earlier first-five-area archive is preserved as a separate revision.

- Initial state: created a separate capability-expansion branch; preserved two
  unrelated existing documentation edits. Tests had not yet run at that point.
- Initial code inspection confirms the six stated restrictions. Support-aware
  proposals currently assume identity randomness, so legacy adapters require
  explicit value-to-randomness mappings, not just loosening type guards.
- First production compile passed; test compilation failed because a new custom
  Atomic fixture omitted its density method. The fixture was corrected; no numerical
  criterion or production API was changed to obtain a pass.
- Focused run: 34/34 passed across five suites. Broader run after additional guards:
  91/91 passed across nine suites, including existing ownership, integration,
  count, covariance and distribution regressions. These selections overlap.
- Independent numerical comparison: 38 actual library rows matched 350-digit
  mpmath formulas using a predeclared normalized tolerance of 5e-14. This is
  bounded numerical evidence, not validation of every parameter combination.
- Existing tiny-shape refusal test was replaced with acceptance at its old point
  and refusal at an unrepresentable endpoint. This is an explicit contract change,
  supported by new independent checks, not a relaxed numerical tolerance.
- Existing `Model`/`ChainResult` constructor signatures were retained by exposing
  tuning through separate `TunedModel`, `TunedResult` and `runTuned` APIs.
- Research-reference checker and all 38 documentation tests passed. Generated API
  inventory: 13019 public method entries; freshness and 15031 local-link targets
  passed. Existing compiler documentation warnings remain visible.
- Thin/source/API artifacts built and published to an isolated LOCAL repository.
  Baseline, 7.0 integration and new capability consumers compiled in a separate
  build and all passed against the same thin JAR, SHA-256
  `3bcb3ead41dcfc6af4c46fbea3b5029c0505657e76c20c8d78548466846afdba`.
  The consumer's historical "published" output means local publication here,
  not Maven Central. Public publication was not performed.
- CI is wired for the snapshot suites, independent numerical comparison and new
  consumer, but has not run remotely. No commit, push, merge or release has occurred
  for this milestone. The two pre-existing unrelated documentation edits remain
  unchanged, verified by their original hashes.

## Remaining decisions and limits

The recovery choice is resolved: a bounded-work indicator filter with detached
immutable states and a fresh fixed-graph adapter. Existing general-weight filters
receive diagnostics, not silent retries or inferred indicator eligibility. General
likelihood adaptive stopping, arbitrary dynamic/shared graph recovery, automatic
budget selection and wall-clock guarantees remain unsupported. Strict default
failure and failed-filter invalidation remain.

The completed checks do not remove arbitrary shared-graph restrictions, deterministic
continuous conditioning semantics, generic combinatorial/treewidth costs, multi-target
or matrix automatic tuning, unknown custom proposal formulas, or singular/unrepresentable
numerical regimes. These are explicit limits, not silently completed requirements.

The working version is prepared as 7.1.0. These additive bounded APIs warrant a
minor release after the milestone, remote CI and release gates are complete; see
the [release-readiness checklist](RELEASE_7_1_STATUS.md). Existing public Model/ChainResult signatures and defaults
remain intact. No change to the published 7.0.0 bytes or tag is authorized or needed.
