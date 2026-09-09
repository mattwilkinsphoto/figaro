# Figaro: probabilistic models in Scala

Figaro lets you describe uncertain quantities, their relationships, and observed evidence as Scala objects. Its inference algorithms answer questions such as “given this observation, how likely is that explanation?” You supply a model without implementing an inference engine yourself.

This modernized line uses **Scala 3.9.0 LTS, sbt 2.0.8, and JDK 17**. It keeps the `com.cra.figaro` packages but is a new Scala 3 artifact, not a binary-compatible replacement for `figaro_2.13`. It is a development snapshot, not a published stable release.

## Quick start: three steps

Prerequisites: Git, JDK 17 on your path, and an sbt runner. sbt downloads the compiler and uses `project/build.properties`; you do not need a separate Scala installation. The first build needs internet access.

1. Get the Scala 3 baseline on main:

   ```sh
   git clone --branch main https://github.com/mattwilkinsphoto/figaro.git
   cd figaro
   ```

2. Run the complete first example:

   ```sh
   sbt "examples / Compile / runMain com.cra.figaro.example.documentation.QuickStart"
   ```

   It prints `P(cause | signal) = 0.692308`. Read its [source](FigaroExamples/src/main/scala/com/cra/figaro/example/documentation/QuickStart.scala): create a model, observe evidence, run inference, query, and clean up.

