# Higher-dimensional GVM fitting protocol

Research provenance: [GVM-05](RESEARCH_REFERENCES.md#gvm-05).
Implements the approved [three-stage plan](GVM_HIGH_DIMENSIONAL_FITTING_RESEARCH.md).
This is test-only representation research; stopping/defaults, scalar fitting and
the parked shared pilot are unchanged. No automatic promotion, merge or release.

## Stage order and independence

First qualify Python/SciPy wrapped-Gaussian controls against high-precision sums,
closed-form Gaussian cases and independently optimized likelihoods. Then qualify
a Commons Math GVM fitter against separately evaluated densities and objectives.
Finally freeze source/configuration hashes before confirmation. Preserve initial
failures and repairs with separate logs; never change gates after seeing held-out
results. The status record will distinguish unfinished work from a negative result.

## Initial numerical envelope

One circular coordinate, radians, and two or three linear coordinates; scalar
controls only for compatibility. Independent, unweighted synthetic training data.
GVM components: 1–3; Gaussian components: 1–4. Three starts, 50 EM iterations,
120 seconds per complete fit and 20 million phase-point contributions. No silent
component pruning/reseeding. Insufficient responsibility mass or ESS is a refusal.
Linear training coordinates use one fixed full-covariance whitening transform.
Eigenvalues are constrained to [0.01,25] in the declared fitting coordinates;
the Gaussian control's angular coordinate remains in radians. GVM phase search
coefficients lie in [-4,4], concentration in [0,50]. These are modeling constraints,
not extensions of public numerical contracts or rotation-equivariance claims.

Wrapped Gaussian evaluation sums conditional angular normal images around the
nearest winding. Bound each omitted side by its first omitted term divided by
one minus the maximum subsequent term ratio; accept only a relative density tail
bound below 1e-12. Refuse after 64 images on either side. Check first/second winding
moments independently as well as density before relying on truncated EM updates.
Primary moderate-range reference tolerance: 1e-8 absolute log density. Check all
probabilities and mixture responsibilities for finite normalization. Hard work
limits are enforced outside optimizer options, including failed/rejected evaluations.

## Frozen confirmation design

Eight cells: dimensions 2 and 3 crossed with curved GVM, seam-crossing GVM mixture,
local Gaussian and wrapped-Gaussian mixture truths. All Gaussian-generated angular
coordinates are wrapped onto the same circle. Predeclared adverse controls cover
weak concentration, overlap, unequal weights, misspecification and rank deficiency.
Truth generators are not implemented by the candidate fitter.

Each primary cell uses 16 confirmation seeds, disjoint from development and
independent train/validation/test streams: 2,000/2,000/20,000 points. Two parameter
ceilings per dimension; fixed K by family/ceiling rather than a test-score-selected
sweep. Three primary methods: GVM, chart-fit GMM, wrapped-likelihood GMM. This
gives 768 method/configuration fits. At most 1,024 fits including development and
controls, with at most three starts each and eight serial-equivalent hours overall.
Before freezing, practical execution calibration may reduce the grid as the plan
allows; any such reduction must be explicit. No incomplete grid can pass.

The full 768-fit grid is retained after development calibration. Parameter
ceilings are 12/39 for two linear dimensions and 20/62 for three; GVM K is 1/3,
and both Gaussian controls use K=1/4. Confirmation seeds are 830001–830016 with
separate child streams for training, validation, testing and predictive draws.
Development uses separate seed ranges. Freeze the exact source hashes in
`tools/gvm_dimension_protocol_v1.json` before running any confirmation case.

Primary endpoint: held-out mean log score, paired by independently generated
training seed, with true-density forward KL as a related diagnostic. Secondary:
fixed-region probability errors, all-outcome status rates, fit/evaluation/sampling
cost and memory. Report conditional-on-success scores alongside failures, never
instead of them. Runtime includes preprocessing and all starts. Cross-language
timing is implementation cost, not an intrinsic algorithmic speedup claim.

Promotion requires all correctness gates and at least 95% `Converged` fits in
every dimension/family/budget/method cell. Budget-limited finite models remain
unqualified even if their retained iterate has a good score. At 16 seeds, this
empirical threshold requires all 16 fits; it is not a population reliability bound.
At the larger parameter ceiling, both curved families must improve log score by
more than 0.05 nats/point over each Gaussian control. On both Gaussian families,
at both ceilings, loss must be at most 0.02 nats/point against each control.
Use paired Student-t intervals over seed-level differences with Bonferroni
family alpha 0.05 over all 32 comparisons. Missing finite pairs cannot pass.
Report measured cost frontiers separately; an accuracy/parameter gate does not
establish cost-matched superiority or automatically authorize promotion.
Optional reverse-KL/Bhattacharyya/MI work cannot displace failed primary cases.
Retain Gaussian-winning cases, unfavorable results and numerical refusals.

## Control implementation and development record

The independent optimizer control uses SciPy direct likelihood optimization,
not scikit-learn: the isolated scikit-learn dependency was not needed. It checks a
small wrapped-normal MLE in a different parameterization and explicit joint-image
objective, alongside closed-form ordinary Gaussian MLE controls. This does not
certify global optima for multicomponent fits. High-precision density and winding
moment checks, coordinate algebra/gradient checks, cross-language GVM objectives
and exports to the unchanged 7.4.0 kernel precede confirmation.

Development exposed two repaired test-tool defects: serialization of an absent
model's score as infinity, and asymmetric roundoff in spectral covariance
reconstruction near zero off-diagonal entries. Record absent scores as null and
construct symmetric matrix pairs once; retain the strict factorization check.
Original failure logs and pre-repair sources remain in the evidence archive.
An initial consumer compile classpath also failed; the retry uses standalone
matching Scala libraries. These are not changes to Figaro's published code.
Development also showed genuine phase-budget exhaustion on multicomponent fits.
The 20-million cap is retained, not increased to turn those outcomes into passes.

Archive exact inputs/hashes, sources/configuration hashes, actual output/errors,
software versions, timestamps, machine-readable outcomes and supporting plots
with rerun instructions. Public summaries exclude private paths and account data.
