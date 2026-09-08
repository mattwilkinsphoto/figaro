# Scalar GVM comparison: accuracy, cost and method selection

Status: locally validated study on the GVM development branch; its CI/integration
gate is pending. The measured public library is commit `21269b97`, now integrated
on main after [passing CI](https://github.com/mattwilkinsphoto/figaro/actions/runs/34196804703). This milestone
adds measurements, evidence checks and guidance, not a new integrator, automatic
method selection, wider numerical limits or replacement release bundle.
Local acceptance passes all 309 modernization tests across 25 suites, the untimed
Scala study, 45 GVM research/evidence tests (including 13 new study checks), 18
documentation-tool tests, generated-reference freshness and local-link checks.

## Overview: what users should do

For ordinary coupled scalar GVM comparisons, **try the existing Fourier API first**.
The positive scalar API is an explicit accuracy-recovery alternative, not a blanket
performance upgrade. At a common absolute distance target of **1e-8 nats**, Fourier
was roughly **39–69 times faster** on three coupled fixtures, and about **2,626 times
faster** on the heavily curved, concentrated fixture.

Positive integration returned an accepted estimate for each of the two tiny-overlap
fixtures that Fourier refused, at about **0.25 and 0.43 milliseconds** per call.
That is a useful extension of numerical coverage. There is no successful-Fourier
timing to divide by in those cases, so we do not call it a speedup.

Both APIs have analytic reductions. The specialized scalar reductions were cheaper
in this hot, repeated-input study, but neither shortcut evaluates a positive integral.
Do not attribute their timing advantage to quadrature or promise it for full inference.

## Results

Each cell is the median of three fresh-JVM medians, with seven rounds per JVM.
Times are **microseconds per complete API call**, including preprocessing and result
allocation, excluding fixed-kernel construction. `Resolved` and `Estimated` results
were independently required to agree with the oracle within 1e-8 nats.

| Fixed scalar fixture | Fourier time | Positive API time | Fourier harmonics / positive evaluations | Interpretation |
| --- | ---: | ---: | ---: | --- |
| Gaussian mean shift | 1.529 | 0.034 | 0 / 0 | Both analytic; hot scalar shortcut is cheaper |
| Constant opposed angles, kappa 50 | 1.754 | 0.153 | 0 / 0 | Both analytic; not an integrator speedup |
| Linear coupling, kappa 4 | 6.041 | 235.229 | 6 / 712 | Fourier about 39x faster |
| Curved, unequal Gaussian marginals and concentrations | 6.084 | 270.659 | 5 / 849 | Fourier about 44x faster |
| Linear coupling, kappa 50 | 9.286 | 640.075 | 21 / 1,800 | Fourier about 69x faster |
| Opposed angles, beta 1e-5, kappa 50 | **Refusal cost** 10.058 | 248.805 | 25 / 764 | Only positive API supplies a distance |
| Opposed angles, beta 0.1, kappa 50 | **Refusal cost** 9.970 | 431.341 | 25 / 1,012 | Only positive API supplies a distance |
| Strong curvature, unequal Gaussian marginals, kappa 50 | 9.987 | 26,223.400 | 21 / 25,098 | Fourier about 2,626x faster |

Harmonics and integrand evaluations are different work units. They are not operation
counts and must not be compared as though one harmonic costs one evaluation.
The full [390-record evidence](GVM_SCALAR_PERFORMANCE_RUNS.txt) includes all 336 timing
rounds, 48 numerical outcome records and six environment/completion records. The
validator prints the range of JVM medians as well: for the strongly curved positive
case it is **26.00–30.13 ms**, versus **9.92–11.69 microseconds** for Fourier.

### Fixture definitions and reference accuracy

The [executable source](../FigaroExamples/src/main/scala/com/cra/figaro/example/GaussVonMisesScalarPerformance.scala)
contains the complete means, variances, angular centers, linear/quadratic coupling
and concentrations. The reference sources are the prior
[reliability grid](GVM_BHATTACHARYYA_RELIABILITY.md),
[general overlap derivation](GVM_BHATTACHARYYA_RESEARCH.md) and analytic reductions.
An additional [80-digit oracle test](../tools/test_gvm_scalar_oracles.py) reconstructs
all eight pairs from physical binary64 inputs, including the actual rounded `sd*sd`
covariance passed to Scala. It requires positive reference affinity, negligible
relative series truncation, and agreement with the stored oracle within 1e-14 nats.

All 14 successful method/fixture combinations meet the requested 1e-8 target;
the largest observed oracle error is about **3.53e-10 nats**. The two refusals expose
no distance. These are fixed-fixture accuracy checks, not certified error bounds,
confidence intervals, statistical success rates or application coverage results.

## Quick start in three steps

1. Check correctness without collecting timings:
   ```text
   sbt "examples / Compile / runMain com.cra.figaro.example.GaussVonMisesScalarPerformance check"
   ```
2. Run three fresh JVMs with the same settings. For example, this Bash/PowerShell
   command forks each example invocation and collects the complete output:
   ```text
   sbt 'set examples / Compile / fork := true; set examples / Compile / javaOptions ++= Seq("-Xms1G", "-Xmx6G"); examples / Compile / runMain com.cra.figaro.example.GaussVonMisesScalarPerformance measure 7; examples / Compile / runMain com.cra.figaro.example.GaussVonMisesScalarPerformance measure 7; examples / Compile / runMain com.cra.figaro.example.GaussVonMisesScalarPerformance measure 7' > gvm-scalar-performance.log
   ```
   Use UTF-8 output. The checked-in study used three separate sbt launches, each with
   a fresh JVM. Do not run unrelated load-generating benchmarks concurrently.
3. Validate and summarize:
   ```text
   python -B tools/summarize_gvm_scalar_performance.py gvm-scalar-performance.log
   ```
   Use `docs/GVM_SCALAR_PERFORMANCE_RUNS.txt` instead to reproduce the checked-in summary.

## API and tool reference

`GaussVonMisesScalarPerformance.main(args: Array[String]): Unit` accepts `check` or
`measure [rounds]`, where rounds is 3..31 and defaults to 7. It prints environment,
method/status/oracle/work, optional per-round timing, and completion records. Invalid
arguments or failed accuracy, outcome, work-cap or repeatability checks throw; caller
interruption throws `CancellationException`. Example: `main(Array("measure", "7"))`.

`tools/summarize_gvm_scalar_performance.py LOG [--runs N]` defaults to three fresh JVMs.
It prints per-JVM-median summaries and successful-result ratios, exiting with an error
for incomplete, duplicate, nonfinite or accuracy-mismatched evidence. It needs only
Python's standard library. Its importable helper contracts are:

- `fields(line) -> dict`: parse unique key/value fields; duplicate keys raise `ValueError`.
- `parse(text, expected_runs=3) -> list[dict]`: validate every expected case/method/round,
  distinct JVMs, fixed protocol, numerical records and work units. Malformed evidence
  raises; for example, `runs = parse(Path("study.log").read_text())`.
- `summarize(runs) -> str`: format already-validated records, labeling refusal cost and
  suppressing ratios where Fourier has no successful result; `print(summarize(runs))`.
- `main() -> None`: read CLI arguments, validate the file and print the summary.

The [12 report tests](../tools/test_gvm_scalar_performance.py) deliberately corrupt
timings, outcomes, units, budgets, reference values, JVM identities and completion
records. CI runs these, the physical-input oracle test and the untimed Scala check.
**Machine-specific elapsed times are never CI pass/fail thresholds.**

## Three common application patterns

### 1. Ordinary coupled comparisons: Fourier first

```scala
val first = GaussVonMisesBhattacharyya.compare(p, q, absoluteTolerance = 1e-8)
first.distance match {
  case Some(d) => println(s"Fourier distance: $d")
  case None => println(s"Needs an explicit alternative: ${first.status}")
}
```

The study's ordinary coupled fixtures support this as a starting policy. Benchmark
your own workload before making a universal decision; concentration alone does not
predict either method's resolution or cost.

### 2. Eligible scalar refusal: explicitly request a second calculation

```scala
val retry = GaussVonMisesScalarBhattacharyya.compare(p, q,
  tolerance = 1e-8, maxEvaluations = 50000)
println((retry.status, retry.method, retry.distance, retry.interval, retry.evaluations))
```

Use this only when both kernels have one linear coordinate and meet the documented
concentration/numerical limits. A second refusal remains a refusal. Record both
outcomes, and budget the sum of both calls if the application chooses this policy.
The library implements no automatic retry or automatic method switch.

### 3. Repeated analytic scalar laws: use the specialized shortcut deliberately

```scala
val result = GaussVonMisesScalarBhattacharyya.compare(p, q)
println((result.method, result.evaluations)) // gaussian/constant-angular may use zero
```

For the Gaussian and constant-angle examples, the public API avoids generic matrix
preprocessing and integration. These very short hot-loop measurements are particularly
sensitive to JIT optimization, timer overhead and repeated immutable inputs. They are
not an estimate of total application latency or of freshly constructing kernels.

## Methodology, gotchas and next work

- Runtime: Java 17.0.4, Windows 11, amd64, 32 visible processors, 6 GiB maximum heap.
  Calls are single-threaded; this is not a multithreading benchmark.
- Each case gets 20 initial interleaved calls, preliminary batch calibration, at least
  500 ms of interleaved warm-up, and recalibration targeting 20 ms per batch, capped
  at 65,536 calls. Seven rounds alternate which method runs first. Very fast shortcuts
  can hit the batch cap before 20 ms; heavy integrals can use batches of one.
- The public comparison, result allocation and a small common observation wrapper
  are timed. Distance consumption feeds a volatile sink. No previous comparison
  result is cached; only immutable input kernels are reused. Construction, sbt startup,
  oracle calculations and logging are outside the measured interval.
- One preliminary short-warm-up run was used to refine the protocol. It is not mixed
  with the three complete final-protocol runs; all final runs are retained. This is a
  bounded microbenchmark, not JMH, an allocation profile, a confidence interval or
  evidence of end-to-end Monte Carlo speedup.
- Repeated complete panel sums and conservative phase-aware partitioning are plausible
  cost candidates in the positive implementation. The present study measures elapsed
  time and integrand counts, not attribution: **profile before claiming either dominates**.
- Next substantive step: profile the high-work scalar case and assess algorithmic
  quadrature/refinement bookkeeping improvements while preserving the oracle, budget,
  cancellation and estimated-error contracts. Do not loosen tolerances or discard
  tiny coupling merely to make the benchmark faster. Certified error analysis and
  multidimensional alternatives remain separate milestones.

Related: [scalar API guide](GVM_SCALAR_BHATTACHARYYA.md),
[Fourier guide](GVM_BHATTACHARYYA.md), [earlier tensor comparison](GVM_BHATTACHARYYA_PERFORMANCE.md),
[roadmap](../ROADMAP.md). No report ingestion, fusion, filtering or propagation is added.
