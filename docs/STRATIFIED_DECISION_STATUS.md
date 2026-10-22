# Decision-focused stratification: prototype validation

Historical prototype milestone. See [production integration](STRATIFIED_INTEGRATION.md)
for the separately approved follow-up, which has now passed all integration gates.
The original results and then-applicable holds below are preserved as history.

[Research provenance](RESEARCH_REFERENCES.md#inf-15).
[Frozen protocol](STRATIFIED_DECISION_PROTOCOL.md).
[Preceding diagnosis](STRATIFIED_NUMERICAL_INVESTIGATION.md).

## Decision

**The isolated prototype passes its frozen numerical and fresh-seed validation.**
It is not integrated into the public API. All production Scala sources, the released
stopping estimator and existing public numerical refusal behavior remain unchanged.
Merge and release remain on hold pending review and production integration gates.

This result addresses the previous excessive refusal mechanism without removing a
guard or silently discarding positive mass. It does not establish universal sampling
accuracy, missing-mode recovery, an optimal ESS threshold or a new stopping theorem.

## What changed in the prototype

Instead of constructing a globally rounded CDF point, keep stratum j and finite-bit
coordinate u separate. Compare N*prefix against (j+u)*total with outward bounds.
When the sign is ambiguous, refine the **same draw** using original log weights and
directed decimal arithmetic at 40, 80 and 160 digits. Equal-log group identities
resolve exact ties. Zero support is never selected; true underflow is enclosed rather
than automatically treated as absent support. Unresolved choices fail explicitly.

The implementation is test-only, using existing JDK arithmetic. Its exponential
enclosure uses a documented Taylor remainder derivation; the independent Python
oracle uses Decimal.exp. No external algorithm implementation was copied or installed.
The protocol states bounds: N<=4096, log span<=1,000,000, 24 halvings, 512 series
terms and three decimal precision levels. These limits are narrower than the public
API's population limit and must not be silently transferred during integration.

## Executed validation

| Gate | Result |
| --- | --- |
| Final Scala suites | 25 passed: 9 prototype checks plus 16 unchanged API checks |
| Independent Python oracle tests | 4 passed |
| Captured-population and adversarial cases | 6,159 cases; 1,572,906 ancestor decisions matched |
| Independent oracle precision | 100 and 180 digits agree; maximum weight/prefix discrepancy below 1.24e-96 |
| Fast raw-prefix enclosure checks | No failures across the captured/adversarial populations |
| Decimal exponential enclosure controls | All 30 passed, through exp(-1,000,000) at all three levels |
| Captured-population refinement | None for the 6,144 ordinary captured vectors; five adversarial decisions resolved at 40 digits |
| Full refinement exhaustion | Explicit refusal verified for logs (0,-1e-200), exhausting all three tables through 160 digits |
| Fresh-seed filter trajectories | 12,288 per JVM, two JVMs; all completed, no refused/error rows |
| Independent whole-filter reference checks | All 576 passed |
| Cross-JVM replay | Scientific/status/work fields and refinement counts identical |
| Ordinary whole-filter decimal refinements | Zero in both runs |

Whole-filter runs used seeds 60001..60256, six existing fixtures, N=256, four
explicit policies and two methods (prototype stratified and unchanged log-tree).
There are 256 independent seeds per group, not 512: the second JVM is replay.
The 576 checks use the previously declared 6-SE regression screen, not a formal
confidence/precision guarantee. No threshold was selected from these outcomes.
All outcomes were retained; no failed trajectories were replaced or excluded.

The final tests add real 160-digit exhaustion, large-offset invariance and finite-grid
conditional offspring counts to the initial 23-test run. The prototype source and
frozen gates did not change after outcome inspection. Both test revisions/logs are
preserved, with the first revision verified against its captured SHA-256.

## Interpretation and remaining limits

The original failed populations now support decisions for actual sampled coordinates;
they do not need a distinct binary64 interval for every positive weight. Ordinary
completion no longer depends on rounding tiny intervals into existence. Independent
enclosure/decision checks support the numerical approach, but they are not a
machine-checked proof or a substitute for independent implementation review.

Finite-bit coordinates still differ from continuous uniforms. Correct decisions
relative to those coordinates do not make the floating-point filter exactly unbiased.
Extremely close boundaries can exhaust the precision cap, and more difficult inputs
can make refinement expensive. The ordinary benchmark did not exercise that cost;
no universal latency or performance claim follows from its zero-refinement result.
The descriptive RMSE results vary by fixture/policy; stratification is not uniformly
better than log-tree across this experiment. The retained comparison plot and CSV
include every completed group and are not a superiority test.

## Next gate before any merge/release

1. Review the directed bounds, remainder argument, tie semantics, finite-bit RNG
   contract and explicit resource limits. Decide how the existing public N limit
   interacts with this prototype's narrower validated scope; do not silently cap it.
2. Integrate the reviewed kernel into the opt-in fixed-budget API only, retaining
   explicit method/policy selection, diagnostics and terminal failure semantics.
   Do not change the released stopping estimator, log-tree method or defaults.
3. Repeat numerical/adversarial and full lifecycle tests through the public API;
   run the unchanged broader regression controls, packaged-consumer acceptance,
   generated documentation freshness and remote CI before considering merge/release.

No production integration, commit/push, remote CI, merge or publication occurred in
this prototype milestone. A separate reproducibility archive preserves inputs and
hashes, exact sources, versions, parameters, actual logs, all-outcome results and plot.
