# Query-aware rare-event proposals

## Overview: when to enable this

Use `RareEventImportance` when the question is **the probability of a specified
rare event under a normalized vector distribution**. Examples include a quantity
exceeding a limit or either of two disjoint failure regions occurring. Ordinary
prior sampling may see few or no events, even when its general-purpose weight
diagnostics look excellent.

This opt-in API draws more often near the event and corrects every contribution
using the full proposal density. It can accept a proposal you specify, or fit one
Gaussian using discarded cross-entropy (CE) pilot rounds. Modern.22 adds
[event-weighted multi-component fitting](WEIGHTED_RARE_EVENT_MIXTURES.md), with explicit
component count and independent production. Existing `Importance`,
`VectorImportance`, graph samplers and stopping policies are unchanged.

Do not enable it merely because a model is large. It is not a replacement for
posterior inference with an unknown normalizing constant, an arbitrary graph
compiler, automatic mixture selection or guaranteed rare-mode discovery.

## Quick start in three steps

1. Define the normalized base law and a score whose high values mean the event:

   ```scala
   import com.cra.figaro.algorithm.sampling.{RareEventImportance as R, VectorImportance as V}
   import com.cra.figaro.library.atomic.continuous.{MultivariateGaussianDistribution as G}
   val base = V.Gaussian(G(Vector(0.0),Vector(Vector(1.0))))
   val score = (x: Vector[Double]) => x.head
   val threshold = 5.0 // Event: X >= 5.
   ```

2. Fit using an explicit, discarded pilot budget and covariance regularization:

   ```scala
   val fit = R.fitGaussian(base,score,threshold,R.FitConfig(
     maxRounds=12,maxScoreEvaluations=12000,diagonalRidge=Vector(.25)))
   ```

3. If fitting succeeds, run fresh fixed-budget production and inspect diagnostics:

   ```scala
   fit.proposal match {
     case Some(q) =>
       val result = R.run(q,score,threshold,R.Config(draws=10000,maxScoreEvaluations=10000))
       println((result.probability,result.relativeStandardError,result.eventEss,result.warnings))
       println(s"Total score calls: ${fit.scoreEvaluations + result.scoreEvaluations}")
     case None => println((fit.status,fit.message,fit.rounds))
   }
   ```

The ridge `.25` is a variance in this standardized coordinate, not a universal
setting. It prevents overly narrow proposals in this example. It changes the
proposal, not the base law or event. Failed fits return no production proposal;
the API does not silently fall back, reduce an ESS requirement or increase budgets.

## What changes mathematically

For event A and normalized base p, production estimates the average of
`indicator(A)*p(x)/q(x)` over draws from q. It does **not** divide by the sum of
importance weights. This distinction matters: `VectorImportance` is intended for
self-normalized inference and can work with an unnormalized target; this API needs
the actual normalized base density.

The defensive proposal is `q = epsilon*p + (1-epsilon)*candidate`. Every weight
uses this **whole mixture**, not just the component that generated the draw.
Under consistent normalized sampling/density contracts, `p/q <= 1/epsilon`.
This preserves support and bounds event contributions; it does not guarantee a
given finite budget will sample every event region. Custom callbacks must be pure,
stateless and sample the density they report. Their normalization cannot be
established by this software from finitely many evaluations.

Pilot rounds move an upper score quantile toward the requested threshold and fit
Gaussian moments to the elite points using base/full-proposal weights. Covariance
ridge and inflation are explicit. All pilot points are discarded from estimation.
Pilot stream 0 and production stream 1 use the versioned `RandomStreams` allocator;
repeating the same root seed does not reuse the same logical stream between phases.

## API reference

All vectors have matching dimension 1..32, finite coordinates and a common vector
Lebesgue measure. Thresholds and score results must be finite. An event includes
equality (`score >= threshold`). Callback errors propagate; no partial probability
result is published. Interruption remains set after cancellation.

| Public function | Parameters | Returns | Example |
| --- | --- | --- | --- |
| `prior(base)` | Normalized `VectorImportance.Proposal` | `Frozen` direct-Monte-Carlo control | `R.prior(base)` |
| `defensive(base,candidate,priorWeight=.1)` | Matching normalized laws; priorWeight in [1e-6,1) | `Frozen` full mixture | `R.defensive(base,shifted,.1)` |
| `run(law,score,threshold,config=Config())` | Frozen law, pure finite score, finite boundary, production settings | `Result`, not an accuracy-success flag | `R.run(q,score,5)` |
| `fitGaussian(base,score,threshold,config=FitConfig())` | Normalized prior, score/boundary and pilot settings | `FitResult`; proposal only for `Fitted` | `R.fitGaussian(base,score,5,policy)` |

