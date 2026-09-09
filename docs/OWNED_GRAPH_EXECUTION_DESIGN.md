# Restricted graph ownership: execution-model decision

Status: **accepted design; first restricted executor implemented in modern.17**.
See [static graph execution](STATIC_GRAPH_EXECUTION.md) for the supported public
vocabulary, lifecycle tests and measured costs. This is not arbitrary-graph thread safety.
The modern.15 reliability milestone documents this boundary alongside the two
[bounded IID APIs](BOUNDED_IID_RELIABILITY.md). Existing owned runners are unchanged.

## Decision and rationale

Preserve fresh-Universe factories for current graph runners. For a future reusable
parallel execution engine, prefer an immutable, restricted static model definition
with independent mutable state per run. Do not add a global graph lock or advertise
a wrapper lease as enforcing safety over existing public mutable Elements.

The audit found mutable `Element.value` and `Element.randomness`, Universe active
and evidence sets, context stacks, dependency caches and algorithm registration.
`Universe.withUniverse` selects a thread-local scope; it does not clone objects.
Dynamic Chains and user callbacks can create or mutate additional state. A user
holding an Element reference can bypass a cooperating wrapper's lock entirely.

## What developers can safely do today

1. Pass a **model factory**, not a prebuilt shared graph, to
   [multi-chain MH](MULTI_CHAIN_MCMC.md) or [graph proposal importance](GRAPH_PROPOSALS.md).
2. Construct every Element inside the runner's owned Universe. Share only immutable
   parameters and immutable proposal descriptions; keep callback state local.
3. Consume detached values/diagnostics. Do not retain Elements, mutate evidence
   concurrently, start nested algorithms or launch background graph work in a factory.

The existing graph-proposal runner rejects foreign dependencies encountered during
evaluation and nested algorithm registration, checks root/evidence invariants,
and cleans its Universe on success/failure. Existing regression tests cover these
checks and concurrent isolated replay. These are **boundary checks**, not a proof
that arbitrary closures obey the ownership contract. A stalled callback is still
not forcibly preempted.

## Static-model boundary

| Component | Shareable data | Mutable state and restrictions |
| --- | --- | --- |
| Model definition | Validated DAG, node IDs, typed scalar parameters, explicit operation tags | No Element/Universe references, arbitrary Scala callbacks, mutable arrays exposed to callers, I/O, or dynamic node creation |
| Evidence snapshot | Immutable per-node observations/constraints from an accepted restricted vocabulary | Changes create a new snapshot/run; no in-flight mutation |
| Run context | Unique run identity and immutable definition reference | Own values/randomness arrays, dependency scheduling/cache state, private RNG and cancellation state |
| Worker ownership | One run context exclusively owned by one worker at a time | No concurrent sampling or querying of mutable state; publish only immutable snapshots |
| Result | Detached query values, work counters, diagnostics and RNG provenance | No handles back into execution state |

The initial vocabulary should cover a small scalar acyclic model (constants,
Bernoulli/normal nodes and explicitly enumerated pure arithmetic operations).
Validate types, cycles, parameter domains, evidence compatibility and query IDs
before launching workers. Reject unsupported nodes explicitly rather than silently
falling back to shared Element execution. Array storage can remain internal;
public immutability must include defensive copies and no escape of state handles.

This design gains concurrency by running **independent inference work** over the
same immutable definition and different state/RNGs. It does not assume that
dependent nodes in one draw are trivially parallelizable. Shared definition memory
may reduce construction costs, but that remains a hypothesis to measure against
the existing isolated graph factories.

## Lifecycle and migration gates

The proposed state machine is `Created -> Running -> Completed/Failed/Cancelled`.
Only the owning worker advances mutable inference state. Finalization happens
once, including on callback-free numerical failure. Results become visible only
after publication; cancellation never publishes a partially successful result.
The first vocabulary avoids arbitrary callbacks, but cancellation still needs
checks between bounded numerical operations and worker tasks.

Before any implementation is promoted, require:

- Semantic parity with existing inference on analytic discrete/Gaussian controls,
  including evidence and query transformations; explicit rejection of unsupported
  dynamic Chains, learning, interventions or user functions.
- Ownership/escape tests, immutable input-copy tests, duplicate-run protection,
  and evidence-snapshot consistency while concurrent callers create new snapshots.
- Repeated race, interruption, cancellation, failure and worker-shutdown tests;
  no leaked workers, deadlocks or mutation after finalization.
- Replay for declared seed/backend/run-ID policy independent of worker scheduling;
  statistical health and estimand checks unchanged across worker counts.
- Initialization-inclusive timings, allocation/memory and effective samples per
  second against today's owned runners at 1, 2 and several workers. Retain cases
  with no speedup; do not equate worker count with speedup.
- A user migration guide and explicit supported-node inventory before exposing
  the executor. Dynamic graphs and learning require separate architecture audits.

## Alternatives not selected

A whole-graph lock serializes cooperating callers and provides no shared-graph
speedup. Fine-grained collection locks do not make evidence updates, evaluation
and cache invalidation transactional. An exclusive lease may be useful internally
for an opaque execution context, but cannot enforce participation by existing
public Element users. Process isolation can contain arbitrary application state
more strongly, at serialization and orchestration cost; it is not this milestone.

## Scope remaining

The first static definition/compiler/executor is implemented with scalar normal,
Bernoulli and closed arithmetic nodes. Tests and total-cost evidence address the
gates above for that restricted vocabulary. No mutable context handle is exposed:
each call creates a fresh context, so concurrent reuse cannot advance one context
twice. Dynamic graphs, arbitrary callbacks, learning and interventions remain
separate work. Existing owned-universe multi-chain execution remains available.
See [reliability assessment](RELIABILITY_LIMITS_ASSESSMENT.md),
[resource scaling](RESOURCE_SCALING_ASSESSMENT.md), and [roadmap](../ROADMAP.md).
