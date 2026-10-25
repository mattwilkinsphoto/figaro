# Mixture weight and restart allocation results

Research provenance: [GVM-08](RESEARCH_REFERENCES.md#gvm-08).

**The three-stage study is complete; no promotion.** Conditional weight polishing
is numerically sound in the tested envelope, but spending work on it after every
map reduces complete multicomponent fits from 70/160 to 62/160. Work-balanced
restarts do not increase completion and retain fewer stationary solutions when
interrupted. All numerical and score-noninferiority checks pass; the reliability
and completion-improvement gates fail. Stopping, defaults, public fitting, runtime
dependencies and the release version remain unchanged. The shared pilot stays parked.

The [protocol](GVM_WEIGHT_PROTOCOL.md) and
[source/seed configuration](../tools/gvm_weight_protocol_v1.json) were committed
and pushed as `1bf01ba8` before confirmation. The
[independent summary](../tools/gvm_weight_summary_v1.json) contains all primary
cells and score comparisons. [Descriptive breakdowns](../tools/gvm_weight_diagnostics_v1.json)
do not replace gates or relabel failures. The earlier
[whole-fit study](GVM_WHOLEFIT_STATUS.md) and its no-promotion result are unchanged.

## What completed

Stage 1 used 40 old fixed-shape models as development diagnostics. There were
31 independently checked conditional qualifications and 13 cases meeting the
predeclared improvement/residual relevance screen. All 40 retained valid support.
The nine uncertified weight solves remain uncertified. Several improvements were
very small: passing this screen established relevance, not a useful speedup.

Stage 2 added a test-only SLSQP adapter with separate convex-gap qualification and
a resumable scheduler. Fourteen numerical, scheduler and audit control groups
passed before freeze, including equivalence to the unchanged baseline and equal
per-start results under interleaving when the budget is sufficient. All 40
development fits passed their independent numerical audit. They did not all
converge: numerical correctness and fit completion are separate outcomes.

Stage 3 completed all 1,040 confirmation fits: 80 fresh datasets, four GVM arms
at K=1,2,3, and 80 wrapped-GMM K=4 descriptive controls. Each primary fit kept the
same 20M shared-work budget, three starts, 200 maps per start and 120-second cap.
No source, seed, parameter bound or gate changed after freeze. All fits retained
finite models; no failed cases were removed. Total confirmation elapsed time was
about 1,046 seconds, with another 50 seconds for the 40-fit development grid.

## Frozen gate results

| Gate | Observed result | Decision |
| --- | --- | --- |
| Independent numerical, inventory and work-accounting checks | All pass | Pass |
| At least 95% completed stationary fits in every candidate cell | 14 of 30 cells meet the threshold | Fail |
| At least 20 percentage points greater multicomponent completion | 70/160 baseline versus 62/160 combined: **5 points worse** | Fail |
| All 30 paired test-score noninferiority bounds above -0.02 nats/point | 30/30 pass | Pass |

The score procedure uses eight training seeds per comparison, paired held-out
scores and the predeclared one-sided Bonferroni Student-t adjustment. It supports
the stated finite-budget noninferiority margin, not equality or superiority.
The 95% empirical cell threshold requires eight of eight here; it is not a
population reliability confidence bound. Correct numerics cannot waive either
failed utility gate.

## Separating the two changes

| Arm | K=2 complete / 80 | K=3 complete / 80 | Multicomponent complete / 160 | Retained stationary / 160 |
| --- | ---: | ---: | ---: | ---: |
| Unchanged accelerated sequential baseline | 54 | 16 | 70 | 100 |
| Weight polishing only | 48 | 14 | 62 | 91 |
| Work-balanced starts only | 54 | 16 | 70 | 78 |
| Weight polishing plus balancing | 48 | 14 | 62 | 70 |

All four arms complete all 80 K=1 fits. The baseline and balancing-only arms each
have 89 multicomponent work-budget failures and one map-limit outcome. The two
polishing arms each have 98 work-budget failures. A stationary retained model
inside a budget-exhausted search is not counted as completed.

Weight polishing alone retains slightly more stationary K=2 models, 69 versus 67,
but fewer K=3 models, 22 versus 33, and fewer completed fits overall. This is the
strongest favorable counterevidence, not justification to promote that ablation.
The designated candidate was the combined arm; none may be selected post hoc and
called the confirmed winner.

Balancing changes which partial trajectories receive work. It cannot, by ordering
alone, reduce the aggregate logical work needed to finish the same three independent
trajectories. With the unchanged all-start search contract, it principally changes
the retained model at budget exhaustion. The observed identical completion counts
and poorer retained-stationarity counts are consistent with that structural limit.
An early-exit or restart-elimination policy would be a different contract, not a
repair to silently insert into this study.

## Why solving the weight block was insufficient

The combined arm spends about 39.7% of its recorded logical work on weight-matrix
operations and qualification. It reduces phase work but increases total recorded
work across its primary fits to about 2.59 billion units versus 2.33 billion for
the baseline. Logical row visits are not FLOPs; these totals do not establish a
hardware-independent runtime ratio.

The weight block dominates 41 of the baseline's 47 nonstationary K=3 retained
models. In the combined arm it dominates only six of 64. The remaining dominant
blocks are angular offset (33), phase coefficients (12), covariance (8) and means
(5). Thus the intervention addresses its targeted residual but does not solve the
coupled full-fit problem. These descriptive counts are not evidence that tuning
one more block in isolation will succeed.

The combined arm records 10,237 accepted polishes, 4,763 uncertified polishes and
153 low-support refusals. Four additional conditional qualifications are interrupted
by the global budget before acceptance. All refusal/interruption work remains
charged. Low-support optima fall back to the ordinary map; no component is removed
and no support threshold is relaxed. The weights-only arm records 9,956 accepted,
5,477 uncertified and 46 low-support polishes, with two qualifications interrupted
before acceptance.

Median K=2 recorded work increases from about 11.43M to 14.48M units with polishing.
K=3 medians stay at approximately the work cap. Timings are advisory, not a
successful-fit-only speed claim: brief monitoring, documentation and control
commands ran during confirmation. The unmodified baseline does not record work at
its first stationary checkpoint; that time-to-qualification comparison is
**unavailable**, not reconstructed from final work. Candidate checkpoint records
include unfinished cases explicitly.

Wrapped-GMM controls meet their older likelihood-stabilization criterion in 70/80
cases and reach iteration limits in ten. They do not use the same convergence or
work contract, so this is not a cost-matched GVM-versus-GMM superiority result.

## Independent validation and reproducibility

Checks actually executed include:

- All 80 input hashes, frozen source hashes, complete unique inventory, every
  retained score/trace, parameter/support bound and reported work ledger. Balanced
  scheduling is independently replayed. Missing/duplicate cases, omitted work,
  wrong allocation and corrupted input/protocol hashes are negative controls.
- 2,080 independent 60-digit density evaluations across every confirmation model:
  maximum absolute log-density discrepancy below `7.2e-15` (limit `1e-8`).
- Full physical-coordinate objective differences for 120 models, covering the first
  seed in every primary cell and arm: maximum discrepancy below `7.8e-8` (limit
  `2e-6`). Independent full-data weight-gradient error is below `4.5e-16`, and
  conditional-gap error below `3.1e-16` on that audit subset.
- The 14 frozen new control groups, three supplementary reporting/corruption
  controls, and unchanged nine whole-fit, five gradient and eight density/reference
  control groups pass. Finite tested-envelope agreement is not an interval proof
  or a guarantee for arbitrary dimensions, extreme parameters or every intermediate
  optimizer state.

The first plot command failed on a command-line string/path conversion. Only the
postprocessing wrapper was repaired; its original log and exact source revision
are preserved. The regenerated all-attempt figure was visually checked. No frozen
fitter, acceptance criterion or numerical result changed because of that failure.

The [archive builder](../tools/gvm_weight_archive.py) includes exact synthetic
inputs, SHA-256 hashes, all source revisions, versions, actual execution logs,
timestamps/errors, machine-readable results, the comparison plot and rerun
instructions. Installed dependency code is not redistributed. The archive must
pass a read-back check of every manifest entry before handoff.

## Decision

Keep the candidate test-only. Do not promote, merge, release, change stopping or
defaults, enlarge caps or relax qualifications. This study does not justify another
allocation-only variant under the same full-search completion contract, nor a
blanket larger optimizer budget. Any next experiment needs a distinct bounded
hypothesis about joint optimization or model complexity, fresh confirmation and
independent validation. Changing the acceptable finite-budget search contract is
a separate design decision, not a numerical workaround.
