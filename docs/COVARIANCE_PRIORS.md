# Covariance modeling: LKJ and inverse-Wishart

## Overview

These priors describe uncertainty about a multivariate model's dependence, not
uncertainty about a single state vector. `LKJDistribution` models a correlation
matrix. `InverseWishartDistribution` models an entire covariance matrix. Both have
immutable numerical kernels, fixed/hierarchical Figaro elements, direct log
likelihoods, caller-owned sampling and analytic KL/Bhattacharyya comparisons.

Choose **LKJ plus separate positive standard deviations** when you want to specify
dependence and marginal scales independently. Choose **inverse-Wishart** when its
joint covariance prior matches the model or when reproducing a model defined with
that prior. Neither is automatically a good prior simply because it is available.
This milestone adds model ingredients, not a covariance fitting engine, HMC,
conjugate-update API or generic thread safety.

## Quick start (three steps)

1. Import the kernels and create a law:

   ```scala
   import com.cra.figaro.library.atomic.continuous.*
   import com.cra.figaro.util.SamplingRandom
   val law = LKJDistribution(dimension = 2, shape = 2)
   ```

2. Draw a correlation and attach physical scales:

   ```scala
   val rng = SamplingRandom.scalaRandom(42)
   val correlation = law.sample(rng)
   val covariance = law.toCovariance(correlation, Vector(2.0, 3.0))
   // Marginal variances are 4 and 9, not 2 and 3.
   ```

3. Use it in a Gaussian kernel, or create `LKJ(law)` as a graph node:

   ```scala
   val gaussian = MultivariateGaussianDistribution(Vector(0.0, 0.0), covariance)
   val logLikelihood = gaussian.logDensity(Vector(1.0, -1.0))
   ```

## Parameter and measure contracts

| Kernel | Parameters | Meaning / limits |
| --- | --- | --- |
| `LKJDistribution(dimension, shape=1)` | Integer order 2..16; finite eta in [.001,1e6] | Density on independent off-diagonal entries is proportional to determinant^(eta-1). Matrix must be exactly symmetric with exact unit diagonal and numerically resolved positive definiteness. |
| `InverseWishartDistribution(degreesOfFreedom, scale)` | Real df in [d+1,1e6]; symmetric positive-definite 1..16 matrix Psi, diagonal [1e-100,1e100] | Density contains exp(-trace(Psi*inverse(X))/2). Mathematical domain df>d-1 is broader than this implementation. Psi is neither the mean nor the inverse scale. |

Numerical matrix validation uses the existing Gaussian kernel's finite-entry,
positive-definiteness and correlation-conditioning contract (condition <=1e10).
Malformed, singular or numerically unresolved inputs throw `IllegalArgumentException`.
Sampling failures throw `ArithmeticException`, without retrying from a different law.
Cancellation is cooperative and preserves the interrupt flag. A caller's own callback
or RNG implementation must return; no library can interrupt a callback that hangs.

Correlation and covariance densities are **not** Cholesky-factor densities.
`logDensityCholesky` includes the appropriate change-of-variables Jacobian. Factors
are always **lower triangular**, have positive diagonals, and represent `L*L.transpose`.
LKJ factor rows must have squared norms within 1e-12 of one. Sampled correlation
matrices have exact unit diagonals by construction; input matrices are never repaired.

## API reference

All matrix values are immutable `Vector[Vector[Double]]`; RNGs are `scala.util.Random`
and are never retained. Factory case classes also provide standard Scala `copy`,
equality, extraction and field access; their copies rerun validation.

