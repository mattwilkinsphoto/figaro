# Deterministic GVM expectation quadrature

## Overview

This module approximates an expectation under a **fixed GVM kernel** by evaluating a
function at a small, deterministic set of points. It independently implements the
third-order sparse rule in [Horwood and Poore, section 5.1](https://doi.org/10.1137/130917296),
not the paper's later filtering or distribution-reconstruction algorithm.

For n linear coordinates plus one angle, the rule uses **2n+3 callback evaluations**.
For n=6, that is 15 evaluations. This can be useful when a function is costly and its
expectation is sufficiently well approximated by the rule. It removes Monte Carlo
sampling variability, but replaces it with deterministic approximation error outside
a specified exactness class. Smoothness alone does not guarantee a good approximation.

**Weights can be negative. There is no automatic error bound, refinement or positivity
guarantee.** Use analytic moments when available; otherwise validate the particular
function and parameter range against independent integration or Monte Carlo before
relying on this low-cost rule. It does not accelerate existing inference automatically.

Status: source-development preview on `modernize/gauss-von-mises`, with separate
remote CI and main-integration gates. The preceding gradient milestone is on main at
`141dcc15`, with [passing CI](https://github.com/mattwilkinsphoto/figaro/actions/runs/34177381982).
Existing RC1 binaries are unchanged.

## Quick start in three steps

1. Obtain a fixed kernel and construct a reusable rule:

   ```scala
   import com.cra.figaro.library.atomic.continuous.*
   val kernel = GaussVonMisesDistribution(Vector(1.0, -2.0),
     Vector(Vector(4.0, 1.2), Vector(1.2, 2.61)), 3.05,
     Vector(0.7, -0.4), Vector(Vector(0.3, 0.2), Vector(0.2, -0.5)), 4.5)
   val rule = kernel.thirdOrderQuadrature()
   ```

2. Approximate an expectation: `val second = rule.expectation(p => p.linear(0)*p.linear(0))`.
3. Check it against a known value: here `second` is 5, equal to
   `kernel.mean(0)*kernel.mean(0) + kernel.covariance(0)(0)` within rounding.

For this quadratic function the rule is exact mathematically. That does not establish
accuracy for an unrelated nonlinear function on the same kernel.

## Public API reference

| API | Parameters | Returns | Example |
| --- | --- | --- | --- |
| `GaussVonMisesQuadrature.thirdOrder(kernel, maxNodes = 1001)` | Non-null fixed kernel; integer node-count guard in 5..10001, at least `2*dimension+3` | Immutable sparse quadrature rule; no sampling | `GaussVonMisesQuadrature.thirdOrder(kernel, 101)` |
| `kernel.thirdOrderQuadrature(maxNodes = 1001)` | Same node-count guard | New rule for this kernel; store it for reuse | `val rule = kernel.thirdOrderQuadrature()` |
| `rule.expectation(f)` | Non-null callback `LinearAngular => Double`; finite output at every node | Compensated signed weighted sum as Double | `rule.expectation(p => p.linear(0))` |
| `rule.expectationVector(outputDimension)(f)` | Output dimension in 1..10000; callback returns a non-null finite `Vector[Double]` of exactly that length | Immutable vector of estimates; callback invoked once per node, not per output component | `rule.expectationVector(2)(p => Vector(p.linear(0), math.sin(p.angle)))` |
| `GaussVonMisesQuadratureExample.main(args)` | Empty `Array[String]`, from `com.cra.figaro.example.documentation` | Unit; prints checked analytic/Monte Carlo comparisons, callback counts and a failure example | `GaussVonMisesQuadratureExample.main(Array.empty[String])` |

The rule has a library-internal constructor and these read-only fields:

| Field | Meaning | Example |
| --- | --- | --- |
| `nodes: Vector[LinearAngular]` | Physical-coordinate points, with normalized angles; deterministic order is center, positive/negative angular points, positive/negative pairs along each canonical linear axis | `rule.nodes.head` |
| `weights: Vector[Double]` | Corresponding signed integration coefficients, summing to one within rounding; not probabilities | `rule.weights.head` |
| `nodeCount: Int` | Number of stored points and callback invocations per successful evaluation, including zero-weight nodes | `rule.nodeCount` |
| `hasNegativeWeights: Boolean` | Whether at least one coefficient is negative | `rule.hasNegativeWeights` |
| `absoluteWeightSum: Double` | Sum of absolute coefficients; potential amplification of uniformly bounded callback errors | `rule.absoluteWeightSum` |

If every callback value has absolute error at most e, the induced weighted-sum error
is at most `absoluteWeightSum*e` in exact arithmetic. This does **not** bound quadrature
approximation error or certify floating-point summation error. No error estimate,
confidence interval, effective sample size or convergence flag is returned.

## Three common patterns

### 1. Replace many sampled function evaluations for a verified low-order expectation

The ordinary Monte Carlo approach is:

```scala
val rng = new scala.util.Random(42L)
val draws = 50000
val sampled = (0 until draws).map { _ =>
  val p = kernel.sample(rng)
  p.linear(0)*p.linear(0)
}.sum / draws
```

The deterministic alternative is:

```scala
val deterministic = rule.expectation(p => p.linear(0)*p.linear(0))
println((sampled, deterministic, rule.nodeCount))
```

The quick-start kernel needs seven deterministic evaluations. A six-linear-coordinate
example needs 15. In the executable example, `E[||x||^2]` for a standard six-dimensional
Gaussian marginal is exactly 6; the sparse rule reproduces it to rounding, whereas
50,000 independent samples retain Monte Carlo variation.

This is a callback-count comparison, not a measured end-to-end speedup. Rule setup,
function cost and acceptable error matter. For a mean or covariance already stored
on the kernel, read that value directly instead of running either method.

### 2. Validate a smooth nonlinear expectation before adopting the cheap rule

```scala
val curved = GaussVonMisesDistribution(Vector(0.0), Vector(Vector(1.0)),
  0.3, Vector(0.1), Vector(Vector(0.05)), 4.5)
val approximate = curved.thirdOrderQuadrature().expectation(p => math.cos(p.angle))
val analytic = curved.moments.meanCos
println((approximate, analytic, approximate - analytic))
```

Here cosine of the **physical angle** is generally outside the canonical exactness
class because the conditional center depends on the linear state. The test accepts
an absolute error below 0.002 for this particular weakly coupled fixture, and checks
the analytic answer against 80,000 independent samples. That tolerance is a fixture
acceptance criterion, not an API guarantee or a general coupling cutoff.

Prefer `kernel.moments` for its supported summaries. Use this comparison as a template
for evaluating a new function: sweep the relevant concentration/coupling regimes,
check bias against an independent answer, and reject the rule where error is too large.
Increasing a Monte Carlo reference sample size can reduce reference uncertainty;
increasing `maxNodes` does **not** refine this fixed-order rule.

### 3. Reuse one expensive evaluation to return several statistics

```scala
var calls = 0
val statistics = rule.expectationVector(3) { p =>
  calls += 1
  // Replace these expressions with several outputs of one expensive computation.
  Vector(p.linear(0), p.linear(1), p.linear(0)*p.linear(0))
}
println(statistics) // approximately Vector(1.0, -2.0, 5.0)
assert(calls == rule.nodeCount)
```

Three separate scalar calls would invoke callbacks three times as often. The vector
API computes the outputs together, without retaining all callback vectors. Calls are
sequential in node order. The counter above is only instrumentation; real numerical
callbacks should be deterministic and not mutate Figaro models or shared state.

You may also evaluate `nodes` and `weights` yourself when integrating with an external
batch evaluator. Preserve point/weight pairing and signed summation. They are not IID
samples, importance weights, or inputs to sample-based ESS/R-hat calculations.

## Exactness and a counterexample users should see

In canonical coordinates `z ~ Normal(0,I)` and `phi ~ vonMises(0,kappa)`, the rule
integrates constants, quadratic forms in z, cos(phi), cos(2 phi), and integrable
functions odd under z -> -z or phi -> -phi, provided the function is finite at the
nodes. It also matches each individual fourth moment E[z_i^4]=3. These statements
are mathematical exactness, subject to finite-precision implementation error.

It is **not** a tensor product rule. Cross-coordinate fourth moments and products
of even linear functions with angular harmonics need not be exact:

| Function / setup | Sparse result | True expectation |
| --- | --- | --- |
| z_0^2 z_1^2, independent standard Gaussian coordinates | 0 | 1 |
| z_0^2 cos(phi), uniform phi | 1 | 0 |
| exp(-sum(z_i^2)), six standard Gaussian coordinates | -0.900425863 | 1/27 = 0.037037037 |

The last function is smooth and strictly positive. Its negative estimate is a genuine
failure of this low-order signed rule, not a value to clip to zero. Likewise, estimated
probabilities can leave [0,1] and estimated covariance matrices can fail positive
semidefiniteness. Do not silently repair such outputs or treat positivity as the only
accuracy check. Use a validated alternative for the affected function.

All comparisons and this counterexample are exercised by the
[executable example](../FigaroExamples/src/main/scala/com/cra/figaro/example/documentation/GaussVonMisesQuadratureExample.scala):

```sh
sbt "examples / Compile / runMain com.cra.figaro.example.documentation.GaussVonMisesQuadratureExample"
```

## Numerical construction

The canonical rule has a center, two angular points at +/-eta, and two points at
z_i=+/-sqrt(3) for every linear axis. With `B_j = 1-I_j(kappa)/I_0(kappa)`:

```text
D = 4 B_1-B_2
eta = 2 asin(sqrt(D/(4 B_1)))
angular weight = B_1^2/D (each)
linear weight = 1/6 (each)
center weight = 1-2*angularWeight-n/3
```

The eta expression is algebraically equivalent to the paper's arccos formula but
retains small offsets at high concentration. D is of order kappa^-2, so subtracting
near-equal Bessel ratios directly eventually loses precision. Above kappa=50, the
implementation combines coefficients from the [NIST Bessel asymptotic expansion](https://dlmf.nist.gov/10.40)
before summation. Independent 80-digit fixtures cover zero, moderate concentration,
both sides of the numerical branch boundary, and kappa=1e8.

Physical nodes use the kernel's existing canonical inverse, including its Cholesky
coordinate convention and conditional angular center. Results use compensated signed
summation. No third-party implementation was copied or new runtime dependency added.

## Gotchas, resources and scope

- “Third order” describes this rule, not a universal error rate or an adaptive accuracy
  knob. There is no higher-order/refinement API in this milestone. Discontinuities,
  sharp likelihoods, strong nonlinearities and tails particularly need independent checks.
- Use periodic angular functions such as sine/cosine for circular summaries. Arithmetic
  angle means across the branch cut can be misleading regardless of integration method.
- The exactness class is defined in canonical coordinates. General approximations can
  depend on the parameterization/orientation of the nodes. Even at kappa=0 the stored
  coupling is used to map nodes, although the true uniform angular law ignores it.
- `maxNodes` is a guard, not an allocation request. The default 1001 permits at most
  499 linear coordinates; the maximum guard 10001 permits at most 4999. These are limits,
  not practical-performance promises. The guard is checked before node allocation.
- Stored physical nodes require O(n^2) coordinates. Current setup maps O(n) points
  through the dense canonical inverse, costing roughly O(n^3); retain a rule instead
  of rebuilding it. **Only the callback count scales linearly.** Evaluation adds O(n*m)
  accumulation work for m outputs and O(m) scratch storage beyond callback allocations.
- Null kernels/callbacks, invalid dimensions/budgets and nonfinite or wrong-length callback
  results throw `IllegalArgumentException`. Arithmetic overflow and coefficient convergence
  failures throw `ArithmeticException`. Callback exceptions propagate; no partial result
  is returned, and callback side effects cannot be rolled back.
- Extreme coupling can overflow node mapping even at kappa=0. Ill-conditioned covariance,
  huge physical locations and loss of phase precision retain the kernel's limitations.
  Finite inputs do not guarantee accurate or finite results. Small terms may underflow.
- Construction/evaluation checks interruption and throws `CancellationException` without
  clearing the flag. A user callback cannot be interrupted internally by this helper;
  cancellation is checked before and after it. Use cooperative callbacks for long work.
- Rule data are immutable and concurrent calls use separate accumulators. Thread safety
  of callbacks remains the caller's responsibility; Figaro universes/elements are not
  made thread-safe. No RNG is stored or consumed by the rule.
- This is an expectation utility for a fixed kernel, not posterior fitting, a sparse-grid
  sampler, a probability-region calibrator, or a reconstruction of an output distribution.
  It adds no report ingestion, fusion, filtering or propagation algorithm.

## Verification and next work

Local acceptance: 12 focused quadrature tests, all 257 modernization regressions across
20 suites, five GVM executable examples, Scala API generation and thin-library packaging.
The [focused suite](../Figaro/src/test/scala/com/cra/figaro/test/modernization/GaussVonMisesQuadratureTest.scala)
also covers exactness failures, callback accounting, vector validation, negative weights,
overflow, cancellation and concurrent reuse. The optional
[reference script](../tools/gauss_von_mises_quadrature_reference.py) uses `mpmath==1.3.0`
outside the Figaro runtime; run it with `python -B`.

Check the exact commit's [branch CI](https://github.com/mattwilkinsphoto/figaro/actions/workflows/ci.yml?query=branch%3Amodernize%2Fgauss-von-mises)
before declaring remote validation or main integration. Whole-library historical-suite
limits and existing release artifacts are unchanged.

Next quadrature work should provide a higher-order or positive-weight reference method
and compare accuracy versus function-evaluation cost, before using general numerical
expectations inside further divergence/entropy APIs. Bhattacharyya divergence and mutual
information remain separate lower-priority research; this rule alone does not validate them.

Related: [joint GVM](GAUSS_VON_MISES.md), [analytic moments](GVM_MOMENTS.md),
[state gradients](GVM_GRADIENTS.md), [score calibration](GVM_SCORE_CALIBRATION.md),
[KL diagnostics](GVM_DIAGNOSTICS.md), [roadmap](../ROADMAP.md), [API reference](api/README.md).
