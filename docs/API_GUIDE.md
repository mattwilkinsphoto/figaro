# Practical API reference

This is the guided part of the reference, covering the entry points most new users need. The [complete public-method reference](api/README.md) supplies every compiler-documented public method's exact signature, overloads, type/context/value parameters, return type, available contract, and call template. The searchable generated Scala 3 site additionally covers fields, type aliases, primary constructors, and inheritance.

The tables below use simplified result supertypes for readability; use the complete reference for exact subtype/refinement signatures. Examples assume imports from `com.cra.figaro.language.*` and the packages noted in each section. A `T` is an ordinary Scala outcome type, such as `Boolean`, `Double`, or `String`. `Element[T]` is a model node over that type, not a value to cast into `T`.

## Universes and model construction

Most constructors have a final contextual argument list containing `Name[T]` and `ElementCollection`. Default name/context lookup makes calls such as `Flip(0.2)` work. To be explicit in Scala 3, use `Flip(0.2)(using Name[Boolean]("cause"), universe)`. Legacy context-argument spelling is rejected by this branch's deprecation gate. Use distinct nodes/names consistently in a model.

| Public entry point | Parameters | Returns | Example |
| --- | --- | --- | --- |
| `Universe.createNew()` | None | A new `Universe`, also installed as the mutable default | `val universe = Universe.createNew()` |
| `universe.clear()` | None | `Unit`; clears that universe's registered state | `universe.clear()` after stopping/disposing algorithms that use it |
| `Constant[T](value)` | A Scala outcome `value: T`; contextual name/collection | `Constant[T]` | `val limit = Constant(21.0)` |
| `Flip(prob: Double)` | Probability of `true`; contextual name/collection | `AtomicFlip` | `val cause = Flip(0.2)` |
| `Flip(prob: Element[Double])` | A probability-valued node; contextual name/collection | A `Flip` element (parameterized for an `AtomicBeta`) | `val chance = Flip(Beta(2.0, 5.0))` with `library.atomic.continuous.Beta` imported |
| `Select[T](clauses*)` | Repeated `(Double, T)` weight/outcome pairs; contextual name/collection | A select element over `T` | `val size = Select(0.4 -> "small", 0.6 -> "large")` |
| `Apply(parent, function)` | An element and a pure mapping `T => U`; contextual name/collection | `Apply1[T, U]`; overloads support more parents | `val doubled = Apply(Constant(2), (n: Int) => n * 2)` |
| `Chain(parent, function)` | A parent node and `T => Element[U]`; contextual name/collection | `Chain[T, U]`, choosing caching according to the parent | `val result = Chain(cause, (b: Boolean) => Flip(if (b) 0.9 else 0.1))` |
| `Create[T](className, inputs*)` | A companion/module name string and ordered element arguments | `Element[T]`; generic result type is caller-selected | `Create[Boolean]("com.cra.figaro.language.Flip", Constant(0.2))` |

`Apply` changes values; `Chain` chooses another model element. Keep mapping functions free of unrelated side effects because evaluation/caching is controlled by the inference algorithm. Parameter validation and supported outcomes vary by distribution: check the precise constructor contract.

Import `com.cra.figaro.library.compound.If` for conditional composition:

| Entry point | Parameters | Returns | Example |
| --- | --- | --- | --- |
| `If(test, thenClause, elseClause)` | A Boolean test element and compatible branches; overloads accept element/value combinations | An element over the branch result type | `val signal = If(cause, Flip(0.9), Flip(0.1))` |

Import `com.cra.figaro.library.atomic.continuous.Normal` for a normal distribution:

| Entry point | Parameters | Returns | Example |
| --- | --- | --- | --- |
| `Normal(mean: Double, variance: Double)` | Constant mean and positive variance; contextual name/collection | `AtomicNormal` | `val temperature = Normal(20.0, 4.0)` |
| `normal.density(d)` | A location `d: Double` | Probability density `Double`, **not** probability mass | `val densityAtMean = temperature.density(20.0)` |

