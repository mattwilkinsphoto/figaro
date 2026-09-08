# Choose an RNG by purpose

## Overview

`RandomSelection` lets you state the execution pattern instead of choosing an RNG
and stream-allocation policy separately. It resolves a transparent, versioned pair
once, before model construction. Nothing benchmarks your machine, examines posterior
answers, or switches algorithms during a run. These are research-informed engineering
defaults, **not a claim to identify the universally best RNG**.

Existing APIs retain their LXM/`SeededV1` defaults. The selector is opt-in and adds no
dependency. Its basis is the [literature assessment](RNG_LITERATURE_REVIEW.md), including
[Java's execution-pattern guidance](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/random/package-summary.html)
and the [counter-based Random123 architecture](https://www.thesalmons.org/john/random123/).
Existing [timing evidence](RNG_STREAMS.md#verification-and-targeted-timing) does not
justify runtime winner selection or a universal Philox speed advantage.

| V1 purpose | Resolved backend and allocation | When to choose it |
| --- | --- | --- |
| `GeneralInference` | L64X128MixRandom / `SeededV1` | Balanced default; preserve existing logical chain/worker seed expansion |
| `FixedChains` | Xoshiro256PlusPlus / `PartitionedV1` | Fixed logical MCMC chains with native jumps and enforced raw-word caps |
| `CounterRanges` | Philox4x64-10 / `PartitionedV1` | Explicit separated counter ranges per logical stream |

For an existing experiment, **replay its recorded descriptor instead of selecting
again**. Changing from `GeneralInference` to another purpose intentionally changes
random sequences. Neither the probability distribution family nor the number of CPU
cores uniquely determines the best RNG. Select by execution requirements.

## Quick start: three steps

1. Resolve the intended purpose:
   ```scala
   import com.cra.figaro.util.RandomSelection as RNG
   val selection = RNG.resolve(RNG.Purpose.FixedChains, preset = RNG.Preset.V1)
   println(selection.reason)
   ```
2. Apply both resolved fields to a runner config:
   ```scala
   import com.cra.figaro.algorithm.sampling.VectorSliceSampler as VS
   import com.cra.figaro.algorithm.sampling.parallel.MultiChainVectorSliceSampler as MC
   val config = selection.configure(MC.Config(
     VS.Config(VS.Method.Quantile, draws = 1000, warmUp = 100, seed = 42L),
     chains = 4, parallelism = 2))
   ```
3. Run, and retain the decision and descriptors with experiment settings:
   ```scala
   val result = MC.run(config) { (index, seedLabel) =>
     MC.Model(Vector(index.toDouble), x => -x.head * x.head / 2)
   }
   val descriptors = result.chains.flatMap(_.randomStream)
   println((selection.preset, selection.algorithm, selection.streams,
     selection.overridden, selection.provider))
   ```

The returned decision exposes its purpose, version, backend, allocation/budget,
requirements, override flag, reason and provider/JDK identity. Persist those fields
and the descriptors yourself; merely printing or holding them in memory does not
save an experiment. Include Figaro revision, data/model, proposal and run settings.

## API reference

Names are under `com.cra.figaro.util.RandomSelection`. Generated case-class accessors
and `copy` use normal Scala semantics; `Decision` has no public constructor or `copy`.

| API | Parameters and return | Example |
| --- | --- | --- |
| `Preset.V1` | Fixed mapping above; future recommendations require a new version | `preset = RNG.Preset.V1` |
| `Purpose` | `GeneralInference`, `FixedChains`, `CounterRanges` | `RNG.Purpose.CounterRanges` |
| `Choice(algorithm, allocation)` | Explicit non-null `SamplingRandom.Algorithm` and `RandomStreams.Allocation`; immutable override pair | `RNG.Choice(SR.Algorithm.Lxm, RS.Allocation.PartitionedV1)` |
| `Requirements(disjointIntervals = false, perSampleAddressing = false)` | Hard Boolean requirements; per-sample addressing currently always rejected | `RNG.Requirements(disjointIntervals = true)` |
| `resolve(purpose, preset = V1, explicit = None, maxRawDraws = 2^48, requirements = Requirements())` | Validates purpose/version/optional pair/positive Long raw-word cap/requirements; returns `Decision`. Throws `IllegalArgumentException` for invalid or unsupported requests | `RNG.resolve(RNG.Purpose.CounterRanges)` |
| `Decision.purpose`, `.preset`, `.algorithm`, `.streams` | Resolved purpose/version/backend and `RandomStreams.Config` | `selection.streams.allocation` |
| `Decision.requirements`, `.overridden`, `.reason`, `.provider` | Requested requirements, Boolean explicit-override flag, explanation String and provider/JDK String | `println(selection.reason)` |
| `Decision.allocate(rootSeed, count)` | Long experiment root, nonnegative Int logical count; returns `Vector[RandomStreams.Stream]` without selecting again | `selection.allocate(42L, 4)` |
| `Decision.configure(config: MH.Config)` | Non-null graph-chain config; returns copy with both RNG fields replaced, preserving other settings | `selection.configure(MH.Config(chains = 4))` |
| `Decision.configure(config: MC.Config)` | Non-null vector-chain config; returns copy with nested sampler backend and stream policy replaced | Quick start above |
| `Decision.importance(generator, numThreads, numSamples, seed, targets*)` | Fresh-universe factory, positive worker/total sample limits, Long root, references; returns blocking `ParSampler & ParOneTime & ParImportance.StreamProvenance` | Pattern 2 below |

`allocate`, `configure` and `importance` reject a provider/JDK mismatch. Configuration
does not allocate random streams or invoke model callbacks. `importance` constructs
its model copies immediately, after selection/provider validation; then use the
normal start/query/kill lifecycle. Raw-word caps are enforced only by `PartitionedV1`.

## Three common patterns

### 1. Fixed graph chains, with an explicit backend override when needed

```scala
import com.cra.figaro.algorithm.sampling.FinalScheme
import com.cra.figaro.algorithm.sampling.parallel.MultiChainMetropolisHastings as MH
import com.cra.figaro.language.Flip
import com.cra.figaro.util.{SamplingRandom as SR, RandomStreams as RS}

val chosen = RNG.resolve(RNG.Purpose.FixedChains,
  explicit = Some(RNG.Choice(SR.Algorithm.Philox4x64, RS.Allocation.PartitionedV1)),
  requirements = RNG.Requirements(disjointIntervals = true), maxRawDraws = 1000000L)
val mh = MH.run(chosen.configure(MH.Config(drawsPerChain = 1000, warmUp = 100))) {
  (universe, index) =>
    val coin = Flip(0.3)(using "coin", universe)
    MH.Model(Vector(MH.Observable("coin", coin)(b => if (b) 1.0 else 0.0)),
      proposal = Some(FinalScheme(() => coin)))
}
```

Omit `explicit` to get V1's Xoshiro jump policy. An override is recorded and is still
validated. LXM/`PartitionedV1` is allowed for fixed chains **without** a disjoint-interval
requirement: native splitting provides statistical separation, not that guarantee.
An explicit proposal avoids the existing default MH proposal's hash-dependent
element traversal; the selector does not fix arbitrary graph replay.

### 2. Counter ranges for blocking importance workers

```scala
import com.cra.figaro.language.{Universe, Flip}
val counters = RNG.resolve(RNG.Purpose.CounterRanges)
def makeModel(): Universe = {
  val u = new Universe
  Flip(0.3)(using "query", u)
  u
}
val algorithm = counters.importance(() => makeModel(), 4, 10000, 42L, "query")
val identities = algorithm.randomStreams
try {
  algorithm.start()
  println(algorithm.probability("query", true))
} finally algorithm.kill()
```

The selector chooses Philox and partitioned allocation together. It does **not**
make importance estimates invariant to worker count: workers still determine model
copies and sample-budget partitions. `CounterRanges` means stream ranges, not
per-sample addresses, distributed job coordination or GPU execution.

### 3. Replay the recorded stream, bypassing automatic choice

```scala
val original = chosen.allocate(42L, 4)(2)
val descriptor = original.descriptor
val expected = Vector.fill(20)(original.random.nextGaussian())
// Retain the descriptor plus the chosen decision fields for a later experiment.
val replay = descriptor.replay()
assert(Vector.fill(20)(replay.nextGaussian()) == expected)
```

Replay recreates the **start** of a stream, not its current position or an entire
model. Do not re-run the selector to recover an old choice after software changes.
Replay requires a compatible provider/JDK; it does not support portable checkpoints.

## Gotchas and rejection rules

- `FixedChains` requires `PartitionedV1`; use `GeneralInference` with an explicit
  seeded pair for compatibility. `CounterRanges` requires Philox/`PartitionedV1`.
- `disjointIntervals=true` accepts bounded Xoshiro jumps or Philox ranges. It rejects
  LXM splits and seeded allocation. If the purpose's preset cannot satisfy a requirement,
  resolution fails: it never silently changes the purpose or substitutes another pair.
  Pick the suitable purpose or supply a valid explicit pair.
- `perSampleAddressing=true` is rejected even for Philox. There is no CPU/GPU, dynamic
  distributed-task, cryptographic or portable-checkpoint preset in this milestone.
- The raw-word cap defaults to 2^48, applies per stream, and is not a sample count.
  Rejection sampling, initialization and warm-up consume words. It is ignored under
  `SeededV1` to preserve behavior. See [stream limits](RNG_STREAMS.md).
- Applying `configure` explicitly replaces existing RNG settings; it leaves all other
  settings alone. Later manually copying/changing those fields can invalidate the
  recorded selection. Keep the decision and final config together.
- GeneralInference preserves existing **logical-stream** seed expansion. Allocating
  one child with root 42 is not equivalent to `SamplingRandom.seeded(42)` directly.
- No selection is cached globally, inferred from model results, or changed with CPU
  worker count. A resolved decision is immutable and can configure multiple runs;
  reusing a root/index reuses a stream, not a globally reserved address.
- Decision metadata is separate from sampler results to avoid changing existing
  result shapes. Results retain exact stream descriptors; selection reasons are not
  automatically attached or serialized.

## Validation and related modules

Focused tests pin every mapping, explicit override/rejection behavior, default
compatibility, no consumption of the current RNG, raw-word caps, direct-provider
equivalence through stream allocation, descriptor replay, both chain runners and
blocking importance. The standalone thin-JAR consumer exercises the public selector.
No statistical tolerances or RNG benchmark claims are changed by this routing layer.

Local acceptance: 392 modernization tests across 36 suites and 80 selected Python
evidence/documentation/artifact checks pass. Examples compile; thin/fat/source/API
artifacts pass content and attribution checks. The independent consumer passes with
the published artifact's SHA-256 verified. Generated API freshness and local links
are checked. Four pre-existing Scaladoc warnings remain. Remote CI and branch
integration remain separate gates; this is not a published stable release.

```text
sbt "figaro / Test / testOnly com.cra.figaro.test.modernization.RandomSelectionTest"
```

[RNG selection research](RNG_LITERATURE_REVIEW.md), [named backends](RNG_ASSESSMENT.md),
[stream contract](RNG_STREAMS.md), [migration](MIGRATION.md) and [roadmap](../ROADMAP.md).
