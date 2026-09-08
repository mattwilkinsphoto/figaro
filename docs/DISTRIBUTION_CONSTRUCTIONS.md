# Distribution constructions and Gaussian mixture models

## Overview

These APIs turn existing probability laws into observation-ready models: affine and
exponential transformations, finite-interval truncation, finite continuous mixtures,
and zero-adjusted counts. They also provide immutable scalar/full-covariance Gaussian
kernels, Gaussian mixture models (GMMs), and analytic Gaussian information diagnostics.
They implement D4's initial reusable building blocks, not every possible transformation
or mixed-measure construction. See the [roadmap](../ROADMAP.md).

Figaro already has `Apply`, `Chain`, and `Dist`. Those remain useful for composing
models. The new kernels additionally expose explicit normalized densities, log
likelihoods, support, and sampling contracts. A transformation's Jacobian or a
truncation's normalization is part of its likelihood, not just its sample generator.

A GMM is a weighted **sum** of Gaussian densities, representing alternative modes.
It is not a product of evidence densities, a fusion operation, or a Gaussian obtained
by averaging means/covariances. Even a mixture of independent-coordinate Gaussians
can have dependent coordinates through its shared component label.

## Quick start: three steps

1. Build/use the modern Scala 3 library as described in the [user guide](USER_GUIDE.md).
2. Define a scalar or vector mixture and evaluate it:

   ```scala
   import com.cra.figaro.library.atomic.continuous.*
   val first = MultivariateGaussianDistribution(
     Vector(-2.0, 0.0), Vector(Vector(1.0, .3), Vector(.3, 1.0)))
   val second = first.copy(mean = Vector(2.0, 0.0))
   val model = GaussianMixtureDistribution(Vector(.4, .6), Vector(first, second))
   val logLikelihood = model.logDensity(Vector(2.0, 0.0))
   val draw = model.sample(new scala.util.Random(42))
   ```

3. Run the complete, tested model examples:

   ```sh
   sbt "examples / Compile / runMain com.cra.figaro.example.documentation.DistributionConstructionsExample"
   ```

For Figaro inference, wrap a scalar kernel in `ScalarElement`, a count kernel in
`CountElement`, or a vector GMM in `GaussianMixture`. Kernels themselves do not create
a universe, infer a posterior, or retain a random generator.

## API reference

Continuous classes below are in `com.cra.figaro.library.atomic.continuous`; the count
wrapper is in `.discrete`. Constructors return immutable validated kernels; invalid
parameters throw `IllegalArgumentException`. Case-class `copy` repeats validation.
Generated case-class members and inherited element operations are also listed in the
[complete public-method reference](api/README.md).

### Constructors and their parameters

| Constructor | Parameters and meaning | Example |
| --- | --- | --- |
| `GaussianDistribution(location, standardDeviation)` | Mean with absolute value <=1e100; SD in [1e-100,1e100]. **Not variance**, unlike existing `Normal(mean, variance)` | `GaussianDistribution(0,2)` has variance 4 |
| `AffineDistribution(base, offset, multiplier)` | Continuous base; finite bounded offset; signed multiplier with absolute value [1e-100,1e100]. Models `offset + multiplier*X`; negative values reflect the law | `AffineDistribution(GaussianDistribution(0,1),3,-2)` |
| `ExpDistribution(base)` | Continuous base; models `exp(X)` with inverse-Jacobian `-log(y)` | `ExpDistribution(GaussianDistribution(0,1))` |
| `TruncatedDistribution(base, lower, upper)` | Finite ordered bounds inside base support. Conditions on the interval; does not clip values onto endpoints | `TruncatedDistribution(GaussianDistribution(0,1),8,9)` |
| `ScalarMixtureDistribution(weights, components)` | Immutable `Vector[Double]` weights and matching `Vector[ScalarDistribution]`; all components continuous | `ScalarMixtureDistribution(Vector(.4,.6),Vector(GaussianDistribution(-2,1),GaussianDistribution(2,1)))` |
| `MultivariateGaussianDistribution(mean, covariance)` | Immutable vector and square vector-of-vectors, dimension 1..128; full-rank covariance, exactly symmetric, diagonal [1e-200,1e200], means bounded as above; correlation condition number <=1e10 | `MultivariateGaussianDistribution(Vector(0.0),Vector(Vector(4.0)))` |
| `GaussianMixtureDistribution(weights, components)` | Matching immutable weights and vector Gaussian components; every component has the same dimension | `GaussianMixtureDistribution(Vector(.4,.6),Vector(first,second))` |
| `ZeroAdjustedDistribution(base, zeroProbability, hurdle=false)` | Nonnegative count law, probability in [0,1]. False adds structural zeros; true removes base zeros before mixing positive counts with zeros | `ZeroAdjustedDistribution(NegativeBinomialDistribution(2,.5),.3,true)` |

