"""Fast regression for the independent numerical-oracle dependency order."""
from pathlib import Path
import unittest


class BudgetWorkflowTest(unittest.TestCase):
    def test_preflight_checks_local_publication(self):
        text = (Path(__file__).resolve().parents[2] / '.github/workflows/ci.yml').read_text(encoding='utf-8')
        preflight = text.split('  release-packaging-preflight:',1)[1].split('  gvm-bhattacharyya-research:',1)[0]
        self.assertIn('run: sbt --server --batch "figaro / publishLocal"', preflight)

    def test_oracle_dependency_precedes_both_consumers(self):
        workflow = (Path(__file__).resolve().parents[2] / '.github/workflows/ci.yml')
        # parents[2] is tools' parent: repository root.
        text = workflow.read_text(encoding='utf-8')
        step = text.split('- name: Verify capability expansion and independent numerical controls', 1)[1]
        step = step.split('\n      - name:', 1)[0]
        install = step.index('python3 -m pip install --disable-pip-version-check mpmath==1.3.0')
        self.assertLess(install, step.index('python3 -B tools/budget_reference.py'))
        self.assertLess(install, step.index('python3 -B tools/capability_reference.py'))
        self.assertLess(step.index('runMain com.cra.figaro.test.modernization.BoundedParticleBudgetStudy'),
                        step.index('python3 -B tools/budget_reference.py'))


if __name__ == '__main__':
    unittest.main()
