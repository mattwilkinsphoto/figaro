# Modeling and algorithm-extension companion

## Why this guide exists

The original Figaro tutorials contain much more than installation instructions. This
companion brings that learning path into the modern documentation and links each topic
to maintained source/signatures. It is adapted from the original tutorial authors'
explanations, with modernization caveats; see [attributions](../FigaroAttributions.txt).
The complete [historical PDFs](../doc/archive/README.md) and LaTeX chapters are preserved
for their detailed derivations and examples, not presented as Scala 3 executable recipes.

**Coverage is not certification.** The three [classic walkthroughs](TUTORIAL.md) are
compiled and checked in CI. Links to advanced examples below establish where to study
the feature, not that each model's numerical behavior, scalability, or all old snippets
has been newly validated. Consult the [migration limits](MIGRATION.md).

Quick start: run [ClassicTutorials](TUTORIAL.md), select a topic below, then compare its
current source/API with the original chapter before adapting it. Application models
remain in application repositories, not in Figaro's library or documentation.

## Representation and composition

An `Element[T]` describes uncertainty over a Scala value; it is not itself a sampled `T`.
Atomic elements implement distributions; compound elements express dependence between
other nodes. `Apply` transforms values with a Scala function, while `Chain` chooses a
conditional element. `Select` chooses values and `Dist` chooses component distributions.
Reusing one node expresses shared uncertainty; constructing two nodes can express two
different draws. This distinction matters more than whether the constructors look alike.

For example, `Apply(x, (n: Int) => n + 1)` depends on the same `x` each time that node is
evaluated. A `Chain(flag, (b: Boolean) => Flip(if (b) 0.9 else 0.1))` specifies a
conditional distribution. These are fragments, not standalone programs: use the imports,
universe and inference lifecycle in the [API guide](API_GUIDE.md). Functions used inside
models should not perform unrelated side effects or assume a particular evaluation order.

Processes index potentially infinite families of elements. Containers have finite index
sets and support maps, folds and aggregates. They are model structures, not simply Scala
collections. Preserve lazy evaluation and memoization when adapting old `Stream`/collection
examples. See [collection APIs](api/com.cra.figaro.library.collection.md) and
[LazyList.scala](../FigaroExamples/src/main/scala/com/cra/figaro/example/LazyList.scala).

## Evidence, mutable models, names and references

An observation fixes a node's value; a condition rejects values; a constraint supplies
relative weights. A logarithmic constraint supplies log weights, not ordinary weights.
An intervention changes a causal mechanism rather than merely conditioning on an outcome.
Do not interchange these operations just because both can make a variable take one value.
Impossible evidence has no normalized posterior. For a continuous node, density at one
point is not probability mass at that point.

The old mutable-field examples remain useful for constructing object models. Mutating a
Scala field does not necessarily replace all existing Figaro dependencies or invalidate
an algorithm's internal state. Finish inference and construct a fresh scenario unless
the specific API documents an update operation. Thread isolation in modern samplers
does not make external mutation of a shared universe safe.

Names identify elements within an `ElementCollection`; references can traverse named
relationships. Multi-valued references represent uncertainty over collections of related
objects, and aggregates combine the referenced values. Avoid ambiguous repeated names.
See [language APIs](api/com.cra.figaro.language.md),
[MutableMovie](../FigaroExamples/src/main/scala/com/cra/figaro/example/MutableMovie.scala)
and [multi-valued references](../FigaroExamples/src/main/scala/com/cra/figaro/example/MultiValuedReferenceUncertainty.scala).

## Relational, hierarchical and open-universe models

Scala classes express objects and relationships; Figaro nodes express uncertain attributes,
types and references. The hierarchy example demonstrates type uncertainty. Open-universe
models additionally represent uncertainty in the number or existence of objects; evidence
can change which latent explanations are plausible. Do not collapse those possibilities
to a single fixed object graph merely to make parallelism easier.

Study [CarAndEngine](../FigaroExamples/src/main/scala/com/cra/figaro/example/CarAndEngine.scala),
[Hierarchy](../FigaroExamples/src/main/scala/com/cra/figaro/example/Hierarchy.scala),
[OpenUniverse](../FigaroExamples/src/main/scala/com/cra/figaro/example/OpenUniverse.scala)
and [Sources](../FigaroExamples/src/main/scala/com/cra/figaro/example/Sources.scala).
Dependent universes represent explicitly connected submodels, not interchangeable worker
contexts. Check ownership, evidence propagation and cleanup before using custom proposals
or modern isolated-chain runners with such models.

## Choosing an inference question and algorithm

