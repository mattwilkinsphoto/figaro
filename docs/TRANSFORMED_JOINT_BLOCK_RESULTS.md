# TJB-01: fixed transformed joint-block result

2026-10-26. **Bounded development completed; no promotion.** The new explicit
joint-block candidate is not in published 7.4.1 and is not scheduled for a release.
Stopping, defaults, strict calibration and earlier no-promotion decisions remain
unchanged. The [frozen protocol](TRANSFORMED_JOINT_BLOCK_PROTOCOL.md) was committed
before implementation; execution inputs were committed at `ea0e87e7` before the
first utility trajectory. No matrices, budgets, thresholds or fixtures were revised
in response to results. Confirmation seeds were not consumed.

## Summary

A caller-supplied covariance can help when it describes actual dependence: both
correlated fixtures met their accuracy and total-cost utility gates. This is the
strongest favorable evidence. However, that result did not extend to the full
declared scope. Both independent fixtures failed utility, and the 8D fixture also
failed accuracy. The curved adverse fixture remained difficult. All 192 attempts
returned, but delivery is not accuracy or convergence.

| Fixture | Candidate accurate / delivered | Candidate cost-error score / best released control | Required ratio | Decision |
| --- | --- | --- | --- | --- |
| Independent mixed support, 2D | 8 / 8 | 1.578, joint-prior control | <=1.25 | Utility failed |
| Independent mixed support, 8D | 5 / 8 | 2.611, scalar control | <=1.25 | Accuracy and utility failed |
| Correlated Normal, 2D | 8 / 8 | 0.544, joint-prior control | <=0.80 | Development gates passed |
| Coupled Gamma/Beta, 2D | 8 / 8 | 0.701, joint-prior control | <=0.80 | Development gates passed |
| Curved mixed support, adverse | 1 / 8 | 2.766, joint-prior control | Descriptive only | Limitation retained |
| Separated modes, adverse | 7 / 8 | 0.157, scalar control | Descriptive only | Gain, not guaranteed mode recovery |

Ratios below one are better. This is **not a wall-clock speedup table**: the score
is the worst query's average squared tolerance-normalized error times mean total
attempt cost. It includes derived variances and construction, sampling, diagnostics
and trace serialization. Every arm had eight completed outcomes per fixture.
Accuracy means every specified query and derived variance met its frozen absolute
tolerance, not that a diagnostic certified convergence.

## Why test this, and what changed?

The earlier learned pilot combined geometry estimation and strict calibration
with sampling. It did not qualify. Here geometry was supplied analytically, free
of estimation cost, to isolate whether one joint identity/log/logit move could be
useful at all. The two-argument candidate accepts two through eight exact constant
Normal/Gamma/Beta roots in one owned fixed graph. It makes one MH decision with
the appropriate Jacobian, rather than composing independent scalar decisions.
There is no fitted covariance, defensive component, scale search or automatic
fallback. The [API review](TRANSFORMED_JOINT_BLOCK_API_REVIEW.md) records the
literature, alternatives and restrictions; it is design notation, not 7.4.1 usage.

Controls were the released default, a joint-prior proposal and uniform fixed
scalar support-aware moves. All used the same graph, queries, four chains, 4,000
discarded transitions and 8,000 retained draws per chain. Eight new seeds per
fixture were rotated across arm order. Oracle geometry is an optimistic assumption:
its useful results do not establish an economical way to estimate that geometry.

## Correctness and independent validation

- 24 candidate tests passed, covering density/Jacobian accounting, covariance,
  support and numerical failures, rollback, ownership and unsupported runners.
- 103 existing tests in eight suites passed, including scalar/block proposals,
  observations, diagnostics, calibration and stopping.
- Independent SciPy calculations checked 18 actual graph ratios and four emitted
  matrix factors. Five audit controls reject missing/duplicate outputs and wrong
  priors, Jacobians or factors.
