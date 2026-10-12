# Capability expansion after 7.0.0

Prepared for **7.1.0**, pending [release verification](RELEASE_7_1_STATUS.md), not functionality in the published 7.0.0
artifact. [Execution status](CAPABILITY_EXPANSION_STATUS.md) distinguishes completed
checks from pending work. Research provenance: [INF-04](RESEARCH_REFERENCES.md#inf-04),
[INF-05](RESEARCH_REFERENCES.md#inf-05), [INF-06](RESEARCH_REFERENCES.md#inf-06),
[INF-07](RESEARCH_REFERENCES.md#inf-07).

## Overview

This milestone expands selected inference contracts without changing existing
default proposals or the strict particle-filter failure policy. It addresses
structured likelihoods, combinatorial count queries, scalar proposal convenience,
custom log proposals and representable numerical boundaries. It does not make
arbitrary graphs thread-safe or all models exactly solvable.

## Quick start: an automatically scaled scalar move

1. Build a fresh fixed scalar kernel in each chain's universe.
2. Opt into `runTuned` and supply its tuner; warm-up is discarded.
3. Inspect both ordinary inference diagnostics and the frozen scale reports.

```scala
import com.cra.figaro.algorithm.sampling.SupportAwareProposal as S
import com.cra.figaro.algorithm.sampling.parallel.MultiChainMetropolisHastings as MH
import com.cra.figaro.library.atomic.continuous.Normal

val result = MH.runTuned(MH.Config(warmUp = 2000)) { (u, _) =>
  val x = Normal(0, 4)(using "x", u)
  x.addLogConstraint(v => -0.5 * math.pow((v-1)/0.2, 2))
  MH.TunedModel(MH.Model(Vector(MH.Observable("x", x)(identity))), S.tune(x))
}
println(result.result.diagnostics("x"))
println(result.tuning)
```

The ordinary `MH.run`, `Model`, `ChainResult` and fixed proposal factories retain
their signatures. No adaptation is silently enabled for existing callers.

## Three common patterns: old and new

### 1. Both likelihood location and variance are unknown

In 7.0, an observed one-child likelihood could depend on an explicitly combined
kernel parameter, but nested likelihood callbacks were refused. The new runner
also accepts an owned chain of likelihood callbacks, so the existing
`Normal(meanElement, varianceElement).observe(value)` construction can be used
directly. The terminal atomic density is scored once, including its normalizer.

```scala
val mean = Select(0.5 -> -1.0, 0.5 -> 1.0)(using "mean", u)
val variance = Select(0.5 -> 0.5, 0.5 -> 2.0)(using "variance", u)
Normal(mean, variance)(using "measurement", u).observe(0.3)
```

Each callback must create only its owned child, and nested Chain parents must be
permanent. The maximum nesting depth is 32. Shared children, additional latent
callback nodes and arbitrary observed `Apply` expressions remain refused. For
noisy observations of deterministic expressions, use the existing
[observation likelihoods](OBSERVATION_MODELS.md); noise changes the model and must
not be invented automatically. Exact continuous equality needs a specified
conditional measure, not an equality test on floating-point draws.

### 2. Ask about category totals without enumerating every count vector

The old full-vector table for 20 trials and 10 positive categories has 10,015,005
possible states. If evidence and queries only distinguish two groups, aggregate
first: the exact reduced table has only 21 states.

```scala
val law = MultinomialDistribution(20, Vector.fill(10)(0.1))
val groups = Vector((0 until 4).toVector, (4 until 10).toVector)
val totals = FiniteCountInference.multinomialAggregated(law, groups)
// Use totals in VariableElimination exactly as an ordinary finite Select.
```

This is marginalization, not independence between groups or an approximation.
Do not apply it if later evidence distinguishes categories within a group. General
factor products still have treewidth/storage costs; existing table budgets and
unrepresentable-mass refusals remain intact.

### 3. Replace hand-chosen scalar scales without adapting production draws

Previously, a caller selected support coordinates and a fixed scale manually.
`automatic` now chooses coordinates for known scalar adapters; `tune` additionally
adjusts the log scale from accepted/rejected warm-up transitions. It uses a bounded,
diminishing stochastic-approximation schedule, then freezes before retained draws.

```scala
// Fixed-scale legacy Gamma move; includes the value/randomness scale mapping.
val move = S.automatic(Gamma(2.0, 3.0), stepSd = 0.3)
// Optional owned-runner tuning; use in MH.TunedModel as in the quick start.
val tuner = S.tune(Gamma(2.0, 3.0), S.TuningConfig(initialStep = 0.3))
```

The tuner currently requires exactly one latent stochastic element. It rejects a
model with additional latent elements, including ones generated later, rather than
quietly freezing them. It does not optimize covariance, choose globally between
prior and local proposals, detect missing modes, or guarantee a target ESS.

## API reference

| API | Parameters | Return and example |
| --- | --- | --- |
| `MultinomialDistribution.aggregate` | `groups: Vector[Vector[Int]]`, a complete nonempty disjoint partition | Reduced immutable law; `law.aggregate(Vector(Vector(0,2),Vector(1)))` |
| `FiniteCountInference.multinomialAggregated` | `law`, `groups`, optional existing `Budget`, contextual name/collection | Exact `AtomicSelect[Vector[Int]]`; see count example above |
| `S.automatic` | Known fixed scalar `element`, `stepSd=0.5` | `ProposalScheme`; `S.automatic(Gamma(2,3), .3)` |
| `S.customScalar` | Fixed scalar `Element & HasLogDensity`, `ScalarCoordinates`, `stepSd`; second parameter list `toRandomness` | Corrected scheme; `S.customScalar(x,S.ScalarCoordinates.Positive,.2)(v => v/scale)` |
| `S.ScalarCoordinates` | `Real`, `Positive`, `Circular`, or `Bounded(lower,upper)` | Explicit support/coordinate declaration for custom adapters |
| `S.TuningConfig` | `initialStep=.5`, `targetAcceptance=.44`, `minStep=1e-6`, `maxStep=3.0` | Validated settings; positive finite ordered scale bounds and target strictly in (0,1) |
| `S.tune` | Known fixed scalar `element`, optional `TuningConfig` | One-use chain-owned `Tuning`; construct inside the model factory |
| `MH.TunedModel` | Ordinary `model`, chain-owned `tuning` | Input wrapper for `runTuned`; ordinary model must not also specify a proposal |
| `MH.runTuned` | Existing `Config` with positive warm-up; factory `(Universe,Int) => TunedModel` | `TunedResult(result,tuning)` with ordinary detached result and chain-ordered reports |
| `S.TuningReport` | Returned fields `stepSd`, `warmUpAttempts`, `atScaleBound` | Frozen settings; a bound hit is a diagnostic, not a calibration success |
| `LogProposalRatios.transition` | `next`, `oldLogModel`, `newLogModel`, `logForward`, `logReverse` | `(next, logReverse-logForward, newLogModel-oldLogModel)`; `transition(next,-1000,-1,-900,-2)` |
| `HasLogRandomnessProposal.proposeRandomness` | `old: Randomness` | User-defined proposed randomness |
| `HasLogRandomnessProposal.logRandomnessDensity` | `value: Randomness` | User-defined log model density in randomness coordinates |
| `HasLogRandomnessProposal.logProposalDensity` | `from`, `to: Randomness` | User-defined log `q(to given from)` in the same measure |
| `HasLogRandomnessProposal.nextLogRandomness` | `old: Randomness` | Validated native log-ratio tuple, derived from the three primitives above |
| `WishartDistribution.logDensityCholesky` | Finite positive-diagonal lower factor | Log density on independent lower entries, including its Jacobian |
| `InverseWishartDistribution.logDensityCholesky` | Same factor contract | Existing API now computes directly without forming its Gram matrix |

Automatic scalar support includes `AtomicScalar` with positive, bounded or all-real
support, ordinary (not subclassed) `AtomicNormal`, `AtomicGamma`, `AtomicBeta`, and
`AtomicVonMises`. Unknown representations require an explicit custom adapter.
Custom `toRandomness` must invert `generateValue` one-to-one; non-injective or
hierarchical representations need a native proposal instead.

### Custom MH: old ratio arithmetic versus native logs

Do not calculate `exp(logNew) / exp(logOld)` and then take its logarithm. The
division can already have overflowed. An existing custom hook can instead return:

```scala
override def nextLogRandomness(old: Randomness) = {
  val next = proposeRandomness(old)
  LogProposalRatios.transition(next, logModel(old), logModel(next),
    logQ(old, next), logQ(next, old))
}
```

Alternatively, implement the three primitives of `HasLogRandomnessProposal`.
The model and proposal terms remain separate for annealing. A zero new density
or zero reverse transition is a rejection; invalid current/forward densities or
unrepresentable finite differences fail explicitly. The library cannot infer
correct formulas or Jacobians for arbitrary third-party proposal code.

## Numerical changes and gotchas

- Tiny nonzero GEV/GPD shapes no longer have an arbitrary `1e-14` lower magnitude
  cutoff. A stable series prevents losing `shape * standardizedValue` before division.
  The magnitude cap 2, scale/location limits, representable distinct endpoints,
  singular endpoint behavior and collapsed-quantile refusals remain.
- Factor densities accept some supplied Cholesky factors whose Gram matrices round
  to singularity. This is a density on factor entries, not the matrix density.
  Triangular trace/log-determinant calculations can still overflow and explicitly fail.
  Sampling, matrix-valued density, degrees-of-freedom and conditioning contracts
  are not thereby expanded. No clipping, jitter or retry-discarding is introduced.
- Adaptation is opt-in, discarded and bounded. The target acceptance rate is not
  a universal optimum. Compare posterior checks, ESS and total warm-up plus sampling
  cost against the existing prior-proposal baseline.
- Single-target schemes require deliberate composition when other latents exist.
  The automatic tuner refuses that broader case for now.
- [Bounded-adaptive indicator filtering](BOUNDED_PARTICLE_FILTER.md) is now an
  opt-in development API. Existing filters gain structured health reports and
  recommendations, while strict failure and invalid-after-failure remain unchanged.

Related: [7.0 integration bridge](INFERENCE_INTEGRATION.md),
[multi-chain MH](MULTI_CHAIN_MCMC.md), [count/vector families](DISTRIBUTION_BREADTH.md),
[covariance priors](COVARIANCE_PRIORS.md), [inference health](INFERENCE_HEALTH.md).
