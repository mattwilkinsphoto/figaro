# Exact, interval, censored and rounded observations

## Overview

A reported number is not always an exact value. This module scores what was actually
reported: a density for an exact continuous measurement, or probability mass for an
interval or censoring event. It does not generate a latent value or silently truncate
the population. The implementation adds no dependency and leaves existing observe APIs unchanged.

## Quick start

1. Import the continuous kernels and choose a law.
2. Declare the reporting semantics.
3. Compute its log likelihood or attach the factor to an element of laws.

```scala
import com.cra.figaro.library.atomic.continuous.*
val law = GaussianDistribution(0, 1) // standard deviation, unlike Normal's variance
val reported = ScalarObservation.RightCensored(3)
val logLikelihood = ObservationLikelihood.logLikelihood(law, reported)
```

## API reference

| API | Parameters | Returns / example |
| --- | --- | --- |
| `ScalarObservation.Exact(value)` | Finite real value | Exact-density specification; `Exact(2)` |
| `Interval(lower,upper)` | Ordered non-NaN bounds; infinities allowed | Probability of `(lower,upper]`; `Interval(1,2)` |
| `LeftCensored(upper)` | Finite bound | Probability of `X<=upper`; `LeftCensored(0)` |
| `RightCensored(lower)` | Finite bound | Probability of `X>lower`; `RightCensored(3)` |
| `ObservationLikelihood.rounded(value,resolution)` | Finite center and positive bin width | Centered interval; rejects overflow/collapsed endpoints; `rounded(12,1)` |
| `logLikelihood(law,observation)` | Non-null continuous kernel and specification | Natural-log density or probability; singular exact density throws |
| `logInterval(law,lower,upper)` | Continuous law and ordered endpoints | Natural-log interval probability; `logInterval(law,40,41)` works for standard Gaussian |
| `attach(kernels,observation)` | Owning-graph `Element[D <: ScalarDistribution]` and specification | `Unit`; adds one log constraint, does not draw or register an observation element |

Parameters are validated when scored; malformed specifications may be constructed but
cannot be used. Callbacks/graphs retain normal Figaro ownership rules. The exhaustive
compiler-derived signatures are in the [API reference](api/README.md).

## Three common patterns

### Saturation or a declared detection threshold

```scala
val saturated = ObservationLikelihood.logLikelihood(law, ScalarObservation.RightCensored(3))
val notDetected = ObservationLikelihood.logLikelihood(law, ScalarObservation.LeftCensored(-2))
```

This assumes a known hard threshold and complete reporting of the event. A missing
record is not automatically a nondetection; uncertain detection and selection mechanisms
must be modeled explicitly. No sensor/report ingestion is provided.

### Rounded rather than exact measurements

```scala
val exact = ObservationLikelihood.logLikelihood(law, ScalarObservation.Exact(2))
val rounded = ObservationLikelihood.logLikelihood(law, ObservationLikelihood.rounded(2, .1))
```

The second is a bin probability, not the PDF at its midpoint. A rounding grid/tie
convention belongs to the reporting model; continuous endpoint choices have zero mass.

### Latent value plus measurement error

```scala
import com.cra.figaro.language.*
import com.cra.figaro.algorithm.sampling.Importance
Universe.createNew()
val latent = Normal(0, 1) // prior variance
val measurement = Apply(latent, (x: Double) => GaussianDistribution(x, 1))
ObservationLikelihood.attach(measurement, ScalarObservation.Exact(1))
val inference = Importance(40000, latent)
try {
  inference.start()
  println(inference.expectation(latent, (x: Double) => x)) // approximately .5
} finally inference.kill()
```

## Gotchas

- Attaching twice adds two independent factors. Do not also observe a sampled node
  for the same datum; that double-counts evidence.
- Exact densities have units and may exceed one. Censoring probabilities cannot.
- Gaussian log tails remain usable when ordinary probabilities underflow; an
  asymptotic Mills series is used only beyond 26 standard deviations. Generic kernels
  use their existing CDF/survival, with explicit refusal when numerical mass is unresolved.
- Very narrow intervals can be cancellation-limited. A heuristic floating-point
  separation guard refuses them; it is not a certified relative-error bound.
- Disjoint support returns negative infinity. Numerical failure inside support is
  not declared impossible. Custom gapped distributions may conservatively refuse.
- These APIs take continuous laws, not [mixed measures](MIXED_MEASURES.md).
  Truncation conditions the population; censoring changes what is reported.
- Graph likelihood weighting is tested; no new exact-factor conversion or learning
  contract is implied. Arbitrary graph state is not made thread-safe.

## Related and basis

[Stan censoring](https://mc-stan.org/docs/stan-users-guide/truncation-censoring.html)
and [measurement-error models](https://mc-stan.org/docs/stan-users-guide/measurement-error.html)
provide the modeling baseline. Formulas are independently implemented, not copied code.
Related: [mixed measures](MIXED_MEASURES.md), [constructions](EXTENDED_CONSTRUCTIONS.md),
[modeling acceptance](MODELING_CAPABILITIES_ACCEPTANCE.md).
