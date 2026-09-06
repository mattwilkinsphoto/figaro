# Bounded multi-chain continuous-vector sampling

For measured worker scaling, worst-coordinate ESS/s, and diagnostic overhead, see the
[baseline study](VECTOR_SAMPLING_PERFORMANCE.md) and the subsequent
[parallel diagnostic implementation](PARALLEL_VECTOR_DIAGNOSTICS.md). Public signatures are unchanged.

## Overview: what changed and when to enable it

`MultiChainVectorSliceSampler` runs independent GPSS or quantile chains on a private,
bounded worker pool, then calculates diagnostics for each coordinate. It calls the
existing [single-chain vector sampler](VECTOR_SLICE_SAMPLING.md) unchanged. This is an
opt-in orchestration API, not a new transition kernel or an automatic graph adapter.

Previously you had to assign independent seeds, schedule individual `VectorSliceSampler.run`
calls, collect results, join workers, and align traces for diagnostics yourself. Use this
runner when you need several independent chains for the **same** explicit continuous-vector
target and want that lifecycle handled together. Use the single-chain API when you want
direct caller-thread execution or already have orchestration with its own ownership rules.

Keep [MultiChainMetropolisHastings](MULTI_CHAIN_MCMC.md) for supported Figaro graph models.
The vector API still requires a complete deterministic log density and does not accept
`Universe`, `Element`, or `observe()` calls. Existing graph inference and stopping policies
are unchanged. This wrapper does not fix the [known exploration failures](SAMPLING_HIGH_DIMENSIONAL.md).

`chains` controls independent traces. `parallelism` bounds both sampling workers and
subsequent coordinate-diagnostic workers. Four chains and two workers means all four
chains run, two at a time; then at most two coordinates are summarized concurrently.
It does not halve the per-chain budget. Sampling and diagnostic pools never overlap.
Serial model construction, allocation, memory bandwidth and CPU contention still limit
gains; use the measured comparisons linked above rather than assuming linear speedup.

## Quick start (three steps)

1. Import the runner and choose the underlying method and **per-chain** work budget.
2. Supply a serial factory returning equivalent targets with suitable independent starts.
3. Check every chain's status, aggregate warnings, and each coordinate's diagnostic warnings.

```scala
import com.cra.figaro.algorithm.sampling.VectorSliceSampler as VS
import com.cra.figaro.algorithm.sampling.parallel.MultiChainVectorSliceSampler as MC

val result = MC.run(MC.Config(
  VS.Config(VS.Method.GPSS, draws = 2000, warmUp = 200, seed = 9301),
  chains = 4, parallelism = 2
)) { (i, _) =>
  MC.Model(Vector(i + 0.5, -i - 0.5), x => -x.map(v => v * v).sum / 2)
}
require(result.chains.forall(_.result.reason == VS.StopReason.DrawsReached))
println(result.warnings)
result.diagnostics.foreach(d => println((d.mean, d.rHat, d.warnings)))
```

Run the three complete workflows:

```sh
sbt --server --batch "examples / Compile / runMain com.cra.figaro.example.MultiChainVectorSamplingExample"
```

## API reference

### `run(config)(build): Result`

`config: Config` defines limits below. `build: (Int, Long) => Model` receives the zero-based
chain index and assigned chain seed. The factory runs serially on the caller's thread,
in index order, before any density callback or worker starts. It is invoked once per
chain unless construction fails or is interrupted. It must return the same target and
coordinate meaning/order for all chains; only starting points and private callback state
may differ. The runner validates dimensions, not mathematical equivalence.

The root is `config.sampler.seed`. A `java.util.SplittableRandom` expands it with one
`nextLong()` per chain, in index order. Each assigned seed replaces the single-chain
configuration's seed. Consequently the root itself is **not** the first chain's seed.
Changing worker count does not change seeded traces for deterministic equivalent models.
To reproduce a chain directly, use `config.sampler.copy(seed = chain.seed)` and that
chain's original initial point/density, not its returned `lastState`.

Returns all detached chain results only after owned workers terminate and are joined.
The quick start is a complete example. Invalid top-level arguments throw
`IllegalArgumentException`; invalid model output and factory/worker errors throw
`ChainFailure` with `chainIndex` and original `getCause`. A failure cancels siblings and
does not return partial success. A worker-thrown `InterruptedException` is a chain
failure; interruption of the caller or serial factory throws `InterruptedException`
and preserves/restores the caller's flag. Diagnostics run after sampling-worker shutdown,
using a separate bounded pool (or the caller when only one diagnostic worker is allowed).
Unexpected diagnostic errors propagate, never produce fabricated summaries.