| Public operation | Parameters | Returns | Example |
| --- | --- | --- | --- |
| `LKJDistribution(...)` | As above | Validated LKJ kernel | `LKJDistribution(3,2)` |
| `InverseWishartDistribution(...)` | As above | Validated covariance kernel | `InverseWishartDistribution(6,Vector(Vector(2.0,.4),Vector(.4,1.0)))` |
| LKJ `mean` | None | Identity expected correlation matrix | `law.mean` |
| Inverse-Wishart `mean` | None | `Some(Psi/(df-d-1))`, or `None` if the mean does not exist | `law.mean` |
| Inverse-Wishart `mode` | None | Psi/(df+d+1) | `law.mode` |
| Both `logDensity(value)` | Matching matrix in stated support/numeric range | Log density | `law.logDensity(matrix)` |
| Both `density(value)` | Same | Exponentiated log density; may under/overflow | `law.density(matrix)` |
| Both `logDensityCholesky(lower)` | Matching lower factor; LKJ requires unit row norms | Factor-coordinate log density including Jacobian | `law.logDensityCholesky(lower)` |
| Both `sampleCholesky(rng)` | Caller-owned RNG | Validated lower factor or explicit numeric failure | `law.sampleCholesky(rng)` |
| Both `sample(rng)` | Caller-owned RNG | Correlation/covariance draw respectively | `law.sample(rng)` |
| LKJ `toCovariance(correlation, standardDeviations)` | Validated correlation, positive finite SD vector in matching order | Numerically validated covariance | `law.toCovariance(law.mean,Vector(2.0,3.0))` |
| `LKJInformation.kl(p,q,tolerance=1e-8)` | Matching-order LKJ laws, positive finite tolerance in nats | `InformationMetricResult` | `LKJInformation.kl(LKJDistribution(3,1),LKJDistribution(3,2))` |
| `LKJInformation.bhattacharyya(p,q,tolerance=1e-8)` | Same | Symmetric negative log affinity result | `LKJInformation.bhattacharyya(p,q)` |
| `InverseWishartInformation.kl(p,q,tolerance=1e-8)` | Matching-order covariance laws, positive finite tolerance | Directed KL result | `InverseWishartInformation.kl(p,q)` |
| `InverseWishartInformation.bhattacharyya(p,q,tolerance=1e-8)` | Same | Symmetric negative log affinity result | `InverseWishartInformation.bhattacharyya(p,q)` |
| `LKJ(distribution)` / `InverseWishart(distribution)` | Fixed kernel; contextual `Name` and `ElementCollection` | Atomic observation-ready matrix element | `LKJ(LKJDistribution(2,2))` |
| Same factories, dynamic overload | `Element` of matching kernels, same contextual parameters | Non-caching hierarchical matrix element | `LKJ(eta.map(e => LKJDistribution(2,e)))` |
| Atomic adapter `generateRandomness()` | None; uses scoped Figaro RNG | Matrix prior draw | `element.generateRandomness()` |
| Atomic adapter `generateValue(value)` | Matrix randomness | Same matrix; runtime lifecycle hook | `element.generateValue(matrix)` |
| Atomic adapter `logDensity(value)` | Validated matrix | Kernel log density | `element.logDensity(matrix)` |

The adapters inherit `observe`, `unobserve`, `addCondition`, `addConstraint`, `density`
and lifecycle operations. See the [generated reference](api/README.md) for inherited
signatures. A direct runtime hook does not register evidence or run inference.

Information results follow the shared [metric status contract](COMMON_INFORMATION_METRICS.md).
Identical laws return exact zero. Numerical roundoff/cancellation allowances are
heuristics, not certified bounds or sampling confidence intervals; unresolved results
have no usable value. Increasing dimension or concentration can make a tight tolerance
unavailable. These are same-family comparisons, not LKJ-versus-inverse-Wishart KL.
MI between matrix entries is not implemented: it requires a specified partition and
the correct induced marginals, not merely substituting correlation into a Gaussian MI formula.

## Three common patterns

### 1. Separate correlation and scale in a hierarchical Gaussian

```scala
import com.cra.figaro.language.*
import com.cra.figaro.library.atomic.continuous.*
import com.cra.figaro.algorithm.sampling.Importance

val u = Universe.createNew()
val correlation = LKJ(LKJDistribution(2,2))(using "correlation",u)
// Unit scales here; use toCovariance to attach non-unit standard deviations.
val covariance = correlation.map(_.map(_.toList).toList)(using "covariance",u)
val outcome = MultivariateNormal(
  Constant(List(0.0,0.0))(using "",u), covariance)(using "outcome",u)
outcome.observe(List(0.0,0.0))
val inference = Importance(20000,correlation)(using u)
try {
  inference.start()
  val posteriorRhoSquared = inference.expectation(correlation,
    (r: Vector[Vector[Double]]) => r(0)(1)*r(0)(1))
  // Analytic value for this deliberately simple example: 0.25.
} finally {
  if (inference.isActive) inference.kill()
  u.clear()
}
```

Do not `observe` a deterministic `Apply` covariance transformation and assume it
supplies a transformed density. Observe the Gaussian child, or the native matrix
element when matrix evidence itself is the intended generative model.

### 2. Draw covariance priors with an interpretable mean

```scala
val desiredMean = Vector(Vector(2.0,.4),Vector(.4,1.0))
val dimension = 2
val df = 8.0
val psi = desiredMean.map(_.map(_ * (df-dimension-1)))
val prior = InverseWishartDistribution(df,psi)
val lower = prior.sampleCholesky(rng)
val matrix = prior.sample(rng) // A NEW draw, not lower*lower.transpose.
assert(prior.mean.contains(desiredMean))
```

For dimension one this law reduces to inverse-Gamma with shape=df/2 and scale=Psi/2.
The variance of the covariance entries may not exist even when the mean does; no
finite variance is fabricated by this API.

### 3. Compare candidate priors without sampling

