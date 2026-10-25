# Mixture weights and restart-budget allocation

Research provenance: [GVM-08](RESEARCH_REFERENCES.md#gvm-08).

## Recommendation and scope

Test a **conditional mixture-weight solve**, followed by **resumable allocation of
work across the existing three starts**. Evaluate them separately before combining
them. The three-stage study is now approved, with independently checked test-only
implementation and a separate [execution protocol](GVM_WEIGHT_PROTOCOL.md).
Confirmation and promotion decisions are separate from implementation correctness.
The released stopping estimator,
defaults, scalar fitter, runtime dependencies and version remain unchanged. The
shared pilot remains parked. Independent numerical and comparative validation,
followed by a separate promotion review, are required before public integration.

The [whole-fit study](GVM_WHOLEFIT_STATUS.md) identifies two different problems:

- In 39 of the 44 accelerated K=3 fits still exceeding the full-gradient threshold,
  the largest residual belongs to the weights. A weight solve may address that
  residual, but a large gradient alone does not establish a substantial likelihood
  improvement, identifiable component weights or a well-conditioned full fit.
- Accelerated fits retained 100/160 stationary multicomponent solutions, while only
  71/160 completed the declared workflow. In 29 cases, a later restart exhausted
  the shared budget after the selected start had qualified. Allocation may improve
  use of that budget; relabeling these cases would not improve the algorithm.

These are existing results, not new measurements. Preserve their original statuses,
thresholds and no-promotion decision. Separated true K=3 models already qualified
in 15/16 accelerated fits; overlapping and overfitted cases are the harder test.
Another optimization step could cost more than it saves, and interleaving starts
could delay the strongest start. Those are the principal counterarguments to test.

## Literature and existing software

Sources were inspected on 2026-10-24. The table distinguishes published methods,
available implementations and the proposed Figaro-specific adaptation.

| Source and inspected scope | Relevance and decision |
| --- | --- |
| Kim, Carbonetto, Stephens and Anitescu, *A Fast Algorithm for Maximum Likelihood Estimation of Mixture Proportions Using Sequential Quadratic Programming*, [paper, v5](https://arxiv.org/pdf/1806.01412), abstract/introduction and sections 3.1–3.3; [journal DOI](https://doi.org/10.1080/10618600.2019.1689985) | With component densities fixed, weight estimation is convex. Their mix-SQP method addresses slow EM progress using sequential quadratic programming and active sets. Its large-matrix/low-rank acceleration is not evidence of a comparable speedup for K=2,3. Use the conditional formulation; do not port the large-scale solver. |
| Official [mixsqp package](https://stephenslab.github.io/mixsqp/) and [function contract](https://stephenslab.github.io/mixsqp/reference/mixsqp.html) | A genuine existing implementation, including log-density input. The package declares MIT licensing; repository metadata inspected reports 0.3-54, not a verified latest CRAN release. R and compiled dependencies add research setup. Its nonconverged output need not sum to one: backend output is not a certificate. Keep it as an optional comparator, not a required runtime dependency. |
| Official [SciPy SLSQP interface](https://docs.scipy.org/doc/scipy/reference/optimize.minimize-slsqp.html) and [licensing](https://scipy.org/about/) | Preferred initial research backend: bounds, equality constraints and an analytic gradient cover this small problem. SciPy is maintained and BSD-licensed. `success`/`ftol` are not our independent qualification; returned multipliers omit bound multipliers. Implement a small adapter and independent checks, not a new SQP solver. |
| Jaggi, *Revisiting Frank-Wolfe: Projection-Free Sparse Convex Optimization* (2013), [section 2](https://proceedings.mlr.press/v28/jaggi13.pdf) | Supplies the convex linearization-gap certificate. Use the independently derived floored-simplex specialization below to qualify any weight solver; adopting Frank-Wolfe as the optimization backend is not required. |
| Jamieson and Talwalkar, *Non-stochastic Best Arm Identification and Hyperparameter Optimization* (2016), [introduction, section 2 and algorithm](https://proceedings.mlr.press/v51/jamieson16.pdf) | Restart trajectories resemble non-stochastic optimization sequences, not independent bandit rewards. Successive Halving allocates work and eliminates candidates. Without convergence-rate information, an apparently poor start cannot be certified inferior. Start with interleaving, not irreversible elimination or an asserted best-start guarantee. |
| Li et al., *Hyperband* (2018), [introduction and section 3.1](https://jmlr.org/papers/volume18/16-558/16-558.pdf) | Brackets address the tradeoff between trying more configurations and spending more on each. Three fixed initializations do not justify a general hyperparameter-search framework or transfer its published speedups. Defer bracket search and aggressive pruning. |
| You, Li and Du, *A new iterative initialization of EM algorithm for Gaussian mixture models* (2023), [abstract, method and overlap discussion](https://journals.plos.org/plosone/article?id=10.1371/journal.pone.0284114) | MRIPEM is a relevant initialization alternative, but its reported benefits weaken with overlap/dimension. Its use of adjusted Rand index in initialization selection also needs scrutiny before any unlabeled adaptation. Keep the existing initializations fixed to isolate weights and allocation; do not borrow labeled clustering selection. |
| Official [scikit-learn GaussianMixture API](https://scikit-learn.org/stable/modules/generated/sklearn.mixture.GaussianMixture.html) | `n_init` selects among repeated fits, while `warm_start` ignores `n_init` and resumes one fit. This is useful baseline behavior, not an off-the-shelf scheduler for the GVM model, accepted-map checkpoints and logical-work ledger. A small research scheduler adapter addresses that concrete gap. |

Liu and Rubin's [ECME abstract](https://doi.org/10.1093/biomet/81.4.633) provides
historical context for replacing selected EM conditional steps with actual-
likelihood optimization. Only the publisher abstract was inspected; no full-text
convergence theorem is relied on. The core convex derivation and allocation
sources above are accessible, so a paywalled PDF is not needed to start this plan.
The search included recent weight-optimization and initialization work, but does
not establish an exhaustive survey or a newer universally superior method.

No external implementation or dataset was copied, installed or benchmarked for
this assessment. The mixsqp raw license endpoint was inaccessible; licensing is
reported from its official site and metadata, not assumed from repository visibility.
During assessment, SciPy imports failed in the active shell and bundled Python.
For the approved study, NumPy 2.5.1, SciPy 1.18.1 and mpmath 1.3.0 were installed
into an isolated research directory; mathematical and development checks now pass.
Rscript was not found on PATH; that is not proof that R is absent from the machine.

## Conditional weight contract

Hold every component's shape parameters fixed and let `L[i,j]` denote its density
at training observation i. For K components and n observations, minimize

```text
f(w) = -(1/n) sum_i log(sum_j L[i,j] w[j])
D = { w : sum_j w[j] = 1, w[j] >= epsilon }, epsilon = 1e-8
g[j] = -(1/n) sum_i L[i,j] / (Lw)[i]
H[j,k] = (1/n) sum_i L[i,j] L[i,k] / (Lw)[i]^2
```

The positive-semidefinite Hessian establishes convexity where every row has
positive mixture density. This is a conditional optimum, not a global optimum of
the joint weight/shape fit. Duplicate components may have nonunique optimal
weights. A Dirichlet penalty, a larger floor, component deletion or a minimum-mass
constraint would change the problem; none is proposed here.

For finite log densities, subtract each row's maximum before exponentiating.
Cache the resulting density matrix only while shapes remain fixed. Row constants
do not change the optimizing weights, but must be restored for the full likelihood.
Invalidate the cache on any shape change. Test extreme log differences explicitly;
do not introduce an arbitrary density floor or silently ignore nonfinite rows.

For a feasible w, minimize the linearization over D to obtain

```text
G(w) = dot(g,w) - [epsilon * sum_j g[j] + (1-K*epsilon) * min_j g[j]]
0 <= f(w) - min_D f <= G(w)
```

This is our specialization of the convex gap in Jaggi, not a claim that his paper
implements a GVM optimizer. The inequality follows by convexity and the minimum
of a linear function on the floored simplex. Floating-point evaluation needs its
own error allowance. Compare independently recomputed objective gaps and feasible
predictions, not equality of weight vectors in rank-deficient cases.

Proposed weight qualification is a conservative recomputed gap at most `1e-8`
nats/observation, with simplex sum/floor violations at most `1e-12`. A materially
negative gap or a nonfinite evaluation is a numerical failure, not something to
clamp to zero. Independently bound/check evaluation error before certifying a
near-threshold result. The complete GVM gradient threshold remains `1e-4` in the
same physical coordinates; a conditional weight certificate never replaces it.
If floating-point output needs a tiny feasibility correction, explicitly project
it onto the floored simplex and reevaluate objective, gradient, gap and support
there. Report that correction; an infeasible raw solver output cannot inherit the
convex certificate merely because its violation was within tolerance.

Refreshed responsibilities must still pass the existing component mass and ESS
requirements, each at least `max(30,5d)`. A conditional optimum can violate those
requirements, especially in an overfitted model. Record `LowSupport` and retain
the previous valid map instead of deleting the component, relaxing support or
calling the full fit successful. Record all rejected and interrupted work.

## Bounded three-stage plan

### Stage 1: Numerical qualification and relevance

Implement test-only weight references after verifying isolated dependency versions.
Use at most 40 fixed-shape cases drawn from the previous study as **development
diagnostics**, plus deterministic mathematical fixtures. Old confirmation seeds
are no longer held out for this follow-up. For each case, record the original
conditional gap, actual likelihood improvement, weight and full-gradient changes,
support validity and cost. Distinguish a numerical residual with negligible
likelihood consequence from a useful optimization opportunity.

Use independently written 60-digit objective/gradient evaluation. For K=2, compare
against a bracketed derivative root with both endpoints checked. For K=3, check
vertices, all edges and interior candidates with independently evaluated convex
optimality gaps; a second SLSQP invocation is not an independent reference. Include
identical/nearly identical columns, boundary optima, extremely unequal log densities,
column permutations, row shifts, zero/invalid rows and cases violating support.
Target score agreement `1e-8` nats/point and gradient agreement `2e-6` on the
declared finite envelope. Unresolved high-precision or gap disagreements stop this
candidate; do not increase tolerances after seeing confirmation results.

Proceed with the weight arm only if at least one diagnostic has an independently
verified feasible improvement greater than `1e-8` nats/point and a reduced weight
residual, with no mathematical-control failure. This is a relevance screen, not
a useful-speedup claim. If the screen fails, report why and revise the proposed
factorial before freezing it; do not manufacture relevance by loosening checks.

### Stage 2: Test-only integration and allocation controls

Keep the existing safeguarded SQUAREM fitter as the primary baseline. In the weight
arm, augment each ordinary generalized-EM map with a conditional polish at its new
fixed shapes. The composite map must pass likelihood/support checks and have a
finite full-gradient evaluation before SQUAREM uses it; each intermediate map need
not be stationary. Preserve the extrapolation cap and rejection guards.
Recompute responsibilities after changing weights; stale responsibilities cannot
qualify the next map. Cache reuse and checkpoint restoration must be explicit.

Proposed local polish limits are 100 SLSQP iterations, 200 objective/gradient callback
evaluations and 1M logical work units, whichever comes first, always subordinate
to the existing global fit budget. Charge density construction, every matrix pass,
validation and rejected trial. Cache hits avoid recomputing densities, not charging
weight matrix products. Define and freeze the ledger before confirmation; these
units are not FLOPs. Backend success without an independent certificate remains
unqualified. A local refusal falls back to the valid ordinary map and remains in
the record; global budget exhaustion terminates the fit with its original meaning.

For allocation, retain three initializations and choose the unfinished start with
the least charged work, breaking ties by its fixed index. Initialize all three
before continuing optimization, with initialization charged to the same total.
Advance one complete safeguarded cycle, then choose again. Save state only at
fully evaluated accepted-map checkpoints. Persist each start's RNG/state, map
count, best model, gradient and work ledger. A cycle is not guaranteed to cost the
same as another: this is approximate work balancing, not equal CPU allocation.
Never overrun the hard global work limit to finish a cycle. If interrupted, keep
the last valid checkpoint and charge the discarded partial work.

No start is discarded solely because its early likelihood is poor. Terminal
stationary, failed and map-limited starts retain their outcomes. Test pause/resume
equivalence, late-improving synthetic trajectories, expensive-cycle imbalance,
ties, initialization failure, all starts exhausted, work/time interruption and
negative controls that would incorrectly drop a better solution or omit work.
Single-threaded execution and deterministic RNG isolation avoid introducing a
parallelism confound. If least-work scheduling only spreads an insufficient budget
more evenly, that is a negative result, not a reason to stop the search earlier.

Report two separate fields: retained-model qualification and restart-search
disposition. Retain the original GVM-07 combined completion metric as the primary
comparison. A budget-exhausted search with a stationary retained model is still
incomplete by that metric. Select the retained model by greatest training
likelihood as before; any best-qualified alternative is a separately labeled
secondary result, not a replacement winner selected to pass the gate.

### Stage 3: Fresh confirmation and independent decision

After development, freeze sources, versions, all controls, ledger rules, seeds and
acceptance criteria before accessing fresh confirmation data. Publish the frozen
protocol only with authorization. Use a new seed family verified disjoint from
earlier development/confirmation runs and preserve exact generated inputs.

Use a 2-by-2 comparison: original versus polished weights, each with sequential
versus work-balanced starts. The designated candidate is polish plus balancing;
the other two arms explain the contribution of each change, not a post-hoc choice
of whichever wins. Retain d=2,3 plus one angle, all five existing truth families,
K=1,2,3 and eight independent training seeds. K=1 bypasses weight optimization but
still tests allocation. Keep 2,000 training, 2,000 validation and 20,000 test points
per dataset, with training-only whitening and unchanged model/support bounds.

This gives 960 GVM fits and 80 descriptive wrapped-GMM controls: **1,040 confirmation
fits**, with at most 160 additional development fits and three hours of fitting
execution in total. Mathematical controls and independent audits are additional,
separately reported costs. The larger total than GVM-07 buys factorial attribution,
not extra work per fit. Every primary fit still has 20M shared work units, 200 maps
per start, three starts and 120 seconds. A study cap leaves a partial study; it
does not permit dropping hard cells. Keep Gaussian controls labeled as having
different convergence/work accounting, not a cost-matched superiority comparator.

Promotion review requires all of the following:

- Independent density, derivative, conditional-gap, feasibility, support, ledger,
  pause/resume, input-hash and complete-inventory checks pass. Audit every retained
  score/trace and record all failures; include corrupted-input/result negative controls.
- At least 95% complete stationary fits in **every** dimension/family/K candidate
  cell, and at least 20 percentage points better multicomponent completion than
  the unchanged accelerated sequential baseline on the new paired data. With eight
  seeds, the per-cell gate requires eight successes; it is not a population
  reliability confidence bound. Preserve the original full-gradient qualification.
- All 30 primary candidate-versus-baseline test-score comparisons pass the same
  one-sided Bonferroni-adjusted paired noninferiority procedure at familywise
  alpha=0.05 and margin 0.02 nats/point. Include unqualified finite-budget models;
  never discard them to improve scores. No finite model means an explicit failed
  gate, not an omitted pair. Ablations are descriptive unless additional inference
  and multiplicity adjustments are frozen in advance.
- Report common-budget quality, retained qualification, complete-search rates,
  support refusals and each work category. Time-to-qualification uses all runs
  with unfinished runs identified, not successful-only averages. Timing is advisory
  unless separately controlled. Validation data may choose K; test data may not.
- Save a read-back-verified archive with input names/SHA-256, exact scripts and
  versions, parameters, actual logs/errors/timestamps, machine-readable outcomes,
  plots, completion status and rerun instructions. Independent validation means a
  separately derived numerical path, not a second pass through candidate code.

Passing these gates permits a promotion review, not an automatic release. Failure
means no promotion. Do not relax the full-fit criterion, enlarge the per-fit budget,
change the stopping estimator or change defaults in response to held-out results.

## Deferred questions

Hard restart elimination, Hyperband brackets, new initialization families, joint
Newton optimization, component merging/deletion, penalties, automatic K selection,
multiple angles and arbitrary dimensions are separate designs. If certified weight
improvements are negligible, or higher likelihood consistently violates support,
the next question is identifiability/model complexity rather than another optimizer.
If weight polishing works but allocation does not, retain that distinction and
design a fresh confirmation before selecting a different candidate for promotion.
