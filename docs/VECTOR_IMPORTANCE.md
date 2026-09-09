# Opt-in frozen vector proposals

## Overview: when to use this

`VectorImportance` estimates expectations and event probabilities from an explicit
joint density on `Vector[Double]`. You supply a normalized sampling proposal and an
unnormalized target log density. Figaro draws from the proposal, computes the full
target/proposal ratios, and returns samples plus a query-specific health report.
An optional pilot stage fits a Gaussian, mixes it with your broad proposal, and
freezes that mixture before drawing a separate production sample.

Use this when you can write the joint density explicitly and ordinary prior
importance spends most of its draws far from a concentrated posterior. A fitted
proposal can put substantially more draws where the posterior matters. Do not
enable it simply because a health warning appeared: support, multimodality and
tail behavior still require model-specific judgment. Pilot work may cost more
than it saves for easy models or small budgets.

This is an additive public API in the `modernize/statistical-validation` branch
snapshot, locally validated, not a claim of remote CI or main integration. It does
not replace graph `Importance`, discover graph coordinates, change defaults,
adapt production weights, or stop automatically when an error target is reached.
Rebuild and publish the branch snapshot to use it; the version coordinate alone
does not distinguish successive snapshot builds.

## Quick start: three steps

1. Import the explicit-density API and define a bounded target and covering proposal:

   ```scala
   import com.cra.figaro.algorithm.sampling.VectorImportance as V
   val broad = V.Box(Vector(-5.0), Vector(5.0))
   val logTarget: Vector[Double] => Double = x =>
     if (math.abs(x.head) < 5) -x.head*x.head/2 else Double.NegativeInfinity
   ```

2. Run a fixed number of independent proposal draws:

   ```scala
   val result = V.run(V.Config(draws=10000, maxEvaluations=10000, seed=43),
     broad, logTarget, _.head)
   ```

3. Inspect the estimate **and** its assessment before using it:

   ```scala
   println(result.health.diagnostics.mean)
   println(result.health.status)
   result.health.issues.foreach(i => println(s"${i.code}: ${i.message}"))
   println(s"Target calls: ${result.evaluations}; stopped: ${result.reason}")
   ```

The mean is optional: for example, all-zero target weights cannot define an
estimate. A completed run is not necessarily a reliable run. `ChecksPassed`
means available diagnostics passed, not that an unseen mode was ruled out.
See [inference health](INFERENCE_HEALTH.md) for report fields and interpretations.

## Three common patterns

### 1. Supply a frozen proposal you already understand

The quick start uses a uniform proposal and needs no training. If you already
have a good Gaussian approximation, combine it with the broad component:

```scala
import com.cra.figaro.library.atomic.continuous.MultivariateGaussianDistribution
val approximation = V.Gaussian(MultivariateGaussianDistribution(
  Vector(0.0), Vector(Vector(1.0))))
val fixed = V.Mixture(Vector(.1, .9), Vector(broad, approximation))
val improved = V.run(V.Config(seed=44), fixed, logTarget, _.head)
```

Uniform-only sampling spends draws evenly across the box; this mixture puts most
draws near zero while retaining a broad component. Each draw is weighted by the
**entire mixture density**, not just the component selected to generate it.
Gaussian draws outside the target box are retained with zero target weight;
they are not clipped, retried or silently turned into truncated-Gaussian draws.

### 2. Train on discarded pilot chains, then freeze

```scala
import com.cra.figaro.algorithm.sampling.VectorSliceSampler as VS
import com.cra.figaro.algorithm.sampling.parallel.MultiChainVectorSliceSampler as MC
val pilot = MC.Config(
  VS.Config(VS.Method.Quantile, draws=300, warmUp=100,
    maxEvaluations=5000, seed=42), parallelism=2)
val starts = Vector(-2.0, -.5, .5, 2.0).map(Vector(_))
val attempt = V.runWithPilot(pilot, starts, broad,
  V.Config(draws=5000, maxEvaluations=5000, seed=43), logTarget, _.head)
println(attempt.fit.status)
println(attempt.pilot.diagnostics)
println(s"Pilot=${attempt.pilotEvaluations}, total=${attempt.totalEvaluations}")
attempt.production match {
  case Some(r) => println((r.health.diagnostics.mean, r.health.status))
  case None => println(attempt.fit.message) // No fallback estimate was produced.
}
```

Compared with pattern 1, this buys a data-informed Gaussian approximation at an
explicit training cost. Starts should be dispersed over plausible posterior
regions, not identical or all inside a known single mode. The default fit uses
twice the pooled empirical covariance and **no ridge**. A fitted covariance does
not establish pilot convergence: inspect pilot diagnostics and production health.
Production starts automatically after a numerical fit; use separate `MC.run`,
`fitGaussian` and `run` calls if you need to approve pilot diagnostics first.

