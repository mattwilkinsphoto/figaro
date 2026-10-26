# Figaro 7.4.1 preparation

**Preparation approved; publication not authorized or performed.** Published
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
- [x] Fresh integration branch CI and reviewed fast-forward into main; post-merge CI is a separate checkpoint below.
- [ ] Final-version 7.4.1 source, clean reproducible artifacts, POM/legal inspection and offline Maven-layout bundle.
- [ ] All existing packaged consumers plus a public diagnostic regression; verify the loaded artifact hash and a published-7.4.0 negative control.
- [ ] Record exact source/artifact hashes and executed validation; retain original failures and timing advisories.

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
That commit was fast-forwarded into main after success. It includes compatibility
through 7.4.0, byte-reproducible snapshot builds, all seven packaged consumers and
the immutable 7.4.0 negative control. Final-version 7.4.1 packaging and the main
run are not inferred from that snapshot result; they must pass separately.

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
