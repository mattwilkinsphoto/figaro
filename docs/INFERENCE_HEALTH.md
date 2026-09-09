# Inference health and warnings

`InferenceHealth` answers: **does this particular inference run show signs that its
answer is unreliable or insufficiently precise?** It is an opt-in assessment layer
for independent importance samples and ordered MCMC chains. It retains the numbers
and reasons behind each status instead of compressing everything into one score.

Use it when evidence concentrates the posterior, when estimates change between
seeds, when sampling budgets are expensive, and before trusting small Monte Carlo
standard errors (MCSE). It works across distribution families: proposal/target
mismatch and poor exploration are not specific to Gamma or Dirichlet.

The layer does **not** repair a proposal, choose an RNG, smooth weights, stop a run,
change an estimate, print a warning, or certify that all modes were found. Existing
samplers and stopping policies behave exactly as before. Applications decide how to
display or act on the returned issues. This is available on the modernization
branch; local acceptance is not a remote CI or release claim.

## Quick start in three steps

1. Import the API and construct a model in an explicitly owned universe.
2. Run the blocking collector with a scalar query projection.
3. Inspect **all** issues before using the estimate; clear your universe afterwards.

```scala
import com.cra.figaro.algorithm.sampling.InferenceHealth as H
import com.cra.figaro.language.Universe
import com.cra.figaro.library.atomic.continuous.Normal
import com.cra.figaro.util.withRandomSeed

val u = new Universe
val x = Normal(0, 1)(using "x", u)
Normal(x, 1.0)(using "measurement", u).observe(0.5)
try {
  val report = withRandomSeed(42L) {
    H.runImportance(10000, x, (v: Double) => v,
      H.Config(maxMeanMcse = Some(0.01)))
  }
  println(s"${report.status}: mean=${report.diagnostics.mean}")
  report.issues.foreach(i => println(s"${i.code}: ${i.message}"))
} finally u.clear()
```

`0.01` is an absolute MCSE target in the query's units, **not** a 95% confidence
interval half-width or a guarantee of accuracy. Choose a target appropriate to your
decision. A passed report without a target has not established application precision.

## Reading a report

| Status | Meaning | Suggested response |
| --- | --- | --- |
| `Danger` | Severe observed concentration, tail instability, or chain disagreement | Do not trust a small MCSE; inspect weights/traces and improve proposal or exploration |
| `InsufficientEvidence` | A required diagnostic is unavailable, unsupported or based on too little information | Inspect the issue; obtain suitable data, longer/dispersed chains, or a method-specific diagnostic |
| `Warning` | An available diagnostic misses a configured quality/efficiency/precision target | Determine whether more effective samples or a better method is needed |
| `ChecksPassed` | None of these checks raised an issue for these samples and this policy | Still check model validity, missing modes and decision-specific precision |

Precedence is `Danger > InsufficientEvidence > Warning > ChecksPassed`. All reasons
remain in `issues`; a favorable diagnostic never cancels an unfavorable one. There
is no universal statistical equivalent of a single nonlinearity index.

## Three common patterns: old versus new

### 1. A Figaro posterior mean or event probability

The standard approach returns an estimate with no health report:

```scala
import com.cra.figaro.algorithm.sampling.Importance
// x and u are the model and universe from the quick start.
val algorithm = Importance(10000, x)(using u)
try {
  algorithm.start()
  println(algorithm.mean(x))
} finally algorithm.kill()
```

The new approach uses the same Importance algorithm, captures individual weights
before aggregation, and returns the projected mean with diagnostics:

```scala
val meanReport = H.runImportance(10000, x, (v: Double) => v)
val eventReport = H.runImportance(10000, x,
  (v: Double) => if (v > 2.0) 1.0 else 0.0,
  H.Config(maxMeanMcse = Some(0.002)))
```

These are separate runs. Use identical explicit RNG scopes if comparing old/new
implementations on identical draws. The projection must be pure: it must neither
consume random numbers nor mutate the model. The event report concerns `P(x > 2)`;
a healthy mean estimate does not establish that a rare-event estimate is healthy.
An all-zero indicator produces insufficient evidence, not a zero-error certificate.
For multiple queries on one run, collect aligned values once and call `importance`
for each query. There is no automatic multi-query aggregate or multiplicity correction.

### 2. An existing independent importance-sampling pipeline

