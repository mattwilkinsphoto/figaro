# Decision-focused stratified prototype protocol

[Research provenance](RESEARCH_REFERENCES.md#inf-15).
This frozen protocol precedes prototype execution. Research/test sources only;
released stopping estimator and development public API remain byte unchanged.
No merge, release, runtime promotion, automatic fallback or threshold default.

## Numerical contract and independent derivation

Inputs are stored binary64 log weights (finite or -Infinity), at least one positive
mass, N=2..4096, finite log span <=1,000,000. A stratum j and stored binary64 u in
[0,1) define q=j+u **exactly**, with comparison N*prefix > q*total. Never form a
rounded global (j+u)/N point. Choose the first strict upper boundary; exact ties
advance past that boundary. This is correctness relative to supplied finite-bit
coordinates and logs, not an exact continuous-uniform or universal unbiasedness claim.

Fast path encloses exp(log_i-peak) with outward-rounded subtraction, Java Math.exp
(documented one-ulp error), two outward neighboring values, and outward sums and
products. Underflow is enclosed, not declared zero. If a boundary's sign is not
resolved, recompute original-log intervals at 40, 80 and 160 decimal digits with
java.math.BigDecimal FLOOR/CEILING arithmetic. Cache at most three prefix tables.
No extra random numbers are consumed by refinement. Exact zero support and
symbolic equal-log group ties are handled explicitly. Unresolved comparisons fail.

The high-precision exponential enclosure is a small independent derivation, not
copied library code: for x>=0, halve exactly until z=x/2^s<=1/8, sum the positive
Taylor series for exp(z), and bound the remainder after term k by
t_(k+1)/(1-z/(k+2)). Take an outward reciprocal, then square s times to enclose
exp(-x). Limit 24 halvings and 512 Taylor terms per evaluation. Tightness is tested
independently; this is not a machine-checked formal proof. Repeated equal logs may
reuse enclosures. Refusal is explicit if any arithmetic/resource limit is reached.

Java BigDecimal point-function libraries were considered, but a point estimate at
a requested precision does not itself provide the directed enclosing comparisons
required here. No new dependency is installed. Python Decimal.exp at 100/180 digits
is an independent validation oracle, not the implementation's Taylor algorithm.

## Gates frozen before execution

1. Unit/adversarial checks: zero support, u=0 and nextDown(1), exact ties, boundary
   neighbors, extreme positive/negative log offsets, underflow, log-span/N limits,
   interruption, and precision exhaustion. Exhaust finite midpoint grids against
   exact equal-log outcomes; check RNG draw counts do not increase on refinement.
2. Replay each of the previous 6,144 captured vectors with N stratified coordinates
   from an independent fixed stream (seed 700001 + vector index). Emit exact log/u
   bits, selected indices and refinement counts. All ordinary captured cases must
   resolve; independent 100/180-digit ancestor indices must agree. Check fast raw
   prefix enclosures against independently computed exponentials. Separate forced
   adversarial cases may refuse; every resolved choice must match the oracle.
3. Independently check decimal exponential enclosures on x=0, 1e-20, .125, .5, 1,
   20, 40, 745, 1000, 1000000 at all three precision levels. Reference convergence
   within 1e-80 absolute for weight/prefix arrays; no numerical floor substituted.
4. Whole-filter comparison: same six fixtures and independent references, N=256,
   Always and ESS .25/.50/.75, both prototype and unchanged log-tree. Fresh seeds
   60001..60256, two fresh serial JVMs (replay, not additional independent seeds).
   Fixed callbacks, carried weights and evidence accounting match original kernel.
   Any failed trajectory fails this ordinary-domain gate; retain all rows. Check
   first/middle/final unnormalized evidence, first/second moments and positive mass
   against references using the unchanged 6-SE + 1e-4*max(1,abs(target)) screen.
   Require scientific/status/work replay excluding timing. Report completion,
   refinements and total inclusive time descriptively; no superiority gate or claim.
5. Run unchanged fixed-budget tests, diagnostic-tool tests, documentation/reference
   checks. Preserve all earlier protocols/results. No production acceptance inferred
   from test-kernel success; integration/packaged-consumer/remote-CI gates remain.

Budget: two hours compute, 6 GiB JVM heap, 2 GiB evidence. If a numerical correctness
gate fails, retain failure, diagnose before patching, and distinguish revised runs.
No acceptance changes, seed replacement or retry-until-success.
