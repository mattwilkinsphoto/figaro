# End-to-end graph proposal cost assessment

## Outcome

Proposal quality matters more than choosing a universally "best" sampler. The
concentrated Gaussian case strongly favors the fitted single Gaussian; the easy
mixture does not justify replacing legacy importance. Joint blocks improve
effective sampling, but callback construction cost can erase their runtime gain.
Rare-event queries benefit from event-focused proposals even with lower weight ESS.

## Protocol and complete evidence

The [initial cost grid](graph-cost-results.csv) contains 1,800 rows: three models,
five workflows, two production budgets (2,000/8,000), 20 declared seeds, three fresh
JVMs. Each JVM warms every workflow before measurement; workflow order rotates by
seed. Repeated JVMs are timing replicates of the **same 20 seeds**, not 60 independent
datasets. Graph/MH traversal can vary between constructions; replay is not promised
across different graph traversal orderings.

Models are concentrated Normal evidence (posterior mean 3/1.01), a hierarchical
Normal model (mean 1/1.02), and separated mixture evidence (positive-mode probability
.24/.38). They are controlled low-dimensional workloads, not a general application
benchmark. Legacy importance and bridge-prior use the same sampling law; observed
evidence and MH log constraints represent the same likelihood. No impossible
evidence/retry workloads are used here.

The five methods are legacy `Importance`, prior draws through the owned bridge,
single-Gaussian fitting, two-component fitting, and graph multi-chain MH. Fitting
uses four discarded MH chains with 250 warm-up and 500 retained pilot transitions
each: **3,000 pilot transitions** total. Production MH includes 1,000 warm-up
transitions total. MH initialization attempts and their runtime are included in
elapsed time but are not counted as transitions. Pilot R-hat is retained, not used
to hide failed/poor runs. Fits are frozen with 10% prior defense. Fit failures
produce no production estimate, remain in the table and prevent unconditional RMSE
reporting for that method/case/budget.

Timing starts before law/model construction and includes pilot, fitting, production,
diagnostics and cleanup. JVM startup and declared warm-ups are excluded. Two MH
workers are used. Work counters are not interchangeable across algorithms; no
single density-call count is presented as equivalent CPU work.

## Held-out calibrated-total-cost comparison

The [held-out grid](graph-matched-cost-results.csv) adds 1,800 rows using 20 **new**
seeds and three fresh JVMs. Counts are fixed by a two-point linear cost model from
the initial grid, targeting 80/160 ms per complete workflow and rounded to four
draws. They are not selected using held-out accuracy. These are approximate cost
matches, not hard deadlines; real runtimes are reported and no slow runs excluded.

Selected 80-ms-target results, averaged across the recorded runs:

| Model / method | Actual mean ms | RMSE | Refusals / 60 |
| --- | ---: | ---: | ---: |
| Concentrated / legacy | 84.0 | .008120 | 0 |
| Concentrated / bridge prior | 73.3 | .009818 | 0 |
| Concentrated / single fit | 74.2 | .000556 | 0 |
| Concentrated / mixture fit | 73.2 | Not reported | 8 |
| Concentrated / MH | 90.9 | .019042 | 0 |
| Hierarchical / legacy | 77.5 | .001546 | 0 |
| Hierarchical / single fit | 72.6 | .001166 | 0 |
| Hierarchical / mixture fit | 73.9 | Not reported | 24 |
| Mixture / legacy | 78.4 | .001894 | 0 |
| Mixture / single fit | 76.5 | .004791 | 0 |
| Mixture / mixture fit | 75.4 | .002024 | 0 |

The concentrated single fit reduces RMSE about 14.6x relative to legacy at roughly
comparable total cost. This is an **accuracy ratio on this fixture**, not a measured
14.6x wall-clock speedup or a universal statement. At the 160-ms target, both
mixture fit and legacy do well on the already-easy mixture; their RMSEs are .001156
and .001227 respectively. Mixture fitting is not automatically useful for unimodal
targets and refused a substantial fraction of hierarchical pilots. No retries,
hidden fallback, convergence-threshold relaxation or production adaptation is added.

## Joint-block/query evidence

