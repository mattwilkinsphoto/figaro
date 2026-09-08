# GVM residuals, Mahalanobis scoring and KL divergence

## Overview

These deterministic utilities help you inspect a point under a Gauss-von Mises (GVM)
distribution and compare two complete GVM distributions. They operate on immutable
`GaussVonMisesDistribution` kernels, without creating Figaro elements, starting an
inference algorithm, or consuming random numbers. They do not fit a distribution,
ingest reports, or implement a fusion, association or propagation workflow.

Use a **Mahalanobis-von-Mises score** to ask how unusual a point is under one kernel.
Use **KL divergence** to ask how much information is lost when a second kernel
represents the first. KL can detect covariance, concentration and coupling differences
even when the two modes coincide. Neither number is a collision probability, posterior
model probability, p-value, or automatically calibrated decision threshold.

## Quick start in three steps

1. Import the types and construct two kernels on the same coordinates:

   ```scala
   import com.cra.figaro.library.atomic.continuous.*
   val p = GaussVonMisesDistribution(Vector(0.0), Vector(Vector(1.0)),
     3.0, Vector(0.7), Vector(Vector(0.4)), 4.5)
   val q = GaussVonMisesDistribution(Vector(0.0), Vector(Vector(1.0)),
     3.0, Vector(0.0), Vector(Vector(0.0)), 4.5)
   ```

2. Score a point: `p.mahalanobisSquared(LinearAngular(Vector(1.0), 3.1))`.
3. Compare distributions: `p.klDivergenceComponents(q)`. Read `.gaussian`,
   `.conditionalAngular` and `.total`; all are in **nats** (natural logarithms).

The added diagnostics are part of the source development preview, not a replacement
for the previously published RC1 library. Compile this revision to use them. See
[building](BUILDING.md) and the [joint distribution guide](GAUSS_VON_MISES.md).

## Public API reference

All arguments are ordinary immutable kernel/value objects, not Figaro elements.

| Function | Parameters | Returns | Example |
| --- | --- | --- | --- |
| `p.canonicalResidual(value)` | Non-null `LinearAngular` with matching finite linear coordinates and finite radians | `LinearAngular(z, delta)`: whitened vector and residual angle in `[-Pi,Pi)` | `p.canonicalResidual(LinearAngular(Vector(1.0), 3.1))` |
| `p.fromCanonical(residual)` | Matching finite standardized vector and finite residual radians | Physical `LinearAngular`, angle in `[-Pi,Pi)`; inverse up to rounding/wrapping | `p.fromCanonical(LinearAngular(Vector(0.0), 0.0))` |
| `p.mahalanobisSquared(value)` | Non-null matching finite state | Nonnegative squared score; may be `+Infinity` for extreme tails | `p.mahalanobisSquared(p.fromCanonical(LinearAngular(Vector(0.0), 0.0)))` gives zero |
| `p.klDivergence(q)` | Non-null GVM of the same dimension and coordinate meanings | Directed `D_KL(p || q)` as a finite nonnegative `Double` in nats | `p.klDivergence(p)` gives zero within rounding |
| `p.klDivergenceComponents(q)` | Same comparison contract | `GaussVonMisesKL` with Gaussian, expected conditional-angular and total contributions | `p.klDivergenceComponents(q).conditionalAngular` |
| `GaussVonMisesKL(gaussian, conditionalAngular)` | Finite nonnegative nats; sum must be finite | Validated immutable result; normally obtain it from the kernel | `GaussVonMisesKL(0.5, 0.25).total` gives `0.75` |

The result's `gaussian`, `conditionalAngular` and `total` fields are immutable.
Case-class copying/extraction/equality and inherited functions are included in the
[generated reference](api/README.md). Invalid inputs throw `IllegalArgumentException`.
Runtime arithmetic outside the supported finite range fails explicitly; see limits below.

## Three common patterns

### 1. Inspect an unusual point and reconstruct its residual

With `p` from the quick start:

```scala
val point = LinearAngular(Vector(1.0), 3.1)
val residual = p.canonicalResidual(point)
println(residual.linear) // Vector(1.0)
println(residual.angle)  // approximately -0.8 radians
println(p.mahalanobisSquared(point))
val restored = p.fromCanonical(residual)
```

The Gaussian residual is standardized; the angle is still in radians, not divided by
an angular standard deviation. The center moves with the linear state, so subtracting
`alpha` alone would give the wrong angular residual in a coupled model. Under prior
draws the canonical vector is standard Gaussian and the residual is an independent
von Mises angle of concentration `kappa`.

