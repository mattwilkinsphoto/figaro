# Sampling research: recent methods and a bounded implementation screen

Research date: 6 September 2026. Branch: `modernize/sampling-research`, based on reliability commit `0c8732b7`. This is an experimental examples/reporting milestone. The production Figaro library, modern.8 version, existing samplers, precision policy, and toolchain are unchanged.

## Overview and decision

We searched the open web for methods that might address the curved-target undercoverage in the [reliability audit](MCMC_RELIABILITY_VALIDATION.md), rather than merely generating more draws. The shortlist favors gradient-free methods compatible with Figaro's arbitrary likelihood callbacks, explicit posterior correction, observable-specific accuracy checks, and credible implementation availability. Dates below refer to publication/submission history, not search-engine crawl timestamps. This is a targeted research screen, not an exhaustive systematic review.

Two recent methods were practical to independently prototype in Scala without adding dependencies: **uniform multiproposal elliptical slice sampling (MESS)** and **quantile slice sampling**. We also implemented the M=1 elliptical baseline for comparison. These are standalone immutable-vector kernels inside an example, not factories for arbitrary Figaro models. The comparator uses Figaro's actual Gaussian-block MH runner.

**Recommendation:** prioritize quantile slice sampling for a broader, equal-computation validation before integrating a production API. Its fixed Cauchy-reference prototype improved the curved target's error and stopped coverage at equal retained draw counts, but consumed about 5.4 times as many density evaluations. Ordinary elliptical sampling was effective on the separated-mode fixture; increasing its proposal count did not reliably improve cost-normalized performance or curved-target coverage. No production default should change on this evidence.

## Key research and integration assessment

### 1. Quantile slice sampling — 2024, revised June 2025

Heiner, Johnson, Christensen, and Dahl use a reference distribution's CDF to perform slice search on the unit interval, correcting with the target/reference density ratio. An accessible [paper, including Algorithm 2](https://arxiv.org/html/2407.12608v2) and [CRAN qslice package](https://CRAN.R-project.org/package=qslice) are available. The CRAN listing offers MIT/Apache licensing options; no R code or dependency was imported here.

Our assessment: a good near-term fit for scalar conditional updates and models without gradients. A heavy-tailed reference can address some proposal-tail mismatches, but poor conditional geometry and numerical CDF saturation remain concerns. The prototype uses fixed Cauchy(0,2) references and a fixed-order coordinate sweep. It does not implement reference fitting or the paper's broader multivariate/conditional transport machinery. Its observed strengths and costs are below.

### 2. Multiproposal elliptical slice sampling — February 2026 preprint

