# Faster scalar GVM integration with audited totals

Status: integrated on main at `213aa587` after
[passing CI](https://github.com/mattwilkinsphoto/figaro/actions/runs/34204059402).
The preceding performance baseline is integrated on main through `42d4ec1f` after
[passing CI](https://github.com/mattwilkinsphoto/figaro/actions/runs/34198268203).
This change affects the opt-in positive scalar comparison only. Fourier behavior,
public signatures, concentration/dimension caps and release coordinates are unchanged.
Local validation passes **312 modernization tests across 26 suites**, 51 GVM
research/evidence tests, 18 documentation-tool tests, reference freshness and local
links. The rebuilt Java 17 thin artifact passes content/legal checks and excludes
the frozen test control and profiling example.
The independently published consumer also passes against the rebuilt JAR, verified
by its SHA-256, including scalar overlap and budget-refusal checks. No new tagged
release or immutable bundle is created by this milestone.

## Overview: what changed

The expensive part of the profiled curved scalar case was repeatedly scanning every
current quadrature panel to recompute two totals after each refinement. Most of those
panels had not changed. The implementation now maintains compensated running totals,
rebuilds them after at most **64 splits**, and **always rebuilds them before a successful
result or a budget/precision termination**. Incremental totals only guide refinement;
the final distance and error diagnostics still use fresh full compensated sums.

Across three fresh JVMs, the heavily curved fixture improved from **24.86 ms to
3.21 ms**, about **7.75x faster**. Two smaller fixtures improved by about **2.9x and
3.1x**. All three used exactly the same integrand evaluation counts and final numerical
diagnostics as the frozen pre-change implementation.

This does not make positive quadrature the preferred general method: Fourier was
roughly 10 microseconds on the heavily curved fixture in the earlier study. Positive
integration remains an explicit recovery option for eligible tiny-overlap comparisons.
Do not divide timings from different studies to manufacture a new matched-run ratio.

## Profiling evidence

The [bounded JFR profiler](../FigaroExamples/src/main/scala/com/cra/figaro/example/GaussVonMisesScalarProfile.scala)
records CPU stack samples after warm-up, without instrumenting library operations.

| Captured CPU samples | Before: 400 calls | After: 4,000 calls |
| --- | ---: | ---: |
| All execution samples | 636 | 746 |
| Samples containing the scalar integrator | 633 | 740 |
| Samples containing full compensated summation | 548 (86.6% of scalar samples) | 145 (19.6%) |

The after-run uses more calls to collect a useful sample count because each call is
faster. These are **inclusive sampled-stack counts**, not precise elapsed-time
attribution, allocation measurements or mutually exclusive categories. The separately
measured speedups below do not use JFR timings. The remaining profile includes 301
integrand samples and 229 Bessel-series samples; those overlap and must not be added.

Raw JFR files remain local because recordings can include environment metadata.
The commands below reproduce the profiling workflow with a fresh output path.

## Matched before/after results

Both versions run in each of three fresh JVMs, with interleaved warm-up and seven
alternating-order rounds. The pre-change control is a
[frozen test-only copy](../Figaro/src/test/scala/com/cra/figaro/test/modernization/GvmScalarFullSumBaseline.scala)
of `21269b97` (unchanged through `42d4ec1f`), apart from package/name and provenance
comments. A normalized-text SHA-256 check guards it against accidental edits. The
control is excluded from published library artifacts; it is not a second public API.

| Fixture | Full scans, median microseconds | Audited totals, median microseconds | Speedup | Evaluations, both |
| --- | ---: | ---: | ---: | ---: |
| Ordinary linear coupling | 235.555 | 80.951 | 2.91x | 712 |
| Opposed weak coupling | 236.173 | 76.725 | 3.08x | 764 |
| Strong curvature, concentration 50 | 24,860.700 | 3,206.412 | 7.75x | 25,098 |

Numbers are medians of three per-JVM medians. For the curved case the JVM median
ranges were 24.79–26.24 ms before and 3.20–4.28 ms after. All **126 timing records**
and nine identical-result checks are retained in the
[141-record evidence file](GVM_SCALAR_AUDIT_RUNS.txt). No slow final run was discarded.

Runtime was Java 17.0.4 on Windows 11, with the test build's 6 GiB maximum heap.
Kernels are constructed outside timing; each timed call performs preprocessing,
integration and result allocation, and consumes its distance through a volatile sink.
The methods share neither results nor mutable work buffers. Calibration targets 20 ms
with at most 4,096 calls per batch; at least 500 ms of interleaved warm-up precedes
recalibration and seven rounds. These are bounded fixed-input microbenchmarks, not JMH,
confidence intervals, cold-start latency or end-to-end inference speedups.

## Quick start in three steps

1. Use the existing call; no new flag or tuning parameter is required:
   ```scala
   val result = GaussVonMisesScalarBhattacharyya.compare(p, q,
     tolerance = 1e-8, maxEvaluations = 50000)
   ```
2. Continue checking `result.status` and `result.distance`. `Estimated` still means
   estimated numerical accuracy, not a certified interval or statistical confidence.
3. Run the differential checks when changing the integration code:
   ```text
   sbt "figaro / Test / testOnly com.cra.figaro.test.modernization.GvmScalarAuditTest"
   ```

See the [complete scalar API reference](GVM_SCALAR_BHATTACHARYYA.md) for parameter,
result-unit, cancellation and unsupported-input contracts; none changed here.

## Three maintainer workflows and tool reference

- **Check numerical compatibility:** the command above exercises 299 before/after
  comparisons: 168 oracle-grid directions, 126 tolerance/budget combinations and five
  shortcut/adversarial controls. It compares status, distance, interval, every error
  diagnostic, method and evaluation count exactly. This is a tested result, not a
  promise of bitwise equivalence for every possible floating-point input.
- **Reproduce timings:** run `sbt "figaro / Test / runMain com.cra.figaro.test.modernization.GvmScalarAuditPerformance"`
  three times and concatenate the output into a UTF-8 log. Test execution is forked;
  the validator requires three distinct JVM IDs. The test-only `main(args): Unit`
  accepts no arguments, validates oracle accuracy and exact diagnostic equality,
  then prints the fixed three-case/six-method/seven-round study.
- **Profile a suspected remaining bottleneck:** run
  `sbt "examples / Compile / runMain com.cra.figaro.example.GaussVonMisesScalarProfile scalar-profile.jfr 4000"`.
  `main(args): Unit` accepts a nonexisting output path and optional call count 10..5,000
  (default 400), warms up 40 calls, records `jdk.ExecutionSample` at a requested 2 ms
  period, writes the recording and prints inclusive stack counts. It rejects an
  existing output, failed accuracy or a recording with no scalar samples. JFR needs
  a writable temporary directory as well as output access; sandbox restrictions may
  prevent initialization. It is a developer tool, not a dependency of the library API.

`python -B tools/summarize_gvm_scalar_audit.py LOG` validates a complete log and prints
median microseconds, JVM ranges and ratios. Its `parse(text) -> list[dict]` requires
three complete runs; malformed evidence raises rather than producing a partial
summary. `summarize(runs) -> str` formats already-validated records; `main() -> None`
reads the CLI path and prints that summary. For example, replace `LOG` with
`docs/GVM_SCALAR_AUDIT_RUNS.txt` to reproduce the checked-in table. Python's standard
library suffices. Six tests cover completeness, tampering, work/accuracy mismatch,
invalid timings, JVM reuse and frozen-reference provenance.

## Gotchas and remaining work

- Running sums involve subtraction, so they are never trusted for a final distance
  or error estimate. A candidate exit triggers a full audit and repeats the stopping
  decision; an optimistic provisional result cannot bypass that audit.
- Audits also precede budget/precision results with interval diagnostics. Malformed,
  unsupported or nonfinite computations still fail or return no usable distance as
  before; an arithmetic failure does not manufacture an audited interval.
- The panel rule, positivity, conservative phase partition, Gaussian tail bound,
  roundoff/preprocessing allowances, evaluation cap and cancellation checks remain.
  Work counts and values matched all 299 controls, but provisional rounding could
  affect refinement counts for other near-threshold inputs. No universal bitwise
  identity or certified error proof is claimed.
- Rebuilding every 64 splits reduces scan frequency; it does **not** eliminate the
  worst-case quadratic scan cost. A tree of positive partial sums would be a separate
  design requiring its own numerical controls, not an automatic next replacement.
- The residual profile points toward integrand evaluation and initial panel work.
  The [tail-radius assessment](GVM_SCALAR_TAIL_ASSESSMENT.md) finds a promising
  reduction in initial panels using a stronger affinity lower bound; it remains
  preserved as research evidence. Its [production follow-on](GVM_SCALAR_TAIL_PRODUCTION.md)
  adds conservative JVM arithmetic, held-out controls and end-to-end timing.
  Any further optimization should preserve small-overlap accuracy and aliasing guards;
  do not loosen tolerance or erase coupling to obtain a faster answer.
- No wider dimensions/concentrations, automatic Fourier fallback, new threading mode,
  report ingestion, fusion, filtering or propagation is introduced.

Related: [public scalar guide](GVM_SCALAR_BHATTACHARYYA.md),
[original method-selection study](GVM_SCALAR_PERFORMANCE.md),
[roadmap](../ROADMAP.md).