### 2. Measure the effect of dropping dependence

The quick-start `q` removes both coupling terms while keeping the same Gaussian
marginal, concentration and angular center at the mean:

```scala
val loss = p.klDivergenceComponents(q)
assert(loss.gaussian == 0.0)
assert(loss.conditionalAngular > 0.0)
println(loss.total)          // loss when q represents p
println(q.klDivergence(p))   // reverse comparison; equal for this particular pair
```

The two kernels have the same mode, so mean separation alone cannot detect this
change. KL detects the changed dependence. This is a comparison of specified kernels,
not a claim that `q` is the optimal approximation or the product of p's marginals.
In particular, p's marginal angle is generally not an ordinary von Mises distribution.
The reverse KL happens to agree here because the Gaussian marginals and concentrations
match; this special symmetry does not hold for general GVM pairs.

### 3. Check the exact Gaussian/Mahalanobis reduction

```scala
def shifted(mu: Double) = GaussVonMisesDistribution(Vector(mu), Vector(Vector(4.0)),
  3.0, Vector(0.0), Vector(Vector(0.0)), 7.0)
val left = shifted(1.0)
val right = shifted(5.0)
assert(left.klDivergence(right) == 2.0)
assert(right.mahalanobisSquared(LinearAngular(left.mean, 3.0)) == 4.0)
```

With equal Gaussian covariances and identical conditional angular laws,
`2 * KL = squared Mahalanobis separation`. Unequal covariances retain additional
Gaussian divergence terms; KL does not generally reduce to a mean-only score.
Equality of raw coupling parameters alone does not ensure identical conditional
angular laws when Gaussian means/factors differ, because coupling uses whitened coordinates.

These workflows are exercised in the [runnable example](../FigaroExamples/src/main/scala/com/cra/figaro/example/documentation/GaussVonMisesExample.scala):
`sbt "examples / Compile / runMain com.cra.figaro.example.documentation.GaussVonMisesExample"`.

## Mathematics and numerical implementation

The [joint guide](GAUSS_VON_MISES.md) defines `z = solve(A, x-mu)` and
`m(x) = alpha + beta^T z + 0.5 z^T Gamma z`. The score is

```text
M = ||z||^2 + 4 kappa sin(delta/2)^2
  = -2 [log p(x,theta) - log p(mu,alpha)]
delta = wrap(theta - m(x))
```

The score itself is exact, not a Gaussian-angle approximation. Its null distribution
is not generally chi-square with n+1 degrees of freedom: at kappa=0 it is chi-square
with n degrees of freedom. Finite-concentration threshold calibration is deferred.

For two GVMs P and Q, the KL chain rule gives the following derived expression:

```text
R = I1(kappaP) / I0(kappaP)
C = E_{x ~ NormalP}[cos(mP(x) - mQ(x))]
KL(P || Q) = KL(NormalP || NormalQ)
           + log I0(kappaQ) - log I0(kappaP) + R (kappaP - kappaQ C)
```

Set `d = solve(Aq, muP-muQ)` and `B = solve(Aq, Ap)`. In P's whitened coordinates,
the center difference is `c + b^T z + 0.5 z^T G z`, where

```text
c = alphaP-alphaQ-betaQ^T d-0.5 d^T GammaQ d
b = betaP-B^T betaQ-B^T GammaQ d
G = GammaP-B^T GammaQ B
```

If G has real eigenvalues lambda_j and b has components v_j in that orthonormal basis,

```text
L   = -1/4 sum log(1+lambda_j^2) - 1/2 sum v_j^2/(1+lambda_j^2)
phi = c + 1/2 sum atan(lambda_j) - 1/2 sum v_j^2 lambda_j/(1+lambda_j^2)
C   = exp(L) cos(phi)
```

