# Distribution support: present capability and gaps

## What exists now

Initial source inventory at `b99c5d56` (2026-09-07), updated 2026-09-09 through the multivariate-t/numerical-information milestone.
This is an API inventory, not new numerical certification of every existing distribution.
The [roadmap](../ROADMAP.md) defines delivery gates; the [wishlist](../WISHLIST.md)
collects broad families and their later flavors.

| Present native entry points | Source | Scope / caution |
| --- | --- | --- |
| AffineDistribution, ExpDistribution, TruncatedDistribution, ScalarMixtureDistribution, ZeroAdjustedDistribution | [Construction guide](DISTRIBUTION_CONSTRUCTIONS.md) | Initial reusable transformations, finite-interval conditioning, continuous mixtures and count-only hurdle/zero inflation; explicit numeric refusals, no general mixed measure |
| GaussianDistribution, MultivariateGaussianDistribution, GaussianMixtureDistribution, GaussianMixture | [GMM and Gaussian guide](DISTRIBUTION_CONSTRUCTIONS.md) | Scalar and full-covariance vector kernels, GMM likelihoods/draws/moments/responsibilities/marginals; fixed/dynamic Figaro GMM adapters, not EM fitting |
| GaussianInformation | [Gaussian information API](DISTRIBUTION_CONSTRUCTIONS.md#gaussian-information) | Analytic Gaussian KL/Bhattacharyya and MI between blocks of one joint Gaussian; not mixture divergence or mixture MI |
| StudentT, Cauchy, Laplace, LogNormal, Weibull, Triangular, Kumaraswamy | [Common-family guide](COMMON_DISTRIBUTIONS.md) | Immutable numeric kernels plus fixed/dynamic observation-ready adapters; direct log densities, CDF/survival/quantiles, scoped RNG and tested importance/MCMC paths; not exact factors or fitting |
| MultivariateStudentT | [Elliptical vector t](MULTIVARIATE_STUDENT_T.md) | Full-rank kernel, exact marginals, fixed/dynamic graph elements, explicit proposals and numerical information metrics; no singular density, multivariate CDF or analytic general t divergence |
| NegativeBinomial, Hypergeometric | [Count conventions](COMMON_DISTRIBUTIONS.md) | Real positive shape/failure count and finite-population draws respectively; explicit Int-range and work limits |
| ScalarDivergence, CountDivergence, DiscreteInformation | [Information measures](COMMON_INFORMATION_METRICS.md) | Same-family KL/Bhattacharyya and explicit finite-joint-table MI; analytic or guarded estimated results, not arbitrary cross-family/joint-model inference |
| GVM mutual information | [MI guide](GVM_MUTUAL_INFORMATION.md) | Fixed complete linear vector versus angle; integrated on main with D3, separate from generic marginal comparisons |
| GaussVonMises, GaussVonMisesDistribution, LinearAngular (development preview) | [Joint guide](GAUSS_VON_MISES.md) | Fixed kernel and complete joint observations on main at CI-verified `3615e26e`; standalone publication approved, no replacement tagged library release |
| GVM canonical residuals, squared Mahalanobis score, analytic KL | [Diagnostics guide](GVM_DIAGNOSTICS.md) | Individual fixed GVMs, same coordinate meanings; deterministic kernel utilities, not calibrated gates or mixture/posterior fitting |
| GVM analytic circular/mixed moments and conditionalAngle | [Moments guide](GVM_MOMENTS.md) | Fixed-kernel physical-coordinate first/second mixed moments and exact angle given the full linear vector; no general posterior moments or Gaussian reverse-conditional claim |
| GVM finite-concentration score CDF, survival and quantiles | [Calibration guide](GVM_SCORE_CALIBRATION.md) | Known fixed kernel; depends only on dimension and kappa; numerical error estimates, not a parameter confidence interval or arbitrary posterior calibration |
| GVM analytic state gradients | [Gradient guide](GVM_GRADIENTS.md) | Log-density and squared-score partials in physical coordinates and per radian; fixed parameters, no graph autodiff, fitting or new sampler |
| GVM third-order sparse expectation quadrature | [Quadrature guide](GVM_QUADRATURE.md) | Fixed kernel; 2n+3 signed-weight nodes, scalar/vector callbacks; limited exactness class, no general error bound, refinement or positivity guarantee |
| GVM positive-weight tensor expectation reference | [Tensor guide](GVM_TENSOR_QUADRATURE.md) | Adjustable Gaussian/angular order and streamed points; A*G^n callbacks with a budget guard; positive estimates do not certify accuracy |
| GVM budgeted order comparison | [Comparison guide](GVM_QUADRATURE_COMPARISON.md) | Four Gaussian/angular order combinations, total callback preflight and per-output sensitivity; agreement is not certified accuracy |
| GVM Bhattacharyya comparison | [Guarded API](GVM_BHATTACHARYYA.md) | Symmetric fixed-law overlap; exact reductions and bounded series with unresolved statuses; initial nonidentity concentration limit 50 and dimension limit 32 |
| VonMises, VonMisesDistribution, CircularStatistics | [Circular foundation](VON_MISES.md) | Native element, independent numeric kernel and equal-weight summaries; radians, finite concentration up to `1e8`; tested evidence and isolated parallel paths, not joint GVM |
| Bernoulli (`Flip`), categorical (`Select`), point mass (`Constant`) | [Flip](../Figaro/src/main/scala/com/cra/figaro/language/Flip.scala), [Select](../Figaro/src/main/scala/com/cra/figaro/language/Select.scala), [Constant](../Figaro/src/main/scala/com/cra/figaro/language/Constant.scala) | `Flip` is Boolean; `Select` samples one category, not a multinomial count vector |
| Binomial, Geometric, Poisson | [Discrete elements](../Figaro/src/main/scala/com/cra/figaro/library/atomic/discrete) | Check each support and parameter convention before adapting another library's call |
| Discrete Uniform and FromRange | [Uniform](../Figaro/src/main/scala/com/cra/figaro/library/atomic/discrete/Uniform.scala), [FromRange](../Figaro/src/main/scala/com/cra/figaro/library/atomic/discrete/FromRange.scala) | Finite discrete choices/ranges; distinguish from continuous Uniform |
| Beta, Gamma, InverseGamma, Exponential, Normal, continuous Uniform | [Continuous elements](../Figaro/src/main/scala/com/cra/figaro/library/atomic/continuous) | `Normal` takes variance; Gamma uses shape/scale. Existing element-specific parameter overloads are not uniform across the library |
| Dirichlet, MultivariateNormal | [Dirichlet](../Figaro/src/main/scala/com/cra/figaro/library/atomic/continuous/Dirichlet.scala), [MultivariateNormal](../Figaro/src/main/scala/com/cra/figaro/library/atomic/continuous/MultivariateNormal.scala) | Simplex and vector outputs; not generic matrix/manifold distributions |
| KernelDensity | [KernelDensity](../Figaro/src/main/scala/com/cra/figaro/library/atomic/continuous/KernelDensity.scala) | Existing empirical-density element; not automatic fitting for every named family |
| Dist, Chain, Apply | [Dist](../Figaro/src/main/scala/com/cra/figaro/language/Dist.scala), [Chain](../Figaro/src/main/scala/com/cra/figaro/language/Chain.scala), [Apply](../Figaro/src/main/scala/com/cra/figaro/language/Apply.scala) | Composition tools, not evidence that every induced distribution has an observation-ready density |

`SwitchingFlip` and `OneShifter` in the discrete package are modeling helpers, not broad
new distribution families. Distribution algorithms available inside Apache Commons Math
are dependencies, not automatically exposed Figaro elements. In particular,
`util.sampleMultinomial` selects a categorical outcome; its name is not evidence of a
first-class multinomial count-vector law.

## Composition is useful, but not the same support level

Examples of mathematical reuse opportunities are a Rademacher law using `Select`,
arcsine using Beta(1/2, 1/2), Erlang/chi-squared using Gamma with appropriate parameters,
and beta-binomial via a random success probability.
These are candidate constructions, not new tested APIs supplied here.
Lognormal now has a native observation-ready element; use it instead of assuming that
`Apply(normal, math.exp)` supplies the transformed observation likelihood automatically.

An `Apply` transformation can generate the right prior samples without furnishing the
right likelihood for an exactly observed transformed continuous node. A monotone change
of variables needs its Jacobian; truncation needs a normalizer; many-to-one wrapping
needs summed images or another equivalent derivation. Mixtures with atoms need a mixed
mass/density contract. Factor conversion and learning require additional support.

## Gaps to resolve before claiming broader compatibility

- Circular von Mises now supplies periodic evidence and circular summaries. Cylindrical
  Gauss-von Mises has a locally tested preview approved for standalone publication. Angular diagnostics need explicit treatment;
  arithmetic averaging across the angle boundary is not a sound default.
- `MultivariateNormal` supplies an atomic density but its `logp` implementation currently
  returns `Double.NegativeInfinity`. Do not use that method as a valid GVM log-density
  building block. This is a concrete audit item, not evidence that every MVN inference
  path fails: inspect the actual method used by each algorithm.
- [HasDensity](../Figaro/src/main/scala/com/cra/figaro/language/HasDensity.scala) documents
  raw density-ratio underflow/overflow risks. New stable log-density calculations do not
  fix all downstream callers automatically. Verify or explicitly restrict those paths.
- New [HasLogDensity](../Figaro/src/main/scala/com/cra/figaro/language/HasLogDensity.scala)
  opts into direct log-density likelihood weighting. Von Mises, GVM and the nine new
  common-family adapters use it; other existing
  distributions are not silently migrated. Audit their mathematical log densities and
  actual callers before broader adoption. Extreme legacy MH proposal ratios can still
  be unrepresentable and the new trait fails explicitly in that case.
- [Continuous](../Figaro/src/main/scala/com/cra/figaro/language/Continuous.scala) declares
  `logp`; its old observation override is commented out. Implementing this trait alone
  does not establish that the intended likelihood path is used.
- The explicit-vector samplers consume a supplied Euclidean log density. A periodic
  angular density repeated across the entire real line is not a normalized Euclidean
  target. A branch-cut representation or a manifold-aware method needs its own tests.

Remaining issues are recorded for targeted work. The circular tests do not broaden
validation claims for old distributions or unrelated inference algorithms.

### Log-density reliability versus performance

Likelihood weighting already accumulates log weights, but its legacy observed-density
path first calls `density` and then `log`. A tiny positive density can become zero
before that conversion. Direct log density preserves distinctions between very small
likelihoods; it does not fix proposal mismatch or guarantee higher effective sample size.
Avoiding an exponential/logarithm round trip may also save work, but no speedup has
been measured for this change.

The historical `SamplingBenchmark` fixtures use unobserved Normal draws or moderate
Boolean weights. `MultiChainMcmcBenchmark` and `StoppingCriteriaValidation` use direct
log constraints for their continuous likelihoods. Thus this particular observed-density
conversion is not an explanation for those timing or undercoverage results. Other
density-ratio paths require a separate audit; this is not a blanket numerical clearance.
A future audit should compare direct and legacy scoring on moderate and extreme
observations, measuring estimates, zero weights, effective sample size and elapsed time.

## Reuse strategy

Information metrics are a parallel capability track, not implied by density/sampling
support. See the [cross-family roadmap](INFORMATION_METRICS_ROADMAP.md) for KL,
Bhattacharyya and MI applicability and acceptance criteria. The initial
[GVM MI diagnostic](GVM_MUTUAL_INFORMATION.md) concerns one complete linear vector
and one angle; it does not provide generic MI for every scalar distribution.

First inspect the already-used [Apache Commons Math distribution APIs](https://commons.apache.org/proper/commons-math/javadocs/api-3.6.1/org/apache/commons/math3/distribution/package-summary.html).
The new common families reuse this existing dependency's special functions, normal inverse
and hypergeometric probabilities, with independently checked formulas, bounded inverses
and caller-owned randomness. They add no runtime dependency. A future wrapper still needs
parameter translation, Figaro-owned RNG integration, density checks and inference tests.

For circular numerical cross-checks, [SciPy's von Mises API](https://docs.scipy.org/doc/scipy/reference/generated/scipy.stats.vonmises.html)
is a candidate independent oracle, not a proposed Python runtime dependency. Pin its
version when generating checked fixtures and record its branch-cut/CDF conventions.
Review exact licenses before importing source. A public repository without a license
is not treated as permission to copy its implementation.