Previously an application might report just a weighted mean or raw-weight ESS.
Now it can submit **every original draw's** log ratio and corresponding scalar:

```scala
val report = H.importance(
  logWeights,                         // Seq[Double]: log(target/proposal), up to a constant
  independentDraws = true,            // an explicit assertion, not inferred from the data
  values = Some(queryValues),         // aligned Seq[Double]
  config = H.Config(maxMeanMcse = Some(0.01)))
val tail = report.diagnostics.pareto.flatMap(_.k)
val rawError = report.diagnostics.rawMcse
report.issues.foreach(i => println(i.message))
```

Weights alone are allowed: `H.importance(logWeights, true)`. That assesses weight
behavior, not query-specific precision. Do not reconstruct raw draws from a histogram
of distinct values: repeated values still represent distinct draws. Do not supply
resampled particles or aggregated log weights. A common additive shift of all log
ratios is harmless, but pass the original per-draw ratios rather than a transformed
or smoothed substitute.

Stratified, Latin-hypercube, quasi-Monte Carlo, adaptive and correlated importance
draws need their own error analysis. With `independentDraws = false`, the layer
retains descriptive concentration metrics and the weighted mean, disables Pareto
and MCSE assessment, and returns insufficient evidence. Setting this flag true
does not make a dependent design independent.

### 3. Multi-chain MCMC

Existing runners already return `McmcDiagnostics` summaries. The health layer gives
those diagnostics the same status/issue interface and an optional mean-error target:

```scala
// graphResult is a MultiChainMetropolisHastings.Result with query named "x".
val graphHealth = H.mcmc(graphResult.chains.map(_.draws("x")),
  H.Config(maxMeanMcse = Some(0.01)))

// vectorResult is a MultiChainVectorSliceSampler.Result; inspect coordinate zero.
val vectorHealth = H.mcmc(
  vectorResult.chains.map(_.result.samples.map(_(0))))
```

Supply equally long, ordered **post-warm-up** chains; do not flatten, sort or shuffle
them. Assess important derived quantities and event indicators as well as individual
coordinates. Use dispersed starts. Independent chains that all miss the same mode
can agree and still be wrong. Acceptance rate alone is not a convergence diagnostic.
This report does not change `McmcPrecision` or sequential stopping behavior.

## API reference

Import from `com.cra.figaro.algorithm.sampling`. All four public operations return
detached immutable data and reject invalid inputs with `IllegalArgumentException`.
Cooperative cancellation throws `InterruptedException` without clearing the flag.

| Operation | Parameters | Returns / example |
| --- | --- | --- |
| `InferenceHealth.importance` | `logWeights: Seq[Double]`; mandatory `independentDraws: Boolean`; `values: Option[Seq[Double]] = None`; `config: Config = Config()` | `ImportanceReport`; raw-pipeline example above |
| `InferenceHealth.runImportance[T]` | Positive `numSamples: Int`; active caller-owned `target: Element[T]`; pure finite `project: T => Double`; optional `config` | `ImportanceReport`; quick-start example above; owns and kills only its sampler, including on failure |
| `InferenceHealth.mcmc` | `chains: Seq[Seq[Double]]`; optional `config` | `McmcReport`; multi-chain examples above |
| `ParetoTail.fit` | `logWeights: Seq[Double]`; positive `maxSamples: Int = 1000000` | `ParetoTail.Result`; `ParetoTail.fit(logWeights)` fits without smoothing |

`Config(...)` is a validated immutable policy with these fields. Store it alongside
the report and Figaro revision; these initial engineering thresholds are **not**
calibrated global false-alarm probabilities.

| Field | Default | Rule |
| --- | --- | --- |
| `maxSamples` | 1,000,000 | Hard cap on importance input draws or total MCMC draws; positive |
| `minSamples` | 100 | Importance evidence minimum; at least 25 and no larger than cap |
| `minEss` | 100 | Positive finite minimum raw-weight ESS; below it warns |
| `minRelativeEss` | 0.01 | ESS / draw count below this warns; in (0,1] |
| `warnWeight` | 0.1 | Maximum normalized weight at/above this warns |
| `dangerWeight` | 0.5 | Maximum normalized weight at/above this is danger; 0 < warning < danger <= 1 |
| `minDrawsPerChain` | 1000 | MCMC evidence minimum, at least 4 |
| `minEssPerChain` | 100 | Positive finite pooled bulk/tail/mean ESS requirement multiplied by chain count |
| `maxRhat` | 1.01 | Finite value > 1; larger rank/folded split R-hat is danger |
| `maxMeanMcse` | `None` | Optional positive finite absolute target; importance requires aligned values |

