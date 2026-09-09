# Bounded independent precision and declared-region coverage

## Overview: when to enable these APIs

Use `BoundedIidPrecision` when you can draw **independent, identically distributed
(IID) observations with a known finite support** and want to stop at a stated
absolute error. Examples include direct simulation of a Bernoulli event or a
bounded utility under a normalized distribution. The support is part of the
model, not the smallest/largest value observed so far.

Use `DeclaredRegionCoverage` when you already know regions worth checking and
want an explicit inventory of visits. An optional, externally justified lower
bound on each region's sampling probability supplies a fixed-budget union bound.
It does not discover unknown modes. Neither API changes any existing sampler.

**Do not feed either IID contract correlated MCMC output.** A self-normalized
importance estimate is not an IID bounded observation either. Proposal-region
visits describe the proposal, not the posterior. Existing [inference health](INFERENCE_HEALTH.md)
and [MCMC precision](MULTI_CHAIN_MCMC.md) retain their separate contracts.

## Quick start: three steps

1. Import the bounded runner:

   ```scala
   import com.cra.figaro.algorithm.sampling.BoundedIidPrecision as B
   ```

2. Declare support, absolute error, confidence and a callback budget:

   ```scala
   val config = B.Config(0, 1, absoluteError = 0.02,
     alpha = 0.05, maxDraws = 100000, seed = 43)
   val result = B.run(config)(rng => if (rng.nextDouble() < 0.3) 1.0 else 0.0)
   ```

3. Check the stopping reason before claiming the requested precision:

   ```scala
   result.reason match {
     case B.StopReason.PrecisionReached => println((result.mean, result.errorBound))
     case B.StopReason.BudgetExhausted => println(s"Need more work: $result")
   }
   ```

`alpha=0.05` means at least 95% simultaneous coverage under the IID/support
assumptions, not a posterior probability statement about one realized interval.
`absoluteError=0.02` is a maximum distance from the reported sample mean, **not**
the full width and not relative error. It cannot establish relative precision
for an extremely rare probability that has not been observed.

## Common patterns: standard versus new

### 1. Bounded event probability with an explicit stopping decision

A fixed-work simulation commonly reports the event fraction after a chosen
sample count. A plug-in standard error can be zero when no events appear:

```scala
val rng = com.cra.figaro.util.SamplingRandom.scalaRandom(43)
val observations = Vector.fill(1000)(if (rng.nextDouble() < 0.001) 1.0 else 0.0)
val fixedEstimate = observations.sum / observations.size
val plugInSe = math.sqrt(fixedEstimate * (1-fixedEstimate) / observations.size)
```

The new runner needs no stored trace, retains a positive uncertainty bound for
an all-zero trace when the known support is `[0,1]`, and distinguishes precision
from running out of work:

```scala
val rare = B.run(B.Config(0, 1, 0.0001, maxDraws=1000)) { rng =>
  if (rng.nextDouble() < 0.001) 1.0 else 0.0
}
assert(rare.reason == B.StopReason.BudgetExhausted)
assert(rare.errorBound > 0)
```

There is no promise of faster sampling: this is a conservative reliability
baseline. For low-variance problems it can require substantially more draws
than a carefully implemented variance-adaptive confidence sequence.

### 2. Bounded utility under direct simulation

The standard fixed-budget mean estimates `E[exp(-X^2)]` for `X ~ N(0,1)` without
knowing in advance whether its error is small enough. Here the **utility**, not
the Gaussian state, is bounded, so the new API is applicable:

```scala
val utility = B.run(B.Config(0, 1, 0.03, maxDraws=50000)) { rng =>
  val x = rng.nextGaussian()
  math.exp(-x*x)
}
// utility.mean estimates 1/sqrt(3). Bounds apply to returned floating-point utility values.
```

Do not clamp an unbounded quantity merely to satisfy the API: that changes the
estimand. A bounded Bhattacharyya **affinity** integrand could use this contract
with a correctly normalized IID sampler, but a bound for affinity is not an
unchanged absolute-error bound for its negative logarithm. Generic KL/MI log
ratios are usually unbounded. [Existing information metrics](MONTE_CARLO_INFORMATION.md)
are not automatically routed through this runner.

### 3. Explicit region inventory alongside convergence checks

Ordinary R-hat/ESS can look healthy when every chain remains in one component.
Declare regions separately so an unvisited region is visible:

```scala
import com.cra.figaro.algorithm.sampling.DeclaredRegionCoverage as R
val regions = Vector(
  R.Region[Double]("left half", _ < 0.5),
  R.Region[Double]("right half", _ >= 0.5))
val occupancy = R.run(R.Config("uniform [0,1)", draws=1000), regions,
  Some(R.MassAssumption(0.5, "Each half has exactly half of the uniform support"))
)(_.nextDouble())
println(occupancy.occupancies)
println(occupancy.missBound)
```

