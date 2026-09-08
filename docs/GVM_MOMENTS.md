# GVM moments and exact angular conditionals

## Overview

Use these immutable numeric helpers when you need summaries of a fixed Gauss-von Mises
(GVM) kernel or the angular distribution at a specified linear state. They avoid drawing
thousands of samples merely to estimate supported expectations. They do not fit a GVM
to data or assume that an arbitrary Figaro posterior is itself a GVM.

`kernel.moments` computes marginal circular summaries and physical-coordinate mixed
moments analytically. `kernel.conditionalAngle(x)` returns the exact von Mises law
of the angle given the **entire** linear vector. Neither call creates a Figaro element
or consumes randomness. Sampling a returned conditional kernel uses your supplied RNG.

The APIs belong to the current source development preview. They are not present in
the earlier immutable RC1 integration bundle. See [building](BUILDING.md), the
[joint guide](GAUSS_VON_MISES.md) and [roadmap](../ROADMAP.md) for integration status.

## Quick start in three steps

1. Construct a kernel:

   ```scala
   import com.cra.figaro.library.atomic.continuous.*
   val kernel = GaussVonMisesDistribution(Vector(0.0), Vector(Vector(1.0)),
     3.0, Vector(0.7), Vector(Vector(0.4)), 4.5)
   ```

2. Store its summaries: `val summary = kernel.moments`, then read
   `summary.meanDirection` and `summary.linearSin`.
3. Fix the linear state: `val angular = kernel.conditionalAngle(Vector(1.0))`.
   Use `angular.logDensity(3.1)` or `angular.sample(new scala.util.Random(42L))`.

## Public API reference

| API | Parameters | Returns / meaning | Example |
| --- | --- | --- | --- |
| `kernel.moments` | None; uses the kernel's validated fixed parameters | Immutable `GaussVonMisesMoments`; recomputes each call, no sampling | `val summary = kernel.moments` |
| `kernel.conditionalAngle(linear)` | Non-null finite `Vector[Double]` of length `kernel.dimension`, in physical units | Immutable `VonMisesDistribution`, same concentration and the appropriate conditional center | `kernel.conditionalAngle(Vector(1.0))` |
| `GaussVonMisesMomentsExample.main(args)` | Empty `Array[String]` | Unit; prints and checks three workflows, cleans up its Figaro model | `GaussVonMisesMomentsExample.main(Array.empty[String])` |

The result has immutable fields rather than additional callable methods. Its constructor
is library-internal; obtain it from a kernel.

| Result field | Exact quantity | Units / shape |
| --- | --- | --- |
| `meanCos` | E[cos(theta)] | Dimensionless scalar |
| `meanSin` | E[sin(theta)] | Dimensionless scalar |
| `meanResultantLength` | Magnitude of E[exp(i theta)] | Dimensionless scalar in [0,1], not variance |
| `logMeanResultantLength` | Natural log of that magnitude | Finite for supported positive-concentration calculations; -Infinity at zero concentration |
| `meanDirection` | Argument of the first circular moment | `Option[Double]`, radians in [-Pi,Pi); None for uniform angles or an underflowed resultant |
| `linearCos` | E[x cos(theta)] | Vector, one entry per physical linear coordinate |
| `linearSin` | E[x sin(theta)] | Vector, one entry per physical linear coordinate |
| `linearLinearCos` | E[x x^T cos(theta)] | Symmetric matrix; entry (i,j) has units of x_i times x_j |
| `linearLinearSin` | E[x x^T sin(theta)] | Same shape and units as above |

For example, `summary.linearSin(0)` is E[x_0 sin(theta)], not E[x_0]*E[sin(theta)].
`summary.linearLinearCos(0)(1)` is E[x_0 x_1 cos(theta)], not a covariance entry.
The Gaussian marginal's mean and covariance are already available as `kernel.mean`
and `kernel.covariance`; do not reinterpret either mixed matrix as that covariance.

The conditional kernel supports `location`, `kappa`, `meanDirection`,
`meanResultantLength`, `density(angle)`, `logDensity(angle)` and
`sample(rng, maxAttempts = 100000)`. Their full parameter, return and failure contracts
are in the [circular API guide](VON_MISES.md). At zero concentration its irrelevant
location is set to zero and `meanDirection` is None. No sampling method shares an RNG.

