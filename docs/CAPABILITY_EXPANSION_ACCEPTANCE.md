# Capability expansion: complete cost evidence and acceptance

## Event-weighted mixtures

Three fresh JVMs retain 1080 rows: 30 predeclared seeds (95000..95029), three
standard-normal events and four methods. These repeat the same statistical trials,
not 90 independent datasets. Each completed run has 12000 total score calls,
including all discarded pilot rounds. Pilot batch=1000, cap=8000, ridge=.25,
inflation=1.5, defensive mass=.1, explicit two-component EM cap=5000000 component
calls. Single and weighted fits share outer settings; all refusals remain in data.
Three untimed warmup passes cover all methods and execution order rotates.

| Event | Prior relative RMSE | Single fit | Weighted mixture | Supplied control |
| --- | --- | --- | --- | --- |
| X>=0 | .79% | .61% | 8/30 refused; .73% on 22 completions | .86% |
| X>=3 | 20.32% | 1.53% | 4/30 refused; 1.50% on 26 completions | 1.79% |
| abs(X)>=4 | 119.54% | 4.71% | 1.52%, all 30 complete | 1.83% |

Median full-call milliseconds across the three JVMs (successful subset only where
refused): common 3.25/5.23/11.15/4.97; one-tail 3.20/5.15/9.10/4.74;
two-tail 3.15/4.88/7.30/6.02, in table order. Timing includes fitting, construction,
production, scoring and diagnostics, excluding JVM/sbt startup and printing.
The supplied control uses known event locations and is not learned.

The two-tail error reduction is about 3.09x while runtime grows about 1.50x versus
the single fit. This is fixed-total-score evidence, not a proven matched-error
runtime multiplier. Trivial score functions understate costs in expensive models.
Unnecessary components make the weighted fitter less robust on the simpler cases.
The earlier single-Gaussian study used 500-point batches and different seeds;
its 22/30 refusal rate is not reused as a paired baseline for this study.

Raw evidence: [A](weighted-rare-event-a.csv), [B](weighted-rare-event-b.csv),
[C](weighted-rare-event-c.csv). `componentCalls` is actual weighted-EM density work;
pilot and production score calls are separate. Refusals have no probability estimate,
not a fabricated zero. Predeclared controls and all unfavorable rows are retained.

## Restricted graph integration

Three JVMs retain 720 rows: 20 paired seeds (98000..98019), three conjugate/deep
Gaussian graph fixtures, 6000 draws and four methods. Setup includes the model and
supplied defensive posterior proposal; no fitting is claimed or omitted. Sixteen
fixed logical streams and rotated method order permit direct owned/static replay.
Two untimed warmup passes cover all methods. Labels: owned proposal, static prior,
static proposal with one worker, and static proposal with four workers.

| Fixture | Owned / static-prior / static-proposal-1 / static-proposal-4 median ms | Prior RMSE | Proposal RMSE (all three implementations) |
| --- | --- | --- | --- |
| Gaussian | 15.31 / 1.77 / 4.50 / 4.31 | .00742 | .00916 |
| Narrow likelihood | 14.03 / 1.68 / 4.27 / 3.65 | .00239 | .000698 |
| 32 multiply nodes | 34.83 / 2.16 / 4.64 / 3.92 | .00893 | .01056 |

The static proposal bridge improves median total runtime over the owned proposal
bridge by about 3.4x, 3.3x and 7.5x with one worker. Four workers provide modest
additional gains, not fourfold scaling. Prior sampling wins on the simple/deep
controls; the narrow likelihood benefits statistically from the proposal. Higher
raw-weight ESS does not by itself imply a better query mean estimate.
Numerical query/ESS results agree across owned/static proposal methods on these
fixtures; different seed consumption is expected for the static prior control.

Raw evidence: [A](static-proposal-a.csv), [B](static-proposal-b.csv),
[C](static-proposal-c.csv). Definitions, proposal creation, likelihood evaluation,
diagnostics and worker/graph cleanup are timed. No object-allocation or peak-memory
claim is inferred from these timing rows.

## Reproduction and release gates

Environment: Windows, JDK 17.0.4, Scala 3.9.0, sbt 2.0.8, LXM with SeededV1
allocation. Timing is machine-dependent. Run each main in three fresh JVMs:

```sh
sbt "figaro / Test / runMain com.cra.figaro.test.modernization.WeightedRareEventStudy 30"
sbt "figaro / Test / runMain com.cra.figaro.test.modernization.StaticProposalStudy 20"
python -B tools/test_capability_expansion_evidence.py
python -B tools/test_extended_construction_reference.py
```

The second Python test requires research-only mpmath 1.3.0; runtime dependencies
are unchanged. Release gates require the complete modernization suite, all four
artifact types, independent published consumer, generated API-reference freshness,
documentation tests and remote CI with two cold reproducibility builds.
Local acceptance on 2026-09-09 compiled 337 library and 244 test sources from a
fresh action cache and passed all 539 modernization tests. All four archives passed
content/legal checks. The independent consumer compiled and exercised the published
binary, verified by SHA-256 `f272a32ff57603df4933154981f47ccbd1da31c65872c0a9fdf0fbdf7be271a2`.
API-reference freshness covers 12547 public method entries; 14051 local link targets,
18 documentation-tool tests, four independent high-precision identities and four
complete-evidence checks pass. The six existing compiler pattern warnings and four
existing Scaladoc warnings remain; no new deprecation warning is introduced.
Remote CI must pass on the exact source commit before main promotion.

Related: [weighted mixtures](WEIGHTED_RARE_EVENT_MIXTURES.md),
[static proposals](STATIC_GRAPH_PROPOSALS.md), [constructions](EXTENDED_CONSTRUCTIONS.md).
