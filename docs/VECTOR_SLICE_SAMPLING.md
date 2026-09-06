# Continuous-vector slice sampling

## Overview: when to use it

`VectorSliceSampler` is an opt-in, blocking single-chain runner for a model that you can
express as an explicit log-density function of `Vector[Double]`. It provides GPSS and
fixed-reference coordinate quantile slice sampling without constructing or mutating a
Figaro `Universe`. Existing Figaro samplers and defaults are unchanged.

Use it when your unknowns are a fixed-size continuous vector and you can evaluate their
**complete joint log density**, including prior and likelihood. You supply a valid initial
point; the runner owns its RNG and intermediate immutable states. It returns ordered vector
draws and work accounting. It does not infer a density from `Element` objects, handle
discrete latent variables, fit a transformation, or automatically decide when to stop for
precision. This is a supported narrow execution contract, not a general inference replacement.

Choose explicitly, based on the [higher-dimensional evidence](SAMPLING_HIGH_DIMENSIONAL.md):

| Situation | Starting choice | Important qualification |
| --- | --- | --- |
| Unconstrained continuous vector, at least two dimensions | `Method.GPSS` | Strong Gaussian/heavy-tail results; zero-radius starts are unsupported |
| Scalar parameter or coordinate-wise constrained vector | `Method.Quantile` | Positive-support results favor it; a sweep evaluates the full density repeatedly |
| Strongly correlated or curved target | Compare against an informed existing proposal | Neither new method consistently reaches precision on the 32D stress fixtures |
| Separated modes / uncertain mode weights | Neither is a demonstrated solution | Both fail the difficult 32D mixture; extra threads do not repair exploration |
| Existing Figaro graph with discrete/dynamic structure | Keep the existing graph sampler | There is no automatic graph adapter |

