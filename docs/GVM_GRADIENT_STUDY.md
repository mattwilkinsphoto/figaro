# Bounded analytic-gradient GVM fitting study

Research provenance: [GVM-06](RESEARCH_REFERENCES.md#gvm-06).
This approved test-only follow-up asks whether an analytic angular gradient can
repair the [previous study's convergence limitation](GVM_HIGH_DIMENSIONAL_STATUS.md).
It does not alter the previous frozen protocol/results, public fitting API,
released stopping estimator, defaults, shared pilot or production dependencies.
No promotion, merge or release is automatic.

## Literature and method choice

Byrd, Lu, Nocedal and Zhu's [limited-memory bound-constrained algorithm](https://users.iems.northwestern.edu/~nocedal/PDFfiles/limited.pdf)
provides the gradient-projection and limited-memory quasi-Newton basis. The
abstract, introduction and algorithm outline were inspected for this follow-up,
not treated as a global convergence guarantee for a periodic mixture objective.
The authors' [software page](https://users.iems.northwestern.edu/~nocedal/lbfgsb.html)
identifies L-BFGS-B and its BSD-3-Clause licensing. The maintained
[SciPy solver documentation](https://docs.scipy.org/doc/scipy/reference/optimize.minimize-lbfgsb.html)
distinguishes objective-reduction termination from projected-gradient termination
and warns that numerical differentiation can exceed its evaluation option.
These sources were inspected on 2026-10-24. SciPy is already present in the
isolated research environment; no new runtime dependency or optimizer port is needed.

Use SciPy L-BFGS-B with the independently derived analytic weighted-resultant
gradient. The concrete custom work is the bounded GVM EM orchestration and
evidence capture, not a new general-purpose optimizer. Keep the covariance,
concentration, phase constraints and Gaussian controls from [GVM-05](RESEARCH_REFERENCES.md#gvm-05).
BOBYQA remains a relevant derivative-free control; finite-difference L-BFGS-B
isolates the benefit of supplying derivatives on small phase problems.

For normalized responsibilities `w`, phase features `F`, coefficients `b`, and
residual `r = theta - F b`, let `C = sum(w cos(r))`, `S = sum(w sin(r))`.
The minimized objective and gradient are

```text
f(b) = -(C*C + S*S)
gradient = -2 F^T [w * (C sin(r) - S cos(r))]
```

This profiles the angular offset/concentration using the resultant. It is a
nonconvex periodic objective; a small gradient is not proof of the best phase
or mixture. Weak concentration can make phase weakly identifiable. Inner-solve
qualification and outer EM likelihood stabilization must be recorded separately.

## Three stages and controls

1. Check the derivative against 60-digit direct complex-resultant differentiation
   and central differences in dimensions 1, 2 and 3. Compare small phase solves
   with finite-difference SLSQP using the separate reference objective, and with
   finite-difference L-BFGS-B. Check bound-stationarity conventions and refusals.
2. Qualify the bounded candidate on development-only data: independent densities,
   monotone accepted likelihood, repeatability, rank/nonfinite/tiny-component,
   time/phase/iteration/angular-budget controls and weak/overlap/minority/misspecified
   cases. Freeze source hashes and configuration before confirmation.
3. Run paired fresh-seed fits against unchanged Java BOBYQA, wrapped Gaussian
   controls and declared work curves. Independently audit every retained model
   and every saved input hash. Retain failures and exact execution records.

The former L-BFGS-B reference is now the candidate backend and cannot count as
an independent optimizer validation. High-precision differentiation, finite
differences, SLSQP and the unchanged Java implementation provide separate checks.
Shared fixtures/density utilities are explicitly not independent; the full
inverse/determinant high-precision evaluator is the density audit.

## Frozen design

The machine-readable protocol and its source hashes are generated after controls
and committed before held-out execution. Primary: dimensions 2 and 3; curved,
seam, local-Gaussian and wrapped-Gaussian truths; eight fresh seeds 940001–940008;
K=1,2,3; analytic GVM and BOBYQA. This gives 384 paired-method fits. Training and
test samples are separate streams of 2,000 and 20,000 observations. The smaller
eight-replicate grid is deliberate for this optimizer study, not a replacement
for the previous 16-replicate distribution qualification.

Both GVM methods use three starts, 50 outer iterations, 400 angular evaluations,
20 million phase-point contributions and 120 seconds per full fit. Coordinates
are whitened on training data only; covariance eigenvalues remain [0.01,25],
phase coefficients [-4,4], concentration [0,50]. Java supplies the third-start
random keys to Python; the first two sorted partitions match the frozen baseline.
No silent component pruning, failed-seed deletion or favorable restart selection
outside the declared likelihood rule. A global budget failure overrides a retained
model's earlier convergence, as in the baseline.

Candidate inner solver: `ftol=1e-14`, `gtol=1e-7`, 400 iterations, 30 line-search
steps, ten correction pairs. Qualification additionally requires a successful
solver status and projected gradient mapping at most 1e-6. Outer threshold remains
1e-5 mean log-likelihood improvement, with a nondecrease tolerance of 1e-8 per point.
This is stricter than trusting the baseline's BOBYQA return alone; statuses are
not identical mathematical convergence certificates. External counters enforce
caps, including rejected objective evaluations. Gradient arithmetic is extra work
not captured by a phase-point count: report wall time and density work too.

Wrapped-likelihood GMM K=4 is run for each of the 64 datasets under its original
50-iteration, three-start and 120-second limits. It is a representation context,
not a test of angular gradients. Diagnostic curves use the first two fresh seeds
on curved/seam truths in each dimension, K=3, both GVM methods: 2M/8M phase points
at 50 iterations, and 20M at 200 iterations (48 fits). Wrapped GMM gets a separate
200-iteration diagnostic on the first two seeds of all eight cells (16 fits).
Total confirmation: 512 fits, plus at most 128 development/control fits; total
serial execution ceiling three hours. Diagnostic extra iterations are never
substituted for a primary failure or described as changed Figaro defaults.

## Decision gates

All independent correctness, inventory, parameter-envelope and failure controls
must pass. Candidate completion requires at least 95% `Converged` in every primary
dimension/family/K cell (with eight seeds, all eight). Report aggregate paired
completion difference for K=2,3; a useful repair targets at least 20 percentage
points improvement over BOBYQA. This is empirical qualification, not a population
reliability guarantee. Fit completion does not certify global optimality.

For all 24 primary dimension/family/K comparisons, report paired mean held-out
log-score differences and Bonferroni-adjusted Student-t intervals across the eight
independent training replicates (family alpha .05). The lower endpoint must exceed
-0.02 nats/point for noninferiority. Missing finite pairs fail this gate; retained
unqualified models remain labeled as such. Use the same observations, components,
starts and parameter constraints. Do not use test scores to select a candidate.

Even a passing optimizer study does not authorize public promotion: the prior
representation qualification, Gaussian-control convergence and public API review
remain separate gates. No claim of cost-matched or universal GVM superiority follows.
If phase work falls but EM iteration limits dominate, report that distinction and
recommend a new bounded study instead of changing the frozen thresholds.

## Evidence

Results will link the frozen configuration, complete outcome summary, independent
audit and reproducibility archive. The archive includes generated inputs and
SHA-256 hashes, exact source versions, software versions, parameters, actual logs
and errors with timestamps, full models/traces, plots and rerun instructions.
Private paths and machine-account details stay out of public documents and Git.
