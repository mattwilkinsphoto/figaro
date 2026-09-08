# Scientific RNG backends and benchmark decision

## Overview

Figaro-owned sampling now defaults to **L64X128MixRandom (LXM)** rather than Java's
legacy 48-bit Random. Selectable alternatives support reproducible experiments and
cross-generator checks. This is not a guarantee against statistical anomalies:
the [validation study](STATISTICAL_VALIDATION.md) found importance-weight collapse
that changing RNGs does not cure.

| Selection | Exact implementation | Role |
| --- | --- | --- |
| `Algorithm.Lxm` | JDK L64X128MixRandom | Production default |
| `Algorithm.Xoshiro256PlusPlus` | JDK Xoshiro256PlusPlus | Fast alternative; explicitly ++, not + |
| `Algorithm.PcgRxsMXs64` | Commons RNG 1.7 PCG_RXS_M_XS_64 | Structurally different comparison |
| `Algorithm.MersenneTwister` | Commons Math 3.6.1 MT19937 | Established comparison/compatibility |
| `Algorithm.LegacyJava` | JDK java.util.Random | Explicit historical replay only |

No custom PRNG algorithm is implemented. MT uses the existing Commons Math dependency.
PCG adds Commons RNG Simple 1.7 and its Core/Client API transitive dependencies.
This PCG has a 64-bit state recurrence and 64-bit output; it is **not** NumPy's
128-state-bit PCG64 or PCG64DXSM. These noncryptographic backends are for simulation.

## Quick start: three steps

1. Import the API:
   ```scala
   import com.cra.figaro.util.{SamplingRandom, withRandomSeed}
   import SamplingRandom.Algorithm
   ```
2. Run synchronous model construction and inference within an owned stream:
   ```scala
   val answer = withRandomSeed(42L, Algorithm.Xoshiro256PlusPlus) {
     // Build a fresh model, run synchronous inference, query and clean up here.
     com.cra.figaro.util.random.nextGaussian()
   }
   // withRandomSeed(42L) selects LXM.
   ```
3. Record the seed and backend/provider/JDK alongside results:
   ```scala
   println(s"seed=42; ${SamplingRandom.provenance(Algorithm.Xoshiro256PlusPlus)}")
   ```

## API reference

| API | Parameters and return | Example |
| --- | --- | --- |
| `Algorithm` | Enum above; `.id` is the exact identifier | `Algorithm.Lxm.id` |
| `defaultAlgorithm` | Returns Lxm, not the unspecified JDK default | `SamplingRandom.defaultAlgorithm` |
| `seeded(seed, algorithm = defaultAlgorithm)` | Long seed, non-null backend; fresh reseedable Java Random-compatible adapter | `SamplingRandom.seeded(42L, Algorithm.PcgRxsMXs64)` |
| `scalaRandom(seed, algorithm = defaultAlgorithm)` | Same arguments; fresh owning Scala Random wrapper | `SamplingRandom.scalaRandom(42L, Algorithm.MersenneTwister)` |
| `provenance(algorithm)` | Non-null backend; identifier, provider/version and JDK text, not serialized state | `SamplingRandom.provenance(Algorithm.Lxm)` |
| `withRandomSeed(seed)(body)` | Long seed, synchronous computation; returns body result using LXM | `withRandomSeed(42L) { random.nextDouble() }` |
| `withRandomSeed(seed, algorithm)(body)` | Explicit backend; returns body result, restores previous scope even on exceptions | `withRandomSeed(42L, Algorithm.LegacyJava) { random.nextDouble() }` |
| `ParImportance.seededWithAlgorithm(generator, numThreads, numSamples, seed, randomAlgorithm, targets*)` | Fresh-universe factory, positive workers/total budget, root seed, backend, references; returns blocking one-time sampler | `ParImportance.seededWithAlgorithm(makeModel, 4, 100000, 42L, Algorithm.Lxm, "query")` |

Existing `ParImportance.seeded` keeps its signature and now selects LXM. Start/query/kill
lifecycle remains unchanged. `MultiChainMetropolisHastings.Config` and
`VectorSliceSampler.Config` gain a final `randomAlgorithm` field, default LXM.
Multi-chain vector sampling carries this through `Config.sampler`.

Adapters provide primitive, bounded, stream, Gaussian/exponential and reseeding methods.
MT uses Commons Math's Gaussian transform; PCG uses JDK RandomGenerator's default
Gaussian/exponential transforms over PCG bits. Provider and transform versions matter.

## Three common patterns

**Compare generators on a fixed problem:**

