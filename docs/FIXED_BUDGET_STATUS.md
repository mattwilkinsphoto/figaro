# Fixed-budget integration status

[Research provenance](RESEARCH_REFERENCES.md#inf-15).
[API guide](FIXED_BUDGET_FILTER.md). [Frozen protocol](FIXED_BUDGET_PROTOCOL.md).

Development target 7.3.0-SNAPSHOT, not a published release. Released stopping
estimator and previous benchmark/protocol sources remain unchanged.

**Current follow-up:** the [decision-focused repair](STRATIFIED_INTEGRATION.md) is
integrated on the development branch, with local gates passed and remote CI pending. The records
below describe the preserved pre-repair implementation and its original refusals;
they are not new measurements of the repaired selector. Merge/release remain held.

- Stage 1: six accounting/API tests passed, including exhaustive two-state outcomes.
- Stage 2: ten additional numerical/lifecycle tests passed (16 combined).
- Stage 3: all frozen integration screens passed: 300 unnormalized statistical
  checks, independent Fraction oracle, complete outcome accounting and identical
  scientific results/statuses across three fresh JVMs. No threshold/default promotion.
- All 79 focused Scala tests and the unchanged 450-check continuous regression
  passed (7,680 trajectories, 153,600 step records).
- The isolated Maven-layout candidate consumer passed all five checks, including
  the new fixed-budget API; its loaded JAR SHA-256 matched the packaged artifact.
  It did not resolve Figaro project classes or Ivy local as the library.
- Four analysis-method tests, 41 documentation tests, reference checks and 15,395
  local link targets passed. Source/API documentation artifacts were generated.
- Initial remote CI identified a stale generated API handbook. The same freshness
  failure was reproduced locally, the three generated files were regenerated, and
  the full remote documentation-step checks then passed locally. No runtime code,
  frozen protocol or acceptance threshold changed. The failed log is retained.
  Fresh remote CI is pending at this correction; consult the branch run for its state.

No merge or publication is included in this milestone. The compatible new API is
a minor-release candidate when accepted, not a reason to overwrite 7.2.0. The
development build is 7.3.0-SNAPSHOT; publication requires separate approval.

Follow-up: corrected branch CI run 39799469278 passed all four jobs, including
reproducible builds and packaged consumers. That establishes the recorded build
gates, not stratified usability. The subsequent [numerical investigation](STRATIFIED_NUMERICAL_INVESTIGATION.md)
reproduced every original stratified row and attributed the refusals. Merge and
release remain explicitly on hold pending a defensible repair and validation.
The subsequent [decision-focused prototype](STRATIFIED_DECISION_STATUS.md) passes
its frozen independent and fresh-seed checks. Subsequent production integration is
tracked separately in the current follow-up above; prototype results alone do not
satisfy integration/consumer/CI gates.

## Completion is not universal stratified applicability

Each JVM ran 12,288 trajectories (eight explicit configurations, six fixtures,
256 independent seeds). Repeated JVMs replay those seeds; they do not triple the
independent sample size. All 6,144 log-tree trajectories completed. Stratified
completed 676 and explicitly refused 5,468. No unexpected errors or retries.

| Stratified fixture | Always completed | ESS .25 completed | ESS .50 completed | ESS .75 completed |
| --- | ---: | ---: | ---: | ---: |
| Growth | 0/256 | 0/256 | 0/256 | 0/256 |
| Growth sharp | 0/256 | 0/256 | 0/256 | 0/256 |
| Persistent modes | 48/256 | 0/256 | 0/256 | 46/256 |
| Late disambiguation | 0/256 | 0/256 | 0/256 | 0/256 |
| Gaussian | 92/256 | 0/256 | 80/256 | 92/256 |
| Switching | 256/256 | 0/256 | 53/256 | 9/256 |

These are detected positive-interval representation failures, not zero likelihood
or evidence that the statistical resampling method is invalid. The production
guard is stricter than the research CDF; earlier successful research outcomes do
not establish production support. No conditional-success performance ranking is
reported. Only complete groups receive unconditional error/evidence summaries.
Consequently, the intended broad stratified schedule ablation remains limited:
none of the ESS-triggered stratified groups completed every held-out trajectory.

The bounded implementation contracts pass, but stratified resampling is **not
recommended as a general continuous-model replacement**. Log-tree scheduling is
the usable option across these tested fixtures, not a universal accuracy guarantee.
Extending stratified numerical support needs a separate design/protocol review;
do not weaken the refusal check or infer that .75 is optimal.
