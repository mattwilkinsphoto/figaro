# Calibrated guided-filter acceptance

Development milestone in 7.2.0-SNAPSHOT; not shipped in 7.1.0.
[Research provenance](RESEARCH_REFERENCES.md#inf-10).

- [x] Inspect Frankenfilter v2 sections 2/3, maintained `particles` GuidedPF source,
  and the original bounded-independent-sum inequality.
- [x] Implement split-pilot scale/work calibration, inclusive total work ceiling,
  explicit fixed-budget fallback, and frozen observation-informed proposals.
- [x] Reuse Gaussian kernels, RNG scope, filter lifecycle and corrected stopping.
- [x] Initial local run: 68 tests across five suites passed, including 14 new tests
  and the existing weighted/binary/health/integration regressions.
- [x] Final expanded local run: 71 tests across five suites passed, including exact
  small-count coverage of the pilot lower bound. The intermediate
  run failed to compile a new test's Java Thread callback; explicit Unit return
  corrected the harness without changing assertions or production behavior.
- [x] Independent rational joint-Gaussian oracle: all 12 study rows passed their
  predeclared six-standard-error gates; analytic references agreed within 1e-12.
- [x] User guide, generated API (13,127 method entries), reference integrity,
  41 documentation tests and 15,235 local link targets passed.
- [x] MiMa binary compatibility, packaged runtime/source/Scaladoc and four isolated
  hash-verified consumers passed. Existing four Scaladoc warnings remain unchanged.
- [x] Exact-source reproducibility record verified: 24 files with raw successful
  and failed logs, source, numerical inputs/results, artifact hashes and rerun instructions.
- [x] All four [remote CI jobs](https://github.com/mattwilkinsphoto/figaro/actions/runs/38298238170)
  passed for implementation commit `b629def1c56ffb38e92ef6053433f14ea6063a4d`:
  binary/TASTy compatibility, numerical/regression controls, clean byte reproducibility,
  artifact/legal checks and independently compiled Maven-layout consumers.

This acceptance update changes documentation only. The recorded run validates the
implementation commit above; any later documentation-only CI run has its own status.

No publication or merge into main is implied by these development checks.
