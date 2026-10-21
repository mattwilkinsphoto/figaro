# Figaro 7.2 release readiness

Baseline: published 7.1.0. **7.2.0 is published and verified on Maven Central.**
Final-version execution and publication evidence are recorded below.

## Scope and consumer impact

This compatible minor release adds an independent-pilot indicator-budget adviser,
weighted bounded bootstrap filtering, split-pilot score/work calibration,
observation-informed proposals, auxiliary ancestor selection and scalar/multivariate
Gaussian guidance. Existing ordinary filtering defaults remain unchanged.

The log-weight repair retains ancestors whose ordinary weights underflow, uses
log-domain ancestor selection and full auxiliary corrections, and exposes
`currentLogWeights`. Same-seed trajectories change. Ordinary summaries still have
floating-point limits; finite populations can still lose modes. Calibration does
not promise precision, mode discovery, or speedups for every model.

## Acceptance checklist

- [x] Integrated numerical repair: all 7,680 unchanged-protocol trajectories
  completed and all 450 original statistical checks passed. See the
  [benchmark record](CONTINUOUS_FILTER_BENCHMARKS.md) for failures before repair,
  independent controls, costs and remaining limitations.
- [x] Integrated snapshot passed all four [main CI jobs](https://github.com/mattwilkinsphoto/figaro/actions/runs/39644734681),
  compatibility checks, reproducibility and all four packaged consumers.
- [x] Final-version clean-build CI, artifact verification and packaged consumers.
- [x] Immutable final GitHub bundle and reviewed source/artifact hashes.
- [x] Signed Central deployment validated and published.
- [x] Four public Central artifact byte matches and all four Central-only consumers.

## Final execution and publication

All four [final-source main CI jobs](https://github.com/mattwilkinsphoto/figaro/actions/runs/39649444437)
passed at `12023c2c4fba152256aec7a0fd6bf31c66ad4e75`, including compatibility with
7.1.0, independent controls, clean byte-for-byte reproducibility, legal/artifact
checks and four packaged consumers. The downloaded bundle is non-candidate and
clean-source; all five contained artifact hashes were verified before release.

The immutable [v7.2.0 release](https://github.com/mattwilkinsphoto/figaro/releases/tag/v7.2.0)
uses that exact source. Bundle SHA-256:
`b6f4b831a3063f130afc39d3faf4b98ff111b8268515b54268a3189a2086462a`.
Thin JAR SHA-256:
`3532720d1b17c8580146b7b9c605ef2fbf46142af5b56a32e563f18e1444cf18`.
These are tested Linux CI bytes, not a cross-platform reproducibility claim.

[Signed staging](https://github.com/mattwilkinsphoto/figaro/actions/runs/39652578059)
validated deployment `01620fe1-39f5-48e3-88ad-d0cf2abb8f86`. The
[initial wait](https://github.com/mattwilkinsphoto/figaro/actions/runs/39652862059)
timed out in `PUBLISHING`; the
[same-deployment continuation](https://github.com/mattwilkinsphoto/figaro/actions/runs/39654901458)
confirmed `PUBLISHED`, all four Central byte matches and all four Central-only
consumers. No duplicate upload was made. Verification completed on 2026-10-21 UTC.

The fresh-runner consumers check the loaded JAR hash and exercise baseline,
integration, capability and budget/filter APIs, including retained log weights and
ordinary-underflowed ancestor recovery. Numerical benchmark evidence was retained,
not regenerated or reinterpreted by publication. Remaining limits above still apply.

The release adds no new scientific method beyond the reviewed implementation.
Scientific provenance remains mapped in [INF-13](RESEARCH_REFERENCES.md#inf-13).
See [release notes](RELEASE_NOTES.md), [auxiliary filtering](AUXILIARY_PARTICLE_FILTER.md)
and [Central publication](MAVEN_CENTRAL.md). Immutable artifact bytes are not rewritten
by subsequent documentation updates.
