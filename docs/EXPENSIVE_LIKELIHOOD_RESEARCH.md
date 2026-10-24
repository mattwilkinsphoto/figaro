# Expensive likelihood branch guide assessment

**Historical relevance screen complete; the subsequent
[qualification](BRANCH_QUALIFICATION_STATUS.md) stops this workload before a guide
utility trial.** The screen identified a plausible synthetic workload for bounded
reference design, not promotion or a speedup claim. A numerically integrated
nonlinear oscillator has a scalar response with multiple inverse branches and
material callback cost. Whether those branches matter under a useful prior, and
whether precomputation eliminates that cost, were the decisive follow-up questions.
The frozen posterior failed branch relevance; validated precomputation substantially
reduced reused-query cost. The original screen and its numerical failures below
remain part of the record.

[Research provenance](RESEARCH_REFERENCES.md#inf-19),
[original frozen screen](EXPENSIVE_LIKELIHOOD_PROTOCOL.md),
[numerical revision](EXPENSIVE_LIKELIHOOD_NUMERICAL_REVISION.md),
[machine-readable result](../tools/expensive_likelihood_summary_v1.json).
The previous [no-promotion result](MULTICOMPONENT_GUIDE_STATUS.md) stands.
The shared pilot stays parked. Stopping, runtime, defaults and 7.4.0 are unchanged.

## Literature and workload selection

This is a targeted relevant-section assessment, not a reproduction of published
experiments or an assertion that a consumer needs the proposed model.

| Source and inspected scope | Relevance and boundary |
| --- | --- |
| Kersting et al., [Differentiable Likelihoods for Fast Inversion of Likelihood-Free Dynamical Systems](https://proceedings.mlr.press/v119/kersting20a/kersting20a.pdf), 2020, introduction and section 2 | Numerical ODE forward maps give a legitimate source of observation cost. Their probabilistic ODE likelihood approximation is not the Figaro branch guide; no algorithm or guarantee is transferred. |
| Galioto and Gorodetsky, [Bayesian system ID](https://ngalioto.github.io/files/galioto2020bayesian.pdf), 2020, section 6.4, equation 51 and discussion | Nonlinear pendulums are an established system-identification example. Their two-state/model-uncertainty experiment is not this scalar reset-response model. Damping, initial conditions, horizons and noise below are independently specified synthetic choices. |
| Branchini and Elvira, [Optimized auxiliary particle filters](https://proceedings.mlr.press/v161/branchini21a/branchini21a.pdf), 2021, section 3.4 | Construction/optimization and weighting costs must be distinguished. Their optimized ancestor-mixture method is not implemented here; reported improvements do not forecast Figaro performance. |
| Official Commons Math 3.6.1 [DP853](https://commons.apache.org/proper/commons-math/javadocs/api-3.6.1/org/apache/commons/math3/ode/nonstiff/DormandPrince853Integrator.html) and [GBS](https://commons.apache.org/proper/commons-math/javadocs/api-3.6.1/org/apache/commons/math3/ode/nonstiff/GraggBulirschStoerIntegrator.html) APIs | Reuse two distinct existing ODE algorithms rather than build a solver. This uses Figaro's existing dependency, not a newly installed backend; no external implementation code or dataset is copied. |

The repository's preceding scalar filter fixtures use inexpensive explicit
observation functions. GVM quadrature examples are not directly compatible scalar
Gaussian response likelihoods. General expensive PDE inversion, image renderers
and vector-state tracking would need broader model interfaces or different noise
semantics. Their cost alone does not qualify them for this unchanged guide.

The selected screen uses q''=-sin(q)-0.15*q', q(0)=0.2, q'(0)=x, with a scalar
unwrapped response q(T) at T=2 or T=6 and additive Gaussian noise sd 0.2.
It can represent uncertain launch velocity in repeated reset experiments; it
does not reduce continuous two-state tracking to a scalar filter. A Gaussian
sequence of launch velocities is a possible later modeling assumption, not
something established by this profile. No application data were supplied or used.

Installed Java 17 and Commons Math were sufficient. The official SciPy
[solve_ivp documentation](https://docs.scipy.org/doc/scipy/reference/generated/scipy.integrate.solve_ivp.html)
was screened as a possible independent reference implementation; SciPy was not
installed or executed in this initial screen (the follow-up uses LSODA). Its
DOP853 shares an algorithm with the JVM candidate;
an independent reference should use a different method and convergence checks.

## Executed numerical and cost screen

The original 1e-8 solver tolerance failed 15 of 3,852 checks at five longer-horizon
inputs. The largest endpoint discrepancy was about 3.305e-6 against a 1e-6 limit.
No timing executed in that failed attempt. The retained additive revision tightened
solver tolerances to 1e-10, not the acceptance criteria, and added GBS as a competing
profiled solver. Inputs, horizons, noise and all original limits were preserved.
This is numerical calibration, not an explanation of a Figaro defect.

All **7,704 checks passed in each of three fresh, sequential JVMs**, with identical
recorded point results. These are repeated deterministic checks, not independent
statistical replications. Four analysis inventory/failure controls also passed.
The checks cover all 642 grid points, tighter-step solutions, a different numerical
algorithm, sensitivities against central differences and analytic linear reduction.
Same-library solver agreement is not an independent posterior reference or a proof
of global error control.

| Horizon | DP853 endpoint cost | DP853 endpoint plus sensitivity cost | Observed branch structure |
| --- | --- | --- | --- |
| T=2 | 8.7–8.9 microseconds/call | 13.0–13.7 microseconds/call | One crossing for each tested observation; no detected derivative sign change |
| T=6 | 22.3–23.1 microseconds/call | 35.5–38.0 microseconds/call | Three crossings for each of y=-0.5,0,0.5; two detected derivative sign changes |

Ranges above span the three JVM medians of seven batch averages, each over all
321 inputs. They are not latency percentiles, posterior-weighted costs or confidence
intervals. Allocation is included. GBS was slower on this grid, with substantial
batch/JVM variability (long-horizon endpoint medians 37.4–63.6 microseconds); retain
all timings and use the faster valid implementation as the planning baseline.
This coarse profile is not a JMH result or an end-to-end filter comparison.

The longer-horizon callback cost is large enough to justify investigating whether
fewer evaluations offset guide construction. That is a relevance judgment, not
a break-even estimate: earlier polynomial-model losses cannot be transferred to
this model. Sensitivities are real charged work; the current separate h/Jacobian
callback shape must not receive free shared computation in an eventual benchmark.

## Original bounded continuation and subsequent decision

The following was the approved continuation plan. The
[completed qualification](BRANCH_QUALIFICATION_STATUS.md) stops before step 3;
these paragraphs are retained as rationale, not an active execution queue.

Proceed only to **reference and workload qualification** before new filtering runs:

1. Freeze a small repeated-reset sequence and meaningful Gaussian initial/transition
   laws. Independently integrate the true likelihood to assess branch probability,
   tails and evidence, then full scalar filtering recursion. Check a second solver
   implementation, domain/resolution refinement and analytic controls. More inverse
   roots do not establish multiple important posterior modes. Keep T=2 as a simpler
   control; do not make minor branches important by tuning to favorable guide results.
2. Define a model-only <=4-anchor recipe, charging root isolation, sensitivity and
   preparation. A grid sign change can miss close or tangent roots, and [-8,8] does
   not cover a Gaussian's whole support. Independently check any claimed supported
   region and its tails. Retain omitted/merging-branch failures. Compare against an
   accurately validated interpolation/lookup forward map available to all methods,
   accounting for both cold construction and amortized repeated-use cost.
3. Only if those gates pass, freeze fresh development/confirmation seeds, numerical
   error budgets, accuracy targets, total-cost matches and all-outcome criteria for
   unchanged branch, bootstrap and a suitable manual proposal. Compare equal time
   and time to predefined accuracy, not ESS alone or equal N alone. Include failures,
   derivative costs, all branch preparation and memory. Use independent posterior
   truth and separate held-out acceptance before considering promotion.

Stop if the relevant branches have negligible posterior mass, a credible parameter
sequence cannot be specified, accurate interpolation removes the cost opportunity,
or a bounded branch inventory cannot be supported. Those outcomes favor returning
to other Figaro priorities, such as higher-dimensional GVM-mixture fitting with GMM
controls. Do not expand this into general ODE integration, unknown-mode discovery,
vector filtering or pilot fitting under the relevance-screen approval.

## Reproducibility and delivery

[Java screen](../tools/ExpensiveLikelihoodRelevance.java),
[strict analysis](../tools/expensive_likelihood_relevance.py) and
[analysis controls](../tools/test_expensive_likelihood_relevance.py) are research
tooling only. Compile the screen with Java 17 and the existing Commons Math 3.6.1
JAR; run it three times in fresh JVMs with a 1 GiB heap, capturing stdout separately.
Pass the three complete JSON-line logs to the Python analysis; `--plot` produces
an SVG response plot. Use the archived original source to reproduce the failed
first revision; the current source implements the recorded numerical revision.

The evidence package preserves both protocol snapshots, both source revisions,
actual commands/exit codes/timestamps, all original logs, hashes, environment
versions, machine-readable results and plot. No random filter samples, full CI,
packaged-consumer checks or promotion validation were run in this screen.