```scala
def estimate(a: Algorithm) = withRandomSeed(104729L, a) {
  // Replace this draw with fresh-model synchronous inference.
  com.cra.figaro.util.random.nextGaussian()
}
val comparison = Vector(Algorithm.Lxm, Algorithm.PcgRxsMXs64)
  .map(a => (a.id, estimate(a)))
```

Keep observations fixed and predeclare multiple seeds. Equal seed labels do not imply
identical draws across backends. Agreement is evidence, not proof; LXM shares xor-based
structure with Xoshiro, so the PCG comparison is particularly useful.

**Supply a modern RNG to a distribution kernel:**

```scala
import com.cra.figaro.library.atomic.continuous.GaussianDistribution
val rng = SamplingRandom.scalaRandom(42L)
val normal = GaussianDistribution(0.0, 1.0) // standard deviation
val draws = Vector.fill(1000)(normal.sample(rng))
```

An explicit `new scala.util.Random(seed)` remains legacy Java Random. Kernel APIs
honor caller-owned generators; Figaro cannot replace them silently. Older examples
using those constructors remain executable, but prefer the factory for new work.

**Select a backend for isolated parallel chains:**

```scala
import com.cra.figaro.algorithm.sampling.parallel.MultiChainMetropolisHastings as MH
val settings = MH.Config(chains = 4, parallelism = 2, seed = 42L,
  randomAlgorithm = Algorithm.PcgRxsMXs64)
// MH.run(settings)(buildIndependentModel)
```

Vector slice uses the corresponding field on VectorSliceSampler.Config; blocking
importance uses seededWithAlgorithm. MH/vector chain allocation does not change with
worker count. Importance worker-count changes still change streams and budgets.

## Gotchas and migration

- **Seeded sequences change under the new default. Recompile consumers.** Legacy
  replay is explicit, not a promise to reproduce arbitrary old asynchronous traversal.
- Every scoped/owned worker has a private instance. Shared fallback draws are synchronized
  per call, but that does not make model graphs or multi-call computations thread-safe.
- Chain/worker seeds are still assigned serially by SplittableRandom. This is seed
  derivation, **not native LXM splitting or Xoshiro jumping**, nor a proof of independent,
  nonoverlapping streams. Native split/jump allocation needs a future versioned replay
  contract and appropriate raw-draw budgets.
- PCG expands a Long seed using two consecutive SplittableRandom nextLong calls into
  the provider's native two-Long seed. Provenance calls this mapping
  SplitMix64-to-native-seed-v1. MT uses the Long constructor, which need not match another
  ecosystem's integer/array seed initialization.
- Applications must persist seed, algorithm, provider/JDK, Figaro revision, data/model,
  chain counts and budgets. The provenance helper supplies provider/JDK text; result
  persistence is not automatic. A seed alone is insufficient.
- Supported-runtime replay is tested; cross-JDK/version bitwise identity of modern
  transforms is not promised. Adapter serialization is not a supported checkpoint
  format. Portable generator checkpoint/resume remains future work.
- Scopes are synchronous: child threads, arbitrary futures and anytime workers do
  not inherit them. Use the explicitly configured owned parallel runners. Consume
  lazy random results inside the scope.
- `random.setSeed` reseeds the current scope or fallback. The older `util.setSeed`
  only changes the initialization seed variable, not an already initialized generator.
- A large period, distinct seeds, or passing application tests does not certify an RNG.
  No TestU01/PractRand battery was run locally in this milestone.

## Benchmark and decision evidence

The [complete CSV](rng-benchmark-results.csv) retains 250 measured rows: five backends,
ten predeclared seeds, million-draw uniform/Gaussian loops, and a real observed
Normal/Importance model at 2,000 / 8,000 / 32,000 draws. Three complete warm-up passes
precede measurement; backend order rotates each repetition. A volatile checksum
prevents primitive loops from being discarded.

The model has Normal(0, variance 1) prior and twenty unit-variance observations
alternating 1.1 and 1.9. The exact posterior mean is 30/21 and SD is sqrt(1/21).
The predeclared accuracy target is absolute mean error <= 0.1 posterior SD.
All five backends met it for all ten seeds at 8,000 draws.

| Backend | 1M uniforms, median ms | 1M Gaussians, median ms | Full inference, 8,000 draws, median ms |
| --- | --- | --- | --- |
| LXM | 9.27 | 10.71 | 73.54 |
| Xoshiro256++ | 9.39 | 10.67 | 73.07 |
| PCG RXS-M-XS-64 | 8.96 | 10.79 | 72.97 |
| MT19937 | 10.57 | 18.46 | 74.37 |
| Legacy Java Random | 7.14 | 33.13 | 72.64 |