GPSS means Gibbsian polar slice sampling. One transition updates direction on a sphere
and radius, accounting for the polar volume factor `radius^(dimension-1)`. Quantile
sampling performs one fixed-order sweep through every coordinate using a Cauchy(0,2)
reference and the target/reference density correction. The reference is a proposal aid,
not an assumed prior. Both algorithms are gradient-free. The primary descriptions are
[GPSS Algorithms 1-3](https://proceedings.mlr.press/v202/schar23a.html) and
[quantile slice Algorithm 2](https://arxiv.org/html/2407.12608v2).
No third-party sampler source or new dependency was imported.

## Quick start (three steps)

1. Import `com.cra.figaro.algorithm.sampling.VectorSliceSampler` as `VS`.
2. Supply a finite nonzero initial vector and log density; run the explicit method below.
3. Inspect `reason`, then analyze complete draws across independently initialized chains.

```scala
import com.cra.figaro.algorithm.sampling.VectorSliceSampler as VS

val result = VS.run(
  VS.Config(VS.Method.GPSS, draws = 2000, warmUp = 200, seed = 42L),
  Vector(1.0, -1.0)
)(x => -x.map(v => v * v).sum / 2)

require(result.reason == VS.StopReason.DrawsReached, "Evaluation budget exhausted")
val firstMean = result.samples.map(_.head).sum / result.samples.size
// DrawsReached describes completed work, not convergence or adequate precision.
```

Run all three examples from the repository root:

```sh
sbt --server --batch "examples / Compile / runMain com.cra.figaro.example.VectorSliceSamplingExample"
```

## API reference

### `run(config, initial)(logDensity): Result`

The sole handwritten sampling function runs synchronously on the calling thread.
`config: Config` sets the limits below; `initial: Vector[Double]` must be nonempty and
finite; `logDensity: Vector[Double] => Double` evaluates the complete unnormalized
target with respect to ordinary Lebesgue measure in these coordinates. Normalizing
constants may be omitted. Return `Double.NegativeInfinity` outside support. Initial
density must be finite; NaN or positive infinity is invalid everywhere.
The target must be integrable and the chosen kernel must be able to explore its support;
the API cannot prove either property. A finite density value alone is not sufficient.

Returns detached immutable `Result`. The quick start is a complete invocation example.
Invalid arguments, density values, or lost floating-point resolution throw
`IllegalArgumentException`. Search exhaustion throws `SearchExhausted`. Callback
exceptions propagate unchanged; they are not converted to successful partial output.
Cooperative interruption throws `InterruptedException` and preserves/restores the flag.
The sampler never closes caller-owned resources, starts threads, or alters a global RNG.

### `Config` constructor and fields

| Parameter | Default | Meaning / validation |
| --- | --- | --- |
| `method: Method` | Required | `GPSS` or `Quantile`; null rejected |
| `draws: Int` | 10000 | Positive requested retained complete transitions |
| `warmUp: Int` | 1000 | Nonnegative discarded complete transitions; no adaptation |
| `seed: Long` | 42 | Private RNG seed; same inputs/callback produce reproducible output |
| `maxEvaluations: Long` | 1000000 | Positive total density-call limit, including initialization, warm-up, and unfinished work |
| `maxSearch: Int` | 10000 | Positive proposal limit per whole GPSS transition or each quantile coordinate |
| `maxStoredValues: Long` | 10000000 | Positive bound on `draws * dimension`, checked before the callback; not a total heap limit |

Example: `VS.Config(VS.Method.Quantile, draws = 500, warmUp = 50, maxEvaluations = 50000)`.
`config.copy(seed = 123L)` returns a revalidated configuration with a different seed.
GPSS additionally requires dimension >= 2 and finite nonzero Euclidean radius.
Quantile supports dimension >= 1. Fixed radial step-out width is one; the quantile
reference is fixed Cauchy(0,2). Neither is adapted or configurable in this milestone.

### `Result` and status types

| Field | Meaning |
| --- | --- |
| `samples: Vector[Vector[Double]]` | Ordered complete post-warm-up draws; empty is valid if the budget ran out early |
| `lastState: Vector[Double]` | Last fully completed transition or initial state if none completed |
| `evaluations: Long` | Actual callback calls charged, including an unfinished transition |
| `warmUpCompleted: Int` | Number of complete discarded transitions |
| `reason: StopReason` | `DrawsReached` or `MaxEvaluationsReached`; neither certifies precision |

`lastState` is **not a continuation token**: restarting loses the RNG position and any
unfinished work. Never append it as an extra draw or resume with the same seed expecting
the uninterrupted trace. For a complete result, `lastState == samples.last`.

`SearchExhausted(message: String)` is an `IllegalStateException` with the failed search
in its message. Catch it to report an unsuccessful run, not to substitute the current
point as an accepted sample. Enum companion methods (`values`, `valueOf`, `fromOrdinal`)
and case-class `apply`, `copy`, extractors/accessors use standard Scala semantics; the
[generated reference](api/com.cra.figaro.algorithm.sampling.md) lists their exact signatures.

## Common pattern 1: explicit Gaussian instead of a graph sampler

For an existing Figaro graph, continue using `MultiChainMetropolisHastings.run` with
chain-owned `Normal` elements, observables, and a proposal. The new path replaces that
model construction with an explicit mathematical density; it does **not** wrap the
existing graph or automatically know which priors/constraints you meant.

```scala
val gaussian = VS.run(
  VS.Config(VS.Method.GPSS, draws = 2000, warmUp = 200), Vector(1.0, -1.0)
)(x => -x.map(v => v * v).sum / 2)
require(gaussian.reason == VS.StopReason.DrawsReached)
```

This density represents two independent standard Normals. GPSS updates their entire
vector direction and radius instead of proposing a graph element. The old graph runner
provides chain scheduling and scalar diagnostics; this new single-chain call provides
neither automatically. It offers a different transition kernel, not a measured CPU speedup.

## Common pattern 2: hard positive constraints

```scala
val positive = VS.run(
  VS.Config(VS.Method.Quantile, draws = 2000, warmUp = 200), Vector(1.0, 1.0)
)(x => if (x.forall(_ > 0)) -x.sum else Double.NegativeInfinity)
require(positive.reason == VS.StopReason.DrawsReached)
assert(positive.samples.forall(_.forall(_ > 0)))
```

This is a product of rate-1 exponential densities, not a uniform positive distribution.
Unlike graph `observe()` or `addConstraint`, support and likelihood are encoded directly
in the callback. Quantile proposals outside support are rejected; no clipping changes the
distribution. A whole sweep must finish before any coordinate changes enter `samples`.
If you instead use log-transformed parameters, you must include the transformation
Jacobian in your callback; the runner cannot infer it.

## Common pattern 3: independent chains and explicit diagnostics

```scala
import com.cra.figaro.algorithm.sampling.parallel.McmcDiagnostics

val seeds = new java.util.SplittableRandom(9301)
val chains = Vector.tabulate(4) { i =>
  VS.run(
    VS.Config(VS.Method.GPSS, draws = 2000, warmUp = 200, seed = seeds.nextLong()),
    Vector(i + 0.5, -i - 0.5)
  )(x => -x.map(v => v * v).sum / 2)
}
require(chains.forall(_.reason == VS.StopReason.DrawsReached))
val summary = McmcDiagnostics.summarize(chains.map(_.samples.map(_.head)))
println(summary.warnings)
```

Keep chain identities and ordered samples separate. Diagnostics require at least two
equal-length chains of at least four draws; do not silently drop failed chains to satisfy
this requirement. `McmcPrecision.evaluate` can assess matching scalar traces, but there
is no persistent adaptive stopping API here. Do not repeatedly restart short runs and
call that one continuing chain. The example runs serially; concurrent independent calls
are safe only if callbacks do not share unsafe mutable state. Scheduling, cancellation,
and executor shutdown belong to the caller, unlike the existing multi-chain graph runner.

## Gotchas, lifecycle, and limits

- Budget exhaustion returns completed prefix samples and charges all attempted callback
  work. A cap reached halfway through a sweep or GPSS transition discards that entire
  unfinished transition. Completion wins if the final requested draw uses exactly the cap.
- Cost-based stopping can select trace lengths based on state. A shorter returned prefix
  is not automatically unbiased or sufficiently mixed. `DrawsReached` is not a convergence
  flag, and the default budget/tolerance is not tuned to your model.
- Search exhaustion, numeric collapse, and invalid models throw; no partial result is
  returned. Limits are fail-closed guards, not a mechanism for turning failed proposals
  into valid self-transitions. Track failures rather than rerunning until one succeeds.
- The callback must be deterministic and must not change the target during sampling.
  Do not use a noisy likelihood estimator or read/mutate a shared live Figaro universe.
  Immutable vectors protect coordinates, not mutable objects captured by the callback.
- Interruption is checked before/after density calls and inside search loops. A callback
  that blocks forever or ignores interruption cannot be forcibly stopped. Since this
  runner owns no threads/resources, there is no hidden background cleanup promise.
- GPSS cannot start at the origin. Quantile CDF rounding at extreme coordinates causes an
  explicit error instead of clipping. Scale/reparameterize extreme models explicitly.
- All retained vectors are stored. The scalar-value bound excludes temporary allocations,
  object overhead, initial vectors, and caller allocations; it is not an out-of-memory guarantee.
- Constrained manifolds, discrete states, dynamic dimension, automatic preconditioning,
  multimodal exploration, and arbitrary graph adapters are not supported by this interface.

## Correctness evidence and its boundary

Regression tests compare production GPSS against a separately implemented planar
angle/determinant formulation on Gaussian, correlated, and banana targets. Quantile
sampling of its own reference is compared with Apache Commons Math inverse-CDF draws.
Independent exact-start moment controls check Gaussian and positive targets at dimensions
8 and 32. These checks exercise the polar Jacobian, support, and reference correction;
they are not a cross-language comparison against a third-party GPSS package or proof of
mixing from arbitrary starts.

Other tests cover complete-prefix budget accounting, mid-sweep rollback, warm-up exclusion,
search/numerical failures, unchanged callback exceptions, caller interruption, nested calls,
and deterministic concurrent calls. Historical research data are not regenerated or
relabeled as production-interface performance measurements.

Local verification passed all 132 modernization regressions, including 11 vector-sampler
test groups, plus the three runnable workflows and 31 documentation/report-tool tests.
Generated-reference freshness and local links also pass. The full historical test suite
is not claimed green; branch CI retains the existing broader acceptance and artifact gates.

## Related

[Higher-dimensional results](SAMPLING_HIGH_DIMENSIONAL.md), [sampling research](SAMPLING_RESEARCH.md),
[multi-chain graph sampler](MULTI_CHAIN_MCMC.md), [reliability safeguards](MCMC_RELIABILITY.md),
[stopping policies](STOPPING_CRITERIA.md), [library module](../Figaro/README.md),
and [runnable example](../FigaroExamples/src/main/scala/com/cra/figaro/example/VectorSliceSamplingExample.scala).
