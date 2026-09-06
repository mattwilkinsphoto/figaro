# Multi-chain Metropolis–Hastings

## Overview

`MultiChainMetropolisHastings` runs several independent MH chains, retains their ordered scalar draws, and reports diagnostics that help you judge whether those draws are useful. It adds a supported, blocking API around Figaro's existing transition kernel: you no longer need to write an executor, manage each sampler's lifecycle, collect traces yourself, or invent a convergence check.

This is an opt-in API in the Scala 3 modernization snapshot, not a new default for `MetropolisHastings`, a new proposal algorithm, or a declaration that all Figaro code is thread-safe. Each chain gets its own universe, model, caches, proposal state, and RNG. Steps within a chain remain sequential. Independent chains can run simultaneously.

Use it when you need **multiple-chain diagnostics and reproducible seed assignment**, with parallel execution as an additional benefit. Keep ordinary MH when its probability-query interface or anytime lifecycle suits your application. Use exact inference for small tractable models. Importance sampling remains useful when weights are well behaved; this API does not replace the [parallel importance sampler](PARALLEL_PERFORMANCE.md).

The initial supported evidence surface is **hard conditions and explicit likelihood constraints**. `observe()` is rejected, including observed dynamic children encountered by the cache. Do not simply remove observations from an existing model: that changes the inference question. See the evidence conversion example below.

## Quick start: three steps

