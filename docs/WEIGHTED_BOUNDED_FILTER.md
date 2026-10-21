# Likelihood-weighted bounded filtering

Published in **7.2.0**, not in 7.1.0. See [installation](MAVEN_CENTRAL.md).
[Research provenance](RESEARCH_REFERENCES.md#inf-09).

## Overview

`WeightedBoundedParticleFilter` is an opt-in bootstrap particle filter for immutable
states and explicit noisy-observation likelihoods. It allocates extra candidates to
a difficult observation, up to a hard cap, using a separate score policy fixed before
that step. It does not change ordinary `ParticleFilter`, retry a failed graph, tune
proposals, or promise a particular posterior precision.

This guide describes the original bootstrap/manual-policy path. The subsequent
opt-in [calibration and guided-proposal extension](CALIBRATED_GUIDED_FILTER.md)
adds independent pilot score/work selection and explicit observation-informed
proposal corrections. It does not change the defaults described here.

Ordinary likelihood-weighted filtering already exists in Figaro. The addition here
is bounded adaptive work with corrected evidence estimation and ancestor selection.
Use it when observation difficulty varies and a fixed candidate count is inconvenient.
Keep standard fixed-budget filtering when predictable candidate counts matter more,
or when the bootstrap prior/transition proposal is already adequate.

## Quick start: three steps

1. Define prior and transition draws of detached immutable states.
2. Declare the work floor/cap, score target, and an independent score scale.
3. Start, advance with explicit likelihood policies, inspect diagnostics, then close.

```scala
import com.cra.figaro.algorithm.filtering.{BoundedParticleFilter as B, WeightedBoundedParticleFilter as W}
import com.cra.figaro.util.random

val model = B.Model(() => random.nextGaussian(),
  (x: Double) => .8*x + math.sqrt(.3)*random.nextGaussian())
val filter = W(model, W.Config(128, 768, scoreTarget = 100, seed = 42))
def evidence(y: Double) = W.Evidence[Double](
  x => -.5*(math.log(2*math.Pi*.5) + (y-x)*(y-x)/.5),
  W.cappedLikelihood(-.5*math.log(2*math.Pi*.5)))
try {
  filter.start(evidence(.2))
  val result = filter.advance(evidence(-.3))
  println(result.expectation(identity))
  println(result.report.health)
  println(result.report.warnings)
} finally filter.close()
```

The scale above is the known Gaussian likelihood peak, not a maximum estimated
from the production candidates. The initial model draws the state at the first
observation; transitions are called only on `advance`.

## Three common patterns: what changes and why

### 1. Fixed effort versus adaptive effort

```scala
val fixed = W.Config(384, 384, scoreTarget = 100, seed = 1)
val adaptive = W.Config(128, 768, scoreTarget = 100, seed = 1)
```

The fixed configuration always draws 384 candidates, even if its score target is
unmet. The adaptive configuration starts with 128 and may extend to 768. Both use
the same explicit-state bootstrap model and unmodified likelihoods. This is a
controlled comparison within the new API, not a claim to reproduce every legacy
graph filter behavior. Compare total work and estimation error across all seeds;
lower work alone is not better accuracy or a speedup claim.

### 2. Hard rejection versus graded noisy evidence

```scala
val hard: Double => Boolean = x => math.abs(x) < 1  // BoundedParticleFilter
val noisy = evidence(.2)                           // WeightedBoundedParticleFilter
```

The Boolean filter treats accepted states equally. The weighted filter keeps their
relative likelihoods. Do not threshold a noisy likelihood merely to use the Boolean
budget adviser: that changes the model. A thousand positive weights can still have
ESS near one, so inspect pre-resampling weight concentration and ancestry.

### 3. A difficult observation exhausts the cap

```scala
val difficult = W.Evidence[Double](x => -10000 - x*x,
  W.cappedLikelihood(-9990))
```

Absolute likelihoods may underflow in ordinary arithmetic, but this log-likelihood
remains usable. The score may be small, exhausting the cap. A viable population is
returned with warnings; it is not discarded just because the score target was missed.
If every retained log likelihood is `-Infinity`, `W.NoViableParticles` terminates the
filter and carries diagnostics and a zero evidence estimate. Never retain only
successful retries when estimating evidence.

## Public API reference

| API | Parameters, return and example |
| --- | --- |
| `Config` | `minCandidates=1000`, `maxCandidates=10000` (2 <= min <= max <= 1,000,000), finite `scoreTarget=100` in (0,max], `seed=1`, RNG `algorithm`, `health`; `W.Config(128,768,100)` |
| `Evidence[S]` | `logLikelihood: S => Double`, `score: Double => Double`; immutable callback bundle; `W.Evidence[Double](x => -x*x, W.cappedLikelihood(0))` |
| `cappedLikelihood` | Finite independently chosen `logScale`; returns `Double => Double`, `min(1,exp(logWeight-logScale))`; `W.cappedLikelihood(0)` |
| `apply` | `B.Model[S]`, optional `Config`; unstarted owned filter; `W(model, adaptive)` |
| `start` | `Evidence[S]`; initial `Update[S]`, once only; `filter.start(evidence(.2))` |
| `advance` | `Evidence[S]`; next `Update[S]` using weighted ancestors; `filter.advance(evidence(-.3))` |
| `current` | No arguments; latest valid `Update`; fails before start, after terminal failure/close |
| `config` | No arguments; immutable configuration supplied at construction; `filter.config.maxCandidates` |
| `lastReport` | No arguments; optional last completed `StepReport`, including collapse; absent after callback/numerical failure |
| `close` | No arguments; terminal, idempotent owner-thread cleanup |
| `Update[S]` | `particles`, normalized `weights` in matching order, cumulative `logEvidence`, `report`; may include zero-weight candidates |
| `Update.expectation` | `f: S => Double`; finite weighted mean; `result.expectation(x => x*x)` |
| `currentLogWeights` | No arguments; immutable normalized log weights aligned with `current.particles`; `filter.currentLogWeights.count(_.isFinite)`; throws when there is no valid current posterior |
| `StepReport` | `attempts` includes all draws, `retainedCandidates` is estimator denominator, `totalScore` includes stopping draw, `reason`, `targetMet`, `distinctParents`, `health` |
| `StepReport.warnings` | No arguments; cap and one-parent warnings; not whole-history diversity or precision certification |
| `StopReason` | `InitialBatch`, `ScoreTarget`, `BudgetExhausted` |
| `NoViableParticles` | Terminal exception with `report`, `logEvidence=-Infinity`; no posterior available |

`B.Model` and fixed evidence-free `B.elementModel` may be reused. Their immutable
state, graph-ownership and RNG contracts still apply. Data-record constructors do
not certify user-invented results. Use the filter to obtain computed updates.

## Accounting and gotchas

The floor takes precedence: if sampling ends at the initial batch, keep every draw.
If extra sampling reaches the score target, exclude the final candidate from both
the likelihood average and the next ancestor pool, including success exactly at
the cap. If the cap is reached below target, keep every draw. Independent ancestors
are sampled with probabilities proportional to retained likelihood weights. No
additional fixed-size resampling layer is inserted.

Evidence uses shifted exponentiation and log-sum-exp. The cumulative likelihood
estimator follows the paper's construction under its assumptions; log evidence and
normalized posterior means are not claimed unbiased. Finite tests do not prove
correctness for arbitrary callbacks. No particle-MCMC driver is provided.

- Scores must be finite in [0,1]; the target is a sum of scores, not ESS or a survivor
  count. The score policy must not adapt to this step's previous draws. Optional
  stopping on observed ESS is not a supported substitute.
- Raw log likelihoods may be finite (including positive density logs) or `-Infinity`.
  NaN and positive infinity fail explicitly. Normalized log weights are retained:
  ordinary `Update.weights` may round a tiny positive value to zero without deleting
  its ancestor. `currentLogWeights` distinguishes this from actual `-Infinity`.
  Overflow of the log range, corrected log weight or evidence still fails explicitly.
- A score can numerically underflow to zero without changing the inference weight.
  That affects work allocation and may force the cap. Near-threshold floating-point
  sums are not exact real arithmetic.
- On the bootstrap path, callbacks must implement the true prior/transition laws.
  Custom draws require the explicit [guided density-ratio API](CALIBRATED_GUIDED_FILTER.md),
  not a silent change to these callbacks. Likelihood constants matter for evidence.
- Use `util.random` for owned seeded replay. Mutable states, external RNGs, shared
  graphs, reentrant calls and cross-thread use are unsupported. Invalid callbacks,
  interruption and numerical failures invalidate the posterior; interruption is not
  a completed capped estimate. Caps count candidates, not runtime inside callbacks.
- Extra current-step draws do not recover extinct histories or certify missing modes.
  The manual path performs no score fitting or automatic fallback. The separate
  [split-pilot extension](CALIBRATED_GUIDED_FILTER.md) calibrates bounded continuous
  scores; the Boolean adviser cannot be reinterpreted as that extension.

## Validation and related modules

### Numerical representation and reproducibility

The numerical repair uses an independent log-mass binary-tree draw for each
ancestor, rather than cumulative ordinary probabilities. Full auxiliary-mixture
corrections also use retained log weights. The tree costs O(N) to prepare and
O(log N) per draw; each decision tests the smaller subtree using a log-uniform
with a random binary exponent and independent mantissa. This avoids both collapsed
CDF intervals and the fixed small-probability cutoff of a single `nextDouble()`.
It is not exact-real arithmetic or an unlimited-support guarantee for a finite PRNG.
See [research provenance and adaptation limits](RESEARCH_REFERENCES.md#inf-13).

No weight floor, selective retry or change to the score/stopping rule is introduced.
A finite tiny log weight can become important under later auxiliary guidance;
it therefore must not be replaced internally by the rounded ordinary output.
For example:

```scala
val result = filter.current
val roundedAway = filter.currentLogWeights.zip(result.weights)
  .count { case (logWeight, weight) => logWeight.isFinite && weight == 0.0 }
// roundedAway > 0 is a representation diagnostic, not impossible evidence.
```

`Update.expectation` and ordinary-weight ESS summaries remain finite-precision
summaries of the rounded weights. They do not offer log-domain rare-event moment
accuracy: an observable that amplifies a vanishing weight can require a separate
log-domain calculation. Inspect the log weights rather than interpreting ordinary
zeros as support exclusions. There is no certified global floating-point error
bound; analytic controls and the unchanged continuous benchmark provide numerical
evidence within their stated scope. Statistical mode depletion remains possible.

The new sampler consumes different RNG draws. Same-version/configuration/seed
replay is tested; trajectories need not match the pre-repair implementation.
Published 7.1.0's separate indicator/legacy filters are not changed by this repair.

The tests exhaustively enumerate small two-step weighted models, including exact-cap
and fixed-floor boundaries, and check lifecycle, weighted ancestry and numerical
limits. The synthetic study uses fixed and adaptive settings with 512 predeclared
seeds each; all outcomes enter evidence and unnormalized-moment calibration. An
independent rational joint-Gaussian conditioning calculation checks the Kalman
reference. This is validation, not a matched-accuracy performance benchmark.

Related: [indicator filtering](BOUNDED_PARTICLE_FILTER.md),
[Boolean budget advice](PARTICLE_BUDGET_ADVISER.md), [inference health](INFERENCE_HEALTH.md),
[status and acceptance gates](WEIGHTED_FILTER_STATUS.md). No new runtime dependency.
