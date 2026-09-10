# Figaro 6.1 release and acceptance

## Scope

6.0 is the modernization baseline. **6.1.0** consolidates subsequent additive
modeling/inference work and closes three bounded milestones:

1. A versioned consumable library, classifiers/POM, reproducible build, independent
   consumer and release checksums; not an application-specific runtime.
2. [Exact-coordinate partial copula evidence and conditional draws](COPULAS.md),
   including Gaussian/t latent conditioning and original-transform Jacobians.
3. [Public scalar-linear GVM-mixture fitting](GVM_MIXTURE_FITTING.md), [full-mixture
   linear/angular MI](GVM_MIXTURE_MI.md) and stronger GMM representation controls.

Existing packages, inference defaults and runtime dependency versions are retained.
Recompile consumers previously using modern.* snapshots. The 6.1 version denotes an
additive feature release, not proof of universal inference accuracy or thread safety.
Release candidates must complete the gates below before tagging/publishing.

## Installation and artifact contract

Figaro 6.1.0 is now published to Maven Central. Normal consumers need only:

```scala
scalaVersion := "3.9.0"
libraryDependencies += "io.github.mattwilkinsphoto" %% "figaro" % "6.1.0"
```

See the [Central publication record](MAVEN_CENTRAL.md). For the supplemental
compiled Maven-layout bundle instead, download the ZIP and
its checksum from the [6.1.0 release](https://github.com/mattwilkinsphoto/figaro/releases/tag/v6.1.0),
verify and extract it, then configure the extracted Maven directory:

```scala
scalaVersion := "3.9.0"
resolvers += "figaro-release" at file("/absolute/path/to/extracted/maven").toURI.toString
libraryDependencies += "io.github.mattwilkinsphoto" %% "figaro" % "6.1.0"
```

The file resolver above is only needed for the downloaded-bundle option, not for
normal Central consumption. The original archive predates Central publication;
its bytes and embedded distribution note remain unchanged. Its POM resolves runtime
dependencies normally; do not use a thin JAR without dependencies. See the bundle's
installation README for the exact resolver. The fat JAR excludes the Scala runtime
and is neither an executable nor a substitute for a tested dependency setup.

The old modern.10-rc.1 bundle remains immutable. No replacement of its bytes or
historical evidence is implied. OSGi deployment is not a validated release target;
the supported integration is ordinary JVM dependency resolution.

## Validation gates

- All 571 modernization tests across 58 suites passed locally on 2026-09-09.
  Sixteen new 6.1 tests include graph evidence, Gaussian/t density identities and
  seeded moments, non-Gaussian Jacobians, work limits, cancellation, circular seam
  handling, monotone accepted fitting traces, concentration/variance constraints,
  single-component MI reductions, multidimensional MI and independent positive
  physical-coordinate integration for mixture-induced dependence.
- Two empty-action-cache clean builds produced identical thin/fat JARs locally.
- All four artifacts passed class-content/legal/Java-17 checks.
- The independent consumer compiled against and hash-checked the published JAR,
  including the new conditional, fitting and information APIs. The Maven-layout
  bundle also passed locally with Ivy local excluded; CI repeats both consumer paths.
- Generated API freshness (12,748 public method entries), local links, 18
  documentation-tool tests, seven artifact-validator tests, four bundle-tool tests,
  and six independent numerical/evidence tests passed.
- [Release-branch CI](https://github.com/mattwilkinsphoto/figaro/actions/runs/34435163932)
  passed all three jobs on `9b4f24d8`, including the complete build and independent
  packaging preflight. The final main-branch CI must also pass before publication;
  the GitHub release records that run and carries its compiled artifacts.

No full historical stochastic-suite or every-platform certification is claimed.

Local reproducibility hashes (the release bundle records the actual published
artifact hashes; cross-platform byte identity is not assumed):

```text
60c50c9c8dac1b61064500ba25f5e1a9a7506b91d757e1abfd052bc2009e29c1  figaro_3-6.1.0.jar
63e2abfe072992cf3825f700895ff1f632bb56edd6a99840fff4d0e19173bc37  figaro_3-6.1.0-fat.jar
```

The release gate also caught three legacy Scala files committed with CRLF/mixed
line endings despite the repository's LF policy. Their line endings were normalized
without semantic edits. Fresh-checkout integrity is checked before packaging; the
guard was not bypassed. The hashes above are from two new clean builds after that repair.

## Stronger representation study

`Release61RepresentationStudy` uses five fixtures (three GVM-generated, two
Gaussian-generated), five independent training/evaluation seeds, three parameter
ceilings (6,13,27), and three methods: periodic-likelihood GVM EM, chart-conditioned
GMM EM, and a wrapped GMM control. This is 225 attempted comparisons per JVM.
Each trial has 800 training and 3000 held-out points, plus 3000 independent candidate
draws. Repeated JVMs repeat those datasets; they are not additional independent trials.

All candidate construction, chart selection and normalization are timed. Common
source-data generation is excluded. Fitting, scoring and sampling are reported
separately. Every refusal and nonconverged candidate remains in the record. GVM
initialization uses three restarts; GMM uses its existing deterministic EM initializer.
Training sample counts and parameter ceilings match, not exact optimizer work or
wall-clock budgets. Parameter slack and actual counts are reported.

The wrapped control wraps draws exactly; its density sums Gaussian images spanning
at least twelve conditional angular standard deviations, with a normalization test.
It fits EM in a declared training-selected chart, NOT by optimizing a wrapped
likelihood. This is a stronger control than raw chart moments but not an optimal
wrapped-model comparison. GVM candidates with iteration-limit status are scored as
such; the existing GMM fitter refuses its unconverged candidates. Compare status
counts as well as scores rather than treating these output contracts as equivalent.

The comparison does not establish GVM-mixture novelty, general superiority,
operational accuracy, or an application-specific filtering capability. Further
research includes higher-dimensional fitting and optimized wrapped-likelihood controls.

### Recorded results

The complete records are [JVM A](release61-representation-a.csv),
[JVM B](release61-representation-b.csv), and [JVM C](release61-representation-c.csv).
All non-timing fields replay identically across the three JVMs. These are five
independent data seeds, **not fifteen**; do not pool repeated rows for uncertainty.
The implementation is preserved in this release's source tag.

Mean held-out log density (nats/observation; higher is better), averaged over all
five seeds at the six-parameter ceiling where all methods returned candidates:

| Fixture | GVM | Chart GMM | Wrapped GMM |
|---|---:|---:|---:|
| Curved GVM | -1.354 | -2.846 | -2.834 |
| Angular-seam GVM | -1.503 | -1.957 | -1.903 |
| Separated GVM components | -2.646 | -2.641 | -2.645 |
| Local Gaussian | -1.054 | -1.053 | -1.053 |
| Broad wrapped Gaussian | -3.132 | -3.210 | -3.165 |

The curved fixture benefits substantially from the GVM representation. The local
Gaussian fixture does not. On the separated fixture, moving from one to two
components improves both GVM and wrapped GMM to about -2.387. Increasing to four
components does not consistently improve held-out scores; many GVM fits reach
their iteration limit and some GMM fits are refused. At higher budgets, reporting
only averages over returned GMM candidates would hide those refusals.

Per JVM, 32 of 75 GVM fits reported convergence and 43 reported an iteration limit.
Each GMM control returned 54 candidates and refused 21 fits (8 insufficient-component
and 13 iteration-limit outcomes). Those counts repeat across JVMs and are not
independent replications. No refused fit is imputed a score.

This is not a speedup claim: for the curved fixture, median total fit times across
the fifteen repeated timings were about 8.2 ms (one-component GVM) versus 1.1 ms
(one-component wrapped GMM), and 477 ms versus 39 ms at four components. Timings
are environment-specific and include warmup variation. The useful tradeoff here
is representation quality per parameter, with explicit optimizer cost and status.

## Reproduction

```sh
sbt "figaro / Test / testOnly com.cra.figaro.test.modernization.Release61ModelingTest"
sbt "figaro / Test / runMain com.cra.figaro.test.modernization.Release61RepresentationStudy 5"
python -B tools/test_release61_reference.py
python -B tools/test_release61_evidence.py
```

The JVM study runs forked, so repeating `runMain` launches fresh JVMs. Existing
source-independent numerical tools use research-only mpmath, not a runtime dependency.

Related: [roadmap](../ROADMAP.md), [migration](MIGRATION.md), [consumer boundary](../CONSUMER_BOUNDARY.md).
