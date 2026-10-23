# Nonlinear proposal experiment status

[Research provenance](RESEARCH_REFERENCES.md#inf-17),
[frozen protocol](NONLINEAR_PROPOSAL_PROTOCOL.md),
[assessment](NONLINEAR_PROPOSAL_RESEARCH.md).

Approved scope: complete the three stages as documentation and test-only work.
No runtime promotion, merge, release, default or stopping-estimator change.

- [x] Stage 1: contract/protocol frozen; independent references accepted.
- [x] Stage 2: builders and deterministic/regression validation.
- [x] Stage 3: development calibration and fresh held-out validation.
- [x] Independent results and promotion recommendation: **no promotion**.

Evidence archive verification, Git delivery and remote CI status are recorded in
the accompanying handoff. They are separate from the scientific promotion gates.

## Stage 1

Independent NumPy density recursion and 2D integration passed all 334 numerical
reference checks and four self-tests. The first affine positive-mass comparison
failed (maximum 2.11e-9 versus 1e-9). The finer affine grid passes unchanged
tolerances. Original code, protocol and failed outputs are retained with the
evidence; the protocol documents the pre-candidate resolution amendment.

At the Stage 1 checkpoint, no candidate trajectories had been sampled. Grid convergence
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
budgets. No held-out confirmation results had been examined at that checkpoint.
Both candidates remain in the comparison; no favorable configuration was selected.

## Stage 3: completed, no runtime promotion

All 31,488 confirmation rows per JVM completed, covering 6,528 runs over 128
fresh seeds and eight fixtures. Three fresh JVMs replayed identical scientific
rows; this is 128 statistical replications, not 384. No refusal or unavailable
row was discarded. All **984 independent unnormalized-measure checks passed**.
The full scientific digests are recorded in the machine-readable analysis.

The accuracy/cost gates failed for every candidate. The following are measured
root primary losses (the protocol's combined moment/event metric), not physical
units, generic error rates or independent estimates of posterior uncertainty:

| Fixture | Bootstrap N64 | Cubature N64 | Jacobian N64 | Auxiliary+cubature N64 | Larger bootstrap |
| --- | ---: | ---: | ---: | ---: | ---: |
| Monotone, ordinary noise | .10285 | .09127 | .08774 | .09101 | .04237 (N337) |
| Monotone, sharp noise | .05514 | .04169 | .06030 | .04118 | .01969 (N400) |
| Asymmetric two-mode | .28891 | .31266 | .31994 | .31497 | .11479 (N407) |
| Far-tail observation | .31275 | 1.29188 | 1.47503 | 1.29188 | .31275 (N64) |
| Curved 2D, one observation | .15930 | .18872 | .20688 | .18872 | .15930 (N64) |

The strongest favorable evidence is the equal-N cubature improvement on ordinary
and sharp monotone observations. It does not survive total-cost comparison:
ordinary-noise median times in JVM 1 were .395 ms bootstrap, 2.116 ms cubature
and 2.124 ms larger bootstrap. Sharp-noise times were .392, 2.121 and 2.519 ms,
respectively; auxiliary+cubature used 2.849 ms. These are local warmed Java 17.0.4,
Scala 3.9.0, sbt 2.0.11 measurements, not cross-platform performance promises.
All primary cost ceilings passed across the three JVMs, but candidate root losses
were 2.07–3.06 times the larger-bootstrap losses, rather than at most .9 times.

Mode-retention counterevidence is also material. On the asymmetric fixture,
bootstrap depleted a mode in 1/128 runs, cubature in 2, Jacobian in 12 and auxiliary
cubature in 4. Jacobian exceeds the allowed excess of three runs. The unchanged
manual two-root recipe had root loss .18773, zero depleted runs and median .710 ms
there. It is model-specific and does not establish a generic mixture algorithm.
All three candidates passed the weak-observation loss margin, but this does not
offset failures of the designated primary gates. Full components, times and all
other configurations remain in machine-readable evidence.

## Interpretation and next recommendation

Correct weighting is not the same as a useful proposal. Repeated per-parent
construction is expensive in this deliberately uncached baseline (321 guide
constructions over six steps in ordinary guided runs). Yet faster construction
alone cannot explain or fix the worse tail and curved-observation accuracy.
The symmetric zero-gain control confirms the Gaussian approximation's structural
limitation; globally better moment integration would not remove it.

**Keep these builders test-only.** Do not change released defaults or stopping.
The next recommended assessment is a bounded scalar multi-component guide with
explicit mode/branch coverage and a normalized evaluable density, using the existing
manual two-root recipe and larger bootstrap as controls. Compare constrained local
branches with the existing pilot-fitted mixture machinery; include fitting cost and
pilot mode misses. This is a new design decision, not permission to add a solver,
learning dependency, automatic mode discovery or to retune this failed experiment.
Factorization reuse can be studied later, but should not substitute for resolving
proposal-shape inadequacy. No universal rejection of cubature/UKF or mixtures follows
from these eight bounded fixtures.

## Additional verification and evidence

The unchanged continuous benchmark completed 7,680 runs / 153,600 rows and passed
all 450 checks with no blocked checks. Final Python controls comprise four oracle
and four analysis tests; the latter reject missing inventories, duplicates and
nonfinite completed outcomes. A metadata-only clarification added the curved
fixture's vector covariance and corrected its unused scalar convenience sd field;
all numerical reference values and checks were verified identical, with both
revisions retained. No candidate inputs, sampling or acceptance rule changed.

Documentation reference checks, 41 documentation tests and 18 CI-tooling tests
passed. Protected runtime, build, workflow, released stopping and defaults remain
unchanged. No merge or release is requested by this result; 7.4.0 remains the
consumable baseline. Branch CI status and the private evidence archive receipt are
reported in the handoff; local acceptance does not imply a remote CI pass.
