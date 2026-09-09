# Monotone, truncated, folded, wrapped and count-mixture laws

## Overview

These reusable constructions avoid writing a new likelihood implementation for each
derived distribution. Use them when transforming an existing quantity, restricting
its range, removing its sign, representing a periodic angle or mixing count regimes.
Those operations define different probability laws; none is merely display formatting.

## Quick start (three steps)

```scala
import com.cra.figaro.library.atomic.continuous.*
import com.cra.figaro.language.*
// 1. Select the base and operation: a Gaussian conditioned to be positive.
val law=TruncatedDistribution(GaussianDistribution(0,1),0,Double.PositiveInfinity)
// 2. Inspect probability or sample with an explicitly owned scientific RNG.
println((law.logDensity(1),law.cdf(1),law.quantile(.95)))
val draw=law.sample(com.cra.figaro.util.SamplingRandom.scalaRandom(42))
// 3. Use the same kernel in a Figaro model, including observed likelihoods.
Universe.createNew()
val value=ScalarElement(law)
value.observe(1.0)
```

## Construction APIs and return contracts

| Constructor | Parameters | Result / example |
| --- | --- | --- |
| `MonotoneDistribution(base,transform)` | Continuous law and pure immutable `MonotoneTransform` | Jacobian-correct push-forward; `MonotoneDistribution(LogNormalDistribution(0,1),MonotoneTransform.Log)` |
| `TruncatedDistribution(base,lower,upper)` | Ordered non-NaN bounds within base support; either may now be infinite | Conditional law, not censoring; `TruncatedDistribution(GaussianDistribution(0,1),8,Double.PositiveInfinity)` |
| `FoldedDistribution(base)` | Continuous scalar law | Law of abs(X), adding both density branches; `FoldedDistribution(GaussianDistribution(1,1))` |
| `WrappedCauchyDistribution(location,rho)` | Mean direction in [-Pi,Pi] radians; resultant length rho in [0,.9999] | Fixed principal-chart law on [-Pi,Pi], including uniform rho=0 |
| `CountMixtureDistribution(weights,components)` | 1..128 matching immutable count laws; nonnegative weights summing to one within 1e-12 | Integer-valued mixture; zero-weight components ignored |

Scalar kernels implement the existing [scalar contract](DISTRIBUTION_CONSTRUCTIONS.md):
`logDensity(x)`/`density(x)` return log/density in output units; `cdf(x)` and
`survival(x)` return lower/upper probabilities; `quantile(p)` accepts probabilities
and returns the inverse CDF; `sample(rng)` uses only the supplied RNG; `support`
returns endpoint limits. `mean`/`variance` return `None` for the new generic scalar
constructions: their moments are not calculated. `retainedProbability` on truncation
is the base mass inside the interval. For example, `law.retainedProbability` is .5
for the quick-start half-normal law.

`MonotoneTransform` requires `domain: (Double,Double)`, `increasing: Boolean`,
`forward(x)`, `inverse(y)` and `logInverseJacobian(y)`. The last returns
log(abs(dx/dy)), not log(abs(dy/dx)). Endpoint calls must return appropriate limits.
The provided `MonotoneTransform.Log` maps positive inputs to their natural logarithm.
Custom transforms are a caller contract, not a proof checked by finite probes.
Decreasing transforms reverse CDF/survival; their quantiles use guarded inversion
to avoid losing small tail probabilities through `1-p` subtraction.

Wrapped Cauchy additionally provides `angularLogDensity(angle)` for any finite
periodically wrapped angle, `circularMean: Option[Double]` (None for rho=0), and
`circularVariance=1-rho`. Ordinary `logDensity` is zero density outside its fixed
principal chart, consistent with `ScalarDistribution`; angular wrapping is explicit.
`cdf`/`survival` refer to that chart, not an origin-free circular ordering. Interior
quantile probabilities below 1e-12 or above 1-1e-12 are refused; endpoints 0/1 return
chart limits. Generic infinite wrapped sums, wrapped normal and circular point masses
are not implemented here.

Count mixtures implement `logProbability(k)`, `probability(k)`, `cdf(k)`,
`survival(k)`, `quantile(p)`, `sample(rng)`, `support`, `mean`, `variance` using the
existing integer-count contract. `support._2=None` means unbounded support; p=1
then throws, as do unrepresentable Int quantiles. `responsibilities(k)` returns the
component probabilities in original order and throws off positive mixture support.
Sampling uses the inherited bounded integer inverse-CDF search, not a new fast
component sampler. Example: `counts.responsibilities(3)`.

