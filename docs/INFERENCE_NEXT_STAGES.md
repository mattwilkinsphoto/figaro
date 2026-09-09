# Inference and distribution delivery stages

Approved sequence, 2026-09-09. These are bounded milestones, not promises of
unconditional statistical guarantees. Each integration needs tests, evidence,
documentation, a version increment, and an explicit commit/CI record.

## 1. End-to-end graph cost

Compare legacy importance, bridge prior importance, pilot-fitted Gaussian and
mixture proposals, and graph multi-chain MH. Include graph construction, discarded
pilot transitions, fitting, production, diagnostics, and cleanup. Retain all seeds,
fit refusals, and inaccurate estimates. Separate observed accuracy from weight ESS.
The initial fixed-work grid measures total cost; MSE times elapsed seconds is an
efficiency indicator, **not** an observed matched-deadline speedup. Follow it with
matched-total-time trials before making a time-to-accuracy claim.

## 2. Joint blocks and query-aware proposals

Use explicit joint coordinates and their original joint prior, retaining
conditional dependencies and Jacobians. Start with a hierarchical Gaussian
control where a root-only proposal leaves an important latent variable untouched.
Use event-focused proposals with a support-covering defensive component; do not
silently replace the target with the event-conditional distribution.

## 3. Distribution breadth and mixture metrics

Start D5 with the full-rank elliptical multivariate Student t, distinct from a
product of independent univariate t variables. Add Gaussian-mixture information
metrics with a clearly labeled numerical contract; no moment-matched Gaussian
substitution or generic closed-form KL claim. Broader D5/D6 families remain listed
separately in the [roadmap](../ROADMAP.md).

## 4. Assumption-bounded reliability and concurrency

Modern.15 supplies [bounded IID stopping and declared-region checks](BOUNDED_IID_RELIABILITY.md).
The [static graph ownership design](OWNED_GRAPH_EXECUTION_DESIGN.md) is complete
as a design only; a new executor remains future work. Existing samplers are unchanged.

The requested universal endpoints cannot be accepted literally:

- **Precision stopping:** bounded independent observations admit time-uniform
  guarantees. Arbitrary heavy-tailed, dependent, self-normalized estimates do not
  inherit those guarantees. Keep existing heuristic/asymptotic policies distinct.
- **Missing modes:** finite draws cannot exclude an unseen region with arbitrary
  target mass. Known-region coverage checks can detect failures, not prove a
  complete inventory of modes. Do not turn a clean diagnostic into a certificate.
- **Thread safety:** a lock around an RNG does not isolate mutable Elements,
  caches, graph construction, evidence, or external callbacks. Concurrent owned
  Universes are the existing safe boundary. Arbitrary shared-graph execution needs
  an ownership/transaction contract; user callbacks with uncooperative mutation
  cannot be made safe by the library alone.

Implement only contracts whose assumptions can be stated and tested. Record
unsupported cases explicitly; never rename an approximation to match a guarantee.
The [follow-on feasibility assessment](RELIABILITY_LIMITS_ASSESSMENT.md) records
the source audit, mathematical limits and specific narrower acceptance gates.

## Literature baseline

- [Owen, Monte Carlo theory, Chapter 9](https://artowen.su.domains/mc/Ch-var-is.pdf):
  importance proposals, full-density corrections, defensive mixtures and
  integrand-sensitive efficiency. A long tail or an unvisited event can defeat
  otherwise plausible diagnostics.
- [Hershey and Olsen, ICASSP 2007](https://research.ibm.com/publications/approximating-the-kullback-leibler-divergence-between-gaussian-mixture-models):
  Gaussian-mixture KL approximations and bounds, not a generic analytic formula.
- [Howard et al., Annals of Statistics 2021](https://arxiv.org/abs/1810.08240):
  time-uniform confidence sequences under explicit probabilistic conditions.
- [Woodard et al., detecting poor convergence](https://people.orie.cornell.edu/woodard/Wood2011.pdf):
  multimodality can defeat conventional convergence diagnostics.
- [SciPy multivariate t reference](https://docs.scipy.org/doc/scipy/reference/generated/scipy.stats.multivariate_t.html):
  independent implementation reference for location, shape and degrees-of-freedom
  conventions. Shape is not covariance; the new Figaro kernel will initially
  require positive definiteness, not singular pseudo-density support.

No new dependency or external source code is adopted by this assessment.