Both mixture constructors accept 1..1024 components. Weights must be nonnegative,
finite, and sum to one within 1e-12. The tiny admitted sum discrepancy is normalized
internally; arbitrary unnormalized amplitudes are rejected. Zero weights preserve
component positions but exclude those components from density, sampling and moments.
Even zero-weight components must be valid kernels with compatible vector dimensions.

### Scalar/count operations

All five scalar kernel/construction types above implement `ScalarDistribution`.
The count wrapper implements `CountDistribution` and works with `CountElement`.

| Method/field | Parameters | Returns and example |
| --- | --- | --- |
| `logDensity(x)` / `density(x)` | Scalar argument; NaN rejected | Natural-log Lebesgue density / its exponential; `normal.logDensity(10)` remains usable when an ordinary density would underflow |
| `logProbability(k)` / `probability(k)` | Integer count, including unsupported negatives | Log mass / mass; `hurdle.probability(0)` is the configured zero probability |
| `cdf(x)` / `survival(x)` | Scalar value, or integer count for a count law | `P(X<=x)` / `P(X>x)`; the survival path does not subtract a rounded CDF. `bounded.survival(8.5)` |
| `quantile(p)` | Finite probability [0,1] | Inverse CDF; endpoints give support limits. Infinite-support count `p=1` and unrepresentable scalar interior quantiles throw. `bounded.quantile(.95)` |
| `support` | None | Scalar `(infimum,supremum)` or count `(minimum,Option[maximum])`; a mixture's scalar pair is a bounding interval and can contain gaps |
| `mean` / `variance` | None | Scalar `Option[Double]`, count `Double`; scalar `None` means undefined **or not implemented**. Generic exp/truncated moments are not implemented; mixture variance includes between-mode variation |
| `sample(rng)` | Non-null caller-owned `scala.util.Random` | One supported draw; `model.sample(new scala.util.Random(42))`. Affine/exp laws transform base draws directly; mixtures draw a component then its value; neither path numerically inverts a mixture/transformed CDF |
| `retainedProbability` | Truncated law only; no arguments | Base probability of the retained interval; `bounded.retainedProbability` is about 6.22e-16 for a standard Gaussian on [8,9] |
| `responsibilities(x)` | Scalar-mixture observation with finite log density | `Vector[Double]` membership probabilities in component order, including zero entries for zero weights; `scalarMixture.responsibilities(2)` |

Scalar and count contracts are now extensible traits. A custom implementation must
be immutable and supply mutually consistent density/mass, support, CDF, direct survival,
quantile and caller-RNG behavior. Scalar laws must be continuous in Lebesgue measure;
do not implement a point mass or spike-and-slab as a `ScalarDistribution`. The metric
dispatchers do not automatically authorize numerical integration of custom laws.

### Vector Gaussian and GMM operations

