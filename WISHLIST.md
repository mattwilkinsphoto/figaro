# Figaro capability wishlist

This is a family-first backlog, not a promise to implement every named distribution.
The [roadmap](ROADMAP.md) selects delivery milestones; the
[support inventory](docs/DISTRIBUTION_SUPPORT.md) records what already exists.
Initial scope: broader distribution support, starting with **both circular von Mises
and joint linear-angular Gauss-von Mises**, as requested on 2026-09-07.

## How to read and extend this list

Figaro 6.1 adds exact-coordinate copula conditioning/partial evidence, public
scalar-linear GVM-mixture fitting and full-mixture linear/angular MI. See the
[release record](docs/RELEASE_6_1.md) for bounded contracts and validation.
Higher-dimensional fitting, automatic component selection and mixed/count copulas
remain candidates. Temporal grammar work is outside this library program.

Status meanings: **native** = a current named Figaro element exists; **composable** =
a mathematical construction is available but may lack a tested density/evidence API;
**researched** = definition/method investigated, no shipped implementation implied;
**wishlist** = candidate requiring assessment. Native does not mean every algorithm
or numerical edge case is validated. Family rows below describe expansion work, not
blanket support for all family members. Circular von Mises is now implemented and
validated on main; joint GVM is a development preview integrated through mutual information.
The nine D3 common-family representatives are also integrated; specialized variants
and other directional families remain future work.

The approved follow-on has integrated [legacy numerical contracts](docs/LEGACY_DISTRIBUTION_CONTRACTS.md)
and [LKJ/inverse-Wishart covariance modeling](docs/COVARIANCE_PRIORS.md). The bounded
[query-aware rare-event proposal](docs/RARE_EVENT_PROPOSALS.md) milestone is integrated
on main at CI-verified `751579be`. See the [ordered delivery plan](ROADMAP.md); these priorities do not
mark the remaining family flavors or generic inference guarantees complete.

Weighted event mixtures, restricted static root proposals and extended reusable
constructions are integrated as modern.22 at `742061bd`, with passing main CI.
Modern.23 adds [observation semantics](docs/OBSERVATION_MODELS.md),
[mixed scalar measures](docs/MIXED_MEASURES.md), [fixed GVM mixtures](docs/GVM_MIXTURES.md)
and [continuous copulas](docs/COPULAS.md). The [GVM/GMM comparison](docs/GVM_MIXTURE_RESEARCH_PLAN.md)
now includes the 6.1 bounded scalar-linear fitter and stronger representation controls.
Higher-dimensional fitting, optimized wrapped-model controls and automatic component
selection remain open; application tracking/fusion is outside this library milestone.

Use stable `DIST-xx` IDs when moving work into the roadmap. Priorities are P0 (first
program), P1 (common missing breadth), P2 (subsequent breadth) and P3 (specialist research).
Record a source, concrete use case, representative first member, dependencies, numerical
and inference tests, and acceptance evidence when promoting a candidate. Keep aliases
and special cases attached to their family instead of creating duplicate implementations.

## Broad families first

