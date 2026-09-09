# Static graph importance sampling

## Overview

`StaticGraphImportance` is an opt-in likelihood-weighting executor for a small,
callback-free scalar DAG. Compile an immutable model once, then reuse it across
independent runs with different immutable evidence snapshots. Each run owns its
arrays and RNG streams; no mutable `Element` or `Universe` is shared.

Use it when graph construction/evaluation dominates a model expressible with the
operations below. Keep ordinary Elements, `GraphProposalImportance`, or multi-chain
MH for dynamic graphs, user functions, fitted proposals or MCMC. This is not an
automatic compiler for existing Figaro models, nor arbitrary-graph thread safety.

## Quick start: three steps

```scala
import com.cra.figaro.algorithm.sampling.{StaticGraphImportance as S}
import S.Node.*
// 1. Build x ~ N(0,1), y|x ~ N(x,1). References are earlier node positions.
val model = S.compile(Vector(Constant(0), Constant(1), Normal(0,1), Normal(2,1)))
// 2. Observe y=1, querying x. Normal takes STANDARD DEVIATION, not variance.
val result = S.run(model, Vector(2), Map(3 -> 1.0), S.Config(draws=20000, parallelism=1))
// 3. Inspect the weighted estimate AND its health report; posterior mean is 0.5.
println(result.health.head)
```

Start with one worker. Increase workers only after measuring total runtime for
your actual graph and draw budget. Reuse the compiled definition to avoid repeated
setup, but never share a mutable RNG. The executor allocates its own streams.

## Public API

`compile(nodes: Vector[Node]): Model` validates 1–4096 nodes, types and dependency
order. `Model.nodes`, `kinds` and `nodeCount` expose immutable descriptions only.
Invalid definitions throw `IllegalArgumentException`; no fallback occurs.

| Node constructor | Parameters and result | Example |
| --- | --- | --- |
| `Constant(value)` | Finite Double; Real | `Constant(0.3)` |
| `Bernoulli(probability)` | Earlier Real node ID, runtime value in [0,1]; Boolean | `Bernoulli(0)` |
| `Normal(mean,standardDeviation)` | Earlier Real IDs; finite mean, positive finite SD; Real | `Normal(0,1)` |
| `Add(left,right)` | Earlier Real IDs; sum | `Add(2,3)` |
| `Multiply(left,right)` | Earlier Real IDs; product | `Multiply(2,3)` |
| `Choose(condition,whenTrue,whenFalse)` | Earlier Boolean ID and two earlier Real IDs; selected Real | `Choose(1,2,3)` |
| `Indicator(boolean)` | Earlier Boolean ID; Real 0 or 1 | `Indicator(1)` |
| `Exp(value)` | Earlier Real ID; exponential, overflow throws | `Exp(2)` |
| `Sigmoid(value)` | Earlier Real ID; stable logistic value | `Sigmoid(2)` |

`Kind.Real` and `Kind.Boolean` are compiler tags. Boolean queries are returned as
Double 0/1. **Choose is eager**: both branch nodes have already been evaluated;
all nodes, including evidence on unused branches, remain active. It is not the
lazy dynamic semantics of an Element `If`/`Chain`.

`run(model, queries, observations=Map.empty, config=Config()): Result` takes 1–128
unique valid query IDs in column order. Observations map stochastic node IDs to
finite Doubles (Bernoulli accepts only 0/1). Deterministic observations, interval
constraints, interventions, learning and arbitrary callbacks are unsupported.

`Config` fields:

| Field | Default | Contract |
| --- | --- | --- |
| `draws` | 10000 | 1–1000000 independent prior attempts; zero weights count |
| `batches` | 16 | 1–256 logical streams, clamped to draws |
| `parallelism` | 4 | 1–32 workers per invocation; one avoids the pool |
| `seed` | 43 | Long root seed |
| `maxStoredValues` | 2000000 | 1–10000000; includes draws × (queries + weights) |
| `maxNodeEvaluations` | 100000000 | 1–1000000000; requires draws × nodes within cap |
| `randomAlgorithm` | Figaro default | Named scientific RNG backend |
| `randomStreams` | `RandomStreams.Config()` | Validated versioned stream allocation |

`Result` contains `logWeights` (one per attempt), `values` (query columns), `queries`,
one `health` report per column, the immutable `observations`, `nodeEvaluations`,
RNG `streams` descriptors and `config`. These are detached immutable snapshots.
Changing worker count preserves values/weights exactly for fixed batches, seed,
backend and stream policy. Changing those other settings can change the draws.
Concurrent calls with the same seed intentionally replay, not produce new runs.

## Three common patterns

### 1. Replace a simple Gaussian hierarchy

Ordinary modeling uses `Normal(0,1)` and `Normal(x,1)` Elements in a fresh Universe.
The quick-start DAG represents the same prior and likelihood without Element
construction. Its Normal parameter is SD; legacy Element Normal takes variance.
For observation variance 0.01, use `Constant(0.1)` for the static SD.

