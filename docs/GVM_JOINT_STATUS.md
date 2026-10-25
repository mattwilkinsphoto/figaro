# Joint optimization feasibility results

Research provenance: [GVM-09](RESEARCH_REFERENCES.md#gvm-09).

**The bounded feasibility screen passes and merits a fresh-confirmation plan.**
The hybrid joint optimizer completes 18/20 development cases versus 10/20 for the
unchanged accelerated baseline, losing no baseline-complete case. Independent
numerical checks and every descriptive score margin pass. This is not promotion:
all cases reuse earlier synthetic data, two candidate fits remain unqualified,
and lower recorded data-pass work does not establish a runtime speedup.

Stopping, defaults, public fitting, runtime dependencies and release versions
remain unchanged. Nothing is merged or released. Earlier no-promotion results
remain valid for their methods; this is a different candidate and development screen.

## What was tested

The [protocol](GVM_JOINT_PROTOCOL.md) and [frozen source/input configuration](../tools/gvm_joint_protocol_v1.json)
were committed and pushed as `7313a92f` before paired execution. All 40 fits finished:
d=2,3; five truth families; K=2,3; one reused seed; two arms. The ten input files
match the original GVM-08 hashes. Every fit retained a finite model; no failed case
was omitted. End-to-end screen execution took about 47 seconds, including output
capture and file-access operations, not just optimizer time.

The candidate uses up to ten unchanged SQUAREM maps per start, then one SciPy
SLSQP joint solve. It is not a Riemannian Newton implementation. The existing
three sequential initializations, 20M data-pass work cap, 120-second fit cap,
parameter bounds, component support and physical stationarity threshold stay in
force. The prefix, rejected trials and qualification checks all count toward work.

## Feasibility gates

| Predeclared gate | Observation | Result |
| --- | --- | --- |
| Independent numerical controls and all-model audit | Pass; all 40 models audited | Pass |
| At least four additional complete cases | Eight additional: 18 versus 10 | Pass |
| No lost baseline-complete cases | Zero lost | Pass |
| Every test-score difference at least -0.02 nats/point | Range approximately -0.00687 to +0.00416 | Pass |

The [machine-readable summary](../tools/gvm_joint_summary_v1.json) contains every
pair, status, score difference and geometry indicator. The score screen has no
confidence interval or population noninferiority interpretation; one reused seed
per case is insufficient for that claim. The result authorizes consideration of
fresh confirmation, not a public fitter or general reliability statement.

## Cost and the strongest counterevidence

| All 20 fits per arm | Baseline | Joint candidate |
| --- | ---: | ---: |
| Complete stationary workflows | 10 | 18 |
| Total recorded data-pass work | 290.838M | 175.276M |
| Median recorded work | 19.030M | 6.059M |
| Sum of measured fit time, advisory | 14.62 seconds | 16.52 seconds |
| Median measured fit time, advisory | 0.91 seconds | 0.48 seconds |

Dense constrained optimization has costs that component-row counts do not capture.
Despite fewer data passes, total candidate fit time was greater in this run; median
time and total time tell different stories. These are single-run advisory times,
not a controlled timing benchmark or cost-matched speedup. Light documentation and
environment checks occurred during execution. A confirmation plan must evaluate
accuracy and qualification against actual total cost as well as the logical ledger.
Constraint counters cover solver callbacks; internal feasibility checks also cost
time and are not separate solver callback counts.

Both unresolved cases are d=3 curved models, K=2 and K=3. Their selected models have
physical residuals approximately `1.37e-3` and `2.93e-4`, above the unchanged `1e-4`
threshold. Selected starts reached the 200-iteration SLSQP cap. Other starts in
these fits did qualify, but had lower training likelihood. The selection rule still
chooses greatest training likelihood; choosing a lower-likelihood qualified start
after seeing results would change the contract. These remain two failures.

## Conditioning and numerical evidence

The pre-fit [20-model geometry screen](../tools/gvm_joint_geometry_v1.json) used
existing baseline models as development diagnostics. Four have relatively flat
directions and one has a negative direction in the tested sum-zero weight tangent.
Off-diagonal Hessian fractions range from about 0.56 to 0.74; finite-difference
symmetry discrepancy is below `5.5e-9`. These coordinate-dependent observations
make joint movement plausible but do not establish that coupling is the sole cause,
prove nonidentifiability or certify a constrained saddle. Active inequality faces
are not removed from that diagnostic tangent. Prefix/final responsibility similarity,
weighted phase-feature conditioning and physical residual blocks are also retained.

Checks actually executed:

- Nine controls cover all transformed gradient coordinates in d=2,3, covariance
  constraint Jacobians away from multiplicity, repeated-eigenvalue feasibility,
  covariance bounds, angle periodicity, zero concentration, invalid/rank-deficient
  data, call/work/time interruption, infeasible/low-support refusals, and false
  backend success. Missing/duplicate inventory and omitted ledger entries are
  rejected. A stationary checkpoint cannot conceal later global exhaustion.
- All ten input hashes and frozen source hashes match. Every retained score,
  parameter/support bound, physical residual, monotone checkpoint trace and work
  total is audited. No missing model is silently excluded.
- All 20 baseline models, scores, statuses, diagnostics, per-start traces and work
  reproduce the earlier GVM-08 run exactly; wall times are excluded from that check.
- Eighty separate 60-digit inverse/determinant density evaluations agree within
  `4.5e-15`; the limit is `1e-8`. All physical-coordinate finite differences across
  all 40 retained fits agree within `1.04e-7`; the limit is `2e-6`. Score recomputation
  discrepancy is below `8.9e-16`.

The independent density path and finite differences do not reuse the joint
coordinate-gradient mapping. Recomputed analytic stationarity is an additional
consistency check, not a second independent derivative oracle. These checks cover
the declared envelope, not arbitrary dimensions or all optimizer trial states.

The initial documentation test failed because the new reference group lacked a
navigation link. The link was repaired and all 41 documentation tests passed;
the original failure log remains preserved. No numerical source or gate changed
after protocol freeze. The all-case comparison figure was visually checked.
Final checks also pass 17 unchanged weight-study controls, nine unchanged whole-fit
controls, 18 tooling tests, reference integrity, local links and protected-scope checks.

## Next decision and reproducibility

Recommend a separate, frozen fresh-seed confirmation plan for this exact candidate,
including repeated cost measurements and explicit treatment of both unresolved
curved cases. Do not increase iteration caps or replace the selection rule using
these outcomes. Broader per-cell reliability, Gaussian-mixture cost controls and
representative consumer workloads remain unestablished. Any production integration
requires independent confirmation and a separate promotion review.

The [evidence tool](../tools/gvm_joint_evidence.py) packages original inputs and
SHA-256 hashes, frozen and per-command source revisions, actual logs/errors,
timestamps, package versions, machine-readable outputs, plot and rerun instructions.
The archive must pass a read-back SHA check of every entry before handoff. Installed
dependency code is not redistributed; runtime library artifacts are untouched.
