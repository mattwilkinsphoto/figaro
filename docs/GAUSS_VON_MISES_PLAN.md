# First distribution milestone: von Mises and Gauss-von Mises

Status: **circular foundation, fixed-kernel joint GVM and KL/residual diagnostics integrated
on main with passing branch CI at `3615e26e`; moments/conditionals locally validated,
awaiting their own CI/integration**. Standalone publication is approved by the maintainer. See the
[circular guide](VON_MISES.md) and [joint development preview](GAUSS_VON_MISES.md).
Both the circular and joint families
were explicitly requested on 2026-09-07. Parent families: `DIST-01` and
`DIST-02` in the [wishlist](../WISHLIST.md). See the [delivery roadmap](../ROADMAP.md).

## Which distribution?

The first reusable component is ordinary circular von Mises. The joint target in this
plan is the Horwood-Poore Gauss-von Mises (GVM) family: a Gaussian real vector coupled
to a circular variable. Names are not interchangeable with generalized von Mises,
von Mises-Fisher on a sphere, or a Gaussian mixture. Some literature also uses
Gauss-von Mises for the ordinary circular law; the API must make the distinction explicit.

The controlling reference is Horwood and Poore,
[Gauss von Mises Distribution for Improved Uncertainty Realism in Space Situational Awareness](https://doi.org/10.1137/130917296),
SIAM/ASA Journal on Uncertainty Quantification 2 (2014), pp. 276-304. The supplied
full paper was consulted: Definition 3.1 and sections 3-4 were checked, including visual
inspection of the definition/property pages; sections 5-6 were read for the later
quadrature and propagation scope. This is not a reproduction of its complete experiments.
The [open 2012 precursor](https://amostech.com/TechnicalPapers/2012/Astrodynamics/HORWOOD.pdf)
provides an accessible introduction. Neither paper is copied into the Figaro repository.

Space situational awareness motivates the joint family: orbital uncertainty can combine
linear coordinates with an angle and develop curved, non-Gaussian dependence. The core
API should remain domain-independent, with SSA-style synthetic examples rather than
application-specific orbit catalogs, dynamics or data embedded in the library.

For a real vector `x` of length `n` and angle `theta`, the model is:

```text
P = A A^T                 A is the lower-triangular Cholesky factor
z = solve(A, x - mu)
m(x) = alpha + beta^T z + 0.5 z^T Gamma z
p(x, theta) = Normal_n(x; mu, P) * VonMises(theta; m(x), kappa)
```

`P` is positive definite; `Gamma` is symmetric but need not be positive definite.
`beta` and `Gamma` are expressed in these whitened coordinates. `kappa >= 0`.
Setting both coupling terms to zero gives independent Gaussian and circular components.
This is not the different model obtained by making the Gaussian mean depend on an angle.

## First increment: reusable circular foundation

The following design goals now have an implementation in `VonMisesDistribution`,
`VonMises` and `CircularStatistics`, including all four fixed/element parameter
combinations. The supported concentration range is `0 <= kappa <= 1e8`.
The [user guide](VON_MISES.md) is the authoritative implemented contract;
CDF, quantiles and fitting remain deferred.

- An explicitly named `VonMises` element, with constant parameters first and tested
  element-valued parameter composition next. Use radians and one documented canonical
  sample interval, proposed `[-pi, pi)`, while accepting equivalent finite-angle inputs.
- Periodic density and stable log-density; evaluate the normalizer using scaled Bessel
  functions or independently verified asymptotic/numerical methods. Avoid forming
  `exp(kappa)` at large concentration. Reject invalid parameters and define the supported
  numeric range; do not silently turn overflow into a degenerate distribution.
- A seeded sampler using Figaro's RNG ownership rules. Assess the
  [Best-Fisher rejection method](https://doi.org/10.2307/2346732), including small/large
  concentration numerical limits and cancellation. Its existence is not proof that a
  naive floating-point translation works for every finite concentration.
- Circular mean/resultant-length helpers with defined behavior when direction is
  unidentified. Linear mean, variance and scalar stopping checks must not be silently
  presented as circular equivalents. Sine/cosine projections are useful diagnostics,
  but do not establish complete mixing by themselves.
- Defer CDF, quantiles and fitting until their branch-cut/identifiability contracts are
  specified. SciPy illustrates a circular CDF with a one-unit increase per full turn;
  copying ordinary real-line CDF assumptions would be misleading.
  [SciPy reference](https://docs.scipy.org/doc/scipy/reference/generated/scipy.stats.vonmises.html).

## Proposed second increment: joint GVM

The fixed-kernel portion is now integrated on main as `GaussVonMisesDistribution`,
`LinearAngular` and `GaussVonMises`, with 13 focused regressions. The following goals
remain the design rationale; the preview guide records the implemented API and limits.
The maintainer approved standalone publication on 2026-09-07; CI passed and main was
fast-forwarded to `3615e26e`. Further extensions need their own gates. This is not a tagged library release.

Start with fixed parameters and an immutable linear-vector/angle result. Separate a
pure numeric kernel from the mutable Figaro element adapter; proposed public names and
types remain provisional until implementation review.

An exact conditional sampling construction follows from the factorization: draw a
standard Gaussian vector `z`, set `x = mu + A z`, then draw the angle from von Mises
with center `m(x)`. This produces independent joint draws when the underlying draws
are independent; no multi-chain MCMC is needed merely to draw from the prior. Posterior
inference with additional evidence is a separate problem.

Compute the joint log density as a sum, using triangular solves and a Cholesky
log-determinant rather than an explicit inverse/determinant. Cache immutable
factorizations for fixed parameters, never mutable per-draw state across workers.
Do not call the existing MVN `logp` placeholder; see the [inventory gap](DISTRIBUTION_SUPPORT.md).

Required contracts include dimension agreement, finite parameters, covariance symmetry/
positive definiteness, rejection of malformed matrices, angle normalization and
dimension/order dependence of the whitened coupling parameters. Reordering coordinates
is not just permuting `beta` while ignoring the Cholesky convention. Singular covariance
is excluded initially, with an explicit error rather than silent regularization.

## Acceptance tests and user examples

The next diagnostic increment adds canonical residuals/inversion, squared
Mahalanobis-von-Mises scoring and analytic directed KL with a component breakdown.
See [GVM diagnostics](GVM_DIAGNOSTICS.md) for the derived formula, runnable examples,
Gaussian/circular reductions, numerical limits and focused regression suite.
Finite-concentration threshold calibration is a separate [implemented development increment](GVM_SCORE_CALIBRATION.md),
with direct tails, numerical diagnostics, independent fixtures and modeled-coverage tests.
Analytic marginal circular and physical first/second linear-angular mixed moments,
plus the exact angular conditional given the full linear vector, are now a separate
[CI-verified increment on main](GVM_MOMENTS.md). Its tests include complex-matrix
fixtures, quadrature, sampling and an explicit hierarchical model with angular evidence.
Bhattacharyya divergence and linear-angular mutual information are explicitly lower
priority research items; the [wishlist](../WISHLIST.md) records their validation gates.

| Check / example | Independent evidence required |
| --- | --- |
| Circular wrap-around, e.g. observations near -179 and +179 degrees | Radian conversion, equivalent-angle density, a mean near the shared boundary rather than zero |
| Von Mises at zero, small, moderate and large concentration | Uniform limit, quadrature normalization and versioned independent density/circular-moment fixtures; justified seeded sampling tolerances |
| GVM without coupling | Product-density equality and Gaussian marginal/circular residual checks |
| Coupled GVM with nonzero `beta` and symmetric `Gamma` | Nontrivial independent density fixtures, whitened Gaussian moments and conditional residual sine/cosine moments |
| Evidence on a linear-angular model | Verify actual likelihood weighting and posterior queries against a small independent quadrature/enumeration fixture; no unsupported transformed-node shortcut |
| Several isolated workers | RNG separation, no shared mutable arrays, reproducibility under the documented worker/seed contract and cleanup after errors |
| Standard Gaussian angle vs circular/joint model | Explain the branch-cut failure and dependence difference with the same synthetic setup; no universal speed or accuracy promise |

Additionally test invalid inputs, extreme covariance scales and cancellation/failure
paths. Record each inference algorithm as validated, restricted, unsupported or not
assessed. Continuous factor discretization, general EM, fitting, GVM quadrature filters,
multi-angle tori and full rotation-group distributions are not included by default.

## Research and release gates

The key 2014 full text is now available; no additional PDF is needed for the basic
distribution milestone. Definition 3.1 supplies the parameterization, section 4.3 provides
moment-oracle candidates and section 4.5 motivates canonical-residual tests. Independently
verify formulas before promoting them into public API contracts, especially endpoints
and transformation assumptions.

Specific follow-on checks from the paper:

- `alpha` is the conditional angular center at `x = mu`, not generally the marginal
  circular mean under coupling. At `kappa = 0`, the angular mode/direction is not unique.
- Treat the Gaussian conversion in section 4.6 as an approximation with local angular
  and concentration/curvature conditions, not a globally equivalent Euclidean density.
- The chi-square calibration of the Mahalanobis-von-Mises statistic in section 4.7 is
  a large-concentration approximation. Require calibrated tail/coverage tests before
  using it for gates or stopping decisions at finite concentration.
- Before exposing section 4.4 transformations, independently test invertibility/rank
  conditions and dimension-reducing projections; do not assume arbitrary marginalization
  retains the same GVM parameterization and concentration.
- Section 5's quadrature nodes and weights are deterministic integration machinery,
  not IID Monte Carlo samples. Test its stated exactness class, negative-weight effects
  and concentration limits separately from random sampling.
- Section 6 approximates a transformed distribution. Its refinement's volume-preserving
  assumption and application-specific simplifications must not silently become a generic
  Figaro transformation/filtering guarantee. General transforms need their Jacobians.

Quadrature, transformed expectations and generic uncertainty propagation are later
`DIST-02` research milestones, not part of the initial sampling/density delivery. Their
acceptance should compare against independent Monte Carlo and simpler Gaussian baselines
on controlled curved linear-angular examples. Domain orbit dynamics remain separate.

The 2016 [measurement-update paper](https://doi.org/10.1016/j.procs.2016.07.380)
and 2024 [generalized Bernoulli GVM work](https://doi.org/10.23919/FUSION59988.2024.10706337)
are later research candidates, not dependencies of the basic circular milestone.

A related patent publication, [US 8,909,586](https://patents.justia.com/patent/8909586),
describes GVM-based tracking methods. The maintainer's
[scope decision](GAUSS_VON_MISES.md#maintainer-scope-decision-2026-09-07) lifts the hold
for the current standalone distribution. Revisit claim/scope/status before adding report
ingestion, fusion, filtering or orbit propagation. This is a due-diligence flag, **not**
a conclusion about infringement, validity, enforceability or whether a standalone
distribution is covered. Code licensing and patent review are separate questions;
the existence of an openly readable paper resolves neither automatically.

No third-party implementation has been selected or copied. Prefer a small independently
written kernel with cited mathematics, or an explicitly compatible licensed implementation
after review. The circular implementation uses independently written mathematics and
an opt-in log-density likelihood path; legacy HasDensity-only elements keep their
behavior. Existing published artifacts are unchanged. Joint GVM remains a development
preview; publication approval is a maintainer scope decision, not a legal conclusion
derived from implementation or test success.
