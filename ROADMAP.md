# Figaro roadmap

This is the delivery plan; [WISHLIST.md](WISHLIST.md) is the wider candidate backlog.
Prioritize capabilities that unlock a class of models, then add specialized variants
when there is a concrete need. A wishlist entry is not an implementation or release promise.
Keep this plan Figaro-specific; application models and project details belong elsewhere.

## Current baseline

The Java 17 / Scala 3 / sbt 2 modernization and core performance milestones are integrated
on main. See [acceptance evidence](docs/CORE_PERFORMANCE_ACCEPTANCE.md),
[release notes](docs/RELEASE_NOTES.md) and [migration limitations](docs/MIGRATION.md).
The source remains a development snapshot, not a universally validated stable release.

## Next program: distribution breadth

Reliability interlude: [statistical validation](docs/STATISTICAL_VALIDATION.md) and
[scientific RNG backends](docs/RNG_ASSESSMENT.md) are implemented on the
`modernize/statistical-validation` branch, with local validation; integration/CI remain
separate gates. The next substantive inference priority is better proposals for
concentrated posteriors at matched accuracy. [Versioned stream allocation and Philox](docs/RNG_STREAMS.md)
are now implemented and locally validated on that branch: default seeded replay is
preserved, native split/jump/counter allocation is opt-in. Portable checkpoints,
deterministic default graph traversal and per-sample counter addressing remain future work.
The opt-in [purpose selector](docs/RNG_SELECTION.md) now resolves versioned presets,
overrides and hard requirements before model construction; it does not autotune or
change existing defaults. Local validation and remote CI remain separate gates.

The opt-in [inference-health assessment](docs/INFERENCE_HEALTH.md) adds explicit
insufficient-evidence/warning/danger states, raw-weight ESS and Pareto-tail diagnostics,
query-specific MCSE and existing multi-chain diagnostics. This is a warning layer,
not adaptive inference or automatic stopping. Next: better proposals at matched accuracy,
broader held-out warning calibration and dependence-aware importance diagnostics.

[Pilot-fitted defensive importance research](docs/DEFENSIVE_IMPORTANCE_RESEARCH.md)
now evaluates that proposal priority against prior importance and existing Quantile
slice sampling, including discarded training costs, new datasets and a fresh-seed
coverage batch. The additive [public frozen-proposal API](docs/VECTOR_IMPORTANCE.md)
is now implemented and locally validated on this branch, with supplied proposals,
discarded pilot training, explicit fit refusals and 210-trial boundary/multimodal/tail
acceptance evidence. It does not alter graph samplers or stopping rules. Remote CI
and integration remain separate gates. Next: broader model/rare-event and MCSE
coverage calibration; automatic proposal replacement is not part of this milestone.

The [6,600-trial calibration assessment](docs/IMPORTANCE_CALIBRATION.md) is integrated
on main as modern.11 at `448f591b`, with passing branch and main CI. It rejects batch
intervals and a diagnostic-selection filter as general coverage repairs;
rare-event and nonlinear limits remain explicit.
Automatic stopping remains deferred rather than declaring these limits solved.

The [multi-component proposal fitter](docs/MIXTURE_PROPOSALS.md) is implemented on
`modernize/multi-component-proposals`, with explicit component counts, bounded
regularized EM, frozen production and 1,200 paired acceptance rows. It improves
effective sampling on the tested separated-mode models without claiming automatic
mode discovery or calibrated stopping. CI/main integration remain separate gates.

The [owned graph proposal bridge](docs/GRAPH_PROPOSALS.md) is implemented on
`modernize/graph-proposal-integration` for modern.13 integration after verification.
It supports explicit joint priors/proposals with ordinary graph evidence, owned
cleanup and a graph-MCMC-pilot-to-mixture workflow. The 600-run comparison shows
that root-only improvements do not remove all hierarchical sampling bottlenecks.
Next candidates: matched-total-cost graph workloads and explicit larger joint
blocks, then query-aware rare-event proposals. Automatic graph rewriting,
adaptation during production and precision stopping are not delivered by this work.

