# Figaro capability wishlist

This is a family-first backlog, not a promise to implement every named distribution.
The [roadmap](ROADMAP.md) selects delivery milestones; the
[support inventory](docs/DISTRIBUTION_SUPPORT.md) records what already exists.
Initial scope: broader distribution support, starting with **both circular von Mises
and joint linear-angular Gauss-von Mises**, as requested on 2026-09-07.

## How to read and extend this list

Status meanings: **native** = a current named Figaro element exists; **composable** =
a mathematical construction is available but may lack a tested density/evidence API;
**researched** = definition/method investigated, no shipped implementation implied;
**wishlist** = candidate requiring assessment. Native does not mean every algorithm
or numerical edge case is validated. Family rows below describe expansion work, not
blanket support for all family members. Circular von Mises is now implemented and
validated on main; joint GVM is a locally tested preview approved for standalone publication,
and other directional families remain future work.

Use stable `DIST-xx` IDs when moving work into the roadmap. Priorities are P0 (first
program), P1 (common missing breadth), P2 (subsequent breadth) and P3 (specialist research).
Record a source, concrete use case, representative first member, dependencies, numerical
and inference tests, and acceptance evidence when promoting a candidate. Keep aliases
and special cases attached to their family instead of creating duplicate implementations.

## Broad families first

| ID / priority | Family and current expansion status | First useful capability | Later flavors / shared work |
| --- | --- | --- | --- |
| DIST-01 / P0 | Circular von Mises native and locally validated; other directional laws researched | [Circular von Mises, angle conventions and circular summaries](docs/VON_MISES.md) | Wrapped normal/Cauchy and other wraps; then von Mises-Fisher, Kent and Bingham with sphere/axis-aware contracts |
| DIST-02 / P0 | Joint linear-angular; locally validated, standalone publication approved, CI/integration remain | [Fixed-kernel Horwood-Poore Gauss-von Mises](docs/GAUSS_VON_MISES.md) using DIST-01 | Mardia-Sutton, GVM mixtures, multiple angles and newer generalized GVM variants; separate quadrature/uncertainty-propagation research and application-level review, not an automatic filtering claim |
| DIST-03 / P1 | Real-line location/scale and heavy tails; wishlist, Normal native | Student t, Cauchy and Laplace | Logistic, skew/noncentral variants, generalized normal, stable and hyperbolic families |
| DIST-04 / P1 | Finite choices and count laws; partially native | Negative binomial and hypergeometric with explicit count conventions | Overdispersion, beta mixtures, heterogeneous Bernoulli sums, noncentral and zero-truncated flavors |
| DIST-05 / P1 | Positive-valued scale/lifetime laws; partially native | Lognormal and Weibull; expose reusable Gamma special cases | Inverse Gaussian, Rayleigh/Rice/Nakagami, generalized Gamma, fatigue-life and survival variants |
| DIST-06 / P1 | Bounded scalar laws; Beta/Uniform native | Triangular and Kumaraswamy; correct bounded transforms | PERT, trapezoidal, raised cosine, logit-normal and continuous Bernoulli/binomial |
| DIST-07 / P2 | Extreme-value and power-law families; wishlist | Generalized extreme value and generalized Pareto with parameter-dependent support | Gumbel, Frechet, Lomax, Zipf-Mandelbrot, tail/truncation variants |
| DIST-08 / P2 | Joint vector/simplex/count laws; MVN/Dirichlet native | Multivariate t and multinomial count vectors | Negative multinomial, Dirichlet count mixtures, joint survival and continuous-categorical |
| DIST-09 / P2 | Matrix/correlation/manifold laws; wishlist | Wishart/inverse-Wishart and LKJ, following matrix validation contracts | Matrix normal/t/beta, matrix Langevin, Stiefel-uniform; positive-definite versus orthonormal support differs |
| DIST-10 / P1 | Transformations, mixtures and mixed measures; composition partly native | Tested transform/Jacobian, truncation, mixture and hurdle/zero-inflation contracts | Folded/wrapped/rectified, compound Poisson-Gamma, spike-and-slab, convolutions and censoring |
| DIST-11 / P3 | Flexible systems and quantile-defined laws; wishlist | Valid parameter/monotonicity contracts before fitting APIs | Pearson, Johnson, metalog, quantile-parameterized, Tukey lambda and Wakeby |
| DIST-12 / P3 | Singular/degenerate and nonnumeric measures; Constant/Select native | Explicit distinction between atoms, densities and singular laws | Cantor, circular point masses; no fabricated Lebesgue PDF for a singular law |
| DIST-13 / P3 | Physical/scattering and random-matrix spectral laws; wishlist | Verify domain, reference measure and normalization first | Henyey-Greenstein, Mie, Marchenko-Pastur, Wigner, Tracy-Widom and other specialist laws |
| DIST-14 / P3 | Process-derived and niche discrete families; wishlist | A reusable phase-type/compound-count representation when justified | Absorption-time, branching, occupancy/partition and specialized empirical laws |

