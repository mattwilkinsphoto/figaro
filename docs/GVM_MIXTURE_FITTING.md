# Fitting GVM mixtures

## Overview

Figaro 6.1 adds `GaussVonMisesMixtureFit`: a bounded public fitter for a real scalar
and one circular angle. It estimates a **density representation** from supplied IID
training points. It is not a graph learner, filtering update, report-fusion operation,
automatic component selector, or global optimizer. Fixed GVM kernels still support
multiple linear coordinates; this first parameter-fitting API deliberately does not.

Unlike the modern.23 test-only regression, accepted fits use full periodic mixture
likelihoods and soft component responsibilities. Unwrapped regression supplies one
initial guess only; it is never the likelihood being maximized.

## Quick start (three steps)

```scala
import com.cra.figaro.library.atomic.continuous.*
import com.cra.figaro.util.SamplingRandom

// 1. Supply training data (replace this synthetic example with independent observations).
val truth = GaussVonMisesDistribution(Vector(0), Vector(Vector(1.0)),
  3.0, Vector(.4), Vector(Vector(.7)), 8)
val rng = SamplingRandom.scalaRandom(42)
val training = Vector.fill(500)(truth.sample(rng))

// 2. Fit an explicitly chosen component count.
val fit = GaussVonMisesMixtureFit.fit(training,
  GaussVonMisesMixtureFit.Config(components=1))

// 3. Inspect termination, then validate the frozen candidate on HELD-OUT data.
println(fit.attempts)
fit.distribution.foreach(law => println(law.logDensity(training.head)))
```

`distribution.nonEmpty` means a finite candidate survived the safeguards, NOT that
every optimizer converged. Check `selectedAttempt` and its status. A budget-limited
candidate can be useful for research; decide explicitly whether your deployment
allows it. Do not silently treat it as a converged fit.

## API reference

| API | Parameters | Returns / example |
| --- | --- | --- |
| `fit(data, config)` | Vector of 30..100000 `LinearAngular` values, exactly one linear coordinate per point, absolute linear value <=1e50; enough data for the component floor | `Result`; `fit(points, Config(components=2))` |
| `Config(...)` | `components=1` (1..16), `restarts=3` (1..8), `maxIterations=40` (1..200), `maxAngularEvaluations=200` (8..2000 per component/start), `relativeTolerance=1e-7` (0..0.01, excluding zero) | Validated immutable work policy |
| Remaining `Config` fields | `varianceFloor=1e-6` (1e-12..1e12 physical variance), `maxConcentration=100` (0.1..500), `minComponentEffectiveSamples=10` (>=3), `seed=42` | Explicit constrained-fit/initialization policy |
| `Result` | `distribution`, `selectedAttempt`, `attempts`, `config` | Best surviving law by mean training log likelihood; no candidate if every restart is refused |
| `Attempt` | `status`, `logLikelihoodTrace`, `angularEvaluations`, `message` | All accepted mean training scores, initialization included, and actual circular objective-call count |

Statuses are `Converged`, `IterationLimit`, `AngularBudgetExhausted`,
`DegenerateComponent`, `Stalled`, and `NumericallyUnresolved`. Convergence is a
local training-likelihood tolerance, not a statistical consistency or adequacy test.
Malformed inputs throw `IllegalArgumentException`; cancellation propagates without
clearing the thread flag. Repeated points are allowed: the declared variance floor
and concentration cap then control an otherwise singular fit.

## Three common patterns

```scala
// 1. Fit two alternatives and inspect their responsibilities.
val two = GaussVonMisesMixtureFit.fit(training,
  GaussVonMisesMixtureFit.Config(components=2))
two.distribution.foreach(law => println(law.responsibilities(training.head)))

// 2. Freeze a fitted law for independent inference/diagnostics.
val candidate = fit.distribution.getOrElse(sys.error("No usable fit"))
val proposal = candidate.asProposal() // one normalized angular chart
println(proposal.logDensity(Vector(0.0, 3.0)))

// 3. Compare full laws, not componentwise averages; use a separate evaluation RNG.
val reference = GaussVonMisesMixtureDistribution(Vector(1), Vector(truth))
println(GaussVonMisesMixtureInformation.kl(reference, candidate))
println(GaussVonMisesMixtureMutualInformation.compute(candidate))
```

## Method and gotchas

EM responsibilities use the complete joint log-sum density. Linear weighted moments
maximize the Gaussian part subject to the variance floor. The previous quadratic
phase is re-expressed in the new standardized coordinate before circular optimization.
The angular intercept is the argument of the weighted residual resultant; Powell
search optimizes the slope/curvature, and a bounded Bessel-ratio inversion estimates
concentration. A decreasing full-data update is refused. Every restart is recorded.

- Initialization uses linear ordering, angular ordering, then seeded randomized
  balanced partitions. Results are local and sensitive to initialization; no missing-mode guarantee.
- Each component needs both sufficient total responsibility and responsibility ESS.
  Components are not silently pruned, reseeded or merged.
- Canonical slope/curvature candidates are limited to magnitude 1000. Variance and
  concentration constraints are genuine modeling constraints, not invisible numerical fixes.
- The returned likelihood can improve while held-out quality worsens. Use independent
  train/validation/test partitions for selecting component counts; do not select on test scores.
- Determinism is for the same data ordering, config, implementation and RNG version;
  it is not a promise across future algorithms or floating-point platforms.
- Per-fit state is local; do not share mutable RNGs/Elements. Raw metric units still matter.
- Costs grow with points, components, restarts, EM iterations and circular objective
  evaluations. Work caps and interruption are not a wall-clock latency guarantee.

## Related and research basis

[Fixed GVM mixtures](GVM_MIXTURES.md), [partition MI](GVM_MIXTURE_MI.md),
[6.1 acceptance](RELEASE_6_1.md), and [independent GMM comparison](GVM_MIXTURE_RESEARCH_PLAN.md).

[Horwood and Poore (2014)](https://epubs.siam.org/doi/10.1137/130917296) supplies the
underlying joint GVM law. [Model-based clustering using a new mixture of circular
regressions (2026)](https://arxiv.org/abs/2601.05345) is relevant recent EM/circular-
regression literature, not the identical joint law or copied software. The periodic
resultant objective and coordinate-change algebra here are an independent specialization
to Figaro's quadratic-phase GVM. No new runtime dependency or third-party source was added.
