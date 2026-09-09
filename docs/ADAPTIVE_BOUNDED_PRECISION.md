# Variance-adaptive bounded precision

## Overview and when to use it

`EmpiricalBernsteinPrecision` estimates a bounded IID mean with a time-uniform
confidence sequence. Unlike the [Hoeffding baseline](BOUNDED_IID_RELIABILITY.md),
its interval responds to past variability. Enable it for low-variance bounded
utilities or expensive independent simulation callbacks. Keep Hoeffding available:
the adaptive arithmetic can cost more than the draws it saves.

This is an opt-in **predictable plug-in empirical-Bernstein** method, not a
change to importance/MCMC stopping and not numerical inversion of a hedged
betting-capital process. It reports a predictable-weighted estimate separately
from the ordinary sample mean. The support and IID assumptions remain the caller's
responsibility; passing diagnostics cannot establish them.

## Quick start in three steps

1. Import the runner and shared configuration:

   ```scala
   import com.cra.figaro.algorithm.sampling.{BoundedIidPrecision as B, EmpiricalBernsteinPrecision as E}
   ```

2. Supply an independent bounded utility:

   ```scala
   val config = B.Config(0, 1, absoluteError=.02, maxDraws=100000, seed=43)
   val result = E.run(config)(rng => .49 + .02*rng.nextDouble())
   ```

3. Inspect the outcome, not just the estimate:

   ```scala
   if (result.reason == B.StopReason.PrecisionReached)
     println((result.estimate, result.lower, result.upper))
   else println(s"Budget exhausted: $result")
   ```

## API reference