The full reference covers `Normal`'s element-valued overloads, the other distributions, and the parameter-type overloads omitted from this starter table.

## Evidence and element state

Here `element: Element[T]`; `Value` in its formal API corresponds to the element's outcome type. `Condition` is a predicate over values, `Constraint` a weight function. The optional `contingency` describes other element/value assignments under which the evidence applies; omit it for unconditional evidence.

| Public method | Parameters | Returns | Example |
| --- | --- | --- | --- |
| `observe(observation)` | An observed `T` | `Unit`; installs observed evidence | `signal.observe(true)` |
| `unobserve()` | None | `Unit`; removes the observation | `signal.unobserve()` before constructing a new inference run |
| `addCondition(condition, contingency = List())` | `T => Boolean`; optional contingency | `Unit`; adds a hard condition | `temperature.addCondition((t: Double) => t > 0.0)` |
| `setCondition(newCondition, contingency = List())` | Replacement predicate; optional contingency | `Unit`; replaces conditions for that contingency | `temperature.setCondition((t: Double) => t < 30.0)` |
| `removeConditions(contingency = List())` | Optional contingency | `Unit`; removes matching conditions | `temperature.removeConditions()` |
| `addConstraint(constraint, contingency = List())` | Nonnegative relative weight function; optional contingency | `Unit`; adds a soft constraint | `cause.addConstraint((b: Boolean) => if (b) 2.0 else 1.0)` |
| `setConstraint(newConstraint, contingency = List())` | Replacement relative weight; optional contingency | `Unit`; replaces matching constraints | `cause.setConstraint((b: Boolean) => if (b) 3.0 else 1.0)` |
| `removeConstraints(contingency = List())` | Optional contingency | `Unit` | `cause.removeConstraints()` |
| `intervene(v)` | An intervention value `T` | `Unit`; installs an intervention and assigns the value | `cause.intervene(true)` |
| `unintervene()` | None | `Unit`; removes intervention state | `cause.unintervene()` |
| `generate()` | None | `Unit`; generates randomness/value into mutable element state | `temperature.generate(); println(temperature.value)` |
| `set(newValue)` | A value `T` to hold fixed | `Unit`; freezes generated state, not a posterior query | `temperature.set(20.0)` |
| `unset()` | None | `Unit`; releases the fixed-value state | `temperature.unset()` |
| `isCachable` | None; no parentheses | `Boolean` describing cacheability | `val reusable = cause.isCachable` |

Observation, intervention, and direct state assignment have different meanings. Interventions are not interchangeable with conditioning on evidence. Changing `value` or calling `set` is not a substitute for asking an algorithm for a distribution. Evidence mutations do not promise automatic live recomputation by every algorithm. For predictable application behavior, finish a run and rebuild/restart deliberately.

## Algorithm factories and lifecycle