`Frozen` exposes immutable `base`, `proposal`, and `defensiveWeight` fields. Its
constructor is private: use `prior` or `defensive`. A supplied candidate may be an
existing Gaussian mixture, Student t or other normalized `VectorImportance.Proposal`.
The Gaussian fitter does not fit multiple components automatically.

### Production Config

| Parameter | Default / contract |
| --- | --- |
| `draws` | 10000; positive, at most 10000000 |
| `maxScoreEvaluations` | 10000; positive. Actual n is min(draws,cap). |
| `seed` | 42; root seed, logical production lane 1 |
| `randomAlgorithm` | Current Figaro default; explicit named backend can override it |
| `streams` | `RandomStreams.Config()`; optional supported partitioned allocation |

For example, `R.Config(draws=20000,maxScoreEvaluations=20000,seed=91)` changes the
fixed production budget. Updating only `draws` leaves the default call cap in effect.

### FitConfig

| Parameter | Default / contract |
| --- | --- |
| `drawsPerRound` | 1000, at least 20; complete batches only |
| `maxRounds` | 8, in 1..64 |
| `eliteFraction` | .1, in (0,.5]; upper score fraction before event-threshold capping |
| `minEliteEss` | 20, at least 2; weighted elite information, not just count |
| `priorWeight` | .1, in [1e-6,1); retained at every adaptive stage and in frozen production |
| `covarianceInflation` | 1.5, positive finite covariance multiplier, not SD multiplier |
| `diagonalRidge` | Empty, or one nonnegative finite variance per coordinate, added before inflation |
| `maxScoreEvaluations` | 8000, positive; an incomplete next batch is not started |
| `maxStoredValues` | 1000000, positive and <=10000000; bounds batch slots N*(d+4), not total JVM heap |
| `seed`, `randomAlgorithm`, `streams` | Same meanings/defaults as production, but pilot lane 0 |

Settings should reflect units and known event geometry. In an initial five-sigma
test without a ridge, weighted elite ESS fell below 20 despite hundreds of event
hits, and the fit correctly refused. The explicitly regularized test reaches the
threshold in five rounds with final elite ESS about 196.5. These are fixed-seed
examples, not universally recommended tuning values or guaranteed round counts.
An indicator-valued score is valid but provides no intermediate notion of proximity;
if no pilot event occurs, quantile adaptation can stall. Prefer a meaningful continuous
limit-state score when one is available, or supply an informed fixed proposal.

### Results and statuses

`Result` contains:

- `probability`, `logProbability`: ordinary IS estimate and stable log estimate.
  A positive estimate below binary64 range gives `None` and a finite log value.
  No positive contributions gives `Some(0)` and negative infinity, not proof that
  the event is impossible. Ordinary IS probability estimates can exceed one due
  to finite-sample noise; they are deliberately not clipped.
- `standardError`, `logStandardError`, `relativeStandardError`: empirical
  fixed-budget MCSE, log MCSE, and MCSE/estimate. Zero contributions or one draw
  make these unavailable; positive underflow can also make ordinary MCSE unavailable.
  Centered, scaled accumulation avoids raw-moment cancellation. These are estimates,
  not confidence sequences, guaranteed confidence intervals or stopping criteria.
- `eventEss`, `largestContributionShare`: concentration of **event contributions**,
  not raw-weight ESS or MCMC ESS. `eventHits` counts score hits; `positiveContributions`
  counts those with positive base density.
- `scoreEvaluations`, `densityEvaluations`: actual production work. Density work
  counts base and full-proposal calls, not nested component evaluations.
- `reason`: `DrawsReached` or `ScoreBudgetReached`, neither an accuracy claim.
- `warnings`: zero/few contributions, concentration and large empirical relative
  error, plus a standing caution about undiscovered event regions.
- `randomStream`: provider/allocation/start-of-stream replay descriptor.

`FitResult` contains `status`, optional `proposal`, `rounds`, score/density work,
`randomStream` and an explanatory `message`. Each `Round` records `level`,
`eliteEss` and observed `eventHits`. Statuses are `Fitted`, `RoundBudgetReached`,
`ScoreBudgetReached`, `InsufficientElite` and `NumericalFailure`. Fitted means a
numerically acceptable pilot reached the threshold, not that the resulting
probability will be accurate. Case-class results/configurations also have ordinary
Scala `copy`, equality and extraction; see the [generated reference](api/README.md).