If you sample only `N(0,1)` but want to check a putative distant region near 100,
add its predicate and **omit the mass assumption**. Zero visits flag that region
as unobserved; they cannot prove that the full target lacks such a mode. Do not
invent a positive mass lower bound for a proposal from the intended target's
mixture weights. An inventory can overlap or omit parts of the support.

## API reference

All names below are in `com.cra.figaro.algorithm.sampling`. Case classes expose
immutable fields and ordinary Scala `apply`/`copy`/pattern matching. The examples
above exercise the two public execution functions.

### `BoundedIidPrecision`

| Entry | Parameters and return contract |
| --- | --- |
| `Config(lower, upper, absoluteError, alpha=.05, maxDraws=100000, minDraws=100, checkEvery=100, seed=43, randomAlgorithm=default)` | Finite ordered support (equal endpoints allowed), positive finite error, alpha in `[1e-12,.5]`, max draws in `1..1000000`, minimum in `1..maxDraws`, positive check interval, non-null RNG algorithm. Fix before drawing. |
| `run(config)(sample)` | `sample: scala.util.Random => Double`, using the supplied private RNG. Returns `Result`; throws on invalid input, nonfinite/out-of-support draw, callback error or interruption. No partial success result. |
| `Result` | `mean`: arithmetic sample mean rounded to binary64; `lower`, `upper`: outward endpoints clipped to known support; `errorBound`: outward maximum endpoint distance from reported mean; `draws`: actual calls; `reason`: enum below; `config` and `randomProvider`: replay/assumption provenance. |
| `StopReason.PrecisionReached` | At least `minDraws` and `errorBound <= absoluteError` at a scheduled or final check. |
| `StopReason.BudgetExhausted` | Final check did not meet the precision contract. Its interval is still reported. |

Checks occur at multiples of `checkEvery` after `minDraws`, plus the final draw.
For a budget below 100, also set `minDraws` explicitly. No empirical variance,
R-hat or zero-MCSE shortcut is used. The interval is not intersected with past
intervals. Its center can move; clipped width may depend on data.

### `DeclaredRegionCoverage`

| Entry | Parameters and return contract |
| --- | --- |
| `Region[A](label, contains)` | Unique nonblank label, at most 200 characters; pure, fixed `A => Boolean` predicate. |
| `MassAssumption(minimumProbability, justification)` | Finite positive probability at most 1, and nonblank external justification; NOT estimated from this run. |
| `Config(samplingLaw, draws=10000, seed=43, randomAlgorithm=default)` | Nonblank identity of actual sampling law, fixed `1..1000000` draws, private RNG settings. |
| `run(config, regions, massAssumption=None)(sample)` | `Vector[Region[A]]` with 1..128 unique labels, at most 10 million total predicate calls; pure IID `scala.util.Random => A` callback. Returns detached `Result`, propagating invalid input, callback errors and interruption. |
| `Occupancy(label, count)` | Visit count for a declared region. Overlap means counts can sum above draws. |
| `Result` | `occupancies`, `status`, optional `missBound`, actual `predicateCalls`, `config`, `randomProvider`; no retained draws, predicates or sampler. |
| `Status.DeclaredInventoryObserved` | Each declared region was visited at least once; unknown regions remain unassessed. |
| `Status.DeclaredRegionsUnobserved` | At least one declared count is zero; inspect the counts. |
| `MissBound` | `probabilityUpper`: outward fixed-budget union bound; `logUpper`: approximate log of the mathematical bound; `assumption`: caller's retained justification. |

For example, `R.run(R.Config("uniform"), regions)(_.nextDouble())` performs
occupancy checks without asserting a mass bound. `.copy(draws=20000)` creates a
new configuration; choose the new budget independently of an intended
fixed-budget confidence claim. Repeatedly examining these region bounds and
stopping on a favorable value is **not** supported by their fixed-budget theorem.

## Research choice and numerical contract

At draw `n`, spend `alpha_n=alpha/[n(n+1)]`. Hoeffding bounds each two-sided failure
probability by `alpha_n`; summing over all positive integers gives `alpha`. Thus
`mean +/- (b-a)*sqrt(log(2*n*(n+1)/alpha)/(2*n))` covers the same true bounded IID
mean simultaneously, allowing data-dependent stopping. This elementary spending
construction has a `sqrt(log(n)/n)` scale; it is not an efficient LIL-rate boundary.

The implementation deliberately widens it: choose the smallest integer `k`
with `2*n*(n+1) <= alpha*2^k`, then replace the logarithm by
`k*0.6931471805599454`. Exact rational series arithmetic independently proves
that constant exceeds `log(2)`. The dyadic ceiling adds less than one `log(2)`
to the log term, apart from the tiny constant allowance. At n=1000, alpha=.05,
support width one, the ideal radius is about .09356 and this bound about .09493.

Returned binary64 observations are accumulated exactly with Java `BigDecimal`.
Directed decimal division and a verified upward square root enclose the
conservative radius; endpoints and the final error are rounded outward on
conversion to doubles. Overflow of the error bound can legitimately produce
positive infinity, which never satisfies a finite precision request. Region
probabilities use upward-rounded decimal exponentiation: positive underflow is
reported as the smallest positive double, not an exact zero. `logUpper` remains
an approximate diagnostic, not a directed bound.

