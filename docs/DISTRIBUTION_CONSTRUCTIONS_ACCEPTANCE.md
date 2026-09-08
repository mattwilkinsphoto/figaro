# D4 constructions and Gaussian mixture acceptance

## Scope and status

The initial D4 milestone is complete and integrated on main from
`modernize/distribution-constructions`, based on the previous main `465cbfa5`.
Final production source **`79615111153e2f6e9e9b203cd2dce8c757971439`** passed
[Linux CI](https://github.com/mattwilkinsphoto/figaro/actions/runs/34256474108), including
the final narrow-interval normalizer safeguard. The integration closeout changes documentation only.
No new stable release, immutable bundle, Maven Central publication or default sampler
change is included. The [user guide](DISTRIBUTION_CONSTRUCTIONS.md) defines the API limits.

Implemented scope:

- Affine and exponential transformations, including Jacobians; finite truncation
  with direct-tail normalization and explicit precision refusal.
- Finite scalar mixtures and full-covariance vector GMMs: log likelihoods, caller-owned
  sampling, moments, responsibilities, exact coordinate marginals and fixed/hierarchical
  observation-ready Figaro adapters.
- Count-only zero inflation/hurdles, with distinct zero-probability meanings.
- Gaussian KL/Bhattacharyya and partitioned joint-Gaussian MI, common-transform
  invariance and same-base zero-adjustment metric reductions.
- Stable legacy `AtomicMultivariateNormal` likelihoods and scoped kernel sampling;
  validation and seeded-sequence changes are documented in [migration notes](MIGRATION.md).

GMM fitting/EM, selecting/merging/pruning components, generic mixture divergences/MI,
arbitrary mixed measures, half-infinite truncation and arbitrary monotone callbacks are
not implemented. No Gaussian approximation is substituted for a mixture metric.

## Completed local checks

- Final full modernization run: **362 tests / 32 suites passed**, including 18 new
  construction/Gaussian regression groups, exact construction metric reductions,
  deterministic covariance reconstruction and isolated worker-count reproducibility.
- Established deterministic legacy continuous density gate: **10 tests passed**.
- The three-pattern runnable construction example passed, including a hierarchical
  GMM posterior near 0.2 and distinct zero-inflation/hurdle probabilities.
- **9 common-family/reference Python tests passed**, including three new construction
  oracle groups. The independent calculations use 50/70-digit arithmetic,
  inverse-matrix/determinant formulas, scalar integrals, mass sums and total covariance;
  mpmath is research-only, not a new library runtime dependency.
- Scala API generation and isolated local publication succeeded. Four pre-existing
  Scaladoc warnings remain. The independent published-JAR consumer passed all new
  construction/GMM/Gaussian-metric checks and its existing inference/lifecycle checks.
  Verified local thin-JAR SHA-256:
  `2c08085e1aa21f04fb629d360329b2a21322de6ede38d01cf0f956ced3e78294`.
  The final candidate includes direct base-draw affine/exp sampling; all 362
  modernization tests, the runnable example and the published consumer were rerun.
  The subsequent normalizer-cancellation safeguard also passed all 362 tests and the
  independent consumer; all four artifact classifiers passed content/legal checks.
- Public reference freshness verified: **11,895 public-method entries / 43 files**;
  documentation tests **18/18** and **13,058 local links / 118 Markdown files** passed.
  Final source [Linux CI](https://github.com/mattwilkinsphoto/figaro/actions/runs/34256474108)
  passed both jobs, including clean byte-for-byte reproducibility, source/API classifiers,
  legal contents, an independently published consumer and SBOM generation.

The [verified source JVM artifacts](https://github.com/mattwilkinsphoto/figaro/actions/runs/34256474108/artifacts/10068604925)
contain the CI-built library/classifiers. CI downloads may require GitHub sign-in and
are retention-limited, not an immutable stable release. Pin the source commit and
publish into a controlled artifact repository for long-term reproducibility.

The successful source run retained a **non-blocking legacy timing advisory**:
`SelectableSetTest` search ratio 2.6253 exceeded 2.4 and enumeration ratio 1.7316
exceeded 1.1. These checks were already `continue-on-error`; no tolerances, exclusions
or collection implementation were changed here. The resulting error annotation does
not mean a required job failed, and a successful workflow does not imply those timing
checks passed. The prior `1abd5f33` run also had an enumeration timing advisory.

## Exploratory legacy statistical suite

The full legacy `ContinuousTest` suite is broader than the existing required density
gate. An exploratory run passed 81/84 and failed three statistical checks:

- Atomic multivariate Normal sample covariance: first variance estimate 0.2484 versus 0.25.
- Compound Gamma importance-conditioned theta: 1.8123 versus 2.0.
- Compound Dirichlet importance-conditioned alpha1: 1.1243 versus 1.0.

These failures are retained, not hidden by a looser tolerance or changed CI exclusion.
The original `TTestResult.errorMessage` labels variance/sqrt(n) as a standard error;
that printed diagnostic is not the standard-deviation/sqrt(n) quantity. Its actual
accept/reject uses Apache's t-test. This task does not change that legacy helper.
An isolated unchanged-baseline run at `465cbfa5` passed 83/84, failing the kernel-density
sampling check instead (0.3386 versus 0.33). A controlled repeat that set the shared
Figaro RNG to seed **271828** before invoking the suite passed **84/84 on both baseline
and candidate**. No tolerances or legacy tests were edited. This supports a test-reliability
concern rather than a reproducible regression, but one paired seed does not establish
universal statistical calibration. New GMM sampling also has seeded moment checks and a
deterministic innovation-to-covariance reconstruction control.

## Reproduce

```sh
sbt "figaro / Test / testOnly com.cra.figaro.test.modernization.*"
sbt 'figaro / Test / testOnly com.cra.figaro.test.library.atomic.continuous.ContinuousTest -- -z "have the correct density"'
sbt "examples / Compile / runMain com.cra.figaro.example.documentation.DistributionConstructionsExample"
python -B -m unittest discover -s tools -p 'test_common_*reference.py' -v
sbt "figaro / Compile / doc; figaro / publishLocal"
python -B tools/docs/build_reference.py --check
python -B tools/docs/check_links.py
```

Then run the [independent consumer](../tools/acceptance-consumer/README.md) with the
just-built thin-JAR hash. The existing CI also verifies clean artifact reproducibility,
source/API classifiers, dependency/license packaging, and the separate consumer boundary.