The pilot's evaluation cap is **per chain**, including warm-up and rejected work.
This example permits up to 4 × 5,000 pilot calls plus 5,000 production calls.
The requested 300 retained draws may finish below that cap. Only fresh production
draws enter the estimate. Undertrained, constant-coordinate or numerically invalid
fits return `production=None`; they do not secretly revert to the prior.

### 3. Reuse a frozen law for another query; cover tails explicitly

```scala
attempt.fit.proposal.foreach { gaussian =>
  val frozen = V.Mixture(Vector(.1, .9), Vector(broad, gaussian))
  val event = V.run(V.Config(seed=44), frozen, logTarget,
    x => if (x.head > 1) 1.0 else 0.0)
  println((event.health.diagnostics.mean, event.health.status))
}
```

This reuses training, not production randomness. To assess another query on the
same production sample without new target calls, pass its aligned finite values
and the original `logWeights` to `InferenceHealth.importance`. Estimates from the
same sample are dependent; do not treat them as independent replications.

An unbounded heavy-tail target needs a different broad component. For example,
estimate a Cauchy tail probability, not its undefined mean:

```scala
val heavy = V.ProductStudentT(Vector(0.0), Vector(1.0), degreesOfFreedom=1)
val cauchyLog: Vector[Double] => Double = x => -math.log(math.Pi)-math.log1p(x.head*x.head)
val tail = V.run(V.Config(seed=47), heavy, cauchyLog,
  x => if (x.head > 5) 1.0 else 0.0)
// Reference: 0.5 - atan(5)/Pi. Inspect tail.health; a bounded query is not a guarantee.
```

A box alone would miss unbounded target support. A Gaussian has positive density
everywhere in exact arithmetic but may still be too light-tailed, or miss a narrow
distant mode completely in a finite run. Independent Student-t coordinates provide
one broad option, not a general solution for dependent multivariate tails.

## API reference

All names below are members of `com.cra.figaro.algorithm.sampling.VectorImportance`.
Vector dimensions are 1–128. Constructors reject invalid arguments with
`IllegalArgumentException`; callback exceptions propagate, with pilot failures
wrapped by the existing multi-chain runner. No partial production result is published.
Compiler-derived declarations, including case-class factories/accessors, are in
the [generated API reference](api/README.md).

### Proposal constructors and methods

| Constructor | Parameters / result | Example |
| --- | --- | --- |
| `Box(lower, upper)` | Finite vectors; strictly positive finite widths. Returns independent uniforms, with finite density at endpoints. | `V.Box(Vector(0.0), Vector(1.0))` |
| `Gaussian(law)` | Non-null immutable `MultivariateGaussianDistribution`; returns its proposal adapter. | `approximation` in pattern 1 |
| `ProductStudentT(location, scale, degreesOfFreedom=5)` | Finite coordinate locations, positive scales, common positive degrees of freedom; inherits scalar-law numerical limits. Returns an independent-coordinate product, **not** an elliptical multivariate t. | `heavy` in pattern 3 |
| `Mixture(weights, components)` | 1–32 positive finite relative weights, normalized without overflow; common dimension. Rejects normalized-weight underflow. Returns a frozen mixture. | `V.Mixture(Vector(1.0,9.0), Vector(broad,approximation))` |

Every `Proposal` exposes `dimension: Int`, `sample(rng): Vector[Double]`, and
`logDensity(x): Double`. `sample` uses the supplied `scala.util.Random` exclusively
and returns a finite vector from exactly the normalized density that `logDensity`
reports. `logDensity` accepts a finite, matching-dimensional vector and returns a
normalized log density (negative infinity off support). Example:

```scala
val rng = com.cra.figaro.util.SamplingRandom.scalaRandom(48)
val x = broad.sample(rng)
val logQ = broad.logDensity(x)
val normalizedFractions = fixed.probabilities
```

`Mixture.probabilities` returns normalized weights in component order. Constructor
fields (`lower`, `upper`, `law`, `location`, `scale`, `degreesOfFreedom`, `weights`,
`components`) are immutable and readable. A custom `Proposal` must be immutable
and stateless, must not retain the RNG, and must cover every region contributing
to the target integral. Figaro checks each realized draw/density, but cannot verify
global normalization, support coverage or callback purity from finite samples.

Student-t `scale` is not its standard deviation. This adapter reuses the existing
scalar inverse-CDF sampling implementation; it is not a newly optimized
multivariate Student-t kernel. Choose using measured total accuracy/cost, not the
name of the distribution alone.

### Production policy and result

`Config` parameters and defaults:

| Parameter | Meaning |
| --- | --- |
| `draws=10000` | Positive requested independent production draws, at most `health.maxSamples`. |
| `maxEvaluations=10000L` | Positive production target-call cap. Actual count is `min(draws,maxEvaluations)`. Set both fields for larger runs. |
| `seed=43L` | Private production RNG seed. |
| `randomAlgorithm=SamplingRandom.defaultAlgorithm` | Explicit supported backend; no global RNG mutation. |
| `maxStoredValues=10000000L` | Positive scalar-slot limit: actual draws × (dimension + 2). Exceeding it rejects before sampling; it is not a heap/workspace bound. |
| `health=InferenceHealth.Config()` | Query-specific assessment policy; never a precision stopping rule. |

`run(config, proposal, logTarget, project): Result` runs synchronously. `logTarget`
is a pure unnormalized joint log density with respect to vector Lebesgue measure;
negative infinity means zero density, while NaN and positive infinity are rejected.
Include any coordinate-transform Jacobian yourself. `project` returns a finite
scalar, including a 0/1 event indicator, **even at zero-target-weight points**.
Proposal log density must be finite at its own draws. See the quick start for use.

`Result` fields are `samples` (all raw production vectors), `logWeights` (raw
log-target minus full log-proposal ratios), `health` (the supplied query's
`ImportanceReport`), `evaluations` (actual target calls), `reason`, `config`, and
`randomProvider` (provider/JDK provenance). `StopReason.DrawsReached` and
`MaxEvaluationsReached` describe completed work, not successful accuracy. If both
limits are reached together, the reason is `DrawsReached`. No draws are resampled
or removed because their weights are zero.

### Fitting policy and result

`FitConfig` parameters: `covarianceInflation=2` is a positive finite covariance
multiplier, **not** a standard-deviation multiplier; `diagonalRidge=Vector.empty`
means no regularization, otherwise supply one finite nonnegative variance in each
coordinate's squared units. `minChains=4`, `minDrawsPerChain=5`, and
`minTotalDraws=20` are each at least two. `maxPilotValues=10000000L` caps input
scalar slots and must be positive.

`fitGaussian(chains, config=FitConfig()): FitResult` accepts immutable post-warm-up
`Vector[Vector[Vector[Double]]]`, allowing unequal chain lengths. It pools draws
without weights, uses the sample covariance, applies the explicit multiplier and
ridge, and returns a frozen Gaussian or an explicit refusal. Example:

```scala
val fit = V.fitGaussian(attempt.pilot.chains.map(_.result.samples),
  V.FitConfig(diagonalRidge=Vector(1e-4)))
```

`FitResult` exposes `status`, `proposal: Option[Gaussian]`, pooled `draws`, actual
`config`, and a human-readable `message`. `FitStatus` values are:

- `Fitted`: numerical construction succeeded; does not certify exploration.
- `InsufficientPilot`: empty or insufficient retained chains/draws.
- `DegeneratePilot`: any constant coordinate; adding ridge does not override this.
- `NumericalFailure`: covariance/mean unsupported by the Gaussian numerical contract;
  no hidden repair. Invalid dimensions, non-finite inputs and storage excess throw.

### Combined pilot/production operation

`runWithPilot(pilotConfig, initialStates, defensive, productionConfig, logTarget,
project, fitConfig=FitConfig(), defensiveWeight=.1): PilotRun` uses existing
multi-chain slice sampling, fits once, and runs the frozen mixture. Parameters:

- `pilotConfig`: `MultiChainVectorSliceSampler.Config`, at most 128 chains;
  requested pilot draws × chains × dimension must fit `fitConfig.maxPilotValues`.
- `initialStates`: one finite, matching-dimensional dispersed start per chain.
- `defensive`: caller-selected normalized broad `Proposal`.
- `productionConfig`: separate production policy; must have a distinct root seed.
- `logTarget`: pure target callback, safe to call concurrently in pilot workers.
- `project`: finite production query callback as in `run`.
- `fitConfig`: explicit numerical fitting requirements described above.
- `defensiveWeight`: strictly between zero and one; remaining mass goes to the fit.

`PilotRun` exposes `pilot` (existing multi-chain result with diagnostics), `fit`,
`production: Option[Result]`, `pilotConfig`, and `defensiveWeight`.
`pilotEvaluations: Long` sums all pilot-chain target calls;
`totalEvaluations: Long` adds actual production calls, or zero after fit refusal.
Pattern 2 demonstrates every part. The proposal is frozen after fitting: pilot
draws are never counted as production samples.

## Gotchas and limitations

- A small MCSE does not establish exploration. A regression deliberately gives a
  left-mode proposal to a two-mode target: nominal full support still misses the
  other mode. Constant event samples must not earn a green accuracy conclusion.
  No health report can prove that unseen target mass does not exist.
- A defensive fraction is not a universal variance bound. The broad law must cover
  the target and have suitable tails; our self-normalized estimator does not inherit
  a control-variate estimator's theorem. See the [literature assessment](DEFENSIVE_IMPORTANCE_RESEARCH.md).
