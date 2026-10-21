# Research references and code map

This is the consolidated navigation index for scientific sources already used in
Figaro's modernization and capability-expansion work. It connects the source,
its actual role, the relevant implementation, validation and explanatory guide.
It supplements the [API reference](api/README.md), not a replacement for it.

## Scope and evidence labels

The index was assembled from the checked-in research notes and current source
layout. This is **not a new full-text literature review**, a claim that every
external link was revalidated, or an exhaustive historical bibliography for every
legacy Figaro algorithm. Original guides retain the detailed derivations, inspected
revisions, provenance limitations and acceptance records. Legacy tutorial material
remains available through the [documentation bridge](DOCUMENTATION_MIGRATION.md).

- **Implemented / adapted:** a shipped, bounded implementation uses the stated idea;
  departures and restrictions matter. Do not transfer a paper's theorem automatically.
- **Backend:** Figaro wraps an existing provider rather than reimplementing the generator.
- **Definition / validation reference:** notation, parameterization, formula or independent
  numerical check; not necessarily the original invention or copied software.
- **Independent extension:** Figaro-specific derivation or engineering specialization
  informed by a source, not presented as the source's own algorithm.
- **Research-only / candidate:** an experiment or future option, not a production feature.

Each stable ID identifies a reference group. Multiple papers are grouped only when
the mapping needs their joint context. Links under **Validation** identify checks
and recorded evidence, **not tests rerun for this documentation change**. Refer to
the [current roadmap](../ROADMAP.md) and [release scope](RELEASE_6_1.md) for delivery status.

## Find references from a code area

