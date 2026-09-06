# Interleaved performance and allocation audit

## Pre-measurement protocol

Branch `modernize/interleaved-performance-audit`, based on `c2329aa0`. No production
library, benchmark fixture, callback, sampler, sorting, diagnostic or cancellation-policy
changes. Compare the pre-sorting FFT checkpoint `a144eb1a` (baseline) with the sorting
checkpoint `c2329aa0` (current). Build/export both before measurement and copy all runtime
jars into isolated snapshots. Require identical benchmark/dependency jars and exactly
one differing Figaro library jar; verify all SHA-256 identities before every invocation.

Use four adjacent fresh-JVM pairs, alternating order: baseline/current, current/baseline,
baseline/current, current/baseline. Every invocation runs the full unchanged six-fixture,
two-method, 1/2/4-worker grid with four chains, 4000 draws, 500 warm-up transitions, two
negative JVM warm-up rounds and five measured seed rounds. This gives 2016 records
including warm-ups, 1440 measured rows. Use the same JDK/machine and 1 GiB initial /
6 GiB maximum heap; direct Java launches for both variants, no builds or other local
tests during the experiment. Preserve every run, including failures and regressions.

Require all non-timing fields/fingerprints to match the existing FFT dataset. Reduce
the five seed-matched ratios within each pair to a median, then report the median/range
of four pair estimates and how many pairs favor current. JVM pairs, not correlated seed
rows, are the replication units. Ranges are descriptive, not confidence intervals. The
fixed order balances first/second position but does not randomize uncontrolled desktop,
thermal, affinity or GC effects. Do not selectively rerun the Gaussian 8D GPSS regression.

Re-read the existing sorting JFR with a standalone JDK tool to distinguish observed
benchmark-density frames from sampler-owned frames and unresolved callback-boundary
stacks. Retain missing/truncated-stack counts, source recording hash, every allocation
weight/execution count and observed caller attribution for diagnostic interruption.
Require exact aggregate totals to reconcile with the existing checked profile. No new
recording or changed callback is needed; absence of a callback frame is not proof that
the callback allocated nothing. Raw recordings and runtime paths remain local.

Commit tools/tests and this protocol before the interleaved measurements. Use findings
to choose a next investigation; do not introduce a new production optimization here.

## Overview: why a user would run this

The previous sorting study showed faster diagnostic calculations but a slower median
runtime for Gaussian 8D GPSS. Comparing one earlier JVM with one later JVM cannot tell
whether that is a repeatable regression. This audit compares fresh processes in balanced,
adjacent pairs and checks that both versions still perform exactly the same work.
The allocation reader addresses a separate ambiguity: a sample attributed to the sampler's
callback call site may actually originate inside the model's density implementation.

This milestone changes only investigation tools, checked results and documentation.
Snapshot modern.10, production code, public library API and defaults are unchanged.
Normal Figaro sampling does not launch this audit or enable profiling. Most users should
read the checked findings first; repeat the measurements when performance matters for
their own hardware. Do not change sampling budgets because a benchmark became faster.

## Quick start (three steps)

1. Validate/read the checked interleaved results:

   ```sh
   python3 -B tools/summarize_interleaved_performance.py check docs/interleaved-performance-results.csv --baseline-csv docs/primitive-fft-performance-results.csv
   ```

2. Inspect the reconciled allocation attribution:

   ```sh
   python3 -B tools/summarize_vector_attribution.py --csv docs/vector-attribution-results.csv --profile docs/primitive-sorting-profile-results.csv
   ```

3. Use the findings below to choose a focused experiment, not to remove safeguards or
   assume the benchmark's model callback represents every application.

## Paired results and what they mean

The tools and protocol were committed as `a504e70e` before full measurement. All eight
fresh-JVM invocations completed, yielding [2016 checked records](interleaved-performance-results.csv)
(1440 measured and 576 warm-up). Every non-timing field and trace/diagnostic fingerprint
matches the FFT reference. No invocation was selectively repeated or omitted. Only the
Figaro library jar differs between snapshots; the benchmark and six dependency jars are
byte-identical. Measurements use JDK 17.0.4, with no overlapping local builds/tests.

