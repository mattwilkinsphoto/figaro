# Diagnostic hotspot study

## Overview

This stage targets a meaningful diagnostic bottleneck rather than reducing statistical
work. The preceding profile implicated rank sorting, and the isolated experiment below
found a large enough algorithmic gain to justify a production candidate. Stable radix
sorting orders fixed-width floating-point keys in eight byte passes; the previous stable
merge sorter repeatedly compares keys across logarithmically many passes.

The library chooses internally: fewer than 16000 pooled rank values, monotone sequences
and constants keep the merge path; larger nonmonotone sequences use radix. There is no
new setting to enable, no public signature change, and no new dependency. Snapshot
modern.10, Scala 3.9.0, sbt 2.0.8 and Java 17 remain unchanged. Rank probabilities and
normal quantiles are still calculated exactly as before, not approximated or cached.

This benefits callers of `McmcDiagnostics.summarize`, including graph and vector multi-chain
diagnostics. The end-to-end measurements here specifically exercise the vector runner;
they do not establish a graph-model speedup. The threshold counts pooled split draws,
not draws per chain: four even-length chains of 4000 draws give 16000 rank values.
Do not increase sample budgets just to trigger this implementation path.

## Quick start (three steps)

1. Rebuild your normal Figaro snapshot. No source migration or new option is required:

   ```sh
   sbt --server --batch "figaro / publishLocal"
   ```

2. Run your existing multi-chain workload with unchanged seeds, budgets and diagnostics.
   For a runnable example:

   ```sh
   sbt --server --batch "examples / Compile / runMain com.cra.figaro.example.MultiChainVectorSamplingExample"
   ```

3. Compare complete elapsed time and diagnostic warnings, not just sorting speed.
   Inspect the checked study with:

   ```sh
   python3 -B tools/summarize_diagnostic_hotspots.py --csv docs/diagnostic-hotspot-results.csv
   ```

## Pre-measurement protocol

Based on `2bb0755a`, branch `modernize/diagnostic-hotspot-study`. First investigate
without changing production diagnostics, sampling, callbacks, defaults or cancellation
policy. The previous audit's CI passed its new gates but failed the legacy anytime
lifecycle step; detailed log access is currently denied. Do not label that checkpoint
CI-green or weaken the lifecycle gate.

Measure the existing stable merge sorter, a study-only stable eight-pass radix candidate,
unchanged inverse-normal score/tie grouping/scatter, full rank normalization with each
sorter, and the unchanged complete scalar summary. The radix candidate uses two primitive
index arrays plus 256 counters, not a retained array of floating-point keys. Preserve
signed-zero ordering, tie stability, input immutability and independent scratch buffers.
Keep entry/exit and periodic interruption checks; do not reduce responsiveness to win.

Use deterministic continuous, tied/signed-zero, ordered, reverse and constant arrays
of 1024, 16000 and 64000 pooled values (four chains). Every stage targets 64000
values of work per round, retaining five warm-up and seven measured rounds. Rotate/reverse
stage order. Run three fresh sequential JVMs on the same JDK/machine, without simultaneous
builds/tests or profiling. Retain every result. Hash complete outputs outside timing,
check both candidate sort orders and rank scores exactly, and record current-thread
allocated bytes when supported. This is not a peak-memory or multi-worker measurement.

Commit this protocol and study before measuring. Use JVM-level medians and ranges,
not individual inner calls as independent replications. Stage times are overlapping
experiments and must not be added/subtracted as exclusive causal percentages.

Promotion screen: look for at least 1.5x sorting and 1.2x full-rank gains on large
continuous fixtures, with repeatability across JVMs and explicit examination of small,
ordered and tied regressions. These are engineering selection criteria, not CI timing
gates. If the candidate merits production integration, require exact regression oracles,
unchanged full-grid statistical fingerprints and balanced fresh-JVM end-to-end validation.
Seek a material end-to-end improvement (roughly 5% or more on affected workloads) before
accepting complexity; do not claim a library gain from this isolated study alone.

The example entry point and candidate are investigation code, not a new inference API.
Protocol wording correction: the original text said "at least 64000"; the committed
integer-division implementation processes 63488 values at size 1024 and exactly 64000
at the other sizes. No implementation, timings or records were changed for this correction.

## Production candidate protocol (after isolated screen)

