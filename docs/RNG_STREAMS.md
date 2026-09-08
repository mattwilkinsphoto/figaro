# Reproducible logical random streams

## Overview

`RandomStreams` assigns an owned generator to each logical chain or importance
worker **before scheduling starts**. It records how to recreate that stream and,
with opt-in `PartitionedV1`, enforces a consumption limit. Choosing a good RNG and
allocating parallel streams correctly are separate responsibilities. This API
does not make shared Figaro model graphs thread-safe.

The default remains **LXM with `SeededV1`**: existing root seeds and logical indices
retain their previous sequences. Enable `PartitionedV1` for native stream allocation
and a hard raw-draw budget. Choose Philox when counter-based allocation is useful,
not because it is universally faster. The [literature review](RNG_LITERATURE_REVIEW.md)
explains the research basis.

## Quick start: three steps

1. Select a policy:
   ```scala
   import com.cra.figaro.util.{RandomStreams as RS, SamplingRandom as SR}
   val policy = RS.Config(RS.Allocation.PartitionedV1, maxRawDraws = 1000000L)
   ```
2. Allocate one stream per logical owner:
   ```scala
   val streams = RS.allocate(42L, 4, SR.Algorithm.Philox4x64, policy)
   val value = streams(0).random.nextGaussian()
   ```
3. Keep the descriptor with experiment settings:
   ```scala
   val descriptor = streams(0).descriptor
   assert(descriptor.replay().nextGaussian() == value)
   ```

Replay starts at draw zero. Persist descriptor fields, Figaro revision, model/data,
proposal, chain count and work limits yourself. There is no automatic result file
or portable mid-run checkpoint format.

## API reference

Names below are under `com.cra.figaro.util.RandomStreams` unless qualified.
Generated case-class accessors and `copy` have their usual Scala semantics.

| Public API | Parameters, return and behavior | Example |
| --- | --- | --- |
| `Allocation` | `SeededV1` or `PartitionedV1`; exact policy versions | `Allocation.PartitionedV1` |
| `Config(allocation, maxRawDraws)` | Policy defaults to `SeededV1`; positive Long limit defaults to 2^48 raw 64-bit words per stream. Limit is **ignored** by `SeededV1` | `Config(Allocation.PartitionedV1, 1000000L)` |
| `Config.validate(algorithm)` | Non-null `SamplingRandom.Algorithm`; returns Unit, throws `IllegalArgumentException` for unsupported combinations | `policy.validate(SR.Algorithm.Lxm)` |
| `allocate(rootSeed, count, algorithm, config = Config())` | Long root, nonnegative Int count, backend, policy; returns index-ordered `Vector[Stream]`. Zero count returns empty after validation | `RS.allocate(42L, 4, SR.Algorithm.Lxm, policy)` |
| `Stream.seed` | Historical Long seed **label**, not sufficient for partitioned replay | `streams(0).seed` |
| `Stream.random` | Owned `java.util.Random`-compatible generator: primitives, bounded draws, bytes, Gaussian, exponential and streams | `streams(0).random.nextDouble()` |
| `Stream.descriptor` | Immutable start-of-stream `Descriptor` | `streams(0).descriptor` |
| `Descriptor(rootSeed, index, algorithm, config, provider)` | Long root, nonnegative Int logical index, backend, policy and provider/JDK string; returns replay identity, not RNG state | Prefer obtaining `streams(0).descriptor` |
| `Descriptor.replay()` | No arguments; fresh `java.util.Random` at start. O(index) setup; rejects provider/JDK mismatch | `descriptor.replay().nextLong()` |
| `BudgetExceeded(limit)` | Exception with configured raw-word limit; extends `IllegalStateException` | `case e: RS.BudgetExceeded => println(e.limit)` |
| `SamplingRandom.Algorithm.Philox4x64` | Apache Commons RNG 1.7 Philox4x64-10; also works with existing backend selectors | `SR.seeded(42L, SR.Algorithm.Philox4x64)` |
| `MH.Config.randomStreams` / `MC.Config.randomStreams` | Final `RS.Config` field, default `SeededV1`; validated against backend | Examples below |
| `MH.ChainResult.randomStream` / `MC.ChainResult.randomStream` | `Option[Descriptor]`; runner results contain `Some` under either policy. Default `None` supports manual construction | `result.chains.head.randomStream.get` |
| `ParImportance.seededWithStreams(generator, numThreads, numSamples, seed, randomAlgorithm, streamConfig, targets*)` | Fresh-universe factory, positive workers/total sample budget, Long root, backend, policy, target references; blocking one-time sampler with `StreamProvenance` | Example below |
| `ParImportance.StreamProvenance.randomStreams` | Index-ordered `Vector[Descriptor]`, one per actual importance worker | `algorithm.randomStreams` |