| Code area / task | Reference groups |
| --- | --- |
| RNG engines, stream ownership and purpose selection | [RNG-01](#rng-01), [RNG-02](#rng-02), [RNG-03](#rng-03), [RNG-04](#rng-04) |
| Vector slice sampling and alternative-method screening | [SAM-01](#sam-01), [SAM-02](#sam-02), [SAM-03](#sam-03) |
| Defensive and mixture proposals | [INF-01](#inf-01), [INF-02](#inf-02), [INF-03](#inf-03) |
| Log-space particle weights, tree ancestors and MH proposal ratios | [INF-04](#inf-04), [INF-13](#inf-13) |
| Observation integration, finite enumeration, support-aware proposals and range checks | [INF-05](#inf-05) |
| Warm-up-only scalar proposal tuning | [INF-06](#inf-06) |
| Bounded-work particle recovery assessment | [INF-07](#inf-07) |
| Independent-pilot indicator budget advice | [INF-08](#inf-08) |
| General-likelihood bounded bootstrap filtering | [INF-09](#inf-09) |
| Split-pilot calibration and observation-informed filtering proposals | [INF-10](#inf-10) |
| Auxiliary ancestor selection and linear-Gaussian proposal helpers | [INF-11](#inf-11) |
| Continuous nonlinear/multimodal filtering validation | [INF-12](#inf-12) |
| Multi-chain diagnostics and inference health | [DIA-01](#dia-01), [DIA-02](#dia-02) |
| Sequential tests and precision stopping | [STP-01](#stp-01), [STP-02](#stp-02), [STP-03](#stp-03), [STP-04](#stp-04), [STP-05](#stp-05) |
| Circular/joint GVM, numerical overlap and mixture fitting | [GVM-01](#gvm-01), [GVM-02](#gvm-02), [GVM-03](#gvm-03), [GVM-04](#gvm-04) |
| Cross-family information measures and mixture MI | [MET-01](#met-01), [MET-02](#met-02) |
| Common families, matrix/correlation priors | [DIST-01](#dist-01), [DIST-02](#dist-02), [DIST-03](#dist-03) |
| Conditional copulas and observation semantics | [MOD-01](#mod-01), [MOD-02](#mod-02) |

## Reference-to-code mappings

<a id="rng-01"></a>

### RNG-01: LXM

**Source:** Steele and Vigna, *LXM: Better Splittable Pseudorandom Number Generators (and Almost as Fast)* (2021). [Author-hosted paper](https://vigna.di.unimi.it/ftp/papers/LXM.pdf).

**Role:** Backend / implemented. JDK-backed LXM engines; Figaro's default and stream-allocation policies are separate engineering choices. The paper does not certify arbitrary child-seed allocation or application posteriors.

**Code:** [SamplingRandom.scala](../Figaro/src/main/scala/com/cra/figaro/util/SamplingRandom.scala); [RandomStreams.scala](../Figaro/src/main/scala/com/cra/figaro/util/RandomStreams.scala); [RandomSelection.scala](../Figaro/src/main/scala/com/cra/figaro/util/RandomSelection.scala).

**Validation:** [SamplingRandomTest](../Figaro/src/test/scala/com/cra/figaro/test/modernization/SamplingRandomTest.scala); [RandomStreamsTest](../Figaro/src/test/scala/com/cra/figaro/test/modernization/RandomStreamsTest.scala); [RandomSelectionTest](../Figaro/src/test/scala/com/cra/figaro/test/modernization/RandomSelectionTest.scala).

**Guide:** [RNG LITERATURE REVIEW](RNG_LITERATURE_REVIEW.md); [RNG SELECTION](RNG_SELECTION.md).

<a id="rng-02"></a>

### RNG-02: Xoshiro and Xoroshiro

**Source:** Blackman and Vigna, *Scrambled Linear Pseudorandom Number Generators* (2021). [Author-hosted paper](https://vigna.di.unimi.it/ftp/papers/ScrambledLinear.pdf).

**Role:** Backend / implemented. Named Xoshiro engines and explicit jump allocation. Scramblers and engine sizes are not interchangeable; this does not make all Xoroshiro variants available or prove independence between experiments.

**Code:** [SamplingRandom.scala](../Figaro/src/main/scala/com/cra/figaro/util/SamplingRandom.scala); [RandomStreams.scala](../Figaro/src/main/scala/com/cra/figaro/util/RandomStreams.scala).

**Validation:** [SamplingRandomTest](../Figaro/src/test/scala/com/cra/figaro/test/modernization/SamplingRandomTest.scala); [RandomStreamsTest](../Figaro/src/test/scala/com/cra/figaro/test/modernization/RandomStreamsTest.scala).

**Guide:** [RNG LITERATURE REVIEW](RNG_LITERATURE_REVIEW.md); [RNG STREAMS](RNG_STREAMS.md).

<a id="rng-03"></a>

### RNG-03: PCG and Mersenne Twister

**Source:** O'Neill, *PCG: A Family of Simple Fast Space-Efficient Statistically Good Algorithms for Random Number Generation* (2014): [technical report](https://www.cs.hmc.edu/tr/hmc-cs-2014-0905.pdf), [errata](https://www.pcg-random.org/paper.html). Matsumoto and Nishimura, *Mersenne Twister: A 623-Dimensionally Equidistributed Uniform Pseudo-Random Number Generator* (1998): [authors' bibliography](https://www.math.sci.hiroshima-u.ac.jp/m-mat/MT/ARTICLES/earticles.html).

**Role:** Backend / assessed. Apache Commons RNG supplies the selected PCG/MT engines, not an independent Figaro reimplementation. PCG64DXSM is not implied by generic PCG support. The earlier review could not retrieve the original MT PDF; its bibliography is not evidence of full-text review.

**Code:** [SamplingRandom.scala](../Figaro/src/main/scala/com/cra/figaro/util/SamplingRandom.scala).

**Validation:** [SamplingRandomTest](../Figaro/src/test/scala/com/cra/figaro/test/modernization/SamplingRandomTest.scala); [RngBenchmark](../Figaro/src/test/scala/com/cra/figaro/test/modernization/RngBenchmark.scala).

**Guide:** [RNG LITERATURE REVIEW](RNG_LITERATURE_REVIEW.md); [RNG ASSESSMENT](RNG_ASSESSMENT.md).

<a id="rng-04"></a>

### RNG-04: Counter-based randomness

**Source:** Salmon, Moraes, Dror and Shaw, *Parallel Random Numbers: As Easy as 1, 2, 3* (2011). [Random123 paper](https://www.thesalmons.org/john/random123/papers/random123sc11.pdf).

**Role:** Backend / adapted. Philox backend plus bounded counter-range allocation and versioned purpose selection. General logical per-sample addressing and Threefry are not supplied by this mapping.

**Code:** [SamplingRandom.scala](../Figaro/src/main/scala/com/cra/figaro/util/SamplingRandom.scala); [RandomStreams.scala](../Figaro/src/main/scala/com/cra/figaro/util/RandomStreams.scala); [RandomSelection.scala](../Figaro/src/main/scala/com/cra/figaro/util/RandomSelection.scala).

**Validation:** [RandomStreamsTest](../Figaro/src/test/scala/com/cra/figaro/test/modernization/RandomStreamsTest.scala); [RandomSelectionTest](../Figaro/src/test/scala/com/cra/figaro/test/modernization/RandomSelectionTest.scala).

**Guide:** [RNG STREAMS](RNG_STREAMS.md); [RNG SELECTION](RNG_SELECTION.md).

<a id="sam-01"></a>

### SAM-01: Gibbsian polar slice sampling

**Source:** Schär, Habeck and Rudolf, *Gibbsian Polar Slice Sampling* (2023). [ICML/PMLR paper](https://proceedings.mlr.press/v202/schar23a.html).

**Role:** Implemented / bounded. GPSS for explicit continuous-vector log densities, with the polar Jacobian and owned execution state. Not automatic compilation of arbitrary Element graphs or an unconditional mixing guarantee.

**Code:** [VectorSliceSampler.scala](../Figaro/src/main/scala/com/cra/figaro/algorithm/sampling/VectorSliceSampler.scala).

**Validation:** [VectorSliceSamplerRegressionTest](../Figaro/src/test/scala/com/cra/figaro/test/modernization/VectorSliceSamplerRegressionTest.scala); [MultiChainVectorSliceSamplerRegressionTest](../Figaro/src/test/scala/com/cra/figaro/test/modernization/MultiChainVectorSliceSamplerRegressionTest.scala).

**Guide:** [VECTOR SLICE SAMPLING](VECTOR_SLICE_SAMPLING.md); [SAMPLING HIGH DIMENSIONAL](SAMPLING_HIGH_DIMENSIONAL.md).

<a id="sam-02"></a>

### SAM-02: Quantile slice sampling

**Source:** Heiner, Johnson, Christensen and Dahl, quantile slice sampling (2024 preprint; inspected 2025 revision), Algorithm 2. [Inspected paper revision](https://arxiv.org/html/2407.12608v2).

**Role:** Implemented / adapted. Coordinate-wise quantile slice search uses a fixed Cauchy(0,2) reference and target/reference correction. This is narrower than arbitrary adaptive reference fitting.

**Code:** [VectorSliceSampler.scala](../Figaro/src/main/scala/com/cra/figaro/algorithm/sampling/VectorSliceSampler.scala).

**Validation:** [VectorSliceSamplerRegressionTest](../Figaro/src/test/scala/com/cra/figaro/test/modernization/VectorSliceSamplerRegressionTest.scala).

**Guide:** [VECTOR SLICE SAMPLING](VECTOR_SLICE_SAMPLING.md); [SAMPLING RESEARCH](SAMPLING_RESEARCH.md).

<a id="sam-03"></a>

### SAM-03: Other slice/transport and QMC candidates

**Source:** Senn et al., [multiproposal elliptical slice sampling](https://arxiv.org/html/2602.22358v1) (2026); Marco and Tokdar, [adaptive generalized elliptical slice sampling](https://arxiv.org/html/2605.21659v3) (2026); Schär et al., [parallel affine transformation tuning](https://proceedings.mlr.press/v235/schar24a.html) (2024); Cabezas and Nemeth, [Transport Elliptical Slice Sampling](https://proceedings.mlr.press/v206/cabezas23a.html) (2023). Ho et al., [QMC with one categorical variable](https://arxiv.org/abs/2506.16582); Chen et al., [adaptive importance sampling with recycling via QMC](https://arxiv.org/abs/2505.05037); Du and He, [RQMC self-normalized importance sampling](https://arxiv.org/abs/2511.10599).

**Role:** Research-only / candidate. The screening includes independently written research kernels and unimplemented candidates. It is not a production implementation of every listed method; the per-method assessment governs. Published convergence/error results require their original assumptions.

**Code:** [SamplingResearchExample.scala (research kernels, not a universal backend)](../FigaroExamples/src/main/scala/com/cra/figaro/example/SamplingResearchExample.scala).

**Validation:** Historical experiments and limitations in the linked research guide; no new acceptance run for this index..

**Guide:** [SAMPLING RESEARCH](SAMPLING_RESEARCH.md); [SAMPLING BUDGET VALIDATION](SAMPLING_BUDGET_VALIDATION.md).

<a id="inf-01"></a>

### INF-01: Defensive importance mixtures

**Source:** Owen and Zhou, *Safe and Effective Importance Sampling* (2000). [Publisher/DOI](https://www.tandfonline.com/doi/abs/10.1080/01621459.2000.10473909).

**Role:** Implemented / adapted. Support-preserving mixture idea in frozen proposal importance sampling. Figaro does not thereby implement the paper's control-variate estimator or inherit its stronger variance claims. Pilot fitting and production sampling are separate.

**Code:** [VectorImportance.scala](../Figaro/src/main/scala/com/cra/figaro/algorithm/sampling/VectorImportance.scala).

**Validation:** [VectorImportanceTest](../Figaro/src/test/scala/com/cra/figaro/test/modernization/VectorImportanceTest.scala); [VectorImportanceAcceptance](../Figaro/src/test/scala/com/cra/figaro/test/modernization/VectorImportanceAcceptance.scala).

**Guide:** [DEFENSIVE IMPORTANCE RESEARCH](DEFENSIVE_IMPORTANCE_RESEARCH.md); [VECTOR IMPORTANCE](VECTOR_IMPORTANCE.md); [IMPORTANCE CALIBRATION](IMPORTANCE_CALIBRATION.md).

<a id="inf-02"></a>

### INF-02: Adaptive mixtures and EM

**Source:** Cappé, Douc, Guillin, Marin and Robert, *Adaptive Importance Sampling in General Mixture Classes* (2008). [Paper](https://arxiv.org/abs/0710.4242). [scikit-learn Gaussian-mixture EM reference](https://scikit-learn.org/stable/modules/generated/sklearn.mixture.GaussianMixture.html) is an implementation comparison, not the originating paper.

**Role:** Implemented / adapted; validation reference. Bounded Gaussian-mixture pilot fitting followed by a frozen defensive proposal. Not full sequential adaptive-mixture inference and not copied scikit-learn software.

**Code:** [GaussianMixtureProposal.scala](../Figaro/src/main/scala/com/cra/figaro/algorithm/sampling/GaussianMixtureProposal.scala).

**Validation:** [GaussianMixtureProposalTest](../Figaro/src/test/scala/com/cra/figaro/test/modernization/GaussianMixtureProposalTest.scala); [MixtureProposalStudy](../Figaro/src/test/scala/com/cra/figaro/test/modernization/MixtureProposalStudy.scala).

**Guide:** [MIXTURE PROPOSALS](MIXTURE_PROPOSALS.md); [GRAPH PROPOSALS](GRAPH_PROPOSALS.md).

<a id="inf-03"></a>

### INF-03: Safe adaptation and regularized alternatives

**Source:** Delyon and Portier, [safe adaptive importance sampling](https://arxiv.org/html/1903.08507v4) (2021); Korba and Portier, [regularized adaptive importance sampling](https://proceedings.mlr.press/v151/korba22a.html) (2022).

**Role:** Research comparison / not implemented as published. The frozen parametric proposal is not Delyon-Portier's sequential kernel-density algorithm. Figaro does not apply Korba-Portier regularized production weights or claim either paper's theorem for its frozen estimator.

**Code:** No matching production implementation of these published algorithms; compare [VectorImportance.scala](../Figaro/src/main/scala/com/cra/figaro/algorithm/sampling/VectorImportance.scala) for the deliberately narrower method..

**Validation:** See the existing pilot protocol and acceptance evidence; no theorem-transfer claim..

**Guide:** [DEFENSIVE IMPORTANCE RESEARCH](DEFENSIVE_IMPORTANCE_RESEARCH.md).

<a id="inf-04"></a>

### INF-04: Stable log weights and proposal ratios

**Source:** Blanchard, D. J. Higham and N. J. Higham, *Accurate Computation of the Log-Sum-Exp and Softmax Functions*, [author-hosted September 2019 manuscript](https://eprints.maths.manchester.ac.uk/2728/1/paper.pdf), section 4 and Algorithm 4.1. Maintained implementation comparison: [particles resampling module](https://github.com/nchopin/particles/blob/master/particles/resampling.py), inspected 2026-10-11. Formula comparison: [Stan manual, Metropolis acceptance](https://mc-stan.org/docs/reference-manual/mcmc.html#metropolis-accept-step).

**Role:** Shipped in Figaro 7.0.0. Shifted exponentiation and log-sum-exp for particle normalization/evidence; explicit log proposal/model ratios for MH and annealing. The Stan reference is a formula comparison, not an implementation of HMC. No external source code was copied. Zero viable particles require a separate policy and cannot be repaired by numerical normalization; strict failure is the approved default, with opt-in legacy fallback.

**Code:** [ParticleFilter.scala](../Figaro/src/main/scala/com/cra/figaro/algorithm/filtering/ParticleFilter.scala); [ParParticleFilter.scala](../Figaro/src/main/scala/com/cra/figaro/algorithm/filtering/ParParticleFilter.scala); [Element.scala](../Figaro/src/main/scala/com/cra/figaro/language/Element.scala); [HasLogPriorProposal.scala](../Figaro/src/main/scala/com/cra/figaro/language/HasLogPriorProposal.scala); [MetropolisHastings.scala](../Figaro/src/main/scala/com/cra/figaro/algorithm/sampling/MetropolisHastings.scala).

**Validation:** [InferenceIntegrationRegressionTest](../Figaro/src/test/scala/com/cra/figaro/test/modernization/InferenceIntegrationRegressionTest.scala) includes analytic underflow/overflow controls, sequential/parallel evidence, explicit zero-weight behavior and legacy proposal compatibility. See the execution record for local/remote results and completed release gates.

**Guide:** [INFERENCE INTEGRATION](INFERENCE_INTEGRATION.md); [INFERENCE INTEGRATION STATUS](INFERENCE_INTEGRATION_STATUS.md); [CAPABILITY EXPANSION](CAPABILITY_EXPANSION.md).

Released in 7.1.0: [HasLogRandomnessProposal.scala](../Figaro/src/main/scala/com/cra/figaro/language/HasLogRandomnessProposal.scala)
adds an explicit randomness-measure custom hook and ratio constructor. No third-party
proposal is automatically reinterpreted. [CapabilityExpansionTest](../Figaro/src/test/scala/com/cra/figaro/test/modernization/CapabilityExpansionTest.scala)
checks asymmetric extreme ratios and invalid inputs; this addition is not in 7.0.0.

<a id="inf-05"></a>

### INF-05: Distribution/inference integration and supported coordinates

**Source:** [Stan constraint transforms](https://mc-stan.org/docs/reference-manual/transforms.html), lower/bounded scalar and covariance/correlation sections; [Stan latent discrete parameters](https://mc-stan.org/docs/stan-users-guide/latent-discrete.html), finite marginalization; Wingate, Stuhlmüller and Goodman (2011), [Lightweight Implementations of Probabilistic Programming Languages via Transformational Compilation](https://proceedings.mlr.press/v15/wingate11a/wingate11a.pdf), sections 2–2.3 and Algorithm 2 inspected, not a full-paper review. Numerical boundary comparisons: official [SciPy GEV](https://docs.scipy.org/doc/scipy/reference/generated/scipy.stats.genextreme.html), [GPD](https://docs.scipy.org/doc/scipy/reference/generated/scipy.stats.genpareto.html), [Wishart](https://docs.scipy.org/doc/scipy/reference/generated/scipy.stats.wishart.html), and [Commons Math hypergeometric API](https://commons.apache.org/proper/commons-math/javadocs/api-3.6.1/org/apache/commons/math3/distribution/HypergeometricDistribution.html). Inspected 2026-10-11; attempted Commons Math source URLs were unavailable, so no claim of source audit for that backend.

**Role:** Independent implementation shipped in Figaro 7.0.0. Existing Figaro MH/cache and finite Select/factor backends are reused. The lightweight-PPL paper motivates explicit likelihood and trace accounting; only the bounded observed-atomic/one-child-Chain surface is supported, not arbitrary trace transformations. Fixed transformed-coordinate walks use analytic Jacobians, not Stan's HMC implementation. Mathematical formulas are independently implemented; no external code copied. Numerical range expansion retains explicit machine-representation/work limits. Matrix boundary warnings are counterevidence to unrestricted support claims.

**Code:** [MultiChainMetropolisHastings.scala](../Figaro/src/main/scala/com/cra/figaro/algorithm/sampling/parallel/MultiChainMetropolisHastings.scala); [FiniteCountInference.scala](../Figaro/src/main/scala/com/cra/figaro/library/atomic/discrete/FiniteCountInference.scala); [SupportAwareProposal.scala](../Figaro/src/main/scala/com/cra/figaro/algorithm/sampling/SupportAwareProposal.scala); [ExtremeValueDistributions.scala](../Figaro/src/main/scala/com/cra/figaro/library/atomic/continuous/ExtremeValueDistributions.scala); [CountDistributions.scala](../Figaro/src/main/scala/com/cra/figaro/library/atomic/discrete/CountDistributions.scala); [Wishart.scala](../Figaro/src/main/scala/com/cra/figaro/library/atomic/continuous/Wishart.scala); [InverseWishart.scala](../Figaro/src/main/scala/com/cra/figaro/library/atomic/continuous/InverseWishart.scala).

**Validation:** [MultiChainObservationTest](../Figaro/src/test/scala/com/cra/figaro/test/modernization/MultiChainObservationTest.scala), [FiniteCountInferenceTest](../Figaro/src/test/scala/com/cra/figaro/test/modernization/FiniteCountInferenceTest.scala), [SupportAwareProposalTest](../Figaro/src/test/scala/com/cra/figaro/test/modernization/SupportAwareProposalTest.scala), [SupportAwareProposalStudy](../Figaro/src/test/scala/com/cra/figaro/test/modernization/SupportAwareProposalStudy.scala), [NumericalRangeIntegrationTest](../Figaro/src/test/scala/com/cra/figaro/test/modernization/NumericalRangeIntegrationTest.scala). Controls include enumerated posteriors, finite-difference Jacobian determinants, exact BigInt combinatorics, small-shape series and matrix scalar reductions. Executed outcomes and remaining gates are recorded separately; a source citation does not validate all regimes.

**Guide:** [INFERENCE INTEGRATION](INFERENCE_INTEGRATION.md); [INFERENCE INTEGRATION STATUS](INFERENCE_INTEGRATION_STATUS.md); [CAPABILITY EXPANSION](CAPABILITY_EXPANSION.md); [CAPABILITY EXPANSION STATUS](CAPABILITY_EXPANSION_STATUS.md).

Released in 7.1.0: nested owned likelihood spines, explicit scalar randomness
adapters, tiny-shape stable ratios and direct Cholesky-entry densities reuse the
listed code. Multinomial aggregation uses the probability-generating-function
category reduction; [University of Washington STAT 512 notes](https://faculty.washington.edu/yenchic/20A_stat512/Lec7_Multinomial.html)
are an inspected teaching/formula reference, not a copied implementation.
[CapabilityExpansionTest](../Figaro/src/test/scala/com/cra/figaro/test/modernization/CapabilityExpansionTest.scala)
provides small exhaustive controls; [CapabilityNumericalStudy](../Figaro/src/test/scala/com/cra/figaro/test/modernization/CapabilityNumericalStudy.scala)
and [independent high-precision comparison](../tools/capability_reference.py) compare
actual library output, including rounded-singular Gram cases. Expanded support is
released in 7.1.0 without changing the immutable 7.0.0 artifacts.

<a id="inf-06"></a>

### INF-06: Discarded warm-up scalar scaling

**Source:** Garthwaite, Fan and Sisson, *Adaptive Optimal Scaling of Metropolis-Hastings Algorithms Using the Robbins-Monro Process*, [2010 preprint](https://arxiv.org/pdf/1006.3690), section 2 inspected. [Stan warm-up and fixed sampling](https://mc-stan.org/docs/cmdstan-guide/mcmc-intro.html) is a maintained implementation-policy comparison, not the source of random-walk formulas. Inspected 2026-10-12; publisher full-text retrieval failed, so no claim of reading the final journal version.

**Role:** Independent adaptation shipped in 7.1.0, not the paper's optimal-steplength algorithm or Stan HMC. Bounded log-scale update uses `(iteration+10)^(-0.6)` times acceptance deviation, then freezes. Acceptance targeting is not a precision or efficiency guarantee. Only one latent scalar is supported initially; existing public constructor contracts remain intact.

**Code:** [SupportAwareProposal.scala](../Figaro/src/main/scala/com/cra/figaro/algorithm/sampling/SupportAwareProposal.scala), `tune` and `Tuning`; [MultiChainMetropolisHastings.scala](../Figaro/src/main/scala/com/cra/figaro/algorithm/sampling/parallel/MultiChainMetropolisHastings.scala), `runTuned`.

**Validation:** [CapabilityExpansionTest](../Figaro/src/test/scala/com/cra/figaro/test/modernization/CapabilityExpansionTest.scala): conjugate posterior, frozen report, worker replay, explicit rejection of extra latents, legacy/custom coordinate maps. No universal mixing result or benchmark superiority claimed.

**Guide:** [CAPABILITY EXPANSION](CAPABILITY_EXPANSION.md); [CAPABILITY EXPANSION STATUS](CAPABILITY_EXPANSION_STATUS.md).

<a id="inf-07"></a>

### INF-07: Bounded-work indicator filtering and diagnostics

**Source:** Jasra, Lee, Yau and Zhang, [The Alive Particle Filter](https://arxiv.org/abs/1304.0151) (preprint; historical background, detailed review pending). Sherlock, Golightly and Lee, [Robust, partially alive particle Metropolis-Hastings via The Frankenfilter](https://arxiv.org/html/2601.23173v1) (2026 preprint v1), sections 1–2, Algorithms 2–5 and Appendix A's exchangeability/induction argument inspected 2026-10-12. Doucet and Johansen, [A Tutorial on Particle Filtering and Smoothing](https://www.stats.ox.ac.uk/~doucet/doucet_johansen_tutorialPF.pdf) (2008 revision), pre-resampling ESS and distinction between weighting and resampling.

**Role:** Independent implementation of the binary-weight specialization shipped in 7.1.0. Retains the corrected variable-size success population and samples ancestors directly; no generic mutable-snapshot retry or general-weight success rule. Figaro adds owned RNG, immutable-state callback contracts, fresh fixed-graph adapters, explicit terminal failure and engineering diagnostics. ESS thresholds are warnings, not precision guarantees. The source theorem depends on valid callbacks and model assumptions; finite local controls do not certify arbitrary programs. No paper performance figure is a Figaro measurement and no external code was copied.

**Code:** [BoundedParticleFilter.scala](../Figaro/src/main/scala/com/cra/figaro/algorithm/filtering/BoundedParticleFilter.scala); [ParticleFilterHealth.scala](../Figaro/src/main/scala/com/cra/figaro/algorithm/filtering/ParticleFilterHealth.scala); [ParticleFilter.scala](../Figaro/src/main/scala/com/cra/figaro/algorithm/filtering/ParticleFilter.scala). Existing strict failure and opt-in legacy policy are unchanged.

**Validation:** [BoundedParticleFilterTest](../Figaro/src/test/scala/com/cra/figaro/test/modernization/BoundedParticleFilterTest.scala): exhaustive one/two-step finite controls, exact-cap stopping, zero evidence, work bounds, RNG/lifecycle/ownership and unsupported evidence refusal. [BoundedParticleFilterStudy](../Figaro/src/test/scala/com/cra/figaro/test/modernization/BoundedParticleFilterStudy.scala): separate-pilot, near-matched-work synthetic HMM comparison with all zero outcomes retained. Execution status is recorded separately; no universal accuracy or performance guarantee.

**Guide:** [BOUNDED PARTICLE FILTER](BOUNDED_PARTICLE_FILTER.md); [CAPABILITY EXPANSION](CAPABILITY_EXPANSION.md); [CAPABILITY EXPANSION STATUS](CAPABILITY_EXPANSION_STATUS.md).

<a id="inf-08"></a>

### INF-08: Fixed-pilot binomial budget advice

**Source:** [NIST exact binomial confidence limits](https://itl.nist.gov/div898/software/dataplot/refman2/auxillar/exacbino.htm), inspected 2026-10-12: binomial-CDF inversion and small-count limitations of symmetric normal intervals. This is an official method reference, not a full-text review of the original Clopper–Pearson paper. [Commons Math 3.6.1 BetaDistribution](https://commons.apache.org/proper/commons-math/javadocs/api-3.6.1/org/apache/commons/math3/distribution/BetaDistribution.html) and [BinomialDistribution](https://commons.apache.org/proper/commons-math/javadocs/api-3.6.1/org/apache/commons/math3/distribution/BinomialDistribution.html) document the reused numerical backend. INF-07 supplies the target/excluded-final-success filtering context.

**Role:** Independent advisory implementation in 7.2.0-SNAPSHOT, not in 7.1.0. Conservative fixed-size IID pilot intervals feed a single-step binomial target-miss calculation. Expected capped waiting time is an elementary independent derivation, not the Frankenfilter paper's general partial-observation variance theorem. No posterior precision, full-sequence guarantee, optional-stopping pilot validity or automatic recovery is claimed. Existing Commons Math dependency reused; no external source code copied.

**Code:** [BoundedParticleBudget.scala](../Figaro/src/main/scala/com/cra/figaro/algorithm/filtering/BoundedParticleBudget.scala).

**Validation:** [BoundedParticleBudgetTest](../Figaro/src/test/scala/com/cra/figaro/test/modernization/BoundedParticleBudgetTest.scala) has exhaustive small-count coverage and stopping-tree controls, explicit refusals, fixed pilot cost and RNG/error semantics. [BoundedParticleBudgetStudy](../Figaro/src/test/scala/com/cra/figaro/test/modernization/BoundedParticleBudgetStudy.scala) emits actual numerical output for the independent [80-digit oracle](../tools/budget_reference.py). Execution status is tracked separately, not implied by this bibliography.

**Guide:** [PARTICLE BUDGET ADVISER](PARTICLE_BUDGET_ADVISER.md); [BOUNDED PARTICLE FILTER](BOUNDED_PARTICLE_FILTER.md).

<a id="inf-09"></a>

### INF-09: Weighted bounded bootstrap filtering

**Source:** Sherlock, Golightly and Lee, [Frankenfilter preprint v2](https://arxiv.org/html/2601.23173v2), section 2, Algorithms 3/4 and stopping/ancestor correction inspected 2026-10-12. [Author reference repository](https://github.com/AndyGolightly/Frankenfilter) inspected for reuse feasibility: R/C++ research applications, no declared repository license; no code/assets copied. INF-04 supplies the log-weight numerical context. Gaussian conditioning is an independent analytic validation, not a copied Kalman implementation.

**Role:** Independent development implementation in 7.2.0-SNAPSHOT, not shipped in 7.1.0. Original bootstrap prior/transition specialization, immutable states, fixed per-candidate scores restricted to [0,1], explicit work floor/cap and corrected retained population. INF-10 maps the subsequent guided-proposal and split-pilot extension. Figaro adds thread confinement, owned RNG, failure semantics and numerical refusal. No automatic ESS stopping, posterior-precision theorem or paper-to-Figaro speedup transfer.

**Code:** [WeightedBoundedParticleFilter.scala](../Figaro/src/main/scala/com/cra/figaro/algorithm/filtering/WeightedBoundedParticleFilter.scala).

**Validation:** [WeightedBoundedParticleFilterTest](../Figaro/src/test/scala/com/cra/figaro/test/modernization/WeightedBoundedParticleFilterTest.scala), exhaustive weighted two-step trees and lifecycle/numerical controls; [WeightedBoundedParticleStudy](../Figaro/src/test/scala/com/cra/figaro/test/modernization/WeightedBoundedParticleStudy.scala), predeclared synthetic calibration; [independent rational Gaussian-conditioning oracle](../tools/weighted_filter_reference.py). Actual execution status is recorded separately.

**Guide:** [WEIGHTED BOUNDED FILTER](WEIGHTED_BOUNDED_FILTER.md); [WEIGHTED FILTER STATUS](WEIGHTED_FILTER_STATUS.md).

<a id="inf-10"></a>

### INF-10: Frozen guided proposals and split-pilot work calibration

**Source:** Sherlock, Golightly and Lee, [Frankenfilter v2](https://arxiv.org/html/2601.23173v2), sections 2/3: importance-corrected bridges, retained ancestors, and tuning limitations. Hoeffding (1963), *Probability Inequalities for Sums of Bounded Random Variables*, [DOI](https://doi.org/10.1080/01621459.1963.10500830), [accessible scan](https://www.cs.rpi.edu/academics/courses/spring06/random/hoefding.pdf), section 2, Theorem 1 equation (2.3), visually inspected. Maintained implementation comparison: [particles GuidedPF](https://github.com/nchopin/particles/blob/master/particles/state_space_models.py), initial/transition density correction and distinction from auxiliary filtering; [license](https://github.com/nchopin/particles/blob/master/LICENSE) inspected. Inspection date 2026-10-12. INF-01 supplies defensive-mixture context; no external implementation code copied. Branchini and Elvira's [optimized auxiliary filters](https://proceedings.mlr.press/v161/branchini21a.html) were screened at abstract level only, not implemented or claimed as a full-text review.

**Role:** Independent development extension in 7.2.0-SNAPSHOT. Guided conditional proposals with unchanged weighted ancestor selection, optional full-density defensive mixtures, and existing scalar Gaussian kernels. Figaro-specific split-pilot scale quantile plus one-sided Hoeffding work advice; not an implementation of the paper's PMMH tuning recipe or a new precision theorem. Independent evaluation is required because the scale is fitted. Unsupported/inconclusive advice uses the remaining fixed budget, not selective retries. Paper guarantees require their original assumptions; score-bound coverage is not posterior accuracy. Subsequent auxiliary look-ahead support is mapped in INF-11; online production adaptation remains unimplemented.

**Code:** [WeightedParticleProposal.scala](../Figaro/src/main/scala/com/cra/figaro/algorithm/filtering/WeightedParticleProposal.scala); [WeightedParticleCalibration.scala](../Figaro/src/main/scala/com/cra/figaro/algorithm/filtering/WeightedParticleCalibration.scala); [WeightedBoundedParticleFilter.scala](../Figaro/src/main/scala/com/cra/figaro/algorithm/filtering/WeightedBoundedParticleFilter.scala).

**Validation:** [CalibratedGuidedFilterTest](../Figaro/src/test/scala/com/cra/figaro/test/modernization/CalibratedGuidedFilterTest.scala), finite non-prior stopping trees, bounded-sum controls and lifecycle/numerical checks; [CalibratedGuidedFilterStudy](../Figaro/src/test/scala/com/cra/figaro/test/modernization/CalibratedGuidedFilterStudy.scala), predeclared four-mode Gaussian study with inclusive pilot cost; [independent joint-conditioning check](../tools/guided_filter_reference.py). Execution status is recorded separately, without transferring older branch CI results.

**Guide:** [CALIBRATED GUIDED FILTER](CALIBRATED_GUIDED_FILTER.md); [CALIBRATED GUIDED STATUS](CALIBRATED_GUIDED_STATUS.md).

<a id="inf-11"></a>

### INF-11: Auxiliary ancestor selection and Gaussian observation guidance

**Source:** Johansen and Doucet, *A Note on Auxiliary Particle Filters* (2008), [author manuscript](https://wrap.warwick.ac.uk/id/eprint/37283/1/WRAP_Johansen_JD08.pdf), sections 2.2-2.4: predictive ancestor weights, equation (5), tail guidance and lack of universal variance ordering. Relevant sections inspected, not a claim of full-paper proof review. Sherlock, Golightly and Lee, [Frankenfilter v2](https://arxiv.org/html/2601.23173v2), section 2.2, Algorithm 3 and conditional IID stopping argument. Maintained implementation comparisons: particles [core weight reset](https://github.com/nchopin/particles/blob/master/particles/core.py), [AuxiliaryPF](https://github.com/nchopin/particles/blob/master/particles/state_space_models.py), [linear-Gaussian conditional/predictive helpers](https://github.com/nchopin/particles/blob/master/particles/kalman.py), [MIT license](https://github.com/nchopin/particles/blob/master/LICENSE). Inspected 2026-10-21. No external code or assets copied; existing Figaro kernels and Commons Math reused.

**Role:** Independent development implementation in 7.2.0-SNAPSHOT. One-stage auxiliary sampling with full parent/child importance corrections and an optional defensive mixture of ancestor probabilities. Combining this with bounded random population sizes is a Figaro extension on the augmented parent/child space, not automatic transfer of the fixed-population APF theorem. Preparation is frozen before split pilots and production. Scalar predictive density and multivariate linear-Gaussian conditioning use existing kernels, scaled Cholesky solves and Joseph covariance algebra. No automatic nonlinear fitting, optimal mixture-weight solver, lost-history recovery or universal efficiency claim.

**Code:** [AuxiliaryParticleProposal.scala](../Figaro/src/main/scala/com/cra/figaro/algorithm/filtering/AuxiliaryParticleProposal.scala); [WeightedBoundedParticleFilter.scala](../Figaro/src/main/scala/com/cra/figaro/algorithm/filtering/WeightedBoundedParticleFilter.scala); [WeightedParticleProposal.scala](../Figaro/src/main/scala/com/cra/figaro/algorithm/filtering/WeightedParticleProposal.scala).

**Validation:** [AuxiliaryParticleFilterTest](../Figaro/src/test/scala/com/cra/figaro/test/modernization/AuxiliaryParticleFilterTest.scala), exhaustive two-step stopping controls, Gaussian identities, support/numerical/lifecycle checks; [AuxiliaryParticleFilterStudy](../Figaro/src/test/scala/com/cra/figaro/test/modernization/AuxiliaryParticleFilterStudy.scala), all-seed five-mode Gaussian/nonlinear-finite comparisons; [independent oracle](../tools/auxiliary_filter_reference.py), rational joint conditioning and full finite-path summation. Execution status, costs and limitations are recorded in the guide/checklist; synthetic benchmarks are not application acceptance.

**Guide:** [AUXILIARY PARTICLE FILTER](AUXILIARY_PARTICLE_FILTER.md); [AUXILIARY FILTER STATUS](AUXILIARY_FILTER_STATUS.md).

<a id="inf-12"></a>

### INF-12: Continuous nonlinear and multimodal filtering benchmarks

**Source:** Gordon, Salmond and Smith, *Novel approach to nonlinear/non-Gaussian Bayesian state estimation* (1993), [accessible paper](https://people.bordeaux.inria.fr/pierre.delmoral/gordon-salmond-smith-1993.pdf), section 4.1/equations 9-10 and discussion of modal posteriors inspected. Maintained implementation comparison: [particles Gordon_etal](https://github.com/nchopin/particles/blob/master/particles/state_space_models.py), initial law and transition-time convention; [MIT license](https://github.com/nchopin/particles/blob/master/LICENSE). Branchini and Elvira, [Optimized auxiliary particle filters](https://proceedings.mlr.press/v161/branchini21a/branchini21a.pdf) (2021), Figure 1/section 5 benchmark discussion; their optimized solver and experiments are not implemented. Johansen-Doucet's tail/variance caution is mapped in INF-11. [SciPy composite Simpson documentation](https://docs.scipy.org/doc/scipy/reference/generated/scipy.integrate.simpson.html) is a numerical-rule reference, not a runtime dependency. Inspected 2026-10-21; the initial Notre Dame mirror failed, then the Inria copy was read. No claim of full-paper proof review.

**Role:** Benchmark-only validation for 7.2.0, not a new inference API. Literature-derived scalar growth dynamics with explicit initial/data conventions, a sharper-observation variant and an independently constructed symmetric persistent-mode stress model. Frozen benchmark-local two-root guidance exercises the existing public proposal/auxiliary APIs. Independent deterministic grid recursion uses NumPy, not Figaro samples. Reference convergence is empirical numerical evidence, not a rigorous quadrature bound or application acceptance. No external source code or dataset copied; no automatic nonlinear fitting or optimized APF is claimed.

**Code:** [ContinuousFilterBenchmark.scala](../Figaro/src/test/scala/com/cra/figaro/test/modernization/ContinuousFilterBenchmark.scala); baseline runtime algorithms mapped in INF-09/INF-10/INF-11; subsequent numerical repair mapped in INF-13. The benchmark harness remains unchanged for the repair comparison.

**Validation:** [ContinuousFilterBenchmarkTest](../Figaro/src/test/scala/com/cra/figaro/test/modernization/ContinuousFilterBenchmarkTest.scala), normalized guidance, symmetric modes and RNG replay; [independent reference and all-outcome analysis](../tools/continuous_filter_reference.py), domain/resolution convergence, Gaussian recursion and mixture controls. The guide records the frozen protocol, failed reference revision and actual execution status. Numerical refusals are not zero-evidence outcomes or excluded seeds.

**Guide:** [CONTINUOUS FILTER BENCHMARKS](CONTINUOUS_FILTER_BENCHMARKS.md).

<a id="inf-13"></a>

### INF-13: Retained log weights and tree-based multinomial ancestors

**Source:** Lee and Whiteley, [Forest resampling for distributed sequential Monte Carlo](https://arxiv.org/html/1406.6010), 2014 preprint v1, section 4.4 Algorithm 4 and Proposition 6 inspected 2026-10-21: recursive categorical sampling by subtree mass. INF-04 supplies shifted normalization/log-sum-exp. Maintained comparisons: [particles resampling](https://github.com/nchopin/particles/blob/master/particles/resampling.py) retains log weights but resamples ordinary weights; [Commons RNG GuideTableDiscreteSampler](https://commons.apache.org/proper/commons-rng/commons-rng-sampling/jacoco/org.apache.commons.rng.sampling.distribution/GuideTableDiscreteSampler.java.html) accepts ordinary probabilities and builds cumulative tables. Neither directly supplies the required log-input ancestor contract. No external code copied or additional dependency introduced.

**Role:** Independent development repair, not an implementation of distributed/adaptive forest resampling. One balanced binary tree preserves independent multinomial draws required by the bounded-work construction. Figaro adapts subtree aggregation and smaller-child decisions to logs and retains log weights through defensive-mixture correction. The log-uniform construction is an elementary independent derivation: P(K=k)=2^-k, U=2^-K(1+V), with independent uniform mantissa V; leading-zero words extend K. Floating-point log arithmetic and PRNG limitations remain; no exact-arithmetic theorem, certified error bound, missing-mode guarantee or likelihood clipping is claimed. Ordinary output weights/expectations can still lose tiny contributions; the retained-log accessor exposes their distinction from actual zero support.

**Code:** [LogParticleWeights.scala](../Figaro/src/main/scala/com/cra/figaro/algorithm/filtering/LogParticleWeights.scala); [WeightedBoundedParticleFilter.scala](../Figaro/src/main/scala/com/cra/figaro/algorithm/filtering/WeightedBoundedParticleFilter.scala); [AuxiliaryParticleProposal.scala](../Figaro/src/main/scala/com/cra/figaro/algorithm/filtering/AuxiliaryParticleProposal.scala).

**Validation:** [LogParticleWeightsTest](../Figaro/src/test/scala/com/cra/figaro/test/modernization/LogParticleWeightsTest.scala), forced rare branches below ordinary underflow, scripted exponent extension, categorical frequency checks, log-mixture recovery and real filter lifecycle; existing exhaustive finite/Gaussian tests remain. [ContinuousFilterBenchmarkTest](../Figaro/src/test/scala/com/cra/figaro/test/modernization/ContinuousFilterBenchmarkTest.scala) replaces archived refusal reproductions with explicit retention/selection assertions. INF-12's identical benchmark protocol, fixtures, seeds and acceptance checks assess the repair; execution results are recorded separately, not implied by these citations.

**Guide:** [WEIGHTED BOUNDED FILTER](WEIGHTED_BOUNDED_FILTER.md); [AUXILIARY PARTICLE FILTER](AUXILIARY_PARTICLE_FILTER.md); [CONTINUOUS FILTER BENCHMARKS](CONTINUOUS_FILTER_BENCHMARKS.md).

<a id="dia-01"></a>

### DIA-01: Multi-chain convergence and ESS

**Source:** [Stan reference manual: posterior analysis](https://mc-stan.org/docs/reference-manual/analysis.html) and [posterior implementation](https://github.com/stan-dev/posterior/blob/master/R/convergence.R): rank-normalized/folded split R-hat and Geyer initial-positive/monotone autocorrelation sequences.

**Role:** Implemented / adapted; implementation reference. The maintained reference is used for the diagnostic formulas, not as a new paper citation. Figaro caps ESS at the split draw count and differs from posterior's antithetic treatment. R-hat/ESS do not guarantee discovery of unknown modes.

**Code:** [McmcDiagnostics.scala](../Figaro/src/main/scala/com/cra/figaro/algorithm/sampling/parallel/McmcDiagnostics.scala).

**Validation:** [McmcDiagnosticsRegressionTest](../Figaro/src/test/scala/com/cra/figaro/test/modernization/McmcDiagnosticsRegressionTest.scala); [McmcReliabilityRegressionTest](../Figaro/src/test/scala/com/cra/figaro/test/modernization/McmcReliabilityRegressionTest.scala).

**Guide:** [MULTI CHAIN MCMC](MULTI_CHAIN_MCMC.md); [MCMC RELIABILITY](MCMC_RELIABILITY.md).

<a id="dia-02"></a>

### DIA-02: Pareto importance-weight diagnostics

**Source:** Vehtari et al., *Pareto Smoothed Importance Sampling* (2024). [JMLR paper](https://www.jmlr.org/papers/v25/19-556.html); [loo Pareto-k guidance](https://mc-stan.org/loo/reference/pareto-k-diagnostic.html).

**Role:** Diagnostic adaptation / not full PSIS. Tail-shape warning conventions and diagnostic fitting. No smoothed weights, PSIS ESS, PSIS MCSE or PSIS interval-coverage guarantee. A large finite-sample k is a warning, not proof that population moments do not exist.

**Code:** [InferenceHealth.scala](../Figaro/src/main/scala/com/cra/figaro/algorithm/sampling/InferenceHealth.scala).

**Validation:** [InferenceHealthTest](../Figaro/src/test/scala/com/cra/figaro/test/modernization/InferenceHealthTest.scala); [InferenceHealthStudy](../Figaro/src/test/scala/com/cra/figaro/test/modernization/InferenceHealthStudy.scala).

**Guide:** [INFERENCE HEALTH](INFERENCE_HEALTH.md); [STATISTICAL VALIDATION](STATISTICAL_VALIDATION.md).

<a id="stp-01"></a>

### STP-01: Truncated sequential tests

**Source:** Tantaratana and Poor, *Asymptotic Efficiencies of Truncated Sequential Tests* (1982), pp. 911–923: [DOI](https://doi.org/10.1109/TIT.1982.1056578). Blostein and Huang, *Detecting Small, Moving Objects in Image Sequences Using Sequential Hypothesis Testing* (1991), equations (12)–(15): [DOI](https://doi.org/10.1109/78.134399).

**Role:** Implemented / specialized. Equal-variance Gaussian truncated SPRT parameter design and sequential updates. This is not a generic MCMC convergence test; feeding correlated estimated divergences into it does not preserve nominal error rates automatically.

**Code:** [TruncatedSprt.scala](../Figaro/src/main/scala/com/cra/figaro/algorithm/sampling/parallel/TruncatedSprt.scala).

**Validation:** [StoppingCriteriaRegressionTest](../Figaro/src/test/scala/com/cra/figaro/test/modernization/StoppingCriteriaRegressionTest.scala).

**Guide:** [STOPPING CRITERIA](STOPPING_CRITERIA.md); [STOPPING VALIDATION](STOPPING_VALIDATION.md).

<a id="stp-02"></a>

### STP-02: Relative fixed-width stopping

**Source:** Flegal and Gong, *Relative fixed-width stopping rules for Markov chain Monte Carlo simulations*. [Paper/preprint](https://arxiv.org/abs/1303.0238).

**Role:** Implemented / inspired adaptation. MCMC precision checks combine batch-means/raw-mean ESS-based MCSE with mixing safeguards. The max-of-two safeguard is a Figaro engineering choice; the reference's asymptotic results do not prove finite-run coverage for arbitrary Figaro models.

**Code:** [McmcPrecision.scala](../Figaro/src/main/scala/com/cra/figaro/algorithm/sampling/parallel/McmcPrecision.scala).

**Validation:** [StoppingCriteriaRegressionTest](../Figaro/src/test/scala/com/cra/figaro/test/modernization/StoppingCriteriaRegressionTest.scala); [McmcReliabilityRegressionTest](../Figaro/src/test/scala/com/cra/figaro/test/modernization/McmcReliabilityRegressionTest.scala).

**Guide:** [STOPPING CRITERIA](STOPPING_CRITERIA.md); [MCMC RELIABILITY](MCMC_RELIABILITY.md).

<a id="stp-03"></a>

### STP-03: Bounded IID concentration

**Source:** Hoeffding, *Probability Inequalities for Sums of Bounded Random Variables* (1963). [Repository record](https://repository.lib.ncsu.edu/items/3f47dae6-2e27-4a2c-9935-54aa9390ffaf).

**Role:** Implemented / derived spending construction. Bounded IID confidence bounds with an explicit error-spending schedule. Declared-region checks concern an explicit inventory, not guaranteed discovery of undeclared modes. Weighted, dependent or unbounded samples do not qualify merely because the API accepts a callback.

**Code:** [BoundedIidPrecision.scala](../Figaro/src/main/scala/com/cra/figaro/algorithm/sampling/BoundedIidPrecision.scala); [DeclaredRegionCoverage.scala](../Figaro/src/main/scala/com/cra/figaro/algorithm/sampling/DeclaredRegionCoverage.scala).

**Validation:** [BoundedIidPrecisionTest](../Figaro/src/test/scala/com/cra/figaro/test/modernization/BoundedIidPrecisionTest.scala).

**Guide:** [BOUNDED IID RELIABILITY](BOUNDED_IID_RELIABILITY.md).

<a id="stp-04"></a>

### STP-04: Predictable empirical-Bernstein confidence sequences

**Source:** Waudby-Smith and Ramdas, *Estimating means of bounded random variables by betting*, inspected revision v7, Theorem 2. [Paper](https://arxiv.org/html/2010.09686v7).

**Role:** Implemented / specified construction. Predictable plug-in exponential empirical-Bernstein construction, not inversion of the hedged capital process. The predictable-weighted center differs from the ordinary sample mean; Figaro reports both and retains bounded IID assumptions.

**Code:** [EmpiricalBernsteinPrecision.scala](../Figaro/src/main/scala/com/cra/figaro/algorithm/sampling/EmpiricalBernsteinPrecision.scala).

**Validation:** [EmpiricalBernsteinPrecisionTest](../Figaro/src/test/scala/com/cra/figaro/test/modernization/EmpiricalBernsteinPrecisionTest.scala).

**Guide:** [ADAPTIVE BOUNDED PRECISION](ADAPTIVE_BOUNDED_PRECISION.md).

<a id="stp-05"></a>

### STP-05: Broader confidence-sequence candidates

**Source:** Howard et al., [time-uniform confidence sequences](https://arxiv.org/abs/1810.08240) (2021); Shekhar and Ramdas, [near-optimal betting research](https://arxiv.org/abs/2310.01547) (2023); Chugg and Ramdas, [closed-form empirical-Bernstein research](https://arxiv.org/abs/2512.21300) (2025 preprint).

**Role:** Research context / candidate. Context for sharper or broader bounds, not three additional shipped stopping algorithms. The implemented construction is identified separately in STP-04; neither general time-varying means nor arbitrary MCMC is silently supported.

**Code:** No separate production implementation claimed for these candidates..

**Validation:** No local validation claimed for the newer candidates; see scoped research discussion..

**Guide:** [BOUNDED IID RELIABILITY](BOUNDED_IID_RELIABILITY.md); [ADAPTIVE BOUNDED PRECISION](ADAPTIVE_BOUNDED_PRECISION.md).

<a id="gvm-01"></a>

### GVM-01: Circular von Mises sampling

**Source:** Best and Fisher, *Efficient Simulation of the von Mises Distribution* (1979). [DOI](https://doi.org/10.2307/2346732).

**Role:** Implemented / numerical adaptation. Rejection sampler for the circular foundation with explicit concentration/numerical handling. SciPy is a parameterization/numerical reference, not the source of Figaro's implementation.

**Code:** [VonMisesDistribution.scala](../Figaro/src/main/scala/com/cra/figaro/library/atomic/continuous/VonMisesDistribution.scala); [VonMises.scala](../Figaro/src/main/scala/com/cra/figaro/library/atomic/continuous/VonMises.scala).

**Validation:** [VonMisesRegressionTest](../Figaro/src/test/scala/com/cra/figaro/test/modernization/VonMisesRegressionTest.scala).

**Guide:** [VON MISES](VON_MISES.md).

<a id="gvm-02"></a>

### GVM-02: Joint Gauss-von Mises law

**Source:** Horwood and Poore, *Gauss von Mises Distribution for Improved Uncertainty Realism in Space Situational Awareness* (2014). [DOI](https://doi.org/10.1137/130917296); [open 2012 precursor](https://amostech.com/TechnicalPapers/2012/Astrodynamics/HORWOOD.pdf) is related material, not a substitute for the final article.

**Role:** Implemented / fixed-law foundation; derived extensions. Joint Gaussian-linear/circular kernel and associated Figaro moments, score, KL and gradient helpers. The library's particular numerical contracts and extensions must be read in their guides; this mapping does not claim implementation of the paper's complete application workflow, report fusion, filtering or propagation.

**Code:** [GaussVonMisesDistribution.scala](../Figaro/src/main/scala/com/cra/figaro/library/atomic/continuous/GaussVonMisesDistribution.scala); [GaussVonMisesMoments.scala](../Figaro/src/main/scala/com/cra/figaro/library/atomic/continuous/GaussVonMisesMoments.scala); [GaussVonMisesStateGradient.scala](../Figaro/src/main/scala/com/cra/figaro/library/atomic/continuous/GaussVonMisesStateGradient.scala).

**Validation:** [GaussVonMisesRegressionTest](../Figaro/src/test/scala/com/cra/figaro/test/modernization/GaussVonMisesRegressionTest.scala); [GaussVonMisesMomentsTest](../Figaro/src/test/scala/com/cra/figaro/test/modernization/GaussVonMisesMomentsTest.scala); [GaussVonMisesDiagnosticsTest](../Figaro/src/test/scala/com/cra/figaro/test/modernization/GaussVonMisesDiagnosticsTest.scala); [GaussVonMisesGradientTest](../Figaro/src/test/scala/com/cra/figaro/test/modernization/GaussVonMisesGradientTest.scala).

**Guide:** [GAUSS VON MISES](GAUSS_VON_MISES.md); [GAUSS VON MISES PLAN](GAUSS_VON_MISES_PLAN.md); [GVM DIAGNOSTICS](GVM_DIAGNOSTICS.md).

<a id="gvm-03"></a>

### GVM-03: Bessel identities and derived overlap calculations

**Source:** NIST DLMF, [modified Bessel integral 10.32.1](https://dlmf.nist.gov/10.32.E1), [Fourier expansion 10.35.2](https://dlmf.nist.gov/10.35.E2). Nielsen (2022), [Chernoff/Bhattacharyya definitions, section 1.1](https://arxiv.org/html/2207.03745v2).

**Role:** Mathematical reference / independently derived extension. The angular-reduced Fourier/Gaussian comparison and separate scalar-positive alternative are Figaro derivations/implementations, not algorithms claimed to have been supplied in full by these references. Inspect refusal statuses and estimated-error limits.

**Code:** [GaussVonMisesBhattacharyya.scala](../Figaro/src/main/scala/com/cra/figaro/library/atomic/continuous/GaussVonMisesBhattacharyya.scala); [GaussVonMisesScalarBhattacharyya.scala](../Figaro/src/main/scala/com/cra/figaro/library/atomic/continuous/GaussVonMisesScalarBhattacharyya.scala).

**Validation:** [GaussVonMisesBhattacharyyaTest](../Figaro/src/test/scala/com/cra/figaro/test/modernization/GaussVonMisesBhattacharyyaTest.scala); [GaussVonMisesBhattacharyyaReliabilityTest](../Figaro/src/test/scala/com/cra/figaro/test/modernization/GaussVonMisesBhattacharyyaReliabilityTest.scala); [GaussVonMisesScalarBhattacharyyaTest](../Figaro/src/test/scala/com/cra/figaro/test/modernization/GaussVonMisesScalarBhattacharyyaTest.scala).

**Guide:** [GVM BHATTACHARYYA RESEARCH](GVM_BHATTACHARYYA_RESEARCH.md); [GVM BHATTACHARYYA POSITIVE RESEARCH](GVM_BHATTACHARYYA_POSITIVE_RESEARCH.md); [GVM BHATTACHARYYA](GVM_BHATTACHARYYA.md); [GVM SCALAR BHATTACHARYYA](GVM_SCALAR_BHATTACHARYYA.md).

<a id="gvm-04"></a>

### GVM-04: GVM mixture fitting and circular-regression context

**Source:** [Model-based clustering using a new mixture of circular regressions](https://arxiv.org/abs/2601.05345) (2026), together with the joint-law foundation in GVM-02.

**Role:** Independent specialization / research context. The recent paper is relevant EM/circular-regression literature, not the identical joint GVM law or copied software. Figaro's periodic resultant objective and coordinate-change algebra specialize its quadratic-phase law. Fitting is bounded to one linear coordinate plus one angle; fixed higher-dimensional kernels do not imply a higher-dimensional fitter.

**Code:** [GaussVonMisesMixture.scala](../Figaro/src/main/scala/com/cra/figaro/library/atomic/continuous/GaussVonMisesMixture.scala); [GaussVonMisesMixtureFit.scala](../Figaro/src/main/scala/com/cra/figaro/library/atomic/continuous/GaussVonMisesMixtureFit.scala).

**Validation:** [Release61ModelingTest](../Figaro/src/test/scala/com/cra/figaro/test/modernization/Release61ModelingTest.scala); [Release61RepresentationStudy](../Figaro/src/test/scala/com/cra/figaro/test/modernization/Release61RepresentationStudy.scala).

**Guide:** [GVM MIXTURE FITTING](GVM_MIXTURE_FITTING.md); [GVM MIXTURE RESEARCH PLAN](GVM_MIXTURE_RESEARCH_PLAN.md).

<a id="met-01"></a>

### MET-01: KL, Bhattacharyya and mutual-information definitions

**Source:** [MIT 6.441 information-theory lecture notes](https://ocw.mit.edu/courses/6-441-information-theory-spring-2016/pages/lecture-notes/) and the Bhattacharyya definitions in GVM-03.

**Role:** Definition / independently derived implementations. Shared information-measure semantics across Gaussian laws, joint GVM, mixtures and Monte Carlo estimates. GVM MI uses the full linear vector versus the angle; mixture MI is not the average component MI. Sample-based estimates and numerical intervals have different guarantees from analytic formulas.

**Code:** [GaussianInformation.scala](../Figaro/src/main/scala/com/cra/figaro/library/atomic/continuous/GaussianInformation.scala); [GaussVonMisesMutualInformation.scala](../Figaro/src/main/scala/com/cra/figaro/library/atomic/continuous/GaussVonMisesMutualInformation.scala); [GaussVonMisesMixtureMutualInformation.scala](../Figaro/src/main/scala/com/cra/figaro/library/atomic/continuous/GaussVonMisesMixtureMutualInformation.scala); [MonteCarloInformation.scala](../Figaro/src/main/scala/com/cra/figaro/algorithm/sampling/MonteCarloInformation.scala).

**Validation:** [DistributionConstructionsTest](../Figaro/src/test/scala/com/cra/figaro/test/modernization/DistributionConstructionsTest.scala); [GaussVonMisesMutualInformationTest](../Figaro/src/test/scala/com/cra/figaro/test/modernization/GaussVonMisesMutualInformationTest.scala); [Release61ModelingTest](../Figaro/src/test/scala/com/cra/figaro/test/modernization/Release61ModelingTest.scala); [MonteCarloInformationTest](../Figaro/src/test/scala/com/cra/figaro/test/modernization/MonteCarloInformationTest.scala).

**Guide:** [COMMON INFORMATION METRICS](COMMON_INFORMATION_METRICS.md); [GVM MUTUAL INFORMATION](GVM_MUTUAL_INFORMATION.md); [GVM MIXTURE MI](GVM_MIXTURE_MI.md); [MONTE CARLO INFORMATION](MONTE_CARLO_INFORMATION.md).

<a id="met-02"></a>

### MET-02: Closed-form scalar KL formulas

**Source:** Bauckhage, [Weibull KL notes](https://arxiv.org/abs/1310.3713) (2013); Chyzak and Nielsen, [Cauchy KL](https://arxiv.org/abs/1905.10965) (2019).

**Role:** Formula reference / independent implementation. Analytic scalar formulas checked against independent integrals. Lognormal invariance and selected common-shape reductions are documented separately; this is not analytic coverage of all distribution pairs.

**Code:** [ScalarDivergence.scala](../Figaro/src/main/scala/com/cra/figaro/library/atomic/continuous/ScalarDivergence.scala) (`kl`, `bhattacharyya` and analytic reductions); [Weibull.scala](../Figaro/src/main/scala/com/cra/figaro/library/atomic/continuous/Weibull.scala); [Cauchy.scala](../Figaro/src/main/scala/com/cra/figaro/library/atomic/continuous/Cauchy.scala); [LogNormal.scala](../Figaro/src/main/scala/com/cra/figaro/library/atomic/continuous/LogNormal.scala).

**Validation:** [CommonInformationMetricsTest](../Figaro/src/test/scala/com/cra/figaro/test/modernization/CommonInformationMetricsTest.scala); [Independent metric integrals](../tools/common_metrics_reference.py).

**Guide:** [COMMON INFORMATION METRICS](COMMON_INFORMATION_METRICS.md).

<a id="dist-01"></a>

### DIST-01: Distribution definitions and independent numerical controls

**Source:** [NIST distribution gallery](https://www.itl.nist.gov/div898/handbook/eda/section3/eda366.htm); SciPy references for [lognormal](https://docs.scipy.org/doc/scipy/reference/generated/scipy.stats.lognorm.html), [negative binomial](https://docs.scipy.org/doc/scipy/reference/generated/scipy.stats.nbinom.html), [multivariate t](https://docs.scipy.org/doc/scipy/reference/generated/scipy.stats.multivariate_t.html), and [von Mises](https://docs.scipy.org/doc/scipy/reference/generated/scipy.stats.vonmises.html).

**Role:** Definition / validation references. Parameterization and numerical checks for representative families, not attribution of every distribution's invention to these manuals. Student-t shape is not covariance; negative-binomial convention and lognormal parameters must follow Figaro's documented API.

**Code:** [StudentT.scala](../Figaro/src/main/scala/com/cra/figaro/library/atomic/continuous/StudentT.scala); [MultivariateStudentT.scala](../Figaro/src/main/scala/com/cra/figaro/library/atomic/continuous/MultivariateStudentT.scala); [LogNormal.scala](../Figaro/src/main/scala/com/cra/figaro/library/atomic/continuous/LogNormal.scala); [NegativeBinomial.scala](../Figaro/src/main/scala/com/cra/figaro/library/atomic/discrete/NegativeBinomial.scala).

**Validation:** [CommonDistributionsTest](../Figaro/src/test/scala/com/cra/figaro/test/modernization/CommonDistributionsTest.scala); [MultivariateStudentTTest](../Figaro/src/test/scala/com/cra/figaro/test/modernization/MultivariateStudentTTest.scala); [DistributionBreadthTest](../Figaro/src/test/scala/com/cra/figaro/test/modernization/DistributionBreadthTest.scala).

**Guide:** [COMMON DISTRIBUTIONS](COMMON_DISTRIBUTIONS.md); [MULTIVARIATE STUDENT T](MULTIVARIATE_STUDENT_T.md); [DISTRIBUTION SUPPORT](DISTRIBUTION_SUPPORT.md).

<a id="dist-02"></a>

### DIST-02: LKJ correlation distributions

**Source:** Lewandowski, Kurowicka and Joe, *Generating Random Correlation Matrices Based on Vines and Extended Onion Method* (2009). [DOI](https://doi.org/10.1016/j.jmva.2009.04.008); [Stan correlation reference](https://mc-stan.org/docs/functions-reference/correlation_matrix_distributions.html).

**Role:** Implemented / convention-specific. Independent partial-correlation Beta draws and matrix/factor densities. Different Jacobians apply to correlation matrices and their Cholesky factors; scale-plus-correlation priors are not an inverse-Wishart alias.

**Code:** [LKJ.scala](../Figaro/src/main/scala/com/cra/figaro/library/atomic/continuous/LKJ.scala); [CovarianceNumerics.scala](../Figaro/src/main/scala/com/cra/figaro/library/atomic/continuous/CovarianceNumerics.scala).

**Validation:** [CovariancePriorsTest](../Figaro/src/test/scala/com/cra/figaro/test/modernization/CovariancePriorsTest.scala); [Independent covariance controls](../tools/test_covariance_reference.py).

**Guide:** [COVARIANCE PRIORS](COVARIANCE_PRIORS.md).

<a id="dist-03"></a>

### DIST-03: Direct inverse-Wishart factor sampling

**Source:** Axen (2023), inverse-Wishart sampling, Theorem 3.2 and Algorithm 4. [Paper](https://arxiv.org/abs/2310.15884); [SciPy inverse-Wishart parameterization](https://docs.scipy.org/doc/scipy/reference/generated/scipy.stats.invwishart.html).

**Role:** Implemented / adapted factor convention. Direct factor construction uses a triangular solve; the paper's upper factor is transposed for Figaro's lower-factor convention. The algorithmic choice is not itself a measured Figaro speedup claim.

**Code:** [InverseWishart.scala](../Figaro/src/main/scala/com/cra/figaro/library/atomic/continuous/InverseWishart.scala).

**Validation:** [CovariancePriorsTest](../Figaro/src/test/scala/com/cra/figaro/test/modernization/CovariancePriorsTest.scala); [Independent covariance controls](../tools/test_covariance_reference.py).

**Guide:** [COVARIANCE PRIORS](COVARIANCE_PRIORS.md).

<a id="mod-01"></a>

### MOD-01: Conditional t laws and copulas

**Source:** Ding, *On the Conditional Distribution of the Multivariate t Distribution* (2016). [Paper](https://arxiv.org/abs/1604.00561); [Stan copula guide](https://mc-stan.org/docs/stan-users-guide/copulas.html).

**Role:** Implemented / bounded; modeling reference. Continuous Gaussian/t copulas and exact-coordinate conditional workflows. Not generic mixed/count copula support or arbitrary interval-conditioned integration; the separate mixed-data research in the guide remains a candidate.

**Code:** [CopulaDistribution.scala](../Figaro/src/main/scala/com/cra/figaro/library/atomic/continuous/CopulaDistribution.scala); [MultivariateStudentT.scala](../Figaro/src/main/scala/com/cra/figaro/library/atomic/continuous/MultivariateStudentT.scala).

**Validation:** [GvmMixtureCopulaTest](../Figaro/src/test/scala/com/cra/figaro/test/modernization/GvmMixtureCopulaTest.scala); [Release61ModelingTest](../Figaro/src/test/scala/com/cra/figaro/test/modernization/Release61ModelingTest.scala).

**Guide:** [COPULAS](COPULAS.md).

<a id="mod-02"></a>

### MOD-02: Censoring and measurement-error semantics

**Source:** Stan User's Guide: [truncation and censoring](https://mc-stan.org/docs/stan-users-guide/truncation-censoring.html), [measurement-error models](https://mc-stan.org/docs/stan-users-guide/measurement-error.html).

**Role:** Modeling reference / independent implementation. Observation-likelihood constructions distinguish exact density, event probability and measurement error. The implemented contracts are Figaro's own; an observation helper does not calibrate a model or choose its inference algorithm.

**Code:** [ObservationLikelihood.scala](../Figaro/src/main/scala/com/cra/figaro/library/atomic/continuous/ObservationLikelihood.scala).

**Validation:** [ObservationModelTest](../Figaro/src/test/scala/com/cra/figaro/test/modernization/ObservationModelTest.scala).

**Guide:** [OBSERVATION MODELS](OBSERVATION_MODELS.md); [MIXED MEASURES](MIXED_MEASURES.md).

## Keeping this index useful

The [contribution rules](../CONTRIBUTING.md) and [repository instructions](../AGENTS.md)
require this maintenance in the same change as the affected content. Run
`python -B tools/docs/check_research_references.py` before handing off. CI checks
reciprocal guide links and registration of research/literature notes in addition
to metadata and local paths; reviewers must still assess citation completeness
and the scientific claims themselves.

When adding or materially changing a method, update its reference group and the
linked guide in the same change:

1. Record the source's authors/title and DOI, stable publisher/author link or exact
   preprint revision when known. Identify manuals and software references as such.
2. Link actual source files and name the method-specific scope in the role field;
   link tests or independent oracles. State explicitly when no implementation exists.
3. Describe adaptations, parameter conventions, unsupported ranges and assumptions.
   Keep theoretical guarantees distinct from local numerical agreement or benchmarks.
4. Retain stable IDs and historical evidence. Update implementation/guide paths when
   files move; add a backlink from the relevant user/research guide.
5. Keep licenses and source-code provenance separate from scientific citation. A paper
   citation or a public repository is not permission to redistribute code or data.

Documentation checks verify local targets and index structure, not external availability,
mathematical correctness, full bibliography completeness or reproduction of a paper.
Do not copy private application models, local paths, reports or historical motivations
into this index. No temporal-grammar/framework-selection roadmap is introduced here.

Related: [distribution inventory](DISTRIBUTION_SUPPORT.md),
[information-metrics roadmap](INFORMATION_METRICS_ROADMAP.md),
[research candidates](../WISHLIST.md), [roadmap history](ROADMAP_HISTORY.md),
[dependency provenance](../DEPENDENCIES.md).
