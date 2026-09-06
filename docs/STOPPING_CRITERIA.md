# Stopping criteria: decisions versus estimation precision

## Overview

This module answers two different questions:

- `TruncatedSprt`: "Have observations supplied enough evidence to choose between two specified hypotheses?"
- `McmcPrecision` with `MultiChainMetropolisHastings.runUntilPrecise`: "Are the requested posterior means estimated precisely enough to stop sampling?"

The first implements an equal-variance Gaussian truncated sequential probability ratio test (TSPRT), plus a discrete KL-divergence utility. The second uses a Flegal-Gong-inspired fixed-width rule, the larger of batch-means and raw-mean ESS-based Monte Carlo standard error (MCSE), and multiple-chain mixing safeguards. Neither certifies convergence for arbitrary models. Feeding correlated, estimated KL values into a Gaussian test does not automatically preserve its nominal error rates.

Nothing is enabled automatically. Existing `MultiChainMetropolisHastings.run` and ordinary Figaro samplers keep their fixed-budget behavior. Opt in when you can state the precision you need and the maximum computation you can afford. Keep fixed sampling for fixed-work benchmarks, very small jobs where checking overhead dominates, or targets not covered by this mean-only policy.

## Quick start: three steps

1. Import the APIs and model types:

   ```scala
   import com.cra.figaro.algorithm.sampling.parallel.{MultiChainMetropolisHastings as MH, McmcPrecision}
   import com.cra.figaro.library.atomic.continuous.Normal
   ```

2. Supply a maximum budget and a full-interval-width tolerance:

   ```scala
   val stopped = MH.runUntilPrecise(
     MH.Config(drawsPerChain = 20000),
     McmcPrecision.Config(relativeTolerance = 0.10)
   ) { (u, _) =>
     val x = Normal(0, 1)(using "", u)
     MH.Model(Vector(MH.Observable("x", x)(identity)))
   }
   ```

3. Inspect the reason and assessments before using the results:

   ```scala
   println(stopped.reason) // PrecisionReached or MaxDrawsReached
   println(stopped.assessments("x"))
   println(stopped.result.diagnostics("x").warnings)
   ```

`MaxDrawsReached` means the requested criteria were not met, not that the sampler failed or converged. Successful return means workers have exited and model universes have been disposed. Warm-up and thinning still add transitions beyond the retained-draw budget.

Run the [complete example](../FigaroExamples/src/main/scala/com/cra/figaro/example/StoppingCriteriaExample.scala):

```sh
sbt "examples / Compile / runMain com.cra.figaro.example.StoppingCriteriaExample"
```

## API reference

All entry points are in `com.cra.figaro.algorithm.sampling.parallel`. The [compiler-derived reference](api/com.cra.figaro.algorithm.sampling.parallel.md) includes exact signatures and generated case-class functions.

### `TruncatedSprt.gaussian(...) : Design`

| Parameter | Meaning | Default |
| --- | --- | --- |
| `mean0`, `mean1` | Finite observation means under H0/H1; `mean1 > mean0` | Required |
| `observationSd` | Known common positive observation **standard deviation**, not variance or LLR SD | Required |
| `falseAlarmRate` | P(select H1 given H0), in `(0, 0.5)` | `0.05` |
| `missedDetectionRate` | P(select H0 given H1), in `(0, 0.5)`; not detection power | `0.10` |
| `terminalFalseAlarmFraction`, `terminalMissFraction` | Fraction of each error budget reserved for the terminal decision, in `(0, 1)` | `0.5`, `0.5` |

Returns immutable `Design` with observation parameters, `lowerBoundary`, `upperBoundary`, real-valued `nominalSamples`, upward-rounded integer `maxSamples`, and recomputed `terminalBoundary`. Invalid inputs, unrepresentable arithmetic, and limits above `Int.MaxValue` throw `IllegalArgumentException`; limits are not silently clipped. Example: `TruncatedSprt.gaussian(0, 1, 1, missedDetectionRate = 0.10)` specifies 90% detection power at H1, not 10%.

