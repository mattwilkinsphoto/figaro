# Higher-dimensional GVM-mixture fitting: assessment and bounded plan

Status: all three test-only stages are complete with **no promotion**. See the
[results and limitations](GVM_HIGH_DIMENSIONAL_STATUS.md) and unchanged
[experiment protocol](GVM_HIGH_DIMENSIONAL_PROTOCOL.md).
The assessment below was recorded against `9771d4e5` on 2026-10-23.
Research provenance: [GVM-05](RESEARCH_REFERENCES.md#gvm-05), with the existing
joint law and scalar fitter in [GVM-02](RESEARCH_REFERENCES.md#gvm-02) and
[GVM-04](RESEARCH_REFERENCES.md#gvm-04).

## Recommendation

Investigate a test-only fitter for **two or three linear coordinates and one
angle**, with full linear covariance and a full symmetric quadratic angular
phase. Qualify independent numerical references and Gaussian controls first,
then a bounded fitter, then a frozen held-out representation study. Do not
extend the public scalar fitter by merely removing its dimension check.

This is density representation work, not a new filtering method. Keep released
stopping, defaults, scalar fitting contracts and previous no-promotion results
unchanged. The shared-pilot proposal experiment stays parked. No merge, release,
public API, automatic model selection, report ingestion or PDF fusion follows
from this assessment. Approval covers the three test-only stages below, not promotion.

## What exists, and what is missing

| Capability | Current evidence | Gap addressed here |
| --- | --- | --- |
| Fixed GVM mixtures | Immutable kernels allow 1–32 linear coordinates and one angle, with full symmetric quadratic phase | Fitting is not implied by density/sampling support |
| Public GVM fitter | Bounded IID fitting for exactly one linear coordinate plus one angle | Multivariate covariance, phase optimization, initialization and failure controls |
| Gaussian controls | Full-covariance GMM fitter and earlier scalar representation studies | Independent multivariate controls, including optimization of the wrapped likelihood |
| Information metrics | Existing bounded full-mixture KL/Bhattacharyya and linear/angular MI paths | Reuse only within their documented limits; not a new metrics implementation |

See [scalar fitting](GVM_MIXTURE_FITTING.md), [fixed mixtures](GVM_MIXTURES.md),
[mixture MI](GVM_MIXTURE_MI.md) and the [historical comparison](GVM_MIXTURE_RESEARCH_PLAN.md).
The existing `Release61RepresentationStudy` fits GMMs in a training-selected
angle chart, then evaluates either a chart-normalized or wrapped law. Its
`gmm-wrapped-em` label does **not** mean EM optimized the wrapped likelihood.
That is a useful historical baseline, but insufficient as the strongest control
for a new general superiority claim. Retain the original results unchanged.

## Literature and implementation assessment

The sources below were inspected for the stated sections, not treated as proof
of the proposed Figaro adaptation. No external code or dataset was incorporated.

| Source and inspected scope | Useful finding | Limitation / decision |
| --- | --- | --- |
| Skhosana and Nakhaei Rad, [Model-based clustering using a new mixture of circular regressions](https://arxiv.org/html/2601.05345v1), 2026; introduction and methodology §§2.1–2.6 | Soft-responsibility EM, numerical regression updates, multiple starts and concentration estimation provide recent circular-regression context | Conditional circular regression is not Figaro's joint Gaussian-marginal, quadratic-phase law. Specialize independently; do not import its guarantees or printed equations without checks |
| Rocci, Gattone and Di Mari, [A data driven equivariant approach to constrained Gaussian mixture modeling](https://arxiv.org/pdf/1609.03317), v4, 2016; introduction and §§3–6 | Mixture likelihoods can have singular/spurious maxima. Covariance constraints relative to a transforming SPD target address scale dependence | Use as constraint-design guidance. Its affine-equivariance results do not automatically cover GVM phase bounds or the proposed optimizer |
| Greco, Saraceno and Agostinelli, [Robust Fitting of a Wrapped Normal Model to Multivariate Circular Data and Outlier Detection](https://giovsaraceno.github.io/papers/stats-04-00028-v2.pdf), 2021, DOI 10.3390/stats4020028; introduction and §2, equations 1–5 | Latent winding integers give a likelihood-based EM construction for wrapped normals | Adapt the ordinary soft-winding likelihood, not the paper's robust extensions. Our space has one angular coordinate, not a multi-angle torus |
| Powell, [The BOBYQA algorithm for bound constrained optimization without derivatives](https://optimization-online.org/wp-content/uploads/2010/05/2616.pdf), 2009; abstract/introduction, and [Commons Math 3.6.1 API](https://commons.apache.org/proper/commons-math/javadocs/api-3.6.1/org/apache/commons/math3/optim/nonlinear/scalar/noderiv/BOBYQAOptimizer.html) | An existing bounded, derivative-free local optimizer avoids writing a new optimizer | Candidate only; compare with an independent gradient-based solve. No global-optimum guarantee. Set bounds, radii, interpolation count and evaluation ceilings explicitly |
| [scikit-learn GaussianMixture](https://scikit-learn.org/stable/modules/generated/sklearn.mixture.GaussianMixture.html) and [mixture guide](https://scikit-learn.org/stable/modules/mixture.html); full-covariance, initialization, regularization and stopping documentation | An independent mature GMM fit can detect weaknesses in the existing Figaro control | Ordinary Gaussian EM is not wrapped EM. Diagonal regularization and spectral constraints are different objectives; do not require identical optima |
| [libDirectional](https://github.com/libDirectional/libDirectional/tree/8a5c791743ddce5463fe192182f98c576b847ace); `GaussVMDistribution.m`, `HypercylindricalWNDistribution.m`, and `COPYING` inspected | Contains the relevant joint GVM and partially wrapped-normal representations, not merely a similarly named toroidal law | Inspected GVM class has no fitting method and uses a Cholesky quadratic expression with an explicit restriction comment. The wrapped evaluator uses a fixed image count. Neither inspected class supplies the required qualified mixture fitter |

License/reuse decisions: libDirectional declares GPL-3.0 in `COPYING`; it is
reference-only here, with no source port. scikit-learn's BSD-3-Clause software
is a candidate isolated validation dependency, not a new Figaro runtime dependency.
Commons Math is an existing Apache-2.0 dependency. Pin actual versions and hashes
before experiments; a paper's accessibility is not a code/data license.
The SciPy online optimizer page timed out; installed SciPy 1.18.1's L-BFGS-B
implementation documentation was inspected instead (bounds, stopping and evaluation
limits). Its numerical-difference option can exceed `maxfun`, so the study must
enforce its own total-work counter rather than assume a solver option is a hard cap.
The 2007 Ingrassia–Rocci constrained-EM article was identified, but its inspected
author-repository full text was restricted; the accessible 2016 treatment above
is the basis used here. No paywalled PDF is needed to start the bounded plan.

The literature establishes useful ingredients, not a verified ready-made fitter
for this exact law. The concrete custom gap is their integration with Figaro's
joint quadratic phase, covariance convention, numerical refusal contracts and
fair wrapped-GMM controls. This is not a claim that no other implementation exists.

## Proposed model and numerical design

For `x` in R^d and one angle `theta`, preserve the current law:

```text
P = L L^T; z = L^-1 (x - mu)
phase(z) = alpha + beta^T z + 0.5 z^T Gamma z
p(x, theta) = Normal_d(x; mu, P) * VonMises(theta; phase(z), kappa)
```

`Gamma` is symmetric, including off-diagonal terms. This is not the toroidal
multivariate generalized von Mises distribution or a spherical vMF mixture.

1. **Keep angular coordinates fixed during fitting.** Use a single training-only
   reference frame `y = G^-1(x-c)` for phase coefficients. Updating a component's
   Gaussian mean/covariance must not silently change its conditional angular law.
   Export to the kernel frame using `y = a + B z`, where
   `a = G^-1(mu-c)` and `B = G^-1 L`:

   ```text
   alpha_kernel = alpha_ref + b^T a + 0.5 a^T C a
   beta_kernel  = B^T (b + C a)
   Gamma_kernel = B^T C B
   ```

   These are algebraic derivations to qualify independently in stage 1, not
   numerical results from this assessment. Preserve full cross terms and wrap
   only the angular intercept, not the linear predictors.
2. **Constrain covariance in declared units.** Freeze an SPD training reference
   and explicit relative eigenvalue bounds. Reject rank-deficient reference
   data; do not add undisclosed jitter. Record covariance-bound hits, component
   responsibility mass and effective sample size. Box bounds on phase in a fixed
   frame do not confer arbitrary rotation equivariance on the full fitter.
3. **Use the periodic likelihood.** With fixed responsibilities, profile the
   angular intercept using `atan2` of the residual resultant and concentration
   using the Bessel ratio. Optimize squared resultant magnitude over linear and
   quadratic coefficients. At zero resultant, the angle is unidentified; use a
   canonical zero-concentration result and report weak identification. Near-zero
   concentration, finite-sample aliasing and label switching must not be reported
   as recovery of unique coefficients.
4. **Reuse optimizers, with bounded work.** Try existing Commons Math BOBYQA with
   explicit bounds and a small frozen set of zero/linear/quadratic starts.
   Independently check gradients and an isolated SciPy L-BFGS-B reference.
   Optimizer agreement is not proof of a global optimum. Avoid extending scalar
   sorted-angle unwrapping into an unjustified multivariate ordering.
5. **Make failures part of the result.** Check responsibility-weighted objective
   improvement and the full observed-data log score. Retain the last valid
   candidate on an unsuccessful update, but return its real failure/budget status,
   not `Converged`. Count all starts, rejected steps, phase-point evaluations and
   full-density evaluations. Never silently delete or reseed a failed component.

## Gaussian controls and complexity accounting

Use three control roles: existing chart-fit GMMs as historical baselines;
independently fitted ordinary GMMs on suitable local charts as software checks;
and **genuinely wrapped-likelihood fitted GMMs** as the principal circular control.
The last needs soft responsibilities over both component and winding integer.
For R^d × S1 there is only one winding index; increasing linear dimension does
not create a multi-angle winding grid.

Evaluate the wrapped law through the conditional angular Gaussian given the
linear coordinates. Qualify image-sum truncation against independent high-precision
references with an explicit log-density error budget, including seam and tail
cases. A fixed number of images or a tail-mass heuristic alone is insufficient.
Refuse unresolved evaluations. Charge optimization, preprocessing and numerical
refinement to the control as well as the candidate. If this control cannot be
qualified within budget, stop short of comparative promotion rather than use a
weaker comparator without disclosure.

Parameter counts below are derived, not measured performance:

| Linear dimension d | Phase search variables d+d(d+1)/2 | GVM parameters/component | GMM parameters/component in d+1 coordinates |
| --- | ---: | ---: | ---: |
| 2 | 5 | 12 | 9 |
| 3 | 9 | 20 | 14 |
| 5 (deferred) | 20 | 42 | 27 |

For K components add K-1 mixture weights: GVM total
`K*(d+1)*(d+2)+K-1`; GMM total `K*(d+1)*(d+4)/2+K-1`.
Equal K is not equal complexity. Compare common parameter ceilings and measured
training/evaluation cost, reporting accuracy-cost frontiers. State each family's
different regularization constraints. Choose components on training/validation
data only; neither family may choose K using confirmation test scores.

## Three bounded stages

### 1. Qualify semantics, independent references and controls

- Specify IID unweighted inputs, `d=2,3`, GVM `K=1..3`, and GMM `K=1..4`.
  Include `d=1` compatibility controls; defer higher dimensions and multiple angles.
- Independently implement reference densities and coordinate transformations.
  Check scalar reduction, cross terms, positive/negative curvature, periodicity,
  linear-coordinate Jacobians, zero concentration and known moments. Test gradients
  by finite differences away from nondifferentiable constraints.
- Qualify ordinary and soft-winding Gaussian controls before interpreting GVM gains.
  Retain source/solver independence; do not use the candidate fitter as its oracle.
- Freeze data laws, train/validation/test partitions, development/confirmation seed
  sets, reference tolerances, objective bounds and statistical comparisons before
  confirmation. Publish the protocol and acceptance criteria, including all failures.

Exit: independent numerical controls pass over the declared envelope. Proposed
moderate-range pointwise log-density tolerance is `1e-8` absolute, with separately
justified tail/conditioning tolerances frozen before candidate results. Failure to
qualify a control is a stop, not permission to relax its tolerance post hoc.

### 2. Build and calibrate the test-only bounded fitter

- Fit in the fixed reference frame with full covariance and full quadratic phase.
  Test monotone updates, evaluation accounting, deterministic replay, nonfinite
  inputs, tiny components, rank deficiency, bound hits and exhausted budgets.
- Initial research envelope: at most 5,000 training points, three restarts and
  50 outer iterations; concentration `0..50` to stay within the existing MI
  diagnostic's concentration envelope. Existing public caps remain untouched.
- Proposed hard per-fit ceiling: 20 million phase-point contributions and
  120 seconds, including all starts. Record other density work separately.
  Calibrate optimizer-call caps and scale constraints on development fixtures,
  then freeze them; budget exhaustion remains an outcome. No confirmation tuning.
- Compare the new `d=1` specialization with the unchanged public scalar fitter
  on the same qualified controls, without requiring identical local optima.

Exit: all deterministic correctness/failure-path controls pass, every fit has a
truthful status, and the complete confirmation protocol is frozen. No public API
or promotion at this stage.

### 3. Run independent held-out representation and cost qualification

Use eight primary cells: each of `d=2,3` crossed with curved/cross-term GVM,
seam-crossing GVM mixture, nearly local Gaussian, and wrapped-Gaussian mixture
truths. Separate adversarial controls cover misspecification, weak concentration,
unequal weights, overlap and near-degenerate training data. These are explicit
synthetic controls, not claimed application measurements.
Gaussian-generated truths wrap their angular coordinate onto the same circle;
the nearly local case uses a narrow angular spread, not a different support measure.

Proposed confirmation size: 16 independent seeds per cell, 2,000 training points,
2,000 validation points and 20,000 independently drawn test points per seed; two
common parameter ceilings, selected and frozen in stage 1. Predeclare K for each
family/ceiling rather than run an unbudgeted model-selection sweep. Three primary
methods (GVM, chart GMM, wrapped-likelihood GMM) give 768 method/configuration fits;
the independent ordinary-GMM software cross-check belongs to stage 1. Cap the whole
study at 1,024 method/configuration fits, each with at most three starts, and eight
serial-equivalent hours. Charge development, controls and every restart to those
limits and the per-fit work counter. If needed, reduce the planned grid **before**
freezing, not after seeing confirmation outcomes. An incomplete grid cannot pass.

Primary outcome is paired held-out log score, accompanied by predeclared-region
probability errors, failure rates, fitting/density/sampling cost and memory.
Use independent training replicates as the unit of uncertainty, not thousands of
test points as independent fits. Report all outcomes; conditional-on-success
scores must sit beside refusal rates, never replace them. Include both families'
winning cases and cost/parameter-matched frontiers, not just equal-component plots.

Before confirmation, freeze numeric practical-effect and noninferiority margins on
development data. Promotion would require zero unresolved correctness failures,
at least 95% completed/qualified fits in each primary method/cell, a paired
confidence interval supporting a practically meaningful gain on the predeclared
curved target cases, and acceptable Gaussian-control loss at matched resources.
Adjust for the declared multiple comparisons. These are proposed gates, **not
results**; no universal superiority or automatic choice follows from passing.

Use true-density forward KL estimates as a secondary check on log-score results;
reverse KL, Bhattacharyya and MI need their own independent samples/error budgets
and documented numerical limits. Componentwise metric averages are not mixture
metrics. Do not let a difficult optional metric silently remove a failed primary
case. Archive input hashes, exact code/configuration/versions, captured execution
logs, all machine-readable outcomes and rerun instructions before reporting results.

## Decision and remaining uncertainty

Proceed through the three approved stages in order. The strongest counterargument is that the
periodic objective may need enough starts to erase GVM's compact representation
advantage; a properly optimized wrapped GMM may already be adequate. Larger
covariance and phase parameter sets also increase data requirements. Existing
scalar results do not answer either question. Stop or narrow scope if controls
cannot be qualified, fits are fragile, or only uncharged-compute gains appear.

Public integration is a later decision after independent validation, with a
separate API/compatibility review. This plan does not expand inference-graph
support, fitting of correlated/weighted samples, automatic K selection, arbitrary
dimension, outlier-robust estimation or unsupported numerical regimes.

## Assessment verification

The original assessment recorded source/code inspection and derived design
proposals, not numerical results. Subsequent execution follows the linked protocol.
The existing scalar implementation and historical studies are unchanged. Repository
documentation checks validate links and reference structure, not the scientific
or statistical gates proposed above.
