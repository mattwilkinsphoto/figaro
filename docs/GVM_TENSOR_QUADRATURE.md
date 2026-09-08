# Positive-weight GVM quadrature reference

## Overview

Use this adjustable-order tensor rule as a low-dimensional reference for expectations
under a fixed GVM. It combines Gauss-Hermite integration of the independent canonical
Gaussian coordinates with Gauss-Legendre integration of the angular residual, weighted
by its von Mises density. It uses existing
[Commons Math integration rules](https://commons.apache.org/proper/commons-math/javadocs/api-3.6.1/org/apache/commons/math3/analysis/integration/gauss/GaussIntegratorFactory.html);
no new runtime dependency or copied implementation is introduced.

Unlike [third-order sparse quadrature](GVM_QUADRATURE.md), this rule has nonnegative,
normalized product weights. A nonnegative callback therefore has a nonnegative estimate,
subject to floating-point limits. It also resolves cross-coordinate moments missed by
the sparse rule. **Positivity is not an accuracy certificate.** Orders must be checked
against analytic answers or independent integration, and sometimes Monte Carlo remains
the practical choice.

For n linear coordinates, Gaussian order G and angular order A, each evaluation costs
`A*G^n` callback invocations. This exponential growth makes it a reference for modest
dimensions, not a universal replacement for the sparse rule or sampling. Physical
points are streamed instead of storing the whole tensor product.

Source-development preview on `modernize/gauss-von-mises`, with its own CI/integration
gate. Main's verified baseline is recorded in the [roadmap](../ROADMAP.md). Existing RC1
binaries are unchanged. There is no fitting, output-distribution reconstruction, report
ingestion, fusion, filtering or propagation algorithm here.

## Quick start in three steps

1. Build a fixed kernel:

   ```scala
   import com.cra.figaro.library.atomic.continuous.*
   val kernel = GaussVonMisesDistribution(Vector(1.0), Vector(Vector(2.25)),
     0.3, Vector(0.7), Vector(Vector(0.4)), 4.5)
   ```

2. Choose a rule and retain it: `val reference = kernel.tensorQuadrature(25, 96)`.
3. Evaluate and check: `val estimate = reference.expectation(p => math.cos(p.angle))`.
   Compare with `kernel.moments.meanCos` in this supported analytic case.

This one-dimensional rule uses 2,400 callback invocations. Read analytic moments
directly in production when they already provide the quantity you need.

## Public API reference

| API | Parameters | Returns | Example |
| --- | --- | --- | --- |
| `GaussVonMisesTensorQuadrature.apply(kernel, gaussianOrder = 5, angularOrder = 64, maxNodes = 100000)` | Non-null kernel; G in 1..32; A in 2..256; budget in 1..1000000, covering A*G^n | Immutable streamed rule; construction checks angular normalization | `GaussVonMisesTensorQuadrature(kernel, 9, 64)` |
| `kernel.tensorQuadrature(gaussianOrder = 5, angularOrder = 64, maxNodes = 100000)` | Same orders and budget | New reusable rule for this kernel | `kernel.tensorQuadrature(9, 64)` |
| `reference.expectation(f)` | Non-null deterministic `LinearAngular => Double`, finite at every point | Compensated weighted sum, not an error-certified estimate | `reference.expectation(p => math.cos(p.angle))` |
| `reference.expectationVector(outputDimension)(f)` | Output length 1..10000; non-null callback returning a non-null finite vector of exactly that length | Immutable vector of estimates; callback runs once per point, not per component | `reference.expectationVector(2)(p => Vector(p.linear(0), math.cos(p.angle)))` |
| `GaussVonMisesTensorQuadratureExample.main(args)` | Empty `Array[String]`, documentation example package | Unit; prints reproducible accuracy/callback-count comparisons and checks analytic answers | `GaussVonMisesTensorQuadratureExample.main(Array.empty[String])` |

The result exposes these immutable fields; constructors and one-dimensional node arrays
are library-internal. There is no public materialized tensor-node collection.

| Field | Meaning | Example |
| --- | --- | --- |
| `gaussianOrder` | Points in each canonical Gaussian coordinate | `reference.gaussianOrder` |
| `angularOrder` | Angular integration points | `reference.angularOrder` |
| `nodeCount` | A*G^n callbacks for a successful scalar or vector evaluation | `reference.nodeCount` |
| `angularMassEstimate` | Numerically integrated angular mass **before** weight normalization; construction requires absolute difference from one <=1e-8 | `reference.angularMassEstimate` |
| `angularTruncationBound` | Bound on omitted angular probability; zero for the full angular interval | `reference.angularTruncationBound` |

Neither diagnostic estimates total expectation error. A mass check only tests the
constant function. A callback can be badly underresolved even when this check passes.

## Three common patterns

### 1. Cross-check a sparse estimate without signed weights

```scala
val sparse = kernel.thirdOrderQuadrature()
val tensor = kernel.tensorQuadrature(9, 64)
def f(p: LinearAngular): Double = p.linear(0)*p.linear(0)*math.cos(p.angle)
println((sparse.expectation(f), tensor.expectation(f)))
println((sparse.nodeCount, tensor.nodeCount))
```

This mixed function is generally outside the sparse exactness class. Comparing two
methods helps identify sensitivity, but agreement alone does not prove correctness.
Here an independent analytic answer is `kernel.moments.linearLinearCos(0)(0)`.

The sparse rule's six-dimensional `exp(-sum(x_i^2))` counterexample becomes positive
with the tensor rule. Low-order tensor estimates are still biased: the checked example
prints results for G=3,5,7, costing 1,458, 31,250 and 235,298 callbacks respectively,
versus 15 sparse callbacks. The recorded estimates show why a positive answer still
needs validation:

| Method | Callbacks | Estimate (truth: 0.037037037) | Absolute error |
| --- | --- | --- | --- |
| Sparse | 15 | -0.900425863 | 0.937462900 |
| Tensor G=3 | 1,458 | 0.101747785 | 0.064710748 |
| Tensor G=5 | 31,250 | 0.048572751 | 0.011535714 |
| Tensor G=7 | 235,298 | 0.039690865 | 0.002653828 |

Its angle is uniform and the function ignores it, so A=2
suffices **for that fixture only**. Its true expectation is 1/27.

### 2. Refine Gaussian and angular orders separately

```scala
val coarse = kernel.tensorQuadrature(9, 64).expectation(p => math.cos(p.angle))
val moreGaussian = kernel.tensorQuadrature(17, 64).expectation(p => math.cos(p.angle))
val moreAngular = kernel.tensorQuadrature(17, 128).expectation(p => math.cos(p.angle))
println((coarse, moreGaussian, moreAngular, kernel.moments.meanCos))
```

Raise G to resolve dependence on canonical linear coordinates and A to resolve angular
structure. Increasing `maxNodes` alone does not refine the rule; it only permits a larger
requested calculation. Orders are not automatically chosen or increased.

For an independent two-dimensional standard Gaussian and `f=exp(-sum(x_i^2))`, the true
expectation is 1/3. The executable comparison uses G=3,7,15,25 with A=2 (angle-independent
function), costing 18,98,450,1250 callbacks; its absolute error decreases at each tested
order and falls below 1e-7 at G=25:

| Gaussian order | Callbacks | Absolute error |
| --- | --- | --- |
| 3 | 18 | 0.133514114 |
| 7 | 98 | 0.00777855326 |
| 15 | 450 | 0.0000303707033 |
| 25 | 1,250 | 0.0000000297151012 |

This is fixture evidence, not a general convergence rate or a wall-clock benchmark.
Low-order tensor rules can be less accurate than sparse rules on some functions despite
costing more; choose based on measured error and cost, not the method's name.

Angular refinement needs its own check: for a uniform angle, eight Gauss-Legendre points
integrate the constant density accurately but can badly estimate cos(16*theta). The
tests compare that failure with A=128, which recovers its zero expectation. Narrow peaks,
oscillation, discontinuities and tails can defeat apparently stable low-order answers.

### 3. Share expensive callback work across several outputs

```scala
val reference = kernel.tensorQuadrature(25, 96)
var calls = 0
val values = reference.expectationVector(2) { p =>
  calls += 1 // instrumentation only; numerical callbacks should be deterministic
  Vector(p.linear(0), math.cos(p.angle))
}
assert(calls == reference.nodeCount)
println(values)
```

This performs one traversal, not two. Use a common expensive computation to produce
several outputs inside the callback. Each physical linear point is mapped once; its
angular residuals are then evaluated in ascending quadrature-node order. Canonical
linear tensor indices run lexicographically with the last coordinate changing fastest.
No RNG is used. Successive calls recompute callbacks and physical mappings; they do
not cache callback results or hold a full tensor grid in memory.

Run the [checked comparison](../FigaroExamples/src/main/scala/com/cra/figaro/example/documentation/GaussVonMisesTensorQuadratureExample.scala):

```sh
sbt "examples / Compile / runMain com.cra.figaro.example.documentation.GaussVonMisesTensorQuadratureExample"
```

## Numerical method and precision limits

Gauss-Hermite nodes for the weight exp(-u^2) are scaled by sqrt(2) to obtain standard
Gaussian coordinates, and their weights are normalized. In exact arithmetic, order G
integrates polynomials of degree at most 2G-1 **in each canonical Gaussian coordinate**.
That does not imply exactness for an arbitrary coupled angular function after mapping.

For the angular rule, let s=max(1,sqrt(kappa)) and t=s*phi. Integrate on
`[-min(Pi*s,12), min(Pi*s,12)]`, using Gauss-Legendre nodes and the exact von Mises
density divided by s. Scaling prevents concentrated angular peaks from disappearing
between unscaled nodes. The Gaussian angular-tail envelope used by
[score calibration](GVM_SCORE_CALIBRATION.md) bounds omitted mass. No Gaussian
approximation is substituted for the von Mises density.

Construction rejects an angular mass discrepancy above 1e-8, then normalizes the
angular weights. This prevents silently renormalizing severely underresolved density,
but does not certify any other integrand. For a bounded callback |f|<=B, omitted angular
mass contributes at most B times the tail bound to the **unnormalized truncated integral**.
Normalization and quadrature have additional error. Unbounded callbacks have no
expectation-error guarantee from a probability-tail bound alone.

The rule uses compensated accumulation and no negative coefficients. Bounded callback
estimates stay within their range in exact arithmetic; rounding/underflow may perturb
that property. There is no clipping, rigorous error interval, statistical confidence
interval, effective sample size or convergence flag.

## Gotchas and resources

- Tensor cost is exponential. At defaults G=5,A=64, six linear dimensions require
  1,000,000 callbacks and exceed the default 100,000 budget. This fails before rule
  generation. A larger budget is a deliberate cost decision, not an accuracy setting.
- Streaming avoids storing A*G^n physical points. Evaluation still pays roughly
  O(G^n*n^2) for dense physical mapping and O(A*G^n*m) for m-output accumulation,
  plus callback/state-construction costs. The retained kernel has dense O(n^2) storage;
  one-dimensional rules, work vectors and output accumulators are much smaller than
  the tensor grid. The budget limits callback count, not elapsed time or callback memory.
- Positivity does not remove deterministic bias. This is a reference method to validate
  for a workload, not a certified gold standard. Prefer analytic moments when supported,
  and independent Monte Carlo or other integration when order refinement is impractical.
- Inputs must describe one fixed GVM. It is not an arbitrary posterior or mixture rule.
  Canonical mapping and angular periodicity retain the joint kernel's numerical limits.
  At kappa=0, mapping still uses stored coupling; extreme coupling can therefore fail.
- Invalid inputs/budgets and malformed or nonfinite callback outputs throw
  `IllegalArgumentException`. Failed angular normalization, numeric checks or arithmetic
  overflow throw `ArithmeticException`; Commons Math rule-generation exceptions can
  propagate. Physical mapping is deferred to evaluation and may fail there.
- Callback exceptions propagate with no partial result; their side effects cannot be
  rolled back. Interruption is checked during work and before/after callbacks without
  clearing the flag. Third-party rule generation and a running callback cannot be
  interrupted internally by this helper. Long callbacks should cooperate with cancellation.
- The rule and kernel are immutable; concurrent calls use separate work buffers.
  Callback thread safety remains your responsibility. Figaro universes and elements
  are not made thread-safe, and no new inference algorithm or sampler is enabled.

## Verification and next work

Local acceptance: 11 focused tests, all 268 modernization regressions across 21 suites,
six GVM executable examples, Scala API generation and thin-library packaging. Tests
use analytic Gaussian integrals, independent analytic GVM mixed moments and circular
moments, including kappa=1e8. They also check refinement failures, positivity without
accuracy, callback counts, budgets, cancellation, exceptions and concurrent reuse.
See the [focused suite](../Figaro/src/test/scala/com/cra/figaro/test/modernization/GaussVonMisesTensorQuadratureTest.scala)
and [passing CI at `9cbcce00`](https://github.com/mattwilkinsphoto/figaro/actions/runs/34179606111).
This tensor milestone is integrated on main. Historical whole-suite limits remain.

The follow-on [budgeted order-comparison helper](GVM_QUADRATURE_COMPARISON.md) is now
integrated on main at CI-verified `4f90f815`. It checks four
order combinations and labels differences as diagnostics rather than rigorous error bounds.
See its analytic false-agreement control before interpreting a tolerance flag. This milestone
does not itself validate Bhattacharyya divergence, mutual information or other entropy
integrals. Those remain separate research/acceptance work.

Related: [sparse quadrature](GVM_QUADRATURE.md), [analytic moments](GVM_MOMENTS.md),
[joint GVM](GAUSS_VON_MISES.md), [roadmap](../ROADMAP.md), [API reference](api/README.md).