- Analytic and independently integrated reference moments were saved before
  utility execution. The first quadrature request did not meet its fixed error
  bound. Tighter integration requests passed without loosening that bound or any
  sampling tolerance; both actual attempts are retained. Five reference controls
  and six analysis controls passed.
- Independent NumPy/SciPy trace recomputation agreed with all **3,072** diagnostic
  summaries within the unchanged `rtol=3e-8`, `atol=2e-9` audit bounds. No audit or
  execution failure occurred. Missing diagnostic values are not interpreted as
  convergence. All input hashes remained unchanged during execution.

These are local executed results, not independent peer review or a theorem about
arbitrary models. Development used stock sbt 2.0.11, Scala 3.9.0, Java 17.0.4,
Python 3.14.6, NumPy 2.5.1 and SciPy 1.18.1. The process-tree monitor was psutil
7.2.2. Total phase wall time was 104.08 seconds; measured attempts totaled 94.57
seconds; the independent audit took 26.14 seconds. Startup/smoke/capture/cleanup
are included in phase wall time, not silently charged as zero. The phase used
1.65 GiB peak summed process RSS and 210.11 MiB decoded output, below every cap.
RSS is not a guarantee of unique physical-memory accounting. Callback evaluations
totaled 12,392,584. Timing is one machine/session, not a portable benchmark claim.

## Failures and uncertainty

The 8D candidate missed three raw-positive variance tolerances (zero-based seed
indices 0 and 3 for x0, and 5 for x6). Errors were about 1.06, 1.32 and 1.38 times
their tolerances. Dropping variances would erase a predeclared requirement, so
these remain failures. The curved case failed multiple moment/variance queries
on seven seeds. In the separated case, one seed missed both positive-coordinate
probability tolerances: estimated probabilities were about 0.548 and 0.545 versus
0.5. Better aggregate score is not a missing-mode guarantee.

With eight seeds, comparative utility has considerable uncertainty. Paired
leave-one-seed-out ratio ranges were 1.13-2.48 (product2), 1.74-3.01 (product8),
0.37-0.70 (correlated Normal) and 0.43-0.88 (coupled support). These are sensitivity
ranges, **not confidence intervals** or alternative gates. In particular, the
coupled-support sensitivity range crosses its 0.80 threshold. No fresh-seed
confirmation was authorized after the ordinary development failures.

## Decision and next boundary

The firm exit gate stops this milestone before confirmation, packaged-candidate
acceptance or promotion. The candidate remains branch-only; do not merge its
runtime into main or treat `7.5.0-SNAPSHOT` as an announced release. The published
7.4.1 diagnostic repair is independently complete and unaffected.

Use the released methods within their documented contracts. Further work should
start from a demonstrated dependent mixed-support workload and a separately
approved, prospectively frozen relevance/utility plan. The current data justify
interest in that narrower question, not removing the failed product controls,
changing the current gates, adding another component or retrying more seeds.

## Reproducibility

[Machine-readable outcomes](reports/transformed-joint-block-v1.json) retain all
192 outcomes, all query errors, diagnostics/warnings, chain metadata, costs,
comparisons and failed gates. The [test harness](../Figaro/src/test/scala/com/cra/figaro/test/modernization/TransformedJointBlockStudy.scala),
[independent references](../tools/transformed_joint_block_reference.py),
[bounded runner](../tools/transformed_joint_block_runner.py),
[independent analyzer](../tools/transformed_joint_block_analysis.py) and protocol
are versioned. The raw compressed trace log has SHA-256
`9badeaea1e15041b672a9a0e9fbf6780c9699dfcd8bb0c28432f694f27d03dc0`;
the pre-sampling reference has SHA-256
`6c707169530d94e7fb0a466b1fd57928a56d42f96e285cd7f5ff5b956da1e4ba`.
The retained evidence archive includes exact executed source bytes, raw traces,
receipts, failed attempts, version records and rerun instructions. Large traces
are not part of the Maven library or this repository's source distribution.

[Research provenance](RESEARCH_REFERENCES.md#inf-22).
