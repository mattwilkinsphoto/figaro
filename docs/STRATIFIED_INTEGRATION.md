# Decision-focused stratification integration

[Research provenance](RESEARCH_REFERENCES.md#inf-15).
[User guide](FIXED_BUDGET_FILTER.md). [Preserved prototype](STRATIFIED_DECISION_STATUS.md).

Status: integrated on the development branch; local gates passed, remote CI pending. Not merged
or released. The released stopping estimator, log-tree selector and policy defaults
are unchanged. This document distinguishes numerical review from executed tests.

## Numerical review

The implementation selects the first prefix satisfying
`N * prefix > (j + u) * total`, where `u` is the exact value of the supplied binary64
coordinate. It never rounds `(j+u)/N` into a global CDF point. Equalities advance
past that boundary; a zero coordinate selects the first positive mass.

- Subtraction is enclosed by neighboring binary64 values. Monotonic exponential
  evaluation with Java's documented one-ulp accuracy is expanded by two neighbors;
  raw prefix sums and nonnegative products are rounded outwards. Underflow gives
  an interval containing the tiny positive mass, not a declaration of zero mass.
  At N<=1,000,000 the raw prefixes and products remain far below overflow.
- An inconclusive interval is not a decision. At N<=4096 the same coordinate is
  compared using directed BigDecimal arithmetic at 40, 80, then 160 digits.
  `new BigDecimal(double)` preserves input bits; decimal-string conversion would
  change the problem. The exact log-span is checked before selecting ancestors.
- For x>=0, exact halving gives z<=1/8. After term k of the positive exponential
  series, subsequent term ratios are at most z/(k+2), so the tail is bounded by
  `t_(k+1)/(1-z/(k+2))`. Directed operations bound both the partial sum and tail.
  A reciprocal reverses the bounds, and positive squaring preserves them. Limits
  of 24 halvings, 512 terms and three precision levels remain explicit.
- Equal-log group coefficients can prove exact ties without approximate equality.
  If all positive weights share one log value, the selected positive-support rank
  is exactly `floor((j+u)*positiveCount/N)`. With no zeros this is simply j. This
  exact special case avoids repeated million-element scans or decimal tables.
- Failure never redraws a coordinate, retries a filter step or changes methods.
  Interruption is checked during prefix/series work and preserves the interrupt flag.

This is an engineering review of the derivation, not a machine-checked proof or
an independent human audit. Python Decimal.exp supplies an algorithmically separate
oracle; agreement at two precisions is numerical evidence, not a proof for all inputs.
Java Math.exp may vary across platforms within its accuracy contract. The comparison
bounds accommodate that contract; tested replay is not universal cross-platform bit identity.

## Population and work limits

The existing configuration range remains **2..1,000,000 for both methods**. It does
not promise that every stratified population is numerically resolvable.

| Condition | Stratified behavior |
| --- | --- |
| Fast enclosing comparison resolves the decision | Supported throughout the population range |
| All positive log weights are equal, possibly with zero-mass entries | Exact rational selection throughout the range |
| Ambiguous nonuniform comparison, N<=4096 | Up to three decimal prefix tables at 40/80/160 digits |
| Ambiguous nonuniform comparison, N>4096 | `StratifiedPrecisionLoss`, with refinement-population reason; no decimal tables allocated |
| Exact positive-log span exceeds 1,000,000 | Explicit domain refusal before ancestor draws |
| Comparison remains unresolved at 160 digits | Explicit precision-budget refusal |

The binary64 tables use O(N) storage. Decimal refinement is O(N) storage per level
but only within its separate 4096-particle budget. These are operation/storage
bounds, not a wall-clock guarantee. Large nonuniform populations may refuse even
when an unlimited-precision method could resolve them. Log-tree is an explicit
alternative for a separately declared run, not an automatic fallback.

Successful step diagnostics expose refined decision count, tables built, maximum
decimal precision and multi-group exact ties. Uniform-support shortcuts are not
counted as decimal refinements or multi-group ties. Numerical failures expose their
reason and invalidate the posterior; they do not fabricate a completed step report.

## Integration acceptance gates

These gates were selected before integration results, without changing the earlier
statistical screens or replacing seeds:

1. Repeat adversarial numerical tests against production code, including real
   160-digit exhaustion, exact-span boundaries and N=4096/4097/1,000,000. Exercise
   public lifecycle, RNG consumption, zero support and explicit terminal refusal.
2. Re-run all 6,159 captured/adversarial cases and 30 exponential controls against
   independent 100/180-digit Decimal references. Preserve all outcomes.
3. Run the same six fixtures, seeds 60001..60256, N=256 and four explicit policies
   through the public API in two JVMs, requiring completion, replay and all 576
   unchanged 6-SE regression screens. The second JVM is replay, not new seeds.
4. Run broader filtering regressions and the unchanged 450-check continuous
   stopping-filter benchmark; verify packaged consumers from the exact JAR.
5. Update generated API documentation/references and run remote CI on the branch.
   No merge or release until gates pass and the remaining limitations are reported.

No performance superiority, ideal-uniform unbiasedness, missing-mode detection,
or universal ESS threshold follows from this repair. Historical refused runs and
test-only prototype sources remain preserved separately.

## Executed local results

- 30 production numerical/capacity/lifecycle tests passed, including one million
  equal-weight selections, exact-domain refusal and genuine 160-digit exhaustion.
- All 1,572,906 ancestor decisions in 6,159 cases matched the separate Decimal
  oracle. No fast-prefix or exponential enclosure failures; all 30 exponential
  checks passed. Five adversarial decisions refined at 40 digits.
- Both public-API JVM runs completed all 12,288 trajectories, with zero failures,
  all 576 reference screens passed and scientific/status/work replay identical.
  None of these ordinary trajectories required decimal refinement. The raw `proto`
  method label is retained solely for the unchanged analysis parser; these runs
  execute the production public API, not the preserved prototype kernel.
- Broader filtering regressions passed all 80 tests. The unchanged released-filter
  continuous benchmark passed all 450 checks across 7,680 trajectories.
- Eight independent numerical-tool tests, 41 documentation tests, reference mapping,
  generated API freshness and 15,471 local link targets passed.
- All five isolated packaged-consumer checks passed from the expected JAR. An
  initial warmed consumer compiled against an earlier same-coordinate bundle and
  failed on the new fields. Dependency records identified that old path. A separate
  consumer output tree and fresh action cache resolved the exact new bundle; the
  hash check was retained. Both runs are preserved, not counted as two passing runs.
- Hash audit preserved all 355 existing runtime files other than the intended
  FixedBudgetParticleFilter edit; DecisionStratified is the new internal file.
  Released stopping-filter sources, previous frozen protocols and controls are unchanged.

These results establish local integration evidence, not remote CI completion or a
publication decision. Remote CI remains a gate; no merge or release has occurred.