### Configuration constructor and fields

| Field | Default | Meaning |
| --- | --- | --- |
| `sampler: VS.Config` | Required | Method, requested draws, warm-up, evaluation/search limits per chain; `seed` is the root |
| `chains: Int` | 4 | At least two independent chains |
| `parallelism: Int` | 4 | Positive worker limit: sampling uses `min(chains, parallelism)`; subsequent diagnostics use `min(dimension, chains, parallelism)` |
| `maxStoredValues: Long` | 40000000 | Positive cap on `chains * requested draws * dimension`, checked before sampling |
| `shutdownTimeoutMillis: Long` | 30000 | 1-30000 ms for each pool's executor termination and thread joins together; **not a run timeout** |

Example: `MC.Config(VS.Config(VS.Method.Quantile, draws = 1000), parallelism = 2)`.
`config.copy(parallelism = 1)` returns a revalidated configuration for serial scheduling.
Both aggregate and underlying per-chain storage limits apply. Neither is a total heap
bound; immutable-vector overhead, diagnostics, temporary arrays, callback allocations,
and factory resources use additional memory. Concurrent diagnostics multiply coordinate
scratch space by their worker count. All requested traces remain stored.

### Model and output constructors/fields

`Model(initial: Vector[Double], logDensity: Vector[Double] => Double)` defines one chain.
Initial vectors must be finite, nonempty, same-sized, and valid for the selected kernel;
initial log densities must be finite. Density checks occur on workers and count toward
their evaluation caps. Negative infinity means outside support. Other requirements are
the [single-chain density contract](VECTOR_SLICE_SAMPLING.md#api-reference), including
the correct measure/Jacobian and an integrable target. Example:
`MC.Model(Vector(1.0), x => if (x.head > 0) -x.head else Double.NegativeInfinity)` for quantile.

| Output | Fields and interpretation |
| --- | --- |
| `ChainResult` | `index: Int`, `seed: Long`, `result: VS.Result`; retains **all** complete post-warm-up vector draws and exact per-chain accounting |
| `Result.chains` | `Vector[ChainResult]`, always in index order, independent of completion order |
| `Result.diagnostics` | `Vector[McmcDiagnostics.Summary]`, coordinate-index order, or empty when any chain has fewer than four retained draws |
| `Result.diagnosticDrawsPerChain` | Shortest chain length used for alignment; may be zero |
| `Result.warnings` | `Vector[String]`: evaluation-cap, unequal-length alignment, or insufficient-trace concerns |
| `Result.elapsedSeconds` | End-to-end seconds, including construction, sampling, shutdown, and diagnostics |

Inspect `result.chains(0).result.evaluations` for chain zero's total calls, and
`result.diagnostics(0).warnings` only after checking diagnostics are nonempty.
Aggregate warnings do not copy each coordinate's warnings; inspect both. Empty warnings
are not proof of convergence. `ChainFailure(chainIndex: Int, cause: Throwable)` retains
the underlying exception; cleanup failures are attached with `getSuppressed`, not allowed
to hide it. Case-class constructors/accessors/copy have standard Scala behavior; manually
constructed result records do not certify execution. See the
[generated reference](api/com.cra.figaro.algorithm.sampling.parallel.md) for compiler-rendered signatures.

## Common pattern 1: bounded Gaussian chains instead of manual futures

The quick start runs four GPSS chains on two workers. Compared with separately launching
four `VS.run` calls, it assigns chain seeds consistently, preserves index order, detects
failures in completion order, cancels siblings, and joins its private threads. It also
calculates coordinate means, R-hat, ESS, and MCSE using the existing diagnostic module.

```scala
val serialConfig = MC.Config(VS.Config(VS.Method.GPSS, draws = 2000), parallelism = 1)
val parallelConfig = serialConfig.copy(parallelism = 2)
// Use the same pure factory with either config: full traces and diagnostics agree.
// elapsedSeconds can differ; do not compare entire Result case classes for seed equality.
```

Independent chains may run together; dependent steps inside one chain remain sequential.
Avoid increasing chain count when your intention is only to change scheduling: more
chains multiply the total requested work and memory.

## Common pattern 2: evaluation-capped positive targets

```scala
val capped = MC.run(MC.Config(VS.Config(
  VS.Method.Quantile, draws = 10000, warmUp = 100, maxEvaluations = 2000
), parallelism = 2)) { (i, _) =>
  MC.Model(Vector(i + 0.5, i + 1.0),
    x => if (x.forall(_ > 0)) -x.sum else Double.NegativeInfinity)
}
println(capped.chains.map(c => (c.index, c.result.reason, c.result.samples.size)))
println(capped.warnings)
```

Every chain keeps its cap; caps are not redistributed when another chain finishes.
Budget exhaustion is an expected incomplete-work status, not an exception, and does not
cancel other chains. Suppose lengths are 80, 90, 95, and 100: coordinate diagnostics use
the first 80 draws from **all four**. All 365 original draws remain in `chains`, and all
work remains charged. The 45 excess draws are not silently pooled into diagnostics.
With any length below four, no coordinate summary is produced. Cost-based trace selection
can affect inference; aligned diagnostics are not a correction for that selection.

## Common pattern 3: a derived event needs its own diagnostics

Coordinate summaries do not automatically diagnose tail events, products, or mode weights.
For the complete Gaussian quick-start result:

```scala
import com.cra.figaro.algorithm.sampling.parallel.McmcDiagnostics
val n = result.diagnosticDrawsPerChain
require(n >= 4)
val event = McmcDiagnostics.summarize(result.chains.map(
  _.result.samples.take(n).map(x => if (x.head > 0) 1.0 else 0.0)
))
println((event.mean, event.rHat, event.warnings))
```

Do not concatenate away chain identity or drop a poorly explored chain. You may separately
call `McmcPrecision.evaluate` on aligned scalar traces, but this API has no persistent
adaptive stopping loop. Repeatedly restarting short chains is not a continuation strategy.

## Lifecycle and gotchas

- The runner owns only its executor, worker threads, RNGs inside the reused kernel, and
  detached results. Factories/callbacks and external resources remain caller-owned; it
  never invokes an assumed `close()` or mutates an external Universe.
- Factories finish before workers start. If a later factory fails, earlier factory
  resources still belong to the caller. Prefer resource-free pure closures or manage
  external resources in a caller scope that accounts for shutdown failures.
- Shared pure callbacks are fine; mutable captured state must be per-chain or properly
  synchronized. Distinct Model objects do not prove independence. Using live graph state,
  nondeterministic density callbacks, or different targets per chain is unsupported.
- Failures are collected in completion order, not submission order. Simultaneous failures
  can report different primary chain indices; mathematical traces remain deterministic
  when runs complete normally. No partial result is presented as successful execution.
- Cancellation is cooperative. Shutdown interrupts workers and waits up to one configured
  deadline for executor termination and thread exit, continuing that cleanup despite
  repeated caller interrupts. It restores the interrupt flag afterward.
- A callback that ignores interruption can outlive a failed call. Timeout is explicit
  (or suppressed onto the primary error); threads are daemon threads, **not forcibly killed**.
  Do not close resources still used by such a callback until you know it has exited.
- The shutdown budget starts during cleanup, not when sampling starts. It cannot time out
  an indefinitely blocked serial factory or density callback while the caller remains
  waiting; cancellation must come from the caller. Diagnostic calculations now check
  interruption between stages and within rank/ESS loops as well as between coordinates.
  Sorting, array operations and a single FFT call remain non-preemptible.
- Coordinate ordering/dimension must agree across chains, but the runner cannot verify
  equivalent posterior semantics. More threads cannot establish mode discovery or cure
  the existing mixture/curvature undercoverage problems.

## Verification checkpoint

Local compilation, all 143 modernization regressions (including 11 new orchestration
groups), all three runnable workflows, and 31 documentation/report-tool tests pass.
Tests compare seeded traces and diagnostics with direct single-chain calls across worker
counts and exercise both cooperative and deliberately uncooperative callbacks. These
are execution-contract tests, not a full historical-suite success or speedup claim.
CI retains the existing documentation, coverage, artifact/publication, and clean-rebuild
reproducibility gates and adds the new regression and example commands.
Generated-reference freshness and local links pass. Thin, fat, source, and documentation
JARs build and preserve byte-identical Figaro legal files; isolated local publication
succeeds, and binary JARs exclude test/coverage runtimes.

## Related modules

[Single-chain vector API](VECTOR_SLICE_SAMPLING.md), [graph multi-chain runner](MULTI_CHAIN_MCMC.md),
[diagnostic reliability](MCMC_RELIABILITY.md), [parallel performance](PARALLEL_PERFORMANCE.md),
[higher-dimensional limitations](SAMPLING_HIGH_DIMENSIONAL.md), and
[runnable workflows](../FigaroExamples/src/main/scala/com/cra/figaro/example/MultiChainVectorSamplingExample.scala).
