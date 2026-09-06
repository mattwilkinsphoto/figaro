# Parallel Monte Carlo performance

## Overview

This stage adds an opt-in, blocking importance sampler that owns a bounded thread pool, a separate model universe per worker, and a separate random stream per worker. It exists because the older parallel sampler shares Figaro's global random generator: adding cores can increase contention instead of throughput. Scala 3 and sbt 2 alone do not remove that runtime bottleneck.

This milestone originated on `modernize/parallel-performance` with snapshot `6.0.0-modern.3-SNAPSHOT`, based on the CI-green deprecation checkpoint `55adc816`. It is also included in the current `modernize/multi-chain-mcmc` branch and `6.0.0-modern.4-SNAPSHOT`, using Scala 3.9.0, sbt 2.0.8, and JDK 17. Existing sequential and legacy parallel factories remain available. This stage does not make arbitrary Figaro models or all algorithms thread-safe. The newer [multi-chain MCMC guide](MULTI_CHAIN_MCMC.md) describes the separate supported MH runner; historical measurements below still describe the original importance/benchmark milestone.

## Should I enable this in my application?

Start with ordinary `Importance(samples, target)` unless you have a reason to parallelize. The new sampler is a candidate when a **single fixed-budget inference job takes too long**, importance sampling is already appropriate for that model, and the machine has spare CPU capacity and enough memory for multiple model copies. Typical candidates are a batch analysis, an offline simulation, or a background calculation that can wait for one completed estimate. The benefit is potentially less elapsed time for the same total sample budget—not a different probabilistic model or an automatically more accurate answer.

Look for these conditions together:

- **Sampling dominates the wait.** Profile or time model construction, sampling, querying, and cleanup separately. Thousands of cheap samples that already finish quickly may not repay worker startup and coordination. More expensive samples or a larger budget provide more work to divide. There is no model-independent minimum sample count that guarantees a win.
- **There are spare cores.** A single inference job may benefit from two or four workers. A service already running many independent requests concurrently may have no spare CPU to exploit; adding a pool inside every request can make overall throughput and latency worse.
- **Replicated state fits comfortably in memory.** Workers each need their own universe, elements, and sampler caches. The same total sample count does not mean the same memory footprint. Expensive model construction is repeated serially, so it can consume the time saved by parallel sampling.
- **The model can be rebuilt independently.** Every factory call must construct an equivalent fresh model and apply the same evidence. Callbacks must not share mutable counters, model nodes, or other unsynchronized state. If those assumptions do not hold, keep the standard approach until ownership is made explicit.
- **A completed fixed-budget answer suits the workflow.** You do not need to query intermediate estimates from this sampler while it is still running. If progressive answers, pause/resume, or an application-managed sampling duration are the requirement, consider the existing anytime API instead.

Do not use more workers to compensate for an unsuitable inference method. Impossible evidence still has no useful posterior. Rare evidence can cause extensive rejection, and highly uneven weights can yield little statistical information despite many samples. Parallelism can process work faster; it does not by itself fix those statistical problems. For a small tractable discrete model, exact inference may be more appropriate than either sequential or parallel Monte Carlo.

### What does "blocking" actually mean?

When you call `a.start()`, the **calling thread waits** while the fixed-budget computation runs. On successful return, sampling is complete and that caller can query the results. In the new sampler, other threads perform the sampling work, but the caller still waits for them. "Parallel" does not mean "asynchronous" from the caller's perspective.

This is also how the existing **one-time** `Importance(samples, target)` behaves: its `start()` completes the requested work before returning. The change is from one sampler to coordinated, isolated workers—not from a nonblocking API to a blocking API. In contrast, `Importance(target)` without a sample count creates an **anytime** sampler: after startup/initialization returns, its worker can keep improving the estimate until the application stops or kills it. Anytime startup and queries can themselves wait for initialization or worker coordination; "anytime" is not a promise that every method returns immediately.

