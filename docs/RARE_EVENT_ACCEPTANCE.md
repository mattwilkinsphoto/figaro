# Rare-event proposal acceptance and total-cost evidence

## Scope and protocol

This is the bounded modern.21 milestone: ordinary event-probability importance
sampling, supplied defensive proposals and pilot-only single-Gaussian cross-entropy
fitting. It is not universal stopping, automatic unknown-mode discovery or a claim
that one Gaussian suffices for arbitrary rare-event geometry.

The fixed study uses 30 predeclared seeds (90000..90029), four standard-normal
event queries and three methods, repeated in three fresh JVMs. **The repetitions
reuse the same statistical trials** to assess timing/replay; they are not 90
independent seeds. Every successful method gets a total of 10000 score calls.
CE spends 500 calls per pilot round, at most ten rounds/5000 pilot calls; production
gets the remainder. Elite fraction=.1, minimum elite ESS=20, defensive mass=.1,
covariance inflation=1.5, and diagonal variance ridge=.25 are fixed in advance.
No parameter search or successful-fit selection is used to present a completed run.

The supplied control shifts a unit-variance Gaussian to the threshold; the two-tail
case uses equal positive/negative components. That uses declared event structure
and is not an automatically discovered mixture. Construction time is included.
Method order rotates. Three untimed warmup passes precede the measured rows.
Elapsed times include fitting, sampling, weighting and score calls, but not sbt
startup, compilation or console output. These trivial score functions do not
represent a costly application model.

Environment: Windows, Eclipse Adoptium JDK 17.0.4, Scala 3.9.0, sbt 2.0.8,
Figaro's `L64X128MixRandom` default with `SeededV1` logical stream allocation.
Timing is machine-dependent; no per-draw or universal wall-time speedup is claimed.

## Results, including failures

Relative RMSE is sqrt(mean((estimate/truth-1)^2)) over the 30 distinct seeds.
Times are median milliseconds over the three JVM repetitions (90 timing rows per
completed case/method; successful subset only where explicitly identified).

| Event / true probability | Prior relative RMSE | CE relative RMSE | Supplied relative RMSE | Prior / CE / supplied median ms |
| --- | --- | --- | --- | --- |
| X>=0 / .5 | .94% | .70% | .78% | 3.75 / 5.94 / 5.47 |
| X>=3 / .001349898 | 33.17% | 1.40% | 1.81% | 3.67 / 5.91 / 5.51 |
| X>=5 / 2.866516e-7 | 100%, all 30 estimates zero | 1.92% | 2.54% | 3.68 / 5.85 / 5.46 |
| abs(X)>=4 / 6.334248e-5 | 155.13%, 18 zero estimates | **22/30 refused**; 4.89% on eight completed trials only | 2.36%, all 30 complete | 3.66 / 6.09 on eight completions / 5.88 |

All failures are retained in the raw files, with their consumed pilot work and no
production estimate. Successful CE trials are not a substitute for the 22 refused
ones; its two-sided error/time figures cannot be interpreted as overall performance.

The three-sigma result is substantial statistical improvement at equal total score
calls, even though each fixed-budget CE run costs more CPU time. Do not convert the
observed RMSE ratio directly into a universal runtime multiplier.

For five sigma, all-zero direct-MC estimates seriously underrepresent its population
variance in this small experiment. The analytic direct-MC relative RMSE is
sqrt((1-p)/(10000*p)), about **1868%**, not the observed 100%. More generally,
zero empirical variance or apparent agreement across empty trials is not precision.

The supplied common-event proposal has exactly the same normal law as the prior;
its slightly different observed RMSE is seed-consumption noise, not variance reduction.
It takes about 46% more median runtime. This is a concrete reason not to enable a
proposal merely because the option exists.

## Robustness and numerical checks

`RareEventImportanceTest` covers full-mixture weights against an exact piecewise
uniform calculation, ordinary fixed-budget variance, weighted Gaussian pilot fitting,
correlated two-dimensional events, log probabilities below binary64 range, explicit
budgets/refusals, stream separation/replay, Philox partitioned allocation, cancellation,
callback errors and near-constant contribution variance.

The deliberately missed-region test targets one of two narrow intervals around
+/-5. Each interval has probability about 2.973451e-9. A 10% defensive component
with 20000 total draws has over 99.999% probability of never visiting the remote
interval. The resulting estimate can be roughly half the truth with a small empirical
MCSE. This is preserved as a failing-coverage counterexample, not labeled solved.

Four independent high-precision tests verify tail probabilities, defensive-box
moments, weighted elite normal moments and the missed-region calculation. Four
read-only evidence tests enforce all rows, paired budgets, explicit refusal semantics,
cross-JVM numerical replay and the favorable/unfavorable results.

## Reproduce and inspect

```sh
sbt "figaro / Test / testOnly com.cra.figaro.test.modernization.RareEventImportanceTest"
sbt "figaro / Test / runMain com.cra.figaro.test.modernization.RareEventCostStudy 30"
python -B tools/test_rare_event_reference.py
python -B tools/test_rare_event_evidence.py
```

The research-only Python reference requires mpmath 1.3.0. The evidence check uses
only Python's standard library. The JVM study prints `RARE_ROW` records; it does not
write files or change inference defaults. Raw evidence:
[JVM A](rare-event-cost-jvm-a.csv), [JVM B](rare-event-cost-jvm-b.csv),
[JVM C](rare-event-cost-jvm-c.csv). All 1080 rows remain available. `densityCalls`
counts base and complete proposal calls, not nested component evaluations;
equal counts do not imply equal arithmetic cost. `NaN` denotes unavailable MCSE
or no estimate on an explicitly refused fit, not a fabricated zero uncertainty.

The clean local gate compiled 335 library and 239 test sources and passed all 524
modernization tests. Binary, assembled, source and API-documentation JARs passed
content checks; an independent consumer compiled and exercised the published API
against binary SHA-256 `091c9fbc0468a4b22d17413be4c6bf1ee12887fd6b51bf52c3be1aafba506f13`.
Generated-reference freshness covers 12476 public method entries; 13928 local link
targets and 18 documentation-tool tests pass. The four numerical references and
four evidence checks above also pass. Remote CI, including clean reproducibility,
remains required before main integration. See the
[user/API guide](RARE_EVENT_PROPOSALS.md) for the contract and research rationale.
