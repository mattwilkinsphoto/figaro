# Circular von Mises foundation

## Overview

Use von Mises for uncertainty in a direction: headings, bearings, phase or orientation
around a circle. Angles differing by a full turn describe the same direction. A normal
distribution on the real line does not express this equivalence: an arithmetic mean of
-179 and +179 degrees is zero, while their circular mean is at the shared 180-degree boundary.

This implementation supplies a reusable immutable numerical kernel, Figaro elements with
fixed or stochastic parameters, and circular sample summaries. It is **ordinary circular
von Mises**, not yet the joint linear-angular Gauss-von Mises distribution. The latter
builds on this foundation; see the [GVM plan](GAUSS_VON_MISES_PLAN.md).

All angles are radians. Concentration `kappa` ranges from 0 (uniform) to `1e8` inclusive.
It is not variance or standard deviation. Sampling returns `[-Pi, Pi)`, while density
methods accept any finite angular representative. Very large floating-point angles can
already have lost meaningful phase precision; normalization cannot recover it.

## Quick start: three steps

1. Use this checkout's Scala 3/JDK 17 [build setup](BUILDING.md).
2. Run `sbt "examples / Compile / runMain com.cra.figaro.example.documentation.VonMisesExample"`.
3. Adapt the [complete example](../FigaroExamples/src/main/scala/com/cra/figaro/example/documentation/VonMisesExample.scala)
   or one of the patterns below. Kill algorithms when finished; parallel workers own
   separate universes. Existing RC1 bundles do not contain this new API: build/publish
   the feature checkout, rather than reuse a previously distributed binary.

## Three common patterns

### 1. Sample headings without constructing a Figaro model

```scala
import com.cra.figaro.library.atomic.continuous.VonMisesDistribution
import com.cra.figaro.util.CircularStatistics

val distribution = VonMisesDistribution(math.toRadians(179.0), 20.0)
val rng = new scala.util.Random(42L)
val angles = Vector.fill(10000)(distribution.sample(rng))
val summary = CircularStatistics.summarize(angles)
println(summary.meanDirection.map(math.toDegrees))
println(summary.meanResultantLength)
```

The kernel stores no random stream or mutable model state, so it can be reused across
workers if each worker supplies its own RNG. These are direct prior draws, not a Markov
chain. Higher concentration narrows uncertainty; no normal approximation is substituted
by the sampler. `meanResultantLength` describes concentration, not effective sample size.

### 2. Infer a direction from a noisy circular observation

```scala
import com.cra.figaro.language.*
import com.cra.figaro.library.atomic.continuous.VonMises
import com.cra.figaro.algorithm.sampling.Importance

val u = Universe.createNew()
val direction = Select(0.5 -> 3.1, 0.5 -> 0.0)(using "direction", u)
val reading = VonMises(direction, 4.0)(using "reading", u)
reading.observe(-3.1)
val algorithm = Importance(10000, direction)
try {
  algorithm.start()
  println(algorithm.probability(direction, 3.1)) // near 1: 3.1 and -3.1 are close on a circle
} finally {
  if (algorithm.isActive) algorithm.kill()
  u.clear()
}
```

This is an actual observed element with conditional likelihoods, not an `Apply` shortcut
that merely wraps normally distributed samples. Stochastic location and/or concentration
are evaluated through non-caching chains. Invalid dynamic values fail when evaluated.

### 3. Use MCMC with a circular likelihood and circular projections

```scala
import com.cra.figaro.algorithm.sampling.{MetropolisHastings, ProposalScheme}
import com.cra.figaro.language.*
import com.cra.figaro.library.atomic.continuous.VonMises

val u = Universe.createNew()
val direction = VonMises(0.0, 2.0)(using "direction", u)
// Observation 1 radian, concentration 3; omit a constant normalizer for this fixed likelihood.
direction.addLogConstraint((theta: Double) => 3.0 * math.cos(1.0 - theta))
val algorithm = MetropolisHastings(25000, ProposalScheme.default(using u), 1000, direction)
try {
  algorithm.start()
  val sine = algorithm.expectation(direction, (x: Double) => math.sin(x))
  val cosine = algorithm.expectation(direction, (x: Double) => math.cos(x))
  println(math.atan2(sine, cosine))
} finally {
  if (algorithm.isActive) algorithm.kill()
  u.clear()
}
```

