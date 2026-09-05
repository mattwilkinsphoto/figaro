# Deprecation retirement

## Scope and baseline

This stage starts from the CI-green Scala 3 baseline `01105e10` on branch `modernize/deprecation-retirement`. It keeps Scala 3.9.0, sbt 2.0.8, and JDK 17 fixed while retiring deprecated language spellings, standard-library operations, and Figaro APIs. Brace-delimited formatting remains supported.

The implementation replaces deprecated usage in all source sets and removes Figaro-owned deprecated entry points. Verification requirements are:

- No deprecation diagnostics in a normal clean compilation of library, tests, and examples, without warning suppression or retained migration-mode flags.
- Explicit replacements and migration instructions for retired Figaro APIs.
- Regression coverage for lazy evaluation, collection mutation, early exits, and affected model construction.
- Passing required CI, documentation freshness, coverage, publication, and fresh artifact reproducibility checks.
- A compiler warning gate preventing reintroduction of deprecations.

Compiler-assisted rewrites are an implementation aid, not a retained build mode. Behavioral changes and public signatures receive separate review from mechanical syntax edits. Historical Scala 2 documentation remains archival, not the current API contract.

## Migrating a consumer

Recompile consumers: removing old entry points and changing lazy collection types is a source/binary compatibility change, even though the Scala and sbt versions stay fixed.

| Retired API or spelling | Replacement |
| --- | --- |
| `Stream[T]`, `toStream` | `LazyList[T]`, `.to(LazyList)`; this includes distribution, posterior-sampling, filtering, and selector randomness signatures |
| `MakeList(count, itemMaker)` | `VariableSizeArray(count, (_: Int) => itemMaker()).foldLeft(List.empty[T])((xs, value) => xs :+ value)` |
| `BetaParameter(a, b)` / `DirichletParameter(alphas*)` | `Beta(a, b)` / `Dirichlet(alphas*)` from `library.atomic.continuous`; constant arguments still construct learnable atomic parameters |
| `AtomicBeta.getLearnedElement` (including experimental normal proposals) | `Flip(parameter.MAPValue)(using "", parameter.collection)` |
| `AtomicDirichlet.getLearnedElement(outcomes)` | `Select((parameter.MAPValue.toList zip outcomes)*)(using "", parameter.collection)` |
| `AtomicBeta.makeValues` / `AtomicDirichlet.makeValues` | Let inference algorithms obtain parameter ranges; use `MAPValue` for the learned point estimate |
| `ElementCollection.allElements` | `namedElements` (still named elements only) |
| `ParticleGenerator.numArgSamples` / `numTotalSamples` | `numSamplesFromAtomics` / `maxNumSamplesAtChain` |
| `ParticleGenerator.defaultArgSamples` / `defaultTotalSamples` | `defaultNumSamplesFromAtomics` / `defaultMaxNumSamplesAtChain` |
| `ParticleFilter(static, initial, previous => next, n)` | `ParticleFilter(static, initial, (static, previous) => next, n)`; the no-static three-argument overload remains |
| `MultiMap` operations through decision leaf `objects` | Mutable map-of-sets operations; prefer `leaf.addObject(key, value)`, which still returns the map and deduplicates bindings |
| `Traversable` / `TraversableOnce` | `Iterable` / `IterableOnce` |
| `x: _*`, wildcard type `_`, explicit legacy context arguments | `x*`, wildcard type `?`, `(using ...)` |

The old parameter companion objects and their reflective `create` entry points are removed together. Update dynamically configured factory names as well as imports.

### Common replacements

```scala
import com.cra.figaro.language.*
import com.cra.figaro.library.collection.*
import com.cra.figaro.library.atomic.continuous.Beta

Universe.createNew()

// 1. A random-length list, including the empty case.
val count = Select(0.4 -> 0, 0.6 -> 2)
val items = VariableSizeArray(count, (_: Int) => Flip(0.5))
val values: Element[List[Boolean]] =
  items.foldLeft(List.empty[Boolean])((xs, value) => xs :+ value)

// 2. Give the list a name for reference-based models.
// ContainerElement.foldLeft has no contextual name parameter list.
val named = Apply(values, (xs: List[Boolean]) => xs)(using "items", Universe.universe)

// 3. Construct a learnable prior and materialize its learned point estimate.
val parameter = Beta(2.0, 2.0)
val learned = Flip(parameter.MAPValue)(using "", parameter.collection)
```

