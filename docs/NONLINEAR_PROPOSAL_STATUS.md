# Nonlinear proposal experiment status

[Research provenance](RESEARCH_REFERENCES.md#inf-17),
[frozen protocol](NONLINEAR_PROPOSAL_PROTOCOL.md),
[assessment](NONLINEAR_PROPOSAL_RESEARCH.md).

Approved scope: complete the three stages as documentation and test-only work.
No runtime promotion, merge, release, default or stopping-estimator change.

- [x] Stage 1: contract/protocol frozen; independent references accepted.
- [x] Stage 2: builders and deterministic/regression validation.
- [ ] Stage 3: development calibration and fresh held-out validation.
- [ ] Independent results, archived evidence and promotion recommendation.
- [ ] Documentation/reference checks, commit/push and remote CI status.

## Stage 1

Independent NumPy density recursion and 2D integration passed all 334 numerical
reference checks and four self-tests. The first affine positive-mass comparison
failed (maximum 2.11e-9 versus 1e-9). The finer affine grid passes unchanged
tolerances. Original code, protocol and failed outputs are retained with the
evidence; the protocol documents the pre-candidate resolution amendment.

No candidate trajectories have been sampled at this checkpoint. Grid convergence
is not a rigorous global error bound. No candidate correctness, performance,
promotion eligibility or publication is established by reference acceptance.

## Stage 2 and development checkpoint

13 new prototype tests and 56 unchanged guided, stratified, weighted-bounded and
stopping regression tests passed (69 across five suites). The first compile failed
because uppercase tuple bindings were parsed as identifiers; the test-only syntax
was corrected. No algorithm, threshold or runtime change followed that failure.
Four oracle self-tests, two analysis inventory tests and three unchanged exact
finite-accounting tests also passed. The exact controls enumerate 48 combinations.

Development-only seeds completed; the protocol now records frozen larger-bootstrap
budgets. No held-out confirmation results have been examined at this checkpoint.
Both candidates remain in the comparison; no favorable configuration was selected.
