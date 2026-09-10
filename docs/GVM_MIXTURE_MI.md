# Linear/angular mutual information of a GVM mixture

## Overview

`GaussVonMisesMixtureMutualInformation` estimates dependence between the **entire
linear vector** and **angle** in one fixed mixture. This completes the first bounded
mixture partition-MI capability alongside the existing full-law KL/Bhattacharyya and
component-label MI. Units are nats; these quantities answer different questions.

Even if every component has independent linear/angle coordinates, the complete
mixture can be dependent: the linear value and angle may both reveal which component
generated the point. Averaging single-component MI loses this dependence.

## Quick start (three steps)

```scala
import com.cra.figaro.library.atomic.continuous.*
// 1. Construct a fixed normalized mixture.
def component(m: Double,a: Double) = GaussVonMisesDistribution(
  Vector(m), Vector(Vector(1.0)), a, Vector(0.0), Vector(Vector(0.0)), 10)
val law = GaussVonMisesMixtureDistribution(Vector(.5,.5),
  Vector(component(-3,-1),component(3,1)))
// 2. Request fixed work, independently seeded.
val result = GaussVonMisesMixtureMutualInformation.compute(law)
// 3. Inspect status and BOTH error diagnostics before using the estimate.
println((result.status,result.value,result.mcse,result.marginalLogErrorEstimate))
```

## API reference

| API | Parameters | Returns / example |
| --- | --- | --- |
| `compute(law, config)` | Fixed GVM mixture; numerical path requires active kappa<=50, linear dimension<=32, canonical beta/Gamma magnitudes<=1000 | `Result`; `compute(law, Config(draws=20000, seed=43))` |
| `Config` | `draws=10000` (2..1000000), `seed=42`, `harmonics=128` (8..256), `maxLogDensityError=1e-7` (0..0.01, excluding zero) | Immutable fixed work policy |
| `Result` | `status`, optional `value` and `mcse`, `marginalLogErrorEstimate`, `completedDraws`, `config` | `Estimated`, `UnsupportedRange`, or `NumericallyUnresolved`; no value on refusal |

The normalized Fourier coefficients of the angular marginal are weighted sums of
component coefficients. Gaussian quadratic characteristic functions evaluate the
linear integral analytically; a positive Bessel series evaluates the circular part.
Joint IID samples then estimate `log p(x,angle) - log p(x) - log p(angle)`.
The linear mixture marginal is exact. Angular truncation has an analytic tail
allowance, but floating-point allowance is heuristic; this is not certified integration.

## Three common patterns

```scala
// 1. Compare dependence with component ambiguity; these are NOT interchangeable.
println(GaussVonMisesMixtureMutualInformation.compute(law))
println(GaussVonMisesMixtureInformation.componentMutualInformation(law))

// 2. Increase fixed sampling work to assess Monte Carlo variability.
val more = GaussVonMisesMixtureMutualInformation.compute(law,
  GaussVonMisesMixtureMutualInformation.Config(draws=50000,seed=44))
println(more.mcse)

// 3. Tighten the allowed angular marginal error independently of sample count.
val tighter = GaussVonMisesMixtureMutualInformation.compute(law,
  GaussVonMisesMixtureMutualInformation.Config(harmonics=192,maxLogDensityError=1e-8))
println(tighter.status)
```

## Gotchas

- MCSE describes IID sampling variability, not a guaranteed confidence interval or
  stopping rule. Increasing draws does not fix unresolved angular density arithmetic.
- Signed noisy estimates are retained, not clamped to zero. Exact common-independent
  angular components take an analytic zero shortcut without sampling.
- A positive-density reconstruction too close to its numerical allowance is refused;
  failed draws are not dropped or replaced. Partial work is reported without a value.
- A fitted mixture may exceed the MI concentration cap; that is an explicit unsupported
  input, not permission to silently modify the fitted concentration.
- Full-mixture angular entropy is not the average of component entropies. A mixture
  partition is also not a divergence between two different laws.
- Kernel state is immutable; each call owns its RNG. Interruption propagates.

## Related

[Single-GVM MI](GVM_MUTUAL_INFORMATION.md), [mixture fitting](GVM_MIXTURE_FITTING.md),
[cross-family information](INFORMATION_METRICS_ROADMAP.md),
[6.1 acceptance and independent positive-integration check](RELEASE_6_1.md).
