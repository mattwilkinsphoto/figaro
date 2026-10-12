# Weighted bounded-filter acceptance

Development milestone in 7.2.0-SNAPSHOT, separate from the immutable 7.1.0 release.
[Research provenance](RESEARCH_REFERENCES.md#inf-09).

- [x] Inspect primary method and existing reference implementation availability.
- [x] Implement independent bootstrap/log-likelihood specialization with fixed
  bounded score policies, explicit failure and weighted ancestor selection.
- [x] Initial 39 focused tests passed, including exact two-step finite controls
  and predeclared 512-seed fixed/adaptive Kalman calibration.
- [x] Expanded lifecycle, concentration, long-sequence and independent oracle gates:
  54 tests across four suites passed; all six study rows passed their predeclared
  six-standard-error gates. Independent rational Gaussian conditioning agreed with
  the Kalman log evidence and first/second moments within 1e-12.
- [x] Local documentation and packaging: 41 tooling tests, regenerated API reference,
  four independent consumers and MiMa compatibility checks passed.
- [x] All four [source-specific remote jobs](https://github.com/mattwilkinsphoto/figaro/actions/runs/38291565136)
  passed for implementation commit `4e5f88792626b6efe340c22db4b89f16d71fee83`,
  including Scala/JVM compatibility, numerical controls, clean byte reproducibility,
  artifact checks and isolated Maven-bundle consumers.
- [x] Exact-source private reproducibility archive verified: 18 files with SHA-256
  manifests, actual local/remote logs, numerical inputs/results and rerun instructions.
  This status update changes documentation only, not the validated implementation.

No third-party implementation code was copied. The inspected author implementation
uses R/C++ and had no declared repository license; no code or dataset assets were
incorporated. The selected equations are independently implemented in Scala with
existing Figaro infrastructure. Section 2 of preprint v2 retains the stopping and
ancestor correction, now numbered Algorithms 3/4 instead of v1's 3–5.

The original milestone excluded custom proposals and automatic score/work calibration.
Those are now a separate approved [development extension](CALIBRATED_GUIDED_STATUS.md);
the successful CI above validates the original implementation, not that extension.
General graph adaptation, posterior precision guarantees, universal speedups and
arbitrary-graph thread safety remain out of scope. Release approval is separate.
