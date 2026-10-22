# Resampling-schedule execution status

[Research provenance](RESEARCH_REFERENCES.md#inf-15).
[Frozen protocol](RESAMPLING_SCHEDULE_PROTOCOL.md), committed at `c904812f` before execution.
Implementation and analysis gates committed at `49ca6cc0`. No production changes.

## Calibration complete; confirmation pending

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

Confirmation will use seeds 40001..40256 in three fresh JVMs. Repetitions assess
timing/replay only, not additional independent samples. The previous study and
released stopping estimator remain unchanged. No runtime promotion decision yet.
