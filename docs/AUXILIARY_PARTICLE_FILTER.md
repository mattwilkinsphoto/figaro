# Observation-informed ancestor selection

Published in **7.2.0**, not in 7.1.0. See [installation](MAVEN_CENTRAL.md).
[Research provenance](RESEARCH_REFERENCES.md#inf-11).

## Overview: choose better parents as well as better children

A particle represents one possible state. Before generating the next state, a
filter chooses a previous particle as its parent (ancestor). Ordinary filtering
chooses parents using their previous posterior weights. Guided filtering changes
where children are sampled, but does not change that parent selection.

An **auxiliary particle filter (APF)** also uses the current observation to choose
promising parents. Figaro exposes this as opt-in methods on
`WeightedBoundedParticleFilter`, sharing its lifecycle, bounded stopping,
log-weight normalization and calibration. It is not a replacement for ordinary
`ParticleFilter` and does not adapt arbitrary mutable Element graphs.

Use it when a new observation distinguishes strongly between possible parents,
and a cheap predictive likelihood or useful approximation is available. Retain
ordinary/guided filtering as a comparison: weak observations, expensive look-ahead
or inaccurate approximations can make auxiliary filtering less efficient.

## Quick start in three steps

1. Define the same model and observation-informed kernel used for guided filtering.
2. Supply a log look-ahead score for each parent, fixed for this observation.
3. Start normally, then advance with the auxiliary method and inspect diagnostics.

```scala
import com.cra.figaro.algorithm.filtering.{BoundedParticleFilter as B,
  WeightedBoundedParticleFilter as W, WeightedParticleProposal as P,
  AuxiliaryParticleProposal as A, WeightedParticleCalibration as C}

val prior = P.gaussian(0, 1) // standard deviation
def transition(x: Double) = P.gaussian(.8*x, .5)
def likelihood(y: Double)(x: Double) = P.gaussian(x, .3).logDensity(y)
def guided(y: Double) = P.Kernel(prior, transition,
  P.gaussianObservation(0, 1, y, .3),
  (x: Double) => P.gaussianObservation(.8*x, .5, y, .3))
def ahead(y: Double) = A.LookAhead[Double](
  x => P.gaussianPredictiveLogLikelihood(.8*x, .5, y, .3))

val filter = W(B.Model(prior.sample, (x: Double) => transition(x).sample()),
  W.Config(minCandidates=256, maxCandidates=256, seed=42))
try {
  filter.start(W.Evidence(likelihood(.2), W.cappedLikelihood(0)), guided(.2))
  val result = filter.advanceAuxiliary(
    W.Evidence(likelihood(1.3), W.cappedLikelihood(0)), ahead(1.3), Some(guided(1.3)))
  println(result.expectation(identity))
  println(filter.lastAuxiliary) // preparation work and ancestor-selection ESS
  println(result.report.health) // DIFFERENT: corrected production weights
} finally filter.close()
```

There is no auxiliary `start`: no previous population exists at initialization.
Use ordinary or guided `start`, or `startCalibrated`.

## Three common patterns: old and new

### 1. Guided filtering still spends work on poor parents

Old next step:

```scala
filter.advance(W.Evidence(likelihood(y), W.cappedLikelihood(0)), guided(y))
```

New next step, used **instead of** the old call for the same observation:

```scala
filter.advanceAuxiliary(W.Evidence(likelihood(y), W.cappedLikelihood(0)),
  ahead(y), Some(guided(y)))
```

For example, suppose two parent groups have equal previous weight, but one makes
the new observation nine times more likely. Ordinary selection allocates roughly
half the children to each. An undefended exact look-ahead allocates approximately
90% to the promising group. The default 10% ordinary-selection share makes that
86%, retaining a safeguard against an inaccurate score. These are illustrative
selection probabilities, not claims about achieved accuracy or speed.

If you can score parents but cannot supply a normalized guided child law, omit
`Some(guided(y))`. The auxiliary filter then uses the model's transition sampler.
No transition density is required when that sampler is the actual target law.

### 2. Combine ancestor guidance with automatic work advice

Old calibrated guided step:

```scala
val request = C.Request(maxTotalCandidates=2000)
filter.advanceCalibrated(likelihood(y), request, Some(guided(y)))
```

New alternative:

```scala
val r = filter.advanceAuxiliaryCalibrated(likelihood(y), request,
  ahead(y), Some(guided(y)))
val candidateWork = filter.lastCalibration.get.pilotCandidates + r.report.attempts
val parentScoringWork = filter.lastAuxiliary.get.evaluations
```

The 2,000 ceiling includes pilots and production candidates, **not parent-score
evaluations**. Preparation scores each positive-weight retained parent once, before
either pilot; it is not repeated per child. Report both quantities. A score may
itself perform costly integration: these counters are not FLOPs or time limits.
Calibration still advises work, not posterior precision. It never reuses pilots
as production samples or retries only unsuccessful runs.

### 3. Vector states with partial linear-Gaussian observations

Previously you had to construct a normalized vector proposal and its predictive
score yourself. The new helper supplies both for `y = H*x + b + noise`:

```scala
import com.cra.figaro.library.atomic.continuous.MultivariateGaussianDistribution
val predicted = MultivariateGaussianDistribution(Vector(1.0,-1.0),
  Vector(Vector(2.0,.5), Vector(.5,1.0)))
val guide = P.linearGaussianObservation(predicted,
  observation=Vector(1.5),
  observationMatrix=Vector(Vector(1.0,2.0)),
  noiseCovariance=Vector(Vector(.5)),
  offset=Vector(.25))
val q = guide.proposal
val parentScore = guide.logPredictive
```

For each parent, `predicted` must describe the model's conditional transition,
not the previous posterior. Use its returned proposal as `proposedTransition`
and its score in `LookAhead`. Keep the real likelihood in `Evidence`; do not add
`logPredictive` as another likelihood. For nonlinear models a caller may supply
an explicitly approximate Gaussian proposal, but must still evaluate the true
target transition and likelihood in the importance correction. No automatic
linearization, Jacobian estimation or nonlinear fitting is supplied here.

The helper validates and factorizes on each call. If a caller caches per-parent
guides, construct an immutable cache before filtering; do not adapt it from
production candidates. Measure matrix-preparation overhead.

## Weighting and stopping contract

Given normalized previous weights `w_i` and log scores `log eta_i`, form
`a_i = w_i*eta_i / sum_j(w_j*eta_j)`. With defensive fraction `epsilon`, draw
each parent independently from `rho_i = epsilon*w_i + (1-epsilon)*a_i`.
Draw its child from `q(x|i)` and use the **full** weight

```text
logWeight = logLikelihood(x) + log p(x|i) - log q(x|i) + log w_i - log rho_i
```

The transition ratio is zero in log space for target-transition sampling. The
ancestor ratio uses the whole mixture, not a sampled component. Retaining its
normalization is essential to the absolute evidence estimate. Scaling all eta
values by the same positive constant does not change the mathematical law.

This is an independent extension of the bounded filter: conditional on the old
population and frozen preparation, the `(parent,child)` candidates are IID, and
their corrected expected weight is the same predictive evidence integral. The
existing stopped-sum correction applies to this augmented candidate space.
Adaptive target-hit drops the final candidate from the estimator AND future
ancestors; initial-floor and exhausted-cap paths retain all. Pilots use this same
law and full corrected weights, then are discarded. This reasoning requires the
documented purity/support assumptions; tests do not prove arbitrary user models.

Normalized posterior estimates need not be unbiased. APF cannot restore extinct
histories, and neither good ESS nor constant weights certifies missing modes.

## Public API reference

All callbacks must be frozen, deterministic/pure except for sampling through the
filter's scoped RNG. States must be detached and immutable. Invalid inputs raise
`IllegalArgumentException`; unsupported arithmetic raises `ArithmeticException`.
Callback failure, numerical failure and interruption invalidate the filter.

| API | Parameters and return | Example |
| --- | --- | --- |
| `A.LookAhead[S]` | `logMultiplier: S => Double`, `priorFraction` in [0,1], default .1; immutable policy | `ahead(y)` |
| `LookAhead.logMultiplier` | Parent state; finite log score or `-Infinity` | `ahead(y).logMultiplier(parent)` |
| `A.Report` | `evaluations`, `previousEss`, `selectionEss`, `usedPriorFallback`, `priorFraction`; preparation diagnostics | `filter.lastAuxiliary.get.selectionEss` |
| `filter.advanceAuxiliary` | Evidence, look-ahead, optional kernel; returns ordinary `W.Update[S]` | Pattern 1 |
| `filter.advanceAuxiliaryCalibrated` | Log likelihood, request, look-ahead, optional kernel; returns production update | Pattern 2 |
| `filter.lastAuxiliary` | No arguments; optional report, including genuine production collapse | `filter.lastAuxiliary` |
| `P.gaussianPredictiveLogLikelihood` | Mean, prior sd, observation, noise sd; finite log predictive density | `P.gaussianPredictiveLogLikelihood(0,1,2,.5)` |
| `P.multivariateGaussian` | Validated `MultivariateGaussianDistribution`; normalized vector `Law` | `P.multivariateGaussian(predicted)` |
| `P.linearGaussianObservation` | Prior Gaussian, observation vector, H matrix, R covariance, optional offset; `GaussianObservation` | Pattern 3 |
| `P.GaussianObservation` | `posterior`, finite `logPredictive`; validated result container | `guide.posterior.mean` |
| `GaussianObservation.proposal` | No arguments; normalized posterior `Law[Vector[Double]]` | `guide.proposal` |

Existing `Law.sample`, `Law.logDensity`, `Kernel`, scalar conditioning, defensive
child mixtures, update/close and calibrated-work APIs retain their
[guided-filter contracts](CALIBRATED_GUIDED_FILTER.md#api-reference).

## Gotchas and numerical limits

### Recommended next assessment, not a shipped helper

The [nonlinear proposal assessment](NONLINEAR_PROPOSAL_RESEARCH.md) and subsequent
[test-only experiment](NONLINEAR_PROPOSAL_STATUS.md) are complete, with **no promotion**
([INF-17](RESEARCH_REFERENCES.md#inf-17), [bounded roadmap](../ROADMAP.md#recommended-next-capability-assessment)).
The existing `linearGaussianObservation` does not linearize nonlinear functions
or fit their possibly multimodal conditional distributions. Cubature Gaussian and
explicit-Jacobian guides passed correctness checks but failed accuracy/cost gates;
they remain test-only. A bounded multi-component assessment is recommended next,
not implemented. True likelihood/transition corrections, explicit support
and preparation costs remain required. No performance improvement, automatic
policy, precision guarantee or recovery of lost histories is established.

### Current limits

- `priorFraction=0` requires finite look-ahead scores on **every** positive-weight
  parent; Figaro refuses zero scores even if a caller believes that branch is
  impossible. Default `.1` permits zero scores while retaining support. If all
  scores are zero, it falls back to previous weights and records that fact.
- `priorFraction=1` skips scoring entirely and preserves ordinary selection.
  A rough look-ahead cannot remove a parent permanently through a zero score,
  but any finite resampling population can still lose histories by chance.
- A genuinely zero-weight (`-Infinity` log-weight) parent is neither scored nor
  selected. Finite log weights remain active even if their ordinary output is zero.
  Log-domain mixture corrections and binary-tree ancestor sampling replace the
  former underflow/CDF refusals; there is no clipping or probability floor. Log
  arithmetic overflow still fails. Inspect `filter.currentLogWeights`; see the
  [numerical contract](WEIGHTED_BOUNDED_FILTER.md#numerical-representation-and-reproducibility)
  and [research provenance](RESEARCH_REFERENCES.md#inf-13).
- A defensive ancestor mixture protects parent support, **not child support**.
  Use an appropriate child proposal, optionally `P.defensive`, as well.
- The score is not the observation likelihood at the parent's current state.
  It should approximate the next observation's predictive likelihood, integrating
  transition uncertainty. A point prediction can be dangerously overconfident.
- Vector helpers require full-rank prior/noise/predictive/posterior covariances,
  dimensions 1..128, and the existing Gaussian range/conditioning limits. Finite
  H entries do not guarantee representable intermediate matrix operations.
  Covariance symmetry is restored after products, but no eigenvalues are repaired.
- Gaussian scalar arguments are standard deviations; vector matrices are
  covariances. Zero-noise observations and singular conditional laws are refused.
- `lastAuxiliary` clears on ordinary steps or callback/numerical failure, remains
  available on genuine zero-weight collapse, and is never a current posterior.
- A high selection ESS does not imply a high production ESS, or vice versa.
  Diagnostics and ancestry must be interpreted together.

## Evidence and limitations

The primary APF source explains predictive ancestor weighting, corrected child
weights, and why approximation tails matter; it also supplies counterexamples
to universal variance superiority. The maintained Python `particles` source is an
implementation comparison, not a new Figaro dependency. Figaro reuses its existing
Scala Gaussian kernels and Commons Math matrix solver. No external source code or
dataset is copied. See the [reference mapping](RESEARCH_REFERENCES.md#inf-11).

Acceptance uses exhaustive two-step finite stopping trees and Gaussian conditioning
identities, plus 512 fixed seeds per mode for a scalar Gaussian sequence and a
five-state nonlinear transition with a multimodal observation model. The latter
has an independent rational full-path oracle; it is not a continuous nonlinear
tracking benchmark. Five modes retain all outcomes: bootstrap, guided, auxiliary,
calibrated auxiliary, and deliberately poor auxiliary guidance. Fixed modes use
256 candidates per step; calibration shares that ceiling including its pilots.
Comparisons report posterior errors, evidence variance, ancestry, candidate and
density calls, look-ahead calls and elapsed time. Different operation types have
different costs. Timing includes preparation and is descriptive, not a CI gate.

Local 512-seed results (normalized evidence-estimator variance):

| Mode | Gaussian sequence | Nonlinear finite sequence |
| --- | ---: | ---: |
| Bootstrap | .01183 | .008321 |
| Guided | .004667 | .001323 |
| Auxiliary | .002499 | .000875 |
| Calibrated auxiliary | .01276 | .003824 |
| Deliberately poor auxiliary | .05219 | .01078 |

All 40 predeclared unnormalized mean checks passed, with no collapsed runs.
Lower variance at a fixed candidate count is not the whole result. Including
parent-score calls, auxiliary evidence variance-times-work was 5.76 versus 5.97
for guided in the Gaussian case, but 1.12 versus 1.02 in the finite case. Candidate
and parent-score calls are not equally expensive. The recorded elapsed-time
comparison favored auxiliary over guided on evidence variance-times-time in this
small run, but these short JVM timings do not establish a stable speedup. Posterior
mean error was essentially unchanged in the finite case. Calibration used less
work but increased posterior error and evidence variance; deliberately poor
look-ahead was worse despite support protection. These results support opt-in
selection, not a blanket replacement. Full per-seed outputs and work/timing
summaries are retained with the independent oracle.

See the [acceptance record](AUXILIARY_FILTER_STATUS.md) for checks actually executed.
The subsequent [continuous nonlinear/multimodal benchmark](CONTINUOUS_FILTER_BENCHMARKS.md)
exposed frequent numerical refusals, including in the standard growth model,
and modal depletion under work calibration. The log-domain/tree repair subsequently
completed all 7,680 unchanged-protocol runs with all 450 statistical checks passing.
Mode depletion remains possible, particularly with smaller calibrated populations;
robust ancestor selection adds cost. Final-source CI, packaged consumers and
[7.2.0 Central publication](RELEASE_7_2_STATUS.md) subsequently passed.
The earlier finite/Gaussian checks remain valid within their scope; they do not
establish broader readiness. High-dimensional and real-model validation remain
separate. No blanket default switch or speedup is promised.

## Related

[Weighted bounded filtering](WEIGHTED_BOUNDED_FILTER.md),
[calibration and guided proposals](CALIBRATED_GUIDED_FILTER.md),
[Gaussian information kernels](COMMON_INFORMATION_METRICS.md),
[inference health](INFERENCE_HEALTH.md), [RNG ownership](RNG_STREAMS.md).
