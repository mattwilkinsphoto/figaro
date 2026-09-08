# Positive scalar GVM overlap: prototype history and preprocessing

The test-only Scala prototype at `a54d665e` passed [CI](https://github.com/mattwilkinsphoto/figaro/actions/runs/34195275750)
and is integrated on main. It has since been moved into the explicit
[public scalar API](GVM_SCALAR_BHATTACHARYYA.md). Its regression suite moved with it;
there is no duplicate test-only implementation to maintain. This page preserves the
preprocessing rationale. Use the public guide for current commands, parameters,
examples, limits and release status. The existing Fourier API remains unchanged.

The original prototype checked 84 scalar pairs in both directions, plus ten
unequal-concentration pairs in both directions. Maximum observed main-grid error
was 3.51e-10 nats at tolerance 1e-8, with at most 25,098 integrand evaluations.
Its full local gate passed 307 modernization tests; these are historical fixture
results, not a universal accuracy guarantee. The public successor adds analytic
shortcuts and near-limit tests and is integrated on main at CI-verified `21269b97`.

## What the preprocessing does

Let physical Gaussian variances be `P` and `Q`, and let `s=sqrt(max(P,Q))`.
Work with `vp=P/s/s`, `vq=Q/s/s`, and `d=(mean_q-mean_p)/s`. This avoids multiplying
potentially extreme physical variances or inverting a generic matrix.
The Gaussian contribution is
`d*d/(4*(vp+vq)) + log1p((vp-vq)^2/(4*vp*vq))/4`.
The public implementation uses this equivalent `log1p` form to retain tiny positive
variance differences; the original prototype used the difference of logarithms.

The bridge coordinate is a standard-normal scalar `z`. Each original canonical
coordinate becomes `offset + transform*z`. Stable offsets are
`sqrt(vp)*d/(vp+vq)` for p and `-sqrt(vq)*d/(vp+vq)` for q; transforms are
`sqrt(2*vq/(vp+vq))` and `sqrt(2*vp/(vp+vq))`. In particular, the q offset is not
computed by subtracting two nearly equal means.

Substitute these into `alpha + beta*z + Gamma*z*z/2` and subtract the two phases.
The resulting coefficients are the input to positive scalar integration. The
underlying positive-panel method, tail selection and discrepancy-based stopping
remain as described in the [research assessment](GVM_BHATTACHARYYA_POSITIVE_RESEARCH.md).
JVM integration uses compensated summation, a stable tie-broken refinement queue,
positive weights, retained panel samples and per-call work buffers.

### Preprocessing uncertainty stays separate

The prototype tracks the magnitudes of the **original operands**, not just their
possibly tiny differences. A large common coupling can otherwise cancel to a small
phase coefficient while masking sensitivity in the transformation.
Its heuristic coefficient-error allowance grows with variance contrast and the
phase magnitudes across the entire truncated integration interval.

For `a=kappa_p/2`, `b=kappa_q/2`, the angular affinity obeys
`abs(d log(h)/d delta) <= min(a,b)`. To see the bound, its resultant radius satisfies
`abs(dr/d delta) <= min(a,b)`, and `0 <= I1(r)/I0(r) <= 1`.
This converts a phase-error allowance to a log-affinity allowance. The Gaussian
contribution and final logarithm/subtraction also receive rounding allowances.
The derivative inequality does **not** make the upstream heuristic coefficient
allowance a proof. No claim of rigorous interval arithmetic is made.


## Related

- [Current public source](../Figaro/src/main/scala/com/cra/figaro/library/atomic/continuous/GaussVonMisesScalarBhattacharyya.scala)
- [Successor regression tests](../Figaro/src/test/scala/com/cra/figaro/test/modernization/GaussVonMisesScalarBhattacharyyaTest.scala)
- [Python research controls](GVM_BHATTACHARYYA_POSITIVE_RESEARCH.md)
- [High-precision fixture grid](GVM_BHATTACHARYYA_RELIABILITY.md)

No report ingestion, fusion, filtering or propagation is implemented.