| Question or strategy from the tutorial | Meaning and present entry point |
| --- | --- |
| Ranges/support | Determine represented outcomes before factor construction; infinite support needs bounded/lazy treatment. [Lazy factored API](api/com.cra.figaro.algorithm.lazyfactored.md) |
| Variable elimination | Sum out non-query variables in a factored model; intermediate factors may be large. [Factored API](api/com.cra.figaro.algorithm.factored.md) |
| Belief propagation | Message passing; loops can make answers approximate and convergence difficult. [BP API](api/com.cra.figaro.algorithm.factored.beliefpropagation.md) |
| Importance | Weighted forward samples; weight degeneracy and rare evidence can dominate effort. [Sampling API](api/com.cra.figaro.algorithm.sampling.md) |
| Metropolis-Hastings | Propose, accept/reject, and retain repeated states after rejection; poor proposals can miss modes. [MCMC guide](MULTI_CHAIN_MCMC.md) |
| Gibbs/collapsed Gibbs | Conditional updates; collapsing integrates out selected variables. [Gibbs API](api/com.cra.figaro.algorithm.factored.gibbs.md), [experimental collapsed API](api/com.cra.figaro.experimental.collapsedgibbs.md) |
| Probability of evidence | A normalization/evidence question, not the same as a target posterior. Existing constraints can be treated as model structure or included evidence depending on the chosen API. [Sampling reference](api/com.cra.figaro.algorithm.sampling.md) |
| MPE | A joint most-probable explanation need not equal the collection of marginal modes. [Factored API](api/com.cra.figaro.algorithm.factored.md) and [annealing example](../FigaroExamples/src/main/scala/com/cra/figaro/example/AnnealingSmokers.scala) |
| Marginal-MAP | Marginalize nuisance variables before maximizing selected variables; summation and maximization are not interchangeable. [Experimental API](api/com.cra.figaro.experimental.marginalmap.md) |

The original proposal-scheme material explains which variables are moved together and
how proposal probabilities affect MH acceptance. Keep that reasoning when adopting
[Gaussian block proposals](BLOCKED_PROPOSALS.md); a block that never updates the rest
of the model does not by itself sample the full target. Pilot calibration adds measured
setup cost and must not reuse pilot draws as if they were fresh production samples.

Joint posterior sampling preserves dependence between queried variables; independently
sampling their marginal results does not. Similarly, probability-of-additional-evidence
APIs have a denominator/model-context contract: do not compare arbitrary evidence scores
from different constraint conventions. Consult exact overloads rather than porting an
old abstract-class constructor literally.

## Structured and lazy inference, abstractions and debugging

The longer tutorial edition adds structured factored inference (SFI): range, refine and
solve components of a hierarchical problem. Atomic rangers, component collections,
function memoization and strategies control how much work is represented and reused.
Lazy structured inference (LSFI) extends this to partially expanded models and bounds;
an incomplete expansion must not be presented as an exact answer.

Keep separate the representations of unexpanded values and ordinary outcomes. Caches
must not outlive the model state that justified their entries. Changing ranging/refining
strategies can alter approximation and cost, not just speed. Start with
[structured algorithms](api/com.cra.figaro.algorithm.structured.algorithm.md),
[range strategies](api/com.cra.figaro.algorithm.structured.strategy.range.md),
[refinement](api/com.cra.figaro.algorithm.structured.strategy.refine.md),
[solvers](api/com.cra.figaro.algorithm.structured.strategy.solve.md) and the
[lazy-structured examples](../FigaroExamples/src/main/scala/com/cra/figaro/example/lazystructured).

The legacy abstraction discussion concerns reducing model detail for a query. Validate
the resulting approximation; it is not the same feature as the new explicit-vector
samplers. The Figaro 5 debugger/visualization material is retained historically; desktop
UI availability is not a headless-server or modernization acceptance guarantee.

## Dynamic models and filtering

Filtering estimates evolving hidden state from observations arriving over time. Define
an initial model and a transition model, and advance evidence with the intended time
step. Particle filtering propagates/resamples particles; factored frontier approximates
the evolving belief state with a factored representation. A table of timestamped rows
does not define either model automatically.

See [filtering APIs](api/com.cra.figaro.algorithm.filtering.md) and
[ValveReliability](../FigaroExamples/src/main/scala/com/cra/figaro/example/ValveReliability.scala).
Preserve state/reference naming and observation timing. Particle degeneracy and state
approximation need application tests. The modern independent-chain MCMC API is not a
parallel particle-filter implementation or a replacement for sequential filtering.

## Decisions and utility

