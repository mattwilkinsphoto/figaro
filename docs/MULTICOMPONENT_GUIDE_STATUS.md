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

## Frozen development calibration

Development produced all 5,104 required rows. The branch candidate completed all
16 runs on every fixture. The pilot had 110 terminal refusals: 61 iteration limits,
33 insufficient-component fits and 16 insufficient-pilot fits. It completed every
development run only on flat and omitted-branch fixtures. These are retained
negative outcomes, not permission to increase fitting limits or select successful
seeds. The failed matches use the protocol's N4096 descriptive controls.

The [machine-readable calibration](../tools/multicomponent_budgets_v1.json) and
[execution budgets](../tools/multicomponent_budgets_v1.tsv) are frozen before
confirmation. Branch timing-matched populations range from 95 to 708 particles;
model-evaluation matches range from 65 to 67. These different matches expose
conditioning cost versus calls to the model. The prior-fraction, component counts,
pilot limits and statistical acceptance rules are unchanged.

## Remaining validation

Confirmation uses 256 fresh seeds and three separate
JVM replays. Pilot refusals and unavailable downstream steps remain in every
inventory; a failed development fit makes its cost match unavailable, not favorable.
Independent all-outcome analysis and the unchanged continuous benchmark precede
any recommendation. No merge, release or runtime integration is part of this task.
