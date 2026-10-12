# Bounded-adaptive indicator particle filtering

Released in **7.1.0**, not in 7.0.0. See the
[release-readiness record](RELEASE_7_1_STATUS.md) for publication status.
[Research provenance](RESEARCH_REFERENCES.md#inf-07).

## Overview: extra effort without success-only retries

`BoundedParticleFilter` filters an immutable state sequence using explicit Boolean
evidence. It starts each step with a fixed batch, extends sampling if the success
target is unmet, and stops at a maximum number of candidates. It follows the binary
weight specialization of Frankenfilter Algorithms 3–5, including the stopping
correction and corrected ancestor population. It is not an automatic repair of an
already failed `ParticleFilter`, nor a general likelihood filter.

Use it when a supported hard constraint rejects many prior-predictive candidates,
particularly when difficulty varies across observations. Ordinary `ParticleFilter`
remains appropriate for general likelihood observations and fixed computational
budgets. No universal speedup, accuracy, mode-discovery or recovery guarantee applies.

## Quick start: three steps

1. Build an immutable-state model. Use Figaro's scoped `util.random` in direct
   sampling callbacks, or use fresh fixed Element graphs as below.
2. Set an initial batch, success target, maximum candidate budget and seed.
3. Start, advance with Boolean evidence, inspect reports, and close the filter.

```scala
import com.cra.figaro.algorithm.filtering.BoundedParticleFilter as B
import com.cra.figaro.library.atomic.discrete.Flip

val model = B.elementModel[Boolean](
  u => Flip(.5)(using "state", u),
  (previous, u) => Flip(if (previous) .8 else .2)(using "state", u))
val filter = B(model, B.Config(
  minCandidates = 100, maxCandidates = 2000, targetSuccesses = 101, seed = 42))
try {
  val first = filter.start(state => state)
  val next = filter.advance(state => state)
  println(next.expectation(state => if (state) 1.0 else 0.0))
  println(next.report)
  println(next.report.warnings)
  println(next.report.health.recommendations)
} finally filter.close()
```

This example deliberately has a known posterior (the observed Boolean is true).
For partially observed models, return an immutable tuple/case class containing all
state needed for the next transition, and test only its observed component.

## Common patterns: standard versus bounded

### 1. A fixed-budget run returns few or zero viable particles

Previously, zero weights produced a generic exception; nonzero weights could look
successful even if concentrated in one candidate. Existing sequential and parallel
filters now expose `lastHealth`, measured before resampling. Their sampling,
particle count and strict default failure policy are unchanged.

```scala
import com.cra.figaro.algorithm.filtering.ParticleFilter

// pf is an ordinary ParticleFilter created through the existing factory.
try {
  pf.start()
  println(pf.lastHealth)
} catch {
  case e: ParticleFilter.NoViableParticles =>
    println(e.diagnostics) // readable although posterior/evidence queries are invalid
    // Inspect the recommendation; do not loop until a successful run appears.
}
```

An ordinary likelihood graph is not proven indicator-compatible merely because its
sampled weights happened to be 0 or 1. These reports declare eligibility `Unknown`
and condition their recommendation on an explicit Boolean model. The fresh-graph
adapter rejects `.observe`, conditions, constraints and interventions. Move true
indicator evidence to the predicate; do not turn a noisy observation into an exact
Boolean equality just to use this filter. For exact continuous values, equality
rejection generally has probability zero; retain an appropriate likelihood model.

### 2. Some observations are much harder than others

A standard filter spends the same number of candidates at each completed step.
The bounded filter automatically extends the initial batch under a policy fixed
before seeing those candidates. No manual failure/restart is necessary.

```scala
import com.cra.figaro.util.random
val transitions = B.Model[Int](
  () => random.nextInt(10),
  old => (old + random.nextInt(3)) % 10)
val adaptive = B(transitions, B.Config(100, 5000, 101, seed = 19))
try {
  adaptive.start(_ >= 2)
  val update = adaptive.advance(_ == 7)
  println((update.report.attempts, update.report.retainedSuccesses,
    update.report.targetMet, update.report.distinctParents))
} finally adaptive.close()
```

This is automatic continuation within a declared method, not switching a completed
ordinary filter after inspecting its result. The latter needs separate estimator
analysis. State and evidence callbacks must not retain mutable histories or change
the model based on the current run's success count.

### 3. The budget is exhausted

If some retained candidates survive, the returned update remains queryable;
`targetMet` is false and `warnings` says the budget was insufficient. Zero survivors
raise `B.NoViableParticles`, carrying the completed report and a **zero evidence
estimate** (`logEvidence = -Infinity`), but no posterior. The filter becomes terminal.

```scala
try filter.advance(state => state)
catch {
  case e: B.NoViableParticles =>
    println(e.report.health.recommendations)
    // In repeated evidence calibration include exp(e.logEvidence) == 0.
    // Review the model/budget before a separately declared new experiment.
}
```

Do not average only surviving runs. Callback errors, interruptions and invalid
numerics are execution failures, not zero-likelihood draws; they must not be counted
as statistically valid zero estimates.

## Choosing thresholds and eligibility

- Zero survivors means no posterior, not proof of impossible evidence.
- For indicator weights, raw weight ESS equals retained survivor count. For general
  likelihoods, use ESS and largest normalized weight, not positive count alone.
- Health defaults warn below ESS 100, below ESS/attempts 0.01, or at largest weight
  0.1; largest weight at least 0.5 is danger. These match existing engineering
  conventions, not calibrated precision criteria. Particle dependence invalidates
  a naive IID error interpretation. No Pareto/IID MCSE claim is made here.
- `targetSuccesses=101` retains 100 successes when extra sampling reaches the
  target. The initial fixed batch can retain more. The target is a work/allocation
  choice, not a universal accuracy requirement; higher precision may need much more.
- Known hard constraints, rare events and earlier collapses identify elevated risk.
  If independent compatibility probability p is known, `(1-p)^N` gives the conditional
  probability of zero survivors in N candidates. A pilot estimate is uncertain;
  zero pilot successes do not establish p=0. Figaro does not infer p or tune budgets.
- Counts cannot certify missing modes. Repeated exhaustion should prompt proposal
  or model review; more effort cannot recover unreachable states lost previously.

## Estimator and state contract

At a step let m be attempted candidates and S observed successes. If the initial
fixed batch suffices, retain it and estimate the increment by S/m. If extra sampling
reaches target r, exclude the final success and use `(r-1)/(m-1)`. If the cap is reached
below target, retain partial successes and use S/m. Success **exactly at the cap**
uses the exclusion rule. If min=max, the fixed-batch rule takes precedence.

Future ancestors are sampled uniformly from the retained successes, with no extra
fixed-size resampling layer. Retained values are an equal-weight posterior
approximation. Evidence increments accumulate in logs. The paper's unbiasedness
result assumes valid prior/transition sampling and indicator potentials; Figaro's
finite arithmetic, callbacks and adapters require their own checks. Posterior means
and log evidence are not claimed unbiased. No particle-MCMC driver is provided.

The Element adapter accepts fixed, evidence-free DAGs with all parents owned by the
supplied universe. It clears every owned universe after sampling or failure. `Apply`
can construct immutable state tuples; dynamic Chains, external Element parents and
graph expansion are rejected. Arbitrary custom code purity and deep immutability
cannot be verified automatically. Never return Elements, Universes or mutable state.

The filter is thread-confined and rejects cross-thread or reentrant calls. Use one
filter per independent experiment. Its RNG is owned and seeded; calling-thread RNG
scope is restored on return/error. External RNGs in callbacks are outside replay.
Candidate budgets do not bound seconds, graph size or internal rejection loops.
There is no wall-clock cancellation estimator, automatic evidence relaxation,
resampling-based claim of restored information, or silent post-failure retry.

## Public API reference

| API | Parameters / return / example |
| --- | --- |
| `B.Config` | `minCandidates=1000`, `maxCandidates=10000`, `targetSuccesses=101`, `seed=1`, named RNG `algorithm`, `health`; validates `2 <= min <= max`, `2 <= target <= max` |
| `B.Model[S]` | `initial: () => S`, `transition: S => S`; callbacks sample the specified laws and return detached immutable states |
| `B.elementModel[S]` | `initial: Universe => Element[S]`, `transition: (S,Universe) => Element[S]`; returns a fresh-graph `Model[S]`; see quick start |
| `B.apply` | `model`, optional `config`; returns an unstarted owned filter, no sampling yet |
| `filter.start` | `evidence: S => Boolean`; initial `Update[S]`; callable once |
| `filter.advance` | `evidence: S => Boolean`; next `Update[S]`; prior step must be valid |
| `filter.current` | No arguments; latest valid update, throws before start/after failure/close |
| `filter.lastReport` | No arguments; optional last completed assessment, including collapse; absent after a failed callback |
| `filter.close` | No arguments; releases retained states, terminal and idempotent |
| `Update` | `particles`, accumulated `logEvidence`, `report`; returned detached record |
| `update.expectation` | `f: S => Double`; finite observable mean over retained values; `update.expectation(_.toDouble)` |
| `StepReport` | `attempts` counts all work; `observedSuccesses` includes stopping candidate; `retainedCandidates` is the estimator denominator; `retainedSuccesses`, `reason`, `targetMet`, `distinctParents`, `health` |
| `report.warnings` | Additional budget/one-parent warnings; immediate parent count is not whole-history diversity |
| `B.NoViableParticles` | Carries `report` and `logEvidence=-Infinity`; terminal no-posterior failure |
| `ParticleFilterHealth.Config` | `minEss=100`, `minRelativeEss=.01`, `warnMaxWeight=.1`, `dangerMaxWeight=.5`; thresholds affect warnings only |
| `ParticleFilterHealth.assess` | Nonempty raw log weights, optional config and eligibility; returns report with counts, ESS, largest weight, issues and recommendations |
| `pf.healthConfig` | Existing filters expose an overridable threshold configuration; no constructor/default sampler changes |
| `pf.lastHealth` | Optional pre-resampling report, readable after zero-weight failure; health `attempts` for bounded updates counts retained candidates, while `StepReport.attempts` records total work |
| `ParticleFilter.NoViableParticles.diagnostics` | Optional fixed-filter report; legacy no-argument exception constructor preserved |

`StopReason` is `InitialBatch`, `SuccessTarget` or `BudgetExhausted`. Health
`Eligibility` is `Unknown` or `IndicatorContract`; `Issue` exposes a `Code`, `Severity`
and message. A report without issues is not an accuracy certificate.

## Validation and related modules

[BoundedParticleFilterTest](../Figaro/src/test/scala/com/cra/figaro/test/modernization/BoundedParticleFilterTest.scala)
enumerates finite stopping trees and a two-step HMM, and checks boundary, lifecycle,
RNG, fixed-filter diagnostics and graph ownership contracts.
[BoundedParticleFilterStudy](../Figaro/src/test/scala/com/cra/figaro/test/modernization/BoundedParticleFilterStudy.scala)
compares a separate fixed bootstrap reference against adaptive runs on two synthetic,
exactly solvable HMMs. A separate 500-run pilot sets a near-matched expected-work
fixed budget; 4000 evaluation runs per method/scenario include every zero outcome.
Six-standard-error smoke gates check evidence and evidence-weighted state moments,
not a universal calibration or speed superiority claim. See the
[execution record](CAPABILITY_EXPANSION_STATUS.md) for actual results.

Related: [inference health](INFERENCE_HEALTH.md), [7.0 migration bridge](INFERENCE_INTEGRATION.md),
[capability expansion](CAPABILITY_EXPANSION.md), and existing sequential/parallel
`ParticleFilter` for general likelihood evidence. No new production dependency.