The three-JVM isolated screen completed all 3240 records. On continuous inputs, radix
sorting gained 4.653x at 16000 values and 5.053x at 64000; full ranks gained 2.340x and
2.552x. Small and monotone cases regressed, so do not replace every sort unconditionally.
The production candidate retains the unchanged merge implementation below 16000 pooled
values and for monotone/constant input. A cancellation-checked monotonicity scan exits
as soon as both directions are disproved. Only larger nonmonotone input uses stable radix
sorting. No normal-score, tie, FFT, estimator, sampling or callback expression changes.

The allocation screen observed two index arrays plus approximately 1040 extra bytes
for the radix counters. This is not a peak-memory guarantee. Guard scanning itself has
a cost on ordered inputs; the end-to-end study must not hide that cost.

Commit the production implementation, tests and this extension before its measurements.
Use four balanced fresh-JVM pairs against the study's unchanged-library runtime, the same
benchmark/dependency jars, 4000 draws, 500 warm-up, all six fixtures, both methods and
1/2/4 workers. Retain all 2016 records and require every non-timing field/fingerprint to
match the sorting checkpoint. Do not run builds/tests concurrently with measurement.

The isolated stage labels `mergeSort`/`mergeRank` refer to the production code at
`eda9ebba`, before this candidate. Reproduce that historical timing study using its pinned
runtime/revision, not the changed library; otherwise those labels no longer identify a
merge-only baseline. The `check` mode remains a valid candidate correctness smoke test.
After measurement, the study entry point was hardened to reject full timing against a
library containing the new radix implementation. This prevents silently labeling the
hybrid production path as `mergeSort`. The guard does not change the measured production
library or either retained runtime; use the pinned pre-integration checkout to reproduce
the historical study. It is a safeguard for these known implementations, not general
runtime provenance authentication.

## Isolated results

Measurements used Windows, JDK 17.0.4, and an AMD Ryzen 9 9950X system exposing 32 logical
processors. Java used a 1 GiB initial / 6 GiB maximum heap and 6 MiB thread stack.
No hardware counters or peak-memory measurements are claimed by this stage.

[All 3240 records](diagnostic-hotspot-results.csv) include the five warm-up rounds and
seven measured rounds from each of three fresh JVMs. Input generation and output hashes
are outside timing. The implementation/protocol commit is `eda9ebba`; no production
library change preceded this screen. Above 1 means the candidate was faster.

| Pooled values / input | Sort gain, median [JVM range] | Full-rank gain, median [JVM range] |
| --- | --- | --- |
| 1024 / continuous | 0.759 [0.701–0.968] | 0.926 [0.892–0.961] |
| 1024 / ties | 0.988 [0.904–1.051] | 0.931 [0.899–1.443] |
| 1024 / ordered | 0.636 [0.627–0.637] | 0.877 [0.847–1.102] |
| 1024 / reverse | 0.705 [0.643–0.708] | 0.894 [0.860–1.116] |
| 1024 / constant | 0.880 [0.869–0.902] | 0.858 [0.842–1.290] |
| 16000 / continuous | 4.653 [4.384–4.683] | 2.340 [2.338–2.382] |
| 16000 / ties | 3.327 [3.280–3.353] | 2.798 [2.740–2.867] |
| 16000 / ordered | 0.887 [0.795–0.934] | 0.946 [0.900–0.990] |
| 16000 / reverse | 0.946 [0.857–0.990] | 0.947 [0.945–0.962] |
| 16000 / constant | 1.302 [1.188–1.393] | 1.108 [1.092–1.210] |
| 64000 / continuous | 5.053 [4.898–5.178] | 2.552 [2.494–2.581] |
| 64000 / ties | 3.563 [3.456–3.587] | 2.976 [2.940–2.992] |
| 64000 / ordered | 0.969 [0.855–1.024] | 0.946 [0.932–0.958] |
| 64000 / reverse | 1.115 [0.941–1.146] | 0.971 [0.965–1.132] |
| 64000 / constant | 1.330 [1.302–1.507] | 1.205 [1.171–2.126] |

For 16000 continuous values, the median separate-operation times were 1.159 ms for merge
sorting, 0.250 ms for radix sorting, 0.423 ms for scores/ties/scatter, 1.585 ms for complete
merge-based ranks, 0.678 ms for radix-based ranks, and 7.357 ms for the unchanged scalar
summary. These operations overlap and were measured separately; they are not additive
exclusive runtime components. They support prioritizing sorting before changing the
normal-score calculation. Complete rank gains are smaller than sorter-only gains.

The candidate allocated approximately 1040 additional bytes per sort at each tested
size, consistent with its 256-counter array. Reported absolute bytes include fixed
measurement overhead and cover only the calling thread, not peak live memory or process
RSS. Neither sort retains a floating-point key cache. Ordered-input protection adds a
linear scan before the old merge path; it is not free and is not a guarantee of zero
regression on every input.