Senn, Glatt-Holtz, Carigi, Holbrook, and Tjelmeland extend Gaussian-reference elliptical sampling by evaluating multiple angles per search batch, with uniform or distance-informed selection. The [open paper](https://arxiv.org/html/2602.22358v1) gives invariance arguments and application experiments. The [authors' repository](https://github.com/guillerminasenn/mess) explicitly leaves parallel likelihood evaluation to users.

Our assessment: relevant to expensive Gaussian-prior likelihoods, but more proposals are not free computation. We implemented only the uniform-selection variant and M=1 baseline; no LP selection or candidate parallelism. A future graph integration must evaluate candidates in isolated state, not mutate one shared universe concurrently. Our curved-target results do not justify prioritizing this as the immediate reliability fix.

### 3. Adaptive generalized elliptical slice sampling — May 2026, revised August 2026 preprint

Marco and Tokdar address mismatch between the target and ellipse-generating reference through adaptation, with ergodicity analysis and non-elliptical/heavy-tailed application studies. See the [latest inspected paper](https://arxiv.org/html/2605.21659v3) and [MIT-licensed Julia implementation](https://github.com/ndmarco/AdaptEllipticalSliceSampler.jl).

Our assessment: highly relevant to the remaining reference-mismatch problem. It is not a small toggle on the frozen pilot-covariance implementation. We would need a separate implementation review, adaptive-state ownership, appropriate diagnostics for the chosen chain arrangement, and tests of adaptation/stationarity assumptions. Shortlisted for a follow-up comparison, not implemented or benchmarked here. Claims in the paper should not be generalized to every Figaro target.

### 4. Gibbsian polar slice sampling and parallel affine transformation tuning — ICML 2023/2024

Schär, Habeck, and Rudolf's [GPSS paper](https://proceedings.mlr.press/v202/schar23a.html) separates radial and directional updates; their [PATT paper](https://proceedings.mlr.press/v235/schar24a.html) learns affine coordinates across chains. [GPSS experiment code](https://github.com/microscopic-image-analysis/Gibbsian_Polar_Slice_Sampling), [PATT reproduction material](https://gitlab.gwdg.de/crc1456/livedocs/a05-patt-mcmc), and a [Julia slice-sampling package](https://github.com/TuringLang/SliceSampling.jl) are accessible.

Our assessment: strong gradient-free candidates for anisotropy and heavy tails. Affine whitening cannot generally straighten nonlinear curvature. This also changes the sampler more substantially than merely choosing a block covariance. Native integration needs radial/angular numerical checks and a clear boundary between adaptive/shared ensemble state and independent production chains. Not implemented in this screen.

### 5. Learned nonlinear transport — TESS (AISTATS 2023) and flowMC

Cabezas and Nemeth's [Transport Elliptical Slice Sampling](https://proceedings.mlr.press/v206/cabezas23a.html) learns nonlinear coordinates before elliptical updates. [flowMC](https://github.com/kazewong/flowMC) supplies an actively developed MIT-licensed JAX implementation combining local and learned global proposals; its foundational methods predate this screen. The [current architecture](https://gw-jax-team.github.io/flowMC/development/guides/architecture/) requires JAX-compatible likelihoods.

Our assessment: potentially the most direct way to learn curved geometry, but not an easy drop-in for arbitrary Scala callbacks. Training cost, missed modes in training data, density/Jacobian correctness, and runtime integration need their own milestone. We did not install JAX/PyTorch or introduce a Python service into Figaro. A target-density correction is important; sampling only from a fitted approximation would introduce a different accuracy problem.

### 6. Stratification and randomized quasi-Monte Carlo — 2025/2026 research

These papers are particularly relevant to the earlier question about LHS/stratification:

- Ho, Owen, and Pan, [Quasi-Monte Carlo with one categorical variable](https://arxiv.org/abs/2506.16582), June 2025, revised January 2026: studies allocation across mixture components, including benefits of oversampling small components under specified RQMC rates. This is attractive for an explicitly weighted stratified/mixture importance interface, not permission to average oversampled categories without correction.
- Chen, Du, Wang, and He, [Adaptive importance sampling with recycling via QMC](https://arxiv.org/abs/2505.05037), May 2025: combines proposal learning, sample recycling, and RQMC, including banana and mixture experiments. Its assumptions and weighting machinery would need validation in Figaro; replacing the random stream alone does not reproduce the method.
- Du and He, [RQMC self-normalized importance sampling for unbounded integrands](https://arxiv.org/abs/2511.10599), November 2025, revised July 2026: gives error-rate analysis under boundary-growth conditions. This matters for second moments/tails, but is not a blanket confidence-interval theorem for arbitrary likelihood-weighted models.

Our assessment: retain this as a separate forward/importance variance-reduction track. Begin with randomized LHS/Sobol designs on a fixed-dimensional interface and independent randomized replicates for error assessment. Do not feed those outputs uncritically into the current Markov-chain stopping diagnostics. These papers were screened at the abstract/method-summary level; their full proofs and software have not been audited or implemented here.

All papers essential to the implemented prototypes were accessible in full on the open web. **No paywalled PDF is currently needed.**

## What was implemented

[SamplingResearchExample.scala](../FigaroExamples/src/main/scala/com/cra/figaro/example/SamplingResearchExample.scala) contains independently written research kernels from the published mathematical algorithms. No external sampler source was copied or vendored, and no new library dependency was added.

The MESS repository's `LICENSE` at inspected commit `95aabf70e38421830ea19ab4d413b00be0826da4` contained only a placeholder. Public visibility was not treated as a redistribution license; see [GitHub's distinction](https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/customizing-your-repository/licensing-a-repository). The MESS paper is CC BY 4.0 and is attributed in the source. This provenance note does not assert an exhaustive intellectual-property clearance or infer the authors' intentions.

The prototypes have bounded searches, explicit invalid-density/numerical failures, interruption checks, and immutable input states. MESS uses a fixed diagonal Gaussian reference, log-scale slice levels, the paper's bracket shrinkage, and uniform selection among valid proposals. Quantile sampling includes the reference-density correction and rejects CDF boundary saturation rather than clipping it. Search exhaustion aborts; it never fabricates an accepted draw or silently drops a failed run. These protections are research safeguards, not a complete production lifecycle contract.

## Experimental design

Thirty preassigned seed labels (`141011 + round*7919`, rounds 0-29), five samplers, three two-dimensional analytic targets, four chains each, 2000 warm-up and 12000 retained draws per chain. The two disjoint runs cover the elliptical/block methods and quantile method respectively; all completed. There are **450 attempted target/sampler/seed experiments**, producing 4500 query records across 900 fixed/stopped groups. Fixed and stopped records are paired observations of the same trace, not independent experiments.

| Target | Definition | Five monitored truths |
| --- | --- | --- |
| Gaussian | X,Y independent standard Normals | E[X]=E[Y]=0; E[X²]=E[Y²]=1; P(\|X\|>2)=0.045500263896358334 |
| Banana | X=Z; Y=0.4(Z²-1)+0.5E, independent standard Normal Z,E | 0, 0, 1, 0.57, same X tail probability |
| Unequal modes | X is 0.8 N(-4,0.25) + 0.2 N(4,0.25); Y independent N(0,1) | -2.4, 0, 16.25, 1, P(X>0) approximately 0.2 |

Normal second parameters above are variances. The unequal mixture tests **relative mode mass**, not merely whether a sampler visits both modes. Its fifth query differs from the absolute-tail event used in the other fixtures.

All methods start from standard Normal draws; none gets an exact posterior initialization or fitted pilot. The actual Figaro block sampler uses increment covariance diag(1,0.57) on banana and identity otherwise. MESS uses N(0,I) reference with one, four, or eight candidates per batch. Quantile sampling uses fixed Cauchy(0,2) references in coordinate order X,Y. Different samplers have different random consumption and are not trajectory-coupled.

All retained draws are sampled before replaying the unchanged modern.8 precision policy on equal-length prefixes every 2000 draws: 0.15-relative full width, nominal 95% Bonferroni-adjusted joint confidence across five observables, default other guards. Fixed coverage is at 12000; stopped coverage is at the first successful checkpoint or cap. Every available interval counts in coverage, including intervals from failed precision assessments. Success-conditional coverage is reported separately.

**Costs are counted density/weight callback evaluations, including initialization and warm-up.** The native block runner counts its actual log-constraint calls, including repeat calls; standalone kernels count their actual residual/full-target calls. They are useful measures of expensive-target work, not equivalent implementations or wall-clock costs. Slice kernels are serial; the Figaro comparator uses two workers. No candidate-parallel speedup, equal-time win, or exact equal-evaluation-budget comparison is claimed. In particular, `evaluationsFullRun` on a stopped record still counts the full generated run, not hypothetical early-stop cost.

## Results

| Target / sampler | Fixed joint coverage | Stopped joint coverage | Precision reached | Coverage among successes | Median full-run evaluations | Median minimum mean ESS / 1000 evaluations |
| --- | --- | --- | --- | --- | --- | --- |
| Gaussian / block | 29/30 | 30/30 | 30/30 | 30/30 | 86966 | 52.82 |
| Gaussian / ESS (M=1) | 30/30 | 29/30 | 30/30 | 29/30 | 56004 | 281.36 |
| Gaussian / MESS-4 | 26/30 | 28/30 | 30/30 | 28/30 | 224004 | 69.85 |
| Gaussian / MESS-8 | 28/30 | 27/30 | 30/30 | 27/30 | 448004 | 35.19 |
| Gaussian / quantile | 29/30 | 29/30 | 30/30 | 29/30 | 340931 | 93.99 |
| Banana / block | 29/30 | 25/30 | 24/30 | 19/24 | 79954 | 18.46 |
| Banana / ESS (M=1) | 25/30 | 21/30 | 19/30 | 10/19 | 107756 | 8.13 |
| Banana / MESS-4 | 24/30 | 19/30 | 21/30 | 10/21 | 257654 | 5.07 |
| Banana / MESS-8 | 22/30 | 18/30 | 20/30 | 8/20 | 471880 | 2.49 |
| Banana / quantile | 29/30 | 29/30 | 30/30 | 29/30 | 434218 | 10.53 |
| Unequal modes / block | 0/30 | 0/30 | 0/30 | 0/0 | 78460 | unavailable |
| Unequal modes / ESS (M=1) | 28/30 | 29/30 | 30/30 | 29/30 | 199362 | 8.85 |
| Unequal modes / MESS-4 | 28/30 | 29/30 | 30/30 | 29/30 | 365572 | 4.91 |
| Unequal modes / MESS-8 | 29/30 | 28/30 | 30/30 | 28/30 | 579384 | 3.05 |
| Unequal modes / quantile | 29/30 | 29/30 | 30/30 | 29/30 | 513628 | 6.01 |

ESS here means elliptical slice sampling; in the last column it means **effective sample size**, calculated by Figaro's existing raw-mean diagnostic. The column uses the lowest of the five query ESS estimates in each run, normalized by that run's total evaluations, then takes the median. It is marked unavailable if any run has a missing query ESS; the trapped block sampler has constant mode-event traces in some runs. These are estimated efficiencies, not independent accuracy certificates.

Some accuracy checks independent of ESS estimates are especially informative:

| Sampler | Banana Y² RMSE | Unequal-mode event-probability RMSE |
| --- | --- | --- |
| Figaro block | 0.04360 | 0.39423 |
| ESS (M=1) | 0.12676 | 0.00396 |
| MESS-4 | 0.06413 | 0.00349 |
| MESS-8 | 0.05054 | 0.00258 |
| Quantile/Cauchy | 0.02161 | 0.00762 |

Quantile sampling roughly halves banana Y² RMSE at these retained counts, and all its stopped runs pass the precision checks with 29/30 joint coverage. However, its median evaluation cost is 434218/79954, about 5.43 times the block cost, and its cost-normalized minimum ESS is lower. This supports a reliability-focused follow-up, **not an established efficiency win**. Thirty replications cannot certify 95% coverage or distinguish small differences reliably.

The elliptical methods recover the unequal mode weights far better than the local block comparator, but perform poorly on the banana. MESS spends more work per step and does not consistently improve either coverage or estimated ESS per evaluation over M=1. This is consistent with reference mismatch remaining important; it is not a refutation of the paper's different, expensive-likelihood workloads or untested distance-informed variants.

## Quick start and three common workflows

From this research branch, with the usual JDK 17 / Scala 3 / sbt 2 setup:

1. Check the kernels: `sbt "examples / Compile / runMain com.cra.figaro.example.SamplingResearchExample check"`.
2. Run a smoke experiment: `sbt "examples / Compile / runMain com.cra.figaro.example.SamplingResearchExample 1 2000"`.
3. Reproduce the full comparison and summarize as below.

### Inspect a complete existing result

```sh
python3 -B tools/summarize_sampling_research.py docs/sampling-research-results.csv --repetitions 30 --draws 12000
```

This validates the checked data and recomputes both tables. [Raw records](sampling-research-results.csv) include each query's estimate, interval width, decision, failure reasons, and full-run evaluation count.

### Repeat the full comparison

```sh
sbt "examples / Compile / runMain com.cra.figaro.example.SamplingResearchExample 30 12000" > sampling-research.log
python3 -B tools/summarize_sampling_research.py sampling-research.log --repetitions 30 --draws 12000
```

The summarizer refuses incomplete or duplicate groups and contradictory decisions/coverage/cap replay. Optional `--output PATH` creates a normalized CSV and refuses to overwrite an existing file; `--acl-script PATH` invokes an access-grant hook immediately after writing for Windows workspaces that require it.

### Split a run into disjoint method groups

```sh
sbt "examples / Compile / runMain com.cra.figaro.example.SamplingResearchExample 30 12000 figaro-block,mess-1,mess-4,mess-8" > elliptical.log
sbt "examples / Compile / runMain com.cra.figaro.example.SamplingResearchExample 30 12000 qslice-cauchy" > quantile.log
python3 -B tools/summarize_sampling_research.py elliptical.log quantile.log --repetitions 30 --draws 12000
```

The [examples API reference](../FigaroExamples/README.md#sampling-research-experimental) documents the only public function, its arguments, return/side effects, examples, and failures. Internal kernels are intentionally private. This is not a supported API for passing live Figaro elements into a slice sampler.

## Verification, limitations, and next step

Kernel checks cover invalid parameters, NaN/positive-infinite densities, exhausted search, callback propagation, repeatable seeds, immutable states, nonzero-mean unequal-scale Gaussian preservation, a nonconstant likelihood with analytic Gaussian posterior, and one-step preservation of unequal mode weights from independent exact target draws. Quantile checks include CDF boundary failure and the target/reference correction. They passed locally. Three report-tool tests cover complete shards, missing/duplicate/corrupt records, and matching cap replay/costs. CI additionally runs the kernel checks, all-method smoke experiment, and validation of the complete checked data.

Remaining gaps: fixed-dimensional vectors only; no general graph/evidence integration, discrete variables, production cancellation/lifecycle ownership, candidate parallelism, fitted references, gradients, or online adaptation. No numerical library/package was installed to run another ecosystem's sampler. Existing independent-chain MCSE checks can still miss unexplored structure; a stop label is not a theorem. High-dimensional, constrained, heavy-tailed, asymmetric curved, and more difficult multimodal targets remain untested.

The next milestone should compare quantile sampling and a geometry-adaptive candidate (AGESS or PATT/GPSS) with more targets and predeclared comparable evaluation budgets, separately evaluating initialization cost and stopped coverage. Only then choose a native production integration. A distinct LHS/RQMC importance track remains worthwhile; mixing it into the current MCMC RNG interface would obscure the statistical assumptions.

Related: [reliability guide](MCMC_RELIABILITY.md), [reliability audit](MCMC_RELIABILITY_VALIDATION.md), [parallel-performance guide](PARALLEL_PERFORMANCE.md), and [pilot calibration](PROPOSAL_CALIBRATION.md).
