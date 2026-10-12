# Distribution and inference integration

7.0.0 release preparation, not yet a publication announcement. Track remaining gates
in the [execution record](INFERENCE_INTEGRATION_STATUS.md).
Research provenance: [INF-04](RESEARCH_REFERENCES.md#inf-04),
[INF-05](RESEARCH_REFERENCES.md#inf-05).

## Modernization bridge: old and new usage

The original tutorials still explain models and inference. These examples replace
their numerical and extension assumptions; they do not require rewriting a model
just to adopt log-space filtering. Imports and full lifecycle examples follow below.

### Filtering and zero-weight evidence

Previously, the usual factory silently retained an unweighted population if every
particle weight became zero. Ordinary-space weights could also round a very small
but possible likelihood to zero. In 7.0 the same factory uses log weights and fails
explicitly if all particles genuinely have zero likelihood:

```scala
// Old and new factory syntax is identical:
val filter = ParticleFilter(initial, transition, numParticles = 10000)
// Old: probEvidence() could underflow; all-zero weights silently fell back.
// New: retain log evidence and do not query a failed population as a posterior.
try {
  filter.start()
  println(filter.getlogProbEvidence())
} finally {
  if (filter.isActive) filter.kill()
}

// Explicit 7.0 compatibility choice, ONLY if unconditioned fallback is intended:
val compatibilityFilter = ParticleFilter(initial, transition, 10000,
  ParticleFilter.ZeroWeightPolicy.LegacyUniform)
```

For example, a Laplace(0,1) observation of 1000 has log density
`-1000 - log(2)`, finite even though its ordinary density rounds to zero.
The new default preserves that distinction. A `NoViableParticles` exception is
not proof that evidence is impossible: the particle budget/proposal may have missed
its support. Discard a failed filter, investigate, and build a fresh one rather than
catching the exception and querying stale state. This applies to both filter factories.

### Custom filter and MH extensions

For a filter subclass, port an override of `makeWeightedParticle` to
`makeLogWeightedParticle` (same arguments, `LogWeightedParticle` return type).
Likewise port `initialWeightedParticle` / `addWeightedParticle` to their `Log`
counterparts. The following shows the return expression inside such an override;
`state` is the generated particle state and `logLikelihood` its directly computed score:

```scala
// Old ordinary-weight hook body:
(math.exp(logLikelihood), state)
// New log-weight hook body:
ParticleFilter.LogWeightedParticle(logLikelihood, state)
```

Do not exponentiate and then take a logarithm: that reintroduces the underflow.
The old `WeightedParticle` alias still means ordinary weight, not log weight.

For a custom element with `Randomness = Double`, the equivalent proposal-hook
change is below. `drawProposal`, `logReverse`, `logForward`, and `logPrior` denote
the subclass's existing proposal and scoring functions, not new Figaro APIs:

```scala
// Old implementation: finite log terms can produce overflowing ratios.
override def nextRandomness(old: Double): (Double, Double, Double) = {
  val next = drawProposal(old)
  (next, math.exp(logReverse(old, next) - logForward(old, next)),
    math.exp(logPrior(next) - logPrior(old)))
}
// New implementation of the same kernel; no intermediate exponentiation.
override def nextLogRandomness(old: Double): (Double, Double, Double) = {
  val next = drawProposal(old)
  (next, logReverse(old, next) - logForward(old, next),
    logPrior(next) - logPrior(old))
}
```

Keep proposal and model terms separate, even if they cancel in ordinary MH:
annealing scales the model term differently. Existing ordinary-ratio overrides
remain bridged, but a bridge cannot repair an already infinite ratio.

### Observations, exact counts, and local proposals

In the owned multi-chain builder, a manually attached likelihood remains valid:

```scala
// Earlier workaround inside the owned builder; includes the density normalizer:
val mean = Normal(0.0, 1.0)(using "mean", u)
mean.addLogConstraint(m => -0.5 * math.log(2 * math.Pi) - 0.5 * math.pow(1.0-m, 2))
// New alternative: a supported conditional observation, with the same likelihood.
val mean = Normal(0.0, 1.0)(using "mean", u)
Normal(mean, 1.0)(using "measurement", u).observe(1.0)
```

These are alternatives in separate models, not two blocks to combine. Combining
them counts the same evidence twice. Supported observation shapes remain bounded;
an arbitrary observed `Apply` is not automatically a continuous likelihood.

```scala
val law = HypergeometricDistribution(20, 7, 5)
// Existing sampling adapter: not a promise of exact support in a factored algorithm.
val sampled = CountElement(law)
// New opt-in finite enumeration for exact variable elimination:
val enumerated = CountElement.exact(law)

val size = ScalarElement(LogNormalDistribution(0, 1))
// Existing MH prior proposals remain the default.
// New opt-in move for tight evidence on a positive parameter:
val move = SupportAwareProposal.positive(size, stepSd = 0.1)
// Pass move as the sampler's proposal, or Some(move) in the owned MH.Model.
```

Enumeration refuses oversized tables rather than silently truncating them. Local
proposals can help concentrated posteriors, but can mix worse than prior proposals
without informative evidence. Inspect diagnostics and freeze tuning before the
production run. Neither feature changes existing defaults automatically.

Finally, formerly refused values such as extreme-value shape `1e-12` or 2D Wishart
df `2.0` now have validated implementations. The range table below lists retained
limits; this is not permission to extrapolate to singular matrices or every tail.

## Stable particle filtering details

The sequential and parallel particle-filter factories retain likelihood weights
in log space, subtract the maximum before exponentiation for resampling, and
accumulate log evidence directly. A finite likelihood no longer becomes impossible
solely because its ordinary floating-point density underflows. This does not cure
particle impoverishment, poor proposals or an inadequate particle budget.

Use the existing factory and lifecycle; no numerical-mode switch is needed:

```scala
val filter = ParticleFilter(initial, transition, numParticles = 10000)
filter.start()
println(filter.getlogProbEvidence())
// Query the posterior, advanceTime as needed, then kill() in finally.
```

Prefer `getlogProbEvidence()` over `probEvidence()` for extreme magnitudes.
The latter necessarily exponentiates and can still round to zero or infinity.
Continuous-observation evidence is a density, not an event probability.

If every particle has log weight `-Infinity`, `NoViableParticles` stops the update.
Posterior queries after that failed update throw rather than presenting rejected
particles as a conditioned posterior. This can mean impossible evidence **or** a
finite particle population that missed the compatible region. Inspect the model,
evidence, proposal and particle budget; do not automatically classify the model
as impossible. NaN and positive-infinite input log weights are invalid.
After any failed initialization/update, discard the filter and construct a fresh
one. Updates are not transactional: neither evidence queries nor further advances
are allowed to conceal a partially modified particle population.

The explicit compatibility option is:

```scala
val filter = ParticleFilter(initial, transition, 10000,
  ParticleFilter.ZeroWeightPolicy.LegacyUniform)
```

This retains an unweighted particle population when all weights are genuinely
zero. It is not a recovered posterior. Log evidence remains `-Infinity`. The
parallel factory exposes the same policy as its last argument.

### Extension migration

`WeightedParticle` remains an ordinary-weight tuple. It has **not** been silently
reinterpreted as a log weight. Built-in filters now use `LogWeightedParticle` and
the protected `makeLogWeightedParticle`, `initialLogWeightedParticle` and
`addLogWeightedParticle` hooks. Subclasses overriding the old ordinary-weight
hooks must port those overrides to the log hooks. This extension-contract change,
along with the strict failure default, is tracked as major-version development.

## Stable MH proposal interface

`Element.nextLogRandomness(old)` returns `(next, logProposalRatio, logModelRatio)`.
The second term is log reverse/forward proposal probability; the third is log
new/old model density. MH consumes these directly. Keeping them separate preserves
annealing semantics: prior-proposal cancellation in ordinary MH must not erase
the model term before temperature scaling.

The default implementation calls the existing `nextRandomness` override and logs
its ratios. Existing custom proposals are therefore retained. This compatibility
bridge cannot rescue an already overflowed legacy ratio; a custom implementation
with extreme ratios must implement the log hook directly.

`HasLogPriorProposal` explicitly opts an element into independent prior proposals
computed from finite log densities. The final modernization distribution adapters
use it. It is deliberately not added to `HasLogDensity` automatically, which would
bypass legacy subclasses' custom `nextRandomness` implementations. Legacy
non-final normal/gamma/beta and discrete local-proposal classes retain the bridge;
this milestone does not silently replace their proposal kernels. Non-finite
starting densities or unrepresentable log differences still fail explicitly.

## Observations in owned multi-chain MH

The owned runner can now score a permanent observed atomic density, a conditional
`Flip(p)`, or a permanent `Chain` creating exactly one fixed-parameter atomic
likelihood child. Its observation stays fixed while the parents move. The score
includes the likelihood normalizer, which matters when scale/shape is inferred.

Quick start (three steps):

```scala
import com.cra.figaro.algorithm.sampling.parallel.MultiChainMetropolisHastings as MH
import com.cra.figaro.library.atomic.continuous.Normal

// 1. Choose an explicit sampling budget.
val config = MH.Config(chains = 4, drawsPerChain = 10000, warmUp = 1000)
// 2. Build a fresh model inside each chain's universe.
val result = MH.run(config) { (u, _) =>
  val mean = Normal(0.0, 1.0)(using "mean", u)
  Normal(mean, 1.0)(using "measurement", u).observe(1.0)
  MH.Model(Vector(MH.Observable("mean", mean)(identity)))
}
// 3. Inspect convergence/precision, not only the estimate (analytic mean: 0.5).
println(result.diagnostics("mean"))
```

The existing `Config`, `Model`, `Observable` and `run` signatures are unchanged;
see their [API reference](MULTI_CHAIN_MCMC.md). Log constraints remain a valid
alternative, but do not attach both equivalent evidence forms: that double-counts
the likelihood. This does not change legacy single-chain observation semantics.

Limitations: attach all evidence before returning `Model`. No changing observations,
observed temporary roots, observed-and-intervened roots, shared observed children,
nested child graphs or extra latent nodes inside an observed Chain callback.
An arbitrary `Apply(...).observe(...)` does not acquire a likelihood/Jacobian.
Only the root owns evidence; its temporary atomic child is clamped internally and
not scored twice. Zero-likelihood proposals reject; bounded initialization fails
if no finite-likelihood state is found. An entirely fixed/observed model with no
latent stochastic proposal choices should use exact evaluation. Custom proposals
must preserve detailed balance and cannot switch observed randomness.

## Exact finite-count tables

Ordinary `CountElement` and `Multinomial` remain sampling adapters. Explicit
`exact` factories enumerate the complete finite law into the existing `AtomicSelect`
backend, so variable elimination uses a finite factor rather than sampled support.
No replacement inference engine or silent truncation is introduced.

```scala
import com.cra.figaro.language.*
import com.cra.figaro.library.atomic.discrete.*
import com.cra.figaro.algorithm.factored.VariableElimination

given Universe = Universe.createNew()
val x = CountElement.exact(HypergeometricDistribution(20, 7, 5))
x.addCondition((k: Int) => k >= 2)
val inference = VariableElimination(x)
try { inference.start(); println(inference.probability(x, 2)) }
finally inference.kill()
```

| Public factory | Parameters | Returns / example |
| --- | --- | --- |
| `FiniteCountInference.Budget` | `maxStates=10000` (1–100000), `maxCells=1000000` (1–10000000) | Immutable enumeration guards; `Budget(100,1000)` |
| `FiniteCountInference.count` / `CountElement.exact` | Fixed finite `CountDistribution`, optional budget, contextual name/collection | `AtomicSelect[Int]`; `CountElement.exact(law, budget)` |
| `FiniteCountInference.multinomial` / `Multinomial.exact` | Fixed `MultinomialDistribution`, optional budget, contextual name/collection | `AtomicSelect[Vector[Int]]`; `Multinomial.exact(MultinomialDistribution(3,Vector(.2,.8)))` |

Finite mixtures work through their `CountDistribution` support; positive-weight
unbounded components are refused. Multinomial enumerates whole joint count vectors,
not independent marginal counts. Zero-probability categories are retained as zero
coordinates without exploding the composition count. Preflight precedes registration.
Budgets bound this atomic table only, not downstream factor products or graph treewidth.
Finite masses that underflow during conversion are refused rather than silently lost;
ordinary Double factor arithmetic can still underflow in a much larger model.

## Support-aware MH proposals

`SupportAwareProposal` is a fixed-scale, opt-in family, not automatic tuning. The
default prior-independence proposal remains available. Each factory returns a
`ProposalScheme`; use it in ordinary MH or `MH.Model(..., proposal=Some(scheme))`.
Targets must be active permanent fixed-kernel elements owned by the sampler,
unobserved and unintervened. A scheme updates one entire target, including a whole
matrix. Variables outside that target require other moves in a state-independent
`DisjointScheme`; otherwise they are frozen.

| Factory | Supported target | `stepSd` default and meaning |
| --- | --- | --- |
| `positive(element, stepSd)` | `AtomicScalar` with support `(0,+Infinity)` | 0.5; SD in log coordinates, not value units |
| `bounded(element, stepSd)` | `AtomicScalar` with finite distinct endpoints | 0.5; SD in logit coordinates |
| `circular(element, stepSd)` | `AtomicVonMises` | 0.5 radians; wrapped-normal increment |
| `covariance(element, stepSd)` | `AtomicWishart` or `AtomicInverseWishart` | 0.1; log Cholesky diagonal and raw off-diagonal coordinates |
| `correlation(element, stepSd)` | `AtomicLKJ` | 0.2; unconstrained partial-correlation coordinates |

All scales must be positive finite. Returns are corrected MH schemes; invalid
construction/execution arguments throw `IllegalArgumentException`. Density guards
can also throw `ArithmeticException`. No sequential composition or `constraintsBound`
early-score optimization; use the whole scheme or a DisjointScheme choice.
Legacy Gamma/Beta and arbitrary custom/transformed elements are not accepted by
these factories. Hierarchical parameters need separate validated moves; this is
not a generic manifold sampler or HMC.

Three common patterns:

```scala
// Positive parameter under tight multiplicative evidence:
val size = ScalarElement(LogNormalDistribution(0, 1))
size.addLogConstraint(x => -0.5 * math.pow((math.log(x)-2)/0.05, 2))
val sizeMove = SupportAwareProposal.positive(size, 0.1)

// Angular evidence near the -Pi/Pi seam: never use a clipped real-line walk.
val angle = VonMises(math.Pi, 2)
val angleMove = SupportAwareProposal.circular(angle, 0.4)

// Correlation prior: the move preserves exact unit diagonal and includes the Jacobian.
val correlation = LKJ(LKJDistribution(3, 2))
val correlationMove = SupportAwareProposal.correlation(correlation, 0.2)
```

For a symmetric random walk in coordinates `y`, with `x = g(y)`, the proposal
correction is `log|J(new)| - log|J(old)|`; the model density ratio stays separate.
This preserves annealing temperature semantics. Circular wrapping is symmetric
and has zero log correction. Covariance and correlation Jacobians use independent
matrix entries, not a density on all redundant symmetric entries.

Tune on pilot runs and freeze the choice before production; inspect ESS/second,
R-hat, MCSE and posterior controls. A tiny scale moves slowly; a huge one rejects
or reaches numerical boundaries. Roundoff collapse, singularity and existing
matrix conditioning refusals stop explicitly: no hidden clipping, jitter or redraw.
Prior proposals can be substantially better without informative evidence. The
seeded comparison in `SupportAwareProposalStudy` includes both cases; its analytic
fixtures do not predict speedups for arbitrary applications.

## Selective numerical-range expansion

| Family | New development range | Retained boundary |
| --- | --- | --- |
| Hypergeometric | Population up to 1e9, support width at most 100000 | Width guard bounds cumulative work; not unrestricted billion-state enumeration |
| GEV / GPD | Shape zero or absolute shape in `[1e-14,2]` | Existing scale, endpoint and singular-density checks; SciPy GEV has opposite shape sign |
| Wishart / inverse-Wishart | Real df in `[dimension,1e6]` | Mathematical `dimension-1 < df < dimension` still refused; full-rank conditioning checks unchanged |

Near-zero shapes reuse stable `log1p`/`expm1` formulas and the existing moment
expansions, checked against an independent series. Larger hypergeometric laws
reuse Commons Math, checked against exact integer-combinatorial masses. Matrix
boundaries have scalar chi-square/reciprocal-chi-square and finite-draw checks.
These tests are not proof of reliability over every dimension/conditioning regime.
LKJ near-singularity, negative-binomial Int tails, high-dimensional integration and
general exact continuous factor inference retain their documented limitations.

Related: [distribution support](DISTRIBUTION_SUPPORT.md),
[multi-chain MH](MULTI_CHAIN_MCMC.md), [inference health](INFERENCE_HEALTH.md).
