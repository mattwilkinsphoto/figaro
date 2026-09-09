# Cross-family information and divergence roadmap

## Intent

User-approved direction: carry KL, Bhattacharyya and mutual information forward as
capabilities across Figaro's distribution families, not isolated GVM conveniences.
Preserve Mahalanobis-style scoring where its geometry is meaningful. Start with broad
families and validated representative laws, then add specialized variants.

This is a capability roadmap, not a claim that every existing distribution already
supports every operation. Do not add a universal method returning misleading numbers
for incompatible supports or a generic estimator without a tested error contract.

## Distinct questions, distinct contracts

| Capability | Inputs and question | Required distinctions |
| --- | --- | --- |
| Directed KL | Two laws P,Q: expected log density ratio under P | Direction matters; support mismatch can give genuine positive infinity. State the common reference measure. |
| Bhattacharyya divergence | Two laws: negative log integral/sum of square-root density product | Symmetric; zero overlap can give infinity. Affinity is in [0,1]; divergence is not generally a metric with a triangle inequality. |
| Mutual information | One joint law and an explicit partition A,B: KL of joint versus product of marginals | Dependence within the joint model, not distance between arbitrary laws. A scalar marginal alone does not specify MI. |
| Mahalanobis-style score | State/residual and valid covariance or family-specific geometry | Not an interchangeable information divergence; document square versus square root and degeneracy. |

