# Multi component guide experiment status

**Three-stage experiment completed with no candidate eligible for promotion.** The released
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

## Independent held out result

All **140,032 expected rows per JVM** are present, with identical scientific
results across three fresh JVMs. There are 256 independent seeds, not 768.
All **4,000 unnormalized-evidence and integral screens on fully completed groups**
passed. This does not pass missing pilot groups: their failed and unavailable
rows are retained. The branch candidate passed its own and control correctness
gates; the pilot failed the all-runs reliability requirement.

The [machine-readable summary](../tools/multicomponent_summary_v1.json) includes
completion counts, refusal reasons, primary costs and loss comparisons, and
failed nonregression gates. The independent [analysis](../tools/multicomponent_analysis.py)
produces the complete checks and failed-row inventory from the captured logs.

### Branch guide

The branch guide completed every run. It improved equal-N accuracy and mode
retention, but failed both prespecified primary utility comparisons. Root loss
below is the protocol's combined moment/region error, not an individual RMSE.
Times are full-sequence medians from JVM 1; all three timing comparisons passed.

| Primary fixture | Branch N64 root loss / time | Time-matched bootstrap N / root loss / time | Branch to matched root-loss ratio |
| --- | --- | --- | --- |
| Asymmetric square | .15448 / 3.447 ms | 538 / .16235 / 3.894 ms | .9515; required <=.9 |
| Three branches | .19724 / 4.658 ms | 683 / .12098 / 4.968 ms | 1.6303; required <=.9 |

The paired upper loss-difference screens were .00606 and .02872, respectively;
both needed to be below zero. Thus even the modest asymmetric improvement was
insufficient under the frozen uncertainty and effect-size gates.

The favorable counterevidence matters: versus N64 bootstrap, branch root-loss
ratios were .3131 and .4928. Three-branch mode-depleted runs fell from 77/256 to
0/256 (paired rate difference -.301, approximate 95% interval [-.357,-.244]).
All four declared depletion comparisons passed. Evaluation-matched root-loss
ratios were .3046 and .4909. Conditioning overhead is important for these cheap
scalar models; benefit with expensive observation callbacks is a hypothesis,
not a measured result here.

Affine/weak aggregate gates and all tail gates passed. The deliberately omitted
branch failed both half-line probability-MSE limits: .10953 versus the allowed
.02613. Positive prior defense preserved support, but did not make an incomplete
anchor inventory reliable at N64. No missing-mode detection is implied.

### Shared pilot

**1,788 of 2,816 pilot runs refused**, with 6,567 downstream unavailable rows.
The distinct reasons were 1,011 iteration limits, 521 insufficient-component
fits and 256 insufficient-pilot fits. These are the unchanged fitter's explicit
numerical/information limits, not evidence of a log-weight overflow or proof
that the target cannot be represented by a mixture.

| Fixture | Complete pilot runs out of 256 |
| --- | --- |
| Affine, weak, monotone, tail, asymmetric, three branches | 0 each |
| Symmetric | 205 |
| Merging | 74 |
| Flat | 256 |
| Omitted branch | 256 |
| Narrow transition | 237 |

Only flat and omitted-branch groups completed all held-out runs. Those still
failed declared probability-error nonregression gates; flat also failed its
tail-error gate. No successful-run-only primary comparison was substituted.
The analysis process returns nonzero for this reliability failure; its actual
output and exit status are retained. There were no candidate retries or changes
to fitting limits, anchors, mixture defense or acceptance thresholds.

## Delivery boundary and next decision

Keep both builders test-only. No runtime helper, automatic selection, auxiliary
score, default change, stopping change, merge or release follows from this result.
The released 7.4.0 caller-supplied proposal interfaces remain available.

A future proposal should separate two questions: whether known-branch construction
is worthwhile for models with materially expensive likelihoods, and whether a
different bounded pilot/fitting design can satisfy the all-outcome reliability
contract. The equal-evaluation gains justify considering the first question;
the high refusal rate argues against automatically expanding pilot integration.
Either needs a new reviewed protocol and fresh seeds, not posthoc relaxation of
this experiment. This is not a general negative result about Gaussian mixtures.

The unchanged continuous-filter regression passed all 450 checks across 7,680
runs and 153,600 rows. Another 39 stopping/weighted-pilot regression tests passed,
in addition to the 68 Scala controls above. The reference index, 41 documentation
tests, 18 CI-tooling tests and 15,667 local link targets passed validation.
Runtime/default/stopping source remains unchanged. Full CI and packaged-consumer gates would be required for a separately
approved runtime promotion; they cannot override the failed utility decision.

## Reproducibility

The source, frozen budgets and analysis scripts are tracked on the research
branch. The preserved evidence archive contains the exact three confirmation
logs, complete machine-readable analysis, actual failed compilation, all refusal
rows, original synthetic input hashes, versions, commands, timestamps and a
measured comparison plot. Raw execution logs are not copied into public guides;
they can contain machine paths. The archived manifest verifies every entry.

Run `MulticomponentGuideStudy confirmation tools/multicomponent_budgets_v1.tsv`
through `figaro / Test / runMain` in three fresh JVMs, then pass the three captured
logs, independent reference JSON and frozen budget JSON to
`tools/multicomponent_analysis.py analyze`. The archive README gives complete
commands. A replay uses existing budgets; a redesigned study requires new seeds
and a separately frozen calibration. Nonzero analysis exit is expected for the
retained pilot reliability failure, not a reason to drop those rows.
