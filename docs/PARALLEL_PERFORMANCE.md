# Parallel Monte Carlo performance

## Overview

This stage adds an opt-in, blocking importance sampler that owns a bounded thread pool, a separate model universe per worker, and a separate random stream per worker. It exists because the older parallel sampler shares Figaro's global random generator: adding cores can increase contention instead of throughput. Scala 3 and sbt 2 alone do not remove that runtime bottleneck.

The branch is `modernize/parallel-performance`, based on the CI-green deprecation checkpoint `55adc816`. Use snapshot `6.0.0-modern.3-SNAPSHOT`, Scala 3.9.0, sbt 2.0.8, and JDK 17. Existing sequential and legacy parallel factories remain available. This stage does not make arbitrary Figaro models or all algorithms thread-safe.

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

The next optimization candidates are measured allocation/collection hot spots and broader model benchmarks. A production multi-chain MCMC API would additionally need convergence diagnostics, explicit result/seed contracts, and a wider cache-ownership audit. Those are separate work, not claims made by this stage.
