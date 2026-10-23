# Guided fixed-budget milestone status

[Research provenance](RESEARCH_REFERENCES.md#inf-16).
[Frozen protocol](GUIDED_FIXED_BUDGET_PROTOCOL.md).

Baseline: verified published Figaro 7.3.0. Development branch only; no promotion,
merge or publication implied. Released stopping estimator and defaults unchanged.

## Requirements and acceptance checklist

- [x] Stage 1: primary literature, implementation comparison and weight accounting.
- [x] Freeze protocol before implementation/benchmark outcomes.
- [ ] Stage 2: opt-in guided/auxiliary API, compatible report shapes, documentation.
- [ ] Deterministic accounting, Gaussian, support, lifecycle and replay tests.
- [ ] Stage 3: independent finite oracle and fresh-seed comparative validation.
- [ ] Three-JVM replay, all-outcome correctness and utility decision.
- [ ] Released-filter regressions, documentation checks, packaged consumer.
- [ ] Reproducibility archive and final audit.
- [ ] Remote CI and explicit promotion decision before merge/release.

No performance result has yet been obtained for this extension. Known
counterevidence is retained: inaccurate guidance can worsen variance; extra
density/look-ahead work can erase same-N gains; resampling cannot recover lost
histories. Fixed-budget scheduling and guidance succeeding separately is not
validation of their combination.