For bounded parallel importance sampling and synchronous seed scopes, see the [parallel API reference](PARALLEL_PERFORMANCE.md#api-reference). It covers `ParImportance.seeded`, `withRandomSeed`, and the scoped default-universe accessors, including parameter/return contracts and runnable examples.

For independent MH chains with ordered scalar traces and R-hat/ESS/MCSE, see the [multi-chain MCMC API](MULTI_CHAIN_MCMC.md#api-reference). It has a factory-based ownership contract and automatically disposes its models; it is not a probability-query algorithm and does not support `observe()` in this milestone.

Imports: `algorithm.factored.VariableElimination` and `algorithm.sampling.Importance`.

| Entry point | Parameters | Returns | Example |
| --- | --- | --- | --- |
| `VariableElimination(targets*)` | Query elements; contextual `Universe` | A one-time probability-query variable-elimination algorithm | `val alg = VariableElimination(cause, signal)` |
| `Importance(myNumSamples, targets*)` | Positive sample budget `Int`, query elements; contextual universe | A one-time importance sampler supporting probability queries | `val alg = Importance(50000, temperature)` |
| `Importance(targets*)` | Query elements; contextual universe | An anytime importance sampler | `val alg = Importance(temperature)` |
| `VariableElimination.probability(target, value)` | Target element and outcome | `Double`; convenience lifecycle wrapper | `val p = VariableElimination.probability(cause, true)` |
| `Importance.probability(target, predicate)` | Target and `T => Boolean` | Estimated `Double`; convenience wrapper with a fixed internal budget | `val p = Importance.probability(temperature, (t: Double) => t > 21)` |

Use explicit algorithm instances when you need several queries, predictable cleanup, or control over the sample budget. Convenience wrappers are useful for small examples but do not expose all tuning choices.

| Public method on `Algorithm` | Parameters | Returns/behavior | Example |
| --- | --- | --- | --- |
| `start()` | None | `Unit`; activates and initializes/runs the algorithm | `alg.start()` |
| `stop()` | None | `Unit`; delegates stopping; anytime algorithms remain queryable | `alg.stop()` |
| `resume()` | None | `Unit`; delegates resumption; use with an appropriate anytime algorithm | `alg.resume()` |
| `kill()` | None | `Unit`; releases the algorithm and marks it inactive | `try query(alg) finally alg.kill()` where `query` is your application function |
| `isActive` | None; no parentheses | `Boolean` lifecycle state | `if (alg.isActive) alg.kill()` |
| `initialize()` | None | `Unit`; overridable initialization hook | Override in a custom algorithm, normally invoked through `start()` |
| `cleanUp()` | None | `Unit`; overridable cleanup hook | Override in a custom algorithm, normally invoked through `kill()` |

`AlgorithmActiveException` protects double starts; `AlgorithmInactiveException` protects inactive operations. `stop`/`resume` support depends on the implementation. Do not directly call hooks as a replacement for lifecycle methods. A failure during startup may leave partial state: inspect `isActive` and perform appropriate cleanup/recovery rather than blindly starting again.

## Querying results

These methods are available through probability-query algorithms. In the ordinary single-universe API the target is `Element[T]`; parallel samplers use `Reference[T]` instead. A target must belong to the algorithm's configured target list, and the algorithm must be active.

| Public method | Parameters | Returns | Example |
| --- | --- | --- | --- |
| `probability(target, value)` | Target and particular outcome `T` | `Double` | `alg.probability(cause, true)` |
| `probability(target, predicate)` | Target and predicate `T => Boolean` | `Double` | `alg.probability(temperature, (t: Double) => t > 21)` |
| `expectation(target, function)` | Target and `T => Double` | Expected/estimated `Double` | `alg.expectation(delivery, (s: String) => if (s == "late") 10.0 else 0.0)` |
| `mean(target)` | `Element[Double]` | Expected/estimated mean | `alg.mean(temperature)` |
| `variance(target)` | `Element[Double]` | Expected/estimated variance | `alg.variance(temperature)` |
| `distribution(target)` | Target element | Lazy `LazyList[(Double, T)]`, probability first | `val finiteResults = alg.distribution(cause).toList` |

Predicate and expectation APIs also expose curried overloads with a compatibility dummy argument; use the two-argument forms above to avoid overload confusion. Query failures include an inactive algorithm and `NotATargetException`. Sampling answers and variances carry numerical/statistical error. Do not assume distributions are sorted or finite for every algorithm/model.

## Advanced public surface

The [full reference](api/README.md) covers filtering, learning, decision policies/indexes, factor/semiring machinery, experimental algorithms, collections, and utilities. These APIs are publicly visible but are not all beginner-level application contracts. For an existing source example, browse [the examples module](../FigaroExamples/README.md).

Changes relevant to extension authors are collected in [migration](MIGRATION.md): `BaseProbQueryAlgorithm[Q, U[_] <: Q]`, correlated `WeightSeen` wrappers, `DistanceConversion[T]` context bounds, unused `TypeTag` removal, and parameterless accessor overrides. Build-only helper functions live in `build.sbt` and are documented in [building](BUILDING.md#build-helper-reference); they are not part of the published Figaro library.
