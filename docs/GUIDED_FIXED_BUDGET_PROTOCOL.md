# Guided fixed-budget filtering: frozen protocol v1

[Research provenance](RESEARCH_REFERENCES.md#inf-16).

Frozen before implementation and benchmark outcomes. This is a bounded extension
of 7.3.0, not a change to the released random-stopping estimator. No tuning on
confirmation outcomes, automatic cutover, new defaults or universal superiority claim.

## Stage 1: accounting and design

Let W_i be the previous normalized weights, p the true initial/transition law,
q the supplied normalized proposal and g the new likelihood. Each successful step
draws exactly N states. Raw weights, whose sum is the evidence increment, are:

| Step | Raw offspring weight |
| --- | --- |
| Initial | p(x) g(x) / (N q(x)) |
| Carry, offspring i from parent i | W_i p(x\|i) g(x) / q(x\|i) |
| Ordinary resampling from W | p(x\|i) g(x) / (N q(x\|i)) |
| Auxiliary resampling from r | W_i p(x\|i) g(x) / (N r_i q(x\|i)) |

Auxiliary r is the existing full defensive mixture of W and normalized W*eta.
Use retained logs, not rounded ordinary weights. Corrections use the full mixture,
not the selected component. No extra predictive normalizer is needed: W_i/r_i
already includes it. Normalize once and accumulate the log of the raw sum.
On carry steps, do NOT evaluate eta or apply W_i/r_i. The existing policy still
uses previous completed posterior ESS, not auxiliary ESS. Initialization has no
ancestors. Guidance must preserve target support; checking sampled points cannot
prove global support. Failure invalidates the filter, without retries or fallback.

Reuse WeightedParticleProposal.Kernel and AuxiliaryParticleProposal.LookAhead.
Add explicit overloads and a separate work report; preserve existing Config,
Update and StepReport constructor shapes and ordinary bootstrap RNG order. Count
top-level target/proposal density calls separately from likelihood/ancestor work;
user callback internals need their own instrumentation. A fixed N is not fixed time.

Primary review: Doucet/Johansen tutorial sections 4.1/4.2; Pitt/Shephard 1997
working-paper section 3.1 (augmented parent/child law); Johansen/Doucet 2008
sections 2.2/2.3 (tail and variance cautions). The Harvard mirror timed out; the
Oxford working paper was accessible. This is a relevant-section review, not a
claim to have checked every proof. The MIT-licensed particles core/GuidedPF/APF
implementation was inspected on 2026-10-22; no external code copied. Its trigger
uses auxiliary ESS, a deliberate difference from our unchanged posterior-ESS
policy. No new dependency or new proposal-fitting algorithm is needed.

## Stage 2: implementation gates

Exact finite N=2 enumeration with non-prior proposals, both ancestor methods,
carry/Always/ESS schedules, defensive and inaccurate auxiliary scores. Independent
Python Fraction oracle checks the full unnormalized measure; Scala control checks
runtime weights/evidence against directly calculated formulas. Scalar and vector
Gaussian conditional identities, bootstrap replay, finite/-Infinity support,
overflow, callback failure, interruption, reentrancy, thread ownership, close,
explicit-policy enforcement and callback counts must pass. Existing filtering and
stratification regressions must pass. No released stopping source or defaults change.

## Stage 3: independent confirmation and promotion

Use the six unchanged INF-15 fixtures and independent references: growth,
growth-sharp, persistent-modes, late, gaussian, switching. Fresh seeds 70001..70256;
three fresh serial JVMs, two excluded warm-ups -302,-301 per configuration/fixture.
Same seeds across JVMs are 256 replications, not 768. Rotate order by seed.

Fixed configurations (labels are protocol identifiers, not recommended defaults):

- b-m-a: bootstrap N128, multinomial Always.
- g-m-a / a-m-a: guided / auxiliary+guided N128, multinomial Always.
- b-s-50 / g-s-50 / a-s-50: N128, stratified, previous ESS/N below .5.
- bad-s-50: a-s-50 with look-ahead log score -2 times the frozen approximation.
- big-s-50: bootstrap N512, stratified ESS .5 (larger-work comparison).
- old-guided / old-auxiliary: unchanged weighted bounded filter, min=max=128,
  scoreTarget=32, cappedLikelihood(0), existing guided/auxiliary interface.

Reuse the frozen INF-12/15 proposal recipes, not their former seed outcomes. No
candidate search or fitting. Count draws, likelihoods, target/proposal densities,
guide construction, look-ahead, ancestors and component densities; record total
wall time separately. Larger N and equal N are not automatically equal cost.

Acceptance gates:

1. Complete exact expected row matrix, retain every failure and subsequent unavailable
   step. Any refusal/error blocks promotion; no success-only accuracy ranking.
2. Scientific rows replay across all three JVMs (timing excluded).
3. For every complete configuration/fixture, first/middle/final unnormalized
   evidence, mean, second moment and positive mass agree with independent reference
   within 6 estimated standard errors + 1e-4*max(1,abs(reference)). These are
   regression screens, not precision guarantees. Inaccurate guidance must retain
   accounting correctness; efficiency degradation is expected and must be reported.
4. Report integrated positive-mass RMSE, depletion outside [.01,.99] on reference
   steps with positive mass in [.1,.9], final moments, and MSE times total counted
   work. A candidate is useful on a named fixture only if integrated RMSE is at
   least 10% lower than b-s-50 AND MSE*work is at least 10% lower; compare big-s-50
   and old alternatives descriptively, without choosing winners by wall time alone.
   Recommend general opt-in promotion only if at least one non-poor candidate meets
   both utility gates on at least two of growth/growth-sharp/persistent-modes/late,
   with depletion no worse by more than 5 of 256 runs on those fixtures. Otherwise
   retain as unpromoted work and explain the tradeoff. No automatic defaults follow.
5. Rerun the unchanged continuous regression, relevant released-filter tests,
   documentation checks and isolated packaged consumer. Remote CI and maintainer
   promotion approval remain separate gates before merge/release.

Resource ceiling: 12 hours serial, 6 GiB JVM heap, 5 GiB evidence. Preserve exact
sources, protocol/input hashes, commands, versions, real logs/errors, timestamps,
all outcomes and machine-readable analysis plus plots in a reproducibility archive.
Prior protocols/results stay unchanged. Failed gates are reported, not retuned away.
