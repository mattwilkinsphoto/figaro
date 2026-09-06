# Pilot calibration for Gaussian block proposals

## Overview: why this exists

A Gaussian block can move correlated variables together, but choosing its covariance by hand requires knowledge of the posterior. `GaussianBlockCalibration` estimates a candidate from a **separate, discarded multi-chain pilot**. It returns an inspectable, immutable matrix that you bind to fresh model elements for production sampling.

The sequence is: pilot with an existing proposal, inspect/fit, then fresh production chains with a frozen proposal. The pilot runner already discards its own warm-up; its remaining draws train the proposal, **not the production estimate**. Production starts again, with a different seed and its own warm-up. Neither ordinary MH nor the multi-chain runner adapts its proposal while collecting production draws.

Enable this explicitly when a roughly elliptical continuous block mixes poorly and a representative pilot is affordable. It is not enabled by default. If you already know a useful covariance, the [manual block API](BLOCKED_PROPOSALS.md) avoids pilot cost. If default or joint-prior moves already explore efficiently, calibration can be slower overall. A local covariance is not a general solution for separated modes or curved ridges.

The [broader validation](PROPOSAL_CALIBRATION_VALIDATION.md) found substantial pilot rejection on difficult geometries and poor finite-run interval coverage on a curved target despite frequent precision success. This helper is not validated as an unattended automatic inference workflow for arbitrary posteriors.

## Quick start in three steps

### 1. Run a separate pilot with raw-value observables

```scala
import com.cra.figaro.algorithm.sampling.{GaussianBlockCalibration as Calibration, ProposalScheme}
import com.cra.figaro.algorithm.sampling.parallel.{MultiChainMetropolisHastings as MH, McmcPrecision}
import com.cra.figaro.language.*
import com.cra.figaro.library.atomic.continuous.Normal

def build(fit: Option[Calibration.Fit])(u: Universe, index: Int): MH.Model = {
  val x = Normal(0, 1)(using "", u)
  val y = Normal(0, 1)(using "", u)
  val difference = Apply(x, y, (a: Double, b: Double) => a - b)(using "", u)
  difference.addLogConstraint((v: Double) => -0.5 * math.pow(v / 0.3, 2))
  val proposal = fit.fold(ProposalScheme(x, y))(_.proposal(Map("x" -> x, "y" -> y)))
  MH.Model(Vector(MH.Observable("x", x)(identity), MH.Observable("y", y)(identity)), Some(proposal))
}
val pilot = MH.run(MH.Config(drawsPerChain = 6000, warmUp = 2000, seed = 71001))(build(None))
```

The pilot uses existing joint-prior proposals. This is a starting example, not an automatic choice for arbitrary models. Each factory invocation builds the same target distribution in its own universe.

### 2. Fit and inspect the frozen covariance

```scala
val fit = Calibration.fit(pilot, Vector("x", "y"))
println(fit.empiricalCovariance)
println(fit.covariance)
println(fit.config)
println(fit.diagnostics)
```

An inadequate pilot throws `IllegalArgumentException`, identifying a rejected observable when diagnostics fail. Inspect the reason and improve exploration or pilot budget. Do not mechanically relax the diagnostic thresholds to force acceptance. Passing these checks does not certify covariance accuracy or convergence.

### 3. Start fresh production chains

```scala
val production = MH.run(MH.Config(drawsPerChain = 12000, warmUp = 2000, seed = 81001))(build(Some(fit)))
println(production.diagnostics)
```

Only `production` estimates are reported. Do not append `pilot.chains` to them. The runnable [example](../FigaroExamples/src/main/scala/com/cra/figaro/example/ProposalCalibrationExample.scala) also demonstrates precision stopping:

```sh
sbt "examples / Compile / runMain com.cra.figaro.example.ProposalCalibrationExample"
```

## API reference

Import `com.cra.figaro.algorithm.sampling.GaussianBlockCalibration` (aliased `Calibration` above).

### `Config(...) : Config`

Immutable settings; invalid values throw `IllegalArgumentException`.