Each gain is baseline time divided by current time: above 1 means current is faster.
The table shows four workers. All 1/2/4-worker records are retained in the CSV. Each pair
estimate is the median of five seed-matched ratios, not a ratio of timing medians.

| Fixture / method | Median pair total gain | Pair range | Pairs faster | Median pair diagnostic gain |
| --- | ---: | --- | ---: | ---: |
| Gaussian 8D / GPSS | 1.184 | 1.098–1.219 | 4/4 | 1.233 |
| Gaussian 8D / Quantile | 1.137 | 1.073–1.185 | 4/4 | 1.265 |
| Gaussian 32D / GPSS | 1.168 | 1.155–1.181 | 4/4 | 1.308 |
| Gaussian 32D / Quantile | 1.027 | 0.932–1.050 | 3/4 | 1.265 |
| Correlated 32D / GPSS | 1.129 | 1.070–1.158 | 4/4 | 1.270 |
| Correlated 32D / Quantile | 1.013 | 1.005–1.022 | 4/4 | 1.251 |
| Positive 32D / GPSS | 1.120 | 1.063–1.139 | 4/4 | 1.254 |
| Positive 32D / Quantile | 1.088 | 1.056–1.101 | 4/4 | 1.227 |
| Likelihood 8D / GPSS | 1.164 | 1.118–1.185 | 4/4 | 1.294 |
| Likelihood 8D / Quantile | 1.052 | 1.001–1.072 | 4/4 | 1.289 |
| Mixture 8D / GPSS | 1.097 | 1.093–1.125 | 4/4 | 1.259 |
| Mixture 8D / Quantile | 1.052 | 1.042–1.086 | 4/4 | 1.260 |

The suspected Gaussian 8D GPSS slowdown did not recur in this experiment:

| Pair / execution order | Baseline median ms | Current median ms | Median seed-paired gain |
| --- | ---: | ---: | ---: |
| 1 / baseline then current | 48.12 | 39.47 | 1.219 |
| 2 / current then baseline | 46.02 | 41.78 | 1.098 |
| 3 / baseline then current | 49.98 | 40.94 | 1.210 |
| 4 / current then baseline | 46.26 | 39.21 | 1.159 |

Both execution orders favor current here, but four pairs do not prove the earlier
observation was purely noise or eliminate machine/order effects. Keep the historical
regression in the [sorting report](PRIMITIVE_DIAGNOSTIC_SORTING.md). Gaussian 32D Quantile
still has one slower pair (0.932x), and Correlated 32D Quantile's median gain is only
1.3%. These are modest/noisy end-to-end gains despite clear diagnostic savings; faster
diagnostics do not imply proportional sampler speedups or better statistical coverage.

Snapshot provenance (SHA-256, not a claim of source-to-binary attestation):

| Artifact | Baseline | Current |
| --- | --- | --- |
| Git revision | `a144eb1a4a89d4ac0912b187b851d7845716c7d8` | `c2329aa008622953f95ab0acb344552cf2f4c9f8` |
| Runtime manifest | `f56ea4902f385955a8d2438bb306087ba3274848dddfdc36602d269392ab08c1` | `752b815b612ca031ad9aa14de69534862010e68c985d7eb34455daed19a34187` |
| Figaro jar | `06e0d663d2b91f2bad3c657aa46cdadd7286e2ab84add405b4c8f8b77b96d420` | `c342dc4c4f51a3728b938eeeb0f51414fb9dcd987fbe3ccc8aa9db08671e385f` |

The common benchmark jar SHA-256 is
`bf7626ff6c2259fd9e1709bd6ce150c6c238acfcbe3d6165a47d67fd5c99efe6`.
The runner verifies all snapshot jars before every invocation. Local manifests/jars are
retained for reproduction but are not distributed with the source repository.

