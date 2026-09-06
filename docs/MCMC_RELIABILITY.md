# Understanding MCMC precision and reliability

## Overview

This milestone hardens Figaro's existing `McmcPrecision` policy; it does not introduce a different sampler. The previous policy sized intervals using batch-means MCSE alone, although Figaro also computed an ESS-based error estimate for the same scalar mean. Those estimates can disagree. A query could pass the width test even when the second estimate implied that more work was needed.

The policy now uses the **larger of the two valid estimates**. It also returns named failure reasons. This makes the reported width consistent with both existing error estimates and helps users understand unsuccessful runs. It cannot make a poorly explored posterior reliable: both estimates can miss the same unseen tail or mode.

These changes apply whenever you call `McmcPrecision.evaluate` or `MH.runUntilPrecise`; no new flag is required. Ordinary fixed-budget `MH.run`, proposals, calibration, RNG routing, and the Gaussian TSPRT are unchanged. For identical trace prefixes and settings, intervals cannot get narrower and precision cannot be established earlier than under the former rule. Some runs will take longer or reach their cap instead.

## Quick start in three steps

### 1. Monitor the quantities you actually need

```scala
import com.cra.figaro.algorithm.sampling.parallel.{McmcPrecision, MultiChainMetropolisHastings as MH}
import com.cra.figaro.language.*
import com.cra.figaro.library.atomic.continuous.Normal

def build(u: Universe, index: Int): MH.Model = {
  val x = Normal(0, 1)(using "", u)
  MH.Model(Vector(
    MH.Observable("mean", x)(identity),
    MH.Observable("secondMoment", x)(v => v * v),
    MH.Observable("tailProbability", x)(v => if (math.abs(v) > 2) 1.0 else 0.0)))
}
```

The policy assesses the mean of **each supplied projection**, not every property of the distribution. A precise estimate of `x` does not establish precision for `x*x`, an event probability, or a quantile.

### 2. Run with a bounded precision budget

```scala
val result = MH.runUntilPrecise(
  MH.Config(drawsPerChain = 20000, warmUp = 1000, seed = 81017),
  McmcPrecision.Config(relativeTolerance = 0.15)
)(build)
println(result.reason)
```

The tolerance specifies **full interval width divided by posterior SD**, not relative error divided by the mean. `drawsPerChain` is a cap, not a promise that precision will be achieved.

### 3. Inspect the decision and its evidence

```scala
result.assessments.toVector.sortBy(_._1).foreach { (name, a) =>
  println(s"$name: passed=${a.criteriaMet}, failed=${a.failureReasons}")
  println(s"batch MCSE=${a.batchMeansMcse}, ESS-based MCSE=${a.diagnostics.mcseMean}")
  println(s"used MCSE=${a.mcseUsed}, full width=${a.fullWidth}, target=${a.targetWidth}")
  println(a.diagnostics.warnings)
}
```

`MaxDrawsReached` means the all-query precision policy was not satisfied. `PrecisionReached` means the configured checks passed, **conditional on the sample being representative and the error estimators being adequate**. Empty failure reasons do not establish those assumptions. A numeric interval may still be available when mixing checks fail; its availability alone is not permission to rely on it.

The runnable [example](../FigaroExamples/src/main/scala/com/cra/figaro/example/McmcReliabilityExample.scala) shows a deliberately insufficient budget and a well-exploring reparameterized control:

```sh
sbt "examples / Compile / runMain com.cra.figaro.example.McmcReliabilityExample"
```

## API reference

### `McmcPrecision.evaluate(chains, config, simultaneousQueries = 1): Assessment`

Parameters are two or more equal-length finite scalar chains with at least four values, an existing `McmcPrecision.Config`, and a positive number of monitored queries. Returns an immutable assessment. Invalid policy/input raises `IllegalArgumentException`. Example: `val a = McmcPrecision.evaluate(traces, McmcPrecision.Config(relativeTolerance = 0.1), 3)`.