These are **adapter-inclusive, single-host/single-JVM** measurements on Java 17.0.4,
Windows amd64, not JMH intrinsic-PRNG rankings or cross-hardware guarantees. Modern
adapters synchronize per call; legacy Random uses its own implementation.
Legacy uniforms were faster; LXM Gaussian draws were about 3.1x faster. Complete
inference timings were similar. Close modern-backend timings do not establish a
universal winner. Ten-seed coverage counts cannot establish calibrated 95% coverage.

LXM combines competitive measured performance with a modern larger-state design and
a supported JDK implementation, so it is the default. Xoshiro and PCG remain useful
alternatives, and MT needs no new MT dependency. No speedup is claimed for every model.

```text
sbt "figaro / Test / testOnly com.cra.figaro.test.modernization.SamplingRandomTest"
sbt "figaro / Test / runMain com.cra.figaro.test.modernization.RngBenchmark"
python -B tools/summarize_rng_benchmark.py docs/rng-benchmark-results.csv
```

RngBenchmark's optional `smoke` argument runs a short execution check. The validator
requires the full grid and rejects missing/duplicate rows, wrong seeds, invalid
timings, incorrect oracle errors and inconsistent accuracy flags. Timing is not a CI gate.

`StatisticalValidationStudy backends` separately compares all five backends over
thirty seeds and three budgets against fixed Gamma/Dirichlet quadrature references.
This is not a randomness certification or a replacement for inference diagnostics.
All [2,250 parameter rows](rng-statistical-results.csv) are retained. Across the five
backends, Dirichlet median ESS at 2,000 draws was 1.002-1.009 and at 200,000 draws
9.34-10.07. Proposal mismatch, not the choice among these generators, dominates.

The fat JAR discards dependency JPMS module descriptors, which cannot coexist as
separate modules in a single classpath assembly. Multi-release implementation classes
and dependency license/notice files remain. Thin-JAR consumers resolve normal dependencies.

## Research and related modules

- [Java RNG guidance](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/random/package-summary.html): named algorithms and workload/stream tradeoffs.
- [Xoshiro authors](https://prng.di.unimi.it/): ++/** versus plain + variants and jump functions.
- [PCG research](https://www.pcg-random.org/paper.html): permuted designs and stream-independence qualifications.
- [Apache PCG provider](https://commons.apache.org/proper/commons-rng/commons-rng-simple/apidocs/org/apache/commons/rng/simple/RandomSource.html): exact variant and native seed.
- [Apache MT](https://commons.apache.org/proper/commons-math/javadocs/api-3.6.1/org/apache/commons/math3/random/MersenneTwister.html): MT19937 implementation.
- [Statistical validation](STATISTICAL_VALIDATION.md), [parallel performance](PARALLEL_PERFORMANCE.md), [migration](MIGRATION.md), [roadmap](../ROADMAP.md).

Native split/jump allocation, checkpointing and counter-based Philox/Threefry are
future work, not hidden capabilities of these adapters.

## Local acceptance status

375 modernization tests across 34 suites pass, including native-provider agreement,
reseeding, nested scopes, both owned parallel-chain implementations and blocking
importance backend selection. The earlier statistical-helper-only control passed
368 tests before changing the default. No statistical seed or tolerance was changed
to obtain a pass: exact old-generator expectations were updated to the named default,
with separate explicit legacy-replay tests retained.

All examples compile. The independent thin-JAR consumer resolves and exercises every
backend from transitive dependencies. Thin/fat/source/API artifacts pass content/legal
checks; the fat JAR retains Commons RNG license/notice files and implementation classes
while dropping only conflicting module descriptors. Public API docs are regenerated.
Four pre-existing Scaladoc warnings remain. Remote CI/integration is a separate gate.

When reusing the same local SNAPSHOT coordinate after a dependency change, the consumer
may cache old dependency metadata even while loading the new Figaro JAR. The reused
acceptance consumer initially hit this and passed after `sbt "clean; update; runMain FigaroConsumerCheck"`.
For an application, refresh its generated build/resolution state after publishing the
new snapshot; do not manually add PCG dependencies to conceal stale metadata. If needed,
sbt documents `COURSIER_TTL=0s` for snapshot re-resolution. Prefer immutable versioned
artifacts for distributed releases. [sbt dependency guidance](https://www.scala-sbt.org/1.x/docs/Dependency-Management-Flow.html).
