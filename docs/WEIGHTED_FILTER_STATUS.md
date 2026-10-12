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
- [ ] Full source-specific remote CI (branch workflow records the current result).
- [ ] Exact-source reproducibility record and final handoff.

No third-party implementation code was copied. The inspected author implementation
uses R/C++ and had no declared repository license; no code or dataset assets were
incorporated. The selected equations are independently implemented in Scala with
existing Figaro infrastructure. Section 2 of preprint v2 retains the stopping and
ancestor correction, now numbered Algorithms 3/4 instead of v1's 3–5.

Out of scope: general graph adaptation, custom importance proposals, automatic score
calibration, a continuous-score budget adviser, posterior precision guarantees,
universal speedups and arbitrary-graph thread safety. Release approval is separate.
