# Modernization release notes

## Current development line

Figaro's modernized main uses Java 17, Scala 3.9.0 and sbt 2.0.8. It retains the
`com.cra.figaro` modeling packages but publishes a different Scala binary artifact:
`io.github.mattwilkinsphoto:figaro_3`. The source default is
`6.0.0-modern.17-SNAPSHOT`; it requires local publication or an explicitly configured
artifact repository. A locally distributed `6.0.0-modern.10-rc.1` integration bundle
was built from `9e939349`; it is not a Maven Central release or Git tag. This documentation
cleanup and attribution update do not alter that immutable bundle.

## Changes users can use

- `6.0.0-modern.17-SNAPSHOT` adds the opt-in [static graph executor](STATIC_GRAPH_EXECUTION.md):
  immutable callback-free scalar DAGs, isolated evidence/RNG state, deterministic
  worker-count replay and bounded lifecycle handling. Eleven focused tests and
  1440 timing rows cover the first vocabulary. Existing Element runners are unchanged.

- `6.0.0-modern.16-SNAPSHOT` adds opt-in [predictable empirical-Bernstein
  precision stopping](ADAPTIVE_BOUNDED_PRECISION.md), with a distinct weighted
  estimate, outward arithmetic, independent references and 480 paired timing rows.
  It saves work on low-variance fixtures but is slower for cheap high-variance
  draws. Existing defaults, MCMC/importance contracts and graph behavior are unchanged.

- `6.0.0-modern.15-SNAPSHOT` adds [bounded IID reliability](BOUNDED_IID_RELIABILITY.md):
  time-uniform conservative mean intervals with absolute precision/budget outcomes,
  and fixed-budget declared-region occupancy with optional external mass assumptions.
  Exact accumulation and outward rounding protect the post-callback arithmetic.
  These do not extend finite-run guarantees to MCMC or self-normalized importance.
  The [restricted graph ownership design](OWNED_GRAPH_EXECUTION_DESIGN.md) documents
  the proposed static executor; that executor is not implemented. Existing defaults
  and graph behavior are unchanged. Rebuild consumers at the new snapshot coordinate.

- `6.0.0-modern.14-SNAPSHOT` adds [conditional joint proposals](JOINT_PROPOSALS.md),
  [elliptical multivariate Student t](MULTIVARIATE_STUDENT_T.md), and fixed-budget
  [Monte Carlo information metrics](MONTE_CARLO_INFORMATION.md), including mixtures.
  The [4,500-row cost/query assessment](GRAPH_COST_ACCEPTANCE.md) includes pilot
  costs, held-out timing targets, explicit fit refusals and counterexamples to
  universal proposal improvements. Defaults and existing stopping policies are
  unchanged. Integration/package/CI verification is a separate gate.

- `6.0.0-modern.13-SNAPSHOT` adds [owned graph proposal integration](GRAPH_PROPOSALS.md):
  explicit joint-root importance correction with ordinary graph evidence, bounded
  attempts/traversal, detached results and a graph-pilot-to-mixture example.
  Existing sampler defaults and stopping policies remain unchanged.
- `6.0.0-modern.12-SNAPSHOT` adds [pilot-only Gaussian mixture fitting](MIXTURE_PROPOSALS.md),
  explicit work/data refusal states, independent numerical oracles and paired proposal
  evidence. Existing single-component fitting and inference defaults are unchanged.
- `6.0.0-modern.11-SNAPSHOT` integrated statistical validation on main:
  scientific RNG selection/streams, inference health, public frozen vector proposals,
  and the [calibration/robustness assessment](IMPORTANCE_CALIBRATION.md). The version
  increments for main integration; historical `.10` evidence and the immutable
  `.10-rc.1` bundle remain unchanged. No calibrated interval or automatic precision
  stopping is claimed. Rebuild consumers against the new snapshot coordinate.

- [Scientific RNG backends](RNG_ASSESSMENT.md): LXM becomes the Figaro-owned default,
  with explicit Xoshiro256++, PCG RXS-M-XS-64, MT19937 and legacy replay options.
  Seeded sequences change; recompile consumers and record backend/provider provenance.
  Includes complete timing and cross-generator evidence, not a universal speedup claim.
- [Statistical validation](STATISTICAL_VALIDATION.md): corrected printed standard
  error, fixed-data posterior references and evidence of importance-weight collapse.
  Legacy significance thresholds and tests remain in place.

