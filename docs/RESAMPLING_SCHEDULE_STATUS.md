# Resampling-schedule execution status

[Research provenance](RESEARCH_REFERENCES.md#inf-15).
[Frozen protocol](RESAMPLING_SCHEDULE_PROTOCOL.md), committed at `c904812f` before execution.
Implementation and analysis gates committed at `49ca6cc0`. No production changes.

## Calibration complete

Four Scala tests and three Python tests passed. Independent Fraction enumeration
checks ten combinations of thresholds and resampling schemes; each gives the
two-step unnormalized vector (93/500, 23/500). New switching-model references use
exact finite-state forward recursion; original grid/Gaussian references are preserved.

All 1,200 calibration trajectories completed on the five training fixtures, seeds
30001..30016. The frozen rule selects **ESS-triggered stratified resampling at .75,
N=256** (`ess75`), against **released guided filtering at N=64**. Candidate scores:
strat 1439.9725; ess25 1889.6288; ess50 1700.3249; ess75 1236.9324.
These are calibration scores, not held-out superiority claims or recommended defaults.
The baseline is chosen from affordable released configurations, not limited to a
bootstrap filter at equal particle count. Held-out seeds and the switching fixture
do not participate in selection. This choice is committed before confirmation.

The choice was committed at `7b2c7e55` before confirmation. No selection rule,
threshold, reference, seed or acceptance gate was changed after outcomes.

## Held-out experiment: all research gates pass

Confirmation used seeds 40001..40256 in three fresh JVMs: 18,432 trajectories per
JVM, **55,296 trajectories and 1,400,832 step records** overall, with no failed
outcomes. Scientific outputs (excluding timings) were identical in all three.
There are 256 independent seeds per configuration/fixture, NOT 768.
All **864 unnormalized statistical controls** and six Gaussian/growth normalized
regression checks passed. These are conservative regression screens, not proofs
of unbiased normalized posterior moments or universally reliable inference.

Primary error is sign-probability RMSE integrated over reference-ambiguous steps,
not final near-one probability. The candidate is ESS75/N256; the preselected
affordable baseline is released guided/N64:

| Fixture | Candidate / baseline integrated RMSE | Ratio and ordinary 95% paired interval | Family-wise one-sided upper ratio | Candidate / baseline depleted runs |
| --- | --- | --- | --- | --- |
| Persistent modes | .120329 / .221436 | .5434 [.4950, .5950] | .5995 | 0/256 versus 27/256 |
| Late disambiguation | .100686 / .253194 | .3977 [.3652, .4339] | .4369 | 0/256 versus 60/256 |
| Confirmation-only switching model | .045117 / .182086 | .2478 [.2284, .2691] | .2709 | 0/256 versus 3/256 |

All three meet the frozen 20% error-reduction requirement and family-wise upper
ratio below one. Depletion means a run leaves [.01,.99] at any step whose reference
mass is in [.1,.9]. Zero observed events is not zero risk: each candidate's exact
one-sided 95% binomial upper bound is about **1.16%**. These are per-fixture bounds,
not a simultaneous guarantee across arbitrary models.

### Resource comparisons: ceilings, not identical work

| Fixture | Candidate / guided-N64 mean counted work | Candidate milliseconds in JVMs 1/2/3 | Guided-N64 milliseconds in JVMs 1/2/3 |
| --- | --- | --- | --- |
| Persistent modes | 20,084 / 16,740 | 1.554 / 1.302 / 1.236 | 1.799 / 1.484 / 1.402 |
| Late disambiguation | 38,234 / 31,928 | 2.917 / 2.337 / 2.279 | 3.446 / 2.864 / 2.732 |
| Switching | 25,958 / 20,288 | 1.396 / 1.116 / 1.098 | 1.713 / 1.352 / 1.350 |

The candidate uses approximately 20-28% more counted work than this cheaper
baseline; callback counts are not equal CPU costs. It is faster in these measured
runs, and meets the 20% accuracy frontier gate against **every tested released
bootstrap/guided/auxiliary configuration at N=64/256/1024** no more costly than
it, and against every measured baseline no slower than it in each JVM. No timing
interpolation, extrapolation or global optimality claim is made. Timings include
step setup, not process startup, warm-up, output or offline selection; memory was
sampled every two seconds during all three runs. Monitoring overhead is included.

### What the controls establish—and what they do not

At the same N=256, one-selection multinomial control RMSE was .15816, .15333 and
.09638 on persistent, late and switching fixtures; the double-selection control
was .16254, .16772 and .10568. The selected combined schedule was better on these
point estimates. These exploratory control comparisons do not carry the primary
comparison's predeclared simultaneous confidence claim.

The selected schedule averaged 18.45/19, 29.35/39 and 5.40/31 resampling operations
on the respective fixtures. In particular, it almost always resampled on persistent
modes: skipping alone cannot be presumed to explain that gain. Stratification,
population size, retained-weight handling and implementation cost all matter.
Always-stratified N256 was compared in calibration but not separately reconfirmed;
the study therefore does **not** isolate a universal benefit of threshold .75 over
always-stratified selection. Do not install .75 as a universal default based on this
experiment. A held-out ablation would be appropriate before making that claim.

The switching fixture is one additional finite-state family, not broad validation
for vector states, very long horizons, rare modes, arbitrary graphs or lost paths.
ESS is a scheduling statistic, not a mode-coverage or precision certificate.
Finite-resolution stratification does not inherit the log-mass tree's tiny-interval
preservation contract. A runtime integration must state this limitation explicitly.

## Recommendation and delivery boundary

The research result supports a **separate, opt-in fixed-budget integration review**:
owned immutable states, explicit resampling policy, correct carried weights and
evidence increments, retained diagnostics, and compatibility/numerical tests.
It does not authorize correlated offspring inside the released random-stopping
estimator. No public API, default, library version or released byte has changed;
Figaro 7.2.0 remains the adoption baseline. This research-only change does not
warrant a consumer release. Production implementation requires separate approval.

The unchanged regression rerun completed 7,680 trajectories / 153,600 steps and
passed all **450 original checks**, with none blocked or failed. All **63 focused
Scala tests** passed, including the new schedule suite. Maximum observed process
peak working sets were 855,412,736 bytes for the forked experiment (277 samples)
and 1,013,567,488 bytes for sbt (283 samples); these are not per-method measurements
or certified final peaks. The study stayed within its declared resource budget.

The reference-navigation test initially failed because the
new group lacked its index-table entry; that entry was corrected, and all 41
documentation tests then passed. The failure log is preserved. No numerical test
or acceptance criterion was relaxed.

Reproduction evidence includes frozen source revisions, original input hashes,
exact commands/versions, actual logs/errors, per-step records, selection and gate
JSON, cost/error plots, resource observations and remote-CI snapshots. The
descriptive report does not participate in selection or acceptance decisions.
Remote CI and final pushed revision are recorded in the delivery handoff/evidence;
local checks must not be read as a claim that an unfinished remote run passed.
