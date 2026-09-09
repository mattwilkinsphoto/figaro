# Observation, mixed-measure, GVM-mixture and copula acceptance

## Delivered scope

The four approved priorities form one additive modeling release, modern.23, implemented
in order with separate capability tests and a shared artifact/consumer integration gate.
No runtime dependency or existing inference default changes.

1. [Observation semantics](OBSERVATION_MODELS.md): exact, interval, left/right censoring,
   rounding bins, stable Gaussian log tails and parameter-dependent graph factors.
2. [Mixed measures](MIXED_MEASURES.md): finite atoms plus continuous slab, clipping and
   rectification, exact-mass evidence and full-law KL/Bhattacharyya decomposition.
3. [GVM mixtures](GVM_MIXTURES.md): fixed kernels, normalized charts, complete likelihoods,
   responsibilities, linear marginals and fixed-budget information comparisons.
4. [Continuous copulas](COPULAS.md): Gaussian and Student-t latent dependence, existing
   scalar marginals, observation-ready vector adapters and appropriate information APIs.

Public GVM parameter fitting, mixture linear/angle MI, general mixed-measure convolutions,
compound Poisson-Gamma and discrete/mixed copulas are not delivered by these first
bounded milestones. They remain explicit follow-ons rather than implied compatibility.

## Literature baseline, reviewed 2026-09-09

