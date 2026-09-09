# Pilot-fitted Gaussian mixture proposals

## Overview

`GaussianMixtureProposal.fit` learns a frozen, full-covariance Gaussian mixture
from discarded pilot traces. Use it when a single fitted Gaussian spreads draws
between separated posterior regions, or when you have evidence that several local
Gaussian approximations fit better. It is an opt-in companion to `VectorImportance`,
not a replacement for graph importance sampling or a general clustering service.

The model must first explore the relevant regions. Adding components cannot find a
mode absent from the pilot, certify convergence, or automatically target a rare event.
You choose the component count and a broad defensive component. Production weights
use the full frozen mixture density, so imperfect fitted component proportions do
not redefine the posterior; they can still damage sampling efficiency.

## Literature and implementation choice

[Cappé et al., 2008](https://arxiv.org/abs/0710.4242) motivate mixture-based
importance proposals with updated weights and component parameters. Figaro's
implementation is deliberately simpler: unweighted pilot-only fitting followed by
independent production, not their adaptive M-PMC algorithm or its convergence claim.
[Gaussian-mixture EM documentation](https://scikit-learn.org/stable/modules/generated/sklearn.mixture.GaussianMixture.html)
provides a maintained reference for explicit component counts, full covariance,
regularization, iteration caps and convergence reporting. No external implementation
is copied or invoked at runtime; no new runtime dependency or license is introduced.
The numerical oracle uses independently expressed NumPy inverse-matrix calculations.

## Quick start in three steps

1. Obtain post-warm-up pilot traces using dispersed chains; inspect their diagnostics.
   The [complete executable example](../FigaroExamples/src/main/scala/com/cra/figaro/example/documentation/MixtureProposalExample.scala)
   includes this step for a two-mode target.

2. Fit explicitly and inspect the result:

   ```scala
   import com.cra.figaro.algorithm.sampling.GaussianMixtureProposal as M
   val fit = M.fit(pilot.chains.map(_.result.samples),
     M.Config(components=2, diagonalRidge=Vector(1e-4))) // One-dimensional example.
   println((fit.status, fit.iterations, fit.densityEvaluations))
   ```

3. If fitted, mix with a covering broad law and draw fresh production samples:

   ```scala
   import com.cra.figaro.algorithm.sampling.VectorImportance as V
   fit.proposal.foreach { q =>
     val frozen = V.Mixture(Vector(.1,.9), Vector(broad,q))
     val result = V.run(V.Config(seed=43), frozen, logTarget, _.head)
     println((result.health.diagnostics.mean, result.health.status))
   }
   ```

Here `pilot`, `broad` and `logTarget` come from your explicit-vector model. Choose a
production seed/stream separate from training. The fitter itself uses no RNG.
`None` means no production proposal was returned: inspect `fit.message` rather than
silently substituting another sampler. The existing [vector proposal guide](VECTOR_IMPORTANCE.md)
covers density/support obligations, production budgets and health semantics.

## Three common patterns

### Separated modes

Use two or more components when pilot traces cover separated regions. In the example,
four chains start near both modes, fit two components, then estimate a mode event:

```scala
val result = V.run(V.Config(draws=10000,maxEvaluations=10000,seed=43),
  frozen, logTarget, x => if(x.head>0) 1.0 else 0.0)
```

A single Gaussian may waste many draws in the gap. The mixture retains local scale
around each mode. Chain occupancy is not proof of true mode probability; importance
correction uses the target and full proposal densities for every production draw.

### Explicit regularization and scaling

For two coordinates with different units, choose ridge **variances** in each
coordinate's squared units, not one unexplained universal constant:

```scala
val fit = M.fit(traces, M.Config(components=3,
  diagonalRidge=Vector(1e-4,1e-2), covarianceInflation=1.5))
```

Ridge is added during covariance updates. Inflation multiplies the whole fitted
covariance, including its ridge, only after convergence. Empty ridge means none;
singular fits refuse instead of inventing a regularization level. Ridge does not
make constant pilot coordinates evidence of exploration.

### Fit once, reuse without adapting production

```scala
fit.proposal.foreach { q =>
  val frozen = V.Mixture(Vector(.1,.9),Vector(broad,q))
  val mean = V.run(V.Config(seed=43),frozen,logTarget,_.head)
  val event = V.run(V.Config(seed=44),frozen,logTarget,
    x => if(x.head>threshold) 1.0 else 0.0)
}
```

This shares training, not production samples. To assess several queries on the same
production sample, reuse aligned raw draws/weights with `InferenceHealth`; those
estimates are dependent. Never concatenate discarded pilot traces with production.

## API reference

All members belong to `com.cra.figaro.algorithm.sampling.GaussianMixtureProposal`.

`Config` constructor parameters:

| Parameter / default | Contract |
| --- | --- |
| `components=2` | Explicit integer 1–8; no automatic component-count selection |
| `maxIterations=100` | Complete E-step density sweeps, 2–1,000 |
| `tolerance=1e-6` | Positive finite relative tolerance: change in average training log density ≤ tolerance × (1 + absolute previous value) |
| `minComponentDraws=20` | Finite responsibility mass at least two per component; not an independent effective sample size |
| `maxDensityEvaluations=2000000L` | Positive cap on actual component log-density calls during E steps, not pilot/production target calls or all fitting operations |
| `maxStoredValues=1000000L` | 1–10,000,000 scalar slots; requires number of pilot points × (dimension + components) within cap; not a heap/workspace guarantee |
| `diagonalRidge=Vector.empty` | No ridge, or one finite nonnegative variance per coordinate |
| `covarianceInflation=1.5` | Positive finite multiplier on converged covariance, not standard deviation |

`fit(chains, config=Config()): Result` takes immutable
`Vector[Vector[Vector[Double]]]`: at least four chains with five retained draws
each; unequal lengths are allowed. Points must be finite with a common dimension
1–32. Total pilot points must support the requested component responsibility masses.
Invalid config, shape or storage throws `IllegalArgumentException`; entry/during-work
interruption throws `InterruptedException` and preserves the interrupt flag.

The implementation pools unweighted pilot draws, computes a global covariance,
uses deterministic standardized farthest-point initialization, then regularized EM.
Ordering is reproducible for identical ordered input; changing trace order can alter
initialization/tie breaking and the local solution. No global RNG or graph state is
used. Invocation-local mutable work arrays are not shared with the returned proposal.

`Result` fields:

- `status: Status`: outcome below.
- `proposal: Option[VectorImportance.Mixture]`: frozen normalized inflated Gaussian
  mixture only for `Fitted`. It does **not** include your broad defensive component.
- `iterations: Int`: completed density sweeps.
- `densityEvaluations: Long`: actual component log-density calls, within the cap.
- `trainingLogDensity: Vector[Double]`: mean uninflated mixture log density at each
  completed sweep. This is training fit, not evidence or held-out posterior accuracy.
- `config: Config`: actual fitting policy; `message: String`: explanation.

`Status` values: `Fitted`, `InsufficientPilot`, `DegeneratePilot`,
`InsufficientComponent`, `NumericalFailure`, `IterationLimit`, `EvaluationLimit`.
All refusals return `proposal=None`. There is no partial-fit fallback, component
pruning, randomized retry or automatic ridge increase. Config/result fields are
immutable; compiler-derived accessors and factories appear in the [API reference](api/README.md).

## Gotchas

- Numerical EM convergence is not global optimality, pilot convergence, or coverage
  of all posterior modes. Regularization means this is not unregularized maximum
  likelihood; monotonic raw likelihood is not promised when ridge is nonzero.
- The initial global Gaussian must be numerically supported. Fitting refuses a
  constant coordinate or singular covariance without explicit regularization.
- `minComponentDraws` checks responsibility mass, not serial independence of pilot
  draws. Highly correlated/stuck chains may fit confidently and still be inadequate.
- Tiny or overlapping components may need more training, a different explicit count,
  scaling, or a different proposal family. The fitter never silently drops them.
- A Gaussian mixture still has Gaussian tails. Add a suitable heavy-tail defensive
  law when needed. A finite box alone does not cover an unbounded target.
- Pilot calls, EM density calls, and production target calls are distinct. Also
  budget covariance work, roughly proportional to points × components × dimension²
  per update. Bounds on one counter are not a wall-clock or whole-heap bound.
- The [calibration limitations](IMPORTANCE_CALIBRATION.md) still apply. More
  components do not authorize automatic precision stopping or prove rare-event coverage.

## Paired acceptance evidence

[Complete results](mixture-proposal-results.csv): 100 fresh seeds for each of three
targets, two production budgets, single versus multi-component fits from the same
pilot; 1,200 rows. Six Quantile pilot chains cost 12,000 target calls per fit trial.
Component count is predeclared (two/two/three), ridge is 1e-4, inflation 1.5, and a
10% broad Gaussian remains in both proposals. Production uses the same seed across
methods, and budgets share prefixes. All fits succeeded; no failed trials were omitted.

| Target | Accurate at 2k draws: single / multi (of 100) | Accurate at 10k: single / multi | Median weight ESS at 10k: single / multi |
| --- | --- | --- | --- |
| Balanced two modes | 56 / 96 | 91 / 100 | 962 / 8,408 |
| Unbalanced two modes | 72 / 100 | 97 / 100 | 1,094 / 8,450 |
| Three modes | 78 / 99 | 99 / 100 | 1,371 / 8,481 |

Accuracy means absolute event-probability error ≤ 0.025; references include the
Gaussian tails at the event threshold, not rounded mixture weights. Median EM
component-density calls were 14,485 / 11,480 / 54,186 respectively, in addition to
pilot and production costs. Single fitting uses moments without component-density
calls, but is not cost-free. These are roughly 6–9× weight-ESS improvements on these
fixtures, **not** measured wall-clock speedups or general high-dimensional guarantees.
Fitting-data oracles also exercise full two-dimensional covariance; broad nonlinear,
high-dimensional and rare-event fitting remain separate evidence needs.

Local modern.12 candidate checks passed: 424 modernization tests (including seven
fitter tests), 73 evidence-tool tests, the independent NumPy oracle test, and seven
artifact-checker tests. The public example and separate published-JAR consumer
passed; all four archives passed legal/runtime/Java-17 checks. The generated
handbook contains 11,988 method entries; local links pass. Four pre-existing
Scaladoc warnings remain. The verified thin-JAR SHA-256 is
`0364fcef276aacd44eb6cbde826fbc529568dd4b1e01bd42e833d7035620cad8`.
Remote CI and main integration remain separate gates from these local results.

```sh
sbt "figaro / Test / testOnly com.cra.figaro.test.modernization.GaussianMixtureProposalTest"
sbt "examples / Compile / runMain com.cra.figaro.example.documentation.MixtureProposalExample"
sbt "figaro / Test / runMain com.cra.figaro.test.modernization.MixtureProposalStudy full"
python -B tools/summarize_mixture_proposals.py docs/mixture-proposal-results.csv
python -B tools/mixture_fit_reference.py
```

## Related and next work

Use with [VectorImportance](VECTOR_IMPORTANCE.md),
[multi-chain vector pilots](MULTI_CHAIN_VECTOR_SAMPLING.md),
[Gaussian distribution constructions](DISTRIBUTION_CONSTRUCTIONS.md) and
[inference health](INFERENCE_HEALTH.md). The subsequent [graph bridge](GRAPH_PROPOSALS.md)
connects frozen proposals to an explicitly constructed owned graph, without automatic
coordinate discovery, graph rewriting, or new stopping claims. Its executable example
also shows how to assemble unweighted graph MCMC traces for this fitter.
