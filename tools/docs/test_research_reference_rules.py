"""Negative controls for provenance rules; no temporary files or network access."""
import contextlib
import io
import unittest
from unittest.mock import patch

from check_research_references import ROOT, check_repository, main, validate

TEXT = '''# References
[Method](#sam-01)
<a id="sam-01"></a>

### SAM-01: Example

**Source:** [Paper](https://example.org/paper).
**Role:** Adapted; not the complete published algorithm.
**Code:** [Kernel](../Figaro/Kernel.scala).
**Validation:** [Test](../Figaro/KernelTest.scala).
**Guide:** [Guide](METHOD_RESEARCH.md).
'''
GUIDE = "Research provenance: [source](RESEARCH_REFERENCES.md#sam-01)."
PATHS = {"Figaro/Kernel.scala", "Figaro/KernelTest.scala", "docs/METHOD_RESEARCH.md"}


class ResearchReferenceRuleTests(unittest.TestCase):
    def errors(self, text=TEXT, documents=None, paths=PATHS):
        if documents is None:
            documents = {"docs/METHOD_RESEARCH.md": GUIDE}
        return validate(text, documents, paths.__contains__)

    def test_repository_obeys_rules(self):
        self.assertEqual(check_repository(), [])

    def test_cli_fails_on_invalid_mappings(self):
        with patch("check_research_references.check_repository", return_value=["Missing source"]):
            output = io.StringIO()
            with contextlib.redirect_stderr(output):
                self.assertEqual(main(), 1)
            self.assertIn("Missing source", output.getvalue())

    def test_policy_and_ci_entry_points_are_present(self):
        self.assertIn("CONTRIBUTING.md", (ROOT / "AGENTS.md").read_text(encoding="utf-8"))
        for name in ("CONTRIBUTING.md", ".github/pull_request_template.md"):
            self.assertIn("tools/docs/check_research_references.py", (ROOT / name).read_text(encoding="utf-8"))
        workflow = (ROOT / ".github/workflows/ci.yml").read_text(encoding="utf-8")
        self.assertIn("  research-reference-integrity:", workflow)
        self.assertIn("python3 -B tools/docs/check_research_references.py", workflow)

    def test_valid_mapping(self):
        self.assertEqual(self.errors(), [])

    def test_missing_metadata_fails(self):
        self.assertTrue(any("Role" in e for e in self.errors(TEXT.replace("**Role:**", "Role:"))))

    def test_moved_source_or_test_fails(self):
        for removed in ("Figaro/Kernel.scala", "Figaro/KernelTest.scala"):
            with self.subTest(removed=removed):
                self.assertTrue(any(removed in e for e in self.errors(paths=PATHS - {removed})))

    def test_missing_or_unrelated_guide_backlink_fails(self):
        for body in ("No backlink", "[Wrong](RESEARCH_REFERENCES.md#rng-01)"):
            with self.subTest(body=body):
                self.assertTrue(any("reciprocal" in e for e in self.errors(documents={"docs/METHOD_RESEARCH.md": body})))

    def test_new_research_or_literature_note_requires_registration(self):
        for name in ("NEW_RESEARCH.md", "nested/LITERATURE_REVIEW.md"):
            documents = {"docs/METHOD_RESEARCH.md": GUIDE, "docs/" + name: "New candidate"}
            self.assertTrue(any("not registered" in e for e in self.errors(documents=documents)))

    def test_new_general_document_does_not_require_an_artificial_citation(self):
        documents = {"docs/METHOD_RESEARCH.md": GUIDE, "docs/INSTALL.md": "Install instructions"}
        self.assertEqual(self.errors(documents=documents), [])

    def test_duplicate_ids_fail(self):
        self.assertTrue(any("duplicate" in e for e in self.errors(TEXT + TEXT)))

    def test_unknown_backlink_fails(self):
        documents = {"docs/METHOD_RESEARCH.md": GUIDE + " [Unknown](RESEARCH_REFERENCES.md#sam-99)"}
        self.assertTrue(any("unknown reference sam-99" in e for e in self.errors(documents=documents)))

    def test_candidate_with_explicit_no_code_or_tests_is_allowed(self):
        text = TEXT.replace("[Kernel](../Figaro/Kernel.scala)", "No production implementation")
        text = text.replace("[Test](../Figaro/KernelTest.scala)", "No local validation claimed")
        self.assertEqual(self.errors(text), [])


if __name__ == "__main__":
    unittest.main()