## Allocation and interruption findings

The [refined attribution CSV](vector-attribution-results.csv) re-reads the existing
sorting recording, not a new workload. It reconciles every original allocation/execution
key, sample count and weight with [the original profile](primitive-sorting-profile-results.csv).
Its 22927 allocation samples carry 402270497440 bytes of sampled allocation weight;
the recording has 3136 Java execution samples. These are sampling estimates, not exact
allocated or retained bytes.

| Observed attribution | Allocation weight share | Execution sample share |
| --- | ---: | ---: |
| Benchmark density callback | 69.07% | 9.09% |
| Sampler frames outside the pinned callback boundary | 19.45% | 8.45% |
| Callback boundary, callback frame absent/unresolved | <0.01% | 0.32% |
| Diagnostics | 10.75% | 73.98% |
| Other | 0.73% | 8.16% |
| Missing stack | <0.01% | 0% |

No callback-only/unanchored samples were observed. There are 1745 truncated allocation
stacks (1743 diagnostic and two other), plus 33 allocation samples with missing stacks;
all are retained. No execution stack is marked truncated. An observed callback frame
supports attribution to the benchmark model; missing/inlined frames can still obscure
ownership, so the sampler category is not an exact exclusive-cost measurement.

Of 669 execution samples whose nearest diagnostic site is `interrupted`, the next
observed diagnostic frame identifies the sorting merge loop in 649 and sort-index
initialization in two. The remaining callers are autocovariance (16), average (one)
and variance (one); none is unresolved. Thus 651/669 are associated with the sorter.
This does **not** establish that interruption checks consume that proportion of runtime:
stack sampling, safepoints and inlining limit causal interpretation. Cancellation checks
remain unchanged. Any follow-up must preserve their cadence and interruption behavior.

Recording SHA-256:
`4949a94d5ea5984a653f3c0d63511565de1c58a2b92b4ca2ab1d29f012cdfdca`.
The raw recording remains local because it can contain environment-specific metadata.

## Reproducing the paired experiment

Use Python 3.11+ and JDK 17. Keep the audit tools in their own checkout while building
each pinned revision in a clean build checkout. At each revision, export the runtime:

```sh
sbt --server --batch "export examples / Runtime / fullClasspath" > classpath.log
```

Then run the snapshot command from the audit checkout, replacing paths and the full
revision with that build's values:

```sh
python3 -B tools/summarize_interleaved_performance.py snapshot --log classpath.log --out-root build-checkout/target/out --cache-root coursier-cache --output baseline-runtime --revision a144eb1a4a89d4ac0912b187b851d7845716c7d8
```

Repeat for `c2329aa008622953f95ab0acb344552cf2f4c9f8`, using `current-runtime` as the
new output directory. `--cache-root` must resolve the `${CSR_CACHE}` references from
the export, not an unrelated cache parent. Both outputs are exclusive new directories;
all runtime jars are copied and SHA-256 checked. A local `runtime.json` records relative
jar names, their hashes and the declared Git revision. The revision is supplied provenance,
not a reproducible-build attestation; make sure it matches the checkout you actually built.

After both snapshots exist, stop local builds/tests and run:

```sh
python3 -B tools/summarize_interleaved_performance.py run --java /path/to/jdk-17/bin/java --baseline baseline-runtime --current current-runtime --output paired-runs --baseline-csv docs/primitive-fft-performance-results.csv
```

The fresh output directory retains all eight logs, per-invocation temporary directories
and `interleaved-results.csv`. Each invocation gets the same explicit heap/stack settings
and an isolated temporary/home directory. Do not overlap the experiment with profiling.
Only the CSV is suitable for publication after review; logs, jars and runtime directories
can contain environment-specific metadata. The runner does not delete them.