For multiple chains use separate model factories and sine/cosine `Observable` projections
with [multi-chain MCMC](MULTI_CHAIN_MCMC.md). For direct weighted parallel inference use
[parallel importance](PARALLEL_PERFORMANCE.md); the runnable example demonstrates it.
Do not share a mutable Figaro element just because its numerical kernel is immutable.

## Public API reference

Imports: `library.atomic.continuous.{VonMisesDistribution, VonMises}`,
`util.CircularStatistics`, and `language.HasLogDensity` under `com.cra.figaro`.
All factories throw `IllegalArgumentException` on invalid fixed parameters. The table
covers handwritten methods; the [compiler reference](api/README.md) also lists inherited
and generated members. Kernel and atomic constructors are intentionally factory-only.

| Entry point | Parameters and return contract | Example |
| --- | --- | --- |
| `VonMisesDistribution(location, kappa)` | Finite radians, finite concentration `[0,1e8]`; immutable normalized kernel | `VonMisesDistribution(0.0, 2.0)` |
| `kernel.logDensity(angle)` | Finite radians; finite periodic log density per radian | `kernel.logDensity(math.Pi)` |
| `kernel.density(angle)` | Finite radians; `exp(logDensity)`, possibly zero through underflow | `kernel.density(0.0)` |
| `kernel.sample(rng, maxAttempts = 100000)` | Non-null caller-owned `scala.util.Random`, positive attempt cap; one canonical angle; exhaustion throws `IllegalStateException`, interruption throws `CancellationException` without clearing interrupt status | `kernel.sample(new scala.util.Random(42L))` |
| `kernel.meanResultantLength` | No arguments; theoretical `I1(kappa)/I0(kappa)` | `kernel.meanResultantLength` |
| `kernel.meanDirection` | No arguments; `Some(location)` for positive concentration, `None` for uniform | `kernel.meanDirection` |
| `VonMises(location: Double, kappa: Double)` | Valid fixed parameters and contextual `Name[Double]`/`ElementCollection`; returns `AtomicVonMises` | `VonMises(0.0, 2.0)(using "heading", u)` |
| `VonMises(location: Element[Double], kappa: Double)` | Stochastic finite-radian center, fixed valid concentration, same contexts; returns `Element[Double]` chain | `VonMises(Constant(0.0), 2.0)` |
| `VonMises(location: Double, kappa: Element[Double])` | Fixed finite center, stochastic valid concentration, same contexts; returns `Element[Double]` chain | `VonMises(0.0, Constant(2.0))` |
| `VonMises(location: Element[Double], kappa: Element[Double])` | Both dynamic parameters, same contexts; returns `Element[Double]` chain | `VonMises(Constant(0.0), Constant(2.0))` |
| `atomic.generateRandomness()` | No arguments; samples from the scoped Figaro RNG | `withRandomSeed(42L) { atomic.generateRandomness() }` |
| `atomic.generateValue(rand)` | Angular randomness `Double`; returns it unchanged | `atomic.generateValue(0.2)` |
| `atomic.logDensity(angle)` / `atomic.logp(angle)` | Finite radians; equivalent kernel log-density calls | `atomic.logp(0.2)` |
| `CircularStatistics.normalize(angle)` | Finite radians; canonical `[-Pi,Pi)` value, positive zero | `CircularStatistics.normalize(3 * math.Pi)` |
| `CircularStatistics.difference(angle, reference)` | Two finite angles; shortest signed displacement, antipodal tie `-Pi` | `CircularStatistics.difference(-3.1, 3.1)` |
| `CircularStatistics.summarize(angles, minResultant = 1e-12)` | Nonempty `IterableOnce[Double]`, finite angles, threshold `[0,1]`; `Summary(count, meanDirection, meanResultantLength)`. Direction is `None` when resultant <= threshold | `CircularStatistics.summarize(Vector(-3.1, 3.1))` |
| `HasLogDensity.logDensity(value)` | Implemented by an element; finite log score or `-Infinity` for zero density, no NaN/+Infinity in likelihood weighting | `atomic.logDensity(0.2)` |
| `HasLogDensity.density(value)` | Default exponentiation of the log score | `atomic.density(0.2)` |
| `HasLogDensity.nextRandomness(old)` | Prior proposal; returns `(next, reverse/forward proposal ratio, new/old density ratio)`; fails with `ArithmeticException` if both legacy ratios cannot be represented | `atomic.nextRandomness(0.2)` |
| `VonMisesExample.main(args)` | Empty string array; prints and checks three example workflows, returns `Unit` | `VonMisesExample.main(Array.empty[String])` |

