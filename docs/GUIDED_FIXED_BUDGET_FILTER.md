# Guided and auxiliary fixed-budget filtering

**Development-only in 7.4.0-SNAPSHOT; not in published 7.3.0.**
[Research provenance](RESEARCH_REFERENCES.md#inf-16).
[Validation and promotion status](GUIDED_FIXED_BUDGET_STATUS.md).

## Overview

Ordinary fixed-budget filtering draws from the model before evaluating the new
observation. When observations are informative, many of those draws can receive
little weight. A **guided proposal** uses the observation to suggest each new
state and corrects its weight by the true target density divided by the proposal
density. An **auxiliary step** can additionally favor parents likely to explain
the observation, with a separate ancestor correction.

These are explicit options on `FixedBudgetParticleFilter`. They do not replace
bootstrap filtering, change defaults, fit proposals automatically, or alter the
released weighted-bounded stopping estimator. All successful steps still use
exactly N state draws and N likelihood calls, but guidance adds density evaluation
and construction costs. N is not a wall-clock budget or accuracy guarantee.

## Quick start: three steps

1. Define the true initial/transition model and a normalized proposal for each observation.
2. Choose N, resampling policy and method explicitly.
3. Call guided `start`/`advance`, or `advanceAuxiliary`; inspect reports and close.

```scala
import com.cra.figaro.algorithm.filtering.{BoundedParticleFilter as B,
  FixedBudgetParticleFilter as F, WeightedParticleProposal as P,
  AuxiliaryParticleProposal as A}

val initial = P.gaussian(0, 2) // standard deviation, not variance
def transition(x: Double) = P.gaussian(.8*x, math.sqrt(.3))
val model = B.Model(initial.sample, (x: Double) => transition(x).sample())
def likelihood(y: Double): Double => Double = x => P.gaussian(x, .4).logDensity(y)
def kernel(y: Double) = P.Kernel(initial, transition,
  P.gaussianObservation(0, 2, y, .4),
  (x: Double) => P.gaussianObservation(.8*x, math.sqrt(.3), y, .4))
def lookAhead(y: Double) = A.LookAhead[Double](
  x => P.gaussianPredictiveLogLikelihood(.8*x, math.sqrt(.3), y, .4),
  priorFraction = .1)

val filter = F(model, F.Config(256, F.Policy.WhenEssBelow(.5),
  F.Method.LogTreeMultinomial, seed = 42)) // illustrative threshold, not a default
try {
  filter.start(likelihood(.2), kernel(.2))
  val result = filter.advanceAuxiliary(likelihood(-.3), lookAhead(-.3), Some(kernel(-.3)))
  println(result.expectation(identity))
  println(result.report)
  println(filter.lastProposalReport)
} finally filter.close()
```

## Common patterns: what changes and why

### 1. Bootstrap versus guided propagation

```scala
// Separate filters/runs with the same declared model, evidence and configuration:
bootstrap.start(likelihood(.2))
guided.start(likelihood(.2), kernel(.2))
bootstrap.advance(likelihood(-.3))
guided.advance(likelihood(-.3), kernel(-.3))
```

Bootstrap needs only true model draws and likelihoods. Guided filtering requires
normalized target and proposal densities and adds N calls to each. It can help
when the new likelihood is concentrated away from typical prior draws. It can
lose on weak observations, poor proposals or expensive density functions. Never
replace `model.transition` with a guided sampler without using the corrected API.

### 2. Guided propagation versus auxiliary ancestor selection

```scala
guided.advance(likelihood(-.3), kernel(-.3))
auxiliary.advanceAuxiliary(likelihood(-.3), lookAhead(-.3), Some(kernel(-.3)))
// Auxiliary selection alone is also possible, with true model propagation:
auxiliaryBootstrap.advanceAuxiliary(likelihood(-.3), lookAhead(-.3))
```

The second call can favor useful parents before drawing children. The third changes
parents but not child proposals. Both auxiliary calls obey the configured policy:
if the **previous completed posterior ESS** does not trigger resampling, parents
are carried one-to-one, look-ahead is not evaluated, and no ancestor correction
is applied. An auxiliary request therefore does not guarantee auxiliary selection.
Use `Always` explicitly if every transition must select ancestors. We do not silently
switch the trigger to auxiliary ESS or the current observation's likelihood.

### 3. Defend an approximate proposal and account for its cost

```scala
val approximate = P.Kernel(initial, transition,
  P.defensive(initial, P.gaussianObservation(0, 2, .2, .4), priorFraction = .1),
  (x: Double) => P.defensive(transition(x),
    P.gaussianObservation(.8*x, math.sqrt(.3), .2, .4), priorFraction = .1))
// Rebuild with the current observation; this kernel is only for observation .2.
guided.start(likelihood(.2), approximate)
val extra = guided.lastProposalReport.get
println(extra.targetDensityCalls + extra.proposalDensityCalls)
```

The full mixture density is used, not just the component that generated a draw.
The child defensive fraction and ancestor defensive fraction protect different
supports; neither guarantees accuracy or discovery of missing modes. Include
guide construction and mixture-component work when comparing against a larger
bootstrap population. The public report cannot see inside arbitrary callbacks.

## API additions

All existing configuration, result and report constructor shapes are preserved.
See the [base API](FIXED_BUDGET_FILTER.md) for unchanged functions and numerical limits.

| Function/type | Parameters, return and example |
| --- | --- |
| `start(logLikelihood, proposal)` | `S => Double`, `P.Kernel[S]`; initial `F.Update[S]`; `f.start(g,k)`; once only |
| `advance(logLikelihood, proposal)` | New log likelihood and frozen kernel; next weighted update; `f.advance(g,k)` |
| `advanceAuxiliary(logLikelihood, lookAhead, proposal=None)` | New log likelihood, `A.LookAhead[S]`, optional kernel; next weighted update; `f.advanceAuxiliary(g,a,Some(k))`; requires start |
| `lastProposalReport` | No arguments; `Option[F.ProposalReport]`; `f.lastProposalReport.flatMap(_.auxiliary)` |
| `ProposalReport` | `targetDensityCalls`, `proposalDensityCalls`, `auxiliaryRequested`, optional existing `A.Report`; top-level counts only; `report.auxiliary.map(_.evaluations)` |

With guidance, the kernel's target laws are authoritative and model callbacks
are not called. They must describe the **same** model used by any bootstrap
steps. Kernels may be replaced between observations but must remain fixed/pure
throughout each step. A user-supplied density or model mismatch is not detectable
in general. Returned particles remain weighted; use `expectation`, not an ordinary
mean. Reports are retained on genuine all-zero collapse, absent after other
callback/numerical/interruption failure. Earlier detached snapshots remain valid.

## Gotchas

- Both densities must be normalized relative to the same measure, and q must be
  positive wherever the target contribution is positive. Sampled-point checks
  cannot certify support. Positive log densities are valid; NaN/+Infinity are not.
- `logEvidence` includes both corrections and likelihood normalizers. It is not
  an unbiased estimate of log evidence; normalized moments are not claimed unbiased.
- Auxiliary scores may be approximate/unnormalized. A zero defensive fraction
  requires finite scores on every positive-log-weight parent. All-zero scores with
  positive defense use the documented prior fallback, reported explicitly.
- Work reports count completed top-level calls, not mixture components, guide
  construction or user side effects. Callback exceptions terminate the filter;
  partial work is not reported as a successful N-particle update.
- Detached immutable states, owner-thread access and Figaro's scoped RNG remain
  required. No arbitrary shared graph, parallel executor or reentrant callback.
- Existing stratification domain/refinement limits apply to the **selection law**,
  which auxiliary scores can make more extreme. Refusal is terminal: no clipping,
  redraw, silent fallback or retention of successful retries only.
- No automatic choice of N, ESS fraction, proposal, defense fraction or look-ahead.
  No smoother, rejuvenation, guaranteed missing-mode detection or precision stopping.

## Related

[Guided helpers and calibration](CALIBRATED_GUIDED_FILTER.md),
[auxiliary and vector-Gaussian helpers](AUXILIARY_PARTICLE_FILTER.md),
[weighted adaptive-work filtering](WEIGHTED_BOUNDED_FILTER.md),
[frozen accounting and validation protocol](GUIDED_FIXED_BUDGET_PROTOCOL.md).
