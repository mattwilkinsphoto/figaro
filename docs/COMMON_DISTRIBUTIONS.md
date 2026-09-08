# Common scalar and count distributions

## Overview

This milestone adds nine missing first-class families: **Student t, Cauchy, Laplace,
lognormal, Weibull, triangular, Kumaraswamy, negative binomial and hypergeometric**.
They cover robust real-valued models, positive/skewed quantities, bounded proportions,
overdispersed counts and sampling without replacement. Specialized multivariate,
mixture, truncated and zero-inflated variants remain later work.

Each family has an immutable numeric kernel (`StudentTDistribution`, for example)
and a named Figaro factory (`StudentT`). Kernels work without a Universe and retain
no RNG. Elements add observation-ready log likelihoods and ordinary model composition.
This is more than an `Apply` transformation that generates samples but lacks the
Jacobian-adjusted likelihood needed for exact continuous observations.

Use the [information-metric guide](COMMON_INFORMATION_METRICS.md) for KL, Bhattacharyya
and finite-joint-table MI. This milestone adds no inference defaults, fitting routine,
domain-specific report ingestion, fusion, filtering or propagation.

## Quick start: three steps

1. Import and construct a kernel:

   ```scala
   import com.cra.figaro.library.atomic.continuous.*
   val law = StudentTDistribution(degreesOfFreedom = 5, location = 0, scale = 1)
   ```

2. Inspect or sample it:

   ```scala
   println(law.logDensity(4.0))
   println(law.survival(4.0))
   val draw = law.sample(new scala.util.Random(42))
   ```

3. Put the same family in a Figaro model:

   ```scala
   import com.cra.figaro.language.*
   val universe = Universe.createNew()
   val x = StudentT(5, 0, 1)(using "x", universe)
   // Configure evidence/inference here, then kill active algorithms and clear the universe.
   universe.clear()
   ```

Run the [complete executable examples](../FigaroExamples/src/main/scala/com/cra/figaro/example/documentation/CommonDistributionsExample.scala):

```sh
sbt "examples / Compile / runMain com.cra.figaro.example.documentation.CommonDistributionsExample"
```

## Choose a family and parameter convention

Continuous kernels/factories are in `com.cra.figaro.library.atomic.continuous`.
Count kernels/factories are in `com.cra.figaro.library.atomic.discrete`.

| Factory / kernel constructor | Parameters and support | When to use it |
| --- | --- | --- |
| `StudentT(df, location=0, scale=1)` / `StudentTDistribution(...)` | Real-valued; df is degrees of freedom, scale is **not** variance or standard deviation | Heavy-tailed residuals and robust uncertainty |
| `Cauchy(location, scale)` / `CauchyDistribution(...)` | Real-valued location-scale law; no mean or variance | Very heavy tails; Student t with df=1 |
| `Laplace(location, scale)` / `LaplaceDistribution(...)` | Real-valued; variance `2*scale^2` | Sharper central peak and exponential tails |
| `LogNormal(logMean, logStandardDeviation)` / `LogNormalDistribution(...)` | Positive; parameters are mean and **standard deviation of log(X)** | Multiplicative variation; not symmetric additive error |
| `Weibull(shape, scale)` / `WeibullDistribution(...)` | Nonnegative Weibull minimum/lifetime law, no location shift | Lifetimes, durations and flexible positive tails |
| `Triangular(lower, mode, upper)` / `TriangularDistribution(...)` | `lower < mode < upper`; bounded real value | Known range and interior most-likely value |
| `Kumaraswamy(a,b)` / `KumaraswamyDistribution(...)` | [0,1], two positive shapes | Beta-like bounded proportions with explicit inverse CDF |
| `NegativeBinomial(successes,p)` / `NegativeBinomialDistribution(...)` | Failure count k=0,1,... before r successes; **r may be real** | Variance greater than mean; not total trial count |
| `Hypergeometric(population,successes,draws)` / `HypergeometricDistribution(...)` | Successes in n draws without replacement from N containing K successes | Finite-population sampling, not independent binomial trials |

