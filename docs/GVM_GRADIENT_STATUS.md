# Analytic-gradient GVM fitting results

Research provenance: [GVM-06](RESEARCH_REFERENCES.md#gvm-06).

**All three research stages are complete; no promotion.** Analytic gradients
substantially reduced angular work and improved the frequency of meeting the
declared fitting criterion. They did not deliver reliable multicomponent fitting:
83 of 128 primary multicomponent fits still hit the outer iteration limit, and
final-responsibility diagnostics expose a distinction between likelihood
stabilization and whole-fit stationarity. All independent numerical checks passed.

The [frozen protocol](GVM_GRADIENT_STUDY.md),
[configuration/source hashes](../tools/gvm_gradient_protocol_v1.json),
[primary summary](../tools/gvm_gradient_summary_v1.json) and
[descriptive diagnostics](../tools/gvm_gradient_diagnostics_v1.json) retain the
complete result. Protocol/source commit `473caae0` was pushed before confirmation.
No held-out results were used to change candidates, caps, thresholds or seeds.
The previous [768-fit study](GVM_HIGH_DIMENSIONAL_STATUS.md) remains unchanged.

## What was implemented and independently checked

The candidate is isolated Python/SciPy research tooling, not a Scala library API.
SciPy L-BFGS-B solves the bounded angular problem using an analytic gradient;
the unchanged Java/Commons Math BOBYQA implementation supplies the baseline.
Both use the same three initialization partitions, including Java-generated
third-start random keys. No Figaro runtime dependency was added.

Five control groups passed: 60-digit complex-resultant derivatives and central
differences in dimensions 1–3; independent finite-difference SLSQP solves;
finite-difference L-BFGS-B ablation; bound-stationarity conventions; and density,
repeatability, monotonicity and explicit failure paths. The eight unchanged
GVM-05 reference groups also passed. A gradient from the same optimizer is not
counted as an independent validation reference.

Development comprised 40 complete fit attempts, including eight BOBYQA controls
and eight weak-concentration, overlap, minority-component and misspecified cases.
Some deliberately adverse fits refused or reached iteration limits; finite output
was not called a utility pass. No failed development execution required a repair.

## Complete held-out results

The new frozen grid contains 512 fits on 64 independent synthetic datasets:
384 primary GVM fits, 64 wrapped-GMM controls and 64 diagnostic fits. Each dataset
has 2,000 training and 20,000 independent test observations. Dimensions are two
or three linear coordinates plus one angle; primary GVM counts are K=1,2,3.
All 512 outcomes retained finite models; every refusal/limit remains recorded.

| Components | Analytic candidate meeting declared convergence criterion | BOBYQA meeting criterion | Other analytic outcomes | Other BOBYQA outcomes |
| --- | ---: | ---: | --- | --- |
| 1 | 64/64 | 64/64 | None | None |
| 2 | 41/64 | 0/64 | 23 iteration limits | 64 phase-budget limits |
| 3 | 4/64 | 0/64 | 60 iteration limits | 64 phase-budget limits |

The multicomponent completion gain is 45/128, about 35 percentage points, exceeding
the declared 20-point improvement target. But the candidate fails the requirement
of at least 95% completion in **every** dimension/family/K cell. With eight seeds,
that threshold required eight of eight per cell; it is not a population reliability
bound. The two-component seam truth met the criterion in all 16 paired cases
across dimensions, a useful narrow result, not justification for broad promotion.

All 24 paired score comparisons pass the predeclared 0.02-nats/point noninferiority
margin versus BOBYQA using Bonferroni-adjusted Student-t intervals over independent
training seeds. These compare retained finite-budget models, including unqualified
ones, not certified global optima. Score noninferiority cannot replace completion.

### Less phase work does not resolve outer convergence

| Components | Analytic median phase points | BOBYQA median phase points |
| --- | ---: | ---: |
| 1 | 96,000 | 867,000 |
| 2 | 5,670,000 | 20,000,000 |
| 3 | 12,295,000 | 20,000,000 |

Under the unchanged 20M/50-iteration primary budgets, no analytic fit exhausted
the phase cap. Instead, 83 multicomponent fits reached the outer iteration limit.
This identifies a remaining limit of the fitting workflow; it is not evidence
that Figaro's released inference stopping estimator is incorrect.

The K=3 diagnostic subset comprised eight curved/seam cases. Both methods exhausted
the reduced 2M and 8M phase caps in every case. At 20M and 200 outer iterations,
the analytic candidate met the declared criterion in four cases and exhausted
the work cap in four; BOBYQA exhausted it in all eight. The matching primary
subset had one analytic completion. These diagnostics do not substitute for the
failed 50-iteration primary cases or authorize larger library defaults.

Wrapped-GMM K=4 controls met their criterion in 6/64 primary fits. On their matched
16-case diagnostic subset, increasing the iteration ceiling from 50 to 200 changed
completion from 2/16 to 12/16. More fitting did not consistently improve held-out
score. Neither family should be described as fully optimized in this comparison.

### Stabilization is not whole-fit stationarity

The recorded `Converged` status means that the outer mean likelihood improvement
was at most 1e-5 and the last component solves passed their projected-gradient
checks under their **M-step responsibilities**. It does not mean that every
gradient of the final mixture likelihood is negligible.

An independent post-fit diagnostic recomputed phase gradients after refreshing
responsibilities from each final model. All 45 multicomponent primary fits labeled
`Converged` exceeded the inner 1e-6 gradient threshold under those refreshed weights;
the maximum was about 0.00137. The 64 single-component fits did not exceed it.
This does not invalidate their declared status, but it limits what that status
establishes. Do not advertise the 45/128 figure as 45 strictly stationary solutions.
The diagnostic checks phase blocks, not a full covariance/weight KKT certificate.

This distinction is consistent with the need to separate fixed-point convergence
from statistical/global-optimum guarantees discussed by Balakrishnan, Wainwright
and Yu, [Statistical guarantees for the EM algorithm](https://www.stat.cmu.edu/~siva/publications/EM_full.pdf)
(2017; abstract and introduction inspected). Their guarantees require model and
initialization conditions that have not been established for this GVM adaptation.

## Independent audit and measured costs

The all-model audit passed: 128 saved input hashes, complete/unique inventory with
missing/duplicate negative controls, parameter envelopes, accepted likelihood
traces, retained training/test scores and declared inner convergence statuses.
Across 1,024 points evaluated using independent 60-digit full inverse/determinant
densities and explicit wrapped sums, the largest absolute log-density difference
was below 4.9e-15 (tolerance 1e-8). Maximum independently recomputed training-total
error was below 3.1e-11. These checks do not prove global optima or untested regimes.

The full grid took about 475 seconds on the measured machine. Median primary
K=2 core fitting times were approximately 0.26 seconds analytic versus 0.69 BOBYQA;
K=3 was approximately 0.54 versus 0.69 seconds. Many fits ended at different kinds
of limits, so these are not time-to-convergence comparisons. Different backends,
JVM warm-up, extra gradient arithmetic and ordinary desktop scheduling prevent an
intrinsic algorithmic speedup claim. Short reference/tooling checks ran concurrently
with part of the grid. Phase counts, density work and actual process timings are
retained; no controlled wall-time superiority is established.

## Decision and next bounded question

Retain the analytic gradient as the stronger **research candidate**, without
promoting a public fitter. Numerical correctness, retained-score noninferiority
and the completion-improvement target passed; per-cell reliability failed.

The next useful assessment is whole-fit convergence and model complexity:

1. Specify a practical final-responsibility/stationarity diagnostic before
   choosing an acceleration method. Keep likelihood, stationarity and predictive
   quality separate; do not retrospectively relabel this study's outcomes.
2. Compare safeguarded outer-EM acceleration or initialization improvements under
   a new fixed total-work protocol, with likelihood-decrease rejection and truthful
   budget failures. Research existing maintained methods before implementation.
3. Include a genuinely three-component truth alongside deliberately overfitted
   cases. The present truths have one or two generating components, so all K=3
   fits are overfitted; this is material counterevidence to a universal claim that
   three-component GVMs are inherently difficult. Fresh held-out seeds and qualified
   Gaussian controls are still required before public integration.

This is a recommendation, not an additional implementation authorization.
Production code, existing tests, stopping/defaults, shared pilot, build/workflows
and release version remain unchanged. No merge, release or upstream submission.

## Reproduction and completion audit

The [study runner](../tools/gvm_gradient_study.py),
[independent audit](../tools/gvm_gradient_audit.py),
[diagnostic analysis](../tools/gvm_gradient_diagnostics.py),
[plot generator](../tools/gvm_gradient_plot.py) and
[archive builder](../tools/gvm_gradient_archive.py) accompany the public summaries.
The private archive preserves exact generated inputs, source/configuration hashes,
versions, actual commands/output/errors/timestamps, full fitted models and traces,
plots, a SHA-256 manifest and rerun instructions. It is read-back verified before
handoff; private machine paths and account details are not committed to Git.

Verified complete: research/control qualification, bounded candidate, frozen
confirmation and independent numerical audit. Promotion readiness: failed, not
pending an unreported successful test. Repository checks validate navigation and
tooling rather than replacing the scientific gates.
