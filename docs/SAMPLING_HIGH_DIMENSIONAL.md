# Higher-dimensional GPSS and quantile validation

## Protocol fixed before the experiment

Branch `modernize/sampling-high-dimensional`, based on `bde34b65` plus the CI foreground
startup fix. This follows [matched-budget validation](SAMPLING_BUDGET_VALIDATION.md).
Production library APIs/defaults and dependencies remain unchanged.

Twenty new seed groups (`1700113 + 130363 * round`, rounds 0-19), dimensions 8 and 32,
six analytic targets, two methods (`gpss`, `quantile`), and four independently seeded
chains give 480 experiments. Each chain has 300000 target-density evaluations including
initialization and 200 discarded warm-up transitions. Checkpoints are 75000, 150000,
and 300000. No affine fitting or post-result tuning is permitted in this screen.

Targets: independent Gaussian; equicorrelated Gaussian (correlation 0.95); independent
banana pairs X=Z,Y=0.4(Z²-1)+0.5E; multivariate Student t(5); independent rate-1
exponentials on the positive orthant; and an asymmetric mixture with shared mode label,
0.9 N(-2*1,0.25 I) + 0.1 N(3*1,0.25 I). Initial states are standard Normal, except
positive-target coordinates use 0.1+abs(Normal). Initial states are not exact target draws.

Six monitored observables are first coordinate, last coordinate, first coordinate squared,
mean squared coordinate, first-times-last coordinate, and P(|first|>2), except the positive
target uses P(first>2) and the mixture uses P(first>0). Truths are specified analytically
before execution, with an independent Python check. Aggregate moments alone cannot hide
bad marginal means or mode mass. These six observables still do not exhaust all coordinates.

GPSS uses (d-1)*log(radius), a normalized Gaussian projected onto the tangent hyperplane,
geodesic shrinkage, and width-1 radial stepping-out/shrinkage. Degenerate tangents are
redrawn with a bounded limit; zero radius, numerical collapse, and exhausted search fail
explicitly. Quantile sampling reuses the fixed-Cauchy(0,2) coordinate sweep, with exact
target/reference correction. Both implementations use immutable vectors and bounded calls.

Only complete transitions whose cost fits a checkpoint are retained. Chain lengths are
aligned to the shortest prefix; excess completed draws and all warm-up costs remain
charged. Incomplete transitions never become samples. Full traces precede replay, so
this is a cost-matched statistical audit, not an online or wall-clock speed benchmark.
State-dependent cost stopping can itself select trace lengths; coverage is measured,
not assumed. Numerical/model failures invalidate that run conservatively at every
checkpoint and stay in all coverage denominators. Interruption aborts the experiment.

The unchanged modern.8 assessment uses minimum 2000 retained draws per chain, relative
full width 0.15, and nominal 95% Bonferroni-adjusted confidence across six observables.
Selected stopping is the first successful cost checkpoint or final cap. Report fixed
and selected-stop joint coverage, successes, success-conditional coverage, failures,
aligned draws, and marginal/aggregate/event RMSE. No promotion threshold is inferred
from a small difference in 20 replications.

Controls will test tangent geometry, polar-Jacobian correctness, support preservation,
one-step stationarity from independently generated analytic target draws, higher-dimensional
moments/cross moments, hard caps, reproducibility, and partial-transition replay. Analytic
oracles are an independent check of target preservation, not a cross-language replication
of a third-party implementation; that separate comparison remains a limitation.

Primary references: [GPSS (2023)](https://proceedings.mlr.press/v202/schar23a.html),
[quantile slice sampling (2025 revision)](https://arxiv.org/html/2407.12608v2), and the
[Julia GPSS documentation](https://turinglang.org/SliceSampling.jl/stable/gibbs_polar/).
No external sampler source is copied or installed. Research controls do not establish
mixing on arbitrary models, and no general graph integration is included.
