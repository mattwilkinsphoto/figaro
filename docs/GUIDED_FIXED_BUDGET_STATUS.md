# Guided fixed-budget milestone status

[Research provenance](RESEARCH_REFERENCES.md#inf-16).
[Frozen protocol](GUIDED_FIXED_BUDGET_PROTOCOL.md).

Research baseline: verified published Figaro 7.3.0. Final review and main integration
completed at `a31fca1a`, with all five main CI jobs passing. The extension is now
published and verified in **7.4.0**; the [release record](RELEASE_7_4_STATUS.md)
records final packaging, Central byte checks and all six consumers, separately
from the retained advisory timing failure. Released stopping estimator and defaults unchanged.

## Requirements and acceptance checklist

- [x] Stage 1: primary literature, implementation comparison and weight accounting.
- [x] Freeze protocol before implementation/benchmark outcomes.
- [x] Stage 2: opt-in guided/auxiliary API, compatible report shapes, documentation.
- [x] Deterministic accounting, Gaussian, support, lifecycle and replay tests: 42 focused Scala tests pass.
- [x] Stage 3: independent finite oracle and fresh-seed comparative validation.
- [x] Three-JVM replay, all-outcome correctness and utility decision: 720 screens pass, no incomplete groups.
- [x] Released-filter regressions, documentation checks, packaged consumer.
- [x] Reproducibility archive and final local audit: 707 entries reopened and hash-verified.
- [x] Remote integration CI and final promotion review at `0275d6a1`.
- [x] Main integration after passing closeout CI; post-merge main CI also passed.
- [x] Separate final-version release preparation, publication approval and published-consumer verification.

## Independent confirmation results

Protocol frozen in `e1ce7509` before implementation/outcomes. Seeds 70001..70256,
six fixtures, ten configurations: **15,360 trajectories per JVM**, repeated in
three serial fresh JVMs. All 46,080 executions completed, with no refused/error
rows. Scientific outcomes replay exactly; timings are excluded from replay.
There are 256 independent seeds per configuration/fixture, not 768.

All **720** first/middle/final unnormalized-measure screens passed against the
independent references. The independent Fraction oracle passes 48 configurations
and its three unit tests. The first oracle invocation failed before computation
because of argument passing; the corrected invocation passed, with both logs retained.

The frozen utility rule passes for scheduled guidance on three primary fixtures
and scheduled auxiliary+guidance on two. Ratios below compare N128 stratified ESS.5
guidance against N128 bootstrap with the same policy/method; lower is better.

| Fixture | Guided RMSE ratio | Guided MSE x work ratio | Auxiliary RMSE ratio | Auxiliary MSE x work ratio |
| --- | ---: | ---: | ---: | ---: |
| Nonlinear growth | 0.914 | 2.656 | 0.826 | 2.325 |
| Sharp-observation growth | 0.468 | 0.700 | 0.457 | 0.745 |
| Persistent modes | 0.490 | 0.832 | 0.500 | 0.907 |
| Late discriminating evidence | 0.461 | 0.731 | 0.450 | 0.713 |

These are measured point estimates under the frozen rule, not confidence bounds
or a universal ordering. Auxiliary persistent-mode efficiency misses the .9 gate;
it is not rounded into a pass. Depletion on sharp growth fell from 146/256 to
0/256 guided and 1/256 auxiliary; persistent-mode depletion fell from 5/256 to
0/256 for both. Zero observed depletion does not guarantee zero probability.

## Counterevidence and recommendation

- Guidance adds roughly 3.2-3.6 times total counted work on these fixtures. Ordinary
  growth fails the cost-adjusted utility gate despite better same-N error. The
  Gaussian control also provides little same-N sign-mass gain for extra work.
- On ordinary growth, N512 bootstrap has RMSE .0383, versus .0726 guided N128 and
  .0656 auxiliary N128. It costs more, but is a strong alternative, not omitted
  from comparisons. On sharp growth, guided N128 has RMSE .0689 at 30,736 work
  units, compared with .0714 at 38,412 for bootstrap N512. On persistent modes,
  the corresponding values are .0908/31,348 versus .0942/36,314.
- The inaccurate auxiliary policy worsens growth RMSE to .1511 and depletion to
  72/256, versus .0794 and 36/256 for bootstrap. Correct importance accounting
  does not make poor look-ahead efficient or harmless.
- Existing 7.2 guided/auxiliary implementations remain competitive on some
  workloads. Their fixed-min=max controls use their original algorithm, not the
  new scheduling semantics. The new API does not dominate them everywhere.
- Vector-Gaussian identities are tested; the comparative utility fixtures are
  scalar/finite state. No broad high-dimensional, real-data or long-history claim.

**Decision:** approved for opt-in integration following independent validation,
packaging, remote CI and final review. Prefer evaluating guided-only first, then pay for auxiliary look-ahead when
predictive parent information justifies it. No new default, threshold tuning,
automatic promotion, precision guarantee or lost-history recovery follows.

## Local integration gates

- 42 focused fixed-budget/selection tests and 57 unchanged released-filter and
  benchmark tests pass. Existing stopping/proposal-helper source files are unchanged.
- The unchanged continuous benchmark completed 7,680 trajectories and passed all
  450 independent screens. Its first analysis invocation selected the historical
  failed v1 reference and was correctly refused. The accepted v2 reference uses
  identical fixture bytes (SHA-256 checked); its preserved historical correction
  splits nonsmooth observables at zero. No fixture, tolerance or acceptance rule
  was changed in this milestone. Both analysis invocations are retained.
- Binary compatibility against published 7.0/7.1/7.2/7.3 passes. The existing
  7.2 TASTy check passes without exclusions; its compiled client runs against
  the replacement artifact. No separate 7.3 TASTy comparison has been claimed.
- All six isolated consumers pass against the exact candidate thin JAR:
  `fa7c1754f42088c6a74a5ae6e06b09e61eae70e9c8e2a9a8b8467c47c94b2760`.
  This is a local dirty-source candidate bundle, not a published release.
- Research-reference checks, 41 documentation tests, 13,190-entry API freshness
  and 15,513 local links pass. Reference group INF-16 maps the new work;
  INF-10/11/15 retain the existing helpers and historical provenance.

Development CI initially targeted 7.4.0-SNAPSHOT and included the new tests and sixth
consumer. The maintainer explicitly approved that alignment after an automated
permission review initially refused it. Publication workflow, immutable artifact
pins and tags remain unchanged. The completed remote validation and integration
decision are recorded below; this is not a declaration that 7.4.0 is ready to publish.

## Final promotion review and integration decision

[Development CI 39908126561](https://github.com/mattwilkinsphoto/figaro/actions/runs/39908126561)
passed all five jobs at `0275d6a1`, including the named repaired direct-launcher
gate, stock coverage, reproducible packaging and all six isolated consumers.
The [companion pilot](https://github.com/mattwilkinsphoto/figaro/actions/runs/39908126558)
also passed. Raw-log review found no duplicate-Promise exception or failed-test
marker; all 20 advisory timing tests passed. This does not establish universal
timing stability or repair the stock runner. See [CI scope and limitations](CI_VALIDATION.md#approved-named-direct-launcher-ci-gates).

The final review reopened and hash-verified the scientific and CI evidence
archives. Reanalysis of the preserved scientific logs reproduced the complete
original result object exactly; no fresh trajectories, retuned thresholds or
replacement seeds were used. The reviewed runtime and comparative-study sources
remain unchanged from `2c7c20c7`. Three oracle tests, 41 documentation tests,
18 CI-tooling tests, reference integrity and local-link checks passed.

The review found no remaining promotion blocker within the frozen scope.
Opt-in integration completed after [closeout CI](https://github.com/mattwilkinsphoto/figaro/actions/runs/39964999116)
passed; [post-merge main CI](https://github.com/mattwilkinsphoto/figaro/actions/runs/39968790021)
also passed. Guidance remains a workload-specific choice; all limitations
and negative results above remain applicable. No automatic proposal choice,
resampling threshold, particle budget or stopping behavior changes.

The final-review evidence archive is 153,106,102 bytes, SHA-256
`3f3ee911f4aecb9d5af1b9a08783f50de82949e9b7fd58798f7135a6eed08536`.
It includes the original scientific and CI archives, reanalysis, captured check
logs and a retained UTF-8 log-reading correction; that correction changed neither
the numerical analysis nor its acceptance criteria.

**Release decision completed:** the additive consumer APIs warranted a **7.4.0 minor
release at normal priority** under the consumer-impact policy. Final-version
packaging, signed publication and all six published-library consumers passed;
7.4.0 is now the adoption baseline. The release record retains exact provenance
and the unresolved advisory timing result. Upstream report submission remains on hold.

Implementation/validation commit: `2c7c20c7` on `codex/guided-fixed-budget`.
The private reproducibility archive is 142,961,413 bytes with SHA-256
`c9542ea79289e8cdabc028f6912eb20a5df102abc889b984c1243277651e3642`.
It includes input/source hashes, exact scripts, captured failures and successful
logs, all outcome rows, machine-readable analysis, plots and rerun instructions.
Its source snapshot and checklist are the pre-sealing implementation commit;
this post-sealing receipt does not rewrite that archive or its evidence.
