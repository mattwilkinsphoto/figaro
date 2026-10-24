# Whole-fit GVM convergence and model complexity: frozen protocol v1

Research provenance: [GVM-07](RESEARCH_REFERENCES.md#gvm-07).

This test-only follow-up asks whether safeguarded outer acceleration improves
whole-mixture stationarity within a fixed work budget, and how underfitting and
overfitting affect that result. It does not change Figaro's released inference
stopping estimator, defaults, public fitter, dependencies or release version.
The [previous gradient study](GVM_GRADIENT_STATUS.md) remains a separate record:
its likelihood-stabilization criterion is not retrospectively relabeled.

## Literature and implementation decision

Du and Varadhan's [SQUAREM paper](https://doi.org/10.18637/jss.v092.i07)
(2020, Algorithm 1 and controls discussion inspected) supplies a suitable bounded
candidate. Two EM maps estimate a direction and its second difference; squared
extrapolation can accelerate slowly converging fixed points. A likelihood guard
and stabilization map protect the accepted sequence. Published speedups are not
measurements of this GVM implementation. Smoothness/contractivity assumptions
have not been established for bounded covariances, circular coordinates and
inexact angular optimization, so the paper's guarantees do not transfer.

The maintained [CRAN SQUAREM package](https://CRAN.R-project.org/package=SQUAREM)
was inspected (2026.1, GPL >= 2), along with its official vignette. No package or
source was copied, installed or linked. No Rscript executable was found on the
checked PATH; this is not a claim that R is unavailable everywhere. A small,
independently written adaptation is justified by this experiment's circular
differences, projections, explicit work accounting and retained-state audits.
SciPy still supplies the angular L-BFGS-B solver; we are not replacing it.
The publisher host timed out; the primary paper's full text was inspected through
a [public mirror](https://www.researchgate.net/publication/339444365_SQUAREM_An_R_Package_for_Off-the-Shelf_Acceleration_of_EM_MM_and_Other_EM-Like_Monotone_Algorithms).

Drton and Plummer's [singular-model BIC paper](https://arxiv.org/pdf/1309.0911)
(v3, abstract/introduction inspected) cautions against treating ordinary BIC as
universally justified for overfitted mixtures. We do not implement sBIC. Instead,
separate validation data select predictive complexity, with untouched test data
for assessment. This does not identify the true number of components reliably.

## Whole-fit diagnostic, not inference stopping

Use the mean negative marginal log likelihood in **fixed training-whitened
coordinates**, refreshing final-model responsibilities. Check every parameter:

| Block | First-order residual |
| --- | --- |
| Weights | Maximum absolute unit-step gradient mapping onto the simplex, each weight >= 1e-8 |
| Linear means | Maximum absolute gradient |
| Symmetric covariance | Frobenius norm of the unit-step mapping onto eigenvalues [0.01, 25] |
| Angular offset | Absolute circular-coordinate derivative |
| Linear/quadratic phase coefficients | Maximum unit-step box mapping, coefficients in [-4, 4] |
| Concentration | Unit-step interval mapping, kappa in [0, 50] |

The largest residual must be <= **1e-4** for `Stationary`. Each component also
needs responsibility mass and ESS >= max(30, 5d). These are declared coordinate-
dependent numerical/qualification thresholds, not posterior-accuracy bounds,
global-optimum certificates or proofs of identifiability. A limit/failure status
overrides a previously retained model's qualification. Never select a lower-
likelihood start merely because it has a better status.

## Candidate and baseline

Both use the same three partitions (first linear coordinate, wrapped angle,
seeded random ordering), moments, analytic angular solver and bounds. The baseline
uses unaccelerated generalized EM. The candidate uses r = theta1 - theta0,
v = theta2 - 2 theta1 + theta0, and alpha = -||r||/||v|| clipped to [-4, -1].
Its trial is theta0 - 2 alpha r + alpha squared v, projected into the domain.
Offset differences use the short circular arc. Zero secants fall back to theta2.
The alpha=-1 identity and a linear-contraction control check the second difference.

Reject trials below theta2's likelihood; stabilize accepted trials with one EM
map and reject decreases relative to both the trial and theta2. Numerical slack
is 1e-10 per training observation. Invalid trial components also fall back;
global work/time exhaustion does not. Rejected work is charged. No component
deletion, reseeding, changed defaults or rescue with unseen larger budgets.

Limits: **200 EM map calls per start**, three starts, **20M total work units per
fit**, 120 seconds per fit. A work unit is a logical row contribution to phase,
scoring, moments or full-gradient evaluation; report all four categories.
This stricter accounting is not the previous phase-only 20M budget and is not a
FLOP-equivalence claim. A squared iteration may use three maps; compare map calls,
not nominal outer iterations. Retain exact evaluated models, scores and traces
on failure. No successful status based only on a solver's termination flag.

## Fresh model-complexity grid

Use two and three linear coordinates plus one angle. Five truths: one-component
curved GVM, two-component seam GVM, genuinely three-component separated and
overlapping GVMs, and a three-component wrapped-Gaussian mixture. The latter three
use distinct means/parameters and positive weights [0.3, 0.4, 0.3]; the exact
[generator](../tools/gvm_wholefit_study.py) is hashed before confirmation.

Eight fresh seeds 1050001–1050008 generate 80 datasets. Each has 2,000 training,
2,000 validation and 20,000 test observations on independent derived streams.
Whitening uses training data only. K=1,2,3 and both methods produce 480 primary
fits. Eighty wrapped-GMM K=4 controls use three starts and 200 iterations;
their older likelihood criterion and different work accounting make them
contextual controls, not cost-matched or whole-fit-qualified competitors.

Within each method/dataset, choose the largest validation score among `Stationary`
K fits, breaking exact ties toward smaller K. If none qualifies, selection is
explicitly unavailable. Charge all K fits to model-selection work. Report
under/correct/overfit cells separately; never use test scores to select K.
The three-component Gaussian truth is not an exactly specified GVM truth.

Development uses distinct seeds, no confirmation seed access. At most 640 fit
attempts including controls/development; confirmation has exactly 560 fits and
a three-hour serial ceiling. Missing cells fail validation, not disappear from
denominators. Source hashes, versions and gates are committed/pushed before the
fresh grid. Any change afterward requires a new protocol and new seeds.

## Independent checks and decision gates

Before confirmation: all-block central differences, projection KKT/boundary
checks, uniform-angle controls, extrapolation algebra, explicit work/time/map
limits, invalid input, exact retained-state scores and rejection fallback.
After confirmation: complete/unique inventory and input/source hashes, all-model
60-digit independent inverse/determinant densities and wrapped-image sums,
retained scores/traces, and all-parameter objective differences for every cell
at the first fresh seed (60 GVM models). Density tolerance 1e-8; central-difference
gradient tolerance 2e-6. A same-formula gradient refresh is an integrity check,
not the independent numerical reference.

All gates are required even to recommend separate promotion review:

1. Independent numerical and inventory checks pass.
2. Candidate `Stationary` rate >=95% in every dimension/truth/K cell (eight of
   eight here, not a population reliability confidence bound).
3. At least 20 percentage points more qualified K=2,3 fits than unaccelerated EM.
4. Every one of 30 paired test-log-score comparisons passes a one-sided
   Bonferroni-adjusted Student-t lower bound above -0.02 nats/point (familywise
   alpha 0.05), using eight independent training seeds. Missing finite pairs fail.

Scores include retained unqualified finite-budget fits, explicitly labeled;
score noninferiority cannot replace stationarity. Report costs, selection
availability and Gaussian-control status even if gates fail. Synthetic coverage
is narrow; passing does not authorize a public API, promotion, merge or release.
Independent representative validation and a separate review remain necessary.
