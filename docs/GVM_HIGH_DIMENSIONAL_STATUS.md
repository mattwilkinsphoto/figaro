# Higher-dimensional GVM fitting results

Research provenance: [GVM-05](RESEARCH_REFERENCES.md#gvm-05).

**All three research stages are complete; no promotion.** The test-only GVM
representation produced useful held-out log-score gains on the curved controls,
but every three-component GVM fit exhausted the frozen phase-work budget.
The Gaussian controls also frequently reached their iteration limit. Independent
numerical checks passed; reliable multicomponent optimization remains unresolved.
No public API, release, stopping/default change or shared-pilot work follows.

The [assessment](GVM_HIGH_DIMENSIONAL_FITTING_RESEARCH.md),
[frozen protocol](GVM_HIGH_DIMENSIONAL_PROTOCOL.md) and
[machine-readable summary](../tools/gvm_dimension_summary_v1.json) distinguish
the planned criteria from these observations. Code/protocol were committed and
pushed at `04efb0a8` before confirmation seeds were used. Results were not used
to alter the candidates, budgets, component counts or acceptance margins.

## Stage 1: independent references and Gaussian controls

Eight reference test groups passed: high-precision joint GVM density; wrapped
normal density and first/second winding moments; seam invariance and explicit
winding-budget refusal; covariance-coordinate transformations with cross terms;
analytic phase-gradient checks; Gaussian/uniform-angle reductions and Jacobians;
ordinary Gaussian MLE and wrapped EM controls; and sampler moments/input failures.

The wrapped control uses soft component/winding responsibilities and optimizes
the wrapped likelihood. A small independent SciPy direct-likelihood solve uses
explicit joint images and a different parameterization; it is not the same
conditional-normal EM routine. This supplements, rather than replaces, the
high-precision checks. It does not certify global multicomponent optima.

## Stage 2: bounded test-only fitting

The Commons Math candidate uses full covariance and a symmetric quadratic phase
in a fixed training coordinate frame. Independent Python density and gradient-based
phase solves agreed in dimensions 1, 2 and 3; scientific replay was identical.
Failure controls exercised phase/time/angular/iteration limits, insufficient
components, nonfinite inputs and rank-deficient data. Sixteen adverse fits covered
weak angular concentration, overlapping components, small mixture weights and
misspecified heavy-tailed/nonquadratic data. All retained finite models, but some
reported iteration limits or insufficient components; these are not utility passes.

Sixty parameter exports agreed with the unchanged Figaro 7.4.0 kernel. The
unchanged public scalar fitter also converged on the scalar compatibility data;
its mean training log score differed from the candidate by about 1.2e-7 nats/point.
Neither implementation is claimed to find a global optimum.

Two development defects were repaired before freezing: absent-model score
serialization and asymmetric roundoff in spectral covariance reconstruction.
The latter is repaired by constructing each symmetric pair once, not by relaxing
the strict Cholesky check. Original failure logs and pre-repair sources are retained.
The first isolated consumer compilation also failed with an assembled-JAR-only
classpath; explicit matching Scala libraries passed. Published Figaro code was
not modified by these repairs.

## Stage 3: complete frozen confirmation

The full grid contains 768 fits: two linear dimensions (2 and 3), four truth
families, 16 independent training seeds, two parameter ceilings and three methods.
Each seed has separate 2,000-point training and validation sets and a 20,000-point
test set. Predictive region checks use another 20,000 draws per fitted law.
Data are generated synthetic controls, not application measurements.

All 768 fits returned finite models. Finite is not the same as qualified:

| Method | Converged | Phase budget exhausted | Iteration limit | Insufficient component |
| --- | ---: | ---: | ---: | ---: |
| GVM | 128 | 128 | 0 | 0 |
| Chart-fit GMM, wrapped evaluation | 146 | 0 | 105 | 5 |
| Wrapped-likelihood GMM | 137 | 0 | 119 | 0 |

All 128 one-component GVM fits converged. All 128 three-component fits exhausted
the 20-million phase-point cap. Only one exhausted fit contained any converged
restart; in the other 127, the selected retained state was itself budget-limited.
This is not principally a reporting artifact caused by an unfinished extra restart.
The frozen 95% completion threshold fails; it is not weakened after observing these
results. Gaussian completion limitations also prevent treating the comparisons as
comparisons of fully optimized mixture likelihoods.

### Representation quality of retained finite-budget models

At the larger parameter ceiling, GVM uses three components versus four Gaussian
components. Positive differences below favor GVM over the wrapped-likelihood GMM.
These comparisons include the retained **unqualified** models and do not reverse
the completion failure.

| Truth family | d=2 mean log-score difference | d=3 mean log-score difference |
| --- | ---: | ---: |
| Curved GVM | +0.077 nats/point | +0.383 nats/point |
| Seam-crossing GVM mixture | +0.147 | +0.534 |
| Nearly local Gaussian | +0.001 | +0.001 |
| Wrapped-Gaussian mixture | -0.003 | -0.007 |

The finite-model score comparisons pass the frozen curved-gain and Gaussian-loss
margins against both Gaussian controls. The Gaussian-generated wrapped mixture
still favors the Gaussian representation, as the negative entries show. The
summary includes all 32 paired comparisons, Bonferroni-adjusted Student-t intervals,
forward-KL estimates and fixed-region errors. These intervals use 16 independent
training replicates and approximate Student-t assumptions, not a distribution-free
guarantee. Conditional accuracy cannot establish reliable fitting when completion
fails.

### Independent numerical audit and costs

A separate 60-digit calculation checked two points from every fitted model
(1,536 points), plus 256 truth-density points, using full inverse/determinant joint
densities and explicit wrapped sums. Maximum absolute log-density discrepancy was
below 4.5e-15 on those points, against the frozen 1e-8 tolerance. Independent GVM
training-log-likelihood totals differed by at most 3.3e-11. All 256 saved input
hashes matched; missing/duplicate inventory negative controls were rejected.
These checks support the tested numerical calculations, not untested parameter
regimes or global optimization claims.

The confirmation grid took about 602 seconds on the measured machine. Median
larger-ceiling core fitting times across cells were approximately 0.54–0.89 seconds
for GVM, 0.23–0.28 for chart GMM and 0.43–1.01 for wrapped GMM. The GVM timing ends
at its work cap; it is **not time to convergence**. JVM startup, shared preprocessing
and evidence-capture overhead are recorded separately. Different backends and
ordinary desktop scheduling prevent an intrinsic algorithmic speedup claim.
The full record retains density/sampling timings, sampled process memory and
region-probability errors. No cost-matched superiority is established.

## Decision and recommended next work

Keep the fitter and controls test-only. The results justify a focused optimizer
robustness follow-up, not more dimensions or a public mixture-fitting API yet.
First inspect angular solve/convergence work and compare an analytic-gradient
candidate (already available as an independent reference) against BOBYQA under
a new frozen protocol. Include smaller component counts and converged Gaussian
controls where budgets permit. Record work-versus-quality curves and all refusals;
do not simply increase defaults or relabel exhausted fits as converged.

The subsequently approved [analytic-gradient follow-up](GVM_GRADIENT_STATUS.md)
is now complete with a separate frozen protocol and no promotion. The original
results above remain unchanged. Shared-pilot work remains parked.
Stopping/defaults, public scalar fitting, production source, build settings and
the release version are unchanged. No release is warranted for this research-only
change, and no merge or promotion is implied.

## Evidence and reproduction

[Frozen configuration/source hashes](../tools/gvm_dimension_protocol_v1.json),
[study runner and strict summary](../tools/gvm_dimension_study.py),
[independent audit](../tools/gvm_dimension_audit.py),
[plot generator](../tools/gvm_dimension_plot.py), and
[archive builder](../tools/gvm_dimension_archive.py) accompany the public summary.
The private evidence archive retains exact generated inputs, earlier failed runs,
source revisions, actual commands/output/errors and timestamps, dependency JARs,
machine-readable full results and plots. Its manifest and every archived entry
are read-back verified before handoff. Original logs include machine paths and
are intentionally not committed to the public repository.

Completion audit: references/controls, bounded candidate and full confirmation
are complete; independent numerical checks passed; promotion/completion gates
failed. Documentation checks validate navigation/provenance structure, not these
scientific conclusions.