## Three common patterns

### 1. Replace sampled summaries with supported analytic expectations

```scala
val summary = kernel.moments
println(summary.meanDirection)       // marginal circular direction
println(summary.meanResultantLength) // marginal concentration summary
println(summary.linearSin(0))        // raw mixed expectation
```

Previously you could sample many joint states and average their sines, cosines and
products with x. For these particular quantities the analytic result removes Monte
Carlo error. It does not remove floating-point error or evaluate every arbitrary
function of a state. Store one result if you need several fields: each `.moments`
call performs its own matrix calculation.

Do not substitute `kernel.alpha` for the marginal mean direction. `alpha` is the
conditional center at the Gaussian mean. As a simple example, with beta=0 and scalar
gamma=1, the marginal direction shifts by Pi/8 from alpha (modulo one turn), while the
conditional center at x=mean remains alpha.

To obtain Cov(x_i, sin(theta)), subtract
`kernel.mean(i) * summary.meanSin` from `summary.linearSin(i)`. The corresponding
cosine formula is analogous. This is a covariance with a circular embedding coordinate,
not an ordinary covariance with an unwrapped angle or a normalized correlation.

### 2. Hold the linear state fixed and inspect its angular uncertainty

```scala
val x = Vector(1.0)
val conditional = kernel.conditionalAngle(x)
println(conditional.location)
println(conditional.logDensity(3.1))
val draw = conditional.sample(new scala.util.Random(42L))
```

This evaluates theta given x, rather than drawing both coordinates from the joint prior.
For this example the conditional center is 3.9 radians, represented canonically as
approximately -2.383185. The concentration remains 4.5; marginal angular dispersion
can be greater because averaging over x mixes different conditional centers.

The exact factorization is

```scala
val jointLog = kernel.linearLogDensity(x) + conditional.logDensity(3.1)
val directLog = kernel.logDensity(LinearAngular(x, 3.1))
```

These agree within numerical rounding. A conditional at an exact continuous value is
defined by the density factorization; it is not rejection sampling for an event of zero
probability.

### 3. Build an explicit hierarchical model with angular evidence

```scala
import com.cra.figaro.language.*
import com.cra.figaro.algorithm.sampling.Importance

val u = Universe.createNew()
val x = Normal(0.0, 1.0)(using "linear", u) // variance, not standard deviation
val theta = NonCachingChain(x, (value: Double) => {
  val conditional = kernel.conditionalAngle(Vector(value))
  VonMises(conditional.location, conditional.kappa)(using "", u)
})(using "angle", u)
theta.observe(3.1)
val algorithm = Importance(40000, x)
try {
  algorithm.start()
  println(algorithm.expectation(x, (value: Double) => value))
} finally {
  if (algorithm.isActive) algorithm.kill()
  u.clear()
}
```

Here x has the kernel's scalar Gaussian marginal and theta has its exact conditional
law, so the unobserved hierarchy represents that GVM joint distribution. After observing
theta, the posterior of x need not be Gaussian. Figaro computes the posterior by
inference; the helper does **not** implement an analytic reverse conditional.

This construction gives the observed angular node a valid density. Merely applying
`Apply(jointState, _.angle)` and observing the projection is not a supported substitute.
For vector-valued Gaussian parents, construct an appropriate vector model and pass
the complete vector; this example does not introduce a new vector Gaussian adapter.

All three patterns are checked in the [executable example](../FigaroExamples/src/main/scala/com/cra/figaro/example/documentation/GaussVonMisesMomentsExample.scala):

```sh
sbt "examples / Compile / runMain com.cra.figaro.example.documentation.GaussVonMisesMomentsExample"
```

Its sampled summaries are compared with analytic values, and the hierarchical posterior
mean is checked against independent numerical integration.

## Mathematical basis and numerical limits

