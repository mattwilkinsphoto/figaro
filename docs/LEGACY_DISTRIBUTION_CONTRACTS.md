# Legacy distribution consolidation

## Overview

Modern.19 brings the established continuous and count Elements onto direct
log-likelihood paths. It fixes support/parameter errors, a non-unit inverse-gamma
sampling inconsistency, large-rate Poisson sampling, and count MH boundary handling.
Existing package names and factory overloads remain. This is not arbitrary graph
thread safety, a new learning algorithm, or a guarantee of posterior convergence.

## Quick start (three steps)

1. Rebuild/publish Figaro at `6.0.0-modern.19-SNAPSHOT` and rebuild your Scala 3 consumer.
2. Use the existing factories; Normal takes variance, Gamma takes scale,
   Exponential takes rate, and Geometric takes **failure** probability.
3. Score with `logDensity`/`logp`, or observe the Element and run inference:

```scala
import com.cra.figaro.language.*
import com.cra.figaro.library.atomic.continuous.*
import com.cra.figaro.library.atomic.LegacyInformation
val u = Universe.createNew()
val noise = InverseGamma(4, 7)(using "noise", u)
println(noise.logDensity(2.0))
println(LegacyInformation.inverseGammaKl(4, 7, 5, 8))
u.clear()
```

## Element API and conventions

All named factories still take contextual `name: Name[T]` and
`collection: ElementCollection`, returning an Element of the stated value type.
Existing dynamic-parameter overloads construct conditional atomic children; direct
`logp` on a compound continuous Element requires initialized current parent values.

| Factory / parameters | Value / support | Validation and interpretation |
| --- | --- | --- |
| `Normal(mean, variance)` | Double, real line | Finite mean, finite positive variance; variance is not SD |
| `Gamma(k, theta=1)` | Double, positive | Finite positive shape and scale |
| `InverseGamma(shape, scale)` | Double, positive | Finite positive parameters; density proportional to `x^(-shape-1)*exp(-scale/x)` |
| `Beta(a,b)` | Double, [0,1] | Finite positive shapes; endpoint limits can be infinite |
| `Dirichlet(alphas*)` or `Dirichlet(array)` | Array[Double], simplex | At least two positive finite concentrations, finite sum; scoring requires matching dimensions and sum within 1e-12 |
| `Exponential(lambda)` | Double, [0,infinity) | Finite positive **rate** |
| `Uniform(lower,upper)` | Double, [lower,upper) | Finite strictly ordered endpoints and finite interval width |
| `MultivariateNormal(means,covariances)` | List[Double], full-rank vector | Existing validated Gaussian kernel limits; atomic and compound `logp` now both implemented |
| `Poisson(lambda)` | Int, 0,1,... | Rate in [0,1e8]; samples outside Int representation fail |
| `Geometric(probFail)` | Int, 1,2,... | Failure probability in [0,1); zero gives a point mass at 1 |
| `Binomial(n,p)` | Int, 0,...,n | Nonnegative Int trials, success probability in [0,1] |

Public scoring/sampling methods on the affected atomic Elements:

| Method | Parameters | Returns / example |
| --- | --- | --- |
| `logDensity(value)` | Value in the corresponding row's type | Natural log density/mass; `Gamma(2,3).logDensity(4)` |
| `logp(value)` | Same, on continuous Elements | Same law as `logDensity`; `InverseGamma(4,7).logp(2)` |
| `density(value)` | Same typed value | Exponentiated log score, possibly zero through underflow or infinity at a singular endpoint |
| `generateRandomness()` | None; uses scoped Figaro RNG | Internal randomness, not necessarily the final value |
| `generateValue(randomness)` | That Element's Randomness type | Model-space value; `inverse.generateValue(2)` is `scale/2` |
| `nextRandomness(old)` | Supported internal randomness | Proposed randomness, reverse/forward proposal ratio, new/old model ratio; unrepresentable ratios throw |

Use ordinary `start`/`probability`/`kill` APIs for inference. The generated
[method reference](api/README.md) preserves factory overloads, parameter-learning
methods and contextual signatures. This milestone does not replace those APIs.

## Information API

Import `com.cra.figaro.library.atomic.LegacyInformation`. Every method below returns
`InformationMetricResult`: status, optional value in **nats**, numerical error
allowance, evaluations, and method label. Numerical allowances are heuristic
floating-point accounting, not certified bounds or sampling confidence intervals.
All methods have trailing `tolerance: Double = 1e-8`, finite and strictly positive.

