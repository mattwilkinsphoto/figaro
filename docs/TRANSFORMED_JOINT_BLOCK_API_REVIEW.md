# Caller-specified transformed joint blocks: bounded API review

**Subsequent execution:** the [frozen development study](TRANSFORMED_JOINT_BLOCK_RESULTS.md)
completed with independent arithmetic/diagnostic checks but failed ordinary
accuracy and utility gates. No confirmation or promotion; this review remains
historical design rationale, not a scheduled implementation or shipped API.

**Design recommendation, not a shipped API.** Subsequent approval authorizes the
[bounded protocol](TRANSFORMED_JOINT_BLOCK_PROTOCOL.md), not automatic promotion. This
review follows the separate diagnostic repair. It does not reopen the closed
learned-pilot campaign or include that prototype in 7.4.1. Stopping, defaults and
strict calibration remain unchanged.

## Decision and why

Recommend a small fixed-geometry facility for callers who already have a justified
covariance in transformed coordinates. Do not build another automatic fitter.
The retained study's known-covariance blocks were accurate on eight of eight
attempts for each correlated fixture. In contrast, the learned workflow delivered
33 of 40 ordinary outcomes and failed every ordinary total-cost gate. These are
specific controls, not proof of a general advantage or mixed-support readiness.

The useful capability gap is joint movement of positive/bounded and real roots.
Released Normal-only blocking cannot express it; sequential scalar support-aware
moves do not constitute one joint transformed MH proposal. The existing internal
adapter is a starting point for review, not a validated public implementation.

## Evidence and alternatives

Sources inspected 2026-10-26; no external code or data copied, installed or run.

