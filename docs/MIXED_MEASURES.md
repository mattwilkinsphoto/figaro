# Finite atoms plus continuous uncertainty

## Overview

`MixedScalarDistribution` models a finite set of exact point masses plus an optional
continuous slab. It supports spike-and-slab and clipping/rectification without
pretending that an atom has a Lebesgue density. Existing count-only zero inflation
and hurdle models remain separate.

## Quick start

1. Build a continuous slab.
2. Add the atom probability.
3. Sample or use the fixed graph adapter.

```scala
import com.cra.figaro.library.atomic.continuous.*
import com.cra.figaro.language.*
val law = MixedScalarDistribution.spikeAndSlab(0, .2, GaussianDistribution(0, 1))
Universe.createNew()
val value = MixedScalarElement(law)
value.observe(0) // likelihood .2, NOT .2 + .8 * gaussian.density(0)
```

## API reference

| API | Parameters | Returns / example |
| --- | --- | --- |
| `MixedScalarDistribution(atoms,slab)` | Up to 128 distinct finite `(location,mass)` pairs with positive masses; optional `(ScalarDistribution,weight)` | Immutable normalized mixed law; masses plus slab weight sum to one within 1e-12 |
| `spikeAndSlab(location,probability,continuous)` | Finite location; probability in [0,1]; continuous law | Mixture; endpoint probabilities produce pure continuous or atomic laws |
| `clipped(base,lower,upper)` | Continuous law and ordered, possibly infinite bounds | Distribution of `max(lower,min(X,upper))`, including endpoint masses |
| `probabilityAt(x)` | Finite value | Exact singleton mass, zero elsewhere |
| `continuousLogDensity(x)` | Finite value | Weighted slab log density, including its conventional value at atoms |
| `logLikelihood(x)` | Finite exact observation | Log atom mass at atoms; otherwise log continuous sub-density |
| `cdf(x)` | Non-NaN endpoint, infinities allowed | `P(X<=x)` with inclusive atoms |
| `intervalProbability(lower,upper)` | Ordered non-NaN endpoints | `P(lower<X<=upper)`; endpoint atoms are handled explicitly |
| `sample(rng)` | Non-null caller-owned generator | Scalar draw; never retains the RNG |
| `MixedScalarElement(law)` | Fixed immutable law; contextual name/collection | Atomic observation-ready graph element |
| `MixedScalarInformation.kl(p,q)` | Two mixed laws in the same physical units | Full directed KL result/status in nats |
| `MixedScalarInformation.bhattacharyya(p,q)` | Two mixed laws | Full atom-plus-slab overlap divergence and propagated error estimate |

The adapter's `generateRandomness()` draws the law, `generateValue(x)` returns its
argument, and `logDensity(x)` delegates to `logLikelihood`. Its density is with respect
to the mixed reference measure, not an ordinary PDF. Standard compiler-generated
case-class methods are covered by the [API reference](api/README.md).

## Three common patterns

```scala
// 1. Absence versus a continuously varying quantity.
val spike = MixedScalarDistribution.spikeAndSlab(0, .3, LogNormalDistribution(0, .5))
println(spike.probabilityAt(0))

// 2. Rectification: negative values become zero, rather than being discarded.
val clipped = MixedScalarDistribution.clipped(GaussianDistribution(0, 1), 0, Double.PositiveInfinity)
println(clipped.probabilityAt(0)) // .5
println(clipped.intervalProbability(0, 1)) // excludes the atom at zero

// 3. Compare complete laws, not just their continuous parts.
val a = MixedScalarDistribution.spikeAndSlab(0, .2, GaussianDistribution(0, 1))
val b = MixedScalarDistribution.spikeAndSlab(0, .6, GaussianDistribution(0, 1))
println(MixedScalarInformation.kl(a, b))
```

## Gotchas

- The reference measure is Lebesgue measure plus counting measure at the union of
  relevant atom locations. Never add a density to a point probability.
- A positive atom absent from the comparison law makes directed KL infinite even
  when that law has positive continuous density at the location.
- Full comparisons decompose into atom terms and a weighted slab comparison.
  Slab numerical restrictions propagate; underflow is not declared disjoint support.
  Error estimates are heuristic, not certified bounds.
- Random atom locations can invalidate likelihood comparisons across hypotheses.
  Only a fixed adapter is exposed. If manually composing kernels with `Chain`, keep
  the atom locations/common measure fixed across alternatives; the library cannot
  prove this for arbitrary callbacks.
- A slab draw that rounds exactly onto an atom is refused, not silently relabeled.
- Clipping may refuse unrepresentable tiny atom masses or truncated normalizers.
  CDF/probability outputs can round; use explicit exceptions rather than assuming
  all extreme-tail mixed models are covered.
- No general quantile/moment API, compound Poisson-Gamma, arbitrary mixed-law
  convolution, atom-location learning, mixed-vector sampler, or automatic MI is added.
  A scalar law alone does not specify a joint distribution for MI.

## Related

[Observation models](OBSERVATION_MODELS.md), [existing constructions](DISTRIBUTION_CONSTRUCTIONS.md),
[information conventions](INFORMATION_METRICS_ROADMAP.md),
[acceptance](MODELING_CAPABILITIES_ACCEPTANCE.md).