The first implementation plan is [von Mises and GVM](docs/GAUSS_VON_MISES_PLAN.md).
Underlying libraries already offer some scalar laws; see the inventory before writing
new kernels or adding dependencies. First-class support includes likelihood/inference
semantics, not merely calling another library's random-number generator.

## Named-distribution intake from the requested list

Source: Wikipedia contributors, [List of probability distributions](https://en.wikipedia.org/wiki/List_of_probability_distributions),
[reviewed revision 1373058746](https://en.wikipedia.org/w/index.php?title=List_of_probability_distributions&oldid=1373058746),
reviewed 2026-09-07. The following is a factual name/alias intake, including nested
variants and related families, **not a copy of the article's explanatory prose**.
Use primary literature or official implementation documentation for specifications.

This inventory retains even obscure candidates; they are not all equally useful or
equally well-defined. Names already listed as native in the support inventory remain
native, not new work. All other names are wishlist candidates unless the family row
explicitly records research; possible composition is not a validated implementation.
Duplicates/aliases are grouped. Several source placements need review: Kent/Bingham
are not scalar bounded-interval laws; scattering phase functions need an explicit
spherical measure; modified half-normal and skew-elliptical names do not establish
discrete support. Dirac delta/comb notation is not an ordinary continuous density API.

### Finite discrete names — DIST-04, DIST-07, DIST-12, DIST-14

- Bernoulli; Rademacher; binomial; beta-binomial; degenerate/point mass; discrete uniform.
- Hypergeometric; negative hypergeometric; Poisson binomial; Fisher's noncentral
  hypergeometric; Wallenius' noncentral hypergeometric.
- Benford; ideal soliton; robust soliton; finite Zipf; Zipf-Mandelbrot.

### Count, process and other names in the infinite-support intake — DIST-04, DIST-14

- Beta negative binomial; negative binomial/Pascal; extended negative binomial;
  geometric; generalized log-series; logarithmic series.
- Poisson; mixed Poisson; discrete compound Poisson; displaced Poisson; hyper-Poisson;
  general Poisson binomial; Poisson type distributions; Conway-Maxwell-Poisson;
  zero-truncated Poisson; Hermite; Skellam.
- Borel; discrete phase-type; Gauss-Kuzmin; Polya-Eggenberger; Yule-Simon;
  parabolic fractal; zeta/infinite Zipf; Hardy.
- Boltzmann and Maxwell-Boltzmann: resolve discrete energy-state versus continuous
  velocity/speed conventions (`DIST-13`) rather than copy a support label blindly.
- Skew elliptical (`DIST-03`/`DIST-08`) and modified half-normal (`DIST-05`): retain
  names for assessment, but do not inherit their placement in the source's discrete list.

### Bounded scalar names — DIST-06, DIST-10, DIST-11, DIST-12

- Beta; four-parameter Beta; arcsine; PERT; continuous uniform/rectangular;
  Irwin-Hall; Bates; logit-normal; Kumaraswamy.
- Logit metalog; bounded quantile-parameterized; raised cosine; reciprocal;
  triangular; trapezoidal; truncated normal; U-quadratic; continuous Bernoulli;
  continuous binomial/cobin.
- Dirac delta: record under point masses, not an ordinary continuous distribution.
- The source also names Kent, von Mises-Fisher, Bingham, Marchenko-Pastur and Wigner
  semicircle here; they are routed to directional/spectral families below.

### Circular, directional and scattering names — DIST-01, DIST-13

- Von Mises; wrapped normal; wrapped exponential; wrapped Levy; wrapped Cauchy;
  wrapped Laplace; wrapped asymmetric Laplace.
- Von Mises-Fisher; Kent; Bingham.
- Dirac comb/circular point mass (`DIST-12`); Henyey-Greenstein and Mie phase
  functions (`DIST-13`, requiring physical/measure specifications).
- User-requested addition beyond the source list: **Gauss-von Mises** (`DIST-02`).
  Later research additions: Mardia-Sutton, generalized von Mises, generalized Bernoulli
  GVM and multiple-angle joint models. These are distinct names, not aliases of GVM.

### Positive, lifetime and semi-bounded names — DIST-05, DIST-07, DIST-10, DIST-11, DIST-14

- Beta prime; Birnbaum-Saunders/fatigue life; chi; noncentral chi; chi-squared;
  inverse-chi-squared; noncentral chi-squared; scaled inverse chi-squared.
- Dagum; exponential; exponential-logarithmic; F; noncentral F; folded normal;
  Frechet; Gamma; Erlang; inverse-gamma; generalized Gamma; generalized Pareto.
- Gamma/Gompertz; Gompertz; half-normal; modified half-normal; Hartman-Watson;
  Hotelling's T-squared; inverse Gaussian/Wald; Levy.
- Log-Cauchy; log-Laplace; log-logistic; log-metalog; lognormal; Lomax;
  Mittag-Leffler; Nakagami; Pareto; Pearson type III.
- Phase-type; phased bi-exponential; phased bi-Weibull; semi-bounded
  quantile-parameterized; Rayleigh; Rayleigh mixture; Rice; shifted Gompertz;
  type-2 Gumbel; Weibull/Rosin-Rammler.

### Real-line and flexible-shape names — DIST-03, DIST-07, DIST-10, DIST-11, DIST-13

- Behrens-Fisher; Cauchy/Lorentzian; centralized inverse-Fano; Chernoff;
  exponentially modified Gaussian; Gaussian minus exponential; expectile.
- Fisher-Tippett/extreme value/log-Weibull; Fisher's z; skewed generalized t;
  gamma-difference; generalized logistic; generalized normal; geometric stable;
  Gumbel/type-1 Gumbel; Holtsmark.
- Hyperbolic; hyperbolic secant; Johnson SU; Landau; Laplace;
  Levy skew alpha-stable/stable; Linnik; logistic; map-Airy; metalog.
- Normal/Gaussian; normal-exponential-gamma; normal-inverse Gaussian; Pearson type IV;
  quantile-parameterized; skew normal; Student t; noncentral t; skew t;
  Champernowne; Tracy-Widom; Voigt/profile.
- Chen: retain from the source intake, but verify its positive lifetime support
  and parameterization under `DIST-05` rather than assume a real-line law.

### Parameter-dependent support — DIST-07, DIST-11

- Generalized extreme value; generalized Pareto; metalog (unbounded/bounded/
  semi-bounded variants); Tukey lambda; Wakeby.

### Mixed discrete/continuous — DIST-10

- Rectified Gaussian; compound Poisson-Gamma/Tweedie (the atom-plus-positive-density
  regime, not a blanket description of every Tweedie index).
- Family-level additions: zero-inflated, hurdle, censored and spike-and-slab laws;
  keep their different likelihood conventions explicit.

### Joint vector, count and simplex names — DIST-08, DIST-14

- Dirichlet; multinomial; multivariate normal; multivariate t; negative multinomial;
  Dirichlet negative multinomial; generalized multivariate log-gamma;
  Marshall-Olkin exponential; continuous-categorical.
- Ewens sampling formula; Balding-Nichols model: establish the exact random object
  and parameterization before assigning an element/result type.

### Matrix and geometric names — DIST-09, DIST-13

- Wishart; inverse-Wishart; Lewandowski-Kurowicka-Joe/LKJ; matrix normal;
  matrix t; matrix Langevin; matrix-variate Beta; uniform on a Stiefel manifold.
- Spectral laws: Marchenko-Pastur; Wigner semicircle; Tracy-Widom (also in the
  real-line intake). These are not interchangeable with matrix-valued distributions.

### Nonnumeric and miscellaneous families — DIST-10, DIST-11, DIST-12, DIST-14

- Categorical (native `Select`); Cantor; generalized logistic family; metalog family;
  Pearson family; phase-type family; mixture distributions.
- Related classification resources named by the source: relationships among
  distributions and ProbOnto. These are possible catalog aids, not distribution APIs.

## DIST-02 diagnostic extensions (2026-09-07)

- **KL and canonical/Mahalanobis utilities — validated, integrated on main at `3615e26e`:**
  see [diagnostic contracts](docs/GVM_DIAGNOSTICS.md).
- **Circular/mixed moments and exact angular conditionals — integrated on main at CI-verified `df5a7bf4`:**
  [physical-coordinate summaries and hierarchical examples](docs/GVM_MOMENTS.md).
  Reverse conditionals and general posterior moments are not implied by this support.
- **Finite-concentration score calibration — integrated on main at CI-verified `755eb425`:**
  [CDF, direct survival and squared-score quantiles](docs/GVM_SCORE_CALIBRATION.md),
  with independent fixtures and known-kernel coverage tests. Not a fitted-parameter
  confidence procedure or an operational decision policy.
- **Analytic state gradients — integrated on main at CI-verified `141dcc15`:**
  [Log-density and squared-score derivatives](docs/GVM_GRADIENTS.md), in physical
  coordinates and per radian. Parameter gradients, Hessians and gradient-based inference
  algorithms are not implied.
- **Third-order sparse quadrature — locally validated, remote CI/integration pending:**
  [Scalar/vector expectations](docs/GVM_QUADRATURE.md) with 2n+3 signed-weight nodes,
  a specified canonical exactness class and explicit failure examples. No automatic
  error bound or refinement. Higher-order/positive-weight reference methods and
  accuracy-versus-cost comparisons remain follow-on work, not a filtering implementation.
- **Bhattacharyya divergence — research wishlist, lower priority:** symmetric comparison
  of complete GVM distributions. Assess analytic angular integration plus a controlled
  numerical linear integral; validate Gaussian reductions, symmetry and identical laws.
- **Mutual information — research wishlist, lower priority:** begin with dependence
  between the Gaussian vector and angle within one GVM. Investigate analytic conditional
  entropy and Fourier/numerical marginal entropy; validate uncoupled and uniform limits.
  This is not the same operation as comparing two GVMs with KL.

These additions retain the domain-independent scope and do not authorize a report-fusion,
tracking or propagation implementation. They follow the KL/residual milestone.

## Promotion record template

For a new item record: `ID`, `family`, `status`, `priority`, `user problem`,
`current/native/composable alternative`, `precise mathematical definition`,
`first representative`, `later variants`, `dependencies and licenses`,
`inference support targets`, `numerical and statistical tests`, `performance evidence`,
`exclusions`, and `completion commit`. Use the GVM plan as the first worked record.

Support for broad families does not mean implementing a single universal distribution
class. Reuse kernels and construction rules where sound; give different supports and
reference measures distinct contracts. Favor a small reliable core over a long list of
constructors with incomplete likelihoods.