### `Design.initial : State` and `Design.advance(state, observation) : State`

`initial` returns zero observations, zero accumulated log likelihood ratio, and `Decision.Continue`. `advance` accepts a nonterminal state from the same design and a finite scalar observation. It returns a new immutable state with `samples`, `logLikelihoodRatio`, `decision`, and `atTruncation`. It does not mutate the input. Terminal/foreign states, nonfinite observations, and numerical overflow throw `IllegalArgumentException`.

The statistic is **log L(H1)/L(H0)**. Before `maxSamples`, values at or above the upper boundary select H1; values at or below the lower boundary select H0; otherwise continue. At `maxSamples`, only the terminal rule applies: values at or above `terminalBoundary` select H1, otherwise H0. Example: `val next = design.advance(design.initial, 0.2)`. Pass an observation, not an already calculated likelihood-ratio increment.

### `TruncatedSprt.klDivergence(p, q) : Double`

Inputs are nonempty `Seq[Double]` probability vectors with identical size/category order. Each must be finite, nonnegative, and sum to one within `1e-10`. Returns `D_KL(P || Q)` in nats. Zero mass in P contributes zero; positive mass in P against zero in Q returns positive infinity. No smoothing or renormalization is implicit. Invalid vectors throw `IllegalArgumentException`. Example: `TruncatedSprt.klDivergence(Vector(1.0, 0.0), Vector(0.5, 0.5))` returns `log(2)`; reversing those inputs returns infinity.

### `McmcPrecision.Config(...)`

| Parameter | Meaning | Default |
| --- | --- | --- |
| `relativeTolerance` | Target **full** confidence-interval width as a positive fraction of posterior SD | `0.05` |
| `absoluteTolerance` | Optional positive full-width target in observable units; overrides relative tolerance | `None` |
| `confidence` | Nominal simultaneous confidence across requested means, in `(0,1)` | `0.95` |
| `minDrawsPerChain` | Minimum retained work before stopping, at least 100 | `1000` |
| `checkEvery` | Additional retained draws per chain between checks, positive | `500` |
| `minBatches` | Minimum complete batches per chain, at least 10 | `20` |
| `maxRHat` | Maximum finite rank/folded split R-hat, greater than one | `1.01` |
| `minEssPerChain` | Minimum pooled bulk AND mean ESS, each divided by chain count | `100` |

Invalid settings throw `IllegalArgumentException`. Relative precision uses posterior SD, not the mean's magnitude, so a mean near zero does not collapse the target. Absolute tolerance applies to every observable; scale projections appropriately when their units differ.

### `McmcPrecision.evaluate(chains, config, simultaneousQueries = 1) : Assessment`

