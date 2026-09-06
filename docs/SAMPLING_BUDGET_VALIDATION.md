# Matched-density-budget sampling validation

## Protocol fixed before the experiment

Research branch `modernize/sampling-budget-validation`, based on `22bba687`.
This is a follow-up to [the draw-matched screen](SAMPLING_RESEARCH.md), not a production release.

The predeclared comparison uses 30 new seed labels (`812031 + 104729 * round`),
four independently seeded chains, and five two-dimensional targets: standard Gaussian,
rotated anisotropic Gaussian, banana, multivariate Student t with five degrees of freedom,
and the previous unequal mixture. Five observables are X, Y, X², Y², and an event probability.
All analytic truths are specified in code, never fitted from experiment output.

Methods: fixed unit-increment random-walk Metropolis, the previous fixed-Cauchy quantile
slice sweep, plain Gibbsian polar slice sampling (GPSS), and finite-pilot affine-tuned GPSS.
All are standalone immutable-vector kernels with the same counted target callback.
The random-walk comparator is not the native Figaro graph runner, so this is not a
direct cost comparison with the earlier graph-based benchmark.

Each chain receives a strict ceiling of 100000 target-density evaluations, including
initialization, 500 pilot transitions, and 500 additional warm-up transitions. The
affine method updates a per-chain mean/regularized covariance after pilot transitions
100, 250, and 500, then freezes it. Other methods discard the same number of transitions.
No pilot information is shared between chains. No gradients, target-specific tuning,
oracle initialization, or post-result parameter selection are used.

Reports use matched evaluation checkpoints 25000, 50000, and 100000 per chain.
Only complete transitions within a checkpoint enter its trace; an unfinished transition
is never converted to a draw. Unequal chain lengths are aligned to the shortest prefix,
with discarded excess draws and pilot costs reported. This computational stopping rule
itself can select state-dependent trace lengths; coverage is measured empirically, not
assumed to retain fixed-draw guarantees. Density counts do not equate CPU time or include
linear algebra/diagnostic overhead. The full experiment is serial, not a thread speed test.

At each checkpoint the unchanged modern.8 precision assessment uses relative full width
0.15, minimum 2000 draws per chain, and nominal Bonferroni-adjusted 95% confidence over
five observables. The selected-stop record uses the first successful budget checkpoint
or the final cap. Report all-run joint coverage, success count, success-conditional
coverage, observable RMSE, and failures, without dropping inconvenient seeds. The
stopped check schedule is cost-based here, not the earlier every-2000-draw schedule.

The geometry candidate follows the affine-transform principle in
[Schär, Habeck, and Rudolf (2024)](https://arxiv.org/html/2401.16567v2) and the radial/angular
kernel in [their GPSS paper (2023)](https://proceedings.mlr.press/v202/schar23a.html).
It is a finite-adaptation, independently piloted variant, not a reproduction of the
paper's shared-chain PATT system or its published performance claims. All sampler
implementation code is independently written; no third-party source is imported.

Selection remains provisional: prefer a candidate only when its error/coverage improves
across targets at these costs, and expose regressions. Thirty replications and small
analytic fixtures cannot certify coverage or general-purpose superiority. Production
graph integration and high-dimensional validation require separate decisions.