| ID / priority | Family and current expansion status | First useful capability | Later flavors / shared work |
| --- | --- | --- | --- |
| DIST-01 / P0 | Circular von Mises native; S2 von Mises-Fisher implemented for modern.18 | [Spherical kernel and divergences](docs/DISTRIBUTION_BREADTH.md), alongside circular angle conventions | Other vMF dimensions, wrapped normal/Cauchy, Kent and Bingham with sphere/axis-aware contracts |
| DIST-02 / P0 | Joint linear-angular; through mutual information integrated with D3 at CI-verified `e78a6f0e` | [Fixed-kernel Horwood-Poore Gauss-von Mises](docs/GAUSS_VON_MISES.md) using DIST-01 | Mardia-Sutton, GVM mixtures, multiple angles and newer generalized GVM variants; separate quadrature/uncertainty-propagation research and application-level review, not an automatic filtering claim |
| DIST-03 / P1 | Real-line location/scale and heavy tails; Student t/Cauchy/Laplace now native, Normal already native | [Three new location/scale representatives](docs/COMMON_DISTRIBUTIONS.md), with KL/Bhattacharyya support | Logistic, skew/noncentral variants, generalized normal, stable and hyperbolic families |
| DIST-04 / P1 | Finite choices and count laws; negative binomial/hypergeometric now native | Real-shape failure counts and finite-population draws; count divergences and explicit finite-table MI | Beta mixtures, heterogeneous Bernoulli sums, noncentral and zero-truncated flavors |
| DIST-05 / P1 | Positive-valued scale/lifetime laws; lognormal/Weibull now native | Observation-ready kernels and analytic/guarded divergences; Gamma special-case conveniences remain | Inverse Gaussian, Rayleigh/Rice/Nakagami, generalized Gamma, fatigue-life and survival variants |
| DIST-06 / P1 | Bounded scalar laws; triangular/Kumaraswamy now native alongside Beta/Uniform | Kernels, adapters and guarded divergences; reusable bounded transforms remain | PERT, trapezoidal, raised cosine, logit-normal and continuous Bernoulli/binomial |
| DIST-07 / P2 | GEV/GPD implemented for modern.18 | [Parameter-dependent support and tail APIs](docs/DISTRIBUTION_BREADTH.md), including zero-shape Gumbel/exponential limits | Convenience Frechet/Lomax aliases, Zipf-Mandelbrot and further tail/truncation variants |
| DIST-08 / P2 | MVN/Dirichlet native; multivariate t and multinomial count vectors implemented | [Joint counts and complementary-block MI](docs/DISTRIBUTION_BREADTH.md), alongside elliptical t | Negative multinomial, Dirichlet count mixtures, joint survival and continuous-categorical |
| DIST-09 / P2 | Restricted Wishart, inverse-Wishart and LKJ integrated through modern.20 | [Covariance priors and KL/Bhattacharyya](docs/COVARIANCE_PRIORS.md) | Near-singular df, matrix normal/t/beta, matrix Langevin, Stiefel-uniform; support measures differ |
| DIST-10 / P1 | Construction/GMM kernels, mixed measures and bounded 6.1 GVM fitting/MI implemented | Affine/exp/monotone transforms, finite/half-infinite truncation, folding, wrapped Cauchy, count/scalar/GVM mixtures, GMMs, zero-adjusted counts, atoms/slabs, clipping and censoring evidence | Generic wrapped sums, compound Poisson-Gamma, mixed convolutions; higher-dimensional GVM fitting, component selection and broader mixture information |
| DIST-11 / P3 | Flexible systems and quantile-defined laws; wishlist | Valid parameter/monotonicity contracts before fitting APIs | Pearson, Johnson, metalog, quantile-parameterized, Tukey lambda and Wakeby |
| DIST-12 / P3 | Singular/degenerate and nonnumeric measures; Constant/Select native | Explicit distinction between atoms, densities and singular laws | Cantor, circular point masses; no fabricated Lebesgue PDF for a singular law |
| DIST-13 / P3 | Physical/scattering and random-matrix spectral laws; wishlist | Verify domain, reference measure and normalization first | Henyey-Greenstein, Mie, Marchenko-Pastur, Wigner, Tracy-Widom and other specialist laws |
| DIST-14 / P3 | Process-derived and niche discrete families; wishlist | A reusable phase-type/compound-count representation when justified | Absorption-time, branching, occupancy/partition and specialized empirical laws |

The first implementation plan is [von Mises and GVM](docs/GAUSS_VON_MISES_PLAN.md).
The nine D3 representatives have [passing CI and main-integration evidence](docs/COMMON_DISTRIBUTIONS_ACCEPTANCE.md);
consult that record for numerical/inference limits and the separate advisory timing result.
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
- **Third-order sparse quadrature — on main at CI-verified `b06e957f`:**
  [Scalar/vector expectations](docs/GVM_QUADRATURE.md) with 2n+3 signed-weight nodes,
  a specified canonical exactness class and explicit failure examples. No automatic
  error bound or refinement; not a filtering implementation.
- **Positive-weight tensor reference — on main at CI-verified `9cbcce00`:**
  [Adjustable Gaussian/angular order](docs/GVM_TENSOR_QUADRATURE.md), streamed points,
  exponential callback-budget guard and analytic accuracy-versus-cost cases. Positivity
  is not accuracy.
- **Budgeted order-comparison diagnostics — on main at CI-verified `4f90f815`:**
  [Four-corner Gaussian/angular comparisons](docs/GVM_QUADRATURE_COMPARISON.md),
  total callback-budget preflight, per-output tolerance flags and directional changes.
  Explicit false-agreement tests prevent treating these diagnostics as certified error
  bounds or automatic stopping criteria. No new sampler or parallel execution mode.
- **Bhattacharyya divergence — bounded Scala API on main at CI-verified `251f9540`:**
  [Exact angular reduction and Fourier/Gaussian series](docs/GVM_BHATTACHARYYA_RESEARCH.md)
  with 12 research tests and passing CI at `b5da340c`, independent positive integration
  and an analytic truncation bound. The [guarded Scala API](docs/GVM_BHATTACHARYYA.md)
  now adds scaled coefficient evaluation, exact reductions and explicit unresolved
  outcomes, initially for nonidentity concentrations through 50 and dimensions through
  32. A [matched-accuracy timing study](docs/GVM_BHATTACHARYYA_PERFORMANCE.md) now records
  substantial six-dimensional tensor-grid savings and faster reduced/reused counterexamples;
  study is integrated on main through CI-verified `e30c8b03`. Wider concentration
  support and rigorous rounding-error certification remain separate work.