The separate [900-row controls](joint-proposal-results.csv) compare prior,
root-focused and joint/event-focused proposals at 4,000 draws and 100 declared
seeds. Proposals are supplied analytically here, not learned, so no pilot cost
is charged. The reported one-JVM timings are descriptive, not an accepted portable
speedup benchmark.

| Query | Prior RMSE | Root-focused RMSE | Joint/event-focused RMSE |
| --- | ---: | ---: | ---: |
| Hierarchical mean | .006726 | .003294 | .002128 |
| Nonlinear sign probability | .027881 | .011251 | .010405 |
| Normal event X>4 | .0001091 | .000003864 | .000002033 |

Joint conditional proposals cost about 30 ms versus 10–11 ms for root-focused
proposals here. The extra density construction does **not** warrant enabling larger
blocks indiscriminately. Prefer cached full-covariance kernels when applicable.
In contrast, the event-focused method costs about 5.16 ms versus prior's 3.46 ms
and meets a 20%-relative-error target in 100/100 runs versus 0/100 for prior. Prior
returns a zero event estimate in 84 runs despite weight ESS essentially 4,000;
event-focused median weight ESS is only 441. Query accuracy and global weight ESS
are different questions. This does not calibrate rare-event precision stopping.

## Reproduce and interpret

```sh
sbt "figaro / Test / runMain com.cra.figaro.test.modernization.GraphCostStudy full 1"
sbt "figaro / Test / runMain com.cra.figaro.test.modernization.GraphCostStudy matched 1"
# Repeat each in fresh JVMs with final argument 2 and 3; retain all output.
python -B tools/summarize_graph_cost.py docs/graph-cost-results.csv
python -B tools/summarize_graph_cost.py docs/graph-matched-cost-results.csv --matched
sbt "figaro / Test / runMain com.cra.figaro.test.modernization.JointProposalStudy full"
python -B tools/summarize_joint_proposals.py docs/joint-proposal-results.csv
```

`GraphCostStudy.main(args)` accepts `smoke|full|matched` and a JVM label 1–3; it
prints `GC` CSV records. `JointProposalStudy.main(args)` accepts `smoke|full` and
prints `JP` records. Both return Unit and fail on invalid arguments/runtime errors.
The Python tools accept raw logs or exported CSV, validate complete grids and
print summaries; `--export PATH` writes CSV and Windows requires `--acl-script`.
The graph-cost tool accepts several logs and `--jvm N` for one complete JVM grid.
MSE times elapsed seconds is a descriptive efficiency indicator, not a convergence
rate proof. Stored evidence describes Java 17.0.4/LXM on the measurement host;
hardware/scheduler/GC/graph ordering affect timings.

## Decision and next boundaries

Keep defaults unchanged. Offer frozen single/mixture/conditional proposals as
explicit tools selected for the model and query. A fitted result, good weight ESS,
or successful example is not evidence of universal posterior coverage. See
[joint proposal usage](JOINT_PROPOSALS.md), [mixture fitting](MIXTURE_PROPOSALS.md),
[calibration findings](IMPORTANCE_CALIBRATION.md), and [the staged plan](INFERENCE_NEXT_STAGES.md).
Version/package/CI validation is recorded separately in release notes; research
timings do not stand in for those integration gates.

## Local modern.14 integration gate

The clean build compiled all 320 library sources and passed 445 modernization
tests across 45 suites. The independent published-JAR consumer exercised the new
conditional proposal, t kernel/element, mixture metrics and partition MI, alongside
the existing acceptance checks. All four archives passed runtime/legal/Java-17
checks; 81 evidence-tool tests, seven artifact tests, 18 documentation-tool tests,
two high-precision t reference tests, 12,080 generated method entries and local
documentation links passed. Four pre-existing Scaladoc warnings remain; none was
added by this milestone.

Clean local thin-JAR SHA-256:
`2bced58a9ea50f788726ce79b44c85dc939f3f692c3ee08110c35963e11f1451`.
This identifies the local Java 17.0.4 build, not assumed cross-JDK byte identity.
Remote CI independently verifies compilation, tests, documentation, two cold builds
and consumer publication before main integration; local success alone is not a
claim that remote CI passed.
