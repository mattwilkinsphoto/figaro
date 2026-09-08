# Common-family milestone acceptance

## Scope and integration status

The D3 milestone adds all nine selected representatives: Student t, Cauchy, Laplace,
lognormal, Weibull, triangular, Kumaraswamy, negative binomial and hypergeometric.
Each has an immutable numeric kernel, named Figaro factory and reusable fixed/dynamic
adapter. Same-family KL/Bhattacharyya follow the base distributions; explicit finite
probability tables supply categorical divergences and joint-table mutual information.

Implemented on `modernize/common-distributions` and integrated on main. Production source
commit `e78a6f0e023a96ffbb3ea5626d50f0a88f2c624b` passed the
[complete Linux CI workflow](https://github.com/mattwilkinsphoto/figaro/actions/runs/34243740997)
on 2026-09-08, including clean compilation, all mandatory regression gates, coverage
instrumentation/restoration, byte-for-byte clean rebuilds, classifier publication,
legal/artifact validation and independent published-consumer acceptance. Local acceptance
below also passes. The milestone includes
the preceding GVM MI implementation, whose [full CI passed at `fc4d23e8`](https://github.com/mattwilkinsphoto/figaro/actions/runs/34235233397).
This is a development snapshot, not a new stable release or replacement of the immutable
`6.0.0-modern.10-rc.1` bundle. No runtime dependency or inference default changes.

The [CI library bundle](https://github.com/mattwilkinsphoto/figaro/actions/runs/34243740997/artifacts/10063681442)
contains the built artifacts (subject to GitHub sign-in/retention). Alternatively, pull
main and run `figaro / publishLocal` to build the snapshot in your own local repository.
The final local thin JAR was independently consumed with SHA-256
`cdd5be44085c1400510953c768b3ee60a6af7ee3e597e44fa9ed030a3d37e56e`;
this identifies that Windows build, not a promised cross-platform artifact hash.

**Advisory exception:** the successful branch workflow reports one annotation from
`Observe legacy collection timing checks (advisory)`, which is explicitly non-blocking.
`SelectableSetTest.scala:160` failed its wall-clock enumeration-ratio assertion
(11.3568 was not below 1.1); 19 other checks in that step passed. This historical timing
test is not changed by D3 and is not represented as a passing test. All mandatory
correctness/integration gates passed; no new speedup claim relies on that advisory result.

## Reproduce the checks

Use JDK 17, Scala 3.9.0 and sbt 2.0.8 from this checkout:

```sh
sbt "figaro / Test / testOnly com.cra.figaro.test.modernization.*"
sbt "examples / Compile / runMain com.cra.figaro.example.documentation.CommonDistributionsExample"
sbt "figaro / publishLocal; figaro / assembly"
python3 -B -m unittest discover -s tools -p 'test_common_*reference.py' -v
python3 -B tools/check_acceptance_artifacts.py target/out/jvm/scala-3.9.0/figaro
```

The Python reference checks require research-only `mpmath==1.3.0`; it is not a library
runtime dependency. Follow the [separate consumer instructions](../tools/acceptance-consumer/README.md)
to validate the exact published thin-JAR hash, without a source-project/test dependency.
The CI workflow runs these controls alongside all existing modernization gates.

## Evidence and acceptance boundaries

- **344 modernization tests across 31 suites pass**, including 11 common-distribution
  and nine common-information test groups. These test groups contain many individual
  parameter cases; their count is not a claim of exhaustive parameter coverage.
- Density/mass and direct-tail controls use independently implemented 70-digit
  formulas. Student t checks include very small degrees of freedom and extreme finite
  arguments. Quantile round trips, family reductions, moments, endpoint singularities,
  invalid parameters, unrepresentable outputs and interruption are exercised.
  Tail-inverse controls include 100-digit lognormal quantiles at probabilities `1e-100`
  and `1e-250`, subnormal Kumaraswamy probabilities, narrow-mode triangular laws,
  scaled Cauchy tails and upper count quantiles within one ULP of probability one.
- Inference tests cover every new family: conditional posterior odds against direct
  likelihood calculations, constrained Metropolis-Hastings, seeded isolated multi-chain
  serial/parallel equality, and log-density importance weighting after raw-density underflow.
- Information tests compare 12 scalar pairs and three count pairs with independent
  high-precision integrals/sums. They check identity, direction/symmetry, restricted
  lognormal Gaussian/Mahalanobis reductions, real support infinities, budgets, cancellation,
  concurrency, rare finite-table overlap and MI as KL of joint versus product marginals.
- The executable example and independent published consumer pass. The latter loads
  all nine named adapters and checks scalar/count divergences and joint-table MI from
  the verified thin library. Thin, fat, source and Scala API JARs build successfully.
- Maximum-size (100,000-cell) finite probability tables are tested. Compensated
  normalization and marginal totals prevent accumulated rounding from rejecting a
  valid uniform table; numerical MI still retains its explicit error allowance.
- Six new Python oracle checks, seven artifact-validator tests and 18 documentation
  tooling tests pass. Compiler-generated documentation contains 11,758 public method
  entries; freshness and local link targets in 116 Markdown files are verified.

The full historical Figaro test suite is not declared green; existing compatibility
limitations remain. New elements do not acquire exact factor conversion, conjugate
learning or every inference algorithm merely by supporting sampling and log likelihoods.
Immutable kernels can be shared; live Figaro universes/elements remain worker-owned.

## What testing improved

The controls caught signed-zero and subnormal Cauchy tail boundaries, overflow-prone Student t tail
algebra, and near-canceling high-shape Weibull variance. Kumaraswamy variance explicitly
refuses unresolved moment cancellation. Incomplete-beta iteration work is bounded.
Numerical Bhattacharyya integration uses a bounded overlap integrand under an equal
mixture of the two laws instead of an unstable square-root importance ratio.

Numerical errors remain estimates, not certified coverage intervals. Kernel representability
limits are distinct from the narrower numerical-divergence limits; accepted parameters
do not guarantee every tail quantile or comparison resolves. Refusals have no usable
value and must not be replaced with zero. The finite-count tail bound controls omitted
mathematical terms but does not certify all floating-point arithmetic.

## Remaining scope, deliberately separate

Reusable transformations/truncation/mixtures, specialized flavors, parameter fitting,
multivariate families and comparisons between unlike families need additional contracts.
MI for a scalar marginal is not defined without a joint dependence model; partitioned
Gaussian MI and sample-based estimators remain on the cross-family roadmap. No fusion,
report ingestion or domain-specific state-processing capability is introduced here.
No performance speedup is claimed for these inverse-transform samplers.

Related: [distribution guide](COMMON_DISTRIBUTIONS.md),
[information guide](COMMON_INFORMATION_METRICS.md), [roadmap](../ROADMAP.md),
[information roadmap](INFORMATION_METRICS_ROADMAP.md), and [support inventory](DISTRIBUTION_SUPPORT.md).
