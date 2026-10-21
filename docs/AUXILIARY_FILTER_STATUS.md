# Auxiliary particle filter milestone

Development work on 7.2.0-SNAPSHOT; not a published release.
[Research provenance](RESEARCH_REFERENCES.md#inf-11).

**Subsequent release gate:** the [broader continuous benchmark](CONTINUOUS_FILTER_BENCHMARKS.md)
initially exposed numerical refusals in standard nonlinear models. The subsequent
log-domain/tree repair completed all 7,680 identical-protocol runs and passed all
450 checks. Finite-population mode depletion remains; the repair is local and
7.2.0 publication stays on hold for review and release integration/CI/consumer gates.
The checked milestones below describe the earlier bounded implementation acceptance,
not completion of those remaining release gates.

## Requirements and acceptance checklist

Baseline: `dec2562e`; preserve unrelated local building/performance documentation.
Commit, push and merge after passing remote CI were subsequently approved.
Publication requires separate approval.

- [x] Inspect current contracts, primary APF construction/limitations, Frankenfilter
  stopping construction, and maintained `particles` source/license.
- [x] Opt-in auxiliary ancestor selection, full importance corrections, defensive
  support, diagnostics, lifecycle and calibrated bounded-work integration.
- [x] Scalar predictive and multivariate linear-Gaussian observation helpers,
  reusing existing Gaussian and Commons Math backends; no implicit jitter.
- [x] Exact finite controls, Gaussian controls, support/failure/RNG/lifecycle tests.
- [x] Predeclared nonlinear/multimodal and Gaussian comparisons with all seeds,
  total work, elapsed time, posterior error, evidence and ancestry reported.
- [x] User/API guide, research mappings, roadmap, CI registration, packaged consumer.
- [x] Executed regression, independent oracle, documentation and package checks.
- [x] Private reproducibility archive with exact source, inputs, scripts and raw logs.

## Acceptance policy

Exact finite expectation checks use absolute tolerance 2e-11; analytic Gaussian
densities/moments use 1e-10. Repeated-run unnormalized evidence/state-mass checks
use six empirical standard errors plus 1e-12, with 512 fixed seeds per mode.
Performance is descriptive, not a pass criterion. No seed deletion, selective
retry, assertion widening or successful-run conditioning. Synthetic controls are
explicitly not application validation. Tests distinguish fixed work from fixed
ceilings and include look-ahead/pilot overhead. Preserve failed execution logs.

## Scope exclusions

No automatic nonlinear fitting, optimized mixture-weight solver, lost-history
recovery, arbitrary mutable graphs, universal precision or default-method switch.
The auxiliary bounded-stop combination is an independently derived extension,
not a claim that the APF paper proves Figaro's variable-population implementation.

## Execution record

First local run: 40/41 tests passed. The new defensive-mixture test incorrectly
required bit-exact decimal arithmetic (`.85` versus `.8500000000000001`). Replaced
that assertion with a 1e-14 arithmetic tolerance; statistical gates unchanged.
The failed raw log is retained. The second run passed 81 tests and all 40
independent mean gates over 5,120 study runs (512 seeds, five modes, two models).
No collapsed outcomes were observed. The third run adds multivariate correlated
observations/unit changes and calibrated RNG isolation: 83 tests in six suites
passed, as did binary compatibility against published baselines and Scala 3 TASTy
compatibility with 7.1.0 (no exclusions). A 7.1.0-compiled filter extension also ran
against the replacement JAR. The four isolated consumers passed against the exact
locally packaged artifact, including both auxiliary advance APIs and vector guidance.

Scientific-reference checks and 41 documentation tests passed. Generated API
inventory: 13,140 public method entries; 15,272 local Markdown targets verified;
four-job CI YAML parsed. API generation retains the existing four warnings about
legacy refined parallel types/link resolution; it completed successfully.

Local runtime JAR SHA-256:
`05fbe3a20edcb82b0abbf405a1f64f231fea37a58291578fda49eee36b085745`.
This is a development artifact, not a newly published Maven Central release.
At the local-acceptance checkpoint, remote CI had not yet run. The new tests and
independent study checks are registered in CI. Integration must require passing
checks on the exact committed source; earlier branch CI does not validate this
change. Consult the [repository workflow runs](https://github.com/mattwilkinsphoto/figaro/actions/workflows/ci.yml)
for subsequent branch/main checks. Local packaging alone is not clean-byte release
reproducibility or publication evidence.

The private reproducibility archive was created and all 18 archived evidence
files verified against their SHA-256 manifest. Archive SHA-256:
`8c42445fc0ef6d4b98362c04fc7256f6c15d9371578da5a720d53bf292fe71ee`.
Its captured checklist precedes this post-archive verification update;
runtime/study sources are unchanged. Earlier intermediate source revisions were
not separately snapshotted; their actual logs are retained, not reconstructed.

The nonlinear benchmark is a five-state model with a nonlinear transition and
multimodal observation likelihood, not a continuous nonlinear application model.
The user guide records both favorable evidence variance and unfavorable cost/error
outcomes. No default switch or universal performance claim is justified.