All named factories validate fixed parameters before registering an element. They
return `AtomicScalar` or `AtomicCount`. Kernel case-class parameters are public immutable
values; `copy` constructs and revalidates a new law. No inference state is copied.

### Initial numeric parameter ranges

- Student df, Weibull shape, both Kumaraswamy shapes, and negative-binomial r:
  `[0.001, 1e6]`. These are implementation ranges, not mathematical family limits.
- Location and triangular endpoints: finite with absolute value at most `1e100`.
  Scalar location-scale parameters: `[1e-100,1e100]` and strictly positive.
- Lognormal log mean: `[-500,500]`; log standard deviation: `[0.001,50]`.
- Negative-binomial success probability: `[1e-6,1]`; p=1 is exactly zero failures.
- Hypergeometric population: integer `[1,100000]`; successes and draws: `[0,N]`.
  Deterministic cases such as zero draws, zero successes or drawing the full population
  are supported. The population cap also bounds cumulative-sum work.

Eligible parameters do not guarantee every tail quantile or moment fits in a Double
or Int. Calculations refuse unrepresentable results instead of clipping draws to a
different distribution. Numeric divergence methods have additional range/work guards.

## API reference

### Continuous kernels: `ScalarDistribution`

These methods apply to all seven continuous kernel constructors in the table above.

| Public operation | Parameters | Returns / example |
| --- | --- | --- |
| `logDensity(x)` | Real x; NaN rejected | Natural-log density per unit x; e.g. `law.logDensity(4)` |
| `density(x)` | Same x | `exp(logDensity(x))`; can underflow to zero or overflow |
| `cdf(x)` | Real x, including infinities | `P(X <= x)`; e.g. `law.cdf(0)` |
| `survival(x)` | Real x, including infinities | Direct `P(X > x)`; e.g. `law.survival(100)` does not subtract a rounded CDF |
| `quantile(p)` | Finite p in [0,1] | Inverse CDF; p=0/1 return support endpoints, possibly infinite |
| `sample(rng)` | Non-null caller-owned `scala.util.Random` | Finite draw; e.g. `law.sample(new scala.util.Random(42))` |
| `support` | None | `(lower,upper)` support limits |
| `mean` | None | `Option[Double]`; None means mathematically undefined |
| `variance` | None | `Option[Double]`; None means undefined; numerical cancellation can throw |

Student t: mean is undefined for df<=1; variance is undefined there, infinite for
1<df<=2, and `scale^2*df/(df-2)` for df>2. Cauchy has neither moment. For the other
families the theoretical moments exist, but floating-point overflow/underflow remains
possible. `Some(infinity)` can mean a mathematically infinite Student t variance or
overflow of a finite moment; consult the family and parameters. Kumaraswamy variance
explicitly refuses cancellation-dominated subtraction instead of inventing zero variance.

Scalar sample generation uses inverse CDFs, consumes only the caller RNG, and bounds
retries for an open-unit draw at 1024. Student t uses a bounded log-coordinate inverse
calculation. This is reproducible general-purpose sampling, not a bulk-throughput claim.

### Count kernels: `CountDistribution`

| Public operation | Parameters | Returns / example |
| --- | --- | --- |
| `logProbability(k)` | Int count | Natural-log mass; negative infinity outside support |
| `probability(k)` | Int count | Mass, not a density per continuous unit |
| `cdf(k)` | Int count | `P(X <= k)` |
| `survival(k)` | Int count | Direct `P(X > k)`, **not** `P(X >= k)` |
| `quantile(p)` | Finite p in [0,1] | Smallest supported Int with CDF>=p; binary search has bounded work |
| `sample(rng)` | Non-null caller RNG | Inverse-transform Int count, never tail-clipped |
| `support` | None | `(minimum, Some(maximum))`, or None for an unbounded mathematical maximum |
| `mean`, `variance` | None | Double moments; negative binomial has mean `r*(1-p)/p`, variance `r*(1-p)/p^2` |

