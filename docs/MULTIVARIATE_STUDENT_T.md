# Elliptical multivariate Student t

## Overview

`MultivariateStudentTDistribution` adds a full-rank heavy-tailed vector law. Unlike
`VectorImportance.ProductStudentT`, its coordinates share one random radial scale.
Even a diagonal shape matrix therefore does not imply independence. Use this for
joint heavy-tail uncertainty and correlated defensive proposals, not as a synonym
for independent scalar Student t variables.

## Quick start

1. Construct the immutable law with degrees of freedom, location and **shape**.
2. Sample directly or register a graph element.
3. Use its log density for observations or explicit proposal corrections.

```scala
import com.cra.figaro.library.atomic.continuous.*
import com.cra.figaro.util.SamplingRandom
val law=MultivariateStudentTDistribution(5,Vector(0.0,1.0),
  Vector(Vector(2.0,.3),Vector(.3,1.0)))
val draw=law.sample(SamplingRandom.scalaRandom(42))
println(law.logDensity(draw))
```

## Public API reference

| Operation | Parameters | Return and example |
| --- | --- | --- |
| `MultivariateStudentTDistribution(df,location,shape)` | df in [0.001,1e6]; 1–128 locations; full-rank symmetric shape | Immutable validated kernel; see quick start |
| `dimension` | None | Int coordinate count: `law.dimension == 2` |
| `logDensity(x)` | Finite matching vector | Natural-log Lebesgue density: `law.logDensity(Vector(0.0,1.0))` |
| `density(x)` | Same | Exponential of log density; may underflow/overflow |
| `sample(rng)` | Non-null, exclusively owned Scala RNG | Finite vector; no retained RNG |
| `mean` | None | `Some(location)` only for df>1; otherwise `None` |
| `covariance` | None | `Some(df/(df-2)*shape)` only for df>2; otherwise `None` |
| `marginal(indices)` | Nonempty distinct valid indices, output order preserved | Exact t marginal: `law.marginal(Vector(1))` |
| `MultivariateStudentT(law)` | Fixed kernel; contextual name/collection | Observation-ready `AtomicMultivariateStudentT` |
| `MultivariateStudentT(kernelElement)` | Owned Element producing validated kernels | Non-caching hierarchical Element |
| `VectorImportance.StudentT(law)` | Fixed kernel | Normalized vector proposal adapter; delegates sample/log density |

The atomic Element exposes `distribution`, `generateRandomness()` (vector draw),
`generateValue(draw)` (identity), and `logDensity`/`logp` (kernel delegation).
Ordinary application code uses the factory and standard Element methods. The
[generated reference](api/README.md) includes inherited and generated methods.

## Three common patterns

### 1. Graph uncertainty with heavy tails

```scala
import com.cra.figaro.language.*
val u=new Universe
val state=MultivariateStudentT(law)(using "state",u)
val first=state.map(_.head)(using "first",u)
// Use standard Figaro inference on first; dispose algorithms and u when done.
```

### 2. A defensive joint proposal

```scala
import com.cra.figaro.algorithm.sampling.VectorImportance as V
val proposal=V.StudentT(law)
// V.run(V.Config(),proposal,originalLogTarget,x => x.head)
```

This is elliptical, not `V.ProductStudentT(location,scales,df)`. Longer tails
provide support but do not guarantee efficient sampling or finite weight variance.

### 3. Marginal uncertainty and information metrics

```scala
val firstLaw=law.marginal(Vector(0))
println(firstLaw.mean)
import com.cra.figaro.algorithm.sampling.MonteCarloInformation as I
val mi=I.mutualInformation(V.StudentT(law),V.StudentT(firstLaw),
  V.StudentT(law.marginal(Vector(1))))
println((mi.value,mi.mcse)) // numerical estimate, not an analytic result
```

## Gotchas

- Shape is **not covariance**: for df=5 covariance is 5/3 times shape. Undefined
  mean/covariance is not returned as zero. `None` at df<=2 does not mean independent.
- The Gaussian kernel's numeric shape limits apply: diagonal [1e-200,1e200],
  location magnitudes <=1e100, correlation condition number <=1e10, exact symmetry.
  No implicit jitter or singular pseudo-density is added.
- Sampling uses one chi-square scale and a Cholesky-transformed Gaussian. Apache
  Commons Math supplies Gamma draws through the caller's RNG, with interruption
  checks and a 10,000-random-call cap. Extreme small-df draws may be unrepresentable;
  that fails explicitly instead of silently redrawing and truncating the tail.
- No multivariate CDF/quantile, fitting, conditional t, matrix-valued law, or
  analytic general t-to-t divergence is introduced. This begins D5, not completes it.
- Forward/importance observations, explicit vector proposals and owned graph MH
  are tested. Factored inference, learning, and particle methods are not newly
  certified merely because the Element compiles.

## Related and reference

[Joint proposals](JOINT_PROPOSALS.md), [numerical information metrics](MONTE_CARLO_INFORMATION.md),
[common scalar laws](COMMON_DISTRIBUTIONS.md), [distribution roadmap](../ROADMAP.md).
The [SciPy reference](https://docs.scipy.org/doc/scipy/reference/generated/scipy.stats.multivariate_t.html)
documents the location/shape convention; Figaro deliberately starts with full rank.
The correlated log-density fixture uses an independent 60-digit mpmath inverse and
determinant, alongside scalar reductions and common-scale sampling checks.