| Functions | Ordered parameters before tolerance | Example |
| --- | --- | --- |
| `gammaKl`, `gammaBhattacharyya` | `shapeP, scaleP, shapeQ, scaleQ: Double` | `LegacyInformation.gammaKl(2,3,4,5)` |
| `inverseGammaKl`, `inverseGammaBhattacharyya` | Same names/types, inverse-gamma scale convention | `LegacyInformation.inverseGammaBhattacharyya(2,3,4,5)` |
| `betaKl`, `betaBhattacharyya` | `alphaP, betaP, alphaQ, betaQ: Double` | `LegacyInformation.betaKl(2,3,4,5)` |
| `dirichletKl`, `dirichletBhattacharyya` | `alphaP, alphaQ: Vector[Double]`, matching category order | `LegacyInformation.dirichletKl(Vector(2,3,4),Vector(3,4,5))` |
| `poissonKl`, `poissonBhattacharyya` | `rateP, rateQ: Double` | `LegacyInformation.poissonKl(2,5)` |
| `geometricKl`, `geometricBhattacharyya` | `failureP, failureQ: Double` | `LegacyInformation.geometricKl(.2,.6)` |
| `binomialKl`, `binomialBhattacharyya` | `trials: Int, successP, successQ: Double` | `LegacyInformation.binomialKl(10,.2,.4)` |

KL is directed P to Q; Bhattacharyya is symmetric negative log affinity, not a
triangle-inequality distance. Gamma/inverse-gamma/Beta/Dirichlet metric shapes are
restricted to [.001,1e6], scales to [1e-100,1e100], and Dirichlet dimensions to 2..128.
The Element constructors are not restricted to those metric ranges: numerical
sampling failures remain possible outside representable interiors. Count parameters
follow the table above; binomial comparisons require the same trial count.
Mathematically incompatible point-mass supports produce `Infinite`; insufficient
numerical tolerance produces `NumericallyUnresolved`, not a made-up finite result.

Exponential comparisons reuse Gamma with shape one and scale `1/rate`, when that
scale is representable and within metric bounds. Gaussian comparisons and Gaussian
partition MI use [GaussianInformation](DISTRIBUTION_CONSTRUCTIONS.md).
No MI is inferred from two separate scalar marginals. For an actual joint finite
table or a supported joint vector law, use the existing dedicated MI APIs.

## Three common patterns

### 1. Tiny observed likelihoods

```scala
val u = Universe.createNew()
val y = Gamma(2, 1)(using "y", u)
println(y.density(1000))    // 0: binary64 underflow
println(y.logDensity(1000)) // finite: inference uses this directly
y.observe(1000)
val alg = com.cra.figaro.algorithm.sampling.Importance(100, y)(using u)
try { alg.start(); println(alg.getTotalWeight) }
finally { if (alg.isActive) alg.kill(); u.clear() }
```

### 2. Compare fixed priors, with explicit numerical refusal

```scala
val comparison = LegacyInformation.dirichletKl(Vector(2,3,4), Vector(3,4,5))
comparison.value match {
  case Some(nats) => println((comparison.status, nats, comparison.errorEstimate))
  case None => println(s"No supported answer: ${comparison.status}")
}
```

### 3. Keep learning summaries separate from a sampling prior

```scala
val u = Universe.createNew()
val p = Beta(2,3)(using "p", u)
p.maximize(Vector(10.0,0.0))
println(p.expectedValue) // learned parameter summary, 0.8
println(p.logDensity(.4)) // still the original Beta(2,3) sampling prior
u.clear()
```

Construct a new Beta/Dirichlet with the learned concentrations if you explicitly
want that distribution. Mutable learning fields are not safe to share between
workers; use separate owned models, as before.

## Migration changes and gotchas

- **InverseGamma scale correction:** old samples were `1/(Gamma(shape,1)*scale)`
  while density used the conventional `scale/Gamma(shape,1)` law. Modern.19 aligns
  sampling with density. Non-unit-scale sample sequences and moments change. If
  reproducing the old *sampling law* is essential, the new scale would be the
  reciprocal of the old scale; that does not preserve the old inconsistent likelihood.
- Atomic Beta/Dirichlet `logp` now describes the same original sampling prior as
  their density and generation, not learned concentration summaries. The learning
  summary APIs remain separate. Dirichlet snapshots input concentrations for sampling
  and scoring; mutating its legacy public array does not redefine that snapshot.
- Invalid parameters now fail earlier. Finite off-support values have log score
  -Infinity. NaN/malformed dimensions throw. Dirichlet boundary combinations whose
  limiting score is unresolved throw rather than returning NaN.
- Infinite endpoint density can be mathematically meaningful, but observing it
  through likelihood weighting is rejected. It is not an infinite-confidence datum.
- Likelihood weighting no longer draws and discards prior randomness for an
  observed `HasLogDensity` element before scoring it. This prevents irrelevant
  prior-tail failures and changes subsequent seeded RNG consumption. Unobserved
  nodes and legacy `HasDensity`-only nodes retain their generation behavior.
