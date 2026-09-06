# Correlated Gaussian block proposals

## Overview: why this exists

`GaussianBlockProposal` is an opt-in proposal for Metropolis-Hastings (MH). It moves a specified group of constant-parameter Normal variables together, using a fixed covariance supplied by you. It does not change the model, likelihood, posterior, sampler defaults, or number of threads.

Use it when related parameters must move together to explore the posterior efficiently. For example, if evidence strongly favors `x approximately y`, independently changing only x tends to move off that narrow region. A covariance with positive off-diagonal entries proposes coordinated changes. Negative correlations can likewise be represented with negative off-diagonal entries.

This is a fixed multivariate Gaussian random walk: a matrix transforms independent Normal increments, then the increments are added to the current values. This is the standard construction described in [Geyer's Metropolis documentation](https://www.stat.umn.edu/geyer/mcmc/library/mcmc/html/metrop.html). The matrix supplied to Figaro is the **covariance**, not that transformation matrix; Figaro computes its Cholesky factor once.

The first supported surface is deliberately narrow: permanent, unobserved, unintervened `AtomicNormal` instances with constant finite means and positive finite variances. Compound Normals and subclasses are rejected. Automatic covariance estimation or adaptation is not included.

## How to know whether to enable it

Start with the default proposal and inspect ordered traces, cross-chain R-hat, effective sample sizes, and whether your precision target is reached. Low ESS despite many transitions, coupled with strong posterior correlations, is a reason to try blocking. Low acceptance alone is not enough to diagnose the problem; high acceptance can also accompany tiny, ineffective moves.

Choose small blocks of parameters that are strongly related by the posterior, not merely by their prior declarations. Estimate a candidate covariance from a **separate, adequately explored pilot run**, use domain knowledge, or use an analytic approximation where available. Keep it fixed during the production run. A pilot that missed a mode or explored poorly cannot supply reliable global geometry.

Compare several fixed scales on independent validation runs. To multiply increment SD by s, multiply the covariance by s squared. Too small means slow exploration; too large means more rejections. There is no universal best scale or acceptance target.

Measure effective samples per second **and** accuracy and uncertainty checks. Raw draw throughput alone ignores correlation; the role of ESS in Monte Carlo error is explained in the [Stan reference manual](https://mc-stan.org/docs/2_38/reference-manual/analysis.html). Even ESS can be misleading for missed modes, so inspect individual traces and use dispersed starts.

## Quick start in three steps

Install the Scala 3 snapshot as described in [Building](BUILDING.md). The current reliability branch uses `6.0.0-modern.8-SNAPSHOT`; the original blocked-proposals checkpoint was `6.0.0-modern.6-SNAPSHOT`.

1. Import the APIs:

   ```scala
   import com.cra.figaro.algorithm.sampling.GaussianBlockProposal
   import com.cra.figaro.algorithm.sampling.parallel.{McmcPrecision, MultiChainMetropolisHastings as MH}
   import com.cra.figaro.language.*
   import com.cra.figaro.library.atomic.continuous.Normal
   ```

2. Define a fresh model factory, with a switch for the comparison:

   ```scala
   def build(blocked: Boolean)(u: Universe, index: Int): MH.Model = {
     val x = Normal(0, 1)(using "", u)
     val y = Normal(0, 1)(using "", u)
     val difference = Apply(x, y, (a: Double, b: Double) => a - b)(using "", u)
     difference.addLogConstraint((v: Double) => -0.5 * math.pow(v / 0.15, 2))
     // Analytic posterior covariance for THIS example, scaled by a fixed 2.8.
     val v = 2.8 * 1.0225 / 2.0225
     val c = 2.8 / 2.0225
     val proposal = if (blocked)
       Some(GaussianBlockProposal(Vector(x, y), Vector(Vector(v, c), Vector(c, v))))
     else None
     MH.Model(Vector(MH.Observable("x", x)(identity), MH.Observable("y", y)(identity)), proposal)
   }
   ```

3. Compare equal-work runs:

   ```scala
   val config = MH.Config(drawsPerChain = 12000, warmUp = 2000, seed = 42L)
   val standard = MH.run(config)(build(false))
   val blocked = MH.run(config)(build(true))
   for ((label, result) <- Vector("standard" -> standard, "blocked" -> blocked)) {
     val d = result.diagnostics("x")
     println((label, d.mean, d.rHat, d.meanEss.map(_ / result.elapsedSeconds)))
   }
   ```

All other model and runner settings are unchanged. The blocked run uses the same number of workers as the standard run. Both posterior means should approach zero; compare diagnostics before trusting either answer. The covariance here is known analytically, not automatically learned by Figaro.

## API reference

Import `com.cra.figaro.algorithm.sampling.GaussianBlockProposal`. The only new public library function is:

```scala
def apply(elements: Seq[AtomicNormal], covariance: Seq[Seq[Double]]): ProposalScheme
```

| Parameter | Meaning | Validation |
| --- | --- | --- |
| `elements` | Ordered variables moved together | Nonempty, non-null, distinct, active permanent exact `AtomicNormal` instances in one universe; no observations/interventions; finite means and positive finite variances |
| `covariance` | Covariance of additive increments in the elements' **value units** | Square with one row/column per target, finite, exactly symmetric, and numerically positive definite; no silent regularization |
| Return | Chain-owned `ProposalScheme` | Covariance and factor are copied into immutable storage; no adaptation, threads, or draws are started by construction |

Example: `GaussianBlockProposal(Vector(x, y), Vector(Vector(0.5, 0.49), Vector(0.49, 0.5)))`.

Invalid construction throws `IllegalArgumentException`. Execution checks ownership, activity, observation/intervention status, and finite current/proposed arithmetic again. Unsupported sequential composition, `constraintsBound = true`, or unrepresentable arithmetic also fail explicitly. The multi-chain runner wraps worker failures in `ChainFailure` and performs its normal cleanup; ordinary MH callers remain responsible for cleanup on failure.

The scheme works as a whole proposal or inside a state-independent `DisjointScheme` mixture. It must not be a continuation after another proposal in `TypedScheme` or `UntypedScheme`. A block alone updates only its listed variables: it does not automatically discover or update all other stochastic nodes.

The compiler-derived [sampling API reference](api/com.cra.figaro.algorithm.sampling.md) includes the signature. Existing `MH.run`, `MH.runUntilPrecise`, `Model`, and `DisjointScheme` contracts are unchanged.

### What the acceptance rule does

In value coordinates, the proposal is `xNew = xOld + L * z`, where `L * transpose(L) = covariance` and z is standard Normal. Its reverse/forward proposal densities cancel because the covariance is fixed and the random walk is symmetric. The **prior densities do not cancel**.

Figaro stores each supported Normal as standard-Normal randomness. For each block member, its log-prior contribution is `-0.5 * (zNew^2 - zOld^2)`. The implementation adds those contributions in log space, then the existing MH engine updates dependent elements and adds the likelihood/constraint difference. There is one accept/reject decision for the whole move. Rejected values and randomness are restored, including early hard-condition rejection; rejected states remain in retained traces.

This differs from `ProposalScheme(x, y)`, which already proposes both variables but independently resamples their usual randomness. That existing joint prior proposal can itself be useful and is included in the comparison.

## Three common patterns

### 1. Replace a poor proposal without changing the inference budget

Use the quick-start `build` function and `MH.run(config)(build(true))`. Keep `chains`, `drawsPerChain`, warm-up, queries, and evidence unchanged while comparing with `build(false)`. This isolates proposal efficiency from the benefit of stopping earlier. Inspect every important query, not only the best-looking one.

### 2. Mix local blocks with updates for the rest of the model

```scala
import com.cra.figaro.algorithm.sampling.{DisjointScheme, ProposalScheme}
val mixed = MH.run(MH.Config(drawsPerChain = 10000)) { (u, _) =>
  val x = Normal(0, 1)(using "", u)
  val flag = Flip(0.3)(using "", u)
  val block = GaussianBlockProposal(Vector(x), Vector(Vector(1.0)))
  val scheme = DisjointScheme(
    0.7 -> (() => block),
    0.3 -> (() => ProposalScheme.default(using u)))
  MH.Model(Vector(MH.Observable("x", x)(identity),
    MH.Observable("flag", flag)(b => if (b) 1.0 else 0.0)), Some(scheme))
}
```

Without the fallback, flag would remain frozen. Fixed positive mixture weights give both kernels a chance to run. You can similarly mix multiple blocks; variables omitted from all choices still do not move. Never make mixture weights depend on the current sampled state without deriving the corresponding proposal correction; this API does not provide that correction.

### 3. Add precision stopping after checking that the block mixes

```scala
val stopped = MH.runUntilPrecise(
  MH.Config(drawsPerChain = 20000, warmUp = 2000),
  McmcPrecision.Config(relativeTolerance = 0.15,
    minDrawsPerChain = 2000, checkEvery = 2000))(build(true))
println(stopped.reason)
println(stopped.assessments)
```

Blocking changes how effectively each transition explores. Stopping controls how many transitions are spent. They are complementary, but `MaxDrawsReached` still means the requested precision was not established. In this policy the tolerance specifies **full interval width divided by posterior SD**, not relative error divided by the posterior mean.

The runnable [example](../FigaroExamples/src/main/scala/com/cra/figaro/example/BlockedProposalExample.scala) exercises all three patterns:

```sh
sbt "examples / Compile / runMain com.cra.figaro.example.BlockedProposalExample"
```

## Gotchas and boundaries

- A well-aligned covariance helps elongated, roughly Gaussian regions; a local block is not a general solution for separated modes. A broad/default mixture can help particular cases but is not a convergence guarantee.
- Do not copy the example covariance into an unrelated model. The matrix refers to increments in **value units**, and its row order must match `elements`. Covariance is not a Cholesky factor, and its diagonal is variance, not SD.
- Singular blocks, including perfect correlation, are rejected. Near-singular matrices may also fail numerical factorization. Choose a justified positive-definite approximation explicitly; there is no hidden jitter.
- Construction costs O(d^3) and stores O(d^2) numbers for a d-variable block; each proposal costs O(d^2), plus normal graph updates. Large blocks can be slower and reject more frequently.
- Construct one scheme inside each model factory; do not share model elements across chains. Callbacks must obey existing ownership/purity rules. Ordinary single-chain MH remains supported; arbitrary shared-universe concurrent use does not become safe.
- Normal subclasses, compound/dynamic block members, discrete block members, observed or intervened targets, online tuning, and the optional early-score optimization are not supported.
- Dependent evidence expressed through Figaro's graph is updated normally. Hidden dependencies in callbacks, model mutation during sampling, or changed prior definitions violate the model contract.
- Seeds identify streams, not universal bitwise results across separately allocated graphs. Existing hash-based universe traversal can alter multi-element initialization order. Exact worker-count/prefix regression checks use a deterministic initialization fixture; analytic correlated-model checks assess statistical correctness separately.
- Precision stopping still stores traces up to the configured cap and checks all monitored means. It does not certify tail probabilities, quantiles, or unseen modes.

## Validation and related modules

See [measured proposal comparisons](BLOCKED_PROPOSAL_VALIDATION.md) for repeated-seed accuracy, joint interval coverage, ESS/s, precision success, and time-to-precision results, including unfavorable cases. The benchmark is not an automatic tuning tool.

Related: [multi-chain MH](MULTI_CHAIN_MCMC.md), [precision stopping](STOPPING_CRITERIA.md), [prior validation](STOPPING_VALIDATION.md), [parallel importance](PARALLEL_PERFORMANCE.md), and [migration](MIGRATION.md).

[Pilot calibration](PROPOSAL_CALIBRATION.md) can now estimate a candidate matrix from separate pilot traces. It freezes the result before fresh production chains start; the manual API above and all of its target/ownership restrictions remain unchanged. Calibration is not automatic and can be rejected or cost more than it saves.
