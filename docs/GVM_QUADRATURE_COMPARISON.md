# GVM quadrature order comparison

## Overview: when to use it

Use this helper when you already have a **fixed GVM** and want to know whether a
deterministic expectation changes when you increase the integration orders. It wraps
the [positive-weight tensor reference](GVM_TENSOR_QUADRATURE.md); it is not a new
sampler, inference algorithm, or automatic stopping policy.

A single `kernel.tensorQuadrature(...).expectation(f)` returns one estimate. It does
not tell you whether Gaussian or angular resolution is inadequate. The comparison
helper evaluates the same function at four combinations, reports both sensitivities,
and checks their combined callback cost **before any rule is constructed**.

| | Baseline angular order A | Refined angular order Aref |
| --- | --- | --- |
| Baseline Gaussian order G | `baseline` | `angularRefined` |
| Refined Gaussian order Gref | `gaussianRefined` | `jointRefined` |

Every horizontal and vertical edge is checked, not just the diagonal. Opposite changes
can cancel when both orders increase; examining only baseline versus joint-refined
would miss that. Even all four estimates agreeing is **not proof of accuracy**.
The third pattern below demonstrates agreement near zero when the true answer is one.

Prefer [analytic moments](GVM_MOMENTS.md) when they cover your statistic. Use this
helper for low-dimensional, deterministic callback integration, especially while
validating an unfamiliar nonlinear statistic. For high dimensions or costly callbacks,
the tensor budget may make another integration method more practical. This helper is
explicitly opt-in; existing Figaro sampling and expectation calls are unchanged.

## Quick start in three steps

1. Import the API and define a fixed kernel (here one standard Gaussian plus an
   independent uniform angle):

   ```scala
   import com.cra.figaro.library.atomic.continuous.*
   val kernel = GaussVonMisesDistribution(
     Vector(0.0), Vector(Vector(1.0)), 0,
     Vector(0.0), Vector(Vector(0.0)), 0)
   ```

2. Choose both pairs of orders and a total callback budget:

   ```scala
   val plan = GaussVonMisesQuadratureComparison(kernel, 3, 7, 2, 4, 60)
   ```

3. Compare and inspect the estimates and changes:

   ```scala
   val result = plan.compare(p => math.exp(-p.linear(0)*p.linear(0)))
   println(result.jointRefined.head)
   println((result.maxGaussianChange.head, result.maxAngularChange.head))
   println((result.ordersAgree, result.evaluations))
   ```

This example deliberately has no angular dependence and kappa=0; angular orders 2 and 4
are appropriate **only for this example**, not a general recommendation. It reports
Gaussian sensitivity and rejects agreement at the default tolerances. The analytic
answer is `1 / sqrt(3)`; a higher-order estimate is not automatically accurate enough.

## API reference

All types are in `com.cra.figaro.library.atomic.continuous`. Construct the plan with
its companion factory; plan and result constructors are not public application APIs.
Scalar comparisons return one-element vectors, so scalar and vector results have
the same diagnostic contract.

| Public function | Parameters | Returns | Example |
| --- | --- | --- | --- |
| `GaussVonMisesQuadratureComparison.apply(kernel, gaussianOrder = 5, refinedGaussianOrder = 9, angularOrder = 64, refinedAngularOrder = 128, maxEvaluations = 100000)` | Non-null kernel; integers `1 <= G < Gref <= 32`, `2 <= A < Aref <= 256`; total budget in 1..1000000 | Immutable plan after budget preflight and construction of all four rules; any failed angular mass check rejects the whole plan | `GaussVonMisesQuadratureComparison(kernel, 3, 7, 64, 128)` |
| `plan.compare(f, absoluteTolerance = 1e-6, relativeTolerance = 1e-4)` | Non-null deterministic scalar callback, finite at every point; absolute tolerance finite and nonnegative; relative tolerance finite in [0,1]; at least one positive | One-component `GaussVonMisesQuadratureComparisonResult` | `plan.compare(p => p.linear(0)*p.linear(0), 1e-8, 1e-5)` |
| `plan.compareVector(outputDimension, absoluteTolerance = 1e-6, relativeTolerance = 1e-4)(f)` | Output dimension in 1..10000; same tolerances for each component; non-null deterministic callback returning a non-null finite vector of exactly that length | Multi-component result; one callback per point, not one per output | `plan.compareVector(2)(p => Vector(p.linear(0), math.cos(p.angle)))` |
| `result.ordersAgree` | No parameters | Boolean: every output passes all four edge checks; **not convergence or a certified error bound** | `println(result.ordersAgree)` |
| `GaussVonMisesQuadratureComparisonExample.main(args)` | Empty `Array[String]`; class is in the documentation example package | Unit; prints and checks the three patterns below | `GaussVonMisesQuadratureComparisonExample.main(Array.empty[String])` |

