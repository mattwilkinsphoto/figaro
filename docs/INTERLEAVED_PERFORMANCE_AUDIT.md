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