Blocking is convenient for a command-line report or batch pipeline: the next statement runs only after the inference result is ready. It is usually inappropriate on a UI event thread or an event-loop server thread. If such an application needs this sampler, dispatch the entire factory/start/query/cleanup operation to a deliberately bounded background execution facility and deliver the final result back to the UI/request owner. The sampler does not provide that application-level scheduling, a progress callback, or a future-returning facade for you. Do not query or mutate the same sampler concurrently while `start()` is running.

A sample budget is **not a deadline or accuracy target**. Rejected attempts and user callbacks can add work, and impossible evidence can prevent useful completion. The shutdown timeout described below is not an automatic 30-second inference timeout. Application cancellation must explicitly interrupt the thread waiting in `start()`, and callbacks still need to cooperate.

### How do I turn it on, and how do I know it is enabled?

Use this branch's library artifact and deliberately construct your algorithm with **`ParImportance.seeded(...)`**. There is no sbt flag, environment variable, global enable switch, or automatic CPU-count detection that changes an existing `Importance(...)` call into the new sampler. A Scala/JDK upgrade, adding `withRandomSeed`, or increasing a legacy parallel factory's thread argument does not select this API.

| What your code constructs | Execution and ownership | What the caller gets |
| --- | --- | --- |
| `Importance(samples, target)` | One fixed-budget sampler over one universe | Blocking `start()`, then queries using that target element; the simplest baseline |
| `ParImportance(makeModel, workers, samples, "query")` | Existing parallel-collection execution over generated universes; shared Figaro RNG/default-universe behavior is not newly isolated | Blocking `start()` and reference-based queries; existing incremental evidence-query interface |
| `ParImportance.seeded(makeModel, workers, samples, seed, "query")` | Bounded private pool, separate model and routed RNG per worker, scoped default universes during construction/sampling | Blocking `start()` and reference-based queries; evidence must be in the factory, with no incremental `probabilityOfEvidence` interface |
| `Importance(target)` | Existing anytime worker over one universe | Sampling continues after initialization; use its stop/query/resume/kill lifecycle when progressive estimates are needed |

The `.seeded` call is the explicit opt-in. Its name describes seed handling, but it selects the whole new execution/ownership implementation. `withRandomSeed(seed) { ... }` by itself only scopes randomness on the current thread; it creates no sampling workers.

For example, `numThreads = 4` and `numSamples = 1000000` request **1,000,000 total samples**, normally 250,000 per worker—not 1,000,000 on each worker. The effective worker count is capped at the sample budget. Setting one worker is valid, but does not promise an advantage over ordinary single-sampler importance sampling. More workers also do not automatically increase the budget or improve accuracy; compare estimator quality as well as elapsed time.

## Quick start: three steps