Public read-only plan fields:

| Field | Meaning | Example |
| --- | --- | --- |
| `baselineRule`, `gaussianRule`, `angularRule`, `jointRule` | The four immutable `GaussVonMisesTensorQuadrature` rules; expose their orders, node counts, angular mass checks and tail bounds | `plan.jointRule.nodeCount` |
| `evaluations: Int` | Exact total callbacks in one successful comparison, scalar or vector | `println(plan.evaluations)` |

Public read-only result fields:

| Field | Meaning | Example |
| --- | --- | --- |
| `baseline`, `gaussianRefined`, `angularRefined`, `jointRefined` | `Vector[Double]` estimates at the named corners; no automatic selection of a best estimate | `result.jointRefined.head` |
| `maxGaussianChange` | Componentwise maximum of `abs(gaussianRefined-baseline)` and `abs(jointRefined-angularRefined)` | `result.maxGaussianChange.head` |
| `maxAngularChange` | Componentwise maximum of `abs(angularRefined-baseline)` and `abs(jointRefined-gaussianRefined)` | `result.maxAngularChange.head` |
| `withinTolerance` | `Vector[Boolean]`, one all-four-edge agreement flag per output | `result.withinTolerance(0)` |
| `evaluations: Int` | Completed callback count for this successful result, including repeated points | `result.evaluations` |

For each edge with estimates `a,b`, the mathematical test is:

```text
abs(a-b) <= absoluteTolerance + relativeTolerance * max(abs(a),abs(b))
```

Each edge uses its own scale. Comparing a maximum change against only the joint estimate's
scale would be a different test. The implementation avoids overflowing the tolerance
sum; if an estimate difference itself overflows, it throws rather than reporting agreement.
Absolute tolerance has the units of the output. Relative tolerance is dimensionless.
Near zero, a positive absolute tolerance usually makes more sense than relative tolerance
alone. The defaults are convenience settings, not validated accuracy requirements for
your application. Both tolerances cannot be zero.

## Three common patterns

### 1. Replace a single estimate with a directional sensitivity check

Using the quick-start kernel:

```scala
def f(p: LinearAngular): Double = math.exp(-p.linear(0)*p.linear(0))

// Existing approach: one rule, one estimate, no order-sensitivity diagnostic.
val single = kernel.tensorQuadrature(3, 2).expectation(f) // 6 callbacks

// New approach: the same baseline plus three comparisons, explicitly budgeted.
val plan = GaussVonMisesQuadratureComparison(kernel, 3, 7, 2, 4, 60)
val r = plan.compare(f)
println((single, r.jointRefined.head, 1/math.sqrt(3)))
println((r.maxGaussianChange.head, r.maxAngularChange.head, r.ordersAgree))
```

Gaussian order changes the answer substantially; angular order does not for this
angle-independent function. That suggests investigating Gaussian order first. It does
not mean the angular discretization is adequate for a different callback. If agreement
fails, choose a new plan deliberately and compare against an analytic answer or an
independent method. There is no hidden loop that increases orders until a test passes.

### 2. Compare several statistics without multiplying point evaluations

```scala
val r = plan.compareVector(2) { p =>
  Vector(p.linear(0)*p.linear(0), math.exp(-p.linear(0)*p.linear(0)))
}
println(r.jointRefined)
println(r.withinTolerance) // Vector(true, false)
println(r.evaluations)     // 60, not 120
```

The Gaussian second moment is integrated correctly by these orders; the nonlinear
statistic remains sensitive to order. A single aggregate success flag would obscure
which statistic needs attention. Vector evaluation shares points, not all the work
inside your callback. If outputs have different units or scales, normalize them before
comparison or use separate comparisons with suitable tolerances. One absolute tolerance
is applied to every component; there is no per-component tolerance API.

### 3. Validate agreement against independent knowledge

```scala
val trap = GaussVonMisesQuadratureComparison(kernel, 3, 5, 2, 4)
val misleading = trap.compare { p =>
  val z = p.linear(0)
  val h3 = z*z*z - 3*z
  val h5 = math.pow(z,5) - 10*z*z*z + 15*z
  math.pow(h3*h5,2) / 295920.0
}
println(misleading.ordersAgree)       // true
println(misleading.jointRefined.head) // approximately zero; true expectation is ONE
```