Invalid settings fail before model callbacks. Allocation/replay honor interruption.
Partitioned generators reject `setSeed`; replay the descriptor instead. The existing
`SamplingRandom.seeded` factory remains reseedable.

## Three common patterns

### 1. Reproduce vector chains while changing CPU parallelism

Previously you selected only a backend and root seed. Now add an explicit policy;
keep logical `chains` fixed when changing physical `parallelism`:

```scala
import com.cra.figaro.algorithm.sampling.VectorSliceSampler as VS
import com.cra.figaro.algorithm.sampling.parallel.MultiChainVectorSliceSampler as MC

val config = MC.Config(
  sampler = VS.Config(VS.Method.Quantile, draws = 1000, seed = 42L,
    randomAlgorithm = SR.Algorithm.Philox4x64),
  chains = 4, parallelism = 2, randomStreams = policy)
def model(i: Int, seedLabel: Long): MC.Model =
  MC.Model(Vector(i.toDouble), x => -x.head * x.head / 2)
val result = MC.run(config)(model)
val serial = MC.run(config.copy(parallelism = 1))(model)
assert(result.chains.map(_.result.samples) == serial.chains.map(_.result.samples))
```

Use deterministic densities and initial points. The factory's seed is a compatibility
label; an RNG you construct from it is outside the allocated stream and budget.
Vector model callbacks/resources remain caller-owned.

### 2. Give graph MCMC an explicit, reproducible proposal

```scala
import com.cra.figaro.algorithm.sampling.FinalScheme
import com.cra.figaro.algorithm.sampling.parallel.MultiChainMetropolisHastings as MH
import com.cra.figaro.language.Flip

val settings = MH.Config(chains = 4, drawsPerChain = 1000, warmUp = 100,
  parallelism = 2, seed = 42L, randomAlgorithm = SR.Algorithm.Xoshiro256PlusPlus,
  randomStreams = policy)
val mh = MH.run(settings) { (universe, index) =>
  val coin = Flip(0.3)(using "coin", universe)
  MH.Model(Vector(MH.Observable("coin", coin)(b => if (b) 1.0 else 0.0)),
    proposal = Some(FinalScheme(() => coin)))
}
val identities = mh.chains.map(_.randomStream.get)
```

Construction and sampling share the owned chain stream and its limit. An explicit
proposal also avoids the existing default proposal's hash-dependent element selection.
**Stream replay does not guarantee arbitrary graph replay:** rebuilt element
identities/hash traversal can change draw consumption. This milestone preserves,
rather than changes, that legacy behavior. Different proposals can produce different traces.

### 3. Use bounded streams in blocking importance sampling

```scala
import com.cra.figaro.algorithm.sampling.parallel.ParImportance
import com.cra.figaro.language.Universe
import com.cra.figaro.language.Flip

def makeModel(): Universe = {
  val u = new Universe
  Flip(0.3)(using "query", u)
  u
}
val algorithm = ParImportance.seededWithStreams(
  () => makeModel(), 4, 10000, 42L, SR.Algorithm.Lxm, policy, "query")
val allocation = algorithm.randomStreams
try {
  algorithm.start() // blocks until owned workers finish
  println(algorithm.probability("query", true))
} finally algorithm.kill()
```

Existing `seeded`/`seededWithAlgorithm` calls still use `SeededV1`. Unlike the chain
runners, this importance API couples model copies and sample partitions to worker
count. Changing `numThreads` can change the estimate; it is **not** sample-addressed,
scheduling-independent importance sampling. Actual workers are capped by sample budget.

## Exact allocation contract and gotchas

| Policy/backend | Version-one mapping and separation claim |
| --- | --- |
| `SeededV1`, any backend | Serial `SplittableRandom(rootSeed).nextLong()` labels seed generators through existing `SamplingRandom.seeded`. Distinct labels are not a nonoverlap proof |
| `PartitionedV1`, Xoshiro256++ | JDK factory seeded by root; serial `copyAndJump()`. Starts are 2^128 raw words apart; each positive Long budget is smaller |
| `PartitionedV1`, LXM | JDK factory seeded by root; serial native `split()`. Literature-supported statistical separation, **not** formal disjoint intervals |
| `PartitionedV1`, Philox4x64-10 | Two serial `SplittableRandom(rootSeed).nextLong()` values form the key. Apache six-word constructor receives `[key0, key1, 0, 0, index, 0]`. Counter words are least-significant first. Apache increments before emitting: first block is `index * 2^128 + 1`; starts are 2^130 output words apart |

