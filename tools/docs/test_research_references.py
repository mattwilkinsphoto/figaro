"""Read-only checks for the scientific reference index and its local navigation."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[2]
INDEX = ROOT / "docs/RESEARCH_REFERENCES.md"


class ResearchReferenceTests(unittest.TestCase):
    def setUp(self):
        self.text = INDEX.read_text(encoding="utf-8")
        self.anchors = re.findall(r'<a id="([a-z]+-\d+)"></a>', self.text)

    def test_unique_stable_ids_and_navigation(self):
        self.assertTrue(self.anchors)
        self.assertEqual(len(self.anchors), len(set(self.anchors)))
        headings = re.findall(r"^### ([A-Z]+-\d+):", self.text, re.MULTILINE)
        self.assertEqual([h.lower() for h in headings], self.anchors)
        for target in re.findall(r"\]\(#([a-z]+-\d+)\)", self.text):
            self.assertIn(target, self.anchors)
        for anchor in self.anchors:
            self.assertIn(f"](#{anchor})", self.text)

    def test_every_group_explains_source_role_code_validation_and_guide(self):
        blocks = re.split(r'<a id="[a-z]+-\d+"></a>', self.text)[1:]
        for block in blocks:
            with self.subTest(group=block.splitlines()[2]):
                for field in ("Source", "Role", "Code", "Validation", "Guide"):
                    self.assertRegex(block, rf"\*\*{field}:\*\* \S")
                source = re.search(r"\*\*Source:\*\* (.+)", block).group(1)
                self.assertIn("https://", source)
                guide = re.search(r"\*\*Guide:\*\* (.+)", block).group(1)
                self.assertRegex(guide, r"\]\([A-Z_0-9]+\.md\)")

    def test_local_source_and_validation_links_exist(self):
        targets = re.findall(r"\]\(([^)]+)\)", self.text)
        source_count = 0
        for target in targets:
            if target.startswith(("https://", "http://", "#")):
                continue
            path = target.split("#", 1)[0]
            with self.subTest(target=target):
                self.assertTrue((INDEX.parent / path).is_file())
            source_count += "/src/main/scala/" in path
        self.assertGreater(source_count, 0)

    def test_guide_backlinks_resolve(self):
        documents = [ROOT / "README.md", *(ROOT / "docs").glob("*.md")]
        backlinks = 0
        for document in documents:
            text = document.read_text(encoding="utf-8")
            for anchor in re.findall(r"RESEARCH_REFERENCES\.md#([a-z]+-\d+)", text):
                with self.subTest(document=document.name, anchor=anchor):
                    self.assertIn(anchor, self.anchors)
                backlinks += 1
        self.assertGreater(backlinks, 0)
        for name in ("README.md", "docs/API_GUIDE.md"):
            self.assertIn("RESEARCH_REFERENCES.md", (ROOT / name).read_text(encoding="utf-8"))

    def test_index_has_no_workstation_links(self):
        self.assertNotRegex(self.text, r"(?i)\b[a-z]:[/\\]|file://")


if __name__ == "__main__":
    unittest.main()