```scala
val narrow = S.compile(Vector(Constant(0), Constant(1), Constant(0.1),
  Normal(0,1), Normal(3,2)))
val posterior = S.run(narrow, Vector(3), Map(4 -> 1.0), S.Config(draws=20000))
println(posterior.health.head) // inspect weight concentration; more throughput is not better proposals
```

### 2. Boolean latent cause and noisy observation

```scala
val binary = S.compile(Vector(Constant(0.3), Bernoulli(0), Constant(0.8),
  Constant(0.2), Choose(1,2,3), Bernoulli(4), Indicator(1)))
val inferred = S.run(binary, Vector(6), Map(5 -> 1.0))
// Weighted mean estimates P(cause=true | observation=true) = 0.24/0.38.
println(inferred.health.head)
```

### 3. Reuse a definition with different evidence

```scala
val positive = S.run(model, Vector(2), Map(3 -> 1.0), S.Config(seed=11))
val negative = S.run(model, Vector(2), Map(3 -> -1.0), S.Config(seed=12))
// These calls can also run concurrently: each invocation owns its execution state.
```

Budget concurrent callers together: each may launch its configured number of
workers and retain its full result. There is no global pool or global memory cap.

## Measurements and limitations

[Complete evidence](STATIC_GRAPH_RUNS.csv) records 1440 rows: 20 paired seeds,
three fresh JVMs, three models, two draw budgets and four methods. Repeated JVM
timings are not additional independent datasets. Each timed call includes model
compile/factory setup, RNG setup, sampling, diagnostics and cleanup, excluding JVM
startup. Methods alternate order; both paths are warmed. The owned baseline uses
16 serial graph batches; the static paths use 16 logical batches and 1/2/4 workers.

| Model / draws | Static 1-worker speedup vs owned | Static 4-worker speedup vs owned | 1→4 worker speedup |
| --- | ---: | ---: | ---: |
| Gaussian / 2000 | 6.56× | 4.48× | 0.67× |
| Gaussian / 20000 | 7.90× | 7.87× | 1.00× |
| Narrow likelihood / 2000 | 6.51× | 4.06× | 0.58× |
| Narrow likelihood / 20000 | 7.53× | 8.00× | 1.03× |
| 64 affine steps / 2000 | 14.68× | 14.34× | 1.00× |
| 64 affine steps / 20000 | 16.69× | 26.04× | 1.52× |

Ratios are medians of paired total-call ratios, not ratios of best runs. The deep
model uses separate static multiply/add nodes versus one ordinary Apply per step;
targets agree, node counts differ. Static worker-count results/ESS are identical.
At 20000 draws, static mean RMSE is 0.00508 (Gaussian), 0.00131 (narrow), 0.00508
(deep). This fixed-budget study does not claim universally matched-error speedups.

One separate JFR profile per implementation used the deep model at 100000 draws.
Owned execution recorded 3,575,054,456 TLAB-reservation bytes and 182,400 outside-
TLAB bytes; static four-worker execution recorded 70,032,048 and 807,200 respectively
(about 50.5× lower combined). These are **TLAB reservations plus outside-TLAB
allocations**, not exact object allocation, peak heap, or RSS. Profiling includes
setup and diagnostics and is a single-run attribution check, not a repeated memory
benchmark. Signed heap deltas in the timing CSV are GC-sensitive and are not used
as allocation estimates.
The allocation check preceded the final accumulated-likelihood-overflow and
repeated-interruption guards; the timing CSV was rerun with those guards included.

Reproduce with `StaticGraphStudy 20` in three fresh JVMs and
`tools/summarize_static_graph.py`; profile with `StaticGraphStudy profile 0 PATH`
and `profile 3 PATH` using new JFR output paths. Sum `tlabSize` for
`jdk.ObjectAllocationInNewTLAB` and `allocationSize` for
`jdk.ObjectAllocationOutsideTLAB`.

Nonfinite arithmetic or invalid dynamic parameters fail the whole run; they are
not silently changed to zero likelihood. Impossible Bernoulli evidence retains
all -Infinity weights and reports danger. Cancellation throws, cancels remaining
tasks and joins workers; no partial successful result is published. Stored-value
caps are logical element counts, not byte-accurate memory limits. Health reports
do not certify posterior precision or missing-mode detection.

## Related and research basis

[Owned graph design](OWNED_GRAPH_EXECUTION_DESIGN.md), [graph proposals](GRAPH_PROPOSALS.md),
[multi-chain MH](MULTI_CHAIN_MCMC.md), [inference health](INFERENCE_HEALTH.md),
and [roadmap](../ROADMAP.md). The restricted-definition architecture is informed
by [Gen's static modeling language](https://www.gen.dev/docs/stable/ref/modeling/sml/)
and its [scaling discussion](https://www.gen.dev/docs/stable/tutorials/scaling_with_sml/).
No Gen code or dependency is adopted; this executor implements likelihood weighting,
not Gen's broader compiler/inference features. Eleven focused Scala regressions
cover analytic parity, ownership, evidence, native stream replay, budgets,
numerical failures and cancellation. Timing validators preserve slower cases.
