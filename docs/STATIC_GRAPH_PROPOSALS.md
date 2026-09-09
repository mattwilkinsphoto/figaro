# Frozen proposals in restricted static graphs

## Overview

`StaticGraphImportance.runWithProposal` applies a frozen joint proposal to selected
independent Normal roots, then evaluates the supported hierarchical graph downstream.
It removes mutable Element-graph overhead while preserving full joint proposal
corrections. This is an explicit API, not compilation of arbitrary existing graphs.

Enable it when your model fits the [static vocabulary](STATIC_GRAPH_EXECUTION.md)
and a supplied or separately fitted root proposal improves effective sampling.
The simpler `run` remains preferable when prior sampling is adequate.

## Quick start (three steps)

```scala
import com.cra.figaro.algorithm.sampling.{StaticGraphImportance as S,VectorImportance as V,RareEventImportance as R}
import com.cra.figaro.library.atomic.continuous.{MultivariateGaussianDistribution as G}
// 1. X~N(0,1), Y|X~N(X,1); static Normal takes STANDARD DEVIATION.
val model=S.compile(Vector(S.Node.Constant(0),S.Node.Constant(1),S.Node.Normal(0,1),S.Node.Normal(2,1)))
// 2. Supply a frozen defensive proposal for X given the intended Y=3 query.
val base=V.Gaussian(G(Vector(0.0),Vector(Vector(1.0))))
val q=R.defensive(base,V.Gaussian(G(Vector(1.5),Vector(Vector(.5))))).proposal
// 3. Observe downstream Y and inspect the self-normalized posterior diagnostic.
val result=S.runWithProposal(model,Vector(2),q,Vector(2),Map(3 -> 3.0))
println(result.health.head.diagnostics.mean) // Approximately 1.5.
```

## API reference

`runWithProposal(model,roots,proposal,queries,observations=Map.empty,config=S.Config())`
returns the same detached `S.Result` as `run`, plus
`proposalDensityEvaluations` (one complete proposal call per draw; not internal
component calls). `roots` is a nonempty ordered vector of distinct node IDs;
proposal coordinate j replaces roots(j). Roots must be unobserved Normal nodes
whose means and standard deviations are constants. Unselected stochastic nodes
still sample conditionally from their declared priors.

Only immutable `V.Gaussian`, elliptical `V.StudentT` and their nested `V.Mixture`
combinations are accepted (at most four nesting levels and 256 total nodes in the
proposal tree). Custom/conditional callbacks and bounded-only proposals are rejected
before workers launch. This deliberately preserves full support and avoids sharing
unknown mutable callback state. It does not imply general callback thread safety.

The weight is the sum of selected-root Normal log priors, minus the **complete joint**
proposal log density, plus downstream observation log likelihoods. Selected roots
are not sampled again. Prior root evaluations are included in node work; setup and
proposal kernels are additional work, not hidden inside a throughput claim.

Additional `S.Node` constructors:

| Node | Parameters and result |
| --- | --- |
| `Uniform(lower,upper)` | Earlier real node IDs, positive finite width; real draw; observations outside bounds get zero weight |
| `Exponential(rate)` | Earlier positive finite real rate; nonnegative real draw; negative observations get zero weight |
| `Log(value)` | Earlier strictly positive real node; natural log; invalid input fails the run |
| `Abs(value)` | Earlier real node; absolute value |
| `GreaterThan(left,right)` | Earlier real nodes; Boolean result, strict comparison (equality is false) |

Existing compile/run/config contracts and cancellation behavior remain in the base
guide. Deterministic observations are still unsupported. Boolean query columns are 0/1.

## Three common patterns

**Prior versus proposal:**
```scala
val baseline=S.run(model,Vector(2),Map(3 -> 3.0))
println((baseline.health.head.diagnostics.ess,result.health.head.diagnostics.ess))
```
Compare accuracy and total runtime too; ESS alone is not a performance guarantee.

**Worker-count replay:**
```scala
val c=S.Config(draws=20000,batches=16,seed=81,parallelism=1)
val serial=S.runWithProposal(model,Vector(2),q,Vector(2),Map(3 -> 3.0),c)
val parallel=S.runWithProposal(model,Vector(2),q,Vector(2),Map(3 -> 3.0),c.copy(parallelism=4))
assert(serial.values==parallel.values && serial.logWeights==parallel.logWeights)
```
Keep logical batch count fixed. Small graphs can be slower with more workers.

**Reuse a fitted event mixture:**
```scala
import com.cra.figaro.algorithm.sampling.GaussianMixtureProposal as M
val fit=R.fitMixture(base,x => math.abs(x.head),4,
  R.FitConfig(seed=94001),M.Config(components=2,diagonalRidge=Vector(.25)))
val eventModel=S.compile(Vector(S.Node.Constant(0),S.Node.Constant(1),
  S.Node.Normal(0,1),S.Node.Abs(2),S.Node.Constant(4),S.Node.GreaterThan(3,4)))
fit.proposal.foreach(f => println(S.runWithProposal(eventModel,Vector(2),f.proposal,Vector(5)).health))
```
Fitting a rare event does not generally fit a downstream posterior. Use the proposal
for the query it was trained for, and keep training outside production. Normalized
event estimation in `R.run` differs from self-normalized graph posterior summaries.

## Evidence, limitations and related

[Complete cost evidence](CAPABILITY_EXPANSION_ACCEPTANCE.md) compares supplied proposals
in owned graphs and static 1/4-worker graphs, with the static prior control retained.
No automatic branch pruning, mutable graph sharing, adaptive production, arbitrary
callbacks or universal precision stopping is added. Runtime failures cancel and join
workers; no partial result is returned. Definitions/evidence are immutable and each
invocation owns its state arrays and streams.

The architecture follows the separation of static model structure and execution
state discussed in [Gen's static modeling language](https://www.gen.dev/docs/stable/ref/modeling/sml/).
No Gen implementation is copied. Related: [owned graph proposals](GRAPH_PROPOSALS.md),
[event-weighted fitting](WEIGHTED_RARE_EVENT_MIXTURES.md), [RNG streams](RNG_STREAMS.md).