| Method/field | Parameters | Returns and example |
| --- | --- | --- |
| `dimension` | None | Number of real coordinates |
| `logDensity(x)` / `density(x)` | Finite immutable vector of matching dimension | Log Lebesgue density / exponential; `model.logDensity(Vector(2.0,0.0))` |
| `sample(rng)` | Non-null caller-owned RNG | Immutable draw vector; `first.sample(new scala.util.Random(42))` |
| `mean` / `covariance` | None | Gaussian parameters or exact GMM first/second central moments; `model.covariance` includes within- and between-component variation |
| `marginal(indices)` | Nonempty distinct valid `Vector[Int]`, output order retained | Exact Gaussian or GMM marginal, **not conditioning**; `model.marginal(Vector(1))` |
| `mahalanobisSquared(x)` | Gaussian only; matching finite observation vector | Squared whitened residual norm; `first.mahalanobisSquared(Vector(0.0,0.0))` |
| `responsibilities(x)` | GMM only; finite vector with resolved likelihood | Posterior component-membership vector for this observation; `model.responsibilities(Vector(2.0,0.0))`. Does not update weights or fit parameters |

`GaussianMixture(kernel)(using name, collection)` returns `AtomicGaussianMixture`.
Its overload accepting `Element[GaussianMixtureDistribution]` returns a non-caching
chain for hierarchical parameters. `generateRandomness()` draws from the scoped Figaro
RNG, `generateValue(value)` returns its vector argument, and `logDensity(value)` and
`logp(value)` return the kernel's stable log likelihood. The fixed adapter exposes
`distribution`. Use ordinary element `observe`, constraints, and algorithm lifecycle
operations as in the [user guide](USER_GUIDE.md).

### Gaussian information

These APIs return `InformationMetricResult`, in **nats**. Read `status` and `value`
instead of assuming every valid input can meet an arbitrarily tight numeric allowance.
For these Gaussian functions, successful results are `Analytic`; numeric refusals are
`NumericallyUnresolved` with no value. Invalid dimensions/partitions/tolerances throw.

| Function | Parameters | Returns and example |
| --- | --- | --- |
| `GaussianInformation.kl(p,q,tolerance=1e-8)` | Two same-dimensional full-rank Gaussian kernels in the same coordinates; positive finite absolute numeric allowance | Directed KL(P\|\|Q); `GaussianInformation.kl(first,second)` |
| `GaussianInformation.bhattacharyya(p,q,tolerance=1e-8)` | Same Gaussian inputs and tolerance meaning | Symmetric negative log overlap; `GaussianInformation.bhattacharyya(first,second)` |
| `GaussianInformation.mutualInformation(joint,first,tolerance=1e-8)` | **One joint** Gaussian and a proper, nonempty set of coordinate indices | MI of that block with its complement; `GaussianInformation.mutualInformation(first,Vector(0))` when `first` is the example's two-dimensional kernel |
| `GaussianInformation.scalar(law)` | Scalar `GaussianDistribution` | Equivalent dimension-one vector kernel for the same metric APIs |
| `ScalarDivergence.kl(p,q,...)` / `.bhattacharyya(p,q,...)` | Two scalar Gaussian kernels; existing tolerance/budget/cancellation arguments | Dispatches to the analytic Gaussian path; `ScalarDivergence.kl(GaussianDistribution(0,1),GaussianDistribution(1,1))`. Full inherited argument contracts are in the [metric guide](COMMON_INFORMATION_METRICS.md) |