## Three common patterns: standard versus new

### 1. A one-sided rare limit

```scala
val standard = R.run(R.prior(base),score,5,R.Config(seed=91))
val shifted = V.Gaussian(G(Vector(5.0),Vector(Vector(1.0))))
val targeted = R.run(R.defensive(base,shifted),score,5,R.Config(seed=91))
```

Both use 10000 score evaluations. The standard approach will usually observe zero
events at five sigma. The targeted approach draws near the threshold, then corrects
for that changed sampling law. If you know the event direction, an explicit shift
avoids pilot cost. If not, the quick-start fitter is an opt-in alternative.

### 2. Pilot-inclusive comparison under one total budget

```scala
val total = 10000
val fit = R.fitGaussian(base,score,3,R.FitConfig(
  drawsPerRound=500,maxRounds=10,maxScoreEvaluations=5000,diagonalRidge=Vector(.25),seed=92))
val control = R.run(R.prior(base),score,3,R.Config(draws=total,maxScoreEvaluations=total,seed=92))
val targeted = fit.proposal.map { q =>
  val remaining = total-fit.scoreEvaluations
  R.run(q,score,3,R.Config(draws=remaining,maxScoreEvaluations=remaining,seed=92))
}
```

Do not compare 10000 production draws plus an uncounted pilot to 10000 control
draws and call it equal work. Track score calls, density calls and elapsed time:
fitting and evaluating a mixture cost more than drawing from the prior.

### 3. Two declared event regions

```scala
val twoSided = (x: Vector[Double]) => math.abs(x.head)
val candidate = V.Mixture(Vector(.5,.5),Vector(
  V.Gaussian(G(Vector(4.0),Vector(Vector(1.0)))),
  V.Gaussian(G(Vector(-4.0),Vector(Vector(1.0))))))
val targeted = R.run(R.defensive(base,candidate),twoSided,4,R.Config(seed=93))
```

The standard prior sampler often sees neither tail. One fitted Gaussian can be
inadequate or refuse; the supplied mixture explicitly covers both declared regions.
Specifying only the positive-tail component does not tell the algorithm that the
negative region matters. A defensive prior component supplies support, not finite-
budget discovery. Keep independent proposals and declared-region checks in the workflow.

## Evidence and limitations

See the [complete pilot-inclusive study](RARE_EVENT_ACCEPTANCE.md) for all 1080
rows from three JVMs, retained fit refusals and ordinary-MC zero-hit results.
On the three-sigma fixture, the fitted method cuts observed relative RMSE from
33.2% to 1.4% at the same total 10000 score calls. Runtime per fixed-budget run
increases; this is improved statistical efficiency, not faster individual draws.
On the two-sided fixture the fitter refuses 22/30 trials, while the explicitly
supplied two-component proposal completes all 30. No universal winner is selected.

The missed-region regression deliberately targets just one of two narrow regions.
It reports roughly half the true total probability despite a small empirical MCSE.
Neither this API nor its defensive component fixes that fundamental finite-sample
diagnostic limitation. Do not use its MCSE for universally reliable automatic stopping.

## Literature basis and exclusions

[Owen's importance-sampling chapter](https://artowen.su.domains/mc/Ch-var-is.pdf)
provides the ordinary/self-normalized distinction, full-mixture weighting and
defensive-sampling foundation. The design uses ordinary IS because the base
normalizer is known; the existing self-normalized API remains available for other
inference contracts. The proposal should approximate the event-weighted density,
not merely the prior or an unrelated posterior moment.

[Gao and Karniadakis, Safe-ICE (2025 preprint)](https://arxiv.org/abs/2509.07160)
examines weighted cross-entropy adaptation, mixture complexity and heavy-tail
exploration. Its fuller penalized mixture and vMF/Nakagami construction is not
implemented here. The bounded first implementation instead uses weighted Gaussian
elite moments, fixed prior defense, explicit regularization and fresh production.
It makes no claim to reproduce Safe-ICE's reported experiments or guarantees.
Automatic component selection, richer weighted mixture fitting, smoothed levels,
high-dimensional rare-event geometry and graph rewriting remain follow-on candidates.
No external source code is copied and no runtime dependency is added.

Related: [vector proposals](VECTOR_IMPORTANCE.md), [mixture fitting](MIXTURE_PROPOSALS.md),
[inference health](INFERENCE_HEALTH.md), [declared regions](BOUNDED_IID_RELIABILITY.md),
[RNG streams](RNG_STREAMS.md) and [roadmap](../ROADMAP.md).
