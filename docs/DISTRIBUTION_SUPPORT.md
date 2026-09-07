# Distribution support: present capability and gaps

## What exists now

Initial source inventory at `b99c5d56` (2026-09-07), updated for the circular foundation.
This is an API inventory, not new numerical certification of every existing distribution.
The [roadmap](../ROADMAP.md) defines delivery gates; the [wishlist](../WISHLIST.md)
collects broad families and their later flavors.

| Present native entry points | Source | Scope / caution |
| --- | --- | --- |
| GaussVonMises, GaussVonMisesDistribution, LinearAngular (local preview) | [Joint guide](GAUSS_VON_MISES.md) | Fixed-parameter kernel and complete joint observations tested locally; not on main or publicly released, review gate open |
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
beta-binomial via a random success probability, and lognormal using `exp` of a Normal.
These are candidate constructions, not new tested APIs supplied here.

An `Apply` transformation can generate the right prior samples without furnishing the
right likelihood for an exactly observed transformed continuous node. A monotone change
of variables needs its Jacobian; truncation needs a normalizer; many-to-one wrapping
needs summed images or another equivalent derivation. Mixtures with atoms need a mixed
mass/density contract. Factor conversion and learning require additional support.

## Gaps to resolve before claiming broader compatibility

- Circular von Mises now supplies periodic evidence and circular summaries. Cylindrical
  Gauss-von Mises has a locally tested preview with public release review open. Angular diagnostics need explicit treatment;
  arithmetic averaging across the angle boundary is not a sound default.
- `MultivariateNormal` supplies an atomic density but its `logp` implementation currently
  returns `Double.NegativeInfinity`. Do not use that method as a valid GVM log-density
  building block. This is a concrete audit item, not evidence that every MVN inference
  path fails: inspect the actual method used by each algorithm.
- [HasDensity](../Figaro/src/main/scala/com/cra/figaro/language/HasDensity.scala) documents
  raw density-ratio underflow/overflow risks. New stable log-density calculations do not
  fix all downstream callers automatically. Verify or explicitly restrict those paths.
- New [HasLogDensity](../Figaro/src/main/scala/com/cra/figaro/language/HasLogDensity.scala)
  opts into direct log-density likelihood weighting. Von Mises uses it; existing
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

First inspect the already-used [Apache Commons Math distribution APIs](https://commons.apache.org/proper/commons-math/javadocs/api-3.6.1/org/apache/commons/math3/distribution/package-summary.html).
They offer potential implementations for Student t, Cauchy, Laplace, Weibull, lognormal,
hypergeometric and other common scalar laws. A wrapper still needs parameter translation,
Figaro-owned RNG integration, density checks and inference tests. Avoid adding a large
runtime dependency just to wrap one existing algorithm.

For circular numerical cross-checks, [SciPy's von Mises API](https://docs.scipy.org/doc/scipy/reference/generated/scipy.stats.vonmises.html)
is a candidate independent oracle, not a proposed Python runtime dependency. Pin its
version when generating checked fixtures and record its branch-cut/CDF conventions.
Review exact licenses before importing source. A public repository without a license
is not treated as permission to copy its implementation.
