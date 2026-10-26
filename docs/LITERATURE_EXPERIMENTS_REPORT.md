# What Figaro learned from literature-led experiments

A retrospective of sampling reliability, particle filtering, GVM-mixture fitting and MCMC adaptation. Evidence cutoff: 26 October 2026. Published capability baseline: Figaro 7.4.0.

[Download the PDF](reports/FIGARO_LITERATURE_EXPERIMENTS.pdf). [Supporting source-note snapshots](reports/EXPERIMENT_RECORDS.md) preserve cited local research records without publishing their experimental implementations. Other research citations use immutable Git commit links; none imply runtime promotion.

## Abstract

Figaro's modernization research asked a practical question: which ideas from the statistical-computing literature improve useful inference after numerical correctness, setup costs, failure rates and difficult cases are taken into account? The experiments evaluated alternative random-number generators and slice samplers; precision stopping and defensive importance proposals; particle diversity, resampling schedules and observation-informed filtering; higher-dimensional Gauss-von Mises (GVM) mixture optimization; and automatic proposal scaling and dependence learning for MCMC.

The strongest positive outcomes came from matching a method to a specific failure mechanism. Defensive proposals substantially improved concentrated-posterior estimation when the training budget was adequate. Retained log weights and decision-focused ancestor selection repaired numerical failures without clipping probabilities. A revised resampling schedule, followed by separately validated fixed-budget integration, produced useful filtering capabilities. Guided and auxiliary fixed-budget filtering earned opt-in release, with explicit evidence that guidance can also cost more than it saves.

The harder automation experiments were informative but did not qualify for promotion. Generic nonlinear and multi-component guides often improved equal-particle accuracy without beating a larger bootstrap population at comparable cost. Higher-dimensional GVM mixtures represented curved densities well, but fitting reliability and total cost remained limiting even after substantial optimizer improvements. Coordinate tuning helped scale mismatch, not arbitrary dependence; additional retained work repaired product-target failures but did not repair correlated and positive/bounded targets. A learned joint pilot delivered accurate accepted results, yet refused too often and cost too much. Its independent audit also exposed a genuine tied-quantile diagnostic bug, now separately repaired and CI-validated but not released at this cutoff.

The overall lesson is not that sophisticated methods fail. It is that a correct kernel, an accurate retained model, a reliable end-to-end workflow and a worthwhile consumer feature are different achievements. The research narrowed where each approach is useful, produced several bounded capabilities, and prevented promising but inadequately qualified automation from becoming a default.

## Executive summary

| Experiment or campaign | Take-home result | Disposition at this cutoff |
| --- | --- | --- |
| RNG comparison and statistical-failure diagnosis | Better RNG infrastructure did not cure proposal mismatch; inference geometry dominated the troublesome tests. | Modern backends available; no RNG-based inference guarantee. |
| Precision stopping and alternative slice samplers | Large savings or error reductions were possible on suitable targets; correlated and multimodal failures remained. | Bounded opt-in methods, not universal stopping or sampler selection. |
| Defensive importance proposals | Adequately trained frozen proposals greatly improved concentrated-posterior accuracy; inadequate pilots wasted small budgets. | Separate opt-in proposal APIs followed. |
| Gaussian-block pilot calibration | Good joint geometry helped; obtaining a trustworthy pilot was itself a major bottleneck. | Strict, explicit calibration; no automatic relaxation. |
| Continuous filtering numerical repair | Ordinary-space weight and CDF representation caused widespread refusals; log-domain retention and tree selection resolved the tested failures. | Shipped in 7.2.0. |
| Particle-diversity/resample-move screen | The selected extra-resampling option worsened depletion; MCMC moves did not earn their cost in calibration. | No promotion. |
| Single-selection resampling schedule | Fresh-seed tests showed useful accuracy/diversity gains within measured resource ceilings. | Separate integration led to 7.3.0; no universal ESS threshold. |
| Decision-focused stratification | Resolve the ancestor decision actually needed rather than require every tiny mass to occupy a distinct rounded CDF interval. | Independently checked repair shipped in 7.3.0. |
| Guided/auxiliary fixed-budget filtering | Useful on sharp and ambiguous observations, but poor look-ahead and easy targets exposed costs. | Opt-in extension shipped in 7.4.0. |
| Generic nonlinear and multi-component guides | Equal-particle gains did not establish cost-adjusted superiority; incomplete branches and unreliable shared pilots remained serious limits. | Test-only; no promotion. |
| Expensive-likelihood relevance and precomputation | Multiple inverse branches were not substantial posterior branches; reusable tables removed much of the proposed cost advantage. | Stopped before an irrelevant guide-utility trial. |
| Scalar GVM overlap numerics | Positive integration avoided cancellation that defeated signed Fourier calculations on a bounded scalar grid. | Separately validated explicit scalar API; not an automatic fallback. |
| Higher-dimensional GVM fitting and analytic gradients | Representation gains were real; faster phase solves moved the bottleneck to whole-fit convergence. | Research-only higher-dimensional fitter. |
| GVM acceleration, weight polishing and restart allocation | Some local improvements did not deliver reliable complete searches; weight polishing actually reduced completion. | No promotion. |
| Joint GVM optimization and predictive selection | Completion improved substantially, but reliability/cost gates failed; subsequent storage benefit was too small. | Firm exit; higher-dimensional selection line parked. |
| Multi-root coordinate tuning and budget studies | More retained work fixed product-target accuracy, but broader requalification failed on dependence. | No promoted multi-root tuner or automatic budget rule. |
| Joint transformed-block pilot and diagnostic repair | Joint geometry remained promising, but the learned workflow failed reliability/cost gates. Independent auditing found a separable numerical defect. | Pilot closed; narrow tie repair passed branch CI, not merged or released. |

## How to read the evidence

This is a synthesis of existing experiment records, not a new benchmark, a fresh systematic literature review or an exhaustive account of every modernization commit. It concentrates on the research campaigns that changed a method choice, exposed a capability limit or produced a promotion decision. It does not retell application motivations or the separate temporal-reasoning project. General build migration, distribution API implementation and routine performance engineering are outside its main scope.

