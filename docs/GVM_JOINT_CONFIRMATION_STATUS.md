# Fresh joint optimization confirmation results

Research provenance: [GVM-09](RESEARCH_REFERENCES.md#gvm-09).

**No promotion.** The unchanged joint optimizer substantially improves bounded
completion, but fails the frozen per-cell reliability and total-cost gates.
Independent numerical validation and all held-out score margins pass. Keep this
candidate as research evidence; no higher-dimensional public fitter, stopping
estimator, default, runtime dependency or release changes.

## Scope and frozen decision

The [protocol](GVM_JOINT_CONFIRMATION_PROTOCOL.md) and
[source-hashed configuration](../tools/gvm_joint_confirm_protocol_v1.json) were
committed and pushed as `d778fce9` before any fresh confirmation data generation.
All 80 synthetic datasets, 160 pairs and 960 repeated fits completed. Eight fresh
seeds cross d=2,3, five truth families and K=2,3. No model or failed case was omitted.
The optimizer, three-start selection rule, budgets, bounds and qualification
threshold remain exactly those of the preceding feasibility screen.

The [machine-readable result](../tools/gvm_joint_confirm_summary_v1.json) includes
every paired outcome, all 20 cells, repeated timing ranges, residual diagnostics,
seed-level costs, simultaneous bounds and audit errors. Repetitions verify timing
and reproducibility; they do not turn eight seed pairs into 24 statistical samples.

| Frozen gate | Observation | Result |
| --- | --- | --- |
| Complete inventory and independent numerical checks | 960 records; exact scientific repeat identity; no missing models or exceptions | Pass |
| At least 95% completion in every cell | Three cells below 8/8; detailed below | Fail |
| At least 20 percentage-point completion improvement | 149/160 versus 71/160, a 48.75-point gain | Pass |
| No lost baseline-complete cases | Zero | Pass |
| All paired score lower bounds at least -0.02 nats/point | Worst simultaneous lower bound approximately -0.01023 | Pass |
| Upper bound on total-cost ratio at most 1 | Upper ratio approximately 1.1735 | Fail |

The candidate completes 93.1% of cases versus 44.4% baseline. These are empirical
rates over this bounded grid, not population guarantees. The 20 score bounds and
one cost bound use the frozen familywise alpha .05 divided by 21. The cost bound
uses seed-wise log ratios of full-grid summed median elapsed costs. Passing score
noninferiority does not certify optimization completion or true component recovery.

## Total cost including unsuccessful fits

| Full inventory measure | Baseline | Joint candidate |
| --- | ---: | ---: |
| Complete cases, requiring all repetitions | 71/160 | 149/160 |
| Sum of per-case median elapsed seconds | 91.49 | 102.41 |
| Sum of per-case median process-CPU seconds | 91.47 | 102.42 |
| Actual elapsed seconds across all three repetitions | 275.28 | 308.64 |
| Actual process-CPU seconds across all three repetitions | 275.03 | 308.19 |
| Canonical recorded data-pass work | 2,293.83M | 1,410.16M |

The candidate uses about 38.5% fewer recorded data-pass units yet costs about 11.9%
more elapsed time for one typical full-grid pass. Total elapsed time is higher in
each repetition, not just in a pooled average. All failed starts, solver algebra,
constraint checks, rejected trials, qualification and training preparation are
inside the timing boundary. Work counters do not count all arithmetic and are not
a speed measure. CPU costs broadly agree with elapsed costs on this host.

The summed medians describe one typical pass over all 160 cases. The actual totals
describe all 480 fits per arm; neither is a success-only average. Whole-study
elapsed time was 917.64 seconds, additionally including the warmup, synthetic data
generation, held-out scoring, input/output and access operations. Audit execution
is separate. No heavy local experiments ran concurrently with measurement; only
lightweight progress polling occurred. The order was counterbalanced and one
BLAS/OpenMP thread was used. This is single-host Python/SciPy research timing,
not a benchmark of the released Scala library or hardware-independent performance.

The completion gain is the strongest evidence in favor of further consideration:
78 additional cases qualify with no lost baseline successes. It does not erase the
predeclared cost and reliability failures. A different completion-versus-cost
tradeoff would require an explicit new decision, not a relabeling of these gates.

## Remaining failures and retained curved cases

| Cell | Baseline complete | Candidate complete | Candidate failures |
| --- | ---: | ---: | ---: |
| d=2 curved, K=3 | 0/8 | 3/8 | 5 |
| d=3 curved, K=3 | 0/8 | 4/8 | 4 |
| d=3 seam, K=3 | 0/8 | 6/8 | 2 |

All other 17 cells complete 8/8 for the candidate, including the previously
unresolved d=3 curved K=2 cell on these fresh seeds. That does not erase its old
development failure, which remains in the [feasibility record](GVM_JOINT_STATUS.md).
Both formerly unresolved curved cells were retained without changing selection,
iteration caps or the stationarity threshold. All 80 candidate K=2 cases qualify;
69/80 K=3 cases qualify. These are descriptive subgroups, not a post-hoc restricted
promotion or an automatic component selector.

All 11 failures have status `SolverStoppedUnqualified`. Each selected start reaches
the unchanged 200-iteration SLSQP cap; physical residuals range from approximately
`3.44e-4` to `8.89e-3`, above `1e-4`. Every failed fit also contains at least one
lower-training-likelihood stationary start. The frozen rule chooses the greatest
training likelihood, not the most convenient qualified restart. Substituting those
other starts would change the fitting contract and cannot repair this confirmation.

The curved truth has one component and the seam truth has two. Their K=3 fits
therefore stress redundant-component behavior. This association is not proof
that redundancy alone causes the failures; no new causal ablation was run. The
baseline has 87 work-budget failures and two map-limit failures. Its unqualified
retained models remain in all accuracy and cost comparisons as well.

## Independent validation and reproducibility

The [audit](../tools/gvm_joint_confirm_audit.py) does not import the joint optimizer.
It checks every record's score, bounds/support, physical diagnostic, monotone trace
and work accounting, and verifies exact scientific identity across repetitions.
The separate density calculation uses high-precision inverse/determinant algebra;
physical finite differences do not reuse the transformed optimizer gradient.
Recomputed analytic stationarity remains a consistency check, not an independent
derivative oracle. Results:

- All 80 input hashes and six frozen source-map checks pass. Every one of the 960
  individual JSON records also matches the consolidated inventory.
- All 320 canonical retained models receive every-coordinate finite-difference
  checks, including unqualified models. Maximum gradient discrepancy is `1.49e-7`
  against the unchanged `2e-6` limit.
- Eight hundred 60-digit density evaluations, including generating truths, have
  maximum discrepancy `4.44e-15` against `1e-8`. Recomputed mean-score discrepancy
  is at most `8.89e-16` against `1e-9`.
- An independent high-precision Student-t tail calculation agrees within `8.37e-18`;
  separate aggregation verifies the reported confidence bounds and cost totals.
- Ten new harness controls, nine unchanged joint controls, nine whole-fit controls
  and 17 weight-study controls pass. Tests retain failures, reject bad inventories,
  check timing boundaries and prevent repetitions from inflating the seed count.

Before the final freeze, the first harness run found a syntax error. An old-seed
rehearsal then exposed an absolute-error comparison applied to an enormous
exponentiated cost bound: the independent log bound differed by only `7.1e-15`.
The audit now checks the primary log bound plus relative exponent error, with a
regression test. The repeated rehearsal passes; no optimizer, scientific gate or
fresh data was changed to achieve that. Original failure logs, source revisions
and preliminary configuration remain in the evidence archive.

The [evidence tool](../tools/gvm_joint_confirm_evidence.py) packages exact input
files and SHA-256 hashes, source revisions, commands, actual output/errors,
timestamps, software versions, machine-readable results, an all-cell plot and rerun
instructions. Every archived entry must pass read-back verification. Reruns should
use the archived source bytes, preserving line endings covered by the hashes.
No external dependency code or private application context is published.

## Recommendation

Keep the exact candidate parked as a promising but unpromoted research result.
Do not increase caps, drop difficult cells, change restart selection or promote
only the successful subgroup using these same seeds. The next decision should
address whether the desired capability is reliable fixed-K optimization or bounded
complexity selection in the presence of redundant components; those are different
contracts. Any resulting method needs a new bounded plan and independent evidence.
Gaussian-mixture cost controls, consumer workloads, larger dimensions and public
integration remain unestablished. Released stopping and defaults stay unchanged.
