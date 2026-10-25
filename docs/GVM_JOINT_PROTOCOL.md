# Joint optimization feasibility study

Research provenance: [GVM-09](RESEARCH_REFERENCES.md#gvm-09).

Test whether moving mixture parameters jointly can improve bounded fit completion
after the [weight/restart no-promotion result](GVM_WEIGHT_STATUS.md). This is a
development feasibility screen, not independent confirmation or a production
fitter. Keep released stopping, defaults, model bounds and all earlier results
unchanged. No component deletion, penalty, new initialization or larger fit budget.

## Literature and method choice

Sembach, Burgard and Schulz, [Riemannian Newton Trust-Region fitting](https://arxiv.org/html/2104.14957v2)
(2022), sections 3.2 and 4, show why joint curvature can help some overlapping
Gaussian mixtures. Their penalized formulation and likelihood-change termination
are not our objective or qualification. Smaller cases can be slower. Hosseini and
Sra's [publication abstract](https://portal.fis.tum.de/de/publications/an-alternative-to-em-for-gaussian-mixture-models-batch-and-stocha/)
(2020) warns that a naive manifold formulation can perform worse than EM. Only
that abstract is used here, not a transferred convergence theorem.

Use maintained SciPy SLSQP as one constrained quasi-Newton feasibility backend,
not an implementation of either paper's Riemannian method. The official
[SLSQP contract](https://docs.scipy.org/doc/scipy/reference/optimize.minimize-slsqp.html)
supports analytic derivatives, equality/inequality constraints and bounds. Its
termination flag does not certify our physical-coordinate stationarity. Trust-region
Newton and Hessian-vector implementations are deferred until this inexpensive
joint-direction screen justifies more investment. SciPy is already installed in
the isolated research environment under its BSD license. No external code/data is
copied or new runtime dependency introduced. Sources inspected 2026-10-24.

## Unchanged target and bounded adapter

Use the exact marginal GVM negative mean log likelihood and existing all-parameter
analytic derivatives. Parameterize each covariance as L L-transpose, with logged
positive Cholesky diagonal; this preserves positive definiteness at solver trials.
Separate constraints still enforce eigenvalues in [0.01,25]. Positive definiteness
alone is not sufficient. Weights retain their 1e-8 floor and sum-one equality;
phase coefficients remain in [-4,4], concentration in [0,50]. Means and angular
offset are free; wrap offsets only when storing accepted models.

The chain rule maps covariance gradient G to 2 G L, with an extra L[j,j] factor
on logged diagonals. Off-diagonal Cholesky entries are independent coordinates.
Eigenvalue constraints use eigenvector derivatives; repeated eigenvalues have
nonunique derivatives, so controls include multiplicities and the result is not
a global smoothness claim. Independent feasibility and the original projected
physical gradient remain the acceptance checks, not transformed-gradient size.

Keep the original three sequential initializations. Run at most ten ordinary
SQUAREM maps per start using the existing resumable adapter without weight polishing,
then one SLSQP solve (200 iterations maximum). A last cycle may stop at ten maps.
If the prefix is already stationary, skip SLSQP. The entire fit shares the original
20M component-row work units and 120-second cap; the baseline retains 200 maps/start.
All objective/gradient evaluations, including rejected/infeasible trials, cost
2 n K units; retained-iterate physical checks cost another 2 n K. Prefix work is
unchanged. Constraint evaluations and dense optimizer work are counted separately
and included in wall time: row units are not FLOPs. A 2,000-objective-call cap per
start also bounds solver work. Time checks apply to callbacks and constraints.

Only finite, feasible, support-valid, non-worsening solver trial points may replace a
checkpoint. Component responsibility mass and ESS each remain at least max(30,5d).
Failed trials are charged and cannot be silently projected into successful fits.
Return the best valid training-likelihood checkpoint; budget exhaustion remains
incomplete even if a retained model is stationary. Backend success without physical
gradient <=1e-4 remains unqualified. Store every start's terminal status and ledger.

## Cases and predeclared feasibility decision

Reuse the exact first-seed (1150001) inputs from the completed GVM-08 study, now
explicitly development data: d=2,3, five truth families, K=2,3, two arms. This is
20 paired cases / 40 fits, with separated cases as easy controls and overlapping,
overfitted and wrapped cases as harder controls. Copy/hash the ten input files.
Retain 2,000 training and 20,000 test points and the original training whitening.
Do not tune on these outcomes or call them fresh confirmation. Rotate arm order;
single-threaded BLAS and isolated RNG. Two hours is the total fitting cap; hitting
it means partial work, never dropping hard cases.

Before the screen, numerical controls must pass: transformed all-coordinate
gradients within 2e-6 of central differences; independent 60-digit density checks
within 1e-8; spectral constraint derivatives away from multiplicity within 2e-6;
roundtrip/boundary/periodicity, invalid data and budget interruption checks.
Record prefix and final responsibility-column similarity, phase-feature weighted
Gram conditioning, concentration, covariance bounds and gradient blocks. These
are conditioning indicators, not proofs of identifiability or its absence.
Before the paired fits, inspect finite-difference objective Hessians at all 20
old baseline models, using the logged-Cholesky coordinates and the sum-zero weight
tangent. Report symmetry discrepancy, off-diagonal Frobenius fraction and negative
or relatively flat directions (threshold 1e-6 times largest absolute eigenvalue).
This is coordinate dependent and does not restrict directions to active inequality
faces; it cannot certify a saddle or nonidentifiability at a boundary solution.

Advance only to a separately approved fresh-confirmation plan if independent
numerics pass, the candidate completes at least four more paired cases than the
baseline (20 percentage points), loses no baseline-complete case, and every
retained test score is at least baseline minus 0.02 nats/point. All cases and
failures stay in the denominator. These are descriptive feasibility gates, not
statistical noninferiority or population-reliability claims. A promising screen
does not authorize promotion or establish a GVM/GMM advantage.

Audit inventory, hashes, every retained score/support/physical gradient, work sums,
monotone checkpoints and budget failures. Independently evaluate two 60-digit
density points and all physical-coordinate finite differences per retained model.
Preserve exact source revisions, parameters, versions, original output/errors,
timestamps, inputs/hashes, machine-readable results and plot in a read-back-verified
archive. No public runtime changes follow from this study.