`run(config: BoundedIidPrecision.Config)(sample: scala.util.Random => Double)`
returns an immutable `EmpiricalBernsteinPrecision.Result`. The config parameters,
defaults, support checks, absolute-error meaning, minimum work, check spacing,
maximum one million draws and private-RNG policy are exactly those in the
[bounded API reference](BOUNDED_IID_RELIABILITY.md#api-reference). No new dependency
or algorithm-selection default is introduced.

| Result field | Meaning |
| --- | --- |
| `estimate` | Predictable-weighted center in original units, not the arithmetic mean |
| `sampleMean` | Ordinary arithmetic mean, included for comparison; the reported error is NOT centered here |
| `lower`, `upper` | Outward confidence-sequence endpoints clipped to the known support |
| `errorBound` | Maximum outward distance from `estimate` to either endpoint |
| `draws` | Actual callback count |
| `reason` | `PrecisionReached` or `BudgetExhausted` from `BoundedIidPrecision.StopReason` |
| `config`, `randomProvider` | Original controls and RNG provenance |

Null arguments, nonfinite/out-of-support observations and invalid config are
rejected. Callback errors and cooperative interruption propagate without a partial
success result. Callbacks must return promptly and use their supplied private RNG.
Equal support endpoints delegate to the original bounded runner.

## Three common patterns

### Compare the two precision policies

```scala
def utility(rng: scala.util.Random): Double = .49 + .02*rng.nextDouble()
val plain = B.run(config)(utility)
val adaptive = E.run(config)(utility)
println((plain.draws, adaptive.draws))
```

The same seed/backend gives a shared initial draw sequence, but stopping times
and estimators differ. Do not choose the narrower completed interval after running
both at alpha=.05 and call that a jointly 95% policy. Preselect the method, or
allocate error probability across the methods/queries being inspected.

### Expensive bounded utility

```scala
val boundedUtility = E.run(config) { rng =>
  val x = rng.nextGaussian()
  math.exp(-x*x) // utility in [0,1], although the underlying state is unbounded
}
```

A more costly independent simulation can replace the callback. A Markov-chain
transition cannot: correlated draws do not satisfy this API's contract. Numerical
approximation inside the callback is not covered by the post-callback arithmetic.

### Rare-event probability with modest absolute accuracy

```scala
val rare = E.run(B.Config(0,1,.02,maxDraws=100000)) { rng =>
  if (rng.nextDouble() < .001) 1.0 else 0.0
}
```

Stopping before an event appears can be valid for this **absolute** tolerance.
It does not establish small relative error, zero event probability, or discovery
of every mode. At tighter tolerances a zero prefix still carries positive
uncertainty and may exhaust the budget. Use [declared regions](BOUNDED_IID_RELIABILITY.md)
as a separate inventory diagnostic, not a replacement theorem.

## Research and numerical decisions

The method follows [Waudby-Smith and Ramdas, Theorem 2](https://arxiv.org/html/2010.09686v7):
for normalized observations, the weighted center has radius
`[log(2/alpha) + sum((X_i-prediction_(i-1))^2 * g(lambda_i))] / sum(lambda_i)`,
where `g(l)=-log(1-l)-l`. The bets and predictions must be selected before the
current observation. We quantize the variance-based bets downward to powers of
two in `[2^-32, .5]`; this predictable choice preserves the theorem, but changes
efficiency compared with the paper's unquantized policy. We do not claim its
stronger hedged-capital or optimal-rate results for this implementation.

Exact binary64 observations and decimal outward enclosures handle normalization,
weighted sums and endpoints. The positive series for `g` is summed through power
48 with a geometric remainder upper bound. `log(2/alpha)` is widened using the
existing proved upper constant for log(2). Floating-point variance/prediction
calculations select bets only; they do not supply an unguarded interval radius.
The independent oracle evaluates **direct logarithms**, with up to 700 decimal
digits for the smallest series remainders.

[Near-optimal betting research](https://arxiv.org/abs/2310.01547) supports keeping
full betting-capital methods on the efficiency roadmap. [New closed-form
empirical-Bernstein work](https://arxiv.org/abs/2512.21300) is also a candidate;
neither is silently substituted for the implemented theorem.

## Measured acceptance and limitations

[Complete evidence](ADAPTIVE_PRECISION_RUNS.csv): 480 rows, 20 paired seeds repeated
in three fresh JVMs, four synthetic laws and two policies. This is **not** 480
independent datasets. Error target .02, alpha .05, cap 100000, checkpoint 100.
Method order alternates by seed; initial warmups precede the retained rows.
Elapsed time includes runner/RNG setup and callbacks, but not JVM/sbt startup.
All rows, including early JIT costs, are retained. Timing assertions are non-gating.

| Case | Median samples saved (baseline/adaptive) | Median runtime ratio (baseline/adaptive) |
| --- | --- | --- |
| Cheap Bernoulli(.5) | 1.425x | 0.057x: adaptive is about 17.5x slower |
| Bernoulli(.001), absolute-error task | 62.4x | 4.576x |
| Uniform bounded utility [.49,.51] | 62.4x | 5.728x |
| Synthetic costly antisymmetric bounded utility | 62.4x | 30.625x |

These are measured fixture results, not general speedup promises. No trial in
this timing grid missed its true mean or exhausted its budget. A separate
held-out 400-run optional-stopping grid had 1/100 terminal misses for Bernoulli(.5)
and 0/100 for each of Bernoulli(.01), Bernoulli(.001), and uniform [.45,.55].
This is empirical regression evidence, not the coverage proof or an exact
false-alarm calibration. Eight Scala tests also cover direct-log references,
nonzero prefix uncertainty, numeric extremes, replay, cancellation and concurrency.

Reproduce the study in three fresh JVMs with
`examples / Compile / runMain com.cra.figaro.example.BoundedPrecisionStudy 20`,
then use `tools/summarize_bounded_precision.py` to validate the complete grid.
Its validator rejects missing/duplicate rows, invalid work and inconsistent
precision claims, while retaining misses and budget exhaustion.

## Related and next work

Modern.16 local integration passed a clean build of 323 library sources and all
467 modernization tests, independent direct-log/series references, all four JAR
checks and the isolated published-JAR consumer. The public reference has 12,101
method entries; 13,479 local links were checked. GitHub CI repeats the new tests,
evidence validation, publication/consumer and cold-rebuild checks before integration.

[Bounded baseline](BOUNDED_IID_RELIABILITY.md), [inference health](INFERENCE_HEALTH.md),
[static executor design](OWNED_GRAPH_EXECUTION_DESIGN.md), [roadmap](../ROADMAP.md).
The next approved milestone is implementing the restricted static executor, not
making arbitrary existing graphs thread-safe or automatically changing samplers.
