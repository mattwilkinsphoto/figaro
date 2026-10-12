# Distribution and inference integration: execution record

Status: **verified complete and released as 7.0.0 on GitHub and Maven Central**.
Baseline: main `269f9086`, published
6.1.0. All five stages below were approved on 2026-10-11. No release publication
was authorized by the original implementation plan. The maintainer subsequently
authorized documentation-bridge examples, commit/push, remote CI, a clean release
candidate, merge and publication/testing of 7.0.0. Preserve acceptance criteria and historical
results; record failed runs as well as successful ones.

Research provenance: [INF-04](RESEARCH_REFERENCES.md#inf-04).

## Requirements and acceptance checklist

### Authorized 7.0.0 release follow-through

- [x] Add old/new examples and rationale to the documentation bridge; no separate
  existing-user migration review is required by the maintainer.
- [x] Commit scoped work and pass remote branch CI (0c6dffb8; runs 38200042513 and 38200082636).
- [x] Pass clean-checkout, independent empty-cache reproducibility and packaged-consumer gates on the branch.
- [x] Merge to main, record consumer-impact release eligibility, and pass main CI.
- [x] Pin the verified source/bundle in the signing workflow; tag and publish 7.0.0.
- [x] Verify Central bytes and independent Central-only consumers; record receipts.

### Feature implementation

The feature branch merged through PR #3 at `13dd276b`. Final release source
`9468aacb` passed all four [main CI jobs](https://github.com/mattwilkinsphoto/figaro/actions/runs/38202501464),
including clean byte-reproducible JARs and both independent consumers. The immutable
[GitHub 7.0.0 release](https://github.com/mattwilkinsphoto/figaro/releases/tag/v7.0.0) is available.
Before freezing the bundle, its installation README was clarified:
bundle creation is not a publication action; Central availability must be verified
separately. The previously published baseline is 6.1.0; consumer impact warrants
the approved 7.0.0 major release. Its required release gates have now passed.
The final source also corrects the bridge's log-particle return expression to the
case-class constructor and includes both old/new expressions in the standalone
consumer compile check. That check compiled and ran against the verified local
candidate and in the final-source remote packaged checks.

Central staging [passed signature and Sonatype validation](https://github.com/mattwilkinsphoto/figaro/actions/runs/38204303085).
Deployment `54dd64e7-80b0-4bf5-bfd2-a19422aac56c` was submitted for publication.
The [first publish wait](https://github.com/mattwilkinsphoto/figaro/actions/runs/38204433317)
timed out while the Portal still reported `PUBLISHING`, not `FAILED`.
The [same-deployment continuation](https://github.com/mattwilkinsphoto/figaro/actions/runs/38205220510)
does not upload another bundle or repeat a publish request while it is `PUBLISHING`.
That continuation confirmed `PUBLISHED`, matched all four Central artifact hashes
to the original release, and passed both Central-only consumers on 2026-10-12 UTC.
No deployment was re-uploaded and no release tag or artifact was replaced.
The two earlier main runs (38201661393 and 38201811585) were superseded/cancelled
after documentation refinements; the final source completed its own full gate.

Released thin JAR SHA-256:
`3d6806de0c895b432c1fc699cf3226df9ec0f8c8ad021479005e5838538a4eb4`.
Bundle SHA-256:
`f26043a59b6741ee7c2372ce694253e2bc48c65bd51aa46a10aa94a6cd4a6ac6`.
See the [publication record](MAVEN_CENTRAL.md) for coordinates and source/signing evidence.

- [x] Stage 1: reproduce particle-weight underflow and MH ratio overflow; implement
  stable log-domain paths with compatibility controls. Verify finite/extreme weights,
  impossible evidence, sequential/parallel filtering, MH and annealing semantics.
- [x] Stage 2: support observations in owned multi-chain MH with explicit boundaries.
  Verify posterior controls, normalization for stochastic parameters, dynamic children,
  ownership, cancellation and worker-count replay; never silently drop evidence.
- [x] Stage 3: exact finite-count integration for hypergeometric, small multinomial
  and finite mixtures. Verify enumerated normalization/posteriors and explicit work
  budgets; distinguish exact finite laws from sampled approximations.
- [x] Stage 4: support-aware proposals for positive, bounded, circular and matrix
  variables. Check proposal corrections, support preservation and representative
  posterior accuracy/efficiency against the retained baseline.
- [x] Stage 5: selectively expand numerical regimes based on independently checked
  algorithms, including large counts, near-zero extreme-value shape and matrix
  boundaries. Retain explicit refusals where finite precision cannot support a law.
- [x] Update user/API guides and scientific reference mappings for delivered changes.
- [x] Run focused and broader regression, documentation and packaged-consumer gates locally.
- [x] Report cumulative consumer impact and release eligibility; no automatic publish.

## Evidence and decisions

- Deterministic controls reproduced particle-filter underflow and overflow, and
  the legacy `HasLogDensity` ratio refusal. The two filtering controls now pass.
- Existing tests cover bounded importance and constrained-MH workflows; they are
  counterevidence to any claim that the distribution additions are merely stubs.
- Literature/method assessment precedes substantial implementation. Public APIs and
  ordinary-range behavior should be preserved where possible. Any incompatible
  architecture or evidence-semantics choice requires a maintainer decision.
- Local execution is available through approved elevated execution after the normal
  runner failed during startup. No sandbox or system permission policy was changed.
- Approved architectural decision: fail explicitly on zero viable particles by
  default; expose `ZeroWeightPolicy.LegacyUniform` only as an opt-in compatibility
  fallback. No claim that zero viable particles proves impossible evidence.
- Branch: `codex/distribution-inference-integration`; development version
  `7.0.0-SNAPSHOT` because the strict failure default and protected filtering
  extension-hook migration change existing contracts. Nothing published.
- MH adds an additive log-ratio hook with an explicit prior-proposal trait for final
  modernization adapters. Existing custom legacy proposal hooks remain delegated
  to, not silently replaced. Legacy ratio overflow still requires migration.

## Executed validation

This section preserves intermediate results; pending statements below describe
those runs, not current blockers. See **Final local acceptance** for their resolution.

- Baseline run: 3 tests, 1 passed and 2 failed as expected from the defect hypotheses.
  Underflow returned `-Infinity` instead of `-1000.6931471805599`; overflow returned
  `Infinity` instead of `918.2614484753785`. These are analytic Laplace fixtures,
  not stochastic performance measurements. sbt also emitted a worker-shutdown
  `Promise already completed` exception after reporting the individual results.
- First implementation run: production compilation succeeded; test compilation
  failed because a new custom-Atomic fixture lacked its required density method.
  The fixture was corrected without changing numerical acceptance criteria.
- Corrected run: 18 tests passed across `InferenceIntegrationRegressionTest` and
  `ParticleFilterTest`. Includes sequential/parallel three-step log evidence,
  strict/legacy zero-weight behavior and proposal compatibility. The initially
  requested annealer suite name did not match a suite; annealer validation is
  **still pending**, using the actual `AnnealingTest` suite next.
- A broader 59-test selection passed 56 and failed three: two legacy anytime
  annealing intervention assertions and the following caller-RNG restoration check.
  A control restoring only the old MH proposal call path reproduced the same three
  failures. This is not a full-main baseline. The isolated intervention tests passed;
  their lifecycle and MAP expectations require independent review, not tolerance changes.
- Observation integration: 33 tests passed (7 observation, 18 existing multi-chain,
  8 log-integration). Caller-RNG restoration passed without the annealing suite.
- Finite enumeration: 15 tests passed (5 finite-count, 7 observation, 3 probability).
- Support-aware proposals: 17 tests passed (6 new, 11 existing Gaussian-block).
  Includes independent finite-difference Jacobians and scalar/circular/matrix moments.
  One concentrated-positive fixture gave much higher ESS with the log walk, but is
  not a representative performance study or a universal speedup claim.
- Documentation tooling: reference checker passed; all 38 documentation tests passed.
- Selective range expansions are under validation. Contract-refusal tests move from
  shape 1e-12 to 1e-15 and Wishart df=2 to df=1.9 for a 2D law, because the formerly
  refused points are now explicitly tested as supported, not to hide a failure.
- Range validation initially passed 41/42: an inverse-Wishart test retained the old
  df cutoff. It was moved to 1.9 for a 2D law; newly supported df=2 has separate
  density/draw checks. No numerical tolerance was relaxed.
- One later test fixture failed compilation because the inferred common type of
  sequential/parallel filters lacked advanceTime. Typed callback pairing fixed the
  fixture; no production API or acceptance criterion was changed for that error.

## Final local acceptance (2026-10-11)

- **Verified complete, stages 1–5 within the stated boundaries:** the final combined
  run passed 86 tests across ten suites; the final guard suite passed 11 tests,
  including two additional parallel-policy/finite-log overflow checks.
- **Verified complete, broader regression selection:** 59/59 passed. The original
  annealing failures were resolved by independent joint enumeration (the two old
  false expectations contradicted the specified model), fixed-work execution and
  finally cleanup. The following caller-RNG check then passed in the combined run.
  This does not claim a new general annealer proof or a full-repository test run.
- **Verified complete, proposal comparison:** three seeds, two chains, 20000 retained
  draws per chain, 2000 warm-up transitions, concentrated lognormal likelihood versus
  prior-only control. All analytic-mean checks passed. Mean ESS increased with the
  log walk in the concentrated case (roughly 8300–8700 versus 175–235); prior proposals
  won without evidence (roughly 39400–39500 versus 4500–4800). Some baseline R-hat
  values exceeded 1.01. Short-run timings and this single scalar family are not a
  universal speedup claim. Exact parameters and CSV output are in the study source
  and retained execution evidence.
- **Verified complete, documentation:** research checker, 38 documentation tests,
  12953 generated public-method entries and 14917 local-link targets passed. An
  earlier check overlapped Scaladoc regeneration and saw missing pages; a later
  completed-generation check passed. Four existing Scaladoc warnings remain
  (unsupported refinements/options and an unrelated unresolved link).
- **Verified complete, packaging:** thin/fat/source/Scaladoc artifacts passed legal,
  classfile and isolation checks. The independent baseline and integration consumers
  passed from the final Maven-layout candidate with Ivy local excluded. Thin JAR
  SHA-256: `d3b115cd7aa60c3c90be7976fd6961ca7cb2aef435d0a6dc18677ccb5e9c0516`.
  Four packaging tests and seven artifact-validator tests passed. No Central upload.
- **Not performed at this local checkpoint (completed later above):** remote CI, a clean-checkout reproducible rebuild, commit, push,
  merge, release tagging or public publication. CI now includes the new suites and
  development-version candidate consumer checks; remote execution is not implied.

## Consumer impact and release decision

Published baseline is 6.1.0 (source `85a12fd559dee4e1cc1233036fd2bbaa06331a65`).
The branch started at main `269f9086ae0162e7dc53093225b6000a5c8d71be` and merged
at `13dd276b1ec172ce901a1b95772ec61a3964cb0d`. A new release was warranted:
stable extreme-likelihood filtering, explicit failed-filter semantics,
new observation/enumeration/proposal APIs and wider numerical ranges affect consumers.
Approved and published version: **7.0.0**, because strict zero-particle failure and
protected filter hook migration change contracts. Final source is `9468aacb`;
later publication-configuration/documentation commits do not change released bytes.
Consumers affected by silent zero-weight fallback should prioritize adoption after
reading the explicit old/new bridge examples. The maintainer requested those examples
instead of a separate existing-user migration review. CI, clean-source/reproducibility,
signing, publication and Central-only consumer gates are complete. No additional
version increment is warranted solely for the release-record documentation follow-up.