Configuration parameters and defaults are unchanged; see the [complete policy reference](STOPPING_CRITERIA.md#mcmcprecisionconfig). Minimum draws/batches, R-hat, bulk ESS, and raw-mean ESS still apply. Tail ESS remains reported rather than made into a new mean-policy gate.

### `Assessment.mcseUsed: Option[Double]`

Parameterless method returning `max(batchMeansMcse, diagnostics.mcseMean)` only when **both** are finite and positive; otherwise `None`. Example: `println(a.mcseUsed)`.

`batchMeansMcse` retains its original meaning and arithmetic. `diagnostics.mcseMean` is the existing raw-mean ESS-based estimate, not an estimate from rank-normalized bulk ESS. It is sometimes called the spectral estimate in the audit. Full width is now `2 * z * mcseUsed`, where `z` applies the existing Bonferroni adjustment across queries. The existing `posteriorSD / totalDraws` penalty is added to width only for the stopping comparison. Neither estimator is exact, and taking their maximum is a conservative consistency safeguard, not a new coverage theorem.

### `Assessment.failureReasons: Vector[McmcPrecision.FailureReason]`

A new immutable field containing all failed checks in stable evaluation order. Example: `a.failureReasons.contains(McmcPrecision.FailureReason.WidthTooLarge)`. For results produced by `evaluate`, `criteriaMet` is true exactly when this vector is empty. The case-class constructor adds this final parameter with a default empty vector for source compatibility with older construction calls; consumers should prefer evaluated assessments. Manually constructed/copied assessments do not enforce consistency between their supplied fields.

| Enum value | Meaning | Appropriate response |
| --- | --- | --- |
| `InsufficientDraws` | Retained work is below `minDrawsPerChain` | Increase the cap or wait for more retained draws |
| `InsufficientBatches` | Too few complete statistical batches | Supply longer traces; `checkEvery` is not batch size |
| `InvalidRHat` | R-hat is missing, nonfinite, or above the configured limit | Inspect chain disagreement, stuck states, and starting regions |
| `InsufficientBulkEss` | Missing/nonfinite/low bulk ESS | Improve exploration and reassess work required |
| `InsufficientMeanEss` | Missing/nonfinite/low raw-mean ESS | Investigate dependence in this particular projection |
| `UnavailableMcse` | Either error estimate, or the derived width, is unavailable | Inspect constant/rare-event traces and numerical range; do not interpret missing error as zero |
| `InvalidTargetWidth` | Target is nonpositive/nonfinite, or penalty is nonfinite | Check the projection's variation, units, and numeric range |
| `WidthTooLarge` | Valid width plus penalty exceeds the target | More work may help if exploration is credible; compare both error estimates |

All earlier assessment fields remain: `diagnostics`, `batchMeansMcse`, `fullWidth`, `targetWidth`, `penalty`, `batchesPerChain`, and `criteriaMet`. `fullWidth` has the deliberately more conservative semantics described above. Generated case-class/enum methods are listed in the [full API reference](api/README.md).

### `MH.runUntilPrecise(config, precision)(build): StoppedResult`

Takes existing work limits, a precision policy, and a factory creating independent equivalent models in the provided universes. Returns detached traces, final per-query assessments, check count, and `PrecisionReached` or `MaxDrawsReached`. Example: the quick start above. Factory/worker failures, interruption, and shutdown failures retain the [multi-chain runner's contracts](MULTI_CHAIN_MCMC.md); this milestone does not change scheduling or model ownership.

## Three common patterns

### 1. Understand a capped run before spending more time

```scala
if (result.reason == MH.StopReason.MaxDrawsReached) {
  result.assessments.filterNot(_._2.criteriaMet).foreach { (name, a) =>
    println(s"$name needs attention: ${a.failureReasons.mkString(", ")}")
  }
}
```

Only `WidthTooLarge` on otherwise credible traces suggests a straightforward work-budget issue. R-hat/ESS problems call for examining exploration; merely extending a badly trapped chain may not solve them. Do not remove a scientifically required observable or relax diagnostic thresholds just to get a successful stop label.

### 2. Check estimates at a fixed budget independently of stopping

```scala
val fixed = MH.run(MH.Config(drawsPerChain = 20000, warmUp = 1000, seed = 91017))(build)
val a = McmcPrecision.evaluate(fixed.chains.map(_.draws("secondMoment")),
  McmcPrecision.Config(relativeTolerance = 0.15), simultaneousQueries = 3)
println((a.diagnostics.mean, a.batchMeansMcse, a.diagnostics.mcseMean, a.fullWidth))
```

Repeated independent seeds, different credible proposals, and increasing budgets help expose estimates that drift or disagree. Do not pool pilot/training draws with production estimates. Agreement is evidence, not proof of discovering every mode. The [paired validation](MCMC_RELIABILITY_VALIDATION.md) uses known targets to measure coverage directly; ordinary applications usually do not know the truth.

### 3. Improve model coordinates when a curved target is hard to explore

For the specific curved target in the validation, an equivalent generative model is available:

```scala
import com.cra.figaro.algorithm.sampling.ProposalScheme
val control = MH.run(MH.Config(drawsPerChain = 12000)) { (u, _) =>
  val z = Normal(0, 1)(using "", u)
  val e = Normal(0, 1)(using "", u)
  val y = Apply(z, e, (a: Double, b: Double) => 0.4 * (a * a - 1) + 0.5 * b)(using "", u)
  MH.Model(Vector(MH.Observable("y", y)(identity), MH.Observable("ySquared", y)(v => v * v)),
    Some(ProposalScheme(z, e)))
}
```

Here joint prior resampling redraws the independent latent coordinates, giving independent target draws after transformation. That statement is specific to this no-extra-evidence model. Additional evidence changes its posterior and generally removes that property. Reparameterization must preserve your actual target and any required density/Jacobian corrections; copying this example into an unrelated model is not automatic tuning.

## Gotchas: when the stop label is not enough

- The [paired curved-target audit](MCMC_RELIABILITY_VALIDATION.md) found joint coverage in only 5 of 34 default-proposal runs labeled `PrecisionReached` under the new rule, and 18 of 54 joint-prior successes. These selected subsets differ from the former rule's successes. This is a serious remaining limitation, not a certified operating regime.
- Both MCSE estimators can underestimate uncertainty in the same poorly explored sample. The floor closes an internal inconsistency, **not** the general tail/mode discovery problem.
- Increasing the number of chains or threads does not compensate for using a proposal that cannot explore important regions effectively.
- Rank diagnostics, mean diagnostics, and uncertainty in other functions are different questions. Monitor relevant transformed quantities explicitly; event-indicator means are probabilities, not quantile estimates.
- The policy remains asymptotic. Finite-sample validity depends on the target, proposal, initialization, moments, and variance estimation. Confidence adjustment across queries does not by itself give simultaneous validity over every time checkpoint.
- More conservative widths at fixed checkpoints do not mathematically guarantee better coverage after choosing a different stopping time. The audit separately measures fixed and stopped outcomes.
- No arbitrary MCSE-disagreement threshold or automatically relaxed guard was added. Compare the reported estimates and inspect the model when they diverge.
- The new field changes `Assessment` product/unapply arity. Recompile consumers and update seven-field pattern matches; see [migration](MIGRATION.md).

## Related and statistical context

[Stopping criteria](STOPPING_CRITERIA.md) defines the complete policy; [multi-chain MCMC](MULTI_CHAIN_MCMC.md) defines ownership and sampling; [pilot calibration](PROPOSAL_CALIBRATION.md) helps choose fixed block geometry; [reliability validation](MCMC_RELIABILITY_VALIDATION.md) records positive controls, adverse outcomes, and remaining limits.

Flegal and Gong establish relative stopping rules under sufficient conditions for **asymptotic** validity, rather than a blanket finite-run guarantee ([paper](https://arxiv.org/abs/1303.0238)). The distinction between raw-mean MCSE and diagnostics for other estimands is also explicit in the [posterior diagnostic reference](https://mc-stan.org/posterior/reference/diagnostics.html). The max-of-two safeguard here is a Figaro engineering change, not a claim that either source proves its finite-sample coverage.
