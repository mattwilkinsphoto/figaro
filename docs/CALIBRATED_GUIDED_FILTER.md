# Calibrated and observation-informed weighted filtering

Development API in **7.2.0-SNAPSHOT**, not published in 7.1.0.
[Research provenance](RESEARCH_REFERENCES.md#inf-10).

## Overview: two different problems, two opt-in tools

The [weighted bounded filter](WEIGHTED_BOUNDED_FILTER.md) originally required a
hand-selected score scale and candidate budget, and drew only from the model's
prior/transition. This extension provides:

- **Automatic score/work calibration:** independent pilot batches choose a score
  scale and a conservative production cap within your total candidate allowance.
- **Observation-informed proposals:** sample from a normalized law concentrated
  where this observation is plausible, then apply the explicit importance correction.

Calibration makes a work policy easier to configure. A better proposal can improve
weight concentration. Neither is a guarantee of posterior accuracy, missing-mode
detection, or faster execution. There is no change to ordinary `ParticleFilter`,
no automatic retry, and no support for arbitrary mutable Element graphs.

## Quick start: three steps

1. Define normalized prior/transition laws and your observation likelihood.
2. Set a total per-observation work ceiling; optionally supply guided laws.
3. Run calibrated steps and inspect both pilot advice and production health.

```scala
import com.cra.figaro.algorithm.filtering.{BoundedParticleFilter as B,
  WeightedBoundedParticleFilter as W, WeightedParticleCalibration as C,
  WeightedParticleProposal as P}

val prior = P.gaussian(0, 1) // standard deviation, NOT variance
def transition(x: Double) = P.gaussian(.8*x, math.sqrt(.3))
val model = B.Model(prior.sample, (x: Double) => transition(x).sample())
val filter = W(model, W.Config(seed = 42))
val request = C.Request(maxTotalCandidates = 2000)
def likelihood(y: Double)(x: Double) = P.gaussian(x, math.sqrt(.5)).logDensity(y)
def guided(y: Double) = P.Kernel(prior, transition,
  P.gaussianObservation(0, 1, y, math.sqrt(.5)),
  (x: Double) => P.gaussianObservation(.8*x, math.sqrt(.3), y, math.sqrt(.5)))

try {
  filter.startCalibrated(likelihood(.2), request, Some(guided(.2)))
  val result = filter.advanceCalibrated(likelihood(-.3), request, Some(guided(-.3)))
  val advice = filter.lastCalibration.get
  println(result.expectation(identity))
  println(advice.status)
  println(advice.pilotCandidates + result.report.attempts) // ACTUAL total candidates
  println(result.report.health) // production weights, not pilot scores
} finally filter.close()
```

The model and kernel must describe the **same target law**. For a guided step,
the kernel supplies the target densities and proposed draws; the bootstrap model
callbacks are not called. At start, use the initial law without an extra transition.
For a new observation construct a new frozen kernel; do not mutate callbacks during
pilots or production. `gaussianObservation` supports only scalar additive Gaussian
observations. General custom laws can use `Law`; vector Gaussian proposals can wrap
the existing immutable `MultivariateGaussianDistribution` sampler/density.

## Three common patterns: before and after

### 1. Keep bootstrap draws, stop guessing a likelihood scale

Previously:

```scala
filter.start(W.Evidence(likelihood(.2), W.cappedLikelihood(knownLogPeak)))
```

With calibration, on a fresh filter:

```scala
filter.startCalibrated(likelihood(.2), C.Request(maxTotalCandidates = 2000))
```

No proposal is supplied, so the target/draw distribution is unchanged. By default,
128 scale draws and 512 evaluation draws precede production. Those 640 draws are
discarded, but charged to the ceiling. Small cheap problems may be better served
by a known scale or ordinary fixed sampling. The user still chooses a meaningful
work allowance and `scoreTarget` (default 100); this is not automatic accuracy selection.

### 2. Change where particles are drawn, not the model being inferred

Previously, a sharp observation had to be reached by prior/transition draws.
Instead, on a fresh filter:

```scala
val y = 2.0
filter.start(W.Evidence(likelihood(y), W.cappedLikelihood(0)), guided(y))
```

For every guided draw, the filter uses `logWeight = logLikelihood + logP - logQ`.
The score receives **that corrected log weight**, not the likelihood alone and not
a normalized population weight. The evidence callback must supply the observation
likelihood only. Do not include the observation a second time in the target law.
The original manual configuration still controls work; guidance does not require
calibration. Ancestors retain ordinary multinomial selection from previous weights.

For exact scalar Gaussian conditioning, the initial corrected weights are constant;
later weights can still differ between parents. This does not cure loss of important
ancestral histories. It is a guided filter, **not** an auxiliary particle filter with
observation-dependent ancestor weights.

### 3. Guard a rough proposal with a prior mixture

When a custom approximation is too narrow or misses a mode, mix in the actual prior
or transition rather than silently assuming its support is adequate:

```scala
def defensive(y: Double) = P.Kernel(prior, transition,
  P.defensive(prior, P.gaussianObservation(0, 1, y, math.sqrt(.5)), .1),
  (x: Double) => P.defensive(transition(x),
    P.gaussianObservation(.8*x, math.sqrt(.3), y, math.sqrt(.5)), .1))

filter.startCalibrated(likelihood(.2), request, Some(defensive(.2)))
```

This uses the **full mixture density** in the correction, whichever component was
sampled. A positive prior fraction preserves support when the supplied prior is
correct, normalized and uses the same reference measure. It does not guarantee that
a finite run visits every mode, nor that a poor guided component improves efficiency.

## How calibration works and what the bound means

For each observation, with the previous particle population held fixed:

1. Draw exactly `scaleSamples` independent candidates. Choose the empirical
   `scaleQuantile` (default .9, lower order statistic) among finite corrected log
   weights. This is a **scale heuristic**, not an upper bound on the density.
2. Freeze `s(w) = min(1, exp(logWeight - logScale))`. Draw exactly
   `evaluationSamples` new candidates; calculate their mean score `sBar`.
3. Set `L = max(0, sBar - sqrt(log(1/(1-confidence))/(2*n)) - 1e-12)`.
   For a proposed production cap `N` with `N*L > scoreTarget`, the Hoeffding
   bound is `exp(-2*(N*L-scoreTarget)^2/N)` (plus a numerical margin).
4. Select the smallest cap above the requested floor whose bound is at most
   `missProbability`. Set the floor to at least the estimated target work, clipped
   to that cap. Freeze the scale, floor and cap; only then draw production candidates.

These are Figaro-specific applications of a bounded independent-sum inequality,
not the Frankenfilter paper's automatic tuning algorithm. Conditional on the scale
pilot and previous population, the lower-mean bound has the stated one-sided
coverage under IID sampling. The chance of **issuing a Ready plan and then missing
its score target** is bounded by `(1-confidence) + missProbability` under those
assumptions. It is not necessarily a conditional guarantee given that a plan was
Ready. It says nothing about posterior error. Across steps the bounds need a union
bound/error allocation for a sequence-wide interpretation; they are not a global
filter certificate. Floating-point margins are not formal interval arithmetic.

Status meanings:

| Status | What production does |
| --- | --- |
| `Ready` | Uses the frozen calibrated floor/cap and score |
| `NoFiniteScale` | Uses the entire remaining fixed budget; constant score one |
| `UnresolvedScoreMean` | Uses the entire remaining fixed budget; pilot cannot establish positive score mean |
| `InsufficientBudget` | Uses the entire remaining fixed budget; requested score miss bound does not fit |

All pilots run at their fixed declared sizes, even when scale selection has no
finite weight. An inconclusive pilot is **not** evidence of an impossible
observation. No pilot state or weight is reused for the posterior, no promising
pilot is cherry-picked, and no run is rejected simply because calibration struggled.
Genuine production collapse retains the existing `NoViableParticles` failure and
pilot work diagnostics. A callback/numerical error invalidates the filter and clears
completed-result diagnostics; it is not converted into zero evidence.

## API reference

All state and callback contracts are immutable/pure except for the scoped Figaro RNG.
Malformed input raises `IllegalArgumentException`; unrepresentable corrections
raise `ArithmeticException`. Interruptions propagate; there are no partial estimates.

| Public API | Parameters and return | Example |
| --- | --- | --- |
| `P.Law[S]` | `sample: () => S`, normalized `logDensity: S => Double`; reusable law | `P.Law(() => law.sample(random), law.logDensity)` |
| `Law.sample`, `Law.logDensity` | Draw one state / return log density or `-Infinity` off support | `prior.sample()`; `prior.logDensity(0)` |
| `P.Kernel[S]` | Target `initial`, `transition`; `proposedInitial`, `proposedTransition`; per-step specification | `guided(y)` above |
| `P.gaussian` | Mean and standard deviation; returns scalar law backed by Figaro's Gaussian kernel | `P.gaussian(0,1)` |
| `P.gaussianObservation` | Prior mean/sd, observation, noise sd; returns Gaussian conditional proposal | `P.gaussianObservation(0,1,2,.5)` |
| `P.defensive` | Prior, guided law, prior fraction in `(0,1)` (default .1); normalized mixture law | `P.defensive(prior,q,.1)` |
| `C.Request` | Total cap, fixed pilot counts, production floor, score target, quantile, confidence, miss aim; validated preferences | `C.Request(maxTotalCandidates=2000)` |
| `C.run` | Request, pilot seed, optional RNG backend, IID corrected-log-weight callback; returns `Advice` without production | `C.run(request,101)(drawLogWeight)` |
| `Advice.score` | Corrected log weight; frozen bounded score | `advice.score(-2)` |
| `Advice.config` | Base `W.Config`; copies frozen counts/target, retaining RNG/health | `advice.config(W.Config(seed=202))` |
| `Advice.maximumTotalCandidates` | No parameters; pilots plus maximum production candidates | `advice.maximumTotalCandidates` |
| `filter.start(evidence,kernel)` | Likelihood/score and frozen guided kernel; first production `Update` | Pattern 2 |
| `filter.advance(evidence,kernel)` | Same, after a successful start; next production `Update` | `filter.advance(e,guided(y))` |
| `filter.startCalibrated` | Likelihood, request, optional kernel; pilots then first production `Update` | Quick start |
| `filter.advanceCalibrated` | Same inputs, conditional on current ancestors; pilots then next `Update` | Quick start |
| `filter.lastCalibration` | No parameters; `Option[Advice]` for most recent step | `filter.lastCalibration.get.status` |

`Advice` also exposes `status`, optional `logScale`/`meanScore`, `lowerMean`,
`minCandidates`, `maxCandidates`, `scoreTarget`, `pilotCandidates`, and optional
`missBound` (only present for Ready). `W.Config` on the filter remains its manual
baseline; `lastCalibration` gives the actual calibrated settings. Standard
`current`, `lastReport`, `expectation` and `close` retain their
[existing contracts](WEIGHTED_BOUNDED_FILTER.md#api-reference).

For standalone `C.run`, production must use fresh independent randomness and
exactly the same candidate law, ancestor population, observation and corrected
weight definition. Do not reapply advice after the model or observation changes.
Integrated methods manage non-overlapping draws of the owned stream automatically.
External/random-stateful callbacks can violate this contract and are unsupported.

## Gotchas and limits

- The total cap is 1,000,000 candidates at most, including pilots. A candidate may
  require several density evaluations; cost is neither RNG words nor elapsed time.
- Calibration overwrites per-step manual work counts, not your stored `W.Config`.
  It does not choose a proposal family or fit arbitrary densities automatically.
- Pilot quantiles and scores never clip inference weights. All original stopping
  corrections remain: adaptive target-hit drops the last candidate; initial batch
  and exhausted cap retain all. Scores include the discarded stopping candidate.
- Finite normalized-weight underflow and unrepresentable log corrections still fail
  explicitly. No new extreme-tail or singular-Gaussian promise is made.
- `q` must be positive wherever the target contributes. Checking sampled points
  cannot prove this. Invalid `q` on its own draw is rejected, but missed support
  elsewhere is a caller responsibility. Dirac/discrete and continuous laws cannot
  be combined by pretending they share ordinary Lebesgue densities.
- Gaussian helpers use **standard deviations**, unlike legacy `Normal` factories
  that take variance. Zero noise and deterministic conditioning are unsupported.
- Pilot work can cost more than it saves. A score scale estimated from easy regions
  can also miss hard regions; concentration/ancestry health must still be reviewed.

## Validation and evidence

Acceptance includes exact finite stopping trees with non-prior proposals, full
mixture-density checks, scalar Gaussian conditioning, lifecycle/RNG/failure checks,
and 512 predeclared seeds for each of four modes on the existing five-observation
linear-Gaussian control. The comparison fixes the **same total ceiling**, not equal
realized work: calibration may spend less work and have larger Monte Carlo variance.
Pilot and production candidates are both counted. All seeds and zero outcomes are
retained; neither lower work alone nor a passing mean gate establishes a speedup.
The independent control conditions the rational joint Gaussian covariance instead
of using the Scala study's Kalman recursion.

Local 512-seed results for the normalized evidence estimator (these are measured
synthetic results, not performance promises):

| Mode | Total candidates over all runs, pilots included | Variance per run | Variance times mean candidate work |
| --- | ---: | ---: | ---: |
| Fixed bootstrap | 1,966,080 | .00360 | 13.82 |
| Fixed guided | 1,966,080 | .00153 | 5.88 |
| Calibrated bootstrap | 988,512 | .01293 | 24.96 |
| Calibrated guided | 754,472 | .00826 | 12.17 |

Lower is better in the last column, but candidate costs differ and it is **not**
wall-time efficiency. Guidance helped this evidence calculation at fixed work.
Calibration saved candidates but increased variance, and neither calibrated mode
beat fixed guided sampling on this work-normalized evidence measure. For the first
and second unnormalized state moments, calibrated guided sampling also had worse
variance-times-work than fixed bootstrap. This is direct counterevidence to a claim
that the new path should replace the baseline everywhere. Calibrated bootstrap used
fixed-budget fallback on 319 of 2,560 steps; calibrated guided used it on two.
The exact parameters, all twelve rows and machine-readable comparison are emitted
by the study and checked by `tools/guided_filter_reference.py`.

See the [execution checklist](CALIBRATED_GUIDED_STATUS.md) for checks actually run.
Real application acceptance, matched wall-time efficiency, nonlinear/high-dimensional
proposal fitting and auxiliary ancestor proposals remain follow-up work.

## Related

[Weighted filter](WEIGHTED_BOUNDED_FILTER.md), [indicator budget advice](PARTICLE_BUDGET_ADVISER.md),
[Gaussian kernels](COMMON_INFORMATION_METRICS.md), [defensive vector importance](VECTOR_IMPORTANCE.md),
[RNG ownership](RNG_STREAMS.md), and [research index](RESEARCH_REFERENCES.md#inf-10).