1. Build the checkout with `sbt "compile"` (see the [root quick start](../README.md#quick-start-three-steps) for installation).
2. Run `sbt "examples / Compile / runMain com.cra.figaro.example.ParallelSamplingExample"`.
3. Adapt the [complete example source](../FigaroExamples/src/main/scala/com/cra/figaro/example/ParallelSamplingExample.scala), retaining the fresh model factory and `try/finally` cleanup shown below.

## API reference

Imports: `com.cra.figaro.algorithm.sampling.parallel.ParImportance`, `com.cra.figaro.language.*`, and `com.cra.figaro.util.withRandomSeed`.

| Public entry point | Parameters | Returns / behavior | Example |
| --- | --- | --- | --- |
| `ParImportance.seeded(generator, numThreads, numSamples, seed, targets*)` | `generator: () => Universe` supplies a fresh, distinct, non-null model with evidence; positive `numThreads: Int` is the maximum worker count; positive `numSamples: Int` is the total budget; `seed: Long` is expanded into worker seeds; `targets: Reference[?]*` resolve in each model | `ParSampler & ParOneTime`; call blocking `start()`, query, then `kill()`. Factory rejects invalid counts and reused/null universes. It does **not** expose incremental `probabilityOfEvidence` | `val a = ParImportance.seeded(makeModel, 4, 80000, 42L, "query")` |
| `withRandomSeed[A](seed: Long)(body: => A): A` | Seed and synchronous computation | The body's result; routes Figaro's stable `util.random` to a scoped RNG, restoring the prior stream even on an exception | `val draw = withRandomSeed(42L) { com.cra.figaro.util.random.nextDouble() }` |
| `Universe.universe: Universe` | No parameters; implicit getter | Current default: thread-local inside seeded model construction/sampling, process-wide otherwise | `val current = Universe.universe` |
| `Universe.universe_=(value: Universe): Unit` | Replacement default universe | Changes only the current scope if present, otherwise the process default | `Universe.universe = new Universe` |

The existing `Universe.createNew()` creates and installs a new default through that setter; it does not clear an older model. The existing one-time `ParImportance.apply(generator, workers, samples, targets*)` now retains remainder samples and caps workers at the sample count; both old overloads reject nonpositive worker counts. The old factory does not provide the new isolation contract.

Queries use references, not elements from one worker: `a.probability[Boolean]("query", true): Double`, `a.expectation[Double]("query", identity): Double`, and `a.distribution[Boolean]("query"): LazyList[(Double, Boolean)]`. Register all targets at construction, query while active, and materialize finite lazy results before disposal. See the [full compiler-derived reference](api/README.md) for all inherited overloads and the [lifecycle guide](API_GUIDE.md#algorithm-factories-and-lifecycle) for `start/stop/resume/kill`.

The two added example entry points, `ParallelSamplingExample.main(args)` and `SamplingBenchmark.main(args)`, both return `Unit`; their full argument contracts are in the [examples API reference](../FigaroExamples/README.md#parallel-performance-examples). No additional public parallel-MCMC factory is introduced.

## Before and after: three explicit comparisons

These examples distinguish **changing execution** from **changing the model**. Run the Scala blocks in this section together, in order, in one Scala 3 application: the later comparisons reuse the functions from the first. All examples query a Boolean node named `query`. The tiny first model explains the API; it is not a claim that this particular run needs parallelism.

### A. The same posterior: standard importance versus the new sampler

First, define the model once so both approaches have the same probability and evidence:

```scala
import com.cra.figaro.algorithm.sampling.Importance
import com.cra.figaro.algorithm.sampling.parallel.ParImportance
import com.cra.figaro.language.*

def makePosteriorModel(): Universe = {
  val u = new Universe
  val query = Flip(0.3)(using "query", u)
  query.addConstraint(b => if (b) 0.8 else 0.2)
  u
}
```

**Standard approach:** create one model, pass its actual target element to `Importance`, and run the whole budget in that sampler.

```scala
def standardEstimate(makeModel: () => Universe, samples: Int): Double = {
  val u = makeModel()
  val query = u.getElementByReference[Boolean]("query")
  val a = Importance(samples, query)(using u)
  try {
    a.start() // this already blocks until the fixed-budget run finishes
    a.probability(query, true) // query the element from this one model
  } finally {
    if (a.isActive) a.kill()
    u.clear() // this helper owns the model it just created
  }
}

val standardPosterior = standardEstimate(() => makePosteriorModel(), 100000)
println(s"Standard posterior: $standardPosterior")
```

**New approach:** pass the model-building function to `.seeded`. It creates independent models and distributes the same total budget. Query by a name/reference because there is no single target element shared by all workers.

```scala
def parallelEstimate(makeModel: () => Universe, samples: Int,
    workers: Int, seed: Long): Double = {
  val a = ParImportance.seeded(makeModel, workers, samples, seed, "query")
  try {
    a.start() // caller still waits, while the workers sample concurrently
    a.probability[Boolean]("query", true) // combines results across models
  } finally {
    if (a.isActive) a.kill() // releases the pool and child sampler resources
  }
}

val parallelPosterior = parallelEstimate(
  () => makePosteriorModel(), samples = 100000, workers = 4, seed = 42L)
println(s"Parallel posterior: $parallelPosterior")
```

Both answers should be near `0.24 / 0.38`, approximately `0.63158`; they need not be identical. Both process a 100,000-sample budget. The parallel version divides it into four 25,000-sample budgets and combines sample weights, rather than simply averaging four normalized answers. It changes neither the prior probability nor the evidence formula.

The required application changes are therefore concrete: move model construction into a repeatable factory, give targets consistent names, choose a worker count and root seed, and replace element-based queries with reference-based queries. The `start -> query -> finally kill` structure stays the same. If the standard run already meets your latency needs, retaining it is reasonable; for this tiny model the extra machinery may not be worthwhile. Killing either sampler does not force garbage collection: do not retain unwanted models/results indefinitely.

### B. Already using legacy `ParImportance`? Compare the actual opt-in

If your code already has a fresh-model factory and reference-based queries, the source change is smaller. These two functions deliberately use the same factory, worker count, total budget, query, and cleanup pattern:

```scala
def legacyParallelPosterior(): Double = {
  val a = ParImportance(() => makePosteriorModel(), 4, 100000, "query")
  try {
    a.start()
    a.probability[Boolean]("query", true)
  } finally {
    if (a.isActive) a.kill()
  }
}

def seededParallelPosterior(): Double = {
  val a = ParImportance.seeded(() => makePosteriorModel(), 4, 100000, 42L, "query")
  try {
    a.start()
    a.probability[Boolean]("query", true)
  } finally {
    if (a.isActive) a.kill()
  }
}

println(s"Legacy parallel: ${legacyParallelPosterior()}")
println(s"Seeded parallel: ${seededParallelPosterior()}")
```

Here the important change is not "one thread becomes four": **both calls already request four workers**. The new call replaces shared RNG contention and legacy execution/default-universe handling with worker-local streams, scoped defaults, a bounded private pool, and explicit shutdown ownership. Merely changing `4` to `8` in the old call does not obtain those benefits.

This is not an unconditional drop-in replacement. If later code calls `probabilityOfEvidence(newEvidence)` on the legacy result, the new return type does not provide that interface. For posterior queries like this example, construct the required evidence inside each fresh model factory. Review any caller-owned shared state as well: using `.seeded` cannot isolate a mutable object captured by all factories or callbacks. Seed assignment is repeatable, but full result reproducibility has the limitations in [gotchas](#gotchas).

### C. A more expensive job: compare equal work before enabling more workers

Consider a simulation that samples 32 independent normal quantities and asks whether their sum exceeds zero. It has more model and RNG work per sample than the single-coin example. Reuse `standardEstimate` and `parallelEstimate` above; only the factory changes:

```scala
import com.cra.figaro.library.atomic.continuous.Normal

def makeAggregateModel(): Universe = {
  val u = new Universe
  val inputs = List.fill(32)(Normal(0.0, 1.0)(using "", u))
  val values = Inject(inputs*)(using "", u)
  Apply(values, (xs: List[Double]) => xs.sum > 0.0)(using "query", u)
  u
}

val standardRisk = standardEstimate(() => makeAggregateModel(), 200000)
val parallelRisk = parallelEstimate(
  () => makeAggregateModel(), samples = 200000, workers = 4, seed = 42L)
println(s"Standard aggregate probability: $standardRisk")
println(s"Parallel aggregate probability: $parallelRisk")
```

Both estimates should be near `0.5`. The parallel run still requests 200,000 samples total, not 800,000. This workload is a more plausible candidate for parallel execution, but the example's printed probabilities alone establish nothing about speed. The [measured grid](#measurements-and-interpretation) reports a benefit on one host; use the benchmark workflow below to test your own model and deployment constraints.

For a useful adoption decision, warm up each approach, repeat runs, and compare **total elapsed time including model construction and disposal**, estimator quality, CPU use, and memory. Hold the model, evidence, and total sample budget fixed; try 1, 2, and 4 workers before assuming 8 is better. Choose the smallest worker count that delivers a worthwhile repeatable improvement without unacceptable resource cost. For example, an offline job with idle cores may justify four workers, whereas the same job inside an already CPU-saturated request service may be better left sequential.

Do not compare a 200,000-sample standard run against four workers each taking 200,000 samples and call the result an equal-work speedup. Conversely, obtaining the same budget's answer sooner is a latency improvement—not proof that its estimate has become more accurate. If you spend the saved time on a larger budget, evaluate that new accuracy/time tradeoff separately.

## Common patterns

### 1. Estimate a posterior with independent importance workers

```scala
import com.cra.figaro.algorithm.sampling.parallel.ParImportance
import com.cra.figaro.language.*

def makeModel(): Universe = {
  val u = Universe.createNew()
  val query = Flip(0.3)(using "query", u)
  query.addConstraint(b => if (b) 0.8 else 0.2)
  u
}

val a = ParImportance.seeded(() => makeModel(), 4, 80000, 42L, "query")
try {
  a.start() // blocks until all workers finish
  println(a.probability[Boolean]("query", true)) // approximately 0.24 / 0.38
} finally {
  if (a.isActive) a.kill()
}
```

Factories run serially, so each worker owns independent mutable elements and caches. Sampling runs concurrently in the private pool. Worker counts are capped at the sample budget; remainder samples go to the first workers. Aggregation combines raw sample weights, not an unweighted average of worker posterior probabilities. This matters for evidence and uneven budgets. Each worker must model the same distribution; the API cannot verify that your factories are equivalent.

### 2. Give a synchronous replicate its own random stream

```scala
import com.cra.figaro.algorithm.sampling.Importance
import com.cra.figaro.language.*
import com.cra.figaro.util.withRandomSeed

def replicate(seed: Long): Double = withRandomSeed(seed) {
  val u = new Universe
  val query = Flip(0.3)(using "query", u)
  val a = Importance(50000, query)(using u)
  try {
    a.start()
    a.probability(query, true)
  } finally {
    if (a.isActive) a.kill()
    u.clear()
  }
}
val estimates = List(11L, 29L, 47L).map(replicate)
```

These are synchronous independent replicates. For a concurrent caller-owned executor, wrap each worker's entire synchronous computation in its own scope and use explicit universes everywhere, including dynamic model callbacks. `withRandomSeed` scopes randomness only, **not** the default universe. Do not assume an `Anytime` worker, `Future`, or child thread inherits this scope. Prefer the seeded importance factory when it meets your needs. The benchmark's independent Metropolis-Hastings chains illustrate a narrower static-model experiment, not a general-purpose concurrency abstraction.

### 3. Measure before choosing a worker count

Run in a dedicated JVM with explicit heap settings. From the repository root:

```sh
sbt 'set examples / Compile / run / fork := true; set examples / Compile / run / javaOptions := Seq("-Xms1G", "-Xmx4G", "-Djava.util.concurrent.ForkJoinPool.common.parallelism=8"); examples / Compile / runMain com.cra.figaro.example.SamplingBenchmark normal 200000 5 1,2,4,8 seeded'
```

Repeat with `legacy` and in another fresh JVM. Other workloads are `coin`, `evidence`, `mh`, and `rng`. All receive the same total budget across the requested worker counts. Two warm-up rounds have indices `-2/-1`; measured rounds start at zero. Worker order alternates to reduce ordering bias. One-worker importance uses ordinary `Importance`, with a synchronous RNG scope in seeded mode; it is the sequential reference, not a measurement of one-worker executor overhead.

For profiling, append `"-XX:StartFlightRecording=filename=target/parallel.jfr,settings=profile,dumponexit=true"` to that JVM option list. JFR recordings can contain host/process metadata: keep raw recordings private and share only reviewed aggregate results. Profiled and unprofiled times should not be compared as if instrumentation were free.

## Measurements and interpretation

The [measurement CSV](performance-results.csv) contains measured rounds from the final workload grid, with no machine paths or account metadata. Host: AMD Ryzen 9 9950X, 16 physical / 32 logical cores, Windows, Temurin 17.0.4, 1 GiB initial / 4 GiB maximum heap. Each workload/mode ran in a fresh JVM, with two warm-ups and five measured rounds. These are illustrative micro-models, not a production capacity forecast.

Median sampling time in milliseconds (setup, queries, and cleanup excluded):

| Workload / total budget | RNG mode | 1 worker | 2 | 4 | 8 |
| --- | --- | ---: | ---: | ---: | ---: |
| Sum of 32 normals / 200,000 | Legacy shared | 554.49 | 417.39 | 396.36 | 464.65 |
| Sum of 32 normals / 200,000 | Scoped | 633.77 | 358.04 | 288.90 | 281.34 |
| Bernoulli / 1,000,000 | Scoped | 174.31 | 106.71 | 107.67 | 106.48 |
| Weighted Bernoulli / 1,000,000 | Scoped | 203.64 | 120.75 | 119.58 | 121.05 |
| Independent MH chains / 1,000,000 retained samples | Legacy shared | 740.92 | 429.38 | 333.95 | 424.19 |
| Independent MH chains / 1,000,000 retained samples | Scoped | 757.83 | 406.52 | 242.51 | 218.05 |

The Gaussian eight-worker result is 1.65x faster than the same-count legacy mode and 1.97x faster than the legacy sequential reference. A separate profiled grid showed roughly 300 ms at eight scoped workers, so the direction of improvement repeated, though exact times vary. The slower scoped sequential result in the final grid is also retained: opt-in routing is not a promise of improvement for single-threaded execution.

For these tiny coin models, two workers already capture almost all the gain. The Gaussian workload allocates roughly 3.0–3.2 GB per measured run despite a much smaller live heap. Allocation, graph traversal, and collection work are now prominent constraints. Profile samples with `java.util.Random.next` as the top frame fell from 1,584/2,483 (63.8%) in the baseline grid to 212/1,437 (14.8%) in a separate scoped grid. Those are sampled stack proportions, not exact CPU attribution or a controlled causal percentage.

Independent MH chains show about 3.4x sampling throughput versus the legacy sequential reference at eight workers, but each chain also has 1,000 burn-in steps. The CSV includes estimator error and approximate effective sample size (ESS), so throughput need not be mistaken for statistical quality. Steps within each chain remain sequential. No convergence certification or cross-chain R-hat diagnostic is supplied.

Metric definitions and limits:

- `setupMs`, `sampleMs`, `queryMs`, `cleanupAndMetricsMs` divide elapsed time into phases; MH query includes trace aggregation/ESS calculation. Include all phases when estimating end-to-end cost.
- `cpuMs` is process CPU time for the whole run, not just sampling; Windows resolution is coarse. More CPU for the same wall time can be a worse resource tradeoff.
- `liveThreadAllocatedBytes` is a before/after thread-allocation-counter difference, sampled before sampler shutdown. Threads already terminated can be absent; it is a diagnostic lower-bound approximation, not an allocation-profiler replacement.
- `heapPoolPeakBytes` sums heap-pool peak usage after resetting counters. Pool peaks need not occur simultaneously; this is not process resident memory or an exact live-set measurement.
- `absoluteError` compares against the analytical answer for these particular models. It is not a confidence interval.
- Importance ESS is N for unweighted coin/normal models, and `(sum weights)^2 / sum squared weights` reconstructed from the known two-weight evidence model. MH ESS uses non-overlapping batch means with approximately square-root-N batches, per chain, summed and capped at retained N; short/constant traces return NaN. This approximation is not a general Figaro ESS API. Compare ESS per total elapsed second, not just samples per second.
- `rng` isolates shared versus local random draws; its speedup is not an inference speedup, and neighboring diagnostic seeds are not a proof of statistically independent streams.

## Gotchas

- **Ownership:** return a fresh universe for every worker and never share mutable model nodes, callbacks' mutable state, or caches. Distinct-universe checking cannot detect every shared external object. Do not mutate models while sampling or querying.
- **Seeds:** worker seeds are deterministically assigned using `SplittableRandom`; each worker uses `java.util.Random`. This is practical stream separation, not a proof of nonoverlap or a cryptographic RNG. Changing worker count changes streams and sample budgets. Hash/traversal order and floating-point aggregation mean a root seed does not promise bit-identical inference results across runs, JVMs, or worker counts.
- **Lazy/async work:** scoped randomness lasts only for the synchronous body on that thread. Consume lazy random results within the scope; a returned lazy function/list will otherwise use the stream current when evaluated. Other RNG instances are unaffected.
- **Legacy seeding:** `util.setSeed` sets the global RNG's initialization seed; it does not reseed an already initialized `util.random`. Use `withRandomSeed` for scoped runs. `util.random.setSeed` actually reseeds the current routed stream but should not race other unscoped users.
- **Lifecycle:** call `start`, query, then `kill` in `try/finally`; do not call lifecycle/query methods concurrently or use `initialize/run/cleanUp` hooks as substitutes. The seeded API is one-time, not anytime, and has no incremental evidence-query factory. Do not restart a disposed instance; build a new one.
- **Cancellation/failure:** interrupt the thread blocked in `start()` to request cooperative cancellation. Rejection retries check interruption, but arbitrary user callbacks may ignore it. Shutdown waits up to 30 seconds; an uncooperative daemon worker may outlive a failed shutdown. Ordinary worker exceptions are propagated after the submitted work finishes; this is not fail-fast cancellation of all sibling work. Construction failures undo registrations of already constructed children, without clearing caller model state.
- **Impossible evidence:** cancellation prevents an endless rejection loop from being uninterruptible, but it does not create a valid posterior. Zero-mass/invalid models still require diagnosis.
- **Capacity:** start with 1/2/4 workers, measure representative models, and avoid nesting pools. More workers replicate model memory and can increase allocation/GC and CPU cost with little wall-time gain. Queries reuse existing weight aggregation over the child samplers; the private sampling pool does not parallelize every query operation.
- **Scope of assurance:** general inference caches, learning, filtering, actors, and external callbacks have not received a universal thread-safety audit. The full historical test suite is not a green gate; see the [migration risks](MIGRATION.md#accepted-workarounds-and-remaining-risk).

## Verification checkpoint

Clean library, test, and example compilation passes with deprecations still treated as errors. The maintained 143-test acceptance selection passes, including the 16 new parallel regressions; those 16 also pass a follow-up check covering assertion-failure cleanup. All three runnable user examples pass. Documentation verification covers 11,237 public-method entries, local link targets, and 12 tooling unit tests. Coverage instrumentation passes the 19 probability/parallel checks; its 12.81% statement coverage describes only that smoke selection, not overall test coverage.

The broader historical selection and unresolved learning-statistics failure are recorded in the [engineering log](../MODERNIZATION.md#stage-7-parallel-monte-carlo-performance). Required CI additionally verifies clean reproducible binary packaging and local publication; check the branch's latest run before merging. Timing checks remain advisory and the stable baseline is not merged by this stage.

## Related and next work

`language` supplies model ownership, references, and default-universe lookup; `util` supplies scoped randomness and atomic hash allocation; `algorithm.sampling` supplies importance/MH kernels; `algorithm.sampling.parallel` coordinates workers and weight aggregation; `FigaroExamples` supplies runnable demonstrations and the benchmark. The [build guide](BUILDING.md) lists verification commands.

The [multi-chain MCMC milestone](MULTI_CHAIN_MCMC.md) now adds an opt-in MH runner with explicit ownership/seed contracts, retained per-chain traces, convergence diagnostics, failure cleanup, and a narrower audited cache path. Its documentation distinguishes that supported API from the original benchmark-only MH experiment above. Measured allocation/collection hot spots, broader models, and separately validated observation support remain candidates for further work.