Runtime provenance (SHA-256):

| Artifact | Baseline | Production candidate |
| --- | --- | --- |
| Full revision | `eda9ebbaefdf404a3b7fdaa0c3105584d34b2373` | `c6410392edd5b83003dbde0a9e0f1626dbac5328` |
| Runtime manifest | `38e02ddef01844140e3dfdfdd87733a37d26f59aeef6fcccc67fb13ed19a7d57` | `3c4eb5aa557927d8666f64a5ccf45de3609b074f89f8e4b1b3604a66d52a3654` |
| Figaro jar | `c342dc4c4f51a3728b938eeeb0f51414fb9dcd987fbe3ccc8aa9db08671e385f` | `27a0e71c35862f9a90594762cb49f6cf0c02c5cdf10ee7bcd79c2b59969a32f8` |

The identical example jar is
`5c272d259474c76e599ef69ef87a20e47246cf4bb42d9f2c715a6b1959482680`.
All six dependency jars match too. Snapshots are hashed before every paired invocation.
These are retained runtime identities and declared source provenance, not a formal
source-to-binary attestation. The isolated study uses the baseline snapshot.

## End-to-end result and decision

**Accept the hybrid sorter for this branch.** The [full balanced comparison](diagnostic-radix-performance-results.csv)
completed all 2016 records (1440 measured and 576 warm-up), preserving every non-timing
field and full trace/diagnostic fingerprint. No selective reruns or omissions were made.
The unchanged merge baseline is `eda9ebba`; the committed hybrid candidate is `c6410392`.

Four-worker results use the same pair-level calculation as the prior audit: median of
five seed-matched ratios within each JVM pair, then median/range across four pairs.

| Fixture / method | Total gain | Pair range | Pairs faster | Diagnostic gain |
| --- | ---: | --- | ---: | ---: |
| Gaussian 8D / GPSS | 1.086 | 1.025–1.169 | 4/4 | 1.189 |
| Gaussian 8D / Quantile | 1.105 | 1.061–1.179 | 4/4 | 1.168 |
| Gaussian 32D / GPSS | 1.129 | 1.087–1.143 | 4/4 | 1.246 |
| Gaussian 32D / Quantile | 1.025 | 1.012–1.032 | 4/4 | 1.258 |
| Correlated 32D / GPSS | 1.048 | 1.019–1.051 | 4/4 | 1.157 |
| Correlated 32D / Quantile | 1.014 | 1.010–1.020 | 4/4 | 1.180 |
| Positive 32D / GPSS | 1.053 | 0.972–1.072 | 3/4 | 1.140 |
| Positive 32D / Quantile | 1.032 | 0.927–1.189 | 3/4 | 1.213 |
| Likelihood 8D / GPSS | 1.091 | 1.063–1.166 | 4/4 | 1.219 |
| Likelihood 8D / Quantile | 1.013 | 0.969–1.056 | 3/4 | 1.245 |
| Mixture 8D / GPSS | 1.048 | 0.950–1.061 | 3/4 | 1.201 |
| Mixture 8D / Quantile | 1.023 | 0.953–1.057 | 3/4 | 1.198 |

Total median pair gains at the other worker counts:

| Fixture / method | One worker | Two workers |
| --- | ---: | ---: |
| Gaussian 8D / GPSS | 1.201 | 1.156 |
| Gaussian 8D / Quantile | 1.196 | 1.147 |
| Gaussian 32D / GPSS | 1.221 | 1.169 |
| Gaussian 32D / Quantile | 1.106 | 1.057 |
| Correlated 32D / GPSS | 1.125 | 1.087 |
| Correlated 32D / Quantile | 1.031 | 1.028 |
| Positive 32D / GPSS | 1.133 | 1.117 |
| Positive 32D / Quantile | 1.094 | 1.078 |
| Likelihood 8D / GPSS | 1.112 | 1.098 |
| Likelihood 8D / Quantile | 1.016 | 1.011 |
| Mixture 8D / GPSS | 1.130 | 1.111 |
| Mixture 8D / Quantile | 1.060 | 1.066 |

The clearest four-worker gains are roughly 9–13% on the Gaussian and likelihood GPSS
cases and Gaussian 8D Quantile. Other gains are smaller and several individual pairs
regress. At one worker, diagnostic work is not distributed across coordinates, so its
improvement has more opportunity to affect elapsed time; observed total gains reach 22%.
This supports a bounded algorithmic change, not a promise of an order-of-magnitude
overall improvement or universal monotonic speedups.