## Three common patterns

**Transform a positive quantity into log coordinates:**
```scala
val positive=LogNormalDistribution(.3,1.2)
val logarithm=MonotoneDistribution(positive,MonotoneTransform.Log)
// logarithm is N(.3,1.2^2), including the Jacobian in its likelihood.
println(logarithm.logDensity(0))
```

**Compare folding and truncation explicitly:**
```scala
val base=GaussianDistribution(1,1)
val magnitude=FoldedDistribution(base)
val positiveOnly=TruncatedDistribution(base,0,Double.PositiveInfinity)
println((magnitude.density(1),positiveOnly.density(1))) // Different laws.
```
Folding moves negative outcomes to positive values. Truncation conditions them out.
Clipping would create a point mass and needs a different mixed-measure contract.

**Mix count regimes and inspect information:**
```scala
import com.cra.figaro.library.atomic.discrete.*
val low=HypergeometricDistribution(20,3,5)
val high=HypergeometricDistribution(20,15,5)
val counts=CountMixtureDistribution(Vector(.4,.6),Vector(low,high))
val observedCount=CountElement(counts)
observedCount.observe(2)
println(CountMixtureInformation.componentMutualInformation(counts))
```

## Information APIs

`ScalarDivergence.kl(p,q,tolerance=1e-6,maxEvaluations=16384,cancelled=()=>false)`
and `bhattacharyya` retain their result/status contracts. A shared **same instance**
of a declared bijection delegates to the base comparison by invariance. Different
custom transforms return Unsupported. Wrapped-Cauchy KL is analytic via its
Poisson-kernel identity; Bhattacharyya is guarded scalar quadrature. Folded/truncated
comparisons use guarded quadrature only for the explicitly supported positive-
interval base families (Gaussian, t, Cauchy, Laplace, lognormal, Weibull, triangular,
Kumaraswamy and their affine/exp constructions). Arbitrary gapped laws are not given
false support-based infinity claims. Numerical errors remain estimates, not certificates.

`CountMixtureInformation.kl(p,q,tolerance=1e-8,maxTerms=100000)` and
`bhattacharyya` compare complete finite count laws in a common integer measure.
`componentMutualInformation(law,tolerance=1e-8,maxTerms=100000)` measures the
explicit component-label/count joint. `maxTerms` limits union-support counts for
two-law comparisons and total component-by-count cells for MI. Over-budget work
returns BudgetExhausted; nonidentical unbounded laws return Unsupported rather
than dropping their tails. Underflowing positive table masses return
NumericallyUnresolved. These are not averages of component divergences.

## Gotchas, research and related

Bounds must lie inside base support. Truncation chooses CDF or survival differences
and refuses unresolved normalization/cancellation; it does not silently clip tiny
mass to zero. Folded tiny interval probabilities can also be explicitly unresolved.
Unrepresentable draws fail without selectively redrawing them. Kernel callback
exceptions propagate; pure callbacks do not make arbitrary mutable graphs thread-safe.
Use fixed/dynamic `ScalarElement` or `CountElement` for inference; this does not
add exact finite-factor implementations or generic MI from a scalar marginal.

Definitions were checked against [Stan's change-of-variables guide](https://mc-stan.org/docs/stan-users-guide/reparameterization.html),
[SciPy truncation](https://docs.scipy.org/doc/scipy/reference/generated/scipy.stats.truncnorm.html),
[folded normal](https://docs.scipy.org/doc/scipy/reference/generated/scipy.stats.foldnorm.html)
and [wrapped Cauchy](https://docs.scipy.org/doc/scipy/reference/generated/scipy.stats.wrapcauchy.html).
No external source implementation or runtime dependency is copied.
Independent high-precision identities are in `tools/test_extended_construction_reference.py`.
Related: [acceptance](CAPABILITY_EXPANSION_ACCEPTANCE.md), [information roadmap](INFORMATION_METRICS_ROADMAP.md),
[distribution inventory](DISTRIBUTION_SUPPORT.md), [roadmap](../ROADMAP.md).
