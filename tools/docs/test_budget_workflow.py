"""Fast regression for the independent numerical-oracle dependency order."""
from pathlib import Path
import ast
import re
import shlex
import unittest


class BudgetWorkflowTest(unittest.TestCase):
    def test_changed_packaging_commands_use_declared_cli_options(self):
        root = Path(__file__).resolve().parents[2]
        text = (root / '.github/workflows/ci.yml').read_text(encoding='utf-8')
        for script in ('tools/check_acceptance_artifacts.py', 'tools/build_release_bundle.py'):
            tree = ast.parse((root / script).read_text(encoding='utf-8'))
            declared = {'--help'}
            for node in ast.walk(tree):
                if isinstance(node,ast.Call) and isinstance(node.func,ast.Attribute) and node.func.attr=='add_argument':
                    declared.update(arg.value for arg in node.args if isinstance(arg,ast.Constant)
                                    and isinstance(arg.value,str) and arg.value.startswith('--'))
            commands = [line for line in text.splitlines() if re.search(r'python3\s+-B\s+'+re.escape(script)+r'\s',line)]
            self.assertTrue(commands, script)
            for line in commands:
                flags = {word.split('=',1)[0] for word in shlex.split(line) if word.startswith('--')}
                self.assertFalse(flags-declared, f'{script}: unknown options {flags-declared}')

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