Takes two or more equal-length ordered finite traces, at least four draws each, a policy, and a positive count of simultaneously checked means. Returns `diagnostics`, `batchMeansMcse`, `fullWidth`, `targetWidth`, `penalty`, `batchesPerChain`, `criteriaMet`, and `failureReasons`; `mcseUsed` exposes the combined error estimate. Missing MCSE/width means insufficient information, not zero uncertainty. Example: `McmcPrecision.evaluate(traces, McmcPrecision.Config(relativeTolerance = 0.1))`. See the [reliability API reference](MCMC_RELIABILITY.md#api-reference) for all named failure reasons and examples.

Batch size is `floor(sqrt(n))`; each chain supplies `floor(n / batchSize)` complete batches. The final remainder is omitted only from batch variance estimation; all draws remain in the reported mean. Independent chain variance estimates combine into pooled-mean MCSE without concatenating boundaries. Confidence critical values use Bonferroni adjustment across observables. Since modern.8, full width is `2 * z * mcseUsed`, where `mcseUsed = max(batchMeansMcse, diagnostics.mcseMean)` requires both estimates to be finite and positive. A vanishing penalty `posteriorSD / totalDraws` is added before comparison with the target. The former batch-only rule is retained only as a historical validation comparison.

Every query must meet minimum work/batches, R-hat, bulk ESS, mean ESS, and precision. Constant batch sequences fail closed. Tail ESS is reported but is not a gate for this **mean-only** policy: some discrete-tail indicators are constant. This is not quantile precision certification.

### `MH.runUntilPrecise(config, precision)(build) : StoppedResult`

Uses the same factory/evidence restrictions as [fixed multi-chain MH](MULTI_CHAIN_MCMC.md). `drawsPerChain` is the maximum retained count per chain. Checks occur first at `min(minDrawsPerChain, maximum)`, then every `checkEvery` additional draws, including a possibly shorter final batch. Returns `result` (ordinary detached MH result), `reason` (`PrecisionReached` or `MaxDrawsReached`), final per-observable `assessments`, and number of `checks`.

Initialization and warm-up happen once. Chains persist between batches; the coordinator assesses equal-length prefixes after every chain finishes its batch. Workers never wait at an in-pool barrier, so fewer workers than chains is supported. Worker count does not change seeds or stopping prefixes for reproducible models. No persistence, restart, or progress callback is introduced. Failure/interrupt cleanup follows the fixed runner's contract. See quick start for an invocation.

## Three common patterns

### 1. Compare categorical beliefs

```scala
import com.cra.figaro.algorithm.sampling.parallel.TruncatedSprt
val changeInNats = TruncatedSprt.klDivergence(Vector(0.5, 0.5), Vector(0.6, 0.4))
```

Previously callers supplied their own zero-support conventions. Now those are explicit and tested. This value is an information measure, **not a p-value or stop decision**. A sequence of estimated divergences is not automatically independent Gaussian evidence. Continuous density estimation, bin selection, and smoothing are not supplied automatically.

For a KL/MDI test, choose calibrated design means satisfying `0 <= mean0 <= MDI < mean1`, supply the divergence observation SD to `gaussian`, and pass each finite divergence to `design.advance`. The API does not estimate those calibration parameters or infer a scientifically meaningful MDI threshold. Normal modeling of a nonnegative divergence is an approximation that must be validated for that stream. The Gaussian example below demonstrates the test mechanics under their stated assumptions, not automatic KL calibration.

### 2. Stop an independent Gaussian evidence stream

```scala
val design = TruncatedSprt.gaussian(0, 1, 1,
  falseAlarmRate = 0.05, missedDetectionRate = 0.10)
val rng = new java.util.Random(42L)
var state = design.initial
while (state.decision == TruncatedSprt.Decision.Continue) {
  state = design.advance(state, 1 + rng.nextGaussian())
}
println((state.decision, state.samples, state.atTruncation))
```

A fixed-sample approach consumes its whole budget. This test may decide earlier but has an explicit maximum/terminal rule. It reserves part of each error budget for that decision instead of merely cutting off an ordinary SPRT. Error-design approximations still need validation for the intended observation stream.

### 3. Compare fixed and precision-limited MCMC

Using the quick-start imports:

```scala
import com.cra.figaro.language.Universe
def build(u: Universe, index: Int): MH.Model = {
  val x = Normal(0, 1)(using "", u)
  MH.Model(Vector(MH.Observable("x", x)(identity)))
}
val budget = MH.Config(drawsPerChain = 20000)
val fixed = MH.run(budget)(build)
val adaptive = MH.runUntilPrecise(budget,
  McmcPrecision.Config(relativeTolerance = 0.10))(build)
println(fixed.chains.head.draws("x").size) // always 20000
println(adaptive.result.chains.head.draws("x").size) // at most 20000
println(adaptive.reason)
```

Fixed sampling spends its whole budget regardless of precision. Adaptive sampling can save draws on well-mixed models, but adds diagnostic work and may be slower on small or poorly mixing jobs. It does not improve proposals or rescue stuck chains. Compare end-to-end time and accuracy across repeated seeds, not just sample counts or one timing result.

## Gotchas

- **No blanket error-rate guarantee.** Gaussian TSPRT uses the source's approximate sequential-boundary design. Plug-in estimates, non-Gaussian KL values, dependence, repeated resetting, and selecting among many tests alter calibration.
- **MCMC coverage is asymptotic.** A functional CLT, suitable moments/mixing, and consistent long-run variance estimation are assumptions, not facts established by R-hat. Combining independent-chain batch estimates with practical guards does not establish a new finite-sample theorem.
- **No automatic KL-based MCMC termination.** Stable empirical distributions can come from stuck chains. The supported automatic policy targets scalar-mean precision; a KL-based MCMC adaptation remains validation work.
- **Different terminal semantics.** TSPRT has a designed terminal H0/H1 decision; precision-limited MCMC reports that its target was not met when its budget expires.
- **Full width is not half width.** A relative tolerance of 0.10 asks for an interval approximately +/-0.05 posterior SD, before allowing for the penalty.
- **Memory is still budgeted at the maximum.** Primitive trace buffers are preallocated at the cap; checkpoints create immutable snapshots. Early stopping saves transitions, not the initial allocation. `maxStoredValues` is not a total heap bound.
- **Checking intervals are not statistical batches.** `checkEvery` controls assessment timing; variance batch size derives from the full retained prefix. Warm-up is excluded, rejected states retained, and thinning does not imply independence.
- **Degenerate queries fail closed.** Deterministic observables can prevent all-query stopping; remove those from the stopping queries instead of interpreting missing MCSE as zero.
- **No old-tuple compatibility.** Means/SD describe observations; boundaries and missed-detection probability are named explicitly. Historical argument conventions are not a contract.

## Verification and related modules

`StoppingCriteriaRegressionTest` checks analytical terminal probabilities after rounding, mean-separation scaling, likelihood-ratio direction, asymmetric thresholds, immutable state, invalid inputs, zero support, seeded Gaussian risks, precision scaling/correlation, exact fixed/adaptive prefixes, worker-count independence, all-query decisions, stuck modes, and cleanup after later-batch failure. Statistical fixtures support the tested cases, not universal calibration.

The seeded Gaussian TSPRT experiment runs 10,000 repetitions per hypothesis at each separation, with observation SD 1, false-alarm target 5%, miss target 10%, and equal 50% terminal allocations:

| Mean separation | Observed false alarms | Observed misses | Maximum observations |
| --- | --- | --- | --- |
| 0.25 | 3.34% | 6.50% | 208 |
| 1.0 | 2.81% | 5.65% | 13 |
| 2.0 | 2.16% | 4.15% | 4 |

For the modern.8 precision policy, 100 replicated stationary Gaussian four-chain experiments at nominal 95% confidence covered the true mean in 98 runs with independent draws and 95 runs with AR(1) correlation 0.9. All reached their 0.15-relative-full-width target within 8,000 draws per chain; mean stopping counts were 1,000 and 3,515 respectively. The former batch-only rule covered 98 and 92 runs, with mean counts 1,000 and 2,960. These small experiments do not establish exact 95% coverage, cover nonstationary starts, or validate arbitrary Figaro models. Tests also deliberately reject separated-mode and constant traces. The [60-seed paired reliability audit](MCMC_RELIABILITY_VALIDATION.md) shows remaining severe curved-target failures despite passing checks.

The [representative-model validation report](STOPPING_VALIDATION.md) compares fixed and adaptive sampling across 50 seeds on seven Figaro workloads, including poorly mixed and deliberately trapped controls. It includes raw results, accuracy and coverage checks, timing limitations, and cases where checking adds overhead without saving draws.

Related: [multi-chain MH](MULTI_CHAIN_MCMC.md), [parallel importance](PARALLEL_PERFORMANCE.md), [migration](MIGRATION.md), and [API reference](api/README.md).

References: Tantaratana and Poor, [Asymptotic Efficiencies of Truncated Sequential Tests](https://doi.org/10.1109/TIT.1982.1056578); Blostein and Huang, [Detecting Small, Moving Objects in Image Sequences Using Sequential Hypothesis Testing](https://doi.org/10.1109/78.134399), equations (12)-(15); Flegal and Gong, [Relative fixed-width stopping rules for Markov chain Monte Carlo simulations](https://arxiv.org/abs/1303.0238).