On Windows, pass `--acl-script` to the snapshot/run commands when workspace ownership
requires an explicit grant. The supplied PowerShell hook receives `-Paths` with the exact
new item and `-Recurse` for the generated temporary tree. It must preserve unrelated
ACLs/ownership and return success only after verifying the required access. No account
names or local paths are stored in the checked CSV.

## CLI/API reference

`summarize_interleaved_performance.py snapshot` requires `--log`, `--out-root`,
`--cache-root`, `--output` and a 40-character `--revision`; `--acl-script` is optional.
It accepts this checkpoint's sbt 2 `List(...)` export, verifies embedded jar hashes/sizes,
copies the runtime and returns success with a manifest hash. Existing output, malformed
references, traversal outside declared roots and jar-identity mismatches fail.

`summarize_interleaved_performance.py run` requires `--java`, `--baseline`, `--current`,
`--output` and `--baseline-csv`. Optional parameters are `--pairs` (4, even and at least 2),
`--repetitions` (5), `--draws` (4000), `--warm-up` (500), and `--acl-script`. It returns
the complete CSV and a text summary. The benchmark jar and dependencies must match
byte-for-byte; exactly one Figaro jar must differ. Child-process errors, incomplete grids,
changed identities or non-timing differences fail the audit; partial output/logs remain
for diagnosis, not a successful performance claim. The checked full study uses only the
predeclared defaults, not the short smoke configuration.

`summarize_interleaved_performance.py check CSV` requires `--baseline-csv` and accepts
the same four study-size parameters. It is read-only and returns a pair-level report.
Wrong order, duplicates, missing warm-up/measured rows, changed runtime identities and
changed statistical outputs fail. The added CSV columns are invocation (zero-based),
pair, position within pair, variant, full revision and runtime-manifest hash; remaining
columns use the unchanged [benchmark schema](VECTOR_SAMPLING_PERFORMANCE.md).

`VectorProfileAttribution.main(args: String[]): void` takes an existing JFR path or
`--self-test`. Launch with `java -XX:-UsePerfData tools/VectorProfileAttribution.java ...`.
It reads without modifying the recording and writes sanitized CSV to stdout. It rejects
reported JFR data loss and propagates file/format errors. The self-test runs ten synthetic
classification cases; it is not a replacement for reconciling real recordings.

`summarize_vector_attribution.py` requires `--profile` (the original sanitized profile)
and exactly one of `--csv` or `--jfr`. JFR extraction also requires `--java`. Optional
`--output` exclusively creates the reconciled CSV; `--acl-script` grants access to it.
It verifies every original kind/category/class-or-execution-leaf/site count and weight,
not just grand totals. Invalid identifiers, multiple recording hashes, duplicate rows,
missing truncation status and reconciliation mismatches fail. No original profile is
rewritten and no raw JFR metadata is published.

Attribution CSV columns: recording SHA-256, kind, original category, new attribution
category, allocated class or execution leaf, original nearest Figaro site, observed
callback/interruption-caller site, stack-truncation flag, sample count and weight/count.
The reported hash identifies the recording read; checking a CSV alone cannot authenticate
its source without retaining/verifying that recording separately.

## Three common patterns

### 1. Check a suspected regression without changing sample work

Use the paired `run` command above with two built snapshots. Compare adjacent JVM pairs,
not an old log against a new run on a different machine. Before: the Gaussian 8D GPSS
median rose in one comparison. After this audit: all four pair estimates and their range
are available, with every trace and work count checked. Results still describe these
fixtures; they do not prove what a different model or JVM will do.

### 2. Separate callback allocation from sampler overhead

```sh
python3 -B tools/summarize_vector_attribution.py --jfr sorting.jfr --java /path/to/jdk-17/bin/java --profile docs/primitive-sorting-profile-results.csv --output attribution.csv
```

Before: the original `sampling` category included model code called by the sampler.
After: `callbackObserved` requires a benchmark density frame above the sampler in the
recorded stack. `samplerObserved` identifies a sampler frame outside the pinned callback
boundary. `callbackBoundaryUnresolved` retains ambiguity when that frame is absent.
`callbackUnanchored`, `other` and `unknown` prevent silent reassignment or lost records.