Report/data fields:

- `Report`: `status`, complete `issues: Vector[Issue]`, and the actual `policy`.
- `Issue`: stable typed `code`, `severity`, human-readable `message`. Use the code,
  not message parsing, for application behavior. Codes cover sample/chain shortages,
  zero/dominant weights, low ESS/efficiency, Pareto warnings/danger/unavailable fits,
  unsupported dependence, constant observables, numerical range, precision misses,
  unavailable/high R-hat, missing MCSE and preserved source warnings.
- `ImportanceReport`: also `diagnostics: WeightSummary`, `independentDraws`, and
  `precisionRequested` (a target was supplied, **not** necessarily established).
- `WeightSummary`: `samples`, `positiveWeights` (finite log weights), `ess`,
  `relativeEss`, `maxWeight`, optional `pareto`, `mean` and `rawMcse`. A very small
  finite log weight may exponentiate to zero numerically. Missing values use `None`.
- `McmcReport`: optional existing `McmcDiagnostics.Summary` in `diagnostics`, plus
  `precisionRequested`. Retains rank/folded R-hat, bulk/tail/mean ESS and mean MCSE.
  Existing summary warnings are retained even if custom thresholds are more lenient.
- `ParetoTail.Result`: `status`, optional shape `k`, optional `scale`, `tailSize`,
  optional sample-size warning `threshold`. Status is `Estimated`, `TooFewSamples`,
  `DegenerateTail`, `NumericalFailure` or `NoPositiveWeights`. Scale is relative to
  the maximum input weight, not the original weight units. The threshold may exist
  even when fitting is unavailable; inspect status/k before interpreting it.

Case-class constructors/copy/accessors are ordinary immutable data operations, not
alternative assessment functions. Obtain reports through the operations above.
Full declarations are also indexed in the [API reference](api/README.md).

## What is measured, and why

For normalized raw weights w, the descriptive ESS is `1 / sum(w*w)` and the largest
weight reveals single-draw dominance. Neither is a quantity-specific accuracy bound.
For scalar values x, the raw self-normalized estimate is `sum(w*x)` and the ordinary
asymptotic plug-in MCSE is `sqrt(sum(w*w*(x-mean)^2))`. A collapsed sample can report
almost zero error while its answer is badly wrong. We deliberately preserve and label
that raw MCSE rather than treating it as a reliable interval.

The Pareto fit uses the largest `ceil(min(N/5, 3*sqrt(N)))` candidate weights, a
strict cutoff and the empirical-Bayes generalized-Pareto estimator used by ArviZ.
Ties may leave fewer tail points. The independent-draw warning threshold is
`min(0.7, 1 - 1/log10(N))`; k at/above 0.7 produces danger. Very large fitted k
indicates severe finite-sample instability, **not proof that the true posterior
mean or importance-weight moments do not exist**. Even bounded ratios can give
large finite-sample fits when the proposal barely reaches the important region.

