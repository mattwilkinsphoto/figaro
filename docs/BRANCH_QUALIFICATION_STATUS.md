# Posterior branch qualification and precomputation

**No guide-utility trial for this frozen workload; no promotion.** Independent
posterior qualification found no observation step with two branch regions each
carrying 5% probability. A validated spline also makes repeated forward-map queries
cheap after amortizing construction. Keep the branch builder test-only and the
shared pilot parked. Figaro 7.4.0, stopping, defaults and runtime are unchanged.

[Research provenance](RESEARCH_REFERENCES.md#inf-19),
[frozen protocol](BRANCH_QUALIFICATION_PROTOCOL.md),
[preceding screen](EXPENSIVE_LIKELIHOOD_RESEARCH.md),
[machine-readable results](../tools/branch_qualification_summary_v1.json).

## What was tested

This is an independently specified synthetic scalar repeated-reset experiment,
not a reproduction of published pendulum tracking or supplied application data.
The response integrates q''=-sin(q)-.15q', starting at q=.2 and velocity x, for
T=2 or 6. Each horizon uses observations [-.5,0,.5,0,-.5,.5], noise sd .2,
initial variance 1.21 and Gaussian transitions with mean .7 times the previous
velocity and variance .64. Parameters and observations were frozen before solving
the posterior. No filter samples or proposal utility benchmarks were run.

### Literature and implementation choice

Stuart and Teckentrup's [posterior consistency paper, preprint v2](https://arxiv.org/html/1603.02004v2)
motivates checking errors in the posterior, not only in the forward map. The
introduction and section 2 were inspected; the paper analyzes Gaussian-process
approximations, **not this natural spline**. Its hypotheses and guarantees do not
transfer to this experiment. Publisher/alternate PDF access failed; this is not
a full-proof review of the final journal version.

The independent reference uses SciPy 1.18.1
[solve_ivp with LSODA](https://docs.scipy.org/doc/scipy/reference/generated/scipy.integrate.solve_ivp.html),
distinct from the JVM's Commons Math DP853. The comparison reuses Commons Math's
existing [natural cubic spline](https://commons.apache.org/proper/commons-math/javadocs/api-3.6.1/org/apache/commons/math3/analysis/interpolation/SplineInterpolator.html),
with [SciPy CubicSpline](https://docs.scipy.org/doc/scipy/reference/generated/scipy.interpolate.CubicSpline.html)
as an independent reconstruction. No custom solver or interpolator was needed.
SciPy's BSD license was inspected and the dependency installed only for this
isolated research; no Figaro dependency was added. No external dataset or
implementation source was copied into Figaro.

## Independent posterior result

The LSODA/direct-DP853, tolerance-refinement and analytic linear-oscillator checks
passed all 1,926 comparisons. Maximum discrepancies were 1.15e-8, 3.76e-9 and
4.32e-10 respectively, below 1e-6. Simpson filtering integration needed the
predeclared .00125 spacing: the .005-to-.0025 comparison still differed by
3.98e-4, while the final refinement differed by 2.99e-8. Expanding [-8,8] to
[-10,10] then changed the monitored quantities by at most 4.27e-12. The analytic
Kalman control agreed within 3.08e-12. The maximum path-exit screen was 4.06e-14;
its numerical evidence denominator is not a certified interval bound.

T=6 turning points are approximately -2.02115 and 2.00536. Roots and sensitivity
signs were checked independently. Posterior mass in their three regions is:

| Observation step | Left | Central | Right |
| --- | ---: | ---: | ---: |
| 1 | 0.5691% | 98.2379% | 1.1930% |
| 2 | 0.0109% | 99.6362% | 0.3529% |
| 3 | 0.1144% | 99.6705% | 0.2151% |
| 4 | 0.1970% | 99.7825% | 0.0206% |
| 5 | 0.1924% | 99.3041% | 0.5035% |
| 6 | 0.0532% | 98.7223% | 1.2245% |

**Zero steps pass the frozen relevance gate; two were required.** These are
branch-region masses, not a proof of global mode completeness. Sub-5% branches
are not universally unimportant, especially for rare-event decisions. Numerically
zero tail entries in the raw output are floating-point results, not proofs of
exactly zero mass. No prior/evidence was retuned to make branches important.

The bounded sign-change recipe finds one T=2 root and three T=6 roots for each
distinct observation, within the four-anchor cap. It required 327–344 LSODA
calls per observation/horizon, about .16–.35 seconds in this Python implementation.
This is not a measured JVM guide-preparation cost, and the search cannot certify
absence of tangent, closely spaced or out-of-range roots.

## Precomputation result

All eight tables were retained. Selection used development points only; the
coarsest passing table then faced disjoint held-out points and posterior checks.
No validation query coincided with a training knot. Java and SciPy reconstruction
agreed within 3.56e-15 on the selected tables. Six outside-domain queries per table
and JVM matched the direct solver exactly, with explicit fallback counts.

| Horizon | Nodes selected | Spacing | Held-out max log-likelihood error | Max posterior metric error | Max total variation |
| --- | ---: | ---: | ---: | ---: | ---: |
| T=2 | 1,001 | .02 | 1.94e-7 | 1.24e-10 | 8.16e-11 |
| T=6 | 4,001 | .005 | 6.27e-4 | 6.86e-8 | 1.57e-8 |

Limits were .001 for pointwise log likelihood and 1e-4 for posterior metrics and
total variation. Moment errors are scaled by max(1,absolute reference moment).
The two coarser T=6 tables **failed** log-likelihood checks (held-out errors .3023
and .01139); their favorable aggregate posterior errors do not override that
failure. The 8,001-node table passed but was not substituted for the selected one.

Tables cover [-10,10] but lookup is restricted to [-8,8]; outside, the original
ODE is solved. Posterior reconstruction outside [-8,8] uses the independent direct
reference; exact JVM fallback identity is checked separately. This distinction,
finite point coverage and the tiny screened truncation mass prevent interpreting
these results as a uniform theorem or certification of arbitrary observations.

### Cold versus reused cost

Three sequential fresh JVMs each used three warm-up and seven measured batches.
Scientific values replayed identically. Ranges below span JVMs and the two query
sets (uniform midpoints and initial-prior quantiles), not confidence intervals.
These are descriptive local timings on a shared desktop, not isolated-host or
end-to-end filtering performance guarantees.

| Horizon | Cold table construction | Direct response/query | Hot lookup/query | Construction break-even |
| --- | ---: | ---: | ---: | ---: |
| T=2 | 39–52 ms | 5.0–8.6 microseconds | 20–33 ns | 4,628–10,389 queries |
| T=6 | 96–98 ms | 12.0–21.3 microseconds | 35–53 ns | 4,557–8,103 queries |

Cold means a new table with no reused node values, not a new JVM for every table.
It includes solving all nodes and spline construction. JVM startup, research
reference generation and any required deployment validation are not included.
The independent reference took about 82 seconds; captured logs retain analysis
and validation execution times separately.

For T=6, modeled costs at 384 queries are about 4.6–8.2 ms direct versus 96–98 ms
including a cold table. At 3,840 queries direct is still cheaper. At 38,400 queries,
direct costs about 461–816 ms versus 98–99 ms including the table. These are
products of measured query costs, **not executed filter speedups**. Changing model
parameters or horizon may invalidate the table; high-dimensional responses can
make tabulation impractical. Reuse, error validation and fallback frequency matter.

## Decision, counterevidence and next priority

Stop this workload before a new branch-guide utility trial. Its posterior does
not meet the prespecified branch criterion, and precomputation removes much of
the repeated-query cost opportunity. Neither result negates the preceding
equal-particle gains. Cold one-off costs remain a possible opportunity, and a
separately justified workload with substantial branch mass and a non-reusable or
higher-dimensional response could change the decision. Do not manufacture such
a workload by tuning this failed gate. Any follow-up needs independent truth,
a fresh frozen utility protocol and explicit review before promotion.

The recommended next capability assessment returns to higher-dimensional GVM
mixture fitting with GMM controls, within density representation rather than
application fusion. That is a recommendation, not implementation authorization.

## Verification and reproducibility

[Java comparison](../tools/BranchPrecomputation.java),
[independent analysis](../tools/branch_qualification.py), and
[seven analytic/failure controls](../tools/test_branch_qualification.py) are
research tools only. The evidence archive contains the frozen protocol, exact
sources, original input hashes, every grid/table result, three JVM logs, independent
query values, posterior arrays, versions, commands, timing metadata and a plot.
The first analysis completed but its recorder hit a filename collision; the error
and original results are retained, followed by a separate unchanged replay with
noncolliding metadata. No failed acceptance gate was relaxed or hidden.
The clean replay exited successfully and reproduced comparison.json byte for byte.
Seven analysis controls, 41 documentation tests, 18 tooling tests, the reference
index check and 15,709 local link targets in 204 Markdown files passed. Frozen
protocol and protected-source checks are recorded separately in the archive.

Rerun with Python 3.14, NumPy 2.5.1, isolated SciPy 1.18.1, Java 17 and Commons
Math 3.6.1. Set OPENBLAS_NUM_THREADS=1. Windows writers require FIGARO_ACCESS_HELPER
to point to the workspace's access-grant script. Run the reference command first,
then three sequential Java runs, then comparison with separate output/log names;
the archive README contains exact commands. No production/Scala tests, full CI,
packaged-consumer tests, merge or release are claimed by this numerical assessment.
