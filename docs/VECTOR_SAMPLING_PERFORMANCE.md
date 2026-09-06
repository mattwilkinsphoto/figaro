# Multi-chain vector scaling study

## Protocol recorded before measurement

Branch `modernize/vector-sampling-performance`, based on `92e3b646`. This is a benchmark,
not a kernel/default/API migration; snapshot modern.10 and toolchain remain unchanged.
Four independent chains each request 4000 retained draws after 500 discarded warm-up
transitions, with a generous 100 million density-call cap per chain and unchanged
10000-proposal search limits. Worker counts are 1, 2, and 4. GPSS and quantile are both
tested on all six fixtures: Gaussian 8D, Gaussian 32D, correlated Gaussian 32D,
positive exponential 32D, dense Gaussian likelihood 8D, asymmetric mixture 8D.

Five measured rounds follow two negative-index JVM warm-up rounds. Within each
fixture/method, rotate and alternate worker order to reduce order bias. Root seed is
`420013 + 7919 * round`, fixed across workers and fixtures; chain seeds follow the
production wrapper's index-ordered expansion. The complete study contains 252 runs,
180 measured and 72 JVM warm-up runs. No after-result tuning or dropping slow rounds.

Gaussian targets have unit variance. Correlated covariance is `0.05 I + 0.95 11^T`.
Positive coordinates are independent rate-1 exponentials. The mixture is
`0.9 N(-2*1, 0.25 I) + 0.1 N(3*1, 0.25 I)` with a shared mode label.
The dense-likelihood model has a standard Normal prior and 64 zero-valued unit-noise
observations with normalized Hadamard design rows in eight dimensions: `X'X = 8 I`,
so its exact posterior is `N(0, I/9)`. The density evaluates the actual 64 row dot products,
not a simplified posterior expression or synthetic delay. Starts are `0.5 + chain/4`
in every coordinate, independent of worker count, with no exact-target initialization.

Package-private instrumentation separates serial construction/validation, pool creation
through sampling and joined shutdown, and aligned coordinate diagnostics/result preparation.
Their times sum to runner end-to-end time; no per-density timers alter the hot loop.
JVM process CPU time includes concurrent runtime/GC work. GC time is the sum of available
collector counters, not an allocation or peak-memory measurement. SHA-256 fingerprints of
all traces, seeds, evaluation counts, statuses and coordinate diagnostics must match across
worker counts, excluding timing. Fingerprint computation, CSV printing and validation are
outside the measured runner interval. Only one benchmark process runs at a time.

Report median paired end-to-end and sampling-phase speedup relative to one worker,
diagnostic share of wall time, worst-coordinate raw-mean ESS per end-to-end second,
maximum coordinate R-hat, warning-bearing runs and coordinate mean errors. ESS/s is a
diagnostic estimate, not proof of convergence; high R-hat or target errors can invalidate
a superficially attractive throughput number. Derived events/mode weights are not diagnosed
in this timing study. Failed/incomplete runs remain explicit, and no timing gain can make
them successful inference. Cross-method comparisons use equal requested draws, **not equal
density-call budgets**, so these results are primarily within-method scheduling comparisons.

The measurements are machine/JVM/workload-specific and exploratory, not a JMH study,
universal speed guarantee, coverage experiment, or evidence for changing sampler defaults.