This is a diagnostic-only adaptation, **not a PSIS implementation**: no smoothing,
PSIS ESS, PSIS MCSE, or claimed PSIS confidence-interval coverage is provided.
The warning convention comes from the [PSIS paper](https://www.jmlr.org/papers/v25/19-556.html)
and [loo diagnostic guidance](https://mc-stan.org/loo/reference/pareto-k-diagnostic.html).
The MCMC defaults follow [Stan diagnostic guidance](https://mc-stan.org/learn-stan/diagnostics-warnings.html);
Figaro reuses its existing implementation rather than introducing another estimator.

## Gotchas and limits

- Empty input is insufficient evidence. Nonempty all-zero weights are danger.
  Finite log weights and negative infinity are accepted; NaN/positive infinity,
  nonfinite values, mismatched lengths and ragged chains are rejected.
- Equal weights often mean a good proposal, but this tail fit is unidentifiable.
  The conservative result is insufficient evidence, **not a claim of sampler failure**.
  A method-specific IID analysis can be appropriate when exact weights are known.
- Constant observables and numerically zero estimated variation cannot certify
  rare-event or mean precision. Do not interpret missing diagnostics as zero risk.
- The collector retains O(N) raw weights/values in addition to ordinary Importance
  bookkeeping. The tail fit sorts a copy and uses a bounded candidate grid; this is
  extra work, not free telemetry. No always-on cost is added to existing samplers.
- `numSamples` counts returned weighted samples, not all rejected attempts. Existing
  hard-condition rejection semantics remain; impossible/rare evidence can run a long
  time. The sample cap is not a wall-clock deadline. Cancellation is cooperative;
  user callbacks must return. The collector does not clear caller-owned universes.
- The IID assertion requires independently generated proposals and pure callbacks;
  arbitrary stateful models cannot be verified automatically. There is no support
  here for dependence-corrected weighted ESS/MCSE or sequential confidence guarantees.
- Small MCSE, low k and good R-hat together still cannot detect an entirely missed
  mode from no observations of it. Check alternative starts/proposals, analytic
  controls and replicated experiments. Warning thresholds are not a substitute for
  model-specific validation or a better importance proposal.

## Validation and reproducibility

The focused tests compare Scala k/scale with five unchanged ArviZ v0.22.0 NumPy
reference fits, plus log-shift/permutation controls, weight collapse, unavailable
tails, query precision, MCMC reuse, invalid inputs, cancellation and sampler cleanup.
The fit port is Apache-2.0 licensed; see [ArviZ-LICENSE.txt](../ArviZ-LICENSE.txt) and
[attribution](../FigaroAttributions.txt). NumPy is research-only, not a library dependency.

The [fixed-workload study](../Figaro/src/test/scala/com/cra/figaro/test/modernization/InferenceHealthStudy.scala)
uses all 30 previously declared seeds for each of the Gamma and Dirichlet posterior
fixtures, 2,000 proposals and LXM, with the first parameter as observable. All 60
runs report danger and retain the prior kernel's mean/ESS to numerical tolerance.
Complete rows are retained in [the assessment results](inference-health-results.csv).
This is targeted detection evidence, not a held-out sensitivity/specificity study,
proof that every sample estimate is inaccurate, or a fresh comparison of RNGs.
The historical five-backend study is now explicitly pinned to those five providers
so adding Philox does not silently change its saved experimental grid.

Local acceptance (2026-09-08): 401 modernization tests in 37 suites passed, including
the nine health groups; 82 Python reference/evidence, documentation and artifact-tool
tests passed. Example compilation, Scaladoc, local publication and assembly passed.
The independent application resolved the rebuilt thin JAR, exercised the new API,
and passed its existing inference/cleanup checks. All four archives contain the
required runtime/source/API contents and exact license/attribution entries. The thin
artifact SHA-256 was `a2a63fa6006f1e1e4fd7d07dbebef9404f720545c7d15125da405e6e956b125b`.
The compiler-derived reference contains 11,949 public method entries and passed
freshness checks. Four pre-existing Scaladoc warnings remain; no remote CI outcome
is asserted for this local branch milestone.

```sh
sbt "figaro / Test / testOnly com.cra.figaro.test.modernization.InferenceHealthTest"
sbt "figaro / Test / runMain com.cra.figaro.test.modernization.InferenceHealthStudy"
python -B -m unittest discover -s tools -p 'test_inference_health_reference.py'
```

## Related and next work

- [Statistical validation](STATISTICAL_VALIDATION.md): why small raw MCSE was misleading.
- [MCMC reliability](MCMC_RELIABILITY.md), [Gaussian block proposals](BLOCKED_PROPOSALS.md)
  and [pilot calibration](PROPOSAL_CALIBRATION.md): improving effective exploration.
- [Multi-chain vector sampling](MULTI_CHAIN_VECTOR_SAMPLING.md): ordered traces for assessment.
- [RNG selection](RNG_SELECTION.md): reproducible random-stream choices, not a cure for weight collapse.

Next work is improved importance proposals at matched accuracy, held-out calibration
across model geometries and rare-event targets, and dependence-aware diagnostics.
Automatic remedies or stopping decisions require separate validation and approval.
The first [pilot-fitted defensive-proposal assessment](DEFENSIVE_IMPORTANCE_RESEARCH.md)
now provides a research-only candidate, pilot-inclusive comparisons and fresh-seed
coverage evidence; it has not changed this warning policy or a public sampler.