| Parameter | Default | Meaning and limits |
| --- | --- | --- |
| `varianceMultiplier: Double` | `1.0` | Positive finite scale applied to the regularized covariance. It scales **variance**, not step SD. No universal optimality is claimed. |
| `diagonalShrinkage: Double` | `0.05` | Fraction in `(0, 1]` removing off-diagonal covariance. `1` gives a diagonal proposal. Zero is deliberately unsupported. |
| `minDrawsPerChain: Int` | `500` | Minimum retained pilot draws in every chain; at least 20. Actual draws must also exceed block dimension. |
| `maxRHat: Double` | `1.01` | Maximum finite rank/folded split R-hat per selected coordinate; at least 1. |
| `minEssPerChain: Double` | `100.0` | Positive threshold for **each** of bulk, tail, and raw-mean ESS, divided by chain count. This is not a separate ESS estimate for each chain. |
| `maxDimension: Int` | `64` | Positive explicit block-size guard; not a recommended dimension. |

Example: `Calibration.Config(varianceMultiplier = 0.5, diagonalShrinkage = 0.1)`. Smaller variance generally gives smaller moves; whether that helps must be measured. Constructor defaults are transparent initial settings, not fitted tuning parameters.

### `fit(pilot: MH.Result, names: Seq[String], config: Config = Config()): Fit`

- `pilot`: a completed **fixed-budget** multi-chain pilot. At least two chains, aligned finite columns of equal length, and nonconstant values in every selected coordinate in every chain are required. The fitter recomputes diagnostics; it does not trust `pilot.diagnostics`.
- `names`: nonempty, distinct names of **raw Normal values**, in desired matrix order. Identity projections are appropriate; log-values, indicators, and squared values are not interchangeable with their underlying variables. The detached trace API cannot verify this semantic contract.
- `config`: the acceptance, shrinkage, and scale settings above.
- Returns a detached `Fit`; it stores no pilot elements or traces. There is no implicit production run, fallback, retry, or mutation of the pilot.
- Throws `IllegalArgumentException` on malformed/short/degenerate traces, rejected diagnostics, or unrepresentable covariance. Throws `InterruptedException` at computation checkpoints when the caller is interrupted, preserving its flag.

Example: `val fit = Calibration.fit(pilot, Vector("x", "y"), Calibration.Config())`.

### `fit.proposal(targets: Map[String, AtomicNormal]): ProposalScheme`

Supply an **exact** name-to-element map with the same target coordinates and units as the pilot. Returns a fresh fixed Gaussian block bound to these elements, ordered by `fit.names`, regardless of map iteration order. Missing/extra names, duplicate elements, and unsupported targets throw `IllegalArgumentException`.

Example inside each production factory: `val proposal = fit.proposal(Map("y" -> y, "x" -> x))`.

Only constant-parameter `AtomicNormal` targets supported by [GaussianBlockProposal](BLOCKED_PROPOSALS.md) are accepted. This method does not bind arbitrary scalar observables or extend support to dynamic/discrete variables.

### Returned fit fields

| Field | Meaning | Example |
| --- | --- | --- |
| `names: Vector[String]` | Matrix row/column order | `fit.names` |
| `empiricalCovariance: Vector[Vector[Double]]` | Unregularized pooled within-chain covariance | `fit.empiricalCovariance(0)(1)` |
| `covariance: Vector[Vector[Double]]` | Frozen increment covariance used by `proposal` | `fit.covariance(0)(0)` |
| `diagnostics: Map[String, McmcDiagnostics.Summary]` | Recomputed scalar pilot summaries | `fit.diagnostics("x").rHat` |
| `chains: Int`, `drawsPerChain: Int` | Pilot size used for fitting | `fit.chains * fit.drawsPerChain` |
| `config: Config` | Exact accepted settings | `fit.config.diagonalShrinkage` |

There are no setters or update methods. Generated language-level case-class methods are also listed in the [complete API reference](api/README.md).

### Covariance calculation

For `C` equally sized chains with `n` draws, sum each chain's scatter around **its own mean**, then divide by `C * (n - 1)`. Between-chain mean differences are not included as proposal variance; disagreement is assessed by the mixing checks. This is an empirical covariance from correlated draws, not an unbiased finite-run covariance guarantee or an uncertainty interval for that matrix.

With empirical covariance `S`, shrinkage `h`, and multiplier `m`, the frozen matrix is `m * ((1 - h) * S + h * diag(S))`. Positive diagonal shrinkage regularizes collinearity explicitly. There is no extra hidden jitter, clipping, or automatic retry. The result must pass the same numerical positive-definiteness checks as the manual block API. Binary coordinate scaling reduces intermediate overflow/underflow; unrepresentable final variances still fail with guidance to rescale.

## Three common patterns

### 1. Compare existing proposals with calibrated proposals fairly

Using `build` above:

```scala
val config = MH.Config(drawsPerChain = 12000, warmUp = 2000, seed = 81001)
val jointPrior = MH.run(config)(build(None))
val calibrated = MH.run(config)(build(Some(fit)))
println(jointPrior.diagnostics)
println(calibrated.diagnostics)
println(s"Existing: ${jointPrior.elapsedSeconds}s; calibrated production: ${calibrated.elapsedSeconds}s")
```

Hold production budget, evidence, observables, and worker count fixed. For a one-off answer, add pilot **and fitting** elapsed time to calibrated production time. Compare ESS/total second and errors/coverage, not acceptance rate alone. If you reuse a fit, report the number of uses and amortization explicitly; changing the model or evidence requires reassessment, not blind reuse.

### 2. Keep updates for variables outside the calibrated block

Inside a factory containing fresh `x`, `y`, and other variables:

```scala
import com.cra.figaro.algorithm.sampling.DisjointScheme
val block = fit.proposal(Map("x" -> x, "y" -> y))
val mixed = DisjointScheme(
  0.8 -> (() => block),
  0.2 -> (() => ProposalScheme.default(using u)))
// Pass Some(mixed) to MH.Model instead of Some(block).
```

A block alone never moves omitted variables. Fixed mixture weights can keep the rest of the model moving; they do not guarantee efficient traversal between modes. Do not make weights state-dependent through callbacks.

### 3. Use precision stopping only on fresh production draws

```scala
val stopped = MH.runUntilPrecise(
  MH.Config(drawsPerChain = 20000, warmUp = 2000, seed = 81001),
  McmcPrecision.Config(relativeTolerance = 0.15, minDrawsPerChain = 2000, checkEvery = 2000)
)(build(Some(fit)))
println(stopped.reason)
println(stopped.assessments)
```

The frozen proposal never changes at precision checkpoints. Pilot diagnostics do not replace production diagnostics. `MaxDrawsReached` is not a successful precision result. Monitor scientifically relevant functions as well as fitted coordinates; a pilot fit of `x` says nothing by itself about the precision of `x*x` or a tail probability.

## Gotchas and limitations

- Calibration needs a representative pilot. It cannot bootstrap reliable global geometry from chains trapped in one unseen mode. Scalar R-hat and ESS checks do not validate all linear combinations, covariance entries, or higher moments.
- A fit is a candidate proposal, **not a posterior covariance certificate**. Do not use it as a reported uncertainty interval or claim that shrinkage repairs poor exploration.
- Pilot rejection is a visible outcome, not missing benchmark data. Improve the pilot proposal, increase its budget, or deliberately keep an existing/manual proposal. Do not repeatedly try pilots until one passes and call that calibrated coverage.
- Independent production seeds, fresh universes, separate production warm-up, and pilot exclusion are caller responsibilities. The helper intentionally does not orchestrate or enforce the two runs. Do not feed an adaptively stopped production result back into the current production proposal.
- A `Fit` can be shared as immutable configuration; each call to `proposal` must receive fresh chain-owned elements. It does not make shared-universe sampling thread-safe.
- Names prevent accidental ordering errors, but cannot verify equivalent evidence, units, raw-value projections, or model semantics between pilot and production.
- Fitting takes O(C*n*d^2) scatter work plus scalar diagnostics and O(d^3) factorization. The existing runner retains pilot traces; release the pilot result when no longer needed. Production storage limits do not include a pilot result still referenced by your application.
- Extremely different units are handled where representable, not at all possible floating-point scales. Strong shrinkage may destroy useful narrow correlation geometry; tiny shrinkage may remain numerically inadequate. Both are explicit user choices.
- Existing multi-element initialization-order limitations remain: seeds do not promise universal bitwise reproducibility across independently allocated graphs. Exact regression checks use a single-coordinate deterministic initialization fixture.
- These are opt-in APIs. No default sampler, stopping policy, proposal, or existing configuration has changed.

## Related

[Calibration validation](PROPOSAL_CALIBRATION_VALIDATION.md) compares six posterior geometries, including unfavorable cases, with pilot-inclusive costs. [Fixed Gaussian blocks](BLOCKED_PROPOSALS.md) defines supported targets and MH correctness. [Multi-chain MCMC](MULTI_CHAIN_MCMC.md) owns isolation, scheduling, and trace diagnostics. [Stopping criteria](STOPPING_CRITERIA.md) assesses precision separately. [Migration](MIGRATION.md) records the additive release boundary.
