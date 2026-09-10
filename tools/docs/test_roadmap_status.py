"""Read-only checks for archived evidence and shipped API documentation status."""
import hashlib
from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]


class RoadmapStatusTests(unittest.TestCase):
    def test_historical_milestone_evidence_is_preserved(self):
        history = (ROOT / "docs/ROADMAP_HISTORY.md").read_text(encoding="utf-8")
        body = history[history.index("## Current baseline"):].rstrip() + "\n"
        self.assertEqual(hashlib.sha256(body.encode("utf-8")).hexdigest(),
                         "764f34fcc4bd5205397eca41441f5207a4b7722a7da9ec7abbb4af089a24b7df")

    def test_current_roadmap_links_history_and_release_contracts(self):
        roadmap = (ROOT / "ROADMAP.md").read_text(encoding="utf-8")
        for target in ("docs/ROADMAP_HISTORY.md", "docs/RELEASE_6_1.md",
                       "docs/MAVEN_CENTRAL.md", "docs/MIGRATION.md", "WISHLIST.md"):
            with self.subTest(target=target):
                self.assertIn(f"]({target})", roadmap)

    def test_shipped_gvm_guides_link_installation_without_preview_labels(self):
        guides = ("GAUSS_VON_MISES", "GVM_DIAGNOSTICS", "GVM_MOMENTS",
                  "GVM_GRADIENTS", "GVM_QUADRATURE", "GVM_SCORE_CALIBRATION",
                  "GVM_TENSOR_QUADRATURE", "GVM_BHATTACHARYYA",
                  "GVM_SCALAR_BHATTACHARYYA", "GVM_SCALAR_TAIL_PRODUCTION",
                  "GVM_QUADRATURE_COMPARISON", "GVM_MUTUAL_INFORMATION")
        stale = ("development preview", "source-development preview",
                 "not a new tagged release", "or declare a tagged release",
                 "Build a snapshot containing this API",
                 "rebuild a commit containing it")
        for guide in guides:
            with self.subTest(guide=guide):
                text = (ROOT / f"docs/{guide}.md").read_text(encoding="utf-8")
                self.assertIn("](MAVEN_CENTRAL.md)", text)
                for phrase in stale:
                    self.assertNotIn(phrase, text)


if __name__ == "__main__":
    unittest.main()