Philox uses the existing Commons RNG dependency, not a new custom PRNG. Plain
`SamplingRandom.seeded(..., Philox4x64)` uses that key expansion and four zero counter
words. Partitioned streams use JDK `RandomGenerator` default distribution transforms
over counted `nextLong` words, potentially different from native transforms. Record
the provider/JDK version. Seed expansion adds no entropy to the 64-bit root.

- Limits count raw words, **not samples**. Gaussian/ bounded generation, rejection,
  warm-up and construction can need multiple words. Default 2^48 is a safety ceiling.
- Exhaustion throws before consuming any word beyond the cap. Bulk calls can consume
  the remaining allowance before failing; there is no rollback. Owned chain runners
  clean up and report failure, not partial success.
- Same root/index/backend/policy means the same stream. Separate calls do not reserve
  addresses globally. Assign experiment roots deliberately.
- Allocation is O(count), replay O(index). No mid-run counters, caches or model state
  are saved. Cross-provider/JDK bitwise replay is not promised.
- Disjoint-interval claims concern indices under the **same Philox key**, or one
  Xoshiro jump sequence with enforced limits; they do not certify independence of
  different experiments or correctness of inference.
- Per-method synchronization protects RNG state, but sharing one instance between
  threads still makes draw assignment schedule-dependent. Use one owner per stream.
- PCG RXS-M-XS-64, MT19937 and legacy Java support `SeededV1` only. Native allocation
  does not fix poor proposals, inadequate mixing or importance-weight collapse.

## Verification and targeted timing

385 modernization tests across 35 suites pass locally. Checks include three published
[Random123 known-answer vectors](https://raw.githubusercontent.com/DEShawResearch/random123/main/tests/kat_vectors),
native-provider agreement, old sequences, replay, caps, reseeding refusal, cancellation
and all three parallel integrations. Philox fixtures account for Apache's
increment-before-first-block convention. This is integration verification, not a new
randomness certification.

Examples compile, thin/fat/source/API artifacts pass content and attribution checks,
and an isolated thin-JAR consumer verifies the artifact hash, resolves every backend
and exercises native allocation/replay and Philox vector chains. All 80 selected
Python evidence, documentation and artifact-validator tests pass. API references are
regenerated and local links verified. Four pre-existing Scaladoc warnings remain;
remote CI and branch integration are separate gates.

The [100-row benchmark](rng-philox-benchmark-results.csv) retains all ten predeclared
seeds, both backends, primitives and three inference budgets. It follows the
[original study's](RNG_ASSESSMENT.md) warmed, rotated method and model. Windows amd64,
Adoptium Java 17.0.4+8; adapter-inclusive, one host/JVM:

| Backend | 1M uniforms, median ms | 1M Gaussians, median ms | Importance, 8,000 samples, median ms |
| --- | --- | --- | --- |
| LXM | 8.33 | 9.50 | 75.37 |
| Philox4x64-10 | 10.85 | 12.01 | 78.55 |

Both met the predeclared mean-error target at 8,000 samples for all ten seeds.
Philox's complete inference time was about 4% higher here, not an established universal
difference. Ten seeds cannot establish calibrated 95% coverage. The benchmark measures
**seeded adapters**, not partitioned-allocation overhead or parallel scaling. It does
not justify replacing LXM as default.

```text
sbt "figaro / Test / testOnly com.cra.figaro.test.modernization.RandomStreamsTest"
sbt "figaro / Test / runMain com.cra.figaro.test.modernization.RngBenchmark philox"
python -B tools/summarize_rng_benchmark.py docs/rng-philox-benchmark-results.csv --profile philox
```

## Related and next work

[Backend selection](RNG_ASSESSMENT.md), [migration](MIGRATION.md),
[vector chains](MULTI_CHAIN_VECTOR_SAMPLING.md), [blocking importance](PARALLEL_PERFORMANCE.md)
and [roadmap](../ROADMAP.md). Portable checkpoints, deterministic default graph
traversal and per-sample counter addressing remain separate work. Better proposals
for concentrated posteriors remain the next substantive inference-performance priority.