Kernel fields `location` and `kappa`, atomic `distribution`, and
`VonMisesDistribution.MaxKappa` are read-only. `CircularStatistics.Summary` is an
immutable result case class; its fields are a count, optional direction and resultant.
Direct case-class construction is not a substitute for validated `summarize` computation.

## Inference compatibility and gotchas

| Path | This milestone's evidence / boundary |
| --- | --- |
| Direct/forward samples | Seeded sampling, all concentration regimes, bounded rejection and interruption tests |
| Importance observation | Atomic tail likelihood and conditional-location/concentration posterior tests; explicit `HasLogDensity` takes precedence over `log(density)` |
| Ordinary MH | Fixed-parameter prior proposals plus log constraints checked against independent conjugate-posterior quadrature |
| Parallel importance | Separate owned models, seeded circular expectation checked |
| Multi-chain MH | Separate owned models, exact seeded sine/cosine traces across worker counts checked; retain existing evidence restrictions |
| Annealing, factored/lazy inference, learning, dynamic Create | Not validated or registered as new supported workflows; no blanket compatibility claim |
| Vector slice samplers | No automatic circular adapter: a periodic density repeated across all of R is not a normalized target |

- The likelihood-weighting change is opt-in through `HasLogDensity`; existing
  `HasDensity` distributions keep their previous path. It prevents premature underflow
  for the new element but cannot prevent weight degeneracy from genuinely rare evidence.
- MH still has a legacy **ratio** interface. Log-density proposals avoid `0/0` but cannot
  represent arbitrarily large reciprocal ratios. Such proposals fail explicitly; they
  are not clipped into a biased result. No global MH rewrite occurs here.
- `observe` preserves the supplied numeric value. Its score is periodic, but downstream
  code and exact-value queries see that representative. Normalize before observing if
  the rest of the model assumes canonical angles. Core equality-based conditions and
  conflicting observations are not globally redefined as angular equivalence.
- Uniform or nearly cancelling directions have no reliable identified sample direction.
  `summarize` uses a numerical threshold, not a statistical significance test. It is for
  equally weighted samples, not raw weighted-importance draws.
- Do not apply existing scalar-mean stopping criteria directly to wrapped angles.
  Sine/cosine diagnostics help, but do not prove that every mode was explored.
- CDF, quantiles, fitting, circular confidence intervals, spherical laws and joint GVM
  are deliberately deferred. No new runtime dependency or existing RC1 replacement.

## Numerical method, verification and provenance

The kernel evaluates the scaled Bessel normalizer with the
[NIST power series](https://dlmf.nist.gov/10.25.E2) through concentration 50 and a
[large-argument expansion](https://dlmf.nist.gov/10.40.E1) above it. The log density uses
`-2*kappa*sin(delta/2)^2`, avoiding both exponentiation of `kappa` and cancellation at
the mode. Sampling uses an exact uniform-envelope rejection method through concentration
1 and an independently implemented, cancellation-resistant rearrangement of
[Best-Fisher (1979)](https://doi.org/10.2307/2346732) above it. No third-party code was copied.

[VonMisesRegressionTest](../Figaro/src/test/scala/com/cra/figaro/test/modernization/VonMisesRegressionTest.scala)
checks independent 80-digit `mpmath==1.3.0` Bessel fixtures, numerical integration,
550,000 seeded sample draws, circular moments, posterior likelihoods and concurrency.
Fixtures bracket both method switches. Large-concentration tests also check rescaled
angular variance to catch a sampler incorrectly collapsing to its location.

Run `sbt "figaro / Test / testOnly com.cra.figaro.test.modernization.VonMisesRegressionTest"`.
The optional [reference generator](../tools/von_mises_reference.py) prints the fixture
triples `(kappa, log density at the mode, mean resultant length)` without writing files:
install `mpmath==1.3.0` in an isolated Python environment, then run
`python -B tools/von_mises_reference.py`. Python/mpmath are not required for library use
or ordinary CI; the Scala suite contains the checked fixtures.

Related: [distribution inventory](DISTRIBUTION_SUPPORT.md), [roadmap](../ROADMAP.md),
[wishlist](../WISHLIST.md), [GVM plan](GAUSS_VON_MISES_PLAN.md),
[parallel importance](PARALLEL_PERFORMANCE.md), [multi-chain MCMC](MULTI_CHAIN_MCMC.md).
