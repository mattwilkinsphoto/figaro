# Contributing to Figaro

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
