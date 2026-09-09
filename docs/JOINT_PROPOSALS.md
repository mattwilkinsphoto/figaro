# Joint-block and query-aware proposals

## Overview

Use an explicit joint proposal when improving a single root leaves poorly sampled
latent variables elsewhere in the graph. `VectorImportance.Conditional` assembles
`q(x,z)=q(x)q(z|x)` without discarding dependence. It works with vector importance
and the owned graph bridge; it does not rewrite an existing graph or fit a proposal.

## Quick start

1. Specify the original joint prior, including conditional dependence.
2. Supply a frozen joint proposal, optionally mixed with the prior for defensive coverage.
3. Build the remaining graph around the complete proposed block.

```scala
import com.cra.figaro.algorithm.sampling.{VectorImportance as V,GraphProposalImportance as P}
import com.cra.figaro.library.atomic.continuous.{MultivariateGaussianDistribution as G,Normal}
import com.cra.figaro.language.*
def g(m: Double,v: Double)=V.Gaussian(G(Vector(m),Vector(Vector(v))))
val original=V.Conditional(g(0,1),1,x => g(x.head,.01))
val informed=V.Conditional(g(1/1.02,.04),1,x => g((x.head+1)/2,.005))
val proposal=V.Mixture(Vector(.1,.9),Vector(original,informed))
val result=P.run(P.Config(),proposal,original.logDensity) { (u,root) =>
  val latent=root.map(_(1))(using "",u)
  Normal(latent,.01)(using "",u).observe(1.0)
  root.map(_.head)(using "",u)
}
// Exact posterior mean of theta is 1/1.02.
```

## API reference

`Conditional(prefix, tailDimension, conditional)` returns an immutable proposal
description. `prefix` is a normalized proposal; `tailDimension` is positive; total
dimension must be at most 128. `conditional: Vector[Double] => Proposal` must be a
pure function returning a normalized tail proposal of exactly that dimension.
Coordinates are concatenated in prefix-then-tail order.

- `dimension: Int`: total coordinate count. Example: `original.dimension == 2`.
- `sample(rng): Vector[Double]`: samples prefix then conditional tail with the same
  exclusively owned Scala RNG. Example: `original.sample(SamplingRandom.scalaRandom(4))`.
- `logDensity(x): Double`: full joint log density for a finite matching vector.
  Example: `original.logDensity(Vector(.2,.3))`. Off prefix support it returns
  negative infinity without invoking a possibly undefined conditional.

Null callbacks, dimensions changing at evaluation, nonfinite draws, invalid
densities and log-sum overflow fail explicitly. Interruption is checked around
callbacks, but a callback that never returns cannot be preempted by these checks.

## Three patterns: what changes and why

### 1. Root-only versus joint hierarchical sampling

Root-only sampling still generates `z ~ N(theta,.01)` before weighting `y|z`.
The quick start instead proposes **both** theta and z near the evidence. Do not
also create a second stochastic `Normal(theta,.01)` for this same z: its density
already belongs to the joint original prior. Keep `Normal(z,.01).observe(1)` once.

### 2. Nonlinear conditional dependence

For `z|theta ~ N(theta²,.01)`, a product of marginal proposals loses the curved
relationship. Keep it explicit, and use a multimodal prefix if the evidence makes
both signs of theta plausible:

```scala
val nonlinearPrior=V.Conditional(g(0,1),1,x => g(x.head*x.head,.01))
val signs=V.Mixture(Vector(.5,.5),Vector(g(-1,.02),g(1,.02)))
val nonlinearProposal=V.Conditional(signs,1,x => g((x.head*x.head+1)/2,.005))
val defensive=V.Mixture(Vector(.1,.9),Vector(nonlinearPrior,nonlinearProposal))
```

The graph must then observe the second coordinate, not generate it again.
These supplied controls are not automatically fitted posteriors.

### 3. Ordinary mean versus rare-event query

For `theta ~ N(0,1)`, ordinary prior draws rarely exceed four. A proposal focused on
that event can be useful even when its **global weight ESS is worse**:

```scala
val prior=g(0,1)
val eventProposal=V.Mixture(Vector(.1,.9),Vector(prior,g(4,1)))
val event=P.run(P.Config(),eventProposal,prior.logDensity) { (u,root) =>
  root.map(x => if(x.head>4) 1.0 else 0.0)(using "",u)
}
// Compare the weighted event estimate to about 0.00003167124, not an unweighted count.
```

This optimizes one query, not every posterior quantity. A zero observed event
count is not evidence that the event is impossible. Proposal selection must use
discarded training or prior knowledge, not repeated inspection of production data.

## Gotchas and related modules

The original joint prior must use matching coordinates and Jacobians. Conditional
callbacks are evaluated during sampling and density evaluation, so mutable state
or random fitting inside them breaks the correction. Do not interpret a defensive
component as guaranteed mode discovery or calibrated stopping. [Graph ownership
rules](GRAPH_PROPOSALS.md), [vector proposal contracts](VECTOR_IMPORTANCE.md),
[mixture fitting](MIXTURE_PROPOSALS.md), and [health warnings](INFERENCE_HEALTH.md)
continue to apply. Full-covariance joint Gaussians are preferable when available;
this compositional helper is not a claim to beat their cached factorization.

Reproduce the 100-seed hierarchical/nonlinear/rare-event controls with:

```sh
sbt "figaro / Test / runMain com.cra.figaro.test.modernization.JointProposalStudy full"
```

The study includes prior, root-focused and joint/event-focused controls. It charges
no pilot because these proposals are supplied analytically, not learned. Timing
includes graph construction and diagnostics; it is not a portable speed guarantee.
