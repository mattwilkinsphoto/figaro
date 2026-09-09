# Event-weighted Gaussian mixture proposals

## Overview and when to enable

Use this opt-in extension when the event has multiple separated regions. A single
Gaussian can waste draws between regions or become too narrow around one of them.
`RareEventImportance.fitMixture` fits weighted elite samples, not an unweighted
posterior trace. The original unweighted `GaussianMixtureProposal.fit` and
single-Gaussian `fitGaussian` remain available and unchanged in purpose.

Two components are not automatically better than one. The study below contains
unnecessary-component failures and cases where prior sampling is cheaper.

## Quick start (three steps)

```scala
import com.cra.figaro.algorithm.sampling.{RareEventImportance as R, GaussianMixtureProposal as M, VectorImportance as V}
import com.cra.figaro.library.atomic.continuous.{MultivariateGaussianDistribution as G}
// 1. Declare the normalized base and event.
val base=V.Gaussian(G(Vector(0.0),Vector(Vector(1.0))))
val score=(x: Vector[Double]) => math.abs(x.head)
// 2. Fit with a separate pilot budget; ridge is a variance in these coordinates.
val fit=R.fitMixture(base,score,4,
  R.FitConfig(drawsPerRound=1000,maxRounds=8,maxScoreEvaluations=8000),
  M.Config(components=2,diagonalRidge=Vector(.25),maxDensityEvaluations=5000000))
// 3. Only successful fits may start independent production.
fit.proposal.foreach(q => println(R.run(q,score,4).probability))
if(fit.proposal.isEmpty) println((fit.status,fit.message))
```

## API reference

| Entry point | Parameters | Return and example |
| --- | --- | --- |
| `M.fitWeighted(points,logWeights,config)` | Matching finite vectors (dimension 1..32); finite log masses or -Infinity; existing `M.Config` | `M.Result`, with mixture only for `Fitted`; `M.fitWeighted(xs,logW,M.Config(components=2))` |
| `R.fitMixture(base,score,threshold,config,mixture)` | Normalized vector base, pure finite score, finite threshold, `R.FitConfig`, `M.Config` | `R.FitResult`, frozen defensive proposal only on success; quick start above |

The full configuration defaults are in [mixture proposals](MIXTURE_PROPOSALS.md)
and [rare-event proposals](RARE_EVENT_PROPOSALS.md). Differences for weighted fitting:

- `minComponentDraws` means weighted component ESS, not raw responsibility mass.
  Overall pilot ESS must also support the requested component count.
- `mixture.diagonalRidge` and `mixture.covarianceInflation` control mixture fitting;
  the same fields in the outer `R.FitConfig` are for the single-Gaussian fitter.
- `mixture.maxDensityEvaluations` is an aggregate component-evaluation budget across
  all CE rounds. `FitResult.componentDensityEvaluations` records actual EM work,
  separately from base/full-proposal `densityEvaluations` and score calls.
- Outer storage preflight includes batch and weighted-fit logical scalar slots;
  the inner fitter also enforces its own cap. Neither cap measures exact heap bytes.

The result/status fields retain the original contracts. `MixtureFitFailure` means
no proposal; its message identifies the EM refusal. No component pruning, reseeding,
hidden ridge, budget increase or partial-success fallback occurs.

## Three common patterns

**Compare one versus multiple regions:**
```scala
val one=R.fitGaussian(base,score,4)
val two=R.fitMixture(base,score,4,mixture=M.Config(components=2,diagonalRidge=Vector(.25)))
println((one.status,two.status)) // A fit status is not a precision certificate.
```

**Preserve a total score budget:**
```scala
val total=12000
fit.proposal.foreach { q =>
  val remaining=total-fit.scoreEvaluations
  require(remaining>0)
  val result=R.run(q,score,4,R.Config(draws=remaining,maxScoreEvaluations=remaining))
  println((result.probability,fit.componentDensityEvaluations,result.warnings))
}
```

**Use a supplied mixture when the regions are already known:**
```scala
val supplied=V.Mixture(Vector(.5,.5),Vector(
  V.Gaussian(G(Vector(-4.0),Vector(Vector(1.0)))),
  V.Gaussian(G(Vector(4.0),Vector(Vector(1.0))))))
val control=R.run(R.defensive(base,supplied),score,4)
```
This avoids fitting cost but uses explicit region knowledge. It is an important
control, not an automatically learned result.

## Evidence and gotchas

The [complete study](CAPABILITY_EXPANSION_ACCEPTANCE.md) retains 30 predeclared seeds,
three event geometries, four methods and three JVM runs. On the two-tail case,
weighted mixtures complete 30/30 and reduce relative RMSE from 4.71% to 1.52%
against the single-Gaussian control at equal total score calls. Their fixed-budget
runtime is higher. On the common/one-tail cases, 8/30 and 4/30 fits are refused.
Do not discard those failures when reporting performance.

The new study uses 1000-draw pilot batches, unlike the earlier 500-draw study;
the earlier 22/30 single-Gaussian refusals are not a paired baseline here.
All production points are fresh; pilot stream 0 and production stream 1 are
separate logical streams. A defensive prior ensures support, not finite-budget
discovery. Small empirical MCSE is still not universal automatic stopping.
EM convergence is numerical stationarity, not proof of the best fit.

## Research and related modules

[Safe-ICE, Gao/Karniadakis (2025 preprint)](https://arxiv.org/abs/2509.07160)
motivates weighted mixture adaptation. This implementation uses ordinary weighted
Gaussian EM and hard elite levels, not its penalized pruning, smoothed levels or
vMF/Nakagami/heavy-tail scheme. No source code or new dependency was copied.

Related: [static proposal bridge](STATIC_GRAPH_PROPOSALS.md),
[health warnings](INFERENCE_HEALTH.md), [roadmap](../ROADMAP.md).
