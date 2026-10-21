# Choosing a bounded indicator-filter budget

Published in **7.2.0**, not in the 7.1.0 library. See [installation](MAVEN_CENTRAL.md).
[Research provenance](RESEARCH_REFERENCES.md#inf-08).

## Overview

`BoundedParticleBudget` helps choose the three candidate counts required by
[`BoundedParticleFilter`](BOUNDED_PARTICLE_FILTER.md). Supply a separate, fixed-size
pilot of the same Boolean candidate/evidence law, a desired number of retained
successes and a hard work ceiling. The adviser reports uncertainty, either proposes
a frozen configuration or explains why the pilot/budget is insufficient. It never
changes an existing filter, retries failures or selects only successful runs.

This addresses work planning, not posterior precision. A hundred surviving states
can still share one ancestor or miss an important mode. General non-binary likelihood
weights need a different design; do not threshold them to fit this API.

## Quick start: three steps

1. Declare a pilot size before drawing, using the same candidate and evidence law.
2. Request advice with an explicit per-step maximum; inspect `status` and `interval`.
3. If `Ready`, convert the plan with a distinct production seed and use it explicitly.

```scala
import com.cra.figaro.algorithm.filtering.{BoundedParticleBudget as A, BoundedParticleFilter as B}
import com.cra.figaro.util.random

val model = B.Model(() => random.nextDouble(), (_: Double) => random.nextDouble())
val evidence: Double => Boolean = _ < .1
val pilot = A.runPilot(2000, seed = 101)(() => evidence(model.initial()))
val advice = A.advise(pilot, A.Request(maxCandidates = 10000, retainedSuccesses = 100))
println((advice.status, advice.interval, advice.tailAtHardCap))
advice.plan.foreach { plan =>
  val filter = B(model, plan.config(seed = 202))
  try println(filter.start(evidence).report)
  finally filter.close()
}
```

The illustrative law is IID uniform with a known survival probability. It does not
demonstrate inference on an unknown real-world model. A transition-step pilot needs
the correct predictive candidate law for that step, including its ancestor mixture;
an initial-prior pilot is not automatically representative of later observations.

## Common patterns: manual versus advised budgets

### 1. A pilot observes a usable survival rate

Manual configuration requires three choices:

```scala
val manual = B.Config(minCandidates = 1000, maxCandidates = 10000,
  targetSuccesses = 101, seed = 202)
```

If a separately completed IID pilot had 200 successes in 2000 attempts, use:

```scala
val advised = A.advise(A.Pilot(2000, 200), A.Request(10000))
advised.plan.foreach { p =>
  println((p.minCandidates, p.maxCandidates, p.expectedCandidates))
  val config = p.config(seed = 202)
}
```

The counts above are an illustrative input, not a measured experiment. The adviser
uses the lower confidence bound rather than assuming the observed 10% rate is exact.
Its selected cap may be below your ceiling. Expected work uses the *point estimate*,
so it is less conservative than the cap design and excludes the pilot's 2000 draws.
Production stopping and evidence correction are identical to a manually configured
bounded filter. You retain the choice to use a different manual policy.

### 2. The pilot sees no successes

```scala
val zero = A.advise(A.Pilot(2000, 0), A.Request(10000))
assert(zero.status == A.Status.NoPilotSuccesses)
assert(zero.plan.isEmpty)
println(zero.interval.upper) // positive: p=0 has not been established
```

Do not divide by the zero point estimate or keep rerunning until a pilot succeeds.
Review the evidence and proposal, or predeclare a new independent study with more
pilot effort and retain the full record. A sequential pilot would need a suitable
confidence-sequence method; this fixed-size interval is not valid under arbitrary
optional stopping or selection among repeated pilots.

### 3. The requested target cannot fit the hard ceiling

```scala
val tight = A.advise(A.Pilot(1000, 1), A.Request(100, retainedSuccesses = 100))
assert(tight.status == A.Status.InsufficientBudget)
assert(tight.plan.isEmpty)
println(tight.tailAtHardCap)
```

The adviser does not exceed the cap, silently lower your target, or promise that
the model is impossible. You can explicitly reconsider the work ceiling or target,
or improve the proposal. Even a `Ready` plan can exhaust its cap in production.
Inspect the normal filter's health, ancestry and stopping report.

## Public API reference

| API | Parameters and return | Example |
| --- | --- | --- |
| `maximumCount` | Current engineering limit: 1,000,000 pilot attempts or candidate cap; not an accuracy guarantee | `A.maximumCount` |
| `Pilot(attempts, successes)` | Completed fixed-size IID counts; `1 <= attempts <= maximumCount`, `0 <= successes <= attempts`; returns immutable pilot | `A.Pilot(2000,200)` |
| `Pilot.estimate` | No arguments; empirical survival fraction as `Double` | `pilot.estimate` |
| `Request(maxCandidates, retainedSuccesses=100, confidence=.95, missProbability=.05)` | Hard cap 2..1,000,000; desired retained successes 1..999,999; interval coverage .8.. .999; design miss probability .001.. .25 | `A.Request(10000, 200)` |
| `runPilot(attempts, seed, algorithm=default)(draw)` | Fixed number of IID `() => Boolean` calls in an owned Figaro RNG scope; returns `Pilot`; no partial return on error/interruption | `A.runPilot(2000,101)(() => random.nextBoolean())` |
| `advise(pilot, request)` | Computes interval, feasibility and optional plan; returns `Advice`; no production sampling | `A.advise(pilot,A.Request(10000))` |
| `Interval(lower, upper)` | Immutable numerical two-sided Clopper–Pearson bounds for fixed p; not a Bayesian credible interval | `advice.interval.lower` |
| `Status` | `Ready`, `NoPilotSuccesses`, `InsufficientBudget` | `advice.status == A.Status.Ready` |
| `Advice(pilot, request, interval, status, tailAtHardCap, plan)` | Immutable result; tail is single-step target-miss probability at the lower p bound and user's cap; optional `Plan` | `advice.plan.foreach(println)` |
| `Plan(minCandidates, maxCandidates, targetSuccesses, expectedCandidates)` | Immutable suggestion; target is requested survivors + 1; expected candidate count uses stationary IID point estimate | `advice.plan.map(_.expectedCandidates)` |
| `Plan.config(seed, algorithm=default, health=default)` | Explicit conversion to `B.Config`; caller supplies a separate production seed/stream | `advice.plan.get.config(202)` |

Input violations throw `IllegalArgumentException`. Unresolved numerical calculations
throw `ArithmeticException`, not a misleading `Ready` result. Pilot callback errors
and interruption propagate; the caller's ambient RNG scope is restored. Constructors
for result records are ordinary data constructors, not independent certification of
user-invented values. Use `advise` to obtain a computed plan.

## Method, assumptions and numerical limits

For n pilot attempts and k successes, obtain a two-sided Clopper–Pearson interval
at coverage `confidence`. This is frequentist coverage over repeated predeclared
IID pilots, not a probability that a particular fixed p lies inside this interval.
When k=0, the lower bound is zero and advice is inconclusive. When k=n, the lower
bound is still below one. Exact boundary formulas avoid generic inverse error.

For requested survivors s, set target r=s+1 because adaptive target stopping discards
the final success from both evidence estimation and retained ancestry. Choose the
smallest cap N (within the user's ceiling) satisfying
`P(Binomial(N, lowerBound) < r) <= missProbability`. Set the initial floor to
`min(N, ceil(r / pilot.estimate))`, at least two. The floor is an engineering
choice, not an optimality theorem. The fixed initial batch can retain more than s.

The pilot interval's error rate and the conditional production miss rate are
distinct. Neither is a posterior error bound or a guarantee across a sequence of
different predictive laws. Frozen advice can become unrepresentative when the
observation or ancestor population changes. Never pool those changing probabilities
as one IID pilot, or reinterpret the count of resampled particles as independent
pilot attempts. The API cannot verify callback independence or stream separation.

Expected work is `E[max(floor, min(T_r, cap))]`, where T_r is the trial count of
the rth IID success. Figaro evaluates a binomial identity; exhaustive small stopping
trees check that identity independently. It is not wall-clock time or memory usage.

Commons Math's existing beta/binomial numerical functions are reused with no new
runtime dependency and no sampling from that library's RNG. Generic beta inverses
use absolute tolerance 1e-14 and a 2e-14 outward margin. This is not certified
interval arithmetic; explicit count/confidence/miss limits and independent numerical
checks bound the tested scope, not every possible floating-point input.

Validation: exhaustive small-count coverage sums, minimal-cap/monotonicity controls,
finite stopping trees, zero/all-success and million-count boundaries, RNG restoration,
fixed callback counts, interruption and packaged API checks. The independent
[80-digit oracle](../tools/budget_reference.py) checks captured output; it does not
substitute synthetic rows for missing execution. See [readiness/follow-up status](RELEASE_7_1_STATUS.md).

Related: [bounded filtering](BOUNDED_PARTICLE_FILTER.md),
[inference health](INFERENCE_HEALTH.md), [RNG streams](RNG_STREAMS.md),
[fixed-size versus sequential IID safeguards](BOUNDED_IID_RELIABILITY.md).