Use natural logarithms/nats as the default, with explicit conversion to bits. Entropy
uses a specified measure/coordinate convention; MI is invariant under invertible
transformations within each variable block. For background see the
[MIT information-measure lectures](https://ocw.mit.edu/courses/6-441-information-theory-spring-2016/pages/lecture-notes/).

## Current representative: joint Gauss-von Mises

| Operation | Current support | Remaining limits |
| --- | --- | --- |
| KL | [Analytic Gaussian/angular decomposition](GVM_DIAGNOSTICS.md) | Fixed matching-coordinate laws, with documented numeric safeguards. |
| Bhattacharyya | [Guarded Fourier method](GVM_BHATTACHARYYA.md), plus [opt-in positive scalar method](GVM_SCALAR_BHATTACHARYYA.md) | Bounded ranges/work, explicit numerical refusals; no certified generic integration. |
| MI | [Linear-vector/angle diagnostic](GVM_MUTUAL_INFORMATION.md), integrated with D3 | One fixed law; full vector versus single angle; estimated numerical errors and initial parameter caps. |
| Mahalanobis-style score | [Canonical residual score](GVM_DIAGNOSTICS.md) and [finite-concentration calibration](GVM_SCORE_CALIBRATION.md) | GVM-specific geometry; not universally chi-square at finite concentration. |

This table is not a complete audit of metrics elsewhere in Figaro. The initial GVM MI
implementation does not introduce sample-based estimators or generalized fusion.

## Delivery sequence

Modern.19 adds [LegacyInformation](LEGACY_DISTRIBUTION_CONTRACTS.md): analytic
Gamma, inverse-Gamma, Beta, Dirichlet, Poisson, Geometric and common-trial-count
Binomial KL/Bhattacharyya. Gaussian metrics already exist; Exponential reduces to
shape-one Gamma. A scalar marginal alone still does not define mutual information.

Modern.18 adds [selected D5 information support](DISTRIBUTION_BREADTH.md):
GEV/GPD analytic and guarded scalar comparisons, multinomial category reductions,
Wishart normalizer comparisons and spherical S2 von Mises-Fisher comparisons.
MI between complementary multinomial category-count blocks uses their binomial
subtotal entropy. Matrix-block and spherical-coordinate MI remain open work;
neither is silently substituted with a generic Lebesgue-density estimator.

Delivered representative expansion: [common-family information measures](COMMON_INFORMATION_METRICS.md)
now cover all nine D3 kernels through same-family analytic/guarded KL and Bhattacharyya,
plus finite categorical KL/Bhattacharyya and explicit joint-table MI. Shared result/status
conventions distinguish mathematical infinity from numerical refusal. See
[acceptance evidence](COMMON_DISTRIBUTIONS_ACCEPTANCE.md) for integration status.
This advances INFO-02 through INFO-04; it does not finish their full cross-family scope.
The D4 [Gaussian extension](DISTRIBUTION_CONSTRUCTIONS.md) implements scalar/vector Gaussian
KL/Bhattacharyya and partitioned multivariate Gaussian MI; see its
[acceptance status](DISTRIBUTION_CONSTRUCTIONS_ACCEPTANCE.md). It also adds GMM kernels,
but originally not divergences/MI between or within mixtures. The modern.14
[fixed-budget Monte Carlo extension](MONTE_CARLO_INFORMATION.md) now implements
full-law mixture KL/Bhattacharyya and exact-marginal partition MI through normalized
vector adapters, including unlike compatible continuous families. Its MCSE is
explicitly distinct from deterministic numerical error or a calibrated confidence
bound. Existing-family adapter audits, analytic mixture bounds and more efficient
specialized estimators remain work.

1. **INFO-01: GVM representative delivered.** MI includes mathematical reduction,
   independent numerical controls, caller-visible limits, examples, packaging and passing CI.
   Its result contract remains distinct from two-law divergence APIs; numerical extensions
   beyond the initial bounds still require their own validation.
2. **INFO-02: inventory and shared conventions.** For each family in the
   [distribution inventory](DISTRIBUTION_SUPPORT.md), record existing/native/composable
   support, analytic versus numerical methods, compatible measures and missing operations.
   Identify genuinely reusable primitives after at least two families need them; avoid
   forcing every distribution into an unsuitable base trait.
3. **INFO-03: broad exact representatives.** Prioritize finite discrete laws and
   Gaussian families for KL/Bhattacharyya; use explicit joint probability tables and
   partitioned multivariate Gaussians for MI. Audit existing APIs before adding duplicates.
   Circular and common exponential-family specializations follow the family-first plan.
4. **INFO-04: guarded numerical extensions.** Add integration/Monte Carlo approaches
   only where analytic reductions are unavailable. Specify convergence checks, bias,
   dependence assumptions, support matching, cancellation and work budgets. Keep
   sample-estimated uncertainty separate from deterministic quadrature error estimates.
5. **INFO-05: specialized forms.** Mixtures, copulas, directional/higher-dimensional
   joint laws and conditional MI need separate mathematical and computational contracts.
   They are later variants, not automatically covered by a scalar family's support.

## Acceptance checklist for every family

- Specify support/reference measure, units, variable partition (MI), parameterization,
  finite/singular cases, incompatible inputs and whether positive infinity is legitimate.
- Separate a mathematically infinite divergence from overflow or unresolved arithmetic.
  Never replace a refusal with zero. Report analytic versus estimated status honestly.
- Check identity/independence, direction or symmetry, nonnegativity, coordinate
  invariance where applicable, disjoint support, and relevant limiting families.
- Use independent oracles and test the complete published API, not just a formula
  copied into both implementation and expected-value code.
- For same-covariance Gaussians, check `KL = squared Mahalanobis / 2` and
  `Bhattacharyya = squared Mahalanobis / 8`; these are restricted reductions, not
  statements that every KL or MI “is Mahalanobis.” For a nonsingular Gaussian partition,
  check `MI = log(det(Sigma_A)*det(Sigma_B)/det(Sigma_AB))/2`, computed using stable
  factorizations rather than raw determinants.
- Document every public operation, three practical patterns, limitations and how to
  choose the appropriate quantity. Add reproducible tests, artifacts and CI coverage.

## Related and exclusions

[Family-first roadmap](../ROADMAP.md), [wishlist](../WISHLIST.md),
[GVM MI guide](GVM_MUTUAL_INFORMATION.md), and [support inventory](DISTRIBUTION_SUPPORT.md).
This work remains domain-independent probability software. No report ingestion,
data-fusion, tracking, filtering or propagation implementation is authorized by this plan.