A decision model asks which action maximizes expected utility given information available
at decision time. It is different from finding the most probable action or latent state.
Finite parent support can permit an explicit policy; infinite support needs an appropriate
approximation/index and distance evidence. Multiple decisions introduce ordering and
information constraints; avoid accidentally giving an earlier decision later observations.

Study [decision APIs](api/com.cra.figaro.algorithm.decision.md),
[decision indexing](api/com.cra.figaro.algorithm.decision.index.md),
[SingleDecision](../FigaroExamples/src/main/scala/com/cra/figaro/example/SingleDecision.scala)
and [MultiDecision](../FigaroExamples/src/main/scala/com/cra/figaro/example/MultiDecision.scala).
The Scala 3 migration uses explicit `Distance` support for custom/tuple parents, not the
old runtime-reflection assumptions. Validate utility conventions and policy behavior.

## Learning and parameter collections

The Beta/coin and Dirichlet/dice examples distinguish model parameters from ordinary latent
draws. Expectation maximization alternates expected sufficient statistics with parameter
updates; the chosen inference backend and iteration budgets affect cost and approximation.
Parameter collections let one model definition use prior/training and learned operational
parameters. A MAP point estimate is not a posterior distribution over parameter uncertainty.

See [learning API](api/com.cra.figaro.algorithm.learning.md),
[parameter patterns](api/com.cra.figaro.patterns.learning.md),
[FairCoin](../FigaroExamples/src/main/scala/com/cra/figaro/example/FairCoin.scala),
[FairDice](../FigaroExamples/src/main/scala/com/cra/figaro/example/FairDice.scala) and
[SimpleLearning](../FigaroExamples/src/main/scala/com/cra/figaro/example/SimpleLearning.scala).
Use distinct parameter names (the old dice text repeats `fairness1` where distinct dice
are intended). Prefer current `EMWith...` spelling and signatures over inconsistently
capitalized historical prose. Supported parameter JSON is not arbitrary universe
serialization. Large learning examples remain outside the claim that the full suite is green.

## Extending elements and algorithms

New elements must define their value/randomness relationship and report dependencies.
Composition through `Apply` or `Chain` is often simpler than implementing a new atomic
class. Atomic customizations can require density, parameter/sufficient-statistic support,
factor conversion or special proposal logic, depending on the algorithms that will use
them. A class that samples correctly is not automatically usable by factored inference.
See [language](api/com.cra.figaro.language.md), [factor factories](api/com.cra.figaro.algorithm.factored.factors.factory.md)
and the full original element-extension chapter retained in the LaTeX sources.

New algorithms need initialization, query/result contracts and cleanup, not just a loop.
One-time versus anytime traits separate execution mechanics from inference; an anytime
step must leave a valid queryable state. Current anytime operations are serialized by a
JDK worker, not Akka. Bound/cooperate with interruption in custom callbacks and test
failure paths. The old statement that Scala traits cannot take arguments is historical,
not a Scala 3 language rule. See [algorithm API](api/com.cra.figaro.algorithm.md).

Factored implementations operate on factors and semirings; sum-product and max-oriented
operations answer different questions. Extension registration, ranging and factor creation
must agree on supported element classes. `Create[T]` now calls a trusted JVM singleton
implementing `Creatable`; it is not arbitrary reflection or a security sandbox. Use
[migration](MIGRATION.md) and explicit consumer tests for custom types/loaders.

## Experimental features and reproducibility

The longer tutorial's experimental chapter covers marginal-MAP, collapsed Gibbs and
univariate normal proposals. Those packages remain experimental; they have not become
production-certified merely because modern block/vector samplers now exist. Also retain
the Figaro 5 feature pointers to kernel-density elements and curried element operations
in [continuous](api/com.cra.figaro.library.atomic.continuous.md) and
[language](api/com.cra.figaro.language.md) APIs; do not assume an older extension package
name maps unchanged to the present source tree.

The old seed guidance is too strong for modern concurrency. Seed assignment and fixed
worker-count controls are documented, but a fixed seed is not a universal cross-JVM
graph-traversal reproducibility guarantee. Preserve model creation order, budgets and
configuration in comparisons; use [resource controls](RESOURCE_SCALING_ASSESSMENT.md)
and [MCMC reliability](MCMC_RELIABILITY.md). Retain rejection states, examine mixing,
and do not equate a precision stopping decision with proof that all modes were explored.

## Related and further migration

The [documentation audit](DOCUMENTATION_MIGRATION.md) maps every original chapter and
the quick-start/release-note topics to these guides. The originals remain available for
details not yet turned into modern executable walkthroughs. Advanced filtering, decision,
learning, SFI and extension recipes should be promoted one at a time with independent
numerical and lifecycle tests, not converted mechanically and labeled validated.