- Ordinary confidence intervals here are diagnostic asymptotic approximations,
  not calibrated finite-sample guarantees. No automatic precision stopping is added.
- Pilot and production budgets are separate; add their maximum target-call budgets
  for a total cap. Proposal density evaluations, fitting and diagnostics also cost
  work but are not target calls. Pilot traces coexist with production traces;
  aggregate memory includes both, plus covariance and diagnostic workspace.
- Distinct root seeds are required. With the same backend and `SeededV1`, an exact
  production/pilot allocated-seed collision is rejected before training. Different
  root seeds alone are not a mathematical proof of disjoint streams; this API does
  not allocate production as a member of the pilot's partitioned stream family.
- Record configs, RNG/provider metadata, proposal parameters and your target/query
  implementation for replay. Custom callbacks are not serialized. Scheduling changes
  preserve ordered seeded pilot results for pure callbacks; production is synchronous.
- Cancellation is cooperative, checked around callbacks and internal work. A callback
  that never returns cannot be forcibly interrupted safely. Pilot workers are owned
  and cleaned up by the existing runner; custom callback resources remain yours.
- This API assumes an explicit continuous vector density. It is not a mixed-measure,
  discrete, singular-manifold or automatic graph-posterior sampler. Gaussian fitting
  is a single-component approximation, not EM or automatic multimodal fitting.

## Acceptance evidence and reproduction

The [saved public-API grid](vector-importance-results.csv) contains 210 trials /
450 query rows: 30 fixed seeds each on four original/held-out Gamma/Dirichlet
posteriors, separated Gaussian modes, a bounded two-coordinate Beta target, and
a Cauchy event probability. Each run charges 10,000 pilot and 10,000 production
target calls. Every run fitted and met all predeclared point-accuracy tolerances.
Two Gamma trials still reported `Danger`; those warnings are retained, not excluded.

Per-query nominal 95% interval coverage ranged from 27/30 to 30/30 (90–100%). These
small groups do not establish calibrated coverage. The earlier research's larger
fresh-seed coverage batch also remains relevant caution, not a guarantee for this
API. The new runner uses its own multi-chain seed allocation and starts; these are
not exact reproductions of the research prototype's traces or a new controlled
speedup benchmark. The Cauchy control estimates a bounded event, never its undefined
mean. This finite suite is not a general high-dimensional or rare-event validation.

Focused contracts cover normalized density oracles, full mixture ratios, no zero-weight
retries, caps and preflight validation, refusal states, scheduling determinism,
seed separation, callback errors/interruption, worker cleanup, and missed-mode risk.
Run the executable example and evidence checker:

```sh
sbt "figaro / Test / testOnly com.cra.figaro.test.modernization.VectorImportanceTest"
sbt "examples / Compile / runMain com.cra.figaro.example.documentation.VectorImportanceExample"
sbt "figaro / Test / runMain com.cra.figaro.test.modernization.VectorImportanceAcceptance full"
python -B tools/summarize_vector_importance.py docs/vector-importance-results.csv
python -B -m unittest discover -s tools -p 'test_summarize_vector_importance.py'
```

The full runner records all outcomes without retries or warning-based exclusions.
The checker rejects missing/duplicate rows, changed references or budget accounting,
and inconsistent flags. CI is configured to rerun it; no remote result is claimed here.

Local delivery checks passed: 415 modernization tests in 39 suites, 105 Python
evidence/reference/tooling tests, the executable example, and an independently
compiled application loading the exact newly published thin JAR. All four archives
passed required-class, Java-17 bytecode, legal-notice and runtime-isolation checks.
Scaladoc generated successfully with four pre-existing warnings; the compiler-derived
handbook contains 11,983 public method entries. The verified thin-JAR SHA-256 is
`913a28339a9fb9da064ca040c3b8178fdda755928ce9c2d681996ef42e99dac6`.
These are local checks of this branch build, not a new release publication.

## Related and next work

- [Inference health](INFERENCE_HEALTH.md): ESS, weight tails, query MCSE and warnings.
- [Multi-chain vector sampling](MULTI_CHAIN_VECTOR_SAMPLING.md): pilot callbacks,
  execution policies, retained chains, diagnostics and cleanup.
- [Defensive proposal research](DEFENSIVE_IMPORTANCE_RESEARCH.md): literature,
  matched-budget comparisons and the unchanged historical prototype.
- [Distribution constructions](DISTRIBUTION_CONSTRUCTIONS.md) and
  [RNG selection](RNG_SELECTION.md): proposal laws and explicit backend choices.

Next priorities are broader geometry/rare-event evidence and query-MCSE coverage
calibration before automatic stopping. Graph proposal integration, mixture fitting,
adaptive production and automatic sampler selection remain separate milestones.