3. Run and adapt the [three common patterns](docs/USER_GUIDE.md#common-patterns):

   ```sh
   sbt "examples / Compile / runMain com.cra.figaro.example.documentation.CommonPatterns"
   ```

   These demonstrate a discrete marginal, a Bayesian posterior, and approximate inference for a continuous model.

## Use Figaro in your application

From this checkout, run `sbt "figaro / publishLocal"`. In a separate Scala application's `build.sbt`:

```scala
scalaVersion := "3.9.0"
libraryDependencies += "io.github.mattwilkinsphoto" %% "figaro" % "6.0.0-modern.10-SNAPSHOT"
```

That coordinate resolves only after local publication, unless you separately publish it to a repository. Local publication is per user and machine. Producer and consumer must use the same local repository. See [installation and integration](docs/USER_GUIDE.md#installation-and-integration), including Java and fat-JAR usage.

## Documentation

- [Frozen vector proposal API](docs/VECTOR_IMPORTANCE.md): opt-in explicit-density
  importance sampling, caller-supplied or pilot-fitted proposals, separate budgets
  and query-specific health reports; locally validated on the statistical-validation branch.
- [Inference health and warnings](docs/INFERENCE_HEALTH.md): opt-in weight-tail,
  effective-sample-size, MCMC convergence and query-precision assessments, with explicit limitations.
- [Defensive importance research](docs/DEFENSIVE_IMPORTANCE_RESEARCH.md): pilot-inclusive
  proposal comparisons on concentrated posteriors; historical evidence behind the opt-in API.

- [Choose an RNG by purpose](docs/RNG_SELECTION.md): versioned execution-pattern
  presets, explicit overrides, hard requirements and recorded selection reasons.

- [Scientific RNG selection and migration](docs/RNG_ASSESSMENT.md): LXM default,
  Xoshiro256++, PCG, MT and Philox alternatives, seeded replay and benchmark evidence.
- [Versioned random streams](docs/RNG_STREAMS.md): opt-in native splitting/jumping,
  Philox counter ranges, consumption limits and start-of-stream replay descriptors.
- [Statistical validation](docs/STATISTICAL_VALIDATION.md): independent posterior
  references, false-alarm controls and importance-weight concentration findings.

- [Distribution constructions and Gaussian mixtures](docs/DISTRIBUTION_CONSTRUCTIONS.md): transformations, truncation, scalar/vector mixtures, zero-adjusted counts, Gaussian KL/Bhattacharyya and partitioned Gaussian MI; [acceptance status](docs/DISTRIBUTION_CONSTRUCTIONS_ACCEPTANCE.md).
- [Common distribution families](docs/COMMON_DISTRIBUTIONS.md): Student t, Cauchy, Laplace, lognormal, Weibull, triangular, Kumaraswamy, negative binomial and hypergeometric, with observation-ready adapters and parameter conventions.
- [Common information measures](docs/COMMON_INFORMATION_METRICS.md): same-family KL/Bhattacharyya and explicit finite-table mutual information, with analytic reductions, work budgets and numerical refusals.
- [Circular von Mises](docs/VON_MISES.md): reusable angular density/sampling, circular summaries, conditional observations and inference limits.
- [Joint Gauss-von Mises preview](docs/GAUSS_VON_MISES.md): tested linear-angular distribution, approved standalone publication scope and inference limits.
- [GVM diagnostics](docs/GVM_DIAGNOSTICS.md): canonical residuals, squared Mahalanobis scoring and analytic directed KL with Gaussian/circular checks.
- [GVM mutual information](docs/GVM_MUTUAL_INFORMATION.md): opt-in dependence between a fixed law's linear vector and angle, with deterministic integration and explicit numerical limits.
- [Cross-family information metrics](docs/INFORMATION_METRICS_ROADMAP.md): continuing KL, Bhattacharyya and MI support across distribution families, with applicability and validation gates.
- [GVM moments and conditionals](docs/GVM_MOMENTS.md): analytic circular/mixed moments and an exact angular conditional for hierarchical models.
- [GVM score calibration](docs/GVM_SCORE_CALIBRATION.md): finite-concentration probabilities and squared-score thresholds, with explicit comparisons against the chi-square approximation.
- [GVM state gradients](docs/GVM_GRADIENTS.md): analytic log-density and squared-score sensitivities in physical coordinates, with finite-difference comparisons and directional examples.
- [GVM sparse quadrature](docs/GVM_QUADRATURE.md): deterministic expectations with 2n+3 function evaluations, including exactness limits, negative weights and analytic/Monte Carlo comparisons.
- [GVM positive-weight reference](docs/GVM_TENSOR_QUADRATURE.md): adjustable-order streamed tensor expectations, with explicit accuracy-versus-cost comparisons and exponential node-budget guards.
- [GVM order-comparison diagnostics](docs/GVM_QUADRATURE_COMPARISON.md): budgeted four-corner Gaussian/angular refinement checks, with per-output sensitivity and explicit false-agreement examples.
- [GVM Bhattacharyya research](docs/GVM_BHATTACHARYYA_RESEARCH.md): tested numerical-method assessment and remaining production gates; not a public API.
- [Guarded GVM Bhattacharyya API](docs/GVM_BHATTACHARYYA.md): symmetric fixed-law comparison with exact reductions, bounded Fourier work and explicit unresolved outcomes; initial concentration limit 50.
- [GVM matched-accuracy timings](docs/GVM_BHATTACHARYYA_PERFORMANCE.md): measured tensor-grid savings, fresh/reused rule comparisons and faster problem-specific reduction counterexamples.
- [GVM numerical reliability](docs/GVM_BHATTACHARYYA_RELIABILITY.md): high-precision concentration/cancellation stress grid, unresolved-result guidance and remaining numerical limits.
- [Positive scalar GVM overlap research](docs/GVM_BHATTACHARYYA_POSITIVE_RESEARCH.md): bounded positive integration for cancellation-sensitive cases; research-only, not an automatic fallback.
- [Opt-in scalar GVM comparison](docs/GVM_SCALAR_BHATTACHARYYA.md): positive integration for cancellation-sensitive scalar overlaps, analytic shortcuts, estimated-error diagnostics and hard work budgets; integrated on main at CI-verified `21269b97`.
- [Scalar comparison performance](docs/GVM_SCALAR_PERFORMANCE.md): matched-accuracy timings of both APIs; Fourier first for ordinary coupled cases, positive integration for eligible unresolved overlaps, and explicit refusal-cost accounting.
- [Audited scalar integration totals](docs/GVM_SCALAR_AUDITED_TOTALS.md): profile-driven 2.9–7.75x positive-integrator improvements on three fixtures, retaining fresh final error checks; integrated on main at CI-verified `213aa587`.
- [Scalar tail-radius assessment](docs/GVM_SCALAR_TAIL_ASSESSMENT.md): research-only lower-bound strategy reducing initial panel work on curved fixtures; not yet a library change or measured speedup.
- [Scalar bounded-tail integration](docs/GVM_SCALAR_TAIL_PRODUCTION.md): automatic within the opt-in scalar API, explicit setup-budget semantics, held-out controls and about 2.53x on the costly curved fixture; integrated on main at CI-verified `f4884cfb`.
- [Scalar tail-bound JVM prototype](docs/GVM_SCALAR_TAIL_JVM.md): preserved prototype evidence and derivation; superseded by the integrated policy.

- [Roadmap](ROADMAP.md) and [capability wishlist](WISHLIST.md): family-first distribution expansion, starting with circular von Mises and joint Gauss-von Mises.
- [Distribution support inventory](docs/DISTRIBUTION_SUPPORT.md): existing elements, composition opportunities and inference/numerical gaps.
- [Classic tutorials in Scala 3](docs/TUTORIAL.md): the original greeting and Burglary models, with executable checks.
- [Legacy modeling companion](docs/LEGACY_MODELING_GUIDE.md): a bridge to the original advanced modeling, inference and extension material.
- [Documentation preservation map](docs/DOCUMENTATION_MIGRATION.md): what was superseded, what is archived, and how the LaTeX sources incorporate modernization.
- [Release notes](docs/RELEASE_NOTES.md): modernization changes alongside preserved historical release notes.

- [Core performance acceptance](docs/CORE_PERFORMANCE_ACCEPTANCE.md): cumulative measured gains, independent application consumption, artifact checks, and remaining integration boundaries.
- [Resource and scaling assessment](docs/RESOURCE_SCALING_ASSESSMENT.md): fresh-JVM memory, callback-allocation, longer-trace and overlapping-job controls; no new sampler defaults.
- [Gaussian block proposals](docs/BLOCKED_PROPOSALS.md): opt-in correlated moves, covariance selection, acceptance rules, and measured counterexamples.
- [Pilot proposal calibration](docs/PROPOSAL_CALIBRATION.md): estimate an inspectable fixed covariance from discarded pilot chains, then start fresh production sampling; includes rejection rules and pilot-inclusive comparisons.
- [MCMC reliability](docs/MCMC_RELIABILITY.md): understand precision failures, compare error estimates, and recognize exploration problems that a stopping rule cannot fix.
- [Sampling research](docs/SAMPLING_RESEARCH.md): recent literature, isolated quantile/multiproposal slice prototypes, and measured limits; not a new production inference API.
- [Matched-budget validation](docs/SAMPLING_BUDGET_VALIDATION.md): compare quantile and affine/polar research samplers with initialization-inclusive evaluation caps and explicit coverage checks.
- [Higher-dimensional validation](docs/SAMPLING_HIGH_DIMENSIONAL.md): GPSS and quantile research at 8 and 32 dimensions, including hard constraints and asymmetric modes.
- [Continuous-vector sampling](docs/VECTOR_SLICE_SAMPLING.md): opt-in GPSS/quantile over explicit log densities, with immutable traces, hard budgets, and cooperative cancellation.
- [Multi-chain vector sampling](docs/MULTI_CHAIN_VECTOR_SAMPLING.md): bounded independent-chain execution, deterministic seeds, complete budget accounting, and aligned coordinate diagnostics.
- [Vector scaling measurements](docs/VECTOR_SAMPLING_PERFORMANCE.md): matched-trace worker comparisons, effective samples per second, and diagnostic overhead.
- [Parallel coordinate diagnostics](docs/PARALLEL_VECTOR_DIAGNOSTICS.md): bounded scheduling, serial fallback, memory tradeoffs, and before/after verification.
- [Allocation and GC profiling](docs/VECTOR_ALLOCATION_PROFILE.md): opt-in JDK recording, allocation hotspots, and limits of memory-bandwidth inference.
- [Primitive diagnostic reductions](docs/PRIMITIVE_DIAGNOSTIC_REDUCTIONS.md): reduced boxing, exact-result checks, and measured end-to-end effects.
- [Primitive FFT autocovariance](docs/PRIMITIVE_FFT_AUTOCOVARIANCE.md): fewer complex-array temporaries, unchanged numerical results, and matched-work measurements.
- [Primitive diagnostic sorting](docs/PRIMITIVE_DIAGNOSTIC_SORTING.md): stable rank indices, primitive value sorting, exact tie handling, and measured tradeoffs.
- [Interleaved performance audit](docs/INTERLEAVED_PERFORMANCE_AUDIT.md): fresh-JVM paired comparisons, callback allocation attribution, and limits of causal interpretation.
- [Diagnostic hotspot study](docs/DIAGNOSTIC_HOTSPOT_STUDY.md): stable radix sorting for large nonmonotone ranks, protected merge paths, exact-result checks, and full-workload validation.

- [User guide](docs/USER_GUIDE.md): concepts, common patterns, gotchas, and related modules.
- [API guide](docs/API_GUIDE.md): practical contracts and examples for the main entry points.
- [Complete public-method reference](docs/api/README.md): compiler-derived signatures, parameter lists, returns, and invocation templates, including advanced and experimental APIs.
- [Migration changes](docs/MIGRATION.md): breaking changes, accepted workarounds, remaining risks, and upgrade checklist.
- [Deprecation retirement](docs/DEPRECATION_RETIREMENT.md): API replacements and lazy-collection behavior.
- [Parallel Monte Carlo](docs/PARALLEL_PERFORMANCE.md): opt-in seeded importance sampling, worker ownership, benchmarks, and limitations.
- [Multi-chain MCMC](docs/MULTI_CHAIN_MCMC.md): isolated MH chains, retained traces, R-hat/ESS/MCSE diagnostics, evidence restrictions, and end-to-end benchmarks.
- [Stopping criteria](docs/STOPPING_CRITERIA.md): Gaussian TSPRT, categorical KL, and opt-in scalar-mean precision stopping for multi-chain MCMC.
- [Build and verification](docs/BUILDING.md): sbt 2 commands, tests, coverage, publication, documentation generation, and Windows troubleshooting.
- [Library module](Figaro/README.md) and [examples module](FigaroExamples/README.md).
- [Engineering history](MODERNIZATION.md), [dependency inventory](DEPENDENCIES.md), and [JVM integration](CONSUMER_BOUNDARY.md).

Generate the searchable Scala 3 API site with `sbt "figaro / Compile / doc"`. Open `target/out/jvm/scala-3.9.0/figaro/api/index.html` locally. The checked-in `ScalaDoc/` tree is historical Scala 2 documentation, not this branch's API reference.

## Important limitations

Set evidence before starting inference. Query only targets supplied to the algorithm. Release an active algorithm with `kill()` when finished. Sampling estimates vary; they are not exact probabilities or confidence guarantees. Do not share mutable universes indiscriminately across threads.

All source sets and focused migration checks pass, but the entire historical test suite is not green. Timing tests remain advisory, some statistical tests are flaky, and OSGi deployment is unvalidated. On Windows, use separate sbt processes for coverage and normal packaging. The [migration guide](docs/MIGRATION.md) explains these limits.

## Provenance and license

This repository modernizes [Charles River Analytics Figaro](https://github.com/charles-river-analytics/figaro). Original authorship and history are preserved. See [LICENSE](LICENSE) and [FigaroAttributions.txt](FigaroAttributions.txt). The historical [release notes](https://github.com/charles-river-analytics/figaro/releases/download/5.0.0.0/Figaro_Release_Notes.pdf) and [tutorial](https://github.com/charles-river-analytics/figaro/releases/download/5.0.0.0/Figaro_Tutorial.pdf) explain the original project, not this branch's build or compatibility requirements.
