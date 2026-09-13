## Summary

Describe the change and its supported scope.

## Release impact

- [ ] Assess cumulative changes against the latest verified published release using [the release policy](../CONTRIBUTING.md#consumer-impact-release-policy).
- [ ] Record consumer impact, release eligibility, proposed version/urgency, and outstanding validation (or why no release is needed).
- [ ] At merge to `main`, include the resulting commit and notify the maintainer in the merge handoff if a release is warranted. Eligibility is not publication approval.

## Research-reference impact

Affected reference IDs and changes (or a specific reason no update is needed):

- [ ] Sources, implementation/test links and relevant guide backlinks are current.
- [ ] Adaptations, assumptions and research-only versus shipped status are accurate.
- [ ] Theoretical claims are distinguished from locally executed validation.
- [ ] Scientific citations and code/data licenses were reviewed separately.
- [ ] No private application context, local paths or account names were added.

## Validation

List commands/results and limitations, including:

- [ ] `python -B tools/docs/check_research_references.py`
- [ ] `python -B -m unittest discover -s tools/docs -p "test_*.py"`
- [ ] Relevant numerical/integration checks, or an explanation for documentation-only scope.
