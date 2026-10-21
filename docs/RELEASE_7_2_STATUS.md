# Figaro 7.2 release readiness

Baseline: published 7.1.0. Publication of 7.2.0 is authorized, subject to the
final-version checks below. Preparation is not evidence of Central availability.

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
- [ ] Final-version clean-build CI, artifact verification and packaged consumers.
- [ ] Immutable final GitHub bundle and reviewed source/artifact hashes.
- [ ] Signed Central deployment validated and published.
- [ ] Four public Central artifact byte matches and all four Central-only consumers.

The release adds no new scientific method beyond the reviewed implementation.
Scientific provenance remains mapped in [INF-13](RESEARCH_REFERENCES.md#inf-13).
See [release notes](RELEASE_NOTES.md), [auxiliary filtering](AUXILIARY_PARTICLE_FILTER.md)
and [Central publication](MAVEN_CENTRAL.md). Publication results will be recorded
here after execution; immutable artifact bytes will not be rewritten.
