"""Read-only checks for the documentation bridge and original PDF preservation."""
import hashlib
from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[2]
ARCHIVED_PDFS = {
    "original-doc/Figaro Quick Start Guide.pdf": "94f7c0caccbbbe610cc8deb268547b75046f9eea959b17a2da328393e686f0ae",
    "original-doc/Figaro Release Notes.pdf": "83bea4f86ecc403ac673c95927c204a9e94842181894cda7594d62f4c5345fd1",
    "original-doc/Figaro Tutorial.pdf": "da8214aac136aceff95efb5ade759c86bf37b7965e5636a2c73836141232bb8a",
    "tutorial-latex/FigaroTutorial.pdf": "f53785a46eb14f607691cf486cc2f166b44a5ee26daab36beb69e868248a97bb",
}


class LegacyDocumentationTests(unittest.TestCase):
    def test_original_pdfs_preserved_byte_for_byte(self):
        for name, expected in ARCHIVED_PDFS.items():
            with self.subTest(pdf=name):
                data = (ROOT / "doc/archive" / name).read_bytes()
                self.assertTrue(data.startswith(b"%PDF-"))
                self.assertEqual(hashlib.sha256(data).hexdigest(), expected)

    def test_no_obsolete_api_jar_at_repository_root(self):
        self.assertFalse((ROOT / "figaro_2.12-5.0.0.0-javadoc.jar").exists())

    def test_original_guide_source_preserved_apart_from_checkout_line_endings(self):
        data = (ROOT / "FigaroLaTeX/archive/FigaroGuide-3.0.tex").read_bytes()
        self.assertEqual(hashlib.sha256(data.replace(b"\r\n", b"\n")).hexdigest(),
                         "28558de8c438f009c6cd03e7dc9f20f7153a249806d488a384a71f852305e036")

    def test_bridge_covers_every_retained_chapter(self):
        tutorial = (ROOT / "FigaroLaTeX/Tutorial/FigaroTutorial.tex").read_text(encoding="utf-8")
        chapters = re.findall(r"\\include\{Sections/([^}]+)\}", tutorial)
        self.assertEqual(len(chapters), 13)
        bridge = (ROOT / "docs/DOCUMENTATION_MIGRATION.md").read_text(encoding="utf-8")
        for chapter in chapters:
            with self.subTest(chapter=chapter):
                self.assertTrue((ROOT / f"FigaroLaTeX/Tutorial/Sections/{chapter}.tex").is_file())
                self.assertIn(f"`{chapter}`", bridge)
        self.assertLess(tutorial.index(r"\chapter*{Modernization preface}"),
                        tutorial.index(r"\include{Sections/1Introduction}"))

    def test_shared_preface_is_reachable_from_both_documents(self):
        for name in ("FigaroGuide/FigaroGuide.tex", "Tutorial/FigaroTutorial.tex"):
            document = ROOT / "FigaroLaTeX" / name
            text = document.read_text(encoding="utf-8")
            self.assertIn(r"\input{../Modernization}", text)
            self.assertTrue((document.parent / "../Modernization.tex").is_file())
        self.assertTrue((ROOT / "FigaroLaTeX/archive/FigaroGuide-3.0.tex").is_file())

    def test_modernization_credits_supplement_original_authorship(self):
        text = (ROOT / "FigaroAttributions.txt").read_text(encoding="utf-8")
        for credit in ("Dr. Avi Pfeffer", "Charles River Analytics Contributors",
                       "External Contributors", "Matthew Wilkins", "Codex (OpenAI)"):
            self.assertIn(credit, text)
        self.assertIn("copyright notices and license terms remain unchanged", text)


if __name__ == "__main__":
    unittest.main()