For example, `NegativeBinomialDistribution(2.5,.4).probability(3)` scores three
**failures**, not three trials or successes. A nondegenerate negative binomial is
mathematically unbounded: `quantile(1)` throws because infinity is not an Int. Interior
quantiles beyond `Int.MaxValue` also throw. The law is not silently truncated at that cap.

### Figaro elements and stochastic parameters

`ScalarElement(kernel)` and `CountElement(kernel)` accept validated fixed kernels.
Their overloads accept `Element[D]` for a scalar/count kernel type D and return a
non-caching conditional element. Both receive the usual contextual `Name` and
`ElementCollection`. Null fixed kernels fail before registration; invalid stochastic
parameters fail when the resulting kernel is evaluated.

`AtomicScalar` and `AtomicCount` expose `distribution`, `generateRandomness()` (scoped
Figaro RNG), `generateValue(rand)` (identity), `logDensity(value)` and `density(value)`.
The scalar adapter also exposes `logp(value)`, an alias of `logDensity`. In count
elements, `logDensity`/`density` mean log mass/mass, consistent with Figaro's interface.
The inherited prior-proposal `nextRandomness(old)` preserves the existing MH ratio
contract and throws if those legacy ratios are not representable.

Full compiler signatures, including standard case-class operations, are in the
[continuous](api/com.cra.figaro.library.atomic.continuous.md),
[discrete](api/com.cra.figaro.library.atomic.discrete.md) and
[shared atomic](api/com.cra.figaro.library.atomic.md) references.

## Common patterns

### 1. Robust hierarchical evidence, not just a heavy-tailed prior draw

```scala
val u = Universe.createNew()
val heavy = Flip(.3)(using "heavy", u)
val law = Apply(heavy, (b: Boolean) => StudentTDistribution(if (b) 3 else 30))
val observed = ScalarElement(law)
observed.observe(4.0)
// Run Importance on heavy, query its posterior, kill the algorithm, then u.clear().
```

This compares two tail assumptions using the actual Student t likelihood. Previously
one could construct a ratio of random variables to sample a t-like prior, but that did
not automatically provide the observation-ready transformed density. The runnable
example compares the inferred posterior against independently enumerated odds.

### 2. Overdispersed counts versus sampling without replacement

```scala
import com.cra.figaro.library.atomic.discrete.*
val overdispersed = NegativeBinomialDistribution(2.5, .4)
println((overdispersed.mean, overdispersed.variance)) // 3.75, 9.375
val finitePopulation = HypergeometricDistribution(20, 7, 5)
println(finitePopulation.probability(2))
```

A Poisson law fixes variance equal to mean; negative binomial does not. Hypergeometric
addresses a different question: draws compete for a finite stock. It is not a substitute
for negative binomial simply because both return counts. For random count parameters,
build the corresponding kernel with `Apply` and pass it to `CountElement`.

### 3. Positive quantities and tail probabilities

```scala
val multiplicative = LogNormalDistribution(.3, .8)
val lifetime = WeibullDistribution(1.7, 2.3)
println(multiplicative.quantile(.5)) // exp(.3), not .3
println(lifetime.survival(3.0))      // probability of exceeding 3
println(lifetime.quantile(.95))     // 95th percentile
```

Exponentiating a Normal through `Apply` can generate the same lognormal samples,
but the new kernel includes the `1/x` Jacobian in its density. Use survival directly
for upper tails; `1-cdf(x)` can lose a small positive probability through rounding.

## Inference compatibility and gotchas

