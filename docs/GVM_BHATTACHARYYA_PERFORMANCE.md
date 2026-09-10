# GVM Bhattacharyya: matched-accuracy performance

Status: integrated on main through `e30c8b03`; the study commit `4de2c9f8` has
[passing CI](https://github.com/mattwilkinsphoto/figaro/actions/runs/34189533727). This adds an
executable study and regression gates, not a new library algorithm, wider numerical
range or compiled release. The measured library is the guarded implementation at
`251f9540`; the study source and raw evidence accompany this document.
For the later positive scalar API, see the separate
[1e-8-nat scalar comparison study](GVM_SCALAR_PERFORMANCE.md). Its timings use different
fixtures/protocol and must not be mixed into this study's 1e-6-nat speed ratios.

## Overview: what changed and why it matters

The [guarded comparison](GVM_BHATTACHARYYA.md) avoids an exponentially growing tensor
grid by integrating the angle analytically and evaluating Gaussian quadratic-phase
Fourier terms. Earlier examples counted work; this study measures elapsed time at
the **same absolute distance-error target of 1e-6 nats** against high-precision oracles.
Harmonics and callbacks are not interchangeable units of work.

For the six-dimensional fixture, the complete guarded call took about **19 microseconds**
versus **6.2 milliseconds** for constructing and evaluating the tensor reference:
approximately **326x faster** using the ratio of the reported median times. This is a
real saving for that tensor comparison, not a claim of 326x faster Figaro inference.
The curved two-dimensional case gained about **2.6x** versus a newly built rule and
**1.4x** versus a reused rule. In one dimension, reusing a tensor rule was faster than
the guarded call. These computations are single-threaded; the saving is mathematical,
not a new multithreading mode.

There is an important counterexample: the linear fixtures reduce exactly to one
Gaussian coordinate. A reused rule for that reduced six-dimensional problem took
only **3.6 microseconds**, about **5.3x faster than the guarded call**. Do not benchmark
against a full tensor grid if your application already exploits such a reduction.

## Quick start: three steps

From the repository root, with the supported Java/Scala/sbt toolchain:

1. Verify accuracy without timing:
   ```text
   sbt "examples / Compile / runMain com.cra.figaro.example.GaussVonMisesBhattacharyyaPerformance check"
   ```
2. Measure in three fresh JVMs, not three runs sharing sbt's JVM:
   ```text
   sbt 'set examples / Compile / fork := true; set examples / Compile / javaOptions ++= Seq("-Xms1G", "-Xmx6G"); examples / Compile / runMain com.cra.figaro.example.GaussVonMisesBhattacharyyaPerformance measure 7; examples / Compile / runMain com.cra.figaro.example.GaussVonMisesBhattacharyyaPerformance measure 7; examples / Compile / runMain com.cra.figaro.example.GaussVonMisesBhattacharyyaPerformance measure 7' > gvm-performance.log
   ```
   This quoting works in Bash and PowerShell; use UTF-8 output. Check that the three
   `GVM_ENV` records have different process IDs and all three completion records exist.
3. Validate the complete output and summarize it:
   ```text
   python -B tools/summarize_gvm_bhattacharyya_performance.py gvm-performance.log
   ```
   Alternatively pass `docs/GVM_BHATTACHARYYA_PERFORMANCE_RUNS.txt` to reproduce the
   checked-in summary without running timings. The script uses Python's standard library.

## What was compared

All fixtures have the same standard-normal linear marginal for both inputs, kappa=4,
and beta vectors filled with +0.2 and -0.2. Linear cases have alpha=0 and Gamma=0.
The curved case has two coordinates, alpha values 0.3 and -0.1, and
`Gamma_p = [[0.1,0.04],[0.04,-0.05]]`, `Gamma_q = 0`.
Figaro's conditional location is `alpha + beta^T z + 0.5 z^T Gamma z`.

The Gaussian overlap factor is one for these fixtures. The tensor callback evaluates
the analytically integrated conditional angular affinity:
`I0(4 * abs(cos(delta/2))) / I0(4)`, where delta is the difference of conditional
locations. A scaled log-density expression avoids overflowing the Bessel ratio.
This is already a stronger baseline than numerical integration over the actual angle.
The generic tensor API requires angular order 2 even here: both callbacks have the
same value. That small redundant factor is included and explicitly reported.

Before timing, choose the first passing Gaussian order in the fixed ladder
`3,5,7,9,13,17,25,32`, subject to 300,000 total nodes. Reject the study if no order
meets the target; do not substitute a coarser rule or choose by runtime. Passing orders
are oracle-validated for these fixtures, not an adaptive accuracy guarantee for new inputs.

| Fixture | Guarded harmonics / absolute error | Tensor order / callbacks / absolute error | Reduced order / callbacks / absolute error |
| --- | --- | --- | --- |
| Linear 1D | 5 / 2.73e-8 | 7 / 14 / 2.41e-8 | 7 / 14 / 2.41e-8 |
| Linear 2D | 5 / 1.61e-9 | 7 / 98 / 1.35e-8 | 9 / 18 / 5.31e-8 |
| Curved 2D | 5 / 3.21e-9 | 7 / 98 / 1.70e-7 | Not used |
| Linear 6D | 5 / 1.95e-14 | 5 / 31,250 / 6.92e-7 | 17 / 34 / 5.19e-8 |

The lower orders that failed remain in the raw output. For example, six-dimensional
order 3 misses by 3.72e-4 nats. Timing it against a passing guarded result would not
be a matched-accuracy comparison. The methods meet a common error threshold; they do
not have identical errors or identical error-estimation contracts.

## Timing protocol and results

Three independent OpenJDK 17.0.4 JVMs on Windows 11 amd64; Scala 3.9.0 and sbt 2.0.8.
Each used a 1 GiB initial / 6 GiB maximum heap and reported 32 available processors.
No CPU affinity, frequency pinning, forced collection or machine isolation was imposed.
These are exploratory local timings, not portable service-level promises or a JMH study.

For each fixture: 20 interleaved single-call warm-ups per method; discarded calibration
doubles batches to at least 20 ms or a 4096-call cap; seven measured rounds rotate method
order. A cap or subsequent JIT compilation can yield measured batches shorter than 20 ms.
Every result reaches a volatile sink. Printing, oracle checks, fixed kernel construction,
order selection and JVM/sbt startup are excluded. Collection and allocation during timed
calls are included. The guarded call includes its preprocessing and status/result creation.
Tensor build/evaluate includes rule construction and traversal; reuse excludes construction.
The reduced methods assume the mathematical reduction is already known, not discovered
inside the timer. No guarded preprocessing cache is supplied.

Times below are microseconds per call: median of the three per-JVM seven-round medians.
Ranges are the smallest/largest per-JVM medians, **not confidence intervals**.

| Fixture | Guarded | Tensor build + evaluate | Tensor reuse | Reduced build + evaluate | Reduced reuse |
| --- | --- | --- | --- | --- | --- |
| Linear 1D | 8.91 [8.31–9.37] | 14.60 [12.74–14.64] | 2.68 [2.18–2.70] | 14.29 [12.87–14.74] | 2.73 [1.91–3.13] |
| Linear 2D | 8.17 [8.03–8.23] | 22.05 [21.37–22.45] | 11.92 [11.76–12.32] | 23.37 [23.09–23.46] | 1.90 [1.72–2.05] |
| Curved 2D | 8.30 [8.18–8.41] | 21.47 [21.47–21.96] | 11.84 [11.71–12.12] | — | — |
| Linear 6D | 19.13 [19.11–19.49] | 6235 [5847–6526] | 6295 [5792–6463] | 160.54 [160.08–160.59] | 3.59 [3.23–3.75] |

The nearly identical build/reuse six-dimensional tensor times show traversal dominates;
their small reversal is measurement variation, not evidence that rebuilding is faster.
The two one-dimensional tensor implementations are mathematically equivalent; differences
between them expose ordinary timing/JIT noise. Avoid reading small differences as wins.

## API and three common workflows

The only public Scala entry point added by the study is
`GaussVonMisesBhattacharyyaPerformance.main(args: Array[String]): Unit`, in
`com.cra.figaro.example`:

- `Array("check")`: accuracy, callback parameterization and complete 18-method matrix checks;
  prints work/error records without timings.
- `Array("measure")` or `Array("measure", "7")`: the same checks, then timing; optional
  rounds must be an integer from 3 through 31, default 7. Returns Unit and prints records.
- Invalid arguments, unresolved fixture results or failed accuracy throw; interruption
  throws `CancellationException`. No partial study is labeled complete. There is no
  public reusable benchmarking class or automatic performance threshold.

Use these workflows as follows:

```scala
// 1. CI or a new machine: verify correctness, without requiring a particular speed.
GaussVonMisesBhattacharyyaPerformance.main(Array("check"))

// 2. Profile this fixed comparison workload: launch in fresh JVMs as shown above.
GaussVonMisesBhattacharyyaPerformance.main(Array("measure", "7"))

// 3. Application use: call the library directly, not this study or a timed inference loop.
val result = GaussVonMisesBhattacharyya.compare(p, q, absoluteTolerance = 1e-6)
result.distance match {
  case Some(value) => println(s"Resolved Bhattacharyya distance: $value nats")
  case None => println(s"No resolved distance: ${result.status}; ${result.message}")
}
```

The last example assumes fixed kernels `p` and `q`; see the
[complete user guide](GVM_BHATTACHARYYA.md) for their construction. Import the study
object from `com.cra.figaro.example` and the library API from
`com.cra.figaro.library.atomic.continuous` as appropriate.

The Python summarizer accepts one UTF-8 log path and optional `--runs N` (positive,
default 3), prints microsecond summaries, and exits unsuccessfully for malformed,
incomplete, duplicate, nonfinite, unmeasured or accuracy-mismatched run matrices.
It verifies distinct recorded PIDs, not the authenticity of externally edited logs.
Use a single measurement session; process IDs can be recycled across unrelated sessions.

## Gotchas and next decisions

- Enable nothing globally. Choose the guarded API explicitly when comparing two fixed
  GVM laws. Existing importance/MCMC algorithms do not switch to it automatically.
- For a known scalar projection, reuse a validated reduced rule where appropriate.
  Here `sum(X_i)` has the same law as `sqrt(n)*Z`. The curved fixture cannot use that
  same sum-only reduction; other problem-specific methods remain possible.
- Reused quadrature needs independently justified orders for your changing inputs.
  It does not gain the guarded API's unresolved status or numerical checks merely by
  being faster on a fixed fixture. Conversely, the guarded roundoff estimate remains
  heuristic, not a certified numerical interval.
- Only moderate-concentration, well-conditioned, common-Gaussian fixtures were timed.
  Unequal covariance, concentration near limits, cancellation, high dimension, changing
  inputs and application-level throughput remain unmeasured here. No range limits changed.
- This is not a Monte Carlo ESS, coverage, posterior-quality or memory-scaling study.
  A faster divergence cannot be translated directly into a whole-application speedup.
- The subsequent gate was broader numerical reliability, particularly concentration
  and cancellation—not tuning away microseconds or expanding the support cap unchecked.
  The follow-on [96-pair reliability grid](GVM_BHATTACHARYYA_RELIABILITY.md) now records
  this first stress assessment, including where the current guard declines to resolve.
  [Single-kernel mutual information](GVM_MUTUAL_INFORMATION.md) and
  [full-mixture partition MI](GVM_MIXTURE_MI.md) subsequently shipped in the 6.1 line;
  they do not expand the accuracy claims of this original timing study.

## Verification and related modules

[Raw records](GVM_BHATTACHARYYA_PERFORMANCE_RUNS.txt) retain all 378 timed batches,
accuracy selections, environment and completion records. The
[eight report-validator tests](../tools/test_gvm_bhattacharyya_performance.py) reject
damaged evidence; CI runs them and the untimed Scala study, with no speed assertions.
The [13 research tests](../tools/test_gvm_bhattacharyya_research.py) include high-precision
projection identities and independent positive quadrature controls for these oracles.
Reproduce oracle constants using `performance_fixtures()` in the
[optional mpmath research tool](../tools/gvm_bhattacharyya_research.py).
Local acceptance also reran all 297 modernization tests across 23 suites, the untimed
18-method study and 18 documentation-parser tests; generated API freshness and local
links passed. Library production sources and dependencies were not changed by this study.

Related: [study source](../FigaroExamples/src/main/scala/com/cra/figaro/example/GaussVonMisesBhattacharyyaPerformance.scala),
[guarded API](GVM_BHATTACHARYYA.md), [tensor reference](GVM_TENSOR_QUADRATURE.md),
[order diagnostics](GVM_QUADRATURE_COMPARISON.md), [research derivation](GVM_BHATTACHARYYA_RESEARCH.md),
[roadmap](../ROADMAP.md). No report ingestion, fusion, filtering or propagation is added.
