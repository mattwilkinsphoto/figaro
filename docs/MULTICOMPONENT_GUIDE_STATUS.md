# Multi component guide experiment status

**Test-only experiment in progress; no promotion decision yet.** The released
7.4.0 stopping estimator, RNG and inference defaults are unchanged.
[Research provenance](RESEARCH_REFERENCES.md#inf-18),
[assessment](MULTICOMPONENT_GUIDE_RESEARCH.md) and
[frozen protocol](MULTICOMPONENT_GUIDE_PROTOCOL.md).

## Completed controls

Stage 1 passed 1,023 independent true-model reference checks and five self-tests.
The specification and references were committed before candidate implementation.

Stage 2 passed 68 Scala controls across seven suites, including 13 new adapter
tests, and seven all-outcome analysis self-tests. A subsequent adapter rerun also
passed with scripted selection of a log-weight -800 component through the actual
mixture sampler. Tests cover affine reduction, full-mixture density and moments,
anchor permutation/overlap, zero slopes, unsupported inputs, budgets, interruption,
independent pilot streams, discarded-pilot accounting, both ancestor methods and
filter lifecycle failures. They do not establish statistical utility.

The first compilation used the nonexistent selector name `Multinomial`; it was
corrected to the existing `LogTreeMultinomial`. The failed source and actual log
are retained with the evidence. No runtime repair or acceptance relaxation occurred.

## Remaining validation

Development-only calibration must freeze timing- and evaluation-matched bootstrap
budgets before confirmation. Confirmation uses 256 fresh seeds and three separate
JVM replays. Pilot refusals and unavailable downstream steps remain in every
inventory; a failed development fit makes its cost match unavailable, not favorable.
Independent all-outcome analysis and the unchanged continuous benchmark precede
any recommendation. No merge, release or runtime integration is part of this task.
