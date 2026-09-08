# GVM state gradients

## Overview

These helpers tell you how a fixed GVM's log density or squared Mahalanobis-von-Mises
score changes when you move a point slightly. They return analytic derivatives with
respect to the physical linear coordinates and the angle. Distribution parameters
are held fixed. No samples, model mutation or repeated finite-difference evaluations
are needed.

Use them for local sensitivity analysis, directional derivatives, or as building
blocks for a separately designed optimization or gradient-aware sampling method.
They do **not** add parameter fitting, automatic differentiation of a Figaro graph,
Hamiltonian Monte Carlo, or a faster inference algorithm by themselves.

Status: source-development preview integrated on main at `141dcc15`, with
[passing CI](https://github.com/mattwilkinsphoto/figaro/actions/runs/34177381982).
Neither this addition nor earlier GVM additions change the immutable RC1 bundle.

## Quick start in three steps

1. Construct a fixed kernel and a point:

   ```scala
   import com.cra.figaro.library.atomic.continuous.*
   val kernel = GaussVonMisesDistribution(Vector(1.0, -2.0),
     Vector(Vector(4.0, 1.2), Vector(1.2, 2.61)), 3.05,
     Vector(0.7, -0.4), Vector(Vector(0.3, 0.2), Vector(0.2, -0.5)), 4.5)
   val point = LinearAngular(Vector(2.0, -1.0), -3.1)
   ```

2. Evaluate once: `val gradient = kernel.logDensityGradient(point)`.
3. Read `gradient.linear` and `gradient.angular`. For this example they are approximately
   `Vector(-0.312386, -0.215376)` and `0.269255` per radian.

A positive partial derivative means a small increase in that coordinate, with the
others fixed, increases the log density locally. Compare derivatives only after
accounting for coordinate units; one meter and one kilometer are different steps.

## Public API reference

| API | Parameters | Returns | Example |
| --- | --- | --- | --- |
| `kernel.logDensityGradient(value)` | Non-null `LinearAngular`; finite linear vector matching `kernel.dimension`, angle in radians; fixed kernel parameters | Immutable `GaussVonMisesStateGradient` of log density in physical coordinates | `kernel.logDensityGradient(point).linear(0)` |
| `kernel.mahalanobisSquaredGradient(value)` | Same point contract | Gradient of the **squared** score, equal to -2 times the log-density gradient | `kernel.mahalanobisSquaredGradient(point).angular` |
| `GaussVonMisesGradientExample.main(args)` | Empty `Array[String]`, from `com.cra.figaro.example.documentation` | Unit; checks and prints three sensitivity examples; no universe or RNG | `GaussVonMisesGradientExample.main(Array.empty[String])` |

`GaussVonMisesStateGradient` has a library-internal constructor and two immutable
fields, obtained from either kernel method:

| Field | Meaning | Example |
| --- | --- | --- |
| `linear: Vector[Double]` | Partial derivatives in the original coordinate order, per physical coordinate unit | `gradient.linear(1)` |
| `angular: Double` | Partial derivative per radian; not an angle | `gradient.angular` |

This result is deliberately **not** a `LinearAngular` state. Do not wrap the angular
derivative into [-Pi,Pi), convert it as though it were an angle, or feed it directly
into a state-density API. If your external angular coordinate is degrees, apply the
chain rule: derivative per degree = derivative per radian * Pi/180.

## Three common patterns

### 1. Replace finite differences for local sensitivity

Previously, estimating each partial derivative required changing a coordinate in
both directions and evaluating the log density twice. For n linear coordinates plus
one angle, central differences require 2(n+1) density evaluations and a step-size choice.

```scala
val h = 1e-4 // chosen in the first coordinate's physical units
val plus = LinearAngular(point.linear.updated(0, point.linear(0) + h), point.angle)
val minus = LinearAngular(point.linear.updated(0, point.linear(0) - h), point.angle)
val finiteDifference = (kernel.logDensity(plus) - kernel.logDensity(minus)) / (2*h)
val analytic = kernel.logDensityGradient(point).linear(0)
println((finiteDifference, analytic))
```

The analytic call evaluates all partials together without a differencing step size.
It still has floating-point error, but avoids subtracting almost equal log densities.
The linear result includes **both** Gaussian displacement and the change in conditional
angular center due to linear/quadratic coupling. Using only the Gaussian gradient
would omit the latter effect.

### 2. Estimate the effect of a specified small move

```scala
val gradient = kernel.logDensityGradient(point)
val linearDirection = Vector(0.2, -0.1)
val angularDirection = 0.3 // radians per unit step
val slope = gradient.linear.zip(linearDirection).map(_ * _).sum +
  gradient.angular * angularDirection
val step = 1e-4
val predictedLogChange = step * slope
val moved = LinearAngular(
  point.linear.zip(linearDirection).map((x, d) => x + step*d),
  point.angle + step*angularDirection)
val actualLogChange = kernel.logDensity(moved) - kernel.logDensity(point)
println((predictedLogChange, actualLogChange))
```

This dot product is the first-order directional derivative. Its accuracy as a finite
move prediction depends on step size and curvature; it is not an exact finite change
or a line-search algorithm. The direction's linear vector must match the kernel's
dimension; ordinary collection `zip` would otherwise silently truncate it.

Derivatives transform inversely to coordinate scale: changing a coordinate from meters
to kilometers makes its derivative per kilometer 1000 times as large. A raw gradient
vector therefore is not a unit-independent movement rule. Optimization and sampling
need an appropriate metric/preconditioner, step-size control and their own validation.

### 3. Inspect sensitivity of the squared score

```scala
val score = kernel.mahalanobisSquared(point)
val scoreGradient = kernel.mahalanobisSquaredGradient(point)
println((score, scoreGradient.linear, scoreGradient.angular))
val threshold = kernel.scoreDistribution().quantile(0.95)
println(score <= threshold)
```

The derivative says how the point's squared score changes locally; the separate
[calibrator](GVM_SCORE_CALIBRATION.md) interprets the score's modeled probability.
These are distinct operations. The derivative is not a tail-probability gradient,
the gradient of the score's square root, or a statistical confidence statement.
For fixed parameters, the squared-score gradient is exactly -2 times the log-density
gradient mathematically. No extra density evaluations are needed for this identity.

All three patterns are checked in the [executable example](../FigaroExamples/src/main/scala/com/cra/figaro/example/documentation/GaussVonMisesGradientExample.scala):

```sh
sbt "examples / Compile / runMain com.cra.figaro.example.documentation.GaussVonMisesGradientExample"
```

## Derivation and numerical behavior

Differentiate the [joint density definition](GAUSS_VON_MISES.md) with all parameters
fixed. Let A be the lower Cholesky factor and Gamma symmetric, as required by the
kernel. Then

```text
z = solve(A, x-mu)
m = alpha + beta^T z + 0.5 z^T Gamma z
delta = theta-m (interpreted periodically)
w = kappa sin(delta)
grad_z log p = -z + w (beta + Gamma z)
grad_x log p = solve(A^T, grad_z log p)
d log p / d theta = -w
grad M = -2 grad log p
```

The implementation uses triangular solves rather than an explicit covariance inverse,
and roughly quadratic work with linear per-call scratch storage for an already
constructed dense kernel (whose retained matrices use quadratic storage). It does
not form a numerical Hessian, use automatic differentiation, or exponentiate the
density. A representable derivative can be returned even when `logDensity` is
`-Infinity` from a squared-norm overflow in an extreme Gaussian tail. This is a
derivative of the mathematical log density, not a derivative of floating-point overflow.

At kappa=0, angular derivatives vanish and linear derivatives reduce to the Gaussian
ones; irrelevant coupling is skipped. Wrapped angle coordinates do not make the
periodic density nondifferentiable at +/-Pi. However, very large phases and inaccurate
range reduction retain the [kernel's numerical limitations](GAUSS_VON_MISES.md).

## Gotchas and limits

- These are **state** derivatives, not derivatives with respect to mean, covariance,
  alpha, beta, Gamma or kappa. They do not include any derivative of a fitted parameter
  as the point changes. Hessians and parameter gradients remain separate work.
- Invalid/null or dimension-mismatched inputs throw `IllegalArgumentException`.
  Nonfinite intermediate results throw `ArithmeticException`, including a squared-score
  derivative whose -2 scaling overflows. Finite inputs do not guarantee finite output.
  Intermediate overflow can fail even if symbolic cancellation might yield a finite answer.
- Ill-conditioned covariance and large coupling can amplify numerical errors. There
  is no regularization, error-bound certificate or promise of accuracy for all scales.
  Very small derivatives can underflow to zero.
- A zero gradient is not proof of a maximum or successful convergence: stationary
  points can also be minima or saddles. In particular, sine vanishes at the circular
  antipode as well as at its conditional mode. Tiny/underflowed derivatives add another
  reason not to treat zero as a universal stopping criterion.
- Returned vectors are immutable and calls keep separate scratch arrays. You can share
  the numeric kernel across threads; this does not make mutable Figaro universes or
  elements thread-safe. Neither method consumes an RNG.
- Interruption is checked during matrix/vector stages and throws `CancellationException`
  without clearing the interrupt flag.
- Existing Figaro inference algorithms are unchanged. These helpers do not automatically
  differentiate a whole probabilistic program or enable HMC, MALA or optimization.
  No end-to-end speedup is claimed without an algorithm and workload benchmark.

## Verification and related work

Local acceptance: 11 focused tests, all 245 modernization regressions across 19 suites,
four GVM executable examples, Scala API generation and thin-library packaging.
The [focused suite](../Figaro/src/test/scala/com/cra/figaro/test/modernization/GaussVonMisesGradientTest.scala)
checks independent 80-digit derivatives, physical-coordinate finite differences,
Gaussian/circular reductions, unit rescaling, angular periodicity/branch cuts, an
80,000-draw zero-mean-gradient check, extremes, cancellation and concurrent reuse.
The [optional oracle](../tools/gauss_von_mises_gradient_reference.py) uses `mpmath==1.3.0`
outside the runtime; run it with `python -B`. No runtime dependency was added.

Remote CI explicitly runs the suite and example. The exact milestone `141dcc15` passed
[branch CI](https://github.com/mattwilkinsphoto/figaro/actions/runs/34177381982) and was
integrated on main. Later increments retain their own gates; historical whole-suite
limits remain unchanged.

Related: [joint GVM](GAUSS_VON_MISES.md), [score and KL diagnostics](GVM_DIAGNOSTICS.md),
[score calibration](GVM_SCORE_CALIBRATION.md), [moments/conditionals](GVM_MOMENTS.md),
[roadmap](../ROADMAP.md), [API reference](api/README.md). [Third-order expectation
quadrature](GVM_QUADRATURE.md) is now locally validated on the development branch,
with signed-weight and exactness limitations. Bhattacharyya divergence and mutual information
remain lower-priority research; no report ingestion, fusion, filtering or propagation
is implemented by these derivatives.
