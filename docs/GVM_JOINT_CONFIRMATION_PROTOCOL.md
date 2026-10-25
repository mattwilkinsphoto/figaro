# Fresh joint optimization confirmation protocol

Research provenance: [GVM-09](RESEARCH_REFERENCES.md#gvm-09).

This freezes confirmation of the exact candidate from the
[joint feasibility screen](GVM_JOINT_STATUS.md), not another optimizer revision.
The screen improved completion but did not reduce total elapsed fit time. Both
unresolved d=3 curved cases, K=2 and K=3, remain mandatory. No public fitter,
stopping estimator, default, dependency, release or prior result changes.

## Frozen scope

The [configuration](../tools/gvm_joint_confirm_protocol_v1.json) records source
hashes and versions before fresh data generation; commit and push that freeze first.
Use seeds 1250001 through 1250008, disjoint from previous frozen GVM protocols.
Cross d=2,3 with curved, seam, three-separated, three-overlap and three-wrapped
synthetic truths and K=2,3: 80 datasets, 160 paired cases. Each dataset has 2,000
training, 2,000 unused validation and 20,000 held-out test points. Reuse the original
generator and independent stream scheme. Training alone determines whitening.
No held-out data selects starts, K, thresholds or a candidate revision.

Compare the unchanged accelerated baseline against the unchanged ten-map prefix
plus joint SLSQP candidate. Preserve three sequential starts, greatest training
likelihood selection, support/bounds, 20M work units, 120 seconds, 200 baseline
maps, 200 joint iterations, 2,000 joint calls and physical stationarity `1e-4`.
A lower-likelihood stationary start does not replace an unqualified selected fit.
Preserve all statuses, missing models, exceptions and budgets without retries.

## Total cost measurement

Run three complete repetitions per arm/case, 960 measured fits. Run one excluded
warmup pair using the old development seed. Repetition-major scheduling separates
repetitions in time; alternate arm order by case and reverse it on each repetition.
Each repetition has equal numbers of baseline-first and joint-first pairs. Use one
BLAS/OpenMP thread and avoid concurrent heavy experiments. No CPU affinity or
machine isolation is assumed. Stop the entire study at three hours and label any
incomplete inventory as incomplete, never a successful subset.

Outer elapsed and process-CPU clocks begin before training whitening and end after
the complete fitter returns (or throws). This includes initialization, every start,
the prefix, solver algebra, trial rejections, constraint work, qualification and
return assembly. The logical work ledger is additional evidence, not a proxy for
time. Input generation/read, output/access operations and held-out scoring are
excluded from fit cost; full study elapsed time is also reported. Warmup and audit
costs are separate. No failed fit is removed from cost totals.

Report the actual sum across all three repetitions, and the sum of per-case median
times as the cost of one typical pass over the full inventory. These are different
quantities. Include CPU, ranges and individual observations. Median fit time alone
cannot establish lower total cost. Repetitions do not create extra independent
statistical samples: the effective sample size remains eight seeds per cell.

These choices adapt warmup, interleaving and repetition practices from the official
[Google Benchmark guide](https://google.github.io/benchmark/user_guide.html), not
its C++ implementation. [Python clock documentation](https://docs.python.org/3.14/library/time.html)
defines the elapsed and process-CPU measurements. This is a bounded single-host
comparison, not a hardware-independent speedup claim or a benchmark of Scala code.

## Gates fixed before execution

All gates are conjunctive, and passing permits only a separate promotion review.

1. Complete inventory and independent numerical checks pass. Every timing
   repetition must reproduce the same scientific result, excluding only recorded
   seconds. Any disagreement, exception or missing retained model prevents passing.
2. Candidate completion is at least 95% in every cell (8/8 at this sample size),
   improves overall completion by at least 20 percentage points, and loses zero
   baseline-complete cases. A case counts complete only if all repetitions do.
   This is an empirical gate, not proof of 95% population reliability.
3. Every cell's paired mean held-out score is noninferior by 0.02 nats/point.
   Use one-sided Student-t lower bounds across the eight seed differences.
4. Total cost does not increase: for each seed sum the per-case median elapsed
   costs over all 20 cells, then take the candidate/baseline log ratio. Its
   one-sided Student-t upper bound across eight seeds must be at most zero.
   This asks for evidence of nonincrease, not merely a favorable point estimate.

Use familywise alpha .05 with Bonferroni division by 21 (20 score bounds and one
cost bound). Compared with earlier studies this is stricter, not a relaxed score
criterion. Student-t inference assumes sufficiently well-behaved independent seed
differences; eight seeds and host timing noise limit extrapolation. Publish raw
paired values, including hard tails, rather than imply that the interval proves
arbitrary-model reliability. CPU and work are descriptive, not alternate gates
chosen after observing elapsed cost. No success-only comparison or post-hoc gate.

## Independent validation and reproducibility

Audit all 960 records and ledger/bounds/score/status consistency. Check exact
scientific repeat identity, then audit all 320 canonical models with independent
60-digit inverse/determinant densities at two fixed held-out points and physical
finite differences of every coordinate. Limits remain `1e-8` for density,
`2e-6` for gradient and `1e-9` for mean score. Check generator truth densities
independently, input/source hashes, training-only whitening, monotone traces and
full inventory. Recomputed analytic stationarity is a consistency check, not an
independent derivative oracle. Verify statistical critical values separately by
high-precision Student-t integration and recompute reporting aggregates independently.

Control tests must reject missing/duplicate records, omitted failed costs, repeat
changes, invalid timing, false stationarity and missing model/score cases. Prior
optimizer and work-accounting controls remain unchanged. Preserve actual output,
errors, commands, versions, timestamps and original data in an exclusive-write
evidence archive; read back and verify every SHA-256 entry before handoff.

Gaussian-mixture cost comparisons, consumer workloads, larger dimensions and
production integration remain separate work; this confirmation makes no superiority
claim over Gaussian mixtures. If a gate fails, retain the result and park the exact
candidate. Any new method or budget requires a new protocol and fresh evidence.
