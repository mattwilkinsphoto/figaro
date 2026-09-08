# Modernization release notes

## Current development line

Figaro's modernized main uses Java 17, Scala 3.9.0 and sbt 2.0.8. It retains the
`com.cra.figaro` modeling packages but publishes a different Scala binary artifact:
`io.github.mattwilkinsphoto:figaro_3`. The source default is
`6.0.0-modern.10-SNAPSHOT`; it requires local publication or an explicitly configured
artifact repository. A locally distributed `6.0.0-modern.10-rc.1` integration bundle
was built from `9e939349`; it is not a Maven Central release or Git tag. This documentation
cleanup and attribution update do not alter that immutable bundle.

## Changes users can use

- [GVM mutual information](GVM_MUTUAL_INFORMATION.md): new guarded diagnostic for
  dependence between the full linear vector and angle of a fixed joint law. Uses
  analytic conditional entropy and one-dimensional numerical marginal entropy, with
  estimated-error intervals, exact independence shortcuts and explicit refusals.
  Initial implementation on the development branch; check its guide for integration
  status. The [cross-family roadmap](INFORMATION_METRICS_ROADMAP.md) records broader
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
