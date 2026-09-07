# Joint Gauss-von Mises: local development preview

Status: implemented on `modernize/gauss-von-mises`, **not publicly released or merged**.
The release-review gate below remains open. The circular foundation is separately
available on main at `fea8b999`, with [passing CI](https://github.com/mattwilkinsphoto/figaro/actions/runs/34138540587).

## Overview

Use this distribution when a real vector and an angle are dependent. Ordinary
[von Mises](VON_MISES.md) describes only an angle; independent Normal and von Mises
elements cannot by themselves express a direction that bends as the linear state changes.
This kernel implements Horwood-Poore (2014), Definition 3.1, using:

```text
P = A A^T                         A is lower triangular
z = solve(A, x - mean)
center(x) = alpha + beta^T z + 0.5 z^T gamma z
p(x, angle) = Normal(x; mean, P) * VonMises(angle; center(x), kappa)
```

The result is an immutable `LinearAngular` value. A separate immutable numeric kernel
supports direct sampling and scoring without a Figaro universe. The Figaro adapter
supports complete joint observations and tested sampling-inference paths. It does not
implement a GVM filter, orbit propagator, report-fusion algorithm or parameter fitting.

## Quick start in three steps

1. Use the local development branch with the [Scala 3/JDK 17 build](BUILDING.md).
2. Run `sbt "examples / Compile / runMain com.cra.figaro.example.documentation.GaussVonMisesExample"`.
3. Adapt a pattern below, retaining the documented coordinate order and ownership rules.
   Existing RC1 bundles and circular-only main do not include these joint APIs.

## Three common patterns

### 1. Draw a curved linear-angular prior directly

```scala
import com.cra.figaro.library.atomic.continuous.*
import com.cra.figaro.util.CircularStatistics

val kernel = GaussVonMisesDistribution(
  mean = Vector(0.0), covariance = Vector(Vector(1.0)),
  alpha = 3.0, beta = Vector(0.7), gamma = Vector(Vector(0.4)), kappa = 4.5)
val rng = new scala.util.Random(42L)
val draws = Vector.fill(10000)(kernel.sample(rng))
val residuals = draws.map(p => CircularStatistics.difference(
  p.angle, kernel.conditionalLocation(p.linear)))
println(CircularStatistics.summarize(residuals))
```

Here `center(x) = 3 + 0.7*x + 0.2*x*x`. Turning off both coupling parameters makes
the vector and angle independent. The residual angle, not the marginal angle, has
von Mises concentration 4.5. Direct prior sampling needs no burn-in or MCMC:
draw a standard Gaussian vector, transform it with `A`, then draw the conditional angle.
Reuse the kernel across workers, but give each worker a separate random stream.

### 2. Score complete observations in a conditional model

```scala
import com.cra.figaro.language.*
import com.cra.figaro.algorithm.sampling.Importance
import com.cra.figaro.library.atomic.continuous.*

val u = Universe.createNew()
def kernel(b: Double) = GaussVonMisesDistribution(Vector(0.0), Vector(Vector(1.0)),
  3.0, Vector(b), Vector(Vector(0.4)), 4.5)
val positive = Flip(0.4)(using "positive", u)
val reading = NonCachingChain(positive, (b: Boolean) =>
  GaussVonMises(kernel(if (b) 0.7 else -0.7))(using "", u))
reading.observe(LinearAngular(Vector(1.0), 3.1))
val algorithm = Importance(20000, positive)
try {
  algorithm.start()
  println(algorithm.probability(positive, true))
} finally {
  if (algorithm.isActive) algorithm.kill()
  u.clear()
}
```

Unlike observing an arbitrary `Apply` projection, this observation has the correct
joint density. The adapter consumes a validated fixed kernel; stochastic parameters
can be composed explicitly as above. No combinatorial set of parameter overloads is
introduced. Observing only part of a joint state is not a supported shortcut: build an
explicit conditional model or supply a correctly defined likelihood for noisy evidence.

### 3. Use a joint prior with additional likelihood information

```scala
import com.cra.figaro.language.*
import com.cra.figaro.algorithm.sampling.{MetropolisHastings, ProposalScheme}
import com.cra.figaro.library.atomic.continuous.*

val u = Universe.createNew()
val kernel = GaussVonMisesDistribution(Vector(0.0), Vector(Vector(1.0)),
  3.0, Vector(0.7), Vector(Vector(0.4)), 4.5)
val state = GaussVonMises(kernel)(using "state", u)
state.addLogConstraint((p: LinearAngular) => -0.5 * math.pow(p.linear(0) - 1, 2))
val algorithm = MetropolisHastings(30000, ProposalScheme.default(using u), 1000, state)
try {
  algorithm.start()
  println(algorithm.expectation(state, (p: LinearAngular) => p.linear(0))) // near 0.5
  val s = algorithm.expectation(state, (p: LinearAngular) => math.sin(p.angle))
  val c = algorithm.expectation(state, (p: LinearAngular) => math.cos(p.angle))
  println(math.atan2(s, c))
} finally {
  if (algorithm.isActive) algorithm.kill()
  u.clear()
}
```

This is ordinary Figaro posterior sampling, not a closed-form GVM measurement-update
method. The posterior need not belong to the GVM family. A prior proposal can mix poorly
when evidence is much tighter than the prior; adding a joint distribution is not an
automatic performance gain. For several chains use separate elements/universes with
[multi-chain MCMC](MULTI_CHAIN_MCMC.md) and scalar linear/sine/cosine projections.

## Public API reference

Import `com.cra.figaro.library.atomic.continuous.*`. All methods below are handwritten;
the [compiler reference](api/README.md) also records inherited/generated functions.

| API | Parameters and returns | Example |
| --- | --- | --- |
| `LinearAngular(linear, angle)` | Nonempty finite `Vector[Double]`, finite radians; immutable value retaining the numeric angle; invalid inputs throw `IllegalArgumentException` | `LinearAngular(Vector(0.2), 3.1)` |
| `GaussVonMisesDistribution(mean, covariance, alpha, beta, gamma, kappa)` | Finite nonempty mean; same-sized finite covariance, beta and gamma; exactly symmetric matrices, positive-definite covariance; finite radians and concentration `[0,1e8]`; returns validated immutable snapshots | See pattern 1 |
| `kernel.conditionalLocation(linear)` | Finite vector of the configured dimension; returns conditional center in `[-Pi,Pi)` | `kernel.conditionalLocation(Vector(0.2))` |
| `kernel.linearLogDensity(linear)` | Matching finite vector; Gaussian marginal log density, possibly `-Infinity` in extreme tails | `kernel.linearLogDensity(Vector(0.2))` |
| `kernel.logDensity(value)` | Non-null matching state; joint log density with periodic angular scoring | `kernel.logDensity(LinearAngular(Vector(0.2), 3.1))` |
| `kernel.density(value)` | Same input; exponentiated score, possibly zero or infinity due to floating-point limits | `kernel.density(LinearAngular(Vector(0.2), 3.1))` |
| `kernel.sample(rng, maxAttempts = 100000)` | Caller-owned non-null `scala.util.Random`, positive circular attempt budget; detached immutable joint draw with canonical angle | `kernel.sample(new scala.util.Random(42L))` |
| `GaussVonMises(distribution)` | Non-null kernel plus contextual `Name[LinearAngular]` and `ElementCollection`; returns `AtomicGaussVonMises` | `GaussVonMises(kernel)(using "state", u)` |
| `atomic.generateRandomness()` | No arguments; joint draw using Figaro's scoped RNG | `atomic.generateRandomness()` |
| `atomic.generateValue(rand)` | Joint randomness; returns it unchanged | `atomic.generateValue(LinearAngular(Vector(0.2), 3.1))` |
| `atomic.logDensity(value)` / `atomic.logp(value)` | Matching state; kernel log-density aliases | `atomic.logp(LinearAngular(Vector(0.2), 3.1))` |
| `atomic.density(value)` / `atomic.nextRandomness(old)` | Inherited [HasLogDensity](VON_MISES.md) contracts; exponential density / prior proposal with reciprocal legacy ratios | `atomic.nextRandomness(LinearAngular(Vector(0.2), 3.1))` |
| `GaussVonMisesExample.main(args)` | Empty string array; prints/checks three workflows, returns Unit and disposes models | `GaussVonMisesExample.main(Array.empty[String])` |

Kernel fields `mean`, `covariance`, `alpha`, `beta`, `gamma`, `kappa`, `dimension`,
atomic `distribution` and result `linear`/`angle` are immutable. Parameters passed in
mutable collections are copied; the kernel holds no RNG or mutable scratch arrays.

## Gotchas and numerical limits

- Covariance diagonals are **variances**, not standard deviations. `gamma` is a coupling
  matrix, not a covariance: it may be indefinite. Neither matrix is silently symmetrized.
- Coupling parameters refer to the whitened vector from a **lower** Cholesky factor.
  Reordering coordinates can require changing coupling parameters beyond a permutation.
- `alpha` is the center at `x = mean`, not generally the marginal circular mean.
  At zero concentration the angle is uniform and independent, regardless of coupling.
- No singular-covariance support, regularization or arbitrary condition-number guarantee.
  Factorization uses correlation scaling to accommodate differing units. Unrepresentable
  factors fail construction; runtime overflow in solves/coupling/sampling throws
  `ArithmeticException`. Finite inputs alone do not guarantee usable numerical precision.
- Large offsets with tiny variance can lose deviations in floating-point coordinates;
  very large coupling phases can lose angular precision. Rescale/recenter before use.
  The tested extreme covariance case is diagonal `1e-280, 1e280` about zero, not a
  certification of every ill-conditioned matrix or remote tail.
- Cancellation throws `CancellationException` and preserves interruption. The circular
  rejection cap throws `IllegalStateException`; failures never return a partial draw.
- Observations retain the supplied angle, and case-class equality is numeric rather than
  circular equivalence. Normalize explicitly when equality needs a canonical representative.
- Direct log scoring avoids premature density underflow. Legacy MH still needs both
  reciprocal ratios representable; it fails rather than clipping extreme proposals.
- Forward sampling, complete observation weighting, ordinary MH, parallel importance and
  multi-chain prior projections have focused tests. Partial exact observations, factored/
  lazy inference, learning, annealing, dynamic Create and automatic circular vector-sampler
  adapters are not newly validated. Raw angular averages/stopping criteria are inappropriate.

## Verification and release gate

The [13 focused regressions](../Figaro/src/test/scala/com/cra/figaro/test/modernization/GaussVonMisesRegressionTest.scala)
pass locally: independent 80-digit joint scores, two-dimensional normalization, uncoupled
and uniform limits, 120,000 prior draws, Gaussian moments and conditional circular
residuals, disparate units, input snapshots, failure paths, real evidence weighting,
posterior projections and exact seeded traces across worker counts.
All 192 modernization regressions, the executable example, Scala API generation and
local thin-library packaging also pass. Joint remote CI has not run: this branch has
not been pushed. Existing whole-library historical-suite limits remain unchanged.
The [optional oracle](../tools/gauss_von_mises_reference.py) prints fixtures using
`mpmath==1.3.0`; it is not a Figaro runtime dependency. Run it with `python -B` in an
isolated environment. It uses an explicit known factor and inverse/determinant formulas,
independent of the production triangular-solve implementation.

The mathematical specification was checked against Horwood and Poore,
[SIAM/ASA JUQ 2 (2014), Definition 3.1](https://doi.org/10.1137/130917296).
No third-party implementation was copied. No new runtime dependency or RC1 replacement.

Release review remains open. On 2026-09-07, [Google Patents' record for US8909586B2](https://patents.google.com/patent/US8909586B2/en)
reported active/reinstated status, with an explicit disclaimer that this is not a legal
conclusion. Its published claims include report fusion constrained by diffeomorphisms.
This development scope does not implement that workflow, but this distinction is **not
patent clearance**. A qualified scope/status review or explicit maintainer direction is
needed before public distribution of this joint milestone. Tracking/filtering remains a
separate future gate. The circular-only release is not held by this development checkpoint.

Related: [circular foundation](VON_MISES.md), [milestone plan](GAUSS_VON_MISES_PLAN.md),
[distribution inventory](DISTRIBUTION_SUPPORT.md), [roadmap](../ROADMAP.md),
[runnable source](../FigaroExamples/src/main/scala/com/cra/figaro/example/documentation/GaussVonMisesExample.scala).