Each discussion below separates the question, intended benefit, actual test and conclusion. The linked status records preserve full protocols, numerical tables, implementation links and original failure records. The [research reference and code map](RESEARCH_REFERENCES.md) identifies primary sources, the sections originally inspected, unavailable full texts, adaptations and code locations. A literature result is motivation, not a measured Figaro result or a theorem automatically inherited by Figaro's adaptation.

Several terms matter:

- **Numerically validated** means the specified arithmetic/reference checks passed in the tested range. It does not mean the sampler mixes well or an optimizer finds the global optimum.
- **Qualified fit** means a model meets that experiment's explicit fitting criterion. Criteria evolved between separately frozen studies; their completion percentages must not be pooled as if they measured one unchanged condition.
- **No promotion** means a candidate failed its declared requirements or did not establish sufficient benefit. It does not mean every underlying mathematical idea is invalid.
- **Refusal** means no qualified result was delivered. It is neither an accurate zero-error result nor necessarily a defect: strict refusal can correctly expose insufficient information or an unsupported numerical regime.
- **Equal particles, target evaluations, recorded work and elapsed time** are different comparisons. Pilot fitting, diagnostics, transformations and solver algebra can dominate costs not counted by the most convenient work counter.

Most fixtures are synthetic with analytic or independently computed numerical references. Numerical grid refinement is evidence, not a certified integration bound. Repeating identical seeds in several JVMs checks replay and timing; it does not increase the number of independent statistical samples. Neither zero observed failures nor a favorable R-hat/ESS value proves universal reliability.

For readers new to the terminology, **RMSE** is root mean squared error across repeated estimates; smaller is better for the specified query. **ESS** is an effective sample size, but weight-based ESS, mean ESS and tail ESS measure different things. **R-hat** compares within- and between-chain behavior; values near one are reassuring, not proof of adequate exploration. A **pilot** is a separate training run whose cost still counts even when its draws are discarded. **Mode depletion** means inadequate represented probability in a relevant region, under the particular experiment's definition. In the fitting studies, **K** is the number of mixture components and **d** counts linear coordinates, with one additional angular coordinate. Log scores and divergence values use **nats**, the units associated with natural logarithms.

## 1. Establishing what was actually going wrong

### 1.1 Random-number generators versus ineffective inference

The initial concern was whether apparently intermittent statistical failures reflected weaknesses in Java's legacy RNG. The literature review compared exact generator variants, including LXM, Xoshiro256++, PCG, Mersenne Twister and counter-based Philox. The hoped-for benefit was a rigorous, reproducible sampling foundation with appropriate stream ownership—not merely a longer period or a favorable microbenchmark.

