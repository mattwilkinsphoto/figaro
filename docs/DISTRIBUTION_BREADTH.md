# Joint counts, extreme values, matrices and spherical directions

## Overview

Modern.18 adds five reusable representatives to Figaro's distribution roadmap:
`Multinomial`, `GeneralizedExtremeValue`, `GeneralizedPareto`, `Wishart` and
`VonMisesFisher3`. Each has an immutable standalone numerical kernel, a Figaro
element adapter, caller-owned/scoped RNG sampling and observation-ready log
likelihoods. They broaden the kinds of variables a model can express; adding a
family does not automatically improve inference efficiency.

Use multinomial for a vector of category counts from a fixed number of trials;
GEV for maxima; GPD for excesses above a threshold; Wishart for positive-definite
random matrices; and von Mises–Fisher for a unit direction on the sphere. These
are first representatives, not completion of every flavor in those broad families.

## Quick start: three steps

```scala
import com.cra.figaro.library.atomic.continuous.*
import com.cra.figaro.util.SamplingRandom
// 1. Specify a validated, immutable law: kappa is concentration, not variance.
val law = VonMisesFisher3Distribution(Vector(0.0,0.0,1.0),10)
// 2. Sample with a private scientific RNG, or wrap it as a Figaro element.
val draw = law.sample(SamplingRandom.scalaRandom(42))
// 3. Score the draw in the law's proper measure.
println(law.logDensity(draw))
```

For graph modeling, create the element inside its owning Universe with
`VonMisesFisher3(law)`. Hierarchical overloads take an `Element[...Distribution]`;
use `Apply`/`map` to construct a validated kernel from stochastic parameters.
`ScalarElement` supplies that overload for GEV/GPD. Invalid dynamic parameters
fail during evaluation, rather than silently changing the model.

## Parameter and support contracts

| Kernel constructor | Parameters | Support and conventions |
| --- | --- | --- |
| `MultinomialDistribution(trials, probabilities)` | Trials 0–1000000; Vector of 1–128 finite nonnegative probabilities summing to one within 1e-12 | Integer count vectors summing to trials; counting measure; only rounding discrepancy is normalized |
| `GeneralizedExtremeValueDistribution(shape, location=0, scale=1)` | Shape xi zero or abs(xi) in [1e-8,2]; abs(location) ≤1e100; scale in [1e-100,1e100] | Maxima convention, 1+xi*(x-location)/scale >0; xi=0 is Gumbel; **SciPy genextreme uses c=-xi** |
| `GeneralizedParetoDistribution(shape, location=0, scale=1)` | Same numerical ranges as GEV | x≥location and 1+xi*(x-location)/scale >0; location is threshold; xi=0 exponential, xi=-1 uniform |
| `WishartDistribution(degreesOfFreedom, scale)` | Dimension 1–16; real df in [dimension+1,1e6]; exactly symmetric SPD scale, diagonal [1e-100,1e100], correlation condition ≤1e10 | Positive-definite symmetric matrices; density in independent symmetric-entry coordinates; mean is df*scale, not scale |
| `VonMisesFisher3Distribution(direction, concentration)` | Three finite unit coordinates within 1e-12; kappa in [0,1e6] | Unit sphere S2 in R3; **surface-area measure**, not 3D Lebesgue density; kappa=0 uniform |

Nonzero extreme-value shapes closer to zero than 1e-8 are deliberately refused;
use zero when the intended model is the limiting law, not an automatic substitution.
Finite support endpoints must be representable and distinct from location.
Wishart's mathematical df range is wider than this implementation: near-singular
df regimes, singular matrices and implicit jitter are excluded. Validating a
Wishart observation reuses the Gaussian matrix limits (diagonal [1e-200,1e200],
resolved correlation condition ≤1e10). Invalid or unresolved observation matrices
throw instead of becoming fabricated zero likelihoods. Unit vectors are normalized
only within the stated rounding tolerance; arbitrary spatial vectors are rejected.

## API reference

All logarithms and information measures use natural logs/nats. Kernels retain no
RNG or Universe. A supplied RNG must be exclusively owned by its caller.

### Scalar GEV and GPD

Both implement the existing `ScalarDistribution` interface:

| Function/property | Parameters and return | Example |
| --- | --- | --- |
| `logDensity(x)` / `density(x)` | Double x, NaN rejected; log PDF / PDF; outside support gives -Infinity / zero | `gpd.logDensity(2)` |
| `cdf(x)` / `survival(x)` | P(X≤x) / direct P(X>x), not subtraction of a rounded CDF | `gev.survival(10)` |
| `quantile(p)` | p in [0,1]; endpoints return support limits; unrepresentable/collapsed interior values throw | `gpd.quantile(.99)` |
| `support` | `(infimum,supremum)` | `gev.support` |
| `mean` / `variance` | Option[Double]; undefined moments return None; infinite variance with defined mean returns Some(+Infinity) | `gpd.mean` |
| `sample(rng)` | Caller Scala RNG; inverse-transform finite draw, or numerical/cancellation failure | `gev.sample(rng)` |

GEV mean exists for xi<1; finite variance for xi<0.5. GPD has the same moment
existence thresholds. Near-zero GEV moments use series to avoid subtracting nearly
equal gamma functions. Density at a finite upper endpoint is zero for -1<xi<0,
1/scale at xi=-1, and infinite for xi<-1. Standalone kernels expose that limit;
`AtomicScalar` rejects singular infinite-density observations. Use interval evidence
or change the observation model, not an arbitrary finite replacement.

Named `GeneralizedExtremeValue(shape,location,scale)` and
`GeneralizedPareto(shape,location,scale)` return `AtomicScalar`. They take contextual
`Name[Double]` and `ElementCollection`, validate before registration, and default
location/scale to 0/1. Stochastic kernels use `ScalarElement(kernelElement)`.

### Multinomial

| Function/property | Parameters and return | Example |
| --- | --- | --- |
| `normalizedProbabilities`, `dimension` | Effective immutable category probabilities and count | `law.normalizedProbabilities` |
| `logProbability(counts)` / `probability(counts)` | Matching `Vector[Int]`; log PMF / PMF; wrong total, negatives or impossible category give -Infinity / zero; wrong length throws | `law.probability(Vector(1,1,2))` |
| `mean`, `covariance` | n*p and n*(diag(p)-p*pᵀ) | `law.covariance(0)(1)` |
| `sample(rng)` | Private RNG; supported count vector using sequential conditional binomials | `law.sample(rng)` |

This is not `util.sampleMultinomial` (which selects one categorical value).
The count-vector covariance is singular because its sum is fixed. Zero trials
produce the all-zero vector; zero-probability categories cannot receive counts.
The sampler does not loop once per trial; it uses existing Commons Math binomial
draws with a caller-owned RNG adapter. An open-unit RNG helper has a 1024-attempt
cap; sampling never retries a complete result to select a desirable outcome.

### Wishart

| Function/property | Parameters and return | Example |
| --- | --- | --- |
| `dimension`, `mean` | Matrix dimension and df*scale matrix | `law.mean` |
| `covariance(i,j,k,l)` | Four valid indices; Cov(Xij,Xkl)=df*(Sik*Sjl+Sil*Sjk) | `law.covariance(0,0,1,1)` |
| `logDensity(value)` / `density(value)` | Validated SPD `Vector[Vector[Double]]`; log PDF / PDF | `law.logDensity(law.mean)` |
| `sample(rng)` | Private RNG; symmetric SPD matrix via Bartlett decomposition | `law.sample(rng)` |

Sampling uses Cholesky factors, normal draws and chi-square draws through Commons
Math's gamma sampler. A 100000-RNG-call cap and interruption checks bound iterative
sampling work. Numerically unresolved matrices fail; they are not regenerated
until one passes, which would alter the sampled distribution. No matrix CDF,
quantile, inverse-Wishart, LKJ or matrix-parameter fitting is supplied.

### Spherical von Mises–Fisher

| Function/property | Parameters and return | Example |
| --- | --- | --- |
| `meanDirection` | Normalized unit direction | `law.meanDirection` |
| `mean` | Expected vector A3(kappa)*meanDirection, generally shorter than unit length | `law.mean` |
| `logDensity(value)` / `density(value)` | Unit Vector[Double]; log surface-area PDF / PDF | `law.logDensity(Vector(0,0,1))` |
| `sample(rng)` | Private RNG; stable inverse polar CDF and uniform azimuth, then rotation to the mean direction | `law.sample(rng)` |