This polynomial vanishes at the Gaussian nodes of both orders: they are roots of
the probabilists' Hermite polynomials H3 and H5. For a standard Gaussian Z,
`E[(H3(Z)*H5(Z))^2] = 295920`, obtained by expanding the polynomial and applying
`E[Z^(2k)] = (2k-1)!!`. Thus the normalized function has expectation one. Every tested
corner nevertheless agrees near zero. Changing tolerances cannot recover information
that none of these nodes sees. This is an intentional adversarial example, not a claim
that all practical integrands behave this way. It establishes why the API says
`ordersAgree`, not `converged`, `accurate`, or `errorBound`.

Run the [checked example](../FigaroExamples/src/main/scala/com/cra/figaro/example/documentation/GaussVonMisesQuadratureComparisonExample.scala):

```sh
sbt "examples / Compile / runMain com.cra.figaro.example.documentation.GaussVonMisesQuadratureComparisonExample"
```

## Gotchas, cost and failures

- Total callbacks for n linear dimensions are `(G^n + Gref^n) * (A + Aref)`.
  Defaults cost 2,688 in one dimension, 20,352 in two, and 163,968 in three. The third
  exceeds the default 100,000 guard. A budget covering only the largest individual rule
  is insufficient. Counts include repeated points; there is no memoization or cross-rule
  reuse of callback results. Reusing a plan saves rule construction, not evaluation.
- The budget caps callback count, not elapsed time, callback memory, or statistical error.
  Each underlying rule streams its points; the plan retains four small one-dimensional
  rule descriptions, not four materialized tensor grids. Cost remains exponential in n.
  All four evaluations run sequentially; this is not a parallel performance feature.
- All four rules must pass construction, including angular normalization, before any
  comparison can run. At nonzero concentration, a very low angular order can fail even
  for an angle-independent callback. Increase angular order explicitly; the helper does
  not suppress that failure. Angular mass and tail diagnostics certify neither the
  callback integral nor the observed order changes.
- Callbacks must be deterministic for these comparisons to mean anything. Random draws
  or mutable model state can create apparent order sensitivity unrelated to integration.
  Evaluation order is baseline, Gaussian-refined, angular-refined, jointly refined.
- Invalid orders, budgets, tolerances, dimensions and malformed/nonfinite callback outputs
  throw `IllegalArgumentException`. Failed angular normalization, arithmetic overflow and
  physical mapping checks throw `ArithmeticException`; underlying rule-generation and
  callback exceptions propagate. No partial result is returned and callback side effects
  cannot be rolled back. Counts after failure are not reported as successful results.
- Cancellation is checked in construction, between rules, throughout underlying evaluation
  and during diagnostics, preserving the interrupt flag. A running callback or third-party
  rule-generation call must cooperate internally; the helper cannot forcibly interrupt it.
- Plans and results are immutable, with separate evaluation buffers for concurrent calls.
  Your callback must be thread-safe if you share a plan. Figaro elements and universes do
  not become thread-safe. This is fixed-distribution integration, not posterior inference,
  GVM fitting, report ingestion, fusion, filtering or propagation.

## Verification, related work and next step

The [focused regressions](../Figaro/src/test/scala/com/cra/figaro/test/modernization/GaussVonMisesQuadratureComparisonTest.scala)
cover direct four-rule equivalence, exact counts, directional sensitivity, diagonal
cancellation, mixed-order interaction, the analytic false-agreement control, tolerances,
overflow, budgets, input failures, cancellation and concurrent reuse. The executable
example checks the three user patterns rather than only printing them.

Integrated on main at `4f90f815`, after [passing CI](https://github.com/mattwilkinsphoto/figaro/actions/runs/34181640610).
Local acceptance included all 281 modernization tests across 22 suites (13 focused
comparison tests), seven GVM examples, Scala API generation and library packaging.
The [roadmap](../ROADMAP.md) records the broader milestone sequence.
This addition does not replace the immutable RC1 library bundle or declare a tagged release.
The follow-on [Bhattacharyya assessment](GVM_BHATTACHARYYA_RESEARCH.md) now supplies
an angular-reduced integral, analytic series and independent accuracy controls. It is
research, not a public API. This helper alone does not establish a Bhattacharyya or
mutual-information API contract. Broader parameter
gradients and Hessians remain separate work, not prerequisites for this comparison helper.

Related: [tensor-rule numerical contracts](GVM_TENSOR_QUADRATURE.md),
[sparse quadrature](GVM_QUADRATURE.md), [analytic moments](GVM_MOMENTS.md),
[KL and residual diagnostics](GVM_DIAGNOSTICS.md), [joint GVM](GAUSS_VON_MISES.md),
[generated API reference](api/README.md), [wishlist](../WISHLIST.md).