- [Stan observation models](https://mc-stan.org/docs/stan-users-guide/truncation-censoring.html)
  and [measurement error](https://mc-stan.org/docs/stan-users-guide/measurement-error.html)
  establish different likelihoods for exact, rounded, censored and latent measurements.
  We implement those semantics independently using Figaro kernels and graph factors.
- [Horwood and Poore, 2014](https://epubs.siam.org/doi/10.1137/130917296) defines the
  cylindrical GVM family underlying the existing kernel. Adding a finite mixture
  uses normalized component probabilities and the full log-sum density; it is not
  a multiplication/fusion operation. The current implementation reuses that kernel.
- [Kurz et al., libDirectional](https://arxiv.org/abs/1712.09718) is relevant prior
  directional-statistics software work. Circular/directional mixtures must not be
  confused with mixtures of the particular joint Horwood-Poore GVM law.
- [DeMars, Bishop and Jah, 2013](https://scholarsmine.mst.edu/mec_aereng_facwork/3830/)
  develops entropy-triggered Gaussian mixture splitting; [Kulik and LeGrand,
  recent splitting research](https://arxiv.org/abs/2412.00343) is an additional
  literature lead for stronger GMM controls. We do not implement their propagation,
  filtering or splitting algorithms here. This focused search is not a novelty review
  proving that GVM mixtures have never been studied.
- [Stan copulas](https://mc-stan.org/docs/stan-users-guide/copulas.html) gives the
  continuous latent-transform/density-ratio construction. [Feldman and Kowal, 2024](https://www.jmlr.org/papers/v25/23-0495.html)
  addresses a richer mixed/missing-data copula problem; that scope is deliberately
  not inferred from our continuous kernels.

No external implementation was copied or new third-party code installed. Public
availability without a license is not permission to reuse source. The baseline
does not require a new paywalled PDF; a deeper optimal-fitting comparison may need
additional papers. No patent-scope conclusion is made.

## Initial GVM versus GMM representation experiment

This is a controlled **research-only fitting baseline**, not a released GVM fitter,
an optimized-GMM contest, or evidence about operational tracking accuracy.

Four synthetic reference laws cover local near-Gaussian, curved, angular-seam and
separated-mode uncertainty. All are generated from specified GVM kernels, so the
reference family favors GVM representability; this limitation is explicit. Five
predeclared training seeds (103000..103004) each supply 1200 samples. Independent
4000-point evaluation sets use 104000..104004; 4000 candidate draws use 105000..105004.
Within a seed, both methods share training/evaluation data and linear-quantile groups.
Methods alternate order by seed. Component counts are 1, 2 and 4; all outcomes remain.

GMM uses empirical chart-coordinate means/covariances with an explicit 1e-6 diagonal
ridge. GVM uses a linear Gaussian marginal with the same linear variance ridge,
quadratic unwrapped-phase regression and circular residual concentration (research
cap 100). Both use fixed group weights; neither performs EM or automatic model selection.
GVM has 7K-1 parameters versus GMM's 6K-1: equal component count is not equal parameter
count. Sorting/grouping is included in each method's fitting timer. Chart selection
and source-data generation are common preprocessing outside that timer; recorded
fitting times are not full pipeline totals.

The angular chart center is selected from TRAINING circular means, including for
the seam fixture. GMM is explicitly conditioned/renormalized to that chart, not
scored as an unnormalized periodically repeated Euclidean Gaussian. Its sampler uses
bounded rejection of the fitted Euclidean mixture. This control is a chart-conditioned
GMM, not a wrapped-normal mixture. A wrapped control and Gaussian-generated truths
remain necessary before a broad superiority claim.

Three fresh JVMs retain 360 rows: [A](gvm-mixture-representation-a.csv),
[B](gvm-mixture-representation-b.csv), [C](gvm-mixture-representation-c.csv).
These repeat the same 120 statistical trials, not 360 independent datasets.
All completed. Mean held-out log scores (higher is better), averaged over five seeds:

| Fixture | Components | GMM chart moments | GVM regression |
| --- | --- | --- | --- |
| Local | 1 | -1.14 | -1.14 |
| Curved | 1 | -2.84 | -1.41 |
| Curved | 4 | -2.21 | -1.50 |
| Seam | 1 | -1.97 | -1.50 |
| Separated | 2 | -2.40 | -2.40 |

On these fixtures, curvature provides the clearest representation benefit; near-Gaussian
and separated controls show little log-score gain. Increasing components sometimes
hurts held-out scores. Region probability checks use `linear>1 && cos(angle)>0`;
their reference is an independent empirical estimate, not exact truth. First-call/JIT
effects remain in timings; no speedup, matched-error runtime, memory or optimal
component-count claim is based on these exploratory rows.

## Validation and reproduction

```sh
sbt "figaro / Test / testOnly com.cra.figaro.test.modernization.ObservationModelTest com.cra.figaro.test.modernization.GvmMixtureCopulaTest"
sbt "figaro / Test / runMain com.cra.figaro.test.modernization.GvmMixtureRepresentationStudy 5"
python -B tools/test_modeling_reference.py
python -B tools/test_modeling_evidence.py
```

Repeat the study in three fresh JVMs to reproduce the table and inspect timing variation.
Environment: Windows, JDK 17.0.4, Scala 3.9.0, sbt 2.0.8, default scientific LXM RNG.
The independent Python tests require research-only mpmath 1.3.0.

The capability tests cover tail/cancellation failures, exact/interval semantics,
hierarchical measurement error and censoring, mixed atom evidence, full-law information,
GVM reduction/permutation/normalization/chart behavior, seeded draws, Gaussian/t
copula reductions, non-Gaussian marginal frequencies, analytic/numerical MI, invalid
matrices, tail refusal and complete-vector graph evidence. Independent 60-digit
formulas check the extreme Gaussian interval and bivariate copula density fixtures.
Release gates additionally require the complete modernization suite, generated API
freshness, local links, packaged classes, independent published consumer and exact-commit CI.

Local acceptance on 2026-09-09 passed all 555 modernization tests across 57 suites
after compiling 341 library and 247 test sources. Two fresh action-cache builds
produced the same thin JAR SHA-256:
`471da0690c93ee8c12805888b9d7c591a27f1879f553d92b2839dc0c46ef8555`.
The independent consumer compiled and exercised that exact binary after `publishLocal`.
All four archives pass class-content/legal checks; the generated reference contains
12731 public method entries. Documentation tooling (18 tests), four independent
high-precision checks and three complete-evidence checks pass. Existing compiler and
Scaladoc warnings remain; these sources add no new warnings or deprecations.

An intermediate local consumer attempt used the wrong repository (`publishM2`
versus the consumer's local Ivy resolver). A subsequent incremental publication also
produced different class/TASTY bytes from the clean build. Neither was accepted as
the final release gate: the producer was rebuilt/published from a fresh cache and the
consumer rerun with exact hash verification. Remote integration requires successful CI
on the source commit, including its independent cold-build reproducibility check.

Related: [roadmap](../ROADMAP.md), [GVM research follow-ons](GVM_MIXTURE_RESEARCH_PLAN.md).
