# Gauss-von Mises mixtures

## Overview

The immutable mixture combines fixed Horwood-Poore GVM components on a real vector
plus one circle. It represents several possible linear-angular states without
collapsing them into one component. This is a probability-law API, not a filter,
PDF-fusion algorithm or orbit propagator. A separate [6.1 bounded fitter](GVM_MIXTURE_FITTING.md)
estimates scalar-linear mixture parameters; this kernel also supports fixed
higher-dimensional components.

## Quick start

1. Construct fixed GVM kernels in common coordinates.
2. Supply normalized component probabilities.
3. Evaluate, sample or register a graph element.

```scala
import com.cra.figaro.library.atomic.continuous.*
val a = GaussVonMisesDistribution(Vector(-1.0), Vector(Vector(1.0)),
  3.0, Vector(.3), Vector(Vector(.1)), 10)
val b = GaussVonMisesDistribution(Vector(1.0), Vector(Vector(1.0)),
  -3.0, Vector(.3), Vector(Vector(.1)), 10)
val law = GaussVonMisesMixtureDistribution(Vector(.4, .6), Vector(a, b))
println(law.logDensity(LinearAngular(Vector(0), 3.1)))
```

## API reference

| API | Parameters | Returns / example |
| --- | --- | --- |
| `GaussVonMisesMixtureDistribution(weights,components)` | 1..128 normalized nonnegative weights and matching kernels, linear dimension 1..32 | Immutable mixture; zero-weight components contribute no density/draws |
| `dimension` | None | Number of linear coordinates, excluding the angle |
| `logDensity(point)`, `density(point)` | Matching `LinearAngular` value | Full log-sum density in linear volume times radians, or its exponential |
| `responsibilities(point)` | Joint value | Conditional component probabilities; undefined extreme tails throw |
| `sample(rng)` | Non-null caller-owned RNG | Component-selected joint draw |
| `linearMarginal` | None | Exact `GaussianMixtureDistribution`, not conditioning |
| `asProposal(chartCenter=0)` | Finite center with absolute value <=1e6 radians | Normalized vector adapter in `[center-Pi,center+Pi)`; vector dimension is linear dimension + 1 |
| `GaussVonMisesMixture(law)` | Fixed kernel; contextual name/collection | Observation-ready `LinearAngular` element |
| `GaussVonMisesMixtureInformation.kl(p,q,config)` | Matching-coordinate laws and `MonteCarloInformation.Config` | Full-mixture directed KL estimate and plug-in MCSE |
| `bhattacharyya(p,q,config)` | Matching-coordinate laws, fixed draw/RNG budget | Full-mixture overlap divergence, or explicit unresolved status |
| `componentMutualInformation(law,config)` | Mixture with declared label; fixed budget | MI estimate between component label and the complete linear-angular state |

Config defaults are 10000 IID draws, seed 43 and the configured scientific RNG;
draw bounds are 2..1000000. Density-evaluation counts are full-law calls, not internal
component kernels. Work per call grows with positive-weight component count.
The graph adapter exposes `generateRandomness`, identity `generateValue`, `logDensity`
and its `logp` alias; all signatures are in the [API reference](api/README.md).

## Three common patterns

```scala
// 1. Multiple alternatives, without choosing one or averaging angles arithmetically.
val point = LinearAngular(Vector(.2), -3.1)
println(law.responsibilities(point))
println(law.linearMarginal.mean)

// 2. A complete observed joint value in an owned Figaro graph.
import com.cra.figaro.language.*
Universe.createNew()
val state = GaussVonMisesMixture(law)
state.observe(point)

// 3. Compare full mixture laws with independent fixed-budget Monte Carlo.
import com.cra.figaro.algorithm.sampling.MonteCarloInformation
val other = GaussVonMisesMixtureDistribution(Vector(.6, .4), Vector(a, b))
val config = MonteCarloInformation.Config(draws=20000, seed=42)
println(GaussVonMisesMixtureInformation.kl(law, other, config))
println(GaussVonMisesMixtureInformation.componentMutualInformation(law, config))
```

## Gotchas

- A mixture is not a product/fusion of PDFs. All component coordinate meanings and
  units must agree; equal vector sizes cannot establish that semantic condition.
- The joint density is periodic, but a Euclidean proposal must use exactly one
  angular chart. `asProposal` supplies that normalization; do not repeat the PDF on R.
- KL/Bhattacharyya between mixtures are not weighted averages of component divergences.
  Signed noisy KL/MI estimates are retained; MCSE is not a certified error bound or
  guaranteed stopping criterion. Zero overlap estimates can be unresolved.
- Label/state MI is NOT linear/angle MI. The existing single-GVM MI method cannot
  simply be averaged to obtain mixture partition MI. Use the separate [6.1 full-mixture
  partition diagnostic](GVM_MIXTURE_MI.md), with numerical/MCSE diagnostics.
- More components do not automatically mean more accuracy; parameter estimation,
  phase unwrapping, label ambiguity and rare components create additional risks.
- Fixed kernels are reusable across threads; elements and RNGs require separate
  ownership. State gradients, quadrature and conditionals of a mixture are not all
  inherited automatically from its components.

## Related

[Public scalar-linear fitting](GVM_MIXTURE_FITTING.md) and [full-mixture partition
MI](GVM_MIXTURE_MI.md) are additive 6.1 capabilities.

[Research and initial GMM comparison](MODELING_CAPABILITIES_ACCEPTANCE.md),
[GVM kernel](GAUSS_VON_MISES.md), [long-term research plan](GVM_MIXTURE_RESEARCH_PLAN.md),
[Monte Carlo information](MONTE_CARLO_INFORMATION.md), [Gaussian mixtures](DISTRIBUTION_CONSTRUCTIONS.md).