For equal covariance, KL is **one half the squared Mahalanobis separation** and
Bhattacharyya distance is **one eighth**. Neither is the unsquared Mahalanobis distance.
For unequal covariance, determinant/shape contributions matter. Gaussian partition MI
depends on covariance, not means, and equals zero for block-diagonal covariance.
These reductions are independently tested. The KL and partition-MI definitions follow
the [multivariate Gaussian KL proof](https://statproofbook.github.io/P/mvn-kl.html) and
[Gaussian MI proof](https://statproofbook.github.io/P/mvn-mi.html); Gaussian overlap is
also treated in [Nielsen and Boltz](https://arxiv.org/abs/1004.5049).

There is deliberately **no** `GaussianInformation.kl(gmm1,gmm2)` or GMM-MI overload.
A weighted average of component divergences is not generally the divergence between
mixtures. A moment-matched Gaussian is a different probability law; passing its
covariance to Gaussian MI does not compute mixture MI. Those numerical diagnostics
and their accuracy/work contracts remain on the [information roadmap](INFORMATION_METRICS_ROADMAP.md).

## Three common patterns

### 1. Transform or restrict a modeled quantity

```scala
import com.cra.figaro.library.atomic.continuous.*
val latent = GaussianDistribution(0,1)
val positive = ExpDistribution(latent) // exp transformation plus Jacobian
val restricted = TruncatedDistribution(latent,-1,2) // conditional, not clipped
val converted = AffineDistribution(restricted,10,2) // change units afterward
val likelihood = converted.logDensity(11)
```

Previously an `Apply` expression could generate transformed values. The new law also
provides its explicit observation likelihood. Truncating changes the density by a
normalizing constant. Clipping would instead create endpoint point masses and is not
interchangeable with this construction.

### 2. Represent multiple modes and infer a hierarchical regime

```scala
import com.cra.figaro.language.*
import com.cra.figaro.library.atomic.continuous.*
import com.cra.figaro.algorithm.sampling.Importance
val u = Universe.createNew()
val a = MultivariateGaussianDistribution(Vector(-2.0,0.0),
  Vector(Vector(1.0,.3),Vector(.3,1.0)))
val b = a.copy(mean=Vector(2.0,0.0))
val regime = Flip(.5)(using "regime",u)
val kernels = Apply(regime,(r: Boolean) => GaussianMixtureDistribution(
  if(r) Vector(.8,.2) else Vector(.2,.8),Vector(a,b)))(using "kernel",u)
GaussianMixture(kernels)(using "observed",u).observe(Vector(2.0,0.0))
val alg = Importance(10000,regime)
try { alg.start(); println(alg.probability(regime,true)) }
finally { if(alg.isActive) alg.kill(); u.clear() }
```

A single Gaussian spreads mass between separated modes; a GMM preserves the modes.
Its covariance includes their separation, not just each component's local spread.
Here Figaro infers the probability of a regime from an explicit prior and observation;
this is not an automatic EM fit. The corresponding runnable example checks that the
posterior is near 0.2. For scalar mixtures use `ScalarMixtureDistribution` and
`ScalarElement`; use `responsibilities` when you want membership for a *fixed* model.

### 3. Model excess zeros in counts

```scala
import com.cra.figaro.library.atomic.discrete.*
val ordinary = NegativeBinomialDistribution(2,.5)
val inflated = ZeroAdjustedDistribution(ordinary,.3)
val hurdle = ZeroAdjustedDistribution(ordinary,.3,hurdle=true)
assert(math.abs(inflated.probability(0)-.475) < 1e-12)
assert(math.abs(hurdle.probability(0)-.3) < 1e-12)
```

Ordinary negative binomial counts already include zeros (probability .25 here).
Zero inflation adds another zero-generating mechanism: .3 + .7*.25 = .475.
A hurdle sets the zero probability to .3 and redistributes the remaining .7 over
strictly positive counts. Choosing the flag changes the model, not just its sampler.
See the [statsmodels hurdle-model discussion](https://www.statsmodels.org/dev/examples/notebooks/generated/count_hurdle.html)
for a separate implementation's distinction between truncated and ordinary count parts.

## Gotchas and boundaries

- These are opt-in APIs; no inference algorithm's default is switched. Kernels are
  shareable immutable values, but an element/universe and a mutable RNG must not be
  shared by concurrent workers. Build separate models for isolated parallel chains.
- A GMM kernel samples modes directly. An MCMC model containing unknown weights or
  component parameters can still mix poorly or exhibit label switching. No general
  convergence or speedup guarantee follows from adding a mixture distribution.
- Gaussian covariance means covariance, scalar `GaussianDistribution` uses SD,
  existing `Normal` uses variance. Keep coordinate meanings/units identical in comparisons.
- Only full-rank, numerically resolved covariance is supported. No singular Gaussian
  measure, pseudoinverse, implicit covariance repair/jitter, or automatic dimension reduction.
  Construction costs include cubic factorization; marginal construction refactors.
- Metric error estimates account heuristically for dimension, conditioning and
  cancellation; they are not rigorous certificates or sampling confidence intervals.
  A covariance may be valid for density/sampling while a metric refuses its requested tolerance.
- Truncation currently requires **finite** bounds and a resolvable base probability
  difference. It selects a lower- or upper-tail difference and rejects a result at
  <=64 ulps of the larger operand. It does not extrapolate missing tail precision.
  Very narrow intervals, tiny subinterval CDF queries, or extreme quantiles can throw
  `ArithmeticException`. Generic truncated/exp moments are `None`, not zero.
- Scalar mixture quantiles use component quantile brackets and at most 2048 bisections.
  Reflections use a bounded bracket search as well. Unrepresentable brackets or draws
  throw rather than clip, retry selectively, or silently change the law. All numeric
  operations remain subject to binary64 rounding, including collapsed nearby coordinates.
- Zero-adjusted counts retain the base law's integer limit. A hurdle needs nonzero
  positive-count mass unless its zero probability is one. This is count-measure support,
  **not** a general continuous/discrete spike-and-slab API.
- Element adapters reject singular infinite boundary likelihoods. Use appropriate
  interval evidence rather than observing a density singularity as an ordinary value.
- Gaussian vector inputs must be finite. Numerical overflow throws explicitly;
  ordinary density underflow to zero is expected and does not invalidate a finite log density.
- New observation-ready kernels are exercised with importance sampling and MH, including
  isolated GMM chains. This does not add exact finite factors, conjugate fitting, EM,
  component selection/merging/pruning, arbitrary autodiff, or GVM fusion.
- Common affine transformations (same offset and multiplier) and paired exp transformations
  reuse their base laws' KL/Bhattacharyya result by change-of-variable invariance. Zero-adjusted
  counts with the **same base** reduce to a two-category zero/positive comparison, even
  when one uses inflation and the other a hurdle. Unlike transformations, truncated laws,
  mixtures and zero-adjusted laws with different bases currently return `Unsupported`.
  Gaussian-only approximations are never silently substituted; identities return zero.

## Migration and acceptance

The existing `MultivariateNormal` List-valued factory remains available. Its atomic
adapter now implements `HasLogDensity`; `logp` no longer returns the old placeholder
negative infinity. Density and sampling use the validated Gaussian kernel and scoped
Figaro RNG. This introduces the full-rank/dimension/numeric limits above to those
operations, and seeded draw sequences differ from the legacy Apache sampler. The
legacy public Apache `distribution` member remains for source compatibility; direct
calls through it bypass the new kernel/scoped-RNG contract and are not recommended.
Scalar `Normal`'s variance convention and proposal behavior are unchanged.

The branch's acceptance suite is `DistributionConstructionsTest`. Independent 70-digit
inverse/determinant controls, direct scalar integrals, normalized count checks and
between-mode moments live in `tools/test_common_constructions_reference.py`; this
uses research-only mpmath and adds no runtime dependency. The runnable example and
independent thin-JAR consumer exercise the public boundary. Linux CI also runs the
new suite and example. See [milestone acceptance](DISTRIBUTION_CONSTRUCTIONS_ACCEPTANCE.md)
for actual completed checks and integration status.

## Related

[Common distributions](COMMON_DISTRIBUTIONS.md), [information measures](COMMON_INFORMATION_METRICS.md),
[parallel sampling](PARALLEL_PERFORMANCE.md), [multichain MCMC](MULTI_CHAIN_MCMC.md),
[distribution inventory](DISTRIBUTION_SUPPORT.md), [examples](../FigaroExamples/README.md),
and [independent consumer](../tools/acceptance-consumer/README.md).