This follows from the Gaussian quadratic characteristic-function identity underlying
[Horwood and Poore, section 4.2](https://doi.org/10.1137/130917296). The KL derivation
is an independently derived extension, not represented as a formula printed in that paper.
No Monte Carlo or angular-Gaussian approximation is used. An eigensystem avoids a
wrong complex square-root branch when several quadratic eigenvalues contribute phase.

The implementation uses triangular solves, nonnegative Gaussian terms, scaled Bessel
normalizers, `log1p`, `expm1`, and half-angle sines. For two concentrations above 50,
a 20-term log-Bessel asymptotic evaluation avoids cancellation of nearly equal
concentrations. This is numerical evaluation of the analytic KL, not an exact-arithmetic
guarantee. It uses the existing Apache Commons Math eigensolver; no new runtime dependency.
Dense comparisons generally require cubic matrix work and quadratic scratch storage.
Per-call scratch arrays are private, so immutable kernels can be shared between workers.

## Gotchas and limits

- KL is directed and is not a metric. Even an averaged two-direction KL need not obey
  the triangle inequality. Divide nats by `log(2)` if bits are required.
- Dimensions are checked, coordinate meanings/units are not. Both kernels must describe
  the same random quantities in the same coordinate system; transform both consistently.
- This API compares individual fixed GVM kernels. A generic posterior, arbitrary mixture,
  or empirical sample set is not automatically a GVM and cannot be passed as one.
- Both covariances must be numerically positive definite. There is no singular support,
  implicit regularization, condition-number guarantee or automatic parameter fitting.
- Finite inputs do not guarantee representable intermediate products. KL overflow raises
  `ArithmeticException`; eigensolver convergence failures propagate rather than returning
  an approximate answer. Interruptions are checked around matrix stages, not within the
  third-party eigensolver; cancellation may wait for a factorization to finish.
- KL below floating-point resolution is not a reliable relative-error measurement.
  The ordinary concentration calculation clips a negative contribution only within
  `64 * ulp(max(1, sum of absolute terms))`; larger negatives fail. Equal concentrations
  bypass that subtraction, preserving very small angular/coupling differences.
  Positive roundoff is not similarly clipped, so equivalent kernels can yield tiny
  positive values. Use an appropriate absolute tolerance, not bitwise equality, generally.
- Large phases or nearly singular covariance can amplify error. Rescale/recenter models;
  the provided extreme-case tests do not certify all ill-conditioned inputs.
- At kappa=0, scoring and KL ignore irrelevant angular coupling. Canonical conversion
  deliberately still uses the specified center, so extreme irrelevant parameters can
  overflow that conversion even when density/scoring remain usable.
- No operational thresholds, finite-kappa confidence regions, or speedup claims are
  supplied by this milestone. Those require separate calibration and benchmarks.

## Verification and follow-on work

The [diagnostics suite](../Figaro/src/test/scala/com/cra/figaro/test/modernization/GaussVonMisesDiagnosticsTest.scala)
covers residual round trips, log-density/score identity, Gaussian and circular reductions,
direct joint-density quadrature, an eight-dimensional complex-branch trap, 80-digit
non-diagonal fixtures, concentration extremes, small perturbations, concurrent reuse,
invalid inputs, overflow and cancellation. CI explicitly includes this suite.
Local acceptance includes 205 modernization regressions (13 diagnostic tests), the
expanded executable example, Scala API generation and thin-library packaging.
The exact milestone `3615e26e` subsequently passed [branch CI](https://github.com/mattwilkinsphoto/figaro/actions/runs/34172281823)
and was integrated on main. This does not certify the entire historical suite or later extensions.
The optional [reference generator](../tools/gauss_von_mises_kl_reference.py) requires
`mpmath==1.3.0`, prints results without writing files, and is not a runtime dependency.

Lower-priority research items, not implemented APIs:

- **Bhattacharyya divergence:** symmetric overlap comparison between two GVMs. Integrate
  the conditional angular overlap analytically, then assess the remaining Gaussian-weighted
  integral. Require identical-law, symmetry and Gaussian special-case checks. Do not
  promise a general finite closed form or label a quadrature estimate exact.
- **Linear-angular mutual information:** `I(X;theta) = h(theta)-h(theta|X)` within one GVM,
  not a distance between two GVMs. Conditional entropy is analytic; investigate Fourier
  evaluation of the angular marginal and validated entropy integration. Require zero
  for uncoupled/uniform cases, nonnegativity and independent numerical checks.
- [Analytic circular/mixed moments and the exact angular conditional](GVM_MOMENTS.md)
  are now implemented in the development branch. Finite-concentration score thresholds,
  additional conditionals and gradients remain separate increments. Generic quadrature needs its
  own approximation/negative-weight assessment. No fusion or propagation scope is added.

Related: [joint GVM](GAUSS_VON_MISES.md), [circular foundation](VON_MISES.md),
[roadmap](../ROADMAP.md), [wishlist](../WISHLIST.md), [milestone plan](GAUSS_VON_MISES_PLAN.md).