- Parno and Marzouk, [Transport map accelerated MCMC, v4 (2017)](https://arxiv.org/html/1412.5492v4), section 3.1 and equations 20-22: a fixed coordinate map induces a value-space proposal with a Jacobian correction. We inspected that construction, not the full adaptive convergence proof. Its learned nonlinear maps and published speedups are not this proposal.
- [Stan 2.40 constraint transforms](https://mc-stan.org/docs/reference-manual/transforms.html), change of variables, lower-bounded and doubly bounded scalars, and finite-precision caveat: transformation removes mathematical support boundaries but floating-point inversion can still hit them. Documentation reference, not a Figaro execution result.
- [TensorFlow Probability TransformedTransitionKernel](https://www.tensorflow.org/probability/api_docs/python/tfp/mcmc/TransformedTransitionKernel), mathematical details: separates a coordinate map from the transition kernel and includes its forward log determinant. This is a useful contract comparison. Linked source and v0.25.0 raw source/license retrieval failed in this review; no implementation inspection or reuse clearance is claimed.
- [Geyer's mcmc metrop manual](https://www.stat.umn.edu/geyer/mcmc/library/mcmc/html/metrop.html), matrix increment scaling: a supplied matrix multiplies Normal noise. Figaro should retain its existing covariance-input convention, explicitly distinguishing covariance from this factor. Manual inspection only.

Using an external numerical engine would not implement Figaro's value/randomness
mapping, graph score updates or rollback. Reusing existing Figaro Cholesky and
scalar transforms plus a narrow dispatch is the smaller justified integration.
Keep general bijector plugins, gradients/HMC, automatic blocking and adaptation
out of this milestone. A new universal transform framework is unnecessary.

## Proposed public surface

Tentative names below are design notation, **not runnable 7.4.1 code**:

```text
TransformedJointBlock.apply(
  roots: Seq[Element[Double]],
  incrementCovariance: Seq[Seq[Double]]
): ProposalScheme
```

Require both arguments; no default covariance, guessed scale or automatic pilot.
Keep one constructor for the first milestone rather than a configurable transform
registry or calibration-result type. Runtime exact-type checks are required even
if a later ergonomic wrapper adds static types: Figaro factories already expose
base element types. Error messages must identify the root index and restriction.

| Contract | Initial recommendation |
| --- | --- |
| Roots | Two to eight distinct exact permanent constant-parameter AtomicNormal, AtomicGamma or AtomicBeta roots, all stochastic roots of one isolated universe |
| Coordinates | Normal value, log(Gamma value), logit(Beta value); root order is matrix order |
| Matrix | Fixed finite exactly symmetric numerically positive-definite covariance of coordinate increments; copy and factor once; reject singularity, no hidden jitter |
| Graph | Exact Constant, Apply1-Apply5 and Inject descendants, local fixed topology, ordinary hard conditions and log constraints; callbacks pure and returned values immutable |
| Evidence | No observed/intervened nodes, contingent evidence, mutation after sampling begins or external dependencies; all stochastic roots explicitly covered |
| Ownership | One scheme per sampler/universe and executing worker; no reuse, thread migration or concurrent mutation |
| Composition | Complete scheme only; no sequential or DisjointScheme embedding initially |
| Runners | One-time MH and owned fixed-budget MH.run only initially; anytime/resumable and runUntilPrecise execution require a later lifecycle design |
| Numeric failure | Explicit error with pre-step model state restored; never clipping, silent rejection-as-recovery or retry-until-finite |

For an increment SD multiplier s, callers multiply covariance by s squared.
An off-diagonal Gamma/Beta entry is a covariance between log and logit increments,
not the raw-value covariance. A matrix estimated on raw values cannot simply be
relabeled. Keep the existing GaussianBlockProposal unchanged for wider supported
Normal-only use; the new contract is deliberately more restrictive.

**Do not expose the research adapter's built-in defensive-mixture knobs initially.**
A fixed symmetric Gaussian random walk is the smallest useful surface. Removing
the unused mixture selection changes RNG consumption, so new reproducibility tests
must target this contract, not claim bitwise identity with the research prototype.
If the frozen utility study requires a second component, stop for a separate design
decision rather than quietly restoring an expanding configuration surface.

## Correctness and lifecycle invariants

Let x=T(z), z'=z+L epsilon, and LL^T=C. For fixed C, the coordinate random walk is
symmetric. The log acceptance ratio is

```text
log pi(x') - log pi(x) + log|J_T(z')| - log|J_T(z)|
```

Here pi includes each prior and graph evidence term once. This equation is an
independent specialization of change of variables, not a claim that a symmetric
value-space proposal can omit the Jacobian. Test it against independently computed
forward/reverse value-space densities, not a second call to the same helper.

Prepare all roots before mutating any. Make one joint accept/reject decision;
update shared descendants after all roots. Rejection restores values/randomness
and keeps the repeated state in traces. Exceptions/interruption must restore score
caches, dissatisfaction and counters as well, preserve interruption, and allow
owned-runner cleanup. RNG consumption and spent callback work are not rolled back.
External side effects and mutable objects modified in place cannot be restored;
unsupported callbacks remain an explicit caller contract, not a detectable proof.

There is a concrete integration boundary: the owned precision runner submits a
new task for each chain at successive checkpoints; it does not promise that the
same executor thread will receive a chain again. The research adapter binds to
one executing thread. Exclude this combination initially rather than silently
weakening ownership or changing stopping. Supporting checkpoint thread transfer
would require an explicit, separately validated ownership handoff design.

Review finite-precision value-to-randomness round trips at non-unit Gamma scales,
extreme Normal locations/scales and Beta endpoints. Mathematical bijectivity does
not ensure machine-representable inversion. Distinguish genuine zero evidence
(ordinary MH rejection) from invalid arithmetic (failure). Retain all failures.
No global graph lock, generic graph safety or unseen-mode guarantee follows.

## Three bounded stages and exit gates

### 1. Freeze the contract and deterministic independent controls

Before coding a public API, record exact scope, fixture definitions, matrices,
truth/tolerances, query set, seeds and costs in a new versioned protocol. Reconcile
the internal adapter's 19 historical controls with this simpler constructor;
these are prior evidence and must be rerun against the integrated candidate.

Required controls: fixed-noise acceptance/rejection against analytic densities;
Normal-only arithmetic comparison; independent SciPy Gamma/Beta density and
Jacobian calculations; omitted/doubled/sign-reversed Jacobian negative controls;
permuted roots/matrix; non-unit scales; singular/near-singular matrices; endpoint
rounding; nonfinite scores; early hard-condition rejection; repeated rejection;
failure after downstream mutation; interruption; cleanup; wrong owner, extra root,
graph/evidence/parameter mutation and unsupported composition. Probe dimensions
2, 4 and 8 and reject 1 and 9. Use independent matrix calculations for numerical
controls; shared production helpers are not independent references.

Stop on any unresolved correctness, rollback or ownership failure. Broader graph
support, custom distributions or changed failure semantics require another review.

### 2. Bounded fixed-budget utility and robustness study

Use six fixtures: factorized mixed support at 2D and 8D, correlated Normal 2D,
coupled Gamma/Beta 2D, curved mixed support, and separated modes. Preserve adverse
cases even when the method is expected to struggle. Compare four arms: released
default, the existing joint-prior proposal (`ProposalScheme(roots*)`), explicitly
fixed scalar moves and the new fixed joint move. Joint-prior resampling already
moves multiple roots together and is an important inexpensive control; scalar
proposals alone would not isolate the benefit of transformed covariance. No pilot,
matrix fitting, parameter search or adaptive retries in this study. Independent
analytic/quadrature truth and query tolerances must precede stochastic execution.

Proposed cap: eight fresh development seeds, four chains, 12,000 transitions per
chain including 4,000 discarded warm-up; 192 development attempts. These are study
budgets, not library defaults. Reserve 32 new confirmation seeds before running;
do not consume any old closed campaign's seeds. At most 768 confirmation attempts
if development passes. Each phase has a four-hour/12-GiB/4-GiB output cap. A cap hit
is incomplete and stops the phase; no budget extension to secure a pass.

Record raw and transformed moments, variances, narrow contrasts and scientific
event probabilities; R-hat/bulk/tail/mean ESS and MCSE; every failure/refusal;
total evaluations and end-to-end time including construction, diagnostics and
retained evidence. Recompute the diagnostics independently. Charge any external
geometry estimation separately; analytic/oracle matrices must be labeled as such,
not presented as free learned geometry. This first study tests capability with
supplied geometry, not an autonomous workflow.

Recommended engineering gates to freeze: no correctness or lifecycle failure;
8/8 development outcomes on every ordinary fixture meet every predeclared accuracy
tolerance; at least 30/32 on each ordinary fixture in confirmation; no statistical
gate relaxation. On both correlated ordinary fixtures require at least a 20%
reduction in worst-query mean squared tolerance-normalized error times mean total
seconds relative to the better complete released control; on each factorized
fixture require a ratio no greater than 1.25. Missing outcomes incur loss one per
query and retain all spent cost; numerical/execution errors additionally fail the
correctness gate. Incomplete controls or zero denominators are inconclusive.
Report delivered-only error, delivery probability and uncertainty across seeds
alongside that engineering score. Adverse errors and mode occupancy remain visible;
high ESS does not override wrong answers. No claim of reliable automatic danger
detection is part of this fixed proposal API.

Any failed ordinary utility/accuracy or independent audit gate ends this bounded
study without promotion or confirmation. Do not select successful fixtures or
add a learned/defensive rescue arm. Published and historical failures stay intact.

### 3. Separate promotion review

Only after fresh confirmation and independent audit pass: review packaged-consumer
examples, all existing MH/calibration/stopping regressions, binary/TASTy compatibility
and full remote CI. Validate one worker and multiple owned workers, including
failure cleanup; do not claim thread safety beyond those isolated models. Document
old versus new approaches, covariance units, all-root scope and unsuitable cases.
Keep automatic pilot learning closed. Any eventual compatible public API is a
minor-release candidate, not part of the 7.4.1 diagnostic patch.

## What this review establishes

Source and literature inspection supports this bounded design; it does not establish
new runtime validation or performance gains. The strongest counterevidence is the
previous failed end-to-end learned workflow and the limitations of local geometry
on curvature/modes. Stop or park the API if fixed caller geometry cannot deliver
useful total-cost gains under the unchanged protocol. Do not escalate automatically
to a larger optimization campaign.

[Research provenance](RESEARCH_REFERENCES.md#inf-22).
