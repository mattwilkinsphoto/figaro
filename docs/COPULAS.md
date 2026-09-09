# Continuous Gaussian and Student-t copulas

## Overview

Copulas separate marginal distributions from dependence. A vector can have Weibull
and lognormal marginals without pretending its joint density is multivariate Gaussian.
The first implementation uses validated full-rank Gaussian and Student-t latent laws,
existing marginal CDF/quantile contracts and the necessary density correction.

## Quick start

1. Choose continuous marginals.
2. Supply a latent correlation matrix and optionally t degrees of freedom.
3. Sample or register the fixed graph adapter.

```scala
import com.cra.figaro.library.atomic.continuous.*
import com.cra.figaro.util.SamplingRandom
val margins = Vector(WeibullDistribution(2,3), LogNormalDistribution(.2,.6))
val correlation = Vector(Vector(1.0,.6), Vector(.6,1.0))
val law = CopulaDistribution(margins, correlation)
println(law.sample(SamplingRandom.scalaRandom(42)))
```

## API reference

| API | Parameters | Returns / example |
| --- | --- | --- |
| `CopulaDistribution(marginals,correlation,degreesOfFreedom=None)` | 1..32 continuous laws; exactly symmetric positive-definite unit-diagonal correlation; optional df in [.1,1e6] | Immutable normalized vector law; `Some(5)` selects Student t |
| `dimension` | None | Marginal count |
| `sample(rng)` | Non-null caller-owned RNG | Dependent physical-coordinate draw |
| `logDensity(x)` | Finite matching vector | Full Lebesgue joint log density, including marginal Jacobians |
| `marginal(indices)` | Nonempty distinct valid indices in desired order | Exact marginal copula, not conditional law |
| `gaussianPartitionMutualInformation(first)` | Nonempty proper coordinate subset | Analytic Gaussian-copula MI in nats versus complement; t law refused |
| `CopulaElement(law)` | Fixed kernel, contextual name and collection | Atomic observation-ready vector element |

The kernel implements `VectorImportance.Proposal`, so existing explicit-vector and
[Monte Carlo information](MONTE_CARLO_INFORMATION.md) APIs consume it directly.
The graph adapter's `generateRandomness`, identity `generateValue`, `logDensity` and
`logp` are documented in the [API reference](api/README.md).

## Three common patterns

```scala
// 1. Change dependence without changing marginal distributions.
val tLaw = CopulaDistribution(margins, correlation, Some(5))
println(tLaw.logDensity(Vector(2,1)))

// 2. Observe a complete dependent vector in a Figaro graph.
import com.cra.figaro.language.*
Universe.createNew()
val observed = CopulaElement(law)
observed.observe(Vector(2,1))

// 3. Compare full laws and dependence, not their separate marginals.
import com.cra.figaro.algorithm.sampling.MonteCarloInformation as I
println(I.kl(law, tLaw, I.Config(10000,42)))
println(I.bhattacharyya(law, tLaw, I.Config(10000,43)))
println(law.gaussianPartitionMutualInformation(Vector(0)))
println(I.mutualInformation(tLaw,tLaw.marginal(Vector(0)),tLaw.marginal(Vector(1))))
```

## Gotchas

- Correlation is on the latent scale, not generally output Pearson correlation.
  Zero off-diagonals imply independence for a Gaussian copula, but NOT generally for
  a t copula: its coordinates share a random scale.
- Matrix condition-number limits come from the existing Gaussian kernel (1e10).
  Singular dependence and implicit diagonal jitter are not supported.
- Probability values rounding to 0 or 1, singular marginal densities and non-finite
  inverse transforms throw. Samples are neither clipped nor redrawn to conceal this.
  In particular, these are not arbitrarily extreme-tail copula implementations.
- Custom continuous kernels must have consistent CDF/survival/quantile/density.
  The library cannot prove callback correctness. Marginal quantile restrictions remain.
- No count/mixed marginals, arbitrary copula family, correlation fitting, missing-coordinate
  likelihood integration or conditional sampler is added. Those need different contracts.
- MI under a Gaussian copula is invariant to invertible marginal transforms; it is
  not computed from a physical covariance matrix. For numerical MI, supply exact
  marginals in the same concatenated block order expected by the existing API.
- Fixed kernel reuse is thread-safe only when supplied marginals are immutable/pure;
  arbitrary graph state is not shared safely by this API.

## Related and basis

[Stan copulas](https://mc-stan.org/docs/stan-users-guide/copulas.html) describes the
latent-transform and density-ratio construction used here. This code independently
composes Figaro's existing validated kernels; no runtime dependency was added.
[Mixed-data copula research](https://www.jmlr.org/papers/v25/23-0495.html) remains a
separate future direction, not support implied by this continuous API.
Related: [acceptance](MODELING_CAPABILITIES_ACCEPTANCE.md), [mixed measures](MIXED_MEASURES.md),
[distribution inventory](DISTRIBUTION_SUPPORT.md).
