# Tied quantile diagnostic repair

Repeated MCMC states must remain on the correct side of a quantile cutoff.
The tail-ESS audit found that interpolating two identical floating-point values
could round below their common value, excluding tied draws from the indicator
`draw <= cutoff`. The repair returns an equal endpoint exactly. It changes no
sampling transition, quantile definition, ESS formula or acceptance threshold.

## Scope and rationale

Tail ESS is the smaller ESS of the pooled 5% and 95% quantile indicators.
[R's type-7 quantile definition](https://stat.ethz.ch/R-manual/R-devel/library/stats/html/quantile.html)
specifies linear interpolation of neighboring order statistics. Its
[maintained implementation](https://raw.githubusercontent.com/wch/r-source/trunk/src/library/stats/R/quantile.R)
skips interpolation for equal endpoints. This is an implementation reference,
not copied source code; no dependency or external code was incorporated.
[Posterior's tail-ESS documentation](https://mc-stan.org/posterior/reference/ess_tail.html)
describes the diagnostic, not a guarantee of convergence.

Figaro retains its original convex-combination arithmetic for unequal endpoints,
including protection against subtracting opposite-sign extreme values. The
internal quantile helper is package-visible for arithmetic regression tests,
not a new supported public API. This is a bounded tie repair, not a replacement
quantile library or a claim of correctly rounded interpolation in every regime.

The first retained failure had identical endpoints of -0.6568859528122107.
Interpolation returned -0.6568859528122108 and excluded ten tied draws. Its
tail ESS changed from approximately 1474.92 to 1438.35 under the independent
reference. The original failed study and numerical audit are preserved in the
separate research evidence; this correction branch does not incorporate that
experimental sampler or claim that it passed qualification.

## Validation contract

The separate numerical review freezes the following checks before full replay:

- Reproduce the exact tied-endpoint failure before applying the correction.
- Preserve identical finite endpoints across signs, exponents and subnormals;
  retain unequal-endpoint arithmetic and constant-tail behavior.
- Compare diagnostic integration with a direct autocovariance-sum reference.
- Replay all 5,798 saved summaries from all 272 original development attempts
  through the actual Scala implementation, without new sampling.
- Independently recompute diagnostics and quantile-indicator membership using
  NumPy/SciPy. Keep the original tolerances, relative `3e-8` and absolute `2e-9`.
  Require non-tail Scala diagnostic values to match the saved values bit for bit.
- List every changed tail diagnostic and qualification decision, including
  unchanged strict-calibration thresholds. Never reinterpret a refused pilot
  as newly successful production sampling.
- Verify that every other runtime source, all frozen protocols/results, stopping
  policies and defaults remain unchanged. Leave confirmation seeds unused.

The independent oracle explicitly handles exactly constant ranked chains; a
previous supplemental audit found that ordinary summed means could otherwise
leave spurious positive variance. That oracle correction is separate from the
runtime quantile repair. Original failed audits are not edited or retroactively
declared passing.

## Consumer implications

Repeated values are normal when MH rejects proposals, including for continuous
distributions. A one-representable-value cutoff error can therefore alter more
than one draw's indicator and materially change tail ESS.

Callers using tail ESS for warnings or calibration may see a different result
near their existing threshold. `GaussianBlockCalibration` recomputes diagnostics
and still requires its configured bulk, tail and mean ESS floors; the repair
does not relax any floor. Undefined discrete tails remain undefined.
The released mean-precision stopping decision uses bulk/mean ESS, R-hat and
mean error, not tail ESS; its estimator and defaults are unchanged. Its returned
diagnostic summary can nevertheless contain the corrected tail ESS or warning.

The learned dependence-pilot workflow remains closed after failing its original
reliability and cost gates. This correction cannot qualify or promote it.

## Implementation and checks

- [Runtime helper](../Figaro/src/main/scala/com/cra/figaro/algorithm/sampling/parallel/McmcDiagnostics.scala)
- [Arithmetic and diagnostic regression tests](../Figaro/src/test/scala/com/cra/figaro/test/modernization/TiedQuantileRegressionTest.scala)
- [Read-only Scala replay](../Figaro/src/test/scala/com/cra/figaro/test/modernization/TiedQuantileReplay.scala)
- [Independent replay audit](../tools/tied_quantile_audit.py)
- [Oracle failure-path tests](../tools/test_tied_quantile_audit.py)
- [Diagnostic provenance](RESEARCH_REFERENCES.md#dia-01)

## Executed result

All 5,798 repaired Scala summaries agreed with the independent NumPy/SciPy
calculation within the unchanged tolerances. Indicator membership changed at
100 quantile cutoffs, all with equal neighboring order statistics. Tail ESS
changed in 45 summaries: 23 decreased and 22 increased. Not every changed
indicator changes the reported minimum tail ESS; some tail diagnostics remain undefined.
Mean, SD, R-hat, bulk ESS, raw-mean ESS and MCSE matched their original Scala
values bit for bit. No projection eligibility, attempt qualification or default
strict-calibration decision changed on these retained traces.

The first reproduced discrepancy is now approximately 1438.35, agreeing with
the independent reference. This finite replay does not prove that another
workload near a threshold will have the same decision before and after repair.
See the [machine-readable result](../tools/tied_quantile_result_v1.json) for all
45 changes, 100 affected cutoffs, input hashes and validation versions.

In the original research checkout, the five new arithmetic/diagnostic tests and 58 existing diagnostic, calibration,
reliability and stopping tests passed together. A separate run of the 80-test
adapter/pilot/support/observation regression set also passed; these runs overlap
and are not 143 distinct tests. Six independent audit controls passed, including
wrong-tail, non-tail-drift, duplicate-record and threshold-boundary failures.

At that historical checkpoint this was a locally validated correctness fix, not a
published release. Remote CI, merge and publication were still separate gates. The
original learned-pilot audit remains failed and its no-promotion result unchanged.
The correction is isolated from that research on a branch based on `main`;
`7.4.1-SNAPSHOT` labels the unpublished development artifacts. CI explicitly runs
the tied-quantile regression suite in the multi-chain diagnostic gate.
The standalone main-based checkout also passed the 63-test targeted Scala set,
six independent audit controls, research-reference validation, 41 documentation
tests, 18 CI-tooling tests and local-link validation after generating Scaladoc.

## Integration and publication checkpoints

The original isolated [branch CI](https://github.com/mattwilkinsphoto/figaro/actions/runs/40366213712)
passed all five jobs at `43ce624e`. Integration with the subsequently published
retrospective and 7.4.1 preparation were subsequently approved; see the
[release gates](RELEASE_7_4_1_STATUS.md). This supersedes the earlier
future-CI wording above without rewriting the historical evidence. Publication
required separate approval. No experimental adapter or learned pilot is included.

Preparation subsequently completed: integration/post-merge CI and final-version
CI passed, with compatibility through 7.4.0 and all seven packaged consumers.
The [7.4.1 release record](RELEASE_7_4_1_STATUS.md) pins the independently
downloaded Linux bundle and records the extra Windows consumer checks, warnings
and resolved environment failures. After explicit approval, 7.4.1 was published
and verified: all four Central payloads match and all seven Central-only consumers
passed. This does not promote the original experimental pilot.
