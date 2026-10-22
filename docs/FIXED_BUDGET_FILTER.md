# Explicit-policy fixed-budget particle filtering

Published API in **7.3.0**, not included in released 7.2.0.
[Research provenance](RESEARCH_REFERENCES.md#inf-15).
[Validation protocol](FIXED_BUDGET_PROTOCOL.md).

## Overview: when to use it

`FixedBudgetParticleFilter` gives an immutable-state bootstrap model exactly N
initial/transition draws and N likelihood evaluations per successfully completed
observation. Use it when predictable candidate counts and explicit control over
resampling matter. It is not a wall-clock deadline: a model callback may be expensive.

Unlike [weighted bounded filtering](WEIGHTED_BOUNDED_FILTER.md), it has no score
target, discarded stopping candidate or adaptive candidate budget. Unlike ordinary
graph `ParticleFilter`, it takes detached states and callbacks, not arbitrary Universes.
The existing filters and their defaults remain unchanged. There is no automatic cutover.

**Numerical contract:** decision-focused stratification avoids the former requirement
for every positive mass to have its own representable CDF interval. It still has
explicit domain, precision and refinement-population limits, explained below.
No hidden truncation, clipping or fallback is performed. Neither method promises
recovery of missing modes or exact continuous-uniform sampling.

**Delivery status:** the [numerical investigation](STRATIFIED_NUMERICAL_INVESTIGATION.md)
identified excessive refusals in the former CDF implementation. Its replacement is
accepted after [production integration validation](STRATIFIED_INTEGRATION.md),
including all local and remote gates. The [7.3.0 readiness record](RELEASE_7_3_STATUS.md)
records completed publication and all five Central-only consumer checks. Examples
require 7.3.0 or later. Choose stratification only within its documented limits;
released 7.2.0 and its stopping estimator remain unchanged.

## Quick start: three steps

1. Define detached immutable state draws using Figaro's scoped RNG.
2. Choose N, a scheduling policy and a resampling method explicitly.
3. Start, advance, inspect weighted results and diagnostics, and close.

```scala
import com.cra.figaro.algorithm.filtering.{BoundedParticleFilter as B, FixedBudgetParticleFilter as F}
import com.cra.figaro.util.random
val model = B.Model(() => random.nextGaussian(),
  (x: Double) => .8*x + math.sqrt(.3)*random.nextGaussian())
val filter = F(model, F.Config(256, F.Policy.Always, F.Method.LogTreeMultinomial, seed = 42))
def likelihood(y: Double): Double => Double =
  x => -.5*(math.log(2*math.Pi*.5) + (y-x)*(y-x)/.5)
try {
  filter.start(likelihood(.2))
  val result = filter.advance(likelihood(-.3))
  println(result.expectation(identity))
  println(result.report)
} finally filter.close()
```

## Three common patterns

### 1. Fixed candidate count versus adaptive work

```scala
import com.cra.figaro.algorithm.filtering.WeightedBoundedParticleFilter
val fixed = F.Config(256, F.Policy.Always, F.Method.LogTreeMultinomial)
// Existing, separate API: may draw extra candidates, with corrected stopping accounting.
val adaptive = WeightedBoundedParticleFilter.Config(128, 768, scoreTarget = 100)
```

Both accept the same `B.Model`; the existing API additionally requires a score
policy and retains its guided/APF capabilities. A fixed-budget configuration is
not a posterior-precision target. More particles do not guarantee mode discovery.

### 2. Retain weights when the previous population is adequate

```scala
val fractionChosenForThisModel = 0.5 // Illustrative, not a recommendation or default.
val scheduled = F.Config(256, F.Policy.WhenEssBelow(fractionChosenForThisModel),
  F.Method.LogTreeMultinomial)
```

The decision uses the previous completed population, before drawing this step.
If ESS/N is strictly below the threshold, select N ancestors once; otherwise each
parent propagates once and its previous weight is multiplied by the likelihood.
The evidence increment is the corresponding weighted sum, not an unweighted
likelihood average. Returned particles remain weighted; there is no second output
resampling pass. `WhenEssBelow(1)` is not `Always` when weights are exactly equal.
Neither 0.5 nor the earlier research selection 0.75 is a universal default.

### 3. Explicit stratification on a numerically supported model

```scala
val finite = B.Model(() => random.nextDouble() < .5, (x: Boolean) =>
  if (random.nextDouble() < .1) !x else x)
val stratified = F(finite, F.Config(256, F.Policy.Always, F.Method.Stratified))
try {
  stratified.start(x => math.log(if (x) .8 else .2))
  stratified.advance(x => math.log(if (x) .3 else .7))
} finally stratified.close()
```

Stratified offspring are dependent; that is allowed by this fixed-N construction,
not permission to insert them into the released random-stopping estimator.
The development repair compares each draw against bounded raw prefix masses and
refines ambiguous decisions without drawing again. Tiny positive mass no longer
requires a distinct binary64 CDF interval. Unresolved decisions or explicit
numerical/resource limits throw `StratifiedPrecisionLoss` and invalidate the
filter. Do not retry until success or keep only surviving runs for evidence or
method comparisons. A newly configured run is a different experiment.

## Public API reference

All names below belong to `FixedBudgetParticleFilter` (`F`).

| API | Parameters, result and example |
| --- | --- |
| `Config` | Required `particles` (2..1,000,000), `policy`, `method`; optional seed=1, Figaro RNG algorithm, health warning configuration; `F.Config(256,F.Policy.Always,F.Method.Stratified)` |
| `Policy.Always` | Resample before every transition, never before initialization |
| `Policy.WhenEssBelow` | Finite fraction in (0,1]; strict previous ESS/N comparison; `F.Policy.WhenEssBelow(.5)` |
| `Method` | `LogTreeMultinomial` or `Stratified`; no default; method changes RNG consumption |
| `apply` | `B.Model[S]`, `Config`; returns unstarted owned filter; `F(model,config)` |
| `start` | `S => Double` log likelihood; returns initial `Update[S]`; `filter.start(likelihood(.2))`; once only |
| `advance` | New log likelihood; returns next `Update[S]`; `filter.advance(likelihood(-.3))` |
| `current` | No arguments; latest valid `Update`; throws before start, after failure or close |
| `config` | Immutable supplied configuration; `filter.config.particles` |
| `lastReport` | Optional completed report; includes genuine collapse, absent after numerical/callback/interruption failure |
| `close` | No arguments; idempotent terminal owner-thread cleanup; detached prior results remain readable |
| `Update` | Aligned immutable `particles`, ordinary `weights`, retained `logWeights`, cumulative `logEvidence`, `report`; ordinary weights can round tiny positives to zero |
| `Update.expectation` | Finite observable `S => Double`; weighted mean; `result.expectation(x => x*x)`; rejects nonfinite values/results |
| `StepReport` | `stateDraws`, `likelihoodCalls`, `ancestorSelections`, `decision`, `previousRelativeEss`, `contributingParents`, new-population `health`, `selection`; `result.report.decision` |
| `SelectionReport` | `refinedDecisions`, `decimalTables`, `maxPrecision`, `exactTies`; zero on initialization/carry/log-tree; `result.report.selection.maxPrecision`; exactTies counts multi-group symbolic ties, not the equal-support shortcut |
| `Decision` | `Initial`, `Resampled`, `CarriedWeights`; initialization has no previous ESS or parent count |
| `NoViableParticles` | Terminal exception carrying completed `report` and `logEvidence=-Infinity`; no posterior, not proof of impossible evidence |
| `StratifiedPrecisionLoss` | Terminal numerical/resource refusal with `reason`; no completed step/report, redraw or automatic method substitution |

## Gotchas and limits

- Callbacks must implement true prior/transition draws. No proposal correction is
  performed; substituting an observation-informed proposal silently changes inference.
- Likelihoods accept finite logs (including positive density logs) and `-Infinity`,
  not NaN/+Infinity. Normalizing constants matter for evidence. Log evidence and
  normalized means are not asserted unbiased. Unrepresentable log arithmetic fails.
- Stratification uses stable input order and finite-bit random coordinates. Decisions
  are relative to those exact coordinates, not ideal continuous uniforms. The exact
  positive-log span must be <=1,000,000. Decimal refinement is limited to N<=4096
  and 40/80/160 digits. Larger populations retain fast bounded and equal-support
  selection, but ambiguous nonuniform decisions refuse explicitly. Configuration
  acceptance up to one million is not a guarantee of completion on arbitrary weights.
  See the [numerical review and capacity contract](STRATIFIED_INTEGRATION.md).
- ESS scheduling is separate from health-warning thresholds. New-population health
  is measured before any future resampling. Equal weights after selection do not
  imply independent histories, accuracy or recovered diversity.
- `contributingParents` counts immediate parent indices with positive retained log
  weight, not unique states or ancestral paths. Full path history is not stored.
- Mutable state, shared graph callbacks, external RNGs, cross-thread use and reentrant
  filtering are unsupported. Callback failure, interruption and numerical failure
  invalidate the current posterior. A partially executed step is not an N-draw estimate.
- No automatic retries, adaptive N, threshold tuning, guided/APF hooks, smoothing,
  rejuvenation or parallel executor in this API.

## Related

[Bounded model and ownership contract](BOUNDED_PARTICLE_FILTER.md),
[weighted adaptive-work filter](WEIGHTED_BOUNDED_FILTER.md),
[guided proposals](CALIBRATED_GUIDED_FILTER.md),
[auxiliary filtering](AUXILIARY_PARTICLE_FILTER.md),
[prior experiment and limitations](RESAMPLING_SCHEDULE_STATUS.md).