This protects arithmetic **after** the callback returns. It cannot certify the
sampler's mathematical law, PRNG independence, model approximation, or numerical
error inside a utility/predicate. The probability theorem uses ideal IID draws;
a reproducible finite-state PRNG is an engineering implementation, not that proof.

For K declared regions with probability at least p under the actual IID law,
`P(any declared region missed) <= min(1, K*(1-p)^n)`. This is an **ex-ante**
probability bound, not a probability that an unseen mode exists after inspecting
these results. Overlapping regions are allowed by the union bound. If the
assumption is incorrect, its numerical bound has no statistical force.

Literature baseline, reviewed before implementation:

- [Hoeffding (1963)](https://repository.lib.ncsu.edu/items/3f47dae6-2e27-4a2c-9935-54aa9390ffaf): bounded-sum concentration, the foundation of the elementary spending proof above.
- [Howard et al. (2021)](https://arxiv.org/abs/1810.08240): time-uniform confidence sequences and sharper variance-sensitive/LIL-rate constructions under explicit conditions.
- [Waudby-Smith and Ramdas (2022 preprint revision)](https://arxiv.org/abs/2010.09686): betting-based bounded-mean intervals and confidence sequences adapt to unknown variance. These are the preferred next efficiency candidate, not replaced by a claim that Hoeffding is fastest.
- [Shekhar and Ramdas (2023)](https://arxiv.org/abs/2310.01547): theoretical near-optimality results supporting betting methods beyond empirical comparisons.
- [Chugg and Ramdas (2025 preprint)](https://arxiv.org/abs/2512.21300): newer closed-form empirical-Bernstein candidates, including time-varying conditional means. Recorded for follow-up; not implemented or locally benchmarked here.

The choice here is an auditable conservative baseline, not a new literature
efficiency result. A betting implementation needs predictable tuning, stable
capital accumulation/inversion, outward boundaries and separate calibration.

## Gotchas and verification

- Do not infer IID from passed diagnostics, support from extrema, or region mass
  from observed proportions. Invalid assumptions can produce misleading reports.
- Bounds are query-specific. Multiple simultaneous quantities/runs require their
  own multiplicity policy; 95% per query is not 95% for every query jointly.
- Bounded absolute precision does not prove rare-event discovery, small relative
  error, or accuracy of unbounded moments.
- Callbacks must be pure/reentrant if used concurrently. Interrupt checks occur
  before/after draws and predicates, not inside a blocked or infinite callback.
- Exact accumulation adds overhead relative to a raw sum, uses bounded-memory
  state rather than a trace, and makes no throughput improvement claim.
- Scala regression controls cover 600 predeclared stopped runs (200 each for
  Bernoulli .3, uniform [0,1), and Bernoulli .001), constant prefixes, extreme
  binary64 ranges, final-budget checks, failures, cancellation, replay and
  concurrent execution. The first local grid had 0/200 terminal misses for each
  law. This conservative empirical control is **not** a proof or sharpness test.
- Independent Python rational/high-precision tests verify the log allowance,
  radius dominance and region oracle. A distant-region regression has zero visits
  despite passing ordinary within-mode MCMC health checks.

## Integration verification

The modern.15 local acceptance gate used a fresh sbt action cache and clean
compilation of 322 library sources: **459 modernization tests in 46 suites
passed**, including all 14 new reliability regressions. Three independent
bounded-arithmetic reference tests, 81 evidence-tool tests, 18 documentation-tool
tests and seven artifact-tool tests passed. The generated reference contains
12,098 public method entries; 13,465 local links were verified.

All four JARs passed artifact checks, and the independent published-JAR consumer
executed both new APIs along with the existing inference/distribution checks.
The local thin JAR SHA-256 is
`0f642d435f5954a84447397fe9988a9da50b023da61f04a606e8d9de56e830ed`
(Temurin 17.0.4); this is not a claim of byte identity across JDK patch versions.
The GitHub workflow includes the new Scala and Python tests, consumer and
artifact checks, and independent cold-build reproducibility gates. Passing
feature-branch CI is required before main integration. The four existing
Scaladoc warnings remain; no new documentation warnings were introduced.

## Related and next work

[RNG policy](RNG_ASSESSMENT.md), [inference health](INFERENCE_HEALTH.md),
[importance calibration](IMPORTANCE_CALIBRATION.md), [information metrics](MONTE_CARLO_INFORMATION.md),
[owned graph design](OWNED_GRAPH_EXECUTION_DESIGN.md), and [reliability limits](RELIABILITY_LIMITS_ASSESSMENT.md).
The opt-in [variance-adaptive runner](ADAPTIVE_BOUNDED_PRECISION.md) now provides
that next efficiency step, with measured benefits and slowdown cases. Bounded
importance numerator/denominator inference and dependent-chain finite-run bounds
remain separate research/API projects, not automatic extensions of this contract.