### 3. Investigate interruption samples without weakening cancellation

Read the attribution report's observed next diagnostic frame. Before: a large count at
`interrupted` did not identify the caller. After: the report distinguishes the merge loop,
index initialization, centering and reductions where observed. Use this to design a
controlled microbenchmark with the same check cadence; do not interpret stack samples
as permission to remove checks or proof of the helper's exclusive CPU cost.

## Verification

Compilation, all 160 modernization regressions, 49 documentation/report-tool tests and
ten Java classification checks pass. Before full measurement, the same snapshot runner
completed a four-JVM/two-pair smoke study (432 records, one measured round, 100 draws,
20 warm-up transitions); those short timings are not used for speed claims. The full
2016-record dataset and refined attribution both pass their exact reconciliation checks.
Scaladoc/reference freshness verifies the unchanged 11321 public method entries in 42
files; local documentation links pass. Scaladoc retains four existing warnings. Production
library code, Scala tests and benchmark source are unchanged from `c2329aa0`.

CI adds the Java classifier self-test and the two checked-dataset validation commands,
alongside the existing Python test discovery and regression/profile/reference gates.
It does not rerun the lengthy eight-JVM experiment or enforce timing thresholds. Local
results do not imply the entire historical test suite is green.

## Recommended next investigation

Keep the sorting implementation and existing sampling budgets. The next bounded library
investigation is an isolated diagnostic sorting/rank-normalization benchmark with exact
output oracles and unchanged interruption cadence. Measure normal-score calculation and
sort-loop costs separately before choosing an implementation change. Stack samples alone
are insufficient justification for weakening cancellation or approximating rank scores.

For applications limited by allocation/GC, also profile the actual density callback: this
benchmark attributes most allocation weight there. A separately labeled benchmark-callback
experiment could assess boxing/temporary reductions, but changing that fixture must not
be reported as a Figaro library gain or mixed into these unchanged-work comparisons.
Do not begin a broad sampler rewrite based on the old combined sampling category.

## Gotchas and related modules

- Four JVM pairs are a small sample. Five deterministic seed rounds within one JVM are
  correlated workload observations, not five independent machine-level replications.
  Pair ranges are not confidence intervals, and balanced order is not randomization.
- Fresh direct-Java launches differ from the historical sbt runner's execution context.
  Both audit variants use the same launcher; do not combine their absolute timings with
  the old single-JVM measurements or replace inconvenient historical observations.
- Exact fingerprints ensure unchanged observable work/results for this grid, not a proof
  of mixing, convergence or coverage. The wrong-mode and poor-mixing cases are retained.
- Stack attribution is benchmark-specific, pinned to `VectorSamplingPerformance.density`
  and `VectorSliceSampler.evaluate$1` line 86 at these revisions. It is not a generic
  classifier for arbitrary user callbacks or changed source layouts. Revalidate it before reuse.
- Truncated or missing frames, inlining and sampling bias limit causal interpretation.
  An observed callback is affirmative evidence; its absence at the callback boundary is
  unresolved. Allocation weights are neither exact object counts nor retained-heap/DRAM
  measurements. Execution samples are not independent wall-time percentages.
- Snapshot/output creation is exclusive. An interrupted or failed experiment leaves
  evidence in place and is not automatically resumed, overwritten or selectively rerun.
  Choose a fresh directory for a deliberately new study and report it separately.

Related: [sorting checkpoint](PRIMITIVE_DIAGNOSTIC_SORTING.md),
[FFT checkpoint](PRIMITIVE_FFT_AUTOCOVARIANCE.md),
[vector profiling](VECTOR_ALLOCATION_PROFILE.md),
[multi-chain vector API](MULTI_CHAIN_VECTOR_SAMPLING.md), and
[diagnostic reliability](MCMC_RELIABILITY.md).