The local benchmark used ten predeclared seeds, primitive draws and a real observed-Normal importance model. Modern Gaussian draws were faster than the legacy implementation in that adapter-inclusive measurement, but whole-inference times were similar. All five initially benchmarked backends met the declared point-accuracy target at 8,000 draws. These were application integration checks, not a local TestU01/PractRand certification or a universal generator ranking. LXM remained the default, with explicit alternatives and later stream-policy support. [Benchmark and exact variants](RNG_ASSESSMENT.md); [literature assessment](RNG_LITERATURE_REVIEW.md); [RNG provenance](RESEARCH_REFERENCES.md#rng-01).

The more consequential investigation fixed Gamma and Dirichlet datasets and compared estimates with independently integrated *posteriors*, rather than with the parameters that generated the data. The old tests mixed finite-data statistical variation with Monte Carlo error. Across the five-backend comparison, Dirichlet median importance ESS at 2,000 draws was only 1.002–1.009; even 200,000 draws gave about 9.34–10.07. Switching generators did not cure the mismatch between broad prior proposals and a concentrated posterior. [Statistical validation](STATISTICAL_VALIDATION.md); [backend cross-check](RNG_ASSESSMENT.md).

We concluded that RNG modernization and inference repair were separate jobs. The [PSIS literature](https://www.jmlr.org/papers/v25/19-556.html) informed weight-tail diagnostics, but Figaro's health assessment is not the complete PSIS estimator and does not import its interval guarantees. Warnings identify evidence of danger; they cannot explain away a known accuracy failure. [Diagnostic scope](RESEARCH_REFERENCES.md#dia-02).

### 1.2 Precision stopping: useful savings, conditional reliability

Fixed-width stopping research suggested that an inference run should stop when it has adequate precision for its monitored queries, rather than always consume a large fixed budget. Figaro tested this against analytic answers using 50 seeds, four chains and seven fixtures, including a correlated target and deliberately trapped chains. [Stopping study](STOPPING_VALIDATION.md); [fixed-width literature](RESEARCH_REFERENCES.md#stp-02).

On the well-mixing fixtures, the adaptive policy usually stopped at the first checkpoint and saved about 83% of retained draws; recorded single-host speed ratios were roughly five to six. Those savings bought lower precision than the full-budget result, not the same answer for free. On the correlated target, all 50 attempts reached the cap and joint interval coverage was only 24/50. Repeated diagnostics made unsuccessful adaptive runs substantially slower. Trapped chains also reached the cap even though balanced initial modes could make their pooled mean look reassuring.

The result justified bounded opt-in mean-precision stopping with explicit unsuccessful termination. It did not establish finite-sample nominal coverage, automatic discovery of missing modes or precision for every tail quantity. All later experiments in this report kept the released stopping estimator unchanged; optimizer termination and particle-budget experiments must not be confused with changes to that estimator.

### 1.3 Slice samplers, affine tuning and higher dimensions

The next question was whether a better transition could address failures that stopping diagnostics merely revealed. [Gibbsian polar slice sampling](https://proceedings.mlr.press/v202/schar23a.html) separates radial and directional updates; quantile slice sampling uses a reference distribution to construct slice moves. Figaro tested bounded adaptations, not every algorithm or guarantee in those papers. [SAM-01](RESEARCH_REFERENCES.md#sam-01), [SAM-02](RESEARCH_REFERENCES.md#sam-02), [broader candidate assessment](RESEARCH_REFERENCES.md#sam-03).

The two-dimensional matched-budget study retained 600 experiments and charged pilot and warm-up evaluations. Plain GPSS was the most balanced candidate in that screen; quantile sampling helped tails and separated modes. Affine tuning improved a rotated Gaussian but damaged unequal-mode reliability: its mode-probability RMSE was 0.05918 versus 0.00394 for plain GPSS. A covariance estimate that helps one geometry can obscure another. Earlier draw-matched advantages also did not necessarily survive equal-density-call accounting. [Matched-budget results](SAMPLING_BUDGET_VALIDATION.md).

The follow-up retained 480 experiments at eight and 32 dimensions. GPSS performed well on unconstrained Gaussian/heavy-tailed fixtures; quantile sampling was stronger on positive constraints. Both had zero joint coverage and zero precision successes on the 32-dimensional asymmetric mixture. This was inadequate exploration despite successful execution, not a numerical crash. [Higher-dimensional results](SAMPLING_HIGH_DIMENSIONAL.md).

The conclusion was to retain complementary, explicit continuous-vector methods rather than select a universal winner. A separately validated [vector API](VECTOR_SLICE_SAMPLING.md) followed. Automatic support for arbitrary mutable Figaro graphs, automatic preconditioning and reliable multimodal exploration did not follow from these experiments.

### 1.4 Defensive importance sampling: improve the proposal, charge the pilot

For the concentrated Gamma/Dirichlet posteriors, the hypothesis was that a discarded training pilot could fit a much more useful proposal than the prior. Defensive-mixture literature motivated retaining a broad support component while freezing the fitted proposal before production. The implementation did not use the source paper's control-variate estimator or inherit its stronger variance claims. [Literature and frozen protocol](DEFENSIVE_IMPORTANCE_RESEARCH.md); [INF-01](RESEARCH_REFERENCES.md#inf-01).

The experiment compared prior importance, pilot-plus-defensive importance and quantile slice under total target-call budgets, including pilot costs. At 20,000 calls, all 120 defensive runs met the all-coordinate point-accuracy target, versus 37/120 for prior importance and 92/120 for slice. At 2,000 calls, all defensive pilots were inadequate: after paying for training, the fallback had less production work and only 1/120 runs met the target. The low-budget failure is part of the result, not a disposable setup issue.

A separate 400-trial replication on the second datasets retained strong point accuracy, but nominal interval coverage remained uncertain; observed coordinate coverage ranged from 92.5% to 94.5%. Some accurate estimates still carried danger warnings. The conclusion was a bounded opt-in [frozen-proposal interface](VECTOR_IMPORTANCE.md), not unattended adaptation or a universal tenfold speedup. Adequate training helped these concentrated low-dimensional targets; successful mean estimation did not settle tail reliability.

### 1.5 Gaussian blocks: geometry helps, but a trustworthy pilot is not free

Joint proposals were attractive because moving one coordinate at a time can be inefficient along a narrow correlated ridge. The block-calibration study compared default MH, joint-prior moves, known-covariance blocks and blocks fitted from a separate pilot. It monitored means and second moments, and charged pilot, fitting and production costs. [Calibration study](PROPOSAL_CALIBRATION_VALIDATION.md); [block controls](BLOCKED_PROPOSAL_VALIDATION.md); [diagnostic provenance](RESEARCH_REFERENCES.md#dia-01).

The known-covariance control established an opportunity: it reached precision in all 30 six-dimensional correlated runs. The learned workflow rejected every pilot on that fixture. For the narrow two-dimensional target, only 11/30 pilots were accepted; their successful production results did not make the whole workflow 30/30 reliable. Easy independent targets favored simpler existing proposals after full costs were counted. On the curved banana target, calibrated precision-run joint coverage was only 22/29 completed runs despite frequent precision success.

This established a recurring theme: useful geometry may exist without a reliable way to learn it cheaply. Calibration remained explicit and strict. Later work therefore had to study pilot delivery and full workflow cost, not just the efficiency of a covariance matrix supplied by an oracle.

## 2. Particle filtering: separating arithmetic, diversity and guidance

### 2.1 Continuous benchmarks exposed a numerical representation problem

Finite-state and Gaussian controls alone could not establish readiness for nonlinear observations. The broader comparison used nonlinear growth, sharp-noise growth and persistent sign ambiguity, drawing on the growth-model literature while independently specifying data and stress cases. Bootstrap, guided, auxiliary, calibrated auxiliary and deliberately poor auxiliary strategies were retained. [Protocol, original failures and repaired rerun](CONTINUOUS_FILTER_BENCHMARKS.md); [INF-12](RESEARCH_REFERENCES.md#inf-12).

The original run completed only 2,537 of 7,680 trajectories. Positive mathematical weights could underflow on conversion from logs; cumulative ancestor probabilities could lose positive intervals. Those refusals blocked 260 of the 450 intended statistical checks. More particles sometimes created more opportunities to encounter the numerical limitation. Calling every refusal an impossible observation would have misdiagnosed the problem.

The repair retained log weights and used log-mass tree selection for independent multinomial ancestors. The identical protocol and original seeds then completed all 7,680 trajectories and passed all 450 checks, without floors, clipping or success-conditioned retries. That numerical repair was separately integrated and shipped in 7.2.0. [Method and code mapping](RESEARCH_REFERENCES.md#inf-13).

Crucially, mode depletion remained. At budget 256, calibrated auxiliary filtering ended depleted in 12/256 persistent-mode runs, while its lower work came with larger mode-mass error. Correct arithmetic made the comparison usable; it did not solve finite-population diversity or make a bad look-ahead efficient.

### 2.2 Resample-move and extra resampling did not earn promotion

Resample-move, marginal particle filtering and related SMC literature suggested that better offspring allocation or local MCMC rejuvenation might preserve diversity. Figaro first instrumented the unchanged filter, then tested a bounded scalar experiment with explicit predictive-mixture costs and independent finite/grid/Gaussian controls. [Assessment](PARTICLE_DIVERSITY_RESEARCH.md); [results](PARTICLE_DIVERSITY_STATUS.md); [INF-14](RESEARCH_REFERENCES.md#inf-14).

Calibration selected stratified resampling alone at N=64; no MCMC candidate won. Evaluating the full predictive-mixture target required quadratic density work that did not earn its cost on these fixtures. This was not a held-out rejection of every MCMC method: confirmation tested the selected option.

The selected option failed. On persistent modes, its final sign-mass RMSE ratio was 1.06094 relative to bootstrap, and trajectories depleted at any eligible step increased from 79/256 to 105/256. Late-disambiguation final error looked exceptionally small because the true final probability was nearly one, yet earlier depletion worsened. That boundary-sensitive endpoint statistic was not evidence of recovered histories.

The schedule also resampled outputs and selected ancestors again at the next step. The experiment identified this extra selection as a plausible source of unnecessary noise, not a proven sole cause. The appropriate conclusion was a new, frozen scheduling experiment—not further tuning of the failed candidate or general dismissal of rejuvenation.

### 2.3 A different resampling schedule passed its research gates

The next design used a single selection when resampling and otherwise propagated once while carrying weights correctly. ESS-triggered interaction was literature-informed; the experiment did not extend its guarantees to a random-stopping estimator. Calibration selected stratified ESS .75 with N=256 against a preselected affordable released guided baseline with N=64. [Protocol and results](RESAMPLING_SCHEDULE_STATUS.md); [INF-15](RESEARCH_REFERENCES.md#inf-15).

Held-out confirmation used 256 seeds and included a switching fixture excluded from calibration. Integrated sign-probability RMSE ratios were about .543, .398 and .248 on persistent modes, late disambiguation and switching respectively. All three met the frozen accuracy, depletion and resource-frontier requirements. No candidate depletion was observed on those fixtures, but each per-fixture upper risk bound was still approximately 1.16%, not zero.

This was not an identical-work comparison: the candidate used about 20–28% more counted work than the designated cheaper baseline, while being faster in the measured JVM runs. The frontier checks compared it with other tested affordable configurations as well. Nor did the study isolate .75 as universally better than always resampling; population size, stratification and carried-weight semantics also mattered.

The result justified a separate opt-in fixed-budget API review. Explicit resampling policy remained required. It did not authorize changing the released random-stopping construction or installing .75 as a default.

### 2.4 High stratified refusal rates required a different numerical contract

Initial integration made another limitation visible: 5,468 of 6,144 stratified trajectories refused. Diagnostic replay matched the original rows exactly and compared captured weights with 80/140-digit arithmetic. Most first failures were cumulative stagnation; others were ordinary-weight underflow or overshoot. The largest reference mass lost to zero/negative-width intervals on those captured vectors was about 4.91e-16. Small local mass loss explained the strict guard's frequent activation, but did not justify silently discarding mass. [Numerical investigation](STRATIFIED_NUMERICAL_INVESTIGATION.md).

Compensated sums still refused 5,425 vectors; even high-precision prefixes rounded back to binary64 refused 5,441. The problem was not simply insufficient summation care: arbitrarily small positive masses cannot all occupy distinct binary64 CDF intervals.

The replacement kept the stratum and random coordinate separate, bounded the relevant ancestor comparison and refined an ambiguous decision at bounded higher precision using the *same draw*. It did not retry with a more convenient random number. The independent prototype audit matched 1,572,906 ancestor decisions; fresh filter runs completed without refusals in the tested grid. Explicit precision exhaustion remained a tested outcome. [Prototype validation](STRATIFIED_DECISION_STATUS.md).

Separate numerical review, population-limit reconciliation and [production integration](STRATIFIED_INTEGRATION.md) led to 7.3.0. Finite-bit uniforms, work limits and possible unresolved comparisons remain limitations. This was a successful repair because it changed how the necessary decision was represented, not because it weakened the original accuracy requirement.

### 2.5 Guided and auxiliary fixed-budget filtering: a useful opt-in extension

Guidance directs children toward the observation; auxiliary look-ahead also changes which parents are selected. Particle-filter literature supplied the accounting: both require the complete proposal correction, including carried previous weights on non-resampling steps. The hoped-for benefit was better accuracy per useful work when observations strongly constrain the state. [Literature mapping](RESEARCH_REFERENCES.md#inf-16); [frozen study and final status](GUIDED_FIXED_BUDGET_STATUS.md).

Independent finite enumeration, Gaussian controls and a six-fixture fresh-seed comparison checked the extension. All 46,080 executions across three JVMs completed and all 720 unnormalized-measure screens passed; those repeats represented 256 independent seeds per configuration/fixture, not 768. The frozen utility rule passed for scheduled guidance on three primary fixtures and auxiliary guidance on two.

The negative controls were important. On ordinary growth, guidance reduced same-N RMSE but increased MSE-times-work by a factor of 2.656. Poor auxiliary look-ahead worsened both error and depletion. A larger bootstrap population remained a serious competitor. On sharp observations and persistent ambiguity, the useful guidance variants justified their additional work.

Following separate integration, CI, packaging and published-consumer gates, the extension shipped in 7.4.0. The practical conclusion is to evaluate guided-only first, and pay for auxiliary look-ahead when predictive parent information is demonstrably useful. No automatic proposal selection, precision guarantee or lost-history recovery was established.

## 3. Can observation-informed proposals become more automatic?

### 3.1 Single-Gaussian nonlinear guides: correct weights, inadequate utility

The next hypothesis was that cubature or Jacobian-based local approximations could construct guides automatically where the observation function is nonlinear. The test-only builders were assessed against independent numerical references, larger bootstrap populations and an existing manual two-root recipe. [Assessment](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/docs/NONLINEAR_PROPOSAL_RESEARCH.md); [results](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/docs/NONLINEAR_PROPOSAL_STATUS.md); [INF-17](RESEARCH_REFERENCES.md#inf-17).

All 984 independent unnormalized-measure checks passed. That validated the specified accounting, not proposal quality. Equal-N cubature improved ordinary and sharp monotone observations, but primary candidate root losses were 2.07–3.06 times the larger-bootstrap losses under the declared cost comparisons. Tail and curved-observation cases exposed more than construction overhead: their approximation quality also deteriorated. On the asymmetric fixture, Jacobian guidance depleted a mode in 12/128 runs versus 1/128 for bootstrap.

The manual two-root recipe was useful counterevidence: a shape-aware proposal could help the multimodal case, but it was not a generic builder. The conclusion was no promotion of the tested single-Gaussian builders, followed by a separate investigation of multi-component structure. Faster construction alone would not resolve a wrong proposal shape.

### 3.2 Multi-component guides: branch information helped; automatic pilots refused

Two alternatives were tested: guides constructed from explicit caller-supplied branches, and a shared discarded pilot fitted to a mixture. The aim was to represent several plausible observation inverses while retaining a broad defensive component and evaluating the full mixture density. [Assessment and protocol](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/docs/MULTICOMPONENT_GUIDE_RESEARCH.md); [complete results](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/docs/MULTICOMPONENT_GUIDE_STATUS.md); [INF-18](reports/EXPERIMENT_RECORDS.md#reference-inf-18).

The branch guide completed every run and substantially improved equal-N accuracy. In the three-branch fixture, depleted runs fell from 77/256 to zero. But its time-matched root-loss ratios were .9515 and 1.6303 on the two primary fixtures, failing the required .9 margin. Deliberately omitting a branch also failed the probability-error limits: defensive support ensured a possible route to the region, not enough finite-budget exploration of it.

The shared pilot refused 1,788 of 2,816 held-out runs. Iteration limits, insufficient-component fits and inadequate pilots were distinct recorded causes. Completed subsets could not stand in for a reliable all-attempt workflow; even the two fully completed fixture groups failed declared probability nonregression checks.

Both builders remained test-only. Equal-evaluation gains made expensive likelihoods a plausible follow-up for the explicit-branch design. They did not justify automatically enlarging pilot budgets, suppressing refusals or claiming missing-mode detection.

### 3.3 Expensive likelihoods: test relevance before optimizing the guide

Could branch guidance become worthwhile when each likelihood call solves an expensive forward model? A synthetic scalar damped-oscillator response provided multiple inverse branches and measurable solver cost. The approved follow-up required independent posterior branch qualification and a fair precomputation baseline before another filtering trial. [Assessment](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/docs/EXPENSIVE_LIKELIHOOD_RESEARCH.md); [qualification and table comparison](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/docs/BRANCH_QUALIFICATION_STATUS.md); [INF-19](reports/EXPERIMENT_RECORDS.md#reference-inf-19).

An independent LSODA calculation, solver comparisons, grid/domain refinement and a Gaussian control qualified the numerical reference. Despite several inverse roots, no observation step had two branch regions each carrying the required 5% posterior mass; two such steps were required. The central region held approximately 98.2–99.8%. Geometrical ambiguity in a forward map was therefore not enough to make this a relevant posterior-branch benchmark.

Separately, selected spline tables passed held-out log-likelihood and posterior checks. Their construction cost amortized at roughly 4,600–10,400 queries in the measured configurations. Direct evaluation was cheaper for short cold workloads. These were forward-model/table measurements, not executed filtering speedups; coarse failed tables remained failed.

The decision was to stop before a guide-utility trial. This saved work on a poorly matched experiment. It does not eliminate the possibility of benefit on a separately justified, genuinely multi-branch posterior with expensive non-reusable likelihoods. No prior or evidence was retuned to manufacture that relevance.

## 4. GVM mixtures: representation quality versus fitting reliability

### 4.1 Scalar overlap: a positive formulation overcame cancellation

Before higher-dimensional fitting, GVM divergence work encountered a numerical limitation: a signed Fourier expansion could lose a very small Bhattacharyya affinity through cancellation. Bessel identities and quadrature references suggested a positive scalar integral after Gaussian-overlap reduction. The goal was an honest numerical answer where the guarded Fourier method refused, not an unchecked fallback. [Assessment](GVM_BHATTACHARYYA_POSITIVE_RESEARCH.md); [mathematical provenance](RESEARCH_REFERENCES.md#gvm-03).

At a requested distance error of 1e-8 nats, the component prototype accepted all 84 scalar pairs, including 12 declined by the default Fourier calculation. Its largest observed discrepancy was 3.51e-10 nats. However, that stage used high-precision preprocessing; it did not establish end-to-end binary64 correctness. Quadrature discrepancy and rounding allowances were estimates rather than a formal interval certificate.

Separate Scala preprocessing and integration validation subsequently led to the explicit [scalar Bhattacharyya API](GVM_SCALAR_BHATTACHARYYA.md). The conclusion was a useful bounded alternative formulation, not arbitrary-dimensional coverage or automatic routing around a refusal. This successful numerical campaign provides a contrast to the optimization limits below.

### 4.2 Higher-dimensional fitting: the representation was useful before the fitter was reliable

The first higher-dimensional campaign asked whether GVM mixtures could represent curved linear-angular densities more compactly than Gaussian-mixture controls. The candidate used full covariance and quadratic phase with two or three linear coordinates plus one angle. Chart-fit Gaussians with wrapped evaluation and a genuine wrapped-likelihood Gaussian control helped separate representation effects from seam handling. [Assessment](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/docs/GVM_HIGH_DIMENSIONAL_FITTING_RESEARCH.md); [results](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/docs/GVM_HIGH_DIMENSIONAL_STATUS.md); [GVM-05](reports/EXPERIMENT_RECORDS.md#reference-gvm-05).

All 768 fits retained finite models and independent density checks passed. At the larger parameter ceiling, retained GVM models gained held-out log score on curved and seam-crossing GVM truths; wrapped-Gaussian truth modestly favored the Gaussian representation. But all 128 three-component GVM fits exhausted the phase-work budget. Gaussian controls also frequently reached iteration limits.

Thus the study demonstrated representation promise, not a reliable public higher-dimensional fitter or superiority between fully optimized families. Finite models were retained in quality comparisons even when optimization was unqualified. The next question was deliberately narrower: could analytic gradients remove the phase-solving bottleneck?

### 4.3 Analytic gradients: a real improvement that exposed the next limit

The gradient study reused the model and initialization controls, comparing the derivative-free phase solver with an independently derived analytic gradient used by SciPy L-BFGS-B. High-precision derivatives, finite differences and a separate solver supplied checks. The literature informed the bounded optimizer choice; its general guarantees were not treated as a guarantee for the whole mixture workflow. [Study](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/docs/GVM_GRADIENT_STATUS.md); [GVM-06](reports/EXPERIMENT_RECORDS.md#reference-gvm-06).

The 512-fit grid showed much less phase work. The analytic candidate met the then-declared convergence criterion in 41/64 two-component and 4/64 three-component primary fits, versus none for the derivative-free multicomponent baseline. Nevertheless, 83/128 primary multicomponent fits hit the outer iteration limit, failing per-cell reliability.

A post-fit diagnostic then recomputed phase gradients using responsibilities from the *final* model. All 45 multicomponent fits labeled converged exceeded the inner gradient threshold under those refreshed weights. This did not invalidate the historical criterion; it showed that likelihood stabilization and conditional component solves were not whole-fit stationarity.

The conclusion was to retain analytic gradients as the stronger research candidate, while making the next study's full-fit criterion explicit. Faster inner optimization alone was not enough, and its timings were not time-to-convergence comparisons for the many unfinished fits.

### 4.4 Whole-fit acceleration and complexity: separation mattered

The follow-up introduced an all-parameter, final-responsibility stationarity diagnostic and safeguarded squared extrapolation informed by SQUAREM. It included a genuinely three-component separated truth, overlapping components and deliberately overfitted cases. This distinguished “three components are difficult” from “the tested mixture geometry is difficult.” [Results](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/docs/GVM_WHOLEFIT_STATUS.md); [GVM-07](reports/EXPERIMENT_RECORDS.md#reference-gvm-07).

Across 560 fits, acceleration increased completed stationary multicomponent workflows from 49/160 to 71/160. The 13.75-point improvement missed the required 20 points, and per-cell reliability failed. Two-component completion improved from 36/80 to 56/80; three-component completion improved only from 13/80 to 15/80.

All 15 accelerated three-component successes came from separated three-component truth. Overlapping true mixtures and redundant-component fits remained problematic. There was also a distinction between a stationary retained model and a completed restart search: 100/160 accelerated retained models were stationary, but only 71 completed the declared workflow before its global budget expired.

The next assessment therefore targeted mixture-weight residuals and restart allocation. Neither the favorable subset nor a partially completed search was relabeled a promotion success. The source's SQUAREM theory was not transferred to this projected, circular, budget-limited adaptation.

### 4.5 Weight polishing and balanced restarts: improving one block hurt the whole workflow

Residual analysis suggested that mixture weights were often the largest unfinished block. Literature on constrained mixture proportions and optimization gaps motivated a bounded conditional solver; resource-allocation research motivated evaluating resumable restart scheduling. The hope was to spend work where it most improved qualified completion. [Assessment](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/docs/GVM_WEIGHT_RESTART_RESEARCH.md); [factorial results](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/docs/GVM_WEIGHT_STATUS.md); [GVM-08](reports/EXPERIMENT_RECORDS.md#reference-gvm-08).

The 1,040-fit confirmation separated weight polishing, balanced starts and their combination. All numerical and score-noninferiority checks passed. Yet polishing reduced multicomponent completion from 70/160 to 62/160. Balancing alone left completion at 70/160 and reduced the number of stationary retained models when the global search was interrupted.

The combined arm spent about 39.7% of its recorded logical work on weight operations and qualification. It reduced the targeted weight residual but left other parameter blocks dominant. Under the unchanged requirement to finish all starts, merely reordering independent trajectories could not reduce their aggregate required work.

This was a clear no-promotion result. A successful conditional solve was not a successful nonconvex mixture fit. An early-elimination restart policy would have changed the search contract and needed separate approval; it could not be inserted to rescue this experiment.

### 4.6 Joint optimization: large completion gains, unresolved total cost

Coupled residuals made a joint constrained solve plausible. The research candidate used a short safeguarded-EM prefix followed by SLSQP in coordinates that preserved covariance validity, with explicit constraints and physical-gradient qualification. This was not a port of the Riemannian Newton methods considered in the literature. [Feasibility study](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/docs/GVM_JOINT_STATUS.md); [GVM-09](reports/EXPERIMENT_RECORDS.md#reference-gvm-09).

The reused-data feasibility screen completed 18/20 cases versus 10/20 for the baseline, with no lost baseline successes. That warranted fresh confirmation, not promotion. The confirmation retained both unresolved curved cases, used eight fresh seeds, charged failed work, and repeated timing without treating repetitions as independent statistical samples. [Fresh confirmation](https://github.com/mattwilkinsphoto/figaro/blob/579bce2cc982dc087641b6b78dc110471f0597ad/docs/GVM_JOINT_CONFIRMATION_STATUS.md).

The candidate completed 149/160 cases versus 71/160: a substantial 48.75-point gain, with no lost baseline-complete cases and all score margins passing. But three three-component cells failed reliability. Total recorded data-pass work fell by about 38.5%, while summed median elapsed cost rose from 91.49 to 102.41 seconds. Dense constrained-solver work was not fully represented by the data-pass counter; the upper cost-ratio bound also failed the frozen gate.

The strongest positive evidence was genuine broader completion. The strongest counterevidence was unresolved curved/seam fitting and higher total cost. The approved decision remained no promotion—not that joint optimization had no value, and not that a different completion-versus-cost contract could never be worthwhile.

### 4.7 Predictive complexity selection: useful output, insufficient incremental benefit

Could selecting a smaller qualified density avoid insisting on redundant components? Model-selection literature warned against conflating predictive quality, the true number of components and successful optimization of every alternative. The experiment therefore kept incomplete comparisons explicit and evaluated compact predictive output, not latent-component recovery. [Assessment](reports/EXPERIMENT_RECORDS.md#record-gvm-complexity-selection-research); [results](reports/EXPERIMENT_RECORDS.md#record-gvm-selection-status); [GVM-10](reports/EXPERIMENT_RECORDS.md#reference-gvm-10).

Across 20 datasets and 60 fits, the selector returned a qualified model for every dataset and passed all declared score and regional-probability checks. Fourteen candidate comparisons were complete; six were incomplete because some larger models did not qualify. The designated storage measure fell only from 954 to 916 float64 scalars—a 3.98% reduction, below the frozen 20% requirement. These were parameter-payload counts, not measured heap or runtime savings.

Ordinary validation-best selection already preferred small models on many datasets, so the additional parsimony rule changed only two choices. Returning a qualified density everywhere was favorable evidence, but did not establish enough incremental benefit to justify the milestone.

The firm exit ended this development experiment; confirmation seeds were not used. The higher-dimensional selection line was parked rather than starting another optimizer cycle or changing the tolerance after observing the result. Existing scalar fitting and distribution APIs were unaffected.

## 5. MCMC adaptation: scales, budgets and dependence

### 5.1 Multi-root warm-up: acceptance tuning was not a universal efficiency gain

Adaptive-MCMC literature and maintained samplers suggested extending discarded scalar warm-up across explicitly declared roots, freezing all scales before retention. This could reduce manual configuration while preserving the existing MH scoring and ownership model. The initial performance screen included product Normals, correlation, coupled Gamma/Beta roots, circular values and separated modes. [Assessment](reports/EXPERIMENT_RECORDS.md#record-multilatent-warmup-research); [results](reports/EXPERIMENT_RECORDS.md#record-multilatent-warmup-status); [INF-20](reports/EXPERIMENT_RECORDS.md#reference-inf-20).

All ordinary candidate runs passed accuracy in the 144-attempt development inventory. However, the four-root error-times-total-cost ratio was .855, above the required .8. Strong correlation and separated modes remained adverse; known-covariance blocks showed a useful direction, while all eight existing block-calibration attempts refused.

The study ended at its development gate with confirmation unused. A separately approved usability-oriented wrapper then added explicit configuration, accounting and monitors without a speed promise. Its 704-attempt fresh qualification completed all candidate executions and passed overhead checks, but two new eight-root seeds failed mean accuracy in both JVMs. Their failing traces matched the unchanged prototype; the evidence did not identify a wrapper-induced trajectory regression. [Usability qualification](reports/EXPERIMENT_RECORDS.md#record-coordinate-warmup-status).

The conclusion was to investigate budget adequacy rather than relax accuracy or treat a favorable acceptance rate as evidence of adequate sampling. Neither the performance candidate nor the convenience API qualified for promotion.

### 5.2 Fresh budget and initialization experiment: retained work explained the product-target failure

For random-scan coordinate moves, adding coordinates reduces how frequently each is updated under a fixed total transition budget. Retained-data analysis suggested this explanation, but did not separate insufficient sampling from initialization or adaptation. A fresh, four-arm experiment varied retained work, warm-up and original versus oracle-posterior initialization, with informed fixed-scale controls. [Protocol and findings](reports/EXPERIMENT_RECORDS.md#record-coordinate-budget-status); [assessment](reports/EXPERIMENT_RECORDS.md#record-coordinate-budget-research).

All 1,152 attempts completed. At eight roots, quadrupling retained draws gave median mean-ESS ratios of 3.929 for tuning and 4.029 for informed fixed scales; mean-RMSE ratios were .521 and .542. Increasing warm-up alone did not establish the required benefit, nor did the declared initialization contrasts. All 96 maximum-budget original-candidate executions passed accuracy; all 80 lower-budget failures remained in the record.

This was more precision from more work, not a faster kernel. The fixed-scale controls strengthened the explanation that retained work per coordinate was inadequate on these product targets. They did not prove initialization never matters or establish a dimension-linear budget law for arbitrary posteriors.

All five diagnostic gates passed, allowing a separate broader requalification review. The earlier failed protocols were not retroactively repaired, and the additional-budget result was not itself a public promotion.

### 5.3 Original-fixture requalification: more scalar work did not solve dependence

The broader test retained every original ordinary and adverse fixture, dimension-scaled budgets, four chains, 32 fresh seeds and two JVM executions. The complete inventory contained 2,816 attempts. [Requalification record](reports/EXPERIMENT_RECORDS.md#record-coordinate-requalification-status).

Product and circular candidate cases passed, but 24/384 ordinary candidate executions failed accuracy: seven correlated seeds and five coupled Gamma/Beta seeds, each repeated in both JVMs. Informed fixed scalar proposals also failed on these geometries. Known-covariance blocks passed every correlated execution, while strict fitted-pilot calibration refused 126/128 attempts.

That contrast shifted the question from step-size or blanket budget increases to learning dependable joint geometry. It also reinforced the limit of diagnostics: one inaccurate Gamma/Beta query had R-hat about 1.00242 and no warning on that query, although other queries warned. The adverse safety gate asked whether *any* query warned; it did not certify every query's absolute error.

The requalification therefore failed. More work had resolved a bounded product-target problem, not general proposal adequacy. No automatic replacement of released choices was justified, especially since default MH remained competitive on some adverse fixtures.

### 5.4 Joint transformed-block pilot: correct integration, unreliable delivery

The next literature-led plan separated learning, independent fixed-kernel qualification and fresh production. A narrowly approved internal graph adapter supported owned fixed Normal/Gamma/Beta roots with identity/log/logit transforms, one joint MH decision and explicit rollback. The goal was to measure dependence learning inside real Figaro graphs rather than compare a lightweight vector prototype unfairly with graph-based controls. [Assessment](reports/EXPERIMENT_RECORDS.md#record-dependence-aware-proposal-research); [adapter and experiment](reports/EXPERIMENT_RECORDS.md#record-dependence-pilot-status); [INF-21](reports/EXPERIMENT_RECORDS.md#reference-inf-21).

Deterministic graph controls and independent density/Jacobian audits passed. That established bounded integration correctness, not a qualified learned sampler. The frozen development experiment retained all 272 attempts. Of 40 ordinary candidate attempts, 33 delivered accurate results and seven refused qualification. Every ordinary fixture failed its designated total-cost ratio requirement; factorized two-dimensional cost-error ratio was 12.58 against a 1.25 limit. Adverse strong-correlation delivery was sparse and all separated-mode attempts refused.

Known-covariance controls again supported the value of joint geometry, but could not substitute for the learned workflow. Costs included learning, qualification, refused attempts, diagnostics and raw-trace emission. These are prototype end-to-end costs, not production-throughput measurements.

Development failed reliability and utility requirements; confirmation remained prohibited. Separately, its frozen independent audit found a tail-ESS discrepancy. That arithmetic finding required investigation even though correcting it could not erase the scientific no-promotion result.

### 5.5 Tied quantiles: a failed experiment produced a useful correctness repair

Tail ESS uses indicators at pooled quantile cutoffs. Repeated values are normal when MH rejects moves. The audit found that interpolating two *identical* floating-point order statistics rounded the cutoff one representable value below them, excluding ten tied draws in the first failing trace. [Diagnosis and repair](https://github.com/mattwilkinsphoto/figaro/blob/43ce624eb97ad320509a6978a5ee5c3db6b7d1cb/docs/TIED_QUANTILE_REPAIR.md); [DIA-01](RESEARCH_REFERENCES.md#dia-01); [tail-ESS definition](https://mc-stan.org/posterior/reference/ess_tail.html).

The narrow repair returns an equal endpoint exactly, retaining the old unequal-endpoint arithmetic. Actual Scala replay and independent recomputation covered all 5,798 summaries from the 272 attempts. Tail ESS changed in 45 summaries—23 down and 22 up—but no retained projection, qualification or strict-calibration decision changed. Non-tail diagnostics were bit-identical. That is a bounded replay result, not proof that a future near-threshold caller cannot change decisions.

The correction was isolated from the unpromoted adapter/pilot work. At this report's cutoff, all five jobs in [branch CI for commit 43ce624e](https://github.com/mattwilkinsphoto/figaro/actions/runs/40366213712) passed, including packaging and consumer checks; it had not been merged or released. Earlier status prose describing remote CI as a future gate records an earlier checkpoint. The learned pilot remains closed and its original frozen audit remains failed.

The lesson is concrete: independent validation can reveal a production-relevant numerical defect even when the candidate being evaluated is not worth promoting. Correcting the defect and promoting the candidate are separate decisions.

## 6. What the combined evidence changes

### Match the remedy to the failure mechanism

The experiments distinguish at least five problems: poor proposal support allocation, insufficient retained work, dependence or missing modes, finite-precision representation, and optimization/search incompleteness. They can produce superficially similar symptoms—low ESS, refusals, inaccurate estimates or long runs—but require different remedies. New RNGs did not repair posterior concentration; more particles did not repair rounded-away ancestor intervals; more scalar sampling did not fully repair coupled directions; faster phase solves did not finish mixture optimization.

### Measure the user's complete outcome

Equal-N gains are useful scientific evidence, but users pay for guide construction and pilot fitting. Likewise, a stationary retained model is valuable, yet not the same outcome as a completed declared restart search. A workflow that delivers accurate answers only after many refused attempts needs its refusal rate and spent work reported. The joint-optimizer study is the clearest warning against equating logical work with runtime: fewer data passes coexisted with higher total elapsed cost.

### Prefer informative controls over a favorable aggregate

Known-covariance blocks isolated the value of geometry from the difficulty of learning it. Oracle initialization and fixed-scale controls distinguished starting-state questions from retained-work shortage. Poor auxiliary guides, omitted branches, separated modes and redundant components exposed limitations that ordinary cases concealed. High-precision checks and independent reference integrations separated implementation bugs from limitations of a method.

Those controls also limit broad negative conclusions. The GVM fitter worked much better on separated true three-component mixtures than on overlap or redundant components. A branch guide improved equal-particle mode retention even though it failed its cost gate. These favorable observations remain useful, but they do not override the requirements that failed.

### Preserve the meaning of a failed gate

Fresh seeds, frozen protocols and all-outcome inventories prevented post-hoc improvements from becoming retroactive successes. Where a different question was justified, it received a separate experiment: schedule redesign after extra-resampling failure; usability after the warm-up performance failure; joint optimization after conditional polishing; predictive selection after fixed-K fitting limits.

The evidence does not imply that every future experiment should inherit the same thresholds. It implies that a changed consumer objective, acceptable failure rate or cost tradeoff should be decided *before* a new confirmatory trial. In particular, the large joint-optimization completion gain could matter under a different contract, but this report does not authorize or claim such a change.

### Keep useful capabilities explicit rather than promise universal automation

The released filtering and vector-sampling options offer meaningful capabilities without silently choosing a universally best policy. The unpromoted studies leave important gaps: dependable high-dimensional GVM fitting, reliable automatic dependence calibration, generic nonlinear proposal construction, and missing-mode detection. Diagnostics help users recognize some failures, but do not remove these limits. Thread parallelism and correct RNG stream ownership also do not make an inadequately exploring kernel statistically adequate.

## 7. Implications for future work

This retrospective does not reopen any closed experiment or alter the roadmap. It suggests three requirements for evaluating a future proposal.

First, identify a concrete mechanism and a relevant workload before investing in a new algorithm. A costly forward model is not automatically a multimodal posterior, and a large gradient residual in one block is not automatically the dominant end-to-end bottleneck. Include a simpler baseline such as more retained work, a model-specific proposal or reusable precomputation.

Second, define the intended consumer contract: best predictive density under a budget, complete fixed-K optimization, reliable qualified delivery, or faster inference at a specified accuracy. These are different targets. Make supported graph structure, parameter ranges, refusal behavior and setup amortization part of that contract.

Third, require independent numerical checks and fresh all-outcome validation before promotion. Retain original hard fixtures, charge unsuccessful work and report the strongest counterevidence. A narrow correctness repair can proceed separately from a failed experimental feature, as the tied-quantile work demonstrates. Released stopping, defaults and strict calibration should not change merely to accommodate a promising candidate.

## Evidence, references and maintenance

The linked experiment records are the authoritative sources for reported measurements. Their protocol/source maps and machine-readable results provide reproduction entry points; their original evidence archives preserve execution logs, failed revisions, inputs, software versions and hashes. This report neither reruns those studies nor reconstructs missing logs. Its separate synthesis bundle records the exact source documents and directly linked machine-readable evidence used here, the report revision, a claim-location ledger and the documentation checks actually executed for this writing task. It is not a replacement for the original large experiment archives.

The literature linkage is inherited from the [consolidated reference map](RESEARCH_REFERENCES.md), including partial-review and access limitations. During synthesis, the GPSS and PSIS publication abstracts and the maintained tail-ESS definition were rechecked; the SQUAREM publisher page timed out. The existing GVM-07 assessment remains the source for that method's recorded literature role. No new full-paper/proof audit is claimed.

When extending this report, preserve the distinction between historical experimental status and subsequent integration. Add a new result or correction with its evidence rather than silently changing an old gate or failure. Update the appropriate stable research-reference group and code/validation links. Research snapshots and unmerged code must not be presented as published capabilities; consult the [roadmap](../ROADMAP.md) and [release record](RELEASE_7_4_STATUS.md) for the baseline described here.
