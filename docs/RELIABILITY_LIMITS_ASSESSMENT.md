# Reliability guarantees and graph concurrency: feasibility assessment

This is the follow-on assessment requested after the modern.14 proposal/distribution
stages. The first three stages have concrete implementation and evidence; the
three universal endpoints below are **not completed features**. Replacing them
with apparently reassuring tests would weaken the inference contract.

## 1. Automatic precision stopping

Existing `McmcPrecision` and `runUntilPrecise` already provide guarded automatic
stopping under an asymptotic functional-CLT/variance-estimation argument. Their
reported precision is not a finite-run or missing-mode guarantee. The importance
[calibration study](IMPORTANCE_CALIBRATION.md) explicitly found undercoverage;
the modern.14 rare-event study again shows zero empirical MCSE on missed events.

A defensible **finite-sample** next implementation is an opt-in bounded-IID mean
runner, not a replacement for all samplers. For observations in a known interval
[a,b], an elementary baseline spends alpha_n=alpha/[n(n+1)] at sample n. Hoeffding
plus a union bound gives the time-uniform radius

`(b-a) sqrt(log(2 n(n+1)/alpha)/(2 n))`.

Since the spending sums to alpha, the simultaneous real-arithmetic coverage is at
least 1-alpha under the declared assumptions. Stop only when this radius reaches
the absolute target, or report budget exhaustion. More efficient empirical-
Bernstein/betting confidence sequences deserve comparison before promotion.

Required acceptance gates:

- Explicit IID (or suitable conditional-mean) contract, known support, finite
  budgets and a distinction between mathematical bounds and floating-point error.
- Bernoulli/bounded continuous, near-boundary rare-event and adversarial constant-
  prefix controls; optional-stopping coverage across held-out seeds.
- No direct reuse for correlated MCMC or self-normalized importance. A bounded
  importance ratio and denominator treatment need their own theorem and API.
- No claim that every legal model reaches requested precision within a user budget.

Primary baseline: [Howard et al., 2021](https://arxiv.org/abs/1810.08240). Their
time-uniform framework has assumptions; "time-uniform" does not mean distribution-
free reliability for arbitrary unbounded dependent output.

## 2. Guaranteed missing-mode detection

With n independent draws, a region of mass epsilon is missed with probability
`(1-epsilon)^n`. This is strictly positive for every finite n and 0<epsilon<1.
Without a lower bound on relevant region mass, finite simulation cannot exclude
unvisited regions. A tiny distant component can also change a mean greatly while
being absent from all traces. More draws, multiple RNGs and good R-hat cannot
remove that information limitation. A region/mode must first have a mathematical
definition; fitted components and density modes are not interchangeable.

Feasible next capability: caller-declared region occupancy diagnostics with
overdispersed independent starts and cross-run/proposal comparisons. If K relevant
regions are known beforehand and each has sampling probability at least p_min,
the probability of missing any is at most `K (1-p_min)^n`. This is a conditional
probability bound for that known inventory and sampling law, **not** discovery of
unknown modes or a posterior-mass bound from proposal occupancy. Estimated p_min
cannot silently replace an externally justified lower bound.

Acceptance must include a deliberately missed distant mode whose within-mode
traces pass ordinary diagnostics. [Woodard et al.](https://people.orie.cornell.edu/woodard/Wood2011.pdf)
discuss the failure of conventional convergence checks in multimodal settings.
No `AllModesFound` status should be exposed for arbitrary models.

## 3. Generic thread safety for arbitrary graphs

The source audit identifies shared mutable active/conditioned/constrained sets,
context stacks and dependency/cache state in `Universe`; mutable values and
randomness in Elements; dynamic graph construction in Chains; and external code
inside user functions. `RandomContext` and the default Universe scope are
thread-local, but these scopes do not copy or isolate shared Element objects.

Existing parallel runners use owned model factories, separate Universes/RNGs,
detached results, cancellation and cleanup. These are the safe execution boundary.
Modern.14 adds immutable conditional descriptions and tests isolated concurrent
metric execution; it does not make mutable callbacks safe.

Putting a single lock around a whole graph could serialize **cooperating** calls,
but gives no shared-graph speedup. Locking individual collections would not make
multi-step sampling, evidence updates, cache invalidation and user callbacks one
consistent transaction. Public mutable Element state also prevents a wrapper from
enforcing participation by every caller. External I/O and uncooperative callbacks
cannot be made safe by an internal graph lock.

The next useful architectural milestone is a **restricted immutable model
definition plus per-run mutable execution state**, or a clearly exclusive graph
ownership/lease contract. Start with pure static graphs, then audit dynamic Chains
and learning separately. Required gates include races, deadlocks, cancellation,
evidence consistency, ownership violations and real scaling against existing
isolated runners. This is an execution-model project, not a promise that arbitrary
existing graphs become parallel-safe without restrictions or migration.

## Decision

Do not ship a universal precision/mode/safety claim. The next implementable work
requires choosing and accepting these narrower public contracts. Keep existing
stopping defaults and graph ownership rules unchanged until those contracts have
their own research, tests, documentation and versioned integration gate.

Related: [staged plan](INFERENCE_NEXT_STAGES.md), [inference health](INFERENCE_HEALTH.md),
[multi-chain MCMC](MULTI_CHAIN_MCMC.md), [graph proposals](GRAPH_PROPOSALS.md),
[cost evidence](GRAPH_COST_ACCEPTANCE.md).
