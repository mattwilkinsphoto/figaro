# Likelihood-weighted bounded filtering

Development API in **7.2.0-SNAPSHOT**, not in published 7.1.0.
[Research provenance](RESEARCH_REFERENCES.md#inf-09).

## Overview

`WeightedBoundedParticleFilter` is an opt-in bootstrap particle filter for immutable
states and explicit noisy-observation likelihoods. It allocates extra candidates to
a difficult observation, up to a hard cap, using a separate score policy fixed before
that step. It does not change ordinary `ParticleFilter`, retry a failed graph, tune
proposals, or promise a particular posterior precision.

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
  NaN and positive infinity fail explicitly. If a finite positive weight becomes
  zero during normalization, the filter refuses that dynamic range instead of
  calling it impossible evidence. Representable categorical probabilities still
  have ordinary floating-point and finite-RNG resolution limits.
- A score can numerically underflow to zero without changing the inference weight.
  That affects work allocation and may force the cap. Near-threshold floating-point
  sums are not exact real arithmetic.
- Callbacks must implement the true prior/transition laws; custom proposals need
  density-ratio correction and are outside this first version. Likelihood constants
  matter for evidence. A callback cannot silently omit them.
- Use `util.random` for owned seeded replay. Mutable states, external RNGs, shared
  graphs, reentrant calls and cross-thread use are unsupported. Invalid callbacks,
  interruption and numerical failures invalidate the posterior; interruption is not
  a completed capped estimate. Caps count candidates, not runtime inside callbacks.
- Extra current-step draws do not recover extinct histories or certify missing modes.
  No automatic fallback, pilot selection, score fitting or weighted budget adviser
  is implemented. The Boolean adviser cannot be reinterpreted as one.

## Validation and related modules

The tests exhaustively enumerate small two-step weighted models, including exact-cap
and fixed-floor boundaries, and check lifecycle, weighted ancestry and numerical
limits. The synthetic study uses fixed and adaptive settings with 512 predeclared
seeds each; all outcomes enter evidence and unnormalized-moment calibration. An
independent rational joint-Gaussian conditioning calculation checks the Kalman
reference. This is validation, not a matched-accuracy performance benchmark.

Related: [indicator filtering](BOUNDED_PARTICLE_FILTER.md),
[Boolean budget advice](PARTICLE_BUDGET_ADVISER.md), [inference health](INFERENCE_HEALTH.md),
[status and acceptance gates](WEIGHTED_FILTER_STATUS.md). No new runtime dependency.