When a custom proposal needs access to individual item elements, retain a `MakeArray[T]` and use its memoized `items` or indexed arrays; fold a `FixedSizeArrayElement` wrapping that array to obtain list values. Do not recreate item elements on every proposal.

### Behavioral boundaries and gotchas

- `LazyList` memoizes evaluated values, including its head. Figaro explicitly retains the initial eager random draw for posterior sampling and `IntSelector.generateRandomness`; it does not precompute an infinite sequence. Holding a head retains the evaluated prefix, so consume long results incrementally.
- Built-in finite distributions and posterior samplers capture their result data before cleanup. Materialize results before `kill()` when using a custom algorithm whose lazy computation may still depend on live state. Never call `.toList` on an unbounded posterior-sampling result.
- Array-to-sequence conversions now explicitly copy where the previous implicit conversion copied. Map-value views remain lazy where they were lazy before. Set/map operations that produce new values remain non-mutating; in-place operations remain in-place.
- Registered caches use delegated map storage with a stable registration hash. This preserves universe deactivation/deregistration behavior without inheriting from a deprecated map implementation.
- Explicit `.runtimeChecked` on refutable test/data patterns preserves runtime shape validation and `MatchError`; it is not an unchecked cast or a warning suppression. Generic-erasure and exhaustivity warnings are a separate audit, not evidence of an obsolete API.
- This stage does not promise identical sample ordering across machines or worker counts, change statistical algorithms, or establish thread safety.
- Classes implementing Scala collection interfaces still inherit deprecated standard-library members such as `toStream`. Figaro neither calls those members nor hides them from the complete generated reference; removing Scala's inherited API would require a separate collection-interface redesign.

## Verification evidence

Local verification on JDK 17, Scala 3.9.0, and sbt 2.0.8:

- Clean library, test, and example compilation passes with deprecations treated as errors. No migration-mode flags or warning suppressions are used. Existing erased-type and match-exhaustivity diagnostics remain visible.
- All 191 selected regression tests pass: modernization, serialization, special functions, collections/caches, factors, densities, anytime lifecycle, deterministic parallel structure, variable-size collections, learning parameters, and migrated list models. This is not a claim that the entire historical stochastic suite passes.
- Both runnable onboarding examples pass. The regenerated reference contains 11,233 public method entries in 42 files; freshness, 11,397 local link targets, and all 12 documentation-tool unit tests pass.
- The coverage-plugin smoke test passes all three probability regressions and generates reports. Its 9.00% statement coverage measures that small smoke selection, not the full regression suite.
- Thin and fat JARs match byte-for-byte across two fresh, cache-bypassed builds of the final sources. Source/API classifiers and isolated local publication succeed.

The branch's GitHub Actions run remains the required remote verification before merge. The stable baseline branch is not changed by this stage.

## Following stage: parallel inference performance

After deprecation retirement is stable, evaluate parallelism and multithreading in a separate branch. Monte Carlo inference is a priority, but speedups must be measured against statistical quality and resource use, not only raw samples per second.

The proposed investigation will:

1. Establish repeatable sequential baselines for representative importance-sampling and MCMC workloads, recording warm-up, elapsed time, CPU use, allocations, peak memory, and estimator error. Include effective sample size per second where the method supports it.
2. Profile current parallel algorithms and identify whether sampling, factor work, synchronization, memory traffic, or result aggregation limits throughput.
3. Define worker ownership of universes, mutable caches, algorithm lifecycle, and random-number streams. Seed handling, cancellation, failures, and reproducibility across worker counts need explicit contracts.
4. Compare independent chains/replicates, bounded worker pools, and batched work where mathematically appropriate. Do not assume steps within a dependent Markov chain can simply run concurrently.
5. Require repeatable scaling measurements and statistical-equivalence checks before accepting an optimization. Preserve a sequential reference and avoid nested oversubscription.

This records the next investigation; the deprecation stage does not add parallel sampling or change the random-number architecture.

## Related

[Migration guide](MIGRATION.md), [build and verification](BUILDING.md), [user guide](USER_GUIDE.md).