Planning baseline: `b99c5d56`, reviewed 2026-09-07. The circular foundation is now
implemented and locally validated; see its [guide and evidence](docs/VON_MISES.md).
The [inventory](docs/DISTRIBUTION_SUPPORT.md) distinguishes
native elements, composition possibilities and missing first-class support.

| Order / milestone | Status | Broad capability before flavors | Exit evidence |
| --- | --- | --- | --- |
| D0: distribution contracts | Initial circular contracts implemented; broader adoption remains | Shared parameter/support conventions, density/log-density tests, seeded RNG ownership and explicit inference compatibility | Circular contract tests and capability matrix; extend stable log-density support to existing distributions through a separate audit |
| D1: circular foundation (`DIST-01`) | Integrated on main at `fea8b999`; CI passed | Circular angle handling and von Mises; reuse for later wrapped and spherical families | 16 new regressions, 179 modernization tests and executable examples pass; [Linux CI](https://github.com/mattwilkinsphoto/figaro/actions/runs/34138540587) |
| D2: linear-angular joint models (`DIST-02`) | Through mutual information, integrated with D3 at CI-verified `e78a6f0e` | Fixed-kernel Gauss-von Mises on a real vector plus one angle, built on D1 | [Scope approval, evidence and boundaries](docs/GAUSS_VON_MISES.md); each extension retains its own CI/integration gate and separate future review for report ingestion/fusion/filtering/propagation |
| D3: common missing scalar/count families | Complete on main; source CI verified at `e78a6f0e` | All nine representatives: Student t/Cauchy/Laplace; negative binomial/hypergeometric; lognormal/Weibull; triangular/Kumaraswamy; appropriate information measures | [Milestone evidence](docs/COMMON_DISTRIBUTIONS_ACCEPTANCE.md): density/tail oracles, observation and MCMC checks, isolated multi-chain determinism, divergence references and independent published consumer |
| D4: reusable constructions (`DIST-10`) | Initial milestone complete on main; source CI verified at `79615111` | Affine/exp transformations, finite truncation, scalar mixtures, full-covariance Gaussian mixture models, hurdle/zero-inflated counts; Gaussian KL/Bhattacharyya and partition MI | [Guide](docs/DISTRIBUTION_CONSTRUCTIONS.md) and [acceptance evidence](docs/DISTRIBUTION_CONSTRUCTIONS_ACCEPTANCE.md): 362 modernization tests, independent oracles/consumer and clean CI reproducibility; no generic mixed-measure law, EM fitter or mixture-divergence shortcut |
| D5: multivariate, tail and matrix breadth | Wishlist; not scheduled | Multivariate t, joint counts, extreme-value, covariance/correlation and directional manifolds | Dedicated dimension/geometry/factorization and inference tests before specialized flavors |
| D6: specialist families | Research backlog | Quantile-defined, singular, physical scattering, phase-type and niche empirical laws | Concrete use case, primary definition, viable numerical method and maintained dependency/license evidence |

D1 comes first even when the ultimate target is D2. Ordinary circular von Mises and
the Horwood-Poore cylindrical Gauss-von Mises are separate deliverables. Neither implies
an orbit propagator, Kalman filter, fitting routine or arbitrary manifold sampler.
Both circular and joint support were confirmed as desired on 2026-09-07. The supplied
Horwood-Poore 2014 paper is the primary GVM reference. A later `DIST-02` research track
can evaluate GVM quadrature and generic uncertainty propagation, with separate approximation,
coverage and release gates; domain-specific orbit dynamics remain outside the core.
This sequence is a proposed priority order, not a calendar estimate.

## D2 diagnostic follow-on priorities

1. Canonical residual/inverse, squared Mahalanobis-von-Mises score and analytic directed
   KL with component breakdown: integrated on main at `3615e26e`, with passing branch CI;
   see [contracts and validation](docs/GVM_DIAGNOSTICS.md). Exact Gaussian/Mahalanobis
   and circular reductions are tested. No replacement compiled release is declared.
2. Analytic circular/mixed moments and exact angle-given-linear conditional: integrated
   on main at `df5a7bf4`, with [passing CI](https://github.com/mattwilkinsphoto/figaro/actions/runs/34173645295);
   see [moments contracts](docs/GVM_MOMENTS.md).
3. [Finite-concentration score calibration](docs/GVM_SCORE_CALIBRATION.md): integrated
   on main at `755eb425`, with [passing CI](https://github.com/mattwilkinsphoto/figaro/actions/runs/34176109863).
   Direct upper tails, quantiles,
   error estimates and modeled-coverage tests replace an assumed chi-square threshold.
4. [Analytic state gradients](docs/GVM_GRADIENTS.md): integrated on main at `141dcc15`,
   with [passing CI](https://github.com/mattwilkinsphoto/figaro/actions/runs/34177381982).
   Log-density and squared-score derivatives, not parameter fitting or a new sampler.
5. [Third-order deterministic expectation quadrature](docs/GVM_QUADRATURE.md): integrated
   on main at `b06e957f`, with [passing CI](https://github.com/mattwilkinsphoto/figaro/actions/runs/34178437678).
   Uses 2n+3 signed-weight nodes; exactness and failure cases are explicit.
6. [Positive-weight tensor reference](docs/GVM_TENSOR_QUADRATURE.md): integrated on main
   at `9cbcce00`, with [passing CI](https://github.com/mattwilkinsphoto/figaro/actions/runs/34179606111),
   11 focused tests and accuracy/callback-cost comparisons. Adjustable orders,
   streamed points and a hard node guard; exponential cost and no certified error bound.
7. [Budgeted order comparison](docs/GVM_QUADRATURE_COMPARISON.md): integrated on main
   at `4f90f815`, with [passing CI](https://github.com/mattwilkinsphoto/figaro/actions/runs/34181640610). Four-corner comparisons
   isolate Gaussian/angular sensitivity with total callback preflight and per-output
   tolerances. Agreement is not an accuracy certificate: diagonal cancellation and
   analytic false-agreement controls are tested. Acceptance: 13 focused tests, all 281
   modernization regressions across 22 suites, seven GVM examples, Scala API generation
   and thin-library packaging pass. Parameter gradients and Hessians remain separate.
8. [Bhattacharyya numerical-method assessment](docs/GVM_BHATTACHARYYA_RESEARCH.md):
   research prototype with 12 tests and [passing CI at `b5da340c`](https://github.com/mattwilkinsphoto/figaro/actions/runs/34185820661);
   the research tool itself is not a public Scala API. Exact angular elimination
   and an analytic Fourier/Gaussian series avoid tensor-grid growth. Truncation bounds
   are derived, but cancellation and high-concentration efficiency require production
   safeguards; see the bounded implementation below.
9. [Guarded Scala Bhattacharyya comparison](docs/GVM_BHATTACHARYYA.md): integrated on main
   at `251f9540`, with [passing CI](https://github.com/mattwilkinsphoto/figaro/actions/runs/34188404684). Nonidentity comparisons
   support concentrations through 50 and at most 32 linear dimensions, subject to
   conditioning/phase checks. Explicit status and optional distance keep unresolved
   results visible; truncation bounds and heuristic rounding estimates remain distinct.
   Acceptance: 16 focused Scala tests, all 297 modernization regressions across 23 suites,
   eight GVM examples, API generation and thin-library packaging. The six-dimensional
   matched-error example compares five harmonics with 31,250 tensor callbacks at a
   1e-6-nat error target. The follow-on [matched-accuracy timing study](docs/GVM_BHATTACHARYYA_PERFORMANCE.md)
   is integrated on main through `e30c8b03`, with [passing study CI](https://github.com/mattwilkinsphoto/figaro/actions/runs/34189533727).
   Four fixtures, 18 method combinations and three fresh JVMs:
   about 326x faster than fresh tensor construction/evaluation in the six-dimensional case,
   but a reused problem-specific scalar reduction is about 5.3x faster than the guarded call.
   These are bounded workload results, not universal inference speedups. Eight report
   validation tests and 13 high-precision research tests pass.
   The follow-on [concentration/cancellation grid](docs/GVM_BHATTACHARYYA_RELIABILITY.md)
   is on main at `e30c8b03`, with [passing CI](https://github.com/mattwilkinsphoto/figaro/actions/runs/34190326556):
   96 pairs in both directions at three tolerances, 460 resolved
   results all meeting oracle accuracy and 116 explicit numerical refusals; four
   high-precision oracle checks and three Scala regression tests. No numerical limits or
   production code changed. The [positive scalar integration assessment](docs/GVM_BHATTACHARYYA_POSITIVE_RESEARCH.md)
   is locally validated with seven tests: all 84 scalar fixtures meet a 1e-8 target,
   including the 12 previously unresolved pairs, plus ten unequal-concentration controls.
   It uses high-precision research preprocessing and heuristic quadrature errors;
   this is not end-to-end Scala validation or a certified fallback. Research
   [CI passed at `70b083f4`](https://github.com/mattwilkinsphoto/figaro/actions/runs/34194233164).
   The follow-on [scalar Scala prototype](docs/GVM_POSITIVE_SCALAR_PROTOTYPE.md)
   validated end to end: 168 main-grid comparisons and ten additional
   unequal-concentration pairs in both directions, preprocessing/units controls,
   budgets, interruption and concurrency; its local 307-test gate passed.
   Both research and prototype are integrated on main through `a54d665e` after
   [passing CI](https://github.com/mattwilkinsphoto/figaro/actions/runs/34195275750).
   The implementation and tests now move into an [explicit public scalar API](docs/GVM_SCALAR_BHATTACHARYYA.md)
   with identity/Gaussian/uniform/constant-angle shortcuts, near-limit controls,
   a user guide and executable examples. All 309 modernization tests pass locally.
   The Fourier API remains unchanged. The public API is integrated on main at `21269b97`
   after [passing CI](https://github.com/mattwilkinsphoto/figaro/actions/runs/34196804703).
   The [matched-accuracy scalar study](docs/GVM_SCALAR_PERFORMANCE.md)
   now compares eight physical-kernel pairs at 1e-8 nats in three fresh JVMs: 14 accepted
   method/case combinations and two explicitly labeled Fourier refusals, with all 336
   timing rounds retained. Fourier is about 39–69x faster on ordinary coupled fixtures
   and 2,626x faster on the high-curvature fixture; positive integration recovers the
   two tiny-overlap refusals at about 0.25–0.43 ms. Analytic shortcuts are treated separately.
   Thirteen evidence/oracle checks and an untimed Scala gate validate the study;
   it is integrated on main through CI-verified `42d4ec1f`.
   The follow-on [audited-totals optimization](docs/GVM_SCALAR_AUDITED_TOTALS.md) targets
   the full-panel scans identified by JFR: compensated incremental totals with periodic
   rebuilds and mandatory final audits. The curved case improves about 7.75x, and two
   smaller cases about 3x in three fresh JVMs. All diagnostics and work counts match
   the frozen control on 299 differential comparisons. Integrated on main at `213aa587`
   after [passing CI](https://github.com/mattwilkinsphoto/figaro/actions/runs/34204059402).
   The [tail-radius assessment](docs/GVM_SCALAR_TAIL_ASSESSMENT.md) now tests a stronger
   affinity lower bound across 84 scalar cases: 49 smaller radii, with initial panels
   reduced from 4,650 to 1,494 on the curved fixture. This is research work, not measured
   throughput. The [test-only JVM candidate](docs/GVM_SCALAR_TAIL_JVM.md) now retains
   conservative bound arithmetic, bounded setup/cancellation and unchanged final tail
   checks. Three-JVM full-call timings show about 2.54x on the costly curved fixture,
   little change on four others; its original 325-test gate passed locally.
   [Production integration](docs/GVM_SCALAR_TAIL_PRODUCTION.md) now passes 319 distinct
   modernization tests after retiring duplicate prototype tests and adding held-out
   radius/screen controls (44 physical fixtures, both directions). Setup budgets are
   explicit; repeated full-call timings retain about 2.53x on the curved fixture.
   The rebuilt library passes artifact and separate published-consumer checks.
   Integrated on main at `f4884cfb` after
   [passing CI](https://github.com/mattwilkinsphoto/figaro/actions/runs/34209082583),
   completing this targeted scalar performance milestone. Current main is ready for
   consumer rebuilds; no additional scalar micro-optimization is scheduled here.
   Stronger rounding analysis and
   multidimensional alternatives remain separate work; no automatic method switch.
10. [Linear-angular mutual information](docs/GVM_MUTUAL_INFORMATION.md): implemented
   as an opt-in guarded diagnostic and integrated on main with the D3 milestone.
   Analytic conditional entropy and Fourier marginal entropy reduce integration to one
   angular dimension; independent high-precision fixtures and explicit work/accuracy
   refusals accompany the API. Its own branch CI and the complete D3 integration CI pass;
   see [acceptance evidence](docs/COMMON_DISTRIBUTIONS_ACCEPTANCE.md).
   The [cross-family metrics roadmap](docs/INFORMATION_METRICS_ROADMAP.md) carries KL,
   Bhattacharyya and MI forward to other broad distribution families, with distinct
   applicability and numerical contracts rather than an assumed universal API.

No report ingestion, fusion, filtering, or propagation is added by these diagnostics.

## Definition of done for a distribution

1. Specify support, units, parameterization, invalid-input behavior, degenerate limits
   and any finite-precision restrictions. Do not silently exchange rate/scale, variance/
   standard deviation, successes/failures or degrees/radians.
2. Provide sampling and mathematically consistent density/log-density or mass/log-mass.
   Add CDF/quantiles/moments only with a defined contract and verification; explicitly
   label unavailable operations. Do not fabricate finite moments for heavy-tailed laws.
3. Test normalization, special cases and numerical boundaries with independent oracles.
   Statistical tests use fixed seeds and justified tolerances, not fragile exact sequences
   borrowed from a different RNG. Include failure and cancellation paths where relevant.
4. Test actual Figaro evidence and inference paths. State support separately for forward
   sampling, importance, MH, parallel runners, factored algorithms, learning and vector
   log-density use. Compilation alone is not algorithm compatibility.
5. Preserve isolated RNG/model ownership. Measure realistic throughput and numerical
   quality; adding a distribution is not automatically a performance improvement.
6. Publish beginner-facing examples, all public API contracts, limitations, sources and
   license/provenance notes. Update the inventory, wishlist status, generated reference
   and CI. A release item needs passing gates and a commit link, not just a checked box.
7. Assess information-metric support using the [cross-family checklist](docs/INFORMATION_METRICS_ROADMAP.md).
   Record analytic, numerical, unavailable or inapplicable operations; a new family does
   not need every metric on day one, but gaps must be explicit.

## Maintaining the plan

Use `wishlist -> researched -> planned -> implementing -> validated` status transitions;
record `blocked` with the exact unmet gate. Keep completed items and their evidence.
Promote a family from the wishlist by recording its use case, representative first
distribution, dependencies, acceptance tests and exclusions. Alias/special-case names
should reuse one implementation where mathematically and numerically appropriate.

Related: [wishlist](WISHLIST.md), [distribution inventory](docs/DISTRIBUTION_SUPPORT.md),
[GVM milestone](docs/GAUSS_VON_MISES_PLAN.md), [engineering history](MODERNIZATION.md).
