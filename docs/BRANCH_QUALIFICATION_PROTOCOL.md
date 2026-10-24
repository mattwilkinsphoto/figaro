# Posterior branch and precomputation protocol

[Research provenance](RESEARCH_REFERENCES.md#inf-19).

Frozen before posterior calculation or spline evaluation. This extends the
[relevance screen](EXPENSIVE_LIKELIHOOD_RESEARCH.md), preserving its failed original
calibration. No shared-pilot work, filter sampling, production changes, stopping
or default changes, promotion, merge or release.

## Fixed scientific workload

Retain q''=-sin(q)-.15*q', q(0)=.2, q'(0)=x, unwrapped h_T(x)=q(T), T=2 and 6,
and observation noise sd .2. Two separate six-observation reset experiments use
y=[-.5,0,.5,0,-.5,.5]. Launch velocities follow X0~N(0,1.21) and
X_t|X_(t-1)~N(.7*X_(t-1),.64). Second arguments here are variances. This is a
synthetic repeated-reset experiment with correlated launch errors, not continuous
pendulum tracking or fitted application data. These Gaussian laws reuse prior
benchmark scales; do not broaden/recenter them after seeing branch probabilities.

Determine T=6 branch boundaries by roots of the endpoint sensitivity in the
previously observed brackets [-2.05,-2] and [2,2.05]. Independently verify their
signs and report the resulting three region masses, positive probability, mean,
second moment, |x|>3 probability and log evidence after every observation. T=2
has one branch/control. Record model-only likelihood roots for each y, using a
.05 grid on [-8,8] and bracketed root refinement; cap at four anchors, report
evaluation count/time and failures. Sign-change searches do not certify absence
of tangential/close roots or whole-real-line completeness.

The engineering relevance gate for this experiment is at least two T=6 branch
regions each having >=.05 posterior probability at two or more observation steps.
Report smaller branch masses rather than treating them as zero or universally
unimportant. Do not tune the prior or evidence to meet this criterion.

## Independent references

Use isolated SciPy 1.18.1 LSODA (ODEPACK), distinct from Java/Commons Math DP853,
with absolute/relative tolerance 1e-11, max step .5. Check against tolerance 1e-12
at the original 321 inputs per horizon and against analytic damped linear motion.
Endpoint agreement must be <=1e-6. Existing Java DP853 uses tolerance 1e-10;
compare its new outputs with LSODA, never with a fitted guide as truth.

Compute true scalar filtering densities by composite Simpson integration and
blocked Gaussian transition integration, no particle draws. Use (radius,spacing)
(8,.01),(8,.005),(8,.0025),(10,.0025). If the last resolution comparison fails,
the sole predeclared finer pair is (8,.00125),(10,.00125). Preserve every level.
Require changes <=1e-5 in each probability and log evidence, and <=1e-5*max(1,abs
fine value) in moments between final resolutions and expanded domains. An analytic
linear-observation Kalman recursion must agree within 1e-8 on the finest grid.

For domain truncation, report a conservative prior path-exit union bound using
the unconditional Gaussian variances, times the maximum likelihood density to
the number of observations, divided by computed truncated evidence. Require this
screen <=1e-6 at every step. This combines an analytic numerator bound with a
numerical denominator; it is not certified interval arithmetic. Never truncate
the eventual model or normalize transition rows to conceal escaped mass.

## Fixed precomputation comparison

Use Commons Math's existing natural cubic spline, built from independent direct
DP853 endpoints on [-10,10] at spacings .02,.01,.005,.0025 (1001 to 8001 nodes).
Each table is constructed cold without reusing previous tables. Charge ODE solves
and spline construction; record nodes and time. No custom interpolator. Query
the spline only on [-8,8]; outside that range call the direct solver, never clip,
extrapolate or return zero likelihood. Input/solver failures stay explicit.

Development validation: 1024 evenly spaced interior x=-8+(i+.37)*16/1024.
Held-out validation: 4096 points x=-8+(i+.73)*16/4096, disjoint from training knots
and development points except any arithmetic coincidences, which must be reported.
For each y=-.5,0,.5 require max absolute log-likelihood error <=.001 against LSODA.
Choose the coarsest development-passing table, then validate it on held-out points
and the converged filtering recursion. Keep all table outcomes. Failed held-out
selection ends eligibility; do not replace it with a different table post hoc.
Require each posterior probability/log-evidence error <=1e-4, moment error
<=1e-4*max(1,abs(reference)), and total variation <=1e-4 at every step.
These are empirical engineering checks, not a uniform interpolation theorem.

Verify Java spline values against an independent reconstruction of the emitted
knots using SciPy natural CubicSpline within 1e-9 on all development/held-out
points before using that reconstruction in reference-grid comparisons.
Explicit outside-domain points [-12,-10,-8.0001,8.0001,10,12] must use the exact
same direct solver result, with fallback counts recorded.

For costs, use three sequential fresh JVMs, three warm-up and seven measured
batches. Alternate direct/table order by batch parity, consume outputs. Each
batch queries 1024 fixed midpoint inputs on [-8,8], and separately 1024 quantiles
of N(0,1.21). Report cold time, hot median batch time/call and measured break-even
queries, plus cold/amortized modeled costs for 384,3840,38400 queries. These counts
represent 1,10,100 six-step N64 workloads, not executed filters or speedups.
Reference generation and independent validation are research costs reported
separately; any required deployment validation would add to construction cost.

## Decision and bounds

A validated cheap lookup weakens the expensive-likelihood rationale when reused;
a costly cold table may leave an opportunity for one-off workloads. Both outcomes
matter. If posterior branches fail relevance, do not start a guide utility study
merely because a callback is expensive. Independent model qualification and
precomputation acceptance cannot promote the branch guide: it still needs a new
frozen full-filter accuracy/cost protocol and explicit promotion review.

Limit execution to two serial hours, 1 GiB JVM heap, blocked reference workspace
under 1 GiB (excluding installed libraries/process overhead), 1 GiB durable
evidence. Use no statistical seed selection or filter trajectories. Preserve
all logs, failed checks, original inputs and SHA256 hashes in an evidence archive.