| Path | Milestone evidence / limitation |
| --- | --- |
| Forward sampling | Seeded kernel and scoped element checks for every new family |
| Importance observations | Conditional-kernel posterior odds checked for all nine; log likelihood survives tested raw-density underflow |
| Ordinary MH | Constrained posterior probabilities checked for all nine; inherited finite-ratio limits remain |
| Isolated multi-chain MH | Serial/parallel seeded traces match for all nine; do not share Elements/Universes |
| Vector log-density samplers | A kernel supplies a scalar log density, not an automatically transformed unconstrained vector model; support/Jacobian choices remain the caller's responsibility |
| Factored inference / learning | No new exact factor conversion, conjugate learner, parameter fitting or EM contract is supplied; finite count support alone does not establish exact factor compatibility |

- Unlike `Normal(mean, variance)`, Student t/Cauchy/Laplace use **scale**, and lognormal
  uses **log standard deviation**. This is the most important parameter translation.
- A kernel can return positive-infinite density at a singular Weibull/Kumaraswamy
  boundary. The Figaro adapter rejects that exact observation to avoid infinite weights.
  Model measurement resolution or interval evidence rather than treating an endpoint
  density as a finite probability. A zero-density observation is also not a viable
  likelihood when every model alternative assigns it zero support.
- NaN, invalid probabilities and malformed fixed parameters throw `IllegalArgumentException`.
  Unrepresentable samples/quantiles and unresolved numeric moment calculations throw
  `ArithmeticException`; do not resample until success, which would condition the law.
- RNG endpoints, finite precision and heavy tails can make a valid mathematical draw
  unrepresentable. Large shape/scale ranges are not a promise of universal accuracy.
- Interruption is checked without clearing the thread flag. Kernels can be shared;
  RNGs and mutable Figaro elements require caller/worker ownership.
- Density underflow and weak inference proposals are different problems. Direct log
  likelihoods help arithmetic, not posterior exploration or effective sample size.

## Numerical references and verification

Student t and negative-binomial incomplete-beta evaluations are capped at 10,000
continued-fraction iterations per call. Failure raises `ArithmeticException`, never
a fabricated zero tail; interruption is checked before and after each such call.

Definitions follow the [NIST distribution gallery](https://www.itl.nist.gov/div898/handbook/eda/section3/eda366.htm),
the primary [SciPy lognormal](https://docs.scipy.org/doc/scipy/reference/generated/scipy.stats.lognorm.html)
and [negative-binomial](https://docs.scipy.org/doc/scipy/reference/generated/scipy.stats.nbinom.html)
parameter conventions, and the explicit formulas recorded in the independent oracle.
Student t uses stable incomplete-beta/log-beta identities rather than an overflowing
squared residual. High-shape Weibull variance cancels its linear log-gamma term using
the [DLMF series](https://dlmf.nist.gov/5.7). Existing Apache Commons Math supplies special
functions and the [hypergeometric backend](https://commons.apache.org/proper/commons-math/javadocs/api-3.6.1/org/apache/commons/math3/distribution/HypergeometricDistribution.html);
the latter receives no RNG and is not used to own sampling state. No third-party source
was copied and no new production dependency was introduced.

The [70-digit reference generator](../tools/common_distribution_reference.py) writes
Scala source to stdout only. Its independent checks differentiate/integrate densities,
check normalization and verify limiting families. `mpmath==1.3.0` is research-only.

```sh
python3 -B -m unittest discover -s tools -p 'test_common_*reference.py' -v
sbt "figaro / Test / testOnly com.cra.figaro.test.modernization.CommonDistributionsTest com.cra.figaro.test.modernization.CommonInformationMetricsTest"
```

See [milestone acceptance](COMMON_DISTRIBUTIONS_ACCEPTANCE.md) for publication and CI
status. Related: [information metrics](COMMON_INFORMATION_METRICS.md),
[user guide](USER_GUIDE.md), [inventory](DISTRIBUTION_SUPPORT.md), [roadmap](../ROADMAP.md).
