# Expensive likelihood relevance protocol

[Research provenance](RESEARCH_REFERENCES.md#inf-19).

Version 1 is frozen before model evaluation. This is a forward-model feasibility
screen, not a filtering experiment or an amendment to the failed
[multi-component protocol](MULTICOMPONENT_GUIDE_PROTOCOL.md). The shared pilot is
parked; runtime, stopping and defaults remain unchanged.

## Model and interpretation

Use a dimensionless damped pendulum response:

```text
q'(s) = v(s)
v'(s) = -sin(q(s)) - 0.15*v(s)
q(0) = 0.2, v(0) = x
h_T(x) = q(T), T in {2, 6}
y | x ~ Normal(h_T(x), 0.2^2)
```

This independently specified synthetic adaptation represents repeated reset
experiments with uncertain launch velocity and a scalar, unwrapped endpoint
measurement. It is not continuous two-state pendulum tracking. A future sequence
could give launch velocities Gaussian conditional laws; that modeling choice
and data sequence are not validated here. The numerical time integration is
necessary work for this damped nonlinear response, not sleep, repeated identical
evaluation or an artificially large mesh. Do not tighten tolerances or extend
the horizon to manufacture overhead after seeing results.

The two horizons are retained regardless of their outcomes. Values are engineering
choices, not taken from a fitted dataset or a cited paper's benchmark parameters.
The preliminary input grid is x=-8,-7.95,...,8 (321 values); it is not truncation
of a future Gaussian prior. Inspect crossings for y in {-0.5,0,0.5} and derivative
sign changes. Grid crossings are evidence of branches, never a certificate of
completeness or a count of posterior modes. No candidate anchors are selected
from posterior reference results.

## Numerical and timing screen

Reuse existing Commons Math 3.6.1 on Java 17, without installing dependencies.
Dormand-Prince 8(5,3) (DP853) is the profiled solver, min step 1e-10, max step .5,
absolute/relative tolerances 1e-8. Cap each call at 100000 RHS evaluations.
Compare every grid point with DP853 tolerance 1e-11 and the distinct
Gragg-Bulirsch-Stoer (GBS) algorithm at 1e-11. Same-library agreement is not an
independent filtering reference.

Also integrate the variational equations dq/dx=u, dv/dx=w, with
u(0)=0,w(0)=1, u'=w,w'=-cos(q)*u-.15*w, using tolerance 1e-8.
Compare endpoint sensitivities against central differences with step 1e-4,
using tight DP853 and GBS endpoints. Require endpoint differences <=1e-6,
derivative differences <=1e-4*max(1,abs(reference derivative)), and the augmented
system's endpoint difference <=1e-6. These are numerical feasibility tolerances,
not global error bounds. An analytic damped *linear* oscillator (replace sin(q)
by q) must agree within 1e-6 on the same inputs/horizons.

Profile DP853 endpoint and augmented endpoint/sensitivity calls, including solver
allocation, after 3 complete warm-up sweeps of the grid. Record 7 measured sweeps,
alternating the method order, in each of 3 sequential fresh JVMs. Consume all
outputs; print each batch's elapsed nanoseconds, call count and RHS count.
No parallel execution. This coarse batch profile is not a JMH speedup benchmark.
No subtraction of tiny loop costs or attribution of full-filter timing to it.
Record the scalar response and derivative for plotting, all failures, and versions.
Ceilings: 15 minutes per process, 1 GiB JVM heap, 250 MiB evidence. A failed
numerical check is retained and stops eligibility, not silently retuned.

## Decision gate

A model can justify a new frozen filtering protocol only if the response is
genuinely numerical, the numerical screens pass, and measured callback costs
are nontrivial compared with the prior study's guide-construction overhead.
Report costs descriptively: there is no universal microsecond cutoff and no
extrapolation of old accuracy ratios into a new model. Favor a horizon with
evidence of multiple branches; retain monotone or unfavorable controls.

Passing this screen establishes a plausible synthetic workload, not production
relevance, a complete <=4-anchor inventory, posterior truth or filter utility.
Before sampling: independently validate the true likelihood and filtering
references; freeze data, Gaussian laws, branch preparation and its full cost,
accuracy targets, budgets, new seed ranges and all-outcome acceptance rules.
Include bootstrap, a suitable model-specific manual guide, and the unchanged
branch candidate; compare equal elapsed time and time to prescribed accuracy.
Charge derivatives, root searches, reference-independent preparation and numerical
refusals. A lookup/interpolated forward model, if accurate, is a competing baseline,
not a cost that may be suppressed to make guidance look favorable.

If no qualified workload emerges, stop this line and return to other roadmap
priorities. Promotion always requires separate review and independent validation;
expensive evaluations cannot cure omitted branches or finite-population mode loss.