- [Distribution constructions and GMMs](DISTRIBUTION_CONSTRUCTIONS.md): affine/exp laws,
  finite truncation, continuous scalar mixtures, full-covariance Gaussian mixtures and
  zero-adjusted count laws. Includes Gaussian KL/Bhattacharyya and partition MI,
  transformation invariance and same-base zero-adjustment metric reductions, stable
  observation likelihoods and scoped draws. The existing multivariate Normal atomic
  adapter now uses stable log likelihoods and explicit Gaussian numeric limits;
  [migration notes](MIGRATION.md) describe seeded-sequence and validation changes.
  GMM fitting and generic mixture information estimators are not included. See
  [actual acceptance status](DISTRIBUTION_CONSTRUCTIONS_ACCEPTANCE.md).

- [Nine common distribution families](COMMON_DISTRIBUTIONS.md): Student t, Cauchy,
  Laplace, lognormal, Weibull, triangular, Kumaraswamy, negative binomial and
  hypergeometric. Immutable kernels supply log density/mass, CDF, direct survival,
  quantiles and caller-owned sampling; named and hierarchical adapters use stable
  observed log likelihoods. [Information measures](COMMON_INFORMATION_METRICS.md)
  add same-family KL/Bhattacharyya and explicit finite-table MI with analytic reductions,
  numerical safeguards and work budgets. See [acceptance status](COMMON_DISTRIBUTIONS_ACCEPTANCE.md).
  No runtime dependency, sampler default, historical bundle or stable version is changed.

- [GVM mutual information](GVM_MUTUAL_INFORMATION.md): new guarded diagnostic for
  dependence between the full linear vector and angle of a fixed joint law. Uses
  analytic conditional entropy and one-dimensional numerical marginal entropy, with
  estimated-error intervals, exact independence shortcuts and explicit refusals.
  Integrated on main with the CI-verified common-family milestone; check its guide for
  numerical limits. The [cross-family roadmap](INFORMATION_METRICS_ROADMAP.md) records broader
  information-measure support as continuing work, not blanket current availability.

- [Scalar GVM bounded-tail selection](GVM_SCALAR_TAIL_PRODUCTION.md), integrated on main
  at CI-verified `f4884cfb`,
  reduces costly curved positive comparisons by about 2.53x in the paired study, with
  unchanged API signatures and retained tail/error checks. Other fixtures change little.
  Setup has a documented fixed cap outside the integrand budget. Existing immutable
  bundles are not replaced; rebuild current main to consume the change.

- Java/Scala/build migration and retirement of deprecated APIs; native Scala 3 dependencies,
  Scala `LazyList`, direct `Creatable` invocation, and a JDK-based anytime worker replace
  the former Scala 2/Akka assumptions. See [migration](MIGRATION.md).
- Opt-in seeded parallel importance and isolated multi-chain Metropolis-Hastings;
  chain diagnostics, bounded worker ownership and cleanup are explicit.
- Gaussian block proposals and pilot calibration, explicit-density vector slice samplers,
  and parallel vector chains. These do not automatically convert arbitrary graph models.
- Gaussian truncated SPRT and guarded MCMC precision stopping, with reliability checks
  and documented undercoverage/mixing limitations.
- Primitive reductions, FFT autocovariance and sorting improvements: the cumulative
  four-worker study measures 1.161-2.183x complete-inference gains across fixture/method
  medians. This is not a promise for every model. See [acceptance evidence](CORE_PERFORMANCE_ACCEPTANCE.md).
- Tested onboarding examples, a compiler-derived public-method reference, independent
  consumer checks, artifact/legal checks and environment-specific reproducible-build gates.

## What has not changed into a guarantee

Scala 2 applications must recompile/migrate. Arbitrary shared-graph threading is unsafe;
independent chains require separate owned models. Faster inference does not certify
convergence, coverage or unexplored modes. The full historical suite is not green, and
OSGi/custom loaders/Java facades require separate validation. Use application-level
acceptance before production. No public stable release is declared by these notes.

## Historical features are still part of the learning path

The old Figaro 5 release notes introduced LSFI, structured MPE, joint posterior sampling,
kernel-density elements, element-operation extensions, annealing changes and the debugger.
Those are historical features, not new modernization claims. In particular, their old
"up to two orders of magnitude" MH claim is **not** evidence of this modernization's speedup.
The [modeling companion](LEGACY_MODELING_GUIDE.md) maps these and the tutorial's deeper
topics to present source/API locations and explains validation boundaries.

Original release notes remain in the [archive](../doc/archive/README.md). For the full
checkpoint sequence see [MODERNIZATION.md](../MODERNIZATION.md). Attribution credits in
[FigaroAttributions.txt](../FigaroAttributions.txt) now include Matthew Wilkins and Codex
for the modernization effort without replacing the original contributors or license.