The implementation independently evaluates [Horwood and Poore, sections 4.2-4.3](https://doi.org/10.1137/130917296).
Let `z = solve(A, x-mu)`, `Q = inverse(I-i Gamma)`, and `F = E[exp(i theta)]`.
The paper gives `E[z exp(i theta)] = i Q beta F` and
`E[z z^T exp(i theta)] = (Q-Q beta beta^T Q) F`.
Our physical-coordinate conversion is

```text
w = mu + i A Q beta
E[x exp(i theta)]     = w F
E[x x^T exp(i theta)] = (A Q A^T + w w^T) F
```

The real and imaginary parts give the cosine and sine fields. These are transposes,
not complex conjugate transposes. The public API requires no complex-number types.
Internally a real symmetric eigensystem evaluates Q and the phase/magnitude of F,
avoiding a potentially wrong principal square root of a complex determinant.

- Dense evaluation uses roughly cubic matrix work and quadratic temporary storage.
  Results contain only immutable vectors/matrices; no model or RNG is retained.
- At zero concentration all first-harmonic mixed moments are exactly zero, regardless
  of beta/gamma. The uniform branch avoids evaluating irrelevant extreme coupling.
- A zero `meanResultantLength` can also mean numerical underflow, not a truly uniform
  angular law. Check `logMeanResultantLength`: a finite negative value distinguishes
  that case from the zero-concentration result. A tiny resultant makes the direction
  a fragile summary even if a direction is returned.
- Mixed moments are scaled before exponentiating: some can remain representable even
  when the unscaled angular moment underflows. This does not guarantee all extreme
  intermediate matrix products are representable.
- Overflow in intermediate products fails with `ArithmeticException`; eigensolver
  failures propagate. Finite parameters alone do not certify accurate results for
  ill-conditioned covariance, enormous coupling phases, or cancellation-sensitive
  moments. Very small outputs can underflow to zero. There is no regularization.
- Interruption is checked at entry and between matrix stages without clearing the flag.
  The third-party eigensolver itself cannot be interrupted mid-factorization.
- Invalid conditional-vector dimensions, null or nonfinite entries throw
  `IllegalArgumentException`; conditional center overflow fails explicitly. A returned
  circular kernel has its own bounded-rejection and RNG-ownership contracts.
- `moments` describes the fixed kernel, not an arbitrary posterior, mixture or empirical
  sample set. The marginal angle is generally not an ordinary von Mises law, and its
  resultant is not automatically a replacement concentration parameter.
- Mixed second-moment matrices can have negative diagonal entries: weighting by cosine
  or sine is signed. They are not covariance matrices and need not be positive definite.

## Verification and related work

Local acceptance: 15 focused moment/conditional tests, all 220 modernization regressions
across 17 suites, both GVM executable examples, Scala API generation and thin-library
packaging pass. This moments increment subsequently passed
[remote CI](https://github.com/mattwilkinsphoto/figaro/actions/runs/34173645295) at
`df5a7bf4` and was integrated on main. Later increments retain their own CI gates.

The [focused suite](../Figaro/src/test/scala/com/cra/figaro/test/modernization/GaussVonMisesMomentsTest.scala)
checks independent 80-digit complex-matrix fixtures, joint-density integration,
80,000 prior samples, uniform and uncoupled limits, physical-coordinate transformations,
an eight-dimensional phase-branch case, underflow/subnormal concentration, concurrent
calls, cancellation, overflow and exact conditional factorization/sampling.
The [optional oracle](../tools/gauss_von_mises_moments_reference.py) uses `mpmath==1.3.0`
outside the Figaro runtime and prints fixtures without writing files.

The next increment, [finite-concentration score calibration](GVM_SCORE_CALIBRATION.md),
is now on main at CI-verified `755eb425`. [State gradients](GVM_GRADIENTS.md) are locally
validated on the development branch, with their own CI gate. General quadrature remains
the next increment. Bhattacharyya divergence
and mutual information remain lower-priority research. This milestone does not add
report ingestion, fusion, filtering or propagation.

Related: [joint GVM guide](GAUSS_VON_MISES.md), [circular API](VON_MISES.md),
[KL and residuals](GVM_DIAGNOSTICS.md), [roadmap](../ROADMAP.md), [wishlist](../WISHLIST.md),
[compiler reference](api/README.md).
