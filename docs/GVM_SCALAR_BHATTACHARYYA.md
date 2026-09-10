# Opt-in scalar GVM Bhattacharyya comparison

Status: public source API integrated on main at `21269b97` after
[passing CI](https://github.com/mattwilkinsphoto/figaro/actions/runs/34196804703).
This API is shipped in [Figaro 6.1.0 on Maven Central](MAVEN_CENTRAL.md).
The commit and CI links record its original acceptance; older bundles are unchanged.
The preceding test-only prototype is integrated on main at CI-verified `a54d665e`.
Main also contains [bounded tail selection](GVM_SCALAR_TAIL_PRODUCTION.md),
with no signature change, integrated at CI-verified `f4884cfb`.

## Overview: when to choose it

`GaussVonMisesScalarBhattacharyya.compare` compares two fixed GVM distributions with
**one linear coordinate plus one angle**. It computes the symmetric Bhattacharyya
distance `D_B = -log(integral sqrt(p*q))` in nats. It is not KL divergence,
Mahalanobis distance, a hypothesis test, or a posterior update.

The existing [Fourier comparison](GVM_BHATTACHARYYA.md) is unchanged and remains useful
across multiple linear dimensions. Its oscillating terms can cancel when the true
overlap is very small. The new method integrates a positive scalar function instead,
avoiding that particular cancellation. Call it explicitly when a scalar comparison
is numerically unresolved by Fourier, or when positive integration is appropriate
for your fixed scalar workload. No algorithm switches methods behind your back.

| Situation | Existing Fourier API | New scalar API |
| --- | --- | --- |
| Scalar, weak coupling, nearly opposed angles at concentration 50 | May return `NumericallyUnresolved` | Recovers the tested case below using positive integration |
| Several linear coordinates | Supports guarded comparisons up to dimension 32 | `UnsupportedRange`; not a multidimensional remedy |
| Gaussian/uniform/constant-angle reduction | Uses analytic shortcuts | Also skips quadrature entirely |
| Highly oscillatory scalar phase | Harmonic budget and rounding limits | Phase-aware panel budget and rounding limits; may also refuse |

This is an accuracy/range alternative, **not a demonstrated general speedup** over
Fourier. Positive panels can require thousands of evaluations. Neither eligibility
nor an `Estimated` result is a certified accuracy guarantee.
The [matched-accuracy scalar study](GVM_SCALAR_PERFORMANCE.md) supports trying Fourier
first for ordinary coupled cases: it was about 39–69x faster on three such fixtures,
and about 2,626x faster on a heavily curved case. Positive integration recovered the
two tested tiny-overlap refusals at about 0.25–0.43 ms. Analytic scalar shortcuts are
cheaper, but that is not an integration speedup. These are bounded workload results.
Those numbers describe the original implementation. The
[audited-totals optimization](GVM_SCALAR_AUDITED_TOTALS.md), integrated on main at CI-verified `213aa587`,
improves positive integration by about 2.9–7.75x on three matched-control fixtures.
It requires no API changes and does not reverse the Fourier-first recommendation.
The subsequent bounded-tail policy measures about 2.53x over that audited implementation
on the costly curved fixture; the other tested cases change only a few percent.

## Quick start in three steps

1. Add [the Figaro 6.1.0 dependency](MAVEN_CENTRAL.md) (the earlier RC1 does not contain this API).
2. Construct fixed kernels in the same coordinates and units:
   ```scala
   import com.cra.figaro.library.atomic.continuous.*
   val p = GaussVonMisesDistribution(Vector(0.0), Vector(Vector(1.0)),
     0.0, Vector(0.0), Vector(Vector(0.0)), 50.0)
   val q = GaussVonMisesDistribution(Vector(0.0), Vector(Vector(1.0)),
     math.Pi, Vector(1e-5), Vector(Vector(0.0)), 50.0)
   ```
3. Compare and handle the outcome:
   ```scala
   val result = GaussVonMisesScalarBhattacharyya.compare(p, q)
   result.distance match {
     case Some(d) => println(s"Estimated distance: $d nats; interval: ${result.interval}")
     case None => println(s"No usable distance: ${result.status}")
   }
   ```

The example returns approximately `47.1275754862468` nats. Setting the small `beta`
to zero changes the result by about `1.56e-8` nats, more than the default tolerance;
the implementation does not erase weak coupling to obtain an analytic answer.

## API reference

The [source](../Figaro/src/main/scala/com/cra/figaro/library/atomic/continuous/GaussVonMisesScalarBhattacharyya.scala)
provides one computation entry point:

```scala
GaussVonMisesScalarBhattacharyya.compare(
  p: GaussVonMisesDistribution, q: GaussVonMisesDistribution,
  tolerance: Double = 1e-8, maxEvaluations: Int = 50000,
  cancelled: () => Boolean = () => false
): GaussVonMisesScalarBhattacharyya.Result
```

| Parameter | Contract |
| --- | --- |
| `p`, `q` | Non-null fixed kernels, matching dimension and coordinate convention; this API requires dimension 1 and each concentration at most 50 |
| `tolerance` | Positive finite absolute distance target in nats, not relative affinity accuracy or a statistical confidence level |
| `maxEvaluations` | Integer 5..200,000, default 50,000; bounds integrand evaluations, not every operation or elapsed time; excludes optional 64-cell/2,304-Bessel-term tail setup; analytic shortcuts use zero |
| `cancelled` | Non-null cooperative predicate; true aborts with `CancellationException`; keep it cheap, thread-safe if shared, and free of side effects |

Malformed arguments (including mismatched dimensions) throw `IllegalArgumentException`.
Matching nonscalar kernels or concentrations above 50 return `UnsupportedRange`, even
for identical inputs. Thread interruption is checked independently of the predicate;
it throws `CancellationException` and does not clear the interrupt flag. Exceptions
from a caller-supplied predicate propagate rather than becoming numerical outcomes.

`Result` is an immutable case class; its constructor/copy fields are listed below.
Constructing/copying a result does not perform or validate a comparison. Consume the
result returned by `compare`, rather than manufacturing an `Estimated` outcome.

| Result field | Meaning |
| --- | --- |
| `status` | Nested `Status` enum: `Estimated`, `BudgetExhausted`, `NumericallyUnresolved`, `UnsupportedRange` |
| `distance` | `Option[Double]` in nats; `Some` only when numerical estimates meet the requested tolerance |
| `interval` | Optional estimated `(lower, upper)` distance interval in nats; upper can be infinite; not a certified enclosure |
| `evaluations` | Actual integrand count, excluding bounded tail-selection setup; zero may mean an analytic shortcut or preflight refusal |
| `radius` | Truncation radius in the standardized Gaussian coordinate; zero for shortcuts |
| `gaussianTailBound` | Omitted Gaussian mass bound, evaluated in Double; units are angular affinity, not distance |
| `quadratureErrorEstimate` | Sum of panel Simpson differences, in angular-affinity units; heuristic |
| `roundoffEstimate` | Integration arithmetic allowance, in angular-affinity units; heuristic |
| `preprocessingErrorEstimate` | Transformation allowance in **nats**; for analytic shortcuts includes final arithmetic allowance |
| `method` | `identity`, `gaussian`, `one-uniform`, `constant-angular`, `positive-integration`, or `unavailable` |

Missing diagnostics can be infinite. Do not add affinity-unit errors directly to
distance-unit errors. The estimated interval combines them after taking logarithms.
`-result.distance.get` gives log affinity; `math.exp(-d)` gives affinity, but can
underflow to zero even when the distance remains finite and useful.

## Three common patterns

### 1. Explicitly compare the two methods

Using `p` and `q` from the quick start:

```scala
val series = GaussVonMisesBhattacharyya.compare(p, q)
val positive = GaussVonMisesScalarBhattacharyya.compare(p, q)
assert(series.status == GaussVonMisesBhattacharyyaStatus.NumericallyUnresolved)
assert(positive.status == GaussVonMisesScalarBhattacharyya.Status.Estimated)
println(positive.distance) // approximately Some(47.1275754862468)
```

This does not reinterpret the Fourier failure as a distance. It requests a separate
calculation with a different error-estimation strategy. Record method and status
alongside the value in downstream comparisons.

### 2. Budget work and handle refusal

```scala
val limited = GaussVonMisesScalarBhattacharyya.compare(p, q, maxEvaluations = 5)
assert(limited.status == GaussVonMisesScalarBhattacharyya.Status.BudgetExhausted)
assert(limited.distance.isEmpty)
val retry = GaussVonMisesScalarBhattacharyya.compare(p, q, maxEvaluations = 50000)
println(retry.status)
```

More budget may help `BudgetExhausted`. It does not cure a floating-point precision
floor: this fixture with `tolerance = 1e-14` returns `NumericallyUnresolved`.
Do not convert `None` to zero, silently relax tolerance, or treat it as disjoint support.
For cooperative cancellation, pass `cancelled = () => stopFlag.get()` with a
`java.util.concurrent.atomic.AtomicBoolean` managed by the calling application.

### 3. Let analytic cases skip the integration

```scala
val g0 = GaussVonMisesDistribution(Vector(0.0), Vector(Vector(1.0)),
  0.0, Vector(0.0), Vector(Vector(0.0)), 0.0)
val g2 = GaussVonMisesDistribution(Vector(2.0), Vector(Vector(1.0)),
  0.0, Vector(0.0), Vector(Vector(0.0)), 0.0)
val gaussian = GaussVonMisesScalarBhattacharyya.compare(g0, g2)
assert(gaussian.distance.contains(0.5))
assert(gaussian.method == "gaussian" && gaussian.evaluations == 0)
```

Stored identical laws, one uniform angular conditional, and structurally constant
angular differences also have shortcuts. The Gaussian variance term uses `log1p`
to preserve small positive distances for nearly equal variances. Shortcuts still
respect numerical precision checks; only exact stored identity has a zero-width interval.

## Gotchas and numerical limits

- The scalar integrator uses positive Simpson panels with phase-aware initial splits,
  compensated sums and reusable samples. Its discrepancy and floating-point allowances
  are heuristic. `Estimated` is deliberate, not synonymous with a proved interval.
- Nonidentity variance contrast above 1e8 is refused. Even below it, tight requested
  tolerance can exceed the estimated preprocessing precision.
- Gaussian distance above 10,000 is unsupported. Nonanalytic transformed phase
  coefficients are limited to absolute value 10,000. Tail radius is capped at 16.
  These conservative work/range controls are not mathematically intrinsic GVM limits.
- Phase-aware prepartitioning may exhaust the evaluation budget before evaluating
  the integrand. Counts do not include Bessel calculations, optional bounded tail
  setup or queue bookkeeping. Tail setup can run before a low-budget refusal.
- Extreme means, underflowed covariance inputs and nearly cancelled large operands
  can cause a numerical refusal. Common unit rescaling of 1e-100 and 1e100 is tested,
  not a guarantee for every parameter combination. Kernels must themselves be valid.
- Per-call buffers are isolated and immutable kernels can be shared. This does not
  make mutable Figaro elements or universes thread-safe. No sampling, inference,
  report ingestion, fusion, filtering or propagation is added.

## Validation and related modules

The original API milestone passed all **309 modernization tests across 25 suites**, the executable
example, 32 high-precision research/report tests, seven artifact-validator tests and
18 documentation-tool tests. The thin Java 17 artifact contains the new API and excludes
test/example/instrumentation classes. Generated API reference and local links also pass.
The separate [published consumer](../tools/acceptance-consumer/README.md) passed using
the just-published JAR, verified by SHA-256, including this API's positive overlap and
budget-refusal checks. CI additionally requires the runtime class in both thin and
assembled artifacts; the full remote publication/reproducibility gate passed at `21269b97`.
The [bounded-tail integration](GVM_SCALAR_TAIL_PRODUCTION.md) has its own CI/integration
gate and passes 319 distinct modernization tests locally, including held-out controls.

The inherited high-precision grid checks 84 scalar pairs in both directions (168
comparisons), plus ten unequal-concentration pairs in both directions. The public
implementation's observed maximum main-grid error after bounded-tail integration is
3.44e-10 nats at a requested 1e-8, with at most 9,102 evaluations. These fixture observations are not universal
error bounds. Tests also cover analytic shortcuts, near-equal variances, extreme
units, conditioning thresholds, work limits, cancellation and concurrent calls.

Run the [executable examples](../FigaroExamples/src/main/scala/com/cra/figaro/example/documentation/GaussVonMisesScalarBhattacharyyaExample.scala):

```text
sbt "examples / Compile / runMain com.cra.figaro.example.documentation.GaussVonMisesScalarBhattacharyyaExample"
sbt "figaro / Test / testOnly com.cra.figaro.test.modernization.GaussVonMisesScalarBhattacharyyaTest"
```

Related: [fixed GVM kernels](GAUSS_VON_MISES.md), [Fourier comparison](GVM_BHATTACHARYYA.md),
[Python positive-integration controls](GVM_BHATTACHARYYA_POSITIVE_RESEARCH.md),
[preprocessing derivation and prototype history](GVM_POSITIVE_SCALAR_PROTOTYPE.md),
[reliability fixtures](GVM_BHATTACHARYYA_RELIABILITY.md), [roadmap](../ROADMAP.md).
