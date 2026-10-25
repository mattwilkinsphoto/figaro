# Mixture weight and restart study protocol

Research provenance: [GVM-08](RESEARCH_REFERENCES.md#gvm-08).
Basis: [literature assessment and bounded plan](GVM_WEIGHT_RESTART_RESEARCH.md).

This test-only study tests conditional weight polishing and resumable allocation
separately and together. Runtime code, inference stopping, defaults, public scalar
fitting, library dependencies and version remain unchanged. The shared pilot stays
parked. A successful study permits a separate promotion review, not public release.

## Stage 1 and implementation qualification

The fixed-density screen reuses 40 old accelerated fits: d=2,3, five existing truth
families, K=2,3 and old seeds 1050001/1050002. These are development data, not fresh
confirmation. The [qualification report](../tools/gvm_weight_qualification_v1.json)
records 31 certified weight solves, 40 support-valid models and 13 cases passing
the predeclared improvement/residual screen. High-precision objective and gradient
checks pass. Nine uncertified solves are retained, not called solver successes.

The [solver](../tools/gvm_weight_solver.py) wraps SciPy SLSQP, with analytic gradient,
100 iterations, 200 callback evaluations and 1M local logical work units. Its
conditional certificate is separately accumulated with compensated sums, a
`128 * binary64_epsilon * max(1,max(abs(gradient)))` error allowance and independent
60-digit checks of the declared envelope. This is not an interval-arithmetic
guarantee for arbitrary math libraries. Qualification requires gap plus allowance
at most `1e-8` nats/point. Full-fit stationarity remains the old `1e-4` threshold.

The [independent reference](../tools/gvm_weight_reference.py) uses mpmath, checked
simplex vertices/edges and interior equations for small fixtures. The solver's
success flag is not accepted as proof. Reference controls cover duplicate columns,
boundary solutions, nearly indistinguishable components, row shifts/permutations,
extreme log densities, invalid inputs and work/call limits.

The [additive adapter](../tools/gvm_weight_fit.py) retains GVM-07 map algebra,
extrapolation cap and strict likelihood/support checks. Weight polishing occurs
after an ordinary map, at fixed new component shapes, before extrapolation or
stabilization uses that composite map. Fresh responsibilities/full gradients qualify
the accepted model. Low-support, uncertified or numerically refused polishing
falls back to the ordinary map explicitly; global work/time failures are never
swallowed. Component mass and ESS must each remain at least `max(30,5d)`.

## Work accounting and checkpoint semantics

The original score, phase, moments and gradient ledger is unchanged. Additional
polishing work is explicit: component density construction costs nK score units;
building the row-scaled matrix costs nK weight units; each combined objective and
gradient callback costs 2nK weight units; final compensated objective/gradient/gap
evaluation costs 3nK weight units. Cache reuse does not make matrix products free.
All successful charges, including rejected work, count against the global budget.
These are logical component-row visits, not FLOPs or measured CPU-equivalent work.

Balanced scheduling initializes all three starts, then advances one safeguarded
cycle of the active start with least charged work, breaking ties by index. Unequal
cycle costs make fairness approximate. Per-start initialization uses its own
deterministic PCG64DXSM partition; no RNG is shared between starts after initialization.
Only fully evaluated accepted maps become checkpoints. Interrupted partial work is
charged, but cannot replace a valid checkpoint. There is no early elimination based
on likelihood and no automatic larger budget. Sequential scheduling initializes
starts lazily, matching the old behavior.

The selected model maximizes retained training likelihood, regardless of its
qualification. Retained stationarity and search disposition are reported separately.
The old combined status is still primary: a global work/time exhaustion overrides
the selected start's status. All-start termination includes explicit map limits or
refusals; it does not claim all starts converged. A reporting change alone cannot
pass the completion-improvement gate.

## Development and frozen confirmation

Development uses seed 450101, all ten dimension/family cells, K=3 and all four arms:
40 fits. Its original source snapshot and results are retained. A subsequent output
metadata addition labels allocation explicitly; the independent audit infers that
label from the arm for the original development records. No fitting algebra changed.
Numerical/integration tests and development audits must pass before freeze.

The [machine-readable protocol](../tools/gvm_weight_protocol_v1.json) pins exact
sources, versions, seeds and criteria. It must be committed and pushed before
confirmation starts. Confirmation seeds 1150001–1150008 are disjoint from the old
protocols and this development seed. No failed seed may be replaced.

| Arm | Weight map | Restart allocation |
| --- | --- | --- |
| baseline | Unmodified GVM-07 safeguarded SQUAREM | Original sequential |
| weights | Composite map with certified conditional polish | Sequential |
| balanced | Original map algebra in the checked adapter | Least charged work |
| combined | Composite polished map | Least charged work |

The designated candidate is **combined**. Other arms explain effects; a favorable
ablation cannot be selected after confirmation and called the designated winner.
The grid uses d=2,3 linear coordinates plus one angle; curved, seam, separated true
K=3, overlapping true K=3 and wrapped-Gaussian truths; fitted K=1,2,3; eight seeds.
K=1 bypasses polishing but tests scheduling. Training-only whitening uses 2,000
training observations; validation and test sizes are 2,000 and 20,000. All model
bounds remain those of GVM-07. Validation may select K; test data may not.

There are 960 primary fits plus 80 wrapped-GMM K=4 descriptive controls, hence
1,040 confirmation fits. Each primary fit has 20M work units shared among three
starts, 200 maps per start and 120 seconds. Total study fitting time, including
development, is capped at three hours. At most 160 additional full development/
control fits are allowed; the fixed-shape Stage 1 screen is not a whole-model fit.
Independent audits and mathematical checks have separately recorded cost. Missing
rows or study-limit interruption make confirmation incomplete, not a reduced grid.
Gaussian controls retain their older convergence/work criterion; no matched-cost
GVM superiority claim follows from them.

## Independent validation and acceptance

The [audit](../tools/gvm_weight_audit.py) checks every input hash, fit identity,
retained score, trace, parameter/support bound, full-gradient report and work ledger.
It replays balanced scheduling independently. Every retained model gets two
60-digit density checks; all physical derivatives and full-data weight gradients/
gaps receive independent checks on the first seed in every cell/arm (all 40 fits
for development). These finite samples are numerical validation, not a proof for
every parameter value or every intermediate polish. Missing/duplicate inventory,
omitted work and wrong scheduling are negative controls.

Qualification requires density/score agreement within `1e-8`, physical derivative
agreement within `2e-6`, and independently computed conditional gap agreement within
`1e-10` on audited retained models. Solver fixtures also check their explicit gap
allowance against high precision. No full-fit success can bypass support checks.

All promotion-review gates must pass:

1. All numerical, inventory, failure and protected-scope controls pass.
2. At least 95% combined-arm complete stationary fits in every d/family/K cell,
   meaning eight of eight here, not a population confidence statement.
3. Combined multicomponent completion exceeds the unchanged accelerated sequential
   baseline by at least 20 percentage points on the paired new data.
4. All 30 combined-versus-baseline paired test-score comparisons pass one-sided
   Student-t Bonferroni noninferiority at familywise alpha=0.05 and margin
   0.02 nats/point. Include unqualified finite models; a missing model fails its
   gate. Other-arm comparisons remain descriptive.

Report common-budget quality, retained/full-search completion, support refusals,
work categories and advisory elapsed times. Censored time-to-qualification records
include unfinished runs; never report success-only average speedups. No weakening
of thresholds, enlargement of budgets or held-out retuning is permitted. On any
failed gate, retain a no-promotion result and the complete evidence.

Archive exact synthetic inputs/hashes, source revisions, versions, parameters,
actual execution logs/errors/timestamps, machine-readable results, plots, status
and rerun instructions. Read back every archived manifest entry before delivery.
