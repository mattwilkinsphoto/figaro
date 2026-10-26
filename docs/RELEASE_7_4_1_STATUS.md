# Figaro 7.4.1 preparation

**Release candidate prepared and validated; publication not authorized or performed.** Published
baseline: [7.4.0](RELEASE_7_4_STATUS.md). Snapshot integration CI passed; the separate
preparation source now uses final version 7.4.1. This page records gates, not a promise
that an unexecuted check passed or that the version exists on Maven Central.

## Scope and consumer impact

The only runtime change is the [tied-quantile diagnostic repair](TIED_QUANTILE_REPAIR.md).
Repeated states are ordinary in MH traces. A rounded cutoff can incorrectly exclude
ties and change tail ESS. This compatible correctness fix warrants a prompt patch
release under the [consumer-impact policy](../CONTRIBUTING.md#consumer-impact-release-policy).

Sampling kernels, stopping estimators, defaults and strict-calibration thresholds
remain unchanged. Tail-based warnings/calibration may change near a threshold;
the retained replay's unchanged decisions do not prove otherwise for all workloads.
No learned joint pilot, multi-root tuner or higher-dimensional GVM fitter ships.
The existing retrospective/PDF remains a dated historical record.

## Evidence already available

- Independent replay: all 5,798 summaries from 272 retained attempts, unchanged
  tolerances; non-tail diagnostics bit-identical; 45 changed tail ESS results and
  no retained qualification-decision changes. No fresh sampling or confirmation.
- Original isolated repair commit `43ce624e`: [five-job branch CI passed](https://github.com/mattwilkinsphoto/figaro/actions/runs/40366213712),
  including packaging/consumer checks. This predates integration with the report
  publication and does not substitute for the new integration/final-version gates.

## Required preparation gates

- [x] Current-main integration, preserved source/evidence and roadmap reconciliation.
- [x] Targeted arithmetic, diagnostic, calibration and stopping regressions; independent audit controls.
- [x] Binary compatibility through the immediate 7.4.0 baseline and prior retained baselines; TASTy/compiled-client checks.
- [x] Fresh integration branch CI, reviewed fast-forward into main and successful post-merge CI.
- [x] Final-version 7.4.1 source, clean reproducible runtime artifacts, POM/legal inspection and offline Maven-layout bundle.
- [x] All existing packaged consumers plus a public diagnostic regression; verify the loaded artifact hash and a published-7.4.0 negative control.
- [x] Record exact source/artifact hashes and executed validation; retain original failures and timing advisories.

Local integration validation passed: 63 Scala tests across the diagnostic,
reliability, calibration and stopping suites; six independent Python audit controls;
MiMa through 7.4.0; 7.4.0 TASTy and compiled-client replacement checks; 44 documentation
tests; 18 CI-tool tests; and the reference/link checks. A separately compiled public
diagnostic consumer passes against the repaired snapshot JAR and reproduces the
expected missing-tail-ESS defect against the hash-verified published 7.4.0 JAR.
These are local results, not substitutes for the pending final-version gates.

The report-only [post-merge main run passed all five jobs](https://github.com/mattwilkinsphoto/figaro/actions/runs/40383127831).

## Final-version checkpoint

The [integration run](https://github.com/mattwilkinsphoto/figaro/actions/runs/40388490501)
passed all five jobs with no failed steps at `bd90247ac43ce78cb0a3ec2a430d6494fa460aa2`.
That commit was fast-forwarded into main after success; its
[post-merge run](https://github.com/mattwilkinsphoto/figaro/actions/runs/40392523746)
also passed all five jobs with no failed steps. It includes compatibility
through 7.4.0, byte-reproducible snapshot builds, all seven packaged consumers and
the immutable 7.4.0 negative control.

Final source **`a72516d1419db87757bdcd8ae42db0439b5d470b`** passed
[all five final-version jobs](https://github.com/mattwilkinsphoto/figaro/actions/runs/40392733808),
with no failed steps. This separate result includes two fresh clean Linux builds
with matching runtime JAR hashes, classifiers/legal/POM checks, compatibility
through 7.4.0 and all seven consumers on the final Maven layout. The downloaded
Linux bundle passed all seven consumers again on Windows after a clean resolution.

The prepared publication candidate is the **Linux bundle from that exact run**,
not a later rebuild or the separate local bundle. No tag or GitHub release exists
for 7.4.1. Later documentation-only closeout commits do not change this source pin.

| Prepared artifact | SHA-256 |
| --- | --- |
| Maven ZIP | `43830e3a28bb4bfbb30933da1a18fde28b9023d5de722f1071a78597e7c3ba66` |
| Thin JAR | `cdf9a7fdaa9c3beb1f3fcb1feb6ead3b525c28e5920246cd5b3bbf4d5c963adb` |
| Sources | `975f66922afbfa6660107f502b6e41b01cb34cbc6ec4197acac0e8c85e3b9d97` |
| API documentation | `56d6c06e1beb7489fdec7b1a92381e1bed5c3195d6ab39f4df72c3011eb14d42` |
| POM | `4744858bd21b7a4d5bf2ac7131318116eafaac1ca084e27532320e58d853a5b3` |
| Supplemental fat JAR | `fa730fa3b0eb61ba058863ee246f8168f02b7046fafb73f09f0f9510b68fc121` |

### Preserved limitations and resolved test-environment failures

Four existing Scaladoc warnings remain. Same-platform clean-build reproducibility
is verified; Windows/Linux JAR byte identity is not claimed. Comparing thin-JAR,
source and documentation payloads found only three legal-text entries differing
between local and Linux builds; those entries match after CRLF/LF normalization.
Compiled classes and TASTy payloads match. Use the pinned Linux bytes above.

The additional Windows consumer initially resolved the earlier local bundle with
the same coordinates and correctly failed its expected-hash assertion. Its saved
dependency report confirmed that path. `clean; update` followed by the unchanged
seven checks resolved the Linux bundle and passed. No hash check or scientific
criterion was relaxed; the failed attempt is retained. Other retained setup failures
were missing scientific Python imports and an empty optional Maven-root setting;
the successful reruns used the retained dependencies and omitted the empty setting.
An optional metadata query encountered an expired TLS certificate on one Central
endpoint; the alternate official endpoint succeeded with normal TLS verification
and reported 7.4.0 as latest. No certificate verification was disabled.

The [caller-specified joint-block API review](TRANSFORMED_JOINT_BLOCK_API_REVIEW.md)
is documentation only. No adapter or automatic pilot is included in 7.4.1.

## Separate publication gates — not authorized here

No release tag, GitHub release, signing/upload or Central deployment is created by
preparation. A later explicit approval must pin the reviewed final source/bundle
and consumer checks, stage and review the signed payloads, publish once, then
compare all four Central artifacts and execute the complete Central-only consumer
set. Never overwrite 7.4.0 or an earlier release. Keep publication tooling's current
7.4.0 pins unchanged until a separately reviewed publication configuration update.

[Research provenance](RESEARCH_REFERENCES.md#dia-01).