- **Bhattacharyya numerical reliability — stress grid on main at CI-verified `e30c8b03`:**
  [96 fixed pairs at three tolerances in both directions](docs/GVM_BHATTACHARYYA_RELIABILITY.md),
  with high-precision oracles and representative independent positive integration controls.
  All 460 resolved comparisons meet requested accuracy; 116 remain explicitly unresolved.
  This does not broaden concentration/dimension caps or establish a statistical success rate.
- **Positive scalar Bhattacharyya integration — research CI passed:**
  [Positive-integrand assessment](docs/GVM_BHATTACHARYYA_POSITIVE_RESEARCH.md) avoids signed-series
  cancellation on 84 scalar fixtures and checks ten unequal-concentration pairs. Seven
  tests cover accuracy, budgets, cancellation and range/precision refusals.
  [CI passed at `70b083f4`](https://github.com/mattwilkinsphoto/figaro/actions/runs/34194233164).
  Integrated on main through CI-verified `a54d665e`. No automatic fallback or wider caps.
- **Positive scalar Scala comparison — public opt-in API, integrated on main at `21269b97`:**
  The [physical-kernel prototype](docs/GVM_POSITIVE_SCALAR_PROTOTYPE.md) has moved into
  the [public scalar comparison](docs/GVM_SCALAR_BHATTACHARYYA.md). It retains the
  oracle grid, preprocessing-error estimates, budgets, interruption and concurrency
  controls, and adds analytic shortcuts, near-limit tests and executable user examples.
  `Estimated` is not a certified bound; no automatic Fourier fallback or multidimensional extension.
- **Scalar method-selection evidence — integrated on main through CI-verified `42d4ec1f`:**
  The [matched-accuracy study](docs/GVM_SCALAR_PERFORMANCE.md) retains three fresh JVMs,
  eight fixtures and 336 rounds at a 1e-8-nat target, with 13 evidence/oracle checks.
  Fourier is the starting choice for ordinary coupled cases; positive integration
  recovers the tested eligible tiny-overlap refusals.
- **Audited scalar totals — integrated on main at CI-verified `213aa587`:**
  [Profile-driven bookkeeping improvement](docs/GVM_SCALAR_AUDITED_TOTALS.md) retains
  mandatory fresh final sums, matches the frozen control on 299 differential comparisons,
  and improves three fixtures by about 2.9–7.75x across three JVMs. Six evidence/provenance
  tests protect the 126 timing records and frozen control. Residual integrand and
  initial-panel work are the next performance candidates, not relaxed error safeguards.
- **Scalar tail-radius selection — research-only assessment complete:**
  [Positive cell-minimum bounds](docs/GVM_SCALAR_TAIL_ASSESSMENT.md) reduce the candidate
  radius in 49 of 84 fixtures; the curved fixture's initial partition falls from 4,650
  to 1,494 panels while the difficult tiny-overlap case retains its wide radius.
  Seven tests include oracle validation and evidence freshness. The follow-on
  [test-only JVM candidate](docs/GVM_SCALAR_TAIL_JVM.md) now has outward-rounded cell
  arithmetic, independent phase-bound controls and setup-inclusive timings: about 2.54x
  on the costly curved fixture, little change on four others. Its original 325-test
  milestone is preserved in the report. [Production integration](docs/GVM_SCALAR_TAIL_PRODUCTION.md)
  now retires duplicate prototype code, adds 44 held-out/boundary fixtures and passes
  319 distinct modernization tests, artifact checks and the published consumer.
  A repeated timing study retains about 2.53x on the curved fixture. Integrated on main
  at CI-verified `f4884cfb`; this targeted milestone is complete, with no further scalar
  micro-optimization scheduled here.
- **Mutual information — approved implementation milestone:** the
  [guarded GVM diagnostic](docs/GVM_MUTUAL_INFORMATION.md) uses analytic conditional
  entropy and one-dimensional Fourier marginal integration, with independence controls,
  high-precision oracles and numerical refusal statuses. It measures the full linear
  vector versus angle within one law, not the difference between two GVMs.
- **Cross-family information measures — approved continuing direction:** retain KL,
  Bhattacharyya and MI in the capability assessment for all broad distribution families.
  Follow [INFO-01 through INFO-05](docs/INFORMATION_METRICS_ROADMAP.md): inventory and
  conventions, exact finite-discrete/Gaussian representatives, guarded numerical methods,
  then specialized variants. Keep Mahalanobis scoring separate and applicable geometry
  explicit. This is a roadmap, not current blanket support or an immediate universal API.

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
