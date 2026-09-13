# Figaro contributor instructions

## Assess release eligibility at each merge to main

Follow the [consumer-impact release policy](CONTRIBUTING.md#consumer-impact-release-policy).
At every merge to `main`, assess all changes since the latest verified published
release, not just the current branch. In the merge handoff, state the baseline and
resulting commit, whether a release is warranted, consumer impact, recommended
version and urgency, and validation completed or still required. Notify the
maintainer when a release is warranted; distinguish eligibility from readiness.
For an existing recommendation, explain material changes rather than issuing a
duplicate alert. If no release is warranted, give the specific reason.

This is part of the merge workflow, not a scheduled recurring check. For externally
performed merges, assess when reviewing them; this rule is not an installed GitHub
notification service. Do not publish merely because a merge occurred or a release
is eligible; publication requires explicit approval. Never overwrite released bytes
or move release tags. Preserve the library version for repository-only housekeeping.

## Scientific references are part of the change

Before adding or changing a method, scientific claim, distribution, diagnostic,
sampler, stopping rule, numerical approximation, research experiment or RNG policy,
read [the research index](docs/RESEARCH_REFERENCES.md) and the relevant topic guide.
Follow [the contribution rules](CONTRIBUTING.md).

- Update the index and relevant guide in the same change when the scientific basis,
  implementation mapping, validation, assumptions or delivery status changes.
- For new research or method selection, start with primary literature and existing
  maintained implementations. Do not present an unread abstract or software manual
  as full-text review of an originating paper. Record access limitations honestly.
- Reuse stable reference IDs for existing sources. Add a group for genuinely new
  provenance; include Source, Role, Code, Validation and Guide fields. Every indexed
  guide must link back to a relevant group. Register new research/literature notes.
- Distinguish a shipped implementation, adapted method, wrapped backend, independent
  derivation, validation reference, research prototype and unimplemented candidate.
  Record deviations, parameter conventions and assumptions. A citation alone does
  not transfer the source's theoretical guarantees to Figaro.
- When files move, update source, test and guide links. When a method is retired,
  retain its ID and historical provenance, explicitly mark its status and point to
  the retained explanation or replacement. Do not leave dead links or imply it ships.
- Preserve independence of numerical controls and record what was actually tested.
  A bibliography update is not a numerical validation run.
- Keep private application context, local paths and account names out of public
  documentation. Citations do not grant code/data redistribution rights; review
  licenses and provenance separately.

Before handing off, run the research-reference checker and documentation tests in
CONTRIBUTING.md. Report the changed reference IDs, checks run and any remaining
limitations. If an in-scope code change needs no reference update, explain why in
the handoff or pull request rather than making a meaningless index edit.

The automated checks validate structure and navigation, not scientific truth or
complete citation coverage. Review those substantive requirements explicitly.
Preserve unrelated local changes, follow the environment's file-access requirements,
and do not commit or publish private research artifacts with the library.
