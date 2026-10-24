# Expensive likelihood numerical revision

[Research provenance](RESEARCH_REFERENCES.md#inf-19).

This additive revision is frozen after the original
[protocol](EXPENSIVE_LIKELIHOOD_PROTOCOL.md) numerical screen failed, and before
any revised execution or timing. Preserve the original source and log. It is
not a change to the original result or permission for repeated tuning.

## Observed failure and bounded response

The initial DP853 absolute/relative tolerance 1e-8 run completed all 642 input
points. Fifteen endpoint checks exceeded 1e-6, at T=6 and x=-6.8,-6.75,-5.9,
-5.85,-5.65. The largest discrepancy was approximately 3.305e-6. All derivative
and analytic linear checks passed. No timing batches executed; no solver bug
or posterior bias is established. Local solver tolerances are not a bound on
global endpoint error.

One revision is allowed: use absolute/relative tolerance 1e-10 for the profiled
solutions, retaining the original 1e-11 comparison solutions, grid, horizons,
measurement noise, work cap and every original acceptance threshold. Include
GBS as a second profiled solver, checking its endpoint against both tight
solutions, its augmented endpoint against its ordinary endpoint, its sensitivity
against both finite-difference references, and its linear response analytically.
This adds six checks per input; no original check is removed. Both algorithms
must pass before profiling. Further failure ends this screen.

Three sequential JVMs retain three warm-up and seven measured sweeps. Rotate
the four endpoint/tangent and DP853/GBS cases forward/backward by batch parity.
Report each algorithm, horizon and JVM separately. Do not pick the slowest
solver to justify a guide. Neither setting is established as the cheapest
solver meeting an application likelihood-error budget. A future comparison
must consider that optimization and accurate interpolation for both filters.

The numerical accuracy requirement, not a desire to increase callback cost,
motivates this revision. It makes no filter-accuracy, speedup or promotion claim.
All remaining limits and independent-validation gates of version 1 still apply.