```scala
val diffuse = LKJDistribution(3,1)
val concentrated = LKJDistribution(3,4)
val directed = LKJInformation.kl(diffuse,concentrated)
val overlap = LKJInformation.bhattacharyya(diffuse,concentrated)
println((directed.status,directed.value,overlap.status,overlap.value))
```

Direction matters for KL. A small divergence describes these specified prior laws,
not evidence that their posterior inferences are interchangeable. The covariance
counterparts use `InverseWishartInformation`; coordinates and units must match.

## Gotchas and algorithm compatibility

- LKJ eta=1 is uniform over whole correlation matrices, not over each correlation
  marginal when dimension>2. Marginal variance is `1/(2*eta+dimension-1)`.
- Low eta concentrates mass near singular matrices. Even valid parameter values can
  produce numerically refused draws; a long graph run can therefore fail. Do not catch
  and redraw silently, which conditions the prior on passing the numerical screen.
- Exact evidence at an invalid matrix raises an error. This is a restricted numeric
  kernel, not a density over singular matrices or all arbitrary matrix inputs.
- Full observation likelihoods include normalization constants. A proportional-only
  expression is wrong when comparing random concentration/df/scale parameters.
- Prior sampling, importance with fixed/dynamic matrix evidence, a hierarchical
  Gaussian child, constrained MH and owned multi-chain replay are tested. Atomic
  MH uses prior independence proposals; it may mix poorly under concentrated evidence.
- Use isolated graph factories for parallel chains. These classes do not make a shared
  mutable `Universe` safe. No exact matrix factor conversion, specialized learning,
  covariance posterior fitting, matrix gradients or HMC is added.
- RNG work is capped at 100000 open-unit requests per draw, with bounded invalid-RNG
  handling. Results remain dependent on the configured RNG/seed and call order.

## Research basis and verification

The literature-first assessment selected independent partial-correlation Beta draws
for LKJ, checked against [Lewandowski, Kurowicka and Joe (2009)](https://doi.org/10.1016/j.jmva.2009.04.008)
and [Stan's correlation reference](https://mc-stan.org/docs/functions-reference/correlation_matrix_distributions.html).
The matrix and factor densities deliberately use different Jacobians. Correlation
plus separate scales is also discussed in the [Stan hierarchical-prior guide](https://mc-stan.org/docs/2_31/stan-users-guide/multivariate-hierarchical-priors.html).

Inverse-Wishart parameterization was checked against the [SciPy reference](https://docs.scipy.org/doc/scipy/reference/generated/scipy.stats.invwishart.html).
[Axen (2023)](https://arxiv.org/abs/2310.15884), Theorem 3.2 and Algorithm 4,
supplies the direct factor construction. The implementation solves a triangular
system, rather than explicitly forming its inverse; the paper's upper factor is
transposed for Figaro's lower-factor convention. This avoids constructing and
inverting a dense Wishart draw. It is an algorithmic choice, not a measured Figaro
speedup claim. KL/Bhattacharyya follow exponential-family normalizers and are checked
against the scalar inverse-Gamma reduction and inversion-equivalent Wishart metrics.
No external implementation is copied and no runtime dependency is added.

Run `CovariancePriorsTest` and `python -B tools/test_covariance_reference.py`.
Tests include independent high-precision integrals, the 3D correlation-volume
identity, matrix-density fixtures, sample means/precision moments, Jacobians,
actual evidence, correlated Gaussian observations, worker replay and refusal paths.
The low-eta sampling grid records refusals and bounds their possible moment
contributions; it never reports only successful draws as unconditional samples.

Local acceptance: all 516 modernization tests passed in a fresh action-cache clean
build, including nine covariance-prior regressions. Three independent high-precision
reference tests passed. The LKJ grid attempted 6000 draws at each combination of
dimensions 2/3/5 and eta .5/1/3: one numerical refusal at dimension 5, eta .5, and
none in the other eight cells. These observations are not bounds on future refusal
rates. All four packaged JARs passed content/legal checks; the binary SHA-256 is
`19ad8c519136680521a3f6b0c0455be1e34558c716b3c2e2fbaf45bcc174a635`.
The independent consumer passed against that exact binary. Generated documentation
freshness (12462 public method entries), 13894 local-link checks and 18 documentation
tool tests passed. Remote CI remains required before main integration.

Related: [legacy contracts](LEGACY_DISTRIBUTION_CONTRACTS.md), [Wishart and other breadth](DISTRIBUTION_BREADTH.md),
[Gaussian kernels/metrics](DISTRIBUTION_CONSTRUCTIONS.md), [multi-chain MCMC](MULTI_CHAIN_MCMC.md),
[information roadmap](INFORMATION_METRICS_ROADMAP.md) and [delivery roadmap](../ROADMAP.md).
