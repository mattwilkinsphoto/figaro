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

Planning baseline: `b99c5d56`, reviewed 2026-09-07. The circular foundation is now
implemented and locally validated; see its [guide and evidence](docs/VON_MISES.md).
The [inventory](docs/DISTRIBUTION_SUPPORT.md) distinguishes
native elements, composition possibilities and missing first-class support.

| Order / milestone | Status | Broad capability before flavors | Exit evidence |
| --- | --- | --- | --- |
| D0: distribution contracts | Initial circular contracts implemented; broader adoption remains | Shared parameter/support conventions, density/log-density tests, seeded RNG ownership and explicit inference compatibility | Circular contract tests and capability matrix; extend stable log-density support to existing distributions through a separate audit |
| D1: circular foundation (`DIST-01`) | Integrated on main at `fea8b999`; CI passed | Circular angle handling and von Mises; reuse for later wrapped and spherical families | 16 new regressions, 179 modernization tests and executable examples pass; [Linux CI](https://github.com/mattwilkinsphoto/figaro/actions/runs/34138540587) |
| D2: linear-angular joint models (`DIST-02`) | Locally validated; standalone publication approved, remote CI/integration remain | Fixed-kernel Gauss-von Mises on a real vector plus one angle, built on D1 | 13 new regressions; [scope approval, evidence and boundaries](docs/GAUSS_VON_MISES.md); separate future review for report ingestion/fusion/filtering/propagation |
| D3: common missing scalar/count families | Proposed next tranche | Student t/Cauchy/Laplace; negative binomial/hypergeometric; lognormal/Weibull; bounded triangular/Kumaraswamy | Select a small representative set across `DIST-03` through `DIST-06`; demonstrate observation/inference, not only random generation |
| D4: reusable constructions (`DIST-10`) | Proposed; start enabling pieces during D1-D3 | Correct transformations, truncation, mixtures and hurdle/zero-inflated laws | Jacobian/normalizer/mixed-measure checks; avoid one-off implementations of every derived name |
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

## Maintaining the plan

Use `wishlist -> researched -> planned -> implementing -> validated` status transitions;
record `blocked` with the exact unmet gate. Keep completed items and their evidence.
Promote a family from the wishlist by recording its use case, representative first
distribution, dependencies, acceptance tests and exclusions. Alias/special-case names
should reuse one implementation where mathematically and numerically appropriate.

Related: [wishlist](WISHLIST.md), [distribution inventory](docs/DISTRIBUTION_SUPPORT.md),
[GVM milestone](docs/GAUSS_VON_MISES_PLAN.md), [engineering history](MODERNIZATION.md).
