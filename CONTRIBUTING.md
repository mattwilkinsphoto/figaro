# Contributing to Figaro

## Consumer-impact release policy

Release eligibility is determined by consumer impact, not the number of commits,
merges or elapsed days. A merge to `main` is not itself a published release.

| Accumulated changes since the latest published release | Release decision |
| --- | --- |
| Validated correctness, security or compatibility fix | Recommend a prompt patch release; one consequential fix is sufficient |
| Compatible new public functionality or public API deprecation | Recommend a minor release when the bounded milestone is complete |
| Breaking public API or supported compatibility contract | Recommend a major release and document migration requirements |
| Meaningful validated performance or consumer packaging/dependency improvement | Recommend a release when consumers benefit; select patch/minor/major according to compatibility and API impact |
| Repository-only documentation, references, CI or publication-tooling housekeeping | No release solely for these changes, unless they correct a consumer-facing defect in published artifacts |

Routine compatible improvements may be batched. Do not defer an important fix merely
to accumulate more changes. Evaluate actual behavior and delivered artifacts, not
just changed filenames. Correcting an already published JAR, POM, sources or API
documentation artifact requires a new version; never overwrite released artifacts
or move their tags. Version categories follow [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

### Assessment and notification at each merge to main

The contributor performing a merge to `main` must assess the cumulative difference
from the latest verified published release, including earlier unreleased merges.
Record the assessment in the merge handoff and notify the maintainer there when a
new release is warranted. This is a merge-time obligation, not a recurring poll.
For changes merged elsewhere, perform the same assessment when reviewing that merge;
these instructions alone do not install an automatic GitHub notification service.

The handoff must state:

- Published baseline version/tag and resulting `main` commit.
- Whether a release is warranted, the concrete consumer benefit and affected commits.
- Recommended version category and urgency, or a specific reason no release is needed.
- Validation completed and outstanding gates; distinguish **eligible** from **ready**.

For an already reported recommendation, identify any material change in scope,
urgency or readiness rather than presenting it as a new finding. A tag, draft release
or build version alone is not evidence that publication succeeded.

Before publishing, require applicable regression/numerical checks, passing CI,
packaged-consumer verification, updated release notes and documentation, and explicit
publication approval. Once runtime work toward a release starts, use an appropriate
next-version `-SNAPSHOT` to distinguish development builds from the immutable release.
Documentation-only merges need not change the library version.

## Maintain scientific provenance with the implementation

The [research references and code map](docs/RESEARCH_REFERENCES.md) is the maintained
scientific index. This policy applies to code, examples, tests, research notes and
documentation—not only to new library APIs.

| Change | Required reference work |
| --- | --- |
| New method, family, diagnostic, RNG policy or research comparison | Identify primary sources first; add/reuse a reference group and link the implementation, tests and user/research guide |
| New derivation or adaptation | Explain what Figaro derives or changes, which source supports which part, and which guarantees do not transfer |
| Parameter, numerical-range, stopping or statistical-assumption change | Reconcile the affected reference roles and guide contracts; update validation evidence |
| Candidate promoted to production | Change research-only status only after the implementation and acceptance gates are met; retain the original research record |
| Source/test/guide rename or retirement | Repair both directions of the mapping; retain stable IDs and explain retirement or replacement |
| Refactor, typo or unrelated infrastructure change | Review reference impact; if mappings and scientific claims are unchanged, say so in the change summary—no artificial bibliography edit required |

Each reference group needs **Source**, **Role**, **Code**, **Validation** and **Guide**.
Record authors/title and a stable DOI/publisher/author URL, with the inspected revision
when relevant. Label manuals, implementation comparisons and incomplete access plainly.
For research-only work, explicitly state that no production implementation or validation
exists instead of inventing code or tests. Keep source-code licenses separate from
mathematical citations.

Each guide listed in the index must contain a link such as
`[Research provenance](RESEARCH_REFERENCES.md#sam-01)` to a group that actually lists
that guide. New Markdown notes under `docs/` whose names contain `RESEARCH` or
`LITERATURE` must be registered in the index. This naming check is only a backstop:
method documentation with other names is subject to the same review policy.

## Required checks

From the repository root:

```text
python -B tools/docs/check_research_references.py
python -B -m unittest discover -s tools/docs -p "test_*.py"
```

Also run `python -B tools/docs/check_links.py` after generating the Scala API
documentation when needed; see [build instructions](docs/BUILDING.md). Add numerical,
lifecycle or integration tests proportional to the implementation change. Do not
describe these documentation checks as executing those scientific tests.

The fast `research-reference-integrity` CI job checks the index and documentation
tooling without compiling Scala. It rejects missing registrations, invalid reference
IDs, missing metadata, broken local mappings and nonreciprocal guide links. It does
not verify external URLs, read papers, establish attribution accuracy or prove that
all newly introduced scientific ideas were cited. Those remain reviewer obligations.
Whether this job is required for merge depends on repository branch protection;
adding the job does not itself configure that protection.

## Change summary / pull request

List affected reference IDs, the role or assumptions changed, and the tests actually
run. If no reference update is needed, give a specific reason. Confirm that claims
match the implementation, research-only methods are not presented as shipped, and
no private application context or unlicensed material was introduced. Use the
repository pull-request checklist; these requirements also apply to direct commits.