- Gamma/Beta/Dirichlet draws that collapse to zero/boundaries or overflow fail,
  rather than silently resampling from a truncated law. Gamma rejection work is
  capped at 100000 open-unit requests, each bounded at 1024 RNG attempts. Count
  adapters cap RNG requests at 100000. Interruption is cooperative and preserved.
- Poisson/Binomial now use Commons Math samplers with the scoped Figaro RNG;
  exact historical seeded sequences change. Geometric's parameter still means
  failure probability, and counts still start at one.
- Count MH preserves adjacent-state proposals, now forming probabilities in log
  space and handling degenerate supports. Its returned ordinary ratios must remain
  representable. This is not an arbitrary-tail MH guarantee.
- Owned graph proposal traversal sorts the active-element snapshot by creation
  hashes for reproducible visit counts. It does not make arbitrary callbacks,
  shared graphs or default graph traversal deterministic/thread-safe.
- Legacy low-level public helpers that accept a caller-supplied raw normalizer
  are not transformed into a new stable API. Use the Element's direct log methods.

## Research, verification and related modules

Conventions follow [NIST Gamma](https://www.itl.nist.gov/div898/handbook/eda/section3/eda366b.htm)
and [SciPy inverse-gamma](https://docs.scipy.org/doc/scipy/reference/generated/scipy.stats.invgamma.html).
Information formulas follow normalized exponential-family integration, with reciprocal
invariance for inverse-gamma and the two-category Dirichlet reduction for Beta.
The positive digamma helper uses [NIST DLMF 5.5](https://dlmf.nist.gov/5.5) and
[5.11](https://dlmf.nist.gov/5.11); this avoids the observed several-e-9 Commons Math 3
digamma discrepancy at small arguments. Wishart information uses the same helper.
No external source code or new runtime dependency is adopted.

The focused Scala suite checks actual underflowing observations, non-unit scale
moments, large-rate counts, learning separation, posterior odds, MH and isolated
worker-count replay. Independent Python density integrals/count sums use mpmath;
they do not import Figaro. Run `LegacyDistributionContractsTest` and
`python -B -m unittest discover -s tools -p test_legacy_information_reference.py -v`.
The complete modernization, selected legacy-distribution, artifact, documentation
and published-consumer gates are required before integration.

### Statistical regression assessment

Modern.19 local acceptance (2026-09-09): a fresh action-cache clean build compiled
331 library and 236 test sources; all 521 selected modernization/inference/learning
tests passed. Ten legacy continuous-density and four Geometric checks passed
separately, as did five high-precision research tests and 18 documentation-tool tests.
All four JAR content/legal checks passed; an independent consumer loaded the exact
published binary (SHA-256 `6e2677b9618227d607a8fc4c303a7cd451b7f29522829a20d1ab12da6cd991b6`)
and passed its inference, metric, lifecycle and isolation checks. The generated
reference has 12344 public method entries; freshness and 13762 local-link checks
passed. [Remote CI passed](https://github.com/mattwilkinsphoto/figaro/actions/runs/34378798563)
at `acaecb98`, which is integrated on main as modern.19.

The initial broad run exposed genuine contract problems (an off-simplex Dirichlet
density fixture and discarded observed-prior draws) that were corrected, not excused
as Monte Carlo noise. A later Geometric MH run estimated 0.09468 for an exact 0.081
event probability, outside the old 0.01 tolerance. The adjacent-step proposal's
reverse/forward and model ratios now have deterministic formula checks over 30 states.
Eight predeclared seeds, each with 1000 warmup and 100000 retained steps, gave
0.08441, 0.07340, 0.08352, 0.08865, 0.08509, 0.07608, 0.08348 and 0.08509.
Their pooled estimate is 0.082465. The legacy regression now pools independent
seeded chains at a fixed budget and retains its original 0.01 tolerance; the new
focused pooled check uses 0.005. This addresses test repeatability, not Geometric
mixing performance. Neither result certifies all posterior workloads.

The exploratory full legacy run also had unseeded Uniform-mean and multivariate
Normal-variance misses. They are retained as statistical-test limitations, not
claimed fixed by the log-density work. The required gates use the explicit
modernization suite, targeted inference/learning checks and legacy density checks.

Related: [distribution inventory](DISTRIBUTION_SUPPORT.md), [information roadmap](INFORMATION_METRICS_ROADMAP.md),
[inference health](INFERENCE_HEALTH.md), [owned graph proposals](GRAPH_PROPOSALS.md),
[migration guide](MIGRATION.md), and [roadmap](../ROADMAP.md).