### Retained adverse case: positive-support Quantile

The first pair's total ratio is 0.729x at both one and two workers. At one worker,
baseline/current median sampling times are 405.24/702.38 ms, while diagnostic medians
improve from 251.56 to 193.33 ms. The next pair reverses the slow-sampling pattern:
baseline/current sampling medians are 701.82/407.51 ms. Both variants therefore exhibit
the slow sampling state in different JVMs. Median GC pause totals do not by themselves
explain it. These separately computed phase medians need not add to the wall-time median.

The sampler source, benchmark callback and work fingerprints are unchanged. However,
JIT/GC/layout/scheduling interactions are not ruled out; the cause of this variability
has not been established. Retain the adverse pair and carry this workload into the next
allocation/scaling assessment. Do not discard it as proven noise or claim the hybrid
sorter guarantees a faster call. No further threshold tuning was done after this study.

## API reference

The existing [diagnostic API](MULTI_CHAIN_MCMC.md#api-reference) is unchanged:
`McmcDiagnostics.summarize(chains: Seq[Seq[Double]]): Summary` takes at least two equal-length
finite scalar chains, each with at least four draws. It returns mean, standard deviation,
R-hat, ESS/MCSE options and warnings. Invalid input throws `IllegalArgumentException`;
cooperative interruption throws `InterruptedException` without clearing the flag. Sorting
helpers remain package-visible regression seams, not supported consumer entry points.

`DiagnosticHotspotStudy.main(args: Array[String]): Unit` accepts `Array("check")` for
70 sort fixtures, 30 exact rank fixtures, concurrent isolation and entry interruption
controls. Otherwise it accepts measured rounds (default 7, 1–20) and work values per
stage (default 64000, 64000–1024000). Full timing must use the pinned **pre-integration**
runtime described above. Each operation repeats `max(1, work / pooledValues)` times;
at 1024 values, integer division yields 63488 processed values with the default, not
exactly 64000. Five negative warm-up rounds are always emitted. The method writes quoted
CSV, returns Unit and fails on invalid parameters, inconsistent results or interruption.
Partial output is diagnostic evidence, not a successful grid. It does not write files.

`summarize_diagnostic_hotspots.py` takes exactly one of `--csv FILE` or `--logs FILE...`.
Optional `--jvms` (3, 1–10), `--rounds` (7, 1–20), `--work` (64000, 64000–1024000)
must match the study. `--output FILE` creates a new validated CSV exclusively;
`--acl-script SCRIPT` optionally grants required Windows access. Existing output is never
overwritten. It returns success with a JVM-level median/range report, or fails on missing
warm-ups, duplicates, malformed records, wrong work counts, or changed fingerprints.
`NaN` allocation counts mean the JVM counter was unavailable, not zero allocation.

CSV fields are JVM index, record marker, input shape, pooled values, round, stage,
iterations, elapsed seconds, current-thread allocated bytes and full-output SHA-256.
The validator requires candidate/reference sort and rank hashes to agree across every
round and JVM. Summary hashes must be stable too. It validates stored evidence, not the
truth of arbitrary timing values or the identity of an unretained runtime.

## Three common patterns

### 1. An existing long-chain analysis

Before and after, call the same API:

```scala
import com.cra.figaro.algorithm.sampling.parallel.McmcDiagnostics
val chains = Vector.tabulate(4)(c => Vector.tabulate(4000)(i => math.sin(i * 0.173 + c)))
val result = McmcDiagnostics.summarize(chains)
println((result.rHat, result.bulkEss, result.warnings))
```

The new sorter is eligible for these nonmonotone pooled ranks; no extra threads or
different samples are introduced. This deterministic example illustrates API use,
not independently sampled chains or a convergence certificate.

### 2. Short or stuck chains

```scala
val short = chains.map(_.take(256))
val stuck = Vector.fill(4)(Vector.fill(4000)(1.0))
println(McmcDiagnostics.summarize(short).warnings)
println(McmcDiagnostics.summarize(stuck).warnings)
```

The small/constant ordering path stays merge-based. Degenerate diagnostics and stuck-chain
warnings stay visible. There is no reason to increase a statistical budget to cross a
performance threshold, and faster ranking cannot make constant chains informative.

### 3. Check a claimed overall gain

```sh
python3 -B tools/summarize_interleaved_performance.py check docs/diagnostic-radix-performance-results.csv --baseline-csv docs/primitive-sorting-performance-results.csv
```

This uses complete sampler runs, including construction, sampling, diagnostics and cleanup,
with matched work and fingerprints. Contrast that with the isolated sorter ratios above;
the latter cannot be presented as a 5x speedup for an entire inference job.

## Reproduction and gotchas

- Use a separate checkout of `eda9ebba` for the original isolated study. Run
  `sbt --server --batch "examples / Compile / runMain com.cra.figaro.example.DiagnosticHotspotStudy check"`
  before preparing a runtime snapshot. Export/copy the runtime with the
  [snapshot tool](INTERLEAVED_PERFORMANCE_AUDIT.md#reproducing-the-paired-experiment).
  Launch three separate Java processes with `-Xms1G -Xmx6G -Xss6M -XX:-UsePerfData`,
  the same jar classpath, isolated temporary/home directories, and main class
  `com.cra.figaro.example.DiagnosticHotspotStudy 7 64000`. Save three separate logs.
  Validate them with `--logs run-0.log run-1.log run-2.log`; never overlap runs with
  builds, other tests or profiling. Review metadata before sharing raw logs.
- For the production comparison, use baseline `eda9ebba` and candidate `c6410392` with
  the [balanced runner](INTERLEAVED_PERFORMANCE_AUDIT.md#reproducing-the-paired-experiment).
  Both contain the same example/dependency jars; only the Figaro jar changes.
- The 16000-value cutoff is a conservative routing decision from the tested grid,
  not a calibrated universal crossover or a new public tuning knob. Nearly ordered
  nonmonotone inputs take radix; their performance is not characterized by the fully
  ordered control. No broad adaptivity or sorting-library dependency is added.
- Scratch storage remains proportional to pooled draws. Rank normalization runs on raw
  and folded traces; coordinate workers own their buffers independently. This study
  does not resolve long-trace memory growth, process-wide oversubscription or DRAM limits.
- Interrupt checks occur on entry/exit, between radix passes and at most 1024 elements
  apart in long loops. Histogram prefix work is bounded to 256 buckets. No user callback
  becomes forcibly interruptible, and unchanged primitive value sorting/FFT calls retain
  their existing cancellation limitations.
- Bit-exact sorting/ranking and unchanged sampler fingerprints protect this optimization's
  semantics. They do not address mixing, mode discovery, precision undercoverage or the
  full historical test suite. Three isolated JVMs and four paired JVM comparisons remain
  limited machine-specific evidence, not confidence intervals or a universal speed promise.

## Verification and CI boundary

Compilation and all 163 modernization regressions pass. New production tests cover
36 cutoff/large ordering cases, 16 exact large-rank fixtures, concurrent/output isolation
and entry interruption. The separate study checks cover 70 sort and 30 rank cases.
Three existing vector/graph example workflows pass. All 53 documentation/report-tool
tests pass, reference freshness verifies the unchanged 11321 public methods, and local
links validate. Four existing Scaladoc warnings remain.

CI retains the required lifecycle, numerical, coverage, artifact and reproducibility
gates, adds the independent study checks, rejects mislabeled historical timings against
the hybrid library, and validates both complete new datasets. No performance threshold
or long benchmark rerun becomes a CI gate. Production library code/tests remain at
`c6410392`; the subsequent example-only guard prevents misuse of historical labels.

The preceding audit run failed its legacy anytime-lifecycle step after passing the new
audit and modernization checks. Detailed log retrieval returned HTTP 403. All four
unchanged lifecycle tests passed locally here; that does not diagnose the previous
failure or establish that the full historical suite is green. New branch CI status is
reported separately from these local results.

## Remaining core scope and stopping point

This is the diagnostic-hotspot milestone, not completion of the entire performance
program. After accepting or rejecting this candidate, move to representative-workload
allocation/memory/scaling assessment rather than searching for another small sorting
tweak. Distinguish library transition costs from application density costs, measure
longer traces and replicated model memory, and examine worker-count/overlap effects.
Only implement changes supported by those measurements; callback-only improvements
must be labeled separately from library gains.

The final core milestone is comparative acceptance and integration: matched statistical
work, representative workloads/hardware, documented worker/memory guidance, unchanged
cancellation/cleanup contracts, and the required CI/artifact gates. Broad shared-graph
thread safety, streaming/checkpoint APIs, automatic adaptation and new sampling-method
research are separate extensions, not prerequisites for closing the core phase.

Related: [interleaved audit](INTERLEAVED_PERFORMANCE_AUDIT.md),
[sorting checkpoint](PRIMITIVE_DIAGNOSTIC_SORTING.md), and
[allocation profiling](VECTOR_ALLOCATION_PROFILE.md).