The method uses small-concentration series and a scaled normalizer at high
concentration. It does not call an overflowing sinh(kappa) at large kappa. There
is no general-dimensional vMF, axial symmetry, Kent/Bingham, fitting or angular
coordinate CDF in this first API. A direction and its negative are distinct.

### Graph factories and atomic methods

`Multinomial(law)`, `Wishart(law)` and `VonMisesFisher3(law)` return their named
`Atomic...` adapter. Each also accepts `Element[Law]` and returns a non-caching
hierarchical `Element[Value]`. Both overloads take contextual `Name[Value]` and
owning `ElementCollection`. For example, `Wishart(df.map(n => WishartDistribution(n,s)))`.

All adapters expose `distribution` (the fixed kernel), `generateRandomness()`
(one scoped Figaro-RNG draw), `generateValue(value)` (identity), and
`logDensity(value)` (the kernel's log likelihood). Scalar adapters also expose
`logp` as the same log likelihood. These low-level methods underpin normal
`observe`, importance and MH use; applications generally call the factory.
Prior-proposal matrix/spherical adapters deliberately do not introduce an arbitrary
Euclidean continuous proposal that could leave their support.

## Information metrics

| API | Coverage |
| --- | --- |
| `ScalarDivergence.kl(p,q,tolerance=1e-6,maxEvaluations=16384,cancelled=...)` | Same-family GEV/GPD; analytic same-scale Gumbel and same-origin exponential controls; otherwise guarded probability quadrature |
| `ScalarDivergence.bhattacharyya(...)` | Same arguments; symmetric negative log affinity; support/disjointness checks and explicit numerical refusals |
| `MultinomialInformation.kl(p,q,tolerance=1e-8)` | n times categorical KL; matching category order/dimension; unequal totals or one-sided zero support can imply infinity |
| `MultinomialInformation.bhattacharyya(p,q,tolerance=1e-8)` | n times categorical negative log affinity; disjoint support gives infinity |
| `MultinomialInformation.mutualInformation(law,first,tolerance=1e-8,maxTerms=100000)` | One joint law; nonempty distinct first-block indices with nonempty complement; binomial-subtotal entropy, requiring n+1 terms unless deterministic |
| `WishartInformation.kl(p,q,tolerance=1e-8)` / `.bhattacharyya(...)` | Same dimension and matrix coordinates; analytic exponential-family reductions with numerical guards |
| `VonMisesFisher3Information.kl(p,q,tolerance=1e-8)` / `.bhattacharyya(...)` | Same spherical axes/measure; analytic normalizer and mean-vector reductions including uniform/opposite limits |

Returns are `InformationMetricResult`: status, optional value, numerical
`errorEstimate`, `evaluations` and `method`. Require a successful status and present
value. Analytic formulas still incur rounding error; tight tolerances, large
parameters or poorly resolved matrix calculations can return `NumericallyUnresolved`.
Estimated errors are heuristic, not confidence intervals or rigorous certificates.
Finite-table reductions propagate their allowance after multiplying by n; large n
can therefore require a looser tolerance. No error-based rerun changes a sample set.

Multinomial MI is not computed from two unrelated marginals: each complementary
block determines its subtotal, and the two allocations are independent conditional
on that subtotal. Thus their MI is the entropy of Binomial(n,p_first). The method
sums the entire subtotal support within its budget, not the exponentially larger
joint count table. It does not implement arbitrary overlapping blocks.

Matrix-partition and spherical-coordinate MI remain unimplemented. Scalar marginals
alone do not specify MI; sphere coordinates also require care about singular joint
support. Do not feed spherical densities into a generic R3 Lebesgue-density
estimator, or flatten a symmetric matrix without specifying independent coordinates.

## Three common patterns

### Category counts and information comparison

```scala
import com.cra.figaro.library.atomic.discrete.*
val counts = MultinomialDistribution(20,Vector(.2,.3,.5))
val alternative = MultinomialDistribution(20,Vector(.3,.3,.4))
println(MultinomialInformation.kl(counts,alternative))
println(MultinomialInformation.mutualInformation(counts,Vector(0)))
```

### Tail thresholds with direct survival and quantiles

```scala
val excess = GeneralizedParetoDistribution(.2,location=0,scale=2)
val threshold = excess.quantile(.99)
println(excess.survival(threshold)) // approximately .01, evaluated directly
val maxima = GeneralizedExtremeValueDistribution(0,location=10,scale=2)
println(maxima.quantile(.99)) // Gumbel limit, not Weibull minimum/lifetime convention
```

### Structured priors and geometric comparisons

```scala
val matrix = WishartDistribution(5,Vector(Vector(1.0,.2),Vector(.2,1.0)))
val other = matrix.copy(degreesOfFreedom=8)
println(WishartInformation.bhattacharyya(matrix,other))
val north = VonMisesFisher3Distribution(Vector(0,0,1),10)
val east = VonMisesFisher3Distribution(Vector(1,0,0),10)
println(VonMisesFisher3Information.kl(north,east))
```

## Verification, provenance and related modules

Local acceptance on Java 17.0.4 / Scala 3.9.0 / sbt 2.0.8:

- Clean compilation of 328 library sources and all 496 modernization tests passed.
- The final condition-aware Wishart allowance passed the 18-test breadth suite;
  all 71 tests in the separate legacy collection/factor group also passed.
- All five independent Python reference tests and the executable examples passed.
- Thin, fat, source and Scaladoc JARs passed artifact checks; the separate published
  consumer resolved the new coordinate and exercised the new public APIs.
- The generated reference contains 12320 public method entries; 13721 local
  Markdown link targets were verified before the final statistical-validation link
  was added (13722 targets in the final check).

The first packaging attempt encountered a Windows JAR replacement/access error
after an example run and a subsequent source recompilation. A fresh-JVM build
completed publication and consumer verification. No test assertion was relaxed
or excluded to obtain these results. Remote CI remains the main-integration gate.
The historical dependent-factor CI failure led to an exact wiring regression and
a larger-budget, unchanged-tolerance sampling check; see
[statistical validation](STATISTICAL_VALIDATION.md#dependent-universe-factor-ci-regression-modern18).

Eighteen focused Scala tests cover 48 independent scalar fixtures, count support
enumeration, chi-square and spherical integrals, moments, zero/large concentration,
tail underflow, divergence controls, hierarchical evidence, all five ordinary MH
paths, isolated multi-chain replay and cancellation. Five Python oracle tests use
mpmath at up to 80 digits. The [executable example](../FigaroExamples/src/main/scala/com/cra/figaro/example/documentation/DistributionBreadthExample.scala)
exercises these patterns. Full clean build and independent published-consumer gates
are required for integration. Fixed seeded tests validate these workloads, not all
possible model graphs or universal convergence. Exact factored inference and
learning are not claimed for these adapters.

Specifications were checked against official [GEV](https://docs.scipy.org/doc/scipy/reference/generated/scipy.stats.genextreme.html),
[GPD](https://docs.scipy.org/doc/scipy/reference/generated/scipy.stats.genpareto.html),
[multinomial](https://docs.scipy.org/doc/scipy/reference/generated/scipy.stats.multinomial.html),
[Wishart](https://docs.scipy.org/doc/scipy/reference/generated/scipy.stats.wishart.html)
and [vMF](https://docs.scipy.org/doc/scipy/reference/generated/scipy.stats.vonmises_fisher.html)
documentation. Analytic directional divergences follow the exponential-family
identities in [Kitagawa and Rowley (2022), Sections 2–3](https://arxiv.org/html/2202.05192v2).
Wishart comparisons use its normalizer and expected log determinant; see the
[explicit derivation](https://statproofbook.github.io/P/wish-kl.html). Equations were
independently implemented; no SciPy or research-repository source was copied.
Existing Commons Math supplies binomial/gamma draws and special functions; no new
runtime dependency is added. No report ingestion or data-fusion API is introduced.

Related: [distribution inventory](DISTRIBUTION_SUPPORT.md), [constructions](DISTRIBUTION_CONSTRUCTIONS.md),
[information roadmap](INFORMATION_METRICS_ROADMAP.md), [inference health](INFERENCE_HEALTH.md),
[multi-chain MH](MULTI_CHAIN_MCMC.md), [roadmap](../ROADMAP.md) and [wishlist](../WISHLIST.md).