Prerequisite: the modern Scala 3 Figaro artifact is on your classpath; see [installation](USER_GUIDE.md#installation-and-integration).

1. Import the entry points:

   ```scala
   import com.cra.figaro.algorithm.sampling.parallel.MultiChainMetropolisHastings.*
   import com.cra.figaro.library.atomic.continuous.Normal
   ```

2. Build a fresh model in the **supplied** universe for every chain:

   ```scala
   val result = run(Config(chains = 4, drawsPerChain = 10000, parallelism = 4)) { (u, _) =>
     val mean = Normal(0.0, 1.0)(using "", u)
     // A measurement y=1 with fixed variance=1.
     mean.addLogConstraint((m: Double) => -0.5 * (m - 1) * (m - 1))
     Model(Vector(Observable("mean", mean)(identity)))
   }
   ```

3. Inspect diagnostics **and** individual traces before using the estimate:

   ```scala
   val summary = result.diagnostics("mean")
   println(summary) // posterior mean should be near 0.5; posterior SD near sqrt(0.5)
   summary.warnings.foreach(println)
   val firstChain = result.chains.head.draws("mean")
   ```

The runner has already stopped workers and cleared its universes when it returns. There is no result-side `kill()` call. Do not retain model elements from the factory; retain the detached numeric results instead. Run the complete, checked [example](../FigaroExamples/src/main/scala/com/cra/figaro/example/MultiChainMcmcExample.scala):

```sh
sbt "examples / Compile / runMain com.cra.figaro.example.MultiChainMcmcExample"
```

## What changed compared with ordinary MH?

| Concern | Ordinary `MetropolisHastings` | New multi-chain runner |
| --- | --- | --- |
| Input | Existing elements/universe and proposal | Factory receiving a runner-owned universe and chain index |
| Work budget | Sample count for one sampler | Retained draws **per chain**, plus per-chain warm-up |
| Parallel work | Caller must coordinate independent samplers | Private pool; `parallelism` is separate from `chains` |
| Result | Active probability-query algorithm | Immutable per-chain scalar traces, metadata, diagnostics |
| Diagnostics | No automatic cross-chain diagnostic in the ordinary factory | Rank-normalized/folded split R-hat, bulk/tail/mean ESS, mean MCSE |
| Cleanup | Caller uses `start`, query, `kill` | Runner owns construction through disposal, including failure |
| Evidence | Legacy algorithm-specific behavior | Conditions and explicit likelihood constraints; no `observe()` |
| Sampling loop | Includes query histograms and target update maps | Records scalar columns directly from the existing MH kernel |

Four chains of 10,000 draws retain **40,000 draws**, not 10,000 total. With 1,000 warm-up transitions and `thin=1`, they attempt 44,000 MH transitions altogether. With one worker the four chains run sequentially; with four workers they can overlap. The chain count, warm-up, and retained work do not change when you change only `parallelism`.

## API reference

For related parameters that mix poorly, see [Gaussian block proposals](BLOCKED_PROPOSALS.md). Supply a chain-owned fixed-covariance scheme through `Model.proposal`; no runner configuration or default changes are required. Compare with existing joint prior resampling before assuming the new proposal is preferable.

For opt-in adaptive work budgets, see [stopping criteria](STOPPING_CRITERIA.md). `runUntilPrecise(config, precision)(build)` keeps chains alive between coordinated batches and returns an explicit precision-reached or budget-exhausted reason. Existing `run(config)(build)` remains fixed-budget; saved-result persistence and restart remain unsupported.

Import `com.cra.figaro.algorithm.sampling.parallel.MultiChainMetropolisHastings.*` and, for standalone diagnostics, `com.cra.figaro.algorithm.sampling.parallel.McmcDiagnostics`. The [compiler-derived reference](api/com.cra.figaro.algorithm.sampling.parallel.md) also lists all generated case-class operations, default accessors, parameter types, return types, and invocation templates.

### Configuration

`Config(...)` returns an immutable configuration. Invalid values throw `IllegalArgumentException` before any model is built. Use `config.copy(parallelism = 2)` to change a setting.

| Parameter | Type / default | Meaning and limits |
| --- | --- | --- |
| `chains` | `Int = 4` | At least 2 independent chains; prefer at least 4 for practical diagnosis |
| `drawsPerChain` | `Int = 10000` | At least 4 retained draws **in each chain**; four is an API minimum, not enough for dependable diagnostics |
| `warmUp` | `Int = 1000` | Nonnegative discarded transitions per chain; no automatic adaptation or convergence detection |
| `parallelism` | `Int = 4` | Positive maximum concurrent chains; pool size is `min(parallelism, chains)` |
| `seed` | `Long = 42L` | Root seed expanded in chain-index order using `SplittableRandom` |
| `thin` | `Int = 1` | Positive transitions per retained draw; normally leave at one |
| `maxInitializationAttempts` | `Int = 1000` | Positive bound on attempts to find a valid prior initial state |
| `maxStoredValues` | `Long = 10000000L` | Positive cap on `chains * drawsPerChain * observableCount`; not a byte or heap limit |

`warmUp + drawsPerChain * thin` must fit in `Int` because the inherited MH counters are integers. Observable storage is checked during construction before workers start. There is no duration limit on arbitrary user code; cancellation is cooperative.

### Model and observables

`Observable.apply[T](name: String, target: Element[T])(project: T => Double): Observable` defines a named scalar projection. `name` must be nonempty and unique within the model; `target` must belong to the supplied universe. `project` must be pure, synchronous, and return a finite number. It runs after each retained transition. For example:

```scala
val probabilityAboveZero = Observable("positive", mean)(m => if (m > 0) 1.0 else 0.0)
```

The public `name: String` accessor returns its result key. The target must be active when constructed and at every retained draw; a deactivated temporary child is not a valid persistent query. The target and evaluation function are intentionally not public query methods.

`Model(observables: Vector[Observable], proposal: Option[ProposalScheme] = None, initialState: () => Boolean = () => true): Model` defines the chain:

- `observables` must be nonempty; every chain must have the same names **in the same order**, representing the same quantities.
- `proposal=None` selects `ProposalScheme.default` for that universe. A custom scheme must use only chain-owned elements and define a valid, sufficiently exploring MH transition. For example, `Some(ProposalScheme(x, y))` proposes both nodes in sequence; this is not automatically better than the default.
- `initialState` is a pure predicate evaluated after prior initialization and validity checks. It selects a starting region; it is **not posterior evidence** and is not applied during retained sampling. Rejection initialization is bounded by `maxInitializationAttempts`. It does not let you supply arbitrary values inconsistent with element randomness.

All three fields have public accessors; `copy(...)` returns a modified definition. Construct the entire graph, attach evidence, and create proposals inside the factory. Do not call `Universe.createNew()` there or return nodes built elsewhere.

### Execution and errors

`run(config: Config)(build: (Universe, Int) => Model): Result` builds models serially, runs the fixed work, joins the worker threads, computes diagnostics, and returns complete output. The index passed to `build` is zero-based. The runner sets the supplied universe as the synchronous default during construction and sampling, so dynamic `Chain` children created normally stay in that chain. Explicit universes remain the clearest option where constructors accept them.

On a model/worker failure, `ChainFailure(chainIndex: Int, cause: Throwable)` identifies the chain through its public `chainIndex` accessor and retains the underlying exception in `getCause`. No partial `Result` is returned. Other workers are interrupted as soon as the completion queue reports the failure. Constructed but queued models are also disposed. Cleanup errors are attached as suppressed exceptions when there is an earlier failure.

To cancel, interrupt the **calling thread blocked in `run`**. The runner throws `InterruptedException`, preserves that caller's interrupt flag, interrupts its pool, and waits for cleanup. It checks cancellation between transitions and initialization attempts, not inside arbitrary callbacks or every diagnostic operation. A callback that ignores interruption can prevent shutdown; after the 30-second shutdown deadline the call fails, and an uncooperative daemon may remain alive. Its model is not concurrently cleared out from under it. Do not treat this as safe forced thread termination or a hard real-time timeout.

### Results

`Result(chains: Vector[ChainResult], diagnostics: Map[String, McmcDiagnostics.Summary], elapsedSeconds: Double)` has public accessors for all three fields. Chains are ordered by index; each map key is an observable name. Elapsed time includes construction, sampling, cleanup, and diagnostics. Results are immutable in-memory values; serialization/persistence and resume/checkpoint support are not provided.

`ChainResult` exposes:

| Field / return type | Meaning / example |
| --- | --- |
| `index: Int` | Zero-based identity, e.g. `result.chains.head.index` |
| `seed: Long` | Assigned RNG seed; record with configuration/model revision |
| `draws: Map[String, Vector[Double]]` | Ordered post-warm-up values, e.g. `chain.draws("mean")(100)` |
| `acceptanceRate: Double` | Accepted post-warm-up decisions / attempted transitions, including thinning; accepted no-change proposals count too |
| `initializationAttempts: Int` | Prior attempts needed to satisfy initial validity/region checks |
| `samplingSeconds: Double` | This chain's initialization, warm-up, and sampling time; excludes its disposal and pooled diagnostics |

Aligned draw indices preserve joint dependence: `chain.draws("x")(i)` and `chain.draws("y")(i)` refer to the same retained state. Rejected proposals repeat the current state. Removing repeats or keeping accepted moves only changes the empirical distribution and invalidates the diagnostics.

### Standalone diagnostics and summary fields

`McmcDiagnostics.summarize(chains: Seq[Seq[Double]]): McmcDiagnostics.Summary` accepts at least two equal-length chains, each with at least four finite draws. It rejects invalid dimensions or non-finite values with `IllegalArgumentException`. Input must already exclude warm-up and retain chain order/identity. It does not perform inference or dispose anything.

```scala
val checkedAgain = McmcDiagnostics.summarize(result.chains.map(_.draws("mean")))
```

`Summary` is an immutable case class with these public fields (and ordinary constructor/`copy` operations):

| Field / return type | Interpretation |
| --- | --- |
| `mean: Double` | Pooled mean of every supplied draw |
| `standardDeviation: Double` | Pooled sample SD: posterior spread, **not** uncertainty in the estimated mean |
| `rHat: Option[Double]` | Maximum rank-normalized and folded split R-hat; large values signal disagreement/drift/scale mismatch |
| `bulkEss: Option[Double]` | Rank-normalized split-chain effective sample size for the distribution's bulk |
| `tailEss: Option[Double]` | Minimum ESS of pooled 5th/95th-percentile indicator sequences, when both are defined |
| `meanEss: Option[Double]` | Raw-scale split-chain ESS used for estimating the mean's Monte Carlo error |
| `mcseMean: Option[Double]` | Estimated standard error `SD / sqrt(meanEss)`; not based on bulk ESS |
| `warnings: Vector[String]` | Short traces, constant chains, high R-hat, insufficient/undefined ESS, or numeric limitations |

`None` means unavailable, not zero and not success. All-constant traces have undefined diagnostics. Chains stuck at different constants yield infinite R-hat. Binary/discrete observables can have undefined tail ESS because a percentile indicator is constant. Four or five draws per chain permit R-hat but are too short for this ESS estimator. Odd-length chains omit their middle draw only for split diagnostics; pooled mean and SD still use every draw.

## Three common patterns

### 1. Replace a single-chain estimate with inspected multiple chains

The standard approach owns one model and queries a live sampler:

```scala
import com.cra.figaro.algorithm.sampling.{MetropolisHastings, ProposalScheme}
import com.cra.figaro.language.*

val u = new Universe
val x = Flip(0.3)(using "", u)
x.addConstraint((b: Boolean) => if (b) 0.8 else 0.2)
val sampler = MetropolisHastings(40000, ProposalScheme.default(using u), x)(using u)
try {
  sampler.start()
  println(sampler.probability(x, true))
} finally {
  if (sampler.isActive) sampler.kill()
  u.clear()
}
```

The new approach asks the same posterior question with four separately inspectable chains:

```scala
val posterior = run(Config(chains = 4, drawsPerChain = 10000)) { (u, _) =>
  val x = Flip(0.3)(using "", u)
  x.addConstraint((b: Boolean) => if (b) 0.8 else 0.2)
  Model(Vector(Observable("true", x)(b => if (b) 1.0 else 0.0)))
}
println(posterior.diagnostics("true")) // mean near 0.24 / 0.38 = 0.63158
```

Both retain 40,000 states, but the new default also discards 1,000 warm-up transitions **per chain**. This is a usage comparison, not an equal-work timing experiment. The new benefit is chain identity, diagnostics, scheduling, and managed cleanup—not a more exact answer just because there are four chains.

### 2. Express evidence explicitly and collect several related quantities

A common existing model uses an observed child, such as `Flip(p).observe(true)`. For this runner, integrate that observed Bernoulli child into the parent's likelihood: observing true contributes a factor `p`, observing false contributes `1-p`. This is a mathematical rewrite, not merely an API spelling change. For a continuous measurement, use its density, not an equality condition on a continuous draw:

```scala
val measurements = run(Config(drawsPerChain = 12000, warmUp = 2000)) { (u, _) =>
  val mean = Normal(0.0, 1.0)(using "", u)
  val observedValue = 1.0
  val knownVariance = 1.0
  mean.addLogConstraint((m: Double) => -0.5 * math.pow(observedValue - m, 2) / knownVariance)
  Model(Vector(
    Observable("mean", mean)(identity),
    Observable("square", mean)(m => m * m),
    Observable("positive", mean)(m => if (m > 0) 1.0 else 0.0)))
}
val posteriorMean = measurements.diagnostics("mean")
val probabilityPositive = measurements.diagnostics("positive")
```

The normalizer was omitted only because `knownVariance` is fixed. If variance is inferred, its normalizing term depends on the model and must be included. For multivariate likelihoods, attach a constraint to an `Apply` of all required parents. Check the rewrite against an analytical or exact-inference answer on a small case. Automatic observation conversion is not implemented; keep an existing supported inference algorithm if you cannot establish the equivalent likelihood.

### 3. Use dispersed starts, then tune workers without changing the statistical experiment

Starting every chain in the same region can hide an undiscovered mode. Use `initialState` to request different **prior** starting regions:

```scala
val config = Config(chains = 4, drawsPerChain = 10000, warmUp = 2000,
  parallelism = 2, maxInitializationAttempts = 10000)
val dispersed = run(config) { (u, index) =>
  val x = Normal(0.0, 1.0)(using "", u)
  x.addLogConstraint((v: Double) => -0.5 * (v - 1) * (v - 1))
  Model(Vector(Observable("x", x)(identity)),
    initialState = () => if (index % 2 == 0) x.value < -1 else x.value > 1)
}
```

This predicate selects the initial state only; retained chains still target the same posterior. Warm-up must be long enough to leave initial transients. Rare regions may exhaust the attempt limit. Do not use a chain-specific likelihood to create dispersed starts—that would give the chains different target distributions.

For a speed comparison, rerun the same factory with `config.copy(parallelism = 1)` and then `2` and `4`. Keep `chains`, draws, warm-up, thinning, observables, model, and root seed fixed. Chain-index seeds do not change with worker count. Figaro graph/hash traversal and floating-point behavior can still affect exact traces for complex models; this is not a cross-JVM bitwise reproducibility promise.

## How to decide whether a result is usable

Inspect each important parameter **and** the functions you plan to report (such as an indicator or nonlinear prediction). Plot ordered per-chain traces in your application's plotting tools. Look for persistent drift, chains staying in different regions, and slow movement. Acceptance rate alone is not a convergence diagnostic: an invalid/frozen proposal can look busy while missing a mode.

As screening rules, this implementation warns when R-hat exceeds 1.01 or bulk/tail ESS is below 100 times the original chain count. These are warning thresholds, not an automatic acceptance policy. Ask whether the MCSE is small enough for your actual decision: an estimated probability of 0.501 with MCSE 0.02 does not resolve which side of 0.5 it lies on. Error estimates assume meaningful exploration; excellent-looking summaries can still miss a mode that **no chain visited**. There is deliberately no `converged=true` field.

If chains mix but MCSE is too large, more retained work may help. If R-hat is poor or traces are stuck, examine initialization, proposal design, parameterization, and the model before buying more threads. More parallel chains do not make an individual chain jump between modes. Thinning saves stored draws but discards information per transition; it does not cure poor mixing.

The formulas follow rank-normalized/folded split diagnostics and multi-chain autocorrelation analysis described in the [Stan reference manual](https://mc-stan.org/docs/reference-manual/analysis.html). Tied midranks use the Blom transform `(rank - 3/8) / (S + 1/4)`. ESS uses zero-padded FFT biased autocovariances and Geyer's initial-positive, monotone paired sequence. **This implementation conservatively caps ESS at the split draw count**; it does not implement the improved antithetic termination/cap used by the [posterior reference implementation](https://github.com/stan-dev/posterior/blob/master/R/convergence.R). Expect close conceptual agreement, not identical numerical output to every Stan/posterior version. Tests include an independent Python-standard-library R-hat fixture and a direct, quadratic-time autocovariance/ESS oracle.

## Performance and resource planning

Run the dedicated benchmark in a fresh JVM:

```sh
sbt 'set examples / Compile / run / fork := true; set examples / Compile / run / javaOptions := Seq("-Xms1G", "-Xmx4G"); examples / Compile / runMain com.cra.figaro.example.MultiChainMcmcBenchmark wide 20000 5 1,2,4 4'
```

Arguments are workload (`normal`, `likelihood`, `correlated`, `wide`), draws per chain, measured repeats, comma-separated worker counts, and chain count. Defaults are `normal 20000 3 1,2,4 4`. Two negative-index rounds warm the JVM; measured rounds start at zero. The worker order alternates. `correlated` couples two latent normals tightly; `wide` queries a sum of 32 normals. The benchmark uses a HotSpot-compatible process CPU management bean and is not a wall-clock CI gate.

`wallMs` includes model construction, initialization, sampling, immutable result materialization, worker cleanup, and diagnostics. `sumChainMs` adds each chain's initialization/warm-up/sampling time; it is neither parallel elapsed time nor CPU time. `cpuMs` is process CPU time, including JVM work. `meanEssPerSecond` divides raw-scale mean ESS by end-to-end elapsed time. `absoluteError` compares against these particular models' analytical mean; it is not a confidence interval. Inspect R-hat before interpreting ESS/second.

The new loop avoids ordinary query histograms and per-draw target maps, but still uses the legacy MH graph update machinery. Model construction and pooled diagnostics are serial; rank sorting and FFT work can dominate tiny models. Running four chains on four workers cannot make the whole call four times faster if much of its time is outside concurrent sampling. Measure your model rather than treating the earlier benchmark-only MH speedups as a forecast for this richer API.

Memory grows with replicated models **plus** retained scalar values. The `maxStoredValues` count is not a memory reservation: vectors, maps, intermediate arrays, sorting, and FFT buffers add substantial overhead. Reduce observables/draws, increase heap deliberately, or consider a future streaming design. This version does not spill traces to disk or estimate memory consumption before construction.

### Measured checkpoint

The [reviewed measurement CSV](mcmc-performance-results.csv) contains five measured rounds per workload/worker count, after two warm-ups in a fresh JVM for each workload. Hardware: AMD Ryzen 9 9950X (16 physical / 32 logical cores), Windows, Temurin 17.0.4, 1 GiB initial / 4 GiB maximum heap. All cases use four chains, 20,000 retained draws per chain, 2,000 warm-up transitions, and one scalar observable.

Median **end-to-end** milliseconds, including diagnostics and cleanup:

| Workload | 1 worker | 2 workers | 4 workers | 1-to-4 speedup |
| --- | ---: | ---: | ---: | ---: |
| One normal | 155.07 | 134.94 | 127.79 | 1.21x |
| Normal plus likelihood | 183.89 | 144.22 | 129.80 | 1.42x |
| Tightly correlated pair | 211.57 | 147.90 | 116.69 | 1.81x |
| Sum of 32 normals | 204.81 | 159.98 | 149.02 | 1.37x |

For the likelihood case, median mean-ESS/second rises from about 204,235 to 300,645. The correlated case is an intentional counterexample: R-hat reaches about **1.35**, so faster completion does not establish a usable posterior estimate or reliable MCSE. Its low ESS/high R-hat points toward proposal/parameterization work, not just more workers. The other three workloads have maximum measured R-hat below 1.01, which is still a screening observation rather than proof of convergence.

These are short illustrative models, not production capacity forecasts. They do **not** establish a speedup over ordinary MH at the same worker count: the benchmark compares the new API against itself with fixed chain work. The earlier sampling-only MH experiment excludes costs that this benchmark deliberately includes. Keep that distinction when interpreting the smaller end-to-end gains here.

## Gotchas and audited boundaries

- Models and all mutable captured callback state must be independent. Read-only input data can be shared. The supplied universe belongs to the runner, including failure paths; do not use it after return.
- Static dependencies, observable ownership, cache-encountered dynamic children, and custom-proposal targets are checked. This cannot discover every shared external object or police malicious callbacks. Model/projection/constraint callbacks must not mutate evidence, spawn inference work, or call unrelated global caches while chains run.
- The audited path covers MH transition/proposal bookkeeping, `MHCache`, `ForwardWeighter` initialization, universe-local collections, scoped default universes, and the routed Figaro RNG. Factored-inference `Variable` caches, particle generators, learning, filtering, actors, nested inference, and arbitrary extensions are **not** certified for concurrent use by this milestone.
- Seed assignment is deterministic and independent of pool size. Each chain uses `java.util.Random`; different derived seeds are practical stream separation, not a mathematical proof of nonoverlap or a cryptographic guarantee. Explicit external RNGs and newly spawned threads are outside the runner's scopes.
- Evidence must already be attached when the model is returned. `observe()` is unsupported in this API. Zero-probability hard conditions, continuous equality conditions, and impossible/rare initial regions can prevent initialization. Positive-infinite or NaN likelihoods/projections are invalid; negative-infinite log likelihood denotes zero mass and is permitted for rejected proposed states.
- All chains must target the same distribution and observables. A factory can violate this semantic contract even if names match; validation cannot infer mathematical equivalence.
- Custom proposals remain your responsibility. The runner does not adapt proposals, validate detailed balance, provide HMC/NUTS, or guarantee movement between modes.
- The default proposal needs a stochastic element to propose. Use exact evaluation for a fully deterministic model; a constant projection inside a stochastic model is allowed but has undefined mixing diagnostics. Finite-sample R-hat can be slightly below one; closeness to one is not a guarantee either way.
- Diagnostics support finite `Double` scalar projections, not categorical distribution objects or arbitrary model snapshots. Extreme numeric ranges can lose precision; unrepresentable SD/MCSE is flagged. Constant/discrete trace warnings deserve interpretation, not automatic failure suppression.
- Cancellation is cooperative, not forcibly interruptible user code. There is no anytime querying, pause/resume, checkpoint recovery, automatic persistence, or partial-success result. Opt-in [precision stopping](STOPPING_CRITERIA.md) is available through `runUntilPrecise`; `run` remains fixed-budget.
- The modern artifact is still a development snapshot. Focused tests and CI do not make the full historical Figaro test suite green; see [remaining migration risks](MIGRATION.md#accepted-workarounds-and-remaining-risk).

## Verification checkpoint

The new suites pass 27 regressions, including known discrete/continuous posteriors, exact retained-state parity with ordinary MH in a controlled seeded case, rejection repeats, dynamic ownership, invalid queries/evidence, bounded initialization, cancellation, and failed-shutdown ownership. The broader local acceptance selection passes 185 tests (the maintained selection plus additional cache tests). These are focused checks, not a green full historical suite or a certification of arbitrary models.

The 30-test probability/MCMC coverage smoke selection passes instrumentation and reporting. Its 16.47% whole-library statement coverage describes that limited checkpoint, not overall test coverage. All guide snippets were compiled and run; the complete public-method reference contains 11,263 entries, and the 12 documentation-tool tests and local-link checks pass. Thin, fat, source, and API-documentation JARs preserve the legal files; binary artifacts contain neither test runtimes nor coverage instrumentation. Two fresh cache-bypassed builds reproduce all four publication JARs byte-for-byte, and local publication succeeds. Required GitHub CI repeats the release gates; check its latest run before merging this development snapshot.

## Related and next work

`language` supplies universe/element ownership and constraints; `algorithm.sampling` supplies the existing MH kernel and proposal schemes; `library.cache` supplies chain-local caching; `util` supplies scoped randomness; Commons Math supplies inverse-normal quantiles and FFTs; `FigaroExamples` supplies the runnable example and benchmark. See the [API guide](API_GUIDE.md), [parallel importance guide](PARALLEL_PERFORMANCE.md), and [build guide](BUILDING.md).

Next candidates are profiling real model workloads, reducing transition allocations, memory-bounded trace/diagnostic options, and separately validating observation support. Proposal improvements and multimodal exploration should be assessed using statistical efficiency, not raw thread count. Broad shared-graph thread safety remains separate work; this milestone uses isolated ownership instead.
